package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterGhastGoalAi;
import net.minecraft.world.entity.monster.Ghast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Ghast.class)
public class GhastMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void examplemod$replaceGhastGoal(CallbackInfo ci) {

        Ghast ghast = (Ghast) (Object) this;

        ghast.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal().getClass().getName().contains("GhastShootFireballGoal")
        );

        ghast.goalSelector.addGoal(
                1,
                new BetterGhastGoalAi(ghast)
        );
    }
}