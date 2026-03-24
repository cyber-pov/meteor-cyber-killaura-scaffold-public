package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cyber.meteor.utils.rotation.RotationManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Ensures that the rotation embedded in the PlayerInteractBlockC2SPacket
 * matches the rotation sent in the movement packet (hooked in ClientPlayerEntityMixin).
 * Without this, Grim's BadPacketsJ check fires because the movement packet uses
 * RotationManager's silent rotation while the interact packet uses the player's actual rotation.
 *
 * In 1.21.11, getYaw()/getPitch() are called in interactBlockInternal() on PlayerEntity,
 * not in interactBlock() on ClientPlayerEntity.
 */
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
