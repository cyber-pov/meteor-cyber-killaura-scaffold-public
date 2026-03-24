package dev.cyber.meteor.mixin;

import dev.cyber.meteor.utils.protection.ExploitContext;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enters SIGN exploit context at the very start of AbstractSignEditScreen
 * construction — before the constructor body populates messages[] by calling
 * signText.getMessage(i, filtered).getString().
 *
 * Static injection is used so the method runs before 'this' is fully initialised.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin {

    @Inject(
        method = "<init>(Lnet/minecraft/block/entity/SignBlockEntity;ZZ)V",
        at = @At("HEAD")
    )
    private static void cyber$enterSignContext(
        SignBlockEntity blockEntity, boolean front, boolean filtered, CallbackInfo ci
    ) {
        if (MinecraftClient.getInstance().isIntegratedServerRunning()) return;
        ExploitContext.enter(ExploitContext.Source.SIGN);
    }
}
