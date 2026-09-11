package com.example.examplemod.Enemy.EnemyAttack;

import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.AdvancedAimMath;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.PlayerVelocityTracker;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.ProjectileTrajectory;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Атака дровнеда киданням тризубця: натяг ({@code startUsingItem}), очікування чистої лінії
 * вогню (окрема швидка перевірка {@link #isPathClear} ДО рахування прицілу, і повна
 * {@link ProjectileTrajectory#isPathClear} ПІСЛЯ — з урахуванням реального випередження), і
 * сам кидок — окремо для плавця (повна 3D-математика) і наземної цілі (без Y, щоб стрибки не
 * ламали приціл). Рух — {@link com.example.examplemod.Enemy.EnemyMovement.DrownedAmphibiousMovement}.
 * <p>
 * Винесено з {@link com.example.examplemod.mobAi.Goal.BetterDrownedGoalAi}, яка рахує
 * {@code canSee}/{@code seeTime}/{@code distanceSq} і передає їх сюди готовими.
 */
public class DrownedTridentAttack {

    private final Drowned mob;
    private final int attackIntervalMin;
    private int attackTime = -1;

    public DrownedTridentAttack(Drowned mob, int attackIntervalMin) {
        this.mob = mob;
        this.attackIntervalMin = attackIntervalMin;
    }

    /**
     * Викликати з {@code Goal.stop()} — той самий момент, коли оригінал скидав attackTime.
     */
    public void reset() {
        this.attackTime = -1;
    }

    public void tick(LivingEntity target, double distanceSq, boolean canSee, int seeTime) {
        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double maxShootDistance = followRange * 0.75;

        if (this.mob.isUsingItem()) {
            if (!canSee && seeTime < -60) {
                this.mob.stopUsingItem();
            } else if (canSee || distanceSq <= maxShootDistance * maxShootDistance) {
                int useTime = this.mob.getTicksUsingItem();
                if (useTime >= 20) { // 1 секунда замаху

                    if (!isPathClear(this.mob, target)) {
                        return;
                    }

                    // 1. Отримуємо чистий вектор швидкості з трекера
                    Vec3 realVel = PlayerVelocityTracker.getRealVelocity(target);

                    AdvancedAimMath.AimResult aim;

                    // 2. РОЗДІЛЕННЯ РЕЖИМІВ
                    if (target.isSwimming() || target.isInWater()) {
                        // ЯКЩО ПЛИВЕШ: викликаємо новий метод, передаємо повну швидкість з Y
                        aim = AdvancedAimMath.calculateSwimmingAim(this.mob, target, 2.5f, realVel.scale(1.8));
                    } else {
                        // НА СУШІ: викликаємо твій старий метод, обнуляючи Y (захист від стрибків для скелетів/інших)
                        Vec3 horizontalVel = new Vec3(realVel.x, 0.0, realVel.z);
                        aim = AdvancedAimMath.calculateAim(this.mob, target, 2.5f, horizontalVel.scale(1.8));
                    }

                    if (aim != null) {
                        if (!ProjectileTrajectory.isPathClear(this.mob, aim, 0.30)) // перевірка траекторії
                        {
                            return;
                        }
                        shootCustomTrident(aim);
                    }

                    this.mob.stopUsingItem();
                    this.attackTime = this.attackIntervalMin;
                }
            }
        } else if (--this.attackTime <= 0 && seeTime >= -60) {
            this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof TridentItem));
        }
    }

    private void shootCustomTrident(AdvancedAimMath.AimResult aim) {
        ItemStack tridentStack = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof TridentItem));
        ThrownTrident trident = new ThrownTrident(this.mob.level(), this.mob, tridentStack.copy());

        trident.shoot(aim.dX(), aim.dY(), aim.dZ(), aim.velocity(), aim.inaccuracy());
        trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        this.mob.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
        this.mob.level().addFreshEntity(trident);
    }

    private boolean isPathClear(Drowned shooter, LivingEntity target) {
        Vec3 start = shooter.getEyePosition();
        Vec3 end = target.getEyePosition();

        AABB area = shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        for (Entity entity : shooter.level().getEntities(shooter, area)) {
            if (entity instanceof LivingEntity && entity != target && entity != shooter) {
                if (entity.getBoundingBox().clip(start, end).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }
}
