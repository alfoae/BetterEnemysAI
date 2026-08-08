package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.BetterEnemysAI;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitBrainBridgeGoal;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemySwap_N_UseWeapon.EnemySwap_and_UseWeaponConditionalBehavior;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemySwap_N_UseWeapon.PiglinSwapWeaponBehavior;
import com.example.examplemod.mobAi.Goal.BetterPiglinGoalAi;
import com.example.examplemod.mobAi.Goal.IdleCrossbowGoal;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
            } else if (taskName.contains("setwalktargetfromattacktarget")) {
                // видаляємо ванільну утримачку walk target
            } else if (taskName.contains("stopattacking")) {
                // ВИДАЛЯЄМО: ця ванільна задача скидає ATTACK_TARGET при втраті прямої видимості.
                // Оскільки PursuitBrainBridgeGoal сама відповідає за очищення ATTACK_TARGET при
                // переході в FORGOTTEN, ванільне скидання викликало нескінченний цикл
                // (Brain видаляє -> Bridge повертає -> смикання анімації та звуку).
            } else {
                tasks.add(task);
            }
        }

        tasks.add(new PiglinSwapWeaponBehavior());
        tasks.add(new BetterPiglinGoalAi());

        return ImmutableList.copyOf(tasks);
    }
}

/**
 * Package-private, у ТОМУ Ж файлі що й {@link PiglinMixin} суто для меншої кількості файлів —
 * але це ОКРЕМИЙ {@code @Mixin} з ОКРЕМОЮ ціллю: {@link PiglinMixin} вище ціллю має
 * {@code PiglinAi.class} (статичний хелпер, модифікує FIGHT-задачі Brain-а), а цей —
 * САМУ сутність {@code Piglin.class} (додає goalSelector-половину системи переслідування +
 * фонове тримання арбалета базовою/зарядженою зброєю поза боєм).
 * Full-qualified ім'я лишається просто {@code ....mobAi.Mixin.PiglinGoalMixin} (top-level
 * клас, НЕ вкладений) — тобто запис у mixins.json не міняється попри спільний файл.
 * <p>
 * {@code registerGoals()} тут НЕ підійшов (TAIL-інжект падав з "could not find method" —
 * Piglin у цій версії його локально не перевизначає), тому ціль — {@code <init>}. Звідси й гард
 * нижче: якщо в {@code Piglin} більше одного конструктора і {@code <init>} без дескриптора
 * зачепить кілька — без гарда це б давало ДВІ паралельні копії всіх трьох Goal-ів на одному мобі.
 * <p>
 * МАЛЯТА (isBaby()) — виняток: жоден з трьох Goal-ів навіть не додається. Малі піглени не
 * повинні воювати взагалі, а {@link IdleCrossbowGoal} спрацьовує на будь-якому {@code getTarget()
 * == null} — тобто майже завжди для малят (вони практично ніколи не входять у бій) — і озброював
 * би їх арбалетом без жодної бойової причини.
 */
@Mixin(Piglin.class)
class PiglinGoalMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addPursuitSystem(CallbackInfo ci) {
        Piglin piglin = (Piglin) (Object) this;

        if (piglin.isBaby()) {
            return; // малята не воюють - їм узагалі не треба жодної частини цієї системи
        }

        boolean alreadyAdded = piglin.goalSelector.getAvailableGoals().stream()
                .anyMatch(g -> g.getGoal() instanceof PursuitEnemyBehavior);
        if (alreadyAdded) {
            BetterEnemysAI.LOGGER.warn("PiglinGoalMixin: addPursuitSystem fired more than once for {} "
                    + "-- <init> matched more than one constructor, skipping duplicate", piglin);
            return;
        }
        piglin.goalSelector.addGoal(0, new PursuitEnemyBehavior(piglin, true));
        piglin.goalSelector.addGoal(1, new PursuitBrainBridgeGoal(piglin));
        piglin.goalSelector.addGoal(2, new IdleCrossbowGoal(piglin, Items.GOLDEN_SWORD));
    }
}