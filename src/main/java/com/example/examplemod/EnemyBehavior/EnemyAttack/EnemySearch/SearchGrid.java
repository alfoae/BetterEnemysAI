package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemySearch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Пер-мобовий диспетчер пошуку поверх {@link GlobalSearchGrid}. Щотік:
 * <ol>
 *   <li>Випадковою вибіркою позначає в глобальному реєстрі точки в межах ЗВИЧАЙНОГО
 *       FOLLOW_RANGE моба як прочекані (пряма видимість або фізична близькість).</li>
 *   <li>Коли треба нова ціль — генерує кандидатів на межі FOLLOW_RANGE+1..+2 навколо ПОТОЧНОЇ
 *       позиції (там сітка щойно потрапила в радіус і напевно ще не прочекана), фільтрує вже
 *       прочекані (могли прочекати ІНШІ моби через спільний реєстр) і йде до найближчої реально
 *       прохідної.</li>
 * </ol>
 * Не прив'язаний до жодного конкретного гравця — позначки означають "тут нещодавно перевіряли на
 * БУДЬ-ЯКОГО ворога", а не "тут нема саме гравця Х". Тому й самі точки-цілі теж без прив'язки
 * до конкретної lastSeenPos: межа FOLLOW_RANGE рухається РАЗОМ із мобом, а не лишається
 * прив'язаною до початкової точки.
 * <p>
 * Координати колонок — через ванільний heightmap (вже обчислена й підтримувана грою структура,
 * O(1) лукап) замість ручного перебору блоків од найвищого до землі.
 */
public class SearchGrid {

    /**
     * Скільки випадкових точок у межах FOLLOW_RANGE перевіряти за тік під час SEARCHING.
     */
    private static final int CHECK_SAMPLES_PER_TICK = 40;
    /**
     * Те саме, але для GOING_TO_LAST_SEEN (підхід до точки) — набагато рідше й менше.
     */
    private static final int LIGHT_CHECK_SAMPLES = 5;
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 8;

    /**
     * Скільки напрямків-кандидатів генерується на межі FOLLOW_RANGE+1..+2.
     */
    private static final int FRONTIER_CANDIDATES = 12;
    /**
     * Скільки найближчих кандидатів перевіряти на реальну прохідність шляху навігатором.
     */
    private static final int PATH_CANDIDATES = 6;
    /**
     * Дистанція в квадраті, на якій точка рахується "фізично досягнутою".
     */
    private static final double REACHED_DIST_SQ = 4.0; // ~2 блоки
    /**
     * Дешевий передфільтр ДО виклику дорогого createPath: якщо різниця висот кандидата й моба
     * більша за це — майже напевно стіна чи прірва, яку звичайний моб не подолає напряму;
     * відкидаємо одразу, не витрачаючи CPU на повний пошук шляху.
     */
    private static final double MAX_STEP_HEIGHT_PREFILTER = 4.0;
    /**
     * Запобіжник: ціль повинна бути не далі цього від моба (100 блоків), інакше не йдемо.
     */
    private static final double MAX_TARGET_DIST_FROM_MOB_SQ = 10000.0; // 100^2

    private Vec3 currentTarget;
    private int lightCheckCooldown = 0;

    /**
     * Легке сканування для GOING_TO_LAST_SEEN — тільки позначає точки прочеканими (побічний
     * ефект руху до lastSeenPos), НЕ вибирає ціль (ціллю підходу лишається сама lastSeenPos, без
     * змін). Набагато рідше й менше за {@link #tick} — коло тепер ~2800+ точок (крок 1 блок), і
     * похід у 50-150+ тіків на повній інтенсивності встиг би виїсти суттєву частину ще до старту
     * SEARCHING (та сама помилка, що вже була з попередньою, грубішою сіткою).
     */
    public void lightTick(Mob mob, long currentGameTime) {
        if (this.lightCheckCooldown > 0) {
            this.lightCheckCooldown--;
            return;
        }
        this.lightCheckCooldown = LIGHT_CHECK_INTERVAL_TICKS;
        double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        checkRandomBatch(mob, followRange, currentGameTime, LIGHT_CHECK_SAMPLES);
    }

    /**
     * Повний тік для SEARCHING: сканує (повна інтенсивність) + за потреби переобирає ціль.
     *
     * @return точка, куди йти зараз, або null, якщо найближчим часом нема куди (можна забувати ціль).
     */
    public Vec3 tick(Mob mob, long currentGameTime) {
        double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        Level level = mob.level();

        checkRandomBatch(mob, followRange, currentGameTime, CHECK_SAMPLES_PER_TICK);

        boolean needsNewTarget = this.currentTarget == null
                || mob.position().distanceToSqr(this.currentTarget) <= REACHED_DIST_SQ
                || GlobalSearchGrid.isChecked(level,
                (int) Math.floor(this.currentTarget.x), (int) Math.floor(this.currentTarget.z),
                currentGameTime);

        if (needsNewTarget) {
            this.currentTarget = pickFrontierTarget(mob, followRange, currentGameTime);
        }

        return this.currentTarget;
    }

