package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.BetterEnemysAI;
import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyMeleeBehavior;
import com.example.examplemod.mobAi.Goal.BetterDrownedGoalAi;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Drowned.class)
public class DrownedMixin {

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("TAIL"))
    private void examplemod$replaceDrownedTridentGoal(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        Drowned drowned = (Drowned) (Object) this;

        // ГАРД: populateDefaultEquipmentSlots — той самий тип методу, що й <init> у Piglin, що
        // виявився здатним спрацювати більше одного разу на ту саму сутність. Якщо це трапляється
        // тут теж — без гарда другий прохід не знайде що видаляти (Trident/MeleeAttackGoal уже
        // прибрані першим проходом), зате додасть ДРУГУ незалежну копію PursuitEnemyBehavior і
        // BetterDrownedGoalAi з власними, розсинхронізованими attackTime/seeTime/strafingTime.
        // ВАЖЛИВО: щоб цей гард не з'їдав ЛЕГІТИМНЕ перше спрацювання — ZombieMixin ОБОВ'ЯЗКОВО
        // повинен ігнорувати Drowned (Drowned extends Zombie!), інакше його PursuitEnemyBehavior
        // (доданий раніше, через registerGoals()) буде тут сприйнятий як "вже є" і весь цей блок
        // пропуститься — саме це й було справжньою причиною "дровнед стріляє без випередження".
        boolean alreadyAdded = drowned.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof PursuitEnemyBehavior);
        if (alreadyAdded) {
            BetterEnemysAI.LOGGER.warn("DrownedMixin: replaceDrownedTridentGoal fired more than once "
                    + "for {} -- populateDefaultEquipmentSlots called >1 time, skipping duplicate", drowned);
            return;
        }

        drowned.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal().getClass().getName().contains("Trident")
                        || goal.getGoal() instanceof MeleeAttackGoal
        );

        drowned.goalSelector.addGoal(0, new PursuitEnemyBehavior(drowned, true, 1.0));
        drowned.goalSelector.addGoal(1, new BetterDrownedGoalAi(drowned, 1.0D, 40));

        // Melee-фолбек — ЛИШЕ коли дровнед НЕ тримає тризуб (BetterDrownedGoalAi.canUse() і так
        // вимагає тризуб, тож ці два Goal-и за конструкцією ніколи не претендують на керування
        // одночасно — номер пріоритету тут не про конфлікт між ними, а суто для чистоти списку).
        // Без цього тризубний дровнед завжди мав хоч щось (ванільний Trident-Goal, поки не
        // видалений), а без тризуба — не мав НІЧОГО: PursuitEnemyBehavior коректно вів стан
        // CHASING/GOING_TO_LAST_SEEN/SEARCHING, але жоден Goal рух і атаку не забирав.
        drowned.goalSelector.addGoal(2, new PursuitEnemyMeleeBehavior(drowned, 1.0D,
                m -> !m.isHolding(is -> is.getItem() instanceof TridentItem)));

        drowned.setCanPickUpLoot(true);
    }
}