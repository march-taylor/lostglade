package com.lostglade.mixin;

import com.lostglade.server.CopperManRepulsorSystem;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCopperManRepulsorDeathMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void lg2$resetCopperManRepulsorChargesOnDeath(DamageSource damageSource, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer player) {
			CopperManRepulsorSystem.onPlayerDeath(player);
		}
	}
}
