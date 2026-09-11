package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.Enemy.EnemyAttack.BlazeFireballAttack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;

import java.util.EnumSet;

/**
 * Goal-обгортка для блейза: перевіряє умови (є жива ціль) і делегує саму атаку
 * {@link BlazeFireballAttack} (включно з мінімальним наближенням до цілі — див. javadoc там).
 */
public class BetterBlazeGoalAi extends Goal {
    private final Blaze blaze;
    private final BlazeFireballAttack attack;

    public BetterBlazeGoalAi(Blaze blaze) {
        this.blaze = blaze;
        this.attack = new BlazeFireballAttack(blaze);
        // Дозволяємо йому і дивитись, і рухатись під час атаки
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.blaze.getTarget();
        return target != null && target.isAlive();
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
        LivingEntity target = this.blaze.getTarget();
        if (target == null) return;

        this.attack.tick(target);
    }
}
