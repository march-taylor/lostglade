package com.lostglade.server;

public record SpeakerAudioSource(
		String sourceKey,
		String relaySessionId,
		String audioStreamUrl,
		long positionMs,
		long audioSyncToken,
		boolean loading,
		boolean paused,
		boolean liveStream,
		boolean positionAuthoritative
) {
}
