package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterSkeletonGoalAi;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSkeleton.class)
public class SkeletonMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void examplemod$replaceSkeletonGoal(CallbackInfo ci) {

        AbstractSkeleton skeleton = (AbstractSkeleton) (Object) this;

        skeleton.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof RangedBowAttackGoal
        );

        skeleton.goalSelector.addGoal(
                1,
                new BetterSkeletonGoalAi(skeleton, 1.0D, 20)
        );
    }
}