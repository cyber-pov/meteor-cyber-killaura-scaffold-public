package dev.cyber.meteor.modules.combat;

import dev.cyber.meteor.CyberCategories;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class TriggerBot extends Module {
    private static final double MAX_ATTACK_RANGE = 6.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Entities to attack.")
            .onlyAttackable()
            .defaultValue(EntityType.PLAYER)
            .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("Maximum attack distance.")
            .defaultValue(3.0)
            .min(0.0)
            .max(MAX_ATTACK_RANGE)
            .sliderRange(0.0, MAX_ATTACK_RANGE)
            .build()
    );

    private final Setting<Boolean> smartDaily = sgGeneral.add(new BoolSetting.Builder()
            .name("smart-daily")
            .description("Smart daily mode: attack when cooldown is ready, with small random delay.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> cooldownThreshold = sgGeneral.add(new DoubleSetting.Builder()
            .name("cooldown-threshold")
            .description("Required cooldown before attacking in smart daily mode.")
            .defaultValue(0.92)
            .min(0.0)
            .max(1.0)
            .sliderRange(0.7, 1.0)
            .visible(smartDaily::get)
            .build()
    );

    private final Setting<Integer> randomDelayMinMs = sgGeneral.add(new IntSetting.Builder()
            .name("random-delay-min-ms")
            .description("Minimum extra delay after cooldown is ready.")
            .defaultValue(6)
            .min(0)
            .sliderRange(0, 80)
            .visible(smartDaily::get)
            .build()
    );

    private final Setting<Integer> randomDelayMaxMs = sgGeneral.add(new IntSetting.Builder()
            .name("random-delay-max-ms")
            .description("Maximum extra delay after cooldown is ready.")
            .defaultValue(24)
            .min(0)
            .sliderRange(0, 120)
            .visible(smartDaily::get)
            .build()
    );

    private long nextAttackAtMs;
    private LivingEntity scheduledTarget;

    public TriggerBot() {
        super(CyberCategories.CYBER, "trigger-bot", "Automatically attacks valid entities under your crosshair.");
    }

    @Override
    public void onActivate() {
        resetAttackSchedule();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        LivingEntity target = getCrosshairTarget();
        if (target == null) {
            resetAttackSchedule();
            return;
        }
        if (!target.isAlive()) {
            resetAttackSchedule();
            return;
        }
        if (target == mc.player) {
            resetAttackSchedule();
            return;
        }
        if (mc.player.distanceTo(target) > range.get()) {
            resetAttackSchedule();
            return;
        }

        // Never attack friends.
        if (target instanceof PlayerEntity player && Friends.get().isFriend(player)) {
            resetAttackSchedule();
            return;
        }

        // Validate selected entity types.
        if (!isValidTarget(target)) {
            resetAttackSchedule();
            return;
        }

        if (!shouldAttackNow(target)) return;

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private LivingEntity getCrosshairTarget() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY) return null;

        Entity entity = ((EntityHitResult) hit).getEntity();
        if (!(entity instanceof LivingEntity living)) return null;
        return living;
    }

    private boolean isValidTarget(LivingEntity entity) {
        return entities.get().contains(entity.getType());
    }

    private boolean shouldAttackNow(LivingEntity target) {
        if (!smartDaily.get()) return true;

        if (!isCooldownReady()) {
            resetAttackSchedule();
            return false;
        }

        long now = System.currentTimeMillis();
        if (scheduledTarget != target) {
            scheduledTarget = target;
            nextAttackAtMs = now + randomDelayMs();
        }

        if (now < nextAttackAtMs) return false;

        resetAttackSchedule();
        return true;
    }

    private boolean isCooldownReady() {
        float charge = mc.player.getAttackCooldownProgress(0.0f);
        return charge + 1.0e-4f >= cooldownThreshold.get().floatValue();
    }

    private long randomDelayMs() {
        int min = Math.max(0, Math.min(randomDelayMinMs.get(), randomDelayMaxMs.get()));
        int max = Math.max(min, Math.max(randomDelayMinMs.get(), randomDelayMaxMs.get()));
        if (randomDelayMaxMs.get() < min) randomDelayMaxMs.set(min);
        return ThreadLocalRandom.current().nextLong(min, max + 1L);
    }

    private void resetAttackSchedule() {
        scheduledTarget = null;
        nextAttackAtMs = 0L;
    }
}
