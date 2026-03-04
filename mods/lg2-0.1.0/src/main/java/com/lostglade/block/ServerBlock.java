package com.lostglade.block;

import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
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

public class ServerBlock extends SimplePolymerBlock {
	public ServerBlock(BlockBehaviour.Properties settings) {
		super(settings, Blocks.BARRIER);
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
		container.setItem(13, new ItemStack(Items.CHEST));

		serverPlayer.openMenu(new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> ChestMenu.threeRows(syncId, inventory, container),
				Component.literal("Server")
		));
		serverPlayer.sendSystemMessage(Component.literal("Test message from server block"));

		return InteractionResult.CONSUME;
	}
}
