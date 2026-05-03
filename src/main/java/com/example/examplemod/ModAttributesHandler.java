package com.example.examplemod;

import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

public class ModAttributesHandler {
    public static void onAttributeModify(EntityAttributeModificationEvent event) {

        double radius = Config.DEFAULT_RADIUS.get();

        event.getTypes().forEach(entityType -> {
            if (entityType.getCategory() == MobCategory.MONSTER) {

                event.add(entityType, Attributes.FOLLOW_RANGE, radius);

            }
        });
    }
}