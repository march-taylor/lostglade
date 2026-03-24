package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCartelDefenseMixin {
	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void lg2$reflectDamageToCartelAttackers(
			ServerLevel level,
			DamageSource damageSource,
			float damage,
			CallbackInfoReturnable<Boolean> cir
	) {
		ServerRaceSystem.handleCartelDefenseDamage(level, (LivingEntity) (Object) this, damageSource, damage, cir.getReturnValueZ());
	}
}

