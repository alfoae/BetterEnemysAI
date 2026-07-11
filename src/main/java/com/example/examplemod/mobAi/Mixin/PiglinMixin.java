package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.EnemyBehavior.EnemySwap_and_UseWeapon.EnemySwap_and_UseWeaponConditionalBehavior;
import com.example.examplemod.EnemyBehavior.EnemySwap_and_UseWeapon.PiglinSwapWeaponBehavior;
import com.example.examplemod.mobAi.Goal.BetterPiglinGoalAi;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.ArrayList;
import java.util.List;

@Mixin(PiglinAi.class)
public class PiglinMixin {

    @ModifyArg(
            method = "initFightActivity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;addActivityAndRemoveMemoryWhenStopped(Lnet/minecraft/world/entity/schedule/Activity;ILcom/google/common/collect/ImmutableList;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)V"
            ),
            index = 2
    )
    private static ImmutableList<? extends BehaviorControl<? super Piglin>> modifyFightActivity(ImmutableList<? extends BehaviorControl<? super Piglin>> originalTasks) {
        List<BehaviorControl<? super Piglin>> tasks = new ArrayList<>();

        for (BehaviorControl<? super Piglin> task : originalTasks) {
            String taskName = task.debugString().toLowerCase();

            if (taskName.contains("melee")) {
                tasks.add(new EnemySwap_and_UseWeaponConditionalBehavior<>(
                        piglin -> piglin.getMainHandItem().is(Items.GOLDEN_SWORD),
                        task
                ));
            } else if (taskName.contains("crossbow")) {
                // видаляємо ванільну crossbow-задачу
            } else {
                tasks.add(task);
            }
        }

        tasks.add(new PiglinSwapWeaponBehavior());
        tasks.add(new BetterPiglinGoalAi());

        return ImmutableList.copyOf(tasks);
    }
}
