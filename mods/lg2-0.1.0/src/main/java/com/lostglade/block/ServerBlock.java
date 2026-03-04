package com.lostglade.block;

import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import xyz.nucleoid.packettweaker.PacketContext;

public class ServerBlock extends SimplePolymerBlock {
	public ServerBlock(BlockBehaviour.Properties settings) {
		super(settings, Blocks.COMMAND_BLOCK);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context)
				? Blocks.BARRIER.defaultBlockState()
				: Blocks.COMMAND_BLOCK.defaultBlockState();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return this.openServerMenu(level, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		return this.openServerMenu(level, player);
	}

	private InteractionResult openServerMenu(Level level, Player player) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		SimpleContainer container = new SimpleContainer(27);
		container.setItem(13, new ItemStack(Items.COMMAND_BLOCK));

		serverPlayer.openMenu(new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> ChestMenu.threeRows(syncId, inventory, container),
				Component.literal("Server")
		));

		return InteractionResult.CONSUME;
	}
}
