package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * Базовий Goal для мелі-мобів без стрільби (зомбі, wither skeleton, iron golem,
 * крипер тощо), а також для дуал-мод мобів (Vindicator/Pillager в axe-режимі —
 * через predicate-конструктор). Розширюється окремим класом для кожного моба
 * (BetterZombieGoalAi тощо) або використовується напряму з предикатом. Інтегрує
 * PursuitEnemyBehavior: коли гравець вийшов за FOLLOW_RANGE або втратив пряму
 * видимість — моб біжить до застиглої останньої відомої точки або шукає гравця
 * навколо неї.
 * <p>
 * Це ПОВНА заміна ванільного MeleeAttackGoal/ZombieAttackGoal (їх видалено в
 * мixin-ах кожного моба) — відповідає і за рух (в тому числі крізь стіни), і за
 * саму атаку через {@link Mob#doHurtTarget}. Виклик саме doHurtTarget (а не щось
 * нижчого рівня) навмисний: усі мобо-специфічні ефекти при ударі (Wither-ефект у
 * WitherSkeleton, нокбек/підкидання у IronGolem тощо) реалізовані як override
 * doHurtTarget на самому класі моба — завдяки поліморфізму вони спрацюють
 * автоматично, без жодного мобо-специфічного коду тут.
 */
public class PursuitEnemyMeleeBehavior extends Goal {

    protected final Mob mob;
    protected final double speedModifier;
    private final Predicate<Mob> extraCanUseCondition;
    private int ticksUntilNextAttack;

    public PursuitEnemyMeleeBehavior(Mob mob, double speedModifier) {
        this(mob, speedModifier, m -> true);
    }

    /**
     * @param extraCanUseCondition додаткова умова понад наявність chasePos — наприклад
     *                             "тримає сокиру в руці" для дуал-мод мобів, які інакше
     *                             б'ються дистанційно. Для звичайних мелі-мобів передавай
     *                             {@code m -> true} (або юзай 2-арг конструктор).
     */
    public PursuitEnemyMeleeBehavior(Mob mob, double speedModifier, Predicate<Mob> extraCanUseCondition) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        this.extraCanUseCondition = extraCanUseCondition;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Активуємось коли є ціль в пам'яті PursuitEnemyBehavior — це охоплює і
        // звичайний бій впритул (CHASING стартує одразу, як тільки ціль в FOLLOW_RANGE),
        // і погоню крізь стіни/по пам'яті. Плюс додаткова умова (за замовчуванням завжди true).
        return this.extraCanUseCondition.test(this.mob)
                && !this.shouldYield()
                && PursuitEnemyBehavior.getChasePosition(this.mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.extraCanUseCondition.test(this.mob)
                && !this.shouldYield()
                && (PursuitEnemyBehavior.getChasePosition(this.mob) != null
                || !this.mob.getNavigation().isDone());
    }

