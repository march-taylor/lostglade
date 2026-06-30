package com.lostglade.server;

import java.util.Objects;

final class SpeakerAudioPlaybackPolicy {
	private SpeakerAudioPlaybackPolicy() {
	}

	static boolean isPositionAuthoritative(PlaybackStreamKind streamKind) {
		return streamKind == PlaybackStreamKind.LIVE_CAMERA;
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

	static boolean shouldRestartAfterProcessExit(
			boolean processEndedCleanly,
			boolean networkInput,
			boolean liveStream,
			boolean loop,
			String previousRelaySessionId,
			String nextRelaySessionId,
			String previousAudioStreamUrl,
			String nextAudioStreamUrl,
			long previousAudioSyncToken,
			long nextAudioSyncToken
	) {
		if (!processEndedCleanly) {
			return true;
		}
		if (networkInput || liveStream || loop) {
			return true;
		}
		return !Objects.equals(previousRelaySessionId, nextRelaySessionId)
				|| !Objects.equals(previousAudioStreamUrl, nextAudioStreamUrl)
				|| previousAudioSyncToken != nextAudioSyncToken;
	}
}
