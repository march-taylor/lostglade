package com.lostglade.server;

import java.util.Objects;

final class MonitorMediaSessionPolicy {
	private MonitorMediaSessionPolicy() {
	}

	static boolean shouldAppendYoutubeRequest(
			boolean youtubeFamilyMode,
			boolean hasDisplayableMedia,
			boolean hasQueue,
			boolean loading,
			boolean hasSourceUrl,
			boolean hasRelaySession,
			boolean hasStreamFrame,
			long durationMs
	) {
		return youtubeFamilyMode
				&& (hasDisplayableMedia
				|| hasQueue
				|| loading
				|| hasSourceUrl
				|| hasRelaySession
				|| hasStreamFrame
				|| durationMs > 0L);
	}

	static int nextGalleryOpenRequestId(int currentRequestId) {
		return currentRequestId == Integer.MAX_VALUE ? 1 : currentRequestId + 1;
	}

	static boolean galleryOpenRequestShouldApply(
			boolean galleryUiActive,
			String pendingUrl,
			int pendingIndex,
			int pendingRequestId,
			String resultUrl
	) {
		return galleryUiActive
				&& pendingIndex >= 0
				&& pendingRequestId > 0
				&& Objects.equals(pendingUrl, resultUrl);
	}

	static boolean youtubeLoadResultStillCurrent(
			boolean targetGallery,
			boolean stateGalleryMode,
			boolean stateGalleryPlayerSurface,
			boolean stateTargetMode,
			String stateSourceUrl,
			String resultUrl
	) {
		if (resultUrl == null || resultUrl.isBlank() || !Objects.equals(stateSourceUrl, resultUrl)) {
			return false;
		}
		if (targetGallery) {
			return stateGalleryMode && stateGalleryPlayerSurface;
		}
		return stateTargetMode;
	}

	static boolean youtubeMusicLoadResultStillCurrent(
			boolean stateYoutubeMusicMode,
			String stateSourceUrl,
			String resultUrl,
			int stateQueueIndex,
			int resultQueueIndex
	) {
		return stateYoutubeMusicMode
				&& Objects.equals(stateSourceUrl, resultUrl)
				&& (resultQueueIndex < 0 || stateQueueIndex == resultQueueIndex);
	}
}
