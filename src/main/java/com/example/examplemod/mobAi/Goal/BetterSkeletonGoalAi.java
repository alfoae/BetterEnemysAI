package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.EnemyFactionRegistry;
import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.ProjectileTrajectory;
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
        mob.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true, 1.0));
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
        this.mob.setSprinting(false);
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
        double followRange = this.mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        double maxShootDistanceSq = (followRange * 0.75) * (followRange * 0.75);
        // canSee враховує і пряму видимість І дистанцію стрільби — Sensing.hasLineOfSight()
        // не перевіряє FOLLOW_RANGE, тому без цієї перевірки скелет міг стріляти за межею радіуса.
        boolean canSee = distanceSq <= maxShootDistanceSq && this.mob.getSensing().hasLineOfSight(target);
        boolean isSeeing = this.seeTime > 0;

        if (canSee != isSeeing) {
            this.seeTime = 0;
        }
        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        // Чи моб зараз "переслідує крізь стіни" по глобальній пам'яті про ціль, і куди саме:
        // жива позиція гравця (в межах повного FOLLOW_RANGE) або застигла остання відома точка
        // (гравець вийшов за радіус — туди вже НЕ оновлюємо позицію, просто доходимо).
        boolean memoryChasing = PursuitEnemyBehavior.isMemoryChasing(this.mob);
        boolean drawBow = PursuitEnemyBehavior.shouldDrawBowstring(this.mob);
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);

        // Швидкість бігу береться з PursuitEnemyBehavior (задається при реєстрації в BetterEnemysBehavior).
        double sprintSpeed = PursuitEnemyBehavior.getSprintSpeedModifier(this.mob);
        double currentSpeed = (memoryChasing && !canSee) ? sprintSpeed : this.speedModifier;

        // Логіка переміщення (ванільний стрейф навколо цілі)
        if (distanceSq <= 225.0D && this.seeTime >= 20) {
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

        // ========================================================
        // ЛОГІКА СТРІЛЬБИ
        // ========================================================
        if (this.mob.isUsingItem()) {
            if (!canSee && !drawBow) {
                // Немає видимості і не в зоні натягу — відпускаємо лук повністю.
                // При memoryChasing (біжимо до точки) це дає ефект "опустив лук і побіг".
                this.mob.stopUsingItem();
                this.attackTime = this.attackIntervalMin; // заново чекатиме перед натягом
            } else if (canSee) {
                int useTime = this.mob.getTicksUsingItem();
                if (useTime >= 20) {
                    Vec3 realVel = com.example.examplemod.utils.PlayerVelocityTracker.getRealVelocity(target);
                    AdvancedAimMath.AimResult aim = ProjectileTrajectory.resolveBallisticAimWithMissCheck(
                            this.mob, target, 3.0f, realVel.scale(1.8), 0.25
                    );
                    if (aim != null) {
                        debugLogShot(target, aim, realVel);
                        shootCustomArrow(aim);
                        this.mob.stopUsingItem();
                        this.attackTime = this.attackIntervalMin;
                    }
                }
                // canSee == false але memoryChasing/drawBow == true: лук лишається натягнутим без пострілу.
            }
        } else if (--this.attackTime <= 0 && (canSee || drawBow)) {
            this.mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof BowItem));
        }
    }

    /**
     * ТИМЧАСОВИЙ DEBUG-ЛОГ: виводить у чат гравцю-цілі повні координати пострілу,
     * чи був на шляху союзник (для точного і для фінального aim), і чи спрацювала похибка.
     * Видалити після того, як баг знайдено.
     */
    private void debugLogShot(LivingEntity target, AdvancedAimMath.AimResult finalAim, Vec3 realVel) {
        if (!(target instanceof net.minecraft.world.entity.player.Player player)) {
            return;
        }

        Vec3 shooterPos = this.mob.position();
        Vec3 startPos = this.mob.getEyePosition();
        Vec3 targetPos = target.position();
        Vec3 aimPoint = startPos.add(finalAim.dX(), finalAim.dY(), finalAim.dZ());

        AdvancedAimMath.AimResult precise = AdvancedAimMath.calculatePreciseAim(this.mob, target, 3.0f, realVel.scale(1.8));
        boolean wasMiss = Math.abs(precise.dX() - finalAim.dX()) > 1.0e-6
                || Math.abs(precise.dY() - finalAim.dY()) > 1.0e-6
                || Math.abs(precise.dZ() - finalAim.dZ()) > 1.0e-6;

        Vec3 startForCheck = this.mob.getEyePosition();
        boolean preciseBlocked = !ProjectileTrajectory.isPathClearBallistic(
                this.mob, startForCheck, startForCheck.add(precise.dX(), precise.dY(), precise.dZ()), precise.velocity(), 0.28);
        boolean finalBlocked = !ProjectileTrajectory.isPathClearBallistic(
                this.mob, startForCheck, aimPoint, finalAim.velocity(), 0.28);

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                String.format(java.util.Locale.US,
                        "[DEBUG] Гравець(%.2f,%.2f,%.2f) Скелет(%.2f,%.2f,%.2f) Виліт(%.2f,%.2f,%.2f) "
                                + "Ціль_польоту(%.2f,%.2f,%.2f) Похибка=%s БлокПряма=%s БлокФінал=%s",
                        targetPos.x, targetPos.y, targetPos.z,
                        shooterPos.x, shooterPos.y, shooterPos.z,
                        startPos.x, startPos.y, startPos.z,
                        aimPoint.x, aimPoint.y, aimPoint.z,
                        wasMiss, preciseBlocked, finalBlocked
                )
        ));

        // Додатковий лог: координати ВСІХ союзних мобів у радіусі 50 блоків від стрільця,
        // щоб точно знати позицію того, хто потенційно заважає, замість гадання.
        net.minecraft.world.phys.AABB searchBox = this.mob.getBoundingBox().inflate(50.0);
        for (net.minecraft.world.entity.Entity e : this.mob.level().getEntities(this.mob, searchBox)) {
            if (e instanceof LivingEntity living && EnemyFactionRegistry.isSameFaction(this.mob, living)) {
                net.minecraft.world.phys.AABB box = living.getBoundingBox();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        String.format(java.util.Locale.US,
                                "[DEBUG] Союзник %s: box(minX=%.3f,maxX=%.3f,minY=%.3f,maxY=%.3f,minZ=%.3f,maxZ=%.3f)",
                                living.getName().getString(),
                                box.minX, box.maxX, box.minY, box.maxY, box.minZ, box.maxZ
                        )
                ));
            }
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