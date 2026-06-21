package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.ProjectileTrajectoryUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class BetterSkeletonGoalAi extends Goal {
    private final AbstractSkeleton mob;
    private final double speedModifier;
    private final int attackIntervalMin;
    private int attackTime = -1;
    private int seeTime;
    private boolean strafingClockwise;
    private boolean strafingBackwards;
    private int strafingTime = -1;



    public BetterSkeletonGoalAi(AbstractSkeleton mob, double speedModifier, int attackIntervalMin) {
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
        if (target == null) {
            return;
        }

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
        // ЛОГІКА СТРІЛЬБИ
        // ========================================================
        if (this.mob.isUsingItem()) {
            if (!canSee && this.seeTime < -60) {
                this.mob.stopUsingItem();
            } else if (canSee) {
                int useTime = this.mob.getTicksUsingItem();
                // 20 тіків = 1 секунда (повний натяг лука)
                if (useTime >= 20) {

                    Vec3 realVel = com.example.examplemod.utils.PlayerVelocityTracker.getRealVelocity(target);

                    // Уся логіка "точний приціл -> перевірка -> похибка -> перевірка -> ретраї"
                    // інкапсульована тут. Якщо навіть ІДЕАЛЬНИЙ (без похибки) вистріл заблокований
                    // союзником — повертає null, і похибка навіть не рахується.
                    AdvancedAimMath.AimResult aim = ProjectileTrajectoryUtils.resolveAimWithMissCheck(
                            this.mob, target, 3.0f, realVel.scale(1.8), 0.25
                    );

                    if (aim != null) {
                        shootCustomArrow(aim);
                        // Скидаємо таймери ТІЛЬКИ після фактичного пострілу
                        this.mob.stopUsingItem();
                        this.attackTime = this.attackIntervalMin;
                    }
                    // якщо aim == null — арбалет/лук ЛИШАЄТЬСЯ натягнутим (заряд тримається),
                    // нічого не скидаємо, наступний тік перевірка повториться знову
                }
            }
        } else if (--this.attackTime <= 0 && this.seeTime >= -60) {
            this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof BowItem));
        }
    }

    // Метод для створення та запуску стріли з фіксом урону
    private void shootCustomArrow(AdvancedAimMath.AimResult aim) {
        // 1. Отримуємо предмети в руках
        ItemStack bowStack = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof net.minecraft.world.item.BowItem));
        ItemStack ammoStack = this.mob.getProjectile(bowStack);

        // 2. Використовуємо ПУБЛІЧНУ утиліту (це виправляє помилку "protected access")
        // Вона сама викликає getArrow всередині Minecraft, де це дозволено
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this.mob, ammoStack, 1.0f, bowStack);

        // 3. РУЧНЕ НАКЛАДАННЯ ЕФЕКТІВ (якщо утиліта їх пропустила)
        // Перевіряємо, чи це Зимогор (Stray)
        if (this.mob instanceof net.minecraft.world.entity.monster.Stray && arrow instanceof net.minecraft.world.entity.projectile.Arrow tippedArrow) {
            tippedArrow.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 600)); // Уповільнення на 30 сек
        }

        // Перевіряємо, чи це Болотяник (Bogged)
        // Якщо твоя версія гри підтримує Bogged, ця перевірка спрацює:
        if (this.mob.getType().toString().contains("bogged") && arrow instanceof net.minecraft.world.entity.projectile.Arrow tippedArrow) {
            tippedArrow.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100)); // Отрута на 5 сек
        }

        // 4. Твоя математика шкоди та запуск
        double damageMultiplier = 3.0 / aim.velocity();
        arrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);

        arrow.shoot(aim.dX(), aim.dY(), aim.dZ(), aim.velocity(), aim.inaccuracy());

        this.mob.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
        this.mob.level().addFreshEntity(arrow);
    }
}