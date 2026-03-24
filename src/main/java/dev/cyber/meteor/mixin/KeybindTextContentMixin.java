package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.cyber.meteor.modules.player.Mc265322Fix;
import dev.cyber.meteor.utils.protection.ExploitContext;
import dev.cyber.meteor.utils.protection.VanillaKeyRegistry;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.text.KeybindTextContent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

/**
 * Blocks mod keybind display values from being resolved in exploit contexts.
 *
 * KeybindTextContent stores a Supplier<Text> whose get() returns the display
 * name of the bound key (e.g. "F5").  @WrapOperation intercepts that Supplier
 * call inside getTranslated() before the value can propagate to the packet.
 *
 * Blocked keybinds return their raw identifier as a literal (e.g. "key.mymod.zoom").
 * The server receives the key NAME but not the BOUND KEY — no mod fingerprinting.
 *
 * Vanilla keybinds (key.forward, key.attack, …) pass through unchanged.
 */
@Mixin(KeybindTextContent.class)
public abstract class KeybindTextContentMixin {

    @Shadow @Final private String key;

    @WrapOperation(
        method = "getTranslated",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"
        )
    )
    private Object cyber$interceptKeybind(Supplier<?> supplier, Operation<Object> original) {
        if (!ExploitContext.isActive()) return original.call(supplier);
        if (VanillaKeyRegistry.isVanillaKeybind(key)) return original.call(supplier);

        Mc265322Fix guard = Modules.get().get(Mc265322Fix.class);
        if (guard == null || !guard.isProtectionEnabled(ExploitContext.getSource())) {
            return original.call(supplier);
        }

        guard.notifyBlocked(key, ExploitContext.getSource());
        // Return the raw identifier — prevents the physical keybind from leaking
        return Text.literal(key);
    }
}
