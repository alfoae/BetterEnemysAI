package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.BetterZombieGoalAi;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Zombie.class)
public class ZombieMixin {

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addBetterAI(CallbackInfo ci) {
        Zombie mob = (Zombie) (Object) this;
        mob.goalSelector.addGoal(1, new BetterZombieGoalAi(mob, 1.0D));
    }
}
