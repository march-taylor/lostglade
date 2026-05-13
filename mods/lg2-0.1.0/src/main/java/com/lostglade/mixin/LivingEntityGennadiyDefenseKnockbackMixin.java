package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityGennadiyDefenseKnockbackMixin {
	@Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyDefenseKnockback(double strength, double x, double z, CallbackInfo ci) {
		if (ServerRaceSystem.shouldBlockGennadiyDefensePush((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