    /**
     * true, якщо моб уже досить близько до цілі, щоб самому звільнити Flag.MOVE/LOOK —
     * потрібно для мобів типу Creeper, де на цій дистанції має перехопити керування ІНШИЙ
     * ванільний Goal з тим самим флагом (SwellGoal). Без цього наш Goal, раз стартувавши
     * здалеку, ніколи сам не зупиняється (canContinueToUse лишається true весь бій) і
     * назавжди блокує той інший Goal від старту, навіть коли моб уже впритул.
     */
    private boolean shouldYield() {
        double yieldDistSq = this.getYieldDistanceSqr();
        if (yieldDistSq < 0) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return false;
        }
        // Йти в yield має сенс ТІЛЬКИ якщо реально бачимо ціль — інакше під час SEARCHING
        // (коли жива позиція гравця може випадково опинитись поруч, хоч і схована) ми
        // віддамо Flag.MOVE іншому Goal-у (напр. SwellGoal), який теж нічого корисного без
        // LOS не зробить — і обидва просто зависнуть на місці, нікого не рухаючи.
        if (!this.mob.getSensing().hasLineOfSight(target)) {
            return false;
        }
        return this.mob.distanceToSqr(target) <= yieldDistSq;
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
        this.ticksUntilNextAttack = 0;
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.mob.setSprinting(false);
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);

        if (this.ticksUntilNextAttack > 0) {
            this.ticksUntilNextAttack--;
        }

        if (chasePos == null) {
            return;
        }

        double sprintSpeed = PursuitEnemyBehavior.getSprintSpeedModifier(this.mob);

        // chasePos — ЗАВЖДИ правильна точка руху для всіх трьох активних станів (жива позиція
        // в CHASING, застигла точка в GOING_TO_LAST_SEEN, точка пошуку в SEARCHING). НЕ звіряємось
        // з canSee для вибору точки: hasLineOfSight — це чистий рейкаст, він не враховує
        // дистанцію, тож у відкритому полі гравець "видимий" навіть далеко за FOLLOW_RANGE — і
        // код лазив у гілку "жива позиція", намагаючись прокласти шлях за межу дальності
        // навігатора, а vanilla pathfinding такий довгий шлях просто не будує — моб стояв на
        // місці. chasePos сам по собі завжди в розумній (досяжній) відстані для поточного стану.
        this.mob.getNavigation().moveTo(chasePos.x, chasePos.y, chasePos.z, sprintSpeed);
        this.mob.getLookControl().setLookAt(
                chasePos.x, chasePos.y + this.mob.getBbHeight() * 0.5, chasePos.z, 30.0F, 30.0F);
        this.mob.setSprinting(true);

        // А атака — дійсно тільки коли реально бачимо ціль, незалежно від того, з якого
        // стану взявся chasePos.
        boolean canSee = target != null && this.mob.getSensing().hasLineOfSight(target);
        if (canSee) {
            this.tryAttack(target);
        }
    }

    private void tryAttack(LivingEntity target) {
        if (!this.canAttack()) {
            return;
        }
        double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        if (distSq <= this.getAttackReachSqr(target) && this.ticksUntilNextAttack <= 0) {
            this.ticksUntilNextAttack = this.getAttackIntervalTicks();
            this.mob.swing(InteractionHand.MAIN_HAND);
            this.mob.doHurtTarget(target);
        }
    }

    /**
     * Чи бʼє цей моб взагалі (doHurtTarget) при контакті з ціллю. За замовчуванням —
     * так. Перевизначи на false для мобів, які не мають ванільної мелі-атаки і
     * підходять близько з іншою метою (наприклад Creeper — підривається, а не бʼє).
     */
    protected boolean canAttack() {
        return true;
    }

    /**
     * Дистанція в квадраті, ближче якої цей Goal сам себе зупиняє (canUse/canContinueToUse
     * повертають false), звільняючи Flag.MOVE/LOOK іншому Goal-у. За замовчуванням -1
     * (вимкнено — ніколи не звільняємось самі, бо звичайним мелі-мобам більше нема кому
     * передавати керування, атаку ми й самі робимо). Перевизначай, якщо на близькій
     * дистанції має перехопити інший ванільний Goal з тим самим флагом.
     */
    protected double getYieldDistanceSqr() {
        return -1;
    }

    /**
     * Дистанція атаки в квадраті — впритул з невеликим запасом (як у ванільного
     * MeleeAttackGoal), автоматично враховує розмір моба й цілі. Перевизначай
     * у нащадку, якщо конкретному мобу треба інша дистанція.
     */
    protected double getAttackReachSqr(LivingEntity target) {
        double reach = this.mob.getBbWidth() * 2.0D + target.getBbWidth();
        return reach * reach;
    }

    /**
     * Кулдаун між ударами в тіках (20 = 1 секунда, як ванільний дефолт).
     * Перевизначай у нащадку за потреби.
     */
    protected int getAttackIntervalTicks() {
        return 20;
    }
}