package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cyber.meteor.utils.rotation.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @ModifyExpressionValue(
        method = "updateVelocity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;getYaw()F"
        )
    )
    private float applySilentVelocityYaw(float original) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return original;
        if ((Object) this != mc.player) return original;
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;

        return RotationManager.INSTANCE.getCurrentYaw();
    }
}
