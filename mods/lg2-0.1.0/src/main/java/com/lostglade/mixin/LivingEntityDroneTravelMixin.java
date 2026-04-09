package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDroneTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void lg2$useDroneTravel(Vec3 travelVector, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player) || !DroneSystem.isControllingDrone(player)) {
			return;
		}
		DroneSystem.applyControlledTravel(player);
		ci.cancel();
	}
}
