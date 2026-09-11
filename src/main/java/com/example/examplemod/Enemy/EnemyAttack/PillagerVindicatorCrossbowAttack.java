package com.example.examplemod.Enemy.EnemyAttack;

import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.AdvancedAimMath;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.PlayerVelocityTracker;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.ProjectileTrajectory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

/**
 * Crossbow-атака піладжера/віндикатора: натяг -> утримання заряду -> постріл лише коли лінія
 * вогню вільна від союзників. Спільна для {@code Pillager} і {@code Vindicator} (обидва
 * приходять сюди як {@link Monster}) — на відміну від пігліна обидва все ще на старому
 * {@link net.minecraft.world.entity.ai.goal.Goal} API, тож той самий задум тут проще
 * (менше станів, без {@code copy()}-синхронізації екіпіровки). Brain-варіант з тими ж ідеями —
 * {@link PiglinCrossbowAttack}.
 * <p>
 * Винесено з {@link com.example.examplemod.mobAi.Goal.BetterPillagerVindicatorGoalAi}.
 */
public class PillagerVindicatorCrossbowAttack {

    private final Monster mob;
    private int attackTimer;
    private int state = 0; // 0 - спокій, 1 - натягування, 2 - заряджений

    public PillagerVindicatorCrossbowAttack(Monster mob) {
        this.mob = mob;
    }

    /**
     * Викликати з {@code Goal.start()}.
     */
    public void reset() {
        this.state = 0;
    }

    /**
     * Викликати з {@code Goal.stop()}.
     */
    public void onStop() {
        mob.stopUsingItem();
        if (mob instanceof Pillager pillager) {
            pillager.setChargingCrossbow(false);
        }
        this.state = 0;
    }

    public void tick(LivingEntity target) {
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        ItemStack crossbow = mob.getMainHandItem();

        if (state == 0) {
            // ПЕРЕВІРКА: чи заряджений арбалет уже?
            boolean isCharged = !crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();

            if (isCharged) {
                // Якщо вже заряджений — пропускаємо натягування, йдемо відразу до цілювання
                this.state = 2;
                this.attackTimer = 5; // Коротка пауза перед пострілом
                mob.setAggressive(true);
            } else {
                // Якщо пустий — починаємо заряджати
                mob.startUsingItem(mob.getUsedItemHand());
                mob.setAggressive(true);
                this.attackTimer = 15;
                this.state = 1;
            }
        } else if (state == 1) {
            this.attackTimer--;
            if (this.attackTimer <= 0) {
                // Візуально заряджаємо арбалет
                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));

                // ПРИПИНЯЄМО тягнути тятиву, АЛЕ залишаємо setAggressive(true)
                mob.stopUsingItem();
                this.state = 2;
                this.attackTimer = 5; // Час, поки він просто стоїть з націленим арбалетом
            }
        } else if (state == 2) {
            double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE); // або mob для CustomCrossbowShootGoal
            if (target.distanceToSqr(mob) > Math.pow(followRange * 0.75, 2)) {
                this.attackTimer = 1;
                return; // занадто далеко — не стріляємо, чекаємо
            }
            this.attackTimer--;
            if (this.attackTimer <= 0) {

                Vec3 targetVel = PlayerVelocityTracker.getRealVelocity(target).scale(2);
                var aim = AdvancedAimMath.calculateAim(mob, target, 4.0F, targetVel);

                if (aim == null) {
                    // Не вдалось розрахувати приціл — тримаємо заряджене, пробуємо ще раз наступний тік.
                    this.attackTimer = 1;
                    return;
                }

                if (!ProjectileTrajectory.isPathClear(mob, aim, 0.30)) {
                    // Союзник на лінії вогню — арбалет ЗАЛИШАЄТЬСЯ заряджений (state не змінюємо,
                    // CHARGED_PROJECTILES не чистимо), просто чекаємо і перевіряємо знову наступний тік.
                    this.attackTimer = 1;
                    return;
                }

                shootWithPrediction(target, aim);

                // Очищуємо арбалет
                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);

                if (mob instanceof Pillager pillager) {
                    pillager.setChargingCrossbow(false);
                }

                // ВАЖЛИВО: НЕ прибираємо setAggressive(true), щоб руки не схрещувалися
                this.state = 0;
                this.attackTimer = 10; // Кулдаун до наступної зарядки
            }
        }
    }

    private void shootWithPrediction(LivingEntity target, AdvancedAimMath.AimResult aim) {
        ItemStack crossbow = mob.getMainHandItem();
        float speed = 4.0F;

        Projectile projectile = ProjectileUtil.getMobArrow(mob, new ItemStack(Items.ARROW), speed, crossbow);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(1.3);
        }

        double pX = mob.getX() + aim.dX();
        double pZ = mob.getZ() + aim.dZ();
        double vanillaAddedHeight = target.getBbHeight() * 0.3333333333333333D;
        double pY = mob.getEyeY() + aim.dY() - vanillaAddedHeight;

        projectile.shoot(pX - mob.getX(), pY - mob.getEyeY(), pZ - mob.getZ(), speed, 1.0F);

        mob.level().addFreshEntity(projectile);
    }
}
