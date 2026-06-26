package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDronePushSuppressionMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$suppressDroneGroundPush(Entity other, CallbackInfo ci) {
		if (DroneSystem.shouldSuppressDroneEntityPush((Entity) (Object) this, other)) {
			ci.cancel();
		}
	}
}
