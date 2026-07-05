package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterCreeperGoalAi;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public class CreeperMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addBetterAI(CallbackInfo ci) {
        Creeper mob = (Creeper) (Object) this;
        mob.goalSelector.addGoal(1, new BetterCreeperGoalAi(mob, 1.0D));
    }
}
