package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

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
import org.jetbrains.annotations.NotNull;

public final class EnemyBreak_N_BuildUtils {

    public static final int SEARCH_XZ_RADIUS = 8;
    public static final int SEARCH_Y_RADIUS = 3;

    private static final double MIN_CANDIDATE_DIST_SQ = 4.0;

    private EnemyBreak_N_BuildUtils() {
    }

    public static boolean canOperate(Mob mob) {
        if (!Config.ENABLE_MOB_TERRAFORMING.get()) return false;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        if (!PursuitEnemyBehavior.isMemoryChasing(mob)) return false;
        return !PursuitEnemyBehavior.isSearchModeActive(mob);
    }

    /**
     * "«аблоковано" з надбавкою за р≥зницю висот: ц≥ль на 2+ блоки вище вважаЇтьс€ заблокованою,
     * нав≥ть €кщо ван≥льний шл€х техн≥чно ≥снуЇ Ч так тригеритьс€ п≥дйом у {@link BuildPathGoal}.
     * {@link DigThroughWallsGoal} висота сама по соб≥ не ц≥кавить (копанн€ не залежить в≥д того,
     * наск≥льки вище ц≥ль) Ч там використовуЇтьс€ чист≥ший {@link #isNavigationBlocked} без ц≥Їњ
     * надбавки. ƒв≥ р≥зн≥ назви навмисно Ч щоб однакова назва не наштовхувала на думку, що це
     * один ≥ той самий тест.
     */
    public static boolean isPathBlocked(Mob mob, Vec3 chasePos) {
        double heightDifference = chasePos.y - mob.getY();

        if (heightDifference >= 2.0) {
            return true;
        }

        return isNavigationBlocked(mob, chasePos);
    }

    /**
     * „исто "чи ≥снуЇ прох≥дний шл€х" до точки пересл≥дуванн€ Ч без надбавки за висоту з
     * {@link #isPathBlocked}. ¬икористовуЇтьс€ {@link DigThroughWallsGoal}, €кому висота не
     * заважаЇ: копанн€ йде в напр€мку ц≥л≥ незалежно в≥д того, вище вона чи нижче.
     */
    public static boolean isNavigationBlocked(Mob mob, Vec3 chasePos) {
        Path path = mob.getNavigation().createPath(
                BlockPos.containing(chasePos), 0);

        return path == null || !path.canReach();
    }

    public static BlockPos findNearestOpenArea(Mob mob, Level level, Vec3 chasePos) {
        BlockPos center = BlockPos.containing(chasePos);
        BlockPos mobPos = mob.blockPosition();

        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dy = -SEARCH_Y_RADIUS; dy <= SEARCH_Y_RADIUS; dy++) {
            for (int dx = -SEARCH_XZ_RADIUS; dx <= SEARCH_XZ_RADIUS; dx++) {
                for (int dz = -SEARCH_XZ_RADIUS; dz <= SEARCH_XZ_RADIUS; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    double distSq = mobPos.distSqr(candidate);

                    if (distSq <= MIN_CANDIDATE_DIST_SQ) continue;
                    if (distSq < bestDistSq && isPassableColumn(level, candidate)) {
                        bestDistSq = distSq;
                        best = candidate;
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

    public static BlockPos nextHorizontalStep(Mob mob, BlockPos target) {
        BlockPos mobPos = mob.blockPosition();
        Vec3 dir = Vec3.atCenterOf(target).subtract(Vec3.atCenterOf(mobPos));
        if (dir.lengthSqr() < 0.01) return mobPos;
        dir = dir.normalize();
        return mobPos.offset((int) Math.round(dir.x), 0, (int) Math.round(dir.z));
    }

    public static boolean isBreakable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.isSolid()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return !state.hasBlockEntity();
    }

    public static void breakBlock(Level level, BlockPos pos, Mob mob) {
        BlockState state = level.getBlockState(pos);

        ItemStack drop = new ItemStack(state.getBlock().asItem());
        if (!drop.isEmpty() && mob instanceof IMobBlockStorage storage) {
            storage.addDugBlock(drop);
        }

        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.HOSTILE, 1.0F, 1.0F);
        level.destroyBlock(pos, false);
    }

    public static void placeBlock(ServerLevel level, BlockPos pos) {
        Block block = DigBlockResolver.getDigBlock(level);
        BlockState state = block.defaultBlockState();

        level.setBlock(pos, state, 3);
        level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.HOSTILE, 1.0F, 1.0F);

        long expireAt = level.getGameTime() + (long) Config.PLACED_BLOCK_LIFETIME_SECONDS.get() * 20L;
        TemporaryBlockData.get(level).track(pos, expireAt);
    }
}