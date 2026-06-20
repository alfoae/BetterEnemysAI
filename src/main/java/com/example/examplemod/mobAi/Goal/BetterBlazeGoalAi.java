package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.ProjectileTrajectoryUtils;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class BetterBlazeGoalAi extends Goal {
    private final Blaze blaze;
    private int chargeTime;

    public BetterBlazeGoalAi(Blaze blaze) {
        this.blaze = blaze;
        // Дозволяємо йому і дивитись, і рухатись під час атаки
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.blaze.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        this.chargeTime = 0;
    }

    @Override
    public void stop() {
        this.setBlazeCharged(false);
    }

    @Override
    public void tick() {
        LivingEntity target = this.blaze.getTarget();
        if (target == null) return;

        double distanceSq = this.blaze.distanceToSqr(target);
        this.blaze.getLookControl().setLookAt(target, 10.0F, 10.0F);

        double followRange = this.blaze.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);

        double maxShootDistance = followRange * 0.75;

        if (distanceSq < (maxShootDistance * maxShootDistance) && this.blaze.hasLineOfSight(target)) {
            // Зупиняємось, щоб постріляти
            this.blaze.getNavigation().stop();
            this.chargeTime++;

            // Загоряється перед стрільбою
            if (this.chargeTime == 20) {
                this.setBlazeCharged(true);
            }

            // Стріляємо 3 рази: на 40-му, 45-му і 50-му тіках
            if (this.chargeTime >= 40 && this.chargeTime <= 50 && this.chargeTime % 5 == 0) {
                int shotIndex = (this.chargeTime - 40) / 5; // Видасть 0, 1 або 2
                shootFireball(target, shotIndex);
            }

            // Скидаємо кулдаун після 3-го пострілу
            if (this.chargeTime >= 50) {
                this.chargeTime = -10; // 1.5 секунди перерви між серіями
                this.setBlazeCharged(false);
            }
        } else {
            // Якщо ціль далеко або за стіною - летимо до неї
            this.blaze.getNavigation().moveTo(target, 1.0D);
            if (this.chargeTime > 0) {
                this.chargeTime--; // Поступово остигає
                if (this.chargeTime == 0) {
                    this.setBlazeCharged(false);
                }
            }
        }
    }

    private void shootFireball(LivingEntity target, int shotIndex) {
        float projectileSpeed = 4f;

        // 1. БАЗОВА ТОЧКА: Рахуємо випередження в центр гравця
        Vec3 targetCenter = new Vec3(target.getX(), target.getY(0.5), target.getZ());
        double flightTime = this.blaze.position().distanceTo(targetCenter) / projectileSpeed;

        // Коефіцієнт випередження
        Vec3 realVel = com.example.examplemod.utils.PlayerVelocityTracker.getRealVelocity(target);
        Vec3 adjustedVel = realVel.scale(2.2);
        Vec3 predictedPos = targetCenter.add(adjustedVel.scale(flightTime));

        // 2. ЛОГІКА ПРОМАХУ
        // shotIndex == 0 (це 1-й снаряд, шанс 25%), інакше 50%
        float missChance = (shotIndex == 0) ? 0.25f : 0.50f;
        boolean isMiss = this.blaze.getRandom().nextFloat() < missChance;

        if (isMiss) {
            // Робимо розброс у квадраті 4х4х4 (-2 до +2 блоків від передбачуваної точки)
            double spreadX = (this.blaze.getRandom().nextDouble() - 0.5) * 4.0;
            double spreadY = (this.blaze.getRandom().nextDouble() - 0.5) * 4.0;
            double spreadZ = (this.blaze.getRandom().nextDouble() - 0.5) * 4.0;

            // Додаємо цей розброс до ТОЧКИ, куди мав летіти снаряд
            predictedPos = predictedPos.add(spreadX, spreadY, spreadZ);
        }

        // 3. ПОСТРІЛ: Рахуємо траєкторію від центру Іфрита до (можливо зміщеної) точки
        Vec3 shooterOrigin = new Vec3(this.blaze.getX(), this.blaze.getY(0.5) + 0.5, this.blaze.getZ());
        Vec3 dir = predictedPos.subtract(shooterOrigin).normalize();

        if (!ProjectileTrajectoryUtils.isPathClear(this.blaze, shooterOrigin, predictedPos, 0.4))  // перевірка траекторії
        {
            return;
        }


        SmallFireball fireball = new SmallFireball(this.blaze.level(), this.blaze, dir);

        // Зміщення точки спавну на 0.8, щоб Іфрит не влучив сам у себе
        fireball.setPos(
                shooterOrigin.x + dir.x * 0.8,
                shooterOrigin.y + dir.y * 0.8,
                shooterOrigin.z + dir.z * 0.8
        );

        fireball.setDeltaMovement(dir.scale(projectileSpeed));

        this.blaze.level().addFreshEntity(fireball);
        this.blaze.playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 1.0F);
    }

    private void setBlazeCharged(boolean charged) {
        try {
            java.lang.reflect.Method method = Blaze.class.getDeclaredMethod("setCharged", boolean.class);
            method.setAccessible(true); // Відкриваємо доступ
            method.invoke(this.blaze, charged);
        } catch (Exception e) {
            // Якщо метод не знайдено (наприклад, після компіляції моду), ігноруємо
        }
    }
}