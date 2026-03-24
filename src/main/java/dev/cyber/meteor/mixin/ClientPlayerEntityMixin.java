package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cyber.meteor.utils.rotation.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @ModifyExpressionValue(
        method = "sendMovementPackets",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"
        )
    )
    private float hookSilentRotationYaw(float original) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return original;
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;
        return RotationManager.INSTANCE.getCurrentYaw();
    }

    @ModifyExpressionValue(
        method = "sendMovementPackets",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"
        )
    )
    private float hookSilentRotationPitch(float original) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return original;
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;
        return RotationManager.INSTANCE.getCurrentPitch();
    }
}
