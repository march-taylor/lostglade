package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.lostglade.block.ServerBlock;
import com.lostglade.item.ModItems;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.lostglade.config.SeasonStartConfig.get;

public final class SeasonStartSystem {
	private static final Gson STATE_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String STATE_FILE_NAME = "lg2-season-start-state.json";
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final int DISSOLVE_BATCH_BLOCKS = 96;
	private static final ItemStack INTRO_TOOL_TEMPLATE = createIntroToolTemplate();

	private static final Map<UUID, PlayerSceneState> PLAYER_STATES = new LinkedHashMap<>();
	private static final List<BlockPos> SHELL_DISSOLVE_ORDER = new ArrayList<>();
	private static boolean stateLoaded = false;
	private static boolean stateDirty = false;
	private static boolean bootstrapComplete = false;
	private static boolean active = false;
	private static boolean completed = false;
	private static boolean shellDissolving = false;
	private static boolean scenePrepared = false;
	private static int dissolveCursor = 0;
	private static long nextSharedReminderTick = Long.MIN_VALUE;
	private static BlockPos serverAnchor = null;

	private SeasonStartSystem() {
	}

	public static void register() {
		stateLoaded = false;
		stateDirty = false;
		bootstrapComplete = false;
		active = false;
		completed = false;
		shellDissolving = false;
		scenePrepared = false;
		dissolveCursor = 0;
		nextSharedReminderTick = Long.MIN_VALUE;
		serverAnchor = null;
		PLAYER_STATES.clear();
		SHELL_DISSOLVE_ORDER.clear();

		ServerLifecycleEvents.SERVER_STARTED.register(SeasonStartSystem::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(SeasonStartSystem::onServerStopping);
		ServerTickEvents.END_SERVER_TICK.register(SeasonStartSystem::tickServer);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> onPlayerJoined(server, (ServerPlayer) handler.player)));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onPlayerJoined(newPlayer.level().getServer(), newPlayer));

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
				return true;
			}
			return onBeforeBlockBreak(level, serverPlayer, pos);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						Commands.literal("seasonstart")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(SeasonStartSystem::toggleSeasonStart)
								.then(Commands.literal("status")
										.executes(SeasonStartSystem::printStatus))
				)
		);
	}

	public static boolean isStartParticipant(ServerPlayer player) {
		return player != null && PLAYER_STATES.containsKey(player.getUUID()) && (active || shellDissolving);
	}

	public static boolean isInSharedPhase(ServerPlayer player) {
		PlayerSceneState state = player == null ? null : PLAYER_STATES.get(player.getUUID());
		return state != null && state.phase == PlayerPhase.SHARED;
	}

	public static boolean isLiveVoiceControlled(ServerPlayer player) {
		return active && player != null && PLAYER_STATES.containsKey(player.getUUID());
	}

	public static boolean canRelayLiveVoice(ServerPlayer player) {
		PlayerSceneState state = player == null ? null : PLAYER_STATES.get(player.getUUID());
		return active && state != null && state.phase == PlayerPhase.SHARED;
	}

	public static boolean canHearLiveVoice(ServerPlayer sender, ServerPlayer receiver) {
		if (!active || sender == null || receiver == null || sender == receiver) {
			return false;
		}
		PlayerSceneState senderState = PLAYER_STATES.get(sender.getUUID());
		PlayerSceneState receiverState = PLAYER_STATES.get(receiver.getUUID());
		return senderState != null
				&& receiverState != null
				&& senderState.phase == PlayerPhase.SHARED
				&& receiverState.phase == PlayerPhase.SHARED
				&& sender.level().dimension().equals(receiver.level().dimension());
	}

	public static boolean shouldSuppressOutgoingPacket(ServerPlayer receiver, Packet<?> packet) {
		if (receiver == null || packet == null || !isInSensoryIsolation(receiver)) {
			return false;
		}
		return packet instanceof ClientboundSoundPacket
				|| packet instanceof ClientboundSoundEntityPacket
				|| packet instanceof ClientboundStopSoundPacket
				|| packet instanceof ClientboundLevelParticlesPacket;
	}

	public static Vec3 resolveServerVoiceOrigin(MinecraftServer server, ServerPlayer focusPlayer) {
		ServerLevel level = server == null ? null : server.overworld();
		if (level == null) {
			return focusPlayer == null ? null : focusPlayer.position();
		}
		BlockPos anchor = resolveServerAnchor(level);
		return anchor == null ? null : new Vec3(anchor.getX() + 0.5D, anchor.getY() + 1.3D, anchor.getZ() + 0.5D);
	}

	public static void onServerFed(ServerLevel level, BlockPos fedServerPos) {
		if (!active || level == null || fedServerPos == null || serverAnchor == null || !serverAnchor.closerThan(fedServerPos, 3.0D)) {
			return;
		}
		ServerPlayer matched = null;
		double bestDistance = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
			if (state == null || state.phase != PlayerPhase.GUIDED_TO_SERVER) {
				continue;
			}
			double distance = player.distanceToSqr(fedServerPos.getX() + 0.5D, fedServerPos.getY() + 0.5D, fedServerPos.getZ() + 0.5D);
			if (distance < bestDistance) {
				bestDistance = distance;
				matched = player;
			}
		}
		if (matched != null && bestDistance <= 64.0D) {
			transitionPlayerToShared(level.getServer(), matched);
		}
	}

	private static void onServerStarted(MinecraftServer server) {
		loadState(server);
		ensureBootstrap(server);
		if (completed && !active) {
			removeSceneShellNow(server.overworld());
		}
		if (active) {
			rebuildActiveScene(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				assignOrRestorePlayer(server, player, false);
			}
		}
	}

	private static void onServerStopping(MinecraftServer server) {
		saveState(server);
	}

	private static void tickServer(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (active) {
			ServerLevel overworld = server.overworld();
			if (overworld != null) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
					if (state == null) {
						continue;
					}
					tickPlayerState(server, overworld, player, state);
				}
				tickSharedReminders(server);
			}
		}
		if (shellDissolving) {
			tickShellDissolve(server);
		}
	}

	private static void onPlayerJoined(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return;
		}
		if (active) {
			assignOrRestorePlayer(server, player, true);
		} else if (shellDissolving) {
			applyFreeState(player);
		}
	}

	private static boolean onBeforeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (!active || level == null || player == null || serverAnchor == null || !Level.OVERWORLD.equals(level.dimension())) {
			return true;
		}
		PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
		if (state == null) {
			return true;
		}
		SlotDefinition slot = resolveSlotDefinition(computeBoxGeometry(resolveServerAnchor(level)), state.slotIndex);
		if (slot != null && slot.orePos.equals(pos) && !state.minedIntroBitcoin) {
			handleIntroOreBroken(level, player, pos, state);
			return false;
		}
		if (isProtectedSceneBlock(level, pos)) {
			player.displayClientMessage(Component.literal("Эта часть стартовой сцены пока недоступна."), true);
			return false;
		}
		return true;
	}

	private static int toggleSeasonStart(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		if (active) {
			finishSeasonStart(server);
			context.getSource().sendSuccess(() -> Component.literal("Старт сезона завершён."), true);
		} else {
			startSeasonStart(server, false);
			context.getSource().sendSuccess(() -> Component.literal("Старт сезона запущен."), true);
		}
		return 1;
	}

	private static int printStatus(CommandContext<CommandSourceStack> context) {
		String anchorText = serverAnchor == null ? "none" : serverAnchor.getX() + ", " + serverAnchor.getY() + ", " + serverAnchor.getZ();
		context.getSource().sendSuccess(
				() -> Component.literal(
						"bootstrap=" + bootstrapComplete
								+ ", active=" + active
								+ ", completed=" + completed
								+ ", dissolving=" + shellDissolving
								+ ", players=" + PLAYER_STATES.size()
								+ ", anchor=" + anchorText
				),
				false
		);
		return 1;
	}

	private static void ensureBootstrap(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (overworld == null) {
			return;
		}
		if (serverAnchor == null) {
			serverAnchor = resolveBootstrapAnchor(overworld);
			stateDirty = true;
		}
		if (!isServerStructurePresent(overworld, serverAnchor)) {
			preparePlatformFloor(overworld);
			ServerBlock.placeServerStructure(overworld, serverAnchor, Direction.NORTH);
			stateDirty = true;
		}
		setDefaultSpawn(overworld);
		if (!bootstrapComplete) {
			bootstrapComplete = true;
			stateDirty = true;
			if (get().autoStartOnFirstLaunch && !completed) {
				startSeasonStart(server, true);
			}
		}
	}

	private static void rebuildActiveScene(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (overworld == null) {
			return;
		}
		ensureSceneBuilt(overworld);
		for (Map.Entry<UUID, PlayerSceneState> entry : PLAYER_STATES.entrySet()) {
			PlayerSceneState state = entry.getValue();
			if (state == null || state.minedIntroBitcoin) {
				continue;
			}
			SlotDefinition slot = resolveSlotDefinition(computeBoxGeometry(resolveServerAnchor(overworld)), state.slotIndex);
			if (slot != null) {
				placeIntroOre(overworld, slot);
			}
		}
	}

	private static void startSeasonStart(MinecraftServer server, boolean automatic) {
		if (server == null) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		ensureBootstrap(server);
		active = true;
		completed = false;
		shellDissolving = false;
		scenePrepared = false;
		dissolveCursor = 0;
		nextSharedReminderTick = Long.MIN_VALUE;
		PLAYER_STATES.clear();
		SHELL_DISSOLVE_ORDER.clear();
		SeasonStartVoiceSystem.resetSceneState();
		ensureSceneBuilt(overworld);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			assignOrRestorePlayer(server, player, true);
		}
		stateDirty = true;
	}

	private static void finishSeasonStart(MinecraftServer server) {
		if (server == null) {
			return;
		}
		active = false;
		completed = true;
		shellDissolving = true;
		scenePrepared = false;
		dissolveCursor = 0;
		nextSharedReminderTick = Long.MIN_VALUE;
		ServerLevel overworld = server.overworld();
		if (overworld != null) {
			SHELL_DISSOLVE_ORDER.clear();
			SHELL_DISSOLVE_ORDER.addAll(collectShellBlocks(computeBoxGeometry(resolveServerAnchor(overworld))));
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				applyFreeState(player);
			}
			SeasonStartVoiceSystem.fireTrigger(server, "season_finished", null);
		}
		stateDirty = true;
	}

	private static void assignOrRestorePlayer(MinecraftServer server, ServerPlayer player, boolean announceIntro) {
		if (server == null || player == null || !active) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return;
		}
		BoxGeometry geometry = computeBoxGeometry(resolveServerAnchor(overworld));
		PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
		boolean created = false;
		if (state == null) {
			state = new PlayerSceneState();
			state.slotIndex = allocateSlotIndex(geometry);
			state.phase = PlayerPhase.ISOLATED;
			PLAYER_STATES.put(player.getUUID(), state);
			created = true;
			stateDirty = true;
		}
		SlotDefinition slot = resolveSlotDefinition(geometry, state.slotIndex);
		if (slot == null) {
			return;
		}
		teleportPlayer(player, overworld, slot.spawnPos, slot.yaw);
		if (state.phase == PlayerPhase.ISOLATED || state.phase == PlayerPhase.GUIDED_TO_SERVER) {
			ensureIntroPlayerState(player, state, slot);
		} else {
			applyStateForPhase(player, state);
		}
		if (!state.minedIntroBitcoin) {
			placeIntroOre(overworld, slot);
		}
		if ((created || announceIntro) && state.phase == PlayerPhase.ISOLATED) {
			SeasonStartVoiceSystem.fireTrigger(server, "player_intro_assigned", player);
		}
	}

	private static void tickPlayerState(MinecraftServer server, ServerLevel level, ServerPlayer player, PlayerSceneState state) {
		BoxGeometry geometry = computeBoxGeometry(resolveServerAnchor(level));
		SlotDefinition slot = resolveSlotDefinition(geometry, state.slotIndex);
		if (slot == null) {
			return;
		}

		if (state.phase == PlayerPhase.ISOLATED) {
			ensureIntroPlayerState(player, state, slot);
			if (!state.minedIntroBitcoin) {
				placeIntroOre(level, slot);
			}
			return;
		}

		if (state.phase == PlayerPhase.GUIDED_TO_SERVER) {
			ensureIntroPlayerState(player, state, slot);
			tickGuidance(server, player, state);
			return;
		}

		if (state.phase == PlayerPhase.SHARED) {
			ensureSharedPlayerState(player);
		}
	}

	private static void tickGuidance(MinecraftServer server, ServerPlayer player, PlayerSceneState state) {
		if (server == null || player == null || state == null || player.level() == null || serverAnchor == null) {
			return;
		}
		long nowTick = player.level().getGameTime();
		if (nowTick < state.nextGuidanceTick) {
			return;
		}

		String trigger = resolveGuidanceTrigger(player);
		if (trigger != null && !trigger.equals(state.lastGuidanceTrigger)) {
			SeasonStartVoiceSystem.fireTrigger(server, trigger, player);
			state.lastGuidanceTrigger = trigger;
		} else if (trigger != null && trigger.equals(state.lastGuidanceTrigger)) {
			SeasonStartVoiceSystem.fireTrigger(server, trigger, player);
		}
		state.nextGuidanceTick = nowTick + get().guidanceRepeatTicks;
	}

	private static String resolveGuidanceTrigger(ServerPlayer player) {
		if (player == null || serverAnchor == null) {
			return null;
		}
		boolean hasBitcoin = hasBitcoin(player);
		Vec3 target = new Vec3(serverAnchor.getX() + 0.5D, player.getY(), serverAnchor.getZ() + 0.5D);
		Vec3 toTarget = target.subtract(player.position());
		double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
		if (horizontalDistance <= 2.4D && hasBitcoin) {
			return "guide_drop_coin";
		}
		if (horizontalDistance <= 1.25D) {
			return "guide_forward";
		}

		double yawRadians = Math.toRadians(player.getYRot());
		Vec3 facing = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians)).normalize();
		Vec3 horizontalTarget = new Vec3(toTarget.x, 0.0D, toTarget.z).normalize();
		double alignment = facing.dot(horizontalTarget);
		double cross = facing.x * horizontalTarget.z - facing.z * horizontalTarget.x;

		if (alignment < -0.25D) {
			return "guide_back";
		}
		if (alignment < 0.65D) {
			return cross > 0.0D ? "guide_turn_left" : "guide_turn_right";
		}
		return "guide_forward";
	}

	private static void transitionPlayerToShared(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null || serverAnchor == null) {
			return;
		}
		PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
		if (state == null || state.phase == PlayerPhase.SHARED) {
			return;
		}
		state.phase = PlayerPhase.SHARED;
		state.poweredServer = true;
		state.lastGuidanceTrigger = "";
		state.nextGuidanceTick = Long.MAX_VALUE;
		stateDirty = true;

		applySharedPlayerState(player);
		spawnLightOnlineParticles(server.overworld());
		SeasonStartVoiceSystem.fireTrigger(server, "player_powered_server", player);
		SeasonStartVoiceSystem.fireTrigger(server, "first_player_shared_phase", player);
		seedSharedBitcoins(server.overworld());
		if (nextSharedReminderTick == Long.MIN_VALUE && player.level() != null) {
			nextSharedReminderTick = player.level().getGameTime() + get().sharedReminderIntervalTicks;
		}
	}

	private static void handleIntroOreBroken(ServerLevel level, ServerPlayer player, BlockPos pos, PlayerSceneState state) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 16, 0.3D, 0.3D, 0.3D, 0.02D);
		level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.85F);
		if (!hasBitcoin(player)) {
			giveOrDrop(player, new ItemStack(ModItems.BITCOIN));
		}
		state.minedIntroBitcoin = true;
		state.phase = PlayerPhase.GUIDED_TO_SERVER;
		state.nextGuidanceTick = 0L;
		state.lastGuidanceTrigger = "";
		stateDirty = true;
		SeasonStartVoiceSystem.fireTrigger(level.getServer(), "player_mined_intro_bitcoin", player);
	}

	private static void tickSharedReminders(MinecraftServer server) {
		if (server == null || nextSharedReminderTick == Long.MIN_VALUE || server.overworld() == null) {
			return;
		}
		if (countSharedPlayers() <= 0) {
			return;
		}
		long nowTick = server.overworld().getGameTime();
		if (nowTick < nextSharedReminderTick) {
			return;
		}
		SeasonStartVoiceSystem.fireTrigger(server, "shared_phase_reminder", null);
		nextSharedReminderTick = nowTick + get().sharedReminderIntervalTicks;
	}

	private static int countSharedPlayers() {
		int count = 0;
		for (PlayerSceneState state : PLAYER_STATES.values()) {
			if (state != null && state.phase == PlayerPhase.SHARED) {
				count++;
			}
		}
		return count;
	}

	private static void tickShellDissolve(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (overworld == null || SHELL_DISSOLVE_ORDER.isEmpty()) {
			shellDissolving = false;
			return;
		}
		for (int i = 0; i < DISSOLVE_BATCH_BLOCKS && dissolveCursor < SHELL_DISSOLVE_ORDER.size(); i++, dissolveCursor++) {
			BlockPos pos = SHELL_DISSOLVE_ORDER.get(dissolveCursor);
			if (!overworld.getBlockState(pos).is(Blocks.BLACK_CONCRETE)) {
				continue;
			}
			overworld.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4, 0.2D, 0.2D, 0.2D, 0.01D);
			overworld.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		if (dissolveCursor >= SHELL_DISSOLVE_ORDER.size()) {
			shellDissolving = false;
			SHELL_DISSOLVE_ORDER.clear();
			dissolveCursor = 0;
			PLAYER_STATES.clear();
			stateDirty = true;
		}
	}

	private static void ensureSceneBuilt(ServerLevel level) {
		if (level == null || serverAnchor == null || scenePrepared) {
			return;
		}
		preparePlatformFloor(level);
		buildSceneShell(level);
		if (!isServerStructurePresent(level, serverAnchor)) {
			ServerBlock.placeServerStructure(level, serverAnchor, Direction.NORTH);
		}
		scenePrepared = true;
	}

	private static void preparePlatformFloor(ServerLevel level) {
		BoxGeometry geometry = computeBoxGeometry(resolveServerAnchor(level));
		for (int x = geometry.minX; x <= geometry.maxX; x++) {
			for (int z = geometry.minZ; z <= geometry.maxZ; z++) {
				BlockPos floorPos = new BlockPos(x, geometry.floorY, z);
				level.setBlock(floorPos, Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
				for (int y = geometry.floorY + 1; y <= geometry.roofY - 1; y++) {
					BlockPos clearPos = new BlockPos(x, y, z);
					if (isServerStructureFootprint(clearPos)) {
						continue;
					}
					if (!level.getBlockState(clearPos).isAir()) {
						level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	private static void buildSceneShell(ServerLevel level) {
		BoxGeometry geometry = computeBoxGeometry(resolveServerAnchor(level));
		for (BlockPos pos : collectShellBlocks(geometry)) {
			level.setBlock(pos, Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
		}
	}

	private static void removeSceneShellNow(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		for (BlockPos pos : collectShellBlocks(computeBoxGeometry(serverAnchor))) {
			if (level.getBlockState(pos).is(Blocks.BLACK_CONCRETE)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		scenePrepared = false;
	}

	private static List<BlockPos> collectShellBlocks(BoxGeometry geometry) {
		List<BlockPos> blocks = new ArrayList<>();
		for (int y = geometry.floorY + 1; y <= geometry.roofY; y++) {
			for (int x = geometry.minX; x <= geometry.maxX; x++) {
				for (int z = geometry.minZ; z <= geometry.maxZ; z++) {
					boolean wall = x == geometry.minX || x == geometry.maxX || z == geometry.minZ || z == geometry.maxZ;
					boolean roof = y == geometry.roofY;
					if (wall || roof) {
						blocks.add(new BlockPos(x, y, z));
					}
				}
			}
		}
		return blocks;
	}

	private static boolean isProtectedSceneBlock(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || serverAnchor == null) {
			return false;
		}
		BoxGeometry geometry = computeBoxGeometry(serverAnchor);
		if (!isInsideFootprint(geometry, pos)) {
			return false;
		}
		if (level.getBlockState(pos).is(Blocks.BLACK_CONCRETE)) {
			return true;
		}
		if (isServerStructureFootprint(pos)) {
			return true;
		}
		for (PlayerSceneState state : PLAYER_STATES.values()) {
			SlotDefinition slot = resolveSlotDefinition(geometry, state.slotIndex);
			if (slot != null && (slot.spawnFloorPos.equals(pos) || slot.orePos.equals(pos))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isInsideFootprint(BoxGeometry geometry, BlockPos pos) {
		return pos.getX() >= geometry.minX
				&& pos.getX() <= geometry.maxX
				&& pos.getZ() >= geometry.minZ
				&& pos.getZ() <= geometry.maxZ
				&& pos.getY() >= geometry.floorY
				&& pos.getY() <= geometry.roofY;
	}

	private static boolean isServerStructureFootprint(BlockPos pos) {
		if (serverAnchor == null || pos == null) {
			return false;
		}
		return ServerStructureBreakSystem.getStructurePositions(serverAnchor, Direction.Axis.Z).contains(pos);
	}

	private static void placeIntroOre(ServerLevel level, SlotDefinition slot) {
		if (level == null || slot == null) {
			return;
		}
		level.setBlock(slot.orePos.below(), Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
		level.setBlock(slot.orePos, ModBlocks.BITCOIN_ORE.defaultBlockState(), 3);
	}

	private static void seedSharedBitcoins(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		BoxGeometry geometry = computeBoxGeometry(serverAnchor);
		List<BlockPos> orePositions = List.of(
				serverAnchor.offset(-6, 0, 0),
				serverAnchor.offset(6, 0, 0),
				serverAnchor.offset(0, 0, -6),
				serverAnchor.offset(0, 0, 6),
				serverAnchor.offset(-4, 0, -4),
				serverAnchor.offset(4, 0, -4),
				serverAnchor.offset(-4, 0, 4),
				serverAnchor.offset(4, 0, 4)
		);
		for (BlockPos origin : orePositions) {
			BlockPos orePos = new BlockPos(origin.getX(), geometry.floorY + 1, origin.getZ());
			if (!isInsideFootprint(geometry, orePos) || isServerStructureFootprint(orePos)) {
				continue;
			}
			if (level.getBlockState(orePos).isAir()) {
				level.setBlock(orePos, ModBlocks.DEEPSLATE_BITCOIN_ORE.defaultBlockState(), 3);
			}
		}
	}

	private static void ensureIntroPlayerState(ServerPlayer player, PlayerSceneState state, SlotDefinition slot) {
		if (player == null || state == null || slot == null || serverAnchor == null) {
			return;
		}
		Vec3 target = slot.spawnPos;
		if (!player.level().dimension().equals(Level.OVERWORLD) || player.distanceToSqr(target.x, target.y, target.z) > 225.0D) {
			MinecraftServer server = player.level().getServer();
			ServerLevel overworld = server == null ? null : server.overworld();
			teleportPlayer(player, overworld, target, slot.yaw);
		}
		player.setSilent(true);
		player.setGameMode(GameType.SURVIVAL);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, get().introBlindnessTicks, 0, false, false, true));
		if (!player.hasEffect(MobEffects.INVISIBILITY)) {
			player.addEffect(ServerAbsoluteInvisibilitySystem.createEffectInstance());
		}
		ServerAbsoluteInvisibilitySystem.activate(player);
		ensureIntroTool(player);
	}

	private static void ensureSharedPlayerState(ServerPlayer player) {
		if (player == null) {
			return;
		}
		applySharedPlayerState(player);
	}

	private static void applySharedPlayerState(ServerPlayer player) {
		ServerAbsoluteInvisibilitySystem.deactivate(player);
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.INVISIBILITY);
		player.setSilent(false);
		player.setGameMode(GameType.SURVIVAL);
	}

	private static void applyFreeState(ServerPlayer player) {
		if (player == null) {
			return;
		}
		ServerAbsoluteInvisibilitySystem.deactivate(player);
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.INVISIBILITY);
		player.setSilent(false);
		player.setGameMode(GameType.SURVIVAL);
		removeIntroTool(player);
	}

	private static void applyStateForPhase(ServerPlayer player, PlayerSceneState state) {
		if (state == null || player == null) {
			return;
		}
		if (state.phase == PlayerPhase.SHARED) {
			applySharedPlayerState(player);
			return;
		}
		if (state.phase == PlayerPhase.FREE) {
			applyFreeState(player);
		}
	}

	private static boolean isInSensoryIsolation(ServerPlayer player) {
		PlayerSceneState state = player == null ? null : PLAYER_STATES.get(player.getUUID());
		return active
				&& state != null
				&& (state.phase == PlayerPhase.ISOLATED || state.phase == PlayerPhase.GUIDED_TO_SERVER);
	}

	private static void ensureIntroTool(ServerPlayer player) {
		Inventory inventory = player == null ? null : player.getInventory();
		if (inventory == null) {
			return;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack existing = inventory.getItem(slot);
			if (isIntroTool(existing)) {
				return;
			}
		}
		giveOrDrop(player, INTRO_TOOL_TEMPLATE.copy());
	}

	private static void removeIntroTool(ServerPlayer player) {
		Inventory inventory = player == null ? null : player.getInventory();
		if (inventory == null) {
			return;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (isIntroTool(stack)) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	private static boolean isIntroTool(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(ModItems.SPECIAL_PICKAXE)) {
			return false;
		}
		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		return customName != null && "Пусковой инструмент".equals(customName.getString());
	}

	private static boolean hasBitcoin(ServerPlayer player) {
		Inventory inventory = player == null ? null : player.getInventory();
		if (inventory == null) {
			return false;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(ModItems.BITCOIN)) {
				return true;
			}
		}
		return false;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return;
		}
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static void spawnLightOnlineParticles(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		level.sendParticles(ParticleTypes.END_ROD, serverAnchor.getX() + 0.5D, serverAnchor.getY() + 1.2D, serverAnchor.getZ() + 0.5D, 80, 3.5D, 2.0D, 3.5D, 0.03D);
		level.playSound(null, serverAnchor, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1.1F, 1.0F);
	}

	private static void teleportPlayer(ServerPlayer player, ServerLevel level, Vec3 target, float yaw) {
		if (player == null || level == null || target == null) {
			return;
		}
		if (player.level().dimension().equals(level.dimension())) {
			player.connection.teleport(target.x, target.y, target.z, yaw, 0.0F);
			return;
		}
		player.teleportTo(level, target.x, target.y, target.z, ABSOLUTE_TELEPORT, yaw, 0.0F, false);
	}

	private static boolean isServerStructurePresent(ServerLevel level, BlockPos anchor) {
		return level != null && anchor != null && level.getBlockState(anchor).is(ModBlocks.SERVER);
	}

	private static BlockPos resolveBootstrapAnchor(ServerLevel level) {
		int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
		if (surfaceY < level.getMinY() + 4) {
			surfaceY = 64;
		}
		return new BlockPos(0, surfaceY, 0);
	}

	private static BlockPos resolveServerAnchor(ServerLevel level) {
		if (serverAnchor != null) {
			return serverAnchor;
		}
		serverAnchor = resolveBootstrapAnchor(level);
		return serverAnchor;
	}

	private static void setDefaultSpawn(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		BlockPos spawn = serverAnchor.offset(0, 0, get().boxHalfDepth - 3);
		MinecraftServer server = level.getServer();
		if (server != null) {
			server.setRespawnData(LevelData.RespawnData.of(level.dimension(), spawn, 180.0F, 0.0F));
		}
	}

	private static int allocateSlotIndex(BoxGeometry geometry) {
		Set<Integer> used = new LinkedHashSet<>();
		for (PlayerSceneState state : PLAYER_STATES.values()) {
			used.add(state.slotIndex);
		}
		List<SlotDefinition> slots = collectSlotDefinitions(geometry);
		for (int index = 0; index < slots.size(); index++) {
			if (!used.contains(index)) {
				return index;
			}
		}
		return Math.max(0, PLAYER_STATES.size() % Math.max(1, slots.size()));
	}

	private static SlotDefinition resolveSlotDefinition(BoxGeometry geometry, int slotIndex) {
		List<SlotDefinition> slots = collectSlotDefinitions(geometry);
		if (slots.isEmpty()) {
			return null;
		}
		return slots.get(Math.floorMod(slotIndex, slots.size()));
	}

	private static List<SlotDefinition> collectSlotDefinitions(BoxGeometry geometry) {
		int inset = 3;
		int spacing = get().playerSlotSpacing;
		int floorY = geometry.floorY;
		List<SlotDefinition> slots = new ArrayList<>();
		for (int x = geometry.minX + inset; x <= geometry.maxX - inset; x += spacing) {
			slots.add(createSlot(floorY, x, geometry.minZ + inset, Direction.SOUTH));
		}
		for (int z = geometry.minZ + inset + spacing; z <= geometry.maxZ - inset; z += spacing) {
			slots.add(createSlot(floorY, geometry.maxX - inset, z, Direction.WEST));
		}
		for (int x = geometry.maxX - inset - spacing; x >= geometry.minX + inset; x -= spacing) {
			slots.add(createSlot(floorY, x, geometry.maxZ - inset, Direction.NORTH));
		}
		for (int z = geometry.maxZ - inset - spacing; z >= geometry.minZ + inset + spacing; z -= spacing) {
			slots.add(createSlot(floorY, geometry.minX + inset, z, Direction.EAST));
		}
		return slots;
	}

	private static SlotDefinition createSlot(int floorY, int x, int z, Direction facing) {
		BlockPos floor = new BlockPos(x, floorY, z);
		BlockPos orePos = floor.relative(facing).above();
		Vec3 spawnPos = new Vec3(x + 0.5D, floorY + 1.0D, z + 0.5D);
		return new SlotDefinition(floor, orePos, spawnPos, facing.toYRot());
	}

	private static BoxGeometry computeBoxGeometry(BlockPos anchor) {
		int halfWidth = get().boxHalfWidth;
		int halfDepth = get().boxHalfDepth;
		int floorY = anchor.getY() - 1;
		return new BoxGeometry(
				anchor.getX() - halfWidth,
				anchor.getX() + halfWidth,
				anchor.getZ() - halfDepth,
				anchor.getZ() + halfDepth,
				floorY,
				floorY + get().boxHeight
		);
	}

	private static Path getStatePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE_NAME);
	}

	private static void loadState(MinecraftServer server) {
		Path path = getStatePath(server);
		PLAYER_STATES.clear();
		stateLoaded = true;
		stateDirty = false;
		scenePrepared = false;
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			PersistedState state = STATE_GSON.fromJson(reader, PersistedState.class);
			if (state == null) {
				return;
			}
			bootstrapComplete = state.bootstrapComplete;
			active = state.active;
			completed = state.completed;
			serverAnchor = parseBlockPos(state.serverAnchor);
			if (state.players != null) {
				for (Map.Entry<String, PersistedPlayerState> entry : state.players.entrySet()) {
					UUID playerId = parseUuid(entry.getKey());
					PersistedPlayerState persisted = entry.getValue();
					if (playerId == null || persisted == null) {
						continue;
					}
					PlayerSceneState runtime = new PlayerSceneState();
					runtime.slotIndex = persisted.slotIndex;
					runtime.minedIntroBitcoin = persisted.minedIntroBitcoin;
					runtime.poweredServer = persisted.poweredServer;
					runtime.phase = PlayerPhase.byId(persisted.phase);
					if (runtime.poweredServer) {
						runtime.phase = PlayerPhase.SHARED;
					}
					PLAYER_STATES.put(playerId, runtime);
				}
			}
		} catch (Exception exception) {
			Lg2.LOGGER.warn("Failed to read season-start state {}", path, exception);
		}
	}

	private static void saveState(MinecraftServer server) {
		if (!stateLoaded || !stateDirty || server == null) {
			return;
		}
		PersistedState state = new PersistedState();
		state.bootstrapComplete = bootstrapComplete;
		state.active = active;
		state.completed = completed;
		state.serverAnchor = serializeBlockPos(serverAnchor);
		state.players = new LinkedHashMap<>();
		for (Map.Entry<UUID, PlayerSceneState> entry : PLAYER_STATES.entrySet()) {
			PlayerSceneState runtime = entry.getValue();
			if (runtime == null) {
				continue;
			}
			PersistedPlayerState persisted = new PersistedPlayerState();
			persisted.slotIndex = runtime.slotIndex;
			persisted.phase = runtime.phase.id;
			persisted.minedIntroBitcoin = runtime.minedIntroBitcoin;
			persisted.poweredServer = runtime.poweredServer;
			state.players.put(entry.getKey().toString(), persisted);
		}
		Path path = getStatePath(server);
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				STATE_GSON.toJson(state, writer);
			}
			stateDirty = false;
		} catch (Exception exception) {
			Lg2.LOGGER.warn("Failed to save season-start state {}", path, exception);
		}
	}

	private static String serializeBlockPos(BlockPos pos) {
		return pos == null ? "" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static BlockPos parseBlockPos(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String[] parts = value.split(",");
		if (parts.length != 3) {
			return null;
		}
		try {
			return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static UUID parseUuid(String value) {
		try {
			return value == null || value.isBlank() ? null : UUID.fromString(value);
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static ItemStack createIntroToolTemplate() {
		ItemStack stack = new ItemStack(ModItems.SPECIAL_PICKAXE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Пусковой инструмент"));
		return stack;
	}

	private enum PlayerPhase {
		ISOLATED("isolated"),
		GUIDED_TO_SERVER("guided"),
		SHARED("shared"),
		FREE("free");

		private final String id;

		PlayerPhase(String id) {
			this.id = id;
		}

		private static PlayerPhase byId(String id) {
			for (PlayerPhase value : values()) {
				if (value.id.equalsIgnoreCase(id == null ? "" : id)) {
					return value;
				}
			}
			return ISOLATED;
		}
	}

	private static final class PlayerSceneState {
		private int slotIndex;
		private PlayerPhase phase = PlayerPhase.ISOLATED;
		private boolean minedIntroBitcoin;
		private boolean poweredServer;
		private long nextGuidanceTick = 0L;
		private String lastGuidanceTrigger = "";
	}

	private record BoxGeometry(int minX, int maxX, int minZ, int maxZ, int floorY, int roofY) {
	}

	private record SlotDefinition(BlockPos spawnFloorPos, BlockPos orePos, Vec3 spawnPos, float yaw) {
	}

	private static final class PersistedState {
		private boolean bootstrapComplete;
		private boolean active;
		private boolean completed;
		private String serverAnchor;
		private Map<String, PersistedPlayerState> players;
	}

	private static final class PersistedPlayerState {
		private int slotIndex;
		private String phase;
		private boolean minedIntroBitcoin;
		private boolean poweredServer;
	}
}
