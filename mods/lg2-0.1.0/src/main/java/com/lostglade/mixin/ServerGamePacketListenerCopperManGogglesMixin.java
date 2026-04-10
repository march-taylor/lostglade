package com.lostglade.mixin;

import com.lostglade.server.CopperManGogglesSystem;
import com.lostglade.server.CopperManRepulsorSystem;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ClickType;
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

	@Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
	private void lg2$handleCopperManGogglesInventoryRightClick(ServerboundContainerClickPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		if (packet.clickType() != ClickType.PICKUP) {
			return;
		}
		if (packet.buttonNum() != 0 && packet.buttonNum() != 1) {
			return;
		}
		if (!this.player.containerMenu.getCarried().isEmpty()) {
			return;
		}
		if (CopperManGogglesSystem.handleInventoryModeClick(this.player, packet.containerId(), packet.slotNum())) {
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

	@Inject(method = "handleMovePlayer", at = @At("TAIL"))
	private void lg2$syncCopperManInvisibleTriggersOnMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		CopperManGogglesSystem.handleMovePacket(this.player);
		CopperManRepulsorSystem.handleMovePacket(this.player);
	}
}
