package com.lostglade.mixin;

import com.lostglade.server.CopperManGogglesSystem;
import com.lostglade.server.CopperManRepulsorSystem;
import com.lostglade.server.ServerRaceSystem;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
		if (ServerRaceSystem.shouldCancelMarkRangedUsePacket(this.player, packet.getHand())) {
			ci.cancel();
			return;
		}
		CopperManGogglesSystem.handleUseAirPacket(this.player, packet.getHand());
		if (CopperManGogglesSystem.shouldCancelUseItemPacket(this.player, packet.getHand())) {
			ci.cancel();
		}
	}

	@Inject(method = "handlePlayerAction", at = @At("HEAD"))
	private void lg2$handleCopperManGogglesReleaseUse(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		if (packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
			CopperManGogglesSystem.handleReleaseUsePacket(this.player);
			return;
		}
		if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			CopperManGogglesSystem.handleSwapWithOffhand(this.player);
		}
	}

	@Inject(method = "handlePlayerAction", at = @At("TAIL"))
	private void lg2$handleCopperManGogglesSwapOffhandApplied(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		if (packet == null || packet.getAction() != ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
			return;
		}
		CopperManGogglesSystem.handleSwapWithOffhandApplied(this.player);
	}

	@Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
	private void lg2$handleCopperManGogglesSelectedSlotChange(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		CopperManGogglesSystem.handleSelectedSlotChange(this.player, packet.getSlot());
	}

	@Inject(method = "handleSetCarriedItem", at = @At("TAIL"))
	private void lg2$handleCopperManGogglesSelectedSlotChangeApplied(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		CopperManGogglesSystem.handleSelectedSlotChangeApplied(this.player, packet.getSlot());
	}

	@ModifyVariable(
			method = "handleMovePlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket;getX(D)D",
					ordinal = 0
			),
			argsOnly = true
	)
	private ServerboundMovePlayerPacket lg2$lockLittleDictatorHorizontalMovement(ServerboundMovePlayerPacket packet) {
		return ServerRaceSystem.sanitizeKilkaSalmonMovementPacket(this.player, ServerRaceSystem.sanitizeKilkaStockLandMovementPacket(this.player, ServerRaceSystem.sanitizeLittleDictatorUniqueMovementPacket(this.player, packet)));
	}

	@Inject(method = "handleMovePlayer", at = @At("TAIL"))
	private void lg2$syncCopperManInvisibleTriggersOnMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		CopperManGogglesSystem.handleMovePacket(this.player);
		CopperManRepulsorSystem.handleMovePacket(this.player);
		ServerRaceSystem.handleMovePacket(this.player);
	}

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void lg2$forceGennadiyDefenseLookOnPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (ServerRaceSystem.handleMilkMouseMovePacket(this.player, packet)) {
			ci.cancel();
			return;
		}
		if (ServerRaceSystem.handleGennadiyDefenseMovementPacket(this.player)) {
			ci.cancel();
			return;
		}
		if (ServerRaceSystem.handleLittleDictatorUniqueMovementPacket(this.player, packet)) {
			ci.cancel();
		}
	}

	@Inject(method = "handleMoveVehicle", at = @At("TAIL"))
	private void lg2$syncGennadiyDonkeyTurretOnVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
		if (packet != null) {
			ServerRaceSystem.handleVehicleMovePacket(this.player, packet.yRot());
		} else {
			ServerRaceSystem.handleVehicleMovePacket(this.player);
		}
	}

	@Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
	private void lg2$forceGennadiyDefenseLookOnVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
		if (ServerRaceSystem.handleGennadiyDefenseMovementPacket(this.player)) {
			ci.cancel();
			return;
		}
		if (ServerRaceSystem.handleLittleDictatorUniqueVehicleMovePacket(this.player)) {
			ci.cancel();
		}
	}
}
