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

        Vec3 targetVel = target.position().subtract(target.xOld, target.yOld, target.zOld);

        // --- РЕАЛЬНА БАЛІСТИКА ---
        double g = 0.05;

        double flightTime = distance / dynamicSpeed;

        // ітераційне уточнення
        for (int i = 0; i < 3; i++) {

            Vec3 predictedPos = target.position().add(targetVel.scale(flightTime));

            double dx = predictedPos.x - shooter.getX();
            double dz = predictedPos.z - shooter.getZ();
            double dy = target.getEyeY() - shooter.getEyeY();

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

        // фінальна позиція

        Vec3 predictedPos = target.position().add(targetVel.scale(flightTime));
        double targetY = target.getEyeY();

        // 3. Шанс промаху
        RandomSource random = shooter.getRandom();
        float missChance = (float) (distance * 0.5) / 100.0f;
        boolean willMiss = random.nextFloat() < missChance;

        double offsetX = 0.0, offsetY = 0.0, offsetZ = 0.0;

        // 4. Логіка штучного промаху
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

        // компенсація гравітації
        double gravityDrop = 0.5 * g * (flightTime * flightTime);

        // фінальні вектори (НЕ нормалізуємо!)
        double dX = predictedPos.x - shooter.getX();
        double dZ = predictedPos.z - shooter.getZ();
        double dY = targetY - shooter.getEyeY() + gravityDrop;

        return new AimResult(dX, dY, dZ, dynamicSpeed, 0.0f);
    }

    public record AimResult(double dX, double dY, double dZ, float velocity, float inaccuracy) {
    }
}