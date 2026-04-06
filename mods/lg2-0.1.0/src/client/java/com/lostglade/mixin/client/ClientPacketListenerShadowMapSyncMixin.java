package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotShadowWorldManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerShadowMapSyncMixin {
	@Inject(method = "handleMapItemData", at = @At("TAIL"))
	private void lg2$syncMapDataIntoShadowWorlds(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
		RendererBotShadowWorldManager.onMapDataUpdated((ClientPacketListener) (Object) this, packet);
	}
}
