package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBrake_N_Build;

import com.example.examplemod.Config;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import com.example.examplemod.utils.IMobBlockStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Спільне для {@link DigThroughWallsGoal} і {@link BuildPathGoal} — обидва мають ту саму гейт-
 * логіку (конфіг/mobGriefing/CHASING-не-SEARCHING) і те саме "куди взагалі йдемо" (найближча до
 * мобу порожня ділянка на висоті гравця, не сама точка гравця — див. {@link #findNearestOpenArea}).
 * Різняться лише тим, ЩО роблять, коли шлях туди заблокований: копання прибирає суцільну
 * перепону, будівництво заповнює яму чи піднімається вгору.
 */
public final class EnemyBrake_N_BuildUtils {

    public static final int SEARCH_XZ_RADIUS = 8;
    public static final int SEARCH_Y_RADIUS = 3;

    private EnemyBrake_N_BuildUtils() {
    }

    /**
     * Спільний гейт: конфіг увімкнений, mobGriefing увімкнений, моб у CHASING/GOING_TO_LAST_SEEN (не SEARCHING).
     */
    public static boolean canOperate(Mob mob) {
        if (!Config.ENABLE_MOB_TERRAFORMING.get()) return false;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        if (!PursuitEnemyBehavior.isMemoryChasing(mob)) return false;
        return !PursuitEnemyBehavior.isSearchModeActive(mob);
    }

    /**
     * Чи справді звичайна навігація не може прокласти шлях (а не просто "є перепона попереду").
     */
    public static boolean isPathBlocked(Mob mob, Vec3 chasePos) {
        Path path = mob.getNavigation().createPath(BlockPos.containing(chasePos), 0);
        return path == null || !path.canReach();
    }

    /**
     * Найближча ДО МОБУ (не до гравця!) "прохідна кишеня" в радіусі навколо живої/застиглої
     * точки гравця, +- {@link #SEARCH_Y_RADIUS} по висоті. Якщо нічого не знайдено, повертає
     * саму точку гравця як фолбек.
     */
    public static BlockPos findNearestOpenArea(Mob mob, Level level, Vec3 chasePos) {
        BlockPos center = BlockPos.containing(chasePos);
        BlockPos mobPos = mob.blockPosition();

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

    private static boolean isPassableColumn(Level level, BlockPos pos) {
        return !level.getBlockState(pos).isSolid()
                && !level.getBlockState(pos.above()).isSolid()
                && level.getBlockState(pos.below()).isSolid();
    }

    /**
     * Грубий "жадібний" горизонтальний крок у напрямку цілі (не повний A*).
     */
    public static BlockPos nextHorizontalStep(Mob mob, BlockPos target) {
        BlockPos mobPos = mob.blockPosition();
        Vec3 dir = Vec3.atCenterOf(target).subtract(Vec3.atCenterOf(mobPos));
        if (dir.lengthSqr() < 0.01) return mobPos;
        dir = dir.normalize();
        return mobPos.offset((int) Math.round(dir.x), 0, (int) Math.round(dir.z));
    }

    /**
     * Чи можна зламати цей блок (не повітря, не бедрок/незламне, немає block entity - скрині/спавнери).
     */
    public static boolean isBreakable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.isSolid()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return !state.hasBlockEntity();
    }

    /**
     * Ламає блок, кладе його в {@link IMobBlockStorage} мобу (якщо той реалізує інтерфейс), грає звук.
     */
    public static void breakBlock(Level level, BlockPos pos, Mob mob) {
        BlockState state = level.getBlockState(pos);

        ItemStack drop = new ItemStack(state.getBlock().asItem());
        if (!drop.isEmpty() && mob instanceof IMobBlockStorage storage) {
            storage.addDugBlock(drop);
        }

        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.HOSTILE, 1.0F, 1.0F);
        level.destroyBlock(pos, false);
    }

    /**
     * Ставить блок мосту/вежі (за {@link DigBlockResolver}) і реєструє його на зникнення в {@link TemporaryBlockData}.
     */
    public static void placeBlock(ServerLevel level, BlockPos pos) {
        Block block = DigBlockResolver.getDigBlock(level);
        BlockState state = block.defaultBlockState();

        level.setBlock(pos, state, 3);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 1.0F, 1.0F);

        long expireAt = level.getGameTime() + (long) Config.PLACED_BLOCK_LIFETIME_SECONDS.get() * 20L;
        TemporaryBlockData.get(level).track(pos, expireAt);
    }
}