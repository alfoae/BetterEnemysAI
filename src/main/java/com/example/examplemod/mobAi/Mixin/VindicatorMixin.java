package com.example.examplemod.mobAi.Mixin;

import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyBehavior;
import com.example.examplemod.EnemyBehavior.EnemyAttack.PursuitEnemyMeleeBehavior;
import com.example.examplemod.mobAi.Goal.BetterPillagerVindicatorGoalAi;
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

        // Спочатку прибираємо ВАНІЛЬНИЙ MeleeAttackGoal (без перевірки на сокиру),
        // інакше він конфліктуватиме з нашим кастомним нижче. Важливо: робимо це
        // ДО addGoal(3, new PursuitEnemyMeleeBehavior(...)), щоб не знести свій же гоал.
        this.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof MeleeAttackGoal
        );

        this.goalSelector.addGoal(0, new PursuitEnemyBehavior(mob, true, 1.0));
        this.goalSelector.addGoal(1, new SwapWeaponGoal(mob));
        this.goalSelector.addGoal(2, new BetterPillagerVindicatorGoalAi(mob));
        this.goalSelector.addGoal(3, new PursuitEnemyMeleeBehavior(mob, 1.2D,
                m -> m.getMainHandItem().is(Items.IRON_AXE)));
    }

    @Override
    public AbstractIllager.IllagerArmPose getArmPose() {
        if (this.getMainHandItem().is(Items.CROSSBOW)) {
            if (this.isUsingItem()) {
                return AbstractIllager.IllagerArmPose.CROSSBOW_CHARGE;
            }
            if (this.isAggressive()) {
                return AbstractIllager.IllagerArmPose.CROSSBOW_HOLD;
            }
        } else if (this.getMainHandItem().is(Items.IRON_AXE)) {
            if (this.isAggressive()) {
                return AbstractIllager.IllagerArmPose.ATTACKING;
            }
        }
        return AbstractIllager.IllagerArmPose.CROSSED;
    }
}
