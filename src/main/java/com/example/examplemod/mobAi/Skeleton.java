package com.example.examplemod.mobAi;

import com.example.examplemod.util.AdvancedAimMath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class Skeleton extends Goal {
    private final AbstractSkeleton mob;
    private final double speedModifier;
    private final int attackIntervalMin;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;

    public Skeleton(AbstractSkeleton mob, double speedModifier, int attackIntervalMin) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.attackIntervalMin = attackIntervalMin;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() != null && this.isHoldingBow();
    }

    private boolean isHoldingBow() {
        return this.mob.isHolding(is -> is.getItem() instanceof BowItem);
    }

    @Override
    public boolean canContinueToUse() {
        return (this.canUse() || !this.mob.getNavigation().isDone());
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
        if (target == null) return;

        double distanceSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);
        boolean isSeeing = this.seeTime > 0;

        if (canSee != isSeeing) {
            this.seeTime = 0;
        }
        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        // Логіка переміщення (ванільний стрейф навколо цілі)
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

        // ========================================================
        // ЛОГІКА СТРІЛЬБИ (Туди ми вставляємо нашу математику)
        // ========================================================
        if (this.mob.isUsingItem()) {
            if (!canSee && this.seeTime < -60) {
                this.mob.stopUsingItem();
            } else if (canSee) {
                int useTime = this.mob.getTicksUsingItem();
                // 20 тіків = 1 секунда (повний натяг лука)
                if (useTime >= 20) {

                    // 1. ПЕРЕВІРКА НА ДРУЖНІЙ ВОГОНЬ ТА БЛОКИ
                    if (!isPathClear(this.mob, target)) {
                        // Якщо шлях перекрито, скелет просто тримає лук натягнутим і чекає
                        return;
                    }

                    // 2. ВИКЛИК НАШОЇ МАТЕМАТИКИ
                    AdvancedAimMath.AimResult aim = AdvancedAimMath.calculateAim(this.mob, target, 3.0f);

                    if (aim != null) {
                        shootCustomArrow(aim);
                    }

                    // Скидаємо таймери після пострілу
                    this.mob.stopUsingItem();
                    this.attackTime = this.attackIntervalMin;
                }
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof BowItem));
        }
    }

    // Метод для створення та запуску стріли з фіксом урону
    private void shootCustomArrow(AdvancedAimMath.AimResult aim) {
        ItemStack bowStack = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof net.minecraft.world.item.BowItem));
        ItemStack ammoStack = this.mob.getProjectile(bowStack);

        AbstractArrow arrow = ProjectileUtil.getMobArrow(this.mob, ammoStack, 1.0f, bowStack);

        double damageMultiplier = 3.0 / aim.velocity();
        arrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);

        // ЗВЕРНИ УВАГУ: Я прибрав distanceHoriz * 0.2D. Тепер ми передаємо просто aim.dY()
        arrow.shoot(aim.dX(), aim.dY(), aim.dZ(), aim.velocity(), aim.inaccuracy());

        this.mob.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
        this.mob.level().addFreshEntity(arrow);
    }

    // Метод перевірки: чи є хтось або щось на лінії вогню
    private boolean isPathClear(AbstractSkeleton shooter, LivingEntity target) {
        Vec3 start = shooter.getEyePosition();
        Vec3 end = target.getEyePosition();

        // Перевіряємо ТІЛЬКИ чи немає на лінії вогню інших мобів (щоб не стріляти у спину зомбі)
        net.minecraft.world.phys.AABB area = shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
        for (net.minecraft.world.entity.Entity entity : shooter.level().getEntities(shooter, area)) {
            if (entity instanceof LivingEntity && entity != target && entity != shooter) {
                if (entity.getBoundingBox().clip(start, end).isPresent()) {
                    return false; // Попереду союзник, чекаємо!
                }
            }
        }
        return true; // Блоки більше не заважають стріляти на великі дистанції
    }
}