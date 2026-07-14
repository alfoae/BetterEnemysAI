package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyBehavior;
import com.example.examplemod.mobAi.Goal.BetterWitherSkeletonGoalAi;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.WitherSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherSkeleton.class)
public class WitherSkeletonMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addBetterAI(CallbackInfo ci) {
        WitherSkeleton mob = (WitherSkeleton) (Object) this;

        // meleeGoal з AbstractSkeleton (плейн MeleeAttackGoal) — WitherSkeleton юзає саме його,
        // а не лук, тому прибираємо так само, як у Zombie.
        mob.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof MeleeAttackGoal
        );

        mob.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true));
        mob.goalSelector.addGoal(1, new BetterWitherSkeletonGoalAi(mob, 1.0D));
    }
}
