package com.lostglade.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

final class BackroomsLayout {
	static final int FLOOR_Y = 64;
	static final int CEILING_Y = 69;
	static final int WALL_MIN_Y = FLOOR_Y + 1;
	static final int WALL_MAX_Y = CEILING_Y - 1;

	private static final int STYLE_REGION_SIZE = 96;
	private static final int LAYOUT_CELL_SIZE = 24;
	private static final int INSET_REGION_SIZE = 56;
	private static final int DOOR_CHANCE_PERCENT = 2;
	private static final long DOOR_LAYOUT_SALT = 0x6B41524F4F4D4452L;
	private static final long INSET_LAYOUT_SALT = 0x494E534554524F4FL;

	private BackroomsLayout() {
	}

	static ZoneType getZoneAtBlock(int x, int z) {
		int regionX = Math.floorDiv(x, STYLE_REGION_SIZE);
		int regionZ = Math.floorDiv(z, STYLE_REGION_SIZE);
		int roll = positiveMod(mix(regionX, regionZ, 0x74D8E1AB4C9F2601L), 100);
		if (roll < 12) {
			return ZoneType.WIDE_LIT;
		}
		if (roll < 25) {
			return ZoneType.WIDE_DARK;
		}
		if (roll < 50) {
			return ZoneType.MIDDLE_LIT;
		}
		if (roll < 75) {
			return ZoneType.MIDDLE_DARK;
		}
		if (roll < 87) {
			return ZoneType.NARROW_LIT;
		}
		return ZoneType.NARROW_DARK;
	}

	static boolean isCorridor(ZoneType zone, int x, int z) {
		return isOpenSpace(zone, x, z);
	}

	static boolean hasCeilingLight(ZoneType zone, int x, int z) {
		if (!isOpenSpace(zone, x, z)) {
			return false;
		}

		int cellX = Math.floorDiv(x, zone.lightCellSize);
		int cellZ = Math.floorDiv(z, zone.lightCellSize);
		long sample = mix(cellX, cellZ, zone.lightSalt);
		if (zone.lightChanceRarity > 1 && positiveMod(sample, zone.lightChanceRarity) != 0) {
			return false;
		}

		int candidateX = cellX * zone.lightCellSize + positiveMod(sample ^ 0x49C6C9C1B52F7A51L, zone.lightCellSize);
		int candidateZ = cellZ * zone.lightCellSize + positiveMod((sample >>> 17) ^ 0x1B03738712FAD5C9L, zone.lightCellSize);
		if (x != candidateX || z != candidateZ) {
			return false;
		}

		return isLightAnchor(zone, candidateX, candidateZ);
	}

	static DoorPlacement getRoomDoorAt(ZoneType zone, int x, int z) {
		DoorPlacement primaryPlacement = getPrimaryDoorAt(zone, x, z);
		if (primaryPlacement != null) {
			return primaryPlacement;
		}

		return getInsetDoorAt(zone, x, z);
	}

	private static boolean isLightAnchor(ZoneType zone, int x, int z) {
		return isOpenSpace(zone, x, z)
				&& isOpenSpace(zone, x + 1, z)
				&& isOpenSpace(zone, x - 1, z)
				&& isOpenSpace(zone, x, z + 1)
				&& isOpenSpace(zone, x, z - 1);
	}

	private static boolean isOpenSpace(ZoneType zone, int x, int z) {
		return isPrimaryOpenSpace(zone, x, z) || isInsetOpenSpace(zone, x, z);
	}

	private static boolean isPrimaryOpenSpace(ZoneType zone, int x, int z) {
		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				int currentCellX = cellX + offsetX;
				int currentCellZ = cellZ + offsetZ;
				CellData current = getCell(zone, currentCellX, currentCellZ);
				if (current.containsRoom(x, z)) {
					return true;
				}

				CellData east = getCell(zone, currentCellX + 1, currentCellZ);
				if (current.connectEast && isInsideConnection(x, z, current, east, zone.corridorHalfWidth, current.eastHorizontalFirst)) {
					return true;
				}

				CellData south = getCell(zone, currentCellX, currentCellZ + 1);
				if (current.connectSouth && isInsideConnection(x, z, current, south, zone.corridorHalfWidth, current.southHorizontalFirst)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isInsetOpenSpace(ZoneType zone, int x, int z) {
		int regionX = Math.floorDiv(x, INSET_REGION_SIZE);
		int regionZ = Math.floorDiv(z, INSET_REGION_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				InsetFeature feature = getInsetFeature(zone, regionX + offsetX, regionZ + offsetZ);
				if (feature == null) {
					continue;
				}

				if (feature.containsRoom(x, z) || feature.containsCorridor(x, z)) {
					return true;
				}
			}
		}

		return false;
	}

	private static DoorPlacement getPrimaryDoorAt(ZoneType zone, int x, int z) {
		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				DoorPlacement placement = getDoorPlacementForCell(zone, cellX + offsetX, cellZ + offsetZ);
				if (placement != null && placement.matches(x, z)) {
					return placement;
				}
			}
		}

