package com.lostglade.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.packettweaker.PacketContext;

public final class RainbowCarpetBlock extends CarpetBlock implements PolymerBlock, PolymerTexturedBlock {
	private final BlockState polymerPackState;
	private final BlockState fallbackState;

	public RainbowCarpetBlock(BlockBehaviour.Properties settings, Identifier modelId) {
		super(settings);
		this.polymerPackState = PolymerBlockResourceUtils.requestBlock(
				BlockModelType.ACTIVE_PRESSURE_PLATE,
				PolymerBlockModel.of(modelId)
		);
		this.fallbackState = Blocks.WHITE_CARPET.defaultBlockState();
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context)
				? this.polymerPackState
				: this.fallbackState;
	}

	@Override
	public BlockState getPolymerBreakEventBlockState(BlockState state, PacketContext context) {
		return this.fallbackState;
	}

	@Override
	protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
		level.levelEvent(player, 2001, pos, Block.getId(Blocks.WHITE_CARPET.defaultBlockState()));
	}
}
