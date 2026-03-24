package dev.cyber.meteor.mixin;

import dev.cyber.meteor.utils.protection.ExploitContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enters ANVIL exploit context at the start of AnvilScreen construction.
 *
 * The anvil probe places an item with a KeybindContents / TranslatableContents
 * display name in slot 0.  The client resolves this name when rendering the
 * rename text field.  By entering context here (before init() runs and the
 * text field is populated), our KeybindTextContentMixin / TranslatableTextContentMixin
 * will intercept those resolutions.
 *
 * Static injection avoids touching an uninitialised 'this'.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {

    @Inject(
        method = "<init>(Lnet/minecraft/screen/AnvilScreenHandler;Lnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/text/Text;)V",
        at = @At("HEAD")
    )
    private static void cyber$enterAnvilContext(
        AnvilScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci
    ) {
        if (MinecraftClient.getInstance().isIntegratedServerRunning()) return;
        ExploitContext.enter(ExploitContext.Source.ANVIL);
    }
}
