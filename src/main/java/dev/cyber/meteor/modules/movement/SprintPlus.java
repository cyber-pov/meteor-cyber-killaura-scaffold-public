package dev.cyber.meteor.modules.movement;

import dev.cyber.meteor.CyberCategories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.GUIMove;

public class SprintPlus
extends Module {
    public SprintPlus() {
        super(CyberCategories.CYBER, "sprint+", "Automatically sprints with vanilla-like conditions.", new String[]{"sprint-plus", "sprintplus"});
    }

    public boolean shouldSprintVanillaFlow() {
        if (this.mc.player == null) return false;
        if (this.mc.currentScreen != null && !((Boolean)((GUIMove)Modules.get().get(GUIMove.class)).sprint.get()).booleanValue()) {
            return false;
        }
        if (this.mc.player.hasVehicle()) {
            return false;
        }
        if (this.mc.player.isSneaking()) {
            return false;
        }
        if (this.mc.player.isUsingItem()) {
            return false;
        }
        if (this.mc.player.hasBlindnessEffect()) {
            return false;
        }
        if (!this.mc.player.getHungerManager().canSprint()) {
            return false;
        }
        if (!this.mc.player.input.hasForwardMovement()) {
            return false;
        }
        return !this.mc.player.horizontalCollision || this.mc.player.collidedSoftly;
    }
}
