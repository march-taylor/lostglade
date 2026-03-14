package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
	private static final int PLATFORM_EXIT_LENGTH = 12;
	private static final int RESPAWN_SEARCH_ATTEMPTS = 48;
	private static final int RESPAWN_SEARCH_RADIUS = 12;
	private static final Identifier BACKROOMS_AMBIENT_SOUND_ID = Identifier.fromNamespaceAndPath("minecraft", "custom.backrooms_ambient_loop");
	// 12s file at 20 TPS ~= 240 ticks. Start every 200 ticks for ~2s overlap.
	private static final int BACKROOMS_AMBIENT_LOOP_TICKS = 200;
	private static final float[] BACKROOMS_AMBIENT_FADE_IN = new float[]{0.35F, 0.55F, 0.75F, 0.85F};
	private static final float BACKROOMS_LOW_PITCH_CHANCE = 0.06F;
	private static final float BACKROOMS_LOW_PITCH = 0.944F;
	private static final float BACKROOMS_DIP_VOLUME_MULTIPLIER = 0.82F;
	private static final int BACKROOMS_VOLTAGE_DIP_TICKS = BACKROOMS_AMBIENT_LOOP_TICKS + 6;
	private static final int BACKROOMS_DARKNESS_REFRESH_TICKS = 6;
	private static final int BACKROOMS_VOLTAGE_DIP_MIN_LOOPS = 2;
	private static final int BACKROOMS_VOLTAGE_DIP_MAX_LOOPS = 3;
	private static final int LAMP_FLICKER_RADIUS_HORIZONTAL = 14;
	private static final int LAMP_FLICKER_RADIUS_VERTICAL = 4;
	private static final int LAMP_FLICKER_SEARCH_ATTEMPTS = 18;
	private static final int LAMP_FLICKER_MAX_PER_TRIGGER = 2;
	private static final int LAMP_FLICKER_MIN_OFF_TICKS = 3;
	private static final int LAMP_FLICKER_MAX_OFF_TICKS = 9;
	private static final int LAMP_FLICKER_RESTORE_BUDGET_PER_TICK = 28;
	private static final int LAMP_FLICKER_MAX_ACTIVE = 640;
	private static final float LAMP_FLICKER_TRIGGER_CHANCE = 0.40F;
	private static final int LAMP_FLICKER_TRIGGER_COOLDOWN_TICKS = 2;
	private static final Map<UUID, ReturnPointState> RETURN_POINTS = new HashMap<>();
	private static final Map<UUID, Integer> PENDING_RESPAWNS = new HashMap<>();
	private static final Map<UUID, AmbientLoopState> AMBIENT_LOOP_STATES = new HashMap<>();
	private static final Map<Long, Long> ACTIVE_LAMP_OUTAGES = new HashMap<>();

	private static boolean stateLoaded = false;
	private static boolean stateDirty = false;

	private ServerBackroomsSystem() {
	}

	public static void register() {
		stateLoaded = false;
		stateDirty = false;
		RETURN_POINTS.clear();
		PENDING_RESPAWNS.clear();
		AMBIENT_LOOP_STATES.clear();
		ACTIVE_LAMP_OUTAGES.clear();

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
		tickBackroomsAmbientLoop(server);
		tickLampRestores(server);
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
		BlockPos safeSpawn = findRandomSafeRespawn(backrooms);
		BlockPos platformCenter;
		if (safeSpawn != null) {
			platformCenter = safeSpawn.below();
		} else {
			platformCenter = pickRandomRespawnCenter(backrooms);
			backrooms.getChunkAt(platformCenter);
			ensurePlatform(backrooms, platformCenter);
		}

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

	private static void tickBackroomsAmbientLoop(MinecraftServer server) {
		long gameTime = server.overworld().getGameTime();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID uuid = player.getUUID();
			boolean shouldPlay = isInBackrooms(player) && PolymerResourcePackUtils.hasMainPack(player);
			AmbientLoopState state = AMBIENT_LOOP_STATES.get(uuid);

			if (!shouldPlay) {
				if (state != null) {
					stopBackroomsAmbient(player);
					AMBIENT_LOOP_STATES.remove(uuid);
				}
				continue;
			}

			if (state == null) {
				state = new AmbientLoopState();
				state.nextPlayTick = gameTime;
				state.fadeStep = 0;
				AMBIENT_LOOP_STATES.put(uuid, state);
			}

			if (gameTime >= state.nextPlayTick) {
				float volume = BACKROOMS_AMBIENT_FADE_IN[Math.min(state.fadeStep, BACKROOMS_AMBIENT_FADE_IN.length - 1)];
				if (state.voltageDipLoopsRemaining <= 0 && player.getRandom().nextFloat() < BACKROOMS_LOW_PITCH_CHANCE) {
					state.voltageDipLoopsRemaining = Mth.nextInt(player.getRandom(), BACKROOMS_VOLTAGE_DIP_MIN_LOOPS, BACKROOMS_VOLTAGE_DIP_MAX_LOOPS);
				}
				boolean lowPitchTick = state.voltageDipLoopsRemaining > 0;
				float pitch = lowPitchTick ? BACKROOMS_LOW_PITCH : 1.0F;
				if (lowPitchTick) {
					volume *= BACKROOMS_DIP_VOLUME_MULTIPLIER;
					state.voltageDipTicks = Math.max(state.voltageDipTicks, BACKROOMS_VOLTAGE_DIP_TICKS);
					state.voltageDipLoopsRemaining--;
				}
				playBackroomsAmbient(player, volume, pitch);
				state.fadeStep = Math.min(state.fadeStep + 1, BACKROOMS_AMBIENT_FADE_IN.length - 1);
				state.nextPlayTick = gameTime + BACKROOMS_AMBIENT_LOOP_TICKS;
			}

			if (state.voltageDipTicks > 0) {
				applyVoltageDip(server, player, state, gameTime);
				state.voltageDipTicks--;
			}
		}

		AMBIENT_LOOP_STATES.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
	}

	private static void playBackroomsAmbient(ServerPlayer player, float volume, float pitch) {
		Holder<SoundEvent> sound = Holder.direct(SoundEvent.createVariableRangeEvent(BACKROOMS_AMBIENT_SOUND_ID));
		long seed = player.level().random.nextLong();
		player.connection.send(
				new ClientboundSoundPacket(
						sound,
						SoundSource.AMBIENT,
						player.getX(),
						player.getY(),
						player.getZ(),
						volume,
						pitch,
						seed
				)
		);
	}

	private static void applyVoltageDip(MinecraftServer server, ServerPlayer player, AmbientLoopState state, long gameTime) {
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, BACKROOMS_DARKNESS_REFRESH_TICKS, 0, true, false, false));

		ServerLevel backrooms = server.getLevel(BACKROOMS_LEVEL);
		if (backrooms == null || !backrooms.dimension().equals(player.level().dimension())) {
			return;
		}

		if (ACTIVE_LAMP_OUTAGES.size() >= LAMP_FLICKER_MAX_ACTIVE) {
			return;
		}

		if (gameTime < state.nextLampFlickerTick) {
			return;
		}

		if (player.getRandom().nextFloat() > LAMP_FLICKER_TRIGGER_CHANCE) {
			return;
		}

		state.nextLampFlickerTick = gameTime + LAMP_FLICKER_TRIGGER_COOLDOWN_TICKS;
		triggerLampFlickerNearPlayer(backrooms, player, gameTime);
	}

	private static void triggerLampFlickerNearPlayer(ServerLevel level, ServerPlayer player, long gameTime) {
		int centerX = Mth.floor(player.getX());
		int centerY = Mth.floor(player.getY());
		int centerZ = Mth.floor(player.getZ());
		int turnedOff = 0;

		for (int attempt = 0; attempt < LAMP_FLICKER_SEARCH_ATTEMPTS; attempt++) {
			if (turnedOff >= LAMP_FLICKER_MAX_PER_TRIGGER || ACTIVE_LAMP_OUTAGES.size() >= LAMP_FLICKER_MAX_ACTIVE) {
				break;
			}

			int x = centerX + Mth.nextInt(level.random, -LAMP_FLICKER_RADIUS_HORIZONTAL, LAMP_FLICKER_RADIUS_HORIZONTAL);
			int y = centerY + Mth.nextInt(level.random, -LAMP_FLICKER_RADIUS_VERTICAL, LAMP_FLICKER_RADIUS_VERTICAL);
			int z = centerZ + Mth.nextInt(level.random, -LAMP_FLICKER_RADIUS_HORIZONTAL, LAMP_FLICKER_RADIUS_HORIZONTAL);
			BlockPos pos = new BlockPos(x, y, z);
			long packedPos = pos.asLong();

			if (ACTIVE_LAMP_OUTAGES.containsKey(packedPos) || !level.hasChunkAt(pos)) {
				continue;
			}

			if (!level.getBlockState(pos).is(ModBlocks.BACKROOMS_LIGHTBLOCK)) {
				continue;
			}

			level.setBlockAndUpdate(pos, ModBlocks.getRandomizedBackroomsBlockState(packedPos));
			long restoreAt = gameTime + Mth.nextInt(level.random, LAMP_FLICKER_MIN_OFF_TICKS, LAMP_FLICKER_MAX_OFF_TICKS);
			ACTIVE_LAMP_OUTAGES.put(packedPos, restoreAt);
			turnedOff++;
		}
	}

	private static void tickLampRestores(MinecraftServer server) {
		if (ACTIVE_LAMP_OUTAGES.isEmpty()) {
			return;
		}

		ServerLevel backrooms = server.getLevel(BACKROOMS_LEVEL);
		if (backrooms == null) {
			ACTIVE_LAMP_OUTAGES.clear();
			return;
		}

		long gameTime = backrooms.getGameTime();
		int restoreBudget = LAMP_FLICKER_RESTORE_BUDGET_PER_TICK;
		var iterator = ACTIVE_LAMP_OUTAGES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Long, Long> entry = iterator.next();
			if (entry.getValue() > gameTime) {
				continue;
			}

			BlockPos pos = BlockPos.of(entry.getKey());
			if (backrooms.hasChunkAt(pos) && backrooms.getBlockState(pos).is(ModBlocks.BACKROOMS_BLOCK)) {
				backrooms.setBlockAndUpdate(pos, ModBlocks.BACKROOMS_LIGHTBLOCK.defaultBlockState());
			}
			iterator.remove();
			restoreBudget--;
			if (restoreBudget <= 0) {
				break;
			}
		}
	}

	private static void stopBackroomsAmbient(ServerPlayer player) {
		player.connection.send(new ClientboundStopSoundPacket(BACKROOMS_AMBIENT_SOUND_ID, SoundSource.AMBIENT));
	}

	private static BlockPos findRandomSafeRespawn(ServerLevel level) {
		for (int attempt = 0; attempt < RESPAWN_SEARCH_ATTEMPTS; attempt++) {
			BlockPos center = pickRandomRespawnCenter(level);
			level.getChunkAt(center);
			BlockPos safePos = findNearbySafeRespawn(level, center);
			if (safePos != null) {
				return safePos;
			}
		}

		return null;
	}

	private static BlockPos findNearbySafeRespawn(ServerLevel level, BlockPos center) {
		if (isSafeRespawnPosition(level, center)) {
			return center.above();
		}

		for (int radius = 1; radius <= RESPAWN_SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				int worldX = center.getX() + dx;

				BlockPos north = new BlockPos(worldX, center.getY(), center.getZ() - radius);
				if (isSafeRespawnPosition(level, north)) {
					return north.above();
				}

				BlockPos south = new BlockPos(worldX, center.getY(), center.getZ() + radius);
				if (isSafeRespawnPosition(level, south)) {
					return south.above();
				}
			}

			for (int dz = -radius + 1; dz <= radius - 1; dz++) {
				int worldZ = center.getZ() + dz;

				BlockPos west = new BlockPos(center.getX() - radius, center.getY(), worldZ);
				if (isSafeRespawnPosition(level, west)) {
					return west.above();
				}

				BlockPos east = new BlockPos(center.getX() + radius, center.getY(), worldZ);
				if (isSafeRespawnPosition(level, east)) {
					return east.above();
				}
			}
		}

		return null;
	}

	private static boolean isSafeRespawnPosition(ServerLevel level, BlockPos floorPos) {
		if (!level.getBlockState(floorPos).isFaceSturdy(level, floorPos, Direction.UP)) {
			return false;
		}

		BlockPos feetPos = floorPos.above();
		BlockPos headPos = feetPos.above();
		if (!level.isEmptyBlock(feetPos) || !level.isEmptyBlock(headPos)) {
			return false;
		}

		return hasAdjacentOpenSpace(level, floorPos);
	}

	private static boolean hasAdjacentOpenSpace(ServerLevel level, BlockPos floorPos) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos adjacentFloor = floorPos.relative(direction);
			if (!level.getBlockState(adjacentFloor).isFaceSturdy(level, adjacentFloor, Direction.UP)) {
				continue;
			}

			BlockPos adjacentFeet = adjacentFloor.above();
			BlockPos adjacentHead = adjacentFeet.above();
			if (level.isEmptyBlock(adjacentFeet) && level.isEmptyBlock(adjacentHead)) {
				return true;
			}
		}

		return false;
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

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			carvePlatformExit(level, center, direction);
		}
	}

	private static void carvePlatformExit(ServerLevel level, BlockPos center, Direction direction) {
		for (int step = PLATFORM_RADIUS + 1; step <= PLATFORM_RADIUS + PLATFORM_EXIT_LENGTH; step++) {
			BlockPos floorPos = center.relative(direction, step);
			level.setBlockAndUpdate(floorPos, ModBlocks.getRandomizedBackroomsBlockState(floorPos.asLong()));
			for (int dy = 1; dy <= PLATFORM_CLEAR_HEIGHT; dy++) {
				level.setBlockAndUpdate(floorPos.above(dy), Blocks.AIR.defaultBlockState());
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

	private static final class AmbientLoopState {
		long nextPlayTick;
		int fadeStep;
		int voltageDipTicks;
		int voltageDipLoopsRemaining;
		long nextLampFlickerTick;
	}
}
