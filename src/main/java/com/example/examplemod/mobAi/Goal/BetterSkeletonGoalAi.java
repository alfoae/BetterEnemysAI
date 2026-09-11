package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.Enemy.EnemyAttack.SkeletonBowAttack;
import com.example.examplemod.Enemy.EnemyMovement.SkeletonStrafeMovement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.BowItem;

import java.util.EnumSet;

/**
 * Goal-обгортка для скелета (і будь-якого {@link AbstractSkeleton}): перевіряє умови
 * (ціль + лук у руці), щотіку рахує {@code canSee}/{@code seeTime} (спільні і для руху, і для
 * стрільби — рахувати їх двічі означало б дублювати distanceSq/hasLineOfSight), і делегує рух
 * {@link SkeletonStrafeMovement}, а стрільбу {@link SkeletonBowAttack}.
 */
public class BetterSkeletonGoalAi extends Goal {
    private final AbstractSkeleton mob;
    private final SkeletonStrafeMovement movement;
    private final SkeletonBowAttack attack;

    private int seeTime;

    public BetterSkeletonGoalAi(AbstractSkeleton mob, double speedModifier, int attackIntervalMin) {
        this.mob = mob;
        this.movement = new SkeletonStrafeMovement(mob, speedModifier);
        this.attack = new SkeletonBowAttack(mob, attackIntervalMin);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() != null && this.isHoldingBow();
    }

    private boolean isHoldingBow() {
        return this.mob.isHolding(is -> is.getItem() instanceof BowItem);
    }

    @Override
    public boolean canContinueToUse() {
        return (this.canUse() || !this.mob.getNavigation().isDone());
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
        this.seeTime = 0;
        this.attack.reset();
        this.mob.stopUsingItem();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        double distanceSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double maxShootDistanceSq = (followRange * 0.75) * (followRange * 0.75);
        // canSee враховує і пряму видимість І дистанцію стрільби — Sensing.hasLineOfSight()
        // не перевіряє FOLLOW_RANGE, тому без цієї перевірки скелет міг стріляти за межею радіуса.
        boolean canSee = distanceSq <= maxShootDistanceSq && this.mob.getSensing().hasLineOfSight(target);
        boolean isSeeing = this.seeTime > 0;

        if (canSee != isSeeing) {
            this.seeTime = 0;
        }
        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        this.movement.tick(target, distanceSq, canSee, this.seeTime);
        this.attack.tick(target, canSee);
    }
}
