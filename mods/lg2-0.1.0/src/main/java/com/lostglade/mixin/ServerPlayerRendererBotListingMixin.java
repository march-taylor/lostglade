package com.lostglade.mixin;

import com.lostglade.server.RendererBotPresenceSystem;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRendererBotListingMixin {
	@Inject(method = "allowsListing", at = @At("HEAD"), cancellable = true)
	private void lg2$hideRendererBotFromPlayerList(CallbackInfoReturnable<Boolean> cir) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (RendererBotPresenceSystem.shouldHideFromPlayerList(player)) {
			cir.setReturnValue(false);
		}
	}
}
