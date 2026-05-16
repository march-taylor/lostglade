package com.lostglade.server;

final class MonitorGalleryRuntimePolicy {
	private MonitorGalleryRuntimePolicy() {
	}

	static boolean hasSavedGalleryItems(ScreenViewMode mode, int totalItems, int liveCameraItems) {
		if (mode == ScreenViewMode.SBER_DRONES) {
			return false;
		}
		return Math.max(0, totalItems) - Math.max(0, liveCameraItems) > 0;
	}

	static boolean shouldRetainDecodedMedia(
			GalleryItemKind kind,
			String itemUrl,
			int itemIndex,
			int selectedIndex,
			String activeSourceUrl,
			String wallpaperUrl,
			String playerBackgroundUrl
	) {
		if (kind != GalleryItemKind.MEDIA || itemUrl == null || itemUrl.isBlank()) {
			return false;
		}
		if (itemIndex == selectedIndex) {
			return true;
		}
		return itemUrl.equals(activeSourceUrl)
				|| itemUrl.equals(wallpaperUrl)
				|| itemUrl.equals(playerBackgroundUrl);
	}
}
