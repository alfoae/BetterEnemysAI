package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterPiglinBruteGoalAi;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.piglin.PiglinBruteAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PiglinBruteAi.class)
public class PiglinBruteMixin {

    @Inject(method = "makeBrain", at = @At("RETURN"), cancellable = true)
    private static void addBetterAI(PiglinBrute piglinBrute, Brain<PiglinBrute> brain, CallbackInfoReturnable<Brain<?>> cir) {
        piglinBrute.goalSelector.addGoal(1, new BetterPiglinBruteGoalAi(piglinBrute, 1.0D));
    }
}
