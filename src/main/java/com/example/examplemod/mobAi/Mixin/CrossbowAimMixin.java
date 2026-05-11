package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.util.AdvancedAimMath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(CrossbowItem.class)
public class CrossbowAimMixin {

    // Тепер ми зберігаємо не тільки координати, а й саму ціль!
    @Unique
    private static final ThreadLocal<LivingEntity> CURRENT_TARGET = new ThreadLocal<>();
    private static final ThreadLocal<Vec3> PREDICTED_POS = new ThreadLocal<>();

    @Inject(method = "shootProjectile", at = @At("HEAD"))
    private void calculatePrediction(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target, CallbackInfo ci    /* твої інші аргументи */) {

        if (shooter instanceof Piglin piglin && target != null) {
            Vec3 realTargetVel = com.example.examplemod.util.PlayerVelocityTracker.getRealVelocity(target);

// 2. Множимо на 5, щоб у грі стріла полетіла ДУЖЕ сильно наперед (для тесту)
            Vec3 targetVelForMath = realTargetVel.scale(5);

// 3. Відправляємо в математику!
            var aim = AdvancedAimMath.calculateAim(piglin, target, 4F, targetVelForMath);

            // ЗАПАМ'ЯТОВУЄМО ЦІЛЬ
            CURRENT_TARGET.set(target);

            double pX = shooter.getX() + aim.dX();
            double pZ = shooter.getZ() + aim.dZ();

            // Компенсуємо ванільне прицілювання арбалета (він сам додає 33% висоти)
            double vanillaAddedHeight = target.getBbHeight() * 0.3333333333333333D;
            double requiredY = shooter.getEyeY() + aim.dY() - vanillaAddedHeight;

            PREDICTED_POS.set(new Vec3(pX, requiredY, pZ));
        } else {
            CURRENT_TARGET.remove();
            PREDICTED_POS.remove();
        }
    }

    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double redirectTargetX(LivingEntity instance) {
        // ПІДМІНЯЄМО ТІЛЬКИ ЯКЩО instance — ЦЕ НАША ЦІЛЬ (не Піглін)
        if (instance == CURRENT_TARGET.get() && PREDICTED_POS.get() != null) {
            return PREDICTED_POS.get().x;
        }
        return instance.getX(); // Якщо це Піглін, віддаємо справжні координати
    }

    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY()D"))
    private double redirectTargetY(LivingEntity instance) {
        if (instance == CURRENT_TARGET.get() && PREDICTED_POS.get() != null) {
            return PREDICTED_POS.get().y;
        }
        return instance.getY();
    }

    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double redirectTargetZ(LivingEntity instance) {
        if (instance == CURRENT_TARGET.get() && PREDICTED_POS.get() != null) {
            return PREDICTED_POS.get().z;
        }
        return instance.getZ();
    }
}