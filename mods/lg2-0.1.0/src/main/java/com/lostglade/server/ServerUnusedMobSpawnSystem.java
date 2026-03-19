package com.lostglade.server;

import com.lostglade.config.Lg2Config;
import com.lostglade.mixin.RabbitAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;

public final class ServerUnusedMobSpawnSystem {
	private static final EntitySpawnReason NATURAL_SPAWN_REASON = EntitySpawnReason.NATURAL;
	private static final int MAX_CANDIDATE_ATTEMPTS = 12;
	private static final int PACK_SPREAD_RADIUS = 6;
	private static final int DEFAULT_NEARBY_SCAN_RADIUS = 48;
	private static final boolean NATURAL_SPAWN_ENABLED = false;
	private static final int NATURAL_SPAWN_INTERVAL_TICKS = 20;
	private static final int NATURAL_SPAWN_ATTEMPTS_PER_INTERVAL = 1;
	private static final int NATURAL_SPAWN_MIN_DISTANCE_BLOCKS = 24;
	private static final int NATURAL_SPAWN_MAX_DISTANCE_BLOCKS = 96;
	private static final int KILLER_RABBIT_WEIGHT = 0;
	private static final int GIANT_WEIGHT = 0;
	private static final int ILLUSIONER_WEIGHT = 0;
	private static final Set<UUID> NATURAL_ZOMBIE_REPLACEMENT_CANDIDATES = new HashSet<>();

	private static final List<SpawnProfile> PROFILES = List.of(
			new SpawnProfile(
					"killer_rabbit",
					EntityType.RABBIT,
					() -> KILLER_RABBIT_WEIGHT,
					SpawnPlacementTypes.ON_GROUND,
					Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					1,
					1,
					2,
					DEFAULT_NEARBY_SCAN_RADIUS,
					ServerUnusedMobSpawnSystem::createKillerRabbit,
					ServerUnusedMobSpawnSystem::checkKillerRabbitSpawn,
					ServerUnusedMobSpawnSystem::configureKillerRabbit
			),
			new SpawnProfile(
					"giant",
					EntityType.GIANT,
					() -> GIANT_WEIGHT,
					SpawnPlacementTypes.ON_GROUND,
					Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					1,
					1,
					1,
					DEFAULT_NEARBY_SCAN_RADIUS,
					level -> EntityType.GIANT.create(level, NATURAL_SPAWN_REASON),
					ServerUnusedMobSpawnSystem::checkGiantSpawn,
					(level, mob, random, pos) -> {
					}
			),
			new SpawnProfile(
					"illusioner",
					EntityType.ILLUSIONER,
					() -> ILLUSIONER_WEIGHT,
					SpawnPlacementTypes.ON_GROUND,
					Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					1,
					1,
					1,
					DEFAULT_NEARBY_SCAN_RADIUS,
					level -> EntityType.ILLUSIONER.create(level, NATURAL_SPAWN_REASON),
					ServerUnusedMobSpawnSystem::checkIllusionerSpawn,
					(level, mob, random, pos) -> {
					}
			)
	);

