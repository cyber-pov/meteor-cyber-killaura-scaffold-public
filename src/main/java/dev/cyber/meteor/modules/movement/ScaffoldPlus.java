package dev.cyber.meteor.modules.movement;

import dev.cyber.meteor.CyberCategories;
import dev.cyber.meteor.utils.rotation.RotationManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Scaffold — Technique=Normal, MovementCorrection=Silent, RotationTiming=Normal.
 * Matches the LiquidBounce config verified to bypass Grim 2.0.
 *
 * Rotation approach (SILENT):
 *   applyNow() feeds the scaffold yaw/pitch to RotationManager in TickEvent.Pre
 *   WITHOUT touching mc.player.setYaw().  Four Mixins handle the rest:
 *     ClientPlayerEntityMixin   — sends RotationManager yaw/pitch in PlayerMove packets
 *     ClientPlayerInteractionManagerMixin — embeds RotationManager yaw/pitch in USE_ITEM
 *     EntityMixin               — uses RotationManager yaw in updateVelocity()
 *     KeyboardInputMixin        — corrects WASD input for the visual↔scaffold yaw delta
 *   The player camera never visibly moves (true silent rotation).
 *
 * AimModulo360 fix (preserved from RotationManager.applyNow):
 *   currentRotationYaw += wrapDegrees(target − currentRotationYaw)
 *   Consecutive sent yaw values differ by ≤180° → Grim AimModulo360 cannot fire.
 */
