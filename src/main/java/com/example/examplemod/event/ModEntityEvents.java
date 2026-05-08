package com.example.examplemod.event;

import com.example.examplemod.mobAi.Skeleton;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = "betterenemysai")
public class ModEntityEvents {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {

        if (!(event.getEntity() instanceof AbstractSkeleton skeleton)) {
            return;
        }

        // тільки сервер
        if (event.getLevel().isClientSide()) {
            return;
        }

        // видаляємо ванільний AI стрільби
        skeleton.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof RangedBowAttackGoal
        );

        // додаємо твій AI з ВИСОКИМ пріоритетом
        skeleton.goalSelector.addGoal(1, new Skeleton(skeleton, 1.0D, 20));

        System.out.println("CUSTOM SKELETON AI LOADED");
    }
}