package com.example.examplemod.EnemyBehavior.EnemySwap_and_UseWeapon;

import com.example.examplemod.utils.IWeaponStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public class PiglinSwapWeaponBehavior extends Behavior<Piglin> {

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
            IWeaponStorage storage = (IWeaponStorage) piglin;
            ItemStack currentHand = piglin.getMainHandItem();

            // 1. Пріоритет - БЛИЖНІЙ БІЙ
            if (distance <= MELEE_DISTANCE) {
                if (!currentHand.is(Items.GOLDEN_SWORD)) {
                    // Зберігаємо арбалет
                    if (currentHand.is(Items.CROSSBOW)) {
                        storage.setStoredRanged(currentHand.copy());
                    }

                    // Дістаємо меч
                    ItemStack storedMelee = storage.getStoredMelee();
                    if (storedMelee.isEmpty()) storedMelee = new ItemStack(Items.GOLDEN_SWORD);

                    piglin.setItemInHand(InteractionHand.MAIN_HAND, storedMelee);
                }
            }
            // 2. ДАЛЬНІЙ БІЙ (якщо дистанція більша за ближню)
            else {
                if (!currentHand.is(Items.CROSSBOW)) {
                    // Зберігаємо меч
                    if (currentHand.is(Items.GOLDEN_SWORD)) {
                        storage.setStoredMelee(currentHand.copy());
                    }

                    // Дістаємо арбалет
                    ItemStack storedRanged = storage.getStoredRanged();
                    if (storedRanged.isEmpty()) storedRanged = new ItemStack(Items.CROSSBOW);

                    piglin.setItemInHand(InteractionHand.MAIN_HAND, storedRanged);
                }
            }
        }
    }
}