public class ScaffoldPlus extends Module {
    private static final double PLACE_RANGE   = 4.5;
    private static final double EDGE_STRENGTH = 0.48;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Selected blocks.")
        .build()
    );

    private final Setting<ListMode> blocksFilter = sgGeneral.add(new EnumSetting.Builder<ListMode>()
        .name("blocks-filter")
        .description("How to use the block list setting.")
        .defaultValue(ListMode.Blacklist)
        .build()
    );

    public ScaffoldPlus() {
        super(CyberCategories.CYBER, "scaffold+", "Places blocks under you.");
    }

    @Override
    public void onDeactivate() {
        RotationManager.INSTANCE.clearRequests(this);
    }

    // ── Main tick ──────────────────────────────────────────────────────────────

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        PlacementPlan plan = findPlacementPlan();
        if (plan == null) {
            RotationManager.INSTANCE.clearRequests(this);
            return;
        }

        // Small randomisation mimics human micro-movements.
        float jitYaw   = plan.yaw   + (float) ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
        float jitPitch = plan.pitch + (float) ThreadLocalRandom.current().nextDouble(-0.3, 0.3);

        // SILENT rotation: RotationManager accumulates yaw via delta internally.
        // mc.player.getYaw() is NOT changed — camera stays at visual direction.
        // ClientPlayerEntityMixin sends scaffold yaw in PlayerMove.
        // ClientPlayerInteractionManagerMixin puts scaffold yaw in USE_ITEM.
        // EntityMixin uses scaffold yaw in updateVelocity().
        // KeyboardInputMixin corrects WASD for the yaw delta.
        RotationManager.INSTANCE.applyNow(jitYaw, jitPitch, this);

        BlockHitResult hit = new BlockHitResult(plan.hitPos, plan.direction, plan.interactedPos, false);
        executePlacement(plan, hit);
    }

    // ── Plan computation ──────────────────────────────────────────────────────

    private PlacementPlan findPlacementPlan() {
        BlockPos blockPos = mc.player.getBlockPos();
        double frac = mc.player.getY() - blockPos.getY();

        boolean ascending = !mc.player.isOnGround() && mc.player.getVelocity().y > 0;
        int targetY = (!ascending && frac >= 0.1) ? blockPos.getY() : blockPos.getY() - 1;

        BlockPos candidate = new BlockPos(blockPos.getX(), targetY, blockPos.getZ());
        if (!BlockUtils.canPlace(candidate)) return null;

        FindItemResult item = InvUtils.findInHotbar(stack -> validItem(stack, candidate));
        if (!item.found()) return null;

        return createPlan(candidate, item);
    }

    private PlacementPlan createPlan(BlockPos targetPos, FindItemResult item) {
        List<PlacementPlan> options = new ArrayList<>();

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = targetPos.offset(dir.getOpposite());
            BlockState neighborState = mc.world.getBlockState(neighbor);

            if (neighborState.isAir() || neighborState.isReplaceable()) continue;
            if (!neighborState.getFluidState().isEmpty()) continue;

            Vec3d hitPos = facePoint(neighbor, dir);
            float yaw    = computeYaw(hitPos);
            float pitch  = computePitch(hitPos);

            BlockHitResult check = raycast(yaw, pitch);
            if (!faceMatches(check, neighbor, dir)) continue;

            options.add(new PlacementPlan(targetPos, neighbor, dir, hitPos, yaw, pitch, item));
        }

        if (options.isEmpty()) return null;

        float playerYaw   = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();
        options.sort(Comparator.comparingDouble(p -> angleDist(playerYaw, playerPitch, p.yaw, p.pitch)));
        return options.getFirst();
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private boolean faceMatches(BlockHitResult hit, BlockPos pos, Direction dir) {
        return hit != null
            && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(pos)
            && hit.getSide() == dir;
    }

    private Vec3d facePoint(BlockPos pos, Direction dir) {
        Box box = shapeBox(pos);
        Vec3d faceCenter = switch (dir) {
            case UP    -> new Vec3d((box.minX + box.maxX) * 0.5, box.maxY,                     (box.minZ + box.maxZ) * 0.5);
            case DOWN  -> new Vec3d((box.minX + box.maxX) * 0.5, box.minY,                     (box.minZ + box.maxZ) * 0.5);
            case NORTH -> new Vec3d((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, box.minZ);
            case SOUTH -> new Vec3d((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, box.maxZ);
            case WEST  -> new Vec3d(box.minX,                     (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
            case EAST  -> new Vec3d(box.maxX,                     (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
        };

        Vec3d eyes = mc.player.getEyePos();
        double x = faceCenter.x, y = faceCenter.y, z = faceCenter.z;

        switch (dir.getAxis()) {
            case X -> { y += MathHelper.clamp(eyes.y - faceCenter.y, -EDGE_STRENGTH, EDGE_STRENGTH);
                        z += MathHelper.clamp(eyes.z - faceCenter.z, -EDGE_STRENGTH, EDGE_STRENGTH); }
            case Y -> { x += MathHelper.clamp(eyes.x - faceCenter.x, -EDGE_STRENGTH, EDGE_STRENGTH);
                        z += MathHelper.clamp(eyes.z - faceCenter.z, -EDGE_STRENGTH, EDGE_STRENGTH); }
            case Z -> { x += MathHelper.clamp(eyes.x - faceCenter.x, -EDGE_STRENGTH, EDGE_STRENGTH);
                        y += MathHelper.clamp(eyes.y - faceCenter.y, -EDGE_STRENGTH, EDGE_STRENGTH); }
        }
        return new Vec3d(x, y, z);
    }

    private BlockHitResult raycast(float yaw, float pitch) {
        Vec3d from = mc.player.getEyePos();
        Vec3d to   = from.add(Vec3d.fromPolar(pitch, yaw).multiply(PLACE_RANGE));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(
            from, to,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private void executePlacement(PlacementPlan plan, BlockHitResult hitResult) {
        Hand hand;
        if (plan.item.isOffhand()) {
            hand = Hand.OFF_HAND;
        } else {
            if (mc.player.getInventory().getSelectedSlot() != plan.item.slot())
                InvUtils.swap(plan.item.slot(), false);
            hand = Hand.MAIN_HAND;
        }
        ActionResult result = mc.interactionManager.interactBlock(mc.player, hand, hitResult);
        if (result.isAccepted()) mc.player.swingHand(hand);
    }

    private Box shapeBox(BlockPos pos) {
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (!shape.isEmpty()) return shape.getBoundingBox().offset(pos);
        return new Box(pos.getX(), pos.getY(), pos.getZ(),
                       pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    private boolean validItem(ItemStack stack, BlockPos pos) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (blocksFilter.get() == ListMode.Blacklist && blocks.get().contains(block)) return false;
        if (blocksFilter.get() == ListMode.Whitelist && !blocks.get().contains(block)) return false;
        if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, pos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.canFallThrough(mc.world.getBlockState(pos));
    }

    private float computeYaw(Vec3d pos) {
        return MathHelper.wrapDegrees(
            (float) (Math.toDegrees(Math.atan2(pos.z - mc.player.getZ(), pos.x - mc.player.getX())) - 90.0)
        );
    }

    private float computePitch(Vec3d pos) {
        double dx = pos.x - mc.player.getX();
        double dy = pos.y - mc.player.getEyeY();
        double dz = pos.z - mc.player.getZ();
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    private double angleDist(float y1, float p1, float y2, float p2) {
        return Math.abs(MathHelper.wrapDegrees(y2 - y1)) + Math.abs(p2 - p1);
    }

    public enum ListMode { Whitelist, Blacklist }

    private record PlacementPlan(
        BlockPos targetPos,
        BlockPos interactedPos,
        Direction direction,
        Vec3d hitPos,
        float yaw,
        float pitch,
        FindItemResult item
    ) {}
}
