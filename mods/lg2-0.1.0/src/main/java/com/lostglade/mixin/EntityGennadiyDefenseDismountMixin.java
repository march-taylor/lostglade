package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityGennadiyDefenseDismountMixin {
	@Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
	private void lg2$blockGennadiyDefenseFsitDismount(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && ServerRaceSystem.shouldBlockGennadiyDefenseDismount(player)) {
			ci.cancel();
		}
	}
}
