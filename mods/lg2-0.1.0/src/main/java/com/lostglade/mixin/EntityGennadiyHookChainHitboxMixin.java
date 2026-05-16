package com.lostglade.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityGennadiyHookChainHitboxMixin {
	private static final String GENNADIY_HOOK_CHAIN_TAG = "lg2.gennadiy_hook_chain";
	private static final EntityDimensions ZERO_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);

	@Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
	private void lg2$zeroGennadiyHookChainDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (lg2$isGennadiyHookChain()) {
			cir.setReturnValue(ZERO_DIMENSIONS);
		}
	}

	@Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
	private void lg2$zeroGennadiyHookChainBoundingBox(Vec3 position, CallbackInfoReturnable<AABB> cir) {
		if (lg2$isGennadiyHookChain()) {
			cir.setReturnValue(new AABB(position.x, position.y, position.z, position.x, position.y, position.z));
		}
	}

	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyHookChainPicking(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$isGennadiyHookChain()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyHookChainPush(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$isGennadiyHookChain()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyHookChainProjectileHit(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$isGennadiyHookChain()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyHookChainAttack(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$isGennadiyHookChain()) {
			cir.setReturnValue(false);
		}
	}

	private boolean lg2$isGennadiyHookChain() {
		return ((Entity) (Object) this).getTags().contains(GENNADIY_HOOK_CHAIN_TAG);
	}
}
