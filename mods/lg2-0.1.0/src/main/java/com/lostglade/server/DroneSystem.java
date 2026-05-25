package com.lostglade.server;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import com.lostglade.Lg2;
import com.lostglade.item.DroneItem;
import com.lostglade.item.ModItems;
import com.lostglade.mixin.ClientboundSetPassengersPacketAccessor;
import com.lostglade.mixin.EntityTrackedDataAccessor;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
import com.lostglade.server.map.MapImageRenderSystem;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DroneSystem {
	private static final String IT_DRONE_SCOUT = "it_drone_scout";
	private static final String IT_DRONE_KAMIKAZE = "it_drone_kamikaze";
	private static final String DRONE_ROOT_TAG = "lg2_drone_root";
	private static final String DRONE_TYPE_TAG_PREFIX = "lg2_drone_type_";
	private static final String DRONE_KAMIKAZE_POWER_TAG_PREFIX = "lg2_drone_kamikaze_power_";
	private static final String DRONE_NIGHT_VISION_TAG = "lg2_drone_night_vision";
	private static final String DRONE_AUTO_AIM_TAG = "lg2_drone_auto_aim";
	private static final String DRONE_PAINT_TAG_PREFIX = "lg2_drone_paint_";
	private static final String DRONE_DISPLAY_TAG = "lg2_drone_display";
	private static final String DRONE_DISPLAY_OWNER_TAG_PREFIX = "lg2_drone_display_owner_";
	private static final String DRONE_DISPLAY_LAYER_TAG_PREFIX = "lg2_drone_display_layer_";
	private static final String DRONE_DISPLAY_LAYER_BASE = "base";
	private static final String DRONE_CAMERA_TAG = "lg2_drone_camera_anchor";
	private static final String DRONE_CAMERA_OWNER_TAG_PREFIX = "lg2_drone_camera_owner_";
	private static final String DRONE_NIGHT_VISION_CAMERA_TAG = "lg2_drone_night_vision_camera";
	private static final String DRONE_TURRET_TRIGGER_TAG = "lg2_drone_turret_trigger";
	private static final String DRONE_TURRET_TRIGGER_OWNER_TAG_PREFIX = "lg2_drone_turret_trigger_owner_";
	private static final Identifier DRONE_LOOP_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_loop");
	private static final Identifier DRONE_KAMIKAZE_LOOP_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_kamikaze_loop");
	private static final Identifier DRONE_BREAK_SOUND_ID = Identifier.fromNamespaceAndPath("minecraft", "entity.firework_rocket.blast");
	private static final Holder<SoundEvent> DRONE_LOOP_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(DRONE_LOOP_SOUND_ID));
	private static final Holder<SoundEvent> DRONE_KAMIKAZE_LOOP_SOUND = Holder.direct(
			SoundEvent.createVariableRangeEvent(DRONE_KAMIKAZE_LOOP_SOUND_ID)
	);
	private static final double DRONE_CRASH_EQUIVALENT_FALL_BLOCKS = 3.25D;
	private static final double DRONE_CRASH_REFERENCE_ACCELERATION = 0.04D;
	private static final double DRONE_SURFACE_WEAR_DECAY_PER_TICK = 0.018D;
	private static final double DRONE_COLLISION_SWEEP_STEP = 0.12D;
	private static final int DRONE_SURFACE_WEAR_PARTICLE_INTERVAL_TICKS = 2;
	private static final float DRONE_WIDTH = DroneGeometry.WIDTH;
	private static final float DRONE_HEIGHT = DroneGeometry.HEIGHT;
	private static final float DRONE_CAMERA_ANCHOR_SIZE = 0.01F;
	private static final double DRONE_SPAWN_Y_OFFSET = 0.24D;
	private static final float DRONE_DISPLAY_VIEW_RANGE = 64.0F;
	private static final float DRONE_DISPLAY_Y_OFFSET = 0.34F;
	private static final int DRONE_DISPLAY_INTERPOLATION_TICKS = 2;
	private static final float DRONE_DISPLAY_DRIVE_SMOOTHING = 0.35F;
	private static final float DRONE_MAX_TILT_DEGREES = 32.0F;
	private static final long DRONE_LOOP_REPLAY_TICKS = 10L;
	private static final double DRONE_SOUND_RADIUS_SQR = 16.0D * 16.0D;
	private static final float DRONE_SOUND_SOURCE_POWER = 0.58F;
	private static final float DRONE_SOUND_MIN_VOLUME = 1.0F;
	private static final float DRONE_SOUND_MAX_VOLUME = 1.0F;
	private static final float DRONE_SOUND_MIN_PITCH = 0.76F;
	private static final float DRONE_SOUND_MAX_PITCH = 1.18F;
	private static final float DRONE_KAMIKAZE_LOOP_LEVEL_1_VOLUME_SCALE = 0.42F;
	private static final float DRONE_KAMIKAZE_LOOP_LEVEL_2_VOLUME_SCALE = 0.76F;
	private static final float DRONE_KAMIKAZE_LOOP_LEVEL_3_VOLUME_SCALE = 1.15F;
	private static final float DRONE_KAMIKAZE_LOOP_PITCH_SHIFT = 1.08F;
	private static final int DRONE_KAMIKAZE_NO_POWER = 0;
	private static final int DRONE_KAMIKAZE_MIN_POWER = 1;
	private static final int DRONE_KAMIKAZE_MAX_POWER = 3;
	private static final float DRONE_KAMIKAZE_TNT_SPREAD = 0.26F;
	private static final float DRONE_VANILLA_WOBBLE_ROTATION_SCALE = 0.015625F;
	private static final float DRONE_VANILLA_WOBBLE_NEGATIVE_SCALE = 0.125F;
	private static final float DRONE_VANILLA_WOBBLE_PROGRESS_TO_RADIANS = (float) (Math.PI * 2.0D);
	private static final float DRONE_VANILLA_WOBBLE_NEGATIVE_PROGRESS_TO_RADIANS = (float) (Math.PI * 3.0D);
	private static final double UNCONTROLLED_GRAVITY = 0.04D;
	private static final double UNCONTROLLED_AIR_DRAG = 0.985D;
	private static final float UNCONTROLLED_ROTATION_LERP = 0.35F;
	private static final double UNCONTROLLED_SETTLED_HORIZONTAL_SPEED_SQR = 1.0E-6D;
	private static final double UNCONTROLLED_SETTLED_VERTICAL_SPEED = 0.045D;
	private static final long UNCONTROLLED_DRONE_RELEASE_GLIDE_TICKS = 60L;
	private static final long CONTROLLED_DRONE_MISSING_ROOT_GRACE_TICKS = 20L * 20L;
	private static final int DRONE_TURRET_INVENTORY_SIZE = 9;
	private static final long DRONE_TURRET_FIRE_COOLDOWN_TICKS = 4L;
	private static final long DRONE_TURRET_CONTROL_START_SUPPRESS_TICKS = 6L;
	private static final float DRONE_TURRET_AIR_TRIGGER_WIDTH = 1.6F;
	private static final float DRONE_TURRET_AIR_TRIGGER_HEIGHT = 1.6F;
	private static final double DRONE_TURRET_AIR_TRIGGER_HEAD_FORWARD_OFFSET = 0.24D;
	private static final double DRONE_TURRET_MUZZLE_FORWARD_OFFSET = 0.36D;
	private static final long DRONE_CONTROL_PRELOAD_TIMEOUT_TICKS = 20L * 8L;
	private static final int DRONE_CONTROL_PRELOAD_READY_RADIUS_CHUNKS = 2;
	private static final int DRONE_LOADING_CHUNK_TICKET_UNIQUE_FLAG = 32;
	private static final int DRONE_SIMULATION_CHUNK_TICKET_UNIQUE_FLAG = 64;
	private static final int OPERATOR_BODY_MIRROR_ENTITY_ID_START = -1_700_000_000;
	private static final byte OPERATOR_BODY_MIRROR_ALL_SKIN_PARTS = (byte) 0x7F;
	// TicketType equality is based on timeout/flags, so keep these distinct from vanilla player tickets.
	private static final TicketType DRONE_LOADING_CHUNK_TICKET_TYPE = new TicketType(
			0L,
			TicketType.FLAG_LOADING | DRONE_LOADING_CHUNK_TICKET_UNIQUE_FLAG
	);
	private static final TicketType DRONE_SIMULATION_CHUNK_TICKET_TYPE = new TicketType(
			0L,
			TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE | DRONE_SIMULATION_CHUNK_TICKET_UNIQUE_FLAG
	);
	private static final int PLAYER_HOTBAR_MENU_SLOT_START = 36;
	private static final int PLAYER_OFFHAND_MENU_SLOT = 45;
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final long DRONE_HUD_REFRESH_TICKS = 2L;
	private static final int DRONE_HUD_GRID_SIZE = 11;
	private static final int DRONE_HUD_GLYPH_BASE = 0xE700;
	private static final int DRONE_HUD_SPEED_BAR_GLYPH_BASE = 0xE780;
	private static final String DRONE_HUD_BAR_OVERLAP_GLYPH = "\uE944";
	private static final int DRONE_HUD_LABEL_COLOR = 0x6BD7FF;
	private static final int DRONE_HUD_VALUE_COLOR = 0xF4FFF6;
	private static final int DRONE_HUD_DIM_COLOR = 0x5A7080;
	private static final byte ENTITY_FLAG_ON_FIRE = 0x01;
	private static final byte ENTITY_FLAG_SHIFTING = 0x02;
	private static final byte ENTITY_FLAG_SPRINTING = 0x08;
	private static final byte ENTITY_FLAG_SWIMMING = 0x10;
	private static final byte ENTITY_FLAG_INVISIBLE = 0x20;
	private static final byte ENTITY_FLAG_FALL_FLYING = (byte) 0x80;
	private static final int CONTROLLED_VIEW_TELEPORT_ID_BASE = 1_000_000_000;
	private static final double CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON = 1.0E-5D;
	private static final double CONTROLLED_DRONE_MAX_REPORTED_MOVE_BLOCKS = 4.0D;
	private static final long POST_CONTROL_MOVE_PACKET_SUPPRESSION_TICKS = 20L;
	private static final long POST_CONTROL_CLIENT_RESYNC_TICKS = 8L;
	private static final double POST_CONTROL_MOVE_ACCEPT_DISTANCE_SQR = 2.0D * 2.0D;
	private static final int DRONE_MANAGED_NIGHT_VISION_AMPLIFIER = 1;
	private static final double DRONE_AUTO_AIM_SELECTION_RANGE_BLOCKS = 64.0D;
	private static final double DRONE_AUTO_AIM_INTERACTION_RANGE_BONUS = 64.0D;
	private static final float DRONE_AUTO_AIM_CONTROLLED_ROTATION_BLEND = 0.08F;
	private static final float DRONE_AUTO_AIM_CONTROLLED_MAX_YAW_STEP_DEGREES = 0.78F;
	private static final float DRONE_AUTO_AIM_CONTROLLED_MAX_PITCH_STEP_DEGREES = 0.60F;
	private static final float DRONE_AUTO_AIM_MANUAL_LOOK_THRESHOLD_DEGREES = 0.08F;
	private static final long DRONE_AUTO_AIM_MANUAL_SUPPRESSION_TICKS = 3L;
	private static final float DRONE_AUTO_AIM_UNCONTROLLED_ROTATION_BLEND = 0.09F;
	private static final float DRONE_AUTO_AIM_UNCONTROLLED_MAX_YAW_STEP_DEGREES = 1.00F;
	private static final float DRONE_AUTO_AIM_UNCONTROLLED_MAX_PITCH_STEP_DEGREES = 0.75F;
	private static final Identifier DRONE_AUTO_AIM_BLOCK_INTERACTION_RANGE_MODIFIER_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_auto_aim_block_interaction_range");
	private static final Identifier DRONE_AUTO_AIM_ENTITY_INTERACTION_RANGE_MODIFIER_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_auto_aim_entity_interaction_range");
	private static final double DRONE_CAMERA_ESCAPE_STEP = 0.04D;
	private static final int DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS = 4;
	private static final double[] DRONE_CAMERA_ESCAPE_Y_OFFSETS = new double[]{
			0.0D,
			-0.05D,
			0.05D,
			-0.10D,
			0.10D,
			-0.15D,
			-0.20D,
			-0.25D,
			-0.30D,
			-0.35D,
			-0.40D
	};
	private static final Map<UUID, DroneControlSession> ACTIVE_SESSIONS = new HashMap<>();
	private static final Map<UUID, DroneInputState> INPUTS = new HashMap<>();
	private static final Map<UUID, UUID> CONTROLLERS_BY_DRONE = new HashMap<>();
	private static final Map<UUID, UUID> DISPLAYS_BY_DRONE = new HashMap<>();
	private static final Map<UUID, UUID> CAMERA_ANCHORS_BY_DRONE = new HashMap<>();
	private static final Set<Item> DRONE_TURRET_PROJECTILES = Set.of(
			Items.ARROW,
			Items.TIPPED_ARROW,
			Items.SPECTRAL_ARROW,
			Items.TRIDENT,
			Items.FIREWORK_ROCKET,
			Items.FIRE_CHARGE,
			Items.WIND_CHARGE,
			Items.SNOWBALL,
			Items.EGG,
			Items.EXPERIENCE_BOTTLE,
			Items.SPLASH_POTION,
			Items.LINGERING_POTION
	);
	private static final Map<UUID, TurretInventory> DRONE_TURRET_INVENTORIES = new HashMap<>();
	private static final Map<UUID, Long> NEXT_DRONE_TURRET_FIRE_TICK = new HashMap<>();
	private static final Map<UUID, UUID> CONTROLLED_DRONE_TURRET_TRIGGERS = new HashMap<>();
	private static final Map<UUID, UncontrolledDroneState> UNCONTROLLED_DRONES = new HashMap<>();
	private static final Map<DroneChunkTicketKey, Integer> ACTIVE_DRONE_CHUNK_TICKETS = new HashMap<>();
	private static final Map<UUID, DroneScreenStreamLoadState> SCREEN_STREAM_DRONE_LOAD_STATES = new HashMap<>();
	private static final Map<UUID, DroneScreenStreamLoadState> LAST_KNOWN_DRONE_FEED_STATES = new HashMap<>();
	private static final Map<UUID, PendingDroneControlStart> PENDING_CONTROL_STARTS = new HashMap<>();
	private static final Map<UUID, OperatorBodyMirror> OPERATOR_BODY_MIRRORS = new HashMap<>();
	private static final Map<UUID, Long> NEXT_DRONE_SOUND_TICK = new HashMap<>();
	private static final Map<UUID, Long> NEXT_DRONE_ARM_ALLOWED_TICK = new HashMap<>();
	private static final Map<UUID, DroneDisplayWobbleState> DISPLAY_WOBBLE_BY_DRONE = new HashMap<>();
	private static final Map<UUID, Long> POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK = new HashMap<>();
	private static final Map<UUID, Long> POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK = new HashMap<>();
	private static final Set<UUID> VISUALLY_CONTROLLED_PLAYERS = new HashSet<>();
	private static final Set<UUID> CONTROLLED_OPERATOR_MANAGED_NIGHT_VISION = new HashSet<>();
	private static final Set<UUID> CONTROLLED_OPERATOR_AUTO_AIM_HIGHLIGHTS = new HashSet<>();
	private static final ThreadLocal<Boolean> CONTROLLED_OPERATOR_PACKET_REWRITE_BYPASS = ThreadLocal.withInitial(() -> false);
	private static int nextOperatorBodyMirrorEntityId = OPERATOR_BODY_MIRROR_ENTITY_ID_START;

	private DroneSystem() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			InteractionResult autoAimResult = handleControlledAutoAimEntityInteraction(serverPlayer, hand, entity);
			if (autoAimResult != InteractionResult.PASS) {
				return autoAimResult;
			}
			Entity root = resolveDroneRoot(entity);
			if (root == null) {
				return InteractionResult.PASS;
			}
			if (serverPlayer.getItemInHand(hand).is(ModItems.BLUETOOTH_ADAPTER)) {
				return InteractionResult.PASS;
			}
			DroneControlSession activeSession = ACTIVE_SESSIONS.get(serverPlayer.getUUID());
			if (activeSession != null && Objects.equals(activeSession.droneUuid(), root.getUUID())) {
				return InteractionResult.CONSUME;
			}
			if (serverPlayer.isShiftKeyDown()
					&& hasDroneTurretModule(root)
					&& !serverPlayer.getItemInHand(hand).is(Items.DISPENSER)
					&& openDroneTurretMenu(serverPlayer, root)) {
				return InteractionResult.SUCCESS;
			}
			InteractionResult tuningResult = serverPlayer.isShiftKeyDown()
					? tryUnloadDroneModule(serverPlayer, root)
					: tryTuneDrone(serverPlayer, root, serverPlayer.getItemInHand(hand));
			if (tuningResult != InteractionResult.PASS) {
				return tuningResult;
			}
			String requiredUpgrade = resolveRequiredUpgradeForDroneRoot(root);
			if (requiredUpgrade != null && !ServerUpgradeUiSystem.hasUpgrade(serverPlayer, requiredUpgrade)) {
				return InteractionResult.FAIL;
			}
			return startControlling(serverPlayer, root) ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			InteractionResult autoAimResult = handleControlledAutoAimEntityInteraction(serverPlayer, hand, entity);
			if (autoAimResult != InteractionResult.PASS) {
				return autoAimResult;
			}
			Entity root = resolveDroneRoot(entity);
			if (root == null) {
				return InteractionResult.PASS;
			}
			destroyDrone(root, serverPlayer, true);
			return InteractionResult.SUCCESS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			return handleControlledAutoAimBlockInteraction(serverPlayer, hand, hitResult == null ? null : hitResult.getBlockPos());
		});
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			return handleControlledAutoAimBlockInteraction(serverPlayer, hand, pos);
		});

		ServerTickEvents.END_SERVER_TICK.register(DroneSystem::tick);
		ServerEntityEvents.ENTITY_LOAD.register(DroneSystem::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(DroneSystem::onEntityUnload);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stopControlling((ServerPlayer) handler.player, false));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> stopControlling(newPlayer, false));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID playerId : new ArrayList<>(ACTIVE_SESSIONS.keySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				if (player != null) {
					stopControlling(player, false);
				}
			}
			releaseAllDroneChunkTickets(server);
			ACTIVE_SESSIONS.clear();
			INPUTS.clear();
			CONTROLLERS_BY_DRONE.clear();
			DISPLAYS_BY_DRONE.clear();
			CAMERA_ANCHORS_BY_DRONE.clear();
			DRONE_TURRET_INVENTORIES.clear();
			NEXT_DRONE_TURRET_FIRE_TICK.clear();
			CONTROLLED_DRONE_TURRET_TRIGGERS.clear();
			UNCONTROLLED_DRONES.clear();
			ACTIVE_DRONE_CHUNK_TICKETS.clear();
			SCREEN_STREAM_DRONE_LOAD_STATES.clear();
			LAST_KNOWN_DRONE_FEED_STATES.clear();
			PENDING_CONTROL_STARTS.clear();
			OPERATOR_BODY_MIRRORS.clear();
			NEXT_DRONE_ARM_ALLOWED_TICK.clear();
			POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.clear();
			POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.clear();
			VISUALLY_CONTROLLED_PLAYERS.clear();
			DISPLAY_WOBBLE_BY_DRONE.clear();
			CONTROLLED_OPERATOR_MANAGED_NIGHT_VISION.clear();
			CONTROLLED_OPERATOR_AUTO_AIM_HIGHLIGHTS.clear();
		});
	}

	public static InteractionResult placeDrone(UseOnContext context) {
		if (context == null) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}
		ItemStack placedStack = context.getItemInHand();
		ItemStack placementSnapshot = placedStack.copy();
		String requiredUpgrade = resolveRequiredUpgradeForDroneItem(placementSnapshot);
		if (requiredUpgrade != null && !ServerUpgradeUiSystem.hasUpgrade(player, requiredUpgrade)) {
			return InteractionResult.FAIL;
		}
		DroneItem.DroneType droneType = DroneItem.getDroneType(placementSnapshot);
		int kamikazePower = DroneItem.getKamikazePower(placementSnapshot);
		boolean nightVision = DroneItem.hasNightVisionModule(placementSnapshot);
		boolean autoAim = DroneItem.hasAutoAimModule(placementSnapshot);
		DyeColor paintColor = DroneItem.getPaintColor(placementSnapshot);

		Vec3 spawnPos = resolvePlacementPosition(context);
		AABB placementBox = droneBoxAt(spawnPos);
		if (!serverLevel.noCollision(placementBox)) {
			return InteractionResult.FAIL;
		}

		float yRot = player.getYRot();
		Interaction root = new Interaction(EntityType.INTERACTION, serverLevel);
		root.addTag(DRONE_ROOT_TAG);
		root.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		root.setYRot(yRot);
		root.setXRot(0.0F);
		root.setNoGravity(true);
		root.setInvulnerable(true);
		root.setSilent(true);
		root.setResponse(true);
		root.setWidth(DRONE_WIDTH);
		root.setHeight(DRONE_HEIGHT);
		if (droneType != DroneItem.DroneType.NORMAL) {
			root.addTag(DRONE_TYPE_TAG_PREFIX + droneType.name().toLowerCase(java.util.Locale.ROOT));
		}
		if (kamikazePower > DRONE_KAMIKAZE_NO_POWER) {
			root.addTag(droneKamikazePowerTag(kamikazePower));
		}
		if (nightVision) {
			root.addTag(DRONE_NIGHT_VISION_TAG);
		}
		if (autoAim) {
			root.addTag(DRONE_AUTO_AIM_TAG);
		}
		if (paintColor != null) {
			root.addTag(DRONE_PAINT_TAG_PREFIX + paintColor.getName());
		}

		Display.ItemDisplay display = createDroneDisplay(
				serverLevel,
				spawnPos,
				yRot,
				0.0F,
				DroneItem.createDisplayStack(ModItems.DRONE, droneType, kamikazePower, nightVision, autoAim, paintColor),
				DRONE_DISPLAY_LAYER_BASE
		);
		Interaction cameraAnchor = createDroneCameraAnchor(serverLevel, droneCameraOrigin(spawnPos), yRot, 0.0F);
		serverLevel.addFreshEntity(root);
		serverLevel.addFreshEntity(display);
		serverLevel.addFreshEntity(cameraAnchor);
		display.addTag(DRONE_DISPLAY_OWNER_TAG_PREFIX + root.getUUID());
		DISPLAYS_BY_DRONE.put(root.getUUID(), display.getUUID());
		cameraAnchor.addTag(DRONE_CAMERA_OWNER_TAG_PREFIX + root.getUUID());
		CAMERA_ANCHORS_BY_DRONE.put(root.getUUID(), cameraAnchor.getUUID());
		syncDroneDisplayLayers(root);
		syncDroneDisplay(root, yRot, 0.0F, 0.0D, 0.0D);
		UNCONTROLLED_DRONES.put(
				root.getUUID(),
				new UncontrolledDroneState(root.getUUID(), serverLevel.dimension(), Vec3.ZERO, yRot, 0.0F)
		);

		if (!player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	public static void handleInput(ServerPlayer player, Input input) {
		if (player == null || input == null || !isControllingDrone(player)) {
			return;
		}
		INPUTS.put(
				player.getUUID(),
				new DroneInputState(
						input.forward(),
						input.backward(),
						input.left(),
						input.right(),
						input.jump(),
						input.shift(),
						input.sprint()
				)
		);
	}

	public static boolean isControllingDrone(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		return session != null && Objects.equals(resolveAuthoritativeDroneControllerId(session.droneUuid()), player.getUUID());
	}

	public static boolean hasActiveController(UUID droneUuid) {
		return resolveAuthoritativeDroneControllerId(droneUuid) != null;
	}

	public static boolean tryStartControllingDrone(ServerPlayer player, UUID droneUuid, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos fallbackPos) {
		if (player == null || droneUuid == null || dimension == null || player.level() == null || player.level().getServer() == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		UUID currentControllerId = resolveAuthoritativeDroneControllerId(droneUuid);
		if (currentControllerId != null && !Objects.equals(currentControllerId, player.getUUID())) {
			player.sendSystemMessage(Component.literal("Этот дрон уже управляется другим игроком."));
			return false;
		}
		Entity root = findDroneRoot(server, dimension, droneUuid);
		if (root == null || !root.isAlive()) {
			DroneLiveFeedState liveFeedState = resolveLiveFeedState(server, droneUuid, dimension, fallbackPos);
			if (liveFeedState == null || liveFeedState.dimension() == null || liveFeedState.pos() == null) {
				return false;
			}
			queueDroneControlStartPreload(player, liveFeedState);
			return true;
		}
		if (isDroneControlStartAreaReady(root, DRONE_CONTROL_PRELOAD_READY_RADIUS_CHUNKS)) {
			return startControlling(player, root);
		}
		queueDroneControlStartPreload(player, root);
		return true;
	}

	private static boolean isActiveDroneController(UUID controllerId, UUID droneUuid) {
		if (controllerId == null || droneUuid == null) {
			return false;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(controllerId);
		return session != null && Objects.equals(session.droneUuid(), droneUuid);
	}

	private static Set<UUID> collectDroneControllerIds(UUID droneUuid) {
		Set<UUID> controllerIds = new LinkedHashSet<>();
		if (droneUuid == null) {
			return controllerIds;
		}

		UUID indexedControllerId = CONTROLLERS_BY_DRONE.get(droneUuid);
		if (isActiveDroneController(indexedControllerId, droneUuid)) {
			controllerIds.add(indexedControllerId);
		} else if (indexedControllerId != null) {
			CONTROLLERS_BY_DRONE.remove(droneUuid, indexedControllerId);
		}

		for (Map.Entry<UUID, DroneControlSession> entry : ACTIVE_SESSIONS.entrySet()) {
			UUID controllerId = entry.getKey();
			DroneControlSession session = entry.getValue();
			if (controllerId == null || session == null || !Objects.equals(session.droneUuid(), droneUuid)) {
				continue;
			}
			controllerIds.add(controllerId);
		}
		return controllerIds;
	}

	private static UUID resolveAuthoritativeDroneControllerId(UUID droneUuid) {
		if (droneUuid == null) {
			return null;
		}
		Set<UUID> controllerIds = collectDroneControllerIds(droneUuid);
		if (controllerIds.isEmpty()) {
			CONTROLLERS_BY_DRONE.remove(droneUuid);
			return null;
		}

		UUID indexedControllerId = CONTROLLERS_BY_DRONE.get(droneUuid);
		UUID resolvedControllerId = indexedControllerId != null && controllerIds.contains(indexedControllerId)
				? indexedControllerId
				: controllerIds.iterator().next();
		if (!Objects.equals(indexedControllerId, resolvedControllerId)) {
			CONTROLLERS_BY_DRONE.put(droneUuid, resolvedControllerId);
		}
		return resolvedControllerId;
	}

	public static void handleControlledMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
		if (player == null || packet == null) {
			return;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}
		MinecraftServer server = player.level() == null ? null : player.level().getServer();
		Entity root = findDroneRoot(server, session.droneDimension(), session.droneUuid());
		if (root == null || !root.isAlive()) {
			if (shouldKeepControlledSessionWaitingForDroneRoot(server, session)) {
				return;
			}
			stopControlling(player, true);
			return;
		}
		session.refreshKnownDroneLocation(root);
		applyControlledMovePacket(player, root, session, packet);
	}

	public static boolean shouldSuppressPostControlMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
		if (player == null || packet == null) {
			return false;
		}
		Long untilTick = POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.get(player.getUUID());
		if (untilTick == null) {
			return false;
		}
		long now = player.level() == null ? Long.MAX_VALUE : player.level().getGameTime();
		if (now > untilTick) {
			POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.remove(player.getUUID(), untilTick);
			return false;
		}
		if (!packet.hasPosition()) {
			return false;
		}

		Vec3 expected = player.position();
		Vec3 reported = new Vec3(
				packet.getX(expected.x),
				packet.getY(expected.y),
				packet.getZ(expected.z)
		);
		if (!Double.isFinite(reported.x) || !Double.isFinite(reported.y) || !Double.isFinite(reported.z)) {
			return true;
		}
		if (reported.subtract(expected).lengthSqr() > POST_CONTROL_MOVE_ACCEPT_DISTANCE_SQR) {
			return true;
		}

		POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.remove(player.getUUID(), untilTick);
		return false;
	}

	private static void destroyControlledDroneFromImpact(ServerPlayer player, DroneControlSession session, Entity root) {
		if (player == null || session == null || root == null || !root.isAlive()) {
			return;
		}
		destroyDrone(root, null, false);
		if (ACTIVE_SESSIONS.containsKey(player.getUUID())) {
			stopControlling(player, true, false);
		}
	}

	public static boolean handleControlledAcceptTeleportPacket(ServerPlayer player, int teleportId) {
		if (player == null) {
			return false;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return false;
		}
		return teleportId >= CONTROLLED_VIEW_TELEPORT_ID_BASE;
	}

	public static ChunkTrackingView createVirtualChunkTrackingView(ServerPlayer player) {
		if (!isControllingDrone(player)) {
			return ChunkTrackingView.of(player.chunkPosition(), resolveRequestedViewDistance(player));
		}

		Entity root = resolveControlledDroneRoot(player);
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		DroneCameraChunkTarget target = root == null && session != null
				? session.lastKnownCameraTarget()
				: resolveDroneCameraChunkTarget(root);
		return ChunkTrackingView.of(chunkPosAt(target.x(), target.z()), resolveServerViewDistance(player.level().getServer()));
	}

	public static boolean isEntityWithinVirtualTrackingRange(ServerPlayer viewer, Entity entity, double horizontalRangeBlocks) {
		if (!isControllingDrone(viewer)
				|| viewer == null
				|| entity == null
				|| horizontalRangeBlocks <= 0.0D
				|| viewer.level() != entity.level()) {
			return false;
		}

		Entity root = resolveControlledDroneRoot(viewer);
		if (root != null) {
			return isWithinHorizontalRange(root.position(), entity, horizontalRangeBlocks);
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(viewer.getUUID());
		if (session == null || !Objects.equals(session.droneDimension(), entity.level().dimension())) {
			return false;
		}
		return isWithinHorizontalRange(session.lastKnownDronePos(), entity, horizontalRangeBlocks);
	}

	public static boolean isOutgoingControlledOperatorPacketRewriteBypassed() {
		return Boolean.TRUE.equals(CONTROLLED_OPERATOR_PACKET_REWRITE_BYPASS.get());
	}

	public static void runWithControlledOperatorPacketRewriteBypass(Runnable action) {
		if (action == null) {
			return;
		}
		CONTROLLED_OPERATOR_PACKET_REWRITE_BYPASS.set(true);
		try {
			action.run();
		} finally {
			CONTROLLED_OPERATOR_PACKET_REWRITE_BYPASS.remove();
		}
	}

	public static Packet<?> rewriteOutgoingControlledOperatorPacket(ServerPlayer receiver, Packet<?> packet) {
		if (receiver == null || packet == null) {
			return packet;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(receiver.getUUID());
		if (session == null) {
			return packet;
		}

		if (packet instanceof ClientboundBundlePacket bundlePacket) {
			List<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> rewrittenPackets = new ArrayList<>();
			boolean changed = false;
			for (Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> bundledPacket : bundlePacket.subPackets()) {
				Packet<?> rewritten = rewriteOutgoingControlledOperatorPacket(receiver, (Packet<?>) bundledPacket);
				if (rewritten != null) {
					rewrittenPackets.add((Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) rewritten);
				}
				if (rewritten != bundledPacket) {
					changed = true;
				}
			}
			return changed ? (rewrittenPackets.isEmpty() ? null : new ClientboundBundlePacket(rewrittenPackets)) : packet;
		}

		if (packet instanceof ClientboundPlayerPositionPacket playerPositionPacket) {
			return buildControlledPlayerPositionPacket(session, playerPositionPacket.id());
		}

		if (packet instanceof ClientboundSetEntityMotionPacket entityMotionPacket
				&& entityMotionPacket.getId() == receiver.getId()) {
			return null;
		}

		if (packet instanceof ClientboundTeleportEntityPacket entityTeleportPacket
				&& entityTeleportPacket.id() == receiver.getId()) {
			return buildControlledSelfTeleportPacket(receiver, session);
		}

		if (packet instanceof ClientboundSetEntityDataPacket entityDataPacket
				&& entityDataPacket.id() == receiver.getId()) {
			return buildControlledSelfMetadataPacket(receiver);
		}

		if (packet instanceof ClientboundSetPassengersPacket passengersPacket
				&& passengersPacket.getVehicle() == receiver.getId()) {
			return buildControlledOperatorPassengerPacket(receiver, session);
		}
		if (packet instanceof ClientboundSetPassengersPacket passengersPacket) {
			Entity root = resolveControlledDroneRoot(receiver);
			if (root != null && passengersPacket.getVehicle() == root.getId()) {
				return buildControlledOperatorDroneLayerPassengerPacket(root);
			}
		}

		if (packet instanceof ClientboundContainerSetSlotPacket slotPacket
				&& slotPacket.getContainerId() == receiver.inventoryMenu.containerId
				&& isControlledHotbarSlot(slotPacket.getSlot())) {
			return new ClientboundContainerSetSlotPacket(
					slotPacket.getContainerId(),
					slotPacket.getStateId(),
					slotPacket.getSlot(),
					ItemStack.EMPTY
			);
		}

		return packet;
	}

	public static boolean isCameraBlockedByDroneControl(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		return isControllingDrone(player) || VISUALLY_CONTROLLED_PLAYERS.contains(player.getUUID());
	}

	public static BluetoothLinkSystem.Endpoint resolveBluetoothDroneEndpoint(ServerLevel level, Entity entity) {
		if (level == null || entity == null) {
			return null;
		}
		Entity root = resolveDroneRoot(entity);
		if (root == null || !root.isAlive() || root.level() != level) {
			return null;
		}
		return BluetoothLinkSystem.droneEndpoint(level.dimension(), root.blockPosition(), root.getUUID());
	}

	public static List<ServerSelectionHighlightSystem.DisplayBlueprint> resolveBluetoothDroneHighlightBlueprints(ServerLevel level, BluetoothLinkSystem.Endpoint endpoint) {
		if (level == null || endpoint == null || endpoint.type() != BluetoothLinkSystem.EndpointType.DRONE) {
			return List.of();
		}
		Entity root = endpoint.deviceUuid() == null ? null : findDroneRoot(level.getServer(), level.dimension(), endpoint.deviceUuid());
		if (root == null || !root.isAlive() || root.level() != level) {
			return List.of();
		}
		Entity highlightEntity = findDroneDisplay(root);
		if (highlightEntity == null || !highlightEntity.isAlive()) {
			highlightEntity = root;
		}
		return List.of(new ServerSelectionHighlightSystem.EntityGlowBlueprint(highlightEntity));
	}

	public static DroneLiveFeedState resolveLiveFeedState(MinecraftServer server, BluetoothLinkSystem.Endpoint endpoint) {
		if (endpoint == null || endpoint.type() != BluetoothLinkSystem.EndpointType.DRONE) {
			return null;
		}
		return resolveLiveFeedState(server, endpoint.deviceUuid(), endpoint.dimension(), endpoint.pos());
	}

	public static DroneLiveFeedState resolveLiveFeedState(
			MinecraftServer server,
			UUID droneUuid,
			net.minecraft.resources.ResourceKey<Level> fallbackDimension,
			BlockPos fallbackPos
	) {
		if (server == null || droneUuid == null) {
			return null;
		}
		Entity root = fallbackDimension == null ? findDroneRoot(server, droneUuid) : findDroneRoot(server, fallbackDimension, droneUuid);
		if (root == null || !root.isAlive() || !(root.level() instanceof ServerLevel droneLevel)) {
			net.minecraft.resources.ResourceKey<Level> dimension = fallbackDimension == null ? Level.OVERWORLD : fallbackDimension;
			BlockPos pos = fallbackPos == null ? BlockPos.ZERO : fallbackPos.immutable();
			DroneScreenStreamLoadState loadState = SCREEN_STREAM_DRONE_LOAD_STATES.get(droneUuid);
			if (loadState != null) {
				Vec3 lastDronePos = loadState.dronePos() == null ? Vec3.atCenterOf(pos) : loadState.dronePos();
				Vec3 lastCameraPos = loadState.cameraPos() == null ? droneCameraOrigin(lastDronePos) : loadState.cameraPos();
				return new DroneLiveFeedState(
						droneUuid,
						loadState.dimension() == null ? dimension : loadState.dimension(),
						BlockPos.containing(lastDronePos.x, lastDronePos.y, lastDronePos.z),
						true,
						lastCameraPos.x,
						lastCameraPos.y,
						lastCameraPos.z,
						loadState.cameraYaw(),
						loadState.cameraPitch(),
						null,
						Set.of(),
						true,
						null
				);
			}
			DroneScreenStreamLoadState lastKnownState = LAST_KNOWN_DRONE_FEED_STATES.get(droneUuid);
			if (lastKnownState != null) {
				Vec3 lastDronePos = lastKnownState.dronePos() == null ? Vec3.atCenterOf(pos) : lastKnownState.dronePos();
				Vec3 lastCameraPos = lastKnownState.cameraPos() == null ? droneCameraOrigin(lastDronePos) : lastKnownState.cameraPos();
				return new DroneLiveFeedState(
						droneUuid,
						lastKnownState.dimension() == null ? dimension : lastKnownState.dimension(),
						BlockPos.containing(lastDronePos.x, lastDronePos.y, lastDronePos.z),
						false,
						lastCameraPos.x,
						lastCameraPos.y,
						lastCameraPos.z,
						lastKnownState.cameraYaw(),
						lastKnownState.cameraPitch(),
						null,
						Set.of(),
						true,
						null
				);
			}
			return new DroneLiveFeedState(
					droneUuid,
					dimension,
					pos,
					false,
					pos.getX() + 0.5D,
					pos.getY(),
					pos.getZ() + 0.5D,
					0.0F,
					0.0F,
					null,
					Set.of(),
					true,
					null
			);
		}
		rememberDroneScreenStreamLoadState(root);
		UUID controllerId = resolveAuthoritativeDroneControllerId(root.getUUID());
		ServerPlayer controller = controllerId == null ? null : server.getPlayerList().getPlayer(controllerId);
		Entity cameraAnchor = ensureDroneCameraAnchor(root);
		Vec3 cameraOrigin = cameraAnchor != null ? cameraAnchor.position() : resolveDroneCameraPosition(root);
		float cameraYaw = cameraAnchor != null ? cameraAnchor.getYRot() : resolveDroneCameraYaw(root);
		float cameraPitch = cameraAnchor != null ? cameraAnchor.getXRot() : resolveDroneCameraPitch(root);
		UUID cameraAnchorUuid = cameraAnchor != null ? cameraAnchor.getUUID() : null;
		Set<UUID> hiddenEntities = hiddenDroneCameraEntityUuids(root, null);
		if (controller != null && ACTIVE_SESSIONS.containsKey(controller.getUUID())) {
			DroneControlSession session = ACTIVE_SESSIONS.get(controller.getUUID());
			if (session != null
					&& Objects.equals(session.droneUuid(), root.getUUID())
					&& controller.level() == droneLevel) {
				return new DroneLiveFeedState(
						root.getUUID(),
						droneLevel.dimension(),
						root.blockPosition(),
						true,
						cameraOrigin.x,
						cameraOrigin.y,
						cameraOrigin.z,
						cameraYaw,
						cameraPitch,
						cameraAnchorUuid,
						hiddenEntities,
						true,
						controller.getScoreboardName()
				);
			}
		}
		return new DroneLiveFeedState(
				root.getUUID(),
				droneLevel.dimension(),
				root.blockPosition(),
				true,
				cameraOrigin.x,
				cameraOrigin.y,
				cameraOrigin.z,
				cameraYaw,
				cameraPitch,
				cameraAnchorUuid,
				hiddenEntities,
				true,
				null
		);
	}

	private static Set<UUID> hiddenDroneCameraEntityUuids(Entity root, ServerPlayer controller) {
		if (root == null) {
			return Set.of();
		}
		Set<UUID> hidden = new LinkedHashSet<>();
		hidden.add(root.getUUID());
		UUID baseDisplayId = DISPLAYS_BY_DRONE.get(root.getUUID());
		if (baseDisplayId != null) {
			hidden.add(baseDisplayId);
		}
		for (Display.ItemDisplay display : findDroneDisplayLayers(root)) {
			if (display != null) {
				hidden.add(display.getUUID());
			}
		}
		return Set.copyOf(hidden);
	}

	private static void applyControlledMovePacket(
			ServerPlayer player,
			Entity root,
			DroneControlSession session,
			ServerboundMovePlayerPacket packet
	) {
		if (player == null || root == null || session == null || packet == null) {
			return;
		}
		Vec3 previousPos = session.proxyPos();
		if (previousPos == null) {
			previousPos = root.position();
		}
		float previousYaw = session.proxyYaw();
		float previousPitch = session.proxyPitch();
		float yaw = packet.hasRotation() ? packet.getYRot(session.controlYaw()) : session.controlYaw();
		float pitch = packet.hasRotation()
				? net.minecraft.util.Mth.clamp(packet.getXRot(session.controlPitch()), -90.0F, 90.0F)
				: session.controlPitch();
		if (packet.hasRotation()) {
			session.recordManualLookDelta(
					net.minecraft.util.Mth.wrapDegrees(yaw - previousYaw),
					pitch - previousPitch,
					root.level() == null ? Long.MIN_VALUE : root.level().getGameTime()
			);
		}
		session.setControlYaw(yaw);
		session.setControlPitch(pitch);
		session.setProxyYaw(yaw);
		session.setProxyPitch(pitch);

		if (!packet.hasPosition()) {
			root.setYRot(yaw);
			root.setXRot(pitch);
			syncControlledDronePresentation(player, root, session);
			session.refreshKnownDroneLocation(root);
			return;
		}
		Vec3 reportedPos = new Vec3(
				packet.getX(previousPos.x),
				packet.getY(previousPos.y),
				packet.getZ(previousPos.z)
		);
		if (!Double.isFinite(reportedPos.x) || !Double.isFinite(reportedPos.y) || !Double.isFinite(reportedPos.z)) {
			stopControlling(player, true);
			return;
		}
		Vec3 actualVelocity = reportedPos.subtract(previousPos);
		Vec3 intendedMovement = DroneFlightPhysics.step(
				pitch,
				yaw,
				session.forwardDrive(),
				session.strafeDrive()
		);
		if (!isPlausibleControlledDroneMove(actualVelocity, intendedMovement)) {
			Vec3 safeVelocity = finiteVecOr(session.velocity(), Vec3.ZERO);
			if (!isPlausibleControlledDroneMove(safeVelocity, intendedMovement)) {
				safeVelocity = Vec3.ZERO;
			}
			session.setProxyPos(previousPos);
			session.setIntendedVelocity(intendedMovement);
			session.setVelocity(safeVelocity);
			prepareControlledDroneBody(root);
			root.setPos(previousPos.x, previousPos.y, previousPos.z);
			root.setBoundingBox(droneBoxAt(previousPos));
			root.setYRot(yaw);
			root.setXRot(pitch);
			root.setDeltaMovement(safeVelocity);
			root.hurtMarked = true;
			syncControlledDronePresentation(player, root, session);
			syncControlledOperatorView(player, session, root, false, true);
			session.refreshKnownDroneLocation(root);
			return;
		}

		session.setProxyPos(reportedPos);
		session.setIntendedVelocity(intendedMovement);
		session.setVelocity(actualVelocity);

		ControlledCollisionState collisionState = resolveControlledCollisionState(
				root,
				previousPos,
				intendedMovement,
				actualVelocity
		);

		prepareControlledDroneBody(root);
		root.setPos(reportedPos.x, reportedPos.y, reportedPos.z);
		root.setBoundingBox(droneBoxAt(reportedPos));
		root.setYRot(yaw);
		root.setXRot(pitch);
		root.setDeltaMovement(actualVelocity);
		root.horizontalCollision = collisionState.horizontalCollision();
		root.verticalCollision = collisionState.verticalCollision();
		root.verticalCollisionBelow = collisionState.verticalCollisionBelow();
		root.hurtMarked = true;

		if (handleControlledServerCollision(player, root, session, intendedMovement, actualVelocity)) {
			return;
		}

		syncControlledDronePresentation(player, root, session);
		session.refreshKnownDroneLocation(root);
	}

	private static void prepareControlledDroneBody(Entity root) {
		if (root == null) {
			return;
		}
		root.setNoGravity(true);
		root.noPhysics = false;
		root.fallDistance = 0.0F;
		root.setBoundingBox(droneBoxAt(root.position()));
	}

	public static boolean isDroneEntity(Entity entity) {
		return resolveDroneRoot(entity) != null;
	}

	public static String requiredUpgradeForDroneEntity(Entity entity) {
		Entity root = resolveDroneRoot(entity);
		return root == null ? null : resolveRequiredUpgradeForDroneRoot(root);
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		updateDroneChunkTickets(server);
		tickPendingControlStarts(server);
		tickControlledSessions(server);
		tickUncontrolledDrones(server);
		updateDroneChunkTickets(server);
		cleanupExpiredPostControlMoveSuppression(server);
		recoverOrphanedControlledOperators(server);
		recoverPlayersWithStaleDronePassenger(server);
		processPendingPostControlClientResync(server);
	}

	private static void updateDroneChunkTickets(MinecraftServer server) {
		if (server == null) {
			return;
		}

		Map<DroneChunkTicketKey, Integer> desiredRefs = new HashMap<>();
		Set<UUID> activeScreenStreamDrones = new HashSet<>();
		for (DroneScreenStreamReference screenStream : MonitorScreenSystem.collectActiveDroneScreenStreams(server)) {
			if (screenStream == null || screenStream.droneUuid() == null) {
				continue;
			}
			activeScreenStreamDrones.add(screenStream.droneUuid());
			Entity root = findDroneRoot(server, screenStream.dimension(), screenStream.droneUuid());
			DroneScreenStreamLoadState loadState;
			if (root != null && root.isAlive()) {
				loadState = rememberDroneScreenStreamLoadState(root);
			} else {
				loadState = SCREEN_STREAM_DRONE_LOAD_STATES.computeIfAbsent(
						screenStream.droneUuid(),
						ignored -> createDroneScreenStreamLoadState(screenStream)
				);
			}
			addDroneChunkTickets(server, desiredRefs, loadState);
		}
		SCREEN_STREAM_DRONE_LOAD_STATES.keySet().removeIf(droneUuid -> !activeScreenStreamDrones.contains(droneUuid));

		for (Map.Entry<UUID, DroneControlSession> entry : ACTIVE_SESSIONS.entrySet()) {
			DroneControlSession session = entry.getValue();
			if (session == null) {
				continue;
			}
			Entity root = findDroneRoot(server, session.droneDimension(), session.droneUuid());
			ServerPlayer controller = server.getPlayerList().getPlayer(entry.getKey());
			if (controller == null || !controller.isAlive() || controller.isSpectator()) {
				continue;
			}
			addControlledOperatorBodyChunkTickets(server, desiredRefs, controller);
			if (root != null && root.isAlive()) {
				session.refreshKnownDroneLocation(root);
				addDroneChunkTickets(server, desiredRefs, root);
			} else {
				addDroneChunkTickets(server, desiredRefs, session);
			}
		}

		for (PendingDroneControlStart pending : PENDING_CONTROL_STARTS.values()) {
			if (pending == null) {
				continue;
			}
			Entity root = findDroneRoot(server, pending.droneDimension(), pending.droneUuid());
			if (root != null && root.isAlive()) {
				addDroneChunkTickets(server, desiredRefs, root);
				continue;
			}
			DroneLiveFeedState liveFeedState = resolveLiveFeedState(server, pending.droneUuid(), pending.droneDimension(), pending.fallbackPos());
			addDroneChunkTickets(server, desiredRefs, liveFeedState);
		}

		for (UncontrolledDroneState state : UNCONTROLLED_DRONES.values()) {
			if (state == null) {
				continue;
			}
			Entity root = findDroneRoot(server, state.dimension(), state.droneUuid());
			if (root == null || !root.isAlive() || !isDroneStreamingToScreen(root)) {
				continue;
			}
			rememberDroneScreenStreamLoadState(root);
			addDroneChunkTickets(server, desiredRefs, root);
		}

		syncDroneChunkTickets(server, desiredRefs);
	}

	private static void addControlledOperatorBodyChunkTickets(MinecraftServer server, Map<DroneChunkTicketKey, Integer> desiredRefs, ServerPlayer player) {
		if (server == null || desiredRefs == null || player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		ChunkPos bodyCenter = player.chunkPosition();
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), bodyCenter.toLong(), resolveServerViewDistance(server), false),
				1,
				Integer::sum
		);
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), bodyCenter.toLong(), resolveServerSimulationTicketRadius(server), true),
				1,
				Integer::sum
		);
	}

	private static void addDroneChunkTickets(MinecraftServer server, Map<DroneChunkTicketKey, Integer> desiredRefs, Entity root) {
		if (desiredRefs == null || root == null || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		if (isDroneActivelyControlled(root)) {
			UUID controllerId = resolveAuthoritativeDroneControllerId(root.getUUID());
			DroneControlSession session = controllerId == null ? null : ACTIVE_SESSIONS.get(controllerId);
			if (session != null) {
				session.refreshKnownDroneLocation(root);
			}
		}
		DroneCameraChunkTarget target = resolveDroneCameraChunkTarget(root);
		ChunkPos loadingCenter = chunkPosAt(target.x(), target.z());
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), loadingCenter.toLong(), resolveServerViewDistance(server), false),
				1,
				Integer::sum
		);
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), root.chunkPosition().toLong(), resolveServerSimulationTicketRadius(server), true),
				1,
				Integer::sum
		);
	}

	private static void addDroneChunkTickets(MinecraftServer server, Map<DroneChunkTicketKey, Integer> desiredRefs, DroneControlSession session) {
		if (server == null || desiredRefs == null || session == null || session.droneDimension() == null) {
			return;
		}
		ServerLevel level = server.getLevel(session.droneDimension());
		if (level == null) {
			return;
		}
		DroneCameraChunkTarget target = session.lastKnownCameraTarget();
		ChunkPos loadingCenter = chunkPosAt(target.x(), target.z());
		Vec3 dronePos = session.lastKnownDronePos();
		ChunkPos simulationCenter = chunkPosAt(dronePos.x, dronePos.z);
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), loadingCenter.toLong(), resolveServerViewDistance(server), false),
				1,
				Integer::sum
		);
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), simulationCenter.toLong(), resolveServerSimulationTicketRadius(server), true),
				1,
				Integer::sum
		);
	}

	private static void addDroneChunkTickets(MinecraftServer server, Map<DroneChunkTicketKey, Integer> desiredRefs, DroneScreenStreamLoadState state) {
		if (server == null || desiredRefs == null || state == null || state.dimension() == null) {
			return;
		}
		ServerLevel level = server.getLevel(state.dimension());
		if (level == null) {
			return;
		}
		DroneCameraChunkTarget target = state.cameraTarget();
		ChunkPos loadingCenter = chunkPosAt(target.x(), target.z());
		Vec3 dronePos = state.dronePos() == null ? Vec3.ZERO : state.dronePos();
		ChunkPos simulationCenter = chunkPosAt(dronePos.x, dronePos.z);
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), loadingCenter.toLong(), resolveServerViewDistance(server), false),
				1,
				Integer::sum
		);
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), simulationCenter.toLong(), resolveServerSimulationTicketRadius(server), true),
				1,
					Integer::sum
			);
	}

	private static void addDroneChunkTickets(MinecraftServer server, Map<DroneChunkTicketKey, Integer> desiredRefs, DroneLiveFeedState state) {
		if (server == null || desiredRefs == null || state == null || state.dimension() == null || state.pos() == null) {
			return;
		}
		ServerLevel level = server.getLevel(state.dimension());
		if (level == null) {
			return;
		}
		ChunkPos loadingCenter = chunkPosAt(state.expectedX(), state.expectedZ());
		ChunkPos simulationCenter = new ChunkPos(state.pos());
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), loadingCenter.toLong(), resolveServerViewDistance(server), false),
				1,
				Integer::sum
		);
		desiredRefs.merge(
				new DroneChunkTicketKey(level.dimension(), simulationCenter.toLong(), resolveServerSimulationTicketRadius(server), true),
				1,
				Integer::sum
		);
	}

	private static DroneScreenStreamLoadState rememberDroneScreenStreamLoadState(Entity root) {
		if (root == null || !(root.level() instanceof ServerLevel level)) {
			return null;
		}
		DroneScreenStreamLoadState state = new DroneScreenStreamLoadState(
				root.getUUID(),
				level.dimension(),
				root.position(),
				resolveDroneCameraPosition(root),
				resolveDroneCameraYaw(root),
				resolveDroneCameraPitch(root)
		);
		SCREEN_STREAM_DRONE_LOAD_STATES.put(root.getUUID(), state);
		LAST_KNOWN_DRONE_FEED_STATES.put(root.getUUID(), state);
		return state;
	}

	private static DroneScreenStreamLoadState createDroneScreenStreamLoadState(DroneScreenStreamReference reference) {
		if (reference == null || reference.droneUuid() == null) {
			return null;
		}
		net.minecraft.resources.ResourceKey<Level> dimension = reference.dimension() == null ? Level.OVERWORLD : reference.dimension();
		BlockPos pos = reference.pos() == null ? BlockPos.ZERO : reference.pos();
		Vec3 dronePos = Vec3.atCenterOf(pos);
		return new DroneScreenStreamLoadState(
				reference.droneUuid(),
				dimension,
				dronePos,
				droneCameraOrigin(dronePos),
				0.0F,
				0.0F
		);
	}

	private static void syncDroneChunkTickets(MinecraftServer server, Map<DroneChunkTicketKey, Integer> desiredRefs) {
		if (server == null || desiredRefs == null) {
			return;
		}
		if (Objects.equals(ACTIVE_DRONE_CHUNK_TICKETS, desiredRefs)) {
			return;
		}

		for (DroneChunkTicketKey key : desiredRefs.keySet()) {
			if (ACTIVE_DRONE_CHUNK_TICKETS.getOrDefault(key, 0) > 0) {
				continue;
			}
			ServerLevel level = server.getLevel(key.dimension());
			if (level != null) {
				level.getChunkSource().addTicketWithRadius(droneChunkTicketType(key), new ChunkPos(key.chunkLong()), key.radius());
			}
		}
		for (DroneChunkTicketKey key : ACTIVE_DRONE_CHUNK_TICKETS.keySet()) {
			if (desiredRefs.getOrDefault(key, 0) > 0) {
				continue;
			}
			ServerLevel level = server.getLevel(key.dimension());
			if (level != null) {
				level.getChunkSource().removeTicketWithRadius(droneChunkTicketType(key), new ChunkPos(key.chunkLong()), key.radius());
			}
		}
		ACTIVE_DRONE_CHUNK_TICKETS.clear();
		ACTIVE_DRONE_CHUNK_TICKETS.putAll(desiredRefs);
	}

	private static void releaseAllDroneChunkTickets(MinecraftServer server) {
		if (server == null || ACTIVE_DRONE_CHUNK_TICKETS.isEmpty()) {
			ACTIVE_DRONE_CHUNK_TICKETS.clear();
			return;
		}
		for (DroneChunkTicketKey key : new ArrayList<>(ACTIVE_DRONE_CHUNK_TICKETS.keySet())) {
			ServerLevel level = server.getLevel(key.dimension());
			if (level != null) {
				level.getChunkSource().removeTicketWithRadius(droneChunkTicketType(key), new ChunkPos(key.chunkLong()), key.radius());
			}
		}
		ACTIVE_DRONE_CHUNK_TICKETS.clear();
	}

	private static TicketType droneChunkTicketType(DroneChunkTicketKey key) {
		return key != null && key.simulation() ? DRONE_SIMULATION_CHUNK_TICKET_TYPE : DRONE_LOADING_CHUNK_TICKET_TYPE;
	}

	private static void cleanupExpiredPostControlMoveSuppression(MinecraftServer server) {
		if (server == null || POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.isEmpty()) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long now = overworld == null ? Long.MAX_VALUE : overworld.getGameTime();
		for (Map.Entry<UUID, Long> entry : new ArrayList<>(POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.entrySet())) {
			Long untilTick = entry.getValue();
			if (untilTick == null || now > untilTick) {
				POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.remove(entry.getKey(), untilTick);
			}
		}
	}

	private static void recoverOrphanedControlledOperators(MinecraftServer server) {
		if (server == null || VISUALLY_CONTROLLED_PLAYERS.isEmpty()) {
			return;
		}
		for (UUID playerId : new ArrayList<>(VISUALLY_CONTROLLED_PLAYERS)) {
			if (playerId == null || ACTIVE_SESSIONS.containsKey(playerId)) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				VISUALLY_CONTROLLED_PLAYERS.remove(playerId);
				continue;
			}
			restoreOrphanedControlledOperator(player);
		}
	}

	private static void recoverPlayersWithStaleDronePassenger(MinecraftServer server) {
		if (server == null || server.getPlayerList() == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null || ACTIVE_SESSIONS.containsKey(player.getUUID())) {
				continue;
			}
			if (VISUALLY_CONTROLLED_PLAYERS.contains(player.getUUID())) {
				restoreOrphanedControlledOperator(player);
				continue;
			}
			boolean hasDronePassenger = false;
			for (Entity passenger : new ArrayList<>(player.getPassengers())) {
				if (resolveDroneRoot(passenger) != null) {
					hasDronePassenger = true;
					break;
				}
			}
			if (!hasDronePassenger) {
				continue;
			}
			restoreOrphanedControlledOperator(player);
		}
	}

	private static void restoreOrphanedControlledOperator(ServerPlayer player) {
		if (player == null) {
			return;
		}
		VISUALLY_CONTROLLED_PLAYERS.remove(player.getUUID());
		clearControlledOperatorTransientState(player, null);
		removeControlledOperatorBodyMirror(player);
		detachAnyDronePassengersFromController(player);
		clearControlledOperatorMovementState(player);
		markPostControlMoveSuppressedForPlayer(player);
		restoreControlledOperatorClientState(player);
		schedulePostControlClientResync(player);
	}

	private static void markPostControlMoveSuppressedForPlayer(ServerPlayer player) {
		if (player == null || player.level() == null) {
			return;
		}
		POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.put(
				player.getUUID(),
				player.level().getGameTime() + POST_CONTROL_MOVE_PACKET_SUPPRESSION_TICKS
		);
	}

	private static void schedulePostControlClientResync(ServerPlayer player) {
		if (player == null || player.level() == null) {
			return;
		}
		POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.put(
				player.getUUID(),
				player.level().getGameTime() + POST_CONTROL_CLIENT_RESYNC_TICKS
		);
	}

	private static void processPendingPostControlClientResync(MinecraftServer server) {
		if (server == null || POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.isEmpty() || server.getPlayerList() == null) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long now = overworld == null ? Long.MAX_VALUE : overworld.getGameTime();
		for (Map.Entry<UUID, Long> entry : new ArrayList<>(POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.entrySet())) {
			UUID playerId = entry.getKey();
			Long untilTick = entry.getValue();
			if (playerId == null || untilTick == null || now > untilTick) {
				POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.remove(playerId, untilTick);
				continue;
			}
			if (ACTIVE_SESSIONS.containsKey(playerId)) {
				POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.remove(playerId, untilTick);
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.remove(playerId, untilTick);
				continue;
			}
			restoreControlledOperatorClientState(player, false);
		}
	}

	private static void tickControlledSessions(MinecraftServer server) {
		if (server == null || ACTIVE_SESSIONS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, DroneControlSession> entry : new ArrayList<>(ACTIVE_SESSIONS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			DroneControlSession session = entry.getValue();
			if (player == null || session == null) {
				continue;
			}

			Entity root = findDroneRoot(server, session.droneDimension(), session.droneUuid());
			if (root == null || !root.isAlive()) {
				removeControlledDroneTurretAirTrigger(player);
				if (shouldKeepControlledSessionWaitingForDroneRoot(server, session)) {
					syncControlledOperatorFallbackView(player, session);
					syncControlledOperatorBodyMirror(player, false);
					updateDroneHud(player, session, false);
					continue;
				}
				stopControlling(player, true);
				continue;
			}
			session.refreshKnownDroneLocation(root);
			if (!player.isAlive() || player.isSpectator()) {
				stopControlling(player, false);
				continue;
			}
			if (!Objects.equals(resolveAuthoritativeDroneControllerId(session.droneUuid()), player.getUUID())) {
				stopControlling(player, false);
				continue;
			}

			DroneInputState input = INPUTS.getOrDefault(player.getUUID(), DroneInputState.EMPTY);
			if (input.shift()) {
				stopControlling(player, true);
				continue;
			}
			tickControlledOperatorBodyPhysics(player);
			updateControlledDrives(player, session, input);
			tickControlledDrone(player, root, session);
			handleControlledTurretJumpInput(player, root, session, input);
			syncControlledDroneTurretAirTrigger(player, root);
		}
	}

	private static void tickControlledOperatorBodyPhysics(ServerPlayer player) {
		if (player == null
				|| !player.isAlive()
				|| player.isSpectator()
				|| !(player.level() instanceof ServerLevel level)) {
			return;
		}
		level.getChunkAt(player.blockPosition());
		Vec3 before = player.position();
		Vec3 beforeVelocity = player.getDeltaMovement();
		player.travel(Vec3.ZERO);
		if (!before.equals(player.position()) || !beforeVelocity.equals(player.getDeltaMovement())) {
			player.hurtMarked = true;
		}
	}

	private static boolean shouldKeepControlledSessionWaitingForDroneRoot(MinecraftServer server, DroneControlSession session) {
		if (server == null || session == null) {
			return false;
		}
		if (SCREEN_STREAM_DRONE_LOAD_STATES.containsKey(session.droneUuid())) {
			return true;
		}
		long now = controlledSessionGameTime(server, session);
		return session.markDroneRootMissing(now);
	}

	private static long controlledSessionGameTime(MinecraftServer server, DroneControlSession session) {
		if (server == null) {
			return Long.MAX_VALUE;
		}
		ServerLevel level = session == null || session.droneDimension() == null ? null : server.getLevel(session.droneDimension());
		if (level == null) {
			level = server.overworld();
		}
		return level == null ? Long.MAX_VALUE : level.getGameTime();
	}

	private static void queueDroneControlStartPreload(ServerPlayer player, Entity root) {
		if (player == null || root == null || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		PENDING_CONTROL_STARTS.put(
				player.getUUID(),
				new PendingDroneControlStart(root.getUUID(), level.dimension(), root.blockPosition(), level.getGameTime())
		);
		updateDroneChunkTickets(level.getServer());
		player.sendSystemMessage(Component.literal("Подготавливаю связь с дроном..."));
	}

	private static void queueDroneControlStartPreload(ServerPlayer player, DroneLiveFeedState state) {
		if (player == null || state == null || state.droneUuid() == null || state.dimension() == null || state.pos() == null) {
			return;
		}
		MinecraftServer server = player.level() == null ? null : player.level().getServer();
		if (server == null) {
			return;
		}
		ServerLevel level = server.getLevel(state.dimension());
		long startedAtTick = level != null ? level.getGameTime() : server.getTickCount();
		PENDING_CONTROL_STARTS.put(
				player.getUUID(),
				new PendingDroneControlStart(state.droneUuid(), state.dimension(), state.pos(), startedAtTick)
		);
		updateDroneChunkTickets(server);
		player.sendSystemMessage(Component.literal("Подготавливаю связь с дроном..."));
	}

	private static void tickPendingControlStarts(MinecraftServer server) {
		if (server == null || PENDING_CONTROL_STARTS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, PendingDroneControlStart> entry : new ArrayList<>(PENDING_CONTROL_STARTS.entrySet())) {
			UUID playerUuid = entry.getKey();
			PendingDroneControlStart pending = entry.getValue();
			ServerPlayer player = playerUuid == null ? null : server.getPlayerList().getPlayer(playerUuid);
			if (player == null || pending == null || pending.droneDimension() == null || pending.droneUuid() == null) {
				PENDING_CONTROL_STARTS.remove(playerUuid);
				continue;
			}
			if (!player.isAlive() || player.isSpectator()) {
				PENDING_CONTROL_STARTS.remove(playerUuid);
				continue;
			}
			UUID currentControllerId = resolveAuthoritativeDroneControllerId(pending.droneUuid());
			if (currentControllerId != null && !Objects.equals(currentControllerId, player.getUUID())) {
				PENDING_CONTROL_STARTS.remove(playerUuid);
				player.sendSystemMessage(Component.literal("Этот дрон уже управляется другим игроком."));
				continue;
			}
			ServerLevel pendingLevel = server.getLevel(pending.droneDimension());
			long now = pendingLevel != null ? pendingLevel.getGameTime() : server.getTickCount();
			boolean timedOut = now - pending.startedAtTick() >= DRONE_CONTROL_PRELOAD_TIMEOUT_TICKS;
			Entity root = findDroneRoot(server, pending.droneDimension(), pending.droneUuid());
			if (root == null || !root.isAlive()) {
				if (!timedOut) {
					continue;
				}
				PENDING_CONTROL_STARTS.remove(playerUuid);
				player.sendSystemMessage(Component.literal("Дрон недоступен."));
				continue;
			}
			boolean ready = isDroneControlStartAreaReady(root, DRONE_CONTROL_PRELOAD_READY_RADIUS_CHUNKS);
			if (!ready && !(timedOut && isDroneControlStartAreaReady(root, 0))) {
				continue;
			}
			PENDING_CONTROL_STARTS.remove(playerUuid);
			startControlling(player, root);
		}
	}

	private static boolean isDroneControlStartAreaReady(Entity root, int radiusChunks) {
		if (root == null || !(root.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 cameraPos = resolveDroneCameraPosition(root);
		ChunkPos center = chunkPosAt(cameraPos.x, cameraPos.z);
		int radius = Math.max(0, radiusChunks);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (level.getChunkSource().getChunkNow(center.x + dx, center.z + dz) == null) {
					return false;
				}
			}
		}
		return level.getChunkSource().getChunkNow(root.chunkPosition().x, root.chunkPosition().z) != null;
	}

	private static void tickUncontrolledDrones(MinecraftServer server) {
		if (server == null || UNCONTROLLED_DRONES.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, UncontrolledDroneState> entry : new ArrayList<>(UNCONTROLLED_DRONES.entrySet())) {
			UncontrolledDroneState state = entry.getValue();
			if (state == null || state.dimension() == null) {
				UNCONTROLLED_DRONES.remove(entry.getKey());
				continue;
			}
			Entity root = findDroneRoot(server, state.dimension(), state.droneUuid());
			if (root == null || !root.isAlive()) {
				UNCONTROLLED_DRONES.remove(entry.getKey());
				continue;
			}

			// If someone is actively controlling the drone, controlled physics owns the root body.
			if (isDroneActivelyControlled(root)) {
				UNCONTROLLED_DRONES.remove(entry.getKey());
				continue;
			}

			tickUncontrolledDrone(root, state);
		}
	}

	private static void tickControlledDrone(ServerPlayer player, Entity root, DroneControlSession session) {
		if (!(root.level() instanceof ServerLevel)) {
			return;
		}
		boolean autoAimAdjustedView = syncControlledOperatorAutoAim(player, session, root);
		session.setIntendedVelocity(DroneFlightPhysics.step(
				session.controlPitch(),
				session.controlYaw(),
				session.forwardDrive(),
				session.strafeDrive()
		));
		decayControlledDroneSurfaceWear(session, root.level().getGameTime());
		syncControlledDronePresentation(player, root, session);
		syncControlledOperatorView(player, session, root, false, autoAimAdjustedView);
		syncControlledOperatorBodyMirror(player, false);
		syncControlledOperatorNightVision(player, session, root);
		updateDroneHud(player, session, false);
	}

	private static void updateControlledDrives(ServerPlayer player, DroneControlSession session, DroneInputState input) {
		if (player == null || session == null) {
			return;
		}
		DroneInputState controlInput = input == null ? DroneInputState.EMPTY : input;
		double driveStep = getControlDriveStep(getControlSpeedSlot(player));
		session.setForwardDrive(DroneFlightPhysics.adjustDrive(
				session.forwardDrive(),
				controlInput.forward(),
				controlInput.backward(),
				driveStep,
				DroneFlightPhysics.MAX_FORWARD_DRIVE
		));
		session.setStrafeDrive(DroneFlightPhysics.adjustDrive(
				session.strafeDrive(),
				controlInput.right(),
				controlInput.left(),
				driveStep,
				DroneFlightPhysics.MAX_STRAFE_DRIVE
		));
	}

	private static void seedControlledDrivesFromWorldVelocity(
			DroneControlSession session,
			Vec3 worldVelocity,
			float yaw,
			float pitch
	) {
		if (session == null || worldVelocity == null) {
			return;
		}
		Vec3 horizontalVelocity = new Vec3(worldVelocity.x, 0.0D, worldVelocity.z);
		if (horizontalVelocity.lengthSqr() <= 1.0E-6D) {
			return;
		}
		Vec3 horizontalForward = Vec3.directionFromRotation(0.0F, yaw);
		Vec3 right = new Vec3(-horizontalForward.z, 0.0D, horizontalForward.x);
		if (right.lengthSqr() <= 1.0E-6D) {
			right = new Vec3(1.0D, 0.0D, 0.0D);
		} else {
			right = right.normalize();
		}
		double forwardProjection = horizontalVelocity.dot(horizontalForward);
		double forwardScale = forwardProjection >= 0.0D
				? DroneFlightPhysics.MAX_FORWARD_SPEED
				: DroneFlightPhysics.MAX_REVERSE_SPEED;
		double forwardDrive = forwardScale <= 1.0E-6D ? 0.0D : forwardProjection / forwardScale;
		double strafeDrive = DroneFlightPhysics.MAX_STRAFE_SPEED <= 1.0E-6D
				? 0.0D
				: horizontalVelocity.dot(right) / DroneFlightPhysics.MAX_STRAFE_SPEED;
		forwardDrive = net.minecraft.util.Mth.clamp(forwardDrive, -DroneFlightPhysics.MAX_FORWARD_DRIVE, DroneFlightPhysics.MAX_FORWARD_DRIVE);
		strafeDrive = net.minecraft.util.Mth.clamp(strafeDrive, -DroneFlightPhysics.MAX_STRAFE_DRIVE, DroneFlightPhysics.MAX_STRAFE_DRIVE);
		if (Math.abs(forwardDrive) <= 1.0E-4D && Math.abs(strafeDrive) <= 1.0E-4D) {
			return;
		}
		session.setForwardDrive(forwardDrive);
		session.setStrafeDrive(strafeDrive);
		session.setDisplayForwardDrive(forwardDrive);
		session.setDisplayStrafeDrive(strafeDrive);
		Vec3 seededVelocity = DroneFlightPhysics.step(pitch, yaw, forwardDrive, strafeDrive);
		session.setIntendedVelocity(seededVelocity);
		session.setVelocity(seededVelocity);
	}

	private static void restoreControlledDrivesFromUncontrolledState(
			DroneControlSession session,
			UncontrolledDroneState state,
			float yaw,
			float pitch
	) {
		if (session == null || state == null) {
			return;
		}
		if (!state.hasDriveState()) {
			seedControlledDrivesFromWorldVelocity(session, state.velocity(), yaw, pitch);
			return;
		}
		session.setForwardDrive(state.forwardDrive());
		session.setStrafeDrive(state.strafeDrive());
		session.setDisplayForwardDrive(state.displayForwardDrive());
		session.setDisplayStrafeDrive(state.displayStrafeDrive());
		Vec3 intendedVelocity = DroneFlightPhysics.step(pitch, yaw, state.forwardDrive(), state.strafeDrive());
		session.setIntendedVelocity(intendedVelocity);
		session.setVelocity(finiteVecOr(state.velocity(), intendedVelocity));
	}

	private static void syncControlledDronePresentation(ServerPlayer player, Entity root, DroneControlSession session) {
		if (root == null || session == null) {
			return;
		}
		Vec3 velocity = controlledOperatorVisualVelocity(session);
		syncDroneCameraAnchor(root, velocity);
		maybePlayDroneLoopSound(root, session.forwardDrive(), session.strafeDrive(), true);
		if (player != null) {
			setHotbarVisualHidden(player, true);
		}
		double displayForwardDrive = net.minecraft.util.Mth.lerp(
				DRONE_DISPLAY_DRIVE_SMOOTHING,
				session.displayForwardDrive(),
				session.forwardDrive()
		);
		double displayStrafeDrive = net.minecraft.util.Mth.lerp(
				DRONE_DISPLAY_DRIVE_SMOOTHING,
				session.displayStrafeDrive(),
				session.strafeDrive()
		);
		session.setDisplayForwardDrive(displayForwardDrive);
		session.setDisplayStrafeDrive(displayStrafeDrive);
		syncDroneDisplay(root, session.proxyYaw(), session.proxyPitch(), displayForwardDrive, displayStrafeDrive);
	}

	private static boolean handleControlledServerCollision(
			ServerPlayer player,
			Entity root,
			DroneControlSession session,
			Vec3 intendedMovement,
			Vec3 actualMovement
	) {
		if (player == null || root == null || session == null) {
			return false;
		}
		boolean horizontalCollision = root.horizontalCollision;
		boolean verticalCollisionBelow = root.verticalCollisionBelow;
		boolean verticalCollision = DroneImpactModel.hasMeaningfulVerticalCollision(
				intendedMovement,
				actualMovement,
				root.verticalCollision,
				verticalCollisionBelow
		);
		boolean groundContact = DroneImpactModel.hasVerifiedGroundWearContact(
				intendedMovement,
				actualMovement,
				verticalCollisionBelow,
				hasSupportingBlockBelow(root)
		);
		if (!horizontalCollision && !verticalCollision && !groundContact) {
			return false;
		}

		float impactDamage = DroneImpactModel.computeImpactDamage(
				intendedMovement,
				actualMovement,
				horizontalCollision,
				verticalCollision
		);
		long gameTime = root.level() == null ? Long.MIN_VALUE : root.level().getGameTime();
		if (impactDamage > DroneImpactModel.CONTROL_IMPACT_BREAK_DAMAGE
				|| updateControlledDroneSurfaceWear(session, root, intendedMovement, actualMovement, groundContact, gameTime)) {
			destroyControlledDroneFromImpact(player, session, root);
			return true;
		}
		return false;
	}

	private static boolean hasBlockedHorizontalMovement(Vec3 intendedMovement, Vec3 actualMovement) {
		if (intendedMovement == null || actualMovement == null) {
			return false;
		}
		double intendedHorizontal = Math.sqrt(intendedMovement.x * intendedMovement.x + intendedMovement.z * intendedMovement.z);
		if (intendedHorizontal <= CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON) {
			return false;
		}
		double actualHorizontal = Math.sqrt(actualMovement.x * actualMovement.x + actualMovement.z * actualMovement.z);
		double requiredLoss = Math.max(CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON, intendedHorizontal * 0.12D);
		return intendedHorizontal - actualHorizontal > requiredLoss;
	}

	private static boolean hasBlockedVerticalMovement(Vec3 intendedMovement, Vec3 actualMovement) {
		if (intendedMovement == null || actualMovement == null) {
			return false;
		}
		return positiveMovementDeficit(intendedMovement.y, actualMovement.y) > CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON;
	}

	private static boolean hasBlockedDownwardMovement(Vec3 intendedMovement, Vec3 actualMovement) {
		if (intendedMovement == null || actualMovement == null) {
			return false;
		}
		return intendedMovement.y < -CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON
				&& positiveMovementDeficit(intendedMovement.y, actualMovement.y) > CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON;
	}

	private static boolean isPlausibleControlledDroneMove(Vec3 actualMovement, Vec3 intendedMovement) {
		if (!isFiniteVec(actualMovement)) {
			return false;
		}
		double intendedDistance = isFiniteVec(intendedMovement) ? intendedMovement.length() : 0.0D;
		double maxDistance = Math.max(CONTROLLED_DRONE_MAX_REPORTED_MOVE_BLOCKS, intendedDistance + 2.0D);
		return actualMovement.lengthSqr() <= maxDistance * maxDistance;
	}

	private static Vec3 finiteVecOr(Vec3 value, Vec3 fallback) {
		return isFiniteVec(value) ? value : fallback;
	}

	private static boolean isFiniteVec(Vec3 value) {
		return value != null
				&& Double.isFinite(value.x)
				&& Double.isFinite(value.y)
				&& Double.isFinite(value.z);
	}

	private static double positiveMovementDeficit(double intendedComponent, double actualComponent) {
		double intendedMagnitude = Math.abs(intendedComponent);
		if (intendedMagnitude <= CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON) {
			return 0.0D;
		}
		double actualMagnitude = Math.abs(actualComponent);
		if (actualMagnitude <= CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON) {
			return intendedMagnitude;
		}
		if (Math.signum(actualComponent) != Math.signum(intendedComponent)) {
			return 0.0D;
		}
		return Math.max(0.0D, intendedMagnitude - actualMagnitude);
	}

	private static ControlledCollisionState resolveControlledCollisionState(
			Entity root,
			Vec3 previousPos,
			Vec3 intendedMovement,
			Vec3 actualMovement
	) {
		if (root == null || !(root.level() instanceof ServerLevel level) || previousPos == null) {
			return ControlledCollisionState.NONE;
		}
		boolean blockedHorizontal = hasBlockedHorizontalMovement(intendedMovement, actualMovement);
		boolean blockedVertical = hasBlockedVerticalMovement(intendedMovement, actualMovement);
		boolean blockedDownward = hasBlockedDownwardMovement(intendedMovement, actualMovement);
		if (!blockedHorizontal && !blockedVertical && !blockedDownward) {
			return ControlledCollisionState.NONE;
		}

		AABB previousBox = droneBoxAt(previousPos);
		Vec3 horizontalMovement = intendedMovement == null
				? Vec3.ZERO
				: new Vec3(intendedMovement.x, 0.0D, intendedMovement.z);
		AABB horizontalTargetBox = previousBox.move(horizontalMovement);
		Vec3 verticalMovement = intendedMovement == null
				? Vec3.ZERO
				: new Vec3(0.0D, intendedMovement.y, 0.0D);
		boolean horizontalCollision = blockedHorizontal && pathHitsSolidCollision(level, previousBox, horizontalMovement);
		boolean verticalCollisionBelow = blockedDownward && pathHitsSolidCollision(level, horizontalTargetBox, verticalMovement);
		boolean verticalCollision = blockedVertical && pathHitsSolidCollision(level, horizontalTargetBox, verticalMovement);
		return new ControlledCollisionState(horizontalCollision, verticalCollision, verticalCollisionBelow);
	}

	private static boolean pathHitsSolidCollision(ServerLevel level, AABB startBox, Vec3 movement) {
		if (level == null || startBox == null || movement == null) {
			return false;
		}
		double movementLength = movement.length();
		if (movementLength <= CONTROLLED_DRONE_BLOCKED_MOVEMENT_EPSILON) {
			return false;
		}
		int steps = Math.max(1, net.minecraft.util.Mth.ceil(movementLength / DRONE_COLLISION_SWEEP_STEP));
		for (int step = 1; step <= steps; step++) {
			double progress = (double) step / (double) steps;
			AABB sampleBox = startBox.move(movement.scale(progress));
			if (boxHitsSolidCollision(level, sampleBox)) {
				return true;
			}
		}
		return false;
	}

	private static boolean boxHitsSolidCollision(ServerLevel level, AABB box) {
		if (level == null || box == null) {
			return false;
		}
		VoxelShape probeShape = Shapes.create(box);
		int minX = net.minecraft.util.Mth.floor(box.minX + 1.0E-7D);
		int minY = net.minecraft.util.Mth.floor(box.minY + 1.0E-7D);
		int minZ = net.minecraft.util.Mth.floor(box.minZ + 1.0E-7D);
		int maxX = net.minecraft.util.Mth.floor(box.maxX - 1.0E-7D);
		int maxY = net.minecraft.util.Mth.floor(box.maxY - 1.0E-7D);
		int maxZ = net.minecraft.util.Mth.floor(box.maxZ - 1.0E-7D);
		for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
			BlockState state = level.getBlockState(pos);
			VoxelShape collisionShape = state.getCollisionShape(level, pos);
			if (collisionShape.isEmpty()) {
				continue;
			}
			VoxelShape shiftedShape = collisionShape.move(pos.getX(), pos.getY(), pos.getZ());
			if (Shapes.joinIsNotEmpty(shiftedShape, probeShape, BooleanOp.AND)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasSupportingBlockBelow(Entity entity) {
		if (entity == null || entity.level() == null) {
			return false;
		}
		AABB box = entity.getBoundingBox();
		if (box == null) {
			return false;
		}
		return !entity.level().noCollision(box.move(0.0D, -1.0E-4D, 0.0D));
	}

	private static void tickUncontrolledDrone(Entity root, UncontrolledDroneState state) {
		if (root == null || state == null) {
			return;
		}
		boolean heldByReleaseGlide = isUncontrolledReleaseGlideActive(root, state);
		boolean heldByScreenStream = isDroneStreamingToScreen(root);
		boolean holdWithoutGravity = heldByReleaseGlide || heldByScreenStream;
		if (!holdWithoutGravity && isUncontrolledDroneSettled(root, state.velocity())) {
			settleUncontrolledDrone(root, state);
			return;
		}

		Vec3 autoAimTargetPoint = resolveUncontrolledDroneAutoAimTargetPoint(root, state);
		if (state.autoAimTarget() != null
				&& autoAimTargetPoint == null
				&& isDroneAutoAimTargetDefinitelyMissing(root.level().getServer(), state.dimension(), state.autoAimTarget())) {
			state.setAutoAimTarget(null);
		}
		boolean autoAimAdjusted = syncUncontrolledDroneAutoAim(root, state, autoAimTargetPoint);
		boolean screenDrive = heldByScreenStream && state.hasDriveState();
		Vec3 velocity;
		if (screenDrive) {
			velocity = DroneFlightPhysics.step(state.pitch(), state.yaw(), state.forwardDrive(), state.strafeDrive());
		} else {
			velocity = state.velocity() == null ? Vec3.ZERO : state.velocity();
		}
		if (!holdWithoutGravity) {
			velocity = new Vec3(
					velocity.x * UNCONTROLLED_AIR_DRAG,
					velocity.y * UNCONTROLLED_AIR_DRAG - UNCONTROLLED_GRAVITY,
					velocity.z * UNCONTROLLED_AIR_DRAG
			);
		}

		Vec3 startPos = root.position();
		root.noPhysics = false;
		root.move(MoverType.SELF, velocity);
		Vec3 actualMovement = root.position().subtract(startPos);

		if (shouldDestroyDroneFromCollision(velocity, actualMovement, root.horizontalCollision, root.verticalCollision)) {
			destroyDrone(root, null, false);
			UNCONTROLLED_DRONES.remove(root.getUUID());
			return;
		}

		state.setVelocity(actualMovement);
		if (!holdWithoutGravity && isUncontrolledDroneSettled(root, actualMovement)) {
			settleUncontrolledDrone(root, state);
			return;
		}
		if (autoAimAdjusted) {
			root.hurtMarked = true;
		} else if (screenDrive) {
			root.setYRot(state.yaw());
			root.setXRot(state.pitch());
		} else if (holdWithoutGravity) {
			applyUncontrolledHeldFlightRotation(root, state);
		} else {
			applyUncontrolledRotation(root, state, actualMovement);
		}
		root.setDeltaMovement(actualMovement);
		root.hurtMarked = true;
		double displayForwardDrive = 0.0D;
		double displayStrafeDrive = 0.0D;
		if (screenDrive) {
			displayForwardDrive = net.minecraft.util.Mth.lerp(
					DRONE_DISPLAY_DRIVE_SMOOTHING,
					state.displayForwardDrive(),
					state.forwardDrive()
			);
			displayStrafeDrive = net.minecraft.util.Mth.lerp(
					DRONE_DISPLAY_DRIVE_SMOOTHING,
					state.displayStrafeDrive(),
					state.strafeDrive()
			);
			state.setDisplayForwardDrive(displayForwardDrive);
			state.setDisplayStrafeDrive(displayStrafeDrive);
		}
		syncDroneDisplay(root, root.getYRot(), root.getXRot(), displayForwardDrive, displayStrafeDrive);
		syncDroneCameraAnchor(root, actualMovement);
		NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
	}

	private static boolean isUncontrolledReleaseGlideActive(Entity root, UncontrolledDroneState state) {
		return root != null
				&& state != null
				&& root.level() != null
				&& root.level().getGameTime() < state.holdWithoutGravityUntilTick();
	}

	private static boolean isDroneStreamingToScreen(Entity root) {
		if (root == null || !root.isAlive()) {
			return false;
		}
		Entity cameraAnchor = findDroneCameraAnchor(root);
		boolean streaming = cameraAnchor != null && RendererBotCameraSystem.hasHealthyLiveStreamFollowingEntity(cameraAnchor.getUUID());
		if (streaming) {
			rememberDroneScreenStreamLoadState(root);
		}
		return streaming;
	}

	private static DroneCameraChunkTarget resolveDroneCameraChunkTarget(Entity root) {
		if (root == null) {
			return new DroneCameraChunkTarget(0.0D, 0.0D, 0.0F);
		}
		Vec3 origin = resolveDroneCameraPosition(root);
		float yaw = resolveDroneCameraYaw(root);
		return new DroneCameraChunkTarget(origin.x, origin.z, yaw);
	}

	private static Vec3 resolveDroneCameraPosition(Entity root) {
		if (root == null) {
			return Vec3.ZERO;
		}
		Entity cameraAnchor = findDroneCameraAnchor(root);
		return cameraAnchor != null ? cameraAnchor.position() : droneCameraOrigin(root);
	}

	private static float resolveDroneCameraYaw(Entity root) {
		if (root == null) {
			return 0.0F;
		}
		Entity cameraAnchor = findDroneCameraAnchor(root);
		return cameraAnchor != null ? cameraAnchor.getYRot() : root.getYRot();
	}

	private static float resolveDroneCameraPitch(Entity root) {
		if (root == null) {
			return 0.0F;
		}
		Entity cameraAnchor = findDroneCameraAnchor(root);
		return cameraAnchor != null ? cameraAnchor.getXRot() : root.getXRot();
	}

	private static ChunkPos chunkPosAt(double x, double z) {
		return new ChunkPos(
				SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(x)),
				SectionPos.blockToSectionCoord(net.minecraft.util.Mth.floor(z))
		);
	}

	private static void applyUncontrolledHeldFlightRotation(Entity root, UncontrolledDroneState state) {
		if (root == null || state == null) {
			return;
		}
		state.setPitch(0.0F);
		root.setYRot(state.yaw());
		root.setXRot(0.0F);
	}

	private static Vec3 resolveUncontrolledDroneAutoAimTargetPoint(Entity root, UncontrolledDroneState state) {
		if (root == null
				|| state == null
				|| state.autoAimTarget() == null
				|| !root.isAlive()
				|| !hasDroneAutoAimModule(root)
				|| root.level() == null) {
			return null;
		}
		MinecraftServer server = root.level().getServer();
		return resolveDroneAutoAimTargetPoint(
				server,
				state.dimension() != null ? state.dimension() : root.level().dimension(),
				state.autoAimTarget()
		);
	}

	private static boolean syncUncontrolledDroneAutoAim(Entity root, UncontrolledDroneState state, Vec3 targetPoint) {
		if (root == null
				|| state == null
				|| targetPoint == null
				|| state.autoAimTarget() == null
				|| !root.isAlive()
				|| !hasDroneAutoAimModule(root)
				|| root.level() == null) {
			return false;
		}
		if (targetPoint == null) {
			return false;
		}
		Vec3 origin = resolveSafeDroneCameraOrigin(root, droneCameraOrigin(root));
		float targetYaw = yawTo(origin, targetPoint);
		float targetPitch = pitchTo(origin, targetPoint);
		DroneAutoAimAngles nextAngles = approachDroneAutoAimAngles(
				state.yaw(),
				state.pitch(),
				targetYaw,
				targetPitch,
				DRONE_AUTO_AIM_UNCONTROLLED_ROTATION_BLEND,
				DRONE_AUTO_AIM_UNCONTROLLED_MAX_YAW_STEP_DEGREES,
				DRONE_AUTO_AIM_UNCONTROLLED_MAX_PITCH_STEP_DEGREES
		);
		state.setYaw(nextAngles.yaw());
		state.setPitch(nextAngles.pitch());
		root.setYRot(nextAngles.yaw());
		root.setXRot(nextAngles.pitch());
		return true;
	}

	private static void applyUncontrolledRotation(Entity root, UncontrolledDroneState state, Vec3 velocity) {
		if (root == null || state == null || velocity == null) {
			return;
		}
		double speedSq = velocity.lengthSqr();
		if (speedSq <= 1.0E-8D) {
			if (root.onGround()) {
				state.setPitch(0.0F);
			}
			root.setYRot(state.yaw());
			root.setXRot(state.pitch());
			return;
		}

		double horizontalSq = velocity.x * velocity.x + velocity.z * velocity.z;
		float nextYaw = state.yaw();
		if (horizontalSq > 1.0E-8D) {
			float targetYaw = (float) Math.toDegrees(Math.atan2(-velocity.x, velocity.z));
			nextYaw = lerpAngleDegrees(state.yaw(), targetYaw, UNCONTROLLED_ROTATION_LERP);
			state.setYaw(nextYaw);
		}
		float targetPitch = (float) Math.toDegrees(Math.atan2(-velocity.y, Math.sqrt(horizontalSq)));
		float nextPitch = (float) net.minecraft.util.Mth.lerp(UNCONTROLLED_ROTATION_LERP, state.pitch(), targetPitch);
		nextPitch = net.minecraft.util.Mth.clamp(nextPitch, -90.0F, 90.0F);
		state.setPitch(nextPitch);

		root.setYRot(nextYaw);
		root.setXRot(nextPitch);
	}

	private static boolean shouldDestroyDroneFromCollision(
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean horizontalCollision,
			boolean verticalCollision
	) {
		return computeCrashEquivalentFallBlocks(intendedMovement, actualMovement, horizontalCollision, verticalCollision)
				>= DRONE_CRASH_EQUIVALENT_FALL_BLOCKS;
	}

	private static double computeCrashEquivalentFallBlocks(
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean horizontalCollision,
			boolean verticalCollision
	) {
		if (intendedMovement == null || actualMovement == null || (!horizontalCollision && !verticalCollision)) {
			return 0.0D;
		}

		Vec3 blockedMovement = intendedMovement.subtract(actualMovement);
		double horizontalImpactSq = 0.0D;
		if (horizontalCollision) {
			horizontalImpactSq = blockedMovement.x * blockedMovement.x + blockedMovement.z * blockedMovement.z;
		}

		double verticalImpact = verticalCollision ? Math.abs(intendedMovement.y) : 0.0D;
		double crashEnergy = horizontalImpactSq + verticalImpact * verticalImpact;
		if (crashEnergy <= 1.0E-8D) {
			return 0.0D;
		}

		return crashEnergy / (2.0D * DRONE_CRASH_REFERENCE_ACCELERATION);
	}

	private static boolean updateControlledDroneSurfaceWear(
			DroneControlSession session,
			Entity root,
			Vec3 intendedMovement,
			Vec3 actualMovement,
			boolean verifiedGroundContact,
			long gameTime
	) {
		if (session == null || intendedMovement == null || actualMovement == null || !verifiedGroundContact) {
			return false;
		}

		DroneImpactModel.SurfaceWear surfaceWear = DroneImpactModel.computeSurfaceWear(
				intendedMovement,
				actualMovement,
				true
		);
		if (surfaceWear.delta() <= 0.0D) {
			return false;
		}

		session.setLastSurfaceWearContactTick(gameTime);
		session.setSurfaceWear(Math.min(DroneImpactModel.SURFACE_WEAR_BREAK_LEVEL, session.surfaceWear() + surfaceWear.delta()));
		playDroneSurfaceWearEffects(
				root,
				actualMovement,
				surfaceWear.speedFactor(),
				surfaceWear.pressureFactor(),
				surfaceWear.delta(),
				gameTime
		);
		return session.surfaceWear() >= DroneImpactModel.SURFACE_WEAR_BREAK_LEVEL;
	}

	private static void playDroneSurfaceWearEffects(
			Entity root,
			Vec3 actualMovement,
			double speedFactor,
			double pressureFactor,
			double wearDelta,
			long gameTime
	) {
		if (root == null || !(root.level() instanceof ServerLevel level) || actualMovement == null || wearDelta <= 1.0E-6D) {
			return;
		}
		double scrapeStrength = net.minecraft.util.Mth.clamp(speedFactor * 0.70D + pressureFactor * 0.30D, 0.0D, 1.0D);
		if (scrapeStrength < 0.08D && gameTime % 4L != 0L) {
			return;
		}
		if (gameTime % DRONE_SURFACE_WEAR_PARTICLE_INTERVAL_TICKS != 0L && scrapeStrength < 0.72D) {
			return;
		}

		Vec3 origin = root.position();
		Vec3 slide = new Vec3(actualMovement.x, 0.0D, actualMovement.z);
		if (slide.lengthSqr() > 1.0E-6D) {
			slide = slide.normalize();
		}
		double particleX = origin.x - slide.x * DRONE_WIDTH * 0.24D;
		double particleY = origin.y + 0.035D;
		double particleZ = origin.z - slide.z * DRONE_WIDTH * 0.24D;
		int scrapeCount = 1 + (int) Math.round(scrapeStrength * 5.0D);
		int sparkCount = pressureFactor > 0.28D ? Math.max(1, (int) Math.round(scrapeStrength * pressureFactor * 4.0D)) : 0;
		double spraySpeed = 0.015D + scrapeStrength * 0.055D;

		level.sendParticles(
				ParticleTypes.SCRAPE,
				particleX,
				particleY,
				particleZ,
				scrapeCount,
				0.10D + scrapeStrength * 0.10D,
				0.015D,
				0.10D + scrapeStrength * 0.10D,
				spraySpeed
		);
		if (sparkCount > 0) {
			level.sendParticles(
					ParticleTypes.ELECTRIC_SPARK,
					particleX,
					particleY + 0.02D,
					particleZ,
					sparkCount,
					0.06D + scrapeStrength * 0.08D,
					0.025D,
					0.06D + scrapeStrength * 0.08D,
					spraySpeed * 0.8D
			);
		}
		if (scrapeStrength > 0.48D) {
			level.sendParticles(
					ParticleTypes.DUST_PLUME,
					particleX,
					particleY,
					particleZ,
					1 + (int) Math.round(scrapeStrength * 2.0D),
					0.08D,
					0.02D,
					0.08D,
					0.005D + scrapeStrength * 0.015D
			);
		}
	}

	private static void decayControlledDroneSurfaceWear(DroneControlSession session, long gameTime) {
		if (session == null || session.surfaceWear() <= 0.0D) {
			return;
		}
		if (session.lastSurfaceWearContactTick() == gameTime) {
			return;
		}
		session.setSurfaceWear(Math.max(0.0D, session.surfaceWear() - DRONE_SURFACE_WEAR_DECAY_PER_TICK));
	}

	private static boolean isUncontrolledDroneSettled(Entity root, Vec3 velocity) {
		if (root == null || velocity == null || !root.onGround()) {
			return false;
		}
		double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
		return horizontalSpeedSq <= UNCONTROLLED_SETTLED_HORIZONTAL_SPEED_SQR
				&& Math.abs(velocity.y) <= UNCONTROLLED_SETTLED_VERTICAL_SPEED;
	}

	private static void settleUncontrolledDrone(Entity root, UncontrolledDroneState state) {
		if (root == null || state == null) {
			return;
		}
		state.setVelocity(Vec3.ZERO);
		state.setPitch(0.0F);
		root.setXRot(0.0F);
		root.setDeltaMovement(Vec3.ZERO);
		root.hurtMarked = true;
		syncDroneDisplay(root, root.getYRot(), 0.0F, 0.0D, 0.0D);
		syncDroneCameraAnchor(root, Vec3.ZERO);
		NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
	}

	private static void maybePlayDroneLoopSound(Entity root, double forwardDrive, double strafeDrive, boolean controlled) {
		if (root == null || !root.isAlive() || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		if (!controlled) {
			NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
			return;
		}

		float power = computeDroneSoundPower(root, forwardDrive, strafeDrive, controlled);
		if (power <= 1.0E-3F) {
			NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
			return;
		}

		long now = level.getGameTime();
		long nextAllowedTick = NEXT_DRONE_SOUND_TICK.getOrDefault(root.getUUID(), Long.MIN_VALUE);
		if (now < nextAllowedTick) {
			return;
		}

		NEXT_DRONE_SOUND_TICK.put(root.getUUID(), now + DRONE_LOOP_REPLAY_TICKS);
		float volume = computeDroneSoundVolume(power);
		float pitch = computeDroneSoundPitch(power);
		playDroneLoopSound(level, root.position(), DRONE_LOOP_SOUND, volume, pitch);
		if (isKamikazeDrone(root)) {
			int kamikazePower = resolveDroneKamikazePower(root);
			playDroneLoopSound(
					level,
					root.position(),
					DRONE_KAMIKAZE_LOOP_SOUND,
					computeKamikazeLoopVolume(volume, kamikazePower),
					pitch * DRONE_KAMIKAZE_LOOP_PITCH_SHIFT
			);
		}
	}

	private static float computeKamikazeLoopVolume(float baseVolume, int kamikazePower) {
		float scale = switch (net.minecraft.util.Mth.clamp(kamikazePower, DRONE_KAMIKAZE_MIN_POWER, DRONE_KAMIKAZE_MAX_POWER)) {
			case 1 -> DRONE_KAMIKAZE_LOOP_LEVEL_1_VOLUME_SCALE;
			case 2 -> DRONE_KAMIKAZE_LOOP_LEVEL_2_VOLUME_SCALE;
			default -> DRONE_KAMIKAZE_LOOP_LEVEL_3_VOLUME_SCALE;
		};
		return baseVolume * scale;
	}

	private static float computeDroneSoundPower(Entity root, double forwardDrive, double strafeDrive, boolean controlled) {
		if (root == null) {
			return 0.0F;
		}

		Vec3 velocity = root.getDeltaMovement();
		if (velocity == null) {
			velocity = Vec3.ZERO;
		}

		float forwardPower = (float) net.minecraft.util.Mth.clamp(Math.abs(forwardDrive) / DroneFlightPhysics.MAX_FORWARD_DRIVE, 0.0D, 1.0D);
		float strafePower = (float) net.minecraft.util.Mth.clamp(Math.abs(strafeDrive) / DroneFlightPhysics.MAX_STRAFE_DRIVE, 0.0D, 1.0D);
		float drivePower = Math.max(forwardPower, strafePower);
		float speedPower = (float) net.minecraft.util.Mth.clamp(velocity.length() / DroneFlightPhysics.MAX_COMBINED_SPEED, 0.0D, 1.0D);
		float verticalPower = (float) net.minecraft.util.Mth.clamp(Math.abs(velocity.y) / 0.42D, 0.0D, 1.0D);

		float hoverFloor = root.onGround()
				? (controlled ? 0.18F : 0.0F)
				: (controlled ? 0.46F : 0.28F);
		float responsivePower = Math.max(drivePower, speedPower * 0.78F + verticalPower * 0.22F);
		return net.minecraft.util.Mth.clamp(Math.max(hoverFloor, responsivePower), 0.0F, 1.0F);
	}

	private static float computeDroneSoundPitch(float power) {
		float shiftedPower = 1.0F + (power - DRONE_SOUND_SOURCE_POWER) * 0.58F;
		return net.minecraft.util.Mth.clamp(shiftedPower, DRONE_SOUND_MIN_PITCH, DRONE_SOUND_MAX_PITCH);
	}

	private static float computeDroneSoundVolume(float power) {
		return net.minecraft.util.Mth.clamp(
				DRONE_SOUND_MIN_VOLUME + power * (DRONE_SOUND_MAX_VOLUME - DRONE_SOUND_MIN_VOLUME),
				0.0F,
				DRONE_SOUND_MAX_VOLUME
		);
	}

	private static void playDroneLoopSound(ServerLevel level, Vec3 origin, Holder<SoundEvent> sound, float volume, float pitch) {
		if (level == null || origin == null || volume <= 0.0F) {
			return;
		}
		if (sound == null) {
			return;
		}

		long seed = level.random.nextLong();
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(origin) > DRONE_SOUND_RADIUS_SQR || !PolymerResourcePackUtils.hasMainPack(viewer)) {
				continue;
			}
			viewer.connection.send(new ClientboundSoundPacket(
					sound,
					SoundSource.PLAYERS,
					origin.x,
					origin.y,
					origin.z,
					volume,
					pitch,
					seed
			));
		}
	}

	private static float lerpAngleDegrees(float current, float target, float delta) {
		float wrapped = net.minecraft.util.Mth.wrapDegrees(target - current);
		return current + wrapped * net.minecraft.util.Mth.clamp(delta, 0.0F, 1.0F);
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity != null && entity.getTags().contains(DRONE_NIGHT_VISION_CAMERA_TAG)) {
			entity.discard();
			return;
		}
		if (!(entity instanceof Interaction root) || level == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		// After a restart we want drones to keep falling without a controller; seed the physics state from entity motion.
		if (isDroneActivelyControlled(root)) {
			return;
		}
		UNCONTROLLED_DRONES.putIfAbsent(
				root.getUUID(),
				new UncontrolledDroneState(root.getUUID(), level.dimension(), root.getDeltaMovement(), root.getYRot(), root.getXRot())
		);
	}

	private static void onEntityUnload(Entity entity, ServerLevel level) {
		if (!(entity instanceof Interaction root) || level == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		UNCONTROLLED_DRONES.remove(root.getUUID());
		NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
		NEXT_DRONE_ARM_ALLOWED_TICK.remove(root.getUUID());
		DISPLAY_WOBBLE_BY_DRONE.remove(root.getUUID());
	}

	private static void detachAnyDronePassengersFromController(ServerPlayer player) {
		if (player == null) {
			return;
		}
		boolean changed = false;
		for (Entity passenger : new ArrayList<>(player.getPassengers())) {
			if (resolveDroneRoot(passenger) == null) {
				continue;
			}
			if (passenger.getVehicle() == player || passenger.isPassenger()) {
				passenger.stopRiding();
				changed = true;
			}
		}
		if (changed) {
			syncPassengerAttachment(player);
		}
	}

	private static Entity resolveControlledDroneRoot(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null || player.level() == null || player.level().getServer() == null) {
			return null;
		}
		return findDroneRoot(player.level().getServer(), session.droneDimension(), session.droneUuid());
	}

	private static int resolveRequestedViewDistance(ServerPlayer player) {
		MinecraftServer server = player != null && player.level() != null ? player.level().getServer() : null;
		return net.minecraft.util.Mth.clamp(
				player != null ? player.requestedViewDistance() : 2,
				2,
				Math.max(2, server != null ? server.getPlayerList().getViewDistance() : 2)
		);
	}

	private static int resolveServerViewDistance(MinecraftServer server) {
		return net.minecraft.util.Mth.clamp(
				server != null && server.getPlayerList() != null ? server.getPlayerList().getViewDistance() : 2,
				2,
				32
		);
	}

	private static int resolveServerSimulationTicketRadius(MinecraftServer server) {
		int simulationDistance = net.minecraft.util.Mth.clamp(
				server != null && server.getPlayerList() != null ? server.getPlayerList().getSimulationDistance() : 0,
				0,
				32
		);
		return Math.min(33, simulationDistance + 2);
	}

	private static boolean isDroneActivelyControlled(Entity root) {
		if (root == null) {
			return false;
		}
		return resolveAuthoritativeDroneControllerId(root.getUUID()) != null;
	}

	private static boolean isWithinHorizontalRange(Vec3 origin, Entity entity, double horizontalRange) {
		if (origin == null || entity == null || horizontalRange <= 0.0D) {
			return false;
		}
		double dx = origin.x - entity.getX();
		double dz = origin.z - entity.getZ();
		double horizontalRangeSq = horizontalRange * horizontalRange;
		return dx * dx + dz * dz <= horizontalRangeSq;
	}

	private static boolean isControlledHotbarSlot(int slot) {
		return (slot >= PLAYER_HOTBAR_MENU_SLOT_START && slot < PLAYER_HOTBAR_MENU_SLOT_START + 9)
				|| slot == PLAYER_OFFHAND_MENU_SLOT;
	}

	private static Packet<?> buildControlledSelfMetadataPacket(ServerPlayer player) {
		EntityDataAccessor<Byte> sharedFlagsAccessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		EntityDataAccessor<Boolean> noGravityAccessor = EntityTrackedDataAccessor.lg2$getDataNoGravity();
		EntityDataAccessor<Pose> poseAccessor = EntityTrackedDataAccessor.lg2$getDataPose();
		byte flags = 0;
		if (player != null) {
			Byte currentFlags = player.getEntityData().get(sharedFlagsAccessor);
			flags = currentFlags == null ? 0 : currentFlags;
		}
		flags &= (byte) ~(ENTITY_FLAG_ON_FIRE | ENTITY_FLAG_SHIFTING | ENTITY_FLAG_SPRINTING | ENTITY_FLAG_SWIMMING);
		flags |= (byte) (ENTITY_FLAG_INVISIBLE | ENTITY_FLAG_FALL_FLYING);
		return new ClientboundSetEntityDataPacket(
				player.getId(),
				List.of(
						SynchedEntityData.DataValue.create(sharedFlagsAccessor, flags),
						SynchedEntityData.DataValue.create(noGravityAccessor, true),
						SynchedEntityData.DataValue.create(poseAccessor, Pose.FALL_FLYING)
				)
		);
	}

	private static Packet<?> buildActualSelfMetadataPacket(ServerPlayer player) {
		EntityDataAccessor<Byte> sharedFlagsAccessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		EntityDataAccessor<Boolean> noGravityAccessor = EntityTrackedDataAccessor.lg2$getDataNoGravity();
		EntityDataAccessor<Pose> poseAccessor = EntityTrackedDataAccessor.lg2$getDataPose();
		byte flags = 0;
		boolean noGravity = false;
		Pose pose = Pose.STANDING;
		if (player != null) {
			Byte currentFlags = player.getEntityData().get(sharedFlagsAccessor);
			flags = currentFlags == null ? 0 : currentFlags;
			Boolean currentNoGravity = player.getEntityData().get(noGravityAccessor);
			noGravity = Boolean.TRUE.equals(currentNoGravity);
			Pose currentPose = player.getEntityData().get(poseAccessor);
			pose = currentPose == null ? player.getPose() : currentPose;
		}
		return new ClientboundSetEntityDataPacket(
				player.getId(),
				List.of(
						SynchedEntityData.DataValue.create(sharedFlagsAccessor, flags),
						SynchedEntityData.DataValue.create(noGravityAccessor, noGravity),
						SynchedEntityData.DataValue.create(poseAccessor, pose)
				)
		);
	}

	private static Packet<?> buildControlledSelfTeleportPacket(ServerPlayer player, DroneControlSession session) {
		PositionMoveRotation change = new PositionMoveRotation(
				session.proxyPos(),
				controlledOperatorVisualVelocity(session),
				session.proxyYaw(),
				session.proxyPitch()
		);
		return ClientboundTeleportEntityPacket.teleport(player.getId(), change, ABSOLUTE_TELEPORT, false);
	}

	private static ClientboundPlayerPositionPacket buildControlledPlayerPositionPacket(DroneControlSession session) {
		return buildControlledPlayerPositionPacket(session, session.nextViewSyncTeleportId());
	}

	private static ClientboundPlayerPositionPacket buildControlledPlayerPositionPacket(DroneControlSession session, int teleportId) {
		PositionMoveRotation change = new PositionMoveRotation(
				session.proxyPos(),
				controlledOperatorVisualVelocity(session),
				session.proxyYaw(),
				session.proxyPitch()
		);
		return ClientboundPlayerPositionPacket.of(teleportId, change, ABSOLUTE_TELEPORT);
	}

	private static Vec3 controlledOperatorVisualVelocity(DroneControlSession session) {
		Vec3 velocity = session == null ? null : session.velocity();
		return velocity == null ? Vec3.ZERO : velocity;
	}

	private static Vec3 controlledOperatorDriveVelocity(DroneControlSession session) {
		Vec3 velocity = session == null ? null : session.intendedVelocity();
		return velocity == null ? Vec3.ZERO : velocity;
	}

	private static ClientboundSetPassengersPacket buildControlledOperatorPassengerPacket(ServerPlayer player, DroneControlSession session) {
		ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(player);
		Entity root = (player != null && player.level() != null && player.level().getServer() != null && session != null)
				? findDroneRoot(player.level().getServer(), session.droneDimension(), session.droneUuid())
				: null;
		int[] passengerIds = root != null && root.isAlive() ? new int[]{root.getId()} : new int[0];
		((ClientboundSetPassengersPacketAccessor) (Object) packet).lg2$setPassengers(passengerIds);
		return packet;
	}

	private static ClientboundSetPassengersPacket buildControlledOperatorDroneLayerPassengerPacket(Entity root) {
		ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(root);
		if (!root.isAlive()) {
			((ClientboundSetPassengersPacketAccessor) (Object) packet).lg2$setPassengers(new int[0]);
			return packet;
		}

		List<Display.ItemDisplay> displays = findDroneDisplayLayers(root);
		int[] passengerIds = new int[displays.size()];
		int count = 0;
		for (Display.ItemDisplay display : displays) {
			if (display == null || !display.isAlive()) {
				continue;
			}
			passengerIds[count++] = display.getId();
		}
		if (count != passengerIds.length) {
			int[] compact = new int[count];
			System.arraycopy(passengerIds, 0, compact, 0, count);
			passengerIds = compact;
		}
		((ClientboundSetPassengersPacketAccessor) (Object) packet).lg2$setPassengers(passengerIds);
		return packet;
	}

	private static void syncControlledOperatorDroneLayerAttachment(ServerPlayer player, Entity root) {
		if (player == null || root == null || player.connection == null || !root.isAlive()) {
			return;
		}
		sendControlledOperatorPacket(player, buildControlledOperatorDroneLayerPassengerPacket(root));
	}

	private static void clearControlledOperatorDroneLayerAttachment(ServerPlayer player, Entity root) {
		if (player == null || root == null || player.connection == null || !root.isAlive()) {
			return;
		}
		sendControlledOperatorPacket(player, new ClientboundSetPassengersPacket(root));
	}

	private static void sendControlledOperatorPacket(ServerPlayer player, Packet<?> packet) {
		if (player == null || player.connection == null || packet == null) {
			return;
		}
		runWithControlledOperatorPacketRewriteBypass(() -> player.connection.send(packet));
	}

	private static void applyControlledOperatorExitRotation(ServerPlayer player, DroneControlSession session) {
		if (player == null || session == null) {
			return;
		}
		float yaw = session.proxyYaw();
		float pitch = session.proxyPitch();
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.yHeadRotO = yaw;
		player.yBodyRotO = yaw;
	}

	private static void refreshControlledOperatorActualView(ServerPlayer player) {
		refreshControlledOperatorActualView(player, true);
	}

	private static void refreshControlledOperatorActualView(ServerPlayer player, boolean includeTeleport) {
		if (player == null || player.connection == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (includeTeleport) {
			level.getChunkAt(BlockPos.containing(player.position()));
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
		}
		player.connection.send(buildActualSelfMetadataPacket(player));
		player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));
		player.connection.send(new ClientboundSetPassengersPacket(player));
		player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
	}

	private static void restoreControlledOperatorClientState(ServerPlayer player) {
		restoreControlledOperatorClientState(player, true);
	}

	private static void restoreControlledOperatorClientState(ServerPlayer player, boolean includeViewTeleport) {
		if (player == null) {
			return;
		}
		setHotbarVisualHidden(player, false);
		refreshControlledOperatorActualView(player, includeViewTeleport);
		ServerMechanicsGateSystem.syncPlayerInventory(player);
	}

	private static void clearControlledOperatorMovementState(ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.setCamera(player);
		player.stopFallFlying();
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0.0F;
		player.hurtMarked = true;
	}

	private static void clearControlledOperatorTransientState(ServerPlayer player, DroneControlSession session) {
		clearManagedDroneNightVision(player);
		clearControlledAutoAimTarget(player, session);
		syncControlledOperatorAutoAimInteractionRange(player, false);
		removeControlledDroneTurretAirTrigger(player);
	}

	private static void syncControlledOperatorView(
			ServerPlayer player,
			DroneControlSession session,
			Entity root,
			boolean initialSync,
			boolean forcePositionSync
	) {
		if (player == null || session == null || root == null || player.connection == null) {
			return;
		}
		if (initialSync || forcePositionSync) {
			sendControlledOperatorPacket(player, buildControlledPlayerPositionPacket(session));
		}
		sendControlledOperatorPacket(player, new ClientboundSetEntityMotionPacket(player.getId(), controlledOperatorDriveVelocity(session)));
		sendControlledOperatorPacket(player, buildControlledSelfMetadataPacket(player));
		sendControlledOperatorPacket(player, buildControlledOperatorPassengerPacket(player, session));
		syncControlledOperatorDroneLayerAttachment(player, root);
	}

	private static void syncControlledOperatorFallbackView(ServerPlayer player, DroneControlSession session) {
		if (player == null || session == null || player.connection == null) {
			return;
		}
		sendControlledOperatorPacket(player, new ClientboundSetEntityMotionPacket(player.getId(), controlledOperatorDriveVelocity(session)));
		sendControlledOperatorPacket(player, buildControlledSelfMetadataPacket(player));
	}

	private static void syncControlledOperatorBodyMirror(ServerPlayer player, boolean forceSpawn) {
		if (player == null || player.connection == null || !isControllingDrone(player)) {
			return;
		}
		OperatorBodyMirror mirror = OPERATOR_BODY_MIRRORS.get(player.getUUID());
		if (mirror == null) {
			mirror = createOperatorBodyMirror(player);
			OPERATOR_BODY_MIRRORS.put(player.getUUID(), mirror);
			forceSpawn = true;
		}
		if (forceSpawn || !mirror.spawned()) {
			sendControlledOperatorBodyMirrorSpawn(player, mirror);
			mirror.setSpawned(true);
		}
		sendControlledOperatorBodyMirrorState(player, mirror);
	}

	private static OperatorBodyMirror createOperatorBodyMirror(ServerPlayer player) {
		UUID profileId = UUID.nameUUIDFromBytes(
				("lg2:drone_operator_body:" + player.getUUID()).getBytes(StandardCharsets.UTF_8)
		);
		GameProfile sourceProfile = player.getGameProfile();
		PropertyMap properties = sourceProfile != null
				? new PropertyMap(ImmutableMultimap.copyOf(sourceProfile.properties()))
				: new PropertyMap(ImmutableMultimap.of());
		String name = sourceProfile == null || sourceProfile.name() == null || sourceProfile.name().isBlank()
				? "operator"
				: sourceProfile.name();
		return new OperatorBodyMirror(allocateOperatorBodyMirrorEntityId(), profileId, new GameProfile(profileId, name, properties));
	}

	private static int allocateOperatorBodyMirrorEntityId() {
		return nextOperatorBodyMirrorEntityId--;
	}

	private static void sendControlledOperatorBodyMirrorSpawn(ServerPlayer player, OperatorBodyMirror mirror) {
		if (player == null || mirror == null) {
			return;
		}
		sendControlledOperatorPacket(player, buildOperatorBodyMirrorPlayerInfoPacket(player, mirror));
		sendControlledOperatorPacket(player, new ClientboundAddEntityPacket(
				mirror.entityId(),
				mirror.profileId(),
				player.getX(),
				player.getY(),
				player.getZ(),
				player.getXRot(),
				player.getYRot(),
				EntityType.PLAYER,
				0,
				player.getDeltaMovement(),
				player.getYHeadRot()
		));
	}

	private static ClientboundPlayerInfoUpdatePacket buildOperatorBodyMirrorPlayerInfoPacket(ServerPlayer player, OperatorBodyMirror mirror) {
		EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions = EnumSet.of(
				ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER,
				ClientboundPlayerInfoUpdatePacket.Action.UPDATE_HAT
		);
		ClientboundPlayerInfoUpdatePacket packet = PolymerEntityUtils.createMutablePlayerListPacket(actions);
		GameType gameMode = player == null || player.gameMode == null ? GameType.SURVIVAL : player.gameMode.getGameModeForPlayer();
		ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
				mirror.profileId(),
				mirror.profile(),
				false,
				0,
				gameMode,
				null,
				true,
				0,
				(RemoteChatSession.Data) null
		);
		packet.entries().add(entry);
		return packet;
	}

	private static void sendControlledOperatorBodyMirrorState(ServerPlayer player, OperatorBodyMirror mirror) {
		if (player == null || mirror == null || player.connection == null) {
			return;
		}
		PositionMoveRotation bodyPose = new PositionMoveRotation(
				player.position(),
				player.getDeltaMovement(),
				player.getYRot(),
				player.getXRot()
		);
		sendControlledOperatorPacket(player, ClientboundTeleportEntityPacket.teleport(
				mirror.entityId(),
				bodyPose,
				ABSOLUTE_TELEPORT,
				player.onGround()
		));
		sendControlledOperatorPacket(player, new ClientboundSetEntityMotionPacket(mirror.entityId(), player.getDeltaMovement()));
		sendControlledOperatorPacket(player, new ClientboundSetEntityDataPacket(
				mirror.entityId(),
				buildOperatorBodyMirrorMetadata(player)
		));
		sendControlledOperatorPacket(player, new ClientboundSetEquipmentPacket(
				mirror.entityId(),
				buildOperatorBodyMirrorEquipment(player)
		));
	}

	private static List<SynchedEntityData.DataValue<?>> buildOperatorBodyMirrorMetadata(ServerPlayer player) {
		List<SynchedEntityData.DataValue<?>> values = player.getEntityData().getNonDefaultValues();
		List<SynchedEntityData.DataValue<?>> data = values == null ? new ArrayList<>() : new ArrayList<>(values);
		EntityDataAccessor<Byte> sharedFlagsAccessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		EntityDataAccessor<Boolean> noGravityAccessor = EntityTrackedDataAccessor.lg2$getDataNoGravity();
		EntityDataAccessor<Pose> poseAccessor = EntityTrackedDataAccessor.lg2$getDataPose();
		EntityDataAccessor<HumanoidArm> mainHandAccessor = PlayerTrackedDataAccessor.lg2$getDataPlayerMainHand();
		EntityDataAccessor<Byte> skinPartsAccessor = PlayerTrackedDataAccessor.lg2$getDataPlayerModeCustomisation();
		upsertTrackedData(data, SynchedEntityData.DataValue.create(sharedFlagsAccessor, player.getEntityData().get(sharedFlagsAccessor)));
		upsertTrackedData(data, SynchedEntityData.DataValue.create(noGravityAccessor, player.getEntityData().get(noGravityAccessor)));
		upsertTrackedData(data, SynchedEntityData.DataValue.create(poseAccessor, player.getEntityData().get(poseAccessor)));
		HumanoidArm mainHand = player.getEntityData().get(mainHandAccessor);
		Byte skinParts = player.getEntityData().get(skinPartsAccessor);
		upsertTrackedData(data, SynchedEntityData.DataValue.create(mainHandAccessor, mainHand == null ? HumanoidArm.RIGHT : mainHand));
		upsertTrackedData(data, SynchedEntityData.DataValue.create(skinPartsAccessor, skinParts == null ? OPERATOR_BODY_MIRROR_ALL_SKIN_PARTS : skinParts));
		return data;
	}

	private static List<Pair<EquipmentSlot, ItemStack>> buildOperatorBodyMirrorEquipment(ServerPlayer player) {
		List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			slots.add(Pair.of(slot, player.getItemBySlot(slot).copy()));
		}
		return slots;
	}

	private static <T> void upsertTrackedData(List<SynchedEntityData.DataValue<?>> data, SynchedEntityData.DataValue<T> replacement) {
		if (data == null || replacement == null) {
			return;
		}
		for (int i = 0; i < data.size(); i++) {
			SynchedEntityData.DataValue<?> current = data.get(i);
			if (current != null && current.id() == replacement.id()) {
				data.set(i, replacement);
				return;
			}
		}
		data.add(replacement);
	}

	private static void removeControlledOperatorBodyMirror(ServerPlayer player) {
		if (player == null) {
			return;
		}
		OperatorBodyMirror mirror = OPERATOR_BODY_MIRRORS.remove(player.getUUID());
		if (mirror == null || player.connection == null) {
			return;
		}
		sendControlledOperatorPacket(player, new ClientboundRemoveEntitiesPacket(mirror.entityId()));
		sendControlledOperatorPacket(player, new ClientboundPlayerInfoRemovePacket(List.of(mirror.profileId())));
	}

	private static void syncControlledOperatorNightVision(ServerPlayer player, DroneControlSession session, Entity root) {
		if (player == null || session == null || root == null) {
			return;
		}
		if (hasDroneNightVisionModule(root)) {
			applyManagedDroneNightVision(player);
			return;
		}
		clearManagedDroneNightVision(player);
	}

	private static void applyManagedDroneNightVision(ServerPlayer player) {
		if (player == null) {
			return;
		}
		MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
		if (current != null && !isDroneManagedNightVision(current)) {
			return;
		}
		if (current == null || !current.isInfiniteDuration()) {
			player.addEffect(new MobEffectInstance(
					MobEffects.NIGHT_VISION,
					MobEffectInstance.INFINITE_DURATION,
					DRONE_MANAGED_NIGHT_VISION_AMPLIFIER,
					false,
					false,
					false
			));
		}
		CONTROLLED_OPERATOR_MANAGED_NIGHT_VISION.add(player.getUUID());
	}

	private static void clearManagedDroneNightVision(ServerPlayer player) {
		if (player == null || !CONTROLLED_OPERATOR_MANAGED_NIGHT_VISION.remove(player.getUUID())) {
			return;
		}
		MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
		if (isDroneManagedNightVision(current)) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	private static boolean isDroneManagedNightVision(MobEffectInstance effect) {
		return effect != null
				&& effect.getAmplifier() == DRONE_MANAGED_NIGHT_VISION_AMPLIFIER
				&& effect.isInfiniteDuration()
				&& !effect.isVisible();
	}

	private static boolean syncControlledOperatorAutoAim(ServerPlayer player, DroneControlSession session, Entity root) {
		if (player == null || session == null || root == null) {
			return false;
		}
		boolean enabled = hasDroneAutoAimModule(root);
		syncControlledOperatorAutoAimInteractionRange(player, enabled);
		if (!enabled) {
			clearControlledAutoAimTarget(player, session);
			return false;
		}

		DroneAutoAimTarget target = session.autoAimTarget();
		if (target == null) {
			return false;
		}
		MinecraftServer server = player.level() == null ? null : player.level().getServer();
		Vec3 targetPoint = resolveControlledAutoAimTargetPoint(server, session, target);
		if (targetPoint == null) {
			if (isDroneAutoAimTargetDefinitelyMissing(server, session.droneDimension(), target)) {
				clearControlledAutoAimTarget(player, session);
			}
			return false;
		}
		if (!CONTROLLED_OPERATOR_AUTO_AIM_HIGHLIGHTS.contains(player.getUUID())) {
			showControlledAutoAimTarget(player, session, target);
		}
		if (session.isManualLookRecentlyActive(root.level() == null ? Long.MIN_VALUE : root.level().getGameTime())) {
			return false;
		}

		Vec3 origin = resolveSafeDroneCameraOrigin(root, droneCameraOrigin(root));
		float targetYaw = yawTo(origin, targetPoint);
		float targetPitch = pitchTo(origin, targetPoint);
		DroneAutoAimAngles nextAngles = approachDroneAutoAimAngles(
				session.proxyYaw(),
				session.proxyPitch(),
				targetYaw,
				targetPitch,
				DRONE_AUTO_AIM_CONTROLLED_ROTATION_BLEND,
				DRONE_AUTO_AIM_CONTROLLED_MAX_YAW_STEP_DEGREES,
				DRONE_AUTO_AIM_CONTROLLED_MAX_PITCH_STEP_DEGREES
		);
		boolean changed = Math.abs(net.minecraft.util.Mth.wrapDegrees(nextAngles.yaw() - session.proxyYaw())) > 1.0E-4F
				|| Math.abs(nextAngles.pitch() - session.proxyPitch()) > 1.0E-4F;
		if (!changed) {
			return false;
		}

		session.setControlYaw(nextAngles.yaw());
		session.setControlPitch(nextAngles.pitch());
		session.setProxyYaw(nextAngles.yaw());
		session.setProxyPitch(nextAngles.pitch());
		root.setYRot(nextAngles.yaw());
		root.setXRot(nextAngles.pitch());
		root.hurtMarked = true;
		return true;
	}

	private static InteractionResult handleControlledAutoAimEntityInteraction(ServerPlayer player, InteractionHand hand, Entity entity) {
		if (player == null || hand != InteractionHand.MAIN_HAND || entity == null || resolveDroneRoot(entity) != null || entity == player) {
			return InteractionResult.PASS;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		Entity root = resolveControlledDroneRoot(player);
		if (session == null || root == null || !hasDroneAutoAimModule(root)) {
			return InteractionResult.PASS;
		}
		Vec3 targetPoint = entity.getEyePosition();
		if (!isWithinControlledAutoAimSelectionRange(root, targetPoint)) {
			return InteractionResult.SUCCESS;
		}
		toggleControlledAutoAimTarget(player, session, new DroneAutoAimEntityTarget(entity.getUUID()));
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult handleControlledAutoAimBlockInteraction(ServerPlayer player, InteractionHand hand, BlockPos pos) {
		if (player == null || hand != InteractionHand.MAIN_HAND || pos == null || !(player.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		Entity root = resolveControlledDroneRoot(player);
		if (session == null || root == null || !hasDroneAutoAimModule(root)) {
			return InteractionResult.PASS;
		}
		if (!level.hasChunkAt(pos)) {
			return InteractionResult.SUCCESS;
		}
		BlockState state = level.getBlockState(pos);
		if (!isSelectableAutoAimBlock(level, pos, state)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 targetPoint = resolveAutoAimBlockTargetPoint(level, pos, state);
		if (targetPoint == null) {
			return InteractionResult.SUCCESS;
		}
		if (!isWithinControlledAutoAimSelectionRange(root, targetPoint)) {
			return InteractionResult.SUCCESS;
		}
		toggleControlledAutoAimTarget(player, session, new DroneAutoAimBlockTarget(pos.immutable()));
		return InteractionResult.SUCCESS;
	}

	private static void toggleControlledAutoAimTarget(ServerPlayer player, DroneControlSession session, DroneAutoAimTarget target) {
		if (player == null || session == null || target == null) {
			return;
		}
		if (Objects.equals(session.autoAimTarget(), target)) {
			clearControlledAutoAimTarget(player, session);
			return;
		}
		session.setAutoAimTarget(target);
		showControlledAutoAimTarget(player, session, target);
	}

	private static void showControlledAutoAimTarget(ServerPlayer player, DroneControlSession session, DroneAutoAimTarget target) {
		if (player == null || session == null || target == null) {
			return;
		}
		MinecraftServer server = player.level() == null ? null : player.level().getServer();
		List<ServerSelectionHighlightSystem.DisplayBlueprint> blueprints = resolveControlledAutoAimHighlightBlueprints(server, session, target);
		if (blueprints.isEmpty()) {
			clearControlledAutoAimHighlight(player);
			return;
		}
		ServerSelectionHighlightSystem.show(player, blueprints);
		CONTROLLED_OPERATOR_AUTO_AIM_HIGHLIGHTS.add(player.getUUID());
	}

	private static void clearControlledAutoAimTarget(ServerPlayer player, DroneControlSession session) {
		if (session != null) {
			session.setAutoAimTarget(null);
		}
		clearControlledAutoAimHighlight(player);
	}

	private static void clearControlledAutoAimHighlight(ServerPlayer player) {
		if (player != null && CONTROLLED_OPERATOR_AUTO_AIM_HIGHLIGHTS.remove(player.getUUID())) {
			ServerSelectionHighlightSystem.clear(player);
		}
	}

	private static List<ServerSelectionHighlightSystem.DisplayBlueprint> resolveControlledAutoAimHighlightBlueprints(
			MinecraftServer server,
			DroneControlSession session,
			DroneAutoAimTarget target
	) {
		if (server == null || session == null || target == null) {
			return List.of();
		}
		ServerLevel level = server.getLevel(session.droneDimension());
		if (level == null) {
			return List.of();
		}
		if (target instanceof DroneAutoAimEntityTarget entityTarget) {
			Entity entity = findEntity(server, session.droneDimension(), entityTarget.entityUuid());
			if (entity == null || !entity.isAlive()) {
				return List.of();
			}
			return List.of(new ServerSelectionHighlightSystem.EntityGlowBlueprint(entity));
		}
		if (target instanceof DroneAutoAimBlockTarget blockTarget) {
			if (!level.hasChunkAt(blockTarget.blockPos())) {
				return List.of();
			}
			BlockState state = level.getBlockState(blockTarget.blockPos());
			if (!isSelectableAutoAimBlock(level, blockTarget.blockPos(), state)) {
				return List.of();
			}
			Vec3 highlightPos = resolveAutoAimBlockTargetPoint(level, blockTarget.blockPos(), state);
			if (highlightPos == null) {
				return List.of();
			}
			return List.of(new ServerSelectionHighlightSystem.ItemDisplayBlueprint(
					level,
					highlightPos,
					0.0F,
					0.0F,
					ServerSelectionHighlightSystem.createHighlightCarrierStack(),
					ItemDisplayContext.FIXED,
					ServerSelectionHighlightSystem.defaultHighlightCarrierTransformation()
			));
		}
		return List.of();
	}

	private static Vec3 resolveControlledAutoAimTargetPoint(MinecraftServer server, DroneControlSession session, DroneAutoAimTarget target) {
		if (session == null) {
			return null;
		}
		return resolveDroneAutoAimTargetPoint(server, session.droneDimension(), target);
	}

	private static Vec3 resolveDroneAutoAimTargetPoint(
			MinecraftServer server,
			net.minecraft.resources.ResourceKey<Level> droneDimension,
			DroneAutoAimTarget target
	) {
		if (server == null || droneDimension == null || target == null) {
			return null;
		}
		if (target instanceof DroneAutoAimEntityTarget entityTarget) {
			Entity entity = findEntity(server, droneDimension, entityTarget.entityUuid());
			return entity != null && entity.isAlive() ? entity.getEyePosition() : null;
		}
		if (target instanceof DroneAutoAimBlockTarget blockTarget) {
			ServerLevel level = server.getLevel(droneDimension);
			if (level == null || !level.hasChunkAt(blockTarget.blockPos())) {
				return null;
			}
			BlockState state = level.getBlockState(blockTarget.blockPos());
			return resolveAutoAimBlockTargetPoint(level, blockTarget.blockPos(), state);
		}
		return null;
	}

	private static boolean isDroneAutoAimTargetDefinitelyMissing(
			MinecraftServer server,
			net.minecraft.resources.ResourceKey<Level> droneDimension,
			DroneAutoAimTarget target
	) {
		if (server == null || droneDimension == null || target == null) {
			return false;
		}
		if (target instanceof DroneAutoAimEntityTarget entityTarget) {
			Entity entity = findEntity(server, droneDimension, entityTarget.entityUuid());
			return entity != null && !entity.isAlive();
		}
		if (target instanceof DroneAutoAimBlockTarget blockTarget) {
			ServerLevel level = server.getLevel(droneDimension);
			if (level == null || !level.hasChunkAt(blockTarget.blockPos())) {
				return false;
			}
			return !isSelectableAutoAimBlock(level, blockTarget.blockPos(), level.getBlockState(blockTarget.blockPos()));
		}
		return false;
	}

	private static boolean isSelectableAutoAimBlock(ServerLevel level, BlockPos pos, BlockState state) {
		return level != null
				&& pos != null
				&& state != null
				&& !state.isAir()
				&& !state.getCollisionShape(level, pos).isEmpty();
	}

	private static Vec3 resolveAutoAimBlockTargetPoint(ServerLevel level, BlockPos pos, BlockState state) {
		if (!isSelectableAutoAimBlock(level, pos, state)) {
			return null;
		}
		AABB bounds = state.getCollisionShape(level, pos).bounds();
		return new Vec3(
				pos.getX() + (bounds.minX + bounds.maxX) * 0.5D,
				pos.getY() + (bounds.minY + bounds.maxY) * 0.5D,
				pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5D
		);
	}

	private static boolean isWithinControlledAutoAimSelectionRange(Entity root, Vec3 targetPoint) {
		if (root == null || targetPoint == null) {
			return false;
		}
		Vec3 origin = resolveSafeDroneCameraOrigin(root, droneCameraOrigin(root));
		return origin.distanceToSqr(targetPoint) <= DRONE_AUTO_AIM_SELECTION_RANGE_BLOCKS * DRONE_AUTO_AIM_SELECTION_RANGE_BLOCKS;
	}

	private static void syncControlledOperatorAutoAimInteractionRange(ServerPlayer player, boolean enabled) {
		if (player == null) {
			return;
		}
		double amount = enabled ? DRONE_AUTO_AIM_INTERACTION_RANGE_BONUS : 0.0D;
		syncControlledOperatorAttributeModifier(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), DRONE_AUTO_AIM_BLOCK_INTERACTION_RANGE_MODIFIER_ID, amount);
		syncControlledOperatorAttributeModifier(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), DRONE_AUTO_AIM_ENTITY_INTERACTION_RANGE_MODIFIER_ID, amount);
	}

	private static void syncControlledOperatorAttributeModifier(AttributeInstance attribute, Identifier modifierId, double amount) {
		if (attribute == null || modifierId == null) {
			return;
		}
		AttributeModifier current = attribute.getModifier(modifierId);
		if (Math.abs(amount) <= 1.0E-6D) {
			if (current != null) {
				attribute.removeModifier(modifierId);
			}
			return;
		}
		if (current == null
				|| current.operation() != AttributeModifier.Operation.ADD_VALUE
				|| Double.compare(current.amount(), amount) != 0) {
			if (current != null) {
				attribute.removeModifier(modifierId);
			}
			attribute.addTransientModifier(new AttributeModifier(modifierId, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static float yawTo(Vec3 origin, Vec3 target) {
		Vec3 delta = target.subtract(origin);
		return (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
	}

	private static float pitchTo(Vec3 origin, Vec3 target) {
		Vec3 delta = target.subtract(origin);
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		return (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
	}

	private static DroneAutoAimAngles approachDroneAutoAimAngles(
			float currentYaw,
			float currentPitch,
			float targetYaw,
			float targetPitch,
			float blend,
			float maxYawStep,
			float maxPitchStep
	) {
		float yawError = net.minecraft.util.Mth.wrapDegrees(targetYaw - currentYaw);
		float pitchError = targetPitch - currentPitch;
		float nextYaw = currentYaw + net.minecraft.util.Mth.clamp(
				yawError * net.minecraft.util.Mth.clamp(blend, 0.0F, 1.0F),
				-Math.abs(maxYawStep),
				Math.abs(maxYawStep)
		);
		float nextPitch = currentPitch + net.minecraft.util.Mth.clamp(
				pitchError * net.minecraft.util.Mth.clamp(blend, 0.0F, 1.0F),
				-Math.abs(maxPitchStep),
				Math.abs(maxPitchStep)
		);
		return new DroneAutoAimAngles(nextYaw, net.minecraft.util.Mth.clamp(nextPitch, -90.0F, 90.0F));
	}

	private static void updateDroneHud(ServerPlayer player, DroneControlSession session, boolean force) {
		if (player == null || session == null || player.connection == null) {
			return;
		}

		long now = player.level().getGameTime();
		int controlSpeedSlot = getControlSpeedSlot(player);
		String snapshot = buildDroneHudSnapshot(session, session.velocity(), controlSpeedSlot);
		if (!force
				&& session.hudVisible()
				&& Objects.equals(session.lastHudSnapshot(), snapshot)
				&& now - session.lastHudTick() < DRONE_HUD_REFRESH_TICKS) {
			return;
		}

		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 0));
		if (player != null && PolymerResourcePackUtils.hasMainPack(player)) {
			if (force) {
				player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
				player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
			}
			player.connection.send(new ClientboundSetActionBarTextPacket(buildDroneHudWidget(session, controlSpeedSlot)));
		} else {
			if (force) {
				player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
				player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
			}
			player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
			player.connection.send(new ClientboundSetActionBarTextPacket(buildDroneHudTextSubtitle(session, session.velocity(), controlSpeedSlot)));
		}
		session.setHudVisible(true);
		session.setLastHudSnapshot(snapshot);
		session.setLastHudTick(now);
	}

	private static void clearDroneHud(ServerPlayer player, DroneControlSession session, boolean force) {
		if (player == null || session == null || player.connection == null) {
			return;
		}
		if (!force && !session.hudVisible()) {
			return;
		}

		player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
		player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
		session.setHudVisible(false);
		session.setLastHudSnapshot("");
		session.setLastHudTick(Long.MIN_VALUE);
	}

	private static Component buildDroneHudTextSubtitle(DroneControlSession session, Vec3 velocity, int controlSpeedSlot) {
		int forwardPct = drivePercent(Math.max(0.0D, session.forwardDrive()), DroneFlightPhysics.MAX_FORWARD_DRIVE);
		int reversePct = drivePercent(Math.max(0.0D, -session.forwardDrive()), DroneFlightPhysics.MAX_FORWARD_DRIVE);
		int leftPct = drivePercent(Math.max(0.0D, -session.strafeDrive()), DroneFlightPhysics.MAX_STRAFE_DRIVE);
		int rightPct = drivePercent(Math.max(0.0D, session.strafeDrive()), DroneFlightPhysics.MAX_STRAFE_DRIVE);
		int speedPct = (int) Math.round(net.minecraft.util.Mth.clamp(
				(velocity == null ? 0.0D : velocity.length()) / DroneFlightPhysics.MAX_COMBINED_SPEED,
				0.0D,
				1.0D
		) * 100.0D);
		int controlPct = (int) Math.round(((controlSpeedSlot + 1) / 9.0D) * 100.0D);

		MutableComponent line = Component.empty();
		line.append(hudLabel("FWD ")).append(hudValue(percentText(forwardPct)));
		line.append(hudSeparator("  "));
		line.append(hudLabel("REV ")).append(hudValue(percentText(reversePct)));
		line.append(hudSeparator("  "));
		line.append(hudLabel("L ")).append(hudValue(percentText(leftPct)));
		line.append(hudSeparator("  "));
		line.append(hudLabel("R ")).append(hudValue(percentText(rightPct)));
		line.append(hudSeparator("   "));
		line.append(hudLabel("CTRL ")).append(hudValue(percentText(controlPct)));
		line.append(hudSeparator("   "));
		line.append(hudLabel("SPD ")).append(hudValue(percentText(speedPct)));
		return line;
	}

	private static Component buildDroneHudWidget(DroneControlSession session, int controlSpeedSlot) {
		int xIndex = quantizeDrive(session.strafeDrive(), DroneFlightPhysics.MAX_STRAFE_DRIVE);
		int yIndex = quantizeDrive(-session.forwardDrive(), DroneFlightPhysics.MAX_FORWARD_DRIVE);
		int stickGlyph = DRONE_HUD_GLYPH_BASE + yIndex * DRONE_HUD_GRID_SIZE + xIndex;
		int speedGlyph = DRONE_HUD_SPEED_BAR_GLYPH_BASE + net.minecraft.util.Mth.clamp(controlSpeedSlot, 0, 8);
		String glyphText = new String(new char[]{(char) stickGlyph}) + DRONE_HUD_BAR_OVERLAP_GLYPH + (char) speedGlyph;
		return Component.literal(glyphText)
				.withStyle(style -> style
						.withColor(DRONE_HUD_VALUE_COLOR)
						.withItalic(false)
						.withShadowColor(0x00000000));
	}

	private static String buildDroneHudSnapshot(DroneControlSession session, Vec3 velocity, int controlSpeedSlot) {
		int forwardPct = drivePercent(Math.max(0.0D, session.forwardDrive()), DroneFlightPhysics.MAX_FORWARD_DRIVE);
		int reversePct = drivePercent(Math.max(0.0D, -session.forwardDrive()), DroneFlightPhysics.MAX_FORWARD_DRIVE);
		int leftPct = drivePercent(Math.max(0.0D, -session.strafeDrive()), DroneFlightPhysics.MAX_STRAFE_DRIVE);
		int rightPct = drivePercent(Math.max(0.0D, session.strafeDrive()), DroneFlightPhysics.MAX_STRAFE_DRIVE);
		int speedPct = (int) Math.round(net.minecraft.util.Mth.clamp(
				(velocity == null ? 0.0D : velocity.length()) / DroneFlightPhysics.MAX_COMBINED_SPEED,
				0.0D,
				1.0D
		) * 100.0D);
		return forwardPct + "|" + reversePct + "|" + leftPct + "|" + rightPct + "|" + speedPct + "|" + controlSpeedSlot;
	}

	private static int getControlSpeedSlot(ServerPlayer player) {
		if (player == null) {
			return 0;
		}
		return net.minecraft.util.Mth.clamp(player.getInventory().getSelectedSlot(), 0, 8);
	}

	private static double getControlDriveStep(int controlSpeedSlot) {
		return DroneControlTuning.driveStepForSlot(controlSpeedSlot);
	}

	private static int drivePercent(double value, double maxValue) {
		if (maxValue <= 1.0E-6D) {
			return 0;
		}
		return (int) Math.round(net.minecraft.util.Mth.clamp(value / maxValue, 0.0D, 1.0D) * 100.0D);
	}

	private static int quantizeDrive(double value, double maxMagnitude) {
		if (maxMagnitude <= 1.0E-6D) {
			return DRONE_HUD_GRID_SIZE / 2;
		}
		double normalized = net.minecraft.util.Mth.clamp(value / maxMagnitude, -1.0D, 1.0D);
		return net.minecraft.util.Mth.clamp(
				(int) Math.round((normalized + 1.0D) * 0.5D * (DRONE_HUD_GRID_SIZE - 1)),
				0,
				DRONE_HUD_GRID_SIZE - 1
		);
	}

	private static String percentText(int value) {
		return "%03d%%".formatted(Math.max(0, Math.min(999, value)));
	}

	private static MutableComponent hudLabel(String text) {
		return Component.literal(text).withStyle(style -> style.withColor(DRONE_HUD_LABEL_COLOR).withItalic(false));
	}

	private static MutableComponent hudValue(String text) {
		return Component.literal(text).withStyle(style -> style.withColor(DRONE_HUD_VALUE_COLOR).withItalic(false));
	}

	private static MutableComponent hudSeparator(String text) {
		return Component.literal(text).withStyle(style -> style.withColor(DRONE_HUD_DIM_COLOR).withItalic(false));
	}

	private static MutableComponent hudSegment(String text, int color) {
		return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
	}

	private static boolean startControlling(ServerPlayer player, Entity root) {
		if (player == null || root == null || !root.isAlive() || !(root.level() instanceof ServerLevel droneLevel)) {
			return false;
		}
		PENDING_CONTROL_STARTS.remove(player.getUUID());
		POST_CONTROL_CLIENT_RESYNC_UNTIL_TICK.remove(player.getUUID());
		UUID currentControllerId = resolveAuthoritativeDroneControllerId(root.getUUID());
		if (currentControllerId != null && !Objects.equals(currentControllerId, player.getUUID())) {
			player.sendSystemMessage(Component.literal("Этот дрон уже управляется другим игроком."));
			return false;
		}

		stopControlling(player, false);
		POST_CONTROL_MOVE_SUPPRESSED_UNTIL_TICK.remove(player.getUUID());
		CameraVideoRecordingSystem.stopForDroneControl(player);
		MapImageRenderSystem.cancelRender(player.getUUID());
		RendererBotCameraSystem.stopCameraHotbarWarmupForPlayer(player.getUUID());
		ServerRaceSystem.suspendCopperManJetpackForDrone(player);
		UncontrolledDroneState previousUncontrolledState = UNCONTROLLED_DRONES.remove(root.getUUID());
		prepareControlledDroneBody(root);
		syncDroneDisplayLayers(root);
		root.setDeltaMovement(Vec3.ZERO);
		droneLevel.getChunkAt(root.blockPosition());
		setHotbarVisualHidden(player, true);
		syncDroneCameraAnchor(root, Vec3.ZERO);

		DroneControlSession session = new DroneControlSession(
				root.getUUID(),
				droneLevel.dimension()
		);
		session.setProxyPos(root.position());
		session.setControlYaw(root.getYRot());
		session.setControlPitch(root.getXRot());
		session.setProxyYaw(root.getYRot());
		session.setProxyPitch(root.getXRot());
		session.refreshKnownDroneLocation(root);
		session.setTurretInputSuppressedUntilTick(droneLevel.getGameTime() + DRONE_TURRET_CONTROL_START_SUPPRESS_TICKS);
		if (previousUncontrolledState != null) {
			session.setAutoAimTarget(previousUncontrolledState.autoAimTarget());
			restoreControlledDrivesFromUncontrolledState(session, previousUncontrolledState, root.getYRot(), root.getXRot());
		}
		syncDroneDisplay(root, root.getYRot(), root.getXRot(), session.displayForwardDrive(), session.displayStrafeDrive());
		VISUALLY_CONTROLLED_PLAYERS.add(player.getUUID());
		ACTIVE_SESSIONS.put(player.getUUID(), session);
		INPUTS.put(player.getUUID(), DroneInputState.EMPTY);
		CONTROLLERS_BY_DRONE.put(root.getUUID(), player.getUUID());
		updateDroneChunkTickets(player.level().getServer());
		syncControlledOperatorAutoAimInteractionRange(player, hasDroneAutoAimModule(root));
		if (session.autoAimTarget() != null) {
			showControlledAutoAimTarget(player, session, session.autoAimTarget());
		}
		syncControlledOperatorBodyMirror(player, true);
		syncControlledOperatorView(player, session, root, true, true);
		syncControlledOperatorNightVision(player, session, root);
		notifyDroneNetworkChanged(root);
		updateDroneHud(player, session, true);
		player.sendSystemMessage(Component.literal("Управление дроном начато. Shift — выйти."));
		return true;
	}

	private static void stopControlling(ServerPlayer player, boolean notify) {
		stopControlling(player, notify, true);
	}

	private static void stopControlling(ServerPlayer player, boolean notify, boolean releaseDrone) {
		if (player == null) {
			return;
		}
		DroneControlSession session = ACTIVE_SESSIONS.remove(player.getUUID());
		INPUTS.remove(player.getUUID());
		if (session == null) {
			clearControlledOperatorTransientState(player, null);
			removeControlledOperatorBodyMirror(player);
			if (VISUALLY_CONTROLLED_PLAYERS.contains(player.getUUID())) {
				restoreOrphanedControlledOperator(player);
			} else {
				markPostControlMoveSuppressedForPlayer(player);
				restoreControlledOperatorClientState(player);
				schedulePostControlClientResync(player);
			}
			return;
		}
		CONTROLLERS_BY_DRONE.remove(session.droneUuid(), player.getUUID());
		MinecraftServer server = player.level().getServer();
		Entity root = server == null ? null : findDroneRoot(server, session.droneDimension(), session.droneUuid());
		DroneAutoAimTarget releasedAutoAimTarget = session.autoAimTarget();

		clearControlledOperatorTransientState(player, session);
		removeControlledOperatorBodyMirror(player);
		clearControlledOperatorMovementState(player);
		markPostControlMoveSuppressedForPlayer(player);
		clearControlledOperatorDroneLayerAttachment(player, root);
		detachAnyDronePassengersFromController(player);
		if (releaseDrone && root != null) {
			Vec3 currentRootPos = root.position();
			Vec3 proxyPos = finiteVecOr(session.proxyPos(), currentRootPos);
			if (!isPlausibleControlledDroneMove(proxyPos.subtract(currentRootPos), session.intendedVelocity())) {
				proxyPos = currentRootPos;
				session.setProxyPos(currentRootPos);
			}
			root.setPos(proxyPos.x, proxyPos.y, proxyPos.z);
			root.setBoundingBox(droneBoxAt(root.position()));
			root.setYRot(session.proxyYaw());
			root.setXRot(session.proxyPitch());
			Vec3 releasedVelocity = finiteVecOr(session.velocity(), Vec3.ZERO);
			if (!isPlausibleControlledDroneMove(releasedVelocity, session.intendedVelocity())) {
				releasedVelocity = Vec3.ZERO;
			}
			root.noPhysics = false;
			root.setDeltaMovement(releasedVelocity);
			root.hurtMarked = true;
			UNCONTROLLED_DRONES.put(
					root.getUUID(),
					new UncontrolledDroneState(
								root.getUUID(),
								((ServerLevel) root.level()).dimension(),
								releasedVelocity,
								root.getYRot(),
								root.getXRot(),
								releasedAutoAimTarget,
								root.level().getGameTime() + UNCONTROLLED_DRONE_RELEASE_GLIDE_TICKS,
								session.forwardDrive(),
								session.strafeDrive(),
								session.displayForwardDrive(),
								session.displayStrafeDrive(),
								true
						)
				);
			syncDroneDisplayLayers(root);
			syncDroneDisplay(root, root.getYRot(), root.getXRot(), 0.0D, 0.0D);
			syncDroneCameraAnchor(root, releasedVelocity);
			notifyDroneNetworkChanged(root);
		} else {
			NEXT_DRONE_SOUND_TICK.remove(session.droneUuid());
		}
		clearDroneHud(player, session, true);
		applyControlledOperatorExitRotation(player, session);
		restoreControlledOperatorClientState(player);
		schedulePostControlClientResync(player);
		VISUALLY_CONTROLLED_PLAYERS.remove(player.getUUID());

		ServerRaceSystem.resumeCopperManJetpackAfterDrone(player);

		if (notify) {
			player.sendSystemMessage(Component.literal("Управление дроном завершено."));
		}
	}

	private static void destroyDrone(Entity root, ServerPlayer breaker, boolean dropItem) {
		if (root == null || !root.isAlive() || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		boolean kamikazeDrone = isKamikazeDrone(root);
		int kamikazePower = resolveDroneKamikazePower(root);
		playDroneBreakEffects(level, droneCameraOrigin(root), root.getDeltaMovement());
		UNCONTROLLED_DRONES.remove(root.getUUID());
		NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
		NEXT_DRONE_ARM_ALLOWED_TICK.remove(root.getUUID());
		NEXT_DRONE_TURRET_FIRE_TICK.remove(root.getUUID());
		DISPLAY_WOBBLE_BY_DRONE.remove(root.getUUID());
		SCREEN_STREAM_DRONE_LOAD_STATES.remove(root.getUUID());
		BluetoothLinkSystem.removeDroneEndpoint(level, root.getUUID(), root.blockPosition());
		stopAllDroneControllers(root, true);
		CONTROLLERS_BY_DRONE.remove(root.getUUID());
		for (Display.ItemDisplay display : findDroneDisplayLayers(root)) {
			display.discard();
		}
		DISPLAYS_BY_DRONE.remove(root.getUUID());
		UUID cameraAnchorId = CAMERA_ANCHORS_BY_DRONE.remove(root.getUUID());
		Entity cameraAnchor = cameraAnchorId == null ? findDroneCameraAnchor(root) : findEntity(level.getServer(), level.dimension(), cameraAnchorId);
		if (cameraAnchor != null) {
			cameraAnchor.discard();
		}
		for (Entity passenger : new ArrayList<>(root.getPassengers())) {
			passenger.discard();
		}
		dropDroneTurretInventory(root);
		if (dropItem && breaker != null && !breaker.getAbilities().instabuild && !kamikazeDrone) {
			root.spawnAtLocation(level, buildDroneDropStack(root));
		}
		if (kamikazeDrone) {
			detonateKamikazeDrone(level, droneCameraOrigin(root), root.getDeltaMovement(), kamikazePower);
		}
		root.discard();
	}

	private static void stopAllDroneControllers(Entity root, boolean notify) {
		if (root == null) {
			return;
		}
		MinecraftServer server = root.level() == null ? null : root.level().getServer();
		if (server == null || server.getPlayerList() == null) {
			return;
		}

		UUID rootUuid = root.getUUID();
		Set<UUID> controllerIds = collectDroneControllerIds(rootUuid);

		for (UUID controllerId : controllerIds) {
			if (controllerId == null) {
				continue;
			}
			ServerPlayer controller = server.getPlayerList().getPlayer(controllerId);
			if (controller == null) {
				continue;
			}
			stopControlling(controller, notify, false);
		}
		CONTROLLERS_BY_DRONE.remove(rootUuid);

		if (server != null) {
			recoverOrphanedControlledOperators(server);
			recoverPlayersWithStaleDronePassenger(server);
		}
	}

	private static void playDroneBreakEffects(ServerLevel level, Vec3 origin, Vec3 velocity) {
		if (level == null || origin == null) {
			return;
		}
		SoundEvent breakSound = resolveVanillaSoundEvent(DRONE_BREAK_SOUND_ID, SoundEvents.GENERIC_EXPLODE.value());
		float pitch = 0.86F + level.random.nextFloat() * 0.10F;
		level.playSound(
				null,
				origin.x,
				origin.y,
				origin.z,
				breakSound,
				SoundSource.PLAYERS,
				1.05F,
				pitch
		);

		double motionSpread = velocity == null ? 0.05D : net.minecraft.util.Mth.clamp(velocity.length() * 0.35D, 0.05D, 0.35D);
		level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + DRONE_HEIGHT * 0.28D, origin.z, 3, 0.10D, 0.07D, 0.10D, 0.01D);
		level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y + DRONE_HEIGHT * 0.12D, origin.z, 5, 0.13D, 0.10D, 0.13D, motionSpread * 0.25D);
		level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y + DRONE_HEIGHT * 0.16D, origin.z, 2, 0.07D, 0.05D, 0.07D, 0.01D);
	}

	private static String resolveRequiredUpgradeForDroneItem(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		if (stack.getItem() == ModItems.DRONE) {
			return DroneItem.getDroneType(stack) == DroneItem.DroneType.KAMIKAZE ? IT_DRONE_KAMIKAZE : IT_DRONE_SCOUT;
		}
		return null;
	}

	private static InteractionResult tryTuneDrone(ServerPlayer player, Entity root, ItemStack heldStack) {
		if (player == null || root == null || !(root.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}

		long now = level.getGameTime();
		long nextAllowedTick = NEXT_DRONE_ARM_ALLOWED_TICK.getOrDefault(root.getUUID(), Long.MIN_VALUE);
		if (now < nextAllowedTick) {
			return InteractionResult.CONSUME;
		}
		if (heldStack == null || heldStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		if (heldStack.is(Items.TNT)) {
			NEXT_DRONE_ARM_ALLOWED_TICK.put(root.getUUID(), now + 1L);
			return tryArmDroneWithTnt(player, root, heldStack);
		}
		if (heldStack.is(Items.STRING)) {
			NEXT_DRONE_ARM_ALLOWED_TICK.put(root.getUUID(), now + 1L);
			return tryInstallDroneType(player, root, heldStack, DroneItem.DroneType.KAMIKAZE, Items.STRING);
		}
		if (heldStack.is(Items.DISPENSER)) {
			NEXT_DRONE_ARM_ALLOWED_TICK.put(root.getUUID(), now + 1L);
			return tryInstallDroneType(player, root, heldStack, DroneItem.DroneType.COMBAT, Items.DISPENSER);
		}
		if (heldStack.is(Items.SPIDER_EYE)) {
			NEXT_DRONE_ARM_ALLOWED_TICK.put(root.getUUID(), now + 1L);
			return tryInstallNightVisionModule(player, root, heldStack);
		}
		if (heldStack.is(Items.CALIBRATED_SCULK_SENSOR)) {
			NEXT_DRONE_ARM_ALLOWED_TICK.put(root.getUUID(), now + 1L);
			return tryInstallAutoAimModule(player, root, heldStack);
		}
		if (heldStack.getItem() instanceof DyeItem dyeItem) {
			NEXT_DRONE_ARM_ALLOWED_TICK.put(root.getUUID(), now + 1L);
			return tryPaintDrone(player, root, heldStack, dyeItem.getDyeColor());
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult tryArmDroneWithTnt(ServerPlayer player, Entity root, ItemStack heldStack) {
		if (player == null || root == null || !(root.level() instanceof ServerLevel level) || heldStack == null || !heldStack.is(Items.TNT)) {
			return InteractionResult.PASS;
		}
		if (resolveDroneType(root) != DroneItem.DroneType.KAMIKAZE) {
			return InteractionResult.PASS;
		}

		int currentPower = resolveDroneKamikazePower(root);
		if (currentPower >= DRONE_KAMIKAZE_MAX_POWER) {
			return InteractionResult.PASS;
		}

		int newPower = net.minecraft.util.Mth.clamp(currentPower + 1, DRONE_KAMIKAZE_MIN_POWER, DRONE_KAMIKAZE_MAX_POWER);
		setDroneKamikazePower(root, newPower);
		triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
		playDroneKamikazeInsertFeedback(level, droneCameraOrigin(root), newPower);
		notifyDroneNetworkChanged(root);

		if (!player.getAbilities().instabuild) {
			heldStack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryInstallDroneType(
			ServerPlayer player,
			Entity root,
			ItemStack heldStack,
			DroneItem.DroneType targetType,
			Item installedItem
	) {
		if (player == null || root == null || heldStack == null || targetType == null || installedItem == null) {
			return InteractionResult.PASS;
		}
		if (targetType == DroneItem.DroneType.KAMIKAZE && !ServerUpgradeUiSystem.hasUpgrade(player, IT_DRONE_KAMIKAZE)) {
			return InteractionResult.PASS;
		}

		DroneItem.DroneType currentType = resolveDroneType(root);
		if (currentType == targetType) {
			return InteractionResult.PASS;
		}
		if (currentType == DroneItem.DroneType.KAMIKAZE && resolveDroneKamikazePower(root) > 0) {
			return InteractionResult.PASS;
		}

		Item returnedTypeItem = switch (currentType) {
			case KAMIKAZE -> Items.STRING;
			case COMBAT -> Items.DISPENSER;
			default -> null;
		};
		setDroneType(root, targetType);
		if (targetType != DroneItem.DroneType.KAMIKAZE) {
			setDroneKamikazePower(root, DRONE_KAMIKAZE_NO_POWER);
		}
		triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
		notifyDroneNetworkChanged(root);

		if (!player.getAbilities().instabuild) {
			heldStack.shrink(1);
			giveOrDropTuningItem(player, root, returnedTypeItem);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryInstallNightVisionModule(ServerPlayer player, Entity root, ItemStack heldStack) {
		if (hasDroneNightVisionModule(root)) {
			return InteractionResult.PASS;
		}
		setDroneNightVisionModule(root, true);
		triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
		notifyDroneNetworkChanged(root);
		if (!player.getAbilities().instabuild) {
			heldStack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryInstallAutoAimModule(ServerPlayer player, Entity root, ItemStack heldStack) {
		if (hasDroneAutoAimModule(root)) {
			return InteractionResult.PASS;
		}
		setDroneAutoAimModule(root, true);
		triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
		notifyDroneNetworkChanged(root);
		if (!player.getAbilities().instabuild) {
			heldStack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryPaintDrone(ServerPlayer player, Entity root, ItemStack heldStack, DyeColor color) {
		if (color == null) {
			return InteractionResult.PASS;
		}
		DyeColor currentColor = resolveDronePaintColor(root);
		if (currentColor == color) {
			return InteractionResult.PASS;
		}
		if (!player.getAbilities().instabuild && currentColor != null) {
			giveOrDropTuningItem(player, root, dyeItemForColor(currentColor));
		}
		setDronePaintColor(root, color);
		triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
		notifyDroneNetworkChanged(root);
		if (!player.getAbilities().instabuild) {
			heldStack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult tryUnloadDroneModule(ServerPlayer player, Entity root) {
		if (player == null || root == null) {
			return InteractionResult.PASS;
		}

		int currentPower = resolveDroneKamikazePower(root);
		if (currentPower > DRONE_KAMIKAZE_NO_POWER) {
			setDroneKamikazePower(root, currentPower - 1);
			giveOrDropTuningItem(player, root, Items.TNT);
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
			notifyDroneNetworkChanged(root);
			return InteractionResult.SUCCESS;
		}
		if (hasDroneNightVisionModule(root)) {
			setDroneNightVisionModule(root, false);
			giveOrDropTuningItem(player, root, Items.SPIDER_EYE);
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
			notifyDroneNetworkChanged(root);
			return InteractionResult.SUCCESS;
		}
		if (hasDroneAutoAimModule(root)) {
			setDroneAutoAimModule(root, false);
			giveOrDropTuningItem(player, root, Items.CALIBRATED_SCULK_SENSOR);
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
			notifyDroneNetworkChanged(root);
			return InteractionResult.SUCCESS;
		}
		DyeColor paintColor = resolveDronePaintColor(root);
		if (paintColor != null) {
			setDronePaintColor(root, null);
			giveOrDropTuningItem(player, root, dyeItemForColor(paintColor));
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
			notifyDroneNetworkChanged(root);
			return InteractionResult.SUCCESS;
		}
		DroneItem.DroneType currentType = resolveDroneType(root);
		if (currentType == DroneItem.DroneType.COMBAT) {
			setDroneType(root, DroneItem.DroneType.NORMAL);
			giveOrDropTuningItem(player, root, Items.DISPENSER);
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
			notifyDroneNetworkChanged(root);
			return InteractionResult.SUCCESS;
		}
		if (currentType == DroneItem.DroneType.KAMIKAZE) {
			setDroneType(root, DroneItem.DroneType.NORMAL);
			giveOrDropTuningItem(player, root, Items.STRING);
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.POSITIVE);
			notifyDroneNetworkChanged(root);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	private static void giveOrDropTuningItem(ServerPlayer player, Entity root, Item item) {
		if (player == null || root == null || item == null) {
			return;
		}
		giveOrDropTuningItem(player, root, new ItemStack(item));
	}

	private static void giveOrDropTuningItem(ServerPlayer player, Entity root, ItemStack stack) {
		if (player == null || root == null || stack == null || stack.isEmpty()) {
			return;
		}
		boolean inserted = player.getInventory().add(stack);
		if (!inserted && root.level() instanceof ServerLevel level) {
			root.spawnAtLocation(level, stack);
		}
	}

	private static Item dyeItemForColor(DyeColor color) {
		if (color == null) {
			return null;
		}
		return switch (color) {
			case WHITE -> Items.WHITE_DYE;
			case ORANGE -> Items.ORANGE_DYE;
			case MAGENTA -> Items.MAGENTA_DYE;
			case LIGHT_BLUE -> Items.LIGHT_BLUE_DYE;
			case YELLOW -> Items.YELLOW_DYE;
			case LIME -> Items.LIME_DYE;
			case PINK -> Items.PINK_DYE;
			case GRAY -> Items.GRAY_DYE;
			case LIGHT_GRAY -> Items.LIGHT_GRAY_DYE;
			case CYAN -> Items.CYAN_DYE;
			case PURPLE -> Items.PURPLE_DYE;
			case BLUE -> Items.BLUE_DYE;
			case BROWN -> Items.BROWN_DYE;
			case GREEN -> Items.GREEN_DYE;
			case RED -> Items.RED_DYE;
			case BLACK -> Items.BLACK_DYE;
		};
	}

	private static String resolveRequiredUpgradeForDroneRoot(Entity root) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return null;
		}
		return resolveDroneType(root) == DroneItem.DroneType.KAMIKAZE ? IT_DRONE_KAMIKAZE : IT_DRONE_SCOUT;
	}

	private static ItemStack buildDroneDropStack(Entity root) {
		return DroneItem.createConfiguredStack(
				ModItems.DRONE,
				resolveDroneType(root),
				resolveDroneKamikazePower(root),
				hasDroneNightVisionModule(root),
				hasDroneAutoAimModule(root),
				resolveDronePaintColor(root)
		);
	}

	private static boolean isKamikazeDrone(Entity root) {
		return resolveDroneKamikazePower(root) > DRONE_KAMIKAZE_NO_POWER;
	}

	private static DroneItem.DroneType resolveDroneType(Entity root) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return DroneItem.DroneType.NORMAL;
		}
		for (String tag : root.getTags()) {
			if (!tag.startsWith(DRONE_TYPE_TAG_PREFIX)) {
				continue;
			}
			String rawType = tag.substring(DRONE_TYPE_TAG_PREFIX.length());
			try {
				return DroneItem.DroneType.valueOf(rawType.trim().toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return resolveDroneKamikazePower(root) > DRONE_KAMIKAZE_NO_POWER ? DroneItem.DroneType.KAMIKAZE : DroneItem.DroneType.NORMAL;
	}

	private static int resolveDroneKamikazePower(Entity root) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return DRONE_KAMIKAZE_NO_POWER;
		}
		int resolvedPower = DRONE_KAMIKAZE_NO_POWER;
		for (String tag : root.getTags()) {
			if (!tag.startsWith(DRONE_KAMIKAZE_POWER_TAG_PREFIX)) {
				continue;
			}
			try {
				int parsed = Integer.parseInt(tag.substring(DRONE_KAMIKAZE_POWER_TAG_PREFIX.length()));
				resolvedPower = net.minecraft.util.Mth.clamp(parsed, DRONE_KAMIKAZE_MIN_POWER, DRONE_KAMIKAZE_MAX_POWER);
				break;
			} catch (NumberFormatException ignored) {
			}
		}
		return resolvedPower;
	}

	private static void setDroneKamikazePower(Entity root, int power) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		int clampedPower = net.minecraft.util.Mth.clamp(power, DRONE_KAMIKAZE_NO_POWER, DRONE_KAMIKAZE_MAX_POWER);
		removeDroneKamikazePowerTags(root);
		if (clampedPower > DRONE_KAMIKAZE_NO_POWER) {
			root.addTag(droneKamikazePowerTag(clampedPower));
		}
		if (clampedPower > DRONE_KAMIKAZE_NO_POWER && resolveDroneType(root) != DroneItem.DroneType.KAMIKAZE) {
			setDroneType(root, DroneItem.DroneType.KAMIKAZE);
		}
		syncDroneDisplayLayers(root);
	}

	private static void removeDroneKamikazePowerTags(Entity root) {
		if (root == null) {
			return;
		}
		for (String tag : new ArrayList<>(root.getTags())) {
			if (tag != null && tag.startsWith(DRONE_KAMIKAZE_POWER_TAG_PREFIX)) {
				root.removeTag(tag);
			}
		}
	}

	private static void applyDroneVisualState(
			Entity root,
			DroneItem.DroneType type,
			int kamikazePower,
			boolean nightVision,
			boolean autoAim,
			DyeColor paintColor
	) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		setDroneType(root, type);
		setDroneKamikazePower(root, kamikazePower);
		setDroneNightVisionModule(root, nightVision);
		setDroneAutoAimModule(root, autoAim);
		setDronePaintColor(root, paintColor);
	}

	private static void setDroneType(Entity root, DroneItem.DroneType type) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		DroneItem.DroneType previousType = resolveDroneType(root);
		for (String tag : new ArrayList<>(root.getTags())) {
			if (tag != null && tag.startsWith(DRONE_TYPE_TAG_PREFIX)) {
				root.removeTag(tag);
			}
		}
		DroneItem.DroneType resolvedType = type == null ? DroneItem.DroneType.NORMAL : type;
		if (resolvedType != DroneItem.DroneType.NORMAL) {
			root.addTag(DRONE_TYPE_TAG_PREFIX + resolvedType.name().toLowerCase(java.util.Locale.ROOT));
		}
		if (previousType == DroneItem.DroneType.COMBAT && resolvedType != DroneItem.DroneType.COMBAT) {
			dropDroneTurretInventory(root);
			NEXT_DRONE_TURRET_FIRE_TICK.remove(root.getUUID());
		}
		syncDroneDisplayLayers(root);
	}

	private static boolean hasDroneTurretModule(Entity root) {
		return resolveDroneType(root) == DroneItem.DroneType.COMBAT;
	}

	private static boolean openDroneTurretMenu(ServerPlayer player, Entity root) {
		if (player == null || root == null || !root.isAlive() || !hasDroneTurretModule(root)) {
			return false;
		}
		TurretInventory inventory = droneTurretInventory(root);
		return player.openMenu(new SimpleMenuProvider(
				(syncId, playerInventory, opener) -> new DispenserMenu(syncId, playerInventory, inventory),
				Component.literal("Турель дрона")
		)).isPresent();
	}

	private static TurretInventory droneTurretInventory(Entity root) {
		return DRONE_TURRET_INVENTORIES.computeIfAbsent(root.getUUID(), ignored -> new TurretInventory());
	}

	private static boolean isDroneTurretProjectileStack(ItemStack stack) {
		return stack != null && !stack.isEmpty() && DRONE_TURRET_PROJECTILES.contains(stack.getItem());
	}

	private static void dropDroneTurretInventory(Entity root) {
		if (root == null) {
			return;
		}
		TurretInventory inventory = DRONE_TURRET_INVENTORIES.remove(root.getUUID());
		if (inventory == null || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			root.spawnAtLocation(level, stack.copy());
			inventory.setItem(slot, ItemStack.EMPTY);
		}
	}

	public static boolean handleControlledUseItem(ServerPlayer player, InteractionHand hand) {
		if (player == null || hand != InteractionHand.MAIN_HAND || player.level() == null) {
			return false;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null || !Objects.equals(resolveAuthoritativeDroneControllerId(session.droneUuid()), player.getUUID())) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		Entity root = server == null ? null : findDroneRoot(server, session.droneDimension(), session.droneUuid());
		if (root == null || !root.isAlive()) {
			return false;
		}
		return fireDroneTurret(player, root, session, false);
	}

	private static void handleControlledTurretJumpInput(
			ServerPlayer player,
			Entity root,
			DroneControlSession session,
			DroneInputState input
	) {
		if (player == null || root == null || session == null || input == null) {
			return;
		}
		if (!hasDroneTurretModule(root)) {
			return;
		}
		if (!input.jump()) {
			return;
		}
		fireDroneTurret(player, root, session, false);
	}

	private static boolean fireDroneTurret(
			ServerPlayer player,
			Entity root,
			DroneControlSession session,
			boolean ignoreInputSuppression
	) {
		if (player == null || root == null || session == null || !(root.level() instanceof ServerLevel level)) {
			return false;
		}
		if (!hasDroneTurretModule(root)) {
			return false;
		}
		long now = level.getGameTime();
		if (!ignoreInputSuppression && now < session.turretInputSuppressedUntilTick()) {
			return true;
		}
		long nextAllowed = NEXT_DRONE_TURRET_FIRE_TICK.getOrDefault(root.getUUID(), Long.MIN_VALUE);
		if (now < nextAllowed) {
			return true;
		}
		TurretInventory inventory = droneTurretInventory(root);
		int slot = selectRandomDroneTurretProjectileSlot(level, inventory);
		if (slot < 0) {
			level.levelEvent(1001, BlockPos.containing(droneCameraOrigin(root)), 0);
			NEXT_DRONE_TURRET_FIRE_TICK.put(root.getUUID(), now + DRONE_TURRET_FIRE_COOLDOWN_TICKS);
			return true;
		}
		ItemStack stack = inventory.getItem(slot);
		ItemStack shotStack = stack.copyWithCount(1);
		Vec3 direction = controlledTurretDirection(player, session);
		Vec3 origin = controlledTurretMuzzleOrigin(root, session, direction);
		Projectile projectile = createDroneTurretProjectile(level, player, origin, direction, shotStack);
		if (projectile == null) {
			level.levelEvent(1001, BlockPos.containing(origin), 0);
			NEXT_DRONE_TURRET_FIRE_TICK.put(root.getUUID(), now + DRONE_TURRET_FIRE_COOLDOWN_TICKS);
			return true;
		}

		level.addFreshEntity(projectile);
		stack.shrink(1);
		inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
		inventory.setChanged();
		level.levelEvent(1000, BlockPos.containing(origin), 0);
		NEXT_DRONE_TURRET_FIRE_TICK.put(root.getUUID(), now + DRONE_TURRET_FIRE_COOLDOWN_TICKS);
		return true;
	}

	private static int selectRandomDroneTurretProjectileSlot(ServerLevel level, TurretInventory inventory) {
		if (level == null || inventory == null) {
			return -1;
		}
		int selectedSlot = -1;
		int validCount = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (!isDroneTurretProjectileStack(inventory.getItem(slot))) {
				continue;
			}
			validCount++;
			if (level.random.nextInt(validCount) == 0) {
				selectedSlot = slot;
			}
		}
		return selectedSlot;
	}

	private static Vec3 controlledTurretDirection(ServerPlayer player, DroneControlSession session) {
		Vec3 direction = session == null ? null : Vec3.directionFromRotation(session.proxyPitch(), session.proxyYaw());
		if (direction == null || direction.lengthSqr() < 1.0E-8D) {
			direction = player == null ? Vec3.ZERO : player.getLookAngle();
		}
		if (direction.lengthSqr() < 1.0E-8D) {
			return new Vec3(0.0D, 0.0D, 1.0D);
		}
		return direction.normalize();
	}

	private static Vec3 controlledTurretMuzzleOrigin(Entity root, DroneControlSession session, Vec3 direction) {
		Vec3 fallback = root == null ? Vec3.ZERO : droneCameraOrigin(root);
		Vec3 base = session == null ? fallback : finiteVecOr(session.proxyPos(), fallback);
		return droneCameraOrigin(base).add(direction.normalize().scale(DRONE_TURRET_MUZZLE_FORWARD_OFFSET));
	}

	private static Projectile createDroneTurretProjectile(
			ServerLevel level,
			ServerPlayer owner,
			Vec3 origin,
			Vec3 direction,
			ItemStack stack
	) {
		if (level == null || origin == null || direction == null || stack == null || stack.isEmpty()) {
			return null;
		}
		Vec3 normalized = direction.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
		Projectile projectile;
		float speed;
		Item item = stack.getItem();
		if (item == Items.SPECTRAL_ARROW) {
			projectile = new SpectralArrow(level, origin.x, origin.y, origin.z, stack, new ItemStack(Items.CROSSBOW));
			speed = 3.0F;
		} else if (item == Items.ARROW || item == Items.TIPPED_ARROW) {
			projectile = new Arrow(level, origin.x, origin.y, origin.z, stack, new ItemStack(Items.CROSSBOW));
			speed = 3.0F;
		} else if (item == Items.TRIDENT) {
			projectile = new ThrownTrident(level, origin.x, origin.y, origin.z, stack);
			speed = 2.5F;
		} else if (item == Items.FIREWORK_ROCKET) {
			projectile = new FireworkRocketEntity(level, stack, owner, origin.x, origin.y, origin.z, true);
			speed = 1.8F;
		} else if (item == Items.FIRE_CHARGE) {
			projectile = new SmallFireball(level, origin.x, origin.y, origin.z, normalized);
			speed = 1.5F;
		} else if (item == Items.WIND_CHARGE) {
			projectile = new WindCharge(level, origin.x, origin.y, origin.z, normalized);
			speed = 1.4F;
		} else if (item == Items.SNOWBALL) {
			projectile = new Snowball(level, origin.x, origin.y, origin.z, stack);
			speed = 1.5F;
		} else if (item == Items.EGG) {
			projectile = new ThrownEgg(level, origin.x, origin.y, origin.z, stack);
			speed = 1.5F;
		} else if (item == Items.EXPERIENCE_BOTTLE) {
			projectile = new ThrownExperienceBottle(level, origin.x, origin.y, origin.z, stack);
			speed = 1.3F;
		} else if (item == Items.SPLASH_POTION) {
			projectile = new ThrownSplashPotion(level, origin.x, origin.y, origin.z, stack);
			speed = 1.3F;
		} else if (item == Items.LINGERING_POTION) {
			projectile = new ThrownLingeringPotion(level, origin.x, origin.y, origin.z, stack);
			speed = 1.3F;
		} else {
			return null;
		}
		projectile.setOwner(owner);
		projectile.setPos(origin.x, origin.y, origin.z);
		projectile.shoot(normalized.x, normalized.y, normalized.z, speed, 0.0F);
		return projectile;
	}

	private static void syncControlledDroneTurretAirTrigger(ServerPlayer player, Entity root) {
		if (player == null || root == null || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null) {
			removeControlledDroneTurretAirTrigger(player);
			return;
		}
		if (!shouldMaintainControlledDroneTurretAirTrigger(player, root)) {
			removeControlledDroneTurretAirTrigger(player);
			return;
		}
		Vec3 look = controlledTurretDirection(player, session);
		Vec3 cameraOrigin = droneCameraOrigin(finiteVecOr(session.proxyPos(), root.position()));
		Vec3 pos = cameraOrigin
				.add(look.normalize().scale(DRONE_TURRET_AIR_TRIGGER_HEAD_FORWARD_OFFSET))
				.subtract(0.0D, DRONE_TURRET_AIR_TRIGGER_HEIGHT * 0.5D, 0.0D);
		UUID triggerId = CONTROLLED_DRONE_TURRET_TRIGGERS.get(player.getUUID());
		Interaction trigger = null;
		if (triggerId != null) {
			Entity existing = level.getEntity(triggerId);
			if (existing instanceof Interaction existingTrigger && existingTrigger.isAlive()) {
				trigger = existingTrigger;
			}
		}
		if (trigger == null) {
			trigger = new Interaction(EntityType.INTERACTION, level);
			trigger.addTag(DRONE_TURRET_TRIGGER_TAG);
			trigger.addTag(DRONE_TURRET_TRIGGER_OWNER_TAG_PREFIX + player.getUUID());
			trigger.setNoGravity(true);
			trigger.setSilent(true);
			trigger.setInvisible(true);
			trigger.setResponse(false);
			trigger.setWidth(DRONE_TURRET_AIR_TRIGGER_WIDTH);
			trigger.setHeight(DRONE_TURRET_AIR_TRIGGER_HEIGHT);
			trigger.setPos(pos.x, pos.y, pos.z);
			trigger.setDeltaMovement(Vec3.ZERO);
			trigger.setYRot(session.proxyYaw());
			trigger.setXRot(session.proxyPitch());
			level.addFreshEntity(trigger);
			CONTROLLED_DRONE_TURRET_TRIGGERS.put(player.getUUID(), trigger.getUUID());
			sendControlledOperatorPacket(player, new ClientboundAddEntityPacket(
					trigger.getId(),
					trigger.getUUID(),
					trigger.getX(),
					trigger.getY(),
					trigger.getZ(),
					trigger.getXRot(),
					trigger.getYRot(),
					EntityType.INTERACTION,
					0,
					Vec3.ZERO,
					trigger.getYHeadRot()
			));
			List<SynchedEntityData.DataValue<?>> trackedData = trigger.getEntityData().getNonDefaultValues();
			if (trackedData != null && !trackedData.isEmpty()) {
				sendControlledOperatorPacket(player, new ClientboundSetEntityDataPacket(trigger.getId(), trackedData));
			}
		}
		trigger.setInvisible(true);
		trigger.setPos(pos.x, pos.y, pos.z);
		trigger.setDeltaMovement(Vec3.ZERO);
		trigger.setYRot(session.proxyYaw());
		trigger.setXRot(session.proxyPitch());
		sendControlledOperatorPacket(player, ClientboundEntityPositionSyncPacket.of(trigger));
	}

	private static boolean shouldMaintainControlledDroneTurretAirTrigger(ServerPlayer player, Entity root) {
		if (player == null
				|| root == null
				|| !root.isAlive()
				|| !hasDroneTurretModule(root)
				|| !player.isAlive()
				|| player.isSpectator()) {
			return false;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		return session != null
				&& Objects.equals(session.droneUuid(), root.getUUID())
				&& Objects.equals(resolveAuthoritativeDroneControllerId(root.getUUID()), player.getUUID());
	}

	private static void removeControlledDroneTurretAirTrigger(ServerPlayer player) {
		if (player == null) {
			return;
		}
		UUID triggerId = CONTROLLED_DRONE_TURRET_TRIGGERS.remove(player.getUUID());
		if (triggerId == null || player.level() == null || player.level().getServer() == null) {
			return;
		}
		Entity trigger = findEntity(player.level().getServer(), triggerId);
		if (trigger != null) {
			sendControlledOperatorPacket(player, new ClientboundRemoveEntitiesPacket(trigger.getId()));
			trigger.discard();
		}
	}

	private static boolean hasDroneNightVisionModule(Entity root) {
		return root != null && root.getTags().contains(DRONE_NIGHT_VISION_TAG);
	}

	private static void setDroneNightVisionModule(Entity root, boolean enabled) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		if (enabled) {
			root.addTag(DRONE_NIGHT_VISION_TAG);
		} else {
			root.removeTag(DRONE_NIGHT_VISION_TAG);
		}
		syncDroneDisplayLayers(root);
	}

	private static boolean hasDroneAutoAimModule(Entity root) {
		return root != null && root.getTags().contains(DRONE_AUTO_AIM_TAG);
	}

	private static void setDroneAutoAimModule(Entity root, boolean enabled) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		boolean changed = hasDroneAutoAimModule(root) != enabled;
		if (enabled) {
			root.addTag(DRONE_AUTO_AIM_TAG);
		} else {
			root.removeTag(DRONE_AUTO_AIM_TAG);
		}
		syncDroneDisplayLayers(root);
		if (changed) {
			syncDroneAutoAimModuleState(root, enabled);
		}
	}

	private static void syncDroneAutoAimModuleState(Entity root, boolean enabled) {
		if (root == null) {
			return;
		}
		UncontrolledDroneState uncontrolledState = UNCONTROLLED_DRONES.get(root.getUUID());
		if (!enabled && uncontrolledState != null) {
			uncontrolledState.setAutoAimTarget(null);
		}
		UUID controllerId = resolveAuthoritativeDroneControllerId(root.getUUID());
		if (controllerId == null || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer controller = level.getServer().getPlayerList().getPlayer(controllerId);
		DroneControlSession session = ACTIVE_SESSIONS.get(controllerId);
		if (controller == null || session == null) {
			return;
		}
		syncControlledOperatorAutoAimInteractionRange(controller, enabled);
		if (!enabled) {
			clearControlledAutoAimTarget(controller, session);
		} else if (session.autoAimTarget() != null) {
			showControlledAutoAimTarget(controller, session, session.autoAimTarget());
		}
	}

	private static DyeColor resolveDronePaintColor(Entity root) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return null;
		}
		for (String tag : root.getTags()) {
			if (!tag.startsWith(DRONE_PAINT_TAG_PREFIX)) {
				continue;
			}
			String rawColor = tag.substring(DRONE_PAINT_TAG_PREFIX.length());
			for (DyeColor color : DyeColor.values()) {
				if (color.getName().equalsIgnoreCase(rawColor)) {
					return color;
				}
			}
		}
		return null;
	}

	private static void setDronePaintColor(Entity root, DyeColor color) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return;
		}
		for (String tag : new ArrayList<>(root.getTags())) {
			if (tag != null && tag.startsWith(DRONE_PAINT_TAG_PREFIX)) {
				root.removeTag(tag);
			}
		}
		if (color != null) {
			root.addTag(DRONE_PAINT_TAG_PREFIX + color.getName());
		}
		syncDroneDisplayLayers(root);
	}

	private static void playDroneKamikazeInsertFeedback(ServerLevel level, Vec3 origin, int newPower) {
		if (level == null || origin == null) {
			return;
		}
		float fillRatio = net.minecraft.util.Mth.clamp(newPower / (float) DRONE_KAMIKAZE_MAX_POWER, 0.0F, 1.0F);
		float pitch = 0.7F + 0.5F * fillRatio;
		level.playSound(
				null,
				origin.x,
				origin.y,
				origin.z,
				SoundEvents.DECORATED_POT_INSERT,
				SoundSource.PLAYERS,
				1.0F,
				pitch
		);
		level.sendParticles(
				ParticleTypes.DUST_PLUME,
				origin.x,
				origin.y + DRONE_HEIGHT * 0.50D,
				origin.z,
				7,
				0.0D,
				0.0D,
				0.0D,
				0.0D
		);
	}

	private static void playDroneKamikazeInsertFailFeedback(ServerLevel level, Vec3 origin) {
		if (level == null || origin == null) {
			return;
		}
		level.playSound(
				null,
				origin.x,
				origin.y,
				origin.z,
				SoundEvents.DECORATED_POT_INSERT_FAIL,
				SoundSource.PLAYERS,
				1.0F,
				1.0F
		);
	}

	private static String droneKamikazePowerTag(int power) {
		return DRONE_KAMIKAZE_POWER_TAG_PREFIX
				+ net.minecraft.util.Mth.clamp(power, DRONE_KAMIKAZE_MIN_POWER, DRONE_KAMIKAZE_MAX_POWER);
	}

	private static void detonateKamikazeDrone(ServerLevel level, Vec3 origin, Vec3 velocity, int power) {
		if (level == null || origin == null) {
			return;
		}
		int charges = net.minecraft.util.Mth.clamp(power, DRONE_KAMIKAZE_MIN_POWER, DRONE_KAMIKAZE_MAX_POWER);
		Vec3 inheritedVelocity = velocity == null ? Vec3.ZERO : velocity.scale(0.30D);

		for (int index = 0; index < charges; index++) {
			Entity created = EntityType.TNT.create(level, EntitySpawnReason.TRIGGERED);
			if (!(created instanceof PrimedTnt tnt)) {
				continue;
			}
			double angle = charges <= 1 ? 0.0D : (Math.PI * 2.0D * index) / charges;
			double radius = charges <= 1 ? 0.0D : DRONE_KAMIKAZE_TNT_SPREAD;
			double x = origin.x + Math.cos(angle) * radius;
			double y = origin.y + 0.04D;
			double z = origin.z + Math.sin(angle) * radius;
			tnt.setPos(x, y, z);
			tnt.setFuse(0);
			tnt.setDeltaMovement(
					inheritedVelocity.add(
							(level.random.nextDouble() - 0.5D) * 0.08D,
							0.03D + level.random.nextDouble() * 0.03D,
							(level.random.nextDouble() - 0.5D) * 0.08D
					)
			);
			level.addFreshEntity(tnt);
		}
	}

	private static SoundEvent resolveVanillaSoundEvent(Identifier soundId, SoundEvent fallback) {
		if (soundId == null) {
			return fallback;
		}
		SoundEvent resolved = BuiltInRegistries.SOUND_EVENT.getValue(soundId);
		return resolved != null ? resolved : fallback;
	}

	private static Entity resolveDroneRoot(Entity entity) {
		if (entity != null && entity.getTags().contains(DRONE_ROOT_TAG)) {
			return entity;
		}
		if (entity != null && entity.getTags().contains(DRONE_DISPLAY_TAG) && entity.level() instanceof ServerLevel level) {
			for (String tag : entity.getTags()) {
				if (!tag.startsWith(DRONE_DISPLAY_OWNER_TAG_PREFIX)) {
					continue;
				}
				try {
					UUID rootId = UUID.fromString(tag.substring(DRONE_DISPLAY_OWNER_TAG_PREFIX.length()));
					Entity root = level.getEntity(rootId);
					if (root != null && root.getTags().contains(DRONE_ROOT_TAG)) {
						return root;
					}
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
		return null;
	}

	private static Display.ItemDisplay createDroneDisplay(
			ServerLevel level,
			Vec3 position,
			float yRot,
			float xRot,
			ItemStack displayStack,
			String layerKey
	) {
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.addTag(DRONE_DISPLAY_TAG);
		display.addTag(DRONE_DISPLAY_LAYER_TAG_PREFIX + (layerKey == null || layerKey.isBlank() ? DRONE_DISPLAY_LAYER_BASE : layerKey));
		display.setPos(position.x, position.y, position.z);
		display.setYRot(yRot);
		display.setXRot(xRot);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setItemStack(displayStack == null ? DroneItem.createDisplayStack() : displayStack);
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(DRONE_DISPLAY_VIEW_RANGE);
		display.setPosRotInterpolationDuration(DRONE_DISPLAY_INTERPOLATION_TICKS);
		display.setTransformationInterpolationDelay(0);
		display.setTransformationInterpolationDuration(DRONE_DISPLAY_INTERPOLATION_TICKS);
		display.setTransformation(Transformation.identity());
		return display;
	}

	private static Interaction createDroneCameraAnchor(ServerLevel level, Vec3 position, float yRot, float xRot) {
		Interaction anchor = new Interaction(EntityType.INTERACTION, level);
		anchor.addTag(DRONE_CAMERA_TAG);
		anchor.setPos(position.x, position.y, position.z);
		anchor.setYRot(yRot);
		anchor.setXRot(xRot);
		anchor.setNoGravity(true);
		anchor.setInvulnerable(true);
		anchor.setSilent(true);
		anchor.setResponse(false);
		anchor.setWidth(DRONE_CAMERA_ANCHOR_SIZE);
		anchor.setHeight(DRONE_CAMERA_ANCHOR_SIZE);
		return anchor;
	}

	private static void syncDroneDisplay(Entity root, float yRot, float xRot, double forwardDrive, double strafeDrive) {
		List<Display.ItemDisplay> displays = findDroneDisplayLayers(root);
		if (displays.isEmpty()) {
			syncDroneDisplayLayers(root);
			displays = findDroneDisplayLayers(root);
		}
		boolean controlled = isDroneActivelyControlled(root);
		for (Display.ItemDisplay display : displays) {
			if (display.isPassenger()) {
				display.stopRiding();
			}
			display.setPosRotInterpolationDuration(DRONE_DISPLAY_INTERPOLATION_TICKS);
			display.setTransformationInterpolationDelay(0);
			display.setTransformationInterpolationDuration(DRONE_DISPLAY_INTERPOLATION_TICKS);
			display.setYRot(yRot);
			display.setXRot(controlled ? 0.0F : xRot);
			display.setTransformation(buildDroneDisplayTransformation(root, forwardDrive, strafeDrive));
			display.setPos(root.getX(), root.getY(), root.getZ());
		}
	}

	private static Transformation buildDroneDisplayTransformation(Entity root, double forwardDrive, double strafeDrive) {
		double forwardNorm = forwardDrive / DroneFlightPhysics.MAX_FORWARD_DRIVE;
		double strafeNorm = strafeDrive / DroneFlightPhysics.MAX_STRAFE_DRIVE;
		forwardNorm = net.minecraft.util.Mth.clamp(forwardNorm, -1.0D, 1.0D);
		strafeNorm = net.minecraft.util.Mth.clamp(strafeNorm, -1.0D, 1.0D);

		// Forward drive pitches the nose down; strafe drive rolls into the turn.
		float pitchTiltRad = (float) Math.toRadians((float) (forwardNorm * DRONE_MAX_TILT_DEGREES));
		float rollTiltRad = (float) Math.toRadians((float) (strafeNorm * DRONE_MAX_TILT_DEGREES));
		Quaternionf rotation = new Quaternionf().rotateXYZ(pitchTiltRad, 0.0F, rollTiltRad);
		DroneDisplayWobble wobble = resolveActiveDroneDisplayWobble(root);
		if (wobble != null) {
			applyVanillaPotWobbleRotation(rotation, wobble);
		}

		return new Transformation(
				new Vector3f(0.0F, DRONE_DISPLAY_Y_OFFSET, 0.0F),
				rotation,
				new Vector3f(1.0F, 1.0F, 1.0F),
				new Quaternionf()
		);
	}

	private static void triggerDroneDisplayWobble(Entity root, DroneDisplayWobbleType type) {
		if (root == null || type == null || root.level() == null) {
			return;
		}
		DISPLAY_WOBBLE_BY_DRONE.put(root.getUUID(), new DroneDisplayWobbleState(type, root.level().getGameTime()));
	}

	private static DroneDisplayWobble resolveActiveDroneDisplayWobble(Entity root) {
		if (root == null || root.level() == null) {
			return null;
		}
		DroneDisplayWobbleState state = DISPLAY_WOBBLE_BY_DRONE.get(root.getUUID());
		if (state == null || state.type() == null) {
			return null;
		}

		float progress = (root.level().getGameTime() - state.startedAtTick()) / (float) state.type().lengthInTicks();
		if (progress < 0.0F || progress > 1.0F) {
			DISPLAY_WOBBLE_BY_DRONE.remove(root.getUUID());
			return null;
		}
		return new DroneDisplayWobble(state.type(), progress);
	}

	private static void applyVanillaPotWobbleRotation(Quaternionf rotation, DroneDisplayWobble wobble) {
		if (rotation == null || wobble == null || wobble.type() == null) {
			return;
		}
		float progress = wobble.progress();
		if (wobble.type() == DroneDisplayWobbleType.POSITIVE) {
			float phase = progress * DRONE_VANILLA_WOBBLE_PROGRESS_TO_RADIANS;
			float wobbleX = ((-1.5F * net.minecraft.util.Mth.cos(phase) + 0.5F) * net.minecraft.util.Mth.sin(phase / 2.0F))
					* DRONE_VANILLA_WOBBLE_ROTATION_SCALE;
			float wobbleZ = net.minecraft.util.Mth.sin(phase) * DRONE_VANILLA_WOBBLE_ROTATION_SCALE;
			rotation.rotateX(wobbleX);
			rotation.rotateZ(wobbleZ);
			return;
		}

		float yawWobble = net.minecraft.util.Mth.sin(-progress * DRONE_VANILLA_WOBBLE_NEGATIVE_PROGRESS_TO_RADIANS)
				* DRONE_VANILLA_WOBBLE_NEGATIVE_SCALE
				* (1.0F - progress);
		rotation.rotateY(yawWobble);
	}

	private static Entity findDroneDisplay(Entity root) {
		if (root == null || !(root.level() instanceof ServerLevel level)) {
			return null;
		}
		UUID displayId = DISPLAYS_BY_DRONE.get(root.getUUID());
		Entity display = displayId == null ? null : level.getEntity(displayId);
		if (display != null
				&& display.getTags().contains(DRONE_DISPLAY_TAG)
				&& display.getTags().contains(DRONE_DISPLAY_LAYER_TAG_PREFIX + DRONE_DISPLAY_LAYER_BASE)) {
			return display;
		}
		for (Entity candidate : level.getEntities(root, root.getBoundingBox().inflate(8.0D))) {
			if (!candidate.getTags().contains(DRONE_DISPLAY_TAG)) {
				continue;
			}
			if (candidate.getTags().contains(DRONE_DISPLAY_OWNER_TAG_PREFIX + root.getUUID())
					&& candidate.getTags().contains(DRONE_DISPLAY_LAYER_TAG_PREFIX + DRONE_DISPLAY_LAYER_BASE)) {
				DISPLAYS_BY_DRONE.put(root.getUUID(), candidate.getUUID());
				return candidate;
			}
		}
		return null;
	}

	private static List<Display.ItemDisplay> findDroneDisplayLayers(Entity root) {
		List<Display.ItemDisplay> displays = new ArrayList<>();
		if (root == null || !(root.level() instanceof ServerLevel level)) {
			return displays;
		}
		Set<UUID> seen = new LinkedHashSet<>();
		collectDroneDisplayLayers(level, root, root.getBoundingBox().inflate(8.0D), displays, seen);
		UUID registeredBaseId = DISPLAYS_BY_DRONE.get(root.getUUID());
		Entity registeredBase = registeredBaseId == null ? null : level.getEntity(registeredBaseId);
		if (registeredBase != null && registeredBase.isAlive()) {
			collectDroneDisplayLayer(root, registeredBase, displays, seen);
			collectDroneDisplayLayers(level, root, registeredBase.getBoundingBox().inflate(8.0D), displays, seen);
		} else if (registeredBaseId != null) {
			DISPLAYS_BY_DRONE.remove(root.getUUID(), registeredBaseId);
		}
		return displays;
	}

	private static void collectDroneDisplayLayers(
			ServerLevel level,
			Entity root,
			AABB bounds,
			List<Display.ItemDisplay> displays,
			Set<UUID> seen
	) {
		if (level == null || root == null || bounds == null || displays == null || seen == null) {
			return;
		}
		for (Entity candidate : level.getEntities(root, bounds)) {
			collectDroneDisplayLayer(root, candidate, displays, seen);
		}
	}

	private static void collectDroneDisplayLayer(
			Entity root,
			Entity candidate,
			List<Display.ItemDisplay> displays,
			Set<UUID> seen
	) {
		if (root == null
				|| !(candidate instanceof Display.ItemDisplay display)
				|| displays == null
				|| seen == null
				|| !candidate.isAlive()) {
			return;
		}
		if (!candidate.getTags().contains(DRONE_DISPLAY_TAG)) {
			return;
		}
		if (!candidate.getTags().contains(DRONE_DISPLAY_OWNER_TAG_PREFIX + root.getUUID())) {
			return;
		}
		if (seen.add(candidate.getUUID())) {
			displays.add(display);
		}
	}

	private static void syncDroneDisplayLayers(Entity root) {
		if (root == null || !(root.level() instanceof ServerLevel level)) {
			return;
		}

		DroneItem.DroneType droneType = resolveDroneType(root);
		int kamikazePower = resolveDroneKamikazePower(root);
		boolean nightVision = hasDroneNightVisionModule(root);
		boolean autoAim = hasDroneAutoAimModule(root);
		DyeColor paintColor = resolveDronePaintColor(root);

		Map<String, ItemStack> desiredLayers = new LinkedHashMap<>();
		desiredLayers.put(
				DRONE_DISPLAY_LAYER_BASE,
				DroneItem.createDisplayStack(ModItems.DRONE, droneType, kamikazePower, nightVision, autoAim, paintColor)
		);
		for (DroneItem.DisplayLayer layer : DroneItem.resolveDisplayLayers(droneType, kamikazePower, nightVision, autoAim, paintColor)) {
			if (layer == null || layer.key() == null || layer.key().isBlank() || layer.modelId() == null) {
				continue;
			}
			desiredLayers.put(layer.key(), DroneItem.createDisplayLayerStack(layer.modelId()));
		}

		Map<String, Display.ItemDisplay> existingLayers = new LinkedHashMap<>();
		for (Display.ItemDisplay display : findDroneDisplayLayers(root)) {
			String layerKey = resolveDroneDisplayLayerKey(display);
			if (layerKey == null || layerKey.isBlank()) {
				layerKey = DRONE_DISPLAY_LAYER_BASE;
			}
			Display.ItemDisplay previous = existingLayers.putIfAbsent(layerKey, display);
			if (previous != null && previous != display) {
				display.discard();
			}
		}

		for (Map.Entry<String, Display.ItemDisplay> entry : existingLayers.entrySet()) {
			if (desiredLayers.containsKey(entry.getKey())) {
				continue;
			}
			entry.getValue().discard();
		}

		for (Map.Entry<String, ItemStack> entry : desiredLayers.entrySet()) {
			String layerKey = entry.getKey();
			Display.ItemDisplay existing = existingLayers.get(layerKey);
			if (existing != null) {
				existing.setItemStack(entry.getValue());
				if (DRONE_DISPLAY_LAYER_BASE.equals(layerKey)) {
					DISPLAYS_BY_DRONE.put(root.getUUID(), existing.getUUID());
				}
				continue;
			}
			Display.ItemDisplay created = createDroneDisplay(level, root.position(), root.getYRot(), root.getXRot(), entry.getValue(), layerKey);
			created.addTag(DRONE_DISPLAY_OWNER_TAG_PREFIX + root.getUUID());
			level.addFreshEntity(created);
			if (DRONE_DISPLAY_LAYER_BASE.equals(layerKey)) {
				DISPLAYS_BY_DRONE.put(root.getUUID(), created.getUUID());
			}
		}
	}

	private static String resolveDroneDisplayLayerKey(Entity entity) {
		if (entity == null) {
			return null;
		}
		for (String tag : entity.getTags()) {
			if (tag == null || !tag.startsWith(DRONE_DISPLAY_LAYER_TAG_PREFIX)) {
				continue;
			}
			return tag.substring(DRONE_DISPLAY_LAYER_TAG_PREFIX.length());
		}
		return null;
	}

	private static Entity findDroneCameraAnchor(Entity root) {
		if (root == null || !(root.level() instanceof ServerLevel level)) {
			return null;
		}
		UUID anchorId = CAMERA_ANCHORS_BY_DRONE.get(root.getUUID());
		Entity anchor = anchorId == null ? null : level.getEntity(anchorId);
		if (anchor != null && anchor.getTags().contains(DRONE_CAMERA_TAG)) {
			return anchor;
		}
		for (Entity candidate : level.getEntities(root, root.getBoundingBox().inflate(16.0D))) {
			if (!candidate.getTags().contains(DRONE_CAMERA_TAG)) {
				continue;
			}
			if (candidate.getTags().contains(DRONE_CAMERA_OWNER_TAG_PREFIX + root.getUUID())) {
				CAMERA_ANCHORS_BY_DRONE.put(root.getUUID(), candidate.getUUID());
				return candidate;
			}
		}
		return null;
	}

	private static Entity ensureDroneCameraAnchor(Entity root) {
		if (root == null || !root.isAlive() || !(root.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity anchor = findDroneCameraAnchor(root);
		if (anchor != null) {
			return anchor;
		}
		Vec3 origin = resolveSafeDroneCameraOrigin(root, droneCameraOrigin(root));
		Interaction created = createDroneCameraAnchor(level, origin, root.getYRot(), root.getXRot());
		created.addTag(DRONE_CAMERA_OWNER_TAG_PREFIX + root.getUUID());
		level.addFreshEntity(created);
		CAMERA_ANCHORS_BY_DRONE.put(root.getUUID(), created.getUUID());
		return created;
	}

	private static void syncDroneCameraAnchor(Entity root, Vec3 velocity) {
		Entity anchor = ensureDroneCameraAnchor(root);
		if (anchor == null) {
			return;
		}
		Vec3 origin = resolveSafeDroneCameraOrigin(root, droneCameraOrigin(root));
		anchor.setPos(origin.x, origin.y, origin.z);
		anchor.setYRot(root.getYRot());
		anchor.setXRot(root.getXRot());
		anchor.setDeltaMovement(velocity == null ? Vec3.ZERO : velocity);
		anchor.hurtMarked = true;
	}

	private static Vec3 resolveSafeDroneCameraOrigin(Entity root, Vec3 desiredOrigin) {
		if (root == null || desiredOrigin == null || !(root.level() instanceof ServerLevel level)) {
			return desiredOrigin == null ? Vec3.ZERO : desiredOrigin;
		}
		if (!isCameraOriginInsideSolid(level, desiredOrigin)) {
			return desiredOrigin;
		}
		double minY = root.getY() + 0.01D;
		Vec3 best = null;
		double bestDistanceSqr = Double.POSITIVE_INFINITY;
		for (double yOffset : DRONE_CAMERA_ESCAPE_Y_OFFSETS) {
			double candidateY = desiredOrigin.y + yOffset;
			if (candidateY < minY) {
				continue;
			}
			for (int xStep = -DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; xStep <= DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; xStep++) {
				for (int zStep = -DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; zStep <= DRONE_CAMERA_ESCAPE_XZ_RADIUS_STEPS; zStep++) {
					if (xStep == 0 && zStep == 0 && yOffset == 0.0D) {
						continue;
					}
					double xOffset = xStep * DRONE_CAMERA_ESCAPE_STEP;
					double zOffset = zStep * DRONE_CAMERA_ESCAPE_STEP;
					Vec3 candidate = new Vec3(
							desiredOrigin.x + xOffset,
							candidateY,
							desiredOrigin.z + zOffset
					);
					if (isCameraOriginInsideSolid(level, candidate)) {
						continue;
					}
					double distanceSqr = xOffset * xOffset + yOffset * yOffset + zOffset * zOffset;
					if (distanceSqr < bestDistanceSqr) {
						best = candidate;
						bestDistanceSqr = distanceSqr;
					}
				}
			}
		}
		if (best != null) {
			return best;
		}
		return new Vec3(desiredOrigin.x, minY, desiredOrigin.z);
	}

	private static boolean isCameraOriginInsideSolid(ServerLevel level, Vec3 origin) {
		if (level == null || origin == null) {
			return false;
		}
		AABB probe = new AABB(
				origin.x - 0.04D,
				origin.y - 0.04D,
				origin.z - 0.04D,
				origin.x + 0.04D,
				origin.y + 0.04D,
				origin.z + 0.04D
		);
		return boxHitsSolidCollision(level, probe);
	}

	private static void setHotbarVisualHidden(ServerPlayer player, boolean hidden) {
		if (player == null || player.connection == null) {
			return;
		}

		AbstractContainerMenu menu = player.inventoryMenu;
		int stateId = menu.incrementStateId();
		for (int index = 0; index < 9; index++) {
			ItemStack stack = hidden ? ItemStack.EMPTY : player.getInventory().getItem(index).copy();
			player.connection.send(new ClientboundContainerSetSlotPacket(
					menu.containerId,
					stateId,
					PLAYER_HOTBAR_MENU_SLOT_START + index,
					stack
			));
		}
		ItemStack offhand = hidden ? ItemStack.EMPTY : player.getOffhandItem().copy();
		player.connection.send(new ClientboundContainerSetSlotPacket(
				menu.containerId,
				stateId,
				PLAYER_OFFHAND_MENU_SLOT,
				offhand
		));
	}

	private static Vec3 resolvePlacementPosition(UseOnContext context) {
		net.minecraft.core.Direction face = context.getClickedFace();
		net.minecraft.core.BlockPos anchor = context.getClickedPos().relative(face);
		double yOffset = face == net.minecraft.core.Direction.UP ? 0.0D : DRONE_SPAWN_Y_OFFSET;
		return new Vec3(anchor.getX() + 0.5D, anchor.getY() + yOffset, anchor.getZ() + 0.5D);
	}

	private static AABB droneBoxAt(Vec3 position) {
		return DroneGeometry.boxAt(position);
	}

	private static Vec3 droneCameraOrigin(Entity root) {
		return root == null ? Vec3.ZERO : droneCameraOrigin(root.position());
	}

	private static Vec3 droneCameraOrigin(Vec3 rootPosition) {
		return DroneGeometry.cameraOrigin(rootPosition);
	}

	private static void notifyDroneNetworkChanged(Entity root) {
		if (root == null || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		MonitorScreenSystem.onDroneNetworkChanged(
				level.getServer(),
				BluetoothLinkSystem.droneEndpoint(level.dimension(), root.blockPosition(), root.getUUID())
		);
	}

	private static Entity findDroneRoot(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension, UUID droneUuid) {
		Entity entity = findEntity(server, dimension, droneUuid);
		return entity != null && entity.getTags().contains(DRONE_ROOT_TAG) ? entity : null;
	}

	private static Entity findDroneRoot(MinecraftServer server, UUID droneUuid) {
		if (server == null || droneUuid == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = findDroneRoot(server, level.dimension(), droneUuid);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static Entity findEntity(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension, UUID uuid) {
		if (server == null || dimension == null || uuid == null) {
			return null;
		}
		ServerLevel level = server.getLevel(dimension);
		return level == null ? null : level.getEntity(uuid);
	}

	private static Entity findEntity(MinecraftServer server, UUID uuid) {
		if (server == null || uuid == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	private static void syncPassengerAttachment(Entity vehicle) {
		if (vehicle == null || !(vehicle.level() instanceof ServerLevel level)) {
			return;
		}

		ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(vehicle);
		for (ServerPlayer viewer : level.players()) {
			viewer.connection.send(packet);
		}
	}

	private static final class UncontrolledDroneState {
		private final UUID droneUuid;
		private final net.minecraft.resources.ResourceKey<Level> dimension;
		private Vec3 velocity;
		private float yaw;
		private float pitch;
		private DroneAutoAimTarget autoAimTarget;
		private long holdWithoutGravityUntilTick;
		private double forwardDrive;
		private double strafeDrive;
		private double displayForwardDrive;
		private double displayStrafeDrive;
		private boolean driveStateKnown;

		private UncontrolledDroneState(UUID droneUuid, net.minecraft.resources.ResourceKey<Level> dimension, Vec3 velocity, float yaw, float pitch) {
			this(droneUuid, dimension, velocity, yaw, pitch, null, Long.MIN_VALUE);
		}

		private UncontrolledDroneState(
				UUID droneUuid,
				net.minecraft.resources.ResourceKey<Level> dimension,
				Vec3 velocity,
				float yaw,
				float pitch,
				DroneAutoAimTarget autoAimTarget,
				long holdWithoutGravityUntilTick
		) {
			this(droneUuid, dimension, velocity, yaw, pitch, autoAimTarget, holdWithoutGravityUntilTick, 0.0D, 0.0D, 0.0D, 0.0D, false);
		}

		private UncontrolledDroneState(
				UUID droneUuid,
				net.minecraft.resources.ResourceKey<Level> dimension,
				Vec3 velocity,
				float yaw,
				float pitch,
				DroneAutoAimTarget autoAimTarget,
				long holdWithoutGravityUntilTick,
				double forwardDrive,
				double strafeDrive,
				double displayForwardDrive,
				double displayStrafeDrive,
				boolean driveStateKnown
		) {
			this.droneUuid = droneUuid;
			this.dimension = dimension;
			this.velocity = velocity == null ? Vec3.ZERO : velocity;
			this.yaw = yaw;
			this.pitch = pitch;
			this.autoAimTarget = autoAimTarget;
			this.holdWithoutGravityUntilTick = holdWithoutGravityUntilTick;
			this.forwardDrive = net.minecraft.util.Mth.clamp(forwardDrive, -DroneFlightPhysics.MAX_FORWARD_DRIVE, DroneFlightPhysics.MAX_FORWARD_DRIVE);
			this.strafeDrive = net.minecraft.util.Mth.clamp(strafeDrive, -DroneFlightPhysics.MAX_STRAFE_DRIVE, DroneFlightPhysics.MAX_STRAFE_DRIVE);
			this.displayForwardDrive = net.minecraft.util.Mth.clamp(displayForwardDrive, -DroneFlightPhysics.MAX_FORWARD_DRIVE, DroneFlightPhysics.MAX_FORWARD_DRIVE);
			this.displayStrafeDrive = net.minecraft.util.Mth.clamp(displayStrafeDrive, -DroneFlightPhysics.MAX_STRAFE_DRIVE, DroneFlightPhysics.MAX_STRAFE_DRIVE);
			this.driveStateKnown = driveStateKnown;
		}

		private UUID droneUuid() {
			return this.droneUuid;
		}

		private net.minecraft.resources.ResourceKey<Level> dimension() {
			return this.dimension;
		}

		private Vec3 velocity() {
			return this.velocity;
		}

		private void setVelocity(Vec3 velocity) {
			this.velocity = velocity == null ? Vec3.ZERO : velocity;
		}

		private float yaw() {
			return this.yaw;
		}

		private void setYaw(float yaw) {
			this.yaw = yaw;
		}

		private float pitch() {
			return this.pitch;
		}

		private void setPitch(float pitch) {
			this.pitch = pitch;
		}

		private DroneAutoAimTarget autoAimTarget() {
			return this.autoAimTarget;
		}

		private void setAutoAimTarget(DroneAutoAimTarget autoAimTarget) {
			this.autoAimTarget = autoAimTarget;
		}

		private long holdWithoutGravityUntilTick() {
			return this.holdWithoutGravityUntilTick;
		}

		private double forwardDrive() {
			return this.forwardDrive;
		}

		private double strafeDrive() {
			return this.strafeDrive;
		}

		private double displayForwardDrive() {
			return this.displayForwardDrive;
		}

		private void setDisplayForwardDrive(double displayForwardDrive) {
			this.displayForwardDrive = net.minecraft.util.Mth.clamp(displayForwardDrive, -DroneFlightPhysics.MAX_FORWARD_DRIVE, DroneFlightPhysics.MAX_FORWARD_DRIVE);
		}

		private double displayStrafeDrive() {
			return this.displayStrafeDrive;
		}

		private void setDisplayStrafeDrive(double displayStrafeDrive) {
			this.displayStrafeDrive = net.minecraft.util.Mth.clamp(displayStrafeDrive, -DroneFlightPhysics.MAX_STRAFE_DRIVE, DroneFlightPhysics.MAX_STRAFE_DRIVE);
		}

		private boolean hasDriveState() {
			return this.driveStateKnown;
		}
	}

	private record DroneChunkTicketKey(net.minecraft.resources.ResourceKey<Level> dimension, long chunkLong, int radius, boolean simulation) {
	}

	private record PendingDroneControlStart(
			UUID droneUuid,
			net.minecraft.resources.ResourceKey<Level> droneDimension,
			BlockPos fallbackPos,
			long startedAtTick
	) {
	}

	private record DroneScreenStreamLoadState(
			UUID droneUuid,
			net.minecraft.resources.ResourceKey<Level> dimension,
			Vec3 dronePos,
			Vec3 cameraPos,
			float cameraYaw,
			float cameraPitch
	) {
		private DroneCameraChunkTarget cameraTarget() {
			Vec3 pos = this.cameraPos == null ? Vec3.ZERO : this.cameraPos;
			return new DroneCameraChunkTarget(pos.x, pos.z, this.cameraYaw);
		}
	}

	public record DroneScreenStreamReference(
			UUID droneUuid,
			net.minecraft.resources.ResourceKey<Level> dimension,
			BlockPos pos
	) {
	}

	private record DroneCameraChunkTarget(double x, double z, float yaw) {
	}

	private static final class OperatorBodyMirror {
		private final int entityId;
		private final UUID profileId;
		private final GameProfile profile;
		private boolean spawned;

		private OperatorBodyMirror(int entityId, UUID profileId, GameProfile profile) {
			this.entityId = entityId;
			this.profileId = profileId;
			this.profile = profile;
		}

		private int entityId() {
			return entityId;
		}

		private UUID profileId() {
			return profileId;
		}

		private GameProfile profile() {
			return profile;
		}

		private boolean spawned() {
			return spawned;
		}

		private void setSpawned(boolean spawned) {
			this.spawned = spawned;
		}
	}

	private record DroneInputState(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift, boolean sprint) {
		private static final DroneInputState EMPTY = new DroneInputState(false, false, false, false, false, false, false);
	}

	private static final class TurretInventory extends SimpleContainer {
		private TurretInventory() {
			super(DRONE_TURRET_INVENTORY_SIZE);
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return isDroneTurretProjectileStack(stack);
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			super.setItem(slot, isDroneTurretProjectileStack(stack) ? stack : ItemStack.EMPTY);
		}
	}

	private record ControlledCollisionState(
			boolean horizontalCollision,
			boolean verticalCollision,
			boolean verticalCollisionBelow
	) {
		private static final ControlledCollisionState NONE = new ControlledCollisionState(false, false, false);
	}

	private record DroneAutoAimAngles(float yaw, float pitch) {
	}

	private static final class DroneControlSession {
		private final UUID droneUuid;
		private final net.minecraft.resources.ResourceKey<Level> droneDimension;
		private Vec3 velocity = Vec3.ZERO;
		private Vec3 intendedVelocity = Vec3.ZERO;
		private Vec3 proxyPos = Vec3.ZERO;
		private Vec3 lastKnownDronePos = Vec3.ZERO;
		private DroneCameraChunkTarget lastKnownCameraTarget = new DroneCameraChunkTarget(0.0D, 0.0D, 0.0F);
		private float controlYaw;
		private float controlPitch;
		private float proxyYaw;
		private float proxyPitch;
		private long missingRootSinceTick = Long.MIN_VALUE;
		private int nextViewSyncTeleportId = CONTROLLED_VIEW_TELEPORT_ID_BASE;
		private double forwardDrive;
		private double strafeDrive;
		private double displayForwardDrive;
		private double displayStrafeDrive;
		private double surfaceWear;
		private long lastSurfaceWearContactTick = Long.MIN_VALUE;
		private boolean hudVisible;
		private String lastHudSnapshot = "";
		private long lastHudTick = Long.MIN_VALUE;
		private DroneAutoAimTarget autoAimTarget;
		private long lastManualLookTick = Long.MIN_VALUE;
		private long turretInputSuppressedUntilTick = Long.MIN_VALUE;

		private DroneControlSession(
				UUID droneUuid,
				net.minecraft.resources.ResourceKey<Level> droneDimension
		) {
			this.droneUuid = droneUuid;
			this.droneDimension = droneDimension;
		}

		private UUID droneUuid() {
			return this.droneUuid;
		}

		private net.minecraft.resources.ResourceKey<Level> droneDimension() {
			return this.droneDimension;
		}

		private Vec3 velocity() {
			return this.velocity;
		}

		private void setVelocity(Vec3 velocity) {
			this.velocity = velocity == null ? Vec3.ZERO : velocity;
		}

		private Vec3 intendedVelocity() {
			return this.intendedVelocity;
		}

		private void setIntendedVelocity(Vec3 intendedVelocity) {
			this.intendedVelocity = intendedVelocity == null ? Vec3.ZERO : intendedVelocity;
		}

		private Vec3 proxyPos() {
			return this.proxyPos;
		}

		private void setProxyPos(Vec3 proxyPos) {
			this.proxyPos = proxyPos == null ? Vec3.ZERO : proxyPos;
		}

		private Vec3 lastKnownDronePos() {
			return this.lastKnownDronePos == null ? this.proxyPos() : this.lastKnownDronePos;
		}

		private DroneCameraChunkTarget lastKnownCameraTarget() {
			return this.lastKnownCameraTarget == null
					? new DroneCameraChunkTarget(this.lastKnownDronePos().x, this.lastKnownDronePos().z, this.proxyYaw)
					: this.lastKnownCameraTarget;
		}

		private void refreshKnownDroneLocation(Entity root) {
			if (root == null || !root.isAlive()) {
				return;
			}
			this.lastKnownDronePos = root.position();
			this.lastKnownCameraTarget = resolveDroneCameraChunkTarget(root);
			this.missingRootSinceTick = Long.MIN_VALUE;
		}

		private boolean markDroneRootMissing(long nowTick) {
			if (nowTick == Long.MAX_VALUE) {
				return false;
			}
			if (this.missingRootSinceTick == Long.MIN_VALUE) {
				this.missingRootSinceTick = nowTick;
			}
			return nowTick - this.missingRootSinceTick <= CONTROLLED_DRONE_MISSING_ROOT_GRACE_TICKS;
		}

		private float controlYaw() {
			return this.controlYaw;
		}

		private void setControlYaw(float controlYaw) {
			this.controlYaw = controlYaw;
		}

		private float controlPitch() {
			return this.controlPitch;
		}

		private void setControlPitch(float controlPitch) {
			this.controlPitch = controlPitch;
		}

		private float proxyYaw() {
			return this.proxyYaw;
		}

		private void setProxyYaw(float proxyYaw) {
			this.proxyYaw = proxyYaw;
		}

		private float proxyPitch() {
			return this.proxyPitch;
		}

		private void setProxyPitch(float proxyPitch) {
			this.proxyPitch = proxyPitch;
		}

		private void recordManualLookDelta(float yawDelta, float pitchDelta, long gameTime) {
			if (gameTime == Long.MIN_VALUE) {
				return;
			}
			if (Math.max(Math.abs(yawDelta), Math.abs(pitchDelta)) > DRONE_AUTO_AIM_MANUAL_LOOK_THRESHOLD_DEGREES) {
				this.lastManualLookTick = gameTime;
			}
		}

		private boolean isManualLookRecentlyActive(long gameTime) {
			return gameTime != Long.MIN_VALUE
					&& this.lastManualLookTick != Long.MIN_VALUE
					&& gameTime - this.lastManualLookTick <= DRONE_AUTO_AIM_MANUAL_SUPPRESSION_TICKS;
		}

		private int nextViewSyncTeleportId() {
			return this.nextViewSyncTeleportId++;
		}

		private double forwardDrive() {
			return this.forwardDrive;
		}

		private void setForwardDrive(double forwardDrive) {
			this.forwardDrive = forwardDrive;
		}

		private double strafeDrive() {
			return this.strafeDrive;
		}

		private void setStrafeDrive(double strafeDrive) {
			this.strafeDrive = strafeDrive;
		}

		private double displayForwardDrive() {
			return this.displayForwardDrive;
		}

		private void setDisplayForwardDrive(double displayForwardDrive) {
			this.displayForwardDrive = displayForwardDrive;
		}

		private double displayStrafeDrive() {
			return this.displayStrafeDrive;
		}

		private void setDisplayStrafeDrive(double displayStrafeDrive) {
			this.displayStrafeDrive = displayStrafeDrive;
		}

		private double surfaceWear() {
			return this.surfaceWear;
		}

		private void setSurfaceWear(double surfaceWear) {
			this.surfaceWear = net.minecraft.util.Mth.clamp(surfaceWear, 0.0D, DroneImpactModel.SURFACE_WEAR_BREAK_LEVEL);
		}

		private long lastSurfaceWearContactTick() {
			return this.lastSurfaceWearContactTick;
		}

		private void setLastSurfaceWearContactTick(long lastSurfaceWearContactTick) {
			this.lastSurfaceWearContactTick = lastSurfaceWearContactTick;
		}

		private boolean hudVisible() {
			return this.hudVisible;
		}

		private void setHudVisible(boolean hudVisible) {
			this.hudVisible = hudVisible;
		}

		private String lastHudSnapshot() {
			return this.lastHudSnapshot;
		}

		private void setLastHudSnapshot(String lastHudSnapshot) {
			this.lastHudSnapshot = lastHudSnapshot == null ? "" : lastHudSnapshot;
		}

		private long lastHudTick() {
			return this.lastHudTick;
		}

		private void setLastHudTick(long lastHudTick) {
			this.lastHudTick = lastHudTick;
		}

		private DroneAutoAimTarget autoAimTarget() {
			return this.autoAimTarget;
		}

		private void setAutoAimTarget(DroneAutoAimTarget autoAimTarget) {
			this.autoAimTarget = autoAimTarget;
		}

		private long turretInputSuppressedUntilTick() {
			return this.turretInputSuppressedUntilTick;
		}

		private void setTurretInputSuppressedUntilTick(long turretInputSuppressedUntilTick) {
			this.turretInputSuppressedUntilTick = turretInputSuppressedUntilTick;
		}

	}

	private sealed interface DroneAutoAimTarget permits DroneAutoAimEntityTarget, DroneAutoAimBlockTarget {
	}

	private record DroneAutoAimEntityTarget(UUID entityUuid) implements DroneAutoAimTarget {
	}

	private record DroneAutoAimBlockTarget(BlockPos blockPos) implements DroneAutoAimTarget {
	}

	private record DroneDisplayWobbleState(DroneDisplayWobbleType type, long startedAtTick) {
	}

	private record DroneDisplayWobble(DroneDisplayWobbleType type, float progress) {
	}

	private enum DroneDisplayWobbleType {
		POSITIVE(7),
		NEGATIVE(10);

		private final int lengthInTicks;

		DroneDisplayWobbleType(int lengthInTicks) {
			this.lengthInTicks = Math.max(1, lengthInTicks);
		}

		private int lengthInTicks() {
			return this.lengthInTicks;
		}
	}

	public record DroneLiveFeedState(
			UUID droneUuid,
			net.minecraft.resources.ResourceKey<Level> dimension,
			BlockPos pos,
			boolean online,
			double expectedX,
			double expectedY,
			double expectedZ,
			float yaw,
			float pitch,
			UUID followEntityUuid,
			Set<UUID> hiddenEntityUuids,
			boolean omnidirectionalChunkLoading,
			String controllerName
	) {
	}
}
