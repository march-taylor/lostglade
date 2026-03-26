package com.lostglade.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.UUID;

public record PhotoPrintData(int mapsWide, int mapsHigh, int[] mapIds) {
	private static final String PHOTO_ROOT_TAG = "lg2_photo_print";
	private static final String FRAME_ROOT_TAG = "lg2_photo_frame";
	private static final String MAPS_WIDE_TAG = "maps_wide";
	private static final String MAPS_HIGH_TAG = "maps_high";
	private static final String MAP_IDS_TAG = "map_ids";
	private static final String GROUP_ID_TAG = "group_id";
	private static final String ANCHOR_X_TAG = "anchor_x";
	private static final String ANCHOR_Y_TAG = "anchor_y";
	private static final String ANCHOR_Z_TAG = "anchor_z";
	private static final String DIRECTION_TAG = "direction";
	private static final String TILE_X_TAG = "tile_x";
	private static final String TILE_Y_TAG = "tile_y";

	public PhotoPrintData {
		mapsWide = Math.max(1, mapsWide);
		mapsHigh = Math.max(1, mapsHigh);
		mapIds = mapIds == null ? new int[0] : Arrays.copyOf(mapIds, mapIds.length);
	}

	public static ItemStack createPhotoItem(Component name, int mapsWide, int mapsHigh, MapId[] mapIds) {
		ItemStack stack = new ItemStack(ModItems.PHOTO_PRINT);
		if (name != null) {
			stack.set(DataComponents.CUSTOM_NAME, name.copy());
		}
		int[] rawIds = new int[mapIds == null ? 0 : mapIds.length];
		for (int i = 0; i < rawIds.length; i++) {
			rawIds[i] = mapIds[i] == null ? -1 : mapIds[i].id();
		}
		writePhotoItem(stack, new PhotoPrintData(mapsWide, mapsHigh, rawIds));
		return stack;
	}

