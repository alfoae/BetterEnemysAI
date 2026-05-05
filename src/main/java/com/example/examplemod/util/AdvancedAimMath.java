package com.example.examplemod.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class AdvancedAimMath {

    public static AimResult calculateAim(Mob shooter, LivingEntity target, float baseProjectileSpeed) {
        double distance = shooter.distanceTo(target);

        // 1. Динамічна швидкість снаряда
        float dynamicSpeed = baseProjectileSpeed;
        if (distance > 15.0) {
            dynamicSpeed = baseProjectileSpeed + (float) ((distance - 15.0) * 0.035);
        }

        // ПОВЕРНУТО: Ванільний метод зчитування швидкості (який був раніше)
        Vec3 targetVel = target.getDeltaMovement();
        Vec3 flatTargetVel = new Vec3(targetVel.x, 0, targetVel.z);

        // 2. ФІКС ПРОМАХУ ПО БОКАХ:
        // Збільшуємо час випередження на 30% (1.3), щоб скелет брав трохи далі наперед
        // і стріла не пролітала в тебе за спиною.
        double flightTime = distance / dynamicSpeed;
        double leadTime = flightTime * 1.3;

        // Майбутня позиція
        Vec3 predictedPos = target.position().add(flatTargetVel.scale(leadTime));
        double targetY = predictedPos.y + (target.getEyeHeight() * 0.5);

        // 3. Шанс промаху
        RandomSource random = shooter.getRandom();
        float missChance = (float) (distance * 0.5) / 100.0f;
        boolean willMiss = random.nextFloat() < missChance;

        double offsetX = 0.0, offsetY = 0.0, offsetZ = 0.0;

        // 4. Логіка штучного промаху по блоках
        if (willMiss) {
            int blockSpread = 1 + (int) (distance / 25.0);

            int shiftX = random.nextInt(blockSpread * 2 + 1) - blockSpread;
            int shiftZ = random.nextInt(blockSpread * 2 + 1) - blockSpread;
            int shiftY = random.nextInt(3) - 1;

            if (shiftX == 0 && shiftZ == 0) {
                shiftX = random.nextBoolean() ? 1 : -1;
            }

            double targetBlockX = Math.floor(predictedPos.x) + shiftX + 0.5;
            double targetBlockZ = Math.floor(predictedPos.z) + shiftZ + 0.5;

            offsetX = targetBlockX - predictedPos.x;
            offsetZ = targetBlockZ - predictedPos.z;
            offsetY = shiftY;
        }

        predictedPos = predictedPos.add(offsetX, offsetY, offsetZ);
        targetY += offsetY;

        // ПОВЕРНУТО: Твоя ідеальна фізична гравітація (яку я випадково зламав минулого разу)
        double gravityDrop = 0.5 * 0.05 * (flightTime * flightTime);

        // 5. Фінальні вектори
        double dX = predictedPos.x - shooter.getX();
        double dY = targetY - shooter.getEyeY() + gravityDrop;
        double dZ = predictedPos.z - shooter.getZ();

        return new AimResult(dX, dY, dZ, dynamicSpeed, 0.0f);
    }

    public record AimResult(double dX, double dY, double dZ, float velocity, float inaccuracy) {
    }
}