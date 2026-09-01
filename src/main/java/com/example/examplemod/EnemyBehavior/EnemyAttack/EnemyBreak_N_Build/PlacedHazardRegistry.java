package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBreak_N_Build;

import com.example.examplemod.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "лава/вогонь/вода які поставив саме гравець" — блоки рідини й вогню самі не пам'ятають, хто їх
 * поставив, тому тримаємо власний реєстр (той самий патерн, що й {@link TemporaryBlockData}: по
 * вимірах, з простим протуханням за часом — заповнюється з {@link PlacedHazardEvents}).
 * <p>
 * НИЗЬКА ВПЕВНЕНІСТЬ (чесно позначаю): сам реєстр — проста, перевірена структура даних, тут
 * ризику мало. Ризик увесь в {@link PlacedHazardEvents} — я не бачив жодного разу, щоб
 * {@code PlayerInteractEvent.RightClickBlock} реально спрацював у ЦЬОМУ проєкті, лише з
 * документації NeoForge. Якщо ця конкретна фіча не спрацює в грі — решта Фази 4 від цього не
 * постраждає, вони незалежні одна від одної.
 */
final class PlacedHazardRegistry {

    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> BY_DIMENSION = new ConcurrentHashMap<>();

    private PlacedHazardRegistry() {
    }

    static void markPlayerPlaced(ServerLevel level, BlockPos pos, long now) {
        long expiresAt = now + (long) Config.PLAYER_HAZARD_MEMORY_SECONDS.get() * 20L;
        dimensionMap(level).put(pos.immutable(), expiresAt);
    }

    /**
     * Чи ми пам'ятаємо цю позицію як поставлену гравцем І термін пам'яті ще не сплив. Не
     * перевіряє сам блок (це робить викликач — реєстр лише про "хто", не про "що там зараз").
     */
    static boolean isPlayerPlaced(ServerLevel level, BlockPos pos, long now) {
        Long expiresAt = dimensionMap(level).get(pos);
        return expiresAt != null && expiresAt > now;
    }

    private static Map<BlockPos, Long> dimensionMap(ServerLevel level) {
        return BY_DIMENSION.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>());
    }
}
