package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.CustomCrossbowShootGoal;
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
        Pillager mob = (Pillager) (Object) this; // У VindicatorMixin тут буде Vindicator

        // 1. Фонова зміна зброї (Не блокує рух, пріоритет 1)
        mob.goalSelector.addGoal(1, new SwapWeaponGoal(mob));

        // 2. Наша кастомна стрільба з випередженням (Працює тільки коли далеко)
        mob.goalSelector.addGoal(2, new CustomCrossbowShootGoal(mob));

        // 3. Ближній бій (Працює тільки коли близько і є топор)
        mob.goalSelector.addGoal(3, new MeleeAttackGoal(mob, 1.2D, false) {
            @Override
            public boolean canUse() {
                return super.canUse() && mob.getMainHandItem().is(Items.IRON_AXE);
            }
        });
    }

    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private void fixPillagerPose(CallbackInfoReturnable<AbstractIllager.IllagerArmPose> cir) {
        Pillager pillager = (Pillager) (Object) this;

        if (pillager.getMainHandItem().is(Items.CROSSBOW)) {
            if (pillager.isUsingItem()) {
                cir.setReturnValue(AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE);
            } else if (pillager.isAggressive()) {
                // Саме це змушує його опустити арбалет горизонтально після зарядки
                cir.setReturnValue(AbstractIllager.IllagerArmPose.CROSSBOW_HOLD);
            }
        }
    }
}