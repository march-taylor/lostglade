package com.lostglade.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDroneProxyCollisionMixin {
	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void lg2$disableClosestSpacePushDuringDroneControl(double x, double z, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player) || !lg2$isDroneProxyActive(player)) {
			return;
		}
		ci.cancel();
	}

	@Unique
	private static boolean lg2$isDroneProxyActive(LocalPlayer player) {
		if (player == null || !player.isInvisible() || !player.isFallFlying() || !player.isNoGravity()) {
			return false;
		}
		for (Entity passenger : player.getPassengers()) {
			if (passenger != null && passenger.getType() == EntityType.INTERACTION) {
				return true;
			}
		}
		return false;
	}
}
