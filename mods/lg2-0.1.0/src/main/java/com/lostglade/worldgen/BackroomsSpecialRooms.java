package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

final class BackroomsSpecialRooms {
	private static final long BACKROOMS_VARIANT_SALT = 0x4c47324241434b52L;
	private static final long STAIRS_LAYOUT_SALT = 0x535441495253524DL;
	private static final long FLOOR_HOLES_LAYOUT_SALT = 0x464C4F4F52484F4CL;
	private static final BlockState BACKROOMS_DOOR_BLOCK = ModBlocks.BACKROOMS_DOOR.defaultBlockState();
	private static final BlockState EXIT_SIGN_WALL_BLOCK = ModBlocks.EXIT_WALL_SIGN.defaultBlockState();
	private static final BlockState CHEST_BLOCK = Blocks.CHEST.defaultBlockState();
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
		if (specialRoom != null) {
			if (specialRoom.type() == BackroomsLayout.SpecialRoomType.STAIRS) {
				applyStairsLowerLevel(buildStairsProfile(specialRoom, levelIndex), x, z, floorY, ceilingY, columnStates);
				return;
			}
			if (specialRoom.type() == BackroomsLayout.SpecialRoomType.TRASH_ROOM) {
				applyTrashRoom(specialRoom, levelIndex, x, z, floorY, columnStates);
				return;
			}
			if (specialRoom.type() == BackroomsLayout.SpecialRoomType.FLOOR_HOLES) {
				applyFloorHolesRoom(buildFloorHolesProfile(specialRoom, levelIndex), x, z, floorY, columnStates);
				return;
			}
		}

		FloorHolesProfile upperHolesProfile = getFloorHolesProfileAt(x, z, levelIndex + 1);
		if (upperHolesProfile != null && upperHolesProfile.piercedFloors() >= 1) {
			applyFloorHolesIntermediateLevel(upperHolesProfile, x, z, floorY, columnStates);
			return;
		}

		FloorHolesProfile twoLevelsUpHolesProfile = getFloorHolesProfileAt(x, z, levelIndex + 2);
		if (twoLevelsUpHolesProfile != null) {
			if (twoLevelsUpHolesProfile.piercedFloors() >= 2) {
				applyFloorHolesIntermediateLevel(twoLevelsUpHolesProfile, x, z, floorY, columnStates);
			} else {
				applyFloorHolesLandingLevel(twoLevelsUpHolesProfile, x, z, floorY, columnStates);
			}
			return;
		}

