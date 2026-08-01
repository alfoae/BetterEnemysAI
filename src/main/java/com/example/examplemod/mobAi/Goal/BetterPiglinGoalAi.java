package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.PlayerVelocityTracker;
import com.example.examplemod.utils.ProjectileTrajectory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * <p>
 * РУХ ТУТ НЕ ВЕДЕМО — раніше тут був прямий {@code navigation.moveTo(...)} для випадку
 * {@code !canSee}, який паралельно з {@code PursuitBrainBridgeGoal} (веде рух через
 * {@code WALK_TARGET}) змагався за контроль над навігацією/спринтом того самого тіку — той
 * самий клас конфлікту, що й був із ванільною {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach}.
 * Тепер рух і спринт — виключно відповідальність {@code PursuitBrainBridgeGoal}; тут лишається
 * тільки "не намагайся стріляти по тому, кого не бачиш".
 */
public class BetterPiglinGoalAi extends Behavior<Piglin> {

    private static final double ARROW_RADIUS = 0.25;

    private static final int CHARGE_TIME = 25;   // час натягу арбалета (як у ванільного гравця/пігліна)
    private static final int COOLDOWN_TIME = 20; // пауза ПІСЛЯ пострілу, перш ніж натягувати знову

    private int state = 0; // 0 - спокій, 1 - натягування, 2 - заряджений (чекаємо чисту лінію), 3 - кулдаун після пострілу
    private int attackTimer;

    public BetterPiglinGoalAi() {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Piglin piglin) {
        return !piglin.isBaby() && piglin.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Piglin piglin, long gameTime) {
        return !piglin.isBaby()
                && piglin.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && piglin.getMainHandItem().is(Items.CROSSBOW);
    }

    @Override
    protected void start(ServerLevel level, Piglin piglin, long gameTime) {
        this.state = 0;
    }

    @Override
    protected void stop(ServerLevel level, Piglin piglin, long gameTime) {
        piglin.stopUsingItem();
        piglin.setChargingCrossbow(false); // про всяк випадок, якщо перервано саме під час натягу (state==1)
        this.state = 0;
    }

    @Override
    protected void tick(ServerLevel level, Piglin piglin, long gameTime) {
        LivingEntity target = piglin.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (target == null) return;

        // Без прямої видимості нема сенсу намагатись стріляти/натягувати - PursuitBrainBridgeGoal
        // і так веде пігліна до chasePos. Просто чекаємо, поки видимість не повернеться.
        boolean canSee = piglin.getSensing().hasLineOfSight(target);
        if (!canSee) {
            return;
        }

        piglin.getLookControl().setLookAt(target, 30.0F, 30.0F);
        ItemStack crossbow = piglin.getMainHandItem();

        if (state == 0) {
            boolean isCharged = !crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();

            if (isCharged) {
                this.state = 2;
                this.attackTimer = 5;
                piglin.setAggressive(true);
            } else {
                piglin.startUsingItem(InteractionHand.MAIN_HAND);
                piglin.setChargingCrossbow(true); // САМЕ цей прапорець керує getArmPose() у Piglin,
                // isUsingItem() сам по собі на позу не впливає
                piglin.setAggressive(true);
                this.attackTimer = CHARGE_TIME;
                this.state = 1;
            }
        } else if (state == 1) {
            this.attackTimer--;
            if (this.attackTimer <= 0) {
                // Копія + setItemInHand, а не мутація на місці: синхронізація екіпіровки до
                // клієнтів (для правильної анімації натягу/підйому) інакше могла не помітити
                // зміну CHARGED_PROJECTILES на ТОМУ Ж об'єкті ItemStack.
                ItemStack chargedCrossbow = crossbow.copy();
                chargedCrossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
                piglin.setItemInHand(InteractionHand.MAIN_HAND, chargedCrossbow);
                piglin.stopUsingItem();
                piglin.setChargingCrossbow(false); // натяг завершено - тепер просто тримає заряджений
                this.state = 2;
                this.attackTimer = 5;
            }
        } else if (state == 2) {
            double followRange = piglin.getAttributeValue(Attributes.FOLLOW_RANGE); // або mob для CustomCrossbowShootGoal
            if (target.distanceToSqr(piglin) > Math.pow(followRange * 0.75, 2)) {
                this.attackTimer = 1;
                return; // занадто далеко — не стріляємо, чекаємо
            }
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

                if (!ProjectileTrajectory.isPathClear(piglin, aim, 0.30)) {
                    // Союзник на лінії вогню — арбалет ЗАЛИШАЄТЬСЯ заряджений (state не змінюємо,
                    // CHARGED_PROJECTILES не чистимо), просто чекаємо ще тік і перевіряємо знову.
                    this.attackTimer = 1;
                    return;
                }

                shootWithPrediction(piglin, target, aim);
                piglin.onCrossbowAttackPerformed(); // частина контракту CrossbowAttackMob

                // Та сама причина, що й вище: копія + setItemInHand для надійної синхронізації.
                ItemStack emptiedCrossbow = crossbow.copy();
                emptiedCrossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
                piglin.setItemInHand(InteractionHand.MAIN_HAND, emptiedCrossbow);

                // ВАЖЛИВО: переходимо в окремий стан "кулдаун", а не одразу в state=0 —
                // інакше на наступному тіку state==0 побачить незаряджений арбалет і одразу
                // почне новий натяг, повністю ігноруючи паузу після пострілу (саме це
                // спричиняло "стрільбу як з кулемета").
                this.state = 3;
                this.attackTimer = COOLDOWN_TIME;
            }
        } else if (state == 3) {
            this.attackTimer--;
            if (this.attackTimer <= 0) {
                this.state = 0; // тепер дозволяємо новий цикл натягу
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