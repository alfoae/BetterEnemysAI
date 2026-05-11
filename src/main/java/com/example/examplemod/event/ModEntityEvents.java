package com.example.examplemod.event;

import com.example.examplemod.mobAi.Goal.BetterBlazeGoalAi;
import com.example.examplemod.mobAi.Goal.BetterGhastGoalAi;
import com.example.examplemod.mobAi.Goal.BetterSkeletonGoalAi;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

@EventBusSubscriber(modid = "betterenemysai")
public class ModEntityEvents {

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        // Працюємо тільки на сервері
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Обробка Скелета
        if (event.getEntity() instanceof AbstractSkeleton skeleton) {
            skeleton.goalSelector.getAvailableGoals().removeIf(goal ->
                    goal.getGoal() instanceof RangedBowAttackGoal
            );
            skeleton.goalSelector.addGoal(1, new BetterSkeletonGoalAi(skeleton, 1.0D, 20));
            System.out.println("CUSTOM SKELETON AI LOADED");
        }

        // Обробка Гаста
        if (event.getEntity() instanceof Ghast ghast) {
            ghast.goalSelector.getAvailableGoals().removeIf(goal ->
                    goal.getGoal().getClass().getName().contains("GhastShootFireballGoal")
            );
            ghast.goalSelector.addGoal(2, new BetterGhastGoalAi(ghast));
            System.out.println("CUSTOM GHAST AI LOADED");
        }

        if (event.getEntity() instanceof Blaze blaze) {
            // Видаляємо ванільну стрільбу
            blaze.goalSelector.getAvailableGoals().removeIf(goal ->
                    goal.getGoal().getClass().getName().contains("BlazeAttackGoal")
            );

            // Додаємо наш кулеметний AI
            blaze.goalSelector.addGoal(4, new BetterBlazeGoalAi(blaze));
            System.out.println("CUSTOM BLAZE AI LOADED");
        }
    }
}