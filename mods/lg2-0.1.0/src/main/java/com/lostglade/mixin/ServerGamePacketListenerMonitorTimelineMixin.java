package com.lostglade.mixin;

import com.lostglade.server.MonitorScreenSystem;
import com.lostglade.server.SpeakerSystem;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMonitorTimelineMixin {
	@Shadow
	@Final
	public ServerPlayer player;

	@Unique
	private int lg2$previousCarriedSlot;

	@Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
	private void lg2$capturePreviousCarriedSlot(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		this.lg2$previousCarriedSlot = this.player.getInventory().getSelectedSlot();
	}

	@Inject(method = "handleSetCarriedItem", at = @At("TAIL"))
	private void lg2$mirrorHotbarScrollToMonitorTimeline(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		int currentSlot = this.player.getInventory().getSelectedSlot();
		if (currentSlot != this.lg2$previousCarriedSlot) {
			if (!SpeakerSystem.onPlayerHotbarScroll(this.player, this.lg2$previousCarriedSlot, currentSlot)) {
				MonitorScreenSystem.onPlayerHotbarScroll(this.player, this.lg2$previousCarriedSlot, currentSlot);
			}
		}
	}
}
