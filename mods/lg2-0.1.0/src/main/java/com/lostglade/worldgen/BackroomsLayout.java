package com.lostglade.worldgen;

final class BackroomsLayout {
	static final int FLOOR_Y = 64;
	static final int CEILING_Y = 69;
	static final int WALL_MIN_Y = FLOOR_Y + 1;
	static final int WALL_MAX_Y = CEILING_Y - 1;

	private static final int STYLE_REGION_SIZE = 96;
	private static final int LAYOUT_CELL_SIZE = 24;

	private BackroomsLayout() {
	}

	static ZoneType getZoneAtBlock(int x, int z) {
		int regionX = Math.floorDiv(x, STYLE_REGION_SIZE);
		int regionZ = Math.floorDiv(z, STYLE_REGION_SIZE);
		return ZoneType.VALUES[positiveMod(mix(regionX, regionZ, 0x74D8E1AB4C9F2601L), ZoneType.VALUES.length)];
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

	private static boolean isLightAnchor(ZoneType zone, int x, int z) {
		return isOpenSpace(zone, x, z)
				&& isOpenSpace(zone, x + 1, z)
				&& isOpenSpace(zone, x - 1, z)
				&& isOpenSpace(zone, x, z + 1)
				&& isOpenSpace(zone, x, z - 1);
	}

	private static boolean isOpenSpace(ZoneType zone, int x, int z) {
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
		int baseX = cellX * LAYOUT_CELL_SIZE;
		int baseZ = cellZ * LAYOUT_CELL_SIZE;

		int centerMarginX = zone.roomHalfWidthMax + 2;
		int centerMarginZ = zone.roomHalfHeightMax + 2;
		int centerRangeX = Math.max(1, LAYOUT_CELL_SIZE - centerMarginX * 2);
		int centerRangeZ = Math.max(1, LAYOUT_CELL_SIZE - centerMarginZ * 2);

		int roomCenterX = baseX + centerMarginX + positiveMod(sample ^ 0x31A8E73FB2C45117L, centerRangeX);
		int roomCenterZ = baseZ + centerMarginZ + positiveMod((sample >>> 11) ^ 0x61C3F7DA4582E905L, centerRangeZ);
		int roomHalfWidth = zone.roomHalfWidthMin + positiveMod(sample ^ 0x12FD4E71A26BC983L, zone.roomHalfWidthMax - zone.roomHalfWidthMin + 1);
		int roomHalfHeight = zone.roomHalfHeightMin + positiveMod((sample >>> 23) ^ 0x5A7C812ED3944C6FL, zone.roomHalfHeightMax - zone.roomHalfHeightMin + 1);

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
}
