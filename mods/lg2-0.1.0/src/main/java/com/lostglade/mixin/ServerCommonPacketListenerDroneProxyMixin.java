package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerDroneProxyMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$rewriteDroneProxyPackets(Packet<?> packet, CallbackInfo ci) {
		lg2$processDroneProxyPacket(packet, null, ci);
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$rewriteDroneProxyPacketsWithListener(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		lg2$processDroneProxyPacket(packet, listener, ci);
	}

	private void lg2$processDroneProxyPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		if (DroneSystem.isOutgoingControlledOperatorPacketRewriteBypassed()) {
			return;
		}

		Object self = this;
		if (!(self instanceof ServerGamePacketListenerImpl gameListener)) {
			return;
		}

		ServerPlayer receiver = gameListener.player;
		if (receiver == null || !DroneSystem.isControllingDrone(receiver)) {
			return;
		}

		Packet<?> rewritten = DroneSystem.rewriteOutgoingControlledOperatorPacket(receiver, packet);
		if (rewritten == packet) {
			return;
		}

		ci.cancel();
		if (rewritten == null) {
			return;
		}

		DroneSystem.runWithControlledOperatorPacketRewriteBypass(() -> {
			if (listener == null) {
				gameListener.send(rewritten);
			} else {
				gameListener.send(rewritten, listener);
			}
		});
	}
}
