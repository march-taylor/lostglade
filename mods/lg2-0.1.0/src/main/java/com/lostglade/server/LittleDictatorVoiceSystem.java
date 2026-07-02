package com.lostglade.server;

import com.lostglade.Lg2;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class LittleDictatorVoiceSystem {

    private static final float LITTLE_DICTATOR_VOICE_VOLUME_MULTIPLIER = 1.45F;
    private static final float LITTLE_DICTATOR_VOICE_DISTANCE_MULTIPLIER = 2.0F;

    private static final Map<UUID, PitchRuntime> PITCH_RUNTIMES = new ConcurrentHashMap<>();
    private static final Map<MicrophonePacket, byte[]> TRANSFORMED_PACKET_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private LittleDictatorVoiceSystem() {
    }

    public static void onMicrophonePacket(MicrophonePacketEvent event) {
        if (event == null || !ServerVoicechatIntegration.isLoaded()) {
            return;
        }
        VoicechatApi api = ServerVoicechatIntegration.getApi();
        if (api == null) {
            return;
        }
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null || senderConnection.getPlayer() == null) {
            return;
        }
        Object rawPlayer = senderConnection.getPlayer().getPlayer();
        if (!(rawPlayer instanceof ServerPlayer senderPlayer)) {
            return;
        }
        float intensity = ServerRaceSystem.getLittleDictatorUniqueVoiceIntensity(senderPlayer);
        if (intensity <= 0.001F) {
            return;
        }
        float pitchFactor = ServerRaceSystem.getLittleDictatorUniqueVoicePitchFactor(senderPlayer);
        float gain = Mth.lerp(intensity, 1.0F, LITTLE_DICTATOR_VOICE_VOLUME_MULTIPLIER);
        MicrophonePacket packet = event.getPacket();
        if (packet == null) {
            return;
        }
        byte[] cached = TRANSFORMED_PACKET_CACHE.get(packet);
        if (cached != null && cached.length > 0) {
            packet.setOpusEncodedData(cached);
            return;
        }
        byte[] opusData = packet.getOpusEncodedData();
        if (opusData == null || opusData.length == 0) {
            return;
        }
        UUID senderId = senderConnection.getPlayer().getUuid();
        PitchRuntime runtime = PITCH_RUNTIMES.compute(senderId, (ignored, existing) -> {
            if (existing != null && !existing.isClosed()) {
                return existing;
            }
            return new PitchRuntime(api.createDecoder(), api.createEncoder());
        });
        if (runtime == null || runtime.isClosed()) {
            return;
        }
        byte[] transformed = runtime.transform(opusData, pitchFactor, gain);
        if (transformed == null || transformed.length == 0) {
            return;
        }
        packet.setOpusEncodedData(transformed);
        TRANSFORMED_PACKET_CACHE.put(packet, transformed);
    }


    public static void onVoiceDistance(VoiceDistanceEvent event) {
        if (event == null || !ServerVoicechatIntegration.isLoaded()) {
            return;
        }
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null || senderConnection.getPlayer() == null) {
            return;
        }
        Object rawPlayer = senderConnection.getPlayer().getPlayer();
        if (!(rawPlayer instanceof ServerPlayer senderPlayer)) {
            return;
        }
        float intensity = ServerRaceSystem.getLittleDictatorUniqueVoiceIntensity(senderPlayer);
        if (intensity <= 0.001F) {
            return;
        }
        float distanceMultiplier = Mth.lerp(intensity, 1.0F, LITTLE_DICTATOR_VOICE_DISTANCE_MULTIPLIER);
        event.setDistance(event.getDistance() * distanceMultiplier);
    }
    private static short[] applyPitchShift(short[] samples, float pitchFactor) {
        if (samples == null || samples.length == 0) {
            return samples;
        }
        float clampedFactor = Mth.clamp(pitchFactor, 0.5F, 1.0F);
        if (clampedFactor >= 0.999F) {
            return samples.clone();
        }
        short[] shifted = new short[samples.length];
        int lastIndex = samples.length - 1;
        for (int i = 0; i < shifted.length; i++) {
            float sourceIndex = i * clampedFactor;
            int left = Mth.clamp((int) Math.floor(sourceIndex), 0, lastIndex);
            int right = Math.min(lastIndex, left + 1);
            float blend = sourceIndex - left;
            float value = samples[left] + (samples[right] - samples[left]) * blend;
            shifted[i] = SpeakerSystem.softLimitSample(value);
        }
        return shifted;
    }


    private static void applyGain(short[] samples, float gain) {
        if (samples == null || samples.length == 0) {
            return;
        }
        float safeGain = Math.max(0.0F, gain);
        if (Math.abs(safeGain - 1.0F) < 1.0E-4F) {
            return;
        }
        for (int i = 0; i < samples.length; i++) {
            float normalized = samples[i] / 32768.0F;
            float amplified = normalized * safeGain;
            float compressed = (float) Math.tanh(amplified * 1.1F);
            samples[i] = SpeakerSystem.softLimitSample(compressed * 32767.0F);
        }
    }
    private static final class PitchRuntime {
        private final OpusDecoder decoder;
        private final OpusEncoder encoder;

        private PitchRuntime(OpusDecoder decoder, OpusEncoder encoder) {
            this.decoder = decoder;
            this.encoder = encoder;
        }

        private boolean isClosed() {
            return this.decoder == null || this.encoder == null || this.decoder.isClosed() || this.encoder.isClosed();
        }

        private byte[] transform(byte[] opusData, float pitchFactor, float gain) {
            if (isClosed() || opusData == null || opusData.length == 0) {
                return null;
            }
            try {
                short[] decoded = this.decoder.decode(opusData);
                if (decoded == null || decoded.length == 0) {
                    return null;
                }
                short[] shifted = applyPitchShift(decoded, pitchFactor);
                applyGain(shifted, gain);
                return shifted == null || shifted.length == 0 ? null : this.encoder.encode(shifted);
            } catch (RuntimeException exception) {
                Lg2.LOGGER.debug("Failed to pitch-shift little dictator voice packet", exception);
                try {
                    this.decoder.resetState();
                } catch (RuntimeException ignored) {
                }
                try {
                    this.encoder.resetState();
                } catch (RuntimeException ignored) {
                }
                return null;
            }
        }
    }
}
