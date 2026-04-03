package com.lostglade.network;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RendererBotPayloads {
	public static final int PROTOCOL_VERSION = 3;
	private static final int MAX_CAPTURE_PAYLOAD_BYTES = 1_048_576;
	private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

	private RendererBotPayloads() {
	}

	public static void registerPayloadTypes() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}

		PayloadTypeRegistry.playC2S().register(RendererBotHelloC2SPayload.TYPE, RendererBotHelloC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RendererBotPreviewFrameC2SPayload.TYPE, RendererBotPreviewFrameC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().registerLarge(RendererBotFullFrameC2SPayload.TYPE, RendererBotFullFrameC2SPayload.STREAM_CODEC, MAX_CAPTURE_PAYLOAD_BYTES);
		PayloadTypeRegistry.playC2S().registerLarge(RendererBotLiveFrameC2SPayload.TYPE, RendererBotLiveFrameC2SPayload.STREAM_CODEC, MAX_CAPTURE_PAYLOAD_BYTES);
		PayloadTypeRegistry.playC2S().registerLarge(RendererBotVideoRecordingCompleteC2SPayload.TYPE, RendererBotVideoRecordingCompleteC2SPayload.STREAM_CODEC, MAX_CAPTURE_PAYLOAD_BYTES);
		PayloadTypeRegistry.playC2S().register(RendererBotCaptureFailureC2SPayload.TYPE, RendererBotCaptureFailureC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RendererBotLiveStreamFailureC2SPayload.TYPE, RendererBotLiveStreamFailureC2SPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(RendererBotCaptureRequestS2CPayload.TYPE, RendererBotCaptureRequestS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(RendererBotLiveStreamStartS2CPayload.TYPE, RendererBotLiveStreamStartS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(RendererBotLiveStreamStopS2CPayload.TYPE, RendererBotLiveStreamStopS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(RendererBotVideoRecordingStartS2CPayload.TYPE, RendererBotVideoRecordingStartS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(RendererBotVideoRecordingStopS2CPayload.TYPE, RendererBotVideoRecordingStopS2CPayload.STREAM_CODEC);
	}

	public record RendererBotHelloC2SPayload(int protocolVersion) implements CustomPacketPayload {
		public static final Type<RendererBotHelloC2SPayload> TYPE = new Type<>(id("renderer_bot_hello"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotHelloC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotHelloC2SPayload::write, RendererBotHelloC2SPayload::new);

		public RendererBotHelloC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readVarInt());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeVarInt(this.protocolVersion);
		}

		@Override
		public Type<RendererBotHelloC2SPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotCaptureRequestS2CPayload(
			UUID requestId,
			String dimensionId,
			double expectedX,
			double expectedY,
			double expectedZ,
			float expectedYaw,
			float expectedPitch,
			int previewWidth,
			int previewHeight,
			int fullWidth,
			int fullHeight,
			int fovDegrees
	) implements CustomPacketPayload {
		public static final Type<RendererBotCaptureRequestS2CPayload> TYPE = new Type<>(id("renderer_bot_capture_request"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotCaptureRequestS2CPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotCaptureRequestS2CPayload::write, RendererBotCaptureRequestS2CPayload::new);

		public RendererBotCaptureRequestS2CPayload(FriendlyByteBuf buffer) {
			this(
					buffer.readUUID(),
					buffer.readUtf(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readFloat(),
					buffer.readFloat(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt()
			);
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.requestId);
			buffer.writeUtf(this.dimensionId);
			buffer.writeDouble(this.expectedX);
			buffer.writeDouble(this.expectedY);
			buffer.writeDouble(this.expectedZ);
			buffer.writeFloat(this.expectedYaw);
			buffer.writeFloat(this.expectedPitch);
			buffer.writeVarInt(this.previewWidth);
			buffer.writeVarInt(this.previewHeight);
			buffer.writeVarInt(this.fullWidth);
			buffer.writeVarInt(this.fullHeight);
			buffer.writeVarInt(this.fovDegrees);
		}

		@Override
		public Type<RendererBotCaptureRequestS2CPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotPreviewFrameC2SPayload(UUID requestId, byte[] pixels) implements CustomPacketPayload {
		public static final Type<RendererBotPreviewFrameC2SPayload> TYPE = new Type<>(id("renderer_bot_preview_frame"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotPreviewFrameC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotPreviewFrameC2SPayload::write, RendererBotPreviewFrameC2SPayload::new);

		public RendererBotPreviewFrameC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readUUID(), buffer.readByteArray());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.requestId);
			buffer.writeByteArray(this.pixels);
		}

		@Override
		public Type<RendererBotPreviewFrameC2SPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotFullFrameC2SPayload(UUID requestId, byte[] pixels) implements CustomPacketPayload {
		public static final Type<RendererBotFullFrameC2SPayload> TYPE = new Type<>(id("renderer_bot_full_frame"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotFullFrameC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotFullFrameC2SPayload::write, RendererBotFullFrameC2SPayload::new);

		public RendererBotFullFrameC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readUUID(), buffer.readByteArray());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.requestId);
			buffer.writeByteArray(this.pixels);
		}

		@Override
		public Type<RendererBotFullFrameC2SPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotLiveStreamStartS2CPayload(
			UUID streamId,
			String dimensionId,
			double expectedX,
			double expectedY,
			double expectedZ,
			float expectedYaw,
			float expectedPitch,
			int fullWidth,
			int fullHeight,
			int fovDegrees,
			int targetFps
	) implements CustomPacketPayload {
		public static final Type<RendererBotLiveStreamStartS2CPayload> TYPE = new Type<>(id("renderer_bot_live_stream_start"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotLiveStreamStartS2CPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotLiveStreamStartS2CPayload::write, RendererBotLiveStreamStartS2CPayload::new);

		public RendererBotLiveStreamStartS2CPayload(FriendlyByteBuf buffer) {
			this(
					buffer.readUUID(),
					buffer.readUtf(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readFloat(),
					buffer.readFloat(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt()
			);
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.streamId);
			buffer.writeUtf(this.dimensionId);
			buffer.writeDouble(this.expectedX);
			buffer.writeDouble(this.expectedY);
			buffer.writeDouble(this.expectedZ);
			buffer.writeFloat(this.expectedYaw);
			buffer.writeFloat(this.expectedPitch);
			buffer.writeVarInt(this.fullWidth);
			buffer.writeVarInt(this.fullHeight);
			buffer.writeVarInt(this.fovDegrees);
			buffer.writeVarInt(this.targetFps);
		}

		@Override
		public Type<RendererBotLiveStreamStartS2CPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotLiveStreamStopS2CPayload(UUID streamId) implements CustomPacketPayload {
		public static final Type<RendererBotLiveStreamStopS2CPayload> TYPE = new Type<>(id("renderer_bot_live_stream_stop"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotLiveStreamStopS2CPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotLiveStreamStopS2CPayload::write, RendererBotLiveStreamStopS2CPayload::new);

		public RendererBotLiveStreamStopS2CPayload(FriendlyByteBuf buffer) {
			this(buffer.readUUID());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.streamId);
		}

		@Override
		public Type<RendererBotLiveStreamStopS2CPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotLiveFrameC2SPayload(UUID streamId, byte[] pixels) implements CustomPacketPayload {
		public static final Type<RendererBotLiveFrameC2SPayload> TYPE = new Type<>(id("renderer_bot_live_frame"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotLiveFrameC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotLiveFrameC2SPayload::write, RendererBotLiveFrameC2SPayload::new);

		public RendererBotLiveFrameC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readUUID(), buffer.readByteArray());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.streamId);
			buffer.writeByteArray(this.pixels);
		}

		@Override
		public Type<RendererBotLiveFrameC2SPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotCaptureFailureC2SPayload(UUID requestId, String message) implements CustomPacketPayload {
		public static final Type<RendererBotCaptureFailureC2SPayload> TYPE = new Type<>(id("renderer_bot_capture_failure"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotCaptureFailureC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotCaptureFailureC2SPayload::write, RendererBotCaptureFailureC2SPayload::new);

		public RendererBotCaptureFailureC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readUUID(), buffer.readUtf(512));
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.requestId);
			buffer.writeUtf(this.message, 512);
		}

		@Override
		public Type<RendererBotCaptureFailureC2SPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotLiveStreamFailureC2SPayload(UUID streamId, String message) implements CustomPacketPayload {
		public static final Type<RendererBotLiveStreamFailureC2SPayload> TYPE = new Type<>(id("renderer_bot_live_stream_failure"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotLiveStreamFailureC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotLiveStreamFailureC2SPayload::write, RendererBotLiveStreamFailureC2SPayload::new);

		public RendererBotLiveStreamFailureC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readUUID(), buffer.readUtf(512));
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.streamId);
			buffer.writeUtf(this.message, 512);
		}

		@Override
		public Type<RendererBotLiveStreamFailureC2SPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotVideoRecordingStartS2CPayload(
			UUID requestId,
			String dimensionId,
			double expectedX,
			double expectedY,
			double expectedZ,
			float expectedYaw,
			float expectedPitch,
			int previewWidth,
			int previewHeight,
			int fullWidth,
			int fullHeight,
			int fovDegrees,
			int targetFps,
			int maxDurationSeconds
	) implements CustomPacketPayload {
		public static final Type<RendererBotVideoRecordingStartS2CPayload> TYPE = new Type<>(id("renderer_bot_video_recording_start"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotVideoRecordingStartS2CPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotVideoRecordingStartS2CPayload::write, RendererBotVideoRecordingStartS2CPayload::new);

		public RendererBotVideoRecordingStartS2CPayload(FriendlyByteBuf buffer) {
			this(
					buffer.readUUID(),
					buffer.readUtf(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readFloat(),
					buffer.readFloat(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt(),
					buffer.readVarInt()
			);
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.requestId);
			buffer.writeUtf(this.dimensionId);
			buffer.writeDouble(this.expectedX);
			buffer.writeDouble(this.expectedY);
			buffer.writeDouble(this.expectedZ);
			buffer.writeFloat(this.expectedYaw);
			buffer.writeFloat(this.expectedPitch);
			buffer.writeVarInt(this.previewWidth);
			buffer.writeVarInt(this.previewHeight);
			buffer.writeVarInt(this.fullWidth);
			buffer.writeVarInt(this.fullHeight);
			buffer.writeVarInt(this.fovDegrees);
			buffer.writeVarInt(this.targetFps);
			buffer.writeVarInt(this.maxDurationSeconds);
		}

		@Override
		public Type<RendererBotVideoRecordingStartS2CPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotVideoRecordingStopS2CPayload(UUID requestId) implements CustomPacketPayload {
		public static final Type<RendererBotVideoRecordingStopS2CPayload> TYPE = new Type<>(id("renderer_bot_video_recording_stop"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotVideoRecordingStopS2CPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotVideoRecordingStopS2CPayload::write, RendererBotVideoRecordingStopS2CPayload::new);

		public RendererBotVideoRecordingStopS2CPayload(FriendlyByteBuf buffer) {
			this(buffer.readUUID());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.requestId);
		}

		@Override
		public Type<RendererBotVideoRecordingStopS2CPayload> type() {
			return TYPE;
		}
	}

	public record RendererBotVideoRecordingCompleteC2SPayload(
			UUID requestId,
			long durationMs,
			int fps,
			byte[] previewPixels,
			byte[] fullPixels
	) implements CustomPacketPayload {
		public static final Type<RendererBotVideoRecordingCompleteC2SPayload> TYPE = new Type<>(id("renderer_bot_video_recording_complete"));
		public static final StreamCodec<FriendlyByteBuf, RendererBotVideoRecordingCompleteC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RendererBotVideoRecordingCompleteC2SPayload::write, RendererBotVideoRecordingCompleteC2SPayload::new);

		public RendererBotVideoRecordingCompleteC2SPayload(FriendlyByteBuf buffer) {
			this(
					buffer.readUUID(),
					buffer.readVarLong(),
					buffer.readVarInt(),
					buffer.readByteArray(),
					buffer.readByteArray()
			);
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeUUID(this.requestId);
			buffer.writeVarLong(this.durationMs);
			buffer.writeVarInt(this.fps);
			buffer.writeByteArray(this.previewPixels);
			buffer.writeByteArray(this.fullPixels);
		}

		@Override
		public Type<RendererBotVideoRecordingCompleteC2SPayload> type() {
			return TYPE;
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, path);
	}
}
