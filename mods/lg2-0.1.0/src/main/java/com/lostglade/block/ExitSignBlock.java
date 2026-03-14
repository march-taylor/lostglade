package com.lostglade.block;

import com.mojang.serialization.MapCodec;
import eu.pb4.polymer.core.api.block.PolymerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

public final class ExitSignBlock extends StandingSignBlock implements PolymerBlock {
	public static final MapCodec<StandingSignBlock> CODEC = simpleCodec(ExitSignBlock::new);
	private static final Component FIXED_MESSAGE = Component.literal("EXIT ->");

	public ExitSignBlock(BlockBehaviour.Properties settings) {
		super(WoodType.PALE_OAK, settings);
	}

	@Override
	public MapCodec<StandingSignBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return Blocks.PALE_OAK_SIGN.defaultBlockState()
				.setValue(ROTATION, state.getValue(ROTATION))
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

	public static void applyFixedText(SignBlockEntity sign) {
		if (sign.getLevel() == null) {
			return;
		}

		SignText currentFront = sign.getFrontText();
		if (isFixedText(currentFront) && sign.isWaxed()) {
			return;
		}

		SignText text = new SignText()
				.setMessage(0, Component.empty())
				.setMessage(1, FIXED_MESSAGE)
				.setMessage(2, Component.empty())
				.setMessage(3, Component.empty())
				.setColor(DyeColor.GREEN)
				.setHasGlowingText(true);
		sign.setText(text, true);
		sign.setText(text, false);
		sign.setWaxed(true);
		sign.setChanged();
	}

	private static boolean isFixedText(SignText text) {
		return text.getMessage(0, false).getString().isEmpty()
				&& text.getMessage(1, false).getString().equals(FIXED_MESSAGE.getString())
				&& text.getMessage(2, false).getString().isEmpty()
				&& text.getMessage(3, false).getString().isEmpty()
				&& text.getColor() == DyeColor.GREEN
				&& text.hasGlowingText();
	}

	static <T extends BlockEntity> void tickExitSign(Level level, BlockPos pos, BlockState state, T blockEntity) {
		if (blockEntity instanceof SignBlockEntity sign) {
			applyFixedText(sign);
			if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				long gameTime = serverLevel.getGameTime();
				if ((gameTime + pos.asLong()) % 20L == 0L) {
					ExitSignDisplayHelper.ensureDisplay(serverLevel, pos, state);
				}
			}
			SignBlockEntity.tick(level, pos, state, sign);
		}
	}

	private static void removeDisplay(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
		if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			ExitSignDisplayHelper.remove(serverLevel, pos);
		}
	}
}
