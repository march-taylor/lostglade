package com.lostglade.server.map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.Arrays;

public final class BlockTintProvider {
	private static final int BIOME_BLEND_RADIUS = 1;
	private static final TextureAssetManager ASSETS = TextureAssetManager.get();
	private static final int NO_TINT = -1;
	private static final int MAX_TINT_LAYERS = 4;
	private static final int ATTACHED_STEM_COLOR = 0xE0C71C;
	private static final int LILY_PAD_IN_WORLD = 0x208030;
	private static final ColorResolver GRASS_RESOLVER = (biome, x, z) -> biome.getGrassColor(x, z);
	private static final ColorResolver FOLIAGE_RESOLVER = (biome, x, z) -> biome.getFoliageColor();
	private static final ColorResolver DRY_FOLIAGE_RESOLVER = (biome, x, z) -> biome.getDryFoliageColor();
	private static final ColorResolver WATER_RESOLVER = (biome, x, z) -> biome.getWaterColor();
	private static final int[] NO_TINTS = new int[0];

	private BlockTintProvider() {
	}

	public static int[] capture(ServerLevel level, BlockPos pos, BlockState state) {
		if (level == null || pos == null || state == null) {
			return NO_TINTS;
		}
		ASSETS.initializeBiomeColorMaps();

		Block block = state.getBlock();
		int[] tintLayers = new int[MAX_TINT_LAYERS];
		Arrays.fill(tintLayers, NO_TINT);

		if (block == Blocks.LARGE_FERN || block == Blocks.TALL_GRASS) {
			BlockPos tintPos = state.getValue(net.minecraft.world.level.block.DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
					? pos.below()
					: pos;
			tintLayers[0] = averageGrass(level, tintPos);
			return tintLayers;
		}

		if (block == Blocks.GRASS_BLOCK
				|| block == Blocks.FERN
				|| block == Blocks.SHORT_GRASS
				|| block == Blocks.POTTED_FERN
				|| block == Blocks.BUSH) {
			tintLayers[0] = averageGrass(level, pos);
			return tintLayers;
		}

		if (block == Blocks.PINK_PETALS || block == Blocks.WILDFLOWERS) {
			tintLayers[1] = averageGrass(level, pos);
			return tintLayers;
		}

		if (block == Blocks.SPRUCE_LEAVES) {
			tintLayers[0] = FoliageColor.FOLIAGE_EVERGREEN;
			return tintLayers;
		}

		if (block == Blocks.BIRCH_LEAVES) {
			tintLayers[0] = FoliageColor.FOLIAGE_BIRCH;
			return tintLayers;
		}

		if (block == Blocks.LEAF_LITTER) {
			tintLayers[0] = averageDryFoliage(level, pos);
			return tintLayers;
		}

		if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN || block == Blocks.WATER_CAULDRON) {
			tintLayers[0] = averageWater(level, pos);
			return tintLayers;
		}

		if (block == Blocks.REDSTONE_WIRE) {
			tintLayers[0] = RedStoneWireBlock.getColorForPower(state.getValue(RedStoneWireBlock.POWER));
			return tintLayers;
		}

		if (block == Blocks.SUGAR_CANE) {
			tintLayers[0] = averageGrass(level, pos);
			return tintLayers;
		}

		if (block == Blocks.ATTACHED_MELON_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM) {
			tintLayers[0] = ATTACHED_STEM_COLOR;
			return tintLayers;
		}

		if (block == Blocks.MELON_STEM || block == Blocks.PUMPKIN_STEM) {
			int age = state.getValue(StemBlock.AGE);
			tintLayers[0] = ((age * 32) << 16) | ((255 - age * 8) << 8) | (age * 4);
			return tintLayers;
		}

		if (block == Blocks.LILY_PAD) {
			tintLayers[0] = LILY_PAD_IN_WORLD;
			return tintLayers;
		}

		if (block == Blocks.OAK_LEAVES
				|| block == Blocks.JUNGLE_LEAVES
				|| block == Blocks.ACACIA_LEAVES
				|| block == Blocks.DARK_OAK_LEAVES
				|| block == Blocks.VINE
				|| block == Blocks.MANGROVE_LEAVES
				|| block instanceof LeavesBlock) {
			tintLayers[0] = averageFoliage(level, pos);
			return tintLayers;
		}

		return NO_TINTS;
	}

	private static int averageGrass(ServerLevel level, BlockPos pos) {
		return averageBiomeTint(level, pos, GRASS_RESOLVER);
	}

	private static int averageFoliage(ServerLevel level, BlockPos pos) {
		return averageBiomeTint(level, pos, FOLIAGE_RESOLVER);
	}

	private static int averageDryFoliage(ServerLevel level, BlockPos pos) {
		return averageBiomeTint(level, pos, DRY_FOLIAGE_RESOLVER);
	}

	private static int averageWater(ServerLevel level, BlockPos pos) {
		return averageBiomeTint(level, pos, WATER_RESOLVER);
	}

	private static int averageBiomeTint(ServerLevel level, BlockPos pos, ColorResolver resolver) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int red = 0;
		int green = 0;
		int blue = 0;
		int count = 0;
		for (int dz = -BIOME_BLEND_RADIUS; dz <= BIOME_BLEND_RADIUS; dz++) {
			for (int dx = -BIOME_BLEND_RADIUS; dx <= BIOME_BLEND_RADIUS; dx++) {
				cursor.set(pos.getX() + dx, pos.getY(), pos.getZ() + dz);
				int tint = resolver.getColor(level.getBiome(cursor).value(), cursor.getX(), cursor.getZ());
				red += (tint >> 16) & 0xFF;
				green += (tint >> 8) & 0xFF;
				blue += tint & 0xFF;
				count++;
			}
		}
		if (count == 0) {
			return 0;
		}
		return ((red / count) << 16) | ((green / count) << 8) | (blue / count);
	}
}
