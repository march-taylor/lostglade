package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
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
public abstract class ServerCommonPacketListenerAbsoluteInvisibilityMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$filterAbsoluteInvisibilityPackets(Packet<?> packet, CallbackInfo ci) {
		if (lg2$shouldCancelPacket(packet)) {
			ci.cancel();
		}
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$filterAbsoluteInvisibilityPacketsWithListener(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		if (lg2$shouldCancelPacket(packet)) {
			ci.cancel();
		}
	}

	private boolean lg2$shouldCancelPacket(Packet<?> packet) {
		Object self = this;
		if (!(self instanceof ServerGamePacketListenerImpl gameListener)) {
			return false;
		}

		ServerPlayer receiver = gameListener.player;
		return receiver != null && ServerAbsoluteInvisibilitySystem.shouldSuppressOutgoingPacket(receiver, packet);
	}
}
