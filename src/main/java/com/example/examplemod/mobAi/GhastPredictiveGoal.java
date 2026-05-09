package com.example.examplemod.mobAi;

import com.example.examplemod.util.AdvancedAimMath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class GhastPredictiveGoal extends Goal {
    private final Ghast ghast;
    private int chargeTime;
    private Vec3 targetVelocity = Vec3.ZERO;
    private Vec3 lastTargetPos = null;

    public GhastPredictiveGoal(Ghast ghast) {
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

        // 1. Згладжування швидкості (як у скелетів)
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

            if (this.chargeTime == 10) {
                // Звук початку заряджання (ваніль)
                this.ghast.level().levelEvent(null, 1015, this.ghast.blockPosition(), 0);
            }

            if (this.chargeTime == 20) {
                this.ghast.setCharging(true);
            }

            if (this.chargeTime >= 40) { // Момент пострілу
                // Вираховуємо вектор напрямку кулі (швидкість фаєрбола зазвичай ~0.5-1.0)
                Vec3 dir = AdvancedAimMath.calculateLinearAim(this.ghast.getEyePosition(), target, this.targetVelocity, 1.2f);


                LargeFireball fireball = new LargeFireball(this.ghast.level(), this.ghast, dir, 1);

                fireball.setPos(this.ghast.getX() + dir.x * 4.0, this.ghast.getY(0.5) + 0.5, this.ghast.getZ() + dir.z * 4.0);
                this.ghast.level().addFreshEntity(fireball);

                this.ghast.level().levelEvent(null, 1016, this.ghast.blockPosition(), 0);
                this.chargeTime = -20; // Кулдаун перед наступним пострілом
                this.ghast.setCharging(false);
            }
        } else if (this.chargeTime > 0) {
            this.chargeTime--;
        }
    }
}