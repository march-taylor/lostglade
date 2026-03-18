package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class BackroomsSpecialRooms {
	private static final long BACKROOMS_VARIANT_SALT = 0x4c47324241434b52L;
	private static final long STAIRS_LAYOUT_SALT = 0x535441495253524DL;
	private static final long FLOOR_HOLES_LAYOUT_SALT = 0x464C4F4F52484F4CL;
	private static final long VOID_HALL_LAYOUT_SALT = 0x564F494448414C4CL;
	private static final long HOUSE_HALL_LAYOUT_SALT = 0x484F55534548414CL;
	private static final long PLUS_MAZE_LAYOUT_SALT = 0x504C55534D415A45L;
	private static final double UPPER_LADDER_CRAWL_Y_OFFSET = 0.8D;
	private static final BlockState BACKROOMS_DOOR_BLOCK = ModBlocks.BACKROOMS_DOOR.defaultBlockState();
	private static final BlockState EXIT_SIGN_WALL_BLOCK = ModBlocks.EXIT_WALL_SIGN.defaultBlockState();
	private static final BlockState CHEST_BLOCK = Blocks.CHEST.defaultBlockState();
	private static final BlockState OAK_DOOR_BLOCK = Blocks.OAK_DOOR.defaultBlockState();
	private static final BlockState OAK_WALL_SIGN_BLOCK = Blocks.OAK_WALL_SIGN.defaultBlockState();
	private static final BlockState WALL_TORCH_BLOCK = Blocks.WALL_TORCH.defaultBlockState();
	private static final BlockState LADDER_BLOCK = Blocks.LADDER.defaultBlockState();
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
			BackroomsLayout.LadderRoomPlacement ladderRoom,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		int levelIndex = BackroomsLayout.getLevelIndex(floorY);
		VoidHallProfile voidHallProfile = getVoidHallProfileForColumn(x, z);
		if (voidHallProfile != null
				&& isInsideVoidHallHole(voidHallProfile, x, z)
				&& levelIndex < voidHallProfile.room().baseLevelIndex() + voidHallProfile.room().levelSpan()) {
			if (levelIndex >= voidHallProfile.room().baseLevelIndex()) {
				applyVoidHall(voidHallProfile, x, z, floorY, ceilingY, columnStates);
			} else {
				applyVoidHallShaft(columnStates);
			}
			return;
		}

		if (specialRoom != null) {
			if (specialRoom.type() == BackroomsLayout.SpecialRoomType.STAIRS) {
				applyStairsLowerLevel(buildStairsProfile(specialRoom, levelIndex), x, z, floorY, ceilingY, columnStates);
				return;
			}
			if (specialRoom.type() == BackroomsLayout.SpecialRoomType.PLUS_MAZE) {
				applyPlusMaze(buildPlusMazeProfile(specialRoom), x, z, floorY, columnStates);
				return;
			}
			if (specialRoom.type() == BackroomsLayout.SpecialRoomType.VOID_HALL) {
				applyVoidHall(buildVoidHallProfile(specialRoom), x, z, floorY, ceilingY, columnStates);
				return;
			}
			if (specialRoom.type() == BackroomsLayout.SpecialRoomType.HOUSE_HALL) {
				applyHouseHall(buildHouseHallProfile(specialRoom), x, z, floorY, ceilingY, columnStates);
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
			return;
		}

		if (ladderRoom != null) {
			applyLadderRoom(ladderRoom, x, z, floorY, ceilingY, columnStates);
		}
	}

	public static boolean isFakeHouseDoorAt(int x, int y, int z) {
		int levelIndex = BackroomsLayout.getLevelIndex(y);
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		BackroomsLayout.SpecialRoomPlacement specialRoom = BackroomsLayout.getSpecialRoomAt(zone, x, z, levelIndex);
		if (specialRoom == null || specialRoom.type() != BackroomsLayout.SpecialRoomType.HOUSE_HALL) {
			return false;
		}

		HouseHallProfile profile = buildHouseHallProfile(specialRoom);
		if (!profile.fakeDoor()) {
			return false;
		}

		int localY = y - getHouseHallBaseFloorY(profile);
		return (localY == 1 || localY == 2) && isHouseDoorColumn(profile, x, z);
	}

	public static BlockPos getHouseHallWaitingStalkerPos(int x, int y, int z) {
		int levelIndex = BackroomsLayout.getLevelIndex(y);
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		BackroomsLayout.SpecialRoomPlacement specialRoom = BackroomsLayout.getSpecialRoomAt(zone, x, z, levelIndex);
		if (specialRoom == null || specialRoom.type() != BackroomsLayout.SpecialRoomType.HOUSE_HALL) {
			return null;
		}

		HouseHallProfile profile = buildHouseHallProfile(specialRoom);
		if (!profile.waitingStalker()) {
			return null;
		}

		Direction inward = profile.doorFacing().getOpposite();
		int spawnDistance = Math.max(1, getHouseHalf(profile) - 2);
		return new BlockPos(
				profile.houseCenterX() + inward.getStepX() * spawnDistance,
				getHouseHallBaseFloorY(profile) + 1,
				profile.houseCenterZ() + inward.getStepZ() * spawnDistance
		);
	}

	public static boolean isUpperLadderCrawlZone(Level level, double x, double y, double z) {
		BlockPos feetPos = BlockPos.containing(x, y + UPPER_LADDER_CRAWL_Y_OFFSET, z);
		return isUpperLadderCrawlZone(level, feetPos.getX(), feetPos.getY(), feetPos.getZ());
	}

	public static boolean isUpperLadderOrTunnelCrawlZone(Level level, double x, double y, double z) {
		BlockPos feetPos = BlockPos.containing(x, y + UPPER_LADDER_CRAWL_Y_OFFSET, z);
		return isUpperLadderOrTunnelCrawlZone(level, feetPos.getX(), feetPos.getY(), feetPos.getZ());
	}

	public static boolean isUpperLadderCrawlZone(Level level, int x, int y, int z) {
		int levelIndex = BackroomsLayout.getLevelIndex(y);
		BackroomsLayout.LadderRoomPlacement placement = BackroomsLayout.getLadderRoomAt(x, z, levelIndex);
		if (placement == null || !placement.isLadderColumn(x, z)) {
			return false;
		}

		int floorY = BackroomsLayout.FLOOR_Y + levelIndex * BackroomsLayout.LEVEL_HEIGHT;
		int localY = y - floorY;
		return (localY == 2 || localY == 3) && hasIntactLadder(level, placement, floorY);
	}

	public static boolean isUpperLadderOrTunnelCrawlZone(Level level, int x, int y, int z) {
		int levelIndex = BackroomsLayout.getLevelIndex(y);
		BackroomsLayout.LadderRoomPlacement placement = BackroomsLayout.getLadderRoomAt(x, z, levelIndex);
		if (placement == null) {
			return false;
		}

		int floorY = BackroomsLayout.FLOOR_Y + levelIndex * BackroomsLayout.LEVEL_HEIGHT;
		if (!hasIntactLadder(level, placement, floorY)) {
			return false;
		}

		int localY = y - floorY;
		if (placement.isLadderColumn(x, z)) {
			return localY >= 1 && localY <= 3;
		}

		return placement.isTunnelColumn(x, z) && (localY == 2 || localY == 3);
	}

	private static boolean hasIntactLadder(Level level, BackroomsLayout.LadderRoomPlacement placement, int floorY) {
		BlockPos lowerPos = new BlockPos(placement.ladderX(), floorY + 1, placement.ladderZ());
		BlockPos upperPos = lowerPos.above();
		return level.getBlockState(lowerPos).is(Blocks.LADDER) && level.getBlockState(upperPos).is(Blocks.LADDER);
	}

	private static FloorHolesProfile getFloorHolesProfileAt(int x, int z, int levelIndex) {
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		BackroomsLayout.SpecialRoomPlacement placement = BackroomsLayout.getSpecialRoomAt(zone, x, z, levelIndex);
		if (placement == null || placement.type() != BackroomsLayout.SpecialRoomType.FLOOR_HOLES) {
			return null;
		}

		return buildFloorHolesProfile(placement, levelIndex);
	}

	private static VoidHallProfile getVoidHallProfileForColumn(int x, int z) {
		BackroomsLayout.SpecialRoomPlacement placement = BackroomsLayout.getVoidHallForColumn(x, z);
		if (placement == null) {
			return null;
		}

		return buildVoidHallProfile(placement);
	}

	private static VoidHallProfile buildVoidHallProfile(BackroomsLayout.SpecialRoomPlacement room) {
		long seed = mix(room.cellX(), room.cellZ(), VOID_HALL_LAYOUT_SALT ^ levelSalt(room.baseLevelIndex()));
		int borderWidth = 3 + positiveMod(seed ^ 0x564F4944424F5244L, 3);
		int holeRadius = Math.max(1, Math.min(room.roomHalfWidth(), room.roomHalfHeight()) - borderWidth);
		return new VoidHallProfile(room, borderWidth, holeRadius);
	}

	private static void applyVoidHall(
			VoidHallProfile profile,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		int levelIndex = BackroomsLayout.getLevelIndex(floorY);
		if (!profile.room().contains(x, z, levelIndex)) {
			return;
		}

		int levelOffset = levelIndex - profile.room().baseLevelIndex();
		boolean inHole = isInsideVoidHallHole(profile, x, z);
		if (levelOffset <= 0) {
			columnStates.setFloor(inHole ? AIR : randomizedBackroomsBlock(x, floorY, z));
			columnStates.setLower(AIR);
			columnStates.setUpper(AIR);
			columnStates.setTop(AIR);
			columnStates.setCeiling(AIR);
			return;
		}

		columnStates.setFloor(AIR);
		columnStates.setLower(AIR);
		columnStates.setUpper(AIR);
		columnStates.setTop(AIR);
		columnStates.setCeiling(levelOffset == profile.room().levelSpan() - 1
				? randomizedBackroomsBlock(x, ceilingY, z)
				: AIR);
	}

	private static void applyVoidHallShaft(ColumnStates columnStates) {
		columnStates.setFloor(AIR);
		columnStates.setLower(AIR);
		columnStates.setUpper(AIR);
		columnStates.setTop(AIR);
		columnStates.setCeiling(AIR);
	}

	private static boolean isInsideVoidHallHole(VoidHallProfile profile, int x, int z) {
		int dx = x - profile.room().roomCenterX();
		int dz = z - profile.room().roomCenterZ();
		return dx * dx + dz * dz <= profile.holeRadius() * profile.holeRadius();
	}

	private static FloorHolesProfile buildFloorHolesProfile(BackroomsLayout.SpecialRoomPlacement room, int levelIndex) {
		long seed = mix(room.cellX(), room.cellZ(), FLOOR_HOLES_LAYOUT_SALT ^ levelSalt(levelIndex));
		boolean tallRoom = !BackroomsLayout.isTopmostFullLevel(levelIndex)
				&& positiveMod(seed ^ 0x4845494748544648L, 100) < 45;
		int piercedFloors = 1 + positiveMod(seed ^ 0x5049455243454450L, 2);
		return new FloorHolesProfile(room, levelIndex, tallRoom, piercedFloors);
	}

	private static PlusMazeProfile buildPlusMazeProfile(BackroomsLayout.SpecialRoomPlacement room) {
		long seed = mix(room.cellX(), room.cellZ(), PLUS_MAZE_LAYOUT_SALT ^ levelSalt(room.baseLevelIndex()));
		return new PlusMazeProfile(room, seed, 6);
	}

	private static void applyPlusMaze(
			PlusMazeProfile profile,
			int x,
			int z,
			int floorY,
			ColumnStates columnStates
	) {
		int levelIndex = BackroomsLayout.getLevelIndex(floorY);
		if (!profile.room().contains(x, z, levelIndex)) {
			return;
		}

		clearInterior(columnStates);
		columnStates.setCeiling(AIR);
		columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
		if (isPlusMazeColumn(profile, x, z)) {
			fillSolidBackroomsColumn(columnStates, x, z, floorY);
		}
	}

	private static boolean isPlusMazeColumn(PlusMazeProfile profile, int x, int z) {
		BackroomsLayout.SpecialRoomPlacement room = profile.room();
		int localX = x - room.roomCenterX();
		int localZ = z - room.roomCenterZ();
		int maxCenterX = Math.max(0, room.roomHalfWidth() - 1);
		int maxCenterZ = Math.max(0, room.roomHalfHeight() - 1);
		int step = profile.gridStep();

		for (int centerX = firstGridCoordinate(-maxCenterX, step); centerX <= maxCenterX; centerX += step) {
			for (int centerZ = firstGridCoordinate(-maxCenterZ, step); centerZ <= maxCenterZ; centerZ += step) {
				if ((localX == centerX && Math.abs(localZ - centerZ) <= 1)
						|| (localZ == centerZ && Math.abs(localX - centerX) <= 1)) {
					return true;
				}
			}
		}

		return false;
	}

	private static int firstGridCoordinate(int min, int step) {
		int remainder = Math.floorMod(min, step);
		return remainder == 0 ? min : min + (step - remainder);
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

	private static void applyLadderRoom(
			BackroomsLayout.LadderRoomPlacement placement,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		if (placement.isLadderColumn(x, z)) {
			columnStates.setLower(createLadderState(placement.ladderFacing()));
			columnStates.setUpper(createLadderState(placement.ladderFacing()));
			columnStates.setTop(AIR);
			return;
		}

		if (placement.isTunnelColumn(x, z)) {
			columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
			columnStates.setLower(randomizedBackroomsBlock(x, floorY + 1, z));
			columnStates.setUpper(randomizedBackroomsBlock(x, floorY + 2, z));
			columnStates.setTop(AIR);
			columnStates.setCeiling(randomizedBackroomsBlock(x, ceilingY, z));
		}
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
		boolean tallRoom = !BackroomsLayout.isTopmostFullLevel(levelIndex)
				&& positiveMod(seed ^ 0x4845494748543431L, 100) < 45;
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

	private static void applyHouseHall(
			HouseHallProfile profile,
			int x,
			int z,
			int floorY,
			int ceilingY,
			ColumnStates columnStates
	) {
		int levelIndex = BackroomsLayout.getLevelIndex(floorY);
		if (!profile.room().contains(x, z, levelIndex)) {
			return;
		}

		int levelOffset = levelIndex - profile.room().baseLevelIndex();
		if (levelOffset <= 0) {
			columnStates.setFloor(randomizedBackroomsBlock(x, floorY, z));
			columnStates.setLower(AIR);
			columnStates.setUpper(AIR);
			columnStates.setTop(AIR);
			columnStates.setCeiling(AIR);
		} else if (levelOffset < profile.room().levelSpan() - 1) {
			columnStates.setFloor(AIR);
			columnStates.setLower(AIR);
			columnStates.setUpper(AIR);
			columnStates.setTop(AIR);
			columnStates.setCeiling(AIR);
		} else {
			columnStates.setFloor(AIR);
			columnStates.setLower(AIR);
			columnStates.setUpper(AIR);
			columnStates.setTop(AIR);
			columnStates.setCeiling(randomizedBackroomsBlock(x, ceilingY, z));
		}

		applyHouseHallState(profile, x, z, floorY, columnStates::setFloor);
		applyHouseHallState(profile, x, z, floorY + 1, columnStates::setLower);
		applyHouseHallState(profile, x, z, floorY + 2, columnStates::setUpper);
		applyHouseHallState(profile, x, z, floorY + 3, columnStates::setTop);
		applyHouseHallState(profile, x, z, ceilingY, columnStates::setCeiling);
	}

	private static void applyHouseHallState(
			HouseHallProfile profile,
			int x,
			int z,
			int y,
			java.util.function.Consumer<BlockState> setter
	) {
		BlockState state = getHouseHallStateAt(profile, x, y, z);
		if (state != null) {
			setter.accept(state);
		}
	}

	private static BlockState getHouseHallStateAt(HouseHallProfile profile, int x, int y, int z) {
		int localX = x - profile.houseCenterX();
		int localZ = z - profile.houseCenterZ();
		int localY = y - getHouseHallBaseFloorY(profile);
		if (localY < 0 || localY >= profile.room().levelSpan() * BackroomsLayout.LEVEL_HEIGHT) {
			return null;
		}

		BlockState houseState = getHouseStructureState(profile, localX, localY, localZ);
		if (houseState != null) {
			return houseState;
		}

		BlockState interiorState = getHouseInteriorState(profile, localX, localY, localZ);
		if (interiorState != null) {
			return interiorState;
		}

		return getHouseHallSceneryState(profile, x, localX, localY, localZ, z);
	}

	private static BlockState getHouseStructureState(HouseHallProfile profile, int localX, int localY, int localZ) {
		int houseHalf = getHouseHalf(profile);
		if (!isInsideHouse(profile, localX, localZ) && !isInsideRoofBounds(profile, localX, localZ)) {
			return null;
		}

		if (isHouseDoorColumn(profile, localX + profile.room().roomCenterX(), localZ + profile.room().roomCenterZ())
				&& localY == 1) {
			return OAK_DOOR_BLOCK
					.setValue(net.minecraft.world.level.block.DoorBlock.FACING, profile.doorFacing())
					.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.LOWER)
					.setValue(net.minecraft.world.level.block.DoorBlock.HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT)
					.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false)
					.setValue(net.minecraft.world.level.block.DoorBlock.POWERED, false);
		}
		if (isHouseDoorColumn(profile, localX + profile.room().roomCenterX(), localZ + profile.room().roomCenterZ())
				&& localY == 2) {
			return OAK_DOOR_BLOCK
					.setValue(net.minecraft.world.level.block.DoorBlock.FACING, profile.doorFacing())
					.setValue(net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.UPPER)
					.setValue(net.minecraft.world.level.block.DoorBlock.HINGE, net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT)
					.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false)
					.setValue(net.minecraft.world.level.block.DoorBlock.POWERED, false);
		}

		if (localY == 0 && isInsideHouse(profile, localX, localZ)) {
			return profile.woodTheme().planks().defaultBlockState();
		}

		BlockState boardedWindowSignState = getBoardedHouseWindowSignState(profile, localX, localY, localZ);
		if (boardedWindowSignState != null) {
			return boardedWindowSignState;
		}

		BlockState exteriorTorchState = getExteriorHouseTorchState(profile, localX, localY, localZ);
		if (exteriorTorchState != null) {
			return exteriorTorchState;
		}

		if (isOnHouseBoundary(profile, localX, localZ) && localY >= 1 && localY <= profile.wallHeight()) {
			if (isHouseWindow(profile, localX, localY, localZ)) {
				return createHouseWindowPaneState(profile, localX, localZ);
			}
			if (isHouseCorner(profile, localX, localZ)) {
				return profile.woodTheme().log().defaultBlockState();
			}
			return profile.woodTheme().planks().defaultBlockState();
		}

		return getHouseRoofState(profile, localX, localY, localZ, houseHalf);
	}

	private static BlockState getHouseRoofState(HouseHallProfile profile, int localX, int localY, int localZ, int houseHalf) {
		boolean ridgeAlongX = profile.doorFacing().getAxis() == Direction.Axis.Z;
		int across = ridgeAlongX ? Math.abs(localZ) : Math.abs(localX);
		int along = ridgeAlongX ? Math.abs(localX) : Math.abs(localZ);
		if (along > houseHalf + 1) {
			return null;
		}

		int roofBaseY = profile.wallHeight() + 1;
		int roofStep = localY - roofBaseY;
		if (roofStep < 0) {
			return null;
		}

		int roofExtent = houseHalf - roofStep;
		if (roofExtent < 0) {
			return null;
		}

		if (across <= roofExtent) {
			if (roofExtent == 0 && across == 0) {
				return profile.woodTheme().slab().defaultBlockState()
						.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
			}
			if (across == roofExtent) {
				return createRoofStair(profile, localX, localZ, ridgeAlongX);
			}
			return profile.woodTheme().planks().defaultBlockState();
		}

		return null;
	}

	private static BlockState createRoofStair(HouseHallProfile profile, int localX, int localZ, boolean ridgeAlongX) {
		Direction facing;
		if (ridgeAlongX) {
			facing = localZ < 0 ? Direction.SOUTH : Direction.NORTH;
		} else {
			facing = localX < 0 ? Direction.EAST : Direction.WEST;
		}
		return profile.woodTheme().stairs().defaultBlockState()
				.setValue(net.minecraft.world.level.block.StairBlock.FACING, facing)
				.setValue(net.minecraft.world.level.block.StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.BOTTOM);
	}

	private static BlockState getHouseInteriorState(HouseHallProfile profile, int localX, int localY, int localZ) {
		if (!isInsideHouse(profile, localX, localZ) || localY <= 0) {
			return null;
		}

		Direction inward = profile.doorFacing().getOpposite();
		Direction right = profile.doorFacing().getClockWise();
		int depth = localX * inward.getStepX() + localZ * inward.getStepZ();
		int side = localX * right.getStepX() + localZ * right.getStepZ();

		BlockState bedState = getHouseBedState(profile, localY, depth, side);
		if (bedState != null) {
			return bedState;
		}

		BlockState chestState = getHouseChestState(profile, localY, depth, side);
		if (chestState != null) {
			return chestState;
		}

		if (localY == 1 && depth == profile.furnaceDepth() && side == profile.furnaceSide()) {
			return Blocks.FURNACE.defaultBlockState()
					.setValue(net.minecraft.world.level.block.FurnaceBlock.FACING, getWallFacingFromDepthSide(profile, profile.furnaceDepth(), profile.furnaceSide()));
		}
		if (localY == 1 && depth == profile.tableDepth() && side == profile.tableSide()) {
			return Blocks.CRAFTING_TABLE.defaultBlockState();
		}
		if (isInteriorTorch(profile, localY, depth, side)) {
			return WALL_TORCH_BLOCK.setValue(WallTorchBlock.FACING, getTorchFacingFromDepthSide(profile, depth, side));
		}
		if (isUpperCornerBookshelf(profile, localY, depth, side)) {
			return Blocks.BOOKSHELF.defaultBlockState();
		}
		if (localY == 1 && isHouseCarpet(profile, depth, side)) {
			return profile.carpetBlock().defaultBlockState();
		}

		return null;
	}

	private static BlockState getHouseBedState(HouseHallProfile profile, int localY, int depth, int side) {
		if (localY != 1) {
			return null;
		}

		BlockState bedState = getPlacedBedState(profile, depth, side, profile.bedDepth(), profile.bedSide(), profile.bedFacing());
		if (bedState != null) {
			return bedState;
		}
		if (profile.bedCount() >= 2) {
			return getPlacedBedState(profile, depth, side, profile.secondBedDepth(), profile.secondBedSide(), profile.secondBedFacing());
		}
		return null;
	}

	private static BlockState createBedState(Direction facing, boolean foot) {
		return Blocks.RED_BED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.BedBlock.FACING, facing)
				.setValue(net.minecraft.world.level.block.BedBlock.PART, foot ? BedPart.FOOT : BedPart.HEAD)
				.setValue(net.minecraft.world.level.block.BedBlock.OCCUPIED, false);
	}

	private static BlockState getHouseChestState(HouseHallProfile profile, int localY, int depth, int side) {
		if (localY != 1) {
			return null;
		}

		if (profile.chestMode() == 2) {
			if (depth == profile.chestDepth() && side == profile.chestSide()) {
				return createHouseChestState(profile.chestFacing(), getDoubleChestType(profile, true));
			}
			if (depth == profile.secondChestDepth() && side == profile.secondChestSide()) {
				return createHouseChestState(profile.chestFacing(), getDoubleChestType(profile, false));
			}
			return null;
		}

		if (depth == profile.chestDepth() && side == profile.chestSide()) {
			return createHouseChestState(profile.chestFacing(), ChestType.SINGLE);
		}
		if (profile.chestMode() == 1 && depth == profile.secondChestDepth() && side == profile.secondChestSide()) {
			return createHouseChestState(profile.secondChestFacing(), ChestType.SINGLE);
		}
		return null;
	}

	private static BlockState createHouseChestState(Direction facing, ChestType chestType) {
		return CHEST_BLOCK
				.setValue(ChestBlock.FACING, facing)
				.setValue(ChestBlock.TYPE, chestType)
				.setValue(ChestBlock.WATERLOGGED, false);
	}

	private static ChestType getDoubleChestType(HouseHallProfile profile, boolean primaryHalf) {
		int firstX = toLocalX(profile, profile.chestDepth(), profile.chestSide());
		int firstZ = toLocalZ(profile, profile.chestDepth(), profile.chestSide());
		int secondX = toLocalX(profile, profile.secondChestDepth(), profile.secondChestSide());
		int secondZ = toLocalZ(profile, profile.secondChestDepth(), profile.secondChestSide());
		int deltaX = secondX - firstX;
		int deltaZ = secondZ - firstZ;
		Direction right = profile.chestFacing().getClockWise();
		boolean firstIsLeft = deltaX * right.getStepX() + deltaZ * right.getStepZ() > 0;
		if (primaryHalf) {
			return firstIsLeft ? ChestType.LEFT : ChestType.RIGHT;
		}
		return firstIsLeft ? ChestType.RIGHT : ChestType.LEFT;
	}

	private static boolean isHouseCarpet(HouseHallProfile profile, int depth, int side) {
		return Math.abs(depth - profile.carpetCenterDepth()) <= profile.carpetRadiusDepth()
				&& Math.abs(side - profile.carpetCenterSide()) <= profile.carpetRadiusSide();
	}

	private static BlockState getHouseHallSceneryState(
			HouseHallProfile profile,
			int worldX,
			int localX,
			int localY,
			int localZ,
			int worldZ
	) {
		if (isInsideHouse(profile, localX, localZ) || isInsideRoofBounds(profile, localX, localZ)) {
			return null;
		}

		BlockState treeState = getWeirdTreeState(profile, localX, localY, localZ);
		if (treeState != null) {
			return treeState;
		}

		BlockState wellState = getWellState(profile, localX, localY, localZ);
		if (wellState != null) {
			return wellState;
		}

		int trashHeight = getHouseHallTrashHeight(profile, worldX, worldZ);
		if (trashHeight > 0 && localY >= 1 && localY <= trashHeight) {
			return randomTrashBlockFromSeed(mix(worldX, worldZ, profile.seed() ^ (0x484F555345545241L + localY * 31L)));
		}

		return null;
	}

	private static int getHouseHallTrashHeight(HouseHallProfile profile, int x, int z) {
		if (!profile.hasTrashPiles()) {
			return 0;
		}
		int height = 0;
		for (int pileIndex = 0; pileIndex < profile.outsidePileCount(); pileIndex++) {
			PileSpec pile = getHouseHallPile(profile, pileIndex);
			int dx = Math.abs(x - pile.centerX());
			int dz = Math.abs(z - pile.centerZ());
			if (dx > pile.radiusX() || dz > pile.radiusZ()) {
				continue;
			}

			int pileHeight = 1;
			if (dx <= Math.max(0, pile.radiusX() - 1) && dz <= Math.max(0, pile.radiusZ() - 1)) {
				pileHeight = 2;
			}
			if (pile.height() >= 3 && dx == 0 && dz == 0) {
				pileHeight = 3;
			}

			long roughnessSeed = mix(x + pileIndex * 11, z - pileIndex * 13, profile.seed() ^ 0x484F555345524F55L);
			if (positiveMod(roughnessSeed, 100) < 24 && !(dx == 0 && dz == 0)) {
				pileHeight = 0;
			}
			height = Math.max(height, pileHeight);
		}
		return height;
	}

	private static PileSpec getHouseHallPile(HouseHallProfile profile, int pileIndex) {
		int houseHalf = getHouseHalf(profile);
		int edgeBand = profile.room().roomHalfWidth() - 5;
		long salt = profile.seed() ^ (0x48484C50494C4553L + pileIndex * 41L);
		boolean xEdge = ((salt >>> 3) & 1L) == 0L;
		int signPrimary = ((salt >>> 7) & 1L) == 0L ? -1 : 1;
		int signSecondary = ((salt >>> 11) & 1L) == 0L ? -1 : 1;
		int centerX = xEdge
				? profile.room().roomCenterX() + signPrimary * edgeBand
				: profile.room().roomCenterX() + signSecondary * (houseHalf + 4 + positiveMod(salt ^ 0x11L, Math.max(1, profile.room().roomHalfWidth() - houseHalf - 7)));
		int centerZ = xEdge
				? profile.room().roomCenterZ() + signSecondary * (houseHalf + 4 + positiveMod(salt ^ 0x22L, Math.max(1, profile.room().roomHalfHeight() - houseHalf - 7)))
				: profile.room().roomCenterZ() + signPrimary * edgeBand;
		int radiusX = 1 + positiveMod(salt ^ 0x33L, 2);
		int radiusZ = 1 + positiveMod(salt ^ 0x44L, 2);
		int height = 2 + positiveMod(salt ^ 0x55L, 2);
		return new PileSpec(centerX, centerZ, radiusX, radiusZ, height);
	}

	private static BlockState randomTrashBlockFromSeed(long seed) {
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

	private static BlockState getWeirdTreeState(HouseHallProfile profile, int localX, int localY, int localZ) {
		if (!profile.hasTree() || profile.treeCorner() < 0) {
			return null;
		}

		Corner corner = Corner.VALUES[profile.treeCorner()];
		int trunkX = corner.signX * (profile.room().roomHalfWidth() - 5);
		int trunkZ = corner.signZ * (profile.room().roomHalfHeight() - 5);
		if (localX == trunkX && localZ == trunkZ && localY >= 1 && localY <= 4) {
			return Blocks.OAK_LOG.defaultBlockState();
		}

		if (localY >= 4 && localY <= 6) {
			int dx = Math.abs(localX - trunkX);
			int dz = Math.abs(localZ - trunkZ);
			int radius = localY == 6 ? 1 : 2;
			if (dx <= radius && dz <= radius && !(dx == 0 && dz == 0 && localY == 4)) {
				return Blocks.OAK_LEAVES.defaultBlockState();
			}
		}
		return null;
	}

	private static BlockState getWellState(HouseHallProfile profile, int localX, int localY, int localZ) {
		if (!profile.hasWell() || profile.wellCorner() < 0) {
			return null;
		}

		Corner corner = Corner.VALUES[profile.wellCorner()];
		int centerX = corner.signX * (profile.room().roomHalfWidth() - 4);
		int centerZ = corner.signZ * (profile.room().roomHalfHeight() - 4);
		int dx = localX - centerX;
		int dz = localZ - centerZ;
		if (Math.abs(dx) > 1 || Math.abs(dz) > 1) {
			return null;
		}

		if (localY == 3 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
			return profile.woodTheme().planks().defaultBlockState();
		}
		if (localY >= 1 && localY <= 2 && Math.abs(dx) == 1 && Math.abs(dz) == 1) {
			return Blocks.COBBLESTONE_WALL.defaultBlockState();
		}

		if (localY == 0) {
			if (dx == 0 && dz == 0) {
				return Blocks.WATER.defaultBlockState();
			}
			return (Math.abs(dx) == 1 && Math.abs(dz) == 1)
					? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
					: Blocks.COBBLESTONE.defaultBlockState();
		}
		if (localY == 1 || localY == 2) {
			if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
				return Blocks.COBBLESTONE_WALL.defaultBlockState();
			}
			if (dx == 0 && dz == 0) {
				return AIR;
			}
		}
		return null;
	}

	private static HouseHallProfile buildHouseHallProfile(BackroomsLayout.SpecialRoomPlacement room) {
		long seed = mix(room.cellX(), room.cellZ(), HOUSE_HALL_LAYOUT_SALT ^ levelSalt(room.baseLevelIndex()));
		Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		Block[] carpetPalette = {Blocks.RED_CARPET, Blocks.GREEN_CARPET, Blocks.BROWN_CARPET, Blocks.GRAY_CARPET};
		Direction doorFacing = directions[positiveMod(seed ^ 0x484F555345444F4FL, directions.length)];
		int structureCountRoll = positiveMod(seed ^ 0x535452554354434EL, 100);
		int structureCount = structureCountRoll < 15 ? 0 : (structureCountRoll < 75 ? 1 : 2);
		boolean[] chosenFeatures = chooseHouseHallFeatures(seed, structureCount);
		int treeCorner = chosenFeatures[1]
				? positiveMod(seed ^ 0x54524545434F524EL, Corner.VALUES.length)
				: -1;
		int wellCorner = chosenFeatures[2]
				? positiveMod(seed ^ 0x57454C4C434F524EL, Corner.VALUES.length)
				: -1;
		if (wellCorner == treeCorner && wellCorner >= 0 && treeCorner >= 0) {
			wellCorner = (wellCorner + 1) % Corner.VALUES.length;
		}
		int houseSize = 7;
		int houseHalf = houseSize / 2;
		int carpetCenterDepth = positiveMod(seed ^ 0x4341525043545244L, 3) - 1;
		int carpetCenterSide = positiveMod(seed ^ 0x4341525043545253L, 3) - 1;
		int furnaceWall = positiveMod(seed ^ 0x4655524E57414C4CL, 4);
		int[] furnacePlacement = pickWallPlacement(furnaceWall, seed ^ 0x4655524E41434553L, houseHalf);
		int furnaceDepth = furnacePlacement[0];
		int furnaceSide = furnacePlacement[1];
		int tableWall = nextDistinctWall(positiveMod(seed ^ 0x5441424C4557414CL, 4), furnaceWall);
		int[] tablePlacement = pickWallPlacement(tableWall, seed ^ 0x5441424C45534944L, houseHalf);
		int tableDepth = tablePlacement[0];
		int tableSide = tablePlacement[1];
		int chestModeRoll = positiveMod(seed ^ 0x43484553544D4F44L, 100);
		int chestMode = chestModeRoll < 34 ? 2 : (chestModeRoll < 73 ? 1 : 0);
		int chestWall = nextDistinctWall(positiveMod(seed ^ 0x434845535457414CL, 4), furnaceWall, tableWall);
		int[] chestPlacement = pickWallPlacement(chestWall, seed ^ 0x4348455354534944L, houseHalf);
		int chestDepth = chestPlacement[0];
		int chestSide = chestPlacement[1];
		int secondChestDepth = chestDepth;
		int secondChestSide = chestSide;
		if (chestMode == 2) {
			int[] pairedChest = getAdjacentChestPlacement(chestWall, chestDepth, chestSide, houseHalf);
			secondChestDepth = pairedChest[0];
			secondChestSide = pairedChest[1];
		} else if (chestMode == 1) {
			int secondChestWall = nextDistinctWall(positiveMod(seed ^ 0x534543434857414CL, 4), furnaceWall, tableWall, chestWall);
			int[] secondPlacement = pickWallPlacement(secondChestWall, seed ^ 0x5345434348534944L, houseHalf);
			secondChestDepth = secondPlacement[0];
			secondChestSide = secondPlacement[1];
		}
		int bedCount = 1 + positiveMod(seed ^ 0x424544434F554E54L, 2);
		int bedWall = positiveMod(seed ^ 0x42454457414C4C31L, 4);
		BedPlacement firstBed = pickBedPlacement(bedWall, seed ^ 0x424544504C434131L, houseHalf, doorFacing);
		BedPlacement secondBed = firstBed;
		if (furnaceDepth == firstBed.footDepth() && furnaceSide == firstBed.footSide()) {
			int[] replacement = pickWallPlacement(nextDistinctWall(furnaceWall + 1, tableWall, chestWall), seed ^ 0x4655524E5245504CL, houseHalf);
			furnaceDepth = replacement[0];
			furnaceSide = replacement[1];
		}
		if (tableDepth == firstBed.footDepth() && tableSide == firstBed.footSide()) {
			int[] replacement = pickWallPlacement(nextDistinctWall(tableWall + 1, furnaceWall, chestWall), seed ^ 0x5441424C45524550L, houseHalf);
			tableDepth = replacement[0];
			tableSide = replacement[1];
		}
		if (tableDepth == furnaceDepth && tableSide == furnaceSide) {
			tableSide = rotateWallSide(tableSide, houseHalf);
		}
		if (chestMode == 2) {
			if ((chestDepth == firstBed.footDepth() && chestSide == firstBed.footSide())
					|| (secondChestDepth == firstBed.footDepth() && secondChestSide == firstBed.footSide())) {
				int[] replacement = pickWallPlacement(nextDistinctWall(chestWall + 1, furnaceWall, tableWall), seed ^ 0x4348455354524550L, houseHalf);
				chestDepth = replacement[0];
				chestSide = replacement[1];
				int[] pairedChest = getAdjacentChestPlacement(chestWall, chestDepth, chestSide, houseHalf);
				secondChestDepth = pairedChest[0];
				secondChestSide = pairedChest[1];
			}
		} else if (chestDepth == firstBed.footDepth() && chestSide == firstBed.footSide()) {
			int[] replacement = pickWallPlacement(nextDistinctWall(chestWall + 1, furnaceWall, tableWall), seed ^ 0x4348455354524550L, houseHalf);
			chestDepth = replacement[0];
			chestSide = replacement[1];
		}
		if (chestMode == 1 && secondChestDepth == firstBed.footDepth() && secondChestSide == firstBed.footSide()) {
			int[] replacement = pickWallPlacement(nextDistinctWall(chestWall + 2, furnaceWall, tableWall, chestWall), seed ^ 0x5345434348524550L, houseHalf);
			secondChestDepth = replacement[0];
			secondChestSide = replacement[1];
		}
		firstBed = resolveBedPlacementConflicts(
				firstBed,
				houseHalf,
				seed ^ 0x424544524553314CL,
				doorFacing,
				furnaceDepth,
				furnaceSide,
				tableDepth,
				tableSide,
				chestDepth,
				chestSide,
				secondChestDepth,
				secondChestSide
		);
		if (bedCount >= 2) {
			int secondBedWall = nextDistinctWall(positiveMod(seed ^ 0x42454457414C4C32L, 4), bedWall);
			secondBed = pickBedPlacement(secondBedWall, seed ^ 0x424544504C434132L, houseHalf, doorFacing);
			secondBed = resolveBedPlacementConflicts(
					secondBed,
					houseHalf,
					seed ^ 0x424544524553324CL,
					doorFacing,
					firstBed.footDepth(),
					firstBed.footSide(),
					furnaceDepth,
					furnaceSide,
					tableDepth,
					tableSide,
					chestDepth,
					chestSide,
					secondChestDepth,
					secondChestSide
			);
		}
		Direction chestFacing = getWallFacingFromDepthSide(doorFacing, houseHalf, chestDepth, chestSide);
		Direction secondChestFacing = getWallFacingFromDepthSide(doorFacing, houseHalf, secondChestDepth, secondChestSide);
		return new HouseHallProfile(
				room,
				seed,
				room.roomCenterX(),
				room.roomCenterZ(),
				houseSize,
				3 + positiveMod(seed ^ 0x484F55534557414CL, 2),
				doorFacing,
				WoodTheme.OAK,
				positiveMod(seed ^ 0x46414B45444F4F52L, 100) < 20,
				positiveMod(seed ^ 0x5741495453544B52L, 100) < 20,
				chosenFeatures[0] ? 1 + positiveMod(seed ^ 0x4F55545349444550L, 2) : 0,
				chosenFeatures[0],
				chosenFeatures[1],
				chosenFeatures[2],
				treeCorner,
				wellCorner,
				chestMode,
				chestDepth,
				chestSide,
				chestFacing,
				secondChestDepth,
				secondChestSide,
				secondChestFacing,
				bedCount,
				firstBed.footDepth(),
				firstBed.footSide(),
				secondBed.footDepth(),
				secondBed.footSide(),
				firstBed.facing(),
				secondBed.facing(),
				positiveMod(seed ^ 0x424F4F4B5348454CL, 4),
				2 + positiveMod(seed ^ 0x424F4F4B53434E54L, 4),
				furnaceDepth,
				furnaceSide,
				tableDepth,
				tableSide,
				carpetCenterDepth,
				carpetCenterSide,
				1 + positiveMod(seed ^ 0x4341525052444450L, 2),
				1 + positiveMod(seed ^ 0x4341525052445349L, 2),
				carpetPalette[positiveMod(seed ^ 0x4341525045545344L, carpetPalette.length)]
		);
	}

	private static boolean[] chooseHouseHallFeatures(long seed, int structureCount) {
		boolean[] selected = new boolean[3]; // trash, tree, well
		if (structureCount <= 0) {
			return selected;
		}

		int start = positiveMod(seed ^ 0x484F555345465445L, 3);
		for (int i = 0; i < structureCount; i++) {
			selected[(start + i) % 3] = true;
		}
		return selected;
	}

	private static int[] pickWallPlacement(int wallIndex, long seed, int houseHalf) {
		int normalizedWall = positiveMod(wallIndex, 4);
		int wallBand = Math.max(1, houseHalf - 1);
		int sideSpan = Math.max(1, houseHalf - 2);
		int offset = positiveMod(seed, sideSpan * 2 + 1) - sideSpan;
		return switch (normalizedWall) {
			case 0 -> new int[]{-wallBand, offset};
			case 1 -> new int[]{wallBand, offset};
			case 2 -> new int[]{offset, -wallBand};
			default -> new int[]{offset, wallBand};
		};
	}

	private static int nextDistinctWall(int preferredWall, int... usedWalls) {
		int normalized = positiveMod(preferredWall, 4);
		for (int step = 0; step < 4; step++) {
			int candidate = (normalized + step) % 4;
			boolean used = false;
			for (int wall : usedWalls) {
				if (candidate == positiveMod(wall, 4)) {
					used = true;
					break;
				}
			}
			if (!used) {
				return candidate;
			}
		}
		return normalized;
	}

	private static int[] getAdjacentChestPlacement(int wallIndex, int depth, int side, int houseHalf) {
		int normalizedWall = positiveMod(wallIndex, 4);
		int sideSpan = Math.max(1, houseHalf - 2);
		if (normalizedWall == 0 || normalizedWall == 1) {
			int secondSide = side < sideSpan ? side + 1 : side - 1;
			return new int[]{depth, secondSide};
		}
		int secondDepth = depth < sideSpan ? depth + 1 : depth - 1;
		return new int[]{secondDepth, side};
	}

	private static BedPlacement pickBedPlacement(int wallIndex, long seed, int houseHalf, Direction doorFacing) {
		int normalizedWall = positiveMod(wallIndex, 4);
		int span = Math.max(1, houseHalf - 2);
		int offset = positiveMod(seed, span * 2 + 1) - span;
		return switch (normalizedWall) {
			case 0 -> new BedPlacement(-(houseHalf - 2), offset, getWallFacingFromDepthSide(doorFacing, houseHalf, -(houseHalf - 1), offset));
			case 1 -> new BedPlacement(houseHalf - 2, offset, getWallFacingFromDepthSide(doorFacing, houseHalf, houseHalf - 1, offset));
			case 2 -> new BedPlacement(offset, -(houseHalf - 2), getWallFacingFromDepthSide(doorFacing, houseHalf, offset, -(houseHalf - 1)));
			default -> new BedPlacement(offset, houseHalf - 2, getWallFacingFromDepthSide(doorFacing, houseHalf, offset, houseHalf - 1));
		};
	}

	private static int pickWallDepth(long seed, int houseHalf, int furnitureBand) {
		if ((seed & 1L) == 0L) {
			return furnitureBand;
		}
		return -furnitureBand;
	}

	private static int pickWallSide(long seed, int houseHalf, int furnitureBand) {
		int max = Math.max(1, furnitureBand);
		int raw = positiveMod(seed, max * 2 + 1) - max;
		if (Math.abs(raw) < houseHalf - 2) {
			return raw;
		}
		return raw < 0 ? -(houseHalf - 2) : (houseHalf - 2);
	}

	private static int rotateWallSide(int side, int houseHalf) {
		int limit = Math.max(1, houseHalf - 2);
		int next = side + 2;
		if (next > limit) {
			next = -limit;
		}
		return next;
	}

	private static int pickBedDepth(long seed, int houseHalf, Direction bedFacing, Direction doorFacing) {
		int band = Math.max(1, houseHalf - 2);
		if (bedFacing == doorFacing.getOpposite()) {
			return houseHalf - 3;
		}
		if (bedFacing == doorFacing) {
			return -(houseHalf - 3);
		}
		return (seed & 1L) == 0L ? band : -band;
	}

	private static int pickBedSide(long seed, int houseHalf) {
		int limit = Math.max(1, houseHalf - 2);
		return ((seed & 1L) == 0L ? -1 : 1) * limit;
	}

	private static int rotateBedSide(int side, int houseHalf) {
		int limit = Math.max(1, houseHalf - 2);
		if (side >= limit) {
			return -limit;
		}
		return Math.min(limit, side + 2);
	}

	private static BedPlacement resolveBedPlacementConflicts(
			BedPlacement placement,
			int houseHalf,
			long seed,
			Direction doorFacing,
			int... occupiedPairs
	) {
		BedPlacement current = placement;
		for (int attempt = 0; attempt < 6; attempt++) {
			if (!bedPlacementConflicts(current, occupiedPairs)) {
				return current;
			}
			int nextWall = positiveMod(getWallIndexForFacing(current.facing(), doorFacing) + 1 + attempt, 4);
			current = pickBedPlacement(nextWall, seed ^ (attempt * 31L), houseHalf, doorFacing);
		}
		return current;
	}

	private static boolean bedPlacementConflicts(BedPlacement placement, int... occupiedPairs) {
		for (int index = 0; index + 1 < occupiedPairs.length; index += 2) {
			if (occupiedPairs[index] == placement.footDepth() && occupiedPairs[index + 1] == placement.footSide()) {
				return true;
			}
		}
		return false;
	}

	private static int getWallIndexForFacing(Direction facing, Direction doorFacing) {
		if (facing == doorFacing.getOpposite()) {
			return 0;
		}
		if (facing == doorFacing) {
			return 1;
		}
		if (facing == doorFacing.getClockWise()) {
			return 2;
		}
		return 3;
	}

	private static BlockState getPlacedBedState(
			HouseHallProfile profile,
			int depth,
			int side,
			int footDepth,
			int footSide,
			Direction bedFacing
	) {
		int headDepth = footDepth + getDepthDeltaForFacing(profile.doorFacing(), bedFacing);
		int headSide = footSide + getSideDeltaForFacing(profile.doorFacing(), bedFacing);
		if (depth == footDepth && side == footSide) {
			return createBedState(bedFacing, true);
		}
		if (depth == headDepth && side == headSide) {
			return createBedState(bedFacing, false);
		}
		return null;
	}

	private static int getDepthDeltaForFacing(Direction doorFacing, Direction facing) {
		Direction inward = doorFacing.getOpposite();
		return facing.getStepX() * inward.getStepX() + facing.getStepZ() * inward.getStepZ();
	}

	private static int getSideDeltaForFacing(Direction doorFacing, Direction facing) {
		Direction right = doorFacing.getClockWise();
		return facing.getStepX() * right.getStepX() + facing.getStepZ() * right.getStepZ();
	}

	private static int getHouseHallBaseFloorY(HouseHallProfile profile) {
		return BackroomsLayout.FLOOR_Y + profile.room().baseLevelIndex() * BackroomsLayout.LEVEL_HEIGHT;
	}

	private static int getHouseHalf(HouseHallProfile profile) {
		return profile.houseSize() / 2;
	}

	private static boolean isInsideHouse(HouseHallProfile profile, int localX, int localZ) {
		int houseHalf = getHouseHalf(profile);
		return Math.abs(localX) <= houseHalf && Math.abs(localZ) <= houseHalf;
	}

	private static boolean isInsideRoofBounds(HouseHallProfile profile, int localX, int localZ) {
		int houseHalf = getHouseHalf(profile) + 1;
		return Math.abs(localX) <= houseHalf && Math.abs(localZ) <= houseHalf;
	}

	private static boolean isOnHouseBoundary(HouseHallProfile profile, int localX, int localZ) {
		if (!isInsideHouse(profile, localX, localZ)) {
			return false;
		}
		int houseHalf = getHouseHalf(profile);
		return Math.abs(localX) == houseHalf || Math.abs(localZ) == houseHalf;
	}

	private static boolean isHouseCorner(HouseHallProfile profile, int localX, int localZ) {
		int houseHalf = getHouseHalf(profile);
		return Math.abs(localX) == houseHalf && Math.abs(localZ) == houseHalf;
	}

	private static boolean isHouseDoorColumn(HouseHallProfile profile, int x, int z) {
		int localX = x - profile.houseCenterX();
		int localZ = z - profile.houseCenterZ();
		int houseHalf = getHouseHalf(profile);
		return switch (profile.doorFacing()) {
			case NORTH -> localX == 0 && localZ == -houseHalf;
			case SOUTH -> localX == 0 && localZ == houseHalf;
			case WEST -> localZ == 0 && localX == -houseHalf;
			case EAST -> localZ == 0 && localX == houseHalf;
			default -> false;
		};
	}

	private static boolean isHouseWindow(HouseHallProfile profile, int localX, int localY, int localZ) {
		if (localY != 2) {
			return false;
		}
		if (isHouseDoorColumn(profile, localX + profile.room().roomCenterX(), localZ + profile.room().roomCenterZ())
				|| isHouseCorner(profile, localX, localZ)
				|| !isOnHouseBoundary(profile, localX, localZ)) {
			return false;
		}
		long windowSeed = mix(localX, localZ, profile.seed() ^ 0x57494E444F574C41L);
		return positiveMod(windowSeed, 100) < 29;
	}

	private static BlockState createHouseWindowPaneState(HouseHallProfile profile, int localX, int localZ) {
		BlockState state = Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.WATERLOGGED, false);
		if (Math.abs(localZ) == getHouseHalf(profile)) {
			return state
					.setValue(IronBarsBlock.EAST, true)
					.setValue(IronBarsBlock.WEST, true)
					.setValue(IronBarsBlock.NORTH, false)
					.setValue(IronBarsBlock.SOUTH, false);
		}
		return state
				.setValue(IronBarsBlock.NORTH, true)
				.setValue(IronBarsBlock.SOUTH, true)
				.setValue(IronBarsBlock.EAST, false)
				.setValue(IronBarsBlock.WEST, false);
	}

	private static boolean isBoardedHouseWindow(HouseHallProfile profile, int localX, int localY, int localZ) {
		if (!hasDenseBoardedWindowSigns(profile)) {
			return false;
		}
		long seed = mix(localX, localZ, profile.seed() ^ (0x57494E444F57424FL + localY * 17L));
		return positiveMod(seed, 100) < 92;
	}

	private static BlockState getBoardedHouseWindowSignState(HouseHallProfile profile, int localX, int localY, int localZ) {
		if (localY != 2 || !isInsideHouse(profile, localX, localZ)) {
			return null;
		}

		int depth = getHouseDepth(profile, localX, localZ);
		int side = getHouseSide(profile, localX, localZ);
		int houseHalf = getHouseHalf(profile);
		int wallDepth = depth;
		int wallSide = side;
		if (depth == houseHalf - 1) {
			wallDepth = houseHalf;
		} else if (depth == -(houseHalf - 1)) {
			wallDepth = -houseHalf;
		} else if (side == houseHalf - 1) {
			wallSide = houseHalf;
		} else if (side == -(houseHalf - 1)) {
			wallSide = -houseHalf;
		} else {
			return null;
		}

		int windowLocalX = toLocalX(profile, wallDepth, wallSide);
		int windowLocalZ = toLocalZ(profile, wallDepth, wallSide);
		if (!isHouseWindow(profile, windowLocalX, localY, windowLocalZ)
				|| !isBoardedHouseWindow(profile, windowLocalX, localY, windowLocalZ)) {
			return null;
		}

		return OAK_WALL_SIGN_BLOCK.setValue(WallSignBlock.FACING, getTorchFacingFromDepthSide(profile, depth, side));
	}

	private static boolean hasDenseBoardedWindowSigns(HouseHallProfile profile) {
		return positiveMod(profile.seed() ^ 0x5349474E4D4F4445L, 100) < 45;
	}

	private static BlockState getExteriorHouseTorchState(HouseHallProfile profile, int localX, int localY, int localZ) {
		if (localY != 2) {
			return null;
		}

		int depth = getHouseDepth(profile, localX, localZ);
		int side = getHouseSide(profile, localX, localZ);
		if (isSelectedHouseTorchSlot(profile, depth, side, true)) {
			return WALL_TORCH_BLOCK.setValue(WallTorchBlock.FACING, getExteriorTorchFacing(profile, depth, side));
		}
		return null;
	}

	private static boolean isInteriorTorch(HouseHallProfile profile, int localY, int depth, int side) {
		if (localY != 2) {
			return false;
		}
		return isSelectedHouseTorchSlot(profile, depth, side, false);
	}

	private static Direction getTorchFacingFromDepthSide(HouseHallProfile profile, int depth, int side) {
		int houseHalf = getHouseHalf(profile);
		if (depth == houseHalf - 1) {
			return profile.doorFacing();
		}
		if (depth == -(houseHalf - 1)) {
			return profile.doorFacing().getOpposite();
		}
		if (side == houseHalf - 1) {
			return profile.doorFacing().getCounterClockWise();
		}
		if (side == -(houseHalf - 1)) {
			return profile.doorFacing().getClockWise();
		}
		if (side > 0) {
			return profile.doorFacing().getCounterClockWise();
		}
		return profile.doorFacing().getClockWise();
	}

	private static Direction getWallFacingFromDepthSide(HouseHallProfile profile, int depth, int side) {
		return getWallFacingFromDepthSide(profile.doorFacing(), getHouseHalf(profile), depth, side);
	}

	private static Direction getWallFacingFromDepthSide(Direction doorFacing, int houseHalf, int depth, int side) {
		if (depth == houseHalf - 1) {
			return doorFacing;
		}
		if (depth == -(houseHalf - 1)) {
			return doorFacing.getOpposite();
		}
		if (side == houseHalf - 1) {
			return doorFacing.getCounterClockWise();
		}
		return doorFacing.getClockWise();
	}

	private static boolean isUpperCornerBookshelf(HouseHallProfile profile, int localY, int depth, int side) {
		if (localY != profile.wallHeight()) {
			return false;
		}
		int slot = bookshelfSlotIndex(profile, depth, side);
		return slot >= 0 && isSelectedBookshelfSlot(profile, slot);
	}

	private static int bookshelfSlotIndex(HouseHallProfile profile, int depth, int side) {
		int corner = getHouseHalf(profile) - 1;
		int nearCorner = Math.max(1, corner - 1);
		int[][] slots = createBookshelfSlots(profile, corner, nearCorner);
		for (int index = 0; index < slots.length; index++) {
			if (depth == slots[index][0] && side == slots[index][1]) {
				return index;
			}
		}
		return -1;
	}

	private static int[][] createBookshelfSlots(HouseHallProfile profile, int corner, int nearCorner) {
		if ((profile.bookshelfVariant() & 1) == 0) {
			return new int[][]{
					{-corner, -corner},
					{-corner, corner},
					{corner, corner},
					{corner, -corner},
					{-nearCorner, -corner},
					{-nearCorner, corner},
					{nearCorner, corner},
					{nearCorner, -corner}
			};
		}
		return new int[][]{
				{-corner, -corner},
				{-corner, corner},
				{corner, corner},
				{corner, -corner},
				{-corner, -nearCorner},
				{-corner, nearCorner},
				{corner, nearCorner},
				{corner, -nearCorner}
		};
	}

	private static boolean isSelectedBookshelfSlot(HouseHallProfile profile, int slot) {
		int slotCount = 8;
		boolean[] used = new boolean[slotCount];
		for (int index = 0; index < profile.bookshelfCount(); index++) {
			int picked = positiveMod(profile.seed() ^ (0x424F4F4B534C4F54L + index * 17L), slotCount);
			while (used[picked]) {
				picked = (picked + 1) % slotCount;
			}
			used[picked] = true;
			if (picked == slot) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSelectedHouseTorchSlot(HouseHallProfile profile, int depth, int side, boolean exterior) {
		int[][] slots = createTorchSlots(profile, exterior);
		int matchedIndex = -1;
		for (int index = 0; index < slots.length; index++) {
			if (depth == slots[index][0] && side == slots[index][1]) {
				matchedIndex = index;
				break;
			}
		}
		if (matchedIndex < 0) {
			return false;
		}

		int selected = 0;
		boolean[] used = new boolean[slots.length];
		for (int attempt = 0; attempt < slots.length && selected < 2; attempt++) {
			int picked = positiveMod(profile.seed() ^ (exterior ? 0x544F524348455854L : 0x544F524348494E54L) ^ (attempt * 19L), slots.length);
			while (used[picked]) {
				picked = (picked + 1) % slots.length;
			}
			used[picked] = true;
			int wallDepth = exterior ? clampTorchWallCoordinate(slots[picked][0], getHouseHalf(profile)) : slots[picked][0];
			int wallSide = exterior ? clampTorchWallCoordinate(slots[picked][1], getHouseHalf(profile)) : slots[picked][1];
			if (isTorchAttachmentBlocked(profile, 2, wallDepth, wallSide)) {
				continue;
			}
			selected++;
			if (picked == matchedIndex) {
				return true;
			}
		}
		return false;
	}

	private static int[][] createTorchSlots(HouseHallProfile profile, boolean exterior) {
		int houseHalf = getHouseHalf(profile);
		int near = 1;
		if (exterior) {
			int outer = houseHalf + 1;
			return new int[][]{
					{-outer, -near},
					{-outer, near},
					{outer, -near},
					{outer, near},
					{-near, -outer},
					{near, -outer},
					{-near, outer},
					{near, outer}
			};
		}
		int inner = houseHalf - 1;
		return new int[][]{
				{-inner, -near},
				{-inner, near},
				{inner, -near},
				{inner, near},
				{-near, -inner},
				{near, -inner},
				{-near, inner},
				{near, inner}
		};
	}

	private static int clampTorchWallCoordinate(int value, int houseHalf) {
		if (value > houseHalf) {
			return houseHalf;
		}
		if (value < -houseHalf) {
			return -houseHalf;
		}
		return value;
	}

	private static Direction getExteriorTorchFacing(HouseHallProfile profile, int depth, int side) {
		int houseHalf = getHouseHalf(profile);
		if (Math.abs(depth) > Math.abs(side)) {
			return getWallFacingFromDepthSide(profile.doorFacing(), houseHalf, depth < 0 ? -(houseHalf - 1) : houseHalf - 1, 0).getOpposite();
		}
		return getWallFacingFromDepthSide(profile.doorFacing(), houseHalf, 0, side < 0 ? -(houseHalf - 1) : houseHalf - 1).getOpposite();
	}

	private static boolean isTorchAttachmentBlocked(HouseHallProfile profile, int localY, int wallDepth, int wallSide) {
		int localX = toLocalX(profile, wallDepth, wallSide);
		int localZ = toLocalZ(profile, wallDepth, wallSide);
		if (isHouseDoorColumn(profile, localX + profile.room().roomCenterX(), localZ + profile.room().roomCenterZ())) {
			return true;
		}
		return isHouseWindow(profile, localX, localY, localZ);
	}

	private static int getHouseDepth(HouseHallProfile profile, int localX, int localZ) {
		Direction inward = profile.doorFacing().getOpposite();
		return localX * inward.getStepX() + localZ * inward.getStepZ();
	}

	private static int getHouseSide(HouseHallProfile profile, int localX, int localZ) {
		Direction right = profile.doorFacing().getClockWise();
		return localX * right.getStepX() + localZ * right.getStepZ();
	}

	private static int toLocalX(HouseHallProfile profile, int depth, int side) {
		Direction inward = profile.doorFacing().getOpposite();
		Direction right = profile.doorFacing().getClockWise();
		return depth * inward.getStepX() + side * right.getStepX();
	}

	private static int toLocalZ(HouseHallProfile profile, int depth, int side) {
		Direction inward = profile.doorFacing().getOpposite();
		Direction right = profile.doorFacing().getClockWise();
		return depth * inward.getStepZ() + side * right.getStepZ();
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

	private static BlockState createLadderState(Direction facing) {
		return LADDER_BLOCK.setValue(LadderBlock.FACING, facing);
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

	private record PlusMazeProfile(
			BackroomsLayout.SpecialRoomPlacement room,
			long seed,
			int gridStep
	) {
	}

	private record VoidHallProfile(
			BackroomsLayout.SpecialRoomPlacement room,
			int borderWidth,
			int holeRadius
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

	private record BedPlacement(
			int footDepth,
			int footSide,
			Direction facing
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

	private record HouseHallProfile(
			BackroomsLayout.SpecialRoomPlacement room,
			long seed,
			int houseCenterX,
			int houseCenterZ,
			int houseSize,
			int wallHeight,
			Direction doorFacing,
			WoodTheme woodTheme,
			boolean fakeDoor,
			boolean waitingStalker,
			int outsidePileCount,
			boolean hasTrashPiles,
			boolean hasTree,
			boolean hasWell,
			int treeCorner,
			int wellCorner,
			int chestMode,
			int chestDepth,
			int chestSide,
			Direction chestFacing,
			int secondChestDepth,
			int secondChestSide,
			Direction secondChestFacing,
			int bedCount,
			int bedDepth,
			int bedSide,
			int secondBedDepth,
			int secondBedSide,
			Direction bedFacing,
			Direction secondBedFacing,
			int bookshelfVariant,
			int bookshelfCount,
			int furnaceDepth,
			int furnaceSide,
			int tableDepth,
			int tableSide,
			int carpetCenterDepth,
			int carpetCenterSide,
			int carpetRadiusDepth,
			int carpetRadiusSide,
			Block carpetBlock
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

	private enum Corner {
		NORTH_WEST(-1, -1),
		NORTH_EAST(1, -1),
		SOUTH_EAST(1, 1),
		SOUTH_WEST(-1, 1);

		private static final Corner[] VALUES = values();

		final int signX;
		final int signZ;

		Corner(int signX, int signZ) {
			this.signX = signX;
			this.signZ = signZ;
		}
	}

	private enum WoodTheme {
		OAK(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_FENCE),
		SPRUCE(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE);

		private final Block planks;
		private final Block log;
		private final Block stairs;
		private final Block slab;
		private final Block fence;

		WoodTheme(Block planks, Block log, Block stairs, Block slab, Block fence) {
			this.planks = planks;
			this.log = log;
			this.stairs = stairs;
			this.slab = slab;
			this.fence = fence;
		}

		Block planks() {
			return this.planks;
		}

		Block log() {
			return this.log;
		}

		Block stairs() {
			return this.stairs;
		}

		Block slab() {
			return this.slab;
		}

		Block fence() {
			return this.fence;
		}
	}
}
