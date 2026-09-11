package com.example.examplemod.Enemy.EnemyAttack;

import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.AdvancedAimMath;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.PlayerVelocityTracker;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.ProjectileTrajectory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Атака гаста: прицілювання, заряджання (відкриття рота + звуки) і сам постріл великим
 * фаєрболом з випередженням. Гаст не переслідує ціль у цьому Goal-і (літає своїм ванільним
 * рухом), тому окремого класу руху для нього нема — уся логіка тут.
 * <p>
 * Винесено з {@link com.example.examplemod.mobAi.Goal.BetterGhastGoalAi}, яка лишилась лише
 * тонкою Goal-обгорткою (canUse/start/stop) і делегує сюди щотіковий цикл атаки.
 */
public class GhastFireballAttack {

    private final Ghast ghast;
    private int chargeTime;

    public GhastFireballAttack(Ghast ghast) {
        this.ghast = ghast;
    }

    /**
     * Викликати з {@code Goal.start()} — обнуляє таймер заряду для нового циклу атаки.
     */
    public void reset() {
        this.chargeTime = 0;
    }

    /**
     * Викликати з {@code Goal.stop()} — закриває рот гаста, якщо Goal перервано під час заряду.
     */
    public void onStop() {
        this.ghast.setCharging(false);
    }

    public void tick(LivingEntity target) {
        double distanceSq = this.ghast.distanceToSqr(target);

        // Гаст завжди дивиться на ціль
        this.ghast.getLookControl().setLookAt(target, 10.0F, 10.0F);

        double followRange = this.ghast.getAttributeValue(Attributes.FOLLOW_RANGE);

        double maxShootDistance = followRange * 0.75;

        if (distanceSq < (maxShootDistance * maxShootDistance) && this.ghast.hasLineOfSight(target)) {
            this.chargeTime++;

            // Закриваємо рота через 10 тіків (0.5 сек) після пострілу
            if (this.chargeTime == -15) {
                this.ghast.setCharging(false);
            }

            // На 5-му тіку: Звук, зміна лиця і сам постріл
            if (this.chargeTime == 5) {
                // 1. Відкриваємо рота
                this.ghast.setCharging(true);

                // 2. Звук крику (1015) і пострілу (1016)
                this.ghast.level().levelEvent(null, 1015, this.ghast.blockPosition(), 0);
                this.ghast.level().levelEvent(null, 1016, this.ghast.blockPosition(), 0);

                // 3. Створюємо і запускаємо фаєрбол
                Vec3 shooterOrigin = new Vec3(this.ghast.getX(), this.ghast.getY(0.5) + 0.5, this.ghast.getZ());
                float fireballSpeed = 3f;
                Vec3 realVel = PlayerVelocityTracker.getRealVelocity(target);
                Vec3 dir = AdvancedAimMath.calculateLinearAim(shooterOrigin, target, realVel, fireballSpeed);

                // dir — це лише напрямок (нормалізований), тому будуємо кінцеву точку самі:
                double travelDistance = shooterOrigin.distanceTo(target.position()) + 4.0; // +запас, щоб капсула напевно дотягнулась до цілі й трохи за неї
                Vec3 aimPoint = shooterOrigin.add(dir.scale(travelDistance));

                if (!ProjectileTrajectory.isPathClear(this.ghast, shooterOrigin, aimPoint, 0.6)) // перевірка траекторії
                {
                    return;
                }

                LargeFireball fireball = new LargeFireball(this.ghast.level(), this.ghast, dir, 2);
                fireball.setPos(shooterOrigin.x + dir.x * 2.0, shooterOrigin.y + dir.y * 2.0, shooterOrigin.z + dir.z * 2.0);
                fireball.setDeltaMovement(dir.scale(fireballSpeed));

                this.ghast.level().addFreshEntity(fireball);

                // 4. Кулдаун 1.5 секунди
                // Від -25 до 0 = 25 тіків + 5 тіків на зарядку = 30 тіків (рівно 1.5 секунди)
                this.chargeTime = -25;
            }
        } else {
            // Якщо ціль сховалася за стіну
            if (this.chargeTime > 0) {
                this.chargeTime--;
            }
            // Страховка: закриваємо рота, якщо гравець зник під час кулдауну
            if (this.chargeTime <= 0) {
                this.ghast.setCharging(false);
            }
        }
    }
}
