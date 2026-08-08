package com.example.examplemod.EnemyBehavior.EnemyAttack.EnemyBrake_N_Build;

import com.example.examplemod.BetterEnemysAI;
import com.example.examplemod.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Який блок моб ставить/копає — залежить від виміру, налаштовується в {@link Config}
 * (overworldDigBlock/netherDigBlock/endDigBlock). Звичайний світ і будь-який КАСТОМНИЙ вимір
 * (не Nether/End) трактуються як "звичайний світ" — фолбек на overworld-налаштування.
 */
public final class DigBlockResolver {

    private DigBlockResolver() {
    }

    public static Block getDigBlock(Level level) {
        String configuredId;
        if (level.dimension() == Level.NETHER) {
            configuredId = Config.NETHER_DIG_BLOCK.get();
        } else if (level.dimension() == Level.END) {
            configuredId = Config.END_DIG_BLOCK.get();
        } else {
            configuredId = Config.OVERWORLD_DIG_BLOCK.get();
        }

        ResourceLocation id = ResourceLocation.tryParse(configuredId);
        if (id == null) {
            BetterEnemysAI.LOGGER.warn("EnemyBrake_N_Build: невалідний ID блоку в конфізі: '{}', fallback на булижник", configuredId);
            return Blocks.COBBLESTONE;
        }

        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR && !id.equals(ResourceLocation.withDefaultNamespace("air"))) {
            // DefaultedRegistry повертає AIR, якщо ID не знайдено в реєстрі — і сам air теж AIR,
            // тому звіряємось ще й з ID, щоб не сплутати "не знайдено" з дійсним запитом air.
            BetterEnemysAI.LOGGER.warn("EnemyBrake_N_Build: блок '{}' не знайдено в реєстрі, fallback на булижник", configuredId);
            return Blocks.COBBLESTONE;
        }
        return block;
    }
}
