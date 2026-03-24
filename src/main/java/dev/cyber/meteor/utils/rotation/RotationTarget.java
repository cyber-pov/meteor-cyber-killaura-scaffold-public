package dev.cyber.meteor.utils.rotation;

import meteordevelopment.meteorclient.systems.modules.Module;

public class RotationTarget {
    public final Module module;
    public final float yaw;
    public final float pitch;
    public final float minHorizontalTurnSpeed;
    public final float maxHorizontalTurnSpeed;
    public final float minVerticalTurnSpeed;
    public final float maxVerticalTurnSpeed;
    public final float resetThreshold;
    public final MovementCorrection movementCorrection;
    public final Runnable onReached;

    public RotationTarget(
        Module module,
        float yaw,
        float pitch,
        float minHorizontalTurnSpeed,
        float maxHorizontalTurnSpeed,
        float minVerticalTurnSpeed,
        float maxVerticalTurnSpeed,
        float resetThreshold,
        MovementCorrection movementCorrection,
        Runnable onReached
    ) {
        this.module = module;
        this.yaw = yaw;
        this.pitch = pitch;
        this.minHorizontalTurnSpeed = minHorizontalTurnSpeed;
        this.maxHorizontalTurnSpeed = maxHorizontalTurnSpeed;
        this.minVerticalTurnSpeed = minVerticalTurnSpeed;
        this.maxVerticalTurnSpeed = maxVerticalTurnSpeed;
        this.resetThreshold = resetThreshold;
        this.movementCorrection = movementCorrection;
        this.onReached = onReached;
    }
}
