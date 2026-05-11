package com.example.examplemod.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// ВАЖЛИВО: Заміни "betterenemysai" на свій справжній modid (якщо він інший)!
@EventBusSubscriber(modid = "betterenemysai")
public class PlayerVelocityTracker {

    private static final Map<UUID, Vec3> lastPositions = new HashMap<>();
    private static final Map<UUID, Vec3> realVelocities = new HashMap<>();

    // Цей метод викликається автоматично 20 разів на секунду для кожного гравця
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        UUID id = player.getUUID();
        Vec3 currentPos = player.position();

        if (lastPositions.containsKey(id)) {
            Vec3 lastPos = lastPositions.get(id);
            // 1. Рахуємо точну різницю між минулим і цим тіком
            Vec3 velocity = currentPos.subtract(lastPos);

            // 2. Згладжуємо (lerp), щоб не було різких стрибків під час лагів інтернету
            Vec3 oldVel = realVelocities.getOrDefault(id, Vec3.ZERO);
            realVelocities.put(id, oldVel.lerp(velocity, 0.4));
        }

        // Запам'ятовуємо поточну позицію для наступного тіку
        lastPositions.put(id, currentPos);
    }

    // Головний метод, який будуть викликати твої моби!
    public static Vec3 getRealVelocity(LivingEntity target) {
        if (target instanceof Player player) {
            // Якщо ціль - гравець, беремо нашу плавно пораховану швидкість
            return realVelocities.getOrDefault(player.getUUID(), Vec3.ZERO);
        }
        // Якщо ціль - інший моб (наприклад, голем), ванільний метод працює нормально
        return target.getDeltaMovement();
    }
}