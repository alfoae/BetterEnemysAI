package com.example.examplemod.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public class AdvancedAimMath {

    /**
     * Універсальний метод для розрахунку ідеального пострілу.
     * Його можна викликати з AI Goal будь-якого моба.
     */
    public static AimResult calculateAim(Mob shooter, LivingEntity target, float baseProjectileSpeed) {
        // 1. Отримуємо радіус з твого ModAttributesHandler
        double maxAggroRadius = shooter.getAttributeValue(Attributes.FOLLOW_RANGE);

        // 2. Радіус стрільби: 3/4 від агра, але не менше ванільних 16 блоків
        double maxShootRange = Math.max(maxAggroRadius * 0.75, 16.0);
        double distance = shooter.distanceTo(target);

        // Якщо ціль вийшла за межі ефективного радіуса пострілу
        if (distance > maxShootRange) {
            return null; // Повертаємо null (моб не повинен стріляти)
        }

        // 3. Динамічна швидкість снаряда
        // Оскільки у стріл є опір повітря та гравітація, на великих дистанціях
        // ванільної швидкості не вистачить. Додаємо компенсацію.
        float dynamicSpeed = baseProjectileSpeed;
        if (distance > 15.0) {
            // Чим далі ціль, тим сильніший "поштовх" потрібен снаряду
            dynamicSpeed = baseProjectileSpeed + (float) ((distance - 15.0) * 0.05);
        }

        // 4. Стрільба на випередження (Predictive Aiming)
        // Враховуємо вектор руху цілі
        Vec3 targetVelocity = target.getDeltaMovement();

        // Час польоту t = відстань / швидкість
        double flightTime = distance / dynamicSpeed;

        // Майбутня позиція: P_future = P_current + (V_target * t)
        Vec3 predictedPos = target.position().add(targetVelocity.scale(flightTime));

        // Цілимось у центр тіла (або голову), а не в стопи
        double targetY = predictedPos.y + (target.getEyeHeight() * 0.5);

        // 5. Шанс похибки (Miss Chance)
        // Формула: 0.5 * відстань (у відсотках). Наприклад: 50 блоків = 25% шансу.
        float missChance = (float) (distance * 0.5) / 100.0f;
        RandomSource random = shooter.getRandom();
        boolean willMiss = random.nextFloat() < missChance;

        // 6. Розброс (Inaccuracy)
        // Формула: 1 блок + 1 блок за кожні 25 блоків дистанції
        float inaccuracy = 1.0f + (float) (distance / 25.0);

        if (willMiss) {
            // Якщо математика сказала, що моб промахнеться — штучно ламаємо йому приціл
            inaccuracy += 15.0f;
        }

        // 7. Розрахунок підсумкового вектора пострілу (dX, dY, dZ)
        double dX = predictedPos.x - shooter.getX();
        double dY = targetY - shooter.getEyeY();
        double dZ = predictedPos.z - shooter.getZ();

        // Повертаємо готовий результат
        return new AimResult(dX, dY, dZ, dynamicSpeed, inaccuracy);
    }

    /**
     * Допоміжний клас (Record), який зберігає результати обчислень.
     * Замість Record можна використовувати звичайний public static class, якщо в тебе стара версія Java.
     */
    public record AimResult(double dX, double dY, double dZ, float velocity, float inaccuracy) {
    }
}