package com.example.examplemod.mobAi.Goal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SwapWeaponGoal extends Goal {
    private final Monster mob;

    public SwapWeaponGoal(Monster mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        // Завдання працює, тільки поки є ціль
        return mob.getTarget() != null;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();

        // ЗАХИСТ ВІД КРАШУ: Якщо ціль раптово зникла, припиняємо роботу
        if (target == null) {
            return;
        }

        double distSq = mob.distanceToSqr(target);
        boolean near = distSq <= 16.0;

        if (near && !mob.getMainHandItem().is(Items.IRON_AXE)) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        } else if (!near && !mob.getMainHandItem().is(Items.CROSSBOW)) {
            mob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CROSSBOW));
        }

        // Робимо його агресивним (піднімає зброю)
        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        // Коли гравець тікає і моб втрачає ціль — він заспокоюється і ховає зброю
        mob.setAggressive(false);
    }
}