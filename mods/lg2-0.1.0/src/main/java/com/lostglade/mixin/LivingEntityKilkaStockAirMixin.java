package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityKilkaStockAirMixin {
	@Unique
	private int lg2$kilkaStockAirBeforeTick;

	@Inject(method = "baseTick", at = @At("HEAD"))
	private void lg2$captureKilkaStockAir(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		this.lg2$kilkaStockAirBeforeTick = self.getAirSupply();
	}

	@Inject(method = "baseTick", at = @At("TAIL"))
	private void lg2$applyKilkaStockAir(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			ServerRaceSystem.handleKilkaStockAirTick(player, this.lg2$kilkaStockAirBeforeTick);
		}
	}
}