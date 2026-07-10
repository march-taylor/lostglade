package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.lostglade.block.ServerBlock;
import com.lostglade.config.SeasonStartConfig;
import com.lostglade.item.ModItems;
import com.lostglade.util.ItemDisplayHitboxHelper;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.lostglade.config.SeasonStartConfig.get;

public final class SeasonStartSystem {
	private static final Gson STATE_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String STATE_FILE_NAME = "lg2-season-start-state.json";
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final int DISSOLVE_BATCH_BLOCKS = 96;
	private static final ItemStack INTRO_TOOL_TEMPLATE = createIntroToolTemplate();
	private static final long WAITING_START_INITIAL_PROMPT_TICKS = 20L * 3L;
	private static final long WAITING_START_REPEAT_TICKS = 20L * 15L;
	private static final long INTRO_IDLE_TRIGGER_TICKS = 20L * 9L;
	private static final long INTRO_IDLE_REPEAT_TICKS = 20L * 10L;
	private static final long INTRO_LEAVE_REPEAT_TICKS = 20L * 7L;
	private static final long INTRO_SPIN_REPEAT_TICKS = 20L * 6L;
	private static final long INTRO_JUMP_REPEAT_TICKS = 20L * 4L;
	private static final long INTRO_AIR_PUNCH_REPEAT_TICKS = 20L * 5L;
	private static final long INTRO_TARGET_REACTION_REPEAT_TICKS = 20L * 9L;
	private static final long GUIDANCE_EVALUATE_TICKS = 4L;
	private static final long GUIDANCE_MIN_VOICE_GAP_TICKS = 20L;
	private static final long GUIDANCE_CATEGORY_COOLDOWN_TICKS = 10L;
	private static final long GUIDANCE_CORRECTION_COOLDOWN_TICKS = 8L;
	private static final long GUIDANCE_FORWARD_COOLDOWN_TICKS = 16L;
	private static final long GUIDANCE_STALL_COOLDOWN_TICKS = 24L;
	private static final long GUIDANCE_RECOVER_REACTION_WINDOW_TICKS = 28L;
	private static final long GUIDANCE_STALL_AFTER_ALIGNMENT_TICKS = 20L;
	private static final long GUIDANCE_STALL_AFTER_TURN_TICKS = 18L;
	private static final int BARRIER_FLOOR_DEPTH = 5;
	private static final double INTRO_ACTIVITY_MOVE_SQR = 0.04D * 0.04D;
	private static final double INTRO_SPIN_TRIGGER_SCORE = 200.0D;
	private static final double INTRO_PICK_REACH = 5.0D;
	private static final double INTRO_TARGET_VERTICAL_GUIDANCE_DISTANCE = 4.8D;
	private static final double INTRO_TARGET_VERTICAL_YAW_WINDOW = 14.0D;
	private static final double INTRO_TARGET_VERTICAL_PITCH_THRESHOLD = 24.0D;
	private static final float INTRO_ACTIVITY_YAW_DEGREES = 6.0F;
	private static final float INTRO_ACTIVITY_PITCH_DEGREES = 4.0F;
	private static final double GUIDANCE_LOCK_ANGLE = 6.0D;
	private static final double GUIDANCE_MICRO_ANGLE = 12.0D;
	private static final double GUIDANCE_MEDIUM_ANGLE = 26.0D;
	private static final double GUIDANCE_HARD_ANGLE = 62.0D;
	private static final double GUIDANCE_TURN_AROUND_ANGLE = 140.0D;
	private static final double GUIDANCE_CLOSE_APPROACH_DISTANCE = 3.0D;
	private static final double GUIDANCE_QUIET_DISTANCE = 2.05D;
	private static final double GUIDANCE_DROP_DISTANCE = 1.35D;
	private static final double GUIDANCE_SERVER_SIGHT_DISTANCE = 4.2D;
	private static final double GUIDANCE_PROGRESS_AWAY = 0.16D;
	private static final double GUIDANCE_PROGRESS_TOWARD = -0.14D;
	private static final double GUIDANCE_STALL_DELTA = 0.05D;
	private static final double GUIDANCE_RECOVER_WORSEN_THRESHOLD = 8.0D;
	private static final String START_WORD_EN = "start";
	private static final String START_WORD_RU = "старт";
	private static final String[] WAITING_START_PROMPT_TRIGGERS = {
			"player_waiting_start_prompt_01",
			"player_waiting_start_prompt_02",
			"player_waiting_start_prompt_03"
	};
	private static final String[] WAITING_START_WRONG_TRIGGERS = {
			"player_waiting_start_wrong_01",
			"player_waiting_start_wrong_02"
	};
	private static final String[] INTRO_TARGET_LOCK_TRIGGERS = {
			"intro_target_locked_01",
			"intro_target_locked_02",
			"intro_target_locked_03"
	};
	private static final String[] INTRO_TARGET_STARE_TRIGGERS = {
			"intro_target_stare_01",
			"intro_target_stare_02"
	};
	private static final String[] INTRO_TARGET_LOOK_UP_TRIGGERS = {
			"intro_target_look_up_01",
			"intro_target_look_up_02"
	};
	private static final String[] INTRO_TARGET_LOOK_DOWN_TRIGGERS = {
			"intro_target_look_down_01",
			"intro_target_look_down_02"
	};
	private static final String[] INTRO_GUIDE_WRONG_WAY_TRIGGERS = {
			"intro_guide_wrong_way_01",
			"intro_guide_wrong_way_02",
			"intro_guide_wrong_way_03"
	};
	private static final String[] INTRO_GUIDE_ROUTE_START_TRIGGERS = {
			"intro_guide_route_start_01",
			"intro_guide_route_start_02",
			"intro_guide_route_start_03"
	};
	private static final String[] GUIDE_TURN_LEFT_HARD_TRIGGERS = {
			"guide_turn_left_hard_01",
			"guide_turn_left_hard_02",
			"guide_turn_left_hard_03"
	};
	private static final String[] GUIDE_TURN_LEFT_MEDIUM_TRIGGERS = {
			"guide_turn_left_medium_01",
			"guide_turn_left_medium_02",
			"guide_turn_left_medium_03"
	};
	private static final String[] GUIDE_TURN_LEFT_SOFT_TRIGGERS = {
			"guide_turn_left_soft_01",
			"guide_turn_left_soft_02",
			"guide_turn_left_soft_03"
	};
	private static final String[] GUIDE_TURN_RIGHT_HARD_TRIGGERS = {
			"guide_turn_right_hard_01",
			"guide_turn_right_hard_02",
			"guide_turn_right_hard_03"
	};
	private static final String[] GUIDE_TURN_RIGHT_MEDIUM_TRIGGERS = {
			"guide_turn_right_medium_01",
			"guide_turn_right_medium_02",
			"guide_turn_right_medium_03"
	};
	private static final String[] GUIDE_TURN_RIGHT_SOFT_TRIGGERS = {
			"guide_turn_right_soft_01",
			"guide_turn_right_soft_02",
			"guide_turn_right_soft_03"
	};
	private static final String[] GUIDE_TURN_LEFT_RECOVER_TRIGGERS = {
			"guide_turn_left_recover_01",
			"guide_turn_left_recover_02",
			"guide_turn_left_recover_03"
	};
	private static final String[] GUIDE_TURN_RIGHT_RECOVER_TRIGGERS = {
			"guide_turn_right_recover_01",
			"guide_turn_right_recover_02",
			"guide_turn_right_recover_03"
	};
	private static final String[] GUIDE_TURN_AROUND_LEFT_TRIGGERS = {
			"guide_turn_around_left_01",
			"guide_turn_around_left_02",
			"guide_turn_around_left_03"
	};
	private static final String[] GUIDE_TURN_AROUND_RIGHT_TRIGGERS = {
			"guide_turn_around_right_01",
			"guide_turn_around_right_02",
			"guide_turn_around_right_03"
	};
	private static final String[] GUIDE_LOCKED_ON_TRIGGERS = {
			"guide_locked_on_01",
			"guide_locked_on_02",
			"guide_locked_on_03",
			"guide_locked_on_04"
	};
	private static final String[] GUIDE_HEADING_LOST_TRIGGERS = {
			"guide_heading_lost_01",
			"guide_heading_lost_02",
			"guide_heading_lost_03"
	};
	private static final String[] GUIDE_SERVER_IN_SIGHT_TRIGGERS = {
			"guide_server_in_sight_01",
			"guide_server_in_sight_02",
			"guide_server_in_sight_03"
	};
	private static final String[] GUIDE_CLOSE_PRESENCE_TRIGGERS = {
			"guide_close_presence_01",
			"guide_close_presence_02",
			"guide_close_presence_03"
	};
	private static final String[] GUIDE_FORWARD_FAR_TRIGGERS = {
			"guide_forward_far_01",
			"guide_forward_far_02",
			"guide_forward_far_03"
	};
	private static final String[] GUIDE_FORWARD_MID_TRIGGERS = {
			"guide_forward_mid_01",
			"guide_forward_mid_02",
			"guide_forward_mid_03"
	};
	private static final String[] GUIDE_FORWARD_NEAR_TRIGGERS = {
			"guide_forward_near_01",
			"guide_forward_near_02",
			"guide_forward_near_03"
	};
	private static final String[] GUIDE_FORWARD_CLOSE_TRIGGERS = {
			"guide_forward_close_01",
			"guide_forward_close_02",
			"guide_forward_close_03"
	};
	private static final String[] GUIDE_DROP_COIN_TRIGGERS = {
			"guide_drop_coin_01",
			"guide_drop_coin_02",
			"guide_drop_coin_03",
			"guide_drop_coin_04"
	};
	private static final String[] GUIDE_WRONG_WAY_TRIGGERS = {
			"guide_wrong_way_01",
			"guide_wrong_way_02",
			"guide_wrong_way_03",
			"guide_wrong_way_04"
	};
	private static final String[] GUIDE_STALL_ALIGNED_TRIGGERS = {
			"guide_stall_aligned_01",
			"guide_stall_aligned_02",
			"guide_stall_aligned_03"
	};
	private static final String[] GUIDE_STALL_MISALIGNED_TRIGGERS = {
			"guide_stall_misaligned_01",
			"guide_stall_misaligned_02",
			"guide_stall_misaligned_03"
	};
	private static final String[] GUIDE_PASSED_SERVER_TRIGGERS = {
			"guide_passed_server_01",
			"guide_passed_server_02",
			"guide_passed_server_03"
	};
	private static final int SHARED_ACTIVE_ORES_PER_PLAYER = 2;
	private static final int SHARED_LAUNCH_BITCOINS_PER_ASSIGNED_PLAYER = 45;
	private static final int SHARED_ORE_MIN_Y_OFFSET = 1;
	private static final int SHARED_ORE_MAX_Y_OFFSET = 4;
	private static final double SHARED_ORE_SERVER_BUFFER = 4.0D;
	private static final double SHARED_FEED_RADIUS = 0.35D;
	private static final double SHARED_FEED_PLAYER_MATCH_DISTANCE = 8.0D;
	private static final long SHARED_IDLE_REMINDER_TICKS = 20L * 45L;
	private static final long SHARED_FINISH_DELAY_TICKS = 20L * 2L;
	private static final int[] SHARED_LAUNCH_MILESTONES = {50, 90, 100};
	private static final String STARTUP_WORLDGEN_DISPLAY_TAG = "lg2_season_start_display";
	private static final int STARTUP_WORLDGEN_FRAME_COUNT = 35;
	private static final float STARTUP_WORLDGEN_VIEW_RANGE = 96.0F;
	private static final float STARTUP_WORLDGEN_MARGIN_BLOCKS = 8.0F;
	private static final float STARTUP_WORLDGEN_THICKNESS_SCALE = 0.25F;
	private static final double STARTUP_WORLDGEN_ROOF_OFFSET = 0.02D;
	private static final Brightness STARTUP_WORLDGEN_BRIGHTNESS = Brightness.FULL_BRIGHT;

	private static final Map<UUID, PlayerSceneState> PLAYER_STATES = new LinkedHashMap<>();
	private static final List<BlockPos> SHELL_DISSOLVE_ORDER = new ArrayList<>();
	private static final Set<BlockPos> SHARED_BITCOIN_POSITIONS = new LinkedHashSet<>();
	private static boolean stateLoaded = false;
	private static boolean stateDirty = false;
	private static boolean bootstrapComplete = false;
	private static boolean active = false;
	private static boolean completed = false;
	private static boolean shellDissolving = false;
	private static boolean scenePrepared = false;
	private static int dissolveCursor = 0;
	private static long nextSharedReminderTick = Long.MIN_VALUE;
	private static long lastSharedLaunchProgressTick = Long.MIN_VALUE;
	private static long pendingSharedFinishTick = Long.MIN_VALUE;
	private static int sharedLaunchCollectedBitcoins = 0;
	private static int sharedLaunchRequiredBitcoins = 0;
	private static int sharedLaunchMilestoneCursor = 0;
	private static boolean sharedLaunchIntroTriggered = false;
	private static int startupWorldgenFrameIndex = Integer.MIN_VALUE;
	private static ServerBossEvent sharedLaunchBossBar = null;
	private static Difficulty difficultyBeforeSeasonStart = null;
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
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		sharedLaunchCollectedBitcoins = 0;
		sharedLaunchRequiredBitcoins = 0;
		sharedLaunchMilestoneCursor = 0;
		sharedLaunchIntroTriggered = false;
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
		sharedLaunchBossBar = null;
		difficultyBeforeSeasonStart = null;
		serverAnchor = null;
		PLAYER_STATES.clear();
		SHELL_DISSOLVE_ORDER.clear();
		SHARED_BITCOIN_POSITIONS.clear();

