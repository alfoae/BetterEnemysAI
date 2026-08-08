package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.Zombie;

public class BetterZombieGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterZombieGoalAi(Zombie mob, double speedModifier) {
        super(mob, speedModifier);
    }
}
