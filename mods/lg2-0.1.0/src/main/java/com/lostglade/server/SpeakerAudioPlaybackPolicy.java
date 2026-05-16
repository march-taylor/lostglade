package com.lostglade.server;

final class SpeakerAudioPlaybackPolicy {
	private SpeakerAudioPlaybackPolicy() {
	}

	static boolean shouldResyncPosition(
			boolean liveStream,
			boolean loading,
			boolean positionAuthoritative,
			long expectedPositionMs,
			long sourcePositionMs,
			long toleranceMs
	) {
		if (liveStream || loading || !positionAuthoritative) {
			return false;
		}
		return Math.abs(expectedPositionMs - sourcePositionMs) > Math.max(0L, toleranceMs);
	}
}
