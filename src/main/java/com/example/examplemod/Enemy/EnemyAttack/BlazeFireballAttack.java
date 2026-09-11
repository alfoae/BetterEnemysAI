package com.example.examplemod.Enemy.EnemyAttack;

import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.PlayerVelocityTracker;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.ProjectileTrajectory;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Атака блейза: заряджання (загоряння), серія з 3 пострілів з випередженням і розкидом, і
 * мінімальне наближення до цілі, коли вона поза дистанцією стрільби. Це наближення (один
 * виклик {@code navigation.moveTo}) навмисно лишилось тут, а не в окремому класі руху — воно
 * невіддільне від того ж самого range-чек-розгалуження, що вирішує заряджати/стріляти, так
 * само як ванільний {@code RangedAttackGoal} поєднує підхід і стрільбу в одній сутності.
 * <p>
 * Винесено з {@link com.example.examplemod.mobAi.Goal.BetterBlazeGoalAi}, яка лишилась лише
 * тонкою Goal-обгорткою (canUse/start/stop) і делегує сюди щотіковий цикл атаки.
 */
public class BlazeFireballAttack {

    private final Blaze blaze;
    private int chargeTime;

    public BlazeFireballAttack(Blaze blaze) {
        this.blaze = blaze;
    }

    /**
     * Викликати з {@code Goal.start()} — обнуляє таймер заряду для нового циклу атаки.
     */
    public void reset() {
        this.chargeTime = 0;
    }

    /**
     * Викликати з {@code Goal.stop()} — гасить блейза, якщо Goal перервано під час заряду.
     */
    public void onStop() {
        this.setBlazeCharged(false);
    }

    public void tick(LivingEntity target) {
        double distanceSq = this.blaze.distanceToSqr(target);
        this.blaze.getLookControl().setLookAt(target, 10.0F, 10.0F);

        double followRange = this.blaze.getAttributeValue(Attributes.FOLLOW_RANGE);

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
        Vec3 realVel = PlayerVelocityTracker.getRealVelocity(target);
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

        if (!ProjectileTrajectory.isPathClear(this.blaze, shooterOrigin, predictedPos, 0.18))  // перевірка траекторії
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
