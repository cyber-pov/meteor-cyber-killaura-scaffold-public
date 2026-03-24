package dev.cyber.meteor.utils.world;

import dev.cyber.meteor.mixin.CountPlacementModifierAccessor;
import dev.cyber.meteor.mixin.HeightRangePlacementModifierAccessor;
import dev.cyber.meteor.mixin.RarityFilterPlacementModifierAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.Dimension;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.IntProvider;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.ScatteredOreFeature;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.heightprovider.HeightProvider;
import net.minecraft.world.gen.placementmodifier.CountPlacementModifier;
import net.minecraft.world.gen.placementmodifier.HeightRangePlacementModifier;
import net.minecraft.world.gen.placementmodifier.PlacementModifier;
import net.minecraft.world.gen.placementmodifier.RarityFilterPlacementModifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class OreConfig {
    private static final Setting<Boolean> coal = new BoolSetting.Builder().name("Coal").build();
    private static final Setting<Boolean> iron = new BoolSetting.Builder().name("Iron").build();
    private static final Setting<Boolean> gold = new BoolSetting.Builder().name("Gold").build();
    private static final Setting<Boolean> redstone = new BoolSetting.Builder().name("Redstone").build();
    private static final Setting<Boolean> diamond = new BoolSetting.Builder().name("Diamond").build();
    private static final Setting<Boolean> lapis = new BoolSetting.Builder().name("Lapis").build();
    private static final Setting<Boolean> copper = new BoolSetting.Builder().name("Copper").build();
    private static final Setting<Boolean> emerald = new BoolSetting.Builder().name("Emerald").build();
    private static final Setting<Boolean> quartz = new BoolSetting.Builder().name("Quartz").build();
    private static final Setting<Boolean> debris = new BoolSetting.Builder().name("Ancient Debris").build();

    public static final List<Setting<Boolean>> ORE_SETTINGS = new ArrayList<>(Arrays.asList(
        coal, iron, gold, redstone, diamond, lapis, copper, emerald, quartz, debris
    ));

    public final int step;
    public final int index;
    public final Setting<Boolean> active;
    public final IntProvider count;
    public final HeightProvider heightProvider;
    public final HeightContext heightContext;
    public final float rarity;
    public final float discardOnAirChance;
    public final int size;
    public final Color color;
    public final boolean scattered;

    private OreConfig(PlacedFeature feature, int step, int index, Setting<Boolean> active, Color color, ChunkGenerator chunkGenerator) {
        this.step = step;
        this.index = index;
        this.active = active;
        this.color = color;

        MinecraftClient mc = MinecraftClient.getInstance();
        int bottomY = mc.world != null ? mc.world.getBottomY() : -64;
        int height = mc.world != null ? mc.world.getHeight() : 384;
        this.heightContext = new HeightContext(chunkGenerator, HeightLimitView.create(bottomY, height));

        IntProvider count = ConstantIntProvider.create(1);
        HeightProvider heightProvider = null;
        float rarity = 1.0f;

        for (PlacementModifier modifier : feature.placementModifiers()) {
            if (modifier instanceof CountPlacementModifier countModifier) {
                count = ((CountPlacementModifierAccessor) countModifier).getCount();
            } else if (modifier instanceof HeightRangePlacementModifier heightRangeModifier) {
                heightProvider = ((HeightRangePlacementModifierAccessor) heightRangeModifier).getHeight();
            } else if (modifier instanceof RarityFilterPlacementModifier rarityFilterPlacementModifier) {
                rarity = ((RarityFilterPlacementModifierAccessor) rarityFilterPlacementModifier).getChance();
            }
        }

        this.count = count;
        this.heightProvider = heightProvider;
        this.rarity = rarity;

        ConfiguredFeature<?, ?> configuredFeature = feature.feature().value();
        if (!(configuredFeature.config() instanceof OreFeatureConfig oreFeatureConfig)) {
            throw new IllegalStateException("Placed feature is not an ore feature: " + feature);
        }

        this.discardOnAirChance = oreFeatureConfig.discardOnAirChance;
        this.size = oreFeatureConfig.size;
        this.scattered = configuredFeature.feature() instanceof ScatteredOreFeature;
    }

    public static Map<RegistryKey<Biome>, List<OreConfig>> getRegistry(Dimension dimension) {
        RegistryWrapper.WrapperLookup registry = BuiltinRegistries.createWrapperLookup();
        RegistryEntryLookup<PlacedFeature> placedFeatures = registry.getOrThrow(RegistryKeys.PLACED_FEATURE);
        RegistryEntryLookup<WorldPreset> worldPresets = registry.getOrThrow(RegistryKeys.WORLD_PRESET);

        Map<RegistryKey<DimensionOptions>, DimensionOptions> dimensions = worldPresets
            .getOrThrow(WorldPresets.DEFAULT)
            .value()
            .createDimensionsRegistryHolder()
            .dimensions();

        DimensionOptions dimensionOptions = switch (dimension) {
            case Overworld -> dimensions.get(DimensionOptions.OVERWORLD);
            case Nether -> dimensions.get(DimensionOptions.NETHER);
            case End -> dimensions.get(DimensionOptions.END);
        };
        ChunkGenerator chunkGenerator = dimensionOptions.chunkGenerator();

        Set<RegistryEntry<Biome>> biomes = dimensionOptions.chunkGenerator().getBiomeSource().getBiomes();
        List<RegistryEntry<Biome>> biomeEntries = biomes.stream().toList();

        List<PlacedFeatureIndexer.IndexedFeatures> indexer = PlacedFeatureIndexer.collectIndexedFeatures(
            biomeEntries,
            biome -> biome.value().getGenerationSettings().getFeatures(),
            true
        );

        Map<PlacedFeature, OreConfig> featureToOre = new HashMap<>();
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_COAL_LOWER, 6, coal, new Color(47, 44, 54), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_COAL_UPPER, 6, coal, new Color(47, 44, 54), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_IRON_MIDDLE, 6, iron, new Color(236, 173, 119), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_IRON_SMALL, 6, iron, new Color(236, 173, 119), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_IRON_UPPER, 6, iron, new Color(236, 173, 119), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_GOLD, 6, gold, new Color(247, 229, 30), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_GOLD_LOWER, 6, gold, new Color(247, 229, 30), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_GOLD_EXTRA, 6, gold, new Color(247, 229, 30), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_GOLD_NETHER, 7, gold, new Color(247, 229, 30), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_GOLD_DELTAS, 7, gold, new Color(247, 229, 30), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_REDSTONE, 6, redstone, new Color(245, 7, 23), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_REDSTONE_LOWER, 6, redstone, new Color(245, 7, 23), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_DIAMOND, 6, diamond, new Color(33, 244, 255), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_DIAMOND_BURIED, 6, diamond, new Color(33, 244, 255), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_DIAMOND_LARGE, 6, diamond, new Color(33, 244, 255), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_DIAMOND_MEDIUM, 6, diamond, new Color(33, 244, 255), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_LAPIS, 6, lapis, new Color(8, 26, 189), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_LAPIS_BURIED, 6, lapis, new Color(8, 26, 189), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_COPPER, 6, copper, new Color(239, 151, 0), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_COPPER_LARGE, 6, copper, new Color(239, 151, 0), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_EMERALD, 6, emerald, new Color(27, 209, 45), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_QUARTZ_NETHER, 7, quartz, new Color(205, 205, 205), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_QUARTZ_DELTAS, 7, quartz, new Color(205, 205, 205), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_DEBRIS_SMALL, 7, debris, new Color(209, 27, 245), chunkGenerator);
        registerOre(featureToOre, indexer, placedFeatures, OrePlacedFeatures.ORE_ANCIENT_DEBRIS_LARGE, 7, debris, new Color(209, 27, 245), chunkGenerator);

        Map<RegistryKey<Biome>, List<OreConfig>> biomeOreMap = new HashMap<>();
        for (RegistryEntry<Biome> biomeEntry : biomeEntries) {
            RegistryKey<Biome> biomeKey = biomeEntry.getKey().orElse(null);
            if (biomeKey == null) continue;

            List<OreConfig> ores = new ArrayList<>();
            for (RegistryEntryList<PlacedFeature> featureList : biomeEntry.value().getGenerationSettings().getFeatures()) {
                featureList.stream()
                    .map(RegistryEntry::value)
                    .filter(featureToOre::containsKey)
                    .forEach(feature -> ores.add(featureToOre.get(feature)));
            }
            biomeOreMap.put(biomeKey, ores);
        }

        return biomeOreMap;
    }

    private static void registerOre(
        Map<PlacedFeature, OreConfig> map,
        List<PlacedFeatureIndexer.IndexedFeatures> indexer,
        RegistryEntryLookup<PlacedFeature> placedFeatures,
        RegistryKey<PlacedFeature> oreKey,
        int generationStep,
        Setting<Boolean> active,
        Color color,
        ChunkGenerator chunkGenerator
    ) {
        PlacedFeature placedFeature = placedFeatures.getOrThrow(oreKey).value();
        int index = indexer.get(generationStep).indexMapping().applyAsInt(placedFeature);
        map.put(placedFeature, new OreConfig(placedFeature, generationStep, index, active, color, chunkGenerator));
    }
}
