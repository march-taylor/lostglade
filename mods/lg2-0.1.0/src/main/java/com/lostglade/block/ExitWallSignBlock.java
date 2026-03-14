package com.lostglade.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

public final class ExitWallSignBlock extends WallSignBlock implements PolymerBlock {
	public static final MapCodec<WallSignBlock> CODEC = simpleCodec(ExitWallSignBlock::new);

	public ExitWallSignBlock(BlockBehaviour.Properties settings) {
		super(WoodType.PALE_OAK, settings);
	}

	@Override
	public MapCodec<WallSignBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return Blocks.PALE_OAK_WALL_SIGN.defaultBlockState()
				.setValue(FACING, state.getValue(FACING))
				.setValue(WATERLOGGED, state.getValue(WATERLOGGED));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SignBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
		return createTickerHelper(blockEntityType, BlockEntityType.SIGN, ExitSignBlock::tickExitSign);
	}

	@Override
	protected BlockState updateShape(
			BlockState state,
			LevelReader level,
			ScheduledTickAccess scheduledTickAccess,
			BlockPos pos,
			Direction direction,
			BlockPos neighborPos,
			BlockState neighborState,
			RandomSource random
	) {
		BlockState updated = super.updateShape(state, level, scheduledTickAccess, pos, direction, neighborPos, neighborState, random);
		if (!(updated.getBlock() instanceof ExitSignBlock) && !(updated.getBlock() instanceof ExitWallSignBlock)) {
			removeDisplay(level, pos);
		}
		return updated;
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		return List.of(new ItemStack(ModBlocks.EXIT_SIGN_ITEM));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		removeDisplay(level, pos);
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void destroy(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
		removeDisplay(level, pos);
		super.destroy(level, pos, state);
	}

	private static void removeDisplay(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			ExitSignDisplayHelper.remove(serverLevel, pos);
		}
	}

	private static void removeDisplay(LevelReader level, BlockPos pos) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			ExitSignDisplayHelper.remove(serverLevel, pos);
		}
	}
}
