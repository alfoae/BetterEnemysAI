package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyMeleeBehavior;
import net.minecraft.world.entity.monster.Creeper;

public class BetterCreeperGoalAi extends PursuitEnemyMeleeBehavior {
    private final Creeper creeper;

    public BetterCreeperGoalAi(Creeper mob, double speedModifier) {
        super(mob, speedModifier);
        this.creeper = mob;
    }

    @Override
    public boolean canUse() {
        // Якщо кріпер уже роздувається (SwellDir > 0), наш AI руху не повинен заважати
        return this.creeper.getSwellDir() <= 0 && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        // Якщо під час погоні кріпер почав підрив, негайно віддаємо руль ванільному SwellGoal
        return this.creeper.getSwellDir() <= 0 && super.canContinueToUse();
    }

    @Override
    protected boolean canAttack() {
        return false;
    }

    @Override
    protected double getYieldDistanceSqr() {
        return 9.0;
    }
}