package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityAncientUkrGasMaskMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void lg2$removeDroppedAncientUkrGasMask(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (ServerRaceSystem.shouldBlockAncientUkrGasMaskDrop(self.getItem())) {
			self.discard();
			ci.cancel();
		}
	}
}