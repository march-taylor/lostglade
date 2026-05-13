package com.lostglade.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCartelSummonLootMixin {
	private static final String CARTEL_SUMMON_TAG = "lg2.cartel_summon";
	private static final String CARTEL_LAWYER_TAG = "lg2.cartel_lawyer";
	private static final String GENNADIY_DONKEY_TAG = "lg2.gennadiy_battle_donkey";

	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
	private void lg2$disableCartelSummonLoot(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity.getTags().contains(CARTEL_SUMMON_TAG)
				|| entity.getTags().contains(CARTEL_LAWYER_TAG)
				|| entity.getTags().contains(GENNADIY_DONKEY_TAG)) {
			ci.cancel();
		}
	}
}
