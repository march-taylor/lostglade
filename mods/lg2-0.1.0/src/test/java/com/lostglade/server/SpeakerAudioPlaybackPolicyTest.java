package com.lostglade.server;

public final class SpeakerAudioPlaybackPolicyTest {
	private SpeakerAudioPlaybackPolicyTest() {
	}

	public static void main(String[] args) {
		require(
				!SpeakerAudioPlaybackPolicy.isPositionAuthoritative(PlaybackStreamKind.DIRECT_VIDEO),
				"direct video/audio playback must not continuously restart from stale monitor positions"
		);
		require(
				!SpeakerAudioPlaybackPolicy.shouldResyncPosition(false, false, false, 1800L, 100L, 500L),
				"local direct audio must not restart just because the monitor snapshot position is stale"
		);
		require(
				!SpeakerAudioPlaybackPolicy.shouldRestartAfterProcessExit(
						true,
						false,
						false,
						false,
						"relay-a",
						"relay-a",
						"/tmp/audio.ogg",
						"/tmp/audio.ogg",
						41L,
						41L
				),
				"a clean EOF on the same local direct track must not restart speaker playback and loop the tail"
		);
		require(
				SpeakerAudioPlaybackPolicy.shouldRestartAfterProcessExit(
						true,
						false,
						false,
						false,
						"relay-a",
						"relay-a",
						"/tmp/audio.ogg",
						"/tmp/audio.ogg",
						41L,
						42L
				),
				"a new audio sync token after EOF must restart playback so replay and seek still work"
		);
		require(
				SpeakerAudioPlaybackPolicy.shouldRestartAfterProcessExit(
						true,
						true,
						false,
						false,
						"relay-a",
						"relay-a",
						"https://audio.test/stream",
						"https://audio.test/stream",
						41L,
						41L
				),
				"network-backed inputs should still restart after disconnect-like process exits"
		);
		System.out.println("Speaker audio playback policy checks passed");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
