package com.example.examplemod.utils;

import com.example.examplemod.EnemyBehavior.BetterEnemysBehavior;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Перевірка "чи не зачепить майбутній снаряд союзника по фракції" (фракції — у
 * {@link BetterEnemysBehavior}).
 * <p>
 * ПІДХІД (переписано з 0): снаряд — нескінченно тонкий промінь від точки вильоту до точки
 * прицілу. Товщина самого снаряда враховується НЕ геометрією навколо лінії (без тунелів,
 * без локальних базисів right/up, без перевірки вершин боксу) — замість цього хітбокс
 * СОЮЗНИКА просто розширюється (`AABB#inflate`) на projectileRadius в усі сторони, і по
 * ньому робиться звичайний raycast (`AABB#clip`). Розширений на half-ребро бокс цілі —
 * це і є врахування "половини ширини стріли" в кожен бік (вниз/вліво/вправо/вверх), просто
 * перенесене з геометрії снаряда на геометрію цілі: математично еквівалентно тунелю, але
 * без купи окремих перевірок (вершини/найближча точка/кутові лінії), які раніше давали баги.
 */
public class ProjectileTrajectory {

    /**
     * Чи вільний шлях від startPos до aimPoint від союзників.
     * <p>
     * aimPoint — РЕАЛЬНА точка, куди летітиме снаряд (вже з випередженням, з AdvancedAimMath),
     * а не поточна позиція цілі.
     *
     * @param shooter          моб, що стріляє
     * @param startPos         точка вильоту снаряда
     * @param aimPoint         точка прицілу з випередженням
     * @param projectileRadius половина ширини хітбокса снаряда (напр. 0.25 для стріли, 0.5 для фаєрбола)
     * @return true — можна стріляти, false — на шляху союзник
     */
    public static boolean isPathClear(Mob shooter, Vec3 startPos, Vec3 aimPoint, double projectileRadius) {
        if (startPos.distanceToSqr(aimPoint) < 1.0e-6) {
            return true; // стрілець і точка прицілу співпадають — нічого перевіряти
        }

        // Спершу дізнаємось, де лінія впирається в терен (якщо впирається) — далі цієї точки
        // снаряд фізично не долетить, тож союзника ЗА перешкодою перевіряти вже не треба.
        Vec3 effectiveEnd = aimPoint;
        BlockHitResult terrainHit = shooter.level().clip(new ClipContext(
                startPos, aimPoint,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                shooter
        ));
        if (terrainHit.getType() != HitResult.Type.MISS) {
            effectiveEnd = terrainHit.getLocation();
        }

        // Невеликий запас лише для відбору кандидатів з world (щоб не ганяти перевірку по всіх entity).
        AABB broadPhase = new AABB(startPos, effectiveEnd).inflate(projectileRadius + 0.5);

        Entity nearestBlocker = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Entity entity : shooter.level().getEntities(shooter, broadPhase)) {
            if (!(entity instanceof LivingEntity living) || entity == shooter) {
                continue;
            }
            if (!BetterEnemysBehavior.isSameFaction(shooter, living)) {
                continue; // чужі не заважають
            }

            AABB inflatedBox = living.getBoundingBox().inflate(projectileRadius);
            Optional<Vec3> hit = inflatedBox.clip(startPos, effectiveEnd);
            if (hit.isPresent()) {
                double distSq = startPos.distanceToSqr(hit.get());
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearestBlocker = living;
                }
            }
        }

        return nearestBlocker == null; // якщо знайшли найближчого союзника на шляху до перешкоди/цілі — шлях не вільний
    }

    /**
     * Перевантаження: aimPoint будується з результату AdvancedAimMath (AimResult зберігає
     * dX/dY/dZ — зсуви ВІД shooter.getEyePosition()).
     */
    public static boolean isPathClear(Mob shooter, AdvancedAimMath.AimResult aim, double projectileRadius) {
        Vec3 start = shooter.getEyePosition();
        Vec3 aimPoint = start.add(aim.dX(), aim.dY(), aim.dZ());
        return isPathClear(shooter, start, aimPoint, projectileRadius);
    }

    /**
     * Спрощений варіант: пряма лінія до ПОТОЧНОЇ позиції цілі, без випередження.
     * Лишається для мобів без прогнозування руху цілі.
     */
    public static boolean isPathClear(Mob shooter, LivingEntity target, double projectileRadius) {
        return isPathClear(shooter, shooter.getEyePosition(), target.getEyePosition(), projectileRadius);
    }

    private static final int MAX_MISS_RETRIES = 8;

    /**
     * Повна послідовність "чи і куди стріляти" з урахуванням похибки промаху:
     * <ol>
     *   <li>Рахуємо ТОЧНИЙ приціл (без похибки) і перевіряємо шлях. Якщо союзник заважає
     *       навіть ідеальному вистрілу — null, похибка навіть не рахується.</li>
     *   <li>Якщо точний шлях вільний — кидаємо шанс похибки. Без похибки: стріляємо точно.</li>
     *   <li>З похибкою: перевіряємо шлях зміщеної точки. Якщо заблоковано — перераховуємо
     *       НОВУ похибку і перевіряємо знову, до {@link #MAX_MISS_RETRIES} спроб. Якщо жодна
     *       не дала чистого шляху — стріляємо точним прицілом (вже підтверджений вільним).</li>
     * </ol>
     */
    public static AdvancedAimMath.AimResult resolveAimWithMissCheck(Mob shooter, LivingEntity target,
                                                                    float baseProjectileSpeed, Vec3 targetVel,
                                                                    double projectileRadius) {
        AdvancedAimMath.AimResult precise = AdvancedAimMath.calculatePreciseAim(shooter, target, baseProjectileSpeed, targetVel);

        if (!isPathClear(shooter, precise, projectileRadius)) {
            return null;
        }

        AdvancedAimMath.AimResult withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);

        if (withMiss == precise) {
            return precise; // похибка не спрацювала
        }

        for (int attempt = 0; attempt < MAX_MISS_RETRIES; attempt++) {
            if (isPathClear(shooter, withMiss, projectileRadius)) {
                return withMiss;
            }
            withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);
        }

        return precise; // жодна спроба похибки не дала чистого шляху — відступаємо до точного
    }

    /**
     * Аналог {@link #resolveAimWithMissCheck}, але з БАЛІСТИЧНОЮ (по тіках, дуговою) перевіркою
     * шляху замість прямої лінії — точніше для скелета/дроунда, де снаряд летить параболою
     * через гравітацію.
     * <p>
     * Звичайні стріли Minecraft НЕ inflate-ять хітбокс цілі при колізії (на відміну від
     * ThrowableProjectile) — тож тут projectileRadius — це не "товщина стріли", а лише малий
     * буфер безпеки (рекомендовано ~0.0-0.05) проти похибок дискретизації по тіках.
     */
    public static AdvancedAimMath.AimResult resolveBallisticAimWithMissCheck(Mob shooter, LivingEntity target,
                                                                             float baseProjectileSpeed, Vec3 targetVel,
                                                                             double projectileRadius) {
        AdvancedAimMath.AimResult precise = AdvancedAimMath.calculatePreciseAim(shooter, target, baseProjectileSpeed, targetVel);
        Vec3 start = shooter.getEyePosition();

        if (!isPathClearBallistic(shooter, start, start.add(precise.dX(), precise.dY(), precise.dZ()),
                precise.velocity(), projectileRadius)) {
            return null;
        }

        AdvancedAimMath.AimResult withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);

        if (withMiss == precise) {
            return precise;
        }

        for (int attempt = 0; attempt < MAX_MISS_RETRIES; attempt++) {
            Vec3 missAimPoint = start.add(withMiss.dX(), withMiss.dY(), withMiss.dZ());
            if (isPathClearBallistic(shooter, start, missAimPoint, withMiss.velocity(), projectileRadius)) {
                return withMiss;
            }
            withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);
        }

        return precise;
    }

    /**
     * Точніша перевірка для балістичних снарядів: симулює РЕАЛЬНУ тікову траєкторію стріли
     * (drag=0.99 щотіку, потім gravity=0.05 від вертикальної компоненти — так само, як рахує
     * сам Minecraft для AbstractArrow), а не пряму лінію до aimPoint. Будує дугу як ламану з
     * тікових сегментів і перевіряє КОЖЕН сегмент тим самим raycast'ом проти розширеного
     * (inflate на projectileRadius) хітбокса союзника, що й {@link #isPathClear}.
     *
     * @param shooter          моб, що стріляє
     * @param startPos         точка вильоту (очі стрільця)
     * @param aimPoint         точка прицілу (з gravityDrop від AdvancedAimMath)
     * @param projectileSpeed  швидкість снаряда
     * @param projectileRadius половина ширини хітбокса снаряда
     * @return true, якщо ВСЯ дуга вільна від союзників і не перекрита терном
     */
    public static boolean isPathClearBallistic(Mob shooter, Vec3 startPos, Vec3 aimPoint,
                                               float projectileSpeed, double projectileRadius) {
        Vec3 dir = aimPoint.subtract(startPos);
        double length = dir.length();
        if (length < 1.0e-6) {
            return true;
        }
        Vec3 dirNorm = dir.scale(1.0 / length);

        double vx = dirNorm.x * projectileSpeed;
        double vy = dirNorm.y * projectileSpeed;
        double vz = dirNorm.z * projectileSpeed;

        final double drag = 0.99;
        final double gravity = 0.05;

        double x = startPos.x, y = startPos.y, z = startPos.z;

        // Тікова симуляція, поки не пролетимо очікувану довжину шляху (+невеликий запас),
        // або поки не вийдемо за межу тіків (захист від нескінченного циклу при майже
        // вертикальному польоті, де горизонтальна довжина ніколи не "доходить").
        final int maxTicks = 200;
        final int microSteps = 4; // підкроки всередині тіку — ловлять різкі стрибки дуги між тіками
        double traveled = 0;
        final double targetTravelApprox = length * 1.05;

        for (int tick = 0; tick < maxTicks && traveled < targetTravelApprox; tick++) {
            double prevX = x, prevY = y, prevZ = z;

            x += vx;
            y += vy;
            z += vz;
            vx *= drag;
            vy *= drag;
            vz *= drag;
            vy -= gravity;

            Vec3 microPrev = new Vec3(prevX, prevY, prevZ);
            for (int step = 1; step <= microSteps; step++) {
                double t = (double) step / microSteps;
                Vec3 microCurrent = new Vec3(
                        prevX + (x - prevX) * t,
                        prevY + (y - prevY) * t,
                        prevZ + (z - prevZ) * t
                );

                if (isBlockedByTerrain(shooter, microPrev, microCurrent)) {
                    return false;
                }
                if (!isSegmentClear(shooter, microPrev, microCurrent, projectileRadius)) {
                    return false;
                }
                microPrev = microCurrent;
            }

            traveled += new Vec3(x, y, z).subtract(new Vec3(prevX, prevY, prevZ)).length();
        }
        return true;
    }

    /**
     * Чи блок (терен) перекриває сегмент дуги [from, to]. Окрема перевірка від союзників —
     * ванільний Sensing#hasLineOfSight рахує пряму лінію до ПОТОЧНОЇ позиції цілі, а не до
     * реальної точки прицілу з gravityDrop, тож дуга (що піднімається вище для компенсації
     * падіння) може насправді битись у стелю/стіну, яких пряма лінія до цілі не зачіпала.
     */
    private static boolean isBlockedByTerrain(Mob shooter, Vec3 from, Vec3 to) {
        BlockHitResult hit = shooter.level().clip(new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                shooter
        ));
        return hit.getType() != HitResult.Type.MISS;
    }

    /**
     * Перевіряє один сегмент дуги [from, to] на перетин із союзниками: raycast проти хітбокса
     * союзника, розширеного (inflate) на projectileRadius — той самий принцип, що в isPathClear.
     */
    private static boolean isSegmentClear(Mob shooter, Vec3 from, Vec3 to, double projectileRadius) {
        if (from.distanceToSqr(to) < 1.0e-12) {
            return true;
        }

        AABB broadPhase = new AABB(from, to).inflate(projectileRadius + 0.5);

        for (Entity entity : shooter.level().getEntities(shooter, broadPhase)) {
            if (!(entity instanceof LivingEntity living) || entity == shooter) {
                continue;
            }
            if (!BetterEnemysBehavior.isSameFaction(shooter, living)) {
                continue;
            }

            AABB inflatedBox = living.getBoundingBox().inflate(projectileRadius);
            Optional<Vec3> hit = inflatedBox.clip(from, to);
            if (hit.isPresent()) {
                return false;
            }
        }
        return true;
    }
}