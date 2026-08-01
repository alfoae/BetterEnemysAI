package com.example.examplemod.EnemyBehavior.EnemyAttack;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Міст між {@link PursuitEnemyBehavior} (звичайний Goal — коректно рахує CHASING/
 * GOING_TO_LAST_SEEN/SEARCHING/FORGOTTEN для БУДЬ-ЯКОГО Mob, включно з Brain-мобами) і
 * РЕАЛЬНОЮ бойовою логікою Brain-мобів (Piglin, PiglinBrute, Hoglin...), яка керується не
 * через {@code mob.getTarget()}, а через memory-модулі {@code ATTACK_TARGET}/{@code WALK_TARGET}.
 * <p>
 * ЧОМУ ЦЕЙ ФАЙЛ ВЗАГАЛІ ПОТРІБЕН. {@link PursuitEnemyBehavior#tick()} підтримує актуальність
 * цілі виключно через {@code mob.setTarget(player)}. Для звичайних (Goal-based) мобів цього
 * достатньо. Але для Brain-мобів {@code setTarget()} РЕАЛЬНИЙ бій не перемикає: Brain продовжує
 * читати {@code ATTACK_TARGET}, повністю ігноруючи класичне поле. Тому цей Goal щотіку
 * "перекладає" рішення {@link PursuitEnemyBehavior} у Brain-пам'ять.
 * <p>
 * ЧОМУ WALK_TARGET ВЕДЕМО ЗАВЖДИ, А НЕ ЛИШЕ КОЛИ {@code !canSee} (діагностовано емпірично, не
 * лише в теорії): ванільна {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach} з FIGHT-списку
 * ПРИБРАНА в {@link com.example.examplemod.mobAi.Mixin.PiglinMixin} — вона щобрейнтік тягла
 * WALK_TARGET на ЖИВУ позицію {@code attack_target}, ігноруючи нашу {@code chasePos}, і коли
 * гравець був за стіною (жива точка недосяжна пафайндеру) — це накопичувало
 * {@code cant_reach_walk_target_since}, через що {@code StopAttackingIfTargetInvalid} скидав
 * {@code ATTACK_TARGET}, ми його одразу відновлювали, і виходив нескінченний цикл: FIGHT-задачі
 * (меле/арбалет) щотіку рестартували (звідси видима "смикана" анімація/звук агра), а сам моб
 * ніколи стабільно не йшов туди, куди насправді треба. Тепер ЦЕЙ Goal — єдине джерело
 * WALK_TARGET для FIGHT: коли canSee, {@code chasePos} від {@link PursuitEnemyBehavior} і так
 * дорівнює живій позиції (те саме, що робила прибрана ванільна задача, просто тепер прозоро для
 * нас), а коли ні — застигла точка з пам'яті/пошуку.
 * <p>
 * ВАЖЛИВО (обмеження застосування): додавай цей Goal ЛИШЕ мобам, чий Brain дійсно має
 * модулі {@code ATTACK_TARGET} і {@code WALK_TARGET} (Piglin, PiglinBrute, Hoglin), і ЛИШЕ якщо
 * ти так само прибрав з їхнього FIGHT-списку ванільний walk-target-setter (інакше знову буде
 * той самий tug-of-war, що й описано вище). Для звичайних Goal-based мобів (Zombie, Skeleton...)
 * цей Goal НЕ потрібен.
 */
public class PursuitBrainBridgeGoal extends Goal {

    private static final int WALK_TARGET_COMPLETION_RANGE = 1; // "досить близько" в блоках

    // Поки моб щось натягує/використовує (arrow charging тощо) — йде повільніше й не спринтить,
    // так само, як гравець сповільнюється під час натягу лука/арбалета. isUsingItem() — ЗВИЧАЙНЕ
    // поле LivingEntity, BetterPiglinGoalAi/PiglinIdleCrossbowGoal керують ним через
    // startUsingItem/stopUsingItem, тут просто читаємо стан, нічого додатково узгоджувати не треба.
    private static final float CHARGING_SPEED_MODIFIER = 0.6F;

    private final Mob mob;

    public PursuitBrainBridgeGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return PursuitEnemyBehavior.getTrackedPlayer(this.mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean isInterruptable() {
        return true;
    }

    @Override
    public void tick() {
        Player tracked = PursuitEnemyBehavior.getTrackedPlayer(this.mob);
        if (tracked == null) {
            return; // canUse/canContinueToUse вже мали б це відсікти — про всяк випадок
        }

        Brain<?> brain = this.mob.getBrain();

        // 1. ATTACK_TARGET — оце РЕАЛЬНО читає бойова логіка Brain-а (меле/арбалет). Якщо
        // PursuitEnemyBehavior вирішив перемкнутись (ближчий видимий гравець вкрав агро, або
        // повертаємось з GOING_TO_LAST_SEEN до справжнього canSee) — форсуємо тут щотіку.
        LivingEntity currentAttackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        if (currentAttackTarget != tracked) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, tracked);
        }

        // 2. WALK_TARGET — ведемо ЗАВЖДИ, поки є куди йти (не лише коли !canSee — див. javadoc
        // класу чому). chasePos сам коректно означає "куди" для будь-якого стану:
        // жива позиція під час CHASING, застигла точка під час GOING_TO_LAST_SEEN/SEARCHING.
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos != null) {
            boolean isCharging = this.mob.isUsingItem();
            float speed = isCharging
                    ? CHARGING_SPEED_MODIFIER
                    : (float) PursuitEnemyBehavior.getSprintSpeedModifier(this.mob);
            brain.setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(chasePos, speed, WALK_TARGET_COMPLETION_RANGE));
            this.mob.getLookControl().setLookAt(
                    chasePos.x, chasePos.y + this.mob.getBbHeight() * 0.5, chasePos.z, 30.0F, 30.0F);
            this.mob.setSprinting(!isCharging);
        }
    }

    @Override
    public void stop() {
        // PursuitEnemyBehavior щойно перейшов у FORGOTTEN (canUse стало false) — прибираємо
        // ATTACK_TARGET, інакше FIGHT-активність Brain-а лишиться "заклиненою" на старій цілі
        // назавжди. Саме час "забути" тепер повністю визначає SEARCH_DURATION_TICKS у
        // PursuitEnemyBehavior — ванільної walk-target-задачі, яка раніше могла б це зробити
        // сама (через cant_reach_walk_target_since), більше немає в FIGHT-списку.
        Brain<?> brain = this.mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        this.mob.setSprinting(false);
    }
}