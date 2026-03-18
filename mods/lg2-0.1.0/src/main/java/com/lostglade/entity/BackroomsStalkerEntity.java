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
import net.minecraft.world.phys.AABB;
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
import java.util.concurrent.ConcurrentHashMap;

public final class BackroomsStalkerEntity extends Monster {
	private static final EntityDimensions PLAYER_SIZED_DIMENSIONS = EntityDimensions.fixed(0.6F, 1.8F);
	private static final byte PLAYER_SKIN_PARTS_WITHOUT_CAPE = (byte) 0x7E;
	private static final double WALK_SPEED = 0.23D;
	private static final double CHASE_SPEED_MODIFIER = 1.65D;
	private static final double ATTACK_RANGE = 2.2D;
	private static final double ATTACK_RANGE_SQR = ATTACK_RANGE * ATTACK_RANGE;
	private static final int FORGET_TARGET_AFTER_TICKS = 100;
	private static final int ATTACK_COOLDOWN_TICKS = 20;
	private static final int TARGET_SCAN_INTERVAL_TICKS = 3;
	private static final int CHASE_REPATH_TICKS = 8;
	private static final int CHASE_STUCK_REPATH_INTERVAL_TICKS = 3;
	private static final int CHASE_STUCK_REPATH_AFTER_TICKS = 12;
	private static final double CHASE_STUCK_MOVE_THRESHOLD_SQR = 0.01D;
	private static final int CHASE_DETOUR_ATTEMPTS = 36;
	private static final int CHASE_DETOUR_MIN_DISTANCE = 8;
	private static final int CHASE_DETOUR_MAX_DISTANCE = 36;
	private static final int CHASE_DETOUR_Y_SEARCH = 2;
	private static final float CHASE_DIRECT_PATH_DIST_TOLERANCE = 1.5F;
	private static final float CHASE_MAX_VISITED_NODES_MULTIPLIER = 10.0F;
	private static final double STEP_JUMP_MIN_DELTA_Y = 0.45D;
	private static final double STEP_JUMP_MAX_DELTA_Y = 1.25D;
	private static final int SIGHT_CACHE_TTL_TICKS = 2;
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
	private static final long MASKED_SKIN_RETRY_COOLDOWN_MS = 15_000L;
	private static final int MIN_WANDER_DISTANCE = 8;
	private static final int MAX_WANDER_DISTANCE = 24;
	private static final int MAX_WANDER_ATTEMPTS = 30;
	private static final int WANDER_REPATH_TICKS = 10;
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
	private static volatile BufferedImage STALKER_MASK_IMAGE;
	private static volatile boolean MINESKIN_RELOADED_LAZILY = false;

	private UUID trackedTargetUuid;
	private long lastSeenTargetTick = Long.MIN_VALUE;
	private long nextAttackTick = 0L;
	private long nextTargetScanTick = 0L;
	private long nextChaseRepathTick = 0L;
	private long nextWanderRefreshTick = 0L;
	private int chaseStuckTicks = 0;
	private Vec3 lastChasePos = Vec3.ZERO;
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
		ServerPlayer target = resolveTargetForTick(level, nowTick);
		if (target != null) {
			this.chasingTarget = true;
			updateChaseMovement(level, nowTick, target);
			updateTouchDamage(level, nowTick);
		} else {
			this.chasingTarget = false;
			updateWanderMovement(level, nowTick);
		}

