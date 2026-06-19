package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLittleDictatorPropagandaHasteMixin {
	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$captureLittleDictatorPropagandaHaste(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (ServerRaceSystem.handleLittleDictatorPropagandaIncomingHaste(self, effect)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$captureLittleDictatorPropagandaHasteWithSource(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (ServerRaceSystem.handleLittleDictatorPropagandaIncomingHaste(self, effect)) {
			cir.setReturnValue(true);
		}
	}
}
