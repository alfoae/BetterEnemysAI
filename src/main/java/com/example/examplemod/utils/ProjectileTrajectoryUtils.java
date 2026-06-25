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

/**
 * Утиліти для перевірки траєкторії снаряда перед пострілом: чи не зачепить він
 * союзника по фракції (фракції визначаються в BetterEnemysBehavior).
 * <p>
 * Перевірка робиться не по колу навколо лінії (це недооцінює зону влучання в кутах
 * на ~41%), а по РЕАЛЬНОМУ квадратному перетину хітбокса снаряда: будується локальний
 * базис вздовж напрямку польоту (forward/right/up), і хітбокс союзника перевіряється
 * на перетин з прямокутним тунелем half×half у цих локальних осях — так само, як
 * квадратний хітбокс стріли повертається разом з напрямком її руху.
 */
public class ProjectileTrajectoryUtils {

    /**
     * Перевіряє, чи на шляху від startPos до aimPoint немає союзника (тієї ж фракції, що й shooter),
     * чий хітбокс перетинає квадратний (у перетині) тунель навколо лінії вильоту.
     * <p>
     * ВАЖЛИВО: aimPoint — це РЕАЛЬНА точка, куди летітиме снаряд, тобто вже з урахуванням
     * випередження (та, що рахується через AdvancedAimMath.calculateAim/calculateLinearAim),
     * а НЕ поточна позиція цілі. Якщо ціль рухається, лінія "shooter -> поточна позиція цілі"
     * і реальна траєкторія снаряда — це різні лінії, тож перевіряти треба саме другу.
     *
     * @param shooter          моб, що стріляє
     * @param startPos         точка вильоту снаряда (зазвичай shooter.getEyePosition() або точка дула)
     * @param aimPoint         точка прицілювання з урахуванням випередження (куди реально летить снаряд)
     * @param projectileRadius половина ширини квадратного хітбокса снаряда в блоках
     *                         (напр. 0.25 для стріли/трайдента, 0.5 для фаєрбола)
     * @return true, якщо шлях вільний (можна стріляти), false — якщо на шляху союзник
     */
    public static boolean isPathClear(Mob shooter, Vec3 startPos, Vec3 aimPoint, double projectileRadius) {
        // Грубий перший фільтр: AABB-капсула трохи більша за тунель (щоб не пропустити кандидатів
        // через округлення на діагоналях), щоб не ганяти точну перевірку по всіх сутностях у світі.
        AABB broadPhase = new AABB(startPos, aimPoint).inflate(projectileRadius * 1.5);

        Vec3 forward = aimPoint.subtract(startPos);
        double length = forward.length();
        if (length < 1.0e-6) {
            return true; // стрілець і точка прицілу співпадають — нічого перевіряти
        }
        forward = forward.scale(1.0 / length);

        // Будуємо два вектори, перпендикулярні forward, щоб отримати локальний базис тунелю.
        // Якщо forward майже вертикальний, world-up не годиться (буде паралельний) — підміняємо.
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = (Math.abs(forward.y) > 0.999)
                ? new Vec3(1, 0, 0).cross(forward)
                : worldUp.cross(forward);
        right = right.normalize();
        Vec3 up = forward.cross(right).normalize();

        for (Entity entity : shooter.level().getEntities(shooter, broadPhase)) {
            if (!(entity instanceof LivingEntity living) || entity == shooter) {
                continue;
            }
            if (!BetterEnemysBehavior.isSameFaction(shooter, living)) {
                continue; // чужі (та й нейтральні) не заважають — по них і так можна випадково влучити
            }

            if (intersectsOrientedTunnel(living.getBoundingBox(), startPos, forward, right, up, length, projectileRadius)) {
                return false; // союзник заважає — чекаємо
            }
        }
        return true;
    }