		return null;
	}

	private static DoorPlacement getInsetDoorAt(ZoneType zone, int x, int z) {
		int regionX = Math.floorDiv(x, INSET_REGION_SIZE);
		int regionZ = Math.floorDiv(z, INSET_REGION_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				InsetFeature feature = getInsetFeature(zone, regionX + offsetX, regionZ + offsetZ);
				if (feature != null && feature.door().matches(x, z)) {
					return feature.door();
				}
			}
		}

		return null;
	}

	private static boolean isInsideConnection(
			int x,
			int z,
			CellData from,
			CellData to,
			int halfWidth,
			boolean horizontalFirst
	) {
		if (horizontalFirst) {
			return isOnHorizontalSegment(x, z, from.roomCenterX, to.roomCenterX, from.roomCenterZ, halfWidth)
					|| isOnVerticalSegment(x, z, from.roomCenterZ, to.roomCenterZ, to.roomCenterX, halfWidth);
		}

		return isOnVerticalSegment(x, z, from.roomCenterZ, to.roomCenterZ, from.roomCenterX, halfWidth)
				|| isOnHorizontalSegment(x, z, from.roomCenterX, to.roomCenterX, to.roomCenterZ, halfWidth);
	}

	private static boolean isOnHorizontalSegment(int x, int z, int x1, int x2, int centerZ, int halfWidth) {
		int minX = Math.min(x1, x2);
		int maxX = Math.max(x1, x2);
		return x >= minX && x <= maxX && Math.abs(z - centerZ) <= halfWidth;
	}

	private static boolean isOnVerticalSegment(int x, int z, int z1, int z2, int centerX, int halfWidth) {
		int minZ = Math.min(z1, z2);
		int maxZ = Math.max(z1, z2);
		return z >= minZ && z <= maxZ && Math.abs(x - centerX) <= halfWidth;
	}

	private static CellData getCell(ZoneType zone, int cellX, int cellZ) {
		long sample = mix(cellX, cellZ, zone.layoutSalt);
		int shiftX = positiveMod(sample ^ 0x0F91A4BL, 9) - 4;
		int shiftZ = positiveMod(sample ^ 0x17B4D29L, 9) - 4;
		int baseX = cellX * LAYOUT_CELL_SIZE + shiftX;
		int baseZ = cellZ * LAYOUT_CELL_SIZE + shiftZ;

		int centerMarginX = zone.roomHalfWidthMax + 1;
		int centerMarginZ = zone.roomHalfHeightMax + 1;
		int centerRangeX = Math.max(1, LAYOUT_CELL_SIZE - centerMarginX * 2);
		int centerRangeZ = Math.max(1, LAYOUT_CELL_SIZE - centerMarginZ * 2);

		int roomCenterX = baseX + centerMarginX + positiveMod(sample ^ 0x31A8E73FB2C45117L, centerRangeX);
		int roomCenterZ = baseZ + centerMarginZ + positiveMod((sample >>> 11) ^ 0x61C3F7DA4582E905L, centerRangeZ);
		int roomHalfWidth = zone.roomHalfWidthMin + positiveMod(sample ^ 0x12FD4E71A26BC983L, zone.roomHalfWidthMax - zone.roomHalfWidthMin + 1);
		int roomHalfHeight = zone.roomHalfHeightMin + positiveMod((sample >>> 23) ^ 0x5A7C812ED3944C6FL, zone.roomHalfHeightMax - zone.roomHalfHeightMin + 1);
		if (roomHalfWidth < zone.roomHalfWidthMax && positiveMod(sample ^ 0x19C3E771L, 100) < 28) {
			roomHalfWidth++;
		}
		if (roomHalfHeight < zone.roomHalfHeightMax && positiveMod(sample ^ 0x52AF114DL, 100) < 28) {
			roomHalfHeight++;
		}

		boolean connectEast = positiveMod(sample ^ 0x5F27A1C4D8B39821L, 100) < zone.connectionChancePercent;
		boolean connectSouth = positiveMod((sample >>> 29) ^ 0x22DCA19077BE54A1L, 100) < zone.connectionChancePercent;
		if (!connectEast && !connectSouth) {
			if (((sample >>> 7) & 1L) == 0L) {
				connectEast = true;
			} else {
				connectSouth = true;
			}
		}
		boolean eastHorizontalFirst = ((sample >>> 7) & 1L) == 0L;
		boolean southHorizontalFirst = ((sample >>> 13) & 1L) == 0L;

		return new CellData(
				roomCenterX,
				roomCenterZ,
				roomHalfWidth,
				roomHalfHeight,
				connectEast,
				connectSouth,
				eastHorizontalFirst,
				southHorizontalFirst
		);
	}

	private static InsetFeature getInsetFeature(ZoneType zone, int regionX, int regionZ) {
		long sample = mix(regionX, regionZ, zone.layoutSalt ^ INSET_LAYOUT_SALT);
		if (positiveMod(sample ^ 0x2D51A19L, 100) >= 62) {
			return null;
		}

		int baseX = regionX * INSET_REGION_SIZE;
		int baseZ = regionZ * INSET_REGION_SIZE;
		int centerX = baseX + 10 + positiveMod(sample ^ 0x31D7A4FL, INSET_REGION_SIZE - 20);
		int centerZ = baseZ + 10 + positiveMod(sample ^ 0x44C82B1L, INSET_REGION_SIZE - 20);
		int halfWidth = 3 + positiveMod(sample ^ 0x18FA11L, 4);
		int halfHeight = 3 + positiveMod(sample ^ 0x52DD77L, 4);

		PrimaryAnchor anchor = findNearestPrimaryAnchor(zone, centerX, centerZ);
		boolean horizontalFirst = ((sample >>> 7) & 1L) == 0L;
		RoomSide doorSide = chooseInsetDoorSide(sample, anchor, centerX, centerZ);
		int doorX = centerX + doorSide.doorOffsetX(halfWidth, halfHeight);
		int doorZ = centerZ + doorSide.doorOffsetZ(halfWidth, halfHeight);

		return new InsetFeature(
				centerX,
				centerZ,
				halfWidth,
				halfHeight,
				anchor.x(),
				anchor.z(),
				horizontalFirst,
				new DoorPlacement(doorX, doorZ, doorSide.facing, ((sample >>> 15) & 1L) == 0L ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
		);
	}

	private static PrimaryAnchor findNearestPrimaryAnchor(ZoneType zone, int x, int z) {
		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);
		CellData bestCell = null;
		int bestDistance = Integer.MAX_VALUE;

		for (int offsetX = -2; offsetX <= 2; offsetX++) {
			for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
				CellData candidate = getCell(zone, cellX + offsetX, cellZ + offsetZ);
				int deltaX = candidate.roomCenterX - x;
				int deltaZ = candidate.roomCenterZ - z;
				int distance = deltaX * deltaX + deltaZ * deltaZ;
				if (distance < bestDistance) {
					bestDistance = distance;
					bestCell = candidate;
				}
			}
		}

		return new PrimaryAnchor(bestCell.roomCenterX, bestCell.roomCenterZ);
	}

	private static RoomSide chooseInsetDoorSide(long sample, PrimaryAnchor anchor, int centerX, int centerZ) {
		int deltaX = anchor.x() - centerX;
		int deltaZ = anchor.z() - centerZ;
		if (Math.abs(deltaX) > Math.abs(deltaZ)) {
			if (deltaX > 0) {
				return ((sample >>> 3) & 1L) == 0L ? RoomSide.NORTH : RoomSide.SOUTH;
			}
			return ((sample >>> 3) & 1L) == 0L ? RoomSide.SOUTH : RoomSide.NORTH;
		}

		if (deltaZ > 0) {
			return ((sample >>> 3) & 1L) == 0L ? RoomSide.WEST : RoomSide.EAST;
		}
		return ((sample >>> 3) & 1L) == 0L ? RoomSide.EAST : RoomSide.WEST;
	}

	private static DoorPlacement getDoorPlacementForCell(ZoneType zone, int cellX, int cellZ) {
		long sample = mix(cellX, cellZ, zone.layoutSalt ^ DOOR_LAYOUT_SALT);
		if (positiveMod(sample ^ 0x4A6D9A1FB77C53E1L, 100) >= DOOR_CHANCE_PERCENT) {
			return null;
		}

		CellData current = getCell(zone, cellX, cellZ);
		RoomSide[] eligibleSides = collectEligibleDoorSides(zone, cellX, cellZ, current);
		if (eligibleSides.length == 0) {
			return null;
		}

		RoomSide side = eligibleSides[positiveMod(sample ^ 0x23E5FA97C14B72D3L, eligibleSides.length)];
		int doorX = current.roomCenterX + side.doorOffsetX(current);
		int doorZ = current.roomCenterZ + side.doorOffsetZ(current);
		if (!isOpenSpace(zone, doorX - side.stepX, doorZ - side.stepZ)) {
			return null;
		}
		if (isOpenSpace(zone, doorX + side.stepX, doorZ + side.stepZ)) {
			return null;
		}

		DoorHingeSide hinge = ((sample >>> 9) & 1L) == 0L ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
		return new DoorPlacement(doorX, doorZ, side.facing, hinge);
	}

	private static RoomSide[] collectEligibleDoorSides(ZoneType zone, int cellX, int cellZ, CellData current) {
		RoomSide[] sides = new RoomSide[4];
		int count = 0;

		if (!getCell(zone, cellX, cellZ - 1).connectSouth) {
			sides[count++] = RoomSide.NORTH;
		}
		if (!current.connectSouth) {
			sides[count++] = RoomSide.SOUTH;
		}
		if (!getCell(zone, cellX - 1, cellZ).connectEast) {
			sides[count++] = RoomSide.WEST;
		}
		if (!current.connectEast) {
			sides[count++] = RoomSide.EAST;
		}

		RoomSide[] result = new RoomSide[count];
		System.arraycopy(sides, 0, result, 0, count);
		return result;
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

	enum ZoneType {
		WIDE_LIT("Wide Lit Rooms", 3, 5, 8, 5, 8, 94, 8, 1, 0x2B6897A1F52E83D1L, 0x51A9C7E3D44F8201L),
		WIDE_DARK("Wide Dark Rooms", 3, 5, 8, 5, 8, 92, 10, 4, 0x6A91C0EF33B27459L, 0x1495AE3772C541B3L),
		MIDDLE_LIT("Middle Lit Rooms", 2, 4, 6, 4, 6, 92, 7, 1, 0x4F1727A6D4E90C21L, 0x2B963F11A75EC8D7L),
		MIDDLE_DARK("Middle Dark Rooms", 2, 4, 6, 4, 6, 90, 9, 4, 0x75BCF1D28AE63F49L, 0x66D701A48E539C25L),
		NARROW_LIT("Narrow Lit Rooms", 1, 3, 5, 3, 5, 88, 6, 1, 0x3C842A95DE716F11L, 0x73F2C4A9E5B12D61L),
		NARROW_DARK("Narrow Dark Rooms", 1, 3, 5, 3, 5, 86, 8, 5, 0x1ED93F6478AB25C3L, 0x0ED6B91F4C3A8275L);

		static final ZoneType[] VALUES = values();

		final String debugName;
		final int corridorHalfWidth;
		final int roomHalfWidthMin;
		final int roomHalfWidthMax;
		final int roomHalfHeightMin;
		final int roomHalfHeightMax;
		final int connectionChancePercent;
		final int lightCellSize;
		final int lightChanceRarity;
		final long lightSalt;
		final long layoutSalt;

		ZoneType(
				String debugName,
				int corridorHalfWidth,
				int roomHalfWidthMin,
				int roomHalfWidthMax,
				int roomHalfHeightMin,
				int roomHalfHeightMax,
				int connectionChancePercent,
				int lightCellSize,
				int lightChanceRarity,
				long lightSalt,
				long layoutSalt
		) {
			this.debugName = debugName;
			this.corridorHalfWidth = corridorHalfWidth;
			this.roomHalfWidthMin = roomHalfWidthMin;
			this.roomHalfWidthMax = roomHalfWidthMax;
			this.roomHalfHeightMin = roomHalfHeightMin;
			this.roomHalfHeightMax = roomHalfHeightMax;
			this.connectionChancePercent = connectionChancePercent;
			this.lightCellSize = lightCellSize;
			this.lightChanceRarity = lightChanceRarity;
			this.lightSalt = lightSalt;
			this.layoutSalt = layoutSalt;
		}
	}

	private record CellData(
			int roomCenterX,
			int roomCenterZ,
			int roomHalfWidth,
			int roomHalfHeight,
			boolean connectEast,
			boolean connectSouth,
			boolean eastHorizontalFirst,
			boolean southHorizontalFirst
	) {
		private boolean containsRoom(int x, int z) {
			return Math.abs(x - this.roomCenterX) <= this.roomHalfWidth && Math.abs(z - this.roomCenterZ) <= this.roomHalfHeight;
		}
	}

	private record InsetFeature(
			int roomCenterX,
			int roomCenterZ,
			int roomHalfWidth,
			int roomHalfHeight,
			int targetX,
			int targetZ,
			boolean horizontalFirst,
			DoorPlacement door
	) {
		private boolean containsRoom(int x, int z) {
			return Math.abs(x - this.roomCenterX) <= this.roomHalfWidth && Math.abs(z - this.roomCenterZ) <= this.roomHalfHeight;
		}

		private boolean containsCorridor(int x, int z) {
			return isInsideConnection(
					x,
					z,
					this.roomCenterX,
					this.targetX,
					this.roomCenterZ,
					this.targetZ,
					1,
					this.horizontalFirst
			);
		}
	}

	private record PrimaryAnchor(int x, int z) {
	}

	record DoorPlacement(int x, int z, Direction facing, DoorHingeSide hinge) {
		private boolean matches(int x, int z) {
			return this.x == x && this.z == z;
		}
	}

	private enum RoomSide {
		NORTH(0, -1, Direction.NORTH) {
			@Override
			int doorOffsetX(CellData cell) {
				return 0;
			}

			@Override
			int doorOffsetZ(CellData cell) {
				return -(cell.roomHalfHeight + 1);
			}

			@Override
			int doorOffsetX(int roomHalfWidth, int roomHalfHeight) {
				return 0;
			}

			@Override
			int doorOffsetZ(int roomHalfWidth, int roomHalfHeight) {
				return -(roomHalfHeight + 1);
			}
		},
		SOUTH(0, 1, Direction.SOUTH) {
			@Override
			int doorOffsetX(CellData cell) {
				return 0;
			}

			@Override
			int doorOffsetZ(CellData cell) {
				return cell.roomHalfHeight + 1;
			}

			@Override
			int doorOffsetX(int roomHalfWidth, int roomHalfHeight) {
				return 0;
			}

			@Override
			int doorOffsetZ(int roomHalfWidth, int roomHalfHeight) {
				return roomHalfHeight + 1;
			}
		},
		WEST(-1, 0, Direction.WEST) {
			@Override
			int doorOffsetX(CellData cell) {
				return -(cell.roomHalfWidth + 1);
			}

			@Override
			int doorOffsetZ(CellData cell) {
				return 0;
			}

			@Override
			int doorOffsetX(int roomHalfWidth, int roomHalfHeight) {
				return -(roomHalfWidth + 1);
			}

			@Override
			int doorOffsetZ(int roomHalfWidth, int roomHalfHeight) {
				return 0;
			}
		},
		EAST(1, 0, Direction.EAST) {
			@Override
			int doorOffsetX(CellData cell) {
				return cell.roomHalfWidth + 1;
			}

			@Override
			int doorOffsetZ(CellData cell) {
				return 0;
			}

			@Override
			int doorOffsetX(int roomHalfWidth, int roomHalfHeight) {
				return roomHalfWidth + 1;
			}

			@Override
			int doorOffsetZ(int roomHalfWidth, int roomHalfHeight) {
				return 0;
			}
		};

		final int stepX;
		final int stepZ;
		final Direction facing;

		RoomSide(int stepX, int stepZ, Direction facing) {
			this.stepX = stepX;
			this.stepZ = stepZ;
			this.facing = facing;
		}

		abstract int doorOffsetX(CellData cell);

		abstract int doorOffsetZ(CellData cell);

		abstract int doorOffsetX(int roomHalfWidth, int roomHalfHeight);

		abstract int doorOffsetZ(int roomHalfWidth, int roomHalfHeight);
	}

	private static boolean isInsideConnection(
			int x,
			int z,
			int fromX,
			int toX,
			int fromZ,
			int toZ,
			int halfWidth,
			boolean horizontalFirst
	) {
		if (horizontalFirst) {
			return isOnHorizontalSegment(x, z, fromX, toX, fromZ, halfWidth)
					|| isOnVerticalSegment(x, z, fromZ, toZ, toX, halfWidth);
		}

		return isOnVerticalSegment(x, z, fromZ, toZ, fromX, halfWidth)
				|| isOnHorizontalSegment(x, z, fromX, toX, toZ, halfWidth);
	}
}