	private ServerUnusedMobSpawnSystem() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (world instanceof ServerLevel level) {
				tryReplaceNaturalZombieWithGiant(entity, level);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ServerUnusedMobSpawnSystem::tickServer);
	}

	public static void markNaturalZombieCandidate(Zombie zombie) {
		NATURAL_ZOMBIE_REPLACEMENT_CANDIDATES.add(zombie.getUUID());
	}

	private static void tryReplaceNaturalZombieWithGiant(net.minecraft.world.entity.Entity entity, ServerLevel level) {
		if (ServerBackroomsSystem.isBackrooms(level) || !(entity instanceof Zombie zombie)) {
			return;
		}

		if (zombie.getType() != EntityType.ZOMBIE || !NATURAL_ZOMBIE_REPLACEMENT_CANDIDATES.remove(zombie.getUUID())) {
			return;
		}

		int replacementChance = Math.max(1, Lg2Config.get().giantZombieReplacementChance);
		if (replacementChance > 1 && level.getRandom().nextInt(replacementChance) != 0) {
			return;
		}

		Giant giant = EntityType.GIANT.create(level, NATURAL_SPAWN_REASON);
		if (giant == null) {
			return;
		}

		giant.setPos(zombie.getX(), zombie.getY(), zombie.getZ());
		giant.setYRot(zombie.getYRot());
		giant.setXRot(zombie.getXRot());
		giant.yHeadRot = zombie.yHeadRot;
		giant.yBodyRot = zombie.yBodyRot;
		clearLeavesForGiant(level, giant.getBoundingBox().inflate(0.2D), giant);
		if (!hasSpawnSpaceForGiant(level, giant)) {
			giant.discard();
			return;
		}

		DifficultyInstance difficulty = level.getCurrentDifficultyAt(giant.blockPosition());
		giant.finalizeSpawn(level, difficulty, NATURAL_SPAWN_REASON, null);
		if (!hasSpawnSpaceForGiant(level, giant)) {
			giant.discard();
			return;
		}

		if (level.addFreshEntity(giant)) {
			zombie.discard();
		} else {
			giant.discard();
		}
	}

	private static void tickServer(MinecraftServer server) {
		if (!NATURAL_SPAWN_ENABLED) {
			return;
		}

		int interval = NATURAL_SPAWN_INTERVAL_TICKS;
		if (server.getTickCount() % interval != 0) {
			return;
		}

		List<SpawnProfile> activeProfiles = getActiveProfiles();
		if (activeProfiles.isEmpty()) {
			return;
		}

		for (ServerLevel level : server.getAllLevels()) {
			if (ServerBackroomsSystem.isBackrooms(level)) {
				continue;
			}

			List<ServerPlayer> players = collectEligiblePlayers(level);
			if (players.isEmpty()) {
				continue;
			}

			tickLevel(level, players, activeProfiles);
		}
	}

	private static void tickLevel(
			ServerLevel level,
			List<ServerPlayer> players,
			List<SpawnProfile> activeProfiles
	) {
		RandomSource random = level.getRandom();
		int attempts = NATURAL_SPAWN_ATTEMPTS_PER_INTERVAL;
		for (int attempt = 0; attempt < attempts; attempt++) {
			SpawnProfile profile = pickProfile(activeProfiles, random);
			if (profile == null) {
				return;
			}

			trySpawnProfile(level, players, profile, random);
		}
	}

	private static boolean trySpawnProfile(
			ServerLevel level,
			List<ServerPlayer> players,
			SpawnProfile profile,
			RandomSource random
	) {
		if (level.getDifficulty() == Difficulty.PEACEFUL && (profile.entityType() == EntityType.GIANT || profile.entityType() == EntityType.ILLUSIONER)) {
			return false;
		}

		int minDistance = NATURAL_SPAWN_MIN_DISTANCE_BLOCKS;
		int maxDistance = NATURAL_SPAWN_MAX_DISTANCE_BLOCKS;

		for (int candidateAttempt = 0; candidateAttempt < MAX_CANDIDATE_ATTEMPTS; candidateAttempt++) {
			ServerPlayer anchor = players.get(random.nextInt(players.size()));
			BlockPos spawnPos = findSpawnCandidate(level, anchor, profile, minDistance, maxDistance, random);
			if (spawnPos == null) {
				continue;
			}

			if (countNearbyEntities(level, profile, spawnPos) >= profile.maxNearbyCount()) {
				continue;
			}

			return spawnPack(level, profile, spawnPos, random);
		}

		return false;
	}

	private static BlockPos findSpawnCandidate(
			ServerLevel level,
			ServerPlayer anchor,
			SpawnProfile profile,
			int minDistance,
			int maxDistance,
			RandomSource random
	) {
		double angle = random.nextDouble() * (Math.PI * 2.0D);
		double distance = minDistance >= maxDistance
				? minDistance
				: Mth.lerp(random.nextDouble(), minDistance, maxDistance);
		int x = Mth.floor(anchor.getX() + Math.cos(angle) * distance);
		int z = Mth.floor(anchor.getZ() + Math.sin(angle) * distance);
		BlockPos probePos = new BlockPos(x, anchor.getBlockY(), z);
		if (!level.hasChunkAt(probePos)) {
			return null;
		}

		int surfaceY = level.getHeight(profile.heightmapType(), x, z);
		BlockPos basePos = new BlockPos(x, surfaceY, z);
		BlockPos spawnPos = profile.placementType().adjustSpawnPosition(level, basePos);
		if (!level.hasChunkAt(spawnPos) || spawnPos.getY() <= level.getMinY() || spawnPos.getY() >= level.getMaxY()) {
			return null;
		}

		if (!profile.placementType().isSpawnPositionOk(level, spawnPos, profile.entityType())) {
			return null;
		}

		if (!isFarEnoughFromPlayers(anchor, spawnPos, minDistance)) {
			return null;
		}

		return spawnPos;
	}

	private static boolean spawnPack(ServerLevel level, SpawnProfile profile, BlockPos origin, RandomSource random) {
		SpawnGroupData spawnGroupData = null;
		int desiredGroupSize = profile.sampleGroupSize(random);
		boolean spawnedAny = false;

		for (int index = 0; index < desiredGroupSize; index++) {
			BlockPos memberPos = index == 0 ? origin : findPackMemberPosition(level, profile, origin, random);
			if (memberPos == null) {
				continue;
			}

			Mob mob = profile.factory().create(level);
			if (mob == null) {
				continue;
			}

			float yaw = random.nextFloat() * 360.0F;
			mob.setPos(memberPos.getX() + 0.5D, memberPos.getY(), memberPos.getZ() + 0.5D);
			mob.setYRot(yaw);
			mob.setXRot(0.0F);
			if (!profile.spawnCheck().canSpawn(level, memberPos, mob, random)) {
				mob.discard();
				continue;
			}

			if (!mob.checkSpawnObstruction(level)) {
				mob.discard();
				continue;
			}

			DifficultyInstance difficulty = level.getCurrentDifficultyAt(memberPos);
			spawnGroupData = mob.finalizeSpawn(level, difficulty, NATURAL_SPAWN_REASON, spawnGroupData);
			profile.postSpawn().configure(level, mob, random, memberPos);
			if (!mob.checkSpawnObstruction(level)) {
				mob.discard();
				continue;
			}

			if (level.addFreshEntity(mob)) {
				spawnedAny = true;
			} else {
				mob.discard();
			}
		}

		return spawnedAny;
	}

	private static BlockPos findPackMemberPosition(ServerLevel level, SpawnProfile profile, BlockPos origin, RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			int x = origin.getX() + random.nextInt(PACK_SPREAD_RADIUS * 2 + 1) - PACK_SPREAD_RADIUS;
			int z = origin.getZ() + random.nextInt(PACK_SPREAD_RADIUS * 2 + 1) - PACK_SPREAD_RADIUS;
			BlockPos basePos = new BlockPos(x, level.getHeight(profile.heightmapType(), x, z), z);
			BlockPos spawnPos = profile.placementType().adjustSpawnPosition(level, basePos);
			if (!level.hasChunkAt(spawnPos)) {
				continue;
			}
			if (!profile.placementType().isSpawnPositionOk(level, spawnPos, profile.entityType())) {
				continue;
			}
			return spawnPos;
		}
		return null;
	}

	private static boolean isFarEnoughFromPlayers(ServerPlayer anchor, BlockPos spawnPos, int minDistance) {
		if (minDistance <= 0) {
			return true;
		}

		double centerX = spawnPos.getX() + 0.5D;
		double centerY = spawnPos.getY();
		double centerZ = spawnPos.getZ() + 0.5D;
		return anchor.distanceToSqr(centerX, centerY, centerZ) >= (double) minDistance * minDistance;
	}

	private static int countNearbyEntities(ServerLevel level, SpawnProfile profile, BlockPos center) {
		double radius = profile.nearbyScanRadius();
		AABB scanBox = new AABB(center).inflate(radius);
		return level.getEntities(
				profile.entityType(),
				scanBox,
				entity -> entity != null && entity.isAlive()
		).size();
	}

	private static List<SpawnProfile> getActiveProfiles() {
		List<SpawnProfile> activeProfiles = new ArrayList<>();
		for (SpawnProfile profile : PROFILES) {
			if (profile.weight() > 0) {
				activeProfiles.add(profile);
			}
		}
		return activeProfiles;
	}

	private static SpawnProfile pickProfile(List<SpawnProfile> profiles, RandomSource random) {
		int totalWeight = 0;
		for (SpawnProfile profile : profiles) {
			totalWeight += Math.max(0, profile.weight());
		}
		if (totalWeight <= 0) {
			return null;
		}

		int roll = random.nextInt(totalWeight);
		for (SpawnProfile profile : profiles) {
			roll -= Math.max(0, profile.weight());
			if (roll < 0) {
				return profile;
			}
		}

		return null;
	}

	private static List<ServerPlayer> collectEligiblePlayers(ServerLevel level) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.isAlive() && !player.isSpectator()) {
				players.add(player);
			}
		}
		return players;
	}

	private static Mob createKillerRabbit(ServerLevel level) {
		return EntityType.RABBIT.create(level, NATURAL_SPAWN_REASON);
	}

	private static boolean checkKillerRabbitSpawn(ServerLevel level, BlockPos pos, Mob mob, RandomSource random) {
		if (!(mob instanceof Rabbit rabbit)) {
			return false;
		}
		return Rabbit.checkRabbitSpawnRules(EntityType.RABBIT, level, NATURAL_SPAWN_REASON, pos, random)
				&& rabbit.checkSpawnObstruction(level);
	}

	private static void configureKillerRabbit(ServerLevel level, Mob mob, RandomSource random, BlockPos pos) {
		if (mob instanceof Rabbit rabbit) {
			((RabbitAccessor) rabbit).lg2$setVariant(Rabbit.Variant.EVIL);
		}
	}

	private static boolean checkGiantSpawn(ServerLevel level, BlockPos pos, Mob mob, RandomSource random) {
		return mob instanceof Monster
				&& Monster.checkMonsterSpawnRules(EntityType.GIANT, level, NATURAL_SPAWN_REASON, pos, random)
				&& hasVerticalClearance(level, pos, 12)
				&& hasHorizontalClearance(level, pos, 2);
	}

	private static boolean checkIllusionerSpawn(ServerLevel level, BlockPos pos, Mob mob, RandomSource random) {
		return mob instanceof Monster
				&& Monster.checkMonsterSpawnRules(EntityType.ILLUSIONER, level, NATURAL_SPAWN_REASON, pos, random)
				&& hasVerticalClearance(level, pos, 3);
	}

	private static boolean hasVerticalClearance(ServerLevel level, BlockPos pos, int blocks) {
		for (int dy = 0; dy < blocks; dy++) {
			BlockPos checkPos = pos.above(dy);
			if (!level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasHorizontalClearance(ServerLevel level, BlockPos center, int radius) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				BlockPos checkPos = center.offset(dx, 0, dz);
				if (!level.getBlockState(checkPos).getCollisionShape(level, checkPos).isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	private static void clearLeavesForGiant(ServerLevel level, AABB area, net.minecraft.world.entity.Entity breaker) {
		BlockPos.betweenClosed(
				Mth.floor(area.minX),
				Mth.floor(area.minY),
				Mth.floor(area.minZ),
				Mth.floor(area.maxX),
				Mth.floor(area.maxY),
				Mth.floor(area.maxZ)
		).forEach(pos -> {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof LeavesBlock) {
				level.destroyBlock(pos, true, breaker);
			}
		});
	}

	private static boolean hasSpawnSpaceForGiant(ServerLevel level, Giant giant) {
		AABB box = giant.getBoundingBox();
		if (level.containsAnyLiquid(box)) {
			return false;
		}

		VoxelShape giantShape = Shapes.create(box);
		int minX = Mth.floor(box.minX + 1.0E-7D);
		int minY = Mth.floor(box.minY + 1.0E-7D);
		int minZ = Mth.floor(box.minZ + 1.0E-7D);
		int maxX = Mth.floor(box.maxX - 1.0E-7D);
		int maxY = Mth.floor(box.maxY - 1.0E-7D);
		int maxZ = Mth.floor(box.maxZ - 1.0E-7D);
		for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
			BlockState state = level.getBlockState(pos);
			VoxelShape collisionShape = state.getCollisionShape(level, pos);
			if (collisionShape.isEmpty()) {
				continue;
			}

			VoxelShape shiftedShape = collisionShape.move(pos.getX(), pos.getY(), pos.getZ());
			if (Shapes.joinIsNotEmpty(shiftedShape, giantShape, BooleanOp.AND)) {
				return false;
			}
		}

		return true;
	}

	@FunctionalInterface
	private interface MobFactory {
		Mob create(ServerLevel level);
	}

	@FunctionalInterface
	private interface SpawnCheck {
		boolean canSpawn(ServerLevel level, BlockPos pos, Mob mob, RandomSource random);
	}

	@FunctionalInterface
	private interface PostSpawn {
		void configure(ServerLevel level, Mob mob, RandomSource random, BlockPos pos);
	}

	private record SpawnProfile(
			String key,
			EntityType<? extends Mob> entityType,
			IntSupplier weightSupplier,
			SpawnPlacementType placementType,
			Heightmap.Types heightmapType,
			int minGroupSize,
			int maxGroupSize,
			int maxNearbyCount,
			int nearbyScanRadius,
			MobFactory factory,
			SpawnCheck spawnCheck,
			PostSpawn postSpawn
	) {
		int weight() {
			return Math.max(0, this.weightSupplier.getAsInt());
		}

		int sampleGroupSize(RandomSource random) {
			if (this.maxGroupSize <= this.minGroupSize) {
				return this.minGroupSize;
			}
			return this.minGroupSize + random.nextInt(this.maxGroupSize - this.minGroupSize + 1);
		}
	}
}
