package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPaddleBoatPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerLittleDictatorLagMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerInput", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handlePlayerInput(packet), ci);
	}

	@Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleMovePlayer(packet), ci);
	}

	@Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorVehicleMove(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleMoveVehicle(packet), ci);
	}

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleUseItem(packet), ci);
	}

	@Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleUseItemOn(packet), ci);
	}

	@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleInteract(packet), ci);
	}

	@Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handlePlayerAction(packet), ci);
	}

	@Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorSetCarriedItem(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleSetCarriedItem(packet), ci);
	}

	@Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorAnimate(ServerboundSwingPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleAnimate(packet), ci);
	}

	@Inject(method = "handlePlayerCommand", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handlePlayerCommand(packet), ci);
	}

	@Inject(method = "handlePaddleBoat", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorPaddle(ServerboundPaddleBoatPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handlePaddleBoat(packet), ci);
	}

	@Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorContainerClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleContainerClick(packet), ci);
	}

	@Inject(method = "handleContainerButtonClick", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorContainerButton(ServerboundContainerButtonClickPacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleContainerButtonClick(packet), ci);
	}

	@Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
	private void lg2$delayLittleDictatorContainerClose(ServerboundContainerClosePacket packet, CallbackInfo ci) {
		delay(packet, () -> ((ServerGamePacketListenerImpl) (Object) this).handleContainerClose(packet), ci);
	}

	private void delay(Object packet, Runnable replay, CallbackInfo ci) {
		if (packet == null || replay == null || ci == null) {
			return;
		}
		if (ServerRaceSystem.delayLittleDictatorServerboundPacket(this.player, replay)) {
			ci.cancel();
		}
	}
}
