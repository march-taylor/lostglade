package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMarkStockHealingMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void lg2$blockMarkStockHealing(float amount, CallbackInfo ci) {
		ServerRaceSystem.copyPuroSanOverdriveHealing((LivingEntity) (Object) this, amount);
		if (amount > 0.0F && ServerRaceSystem.shouldBlockMarkStockHealing((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
