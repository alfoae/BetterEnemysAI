package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * навігація заблокована, але причина НЕ суцільна стіна (це {@link DigThroughWallsGoal}), а
 * відсутність опори під ногами. Спільний гейт і "куди йдемо" — {@link EnemyBreak_N_BuildUtils}.
 * <p>
 * Два режими, обирається щотіку заново залежно від різниці висот з ціллю:
 * <ul>
 *   <li><b>Climb</b> (ціль значно вище — {@link #CLIMB_HEIGHT_THRESHOLD}) — ставить блок під
 *       ноги і стрибає, "піларить" вгору. Якщо щось заважає стрибку (стеля над головою) —
 *       спочатку ламає його ({@link EnemyBreak_N_BuildUtils#isBreakable}/{@link EnemyBreak_N_BuildUtils#breakBlock}),
 *       саме як просив користувач: "якщо перекриває блок — ламає".</li>
 *   <li><b>Bridge</b> (ціль приблизно на тій самій висоті, але попереду яма) — кладе блок під
 *       наступний горизонтальний крок, щоб з'явилась опора, і йде далі.</li>
 * </ul>
 * Розмежування з {@link DigThroughWallsGoal} природне через {@code canUse()}: якщо наступний
 * крок — суцільний блок (стіна), тут {@code canUse()} поверне false (це не яма і не підйом),
 * і копання само візьме на себе. Явної координації між двома Goal-ами не потрібно.
 */
public class BuildPathGoal extends Goal {

    private static final double CLIMB_HEIGHT_THRESHOLD = 2.0;
    private static final int RETARGET_INTERVAL_TICKS = 40;
    private static final int STUCK_CHECK_INTERVAL_TICKS = 20;
    private static final double ARRIVED_DIST_SQ = 4.0;
    private static final int BUILD_ACTION_COOLDOWN_TICKS = 10; // не ставити/ламати щотіку - виглядає природніше

    private final Mob mob;

    private BlockPos buildTarget;
    private int retargetTimer;
    private int stuckCheckTimer;
    private int actionCooldown;

    public BuildPathGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!EnemyBreak_N_BuildUtils.canOperate(this.mob)) return false;
        if (!(this.mob.level() instanceof ServerLevel level)) return false;

        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos == null) return false;
        if (!EnemyBreak_N_BuildUtils.isPathBlocked(this.mob, chasePos)) return false;

        BlockPos target = EnemyBreak_N_BuildUtils.findNearestOpenArea(this.mob, level, chasePos);
        return needsClimb(target) || needsBridge(level, target);
    }

    @Override
    public boolean canContinueToUse() {
        if (!EnemyBreak_N_BuildUtils.canOperate(this.mob)) return false;
        return this.buildTarget != null;
    }

    @Override
    public void start() {
        this.retargetTimer = 0;
        this.stuckCheckTimer = STUCK_CHECK_INTERVAL_TICKS;
        this.actionCooldown = 0;
    }

    @Override
    public void stop() {
        this.buildTarget = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.mob.level() instanceof ServerLevel level)) return;

        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos == null) {
            this.buildTarget = null;
            return;
        }

        if (--this.retargetTimer <= 0 || this.buildTarget == null) {
            this.buildTarget = EnemyBreak_N_BuildUtils.findNearestOpenArea(this.mob, level, chasePos);
            this.retargetTimer = RETARGET_INTERVAL_TICKS;
        }

        if (--this.stuckCheckTimer <= 0) {
            this.stuckCheckTimer = STUCK_CHECK_INTERVAL_TICKS;
            if (!EnemyBreak_N_BuildUtils.isPathBlocked(this.mob, chasePos)) {
                this.buildTarget = null;
                return;
            }
        }

        if (this.mob.blockPosition().distSqr(this.buildTarget) <= ARRIVED_DIST_SQ) {
            this.buildTarget = null;
            return;
        }

        this.mob.getLookControl().setLookAt(
                this.buildTarget.getX() + 0.5, this.buildTarget.getY() + 0.5, this.buildTarget.getZ() + 0.5, 30.0F, 30.0F);

        if (--this.actionCooldown <= 0) {
            this.actionCooldown = BUILD_ACTION_COOLDOWN_TICKS;
            if (needsClimb(this.buildTarget)) {
                handleClimb(level);
            } else {
                handleBridge(level);
            }
        }

        this.mob.getNavigation().moveTo(
                this.buildTarget.getX() + 0.5, this.buildTarget.getY(), this.buildTarget.getZ() + 0.5, 1.0D);
    }

    private boolean needsClimb(BlockPos target) {
        return target.getY() - this.mob.blockPosition().getY() >= CLIMB_HEIGHT_THRESHOLD;
    }

    /**
     * Яма: наступний крок сам НЕ суцільний (інакше це DigThroughWallsGoal), і під ним теж немає опори.
     */
    private boolean needsBridge(Level level, BlockPos target) {
        BlockPos step = EnemyBreak_N_BuildUtils.nextHorizontalStep(this.mob, target);
        if (EnemyBreak_N_BuildUtils.isBreakable(level, step)) return false; // стіна - не наша справа
        return !level.getBlockState(step.below()).isSolid();
    }

    private void handleClimb(ServerLevel level) {
        BlockPos feet = this.mob.blockPosition();
        BlockPos overhead = feet.above(2);

        // Спочатку прибираємо перепону над головою, якщо є ("якщо перекриває блок - ламає") -
        // інакше стрибок все одно вдариться об стелю і нічого не дасть.
        if (EnemyBreak_N_BuildUtils.isBreakable(level, overhead)) {
            EnemyBreak_N_BuildUtils.breakBlock(level, overhead, this.mob);
            return;
        }

        BlockPos below = feet.below();
        if (!level.getBlockState(below).isSolid()) {
            EnemyBreak_N_BuildUtils.placeBlock(level, below);
        }

        this.mob.getJumpControl().jump();
    }

    private void handleBridge(ServerLevel level) {
        BlockPos step = EnemyBreak_N_BuildUtils.nextHorizontalStep(this.mob, this.buildTarget);
        BlockPos stepBelow = step.below();

        if (!level.getBlockState(stepBelow).isSolid()) {
            EnemyBreak_N_BuildUtils.placeBlock(level, stepBelow);
        }
    }
}