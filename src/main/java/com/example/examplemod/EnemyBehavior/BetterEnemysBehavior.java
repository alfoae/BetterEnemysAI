package com.example.examplemod.EnemyBehavior;

import com.example.examplemod.BetterEnemysAI;
import com.example.examplemod.Config;
import net.minecraft.world.entity.Entity;
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
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.function.Predicate;

@EventBusSubscriber(modid = BetterEnemysAI.MODID)
public class BetterEnemysBehavior {

    /**
     * Визначає фракцію сутності за тими ж правилами, що й onEntityJoinLevel нижче.
     * Викликати з Goal/Mixin класів для перевірки "свій/чужий" перед стрільбою.
     */
    public static Faction getFaction(Entity entity) {
        if (entity instanceof AbstractIllager || entity instanceof Ravager || entity instanceof Vex) {
            return Faction.ILLAGER;
        }
        if (entity instanceof AbstractPiglin) {
            return Faction.PIGLIN;
        }
        if (entity instanceof Witch) {
            // Відьма ні з ким не дружить (її проти неї теж ніхто не б'є за правилами нижче,
            // але вона сама не входить ні в одну фракцію), тож вважаємо її окремо.
            return Faction.OTHER;
        }
        if (entity instanceof Monster) {
            return Faction.MONSTER;
        }
        return Faction.OTHER;
    }

    /**
     * Чи належать дві сутності до однієї (не OTHER) фракції.
     * Гравці, голем, жителі (OTHER) НІКОЛИ не вважаються "своїми".
     */
    public static boolean isSameFaction(Entity a, Entity b) {
        Faction fa = getFaction(a);
        Faction fb = getFaction(b);
        return fa != Faction.OTHER && fa == fb;
    }

    /**
     * Якщо снаряд (чи будь-який інший урон) одного моба зачепив союзника по фракції,
     * постраждалий НЕ переключає ціль на свого ж — урон лишається, агро не з'являється.
     * Працює для всіх мобів одразу, без правок у кожному Goal/Mixin окремо.
     */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!Config.ENABLE_CUSTOM_AI.get()) return;

        // entity      — той, у кого зараз намагається змінитись ціль (наприклад зомбі, по якому випадково влучив скелет)
        // newTarget   — на кого entity хоче переключитись (тут — скелет, союзник по фракції)
        LivingEntity entity = event.getEntity();
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) return;

        if (isSameFaction(entity, newTarget)) {
            event.setCanceled(true); // забуваємо про "удар", не агримось на свого
        }
    }

    // ========================================================
    // НЕ АГРИТИСЬ НА СВОЇХ ПРИ ВИПАДКОВОМУ ПОПАДАННІ
    // ========================================================

    // ========================================================
    // ФРАКЦІЇ
    // ========================================================
    public enum Faction {
        MONSTER,   // зомбі, скелети, павуки, кріпери, зогліни, потоплені тощо
        ILLAGER,   // розбійники, рейвагер, векс
        PIGLIN,    // пігліни (звичайні й бруті, не зомбі-пігліни — ті MONSTER)
        OTHER      // гравці, жителі, голем, тварини — нікому не "свої"
    }

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
            // PursuitEnemyBehavior реєструється окремо в кожному Goal/Mixin файлі моба.
        }
    }
}