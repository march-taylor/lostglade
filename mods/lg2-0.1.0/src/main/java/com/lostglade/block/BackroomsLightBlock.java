package com.lostglade.block;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public final class BackroomsLightBlock extends BackroomsBlock {
	public BackroomsLightBlock(
			BlockBehaviour.Properties settings,
			Identifier modelId,
			Block preferredPolymerBlock,
			Block fallbackBlock
	) {
		super(settings, modelId, preferredPolymerBlock, fallbackBlock);
	}

	@Override
	public boolean forceLightUpdates(BlockState blockState) {
		return true;
	}
}
