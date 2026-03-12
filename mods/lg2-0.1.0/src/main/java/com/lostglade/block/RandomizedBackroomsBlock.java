package com.lostglade.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import xyz.nucleoid.packettweaker.PacketContext;

public final class RandomizedBackroomsBlock extends BackroomsBlock {
	public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 4);
	private static final int WEIGHT_SCALE = 40;
	private static final int COMMON_VARIANT_WEIGHT = 36;

	private final BlockState[] polymerPackStates;

	public RandomizedBackroomsBlock(
			BlockBehaviour.Properties settings,
			Identifier[] modelIds,
			Block preferredPolymerBlock,
			Block fallbackBlock
	) {
		super(settings, modelIds[0], preferredPolymerBlock, fallbackBlock);
		if (modelIds.length != 5) {
			throw new IllegalArgumentException("RandomizedBackroomsBlock requires exactly 5 model ids");
		}

		this.polymerPackStates = new BlockState[modelIds.length];
		for (int i = 0; i < modelIds.length; i++) {
			this.polymerPackStates[i] = requestTargetState(modelIds[i], preferredPolymerBlock);
		}

		this.registerDefaultState(this.defaultBlockState().setValue(VARIANT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(VARIANT);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		if (!eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(context)) {
			return this.fallbackState;
		}

		int variant = state.hasProperty(VARIANT) ? state.getValue(VARIANT) : 0;
		if (variant < 0 || variant >= this.polymerPackStates.length) {
			variant = 0;
		}
		return this.polymerPackStates[variant];
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.getRandomizedState(context.getClickedPos().asLong() ^ context.getLevel().getGameTime());
	}

	public BlockState getRandomizedState(long seed) {
		return this.defaultBlockState().setValue(VARIANT, pickWeightedVariant(seed));
	}

	public BlockState getRandomizedState(RandomSource random) {
		return this.defaultBlockState().setValue(VARIANT, pickWeightedVariant(random.nextInt(WEIGHT_SCALE)));
	}

	public BlockState getRandomizedState(BlockPos pos) {
		return this.getRandomizedState(pos.asLong());
	}

	private static int pickWeightedVariant(long seed) {
		return pickWeightedVariant(Math.floorMod(mixSeed(seed), WEIGHT_SCALE));
	}

	private static int pickWeightedVariant(int weightedRoll) {
		if (weightedRoll < COMMON_VARIANT_WEIGHT) {
			return 0;
		}
		return 1 + (weightedRoll - COMMON_VARIANT_WEIGHT);
	}

	private static long mixSeed(long seed) {
		long mixed = seed;
		mixed ^= (mixed >>> 33);
		mixed *= 0xff51afd7ed558ccdl;
		mixed ^= (mixed >>> 33);
		mixed *= 0xc4ceb9fe1a85ec53l;
		mixed ^= (mixed >>> 33);
		return mixed;
	}
}
