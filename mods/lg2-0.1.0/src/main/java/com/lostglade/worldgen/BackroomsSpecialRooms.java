package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class BackroomsSpecialRooms {
	private static final long BACKROOMS_VARIANT_SALT = 0x4c47324241434b52L;
	private static final long STAIRS_LAYOUT_SALT = 0x535441495253524DL;
	private static final BlockState BACKROOMS_DOOR_BLOCK = ModBlocks.BACKROOMS_DOOR.defaultBlockState();
	private static final BlockState EXIT_SIGN_WALL_BLOCK = ModBlocks.EXIT_WALL_SIGN.defaultBlockState();
	private static final BlockState AIR = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

	private BackroomsSpecialRooms() {
	}

	static ColumnStates createBaseStates(
			BackroomsLayout.DoorPlacement doorPlacement,
			boolean corridor,
			boolean ceilingLight,
			int x,
			int z,
			int floorY,
			int ceilingY,
			Direction exitSignFacing,
			BlockState lightBlock,
			BlockState air
	) {
		BlockState floor = randomizedBackroomsBlock(x, floorY, z);
		BlockState lower = doorPlacement != null
				? createDoorState(doorPlacement, DoubleBlockHalf.LOWER)
				: (corridor ? air : randomizedBackroomsBlock(x, floorY + 1, z));
		BlockState upper = doorPlacement != null
				? createDoorState(doorPlacement, DoubleBlockHalf.UPPER)
				: (corridor ? air : randomizedBackroomsBlock(x, floorY + 2, z));
		BlockState top = exitSignFacing != null
				? createExitSignState(exitSignFacing)
				: (corridor ? air : randomizedBackroomsBlock(x, floorY + 3, z));
		BlockState ceiling = ceilingLight ? lightBlock : randomizedBackroomsBlock(x, ceilingY, z);
		return new ColumnStates(floor, lower, upper, top, ceiling);
	}

	static void applyColumnOverrides(
			BackroomsLayout.SpecialRoomPlacement specialRoom,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		int levelIndex = BackroomsLayout.getLevelIndex(floorY);
		if (specialRoom != null && specialRoom.type() == BackroomsLayout.SpecialRoomType.STAIRS) {
			applyStairsLowerLevel(buildStairsProfile(specialRoom, levelIndex), x, z, floorY, ceilingY, columnStates);
			return;
		}

		StairsProfile lowerProfile = getStairsProfileAt(x, z, levelIndex - 1);
		if (lowerProfile != null) {
			applyStairsUpperLevel(lowerProfile, x, z, floorY, ceilingY, columnStates);
			return;
		}

		StairsProfile upperProfile = getStairsProfileAt(x, z, levelIndex + 1);
		if (upperProfile != null) {
			applyStairsReceivingShaft(upperProfile, x, z, floorY, ceilingY, columnStates);
		}
	}

	private static StairsProfile getStairsProfileAt(int x, int z, int levelIndex) {
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		BackroomsLayout.SpecialRoomPlacement placement = BackroomsLayout.getSpecialRoomAt(zone, x, z, levelIndex);
		if (placement == null || placement.type() != BackroomsLayout.SpecialRoomType.STAIRS) {
			return null;
		}

		return buildStairsProfile(placement, levelIndex);
	}

	private static StairsProfile buildStairsProfile(BackroomsLayout.SpecialRoomPlacement placement, int levelIndex) {
		long sample = mix(placement.cellX(), placement.cellZ(), STAIRS_LAYOUT_SALT ^ levelSalt(levelIndex));
		boolean xAxis = placement.roomHalfWidth() > placement.roomHalfHeight()
				|| (placement.roomHalfWidth() == placement.roomHalfHeight() && ((sample >>> 1) & 1L) == 0L);
		int forwardStep = ((sample >>> 2) & 1L) == 0L ? 1 : -1;
		int width = 4 + positiveMod(sample ^ 0x1D73AB91L, 2);
		BrokenStairsVariant variant = BrokenStairsVariant.NORMAL;
		if (positiveMod(sample ^ 0x51EAC83A7B92D4C1L, 100) < 20) {
			BrokenStairsVariant[] brokenVariants = BrokenStairsVariant.BROKEN_VALUES;
			variant = brokenVariants[positiveMod(sample ^ 0x24F1A9B77C3265D3L, brokenVariants.length)];
		}

		int turnStepX = 0;
		int turnStepZ = 0;
		if (variant == BrokenStairsVariant.TWISTED) {
			boolean clockwise = ((sample >>> 7) & 1L) == 0L;
			if (xAxis) {
				turnStepZ = clockwise ? 1 : -1;
			} else {
				turnStepX = clockwise ? -1 : 1;
			}
		}

		int holeSlice = 1 + positiveMod(sample ^ 0x7AF491C3L, 3);
		boolean holeLeadsDown = variant == BrokenStairsVariant.HOLES && ((sample >>> 11) & 1L) == 0L;
		return new StairsProfile(
				placement,
				levelIndex,
				xAxis,
				forwardStep,
				width,
				variant,
				turnStepX,
				turnStepZ,
				holeSlice,
				holeLeadsDown
		);
	}

	private static void applyStairsLowerLevel(
			StairsProfile profile,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		if (!profile.room().contains(x, z)) {
			return;
		}

		clearInterior(columnStates);

		int slice = getSliceIndex(profile, x, z);
		if (slice < 0) {
			return;
		}

		if (profile.variant() == BrokenStairsVariant.HOLES && slice == profile.holeSlice()) {
			if (profile.holeLeadsDown()) {
				columnStates.setFloor(AIR);
			}
			return;
		}

		int heightIndex = slice;
		if (heightIndex > 4) {
			heightIndex = 4;
		}
		placeStep(columnStates, x, z, floorY, ceilingY, heightIndex);
	}

	private static void applyStairsUpperLevel(
			StairsProfile profile,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		if (!profile.room().contains(x, z)) {
			return;
		}

		int slice = getSliceIndex(profile, x, z);
		if (profile.variant() == BrokenStairsVariant.INTO_WALL) {
			if (isUpperShaftColumn(profile, x, z)) {
				columnStates.setFloor(AIR);
				clearInterior(columnStates);
			}
			return;
		}

		clearInterior(columnStates);
		applyUpperSliceClearance(columnStates, slice);
	}

	private static void applyStairsReceivingShaft(
			StairsProfile profile,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		if (profile.variant() != BrokenStairsVariant.HOLES || !profile.holeLeadsDown()) {
			return;
		}

		if (!profile.room().contains(x, z) || !isHoleSliceColumn(profile, x, z)) {
			return;
		}

		clearInterior(columnStates);
		columnStates.setCeiling(AIR);
	}

	private static void clearInterior(ColumnStates columnStates) {
		columnStates.setLower(AIR);
		columnStates.setUpper(AIR);
		columnStates.setTop(AIR);
	}

	private static void placeStep(ColumnStates columnStates, int x, int z, int floorY, int ceilingY, int heightIndex) {
		columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
		columnStates.setLower(AIR);
		columnStates.setUpper(AIR);
		columnStates.setTop(AIR);
		columnStates.setCeiling(AIR);

		switch (heightIndex) {
			case 0 -> {
			}
			case 1 -> {
				columnStates.setLower(randomizedBackroomsBlock(x, floorY + 1, z));
			}
			case 2 -> {
				columnStates.setLower(randomizedBackroomsBlock(x, floorY + 1, z));
				columnStates.setUpper(randomizedBackroomsBlock(x, floorY + 2, z));
				columnStates.setCeiling(AIR);
			}
			case 3 -> {
				columnStates.setLower(randomizedBackroomsBlock(x, floorY + 1, z));
				columnStates.setUpper(randomizedBackroomsBlock(x, floorY + 2, z));
				columnStates.setTop(randomizedBackroomsBlock(x, floorY + 3, z));
			}
			default -> {
				columnStates.setLower(randomizedBackroomsBlock(x, floorY + 1, z));
				columnStates.setUpper(randomizedBackroomsBlock(x, floorY + 2, z));
				columnStates.setTop(randomizedBackroomsBlock(x, floorY + 3, z));
				columnStates.setCeiling(randomizedBackroomsBlock(x, ceilingY, z));
			}
		}
	}

	private static void applyUpperSliceClearance(ColumnStates columnStates, int slice) {
		if (slice >= 1) {
			columnStates.setFloor(AIR);
		}
		if (slice >= 2) {
			columnStates.setLower(AIR);
		}
		if (slice >= 3) {
			columnStates.setUpper(AIR);
		}
	}

	private static boolean isUpperShaftColumn(StairsProfile profile, int x, int z) {
		return isSliceColumn(profile, x, z, 4);
	}

	private static boolean isHoleSliceColumn(StairsProfile profile, int x, int z) {
		return isSliceColumn(profile, x, z, profile.holeSlice());
	}

	private static int getSliceIndex(StairsProfile profile, int x, int z) {
		for (int slice = 0; slice <= 4; slice++) {
			if (isSliceColumn(profile, x, z, slice)) {
				return slice;
			}
		}

		return -1;
	}

	private static boolean isSliceColumn(StairsProfile profile, int x, int z, int slice) {
		Point center = getSliceCenter(profile, slice);
		int lateralDelta = profile.xAxis()
				? z - center.z()
				: x - center.x();
		int forwardDelta = profile.xAxis()
				? x - center.x()
				: z - center.z();
		if (forwardDelta != 0) {
			return false;
		}

		int lateralMin = -(profile.width() / 2);
		int lateralMax = lateralMin + profile.width() - 1;
		return lateralDelta >= lateralMin && lateralDelta <= lateralMax;
	}

	private static Point getSliceCenter(StairsProfile profile, int slice) {
		int baseOffset = slice - 2;
		int x = profile.room().roomCenterX();
		int z = profile.room().roomCenterZ();
		if (profile.xAxis()) {
			x += baseOffset * profile.forwardStep();
			if (profile.variant() == BrokenStairsVariant.TWISTED && slice >= 3) {
				x += (2 - slice) * profile.forwardStep();
				z += (slice - 2) * profile.turnStepZ();
			}
		} else {
			z += baseOffset * profile.forwardStep();
			if (profile.variant() == BrokenStairsVariant.TWISTED && slice >= 3) {
				z += (2 - slice) * profile.forwardStep();
				x += (slice - 2) * profile.turnStepX();
			}
		}
		return new Point(x, z);
	}

	private static BlockState randomizedBackroomsBlock(int x, int y, int z) {
		return ModBlocks.getRandomizedBackroomsBlockState(BlockPos.asLong(x, y, z) ^ BACKROOMS_VARIANT_SALT);
	}

	private static BlockState createDoorState(BackroomsLayout.DoorPlacement placement, DoubleBlockHalf half) {
		return BACKROOMS_DOOR_BLOCK
				.setValue(net.minecraft.world.level.block.DoorBlock.FACING, placement.facing())
				.setValue(net.minecraft.world.level.block.DoorBlock.HINGE, placement.hinge())
				.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false)
				.setValue(net.minecraft.world.level.block.DoorBlock.POWERED, false)
				.setValue(net.minecraft.world.level.block.DoorBlock.HALF, half);
	}

	private static BlockState createExitSignState(Direction facing) {
		return EXIT_SIGN_WALL_BLOCK.setValue(WallSignBlock.FACING, facing);
	}

	private static int positiveMod(long value, int modulo) {
		return (int) Math.floorMod(value, modulo);
	}

	private static long mix(int a, int b, long salt) {
		long value = salt;
		value ^= (long) a * 341873128712L;
		value ^= (long) b * 132897987541L;
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdl;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}

	private static long levelSalt(int levelIndex) {
		return mix(levelIndex, levelIndex ^ 0x51F2A, 0x4C564C53414C5431L);
	}

	static final class ColumnStates {
		private BlockState floor;
		private BlockState lower;
		private BlockState upper;
		private BlockState top;
		private BlockState ceiling;

		ColumnStates(BlockState floor, BlockState lower, BlockState upper, BlockState top, BlockState ceiling) {
			this.floor = floor;
			this.lower = lower;
			this.upper = upper;
			this.top = top;
			this.ceiling = ceiling;
		}

		BlockState floor() {
			return this.floor;
		}

		BlockState lower() {
			return this.lower;
		}

		BlockState upper() {
			return this.upper;
		}

		BlockState top() {
			return this.top;
		}

		BlockState ceiling() {
			return this.ceiling;
		}

		void setFloor(BlockState floor) {
			this.floor = floor;
		}

		void setLower(BlockState lower) {
			this.lower = lower;
		}

		void setUpper(BlockState upper) {
			this.upper = upper;
		}

		void setTop(BlockState top) {
			this.top = top;
		}

		void setCeiling(BlockState ceiling) {
			this.ceiling = ceiling;
		}
	}

	private record Point(int x, int z) {
	}

	private record StairsProfile(
			BackroomsLayout.SpecialRoomPlacement room,
			int levelIndex,
			boolean xAxis,
			int forwardStep,
			int width,
			BrokenStairsVariant variant,
			int turnStepX,
			int turnStepZ,
			int holeSlice,
			boolean holeLeadsDown
	) {
	}

	private enum BrokenStairsVariant {
		NORMAL,
		INTO_WALL,
		TWISTED,
		HOLES;

		private static final BrokenStairsVariant[] BROKEN_VALUES = {
				INTO_WALL,
				TWISTED,
				HOLES
		};
	}
}
