package com.lostglade.mixin;

import com.lostglade.server.CopperManRepulsorSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityCopperManRepulsorLightningMixin {
	@Inject(method = "thunderHit", at = @At("TAIL"))
	private void lg2$restoreRepulsorChargesOnNaturalLightning(ServerLevel level, LightningBolt lightningBolt, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (self instanceof ServerPlayer player) {
			CopperManRepulsorSystem.onNaturalLightningStrike(player, lightningBolt);
		}
	}
}
