package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.PillagerWeaponManagerGoalAi;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Pillager.class)
public class PillagerMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addDualModeAI(CallbackInfo ci) {
        Pillager pillager = (Pillager) (Object) this;

        // 1. Додаємо перемикач зброї (найвищий пріоритет)
        pillager.goalSelector.addGoal(1, new PillagerWeaponManagerGoalAi(pillager));

        // 2. Додаємо логіку атаки сокирою (працюватиме ТІЛЬКИ коли в руках сокира)
        pillager.goalSelector.addGoal(2, new MeleeAttackGoal(pillager, 1.2D, false) {
            @Override
            public boolean canUse() {
                return super.canUse() && pillager.getMainHandItem().is(Items.IRON_AXE);
            }
        });
    }
}