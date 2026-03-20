package com.lostglade.mixin.client;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.player.LocalPlayer")
public abstract class LocalPlayerAbsoluteInvisibilityMixin {
	@Inject(method = "canSpawnSprintParticle", at = @At("HEAD"), cancellable = true)
	private void lg2$cancelSprintParticleCheckForAbsoluteInvisibility(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity player = (LivingEntity) (Object) this;
		MobEffectInstance effect = player.getEffect(MobEffects.INVISIBILITY);
		if (effect != null && !effect.isVisible()) {
			cir.setReturnValue(false);
		}
	}
}
