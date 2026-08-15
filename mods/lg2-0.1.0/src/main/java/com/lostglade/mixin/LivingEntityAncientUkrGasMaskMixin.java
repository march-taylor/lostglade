package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAncientUkrGasMaskMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void lg2$blockAncientUkrGasDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (ServerRaceSystem.shouldBlockAncientUkrGasDamage((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}
}