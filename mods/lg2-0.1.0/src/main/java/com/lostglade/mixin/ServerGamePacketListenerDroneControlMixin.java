package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import com.lostglade.server.DroneInteractionDispatcher;
import com.lostglade.server.SeasonStartSystem;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
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

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void lg2$fireControlledDroneTurretOnUseAir(ServerboundUseItemPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		if (DroneSystem.handleControlledUseItem(this.player, packet.getHand())
				|| DroneSystem.isControllingDrone(this.player)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
	private void lg2$fireControlledDroneTurretOnInteraction(ServerboundInteractPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		if (this.player.level() instanceof ServerLevel level
				&& SeasonStartSystem.shouldBlockEntityInteraction(this.player, packet.getTarget(level))) {
			ci.cancel();
			return;
		}
		if (DroneInteractionDispatcher.handle(this.player, packet)) {
			ci.cancel();
		}
	}

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void lg2$blockVanillaActionsWhileControllingDrone(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet == null || !DroneSystem.isControllingDrone(this.player)) {
			return;
		}
		DroneSystem.handleControlledPlayerAction(this.player, packet);
		ci.cancel();
	}

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
		if (packet == null) {
			return;
		}
		if (DroneSystem.shouldConsumeDroneProxyTeleportAck(this.player, packet.getId())) {
			ci.cancel();
		}
	}
}
