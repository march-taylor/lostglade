package com.lostglade.server;

import com.lostglade.block.ModBlocks;
import com.lostglade.block.ExitSignSoundHelper;
import com.lostglade.item.ModItems;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerBackroomsBlockBreakSystem {
	private static final long EXIT_SIGN_HIT_SOUND_INTERVAL_TICKS = 4L;
	private static final Map<UUID, ExitSignHitState> LAST_EXIT_SIGN_HIT = new HashMap<>();

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
			if (!isProtectedBackroomsBlock(state) && !isProtectedBackroomsUtility(state)) {
				return InteractionResult.PASS;
			}
			if (serverPlayer.isCreative()) {
				return InteractionResult.PASS;
			}
			if (serverPlayer.isSpectator() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (!isSpecialPickaxe(serverPlayer)) {
				resetDeniedBreakFeedback(serverPlayer, (ServerLevel) world, pos, state);
				return InteractionResult.FAIL;
			}
			if (world instanceof ServerLevel serverLevel && isExitSign(state) && shouldPlayExitSignHitSound(serverPlayer, pos)) {
				ExitSignSoundHelper.playHitSound(serverLevel, pos);
			}

			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!isProtectedBackroomsBlock(state) && !isProtectedBackroomsUtility(state)) {
				return true;
			}
			if (!(player instanceof ServerPlayer serverPlayer)) {
				return false;
			}
			if (serverPlayer.isCreative()) {
				return true;
			}
			boolean allowed = isSpecialPickaxe(serverPlayer);
			if (!allowed && world instanceof ServerLevel serverLevel) {
				resetDeniedBreakFeedback(serverPlayer, serverLevel, pos, state);
			}
			return allowed;
		});

		PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
				return;
			}
			if (!isProtectedBackroomsBlock(state) && !isProtectedBackroomsUtility(state)) {
				return;
			}
			resetDeniedBreakFeedback(serverPlayer, serverLevel, pos, state);
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerLevel serverLevel)) {
				return;
			}
			if (!state.is(ModBlocks.EXIT_SIGN) && !state.is(ModBlocks.EXIT_WALL_SIGN)) {
				return;
			}
			ExitSignSoundHelper.playBreakSound(serverLevel, pos);
		});
	}

	private static boolean isProtectedBackroomsBlock(BlockState state) {
		return state.is(ModBlocks.BACKROOMS_BLOCK) || state.is(ModBlocks.BACKROOMS_LIGHTBLOCK);
	}

	private static boolean isProtectedBackroomsUtility(BlockState state) {
		return state.is(ModBlocks.BACKROOMS_DOOR)
				|| state.is(ModBlocks.EXIT_SIGN)
				|| state.is(ModBlocks.EXIT_WALL_SIGN);
	}

	private static boolean isExitSign(BlockState state) {
		return state.is(ModBlocks.EXIT_SIGN) || state.is(ModBlocks.EXIT_WALL_SIGN);
	}

	private static boolean isSpecialPickaxe(ServerPlayer player) {
		return player.getMainHandItem().is(ModItems.SPECIAL_PICKAXE);
	}

	private static boolean shouldPlayExitSignHitSound(ServerPlayer player, net.minecraft.core.BlockPos pos) {
		long tick = ((ServerLevel) player.level()).getGameTime();
		UUID uuid = player.getUUID();
		ExitSignHitState previous = LAST_EXIT_SIGN_HIT.get(uuid);
		if (previous != null && previous.pos().equals(pos) && tick - previous.tick() < EXIT_SIGN_HIT_SOUND_INTERVAL_TICKS) {
			return false;
		}
		LAST_EXIT_SIGN_HIT.put(uuid, new ExitSignHitState(pos.immutable(), tick));
		return true;
	}

	private static void resetDeniedBreakFeedback(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos, BlockState state) {
		level.destroyBlockProgress(player.getId(), pos, -1);
		player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (blockEntity != null) {
			Packet<?> packet = blockEntity.getUpdatePacket();
			if (packet != null) {
				player.connection.send(packet);
			}
		}
	}

	private record ExitSignHitState(net.minecraft.core.BlockPos pos, long tick) {
	}
}
