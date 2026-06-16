package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterDrownedTridentGoalAi;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Drowned.class)
public class DrownedMixin {

    // Впорскуємося в кінець методу, який налаштовує спорядження моба при спавні
    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void examplemod$replaceDrownedTridentGoal(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        Drowned drowned = (Drowned) (Object) this;

        // Видаляємо оригінальний ванільний гоал атаки тризубцем
        drowned.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal().getClass().getName().contains("Trident")
        );

        // Додаємо наш кастомний гоал з AdvancedAimMath
        // Пріоритет 2 (як у ванілі), швидкість 1.0D, інтервал між атаками — 40 тіків (2 секунди)
        drowned.goalSelector.addGoal(
                2,
                new BetterDrownedTridentGoalAi(drowned, 1.0D, 40)
        );

        // Дозволяємо абсолютно всім утопленикам підбирати речі з землі (в тому числі кинуті тризубці)
        drowned.setCanPickUpLoot(true);
    }
}