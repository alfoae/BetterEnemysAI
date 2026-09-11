package com.example.examplemod.mobAi.Goal;

import com.example.examplemod.Enemy.EnemyAttack.DrownedTridentAttack;
import com.example.examplemod.Enemy.EnemyBehavior.EnemyPursuit_N_Search.PursuitBehavior.PursuitEnemyBehavior;
import com.example.examplemod.Enemy.EnemyMovement.DrownedAmphibiousMovement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Goal-обгортка для дровнеда: перевіряє умови (ціль + тризубець у руці), щотіку рахує
 * {@code canSee}/{@code seeTime} і вирішує "сліпу гонитву" по пам'яті (chasePos) — це спільний
 * препроцесинг, потрібний і руху, і стрільбі, тому лишився тут, а не в одному з двох класів
 * нижче. Сам рух (вода/суша) — {@link DrownedAmphibiousMovement}, кидок тризубця —
 * {@link DrownedTridentAttack}.
 */
public class BetterDrownedGoalAi extends Goal {
    private final Drowned mob;
    private final DrownedAmphibiousMovement movement;
    private final DrownedTridentAttack attack;

    private int seeTime;

    public BetterDrownedGoalAi(Drowned mob, double speedModifier, int attackIntervalMin) {
        this.mob = mob;
        this.movement = new DrownedAmphibiousMovement(mob, speedModifier);
        this.attack = new DrownedTridentAttack(mob, attackIntervalMin);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.getTarget() != null && this.isHoldingTrident();
    }

    private boolean isHoldingTrident() {
        return this.mob.isHolding(is -> is.getItem() instanceof TridentItem);
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() || !this.mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        super.start();
        this.mob.setAggressive(true);
    }

    @Override
    public void stop() {
        super.stop();
        this.mob.setAggressive(false);
        this.seeTime = 0;
        this.attack.reset();
        this.mob.stopUsingItem();
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        double distanceSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean canSee = this.mob.getSensing().hasLineOfSight(target);
        boolean isSeeing = this.seeTime > 0;

        if (canSee != isSeeing) {
            this.seeTime = 0;
        }
        if (canSee) {
            ++this.seeTime;
        } else {
            --this.seeTime;
        }

        // Немає прямої видимості — йдемо по пам'яті (chasePos), а не напряму до живої (можливо
        // дуже далекої) позиції гравця. Інакше vanilla-навігатор не будує такий довгий шлях, і
        // утопленик просто стоїть на місці, дійшовши до застиглої точки замість шукати далі.
        Vec3 chasePos = PursuitEnemyBehavior.getChasePosition(this.mob);
        if (!canSee && chasePos != null) {
            if (this.mob.isUsingItem()) {
                this.mob.stopUsingItem(); // не тримаємо тризуб натягнутим, поки біжимо всліпу
            }
            this.mob.setSwimming(this.mob.isInWater());
            double sprintSpeed = PursuitEnemyBehavior.getSprintSpeedModifier(this.mob);
            this.mob.getNavigation().moveTo(chasePos.x, chasePos.y, chasePos.z, sprintSpeed);
            this.mob.getLookControl().setLookAt(
                    chasePos.x, chasePos.y + this.mob.getBbHeight() * 0.5, chasePos.z, 30.0F, 30.0F);
            this.mob.setSprinting(true);
            return;
        }
        this.mob.setSprinting(false);

        this.movement.tick(target, distanceSq, this.seeTime);
        this.attack.tick(target, distanceSq, canSee, this.seeTime);
    }
}
