package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.mobAi.Goal.CustomCrossbowShootGoal;
import com.example.examplemod.mobAi.Goal.SwapWeaponGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Vindicator.class)
public abstract class VindicatorMixin extends AbstractIllager {

    protected VindicatorMixin(EntityType<? extends AbstractIllager> type, Level level) {
        super(type, level);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void addDualModeAI(CallbackInfo ci) {
        Vindicator mob = (Vindicator) (Object) this;

        // 1. Зміна зброї (залежно від дистанції)
        this.goalSelector.addGoal(1, new SwapWeaponGoal(mob));

        // 2. Стрільба з арбалета на випередження
        this.goalSelector.addGoal(2, new CustomCrossbowShootGoal(mob));

        // 3. Ближній бій (тільки якщо в руках сокира)
        this.goalSelector.addGoal(3, new MeleeAttackGoal(mob, 1.2D, false) {
            @Override
            public boolean canUse() {
                return super.canUse() && mob.getMainHandItem().is(Items.IRON_AXE);
            }
        });
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        // 1. ЛОГІКА ДЛЯ АРБАЛЕТА
        if (this.getMainHandItem().is(Items.CROSSBOW)) {
            if (this.isUsingItem()) {
                // Поки він реально натягує тятиву (наші 25 тіків)
                return AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE;
            }
            if (this.isAggressive()) {
                // Коли він уже зарядив і просто цілиться перед пострілом
                return AbstractIllager.IllagerArmPose.CROSSBOW_HOLD;
            }
        }

        // 2. ЛОГІКА ДЛЯ СОКИРИ (Щоб не була біля пуза)
        else if (this.getMainHandItem().is(Items.IRON_AXE)) {
            if (this.isAggressive()) {
                // Це змусить його підняти руку з сокирою вгору для удару (як у ванілі)
                return AbstractIllager.IllagerArmPose.ATTACKING;
            }
        }

        // 3. СТАН СПОКОЮ (схрещені руки)
        return AbstractIllager.IllagerArmPose.CROSSED;
    }
}