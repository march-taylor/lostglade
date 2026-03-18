package com.lostglade.worldgen;

import com.lostglade.config.Lg2Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class BackroomsLayout {
	static final int FLOOR_Y = 64;
	static final int LEVEL_HEIGHT = 5;
	static final int CEILING_Y = FLOOR_Y + LEVEL_HEIGHT - 1;
	static final int WALL_MIN_Y = FLOOR_Y + 1;
	static final int WALL_MAX_Y = CEILING_Y - 1;
	static final int WORLD_MIN_Y = 0;
	static final int WORLD_HEIGHT = 384;
	static final int WORLD_MAX_Y = WORLD_MIN_Y + WORLD_HEIGHT - 1;
	static final int MAX_FULL_LEVEL_INDEX = computeMaxFullLevelIndex();

	private static final int STYLE_REGION_SIZE = 96;
	private static final int LAYOUT_CELL_SIZE = 24;
	private static final int INSET_REGION_SIZE = 56;
	private static final int DOOR_CHANCE_PERCENT = 2;
	private static final int SPECIAL_ROOM_CHANCE_BASIS_POINTS = 25;
	private static final int INSET_DOOR_CHANCE_PERCENT = 10;
	private static final long DOOR_LAYOUT_SALT = 0x6B41524F4F4D4452L;
	private static final long SPECIAL_ROOM_LAYOUT_SALT = 0x5350454349414C31L;
	private static final long INSET_LAYOUT_SALT = 0x494E534554524F4FL;
	private static final long LEVEL_LAYOUT_SALT = 0x4C564C53414C5431L;
	private static final long CELL_CACHE_SALT = 0x43454C4C43414348L;
	private static final long SPECIAL_ROOM_CACHE_SALT = 0x5350435243414348L;
	private static final long INSET_CACHE_SALT = 0x494E534554434143L;
	private static final long OPEN_CACHE_SALT = 0x4F50454E43414348L;
	private static final long DOOR_CACHE_SALT = 0x444F4F5243414348L;
	private static final long ZONE_CACHE_SALT = 0x5A4F4E4543414348L;
	private static final long VOID_HALL_COLUMN_CACHE_SALT = 0x564F494448434143L;
	private static final ThreadLocal<LayoutCache> LAYOUT_CACHE = ThreadLocal.withInitial(LayoutCache::new);

	private BackroomsLayout() {
	}

	static int getLevelIndex(int y) {
		return Math.floorDiv(y - FLOOR_Y, LEVEL_HEIGHT);
	}

	static boolean canFitFullLevels(int baseLevelIndex, int levelSpan) {
		return levelSpan > 0
				&& baseLevelIndex + levelSpan - 1 <= MAX_FULL_LEVEL_INDEX;
	}

	static int getMaxAvailableFullLevelSpan(int baseLevelIndex) {
		return Math.max(0, (MAX_FULL_LEVEL_INDEX - baseLevelIndex) + 1);
	}

	static boolean isTopmostFullLevel(int levelIndex) {
		return levelIndex >= MAX_FULL_LEVEL_INDEX;
	}

	static ZoneType getZoneAtBlock(int x, int z, int levelIndex) {
		int regionX = Math.floorDiv(x, STYLE_REGION_SIZE);
		int regionZ = Math.floorDiv(z, STYLE_REGION_SIZE);
		LayoutCache cache = layoutCache();
		long cacheKey = regionCacheKey(regionX, regionZ, levelIndex, ZONE_CACHE_SALT);
		ZoneType cached = cache.zoneCache.get(cacheKey);
		if (cached != null) {
			return cached;
		}

		int roll = positiveMod(mix(regionX, regionZ, 0x74D8E1AB4C9F2601L ^ levelSalt(levelIndex)), 100);
		ZoneType zone;
		if (roll < 12) {
			zone = ZoneType.WIDE_LIT;
		} else if (roll < 25) {
			zone = ZoneType.WIDE_DARK;
		} else if (roll < 50) {
			zone = ZoneType.MIDDLE_LIT;
		} else if (roll < 75) {
			zone = ZoneType.MIDDLE_DARK;
		} else if (roll < 87) {
			zone = ZoneType.NARROW_LIT;
		} else {
			zone = ZoneType.NARROW_DARK;
		}

		cache.zoneCache.put(cacheKey, zone);
		return zone;
	}

	private static ZoneType getZoneAtCell(int cellX, int cellZ, int levelIndex) {
		int sampleX = cellX * LAYOUT_CELL_SIZE + (LAYOUT_CELL_SIZE / 2);
		int sampleZ = cellZ * LAYOUT_CELL_SIZE + (LAYOUT_CELL_SIZE / 2);
		return getZoneAtBlock(sampleX, sampleZ, levelIndex);
	}

	static boolean isCorridor(ZoneType zone, int x, int z, int levelIndex) {
		return isOpenSpace(zone, x, z, levelIndex);
	}

	static boolean hasCeilingLight(ZoneType zone, int x, int z, int levelIndex) {
		int cellX = Math.floorDiv(x, zone.lightCellSize);
		int cellZ = Math.floorDiv(z, zone.lightCellSize);
		long sample = mix(cellX, cellZ, zone.lightSalt ^ levelSalt(levelIndex));
		if (zone.lightChanceRarity > 1 && positiveMod(sample, zone.lightChanceRarity) != 0) {
			return false;
		}

		int candidateX = cellX * zone.lightCellSize + positiveMod(sample ^ 0x49C6C9C1B52F7A51L, zone.lightCellSize);
		int candidateZ = cellZ * zone.lightCellSize + positiveMod((sample >>> 17) ^ 0x1B03738712FAD5C9L, zone.lightCellSize);
		if (x != candidateX || z != candidateZ) {
			return false;
		}

		return isLightAnchor(zone, candidateX, candidateZ, levelIndex);
	}

	static DoorPlacement getRoomDoorAt(ZoneType zone, int x, int z, int levelIndex) {
		DoorPlacement primaryPlacement = getPrimaryDoorAt(zone, x, z, levelIndex);
		if (primaryPlacement != null) {
			return primaryPlacement;
		}

		return getInsetDoorAt(zone, x, z, levelIndex);
	}

	static SpecialRoomPlacement getSpecialRoomAt(ZoneType zone, int x, int z, int levelIndex) {
		SpecialRoomPlacement best = findSpecialRoomAtLevel(zone, x, z, levelIndex, levelIndex, true);
		for (int anchorLevel = levelIndex - 1; anchorLevel >= levelIndex - 3; anchorLevel--) {
			SpecialRoomPlacement candidate = findSpecialRoomAtLevel(
					getZoneAtBlock(x, z, anchorLevel),
					x,
					z,
					anchorLevel,
					levelIndex,
					false
			);
			best = choosePreferredPlacement(best, candidate, x, z, levelIndex);
		}
		return best;
	}

	private static SpecialRoomPlacement findSpecialRoomAtLevel(
			ZoneType ignoredZone,
			int x,
			int z,
			int anchorLevelIndex,
			int targetLevelIndex,
			boolean includeSingleLevelRooms
	) {
		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);
		SpecialRoomPlacement best = null;

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				int candidateCellX = cellX + offsetX;
				int candidateCellZ = cellZ + offsetZ;
				ZoneType candidateZone = getZoneAtCell(candidateCellX, candidateCellZ, anchorLevelIndex);
				SpecialRoomPlacement placement = getSpecialRoomForCell(candidateZone, candidateCellX, candidateCellZ, anchorLevelIndex);
				if (placement == null
						|| (!includeSingleLevelRooms && placement.levelSpan() <= 1)
						|| !placement.contains(x, z, targetLevelIndex)) {
					continue;
				}
				best = choosePreferredPlacement(best, placement, x, z, targetLevelIndex);
			}
		}

		return best;
	}

	private static SpecialRoomPlacement choosePreferredPlacement(
			SpecialRoomPlacement current,
			SpecialRoomPlacement candidate,
			int x,
			int z,
			int levelIndex
	) {
		if (candidate == null) {
			return current;
		}
		if (current == null) {
			return candidate;
		}

		int currentPriority = current.type().isLargeHall() ? 2 : 1;
		int candidatePriority = candidate.type().isLargeHall() ? 2 : 1;
		if (candidatePriority != currentPriority) {
			return candidatePriority > currentPriority ? candidate : current;
		}

		int currentLevelDistance = Math.abs(levelIndex - current.baseLevelIndex());
		int candidateLevelDistance = Math.abs(levelIndex - candidate.baseLevelIndex());
		if (candidateLevelDistance != currentLevelDistance) {
			return candidateLevelDistance < currentLevelDistance ? candidate : current;
		}

		int currentDistance = Math.abs(x - current.roomCenterX()) + Math.abs(z - current.roomCenterZ());
		int candidateDistance = Math.abs(x - candidate.roomCenterX()) + Math.abs(z - candidate.roomCenterZ());
		return candidateDistance < currentDistance ? candidate : current;
	}

	private static boolean isLightAnchor(ZoneType zone, int x, int z, int levelIndex) {
		return isOpenSpace(zone, x, z, levelIndex)
				&& isOpenSpace(zone, x + 1, z, levelIndex)
				&& isOpenSpace(zone, x - 1, z, levelIndex)
				&& isOpenSpace(zone, x, z + 1, levelIndex)
				&& isOpenSpace(zone, x, z - 1, levelIndex);
	}

	private static boolean isOpenSpace(ZoneType zone, int x, int z, int levelIndex) {
		LayoutCache cache = layoutCache();
		long key = cacheKey(x, z, levelIndex, zone, OPEN_CACHE_SALT);
		Boolean cached = cache.openSpaceCache.get(key);
		if (cached != null) {
			return cached;
		}

		boolean result = isPrimaryOpenSpace(zone, x, z, levelIndex) || isInsetOpenSpace(zone, x, z, levelIndex);
		cache.openSpaceCache.put(key, result);
		return result;
	}

	private static boolean isPrimaryOpenSpace(ZoneType zone, int x, int z, int levelIndex) {
		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				int currentCellX = cellX + offsetX;
				int currentCellZ = cellZ + offsetZ;
				CellData current = getCell(zone, currentCellX, currentCellZ, levelIndex);
				if (current.containsRoom(x, z)) {
					return true;
				}

				CellData east = getCell(zone, currentCellX + 1, currentCellZ, levelIndex);
				if (current.connectEast && isInsideConnection(x, z, current, east, zone.corridorHalfWidth, current.eastHorizontalFirst)) {
					return true;
				}

				CellData south = getCell(zone, currentCellX, currentCellZ + 1, levelIndex);
				if (current.connectSouth && isInsideConnection(x, z, current, south, zone.corridorHalfWidth, current.southHorizontalFirst)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean isInsetOpenSpace(ZoneType zone, int x, int z, int levelIndex) {
		int regionX = Math.floorDiv(x, INSET_REGION_SIZE);
		int regionZ = Math.floorDiv(z, INSET_REGION_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				InsetFeature feature = getInsetFeature(zone, regionX + offsetX, regionZ + offsetZ, levelIndex);
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

	private static DoorPlacement getPrimaryDoorAt(ZoneType zone, int x, int z, int levelIndex) {
		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				DoorPlacement placement = getDoorPlacementForCell(zone, cellX + offsetX, cellZ + offsetZ, levelIndex);
				if (placement != null && placement.matches(x, z)) {
					return placement;
				}
			}
		}

		return null;
	}

	private static DoorPlacement getInsetDoorAt(ZoneType zone, int x, int z, int levelIndex) {
		int regionX = Math.floorDiv(x, INSET_REGION_SIZE);
		int regionZ = Math.floorDiv(z, INSET_REGION_SIZE);

		for (int offsetX = -1; offsetX <= 1; offsetX++) {
			for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
				InsetFeature feature = getInsetFeature(zone, regionX + offsetX, regionZ + offsetZ, levelIndex);
				if (feature != null && feature.door() != null && feature.door().matches(x, z)) {
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

	private static CellData getCell(ZoneType zone, int cellX, int cellZ, int levelIndex) {
		LayoutCache cache = layoutCache();
		long key = cacheKey(cellX, cellZ, levelIndex, zone, CELL_CACHE_SALT);
		CellData cached = cache.cellCache.get(key);
		if (cached != null) {
			return cached;
		}

		long sample = mix(cellX, cellZ, zone.layoutSalt ^ levelSalt(levelIndex));
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

		CellData cellData = new CellData(
				roomCenterX,
				roomCenterZ,
				roomHalfWidth,
				roomHalfHeight,
				connectEast,
				connectSouth,
				eastHorizontalFirst,
				southHorizontalFirst
		);
		cache.cellCache.put(key, cellData);
		return cellData;
	}

	private static InsetFeature getInsetFeature(ZoneType zone, int regionX, int regionZ, int levelIndex) {
		LayoutCache cache = layoutCache();
		long key = cacheKey(regionX, regionZ, levelIndex, zone, INSET_CACHE_SALT);
		Optional<InsetFeature> cached = cache.insetFeatureCache.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}

		long sample = mix(regionX, regionZ, zone.layoutSalt ^ INSET_LAYOUT_SALT ^ levelSalt(levelIndex));
		if (positiveMod(sample ^ 0x2D51A19L, 100) >= 62) {
			cache.insetFeatureCache.put(key, Optional.empty());
			return null;
		}

		int baseX = regionX * INSET_REGION_SIZE;
		int baseZ = regionZ * INSET_REGION_SIZE;
		int centerX = baseX + 10 + positiveMod(sample ^ 0x31D7A4FL, INSET_REGION_SIZE - 20);
		int centerZ = baseZ + 10 + positiveMod(sample ^ 0x44C82B1L, INSET_REGION_SIZE - 20);
		int halfWidth = 3 + positiveMod(sample ^ 0x18FA11L, 4);
		int halfHeight = 3 + positiveMod(sample ^ 0x52DD77L, 4);

		PrimaryAnchor anchor = findNearestPrimaryAnchor(zone, centerX, centerZ, levelIndex);
		boolean horizontalFirst = ((sample >>> 7) & 1L) == 0L;
		RoomSide corridorSide = getInsetCorridorSide(anchor, centerX, centerZ);
		DoorPlacement doorPlacement = null;
		if (positiveMod(sample ^ 0x4F55B19L, 100) < INSET_DOOR_CHANCE_PERCENT) {
			RoomSide doorSide = chooseInsetDoorSide(sample, corridorSide);
			if (doorSide != null) {
				int doorX = centerX + doorSide.doorOffsetX(halfWidth, halfHeight);
				int doorZ = centerZ + doorSide.doorOffsetZ(halfWidth, halfHeight);
				if (isValidInsetDoor(zone, centerX, centerZ, halfWidth, halfHeight, doorSide, doorX, doorZ, levelIndex)) {
					doorPlacement = new DoorPlacement(
							doorX,
							doorZ,
							doorSide.facing,
							((sample >>> 15) & 1L) == 0L ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT
					);
				}
			}
		}

		InsetFeature feature = new InsetFeature(
				centerX,
				centerZ,
				halfWidth,
				halfHeight,
				anchor.x(),
				anchor.z(),
				horizontalFirst,
				doorPlacement
		);
		cache.insetFeatureCache.put(key, Optional.of(feature));
		return feature;
	}

	private static PrimaryAnchor findNearestPrimaryAnchor(ZoneType zone, int x, int z, int levelIndex) {
		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);
		CellData bestCell = null;
		int bestDistance = Integer.MAX_VALUE;

		for (int offsetX = -2; offsetX <= 2; offsetX++) {
			for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
				CellData candidate = getCell(zone, cellX + offsetX, cellZ + offsetZ, levelIndex);
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

	private static RoomSide getInsetCorridorSide(PrimaryAnchor anchor, int centerX, int centerZ) {
		int deltaX = anchor.x() - centerX;
		int deltaZ = anchor.z() - centerZ;
		if (Math.abs(deltaX) > Math.abs(deltaZ)) {
			return deltaX > 0 ? RoomSide.EAST : RoomSide.WEST;
		}

		return deltaZ > 0 ? RoomSide.SOUTH : RoomSide.NORTH;
	}

	private static RoomSide chooseInsetDoorSide(long sample, RoomSide excludedSide) {
		RoomSide[] candidates = new RoomSide[3];
		int index = 0;
		for (RoomSide side : RoomSide.values()) {
			if (side != excludedSide) {
				candidates[index++] = side;
			}
		}

		return candidates[positiveMod(sample ^ 0x33E71DAL, index)];
	}

	private static boolean isValidInsetDoor(
			ZoneType zone,
			int centerX,
			int centerZ,
			int halfWidth,
			int halfHeight,
			RoomSide side,
			int doorX,
			int doorZ,
			int levelIndex
	) {
		int insideX = doorX - side.stepX;
		int insideZ = doorZ - side.stepZ;
		int outsideX = doorX + side.stepX;
		int outsideZ = doorZ + side.stepZ;

		boolean insideRoom = Math.abs(insideX - centerX) <= halfWidth && Math.abs(insideZ - centerZ) <= halfHeight;
		if (!insideRoom) {
			return false;
		}

		if (Math.abs(outsideX - centerX) <= halfWidth && Math.abs(outsideZ - centerZ) <= halfHeight) {
			return false;
		}

		if (isPrimaryOpenSpace(zone, doorX, doorZ, levelIndex)) {
			return false;
		}

		return !isPrimaryOpenSpace(zone, outsideX, outsideZ, levelIndex);
	}

	private static DoorPlacement getDoorPlacementForCell(ZoneType zone, int cellX, int cellZ, int levelIndex) {
		LayoutCache cache = layoutCache();
		long key = cacheKey(cellX, cellZ, levelIndex, zone, DOOR_CACHE_SALT);
		Optional<DoorPlacement> cached = cache.doorPlacementCache.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}

		long sample = mix(cellX, cellZ, zone.layoutSalt ^ DOOR_LAYOUT_SALT ^ levelSalt(levelIndex));
		if (positiveMod(sample ^ 0x4A6D9A1FB77C53E1L, 100) >= DOOR_CHANCE_PERCENT) {
			cache.doorPlacementCache.put(key, Optional.empty());
			return null;
		}

		CellData current = getCell(zone, cellX, cellZ, levelIndex);
		RoomSide[] eligibleSides = collectEligibleDoorSides(zone, cellX, cellZ, current, levelIndex);
		if (eligibleSides.length == 0) {
			cache.doorPlacementCache.put(key, Optional.empty());
			return null;
		}

		RoomSide side = eligibleSides[positiveMod(sample ^ 0x23E5FA97C14B72D3L, eligibleSides.length)];
		int doorX = current.roomCenterX + side.doorOffsetX(current);
		int doorZ = current.roomCenterZ + side.doorOffsetZ(current);
		if (isOpenSpace(zone, doorX, doorZ, levelIndex)) {
			cache.doorPlacementCache.put(key, Optional.empty());
			return null;
		}
		if (!isOpenSpace(zone, doorX - side.stepX, doorZ - side.stepZ, levelIndex)) {
			cache.doorPlacementCache.put(key, Optional.empty());
			return null;
		}
		if (isOpenSpace(zone, doorX + side.stepX, doorZ + side.stepZ, levelIndex)) {
			cache.doorPlacementCache.put(key, Optional.empty());
			return null;
		}

		DoorHingeSide hinge = ((sample >>> 9) & 1L) == 0L ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT;
		DoorPlacement placement = new DoorPlacement(doorX, doorZ, side.facing, hinge);
		cache.doorPlacementCache.put(key, Optional.of(placement));
		return placement;
	}

	private static SpecialRoomPlacement getSpecialRoomForCell(ZoneType zone, int cellX, int cellZ, int levelIndex) {
		LayoutCache cache = layoutCache();
		long key = cacheKey(cellX, cellZ, levelIndex, zone, SPECIAL_ROOM_CACHE_SALT);
		Optional<SpecialRoomPlacement> cached = cache.specialRoomCache.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}

		long sample = mix(cellX, cellZ, zone.layoutSalt ^ SPECIAL_ROOM_LAYOUT_SALT ^ levelSalt(levelIndex));
		if (positiveMod(sample ^ 0x2A5C7D11E3A19F43L, 10000) >= SPECIAL_ROOM_CHANCE_BASIS_POINTS) {
			cache.specialRoomCache.put(key, Optional.empty());
			return null;
		}

		CellData current = getCell(zone, cellX, cellZ, levelIndex);
		if (isCoveredByNeighboringLargeHall(current.roomCenterX, current.roomCenterZ, cellX, cellZ, levelIndex)) {
			cache.specialRoomCache.put(key, Optional.empty());
			return null;
		}

		SpecialRoomType[] eligibleTypes = SpecialRoomType.collectEligible(current, levelIndex);
		if (eligibleTypes.length == 0) {
			cache.specialRoomCache.put(key, Optional.empty());
			return null;
		}

		SpecialRoomType type = pickSpecialRoomType(sample ^ 0x61E2D34FA719BC25L, eligibleTypes);
		SpecialRoomPlacement placement = buildSpecialRoomPlacement(type, current, cellX, cellZ, levelIndex, sample);
		if (placement == null) {
			cache.specialRoomCache.put(key, Optional.empty());
			return null;
		}
		cache.specialRoomCache.put(key, Optional.of(placement));
		return placement;
	}

	private static boolean isCoveredByNeighboringLargeHall(int x, int z, int cellX, int cellZ, int levelIndex) {
		int minLevelIndex = getLevelIndex(WORLD_MIN_Y);
		for (int anchorLevel = MAX_FULL_LEVEL_INDEX; anchorLevel >= minLevelIndex; anchorLevel--) {
			for (int offsetX = -2; offsetX <= 2; offsetX++) {
				for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
					int candidateCellX = cellX + offsetX;
					int candidateCellZ = cellZ + offsetZ;
					if (candidateCellX == cellX && candidateCellZ == cellZ && anchorLevel == levelIndex) {
						continue;
					}

					ZoneType candidateZone = getZoneAtCell(candidateCellX, candidateCellZ, anchorLevel);
					SpecialRoomPlacement hall = getLargeHallPlacementForCell(candidateZone, candidateCellX, candidateCellZ, anchorLevel);
					if (hall != null && largeHallCoversLevel(hall, x, z, levelIndex)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static SpecialRoomPlacement getLargeHallPlacementForCell(ZoneType zone, int cellX, int cellZ, int levelIndex) {
		SpecialRoomPlacement placement = getLargeHallPlacementForCellUnchecked(zone, cellX, cellZ, levelIndex);
		if (placement == null || hasLargeHallConflict(placement)) {
			return null;
		}
		return placement;
	}

	private static SpecialRoomPlacement getLargeHallPlacementForCellUnchecked(ZoneType zone, int cellX, int cellZ, int levelIndex) {
		long sample = mix(cellX, cellZ, zone.layoutSalt ^ SPECIAL_ROOM_LAYOUT_SALT ^ levelSalt(levelIndex));
		if (positiveMod(sample ^ 0x2A5C7D11E3A19F43L, 10000) >= SPECIAL_ROOM_CHANCE_BASIS_POINTS) {
			return null;
		}

		CellData current = getCell(zone, cellX, cellZ, levelIndex);
		SpecialRoomType[] eligibleTypes = SpecialRoomType.collectEligible(current, levelIndex);
		if (eligibleTypes.length == 0) {
			return null;
		}

		SpecialRoomType type = pickSpecialRoomType(sample ^ 0x61E2D34FA719BC25L, eligibleTypes);
		if (!type.isLargeHall()) {
			return null;
		}

		return buildLargeHallPlacementUnchecked(type, current, cellX, cellZ, levelIndex, sample);
	}

	private static SpecialRoomType pickSpecialRoomType(long sample, SpecialRoomType[] eligibleTypes) {
		int totalWeight = 0;
		for (SpecialRoomType type : eligibleTypes) {
			totalWeight += type.selectionWeight();
		}
		if (totalWeight <= 0) {
			return eligibleTypes[positiveMod(sample, eligibleTypes.length)];
		}

		int roll = positiveMod(sample, totalWeight);
		for (SpecialRoomType type : eligibleTypes) {
			roll -= type.selectionWeight();
			if (roll < 0) {
				return type;
			}
		}
		return eligibleTypes[0];
	}

	private static SpecialRoomPlacement buildSpecialRoomPlacement(
			SpecialRoomType type,
			CellData current,
			int cellX,
			int cellZ,
			int levelIndex,
			long sample
	) {
		if (type.isLargeHall()) {
			SpecialRoomPlacement placement = buildLargeHallPlacementUnchecked(type, current, cellX, cellZ, levelIndex, sample);
			if (placement == null || hasLargeHallConflict(placement)) {
				return null;
			}
			return placement;
		}

		return new SpecialRoomPlacement(
				type,
				cellX,
				cellZ,
				levelIndex,
				current.roomCenterX,
				current.roomCenterZ,
				current.roomHalfWidth,
				current.roomHalfHeight,
				1
		);
	}

	private static SpecialRoomPlacement buildLargeHallPlacementUnchecked(
			SpecialRoomType type,
			CellData current,
			int cellX,
			int cellZ,
			int levelIndex,
			long sample
	) {
		int maxLevelSpan = Math.min(4, getMaxAvailableFullLevelSpan(levelIndex));
		if (maxLevelSpan < 3) {
			return null;
		}
		int hallHalf;
		int levelSpan;
		if (type == SpecialRoomType.HOUSE_HALL) {
			hallHalf = 12 + positiveMod(sample ^ 0x484F55534548414CL, 9);
			levelSpan = 3 + positiveMod(sample ^ 0x484F555345464C52L, maxLevelSpan - 2);
		} else if (type == SpecialRoomType.VOID_HALL) {
			hallHalf = 15 + positiveMod(sample ^ 0x564F494448414C4CL, 10);
			levelSpan = 3 + positiveMod(sample ^ 0x564F4944464C4F52L, maxLevelSpan - 2);
		} else if (type == SpecialRoomType.PLUS_MAZE) {
			int hallHalfWidth = 25 + positiveMod(sample ^ 0x504C55534D415A58L, 5);
			int hallHalfHeight = 25 + positiveMod(sample ^ 0x504C55534D415A5AL, 5);
			return new SpecialRoomPlacement(
					type,
					cellX,
					cellZ,
					levelIndex,
					current.roomCenterX,
					current.roomCenterZ,
					hallHalfWidth,
					hallHalfHeight,
					1
			);
		} else {
			return null;
		}
		return new SpecialRoomPlacement(
				type,
				cellX,
				cellZ,
				levelIndex,
				current.roomCenterX,
				current.roomCenterZ,
				hallHalf,
				hallHalf,
				levelSpan
		);
	}

	private static boolean hasLargeHallConflict(SpecialRoomPlacement candidate) {
		int minX = candidate.roomCenterX() - candidate.roomHalfWidth();
		int maxX = candidate.roomCenterX() + candidate.roomHalfWidth();
		int minZ = candidate.roomCenterZ() - candidate.roomHalfHeight();
		int maxZ = candidate.roomCenterZ() + candidate.roomHalfHeight();
		int minCellX = Math.floorDiv(minX, LAYOUT_CELL_SIZE) - 1;
		int maxCellX = Math.floorDiv(maxX, LAYOUT_CELL_SIZE) + 1;
		int minCellZ = Math.floorDiv(minZ, LAYOUT_CELL_SIZE) - 1;
		int maxCellZ = Math.floorDiv(maxZ, LAYOUT_CELL_SIZE) + 1;
		int minLevel = getLevelIndex(WORLD_MIN_Y);
		int maxLevel = MAX_FULL_LEVEL_INDEX;

		for (int anchorLevel = minLevel; anchorLevel <= maxLevel; anchorLevel++) {
			if (!canFitFullLevels(anchorLevel, 2)) {
				continue;
			}
			for (int candidateCellX = minCellX; candidateCellX <= maxCellX; candidateCellX++) {
				for (int candidateCellZ = minCellZ; candidateCellZ <= maxCellZ; candidateCellZ++) {
					if (candidateCellX == candidate.cellX()
							&& candidateCellZ == candidate.cellZ()
							&& anchorLevel == candidate.baseLevelIndex()) {
						continue;
					}

					ZoneType candidateZone = getZoneAtCell(candidateCellX, candidateCellZ, anchorLevel);
					SpecialRoomPlacement other = getLargeHallPlacementForCellUnchecked(candidateZone, candidateCellX, candidateCellZ, anchorLevel);
					if (other != null && largeHallPlacementsOverlap(candidate, other)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static boolean largeHallPlacementsOverlap(SpecialRoomPlacement first, SpecialRoomPlacement second) {
		if (!largeHallHorizontalOverlap(first, second)) {
			return false;
		}

		return largeHallAffectsLevels(first, second)
				|| largeHallAffectsLevels(second, first);
	}

	private static boolean largeHallHorizontalOverlap(SpecialRoomPlacement first, SpecialRoomPlacement second) {
		return Math.abs(first.roomCenterX() - second.roomCenterX()) <= first.roomHalfWidth() + second.roomHalfWidth()
				&& Math.abs(first.roomCenterZ() - second.roomCenterZ()) <= first.roomHalfHeight() + second.roomHalfHeight();
	}

	private static boolean largeHallAffectsLevels(SpecialRoomPlacement source, SpecialRoomPlacement target) {
		int sourceTopExclusive = source.baseLevelIndex() + source.levelSpan();
		if (source.type() == SpecialRoomType.VOID_HALL) {
			return target.baseLevelIndex() < sourceTopExclusive;
		}

		int targetTopExclusive = target.baseLevelIndex() + target.levelSpan();
		return source.baseLevelIndex() < targetTopExclusive
				&& target.baseLevelIndex() < sourceTopExclusive;
	}

	private static boolean largeHallCoversLevel(SpecialRoomPlacement placement, int x, int z, int levelIndex) {
		if (!placement.contains(x, z)) {
			return false;
		}
		if (placement.contains(x, z, levelIndex)) {
			return true;
		}
		return placement.type() == SpecialRoomType.VOID_HALL
				&& levelIndex < placement.baseLevelIndex() + placement.levelSpan();
	}

	static SpecialRoomPlacement getVoidHallForColumn(int x, int z) {
		LayoutCache cache = layoutCache();
		long key = BlockPos.asLong(x, 0, z) ^ VOID_HALL_COLUMN_CACHE_SALT;
		Optional<SpecialRoomPlacement> cached = cache.voidHallColumnCache.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}

		int cellX = Math.floorDiv(x, LAYOUT_CELL_SIZE);
		int cellZ = Math.floorDiv(z, LAYOUT_CELL_SIZE);
		SpecialRoomPlacement result = null;
		int minLevelIndex = getLevelIndex(WORLD_MIN_Y);
		for (int anchorLevel = MAX_FULL_LEVEL_INDEX; anchorLevel >= minLevelIndex; anchorLevel--) {
			for (int offsetX = -2; offsetX <= 2; offsetX++) {
				for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
					int candidateCellX = cellX + offsetX;
					int candidateCellZ = cellZ + offsetZ;
					ZoneType candidateZone = getZoneAtCell(candidateCellX, candidateCellZ, anchorLevel);
					SpecialRoomPlacement placement = getLargeHallPlacementForCell(candidateZone, candidateCellX, candidateCellZ, anchorLevel);
					if (placement != null
							&& placement.type() == SpecialRoomType.VOID_HALL
							&& placement.contains(x, z)) {
						result = placement;
						cache.voidHallColumnCache.put(key, Optional.of(result));
						return result;
					}
				}
			}
		}

		cache.voidHallColumnCache.put(key, Optional.empty());
		return null;
	}

	private static RoomSide[] collectEligibleDoorSides(ZoneType zone, int cellX, int cellZ, CellData current, int levelIndex) {
		RoomSide[] sides = new RoomSide[4];
		int count = 0;

		if (!getCell(zone, cellX, cellZ - 1, levelIndex).connectSouth) {
			sides[count++] = RoomSide.NORTH;
		}
		if (!current.connectSouth) {
			sides[count++] = RoomSide.SOUTH;
		}
		if (!getCell(zone, cellX - 1, cellZ, levelIndex).connectEast) {
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

	private static int computeMaxFullLevelIndex() {
		int topLevelIndex = getLevelIndex(WORLD_MAX_Y);
		int ceilingY = FLOOR_Y + topLevelIndex * LEVEL_HEIGHT + (LEVEL_HEIGHT - 1);
		if (ceilingY > WORLD_MAX_Y) {
			topLevelIndex--;
		}
		return topLevelIndex;
	}

	private static long levelSalt(int levelIndex) {
		return mix(levelIndex, levelIndex ^ 0x51F2A, LEVEL_LAYOUT_SALT);
	}

	private static LayoutCache layoutCache() {
		LayoutCache cache = LAYOUT_CACHE.get();
		cache.trimIfNeeded();
		return cache;
	}

	private static long cacheKey(int x, int z, int levelIndex, ZoneType zone, long salt) {
		return BlockPos.asLong(x, levelIndex, z) ^ (((long) zone.ordinal()) << 58) ^ salt;
	}

	private static long regionCacheKey(int x, int z, int levelIndex, long salt) {
		return BlockPos.asLong(x, levelIndex, z) ^ salt;
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

	record SpecialRoomPlacement(
			SpecialRoomType type,
			int cellX,
			int cellZ,
			int baseLevelIndex,
			int roomCenterX,
			int roomCenterZ,
			int roomHalfWidth,
			int roomHalfHeight,
			int levelSpan
		) {
		boolean contains(int x, int z) {
			return Math.abs(x - this.roomCenterX) <= this.roomHalfWidth
					&& Math.abs(z - this.roomCenterZ) <= this.roomHalfHeight;
		}

		boolean contains(int x, int z, int levelIndex) {
			return this.contains(x, z)
					&& levelIndex >= this.baseLevelIndex
					&& levelIndex < this.baseLevelIndex + this.levelSpan;
		}
	}

	enum SpecialRoomType {
		TRASH_ROOM("trash_room", 3, 3, true),
		FLOOR_HOLES("floor_holes", 4, 4, true),
		VOID_HALL("void_hall", 0, 0, true),
		HOUSE_HALL("house_hall", 0, 0, true),
		PLUS_MAZE("plus_maze", 0, 0, true),
		STAIRS("stairs", 3, 4, true);

		private static final SpecialRoomType[] VALUES = values();

		final String id;
		final int minHalfWidth;
		final int minHalfHeight;
		final boolean enabled;
		SpecialRoomType(String id, int minHalfWidth, int minHalfHeight, boolean enabled) {
			this.id = id;
			this.minHalfWidth = minHalfWidth;
			this.minHalfHeight = minHalfHeight;
			this.enabled = enabled;
		}

		private boolean canFit(CellData cell, int levelIndex) {
			if ((this == HOUSE_HALL || this == VOID_HALL) && !canFitFullLevels(levelIndex, 3)) {
				return false;
			}
			if (this == PLUS_MAZE && !canFitFullLevels(levelIndex, 2)) {
				return false;
			}
			if (this == HOUSE_HALL || this == VOID_HALL || this == PLUS_MAZE) {
				return true;
			}
			return cell.roomHalfWidth >= this.minHalfWidth && cell.roomHalfHeight >= this.minHalfHeight;
		}

		private boolean isLargeHall() {
			return this == HOUSE_HALL || this == VOID_HALL || this == PLUS_MAZE;
		}

		private int selectionWeight() {
			Lg2Config.ConfigData config = Lg2Config.get();
			return switch (this) {
				case TRASH_ROOM -> config.backroomsTrashRoomWeight;
				case FLOOR_HOLES -> config.backroomsFloorHolesRoomWeight;
				case VOID_HALL -> config.backroomsVoidHallRoomWeight;
				case HOUSE_HALL -> config.backroomsHouseHallRoomWeight;
				case PLUS_MAZE -> config.backroomsPlusMazeRoomWeight;
				case STAIRS -> config.backroomsStairsRoomWeight;
			};
		}

		static SpecialRoomType[] collectEligible(CellData cell, int levelIndex) {
			SpecialRoomType[] result = new SpecialRoomType[VALUES.length];
			int count = 0;
			for (SpecialRoomType type : VALUES) {
				if (type.enabled && type.canFit(cell, levelIndex)) {
					result[count++] = type;
				}
			}

			SpecialRoomType[] trimmed = new SpecialRoomType[count];
			System.arraycopy(result, 0, trimmed, 0, count);
			return trimmed;
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

	private static final class LayoutCache {
		private static final int MAX_CELL_CACHE = 8192;
		private static final int MAX_INSET_CACHE = 2048;
		private static final int MAX_OPEN_SPACE_CACHE = 32768;
		private static final int MAX_DOOR_CACHE = 8192;
		private static final int MAX_SPECIAL_ROOM_CACHE = 4096;
		private static final int MAX_ZONE_CACHE = 4096;
		private static final int MAX_VOID_HALL_COLUMN_CACHE = 4096;

		final Map<Long, ZoneType> zoneCache = new HashMap<>();
		final Map<Long, CellData> cellCache = new HashMap<>();
		final Map<Long, Optional<InsetFeature>> insetFeatureCache = new HashMap<>();
		final Map<Long, Boolean> openSpaceCache = new HashMap<>();
		final Map<Long, Optional<DoorPlacement>> doorPlacementCache = new HashMap<>();
		final Map<Long, Optional<SpecialRoomPlacement>> specialRoomCache = new HashMap<>();
		final Map<Long, Optional<SpecialRoomPlacement>> voidHallColumnCache = new HashMap<>();

		void trimIfNeeded() {
			if (this.zoneCache.size() > MAX_ZONE_CACHE) {
				this.zoneCache.clear();
			}
			if (this.cellCache.size() > MAX_CELL_CACHE) {
				this.cellCache.clear();
			}
			if (this.insetFeatureCache.size() > MAX_INSET_CACHE) {
				this.insetFeatureCache.clear();
			}
			if (this.openSpaceCache.size() > MAX_OPEN_SPACE_CACHE) {
				this.openSpaceCache.clear();
			}
			if (this.doorPlacementCache.size() > MAX_DOOR_CACHE) {
				this.doorPlacementCache.clear();
			}
			if (this.specialRoomCache.size() > MAX_SPECIAL_ROOM_CACHE) {
				this.specialRoomCache.clear();
			}
			if (this.voidHallColumnCache.size() > MAX_VOID_HALL_COLUMN_CACHE) {
				this.voidHallColumnCache.clear();
			}
		}
	}
}