    /**
     * Випадкова вибірка точок у межах FOLLOW_RANGE навколо ПОТОЧНОЇ позиції моба — позначає
     * видимі/близькі як прочекані в глобальному реєстрі. Випадкова, бо системно обійти ~2800+
     * точок кола (followRange~30, крок 1 блок) за один тік нереально; за весь час SEARCHING
     * (300 тіків) навіть по 40/тік — це ~12000 спроб, з надлишком покриває коло попри повтори.
     */
    private void checkRandomBatch(Mob mob, double followRange, long currentGameTime, int samples) {
        Level level = mob.level();
        Vec3 mobPos = mob.position();
        var random = mob.getRandom();
        for (int i = 0; i < samples; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double dist = Math.sqrt(random.nextDouble()) * followRange; // рівномірно по площі, не по радіусу
            int x = (int) Math.floor(mobPos.x + Math.cos(angle) * dist);
            int z = (int) Math.floor(mobPos.z + Math.sin(angle) * dist);
            if (GlobalSearchGrid.isChecked(level, x, z, currentGameTime)) {
                continue;
            }
            Vec3 point = columnPoint(level, x, z);
            if (mobPos.distanceToSqr(point) <= REACHED_DIST_SQ || hasDirectLineOfSight(mob, point)) {
                GlobalSearchGrid.markChecked(level, x, z, currentGameTime);
            }
        }
    }

    /**
     * Кандидати на межі FOLLOW_RANGE+1..+2 навколо ПОТОЧНОЇ позиції — там зона щойно потрапила в
     * радіус і напевно ще не прочекана. Фільтруються по глобальному реєстру (може вже прочекав
     * ІНШИЙ моб), по дешевому висотному передфільтру, і по реальній прохідності шляху.
     */
    private Vec3 pickFrontierTarget(Mob mob, double followRange, long currentGameTime) {
        Level level = mob.level();
        Vec3 mobPos = mob.position();
        double baseAngle = mob.getRandom().nextDouble() * 2.0 * Math.PI;
        double frontierDist = followRange + 1.5; // приблизно середина смуги +1..+2

        List<Vec3> candidates = new ArrayList<>();
        for (int i = 0; i < FRONTIER_CANDIDATES; i++) {
            double angle = baseAngle + (2.0 * Math.PI / FRONTIER_CANDIDATES) * i;
            int x = (int) Math.floor(mobPos.x + Math.cos(angle) * frontierDist);
            int z = (int) Math.floor(mobPos.z + Math.sin(angle) * frontierDist);
            if (GlobalSearchGrid.isChecked(level, x, z, currentGameTime)) {
                continue;
            }
            Vec3 point = columnPoint(level, x, z);
            if (mobPos.distanceToSqr(point) > MAX_TARGET_DIST_FROM_MOB_SQ) {
                continue; // запобіжник - задалеко від моба (>100 блоків), не йдемо
            }
            if (Math.abs(point.y - mobPos.y) > MAX_STEP_HEIGHT_PREFILTER) {
                // Дешевий передфільтр: різкий перепад висоти - майже напевно стіна/прірва.
                // Одразу позначаємо прочеканою (щоб і інші моби її не пропонували знову) і НЕ
                // витрачаємо CPU на дорогий createPath для завідомо проблемної точки.
                GlobalSearchGrid.markChecked(level, x, z, currentGameTime);
                continue;
            }
            candidates.add(point);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort((a, b) -> Double.compare(mobPos.distanceToSqr(a), mobPos.distanceToSqr(b)));

        Vec3 best = null;
        double bestPathLen = Double.MAX_VALUE;
        int n = Math.min(PATH_CANDIDATES, candidates.size());
        for (int i = 0; i < n; i++) {
            Vec3 candidate = candidates.get(i);
            Path path = mob.getNavigation().createPath(
                    BlockPos.containing(candidate.x, candidate.y, candidate.z), 1);
            if (path == null || !path.canReach()) {
                GlobalSearchGrid.markChecked(level,
                        (int) Math.floor(candidate.x), (int) Math.floor(candidate.z), currentGameTime);
                continue;
            }
            double len = pathLength(mobPos, path);
            if (len < bestPathLen) {
                bestPathLen = len;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Y-координата колонки — через ванільний heightmap (вже обчислена й підтримувана грою
     * структура даних для "яка тут найвища тверда точка", O(1)) замість ручного опускання від
     * найвищого блока до землі блок-за-блоком.
     */
    private Vec3 columnPoint(Level level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    /**
     * Чистий рейкаст по блоках (не по сутностях) від очей моба до точки — саме "бачить напряму,
     * не через стіни", на відміну від Sensing.hasLineOfSight (той про сутностей).
     */
    private boolean hasDirectLineOfSight(Mob mob, Vec3 targetPos) {
        Level level = mob.level();
        Vec3 from = mob.getEyePosition();
        Vec3 to = targetPos.add(0.0, 1.0, 0.0);
        ClipContext ctx = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob);
        BlockHitResult result = level.clip(ctx);
        return result.getType() == HitResult.Type.MISS;
    }

    private double pathLength(Vec3 from, Path path) {
        double total = 0.0;
        Vec3 prev = from;
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            Vec3 next = new Vec3(node.x, node.y, node.z);
            total += prev.distanceTo(next);
            prev = next;
        }
        return total;
    }
}
