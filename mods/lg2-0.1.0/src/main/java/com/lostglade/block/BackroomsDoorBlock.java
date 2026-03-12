package com.lostglade.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import xyz.nucleoid.packettweaker.PacketContext;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class BackroomsDoorBlock extends DoorBlock implements PolymerTexturedBlock {
	private static final BlockModelType[] DOOR_MODEL_TYPES = new BlockModelType[] {
			BlockModelType.NORTH_DOOR,
			BlockModelType.EAST_DOOR,
			BlockModelType.SOUTH_DOOR,
			BlockModelType.WEST_DOOR
	};

	private final Map<DoorVisualKey, BlockState> polymerPackStates;
	private final Block fallbackBlock;

	public BackroomsDoorBlock(
			BlockSetType blockSetType,
			BlockBehaviour.Properties settings,
			Identifier modelBaseId,
			Block fallbackBlock
	) {
		super(blockSetType, settings);
		this.fallbackBlock = fallbackBlock;
		this.polymerPackStates = createPolymerStates(modelBaseId);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		if (PolymerResourcePackUtils.hasMainPack(context)) {
			BlockState polymerState = this.polymerPackStates.get(DoorVisualKey.of(state));
			if (polymerState != null) {
				return polymerState;
			}
		}

		return copyDoorState(this.fallbackBlock.defaultBlockState(), state);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		return state.getValue(HALF) == DoubleBlockHalf.LOWER
				? List.of(new ItemStack(this))
				: List.of();
	}

	private Map<DoorVisualKey, BlockState> createPolymerStates(Identifier modelBaseId) {
		EnumMap<DoorVisualKey, BlockState> states = new EnumMap<>(DoorVisualKey.class);
		for (BlockState state : this.getStateDefinition().getPossibleStates()) {
			DoorVisualKey key = DoorVisualKey.of(state);
			if (states.containsKey(key)) {
				continue;
			}

			PolymerBlockModel model = PolymerBlockModel.of(getModelId(modelBaseId, key), 0, getYRotation(key));
			BlockState polymerState = requestDoorState(key, model);
			if (polymerState == null) {
				throw new IllegalStateException("Unable to allocate polymer door state for " + key);
			}

			states.put(key, polymerState);
		}

		return Map.copyOf(states);
	}

	private static BlockState requestDoorState(DoorVisualKey key, PolymerBlockModel model) {
		try {
			Field creatorField = PolymerBlockResourceUtils.class.getDeclaredField("CREATOR");
			creatorField.setAccessible(true);
			BlockResourceCreator creator = (BlockResourceCreator) creatorField.get(null);

			for (BlockModelType type : DOOR_MODEL_TYPES) {
				BlockState state = creator.requestBlock(type, candidate -> matchesVisualState(candidate, key), model);
				if (state != null) {
					return state;
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to access Polymer block creator", e);
		}

		return null;
	}

	private static boolean matchesVisualState(BlockState state, DoorVisualKey key) {
		return !state.getValue(POWERED)
				&& state.getValue(FACING) == key.facing
				&& state.getValue(HALF) == key.half
				&& state.getValue(HINGE) == key.hinge
				&& state.getValue(OPEN) == key.open;
	}

	private static BlockState copyDoorState(BlockState target, BlockState source) {
		return target
				.setValue(FACING, source.getValue(FACING))
				.setValue(HALF, source.getValue(HALF))
				.setValue(HINGE, source.getValue(HINGE))
				.setValue(OPEN, source.getValue(OPEN))
				.setValue(POWERED, source.getValue(POWERED));
	}

	private static Identifier getModelId(Identifier modelBaseId, DoorVisualKey key) {
		StringBuilder path = new StringBuilder(modelBaseId.getPath())
				.append('_')
				.append(key.half == DoubleBlockHalf.LOWER ? "bottom" : "top")
				.append('_')
				.append(key.hinge == DoorHingeSide.LEFT ? "left" : "right");
		if (key.open) {
			path.append("_open");
		}

		return Identifier.fromNamespaceAndPath(modelBaseId.getNamespace(), path.toString());
	}

	private static int getYRotation(DoorVisualKey key) {
		Direction direction = key.facing;
		if (key.open) {
			direction = key.hinge == DoorHingeSide.LEFT
					? direction.getClockWise()
					: direction.getCounterClockWise();
		}

		return switch (direction) {
			case EAST -> 0;
			case SOUTH -> 90;
			case WEST -> 180;
			case NORTH -> 270;
			default -> throw new IllegalArgumentException("Only horizontal door directions are supported");
		};
	}

	private enum DoorVisualKey {
		NORTH_LOWER_LEFT_CLOSED(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false),
		NORTH_LOWER_LEFT_OPEN(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true),
		NORTH_LOWER_RIGHT_CLOSED(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false),
		NORTH_LOWER_RIGHT_OPEN(Direction.NORTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true),
		NORTH_UPPER_LEFT_CLOSED(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false),
		NORTH_UPPER_LEFT_OPEN(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true),
		NORTH_UPPER_RIGHT_CLOSED(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false),
		NORTH_UPPER_RIGHT_OPEN(Direction.NORTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true),
		EAST_LOWER_LEFT_CLOSED(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false),
		EAST_LOWER_LEFT_OPEN(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true),
		EAST_LOWER_RIGHT_CLOSED(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false),
		EAST_LOWER_RIGHT_OPEN(Direction.EAST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true),
		EAST_UPPER_LEFT_CLOSED(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false),
		EAST_UPPER_LEFT_OPEN(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true),
		EAST_UPPER_RIGHT_CLOSED(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false),
		EAST_UPPER_RIGHT_OPEN(Direction.EAST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true),
		SOUTH_LOWER_LEFT_CLOSED(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false),
		SOUTH_LOWER_LEFT_OPEN(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true),
		SOUTH_LOWER_RIGHT_CLOSED(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false),
		SOUTH_LOWER_RIGHT_OPEN(Direction.SOUTH, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true),
		SOUTH_UPPER_LEFT_CLOSED(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false),
		SOUTH_UPPER_LEFT_OPEN(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true),
		SOUTH_UPPER_RIGHT_CLOSED(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false),
		SOUTH_UPPER_RIGHT_OPEN(Direction.SOUTH, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true),
		WEST_LOWER_LEFT_CLOSED(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, false),
		WEST_LOWER_LEFT_OPEN(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.LEFT, true),
		WEST_LOWER_RIGHT_CLOSED(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, false),
		WEST_LOWER_RIGHT_OPEN(Direction.WEST, DoubleBlockHalf.LOWER, DoorHingeSide.RIGHT, true),
		WEST_UPPER_LEFT_CLOSED(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, false),
		WEST_UPPER_LEFT_OPEN(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.LEFT, true),
		WEST_UPPER_RIGHT_CLOSED(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, false),
		WEST_UPPER_RIGHT_OPEN(Direction.WEST, DoubleBlockHalf.UPPER, DoorHingeSide.RIGHT, true);

		private final Direction facing;
		private final DoubleBlockHalf half;
		private final DoorHingeSide hinge;
		private final boolean open;

		DoorVisualKey(Direction facing, DoubleBlockHalf half, DoorHingeSide hinge, boolean open) {
			this.facing = facing;
			this.half = half;
			this.hinge = hinge;
			this.open = open;
		}

		private static DoorVisualKey of(BlockState state) {
			Direction facing = state.getValue(FACING);
			DoubleBlockHalf half = state.getValue(HALF);
			DoorHingeSide hinge = state.getValue(HINGE);
			boolean open = state.getValue(OPEN);

			for (DoorVisualKey key : values()) {
				if (key.facing == facing && key.half == half && key.hinge == hinge && key.open == open) {
					return key;
				}
			}

			throw new IllegalArgumentException("Unsupported door state: " + state);
		}
	}
}
