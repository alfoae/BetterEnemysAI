package com.example.examplemod.mobAi.Mixin;

import net.minecraft.world.entity.monster.piglin.PiglinBruteAi;
import org.spongepowered.asm.mixin.Mixin;

// TODO: PiglinBrute AI (переслідування+атака) відкладено — переробимо після того, як
// доробимо Goal-based мобів, окремо під Brain (не через goalSelector).
@Mixin(PiglinBruteAi.class)
public class PiglinBruteMixin {
}
