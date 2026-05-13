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
public abstract class LivingEntityGennadiyDefenseEffectMixin {
	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyDefenseEffect(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (ServerRaceSystem.shouldBlockGennadiyDefenseEffect((LivingEntity) (Object) this, effect)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyDefenseEffectWithSource(MobEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
		if (ServerRaceSystem.shouldBlockGennadiyDefenseEffect((LivingEntity) (Object) this, effect)) {
			cir.setReturnValue(false);
		}
	}
}
