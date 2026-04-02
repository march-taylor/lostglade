package com.lostglade.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public record CameraPhotoSettings(int mapsWide, int mapsHigh, CameraPhotoSettings.CaptureMode captureMode) {
	private static final String ROOT_TAG = "lg2_camera";
	private static final String MAPS_WIDE_TAG = "photo_maps_wide";
	private static final String MAPS_HIGH_TAG = "photo_maps_high";
	private static final String CAPTURE_MODE_TAG = "capture_mode";

	public static final int DEFAULT_MAPS_WIDE = 1;
	public static final int DEFAULT_MAPS_HIGH = 1;
	public static final int MAX_MAPS_WIDE = 6;
	public static final int MAX_MAPS_HIGH = 4;

	public enum CaptureMode {
		PHOTO("photo"),
		VIDEO("video");

		private final String serializedName;

		CaptureMode(String serializedName) {
			this.serializedName = serializedName;
		}

		public String serializedName() {
			return this.serializedName;
		}

		public static CaptureMode fromSerializedName(String raw) {
			if (raw == null) {
				return PHOTO;
			}
			for (CaptureMode mode : values()) {
				if (mode.serializedName.equalsIgnoreCase(raw)) {
					return mode;
				}
			}
			return PHOTO;
		}
	}

	public CameraPhotoSettings(int mapsWide, int mapsHigh) {
		this(mapsWide, mapsHigh, CaptureMode.PHOTO);
	}

	public CameraPhotoSettings(int mapsWide, int mapsHigh, CaptureMode captureMode) {
		this.mapsWide = clampWide(mapsWide);
		this.mapsHigh = clampHigh(mapsHigh);
		this.captureMode = captureMode == null ? CaptureMode.PHOTO : captureMode;
	}

	public static CameraPhotoSettings read(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(ModItems.CAMERA)) {
			return defaults();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return defaults();
		}
		CompoundTag rootTag = customData.copyTag();
		if (!rootTag.contains(ROOT_TAG)) {
			return defaults();
		}
		CompoundTag cameraTag = rootTag.getCompoundOrEmpty(ROOT_TAG);
		return new CameraPhotoSettings(
				cameraTag.getIntOr(MAPS_WIDE_TAG, DEFAULT_MAPS_WIDE),
				cameraTag.getIntOr(MAPS_HIGH_TAG, DEFAULT_MAPS_HIGH),
				CaptureMode.fromSerializedName(cameraTag.getStringOr(CAPTURE_MODE_TAG, CaptureMode.PHOTO.serializedName()))
		);
	}

	public static void write(ItemStack stack, CameraPhotoSettings settings) {
		if (stack == null || stack.isEmpty() || !stack.is(ModItems.CAMERA) || settings == null) {
			return;
		}
		CameraPhotoSettings normalized = new CameraPhotoSettings(settings.mapsWide(), settings.mapsHigh(), settings.captureMode());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag cameraTag = tag.getCompoundOrEmpty(ROOT_TAG);
			cameraTag.putInt(MAPS_WIDE_TAG, normalized.mapsWide());
			cameraTag.putInt(MAPS_HIGH_TAG, normalized.mapsHigh());
			cameraTag.putString(CAPTURE_MODE_TAG, normalized.captureMode().serializedName());
			tag.put(ROOT_TAG, cameraTag);
		});
	}

	public static CameraPhotoSettings defaults() {
		return new CameraPhotoSettings(DEFAULT_MAPS_WIDE, DEFAULT_MAPS_HIGH, CaptureMode.PHOTO);
	}

	public int totalMaps() {
		return this.mapsWide * this.mapsHigh;
	}

	public boolean isVideoMode() {
		return this.captureMode == CaptureMode.VIDEO;
	}

	private static int clampWide(int value) {
		return Math.max(1, Math.min(MAX_MAPS_WIDE, value));
	}

	private static int clampHigh(int value) {
		return Math.max(1, Math.min(MAX_MAPS_HIGH, value));
	}
}
