package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownSplashPotion.class)
public abstract class AncientUkrSplashPotionMixin {
	@Inject(method = "onHitAsPotion", at = @At("HEAD"))
	private void lg2$beginAncientUkrGasContext(ServerLevel level, ItemStack stack, HitResult hitResult, CallbackInfo ci) {
		ServerRaceSystem.beginAncientUkrGasEffectContext();
	}

	@Inject(method = "onHitAsPotion", at = @At("RETURN"))
	private void lg2$endAncientUkrGasContext(ServerLevel level, ItemStack stack, HitResult hitResult, CallbackInfo ci) {
		ServerRaceSystem.endAncientUkrGasEffectContext();
	}
}