package com.example.examplemod.EnemyBehavior;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;


@Mod(СhangeSearchRadius.MODID)

public class СhangeSearchRadius {


    public static final String MODID = "betterenemysai"; // Має співпадати з mods.toml

    public СhangeSearchRadius(IEventBus modEventBus) {

        modEventBus.addListener(this::modifyAttributes);
    }

    public void modifyAttributes(EntityAttributeModificationEvent event) {

        // ==========================================
        // 1. МОНСТРИ
        // ==========================================
        event.add(EntityType.ZOMBIE, Attributes.FOLLOW_RANGE, 30.0);
        event.add(EntityType.HUSK, Attributes.FOLLOW_RANGE, 40.0);
        event.add(EntityType.DROWNED, Attributes.FOLLOW_RANGE, 25.0);
        event.add(EntityType.ZOMBIE_VILLAGER, Attributes.FOLLOW_RANGE, 30.0);

        event.add(EntityType.SKELETON, Attributes.FOLLOW_RANGE, 40.0);
        event.add(EntityType.STRAY, Attributes.FOLLOW_RANGE, 35.0);
        event.add(EntityType.WITHER_SKELETON, Attributes.FOLLOW_RANGE, 30.0);

        event.add(EntityType.SPIDER, Attributes.FOLLOW_RANGE, 24.0);
        event.add(EntityType.CAVE_SPIDER, Attributes.FOLLOW_RANGE, 16.0);

        event.add(EntityType.CREEPER, Attributes.FOLLOW_RANGE, 20.0);
        event.add(EntityType.ZOGLIN, Attributes.FOLLOW_RANGE, 20.0);
        event.add(EntityType.ZOMBIFIED_PIGLIN, Attributes.FOLLOW_RANGE, 20.0);

        // ==========================================
        // 2. РОЗБІЙНИКИ
        // ==========================================
        event.add(EntityType.PILLAGER, Attributes.FOLLOW_RANGE, 45.0);
        event.add(EntityType.VINDICATOR, Attributes.FOLLOW_RANGE, 45.0);
        event.add(EntityType.EVOKER, Attributes.FOLLOW_RANGE, 25.0);
        event.add(EntityType.VEX, Attributes.FOLLOW_RANGE, 20.0);
        event.add(EntityType.RAVAGER, Attributes.FOLLOW_RANGE, 30.0);

        // ==========================================
        // 3. ПІГЛІНИ
        // ==========================================
        event.add(EntityType.PIGLIN, Attributes.FOLLOW_RANGE, 50.0);
        event.add(EntityType.PIGLIN_BRUTE, Attributes.FOLLOW_RANGE, 20.0);

        // ==========================================
        // 4. ІНШІ
        // ==========================================
        event.add(EntityType.IRON_GOLEM, Attributes.FOLLOW_RANGE, 30.0);
        event.add(EntityType.WITCH, Attributes.FOLLOW_RANGE, 25.0);

        // ==========================================
        // 4. НЕЗЕР
        // ==========================================
        event.add(EntityType.GHAST, Attributes.FOLLOW_RANGE, 70.0);
        event.add(EntityType.BLAZE, Attributes.FOLLOW_RANGE, 70.0);

    }
}