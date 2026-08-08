package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBrake_N_Build;

import com.example.examplemod.Config;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import com.example.examplemod.event.EnemyBrake_N_BuildEvents;
import com.example.examplemod.utils.IMobBlockStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Копання крізь перепони до заагреного гравця. Діє ЛИШЕ в CHASING/GOING_TO_LAST_SEEN (не в
 * SEARCHING — див. {@link PursuitEnemyBehavior#isSearchModeActive}) і ЛИШЕ коли звичайна
 * навігація реально не може прокласти шлях (не про "перепону є" — про "перепона блокує ВЕСЬ
 * шлях"). Ціль копання — НЕ точна позиція гравця, а найближча до мобу "порожня ділянка" на
 * приблизно тій самій висоті (див. {@link #findNearestOpenArea}) — щоб гравець, який просто
 * бігає колами на верхівці вузької колони, не змушував мобу копати нескінченний тунель за живою
 * позицією, а натомість моб пробивався до найближчого природного "кишені" на тому ж рівні.
 * <p>
 * Швидкість лам ання — {@link MiningTierData#getTicksPerBlock()}, глобальний тір усього сервера.
 * Зламані блоки йдуть у {@link IMobBlockStorage} мобу (дроп при смерті — {@link EnemyBrake_N_BuildEvents}).
 * Пропускає блоки з block entity (скрині, спавнери тощо) — свідоме обмеження, не було в
 * ТЗ явно, але ламати гравцеві скриню виглядає як явний перебір.
 */
public class DigThroughWallsGoal extends Goal {

    private static final int SEARCH_XZ_RADIUS = 8;
    private static final int SEARCH_Y_RADIUS = 3;
    private static final int RETARGET_INTERVAL_TICKS = 40; // раз на 2с - гравець встигає рухатись, але не спамимо пошук
    private static final int STUCK_CHECK_INTERVAL_TICKS = 20;
    private static final double ARRIVED_DIST_SQ = 4.0; // 2 блоки - "дійшли, далі хай навігація сама"

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
        if (!Config.ENABLE_MOB_TERRAFORMING.get()) return false;
        if (!(this.mob.level() instanceof ServerLevel level)) return false;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        if (!PursuitEnemyBehavior.isMemoryChasing(this.mob)) return false;
        if (PursuitEnemyBehavior.isSearchModeActive(this.mob)) return false;

        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos == null) return false;

        return isPathBlocked(chasePos);
    }

    @Override
    public boolean canContinueToUse() {
        if (!Config.ENABLE_MOB_TERRAFORMING.get()) return false;
        if (!(this.mob.level() instanceof ServerLevel level)) return false;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        if (!PursuitEnemyBehavior.isMemoryChasing(this.mob)) return false;
        if (PursuitEnemyBehavior.isSearchModeActive(this.mob)) return false;
        // НЕ перевіряємо isPathBlocked щотіку тут (дорого) - лише при периодичному
        // stuckCheckTimer в tick(), який сам зупинить Goal, скинувши стан до "шлях вільний".
        return this.digTarget != null;
    }

    @Override
    public void start() {
        this.retargetTimer = 0;
        this.stuckCheckTimer = STUCK_CHECK_INTERVAL_TICKS;
        this.currentlyBreaking = null;
        this.breakProgressTicks = 0;
    }

    @Override
    public void stop() {
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
            this.digTarget = findNearestOpenArea(level, chasePos);
            this.retargetTimer = RETARGET_INTERVAL_TICKS;
        }

        // Періодично перевіряємо, чи шлях уже звільнився (наприклад, гравець сам відійшов) -
        // якщо так, canContinueToUse на наступному циклі GoalSelector-а поверне false природно
        // через те, що currentlyBreaking==null і pickNextBlockToBreak() теж поверне null,
        // а digTarget не скидаємо тут навмисно - хай navigation сама доведе до кінця останній крок.
        if (--this.stuckCheckTimer <= 0) {
            this.stuckCheckTimer = STUCK_CHECK_INTERVAL_TICKS;
            if (!isPathBlocked(chasePos)) {
                this.digTarget = null;
                return;
            }
        }

        if (this.mob.blockPosition().distSqr(this.digTarget) <= ARRIVED_DIST_SQ) {
            // Дійшли до відкритої ділянки - далі це вже не наша робота.
            this.digTarget = null;
            return;
        }

        if (this.currentlyBreaking == null) {
            this.currentlyBreaking = pickNextBlockToBreak(level);
            this.breakProgressTicks = 0;
        }

        if (this.currentlyBreaking != null) {
            this.breakProgressTicks++;
            int ticksNeeded = MiningTierData.get(level.getServer()).getTicksPerBlock();
            if (this.breakProgressTicks >= ticksNeeded) {
                breakBlock(level, this.currentlyBreaking);
                this.currentlyBreaking = null;
                this.breakProgressTicks = 0;
            }
        }

        this.mob.getLookControl().setLookAt(
                this.digTarget.getX() + 0.5, this.digTarget.getY() + 0.5, this.digTarget.getZ() + 0.5, 30.0F, 30.0F);
        this.mob.getNavigation().moveTo(
                this.digTarget.getX() + 0.5, this.digTarget.getY(), this.digTarget.getZ() + 0.5, 1.0D);
    }

    /**
     * Чи справді звичайна навігація не може прокласти шлях (а не просто "є перепона попереду").
     */
    private boolean isPathBlocked(Vec3 chasePos) {
        Path path = this.mob.getNavigation().createPath(BlockPos.containing(chasePos), 0);
        return path == null || !path.canReach();
    }

    /**
     * Найближча ДО МОБУ (не до гравця!) "прохідна кишеня" в радіусі навколо живої/застиглої
     * точки гравця, +- {@link #SEARCH_Y_RADIUS} по висоті. Свідомо обмежений радіус пошуку —
     * якщо нічого не знайдено, повертаємось до самої точки гравця як фолбек (краще спробувати
     * докопатись напряму, ніж взагалі нічого не робити).
     */
    private BlockPos findNearestOpenArea(Level level, Vec3 chasePos) {
        BlockPos center = BlockPos.containing(chasePos);
        BlockPos mobPos = this.mob.blockPosition();

        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dy = -SEARCH_Y_RADIUS; dy <= SEARCH_Y_RADIUS; dy++) {
            for (int dx = -SEARCH_XZ_RADIUS; dx <= SEARCH_XZ_RADIUS; dx++) {
                for (int dz = -SEARCH_XZ_RADIUS; dz <= SEARCH_XZ_RADIUS; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (isPassableColumn(level, candidate)) {
                        double distSq = mobPos.distSqr(candidate);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best != null ? best : center;
    }

    private boolean isPassableColumn(Level level, BlockPos pos) {
        return !level.getBlockState(pos).isSolid()
                && !level.getBlockState(pos.above()).isSolid()
                && level.getBlockState(pos.below()).isSolid();
    }

    /**
     * Наступний блок на шляху до digTarget - грубий "жадібний" крок у напрямку цілі, не повний A*.
     */
    private BlockPos pickNextBlockToBreak(Level level) {
        BlockPos mobPos = this.mob.blockPosition();
        Vec3 dir = Vec3.atCenterOf(this.digTarget).subtract(Vec3.atCenterOf(mobPos));
        if (dir.lengthSqr() < 0.01) return null;
        dir = dir.normalize();

        BlockPos step = mobPos.offset((int) Math.round(dir.x), 0, (int) Math.round(dir.z));
        if (isBreakable(level, step)) return step;

        BlockPos stepUp = step.above();
        if (isBreakable(level, stepUp)) return stepUp;

        // Можливо треба піднятись/спуститись (гравець вище/нижче) - пробуємо й вертикальний крок.
        if (dir.y > 0.3) {
            BlockPos up = mobPos.above(2);
            if (isBreakable(level, up)) return up;
        } else if (dir.y < -0.3) {
            BlockPos down = mobPos.below();
            if (isBreakable(level, down)) return down;
        }

        return null;
    }

    private boolean isBreakable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.isSolid()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false; // незламний (бедрок і т.д.)
        return !state.hasBlockEntity(); // скрині, спавнери, тощо - не чіпаємо
    }

    private void breakBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        ItemStack drop = new ItemStack(state.getBlock().asItem());
        if (!drop.isEmpty() && this.mob instanceof IMobBlockStorage storage) {
            storage.addDugBlock(drop);
        }

        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.HOSTILE, 1.0F, 1.0F);
        level.destroyBlock(pos, false);
    }
}