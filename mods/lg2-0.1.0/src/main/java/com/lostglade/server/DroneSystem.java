package com.lostglade.server;

import com.google.common.collect.ImmutableMultimap;
import com.lostglade.Lg2;
import com.lostglade.item.DroneItem;
import com.lostglade.item.ModItems;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.skin.SkinStorage;
import net.lionarius.skinrestorer.skin.SkinValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
	private static final String DRONE_DUMMY_TAG = "lg2_drone_dummy";
	private static final double DRONE_CRASH_SPEED = 1.25D;
	private static final float DRONE_WIDTH = 0.95F;
	private static final float DRONE_HEIGHT = 0.35F;
	private static final double DRONE_SPAWN_Y_OFFSET = 0.24D;
	private static final float DRONE_DISPLAY_VIEW_RANGE = 64.0F;
	private static final byte ALL_PLAYER_SKIN_PARTS = (byte) 0x7F;
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final Map<UUID, DroneControlSession> ACTIVE_SESSIONS = new HashMap<>();
	private static final Map<UUID, DroneInputState> INPUTS = new HashMap<>();
	private static final Map<UUID, UUID> CONTROLLERS_BY_DRONE = new HashMap<>();
	private static final Map<UUID, UUID> DISPLAYS_BY_DRONE = new HashMap<>();

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
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stopControlling((ServerPlayer) handler.player, true, false));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> stopControlling(newPlayer, false, false));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID playerId : new ArrayList<>(ACTIVE_SESSIONS.keySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				if (player != null) {
					stopControlling(player, false, false);
				}
			}
			ACTIVE_SESSIONS.clear();
			INPUTS.clear();
			CONTROLLERS_BY_DRONE.clear();
			DISPLAYS_BY_DRONE.clear();
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
		serverLevel.addFreshEntity(root);
		serverLevel.addFreshEntity(display);
		display.addTag(DRONE_DISPLAY_OWNER_TAG_PREFIX + root.getUUID());
		DISPLAYS_BY_DRONE.put(root.getUUID(), display.getUUID());
		syncDroneDisplay(root, yRot, 0.0F);

		if (!player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	public static void handleInput(ServerPlayer player, Input input) {
		if (player == null || input == null || !ACTIVE_SESSIONS.containsKey(player.getUUID())) {
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

	public static boolean isDroneEntity(Entity entity) {
		return resolveDroneRoot(entity) != null;
	}

	private static void tick(MinecraftServer server) {
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

			DroneInputState input = INPUTS.getOrDefault(player.getUUID(), DroneInputState.EMPTY);
			if (input.shift()) {
				stopControlling(player, true, true);
				continue;
			}
			tickControlledDrone(player, root, session, input);
		}
	}

	private static void tickControlledDrone(ServerPlayer player, Entity root, DroneControlSession session, DroneInputState input) {
		if (!(root.level() instanceof ServerLevel)) {
			return;
		}

		session.setForwardDrive(
				DroneFlightPhysics.adjustDrive(
						session.forwardDrive(),
						input.forward(),
						input.backward(),
						DroneFlightPhysics.PLANAR_DRIVE_STEP,
						DroneFlightPhysics.MAX_FORWARD_DRIVE
				)
		);
		session.setStrafeDrive(
				DroneFlightPhysics.adjustDrive(
						session.strafeDrive(),
						input.right(),
						input.left(),
						DroneFlightPhysics.PLANAR_DRIVE_STEP,
						DroneFlightPhysics.MAX_STRAFE_DRIVE
				)
		);

		Vec3 currentPos = player.position();
		Vec3 actualMovement = currentPos.subtract(session.lastPlayerPos());
		double impactSpeed = Math.max(session.velocity().length(), actualMovement.length());
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		root.setYRot(yaw);
		root.setXRot(pitch);
		ensureDroneFlight(player, session, input);
		root.setPos(player.getX(), player.getY(), player.getZ());
		root.setDeltaMovement(player.getDeltaMovement());
		root.hurtMarked = true;
		session.setVelocity(player.getDeltaMovement());
		session.setLastPlayerPos(currentPos);
		if ((player.horizontalCollision || player.verticalCollision) && impactSpeed >= DRONE_CRASH_SPEED) {
			destroyDrone(root, null, false);
			stopControlling(player, true, true);
			return;
		}
		syncDroneDisplay(root, yaw, pitch);
		syncControlledPlayer(player, root);
	}

	private static void ensureDroneFlight(ServerPlayer player, DroneControlSession session, DroneInputState input) {
		player.setNoGravity(false);
		player.noPhysics = false;
		player.fallDistance = 0.0F;
		if (!player.isFallFlying()) {
			player.startFallFlying();
		}
		Vec3 nextVelocity = DroneFlightPhysics.step(
				player.getDeltaMovement(),
				player.getXRot(),
				player.getYRot(),
				session.forwardDrive(),
				session.strafeDrive(),
				new DroneFlightPhysics.ControlInput(
						input.forward(),
						input.backward(),
						input.left(),
						input.right(),
						input.jump(),
						input.sprint()
				),
				Vec3.ZERO
		);
		player.setDeltaMovement(nextVelocity);
		player.hurtMarked = true;
	}

	private static void syncControlledPlayer(ServerPlayer player, Entity root) {
		player.fallDistance = 0.0F;
		if (player.getCamera() != player) {
			player.setCamera(player);
		}
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
		player.setInvisible(true);
		player.setNoGravity(false);
		player.noPhysics = false;
		player.setInvulnerable(true);
		player.setCamera(player);
		player.fallDistance = 0.0F;
		player.startFallFlying();

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
		if (server != null && session.dummyUuid() != null) {
			Entity dummy = findEntity(server, session.originDimension(), session.dummyUuid());
			if (dummy != null) {
				dummy.discard();
			}
		}

		player.setCamera(player);
		if (root != null) {
			root.setPos(player.getX(), player.getY(), player.getZ());
			root.setYRot(player.getYRot());
			root.setXRot(player.getXRot());
			root.setDeltaMovement(Vec3.ZERO);
			root.hurtMarked = true;
		}
		player.setInvisible(session.wasInvisible());
		player.setNoGravity(session.wasNoGravity());
		player.noPhysics = session.wasNoPhysics();
		player.setInvulnerable(session.wasInvulnerable());
		player.fallDistance = 0.0F;

		if (returnToOrigin && server != null) {
			ServerLevel level = server.getLevel(session.originDimension());
			if (level != null) {
				level.getChunkAt(net.minecraft.core.BlockPos.containing(session.originPos()));
				player.teleportTo(
						level,
						session.originPos().x,
						session.originPos().y,
						session.originPos().z,
						ABSOLUTE_TELEPORT,
						session.originYaw(),
						session.originPitch(),
						false
				);
			}
		}

		if (notify) {
			player.sendSystemMessage(Component.literal("Управление дроном завершено."));
		}
	}

	private static void destroyDrone(Entity root, ServerPlayer breaker, boolean dropItem) {
		if (root == null || !root.isAlive() || !(root.level() instanceof ServerLevel level)) {
			return;
		}
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

	private static void syncDroneDisplay(Entity root, float yRot, float xRot) {
		Entity entity = findDroneDisplay(root);
		if (entity instanceof Display.ItemDisplay display) {
			display.setYRot(yRot);
			display.setXRot(xRot);
			display.setPos(root.getX(), root.getY(), root.getZ());
		}
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
		GameProfile profile = createDummyProfile(sourcePlayer, dummy.getUUID());
		PolymerEntityUtils.setPolymerEntity(dummy, new DronePilotOverlay(profile));
		level.addFreshEntity(dummy);
		return dummy;
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
		return new Vec3(anchor.getX() + 0.5D, anchor.getY() + DRONE_SPAWN_Y_OFFSET, anchor.getZ() + 0.5D);
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

	private static Entity findDroneRoot(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension, UUID droneUuid) {
		Entity entity = findEntity(server, dimension, droneUuid);
		return entity != null && entity.getTags().contains(DRONE_ROOT_TAG) ? entity : null;
	}

	private static Entity findEntity(MinecraftServer server, net.minecraft.resources.ResourceKey<Level> dimension, UUID uuid) {
		if (server == null || dimension == null || uuid == null) {
			return null;
		}
		ServerLevel level = server.getLevel(dimension);
		return level == null ? null : level.getEntity(uuid);
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
	}
}
