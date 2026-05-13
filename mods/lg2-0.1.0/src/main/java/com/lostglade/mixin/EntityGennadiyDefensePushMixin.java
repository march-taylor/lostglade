package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityGennadiyDefensePushMixin {
	@Inject(method = "push(DDD)V", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyDefensePush(double x, double y, double z, CallbackInfo ci) {
		if (ServerRaceSystem.shouldBlockGennadiyDefensePush((Entity) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "push(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyDefenseVectorPush(Vec3 vector, CallbackInfo ci) {
		if (ServerRaceSystem.shouldBlockGennadiyDefensePush((Entity) (Object) this)) {
			ci.cancel();
		}
	}
}
