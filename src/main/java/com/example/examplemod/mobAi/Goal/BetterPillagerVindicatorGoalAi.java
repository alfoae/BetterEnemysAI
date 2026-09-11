package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.Enemy.EnemyAttack.PillagerVindicatorCrossbowAttack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * Goal-обгортка для піладжера й віндикатора: перевіряє умови (ціль + арбалет у руці) і
 * делегує саму crossbow-атаку {@link PillagerVindicatorCrossbowAttack}.
 */
public class BetterPillagerVindicatorGoalAi extends Goal {
    private final Monster mob;
    private final PillagerVindicatorCrossbowAttack attack;

    public BetterPillagerVindicatorGoalAi(Monster mob) {
        this.mob = mob;
        this.attack = new PillagerVindicatorCrossbowAttack(mob);
        this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    public void start() {
        this.attack.reset();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        this.attack.tick(target);
    }

    @Override
    public void stop() {
        this.attack.onStop();
    }
}
