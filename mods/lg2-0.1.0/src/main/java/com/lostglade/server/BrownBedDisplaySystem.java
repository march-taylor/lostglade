package com.lostglade.server;

import com.lostglade.block.RainbowBedDisplayHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BrownBedDisplaySystem {
	private static final int MAX_PENDING_CHECKS_PER_TICK = 128;
	private static final Set<BrownBedKey> PENDING_BEDS = new LinkedHashSet<>();

	private BrownBedDisplaySystem() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register(BrownBedDisplaySystem::onChunkLoad);
		ServerTickEvents.END_SERVER_TICK.register(BrownBedDisplaySystem::tickPendingDisplays);
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel serverLevel && isBrownBed(state)) {
				removePendingBed(serverLevel, pos, state);
				RainbowBedDisplayHelper.removeBrown(serverLevel, pos, state);
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel serverLevel && isBrownBed(state)) {
				removePendingBed(serverLevel, pos, state);
				RainbowBedDisplayHelper.removeBrown(serverLevel, pos, state);
			}
		});
		PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) -> {
			if (world instanceof ServerLevel serverLevel && isBrownBed(state)) {
				RainbowBedDisplayHelper.ensureBrownDisplay(serverLevel, pos, state);
			}
		});
	}

	public static void onPotentialBrownBedPlacement(Level level, BlockPos origin) {
		if (!(level instanceof ServerLevel serverLevel) || origin == null) {
			return;
		}

		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(2, 1, 2))) {
			BlockState state = serverLevel.getBlockState(pos);
			if (isBrownBed(state)) {
				RainbowBedDisplayHelper.ensureBrownDisplay(serverLevel, pos, state);
			}
		}
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
			BlockPos pos = blockEntity.getBlockPos();
			BlockState state = chunk.getBlockState(pos);
			if (isBrownBed(state)) {
				queueBrownBed(level, pos, state);
			}
		}
	}

	private static void tickPendingDisplays(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getGameTime() % 20L == 0L) {
				RainbowBedDisplayHelper.cleanupOrphanDisplays(level);
			}
		}
		if (PENDING_BEDS.isEmpty()) {
			return;
		}
		Iterator<BrownBedKey> iterator = PENDING_BEDS.iterator();
		int checked = 0;
		while (iterator.hasNext() && checked++ < MAX_PENDING_CHECKS_PER_TICK) {
			BrownBedKey key = iterator.next();
			ServerLevel level = server.getLevel(key.dimension());
			if (level == null) {
				iterator.remove();
				continue;
			}
			BlockState state = getLoadedBlockState(level, key.pos());
			if (state == null) {
				continue;
			}
			if (!isBrownBed(state)) {
				iterator.remove();
				continue;
			}
			if (!isBedPairLoaded(level, key.pos(), state)) {
				continue;
			}
			RainbowBedDisplayHelper.ensureBrownDisplay(level, key.pos(), state);
			iterator.remove();
		}
	}

	private static void queueBrownBed(ServerLevel level, BlockPos pos, BlockState state) {
		PENDING_BEDS.add(new BrownBedKey(level.dimension(), footPos(pos.immutable(), state)));
	}

	private static void removePendingBed(ServerLevel level, BlockPos pos, BlockState state) {
		PENDING_BEDS.remove(new BrownBedKey(level.dimension(), footPos(pos.immutable(), state)));
	}

	private static boolean isBedPairLoaded(ServerLevel level, BlockPos pos, BlockState state) {
		BlockPos otherPos = state.getValue(BedBlock.PART) == BedPart.FOOT
				? pos.relative(state.getValue(BedBlock.FACING))
				: pos.relative(state.getValue(BedBlock.FACING).getOpposite());
		return getLoadedBlockState(level, otherPos) != null;
	}

	private static BlockState getLoadedBlockState(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(
				SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getZ())
		);
		return chunk == null ? null : chunk.getBlockState(pos);
	}

	private static BlockPos footPos(BlockPos pos, BlockState state) {
		if (state.getValue(BedBlock.PART) == BedPart.FOOT) {
			return pos;
		}
		return pos.relative(state.getValue(BedBlock.FACING).getOpposite());
	}

	private static boolean isBrownBed(BlockState state) {
		return state.is(Blocks.BROWN_BED);
	}

	private record BrownBedKey(
			ResourceKey<Level> dimension,
			BlockPos pos
	) {
	}
}
