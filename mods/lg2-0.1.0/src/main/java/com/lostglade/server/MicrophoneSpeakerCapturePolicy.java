package com.lostglade.server;

final class MicrophoneSpeakerCapturePolicy {
	private static final float DEFAULT_EXTERNAL_SPEAKER_CAPTURE_GAIN = 0.5F;
	private static final float MAX_FEEDBACK_LOOP_GAIN = 0.8F;

	private MicrophoneSpeakerCapturePolicy() {
	}

	static float captureGain(
			boolean connectedToMicrophoneOrComputer,
			double sourceDistance,
			double microphoneCaptureDistance,
			double speakerAudibleDistance,
			float maximumForwardingGain
	) {
		if (connectedToMicrophoneOrComputer
				|| !Double.isFinite(sourceDistance)
				|| !Double.isFinite(microphoneCaptureDistance)
				|| !Double.isFinite(speakerAudibleDistance)
				|| sourceDistance < 0.0D) {
			return 0.0F;
		}
		double captureDistance = Math.min(microphoneCaptureDistance, speakerAudibleDistance);
		if (captureDistance <= 0.0D || sourceDistance >= captureDistance) {
			return 0.0F;
		}
		float feedbackSafeGain = maximumForwardingGain > 0.0F && Float.isFinite(maximumForwardingGain)
				? MAX_FEEDBACK_LOOP_GAIN / maximumForwardingGain
				: DEFAULT_EXTERNAL_SPEAKER_CAPTURE_GAIN;
		float baseGain = Math.min(DEFAULT_EXTERNAL_SPEAKER_CAPTURE_GAIN, feedbackSafeGain);
		return (float) Math.clamp((1.0D - sourceDistance / captureDistance) * baseGain, 0.0D, baseGain);
	}
}
