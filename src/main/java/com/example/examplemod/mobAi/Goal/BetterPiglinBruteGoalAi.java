package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

public class BetterPiglinBruteGoalAi extends PursuitEnemyMeleeBehavior {

    public BetterPiglinBruteGoalAi(PiglinBrute mob, double speedModifier) {
        super(mob, speedModifier);
        mob.goalSelector.addGoal(0, new PursuitEnemyMeleeBehavior(mob, speedModifier));
    }
}
