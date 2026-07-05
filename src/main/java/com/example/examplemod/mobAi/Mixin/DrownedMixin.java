package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterDrownedGoalAi;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.monster.Drowned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Drowned.class)
public class DrownedMixin {

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void examplemod$replaceDrownedTridentGoal(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        Drowned drowned = (Drowned) (Object) this;

        drowned.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal().getClass().getName().contains("Trident")
        );

        drowned.goalSelector.addGoal(2, new BetterDrownedGoalAi(drowned, 1.0D, 40));

        drowned.setCanPickUpLoot(true);
    }
}