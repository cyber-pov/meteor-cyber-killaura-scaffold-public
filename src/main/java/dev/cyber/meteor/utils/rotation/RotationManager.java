package dev.cyber.meteor.utils.rotation;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

public final class RotationManager {
    public static final RotationManager INSTANCE = new RotationManager();

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private RotationTarget targetRotation;
    private Module rotationOwner;
    private float currentRotationYaw;
    private float currentRotationPitch;
    private boolean initialized;
    private int transientSilentTicks;

    private RotationManager() {
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        if (!initialized) {
            resetToPlayerRotation();
            initialized = true;
        }

        if (targetRotation == null) {
            if (transientSilentTicks > 0) transientSilentTicks--;
            else resetToPlayerRotation();
            return;
        }

        stepTowardsTarget();

        if (hasReachedTarget()) {
            MovementCorrection movementCorrection = targetRotation.movementCorrection;
            Runnable onReached = targetRotation.onReached;
            targetRotation = null;

            transientSilentTicks = movementCorrection == MovementCorrection.SILENT ? 1 : 0;

            if (onReached != null) onReached.run();
        }
    }

    public void requestRotation(RotationTarget rotationTarget) {
        if (rotationTarget == null || mc.player == null) return;

        if (!initialized) {
            resetToPlayerRotation();
            initialized = true;
        }

        targetRotation = rotationTarget;
        rotationOwner = rotationTarget.module;
        transientSilentTicks = 0;
    }

    public void applyNow(float yaw, float pitch, Module owner) {
        if (!initialized) {
            resetToPlayerRotation();
            initialized = true;
        }
        float delta = MathHelper.wrapDegrees(yaw - currentRotationYaw);
        currentRotationYaw   = currentRotationYaw + delta;
        currentRotationPitch = MathHelper.clamp(pitch, -90f, 90f);
        rotationOwner        = owner;
        targetRotation       = null;
        transientSilentTicks = 2;
    }

    public void clearRequests(Module module) {
        if (rotationOwner == module) {
            targetRotation = null;
            rotationOwner = null;
            transientSilentTicks = 0;
            resetToPlayerRotation();
        }
    }

    public void reset() {
        targetRotation = null;
        rotationOwner = null;
        initialized = false;
        transientSilentTicks = 0;

        if (mc.player != null) {
            resetToPlayerRotation();
            initialized = true;
        }
    }

    public boolean isActive(Module module) {
        return targetRotation != null && targetRotation.module == module;
    }

    public boolean hasActiveMovementCorrection() {
        return initialized
            && rotationOwner != null
            && (!rotationMatchesPlayer() || targetRotation != null || transientSilentTicks > 0)
            && ((targetRotation != null && targetRotation.movementCorrection == MovementCorrection.SILENT) || transientSilentTicks > 0);
    }

    public float getCurrentYaw() {
        return currentRotationYaw;
    }

    public float getCurrentPitch() {
        return currentRotationPitch;
    }

    public boolean isWithinThreshold(float yaw, float pitch, float threshold) {
        float yawDiff = Math.abs(MathHelper.wrapDegrees(yaw - currentRotationYaw));
        float pitchDiff = Math.abs(pitch - currentRotationPitch);
        return yawDiff <= threshold && pitchDiff <= threshold;
    }

    public int estimateTicksToReach(RotationTarget rotationTarget) {
        if (rotationTarget == null || mc.player == null) return 0;

        float yaw = initialized && rotationOwner == rotationTarget.module ? currentRotationYaw : mc.player.getYaw();
        float pitch = initialized && rotationOwner == rotationTarget.module ? currentRotationPitch : mc.player.getPitch();
        int ticks = 0;

        while (ticks < 512) {
            float yawDiff = Math.abs(MathHelper.wrapDegrees(rotationTarget.yaw - yaw));
            float pitchDiff = Math.abs(rotationTarget.pitch - pitch);
            if (yawDiff <= rotationTarget.resetThreshold && pitchDiff <= rotationTarget.resetThreshold) {
                return ticks;
            }

            float yawDelta = MathHelper.wrapDegrees(rotationTarget.yaw - yaw);
            float pitchDelta = rotationTarget.pitch - pitch;
            double deltaLength = Math.hypot(yawDelta, pitchDelta);
            if (deltaLength <= 1.0E-6) return ticks;

            float straightLineYaw = (float) (Math.abs(yawDelta / deltaLength) * rotationTarget.minHorizontalTurnSpeed);
            float straightLinePitch = (float) (Math.abs(pitchDelta / deltaLength) * rotationTarget.minVerticalTurnSpeed);

            yaw += MathHelper.clamp(yawDelta, -straightLineYaw, straightLineYaw);
            pitch += MathHelper.clamp(pitchDelta, -straightLinePitch, straightLinePitch);
            ticks++;
        }

        return 512;
    }

    private boolean rotationMatchesPlayer() {
        if (mc.player == null) return true;

        float yawDiff = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - currentRotationYaw));
        float pitchDiff = Math.abs(mc.player.getPitch() - currentRotationPitch);
        return yawDiff <= 0.001f && pitchDiff <= 0.001f;
    }

    private void resetToPlayerRotation() {
        if (mc.player == null) return;

        currentRotationYaw = mc.player.getYaw();
        currentRotationPitch = mc.player.getPitch();
    }

    private boolean hasReachedTarget() {
        if (targetRotation == null) return false;

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetRotation.yaw - currentRotationYaw));
        float pitchDiff = Math.abs(targetRotation.pitch - currentRotationPitch);
        return yawDiff <= targetRotation.resetThreshold && pitchDiff <= targetRotation.resetThreshold;
    }

    private void stepTowardsTarget() {
        float yawDelta = MathHelper.wrapDegrees(targetRotation.yaw - currentRotationYaw);
        float pitchDelta = targetRotation.pitch - currentRotationPitch;

        double deltaLength = Math.hypot(yawDelta, pitchDelta);
        if (deltaLength <= 1.0E-6) {
            currentRotationYaw = targetRotation.yaw;
            currentRotationPitch = targetRotation.pitch;
            return;
        }

        float horizontalFactor = randomInRange(
            targetRotation.minHorizontalTurnSpeed,
            targetRotation.maxHorizontalTurnSpeed
        );
        float verticalFactor = randomInRange(
            targetRotation.minVerticalTurnSpeed,
            targetRotation.maxVerticalTurnSpeed
        );

        float straightLineYaw = (float) (Math.abs(yawDelta / deltaLength) * horizontalFactor);
        float straightLinePitch = (float) (Math.abs(pitchDelta / deltaLength) * verticalFactor);

        currentRotationYaw += MathHelper.clamp(yawDelta, -straightLineYaw, straightLineYaw);
        currentRotationPitch += MathHelper.clamp(pitchDelta, -straightLinePitch, straightLinePitch);
    }

    private float randomInRange(float min, float max) {
        if (max <= min) return min;
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }
}
