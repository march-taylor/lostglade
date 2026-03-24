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
public abstract class LivingEntityCartelOwnerFriendlyFireMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void lg2$blockCartelOwnerDamage(
			ServerLevel level,
			DamageSource damageSource,
			float damage,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (ServerRaceSystem.shouldCancelCartelOwnerDamage((LivingEntity) (Object) this, damageSource)) {
			cir.setReturnValue(false);
		}
	}
}
