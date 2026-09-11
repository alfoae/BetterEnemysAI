package com.example.examplemod.Enemy.EnemyBehavior.EnemyBreak_N_Build;

import com.example.examplemod.Config;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyPursuit_N_Search.PursuitBehavior.PursuitEnemyBehavior;
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
 * <b>Дах — ПОКЛІТИННО, не глобально</b> (живий тест: "сходи в межах прогрузки піднімають зону до
 * неба, хоча гравець на Y=0"): кожна клітинка {@link #bufferedFootprintXZ} несе ВЛАСНЕ значення
 * даху, порахованe від НАЙБЛИЖЧОЇ (у радіусі reach) ділянки накопиченої площі — по суті навколо
 * КОЖНОГО блока площі власне коло радіусом reach, як і навколо КОЖНОГО блока буфера — а не одне
 * спільне число на всю "ковбасу" відразу. Раніше дах рахувався ОДНИМ глобальним максимумом
 * (найвища колись відскановану точку + reach) і застосовувався до всієї накопиченої площі
 * одночасно: досить одного разу зачепити сканом сходинки чи пагорб поруч — і дах злітав над усією
 * зоною, включно з ділянками, де гравець зараз стоїть на Y=0. Тепер висока ділянка піднімає дах
 * лише НАД СОБОЮ (і в межах reach навколо себе), а не над усім маршрутом, який гравець колись
 * пройшов.
 * <p>
 * <b>Монотонне зростання</b> (навмисно, тепер ПОКЛІТИННО): відскановану площу і дах кожної
 * конкретної клітинки можна лише піднімати, ніколи не занижувати в межах життя одного запису.
 * Якщо гравець тимчасово бере предмет, що збільшує {@code Attributes.ENTITY_INTERACTION_RANGE} чи
 * {@code Attributes.BLOCK_INTERACTION_RANGE}, а потім знімає його — уже порахована ділянка зони
 * лишається такою ж великою, ніби предмет і досі надітий. Але це більше не тягне за собою
 * ВЕСЬ накопичений маршрут: підвищення в одному місці "ковбаси" не чіпає дах в інших її
 * ділянках.
 * <p>
 * <b>Виняток — {@link #full7x7Center}</b>: НЕ монотонне, завжди СВІЖЕ значення з останнього
 * сканування. Це не про безпеку (як решта зони), а про "чи є зараз зручне місце для короткого
 * заходу" — якщо гравець розібрав свою рівну площадку, вдавати, що вона й досі є, було б
 * помилкою, а не обережністю.
 * <p>
 * <b>Забування запису — за часом АБО за живучістю мобів</b> (живий тест: "заагрили на Y=100,
 * зомбі загинули, за секунди напали знову вже на Y=50 — зона все ще пам'ятає 100"): запис
 * забувається не лише через {@link #IDLE_FORGET_TICKS} тіш, а й одразу, щойно ЖОДЕН із мобів, що
 * колись реєструвались активними по цій зоні ({@link #activeMobIds}), більше не живий. Перевірка
 * лінива (як і решта реєстру) — спрацьовує в момент, коли НАСТУПНИЙ моб намагається скористатись
 * зоною, тобто рівно тоді, коли стара пам'ять могла б завадити.
 * <p>
 * <b>Об'єднання зон</b>: якщо моб, лізучи до гравця A, БАЧИТЬ напряму (Sensing.hasLineOfSight —
 * той самий метод, що й для "ближчий видимий гравець" в {@link PursuitEnemyBehavior}) гравця B —
 * їхні зони зливаються (union даних, назавжди) в один спільний об'єкт, і ВІДТОДІ обидва UUID у
 * реєстрі вказують на нього: будь-який інший моб, що полізе до A чи до B, читає й доростає той
 * самий спільний запис. Дані не розділяються назад навмисно (як і решта зони — тільки монотонно
 * росте); натомість увесь об'єднаний запис живе/зникає РАЗОМ як одне ціле (один спільний
 * lastTouchedGameTime, один спільний activeMobIds) — щойно жоден моб довго не торкається жодного
 * з пов'язаних гравців (або всі зареєстровані моби мертві), зникають усі одночасно.
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
     * одразу, тому лениво: перевіряємо тільки при читанні/записі, як GlobalSearchGrid). Це
     * і досі верхня межа "на про всяк випадок" — фактична смерть усіх зареєстрованих мобів
     * (див. {@link #activeMobIds}) зазвичай спрацьовує набагато раніше цього таймауту.
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

    /**
     * Наскільки вище за "сира поверхня площі (локально) + reach" піднімаємо дах кожної буферної
     * клітинки. Впливає ЛИШЕ на Y - на відміну від {@link #BUILD_GAP_EXTRA_BLOCKS}, тут навмисно
     * НЕ чіпаємо сам {@code reachBlocks}, який іде і в {@link #bufferedFootprintXZ}, і в
     * {@link #buildExclusionXZ} - інакше цей запас поповз би і в X/Z-буфери теж.
     */
    private static final int ROOF_EXTRA_BLOCKS = 1;

    private final Map<Long, Integer> platformColumns = new HashMap<>();
    /**
     * xz-ключ -> дах ЦІЄЇ конкретної клітинки (локально, не глобальний максимум - див. клас-джавадок).
     */
    private final Map<Long, Integer> bufferedFootprintXZ = new HashMap<>();
    private final Set<Long> buildExclusionXZ = new HashSet<>();
    private final Set<UUID> linkedPlayerIds = new HashSet<>();
    /** Моби, що колись реєструвались як активні по цій зоні - для живучого забування, див. клас-джавадок. */
    private final Set<UUID> activeMobIds = new HashSet<>();
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
        TowerZoneData zone = resolveOrCreate(player.getUUID(), now, level);
        zone.activeMobIds.add(mob.getUUID()); // "тримає" запис живим, поки сам живий - див. purgeIfStale
        zone.touch(now); // дешево, не залежить від throttle скану нижче

        // БАГ, знайдений живим тестом: "now - Long.MIN_VALUE" переповнює long (загортається у
        // ВЕЛИКЕ ВІД'ЄМНЕ число, бо now завжди >=0, а -Long.MIN_VALUE саме собою вже
        // переповнення) - через це умова нижче ніколи не спрацьовувала на найпершому виклику, і
        // rescan() не викликався ЖОДНОГО РАЗУ. Тому сентинел перевіряємо явно, ДО віднімання, а
        // не покладаємось на арифметику з ним.
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
        if (player.level() instanceof ServerLevel level) {
            purgeIfStale(player.getUUID(), now, level);
        }
        return BY_PLAYER.get(player.getUUID());
    }

    private static TowerZoneData resolveOrCreate(UUID playerId, long now, ServerLevel level) {
        purgeIfStale(playerId, now, level);
        TowerZoneData zone = BY_PLAYER.computeIfAbsent(playerId, id -> new TowerZoneData());
        zone.linkedPlayerIds.add(playerId);
        return zone;
    }

    /**
     * Забуває весь запис, якщо (а) {@link #IDLE_FORGET_TICKS} ніхто не торкався (як і раніше),
     * АБО (б) — ФІКС бага "зона пам'ятає висоту навіть після смерті останнього моба" — жоден із
     * мобів, що колись реєструвались активними по цій зоні ({@link #activeMobIds}), вже не живий.
     * (б) власне і покриває випадок з живого тесту: заагрили на Y=100, зомбі загинули, за секунди
     * напали знову вже на Y=50 — перший же виклик {@link #updateForClimbingMob} від НОВОГО моба
     * бачить, що старих активних мобів більше нема в живих, і стирає стару зону ДО того, як новий
     * моб встигне щось із неї прочитати чи доростити.
     */
    private static void purgeIfStale(UUID playerId, long now, ServerLevel level) {
        TowerZoneData existing = BY_PLAYER.get(playerId);
        if (existing == null) return;

        boolean timedOut = now - existing.lastTouchedGameTime > IDLE_FORGET_TICKS;
        boolean noMobsLeftAlive = !existing.activeMobIds.isEmpty() && allDead(existing.activeMobIds, level);

        if (timedOut || noMobsLeftAlive) {
            for (UUID linked : existing.linkedPlayerIds) {
                BY_PLAYER.remove(linked, existing); // тільки якщо й досі вказує саме на цей об'єкт
            }
        }
    }

    /** Заразом прибирає мертві/зниклі UUID із переданого набору - дешева побічна прибирка. */
    private static boolean allDead(Set<UUID> mobIds, ServerLevel level) {
        mobIds.removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
        return mobIds.isEmpty();
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
            for (Map.Entry<Long, Integer> e : b.bufferedFootprintXZ.entrySet()) {
                merged.bufferedFootprintXZ.merge(e.getKey(), e.getValue(), Math::max);
            }
            merged.buildExclusionXZ.addAll(b.buildExclusionXZ);
            merged.activeMobIds.addAll(b.activeMobIds);
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
     * однаково повністю перекриті буфером сусідніх крайових, рахувати їх окремо зайве. Версія
     * "лише членство", без висоти - для {@link #buildExclusionXZ}, якому висота не потрібна
     * (лише "чи ця колонка взагалі колись потрапляє в буфер").
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

    /**
     * Той самий Мінковський-буфер крайових клітинок, але кожна буферна клітинка додатково несе
     * СВОЄ значення даху (surfaceY цього конкретного джерела + {@code reachBlocks} +
     * {@link #ROOF_EXTRA_BLOCKS}) - для {@link #bufferedFootprintXZ}. ФІКС бага "дах до неба по
     * сходах": раніше дах рахувався ОДНИМ числом на всю накопичену площу (глобальний максимум),
     * тепер - окремо для кожної клітинки, від НАЙБЛИЖЧОЇ (в радіусі reach) ділянки площі. Коли до
     * однієї клітинки дотягуються диски з РІЗНИХ за висотою ділянок (стик рівнів) - береться
     * максимум із них: так само "безпечно", як і раніше, але це вже не тягне за собою ввесь
     * накопичений маршрут гравця, а лише те, що справді поруч.
     */
    private static Map<Long, Integer> bufferBoundaryWithRoof(Map<Long, Integer> columns, int reachBlocks, double preciseRadius) {
        Map<Long, Integer> buffered = new HashMap<>();
        double radiusSq = preciseRadius * preciseRadius;
        Set<Long> keys = columns.keySet();

        for (Map.Entry<Long, Integer> col : columns.entrySet()) {
            int x = PlatformScanner.unpackX(col.getKey());
            int z = PlatformScanner.unpackZ(col.getKey());
            if (!isBoundaryCell(keys, x, z)) continue;

            int localRoof = col.getValue() + reachBlocks + ROOF_EXTRA_BLOCKS;
            for (int dx = -reachBlocks; dx <= reachBlocks; dx++) {
                for (int dz = -reachBlocks; dz <= reachBlocks; dz++) {
                    if (dx * (double) dx + dz * (double) dz <= radiusSq) {
                        buffered.merge(PlatformScanner.key(x + dx, z + dz), localRoof, Math::max);
                    }
                }
            }
        }
        for (Map.Entry<Long, Integer> col : columns.entrySet()) { // площа сама по собі теж всередині зони
            buffered.merge(col.getKey(), col.getValue() + reachBlocks + ROOF_EXTRA_BLOCKS, Math::max);
        }
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

    /**
     * Той самий однокроковий "більша вісь наперед" рух, що й {@code EnemyBreak_N_BuildUtils.nextHorizontalStep}.
     */
    private static BlockPos straightHorizontalStep(BlockPos from, BlockPos to, int travelY) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (dx == 0 && dz == 0) return new BlockPos(to.getX(), travelY, to.getZ());
        if (Math.abs(dx) >= Math.abs(dz)) {
            return new BlockPos(from.getX() + Integer.signum(dx), travelY, from.getZ());
        }
        return new BlockPos(from.getX(), travelY, from.getZ() + Integer.signum(dz));
    }

    /**
     * Чи ця позиція (Y ≤ дах ЦІЄЇ конкретної (x,z)-клітинки) належить накопиченій (монотонній)
     * зоні — "чесна" лінія, порахована прямо з reach, БЕЗ {@link #BUILD_GAP_EXTRA_BLOCKS} (та
     * сама, що бачать партикли). Чисто геометричний факт — виняток "є 7x7, тому не зважай" це
     * рішення Фази 2, не цього шару.
     */
    public boolean isInsideZone(BlockPos pos) {
        Integer localRoof = this.bufferedFootprintXZ.get(PlatformScanner.key(pos.getX(), pos.getZ()));
        return localRoof != null && pos.getY() <= localRoof;
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

    private void rescan(ServerLevel level, Player player, long now) {
        // ФІКС (живий тест: "стрибаю - зона росте на 2 блоки, не на 1"): getOnPos() під час
        // польоту (не на землі) - це НЕ реальна опорна поверхня, а груба floor(поточний_Y - 0.2)
        // від фактичної висоти гравця в повітрі; на звичайному стрибку без буста (~1.25 блока)
        // саме через це заокруглення воно може стрибнути одразу на 2, а не на 1, залежно від
        // дробової частини Y, з якої стрибок почався. lastScanGameTime свідомо НЕ чіпаємо -
        // наступний тік одразу спробує ще раз (а не жде повний новий SCAN_INTERVAL_TICKS), тож
        // це лише невелика затримка до приземлення, а не пропущене сканування.
        if (!player.onGround()) return;

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

        for (Map.Entry<Long, Integer> entry : result.columns().entrySet()) {
            // МОНОТОННО щодо ЧЛЕНСТВА (ключ ніколи не видаляється), але Y перезаписуємо на
            // актуальний з цього скану - для посадки важливо знати РЕАЛЬНУ поверхню зараз, а не
            // історичну (на відміну від самого факту "ця колонка колись була площею", який має
            // лишатись назавжди заради буфера/безпеки).
            this.platformColumns.put(entry.getKey(), entry.getValue());
        }

        this.full7x7Center = result.full7x7Center(); // "живе" значення цього скану, див. джавадок класу

        // ФІКС ("дах до неба по сходах"): дах тепер рахуємо ПОКЛІТИННО від НАЙБЛИЖЧОЇ ділянки
        // площі, а не одним глобальним максимумом по всій накопиченій площі - див. клас-джавадок
        // і javadoc bufferBoundaryWithRoof.
        Map<Long, Integer> freshRoofBuffer = bufferBoundaryWithRoof(this.platformColumns, reachBlocks, cappedReach);
        for (Map.Entry<Long, Integer> e : freshRoofBuffer.entrySet()) {
            this.bufferedFootprintXZ.merge(e.getKey(), e.getValue(), Math::max);
        }

        this.buildExclusionXZ.addAll(bufferBoundary(this.platformColumns.keySet(),
                reachBlocks + BUILD_GAP_EXTRA_BLOCKS, cappedReach + BUILD_GAP_EXTRA_BLOCKS));

        debugReport(level, player, result, attackRange, blockRange, rawReach, cappedReach);
    }

    private boolean allNeighborsKnown(int x, int z) {
        return this.platformColumns.containsKey(PlatformScanner.key(x + 1, z))
                && this.platformColumns.containsKey(PlatformScanner.key(x - 1, z))
                && this.platformColumns.containsKey(PlatformScanner.key(x, z + 1))
                && this.platformColumns.containsKey(PlatformScanner.key(x, z - 1));
    }

    /**
     * "будує міст строго над партиклами зони, а не рівно під ними" — один крок горизонтального
     * руху мосту ВІД fromXZ ДО toXZ, обмежений так, щоб (коли можливо) лишатись над клітинками
     * {@link #bufferedFootprintXZ} — тими самими, що позначені партиклями в
     * {@link #spawnBoundaryParticles} — а не різати найкоротшою прямою повз зону, над нічим не
     * позначеною територією.
     * <p>
     * Поки міст ще НЕ зайшов у зону (моб щойно з опорного стовпа, який за побудовою лежить ЗА
     * {@link #buildExclusionXZ}, тобто поза й {@link #bufferedFootprintXZ} теж) — веде
     * найкоротшим шляхом ДО найближчої клітинки зони: сам розрив між стовпом і межею зони нічим
     * не позначений, там і так нема на що дивитись. Щойно fromXZ усередині — веде СУВОРО по
     * клітинках зони (BFS по 4-сусідах) аж до toXZ (або до найближчої зонної клітинки до toXZ,
     * якщо сама ціль з якоїсь причини поза зоною — наприклад {@code findAnyLandingTile} віддав
     * щось за межами {@link #platformColumns}, чого штатно не мало б статись).
     */
    public BlockPos nextBridgeStep(BlockPos fromXZ, BlockPos toXZ, int travelY) {
        long fromKey = PlatformScanner.key(fromXZ.getX(), fromXZ.getZ());
        long toKey = PlatformScanner.key(toXZ.getX(), toXZ.getZ());
        if (fromKey == toKey) {
            return new BlockPos(toXZ.getX(), travelY, toXZ.getZ());
        }

        if (!this.bufferedFootprintXZ.containsKey(fromKey)) {
            Long nearest = nearestZoneCellKey(fromXZ.getX(), fromXZ.getZ());
            BlockPos towards = nearest != null
                    ? new BlockPos(PlatformScanner.unpackX(nearest), travelY, PlatformScanner.unpackZ(nearest))
                    : toXZ; // зона порожня (не мало б статись за живого buildExclusionXZ-старту) - фолбек на пряму до цілі
            return straightHorizontalStep(fromXZ, towards, travelY);
        }

        long targetKey = toKey;
        if (!this.bufferedFootprintXZ.containsKey(targetKey)) {
            Long nearest = nearestZoneCellKey(toXZ.getX(), toXZ.getZ());
            if (nearest == null)
                return straightHorizontalStep(fromXZ, toXZ, travelY); // зона порожня - не мало б статись
            targetKey = nearest;
        }

        Long stepKey = bfsFirstStepWithinZone(fromKey, targetKey);
        if (stepKey == null) {
            // недосяжно СУВОРО по зоні (розірвана на непов'язані острівці) - пряма лінія, як і
            // раніше, краще за повну зупинку.
            return straightHorizontalStep(fromXZ, toXZ, travelY);
        }
        return new BlockPos(PlatformScanner.unpackX(stepKey), travelY, PlatformScanner.unpackZ(stepKey));
    }

    private Long nearestZoneCellKey(int x, int z) {
        Long best = null;
        long bestDistSq = Long.MAX_VALUE;
        for (long key : this.bufferedFootprintXZ.keySet()) {
            long dx = PlatformScanner.unpackX(key) - x;
            long dz = PlatformScanner.unpackZ(key) - z;
            long distSq = dx * dx + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = key;
            }
        }
        return best;
    }

    /**
     * BFS по 4-сусідах СУВОРО в межах {@link #bufferedFootprintXZ}, від fromKey до targetKey.
     * Повертає ключ ПЕРШОГО кроку на знайденому найкоротшому шляху, або null, якщо шляху нема
     * (зона розірвана на непов'язані острівці — теоретично можливо після часткового руйнування).
     * Дешево: зона за живих тестів — одиниці/десятки клітинок, не тисячі.
     */
    private Long bfsFirstStepWithinZone(long fromKey, long targetKey) {
        if (fromKey == targetKey) return null;

        Map<Long, Long> cameFrom = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(fromKey);
        cameFrom.put(fromKey, fromKey);

        while (!queue.isEmpty()) {
            long current = queue.poll();
            if (current == targetKey) break;
            int cx = PlatformScanner.unpackX(current);
            int cz = PlatformScanner.unpackZ(current);
            long[] neighbors = {
                    PlatformScanner.key(cx + 1, cz), PlatformScanner.key(cx - 1, cz),
                    PlatformScanner.key(cx, cz + 1), PlatformScanner.key(cx, cz - 1)
            };
            for (long next : neighbors) {
                if (!this.bufferedFootprintXZ.containsKey(next) || cameFrom.containsKey(next)) continue;
                cameFrom.put(next, current);
                queue.add(next);
            }
        }

        if (!cameFrom.containsKey(targetKey)) return null;

        long step = targetKey;
        while (cameFrom.get(step) != fromKey) {
            step = cameFrom.get(step);
        }
        return step;
    }

    public BlockPos getFull7x7Center() {
        return this.full7x7Center;
    }

    /**
     * Дах ЗОНИ, локальний для конкретної (x,z)-колонки — на відміну від старого глобального
     * roofY, кожна ділянка "ковбаси" тепер несе власну висоту (див. клас-джавадок). Якщо сама
     * колонка не в буфері (звичний випадок для мобової pillarColumn — вона за побудовою ЗА межею
     * буфера, бо {@code isColumnInsideFootprint} мусить бути false, щоб моб узагалі туди
     * закомітився), береться дах НАЙБЛИЖЧОЇ буферної клітинки — саме туди моб і буде мостити.
     * {@code Integer.MIN_VALUE}, якщо зона взагалі порожня (не мало б траплятись, поки
     * TowerClimbGoal її використовує).
     */
    public int getLocalRoofY(int x, int z) {
        long key = PlatformScanner.key(x, z);
        Integer direct = this.bufferedFootprintXZ.get(key);
        if (direct != null) return direct;
        Long nearest = nearestZoneCellKey(x, z);
        return nearest != null ? this.bufferedFootprintXZ.get(nearest) : Integer.MIN_VALUE;
    }

    // ТИМЧАСОВИЙ DEBUG: текстовий звіт УСІМ гравцям, чиї зони зараз об'єднані сюди (щоб було видно
    // й саме об'єднання), + часточки по периметру буферної зони. Прибрати разом з рештою
    // debugMsg-викликів після тестування Фази 1.
    private void debugReport(ServerLevel level, Player player, PlatformScanner.Result result,
                             double attackRange, double blockRange, double rawReach, double cappedReach) {
        BlockPos here = player.getOnPos().above();
        int localRoofHere = getLocalRoofY(here.getX(), here.getZ());
        String msg = String.format(
                "[DEBUG TowerZone] скан=%d_клітинок 7x7=%s атака/блоки=%.1f/%.1f reach(комб.сирий/кеп)=%.1f/%.1f "
                        + "площа(накоп)=%d буфер_партиклів(накоп)=%d буфер_будівництва(накоп)=%d roofY(тут)=%d "
                        + "активних_мобів=%d гравців_в_зоні=%d",
                result.columns().size(),
                result.full7x7Center() != null ? result.full7x7Center().toShortString() : "нема",
                attackRange, blockRange, rawReach, cappedReach,
                this.platformColumns.size(), this.bufferedFootprintXZ.size(), this.buildExclusionXZ.size(),
                localRoofHere, this.activeMobIds.size(), this.linkedPlayerIds.size());

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
        for (Map.Entry<Long, Integer> entry : this.bufferedFootprintXZ.entrySet()) {
            int x = PlatformScanner.unpackX(entry.getKey());
            int z = PlatformScanner.unpackZ(entry.getKey());
            if (!isBoundaryCell(this.bufferedFootprintXZ.keySet(), x, z)) continue;
            level.sendParticles(ParticleTypes.END_ROD, x + 0.5, entry.getValue() + 0.2, z + 0.5,
                    1, 0.0, 0.0, 0.0, 0.0);
            if (++shown > 400) break; // запобіжник - не спамити пакетами на дуже великій зоні
        }
    }
}