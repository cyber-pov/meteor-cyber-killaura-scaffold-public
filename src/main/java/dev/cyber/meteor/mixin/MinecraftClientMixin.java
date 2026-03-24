package dev.cyber.meteor.mixin;

import dev.cyber.meteor.utils.protection.ExploitContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cleans up the exploit context when leaving exploitable screens.
 *
 * Closing to null is deferred by one tick to allow the UpdateSign / RenameItem
 * packet to finish serialising before the context is cleared.  Switching to a
 * non-exploitable screen exits immediately.
 *
 * Detection (entering context) is handled by screen constructor Mixins.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void cyber$cleanupExploitContext(Screen screen, CallbackInfo ci) {
        if (!ExploitContext.isActive()) return;

        if (screen == null) {
            // Closing — defer cleanup so in-flight packet serialisation still sees context
            ExploitContext.Source captured = ExploitContext.getSource();
            MinecraftClient.getInstance().execute(() -> {
                if (ExploitContext.getSource() == captured) ExploitContext.exit();
            });
        } else if (!(screen instanceof AbstractSignEditScreen) && !(screen instanceof AnvilScreen)) {
            // Switched to a non-exploitable screen
            ExploitContext.exit();
        }
        // If switching to another exploitable screen, that screen's constructor Mixin
        // will call ExploitContext.enter() which overwrites the source — correct behaviour.
    }
}
