package com.lostglade.server;

import com.lostglade.config.Lg2Config;
import com.lostglade.entity.BackroomsStalkerEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServerBackroomsStalkerSystem {
	private static final int MIN_SPAWN_DISTANCE_BLOCKS = 8;
	private static final int MAX_SPAWN_ATTEMPTS_PER_PLAYER = 48;
	private static final int MAX_VERTICAL_SEARCH = 4;

	private ServerBackroomsStalkerSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ServerBackroomsStalkerSystem::tickServer);
		AttackEntityCallback.EVENT.register(ServerBackroomsStalkerSystem::onAttackEntity);
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (!BackroomsStalkerEntity.isStalker(entity)) {
				return;
			}
			if (!(entity instanceof BackroomsStalkerEntity stalker)
					|| !(world instanceof ServerLevel level)
					|| !ServerBackroomsSystem.isBackrooms(level)) {
				entity.discard();
				return;
			}
			stalker.setInvulnerable(true);
			stalker.setSilent(true);
			stalker.attachPolymerAppearance();
		});
	}

	private static void tickServer(MinecraftServer server) {
		discardOutsideBackrooms(server);

		ServerLevel backrooms = server.getLevel(ServerBackroomsSystem.BACKROOMS_LEVEL);
		if (backrooms == null) {
			return;
		}

		List<ServerPlayer> players = collectEligiblePlayers(backrooms);
		List<BackroomsStalkerEntity> stalkers = collectStalkers(backrooms);
		if (players.isEmpty()) {
			discardAll(stalkers);
			return;
		}

		BackroomsStalkerEntity primary = null;
		for (BackroomsStalkerEntity stalker : stalkers) {
			if (primary == null) {
				primary = stalker;
				continue;
			}
			stalker.discardFromSystem();
		}

		int radiusBlocks = Math.max(16, Lg2Config.get().backroomsEntityRadiusChunks * 16);
		if (primary == null || !primary.isAlive() || primary.isRemoved()) {
			spawnNearPlayers(backrooms, players, radiusBlocks);
			return;
		}

		if (!isWithinPresenceRadius(primary, players, radiusBlocks)) {
			primary.discardFromSystem();
			spawnNearPlayers(backrooms, players, radiusBlocks);
		}
	}

	private static InteractionResult onAttackEntity(
			Player player,
			Level world,
			InteractionHand hand,
			Entity entity,
			EntityHitResult hitResult
	) {
		if (world.isClientSide()) {
			return InteractionResult.PASS;
		}
		return BackroomsStalkerEntity.isStalker(entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	private static void discardOutsideBackrooms(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (ServerBackroomsSystem.isBackrooms(level)) {
				continue;
			}
			for (Entity entity : level.getAllEntities()) {
				if (BackroomsStalkerEntity.isStalker(entity)) {
					entity.discard();
				}
			}
		}
	}

	private static List<ServerPlayer> collectEligiblePlayers(ServerLevel level) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (!ServerBackroomsSystem.isInBackrooms(player)) {
				continue;
			}
			players.add(player);
		}
		return players;
	}

	private static List<BackroomsStalkerEntity> collectStalkers(ServerLevel level) {
		List<BackroomsStalkerEntity> stalkers = new ArrayList<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof BackroomsStalkerEntity stalker) {
				stalkers.add(stalker);
			}
		}
		return stalkers;
	}

	private static void discardAll(List<BackroomsStalkerEntity> stalkers) {
		for (BackroomsStalkerEntity stalker : stalkers) {
			stalker.discardFromSystem();
		}
	}

	private static boolean isWithinPresenceRadius(BackroomsStalkerEntity stalker, List<ServerPlayer> players, int radiusBlocks) {
		double maxDistanceSqr = (double) radiusBlocks * radiusBlocks;
		for (ServerPlayer player : players) {
			if (stalker.distanceToSqr(player) <= maxDistanceSqr) {
				return true;
			}
		}
		return false;
	}

	private static void spawnNearPlayers(ServerLevel level, List<ServerPlayer> players, int radiusBlocks) {
		BackroomsStalkerEntity stalker = BackroomsStalkerEntity.create(level);

		Vec3 spawnPos = findSpawnPosition(level, stalker, players, radiusBlocks);
		if (spawnPos == null) {
			stalker.discard();
			return;
		}

		float yaw = level.random.nextFloat() * 360.0F;
		stalker.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
		stalker.setYRot(yaw);
		stalker.setYHeadRot(yaw);
		stalker.setYBodyRot(yaw);
		stalker.setXRot(0.0F);
		if (!level.noCollision(stalker) || level.containsAnyLiquid(stalker.getBoundingBox())) {
			stalker.discard();
			return;
		}

		level.addFreshEntity(stalker);
	}

	private static Vec3 findSpawnPosition(
			ServerLevel level,
			BackroomsStalkerEntity stalker,
			List<ServerPlayer> players,
			int radiusBlocks
	) {
		List<ServerPlayer> shuffledPlayers = new ArrayList<>(players);
		Collections.shuffle(shuffledPlayers, new java.util.Random(level.getGameTime()));
		RandomSource random = level.random;

		for (ServerPlayer player : shuffledPlayers) {
			for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS_PER_PLAYER; attempt++) {
				double angle = random.nextDouble() * (Math.PI * 2.0D);
				int distance = MIN_SPAWN_DISTANCE_BLOCKS + random.nextInt(Math.max(1, radiusBlocks - MIN_SPAWN_DISTANCE_BLOCKS + 1));
				int x = net.minecraft.util.Mth.floor(player.getX() + (Math.cos(angle) * distance));
				int z = net.minecraft.util.Mth.floor(player.getZ() + (Math.sin(angle) * distance));

				for (int yOffset = MAX_VERTICAL_SEARCH; yOffset >= -MAX_VERTICAL_SEARCH; yOffset--) {
					Vec3 spawnPos = findOpenPosition(level, stalker, x, player.blockPosition().getY() + yOffset, z);
					if (spawnPos != null) {
						return spawnPos;
					}
				}
			}
		}

		return null;
	}

	private static Vec3 findOpenPosition(ServerLevel level, BackroomsStalkerEntity stalker, int x, int y, int z) {
		for (int scanY = y + MAX_VERTICAL_SEARCH; scanY >= y - MAX_VERTICAL_SEARCH; scanY--) {
			BlockPos feetPos = new BlockPos(x, scanY, z);
			if (!canSpawnAt(level, stalker, feetPos)) {
				continue;
			}
			return Vec3.atBottomCenterOf(feetPos);
		}
		return null;
	}

	private static boolean canSpawnAt(ServerLevel level, BackroomsStalkerEntity stalker, BlockPos feetPos) {
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

		stalker.setPos(feetPos.getX() + 0.5D, feetPos.getY(), feetPos.getZ() + 0.5D);
		return level.noCollision(stalker, stalker.getBoundingBox());
	}
}
