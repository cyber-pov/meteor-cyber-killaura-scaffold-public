package dev.cyber.meteor.modules.render;

import dev.cyber.meteor.CyberCategories;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-Xray ESP exploiting the fact that Anti-Xray is not applied at chunk load distance.
 * Ores visible at chunk load = real. Removed only when actually mined (ore → air).
 */
public class OreReveal extends Module {

    public enum OreKind {
        COAL    ("Coal",          new Color(80,  80,  80 ), "coal_ore", "deepslate_coal_ore"),
        IRON    ("Iron",          new Color(236, 173, 119), "iron_ore", "deepslate_iron_ore"),
        GOLD    ("Gold",          new Color(247, 229, 30 ), "gold_ore", "deepslate_gold_ore", "nether_gold_ore"),
        REDSTONE("Redstone",      new Color(245, 7,   23 ), "redstone_ore", "deepslate_redstone_ore"),
        DIAMOND ("Diamond",       new Color(33,  244, 255), "diamond_ore", "deepslate_diamond_ore"),
        LAPIS   ("Lapis",         new Color(8,   26,  189), "lapis_ore", "deepslate_lapis_ore"),
        COPPER  ("Copper",        new Color(239, 151, 0  ), "copper_ore", "deepslate_copper_ore"),
        EMERALD ("Emerald",       new Color(27,  209, 45 ), "emerald_ore", "deepslate_emerald_ore"),
        QUARTZ  ("Quartz",        new Color(205, 205, 205), "nether_quartz_ore"),
        DEBRIS  ("Ancient-Debris",new Color(209, 27,  245), "ancient_debris");

        public final String label;
        public final Color color;
        public final Set<String> blockIds;

        OreKind(String label, Color color, String... ids) {
            this.label = label;
            this.color = color;
            this.blockIds = Set.of(ids);
        }

        public static OreKind fromBlockId(String id) {
            for (OreKind kind : values()) {
                if (kind.blockIds.contains(id)) return kind;
            }
            return null;
        }
    }

    // ---- Settings ----

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range").description("Display range in blocks. 0 = unlimited.")
        .defaultValue(0).min(0).sliderMax(256).build()
    );
    private final Setting<Boolean> baritone = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone").description("Send ore positions to Baritone mine process.")
        .defaultValue(false).build()
    );
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build()
    );
    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").defaultValue(false).build()
    );

    // Per-ore ON/OFF only (color is fixed per ore type)
    private final Map<OreKind, Setting<Boolean>> enabledSettings = new LinkedHashMap<>();

    // ---- State ----

    private final Map<BlockPos, OreKind> ores     = new ConcurrentHashMap<>();
    private final List<BlockPos>         oreGoals = new ArrayList<>();

    // ---- Constructor ----

    public OreReveal() {
        super(CyberCategories.CYBER, "ore-reveal", "Anti-Xray ESP using chunk load distance loophole.");

        SettingGroup sgOres = settings.createGroup("Ores");
        for (OreKind kind : OreKind.values()) {
            enabledSettings.put(kind, sgOres.add(new BoolSetting.Builder()
                .name(kind.label).defaultValue(true).build()
            ));
        }
    }

    // ---- Baritone API ----

    public boolean baritone() {
        return isActive() && baritone.get() && BaritoneUtils.IS_AVAILABLE;
    }

    public List<BlockPos> getOreGoals() {
        return oreGoals;
    }

    // ---- Lifecycle ----

    @Override
    public void onDeactivate() {
        ores.clear();
        oreGoals.clear();
    }

    // ---- Events ----

    /**
     * Chunk load: Anti-Xray not yet applied → all visible ores are real.
     */
    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        WorldChunk chunk   = event.chunk();
        int baseX          = chunk.getPos().getStartX();
        int baseZ          = chunk.getPos().getStartZ();
        int bottomY        = chunk.getBottomY();
        ChunkSection[] sections = chunk.getSectionArray();

        for (int s = 0; s < sections.length; s++) {
            ChunkSection section = sections[s];
            if (section == null || section.isEmpty()) continue;
            int baseY = bottomY + s * 16;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        OreKind kind = kindOf(section.getBlockState(x, y, z));
                        if (kind == null) continue;
                        ores.put(new BlockPos(baseX + x, baseY + y, baseZ + z), kind);
                    }
                }
            }
        }
    }

    /**
     * ore → air = actually mined → remove.
     * ore → non-ore = Anti-Xray hiding it, ore still physically there → keep.
     * non-ore → ore = fake ore injected → ignore.
     */
    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (kindOf(event.oldState) != null && event.newState.isAir()) {
            ores.remove(event.pos);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!baritone() || mc.player == null) return;
        oreGoals.clear();
        for (var entry : ores.entrySet()) {
            if (enabledSettings.get(entry.getValue()).get() && inRange(entry.getKey())) oreGoals.add(entry.getKey());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        for (var entry : ores.entrySet()) {
            OreKind kind = entry.getValue();
            if (!enabledSettings.get(kind).get() || !inRange(entry.getKey())) continue;
            renderOre(event, entry.getKey(), kind.color);
        }
    }

    // ---- Helpers ----

    private void renderOre(Render3DEvent event, BlockPos pos, Color color) {
        SettingColor line = new SettingColor(color.r, color.g, color.b, 255);
        SettingColor side = new SettingColor(color.r, color.g, color.b, 50);
        event.renderer.box(
            pos.getX(), pos.getY(), pos.getZ(),
            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
            side, line, shapeMode.get(), 0
        );
        if (tracers.get()) {
            event.renderer.line(
                RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, line
            );
        }
    }

    private boolean inRange(BlockPos pos) {
        int r = range.get();
        if (r == 0) return true;
        return mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= (double) r * r;
    }

    private static OreKind kindOf(BlockState state) {
        if (state == null || state.isAir()) return null;
        return OreKind.fromBlockId(Registries.BLOCK.getId(state.getBlock()).getPath());
    }
}
