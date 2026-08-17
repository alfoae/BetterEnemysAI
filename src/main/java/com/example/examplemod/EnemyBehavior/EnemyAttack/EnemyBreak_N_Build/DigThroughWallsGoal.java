package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import com.example.examplemod.event.EnemyBreak_N_BuildEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Копає крізь суцільну стіну на шляху до цілі. Пара до {@link BuildPathGoal}: той бере на себе
 * підйом (ціль помітно вище) і мости (яма попереду), цей — випадок, коли попереду просто стіна.
 * Розмежування природне через {@code canUse()} обох Goal-ів (якщо наступний крок не суцільний —
 * це не сюди), явної координації між ними не потрібно.
 * <p>
 * Активний лише в CHASING/GOING_TO_LAST_SEEN (не в SEARCHING — див.
 * {@link PursuitEnemyBehavior#isSearchModeActive}), і лише поки шлях до живої/застиглої точки
 * переслідування дійсно заблокований. Спільний гейт і перевірка шляху — {@link EnemyBreak_N_BuildUtils}.
 * <p>
 * Ціль копання — не точна позиція гравця, а найближча прохідна колонка біля неї (див.
 * {@link EnemyBreak_N_BuildUtils#findNearestOpenArea}), і на кожному кроці ламається лише один
 * блок у напрямку руху по прямій — це НЕ повноцінний A*, а проста евристика.
 * <p>
 * Швидкість копання — {@link MiningTierData#getTicksPerBlock()}, глобальний тір на весь сервер.
 * Викопані блоки складаються в {@code IMobBlockStorage} моба (дроп при смерті — обробляється в
 * {@link EnemyBreak_N_BuildEvents}). Блоки з block entity (скрині, спавнери тощо) не ламаються —
 * див. {@link EnemyBreak_N_BuildUtils#isBreakable}.
 */
public class DigThroughWallsGoal extends Goal {

    private static final int RETARGET_INTERVAL_TICKS = 40; // раз на 2с - досить часто, щоб не тупити, і не занадто, щоб не гріти сервер
    private static final int STUCK_CHECK_INTERVAL_TICKS = 20;
    private static final double ARRIVED_DIST_SQ = 4.0; // 2 блоки - вважаємо, що дійшли до цілі

    private final Mob mob;

    private BlockPos digTarget;
    private BlockPos currentlyBreaking;
    private int breakProgressTicks;
    private int retargetTimer;
    private int stuckCheckTimer;

    public DigThroughWallsGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!EnemyBreak_N_BuildUtils.canOperate(this.mob)) return false;

        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos == null) return false;

        boolean blocked = EnemyBreak_N_BuildUtils.isNavigationBlocked(this.mob, chasePos);
        // ТИМЧАСОВИЙ DEBUG: throttle через tickCount - canUse() опитується щотіку, поки Goal
        // не активний.
        if (blocked && this.mob.tickCount % 20 == 0) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] canUse()=true, "
                    + "chasePos=" + BlockPos.containing(chasePos) + " моб=" + this.mob.blockPosition());
        }
        return blocked;
    }

    @Override
    public boolean canContinueToUse() {
        if (!EnemyBreak_N_BuildUtils.canOperate(this.mob)) return false;
        // Свідомо НЕ перевіряємо isNavigationBlocked тут ще раз (дорога операція) - поки Goal вже
        // активний, за цим стежить stuckCheckTimer у tick(), скидаючи digTarget на "шлях вільний".
        return this.digTarget != null;
    }

    @Override
    public void start() {
        this.retargetTimer = 0;
        this.stuckCheckTimer = STUCK_CHECK_INTERVAL_TICKS;
        this.currentlyBreaking = null;
        this.breakProgressTicks = 0;
        // ТИМЧАСОВИЙ DEBUG
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] START моб=" + this.mob.blockPosition());
    }

    @Override
    public void stop() {
        // ТИМЧАСОВИЙ DEBUG: лог ДО обнулення digTarget.
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] STOP. Був target=" + this.digTarget
                + " моб=" + this.mob.blockPosition());
        this.digTarget = null;
        this.currentlyBreaking = null;
        this.breakProgressTicks = 0;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.mob.level() instanceof ServerLevel level)) return;

        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos == null) {
            this.digTarget = null;
            return;
        }

        if (--this.retargetTimer <= 0 || this.digTarget == null) {
            BlockPos oldTarget = this.digTarget; // ТИМЧАСОВИЙ DEBUG
            this.digTarget = EnemyBreak_N_BuildUtils.findNearestOpenArea(this.mob, level, chasePos);
            this.retargetTimer = RETARGET_INTERVAL_TICKS;
            if (oldTarget != null && !oldTarget.equals(this.digTarget)) {
                EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] RETARGET "
                        + oldTarget + " -> " + this.digTarget);
            }
        }

        // Періодично перепровіряємо, чи шлях і досі заблокований (наприклад, гравець сам
        // прокопав стіну) - інакше canContinueToUse() тут не допоможе: він лише дивиться, чи
        // currentlyBreaking==null і pickNextBlockToBreak() і далі повертатиме null, а digTarget
        // сам собою не скинеться - без цього navigation міг би довго йти на застарілу ціль.
        if (--this.stuckCheckTimer <= 0) {
            this.stuckCheckTimer = STUCK_CHECK_INTERVAL_TICKS;
            if (!EnemyBreak_N_BuildUtils.isNavigationBlocked(this.mob, chasePos)) {
                // ТИМЧАСОВИЙ DEBUG
                EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] СКИДАННЯ (stuckCheck): "
                        + "шлях більше не заблокований. target=" + this.digTarget);
                this.digTarget = null;
                return;
            }
        }

        if (this.mob.blockPosition().distSqr(this.digTarget) <= ARRIVED_DIST_SQ
                && !EnemyBreak_N_BuildUtils.isNavigationBlocked(this.mob, chasePos)) {
            // ТИМЧАСОВИЙ DEBUG
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] СКИДАННЯ (arrived): "
                    + "моб=" + this.mob.blockPosition() + " target=" + this.digTarget);
            this.digTarget = null;
            return;
        }

        if (this.currentlyBreaking == null) {
            this.currentlyBreaking = pickNextBlockToBreak(level);
            this.breakProgressTicks = 0;
            // ТИМЧАСОВИЙ DEBUG: якщо currentlyBreaking постійно лишається null, поки Goal
            // активний - моб "стоїть і копає повітря", саме кейс "агриться, але не бʼє".
            if (this.mob.tickCount % 20 == 0) {
                EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] pickNextBlockToBreak() -> "
                        + this.currentlyBreaking + " (target=" + this.digTarget + ")");
            }
        }

        if (this.currentlyBreaking != null) {
            this.breakProgressTicks++;
            int ticksNeeded = MiningTierData.get(level.getServer()).getTicksPerBlock();
            if (this.breakProgressTicks >= ticksNeeded) {
                EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG DigThroughWallsGoal] ламаю блок "
                        + this.currentlyBreaking); // ТИМЧАСОВИЙ DEBUG
                EnemyBreak_N_BuildUtils.breakBlock(level, this.currentlyBreaking, this.mob);
                this.currentlyBreaking = null;
                this.breakProgressTicks = 0;
            }
        }

        // ТИМЧАСОВИЙ DEBUG: heartbeat раз/сек поки Goal активний.
        if (this.mob.tickCount % 20 == 0) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, String.format(
                    "[DEBUG DigThroughWallsGoal] активний. моб=%s target=%s currentlyBreaking=%s chasePos=(%.1f,%.1f,%.1f)",
                    this.mob.blockPosition(), this.digTarget, this.currentlyBreaking, chasePos.x, chasePos.y, chasePos.z));
        }

        this.mob.getLookControl().setLookAt(
                this.digTarget.getX() + 0.5, this.digTarget.getY() + 0.5, this.digTarget.getZ() + 0.5, 30.0F, 30.0F);
        this.mob.getNavigation().moveTo(
                this.digTarget.getX() + 0.5, this.digTarget.getY(), this.digTarget.getZ() + 0.5, 1.0D);
    }

    /**
     * Прокладає шлях до digTarget по прямій — ламає лише "наступний" блок у напрямку руху,
     * а не повноцінний A*.
     */
    private BlockPos pickNextBlockToBreak(Level level) {
        BlockPos mobPos = this.mob.blockPosition();
        Vec3 dir = Vec3.atCenterOf(this.digTarget).subtract(Vec3.atCenterOf(mobPos));
        if (dir.lengthSqr() < 0.01) return null;
        dir = dir.normalize();

        BlockPos step = mobPos.offset((int) Math.round(dir.x), 0, (int) Math.round(dir.z));
        if (EnemyBreak_N_BuildUtils.isBreakable(level, step)) return step;

        BlockPos stepUp = step.above();
        if (EnemyBreak_N_BuildUtils.isBreakable(level, stepUp)) return stepUp;

        // Ціль помітно вище/нижче (яма/тунель вертикально) - пробуємо в вертикальному напрямку.
        if (dir.y > 0.3) {
            BlockPos up = mobPos.above(2);
            if (EnemyBreak_N_BuildUtils.isBreakable(level, up)) return up;
        } else if (dir.y < -0.3) {
            BlockPos down = mobPos.below();
            if (EnemyBreak_N_BuildUtils.isBreakable(level, down)) return down;
        }

        return null;
    }
}
