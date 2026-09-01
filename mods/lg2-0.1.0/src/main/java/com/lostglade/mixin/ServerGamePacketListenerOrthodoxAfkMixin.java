package com.lostglade.mixin;

import com.lostglade.server.OrthodoxHolinessSystem;
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
public abstract class ServerGamePacketListenerOrthodoxAfkMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerInput", at = @At("HEAD"))
	private void lg2$recordOrthodoxInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
		OrthodoxHolinessSystem.recordClientActivity(this.player);
	}

	@Inject(method = "handleMovePlayer", at = @At("HEAD"))
	private void lg2$recordOrthodoxCameraMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
		if (!packet.hasRotation()) return;
		float yaw = packet.getYRot(this.player.getYRot());
		float pitch = packet.getXRot(this.player.getXRot());
		if (Float.compare(yaw, this.player.getYRot()) != 0 || Float.compare(pitch, this.player.getXRot()) != 0) {
			OrthodoxHolinessSystem.recordClientActivity(this.player);
		}
	}

	@Inject(method = {
			"handlePlayerAction",
			"handleUseItemOn",
			"handleUseItem",
			"handlePaddleBoat",
			"handleSetCarriedItem",
			"handleChat",
			"handleChatCommand",
			"handleSignedChatCommand",
			"handleAnimate",
			"handlePlayerCommand",
			"handleInteract",
			"handleClientCommand",
			"handleContainerClose",
			"handleContainerClick",
			"handlePlaceRecipe",
			"handleContainerButtonClick",
			"handleSetCreativeModeSlot",
			"handleSignUpdate",
			"handlePlayerAbilities",
			"handleRenameItem",
			"handleSelectTrade",
			"handleEditBook"
	}, at = @At("HEAD"))
	private void lg2$recordOrthodoxIntentionalActivity(CallbackInfo ci) {
		OrthodoxHolinessSystem.recordClientActivity(this.player);
	}
}
