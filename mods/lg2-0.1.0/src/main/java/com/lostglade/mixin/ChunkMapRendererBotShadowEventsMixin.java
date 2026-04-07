package com.lostglade.mixin;

import com.lostglade.server.RendererBotCameraSystem;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

@Mixin(ChunkMap.class)
public abstract class ChunkMapRendererBotShadowEventsMixin {
	@Inject(method = "sendToTrackingPlayers", at = @At("TAIL"))
	private void lg2$mirrorShadowEntityTransientPackets(
			Entity entity,
			Packet<? super ClientGamePacketListener> packet,
			CallbackInfo ci
	) {
		RendererBotCameraSystem.mirrorTransientEntityPacket(entity, packet);
	}

	@Inject(method = "sendToTrackingPlayersFiltered", at = @At("TAIL"))
	private void lg2$mirrorShadowEntityTransientPacketsFiltered(
			Entity entity,
			Packet<? super ClientGamePacketListener> packet,
			Predicate<ServerPlayer> predicate,
			CallbackInfo ci
	) {
		RendererBotCameraSystem.mirrorTransientEntityPacket(entity, packet);
	}

	@Inject(method = "sendToTrackingPlayersAndSelf", at = @At("TAIL"))
	private void lg2$mirrorShadowEntityTransientPacketsAndSelf(
			Entity entity,
			Packet<? super ClientGamePacketListener> packet,
			CallbackInfo ci
	) {
		RendererBotCameraSystem.mirrorTransientEntityPacket(entity, packet);
	}
}
