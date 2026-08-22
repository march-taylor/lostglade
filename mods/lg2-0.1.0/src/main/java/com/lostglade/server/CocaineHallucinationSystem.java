package com.lostglade.server;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CocaineHallucinationSystem {
	private static final String HALLUCINATION_TAG = "lg2_cocaine_hallucination_mob";
	private static final double DEFAULT_MIN_SPAWN_DISTANCE = 4.0D;
	private static final double DEFAULT_MAX_SPAWN_DISTANCE = 14.0D;
	private static final int DEFAULT_MIN_DURATION_SECONDS = 5;
	private static final int DEFAULT_MAX_DURATION_SECONDS = 14;
	private static final double DEFAULT_MIN_CHASING_SPEED = 0.34D;
	private static final double DEFAULT_MAX_CHASING_SPEED = 0.50D;
	private static final double STANDING_WEIGHT = 1.0D;
	private static final double CHASING_WEIGHT = 1.0D;
	private static final double WANDERING_WEIGHT = 1.0D;
	private static final double WANDERING_SPEED = 0.09D;
	private static final double MOTION_BLEND_FACTOR = 0.34D;
	private static final double CONTACT_PADDING = 0.08D;
	private static final double MAX_FALL_SPEED = 0.8D;
	private static final double STEP_UP_JUMP_VELOCITY = 0.42D;
	private static final double STEP_UP_CLEARANCE = 1.05D;
	private static final double GROUND_CHECK_DISTANCE = 0.08D;
	private static final int MIN_WANDER_CHANGE_TICKS = 16;
	private static final int MAX_WANDER_CHANGE_TICKS = 48;
	private static final int MAX_MOB_SPAWN_ATTEMPTS = 10;
	private static final int MAX_POSITION_ATTEMPTS = 20;
	private static final List<Integer> SPAWN_Y_OFFSETS = List.of(2, 1, 0, -1, -2, -3, -4, -5, 3, 4);
	private static final Set<EntityType<?>> EXCLUDED_MOBS = Set.of(
			EntityType.ENDER_DRAGON,
			EntityType.WITHER,
			EntityType.ELDER_GUARDIAN,
			EntityType.WARDEN,
			EntityType.GIANT
	);
	private static final Map<UUID, ActiveHallucinationState> ACTIVE_STATES = new HashMap<>();
	private static final Map<Class<?>, EntityIdAccessor> ENTITY_ID_ACCESSORS = new java.util.concurrent.ConcurrentHashMap<>();
	private static final EntityIdAccessor MISSING_ENTITY_ID_ACCESSOR = packet -> Integer.MIN_VALUE;
	private static List<EntityType<?>> mobPool;

	private CocaineHallucinationSystem() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register(CocaineHallucinationSystem::onAttackEntity);
	}

	public static boolean spawn(ServerPlayer player, RandomSource random) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}

		List<EntityType<?>> pool = getMobPool(level);
		if (pool.isEmpty()) {
			return false;
		}

		RandomSource safeRandom = random != null ? random : level.random;
		int durationSeconds = sampleRangeInt(safeRandom, DEFAULT_MIN_DURATION_SECONDS, DEFAULT_MAX_DURATION_SECONDS);
		long nowTick = level.getGameTime();
		long durationTicks = Math.max(20L, durationSeconds * 20L);
		MovementType movementType = pickMovementType(safeRandom);
		double chasingSpeed = sampleRange(safeRandom, DEFAULT_MIN_CHASING_SPEED, DEFAULT_MAX_CHASING_SPEED);
		EntityType<?> selectedMobType = pool.get(safeRandom.nextInt(pool.size()));

		for (int attempt = 0; attempt < MAX_MOB_SPAWN_ATTEMPTS; attempt++) {
			Mob mob = createMobFromType(level, selectedMobType);
			if (mob == null) {
				continue;
			}

			configureMob(mob);
			Vec3 spawnPos = findSpawnPosition(level, player, mob, safeRandom, DEFAULT_MIN_SPAWN_DISTANCE, DEFAULT_MAX_SPAWN_DISTANCE);
			if (spawnPos == null) {
				mob.discard();
				continue;
			}

			mob.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
			mob.setYRot(safeRandom.nextFloat() * 360.0F);
			if (!level.noCollision(mob) || level.containsAnyLiquid(mob.getBoundingBox())) {
				mob.discard();
				continue;
			}

			ActiveHallucinationState state = new ActiveHallucinationState(
					mob.getUUID(),
					level.dimension(),
					player.getUUID(),
					movementType,
					nowTick + durationTicks,
					nowTick + sampleRangeInt(safeRandom, MIN_WANDER_CHANGE_TICKS, MAX_WANDER_CHANGE_TICKS),
					randomHorizontalDirection(safeRandom),
					Vec3.ZERO,
					chasingSpeed
			);
			ACTIVE_STATES.put(mob.getUUID(), state);
			if (!level.addFreshEntity(mob)) {
				ACTIVE_STATES.remove(mob.getUUID());
				mob.discard();
				continue;
			}

			applyMovement(mob, player, state, nowTick);
			return true;
		}

		return false;
	}

	public static void tick(MinecraftServer server) {
		if (server == null || ACTIVE_STATES.isEmpty()) {
			cleanupOrphanedHallucinations(server);
			return;
		}

		long nowTick = server.overworld().getGameTime();
		for (Map.Entry<UUID, ActiveHallucinationState> entry : new ArrayList<>(ACTIVE_STATES.entrySet())) {
			ActiveHallucinationState state = entry.getValue();
			Entity rawEntity = findEntity(server, state.dimension, state.entityUuid);
			if (!(rawEntity instanceof Mob mob) || !isManagedHallucination(mob)) {
				ACTIVE_STATES.remove(entry.getKey(), state);
				continue;
			}
			if (ServerBackroomsSystem.isInBackrooms(mob)) {
				discardEntityOnly(mob, true, false);
				ACTIVE_STATES.remove(entry.getKey(), state);
				continue;
			}

			ServerPlayer targetPlayer = server.getPlayerList().getPlayer(state.targetPlayerUuid);
			if (targetPlayer == null
					|| !targetPlayer.isAlive()
					|| targetPlayer.isSpectator()
					|| ServerBackroomsSystem.isInBackrooms(targetPlayer)
					|| targetPlayer.level() != mob.level()) {
				discardEntityOnly(mob, true, false);
				ACTIVE_STATES.remove(entry.getKey(), state);
				continue;
			}

			if (mob.level() instanceof ServerLevel level) {
				state.dimension = level.dimension();
			}

			boolean touchedPlayer = touchesTargetPlayer(mob, targetPlayer);
			if (nowTick >= state.endTick || touchedPlayer) {
				discardEntityOnly(mob, true, touchedPlayer);
				ACTIVE_STATES.remove(entry.getKey(), state);
				continue;
			}

			applyMovement(mob, targetPlayer, state, nowTick);
		}

		cleanupOrphanedHallucinations(server);
	}

	public static boolean handleIncomingDamage(Entity entity) {
		if (!isManagedHallucination(entity)) {
			return false;
		}

		discardEntityOnly(entity, true, true);
		return true;
	}

	public static Packet<?> filterOutgoingPacket(ServerPlayer viewer, Packet<?> packet) {
		if (viewer == null || packet == null) {
			return packet;
		}

		if (packet instanceof ClientboundBundlePacket bundlePacket) {
			List<Packet<? super ClientGamePacketListener>> filteredPackets = new ArrayList<>();
			boolean changed = false;
			for (Packet<?> subPacket : bundlePacket.subPackets()) {
				Packet<?> filteredSubPacket = filterOutgoingPacket(viewer, subPacket);
				if (filteredSubPacket == null) {
					changed = true;
					continue;
				}
				if (filteredSubPacket != subPacket) {
					changed = true;
				}
				filteredPackets.add(castGamePacket(filteredSubPacket));
			}

			if (!changed) {
				return packet;
			}
			if (filteredPackets.isEmpty()) {
				return null;
			}
			return new ClientboundBundlePacket(filteredPackets);
		}

		if (packet instanceof ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
			int[] filteredIds = filterRemovedEntityIds(viewer, removeEntitiesPacket);
			if (filteredIds == null) {
				return packet;
			}
			if (filteredIds.length == 0) {
				return null;
			}
			return new ClientboundRemoveEntitiesPacket(filteredIds);
		}

		if (packet instanceof ClientboundAddEntityPacket
				|| packet instanceof ClientboundAnimatePacket
				|| packet instanceof ClientboundEntityEventPacket
				|| packet instanceof ClientboundSetEntityDataPacket
				|| packet instanceof ClientboundSetEntityMotionPacket
				|| packet instanceof ClientboundSetEquipmentPacket
				|| packet instanceof ClientboundSoundEntityPacket
				|| packet instanceof ClientboundUpdateAttributesPacket
				|| packet instanceof ClientboundEntityPositionSyncPacket
				|| packet instanceof ClientboundMoveEntityPacket
				|| packet instanceof ClientboundRotateHeadPacket
				|| packet instanceof ClientboundTeleportEntityPacket
				|| packet instanceof ClientboundSoundPacket) {
			int entityId = extractEntityId(packet);
			if (entityId != Integer.MIN_VALUE) {
				Entity entity = ((ServerLevel) viewer.level()).getEntity(entityId);
				if (shouldHideEntityFromViewer(viewer, entity)) {
					return null;
				}
			}
		}

		return packet;
	}

	private static List<EntityType<?>> getMobPool(ServerLevel level) {
		if (mobPool != null) {
			return mobPool;
		}

		List<EntityType<?>> collected = new ArrayList<>();
		for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
			if (EXCLUDED_MOBS.contains(entityType)) {
				continue;
			}

			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
			if (id == null || !"minecraft".equals(id.getNamespace())) {
				continue;
			}

			Entity entity = entityType.create(level, EntitySpawnReason.TRIGGERED);
			if (entity instanceof Mob mob) {
				collected.add(entityType);
				mob.discard();
				continue;
			}
			if (entity != null) {
				entity.discard();
			}
		}

		mobPool = collected;
		return mobPool;
	}

	private static Mob createMobFromType(ServerLevel level, EntityType<?> entityType) {
		if (entityType == null) {
			return null;
		}

		Entity entity = entityType.create(level, EntitySpawnReason.TRIGGERED);
		if (entity instanceof Mob mob) {
			return mob;
		}
		if (entity != null) {
			entity.discard();
		}
		return null;
	}

	private static void configureMob(Mob mob) {
		mob.setNoAi(true);
		mob.setSilent(true);
		mob.setInvulnerable(true);
		mob.setCanPickUpLoot(false);
		mob.setTarget(null);
		mob.addTag(HALLUCINATION_TAG);
	}

	private static Vec3 findSpawnPosition(
			ServerLevel level,
			ServerPlayer player,
			Mob mob,
			RandomSource random,
			double minDistance,
			double maxDistance
	) {
		int minBuildY = level.getMinY() + 1;
		int baseY = (int) Math.floor(player.getY());

		for (int attempt = 0; attempt < MAX_POSITION_ATTEMPTS; attempt++) {
			double angle = sampleRange(random, 0.0D, Math.PI * 2.0D);
			double distance = sampleRange(random, minDistance, maxDistance);
			double x = player.getX() + (Math.cos(angle) * distance);
			double z = player.getZ() + (Math.sin(angle) * distance);

			for (int yOffset : SPAWN_Y_OFFSETS) {
				int blockY = Math.max(minBuildY, baseY + yOffset);
				Double supportTopY = getSupportTopY(level, mob, BlockPos.containing(x, blockY - 1, z));
				if (supportTopY == null) {
					continue;
				}
				double y = supportTopY;
				mob.setPos(x, y, z);
				if (!level.noCollision(mob) || level.containsAnyLiquid(mob.getBoundingBox())) {
					continue;
				}
				return new Vec3(x, y, z);
			}
		}

		return null;
	}

	private static Double getSupportTopY(ServerLevel level, Entity entity, BlockPos supportPos) {
		BlockState groundState = level.getBlockState(supportPos);
		CollisionContext context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
		VoxelShape shape = groundState.getCollisionShape(level, supportPos, context);
		if (shape.isEmpty()) {
			return null;
		}

		double top = shape.max(Direction.Axis.Y);
		if (top <= 1.0E-6D) {
			return null;
		}

		return supportPos.getY() + top;
	}

	private static void applyMovement(Mob mob, ServerPlayer targetPlayer, ActiveHallucinationState state, long nowTick) {
		if (mob == null || targetPlayer == null || state == null) {
			return;
		}

		if (state.movementType == MovementType.WANDERING && nowTick >= state.nextWanderChangeTick) {
			state.wanderDirection = randomHorizontalDirection(mob.getRandom());
			state.nextWanderChangeTick = nowTick + sampleRangeInt(mob.getRandom(), MIN_WANDER_CHANGE_TICKS, MAX_WANDER_CHANGE_TICKS);
		}

		Vec3 desiredHorizontal = switch (state.movementType) {
			case STANDING -> Vec3.ZERO;
			case CHASING -> getChasingVelocity(mob, targetPlayer, state.chasingSpeed);
			case WANDERING -> state.wanderDirection.scale(WANDERING_SPEED);
		};

		double nextX = state.currentMotion.x + ((desiredHorizontal.x - state.currentMotion.x) * MOTION_BLEND_FACTOR);
		double nextZ = state.currentMotion.z + ((desiredHorizontal.z - state.currentMotion.z) * MOTION_BLEND_FACTOR);
		double currentVerticalMotion = mob.getDeltaMovement().y;
		double gravity = mob.isNoGravity() ? 0.0D : mob.getGravity();
		double nextVerticalMotion = mob.onGround() && currentVerticalMotion >= 0.0D
				? 0.0D
				: Math.max(currentVerticalMotion - gravity, -MAX_FALL_SPEED);
		if (shouldAttemptStepUp(mob, new Vec3(nextX, 0.0D, nextZ))) {
			nextVerticalMotion = Math.max(nextVerticalMotion, STEP_UP_JUMP_VELOCITY);
		}
		Vec3 requestedMotion = new Vec3(nextX, nextVerticalMotion, nextZ);
		Vec3 appliedMotion = moveWithPhysics(mob, requestedMotion);
		state.currentMotion = new Vec3(appliedMotion.x, 0.0D, appliedMotion.z);
		mob.setDeltaMovement(appliedMotion.x, mob.onGround() ? 0.0D : appliedMotion.y, appliedMotion.z);
		mob.resetFallDistance();
		mob.hurtMarked = true;

		if (desiredHorizontal.lengthSqr() > 1.0E-6D) {
			float yaw = (float) Math.toDegrees(Math.atan2(desiredHorizontal.z, desiredHorizontal.x)) - 90.0F;
			mob.setYRot(yaw);
			mob.setYBodyRot(yaw);
			mob.setYHeadRot(yaw);
		}
	}

	private static Vec3 moveWithPhysics(Mob mob, Vec3 motion) {
		if (mob == null || motion.lengthSqr() <= 1.0E-8D) {
			return Vec3.ZERO;
		}

		Vec3 startPos = mob.position();
		mob.move(MoverType.SELF, motion);
		return mob.position().subtract(startPos);
	}

	private static boolean shouldAttemptStepUp(Mob mob, Vec3 horizontalMotion) {
		if (mob == null || horizontalMotion.horizontalDistanceSqr() <= 1.0E-8D) {
			return false;
		}
		if (!(mob.onGround() || isNearGround(mob))) {
			return false;
		}

		AABB boundingBox = mob.getBoundingBox();
		Level level = mob.level();
		AABB horizontalBox = boundingBox.move(horizontalMotion.x, 0.0D, horizontalMotion.z);
		if (level.noCollision(mob, horizontalBox)) {
			return false;
		}

		AABB steppedBox = boundingBox.move(horizontalMotion.x, STEP_UP_CLEARANCE, horizontalMotion.z);
		return level.noCollision(mob, steppedBox);
	}

	private static boolean isNearGround(Mob mob) {
		if (mob == null) {
			return false;
		}
		return !mob.level().noCollision(mob, mob.getBoundingBox().move(0.0D, -GROUND_CHECK_DISTANCE, 0.0D));
	}

	private static Vec3 getChasingVelocity(Mob mob, ServerPlayer targetPlayer, double chasingSpeed) {
		Vec3 delta = targetPlayer.position().subtract(mob.position());
		Vec3 horizontal = new Vec3(delta.x, 0.0D, delta.z);
		if (horizontal.lengthSqr() <= 1.0E-6D) {
			return Vec3.ZERO;
		}
		return horizontal.normalize().scale(chasingSpeed);
	}

	private static boolean touchesTargetPlayer(Mob mob, ServerPlayer targetPlayer) {
		return mob != null
				&& targetPlayer != null
				&& targetPlayer.isAlive()
				&& targetPlayer.getBoundingBox().intersects(mob.getBoundingBox().inflate(CONTACT_PADDING));
	}

	private static boolean isManagedHallucination(Entity entity) {
		return entity != null
				&& entity.isAlive()
				&& (ACTIVE_STATES.containsKey(entity.getUUID()) || entity.getTags().contains(HALLUCINATION_TAG));
	}

	private static void discardEntityOnly(Entity entity, boolean particles, boolean playDespawnSound) {
		if (entity == null) {
			return;
		}

		ActiveHallucinationState state = ACTIVE_STATES.remove(entity.getUUID());
		if (entity.level() instanceof ServerLevel level && entity.isAlive() && !entity.isRemoved() && state != null) {
			if (particles) {
				spawnDespawnParticles(level, entity, state.targetPlayerUuid);
			}
			if (playDespawnSound) {
				playContactDespawnSound(level, entity, state.targetPlayerUuid);
			}
		}
		if (!entity.isRemoved()) {
			entity.discard();
		}
	}

	private static InteractionResult onAttackEntity(
			Player player,
			Level world,
			InteractionHand hand,
			Entity entity,
			EntityHitResult hitResult
	) {
		if (world.isClientSide() || !(player instanceof ServerPlayer)) {
			return InteractionResult.PASS;
		}

		return handleIncomingDamage(entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	private static void spawnDespawnParticles(ServerLevel level, Entity entity, UUID targetPlayerId) {
		ServerPlayer targetPlayer = targetPlayerId == null ? null : level.getServer().getPlayerList().getPlayer(targetPlayerId);
		if (targetPlayer == null || targetPlayer.level() != level) {
			return;
		}

		double centerX = entity.getX();
		double centerY = entity.getY() + (entity.getBbHeight() * 0.5D);
		double centerZ = entity.getZ();
		double spreadX = Math.max(0.15D, entity.getBbWidth() * 0.35D);
		double spreadY = Math.max(0.15D, entity.getBbHeight() * 0.25D);
		double spreadZ = Math.max(0.15D, entity.getBbWidth() * 0.35D);

		level.sendParticles(targetPlayer, ParticleTypes.SMOKE, false, false, centerX, centerY, centerZ, 18, spreadX, spreadY, spreadZ, 0.01D);
		level.sendParticles(targetPlayer, ParticleTypes.ASH, false, false, centerX, centerY, centerZ, 12, spreadX, spreadY, spreadZ, 0.01D);
	}

	private static void playContactDespawnSound(ServerLevel level, Entity entity, UUID targetPlayerId) {
		ServerPlayer targetPlayer = targetPlayerId == null ? null : level.getServer().getPlayerList().getPlayer(targetPlayerId);
		if (targetPlayer == null || targetPlayer.level() != level) {
			return;
		}

		targetPlayer.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.FIRE_EXTINGUISH),
				SoundSource.HOSTILE,
				entity.getX(),
				entity.getY() + (entity.getBbHeight() * 0.5D),
				entity.getZ(),
				0.55F,
				1.15F,
				level.getRandom().nextLong()
		));
	}

	private static Entity findEntity(MinecraftServer server, ResourceKey<Level> dimension, UUID entityUuid) {
		if (server == null || entityUuid == null) {
			return null;
		}

		if (dimension != null) {
			ServerLevel preferredLevel = server.getLevel(dimension);
			if (preferredLevel != null) {
				Entity found = preferredLevel.getEntity(entityUuid);
				if (found != null) {
					return found;
				}
			}
		}

		for (ServerLevel level : server.getAllLevels()) {
			Entity found = level.getEntity(entityUuid);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	private static boolean shouldHideEntityFromViewer(ServerPlayer viewer, Entity entity) {
		if (viewer == null || entity == null || !isManagedHallucination(entity)) {
			return false;
		}

		UUID targetPlayerUuid = resolveTargetPlayerUuid(entity);
		return targetPlayerUuid != null && !viewer.getUUID().equals(targetPlayerUuid);
	}

	private static void cleanupOrphanedHallucinations(MinecraftServer server) {
		if (server == null) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!entity.getTags().contains(HALLUCINATION_TAG) || ACTIVE_STATES.containsKey(entity.getUUID())) {
					continue;
				}

				UUID targetPlayerUuid = resolveTargetPlayerUuid(entity);
				ServerPlayer targetPlayer = targetPlayerUuid == null ? null : server.getPlayerList().getPlayer(targetPlayerUuid);
				boolean touchedPlayer = entity instanceof Mob mob && targetPlayer != null && touchesTargetPlayer(mob, targetPlayer);
				discardEntityOnly(entity, true, touchedPlayer);
			}
		}
	}

	private static UUID resolveTargetPlayerUuid(Entity entity) {
		if (entity == null) {
			return null;
		}

		ActiveHallucinationState state = ACTIVE_STATES.get(entity.getUUID());
		if (state != null) {
			return state.targetPlayerUuid;
		}

		return null;
	}

	private static int[] filterRemovedEntityIds(ServerPlayer viewer, ClientboundRemoveEntitiesPacket removeEntitiesPacket) {
		Object entityIds = invokeNoArgs(removeEntitiesPacket, "entityIds");
		if (entityIds == null) {
			entityIds = readFieldValue(removeEntitiesPacket, "entityIds");
		}
		if (!(entityIds instanceof Iterable<?> iterable)) {
			return null;
		}

		List<Integer> keptEntityIds = new ArrayList<>();
		boolean changed = false;
		ServerLevel level = (ServerLevel) viewer.level();
		for (Object entry : iterable) {
			if (!(entry instanceof Number number)) {
				continue;
			}

			int entityId = number.intValue();
			Entity entity = level.getEntity(entityId);
			if (shouldHideEntityFromViewer(viewer, entity)) {
				changed = true;
				continue;
			}
			keptEntityIds.add(entityId);
		}

		if (!changed) {
			return null;
		}

		int[] result = new int[keptEntityIds.size()];
		for (int i = 0; i < keptEntityIds.size(); i++) {
			result[i] = keptEntityIds.get(i);
		}
		return result;
	}

	private static int extractEntityId(Packet<?> packet) {
		if (packet == null) return Integer.MIN_VALUE;
		return ENTITY_ID_ACCESSORS.computeIfAbsent(packet.getClass(), CocaineHallucinationSystem::createEntityIdAccessor)
				.read(packet);
	}

	private static EntityIdAccessor createEntityIdAccessor(Class<?> packetType) {
		for (String fieldName : List.of("id", "entityId", "entity")) {
			Class<?> type = packetType;
			while (type != null) {
				try {
					java.lang.reflect.Field field = type.getDeclaredField(fieldName);
					field.setAccessible(true);
					return packet -> numberValue(() -> field.get(packet));
				} catch (NoSuchFieldException ignored) {
					type = type.getSuperclass();
				} catch (RuntimeException ignored) {
					break;
				}
			}
		}
		for (String methodName : List.of("getId", "getEntityId", "getEntity")) {
			try {
				java.lang.reflect.Method method = packetType.getMethod(methodName);
				return packet -> numberValue(() -> method.invoke(packet));
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return MISSING_ENTITY_ID_ACCESSOR;
	}

	private static int numberValue(ReflectiveValueReader reader) {
		try {
			Object value = reader.read();
			return value instanceof Number number ? number.intValue() : Integer.MIN_VALUE;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return Integer.MIN_VALUE;
		}
	}

	private static Object invokeNoArgs(Object target, String... methodNames) {
		if (target == null) {
			return null;
		}

		for (String methodName : methodNames) {
			try {
				return target.getClass().getMethod(methodName).invoke(target);
			} catch (ReflectiveOperationException ignored) {
			}
		}
		return null;
	}

	private static Object readFieldValue(Object target, String... fieldNames) {
		if (target == null) {
			return null;
		}

		for (String fieldName : fieldNames) {
			Class<?> type = target.getClass();
			while (type != null) {
				try {
					java.lang.reflect.Field field = type.getDeclaredField(fieldName);
					field.setAccessible(true);
					return field.get(target);
				} catch (ReflectiveOperationException ignored) {
					type = type.getSuperclass();
				}
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static Packet<? super ClientGamePacketListener> castGamePacket(Packet<?> packet) {
		return (Packet<? super ClientGamePacketListener>) packet;
	}

	@FunctionalInterface
	private interface EntityIdAccessor {
		int read(Packet<?> packet);
	}

	@FunctionalInterface
	private interface ReflectiveValueReader {
		Object read() throws ReflectiveOperationException;
	}

	private static MovementType pickMovementType(RandomSource random) {
		double totalWeight = STANDING_WEIGHT + CHASING_WEIGHT + WANDERING_WEIGHT;
		double roll = random.nextDouble() * totalWeight;
		if (roll < STANDING_WEIGHT) {
			return MovementType.STANDING;
		}
		roll -= STANDING_WEIGHT;
		if (roll < CHASING_WEIGHT) {
			return MovementType.CHASING;
		}
		return MovementType.WANDERING;
	}

	private static Vec3 randomHorizontalDirection(RandomSource random) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double x = sampleRange(random, -1.0D, 1.0D);
			double z = sampleRange(random, -1.0D, 1.0D);
			Vec3 direction = new Vec3(x, 0.0D, z);
			if (direction.lengthSqr() > 1.0E-6D) {
				return direction.normalize();
			}
		}
		return new Vec3(1.0D, 0.0D, 0.0D);
	}

	private static double sampleRange(RandomSource random, double min, double max) {
		if (max <= min) {
			return min;
		}
		return min + (random.nextDouble() * (max - min));
	}

	private static int sampleRangeInt(RandomSource random, int min, int max) {
		if (max <= min) {
			return min;
		}
		return min + random.nextInt(max - min + 1);
	}

	private enum MovementType {
		STANDING,
		CHASING,
		WANDERING
	}

	private static final class ActiveHallucinationState {
		private final UUID entityUuid;
		private ResourceKey<Level> dimension;
		private final UUID targetPlayerUuid;
		private final MovementType movementType;
		private final long endTick;
		private long nextWanderChangeTick;
		private Vec3 wanderDirection;
		private Vec3 currentMotion;
		private final double chasingSpeed;

		private ActiveHallucinationState(
				UUID entityUuid,
				ResourceKey<Level> dimension,
				UUID targetPlayerUuid,
				MovementType movementType,
				long endTick,
				long nextWanderChangeTick,
				Vec3 wanderDirection,
				Vec3 currentMotion,
				double chasingSpeed
		) {
			this.entityUuid = entityUuid;
			this.dimension = dimension;
			this.targetPlayerUuid = targetPlayerUuid;
			this.movementType = movementType;
			this.endTick = endTick;
			this.nextWanderChangeTick = nextWanderChangeTick;
			this.wanderDirection = wanderDirection;
			this.currentMotion = currentMotion;
			this.chasingSpeed = chasingSpeed;
		}
	}
}
