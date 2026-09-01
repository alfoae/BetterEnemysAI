package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.Config;
import com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyPursuit_N_Search.PursuitEnemyBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * ФАЗА 2+3. Замінює {@link BuildPathGoal}'s наївний "піларь-впритул" саме для підйому до ЖИВОЇ
 * позиції гравця (GOING_TO_LAST_SEEN/SEARCHING і далі лишаються за {@link BuildPathGoal} —
 * навмисно, "як можна швидше, ігноруючи логіку зони").
 * <p>
 * Покроково (узгоджено з користувачем):
 * <ol>
 *   <li>{@link Stage#DECIDE} (і, опортуністично, кожен тік {@link Stage#CLIMBING} теж) — є
 *       суцільний 7×7 І моб уже НЕ нижче за його поверхню (інакше чиста навігація не дійде туди
 *       без готових сходинок — саме тут був баг "моб просто стоїть")? → {@link Stage#TO_7X7}.
 *       Інакше — шукає найближчий вихід за межу проекції ВІДНОСНО ГРАВЦЯ (не моба - інший
 *       знайдений живим тестом баг: інакше стовп міг початись за 12+ блоків, там де моб просто
 *       випадково зупинився), комітиться туди.</li>
 *   <li>{@link Stage#TO_7X7} → {@link Stage#CROSS_AND_DIG} — доходить до центру 7×7 (може
 *       перетнути зону — короткий захід, стіна тут НЕ будується), ставить хрест-підпору й
 *       ламає стелю (з тими самими лава-перевірками, що й нижче). Захищено таймаутом
 *       ({@link #TO_7X7_STUCK_TIMEOUT_TICKS}) — якщо навігація все ж не може дійти (не лише
 *       через висоту), не стоїмо вічно, повертаємось до звичайного підйому.</li>
 *   <li>{@link Stage#CLIMBING} — комітнута (x,z)-колонка: піларить вгору, на кожному НОВОМУ
 *       шарі заморожує напрямок до гравця й будує стіну (1 блок кардинально / 3 блоки в куток
 *       по діагоналі — картинка користувача про щілину хітбокса). Перед КОЖНИМ проломом стелі:
 *       лава над головою? → {@link Stage#SIDESTEP} в обхід (сусідня колонка без лави); стеля
 *       рівно 1 блок і далі повітря? → хрест-підпора (як в CROSS_AND_DIG) ПЕРЕД ломом. Якщо
 *       зона за час підйому виросла настільки, що поглинула колонку — теж {@link Stage#SIDESTEP}
 *       (інша причина, той самий механізм). Якщо піднялись вище даху зони —
 *       {@link Stage#BRIDGE_TO_PLAYER}.</li>
 *   <li>{@link Stage#BRIDGE_TO_PLAYER} — <b>СПРОЩЕННЯ:</b> звичайний міст до гравця (як
 *       {@code BuildPathGoal.handleBridge}). Повноцінний "обліт даху зони й посадка з
 *       пріоритетами" (12-блоків-правило, "не над прірвою" тощо) — ще НЕ реалізовано, це
 *       наступний прохід (Фаза 4).</li>
 * </ol>
 * Коли шлях перестає бути заблокованим (мобу вже недалеко) — {@link #canContinueToUse} сам
 * поверне false, і керування чисто (stop/start, не через прапорці) переходить до
 * {@code PursuitEnemyMeleeBehavior}, як і в {@link BuildPathGoal}.
 * <p>
 * НЕ реалізовано в цьому проході (свідомо відкладено, Фаза 4): "обліт+посадка з пріоритетами"
 * вище даху зони, нейтралізація гравецьких лави/вогню/води НА ШЛЯХУ до гравця (тут — лише лава,
 * яка сама заважає ЗВЕРХУ під час підйому), клатч-механіка при падінні.
 */
public class TowerClimbGoal extends Goal {

    private static final int ACTION_COOLDOWN_TICKS = 10; // той самий темп дій, що й у BuildPathGoal
    private static final double NAV_ARRIVE_DIST_SQ = 4.0;
    private static final int WALL_HEIGHT = 2; // "стінку, яка буде мобу по голову"
    // Наскільки менша складова (dx чи dz) має бути відносно більшої, щоб вважати напрямок
    // ДІАГОНАЛЬНИМ (і будувати кутовий блок), а не чисто кардинальним (1 блок). 0.5 = менша
    // складова хоча б половина більшої.
    private static final double DIAGONAL_RATIO_THRESHOLD = 0.5;
    private static final Direction[] CARDINALS =
            {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private static final int TO_7X7_STUCK_TIMEOUT_TICKS = 100; // 5с без прогресу - здаємось, лізем звичайним шляхом
    private static final double CLOSE_RANGE_DIST_SQ = 144.0; // "12 блоків" з опису користувача
    private static final double FALL_ZONE_DIST_SQ = 4.0; // ~2 блоки - тут вже просто падає, не будує
    private final Mob mob;
    private Stage stage = Stage.DECIDE;
    private BlockPos pillarColumn;   // закомітчена (x,z) колонка підйому (Y не використовується)
    private BlockPos sidestepTarget;
    private BlockPos target7x7;
    private int to7x7StuckTicks;
    private int layerY = Integer.MIN_VALUE; // Y, для якого вже заморожений напрямок стіни нижче
    private Direction wallDirA;
    private Direction wallDirB;      // != null лише для діагонального (кутового) випадку
    private int actionCooldown;

    public TowerClimbGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!EnemyBreak_N_BuildUtils.canOperate(this.mob)) return false;
        if (!PursuitEnemyBehavior.isLiveChasing(this.mob)) return false; // GTLS/SEARCHING - не сюди
        if (!(this.mob.level() instanceof ServerLevel level)) return false;
        if (PursuitEnemyBehavior.getTrackedPlayer(this.mob) == null) return false;

        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos == null) return false;
        if (!EnemyBreak_N_BuildUtils.isPathBlocked(this.mob, chasePos)) return false;

        BlockPos target = EnemyBreak_N_BuildUtils.findNearestOpenArea(this.mob, level, chasePos);
        boolean climb = EnemyBreak_N_BuildUtils.needsClimb(this.mob, target);
        if (this.mob.tickCount % 20 == 0) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] canUse(): needsClimb=" + climb);
        }
        return climb;
    }

    @Override
    public boolean canContinueToUse() {
        if (!EnemyBreak_N_BuildUtils.canOperate(this.mob)) return false;
        if (!PursuitEnemyBehavior.isLiveChasing(this.mob)) return false;
        if (PursuitEnemyBehavior.getTrackedPlayer(this.mob) == null) return false;
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (chasePos == null) return false;
        return EnemyBreak_N_BuildUtils.isPathBlocked(this.mob, chasePos);
    }

    @Override
    public void start() {
        this.stage = Stage.DECIDE;
        this.pillarColumn = null;
        this.sidestepTarget = null;
        this.target7x7 = null;
        this.to7x7StuckTicks = 0;
        this.layerY = Integer.MIN_VALUE;
        this.wallDirA = null;
        this.wallDirB = null;
        this.actionCooldown = 0;
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] START моб=" + this.mob.blockPosition());
    }

    // ================= DECIDE =================

    @Override
    public void stop() {
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] STOP stage=" + this.stage
                + " моб=" + this.mob.blockPosition());
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(this.mob.level() instanceof ServerLevel level)) return;
        Player player = PursuitEnemyBehavior.getTrackedPlayer(this.mob);
        if (player == null) return;

        TowerZoneData zone = TowerZoneData.updateForClimbingMob(this.mob, level);
        if (zone == null) return; // теоретично неможливо тут (player != null вище), про всяк випадок

        if (this.mob.tickCount % 20 == 0) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] tick stage=" + this.stage
                    + " моб=" + this.mob.blockPosition() + " roofY=" + zone.getRoofY());
        }

        if (this.stage == Stage.DECIDE) {
            decide(zone, player);
        }
        if (this.stage == Stage.TO_7X7) {
            tickTo7x7();
        }
        if (this.stage == Stage.CROSS_AND_DIG) {
            tickCrossAndDig(level);
        }
        if (this.stage == Stage.CLIMBING) {
            tickClimbing(level, player, zone);
        }
        if (this.stage == Stage.SIDESTEP) {
            tickSidestep(level, zone);
        }
        if (this.stage == Stage.BRIDGE_TO_PLAYER) {
            tickBridgeToPlayer(level, player, zone);
        }
    }

    private void decide(TowerZoneData zone, Player player) {
        if (tryEnter7x7IfReachable(zone)) return;

        // ВИПРАВЛЕНО (живий тест): раніше шукали вихід відносно ПОТОЧНОЇ позиції моба - якщо той
        // зупинився далеко (наприклад melee просто йшла, поки могла, і стала за 12+ блоків),
        // стовп починався саме там. "Проекція" з опису - це позиція БІЛЯ ГРАВЦЯ, тому шукаємо
        // відносно гравця; моб сам дійде туди через stepToward() у CLIMBING.
        BlockPos playerPos = player.getOnPos().above();
        BlockPos exit = findNearestPointOutsideFootprint(zone, playerPos);
        // Узгоджений фолбек: валідної точки старту нема (заблоковано з усіх боків) - будуємось
        // де стоїмо, а не софтлочимось без дій.
        commitPillar(exit != null ? exit : this.mob.blockPosition());
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] DECIDE -> CLIMBING, комітнуто "
                + this.pillarColumn);
        this.stage = Stage.CLIMBING;
    }

    /**
     * ВИПРАВЛЕНО (живий тест, "моб просто стоїть"): раніше перемикались на TO_7X7 БЕЗУМОВНО,
     * щойно 7x7 взагалі існує — навіть якщо моб ще внизу, далеко під площею. tickTo7x7() це
     * ЧИСТА навігація (nav.moveTo), без стрибків і без блоків — а звичайна навігація не вміє
     * прокласти шлях вгору без готових сходинок, тож моб просто стояв, намагаючись дійти туди,
     * куди фізично не може піднятись. Тепер: (а) перевіряємо реальну досяжність (моб уже НЕ
     * нижче за поверхню площі) перед перемиканням; (б) цей метод викликається не тільки з
     * DECIDE, а й з КОЖНОГО тіку {@link #tickClimbing} — тож щойно моб підніметься досить
     * високо посеред звичайного підйому, він одразу скористається шорткатом, а не долізе аж
     * до даху зони.
     */
    private boolean tryEnter7x7IfReachable(TowerZoneData zone) {
        BlockPos found7x7 = zone.getFull7x7Center();
        if (found7x7 == null) return false;
        if (this.mob.blockPosition().getY() < found7x7.getY()) return false; // ще занадто низько

        this.target7x7 = found7x7;
        this.stage = Stage.TO_7X7;
        this.to7x7StuckTicks = 0;
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] -> TO_7X7 " + found7x7);
        return true;
    }

    // ================= TO_7X7 / CROSS_AND_DIG =================

    private void commitPillar(BlockPos column) {
        this.pillarColumn = new BlockPos(column.getX(), this.mob.blockPosition().getY(), column.getZ());
        this.layerY = Integer.MIN_VALUE; // форсує заморозку напрямку стіни на найпершому шарі
    }

    /**
     * Ring-пошук (по Чебишеву) найближчої (x,z)-колонки, якої НЕМАЄ в буферній зоні —
     * "проекція" з опису: моб має стати ЗА її межами, перш ніж почати ставити перший блок.
     */
    private BlockPos findNearestPointOutsideFootprint(TowerZoneData zone, BlockPos from) {
        int maxRadius = Config.TOWER_ZONE_SCAN_RADIUS.get() + Config.TOWER_ZONE_REACH_CAP.get() + 4;
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // лише периметр кільця r
                    int x = from.getX() + dx;
                    int z = from.getZ() + dz;
                    if (!zone.isColumnInsideFootprint(x, z)) {
                        return new BlockPos(x, from.getY(), z);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Захист від того самого класу багів, що й вище: якщо ЧОМУСЬ (не лише через висоту -
     * наприклад перепону, яку не вміє обійти vanilla-навігація) tickTo7x7 не може дійти,
     * не стоїмо тут вічно - за {@link #TO_7X7_STUCK_TIMEOUT_TICKS} відмовляємось від 7x7 для
     * цього заходу й повертаємось до звичайного підйому.
     */
    private void tickTo7x7() {
        BlockPos mobPos = this.mob.blockPosition();
        if (mobPos.distSqr(this.target7x7) <= NAV_ARRIVE_DIST_SQ) {
            this.stage = Stage.CROSS_AND_DIG;
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] TO_7X7 -> CROSS_AND_DIG "
                    + this.target7x7);
            return;
        }

        if (++this.to7x7StuckTicks > TO_7X7_STUCK_TIMEOUT_TICKS) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] TO_7X7 застряг - відмовляюсь, "
                    + "комітюсь на місці й лізу звичайним шляхом");
            commitPillar(this.mob.blockPosition());
            this.stage = Stage.CLIMBING;
            return;
        }

        this.mob.getNavigation().moveTo(
                this.target7x7.getX() + 0.5, this.target7x7.getY(), this.target7x7.getZ() + 0.5, 1.0D);
    }

    /**
     * Хрест + "паркан" по краях (по 1 блоку в кожен бік на рівні ніг, і ще по 1 зверху кожного з
     * них) — щоб коли моб проб'є стелю над центром, нокбек не міг зіштовхнути його вбік у
     * повітря: з усіх 4 боків на висоті голови вже стоїть блок.
     */
    private void tickCrossAndDig(ServerLevel level) {
        if (--this.actionCooldown > 0) return;
        this.actionCooldown = ACTION_COOLDOWN_TICKS;

        BlockPos center = this.mob.blockPosition();
        if (buildCrossSupportStep(level, center)) return; // один блок за раз - ще не все готово

        BlockPos overhead = center.above(2);
        if (EnemyBreak_N_BuildUtils.isBreakable(level, overhead)) {
            breakCeilingWithLavaCover(level, overhead);
            return;
        }

        if (this.mob.onGround()) {
            this.mob.getJumpControl().jump();
        }
        // Досягли гравця - canContinueToUse() сам поверне false (isPathBlocked стане false).
    }

    /**
     * Хрест + "паркан" по краях (по 1 блоку в кожен бік НА РІВНІ ПІДЛОГИ під мобом, і ще по 1
     * зверху кожного з них, уже на рівні ніг моба) — щоб коли моб проб'є стелю над центром,
     * нокбек не міг зіштовхнути його вбік у повітря: з усіх 4 боків на висоті тіла вже стоїть
     * блок. Ставить РІВНО ОДИН відсутній блок за виклик і повертає true (ще не готово) — false,
     * коли хрест уже повністю зібраний.
     * <p>
     * ВИПРАВЛЕНО (живий тест, "блоки в повітрі"): руки раніше ставились на висоті НІГ моба, не
     * підлоги — тобто рівно поряд із власним "повітряним" простором моба, без жодної реальної
     * грані-сусіда, до якої можна приліпитись (мобове тіло не solid). "блок на якому стоїть" з
     * опису буквально означає підлогу ({@code center.below()}), не самого моба.
     */
    private boolean buildCrossSupportStep(ServerLevel level, BlockPos center) {
        BlockPos floor = center.below(); // блок, на якому моб СТОЇТЬ
        for (Direction dir : CARDINALS) {
            BlockPos armFloor = floor.relative(dir);
            if (!level.getBlockState(armFloor).isSolid()) {
                EnemyBreak_N_BuildUtils.placeBlock(level, armFloor, this.mob);
                return true;
            }
            BlockPos armParapet = armFloor.above(); // тепер на висоті ніг моба - правильна висота "паркану"
            if (!level.getBlockState(armParapet).isSolid()) {
                EnemyBreak_N_BuildUtils.placeBlock(level, armParapet, this.mob);
                return true;
            }
        }
        return false;
    }

    /**
     * Ламає стелю, а якщо над нею сиділа лава — одразу перекриває (перевірено ДО лому, поки
     * ще відомо, що там було). Той самий фолбек з опису: "не зважаючи на те що має обходити,
     * все ж натикається" — коли обійти вже нема з чого (використовується і для 7×7, і як
     * останній fallback у звичайному підйомі, коли обхід не знайшов вільної сусідньої колонки).
     */
    private void breakCeilingWithLavaCover(ServerLevel level, BlockPos overhead) {
        boolean lavaAbove = isLavaAbove(level, overhead);
        EnemyBreak_N_BuildUtils.breakBlock(level, overhead, this.mob);
        if (lavaAbove) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] лава над проломом - перекриваю");
            EnemyBreak_N_BuildUtils.placeBlock(level, overhead.above(), this.mob);
        }
    }

    /**
     * "якщо товщина потолка 1 блок і далі повітря" — саме цей випадок (не товща стеля) означає,
     * що пролом одразу виставить моба у відкрите повітря, де нокбек може скинути його вбік.
     */
    private boolean ceilingIsThinWithAirBeyond(ServerLevel level, BlockPos overhead) {
        return !level.getBlockState(overhead.above()).isSolid();
    }

    private boolean isLavaAbove(ServerLevel level, BlockPos overhead) {
        return level.getFluidState(overhead.above()).is(FluidTags.LAVA);
    }

    /**
     * "нехай він перевіряє чи є над ним лава якщо так то нехай обходить її" — для звичайного
     * підйому (на відміну від CROSS_AND_DIG, де обходити нема з чого — це вже єдиний знайдений
     * 7x7) обхід МОЖЛИВИЙ: колонка не закомітчена намертво до конкретної точки простору, тож
     * шукаємо серед 4 сусідніх (та сама висота), у якої над відповідною стелею лави немає.
     */
    private BlockPos findLavaFreeAdjacentColumn(ServerLevel level, BlockPos center, int overheadY) {
        for (Direction dir : CARDINALS) {
            BlockPos candidate = center.relative(dir);
            BlockPos candidateOverhead = new BlockPos(candidate.getX(), overheadY, candidate.getZ());
            if (!level.getFluidState(candidateOverhead.above()).is(FluidTags.LAVA)) {
                return candidate;
            }
        }
        return null; // з усіх боків лава - нема куди обійти, викликач сам впаде на фолбек-перекриття
    }

    // ================= CLIMBING / SIDESTEP =================

    /**
     * true — вдалось знайти й почати обхід (стан уже CLIMBING -> SIDESTEP), викликач просто
     * виходить із тіку. false — обходити нема куди, викликач сам ламає й перекриває (фолбек).
     */
    private boolean tryAvoidLavaBySidestep(ServerLevel level, BlockPos overhead) {
        BlockPos altColumn = findLavaFreeAdjacentColumn(level, this.mob.blockPosition(), overhead.getY());
        if (altColumn == null) return false;
        this.sidestepTarget = altColumn;
        this.stage = Stage.SIDESTEP;
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] лава над стелею - обхожу через " + altColumn);
        return true;
    }

    private void tickClimbing(ServerLevel level, Player player, TowerZoneData zone) {
        ensureFloorUnderneathWhileAirborne(level);

        if (tryEnter7x7IfReachable(zone)) return; // піднялись досить високо - скористаємось шорткатом

        if (zone.isColumnInsideFootprint(this.pillarColumn.getX(), this.pillarColumn.getZ())) {
            beginSidestep(zone);
            if (this.stage != Stage.CLIMBING) return; // дійсно перейшли в SIDESTEP
            // інакше (фолбек - нема куди виходити) - просто ігноруємо цей ріст і йдемо далі нижче
        }

        boolean roofKnown = zone.getRoofY() != Integer.MIN_VALUE; // захист від того самого сентинел-багу
        boolean aboveRoof = roofKnown && this.mob.blockPosition().getY() >= zone.getRoofY();
        // "якщо моб на границі висоти то він просто йде прямо до ближайшої площі без того щоб
        // простроїтися више" - лишаємо запас в WALL_HEIGHT+1, бо на цьому ж кроці ще треба буде
        // місце під стіну поверх підлоги.
        boolean atWorldHeightLimit = this.mob.blockPosition().getY() >= level.getMaxBuildHeight() - (WALL_HEIGHT + 1);
        if (aboveRoof || atWorldHeightLimit) {
            this.stage = Stage.BRIDGE_TO_PLAYER;
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] CLIMBING -> BRIDGE_TO_PLAYER "
                    + (atWorldHeightLimit ? "(межа висоти світу) " : "(вище даху зони) ")
                    + "Y=" + this.mob.blockPosition().getY() + " roofY=" + zone.getRoofY());
            return;
        }

        if (!stepToward(level, this.pillarColumn)) return;

        if (--this.actionCooldown > 0) return;
        this.actionCooldown = ACTION_COOLDOWN_TICKS;

        if (this.mob.blockPosition().getY() != this.layerY) {
            lockWallDirectionForNewLayer(player);
        }

        BlockPos overhead = this.mob.blockPosition().above(2);
        if (EnemyBreak_N_BuildUtils.isBreakable(level, overhead)) {
            if (isLavaAbove(level, overhead) && tryAvoidLavaBySidestep(level, overhead)) {
                return; // пішли в обхід - до лому цього конкретного блоку тут не дійшло
            }
            if (ceilingIsThinWithAirBeyond(level, overhead) && buildCrossSupportStep(level, this.mob.blockPosition())) {
                return; // хрест ще не добудований - продовжимо наступного кулдауну
            }
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] climb: ламаю стелю " + overhead);
            breakCeilingWithLavaCover(level, overhead);
            return;
        }

        buildWallForCurrentLayer(level);

        if (this.mob.onGround()) {
            this.mob.getJumpControl().jump();
        }
    }

    private void beginSidestep(TowerZoneData zone) {
        BlockPos exit = findNearestPointOutsideFootprint(zone, this.pillarColumn);
        if (exit == null) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] зона поглинула колонку, "
                    + "вийти нема куди - ігнорую й продовжую підйом як є (фолбек)");
            return; // лишаємось у CLIMBING без реакції на цей конкретний ріст
        }
        this.sidestepTarget = exit;
        this.stage = Stage.SIDESTEP;
        EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] CLIMBING -> SIDESTEP до " + exit);
    }

    private void tickSidestep(ServerLevel level, TowerZoneData zone) {
        ensureFloorUnderneathWhileAirborne(level);

        BlockPos mobPos = this.mob.blockPosition();
        // ВИПРАВЛЕНО (живий тест): раніше завершували сайдстеп щойно мобу опинявся в межах
        // generic-порогу відстані від sidestepTarget - а сусідня клітинка ЗАВЖДИ в цих межах від
        // старту, тож перевірка проходила МИТТЄВО, без жодного реального кроку. Моб комітився
        // туди ж, звідки щойно "вийшов", наступний тік знову бачив себе в зоні - нескінченний
        // цикл (звідси й "ставить блок собі і стрибає на наступний": насправді ніколи не рухався
        // вгору, тільки туди-сюди по одній і тій самій клітинці). Тепер перевіряємо РЕАЛЬНИЙ
        // геометричний факт - чи колонка, де моб ЗАРАЗ стоїть, дійсно поза footprint-ом.
        if (!zone.isColumnInsideFootprint(mobPos.getX(), mobPos.getZ())) {
            commitPillar(mobPos);
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] SIDESTEP -> CLIMBING, вийшли з "
                    + "проекції, нова колонка " + this.pillarColumn);
            this.stage = Stage.CLIMBING;
            return;
        }
        stepToward(level, this.sidestepTarget); // рух далі - результат тут не вирішальний, див. вище
    }

    /**
     * Фіксує напрямок(и) стіни РІВНО РАЗ на новий шар (Y змінився відносно {@link #layerY}) —
     * навмисно НЕ перечитує позицію гравця щотіку всередині того самого шару, щоб зигзаг-стовп
     * гравця не смикав моба між кутовим і кардинальним варіантом посеред одного й того ж рівня.
     */
    private void lockWallDirectionForNewLayer(Player player) {
        this.layerY = this.mob.blockPosition().getY();

        double dx = player.getX() - this.mob.getX();
        double dz = player.getZ() - this.mob.getZ();
        double absDx = Math.abs(dx);
        double absDz = Math.abs(dz);
        Direction ew = dx >= 0 ? Direction.EAST : Direction.WEST;
        Direction ns = dz >= 0 ? Direction.SOUTH : Direction.NORTH;

        if (absDx < 1.0e-6 && absDz < 1.0e-6) {
            this.wallDirA = ew; // гравець майже точно над мобом - довільний вибір, аби не ділити на 0
            this.wallDirB = null;
            return;
        }

        double bigger = Math.max(absDx, absDz);
        double smaller = Math.min(absDx, absDz);
        if (smaller >= bigger * DIAGONAL_RATIO_THRESHOLD) {
            this.wallDirA = ns;
            this.wallDirB = ew; // діагональ - додається ще й кутовий блок нижче
        } else if (absDx >= absDz) {
            this.wallDirA = ew;
            this.wallDirB = null;
        } else {
            this.wallDirA = ns;
            this.wallDirB = null;
        }
    }

    /**
     * 1 блок з боку гравця (кардинальний випадок) або 3 блоки (обидва кардинальні + сам кутовий)
     * для діагоналі — без кутового блока два кардинальні блоки дотикаються лише по діагоналі й
     * лишають щілину, крізь яку може "виглядати" ребро хітбокса моба.
     */
    private void buildWallForCurrentLayer(ServerLevel level) {
        BlockPos feet = this.mob.blockPosition();
        placeWallColumn(level, feet.relative(this.wallDirA));
        if (this.wallDirB != null) {
            placeWallColumn(level, feet.relative(this.wallDirB));
            placeWallColumn(level, feet.relative(this.wallDirA).relative(this.wallDirB));
        }
    }

    // ================= BRIDGE_TO_PLAYER =================

    /**
     * ВИПРАВЛЕНО (живий тест, "блоки в повітрі"): стіна стирчить убік від вузького 1-блочного
     * стовпа — сама по собі, без жодного розширення підлоги під собою, вона НЕ дотикається
     * гранню жодного вже існуючого блока (сусід "до моба" — власний повітряний простір моба, не
     * solid). Так само, як довелось би гравцю: спершу продовжуємо підлогу вбік
     * ({@code base.below()}, впритул до підлоги моба), і лише ПОТІМ будуємо стіну на ній.
     */
    private void placeWallColumn(ServerLevel level, BlockPos base) {
        BlockPos floorExtension = base.below();
        if (!level.getBlockState(floorExtension).isSolid()) {
            EnemyBreak_N_BuildUtils.placeBlock(level, floorExtension, this.mob);
        }
        for (int dy = 0; dy < WALL_HEIGHT; dy++) {
            BlockPos pos = base.above(dy);
            if (!level.getBlockState(pos).isSolid()) {
                EnemyBreak_N_BuildUtils.placeBlock(level, pos, this.mob);
            }
        }
    }

    /**
     * "0 - до гравця, 1 - до безопасної площі, 2 - просто до площі" — обирає куди летіти/падати.
     * Пріоритет 0 (сам гравець) теж перевіряється на "не скраю", бо мета цих пріоритетів саме
     * ЦЕ: не націлюватись точно на клітинку над відкритим падінням.
     */
    private BlockPos chooseLandingTarget(Player player, TowerZoneData zone) {
        BlockPos playerPos = player.getOnPos().above();
        if (zone.isSafeLandingTile(playerPos.getX(), playerPos.getZ())) {
            return playerPos;
        }
        BlockPos safe = zone.findSafeLandingTile(this.mob.blockPosition());
        if (safe != null) return safe;

        BlockPos any = zone.findAnyLandingTile(this.mob.blockPosition());
        if (any != null) return any;

        return playerPos; // площа порожня (не мало б траплятись) - фолбек на самого гравця
    }

    private void tickBridgeToPlayer(ServerLevel level, Player player, TowerZoneData zone) {
        BlockPos landingTarget = chooseLandingTarget(player, zone);
        BlockPos mobPos = this.mob.blockPosition();
        double dx = landingTarget.getX() - mobPos.getX();
        double dz = landingTarget.getZ() - mobPos.getZ();
        double horizontalDistSq = dx * dx + dz * dz;

        this.mob.getNavigation().moveTo(landingTarget.getX() + 0.5, landingTarget.getY(), landingTarget.getZ() + 0.5, 1.0D);

        if (horizontalDistSq <= FALL_ZONE_DIST_SQ) {
            // "просто падає на цей блок/гравця" - вже прямо над ціллю, опору більше НЕ підкладаємо.
            return;
        }

        ensureFloorUnderneathWhileAirborne(level);

        // >12 блоків і ще не близько по горизонталі - летимо мостом на висоті даху зони (безпечно
        // над будь-якою частиною площі, бо зона не сягає вище roofY), а не одразу вниз до гравця;
        // ≤12 - одразу цілимось на реальну висоту цілі (короткий відрізок, обходити нема сенсу).
        boolean closeRange = horizontalDistSq <= CLOSE_RANGE_DIST_SQ;
        int travelY = closeRange ? landingTarget.getY() : Math.max(mobPos.getY(), zone.getRoofY());
        BlockPos travelTarget = new BlockPos(landingTarget.getX(), travelY, landingTarget.getZ());

        if (--this.actionCooldown > 0) return;
        this.actionCooldown = ACTION_COOLDOWN_TICKS;

        BlockPos step = EnemyBreak_N_BuildUtils.nextHorizontalStep(this.mob, travelTarget);
        neutralizeHazardIfPlayerPlaced(level, step);
        neutralizeHazardIfPlayerPlaced(level, step.above());
        BlockPos stepBelow = step.below();
        if (!level.getBlockState(stepBelow).isSolid()) {
            EnemyBreak_N_BuildUtils.placeBlock(level, stepBelow, this.mob);
        }
    }

    /**
     * ФІКС, той самий що й у BuildPathGoal: підкладає підлогу під ноги КОЖЕН тік, поки моб у
     * повітрі - коротке вікно, 10-тіковий кулдаун легко його пропускає повністю.
     */
    private void ensureFloorUnderneathWhileAirborne(ServerLevel level) {
        if (!this.mob.onGround()) {
            BlockPos below = this.mob.blockPosition().below();
            if (!level.getBlockState(below).isSolid()) {
                EnemyBreak_N_BuildUtils.placeBlock(level, below, this.mob);
            }
        }
    }

    // ================= спільні дрібні хелпери =================

    /**
     * Один крок вбік до цільової (x,z)-колонки (Y цілі ігнорується — йдемо на поточній висоті
     * моба): якщо під наступним кроком порожньо, підкладає опору (як
     * {@code BuildPathGoal.handleBridge}), інакше просто веде туди навігацією. true, коли вже
     * практично на місці по X/Z.
     */
    private boolean stepToward(ServerLevel level, BlockPos targetColumn) {
        BlockPos mobPos = this.mob.blockPosition();
        double dx = targetColumn.getX() - mobPos.getX();
        double dz = targetColumn.getZ() - mobPos.getZ();
        if (dx * dx + dz * dz <= NAV_ARRIVE_DIST_SQ) return true;

        this.mob.getNavigation().moveTo(targetColumn.getX() + 0.5, mobPos.getY(), targetColumn.getZ() + 0.5, 1.0D);

        BlockPos step = EnemyBreak_N_BuildUtils.nextHorizontalStep(this.mob, targetColumn);
        neutralizeHazardIfPlayerPlaced(level, step);
        neutralizeHazardIfPlayerPlaced(level, step.above());
        BlockPos stepBelow = step.below();
        if (!level.getBlockState(stepBelow).isSolid()) {
            EnemyBreak_N_BuildUtils.placeBlock(level, stepBelow, this.mob);
        }
        return false;
    }

    /**
     * "!!ТІЛЬКИ КОЛИ ВОНА НА ПУТИ ЗОМБИ!!" — перевіряє й за потреби нейтралізує (перекриває
     * блоком) гравецький хазард САМЕ на позиції наступного кроку, не будь-де поруч.
     * <p>
     * СПРОЩЕНО відносно опису: правило "3+ джерел підряд — обійти замість перекривати" в цьому
     * проході не реалізовано (порахувати "скільки джерел підряд на шляху" і "чи є обхід" —
     * окрема, доволі велика підзадача) — зараз завжди перекриває, незалежно від кількості.
     */
    private void neutralizeHazardIfPlayerPlaced(ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (!PlacedHazardRegistry.isPlayerPlaced(level, pos, now)) return;

        boolean isFire = level.getBlockState(pos).is(Blocks.FIRE);
        boolean isHazardFluidSource = level.getFluidState(pos).isSource()
                && (level.getFluidState(pos).is(FluidTags.LAVA) || level.getFluidState(pos).is(FluidTags.WATER));

        if (isFire || isHazardFluidSource) {
            EnemyBreak_N_BuildUtils.debugMsg(this.mob, "[DEBUG TowerClimbGoal] гравецький хазард на шляху - "
                    + "перекриваю " + pos);
            EnemyBreak_N_BuildUtils.placeBlock(level, pos, this.mob);
        }
    }

    private enum Stage {DECIDE, TO_7X7, CROSS_AND_DIG, CLIMBING, SIDESTEP, BRIDGE_TO_PLAYER}
}
