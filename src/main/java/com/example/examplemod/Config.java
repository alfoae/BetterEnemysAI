package com.example.examplemod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    // Головний об'єкт конфігу
    public static final ModConfigSpec SPEC;

    // ВИМИКАЧ ДЛЯ ШТУЧНОГО ІНТЕЛЕКТУ
    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_AI;

    // ЗМІННІ ДЛЯ РАДІУСІВ УСІХ МОБІВ
    public static final ModConfigSpec.DoubleValue ZOMBIE_RADIUS;
    public static final ModConfigSpec.DoubleValue HUSK_RADIUS;
    public static final ModConfigSpec.DoubleValue DROWNED_RADIUS;
    public static final ModConfigSpec.DoubleValue ZOMBIE_VILLAGER_RADIUS;
    public static final ModConfigSpec.DoubleValue SKELETON_RADIUS;
    public static final ModConfigSpec.DoubleValue STRAY_RADIUS;
    public static final ModConfigSpec.DoubleValue WITHER_SKELETON_RADIUS;
    public static final ModConfigSpec.DoubleValue SPIDER_RADIUS;
    public static final ModConfigSpec.DoubleValue CAVE_SPIDER_RADIUS;
    public static final ModConfigSpec.DoubleValue CREEPER_RADIUS;
    public static final ModConfigSpec.DoubleValue ZOGLIN_RADIUS;
    public static final ModConfigSpec.DoubleValue ZOMBIFIED_PIGLIN_RADIUS;
    public static final ModConfigSpec.DoubleValue PILLAGER_RADIUS;
    public static final ModConfigSpec.DoubleValue VINDICATOR_RADIUS;
    public static final ModConfigSpec.DoubleValue EVOKER_RADIUS;
    public static final ModConfigSpec.DoubleValue VEX_RADIUS;
    public static final ModConfigSpec.DoubleValue RAVAGER_RADIUS;
    public static final ModConfigSpec.DoubleValue PIGLIN_RADIUS;
    public static final ModConfigSpec.DoubleValue PIGLIN_BRUTE_RADIUS;
    public static final ModConfigSpec.DoubleValue IRON_GOLEM_RADIUS;
    public static final ModConfigSpec.DoubleValue WITCH_RADIUS;
    public static final ModConfigSpec.DoubleValue DEFAULT_RADIUS;


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        DEFAULT_RADIUS = builder
                .comment("Default aggro radius for all monsters")
                .defineInRange("defaultRadius", 20.0, 1.0, 256.0);

        builder.push("Global_AI_Settings");
        ENABLE_CUSTOM_AI = builder
                .comment("Увімкнути/Вимкнути кастомний штучний інтелект (війну фракцій)")
                .define("enableCustomAI", true);
        builder.pop();

        builder.push("Mob_Aggro_Radiuses");
        builder.comment("Налаштування радіусу (в блоках), на якому моби бачать ворогів");

        // Монстри
        ZOMBIE_RADIUS = builder.defineInRange("zombie", 30.0, 1.0, 128.0);
        HUSK_RADIUS = builder.defineInRange("husk", 40.0, 1.0, 128.0);
        DROWNED_RADIUS = builder.defineInRange("drowned", 25.0, 1.0, 128.0);
        ZOMBIE_VILLAGER_RADIUS = builder.defineInRange("zombie_villager", 30.0, 1.0, 128.0);
        SKELETON_RADIUS = builder.defineInRange("skeleton", 35.0, 1.0, 128.0);
        STRAY_RADIUS = builder.defineInRange("stray", 35.0, 1.0, 128.0);
        WITHER_SKELETON_RADIUS = builder.defineInRange("wither_skeleton", 30.0, 1.0, 128.0);
        SPIDER_RADIUS = builder.defineInRange("spider", 24.0, 1.0, 128.0);
        CAVE_SPIDER_RADIUS = builder.defineInRange("cave_spider", 16.0, 1.0, 128.0);
        CREEPER_RADIUS = builder.defineInRange("creeper", 20.0, 1.0, 128.0);
        ZOGLIN_RADIUS = builder.defineInRange("zoglin", 20.0, 1.0, 128.0);
        ZOMBIFIED_PIGLIN_RADIUS = builder.defineInRange("zombified_piglin", 20.0, 1.0, 128.0);

        // Розбійники
        PILLAGER_RADIUS = builder.defineInRange("pillager", 45.0, 1.0, 128.0);
        VINDICATOR_RADIUS = builder.defineInRange("vindicator", 20.0, 1.0, 128.0);
        EVOKER_RADIUS = builder.defineInRange("evoker", 25.0, 1.0, 128.0);
        VEX_RADIUS = builder.defineInRange("vex", 20.0, 1.0, 128.0);
        RAVAGER_RADIUS = builder.defineInRange("ravager", 30.0, 1.0, 128.0);

        // Пігліни
        PIGLIN_RADIUS = builder.defineInRange("piglin", 25.0, 1.0, 128.0);
        PIGLIN_BRUTE_RADIUS = builder.defineInRange("piglin_brute", 20.0, 1.0, 128.0);

        // Інші
        IRON_GOLEM_RADIUS = builder.defineInRange("iron_golem", 30.0, 1.0, 128.0);
        WITCH_RADIUS = builder.defineInRange("witch", 25.0, 1.0, 128.0);


        builder.pop();

        SPEC = builder.build();


    }
}