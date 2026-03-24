package dev.cyber.meteor.mixin.baritone;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.pathing.movement.CalculationContext;
import baritone.process.MineProcess;
import dev.cyber.meteor.modules.render.OreReveal;
import dev.cyber.meteor.modules.render.OreSim;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = MineProcess.class, remap = false)
public abstract class MineProcessMixin {

    @Shadow(remap = false)
    private List<BlockPos> knownOreLocations;

    /**
     * Replace Baritone's world scan with OreReveal / OreSim positions.
     */
    @Inject(
        method = "rescan(Ljava/util/List;Lbaritone/pathing/movement/CalculationContext;)V",
        at = @At("HEAD"), cancellable = true, remap = false
    )
    private void hookRescan(List<BlockPos> already, CalculationContext context, CallbackInfo ci) {
        OreSim oreSim = Modules.get().get(OreSim.class);
        if (oreSim != null && oreSim.baritone()) {
            knownOreLocations = oreSim.getOreGoals();
            ci.cancel();
            return;
        }

        OreReveal oreReveal = Modules.get().get(OreReveal.class);
        if (oreReveal != null && oreReveal.baritone()) {
            knownOreLocations = oreReveal.getOreGoals();
            ci.cancel();
        }
    }

    /**
     * Skip addNearby() entirely when OreReveal/OreSim is active.
     * This prevents prune() from removing Anti-Xray-hidden ores
     * (which now show as stone in the world) from knownOreLocations.
     */
    @Inject(
        method = "addNearby()Z",
        at = @At("HEAD"), cancellable = true, remap = false
    )
    private void hookAddNearby(CallbackInfoReturnable<Boolean> cir) {
        OreSim oreSim = Modules.get().get(OreSim.class);
        if (oreSim != null && oreSim.baritone()) {
            cir.setReturnValue(true);
            return;
        }

        OreReveal oreReveal = Modules.get().get(OreReveal.class);
        if (oreReveal != null && oreReveal.baritone()) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Bypass prune() in updateGoal() when OreReveal/OreSim is active.
     * prune() removes positions where filter.has(actual_block) is false —
     * which is ALL our Anti-Xray-hidden positions (block shows as stone).
     * Instead, build the goal directly from knownOreLocations.
     */
    @Inject(
        method = "updateGoal()Lbaritone/api/process/PathingCommand;",
        at = @At("HEAD"), cancellable = true, remap = false
    )
    private void hookUpdateGoal(CallbackInfoReturnable<PathingCommand> cir) {
        OreSim oreSim = Modules.get().get(OreSim.class);
        OreReveal oreReveal = Modules.get().get(OreReveal.class);
        boolean active = (oreSim != null && oreSim.baritone())
                      || (oreReveal != null && oreReveal.baritone());
        if (!active) return;

        List<BlockPos> locs = knownOreLocations;
        if (locs == null || locs.isEmpty()) {
            cir.setReturnValue(null);
            return;
        }

        Goal[] goals = locs.stream()
            .map(pos -> (Goal) new GoalBlock(pos.getX(), pos.getY(), pos.getZ()))
            .toArray(Goal[]::new);
        cir.setReturnValue(new PathingCommand(new GoalComposite(goals), PathingCommandType.REVALIDATE_GOAL_AND_PATH));
    }
}
