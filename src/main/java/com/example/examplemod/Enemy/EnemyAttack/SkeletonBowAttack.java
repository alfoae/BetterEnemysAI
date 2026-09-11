package com.example.examplemod.Enemy.EnemyAttack;

import com.example.examplemod.Enemy.EnemyBehavior.EnemyPursuit_N_Search.PursuitBehavior.PursuitEnemyBehavior;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.AdvancedAimMath;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.PlayerVelocityTracker;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.ProjectileTrajectory;
import com.example.examplemod.Enemy.EnemyFactionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Атака скелета (і будь-якого {@link AbstractSkeleton}) луком: натяг/відпускання за
 * {@code canSee}, сам постріл кастомною стрілою з балістичним випередженням і похибкою
 * промаху. Рух лишився в {@link com.example.examplemod.Enemy.EnemyMovement.SkeletonStrafeMovement}
 * — {@code canSee} рахує саме він (щоб не дублювати distanceSq/hasLineOfSight), Goal передає
 * готове значення сюди.
 * <p>
 * Винесено з {@link com.example.examplemod.mobAi.Goal.BetterSkeletonGoalAi}.
 */
public class SkeletonBowAttack {

    private final AbstractSkeleton mob;
    private final int attackIntervalMin;
    private int attackTime = -1;

    public SkeletonBowAttack(AbstractSkeleton mob, int attackIntervalMin) {
        this.mob = mob;
        this.attackIntervalMin = attackIntervalMin;
    }

    /**
     * Викликати з {@code Goal.stop()} — той самий момент, коли оригінал скидав attackTime.
     */
    public void reset() {
        this.attackTime = -1;
    }

    public void tick(LivingEntity target, boolean canSee) {
        // Чи моб зараз "переслідує крізь стіни" по глобальній пам'яті про ціль — якщо так,
        // дозволяємо тримати лук натягнутим навіть без прямої видимості (canSee==false).
        boolean drawBow = PursuitEnemyBehavior.shouldDrawBowstring(this.mob);

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
                    Vec3 realVel = PlayerVelocityTracker.getRealVelocity(target);
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
        if (!(target instanceof Player player)) {
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

        player.sendSystemMessage(Component.literal(
                String.format(Locale.US,
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
        AABB searchBox = this.mob.getBoundingBox().inflate(50.0);
        for (Entity e : this.mob.level().getEntities(this.mob, searchBox)) {
            if (e instanceof LivingEntity living && EnemyFactionRegistry.isSameFaction(this.mob, living)) {
                AABB box = living.getBoundingBox();
                player.sendSystemMessage(Component.literal(
                        String.format(Locale.US,
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
        ItemStack bowStack = this.mob.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this.mob, item -> item instanceof BowItem));
        ItemStack ammoStack = this.mob.getProjectile(bowStack);

        // 2. Використовуємо ПУБЛІЧНУ утиліту (це виправляє помилку "protected access")
        // Вона сама викликає getArrow всередині Minecraft, де це дозволено
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this.mob, ammoStack, 1.0f, bowStack);

        // 3. РУЧНЕ НАКЛАДАННЯ ЕФЕКТІВ (якщо утиліта їх пропустила)
        // Перевіряємо, чи це Зимогор (Stray)
        if (this.mob instanceof Stray && arrow instanceof Arrow tippedArrow) {
            tippedArrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600)); // Уповільнення на 30 сек
        }

        // Перевіряємо, чи це Болотяник (Bogged)
        // Якщо твоя версія гри підтримує Bogged, ця перевірка спрацює:
        if (this.mob.getType().toString().contains("bogged") && arrow instanceof Arrow tippedArrow) {
            tippedArrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100)); // Отрута на 5 сек
        }

        // 4. Твоя математика шкоди та запуск
        double damageMultiplier = 3.0 / aim.velocity();
        arrow.setBaseDamage(arrow.getBaseDamage() * damageMultiplier);

        arrow.shoot(aim.dX(), aim.dY(), aim.dZ(), aim.velocity(), aim.inaccuracy());

        this.mob.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.mob.getRandom().nextFloat() * 0.4F + 0.8F));
        this.mob.level().addFreshEntity(arrow);
    }
}
