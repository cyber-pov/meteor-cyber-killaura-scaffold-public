package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cyber.meteor.modules.movement.SprintPlus;
import dev.cyber.meteor.utils.rotation.RotationManager;
import meteordevelopment.meteorclient.systems.modules.Modules;
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
        // Return the continuous (non-wrapped) yaw maintained by RotationManager.
        // applyNow() accumulates via delta, so this value is always near the last
        // sent yaw — no ±360 jump → no AimModulo360.
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

    @ModifyExpressionValue(
        method = "tickMovement",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/PlayerInput;sprint()Z"
        )
    )
    private boolean hookAutoSprintKey(boolean original) {
        SprintPlus sprintPlus = Modules.get().get(SprintPlus.class);
        if (sprintPlus == null || !sprintPlus.isActive()) return original;
        return original || sprintPlus.shouldSprintVanillaFlow();
    }
}
