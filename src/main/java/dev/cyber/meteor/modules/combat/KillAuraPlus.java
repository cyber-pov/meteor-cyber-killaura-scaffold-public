package dev.cyber.meteor.modules.combat;

import dev.cyber.meteor.CyberCategories;
import dev.cyber.meteor.utils.rotation.MovementCorrection;
import dev.cyber.meteor.utils.rotation.RotationManager;
import dev.cyber.meteor.utils.rotation.RotationTarget;
import dev.cyber.meteor.utils.rotation.RotationTiming;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class KillAuraPlus extends Module {
    private static final double[] AIM_POINT_SAMPLES = {0.05, 0.15, 0.25, 0.35, 0.45, 0.55, 0.65, 0.75, 0.85, 0.95};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgRotations = settings.createGroup("Rotations");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    private final Setting<AttackItems> attackWhenHolding = sgGeneral.add(new EnumSetting.Builder<AttackItems>()
        .name("attack-when-holding")
        .description("Only attacks an entity when a specified item is in your hand.")
        .defaultValue(AttackItems.Weapons)
        .build()
    );

    private final Setting<List<Item>> weapons = sgGeneral.add(new ItemListSetting.Builder()
        .name("selected-weapon-types")
        .description("Which types of weapons to attack with.")
        .defaultValue(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.TRIDENT)
        .filter(FILTER::contains)
        .visible(() -> attackWhenHolding.get() == AttackItems.Weapons)
        .build()
    );

    private final Setting<Boolean> autoSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to an acceptable weapon when attacking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swapBack = sgGeneral.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Swaps back to previous slot after attacking.")
        .defaultValue(false)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> onlyOnClick = sgGeneral.add(new BoolSetting.Builder()
        .name("only-on-click")
        .description("Only attacks when holding left click.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnCombat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-baritone")
        .description("Freezes Baritone while fighting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to sort valid targets.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Integer> maxTargets = sgTargeting.add(new IntSetting.Builder()
        .name("max-targets")
        .description("How many entities to target at once.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 5)
        .build()
    );

    private final Setting<Double> rangeIncrease = sgTargeting.add(new DoubleSetting.Builder()
        .name("range-increase")
        .description("Extra attack range added on top of the player's interaction range.")
        .defaultValue(0)
        .min(0)
        .sliderMax(5)
        .build()
    );

    private final Setting<Double> wallsRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("The maximum range the entity can be attacked through walls.")
        .defaultValue(3)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> scanRangeIncrease = sgTargeting.add(new DoubleSetting.Builder()
        .name("scan-range-increase")
        .description("Extra range used for scanning targets before they are in hit range.")
        .defaultValue(2.5)
        .min(0)
        .sliderMax(7)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Does not attack mobs with a custom name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-passive")
        .description("Only attacks passive mobs (Enderman, Piglin, Wolf) if they are targeting you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreInvisible = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-invisible")
        .description("Does not attack invisible entities.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rotate = sgRotations.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates silently towards the target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<MovementCorrection> movementCorrection = sgRotations.add(new EnumSetting.Builder<MovementCorrection>()
        .name("movement-correction")
        .description("How movement is corrected while rotating.")
        .defaultValue(MovementCorrection.SILENT)
        .visible(rotate::get)
        .build()
    );

    private final Setting<RotationTiming> rotationTiming = sgRotations.add(new EnumSetting.Builder<RotationTiming>()
        .name("rotation-timing")
        .description("When rotations should be applied.")
        .defaultValue(RotationTiming.Normal)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> minHorizontalTurnSpeed = sgRotations.add(new DoubleSetting.Builder()
        .name("min-horizontal-turn-speed")
        .description("Minimum horizontal turn speed in degrees per tick.")
        .defaultValue(180)
        .min(1)
        .sliderMax(180)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> maxHorizontalTurnSpeed = sgRotations.add(new DoubleSetting.Builder()
        .name("max-horizontal-turn-speed")
        .description("Maximum horizontal turn speed in degrees per tick.")
        .defaultValue(180)
        .min(1)
        .sliderMax(180)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> minVerticalTurnSpeed = sgRotations.add(new DoubleSetting.Builder()
        .name("min-vertical-turn-speed")
        .description("Minimum vertical turn speed in degrees per tick.")
        .defaultValue(180)
        .min(1)
        .sliderMax(180)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> maxVerticalTurnSpeed = sgRotations.add(new DoubleSetting.Builder()
        .name("max-vertical-turn-speed")
        .description("Maximum vertical turn speed in degrees per tick.")
        .defaultValue(180)
        .min(1)
        .sliderMax(180)
        .visible(rotate::get)
        .build()
    );

    private final Setting<Double> rotationThreshold = sgRotations.add(new DoubleSetting.Builder()
        .name("rotation-threshold")
        .description("Maximum difference before hits wait for the rotation to line up.")
        .defaultValue(2)
        .min(0.1)
        .sliderMax(20)
        .visible(rotate::get)
        .build()
    );

    private final Setting<RaycastMode> raycast = sgRotations.add(new EnumSetting.Builder<RaycastMode>()
        .name("raycast")
        .description("Whether to prioritize entities directly under your server-side crosshair.")
        .defaultValue(RaycastMode.NONE)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Does not attack while using an item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnCA = sgTiming.add(new BoolSetting.Builder()
        .name("pause-on-CA")
        .description("Does not attack while Crystal Aura is placing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgTiming.add(new BoolSetting.Builder()
        .name("TPS-sync")
        .description("Tries to sync attack delay with the server TPS.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> customDelay = sgTiming.add(new BoolSetting.Builder()
        .name("custom-delay")
        .description("Use a custom delay instead of vanilla cooldown.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hitDelay = sgTiming.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("How fast you hit in ticks.")
        .defaultValue(11)
        .min(0)
        .sliderMax(60)
        .visible(customDelay::get)
        .build()
    );

    private final Setting<Integer> switchDelay = sgTiming.add(new IntSetting.Builder()
        .name("switch-delay")
        .description("How many ticks to wait after switching hotbar slots.")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private static final ArrayList<Item> FILTER = new ArrayList<>(List.of(
        Items.DIAMOND_SWORD,
        Items.DIAMOND_AXE,
        Items.DIAMOND_PICKAXE,
        Items.DIAMOND_SHOVEL,
        Items.DIAMOND_HOE,
        Items.MACE,
        Items.DIAMOND_SPEAR,
        Items.TRIDENT
    ));

    private final List<Entity> targets = new ArrayList<>();

    private int switchTimer;
    private int hitTimer;
    private boolean wasPathing;

    public boolean attacking;
    public boolean swapped;
    public static int previousSlot;
    private int previousAimEntityId = -1;
    private Vec3d previousAimPoint;
    private int previousAimTicks;
    private int snapTargetEntityId = -1;
    private int snapKeepTicks;

    public KillAuraPlus() {
        super(CyberCategories.CYBER, "kill-aura+", "KillAura with LiquidBounce-style rotate timing.");
    }

    @Override
    public void onActivate() {
        previousSlot = -1;
        swapped = false;
        hitTimer = 0;
        switchTimer = 0;
        attacking = false;
        previousAimEntityId = -1;
        previousAimPoint = null;
        previousAimTicks = 0;
        snapTargetEntityId = -1;
        snapKeepTicks = 0;
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        stopAttacking();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (previousAimTicks > 0) previousAimTicks--;
        if (snapKeepTicks > 0) snapKeepTicks--;

        if (!shouldAttackNow()) {
            stopAttacking();
            return;
        }

        TargetPlan targetPlan = selectTargetPlan(rotate.get() ? null : mc.player.getYaw(), rotate.get() ? null : mc.player.getPitch());
        if (targetPlan == null) {
            stopAttacking();
            return;
        }

        Candidate candidate = targetPlan.candidate;
        targets.clear();
        targets.add(candidate.entity);

        if (autoSwitch.get()) {
            FindItemResult weaponResult = attackWhenHolding.get() == AttackItems.Weapons
                ? InvUtils.find(this::acceptableWeapon, 0, 8)
                : new FindItemResult(mc.player.getInventory().getSelectedSlot(), 1);

            if (!weaponResult.found()) {
                stopAttacking();
                return;
            }

            if (!swapped) {
                previousSlot = mc.player.getInventory().getSelectedSlot();
                swapped = true;
            }

            InvUtils.swap(weaponResult.slot(), false);
        }

        if (!acceptableWeapon(mc.player.getMainHandStack())) {
            stopAttacking();
            return;
        }

        attacking = true;

        if (pauseOnCombat.get() && PathManagers.get().isPathing() && !wasPathing) {
            PathManagers.get().pause();
            wasPathing = true;
        }

        boolean readyToHit = delayCheck(candidate.entity);

        if (rotate.get()) {
            applyRotationPlan(targetPlan);

            if (!targetPlan.requestRotation) {
                return;
            }

            if (!readyToHit) {
                return;
            }

            if (!canAttackPlannedTarget(targetPlan)) {
                return;
            }
        } else if (!readyToHit) {
            return;
        }

        attackTarget(candidate);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof UpdateSelectedSlotC2SPacket) {
            switchTimer = switchDelay.get();
        }
    }

    private void attackTarget(Candidate candidate) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!shouldAttackNow()) {
            stopAttacking();
            return;
        }

        Entity target = candidate.entity;
        if (!canAttackEntity(target)) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        hitTimer = 0;
    }

    private void stopAttacking() {
        RotationManager.INSTANCE.clearRequests(this);
        targets.clear();
        previousAimEntityId = -1;
        previousAimPoint = null;
        previousAimTicks = 0;
        snapTargetEntityId = -1;
        snapKeepTicks = 0;

        if (!attacking) return;

        attacking = false;

        if (wasPathing) {
            PathManagers.get().resume();
            wasPathing = false;
        }

        if (swapBack.get() && swapped) {
            InvUtils.swap(previousSlot, false);
            swapped = false;
        }
    }

    private boolean entityCheck(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (ignoreInvisible.get() && entity.isInvisible()) return false;
        if (entity instanceof Tameable tameable && tameable.getOwner() != null && tameable.getOwner().equals(mc.player)) return false;
        if (ignorePassive.get()) {
            if (entity instanceof EndermanEntity enderman && !enderman.isAngry()) return false;
            if ((entity instanceof PiglinEntity || entity instanceof ZombifiedPiglinEntity || entity instanceof WolfEntity) && !((MobEntity) entity).isAttacking()) return false;
        }

        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer && fakePlayer.noHit) return false;
        }

        return isWithinSearchRange(entity);
    }

    private boolean delayCheck(Entity target) {
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = customDelay.get() ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20f);

        if (customDelay.get()) {
            hitTimer++;
            return hitTimer >= delay || predictExitingRange(target, 1.0);
        }

        return mc.player.getAttackCooldownProgress(delay) >= 1f || predictExitingRange(target, 1.0);
    }

    private boolean canAttackIn(int ticks) {
        if (mc.player == null) return false;

        int ticksAhead = Math.max(0, ticks);
        if (switchTimer > ticksAhead) return false;

        float delay = customDelay.get() ? hitDelay.get() : 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20f);

        if (customDelay.get()) {
            return hitTimer + ticksAhead + 1 >= delay;
        }

        return mc.player.getAttackCooldownProgress(delay + ticksAhead) >= 1f;
    }

    private boolean predictExitingRange(Entity target, double ticks) {
        if (mc.player == null || mc.world == null || target == null) return false;
        if (!(target instanceof LivingEntity livingTarget) || livingTarget.hurtTime > 7) return false;
        if (!entityCheckBase(target)) return false;

        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        Vec3d targetPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        Vec3d futurePlayerPos = playerPos.add(mc.player.getVelocity().multiply(ticks));
        Vec3d futureTargetPos = targetPos.add(target.getVelocity().multiply(ticks));
        Vec3d futureEyes = futurePlayerPos.add(0.0, mc.player.getEyeHeight(mc.player.getPose()), 0.0);
        Box futureBox = target.getBoundingBox().offset(futureTargetPos.subtract(targetPos)).expand(target.getTargetingMargin());

        return !canSeePredictedBox(futureEyes, futureBox, getInteractionRange(), getThroughWallsRange());
    }

    private boolean canSeePredictedBox(Vec3d eyes, Box box, double range, double wallsRange) {
        AimData aimData = createAimData(eyes, null, box, range, wallsRange);
        return aimData != null;
    }

    private void applyRotationPlan(TargetPlan targetPlan) {
        if (targetPlan.requestRotation) {
            RotationManager.INSTANCE.requestRotation(targetPlan.candidate.rotationTarget);
        } else {
            RotationManager.INSTANCE.clearRequests(this);
        }
    }

    private boolean canAttackPlannedTarget(TargetPlan targetPlan) {
        Candidate candidate = targetPlan.candidate;
        float attackYaw = RotationManager.INSTANCE.getCurrentYaw();
        float attackPitch = RotationManager.INSTANCE.getCurrentPitch();
        return getAttackHitResult(candidate.entity, attackYaw, attackPitch) != null && canAttackEntity(candidate.entity);
    }

    private RotationTarget createRotationTarget(Entity target, AimData aimData) {
        return new RotationTarget(
            this,
            aimData.yaw,
            aimData.pitch,
            minHorizontalTurnSpeed.get().floatValue(),
            maxHorizontalTurnSpeed.get().floatValue(),
            minVerticalTurnSpeed.get().floatValue(),
            maxVerticalTurnSpeed.get().floatValue(),
            rotationThreshold.get().floatValue(),
            movementCorrection.get(),
            null
        );
    }

    private boolean acceptableWeapon(ItemStack stack) {
        if (attackWhenHolding.get() == AttackItems.All) return true;

        Item item = stack.getItem();
        if (weapons.get().contains(Items.DIAMOND_SWORD) && stack.isIn(ItemTags.SWORDS)) return true;
        if (weapons.get().contains(Items.DIAMOND_AXE) && stack.isIn(ItemTags.AXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_PICKAXE) && stack.isIn(ItemTags.PICKAXES)) return true;
        if (weapons.get().contains(Items.DIAMOND_SHOVEL) && stack.isIn(ItemTags.SHOVELS)) return true;
        if (weapons.get().contains(Items.DIAMOND_HOE) && stack.isIn(ItemTags.HOES)) return true;
        if (weapons.get().contains(Items.MACE) && item == Items.MACE) return true;
        if (weapons.get().contains(Items.DIAMOND_SPEAR) && stack.isIn(ItemTags.SPEARS)) return true;
        return weapons.get().contains(Items.TRIDENT) && item == Items.TRIDENT;
    }

    private boolean shouldAttackNow() {
        if (mc.player == null || mc.interactionManager == null) return false;
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return false;
        if (pauseOnUse.get() && (mc.interactionManager.isBreakingBlock() || mc.player.isUsingItem())) return false;
        if (onlyOnClick.get() && !mc.options.attackKey.isPressed()) return false;
        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1f) return false;

        if (pauseOnCA.get()) {
            CrystalAura crystalAura = Modules.get().get(CrystalAura.class);
            if (crystalAura != null && crystalAura.isActive() && crystalAura.kaTimer > 0) return false;
        }

        return true;
    }

    private TargetPlan selectTargetPlan(Float yaw, Float pitch) {
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), maxTargets.get());
        if (targets.isEmpty()) return null;

        Entity primary = applyRaycast(targets.getFirst(), yaw, pitch);
        if (primary != null && entityCheck(primary)) {
            targets.remove(primary);
            targets.addFirst(primary);
        }

        double interactionRange = getInteractionRange();
        double maximumRange = getMaximumTargetRange(interactionRange);
        double throughWallsRange = getThroughWallsRange();
        sortTargetsByInteractionRange(interactionRange * interactionRange);

        for (Entity entity : targets) {
            TargetPlan targetPlan = processTarget(entity, maximumRange, throughWallsRange);
            if (targetPlan != null) return targetPlan;
        }

        return null;
    }

    private TargetPlan processTarget(Entity entity, double range, double wallsRange) {
        Candidate candidate = createCandidate(entity, range, wallsRange);
        if (candidate == null) return null;

        boolean requestRotation = rotationTiming.get() == RotationTiming.Normal
            || shouldStartOrKeepSnapRotation(candidate);
        return new TargetPlan(candidate, requestRotation);
    }

    private boolean shouldStartOrKeepSnapRotation(Candidate candidate) {
        if (rotationTiming.get() == RotationTiming.Normal) return true;

        if (snapKeepTicks > 0 && snapTargetEntityId == candidate.entity.getId()) {
            return true;
        }

        boolean shouldStart = canAttackIn(Math.max(0, candidate.rotationTicks - 1));
        if (shouldStart) {
            snapTargetEntityId = candidate.entity.getId();
            snapKeepTicks = Math.max(2, candidate.rotationTicks + 1);
        }

        return shouldStart;
    }

    private Candidate createCandidate(Entity entity, double range, double wallsRange) {
        AimData aimData = createAimData(entity, range, wallsRange);
        if (aimData == null) return null;

        previousAimEntityId = entity.getId();
        previousAimPoint = aimData.point;
        previousAimTicks = 2;

        RotationTarget rotationTarget = createRotationTarget(entity, aimData);
        int rotationTicks = Math.max(1, RotationManager.INSTANCE.estimateTicksToReach(rotationTarget));
        return new Candidate(entity, aimData, rotationTarget, rotationTicks);
    }

    private Entity applyRaycast(Entity fallback, Float yaw, Float pitch) {
        if (raycast.get() != RaycastMode.ALL || mc.player == null) return fallback;

        Entity camera = mc.getCameraEntity();
        if (camera == null) return fallback;

        float rotationYaw = yaw != null ? yaw : mc.player.getYaw();
        float rotationPitch = pitch != null ? pitch : mc.player.getPitch();
        double interactionRange = getInteractionRange();

        Vec3d start = camera.getEyePos();
        Vec3d rotation = Vec3d.fromPolar(rotationPitch, rotationYaw);
        Vec3d end = start.add(rotation.x * interactionRange, rotation.y * interactionRange, rotation.z * interactionRange);
        Box box = camera.getBoundingBox().stretch(rotation.multiply(interactionRange)).expand(1.0);

        EntityHitResult hitResult = ProjectileUtil.raycast(
            camera,
            start,
            end,
            box,
            entity -> canAttackEntity(entity) && entity.canHit(),
            interactionRange * interactionRange
        );

        return hitResult != null ? hitResult.getEntity() : fallback;
    }

    private EntityHitResult getAttackHitResult(Entity entity, float yaw, float pitch) {
        if (mc.player == null) return null;

        double range = getInteractionRange();
        double throughWallsRange = getThroughWallsRange();
        Vec3d eyes = mc.player.getEyePos();
        Vec3d rotation = Vec3d.fromPolar(pitch, yaw);
        Vec3d end = eyes.add(rotation.x * range, rotation.y * range, rotation.z * range);
        Box box = mc.player.getBoundingBox().stretch(rotation.multiply(range)).expand(1.0, 1.0, 1.0);

        EntityHitResult hitResult = ProjectileUtil.raycast(
            mc.player,
            eyes,
            end,
            box,
            candidate -> candidate == entity && entityCheckBase(candidate) && candidate.canHit(),
            range * range
        );

        if (hitResult == null || hitResult.getEntity() != entity) return null;

        double distanceSq = eyes.squaredDistanceTo(hitResult.getPos());
        if (distanceSq <= throughWallsRange * throughWallsRange) return hitResult;
        if (distanceSq <= range * range && hasLineOfSight(eyes, hitResult.getPos())) return hitResult;

        return null;
    }

    private boolean isWithinSearchRange(Entity entity) {
        return distanceToTarget(entity) <= getSearchRange();
    }

    private boolean canAttackEntity(Entity entity) {
        if (!entityCheckBase(entity)) return false;

        double allowedRange = PlayerUtils.canSeeEntity(entity) ? getInteractionRange() : getThroughWallsRange();
        return distanceToTarget(entity) <= allowedRange;
    }

    private boolean entityCheckBase(Entity entity) {
        if (entity == null || entity == mc.player || entity == mc.getCameraEntity()) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (entity instanceof Tameable tameable && tameable.getOwner() != null && tameable.getOwner().equals(mc.player)) return false;

        if (entity instanceof PlayerEntity player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
            if (entity instanceof FakePlayerEntity fakePlayer && fakePlayer.noHit) return false;
        }

        return true;
    }

    private double getInteractionRange() {
        if (mc.player == null) return 3.0 + rangeIncrease.get();
        return mc.player.getAttributeValue(EntityAttributes.ENTITY_INTERACTION_RANGE) + rangeIncrease.get();
    }

    private double getThroughWallsRange() {
        return Math.min(getInteractionRange(), wallsRange.get());
    }

    private double getSearchRange() {
        return Math.max(getInteractionRange(), getThroughWallsRange()) + scanRangeIncrease.get();
    }

    private double getMaximumTargetRange(double interactionRange) {
        double interactionRangeSq = interactionRange * interactionRange;
        double closestDistanceSq = Double.POSITIVE_INFINITY;

        for (Entity entity : targets) {
            closestDistanceSq = Math.min(closestDistanceSq, squaredDistanceToTarget(entity));
        }

        return closestDistanceSq > interactionRangeSq ? getSearchRange() : interactionRange;
    }

    private void sortTargetsByInteractionRange(double interactionRangeSq) {
        targets.sort((first, second) -> {
            int firstBucket = squaredDistanceToTarget(first) <= interactionRangeSq ? 0 : 1;
            int secondBucket = squaredDistanceToTarget(second) <= interactionRangeSq ? 0 : 1;
            return Integer.compare(firstBucket, secondBucket);
        });
    }

    private double distanceToTarget(Entity entity) {
        Vec3d eyes = mc.player.getEyePos();
        Box hitbox = entity.getBoundingBox();

        double x = MathHelper.clamp(eyes.x, hitbox.minX, hitbox.maxX);
        double y = MathHelper.clamp(eyes.y, hitbox.minY, hitbox.maxY);
        double z = MathHelper.clamp(eyes.z, hitbox.minZ, hitbox.maxZ);

        return eyes.distanceTo(new Vec3d(x, y, z));
    }

    private double squaredDistanceToTarget(Entity entity) {
        double distance = distanceToTarget(entity);
        return distance * distance;
    }

    private AimData createAimData(Entity entity, double range, double wallsRange) {
        if (mc.player == null || mc.world == null) return null;

        return createAimData(mc.player.getEyePos(), entity, entity.getBoundingBox().expand(entity.getTargetingMargin()), range, wallsRange);
    }

    private AimData createAimData(Vec3d eyes, Entity entity, Box box, double range, double wallsRange) {
        currentTargetBox = box;
        AimData best = null;

        Vec3d closest = new Vec3d(
            MathHelper.clamp(eyes.x, box.minX, box.maxX),
            MathHelper.clamp(eyes.y, box.minY, box.maxY),
            MathHelper.clamp(eyes.z, box.minZ, box.maxZ)
        );

        best = considerAimPoint(best, eyes, entity, closest, range, wallsRange);

        if (shouldPreferPreviousAimPoint(entity, box)) {
            AimData preservedAim = considerAimPoint(null, eyes, entity, previousAimPoint, range, wallsRange);
            if (preservedAim != null) {
                currentTargetBox = null;
                return preservedAim;
            }
        }

        for (double x : AIM_POINT_SAMPLES) {
            for (double y : AIM_POINT_SAMPLES) {
                best = considerAimPoint(best, eyes, entity, new Vec3d(
                    MathHelper.lerp(x, box.minX, box.maxX),
                    MathHelper.lerp(y, box.minY, box.maxY),
                    box.minZ
                ), range, wallsRange);
                best = considerAimPoint(best, eyes, entity, new Vec3d(
                    MathHelper.lerp(x, box.minX, box.maxX),
                    MathHelper.lerp(y, box.minY, box.maxY),
                    box.maxZ
                ), range, wallsRange);
                best = considerAimPoint(best, eyes, entity, new Vec3d(
                    box.minX,
                    MathHelper.lerp(y, box.minY, box.maxY),
                    MathHelper.lerp(x, box.minZ, box.maxZ)
                ), range, wallsRange);
                best = considerAimPoint(best, eyes, entity, new Vec3d(
                    box.maxX,
                    MathHelper.lerp(y, box.minY, box.maxY),
                    MathHelper.lerp(x, box.minZ, box.maxZ)
                ), range, wallsRange);
                best = considerAimPoint(best, eyes, entity, new Vec3d(
                    MathHelper.lerp(x, box.minX, box.maxX),
                    box.minY,
                    MathHelper.lerp(y, box.minZ, box.maxZ)
                ), range, wallsRange);
                best = considerAimPoint(best, eyes, entity, new Vec3d(
                    MathHelper.lerp(x, box.minX, box.maxX),
                    box.maxY,
                    MathHelper.lerp(y, box.minZ, box.maxZ)
                ), range, wallsRange);
            }
        }

        currentTargetBox = null;
        return best;
    }

    private boolean shouldPreferPreviousAimPoint(Entity entity, Box box) {
        if (entity == null) return false;
        return previousAimTicks > 0
            && previousAimPoint != null
            && previousAimEntityId == entity.getId()
            && box.contains(previousAimPoint);
    }

    private AimData considerAimPoint(AimData currentBest, Vec3d eyes, Entity entity, Vec3d point, double range, double wallsRange) {
        Vec3d spotOnBox = firstHitPoint(getCurrentTargetBox(), eyes, point);
        if (spotOnBox == null) return currentBest;

        double distanceSq = eyes.squaredDistanceTo(spotOnBox);
        if (distanceSq > range * range) return currentBest;

        boolean visible = isVisible(eyes, spotOnBox);
        if (!visible && distanceSq > wallsRange * wallsRange) return currentBest;

        float yaw = (float) Rotations.getYaw(point);
        float pitch = (float) Rotations.getPitch(point);
        float referenceYaw = RotationManager.INSTANCE.hasActiveMovementCorrection() ? RotationManager.INSTANCE.getCurrentYaw() : mc.player.getYaw();
        float referencePitch = RotationManager.INSTANCE.hasActiveMovementCorrection() ? RotationManager.INSTANCE.getCurrentPitch() : mc.player.getPitch();
        float yawDiff = Math.abs(MathHelper.wrapDegrees(yaw - referenceYaw));
        float pitchDiff = Math.abs(pitch - referencePitch);
        float stabilityPenalty = getStabilityPenalty(entity, point);
        AimData candidate = new AimData(yaw, pitch, point, distanceSq, visible, yawDiff + pitchDiff, stabilityPenalty);

        if (currentBest == null) return candidate;
        if (candidate.visible && !currentBest.visible) return candidate;
        if (candidate.visible == currentBest.visible && candidate.preferenceScore() < currentBest.preferenceScore() - 1.0E-3) return candidate;
        if (candidate.visible == currentBest.visible && Math.abs(candidate.preferenceScore() - currentBest.preferenceScore()) <= 1.0E-3
            && candidate.distanceSq < currentBest.distanceSq) return candidate;
        return currentBest;
    }

    private float getStabilityPenalty(Entity entity, Vec3d point) {
        if (entity == null) return 0.0f;
        if (previousAimTicks <= 0 || previousAimPoint == null || previousAimEntityId != entity.getId()) return 0.0f;
        return (float) previousAimPoint.distanceTo(point) * 8.0f;
    }

    private Box currentTargetBox;

    private Box getCurrentTargetBox() {
        return currentTargetBox;
    }

    private boolean isVisible(Vec3d eyes, Vec3d point) {
        if (mc.world == null || mc.player == null) return false;

        HitResult hitResult = mc.world.raycast(new RaycastContext(
            eyes,
            point,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return hitResult.getType() == HitResult.Type.MISS;
    }

    private boolean hasLineOfSight(Vec3d eyes, Vec3d point) {
        if (mc.world == null || mc.player == null) return false;

        HitResult hitResult = mc.world.raycast(new RaycastContext(
            eyes,
            point,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            mc.player
        ));

        return hitResult.getType() == HitResult.Type.MISS;
    }

    private Vec3d firstHitPoint(Box box, Vec3d from, Vec3d target) {
        if (box == null) return null;

        Optional<Vec3d> hit = box.contains(from) ? box.raycast(target, from) : box.raycast(from, target);
        return hit.orElse(null);
    }

    public Entity getTarget() {
        return targets.isEmpty() ? null : targets.getFirst();
    }

    @Override
    public String getInfoString() {
        return targets.isEmpty() ? null : EntityUtils.getName(getTarget());
    }

    public enum AttackItems {
        Weapons,
        All
    }

    public enum RaycastMode {
        NONE,
        ALL
    }

    private record Candidate(Entity entity, AimData aimData, RotationTarget rotationTarget, int rotationTicks) {
    }

    private record TargetPlan(Candidate candidate, boolean requestRotation) {
    }

    private record AimData(float yaw, float pitch, Vec3d point, double distanceSq, boolean visible, float angleDifference, float stabilityPenalty) {
        private float preferenceScore() {
            return angleDifference + stabilityPenalty;
        }
    }
}
