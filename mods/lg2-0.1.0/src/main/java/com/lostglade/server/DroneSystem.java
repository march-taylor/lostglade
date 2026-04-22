package com.lostglade.server;

import com.google.common.collect.ImmutableMultimap;
import com.lostglade.Lg2;
import com.lostglade.item.DroneItem;
import com.lostglade.item.ModItems;
import com.lostglade.mixin.ClientboundSetPassengersPacketAccessor;
import com.lostglade.mixin.EntityTrackedDataAccessor;
import com.lostglade.mixin.EntityPassengerAccessor;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
import com.lostglade.mixin.ServerCommonPacketListenerImplAccessor;
import com.lostglade.mixin.ServerGamePacketListenerImplAccessor;
import com.lostglade.server.map.MapImageRenderSystem;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Pair;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.skin.SkinStorage;
import net.lionarius.skinrestorer.skin.SkinValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
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
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DroneSystem {
	private static final String IT_DRONE_SCOUT = "it_drone_scout";
	private static final String IT_DRONE_KAMIKAZE = "it_drone_kamikaze";
	private static final String DRONE_ROOT_TAG = "lg2_drone_root";
	private static final String DRONE_KAMIKAZE_POWER_TAG_PREFIX = "lg2_drone_kamikaze_power_";
	private static final String DRONE_DISPLAY_TAG = "lg2_drone_display";
	private static final String DRONE_DISPLAY_OWNER_TAG_PREFIX = "lg2_drone_display_owner_";
	private static final String DRONE_CAMERA_TAG = "lg2_drone_camera_anchor";
	private static final String DRONE_CAMERA_OWNER_TAG_PREFIX = "lg2_drone_camera_owner_";
	private static final String DRONE_CONTROLLED_PROXY_TAG = "lg2_drone_controlled_proxy";
	private static final String DRONE_DUMMY_TAG = "lg2_drone_dummy";
	private static final Identifier DRONE_LOOP_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_loop");
	private static final Identifier DRONE_KAMIKAZE_LOOP_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_kamikaze_loop");
	private static final Identifier DRONE_BREAK_SOUND_ID = Identifier.fromNamespaceAndPath("minecraft", "entity.firework_rocket.blast");
	private static final Holder<SoundEvent> DRONE_LOOP_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(DRONE_LOOP_SOUND_ID));
	private static final Holder<SoundEvent> DRONE_KAMIKAZE_LOOP_SOUND = Holder.direct(
			SoundEvent.createVariableRangeEvent(DRONE_KAMIKAZE_LOOP_SOUND_ID)
	);
	private static final double DRONE_CRASH_EQUIVALENT_FALL_BLOCKS = 3.25D;
	private static final double DRONE_CRASH_REFERENCE_ACCELERATION = 0.04D;
	private static final float DRONE_WIDTH = 0.95F;
	private static final float DRONE_HEIGHT = 0.35F;
	private static final float DRONE_CAMERA_ANCHOR_SIZE = 0.01F;
	private static final double DRONE_SPAWN_Y_OFFSET = 0.24D;
	private static final float DRONE_DISPLAY_VIEW_RANGE = 64.0F;
	private static final float DRONE_DISPLAY_CONTROLLED_Y_OFFSET = -0.6F;
	private static final int DRONE_DISPLAY_INTERPOLATION_TICKS = 2;
	private static final float DRONE_DISPLAY_DRIVE_SMOOTHING = 0.35F;
	private static final float DRONE_MAX_TILT_DEGREES = 32.0F;
	private static final long DRONE_LOOP_REPLAY_TICKS = 10L;
	private static final long DRONE_CAMERA_SUPPRESS_AFTER_CONTROL_TICKS = 20L;
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
	private static final int PLAYER_HOTBAR_MENU_SLOT_START = 36;
	private static final int PLAYER_OFFHAND_MENU_SLOT = 45;
	private static final byte ALL_PLAYER_SKIN_PARTS = (byte) 0x7F;
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final long DRONE_HUD_REFRESH_TICKS = 2L;
	private static final int DRONE_HUD_GRID_SIZE = 11;
	private static final int DRONE_HUD_GLYPH_BASE = 0xE700;
	private static final int DRONE_HUD_SPEED_BAR_GLYPH_BASE = 0xE780;
	private static final String DRONE_HUD_BAR_OVERLAP_GLYPH = "\uE944";
	private static final int DRONE_HUD_LABEL_COLOR = 0x6BD7FF;
	private static final int DRONE_HUD_VALUE_COLOR = 0xF4FFF6;
	private static final int DRONE_HUD_DIM_COLOR = 0x5A7080;
	private static final double DRONE_MIN_CONTROL_DRIVE_STEP = 0.055D;
	private static final double DRONE_MAX_CONTROL_DRIVE_STEP = 0.500D;
	private static final byte ENTITY_FLAG_ON_FIRE = 0x01;
	private static final byte ENTITY_FLAG_SHIFTING = 0x02;
	private static final byte ENTITY_FLAG_SPRINTING = 0x08;
	private static final byte ENTITY_FLAG_SWIMMING = 0x10;
	private static final byte ENTITY_FLAG_INVISIBLE = 0x20;
	private static final byte ENTITY_FLAG_FALL_FLYING = (byte) 0x80;
	private static final int CONTROLLED_VIEW_TELEPORT_ID_BASE = 1_000_000_000;
	private static final double CONTROLLED_PROXY_RESYNC_DISTANCE_SQR = 0.55D * 0.55D;
	private static final double CONTROLLED_PROXY_COLLISION_RESYNC_MARGIN = 0.02D;
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
	private static final Map<UUID, UncontrolledDroneState> UNCONTROLLED_DRONES = new HashMap<>();
	private static final Map<UUID, UUID> CONTROLLED_PROXY_TO_CONTROLLER = new HashMap<>();
	private static final Map<UUID, Vec3> CONTROLLED_OPERATOR_KNOCKBACK_VELOCITY = new HashMap<>();
	private static final Map<UUID, Long> NEXT_DRONE_SOUND_TICK = new HashMap<>();
	private static final Map<UUID, Long> NEXT_DRONE_ARM_ALLOWED_TICK = new HashMap<>();
	private static final Map<UUID, DroneDisplayWobbleState> DISPLAY_WOBBLE_BY_DRONE = new HashMap<>();
	private static final Map<UUID, Long> CAMERA_SUPPRESSED_UNTIL_TICK = new HashMap<>();
	private static final Set<UUID> FORCED_CONTROLLED_PLAYERS = new HashSet<>();
	private static final Map<UUID, ControlledInventorySnapshot> CONTROLLED_INVENTORY_SNAPSHOTS = new HashMap<>();
	private static final Map<UUID, UUID> DUMMY_OWNER_BY_UUID = new HashMap<>();
	private static final ThreadLocal<Boolean> CONTROLLED_OPERATOR_PACKET_REWRITE_BYPASS = ThreadLocal.withInitial(() -> false);

	private DroneSystem() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			Entity root = resolveDroneRoot(entity);
			if (root == null) {
				return InteractionResult.PASS;
			}
			if (serverPlayer.getItemInHand(hand).is(ModItems.BLUETOOTH_ADAPTER)) {
				return InteractionResult.PASS;
			}
			InteractionResult armResult = tryArmDroneWithTnt(serverPlayer, root, serverPlayer.getItemInHand(hand));
			if (armResult != InteractionResult.PASS) {
				return armResult;
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
			Entity root = resolveDroneRoot(entity);
			if (root == null) {
				return InteractionResult.PASS;
			}
			destroyDrone(root, serverPlayer, true);
			return InteractionResult.SUCCESS;
		});

		ServerTickEvents.END_SERVER_TICK.register(DroneSystem::tick);
		ServerEntityEvents.ENTITY_LOAD.register(DroneSystem::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(DroneSystem::onEntityUnload);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stopControlling((ServerPlayer) handler.player, true, false));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> stopControlling(newPlayer, false, false));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID playerId : new ArrayList<>(ACTIVE_SESSIONS.keySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				if (player != null) {
					stopControlling(player, true, false);
				}
			}
			ACTIVE_SESSIONS.clear();
			INPUTS.clear();
			CONTROLLERS_BY_DRONE.clear();
			DISPLAYS_BY_DRONE.clear();
			CAMERA_ANCHORS_BY_DRONE.clear();
			UNCONTROLLED_DRONES.clear();
			CONTROLLED_PROXY_TO_CONTROLLER.clear();
			CONTROLLED_OPERATOR_KNOCKBACK_VELOCITY.clear();
			NEXT_DRONE_ARM_ALLOWED_TICK.clear();
			CAMERA_SUPPRESSED_UNTIL_TICK.clear();
			FORCED_CONTROLLED_PLAYERS.clear();
			CONTROLLED_INVENTORY_SNAPSHOTS.clear();
			DUMMY_OWNER_BY_UUID.clear();
			DISPLAY_WOBBLE_BY_DRONE.clear();
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
		int kamikazePower = DRONE_KAMIKAZE_NO_POWER;

		Vec3 spawnPos = resolvePlacementPosition(context);
		AABB placementBox = droneBoxAt(spawnPos);
		if (!serverLevel.noCollision(placementBox)) {
			return InteractionResult.FAIL;
		}

		float yRot = player.getYRot();
		Interaction root = new Interaction(EntityType.INTERACTION, serverLevel);
		root.addTag(DRONE_ROOT_TAG);
		if (kamikazePower > DRONE_KAMIKAZE_NO_POWER) {
			root.addTag(droneKamikazePowerTag(kamikazePower));
		}
		root.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		root.setYRot(yRot);
		root.setXRot(0.0F);
		root.setNoGravity(true);
		root.setInvulnerable(true);
		root.setSilent(true);
		root.setResponse(true);
		root.setWidth(DRONE_WIDTH);
		root.setHeight(DRONE_HEIGHT);

		Display.ItemDisplay display = createDroneDisplay(
				serverLevel,
				spawnPos,
				yRot,
				0.0F,
				ModItems.DRONE,
				kamikazePower
		);
		Interaction cameraAnchor = createDroneCameraAnchor(serverLevel, droneCameraOrigin(spawnPos), yRot, 0.0F);
		serverLevel.addFreshEntity(root);
		serverLevel.addFreshEntity(display);
		serverLevel.addFreshEntity(cameraAnchor);
		forceEntityPassenger(root, display);
		display.addTag(DRONE_DISPLAY_OWNER_TAG_PREFIX + root.getUUID());
		DISPLAYS_BY_DRONE.put(root.getUUID(), display.getUUID());
		cameraAnchor.addTag(DRONE_CAMERA_OWNER_TAG_PREFIX + root.getUUID());
		CAMERA_ANCHORS_BY_DRONE.put(root.getUUID(), cameraAnchor.getUUID());
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
		if (session == null) {
			return false;
		}
		return Objects.equals(CONTROLLERS_BY_DRONE.get(session.droneUuid()), player.getUUID());
	}

	public static boolean isControlledDroneProxy(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		if (CONTROLLED_PROXY_TO_CONTROLLER.containsKey(player.getUUID())) {
			return true;
		}
		if (player.getTags().contains(DRONE_CONTROLLED_PROXY_TAG)) {
			return true;
		}
		for (DroneControlSession session : ACTIVE_SESSIONS.values()) {
			if (session != null && session.controlledProxyPlayer() == player) {
				return true;
			}
		}
		return false;
	}

	public static boolean shouldSkipChunkTrackingMove(ServerPlayer player) {
		if (player == null) {
			return true;
		}
		if (isControlledDroneProxy(player)) {
			return true;
		}
		MinecraftServer server = player.level() != null ? player.level().getServer() : null;
		if (server == null || server.getPlayerList() == null) {
			return false;
		}
		ServerPlayer listed = server.getPlayerList().getPlayer(player.getUUID());
		return listed != player;
	}

	public static boolean shouldApplyDroneTravelToPlayer(ServerPlayer player) {
		return isControlledDroneProxy(player);
	}

	public static void recordControlledOperatorKnockback(ServerPlayer player, Vec3 velocity) {
		if (player == null || velocity == null || !isControllingDrone(player) || velocity.lengthSqr() <= 1.0E-5D) {
			return;
		}
		CONTROLLED_OPERATOR_KNOCKBACK_VELOCITY.put(player.getUUID(), velocity);
		player.hurtMarked = true;
	}

	public static Vec3 consumeControlledOperatorKnockback(ServerPlayer player) {
		if (player == null) {
			return Vec3.ZERO;
		}
		Vec3 velocity = CONTROLLED_OPERATOR_KNOCKBACK_VELOCITY.remove(player.getUUID());
		return velocity == null ? Vec3.ZERO : velocity;
	}

	public static void handleControlledMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
		if (player == null || packet == null) {
			return;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}
		ServerGamePacketListenerImpl proxyListener = session.controlledProxyListener();
		ServerPlayer proxyPlayer = session.controlledProxyPlayer();
		if (proxyListener == null || proxyPlayer == null) {
			return;
		}
		syncControlledProxyListenerTickState(player, session);
		runWithControlledOperatorPacketRewriteBypass(() -> proxyListener.handleMovePlayer(packet));
		syncControlledProxyShellState(session, proxyPlayer);
	}

	public static void handleControlledAcceptTeleportPacket(ServerPlayer player, int teleportId) {
		if (player == null) {
			return;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}
		if (teleportId >= CONTROLLED_VIEW_TELEPORT_ID_BASE) {
			session.setLastAcceptedProxyTeleportId(teleportId);
			return;
		}
		ServerGamePacketListenerImpl proxyListener = session.controlledProxyListener();
		if (proxyListener == null) {
			return;
		}
		runWithControlledOperatorPacketRewriteBypass(() ->
				proxyListener.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(teleportId))
		);
	}

	public static ChunkTrackingView createVirtualChunkTrackingView(ServerPlayer player) {
		if (!isControllingDrone(player)) {
			return ChunkTrackingView.of(player.chunkPosition(), resolveRequestedViewDistance(player));
		}

		Entity root = resolveControlledDroneRoot(player);
		if (root == null) {
			return ChunkTrackingView.EMPTY;
		}
		return ChunkTrackingView.of(root.chunkPosition(), resolveRequestedViewDistance(player));
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
		return root != null && isWithinHorizontalRange(root.position(), entity, horizontalRangeBlocks);
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
				rewrittenPackets.add((Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>) rewritten);
				if (rewritten != bundledPacket) {
					changed = true;
				}
			}
			return changed ? new ClientboundBundlePacket(rewrittenPackets) : packet;
		}

		if (packet instanceof ClientboundGameEventPacket gameEventPacket
				&& gameEventPacket.getEvent() == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
			return new ClientboundGameEventPacket(
					ClientboundGameEventPacket.CHANGE_GAME_MODE,
					GameType.SPECTATOR.getId()
			);
		}

		if (packet instanceof ClientboundPlayerAbilitiesPacket) {
			return new ClientboundPlayerAbilitiesPacket(buildControlledOperatorAbilities(receiver));
		}

		if (packet instanceof ClientboundPlayerPositionPacket) {
			return buildControlledPlayerPositionPacket(session);
		}

		if (packet instanceof ClientboundSetEntityMotionPacket entityMotionPacket
				&& entityMotionPacket.getId() == receiver.getId()) {
			return new ClientboundSetEntityMotionPacket(receiver.getId(), session.velocity());
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

	public static boolean isDroneCameraSuppressed(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		if (isControllingDrone(player)) {
			return true;
		}
		Long untilTick = CAMERA_SUPPRESSED_UNTIL_TICK.get(player.getUUID());
		if (untilTick == null) {
			return false;
		}
		long now = player.level() == null ? Long.MAX_VALUE : player.level().getGameTime();
		if (now > untilTick) {
			CAMERA_SUPPRESSED_UNTIL_TICK.remove(player.getUUID(), untilTick);
			return false;
		}
		return true;
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
		UUID controllerId = CONTROLLERS_BY_DRONE.get(root.getUUID());
		ServerPlayer controller = controllerId == null ? null : server.getPlayerList().getPlayer(controllerId);
		UUID displayId = DISPLAYS_BY_DRONE.get(root.getUUID());
		Entity cameraAnchor = ensureDroneCameraAnchor(root);
		Vec3 cameraOrigin = cameraAnchor != null ? cameraAnchor.position() : droneCameraOrigin(root);
		float cameraYaw = cameraAnchor != null ? cameraAnchor.getYRot() : root.getYRot();
		float cameraPitch = cameraAnchor != null ? cameraAnchor.getXRot() : root.getXRot();
		UUID cameraAnchorUuid = cameraAnchor != null ? cameraAnchor.getUUID() : null;
		Set<UUID> hiddenEntities = displayId == null ? Set.of(root.getUUID()) : Set.of(root.getUUID(), displayId);
		if (controller != null && ACTIVE_SESSIONS.containsKey(controller.getUUID())) {
			DroneControlSession session = ACTIVE_SESSIONS.get(controller.getUUID());
			if (session != null
					&& Objects.equals(session.droneUuid(), root.getUUID())
					&& controller.level() == droneLevel) {
				Set<UUID> activeHiddenEntities = displayId == null
						? Set.of(root.getUUID(), controller.getUUID())
						: Set.of(root.getUUID(), controller.getUUID(), displayId);
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
						activeHiddenEntities,
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

	public static void applyControlledTravel(ServerPlayer player) {
		if (player == null) {
			return;
		}
		DroneControlSession session = resolveDroneControlSession(player);
		ServerPlayer controller = resolveDroneController(player);
		if (session == null || controller == null) {
			return;
		}
		int controlSpeedSlot = getControlSpeedSlot(controller);
		double driveStep = getControlDriveStep(controlSpeedSlot);

		DroneInputState input = INPUTS.getOrDefault(controller.getUUID(), DroneInputState.EMPTY);
		session.setForwardDrive(
				DroneFlightPhysics.adjustDrive(
						session.forwardDrive(),
						input.forward(),
						input.backward(),
						driveStep,
						DroneFlightPhysics.MAX_FORWARD_DRIVE
				)
		);
		session.setStrafeDrive(
				DroneFlightPhysics.adjustDrive(
						session.strafeDrive(),
						input.right(),
						input.left(),
						driveStep,
						DroneFlightPhysics.MAX_STRAFE_DRIVE
				)
		);

		ensureControlledProxyState(player);

		Vec3 nextVelocity = DroneFlightPhysics.step(
				player.getXRot(),
				player.getYRot(),
				session.forwardDrive(),
				session.strafeDrive()
		);
		player.setDeltaMovement(nextVelocity);
		player.move(MoverType.SELF, nextVelocity);
		session.setVelocity(nextVelocity);
		player.hurtMarked = true;
	}

	private static DroneControlSession resolveDroneControlSession(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		if (isControllingDrone(player)) {
			return ACTIVE_SESSIONS.get(player.getUUID());
		}
		UUID controllerId = CONTROLLED_PROXY_TO_CONTROLLER.get(player.getUUID());
		return controllerId == null ? null : ACTIVE_SESSIONS.get(controllerId);
	}

	private static ServerPlayer resolveDroneController(ServerPlayer player) {
		if (player == null) {
			return null;
		}
		if (isControllingDrone(player)) {
			return player;
		}
		UUID controllerId = CONTROLLED_PROXY_TO_CONTROLLER.get(player.getUUID());
		if (controllerId == null) {
			return null;
		}
		MinecraftServer server = player.level() != null ? player.level().getServer() : null;
		return server == null ? null : server.getPlayerList().getPlayer(controllerId);
	}

	private static void syncControlledProxyShellState(DroneControlSession session, ServerPlayer proxyPlayer) {
		if (session == null || proxyPlayer == null) {
			return;
		}
		session.setProxyPos(proxyPlayer.position());
		session.setProxyYaw(proxyPlayer.getYRot());
		session.setProxyPitch(proxyPlayer.getXRot());
	}

	private static void syncControlledProxyListenerTickState(ServerPlayer controller, DroneControlSession session) {
		if (controller == null || controller.connection == null || session == null || session.controlledProxyListener() == null) {
			return;
		}
		ServerGamePacketListenerImplAccessor controllerAccessor = (ServerGamePacketListenerImplAccessor) controller.connection;
		ServerGamePacketListenerImplAccessor proxyAccessor = (ServerGamePacketListenerImplAccessor) session.controlledProxyListener();
		proxyAccessor.lg2$setTickCount(Math.max(1, controllerAccessor.lg2$getTickCount()));
		proxyAccessor.lg2$setKnownMovePacketCount(proxyAccessor.lg2$getReceivedMovePacketCount());
		session.controlledProxyListener().resetPosition();
		session.controlledProxyListener().resetFlyingTicks();
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
		tickControlledSessions(server);
		tickUncontrolledDrones(server);
		cleanupExpiredCameraSuppression(server);
		recoverOrphanedControlledPlayers(server);
		recoverPlayersWithStaleDronePassenger(server);
	}

	private static void cleanupExpiredCameraSuppression(MinecraftServer server) {
		if (server == null || CAMERA_SUPPRESSED_UNTIL_TICK.isEmpty()) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long now = overworld == null ? Long.MAX_VALUE : overworld.getGameTime();
		for (Map.Entry<UUID, Long> entry : new ArrayList<>(CAMERA_SUPPRESSED_UNTIL_TICK.entrySet())) {
			Long untilTick = entry.getValue();
			if (untilTick == null || now > untilTick) {
				CAMERA_SUPPRESSED_UNTIL_TICK.remove(entry.getKey(), untilTick);
			}
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
			detachAnyDronePassengersFromController(player);
			clearForcedControlMovementState(player);
			markCameraSuppressedForPlayer(player);
			spoofClientGameMode(player, resolveServerGameMode(player));
			restoreControlledInventoryIfNeeded(player);
			setHotbarVisualHidden(player, false);
			broadcastDronePilotEquipmentHidden(player, false);
			refreshControlledOperatorActualView(player);
		}
	}

	private static void markCameraSuppressedForPlayer(ServerPlayer player) {
		if (player == null || player.level() == null) {
			return;
		}
		CAMERA_SUPPRESSED_UNTIL_TICK.put(
				player.getUUID(),
				player.level().getGameTime() + DRONE_CAMERA_SUPPRESS_AFTER_CONTROL_TICKS
		);
	}

	private static void recoverOrphanedControlledPlayers(MinecraftServer server) {
		if (server == null || FORCED_CONTROLLED_PLAYERS.isEmpty()) {
			return;
		}
		for (UUID playerId : new ArrayList<>(FORCED_CONTROLLED_PLAYERS)) {
			if (playerId == null || ACTIVE_SESSIONS.containsKey(playerId)) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				FORCED_CONTROLLED_PLAYERS.remove(playerId);
				continue;
			}
			clearForcedControlMovementState(player);
			detachAnyDronePassengersFromController(player);
			spoofClientGameMode(player, resolveServerGameMode(player));
			restoreControlledInventoryIfNeeded(player);
			setHotbarVisualHidden(player, false);
			broadcastDronePilotEquipmentHidden(player, false);
			refreshControlledOperatorActualView(player);
			FORCED_CONTROLLED_PLAYERS.remove(playerId);
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
				stopControlling(player, true, true);
				continue;
			}
			if (!player.isAlive() || player.isSpectator()) {
				stopControlling(player, false, false);
				continue;
			}
			if (!Objects.equals(CONTROLLERS_BY_DRONE.get(session.droneUuid()), player.getUUID())) {
				stopControlling(player, true, false);
				continue;
			}

			DroneInputState input = INPUTS.getOrDefault(player.getUUID(), DroneInputState.EMPTY);
			if (input.shift()) {
				stopControlling(player, true, true);
				continue;
			}
			tickControlledDrone(player, root, session, input);
		}
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

			// If someone is actively controlling the drone, it should follow the operator proxy, not uncontrolled physics.
			if (isDroneActivelyControlled(root)) {
				UNCONTROLLED_DRONES.remove(entry.getKey());
				continue;
			}

			tickUncontrolledDrone(root, state);
		}
	}

	private static void tickControlledDrone(ServerPlayer player, Entity root, DroneControlSession session, DroneInputState input) {
		if (!(root.level() instanceof ServerLevel)) {
			return;
		}

		ServerPlayer proxyPlayer = session.controlledProxyPlayer();
		ServerGamePacketListenerImpl proxyListener = session.controlledProxyListener();
		if (proxyPlayer == null || proxyListener == null || proxyPlayer.level() != root.level()) {
			stopControlling(player, true, true);
			return;
		}

		Vec3 intendedMovement = session.velocity();
		syncControlledProxyListenerTickState(player, session);
		applyControlledTravel(proxyPlayer);
		Vec3 currentPos = proxyPlayer.position();
		Vec3 actualMovement = currentPos.subtract(session.lastPlayerPos());
		float yaw = proxyPlayer.getYRot();
		float pitch = proxyPlayer.getXRot();
		syncControlledProxyShellState(session, proxyPlayer);
		proxyListener.resetPosition();
		root.noPhysics = true;
		root.setPos(currentPos.x, currentPos.y, currentPos.z);
		root.setYRot(yaw);
		root.setXRot(pitch);
		root.setDeltaMovement(proxyPlayer.getDeltaMovement());
		root.hurtMarked = true;
		syncDroneCameraAnchor(root, proxyPlayer.getDeltaMovement());
		session.setLastPlayerPos(currentPos);
		maybePlayDroneLoopSound(root, session.forwardDrive(), session.strafeDrive(), true);
		setHotbarVisualHidden(player, true);
		if (shouldDestroyDroneFromCollision(intendedMovement, actualMovement, proxyPlayer.horizontalCollision, proxyPlayer.verticalCollision)) {
			destroyDrone(root, null, false);
			stopControlling(player, true, true);
			return;
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
		syncDroneDisplay(root, yaw, pitch, displayForwardDrive, displayStrafeDrive);
		syncControlledPlayer(player, root);
		updateDroneHud(player, session, false);
	}

	private static void tickUncontrolledDrone(Entity root, UncontrolledDroneState state) {
		if (root == null || state == null) {
			return;
		}
		if (isUncontrolledDroneSettled(root, state.velocity())) {
			settleUncontrolledDrone(root, state);
			return;
		}

		Vec3 velocity = state.velocity() == null ? Vec3.ZERO : state.velocity();
		velocity = new Vec3(
				velocity.x * UNCONTROLLED_AIR_DRAG,
				velocity.y * UNCONTROLLED_AIR_DRAG - UNCONTROLLED_GRAVITY,
				velocity.z * UNCONTROLLED_AIR_DRAG
		);

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
		if (isUncontrolledDroneSettled(root, actualMovement)) {
			settleUncontrolledDrone(root, state);
			return;
		}
		applyUncontrolledRotation(root, state, actualMovement);
		root.setDeltaMovement(actualMovement);
		root.hurtMarked = true;
		syncDroneDisplay(root, root.getYRot(), root.getXRot(), 0.0D, 0.0D);
		syncDroneCameraAnchor(root, actualMovement);
		NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
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

	private static ReturnLocation resolveReturnLocation(MinecraftServer server, DroneControlSession session, Entity dummy) {
		if (server == null || session == null) {
			return new ReturnLocation(null, null, 0.0F, 0.0F);
		}
		if (dummy != null && dummy.level() instanceof ServerLevel dummyLevel && dummy.isAlive()) {
			return new ReturnLocation(dummyLevel, dummy.position(), dummy.getYRot(), dummy.getXRot());
		}
		ServerLevel origin = server.getLevel(session.originDimension());
		return origin == null
				? new ReturnLocation(null, null, session.originYaw(), session.originPitch())
				: new ReturnLocation(origin, session.originPos(), session.originYaw(), session.originPitch());
	}

	private static void discardDummyIfPresent(MinecraftServer server, DroneControlSession session, Entity loadedDummy) {
		if (server == null || session == null || session.dummyUuid() == null) {
			return;
		}
		DUMMY_OWNER_BY_UUID.remove(session.dummyUuid());
		if (loadedDummy != null) {
			loadedDummy.discard();
			return;
		}
		ServerLevel originLevel = server.getLevel(session.originDimension());
		if (originLevel == null) {
			return;
		}
		originLevel.getChunkAt(net.minecraft.core.BlockPos.containing(session.originPos()));
		Entity dummy = findEntity(server, session.originDimension(), session.dummyUuid());
		if (dummy != null) {
			dummy.discard();
		}
	}

	private static ServerPlayer createControlledProxyPlayer(ServerPlayer controller, ServerLevel droneLevel, Entity root) {
		if (controller == null || droneLevel == null || root == null) {
			return null;
		}
		GameProfile sourceProfile = controller.getGameProfile();
		String profileName = sourceProfile == null || sourceProfile.name() == null || sourceProfile.name().isBlank()
				? "DroneProxy"
				: sourceProfile.name();
		PropertyMap sourceProperties = sourceProfile != null
				? new PropertyMap(ImmutableMultimap.copyOf(sourceProfile.properties()))
				: new PropertyMap(ImmutableMultimap.of());
		GameProfile proxyProfile = new GameProfile(UUID.randomUUID(), profileName, sourceProperties);

		ServerPlayer proxyPlayer = new ServerPlayer(droneLevel.getServer(), droneLevel, proxyProfile, controller.clientInformation());
		proxyPlayer.setPos(root.getX(), root.getY(), root.getZ());
		proxyPlayer.setYRot(root.getYRot());
		proxyPlayer.setXRot(root.getXRot());
		proxyPlayer.setYHeadRot(root.getYRot());
		proxyPlayer.setYBodyRot(root.getYRot());
		proxyPlayer.setDeltaMovement(Vec3.ZERO);
		proxyPlayer.setInvisible(true);
		proxyPlayer.setNoGravity(true);
		proxyPlayer.setInvulnerable(true);
		proxyPlayer.setSilent(true);
		proxyPlayer.addTag(DRONE_CONTROLLED_PROXY_TAG);
		proxyPlayer.noPhysics = false;
		proxyPlayer.fallDistance = 0.0F;
		return proxyPlayer;
	}

	private static ServerGamePacketListenerImpl createControlledProxyListener(ServerPlayer controller, ServerPlayer proxyPlayer) {
		if (controller == null || controller.connection == null || proxyPlayer == null) {
			return null;
		}
		ClientInformation clientInformation = controller.clientInformation();
		if (clientInformation == null) {
			return null;
		}
		CommonListenerCookie cookie = new CommonListenerCookie(
				proxyPlayer.getGameProfile(),
				0,
				clientInformation,
				false
		);
		MinecraftServer server = controller.level() != null ? controller.level().getServer() : null;
		if (server == null) {
			return null;
		}
		ServerGamePacketListenerImpl listener = new ServerGamePacketListenerImpl(
				server,
				((ServerCommonPacketListenerImplAccessor) controller.connection).lg2$getConnection(),
				proxyPlayer,
				cookie
		);
		ServerGamePacketListenerImplAccessor accessor = (ServerGamePacketListenerImplAccessor) listener;
		accessor.lg2$markClientLoaded();
		listener.resetPosition();
		return listener;
	}

	private static void detachDroneFromController(ServerPlayer player, Entity root) {
		if (player == null || root == null) {
			return;
		}
		if (root.getVehicle() == player || root.isPassenger() || player.hasPassenger(root)) {
			root.stopRiding();
		}
		syncPassengerAttachment(player);
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

	private static void restoreControlledPlayerState(ServerPlayer player, DroneControlSession session) {
		if (player == null || session == null) {
			return;
		}
		player.setCamera(player);
		player.setInvisible(session.wasInvisible());
		player.setNoGravity(session.wasNoGravity());
		player.noPhysics = session.wasNoPhysics();
		player.setInvulnerable(session.wasInvulnerable());
		player.stopFallFlying();
		player.setDeltaMovement(Vec3.ZERO);
		player.getAbilities().mayfly = session.hadMayfly();
		player.getAbilities().flying = session.wasFlying();
		player.onUpdateAbilities();
		player.fallDistance = 0.0F;
		player.hurtMarked = true;
		FORCED_CONTROLLED_PLAYERS.remove(player.getUUID());
	}

	private static GameType resolveServerGameMode(ServerPlayer player) {
		if (player == null || player.gameMode == null) {
			return GameType.SURVIVAL;
		}
		GameType gameMode = player.gameMode.getGameModeForPlayer();
		return gameMode == null ? GameType.SURVIVAL : gameMode;
	}

	private static void spoofClientGameMode(ServerPlayer player, GameType gameMode) {
		if (player == null || player.connection == null) {
			return;
		}
		GameType resolved = gameMode == null ? GameType.SURVIVAL : gameMode;
		sendControlledOperatorPacket(player, new ClientboundGameEventPacket(
			ClientboundGameEventPacket.CHANGE_GAME_MODE,
			resolved.getId()
		));
	}

	private static void ensureControlledPlayerState(ServerPlayer player) {
		if (player == null) {
			return;
		}
		FORCED_CONTROLLED_PLAYERS.add(player.getUUID());
		player.setCamera(player);
		player.setInvisible(true);
		player.setNoGravity(true);
		player.noPhysics = false;
		player.setInvulnerable(true);
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
		player.fallDistance = 0.0F;
		if (!player.isFallFlying()) {
			player.startFallFlying();
		}
		player.hurtMarked = true;
	}

	private static void ensureControlledProxyState(ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.setCamera(player);
		player.setInvisible(true);
		player.setNoGravity(true);
		player.noPhysics = false;
		player.setInvulnerable(true);
		player.setSilent(true);
		if (player.getAbilities().flying) {
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
		player.fallDistance = 0.0F;
		if (!player.isFallFlying()) {
			player.startFallFlying();
		}
		player.hurtMarked = true;
	}

	private static void clearForcedControlMovementState(ServerPlayer player) {
		if (player == null) {
			return;
		}
		player.setCamera(player);
		if (!player.getAbilities().mayfly) {
			player.setNoGravity(false);
		}
		player.noPhysics = false;
		player.stopFallFlying();
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0.0F;
		player.hurtMarked = true;
	}

	private static void syncControlledPlayer(ServerPlayer player, Entity root) {
		if (player != null) {
			DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
			if (session != null) {
				if (session.controlledProxyPlayer() != null) {
					syncControlledOperatorView(player, session, root, false, false);
				}
				return;
			}
			player.fallDistance = 0.0F;
			if (player.getCamera() != player) {
				player.setCamera(player);
			}
			return;
		}
	}

	private static void syncControlledPlayer(ServerPlayer player, Entity root, boolean forcePositionSync) {
		if (player != null) {
			DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
			if (session != null) {
				if (session.controlledProxyPlayer() != null) {
					syncControlledOperatorView(player, session, root, false, forcePositionSync);
				}
			}
			return;
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

	private static boolean isDroneActivelyControlled(Entity root) {
		if (root == null) {
			return false;
		}
		UUID controllerId = CONTROLLERS_BY_DRONE.get(root.getUUID());
		if (controllerId == null) {
			return false;
		}
		DroneControlSession session = ACTIVE_SESSIONS.get(controllerId);
		return session != null && Objects.equals(session.droneUuid(), root.getUUID());
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

	private static Abilities buildControlledOperatorAbilities(ServerPlayer player) {
		Abilities abilities = new Abilities();
		abilities.invulnerable = false;
		abilities.flying = false;
		abilities.mayfly = false;
		abilities.instabuild = player != null && player.getAbilities().instabuild;
		abilities.mayBuild = player == null || player.getAbilities().mayBuild;
		abilities.setFlyingSpeed(player == null ? 0.05F : player.getAbilities().getFlyingSpeed());
		abilities.setWalkingSpeed(player == null ? 0.1F : player.getAbilities().getWalkingSpeed());
		return abilities;
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
				session.velocity(),
				session.proxyYaw(),
				session.proxyPitch()
		);
		return ClientboundTeleportEntityPacket.teleport(player.getId(), change, ABSOLUTE_TELEPORT, false);
	}

	private static ClientboundPlayerPositionPacket buildControlledPlayerPositionPacket(DroneControlSession session) {
		PositionMoveRotation change = new PositionMoveRotation(
				session.proxyPos(),
				session.velocity(),
				session.proxyYaw(),
				session.proxyPitch()
		);
		return ClientboundPlayerPositionPacket.of(session.nextViewSyncTeleportId(), change, ABSOLUTE_TELEPORT);
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

	private static void sendControlledOperatorPacket(ServerPlayer player, Packet<?> packet) {
		if (player == null || player.connection == null || packet == null) {
			return;
		}
		runWithControlledOperatorPacketRewriteBypass(() -> player.connection.send(packet));
	}

	private static void refreshControlledOperatorActualView(ServerPlayer player) {
		if (player == null || player.connection == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		level.getChunkAt(BlockPos.containing(player.position()));
		player.teleportTo(level, player.getX(), player.getY(), player.getZ(), ABSOLUTE_TELEPORT, player.getYRot(), player.getXRot(), false);
		player.connection.send(buildActualSelfMetadataPacket(player));
		player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), player.getDeltaMovement()));
		player.connection.send(new ClientboundSetPassengersPacket(player));
		player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
	}

	private static void syncControlledOperatorView(
			ServerPlayer player,
			DroneControlSession session,
			Entity root,
			boolean forceGameMode,
			boolean forcePositionSync
	) {
		if (player == null || session == null || root == null || player.connection == null) {
			return;
		}
		if (forceGameMode) {
			sendControlledOperatorPacket(player, new ClientboundGameEventPacket(
					ClientboundGameEventPacket.CHANGE_GAME_MODE,
					GameType.SPECTATOR.getId()
			));
			sendControlledOperatorPacket(player, new ClientboundPlayerAbilitiesPacket(buildControlledOperatorAbilities(player)));
		}
		if (forceGameMode || forcePositionSync) {
			sendControlledOperatorPacket(player, buildControlledPlayerPositionPacket(session));
		}
		sendControlledOperatorPacket(player, new ClientboundSetEntityMotionPacket(player.getId(), session.velocity()));
		sendControlledOperatorPacket(player, buildControlledSelfMetadataPacket(player));
		sendControlledOperatorPacket(player, buildControlledOperatorPassengerPacket(player, session));
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
		double normalized = net.minecraft.util.Mth.clamp(controlSpeedSlot, 0, 8) / 8.0D;
		return net.minecraft.util.Mth.lerp(normalized, DRONE_MIN_CONTROL_DRIVE_STEP, DRONE_MAX_CONTROL_DRIVE_STEP);
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
		UUID currentControllerId = CONTROLLERS_BY_DRONE.get(root.getUUID());
		if (currentControllerId != null && !Objects.equals(currentControllerId, player.getUUID())) {
			player.sendSystemMessage(Component.literal("Этот дрон уже управляется другим игроком."));
			return false;
		}

		stopControlling(player, true, false);
		markCameraSuppressedForPlayer(player);
		CameraVideoRecordingSystem.stopForDroneControl(player);
		MapImageRenderSystem.cancelRender(player.getUUID());
		RendererBotCameraSystem.stopCameraHotbarWarmupForPlayer(player.getUUID());
		ServerRaceSystem.suspendCopperManJetpackForDrone(player);
		GameType originalServerGameMode = resolveServerGameMode(player);
		UNCONTROLLED_DRONES.remove(root.getUUID());
		root.noPhysics = true;

		ServerLevel originLevel = player.level();
		Vec3 originPos = player.position();
		float originYaw = player.getYRot();
		float originPitch = player.getXRot();
		boolean wasInvisible = player.isInvisible();
		boolean wasNoGravity = player.isNoGravity();
		boolean wasNoPhysics = player.noPhysics;
		boolean wasInvulnerable = player.isInvulnerable();
		boolean hadMayfly = player.getAbilities().mayfly;
		boolean wasFlying = player.getAbilities().flying;
		ServerPlayer controlledProxyPlayer = createControlledProxyPlayer(player, droneLevel, root);
		if (controlledProxyPlayer == null) {
			return false;
		}
		ServerGamePacketListenerImpl controlledProxyListener = createControlledProxyListener(player, controlledProxyPlayer);
		if (controlledProxyListener == null) {
			return false;
		}
		root.level().getChunkAt(root.blockPosition());
		spoofClientGameMode(player, GameType.SPECTATOR);
		setHotbarVisualHidden(player, true);
		syncDroneCameraAnchor(root, Vec3.ZERO);

		DroneControlSession session = new DroneControlSession(
				root.getUUID(),
				droneLevel.dimension(),
				null,
				originLevel.dimension(),
				originPos,
				originYaw,
				originPitch,
				wasInvisible,
				wasNoGravity,
				wasNoPhysics,
				wasInvulnerable,
				hadMayfly,
				wasFlying,
				originalServerGameMode
		);
		session.setControlledProxyPlayer(controlledProxyPlayer);
		session.setControlledProxyListener(controlledProxyListener);
		session.setProxyPos(root.position());
		session.setProxyYaw(root.getYRot());
		session.setProxyPitch(root.getXRot());
		session.setLastPlayerPos(root.position());
		ACTIVE_SESSIONS.put(player.getUUID(), session);
		INPUTS.put(player.getUUID(), DroneInputState.EMPTY);
		CONTROLLERS_BY_DRONE.put(root.getUUID(), player.getUUID());
		CONTROLLED_PROXY_TO_CONTROLLER.put(controlledProxyPlayer.getUUID(), player.getUUID());
		syncControlledOperatorView(player, session, root, true, true);
		notifyDroneNetworkChanged(root);
		updateDroneHud(player, session, true);
		player.sendSystemMessage(Component.literal("Управление дроном начато. Shift — выйти."));
		return true;
	}

	private static void stopControlling(ServerPlayer player, boolean returnToOrigin, boolean notify) {
		if (player == null) {
			return;
		}
		DroneControlSession session = ACTIVE_SESSIONS.remove(player.getUUID());
		INPUTS.remove(player.getUUID());
		CONTROLLED_OPERATOR_KNOCKBACK_VELOCITY.remove(player.getUUID());
		if (session == null) {
			spoofClientGameMode(player, resolveServerGameMode(player));
			if (!FORCED_CONTROLLED_PLAYERS.contains(player.getUUID())) {
				setHotbarVisualHidden(player, false);
				refreshControlledOperatorActualView(player);
				return;
			}
			FORCED_CONTROLLED_PLAYERS.remove(player.getUUID());
			markCameraSuppressedForPlayer(player);
			clearForcedControlMovementState(player);
			detachAnyDronePassengersFromController(player);
			setHotbarVisualHidden(player, false);
			refreshControlledOperatorActualView(player);
			return;
		}
		ServerPlayer controlledProxyPlayer = session.controlledProxyPlayer();
		if (controlledProxyPlayer != null) {
			CONTROLLED_PROXY_TO_CONTROLLER.remove(controlledProxyPlayer.getUUID(), player.getUUID());
		}
		if (session.dummyUuid() != null) {
			DUMMY_OWNER_BY_UUID.remove(session.dummyUuid());
		}

		CONTROLLERS_BY_DRONE.remove(session.droneUuid(), player.getUUID());
		MinecraftServer server = player.level().getServer();
		Entity root = server == null ? null : findDroneRoot(server, session.droneDimension(), session.droneUuid());

		player.setCamera(player);
		detachAnyDronePassengersFromController(player);
		if (root != null) {
			Vec3 proxyPos = session.proxyPos();
			root.setPos(proxyPos.x, proxyPos.y, proxyPos.z);
			root.setYRot(session.proxyYaw());
			root.setXRot(session.proxyPitch());
			Vec3 releasedVelocity = session.velocity();
			root.noPhysics = false;
			root.setDeltaMovement(releasedVelocity);
			root.hurtMarked = true;
			UNCONTROLLED_DRONES.put(
					root.getUUID(),
					new UncontrolledDroneState(root.getUUID(), ((ServerLevel) root.level()).dimension(), releasedVelocity, root.getYRot(), root.getXRot())
			);
			syncDroneDisplay(root, root.getYRot(), root.getXRot(), 0.0D, 0.0D);
			syncDroneCameraAnchor(root, releasedVelocity);
			notifyDroneNetworkChanged(root);
		} else {
			NEXT_DRONE_SOUND_TICK.remove(session.droneUuid());
		}
		clearDroneHud(player, session, true);
		spoofClientGameMode(player, session.serverGameMode());
		setHotbarVisualHidden(player, false);
		refreshControlledOperatorActualView(player);

		detachAnyDronePassengersFromController(player);
		markCameraSuppressedForPlayer(player);
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
		DISPLAY_WOBBLE_BY_DRONE.remove(root.getUUID());
		BluetoothLinkSystem.removeDroneEndpoint(level, root.getUUID(), root.blockPosition());
		UUID controllerId = CONTROLLERS_BY_DRONE.get(root.getUUID());
		if (controllerId != null && level.getServer() != null) {
			ServerPlayer controller = level.getServer().getPlayerList().getPlayer(controllerId);
			if (controller != null) {
				stopControlling(controller, true, true);
			}
		}
		UUID displayId = DISPLAYS_BY_DRONE.remove(root.getUUID());
		Entity display = displayId == null ? findDroneDisplay(root) : findEntity(level.getServer(), level.dimension(), displayId);
		if (display != null) {
			display.discard();
		}
		UUID cameraAnchorId = CAMERA_ANCHORS_BY_DRONE.remove(root.getUUID());
		Entity cameraAnchor = cameraAnchorId == null ? findDroneCameraAnchor(root) : findEntity(level.getServer(), level.dimension(), cameraAnchorId);
		if (cameraAnchor != null) {
			cameraAnchor.discard();
		}
		for (Entity passenger : new ArrayList<>(root.getPassengers())) {
			passenger.discard();
		}
		if (dropItem && breaker != null && !breaker.getAbilities().instabuild && !kamikazeDrone) {
			root.spawnAtLocation(level, buildDroneDropStack(root));
		}
		if (kamikazeDrone) {
			detonateKamikazeDrone(level, droneCameraOrigin(root), root.getDeltaMovement(), kamikazePower);
		}
		root.discard();
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
			return IT_DRONE_SCOUT;
		}
		return null;
	}

	private static InteractionResult tryArmDroneWithTnt(ServerPlayer player, Entity root, ItemStack heldStack) {
		if (player == null || root == null || !(root.level() instanceof ServerLevel level) || heldStack == null || !heldStack.is(Items.TNT)) {
			return InteractionResult.PASS;
		}

		long now = level.getGameTime();
		long nextAllowedTick = NEXT_DRONE_ARM_ALLOWED_TICK.getOrDefault(root.getUUID(), Long.MIN_VALUE);
		if (now < nextAllowedTick) {
			return InteractionResult.CONSUME;
		}
		NEXT_DRONE_ARM_ALLOWED_TICK.put(root.getUUID(), now + 1L);

		int currentPower = resolveDroneKamikazePower(root);
		if (currentPower >= DRONE_KAMIKAZE_MAX_POWER) {
			playDroneKamikazeInsertFailFeedback(level, droneCameraOrigin(root));
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.NEGATIVE);
			return InteractionResult.CONSUME;
		}

		if (currentPower <= DRONE_KAMIKAZE_NO_POWER && !ServerUpgradeUiSystem.hasUpgrade(player, IT_DRONE_KAMIKAZE)) {
			playDroneKamikazeInsertFailFeedback(level, droneCameraOrigin(root));
			triggerDroneDisplayWobble(root, DroneDisplayWobbleType.NEGATIVE);
			return InteractionResult.FAIL;
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

	private static String resolveRequiredUpgradeForDroneRoot(Entity root) {
		if (root == null || !root.getTags().contains(DRONE_ROOT_TAG)) {
			return null;
		}
		return isKamikazeDrone(root) ? IT_DRONE_KAMIKAZE : IT_DRONE_SCOUT;
	}

	private static ItemStack buildDroneDropStack(Entity root) {
		if (isKamikazeDrone(root)) {
			return DroneItem.createKamikazeStack(resolveDroneKamikazePower(root));
		}
		return new ItemStack(ModItems.DRONE);
	}

	private static boolean isKamikazeDrone(Entity root) {
		return resolveDroneKamikazePower(root) > DRONE_KAMIKAZE_NO_POWER;
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

		Entity displayEntity = findDroneDisplay(root);
		if (displayEntity instanceof Display.ItemDisplay display) {
			display.setItemStack(DroneItem.createDisplayStack(ModItems.DRONE, clampedPower));
		}
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
			net.minecraft.world.item.Item droneItem,
			int kamikazePower
	) {
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.addTag(DRONE_DISPLAY_TAG);
		display.setPos(position.x, position.y, position.z);
		display.setYRot(yRot);
		display.setXRot(xRot);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setItemStack(DroneItem.createDisplayStack(droneItem, kamikazePower));
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
		Entity entity = findDroneDisplay(root);
		if (entity instanceof Display.ItemDisplay display) {
			boolean controlled = isDroneActivelyControlled(root);
			display.setPosRotInterpolationDuration(DRONE_DISPLAY_INTERPOLATION_TICKS);
			display.setTransformationInterpolationDelay(0);
			display.setTransformationInterpolationDuration(DRONE_DISPLAY_INTERPOLATION_TICKS);
			display.setYRot(yRot);
			display.setXRot(controlled ? 0.0F : xRot);
			display.setTransformation(buildDroneDisplayTransformation(root, forwardDrive, strafeDrive));
			if (!(display.isPassenger() && display.getVehicle() == root && root.hasPassenger(display))) {
				display.setPos(root.getX(), root.getY(), root.getZ());
			}
		}
	}

	private static Transformation buildDroneDisplayTransformation(Entity root, double forwardDrive, double strafeDrive) {
		float yOffset = 0.0F;
		if (isDroneActivelyControlled(root)) {
			yOffset = DRONE_DISPLAY_CONTROLLED_Y_OFFSET;
		}

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
				new Vector3f(0.0F, yOffset, 0.0F),
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
		if (display != null && display.getTags().contains(DRONE_DISPLAY_TAG)) {
			return display;
		}
		for (Entity candidate : level.getEntities(root, root.getBoundingBox().inflate(8.0D))) {
			if (!candidate.getTags().contains(DRONE_DISPLAY_TAG)) {
				continue;
			}
			if (candidate.getTags().contains(DRONE_DISPLAY_OWNER_TAG_PREFIX + root.getUUID())) {
				DISPLAYS_BY_DRONE.put(root.getUUID(), candidate.getUUID());
				return candidate;
			}
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
		return !level.noCollision(probe);
	}

	private static DronePilotDummyEntity spawnPlayerDummy(ServerLevel level, ServerPlayer sourcePlayer, Vec3 position) {
		DronePilotDummyEntity dummy = new DronePilotDummyEntity(level);
		dummy.addTag(DRONE_DUMMY_TAG);
		dummy.setPos(position.x, position.y, position.z);
		dummy.setYRot(sourcePlayer.getYRot());
		dummy.setXRot(sourcePlayer.getXRot());
		dummy.setYHeadRot(sourcePlayer.getYRot());
		dummy.yBodyRot = sourcePlayer.getYRot();
		dummy.setCustomName(Component.literal(sourcePlayer.getGameProfile().name()));
		dummy.setCustomNameVisible(true);
		dummy.setNoAi(true);
		dummy.setNoGravity(true);
		dummy.setInvulnerable(true);
		dummy.setSilent(true);
		dummy.setPersistenceRequired();
		copyEquipmentToDummy(sourcePlayer, dummy);
		GameProfile profile = createDummyProfile(sourcePlayer, dummy.getUUID());
		PolymerEntityUtils.setPolymerEntity(dummy, new DronePilotOverlay(profile));
		level.addFreshEntity(dummy);
		return dummy;
	}

	private static void copyEquipmentToDummy(ServerPlayer sourcePlayer, DronePilotDummyEntity dummy) {
		if (sourcePlayer == null || dummy == null) {
			return;
		}

		dummy.setItemSlot(EquipmentSlot.HEAD, sourcePlayer.getItemBySlot(EquipmentSlot.HEAD).copy());
		dummy.setItemSlot(EquipmentSlot.CHEST, sourcePlayer.getItemBySlot(EquipmentSlot.CHEST).copy());
		dummy.setItemSlot(EquipmentSlot.LEGS, sourcePlayer.getItemBySlot(EquipmentSlot.LEGS).copy());
		dummy.setItemSlot(EquipmentSlot.FEET, sourcePlayer.getItemBySlot(EquipmentSlot.FEET).copy());
		dummy.setItemSlot(EquipmentSlot.MAINHAND, sourcePlayer.getMainHandItem().copy());
		dummy.setItemSlot(EquipmentSlot.OFFHAND, sourcePlayer.getOffhandItem().copy());
	}

	private static void syncDummyHeldItems(ServerPlayer sourcePlayer, DroneControlSession session) {
		if (sourcePlayer == null || session == null || session.dummyUuid() == null) {
			return;
		}
		MinecraftServer server = sourcePlayer.level().getServer();
		if (server == null) {
			return;
		}

		Entity entity = findEntity(server, session.originDimension(), session.dummyUuid());
		if (!(entity instanceof DronePilotDummyEntity dummy)) {
			return;
		}

		ItemStack head = sourcePlayer.getItemBySlot(EquipmentSlot.HEAD);
		ItemStack chest = sourcePlayer.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack legs = sourcePlayer.getItemBySlot(EquipmentSlot.LEGS);
		ItemStack feet = sourcePlayer.getItemBySlot(EquipmentSlot.FEET);
		ItemStack main = resolveDummyMainHandStack(sourcePlayer);
		ItemStack off = resolveDummyOffhandStack(sourcePlayer);
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.HEAD), head)) {
			dummy.setItemSlot(EquipmentSlot.HEAD, head.copy());
		}
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.CHEST), chest)) {
			dummy.setItemSlot(EquipmentSlot.CHEST, chest.copy());
		}
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.LEGS), legs)) {
			dummy.setItemSlot(EquipmentSlot.LEGS, legs.copy());
		}
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.FEET), feet)) {
			dummy.setItemSlot(EquipmentSlot.FEET, feet.copy());
		}
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.MAINHAND), main)) {
			dummy.setItemSlot(EquipmentSlot.MAINHAND, main.copy());
		}
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.OFFHAND), off)) {
			dummy.setItemSlot(EquipmentSlot.OFFHAND, off.copy());
		}
	}

	private static ItemStack resolveDummyMainHandStack(ServerPlayer player) {
		if (player == null) {
			return ItemStack.EMPTY;
		}
		ControlledInventorySnapshot snapshot = CONTROLLED_INVENTORY_SNAPSHOTS.get(player.getUUID());
		if (snapshot == null || snapshot.hotbar().isEmpty()) {
			return player.getMainHandItem();
		}
		int selectedSlot = net.minecraft.util.Mth.clamp(player.getInventory().getSelectedSlot(), 0, 8);
		if (selectedSlot < 0 || selectedSlot >= snapshot.hotbar().size()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = snapshot.hotbar().get(selectedSlot);
		return stack == null ? ItemStack.EMPTY : stack;
	}

	private static ItemStack resolveDummyOffhandStack(ServerPlayer player) {
		if (player == null) {
			return ItemStack.EMPTY;
		}
		ControlledInventorySnapshot snapshot = CONTROLLED_INVENTORY_SNAPSHOTS.get(player.getUUID());
		if (snapshot == null) {
			return player.getOffhandItem();
		}
		ItemStack stack = snapshot.offhand();
		return stack == null ? ItemStack.EMPTY : stack;
	}

	private static boolean stacksEqual(ItemStack first, ItemStack second) {
		if (first == null || first.isEmpty()) {
			return second == null || second.isEmpty();
		}
		if (second == null || second.isEmpty()) {
			return false;
		}
		if (first.getCount() != second.getCount()) {
			return false;
		}
		return ItemStack.isSameItemSameComponents(first, second);
	}

	private static ServerPlayer resolveDummyController(ServerLevel level, UUID dummyUuid) {
		if (level == null || dummyUuid == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return null;
		}
		UUID controllerId = DUMMY_OWNER_BY_UUID.get(dummyUuid);
		ServerPlayer controller = controllerId == null ? null : server.getPlayerList().getPlayer(controllerId);
		if (controller != null && ACTIVE_SESSIONS.containsKey(controller.getUUID())) {
			return controller;
		}
		for (Map.Entry<UUID, DroneControlSession> entry : ACTIVE_SESSIONS.entrySet()) {
			DroneControlSession session = entry.getValue();
			if (session == null || !Objects.equals(session.dummyUuid(), dummyUuid)) {
				continue;
			}
			ServerPlayer resolved = server.getPlayerList().getPlayer(entry.getKey());
			if (resolved != null) {
				DUMMY_OWNER_BY_UUID.put(dummyUuid, resolved.getUUID());
				return resolved;
			}
		}
		DUMMY_OWNER_BY_UUID.remove(dummyUuid);
		return null;
	}

	private static boolean forwardDummyDamageToController(DronePilotDummyEntity dummy, ServerLevel level, DamageSource source, float amount) {
		if (dummy == null || level == null || amount <= 0.0F) {
			return false;
		}
		ServerPlayer controller = resolveDummyController(level, dummy.getUUID());
		if (controller == null || !controller.isAlive()) {
			return false;
		}
		if (!(controller.level() instanceof ServerLevel controllerLevel)) {
			return false;
		}
		DamageSource forwardedSource = source != null ? source : controllerLevel.damageSources().generic();
		boolean damaged;
		try {
			damaged = controller.hurtServer(controllerLevel, forwardedSource, amount);
		} catch (Exception exception) {
			damaged = controller.hurtServer(controllerLevel, controllerLevel.damageSources().generic(), amount);
		}
		if (!controller.isAlive() || controller.isDeadOrDying()) {
			stopControlling(controller, true, false);
		}
		return damaged;
	}

	private static void broadcastDronePilotEquipmentHidden(ServerPlayer player, boolean hidden) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>(6);
		slots.add(Pair.of(EquipmentSlot.HEAD, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.HEAD).copy()));
		slots.add(Pair.of(EquipmentSlot.CHEST, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.CHEST).copy()));
		slots.add(Pair.of(EquipmentSlot.LEGS, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.LEGS).copy()));
		slots.add(Pair.of(EquipmentSlot.FEET, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.FEET).copy()));
		slots.add(Pair.of(EquipmentSlot.MAINHAND, hidden ? ItemStack.EMPTY : player.getMainHandItem().copy()));
		slots.add(Pair.of(EquipmentSlot.OFFHAND, hidden ? ItemStack.EMPTY : player.getOffhandItem().copy()));

		ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(player.getId(), slots);
		for (ServerPlayer viewer : level.players()) {
			viewer.connection.send(packet);
		}
	}

	private static void stashAndHideControlledInventory(ServerPlayer player) {
		if (player == null) {
			return;
		}
		UUID playerId = player.getUUID();
		if (CONTROLLED_INVENTORY_SNAPSHOTS.containsKey(playerId)) {
			return;
		}
		List<ItemStack> hotbar = new ArrayList<>(9);
		for (int index = 0; index < 9; index++) {
			hotbar.add(player.getInventory().getItem(index).copy());
			player.getInventory().setItem(index, ItemStack.EMPTY);
		}
		ItemStack offhand = player.getOffhandItem().copy();
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		CONTROLLED_INVENTORY_SNAPSHOTS.put(playerId, new ControlledInventorySnapshot(hotbar, offhand));
		player.inventoryMenu.broadcastChanges();
		setHotbarVisualHidden(player, true);
	}

	private static void restoreControlledInventoryIfNeeded(ServerPlayer player) {
		if (player == null) {
			return;
		}
		ControlledInventorySnapshot snapshot = CONTROLLED_INVENTORY_SNAPSHOTS.remove(player.getUUID());
		if (snapshot == null) {
			return;
		}
		List<ItemStack> hotbar = snapshot.hotbar();
		for (int index = 0; index < 9; index++) {
			ItemStack saved = index < hotbar.size() ? hotbar.get(index) : ItemStack.EMPTY;
			restoreSavedStackToHotbarSlot(player, index, saved);
		}
		restoreSavedStackToOffhand(player, snapshot.offhand());
		player.inventoryMenu.broadcastChanges();
		player.inventoryMenu.sendAllDataToRemote();
	}

	private static void restoreSavedStackToHotbarSlot(ServerPlayer player, int slot, ItemStack saved) {
		if (player == null || slot < 0 || slot >= 9 || saved == null || saved.isEmpty()) {
			return;
		}
		ItemStack current = player.getInventory().getItem(slot);
		if (current == null || current.isEmpty()) {
			player.getInventory().setItem(slot, saved.copy());
			return;
		}
		if (!player.getInventory().add(saved.copy())) {
			player.drop(saved.copy(), false);
		}
	}

	private static void restoreSavedStackToOffhand(ServerPlayer player, ItemStack saved) {
		if (player == null || saved == null || saved.isEmpty()) {
			return;
		}
		ItemStack current = player.getOffhandItem();
		if (current == null || current.isEmpty()) {
			player.setItemInHand(InteractionHand.OFF_HAND, saved.copy());
			return;
		}
		if (!player.getInventory().add(saved.copy())) {
			player.drop(saved.copy(), false);
		}
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

	private static GameProfile createDummyProfile(ServerPlayer sourcePlayer, UUID fakeProfileId) {
		GameProfile sourceProfile = sourcePlayer.getGameProfile();
		PropertyMap properties = sourceProfile != null
				? new PropertyMap(ImmutableMultimap.copyOf(sourceProfile.properties()))
				: new PropertyMap(ImmutableMultimap.of());
		applySkinRestorerSkin(sourcePlayer, properties);
		return new GameProfile(fakeProfileId, sourcePlayer.getGameProfile().name(), properties);
	}

	private static void applySkinRestorerSkin(ServerPlayer sourcePlayer, PropertyMap properties) {
		if (sourcePlayer == null || properties == null) {
			return;
		}
		try {
			SkinStorage skinStorage = SkinRestorer.getSkinStorage();
			if (skinStorage == null) {
				return;
			}
			SkinValue skinValue = skinStorage.getSkin(sourcePlayer.getUUID());
			if (skinValue == null || skinValue.value() == null) {
				return;
			}
			properties.removeAll("textures");
			properties.put("textures", skinValue.value());
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to resolve drone dummy skin for {}", sourcePlayer.getScoreboardName(), exception);
		}
	}

	private static Vec3 resolvePlacementPosition(UseOnContext context) {
		net.minecraft.core.Direction face = context.getClickedFace();
		net.minecraft.core.BlockPos anchor = context.getClickedPos().relative(face);
		double yOffset = face == net.minecraft.core.Direction.UP ? 0.0D : DRONE_SPAWN_Y_OFFSET;
		return new Vec3(anchor.getX() + 0.5D, anchor.getY() + yOffset, anchor.getZ() + 0.5D);
	}

	private static AABB droneBoxAt(Vec3 position) {
		double halfWidth = DRONE_WIDTH * 0.5D;
		return new AABB(
				position.x - halfWidth,
				position.y,
				position.z - halfWidth,
				position.x + halfWidth,
				position.y + DRONE_HEIGHT,
				position.z + halfWidth
		);
	}

	private static Vec3 droneCameraOrigin(Entity root) {
		return root == null ? Vec3.ZERO : droneCameraOrigin(root.position());
	}

	private static Vec3 droneCameraOrigin(Vec3 rootPosition) {
		if (rootPosition == null) {
			return Vec3.ZERO;
		}
		return new Vec3(rootPosition.x, rootPosition.y + DRONE_HEIGHT * 0.5D, rootPosition.z);
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

	private static void ensureDroneMounted(ServerPlayer player, Entity root) {
		if (player == null || root == null || !root.isAlive() || root.level() != player.level()) {
			return;
		}
		if (root.getVehicle() == player && player.hasPassenger(root)) {
			return;
		}
		forceEntityPassenger(player, root);
		Entity display = findDroneDisplay(root);
		if (display != null && !(display.getVehicle() == root && root.hasPassenger(display))) {
			forceEntityPassenger(root, display);
		}
	}

	private static void forceEntityPassenger(Entity vehicle, Entity passenger) {
		if (vehicle == null || passenger == null || vehicle == passenger) {
			return;
		}

		if (passenger.getVehicle() == vehicle && vehicle.hasPassenger(passenger)) {
			return;
		}

		if (passenger.isPassenger()) {
			passenger.stopRiding();
		}

		((EntityPassengerAccessor) passenger).lg2$setVehicle(vehicle);
		((EntityPassengerAccessor) vehicle).lg2$addPassenger(passenger);
		vehicle.positionRider(passenger);
		syncPassengerAttachment(vehicle);
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

	private static final class DronePilotOverlay implements PolymerEntity {
		private final GameProfile profile;

		private DronePilotOverlay(GameProfile profile) {
			this.profile = profile;
		}

		@Override
		public EntityType<?> getPolymerEntityType(PacketContext context) {
			return EntityType.PLAYER;
		}

		@Override
		public void onBeforeSpawnPacket(ServerPlayer player, java.util.function.Consumer<Packet<?>> packetConsumer) {
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
			ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
					this.profile.id(),
					this.profile,
					false,
					0,
					GameType.SURVIVAL,
					null,
					true,
					0,
					(RemoteChatSession.Data) null
			);
			packet.entries().add(entry);
			packetConsumer.accept(packet);
		}

		@Override
		public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
			upsertTrackedData(data, SynchedEntityData.DataValue.create(PlayerTrackedDataAccessor.lg2$getDataPlayerMainHand(), HumanoidArm.RIGHT));
			upsertTrackedData(data, SynchedEntityData.DataValue.create(PlayerTrackedDataAccessor.lg2$getDataPlayerModeCustomisation(), ALL_PLAYER_SKIN_PARTS));
		}

		private static void upsertTrackedData(List<SynchedEntityData.DataValue<?>> data, SynchedEntityData.DataValue<?> replacement) {
			for (int i = 0; i < data.size(); i++) {
				SynchedEntityData.DataValue<?> current = data.get(i);
				if (current.id() == replacement.id()) {
					data.set(i, replacement);
					return;
				}
			}
			data.add(replacement);
		}
	}

	private record ControlledInventorySnapshot(List<ItemStack> hotbar, ItemStack offhand) {
		private ControlledInventorySnapshot {
			List<ItemStack> safeHotbar = hotbar == null ? List.of() : hotbar;
			List<ItemStack> copiedHotbar = new ArrayList<>(safeHotbar.size());
			for (ItemStack stack : safeHotbar) {
				copiedHotbar.add(stack == null ? ItemStack.EMPTY : stack.copy());
			}
			hotbar = List.copyOf(copiedHotbar);
			offhand = offhand == null ? ItemStack.EMPTY : offhand.copy();
		}
	}

	private record ReturnLocation(ServerLevel level, Vec3 pos, float yaw, float pitch) {
	}

	private static final class UncontrolledDroneState {
		private final UUID droneUuid;
		private final net.minecraft.resources.ResourceKey<Level> dimension;
		private Vec3 velocity;
		private float yaw;
		private float pitch;

		private UncontrolledDroneState(UUID droneUuid, net.minecraft.resources.ResourceKey<Level> dimension, Vec3 velocity, float yaw, float pitch) {
			this.droneUuid = droneUuid;
			this.dimension = dimension;
			this.velocity = velocity == null ? Vec3.ZERO : velocity;
			this.yaw = yaw;
			this.pitch = pitch;
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
	}

	private record DroneInputState(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift, boolean sprint) {
		private static final DroneInputState EMPTY = new DroneInputState(false, false, false, false, false, false, false);
	}

	private static final class DronePilotDummyEntity extends PathfinderMob {
		private DronePilotDummyEntity(ServerLevel level) {
			super(EntityType.HUSK, level);
			this.xpReward = 0;
			this.setPersistenceRequired();
			this.setSilent(true);
			this.setInvulnerable(false);
			this.setNoAi(true);
			this.setNoGravity(true);
			this.refreshDimensions();
		}

		@Override
		protected void registerGoals() {
		}

		@Override
		protected PathNavigation createNavigation(Level level) {
			GroundPathNavigation navigation = new GroundPathNavigation(this, level);
			navigation.setCanFloat(true);
			return navigation;
		}

		@Override
		public void checkDespawn() {
		}

		@Override
		public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
			return forwardDummyDamageToController(this, level, source, amount);
		}
	}

	private static final class DroneControlSession {
		private final UUID droneUuid;
		private final net.minecraft.resources.ResourceKey<Level> droneDimension;
		private final UUID dummyUuid;
		private final net.minecraft.resources.ResourceKey<Level> originDimension;
		private final Vec3 originPos;
		private final float originYaw;
		private final float originPitch;
		private boolean wasInvisible;
		private boolean wasNoGravity;
		private boolean wasNoPhysics;
		private boolean wasInvulnerable;
		private boolean hadMayfly;
		private boolean wasFlying;
		private final GameType serverGameMode;
		private ServerPlayer controlledProxyPlayer;
		private ServerGamePacketListenerImpl controlledProxyListener;
		private Vec3 velocity = Vec3.ZERO;
		private Vec3 intendedVelocity = Vec3.ZERO;
		private Vec3 lastPlayerPos = Vec3.ZERO;
		private Vec3 proxyPos = Vec3.ZERO;
		private float proxyYaw;
		private float proxyPitch;
		private int nextViewSyncTeleportId = CONTROLLED_VIEW_TELEPORT_ID_BASE;
		private int lastAcceptedProxyTeleportId;
		private double forwardDrive;
		private double strafeDrive;
		private double displayForwardDrive;
		private double displayStrafeDrive;
		private boolean hudVisible;
		private String lastHudSnapshot = "";
		private long lastHudTick = Long.MIN_VALUE;

		private DroneControlSession(
				UUID droneUuid,
				net.minecraft.resources.ResourceKey<Level> droneDimension,
				UUID dummyUuid,
				net.minecraft.resources.ResourceKey<Level> originDimension,
				Vec3 originPos,
				float originYaw,
				float originPitch,
				boolean wasInvisible,
				boolean wasNoGravity,
				boolean wasNoPhysics,
				boolean wasInvulnerable,
				boolean hadMayfly,
				boolean wasFlying,
				GameType serverGameMode
		) {
			this.droneUuid = droneUuid;
			this.droneDimension = droneDimension;
			this.dummyUuid = dummyUuid;
			this.originDimension = originDimension;
			this.originPos = originPos;
			this.originYaw = originYaw;
			this.originPitch = originPitch;
			this.wasInvisible = wasInvisible;
			this.wasNoGravity = wasNoGravity;
			this.wasNoPhysics = wasNoPhysics;
			this.wasInvulnerable = wasInvulnerable;
			this.hadMayfly = hadMayfly;
			this.wasFlying = wasFlying;
			this.serverGameMode = serverGameMode == null ? GameType.SURVIVAL : serverGameMode;
		}

		private UUID droneUuid() {
			return this.droneUuid;
		}

		private net.minecraft.resources.ResourceKey<Level> droneDimension() {
			return this.droneDimension;
		}

		private UUID dummyUuid() {
			return this.dummyUuid;
		}

		private net.minecraft.resources.ResourceKey<Level> originDimension() {
			return this.originDimension;
		}

		private Vec3 originPos() {
			return this.originPos;
		}

		private float originYaw() {
			return this.originYaw;
		}

		private float originPitch() {
			return this.originPitch;
		}

		private boolean wasInvisible() {
			return this.wasInvisible;
		}

		private boolean wasNoGravity() {
			return this.wasNoGravity;
		}

		private boolean wasNoPhysics() {
			return this.wasNoPhysics;
		}

		private boolean wasInvulnerable() {
			return this.wasInvulnerable;
		}

		private boolean hadMayfly() {
			return this.hadMayfly;
		}

		private boolean wasFlying() {
			return this.wasFlying;
		}

		private GameType serverGameMode() {
			return this.serverGameMode;
		}

		private ServerPlayer controlledProxyPlayer() {
			return this.controlledProxyPlayer;
		}

		private void setControlledProxyPlayer(ServerPlayer controlledProxyPlayer) {
			this.controlledProxyPlayer = controlledProxyPlayer;
		}

		private ServerGamePacketListenerImpl controlledProxyListener() {
			return this.controlledProxyListener;
		}

		private void setControlledProxyListener(ServerGamePacketListenerImpl controlledProxyListener) {
			this.controlledProxyListener = controlledProxyListener;
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

		private Vec3 lastPlayerPos() {
			return this.lastPlayerPos;
		}

		private void setLastPlayerPos(Vec3 lastPlayerPos) {
			this.lastPlayerPos = lastPlayerPos == null ? Vec3.ZERO : lastPlayerPos;
		}

		private Vec3 proxyPos() {
			return this.proxyPos;
		}

		private void setProxyPos(Vec3 proxyPos) {
			this.proxyPos = proxyPos == null ? Vec3.ZERO : proxyPos;
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

		private int nextViewSyncTeleportId() {
			return this.nextViewSyncTeleportId++;
		}

		private int lastAcceptedProxyTeleportId() {
			return this.lastAcceptedProxyTeleportId;
		}

		private void setLastAcceptedProxyTeleportId(int lastAcceptedProxyTeleportId) {
			this.lastAcceptedProxyTeleportId = Math.max(0, lastAcceptedProxyTeleportId);
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
