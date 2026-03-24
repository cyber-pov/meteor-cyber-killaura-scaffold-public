package dev.cyber.meteor.modules.render;

import baritone.api.BaritoneAPI;
import dev.cyber.meteor.CyberCategories;
import dev.cyber.meteor.utils.world.OreConfig;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.Xoroshiro128PlusPlusRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OreSim extends Module {
    private final Map<Long, Map<OreConfig, Set<Vec3d>>> chunkRenderers = new ConcurrentHashMap<>();
    private final List<BlockPos> oreGoals = new ArrayList<>();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> seed = sgGeneral.add(new StringSetting.Builder()
        .name("seed")
        .description("World seed used for ore simulation. Singleplayer uses the actual world seed automatically.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range")
        .description("Taxi cap distance of chunks being shown.")
        .defaultValue(5)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<AirCheck> airCheck = sgGeneral.add(new EnumSetting.Builder<AirCheck>()
        .name("air-check-mode")
        .description("Checks if there is air at a calculated ore position.")
        .defaultValue(AirCheck.RECHECK)
        .build()
    );

    private final Setting<Boolean> baritone = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone")
        .description("Set baritone ore positions to the simulated ones.")
        .defaultValue(false)
        .build()
    );

    private Long worldSeed;
    private Map<RegistryKey<Biome>, List<OreConfig>> oreConfig;

    public OreSim() {
        super(CyberCategories.CYBER, "ore-sim", "Simulates ore placement from the world seed.");
        SettingGroup sgOres = settings.createGroup("Ores");
        OreConfig.ORE_SETTINGS.forEach(sgOres::add);
    }

    public boolean baritone() {
        return isActive() && baritone.get() && BaritoneUtils.IS_AVAILABLE;
    }

    public List<BlockPos> getOreGoals() {
        return oreGoals;
    }

    @Override
    public void onActivate() {
        worldSeed = resolveSeed();
        if (worldSeed == null) {
            error("Set the seed before enabling OreSim.");
            toggle();
            return;
        }

        reload();
    }

    @Override
    public void onDeactivate() {
        chunkRenderers.clear();
        oreGoals.clear();
        oreConfig = null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || oreConfig == null) return;

        int chunkX = mc.player.getChunkPos().x;
        int chunkZ = mc.player.getChunkPos().z;
        int rangeValue = horizontalRadius.get();

        for (int range = 0; range <= rangeValue; range++) {
            for (int x = -range + chunkX; x <= range + chunkX; x++) {
                renderChunk(x, chunkZ + range - rangeValue, event);
            }
            for (int x = -range + 1 + chunkX; x < range + chunkX; x++) {
                renderChunk(x, chunkZ - range + rangeValue + 1, event);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (airCheck.get() != AirCheck.RECHECK || event.newState.isOpaque()) return;

        long chunkKey = ChunkPos.toLong(event.pos);
        Map<OreConfig, Set<Vec3d>> chunk = chunkRenderers.get(chunkKey);
        if (chunk == null) return;

        Vec3d pos = Vec3d.of(event.pos);
        for (Set<Vec3d> ores : chunk.values()) {
            ores.remove(pos);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        Long resolvedSeed = resolveSeed();
        if (resolvedSeed != null && !resolvedSeed.equals(worldSeed)) {
            worldSeed = resolvedSeed;
            reload();
        }

        if (!baritone() || oreConfig == null || !BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().isActive()) return;

        oreGoals.clear();
        ChunkPos chunkPos = mc.player.getChunkPos();
        int rangeValue = 4;
        for (int range = 0; range <= rangeValue; range++) {
            for (int x = -range + chunkPos.x; x <= range + chunkPos.x; ++x) {
                oreGoals.addAll(addToBaritone(x, chunkPos.z + range - rangeValue));
            }
            for (int x = -range + 1 + chunkPos.x; x < range + chunkPos.x; ++x) {
                oreGoals.addAll(addToBaritone(x, chunkPos.z - range + rangeValue + 1));
            }
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        doMathOnChunk(event.chunk());
    }

    private void renderChunk(int x, int z, Render3DEvent event) {
        Map<OreConfig, Set<Vec3d>> chunk = chunkRenderers.get(ChunkPos.toLong(x, z));
        if (chunk == null) return;

        for (Map.Entry<OreConfig, Set<Vec3d>> entry : chunk.entrySet()) {
            if (!entry.getKey().active.get()) continue;

            for (Vec3d pos : entry.getValue()) {
                event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, entry.getKey().color, 0);
            }
        }
    }

    private ArrayList<BlockPos> addToBaritone(int chunkX, int chunkZ) {
        ArrayList<BlockPos> baritoneGoals = new ArrayList<>();
        Map<OreConfig, Set<Vec3d>> chunk = chunkRenderers.get(ChunkPos.toLong(chunkX, chunkZ));
        if (chunk == null) return baritoneGoals;

        chunk.entrySet().stream()
            .filter(entry -> entry.getKey().active.get())
            .flatMap(entry -> entry.getValue().stream())
            .map(BlockPos::ofFloored)
            .forEach(baritoneGoals::add);

        return baritoneGoals;
    }

    private void reload() {
        if (mc.world == null || worldSeed == null) return;

        oreConfig = OreConfig.getRegistry(PlayerUtils.getDimension());
        chunkRenderers.clear();
        oreGoals.clear();

        for (var chunk : Utils.chunks(false)) {
            if (chunk instanceof WorldChunk worldChunk) {
                doMathOnChunk(worldChunk);
            }
        }
    }

    private void doMathOnChunk(WorldChunk chunk) {
        if (mc.world == null || oreConfig == null || worldSeed == null) return;

        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        if (chunkRenderers.containsKey(chunkKey)) return;

        Set<RegistryKey<Biome>> biomes = new HashSet<>();
        ChunkPos.stream(new ChunkPos(chunkPos.x - 1, chunkPos.z - 1), new ChunkPos(chunkPos.x + 1, chunkPos.z + 1)).forEach(pos -> {
            WorldChunk biomeChunk = mc.world.getChunkManager().getWorldChunk(pos.x, pos.z);
            if (biomeChunk == null) return;

            for (ChunkSection chunkSection : biomeChunk.getSectionArray()) {
                chunkSection.getBiomeContainer().forEachValue(entry -> entry.getKey().ifPresent(biomes::add));
            }
        });

        Set<OreConfig> oreSet = biomes.stream()
            .flatMap(biome -> getDefaultOres(biome).stream())
            .collect(Collectors.toSet());

        int chunkStartX = chunkPos.getStartX();
        int chunkStartZ = chunkPos.getStartZ();
        ChunkRandom random = new ChunkRandom(new Xoroshiro128PlusPlusRandom(0L));
        long populationSeed = random.setPopulationSeed(worldSeed, chunkStartX, chunkStartZ);
        HashMap<OreConfig, Set<Vec3d>> renderedOres = new HashMap<>();

        for (OreConfig ore : oreSet) {
            HashSet<Vec3d> ores = new HashSet<>();
            random.setDecoratorSeed(populationSeed, ore.index, ore.step);
            int repeat = ore.count.get(random);

            for (int i = 0; i < repeat; i++) {
                if (ore.rarity != 1.0f && random.nextFloat() >= 1.0f / ore.rarity) continue;
                if (ore.heightProvider == null) continue;

                int x = random.nextInt(16) + chunkStartX;
                int z = random.nextInt(16) + chunkStartZ;
                int y = ore.heightProvider.get(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);

                RegistryEntry<Biome> biome = chunk.getBiomeForNoiseGen(x >> 2, y >> 2, z >> 2);
                RegistryKey<Biome> biomeKey = biome.getKey().orElse(null);
                if (biomeKey == null || !getDefaultOres(biomeKey).contains(ore)) continue;

                if (ore.scattered) {
                    ores.addAll(generateHidden(random, origin, ore.size));
                } else {
                    ores.addAll(generateNormal(random, origin, ore.size, ore.discardOnAirChance));
                }
            }

            if (!ores.isEmpty()) renderedOres.put(ore, ores);
        }

        chunkRenderers.put(chunkKey, renderedOres);
    }

    private List<OreConfig> getDefaultOres(RegistryKey<Biome> biomeKey) {
        List<OreConfig> ores = oreConfig.get(biomeKey);
        if (ores != null) return ores;
        return oreConfig.values().stream().findAny().orElseGet(List::of);
    }

    private ArrayList<Vec3d> generateNormal(ChunkRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        float angle = random.nextFloat() * (float) Math.PI;
        float g = (float) veinSize / 8.0f;
        int i = MathHelper.ceil(((float) veinSize / 16.0f * 2.0f + 1.0f) / 2.0f);
        double startX = blockPos.getX() + Math.sin(angle) * g;
        double endX = blockPos.getX() - Math.sin(angle) * g;
        double startZ = blockPos.getZ() + Math.cos(angle) * g;
        double endZ = blockPos.getZ() - Math.cos(angle) * g;
        double startY = blockPos.getY() + random.nextInt(3) - 2;
        double endY = blockPos.getY() + random.nextInt(3) - 2;
        int minX = blockPos.getX() - MathHelper.ceil(g) - i;
        int minY = blockPos.getY() - 2 - i;
        int minZ = blockPos.getZ() - MathHelper.ceil(g) - i;
        int sizeX = 2 * (MathHelper.ceil(g) + i);
        int sizeY = 2 * (2 + i);

        for (int x = minX; x <= minX + sizeX; ++x) {
            for (int z = minZ; z <= minZ + sizeX; ++z) {
                if (minY <= mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z)) {
                    return generateVeinPart(random, veinSize, startX, endX, startZ, endZ, startY, endY, minX, minY, minZ, sizeX, sizeY, discardOnAir);
                }
            }
        }

        return new ArrayList<>();
    }

    private ArrayList<Vec3d> generateVeinPart(
        ChunkRandom random,
        int veinSize,
        double startX,
        double endX,
        double startZ,
        double endZ,
        double startY,
        double endY,
        int x,
        int y,
        int z,
        int sizeX,
        int sizeY,
        float discardOnAir
    ) {
        BitSet bitSet = new BitSet(sizeX * sizeY * sizeX);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double[] data = new double[veinSize * 4];
        ArrayList<Vec3d> positions = new ArrayList<>();

        for (int i = 0; i < veinSize; ++i) {
            float progress = (float) i / (float) veinSize;
            double px = MathHelper.lerp(progress, startX, endX);
            double py = MathHelper.lerp(progress, startY, endY);
            double pz = MathHelper.lerp(progress, startZ, endZ);
            double spread = random.nextDouble() * veinSize / 16.0d;
            double radius = ((MathHelper.sin((float) Math.PI * progress) + 1.0f) * spread + 1.0d) / 2.0d;
            data[i * 4] = px;
            data[i * 4 + 1] = py;
            data[i * 4 + 2] = pz;
            data[i * 4 + 3] = radius;
        }

        for (int i = 0; i < veinSize - 1; ++i) {
            if (data[i * 4 + 3] <= 0.0d) continue;

            for (int j = i + 1; j < veinSize; ++j) {
                if (data[j * 4 + 3] <= 0.0d) continue;

                double dx = data[i * 4] - data[j * 4];
                double dy = data[i * 4 + 1] - data[j * 4 + 1];
                double dz = data[i * 4 + 2] - data[j * 4 + 2];
                double dr = data[i * 4 + 3] - data[j * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0d) data[j * 4 + 3] = -1.0d;
                    else data[i * 4 + 3] = -1.0d;
                }
            }
        }

        for (int i = 0; i < veinSize; ++i) {
            double radius = data[i * 4 + 3];
            if (radius < 0.0d) continue;

            double px = data[i * 4];
            double py = data[i * 4 + 1];
            double pz = data[i * 4 + 2];
            int minX = Math.max(MathHelper.floor(px - radius), x);
            int minY = Math.max(MathHelper.floor(py - radius), y);
            int minZ = Math.max(MathHelper.floor(pz - radius), z);
            int maxX = Math.max(MathHelper.floor(px + radius), minX);
            int maxY = Math.max(MathHelper.floor(py + radius), minY);
            int maxZ = Math.max(MathHelper.floor(pz + radius), minZ);

            for (int currentX = minX; currentX <= maxX; ++currentX) {
                double normalizedX = ((double) currentX + 0.5d - px) / radius;
                if (normalizedX * normalizedX >= 1.0d) continue;

                for (int currentY = minY; currentY <= maxY; ++currentY) {
                    double normalizedY = ((double) currentY + 0.5d - py) / radius;
                    if (normalizedX * normalizedX + normalizedY * normalizedY >= 1.0d) continue;

                    for (int currentZ = minZ; currentZ <= maxZ; ++currentZ) {
                        double normalizedZ = ((double) currentZ + 0.5d - pz) / radius;
                        if (normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ >= 1.0d) continue;

                        int index = currentX - x + (currentY - y) * sizeX + (currentZ - z) * sizeX * sizeY;
                        if (bitSet.get(index)) continue;
                        bitSet.set(index);

                        mutable.set(currentX, currentY, currentZ);
                        if (mc.world.isOutOfHeightLimit(currentY)) continue;
                        if (airCheck.get() != AirCheck.OFF && !mc.world.getBlockState(mutable).isOpaque()) continue;
                        if (shouldPlace(mutable, discardOnAir, random)) {
                            positions.add(Vec3d.of(mutable));
                        }
                    }
                }
            }
        }

        return positions;
    }

    private boolean shouldPlace(BlockPos orePos, float discardOnAir, ChunkRandom random) {
        if (discardOnAir == 0.0f || (discardOnAir != 1.0f && random.nextFloat() >= discardOnAir)) return true;

        for (Direction direction : Direction.values()) {
            BlockState neighbor = mc.world.getBlockState(orePos.offset(direction));
            if (!neighbor.isOpaque() && discardOnAir != 1.0f) return false;
        }

        return true;
    }

    private ArrayList<Vec3d> generateHidden(ChunkRandom random, BlockPos origin, int size) {
        ArrayList<Vec3d> positions = new ArrayList<>();
        int repeats = random.nextInt(size + 1);

        for (int i = 0; i < repeats; ++i) {
            int boundedSize = Math.min(i, 7);
            int x = randomCoord(random, boundedSize) + origin.getX();
            int y = randomCoord(random, boundedSize) + origin.getY();
            int z = randomCoord(random, boundedSize) + origin.getZ();
            BlockPos pos = new BlockPos(x, y, z);

            if (airCheck.get() != AirCheck.OFF && !mc.world.getBlockState(pos).isOpaque()) continue;
            if (shouldPlace(pos, 1.0f, random)) positions.add(Vec3d.of(pos));
        }

        return positions;
    }

    private int randomCoord(ChunkRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * size);
    }

    private Long resolveSeed() {
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return mc.getServer().getOverworld().getSeed();
        }

        String value = seed.get().trim();
        if (value.isEmpty()) return null;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return (long) value.hashCode();
        }
    }

    public enum AirCheck {
        ON_LOAD,
        RECHECK,
        OFF
    }
}
