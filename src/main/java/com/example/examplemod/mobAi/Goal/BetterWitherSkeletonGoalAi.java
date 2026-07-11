package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.WitherSkeleton;

public class BetterWitherSkeletonGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterWitherSkeletonGoalAi(WitherSkeleton mob, double speedModifier) {
        super(mob, speedModifier);
        mob.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true));
    }
}
