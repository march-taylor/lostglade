package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import com.lostglade.server.SeasonStartSystem;
import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityDronePushSuppressionMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$suppressDroneGroundPush(Entity other, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (SeasonStartSystem.shouldBlockEntityPush(self, other)
				|| ServerRaceSystem.handleKilkaSalmonVisualBodyPush(self, other)
				|| DroneSystem.shouldSuppressDroneEntityPush(self, other)) {
			ci.cancel();
		}
	}
}
