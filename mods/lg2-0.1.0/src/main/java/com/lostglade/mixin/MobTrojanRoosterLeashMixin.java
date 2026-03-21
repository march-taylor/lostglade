package com.lostglade.mixin;

import com.lostglade.entity.TrojanChickenAccess;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobTrojanRoosterLeashMixin {
	@Inject(method = "canBeLeashed", at = @At("HEAD"), cancellable = true)
	private void lg2$preventLeashingTrojanRooster(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Chicken chicken && ((TrojanChickenAccess) chicken).lg2$isTrojanRooster()) {
			cir.setReturnValue(false);
		}
	}
}
