package com.example.examplemod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_AI;

    public static final ModConfigSpec.BooleanValue ENABLE_CHANGE_MOBS_RANGE;

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

        SPEC = builder.build();
    }
}