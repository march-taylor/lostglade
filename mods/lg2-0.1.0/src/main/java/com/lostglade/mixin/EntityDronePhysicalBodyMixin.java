package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityDronePhysicalBodyMixin {
	private static final String DRONE_ROOT_TAG = "lg2_drone_root";

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void lg2$makeDroneRootPushable(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (lg2$isDroneRoot(self)) {
			cir.setReturnValue(DroneSystem.shouldDroneRootCollideWithEntities(self));
		}
	}

	@Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
	private void lg2$makeDroneRootPistonPushable(CallbackInfoReturnable<PushReaction> cir) {
		if (lg2$isDroneRoot()) {
			cir.setReturnValue(PushReaction.NORMAL);
		}
	}

	@Inject(method = "isAffectedByBlocks", at = @At("HEAD"), cancellable = true)
	private void lg2$makeDroneRootAffectedByBlocks(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$isDroneRoot()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
	private void lg2$makeDroneRootCollideWithEntities(Entity other, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (lg2$isDroneRoot(self)) {
			cir.setReturnValue(DroneSystem.shouldDroneRootCollideWithEntities(self) && (other == null || !other.isSpectator()));
			return;
		}
		if (lg2$isDroneRoot(other)) {
			cir.setReturnValue(DroneSystem.shouldDroneRootCollideWithEntities(other) && !self.isSpectator());
		}
	}

	@Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
	private void lg2$makeDroneRootReceiveEntityCollisions(Entity other, CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		if (lg2$isDroneRoot(self)) {
			cir.setReturnValue(DroneSystem.shouldDroneRootCollideWithEntities(self) && (other == null || !other.isSpectator()));
		}
	}

	private boolean lg2$isDroneRoot() {
		return lg2$isDroneRoot((Entity) (Object) this);
	}

	private static boolean lg2$isDroneRoot(Entity entity) {
		return entity != null && entity.getTags().contains(DRONE_ROOT_TAG);
	}
}
