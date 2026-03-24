package com.lostglade.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityCartelNoSaveMixin {
	private static final String CARTEL_SUMMON_TAG = "lg2.cartel_summon";
	private static final String CARTEL_LAWYER_TAG = "lg2.cartel_lawyer";
	private static final String CARTEL_LAWYER_MARKER_NAME = "lg2_cartel_lawyer_marker";

	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void lg2$preventSavingCartelTemporaryEntities(CallbackInfoReturnable<Boolean> cir) {
		Entity self = (Entity) (Object) this;
		boolean markedLawyer = self.getTags().contains(CARTEL_LAWYER_TAG)
				|| self.hasCustomName() && CARTEL_LAWYER_MARKER_NAME.equals(self.getCustomName().getString());
		if (self.getTags().contains(CARTEL_SUMMON_TAG) || markedLawyer) {
			cir.setReturnValue(false);
		}
	}
}
