package com.example.examplemod.EnemyBehavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Базовий Goal для мелі-мобів без стрільби (зомбі, кріпер, голем тощо).
 * Розширюється окремим класом для кожного моба (BetterZombieGoalAi тощо).
 * Інтегрує PursuitEnemyBehavior: коли гравець вийшов за FOLLOW_RANGE — моб
 * біжить до застиглої останньої відомої точки або шукає гравця навколо неї.
 */
public class PursuitEnemyMeleeBehavior extends Goal {

    protected final Mob mob;
    protected final double speedModifier;

    public PursuitEnemyMeleeBehavior(Mob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Активуємось ТІЛЬКИ коли є точка переслідування (гравець за стіною або за радіусом).
        // В звичайному бою з прямою видимістю — поступаємось ванільним Goal-ам (ZombieAttackGoal тощо).
        return PursuitEnemyBehavior.getChasePosition(this.mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return PursuitEnemyBehavior.getChasePosition(this.mob) != null
                || !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.mob.setSprinting(false);
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        double sprintSpeed = PursuitEnemyBehavior.getSprintSpeedModifier(this.mob);

        if (chasePos != null) {
            boolean canSee = target != null && this.mob.getSensing().hasLineOfSight(target);
            if (!canSee) {
                this.mob.getNavigation().moveTo(chasePos.x, chasePos.y, chasePos.z, sprintSpeed);
                this.mob.getLookControl().setLookAt(
                        chasePos.x, chasePos.y + this.mob.getBbHeight() * 0.5, chasePos.z, 30.0F, 30.0F);
                this.mob.setSprinting(true);
                return;
            }
        }

        if (target != null) {
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            this.mob.getNavigation().moveTo(target, this.speedModifier);
            this.mob.setSprinting(false);
        }
    }
}