package dev.cyber.meteor.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.cyber.meteor.utils.rotation.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @ModifyExpressionValue(method = "tick", at = @At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;"))
    private PlayerInput applyMovementCorrection(PlayerInput original) {
        if (mc.player == null) return original;
        if (!RotationManager.INSTANCE.hasActiveMovementCorrection()) return original;

        return correctMovement(original, mc.player.getYaw(), RotationManager.INSTANCE.getCurrentYaw());
    }

    private static PlayerInput correctMovement(PlayerInput input, float playerYaw, float correctionYaw) {
        int forward = axis(input.forward(), input.backward());
        int sideways = axis(input.left(), input.right());

        if (forward == 0 && sideways == 0) return input;

        float deltaYaw = playerYaw - correctionYaw;
        float radians = deltaYaw * MathHelper.RADIANS_PER_DEGREE;

        float newSideways = sideways * MathHelper.cos(radians) - forward * MathHelper.sin(radians);
        float newForward = forward * MathHelper.cos(radians) + sideways * MathHelper.sin(radians);

        int correctedForward = Math.round(newForward);
        int correctedSideways = Math.round(newSideways);

        return new PlayerInput(
            correctedForward > 0,
            correctedForward < 0,
            correctedSideways > 0,
            correctedSideways < 0,
            input.jump(),
            input.sneak(),
            input.sprint()
        );
    }

    private static int axis(boolean positive, boolean negative) {
        if (positive == negative) return 0;
        return positive ? 1 : -1;
    }
}
