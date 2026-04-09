package com.lostglade.mixin;

import com.lostglade.server.CopperManGogglesSystem;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCopperManGogglesMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void lg2$handleCopperManGogglesAirUse(ServerboundUseItemPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		CopperManGogglesSystem.handleUseAirPacket(this.player, packet.getHand());
		if (CopperManGogglesSystem.shouldCancelUseItemPacket(this.player, packet.getHand())) {
			ci.cancel();
		}
	}

	@Inject(method = "handlePlayerAction", at = @At("HEAD"))
	private void lg2$handleCopperManGogglesReleaseUse(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet == null || packet.getAction() != ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
			return;
		}
		CopperManGogglesSystem.handleReleaseUsePacket(this.player);
	}
}
