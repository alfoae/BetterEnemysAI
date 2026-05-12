package com.example.examplemod.mobAi.Goal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PillagerWeaponManagerGoalAi extends Goal {
    private final Pillager pillager;

    public PillagerWeaponManagerGoalAi(Pillager pillager) {
        this.pillager = pillager;
    }

    @Override
    public boolean canUse() {
        return pillager.getTarget() != null; // Працює, коли є ворог
    }

    @Override
    public void tick() {
        double distSq = pillager.distanceToSqr(pillager.getTarget());
        boolean near = distSq <= 16.0; // 4 блоки

        if (near && !pillager.getMainHandItem().is(Items.IRON_AXE)) {
            pillager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        } else if (!near && !pillager.getMainHandItem().is(Items.CROSSBOW)) {
            pillager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
        }
    }
}