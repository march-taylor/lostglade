package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityAbsoluteInvisibilityCombatMixin {
	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void lg2$revealAbsoluteInvisibleAttackersToHitMob(
			ServerLevel level,
			DamageSource damageSource,
			float damage,
			CallbackInfoReturnable<Boolean> cir
	) {
		if (!cir.getReturnValueZ() || !((Object) this instanceof Mob mob) || !(damageSource.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		ServerAbsoluteInvisibilitySystem.revealMobToPlayer(mob, player);
	}
}
