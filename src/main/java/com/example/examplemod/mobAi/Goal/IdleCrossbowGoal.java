package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.IWeaponStorage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

import java.util.EnumSet;

/**
 * "Базова" зброя мобу — арбалет. Працює на БУДЬ-ЯКОМУ {@link Mob}, що перемикає арбалет/melee
 * через {@link IWeaponStorage} (той реалізований глобально на {@code Mob} через
 * {@code MobWeaponStorageMixin} — тобто підходить Piglin, Pillager, Vindicator без жодних змін
 * тут). Поки моб НЕ в бою ({@code mob.getTarget() == null}):
 * <ol>
 *   <li>повертає арбалет у руку, якщо зараз там melee-зброя (ховає її в {@link IWeaponStorage},
 *       не губить) — "хай там що станеться, зрештою моб візьме арбалет";</li>
 *   <li>тримає його ЗАРЯДЖЕНИМ — щоб на момент агро постріл був готовий одразу.</li>
 * </ol>
 * <p>
 * ЧОМУ {@code mob.getTarget()}, А НЕ якась Brain-специфічна перевірка (актуально для Piglin):
 * {@code PursuitEnemyBehavior} тримає {@code mob.getTarget()} синхронізованим протягом усього
 * епізоду переслідування (CHASING/GOING_TO_LAST_SEEN/SEARCHING) так само надійно, як і
 * Brain-пам'ять {@code ATTACK_TARGET} (обидва керуються з того самого {@code data.trackedPlayer}
 * — див. {@code PursuitBrainBridgeGoal}). Тож ОДНА перевірка коректна для Goal-based
 * (Pillager/Vindicator, де це взагалі рідне поле) і для Brain-based (Piglin) мобів одночасно —
 * файл не повинен знати, з яким саме типом мобу має справу.
 * <p>
 * meleeItem параметризований конструктором: {@code Items.GOLDEN_SWORD} для Piglin,
 * {@code Items.IRON_AXE} для Pillager/Vindicator.
 */
public class IdleCrossbowGoal extends Goal {

    private static final int CHARGE_TIME = 25; // той самий час натягу, що й у бойових Goal-ах

    private final Mob mob;
    private final Item meleeItem;
    private int chargeTimer = -1; // -1 = зараз не натягуємо

    public IdleCrossbowGoal(Mob mob, Item meleeItem) {
        this.mob = mob;
        this.meleeItem = meleeItem;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        // Не дозволяємо виконувати фонову видачу/тримання арбалета малятам
        if (this.mob.isBaby()) {
            return false;
        }

        return this.mob.getTarget() == null && !isBusyWithSomethingElse();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    /**
     * Ванільна "милування предметом" (гравець кинув / піглін підняв золото — стан
     * {@code MemoryModuleType.ADMIRING_ITEM}) ТИМЧАСОВО тримає підняту річ у ГОЛОВНІЙ руці —
     * тій самій, куди цей Goal хоче покласти арбалет. Без цієї перевірки обидва боролись би за
     * головну руку кожен тік: моб підняв золото щоб помилуватись, а ми тут-таки виривали його й
     * тицяли туди арбалет. ADMIRING_ITEM — суто піглінська пам'ять, тому перевірка через
     * {@code instanceof}, а не напряму (Pillager/Vindicator такого модуля не мають).
     */
    private boolean isBusyWithSomethingElse() {
        if (this.mob instanceof Piglin piglin) {
            return piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM);
        }
        return false;
    }

    @Override
    public void tick() {
        ItemStack mainHand = this.mob.getMainHandItem();

        if (!mainHand.is(Items.CROSSBOW)) {
            // Базова зброя - арбалет: якщо зараз тримає щось інше (типово meleeItem, лишився
            // після бою), повертаємо арбалет, а те, що було в руці, ховаємо на потім.
            IWeaponStorage storage = (IWeaponStorage) this.mob;
            if (mainHand.is(this.meleeItem)) {
                storage.setStoredMelee(mainHand.copy());
            }
            ItemStack storedRanged = storage.getStoredRanged();
            if (storedRanged.isEmpty()) storedRanged = new ItemStack(Items.CROSSBOW);
            this.mob.stopUsingItem(); // про всяк випадок, та сама причина, що й у SwapWeaponGoal
            this.mob.setItemInHand(InteractionHand.MAIN_HAND, storedRanged);
            this.chargeTimer = -1;
            return; // предмет щойно змінився в руці - почнемо заряджати з наступного тіку
        }

        boolean isCharged = !mainHand.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();
        if (isCharged) {
            if (this.chargeTimer >= 0) {
                this.mob.stopUsingItem();
                setCharging(false);
                this.chargeTimer = -1;
            }
            return; // вже натягнутий - і так усе готово до бою
        }

        if (this.chargeTimer < 0) {
            this.mob.startUsingItem(InteractionHand.MAIN_HAND);
            setCharging(true); // саме цей прапорець керує позою в Piglin (getArmPose() читає
            // isChargingCrossbow(), а не isUsingItem()); для мобів без
            // CrossbowAttackMob (instanceof-гард нижче) просто нічого не робить
            this.chargeTimer = CHARGE_TIME;
        } else {
            this.chargeTimer--;
            if (this.chargeTimer <= 0) {
                // Копія + setItemInHand (не мутація на місці) — інакше синхронізація до клієнтів
                // може не помітити зміну CHARGED_PROJECTILES на тому ж об'єкті ItemStack.
                ItemStack chargedCrossbow = mainHand.copy();
                chargedCrossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
                this.mob.setItemInHand(InteractionHand.MAIN_HAND, chargedCrossbow);
                this.mob.stopUsingItem();
                setCharging(false); // натяг завершено - тепер просто тримає заряджений
                this.chargeTimer = -1;
            }
        }
    }

    /**
     * {@code CrossbowAttackMob.setChargingCrossbow} — не всі можливі майбутні користувачі цього
     * Goal-а гарантовано реалізують цей інтерфейс, тому instanceof, а не прямий каст.
     */
    private void setCharging(boolean charging) {
        if (this.mob instanceof CrossbowAttackMob crossbowMob) {
            crossbowMob.setChargingCrossbow(charging);
        }
    }

    @Override
    public void stop() {
        if (this.chargeTimer >= 0) {
            this.mob.stopUsingItem();
            setCharging(false);
            this.chargeTimer = -1;
        }
    }
}