package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyMeleeBehavior;
import com.example.examplemod.mobAi.Goal.BetterPillagerVindicatorGoalAi;
import com.example.examplemod.mobAi.Goal.SwapWeaponGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Pillager.class)
public class PillagerMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addDualModeAI(CallbackInfo ci) {
        Pillager mob = (Pillager) (Object) this;

        // Прибираємо ДО додавання свого MeleeAttackGoal нижче (щоб не знести його ж).
        // У ванільного Pillager мелі-гоала зазвичай нема (тільки арбалет), але про всяк
        // випадок — якщо колись з'явиться/додасться іншим мод, тут теж safety net.
        mob.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof MeleeAttackGoal
        );

        mob.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true, 1.0));
        mob.goalSelector.addGoal(1, new SwapWeaponGoal(mob));
        mob.goalSelector.addGoal(2, new BetterPillagerVindicatorGoalAi(mob));
        mob.goalSelector.addGoal(3, new PursuitEnemyMeleeBehavior(mob, 1.2D,
                m -> m.getMainHandItem().is(Items.IRON_AXE)));
    }

    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private void fixPillagerPose(CallbackInfoReturnable<AbstractIllager.IllagerArmPose> cir) {
        Pillager pillager = (Pillager) (Object) this;

        if (pillager.getMainHandItem().is(Items.CROSSBOW)) {
            if (pillager.isUsingItem()) {
                cir.setReturnValue(AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE);
            } else if (pillager.isAggressive()) {
                cir.setReturnValue(AbstractIllager.IllagerArmPose.CROSSBOW_HOLD);
            }
        }
    }
}
