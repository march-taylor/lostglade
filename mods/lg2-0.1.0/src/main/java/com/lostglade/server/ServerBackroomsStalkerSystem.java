package com.lostglade.server;

import com.lostglade.config.Lg2Config;
import com.lostglade.entity.BackroomsStalkerEntity;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerBackroomsStalkerSystem {
	private static final int MAX_SPAWN_ATTEMPTS_PER_PLAYER = 48;
	private static final int OUTSIDE_BACKROOMS_SWEEP_INTERVAL_TICKS = 80;
	// Must match BackroomsLayout floor model: floor index = floorDiv(y - 64, 5).
	private static final int BACKROOMS_FLOOR_BASE_Y = 64;
	private static final int BACKROOMS_FLOOR_HEIGHT = 5;
	private static final Identifier STALKER_RUN_SOUND_ID = Identifier.fromNamespaceAndPath("minecraft", "custom.backrooms_stalker_approach");
	private static final double STALKER_RUN_HEAR_RADIUS_SQR = 32.0D * 32.0D;
	private static final float STALKER_RUN_VOLUME = 1.50F;
	private static final float STALKER_RUN_PITCH = 1.0F;
	private static final int STALKER_RUN_CLIP_TICKS = 240;
	// 12s clip at 20 TPS; replay every 10s so fades overlap by 2s.
	private static final int STALKER_RUN_LOOP_TICKS = 200;
	private static final Map<UUID, RunLoopState> STALKER_RUN_LOOP_STATES = new HashMap<>();
	private static long nextOutsideBackroomsSweepTick = 0L;

	private ServerBackroomsStalkerSystem() {
	}

	public static void register() {
		STALKER_RUN_LOOP_STATES.clear();
		nextOutsideBackroomsSweepTick = 0L;
		ServerTickEvents.END_SERVER_TICK.register(ServerBackroomsStalkerSystem::tickServer);
		AttackEntityCallback.EVENT.register(ServerBackroomsStalkerSystem::onAttackEntity);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!BackroomsStalkerEntity.isStalker(entity)) {
				return;
			}
			if (!(entity instanceof BackroomsStalkerEntity stalker)
					|| !(world instanceof ServerLevel level)
					|| !ServerBackroomsSystem.isBackrooms(level)) {
				entity.discard();
				return;
			}
			stalker.setInvulnerable(true);
			stalker.setSilent(true);
			stalker.attachPolymerAppearance();
		});
	}

	private static void tickServer(MinecraftServer server) {
		if (shouldSweepOutsideBackrooms(server)) {
			discardOutsideBackrooms(server);
		}

		ServerLevel backrooms = server.getLevel(ServerBackroomsSystem.BACKROOMS_LEVEL);
		if (backrooms == null) {
			stopAllRunLoops(server);
			return;
		}

		List<ServerPlayer> players = collectEligiblePlayers(backrooms);
		List<BackroomsStalkerEntity> stalkers = collectStalkers(backrooms);
		if (players.isEmpty()) {
			discardAll(stalkers);
			stopAllRunLoops(server);
			return;
		}

		int minSpawnRadiusBlocks = Math.max(0, Lg2Config.get().backroomsEntitySpawnMinRadiusChunks * 16);
		int maxSpawnRadiusBlocks = Math.max(16, Lg2Config.get().backroomsEntitySpawnMaxRadiusChunks * 16);
		if (maxSpawnRadiusBlocks < minSpawnRadiusBlocks) {
			maxSpawnRadiusBlocks = minSpawnRadiusBlocks;
		}
		int radiusBlocks = maxSpawnRadiusBlocks;
		int groupRadiusBlocks = Math.max(16, Lg2Config.get().backroomsEntityGroupRadiusChunks * 16);
		List<PlayerCluster> clusters = buildClusters(players, groupRadiusBlocks);
		Set<BackroomsStalkerEntity> assignedStalkers = new HashSet<>();
		List<BackroomsStalkerEntity> activeStalkers = new ArrayList<>(clusters.size());

		for (PlayerCluster cluster : clusters) {
			BackroomsStalkerEntity assigned = findAssignedStalker(cluster, stalkers, assignedStalkers, radiusBlocks);
			if (assigned != null) {
				assignedStalkers.add(assigned);
				activeStalkers.add(assigned);
				continue;
			}
			BackroomsStalkerEntity spawned = spawnNearPlayers(backrooms, cluster.players(), minSpawnRadiusBlocks, maxSpawnRadiusBlocks);
			if (spawned != null) {
				activeStalkers.add(spawned);
			}
		}

		for (BackroomsStalkerEntity stalker : stalkers) {
			if (!assignedStalkers.contains(stalker)) {
				stalker.discardFromSystem();
			}
		}

		tickStalkerRunLoopAudio(backrooms, players, activeStalkers);
	}

	private static boolean shouldSweepOutsideBackrooms(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return true;
		}

		long nowTick = overworld.getGameTime();
		if (nowTick < nextOutsideBackroomsSweepTick) {
			return false;
		}
		nextOutsideBackroomsSweepTick = nowTick + OUTSIDE_BACKROOMS_SWEEP_INTERVAL_TICKS;
		return true;
	}

	private static void tickStalkerRunLoopAudio(
			ServerLevel level,
			List<ServerPlayer> players,
			List<BackroomsStalkerEntity> stalkers
	) {
		long gameTime = level.getGameTime();
		List<BackroomsStalkerEntity> chasingStalkers = new ArrayList<>();
		for (BackroomsStalkerEntity stalker : stalkers) {
			if (stalker.isAlive() && !stalker.isRemoved() && stalker.isChasingTarget()) {
				chasingStalkers.add(stalker);
			}
		}

		Set<UUID> processedPlayers = new HashSet<>();
		for (ServerPlayer player : players) {
			UUID playerId = player.getUUID();
			processedPlayers.add(playerId);
			if (!PolymerResourcePackUtils.hasMainPack(player)) {
				stopRunLoopIfActive(player);
				continue;
			}

			BackroomsStalkerEntity nearestChasing = findNearestChasingStalker(player, chasingStalkers);
			if (nearestChasing == null) {
				RunLoopState state = STALKER_RUN_LOOP_STATES.get(playerId);
				if (state != null && gameTime >= state.lastPlayTick + STALKER_RUN_CLIP_TICKS) {
					STALKER_RUN_LOOP_STATES.remove(playerId);
				}
				continue;
			}

			RunLoopState state = STALKER_RUN_LOOP_STATES.computeIfAbsent(playerId, ignored -> new RunLoopState(gameTime));
			if (gameTime >= state.nextPlayTick) {
				playRunLoopSound(player, level, nearestChasing);
				state.lastPlayTick = gameTime;
				state.nextPlayTick = gameTime + STALKER_RUN_LOOP_TICKS;
			}
		}

		var iterator = STALKER_RUN_LOOP_STATES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, RunLoopState> entry = iterator.next();
			if (processedPlayers.contains(entry.getKey())) {
				continue;
			}

			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			if (player != null) {
				stopRunLoopSound(player);
			}
			iterator.remove();
		}
	}

	private static BackroomsStalkerEntity findNearestChasingStalker(ServerPlayer player, List<BackroomsStalkerEntity> stalkers) {
		BackroomsStalkerEntity nearest = null;
		double nearestDistanceSqr = STALKER_RUN_HEAR_RADIUS_SQR + 1.0D;
		for (BackroomsStalkerEntity stalker : stalkers) {
			double distanceSqr = stalker.distanceToSqr(player);
			if (distanceSqr > STALKER_RUN_HEAR_RADIUS_SQR || distanceSqr >= nearestDistanceSqr) {
				continue;
			}

			nearest = stalker;
			nearestDistanceSqr = distanceSqr;
		}
		return nearest;
	}

	private static void playRunLoopSound(ServerPlayer player, ServerLevel level, BackroomsStalkerEntity stalker) {
		Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(STALKER_RUN_SOUND_ID));
		long seed = level.random.nextLong();
		player.connection.send(new ClientboundSoundPacket(
				sound,
				SoundSource.AMBIENT,
				stalker.getX(),
				stalker.getY(),
				stalker.getZ(),
				STALKER_RUN_VOLUME,
				STALKER_RUN_PITCH,
				seed
		));
	}

	private static void stopRunLoopIfActive(ServerPlayer player) {
		if (STALKER_RUN_LOOP_STATES.remove(player.getUUID()) != null) {
			stopRunLoopSound(player);
		}
	}

	private static void stopRunLoopSound(ServerPlayer player) {
		player.connection.send(new ClientboundStopSoundPacket(STALKER_RUN_SOUND_ID, SoundSource.AMBIENT));
	}

	private static void stopAllRunLoops(MinecraftServer server) {
		if (STALKER_RUN_LOOP_STATES.isEmpty()) {
			return;
		}

		for (UUID playerId : STALKER_RUN_LOOP_STATES.keySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				stopRunLoopSound(player);
			}
		}
		STALKER_RUN_LOOP_STATES.clear();
	}

	private static InteractionResult onAttackEntity(
			Player player,
			Level world,
			InteractionHand hand,
			Entity entity,
			EntityHitResult hitResult
	) {
		if (world.isClientSide()) {
			return InteractionResult.PASS;
		}
		return BackroomsStalkerEntity.isStalker(entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	private static void discardOutsideBackrooms(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (ServerBackroomsSystem.isBackrooms(level)) {
				continue;
			}
			for (Entity entity : level.getAllEntities()) {
				if (BackroomsStalkerEntity.isStalker(entity)) {
					entity.discard();
				}
			}
		}
	}

	private static List<ServerPlayer> collectEligiblePlayers(ServerLevel level) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (!ServerBackroomsSystem.isInBackrooms(player)) {
				continue;
			}
			players.add(player);
		}
		return players;
	}

	private static List<BackroomsStalkerEntity> collectStalkers(ServerLevel level) {
		List<BackroomsStalkerEntity> stalkers = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof BackroomsStalkerEntity stalker) {
				stalkers.add(stalker);
			}
		}
		return stalkers;
	}

	private static void discardAll(List<BackroomsStalkerEntity> stalkers) {
		for (BackroomsStalkerEntity stalker : stalkers) {
			stalker.discardFromSystem();
		}
	}

	private static BackroomsStalkerEntity findAssignedStalker(
			PlayerCluster cluster,
			List<BackroomsStalkerEntity> stalkers,
			Set<BackroomsStalkerEntity> assigned,
			int radiusBlocks
	) {
		BackroomsStalkerEntity nearest = null;
		double nearestDistanceSqr = Double.MAX_VALUE;
		for (BackroomsStalkerEntity stalker : stalkers) {
			if (assigned.contains(stalker) || !stalker.isAlive() || stalker.isRemoved()) {
				continue;
			}
			if (!isWithinPresenceRadius(stalker, cluster.players(), radiusBlocks)) {
				continue;
			}

			double distanceSqr = stalker.position().distanceToSqr(cluster.center());
			if (distanceSqr < nearestDistanceSqr) {
				nearest = stalker;
				nearestDistanceSqr = distanceSqr;
			}
		}
		return nearest;
	}

	private static boolean isWithinPresenceRadius(BackroomsStalkerEntity stalker, List<ServerPlayer> players, int radiusBlocks) {
		double maxDistanceSqr = (double) radiusBlocks * radiusBlocks;
		int stalkerFloor = getBackroomsFloorIndex(stalker.blockPosition().getY());
		for (ServerPlayer player : players) {
			if (stalkerFloor != getBackroomsFloorIndex(player.blockPosition().getY())) {
				continue;
			}
			if (stalker.distanceToSqr(player) <= maxDistanceSqr) {
				return true;
			}
		}
		return false;
	}

	private static List<PlayerCluster> buildClusters(List<ServerPlayer> players, int groupRadiusBlocks) {
		List<PlayerCluster> clusters = new ArrayList<>();
		Set<UUID> visited = new HashSet<>();
		double maxDistanceSqr = (double) groupRadiusBlocks * groupRadiusBlocks;

		for (ServerPlayer start : players) {
			if (!visited.add(start.getUUID())) {
				continue;
			}

			List<ServerPlayer> clusterPlayers = new ArrayList<>();
			List<ServerPlayer> queue = new ArrayList<>();
			queue.add(start);

			for (int i = 0; i < queue.size(); i++) {
				ServerPlayer current = queue.get(i);
				clusterPlayers.add(current);
				for (ServerPlayer candidate : players) {
					if (visited.contains(candidate.getUUID())) {
						continue;
					}
					if (getBackroomsFloorIndex(current.blockPosition().getY()) != getBackroomsFloorIndex(candidate.blockPosition().getY())) {
						continue;
					}
					if (horizontalDistanceSqr(current, candidate) > maxDistanceSqr) {
						continue;
					}
					visited.add(candidate.getUUID());
					queue.add(candidate);
				}
			}

			clusters.add(new PlayerCluster(clusterPlayers, computeCenter(clusterPlayers)));
		}

		return clusters;
	}

	private static Vec3 computeCenter(List<ServerPlayer> players) {
		double sumX = 0.0D;
		double sumY = 0.0D;
		double sumZ = 0.0D;
		for (ServerPlayer player : players) {
			sumX += player.getX();
			sumY += player.getY();
			sumZ += player.getZ();
		}
		double size = players.isEmpty() ? 1.0D : players.size();
		return new Vec3(sumX / size, sumY / size, sumZ / size);
	}

	private static int getBackroomsFloorIndex(int y) {
		return Math.floorDiv(y - BACKROOMS_FLOOR_BASE_Y, BACKROOMS_FLOOR_HEIGHT);
	}

	private static double horizontalDistanceSqr(ServerPlayer first, ServerPlayer second) {
		double dx = first.getX() - second.getX();
		double dz = first.getZ() - second.getZ();
		return (dx * dx) + (dz * dz);
	}

	private static BackroomsStalkerEntity spawnNearPlayers(ServerLevel level, List<ServerPlayer> players, int minRadiusBlocks, int maxRadiusBlocks) {
		BackroomsStalkerEntity stalker = BackroomsStalkerEntity.create(level);

		Vec3 spawnPos = findSpawnPosition(level, stalker, players, minRadiusBlocks, maxRadiusBlocks);
		if (spawnPos == null) {
			stalker.discard();
			return null;
		}

		float yaw = level.random.nextFloat() * 360.0F;
		stalker.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		stalker.setYRot(yaw);
		stalker.setYHeadRot(yaw);
		stalker.setYBodyRot(yaw);
		stalker.setXRot(0.0F);
		if (!level.noCollision(stalker) || level.containsAnyLiquid(stalker.getBoundingBox())) {
			stalker.discard();
			return null;
		}

		level.addFreshEntity(stalker);
		return stalker;
	}

	private static Vec3 findSpawnPosition(
			ServerLevel level,
			BackroomsStalkerEntity stalker,
			List<ServerPlayer> players,
			int minRadiusBlocks,
			int maxRadiusBlocks
	) {
		List<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
		Collections.shuffle(shuffledPlayers, new java.util.Random(level.getGameTime()));
		RandomSource random = level.random;
		int effectiveMaxRadius = Math.max(minRadiusBlocks, maxRadiusBlocks);

		for (ServerPlayer player : shuffledPlayers) {
			for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS_PER_PLAYER; attempt++) {
				double angle = random.nextDouble() * (Math.PI * 2.0D);
				int distance = minRadiusBlocks + random.nextInt(Math.max(1, effectiveMaxRadius - minRadiusBlocks + 1));
				int x = net.minecraft.util.Mth.floor(player.getX() + (Math.cos(angle) * distance));
				int z = net.minecraft.util.Mth.floor(player.getZ() + (Math.sin(angle) * distance));
				int y = player.blockPosition().getY();
				Vec3 spawnPos = findOpenPositionAtExactY(level, stalker, x, y, z);
				if (spawnPos != null) {
					return spawnPos;
				}
			}
		}

		return null;
	}

	private static Vec3 findOpenPositionAtExactY(ServerLevel level, BackroomsStalkerEntity stalker, int x, int y, int z) {
		BlockPos feetPos = new BlockPos(x, y, z);
		if (!canSpawnAt(level, stalker, feetPos)) {
			return null;
		}
		return Vec3.atBottomCenterOf(feetPos);
	}

	private static boolean canSpawnAt(ServerLevel level, BackroomsStalkerEntity stalker, BlockPos feetPos) {
		if (!level.getWorldBorder().isWithinBounds(feetPos)) {
			return false;
		}

		BlockState feetState = level.getBlockState(feetPos);
		BlockState headState = level.getBlockState(feetPos.above());
		BlockState floorState = level.getBlockState(feetPos.below());
		FluidState feetFluid = level.getFluidState(feetPos);
		FluidState headFluid = level.getFluidState(feetPos.above());
		if (!feetState.getCollisionShape(level, feetPos).isEmpty()) {
			return false;
		}
		if (!headState.getCollisionShape(level, feetPos.above()).isEmpty()) {
			return false;
		}
		if (!feetFluid.isEmpty() || !headFluid.isEmpty()) {
			return false;
		}
		if (floorState.getCollisionShape(level, feetPos.below()).isEmpty()) {
			return false;
		}

		stalker.setPos(feetPos.getX() + 0.5D, feetPos.getY(), feetPos.getZ() + 0.5D);
		return level.noCollision(stalker, stalker.getBoundingBox());
	}

	private record PlayerCluster(List<ServerPlayer> players, Vec3 center) {
	}

	private static final class RunLoopState {
		long nextPlayTick;
		long lastPlayTick;

		RunLoopState(long nextPlayTick) {
			this.nextPlayTick = nextPlayTick;
			this.lastPlayTick = Long.MIN_VALUE;
		}
	}
}
