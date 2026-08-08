package com.example.examplemod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_AI;

    public static final ModConfigSpec.BooleanValue ENABLE_CHANGE_MOBS_RANGE;

    public static final ModConfigSpec.BooleanValue ENABLE_MOB_TERRAFORMING;
    public static final ModConfigSpec.ConfigValue<String> OVERWORLD_DIG_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> NETHER_DIG_BLOCK;
    public static final ModConfigSpec.ConfigValue<String> END_DIG_BLOCK;
    public static final ModConfigSpec.IntValue PLACED_BLOCK_LIFETIME_SECONDS;
    public static final ModConfigSpec.BooleanValue PLACED_BLOCK_DROPS_WHEN_BROKEN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("Global_AI_Settings");

        ENABLE_CUSTOM_AI = builder
                .comment("Увімкнути/Вимкнути кастомний штучний інтелект (війну фракцій) (true/false)")
                .define("enableCustomAI", true);
        builder.pop();

        builder.push("Change_Mobs_Vision_Range");

        ENABLE_CHANGE_MOBS_RANGE = builder
                .comment("Увімкнути/Вимкнути кастомний радіус бачення мобів (true/false)")
                .define("enableSpecialFeature", true);
        builder.pop();

        builder.push("Mob_Terraforming");

        ENABLE_MOB_TERRAFORMING = builder
                .comment("Увімкнути/Вимкнути копання та будівництво мобами (true/false). ",
                        "Незалежно від цього значення, поважається ванільний геймрул mobGriefing — ",
                        "якщо він вимкнений на сервері, ця система теж не діятиме.")
                .define("enableMobTerraforming", true);

        OVERWORLD_DIG_BLOCK = builder
                .comment("ID блоку, який моби ставлять/копають у Звичайному світі.")
                .define("overworldDigBlock", "minecraft:cobblestone");
        NETHER_DIG_BLOCK = builder
                .comment("ID блоку, який моби ставлять/копають у Незері.")
                .define("netherDigBlock", "minecraft:nether_bricks");
        END_DIG_BLOCK = builder
                .comment("ID блоку, який моби ставлять/копають в Енді.")
                .define("endDigBlock", "minecraft:end_stone");

        PLACED_BLOCK_LIFETIME_SECONDS = builder
                .comment("Через скільки секунд блок, поставлений мобом (міст/вежа), зникає сам.")
                .defineInRange("placedBlockLifetimeSeconds", 30, 1, 24000);

        PLACED_BLOCK_DROPS_WHEN_BROKEN = builder
                .comment("Чи випадає предмет, якщо гравець зламає блок, поставлений мобом ",
                        "(поки той ще не зник сам) (true/false).")
                .define("placedBlockDropsWhenBroken", false);

        builder.pop();

        SPEC = builder.build();
    }
}