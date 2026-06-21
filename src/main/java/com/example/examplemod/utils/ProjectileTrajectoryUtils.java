package com.example.examplemod.utils;

import com.example.examplemod.EnemyBehavior.BetterEnemysBehavior;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
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

    private static final int MAX_MISS_RETRIES = 8;

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
     * (без похибки) вистріл по ворогу заблокований союзником.
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
     * Перевіряє, чи AABB союзника перетинає орієнтований прямокутний тунель квадратного
     * перетину (half=projectileRadius) вздовж forward, обмежений довжиною length.
     * <p>
     * Метод: семплимо box по його 8 вершинах ТА по найближчій до осі точці (через clamp
     * у локальних координатах), переводимо кожну точку в локальний базис (right, up, forward)
     * відносно start, і перевіряємо: |right| <= half І |up| <= half І 0 <= forward <= length.
     * Семплінгу вершин достатньо, бо AABB — випуклий об'єкт: якщо жодна вершина не всередині
     * тунелю, але тунель проходить крізь середину боксу, найближча-до-осі точка це покриє.
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

        return pointInTunnel(cx, cy, cz, start, forward, right, up, length, half);
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