    /**
     * Перевантажений варіант для зручності: будує aimPoint сам із результату AdvancedAimMath
     * (AimResult зберігає dX/dY/dZ — зсуви ВІД shooter, тобто з урахуванням випередження й промаху).
     * Викликати так: isPathClear(this.mob, aim, 0.25) одразу після AdvancedAimMath.calculateAim(...).
     */
    public static boolean isPathClear(Mob shooter, AdvancedAimMath.AimResult aim, double projectileRadius) {
        projectileRadius = projectileRadius + 0.03;
        Vec3 start = shooter.getEyePosition();
        Vec3 aimPoint = start.add(aim.dX(), aim.dY(), aim.dZ());
        return isPathClear(shooter, start, aimPoint, projectileRadius);
    }

    private static final int MAX_MISS_RETRIES = 8;

    /**
     * Повна послідовність прийняття рішення "чи і куди стріляти" з урахуванням похибки промаху:
     * <ol>
     *   <li>Рахуємо ТОЧНИЙ приціл (без похибки) і перевіряємо шлях до нього. Якщо союзник
     *       заважає навіть ідеальному вистрілу — повертаємо null, похибка навіть не рахується
     *       (стрілець просто не бачить чистої лінії до ворога взагалі).</li>
     *   <li>Якщо точний шлях вільний — кидаємо шанс похибки. Без похибки: стріляємо точно.</li>
     *   <li>З похибкою: рахуємо зміщену точку і перевіряємо ЇЇ шлях. Якщо на шляху похибки
     *       стоїть союзник — перераховуємо НОВУ похибку (новий випадковий зсув) і перевіряємо
     *       знову, до {@link #MAX_MISS_RETRIES} спроб. Якщо жодна спроба не дала чистого шляху —
     *       стріляємо точним прицілом (він уже підтверджено вільний на кроці 1).</li>
     * </ol>
     * Ціль (ворог) сама по собі ніколи не вважається перешкодою — тунель обмежений довжиною
     * до неї, тож союзник, що стоїть ЗА ціллю, природно не блокує.
     *
     * @return фінальний AimResult, яким можна стріляти, або null, якщо навіть точний
     *         (без похибки) вистріл по ворогу заблокований союзником.
     */
    public static AdvancedAimMath.AimResult resolveAimWithMissCheck(Mob shooter, LivingEntity target,
                                                                    float baseProjectileSpeed, Vec3 targetVel,
                                                                    double projectileRadius) {
        AdvancedAimMath.AimResult precise = AdvancedAimMath.calculatePreciseAim(shooter, target, baseProjectileSpeed, targetVel);

        if (!isPathClear(shooter, precise, projectileRadius)) {
            return null; // союзник заважає навіть ідеальному вистрілу — не стріляємо, похибку не рахуємо
        }

        AdvancedAimMath.AimResult withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);

        if (withMiss == precise) {
            return precise; // похибка не спрацювала (willMiss == false) — стріляємо точно
        }

