package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import com.lostglade.server.ServerBossBarVisibilitySystem;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerAbsoluteInvisibilityMixin {
	@Unique
	private static final ThreadLocal<Boolean> LG2_ABSOLUTE_INVISIBILITY_BYPASS = ThreadLocal.withInitial(() -> false);

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$filterAbsoluteInvisibilityPackets(Packet<?> packet, CallbackInfo ci) {
		lg2$processAbsoluteInvisibilityPacket(packet, null, ci);
	}

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void lg2$filterAbsoluteInvisibilityPacketsWithListener(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		lg2$processAbsoluteInvisibilityPacket(packet, listener, ci);
	}

	@Unique
	private void lg2$processAbsoluteInvisibilityPacket(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		if (LG2_ABSOLUTE_INVISIBILITY_BYPASS.get() || ServerBossBarVisibilitySystem.shouldBypassPacketFilter()) {
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

		if (packet instanceof ClientboundSetEntityDataPacket entityDataPacket) {
			ServerAbsoluteInvisibilitySystem.maskSprintingMetadataForViewer(receiver, entityDataPacket);
		}

		if (packet instanceof ClientboundSetEquipmentPacket equipmentPacket) {
			ClientboundSetEquipmentPacket replacement = ServerAbsoluteInvisibilitySystem.maskArmorEquipmentForViewer(receiver, equipmentPacket);
				if (replacement != equipmentPacket) {
					ci.cancel();
					LG2_ABSOLUTE_INVISIBILITY_BYPASS.set(true);
					try {
						if (listener == null) {
							gameListener.send(replacement);
						} else {
							gameListener.send(replacement, listener);
						}
					} finally {
						LG2_ABSOLUTE_INVISIBILITY_BYPASS.remove();
					}
					return;
				}
		}

		if (packet instanceof ClientboundBossEventPacket bossEventPacket
				&& ServerBossBarVisibilitySystem.handleOutgoingBossEventPacket(receiver, bossEventPacket)) {
			ci.cancel();
			return;
		}

		if (packet instanceof ClientboundTrackedWaypointPacket waypointPacket
				&& com.lostglade.server.ServerRaceSystem.shouldSuppressMilkMouseWaypoint(receiver, waypointPacket)) {
			ci.cancel();
			return;
		}

		if (ServerAbsoluteInvisibilitySystem.shouldSuppressOutgoingPacket(receiver, packet)) {
			ci.cancel();
		}
	}
}
