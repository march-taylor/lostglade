package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerBackroomsSystem {
	public static final ResourceKey<Level> BACKROOMS_LEVEL = ResourceKey.create(
			Registries.DIMENSION,
			Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms")
	);

	private static final Gson STATE_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String RETURNS_FILE_NAME = "lg2-backrooms-returns.json";
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final BlockPos BACKROOMS_PLATFORM_CENTER = new BlockPos(0, 64, 0);
	private static final int RESPAWN_MIN_COORD = -4096;
	private static final int RESPAWN_MAX_COORD = 4096;
	private static final int BACKROOMS_RESPAWN_DELAY_TICKS = 2;
	private static final int PLATFORM_RADIUS = 2;
	private static final int PLATFORM_CLEAR_HEIGHT = 4;
	private static final Map<UUID, ReturnPointState> RETURN_POINTS = new HashMap<>();
	private static final Map<UUID, Integer> PENDING_RESPAWNS = new HashMap<>();

	private static boolean stateLoaded = false;
	private static boolean stateDirty = false;

	private ServerBackroomsSystem() {
	}

	public static void register() {
		stateLoaded = false;
		stateDirty = false;
		RETURN_POINTS.clear();
		PENDING_RESPAWNS.clear();

		ServerLifecycleEvents.SERVER_STARTED.register(ServerBackroomsSystem::loadState);
		ServerLifecycleEvents.SERVER_STOPPING.register(ServerBackroomsSystem::saveState);
		ServerPlayerEvents.AFTER_RESPAWN.register(ServerBackroomsSystem::onAfterRespawn);
		ServerTickEvents.END_SERVER_TICK.register(ServerBackroomsSystem::tickServer);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						Commands.literal("backrooms")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(context -> enterPlayers(context.getSource().getServer(), List.of(context.getSource().getPlayerOrException()), true))
								.then(Commands.literal("enter")
										.executes(context -> enterPlayers(context.getSource().getServer(), List.of(context.getSource().getPlayerOrException()), true))
										.then(Commands.argument("targets", EntityArgument.players())
												.executes(context -> enterPlayers(
														context.getSource().getServer(),
														EntityArgument.getPlayers(context, "targets"),
														false
												))))
								.then(Commands.literal("return")
										.executes(context -> returnPlayers(context.getSource().getServer(), List.of(context.getSource().getPlayerOrException()), true))
										.then(Commands.argument("targets", EntityArgument.players())
												.executes(context -> returnPlayers(
														context.getSource().getServer(),
														EntityArgument.getPlayers(context, "targets"),
														false
												))))
				)
		);
	}

	public static boolean isBackrooms(Level level) {
		return level != null && isBackrooms(level.dimension());
	}

	public static boolean isBackrooms(ResourceKey<Level> dimension) {
		return BACKROOMS_LEVEL.equals(dimension);
	}

	public static boolean isInBackrooms(Entity entity) {
		return entity != null && isBackrooms(entity.level());
	}

	public static int countPlayersOutsideBackrooms(MinecraftServer server) {
		if (server == null) {
			return 0;
		}

		int count = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || !player.isAlive() || player.isSpectator() || isInBackrooms(player)) {
				continue;
			}
			count++;
		}
		return count;
	}

	public static boolean teleportPlayerToBackrooms(ServerPlayer player) {
		if (player == null) {
			return false;
		}

		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}

		ServerLevel backrooms = server.getLevel(BACKROOMS_LEVEL);
		if (backrooms == null) {
			return false;
		}

		if (!player.level().dimension().equals(BACKROOMS_LEVEL)) {
			storeReturnPoint(player);
		}

		teleportToRandomBackroomsRespawn(backrooms, player);
		return true;
	}

	public static boolean teleportPlayerToNormalSpawn(ServerPlayer player) {
		if (player == null) {
			return false;
		}

		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}

		ServerLevel targetLevel = server.overworld();
		BlockPos targetPos = targetLevel.getRespawnData().pos();
		float targetYaw = targetLevel.getRespawnData().yaw();
		float targetPitch = targetLevel.getRespawnData().pitch();

		ServerPlayer.RespawnConfig respawnConfig = player.getRespawnConfig();
		LevelData.RespawnData respawnData = respawnConfig == null ? null : respawnConfig.respawnData();
		if (respawnData != null && Level.OVERWORLD.equals(respawnData.dimension())) {
			BlockPos respawnPos = respawnData.pos();
			if (respawnPos != null) {
				targetPos = respawnPos;
				targetYaw = respawnData.yaw();
				targetPitch = respawnData.pitch();
			}
		}

		targetLevel.getChunkAt(targetPos);
		player.teleportTo(
				targetLevel,
				targetPos.getX() + 0.5D,
				targetPos.getY() + 0.1D,
				targetPos.getZ() + 0.5D,
				ABSOLUTE_TELEPORT,
				targetYaw,
				targetPitch,
				false
		);
		player.fallDistance = 0.0F;
		return true;
	}

	private static int enterPlayers(MinecraftServer server, Collection<ServerPlayer> players, boolean singleFeedback) {
		ServerLevel backrooms = server.getLevel(BACKROOMS_LEVEL);
		if (backrooms == null) {
			throw new IllegalStateException("Dimension lg2:backrooms is not loaded. Restart the server after updating the mod resources.");
		}

		int moved = 0;
		for (ServerPlayer player : players) {
			if (teleportPlayerToBackrooms(player)) {
				moved++;
			}
		}

		if (singleFeedback && moved == 1) {
			players.iterator().next().sendSystemMessage(Component.literal("Перемещение в backrooms выполнено."));
		}

		return moved;
	}

	private static int returnPlayers(MinecraftServer server, Collection<ServerPlayer> players, boolean singleFeedback) {
		int moved = 0;
		for (ServerPlayer player : players) {
			if (player == null) {
				continue;
			}

			ReturnPointState returnPoint = RETURN_POINTS.remove(player.getUUID());
			if (returnPoint == null) {
				teleportToFallback(server, player);
			} else {
				ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(returnPoint.dimension));
				ServerLevel level = server.getLevel(dimension);
				if (level == null) {
					teleportToFallback(server, player);
				} else {
					level.getChunkAt(BlockPos.containing(returnPoint.x, returnPoint.y, returnPoint.z));
					player.teleportTo(
							level,
							returnPoint.x,
							returnPoint.y,
							returnPoint.z,
							ABSOLUTE_TELEPORT,
							returnPoint.yaw,
							returnPoint.pitch,
							false
					);
				}
				stateDirty = true;
			}

			player.fallDistance = 0.0F;
			moved++;
		}

		if (singleFeedback && moved == 1) {
			players.iterator().next().sendSystemMessage(Component.literal("Возврат из backrooms выполнен."));
		}

		return moved;
	}

	private static void teleportToFallback(MinecraftServer server, ServerPlayer player) {
		teleportPlayerToNormalSpawn(player);
	}

	private static void storeReturnPoint(ServerPlayer player) {
		ReturnPointState state = new ReturnPointState();
		state.dimension = player.level().dimension().identifier().toString();
		state.x = player.getX();
		state.y = player.getY();
		state.z = player.getZ();
		state.yaw = player.getYRot();
		state.pitch = player.getXRot();
		RETURN_POINTS.put(player.getUUID(), state);
		stateDirty = true;
	}

	private static void loadState(MinecraftServer server) {
		RETURN_POINTS.clear();
		stateLoaded = true;
		stateDirty = false;

		Path path = getStateFilePath(server);
		if (!Files.isRegularFile(path)) {
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			ReturnStateFile file = STATE_GSON.fromJson(reader, ReturnStateFile.class);
			if (file == null || file.players == null) {
				return;
			}

			for (Map.Entry<String, ReturnPointState> entry : file.players.entrySet()) {
				try {
					RETURN_POINTS.put(UUID.fromString(entry.getKey()), entry.getValue());
				} catch (IllegalArgumentException ignored) {
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.error("Failed to load backrooms return points", exception);
		}
	}

	private static void onAfterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
		if (alive || oldPlayer == null || newPlayer == null || !isInBackrooms(oldPlayer)) {
			return;
		}

		PENDING_RESPAWNS.put(newPlayer.getUUID(), BACKROOMS_RESPAWN_DELAY_TICKS);
	}

	private static void saveState(MinecraftServer server) {
		if (!stateLoaded || !stateDirty) {
			return;
		}

		Path path = getStateFilePath(server);
		ReturnStateFile file = new ReturnStateFile();
		file.players = new LinkedHashMap<>();
		for (Map.Entry<UUID, ReturnPointState> entry : RETURN_POINTS.entrySet()) {
			file.players.put(entry.getKey().toString(), entry.getValue());
		}

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				STATE_GSON.toJson(file, writer);
			}
			stateDirty = false;
		} catch (IOException exception) {
			Lg2.LOGGER.error("Failed to save backrooms return points", exception);
		}
	}

	private static Path getStateFilePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(RETURNS_FILE_NAME);
	}

	private static void tickPendingRespawns(MinecraftServer server) {
		if (PENDING_RESPAWNS.isEmpty()) {
			return;
		}

		ServerLevel backrooms = server.getLevel(BACKROOMS_LEVEL);
		if (backrooms == null) {
			PENDING_RESPAWNS.clear();
			return;
		}

		var iterator = PENDING_RESPAWNS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Integer> entry = iterator.next();
			int ticksRemaining = entry.getValue() - 1;
			if (ticksRemaining > 0) {
				entry.setValue(ticksRemaining);
				continue;
			}

			iterator.remove();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				continue;
			}

			teleportToRandomBackroomsRespawn(backrooms, player);
		}
	}

	private static void tickServer(MinecraftServer server) {
		enforceClearWeather(server);
		tickPendingRespawns(server);
	}

	private static void enforceClearWeather(MinecraftServer server) {
		ServerLevel backrooms = server.getLevel(BACKROOMS_LEVEL);
		if (backrooms == null) {
			return;
		}

		if (!backrooms.isRaining() && !backrooms.isThundering() && backrooms.getRainLevel(1.0F) <= 0.0F && backrooms.getThunderLevel(1.0F) <= 0.0F) {
			return;
		}

		backrooms.setWeatherParameters(12000, 0, false, false);
		backrooms.setRainLevel(0.0F);
		backrooms.setThunderLevel(0.0F);
	}

	private static void teleportToRandomBackroomsRespawn(ServerLevel backrooms, ServerPlayer player) {
		BlockPos platformCenter = pickRandomRespawnCenter(backrooms);
		backrooms.getChunkAt(platformCenter);
		ensurePlatform(backrooms, platformCenter);
		player.teleportTo(
				backrooms,
				platformCenter.getX() + 0.5D,
				platformCenter.getY() + 1.0D,
				platformCenter.getZ() + 0.5D,
				ABSOLUTE_TELEPORT,
				player.getYRot(),
				player.getXRot(),
				false
		);
		player.fallDistance = 0.0F;
	}

	private static BlockPos pickRandomRespawnCenter(ServerLevel level) {
		int x = Mth.nextInt(level.random, RESPAWN_MIN_COORD, RESPAWN_MAX_COORD);
		int z = Mth.nextInt(level.random, RESPAWN_MIN_COORD, RESPAWN_MAX_COORD);
		return new BlockPos(x, BACKROOMS_PLATFORM_CENTER.getY(), z);
	}

	private static void ensurePlatform(ServerLevel level, BlockPos center) {
		for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
			for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
				BlockPos floorPos = center.offset(dx, 0, dz);
				level.setBlockAndUpdate(floorPos, ModBlocks.getRandomizedBackroomsBlockState(floorPos.asLong()));
				for (int dy = 1; dy <= PLATFORM_CLEAR_HEIGHT; dy++) {
					level.setBlockAndUpdate(floorPos.above(dy), Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private static final class ReturnStateFile {
		Map<String, ReturnPointState> players = new LinkedHashMap<>();
	}

	private static final class ReturnPointState {
		String dimension;
		double x;
		double y;
		double z;
		float yaw;
		float pitch;
	}
}
