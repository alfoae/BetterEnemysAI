package com.example.examplemod.Enemy.EnemyMovement;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;

/**
 * Рух дровнеда під час бою: у воді — підпливання/утримання дистанції без наземного стрейфу
 * (стрейф ламає плавання), на суші — той самий ванільний стрейф-патерн, що й
 * {@link SkeletonStrafeMovement}. Атака (кидок тризубця) —
 * {@link com.example.examplemod.Enemy.EnemyAttack.DrownedTridentAttack}.
 * <p>
 * {@code distanceSq}/{@code seeTime} рахує і передає сюди {@link com.example.examplemod.mobAi.Goal.BetterDrownedGoalAi}
 * — той самий Goal також окремо обробляє випадок "видимості нема, біжимо по пам'яті" ДО
 * виклику цього методу (там же й {@code return}, тобто рух і атака цей тік не викликаються
 * взагалі), тож тут можна вважати, що ми або бачимо ціль, або просто втратили її щойно.
 * <p>
 * Винесено з {@code BetterDrownedGoalAi}.
 */
public class DrownedAmphibiousMovement {

    private final Drowned mob;
    private final double speedModifier;

    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public DrownedAmphibiousMovement(Drowned mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
    }

    public void tick(LivingEntity target, double distanceSq, int seeTime) {
        if (this.mob.isInWater()) {
            // ФІКС 1: У воді утопленик пливе ТІЛЬКИ коли НЕ замахується тризубцем!
            // Це дозволяє йому нормально зарядити і кинути снаряд.
            this.mob.setSwimming(!this.mob.isUsingItem());

            // ФІКС 2: Вимикаємо наземний стрейф (.strafe) під водою, бо він ламає рух утопленика.
            // Натомість використовуємо надійне водне наближення або утримання дистанції.
            if (distanceSq > 144.0D) { // Якщо гравець далі ніж за 12 блоків — пливемо до нього
                this.mob.getNavigation().moveTo(target, this.speedModifier);
            } else if (distanceSq < 36.0D) { // Якщо занадто близько (менше 6 блоків) — зупиняємось/відпливаємо
                this.mob.getNavigation().stop();
            } else {
                this.mob.getNavigation().stop(); // Ідеальна дистанція для стрільби
            }
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        } else {
            // НА СУШІ: повертаємо класичний скелетний стрейф навколо цілі
            this.mob.setSwimming(false);

            if (distanceSq <= 225.0D && seeTime >= 20) {
                this.mob.getNavigation().stop();
                ++this.strafingTime;
            } else {
                this.mob.getNavigation().moveTo(target, this.speedModifier);
                this.strafingTime = -1;
            }

            if (this.strafingTime >= 20) {
                if (this.mob.getRandom().nextFloat() < 0.3F) {
                    this.strafingClockwise = !this.strafingClockwise;
                }
                if (this.mob.getRandom().nextFloat() < 0.3F) {
                    this.strafingBackwards = !this.strafingBackwards;
                }
                this.strafingTime = 0;
            }

            if (this.strafingTime > -1) {
                if (distanceSq > 225.0D) {

                    this.strafingBackwards = false;
                } else if (distanceSq < 49.0D) {
                    this.strafingBackwards = true;
                }
                this.mob.getMoveControl().strafe(this.strafingBackwards ? -0.5F : 0.5F, this.strafingClockwise ? 0.5F : -0.5F);
                this.mob.lookAt(target, 30.0F, 30.0F);
            } else {
                this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
        }
    }
}
