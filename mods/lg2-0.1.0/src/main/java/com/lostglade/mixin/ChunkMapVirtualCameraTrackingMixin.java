package com.lostglade.mixin;

import com.lostglade.server.DroneSystem;
import com.lostglade.server.RendererBotCameraSystem;
import com.lostglade.server.RendererBotPresenceSystem;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ChunkMap.class)
public abstract class ChunkMapVirtualCameraTrackingMixin {
	@Shadow
	protected abstract void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkTrackingView);

	@Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
	private void lg2$useVirtualCameraChunkTracking(ServerPlayer player, CallbackInfo ci) {
		if (RendererBotPresenceSystem.isRendererBot(player)) {
			ChunkTrackingView desiredView = RendererBotCameraSystem.createVirtualChunkTrackingView(player);
			if (Objects.equals(player.getChunkTrackingView(), desiredView)) {
				ci.cancel();
				return;
			}
			this.applyChunkTrackingView(player, desiredView);
			ci.cancel();
			return;
		}

		if (!DroneSystem.isControllingDrone(player)) {
			return;
		}

		ChunkTrackingView desiredView = DroneSystem.createVirtualChunkTrackingView(player);
		if (Objects.equals(player.getChunkTrackingView(), desiredView)) {
			ci.cancel();
			return;
		}

		this.applyChunkTrackingView(player, desiredView);
		ci.cancel();
	}
}
