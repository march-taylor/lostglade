package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
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

	@Inject(method = "handlePlayerInput", at = @At("HEAD"))
	private void lg2$trackDroneInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
		if (packet == null) {
			return;
		}
		DroneSystem.handleInput(this.player, packet.input());
	}
}
