package com.lostglade.mixin;

import com.lostglade.server.CocaineHallucinationSystem;
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
public abstract class ServerCommonPacketListenerCocaineHallucinationMixin {
	private static final ThreadLocal<Boolean> LG2_BYPASS = ThreadLocal.withInitial(() -> false);

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$filterCocaineHallucinationPackets(Packet<?> packet, CallbackInfo ci) {
		lg2$processPacket(packet, null, ci);
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$filterCocaineHallucinationPacketsWithListener(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		lg2$processPacket(packet, listener, ci);
	}

	private void lg2$processPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		if (LG2_BYPASS.get()) {
			return;
		}

		Object self = this;
		if (!(self instanceof ServerGamePacketListenerImpl gameListener)) {
			return;
		}

		ServerPlayer receiver = gameListener.player;
		if (receiver == null) {
			return;
		}

		Packet<?> filteredPacket = CocaineHallucinationSystem.filterOutgoingPacket(receiver, packet);
		if (filteredPacket == packet) {
			return;
		}

		ci.cancel();
		if (filteredPacket == null) {
			return;
		}

		LG2_BYPASS.set(true);
		try {
			if (listener == null) {
				gameListener.send(filteredPacket);
			} else {
				gameListener.send(filteredPacket, listener);
			}
		} finally {
			LG2_BYPASS.remove();
		}
	}
}
