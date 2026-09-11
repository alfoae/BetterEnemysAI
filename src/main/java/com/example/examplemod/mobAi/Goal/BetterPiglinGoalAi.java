package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.Enemy.EnemyAttack.PiglinCrossbowAttack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Items;

import java.util.Map;

/**
 * Behavior-обгортка для пігліна: перевіряє умови старту/продовження (дорослий + арбалет у
 * руці), дістає ціль з Brain-пам'яті (ATTACK_TARGET) і делегує саму crossbow-атаку
 * {@link PiglinCrossbowAttack}. Деталі поведінки (чому рух тут не ведеться, чому окремий клас
 * від піладжера/віндикатора тощо) — у javadoc там.
 */
public class BetterPiglinGoalAi extends Behavior<Piglin> {

    private final PiglinCrossbowAttack attack = new PiglinCrossbowAttack();

    public BetterPiglinGoalAi() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Piglin piglin) {
        return !piglin.isBaby() && piglin.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Piglin piglin, long gameTime) {
        return !piglin.isBaby()
                && piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && piglin.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    protected void start(ServerLevel level, Piglin piglin, long gameTime) {
        this.attack.reset();
    }

    @Override
    protected void stop(ServerLevel level, Piglin piglin, long gameTime) {
        this.attack.onStop(piglin);
    }

    @Override
    protected void tick(ServerLevel level, Piglin piglin, long gameTime) {
        LivingEntity target = piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) return;

        this.attack.tick(piglin, target);
    }
}
