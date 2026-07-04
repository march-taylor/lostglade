package com.lostglade.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import xyz.nucleoid.packettweaker.PacketContext;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class DictatorIronTrapdoorBlock extends TrapDoorBlock implements PolymerTexturedBlock {
	private final Map<TrapdoorVisualKey, BlockState> polymerPackStates;
	private final Block fallbackBlock;

	public DictatorIronTrapdoorBlock(BlockBehaviour.Properties settings, Identifier modelBaseId, Block fallbackBlock) {
		super(BlockSetType.IRON, settings);
		this.fallbackBlock = fallbackBlock;
		this.polymerPackStates = createPolymerStates(modelBaseId);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		if (PolymerResourcePackUtils.hasMainPack(context)) {
			BlockState polymerState = this.polymerPackStates.get(TrapdoorVisualKey.of(state));
			if (polymerState != null) {
				return polymerState;
			}
		}
		return copyTrapdoorState(this.fallbackBlock.defaultBlockState(), state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return toggleByHand(state, level, pos, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		return toggleByHand(state, level, pos, player);
	}

	private InteractionResult toggleByHand(BlockState state, Level level, BlockPos pos, Player player) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		boolean open = !state.getValue(OPEN);
		level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		playIronTrapdoorSound(level, null, pos, open);
		level.gameEvent(serverPlayer, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
		return InteractionResult.CONSUME;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean movedByPiston) {
		boolean powered = level.hasNeighborSignal(pos);
		boolean wasOpen = state.getValue(OPEN);
		if (powered != state.getValue(POWERED)) {
			level.setBlock(pos, state.setValue(OPEN, powered).setValue(POWERED, powered), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (powered != wasOpen) {
				playIronTrapdoorSound(level, null, pos, powered);
				level.gameEvent(null, powered ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
			}
		}
	}

	private static void playIronTrapdoorSound(Level level, Player excludedPlayer, BlockPos pos, boolean open) {
		level.playSound(excludedPlayer, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
	}

	private Map<TrapdoorVisualKey, BlockState> createPolymerStates(Identifier modelBaseId) {
		Map<TrapdoorVisualKey, BlockState> states = new HashMap<>();
		for (BlockState state : this.getStateDefinition().getPossibleStates()) {
			TrapdoorVisualKey key = TrapdoorVisualKey.of(state);
			if (states.containsKey(key)) {
				continue;
			}
			PolymerBlockModel model = PolymerBlockModel.of(getModelId(modelBaseId, key), 0, getYRotation(key));
			BlockState polymerState = requestTrapdoorState(key, model);
			if (polymerState == null) {
				throw new IllegalStateException("Unable to allocate polymer dictator iron trapdoor state for " + key);
			}
			states.put(key, polymerState);
		}
		return Map.copyOf(states);
	}

	private static BlockState requestTrapdoorState(TrapdoorVisualKey key, PolymerBlockModel model) {
		try {
			Field creatorField = PolymerBlockResourceUtils.class.getDeclaredField("CREATOR");
			creatorField.setAccessible(true);
			BlockResourceCreator creator = (BlockResourceCreator) creatorField.get(null);
			BlockModelType type = BlockModelType.getTrapdoor(key.modelDirection(), false);
			return creator.requestBlock(type, candidate -> matchesCarrierState(candidate, key), model);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to access Polymer block creator", e);
		}
	}
	private static boolean matchesCarrierState(BlockState state, TrapdoorVisualKey key) {
		// Iron trapdoor carriers do not send hand-open interaction from the client.
		return state.is(Blocks.OAK_TRAPDOOR)
				&& state.getValue(FACING) == key.facing
				&& state.getValue(HALF) == key.half
				&& state.getValue(OPEN) == key.open
				&& !state.getValue(WATERLOGGED);
	}



	private static BlockState copyTrapdoorState(BlockState target, BlockState source) {
		return target
				.setValue(FACING, source.getValue(FACING))
				.setValue(OPEN, source.getValue(OPEN))
				.setValue(HALF, source.getValue(HALF))
				.setValue(POWERED, source.getValue(POWERED))
				.setValue(WATERLOGGED, source.getValue(WATERLOGGED));
	}

	private static Identifier getModelId(Identifier modelBaseId, TrapdoorVisualKey key) {
		String suffix = key.open ? "open" : (key.half == Half.TOP ? "top" : "bottom");
		return Identifier.fromNamespaceAndPath(modelBaseId.getNamespace(), modelBaseId.getPath() + "_" + suffix);
	}

	private static int getYRotation(TrapdoorVisualKey key) {
		if (!key.open) {
			return 0;
		}
		return switch (key.facing) {
			case NORTH -> 0;
			case EAST -> 90;
			case SOUTH -> 180;
			case WEST -> 270;
			default -> throw new IllegalArgumentException("Only horizontal trapdoor directions are supported");
		};
	}

	private record TrapdoorVisualKey(Direction facing, Half half, boolean open) {
		private static TrapdoorVisualKey of(BlockState state) {
			return new TrapdoorVisualKey(
					state.getValue(FACING),
					state.getValue(HALF),
					state.getValue(OPEN)
			);
		}

		private Direction modelDirection() {
			if (this.open) {
				return this.facing;
			}
			return this.half == Half.TOP ? Direction.DOWN : Direction.UP;
		}
	}
}