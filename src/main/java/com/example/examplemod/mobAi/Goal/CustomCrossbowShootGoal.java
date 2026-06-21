package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.PlayerVelocityTracker;
import com.example.examplemod.utils.ProjectileTrajectoryUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CustomCrossbowShootGoal extends Goal {
    private final Monster mob;
    private int attackTimer;
    private int state = 0; // 0 - спокій, 1 - натягування, 2 - заряджений

    public CustomCrossbowShootGoal(Monster mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK, Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null && mob.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    public void start() {
        this.state = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        ItemStack crossbow = mob.getMainHandItem();

        if (state == 0) {
            // ПЕРЕВІРКА: чи заряджений арбалет уже?
            boolean isCharged = !crossbow.getOrDefault(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                    net.minecraft.world.item.component.ChargedProjectiles.EMPTY).isEmpty();

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
            this.attackTimer--;
            if (this.attackTimer <= 0) {

                Vec3 targetVel = PlayerVelocityTracker.getRealVelocity(target).scale(2);
                var aim = AdvancedAimMath.calculateAim(mob, target, 4.0F, targetVel);

                if (aim == null) {
                    // Не вдалось розрахувати приціл — тримаємо заряджене, пробуємо ще раз наступний тік.
                    this.attackTimer = 1;
                    return;
                }

                if (!ProjectileTrajectoryUtils.isPathClear(mob, aim, 0.25)) {
                    // Союзник на лінії вогню — арбалет ЗАЛИШАЄТЬСЯ заряджений (state не змінюємо,
                    // CHARGED_PROJECTILES не чистимо), просто чекаємо і перевіряємо знову наступний тік.
                    this.attackTimer = 1;
                    return;
                }

                shootWithPrediction(target, aim);

                // Очищуємо арбалет
                crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                        net.minecraft.world.item.component.ChargedProjectiles.EMPTY);

                if (mob instanceof net.minecraft.world.entity.monster.Pillager pillager) {
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
        if (projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
            arrow.setBaseDamage(1.3);
        }

        double pX = mob.getX() + aim.dX();
        double pZ = mob.getZ() + aim.dZ();
        double vanillaAddedHeight = target.getBbHeight() * 0.3333333333333333D;
        double pY = mob.getEyeY() + aim.dY() - vanillaAddedHeight;

        projectile.shoot(pX - mob.getX(), pY - mob.getEyeY(), pZ - mob.getZ(), speed, 1.0F);

        mob.level().addFreshEntity(projectile);
    }

    @Override
    public void stop() {
        mob.stopUsingItem();
        if (mob instanceof Pillager pillager) {
            pillager.setChargingCrossbow(false);
        }
        this.state = 0;
    }
}