		FloorHolesProfile threeLevelsUpHolesProfile = getFloorHolesProfileAt(x, z, levelIndex + 3);
		if (threeLevelsUpHolesProfile != null && threeLevelsUpHolesProfile.piercedFloors() >= 2) {
			applyFloorHolesLandingLevel(threeLevelsUpHolesProfile, x, z, floorY, columnStates);
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

	private static FloorHolesProfile getFloorHolesProfileAt(int x, int z, int levelIndex) {
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		BackroomsLayout.SpecialRoomPlacement placement = BackroomsLayout.getSpecialRoomAt(zone, x, z, levelIndex);
		if (placement == null || placement.type() != BackroomsLayout.SpecialRoomType.FLOOR_HOLES) {
			return null;
		}

		return buildFloorHolesProfile(placement, levelIndex);
	}

	private static FloorHolesProfile buildFloorHolesProfile(BackroomsLayout.SpecialRoomPlacement room, int levelIndex) {
		long seed = mix(room.cellX(), room.cellZ(), FLOOR_HOLES_LAYOUT_SALT ^ levelSalt(levelIndex));
		boolean tallRoom = positiveMod(seed ^ 0x4845494748544648L, 100) < 45;
		int piercedFloors = 1 + positiveMod(seed ^ 0x5049455243454450L, 2);
		return new FloorHolesProfile(room, levelIndex, tallRoom, piercedFloors);
	}

	private static void applyFloorHolesRoom(
			FloorHolesProfile profile,
			int x,
			int z,
			int floorY,
			ColumnStates columnStates
	) {
		if (!profile.room().contains(x, z)) {
			return;
		}

		clearInterior(columnStates);
		if (profile.tallRoom()) {
			columnStates.setCeiling(AIR);
		}

		if (isFloorHoleColumn(profile, x, z)) {
			columnStates.setFloor(AIR);
		} else {
			columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
		}
	}

	private static void applyFloorHolesIntermediateLevel(
			FloorHolesProfile profile,
			int x,
			int z,
			int floorY,
			ColumnStates columnStates
	) {
		if (!profile.room().contains(x, z)) {
			return;
		}

		if (isFloorHoleColumn(profile, x, z)) {
			columnStates.setFloor(AIR);
			columnStates.setLower(AIR);
			columnStates.setUpper(AIR);
			columnStates.setTop(AIR);
			columnStates.setCeiling(AIR);
			return;
		}

		fillSolidBackroomsColumn(columnStates, x, z, floorY);
	}

	private static void applyFloorHolesLandingLevel(
			FloorHolesProfile profile,
			int x,
			int z,
			int floorY,
			ColumnStates columnStates
	) {
		if (!profile.room().contains(x, z) || !isFloorHoleColumn(profile, x, z)) {
			return;
		}

		columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
		columnStates.setLower(AIR);
		columnStates.setUpper(AIR);
		columnStates.setTop(AIR);
		columnStates.setCeiling(AIR);
	}

	private static void fillSolidBackroomsColumn(ColumnStates columnStates, int x, int z, int floorY) {
		columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
		columnStates.setLower(randomizedBackroomsBlock(x, floorY + 1, z));
		columnStates.setUpper(randomizedBackroomsBlock(x, floorY + 2, z));
		columnStates.setTop(randomizedBackroomsBlock(x, floorY + 3, z));
		columnStates.setCeiling(randomizedBackroomsBlock(x, floorY + 4, z));
	}

	private static boolean isFloorHoleColumn(FloorHolesProfile profile, int x, int z) {
		BackroomsLayout.SpecialRoomPlacement room = profile.room();
		int localX = x - (room.roomCenterX() - room.roomHalfWidth());
		int localZ = z - (room.roomCenterZ() - room.roomHalfHeight());
		int width = room.roomHalfWidth() * 2 + 1;
		int depth = room.roomHalfHeight() * 2 + 1;

		if (localX < 0 || localX >= width || localZ < 0 || localZ >= depth) {
			return false;
		}

		return isFloorHoleAxisCoordinate(localX, width) && isFloorHoleAxisCoordinate(localZ, depth);
	}

	private static boolean isFloorHoleAxisCoordinate(int local, int size) {
		if (local <= 0) {
			return false;
		}

		int innerSpan = size - 2;
		if (local == size - 1) {
			return innerSpan % 3 == 1;
		}

		int inner = local - 1;
		return inner % 3 < 2;
	}

	private static void applyTrashRoom(
			BackroomsLayout.SpecialRoomPlacement room,
			int levelIndex,
			int x,
			int z,
			int floorY,
			ColumnStates columnStates
	) {
		if (!room.contains(x, z)) {
			return;
		}

		TrashRoomProfile profile = buildTrashRoomProfile(room, levelIndex);
		clearInterior(columnStates);
		if (profile.tallRoom()) {
			columnStates.setCeiling(AIR);
		}

		if (isTrashChestColumn(profile, x, z)) {
			columnStates.setLower(createTrashChestState(profile, x, z));
			columnStates.setUpper(AIR);
			columnStates.setTop(AIR);
			return;
		}

		int pileHeight = getTrashPileHeight(profile, x, z);
		if (pileHeight <= 0) {
			return;
		}

		columnStates.setLower(randomTrashBlock(x, floorY + 1, z, profile, 0));
		if (pileHeight >= 2) {
			columnStates.setUpper(randomTrashBlock(x, floorY + 2, z, profile, 1));
		}
		if (pileHeight >= 3) {
			columnStates.setTop(randomTrashBlock(x, floorY + 3, z, profile, 2));
		}
		if (pileHeight >= 4 && profile.tallRoom()) {
			columnStates.setCeiling(randomTrashBlock(x, floorY + 4, z, profile, 3));
		}
	}

	private static TrashRoomProfile buildTrashRoomProfile(BackroomsLayout.SpecialRoomPlacement room, int levelIndex) {
		long seed = trashSalt(room, levelIndex);
		boolean tallRoom = positiveMod(seed ^ 0x4845494748543431L, 100) < 45;
		int pileCount = 2 + positiveMod(seed ^ 0x50494C45434F554EL, 3);
		return new TrashRoomProfile(room, levelIndex, tallRoom, pileCount);
	}

	private static boolean isTrashChestColumn(TrashRoomProfile profile, int x, int z) {
		BackroomsLayout.SpecialRoomPlacement room = profile.room();
		int localX = x - room.roomCenterX();
		int localZ = z - room.roomCenterZ();
		if (!isInsideTrashChestArea(room, localX, localZ)) {
			return false;
		}

		int slotWidth = (Math.max(1, room.roomHalfWidth() - 1) * 2) + 1;
		int slotHeight = (Math.max(1, room.roomHalfHeight() - 1) * 2) + 1;
		int slotX = localX + Math.max(1, room.roomHalfWidth() - 1);
		int slotZ = localZ + Math.max(1, room.roomHalfHeight() - 1);
		int targetSlot = (slotZ * slotWidth) + slotX;

		int chestCount = getTrashChestCount(profile);
		int slotCount = slotWidth * slotHeight;
		boolean[] used = new boolean[slotCount];
		for (int index = 0; index < chestCount; index++) {
			int slot = getTrashChestSlot(profile, index, slotCount, used);
			if (slot == targetSlot) {
				return true;
			}
		}
		return false;
	}

	private static boolean isInsideTrashChestArea(BackroomsLayout.SpecialRoomPlacement room, int localX, int localZ) {
		int maxX = Math.max(1, room.roomHalfWidth() - 1);
		int maxZ = Math.max(1, room.roomHalfHeight() - 1);
		return Math.abs(localX) <= maxX && Math.abs(localZ) <= maxZ;
	}

	private static int getTrashChestCount(TrashRoomProfile profile) {
		BackroomsLayout.SpecialRoomPlacement room = profile.room();
		long seed = trashSalt(room, profile.levelIndex()) ^ 0x4348455354434F55L;
		return 1 + positiveMod(mix(room.cellX(), room.cellZ(), seed), 3);
	}

	private static int getTrashChestSlot(
			TrashRoomProfile profile,
			int index,
			int slotCount,
			boolean[] used
	) {
		BackroomsLayout.SpecialRoomPlacement room = profile.room();
		long salt = trashSalt(room, profile.levelIndex()) ^ (0x53504F544944584CL + (31L * index));
		int slot = positiveMod(mix(room.cellX() + index * 13, room.cellZ() - index * 17, salt), slotCount);
		while (used[slot]) {
			slot = (slot + 1) % slotCount;
		}
		used[slot] = true;
		return slot;
	}

	private static BlockState createTrashChestState(TrashRoomProfile profile, int x, int z) {
		long seed = mix(x, z, trashSalt(profile.room(), profile.levelIndex()) ^ 0x4348455354464143L);
		Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		Direction facing = directions[positiveMod(seed, directions.length)];
		return CHEST_BLOCK.setValue(ChestBlock.FACING, facing);
	}

	private static int getTrashPileHeight(TrashRoomProfile profile, int x, int z) {
		int height = 0;
		for (int pileIndex = 0; pileIndex < profile.pileCount(); pileIndex++) {
			PileSpec pile = getPileSpec(profile, pileIndex);
			int dx = Math.abs(x - pile.centerX());
			int dz = Math.abs(z - pile.centerZ());
			if (dx > pile.radiusX() || dz > pile.radiusZ()) {
				continue;
			}

			int pileHeight = 1;
			if (dx <= Math.max(0, pile.radiusX() - 1) && dz <= Math.max(0, pile.radiusZ() - 1)) {
				pileHeight = 2;
			}
			if (pile.height() >= 3 && dx <= Math.max(0, pile.radiusX() - 2) && dz <= Math.max(0, pile.radiusZ() - 2)) {
				pileHeight = 3;
			}
			if (pile.height() >= 4 && dx == 0 && dz == 0) {
				pileHeight = 4;
			}
			pileHeight = applyTrashPileRoughness(profile, pileIndex, x, z, dx, dz, pileHeight);
			height = Math.max(height, pileHeight);
		}

		if (height == 0) {
			long debrisSeed = mix(x, z, trashSalt(profile.room(), profile.levelIndex()) ^ 0x4445425249535253L);
			if (positiveMod(debrisSeed, 100) < 10) {
				return 1;
			}
		}

		return height;
	}

	private static int applyTrashPileRoughness(
			TrashRoomProfile profile,
			int pileIndex,
			int x,
			int z,
			int dx,
			int dz,
			int pileHeight
	) {
		if (pileHeight <= 0) {
			return 0;
		}

		long roughnessSeed = mix(
				x + (pileIndex * 17),
				z - (pileIndex * 19),
				trashSalt(profile.room(), profile.levelIndex()) ^ 0x525547474544504CL
		);
		boolean centerColumn = dx == 0 && dz == 0;
		int roll = positiveMod(roughnessSeed, 100);

		// Keep pile peaks reliable, but make flanks and interior columns patchy.
		if (!centerColumn && roll < 18) {
			return 0;
		}
		if (pileHeight >= 3 && roll >= 18 && roll < 42) {
			pileHeight--;
		}
		if (pileHeight >= 2 && roll >= 42 && roll < 56) {
			pileHeight--;
		}

		return pileHeight;
	}

	private static PileSpec getPileSpec(TrashRoomProfile profile, int pileIndex) {
		BackroomsLayout.SpecialRoomPlacement room = profile.room();
		long salt = trashSalt(room, profile.levelIndex()) ^ (0x50494C4553504543L + (137L * pileIndex));
		int minX = room.roomCenterX() - Math.max(1, room.roomHalfWidth() - 1);
		int maxX = room.roomCenterX() + Math.max(1, room.roomHalfWidth() - 1);
		int minZ = room.roomCenterZ() - Math.max(1, room.roomHalfHeight() - 1);
		int maxZ = room.roomCenterZ() + Math.max(1, room.roomHalfHeight() - 1);
		int centerX = minX + positiveMod(mix(room.cellX() + pileIndex, room.cellZ(), salt ^ 0x11L), maxX - minX + 1);
		int centerZ = minZ + positiveMod(mix(room.cellX(), room.cellZ() - pileIndex, salt ^ 0x22L), maxZ - minZ + 1);
		int radiusX = 1 + positiveMod(mix(room.cellX(), pileIndex, salt ^ 0x33L), 2);
		int radiusZ = 1 + positiveMod(mix(room.cellZ(), pileIndex, salt ^ 0x44L), 2);
		long heightSeed = mix(room.cellX() - pileIndex, room.cellZ() + pileIndex, salt ^ 0x55L);
		int height = 2;
		if (positiveMod(heightSeed ^ 0x66L, 100) < 34) {
			height = 3;
		}
		if (profile.tallRoom() && positiveMod(heightSeed ^ 0x77L, 100) < 10) {
			height = 4;
		}
		return new PileSpec(centerX, centerZ, radiusX, radiusZ, height);
	}

	private static BlockState randomTrashBlock(
			int x,
			int y,
			int z,
			TrashRoomProfile profile,
			int layer
	) {
		long seed = mix(x, z, trashSalt(profile.room(), profile.levelIndex()) ^ (0x54524153484C4159L + layer * 97L));
		return switch (positiveMod(seed, 15)) {
			case 0 -> Blocks.DIRT.defaultBlockState();
			case 1 -> Blocks.COARSE_DIRT.defaultBlockState();
			case 2 -> Blocks.OAK_PLANKS.defaultBlockState();
			case 3 -> Blocks.SPRUCE_PLANKS.defaultBlockState();
			case 4 -> Blocks.OAK_LOG.defaultBlockState();
			case 5 -> Blocks.SPRUCE_LOG.defaultBlockState();
			case 6 -> Blocks.COBBLESTONE.defaultBlockState();
			case 7 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
			case 8 -> Blocks.STONE.defaultBlockState();
			case 9 -> Blocks.ANDESITE.defaultBlockState();
			case 10 -> Blocks.GRAVEL.defaultBlockState();
			case 11 -> Blocks.SAND.defaultBlockState();
			case 12 -> Blocks.OAK_SLAB.defaultBlockState();
			case 13 -> Blocks.COBBLESTONE_SLAB.defaultBlockState();
			default -> Blocks.STONE_SLAB.defaultBlockState();
		};
	}

	private static long trashSalt(BackroomsLayout.SpecialRoomPlacement room, int levelIndex) {
		return mix(room.cellX(), room.cellZ(), 0x5452415348524F4FL ^ levelSalt(levelIndex));
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

		return new StairsProfile(
				placement,
				levelIndex,
				xAxis,
				forwardStep,
				width,
				variant,
				turnStepX,
				turnStepZ
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

		int heightIndex = slice;
		if (heightIndex > 4) {
			heightIndex = 4;
		}
		placeStep(columnStates, x, z, floorY, ceilingY, heightIndex);
		if (profile.variant() == BrokenStairsVariant.HOLES) {
			int holeIndex = getHoleIndexForColumn(profile, x, z);
			if (holeIndex >= 0) {
				carveHoleColumn(columnStates, x, z, floorY, holeLeadsDown(holeIndex));
			}
		}
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
			// For "into wall" variant the upper opening must stay blocked, including above the top step.
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
		if (profile.variant() != BrokenStairsVariant.HOLES) {
			return;
		}

		int holeIndex = getHoleIndexForColumn(profile, x, z);
		if (!profile.room().contains(x, z) || holeIndex < 0 || !holeLeadsDown(holeIndex)) {
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

	private static int getHoleIndexForColumn(StairsProfile profile, int x, int z) {
		if (profile.variant() != BrokenStairsVariant.HOLES) {
			return -1;
		}

		int slice = getSliceIndex(profile, x, z);
		if (slice < 1 || slice > 3) {
			return -1;
		}

		Point center = getSliceCenter(profile, slice);
		int lateralDelta = profile.xAxis() ? (z - center.z()) : (x - center.x());
		int lateralMin = -(profile.width() / 2);
		int lateralIndex = lateralDelta - lateralMin;
		if (lateralIndex < 0 || lateralIndex >= profile.width()) {
			return -1;
		}

		int slotCount = 3 * profile.width();
		int targetSlot = ((slice - 1) * profile.width()) + lateralIndex;
		int holeCount = getHoleCount(profile);
		if (holeCount <= 0) {
			return -1;
		}

		boolean[] used = new boolean[slotCount];
		for (int holeIndex = 0; holeIndex < holeCount; holeIndex++) {
			long holeSalt = 0x484F4C45534C4F4EL ^ levelSalt(profile.levelIndex()) ^ (31L * holeIndex);
			int slot = positiveMod(
					mix(profile.room().cellX() + (17 * holeIndex), profile.room().cellZ() - (23 * holeIndex), holeSalt),
					slotCount
			);
			while (used[slot]) {
				slot = (slot + 1) % slotCount;
			}
			used[slot] = true;
			if (slot == targetSlot) {
				return holeIndex;
			}
		}
		return -1;
	}

	private static int getHoleCount(StairsProfile profile) {
		long holeSalt = 0x484F4C45434F554EL ^ levelSalt(profile.levelIndex());
		int holeCount = 2 + positiveMod(mix(profile.room().cellX(), profile.room().cellZ(), holeSalt), 4);
		int maxHoles = 3 * profile.width();
		return Math.min(holeCount, maxHoles);
	}

	private static boolean holeLeadsDown(int holeIndex) {
		return (holeIndex % 2) == 0;
	}

	private static void carveHoleColumn(ColumnStates columnStates, int x, int z, int floorY, boolean leadsDown) {
		columnStates.setLower(AIR);
		columnStates.setUpper(AIR);
		columnStates.setTop(AIR);
		columnStates.setCeiling(AIR);
		if (leadsDown) {
			columnStates.setFloor(AIR);
		} else {
			columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
		}
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
		if (profile.variant() == BrokenStairsVariant.TWISTED && slice == 3) {
			return matchesSliceFootprint(profile, center, x, z, profile.xAxis())
					|| matchesSliceFootprint(profile, center, x, z, !profile.xAxis());
		}

		boolean useXAxis = profile.xAxis();
		if (profile.variant() == BrokenStairsVariant.TWISTED && slice >= 4) {
			useXAxis = !useXAxis;
		}
		return matchesSliceFootprint(profile, center, x, z, useXAxis);
	}

	private static boolean matchesSliceFootprint(StairsProfile profile, Point center, int x, int z, boolean useXAxis) {
		int lateralDelta = useXAxis ? (z - center.z()) : (x - center.x());
		int forwardDelta = useXAxis ? (x - center.x()) : (z - center.z());
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
				x += (3 - slice) * profile.forwardStep();
				z += (slice - 2) * profile.turnStepZ();
			}
		} else {
			z += baseOffset * profile.forwardStep();
			if (profile.variant() == BrokenStairsVariant.TWISTED && slice >= 3) {
				z += (3 - slice) * profile.forwardStep();
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

	private record TrashRoomProfile(
			BackroomsLayout.SpecialRoomPlacement room,
			int levelIndex,
			boolean tallRoom,
			int pileCount
	) {
	}

	private record FloorHolesProfile(
			BackroomsLayout.SpecialRoomPlacement room,
			int levelIndex,
			boolean tallRoom,
			int piercedFloors
	) {
	}

	private record PileSpec(
			int centerX,
			int centerZ,
			int radiusX,
			int radiusZ,
			int height
	) {
	}

	private record StairsProfile(
			BackroomsLayout.SpecialRoomPlacement room,
			int levelIndex,
			boolean xAxis,
			int forwardStep,
			int width,
			BrokenStairsVariant variant,
			int turnStepX,
			int turnStepZ
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
