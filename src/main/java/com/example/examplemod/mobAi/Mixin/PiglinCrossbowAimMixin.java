package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.utils.AdvancedAimMath;
import com.example.examplemod.utils.PlayerVelocityTracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.projectile.AbstractArrow;
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
public class PiglinCrossbowAimMixin {

    @Unique
    private static final ThreadLocal<LivingEntity> CURRENT_TARGET = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Vec3> PREDICTED_POS = new ThreadLocal<>();

    @Inject(method = "shootProjectile", at = @At("HEAD"))
    private void calculatePredictionForPiglin(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy, float angle, LivingEntity target, CallbackInfo ci) {

        // Фільтр: Працює ТІЛЬКИ якщо стрілець — Піглін і у нього є ціль
        if (shooter instanceof Piglin && target != null) {
            CURRENT_TARGET.set(target);

            // Налаштування "снайперського" Пігліна
            float arrowSpeed = 4.0F;

            // Баланс урону (щоб не вбивав з одного удару при такій швидкості)
            if (projectile instanceof AbstractArrow arrow) {
                arrow.setBaseDamage(1.3);
            }

            // Отримуємо реальну швидкість гравця
            Vec3 targetVel = PlayerVelocityTracker.getRealVelocity(target).scale(4.8);

            // Рахуємо випередження
            var aim = AdvancedAimMath.calculateAim((Mob) shooter, target, arrowSpeed, targetVel);

            if (aim != null) {
                double pX = shooter.getX() + aim.dX();
                double pZ = shooter.getZ() + aim.dZ();

                // Забираємо додавання дуги, щоб Піглін не стріляв по "літаках"
                double vanillaAddedHeight = target.getBbHeight() * 0.3333333333333333D;
                double requiredY = shooter.getEyeY() + aim.dY() - vanillaAddedHeight;

                PREDICTED_POS.set(new Vec3(pX, requiredY, pZ));
            } else {
                PREDICTED_POS.remove();
            }
        }
    }

    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getX()D"))
    private double redirectPiglinTargetX(LivingEntity instance) {
        return (instance == CURRENT_TARGET.get() && PREDICTED_POS.get() != null) ? PREDICTED_POS.get().x : instance.getX();
    }

    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getY()D"))
    private double redirectPiglinTargetY(LivingEntity instance) {
        return (instance == CURRENT_TARGET.get() && PREDICTED_POS.get() != null) ? PREDICTED_POS.get().y : instance.getY();
    }

    @Redirect(method = "shootProjectile", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getZ()D"))
    private double redirectPiglinTargetZ(LivingEntity instance) {
        return (instance == CURRENT_TARGET.get() && PREDICTED_POS.get() != null) ? PREDICTED_POS.get().z : instance.getZ();
    }

    @Inject(method = "shootProjectile", at = @At("TAIL"))
    private void cleanup(CallbackInfo ci) {
        // Очищаємо дані після пострілу, щоб уникнути витоку пам'яті
        CURRENT_TARGET.remove();
        PREDICTED_POS.remove();
    }
}