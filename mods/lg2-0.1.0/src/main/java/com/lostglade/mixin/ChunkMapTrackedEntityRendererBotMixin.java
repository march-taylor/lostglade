package com.lostglade.mixin;

import com.lostglade.server.RendererBotCameraSystem;
import com.lostglade.server.RendererBotPresenceSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class ChunkMapTrackedEntityRendererBotMixin {
	@Shadow
	@Final
	private Set<ServerPlayerConnection> seenBy;

	@Shadow
	@Final
	private ServerEntity serverEntity;

	@Shadow
	@Final
	private Entity entity;

	@Shadow
	protected abstract int getEffectiveRange();

	@Shadow
	public abstract void removePlayer(ServerPlayer player);

	@Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
	private void lg2$trackEntitiesFromVirtualCameraPositions(ServerPlayer player, CallbackInfo ci) {
		if (!RendererBotPresenceSystem.isRendererBot(player)) {
			return;
		}

		ci.cancel();
		if (player == this.entity || !(this.entity.level() instanceof ServerLevel entityLevel)) {
			return;
		}

		double horizontalRange = this.getEffectiveRange();
		boolean withinRealPlayerRange = lg2$isWithinHorizontalRange(player.position(), this.entity, horizontalRange);
		boolean withinVirtualCameraRange = RendererBotCameraSystem.isEntityWithinAnyVirtualTrackingRange(player, this.entity, horizontalRange);
		ChunkPos chunkPos = this.entity.chunkPosition();
		boolean realChunkTracked = entityLevel.getChunkSource().chunkMap.isChunkTracked(player, chunkPos.x, chunkPos.z);
		boolean shouldTrack = this.entity.broadcastToPlayer(player)
				&& ((withinRealPlayerRange && realChunkTracked) || withinVirtualCameraRange);

		if (!shouldTrack) {
			this.removePlayer(player);
			return;
		}

		if (!this.seenBy.add(player.connection)) {
			return;
		}

		this.serverEntity.addPairing(player);
		if (this.seenBy.size() == 1) {
			entityLevel.debugSynchronizers().registerEntity(this.entity);
		}
		entityLevel.debugSynchronizers().startTrackingEntity(player, this.entity);
	}

	private static boolean lg2$isWithinHorizontalRange(Vec3 origin, Entity entity, double horizontalRange) {
		if (origin == null || entity == null || horizontalRange <= 0.0D) {
			return false;
		}
		double dx = origin.x - entity.getX();
		double dz = origin.z - entity.getZ();
		double horizontalRangeSq = horizontalRange * horizontalRange;
		return dx * dx + dz * dz <= horizontalRangeSq;
	}
}
