package com.example.examplemod.EnemyBehavior;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Глобальна "пам'ять про ціль" для всіх злих мобів (зомбі, скелети, пігліни, розбійники тощо).
 * <p>
 * Логіка (4 стани для пари моб-гравець):
 * <ol>
 *   <li><b>CHASING</b> — гравець знаходиться в межах ПОВНОГО {@code Attributes.FOLLOW_RANGE}
 *       моба. Незалежно від того, чи є пряма видимість (навіть за стіною) — моб переслідує
 *       ЖИВУ позицію гравця крізь стіни. Лук/арбалет можуть тягнутись (анімація натягу), але
 *       фактичний виліт снаряда лишається заблокованим звичайним canSee — за це відповідає
 *       {@link #isMemoryChasing(Mob)}, який треба перевіряти поряд з canSee в Goal-ах стрільби.</li>
 *   <li><b>GOING_TO_LAST_SEEN</b> — гравець вийшов за межі ПОВНОГО FOLLOW_RANGE. Моб іде до
 *       ЗАСТИГЛОЇ точки — там, де гравець був у момент виходу (позиція більше НЕ оновлюється
 *       живою позицією гравця).</li>
 *   <li><b>SEARCHING</b> — моб дійшов до застиглої точки і там нікого немає. ТІЛЬКИ для мобів
 *       без накопиченого заряду (скелет/stray/bogged, дроунд із тризубом — НЕ арбалетники, НЕ
 *       блейз/гаст): протягом {@link #SEARCH_DURATION_TICKS} (15с) моб "никається" навколо
 *       останньої точки — раз у кілька секунд обирає нову випадкову сусідню точку й іде туди,
 *       не відпускаючи натягнуту тетиву ({@link #shouldDrawBowstring(Mob)} лишається true).</li>
 *   <li><b>FORGOTTEN</b> — час пошуку вийшов без повторного бачення. Повне забуття:
 *       {@code mob.setTarget(null)}, контроль повертається звичайному AI.</li>
 * </ol>
 * У БУДЬ-ЯКОМУ з трьох "не-CHASING" станів — якщо моб реально побачить гравця (справжній
 * ванільний canSee) — миттєвий перехід назад в CHASING з живою позицією (тетива вже натягнута,
 * якщо була, тож постріл може статись одразу).
 * <p>
 * Перше "побачив хоч раз" лишається повністю ванільним — цю систему запускає лише той факт,
 * що {@code mob.getTarget()} вже є гравцем (а ціль гравцю ванільно встановлюється тільки коли
 * реальна видимість справді була).
 */
public class PursuitEnemyBehavior extends Goal {

    /**
     * Дистанція (у блоках), на якій моб ще йде до застиглої точки, але вже починає тягнути тетиву.
     */
    private static final double DRAW_BOWSTRING_DISTANCE_SQ = 64.0; // 8 блоків

    /**
     * Тривалість "никання" біля останньої точки, перш ніж остаточне забуття.
     */
    private static final int SEARCH_DURATION_TICKS = 15 * 20;

    /**
     * Як часто (в тіках) моб обирає нову випадкову точку під час пошуку.
     */
    private static final int SEARCH_REPICK_INTERVAL_TICKS = 40; // ~2 секунди

    /**
     * Радіус, у якому обираються випадкові точки навколо останньої відомої позиції гравця.
     */
    private static final double SEARCH_POINT_RADIUS = 5.0;
    /**
     * Стан пам'яті прив'язаний до конкретного моба (WeakHashMap — записи самі зникають,
     * коли моб деспавнився/вивантажився і на нього більше немає сильних посилань).
     */
    private static final Map<Mob, MemoryData> MEMORY = new WeakHashMap<>();
    /**
     * Зберігає sprintSpeedModifier для кожного моба — читається Goal-ами через getSprintSpeedModifier.
     */
    private static final Map<Mob, Double> SPRINT_SPEED = new WeakHashMap<>();
    private final Mob mob;
    private final boolean supportsSearchBehavior;
    /**
     * Множник швидкості для moveTo під час бігу до застиглої точки (GOING_TO_LAST_SEEN) або
     * пошуку (SEARCHING). Задається при реєстрації Goal-у в BetterEnemysBehavior окремо для
     * кожного моба. Зчитується стрілецькими/мелі Goal-ами через getSprintSpeedModifier(mob).
     */
    private final double sprintSpeedModifier;

    public PursuitEnemyBehavior(Mob mob) {
        this(mob, false, 1.4);
    }

    public PursuitEnemyBehavior(Mob mob, boolean supportsSearchBehavior) {
        this(mob, supportsSearchBehavior, 1.4);
    }

    public PursuitEnemyBehavior(Mob mob, boolean supportsSearchBehavior, double sprintSpeedModifier) {
        this.mob = mob;
        this.supportsSearchBehavior = supportsSearchBehavior;
        this.sprintSpeedModifier = sprintSpeedModifier;
        SPRINT_SPEED.put(mob, sprintSpeedModifier);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    /**
     * Множник швидкості для moveTo під час бігу — для використання в Goal-ах стрільби/мелі.
     * Повертає 1.0 якщо моб не має зареєстрованого PursuitEnemyBehavior.
     */
    public static double getSprintSpeedModifier(Mob mob) {
        return SPRINT_SPEED.getOrDefault(mob, 1.0);
    }

    /**
     * Чи зараз цей моб переслідує гравця "крізь стіни" по пам'яті (CHASING або
     * GOING_TO_LAST_SEEN). Виклич це з Goal-ів стрільби поряд з уже наявним canSee: якщо true
     * і canSee == false, лук/арбалет можна тримати натягнутим, але стріляти НЕЛЬЗЯ.
     */
    public static boolean isMemoryChasing(Mob mob) {
        MemoryData data = MEMORY.get(mob);
        return data != null && data.state != State.FORGOTTEN;
    }

    /**
     * Точка, до якої моб має рухатись цього тіку, ігноруючи відсутність прямої видимості:
     * жива позиція гравця (CHASING), застигла остання відома точка (GOING_TO_LAST_SEEN) або
     * поточна точка "никання" (SEARCHING). Повертає null, якщо система не активна — тоді рух
     * лишається повністю на звичайній (canSee-based) логіці конкретного Goal-у моба.
     */
    public static Vec3 getChasePosition(Mob mob) {
        MemoryData data = MEMORY.get(mob);
        if (data == null) {
            return null;
        }
        return switch (data.state) {
            case CHASING -> data.trackedPlayer != null ? data.trackedPlayer.position() : null;
            case GOING_TO_LAST_SEEN -> data.lastSeenPos;
            case SEARCHING -> data.searchPoint;
            case FORGOTTEN -> null;
        };
    }

    /**
     * Чи має моб тримати тетиву натягнутою НАВІТЬ БЕЗ canSee — для категорії мобів без
     * накопиченого заряду (скелет/stray/bogged, дроунд із тризубом). True, коли моб уже досить
     * близько до застиглої точки (GOING_TO_LAST_SEEN) або вже у фазі активного пошуку
     * (SEARCHING) — в обох випадках тетива не відпускається, поки не настане FORGOTTEN або не
     * з'явиться справжній canSee (тоді постріл відбувається одразу, бо натяг вже повний).
     */
    public static boolean shouldDrawBowstring(Mob mob) {
        MemoryData data = MEMORY.get(mob);
        if (data == null) {
            return false;
        }
        if (data.state == State.SEARCHING) {
            return true;
        }
        if (data.state == State.GOING_TO_LAST_SEEN && data.lastSeenPos != null) {
            return mob.distanceToSqr(data.lastSeenPos) <= DRAW_BOWSTRING_DISTANCE_SQ;
        }
        return false;
    }

    /**
     * ТИМЧАСОВИЙ DEBUG-ЛОГ: відправляє повідомлення в чат гравцю-цілі.
     * Видалити всі виклики debugMsg (і сам метод) після завершення тестування.
     */
    private static void debugMsg(Player player, String msg) {
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
    }

    @Override
    public boolean canUse() {
        return findCandidatePlayer() != null;
    }

    @Override
    public boolean canContinueToUse() {
        MemoryData data = MEMORY.get(this.mob);
        return (data != null && data.state != State.FORGOTTEN) || findCandidatePlayer() != null;
    }

    @Override
    public boolean isInterruptable() {
        return true; // не заважає вищим за пріоритетом Goal-ам (стрільбі/мелі) працювати одночасно
    }

    /**
     * Гравець, на якого варто звернути увагу цього тіку: або вже відстежуваний гравець (щоб не
     * втратити стан, навіть якщо ванільний targetSelector щойно скинув ціль), або поточна ціль
     * моба, якщо це гравець.
     */
    private Player findCandidatePlayer() {
        MemoryData data = MEMORY.get(this.mob);
        if (data != null && data.state == State.CHASING && data.trackedPlayer != null && data.trackedPlayer.isAlive()) {
            return data.trackedPlayer;
        }
        return (this.mob.getTarget() instanceof Player player) ? player : null;
    }

    @Override
    public void tick() {
        MemoryData data = MEMORY.computeIfAbsent(this.mob, m -> new MemoryData());

        // GOING_TO_LAST_SEEN і SEARCHING мають свою окрему логіку тіку.
        if (data.state == State.GOING_TO_LAST_SEEN) {
            tickGoingToLastSeen(data);
            // Спринт для мелі-мобів під час бігу до точки.
            if (!supportsSearchBehavior) {
                this.mob.setSprinting(true);
            }
            return;
        }
        if (data.state == State.SEARCHING) {
            tickSearching(data);
            return;
        }

        Player player = findCandidatePlayer();
        if (player == null) {
            if (!supportsSearchBehavior) {
                this.mob.setSprinting(false);
            }
            return;
        }

        data.trackedPlayer = player;

        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double distSq = this.mob.distanceToSqr(player);

        if (distSq <= followRange * followRange) {
            // У межах повного радіуса — переслідуємо живу позицію крізь стіни.
            data.state = State.CHASING;
            data.trackedPlayer = player;
            reinforceTarget(player);
            // Мелі-моби спринтують завжди коли заагрені (стан CHASING).
            // Для мобів зі стрільбою (supportsSearchBehavior) спринт керується їх власним Goal-ом.
            if (!supportsSearchBehavior) {
                this.mob.setSprinting(true);
            }
            // Оновлюємо lastVisiblePos ТІЛЬКИ коли моб реально бачить гравця (не через стіну).
            if (this.mob.getSensing().hasLineOfSight(player)) {
                data.lastVisiblePos = player.position();
            }
        } else if (data.state == State.CHASING) {
            // Вийшов за межі FOLLOW_RANGE — фіксуємо ОСТАННЮ ВИДИМУ позицію (не поточну!).
            Vec3 fixedPos = data.lastVisiblePos != null ? data.lastVisiblePos : player.position();
            data.state = State.GOING_TO_LAST_SEEN;
            data.lastSeenPos = fixedPos;
            data.trackedPlayer = player;
            debugMsg(player, String.format("[DEBUG] Доганяю ворога. Координати точки: %.1f %.1f %.1f",
                    fixedPos.x, fixedPos.y, fixedPos.z));
        }
    }

    /**
     * Поки моб іде до застиглої точки: повернення в CHASING можливе лише через СПРАВЖНЄ ванільне
     * бачення гравця (canSee). Якщо фізично дійшов до точки — або стартуємо SEARCHING (мобам,
     * що підтримують), або одразу FORGOTTEN.
     */
    private void tickGoingToLastSeen(MemoryData data) {
        if (data.lastSeenPos == null) {
            forgetInternal(data);
            return;
        }

        // Тримаємо target живим поки йдемо до точки.
        if (data.trackedPlayer != null && data.trackedPlayer.isAlive()) {
            reinforceTarget(data.trackedPlayer);
        }

        // Справжнє нове ванільне бачення — миттєво повертаємось в CHASING.
        if (tryResumeChasing(data)) {
            return;
        }

        // DEBUG: "готуюсь до вистрілу" — раз на секунду коли вже в зоні натягу тетиви (≤8 блоків).
        double distToPoint = Math.sqrt(this.mob.position().distanceToSqr(data.lastSeenPos));
        if (distToPoint <= Math.sqrt(DRAW_BOWSTRING_DISTANCE_SQ) && data.trackedPlayer != null
                && this.mob.tickCount % 20 == 0) {
            debugMsg(data.trackedPlayer, String.format(
                    "[DEBUG] Готуюсь до вистрілу. Відстань до точки: %.1f блоків", distToPoint));
        }

        double reachThresholdSq = 4.0; // ~2 блоки
        if (this.mob.position().distanceToSqr(data.lastSeenPos) <= reachThresholdSq) {
            if (supportsSearchBehavior) {
                startSearching(data);
            } else {
                forgetInternal(data);
            }
        }
    }

    /**
     * Моб "никається" навколо останньої відомої точки протягом SEARCH_DURATION_TICKS (15с):
     * раз на SEARCH_REPICK_INTERVAL_TICKS (~2с) обирає нову випадкову точку поблизу і йде до неї.
     * Тетива весь цей час натягнута (shouldDrawBowstring повертає true для SEARCHING).
     * Якщо canSee стає true — миттєво виходимо в CHASING і стріляємо (тетива вже повна).
     * Якщо час вийшов — FORGOTTEN.
     */
    private void tickSearching(MemoryData data) {
        if (tryResumeChasing(data)) {
            return;
        }

        if (data.trackedPlayer != null && data.trackedPlayer.isAlive()) {
            reinforceTarget(data.trackedPlayer);
        }

        data.searchTicksLeft--;
        if (data.searchTicksLeft <= 0) {
            forgetInternal(data);
            return;
        }

        // DEBUG: таймер пошуку — раз на секунду.
        if (data.searchTicksLeft % 20 == 0) {
            Player debugPlayer = (this.mob.getTarget() instanceof Player p) ? p
                    : (data.trackedPlayer != null ? data.trackedPlayer : null);
            if (debugPlayer != null) {
                int secsLeft = data.searchTicksLeft / 20;
                debugMsg(debugPlayer, "[DEBUG] Шукаю ціль. Залишилось: " + secsLeft + "с");
            }
        }

        data.searchRepickCooldown--;
        boolean needsNewPoint = data.searchRepickCooldown <= 0
                || data.searchPoint == null
                || hasReachedSearchPoint(data.searchPoint);

        if (needsNewPoint) {
            Vec3 newPoint = pickRandomSearchPoint(data.lastSeenPos);
            if (newPoint != null) {
                data.searchPoint = newPoint;
                // DEBUG: нова точка пошуку.
                Player debugPlayer = (this.mob.getTarget() instanceof Player p) ? p
                        : (data.trackedPlayer != null ? data.trackedPlayer : null);
                if (debugPlayer != null) {
                    debugMsg(debugPlayer, String.format("[DEBUG] Шукаю ціль. Координати вибраної точки: %.1f %.1f %.1f",
                            newPoint.x, newPoint.y, newPoint.z));
                }
            }
            data.searchRepickCooldown = SEARCH_REPICK_INTERVAL_TICKS;
        }
    }

    /**
     * Спільна перевірка "справжній canSee І в межах FOLLOW_RANGE → назад в CHASING".
     * Обидві умови обов'язкові: Sensing.hasLineOfSight() має власний кеш і може "бачити"
     * гравця далі за FOLLOW_RANGE — без перевірки дистанції виникає цикл
     * GOING_TO_LAST_SEEN → CHASING → GOING_TO_LAST_SEEN кожен тік.
     */
    private boolean tryResumeChasing(MemoryData data) {
        Player nearbyPlayer = (this.mob.getTarget() instanceof Player player) ? player
                : (data.trackedPlayer != null ? data.trackedPlayer : null);
        if (nearbyPlayer == null || !nearbyPlayer.isAlive()) {
            return false;
        }
        double followRange = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (this.mob.distanceToSqr(nearbyPlayer) > followRange * followRange) {
            return false; // за межею радіуса — Sensing може "бачити" далі, але нас це не цікавить
        }
        if (!this.mob.getSensing().hasLineOfSight(nearbyPlayer)) {
            return false;
        }
        data.state = State.CHASING;
        data.trackedPlayer = nearbyPlayer;
        data.lastSeenPos = null;
        data.searchPoint = null;
        reinforceTarget(nearbyPlayer);
        return true;
    }

    private void startSearching(MemoryData data) {
        // trackedPlayer НЕ обнуляємо — він потрібен для reinforceTarget під час пошуку,
        // щоб ванільний targetSelector не скинув mob.getTarget() раніше ніж 15с вийдуть.
        data.state = State.SEARCHING;
        data.searchTicksLeft = SEARCH_DURATION_TICKS;
        data.searchRepickCooldown = 0;
        data.searchPoint = pickRandomSearchPoint(data.lastSeenPos);
    }

    /**
     * Чи досяг моб поточної точки пошуку (порогова відстань ~2 блоки).
     */
    private boolean hasReachedSearchPoint(Vec3 point) {
        return this.mob.position().distanceToSqr(point) <= 4.0;
    }

    /**
     * Обирає випадкову точку поблизу {@code center} у радіусі {@link #SEARCH_POINT_RADIUS}.
     * Перевіряє, що точка реально досяжна для навігатора моба — якщо ні (прірва, закритий
     * простір), повертає null: тоді моб лишається на місці до наступного репіку.
     */
    private Vec3 pickRandomSearchPoint(Vec3 center) {
        if (center == null) {
            return null;
        }
        var random = this.mob.getRandom();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double radius = SEARCH_POINT_RADIUS * (0.4 + random.nextDouble() * 0.6);
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            // Шукаємо найближчий твердий блок по Y, щоб не класти точку в повітрі чи під землею.
            net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(x, center.y, z);
            // Перевіряємо, чи навігатор взагалі може побудувати шлях до цієї точки.
            var path = this.mob.getNavigation().createPath(blockPos, 1);
            if (path != null && path.canReach()) {
                return new Vec3(x, center.y, z);
            }
        }
        return null; // не знайшли досяжну точку за 8 спроб — повернемо null, тікаємо далі
    }

    /**
     * Примусово тримає mob.getTarget() == player, поки наш стан CHASING — це захищає від того,
     * що ванільний targetSelector (зі своїм коротшим unseenMemoryTicks) скине ціль занадто рано.
     */
    private void reinforceTarget(Player player) {
        if (this.mob.getTarget() != player) {
            this.mob.setTarget(player);
        }
    }

    private void forgetInternal(MemoryData data) {
        data.state = State.FORGOTTEN;
        data.trackedPlayer = null;
        data.lastVisiblePos = null;
        data.lastSeenPos = null;
        data.searchPoint = null;
        if (!supportsSearchBehavior) {
            this.mob.setSprinting(false);
        }
        if (this.mob.getTarget() instanceof Player) {
            this.mob.setTarget(null);
        }
    }

    private enum State {
        CHASING,
        GOING_TO_LAST_SEEN,
        SEARCHING,
        FORGOTTEN
    }

    private static final class MemoryData {
        State state = State.FORGOTTEN;
        Player trackedPlayer = null;
        /**
         * Остання позиція гравця де моб його РЕАЛЬНО БАЧИВ (canSee + в межах FOLLOW_RANGE).
         * Оновлюється щотіку під час CHASING тільки при hasLineOfSight. Саме ця точка стає
         * lastSeenPos при переході в GOING_TO_LAST_SEEN — а не поточна позиція гравця в момент
         * виходу за радіус (він може вже бути за стіною/далеко в той момент).
         */
        Vec3 lastVisiblePos = null;
        /**
         * Застигла точка куди моб іде після виходу гравця за FOLLOW_RANGE. Не оновлюється.
         */
        Vec3 lastSeenPos = null;
        /**
         * Поточна точка "никання" під час SEARCHING (оновлюється раз на SEARCH_REPICK_INTERVAL_TICKS).
         */
        Vec3 searchPoint = null;
        int searchTicksLeft = 0;
        int searchRepickCooldown = 0;
    }
}