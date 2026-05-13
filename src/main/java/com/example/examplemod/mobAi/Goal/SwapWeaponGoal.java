package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.IWeaponStorage;
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
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        double distSq = mob.distanceToSqr(target);
        boolean near = distSq <= 16.0;

        // Кастимо моба до нашого міксіна-сховища
        IWeaponStorage storage = (IWeaponStorage) mob;
        ItemStack current = mob.getMainHandItem();

        if (near && !current.is(Items.IRON_AXE)) {
            // Зберігаємо арбалет, дістаємо сокиру
            storage.setStoredRanged(current.copy());
            ItemStack next = storage.getStoredMelee();

            ItemStack nextMelee = storage.getStoredMelee();
            if (nextMelee.isEmpty()) nextMelee = new ItemStack(Items.IRON_AXE);

            mob.setItemInHand(InteractionHand.MAIN_HAND, nextMelee);
        } else if (!near && !current.is(Items.CROSSBOW)) {
            // Зберігаємо сокиру, дістаємо арбалет
            storage.setStoredMelee(current.copy());

            ItemStack nextRanged = storage.getStoredRanged();
            if (nextRanged.isEmpty()) nextRanged = new ItemStack(Items.CROSSBOW);

            mob.setItemInHand(InteractionHand.MAIN_HAND, nextRanged);
        }

        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
    }
}