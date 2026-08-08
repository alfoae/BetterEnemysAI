package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 *   <li>Якщо з поточної позиції реально прохідних кандидатів нема (глухий кут) — не здається
 *       одразу, а повертається до останньої "розвилки" (позиції, звідки вже відгалужувались) і
 *       пробує інший із ~11 напрямків, що там не перевіряли. Якщо й там глухо — до ще старішої.
 *       Тільки коли й історія розвилок вичерпана, немає куди йти насправді.</li>
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
    /** Те саме, але для GOING_TO_LAST_SEEN (підхід до точки) — набагато рідше й менше. */
    private static final int LIGHT_CHECK_SAMPLES = 5;
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 8;
    /**
     * Радіус (у блоках) навколо ПОТОЧНОЇ позиції моба, який скановується ПОВНІСТЮ (не випадково,
     * на відміну від {@link #checkRandomBatch}) щотік — див. {@link #sweepNearbyColumns}. Малий,
     * тому дешевий: (2r+1)² клітинок замість ймовірнісного покриття всього FOLLOW_RANGE.
     */
    private static final int NEARBY_SWEEP_RADIUS = 4;

    /** Скільки напрямків-кандидатів генерується на межі FOLLOW_RANGE+1..+2. */
    private static final int FRONTIER_CANDIDATES = 12;
    /**
     * Скільки найближчих кандидатів перевіряти на реальну прохідність шляху навігатором.
     */
    private static final int PATH_CANDIDATES = 12;
    /** Дистанція в квадраті, на якій точка рахується "фізично досягнутою". */
    private static final double REACHED_DIST_SQ = 4.0; // ~2 блоки
    /** Запобіжник: ціль повинна бути не далі цього від моба (100 блоків), інакше не йдемо. */
    private static final double MAX_TARGET_DIST_FROM_MOB_SQ = 10000.0; // 100^2
    /**
     * Запас у блоках понад РЕАЛЬНУ відстань до кандидата, який передається як maxRange у
     * {@code createPath(pos, accuracy, maxRange)}. Без явного maxRange navigator сам рахує зону
     * пошуку як куб {@code mobPos ± (FOLLOW_RANGE + accuracy)} — тобто той самий FOLLOW_RANGE,
     * від якого frontier-кандидати навмисно відступають на +1..+2 блоки (щоб не потрапити в уже
     * прочекану зону). Через це 2-аргументний createPath(pos, accuracy) для них гарантовано
     * провалювався: точка фізично лежала за межею його власного куба пошуку.
     */
    private static final int PATH_RANGE_MARGIN = 4;
    /**
     * Прямовисна відстань до кандидата — це нижня межа реального шляху, не сам шлях: коридор може
     * звиватись. Особливо це важливо при відкаті до старої розвилки (кандидат рахується навколо
     * НЕЇ, а не навколо моба) — реальний маршрут туди йде В ОБХІД, назад через той самий коридор,
     * і фізично довший за пряму лінію. Множник дає запас під це викривлення поверх фіксованого
     * {@link #PATH_RANGE_MARGIN}.
     */
    private static final double PATH_RANGE_DIST_MULTIPLIER = 1.5;
    /**
     * "Вартість" одного градуса повороту від поточного напрямку погляду моба, у тих самих
     * умовних одиницях, що й довжина шляху (блоки) — див. {@link #pickFrontierTarget}. Повний
     * розворот на 180° коштує 180×0.1=18 умовних блоків: достатньо, щоб моб віддавав перевагу
     * кандидату майже впритул попереду перед трохи коротшим, але збоку/ззаду, і йшов назад лише
     * коли попереду справді нічого прохідного не лишилось.
     */
    private static final double TURN_PENALTY_PER_DEGREE = 0.1;
    /**
     * Скільки останніх "розвилок" (позицій, звідки моб успішно обирав нову ціль) пам'ятаємо для
     * відкату. У кожній розвилці з {@link #FRONTIER_CANDIDATES} напрямків реально йшли лише в
     * один — якщо він приведе в глухий кут, лишається ще ~11 неперевірених.
     */
    private static final int MAX_JUNCTION_HISTORY = 10;
    /**
     * Щоб не захаращувати історію розвилками за пару кроків одна від одної.
     */
    private static final double MIN_JUNCTION_SPACING_SQ = 36.0; // 6 блоків
    /**
     * Поріг (у блоках) різниці висот між колонкою й референсною точкою (зазвичай Y моба чи
     * ringCenter), після якого колонка вважається "підозрілою" — швидше за все, це верх стіни
     * лабіринту, бар'єра чи якогось декору (heightmap рахує їх так само, як справжню підлогу), а
     * не реальна підлога поруч. За цим порогом {@link #columnPoint} пробує сусідні колонки.
     */
    private static final int COLUMN_SNAP_THRESHOLD = 3;
    /**
     * Радіус (у блоках), у якому {@link #columnPoint} шукає сусідню колонку з правдоподібнішою висотою.
     */
    private static final int COLUMN_SNAP_RADIUS = 2;
    /**
     * Діапазон (у блоках) вертикального пошуку прохідної підлоги в {@link #findFloorNear} —
     * від referenceY вгору й вниз. Замінює heightmap там, де він дає завідомо хибну висоту
     * (під дахом/стелею — див. {@link #findFloorNear}).
     */
    private static final int VERTICAL_SNAP_RANGE = 6;
    /**
     * ЧИСТО ДЕБАГ-ВІЗУАЛІЗАЦІЯ: ставить червоний бетон на кожну прочекану клітинку, щоб бачити
     * сітку прямо в грі. Прибрати разом з debugMsg в PursuitEnemyBehavior після тестування —
     * за тік може позначатись до {@link #CHECK_SAMPLES_PER_TICK} клітинок, тобто світ вкриється
     * бетоном дуже швидко.
     */
    private static final boolean DEBUG_PLACE_BLOCKS = true;
    /**
     * ЧИСТО ДЕБАГ-ВІЗУАЛІЗАЦІЯ: тримає одну звичайну (не marker/не invisible) стойку для брони,
     * яку телепортує в поточну ціль пошуку щоразу, як вона змінюється — видно прямо в грі, куди
     * зараз прямує моб. Один моб — одна стойка (переміщується, а не плодиться заново). Прибрати
     * разом з debugMsg/DEBUG_PLACE_BLOCKS після тестування.
     */
    private static final boolean DEBUG_SPAWN_TARGET_MARKER = true;

    private Vec3 currentTarget;
    private int lightCheckCooldown = 0;
    /**
     * LIFO-стек розвилок для відкату при глухому куті. Найновіша розвилка — спереду.
     */
    private final Deque<Vec3> junctionHistory = new ArrayDeque<>();
    /**
     * Скільки тіків позначка "прочекано" лишається дійсною в {@link GlobalSearchGrid} — САМЕ
     * звідси, а не з окремої захардкодженої константи всередині GlobalSearchGrid. Раніше
     * GlobalSearchGrid мав своє власне число, синхронізоване з PursuitEnemyBehavior.SEARCH_DURATION_TICKS
     * лише коментарем — і коли час пошуку збільшили для тестів (SEARCH_DURATION_TICKS=9999), ці два
     * числа розійшлись: позначки протухали через стару кількість тіків, ще ДО завершення того
     * самого пошуку, який їх поставив. Тепер довжина пам'яті передається сюди явно при створенні.
     */
    private final long staleAfterTicks;
    /**
     * Дебаг-стойка, що позначає поточну ціль пошуку. null, поки жодної цілі ще не обирали.
     */
    private ArmorStand debugTargetMarker;

    public SearchGrid(long staleAfterTicks) {
        this.staleAfterTicks = staleAfterTicks;
    }

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
        sweepNearbyColumns(mob, currentGameTime);

        // Перевіряємо isChecked і для currentTarget — НАВМИСНА оптимізація: якщо ціль стала
        // прочеканою ще ДО фізичного прибуття (типовий випадок — прямий тунель, де моб бачить
        // ціль на 20-30 блоків уперед прямим променем задовго до того, як дійде), нема сенсу
        // йти до неї впритул, досить того, що вона вже візуально підтверджено порожня.
        //
        // ЦЕ НЕ причина "туда-сюда" — я раніше помилково знімав саме цю перевірку, підозрюючи
        // її. Справжня причина була в SEARCH_DURATION_TICKS=9999*20 (звідси й staleAfterTicks
        // ~2.78 год замість ~20с, уже полагоджено окремо): поки МАЙЖЕ ВСЯ територія годинами
        // висіла "прочеканою", будь-яка щойно обрана ціль (вона й так на самій межі
        // checked-зони — саме там pickFrontierTarget і шукає кандидатів) миттєво теж ставала
        // прочеканою, і перевибір спрацьовував ледь не щотіку. У свіжій непрочеканій території
        // (де ця перевірка й задумана) шанс що саме колонка ЦІЛІ впаде під випадкову вибірку
        // задовго до прибуття - значно нижчий, тому там усе працювало як слід.
        boolean needsNewTarget = this.currentTarget == null
                || mob.position().distanceToSqr(this.currentTarget) <= REACHED_DIST_SQ
                || GlobalSearchGrid.isChecked(level,
                (int) Math.floor(this.currentTarget.x), (int) Math.floor(this.currentTarget.z),
                currentGameTime, this.staleAfterTicks);

        if (needsNewTarget) {
            Vec3 mobPos = mob.position();
            Vec3 next = pickFrontierTarget(mob, followRange, currentGameTime, mobPos);
            if (next != null) {
                // Ця позиція — розвилка: обрали один напрямок, лишились неперевірені інші.
                // Якщо обраний заведе в глухий кут, буде куди повертатись.
                pushJunction(mobPos);
            } else if (!junctionHistory.isEmpty()) {
                // Глухий кут: з ПОТОЧНОЇ позиції реально нема куди йти. За один тік пробуємо ОДНУ
                // (найновішу) розвилку з історії, а не всі одразу — createPath недешевий, а
                // розвилок може бути до MAX_JUNCTION_HISTORY. Кільце кандидатів рахуємо навколо
                // старої розвилки (там справді лишались неперевірені напрямки), а прохідність
                // шляху — як завжди, від РЕАЛЬНОЇ поточної позиції моба: фізично це і є
                // "розвернутись і піти в іншу гілку від того ж перехрестя".
                Vec3 junction = junctionHistory.peekFirst();
                System.out.println("Dead end at " + mobPos + " -> backtrack to junction " + junction
                        + " (" + junctionHistory.size() + " in history)");
                next = pickFrontierTarget(mob, followRange, currentGameTime, junction);
                if (next == null) {
                    // І ця розвилка вичерпана (стала прочеканою або оточена стінами) — прибираємо,
                    // наступний тік візьме ще старішу. Лишаємось на місці ЦЕЙ тік (не null!):
                    // null тут означав би "остаточно нема куди" і моб забув би ціль негайно,
                    // хоча в історії могли лишатись ще старіші розвилки для спроби.
                    junctionHistory.removeFirst();
                    next = mobPos;
                }
            }
            this.currentTarget = next;
            if (next != null && next != mobPos) {
                // != mobPos (посилання, не значення) навмисно: саме так відрізняємо СПРАВЖНЮ нову
                // ціль від сентінела "постій цей тік, спробуй ще одну розвилку наступного разу" —
                // для нього next це той самий об'єкт mobPos, що й був присвоєний вище.
                updateDebugTargetMarker(mob, next);
            }
        }

        return this.currentTarget;
    }

    /**
     * Додає точку в історію розвилок (найновіша — спереду), якщо вона не надто близько до останньої.
     */
    private void pushJunction(Vec3 point) {
        if (!junctionHistory.isEmpty() && junctionHistory.peekFirst().distanceToSqr(point) < MIN_JUNCTION_SPACING_SQ) {
            return;
        }
        junctionHistory.addFirst(point);
        while (junctionHistory.size() > MAX_JUNCTION_HISTORY) {
            junctionHistory.removeLast();
        }
    }

    /**
     * Телепортує дебаг-стойку в {@code point}, спавнячи її, якщо це перша ціль або попередню
     * прибрали ззовні (гравець зламав/вбив). Один моб — одна стойка, а не нова щоразу.
     */
    private void updateDebugTargetMarker(Mob mob, Vec3 point) {
        if (!DEBUG_SPAWN_TARGET_MARKER || !(mob.level() instanceof ServerLevel serverLevel)) {
            return; // на клієнті сутностей не спавнимо - сервер сам розішле її всім
        }
        if (this.debugTargetMarker == null || !this.debugTargetMarker.isAlive()) {
            this.debugTargetMarker = new ArmorStand(EntityType.ARMOR_STAND, serverLevel);
            serverLevel.addFreshEntity(this.debugTargetMarker);
        }
        this.debugTargetMarker.moveTo(point.x, point.y, point.z, 0.0f, 0.0f);
    }

    /**
     * Прибирає дебаг-стойку, якщо вона є. Викликати ПЕРЕД тим, як цей SearchGrid відкидається чи
     * замінюється новим (інакше стойка лишається сиротою в світі назавжди — вона не деспавнюється
     * сама, на відміну від ворожих мобів).
     */
    public void discardDebugMarker() {
        if (this.debugTargetMarker != null) {
            this.debugTargetMarker.discard();
            this.debugTargetMarker = null;
        }
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
            if (GlobalSearchGrid.isChecked(level, x, z, currentGameTime, this.staleAfterTicks)) {
                continue;
            }
            Vec3 point = columnPoint(level, x, z, mobPos.y);
            boolean checked = false;
            if (mobPos.distanceToSqr(point) <= REACHED_DIST_SQ || hasDirectLineOfSight(mob, point)) {

                checked = true;

                // Координати ТОЧКИ, яку реально перевірили (могла зміститись у columnPoint, якщо
                // (x,z) виявилась верхом стіни/бар'єра) - а не сирі x,z із сімпла.
                GlobalSearchGrid.markChecked(level,
                        (int) Math.floor(point.x), (int) Math.floor(point.z), currentGameTime);
            }
            System.out.println("IsCheaked? " + checked);
        }
    }

    /**
     * Гарантоване (НЕ випадкове) сканування квадрата {@link #NEARBY_SWEEP_RADIUS} навколо
     * ПОТОЧНОЇ позиції моба — на додачу до {@link #checkRandomBatch}.
     * <p>
     * checkRandomBatch дає рівномірне ПО ПЛОЩІ покриття всього FOLLOW_RANGE (~2800+ клітинок),
     * але саме тому в тонких "смужках" (1 блок уздовж стіни коридору чи краю обриву) шанс
     * потрапити під випадкову вибірку пропорційно малий — площа смужки мізерна порівняно з
     * рештою кола. Могло статись так, що моб пройшов прямо ПОВЗ таку смужку впритул, а вона
     * жодного разу не потрапила під випадковий семпл і лишилась непрочеканою; пізніше
     * pickFrontierTarget "знаходить" її як frontier здалеку, і моб спеціально йде через усю
     * печеру відмітити один-єдиний блок, який давно міг перевірити мимохідь.
     * <p>
     * Тут той самий критерій прочеканості, що й у checkRandomBatch (REACHED_DIST_SQ або пряма
     * видимість) — просто застосований ГАРАНТОВАНО до кожної клітинки малого квадрата, а не
     * ймовірнісно. Малий радіус утримує вартість дешевою: (2·{@link #NEARBY_SWEEP_RADIUS}+1)² =
     * 81 клітинка при радіусі 4, і більшість із них відсіюється одразу на isChecked (уже
     * позначені попереднім тіком, коли моб стояв на крок раніше).
     */
    private void sweepNearbyColumns(Mob mob, long currentGameTime) {
        Level level = mob.level();
        Vec3 mobPos = mob.position();
        int mobX = (int) Math.floor(mobPos.x);
        int mobZ = (int) Math.floor(mobPos.z);
        for (int dx = -NEARBY_SWEEP_RADIUS; dx <= NEARBY_SWEEP_RADIUS; dx++) {
            for (int dz = -NEARBY_SWEEP_RADIUS; dz <= NEARBY_SWEEP_RADIUS; dz++) {
                int x = mobX + dx;
                int z = mobZ + dz;
                if (GlobalSearchGrid.isChecked(level, x, z, currentGameTime, this.staleAfterTicks)) {
                    continue;
                }
                Vec3 point = columnPoint(level, x, z, mobPos.y);
                if (mobPos.distanceToSqr(point) <= REACHED_DIST_SQ || hasDirectLineOfSight(mob, point)) {
                    GlobalSearchGrid.markChecked(level,
                            (int) Math.floor(point.x), (int) Math.floor(point.z), currentGameTime);
                }
            }
        }
    }

    /**
     * Кандидати на межі ringCenter+1..+2 — там зона щойно потрапила в радіус (від ringCenter) і
     * напевно ще не прочекана. ringCenter ЗАЗВИЧАЙ дорівнює поточній позиції моба, але при
     * відкаті з глухого кута (див. {@link #tick}) це стара розвилка з історії: кандидатів рахуємо
     * навколо НЕЇ (там справді лишались неперевірені напрямки), а прохідність шляху — від
     * РЕАЛЬНОЇ позиції моба, бо саме звідти navigator фактично будує маршрут. Фільтруються по
     * глобальному реєстру (може вже прочекав ІНШИЙ моб), і по реальній прохідності шляху
     * навігатором.
     */
    private Vec3 pickFrontierTarget(Mob mob, double followRange, long currentGameTime, Vec3 ringCenter) {
        Level level = mob.level();
        Vec3 mobPos = mob.position();
        double baseAngle = mob.getRandom().nextDouble() * 2.0 * Math.PI;
        double frontierDist = followRange + 2.5; // приблизно середина смуги +1..+2
        AttributeInstance attribute = mob.getAttribute(Attributes.FOLLOW_RANGE);
        System.out.println("============================================================");

        System.out.println("Base = " + attribute.getBaseValue());

        for (AttributeModifier modifier : attribute.getModifiers()) {
            System.out.println(
                    modifier.id() + " | " +
                            modifier.amount() + " | " +
                            modifier.operation()
            );
        }

        System.out.println("Value = " + attribute.getValue());
        System.out.println("============================================================");
        System.out.println("FOLLOW_RANGE = " + followRange);
        System.out.println("frontierDist = " + (followRange + 2.5));
        System.out.println("ringCenter = " + ringCenter + " | mobPos = " + mobPos);
        mob.getNavigation().setMaxVisitedNodesMultiplier(10.0f); // або навіть більше



        List<Vec3> candidates = new ArrayList<>();
        for (int i = 0; i < FRONTIER_CANDIDATES; i++) {
            double angle = baseAngle + (2.0 * Math.PI / FRONTIER_CANDIDATES) * i;
            int x = (int) Math.floor(ringCenter.x + Math.cos(angle) * frontierDist);
            int z = (int) Math.floor(ringCenter.z + Math.sin(angle) * frontierDist);
            if (GlobalSearchGrid.isChecked(level, x, z, currentGameTime, this.staleAfterTicks)) {
                continue;
            }
            Vec3 point = columnPoint(level, x, z, ringCenter.y);
            if (mobPos.distanceToSqr(point) > MAX_TARGET_DIST_FROM_MOB_SQ) {
                continue; // запобіжник - задалеко від моба (>100 блоків), не йдемо
            }
            // Висотний передфільтр тут НЕ застосовуємо: frontierDist — 20-35+ блоків від моба,
            // і на такій відстані різниця висот в 4+ блоки - звичайне рельєфне коливання
            // (пологий пагорб, лощина), а не стіна. Фіксований поріг мав сенс тільки для
            // близьких точок; тут покладаємось повністю на createPath нижче — він коректно
            // враховує реальну форму рельєфу незалежно від дистанції.
            candidates.add(point);
        }
        if (candidates.isEmpty()) {
            System.out.println("Candidates: " + candidates.size());
            return null;
        }
        System.out.println("Candidates: " + candidates.size());
        candidates.sort((a, b) -> Double.compare(mobPos.distanceToSqr(a), mobPos.distanceToSqr(b)));

        // Напрямок погляду моба ЗАРАЗ — точка відліку для штрафу за поворот нижче. XZ-складові
        // getViewVector коректні незалежно від pitch (pitch масштабує горизонтальну проекцію
        // рівномірно, не міняючи X:Z співвідношення), тож для суто горизонтального кута це
        // безпечно попри те, що вектор технічно тривимірний.
        Vec3 facing = mob.getViewVector(1.0F);

        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        int n = Math.min(PATH_CANDIDATES, candidates.size());
        for (int i = 0; i < n; i++) {
            Vec3 candidate = candidates.get(i);
            // maxRange явно під РЕАЛЬНУ відстань до кандидата (а не мовчазний FOLLOW_RANGE+accuracy
            // з 2-аргументного createPath) — інакше кандидати на межі FOLLOW_RANGE+1..+2 лежать за
            // межею куба пошуку навігатора, і createPath завжди повертає null / canReach()==false.
            int maxRange = (int) Math.ceil(mobPos.distanceTo(candidate) * PATH_RANGE_DIST_MULTIPLIER) + PATH_RANGE_MARGIN;
            Path path = mob.getNavigation().createPath(
                    BlockPos.containing(candidate.x, candidate.y, candidate.z), 1, maxRange);


            System.out.println("Candidate: " + candidate + " | maxRange: " + maxRange);
            System.out.println("Path: " + path);
            if (path != null) {
                System.out.println("CanReach: " + path.canReach());

            }

            if (path == null || !path.canReach()) {
                GlobalSearchGrid.markChecked(level,
                        (int) Math.floor(candidate.x), (int) Math.floor(candidate.z), currentGameTime);
                continue;
            }
            double len = pathLength(mobPos, path);
            // Кут між facing і напрямком НА кандидата (0° = прямо попереду, 180° = позаду).
            // atan2(|cross|, dot) дає магнітуду кута між двома векторами в [0, π] незалежно від
            // їхніх довжин (обидві скорочуються в співвідношенні) — не треба окремо нормалізувати
            // чи розгортати/обгортати градуси, як довелось би при порівнянні з mob.getYRot().
            double dx = candidate.x - mobPos.x;
            double dz = candidate.z - mobPos.z;
            double dot = facing.x * dx + facing.z * dz;
            double cross = facing.x * dz - facing.z * dx;
            double turnAngleDeg = Math.toDegrees(Math.atan2(Math.abs(cross), dot));
            double score = len + turnAngleDeg * TURN_PENALTY_PER_DEGREE;
            System.out.println("  len=" + len + " turnAngle=" + turnAngleDeg + " score=" + score);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        System.out.println("Best: " + best);

        return best;
    }

    /**
     * Y-координата колонки — через ванільний heightmap (вже обчислена й підтримувана грою
     * структура даних для "яка тут найвища тверда точка", O(1)) замість ручного опускання від
     * найвищого блока до землі блок-за-блоком.
     * <p>
     * MOTION_BLOCKING бачить БУДЬ-ЯКИЙ твердий блок — включно з верхом стіни лабіринту, бар'єра
     * чи декору. У відкритому рельєфі висота між сусідніми колонками міняється плавно, тому це
     * рідко проблема. У лабіринті ж стіни зазвичай набагато вищі за моба і займають більшість
     * площі — тому й будь-який високий блок "заважає поставити точку": колонка з (x,z) під самою
     * стіною дає точку НА ЇЇ ВЕРХІВЦІ, а не на підлозі коридору поруч, createPath туди
     * (обґрунтовано) не може прокласти шлях, і напрямок списується як непрохідний, хоча підлога
     * за метр звідти цілком нормальна.
     * <p>
     * Якщо знайдена висота відхиляється від {@code referenceY} (Y моба чи ringCenter) більш ніж на
     * {@link #COLUMN_SNAP_THRESHOLD} — пробуємо сусідні колонки в радіусі {@link #COLUMN_SNAP_RADIUS}
     * і беремо ту, чия висота найближча до referenceY (і x,z, і y — разом, це й є "підлога поруч зі
     * стіною", а не вигадана висота на тому самому (x,z)). Кращого сусіда не знайшли — лишаємо як
     * було; createPath все одно перевірить реальну прохідність, це лише зменшує ЧАСТОТУ
     * марних спроб, а не остаточний фільтр.
     */
    private Vec3 columnPoint(Level level, int x, int z, double referenceY) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (Math.abs(y - referenceY) <= COLUMN_SNAP_THRESHOLD) {
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        // Heightmap сильно розійшовся з referenceY. РАНІШЕ тут одразу йшли шукати сусідню
        // (x,z)-колонку — але Heightmap.Types.MOTION_BLOCKING ЗАВЖДИ повертає найвищий твердий
        // блок у стовпці, рахуючи від верху світу вниз. Під суцільною стелею це верх стелі (чи
        // рельєф над нею), а не підлога кімнати нижче — і рівно ТА САМА помилка повторюється на
        // будь-якій сусідній колонці під тим самим дахом, тому лателеральний снап нижче нічого
        // не виправляв у цьому випадку (звідси "no reachable frontier point found" одразу під
        // дахом: усі 12 кандидатів отримували Y на даху й createPath туди не міг прокласти шлях).
        // Спершу пробуємо ЛОКАЛЬНИЙ ВЕРТИКАЛЬНИЙ пошук підлоги в ЦІЙ ЖЕ колонці навколо referenceY.
        Vec3 localFloor = findFloorNear(level, x, z, referenceY);
        if (localFloor != null) {
            return localFloor;
        }
        // Нічого прохідного по вертикалі поруч не знайшли (напр. суцільна скеля) — як і раніше,
        // пробуємо сусідні колонки через heightmap; це не гірше за попередню поведінку і досі
        // корисне для звичайного (не накритого) рельєфу.
        int bestX = x;
        int bestZ = z;
        int bestY = y;
        double bestDiff = Math.abs(y - referenceY);
        for (int dx = -COLUMN_SNAP_RADIUS; dx <= COLUMN_SNAP_RADIUS; dx++) {
            for (int dz = -COLUMN_SNAP_RADIUS; dz <= COLUMN_SNAP_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int ny = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz);
                double diff = Math.abs(ny - referenceY);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestX = x + dx;
                    bestZ = z + dz;
                    bestY = ny;
                }
            }
        }
        return new Vec3(bestX + 0.5, bestY, bestZ + 0.5);
    }

    /**
     * Локальний вертикальний пошук прохідної підлоги в стовпці (x,z): від referenceY по черзі
     * 0, +1, -1, +2, -2, ... в межах {@link #VERTICAL_SNAP_RANGE}. На відміну від heightmap, НЕ
     * залежить від того, що знаходиться вище по стовпцю (дах, рельєф над дахом тощо) — саме тому
     * коректно працює під стелею. Повертає null, якщо в межах діапазону нічого прохідного нема
     * (наприклад, суцільна скеля без порожнин).
     */
    private Vec3 findFloorNear(Level level, int x, int z, double referenceY) {
        int centerY = (int) Math.floor(referenceY);
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        for (int offset = 0; offset <= VERTICAL_SNAP_RANGE; offset++) {
            int steps = (offset == 0) ? 1 : 2;
            for (int i = 0; i < steps; i++) {
                int y = centerY + (i == 0 ? offset : -offset);
                if (y < minY + 1 || y > maxY - 2) {
                    continue;
                }
                BlockPos feet = new BlockPos(x, y, z);
                if (isStandable(level, feet)) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
            }
        }
        return null;
    }

    /**
     * Твердий блок під ногами (не порожня форма зіткнення) і прохідно на висоті ніг та голови.
     */
    private boolean isStandable(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos head = feet.above();
        boolean floorSolid = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
        boolean feetOpen = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
        boolean headOpen = level.getBlockState(head).getCollisionShape(level, head).isEmpty();
        return floorSolid && feetOpen && headOpen;
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