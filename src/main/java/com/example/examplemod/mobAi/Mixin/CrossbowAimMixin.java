package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.util.AdvancedAimMath;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
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

    @Unique
    private static final ThreadLocal<LivingEntity> CURRENT_TARGET = new ThreadLocal<>();
    private static final ThreadLocal<Vec3> PREDICTED_POS = new ThreadLocal<>();

    @Inject(method = "shootProjectile", at = @At("HEAD"))
    private void calculatePrediction(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target, CallbackInfo ci) {

        // Змінюємо умову: тепер перевіряємо і на Пігліна, і на Розбійника
        if ((shooter instanceof Piglin || shooter instanceof Pillager) && target != null) {
            Vec3 realTargetVel = com.example.examplemod.util.PlayerVelocityTracker.getRealVelocity(target);

            // Множимо швидкість для тесту випередження
            Vec3 targetVelForMath = realTargetVel.scale(5);

            // Вираховуємо точку прицілювання (передаємо shooter як LivingEntity)
            var aim = AdvancedAimMath.calculateAim((Mob) shooter, target, 4F, targetVelForMath);

            CURRENT_TARGET.set(target);

            double pX = shooter.getX() + aim.dX();
            double pZ = shooter.getZ() + aim.dZ();

            // Компенсація ванільного прицілювання
            double vanillaAddedHeight = target.getBbHeight() * 0.3333333333333333D;
            double requiredY = shooter.getEyeY() + aim.dY() - vanillaAddedHeight;

            PREDICTED_POS.set(new Vec3(pX, requiredY, pZ));
        } else {
            CURRENT_TARGET.remove();
            PREDICTED_POS.remove();
        }
    }

    // Редіректи залишаються без змін, оскільки вони працюють з CURRENT_TARGET
    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double redirectTargetX(LivingEntity instance) {
        if (instance == CURRENT_TARGET.get() && PREDICTED_POS.get() != null) {
            return PREDICTED_POS.get().x;
        }
        return instance.getX();
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