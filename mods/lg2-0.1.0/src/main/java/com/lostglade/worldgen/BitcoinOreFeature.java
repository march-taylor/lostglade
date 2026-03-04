package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class BitcoinOreFeature extends Feature<NoneFeatureConfiguration> {
	public BitcoinOreFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		RandomSource random = context.random();
		Lg2Config.ConfigData config = Lg2Config.get();

		int attempts = config.sampleOresPerChunk(random);
		if (attempts <= 0) {
			return false;
		}

		ChunkPos chunkPos = new ChunkPos(context.origin());
		boolean anyPlaced = false;

		for (int attempt = 0; attempt < attempts; attempt++) {
			int x = chunkPos.getMinBlockX() + random.nextInt(16);
			int y = config.sampleSpawnY(random);
			int z = chunkPos.getMinBlockZ() + random.nextInt(16);
			int veinSize = config.sampleVeinSize(random);

			if (veinSize <= 0) {
				continue;
			}

			if (placeSingleVein(level, random, new BlockPos(x, y, z), veinSize)) {
				anyPlaced = true;
			}
		}

		return anyPlaced;
	}

	private static boolean placeSingleVein(WorldGenLevel level, RandomSource random, BlockPos origin, int veinSize) {
		BlockPos.MutableBlockPos cursor = origin.mutable();
		int currentX = origin.getX();
		int currentY = origin.getY();
		int currentZ = origin.getZ();

		int placedCount = 0;
		int maxSteps = Math.max(veinSize * 3, veinSize);

		for (int step = 0; step < maxSteps && placedCount < veinSize; step++) {
			if (step > 0) {
				currentX += random.nextInt(3) - 1;
				currentY += random.nextInt(3) - 1;
				currentZ += random.nextInt(3) - 1;
			}

			if (level.isOutsideBuildHeight(currentY)) {
				continue;
			}

			cursor.set(currentX, currentY, currentZ);
			BlockState currentState = level.getBlockState(cursor);
			BlockState targetState = null;

			if (currentState.is(BlockTags.STONE_ORE_REPLACEABLES)) {
				targetState = ModBlocks.BITCOIN_ORE.defaultBlockState();
			} else if (currentState.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
				targetState = ModBlocks.DEEPSLATE_BITCOIN_ORE.defaultBlockState();
			}

			if (targetState != null) {
				level.setBlock(cursor, targetState, Block.UPDATE_CLIENTS);
				placedCount++;
			}
		}

		return placedCount > 0;
	}
}
