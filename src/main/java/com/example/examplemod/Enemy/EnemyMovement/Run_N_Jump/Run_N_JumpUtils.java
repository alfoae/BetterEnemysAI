package com.example.examplemod.Enemy.EnemyMovement.Run_N_Jump;

import com.example.examplemod.Enemy.ChangeEnemiesAttributes;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyPursuit_N_Search.PursuitBehavior.PursuitEnemyBehavior;
import net.minecraft.world.entity.Mob;

/**
 * Спільна логіка "бігу", винесена з {@link PursuitEnemyBehavior}.
 * <p>
 * Дефолтне правило просте: моб БІЖИТЬ, поки заагрений (стан CHASING, GOING_TO_LAST_SEEN або
 * SEARCHING — тобто {@link PursuitEnemyBehavior#isMemoryChasing(Mob)} повертає true), і ХОДИТЬ,
 * щойно забув ціль (FORGOTTEN) чи взагалі не перебуває в системі переслідування. Це не залежить
 * від того, яка саме поведінка зараз керує рухом (мелі-атака, пошук, підйом вежею, прокладання
 * шляху, прокопування стіни) — усі вони можуть просто викликати {@link #applyDefaultRun(Mob)}.
 * <p>
 * ВАЖЛИВО: якщо в конкретної поведінки є ВЛАСНА причина не бігти прямо зараз — скелет натягує
 * тетиву, дровнед замахується тризубом, піглін заряджає арбалет, лучник близько стрейфить —
 * ця поведінка НЕ повинна викликати {@link #applyDefaultRun(Mob)} узагалі, а сама вирішувати
 * {@code setSprinting(...)} по своїй логіці. Дефолт із цього класу свідомо НЕ намагається
 * перебити такі рішення: {@link com.example.examplemod.Enemy.EnemyMovement.SkeletonStrafeMovement},
 * {@code BetterDrownedGoalAi} та {@code PursuitBrainBridgeGoal} саме тому цей клас не використовують.
 */
public final class Run_N_JumpUtils {

    private Run_N_JumpUtils() {
    }

    /**
     * Дефолтне правило бігу: sprint = моб зараз заагрений (в будь-якому з активних станів
     * переслідування), незалежно від того, яка саме поведінка зараз керує рухом.
     * <p>
     * Викликати з {@code tick()} тих поведінок, у яких немає власної, розумнішої за цей дефолт
     * причини вирішувати інакше (наприклад {@code TowerClimbGoal}, {@code BuildPathGoal},
     * {@code DigThroughWallsGoal}, {@code PursuitEnemyMeleeBehavior}).
     */
    public static void applyDefaultRun(Mob mob) {
        mob.setSprinting(PursuitEnemyBehavior.isMemoryChasing(mob));
    }

    /**
     * Множник швидкості бігу (у скільки разів швидше за ходьбу рухається моб, поки заагрений).
     * Єдине джерело правди для цих чисел — {@link ChangeEnemiesAttributes}, за типом моба.
     */
    public static double getRunSpeedModifier(Mob mob) {
        return ChangeEnemiesAttributes.getRunSpeedMultiplier(mob.getType());
    }
}
