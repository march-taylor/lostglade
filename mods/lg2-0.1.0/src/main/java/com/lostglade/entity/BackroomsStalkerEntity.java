package com.lostglade.entity;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.lostglade.Lg2;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
import com.lostglade.server.ServerBackroomsSystem;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.loader.api.FabricLoader;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.mineskin.MineskinService;
import net.lionarius.skinrestorer.skin.SkinStorage;
import net.lionarius.skinrestorer.skin.SkinValue;
import net.lionarius.skinrestorer.skin.SkinVariant;
import net.lionarius.skinrestorer.util.PlayerUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import xyz.nucleoid.packettweaker.PacketContext;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BackroomsStalkerEntity extends Monster {
	private static final EntityDimensions PLAYER_SIZED_DIMENSIONS = EntityDimensions.fixed(0.6F, 1.8F);
	private static final byte PLAYER_SKIN_PARTS_WITHOUT_CAPE = (byte) 0x7E;
	private static final double WALK_SPEED = 0.23D;
	private static final double CHASE_SPEED_MODIFIER = 1.65D;
	private static final double ATTACK_RANGE = 2.2D;
	private static final double ATTACK_RANGE_SQR = ATTACK_RANGE * ATTACK_RANGE;
	private static final double TARGET_ACQUIRE_DISTANCE_SQR = 96.0D * 96.0D;
	private static final int FORGET_TARGET_AFTER_TICKS = 100;
	private static final int CHASE_LAST_SEEN_DIRECT_MOVE_TICKS = 20;
	private static final int ATTACK_COOLDOWN_TICKS = 20;
	private static final int TARGET_SCAN_INTERVAL_TICKS = 1;
	private static final int TARGET_IDLE_SCAN_INTERVAL_TICKS = 2;
	private static final int CHASE_REPATH_TICKS = 10;
	private static final int CHASE_STUCK_REPATH_INTERVAL_TICKS = 3;
	private static final int CHASE_STUCK_REPATH_AFTER_TICKS = 12;
	private static final double CHASE_STUCK_MOVE_THRESHOLD_SQR = 0.01D;
	private static final int CHASE_DETOUR_ATTEMPTS = 6;
	private static final int CHASE_DETOUR_MIN_DISTANCE = 8;
	private static final int CHASE_DETOUR_MAX_DISTANCE = 36;
	private static final int CHASE_DETOUR_Y_SEARCH = 1;
	private static final int CHASE_DETOUR_COOLDOWN_TICKS = 8;
	private static final float CHASE_DIRECT_PATH_DIST_TOLERANCE = 1.5F;
	private static final float CHASE_MAX_VISITED_NODES_MULTIPLIER = 7.0F;
	private static final float WANDER_MAX_VISITED_NODES_MULTIPLIER = 3.5F;
	private static final double STEP_JUMP_MIN_DELTA_Y = 0.45D;
	private static final double STEP_JUMP_MAX_DELTA_Y = 1.25D;
	private static final int SIGHT_CACHE_TTL_TICKS = 6;
	private static final int SIGHT_CACHE_MAX_ENTRIES = 32;
	private static final double SKIN_SCREAMER_TRIGGER_RADIUS_SQR = 32.0D * 32.0D;
	private static final double SKIN_SCREAMER_MIN_LOOK_DOT = 0.8660254D; // ~30 degrees from center
	private static final float SKIN_SCREAMER_BLINK_CHANCE = 0.25F;
	private static final int SKIN_SCREAMER_MIN_TOGGLES = 10;
	private static final int SKIN_SCREAMER_MAX_TOGGLES = 18;
	private static final int SKIN_SCREAMER_MASK_PHASE_MIN_TICKS = 1;
	private static final int SKIN_SCREAMER_MASK_PHASE_MAX_TICKS = 3;
	private static final int SKIN_SCREAMER_GAP_PHASE_MIN_TICKS = 1;
	private static final int SKIN_SCREAMER_GAP_PHASE_MAX_TICKS = 5;
	private static final int SKIN_SCREAMER_LOOK_CHECK_INTERVAL_TICKS = 8;
	private static final int SKIN_SCREAMER_MASK_PREWARM_INTERVAL_TICKS = 40;
	private static final int SKIN_SCREAMER_IDLE_TICK_INTERVAL = 2;
	private static final long MASKED_SKIN_RETRY_COOLDOWN_MS = 15_000L;
	private static final int MIN_WANDER_DISTANCE = 10;
	private static final int MAX_WANDER_DISTANCE = 30;
	private static final int MAX_WANDER_ATTEMPTS = 14;
	private static final int WANDER_REPATH_TICKS = 20;
	private static final int WANDER_SEARCH_COOLDOWN_TICKS = 12;
	private static final int MAX_WANDER_PATH_CHECKS = 2;
	private static final double TRANSPARENT_SIGHT_STEP_BLOCKS = 0.35D;
	private static final int MAX_VERTICAL_SEARCH = 3;
	private static final int STUCK_REPATH_TICKS = 15;
	private static final float WANDER_HEAD_PITCH = 45.0F;
	private static final String STALKER_TAG = "lg2_backrooms_stalker";
	private static final String STALKER_MASK_RESOURCE_PATH = "/stalker/skin_mask.png";
	private static final String STALKER_MASK_CACHE_VERSION = "v7-head-layer-blend-then-mask";
	private static final java.nio.file.Path STALKER_MASK_FALLBACK_PATH = java.nio.file.Path.of("/home/mart/Pictures/Edited/skin1.png");
	private static final java.nio.file.Path STALKER_MASKED_SKIN_CACHE_DIR = FabricLoader.getInstance()
			.getGameDir()
			.resolve("cache")
			.resolve("lg2")
			.resolve("stalker_masked_skins");
	private static final Map<UUID, Map<UUID, Boolean>> MASKED_SKIN_VIEWER_STATE = new ConcurrentHashMap<>();
	private static final Map<String, Property> MASKED_SKIN_BY_SOURCE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Long> MASKED_SKIN_RETRY_AT_MS = new ConcurrentHashMap<>();
	private static final Set<String> MASKED_SKIN_BUILD_IN_FLIGHT = ConcurrentHashMap.newKeySet();
	private static volatile BufferedImage STALKER_MASK_IMAGE;
	private static volatile boolean MINESKIN_RELOADED_LAZILY = false;

	private UUID trackedTargetUuid;
	private long lastSeenTargetTick = Long.MIN_VALUE;
	private long nextAttackTick = 0L;
	private long nextTargetScanTick = 0L;
	private long nextChaseRepathTick = 0L;
	private long nextChaseDetourTick = 0L;
	private long nextWanderRefreshTick = 0L;
	private long nextWanderSearchTick = 0L;
	private int chaseStuckTicks = 0;
	private Vec3 lastChasePos = Vec3.ZERO;
	private Vec3 lastChasePathTargetPos = Vec3.ZERO;
	private Vec3 lastSeenTargetPos = Vec3.ZERO;
	private int stationaryTicks = 0;
	private Vec3 lastTickPos = Vec3.ZERO;
	private Vec3 chaseDetourTarget;
	private Vec3 wanderTarget;
	private final Map<UUID, SightCacheEntry> sightCache = new HashMap<>();
	private final Map<UUID, ViewerSkinScreamerState> viewerSkinScreamerStates = new HashMap<>();
	private boolean removalPacketsSent = false;
	private boolean chasingTarget = false;

	public BackroomsStalkerEntity(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		this.xpReward = 0;
		this.setPersistenceRequired();
		this.setInvulnerable(true);
		this.setSilent(true);
		this.setCanPickUpLoot(false);
		this.addTag(STALKER_TAG);
		this.refreshDimensions();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
				.add(Attributes.FOLLOW_RANGE, 1024.0D)
				.add(Attributes.ATTACK_DAMAGE, 0.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
	}

	public static boolean isStalker(Entity entity) {
		return entity instanceof BackroomsStalkerEntity || (entity != null && entity.getTags().contains(STALKER_TAG));
	}

	public static BackroomsStalkerEntity create(Level level) {
		BackroomsStalkerEntity stalker = new BackroomsStalkerEntity(EntityType.HUSK, level);
		stalker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
		stalker.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(WALK_SPEED);
		stalker.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(1024.0D);
		stalker.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
		stalker.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
		stalker.setHealth(stalker.getMaxHealth());
		stalker.refreshDimensions();
		stalker.attachPolymerAppearance();
		return stalker;
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return PLAYER_SIZED_DIMENSIONS;
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		GroundPathNavigation navigation = new GroundPathNavigation(this, level);
		navigation.setCanOpenDoors(true);
		navigation.setMaxVisitedNodesMultiplier(CHASE_MAX_VISITED_NODES_MULTIPLIER);
		return navigation;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		sendRemovalPackets();
		super.remove(reason);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			tryJumpStepUp();
			if (!this.chasingTarget) {
				setHeadPitch(WANDER_HEAD_PITCH);
			}
		}
	}

	@Override
	public float maxUpStep() {
		return 1.0F;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (!ServerBackroomsSystem.isBackrooms(level)) {
			this.discard();
			return;
		}

		long nowTick = level.getGameTime();
		boolean wasChasingTarget = this.chasingTarget;
		ServerPlayer target = resolveTargetForTick(level, nowTick);
		if (target != null) {
			this.chasingTarget = true;
			if (!wasChasingTarget) {
				this.nextChaseRepathTick = 0L;
				this.chaseDetourTarget = null;
				this.chaseStuckTicks = 0;
				this.lastChasePos = this.position();
			}
			updateChaseMovement(level, nowTick, target);
			updateTouchDamage(level, nowTick, target);
		} else {
			this.chasingTarget = false;
			updateWanderMovement(level, nowTick);
		}

		tickSkinScreamer(level, nowTick);
	}

	private ServerPlayer resolveTargetForTick(ServerLevel level, long nowTick) {
		if (this.trackedTargetUuid == null) {
			if (nowTick < this.nextTargetScanTick) {
				return null;
			}
			this.nextTargetScanTick = nowTick + TARGET_IDLE_SCAN_INTERVAL_TICKS;
			return updateTrackedTarget(level, nowTick);
		}

		if (nowTick >= this.nextTargetScanTick) {
			this.nextTargetScanTick = nowTick + TARGET_SCAN_INTERVAL_TICKS;
			return updateTrackedTarget(level, nowTick);
		}

		ServerPlayer currentTarget = resolveTrackedTarget(level, nowTick);
		if (currentTarget == null) {
			clearTrackedTarget();
			return null;
		}
		return currentTarget;
	}

	public void discardFromSystem() {
		sendRemovalPackets();
		this.discard();
	}

	public void attachPolymerAppearance() {
		PolymerEntityUtils.setPolymerEntity(this, new StalkerPlayerOverlay(this.getUUID()));
	}

	public boolean isChasingTarget() {
		return this.chasingTarget;
	}

	public boolean isTrackingPlayer(ServerPlayer player) {
		return player != null && this.trackedTargetUuid != null && this.trackedTargetUuid.equals(player.getUUID());
	}

	private ServerPlayer updateTrackedTarget(ServerLevel level, long nowTick) {
		ServerPlayer currentTarget = resolveTrackedTarget(level, nowTick);
		if (currentTarget != null) {
			return currentTarget;
		}

		ServerPlayer nearestTarget = findNearestTarget(level, nowTick);
		if (nearestTarget != null) {
			this.trackedTargetUuid = nearestTarget.getUUID();
			this.lastSeenTargetTick = nowTick;
			this.lastSeenTargetPos = nearestTarget.position();
			return nearestTarget;
		}

		clearTrackedTarget();
		return null;
	}

	private ServerPlayer resolveTrackedTarget(ServerLevel level, long nowTick) {
		if (this.trackedTargetUuid == null) {
			return null;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.trackedTargetUuid);
		if (!isEligibleTarget(player)) {
			clearTrackedTarget();
			return null;
		}

		if (hasTransparentAwareSight(player, nowTick)) {
			this.lastSeenTargetTick = nowTick;
			this.lastSeenTargetPos = player.position();
			return player;
		}

		if (this.lastSeenTargetTick != Long.MIN_VALUE
				&& nowTick - this.lastSeenTargetTick <= FORGET_TARGET_AFTER_TICKS) {
			return player;
		}

		clearTrackedTarget();
		return null;
	}

	private ServerPlayer findNearestTarget(ServerLevel level, long nowTick) {
		ServerPlayer nearest = null;
		double nearestDistanceSqr = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!isEligibleTarget(player)) {
				continue;
			}

			double distanceSqr = this.distanceToSqr(player);
			if (distanceSqr > TARGET_ACQUIRE_DISTANCE_SQR || distanceSqr >= nearestDistanceSqr) {
				continue;
			}
			if (!hasTransparentAwareSight(player, nowTick)) {
				continue;
			}

			nearest = player;
			nearestDistanceSqr = distanceSqr;
		}

		return nearest;
	}

	private boolean hasTransparentAwareSight(ServerPlayer player, long nowTick) {
		if (!isEligibleTarget(player) || !(this.level() instanceof ServerLevel level)) {
			return false;
		}

		SightCacheEntry cached = this.sightCache.get(player.getUUID());
		if (cached != null && nowTick - cached.tick <= SIGHT_CACHE_TTL_TICKS) {
			return cached.hasSight;
		}

		boolean hasSight = computeTransparentAwareSight(level, player);
		if (this.sightCache.size() >= SIGHT_CACHE_MAX_ENTRIES) {
			this.sightCache.clear();
		}
		this.sightCache.put(player.getUUID(), new SightCacheEntry(nowTick, hasSight));
		return hasSight;
	}

	private boolean computeTransparentAwareSight(ServerLevel level, ServerPlayer player) {
		Vec3 start = this.getEyePosition();
		Vec3 end = player.getEyePosition();
		Vec3 direction = end.subtract(start);
		double distance = direction.length();
		if (distance <= 1.0E-6D) {
			return true;
		}

		int steps = Math.max(1, net.minecraft.util.Mth.ceil(distance / TRANSPARENT_SIGHT_STEP_BLOCKS));
		Vec3 step = direction.scale(1.0D / steps);
		BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos lastPos = new BlockPos.MutableBlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
		Vec3 current = start;
		for (int i = 1; i < steps; i++) {
			current = current.add(step);
			mutablePos.set(current.x, current.y, current.z);
			if (mutablePos.equals(lastPos)) {
				continue;
			}
			lastPos.set(mutablePos);
			if (blocksSight(level, mutablePos)) {
				return false;
			}
		}

		return true;
	}

	private boolean blocksSight(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
			return true;
		}
		return state.isSolidRender();
	}

	private boolean isEligibleTarget(ServerPlayer player) {
		return player != null
				&& player.isAlive()
				&& !player.isCreative()
				&& !player.isSpectator()
				&& player.level() == this.level()
				&& ServerBackroomsSystem.isInBackrooms(player);
	}

	private void clearTrackedTarget() {
		this.trackedTargetUuid = null;
		this.lastSeenTargetTick = Long.MIN_VALUE;
		this.nextChaseRepathTick = 0L;
		this.nextChaseDetourTick = 0L;
		this.chaseStuckTicks = 0;
		this.lastChasePos = this.position();
		this.lastChasePathTargetPos = Vec3.ZERO;
		this.lastSeenTargetPos = Vec3.ZERO;
		this.chaseDetourTarget = null;
		this.wanderTarget = null;
		this.nextWanderSearchTick = 0L;
		this.stationaryTicks = 0;
		this.sightCache.clear();
	}

	private void updateChaseMovement(ServerLevel level, long nowTick, ServerPlayer target) {
		this.getNavigation().setMaxVisitedNodesMultiplier(CHASE_MAX_VISITED_NODES_MULTIPLIER);
		Vec3 currentPos = this.position();
		Vec3 targetPos = target.position();
		boolean directSight = hasTransparentAwareSight(target, nowTick);
		if (currentPos.distanceToSqr(this.lastChasePos) < CHASE_STUCK_MOVE_THRESHOLD_SQR) {
			this.chaseStuckTicks++;
		} else {
			this.chaseStuckTicks = 0;
		}
		this.lastChasePos = currentPos;

		if (directSight) {
			this.getNavigation().stop();
			this.chaseDetourTarget = null;
			this.lastChasePathTargetPos = targetPos;
			this.lastSeenTargetPos = targetPos;
			this.getMoveControl().setWantedPosition(targetPos.x, targetPos.y, targetPos.z, CHASE_SPEED_MODIFIER);
			lookInMovementDirection();
			lookAtTarget(target);
			return;
		}

		if (this.lastSeenTargetTick != Long.MIN_VALUE
				&& nowTick - this.lastSeenTargetTick <= CHASE_LAST_SEEN_DIRECT_MOVE_TICKS
				&& this.lastSeenTargetPos != Vec3.ZERO) {
			this.getNavigation().stop();
			this.chaseDetourTarget = null;
			this.getMoveControl().setWantedPosition(this.lastSeenTargetPos.x, this.lastSeenTargetPos.y, this.lastSeenTargetPos.z, CHASE_SPEED_MODIFIER);
			lookInMovementDirection();
			lookAtTarget(target);
			return;
		}

		boolean hardStuck = this.chaseStuckTicks >= CHASE_STUCK_REPATH_AFTER_TICKS;
		boolean hasActivePath = this.getNavigation().isInProgress() && !this.getNavigation().isDone();
		boolean targetMoved = this.lastChasePathTargetPos == Vec3.ZERO
				|| targetPos.distanceToSqr(this.lastChasePathTargetPos) >= 4.0D;
		boolean shouldRepath = nowTick >= this.nextChaseRepathTick
				|| this.getNavigation().isDone()
				|| this.getNavigation().isStuck()
				|| hardStuck
				|| !hasActivePath
				|| targetMoved;
		if (shouldRepath) {
			boolean moved = this.getNavigation().moveTo(target, CHASE_SPEED_MODIFIER);
			this.chaseDetourTarget = null;
			if (!moved && !hasActivePath) {
				this.getNavigation().stop();
				this.chaseDetourTarget = null;
			}
			if (moved) {
				this.lastChasePathTargetPos = targetPos;
			}
			this.nextChaseRepathTick = nowTick + (hardStuck ? CHASE_STUCK_REPATH_INTERVAL_TICKS : CHASE_REPATH_TICKS);
		}

		if (this.chaseDetourTarget != null && currentPos.distanceToSqr(this.chaseDetourTarget) < 2.25D) {
			this.chaseDetourTarget = null;
			this.nextChaseRepathTick = nowTick;
		}

		lookInMovementDirection();
		lookAtTarget(target);
	}

	private ChasePathPlan buildBestChasePlan(ServerLevel level, ServerPlayer target, boolean allowDetourSearch) {
		Path directPath = this.getNavigation().createPath(target, 0);
		if (directPath != null && directPath.canReach() && directPath.getDistToTarget() <= CHASE_DIRECT_PATH_DIST_TOLERANCE) {
			return new ChasePathPlan(directPath, null);
		}
		if (directPath != null && (!allowDetourSearch || directPath.canReach())) {
			return new ChasePathPlan(directPath, null);
		}
		if (!allowDetourSearch) {
			return null;
		}

		Path bestPath = directPath;
		Vec3 bestDetour = null;
		double bestScore = directPath == null ? Double.POSITIVE_INFINITY : pathPlanScore(directPath, null, target.position());

		RandomSource random = this.getRandom();
		Vec3 targetPos = target.position();
		double baseAngle = Math.atan2(this.getZ() - targetPos.z, this.getX() - targetPos.x);
		int targetY = target.blockPosition().getY();
		for (int attempt = 0; attempt < CHASE_DETOUR_ATTEMPTS; attempt++) {
			double direction = (attempt & 1) == 0 ? 1.0D : -1.0D;
			double sweep = ((attempt / 2) + 1) * (Math.PI / 18.0D);
			double angle = baseAngle + (direction * sweep) + ((random.nextDouble() - 0.5D) * 0.35D);
			int distance = randomInclusive(random, CHASE_DETOUR_MIN_DISTANCE, CHASE_DETOUR_MAX_DISTANCE);
			int x = net.minecraft.util.Mth.floor(targetPos.x + (Math.cos(angle) * distance));
			int z = net.minecraft.util.Mth.floor(targetPos.z + (Math.sin(angle) * distance));

			for (int yOffset = CHASE_DETOUR_Y_SEARCH; yOffset >= -CHASE_DETOUR_Y_SEARCH; yOffset--) {
				Vec3 candidate = findWalkablePosition(level, x, targetY + yOffset, z);
				if (candidate == null || candidate.distanceToSqr(this.position()) < 4.0D) {
					continue;
				}

				Path path = this.getNavigation().createPath(net.minecraft.core.BlockPos.containing(candidate), 0);
				if (path == null || !path.canReach()) {
					continue;
				}

				double score = pathPlanScore(path, candidate, targetPos);
				if (score < bestScore) {
					bestScore = score;
					bestPath = path;
					bestDetour = candidate;
				}
			}
		}

		if (bestPath == null) {
			return null;
		}
		return new ChasePathPlan(bestPath, bestDetour);
	}

	private static double pathPlanScore(Path path, Vec3 detourTarget, Vec3 chaseTargetPos) {
		double distanceToTarget = detourTarget == null
				? path.getDistToTarget()
				: Math.sqrt(detourTarget.distanceToSqr(chaseTargetPos));
		double reachPenalty = path.canReach() ? 0.0D : 128.0D;
		return reachPenalty + (distanceToTarget * 3.0D) + path.getNodeCount();
	}

	private void updateWanderMovement(ServerLevel level, long nowTick) {
		this.getNavigation().stop();
		Vec3 currentPos = this.position();
		if (currentPos.distanceToSqr(this.lastTickPos) < 0.0025D) {
			this.stationaryTicks++;
		} else {
			this.stationaryTicks = 0;
		}
		this.lastTickPos = currentPos;

		boolean reachedTarget = this.wanderTarget != null && currentPos.distanceToSqr(this.wanderTarget) < 2.25D;
		boolean pathInvalid = this.stationaryTicks >= STUCK_REPATH_TICKS;
		boolean needsNewTarget = this.wanderTarget == null || reachedTarget || pathInvalid;
		if (needsNewTarget && nowTick >= this.nextWanderSearchTick) {
			this.wanderTarget = pickWanderTarget(level);
			this.stationaryTicks = 0;
			this.nextWanderRefreshTick = nowTick + WANDER_REPATH_TICKS;
			this.nextWanderSearchTick = nowTick + WANDER_SEARCH_COOLDOWN_TICKS;
		}

		if (this.wanderTarget != null) {
			this.getMoveControl().setWantedPosition(this.wanderTarget.x, this.wanderTarget.y, this.wanderTarget.z, 1.0D);
		}

		lookInMovementDirection();
		setHeadPitch(WANDER_HEAD_PITCH);
	}

	private Vec3 pickWanderTarget(ServerLevel level) {
		RandomSource random = this.getRandom();
		BlockPos origin = this.blockPosition();
		Direction[] directions = new Direction[]{
				Direction.NORTH,
				Direction.SOUTH,
				Direction.WEST,
				Direction.EAST
		};
		for (int attempt = 0; attempt < MAX_WANDER_ATTEMPTS; attempt++) {
			Direction direction = directions[random.nextInt(directions.length)];
			int maxDistance = MIN_WANDER_DISTANCE + random.nextInt(MAX_WANDER_DISTANCE - MIN_WANDER_DISTANCE + 1);
			Vec3 bestCandidate = null;
			for (int step = 1; step <= maxDistance; step++) {
				int x = origin.getX() + (direction.getStepX() * step);
				int z = origin.getZ() + (direction.getStepZ() * step);
				Vec3 candidate = findWalkablePosition(level, x, origin.getY(), z);
				if (candidate == null) {
					break;
				}
				bestCandidate = candidate;
			}
			if (bestCandidate != null && bestCandidate.distanceToSqr(this.position()) >= (MIN_WANDER_DISTANCE * MIN_WANDER_DISTANCE)) {
				return bestCandidate;
			}
		}
		return null;
	}

	private Vec3 findWalkablePosition(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos feetPos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos headPos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos floorPos = new BlockPos.MutableBlockPos();
		for (int scanY = y + MAX_VERTICAL_SEARCH; scanY >= y - MAX_VERTICAL_SEARCH; scanY--) {
			feetPos.set(x, scanY, z);
			if (!canStandAt(level, feetPos, headPos, floorPos)) {
				continue;
			}
			return Vec3.atBottomCenterOf(feetPos);
		}
		return null;
	}

	private boolean canStandAt(
			ServerLevel level,
			BlockPos.MutableBlockPos feetPos,
			BlockPos.MutableBlockPos headPos,
			BlockPos.MutableBlockPos floorPos
	) {
		if (!level.getWorldBorder().isWithinBounds(feetPos)) {
			return false;
		}

		headPos.set(feetPos.getX(), feetPos.getY() + 1, feetPos.getZ());
		floorPos.set(feetPos.getX(), feetPos.getY() - 1, feetPos.getZ());
		BlockState feetState = level.getBlockState(feetPos);
		BlockState headState = level.getBlockState(headPos);
		BlockState floorState = level.getBlockState(floorPos);
		FluidState feetFluid = level.getFluidState(feetPos);
		FluidState headFluid = level.getFluidState(headPos);
		if (!feetState.getCollisionShape(level, feetPos).isEmpty()) {
			return false;
		}
		if (!headState.getCollisionShape(level, headPos).isEmpty()) {
			return false;
		}
		if (!feetFluid.isEmpty() || !headFluid.isEmpty()) {
			return false;
		}
		if (floorState.getCollisionShape(level, floorPos).isEmpty()) {
			return false;
		}
		return true;
	}

	private void updateTouchDamage(ServerLevel level, long nowTick, ServerPlayer target) {
		if (nowTick < this.nextAttackTick) {
			return;
		}

		if (target != null
				&& isEligibleTarget(target)
				&& this.distanceToSqr(target) <= ATTACK_RANGE_SQR
				&& hasTransparentAwareSight(target, nowTick)) {
			applyUnstoppableHit(target);
			this.nextAttackTick = nowTick + ATTACK_COOLDOWN_TICKS;
		}
	}

	private void applyUnstoppableHit(ServerPlayer player) {
		float remainingHealth = player.getHealth() - 6.0F;
		if (remainingHealth > 0.0F) {
			player.setHealth(remainingHealth);
			return;
		}

		player.kill((ServerLevel) player.level());
	}

	private void lookAtTarget(ServerPlayer target) {
		Vec3 eyes = target.getEyePosition();
		Vec3 ownEyes = this.getEyePosition();
		Vec3 delta = eyes.subtract(ownEyes);
		double horizontalDistance = Math.sqrt((delta.x * delta.x) + (delta.z * delta.z));
		float yaw = (float) (net.minecraft.util.Mth.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
		float pitch = (float) (-(net.minecraft.util.Mth.atan2(delta.y, horizontalDistance) * (180.0D / Math.PI)));

		this.setYHeadRot(net.minecraft.util.Mth.approachDegrees(this.getYHeadRot(), yaw, 24.0F));
		this.getLookControl().setLookAt(target, 360.0F, 360.0F);
		setHeadPitch(pitch);
	}

	private void lookInMovementDirection() {
		Vec3 velocity = this.getDeltaMovement();
		Vec3 navigationDelta = this.wanderTarget == null ? Vec3.ZERO : this.wanderTarget.subtract(this.position());
		Vec3 direction = velocity.horizontalDistanceSqr() > 1.0E-4D ? velocity : navigationDelta;
		if (direction.horizontalDistanceSqr() <= 1.0E-4D) {
			return;
		}

		float yaw = (float) (net.minecraft.util.Mth.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
		this.setYRot(yaw);
		this.setYBodyRot(yaw);
		this.setYHeadRot(net.minecraft.util.Mth.approachDegrees(this.getYHeadRot(), yaw, 20.0F));
	}

	private void setHeadPitch(float pitch) {
		this.setXRot(pitch);
		this.xRotO = pitch;
	}

	private void tryJumpStepUp() {
		if (!this.onGround() || !this.horizontalCollision || this.isInWater() || this.isInLava()) {
			return;
		}

		if (this.chasingTarget) {
			this.jumpFromGround();
			return;
		}

		PathNavigation navigation = this.getNavigation();
		if (navigation == null || !navigation.isInProgress()) {
			return;
		}

		Path path = navigation.getPath();
		if (path == null || path.isDone()) {
			return;
		}

		Vec3 nextNodePos = path.getNextEntityPos(this);
		double deltaY = nextNodePos.y - this.getY();
		if (deltaY < STEP_JUMP_MIN_DELTA_Y || deltaY > STEP_JUMP_MAX_DELTA_Y) {
			return;
		}

		this.jumpFromGround();
	}

	private void tickSkinScreamer(ServerLevel level, long nowTick) {
		if ((nowTick + this.getId()) % SKIN_SCREAMER_IDLE_TICK_INTERVAL != 0L && !hasActiveSkinScreamerState()) {
			return;
		}
		Set<UUID> activeViewers = new HashSet<>();
		for (ServerPlayer viewer : level.players()) {
			UUID viewerId = viewer.getUUID();
			activeViewers.add(viewerId);
			if (viewer.connection == null) {
				continue;
			}

			ViewerSkinScreamerState state = this.viewerSkinScreamerStates.computeIfAbsent(viewerId, ignored -> new ViewerSkinScreamerState());
			if (!isEligibleTarget(viewer)) {
				if (state.showMasked) {
					state.showMasked = false;
					setMaskedSkinForViewer(this.getUUID(), viewerId, false);
					applyViewerSkinState(viewer, false);
				}
				state.reset();
				continue;
			}
			if (this.distanceToSqr(viewer) > SKIN_SCREAMER_TRIGGER_RADIUS_SQR) {
				if (state.showMasked) {
					state.showMasked = false;
					setMaskedSkinForViewer(this.getUUID(), viewerId, false);
					applyViewerSkinState(viewer, false);
				}
				state.reset();
				continue;
			}

			boolean lookingNow = state.lookingNow;
			if (state.togglesRemaining > 0 || nowTick >= state.nextLookCheckTick) {
				lookingNow = isViewerLookingAtStalker(viewer, nowTick);
				state.lookingNow = lookingNow;
				state.nextLookCheckTick = nowTick + (lookingNow ? 1L : SKIN_SCREAMER_LOOK_CHECK_INTERVAL_TICKS);
			}

			if (!lookingNow) {
				state.wasLooking = false;
				state.togglesRemaining = 0;
				state.nextToggleTick = 0L;
				if (state.showMasked) {
					state.showMasked = false;
					setMaskedSkinForViewer(this.getUUID(), viewerId, false);
					applyViewerSkinState(viewer, false);
				}
				continue;
			}

			boolean maskReady = state.maskReady;
			if (!maskReady || nowTick >= state.nextMaskPrewarmTick) {
				maskReady = prepareMaskedSkin(viewer) != null;
				state.maskReady = maskReady;
				state.nextMaskPrewarmTick = nowTick + SKIN_SCREAMER_MASK_PREWARM_INTERVAL_TICKS;
			}

			if (!state.wasLooking && maskReady && level.random.nextFloat() < SKIN_SCREAMER_BLINK_CHANCE) {
				state.togglesRemaining = randomInclusive(level.random, SKIN_SCREAMER_MIN_TOGGLES, SKIN_SCREAMER_MAX_TOGGLES);
				state.nextToggleTick = nowTick;
			}
			state.wasLooking = true;

			if (state.togglesRemaining > 0 && nowTick >= state.nextToggleTick) {
				state.showMasked = !state.showMasked;
				state.togglesRemaining--;
				int phaseTicks = state.showMasked
						? randomInclusive(level.random, SKIN_SCREAMER_MASK_PHASE_MIN_TICKS, SKIN_SCREAMER_MASK_PHASE_MAX_TICKS)
						: randomInclusive(level.random, SKIN_SCREAMER_GAP_PHASE_MIN_TICKS, SKIN_SCREAMER_GAP_PHASE_MAX_TICKS);
				state.nextToggleTick = nowTick + phaseTicks;

				setMaskedSkinForViewer(this.getUUID(), viewerId, state.showMasked);
				applyViewerSkinState(viewer, state.showMasked);

				if (state.togglesRemaining == 0 && state.showMasked) {
					state.showMasked = false;
					setMaskedSkinForViewer(this.getUUID(), viewerId, false);
					applyViewerSkinState(viewer, false);
				}
			}
		}

		this.viewerSkinScreamerStates.entrySet().removeIf(entry -> !activeViewers.contains(entry.getKey()));
		Map<UUID, Boolean> perViewerState = MASKED_SKIN_VIEWER_STATE.get(this.getUUID());
		if (perViewerState != null) {
			perViewerState.keySet().removeIf(id -> !activeViewers.contains(id));
			if (perViewerState.isEmpty()) {
				MASKED_SKIN_VIEWER_STATE.remove(this.getUUID());
			}
		}
	}

	private static int randomInclusive(RandomSource random, int min, int max) {
		if (max <= min) {
			return min;
		}
		return min + random.nextInt((max - min) + 1);
	}

	private void applyViewerSkinState(ServerPlayer viewer, boolean useMaskedSkin) {
		viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(this.getUUID())));
		PolymerEntityUtils.refreshEntity(viewer, this);
		syncViewerRotationAfterSkinRefresh(viewer);
	}

	private void syncViewerRotationAfterSkinRefresh(ServerPlayer viewer) {
		byte bodyYaw = toRotationByte(this.getYRot());
		byte pitch = toRotationByte(this.getXRot());
		byte headYaw = toRotationByte(this.getYHeadRot());
		viewer.connection.send(new ClientboundMoveEntityPacket.Rot(this.getId(), bodyYaw, pitch, this.onGround()));
		viewer.connection.send(new ClientboundRotateHeadPacket(this, headYaw));
	}

	private static byte toRotationByte(float degrees) {
		return (byte) net.minecraft.util.Mth.floor((degrees * 256.0F) / 360.0F);
	}

	private boolean isViewerLookingAtStalker(ServerPlayer viewer, long nowTick) {
		Vec3 viewerEyes = viewer.getEyePosition();
		Vec3 stalkerEyes = this.getEyePosition();
		Vec3 toStalker = stalkerEyes.subtract(viewerEyes);
		double toStalkerLengthSqr = toStalker.lengthSqr();
		if (toStalkerLengthSqr > SKIN_SCREAMER_TRIGGER_RADIUS_SQR) {
			return false;
		}
		if (toStalkerLengthSqr <= 1.0E-8D) {
			return true;
		}

		Vec3 look = viewer.getViewVector(1.0F);
		double lookLengthSqr = look.lengthSqr();
		if (lookLengthSqr <= 1.0E-8D) {
			return false;
		}

		double dot = look.normalize().dot(toStalker.normalize());
		if (dot < SKIN_SCREAMER_MIN_LOOK_DOT) {
			return false;
		}

		return hasTransparentAwareSight(viewer, nowTick);
	}

	private void sendRemovalPackets() {
		if (this.removalPacketsSent || !(this.level() instanceof ServerLevel level)) {
			return;
		}

		this.removalPacketsSent = true;
		MASKED_SKIN_VIEWER_STATE.remove(this.getUUID());
		PlayerTeam team = createHiddenTeam(this.getUUID(), buildProfileName(this.getUUID()));
		ClientboundSetPlayerTeamPacket teamPacket = ClientboundSetPlayerTeamPacket.createRemovePacket(team);
		ClientboundPlayerInfoRemovePacket removePacket = new ClientboundPlayerInfoRemovePacket(List.of(this.getUUID()));
		for (ServerPlayer player : level.players()) {
			player.connection.send(teamPacket);
			player.connection.send(removePacket);
		}
	}

	private static GameProfile buildViewerProfile(UUID profileId, ServerPlayer viewer, boolean useMaskedSkin) {
		PropertyMap properties = buildViewerProperties(viewer, useMaskedSkin);
		return new GameProfile(profileId, buildProfileName(profileId), properties);
	}

	private static PropertyMap buildViewerProperties(ServerPlayer viewer, boolean useMaskedSkin) {
		Multimap<String, Property> mutableProperties = ArrayListMultimap.create();
		if (viewer.getGameProfile() != null) {
			mutableProperties.putAll(viewer.getGameProfile().properties());
		}

		try {
			Property sourceSkin = resolveViewerSourceSkin(viewer);
			if (sourceSkin == null) {
				return new PropertyMap(mutableProperties);
			}

			Property selectedSkin = sourceSkin;
			if (useMaskedSkin) {
				Property maskedSkin = getCachedMaskedViewerSkin(sourceSkin);
				if (maskedSkin != null) {
					selectedSkin = maskedSkin;
				}
			}
			mutableProperties.removeAll("textures");
			mutableProperties.put("textures", selectedSkin);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to resolve viewer skin for backrooms stalker and {}", viewer.getScoreboardName(), exception);
		}

		return new PropertyMap(mutableProperties);
	}

	private static Property resolveViewerSourceSkin(ServerPlayer viewer) {
		Property current = PlayerUtils.getPlayerSkin(viewer.getGameProfile());
		if (current != null) {
			return current;
		}

		SkinStorage skinStorage = SkinRestorer.getSkinStorage();
		if (skinStorage == null) {
			return null;
		}

		SkinValue stored = skinStorage.getSkin(viewer.getUUID());
		return stored == null ? null : stored.value();
	}

	private static Property prepareMaskedSkin(ServerPlayer viewer) {
		Property sourceSkin = resolveViewerSourceSkin(viewer);
		if (sourceSkin == null) {
			return null;
		}
		String sourceCacheKey = getMaskedSkinSourceCacheKey(sourceSkin);
		Property cached = MASKED_SKIN_BY_SOURCE_CACHE.get(sourceCacheKey);
		if (cached != null) {
			return cached;
		}

		long nowMs = System.currentTimeMillis();
		Long retryAtMs = MASKED_SKIN_RETRY_AT_MS.get(sourceCacheKey);
		if (retryAtMs != null && nowMs < retryAtMs) {
			return null;
		}

		queueMaskedSkinBuild(viewer.getScoreboardName(), sourceSkin, sourceCacheKey, nowMs);
		return null;
	}

	private static void queueMaskedSkinBuild(String viewerName, Property sourceSkin, String sourceCacheKey, long nowMs) {
		if (!MASKED_SKIN_BUILD_IN_FLIGHT.add(sourceCacheKey)) {
			return;
		}

		CompletableFuture.runAsync(() -> {
			Property generated = null;
			try {
				generated = buildMaskedViewerSkin(sourceSkin, viewerName);
			} finally {
				if (generated != null) {
					MASKED_SKIN_BY_SOURCE_CACHE.put(sourceCacheKey, generated);
					MASKED_SKIN_RETRY_AT_MS.remove(sourceCacheKey);
				} else {
					MASKED_SKIN_RETRY_AT_MS.put(sourceCacheKey, nowMs + MASKED_SKIN_RETRY_COOLDOWN_MS);
				}
				MASKED_SKIN_BUILD_IN_FLIGHT.remove(sourceCacheKey);
			}
		});
	}

	private static String getMaskedSkinSourceCacheKey(Property sourceSkin) {
		return sourceSkin.value() + "|" + STALKER_MASK_CACHE_VERSION;
	}

	private static Property getCachedMaskedViewerSkin(Property sourceSkin) {
		return MASKED_SKIN_BY_SOURCE_CACHE.get(getMaskedSkinSourceCacheKey(sourceSkin));
	}

	private static Property buildMaskedViewerSkin(Property sourceSkin, String viewerName) {
		try {
			Pair<String, SkinVariant> skinData = PlayerUtils.getSkinUrl(sourceSkin);
			if (skinData == null || skinData.first() == null || skinData.first().isBlank()) {
				return null;
			}

			URI sourceSkinUri = new URI(skinData.first());
			BufferedImage sourceSkinImage = loadSkinImage(sourceSkinUri);
			BufferedImage maskImage = getStalkerMaskImage();
			if (sourceSkinImage == null || maskImage == null) {
				return null;
			}

			BufferedImage preparedSkinImage = blendHeadOuterLayerOntoBaseAndClear(sourceSkinImage);
			BufferedImage composed = composeMaskedSkin(preparedSkinImage, maskImage);
			java.nio.file.Path composedPath = writeComposedSkin(getMaskedSkinSourceCacheKey(sourceSkin), composed);
			SkinVariant variant = skinData.second() == null ? SkinVariant.CLASSIC : skinData.second();
			return signMaskedSkin(composedPath.toUri(), variant);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to build masked stalker skin for {}", viewerName, exception);
			return null;
		}
	}

	private static Property signMaskedSkin(URI skinUri, SkinVariant variant) {
		try {
			return MineskinService.INSTANCE.signSkin(skinUri, variant).orElse(null);
		} catch (Exception firstFailure) {
			if (!MINESKIN_RELOADED_LAZILY) {
				MINESKIN_RELOADED_LAZILY = true;
				try {
					MineskinService.INSTANCE.reload();
					return MineskinService.INSTANCE.signSkin(skinUri, variant).orElse(null);
				} catch (Exception secondFailure) {
					Lg2.LOGGER.debug("Failed to sign masked stalker skin after lazy Mineskin reload", secondFailure);
				}
			} else {
				Lg2.LOGGER.debug("Failed to sign masked stalker skin", firstFailure);
			}
		}
		return null;
	}

	private static BufferedImage loadSkinImage(URI uri) throws IOException {
		try (InputStream stream = uri.toURL().openStream()) {
			BufferedImage image = ImageIO.read(stream);
			if (image == null) {
				return null;
			}
			return normalizeSkinImage(toArgb(image));
		}
	}

	private static BufferedImage getStalkerMaskImage() {
		BufferedImage image = STALKER_MASK_IMAGE;
		if (image != null) {
			return image;
		}

		synchronized (BackroomsStalkerEntity.class) {
			if (STALKER_MASK_IMAGE != null) {
				return STALKER_MASK_IMAGE;
			}
			STALKER_MASK_IMAGE = loadMaskImage();
			return STALKER_MASK_IMAGE;
		}
	}

	private static BufferedImage loadMaskImage() {
		try (InputStream resourceStream = BackroomsStalkerEntity.class.getResourceAsStream(STALKER_MASK_RESOURCE_PATH)) {
			if (resourceStream != null) {
				BufferedImage image = ImageIO.read(resourceStream);
				if (image != null) {
					return normalizeSkinImage(toArgb(image));
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.debug("Failed to load stalker mask from classpath {}", STALKER_MASK_RESOURCE_PATH, exception);
		}

		if (Files.exists(STALKER_MASK_FALLBACK_PATH)) {
			try (InputStream fileStream = Files.newInputStream(STALKER_MASK_FALLBACK_PATH)) {
				BufferedImage image = ImageIO.read(fileStream);
				if (image != null) {
					return normalizeSkinImage(toArgb(image));
				}
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to load stalker mask from {}", STALKER_MASK_FALLBACK_PATH, exception);
			}
		}

		return null;
	}

	private static BufferedImage composeMaskedSkin(BufferedImage source, BufferedImage mask) {
		int width = source.getWidth();
		int height = source.getHeight();
		BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int sourceArgb = source.getRGB(x, y);
				int maskArgb = mask.getRGB(x, y);
				result.setRGB(x, y, blendSrcOver(sourceArgb, maskArgb));
			}
		}

		return result;
	}

	private static BufferedImage blendHeadOuterLayerOntoBaseAndClear(BufferedImage source) {
		BufferedImage merged = toArgb(source);
		int[][] headLayerRegions = new int[][]{
				{40, 0, 8, 0},   // top
				{48, 0, 16, 0},  // bottom
				{32, 8, 0, 8},   // right
				{40, 8, 8, 8},   // front
				{48, 8, 16, 8},  // left
				{56, 8, 24, 8}   // back
		};

		for (int[] region : headLayerRegions) {
			int sourceX = region[0];
			int sourceY = region[1];
			int targetX = region[2];
			int targetY = region[3];

			for (int y = 0; y < 8; y++) {
				for (int x = 0; x < 8; x++) {
					int baseArgb = merged.getRGB(targetX + x, targetY + y);
					int overlayArgb = source.getRGB(sourceX + x, sourceY + y);
					merged.setRGB(targetX + x, targetY + y, blendSrcOver(baseArgb, overlayArgb));
					merged.setRGB(sourceX + x, sourceY + y, 0x00000000);
				}
			}
		}

		return merged;
	}

	private static int blendSrcOver(int baseArgb, int overlayArgb) {
		int overlayAlpha = (overlayArgb >>> 24) & 0xFF;
		if (overlayAlpha <= 0) {
			return baseArgb;
		}
		if (overlayAlpha >= 255) {
			return overlayArgb;
		}

		float oa = overlayAlpha / 255.0F;
		float ba = ((baseArgb >>> 24) & 0xFF) / 255.0F;
		float outA = oa + (ba * (1.0F - oa));
		if (outA <= 1.0E-6F) {
			return 0;
		}

		float br = ((baseArgb >>> 16) & 0xFF) / 255.0F;
		float bg = ((baseArgb >>> 8) & 0xFF) / 255.0F;
		float bb = (baseArgb & 0xFF) / 255.0F;

		float or = ((overlayArgb >>> 16) & 0xFF) / 255.0F;
		float og = ((overlayArgb >>> 8) & 0xFF) / 255.0F;
		float ob = (overlayArgb & 0xFF) / 255.0F;

		float outR = ((or * oa) + (br * ba * (1.0F - oa))) / outA;
		float outG = ((og * oa) + (bg * ba * (1.0F - oa))) / outA;
		float outB = ((ob * oa) + (bb * ba * (1.0F - oa))) / outA;

		int a = Math.round(outA * 255.0F);
		int r = Math.round(outR * 255.0F);
		int g = Math.round(outG * 255.0F);
		int b = Math.round(outB * 255.0F);
		return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}

	private static java.nio.file.Path writeComposedSkin(String cacheKey, BufferedImage image) throws IOException {
		Files.createDirectories(STALKER_MASKED_SKIN_CACHE_DIR);
		String hash = shortSha1(cacheKey);
		java.nio.file.Path output = STALKER_MASKED_SKIN_CACHE_DIR.resolve("stalker_masked_" + hash + ".png");
		ImageIO.write(image, "PNG", output.toFile());
		return output;
	}

	private static BufferedImage normalizeSkinImage(BufferedImage image) {
		if (image.getWidth() == 64 && image.getHeight() == 64) {
			return image;
		}

		BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = normalized.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		graphics.drawImage(image, 0, 0, 64, 64, null);
		graphics.dispose();
		return normalized;
	}

	private static BufferedImage toArgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}

		BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = converted.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return converted;
	}

	private static String shortSha1(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException exception) {
			return Integer.toHexString(value.hashCode());
		}
	}

	private static String buildProfileName(UUID uuid) {
		String compact = (uuid == null ? UUID.randomUUID() : uuid).toString().replace("-", "");
		return "st" + compact.substring(0, 14);
	}

	private static String buildTeamName(UUID uuid) {
		String compact = (uuid == null ? UUID.randomUUID() : uuid).toString().replace("-", "");
		return "lg2st_" + compact.substring(0, 10);
	}

	private static void setMaskedSkinForViewer(UUID stalkerProfileId, UUID viewerId, boolean enabled) {
		if (stalkerProfileId == null || viewerId == null) {
			return;
		}
		if (!enabled) {
			Map<UUID, Boolean> perViewer = MASKED_SKIN_VIEWER_STATE.get(stalkerProfileId);
			if (perViewer == null) {
				return;
			}
			perViewer.remove(viewerId);
			if (perViewer.isEmpty()) {
				MASKED_SKIN_VIEWER_STATE.remove(stalkerProfileId);
			}
			return;
		}

		MASKED_SKIN_VIEWER_STATE
				.computeIfAbsent(stalkerProfileId, ignored -> new ConcurrentHashMap<>())
				.put(viewerId, Boolean.TRUE);
	}

	private static boolean isMaskedSkinEnabledForViewer(UUID stalkerProfileId, UUID viewerId) {
		Map<UUID, Boolean> perViewer = MASKED_SKIN_VIEWER_STATE.get(stalkerProfileId);
		if (perViewer == null) {
			return false;
		}
		return Boolean.TRUE.equals(perViewer.get(viewerId));
	}

	private static ClientboundPlayerInfoUpdatePacket createPlayerInfoUpdatePacket(GameProfile profile) {
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
				profile.id(),
				profile,
				false,
				0,
				GameType.SURVIVAL,
				null,
				true,
				0,
				(RemoteChatSession.Data) null
		);
		packet.entries().add(entry);
		return packet;
	}

	private static PlayerTeam createHiddenTeam(UUID profileId, String profileName) {
		PlayerTeam team = new PlayerTeam(new Scoreboard(), buildTeamName(profileId));
		team.setDisplayName(Component.empty());
		team.setPlayerPrefix(Component.empty());
		team.setPlayerSuffix(Component.empty());
		team.setNameTagVisibility(Team.Visibility.NEVER);
		team.setDeathMessageVisibility(Team.Visibility.NEVER);
		team.setCollisionRule(Team.CollisionRule.NEVER);
		team.getPlayers().add(profileName);
		return team;
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

	private static final class ChasePathPlan {
		private final Path path;
		private final Vec3 detourTarget;

		private ChasePathPlan(Path path, Vec3 detourTarget) {
			this.path = path;
			this.detourTarget = detourTarget;
		}

		private Path path() {
			return this.path;
		}

		private Vec3 detourTarget() {
			return this.detourTarget;
		}
	}

	private static final class SightCacheEntry {
		private final long tick;
		private final boolean hasSight;

		private SightCacheEntry(long tick, boolean hasSight) {
			this.tick = tick;
			this.hasSight = hasSight;
		}
	}

	private static final class ViewerSkinScreamerState {
		private boolean wasLooking = false;
		private boolean lookingNow = false;
		private boolean showMasked = false;
		private boolean maskReady = false;
		private int togglesRemaining = 0;
		private long nextLookCheckTick = 0L;
		private long nextMaskPrewarmTick = 0L;
		private long nextToggleTick = 0L;

		private void reset() {
			this.wasLooking = false;
			this.lookingNow = false;
			this.showMasked = false;
			this.maskReady = false;
			this.togglesRemaining = 0;
			this.nextLookCheckTick = 0L;
			this.nextMaskPrewarmTick = 0L;
			this.nextToggleTick = 0L;
		}
	}

	private boolean hasActiveSkinScreamerState() {
		for (ViewerSkinScreamerState state : this.viewerSkinScreamerStates.values()) {
			if (state.showMasked || state.togglesRemaining > 0 || state.lookingNow) {
				return true;
			}
		}
		return false;
	}

	public static final class StalkerPlayerOverlay implements eu.pb4.polymer.core.api.entity.PolymerEntity {
		private final UUID profileId;

		private StalkerPlayerOverlay(UUID profileId) {
			this.profileId = profileId;
		}

		@Override
		public EntityType<?> getPolymerEntityType(PacketContext context) {
			return EntityType.PLAYER;
		}

		@Override
		public void onBeforeSpawnPacket(ServerPlayer player, java.util.function.Consumer<Packet<?>> packetConsumer) {
			boolean useMaskedSkin = isMaskedSkinEnabledForViewer(this.profileId, player.getUUID());
			GameProfile profile = buildViewerProfile(this.profileId, player, useMaskedSkin);
			packetConsumer.accept(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(
					createHiddenTeam(profile.id(), profile.name()),
					true
			));
			packetConsumer.accept(createPlayerInfoUpdatePacket(profile));
		}

		@Override
		public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
			upsertTrackedData(data, SynchedEntityData.DataValue.create(PlayerTrackedDataAccessor.lg2$getDataPlayerMainHand(), HumanoidArm.RIGHT));
			upsertTrackedData(data, SynchedEntityData.DataValue.create(PlayerTrackedDataAccessor.lg2$getDataPlayerModeCustomisation(), PLAYER_SKIN_PARTS_WITHOUT_CAPE));
		}
	}
}
