package com.lostglade.server;

import com.google.common.collect.ImmutableMultimap;
import com.lostglade.Lg2;
import com.lostglade.item.DroneItem;
import com.lostglade.item.ModItems;
import com.lostglade.mixin.EntityPassengerAccessor;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class DroneSystem {
	private static final String IT_DRONE_SCOUT = "it_drone_scout";
	private static final String DRONE_ROOT_TAG = "lg2_drone_root";
	private static final String DRONE_DISPLAY_TAG = "lg2_drone_display";
	private static final String DRONE_DISPLAY_OWNER_TAG_PREFIX = "lg2_drone_display_owner_";
	private static final String DRONE_CAMERA_TAG = "lg2_drone_camera_anchor";
	private static final String DRONE_CAMERA_OWNER_TAG_PREFIX = "lg2_drone_camera_owner_";
	private static final String DRONE_DUMMY_TAG = "lg2_drone_dummy";
	private static final Identifier DRONE_LOOP_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_loop");
	private static final Holder<SoundEvent> DRONE_LOOP_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(DRONE_LOOP_SOUND_ID));
	private static final double DRONE_CRASH_EQUIVALENT_FALL_BLOCKS = 3.25D;
	private static final double DRONE_CRASH_REFERENCE_ACCELERATION = 0.04D;
	private static final float DRONE_WIDTH = 0.95F;
	private static final float DRONE_HEIGHT = 0.35F;
	private static final float DRONE_CAMERA_ANCHOR_SIZE = 0.01F;
	private static final double DRONE_SPAWN_Y_OFFSET = 0.24D;
	private static final float DRONE_DISPLAY_VIEW_RANGE = 64.0F;
	private static final float DRONE_DISPLAY_CONTROLLED_Y_OFFSET = 0.0F;
	private static final float DRONE_MAX_TILT_DEGREES = 32.0F;
	private static final long DRONE_LOOP_REPLAY_TICKS = 10L;
	private static final double DRONE_SOUND_RADIUS_SQR = 16.0D * 16.0D;
	private static final float DRONE_SOUND_SOURCE_POWER = 0.58F;
	private static final float DRONE_SOUND_MIN_VOLUME = 1.0F;
	private static final float DRONE_SOUND_MAX_VOLUME = 1.0F;
	private static final float DRONE_SOUND_MIN_PITCH = 0.76F;
	private static final float DRONE_SOUND_MAX_PITCH = 1.18F;
	private static final double UNCONTROLLED_GRAVITY = 0.04D;
	private static final double UNCONTROLLED_AIR_DRAG = 0.985D;
	private static final float UNCONTROLLED_ROTATION_LERP = 0.35F;
	private static final double UNCONTROLLED_SETTLED_HORIZONTAL_SPEED_SQR = 1.0E-6D;
	private static final double UNCONTROLLED_SETTLED_VERTICAL_SPEED = 0.045D;
	private static final int PLAYER_HOTBAR_MENU_SLOT_START = 36;
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
	private static final Map<UUID, DroneControlSession> ACTIVE_SESSIONS = new HashMap<>();
	private static final Map<UUID, DroneInputState> INPUTS = new HashMap<>();
	private static final Map<UUID, UUID> CONTROLLERS_BY_DRONE = new HashMap<>();
	private static final Map<UUID, UUID> DISPLAYS_BY_DRONE = new HashMap<>();
	private static final Map<UUID, UUID> CAMERA_ANCHORS_BY_DRONE = new HashMap<>();
	private static final Map<UUID, UncontrolledDroneState> UNCONTROLLED_DRONES = new HashMap<>();
	private static final Map<UUID, Long> NEXT_DRONE_SOUND_TICK = new HashMap<>();

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
			if (!ServerUpgradeUiSystem.hasUpgrade(serverPlayer, IT_DRONE_SCOUT)) {
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
		if (!ServerUpgradeUiSystem.hasUpgrade(player, IT_DRONE_SCOUT)) {
			return InteractionResult.FAIL;
		}

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

		Display.ItemDisplay display = createDroneDisplay(serverLevel, spawnPos, yRot, 0.0F);
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
		DroneControlSession session = ACTIVE_SESSIONS.get(player.getUUID());
		if (session == null) {
			return;
		}
		int controlSpeedSlot = getControlSpeedSlot(player);
		double driveStep = getControlDriveStep(controlSpeedSlot);

		DroneInputState input = INPUTS.getOrDefault(player.getUUID(), DroneInputState.EMPTY);
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

		ensureControlledPlayerState(player);

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

	public static boolean isDroneEntity(Entity entity) {
		return resolveDroneRoot(entity) != null;
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickControlledSessions(server);
		tickUncontrolledDrones(server);
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

			// If someone is actively controlling the drone, it should follow the player, not our physics.
			if (root.isPassenger() && root.getVehicle() instanceof ServerPlayer controller) {
				DroneControlSession session = ACTIVE_SESSIONS.get(controller.getUUID());
				if (session != null && Objects.equals(session.droneUuid(), root.getUUID())) {
					UNCONTROLLED_DRONES.remove(entry.getKey());
					continue;
				}
			}

			tickUncontrolledDrone(root, state);
		}
	}

	private static void tickControlledDrone(ServerPlayer player, Entity root, DroneControlSession session, DroneInputState input) {
		if (!(root.level() instanceof ServerLevel)) {
			return;
		}

		Vec3 currentPos = player.position();
		Vec3 actualMovement = currentPos.subtract(session.lastPlayerPos());
		Vec3 intendedMovement = session.velocity();
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		ensureDroneMounted(player, root);
		broadcastDronePilotEquipmentHidden(player, true);
		syncDummyHeldItems(player, session);
		root.setYRot(yaw);
		root.setXRot(pitch);
		root.setDeltaMovement(player.getDeltaMovement());
		root.hurtMarked = true;
		syncDroneCameraAnchor(root, player.getDeltaMovement());
		session.setVelocity(player.getDeltaMovement());
		session.setLastPlayerPos(currentPos);
		maybePlayDroneLoopSound(root, session.forwardDrive(), session.strafeDrive(), true);
		if (shouldDestroyDroneFromCollision(intendedMovement, actualMovement, player.horizontalCollision, player.verticalCollision)) {
			destroyDrone(root, null, false);
			stopControlling(player, true, true);
			return;
		}
		syncDroneDisplay(root, yaw, pitch, session.forwardDrive(), session.strafeDrive());
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
		playDroneLoopSound(level, root.position(), computeDroneSoundVolume(power), computeDroneSoundPitch(power));
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

	private static void playDroneLoopSound(ServerLevel level, Vec3 origin, float volume, float pitch) {
		if (level == null || origin == null || volume <= 0.0F) {
			return;
		}

		long seed = level.random.nextLong();
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(origin) > DRONE_SOUND_RADIUS_SQR || !PolymerResourcePackUtils.hasMainPack(viewer)) {
				continue;
			}
			viewer.connection.send(new ClientboundSoundPacket(
					DRONE_LOOP_SOUND,
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
		if (root.isPassenger() && root.getVehicle() instanceof ServerPlayer) {
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

	private static void detachDroneFromController(ServerPlayer player, Entity root) {
		if (player == null || root == null) {
			return;
		}
		if (player.hasPassenger(root)) {
			player.ejectPassengers();
		}
		if (root.isPassenger()) {
			root.stopRiding();
		}
		if (player.hasPassenger(root)) {
			((EntityPassengerAccessor) player).lg2$removePassenger(root);
		}
		if (root.getVehicle() != null) {
			((EntityPassengerAccessor) root).lg2$setVehicle(null);
		}
		syncPassengerAttachment(player);
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
	}

	private static void ensureControlledPlayerState(ServerPlayer player) {
		if (player == null) {
			return;
		}
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

	private static void syncControlledPlayer(ServerPlayer player, Entity root) {
		if (player != null && ACTIVE_SESSIONS.containsKey(player.getUUID())) {
			ensureControlledPlayerState(player);
			return;
		}
		player.fallDistance = 0.0F;
		if (player.getCamera() != player) {
			player.setCamera(player);
		}
	}

	private static void updateDroneHud(ServerPlayer player, DroneControlSession session, boolean force) {
		if (player == null || session == null || player.connection == null) {
			return;
		}

		long now = player.level().getGameTime();
		int controlSpeedSlot = getControlSpeedSlot(player);
		String snapshot = buildDroneHudSnapshot(session, player.getDeltaMovement(), controlSpeedSlot);
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
			player.connection.send(new ClientboundSetActionBarTextPacket(buildDroneHudTextSubtitle(session, player.getDeltaMovement(), controlSpeedSlot)));
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
		ServerRaceSystem.suspendCopperManJetpackForDrone(player);
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
		DronePilotDummyEntity dummy = spawnPlayerDummy(originLevel, player, originPos);
		root.level().getChunkAt(root.blockPosition());
		player.teleportTo(droneLevel, root.getX(), root.getY(), root.getZ(), ABSOLUTE_TELEPORT, root.getYRot(), root.getXRot(), false);
		broadcastDronePilotEquipmentHidden(player, true);
		setHotbarVisualHidden(player, true);
		player.stopFallFlying();
		player.setDeltaMovement(Vec3.ZERO);
		ensureControlledPlayerState(player);
		ensureDroneMounted(player, root);
		syncDroneCameraAnchor(root, Vec3.ZERO);

		DroneControlSession session = new DroneControlSession(
				root.getUUID(),
				droneLevel.dimension(),
				dummy != null ? dummy.getUUID() : null,
				originLevel.dimension(),
				originPos,
				originYaw,
				originPitch,
				wasInvisible,
				wasNoGravity,
				wasNoPhysics,
				wasInvulnerable,
				hadMayfly,
				wasFlying
		);
		session.setLastPlayerPos(player.position());
		ACTIVE_SESSIONS.put(player.getUUID(), session);
		INPUTS.put(player.getUUID(), DroneInputState.EMPTY);
		CONTROLLERS_BY_DRONE.put(root.getUUID(), player.getUUID());
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
		if (session == null) {
			return;
		}

		CONTROLLERS_BY_DRONE.remove(session.droneUuid(), player.getUUID());
		MinecraftServer server = player.level().getServer();
		Entity root = server == null ? null : findDroneRoot(server, session.droneDimension(), session.droneUuid());
		Entity dummy = server != null && session.dummyUuid() != null
				? findEntity(server, session.originDimension(), session.dummyUuid())
				: null;

		player.setCamera(player);
		if (root != null) {
			// Fully detach before teleporting the controller back, otherwise the passenger chain can drag the drone with the player.
			detachDroneFromController(player, root);
			root.setPos(player.getX(), player.getY(), player.getZ());
			root.setYRot(player.getYRot());
			root.setXRot(player.getXRot());
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
		restoreControlledPlayerState(player, session);
		clearDroneHud(player, session, true);
		broadcastDronePilotEquipmentHidden(player, false);
		setHotbarVisualHidden(player, false);

		if (returnToOrigin && server != null) {
			ReturnLocation returnLocation = resolveReturnLocation(server, session, dummy);
			ServerLevel level = returnLocation.level();
			Vec3 returnPos = returnLocation.pos();
			if (level != null && returnPos != null) {
				level.getChunkAt(net.minecraft.core.BlockPos.containing(returnPos));
				player.teleportTo(level, returnPos.x, returnPos.y, returnPos.z, ABSOLUTE_TELEPORT, returnLocation.yaw(), returnLocation.pitch(), false);
				restoreControlledPlayerState(player, session);
				if (root != null) {
					detachDroneFromController(player, root);
				}
				broadcastDronePilotEquipmentHidden(player, false);
			}
		}
		discardDummyIfPresent(server, session, dummy);
		ServerRaceSystem.resumeCopperManJetpackAfterDrone(player);

		if (notify) {
			player.sendSystemMessage(Component.literal("Управление дроном завершено."));
		}
	}

	private static void destroyDrone(Entity root, ServerPlayer breaker, boolean dropItem) {
		if (root == null || !root.isAlive() || !(root.level() instanceof ServerLevel level)) {
			return;
		}
		UNCONTROLLED_DRONES.remove(root.getUUID());
		NEXT_DRONE_SOUND_TICK.remove(root.getUUID());
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
		if (dropItem && breaker != null && !breaker.getAbilities().instabuild) {
			root.spawnAtLocation(level, new ItemStack(ModItems.DRONE));
		}
		root.discard();
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

	private static Display.ItemDisplay createDroneDisplay(ServerLevel level, Vec3 position, float yRot, float xRot) {
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
		display.addTag(DRONE_DISPLAY_TAG);
		display.setPos(position.x, position.y, position.z);
		display.setYRot(yRot);
		display.setXRot(xRot);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setItemStack(DroneItem.createDisplayStack());
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(DRONE_DISPLAY_VIEW_RANGE);
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
			boolean controlled = root != null && root.isPassenger() && root.getVehicle() instanceof ServerPlayer;
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
		if (root != null && root.isPassenger() && root.getVehicle() instanceof ServerPlayer) {
			yOffset = DRONE_DISPLAY_CONTROLLED_Y_OFFSET;
		}

		double forwardNorm = forwardDrive / DroneFlightPhysics.MAX_FORWARD_DRIVE;
		double strafeNorm = strafeDrive / DroneFlightPhysics.MAX_STRAFE_DRIVE;
		forwardNorm = net.minecraft.util.Mth.clamp(forwardNorm, -1.0D, 1.0D);
		strafeNorm = net.minecraft.util.Mth.clamp(strafeNorm, -1.0D, 1.0D);

		// Forward drive pitches the nose down; strafe drive rolls into the turn.
		float pitchTiltRad = (float) Math.toRadians((float) (forwardNorm * DRONE_MAX_TILT_DEGREES));
		float rollTiltRad = (float) Math.toRadians((float) (-strafeNorm * DRONE_MAX_TILT_DEGREES));

		return new Transformation(
				new Vector3f(0.0F, yOffset, 0.0F),
				new Quaternionf().rotateXYZ(pitchTiltRad, 0.0F, rollTiltRad),
				new Vector3f(1.0F, 1.0F, 1.0F),
				new Quaternionf()
		);
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
		Vec3 origin = droneCameraOrigin(root);
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
		Vec3 origin = droneCameraOrigin(root);
		anchor.setPos(origin.x, origin.y, origin.z);
		anchor.setYRot(root.getYRot());
		anchor.setXRot(root.getXRot());
		anchor.setDeltaMovement(velocity == null ? Vec3.ZERO : velocity);
		anchor.hurtMarked = true;
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

		ItemStack main = sourcePlayer.getMainHandItem();
		ItemStack off = sourcePlayer.getOffhandItem();
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.MAINHAND), main)) {
			dummy.setItemSlot(EquipmentSlot.MAINHAND, main.copy());
		}
		if (!stacksEqual(dummy.getItemBySlot(EquipmentSlot.OFFHAND), off)) {
			dummy.setItemSlot(EquipmentSlot.OFFHAND, off.copy());
		}
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
			this.setInvulnerable(true);
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
		private Vec3 velocity = Vec3.ZERO;
		private Vec3 lastPlayerPos = Vec3.ZERO;
		private double forwardDrive;
		private double strafeDrive;
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
				boolean wasFlying
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

		private Vec3 velocity() {
			return this.velocity;
		}

		private void setVelocity(Vec3 velocity) {
			this.velocity = velocity == null ? Vec3.ZERO : velocity;
		}

		private Vec3 lastPlayerPos() {
			return this.lastPlayerPos;
		}

		private void setLastPlayerPos(Vec3 lastPlayerPos) {
			this.lastPlayerPos = lastPlayerPos == null ? Vec3.ZERO : lastPlayerPos;
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
