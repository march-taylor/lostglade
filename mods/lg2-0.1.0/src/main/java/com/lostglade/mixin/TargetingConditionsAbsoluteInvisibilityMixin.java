package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetingConditions.class)
public abstract class TargetingConditionsAbsoluteInvisibilityMixin {
	@Inject(method = "test", at = @At("HEAD"), cancellable = true)
	private void lg2$blockMobTargetingAbsoluteInvisiblePlayers(
			ServerLevel level,
			LivingEntity observer,
			LivingEntity target,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!(observer instanceof Mob mob) || !ServerAbsoluteInvisibilitySystem.shouldSuppressMobDetection(mob, target)) {
			return;
		}

		cir.setReturnValue(false);
	}
}
