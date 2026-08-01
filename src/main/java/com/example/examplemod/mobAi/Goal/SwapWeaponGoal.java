package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.utils.IWeaponStorage;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class SwapWeaponGoal extends Goal {

    // Було ОДНЕ значення (distSq <= 16.0, тобто 4 блоки) для обох напрямків — та сама конструкція,
    // що спричиняла смикання рук/анімації натягу в пігліна (див. PiglinSwapWeaponBehavior): коли
    // дистанція гойдається біля цієї межі, зброя перемикається щотіку, і кожне перемикання
    // ГЕТЬ від арбалета скидає натяг, а кожне НАЗАД - починає натягувати заново. Два пороги з
    // запасом ("нейтральна зона" 9-25, тобто 3-5 блоків, де зброя НЕ перемикається) це усувають.
    private static final double SWITCH_TO_MELEE_DIST_SQ = 9.0;   // 3 блоки - ближче цієї = точно сокира
    private static final double SWITCH_TO_RANGED_DIST_SQ = 25.0; // 5 блоків - далі цієї = точно арбалет

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

        // Кастимо моба до нашого міксіна-сховища
        IWeaponStorage storage = (IWeaponStorage) mob;
        ItemStack current = mob.getMainHandItem();

        if (distSq <= SWITCH_TO_MELEE_DIST_SQ && !current.is(Items.IRON_AXE)) {
            // Зберігаємо арбалет, дістаємо сокиру
            storage.setStoredRanged(current.copy());

            ItemStack nextMelee = storage.getStoredMelee();
            if (nextMelee.isEmpty()) nextMelee = new ItemStack(Items.IRON_AXE);

            // ВАЖЛИВО: stopUsingItem() ПЕРЕД зміною предмета в руці — та сама причина, що й у
            // PiglinSwapWeaponBehavior: інакше isUsingItem() лишається true (прив'язаним до
            // арбалета, якого вже нема в руці), і ванільна меле-атака відмовляється атакувати,
            // поки моб "зайнятий" — виглядає так, ніби ігнорує гравця впритул.
            mob.stopUsingItem();
            mob.setItemInHand(InteractionHand.MAIN_HAND, nextMelee);
        } else if (distSq >= SWITCH_TO_RANGED_DIST_SQ && !current.is(Items.CROSSBOW)) {
            // Зберігаємо сокиру, дістаємо арбалет
            storage.setStoredMelee(current.copy());

            ItemStack nextRanged = storage.getStoredRanged();
            if (nextRanged.isEmpty()) nextRanged = new ItemStack(Items.CROSSBOW);

            mob.stopUsingItem();
            mob.setItemInHand(InteractionHand.MAIN_HAND, nextRanged);
        }
        // інакше (3-5 блоків) - нейтральна зона, навмисно нічого не робимо, лишаємо поточну зброю

        mob.setAggressive(true);
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
    }
}