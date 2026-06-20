package com.example.examplemod.EnemyBehavior;

import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.PlayerVelocityTracker;
import com.example.examplemod.utils.ProjectileTrajectoryUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Повна заміна ванільної crossbow-атаки пігліна.
 * Поведінка ідентична CustomCrossbowShootGoal (скелет/піладжер/віндикатор):
 * натягує арбалет -> тримає заряджений -> стріляє ТІЛЬКИ коли лінія вогню
 * (з урахуванням товщини стріли) вільна від союзників по фракції.
 * Якщо союзник заважає — піглін НЕ скидає заряд, просто чекає далі з націленим арбалетом.
 */
public class PiglinCrossbowAttackBehavior extends Behavior<Piglin> {

    private static final double ARROW_RADIUS = 0.25;

    private int state = 0; // 0 - спокій, 1 - натягування, 2 - заряджений (чекаємо чисту лінію)
    private int attackTimer;

    public PiglinCrossbowAttackBehavior() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Piglin piglin) {
        return piglin.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Piglin piglin, long gameTime) {
        return piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && piglin.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    protected void start(ServerLevel level, Piglin piglin, long gameTime) {
        this.state = 0;
    }

    @Override
    protected void stop(ServerLevel level, Piglin piglin, long gameTime) {
        piglin.stopUsingItem();
        this.state = 0;
    }

    @Override
    protected void tick(ServerLevel level, Piglin piglin, long gameTime) {
        LivingEntity target = piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) return;

        piglin.getLookControl().setLookAt(target, 30.0F, 30.0F);
        ItemStack crossbow = piglin.getMainHandItem();

        if (state == 0) {
            boolean isCharged = !crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();

            if (isCharged) {
                this.state = 2;
                this.attackTimer = 5;
                piglin.setAggressive(true);
            } else {
                piglin.startUsingItem(piglin.getUsedItemHand());
                piglin.setAggressive(true);
                this.attackTimer = 15;
                this.state = 1;
            }
        } else if (state == 1) {
            this.attackTimer--;
            if (this.attackTimer <= 0) {
                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
                piglin.stopUsingItem();
                this.state = 2;
                this.attackTimer = 5;
            }
        } else if (state == 2) {
            this.attackTimer--;
            if (this.attackTimer <= 0) {

                // Рахуємо приціл заздалегідь, бо isPathClear перевіряє саме РЕАЛЬНУ
                // траєкторію з випередженням, а не пряму лінію до поточної позиції цілі.
                Vec3 targetVel = PlayerVelocityTracker.getRealVelocity(target).scale(2);
                AdvancedAimMath.AimResult aim = AdvancedAimMath.calculateAim(piglin, target, 4.0F, targetVel);

                if (aim == null) {
                    // Не вдалось розрахувати приціл (наприклад занадто екстремальна геометрія) —
                    // тримаємо заряджене і пробуємо знову наступного тіку.
                    this.attackTimer = 1;
                    return;
                }

                if (!ProjectileTrajectoryUtils.isPathClear(piglin, aim, ARROW_RADIUS)) {
                    // Союзник на лінії вогню — арбалет ЗАЛИШАЄТЬСЯ заряджений (state не змінюємо,
                    // CHARGED_PROJECTILES не чистимо), просто чекаємо ще тік і перевіряємо знову.
                    this.attackTimer = 1;
                    return;
                }

                shootWithPrediction(piglin, target, aim);

                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);

                this.state = 0;
                this.attackTimer = 10; // кулдаун до наступної зарядки
            }
        }
    }

    private void shootWithPrediction(Piglin piglin, LivingEntity target, AdvancedAimMath.AimResult aim) {
        ItemStack crossbow = piglin.getMainHandItem();
        float speed = 4.0F;

        Projectile projectile = ProjectileUtil.getMobArrow(piglin, new ItemStack(Items.ARROW), speed, crossbow);
        if (projectile instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(1.3);
        }

        double pX = piglin.getX() + aim.dX();
        double pZ = piglin.getZ() + aim.dZ();
        double vanillaAddedHeight = target.getBbHeight() * 0.3333333333333333D;
        double pY = piglin.getEyeY() + aim.dY() - vanillaAddedHeight;

        projectile.shoot(pX - piglin.getX(), pY - piglin.getEyeY(), pZ - piglin.getZ(), speed, 1.0F);

        piglin.level().addFreshEntity(projectile);
    }
}