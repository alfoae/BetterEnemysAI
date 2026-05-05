package com.example.examplemod.event;

// ВАЖЛИВО: Переконайся, що тут імпортується ТВІЙ клас з папки mobAi, а не ванільний моб Skeleton!

import com.example.examplemod.mobAi.Skeleton;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// Видалили deprecated параметр bus, залишили тільки modid
@EventBusSubscriber(modid = "betterenemysai")
public class ModEntityEvents {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof AbstractSkeleton skeleton && !event.getLevel().isClientSide()) {

            // Видаляємо старий ШІ
            skeleton.goalSelector.getAvailableGoals().removeIf(prioritizedGoal ->
                    prioritizedGoal.getGoal() instanceof RangedBowAttackGoal);

            // Додаємо твій новий ШІ (переконайся, що імпорт з mobAi правильний)
            skeleton.goalSelector.addGoal(3, new Skeleton(skeleton, 1.0D, 20));
        }
    }
}