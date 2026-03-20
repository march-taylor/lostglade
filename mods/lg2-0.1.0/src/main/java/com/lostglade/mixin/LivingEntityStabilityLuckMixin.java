package com.lostglade.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityStabilityLuckMixin {
	@Inject(method = "getAttributeValue", at = @At("RETURN"), cancellable = true)
	private void lg2$neutralizeUnluckPenaltyForStability(Holder<Attribute> attribute, CallbackInfoReturnable<Double> cir) {
		if (!attribute.equals(Attributes.LUCK)) {
			return;
		}

		LivingEntity self = (LivingEntity) (Object) this;
		MobEffectInstance unluck = self.getEffect(MobEffects.UNLUCK);
		if (unluck == null) {
			return;
		}

		cir.setReturnValue(cir.getReturnValueD() + (double) (unluck.getAmplifier() + 1));
	}
}
