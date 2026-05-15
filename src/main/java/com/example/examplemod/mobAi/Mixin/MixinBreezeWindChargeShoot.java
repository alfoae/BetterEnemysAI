package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.PlayerVelocityTracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.windcharge.BreezeWindCharge;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// 1. Ціль тепер - базовий клас усіх снарядів
@Mixin(Projectile.class)
public abstract class MixinBreezeWindChargeShoot {

    // 2. Використовуємо точний дескриптор методу (DDDFF)V, щоб IntelliJ точно його знайшла
    @Inject(method = "shoot(DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void redirectAimOnShoot(double x, double y, double z, float velocity, float inaccuracy, CallbackInfo ci) {

        // 3. Фільтруємо: нас цікавлять ТІЛЬКИ снаряди Бриза
        if ((Object) this instanceof BreezeWindCharge charge) {

            // Перевіряємо, чи власник снаряда — саме Бриз
            if (charge.getOwner() instanceof Breeze breeze) {

                // Шукаємо ціль
                LivingEntity target = breeze.getTarget();
                if (target == null) {
                    target = breeze.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                }

                if (target != null) {
                    // Отримуємо реальну швидкість
                    Vec3 targetVel = PlayerVelocityTracker.getRealVelocity(target);
                    Vec3 shooterPos = charge.position();

                    // 1. СТВОРЮЄМО НОВУ ШВИДКІСТЬ
                    // Наприклад, зробимо його в 1.5 раза швидшим за стандартний:
                    float customSpeed = 3;

                    // Або можеш задати фіксовану швидкість (ванільна зазвичай близько 1.0 - 1.5)
                    // float customSpeed = 2.5f;

                    // 2. Передаємо НОВУ швидкість у твою математику
                    Vec3 aimDir = AdvancedAimMath.calculateLinearAim(shooterPos, target, targetVel.scale(1.3), customSpeed);

                    // 3. Задаємо рух з НОВОЮ швидкістю
                    charge.setDeltaMovement(aimDir.scale(customSpeed));

                    // Скасовуємо стандартний постріл
                    ci.cancel();
                }
            }
        }
    }
}