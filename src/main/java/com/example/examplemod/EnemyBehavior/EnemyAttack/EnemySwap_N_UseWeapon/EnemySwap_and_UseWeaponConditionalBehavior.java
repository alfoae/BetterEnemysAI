package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemySwap_N_UseWeapon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.function.Predicate;

public class EnemySwap_and_UseWeaponConditionalBehavior<E extends LivingEntity> implements BehaviorControl<E> {

    private final Predicate<E> condition;
    private final BehaviorControl<? super E> wrapped;

    public EnemySwap_and_UseWeaponConditionalBehavior(Predicate<E> condition, BehaviorControl<? super E> wrapped) {
        this.condition = condition;
        this.wrapped = wrapped;
    }

    @Override
    public Behavior.Status getStatus() {
        return this.wrapped.getStatus();
    }

    @Override
    public boolean tryStart(ServerLevel level, E entity, long gameTime) {
        // Якщо умова не виконується (наприклад, не той предмет) — навіть не починаємо
        if (!this.condition.test(entity)) {
            return false;
        }
        return this.wrapped.tryStart(level, entity, gameTime);
    }

    @Override
    public void tickOrStop(ServerLevel level, E entity, long gameTime) {
        // Якщо умова раптом перестала виконуватись (змінилась зброя) — примусово зупиняємо
        if (!this.condition.test(entity) && this.getStatus() == Behavior.Status.RUNNING) {
            this.wrapped.doStop(level, entity, gameTime);
            return;
        }
        this.wrapped.tickOrStop(level, entity, gameTime);
    }

    @Override
    public void doStop(ServerLevel level, E entity, long gameTime) {
        this.wrapped.doStop(level, entity, gameTime);
    }

    @Override
    public String debugString() {
        return "ConditionalBehavior";
    }
}