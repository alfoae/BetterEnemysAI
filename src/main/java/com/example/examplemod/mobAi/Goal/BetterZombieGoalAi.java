package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.Zombie;

public class BetterZombieGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterZombieGoalAi(Zombie mob, double speedModifier) {
        // canYieldToTerraforming=true — Zombie єдиний зараз має BuildPathGoal/DigThroughWallsGoal
        // (див. ZombieMixin), тож лише тут є кому передати чергу, коли цей Goal сам зупиняється.
        super(mob, speedModifier, m -> true, true);
    }
}
