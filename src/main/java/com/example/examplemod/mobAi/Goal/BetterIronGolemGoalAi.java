package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.animal.IronGolem;

public class BetterIronGolemGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterIronGolemGoalAi(IronGolem mob, double speedModifier) {
        super(mob, speedModifier);
        mob.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true, 1.0));
    }
}
