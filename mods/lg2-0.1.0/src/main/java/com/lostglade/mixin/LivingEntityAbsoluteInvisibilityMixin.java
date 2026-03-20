package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAbsoluteInvisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("HEAD"), cancellable = true)
	private void lg2$forceZeroVisibilityAgainstMobs(Entity observer, CallbackInfoReturnable<Double> cir) {
		if (!(observer instanceof Mob mob)
				|| !ServerAbsoluteInvisibilitySystem.shouldSuppressMobDetection(mob, (LivingEntity) (Object) this)) {
			return;
		}

		cir.setReturnValue(0.0D);
	}
}
