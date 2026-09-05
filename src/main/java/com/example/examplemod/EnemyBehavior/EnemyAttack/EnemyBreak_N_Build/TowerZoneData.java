package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.Config;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ФАЗА 1 (фундамент): "площа гравця + reach" — зона, яку моб має обходити ЗОВНІ під час підйому,
 * а не впиратися впритул. "reach" тут — {@link PlayerReachUtils#getCombinedRawReach}: БІЛЬШИЙ з
 * радіуса АТАКИ ({@code Attributes.ENTITY_INTERACTION_RANGE}) і радіуса ВЗАЄМОДІЇ З БЛОКАМИ
 * ({@code Attributes.BLOCK_INTERACTION_RANGE}), +2 — а НЕ сам по собі радіус атаки. Цей reach
 * додається по БОКАХ і ВВЕРХ (буфер площі й дах зони відповідно); ВНИЗ зона нічим не обмежена —
 * (x,z)-колонка, що потрапила в буфер, лишається "в зоні" на будь-якій глибині, а не тільки біля
 * поверхні площі. Спільна на ГРАВЦЯ (не на конкретного моба): будь-який моб, що зараз лізе вгору
 * до того самого гравця, читає й доростає ОДНІ Й ТІ САМІ дані.
 * <p>
 * <b>Два кільця, не одне</b> (живий тест: "моб будується рівно під партиклами"): партикли й
 * {@link #isInsideZone} показують "чесну" лінію — рахуючи прямо з reach, без запасу.
 * {@link #isColumnInsideFootprint} (те, що РЕАЛЬНО вирішує, де мобу можна стартувати/лишатись
 * під час підйому) — на {@link #BUILD_GAP_EXTRA_BLOCKS} ширше. Просто піднявши сам reach, цього
 * проміжку не отримати: "перша клітинка поза буфером" (де стартує моб) і "остання клітинка в
 * буфері" (де стоять партикли) СУСІДНІ за побудовою, хай яким великим не рахуй сам reach —
 * потрібне саме окреме, ширше кільце для рішень підйому.
 * <p>
 * <b>Монотонне зростання</b> (навмисно): відсканована площа, обидва буфери (площа+reach і
 * площа+reach+{@link #BUILD_GAP_EXTRA_BLOCKS}) і "дах" зони (roofY) можуть тільки РОСТИ. Якщо
 * гравець тимчасово бере предмет, що збільшує {@code Attributes.ENTITY_INTERACTION_RANGE} чи
 * {@code Attributes.BLOCK_INTERACTION_RANGE}, а потім знімає його — зона лишається такою ж
 * великою, ніби предмет і досі надітий.
 * <p>
 * <b>Виняток — {@link #full7x7Center}</b>: НЕ монотонне, завжди СВІЖЕ значення з останнього
 * сканування. Це не про безпеку (як решта зони), а про "чи є зараз зручне місце для короткого
 * заходу" — якщо гравець розібрав свою рівну площадку, вдавати, що вона й досі є, було б
 * помилкою, а не обережністю.
 * <p>
 * <b>Об'єднання зон</b>: якщо моб, лізучи до гравця A, БАЧИТЬ напряму (Sensing.hasLineOfSight —
 * той самий метод, що й для "ближчий видимий гравець" в {@link PursuitEnemyBehavior}) гравця B —
 * їхні зони зливаються (union даних, назавжди) в один спільний об'єкт, і ВІДТОДІ обидва UUID у
 * реєстрі вказують на нього: будь-який інший моб, що полізе до A чи до B, читає й доростає той
 * самий спільний запис. Дані не розділяються назад навмисно (як і решта зони — тільки монотонно
 * росте); натомість увесь об'єднаний запис живе/зникає РАЗОМ як одне ціле (один спільний
 * lastTouchedGameTime) — щойно жоден моб довго не торкається жодного з пов'язаних гравців,
 * зникають усі одночасно.
 */
public final class TowerZoneData {

    /**
     * Як часто (в тіках) РЕАЛЬНО пересканувати площу — спільно на всіх мобів, що зараз лізуть
     * до цього гравця (перший, хто прийде після спливання інтервалу, і запускає сканування).
     */
    private static final int SCAN_INTERVAL_TICKS = 10; // 0.5с

    /**
     * Через скільки тіків БЕЗ жодного дотику (жоден climb-моб не викликав update ні для цього
     * гравця, ні для будь-кого, з ким його зону об'єднано) весь запис забувається — грубе
     * наближення до "поки є заагрений моб, що вміє лізти вгору" (реєстр не бачить усіх мобів
     * одразу, тому лениво: перевіряємо тільки при читанні/записі, як GlobalSearchGrid).
     */
    private static final long IDLE_FORGET_TICKS = 60L * 20L; // 60с

    private static final Map<UUID, TowerZoneData> BY_PLAYER = new ConcurrentHashMap<>();

    /**
     * "щоб він строївся за партиклами, не рівно під ними" — наскільки ширшим за
     * {@link #bufferedFootprintXZ} (той самий, що бачать партикли) є набір, який РЕАЛЬНО
     * використовує {@link #isColumnInsideFootprint} для рішень підйому. РІВНО 1 додаткове кільце
     * клітинок так, щоб моб завжди комітився щонайменше на 1 блок ДАЛІ за лінію партиклів, а не
     * впритул до неї — інакше "перша клітинка поза буфером" і "остання клітинка в буфері"
     * (де стоять партикли) за побудовою СУСІДНІ, і жодного видимого проміжку нема, скільки б не
     * рахувати сам reach.
     */
    private static final int BUILD_GAP_EXTRA_BLOCKS = 1;

    private final Map<Long, Integer> platformColumns = new HashMap<>();
    private final Set<Long> bufferedFootprintXZ = new HashSet<>();
    private final Set<Long> buildExclusionXZ = new HashSet<>();
    private final Set<UUID> linkedPlayerIds = new HashSet<>();
    private int roofY = Integer.MIN_VALUE;
    private BlockPos full7x7Center;
    private long lastScanGameTime = Long.MIN_VALUE;
    private long lastTouchedGameTime;

    private TowerZoneData() {
    }

    /**
     * Викликати з будь-якого місця, де моб УЖЕ лізе вгору до гравця (Фаза 1: зараз тільки
     * {@code BuildPathGoal.handleClimb}) — оновлює (за потреби пересканувавши, і за потреби
     * об'єднавши з зонами видимих поруч гравців) і повертає спільну зону цього гравця. Безпечно
     * викликати часто/з кількох мобів одночасно.
     */
    public static TowerZoneData updateForClimbingMob(Mob mob, ServerLevel level) {
        Player player = PursuitEnemyBehavior.getTrackedPlayer(mob);
        if (player == null) return null;

        long now = level.getGameTime();
        TowerZoneData zone = resolveOrCreate(player.getUUID(), now);
        zone.touch(now); // дешево, не залежить від throttle скану нижче

        // БАГ, знайдений живим тестом: "now - Long.MIN_VALUE" переповнює long (загортається у
        // ВЕЛИКЕ ВІД'ЄМНЕ число, бо now завжди >=0, а -Long.MIN_VALUE саме собою вже
        // переповнення) - через це умова нижче ніколи не спрацьовувала на найпершому виклику, і
        // rescan() не викликався ЖОДНОГО РАЗУ (roofY лишався Integer.MIN_VALUE назавжди). Тому
        // сентинел перевіряємо явно, ДО віднімання, а не покладаємось на арифметику з ним.
        if (zone.lastScanGameTime == Long.MIN_VALUE || now - zone.lastScanGameTime >= SCAN_INTERVAL_TICKS) {
            zone.rescan(level, player, now);
            zone.linkVisibleNearbyPlayers(mob, player, level, now);
        }
        return zone;
    }

    /**
     * Та сама зона гравця "на читання", без побічного ефекту сканування — для майбутніх фаз.
     */
    public static TowerZoneData peek(Player player) {
        long now = player.level().getGameTime();
        purgeIfStale(player.getUUID(), now);
        return BY_PLAYER.get(player.getUUID());
    }

    private static TowerZoneData resolveOrCreate(UUID playerId, long now) {
        purgeIfStale(playerId, now);
        TowerZoneData zone = BY_PLAYER.computeIfAbsent(playerId, id -> new TowerZoneData());
        zone.linkedPlayerIds.add(playerId);
        return zone;
    }

    private static void purgeIfStale(UUID playerId, long now) {
        TowerZoneData existing = BY_PLAYER.get(playerId);
        if (existing != null && now - existing.lastTouchedGameTime > IDLE_FORGET_TICKS) {
            for (UUID linked : existing.linkedPlayerIds) {
                BY_PLAYER.remove(linked, existing); // тільки якщо й досі вказує саме на цей об'єкт
            }
        }
    }

    private static void mergeInto(UUID primaryId, Player other, ServerLevel level, long now) {
        UUID otherId = other.getUUID();
        if (primaryId.equals(otherId)) return;

        TowerZoneData a = BY_PLAYER.get(primaryId);
        TowerZoneData b = BY_PLAYER.get(otherId);
        if (a != null && a == b) return; // вже об'єднані - нема що робити

        TowerZoneData merged = (a != null) ? a : new TowerZoneData();
        boolean otherWasNew = (b == null);
        if (b != null && b != merged) {
            merged.platformColumns.putAll(b.platformColumns);
            merged.bufferedFootprintXZ.addAll(b.bufferedFootprintXZ);
            merged.buildExclusionXZ.addAll(b.buildExclusionXZ);
            merged.roofY = Math.max(merged.roofY, b.roofY);
            merged.linkedPlayerIds.addAll(b.linkedPlayerIds);
            merged.lastTouchedGameTime = Math.max(merged.lastTouchedGameTime, b.lastTouchedGameTime);
        }
        merged.linkedPlayerIds.add(primaryId);
        merged.linkedPlayerIds.add(otherId);
        merged.touch(now);

        BY_PLAYER.put(primaryId, merged);
        BY_PLAYER.put(otherId, merged);

        // Гравця, якого щойно долучили, одразу скануємо - інакше "обєднана зона" аж до наступного
        // разу, коли хтось буде лізти саме до нього, включала б лише його порожню стартову клітинку.
        if (otherWasNew) {
            merged.rescan(level, other, now);
        }
    }

    /**
     * Буферизує (Мінковський, диском радіуса reach) лише КРАЙОВІ клітинки площі (ті, в кого хоч
     * один із 4 сусідів — НЕ площа) — запропоноване користувачем спрощення: внутрішні клітинки
     * однаково повністю перекриті буфером сусідніх крайових, рахувати їх окремо зайве.
     */
    private static Set<Long> bufferBoundary(Set<Long> columns, int ceilRadius, double preciseRadius) {
        Set<Long> buffered = new HashSet<>();
        double radiusSq = preciseRadius * preciseRadius;

        for (long colKey : columns) {
            int x = PlatformScanner.unpackX(colKey);
            int z = PlatformScanner.unpackZ(colKey);
            if (!isBoundaryCell(columns, x, z)) continue;

            for (int dx = -ceilRadius; dx <= ceilRadius; dx++) {
                for (int dz = -ceilRadius; dz <= ceilRadius; dz++) {
                    if (dx * (double) dx + dz * (double) dz <= radiusSq) {
                        buffered.add(PlatformScanner.key(x + dx, z + dz));
                    }
                }
            }
        }
        buffered.addAll(columns); // площа сама по собі теж всередині зони
        return buffered;
    }

    private static boolean isBoundaryCell(Set<Long> columns, int x, int z) {
        return !columns.contains(PlatformScanner.key(x + 1, z))
                || !columns.contains(PlatformScanner.key(x - 1, z))
                || !columns.contains(PlatformScanner.key(x, z + 1))
                || !columns.contains(PlatformScanner.key(x, z - 1));
    }

    private void touch(long now) {
        this.lastTouchedGameTime = now;
    }

    /**
     * "якщо хочаб 1 моб таким образом обєднає зону то всі моби... теж будуть учитувати обєднану
     * зону" — тригер НЕ перемикання цілі, а сам факт, що моб (поки лізе вгору до primary) БАЧИТЬ
     * іншого гравця напряму. Радіус — {@code Attributes.FOLLOW_RANGE} моба (та сама межа, що й
     * для "ближчий видимий гравець"): гравець, якого моб і так ніколи б не заагрив через
     * дальність, не повинен об'єднувати зони лише тому, що випадково видимий через відкриту
     * місцевість.
     */
    private void linkVisibleNearbyPlayers(Mob mob, Player primary, ServerLevel level, long now) {
        double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double followRangeSq = followRange * followRange;

        for (Player other : level.players()) {
            if (other == primary || other.isSpectator() || other.isCreative() || !other.isAlive()) {
                continue;
            }
            if (mob.distanceToSqr(other) > followRangeSq) continue;
            if (!mob.getSensing().hasLineOfSight(other)) continue;

            mergeInto(primary.getUUID(), other, level, now);
        }
    }

    private void rescan(ServerLevel level, Player player, long now) {
        this.lastScanGameTime = now;

        BlockPos standingPos = player.getOnPos().above();
        PlatformScanner.Result result = PlatformScanner.scan(
                level, standingPos,
                Config.TOWER_ZONE_SCAN_RADIUS.get(),
                Config.TOWER_ZONE_GAP_MERGE_BLOCKS.get());

        double attackRange = PlayerReachUtils.getRawEntityInteractionRange(player);
        double blockRange = PlayerReachUtils.getRawBlockInteractionRange(player);
        double rawReach = PlayerReachUtils.getCombinedRawReach(player); // = max(attackRange, blockRange) + 2
        double cappedReach = PlayerReachUtils.getReachCappedForZoneSizing(player);
        int reachBlocks = (int) Math.ceil(cappedReach);

        int scanMaxY = Integer.MIN_VALUE;
        for (Map.Entry<Long, Integer> entry : result.columns().entrySet()) {
            // МОНОТОННО щодо ЧЛЕНСТВА (ключ ніколи не видаляється), але Y перезаписуємо на
            // актуальний з цього скану - для посадки важливо знати РЕАЛЬНУ поверхню зараз, а не
            // історичну (на відміну від самого факту "ця колонка колись була площею", який має
            // лишатись назавжди заради буфера/безпеки).
            this.platformColumns.put(entry.getKey(), entry.getValue());
            scanMaxY = Math.max(scanMaxY, entry.getValue());
        }

        this.full7x7Center = result.full7x7Center(); // "живе" значення цього скану, див. джавадок класу

        this.bufferedFootprintXZ.addAll(bufferBoundary(this.platformColumns.keySet(), reachBlocks, cappedReach));
        this.buildExclusionXZ.addAll(bufferBoundary(this.platformColumns.keySet(),
                reachBlocks + BUILD_GAP_EXTRA_BLOCKS, cappedReach + BUILD_GAP_EXTRA_BLOCKS));

        if (scanMaxY != Integer.MIN_VALUE) {
            this.roofY = Math.max(this.roofY, scanMaxY + reachBlocks); // МОНОТОННО - лише max
        }

        debugReport(level, result, attackRange, blockRange, rawReach, cappedReach);
    }

    /**
     * Чи ця позиція (будь-який Y ≤ дах) належить накопиченій (монотонній) зоні — "чесна" лінія,
     * порахована прямо з reach, БЕЗ {@link #BUILD_GAP_EXTRA_BLOCKS} (та сама, що бачать
     * партикли). Чисто геометричний факт — виняток "є 7x7, тому не зважай" це рішення Фази 2, не
     * цього шару.
     */
    public boolean isInsideZone(BlockPos pos) {
        return pos.getY() <= this.roofY
                && this.bufferedFootprintXZ.contains(PlatformScanner.key(pos.getX(), pos.getZ()));
    }

    /**
     * "чи ця (x,z)-колонка взагалі колись потрапляє в буфер на БУДЬ-ЯКІЙ висоті" — але, на
     * відміну від {@link #isInsideZone}, це НЕ "чесна" лінія партиклів, а вже розширена на
     * {@link #BUILD_GAP_EXTRA_BLOCKS}: саме цей (ширший) варіант використовують усі рішення
     * підйому в {@code TowerClimbGoal} (де стартувати, чи "з'їла" зона колонку під час росту, чи
     * вже вийшли з неї після сайдстепу) — щоб моб завжди лишав видимий проміжок ЗА лінією
     * партиклів, а не впритул до неї (партикли й пошук виходу за побудовою СУСІДНІ, тож самого
     * reach для проміжку не досить, потрібне окреме додаткове кільце).
     */
    public boolean isColumnInsideFootprint(int x, int z) {
        return this.buildExclusionXZ.contains(PlatformScanner.key(x, z));
    }

    /**
     * "не контактує з прірвою" — усі 4 сусіди теж є у відомій площі (тобто не самий край, де
     * знизу може виявитись відкритий космос). Клітинки, яких немає в {@link #platformColumns}
     * взагалі, автоматично "небезпечні" (рахуємо невідоме як ризиковане, а не як безпечне).
     */
    public boolean isSafeLandingTile(int x, int z) {
        return this.platformColumns.containsKey(PlatformScanner.key(x, z)) && allNeighborsKnown(x, z);
    }

    /**
     * Пріоритет 1: найближча (до preferNearestTo) БЕЗПЕЧНА клітинка відомої площі. null, якщо
     * такої взагалі немає (наприклад площа — суцільно тонкий міст 1 блок завширшки).
     */
    public BlockPos findSafeLandingTile(BlockPos preferNearestTo) {
        return findLandingTile(preferNearestTo, true);
    }

    /**
     * Пріоритет 2: найближча клітинка площі БЕЗ вимоги безпечності — останній варіант, коли
     * безпечної взагалі немає: краще ризикована ціль, ніж жодної.
     */
    public BlockPos findAnyLandingTile(BlockPos preferNearestTo) {
        return findLandingTile(preferNearestTo, false);
    }

    private BlockPos findLandingTile(BlockPos preferNearestTo, boolean requireSafe) {
        BlockPos best = null;
        long bestDistSq = Long.MAX_VALUE;
        for (Map.Entry<Long, Integer> entry : this.platformColumns.entrySet()) {
            int x = PlatformScanner.unpackX(entry.getKey());
            int z = PlatformScanner.unpackZ(entry.getKey());
            if (requireSafe && !allNeighborsKnown(x, z)) continue;

            long dx = x - preferNearestTo.getX();
            long dz = z - preferNearestTo.getZ();
            long distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = new BlockPos(x, entry.getValue(), z);
            }
        }
        return best;
    }

    private boolean allNeighborsKnown(int x, int z) {
        return this.platformColumns.containsKey(PlatformScanner.key(x + 1, z))
                && this.platformColumns.containsKey(PlatformScanner.key(x - 1, z))
                && this.platformColumns.containsKey(PlatformScanner.key(x, z + 1))
                && this.platformColumns.containsKey(PlatformScanner.key(x, z - 1));
    }

    public BlockPos getFull7x7Center() {
        return this.full7x7Center;
    }

    public int getRoofY() {
        return this.roofY;
    }

    // ТИМЧАСОВИЙ DEBUG: текстовий звіт УСІМ гравцям, чиї зони зараз об'єднані сюди (щоб було видно
    // й саме об'єднання), + часточки по периметру буферної зони. Прибрати разом з рештою
    // debugMsg-викликів після тестування Фази 1.
    private void debugReport(ServerLevel level, PlatformScanner.Result result,
                             double attackRange, double blockRange, double rawReach, double cappedReach) {
        String msg = String.format(
                "[DEBUG TowerZone] скан=%d_клітинок 7x7=%s атака/блоки=%.1f/%.1f reach(комб.сирий/кеп)=%.1f/%.1f "
                        + "площа(накоп)=%d буфер_партиклів(накоп)=%d буфер_будівництва(накоп)=%d roofY=%d "
                        + "гравців_в_зоні=%d",
                result.columns().size(),
                result.full7x7Center() != null ? result.full7x7Center().toShortString() : "нема",
                attackRange, blockRange, rawReach, cappedReach,
                this.platformColumns.size(), this.bufferedFootprintXZ.size(), this.buildExclusionXZ.size(),
                this.roofY, this.linkedPlayerIds.size());

        for (UUID id : this.linkedPlayerIds) {
            Player p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                PursuitEnemyBehavior.debugMsg(p, msg);
            }
        }

        spawnBoundaryParticles(level);
    }

    private void spawnBoundaryParticles(ServerLevel level) {
        int shown = 0;
        for (long key : this.bufferedFootprintXZ) {
            int x = PlatformScanner.unpackX(key);
            int z = PlatformScanner.unpackZ(key);
            if (!isBoundaryCell(this.bufferedFootprintXZ, x, z)) continue;
            level.sendParticles(ParticleTypes.END_ROD, x + 0.5, this.roofY + 0.2, z + 0.5,
                    1, 0.0, 0.0, 0.0, 0.0);
            if (++shown > 400) break; // запобіжник - не спамити пакетами на дуже великій зоні
        }
    }
}