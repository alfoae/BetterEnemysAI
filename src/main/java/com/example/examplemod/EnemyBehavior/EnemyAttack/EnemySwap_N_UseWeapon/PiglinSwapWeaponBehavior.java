package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemySwap_N_UseWeapon;

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

    private static final float SWITCH_TO_MELEE_DISTANCE = 4.0F;  // ближче цієї - точно меч
    private static final float SWITCH_TO_RANGED_DISTANCE = 6.0F; // далі цієї - точно арбалет

    public PiglinSwapWeaponBehavior() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Piglin piglin) {
        // Блокуємо старт поведінки, якщо це дитинча
        return !piglin.isBaby();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Piglin piglin, long gameTime) {
        return !piglin.isBaby() && piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
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
        // Додатковий запобіжник від видачі зброї малятам
        if (piglin.isBaby()) {
            return;
        }

        LivingEntity target = piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);

        if (target != null) {
            float distance = piglin.distanceTo(target);
            IWeaponStorage storage = (IWeaponStorage) piglin;
            ItemStack currentHand = piglin.getMainHandItem();

            // 1. Пріоритет - БЛИЖНІЙ БІЙ
            if (distance <= SWITCH_TO_MELEE_DISTANCE) {
                if (!currentHand.is(Items.GOLDEN_SWORD)) {
                    // Зберігаємо арбалет
                    if (currentHand.is(Items.CROSSBOW)) {
                        storage.setStoredRanged(currentHand.copy());
                    }

                    // Дістаємо меч
                    ItemStack storedMelee = storage.getStoredMelee();
                    if (storedMelee.isEmpty()) storedMelee = new ItemStack(Items.GOLDEN_SWORD);

                    piglin.stopUsingItem();
                    piglin.setChargingCrossbow(false);
                    piglin.setItemInHand(InteractionHand.MAIN_HAND, storedMelee);
                }
            }
            // 2. ДАЛЬНІЙ БІЙ
            else if (distance >= SWITCH_TO_RANGED_DISTANCE) {
                if (!currentHand.is(Items.CROSSBOW)) {
                    // Зберігаємо меч
                    if (currentHand.is(Items.GOLDEN_SWORD)) {
                        storage.setStoredMelee(currentHand.copy());
                    }

                    // Дістаємо арбалет
                    ItemStack storedRanged = storage.getStoredRanged();
                    if (storedRanged.isEmpty()) storedRanged = new ItemStack(Items.CROSSBOW);

                    piglin.stopUsingItem();
                    piglin.setItemInHand(InteractionHand.MAIN_HAND, storedRanged);
                }
            }
        }
    }
}