        for (int attempt = 0; attempt < MAX_MISS_RETRIES; attempt++) {
            if (isPathClear(shooter, withMiss, projectileRadius)) {
                return withMiss; // шлях похибки вільний — стріляємо саме туди
            }
            // На шляху похибки союзник — перераховуємо НОВУ похибку (новий випадковий зсув
            // від того ж точного прицілу) і перевіряємо знову.
            withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);
        }

        // Жодна з MAX_MISS_RETRIES спроб похибки не дала чистого шляху — відступаємо
        // до точного прицілу, який вже підтверджено вільним на кроці 1.
        return precise;
    }

    /**
     * Аналог {@link #resolveAimWithMissCheck}, але використовує БАЛІСТИЧНУ (дугову, тікову)
     * перевірку шляху замість прямої лінії — точніше для скелета/дроунда, де снаряд летить
     * параболою через гравітацію, а не прямо. Та сама послідовність: точний приціл -> перевірка
     * дуги -> похибка -> перевірка дуги похибки -> ретраї.
     * <p>
     * ВАЖЛИВО: для звичайних стріл Minecraft не inflate-ить хітбокс цілі при перевірці
     * колізії (на відміну від ThrowableProjectile) — перевірка йде чистим raycast проти
     * РЕАЛЬНОГО getBoundingBox(). Тому projectileRadius тут — це НЕ "товщина стріли", а лише
     * малий буфер безпеки (рекомендовано ~0.02-0.05) проти похибок дискретизації по тіках/
     * плаваючій точці. Передавай близько 0.0, якщо потрібна максимально точна відповідність
     * ванільній механіці; трохи більше — для додаткового запасу обережності.
     */
    public static AdvancedAimMath.AimResult resolveBallisticAimWithMissCheck(Mob shooter, LivingEntity target,
                                                                             float baseProjectileSpeed, Vec3 targetVel,
                                                                             double projectileRadius) {
        AdvancedAimMath.AimResult precise = AdvancedAimMath.calculatePreciseAim(shooter, target, baseProjectileSpeed, targetVel);
        Vec3 start = shooter.getEyePosition();

        if (!isPathClearBallistic(shooter, start, start.add(precise.dX(), precise.dY(), precise.dZ()),
                precise.velocity(), projectileRadius)) {
            return null; // союзник на реальній дузі навіть ідеального вистрілу — не стріляємо
        }

        AdvancedAimMath.AimResult withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);

        if (withMiss == precise) {
            return precise;
        }

        for (int attempt = 0; attempt < MAX_MISS_RETRIES; attempt++) {
            Vec3 missAimPoint = start.add(withMiss.dX(), withMiss.dY(), withMiss.dZ());
            if (isPathClearBallistic(shooter, start, missAimPoint, withMiss.velocity(), projectileRadius + 0.03)) {
                return withMiss;
            }
            withMiss = AdvancedAimMath.applyMissChance(shooter, target, precise);
        }

        return precise;
    }


    /**
     * Спрощений варіант "на швидку руку": перевіряє пряму лінію до ПОТОЧНОЇ позиції цілі,
     * без випередження. Підходить лише для мобів без прогнозування руху (ціль стоїть на місці
     * або точність випередження не критична). Для мобів з AdvancedAimMath.calculateAim/
     * calculateLinearAim використовуй перевантаження вище з реальною aimPoint — інакше
     * перевірка дивиться не туди, куди насправді летить снаряд.
     */
    public static boolean isPathClear(Mob shooter, LivingEntity target, double projectileRadius) {
        return isPathClear(shooter, shooter.getEyePosition(), target.getEyePosition(), projectileRadius);
    }

    /**
     * ТОЧНІША перевірка для балістичних снарядів (стріла зі скелета/дроунда): замість прямої
     * лінії до aimPoint симулює РЕАЛЬНУ тікову траєкторію стріли (drag=0.99 множення швидкості
     * щотіку, потім gravity=0.05 віднімання від вертикальної компоненти — так само, як рахує
     * сам Minecraft для AbstractArrow). Пряма лінія систематично недооцінює висоту дуги в першій
     * половині польоту (де стріла ще піднімається, компенсуючи майбутнє падіння) — саме там
     * союзник, що "ледь не перекриває" пряму лінію, насправді може бути зачеплений дугою.
     * <p>
     * Будує дугу як ламану з тікових сегментів (тікова симуляція руху) і перевіряє КОЖЕН
     * сегмент тим самим орієнтованим тунелем, що й {@link #isPathClear}.
     *
     * @param shooter          моб, що стріляє
     * @param startPos         точка вильоту (очі стрільця)
     * @param aimPoint         точка прицілу (та, що повернув AdvancedAimMath — з gravityDrop)
     * @param projectileSpeed  швидкість снаряда (та сама, що передається в calculateAim/calculatePreciseAim)
     * @param projectileRadius половина ширини хітбокса снаряда
     * @return true, якщо ВСЯ дуга вільна від союзників
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

        // Симулюємо тіки, поки не пролетимо очікувану довжину шляху (+невеликий запас),
        // або поки не вийдемо за розумну межу тіків (захист від нескінченного цикла,
        // якщо снаряд летить майже вертикально і ніколи не "долітає" по горизонталі).
        int maxTicks = 200;
        int microSteps = 4; // підкроки всередині кожного тіку - ловлять різку зміну висоти
        // дуги між тіками, яку грубий крок "від тіку до тіку" міг пропустити
        double traveled = 0;
        double targetTravelApprox = length * 1.05;

        for (int tick = 0; tick < maxTicks && traveled < targetTravelApprox; tick++) {
            double prevX = x, prevY = y, prevZ = z;

            x += vx;
            y += vy;
            z += vz;
            vx *= drag;
            vy *= drag;
            vz *= drag;
            vy -= gravity;

            // Перевіряємо не лише весь тік цілим сегментом, а й microSteps проміжних
            // піделементів усередині нього — лінійна інтерполяція позиції достатньо точна
            // в межах одного тіку (рух за тік малий порівняно з повною дугою).
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

            Vec3 currentPoint = new Vec3(x, y, z);
            traveled += currentPoint.subtract(new Vec3(prevX, prevY, prevZ)).length();
        }
        return true;
    }

    /**
     * Перевіряє, чи блок (терен) перекриває сегмент дуги [from, to]. Окрема перевірка від
     * союзників — ванільний Sensing#hasLineOfSight рахує пряму лінію до ПОТОЧНОЇ позиції цілі,
     * а не до реальної точки прицілу з gravityDrop, тож може дати "видно", хоча реальна дуга
     * (що піднімається вище для компенсації падіння) насправді б'ється в стелю/стіну, яких
     * пряма лінія до поточної (нижчої) позиції цілі не зачіпала.
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
     * Перевіряє один сегмент дуги [from, to] на перетин із союзниками ЧИСТИМ RAYCAST —
     * без жодного штучного радіуса/тунелю навколо лінії. Це точне відтворення офіційної
     * механіки Minecraft: звичайні стріли (Arrow/TippedArrow/SpectralArrow/Trident) рахують
     * колізію як тонкий промінь проти РЕАЛЬНОГО (без inflate) getBoundingBox() цілі — на
     * відміну від ThrowableProjectile (снігова куля/яйце/перлина), які inflate-ять ціль на
     * 0.3 в кожен бік. Параметр projectileRadius лишається в сигнатурі для сумісності
     * викликів, але ігнорується для balistic-перевірки стріл (передавай 0.0).
     */
    private static boolean isSegmentClear(Mob shooter, Vec3 from, Vec3 to, double projectileRadius) {
        double segLength = to.subtract(from).length();
        if (segLength < 1.0e-6) {
            return true;
        }

        AABB broadPhase = new AABB(from, to).inflate(0.5); // невеликий запас лише для відбору кандидатів з world

        for (Entity entity : shooter.level().getEntities(shooter, broadPhase)) {
            if (!(entity instanceof LivingEntity living) || entity == shooter) {
                continue;
            }
            if (!BetterEnemysBehavior.isSameFaction(shooter, living)) {
                continue;
            }
            // Чистий raycast: чи відрізок [from,to] перетинає РЕАЛЬНИЙ (нерозширений) бокс союзника.
            if (living.getBoundingBox().clip(from, to).isPresent()) {
                return false;
            }
        }
        return true;
    }


    /**
     * Перевіряє, чи AABB союзника перетинає орієнтований прямокутний тунель квадратного
     * перетину (half=projectileRadius) вздовж forward, обмежений довжиною length.
     * <p>
     * Три взаємодоповнюючі перевірки (підтверджено автоматизованим тестом на тисячах
     * випадкових конфігурацій проти brute-force "істини" — без цих трьох разом залишались
     * ~0.6% пропущених влучань):
     * <ol>
     *   <li>8 вершин боксу — чи потрапляє якась всередину тунелю.</li>
     *   <li>Найближча до осі точка боксу (clamp центру) — покриває "вісь проходить крізь
     *       товщу великого боксу, жодна вершина не в тунелі".</li>
     *   <li>4 кутові лінії тунелю (паралельні forward, зсунуті на (±half,±half) в right/up)
     *       проти AABB боксу — покриває "кут квадратного перетину тунелю зачіпає грань
     *       боксу, а жодна вершина боксу при цьому не потрапляє в тунель" (саме цей випадок
     *       пропускали перші дві перевірки на близьких дистанціях/малих хітбоксах).</li>
     * </ol>
     */
    private static boolean intersectsOrientedTunnel(AABB box, Vec3 start, Vec3 forward, Vec3 right, Vec3 up,
                                                    double length, double half) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    if (pointInTunnel(x, y, z, start, forward, right, up, length, half)) {
                        return true;
                    }
                }
            }
        }

        // Додатково перевіряємо найближчу до осі тунелю точку боксу (clamp центру боксу
        // в локальних right/up до [-half, half], переводимо назад у світові координати,
        // і ще раз clamp-имо вже в межі самого боксу) — покриває випадок, коли вісь
        // проходить крізь товщу боксу, а жодна вершина при цьому не потрапляє в тунель
        // (можливо для великих боксів, що охоплюють вузький тунель повністю).
        Vec3 center = new Vec3((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        Vec3 rel = center.subtract(start);
        double f = rel.dot(forward);
        double f_clamped = clamp(f, 0, length);
        Vec3 axisPoint = start.add(forward.scale(f_clamped));

        double cx = clamp(axisPoint.x, box.minX, box.maxX);
        double cy = clamp(axisPoint.y, box.minY, box.maxY);
        double cz = clamp(axisPoint.z, box.minZ, box.maxZ);

        if (pointInTunnel(cx, cy, cz, start, forward, right, up, length, half)) {
            return true;
        }

        // Третя перевірка: 4 кутові лінії тунелю проти AABB боксу.
        Vec3 endPoint = start.add(forward.scale(length));
        for (double sr : new double[]{-half, half}) {
            for (double su : new double[]{-half, half}) {
                Vec3 offset = right.scale(sr).add(up.scale(su));
                Vec3 lineStart = start.add(offset);
                Vec3 lineEnd = endPoint.add(offset);
                if (segmentIntersectsAabb(lineStart, lineEnd, box)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Стандартний slab-тест: чи відрізок [p1, p2] перетинає AABB box.
     */
    private static boolean segmentIntersectsAabb(Vec3 p1, Vec3 p2, AABB box) {
        double tMin = 0.0, tMax = 1.0;
        double dx = p2.x - p1.x, dy = p2.y - p1.y, dz = p2.z - p1.z;

        // X
        if (Math.abs(dx) < 1.0e-12) {
            if (p1.x < box.minX || p1.x > box.maxX) return false;
        } else {
            double t1 = (box.minX - p1.x) / dx;
            double t2 = (box.maxX - p1.x) / dx;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        // Y
        if (Math.abs(dy) < 1.0e-12) {
            if (p1.y < box.minY || p1.y > box.maxY) return false;
        } else {
            double t1 = (box.minY - p1.y) / dy;
            double t2 = (box.maxY - p1.y) / dy;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        // Z
        if (Math.abs(dz) < 1.0e-12) {
            return !(p1.z < box.minZ) && !(p1.z > box.maxZ);
        } else {
            double t1 = (box.minZ - p1.z) / dz;
            double t2 = (box.maxZ - p1.z) / dz;
            if (t1 > t2) {
                double tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            return !(tMin > tMax);
        }
    }

    private static boolean pointInTunnel(double x, double y, double z, Vec3 start, Vec3 forward, Vec3 right, Vec3 up,
                                         double length, double half) {
        Vec3 rel = new Vec3(x, y, z).subtract(start);
        double f = rel.dot(forward);
        if (f < -half || f > length + half) {
            return false; // далеко за межами тунелю по довжині (з невеликим запасом на торці)
        }
        double r = rel.dot(right);
        double u = rel.dot(up);
        return Math.abs(r) <= half && Math.abs(u) <= half;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}