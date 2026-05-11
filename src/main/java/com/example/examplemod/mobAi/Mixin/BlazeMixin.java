package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterBlazeGoalAi;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Blaze.class)
public class BlazeMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void examplemod$replaceBlazeGoal(CallbackInfo ci) {

        Blaze blaze = (Blaze) (Object) this;

        blaze.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof RangedAttackGoal
        );

        blaze.goalSelector.addGoal(
                1,
                new BetterBlazeGoalAi(blaze)
        );
    }
}