		ServerLifecycleEvents.SERVER_STARTED.register(SeasonStartSystem::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPING.register(SeasonStartSystem::onServerStopping);
		ServerTickEvents.START_SERVER_TICK.register(SeasonStartSystem::preTickServer);
		ServerTickEvents.END_SERVER_TICK.register(SeasonStartSystem::tickServer);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> onPlayerJoined(server, (ServerPlayer) handler.player)));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> onPlayerJoined(newPlayer.level().getServer(), newPlayer));
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(SeasonStartSystem::onAllowChatMessage);

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

	public static boolean shouldOverrideStabilityHud() {
		return active && !completed;
	}

	public static float getStartupHudProgress() {
		if (sharedLaunchRequiredBitcoins <= 0) {
			return 0.0F;
		}
		return Mth.clamp((float) sharedLaunchCollectedBitcoins / (float) sharedLaunchRequiredBitcoins, 0.0F, 1.0F);
	}

	public static boolean shouldSuspendStabilitySystem() {
		return active && !completed;
	}

	public static boolean shouldUseFlatNarrationMix() {
		return active && !completed;
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
			transitionPlayerAfterFirstPayment(level.getServer(), matched);
			return;
		}
		if (countSharedPlayers() > 0) {
			incrementSharedLaunchProgress(level.getServer(), 1);
		}
	}

	public static void onPlayerAnimate(ServerPlayer player) {
		if (!active || player == null || !(player.level() instanceof ServerLevel level) || serverAnchor == null) {
			return;
		}
		PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
		if (state == null || state.phase != PlayerPhase.ISOLATED || state.minedIntroBitcoin) {
			return;
		}
		SlotDefinition slot = resolveSlotDefinition(computeBarrierGeometry(resolveServerAnchor(level)), state.slotIndex);
		if (slot == null) {
			return;
		}

		long nowTick = level.getGameTime();
		state.lastActivityTick = nowTick;
		if (nowTick < state.guidanceNarrationGateTick) {
			updateObservationBaseline(player, state);
			return;
		}
		if (isLookingAtIntroOre(player, slot)) {
			if (!state.introTargetLocked || nowTick >= state.nextIntroTargetReactionTick) {
				fireTriggerCycle(level.getServer(), player, state, "intro_target_locked", INTRO_TARGET_LOCK_TRIGGERS);
				state.introTargetLocked = true;
				state.nextIntroTargetReactionTick = nowTick + INTRO_TARGET_REACTION_REPEAT_TICKS;
			}
			updateObservationBaseline(player, state);
			return;
		}
		if (nowTick < state.nextAirPunchReactionTick) {
			updateObservationBaseline(player, state);
			return;
		}

		state.nextAirPunchReactionTick = nowTick + INTRO_AIR_PUNCH_REPEAT_TICKS;
		updateObservationBaseline(player, state);
		SeasonStartVoiceSystem.fireTrigger(level.getServer(), "intro_phase1_air_punch", player);
	}

	private static void onServerStarted(MinecraftServer server) {
		loadState(server);
		ensureBootstrap(server);
		applyStartDifficultyPolicy(server);
		if (completed && !active) {
			removeSceneShellNow(server.overworld());
		}
		if (!active && server.overworld() != null) {
			clearStartupWorldgenDisplay(server.overworld());
		}
		if (active) {
			rebuildActiveScene(server);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				assignOrRestorePlayer(server, player, false);
			}
			if (countSharedPlayers() > 0) {
				if (nextSharedReminderTick == Long.MIN_VALUE && server.overworld() != null) {
					nextSharedReminderTick = server.overworld().getGameTime() + SHARED_IDLE_REMINDER_TICKS;
				}
				refreshSharedLaunchBossBar(server);
			}
		}
	}

	private static void onServerStopping(MinecraftServer server) {
		saveState(server);
	}

	private static void preTickServer(MinecraftServer server) {
		if (server == null || !active) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (overworld == null || serverAnchor == null) {
			return;
		}
		tickStartupOfferings(server, overworld);
	}

	private static void tickServer(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (active) {
			ServerLevel overworld = server.overworld();
			if (overworld != null) {
				clearSceneMobs(overworld);
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
					if (state == null) {
						continue;
					}
					tickPlayerState(server, overworld, player, state);
				}
				tickSharedLaunch(server);
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
		if (state.phase == PlayerPhase.WAITING_START) {
			player.displayClientMessage(Component.literal("Сначала напишите в чат start или старт."), true);
			return false;
		}
		if (SHARED_BITCOIN_POSITIONS.contains(pos)) {
			if (state.phase != PlayerPhase.SHARED) {
				player.displayClientMessage(Component.literal("Эта руда пока не для вашей фазы."), true);
				return false;
			}
			return true;
		}
		SlotDefinition slot = resolveSlotDefinition(computeBarrierGeometry(resolveServerAnchor(level)), state.slotIndex);
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

	private static boolean onAllowChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
		if (!active || message == null || sender == null || params == null) {
			return true;
		}
		PlayerSceneState state = PLAYER_STATES.get(sender.getUUID());
		if (state == null || state.phase != PlayerPhase.WAITING_START) {
			return true;
		}
		MinecraftServer server = sender.level().getServer();
		if (server == null) {
			return true;
		}

		String raw = message.signedContent() == null ? "" : message.signedContent().trim();
		String transformed = shouldUseLatinReplacement(raw) ? "srat" : "срать";
		server.getPlayerList().broadcastSystemMessage(params.decorate(Component.literal(transformed)), false);

		if (matchesStartWord(raw)) {
			beginIntroAfterChatStart(server, sender, state);
		} else {
			fireRoundRobinTrigger(server, sender, state, WAITING_START_WRONG_TRIGGERS, false);
		}
		return false;
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
								+ ", shared=" + countSharedPlayers()
								+ ", launch=" + sharedLaunchCollectedBitcoins + "/" + Math.max(0, sharedLaunchRequiredBitcoins)
								+ ", ores=" + SHARED_BITCOIN_POSITIONS.size()
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

	private static void applyStartDifficultyPolicy(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (active && !completed) {
			enforcePeacefulDifficulty(server);
			return;
		}
		restoreSeasonStartDifficulty(server);
	}

	private static void enforcePeacefulDifficulty(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (difficultyBeforeSeasonStart == null && server.overworld() != null) {
			difficultyBeforeSeasonStart = server.overworld().getDifficulty();
			stateDirty = true;
		}
		if (server.overworld() != null && server.overworld().getDifficulty() != Difficulty.PEACEFUL) {
			server.setDifficulty(Difficulty.PEACEFUL, true);
		}
	}

	private static void restoreSeasonStartDifficulty(MinecraftServer server) {
		if (server == null || difficultyBeforeSeasonStart == null) {
			return;
		}
		if (server.overworld() != null && server.overworld().getDifficulty() != difficultyBeforeSeasonStart) {
			server.setDifficulty(difficultyBeforeSeasonStart, true);
		}
		difficultyBeforeSeasonStart = null;
		stateDirty = true;
	}

	private static void rebuildActiveScene(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (overworld == null) {
			return;
		}
		ensureSceneBuilt(overworld);
		clearSharedBitcoins(overworld, true);
		for (Map.Entry<UUID, PlayerSceneState> entry : PLAYER_STATES.entrySet()) {
			PlayerSceneState state = entry.getValue();
			if (state == null || state.phase == PlayerPhase.WAITING_START || state.phase == PlayerPhase.SHARED || state.phase == PlayerPhase.RESTORING) {
				continue;
			}
			SlotDefinition slot = resolveSlotDefinition(computeBarrierGeometry(resolveServerAnchor(overworld)), state.slotIndex);
			if (slot != null) {
				placeIntroOre(overworld, slot);
			}
		}
		ensureSharedBitcoinPopulation(overworld);
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
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		sharedLaunchCollectedBitcoins = 0;
		sharedLaunchRequiredBitcoins = 0;
		sharedLaunchMilestoneCursor = 0;
		sharedLaunchIntroTriggered = false;
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
		clearSharedLaunchBossBar();
		if (difficultyBeforeSeasonStart == null && overworld != null) {
			difficultyBeforeSeasonStart = overworld.getDifficulty();
		}
		PLAYER_STATES.clear();
		SHELL_DISSOLVE_ORDER.clear();
		SHARED_BITCOIN_POSITIONS.clear();
		SeasonStartVoiceSystem.resetSceneState();
		enforcePeacefulDifficulty(server);
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
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
		dissolveCursor = 0;
		nextSharedReminderTick = Long.MIN_VALUE;
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		clearSharedLaunchBossBar();
		ServerLevel overworld = server.overworld();
		if (overworld != null) {
			clearStartupWorldgenDisplay(overworld);
			clearSharedBitcoins(overworld, true);
			SHELL_DISSOLVE_ORDER.clear();
			SHELL_DISSOLVE_ORDER.addAll(collectSceneShellBlocks(resolveServerAnchor(overworld)));
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				applyFreeState(player);
			}
			SeasonStartVoiceSystem.fireTrigger(server, "season_finished", null);
		}
		restoreSeasonStartDifficulty(server);
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
		BoxGeometry geometry = computeBarrierGeometry(resolveServerAnchor(overworld));
		PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
		boolean created = false;
		if (state == null) {
			state = new PlayerSceneState();
			state.slotIndex = allocateSlotIndex(geometry);
			state.phase = PlayerPhase.WAITING_START;
			PLAYER_STATES.put(player.getUUID(), state);
			created = true;
			stateDirty = true;
		}
		SlotDefinition slot = resolveSlotDefinition(geometry, state.slotIndex);
		if (slot == null) {
			return;
		}
		teleportPlayer(player, overworld, slot.spawnPos, slot.yaw);
		if (state.phase == PlayerPhase.WAITING_START) {
			ensureWaitingStartPlayerState(player, state, slot);
		} else if (state.phase == PlayerPhase.ISOLATED || state.phase == PlayerPhase.GUIDED_TO_SERVER) {
			ensureIntroPlayerState(player, state, slot);
		} else if (state.phase == PlayerPhase.RESTORING) {
			ensureRestoringPlayerState(player, state);
		} else {
			applyStateForPhase(player, state);
		}
		primeObservationState(player, state);
		if ((state.phase == PlayerPhase.ISOLATED || state.phase == PlayerPhase.GUIDED_TO_SERVER) && !state.minedIntroBitcoin) {
			placeIntroOre(overworld, slot);
		}
		if ((created || announceIntro) && state.phase == PlayerPhase.WAITING_START && state.nextStartPromptTick <= 0L) {
			state.nextStartPromptTick = overworld.getGameTime() + WAITING_START_INITIAL_PROMPT_TICKS;
		}
	}

	private static void tickPlayerState(MinecraftServer server, ServerLevel level, ServerPlayer player, PlayerSceneState state) {
		BoxGeometry geometry = computeBarrierGeometry(resolveServerAnchor(level));
		SlotDefinition slot = resolveSlotDefinition(geometry, state.slotIndex);
		if (slot == null) {
			return;
		}

		if (state.phase == PlayerPhase.ISOLATED) {
			ensureIntroPlayerState(player, state, slot);
			if (!state.minedIntroBitcoin) {
				placeIntroOre(level, slot);
			}
			tickIsolatedPhaseReactions(server, player, state, slot);
			tickIntroOreGuidance(server, player, state, slot);
			return;
		}

		if (state.phase == PlayerPhase.WAITING_START) {
			ensureWaitingStartPlayerState(player, state, slot);
			tickWaitingStart(server, player, state);
			return;
		}

		if (state.phase == PlayerPhase.GUIDED_TO_SERVER) {
			ensureGuidedPlayerState(player, state, slot);
			tickGuidance(server, player, state);
			return;
		}

		if (state.phase == PlayerPhase.RESTORING) {
			ensureRestoringPlayerState(player, state);
			tickRestoringPhase(server, player, state);
			return;
		}

		if (state.phase == PlayerPhase.SHARED) {
			ensureSharedPlayerState(player);
		}
	}

	private static void tickWaitingStart(MinecraftServer server, ServerPlayer player, PlayerSceneState state) {
		if (server == null || player == null || state == null || player.level() == null) {
			return;
		}
		long nowTick = player.level().getGameTime();
		if (state.nextStartPromptTick <= 0L) {
			state.nextStartPromptTick = nowTick + WAITING_START_INITIAL_PROMPT_TICKS;
			return;
		}
		if (nowTick < state.nextStartPromptTick) {
			return;
		}
		fireRoundRobinTrigger(server, player, state, WAITING_START_PROMPT_TRIGGERS, true);
		state.nextStartPromptTick = nowTick + WAITING_START_REPEAT_TICKS;
	}

	private static void tickGuidance(MinecraftServer server, ServerPlayer player, PlayerSceneState state) {
		if (server == null || player == null || state == null || player.level() == null || serverAnchor == null) {
			return;
		}
		long nowTick = player.level().getGameTime();
		if (nowTick < state.guidanceNarrationGateTick) {
			return;
		}
		if (nowTick < state.nextGuidanceTick) {
			return;
		}
		state.nextGuidanceTick = nowTick + GUIDANCE_EVALUATE_TICKS;

		GuidanceSnapshot snapshot = resolveGuidanceSnapshot(player);
		if (snapshot == null) {
			return;
		}
		boolean lookingAtServerStructure = isLookingAtServerStructure(player);
		if (lookingAtServerStructure && !snapshot.aligned) {
			snapshot = snapshot.withAlignmentLock();
		}
		boolean quietZone = hasBitcoin(player) && snapshot.horizontalDistance <= GUIDANCE_QUIET_DISTANCE;
		if (quietZone) {
			if (!state.guidanceQuietZoneActive) {
				SeasonStartVoiceSystem.clearPlayerChannel(player);
				state.guidanceQuietZoneActive = true;
				state.nextGuidanceVoiceTick = nowTick;
				state.nextGuidanceEarliestTick = nowTick;
				state.lastGuidanceStateKey = "";
			}
		} else {
			state.guidanceQuietZoneActive = false;
		}

		if (!Double.isFinite(state.lastGuidanceDistance)) {
			state.lastGuidanceDistance = snapshot.horizontalDistance;
		}
		double distanceDelta = snapshot.horizontalDistance - state.lastGuidanceDistance;
		boolean seesServer = snapshot.horizontalDistance <= GUIDANCE_SERVER_SIGHT_DISTANCE && lookingAtServerStructure;
		GuidanceInstruction instruction = resolveGuidanceInstruction(snapshot, distanceDelta, hasBitcoin(player), seesServer, state, nowTick);
		state.lastGuidanceDistance = snapshot.horizontalDistance;
		if (instruction == null) {
			state.wasGuidanceAligned = snapshot.aligned;
			state.lastGuidanceDistanceBucket = snapshot.distanceBucket;
			state.wasGuidanceClose = snapshot.horizontalDistance <= GUIDANCE_CLOSE_APPROACH_DISTANCE;
			state.lastGuidanceAbsYaw = Math.abs(snapshot.deltaYaw);
			return;
		}

		boolean changed = !instruction.stateKey.equals(state.lastGuidanceStateKey);
		boolean bypassNarrationLock = shouldBypassGuidanceNarrationLock(instruction);
		if (!bypassNarrationLock && nowTick < state.nextGuidanceEarliestTick) {
			state.wasGuidanceAligned = snapshot.aligned;
			state.lastGuidanceDistanceBucket = snapshot.distanceBucket;
			state.wasGuidanceClose = snapshot.horizontalDistance <= GUIDANCE_CLOSE_APPROACH_DISTANCE;
			return;
		}
		if (!changed && nowTick < state.nextGuidanceVoiceTick) {
			return;
		}

		fireTriggerCycle(server, player, state, instruction.groupKey, instruction.triggers);
		state.lastGuidanceStateKey = instruction.stateKey;
		state.lastGuidanceDistanceBucket = snapshot.distanceBucket;
		state.wasGuidanceAligned = snapshot.aligned;
		state.wasGuidanceClose = snapshot.horizontalDistance <= GUIDANCE_CLOSE_APPROACH_DISTANCE;
		state.lastGuidanceAbsYaw = Math.abs(snapshot.deltaYaw);
		if ("guide_server_in_sight".equals(instruction.stateKey)) {
			state.announcedServerSight = true;
		}
		applyGuidanceInstructionState(state, instruction, snapshot, nowTick);
		long narrationLockTicks = resolveGuidanceNarrationLockTicks(instruction.triggers);
		state.nextGuidanceVoiceTick = nowTick + Math.max(instruction.cooldownTicks, narrationLockTicks);
		state.nextGuidanceEarliestTick = nowTick + narrationLockTicks;
	}

	private static void tickIntroOreGuidance(MinecraftServer server, ServerPlayer player, PlayerSceneState state, SlotDefinition slot) {
		if (server == null || player == null || state == null || slot == null || player.level() == null || state.minedIntroBitcoin) {
			return;
		}
		long nowTick = player.level().getGameTime();
		if (nowTick < state.guidanceNarrationGateTick) {
			return;
		}
		if (nowTick < state.nextGuidanceTick) {
			return;
		}
		state.nextGuidanceTick = nowTick + GUIDANCE_EVALUATE_TICKS;
		if (!state.guidanceRouteStarted) {
			if (nowTick < state.nextGuidanceEarliestTick || nowTick < state.nextGuidanceVoiceTick) {
				return;
			}
			fireTriggerCycle(server, player, state, "intro_guide_route_start", INTRO_GUIDE_ROUTE_START_TRIGGERS);
			state.guidanceRouteStarted = true;
			long narrationLockTicks = resolveGuidanceNarrationLockTicks(INTRO_GUIDE_ROUTE_START_TRIGGERS);
			state.nextGuidanceVoiceTick = nowTick + Math.max(GUIDANCE_FORWARD_COOLDOWN_TICKS, narrationLockTicks);
			state.nextGuidanceEarliestTick = nowTick + narrationLockTicks;
			return;
		}

		GuidanceSnapshot snapshot = resolveGuidanceSnapshot(player, centerOf(slot.orePos));
		if (snapshot == null) {
			return;
		}
		if (!Double.isFinite(state.lastGuidanceDistance)) {
			state.lastGuidanceDistance = snapshot.horizontalDistance;
		}
		double distanceDelta = snapshot.horizontalDistance - state.lastGuidanceDistance;
		boolean lookingAtOre = isLookingAtIntroOre(player, slot);
		VerticalAimHint verticalAimHint = resolveIntroVerticalAimHint(player, slot, snapshot, lookingAtOre);
		GuidanceInstruction instruction = resolveIntroGuidanceInstruction(snapshot, distanceDelta, lookingAtOre, verticalAimHint, state, nowTick);
		state.lastGuidanceDistance = snapshot.horizontalDistance;
		if (instruction == null) {
			state.wasGuidanceAligned = snapshot.aligned;
			state.lastGuidanceDistanceBucket = snapshot.distanceBucket;
			state.lastGuidanceAbsYaw = Math.abs(snapshot.deltaYaw);
			return;
		}

		boolean changed = !instruction.stateKey.equals(state.lastGuidanceStateKey);
		boolean bypassNarrationLock = shouldBypassGuidanceNarrationLock(instruction);
		if (!bypassNarrationLock && nowTick < state.nextGuidanceEarliestTick) {
			state.wasGuidanceAligned = snapshot.aligned;
			state.lastGuidanceDistanceBucket = snapshot.distanceBucket;
			return;
		}
		if (!changed && nowTick < state.nextGuidanceVoiceTick) {
			return;
		}

		fireTriggerCycle(server, player, state, instruction.groupKey, instruction.triggers);
		state.lastGuidanceStateKey = instruction.stateKey;
		state.lastGuidanceDistanceBucket = snapshot.distanceBucket;
		state.wasGuidanceAligned = snapshot.aligned;
		state.lastGuidanceAbsYaw = Math.abs(snapshot.deltaYaw);
		applyGuidanceInstructionState(state, instruction, snapshot, nowTick);
		long narrationLockTicks = resolveGuidanceNarrationLockTicks(instruction.triggers);
		state.nextGuidanceVoiceTick = nowTick + Math.max(instruction.cooldownTicks, narrationLockTicks);
		state.nextGuidanceEarliestTick = nowTick + narrationLockTicks;
	}

	private static void tickIsolatedPhaseReactions(MinecraftServer server, ServerPlayer player, PlayerSceneState state, SlotDefinition slot) {
		if (server == null || player == null || state == null || slot == null || player.level() == null) {
			return;
		}
		long nowTick = player.level().getGameTime();
		if (state.lastObservationTick == Long.MIN_VALUE) {
			primeObservationState(player, state);
			return;
		}
		if (nowTick < state.guidanceNarrationGateTick) {
			state.lastActivityTick = nowTick;
			updateObservationBaseline(player, state);
			return;
		}

		double horizontalMoveSqr = horizontalDistanceSqr(player.position(), new Vec3(state.lastObservedX, player.getY(), state.lastObservedZ));
		double verticalMove = Math.abs(player.getY() - state.lastObservedY);
		float yawDelta = Math.abs(Mth.wrapDegrees(player.getYRot() - state.lastYaw));
		float pitchDelta = Math.abs(player.getXRot() - state.lastPitch);
		boolean moved = horizontalMoveSqr > INTRO_ACTIVITY_MOVE_SQR || verticalMove > 0.12D;
		boolean looked = yawDelta >= INTRO_ACTIVITY_YAW_DEGREES || pitchDelta >= INTRO_ACTIVITY_PITCH_DEGREES;
		boolean lookingAtOre = isLookingAtIntroOre(player, slot);

		if (moved || looked) {
			state.lastActivityTick = nowTick;
		}

		if (lookingAtOre && (!state.introTargetLocked || nowTick >= state.nextIntroTargetReactionTick)) {
			fireTriggerCycle(
					server,
					player,
					state,
					state.introTargetLocked ? "intro_target_stare" : "intro_target_locked",
					state.introTargetLocked ? INTRO_TARGET_STARE_TRIGGERS : INTRO_TARGET_LOCK_TRIGGERS
			);
			state.introTargetLocked = true;
			state.nextIntroTargetReactionTick = nowTick + INTRO_TARGET_REACTION_REPEAT_TICKS;
		}

		state.spinScore = Math.max(
				0.0D,
				state.spinScore * 0.72D + yawDelta + pitchDelta * 0.45D - (moved ? 8.0D : 0.0D)
		);

		if (state.lastOnGround && !player.onGround() && player.getDeltaMovement().y > 0.18D && nowTick >= state.nextJumpReactionTick) {
			state.nextJumpReactionTick = nowTick + INTRO_JUMP_REPEAT_TICKS;
			SeasonStartVoiceSystem.fireTrigger(server, "intro_phase1_jump", player);
		}

		if (state.spinScore >= INTRO_SPIN_TRIGGER_SCORE && nowTick >= state.nextSpinReactionTick) {
			state.nextSpinReactionTick = nowTick + INTRO_SPIN_REPEAT_TICKS;
			state.spinScore = 0.0D;
			SeasonStartVoiceSystem.fireTrigger(server, "intro_phase1_spin", player);
		}

		double leaveDistanceSqr = horizontalDistanceSqr(player.position(), slot.spawnPos);
		double oreDistanceSqr = horizontalDistanceSqr(player.position(), centerOf(slot.orePos));
		if (leaveDistanceSqr >= 12.0D * 12.0D && oreDistanceSqr >= 7.0D * 7.0D && nowTick >= state.nextLeaveReactionTick) {
			state.nextLeaveReactionTick = nowTick + INTRO_LEAVE_REPEAT_TICKS;
			SeasonStartVoiceSystem.fireTrigger(server, "intro_phase1_leave_attempt", player);
		}

		if (nowTick - state.lastActivityTick >= INTRO_IDLE_TRIGGER_TICKS && nowTick >= state.nextIdleReactionTick) {
			state.nextIdleReactionTick = nowTick + INTRO_IDLE_REPEAT_TICKS;
			SeasonStartVoiceSystem.fireTrigger(server, "intro_phase1_idle", player);
		}

		updateObservationBaseline(player, state);
	}

	private static GuidanceSnapshot resolveGuidanceSnapshot(ServerPlayer player) {
		if (player == null || serverAnchor == null) {
			return null;
		}
		ServerStructureBounds bounds = resolveServerStructureBounds();
		if (bounds == null) {
			return resolveGuidanceSnapshot(player, new Vec3(serverAnchor.getX() + 0.5D, player.getY(), serverAnchor.getZ() + 0.5D));
		}
		Vec3 playerPos = player.position();
		double targetX = Mth.clamp(playerPos.x, bounds.minX, bounds.maxX);
		double targetZ = Mth.clamp(playerPos.z, bounds.minZ, bounds.maxZ);
		return resolveGuidanceSnapshot(player, new Vec3(targetX, player.getY(), targetZ));
	}

	private static GuidanceSnapshot resolveGuidanceSnapshot(ServerPlayer player, Vec3 target) {
		if (player == null || target == null) {
			return null;
		}
		Vec3 playerPos = player.position();
		Vec3 toTarget = target.subtract(playerPos);
		double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
		if (horizontalDistance <= 1.0E-4D) {
			return new GuidanceSnapshot(0.0D, 0.0D, true, 0);
		}
		float targetYaw = (float) (Math.atan2(-toTarget.x, toTarget.z) * Mth.RAD_TO_DEG);
		double deltaYaw = Mth.wrapDegrees(targetYaw - player.getYRot());
		boolean aligned = Math.abs(deltaYaw) <= GUIDANCE_LOCK_ANGLE;
		return new GuidanceSnapshot(horizontalDistance, deltaYaw, aligned, guidanceDistanceBucket(horizontalDistance));
	}

	private static GuidanceInstruction resolveGuidanceInstruction(
			GuidanceSnapshot snapshot,
			double distanceDelta,
			boolean hasBitcoin,
			boolean seesServer,
			PlayerSceneState state,
			long nowTick
	) {
		if (snapshot == null || state == null) {
			return null;
		}
		double absYaw = Math.abs(snapshot.deltaYaw);
		boolean quietZone = snapshot.horizontalDistance <= GUIDANCE_QUIET_DISTANCE;
		boolean inDropZone = snapshot.horizontalDistance <= GUIDANCE_DROP_DISTANCE;

		if (hasBitcoin && state.wasGuidanceClose && snapshot.horizontalDistance >= 3.1D && distanceDelta >= GUIDANCE_PROGRESS_AWAY) {
			return new GuidanceInstruction("guide_passed_server", "guide_passed_server", GUIDE_PASSED_SERVER_TRIGGERS, GUIDANCE_CATEGORY_COOLDOWN_TICKS);
		}
		if (hasBitcoin && inDropZone) {
			return new GuidanceInstruction("guide_drop_coin", "guide_drop_coin", GUIDE_DROP_COIN_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
		}
		if (hasBitcoin && quietZone) {
			if (!state.announcedServerSight && (snapshot.aligned || seesServer)) {
				return new GuidanceInstruction("guide_server_in_sight", "guide_server_in_sight", GUIDE_SERVER_IN_SIGHT_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
			}
			if (state.wasGuidanceAligned && absYaw >= GUIDANCE_MICRO_ANGLE) {
				return new GuidanceInstruction("guide_heading_lost", "guide_heading_lost", GUIDE_HEADING_LOST_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS);
			}
			if (absYaw >= GUIDANCE_MEDIUM_ANGLE) {
				return new GuidanceInstruction("guide_close_presence", "guide_close_presence", GUIDE_CLOSE_PRESENCE_TRIGGERS, GUIDANCE_STALL_COOLDOWN_TICKS);
			}
			return null;
		}
		if (hasBitcoin && seesServer && !state.announcedServerSight) {
			return new GuidanceInstruction("guide_server_in_sight", "guide_server_in_sight", GUIDE_SERVER_IN_SIGHT_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
		}

		GuidanceInstruction turnInstruction = resolveTurnInstruction(snapshot, absYaw, state, nowTick);
		if (turnInstruction != null) {
			return turnInstruction;
		}
		if (distanceDelta >= GUIDANCE_PROGRESS_AWAY) {
			return new GuidanceInstruction("guide_wrong_way", "guide_wrong_way", GUIDE_WRONG_WAY_TRIGGERS, GUIDANCE_CATEGORY_COOLDOWN_TICKS);
		}
		if (!state.wasGuidanceAligned && snapshot.aligned) {
			return new GuidanceInstruction("guide_locked_on", "guide_locked_on", GUIDE_LOCKED_ON_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
		}
		if (Math.abs(distanceDelta) < GUIDANCE_STALL_DELTA) {
			GuidanceInstruction stallInstruction = resolveStallInstruction(snapshot, state, nowTick);
			if (stallInstruction != null) {
				return stallInstruction;
			}
		}
		if (snapshot.distanceBucket != state.lastGuidanceDistanceBucket || distanceDelta <= GUIDANCE_PROGRESS_TOWARD) {
			return switch (snapshot.distanceBucket) {
				case 2 -> new GuidanceInstruction("guide_forward_close", "guide_forward_close", GUIDE_FORWARD_CLOSE_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
				case 4 -> new GuidanceInstruction("guide_forward_near", "guide_forward_near", GUIDE_FORWARD_NEAR_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
				case 7 -> new GuidanceInstruction("guide_forward_mid", "guide_forward_mid", GUIDE_FORWARD_MID_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
				default -> new GuidanceInstruction("guide_forward_far", "guide_forward_far", GUIDE_FORWARD_FAR_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
			};
		}
		return null;
	}

	private static GuidanceInstruction resolveIntroGuidanceInstruction(
			GuidanceSnapshot snapshot,
			double distanceDelta,
			boolean lookingAtOre,
			VerticalAimHint verticalAimHint,
			PlayerSceneState state,
			long nowTick
	) {
		if (snapshot == null || state == null || lookingAtOre) {
			return null;
		}
		if (verticalAimHint == VerticalAimHint.UP) {
			return new GuidanceInstruction("intro_target_look_up", "intro_target_look_up", INTRO_TARGET_LOOK_UP_TRIGGERS, GUIDANCE_STALL_COOLDOWN_TICKS);
		}
		if (verticalAimHint == VerticalAimHint.DOWN) {
			return new GuidanceInstruction("intro_target_look_down", "intro_target_look_down", INTRO_TARGET_LOOK_DOWN_TRIGGERS, GUIDANCE_STALL_COOLDOWN_TICKS);
		}
		double absYaw = Math.abs(snapshot.deltaYaw);
		GuidanceInstruction turnInstruction = resolveTurnInstruction(snapshot, absYaw, state, nowTick);
		if (turnInstruction != null) {
			return turnInstruction;
		}
		if (distanceDelta >= GUIDANCE_PROGRESS_AWAY) {
			return new GuidanceInstruction("intro_guide_wrong_way", "intro_guide_wrong_way", INTRO_GUIDE_WRONG_WAY_TRIGGERS, GUIDANCE_CATEGORY_COOLDOWN_TICKS);
		}
		if (!state.wasGuidanceAligned && snapshot.aligned) {
			return new GuidanceInstruction("guide_locked_on", "guide_locked_on", GUIDE_LOCKED_ON_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
		}
		if (Math.abs(distanceDelta) < GUIDANCE_STALL_DELTA) {
			GuidanceInstruction stallInstruction = resolveStallInstruction(snapshot, state, nowTick);
			if (stallInstruction != null) {
				return stallInstruction;
			}
		}
		if (snapshot.distanceBucket != state.lastGuidanceDistanceBucket || distanceDelta <= GUIDANCE_PROGRESS_TOWARD) {
			return switch (snapshot.distanceBucket) {
				case 2 -> new GuidanceInstruction("guide_forward_close", "guide_forward_close", GUIDE_FORWARD_CLOSE_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
				case 4 -> new GuidanceInstruction("guide_forward_near", "guide_forward_near", GUIDE_FORWARD_NEAR_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
				case 7 -> new GuidanceInstruction("guide_forward_mid", "guide_forward_mid", GUIDE_FORWARD_MID_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
				default -> new GuidanceInstruction("guide_forward_far", "guide_forward_far", GUIDE_FORWARD_FAR_TRIGGERS, GUIDANCE_FORWARD_COOLDOWN_TICKS);
			};
		}
		return null;
	}

	private static GuidanceInstruction resolveTurnInstruction(
			GuidanceSnapshot snapshot,
			double absYaw,
			PlayerSceneState state,
			long nowTick
	) {
		if (snapshot == null || state == null) {
			return null;
		}
		TurnHintDirection desiredDirection = snapshot.deltaYaw < 0.0D ? TurnHintDirection.LEFT : TurnHintDirection.RIGHT;
		GuidanceInstruction recoverInstruction = resolveTurnRecoverInstruction(state, desiredDirection, absYaw, nowTick);
		if (recoverInstruction != null) {
			return recoverInstruction;
		}
		if (absYaw >= GUIDANCE_TURN_AROUND_ANGLE) {
			return snapshot.deltaYaw < 0.0D
					? new GuidanceInstruction("guide_turn_around_left", "guide_turn_around_left", GUIDE_TURN_AROUND_LEFT_TRIGGERS, GUIDANCE_CATEGORY_COOLDOWN_TICKS)
					: new GuidanceInstruction("guide_turn_around_right", "guide_turn_around_right", GUIDE_TURN_AROUND_RIGHT_TRIGGERS, GUIDANCE_CATEGORY_COOLDOWN_TICKS);
		}
		if (state.wasGuidanceAligned && absYaw >= GUIDANCE_MICRO_ANGLE) {
			return new GuidanceInstruction("guide_heading_lost", "guide_heading_lost", GUIDE_HEADING_LOST_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS);
		}
		if (absYaw >= GUIDANCE_HARD_ANGLE) {
			return snapshot.deltaYaw < 0.0D
					? new GuidanceInstruction("guide_turn_left_hard", "guide_turn_left_hard", GUIDE_TURN_LEFT_HARD_TRIGGERS, GUIDANCE_CATEGORY_COOLDOWN_TICKS)
					: new GuidanceInstruction("guide_turn_right_hard", "guide_turn_right_hard", GUIDE_TURN_RIGHT_HARD_TRIGGERS, GUIDANCE_CATEGORY_COOLDOWN_TICKS);
		}
		if (absYaw >= GUIDANCE_MEDIUM_ANGLE) {
			return snapshot.deltaYaw < 0.0D
					? new GuidanceInstruction("guide_turn_left_medium", "guide_turn_left_medium", GUIDE_TURN_LEFT_MEDIUM_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS)
					: new GuidanceInstruction("guide_turn_right_medium", "guide_turn_right_medium", GUIDE_TURN_RIGHT_MEDIUM_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS);
		}
		if (absYaw >= GUIDANCE_MICRO_ANGLE) {
			return snapshot.deltaYaw < 0.0D
					? new GuidanceInstruction("guide_turn_left_soft", "guide_turn_left_soft", GUIDE_TURN_LEFT_SOFT_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS)
					: new GuidanceInstruction("guide_turn_right_soft", "guide_turn_right_soft", GUIDE_TURN_RIGHT_SOFT_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS);
		}
		return null;
	}

	private static GuidanceInstruction resolveTurnRecoverInstruction(
			PlayerSceneState state,
			TurnHintDirection desiredDirection,
			double absYaw,
			long nowTick
	) {
		TurnHintDirection recoverContextDirection = state == null
				? TurnHintDirection.NONE
				: resolveRecoverContextDirection(state.lastGuidanceStateKey);
		if (state == null
				|| desiredDirection == TurnHintDirection.NONE
				|| recoverContextDirection != desiredDirection
				|| state.lastGuidanceTurnDirection != desiredDirection
				|| state.lastGuidanceTurnRecoverUsed
				|| !Double.isFinite(state.lastGuidanceTurnAbsYaw)
				|| nowTick - state.lastGuidanceTurnTick > GUIDANCE_RECOVER_REACTION_WINDOW_TICKS
				|| absYaw < state.lastGuidanceTurnAbsYaw + GUIDANCE_RECOVER_WORSEN_THRESHOLD) {
			return null;
		}
		return desiredDirection == TurnHintDirection.LEFT
				? new GuidanceInstruction("guide_turn_left_recover", "guide_turn_left_recover", GUIDE_TURN_LEFT_RECOVER_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS)
				: new GuidanceInstruction("guide_turn_right_recover", "guide_turn_right_recover", GUIDE_TURN_RIGHT_RECOVER_TRIGGERS, GUIDANCE_CORRECTION_COOLDOWN_TICKS);
	}

	private static VerticalAimHint resolveIntroVerticalAimHint(
			ServerPlayer player,
			SlotDefinition slot,
			GuidanceSnapshot snapshot,
			boolean lookingAtOre
	) {
		if (player == null || slot == null || snapshot == null || lookingAtOre) {
			return VerticalAimHint.NONE;
		}
		if (snapshot.horizontalDistance > INTRO_TARGET_VERTICAL_GUIDANCE_DISTANCE
				|| Math.abs(snapshot.deltaYaw) > INTRO_TARGET_VERTICAL_YAW_WINDOW) {
			return VerticalAimHint.NONE;
		}
		Vec3 eyePos = player.getEyePosition();
		Vec3 toTarget = centerOf(slot.orePos).subtract(eyePos);
		double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
		if (horizontalDistance <= 1.0E-4D) {
			return VerticalAimHint.NONE;
		}
		double targetPitch = -(Math.atan2(toTarget.y, horizontalDistance) * Mth.RAD_TO_DEG);
		double pitchDelta = Mth.wrapDegrees(targetPitch - player.getXRot());
		if (pitchDelta >= INTRO_TARGET_VERTICAL_PITCH_THRESHOLD) {
			return VerticalAimHint.DOWN;
		}
		if (pitchDelta <= -INTRO_TARGET_VERTICAL_PITCH_THRESHOLD) {
			return VerticalAimHint.UP;
		}
		return VerticalAimHint.NONE;
	}

	private static GuidanceInstruction resolveStallInstruction(
			GuidanceSnapshot snapshot,
			PlayerSceneState state,
			long nowTick
	) {
		if (snapshot == null || state == null) {
			return null;
		}
		if (snapshot.aligned) {
			if (nowTick - state.lastGuidanceAlignedTick < GUIDANCE_STALL_AFTER_ALIGNMENT_TICKS) {
				return null;
			}
			return new GuidanceInstruction("guide_stall_aligned", "guide_stall_aligned", GUIDE_STALL_ALIGNED_TRIGGERS, GUIDANCE_STALL_COOLDOWN_TICKS);
		}
		if (nowTick - state.lastGuidanceTurnTick < GUIDANCE_STALL_AFTER_TURN_TICKS) {
			return null;
		}
		return new GuidanceInstruction("guide_stall_misaligned", "guide_stall_misaligned", GUIDE_STALL_MISALIGNED_TRIGGERS, GUIDANCE_STALL_COOLDOWN_TICKS);
	}

	private static int guidanceDistanceBucket(double distance) {
		if (distance <= 2.6D) {
			return 2;
		}
		if (distance <= 4.7D) {
			return 4;
		}
		if (distance <= 7.5D) {
			return 7;
		}
		return 10;
	}

	private static void applyGuidanceInstructionState(
			PlayerSceneState state,
			GuidanceInstruction instruction,
			GuidanceSnapshot snapshot,
			long nowTick
	) {
		if (state == null || instruction == null || snapshot == null) {
			return;
		}
		double absYaw = Math.abs(snapshot.deltaYaw);
		state.guidanceRouteStarted = true;
		updateGuidanceTurnContext(state, instruction, absYaw, nowTick);
		if ("guide_locked_on".equals(instruction.stateKey)) {
			state.guidanceAlignedEver = true;
			state.lastGuidanceAlignedTick = nowTick;
			return;
		}
		if ("guide_heading_lost".equals(instruction.stateKey)
				|| "guide_wrong_way".equals(instruction.stateKey)
				|| "intro_guide_wrong_way".equals(instruction.stateKey)
				|| "guide_passed_server".equals(instruction.stateKey)
				|| "guide_close_presence".equals(instruction.stateKey)) {
			state.guidanceMistakeCount++;
			return;
		}
		if ("guide_turn_left_recover".equals(instruction.stateKey)) {
			state.guidanceMistakeCount++;
			return;
		}
		if ("guide_turn_right_recover".equals(instruction.stateKey)) {
			state.guidanceMistakeCount++;
			return;
		}
	}

	private static TurnHintDirection resolveTurnDirectionFromStateKey(String stateKey) {
		if (stateKey == null || stateKey.isBlank()) {
			return TurnHintDirection.NONE;
		}
		if (stateKey.contains("_left")) {
			return TurnHintDirection.LEFT;
		}
		if (stateKey.contains("_right")) {
			return TurnHintDirection.RIGHT;
		}
		return TurnHintDirection.NONE;
	}

	private static TurnHintDirection resolveRecoverContextDirection(String stateKey) {
		if (stateKey == null || stateKey.isBlank()) {
			return TurnHintDirection.NONE;
		}
		return switch (stateKey) {
			case "guide_turn_left_soft",
					"guide_turn_left_medium",
					"guide_turn_left_hard",
					"guide_turn_around_left" -> TurnHintDirection.LEFT;
			case "guide_turn_right_soft",
					"guide_turn_right_medium",
					"guide_turn_right_hard",
					"guide_turn_around_right" -> TurnHintDirection.RIGHT;
			default -> TurnHintDirection.NONE;
		};
	}

	private static void updateGuidanceTurnContext(
			PlayerSceneState state,
			GuidanceInstruction instruction,
			double absYaw,
			long nowTick
	) {
		if (state == null || instruction == null) {
			return;
		}
		if ("guide_turn_left_recover".equals(instruction.stateKey)) {
			state.lastGuidanceTurnDirection = TurnHintDirection.LEFT;
			state.lastGuidanceTurnAbsYaw = absYaw;
			state.lastGuidanceTurnTick = nowTick;
			state.lastGuidanceTurnRecoverUsed = true;
			return;
		}
		if ("guide_turn_right_recover".equals(instruction.stateKey)) {
			state.lastGuidanceTurnDirection = TurnHintDirection.RIGHT;
			state.lastGuidanceTurnAbsYaw = absYaw;
			state.lastGuidanceTurnTick = nowTick;
			state.lastGuidanceTurnRecoverUsed = true;
			return;
		}
		TurnHintDirection followUpDirection = resolveRecoverContextDirection(instruction.stateKey);
		if (followUpDirection != TurnHintDirection.NONE) {
			state.lastGuidanceTurnDirection = followUpDirection;
			state.lastGuidanceTurnAbsYaw = absYaw;
			state.lastGuidanceTurnTick = nowTick;
			state.lastGuidanceTurnRecoverUsed = false;
			return;
		}
		state.lastGuidanceTurnDirection = TurnHintDirection.NONE;
		state.lastGuidanceTurnAbsYaw = Double.NaN;
		state.lastGuidanceTurnTick = Long.MIN_VALUE;
		state.lastGuidanceTurnRecoverUsed = false;
	}

	private static ServerStructureBounds resolveServerStructureBounds() {
		if (serverAnchor == null) {
			return null;
		}
		List<BlockPos> positions = ServerStructureBreakSystem.getStructurePositions(serverAnchor, Direction.Axis.Z);
		if (positions.isEmpty()) {
			return null;
		}
		double minX = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (BlockPos pos : positions) {
			minX = Math.min(minX, pos.getX());
			maxX = Math.max(maxX, pos.getX() + 1.0D);
			minZ = Math.min(minZ, pos.getZ());
			maxZ = Math.max(maxZ, pos.getZ() + 1.0D);
		}
		return new ServerStructureBounds(minX, maxX, minZ, maxZ);
	}

	private static long resolveTriggerSequenceDurationTicks(String trigger) {
		if (trigger == null || trigger.isBlank()) {
			return 0L;
		}
		long maxTicks = 0L;
		for (SeasonStartConfig.VoiceCue cue : SeasonStartConfig.get().cues) {
			if (cue == null || !trigger.equals(cue.trigger)) {
				continue;
			}
			maxTicks = Math.max(maxTicks, (long) cue.delayTicks + cue.durationTicks);
		}
		return maxTicks;
	}

	private static long resolveGuidanceNarrationLockTicks(String[] triggers) {
		if (triggers == null || triggers.length == 0) {
			return GUIDANCE_MIN_VOICE_GAP_TICKS;
		}
		long maxTicks = GUIDANCE_MIN_VOICE_GAP_TICKS;
		for (String trigger : triggers) {
			maxTicks = Math.max(maxTicks, resolveTriggerSequenceDurationTicks(trigger));
		}
		return maxTicks;
	}

	private static boolean shouldBypassGuidanceNarrationLock(GuidanceInstruction instruction) {
		if (instruction == null || instruction.stateKey == null || instruction.stateKey.isBlank()) {
			return false;
		}
		return "guide_wrong_way".equals(instruction.stateKey)
				|| "guide_passed_server".equals(instruction.stateKey)
				|| "intro_guide_wrong_way".equals(instruction.stateKey);
	}

	private static long resolveCueEndTickById(String cueId) {
		if (cueId == null || cueId.isBlank()) {
			return 0L;
		}
		for (SeasonStartConfig.VoiceCue cue : SeasonStartConfig.get().cues) {
			if (cue != null && cueId.equals(cue.id)) {
				return (long) cue.delayTicks + cue.durationTicks;
			}
		}
		return 0L;
	}

	private static void transitionPlayerAfterFirstPayment(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null || serverAnchor == null) {
			return;
		}
		SeasonStartVoiceSystem.clearPlayerChannel(player);
		PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
		if (state == null || state.phase == PlayerPhase.SHARED || state.phase == PlayerPhase.RESTORING) {
			return;
		}
		long nowTick = player.level() == null ? 0L : player.level().getGameTime();
		state.phase = PlayerPhase.RESTORING;
		state.poweredServer = true;
		state.nextGuidanceTick = Long.MAX_VALUE;
		state.nextGuidanceVoiceTick = Long.MAX_VALUE;
		state.nextGuidanceEarliestTick = Long.MAX_VALUE;
		state.lastGuidanceStateKey = "";
		state.restoreVisionTick = nowTick + resolveCueEndTickById("phase3_restore_vision");
		state.restoreBodyTick = nowTick + resolveCueEndTickById("phase3_restore_body");
		state.activateSharedTick = nowTick + resolveTriggerSequenceDurationTicks("player_powered_server");
		state.sharedVisionRestored = false;
		state.sharedBodyRestored = false;
		state.pendingSharedPeersLine = countSharedPlayers() > 0;
		stateDirty = true;

		ensureRestoringPlayerState(player, state);
		spawnLightOnlineParticles(server.overworld());
		SeasonStartVoiceSystem.fireTrigger(server, "player_powered_server", player);
	}

	private static void handleIntroOreBroken(ServerLevel level, ServerPlayer player, BlockPos pos, PlayerSceneState state) {
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		if (level.getBlockState(pos.below()).is(Blocks.BLACK_CONCRETE)) {
			level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
		}
		level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 16, 0.3D, 0.3D, 0.3D, 0.02D);
		level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.85F);
		if (!hasBitcoin(player)) {
			giveOrDrop(player, new ItemStack(ModItems.BITCOIN));
		}
		state.minedIntroBitcoin = true;
		state.phase = PlayerPhase.GUIDED_TO_SERVER;
		state.nextGuidanceTick = 0L;
		state.nextGuidanceVoiceTick = level.getGameTime() + 28L;
		state.nextGuidanceEarliestTick = level.getGameTime() + 28L;
		state.guidanceNarrationGateTick = level.getGameTime() + resolveTriggerSequenceDurationTicks("player_mined_intro_bitcoin");
		state.lastGuidanceStateKey = "";
		state.lastGuidanceDistance = Double.NaN;
		state.lastGuidanceAbsYaw = Double.NaN;
		state.lastGuidanceDistanceBucket = Integer.MIN_VALUE;
		state.wasGuidanceAligned = false;
		state.wasGuidanceClose = false;
		state.lastGuidanceAlignedTick = Long.MIN_VALUE;
		state.guidanceRouteStarted = true;
		state.guidanceAlignedEver = false;
		state.guidanceMistakeCount = 0;
		state.lastGuidanceTurnDirection = TurnHintDirection.NONE;
		state.lastGuidanceTurnAbsYaw = Double.NaN;
		state.lastGuidanceTurnTick = Long.MIN_VALUE;
		state.lastGuidanceTurnRecoverUsed = false;
		state.announcedServerSight = false;
		state.guidanceQuietZoneActive = false;
		state.guidanceCueCycles.clear();
		state.spinScore = 0.0D;
		state.introTargetLocked = false;
		state.nextIntroTargetReactionTick = 0L;
		stateDirty = true;
		SeasonStartVoiceSystem.fireTrigger(level.getServer(), "player_mined_intro_bitcoin", player);
	}

	private static void tickRestoringPhase(MinecraftServer server, ServerPlayer player, PlayerSceneState state) {
		if (server == null || player == null || state == null || player.level() == null) {
			return;
		}
		long nowTick = player.level().getGameTime();
		if (!state.sharedVisionRestored && nowTick >= state.restoreVisionTick) {
			player.removeEffect(MobEffects.BLINDNESS);
			state.sharedVisionRestored = true;
			stateDirty = true;
		}
		if (!state.sharedBodyRestored && nowTick >= state.restoreBodyTick) {
			ServerAbsoluteInvisibilitySystem.deactivate(player);
			player.removeEffect(MobEffects.INVISIBILITY);
			state.sharedBodyRestored = true;
			stateDirty = true;
		}
		if (nowTick < state.activateSharedTick) {
			return;
		}
		state.phase = PlayerPhase.SHARED;
		state.restoreVisionTick = Long.MAX_VALUE;
		state.restoreBodyTick = Long.MAX_VALUE;
		state.activateSharedTick = Long.MAX_VALUE;
		stateDirty = true;
		applySharedPlayerState(player);
		if (state.pendingSharedPeersLine) {
			SeasonStartVoiceSystem.fireTrigger(server, "player_powered_server_others_visible", player);
			state.pendingSharedPeersLine = false;
		}
		onPlayerEnteredSharedPhase(server);
	}

	private static void onPlayerEnteredSharedPhase(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (server == null || overworld == null) {
			return;
		}
		if (sharedLaunchRequiredBitcoins <= 0) {
			sharedLaunchRequiredBitcoins = Math.max(1, PLAYER_STATES.size()) * SHARED_LAUNCH_BITCOINS_PER_ASSIGNED_PLAYER;
		}
		if (!sharedLaunchIntroTriggered) {
			sharedLaunchIntroTriggered = true;
			SeasonStartVoiceSystem.fireTrigger(server, "first_player_shared_phase", null);
		}
		if (nextSharedReminderTick == Long.MIN_VALUE) {
			long nowTick = overworld.getGameTime();
			lastSharedLaunchProgressTick = nowTick;
			nextSharedReminderTick = nowTick + SHARED_IDLE_REMINDER_TICKS;
		}
		ensureSharedBitcoinPopulation(overworld);
		refreshSharedLaunchBossBar(server);
		stateDirty = true;
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

	private static void tickSharedLaunch(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (server == null || overworld == null) {
			return;
		}
		ensureStartupWorldgenDisplay(overworld);
		ensureSharedBitcoinPopulation(overworld);
		refreshSharedLaunchBossBar(server);
		if (countSharedPlayers() <= 0) {
			return;
		}
		long nowTick = overworld.getGameTime();
		if (pendingSharedFinishTick != Long.MIN_VALUE && nowTick >= pendingSharedFinishTick) {
			pendingSharedFinishTick = Long.MIN_VALUE;
			finishSeasonStart(server);
			return;
		}
		if (nextSharedReminderTick != Long.MIN_VALUE && nowTick >= nextSharedReminderTick && sharedLaunchCollectedBitcoins < sharedLaunchRequiredBitcoins) {
			SeasonStartVoiceSystem.fireTrigger(server, "shared_phase_reminder", null);
			nextSharedReminderTick = nowTick + SHARED_IDLE_REMINDER_TICKS;
		}
	}

	private static void tickShellDissolve(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (overworld == null || SHELL_DISSOLVE_ORDER.isEmpty()) {
			shellDissolving = false;
			return;
		}
		for (int i = 0; i < DISSOLVE_BATCH_BLOCKS && dissolveCursor < SHELL_DISSOLVE_ORDER.size(); i++, dissolveCursor++) {
			BlockPos pos = SHELL_DISSOLVE_ORDER.get(dissolveCursor);
			if (!overworld.getBlockState(pos).is(Blocks.BLACK_CONCRETE) && !overworld.getBlockState(pos).is(Blocks.BARRIER)) {
				continue;
			}
			if (overworld.getBlockState(pos).is(Blocks.BLACK_CONCRETE)) {
				overworld.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 4, 0.2D, 0.2D, 0.2D, 0.01D);
			}
			overworld.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		if (dissolveCursor >= SHELL_DISSOLVE_ORDER.size()) {
			shellDissolving = false;
			SHELL_DISSOLVE_ORDER.clear();
			dissolveCursor = 0;
			PLAYER_STATES.clear();
			SHARED_BITCOIN_POSITIONS.clear();
			clearSharedLaunchBossBar();
			stateDirty = true;
		}
	}

	private static void ensureSceneBuilt(ServerLevel level) {
		if (level == null || serverAnchor == null || scenePrepared) {
			return;
		}
		preparePlatformFloor(level);
		buildSceneShell(level);
		ensureStartupWorldgenDisplay(level);
		clearSceneMobs(level);
		if (!isServerStructurePresent(level, serverAnchor)) {
			ServerBlock.placeServerStructure(level, serverAnchor, Direction.NORTH);
		}
		scenePrepared = true;
	}

	private static void preparePlatformFloor(ServerLevel level) {
		BoxGeometry geometry = computeOuterBoxGeometry(resolveServerAnchor(level));
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
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		for (BlockPos pos : collectBlackShellBlocks(outerGeometry)) {
			level.setBlock(pos, Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
		}
		BoxGeometry barrierGeometry = computeBarrierGeometry(resolveServerAnchor(level));
		for (BlockPos pos : collectBarrierShellBlocks(barrierGeometry)) {
			level.setBlock(pos, Blocks.BARRIER.defaultBlockState(), 3);
		}
	}

	private static void removeSceneShellNow(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		clearStartupWorldgenDisplay(level);
		for (BlockPos pos : collectSceneShellBlocks(serverAnchor)) {
			if (level.getBlockState(pos).is(Blocks.BLACK_CONCRETE) || level.getBlockState(pos).is(Blocks.BARRIER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		scenePrepared = false;
	}

	private static List<BlockPos> collectSceneShellBlocks(BlockPos anchor) {
		List<BlockPos> blocks = new ArrayList<>();
		blocks.addAll(collectBarrierShellBlocks(computeBarrierGeometry(anchor)));
		blocks.addAll(collectBlackShellBlocks(computeOuterBoxGeometry(anchor)));
		return blocks;
	}

	private static List<BlockPos> collectBlackShellBlocks(BoxGeometry geometry) {
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

	private static List<BlockPos> collectBarrierShellBlocks(BoxGeometry geometry) {
		List<BlockPos> blocks = new ArrayList<>();
		for (int y = geometry.floorY; y >= geometry.floorY - (BARRIER_FLOOR_DEPTH - 1); y--) {
			for (int x = geometry.minX; x <= geometry.maxX; x++) {
				for (int z = geometry.minZ; z <= geometry.maxZ; z++) {
					blocks.add(new BlockPos(x, y, z));
				}
			}
		}
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
		BoxGeometry outerGeometry = computeOuterBoxGeometry(serverAnchor);
		if (!isInsideFootprint(outerGeometry, pos)) {
			return false;
		}
		if (level.getBlockState(pos).is(Blocks.BLACK_CONCRETE) || level.getBlockState(pos).is(Blocks.BARRIER)) {
			return true;
		}
		if (isServerStructureFootprint(pos)) {
			return true;
		}
		BoxGeometry barrierGeometry = computeBarrierGeometry(serverAnchor);
		for (PlayerSceneState state : PLAYER_STATES.values()) {
			SlotDefinition slot = resolveSlotDefinition(barrierGeometry, state.slotIndex);
			if (slot != null && (slot.spawnFloorPos.equals(pos) || slot.oreSupportPos.equals(pos) || slot.orePos.equals(pos))) {
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
		level.setBlock(slot.oreSupportPos, Blocks.BLACK_CONCRETE.defaultBlockState(), 3);
		level.setBlock(slot.orePos, ModBlocks.BITCOIN_ORE.defaultBlockState(), 3);
	}

	private static void tickStartupOfferings(MinecraftServer server, ServerLevel level) {
		if (server == null || level == null || serverAnchor == null) {
			return;
		}
		boolean hasGuidedPlayers = false;
		for (PlayerSceneState state : PLAYER_STATES.values()) {
			if (state != null && state.phase == PlayerPhase.GUIDED_TO_SERVER) {
				hasGuidedPlayers = true;
				break;
			}
		}
		if (!hasGuidedPlayers && countSharedPlayers() <= 0) {
			return;
		}

		ServerStructureBounds bounds = resolveServerStructureBounds();
		if (bounds == null) {
			return;
		}
		AABB scanBox = new AABB(
				bounds.minX - 1.5D,
				serverAnchor.getY() - 1.5D,
				bounds.minZ - 1.5D,
				bounds.maxX + 1.5D,
				serverAnchor.getY() + 4.5D,
				bounds.maxZ + 1.5D
		);
		for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, scanBox, entity ->
				entity != null
						&& entity.isAlive()
						&& !entity.isRemoved()
						&& !entity.getItem().isEmpty()
						&& entity.getItem().is(ModItems.BITCOIN)
		)) {
			if (distanceToServerStructureSqr(itemEntity.position(), bounds) > SHARED_FEED_RADIUS * SHARED_FEED_RADIUS) {
				continue;
			}
			ServerPlayer guidedPlayer = resolveNearestOfferingPlayer(level, itemEntity, PlayerPhase.GUIDED_TO_SERVER);
			ServerPlayer sharedPlayer = resolveNearestOfferingPlayer(level, itemEntity, PlayerPhase.SHARED);
			double guidedDistance = guidedPlayer == null ? Double.MAX_VALUE : guidedPlayer.distanceToSqr(itemEntity);
			double sharedDistance = sharedPlayer == null ? Double.MAX_VALUE : sharedPlayer.distanceToSqr(itemEntity);
			if (guidedPlayer != null && guidedDistance <= sharedDistance + 0.25D) {
				consumeOffering(itemEntity, 1);
				spawnLaunchFeedParticles(level, itemEntity.position());
				transitionPlayerAfterFirstPayment(server, guidedPlayer);
				continue;
			}
			if (sharedPlayer == null || countSharedPlayers() <= 0) {
				continue;
			}
			int consumed = itemEntity.getItem().getCount();
			if (consumed <= 0) {
				continue;
			}
			consumeOffering(itemEntity, consumed);
			spawnLaunchFeedParticles(level, itemEntity.position());
			incrementSharedLaunchProgress(server, consumed);
		}
	}

	private static ServerPlayer resolveNearestOfferingPlayer(ServerLevel level, ItemEntity itemEntity, PlayerPhase phase) {
		if (level == null || itemEntity == null || phase == null) {
			return null;
		}
		ServerPlayer matched = null;
		double bestDistance = SHARED_FEED_PLAYER_MATCH_DISTANCE * SHARED_FEED_PLAYER_MATCH_DISTANCE;
		for (ServerPlayer player : level.players()) {
			PlayerSceneState state = PLAYER_STATES.get(player.getUUID());
			if (state == null || state.phase != phase) {
				continue;
			}
			double distance = player.distanceToSqr(itemEntity);
			if (distance < bestDistance) {
				bestDistance = distance;
				matched = player;
			}
		}
		return matched;
	}

	private static void consumeOffering(ItemEntity itemEntity, int amount) {
		if (itemEntity == null || amount <= 0) {
			return;
		}
		ItemStack stack = itemEntity.getItem();
		if (stack.isEmpty() || !stack.is(ModItems.BITCOIN)) {
			return;
		}
		if (amount >= stack.getCount()) {
			itemEntity.discard();
			return;
		}
		stack.shrink(amount);
		itemEntity.setItem(stack);
	}

	private static void spawnLaunchFeedParticles(ServerLevel level, Vec3 position) {
		if (level == null || position == null) {
			return;
		}
		ServerStabilitySystem.emitFeedParticles(level, position.x, position.y, position.z, 10);
	}

	private static void incrementSharedLaunchProgress(MinecraftServer server, int bitcoins) {
		if (server == null || bitcoins <= 0) {
			return;
		}
		if (sharedLaunchRequiredBitcoins <= 0) {
			sharedLaunchRequiredBitcoins = Math.max(1, PLAYER_STATES.size()) * SHARED_LAUNCH_BITCOINS_PER_ASSIGNED_PLAYER;
		}
		if (sharedLaunchCollectedBitcoins >= sharedLaunchRequiredBitcoins) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long nowTick = overworld == null ? 0L : overworld.getGameTime();
		sharedLaunchCollectedBitcoins = Math.min(sharedLaunchRequiredBitcoins, sharedLaunchCollectedBitcoins + bitcoins);
		lastSharedLaunchProgressTick = nowTick;
		nextSharedReminderTick = nowTick + SHARED_IDLE_REMINDER_TICKS;
		while (sharedLaunchMilestoneCursor < SHARED_LAUNCH_MILESTONES.length
				&& getSharedLaunchPercent() >= SHARED_LAUNCH_MILESTONES[sharedLaunchMilestoneCursor]) {
			String trigger = resolveSharedLaunchMilestoneTrigger(SHARED_LAUNCH_MILESTONES[sharedLaunchMilestoneCursor]);
			if (trigger != null) {
				SeasonStartVoiceSystem.fireTrigger(server, trigger, null);
			}
			sharedLaunchMilestoneCursor++;
		}
		if (sharedLaunchCollectedBitcoins >= sharedLaunchRequiredBitcoins && pendingSharedFinishTick == Long.MIN_VALUE) {
			pendingSharedFinishTick = nowTick + resolveTriggerSequenceDurationTicks("shared_launch_complete") + SHARED_FINISH_DELAY_TICKS;
		}
		refreshSharedLaunchBossBar(server);
		stateDirty = true;
	}

	private static String resolveSharedLaunchMilestoneTrigger(int milestone) {
		return switch (milestone) {
			case 50 -> "shared_launch_halfway";
			case 90 -> "shared_launch_ninety";
			case 100 -> "shared_launch_complete";
			default -> null;
		};
	}

	private static int getSharedLaunchPercent() {
		if (sharedLaunchRequiredBitcoins <= 0) {
			return 0;
		}
		double percent = (double) sharedLaunchCollectedBitcoins * 100.0D / (double) sharedLaunchRequiredBitcoins;
		return Mth.clamp((int) Math.floor(percent), 0, 100);
	}

	private static void ensureStartupWorldgenDisplay(ServerLevel level) {
		if (level == null || serverAnchor == null || !active || completed) {
			return;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(serverAnchor);
		AABB displayBounds = startupWorldgenDisplayBounds(serverAnchor, outerGeometry);
		Display.ItemDisplay display = resolveStartupWorldgenDisplay(level, displayBounds);
		boolean created = display == null;
		if (created) {
			display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
			display.addTag(STARTUP_WORLDGEN_DISPLAY_TAG);
		}
		if (display == null) {
			return;
		}

		float scale = Math.max(
				16.0F,
				Math.min(outerGeometry.maxX - outerGeometry.minX, outerGeometry.maxZ - outerGeometry.minZ) - STARTUP_WORLDGEN_MARGIN_BLOCKS
		);
		display.setPos(serverAnchor.getX() + 0.5D, outerGeometry.roofY - STARTUP_WORLDGEN_ROOF_OFFSET, serverAnchor.getZ() + 0.5D);
		display.setYRot(0.0F);
		display.setXRot(0.0F);
		display.setYHeadRot(0.0F);
		display.setYBodyRot(0.0F);
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setTransformation(new Transformation(
				new Vector3f(0.0F, 0.0F, 0.0F),
				new Quaternionf(),
				new Vector3f(scale, STARTUP_WORLDGEN_THICKNESS_SCALE, scale),
				new Quaternionf()
		));
		display.setBrightnessOverride(STARTUP_WORLDGEN_BRIGHTNESS);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(STARTUP_WORLDGEN_VIEW_RANGE);
		ItemDisplayHitboxHelper.clear(display);

		int desiredFrameIndex = resolveStartupWorldgenFrameIndex();
		if (created || desiredFrameIndex != startupWorldgenFrameIndex) {
			display.setItemStack(createStartupWorldgenFrameStack(desiredFrameIndex));
			startupWorldgenFrameIndex = desiredFrameIndex;
		}

		if (created) {
			level.addFreshEntity(display);
		}
	}

	private static int resolveStartupWorldgenFrameIndex() {
		int percent = getSharedLaunchPercent();
		double normalized = percent / 100.0D;
		return Mth.clamp((int) Math.round(normalized * (STARTUP_WORLDGEN_FRAME_COUNT - 1)), 0, STARTUP_WORLDGEN_FRAME_COUNT - 1);
	}

	private static ItemStack createStartupWorldgenFrameStack(int frameIndex) {
		int clamped = Mth.clamp(frameIndex, 0, STARTUP_WORLDGEN_FRAME_COUNT - 1);
		String suffix = clamped < 10 ? "0" + clamped : Integer.toString(clamped);
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "startup_worldgen_frame_" + suffix));
		return stack;
	}

	private static AABB startupWorldgenDisplayBounds(BlockPos anchor, BoxGeometry outerGeometry) {
		double centerX = anchor.getX() + 0.5D;
		double centerY = outerGeometry.roofY - STARTUP_WORLDGEN_ROOF_OFFSET;
		double centerZ = anchor.getZ() + 0.5D;
		return new AABB(
				centerX - 2.0D,
				centerY - 2.0D,
				centerZ - 2.0D,
				centerX + 2.0D,
				centerY + 2.0D,
				centerZ + 2.0D
		);
	}

	private static Display.ItemDisplay resolveStartupWorldgenDisplay(ServerLevel level, AABB box) {
		if (level == null || box == null) {
			return null;
		}
		List<Display.ItemDisplay> displays = level.getEntities(
				EntityType.ITEM_DISPLAY,
				box,
				display -> display.getTags().contains(STARTUP_WORLDGEN_DISPLAY_TAG)
		);
		if (displays.isEmpty()) {
			return null;
		}
		Display.ItemDisplay root = displays.get(0);
		for (int index = 1; index < displays.size(); index++) {
			displays.get(index).discard();
		}
		return root;
	}

	private static void clearStartupWorldgenDisplay(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			startupWorldgenFrameIndex = Integer.MIN_VALUE;
			return;
		}
		AABB box = startupWorldgenDisplayBounds(serverAnchor, computeOuterBoxGeometry(serverAnchor));
		for (Display.ItemDisplay display : level.getEntities(
				EntityType.ITEM_DISPLAY,
				box,
				candidate -> candidate.getTags().contains(STARTUP_WORLDGEN_DISPLAY_TAG)
		)) {
			display.discard();
		}
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
	}

	private static void refreshSharedLaunchBossBar(MinecraftServer server) {
		clearSharedLaunchBossBar();
	}

	private static void clearSharedLaunchBossBar() {
		if (sharedLaunchBossBar != null) {
			sharedLaunchBossBar.removeAllPlayers();
			sharedLaunchBossBar = null;
		}
	}

	private static void ensureSharedBitcoinPopulation(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		pruneSharedBitcoinPositions(level);
		int targetCount = Math.max(0, countSharedPlayers() * SHARED_ACTIVE_ORES_PER_PLAYER);
		if (targetCount <= 0) {
			clearSharedBitcoins(level, true);
			return;
		}
		while (SHARED_BITCOIN_POSITIONS.size() > targetCount) {
			BlockPos pos = SHARED_BITCOIN_POSITIONS.iterator().next();
			SHARED_BITCOIN_POSITIONS.remove(pos);
			if (isSharedBitcoinBlock(level.getBlockState(pos))) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		int attempts = 0;
		while (SHARED_BITCOIN_POSITIONS.size() < targetCount && attempts++ < targetCount * 80) {
			BlockPos candidate = pickSharedBitcoinSpawnPos(level);
			if (candidate == null) {
				break;
			}
			level.setBlock(
					candidate,
					(level.getRandom().nextBoolean() ? ModBlocks.BITCOIN_ORE : ModBlocks.DEEPSLATE_BITCOIN_ORE).defaultBlockState(),
					3
			);
			SHARED_BITCOIN_POSITIONS.add(candidate.immutable());
			Vec3 center = centerOf(candidate);
			ServerStabilitySystem.emitFeedParticles(level, center.x, center.y - 0.4D, center.z, 8);
		}
	}

	private static void pruneSharedBitcoinPositions(ServerLevel level) {
		if (level == null) {
			return;
		}
		SHARED_BITCOIN_POSITIONS.removeIf(pos -> pos == null || !isSharedBitcoinBlock(level.getBlockState(pos)));
	}

	private static BlockPos pickSharedBitcoinSpawnPos(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return null;
		}
		BoxGeometry geometry = computeBarrierGeometry(serverAnchor);
		ServerStructureBounds bounds = resolveServerStructureBounds();
		int minX = geometry.minX + 1;
		int maxX = geometry.maxX - 1;
		int minZ = geometry.minZ + 1;
		int maxZ = geometry.maxZ - 1;
		int minY = geometry.floorY + SHARED_ORE_MIN_Y_OFFSET;
		int maxY = Math.min(geometry.roofY - 1, geometry.floorY + SHARED_ORE_MAX_Y_OFFSET);
		if (maxX < minX || maxZ < minZ || maxY < minY) {
			return null;
		}
		for (int attempt = 0; attempt < 96; attempt++) {
			int x = Mth.nextInt(level.getRandom(), minX, maxX);
			int y = Mth.nextInt(level.getRandom(), minY, maxY);
			int z = Mth.nextInt(level.getRandom(), minZ, maxZ);
			BlockPos candidate = new BlockPos(x, y, z);
			if (!isValidSharedBitcoinSpawn(level, candidate, geometry, bounds)) {
				continue;
			}
			return candidate;
		}
		return null;
	}

	private static boolean isValidSharedBitcoinSpawn(ServerLevel level, BlockPos pos, BoxGeometry geometry, ServerStructureBounds bounds) {
		if (level == null || pos == null || geometry == null || !isInsideFootprint(geometry, pos)) {
			return false;
		}
		if (SHARED_BITCOIN_POSITIONS.contains(pos) || !level.getBlockState(pos).isAir()) {
			return false;
		}
		if (isServerStructureFootprint(pos) || isIntroReservedPosition(pos)) {
			return false;
		}
		return bounds == null || distanceToServerStructureSqr(centerOf(pos), bounds) >= SHARED_ORE_SERVER_BUFFER * SHARED_ORE_SERVER_BUFFER;
	}

	private static boolean isIntroReservedPosition(BlockPos pos) {
		if (pos == null || serverAnchor == null) {
			return false;
		}
		BoxGeometry barrierGeometry = computeBarrierGeometry(serverAnchor);
		for (PlayerSceneState state : PLAYER_STATES.values()) {
			if (state == null) {
				continue;
			}
			SlotDefinition slot = resolveSlotDefinition(barrierGeometry, state.slotIndex);
			if (slot == null) {
				continue;
			}
			if (slot.spawnFloorPos.equals(pos)
					|| slot.spawnFloorPos.above().equals(pos)
					|| slot.oreSupportPos.equals(pos)
					|| slot.orePos.equals(pos)) {
				return true;
			}
		}
		return false;
	}

	private static void clearSharedBitcoins(ServerLevel level, boolean removeBlocks) {
		if (level == null || serverAnchor == null) {
			SHARED_BITCOIN_POSITIONS.clear();
			return;
		}
		BoxGeometry geometry = computeBarrierGeometry(serverAnchor);
		for (int x = geometry.minX + 1; x <= geometry.maxX - 1; x++) {
			for (int y = geometry.floorY + SHARED_ORE_MIN_Y_OFFSET; y <= Math.min(geometry.roofY - 1, geometry.floorY + SHARED_ORE_MAX_Y_OFFSET); y++) {
				for (int z = geometry.minZ + 1; z <= geometry.maxZ - 1; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!isSharedBitcoinBlock(level.getBlockState(pos)) || isIntroReservedPosition(pos)) {
						continue;
					}
					if (removeBlocks) {
						level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		SHARED_BITCOIN_POSITIONS.clear();
	}

	private static boolean isSharedBitcoinBlock(net.minecraft.world.level.block.state.BlockState state) {
		return state != null && (state.is(ModBlocks.BITCOIN_ORE) || state.is(ModBlocks.DEEPSLATE_BITCOIN_ORE));
	}

	private static double distanceToServerStructureSqr(Vec3 point, ServerStructureBounds bounds) {
		if (point == null || bounds == null) {
			return Double.MAX_VALUE;
		}
		double dx = axisDistance(point.x, bounds.minX, bounds.maxX);
		double dy = axisDistance(point.y, serverAnchor.getY(), serverAnchor.getY() + ServerStructureBreakSystem.STRUCTURE_HEIGHT);
		double dz = axisDistance(point.z, bounds.minZ, bounds.maxZ);
		return dx * dx + dy * dy + dz * dz;
	}

	private static void clearSceneMobs(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		BoxGeometry geometry = computeOuterBoxGeometry(serverAnchor);
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Mob && isInsideFootprint(geometry, entity.blockPosition())) {
				entity.discard();
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

	private static void ensureGuidedPlayerState(ServerPlayer player, PlayerSceneState state, SlotDefinition slot) {
		if (player == null || state == null || slot == null || serverAnchor == null) {
			return;
		}
		ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		if (level == null) {
			return;
		}
		BoxGeometry barrierGeometry = computeBarrierGeometry(resolveServerAnchor(level));
		Vec3 target = slot.spawnPos;
		if (!player.level().dimension().equals(Level.OVERWORLD) || !isInsideFootprint(barrierGeometry, player.blockPosition())) {
			teleportPlayer(player, level, target, slot.yaw);
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

	private static void ensureWaitingStartPlayerState(ServerPlayer player, PlayerSceneState state, SlotDefinition slot) {
		if (player == null || state == null || slot == null) {
			return;
		}
		Vec3 target = slot.spawnPos;
		if (!player.level().dimension().equals(Level.OVERWORLD) || player.distanceToSqr(target.x, target.y, target.z) > 225.0D) {
			MinecraftServer server = player.level().getServer();
			ServerLevel overworld = server == null ? null : server.overworld();
			teleportPlayer(player, overworld, target, slot.yaw);
		}
		player.setSilent(true);
		player.setGameMode(GameType.ADVENTURE);
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, get().introBlindnessTicks, 0, false, false, true));
		if (!player.hasEffect(MobEffects.INVISIBILITY)) {
			player.addEffect(ServerAbsoluteInvisibilitySystem.createEffectInstance());
		}
		ServerAbsoluteInvisibilitySystem.activate(player);
		removeIntroTool(player);
	}

	private static void ensureSharedPlayerState(ServerPlayer player) {
		if (player == null) {
			return;
		}
		applySharedPlayerState(player);
	}

	private static void ensureRestoringPlayerState(ServerPlayer player, PlayerSceneState state) {
		if (player == null || state == null) {
			return;
		}
		player.setGameMode(GameType.SURVIVAL);
		player.setSilent(true);
		if (!state.sharedVisionRestored) {
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, get().introBlindnessTicks, 0, false, false, true));
		} else {
			player.removeEffect(MobEffects.BLINDNESS);
		}
		if (!state.sharedBodyRestored) {
			if (!player.hasEffect(MobEffects.INVISIBILITY)) {
				player.addEffect(ServerAbsoluteInvisibilitySystem.createEffectInstance());
			}
			ServerAbsoluteInvisibilitySystem.activate(player);
		} else {
			ServerAbsoluteInvisibilitySystem.deactivate(player);
			player.removeEffect(MobEffects.INVISIBILITY);
		}
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
		if (state.phase == PlayerPhase.RESTORING) {
			ensureRestoringPlayerState(player, state);
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
				&& (state.phase == PlayerPhase.WAITING_START
				|| state.phase == PlayerPhase.ISOLATED
				|| state.phase == PlayerPhase.GUIDED_TO_SERVER
				|| state.phase == PlayerPhase.RESTORING);
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
		BlockPos spawn = serverAnchor.offset(0, 0, get().barrierHalfDepth - 2);
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
			slots.add(createSlot(slots.size(), floorY, x, geometry.minZ + inset, Direction.SOUTH));
		}
		for (int z = geometry.minZ + inset + spacing; z <= geometry.maxZ - inset; z += spacing) {
			slots.add(createSlot(slots.size(), floorY, geometry.maxX - inset, z, Direction.WEST));
		}
		for (int x = geometry.maxX - inset - spacing; x >= geometry.minX + inset; x -= spacing) {
			slots.add(createSlot(slots.size(), floorY, x, geometry.maxZ - inset, Direction.NORTH));
		}
		for (int z = geometry.maxZ - inset - spacing; z >= geometry.minZ + inset + spacing; z -= spacing) {
			slots.add(createSlot(slots.size(), floorY, geometry.minX + inset, z, Direction.EAST));
		}
		return slots;
	}

	private static SlotDefinition createSlot(int slotIndex, int floorY, int x, int z, Direction facing) {
		BlockPos floor = new BlockPos(x, floorY, z);
		int[] lateralOffsets = {-4, -2, 0, 2, 4};
		int lateral = lateralOffsets[Math.floorMod(slotIndex * 3 + 1, lateralOffsets.length)];
		Direction lateralDirection = lateral >= 0 ? facing.getClockWise() : facing.getCounterClockWise();
		BlockPos oreSupportPos = floor.relative(facing, 8).relative(lateralDirection, Math.abs(lateral)).above();
		BlockPos orePos = oreSupportPos.above();
		Vec3 spawnPos = new Vec3(x + 0.5D, floorY + 1.0D, z + 0.5D);
		return new SlotDefinition(floor, oreSupportPos, orePos, spawnPos, facing.toYRot());
	}

	private static BoxGeometry computeOuterBoxGeometry(BlockPos anchor) {
		int halfExtent = Math.max(get().boxHalfWidth, get().boxHalfDepth);
		int barrierFloorY = anchor.getY() - 1;
		int floorY = barrierFloorY - BARRIER_FLOOR_DEPTH;
		int sideLength = halfExtent * 2 + 1;
		return new BoxGeometry(
				anchor.getX() - halfExtent,
				anchor.getX() + halfExtent,
				anchor.getZ() - halfExtent,
				anchor.getZ() + halfExtent,
				floorY,
				floorY + sideLength - 1
		);
	}

	private static BoxGeometry computeBarrierGeometry(BlockPos anchor) {
		int halfWidth = get().barrierHalfWidth;
		int halfDepth = get().barrierHalfDepth;
		int floorY = anchor.getY() - 1;
		return new BoxGeometry(
				anchor.getX() - halfWidth,
				anchor.getX() + halfWidth,
				anchor.getZ() - halfDepth,
				anchor.getZ() + halfDepth,
				floorY,
				floorY + resolveBarrierBoxHeight()
		);
	}

	private static int resolveBarrierBoxHeight() {
		return Math.max(get().barrierHeight, Math.max(get().barrierHalfWidth, get().barrierHalfDepth) * 2);
	}

	private static Path getStatePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE_NAME);
	}

	private static void loadState(MinecraftServer server) {
		Path path = getStatePath(server);
		PLAYER_STATES.clear();
		SHARED_BITCOIN_POSITIONS.clear();
		clearSharedLaunchBossBar();
		stateLoaded = true;
		stateDirty = false;
		scenePrepared = false;
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		sharedLaunchCollectedBitcoins = 0;
		sharedLaunchRequiredBitcoins = 0;
		sharedLaunchMilestoneCursor = 0;
		sharedLaunchIntroTriggered = false;
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
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
			difficultyBeforeSeasonStart = parseDifficulty(state.difficultyBeforeSeasonStart);
			sharedLaunchCollectedBitcoins = Math.max(0, state.sharedLaunchCollectedBitcoins);
			sharedLaunchRequiredBitcoins = Math.max(0, state.sharedLaunchRequiredBitcoins);
			sharedLaunchMilestoneCursor = Math.max(0, Math.min(SHARED_LAUNCH_MILESTONES.length, state.sharedLaunchMilestoneCursor));
			sharedLaunchIntroTriggered = state.sharedLaunchIntroTriggered;
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
		state.difficultyBeforeSeasonStart = difficultyBeforeSeasonStart == null ? "" : difficultyBeforeSeasonStart.getKey();
		state.sharedLaunchCollectedBitcoins = sharedLaunchCollectedBitcoins;
		state.sharedLaunchRequiredBitcoins = sharedLaunchRequiredBitcoins;
		state.sharedLaunchMilestoneCursor = sharedLaunchMilestoneCursor;
		state.sharedLaunchIntroTriggered = sharedLaunchIntroTriggered;
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

	private static Difficulty parseDifficulty(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		for (Difficulty difficulty : Difficulty.values()) {
			if (difficulty.getKey().equalsIgnoreCase(value)) {
				return difficulty;
			}
		}
		return null;
	}

	private static ItemStack createIntroToolTemplate() {
		ItemStack stack = new ItemStack(ModItems.SPECIAL_PICKAXE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Пусковой инструмент"));
		return stack;
	}

	private static void primeObservationState(ServerPlayer player, PlayerSceneState state) {
		if (player == null || state == null || player.level() == null) {
			return;
		}
		long nowTick = player.level().getGameTime();
		state.lastActivityTick = nowTick;
		state.nextIdleReactionTick = nowTick + INTRO_IDLE_TRIGGER_TICKS;
		state.nextLeaveReactionTick = 0L;
		state.nextSpinReactionTick = 0L;
		state.nextJumpReactionTick = 0L;
		state.nextAirPunchReactionTick = 0L;
		state.nextIntroTargetReactionTick = 0L;
		state.introTargetLocked = false;
		state.nextGuidanceTick = 0L;
		state.nextGuidanceVoiceTick = 0L;
		state.nextGuidanceEarliestTick = 0L;
		state.lastGuidanceStateKey = "";
		state.lastGuidanceDistance = Double.NaN;
		state.lastGuidanceAbsYaw = Double.NaN;
		state.lastGuidanceDistanceBucket = Integer.MIN_VALUE;
		state.wasGuidanceAligned = false;
		state.wasGuidanceClose = false;
		state.lastGuidanceAlignedTick = Long.MIN_VALUE;
		state.guidanceRouteStarted = false;
		state.guidanceAlignedEver = false;
		state.guidanceMistakeCount = 0;
		state.lastGuidanceTurnDirection = TurnHintDirection.NONE;
		state.lastGuidanceTurnAbsYaw = Double.NaN;
		state.lastGuidanceTurnTick = Long.MIN_VALUE;
		state.lastGuidanceTurnRecoverUsed = false;
		state.announcedServerSight = false;
		state.guidanceCueCycles.clear();
		if (state.phase == PlayerPhase.WAITING_START && state.nextStartPromptTick <= 0L && player.level() != null) {
			state.nextStartPromptTick = player.level().getGameTime() + WAITING_START_INITIAL_PROMPT_TICKS;
		}
		state.lastOnGround = player.onGround();
		state.spinScore = 0.0D;
		updateObservationBaseline(player, state);
	}

	private static void beginIntroAfterChatStart(MinecraftServer server, ServerPlayer player, PlayerSceneState state) {
		if (server == null || player == null || state == null || state.phase != PlayerPhase.WAITING_START) {
			return;
		}
		long nowTick = player.level() == null ? 0L : player.level().getGameTime();
		state.phase = PlayerPhase.ISOLATED;
		state.nextStartPromptTick = Long.MAX_VALUE;
		state.guidanceNarrationGateTick = nowTick + resolveTriggerSequenceDurationTicks("player_intro_assigned");
		stateDirty = true;
		primeObservationState(player, state);
		SeasonStartVoiceSystem.fireTrigger(server, "player_waiting_start_confirmed", player);
		SeasonStartVoiceSystem.fireTrigger(server, "player_intro_assigned", player);
	}

	private static void fireRoundRobinTrigger(
			MinecraftServer server,
			ServerPlayer player,
			PlayerSceneState state,
			String[] triggers,
			boolean promptCycle
	) {
		if (server == null || player == null || state == null || triggers == null || triggers.length == 0) {
			return;
		}
		int index;
		if (promptCycle) {
			index = Math.floorMod(state.startPromptVariantIndex++, triggers.length);
		} else {
			index = Math.floorMod(state.wrongStartVariantIndex++, triggers.length);
		}
		SeasonStartVoiceSystem.fireTrigger(server, triggers[index], player);
	}

	private static void fireTriggerCycle(
			MinecraftServer server,
			ServerPlayer player,
			PlayerSceneState state,
			String cycleKey,
			String[] triggers
	) {
		if (server == null || player == null || state == null || cycleKey == null || cycleKey.isBlank() || triggers == null || triggers.length == 0) {
			return;
		}
		int nextIndex = Math.floorMod(state.guidanceCueCycles.getOrDefault(cycleKey, 0), triggers.length);
		state.guidanceCueCycles.put(cycleKey, nextIndex + 1);
		SeasonStartVoiceSystem.fireTrigger(server, triggers[nextIndex], player);
	}

	private static boolean matchesStartWord(String content) {
		if (content == null) {
			return false;
		}
		String normalized = content.trim();
		return START_WORD_EN.equalsIgnoreCase(normalized) || START_WORD_RU.equalsIgnoreCase(normalized);
	}

	private static boolean shouldUseLatinReplacement(String content) {
		if (content == null || content.isBlank()) {
			return false;
		}
		for (int index = 0; index < content.length(); index++) {
			if (Character.UnicodeScript.of(content.charAt(index)) == Character.UnicodeScript.LATIN) {
				return true;
			}
		}
		return false;
	}

	private static void updateObservationBaseline(ServerPlayer player, PlayerSceneState state) {
		if (player == null || state == null || player.level() == null) {
			return;
		}
		state.lastObservedX = player.getX();
		state.lastObservedY = player.getY();
		state.lastObservedZ = player.getZ();
		state.lastYaw = player.getYRot();
		state.lastPitch = player.getXRot();
		state.lastOnGround = player.onGround();
		state.lastObservationTick = player.level().getGameTime();
	}

	private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return dx * dx + dz * dz;
	}

	private static double axisDistance(double value, double min, double max) {
		if (value < min) {
			return min - value;
		}
		if (value > max) {
			return value - max;
		}
		return 0.0D;
	}

	private static Vec3 centerOf(BlockPos pos) {
		return pos == null ? Vec3.ZERO : new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
	}

	private static boolean isLookingAtIntroOre(ServerPlayer player, SlotDefinition slot) {
		if (player == null || slot == null) {
			return false;
		}
		HitResult hit = player.pick(INTRO_PICK_REACH, 0.0F, false);
		return hit instanceof BlockHitResult blockHit
				&& hit.getType() == HitResult.Type.BLOCK
				&& slot.orePos.equals(blockHit.getBlockPos());
	}

	private static boolean isLookingAtServerStructure(ServerPlayer player) {
		if (player == null || serverAnchor == null) {
			return false;
		}
		HitResult hit = player.pick(INTRO_PICK_REACH, 0.0F, false);
		return hit instanceof BlockHitResult blockHit
				&& hit.getType() == HitResult.Type.BLOCK
				&& isServerStructureFootprint(blockHit.getBlockPos());
	}

	private enum PlayerPhase {
		WAITING_START("waiting_start"),
		ISOLATED("isolated"),
		GUIDED_TO_SERVER("guided"),
		RESTORING("restoring"),
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
		private long restoreVisionTick = Long.MAX_VALUE;
		private long restoreBodyTick = Long.MAX_VALUE;
		private long activateSharedTick = Long.MAX_VALUE;
		private boolean sharedVisionRestored = false;
		private boolean sharedBodyRestored = false;
		private boolean pendingSharedPeersLine = false;
		private long guidanceNarrationGateTick = 0L;
		private long nextGuidanceTick = 0L;
		private long nextGuidanceVoiceTick = 0L;
		private long nextGuidanceEarliestTick = 0L;
		private String lastGuidanceStateKey = "";
		private double lastGuidanceDistance = Double.NaN;
		private double lastGuidanceAbsYaw = Double.NaN;
		private int lastGuidanceDistanceBucket = Integer.MIN_VALUE;
		private boolean wasGuidanceAligned = false;
		private boolean wasGuidanceClose = false;
		private long lastGuidanceAlignedTick = Long.MIN_VALUE;
		private boolean guidanceRouteStarted = false;
		private boolean guidanceAlignedEver = false;
		private int guidanceMistakeCount = 0;
		private boolean announcedServerSight = false;
		private boolean guidanceQuietZoneActive = false;
		private TurnHintDirection lastGuidanceTurnDirection = TurnHintDirection.NONE;
		private double lastGuidanceTurnAbsYaw = Double.NaN;
		private long lastGuidanceTurnTick = Long.MIN_VALUE;
		private boolean lastGuidanceTurnRecoverUsed = false;
		private final Map<String, Integer> guidanceCueCycles = new LinkedHashMap<>();
		private long lastActivityTick = 0L;
		private long nextIdleReactionTick = 0L;
		private long nextLeaveReactionTick = 0L;
		private long nextSpinReactionTick = 0L;
		private long nextJumpReactionTick = 0L;
		private long nextAirPunchReactionTick = 0L;
		private long nextIntroTargetReactionTick = 0L;
		private long lastObservationTick = Long.MIN_VALUE;
		private double lastObservedX;
		private double lastObservedY;
		private double lastObservedZ;
		private float lastYaw;
		private float lastPitch;
		private boolean lastOnGround = true;
		private boolean introTargetLocked = false;
		private double spinScore = 0.0D;
		private long nextStartPromptTick = 0L;
		private int startPromptVariantIndex = 0;
		private int wrongStartVariantIndex = 0;
	}

	private record BoxGeometry(int minX, int maxX, int minZ, int maxZ, int floorY, int roofY) {
	}

	private record SlotDefinition(BlockPos spawnFloorPos, BlockPos oreSupportPos, BlockPos orePos, Vec3 spawnPos, float yaw) {
	}

	private record GuidanceSnapshot(double horizontalDistance, double deltaYaw, boolean aligned, int distanceBucket) {
		private GuidanceSnapshot withAlignmentLock() {
			return new GuidanceSnapshot(horizontalDistance, 0.0D, true, distanceBucket);
		}
	}

	private record GuidanceInstruction(String stateKey, String groupKey, String[] triggers, long cooldownTicks) {
	}

	private record ServerStructureBounds(double minX, double maxX, double minZ, double maxZ) {
	}

	private enum TurnHintDirection {
		NONE,
		LEFT,
		RIGHT
	}

	private enum VerticalAimHint {
		NONE,
		UP,
		DOWN
	}

	private static final class PersistedState {
		private boolean bootstrapComplete;
		private boolean active;
		private boolean completed;
		private String serverAnchor;
		private String difficultyBeforeSeasonStart;
		private int sharedLaunchCollectedBitcoins;
		private int sharedLaunchRequiredBitcoins;
		private int sharedLaunchMilestoneCursor;
		private boolean sharedLaunchIntroTriggered;
		private Map<String, PersistedPlayerState> players;
	}

	private static final class PersistedPlayerState {
		private int slotIndex;
		private String phase;
		private boolean minedIntroBitcoin;
		private boolean poweredServer;
	}
}