	public static void writePhotoItem(ItemStack stack, PhotoPrintData data) {
		if (stack == null || stack.isEmpty() || !stack.is(ModItems.PHOTO_PRINT) || data == null) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag photoTag = tag.getCompoundOrEmpty(PHOTO_ROOT_TAG);
			photoTag.putInt(MAPS_WIDE_TAG, data.mapsWide());
			photoTag.putInt(MAPS_HIGH_TAG, data.mapsHigh());
			photoTag.putIntArray(MAP_IDS_TAG, data.mapIds());
			tag.put(PHOTO_ROOT_TAG, photoTag);
		});
	}

	public static PhotoPrintData readPhotoItem(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(ModItems.PHOTO_PRINT)) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		CompoundTag rootTag = customData.copyTag();
		if (!rootTag.contains(PHOTO_ROOT_TAG)) {
			return null;
		}
		CompoundTag photoTag = rootTag.getCompoundOrEmpty(PHOTO_ROOT_TAG);
		PhotoPrintData data = new PhotoPrintData(
				photoTag.getIntOr(MAPS_WIDE_TAG, 1),
				photoTag.getIntOr(MAPS_HIGH_TAG, 1),
				photoTag.getIntArray(MAP_IDS_TAG).orElseGet(() -> new int[0])
		);
		return data.isValid() ? data : null;
	}

	public boolean isValid() {
		if (this.mapsWide <= 0 || this.mapsHigh <= 0) {
			return false;
		}
		if (this.mapIds.length != this.mapsWide * this.mapsHigh) {
			return false;
		}
		for (int mapId : this.mapIds) {
			if (mapId < 0) {
				return false;
			}
		}
		return true;
	}

	public int totalTiles() {
		return this.mapsWide * this.mapsHigh;
	}

	public int firstMapId() {
		return this.mapIds.length <= 0 ? -1 : this.mapIds[0];
	}

	public boolean samePhoto(PhotoPrintData other) {
		return other != null
				&& this.mapsWide == other.mapsWide
				&& this.mapsHigh == other.mapsHigh
				&& Arrays.equals(this.mapIds, other.mapIds);
	}

	public int mapIdAt(int tileX, int tileY) {
		if (tileX < 0 || tileY < 0 || tileX >= this.mapsWide || tileY >= this.mapsHigh) {
			return -1;
		}
		return this.mapIds[tileY * this.mapsWide + tileX];
	}

	public PlacedPhotoFrameData placed(UUID groupId, BlockPos anchorPos, Direction direction, int tileX, int tileY) {
		return new PlacedPhotoFrameData(groupId, anchorPos, direction, this.mapsWide, this.mapsHigh, tileX, tileY, this.mapIds);
	}

	public static void writeFrameTile(ItemStack stack, PlacedPhotoFrameData data) {
		if (stack == null || stack.isEmpty() || data == null) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag frameTag = tag.getCompoundOrEmpty(FRAME_ROOT_TAG);
			frameTag.putString(GROUP_ID_TAG, data.groupId().toString());
			frameTag.putInt(ANCHOR_X_TAG, data.anchorPos().getX());
			frameTag.putInt(ANCHOR_Y_TAG, data.anchorPos().getY());
			frameTag.putInt(ANCHOR_Z_TAG, data.anchorPos().getZ());
			frameTag.putString(DIRECTION_TAG, data.direction().getName());
			frameTag.putInt(MAPS_WIDE_TAG, data.mapsWide());
			frameTag.putInt(MAPS_HIGH_TAG, data.mapsHigh());
			frameTag.putInt(TILE_X_TAG, data.tileX());
			frameTag.putInt(TILE_Y_TAG, data.tileY());
			frameTag.putIntArray(MAP_IDS_TAG, data.mapIds());
			tag.put(FRAME_ROOT_TAG, frameTag);
		});
	}

	public static PlacedPhotoFrameData readFrameTile(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		CompoundTag rootTag = customData.copyTag();
		if (!rootTag.contains(FRAME_ROOT_TAG)) {
			return null;
		}
		CompoundTag frameTag = rootTag.getCompoundOrEmpty(FRAME_ROOT_TAG);
		String groupIdRaw = frameTag.getString(GROUP_ID_TAG).orElse("");
		if (groupIdRaw.isEmpty()) {
			return null;
		}
		Direction direction = Direction.byName(frameTag.getString(DIRECTION_TAG).orElse(""));
		if (direction == null) {
			return null;
		}
		UUID groupId;
		try {
			groupId = UUID.fromString(groupIdRaw);
		} catch (IllegalArgumentException exception) {
			return null;
		}
		PlacedPhotoFrameData data = new PlacedPhotoFrameData(
				groupId,
				new BlockPos(
						frameTag.getIntOr(ANCHOR_X_TAG, 0),
						frameTag.getIntOr(ANCHOR_Y_TAG, 0),
						frameTag.getIntOr(ANCHOR_Z_TAG, 0)
				),
				direction,
				frameTag.getIntOr(MAPS_WIDE_TAG, 1),
				frameTag.getIntOr(MAPS_HIGH_TAG, 1),
				frameTag.getIntOr(TILE_X_TAG, 0),
				frameTag.getIntOr(TILE_Y_TAG, 0),
				frameTag.getIntArray(MAP_IDS_TAG).orElseGet(() -> new int[0])
		);
		return data.isValid() ? data : null;
	}

	public record PlacedPhotoFrameData(
			UUID groupId,
			BlockPos anchorPos,
			Direction direction,
			int mapsWide,
			int mapsHigh,
			int tileX,
			int tileY,
			int[] mapIds
	) {
		public PlacedPhotoFrameData {
			mapIds = mapIds == null ? new int[0] : Arrays.copyOf(mapIds, mapIds.length);
		}

		public boolean isValid() {
			if (this.groupId == null || this.anchorPos == null || this.direction == null) {
				return false;
			}
			if (this.mapsWide <= 0 || this.mapsHigh <= 0 || this.mapIds.length != this.mapsWide * this.mapsHigh) {
				return false;
			}
			if (this.tileX < 0 || this.tileY < 0 || this.tileX >= this.mapsWide || this.tileY >= this.mapsHigh) {
				return false;
			}
			for (int mapId : this.mapIds) {
				if (mapId < 0) {
					return false;
				}
			}
			return true;
		}

		public PhotoPrintData asPhotoPrintData() {
			return new PhotoPrintData(this.mapsWide, this.mapsHigh, this.mapIds);
		}

		public boolean samePhoto(PhotoPrintData photoData) {
			return this.asPhotoPrintData().samePhoto(photoData);
		}
	}
}
