package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.EnemyBehavior.ConditionalBehavior;
import com.example.examplemod.EnemyBehavior.PiglinSwapWeaponBehavior;
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
            index = 2 // Індекс аргументу ImmutableList (список завдань)
    )
    private static ImmutableList<? extends BehaviorControl<? super Piglin>> modifyFightActivity(ImmutableList<? extends BehaviorControl<? super Piglin>> originalTasks) {
        List<BehaviorControl<? super Piglin>> tasks = new ArrayList<>();

        // Проходимося по всіх стандартних завданнях бою Пігліна
        for (BehaviorControl<? super Piglin> task : originalTasks) {
            String taskName = task.debugString().toLowerCase();

            // Знаходимо ванільну атаку ближнього бою і обгортаємо її:
            // Вона працюватиме ТІЛЬКИ тоді, коли в руках Золотий Меч
            if (taskName.contains("melee")) {
                tasks.add(new ConditionalBehavior<>(
                        piglin -> piglin.getMainHandItem().is(Items.GOLDEN_SWORD),
                        task
                ));
            }
            // Знаходимо ванільну атаку з арбалета і обгортаємо її:
            // Вона працюватиме ТІЛЬКИ тоді, коли в руках Арбалет
            else if (taskName.contains("crossbow")) {
                tasks.add(new ConditionalBehavior<>(
                        piglin -> piglin.getMainHandItem().is(Items.CROSSBOW),
                        task
                ));
            }
            // Усі інші завдання (переміщення, ухилення тощо) залишаємо без змін
            else {
                tasks.add(task);
            }
        }

        // Додаємо твою нову логіку зміни зброї в загальний список завдань
        tasks.add(new PiglinSwapWeaponBehavior());

        // Повертаємо новий змінений список назад у гру
        return ImmutableList.copyOf(tasks);
    }
}