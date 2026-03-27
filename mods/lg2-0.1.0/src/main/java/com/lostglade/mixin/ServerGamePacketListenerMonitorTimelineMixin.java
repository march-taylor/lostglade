package com.lostglade.mixin;

import com.lostglade.server.MonitorScreenSystem;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMonitorTimelineMixin {
	@Shadow
	@Final
	public ServerPlayer player;

	@Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
	private void lg2$redirectHotbarScrollToMonitorTimeline(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		if (packet != null && MonitorScreenSystem.onPlayerHotbarScroll(this.player, packet.getSlot())) {
			ci.cancel();
		}
	}
}
