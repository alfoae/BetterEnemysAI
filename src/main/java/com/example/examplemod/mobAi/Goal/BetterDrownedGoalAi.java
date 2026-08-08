package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.ProjectileTrajectory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class BetterDrownedGoalAi extends Goal {
    private final Drowned mob;
    private final double speedModifier;
    private final int attackIntervalMin;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public BetterDrownedGoalAi(Drowned mob, double speedModifier, int attackIntervalMin) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackIntervalMin = attackIntervalMin;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() != null && this.isHoldingTrident();
    }

    private boolean isHoldingTrident() {
        return this.mob.isHolding(is -> is.getItem() instanceof TridentItem);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() || !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.attackTime = -1;
        this.mob.stopUsingItem();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        double distanceSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);
        boolean isSeeing = this.seeTime > 0;
        double followRange = this.mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double maxShootDistance = followRange * 0.75;

        if (canSee != isSeeing) {
            this.seeTime = 0;
        }
        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        // Немає прямої видимості — йдемо по пам'яті (chasePos), а не напряму до живої (можливо
        // дуже далекої) позиції гравця. Інакше vanilla-навігатор не будує такий довгий шлях, і
        // утопленик просто стоїть на місці, дійшовши до застиглої точки замість шукати далі.
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (!canSee && chasePos != null) {
            if (this.mob.isUsingItem()) {
                this.mob.stopUsingItem(); // не тримаємо тризуб натягнутим, поки біжимо всліпу
            }
            this.mob.setSwimming(this.mob.isInWater());
            double sprintSpeed = PursuitEnemyBehavior.getSprintSpeedModifier(this.mob);
            this.mob.getNavigation().moveTo(chasePos.x, chasePos.y, chasePos.z, sprintSpeed);
            this.mob.getLookControl().setLookAt(
                    chasePos.x, chasePos.y + this.mob.getBbHeight() * 0.5, chasePos.z, 30.0F, 30.0F);
            this.mob.setSprinting(true);
            return;
        }
        this.mob.setSprinting(false);

        // ========================================================
        // РОЗДІЛЕННЯ ЛОГІКИ РУХУ (ВОДА / СУША)
        // ========================================================
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

            if (distanceSq <= 225.0D && this.seeTime >= 20) {
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

        // ========================================================
        // ЛОГІКА СТРІЛЬБИ (КИДАННЯ ТРИЗУБЦЯ)
        // ========================================================
        if (this.mob.isUsingItem()) {
            if (!canSee && this.seeTime < -60) {
                this.mob.stopUsingItem();
            } else if (canSee || distanceSq <= maxShootDistance * maxShootDistance) {
                int useTime = this.mob.getTicksUsingItem();
                if (useTime >= 20) { // 1 секунда замаху

                    if (!isPathClear(this.mob, target)) {
                        return;
                    }

                    // 1. Отримуємо чистий вектор швидкості з трекера
                    Vec3 realVel = com.example.examplemod.utils.PlayerVelocityTracker.getRealVelocity(target);

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
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof TridentItem));
        }
    }

    private void shootCustomTrident(AdvancedAimMath.AimResult aim) {
        ItemStack tridentStack = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof TridentItem));
        ThrownTrident trident = new ThrownTrident(this.mob.level(), this.mob, tridentStack.copy());

        trident.shoot(aim.dX(), aim.dY(), aim.dZ(), aim.velocity(), aim.inaccuracy());
        trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        this.mob.playSound(net.minecraft.sounds.SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
        this.mob.level().addFreshEntity(trident);
    }

    private boolean isPathClear(Drowned shooter, LivingEntity target) {
        Vec3 start = shooter.getEyePosition();
        Vec3 end = target.getEyePosition();

        net.minecraft.world.phys.AABB area = shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        for (net.minecraft.world.entity.Entity entity : shooter.level().getEntities(shooter, area)) {
            if (entity instanceof LivingEntity && entity != target && entity != shooter) {
                if (entity.getBoundingBox().clip(start, end).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }
}