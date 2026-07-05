package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.Creeper;

public class BetterCreeperGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterCreeperGoalAi(Creeper mob, double speedModifier) {
        super(mob, speedModifier);
        mob.goalSelector.addGoal(0, new PursuitEnemyMeleeBehavior(mob, speedModifier));
    }
}
