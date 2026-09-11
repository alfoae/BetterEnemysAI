package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.Enemy.EnemyAttack.GhastFireballAttack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;

import java.util.EnumSet;

/**
 * Goal-обгортка для гаста: перевіряє умови (є ціль) і делегує саму атаку
 * {@link GhastFireballAttack}. Гаст не переслідує ціль цим Goal-ом (летить своїм ванільним
 * рухом), тому окремого класу руху тут нема.
 */
public class BetterGhastGoalAi extends Goal {
    private final Ghast ghast;
    private final GhastFireballAttack attack;

    public BetterGhastGoalAi(Ghast ghast) {
        this.ghast = ghast;
        this.attack = new GhastFireballAttack(ghast);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.ghast.getTarget() != null;
    }

    @Override
    public void start() {
        this.attack.reset();
    }

    @Override
    public void stop() {
        this.attack.onStop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.ghast.getTarget();
        if (target == null) return;

        this.attack.tick(target);
    }
}
