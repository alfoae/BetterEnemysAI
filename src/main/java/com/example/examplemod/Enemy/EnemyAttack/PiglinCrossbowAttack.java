package com.example.examplemod.Enemy.EnemyAttack;

import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.AdvancedAimMath;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.PlayerVelocityTracker;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyShootAhead.ProjectileTrajectory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

/**
 * Повна заміна ванільної crossbow-атаки пігліна. Поведінка ідентична задумом
 * {@link PillagerVindicatorCrossbowAttack}: натягує арбалет -> тримає заряджений -> стріляє
 * ТІЛЬКИ коли лінія вогню (з урахуванням товщини стріли) вільна від союзників по фракції. Якщо
 * союзник заважає — піглін НЕ скидає заряд, просто чекає далі з націленим арбалетом. Окремий
 * клас від піладжера/віндикатора — бо піглін живе на Brain/{@link
 * net.minecraft.world.entity.ai.behavior.Behavior} API (копія+{@code setItemInHand} для
 * синхронізації екіпіровки, окремий стан "кулдаун" після пострілу), а не на {@link
 * net.minecraft.world.entity.ai.goal.Goal}.
 * <p>
 * РУХ ТУТ НЕ ВЕДЕМО — раніше тут був прямий {@code navigation.moveTo(...)} для випадку
 * {@code !canSee}, який паралельно з {@code PursuitBrainBridgeGoal} (веде рух через
 * {@code WALK_TARGET}) змагався за контроль над навігацією/спринтом того самого тіку — той
 * самий клас конфлікту, що й був із ванільною {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach}.
 * Рух і спринт — виключно відповідальність {@code PursuitBrainBridgeGoal}; тут лишається
 * тільки "не намагайся стріляти по тому, кого не бачиш".
 * <p>
 * Винесено з {@link com.example.examplemod.mobAi.Goal.BetterPiglinGoalAi}, яка лишилась
 * тонкою Behavior-обгорткою (checkExtraStartConditions/canStillUse/start/stop/tick — дістає
 * ціль з Brain-пам'яті і делегує сюди).
 */
public class PiglinCrossbowAttack {

    private static final double ARROW_RADIUS = 0.25;

    private static final int CHARGE_TIME = 25;   // час натягу арбалета (як у ванільного гравця/пігліна)
    private static final int COOLDOWN_TIME = 20; // пауза ПІСЛЯ пострілу, перш ніж натягувати знову

    private int state = 0; // 0 - спокій, 1 - натягування, 2 - заряджений (чекаємо чисту лінію), 3 - кулдаун після пострілу
    private int attackTimer;

    /**
     * Викликати з {@code Behavior.start()}.
     */
    public void reset() {
        this.state = 0;
    }

    /**
     * Викликати з {@code Behavior.stop()}.
     */
    public void onStop(Piglin piglin) {
        piglin.stopUsingItem();
        piglin.setChargingCrossbow(false); // про всяк випадок, якщо перервано саме під час натягу (state==1)
        this.state = 0;
    }

    public void tick(Piglin piglin, LivingEntity target) {
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
