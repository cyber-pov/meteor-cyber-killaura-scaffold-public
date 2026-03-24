package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cyber.meteor.utils.rotation.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyExpressionValue(
        method = "jump",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"
        )
    )
    private float applySilentJumpYaw(float original) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return original;
        if ((Object) this != mc.player) return original;
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;

        return RotationManager.INSTANCE.getCurrentYaw();
    }

    @ModifyExpressionValue(
        method = "jump",
        at = @At(
            value = "NEW",
            target = "(DDD)Lnet/minecraft/util/math/Vec3d;"
        )
    )
    private Vec3d applySilentJumpVelocity(Vec3d original) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return original;
        if ((Object) this != mc.player) return original;
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;

        float yaw = RotationManager.INSTANCE.getCurrentYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw) * 0.2F, original.y, MathHelper.cos(yaw) * 0.2F);
    }
}
