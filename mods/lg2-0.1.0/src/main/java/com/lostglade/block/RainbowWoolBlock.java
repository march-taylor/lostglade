package com.lostglade.block;

import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.packettweaker.PacketContext;

public final class RainbowWoolBlock extends SimplePolymerBlock implements PolymerTexturedBlock {
	private final BlockState polymerPackState;
	private final BlockState fallbackState;

	public RainbowWoolBlock(BlockBehaviour.Properties settings, Identifier modelId) {
		super(settings, Blocks.WHITE_WOOL);
		this.polymerPackState = BackroomsBlock.requestTargetState(modelId, Blocks.WHITE_WOOL);
		this.fallbackState = Blocks.WHITE_WOOL.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context)
				? this.polymerPackState
				: this.fallbackState;
	}

	@Override
	protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
		level.levelEvent(player, 2001, pos, Block.getId(Blocks.WHITE_WOOL.defaultBlockState()));
	}
}
