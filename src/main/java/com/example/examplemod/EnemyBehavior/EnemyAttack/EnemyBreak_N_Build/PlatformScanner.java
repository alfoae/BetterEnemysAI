package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/**
 * Сканує площу, на якій стоїть гравець: НЕ сітка з фіксованим кроком (могла б пропустити стовп,
 * що стоїть не на її клітинках), а BFS від блока під ногами гравця в 4 сторони — так, як гравець
 * реально ходить. Якщо сусідня колонка порожня, шукає далі в тому ж напрямку ще до
 * {@code maxGapBlocks} клітинок; знайшовши там опору — вважає її з'єднаною (гравець міг
 * перестрибнути розрив) і продовжує BFS вже звідти. Обмежено {@code scanRadius} блоків ВІД
 * САМОГО ГРАВЦЯ (не від поточної клітинки фронту) — інакше ланцюжок дозволених розривів міг би
 * "виповзти" далеко за задуманий ліміт.
 * <p>
 * Побічний продукт того самого проходу — пошук повністю суцільного (без жодного розриву,
 * абсолютно рівного) квадрата будь-де у відсканованій площі. Історично й далі зветься "7x7" у
 * коді ({@link #find7x7Center}, {@code full7x7Center} тощо), але фактичний розмір з
 * {@link Config#TOWER_ZONE_FLAT_PATCH_RADIUS} за замовчуванням МЕНШИЙ (3x3) — див. коментар тієї
 * константи.
 */
final class PlatformScanner {

    private PlatformScanner() {
    }

    static Result scan(ServerLevel level, BlockPos playerStandingPos, int scanRadius, int maxGapBlocks) {
        Map<Long, Integer> columns = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();

        long startKey = key(playerStandingPos.getX(), playerStandingPos.getZ());
        columns.put(startKey, playerStandingPos.getY());
        visited.add(startKey);
        frontier.add(playerStandingPos);

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.poll();

            for (int[] dir : dirs) {
                for (int step = 1; step <= maxGapBlocks + 1; step++) {
                    int cx = current.getX() + dir[0] * step;
                    int cz = current.getZ() + dir[1] * step;

                    if (outsideRadius(playerStandingPos, cx, cz, scanRadius)) break;

                    long candidateKey = key(cx, cz);
                    if (visited.contains(candidateKey)) break; // вже дісталися сюди іншим шляхом

                    Integer surfaceY = findStandableY(level, cx, cz, current.getY());
                    if (surfaceY != null) {
                        columns.put(candidateKey, surfaceY);
                        visited.add(candidateKey);
                        frontier.add(new BlockPos(cx, surfaceY, cz));
                        break; // з'єдналися - далі в ЦЬОМУ напрямку піде вже наступна ітерація BFS
                    }
                    // порожньо - пробуємо на 1 блок далі в тому ж напрямку (в межах maxGapBlocks)
                }
            }
        }

        BlockPos full7x7 = find7x7Center(columns, playerStandingPos);
        return new Result(columns, full7x7);
    }

    private static boolean outsideRadius(BlockPos center, int x, int z, int radius) {
        long dx = x - center.getX();
        long dz = z - center.getZ();
        return dx * dx + dz * dz > (long) radius * radius;
    }

    /**
     * Шукає найближчу (до nearY) висоту стояння в (x,z): суцільний блок під ногами і 2 прохідні
     * блоки над ним (ноги+голова) — той самий критерій, що й isPassableColumn у
     * {@link EnemyBreak_N_BuildUtils}, тільки параметризований по Y, а не прив'язаний до однієї
     * конкретної позиції. Порядок перебору {0,1,-1,2,-2} — спершу шукаємо рівно на тій самій
     * висоті, звідки прийшли, тоді на 1 вище/нижче тощо (легкі сходинки/нерівності платформи).
     */
    private static Integer findStandableY(ServerLevel level, int x, int z, int nearY) {
        int[] offsets = {0, 1, -1, 2, -2};
        for (int dy : offsets) {
            int standY = nearY + dy;
            BlockPos below = new BlockPos(x, standY - 1, z);
            BlockPos feet = new BlockPos(x, standY, z);
            BlockPos head = new BlockPos(x, standY + 1, z);
            if (level.getBlockState(below).isSolid()
                    && !level.getBlockState(feet).isSolid()
                    && !level.getBlockState(head).isSolid()) {
                return standY;
            }
        }
        return null;
    }

    private static BlockPos find7x7Center(Map<Long, Integer> columns, BlockPos preferNearestTo) {
        BlockPos best = null;
        long bestDistSq = Long.MAX_VALUE;

        for (Map.Entry<Long, Integer> entry : columns.entrySet()) {
            int cx = unpackX(entry.getKey());
            int cz = unpackZ(entry.getKey());
            int centerY = entry.getValue();

            if (!isFull7x7(columns, cx, cz, centerY)) continue;

            long dx = cx - preferNearestTo.getX();
            long dz = cz - preferNearestTo.getZ();
            long distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = new BlockPos(cx, centerY, cz);
            }
        }
        return best;
    }

    /**
     * Повністю суцільний (жодного розриву) і абсолютно РІВНИЙ (та сама висота) квадрат — розмір
     * керується {@link Config#TOWER_ZONE_FLAT_PATCH_RADIUS} (за замовчуванням радіус 1 = 3x3, не
     * 7x7 — див. коментар константи: ЖИВИЙ ТЕСТ показав, що вимога рівно 7x7 без жодного розриву
     * на реальних площадках гравця майже ніколи не справджується, і "короткий" шлях у
     * {@code TowerClimbGoal} (TO_7X7/CROSS_AND_DIG) просто ніколи не отримує керування).
     */
    private static boolean isFull7x7(Map<Long, Integer> columns, int cx, int cz, int y) {
        int radius = Config.TOWER_ZONE_FLAT_PATCH_RADIUS.get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Integer colY = columns.get(key(cx + dx, cz + dz));
                if (colY == null || colY != y) return false;
            }
        }
        return true;
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    static int unpackX(long key) {
        return (int) (key >> 32);
    }

    static int unpackZ(long key) {
        return (int) key;
    }

    record Result(Map<Long, Integer> columns, BlockPos full7x7Center) {
    }
}