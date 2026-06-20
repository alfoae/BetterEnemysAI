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
 * Перевірка робиться не по тонкій лінії, а по "капсулі" (труба заданого радіуса
 * навколо лінії вильоту) — так само, як снаряд має реальну товщину хітбокса.
 */
public class ProjectileTrajectoryUtils {

    /**
     * Перевіряє, чи на шляху від startPos до aimPoint немає союзника (тієї ж фракції, що й shooter),
     * чий хітбокс перетинає "трубу" радіусом projectileRadius навколо лінії вильоту.
     * <p>
     * ВАЖЛИВО: aimPoint — це РЕАЛЬНА точка, куди летітиме снаряд, тобто вже з урахуванням
     * випередження (та, що рахується через AdvancedAimMath.calculateAim/calculateLinearAim),
     * а НЕ поточна позиція цілі. Якщо ціль рухається, лінія "shooter -> поточна позиція цілі"
     * і реальна траєкторія снаряда — це різні лінії, тож перевіряти треба саме другу.
     *
     * @param shooter          моб, що стріляє
     * @param startPos         точка вильоту снаряда (зазвичай shooter.getEyePosition() або точка дула)
     * @param aimPoint         точка прицілювання з урахуванням випередження (куди реально летить снаряд)
     * @param projectileRadius "товщина" снаряда в блоках (напр. 0.25 для стріли/трайдента, 0.5 для фаєрбола)
     * @return true, якщо шлях вільний (можна стріляти), false — якщо на шляху союзник
     */
    public static boolean isPathClear(Mob shooter, Vec3 startPos, Vec3 aimPoint, double projectileRadius) {
        // Капсула: AABB по всій довжині лінії, розширена на радіус снаряда в усіх напрямках.
        AABB capsule = new AABB(startPos, aimPoint).inflate(projectileRadius);

        for (Entity entity : shooter.level().getEntities(shooter, capsule)) {
            if (!(entity instanceof LivingEntity living) || entity == shooter) {
                continue;
            }
            if (!BetterEnemysBehavior.isSameFaction(shooter, living)) {
                continue; // чужі (та й нейтральні) не заважають — по них і так можна випадково влучити
            }

            // Найкоротша відстань від хітбокса союзника до самої лінії вильоту (не до капсули!),
            // щоб коректно ловити випадки "хітбокс стоїть впритул до траєкторії, а не точно на ній".
            if (distanceFromBoxToSegment(living.getBoundingBox(), startPos, aimPoint) <= projectileRadius) {
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
        Vec3 start = shooter.getEyePosition();
        Vec3 aimPoint = start.add(aim.dX(), aim.dY(), aim.dZ());
        return isPathClear(shooter, start, aimPoint, projectileRadius);
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
     * Найкоротша відстань між AABB і відрізком [start, end].
     * Реалізовано через семплінг точок відрізка з clamp до меж боксу — достатньої
     * точності для ігрових хітбоксів (12 сегментів вистачає навіть на дальній дистанції).
     */
    private static double distanceFromBoxToSegment(AABB box, Vec3 start, Vec3 end) {
        // Якщо відрізок прямо перетинає бокс — відстань 0.
        if (box.clip(start, end).isPresent()) {
            return 0.0;
        }

        Vec3 dir = end.subtract(start);
        double bestDistSq = Double.MAX_VALUE;

        int samples = 12;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 p = start.add(dir.scale(t));

            double cx = clamp(p.x, box.minX, box.maxX);
            double cy = clamp(p.y, box.minY, box.maxY);
            double cz = clamp(p.z, box.minZ, box.maxZ);

            double dx = p.x - cx;
            double dy = p.y - cy;
            double dz = p.z - cz;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
            }
        }
        return Math.sqrt(bestDistSq);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}
