package com.lostglade.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Interaction.class)
public abstract class InteractionDronePhysicalBodyMixin {
	private static final String DRONE_ROOT_TAG = "lg2_drone_root";

	@Inject(method = "getPistonPushReaction", at = @At("HEAD"), cancellable = true)
	private void lg2$makeDroneRootPistonPushable(CallbackInfoReturnable<PushReaction> cir) {
		if (lg2$isDroneRoot()) {
			cir.setReturnValue(PushReaction.NORMAL);
		}
	}

	@Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
	private void lg2$allowDroneRootBlockTriggers(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$isDroneRoot()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
	private void lg2$allowDroneRootProjectileHits(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$isDroneRoot()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void lg2$tickDroneRootAsPhysicalEntity(CallbackInfo ci) {
		if (!lg2$isDroneRoot()) {
			return;
		}
		((Entity) (Object) this).baseTick();
		ci.cancel();
	}

	private boolean lg2$isDroneRoot() {
		return ((Entity) (Object) this).getTags().contains(DRONE_ROOT_TAG);
	}
}
