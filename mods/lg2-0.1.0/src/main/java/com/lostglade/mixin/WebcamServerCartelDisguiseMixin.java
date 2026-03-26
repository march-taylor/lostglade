package com.lostglade.mixin;

import com.lostglade.server.CartelWebcamBridge;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dimaskama.webcam.server.C2SPacket;
import ru.dimaskama.webcam.server.WebcamServer;

@Mixin(value = WebcamServer.class, remap = false)
public abstract class WebcamServerCartelDisguiseMixin {
	@Inject(
			method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lru/dimaskama/webcam/server/C2SPacket;)V",
			at = @At("HEAD"),
			cancellable = true,
			remap = false
	)
	private void lg2$handleCartelDisguiseWebcam(ChannelHandlerContext context, C2SPacket packet, CallbackInfo ci) {
		if (CartelWebcamBridge.handleIncomingPacket((WebcamServer) (Object) this, packet)) {
			ci.cancel();
		}
	}
}
