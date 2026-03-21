package com.lostglade.mixin;

import com.lostglade.entity.TrojanChickenAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Chicken.class)
public abstract class ChickenTrojanRoosterAmbientSoundMixin {
	@Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
	private void lg2$silenceTrojanRoosterAmbientSound(CallbackInfoReturnable<SoundEvent> cir) {
		if (((TrojanChickenAccess) this).lg2$isTrojanRooster()) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
	private void lg2$silenceTrojanRoosterDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
		if (((TrojanChickenAccess) this).lg2$isTrojanRooster()) {
			cir.setReturnValue(null);
		}
	}

	@Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
	private void lg2$silenceTrojanRoosterHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
		if (((TrojanChickenAccess) this).lg2$isTrojanRooster()) {
			cir.setReturnValue(null);
		}
	}
}
