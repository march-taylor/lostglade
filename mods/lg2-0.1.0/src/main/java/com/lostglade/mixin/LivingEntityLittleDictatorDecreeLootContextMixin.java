package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityLittleDictatorDecreeLootContextMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
	private void lg2$beginLittleDictatorDeathLoot(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
		ServerRaceSystem.beginLittleDictatorDeathLootContext(level, (LivingEntity) (Object) this, damageSource);
	}

	@Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
	private void lg2$endLittleDictatorDeathLoot(ServerLevel level, DamageSource damageSource, CallbackInfo ci) {
		ServerRaceSystem.endLittleDictatorLootContext();
	}
}
