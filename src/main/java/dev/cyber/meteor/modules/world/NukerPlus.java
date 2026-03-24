package dev.cyber.meteor.modules.world;

import dev.cyber.meteor.CyberCategories;
import dev.cyber.meteor.utils.rotation.RotationManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Grim-safe Nuker.
 *
 * FastBreak bypass  : waits exact break ticks + enforces 6-tick minimum between blocks (≥275ms).
 * AirLiquidBreak    : blacklists recently broken positions for 5 ticks; skips fluids.
 * Post bypass       : all packets sent in TickEvent.Pre (before flying packet).
 * Simulation bypass : RotationManager.applyNow() — AimModulo360-safe silent rotation.
 * PositionBreakB    : ABORT always sent with face=DOWN (releaseFace=0).
 */
public class NukerPlus extends Module {

    // Grim requires ≥275ms between FINISHED_DIGGING and next START_DIGGING.
    // 275ms / 50ms = 5.5 → ceil = 6 ticks minimum.
    private static final int MIN_BREAK_DELAY = 6;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range").description("Block break range.").defaultValue(4.0).min(1.0).sliderMax(6.0).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Extra ticks between blocks (added on top of 6-tick minimum).").defaultValue(0).min(0).sliderMax(20).build());

    private final Setting<Boolean> filterType = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-type").description("Only break the same block type as the last manually left-clicked block.")
        .defaultValue(true).build());

    private final Setting<Boolean> renderTarget = sgRender.add(new BoolSetting.Builder()
        .name("render-target").description("Highlight the block currently being broken.")
        .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").defaultValue(ShapeMode.Both).build());

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 80, 80, 255)).build());

    private final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color").defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 80, 80, 40)).build());

    // ---- State ----
    private BlockPos  breakTarget;
    private Direction breakFace;
    private int       breakTicks;
    private int       ticksNeeded;
    private int       delayTimer;
    private boolean   breaking;

    // Filter: only break this block type (set by left-clicking a block)
    private Block     filterBlock = null;
    private boolean   wasAttackPressed = false;

    // Recently broken positions → skip for 5 ticks to avoid AirLiquidBreak
    private final Map<BlockPos, Integer> recentlyBroken = new HashMap<>();

    public NukerPlus() {
        super(CyberCategories.CYBER, "Nuker+", "Grim-bypass nuker.");
    }

    @Override
    public void onDeactivate() {
        if (breaking && breakTarget != null) {
            // face=DOWN (ID=0) = Grim's releaseFace → does NOT set lastFace → no PositionBreakB
            sendDig(breakTarget, Direction.DOWN, PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK);
        }
        reset();
        recentlyBroken.clear();
        wasAttackPressed = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Detect manual left-click on a block to set filter type
        boolean attackPressed = mc.options.attackKey.isPressed();
        if (filterType.get() && attackPressed && !wasAttackPressed) {
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos clicked = ((BlockHitResult) mc.crosshairTarget).getBlockPos();
                filterBlock = mc.world.getBlockState(clicked).getBlock();
            }
        }
        wasAttackPressed = attackPressed;

        // Age out blacklisted positions
        recentlyBroken.entrySet().removeIf(e -> e.getValue() <= 0);
        recentlyBroken.replaceAll((pos, ticks) -> ticks - 1);

        if (delayTimer > 0) { delayTimer--; return; }

        // While breaking, keep the current target if it's still valid — don't switch to a
        // closer block mid-break (would abort every tick while walking, never finishing).
        BlockPos target;
        if (breaking) {
            target = isStillValid(breakTarget) ? breakTarget : null;
        } else {
            target = findTarget();
        }

        // Target lost → abort
        // face=DOWN (ID=0) = Grim's releaseFace → does NOT set lastFace → no PositionBreakB
        if (breaking && target == null) {
            sendDig(breakTarget, Direction.DOWN, PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK);
            reset();
        }

        if (target == null) return;

        aimAt(target);

        BlockState state = mc.world.getBlockState(target);
        float damage  = state.calcBlockBreakingDelta(mc.player, mc.world, target);
        int   needed  = damage >= 1f ? 1 : Math.max(1, (int) Math.ceil(1.0 / damage));

        // Begin break
        if (!breaking) {
            breakTarget = target;
            breakFace   = closestFace(target);
            ticksNeeded = needed;
            breakTicks  = 0;
            sendDig(target, breakFace, PlayerActionC2SPacket.Action.START_DESTROY_BLOCK);
            mc.player.swingHand(Hand.MAIN_HAND);
            breaking = true;
            return; // never STOP in the same tick as START — Grim requires ≥50ms gap
        }

        // Swing every tick while breaking (Grim tracks animation packets for break-time prediction)
        mc.player.swingHand(Hand.MAIN_HAND);
        breakTicks++;

        // Complete
        if (breakTicks >= ticksNeeded) {
            sendDig(breakTarget, breakFace, PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK);
            recentlyBroken.put(breakTarget, 5); // blacklist for 5 ticks
            delayTimer = MIN_BREAK_DELAY + delay.get();
            reset();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderTarget.get() || breakTarget == null || !breaking) return;
        event.renderer.box(
            breakTarget.getX(), breakTarget.getY(), breakTarget.getZ(),
            breakTarget.getX() + 1, breakTarget.getY() + 1, breakTarget.getZ() + 1,
            sideColor.get(), lineColor.get(), shapeMode.get(), 0
        );
    }

    // ---- Helpers ----

    /** Returns true if the current break target is still within range and breakable. */
    private boolean isStillValid(BlockPos pos) {
        if (pos == null) return false;
        Vec3d eye = mc.player.getEyePos();
        double r = range.get();
        if (eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > r * r) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.getHardness(mc.world, pos) < 0) return false;
        if (!state.getFluidState().isEmpty()) return false;
        return true;
    }

    private BlockPos findTarget() {
        Vec3d eye = mc.player.getEyePos();
        double r  = range.get();
        int    ri = (int) Math.ceil(r);
        BlockPos origin = mc.player.getBlockPos();
        List<BlockPos> candidates = new ArrayList<>();

        // filter-type ON but no block clicked yet → break nothing
        if (filterType.get() && filterBlock == null) return null;
        Block filter = filterType.get() ? filterBlock : null;

        for (int x = -ri; x <= ri; x++) {
            for (int y = -ri; y <= ri; y++) {
                for (int z = -ri; z <= ri; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (eye.squaredDistanceTo(Vec3d.ofCenter(pos)) > r * r) continue;
                    if (recentlyBroken.containsKey(pos)) continue;
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getHardness(mc.world, pos) < 0) continue; // bedrock
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) continue; // water / lava
                    if (filter != null && state.getBlock() != filter) continue;
                    candidates.add(pos);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(p -> eye.squaredDistanceTo(Vec3d.ofCenter(p))));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void aimAt(BlockPos pos) {
        Vec3d eye    = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        float yaw   = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        RotationManager.INSTANCE.applyNow(yaw, pitch, this);
    }

    private Direction closestFace(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Direction best     = Direction.UP;
        double    bestDist = Double.MAX_VALUE;
        for (Direction dir : Direction.values()) {
            Vec3d face = Vec3d.ofCenter(pos).add(dir.getOffsetX() * 0.5, dir.getOffsetY() * 0.5, dir.getOffsetZ() * 0.5);
            double d = face.squaredDistanceTo(eye);
            if (d < bestDist) { bestDist = d; best = dir; }
        }
        return best;
    }

    private void sendDig(BlockPos pos, Direction face, PlayerActionC2SPacket.Action action) {
        if (mc.interactionManager == null || mc.world == null) return;
        mc.interactionManager.sendSequencedPacket(mc.world,
            sequence -> new PlayerActionC2SPacket(action, pos, face, sequence));
    }

    private void reset() {
        breakTarget = null;
        breakFace   = null;
        breakTicks  = 0;
        ticksNeeded = 0;
        breaking    = false;
    }
}
