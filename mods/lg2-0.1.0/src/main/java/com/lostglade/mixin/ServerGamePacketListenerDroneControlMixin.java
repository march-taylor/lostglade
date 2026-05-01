package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerDroneControlMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
	private void lg2$trackDroneInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		DroneSystem.handleInput(this.player, packet.input());
		if (DroneSystem.isControllingDrone(this.player)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void lg2$redirectDroneOperatorMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		if (DroneSystem.shouldSuppressPostControlMovePacket(this.player, packet)) {
			ci.cancel();
			return;
		}
		if (!DroneSystem.isControllingDrone(this.player)) {
			return;
		}
		DroneSystem.handleControlledMovePacket(this.player, packet);
		ci.cancel();
	}

	@Inject(method = "handleAcceptTeleportPacket", at = @At("HEAD"), cancellable = true)
	private void lg2$consumeDroneProxyTeleportAck(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
		if (packet == null || !DroneSystem.isControllingDrone(this.player)) {
			return;
		}
		if (DroneSystem.handleControlledAcceptTeleportPacket(this.player, packet.getId())) {
			ci.cancel();
		}
	}
}
