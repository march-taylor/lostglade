package com.lostglade.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDroneProxyCollisionMixin {
	@Unique
	private static final double LG2_DRONE_WIDTH = 0.95D;
	@Unique
	private static final double LG2_DRONE_HEIGHT = 0.35D;

	@Inject(method = "move", at = @At("HEAD"))
	private void lg2$useDroneHitboxDuringMovement(MoverType moverType, Vec3 movement, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player) || !lg2$isDroneProxyActive(player)) {
			return;
		}
		lg2$alignDroneProxyHitbox(player);
	}

	@Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
	private void lg2$disableClosestSpacePushDuringDroneControl(double x, double y, double z, CallbackInfo ci) {
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

	@Unique
	private static void lg2$alignDroneProxyHitbox(LocalPlayer player) {
		double halfWidth = LG2_DRONE_WIDTH * 0.5D;
		Vec3 position = player.position();
		player.setBoundingBox(new AABB(
				position.x - halfWidth,
				position.y,
				position.z - halfWidth,
				position.x + halfWidth,
				position.y + LG2_DRONE_HEIGHT,
				position.z + halfWidth
		));
	}
}
