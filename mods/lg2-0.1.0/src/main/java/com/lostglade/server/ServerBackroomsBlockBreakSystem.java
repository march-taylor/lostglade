package com.lostglade.server;

import com.lostglade.block.ModBlocks;
import com.lostglade.item.ModItems;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

public final class ServerBackroomsBlockBreakSystem {
	private ServerBackroomsBlockBreakSystem() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClientSide()) {
				return InteractionResult.PASS;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}

			BlockState state = world.getBlockState(pos);
			if (!state.is(ModBlocks.BACKROOMS_BLOCK)) {
				return InteractionResult.PASS;
			}
			if (serverPlayer.isCreative()) {
				return InteractionResult.PASS;
			}
			if (serverPlayer.isSpectator() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!serverPlayer.getMainHandItem().is(ModItems.SPECIAL_PICKAXE)) {
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!state.is(ModBlocks.BACKROOMS_BLOCK)) {
				return true;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return false;
			}
			if (serverPlayer.isCreative()) {
				return true;
			}
			return serverPlayer.getMainHandItem().is(ModItems.SPECIAL_PICKAXE);
		});
	}
}
