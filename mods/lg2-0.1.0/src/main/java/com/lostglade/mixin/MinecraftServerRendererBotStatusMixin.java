package com.lostglade.mixin;

import com.lostglade.server.RendererBotPresenceSystem;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerRendererBotStatusMixin {
	@Inject(method = "getStatus", at = @At("RETURN"), cancellable = true)
	private void lg2$hideRendererBotFromServerStatus(CallbackInfoReturnable<ServerStatus> cir) {
		cir.setReturnValue(RendererBotPresenceSystem.sanitizeStatus(cir.getReturnValue()));
	}
}
