package com.lostglade.mixin;

import com.lostglade.server.PhotoFramePlacementSystem;
import com.lostglade.server.MonitorScreenSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFrame.class)
public abstract class ItemFramePhotoMixin {
	@Inject(
			method = "dropItem(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Z)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void lg2$dropLargePhoto(ServerLevel level, Entity breaker, boolean alwaysDrop, CallbackInfo ci) {
		if (MonitorScreenSystem.onFrameBroken(level, (ItemFrame) (Object) this, breaker, alwaysDrop)) {
			ci.cancel();
			return;
		}
		PhotoFramePlacementSystem.onFrameBroken(level, (ItemFrame) (Object) this, breaker, alwaysDrop);
	}
}
