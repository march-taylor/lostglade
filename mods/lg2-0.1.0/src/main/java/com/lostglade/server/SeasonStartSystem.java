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
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
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
	private static final int SCENE_BLOCK_SET_FLAGS = 2 | 16 | 32;
	private static final int EXISTING_SERVER_SCAN_RADIUS = 192;
	private static final int EXISTING_SERVER_SCAN_VERTICAL_MARGIN = 24;
	private static final Brightness STARTUP_WORLDGEN_BRIGHTNESS = Brightness.FULL_BRIGHT;
	private static final Identifier WORLD_REVEAL_EARTHQUAKE_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "season_start_earthquake");
	private static final Holder<SoundEvent> WORLD_REVEAL_EARTHQUAKE_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(WORLD_REVEAL_EARTHQUAKE_SOUND_ID));
	private static final float WORLD_REVEAL_EARTHQUAKE_VOLUME = 7.5F;
	private static final long WORLD_REVEAL_CRACKING_DURATION_TICKS = 20L * 30L;
	private static final long WORLD_REVEAL_DARKNESS_ONSET_TICKS = 60L;
	private static final long WORLD_REVEAL_CRACK_START_BUFFER_TICKS = 4L;
	private static final long WORLD_REVEAL_DARKNESS_LEAD_OUT_TICKS = 20L * 4L;
	private static final double WORLD_REVEAL_VISIBLE_TARGET_PROGRESS = 1.0D;
	private static final int WORLD_REVEAL_VISIBLE_PROTECTION_RADIUS = 2;
	private static final int WORLD_REVEAL_VISIBLE_PROTECTION_HEIGHT = 4;
	private static final int WORLD_REVEAL_CRACK_PARTICLE_LOOKAHEAD_EPISODES = 5;
	private static final double WORLD_REVEAL_CRACK_SAMPLE_STEP = 0.42D;
	private static final double WORLD_REVEAL_PRIMARY_CRACK_DISPLACEMENT = 5.4D;
	private static final double WORLD_REVEAL_BRANCH_CRACK_DISPLACEMENT = 3.1D;
	private static final double WORLD_REVEAL_MAX_TERRAIN_GROWTH_RADIUS = 3.6D;
	private static final long WORLD_REVEAL_POST_START_MORNING_TIME = 1000L;
	private static final int WORLD_REVEAL_EPISODE_MAX_REVEAL_POSITIONS = 56;
	private static final int WORLD_REVEAL_EPISODE_MAX_PARTICLE_POINTS = 18;
	private static final int WORLD_REVEAL_RELOCATE_DEFERRED_BATCH = 36;
	private static final int WORLD_REVEAL_SETTLE_DEFERRED_BATCH = 320;
	private static final DustParticleOptions WORLD_REVEAL_CRACK_CORE_PARTICLE = new DustParticleOptions(0x09090C, 1.18F);
	private static final DustParticleOptions WORLD_REVEAL_CRACK_EDGE_PARTICLE = new DustParticleOptions(0x2F3138, 0.78F);
	private static final long WORLD_REVEAL_RELOCATE_MIN_TICKS = 20L;
	private static final long WORLD_REVEAL_SETTLE_MIN_TICKS = 20L;
	private static final double WORLD_REVEAL_HORIZONTAL_SPEED = 0.22D;
	private static final double WORLD_REVEAL_VERTICAL_SPEED = 0.30D;
	private static final double WORLD_REVEAL_READY_DISTANCE_SQR = 0.65D * 0.65D;
	private static final double WORLD_REVEAL_READY_VERTICAL_DELTA = 0.72D;
	private static final double WORLD_REVEAL_PREP_TARGET_OFFSET = 1.35D;
	private static final double WORLD_REVEAL_FINAL_TARGET_OFFSET = 1.02D;
	// Synced to the densest short-impact section of `Downloads/zemletryasenie-2.mp3` (7.1s-37.1s).
	private static final WorldRevealBurst[] WORLD_REVEAL_CRACK_BURSTS = {
			burst(7, 0.445F, 14, 0.545F),
			burst(18, 0.495F, 15, 0.586F),
			burst(26, 0.510F, 15, 0.598F),
			burst(37, 0.545F, 16, 0.627F),
			burst(44, 0.960F, 23, 0.968F),
			burst(50, 0.598F, 17, 0.670F),
			burst(56, 0.662F, 18, 0.723F),
			burst(65, 0.586F, 17, 0.661F),
			burst(74, 1.000F, 24, 1.000F),
			burst(83, 0.964F, 23, 0.970F),
			burst(91, 0.866F, 22, 0.890F),
			burst(100, 0.500F, 15, 0.590F),
			burst(108, 0.671F, 18, 0.730F),
			burst(119, 0.892F, 22, 0.912F),
			burst(129, 0.912F, 22, 0.928F),
			burst(136, 0.610F, 17, 0.681F),
			burst(144, 0.548F, 16, 0.630F),
			burst(155, 1.000F, 24, 1.000F),
			burst(164, 0.863F, 22, 0.888F),
			burst(172, 0.528F, 16, 0.613F),
			burst(180, 0.611F, 17, 0.681F),
			burst(186, 0.647F, 18, 0.711F),
			burst(195, 0.487F, 15, 0.579F),
			burst(207, 0.628F, 17, 0.695F),
			burst(214, 0.428F, 14, 0.531F),
			burst(221, 0.625F, 17, 0.693F),
			burst(241, 0.493F, 15, 0.584F),
			burst(249, 0.663F, 18, 0.723F),
			burst(256, 0.462F, 14, 0.559F),
			burst(271, 0.667F, 18, 0.727F),
			burst(279, 0.961F, 23, 0.968F),
			burst(288, 0.640F, 18, 0.704F),
			burst(298, 0.446F, 14, 0.546F),
			burst(305, 0.605F, 17, 0.676F),
			burst(315, 0.635F, 17, 0.701F),
			burst(321, 0.754F, 20, 0.799F),
			burst(333, 0.755F, 20, 0.799F),
			burst(341, 0.601F, 17, 0.673F),
			burst(352, 0.643F, 18, 0.707F),
			burst(366, 0.603F, 17, 0.675F),
			burst(390, 0.769F, 20, 0.811F),
			burst(397, 0.524F, 15, 0.610F),
			burst(403, 0.581F, 16, 0.656F),
			burst(411, 0.795F, 20, 0.832F),
			burst(424, 0.512F, 15, 0.600F),
			burst(430, 0.748F, 19, 0.793F),
			burst(438, 0.446F, 14, 0.546F),
			burst(444, 0.626F, 17, 0.694F),
			burst(451, 1.000F, 24, 1.000F),
			burst(458, 0.815F, 21, 0.849F),
			burst(473, 0.677F, 18, 0.735F),
			burst(485, 0.610F, 17, 0.680F),
			burst(496, 0.454F, 14, 0.552F),
			burst(509, 0.621F, 17, 0.689F),
			burst(521, 0.481F, 15, 0.574F),
			burst(527, 0.624F, 17, 0.692F),
			burst(537, 0.571F, 16, 0.648F),
			burst(554, 0.454F, 14, 0.552F),
			burst(560, 0.578F, 16, 0.654F),
			burst(573, 0.579F, 16, 0.655F),
			burst(586, 0.660F, 18, 0.721F),
			burst(597, 0.618F, 17, 0.687F)
	};
	private static final float WORLD_REVEAL_TOTAL_BURST_WEIGHT = computeWorldRevealTotalBurstWeight();

	private static final Map<UUID, PlayerSceneState> PLAYER_STATES = new LinkedHashMap<>();
	private static final List<BlockPos> SHELL_DISSOLVE_ORDER = new ArrayList<>();
	private static final List<TerrainPlacement> WORLD_REVEAL_TERRAIN = new ArrayList<>();
	private static final List<BlockPos> WORLD_REVEAL_BARRIER_COLLISION = new ArrayList<>();
	private static final List<WorldRevealEpisode> WORLD_REVEAL_EPISODES = new ArrayList<>();
	private static final Map<Long, Integer> WORLD_REVEAL_SURFACE_Y = new LinkedHashMap<>();
	private static final Map<Long, BlockState> WORLD_REVEAL_TARGET_STATES = new HashMap<>();
	private static final Map<UUID, Vec3> WORLD_REVEAL_SAFE_TARGETS = new LinkedHashMap<>();
	private static final Set<Long> WORLD_REVEAL_REQUIRED_POSITIONS = new LinkedHashSet<>();
	private static final Set<Long> WORLD_REVEAL_DEFERRED_POSITIONS = new LinkedHashSet<>();
	private static final Set<Long> WORLD_REVEAL_REVEALED_POSITIONS = new HashSet<>();
	private static final Set<BlockPos> SHARED_BITCOIN_POSITIONS = new LinkedHashSet<>();
	private static boolean stateLoaded = false;
	private static boolean stateDirty = false;
	private static boolean bootstrapComplete = false;
	private static boolean active = false;
	private static boolean completed = false;
	private static boolean shellDissolving = false;
	private static boolean worldRevealActive = false;
	private static boolean worldRevealBarriersPlaced = false;
	private static boolean scenePrepared = false;
	private static int dissolveCursor = 0;
	private static long nextSharedReminderTick = Long.MIN_VALUE;
	private static long lastSharedLaunchProgressTick = Long.MIN_VALUE;
	private static long pendingSharedFinishTick = Long.MIN_VALUE;
	private static long worldRevealPhaseStartTick = Long.MIN_VALUE;
	private static long worldRevealCrackStartTick = Long.MIN_VALUE;
	private static long worldRevealMusicEndTick = Long.MIN_VALUE;
	private static long worldRevealDarknessClearTick = Long.MIN_VALUE;
	private static int sharedLaunchCollectedBitcoins = 0;
	private static int sharedLaunchRequiredBitcoins = 0;
	private static int sharedLaunchMilestoneCursor = 0;
	private static boolean sharedLaunchIntroTriggered = false;
	private static int startupWorldgenFrameIndex = Integer.MIN_VALUE;
	private static int worldRevealVisibleEpisodeCursor = 0;
	private static int worldRevealBurstCursor = 0;
	private static float worldRevealBurstWeightProgress = 0.0F;
	private static boolean worldRevealEarthquakeSoundStarted = false;
	private static WorldRevealPhase worldRevealPhase = WorldRevealPhase.NONE;
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
		worldRevealActive = false;
		worldRevealBarriersPlaced = false;
		scenePrepared = false;
		dissolveCursor = 0;
		nextSharedReminderTick = Long.MIN_VALUE;
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		worldRevealPhaseStartTick = Long.MIN_VALUE;
		worldRevealCrackStartTick = Long.MIN_VALUE;
		worldRevealMusicEndTick = Long.MIN_VALUE;
		worldRevealDarknessClearTick = Long.MIN_VALUE;
		sharedLaunchCollectedBitcoins = 0;
		sharedLaunchRequiredBitcoins = 0;
		sharedLaunchMilestoneCursor = 0;
		sharedLaunchIntroTriggered = false;
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
		worldRevealVisibleEpisodeCursor = 0;
		worldRevealBurstCursor = 0;
		worldRevealBurstWeightProgress = 0.0F;
		worldRevealEarthquakeSoundStarted = false;
		worldRevealPhase = WorldRevealPhase.NONE;
		sharedLaunchBossBar = null;
		difficultyBeforeSeasonStart = null;
		serverAnchor = null;
		PLAYER_STATES.clear();
		SHELL_DISSOLVE_ORDER.clear();
		WORLD_REVEAL_TERRAIN.clear();
		WORLD_REVEAL_BARRIER_COLLISION.clear();
		WORLD_REVEAL_EPISODES.clear();
		WORLD_REVEAL_SURFACE_Y.clear();
		WORLD_REVEAL_TARGET_STATES.clear();
		WORLD_REVEAL_SAFE_TARGETS.clear();
		WORLD_REVEAL_REQUIRED_POSITIONS.clear();
		WORLD_REVEAL_DEFERRED_POSITIONS.clear();
		WORLD_REVEAL_REVEALED_POSITIONS.clear();
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
		return player != null && PLAYER_STATES.containsKey(player.getUUID()) && (active || shellDissolving || worldRevealActive);
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
		return (active || worldRevealActive) && !completed;
	}

	public static float getStartupHudProgress() {
		if (sharedLaunchRequiredBitcoins <= 0) {
			return 0.0F;
		}
		return Mth.clamp((float) sharedLaunchCollectedBitcoins / (float) sharedLaunchRequiredBitcoins, 0.0F, 1.0F);
	}

	public static boolean shouldSuspendStabilitySystem() {
		return (active || worldRevealActive) && !completed;
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
			if (isLookingAtIntroOre(player, slot) && shouldFireIntroTargetReaction(state, nowTick)) {
				interruptAndFastForwardPlayerNarration(player, state, nowTick);
				fireIntroTargetReaction(level.getServer(), player, state, nowTick);
			}
			updateObservationBaseline(player, state);
			return;
		}
		if (isLookingAtIntroOre(player, slot)) {
			if (shouldFireIntroTargetReaction(state, nowTick)) {
				fireIntroTargetReaction(level.getServer(), player, state, nowTick);
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
		if (worldRevealActive) {
			forceCompleteWorldReveal(server);
		}
		applyStartDifficultyPolicy(server);
		if (completed && !active) {
			removeSceneShellNow(server.overworld());
		}
		if (!active && !worldRevealActive && server.overworld() != null) {
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
		if (worldRevealActive) {
			tickWorldReveal(server);
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
		} else if (worldRevealActive) {
			applyFreeState(player);
			if (worldRevealPhase == WorldRevealPhase.BLACKOUT_FADE
					|| worldRevealPhase == WorldRevealPhase.RELOCATE
					|| worldRevealPhase == WorldRevealPhase.SETTLE) {
				long remainingDarknessTicks = worldRevealDarknessClearTick == Long.MIN_VALUE || player.level() == null
						? 0L
						: worldRevealDarknessClearTick - player.level().getGameTime();
				if (remainingDarknessTicks > 0L) {
					applyWorldRevealDarkness(player, Math.max(80L, remainingDarknessTicks + 20L));
				}
			}
		} else if (shellDissolving) {
			applyFreeState(player);
		}
	}

	private static boolean onBeforeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (level == null || player == null || serverAnchor == null || !Level.OVERWORLD.equals(level.dimension())) {
			return true;
		}
		if (worldRevealActive) {
			BoxGeometry geometry = computeOuterBoxGeometry(resolveServerAnchor(level));
			if (isInsideFootprint(geometry, pos)) {
				player.displayClientMessage(Component.literal("Старт ещё завершает восстановление мира."), true);
				return false;
			}
			return true;
		}
		if (!active) {
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
		BlockPos anchor = resolveServerAnchor(overworld);
		BlockPos bootstrapAnchor = resolveBootstrapAnchor(overworld);
		if (anchor == null) {
			anchor = bootstrapAnchor;
			serverAnchor = anchor;
			stateDirty = true;
		}
		if (shouldRelocateBuriedBootstrapServer(anchor, bootstrapAnchor)
				&& isServerStructurePresent(overworld, anchor)) {
			ServerStructureBreakSystem.clearStructureSilently(overworld, anchor, Direction.Axis.Z);
			anchor = bootstrapAnchor;
			serverAnchor = anchor.immutable();
			stateDirty = true;
		}
		if (!isServerStructurePresent(overworld, anchor)) {
			BlockPos existingAnchor = discoverExistingServerAnchor(overworld, anchor, bootstrapAnchor);
			if (existingAnchor != null) {
				anchor = existingAnchor.immutable();
				serverAnchor = anchor;
				stateDirty = true;
			}
		}
		if (!isServerStructurePresent(overworld, anchor)) {
			ServerBlock.placeServerStructure(overworld, anchor, Direction.NORTH);
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
		if ((active || worldRevealActive) && !completed) {
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
		worldRevealActive = false;
		worldRevealBarriersPlaced = false;
		scenePrepared = false;
		dissolveCursor = 0;
		nextSharedReminderTick = Long.MIN_VALUE;
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		worldRevealPhaseStartTick = Long.MIN_VALUE;
		worldRevealCrackStartTick = Long.MIN_VALUE;
		worldRevealMusicEndTick = Long.MIN_VALUE;
		worldRevealDarknessClearTick = Long.MIN_VALUE;
		sharedLaunchCollectedBitcoins = 0;
		sharedLaunchRequiredBitcoins = 0;
		sharedLaunchMilestoneCursor = 0;
		sharedLaunchIntroTriggered = false;
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
		worldRevealVisibleEpisodeCursor = 0;
		worldRevealBurstCursor = 0;
		worldRevealBurstWeightProgress = 0.0F;
		worldRevealEarthquakeSoundStarted = false;
		worldRevealPhase = WorldRevealPhase.NONE;
		clearSharedLaunchBossBar();
		if (difficultyBeforeSeasonStart == null && overworld != null) {
			difficultyBeforeSeasonStart = overworld.getDifficulty();
		}
		PLAYER_STATES.clear();
		SHELL_DISSOLVE_ORDER.clear();
		WORLD_REVEAL_TERRAIN.clear();
		WORLD_REVEAL_BARRIER_COLLISION.clear();
		WORLD_REVEAL_EPISODES.clear();
		WORLD_REVEAL_SURFACE_Y.clear();
		WORLD_REVEAL_TARGET_STATES.clear();
		WORLD_REVEAL_SAFE_TARGETS.clear();
		WORLD_REVEAL_REQUIRED_POSITIONS.clear();
		WORLD_REVEAL_DEFERRED_POSITIONS.clear();
		WORLD_REVEAL_REVEALED_POSITIONS.clear();
		SHARED_BITCOIN_POSITIONS.clear();
		SeasonStartVoiceSystem.resetSceneState();
		stopWorldRevealEarthquakeSound(overworld);
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
		completed = false;
		shellDissolving = false;
		worldRevealActive = true;
		worldRevealBarriersPlaced = false;
		scenePrepared = false;
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
		dissolveCursor = 0;
		nextSharedReminderTick = Long.MIN_VALUE;
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		worldRevealPhaseStartTick = Long.MIN_VALUE;
		worldRevealCrackStartTick = Long.MIN_VALUE;
		worldRevealMusicEndTick = Long.MIN_VALUE;
		worldRevealDarknessClearTick = Long.MIN_VALUE;
		worldRevealVisibleEpisodeCursor = 0;
		worldRevealBurstCursor = 0;
		worldRevealBurstWeightProgress = 0.0F;
		worldRevealEarthquakeSoundStarted = false;
		worldRevealPhase = WorldRevealPhase.CRACKING;
		clearSharedLaunchBossBar();
		ServerLevel overworld = server.overworld();
		if (overworld != null) {
			stopWorldRevealEarthquakeSound(overworld);
			clearStartupWorldgenDisplay(overworld);
			clearSharedBitcoins(overworld, true);
			SHELL_DISSOLVE_ORDER.clear();
			WORLD_REVEAL_EPISODES.clear();
			WORLD_REVEAL_REQUIRED_POSITIONS.clear();
			WORLD_REVEAL_DEFERRED_POSITIONS.clear();
			WORLD_REVEAL_REVEALED_POSITIONS.clear();
			WORLD_REVEAL_SAFE_TARGETS.clear();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				applyFreeState(player);
			}
			SeasonStartVoiceSystem.fireTrigger(server, "season_finished", null);
			worldRevealCrackStartTick = overworld.getGameTime()
					+ resolveTriggerSequenceDurationTicks("season_finished")
					+ WORLD_REVEAL_CRACK_START_BUFFER_TICKS;
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
		GuidanceSnapshot snapshot = resolveGuidanceSnapshot(player);
		if (snapshot == null) {
			return;
		}
		boolean carryingBitcoin = hasBitcoin(player);
		boolean lookingAtServerStructure = isLookingAtServerStructure(player);
		boolean seesServer = snapshot.horizontalDistance <= GUIDANCE_SERVER_SIGHT_DISTANCE && lookingAtServerStructure;
		if (nowTick < state.guidanceNarrationGateTick) {
			if (!shouldFastForwardGuidanceNarration(snapshot, carryingBitcoin, seesServer)) {
				return;
			}
			interruptAndFastForwardPlayerNarration(player, state, nowTick);
		}
		if (nowTick < state.nextGuidanceTick) {
			return;
		}
		state.nextGuidanceTick = nowTick + GUIDANCE_EVALUATE_TICKS;
		if (lookingAtServerStructure && !snapshot.aligned) {
			snapshot = snapshot.withAlignmentLock();
		}
		boolean quietZone = carryingBitcoin && snapshot.horizontalDistance <= GUIDANCE_QUIET_DISTANCE;
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
		GuidanceInstruction instruction = resolveGuidanceInstruction(snapshot, distanceDelta, carryingBitcoin, seesServer, state, nowTick);
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
		boolean lookingAtOre = isLookingAtIntroOre(player, slot);
		if (nowTick < state.guidanceNarrationGateTick) {
			state.lastActivityTick = nowTick;
			if (lookingAtOre && shouldFireIntroTargetReaction(state, nowTick)) {
				interruptAndFastForwardPlayerNarration(player, state, nowTick);
				fireIntroTargetReaction(server, player, state, nowTick);
			}
			updateObservationBaseline(player, state);
			return;
		}

		double horizontalMoveSqr = horizontalDistanceSqr(player.position(), new Vec3(state.lastObservedX, player.getY(), state.lastObservedZ));
		double verticalMove = Math.abs(player.getY() - state.lastObservedY);
		float yawDelta = Math.abs(Mth.wrapDegrees(player.getYRot() - state.lastYaw));
		float pitchDelta = Math.abs(player.getXRot() - state.lastPitch);
		boolean moved = horizontalMoveSqr > INTRO_ACTIVITY_MOVE_SQR || verticalMove > 0.12D;
		boolean looked = yawDelta >= INTRO_ACTIVITY_YAW_DEGREES || pitchDelta >= INTRO_ACTIVITY_PITCH_DEGREES;

		if (moved || looked) {
			state.lastActivityTick = nowTick;
		}

		if (lookingAtOre && shouldFireIntroTargetReaction(state, nowTick)) {
			fireIntroTargetReaction(server, player, state, nowTick);
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
		SeasonStartVoiceSystem.clearPlayerChannel(player);
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

	private static void tickWorldReveal(MinecraftServer server) {
		ServerLevel overworld = server == null ? null : server.overworld();
		if (server == null || overworld == null) {
			return;
		}
		prepareWorldReveal(overworld);
		if (WORLD_REVEAL_SURFACE_Y.isEmpty()) {
			forceCompleteWorldReveal(server);
			return;
		}
		if (worldRevealPhaseStartTick == Long.MIN_VALUE) {
			worldRevealPhaseStartTick = overworld.getGameTime();
		}

		long nowTick = overworld.getGameTime();
		switch (worldRevealPhase) {
			case CRACKING -> tickWorldRevealCracking(server, overworld, nowTick);
			case BLACKOUT_FADE -> tickWorldRevealBlackoutFade(server, overworld, nowTick);
			case RELOCATE -> tickWorldRevealRelocate(server, overworld, nowTick);
			case SETTLE -> tickWorldRevealSettle(server, overworld, nowTick);
			default -> forceCompleteWorldReveal(server);
		}
	}

	private static void prepareWorldReveal(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		ensureWorldRevealSnapshotIndexes();
		if (WORLD_REVEAL_SURFACE_Y.isEmpty() || WORLD_REVEAL_TARGET_STATES.isEmpty()) {
			ensureSceneSnapshot(level);
		}
		if (shouldRebuildWorldRevealPlan()) {
			resetWorldRevealPlanState();
		}
		if (WORLD_REVEAL_EPISODES.isEmpty()) {
			prepareWorldRevealGrowthPlan(level);
		}
		if (worldRevealPhase == WorldRevealPhase.RELOCATE || worldRevealPhase == WorldRevealPhase.SETTLE) {
			refreshWorldRevealTargets(level);
		}
	}

	private static void ensureWorldRevealSnapshotIndexes() {
		if (serverAnchor == null || WORLD_REVEAL_TERRAIN.isEmpty()) {
			return;
		}
		if (!WORLD_REVEAL_TARGET_STATES.isEmpty() && !WORLD_REVEAL_SURFACE_Y.isEmpty()) {
			return;
		}
		rebuildWorldRevealSnapshotIndexesFromTerrain();
	}

	private static boolean shouldRebuildWorldRevealPlan() {
		return !WORLD_REVEAL_EPISODES.isEmpty()
				&& !WORLD_REVEAL_TARGET_STATES.isEmpty()
				&& !WORLD_REVEAL_REQUIRED_POSITIONS.containsAll(WORLD_REVEAL_TARGET_STATES.keySet());
	}

	private static void resetWorldRevealPlanState() {
		WORLD_REVEAL_EPISODES.clear();
		WORLD_REVEAL_REQUIRED_POSITIONS.clear();
		WORLD_REVEAL_DEFERRED_POSITIONS.clear();
		WORLD_REVEAL_REVEALED_POSITIONS.clear();
		worldRevealVisibleEpisodeCursor = 0;
		worldRevealBurstCursor = 0;
		worldRevealBurstWeightProgress = 0.0F;
		stateDirty = true;
	}

	private static void ensureSceneSnapshot(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		ensureWorldRevealSnapshotIndexes();
		if (!WORLD_REVEAL_TERRAIN.isEmpty() && !WORLD_REVEAL_TARGET_STATES.isEmpty() && !WORLD_REVEAL_SURFACE_Y.isEmpty()) {
			return;
		}
		if (isSceneShellAlreadyBuilt(level)) {
			buildSceneSnapshotFromGenerator(level);
			return;
		}
		captureSceneSnapshotFromWorld(level);
	}

	private static boolean isSceneShellAlreadyBuilt(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return false;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		BoxGeometry barrierGeometry = computeBarrierGeometry(resolveServerAnchor(level));
		return level.getBlockState(new BlockPos(outerGeometry.minX, outerGeometry.floorY, outerGeometry.minZ)).is(Blocks.BLACK_CONCRETE)
				|| level.getBlockState(new BlockPos(outerGeometry.minX, outerGeometry.roofY, outerGeometry.minZ)).is(Blocks.BLACK_CONCRETE)
				|| level.getBlockState(new BlockPos(barrierGeometry.minX, barrierGeometry.floorY, barrierGeometry.minZ)).is(Blocks.BARRIER);
	}

	private static void captureSceneSnapshotFromWorld(ServerLevel level) {
		captureSceneSnapshot(level, false);
	}

	private static void buildSceneSnapshotFromGenerator(ServerLevel level) {
		captureSceneSnapshot(level, true);
	}

	private static void captureSceneSnapshot(ServerLevel level, boolean generatorFallback) {
		BlockPos anchor = resolveServerAnchor(level);
		if (level == null || anchor == null) {
			return;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(anchor);
		BoxGeometry barrierGeometry = computeBarrierGeometry(anchor);
		WORLD_REVEAL_TERRAIN.clear();
		WORLD_REVEAL_BARRIER_COLLISION.clear();
		WORLD_REVEAL_SURFACE_Y.clear();
		WORLD_REVEAL_TARGET_STATES.clear();
		WORLD_REVEAL_SAFE_TARGETS.clear();

		ChunkGenerator generator = generatorFallback ? level.getChunkSource().getGenerator() : null;
		RandomState randomState = generatorFallback ? level.getChunkSource().randomState() : null;

		for (int x = outerGeometry.minX; x <= outerGeometry.maxX; x++) {
			for (int z = outerGeometry.minZ; z <= outerGeometry.maxZ; z++) {
				NoiseColumn column = generatorFallback ? generator.getBaseColumn(x, z, level, randomState) : null;
				int topSolidY = Integer.MIN_VALUE;
				int topFilledY = Integer.MIN_VALUE;
				boolean insideBarrier = x >= barrierGeometry.minX + 1
						&& x <= barrierGeometry.maxX - 1
						&& z >= barrierGeometry.minZ + 1
						&& z <= barrierGeometry.maxZ - 1;

				for (int y = outerGeometry.floorY; y <= outerGeometry.roofY; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (isServerStructureFootprint(pos)) {
						continue;
					}
					BlockState state = generatorFallback ? column.getBlock(y) : level.getBlockState(pos);
					if (shouldIgnoreSceneSnapshotState(pos, state, outerGeometry, barrierGeometry)) {
						continue;
					}
					if (state == null || state.isAir()) {
						continue;
					}
					WORLD_REVEAL_TERRAIN.add(new TerrainPlacement(pos.immutable(), state));
					WORLD_REVEAL_TARGET_STATES.put(pos.asLong(), state);
					topFilledY = y;
					if (insideBarrier && isWorldRevealCollisionState(state)) {
						WORLD_REVEAL_BARRIER_COLLISION.add(pos.immutable());
						topSolidY = y;
					}
				}

				if (insideBarrier) {
					int fallbackSurface = barrierGeometry.floorY;
					int surfaceY = topSolidY != Integer.MIN_VALUE
							? topSolidY
							: (topFilledY != Integer.MIN_VALUE ? topFilledY : fallbackSurface);
					WORLD_REVEAL_SURFACE_Y.put(surfaceColumnKey(x, z), surfaceY);
				}
			}
		}
	}

	private static void rebuildWorldRevealSnapshotIndexesFromTerrain() {
		if (serverAnchor == null || WORLD_REVEAL_TERRAIN.isEmpty()) {
			return;
		}
		BoxGeometry barrierGeometry = computeBarrierGeometry(serverAnchor);
		WORLD_REVEAL_BARRIER_COLLISION.clear();
		WORLD_REVEAL_SURFACE_Y.clear();
		WORLD_REVEAL_TARGET_STATES.clear();
		WORLD_REVEAL_SAFE_TARGETS.clear();
		Map<Long, Integer> topSolidByColumn = new HashMap<>();
		Map<Long, Integer> topFilledByColumn = new HashMap<>();
		for (TerrainPlacement placement : WORLD_REVEAL_TERRAIN) {
			if (placement == null || placement.pos() == null || placement.state() == null || placement.state().isAir()) {
				continue;
			}
			BlockPos pos = placement.pos();
			BlockState state = placement.state();
			WORLD_REVEAL_TARGET_STATES.put(pos.asLong(), state);
			boolean insideBarrier = pos.getX() >= barrierGeometry.minX + 1
					&& pos.getX() <= barrierGeometry.maxX - 1
					&& pos.getZ() >= barrierGeometry.minZ + 1
					&& pos.getZ() <= barrierGeometry.maxZ - 1;
			if (!insideBarrier) {
				continue;
			}
			long columnKey = surfaceColumnKey(pos.getX(), pos.getZ());
			topFilledByColumn.merge(columnKey, pos.getY(), Math::max);
			if (isWorldRevealCollisionState(state)) {
				WORLD_REVEAL_BARRIER_COLLISION.add(pos.immutable());
				topSolidByColumn.merge(columnKey, pos.getY(), Math::max);
			}
		}
		for (int x = barrierGeometry.minX + 1; x <= barrierGeometry.maxX - 1; x++) {
			for (int z = barrierGeometry.minZ + 1; z <= barrierGeometry.maxZ - 1; z++) {
				long columnKey = surfaceColumnKey(x, z);
				int topSolidY = topSolidByColumn.getOrDefault(columnKey, Integer.MIN_VALUE);
				int topFilledY = topFilledByColumn.getOrDefault(columnKey, Integer.MIN_VALUE);
				int fallbackSurface = barrierGeometry.floorY;
				int surfaceY = topSolidY != Integer.MIN_VALUE
						? topSolidY
						: (topFilledY != Integer.MIN_VALUE ? topFilledY : fallbackSurface);
				WORLD_REVEAL_SURFACE_Y.put(columnKey, surfaceY);
			}
		}
	}

	private static boolean shouldIgnoreSceneSnapshotState(BlockPos pos, BlockState state, BoxGeometry outerGeometry, BoxGeometry barrierGeometry) {
		if (pos == null || state == null) {
			return false;
		}
		boolean blackShell = state.is(Blocks.BLACK_CONCRETE)
				&& outerGeometry != null
				&& (pos.getY() == outerGeometry.floorY
						|| pos.getY() == outerGeometry.roofY
						|| pos.getX() == outerGeometry.minX
						|| pos.getX() == outerGeometry.maxX
						|| pos.getZ() == outerGeometry.minZ
						|| pos.getZ() == outerGeometry.maxZ);
		if (blackShell) {
			return true;
		}
		if (!state.is(Blocks.BARRIER) || barrierGeometry == null) {
			return false;
		}
		boolean barrierFloor = pos.getY() <= barrierGeometry.floorY && pos.getY() >= barrierGeometry.floorY - (BARRIER_FLOOR_DEPTH - 1)
				&& pos.getX() >= barrierGeometry.minX && pos.getX() <= barrierGeometry.maxX
				&& pos.getZ() >= barrierGeometry.minZ && pos.getZ() <= barrierGeometry.maxZ;
		boolean barrierWallOrRoof = pos.getY() >= barrierGeometry.floorY + 1 && pos.getY() <= barrierGeometry.roofY
				&& ((pos.getX() == barrierGeometry.minX || pos.getX() == barrierGeometry.maxX
				|| pos.getZ() == barrierGeometry.minZ || pos.getZ() == barrierGeometry.maxZ)
				|| pos.getY() == barrierGeometry.roofY)
				&& pos.getX() >= barrierGeometry.minX && pos.getX() <= barrierGeometry.maxX
				&& pos.getZ() >= barrierGeometry.minZ && pos.getZ() <= barrierGeometry.maxZ;
		return barrierFloor || barrierWallOrRoof;
	}

	private static boolean isWorldRevealCollisionState(BlockState state) {
		return state != null && !state.isAir() && (state.blocksMotion() || !state.getFluidState().isEmpty());
	}

	private static void prepareWorldRevealGrowthPlan(ServerLevel level) {
		if (level == null || serverAnchor == null || !WORLD_REVEAL_EPISODES.isEmpty()) {
			return;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		BoxGeometry barrierGeometry = computeBarrierGeometry(resolveServerAnchor(level));
		Set<Long> shellCandidates = new LinkedHashSet<>();
		for (BlockPos pos : collectBlackShellBlocks(outerGeometry)) {
			if (!isServerStructureFootprint(pos)) {
				shellCandidates.add(pos.asLong());
			}
		}
		for (BlockPos pos : collectBarrierShellBlocks(barrierGeometry)) {
			if (!isServerStructureFootprint(pos)) {
				shellCandidates.add(pos.asLong());
			}
		}
		if (shellCandidates.isEmpty() && WORLD_REVEAL_TARGET_STATES.isEmpty()) {
			return;
		}
		WORLD_REVEAL_REQUIRED_POSITIONS.clear();
		WORLD_REVEAL_DEFERRED_POSITIONS.clear();
		for (long key : shellCandidates) {
			BlockPos pos = BlockPos.of(key);
			if (!isServerStructureFootprint(pos)) {
				WORLD_REVEAL_REQUIRED_POSITIONS.add(key);
			}
		}
		for (Map.Entry<Long, BlockState> targetEntry : WORLD_REVEAL_TARGET_STATES.entrySet()) {
			BlockState targetState = targetEntry.getValue();
			if (targetState == null || targetState.isAir()) {
				continue;
			}
			BlockPos pos = BlockPos.of(targetEntry.getKey());
			if (!isServerStructureFootprint(pos)) {
				WORLD_REVEAL_REQUIRED_POSITIONS.add(targetEntry.getKey());
			}
		}

		List<BlockPos> terrainAnchors = collectWorldRevealTerrainAnchors(randomForWorldReveal(level), 56);
		if (terrainAnchors.isEmpty()) {
			terrainAnchors.add(resolveServerAnchor(level));
		}

		Random random = randomForWorldReveal(level);
		Map<Integer, LinkedHashSet<Long>> revealByRound = new TreeMap<>();
		Map<Integer, List<Vec3>> particlesByRound = new TreeMap<>();
		List<WorldRevealSeed> seeds = new ArrayList<>();
		for (int face = 0; face < 6; face++) {
			int primaryCrackCount = switch (face) {
				case 4 -> 6;
				case 5 -> 4;
				default -> 4;
			};
			for (int primaryIndex = 0; primaryIndex < primaryCrackCount; primaryIndex++) {
				BlockPos start = worldRevealShellPointForFace(outerGeometry, face, random);
				BlockPos pivot = createWorldRevealFacePivot(outerGeometry, face, random);
				for (int attempt = 0; attempt < 5 && start.distManhattan(pivot) < 6; attempt++) {
					pivot = createWorldRevealFacePivot(outerGeometry, face, random);
				}
				BlockPos entry = createWorldRevealFaceEntry(outerGeometry, pivot, face, random);
				BlockPos target = terrainAnchors.get(random.nextInt(terrainAnchors.size()));
				int startRound = face * 8 + primaryIndex * 2 + random.nextInt(4);
				WorldRevealFaceConstraint faceConstraint = new WorldRevealFaceConstraint(faceAxis(face), facePlaneCenterCoordinate(outerGeometry, face));
				List<Vec3> mainPolyline = new ArrayList<>();
				appendWorldRevealLightningSegment(
						mainPolyline,
						blockCenter(start),
						blockCenter(pivot),
						outerGeometry,
						faceConstraint,
						random,
						5,
						WORLD_REVEAL_PRIMARY_CRACK_DISPLACEMENT
				);
				appendWorldRevealLightningSegment(
						mainPolyline,
						mainPolyline.get(mainPolyline.size() - 1),
						blockCenter(entry),
						outerGeometry,
						null,
						random,
						4,
						WORLD_REVEAL_PRIMARY_CRACK_DISPLACEMENT * 0.52D
				);
				appendWorldRevealLightningSegment(
						mainPolyline,
						mainPolyline.get(mainPolyline.size() - 1),
						blockCenter(target),
						outerGeometry,
						null,
						random,
						5,
						WORLD_REVEAL_PRIMARY_CRACK_DISPLACEMENT * 0.70D
				);
				stampWorldRevealCrack(
						revealByRound,
						particlesByRound,
						seeds,
						mainPolyline,
						startRound,
						54 + random.nextInt(24),
						(face == 4 ? 2.45D : 1.95D) + random.nextDouble() * 0.75D,
						shellCandidates,
						WORLD_REVEAL_TARGET_STATES
				);

				int branchCount = 2 + random.nextInt(3);
				for (int branchIndex = 0; branchIndex < branchCount; branchIndex++) {
					if (mainPolyline.size() < 4) {
						break;
					}
					int minIndex = Math.max(1, mainPolyline.size() / 4);
					int maxIndex = Math.max(minIndex + 1, (mainPolyline.size() * 4) / 5);
					int branchSourceIndex = nextIntInclusive(random, minIndex, maxIndex);
					Vec3 branchSource = mainPolyline.get(branchSourceIndex);
					BlockPos branchTarget = terrainAnchors.get(random.nextInt(terrainAnchors.size()));
					List<Vec3> branchPolyline = new ArrayList<>();
					appendWorldRevealLightningSegment(
							branchPolyline,
							branchSource,
							blockCenter(branchTarget),
							outerGeometry,
							null,
							random,
							4,
							WORLD_REVEAL_BRANCH_CRACK_DISPLACEMENT
					);
					stampWorldRevealCrack(
							revealByRound,
							particlesByRound,
							seeds,
							branchPolyline,
							startRound + 8 + random.nextInt(14),
							24 + random.nextInt(18),
							1.55D + random.nextDouble() * 0.45D,
							shellCandidates,
							WORLD_REVEAL_TARGET_STATES
					);
				}
			}
		}
		backfillWorldRevealCoverage(revealByRound, particlesByRound, seeds, shellCandidates, WORLD_REVEAL_TARGET_STATES, WORLD_REVEAL_REQUIRED_POSITIONS, random);

		WORLD_REVEAL_EPISODES.clear();
		for (Map.Entry<Integer, LinkedHashSet<Long>> entry : revealByRound.entrySet()) {
			LinkedHashSet<Long> roundPositions = entry.getValue();
			List<Vec3> crackPoints = particlesByRound.get(entry.getKey());
			if ((roundPositions == null || roundPositions.isEmpty()) && (crackPoints == null || crackPoints.isEmpty())) {
				continue;
			}
			List<BlockPos> revealPositions = new ArrayList<>(roundPositions == null ? 0 : roundPositions.size());
			if (roundPositions != null) {
				for (long key : roundPositions) {
					revealPositions.add(BlockPos.of(key));
				}
			}
			List<Vec3> particlePoints = crackPoints == null ? List.of() : new ArrayList<>(crackPoints);
			int chunkCount = Math.max(
					1,
					Math.max(
							(int) Math.ceil(revealPositions.size() / (double) WORLD_REVEAL_EPISODE_MAX_REVEAL_POSITIONS),
							(int) Math.ceil(particlePoints.size() / (double) WORLD_REVEAL_EPISODE_MAX_PARTICLE_POINTS)
					)
			);
			for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
				int revealStart = (int) Math.floor(revealPositions.size() * (double) chunkIndex / chunkCount);
				int revealEnd = (int) Math.floor(revealPositions.size() * (double) (chunkIndex + 1) / chunkCount);
				int particleStart = (int) Math.floor(particlePoints.size() * (double) chunkIndex / chunkCount);
				int particleEnd = (int) Math.floor(particlePoints.size() * (double) (chunkIndex + 1) / chunkCount);
				List<BlockPos> revealChunk = revealStart >= revealEnd
						? List.of()
						: new ArrayList<>(revealPositions.subList(revealStart, revealEnd));
				List<Vec3> particleChunk = particleStart >= particleEnd
						? List.of()
						: new ArrayList<>(particlePoints.subList(particleStart, particleEnd));
				if (!revealChunk.isEmpty() || !particleChunk.isEmpty()) {
					WORLD_REVEAL_EPISODES.add(new WorldRevealEpisode(revealChunk, particleChunk));
				}
			}
		}
	}

	private static Random randomForWorldReveal(ServerLevel level) {
		long seed = level == null || serverAnchor == null
				? 0x6C6732777265616CL
				: level.getSeed() ^ serverAnchor.asLong() ^ 0x6C6732777265616CL;
		return new Random(seed);
	}

	private static List<BlockPos> collectWorldRevealTerrainAnchors(Random random, int limit) {
		List<BlockPos> anchors = new ArrayList<>();
		if (random == null || limit <= 0) {
			return anchors;
		}
		List<BlockPos> surfaceCandidates = new ArrayList<>();
		for (Map.Entry<Long, Integer> entry : WORLD_REVEAL_SURFACE_Y.entrySet()) {
			BlockPos column = BlockPos.of(entry.getKey());
			BlockPos anchor = resolveWorldRevealSurfaceAnchor(column.getX(), column.getZ(), entry.getValue());
			if (anchor != null) {
				surfaceCandidates.add(anchor);
			}
		}
		if (surfaceCandidates.isEmpty()) {
			return anchors;
		}
		int seen = 0;
		for (BlockPos candidate : surfaceCandidates) {
			if (candidate == null) {
				continue;
			}
			seen++;
			if (anchors.size() < limit) {
				anchors.add(candidate.immutable());
				continue;
			}
			int replacementIndex = random.nextInt(seen);
			if (replacementIndex < limit) {
				anchors.set(replacementIndex, candidate.immutable());
			}
		}
		return anchors;
	}

	private static BlockPos resolveWorldRevealSurfaceAnchor(int x, int z, int surfaceY) {
		if (serverAnchor == null) {
			return null;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(serverAnchor);
		int minY = outerGeometry.floorY;
		int maxY = outerGeometry.roofY;
		for (int y = maxY; y >= minY; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = WORLD_REVEAL_TARGET_STATES.get(pos.asLong());
			if (state == null || state.isAir() || isServerStructureFootprint(pos)) {
				continue;
			}
			if (hasWorldRevealExposedFace(pos)) {
				return pos.immutable();
			}
		}
		for (int y = Mth.clamp(surfaceY, minY, maxY); y >= minY; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = WORLD_REVEAL_TARGET_STATES.get(pos.asLong());
			if (state != null && !state.isAir() && !isServerStructureFootprint(pos)) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static boolean hasWorldRevealExposedFace(BlockPos pos) {
		if (pos == null) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			BlockPos adjacent = pos.relative(direction);
			BlockState adjacentState = WORLD_REVEAL_TARGET_STATES.get(adjacent.asLong());
			if (adjacentState == null || adjacentState.isAir()) {
				return true;
			}
		}
		return false;
	}

	private static void stampWorldRevealCrack(
			Map<Integer, LinkedHashSet<Long>> revealByRound,
			Map<Integer, List<Vec3>> particlesByRound,
			List<WorldRevealSeed> seeds,
			List<Vec3> polyline,
			int startRound,
			int roundSpan,
			double baseFillRadius,
			Set<Long> shellCandidates,
			Map<Long, BlockState> terrainTargets
	) {
		if (revealByRound == null
				|| particlesByRound == null
				|| seeds == null
				|| polyline == null
				|| polyline.size() < 2
				|| shellCandidates == null
				|| terrainTargets == null) {
			return;
		}
		List<Vec3> samples = sampleWorldRevealPolyline(polyline, WORLD_REVEAL_CRACK_SAMPLE_STEP);
		if (samples.isEmpty()) {
			return;
		}
		for (int sampleIndex = 0; sampleIndex < samples.size(); sampleIndex++) {
			Vec3 sample = samples.get(sampleIndex);
			double progress = samples.size() <= 1 ? 1.0D : (double) sampleIndex / (double) (samples.size() - 1);
			int round = startRound + (int) Math.floor(progress * roundSpan);
			particlesByRound.computeIfAbsent(round, ignored -> new ArrayList<>()).add(sample);
			BlockPos linePos = BlockPos.containing(sample);
			addWorldRevealRevealCell(revealByRound, round, linePos, shellCandidates, terrainTargets);
			double crackRadius = progress < 0.05D
					? 0.0D
					: Math.min(2.15D, 0.42D + baseFillRadius * 0.34D + progress * 0.88D);
			double fillRadius = progress < 0.08D
					? 0.0D
					: Math.min(
							WORLD_REVEAL_MAX_TERRAIN_GROWTH_RADIUS,
							baseFillRadius * (0.45D + progress * 1.15D)
					);
			seeds.add(new WorldRevealSeed(sample, round, crackRadius, fillRadius));
			if (crackRadius > 0.01D || fillRadius > 0.01D) {
				growWorldRevealCellsAroundSample(revealByRound, particlesByRound, round, sample, crackRadius, fillRadius, shellCandidates, terrainTargets);
			}
		}
	}

	private static void addWorldRevealRevealCell(
			Map<Integer, LinkedHashSet<Long>> revealByRound,
			int round,
			BlockPos pos,
			Set<Long> shellCandidates,
			Map<Long, BlockState> terrainTargets
	) {
		if (revealByRound == null || pos == null || shellCandidates == null || terrainTargets == null || isServerStructureFootprint(pos)) {
			return;
		}
		long key = pos.asLong();
		BlockState targetState = terrainTargets.get(key);
		if (!shellCandidates.contains(key) && (targetState == null || targetState.isAir())) {
			return;
		}
		revealByRound.computeIfAbsent(round, ignored -> new LinkedHashSet<>()).add(key);
	}

	private static void growWorldRevealCellsAroundSample(
			Map<Integer, LinkedHashSet<Long>> revealByRound,
			Map<Integer, List<Vec3>> particlesByRound,
			int round,
			Vec3 sample,
			double crackRadius,
			double fillRadius,
			Set<Long> shellCandidates,
			Map<Long, BlockState> terrainTargets
	) {
		if (revealByRound == null
				|| particlesByRound == null
				|| sample == null
				|| terrainTargets == null
				|| (crackRadius <= 0.0D && fillRadius <= 0.0D)) {
			return;
		}
		BlockPos origin = BlockPos.containing(sample);
		double maxRadius = Math.max(crackRadius, fillRadius);
		int radiusBlocks = Mth.ceil(maxRadius + 0.35D);
		for (int dx = -radiusBlocks; dx <= radiusBlocks; dx++) {
			for (int dy = -radiusBlocks; dy <= radiusBlocks; dy++) {
				for (int dz = -radiusBlocks; dz <= radiusBlocks; dz++) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (isServerStructureFootprint(candidate)) {
						continue;
					}
					long key = candidate.asLong();
					boolean shellCandidate = shellCandidates != null && shellCandidates.contains(key);
					BlockState targetState = terrainTargets.get(key);
					boolean terrainCandidate = targetState != null && !targetState.isAir();
					if (!shellCandidate && !terrainCandidate) {
						continue;
					}
					Vec3 candidateCenter = blockCenter(candidate);
					double distance = candidateCenter.distanceTo(sample);
					if (shellCandidate && crackRadius > 0.0D && distance <= crackRadius + 0.26D) {
						int delay = (int) Math.floor(distance * 1.55D + Math.abs(dy) * 0.18D);
						addWorldRevealRevealCell(revealByRound, round + delay, candidate, shellCandidates, terrainTargets);
						if (((dx + dz) & 1) == 0) {
							particlesByRound.computeIfAbsent(round + delay, ignored -> new ArrayList<>()).add(candidateCenter);
						}
					}
					if (terrainCandidate && fillRadius > 0.0D && distance <= fillRadius + 0.42D) {
						int delay = (int) Math.floor(distance * 2.05D + Math.max(0, dy) * 0.25D);
						addWorldRevealRevealCell(revealByRound, round + delay, candidate, shellCandidates, terrainTargets);
						if (((dx + dy + dz) & 1) == 0 && distance <= fillRadius * 0.72D) {
							particlesByRound.computeIfAbsent(round + delay, ignored -> new ArrayList<>()).add(candidateCenter);
						}
					}
				}
			}
		}
	}

	private static void backfillWorldRevealCoverage(
			Map<Integer, LinkedHashSet<Long>> revealByRound,
			Map<Integer, List<Vec3>> particlesByRound,
			List<WorldRevealSeed> seeds,
			Set<Long> shellCandidates,
			Map<Long, BlockState> terrainTargets,
			Set<Long> requiredPositions,
			Random random
	) {
		if (revealByRound == null
				|| particlesByRound == null
				|| terrainTargets == null
				|| requiredPositions == null
				|| requiredPositions.isEmpty()) {
			return;
		}
		if (seeds == null || seeds.isEmpty()) {
			BlockPos anchor = serverAnchor == null ? BlockPos.ZERO : serverAnchor;
			seeds = new ArrayList<>(List.of(new WorldRevealSeed(blockCenter(anchor), 0, 0.9D, 1.2D)));
		}
		Set<Long> scheduled = new HashSet<>();
		for (LinkedHashSet<Long> positions : revealByRound.values()) {
			if (positions != null) {
				scheduled.addAll(positions);
			}
		}
		for (long key : requiredPositions) {
			if (scheduled.contains(key)) {
				continue;
			}
			BlockPos pos = BlockPos.of(key);
			if (isServerStructureFootprint(pos)) {
				continue;
			}
			BlockState targetState = terrainTargets.get(key);
			boolean terrain = targetState != null && !targetState.isAir();
			WorldRevealSeed seed = findNearestWorldRevealSeed(seeds, pos, terrain);
			if (seed == null) {
				continue;
			}
			Vec3 center = blockCenter(pos);
			double distance = center.distanceTo(seed.point());
			double influenceRadius = terrain
					? Math.max(0.68D, seed.terrainRadius())
					: Math.max(0.38D, seed.crackRadius());
			double uncovered = Math.max(0.0D, distance - influenceRadius);
			int jitter = random == null ? 0 : random.nextInt(terrain ? 4 : 3);
			int delay = terrain
					? (int) Math.floor(uncovered * 3.65D + Math.max(0.0D, center.y - seed.point().y) * 0.35D)
					: (int) Math.floor(uncovered * 2.75D + Math.abs(center.y - seed.point().y) * 0.12D);
			int round = seed.round() + jitter + delay;
			addWorldRevealRevealCell(revealByRound, round, pos, shellCandidates, terrainTargets);
			scheduled.add(key);
			if (((pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13) & (terrain ? 1 : 3)) == 0) {
				particlesByRound.computeIfAbsent(round, ignored -> new ArrayList<>()).add(center);
			}
		}
	}

	private static WorldRevealSeed findNearestWorldRevealSeed(List<WorldRevealSeed> seeds, BlockPos pos, boolean terrain) {
		if (seeds == null || seeds.isEmpty() || pos == null) {
			return null;
		}
		Vec3 center = blockCenter(pos);
		WorldRevealSeed best = null;
		double bestScore = Double.MAX_VALUE;
		for (WorldRevealSeed seed : seeds) {
			if (seed == null) {
				continue;
			}
			double effectiveRadius = terrain ? Math.max(0.68D, seed.terrainRadius()) : Math.max(0.38D, seed.crackRadius());
			double score = Math.max(0.0D, center.distanceTo(seed.point()) - effectiveRadius) + seed.round() * 0.038D;
			if (score < bestScore) {
				bestScore = score;
				best = seed;
			}
		}
		return best;
	}

	private static List<Vec3> sampleWorldRevealPolyline(List<Vec3> polyline, double stepSize) {
		List<Vec3> samples = new ArrayList<>();
		if (polyline == null || polyline.isEmpty()) {
			return samples;
		}
		samples.add(polyline.get(0));
		for (int index = 1; index < polyline.size(); index++) {
			Vec3 from = polyline.get(index - 1);
			Vec3 to = polyline.get(index);
			Vec3 delta = to.subtract(from);
			double length = delta.length();
			if (length <= 1.0E-6D) {
				continue;
			}
			int steps = Math.max(1, (int) Math.ceil(length / Math.max(0.1D, stepSize)));
			for (int step = 1; step <= steps; step++) {
				double progress = (double) step / (double) steps;
				samples.add(from.add(delta.scale(progress)));
			}
		}
		return samples;
	}

	private static void appendWorldRevealLightningSegment(
			List<Vec3> polyline,
			Vec3 start,
			Vec3 end,
			BoxGeometry geometry,
			WorldRevealFaceConstraint faceConstraint,
			Random random,
			int depth,
			double displacement
	) {
		if (polyline == null || start == null || end == null || geometry == null || random == null) {
			return;
		}
		List<Vec3> segment = new ArrayList<>();
		segment.add(clampWorldRevealPoint(start, geometry, faceConstraint));
		subdivideWorldRevealLightning(
				segment,
				clampWorldRevealPoint(start, geometry, faceConstraint),
				clampWorldRevealPoint(end, geometry, faceConstraint),
				geometry,
				faceConstraint,
				random,
				depth,
				displacement
		);
		if (polyline.isEmpty()) {
			polyline.addAll(segment);
			return;
		}
		for (int index = 1; index < segment.size(); index++) {
			Vec3 point = segment.get(index);
			if (polyline.get(polyline.size() - 1).distanceToSqr(point) > 1.0E-6D) {
				polyline.add(point);
			}
		}
	}

	private static void subdivideWorldRevealLightning(
			List<Vec3> out,
			Vec3 start,
			Vec3 end,
			BoxGeometry geometry,
			WorldRevealFaceConstraint faceConstraint,
			Random random,
			int depth,
			double displacement
	) {
		if (out == null || start == null || end == null || geometry == null || random == null) {
			return;
		}
		if (depth <= 0 || start.distanceTo(end) <= 1.15D) {
			out.add(clampWorldRevealPoint(end, geometry, faceConstraint));
			return;
		}
		Vec3 midpoint = start.add(end).scale(0.5D);
		Vec3 offsetDirection = faceConstraint == null
				? randomPerpendicularDirection(end.subtract(start), random)
				: randomFaceOffsetDirection(end.subtract(start), faceConstraint.axis(), random);
		double offsetScale = displacement * (0.6D + random.nextDouble() * 0.75D);
		Vec3 offsetMidpoint = midpoint.add(offsetDirection.scale(offsetScale));
		Vec3 clampedMidpoint = clampWorldRevealPoint(offsetMidpoint, geometry, faceConstraint);
		subdivideWorldRevealLightning(out, start, clampedMidpoint, geometry, faceConstraint, random, depth - 1, displacement * 0.56D);
		subdivideWorldRevealLightning(out, clampedMidpoint, end, geometry, faceConstraint, random, depth - 1, displacement * 0.56D);
	}

	private static Vec3 randomPerpendicularDirection(Vec3 segment, Random random) {
		Vec3 direction = normalizeOrFallback(segment, new Vec3(1.0D, 0.0D, 0.0D));
		Vec3 randomSeed = normalizeOrFallback(
				new Vec3(random.nextDouble() - 0.5D, random.nextDouble() - 0.5D, random.nextDouble() - 0.5D),
				new Vec3(0.0D, 1.0D, 0.0D)
		);
		Vec3 perpendicularA = direction.cross(randomSeed);
		if (perpendicularA.lengthSqr() <= 1.0E-6D) {
			perpendicularA = direction.cross(Math.abs(direction.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D));
		}
		perpendicularA = normalizeOrFallback(perpendicularA, new Vec3(0.0D, 1.0D, 0.0D));
		Vec3 perpendicularB = normalizeOrFallback(direction.cross(perpendicularA), new Vec3(0.0D, 0.0D, 1.0D));
		double angle = random.nextDouble() * (Math.PI * 2.0D);
		return normalizeOrFallback(
				perpendicularA.scale(Math.cos(angle)).add(perpendicularB.scale(Math.sin(angle))),
				perpendicularA
		);
	}

	private static Vec3 randomFaceOffsetDirection(Vec3 segment, Direction.Axis axis, Random random) {
		Vec3 planar = switch (axis) {
			case X -> new Vec3(0.0D, segment.y, segment.z);
			case Y -> new Vec3(segment.x, 0.0D, segment.z);
			case Z -> new Vec3(segment.x, segment.y, 0.0D);
		};
		Vec3 tangent = normalizeOrFallback(planar, fallbackPlanarDirection(axis));
		Vec3 perpendicular = switch (axis) {
			case X -> new Vec3(0.0D, -tangent.z, tangent.y);
			case Y -> new Vec3(-tangent.z, 0.0D, tangent.x);
			case Z -> new Vec3(-tangent.y, tangent.x, 0.0D);
		};
		perpendicular = normalizeOrFallback(perpendicular, fallbackPlanarPerpendicular(axis));
		double along = (random.nextDouble() - 0.5D) * 0.45D;
		double across = random.nextBoolean() ? 1.0D : -1.0D;
		return normalizeOrFallback(perpendicular.scale(across).add(tangent.scale(along)), perpendicular);
	}

	private static Vec3 fallbackPlanarDirection(Direction.Axis axis) {
		return switch (axis) {
			case X -> new Vec3(0.0D, 1.0D, 0.0D);
			case Y -> new Vec3(1.0D, 0.0D, 0.0D);
			case Z -> new Vec3(1.0D, 0.0D, 0.0D);
		};
	}

	private static Vec3 fallbackPlanarPerpendicular(Direction.Axis axis) {
		return switch (axis) {
			case X -> new Vec3(0.0D, 0.0D, 1.0D);
			case Y -> new Vec3(0.0D, 0.0D, 1.0D);
			case Z -> new Vec3(0.0D, 1.0D, 0.0D);
		};
	}

	private static Vec3 normalizeOrFallback(Vec3 vector, Vec3 fallback) {
		if (vector != null && vector.lengthSqr() > 1.0E-6D) {
			return vector.normalize();
		}
		return fallback == null || fallback.lengthSqr() <= 1.0E-6D ? Vec3.ZERO : fallback.normalize();
	}

	private static Vec3 clampWorldRevealPoint(Vec3 point, BoxGeometry geometry, WorldRevealFaceConstraint faceConstraint) {
		if (point == null || geometry == null) {
			return Vec3.ZERO;
		}
		double minX = geometry.minX + 0.15D;
		double maxX = geometry.maxX + 0.85D;
		double minY = geometry.floorY + 0.15D;
		double maxY = geometry.roofY + 0.85D;
		double minZ = geometry.minZ + 0.15D;
		double maxZ = geometry.maxZ + 0.85D;
		double x = Mth.clamp(point.x, minX, maxX);
		double y = Mth.clamp(point.y, minY, maxY);
		double z = Mth.clamp(point.z, minZ, maxZ);
		if (faceConstraint != null) {
			switch (faceConstraint.axis()) {
				case X -> x = faceConstraint.value();
				case Y -> y = faceConstraint.value();
				case Z -> z = faceConstraint.value();
			}
		}
		return new Vec3(x, y, z);
	}

	private static BlockPos clampToGeometry(BlockPos pos, BoxGeometry geometry) {
		if (pos == null || geometry == null) {
			return BlockPos.ZERO;
		}
		return new BlockPos(
				Mth.clamp(pos.getX(), geometry.minX, geometry.maxX),
				Mth.clamp(pos.getY(), geometry.floorY, geometry.roofY),
				Mth.clamp(pos.getZ(), geometry.minZ, geometry.maxZ)
		);
	}

	private static BlockPos createWorldRevealFacePivot(BoxGeometry geometry, int face, Random random) {
		if (geometry == null || random == null) {
			return BlockPos.ZERO;
		}
		int centerX = (geometry.minX + geometry.maxX) / 2;
		int centerY = (geometry.floorY + geometry.roofY) / 2;
		int centerZ = (geometry.minZ + geometry.maxZ) / 2;
		int spreadX = Math.max(3, (geometry.maxX - geometry.minX) / 3);
		int spreadY = Math.max(3, (geometry.roofY - geometry.floorY) / 3);
		int spreadZ = Math.max(3, (geometry.maxZ - geometry.minZ) / 3);
		return switch (face) {
			case 0 -> new BlockPos(
					geometry.minX,
					nextIntInclusive(random, centerY - spreadY, centerY + spreadY),
					nextIntInclusive(random, centerZ - spreadZ, centerZ + spreadZ)
			);
			case 1 -> new BlockPos(
					geometry.maxX,
					nextIntInclusive(random, centerY - spreadY, centerY + spreadY),
					nextIntInclusive(random, centerZ - spreadZ, centerZ + spreadZ)
			);
			case 2 -> new BlockPos(
					nextIntInclusive(random, centerX - spreadX, centerX + spreadX),
					nextIntInclusive(random, centerY - spreadY, centerY + spreadY),
					geometry.minZ
			);
			case 3 -> new BlockPos(
					nextIntInclusive(random, centerX - spreadX, centerX + spreadX),
					nextIntInclusive(random, centerY - spreadY, centerY + spreadY),
					geometry.maxZ
			);
			case 4 -> new BlockPos(
					nextIntInclusive(random, centerX - spreadX, centerX + spreadX),
					geometry.floorY,
					nextIntInclusive(random, centerZ - spreadZ, centerZ + spreadZ)
			);
			default -> new BlockPos(
					nextIntInclusive(random, centerX - spreadX, centerX + spreadX),
					geometry.roofY,
					nextIntInclusive(random, centerZ - spreadZ, centerZ + spreadZ)
			);
		};
	}

	private static BlockPos createWorldRevealFaceEntry(BoxGeometry geometry, BlockPos pivot, int face, Random random) {
		if (geometry == null || pivot == null) {
			return BlockPos.ZERO;
		}
		Direction inward = switch (face) {
			case 0 -> Direction.EAST;
			case 1 -> Direction.WEST;
			case 2 -> Direction.SOUTH;
			case 3 -> Direction.NORTH;
			case 4 -> Direction.UP;
			default -> Direction.DOWN;
		};
		int depth = face >= 4 ? 4 + (random == null ? 0 : random.nextInt(4)) : 5 + (random == null ? 0 : random.nextInt(6));
		BlockPos current = pivot;
		for (int step = 0; step < depth; step++) {
			current = clampToGeometry(current.relative(inward), geometry);
		}
		return current;
	}

	private static Direction.Axis faceAxis(int face) {
		return switch (face) {
			case 0, 1 -> Direction.Axis.X;
			case 2, 3 -> Direction.Axis.Z;
			default -> Direction.Axis.Y;
		};
	}

	private static double facePlaneCenterCoordinate(BoxGeometry geometry, int face) {
		return switch (face) {
			case 0 -> geometry.minX + 0.5D;
			case 1 -> geometry.maxX + 0.5D;
			case 2 -> geometry.minZ + 0.5D;
			case 3 -> geometry.maxZ + 0.5D;
			case 4 -> geometry.floorY + 0.5D;
			default -> geometry.roofY + 0.5D;
		};
	}

	private static Vec3 blockCenter(BlockPos pos) {
		return pos == null ? Vec3.ZERO : new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
	}

	private static int nextIntInclusive(Random random, int min, int max) {
		if (random == null || max <= min) {
			return min;
		}
		return min + random.nextInt(max - min + 1);
	}

	private static BlockPos worldRevealShellPointForFace(BoxGeometry geometry, int face, Random random) {
		if (geometry == null || random == null) {
			return BlockPos.ZERO;
		}
		int x = nextIntInclusive(random, geometry.minX, geometry.maxX);
		int y = nextIntInclusive(random, geometry.floorY, geometry.roofY);
		int z = nextIntInclusive(random, geometry.minZ, geometry.maxZ);
		return switch (face) {
			case 0 -> new BlockPos(geometry.minX, y, z);
			case 1 -> new BlockPos(geometry.maxX, y, z);
			case 2 -> new BlockPos(x, y, geometry.minZ);
			case 3 -> new BlockPos(x, y, geometry.maxZ);
			case 4 -> new BlockPos(x, geometry.floorY, z);
			default -> new BlockPos(x, geometry.roofY, z);
		};
	}

	private static void tickWorldRevealCracking(MinecraftServer server, ServerLevel level, long nowTick) {
		if (server == null || level == null) {
			return;
		}
		if (WORLD_REVEAL_EPISODES.isEmpty()) {
			beginWorldRevealBlackoutFade(server, level, nowTick);
			return;
		}
		if (worldRevealCrackStartTick == Long.MIN_VALUE) {
			worldRevealCrackStartTick = nowTick;
		}
		if (nowTick < worldRevealCrackStartTick) {
			return;
		}
		if (!worldRevealEarthquakeSoundStarted) {
			startWorldRevealEarthquakeSound(level);
			worldRevealEarthquakeSoundStarted = true;
			stateDirty = true;
		}
		long elapsedTicks = nowTick - worldRevealCrackStartTick;
		int visibleThreshold = resolveWorldRevealVisibleEpisodeThreshold();
		while (worldRevealBurstCursor < WORLD_REVEAL_CRACK_BURSTS.length
				&& elapsedTicks >= WORLD_REVEAL_CRACK_BURSTS[worldRevealBurstCursor].offsetTicks()) {
			WorldRevealBurst burst = WORLD_REVEAL_CRACK_BURSTS[worldRevealBurstCursor];
			int previousCursor = worldRevealVisibleEpisodeCursor;
			worldRevealBurstWeightProgress += burst.growthWeight();
			int targetCursor = resolveWorldRevealBurstTargetCursor(visibleThreshold, elapsedTicks, worldRevealBurstWeightProgress);
			if (targetCursor <= worldRevealVisibleEpisodeCursor && worldRevealVisibleEpisodeCursor < visibleThreshold) {
				targetCursor = worldRevealVisibleEpisodeCursor + 1;
			}
			while (worldRevealVisibleEpisodeCursor < targetCursor && worldRevealVisibleEpisodeCursor < visibleThreshold) {
				revealWorldRevealEpisode(level, WORLD_REVEAL_EPISODES.get(worldRevealVisibleEpisodeCursor));
				worldRevealVisibleEpisodeCursor++;
			}
			emitWorldRevealBurstParticles(level, previousCursor, worldRevealVisibleEpisodeCursor, burst.particleBudget(), burst.impactStrength());
			worldRevealBurstCursor++;
			stateDirty = true;
		}
		if (elapsedTicks < WORLD_REVEAL_CRACKING_DURATION_TICKS) {
			return;
		}
		while (worldRevealVisibleEpisodeCursor < visibleThreshold) {
			revealWorldRevealEpisode(level, WORLD_REVEAL_EPISODES.get(worldRevealVisibleEpisodeCursor));
			worldRevealVisibleEpisodeCursor++;
		}
		if (worldRevealVisibleEpisodeCursor > 0) {
			emitWorldRevealBurstParticles(level, Math.max(0, worldRevealVisibleEpisodeCursor - 2), worldRevealVisibleEpisodeCursor, 28, 1.0F);
		}
		if (elapsedTicks >= WORLD_REVEAL_CRACKING_DURATION_TICKS) {
			beginWorldRevealBlackoutFade(server, level, nowTick);
		}
	}

	private static int resolveWorldRevealVisibleEpisodeThreshold() {
		if (WORLD_REVEAL_EPISODES.isEmpty()) {
			return 0;
		}
		return Mth.clamp((int) Math.ceil(WORLD_REVEAL_EPISODES.size() * WORLD_REVEAL_VISIBLE_TARGET_PROGRESS), 1, WORLD_REVEAL_EPISODES.size());
	}

	private static int resolveWorldRevealBurstTargetCursor(int visibleThreshold, long elapsedTicks, float burstWeightProgress) {
		if (visibleThreshold <= 0) {
			return 0;
		}
		float timeRatio = WORLD_REVEAL_CRACKING_DURATION_TICKS <= 0L
				? 1.0F
				: Mth.clamp((float) elapsedTicks / (float) WORLD_REVEAL_CRACKING_DURATION_TICKS, 0.0F, 1.0F);
		float ratio = WORLD_REVEAL_TOTAL_BURST_WEIGHT <= 0.0F
				? timeRatio
				: Mth.clamp(burstWeightProgress / WORLD_REVEAL_TOTAL_BURST_WEIGHT, 0.0F, 1.0F);
		ratio = Mth.clamp(ratio * 0.28F + timeRatio * 0.72F, 0.0F, 1.0F);
		return Mth.clamp((int) Math.round(visibleThreshold * ratio), 0, visibleThreshold);
	}

	private static void revealWorldRevealEpisode(ServerLevel level, WorldRevealEpisode episode) {
		revealWorldRevealEpisode(level, episode, false);
	}

	private static void revealWorldRevealEpisode(ServerLevel level, WorldRevealEpisode episode, boolean ignoreProtection) {
		if (level == null || episode == null || episode.revealPositions().isEmpty()) {
			return;
		}
		for (BlockPos pos : episode.revealPositions()) {
			if (!revealWorldRevealPosition(level, pos, ignoreProtection) && !ignoreProtection && pos != null) {
				WORLD_REVEAL_DEFERRED_POSITIONS.add(pos.asLong());
			}
		}
	}

	private static boolean revealWorldRevealPosition(ServerLevel level, BlockPos pos, boolean ignoreProtection) {
		if (level == null || pos == null || isServerStructureFootprint(pos)) {
			return true;
		}
		long key = pos.asLong();
		if (WORLD_REVEAL_REVEALED_POSITIONS.contains(key)) {
			WORLD_REVEAL_DEFERRED_POSITIONS.remove(key);
			return true;
		}
		if (!ignoreProtection && isProtectedDuringVisibleReveal(level, pos)) {
			return false;
		}
		BlockState targetState = WORLD_REVEAL_TARGET_STATES.get(key);
		BlockState currentState = level.getBlockState(pos);
		boolean shellBlock = currentState.is(Blocks.BLACK_CONCRETE) || currentState.is(Blocks.BARRIER);
		if (targetState == null && !shellBlock) {
			WORLD_REVEAL_REVEALED_POSITIONS.add(key);
			WORLD_REVEAL_DEFERRED_POSITIONS.remove(key);
			return true;
		}
		if (shellBlock) {
			level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 5, 0.22D, 0.22D, 0.22D, 0.01D);
			level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 2, 0.18D, 0.18D, 0.18D, 0.0D);
		}
		if (targetState != null && !targetState.isAir()) {
			setSceneBlockSilently(level, pos, targetState);
			level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 2, 0.12D, 0.12D, 0.12D, 0.0D);
		} else if (shellBlock) {
			setSceneBlockSilently(level, pos, Blocks.AIR.defaultBlockState());
		}
		WORLD_REVEAL_REVEALED_POSITIONS.add(key);
		WORLD_REVEAL_DEFERRED_POSITIONS.remove(key);
		return true;
	}

	private static void revealWorldRevealEpisodeBatch(ServerLevel level, int maxEpisodes, boolean ignoreProtection) {
		if (level == null || maxEpisodes <= 0) {
			return;
		}
		int processed = 0;
		while (worldRevealVisibleEpisodeCursor < WORLD_REVEAL_EPISODES.size() && processed < maxEpisodes) {
			revealWorldRevealEpisode(level, WORLD_REVEAL_EPISODES.get(worldRevealVisibleEpisodeCursor), ignoreProtection);
			worldRevealVisibleEpisodeCursor++;
			processed++;
		}
	}

	private static void revealWorldRevealDeferredBatch(ServerLevel level, int maxPositions, boolean ignoreProtection) {
		if (level == null || maxPositions <= 0 || WORLD_REVEAL_DEFERRED_POSITIONS.isEmpty()) {
			return;
		}
		List<Long> retry = new ArrayList<>();
		Iterator<Long> iterator = WORLD_REVEAL_DEFERRED_POSITIONS.iterator();
		int processed = 0;
		while (iterator.hasNext() && processed < maxPositions) {
			long key = iterator.next();
			iterator.remove();
			if (!revealWorldRevealPosition(level, BlockPos.of(key), ignoreProtection)) {
				retry.add(key);
			}
			processed++;
		}
		WORLD_REVEAL_DEFERRED_POSITIONS.addAll(retry);
	}

	private static boolean isWorldRevealCoverageComplete() {
		return worldRevealVisibleEpisodeCursor >= WORLD_REVEAL_EPISODES.size()
				&& WORLD_REVEAL_DEFERRED_POSITIONS.isEmpty()
				&& WORLD_REVEAL_REVEALED_POSITIONS.containsAll(WORLD_REVEAL_REQUIRED_POSITIONS);
	}

	private static void emitWorldRevealBurstParticles(
			ServerLevel level,
			int fromEpisode,
			int toEpisode,
			int particleBudget,
			float impactStrength
	) {
		if (level == null || particleBudget <= 0 || WORLD_REVEAL_EPISODES.isEmpty()) {
			return;
		}
		int sampleStart = Mth.clamp(Math.max(0, fromEpisode - 1), 0, WORLD_REVEAL_EPISODES.size() - 1);
		int sampleEndExclusive = Mth.clamp(
				Math.max(sampleStart + 1, toEpisode + WORLD_REVEAL_CRACK_PARTICLE_LOOKAHEAD_EPISODES),
				sampleStart + 1,
				WORLD_REVEAL_EPISODES.size()
		);
		int window = sampleEndExclusive - sampleStart;
		for (int i = 0; i < particleBudget; i++) {
			int episodeIndex = sampleStart + Math.floorMod(i * 5 + worldRevealBurstCursor * 3, window);
			WorldRevealEpisode episode = WORLD_REVEAL_EPISODES.get(episodeIndex);
			if (episode == null || episode.crackPoints().isEmpty()) {
				continue;
			}
			List<Vec3> crackPoints = episode.crackPoints();
			Vec3 particleSpot = crackPoints.get(Math.floorMod(i * 11 + episodeIndex * 7 + toEpisode * 13, crackPoints.size()));
			level.sendParticles(WORLD_REVEAL_CRACK_CORE_PARTICLE, particleSpot.x, particleSpot.y, particleSpot.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			if ((i & 1) == 0 || impactStrength >= 0.8F) {
				level.sendParticles(
						WORLD_REVEAL_CRACK_EDGE_PARTICLE,
						particleSpot.x,
						particleSpot.y,
						particleSpot.z,
						1,
						0.018D,
						0.018D,
						0.018D,
						0.0D
				);
			}
		}
	}

	private static void startWorldRevealEarthquakeSound(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		double x = serverAnchor.getX() + 0.5D;
		double y = serverAnchor.getY() + 0.5D;
		double z = serverAnchor.getZ() + 0.5D;
		for (ServerPlayer player : level.players()) {
			if (player == null || player.connection == null || !isInsideFootprint(outerGeometry, player.blockPosition())) {
				continue;
			}
			player.connection.send(new ClientboundStopSoundPacket(WORLD_REVEAL_EARTHQUAKE_SOUND_ID, SoundSource.AMBIENT));
			player.connection.send(
					new ClientboundSoundPacket(
							WORLD_REVEAL_EARTHQUAKE_SOUND,
							SoundSource.AMBIENT,
							x,
							y,
							z,
							WORLD_REVEAL_EARTHQUAKE_VOLUME,
							1.0F,
							level.random.nextLong()
					)
			);
		}
	}

	private static void stopWorldRevealEarthquakeSound(ServerLevel level) {
		if (level == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player == null || player.connection == null) {
				continue;
			}
			player.connection.send(new ClientboundStopSoundPacket(WORLD_REVEAL_EARTHQUAKE_SOUND_ID, SoundSource.AMBIENT));
		}
	}

	private static boolean isProtectedDuringVisibleReveal(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || serverAnchor == null) {
			return false;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		for (ServerPlayer player : level.players()) {
			if (player == null || !isInsideFootprint(outerGeometry, player.blockPosition())) {
				continue;
			}
			BlockPos playerPos = player.blockPosition();
			if (Math.abs(pos.getX() - playerPos.getX()) <= WORLD_REVEAL_VISIBLE_PROTECTION_RADIUS
					&& Math.abs(pos.getZ() - playerPos.getZ()) <= WORLD_REVEAL_VISIBLE_PROTECTION_RADIUS
					&& pos.getY() >= playerPos.getY() - 1
					&& pos.getY() <= playerPos.getY() + WORLD_REVEAL_VISIBLE_PROTECTION_HEIGHT) {
				return true;
			}
		}
		return false;
	}

	private static void beginWorldRevealBlackoutFade(MinecraftServer server, ServerLevel level, long nowTick) {
		if (server == null || level == null || worldRevealPhase == WorldRevealPhase.BLACKOUT_FADE
				|| worldRevealPhase == WorldRevealPhase.RELOCATE
				|| worldRevealPhase == WorldRevealPhase.SETTLE) {
			return;
		}
		stopWorldRevealEarthquakeSound(level);
		worldRevealEarthquakeSoundStarted = false;
		worldRevealPhase = WorldRevealPhase.BLACKOUT_FADE;
		worldRevealPhaseStartTick = nowTick;
		worldRevealMusicEndTick = Long.MIN_VALUE;
		worldRevealDarknessClearTick = nowTick + 80L;
		clearStartupWorldgenDisplay(level);
		long initialDarknessTicks = 20L * 6L;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player != null && player.level() == level) {
				applyWorldRevealDarkness(player, initialDarknessTicks);
			}
		}
		stateDirty = true;
	}

	private static void tickWorldRevealBlackoutFade(MinecraftServer server, ServerLevel level, long nowTick) {
		if (server == null || level == null) {
			return;
		}
		if (worldRevealMusicEndTick == Long.MIN_VALUE) {
			long musicDurationTicks = ServerStabilitySystem.playFeedMusicForStartup(level, resolveServerAnchor(level));
			worldRevealMusicEndTick = nowTick + Math.max(1L, musicDurationTicks);
		}
		worldRevealPhase = WorldRevealPhase.RELOCATE;
		worldRevealPhaseStartTick = nowTick;
		refreshWorldRevealTargets(level);
		stateDirty = true;
	}

	private static long resolveWorldRevealDarknessBlendInTicks() {
		int blendInTicks = MobEffects.DARKNESS.value().getBlendInDurationTicks();
		return Math.max(1L, blendInTicks > 0 ? blendInTicks : WORLD_REVEAL_DARKNESS_ONSET_TICKS);
	}

	private static void applyWorldRevealDarkness(ServerPlayer player) {
		applyWorldRevealDarkness(player, 20L * 6L);
	}

	private static void applyWorldRevealDarkness(ServerPlayer player, long durationTicks) {
		if (player == null) {
			return;
		}
		int duration = (int) Math.max(80L, durationTicks);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, false, true));
	}

	private static void tickWorldRevealDarknessRelease(ServerLevel level, long nowTick) {
		if (level == null || worldRevealDarknessClearTick == Long.MIN_VALUE || nowTick < worldRevealDarknessClearTick) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player != null) {
				player.removeEffect(MobEffects.DARKNESS);
			}
		}
		worldRevealDarknessClearTick = Long.MIN_VALUE;
	}

	private static float computeWorldRevealTotalBurstWeight() {
		float total = 0.0F;
		for (WorldRevealBurst burst : WORLD_REVEAL_CRACK_BURSTS) {
			if (burst != null) {
				total += Math.max(0.0F, burst.growthWeight());
			}
		}
		return total;
	}

	private static WorldRevealBurst burst(int offsetTicks, float growthWeight, int particleBudget, float impactStrength) {
		return new WorldRevealBurst(offsetTicks, growthWeight, particleBudget, impactStrength);
	}

	private static void tickWorldRevealRelocate(MinecraftServer server, ServerLevel level, long nowTick) {
		tickWorldRevealDarknessRelease(level, nowTick);
		revealWorldRevealEpisodeBatch(level, 8, false);
		revealWorldRevealDeferredBatch(level, WORLD_REVEAL_RELOCATE_DEFERRED_BATCH, false);
		refreshWorldRevealTargets(level);
		steerWorldRevealPlayers(server, level, true);
		if (nowTick - worldRevealPhaseStartTick < WORLD_REVEAL_RELOCATE_MIN_TICKS) {
			return;
		}
		if (!areWorldRevealPlayersReady(server, level, true)) {
			return;
		}
		beginWorldRevealSettlePhase(level, nowTick);
	}

	private static void beginWorldRevealSettlePhase(ServerLevel level, long nowTick) {
		revealWorldRevealDeferredBatch(level, WORLD_REVEAL_SETTLE_DEFERRED_BATCH, true);
		worldRevealBarriersPlaced = false;
		worldRevealPhase = WorldRevealPhase.SETTLE;
		worldRevealPhaseStartTick = nowTick;
		stateDirty = true;
	}

	private static void tickWorldRevealSettle(MinecraftServer server, ServerLevel level, long nowTick) {
		tickWorldRevealDarknessRelease(level, nowTick);
		revealWorldRevealEpisodeBatch(level, 96, true);
		revealWorldRevealDeferredBatch(level, WORLD_REVEAL_SETTLE_DEFERRED_BATCH, true);
		refreshWorldRevealTargets(level);
		steerWorldRevealPlayers(server, level, false);
		if (nowTick - worldRevealPhaseStartTick < WORLD_REVEAL_SETTLE_MIN_TICKS) {
			return;
		}
		if (!areWorldRevealPlayersReady(server, level, false)) {
			return;
		}
		if (worldRevealMusicEndTick != Long.MIN_VALUE && nowTick < worldRevealMusicEndTick) {
			return;
		}
		if (!isWorldRevealCoverageComplete()) {
			return;
		}
		completeWorldReveal(server, level);
	}

	private static void refreshWorldRevealTargets(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		BoxGeometry barrierGeometry = computeBarrierGeometry(resolveServerAnchor(level));
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		WORLD_REVEAL_SAFE_TARGETS.entrySet().removeIf(entry -> level.getServer() == null
				|| level.getServer().getPlayerList().getPlayer(entry.getKey()) == null
				|| level.getServer().getPlayerList().getPlayer(entry.getKey()).level() != level);
		for (ServerPlayer player : level.players()) {
			if (!isInsideFootprint(outerGeometry, player.blockPosition())) {
				continue;
			}
			WORLD_REVEAL_SAFE_TARGETS.computeIfAbsent(player.getUUID(), ignored -> resolveNearestWorldRevealTarget(player, barrierGeometry));
		}
	}

	private static Vec3 resolveNearestWorldRevealTarget(ServerPlayer player, BoxGeometry barrierGeometry) {
		if (player == null || barrierGeometry == null) {
			return Vec3.ZERO;
		}
		int startX = Mth.clamp(player.blockPosition().getX(), barrierGeometry.minX + 1, barrierGeometry.maxX - 1);
		int startZ = Mth.clamp(player.blockPosition().getZ(), barrierGeometry.minZ + 1, barrierGeometry.maxZ - 1);
		double bestScore = Double.MAX_VALUE;
		Vec3 best = null;
		int maxRadius = Math.max(barrierGeometry.maxX - barrierGeometry.minX, barrierGeometry.maxZ - barrierGeometry.minZ);
		for (int radius = 0; radius <= maxRadius; radius++) {
			boolean foundAtRadius = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int x = startX + dx;
					int z = startZ + dz;
					if (x < barrierGeometry.minX + 1 || x > barrierGeometry.maxX - 1 || z < barrierGeometry.minZ + 1 || z > barrierGeometry.maxZ - 1) {
						continue;
					}
					int surfaceY = WORLD_REVEAL_SURFACE_Y.getOrDefault(surfaceColumnKey(x, z), barrierGeometry.floorY);
					if (surfaceY + 2 >= barrierGeometry.roofY) {
						continue;
					}
					double score = dx * dx + dz * dz + Math.abs((surfaceY + WORLD_REVEAL_FINAL_TARGET_OFFSET) - player.getY()) * 0.2D;
					if (best == null || score < bestScore) {
						bestScore = score;
						best = new Vec3(x + 0.5D, surfaceY + WORLD_REVEAL_FINAL_TARGET_OFFSET, z + 0.5D);
						foundAtRadius = true;
					}
				}
			}
			if (foundAtRadius && best != null) {
				break;
			}
		}
		if (best != null) {
			return best;
		}
		return new Vec3(startX + 0.5D, barrierGeometry.floorY + WORLD_REVEAL_FINAL_TARGET_OFFSET, startZ + 0.5D);
	}

	private static void steerWorldRevealPlayers(MinecraftServer server, ServerLevel level, boolean relocatePhase) {
		if (server == null || level == null || serverAnchor == null) {
			return;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || player.level() != level || !isInsideFootprint(outerGeometry, player.blockPosition())) {
				continue;
			}
			Vec3 finalTarget = WORLD_REVEAL_SAFE_TARGETS.get(player.getUUID());
			if (finalTarget == null) {
				continue;
			}
			Vec3 target = relocatePhase
					? new Vec3(finalTarget.x, Math.max(finalTarget.y + (WORLD_REVEAL_PREP_TARGET_OFFSET - WORLD_REVEAL_FINAL_TARGET_OFFSET), player.getY()), finalTarget.z)
					: finalTarget;
			steerPlayerToWorldRevealTarget(player, target, relocatePhase);
		}
	}

	private static void steerPlayerToWorldRevealTarget(ServerPlayer player, Vec3 target, boolean relocatePhase) {
		if (player == null || target == null) {
			return;
		}
		Vec3 delta = target.subtract(player.position());
		Vec3 current = player.getDeltaMovement();
		double pushX = Mth.clamp(delta.x * 0.18D, -WORLD_REVEAL_HORIZONTAL_SPEED, WORLD_REVEAL_HORIZONTAL_SPEED);
		double pushZ = Mth.clamp(delta.z * 0.18D, -WORLD_REVEAL_HORIZONTAL_SPEED, WORLD_REVEAL_HORIZONTAL_SPEED);
		double vertical;
		if (relocatePhase && delta.y < 0.0D) {
			vertical = Math.max(0.0D, current.y * 0.6D);
		} else {
			vertical = Math.abs(delta.y) < 0.04D ? 0.0D : Mth.clamp(delta.y * 0.22D, -WORLD_REVEAL_VERTICAL_SPEED, WORLD_REVEAL_VERTICAL_SPEED);
		}
		player.setDeltaMovement(current.x * 0.55D + pushX, vertical, current.z * 0.55D + pushZ);
		player.hurtMarked = true;
		player.fallDistance = 0.0F;
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false, false));
	}

	private static boolean areWorldRevealPlayersReady(MinecraftServer server, ServerLevel level, boolean relocatePhase) {
		if (server == null || level == null || serverAnchor == null) {
			return true;
		}
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || player.level() != level || !isInsideFootprint(outerGeometry, player.blockPosition())) {
				continue;
			}
			Vec3 finalTarget = WORLD_REVEAL_SAFE_TARGETS.get(player.getUUID());
			if (finalTarget == null) {
				continue;
			}
			if (relocatePhase) {
				double horizontalDistanceSqr = horizontalDistanceSqr(player.position(), finalTarget);
				if (horizontalDistanceSqr > WORLD_REVEAL_READY_DISTANCE_SQR || player.getY() + 0.05D < finalTarget.y + 0.20D) {
					return false;
				}
				continue;
			}
			double distanceSqr = player.position().distanceToSqr(finalTarget);
			if (distanceSqr > WORLD_REVEAL_READY_DISTANCE_SQR || Math.abs(player.getY() - finalTarget.y) > WORLD_REVEAL_READY_VERTICAL_DELTA) {
				return false;
			}
		}
		return true;
	}

	private static void clearBarrierInteriorForWorldReveal(ServerLevel level, BoxGeometry geometry) {
		if (level == null || geometry == null) {
			return;
		}
		for (int x = geometry.minX + 1; x <= geometry.maxX - 1; x++) {
			for (int z = geometry.minZ + 1; z <= geometry.maxZ - 1; z++) {
				for (int y = geometry.floorY - (BARRIER_FLOOR_DEPTH - 1); y <= geometry.roofY - 1; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (level.getBlockState(pos).is(Blocks.BARRIER)) {
						setSceneBlockSilently(level, pos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static void materializeWorldRevealBarrierTerrain(ServerLevel level) {
		if (level == null) {
			return;
		}
		for (BlockPos pos : WORLD_REVEAL_BARRIER_COLLISION) {
			if (pos == null || isServerStructureFootprint(pos) || level.getBlockState(pos).is(ModBlocks.SERVER)) {
				continue;
			}
			setSceneBlockSilently(level, pos, Blocks.BARRIER.defaultBlockState());
		}
	}

	private static void forceCompleteWorldReveal(MinecraftServer server) {
		ServerLevel level = server == null ? null : server.overworld();
		if (server == null || level == null) {
			return;
		}
		prepareWorldReveal(level);
		materializeCompletedWorldRevealTerrain(level);
		completeWorldReveal(server, level);
	}

	private static void completeWorldReveal(MinecraftServer server, ServerLevel level) {
		if (server == null || level == null) {
			return;
		}
		clearStartupWorldgenDisplay(level);
		restorePostStartMorning(level);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyFreeState(player);
			player.fallDistance = 0.0F;
		}
		worldRevealActive = false;
		worldRevealBarriersPlaced = false;
		worldRevealPhaseStartTick = Long.MIN_VALUE;
		worldRevealCrackStartTick = Long.MIN_VALUE;
		worldRevealMusicEndTick = Long.MIN_VALUE;
		worldRevealDarknessClearTick = Long.MIN_VALUE;
		worldRevealVisibleEpisodeCursor = 0;
		worldRevealBurstCursor = 0;
		worldRevealBurstWeightProgress = 0.0F;
		worldRevealEarthquakeSoundStarted = false;
		worldRevealPhase = WorldRevealPhase.NONE;
		completed = true;
		shellDissolving = false;
		scenePrepared = false;
		stopWorldRevealEarthquakeSound(level);
		PLAYER_STATES.clear();
		SHARED_BITCOIN_POSITIONS.clear();
		WORLD_REVEAL_TERRAIN.clear();
		WORLD_REVEAL_BARRIER_COLLISION.clear();
		WORLD_REVEAL_EPISODES.clear();
		WORLD_REVEAL_SURFACE_Y.clear();
		WORLD_REVEAL_TARGET_STATES.clear();
		WORLD_REVEAL_SAFE_TARGETS.clear();
		WORLD_REVEAL_REQUIRED_POSITIONS.clear();
		WORLD_REVEAL_DEFERRED_POSITIONS.clear();
		WORLD_REVEAL_REVEALED_POSITIONS.clear();
		clearSharedLaunchBossBar();
		restoreSeasonStartDifficulty(server);
		stateDirty = true;
	}

	private static void materializeCompletedWorldRevealTerrain(ServerLevel level) {
		BlockPos anchor = resolveServerAnchor(level);
		if (level == null || anchor == null) {
			return;
		}
		clearStartupWorldgenDisplay(level);
		removeSceneShellNow(level);
		for (TerrainPlacement placement : WORLD_REVEAL_TERRAIN) {
			if (placement == null || placement.pos() == null || placement.state() == null || isServerStructureFootprint(placement.pos())) {
				continue;
			}
			setSceneBlockSilently(level, placement.pos(), placement.state());
		}
		scenePrepared = false;
	}

	private static void restorePostStartMorning(ServerLevel level) {
		if (level == null) {
			return;
		}
		long currentDayTime = Math.max(0L, level.getDayTime());
		long dayBase = (currentDayTime / 24000L) * 24000L;
		long morning = dayBase + WORLD_REVEAL_POST_START_MORNING_TIME;
		if (currentDayTime >= morning) {
			morning += 24000L;
		}
		level.setDayTime(morning);
		level.setWeatherParameters(12000, 0, false, false);
		level.setRainLevel(0.0F);
		level.setThunderLevel(0.0F);
	}

	private static void ensureSceneBuilt(ServerLevel level) {
		if (level == null || serverAnchor == null || scenePrepared) {
			return;
		}
		ensureSceneSnapshot(level);
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
				setSceneBlockSilently(level, floorPos, Blocks.BLACK_CONCRETE.defaultBlockState());
				for (int y = geometry.floorY + 1; y <= geometry.roofY - 1; y++) {
					BlockPos clearPos = new BlockPos(x, y, z);
					if (isServerStructureFootprint(clearPos)) {
						continue;
					}
					if (!level.getBlockState(clearPos).isAir()) {
						setSceneBlockSilently(level, clearPos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static void buildSceneShell(ServerLevel level) {
		BoxGeometry outerGeometry = computeOuterBoxGeometry(resolveServerAnchor(level));
		for (BlockPos pos : collectBlackShellBlocks(outerGeometry)) {
			setSceneBlockSilently(level, pos, Blocks.BLACK_CONCRETE.defaultBlockState());
		}
		BoxGeometry barrierGeometry = computeBarrierGeometry(resolveServerAnchor(level));
		for (BlockPos pos : collectBarrierShellBlocks(barrierGeometry)) {
			setSceneBlockSilently(level, pos, Blocks.BARRIER.defaultBlockState());
		}
	}

	private static void removeSceneShellNow(ServerLevel level) {
		if (level == null || serverAnchor == null) {
			return;
		}
		clearStartupWorldgenDisplay(level);
		for (BlockPos pos : collectSceneShellBlocks(serverAnchor)) {
			if (level.getBlockState(pos).is(Blocks.BLACK_CONCRETE) || level.getBlockState(pos).is(Blocks.BARRIER)) {
				setSceneBlockSilently(level, pos, Blocks.AIR.defaultBlockState());
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
		for (int y = geometry.floorY; y <= geometry.roofY; y++) {
			for (int x = geometry.minX; x <= geometry.maxX; x++) {
				for (int z = geometry.minZ; z <= geometry.maxZ; z++) {
					boolean floor = y == geometry.floorY;
					boolean wall = x == geometry.minX || x == geometry.maxX || z == geometry.minZ || z == geometry.maxZ;
					boolean roof = y == geometry.roofY;
					if (floor || wall || roof) {
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
		if (level.getBlockState(slot.oreSupportPos).is(Blocks.BLACK_CONCRETE)) {
			level.setBlock(slot.oreSupportPos, Blocks.AIR.defaultBlockState(), 3);
		}
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
		player.removeEffect(MobEffects.DARKNESS);
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

	private static boolean shouldRelocateBuriedBootstrapServer(BlockPos currentAnchor, BlockPos bootstrapAnchor) {
		if (currentAnchor == null || bootstrapAnchor == null) {
			return false;
		}
		return currentAnchor.getX() == 0
				&& currentAnchor.getZ() == 0
				&& bootstrapAnchor.getX() == 0
				&& bootstrapAnchor.getZ() == 0
				&& bootstrapAnchor.getY() > currentAnchor.getY();
	}

	private static BlockPos resolveBootstrapAnchor(ServerLevel level) {
		if (level == null) {
			return new BlockPos(0, 64, 0);
		}
		int maxSupportY = Integer.MIN_VALUE;
		for (int x = -ServerStructureBreakSystem.STRUCTURE_HALF_WIDTH; x <= ServerStructureBreakSystem.STRUCTURE_HALF_WIDTH; x++) {
			for (int z = -ServerStructureBreakSystem.STRUCTURE_HALF_DEPTH; z <= ServerStructureBreakSystem.STRUCTURE_HALF_DEPTH; z++) {
				maxSupportY = Math.max(maxSupportY, resolveBootstrapSupportY(level, x, z));
			}
		}
		int anchorY = maxSupportY == Integer.MIN_VALUE ? 64 : maxSupportY + 1;
		if (anchorY < level.getMinY() + 4) {
			anchorY = 64;
		}
		int maxAnchorY = level.getMaxY() - ServerStructureBreakSystem.STRUCTURE_HEIGHT - 2;
		if (anchorY > maxAnchorY) {
			anchorY = maxAnchorY;
		}
		BlockPos candidate = new BlockPos(0, anchorY, 0);
		while (candidate.getY() < maxAnchorY && !isBootstrapVolumeClear(level, candidate, Direction.Axis.Z)) {
			candidate = candidate.above();
		}
		return candidate;
	}

	private static int resolveBootstrapSupportY(ServerLevel level, int x, int z) {
		if (level == null) {
			return Integer.MIN_VALUE;
		}
		int surfaceTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		int solidY = Math.max(level.getMinY(), surfaceTop - 1);
		while (solidY > level.getMinY() && !level.getBlockState(new BlockPos(x, solidY, z)).blocksMotion()) {
			solidY--;
		}
		return level.getBlockState(new BlockPos(x, solidY, z)).blocksMotion() ? solidY : Integer.MIN_VALUE;
	}

	private static boolean isBootstrapVolumeClear(ServerLevel level, BlockPos anchor, Direction.Axis axis) {
		if (level == null || anchor == null || axis == null) {
			return false;
		}
		for (BlockPos pos : ServerStructureBreakSystem.getStructurePositions(anchor, axis)) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	private static BlockPos resolveServerAnchor(ServerLevel level) {
		if (level == null) {
			return serverAnchor;
		}
		BlockPos resolved = discoverLiveServerAnchor(level);
		if (resolved != null) {
			if (!resolved.equals(serverAnchor)) {
				serverAnchor = resolved.immutable();
				stateDirty = true;
			}
			return serverAnchor;
		}
		if (serverAnchor == null) {
			serverAnchor = resolveBootstrapAnchor(level);
			stateDirty = true;
		}
		return serverAnchor;
	}

	private static BlockPos discoverLiveServerAnchor(ServerLevel level) {
		if (level == null) {
			return serverAnchor;
		}
		if (serverAnchor != null && isServerStructurePresent(level, serverAnchor)) {
			return serverAnchor;
		}
		BlockPos preferred = serverAnchor != null ? serverAnchor : resolveBootstrapAnchor(level);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity entity : level.getAllEntities()) {
			if (!ServerStructureBreakSystem.isServerStructureDisplay(entity)) {
				continue;
			}
			var anchor = ServerStructureBreakSystem.getServerStructureDisplayAnchor(entity);
			if (anchor.isEmpty() || !isServerStructurePresent(level, anchor.get())) {
				continue;
			}
			double distance = anchor.get().distSqr(preferred);
			if (best == null || distance < bestDistance) {
				best = anchor.get().immutable();
				bestDistance = distance;
			}
		}
		return best;
	}

	private static BlockPos discoverExistingServerAnchor(ServerLevel level, BlockPos primaryAnchor, BlockPos bootstrapAnchor) {
		if (level == null) {
			return null;
		}
		BlockPos discovered = discoverLiveServerAnchor(level);
		if (discovered != null) {
			return discovered.immutable();
		}
		BlockPos around = primaryAnchor != null ? primaryAnchor : bootstrapAnchor;
		discovered = discoverServerAnchorNearSurface(level, around, EXISTING_SERVER_SCAN_RADIUS);
		if (discovered != null) {
			return discovered.immutable();
		}
		if (bootstrapAnchor != null && (around == null || !bootstrapAnchor.equals(around))) {
			discovered = discoverServerAnchorNearSurface(level, bootstrapAnchor, EXISTING_SERVER_SCAN_RADIUS);
			if (discovered != null) {
				return discovered.immutable();
			}
		}
		return null;
	}

	private static BlockPos discoverServerAnchorNearSurface(ServerLevel level, BlockPos center, int radius) {
		if (level == null || center == null || radius < 0) {
			return null;
		}
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				int surfaceTop = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				int minY = Math.max(level.getMinY(), surfaceTop - EXISTING_SERVER_SCAN_VERTICAL_MARGIN);
				int maxY = Math.min(level.getMaxY() - 1, surfaceTop + EXISTING_SERVER_SCAN_VERTICAL_MARGIN);
				for (int y = maxY; y >= minY; y--) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.getBlockState(pos).is(ModBlocks.SERVER)) {
						continue;
					}
					double distance = pos.distSqr(center);
					if (best == null || distance < bestDistance) {
						best = pos.immutable();
						bestDistance = distance;
					}
					break;
				}
			}
		}
		return best;
	}

	private static void setDefaultSpawn(ServerLevel level) {
		BlockPos anchor = resolveServerAnchor(level);
		if (level == null || anchor == null) {
			return;
		}
		BlockPos spawn = anchor.offset(0, 0, get().barrierHalfDepth - 2);
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
		shellDissolving = false;
		worldRevealActive = false;
		worldRevealBarriersPlaced = false;
		lastSharedLaunchProgressTick = Long.MIN_VALUE;
		pendingSharedFinishTick = Long.MIN_VALUE;
		worldRevealPhaseStartTick = Long.MIN_VALUE;
		worldRevealCrackStartTick = Long.MIN_VALUE;
		worldRevealMusicEndTick = Long.MIN_VALUE;
		worldRevealDarknessClearTick = Long.MIN_VALUE;
		sharedLaunchCollectedBitcoins = 0;
		sharedLaunchRequiredBitcoins = 0;
		sharedLaunchMilestoneCursor = 0;
		sharedLaunchIntroTriggered = false;
		startupWorldgenFrameIndex = Integer.MIN_VALUE;
		worldRevealVisibleEpisodeCursor = 0;
		worldRevealBurstCursor = 0;
		worldRevealBurstWeightProgress = 0.0F;
		worldRevealEarthquakeSoundStarted = false;
		worldRevealPhase = WorldRevealPhase.NONE;
		SHELL_DISSOLVE_ORDER.clear();
		WORLD_REVEAL_TERRAIN.clear();
		WORLD_REVEAL_BARRIER_COLLISION.clear();
		WORLD_REVEAL_EPISODES.clear();
		WORLD_REVEAL_SURFACE_Y.clear();
		WORLD_REVEAL_TARGET_STATES.clear();
		WORLD_REVEAL_SAFE_TARGETS.clear();
		WORLD_REVEAL_REQUIRED_POSITIONS.clear();
		WORLD_REVEAL_DEFERRED_POSITIONS.clear();
		WORLD_REVEAL_REVEALED_POSITIONS.clear();
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
			worldRevealActive = state.worldRevealActive;
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
		state.worldRevealActive = worldRevealActive;
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
		SeasonStartVoiceSystem.clearPlayerChannel(player);
		state.phase = PlayerPhase.ISOLATED;
		state.nextStartPromptTick = Long.MAX_VALUE;
		state.guidanceNarrationGateTick = nowTick + resolveTriggerSequenceDurationTicks("player_intro_assigned");
		stateDirty = true;
		primeObservationState(player, state);
		SeasonStartVoiceSystem.fireTrigger(server, "player_waiting_start_confirmed", player);
		SeasonStartVoiceSystem.fireTrigger(server, "player_intro_assigned", player);
	}

	private static boolean shouldFastForwardGuidanceNarration(
			GuidanceSnapshot snapshot,
			boolean carryingBitcoin,
			boolean seesServer
	) {
		if (snapshot == null || !carryingBitcoin) {
			return false;
		}
		return snapshot.horizontalDistance <= GUIDANCE_QUIET_DISTANCE || seesServer;
	}

	private static void interruptAndFastForwardPlayerNarration(ServerPlayer player, PlayerSceneState state, long nowTick) {
		if (player != null) {
			SeasonStartVoiceSystem.clearPlayerChannel(player);
		}
		if (state == null) {
			return;
		}
		state.guidanceNarrationGateTick = nowTick;
		state.nextGuidanceTick = nowTick;
		state.nextGuidanceVoiceTick = nowTick;
		state.nextGuidanceEarliestTick = nowTick;
		state.lastGuidanceStateKey = "";
	}

	private static boolean shouldFireIntroTargetReaction(PlayerSceneState state, long nowTick) {
		return state != null && (!state.introTargetLocked || nowTick >= state.nextIntroTargetReactionTick);
	}

	private static void fireIntroTargetReaction(MinecraftServer server, ServerPlayer player, PlayerSceneState state, long nowTick) {
		if (server == null || player == null || state == null) {
			return;
		}
		state.guidanceRouteStarted = true;
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

	private static long surfaceColumnKey(int x, int z) {
		return BlockPos.asLong(x, 0, z);
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

	private static void setSceneBlockSilently(ServerLevel level, BlockPos pos, BlockState state) {
		if (level == null || pos == null || state == null) {
			return;
		}
		level.setBlock(pos, state, SCENE_BLOCK_SET_FLAGS);
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

	private record TerrainPlacement(BlockPos pos, BlockState state) {
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

	private record WorldRevealSeed(Vec3 point, int round, double crackRadius, double terrainRadius) {
	}

	private record WorldRevealEpisode(List<BlockPos> revealPositions, List<Vec3> crackPoints) {
	}

	private record WorldRevealFaceConstraint(Direction.Axis axis, double value) {
	}

	private record WorldRevealBurst(int offsetTicks, float growthWeight, int particleBudget, float impactStrength) {
	}

	private enum TurnHintDirection {
		NONE,
		LEFT,
		RIGHT
	}

	private enum WorldRevealPhase {
		NONE,
		CRACKING,
		BLACKOUT_FADE,
		RELOCATE,
		SETTLE
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
		private boolean worldRevealActive;
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
