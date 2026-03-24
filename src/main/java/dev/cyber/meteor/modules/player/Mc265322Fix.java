package dev.cyber.meteor.modules.player;

import dev.cyber.meteor.CyberCategories;
import dev.cyber.meteor.utils.protection.ExploitContext;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * MC-265322 key-leak protection — component level.
 *
 * Intercepts TranslatableTextContent and KeybindTextContent resolution
 * inside sign / anvil screens before the translated string is sent back
 * to the server.  This mirrors OpSec's approach: block at the source
 * rather than at the packet, so the server never obtains display values
 * that reveal installed mods or custom keybindings.
 */
public class Mc265322Fix extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> protectSigns = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-signs")
        .description("Block mod translation/keybind probes in sign editor screens.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> protectAnvils = sgGeneral.add(new BoolSetting.Builder()
        .name("protect-anvils")
        .description("Block mod translation/keybind probes in anvil screens.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Show a chat message when a probe key is blocked.")
        .defaultValue(true)
        .build()
    );

    public Mc265322Fix() {
        super(CyberCategories.CYBER, "mc265322-fix", "Blocks MC-265322 translation and keybind leak exploits.");
    }

    /** Returns true if protection is active for the given exploit source. */
    public boolean isProtectionEnabled(ExploitContext.Source source) {
        if (!isActive() || source == null) return false;
        return source == ExploitContext.Source.SIGN ? protectSigns.get() : protectAnvils.get();
    }

    /** Send a chat notification when a mod key is blocked. */
    public void notifyBlocked(String key, ExploitContext.Source source) {
        if (!isActive() || !notify.get() || mc.player == null) return;
        String src = source == ExploitContext.Source.SIGN ? "Sign" : "Anvil";
        mc.player.sendMessage(
            Text.literal("[")
                .formatted(Formatting.DARK_GRAY)
                .append(Text.literal("mc265322").formatted(Formatting.AQUA))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal("BLOCKED ").formatted(Formatting.RED))
                .append(Text.literal(src + ": ").formatted(Formatting.WHITE))
                .append(Text.literal(key).formatted(Formatting.GRAY)),
            false
        );
    }
}
