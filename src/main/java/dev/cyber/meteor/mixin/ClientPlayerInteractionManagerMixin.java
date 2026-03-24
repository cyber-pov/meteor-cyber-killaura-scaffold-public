package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cyber.meteor.utils.rotation.RotationManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @ModifyExpressionValue(
        method = "interactBlockInternal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"
        )
    )
    private float hookUseItemYaw(float original) {
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;
        return RotationManager.INSTANCE.getCurrentYaw();
    }

    @ModifyExpressionValue(
        method = "interactBlockInternal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;getPitch()F"
        )
    )
    private float hookUseItemPitch(float original) {
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;
        return RotationManager.INSTANCE.getCurrentPitch();
    }
}