		tickSkinScreamer(level, nowTick);
	}

	private ServerPlayer resolveTargetForTick(ServerLevel level, long nowTick) {
		if (nowTick >= this.nextTargetScanTick || this.trackedTargetUuid == null) {
			this.nextTargetScanTick = nowTick + TARGET_SCAN_INTERVAL_TICKS;
			return updateTrackedTarget(level, nowTick);
		}

		ServerPlayer currentTarget = resolveTrackedTarget(level);
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
		ServerPlayer nearestTarget = findNearestTarget(level);
		if (nearestTarget != null) {
			this.trackedTargetUuid = nearestTarget.getUUID();
			this.lastSeenTargetTick = nowTick;
			return nearestTarget;
		}

		clearTrackedTarget();
		return null;
	}

	private ServerPlayer resolveTrackedTarget(ServerLevel level) {
		if (this.trackedTargetUuid == null) {
			return null;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.trackedTargetUuid);
		if (!isEligibleTarget(player)) {
			clearTrackedTarget();
			return null;
		}
		return player;
	}

	private ServerPlayer findNearestTarget(ServerLevel level) {
		ServerPlayer nearest = null;
		double nearestDistanceSqr = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!isEligibleTarget(player)) {
				continue;
			}

			double distanceSqr = this.distanceToSqr(player);
			if (distanceSqr >= nearestDistanceSqr) {
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

		Vec3 unit = direction.normalize();
		Vec3 rayStart = start;
		for (int i = 0; i < 256; i++) {
			BlockHitResult hit = level.clip(new net.minecraft.world.level.ClipContext(
					rayStart,
					end,
					net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE,
					this
			));
			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}

			BlockPos hitPos = hit.getBlockPos();
			if (blocksSight(level, hitPos)) {
				return false;
			}

			Vec3 nextStart = hit.getLocation().add(unit.scale(0.03125D));
			if (nextStart.distanceToSqr(rayStart) < 1.0E-8D || nextStart.distanceToSqr(end) < 1.0E-8D) {
				return true;
			}
			rayStart = nextStart;
		}

		return false;
	}

	private boolean blocksSight(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return false;
		}
		if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
			return true;
		}
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return false;
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
		this.chaseStuckTicks = 0;
		this.lastChasePos = this.position();
		this.chaseDetourTarget = null;
		this.wanderTarget = null;
		this.stationaryTicks = 0;
		this.sightCache.clear();
	}

	private void updateChaseMovement(ServerLevel level, long nowTick, ServerPlayer target) {
		Vec3 currentPos = this.position();
		if (currentPos.distanceToSqr(this.lastChasePos) < CHASE_STUCK_MOVE_THRESHOLD_SQR) {
			this.chaseStuckTicks++;
		} else {
			this.chaseStuckTicks = 0;
		}
		this.lastChasePos = currentPos;

		boolean hardStuck = this.chaseStuckTicks >= CHASE_STUCK_REPATH_AFTER_TICKS;
		boolean shouldRepath = nowTick >= this.nextChaseRepathTick
				|| this.getNavigation().isDone()
				|| this.getNavigation().isStuck()
				|| hardStuck;
		if (shouldRepath) {
			ChasePathPlan plan = buildBestChasePlan(level, target);
			if (plan != null && this.getNavigation().moveTo(plan.path(), CHASE_SPEED_MODIFIER)) {
				this.chaseDetourTarget = plan.detourTarget();
			} else {
				this.getNavigation().stop();
				this.chaseDetourTarget = null;
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

	private ChasePathPlan buildBestChasePlan(ServerLevel level, ServerPlayer target) {
		Path directPath = this.getNavigation().createPath(target, 0);
		if (directPath != null && directPath.canReach() && directPath.getDistToTarget() <= CHASE_DIRECT_PATH_DIST_TOLERANCE) {
			return new ChasePathPlan(directPath, null);
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
		Vec3 currentPos = this.position();
		if (currentPos.distanceToSqr(this.lastTickPos) < 0.0025D) {
			this.stationaryTicks++;
		} else {
			this.stationaryTicks = 0;
		}
		this.lastTickPos = currentPos;

		boolean needsNewTarget = this.wanderTarget == null
				|| this.getNavigation().isDone()
				|| currentPos.distanceToSqr(this.wanderTarget) < 2.25D
				|| this.stationaryTicks >= STUCK_REPATH_TICKS
				|| nowTick >= this.nextWanderRefreshTick;
		if (needsNewTarget) {
			this.wanderTarget = pickWanderTarget(level);
			this.stationaryTicks = 0;
			this.nextWanderRefreshTick = nowTick + WANDER_REPATH_TICKS;
			if (this.wanderTarget != null) {
				this.getNavigation().moveTo(this.wanderTarget.x, this.wanderTarget.y, this.wanderTarget.z, 1.0D);
			} else {
				this.getNavigation().stop();
			}
		}

		lookInMovementDirection();
		setHeadPitch(WANDER_HEAD_PITCH);
	}

	private Vec3 pickWanderTarget(ServerLevel level) {
		RandomSource random = this.getRandom();
		for (int attempt = 0; attempt < MAX_WANDER_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * (Math.PI * 2.0D);
			int distance = MIN_WANDER_DISTANCE + random.nextInt(MAX_WANDER_DISTANCE - MIN_WANDER_DISTANCE + 1);
			int x = net.minecraft.util.Mth.floor(this.getX() + (Math.cos(angle) * distance));
			int z = net.minecraft.util.Mth.floor(this.getZ() + (Math.sin(angle) * distance));

			for (int yOffset = MAX_VERTICAL_SEARCH; yOffset >= -MAX_VERTICAL_SEARCH; yOffset--) {
				Vec3 candidate = findWalkablePosition(level, x, this.blockPosition().getY() + yOffset, z);
				if (candidate == null) {
					continue;
				}
				if (candidate.distanceToSqr(this.position()) < (MIN_WANDER_DISTANCE * MIN_WANDER_DISTANCE)) {
					continue;
				}

				Path path = this.getNavigation().createPath(net.minecraft.core.BlockPos.containing(candidate), 0);
				if (path != null && path.canReach()) {
					return candidate;
				}
			}
		}

		return null;
	}

	private Vec3 findWalkablePosition(ServerLevel level, int x, int y, int z) {
		for (int scanY = y + MAX_VERTICAL_SEARCH; scanY >= y - MAX_VERTICAL_SEARCH; scanY--) {
			net.minecraft.core.BlockPos feetPos = new net.minecraft.core.BlockPos(x, scanY, z);
			if (!canStandAt(level, feetPos)) {
				continue;
			}
			return Vec3.atBottomCenterOf(feetPos);
		}
		return null;
	}

	private boolean canStandAt(ServerLevel level, net.minecraft.core.BlockPos feetPos) {
		if (!level.getWorldBorder().isWithinBounds(feetPos)) {
			return false;
		}

		BlockState feetState = level.getBlockState(feetPos);
		BlockState headState = level.getBlockState(feetPos.above());
		BlockState floorState = level.getBlockState(feetPos.below());
		FluidState feetFluid = level.getFluidState(feetPos);
		FluidState headFluid = level.getFluidState(feetPos.above());
		if (!feetState.getCollisionShape(level, feetPos).isEmpty()) {
			return false;
		}
		if (!headState.getCollisionShape(level, feetPos.above()).isEmpty()) {
			return false;
		}
		if (!feetFluid.isEmpty() || !headFluid.isEmpty()) {
			return false;
		}
		if (floorState.getCollisionShape(level, feetPos.below()).isEmpty()) {
			return false;
		}

		AABB standingBox = this.getDimensions(this.getPose()).makeBoundingBox(
				feetPos.getX() + 0.5D,
				feetPos.getY(),
				feetPos.getZ() + 0.5D
		);
		return level.noCollision(this, standingBox);
	}

	private void updateTouchDamage(ServerLevel level, long nowTick) {
		if (nowTick < this.nextAttackTick) {
			return;
		}

		ServerPlayer nearestInRange = null;
		double nearestDistanceSqr = ATTACK_RANGE_SQR + 1.0D;
		for (ServerPlayer player : level.players()) {
			if (!isEligibleTarget(player)) {
				continue;
			}

			double distanceSqr = this.distanceToSqr(player);
			if (distanceSqr > ATTACK_RANGE_SQR || distanceSqr >= nearestDistanceSqr) {
				continue;
			}
			nearestInRange = player;
			nearestDistanceSqr = distanceSqr;
		}

		if (nearestInRange != null) {
			applyUnstoppableHit(nearestInRange);
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

			Property prewarmedMasked = prepareMaskedSkin(viewer);
			boolean lookingNow = isViewerLookingAtStalker(viewer, nowTick);
			boolean maskReady = prewarmedMasked != null;

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
				Property maskedSkin = resolveMaskedViewerSkin(viewer, sourceSkin);
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
		return resolveMaskedViewerSkin(viewer, sourceSkin);
	}

	private static Property resolveMaskedViewerSkin(ServerPlayer viewer, Property sourceSkin) {
		String sourceValue = sourceSkin.value();
		String sourceCacheKey = sourceValue + "|" + STALKER_MASK_CACHE_VERSION;
		Property cached = MASKED_SKIN_BY_SOURCE_CACHE.get(sourceCacheKey);
		if (cached != null) {
			return cached;
		}

		long nowMs = System.currentTimeMillis();
		Long retryAtMs = MASKED_SKIN_RETRY_AT_MS.get(sourceCacheKey);
		if (retryAtMs != null && nowMs < retryAtMs) {
			return null;
		}

		Property generated = null;
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
			java.nio.file.Path composedPath = writeComposedSkin(sourceCacheKey, composed);
			SkinVariant variant = skinData.second() == null ? SkinVariant.CLASSIC : skinData.second();
			generated = signMaskedSkin(composedPath.toUri(), variant);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to build masked stalker skin for {}", viewer.getScoreboardName(), exception);
		}

		if (generated != null) {
			MASKED_SKIN_BY_SOURCE_CACHE.put(sourceCacheKey, generated);
			MASKED_SKIN_RETRY_AT_MS.remove(sourceCacheKey);
		} else {
			MASKED_SKIN_RETRY_AT_MS.put(sourceCacheKey, nowMs + MASKED_SKIN_RETRY_COOLDOWN_MS);
		}
		return generated;
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
		private boolean showMasked = false;
		private int togglesRemaining = 0;
		private long nextToggleTick = 0L;

		private void reset() {
			this.wasLooking = false;
			this.showMasked = false;
			this.togglesRemaining = 0;
			this.nextToggleTick = 0L;
		}
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
