package com.example.examplemod.Enemy.EnemyMovement;

import com.example.examplemod.Enemy.EnemyBehavior.EnemyPursuit_N_Search.PursuitBehavior.PursuitEnemyBehavior;
import com.example.examplemod.Enemy.EnemyMovement.Run_N_Jump.Run_N_JumpUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.phys.Vec3;

/**
 * Рух скелета (і будь-якого {@link AbstractSkeleton}) під час бою: ванільний стрейф-патерн
 * навколо цілі, коли дистанція комфортна й ціль давно на очах, або біг до останньої відомої
 * точки ({@code chasePos}) з бустом швидкості, коли видимості нема. Атака —
 * {@link com.example.examplemod.Enemy.EnemyAttack.SkeletonBowAttack}.
 * <p>
 * {@code canSee}/{@code seeTime} рахує і передає сюди {@link com.example.examplemod.mobAi.Goal.BetterSkeletonGoalAi}
 * (той самий тік потрібен і атаці — рахувати їх тут ще раз означало б дублювати
 * distanceSq/hasLineOfSight).
 * <p>
 * Винесено з {@code BetterSkeletonGoalAi}.
 */
public class SkeletonStrafeMovement {

    private final AbstractSkeleton mob;
    private final double speedModifier;

    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public SkeletonStrafeMovement(AbstractSkeleton mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
    }

    public void tick(LivingEntity target, double distanceSq, boolean canSee, int seeTime) {
        // Чи моб зараз "переслідує крізь стіни" по глобальній пам'яті про ціль, і куди саме:
        // жива позиція гравця (в межах повного FOLLOW_RANGE) або застигла остання відома точка
        // (гравець вийшов за радіус — туди вже НЕ оновлюємо позицію, просто доходимо).
        boolean memoryChasing = PursuitEnemyBehavior.isMemoryChasing(this.mob);
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);

        // Швидкість бігу береться з PursuitEnemyBehavior (задається при реєстрації в BetterEnemysBehavior).
        double sprintSpeed = Run_N_JumpUtils.getRunSpeedModifier(this.mob);
        double currentSpeed = (memoryChasing && !canSee) ? sprintSpeed : this.speedModifier;

        // Логіка переміщення (ванільний стрейф навколо цілі)
        if (distanceSq <= 225.0D && seeTime >= 20) {
            this.mob.getNavigation().stop();
            this.mob.setSprinting(false);
            ++this.strafingTime;
        } else if (chasePos != null) {
            this.mob.getNavigation().moveTo(chasePos.x, chasePos.y, chasePos.z, currentSpeed);
            // setSprinting дає реальний біг (як тікання від вовка), а не просто прискорений хід.
            this.mob.setSprinting(!canSee);
            this.strafingTime = -1;
        } else {
            this.mob.getNavigation().moveTo(target, currentSpeed);
            this.mob.setSprinting(false);
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
        } else if (chasePos != null) {
            // Під час бігу до точки навігація сама повертає моба — не заважаємо їй щотіку
            // викликом setLookAt (це і давало дергання голови вгору-вниз). Лише якщо є реальна
            // ціль — дивимось на рівень її очей (не ніг), щоб голова не задиралась/опускалась.
            if (canSee) {
                this.mob.getLookControl().setLookAt(
                        target.getX(), target.getEyeY(), target.getZ(), 30.0F, 30.0F);
            }
            // canSee == false: голова йде туди куди веде навігація — природньо
        } else {
            this.mob.getLookControl().setLookAt(
                    target.getX(), target.getEyeY(), target.getZ(), 30.0F, 30.0F);
        }
    }
}
