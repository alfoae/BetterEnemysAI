package com.example.examplemod.mobAi;

import com.example.examplemod.util.AdvancedAimMath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class BetterGhastGoalAi extends Goal {
    private final Ghast ghast;
    private int chargeTime;
    private Vec3 targetVelocity = Vec3.ZERO;
    private Vec3 lastTargetPos = null;

    public BetterGhastGoalAi(Ghast ghast) {
        this.ghast = ghast;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.ghast.getTarget() != null;
    }

    @Override
    public void start() {
        this.chargeTime = 0;
    }

    @Override
    public void stop() {
        this.ghast.setCharging(false);
        this.lastTargetPos = null;
    }

    @Override
    public void tick() {
        LivingEntity target = this.ghast.getTarget();
        if (target == null) return;

        // Згладжування швидкості
        Vec3 currentPos = target.position();
        if (this.lastTargetPos != null) {
            Vec3 instantVel = currentPos.subtract(this.lastTargetPos);
            this.targetVelocity = this.targetVelocity.lerp(instantVel, 0.4);
        }
        this.lastTargetPos = currentPos;

        double distanceSq = this.ghast.distanceToSqr(target);

        // Гаст завжди дивиться на ціль
        this.ghast.getLookControl().setLookAt(target, 10.0F, 10.0F);

        if (distanceSq < 4096.0D && this.ghast.hasLineOfSight(target)) {
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
                float fireballSpeed = 1.5f;
                Vec3 dir = AdvancedAimMath.calculateLinearAim(shooterOrigin, target, this.targetVelocity, fireballSpeed);

                LargeFireball fireball = new LargeFireball(this.ghast.level(), this.ghast, dir, 1);
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