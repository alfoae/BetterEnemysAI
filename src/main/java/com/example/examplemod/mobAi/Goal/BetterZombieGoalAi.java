package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.Zombie;

public class BetterZombieGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterZombieGoalAi(Zombie mob, double speedModifier) {
        super(mob, speedModifier);
        mob.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true));
    }
}
