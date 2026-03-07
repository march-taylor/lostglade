package com.lostglade.server.glitch;

import com.google.gson.JsonObject;
import com.lostglade.config.GlitchConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CheckpointDesyncGlitch implements RespawnGlitchHandler {
	private static final Map<UUID, ServerPlayer.RespawnConfig> PENDING_RESPAWN_RESTORE = new HashMap<>();

	@Override
	public String id() {
		return "checkpoint_desync";
	}

	@Override
	public GlitchConfig.GlitchEntry defaultEntry() {
		GlitchConfig.GlitchEntry entry = new GlitchConfig.GlitchEntry();
		entry.enabled = true;
		entry.minStabilityPercent = 0.0D;
		entry.maxStabilityPercent = 70.0D;
		entry.chancePerCheck = 0.35D;
		entry.stabilityInfluence = 1.0D;
		entry.minCooldownTicks = 0;
		entry.maxCooldownTicks = 0;
		entry.settings = new JsonObject();
		return entry;
	}

	@Override
	public boolean sanitizeSettings(GlitchConfig.GlitchEntry entry) {
		if (entry.settings == null) {
			entry.settings = new JsonObject();
			return true;
		}
		return false;
	}

	@Override
	public boolean trigger(MinecraftServer server, RandomSource random, GlitchConfig.GlitchEntry entry, double stabilityPercent) {
		return false;
	}

	@Override
	public boolean triggerOnCopyFrom(
			MinecraftServer server,
			RandomSource random,
			GlitchConfig.GlitchEntry entry,
			double stabilityPercent,
			ServerPlayer oldPlayer,
			ServerPlayer newPlayer,
			boolean alive
	) {
		if (alive || newPlayer == null) {
			return false;
		}

		Map<SpawnPointKey, SpawnTarget> uniqueTargets = collectUniqueForeignSpawnTargets(server, newPlayer);
		if (uniqueTargets.isEmpty()) {
			return false;
		}

		List<SpawnTarget> targets = new ArrayList<>(uniqueTargets.values());
		SpawnTarget picked = targets.get(random.nextInt(targets.size()));

		ServerPlayer.RespawnConfig originalConfig = normalizeRespawnConfig(newPlayer.getRespawnConfig());
		if (oldPlayer != null) {
			originalConfig = normalizeRespawnConfig(oldPlayer.getRespawnConfig());
		}

		ServerPlayer.RespawnConfig glitchConfig = new ServerPlayer.RespawnConfig(picked.respawnData(), picked.forced());
		newPlayer.setRespawnPosition(glitchConfig, false);
		PENDING_RESPAWN_RESTORE.put(newPlayer.getUUID(), originalConfig);
		return true;
	}

	@Override
	public void onAfterRespawn(MinecraftServer server, ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (newPlayer == null) {
			return;
		}

		ServerPlayer.RespawnConfig originalConfig = PENDING_RESPAWN_RESTORE.remove(newPlayer.getUUID());
		if (originalConfig == null) {
			return;
		}

		newPlayer.setRespawnPosition(originalConfig, false);
	}

	private static Map<SpawnPointKey, SpawnTarget> collectUniqueForeignSpawnTargets(MinecraftServer server, ServerPlayer respawnedPlayer) {
		Map<SpawnPointKey, SpawnTarget> uniqueTargets = new LinkedHashMap<>();
		for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
			if (candidate.getUUID().equals(respawnedPlayer.getUUID())) {
				continue;
			}

			SpawnTarget target = resolveSpawnTarget(server, candidate);
			if (target == null) {
				continue;
			}

			SpawnPointKey key = new SpawnPointKey(target.dimension(), target.blockPos());
			uniqueTargets.putIfAbsent(key, target);
		}

		return uniqueTargets;
	}

	private static SpawnTarget resolveSpawnTarget(MinecraftServer server, ServerPlayer player) {
		ServerPlayer.RespawnConfig respawnConfig = normalizeRespawnConfig(player.getRespawnConfig());
		LevelData.RespawnData respawnData = respawnConfig.respawnData();

		ResourceKey<Level> dimension = respawnData.dimension();
		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			return null;
		}

		BlockPos blockPos = respawnData.pos();
		if (LevelData.RespawnData.DEFAULT.equals(respawnData)) {
			respawnData = level.getRespawnData();
			blockPos = respawnData.pos();
		}

		return new SpawnTarget(
				dimension,
				blockPos.immutable(),
				respawnData,
				respawnConfig.forced()
		);
	}

	private static ServerPlayer.RespawnConfig normalizeRespawnConfig(ServerPlayer.RespawnConfig config) {
		if (config != null) {
			return config;
		}
		return new ServerPlayer.RespawnConfig(LevelData.RespawnData.DEFAULT, false);
	}

	private record SpawnPointKey(ResourceKey<Level> dimension, BlockPos blockPos) {
	}

	private record SpawnTarget(
			ResourceKey<Level> dimension,
			BlockPos blockPos,
			LevelData.RespawnData respawnData,
			boolean forced
	) {
	}
}
