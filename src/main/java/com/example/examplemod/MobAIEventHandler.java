package com.example.examplemod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.function.Predicate;

@EventBusSubscriber(modid = BetterEnemysAI.MODID)
public class MobAIEventHandler {

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (!Config.ENABLE_CUSTOM_AI.get()) return;

        if (event.getEntity() instanceof Mob mob) {

            // 1. ВИЗНАЧАЄМО ФРАКЦІЮ МОБА, ЯКИЙ З'ЯВИВСЯ
            boolean isIllagerFaction = mob instanceof AbstractIllager || mob instanceof Ravager || mob instanceof Vex;
            boolean isPiglinFaction = mob instanceof AbstractPiglin;
            boolean isWitch = mob instanceof Witch;

            // Фракція Монстрів: всі ворожі моби, які НЕ є розбійниками, піглінами чи відьмами
            // (сюди автоматично потрапляють зомбі, скелети, павуки, кріпери, зогліни, зомбі-пігліни тощо)
            boolean isMonsterFaction = mob instanceof Monster && !isIllagerFaction && !isPiglinFaction && !isWitch;

            // 2. СТВОРЮЄМО ФІЛЬТРИ ДЛЯ ПОШУКУ ВОРОГІВ (хто кого б'є)

            // Вороги для Монстрів: б'ють Розбійників та Піглінів
            Predicate<LivingEntity> monsterTargets = (target) ->
                    target instanceof AbstractIllager || target instanceof Ravager || target instanceof Vex ||
                            target instanceof AbstractPiglin;

            // Вороги для Розбійників: б'ють Монстрів (але Відьму не чіпають!) та Піглінів
            Predicate<LivingEntity> illagerTargets = (target) ->
                    (target instanceof Monster && !(target instanceof AbstractIllager) && !(target instanceof Ravager) && !(target instanceof Vex) && !(target instanceof AbstractPiglin) && !(target instanceof Witch)) ||
                            target instanceof AbstractPiglin;

            // Вороги для Піглінів: б'ють Монстрів (включаючи Відьму), Розбійників, Жителів та Големів
            Predicate<LivingEntity> piglinTargets = (target) ->
                    (target instanceof Monster && !(target instanceof AbstractPiglin)) ||
                            target instanceof AbstractVillager || target instanceof IronGolem;

            // 3. ДОДАЄМО ЦІЛІ В МОЗОК МОБАМ ВІДПОВІДНО ДО ЇХНЬОЇ ФРАКЦІЇ
            // Ми додаємо пріоритет 2 (щоб пріоритет 1 ми потім залишили для гравця!)
            if (isMonsterFaction) {
                // Монстри шукають цілі згідно з фільтром monsterTargets
                mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Mob.class, 0, true, false, monsterTargets));
            } else if (isIllagerFaction) {
                // Розбійники шукають цілі згідно з фільтром illagerTargets
                mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Mob.class, 0, true, false, illagerTargets));
            } else if (isPiglinFaction) {
                // Пігліни шукають цілі згідно з фільтром piglinTargets
                mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, Mob.class, 0, true, false, piglinTargets));
            }

            // (Големи вже в оригінальній грі запрограмовані бити всіх монстрів, розбійників та піглінів, тому їх AI не чіпаємо)
        }
    }
}