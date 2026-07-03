package com.example.examplemod.utils;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class AdvancedAimMath {

    /**
     * Точний приціл БЕЗ випадкової похибки промаху — детермінований (для одних і тих же
     * shooter/target/швидкості/швидкості цілі результат завжди однаковий). Використовується
     * для стабільної перевірки шляху щотіку очікування: перевіряти лінію вогню до того, як
     * випадковий промах хоч раз кинутий, інакше кожен тік очікування генерував би НОВИЙ
     * випадковий кидок willMiss, і зрештою якийсь випадковий варіант технічно "проходив"
     * перевірку шляху, хоча реальний політ стріли (з ІНШИМ випадковим зсувом) міг зачепити
     * союзника — бо то була вже зовсім інша, статистично незалежна спроба прицілу.
     */
    public static AimResult calculatePreciseAim(Mob shooter, LivingEntity target, float baseProjectileSpeed, Vec3 targetVel) {
        double distance = shooter.distanceTo(target);

        // 1. Динамічна швидкість снаряда
        float dynamicSpeed = baseProjectileSpeed;
        if (distance > 15.0) {
            dynamicSpeed = baseProjectileSpeed + (float) ((distance - 15.0) * 0.035);
        }

        // --- РЕАЛЬНА БАЛІСТИКА ---
        double g = 0.05;

        double flightTime = distance / dynamicSpeed;

        Vec3 adjustedVel = targetVel.scale(1);

        // ітераційне уточнення
        for (int i = 0; i < 3; i++) {
            Vec3 predictedPos = target.position().add(adjustedVel.scale(flightTime));

            double dx = predictedPos.x - shooter.getX();
            double dz = predictedPos.z - shooter.getZ();
            // ВИПРАВЛЕНО: враховуємо передбачену вертикальну позицію цілі (стрибок/присідання
            // в момент пострілу), а не лише ПОТОЧНУ target.getEyeY() — інакше й кут, і flightTime
            // рахуються для застарілої висоти цілі.
            double predictedEyeY = target.getEyeY() + adjustedVel.y * flightTime;
            double dy = predictedEyeY - shooter.getEyeY();

            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            double speedSq = dynamicSpeed * dynamicSpeed;

            double underRoot = speedSq * speedSq - g * (g * horizontalDist * horizontalDist + 2 * dy * speedSq);

            if (underRoot < 0) {
                break;
            }

            double angle = Math.atan((speedSq - Math.sqrt(underRoot)) / (g * horizontalDist));

            double newTime = horizontalDist / (dynamicSpeed * Math.cos(angle));

            flightTime = (flightTime + newTime) * 0.5;
        }

        Vec3 predictedPos = target.position().add(adjustedVel.scale(flightTime));
        double targetY = target.getEyeY() + adjustedVel.y * flightTime;

        double gravityDrop = 0.5 * g * (flightTime * flightTime);

        double dX = predictedPos.x - shooter.getX();
        double dZ = predictedPos.z - shooter.getZ();
        double dY = targetY - shooter.getEyeY() + gravityDrop;

        return new AimResult(dX, dY, dZ, dynamicSpeed, 0.0f);
    }

    /**
     * Повний приціл З випадковою похибкою промаху (willMiss), кинутою РІВНО ОДИН РАЗ
     * усередині цього виклику. Викликати тільки в момент фактичного пострілу (не щотіку
     * очікування!) — інакше похибка перекидається щоразу, і шлях, перевірений для одного
     * випадкового варіанту, не гарантує безпеку для іншого, кинутого пізніше.
     * <p>
     * Це тонка обгортка над {@link #applyMissChance} для зворотної сумісності зі старими
     * викликами. Для нового сценарію "перевірити шлях до похибки і перерахувати при потребі"
     * використовуй {@link ProjectileTrajectory#resolveAimWithMissCheck}.
     */
    public static AimResult calculateAim(Mob shooter, LivingEntity target, float baseProjectileSpeed, Vec3 targetVel) {
        AimResult precise = calculatePreciseAim(shooter, target, baseProjectileSpeed, targetVel);
        return applyMissChance(shooter, target, precise);
    }

    /**
     * Кидає шанс похибки промаху і, якщо спрацювало, додає випадковий зсув до вже готового
     * точного прицілу {@code precise}. Кожен виклик — це НОВИЙ незалежний випадковий кидок
     * (і нового willMiss, і нового зсуву, якщо він спрацював) — викликати повторно для
     * перерахунку іншої похибки, якщо попередня виявилась заблокованою союзником.
     *
     * @return сам {@code precise} (той самий об'єкт, можна звірити через ==), якщо похибка
     * не спрацювала; інакше новий AimResult зі зсувом.
     */
    public static AimResult applyMissChance(Mob shooter, LivingEntity target, AimResult precise) {
        double distance = shooter.distanceTo(target);
        RandomSource random = shooter.getRandom();
        float missChance = (float) (distance * 0.5) / 100.0f;
        boolean willMiss = random.nextFloat() < missChance;

        if (!willMiss) {
            return precise;
        }

        // predictedPos у світових координатах (precise.dX/dY/dZ — зсуви від shooter):
        double predictedX = shooter.getX() + precise.dX();
        double predictedZ = shooter.getZ() + precise.dZ();

        int blockSpread = 1 + (int) (distance / 25.0);

        int shiftX = random.nextInt(blockSpread * 2 + 1) - blockSpread;
        int shiftZ = random.nextInt(blockSpread * 2 + 1) - blockSpread;
        int shiftY = random.nextInt(3) - 1;

        if (shiftX == 0 && shiftZ == 0) {
            shiftX = random.nextBoolean() ? 1 : -1;
        }

        double targetBlockX = Math.floor(predictedX) + shiftX + 0.5;
        double targetBlockZ = Math.floor(predictedZ) + shiftZ + 0.5;

        double offsetX = targetBlockX - predictedX;
        double offsetZ = targetBlockZ - predictedZ;
        double offsetY = shiftY;

        return new AimResult(
                precise.dX() + offsetX,
                precise.dY() + offsetY,
                precise.dZ() + offsetZ,
                precise.velocity(),
                precise.inaccuracy()
        );
    }

    public static Vec3 calculateLinearAim(Vec3 shooterPos, LivingEntity target, Vec3 targetVel, float projectileSpeed) {
        // БЕРЕМО ЦЕНТР ТІЛА (getY(0.5)), А НЕ ОЧІ
        Vec3 targetCenterPos = new Vec3(target.getX(), target.getY(0.5), target.getZ());

        double distance = shooterPos.distanceTo(targetCenterPos);

        double flightTime = distance / projectileSpeed;
        Vec3 adjustedVel = targetVel.scale(1.7); // Твій налаштований коефіцієнт

        // Передбачувана позиція центру тіла
        Vec3 predictedPos = targetCenterPos.add(adjustedVel.scale(flightTime));

        return predictedPos.subtract(shooterPos).normalize();
    }

    public static AimResult calculateSwimmingAim(Mob shooter, LivingEntity target, float baseProjectileSpeed, Vec3 targetVel) {
        double distance = shooter.distanceTo(target);

        // 1. Динамічна швидкість снаряда (як у твоєму основному методі)
        float dynamicSpeed = baseProjectileSpeed;
        if (distance > 15.0) {
            dynamicSpeed = baseProjectileSpeed + (float) ((distance - 15.0) * 0.035);
        }

        double g = 0.05;
        double flightTime = distance / dynamicSpeed;

        // Розрахунок ітерацій (точно як у тебе, але для ВСІХ осей X, Y, Z з центром тіла)
        // Множник швидкості залишаємо 1, бо ти передаєш scale(1.8) з гоалу
        Vec3 adjustedVel = targetVel.scale(1);

        // Фіксуємо точку відліку на центрі тіла гравця (getY(0.5)), щоб хитбокс у 1 блок не ламав приціл
        double targetY = target.getY(0.5);
        Vec3 predictedPos = target.position();

        for (int i = 0; i < 3; i++) {
            predictedPos = target.position().add(adjustedVel.x * flightTime, 0.0, adjustedVel.z * flightTime);
            targetY = target.getY(0.5) + (adjustedVel.y * flightTime);
        }

        // Компенсація гравітації тризубця
        double gravityDrop = 0.5 * g * (flightTime * flightTime);

        // Фінальні вектори
        double dX = predictedPos.x - shooter.getX();
        double dZ = predictedPos.z - shooter.getZ();
        double dY = targetY - shooter.getEyeY() + gravityDrop;

        return new AimResult(dX, dY, dZ, dynamicSpeed, 0.0f);
    }

    public record AimResult(double dX, double dY, double dZ, float velocity, float inaccuracy) {
    }
}