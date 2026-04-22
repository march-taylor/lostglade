package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerDroneProxyChunkMoveMixin {
	@Redirect(
			method = "handleMovePlayer",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/server/level/ServerChunkCache;move(Lnet/minecraft/server/level/ServerPlayer;)V"
			)
	)
	private void lg2$skipChunkTrackingForDroneProxy(ServerChunkCache chunkCache, ServerPlayer player) {
		if (DroneSystem.shouldSkipChunkTrackingMove(player)) {
			return;
		}
		chunkCache.move(player);
	}
}
