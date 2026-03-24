package dev.cyber.meteor.utils.protection;

import java.util.Set;

/**
 * Identifies vanilla Minecraft keybinds and translation keys.
 * Used by protection Mixins to allow vanilla content through
 * while blocking mod identifiers in exploit contexts.
 */
public final class VanillaKeyRegistry {

    private VanillaKeyRegistry() {}

    // ── Vanilla keybind names (MC 1.21.x) ────────────────────────────────────

    private static final Set<String> VANILLA_KEYBINDS = Set.of(
        "key.attack", "key.use", "key.forward", "key.left", "key.right", "key.back",
        "key.jump", "key.sneak", "key.sprint", "key.drop", "key.inventory",
        "key.chat", "key.playerlist", "key.pickItem", "key.command",
        "key.socialInteractions", "key.advancements", "key.swapOffhand",
        "key.loadToolbarActivator", "key.saveToolbarActivator",
        "key.screenshot", "key.smoothCamera", "key.fullscreen",
        "key.spectatorOutlines", "key.togglePerspective", "key.narrator",
        "key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5",
        "key.hotbar.6", "key.hotbar.7", "key.hotbar.8", "key.hotbar.9"
    );

    // ── Minecraft-namespaced translation key prefixes (unambiguously vanilla) ──

    private static final String[] MC_PREFIXES = {
        "item.minecraft.", "block.minecraft.", "entity.minecraft.",
        "biome.minecraft.", "enchantment.minecraft.", "effect.minecraft.",
        "attribute.minecraft.", "painting.minecraft.", "instrument.minecraft.",
        "trim_material.minecraft.", "trim_pattern.minecraft.",
        "potion.minecraft.", "mob_effect.minecraft.",
        "item_group.minecraft.", "fluid.minecraft.",
        "advancement.minecraft.", "recipe.minecraft.", "loot_table.minecraft.",
        "banner_pattern.minecraft.", "wolf_variant.minecraft.",
        "cat_variant.minecraft.", "frog_variant.minecraft."
    };

    // ── Vanilla UI / system translation key prefixes ───────────────────────────

    private static final String[] UI_PREFIXES = {
        "gui.", "container.", "options.", "controls.", "soundCategory.",
        "narrator.", "chat.", "death.", "commands.", "stat.", "argument.",
        "gameMode.", "difficulty.", "structure_block.", "advancementType.",
        "book.", "menu.", "sign.", "pack.", "realms.", "demo.",
        "deathScreen.", "social.", "subtitles.", "disconnect.",
        "inventory.", "selectWorld.", "createWorld.", "editServer.",
        "addServer.", "spectatorMenu.", "chat_type.",
        "filled_map.", "screen.", "hud.", "gamerule.",
        "item_modifier.", "stat_type.", "accessibility.",
        "telemetry.", "profileEditor.", "onboarding."
    };

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Returns true if {@code key} is a known vanilla MC keybind identifier. */
    public static boolean isVanillaKeybind(String key) {
        return VANILLA_KEYBINDS.contains(key);
    }

    /**
     * Returns true if {@code key} is a vanilla Minecraft translation key.
     * Keybind keys ({@code key.*}) are checked against the vanilla keybind set.
     * All other keys are matched against vanilla namespace / UI prefixes.
     */
    public static boolean isVanillaTranslationKey(String key) {
        if (key == null) return true;
        if (key.startsWith("key.")) return VANILLA_KEYBINDS.contains(key);
        for (String p : MC_PREFIXES) { if (key.startsWith(p)) return true; }
        for (String p : UI_PREFIXES)  { if (key.startsWith(p)) return true; }
        return false;
    }
}
