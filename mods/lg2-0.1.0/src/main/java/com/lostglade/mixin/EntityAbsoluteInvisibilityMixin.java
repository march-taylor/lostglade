package com.lostglade.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityAbsoluteInvisibilityMixin {
	@Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
	private void lg2$cancelSprintParticleCheckForAbsoluteInvisibility(CallbackInfoReturnable<Boolean> cir) {
		if (lg2$hasHiddenInvisibility()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "spawnSprintParticle", at = @At("HEAD"), cancellable = true)
	private void lg2$cancelSprintParticlesForAbsoluteInvisibility(CallbackInfo ci) {
		if (lg2$hasHiddenInvisibility()) {
			ci.cancel();
		}
	}

	private boolean lg2$hasHiddenInvisibility() {
		Entity entity = (Entity) (Object) this;
		if (!(entity instanceof LivingEntity living)) {
			return false;
		}

		MobEffectInstance effect = living.getEffect(MobEffects.INVISIBILITY);
		return effect != null && !effect.isVisible();
	}
}
