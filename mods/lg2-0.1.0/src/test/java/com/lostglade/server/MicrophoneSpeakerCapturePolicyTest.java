package com.lostglade.server;

public final class MicrophoneSpeakerCapturePolicyTest {
	private MicrophoneSpeakerCapturePolicyTest() {
	}

	public static void main(String[] args) {
		float maximumForwardingGain = 2.4F * 0.28F;
		require(
				MicrophoneSpeakerCapturePolicy.captureGain(true, 0.0D, 48.0D, 48.0D, maximumForwardingGain) == 0.0F,
				"a microphone must ignore speakers connected to itself or its computer"
		);
		require(
				MicrophoneSpeakerCapturePolicy.captureGain(false, 48.0D, 48.0D, 48.0D, maximumForwardingGain) == 0.0F,
				"a microphone must not capture speakers beyond their audible range"
		);
		float nearbySpeakerGain = MicrophoneSpeakerCapturePolicy.captureGain(false, 0.0D, 48.0D, 48.0D, maximumForwardingGain);
		require(nearbySpeakerGain > 0.0F, "an external nearby speaker must be audible to a microphone");
		require(
				nearbySpeakerGain * maximumForwardingGain < 0.8F,
				"speaker-to-microphone forwarding must decay instead of resonating"
		);
		System.out.println("Microphone speaker capture policy checks passed");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
