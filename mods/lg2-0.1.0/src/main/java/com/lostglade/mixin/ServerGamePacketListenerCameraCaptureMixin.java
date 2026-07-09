package com.lostglade.mixin;

import com.lostglade.server.CameraCaptureSystem;
import com.lostglade.server.SeasonStartSystem;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCameraCaptureMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleAnimate", at = @At("HEAD"), cancellable = true)
	private void lg2$captureCameraOnLeftClickAir(ServerboundSwingPacket packet, CallbackInfo ci) {
		SeasonStartSystem.onPlayerAnimate(this.player);
		if (CameraCaptureSystem.handleLeftClickAir(this.player, packet.getHand())) {
			ci.cancel();
		}
	}
}
