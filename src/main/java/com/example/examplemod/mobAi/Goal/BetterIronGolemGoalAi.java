package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.animal.IronGolem;

public class BetterIronGolemGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterIronGolemGoalAi(IronGolem mob, double speedModifier) {
        super(mob, speedModifier);
    }
}
