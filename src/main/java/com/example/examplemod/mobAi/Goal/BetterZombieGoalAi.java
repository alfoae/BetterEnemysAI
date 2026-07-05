package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.Zombie;

public class BetterZombieGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterZombieGoalAi(Zombie mob, double speedModifier) {
        super(mob, speedModifier);
        mob.goalSelector.addGoal(0, new PursuitEnemyMeleeBehavior(mob, speedModifier));
    }
}
