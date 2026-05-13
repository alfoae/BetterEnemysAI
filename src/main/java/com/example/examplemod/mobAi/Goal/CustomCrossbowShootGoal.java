package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.PlayerVelocityTracker;
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
            // КРОК 1: Починаємо зарядку
            mob.startUsingItem(mob.getUsedItemHand());
            // Вмикаємо агресію відразу, щоб міксін знав, що ми в бойовому стані
            mob.setAggressive(true);

            if (mob instanceof Pillager pillager) {
                pillager.setChargingCrossbow(true);
            }
            this.attackTimer = 15;
            this.state = 1;
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
            // Поки таймер іде, моб просто стоїть у позі CROSSBOW_HOLD завдяки міксіну і агресії
            if (this.attackTimer <= 0) {
                shootWithPrediction(target);

                // ПІСЛЯ ПОСТРІЛУ: розряджаємо і знімаємо агресію
                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);

                if (mob instanceof Pillager pillager) {
                    pillager.setChargingCrossbow(false);
                }

                // Тільки тепер опускаємо руки
                mob.setAggressive(false);

                this.state = 0;
                this.attackTimer = 5;
            }
        }
    }

    private void shootWithPrediction(LivingEntity target) {
        ItemStack crossbow = mob.getMainHandItem();
        float speed = 4.0F;

        Projectile projectile = ProjectileUtil.getMobArrow(mob, new ItemStack(Items.ARROW), speed, crossbow);
        if (projectile instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
            arrow.setBaseDamage(1.3);
        }

        Vec3 targetVel = PlayerVelocityTracker.getRealVelocity(target).scale(2);
        var aim = AdvancedAimMath.calculateAim(mob, target, speed, targetVel);

        if (aim != null) {
            double pX = mob.getX() + aim.dX();
            double pZ = mob.getZ() + aim.dZ();
            double vanillaAddedHeight = target.getBbHeight() * 0.3333333333333333D;
            double pY = mob.getEyeY() + aim.dY() - vanillaAddedHeight;

            projectile.shoot(pX - mob.getX(), pY - mob.getEyeY(), pZ - mob.getZ(), speed, 1.0F);
        }

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