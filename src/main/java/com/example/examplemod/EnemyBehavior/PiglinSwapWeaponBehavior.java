package com.example.examplemod.EnemyBehavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public class PiglinSwapWeaponBehavior extends Behavior<Piglin> {

    // Дистанція, на якій Піглін ховає арбалет і дістає меч
    private static final float MELEE_DISTANCE = 5.0F;

    public PiglinSwapWeaponBehavior() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Piglin piglin, long gameTime) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected void start(ServerLevel level, Piglin piglin, long gameTime) {
        swapWeaponIfNeeded(piglin);
    }

    @Override
    protected void tick(ServerLevel level, Piglin piglin, long gameTime) {
        swapWeaponIfNeeded(piglin);
    }

    private void swapWeaponIfNeeded(Piglin piglin) {
        LivingEntity target = piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);

        if (target != null) {
            float distance = piglin.distanceTo(target);

            // Отримуємо його максимальний радіус зору (за замовчуванням 16)
            double followRange = piglin.getAttributeValue(Attributes.FOLLOW_RANGE);

            // Рахуємо 3/4 від цього радіусу (це його максимальна зона стрільби)
            double maxShootDistance = followRange * 0.75;

            if (distance <= MELEE_DISTANCE) {
                // Ми ближче ніж 5 блоків — беремо МЕЧ
                if (!piglin.getMainHandItem().is(Items.GOLDEN_SWORD)) {
                    piglin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_SWORD));
                }
            } else if (distance <= maxShootDistance) {
                // Ми в ідеальній зоні (від 5 блоків до 3/4 радіусу) — беремо АРБАЛЕТ
                if (!piglin.getMainHandItem().is(Items.CROSSBOW)) {
                    piglin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
                }
            } else {
                // Якщо гравець ще далі (піглін тільки помітив його і біжить назустріч),
                // теж тримаємо арбалет напоготові
                if (!piglin.getMainHandItem().is(Items.CROSSBOW)) {
                    piglin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
                }
            }
        }
    }
}