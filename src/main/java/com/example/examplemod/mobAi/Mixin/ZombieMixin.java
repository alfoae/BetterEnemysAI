package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build.BuildPathGoal;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build.DigThroughWallsGoal;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import com.example.examplemod.mobAi.Goal.BetterZombieGoalAi;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Drowned;
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

        // ОБОВ'ЯЗКОВО, не опціонально: Drowned extends Zombie у ванілі, тож цей інжект у
        // registerGoals() спрацьовує і для нього — а це ЗАВЖДИ ламає DrownedMixin, незалежно
        // від пріоритету BetterZombieGoalAi. Причина не в конфлікті прапорців (це окрема,
        // другорядна проблема) — а в тому, що registerGoals() виконується РАНІШЕ за
        // populateDefaultEquipmentSlots(), тож коли доходить черга до DrownedMixin, його гард
        // від подвійної реєстрації бачить PursuitEnemyBehavior, щойно доданий ЗВІДСИ, і
        // пропускає ввесь свій блок — разом із видаленням ванільного Trident-Goal і додаванням
        // BetterDrownedGoalAi. Результат: дровнед лишається на чистій ванілі (без prediction,
        // без getChasePosition) незалежно від того, яку цифру пріоритету поставити нижче.
        if (mob instanceof Drowned) {
            return;
        }

        // Ванільний ZombieAttackGoal (public, extends MeleeAttackGoal) — прибираємо,
        // щоб не конфліктував по флагах MOVE/LOOK з PursuitEnemyMeleeBehavior.
        mob.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof MeleeAttackGoal
        );

        mob.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true));
        // Пріоритет 1 (вищий за BetterZombieGoalAi на 2) - обидва тримають MOVE+LOOK, і коли
        // копання дійсно потрібне (шлях заблокований), воно повинне перебивати звичайний рух.
        // Коли шлях вільний - canUse() тут false, і BetterZombieGoalAi спокійно керує сам.
        mob.goalSelector.addGoal(1, new BuildPathGoal(mob));
        mob.goalSelector.addGoal(2, new DigThroughWallsGoal(mob));
        mob.goalSelector.addGoal(3, new BetterZombieGoalAi(mob, 1.0D));
    }
}