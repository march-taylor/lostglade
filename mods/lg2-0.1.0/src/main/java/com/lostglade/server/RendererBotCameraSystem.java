package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.Lg2Config;
import com.lostglade.network.RendererBotPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class RendererBotCameraSystem {
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final int MAX_VIDEO_RECORDING_FPS = 20;
	private static final int MAX_LIVE_STREAM_FPS = 20;
	private static final long LIVE_STREAM_STALE_MS = 1_500L;
	private static final Map<UUID, BotHandshake> READY_BOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingCapture> PENDING_CAPTURES = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingVideoRecording> PENDING_VIDEO_RECORDINGS = new ConcurrentHashMap<>();
	private static final Map<UUID, ActiveLiveStream> ACTIVE_LIVE_STREAMS = new ConcurrentHashMap<>();
	private static final Map<String, UUID> LIVE_STREAMS_BY_OWNER = new ConcurrentHashMap<>();

	private RendererBotCameraSystem() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID botUuid = handler.player.getUUID();
			READY_BOTS.remove(botUuid);
			failCapturesForBot(botUuid, "Renderer bot disconnected during capture");
			failVideoRecordingsForBot(botUuid, "Renderer bot disconnected during video recording");
			failLiveStreamsForBot(botUuid, "Renderer bot disconnected during live stream");
		});
		ServerPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotHelloC2SPayload.TYPE,
				(payload, context) -> {
					if (payload.protocolVersion() != RendererBotPayloads.PROTOCOL_VERSION) {
						READY_BOTS.remove(context.player().getUUID());
						return;
					}
					READY_BOTS.put(context.player().getUUID(), new BotHandshake(context.player().getUUID(), context.player().getScoreboardName()));
				}
		);
		ServerPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotPreviewFrameC2SPayload.TYPE,
				(payload, context) -> {
					PendingCapture capture = PENDING_CAPTURES.get(payload.requestId());
					if (capture == null || !capture.botUuid().equals(context.player().getUUID())) {
						return;
					}
					capture.previewFuture().complete(payload.pixels());
					cleanupIfFinished(payload.requestId(), capture);
				}
		);
		ServerPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotFullFrameC2SPayload.TYPE,
				(payload, context) -> {
					PendingCapture capture = PENDING_CAPTURES.get(payload.requestId());
					if (capture == null || !capture.botUuid().equals(context.player().getUUID())) {
						return;
					}
					capture.fullFuture().complete(payload.pixels());
					cleanupIfFinished(payload.requestId(), capture);
				}
		);
		ServerPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotLiveFrameC2SPayload.TYPE,
				(payload, context) -> {
					ActiveLiveStream stream = ACTIVE_LIVE_STREAMS.get(payload.streamId());
					if (stream == null || !stream.botUuid().equals(context.player().getUUID())) {
						return;
					}
					stream.markFrameReceived();
					context.player().level().getServer().execute(() -> {
						ActiveLiveStream current = ACTIVE_LIVE_STREAMS.get(payload.streamId());
						if (current == null || !current.botUuid().equals(context.player().getUUID())) {
							return;
						}
						current.onFrame().accept(payload.pixels());
					});
				}
		);
		ServerPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotCaptureFailureC2SPayload.TYPE,
				(payload, context) -> {
					PendingCapture capture = PENDING_CAPTURES.get(payload.requestId());
					if (capture == null || !capture.botUuid().equals(context.player().getUUID())) {
						PendingVideoRecording recording = PENDING_VIDEO_RECORDINGS.get(payload.requestId());
						if (recording == null || !recording.botUuid().equals(context.player().getUUID())) {
							return;
						}
						IllegalStateException failure = new IllegalStateException(payload.message());
						recording.completionFuture().completeExceptionally(failure);
						PENDING_VIDEO_RECORDINGS.remove(payload.requestId());
						releaseBotCameraIfNeeded(recording.server(), recording.botUuid(), recording.resetCameraOnFinish());
						return;
					}
					IllegalStateException failure = new IllegalStateException(payload.message());
					capture.previewFuture().completeExceptionally(failure);
					capture.fullFuture().completeExceptionally(failure);
					PENDING_CAPTURES.remove(payload.requestId());
					releaseBotCameraIfNeeded(capture.server(), capture.botUuid(), capture.resetCameraOnFinish());
				}
		);
		ServerPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotLiveStreamFailureC2SPayload.TYPE,
				(payload, context) -> {
					ActiveLiveStream stream = ACTIVE_LIVE_STREAMS.get(payload.streamId());
					if (stream == null || !stream.botUuid().equals(context.player().getUUID())) {
						return;
					}
					context.player().level().getServer().execute(() -> {
						ActiveLiveStream current = ACTIVE_LIVE_STREAMS.get(payload.streamId());
						if (current == null || !current.botUuid().equals(context.player().getUUID())) {
							return;
						}
						stopLiveStreamInternal(current, payload.message(), true);
					});
				}
		);
		ServerPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotVideoRecordingCompleteC2SPayload.TYPE,
				(payload, context) -> {
					PendingVideoRecording recording = PENDING_VIDEO_RECORDINGS.get(payload.requestId());
					if (recording == null || !recording.botUuid().equals(context.player().getUUID())) {
						return;
					}
					recording.completionFuture().complete(new VideoRecordingResult(payload.durationMs(), payload.fps(), payload.previewPixels(), payload.fullPixels()));
					PENDING_VIDEO_RECORDINGS.remove(payload.requestId());
					releaseBotCameraIfNeeded(recording.server(), recording.botUuid(), recording.resetCameraOnFinish());
				}
		);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			READY_BOTS.clear();
			for (PendingCapture capture : PENDING_CAPTURES.values()) {
				IllegalStateException failure = new IllegalStateException("Renderer bot capture aborted: server stopping");
				capture.previewFuture().completeExceptionally(failure);
				capture.fullFuture().completeExceptionally(failure);
			}
			PENDING_CAPTURES.clear();
			for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
				recording.completionFuture().completeExceptionally(new IllegalStateException("Renderer bot recording aborted: server stopping"));
			}
			PENDING_VIDEO_RECORDINGS.clear();
			for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
				stream.onFailure().accept("Renderer bot live stream aborted: server stopping");
			}
			ACTIVE_LIVE_STREAMS.clear();
			LIVE_STREAMS_BY_OWNER.clear();
		});
	}

	public static ClientCaptureHandle requestPhotoCapture(ServerPlayer requester, int mapsWide, int mapsHigh) {
		ServerLevel level = requester == null || !(requester.level() instanceof ServerLevel serverLevel) ? null : serverLevel;
		if (level == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return null;
		}

		ServerPlayer bot = selectBot(server);
		if (bot == null) {
			return null;
		}
		stopLiveStreamForBot(bot.getUUID(), "Renderer bot switched from live stream to photo capture", false);

		return requestCaptureInternal(
				server,
				bot,
				level,
				requester.getX(),
				requester.getY(),
				requester.getZ(),
				requester.getYRot(),
				requester.getXRot(),
				128,
				128,
				Math.max(1, mapsWide) * 128,
				Math.max(1, mapsHigh) * 128,
				70,
				requester
		);
	}

	public static ClientCaptureHandle requestCapture(
			ServerLevel level,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			int previewWidth,
			int previewHeight,
			int fullWidth,
			int fullHeight,
			int fovDegrees
	) {
		MinecraftServer server = level != null ? level.getServer() : null;
		if (server == null) {
			return null;
		}

		ServerPlayer bot = selectBot(server);
		if (bot == null) {
			return null;
		}
		stopLiveStreamForBot(bot.getUUID(), "Renderer bot switched from live stream to photo capture", false);

		return requestCaptureInternal(
				server,
				bot,
				level,
				x,
				y,
				z,
				yaw,
				pitch,
				previewWidth,
				previewHeight,
				fullWidth,
				fullHeight,
				fovDegrees,
				null
		);
	}

	private static ClientCaptureHandle requestCaptureInternal(
			MinecraftServer server,
			ServerPlayer bot,
			ServerLevel level,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			int previewWidth,
			int previewHeight,
			int fullWidth,
			int fullHeight,
			int fovDegrees,
			Entity followTarget
	) {
		if (server == null || bot == null || level == null) {
			return null;
		}

		int clampedPreviewWidth = Math.max(1, previewWidth);
		int clampedPreviewHeight = Math.max(1, previewHeight);
		int clampedFullWidth = Math.max(1, fullWidth);
		int clampedFullHeight = Math.max(1, fullHeight);
		UUID requestId = UUID.randomUUID();
		long timeoutMillis = Math.max(500L, Lg2Config.get().cameraRendererBotTimeoutMs);
		CompletableFuture<byte[]> previewFuture = new CompletableFuture<>();
		CompletableFuture<byte[]> fullFuture = new CompletableFuture<>();
		PendingCapture pending = new PendingCapture(
				requestId,
				server,
				bot.getUUID(),
				followTarget != null,
				previewFuture,
				fullFuture
		);
		PENDING_CAPTURES.put(requestId, pending);
		applyTimeout(requestId, pending, timeoutMillis);

		if (followTarget != null) {
			prepareBotToFollowEntity(bot, level, x, y, z, yaw, pitch, followTarget);
		} else {
			prepareBotForStaticView(bot, level, x, y, z, yaw, pitch);
		}

		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotCaptureRequestS2CPayload(
						requestId,
						level.dimension().identifier().toString(),
						x,
						y,
						z,
						yaw,
						pitch,
						clampedPreviewWidth,
						clampedPreviewHeight,
						clampedFullWidth,
						clampedFullHeight,
						Math.max(1, fovDegrees)
				)
		);

		return new ClientCaptureHandle(requestId, previewFuture, fullFuture);
	}

	public static boolean ensureLiveStream(
			String ownerKey,
			ServerLevel level,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			int fullWidth,
			int fullHeight,
			int fovDegrees,
			int targetFps,
			Consumer<byte[]> onFrame,
			Consumer<String> onFailure
	) {
		if (ownerKey == null || level == null || onFrame == null || onFailure == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			onFailure.accept("Сервер камеры недоступен");
			return false;
		}

		ServerPlayer bot = selectBot(server);
		if (bot == null) {
			onFailure.accept("Нет активного клиента камеры");
			return false;
		}

		LiveStreamSpec desiredSpec = new LiveStreamSpec(
				level.dimension().identifier().toString(),
				x,
				y,
				z,
				yaw,
				pitch,
				Math.max(1, fullWidth),
				Math.max(1, fullHeight),
				Math.max(1, fovDegrees),
				Math.clamp(Math.max(1, targetFps), 1, MAX_LIVE_STREAM_FPS)
		);
		UUID existingStreamId = LIVE_STREAMS_BY_OWNER.get(ownerKey);
		if (existingStreamId != null) {
			ActiveLiveStream existing = ACTIVE_LIVE_STREAMS.get(existingStreamId);
			if (existing != null
					&& existing.botUuid().equals(bot.getUUID())
					&& existing.spec().equals(desiredSpec)
					&& !existing.isStale()) {
				return true;
			}
			stopLiveStreamInternal(existing, "Renderer bot live stream restarted", false);
		}

		stopLiveStreamForBot(bot.getUUID(), "Renderer bot reassigned to another live stream", false);

		prepareBotForStaticView(bot, level, x, y, z, yaw, pitch);

		UUID streamId = UUID.randomUUID();
		ActiveLiveStream stream = new ActiveLiveStream(server, streamId, ownerKey, bot.getUUID(), desiredSpec, onFrame, onFailure);
		ACTIVE_LIVE_STREAMS.put(streamId, stream);
		LIVE_STREAMS_BY_OWNER.put(ownerKey, streamId);
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotLiveStreamStartS2CPayload(
						streamId,
						desiredSpec.dimensionId(),
						desiredSpec.expectedX(),
						desiredSpec.expectedY(),
						desiredSpec.expectedZ(),
						desiredSpec.expectedYaw(),
						desiredSpec.expectedPitch(),
						desiredSpec.fullWidth(),
						desiredSpec.fullHeight(),
						desiredSpec.fovDegrees(),
						desiredSpec.targetFps()
				)
		);
		return true;
	}

	public static void stopLiveStream(String ownerKey) {
		if (ownerKey == null) {
			return;
		}
		UUID streamId = LIVE_STREAMS_BY_OWNER.remove(ownerKey);
		if (streamId == null) {
			return;
		}
		stopLiveStreamInternal(ACTIVE_LIVE_STREAMS.remove(streamId), "Renderer bot live stream stopped", false);
	}

	public static boolean isLiveStreamHealthy(String ownerKey) {
		if (ownerKey == null) {
			return false;
		}
		UUID streamId = LIVE_STREAMS_BY_OWNER.get(ownerKey);
		if (streamId == null) {
			return false;
		}
		ActiveLiveStream stream = ACTIVE_LIVE_STREAMS.get(streamId);
		return stream != null && !stream.isStale();
	}

	public static VideoRecordingHandle startVideoRecording(ServerPlayer requester, int mapsWide, int mapsHigh, int targetFps, int maxDurationSeconds) {
		MinecraftServer server = requester == null || requester.level() == null ? null : requester.level().getServer();
		if (server == null) {
			return null;
		}

		ServerPlayer bot = selectBot(server);
		if (bot == null) {
			return null;
		}
		stopLiveStreamForBot(bot.getUUID(), "Renderer bot switched from live stream to video recording", false);

		int previewWidth = 128;
		int previewHeight = 128;
		int fullWidth = Math.max(1, mapsWide) * 128;
		int fullHeight = Math.max(1, mapsHigh) * 128;
		UUID requestId = UUID.randomUUID();
		long timeoutMillis = Math.max(5_000L, Lg2Config.get().cameraRendererBotTimeoutMs);
		CompletableFuture<VideoRecordingResult> completionFuture = new CompletableFuture<>();
		PendingVideoRecording pending = new PendingVideoRecording(requestId, server, bot.getUUID(), true, completionFuture);
		PENDING_VIDEO_RECORDINGS.put(requestId, pending);
		completionFuture.orTimeout(Math.max(timeoutMillis, maxDurationSeconds * 1_000L + 30_000L), TimeUnit.MILLISECONDS)
				.exceptionally(throwable -> {
					if (PENDING_VIDEO_RECORDINGS.remove(requestId, pending)) {
						releaseBotCameraIfNeeded(pending.server(), pending.botUuid(), pending.resetCameraOnFinish());
						completionFuture.completeExceptionally(throwable);
					}
					return null;
				});

		prepareBotToFollowEntity(
				bot,
				(ServerLevel) requester.level(),
				requester.getX(),
				requester.getY(),
				requester.getZ(),
				requester.getYRot(),
				requester.getXRot(),
				requester
		);

		int clampedTargetFps = Math.clamp(Math.max(1, targetFps), 1, MAX_VIDEO_RECORDING_FPS);
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload(
						requestId,
						requester.level().dimension().identifier().toString(),
						requester.getX(),
						requester.getY(),
						requester.getZ(),
						requester.getYRot(),
						requester.getXRot(),
						previewWidth,
						previewHeight,
						fullWidth,
						fullHeight,
						70,
						clampedTargetFps,
						Math.max(1, maxDurationSeconds)
				)
		);
		return new VideoRecordingHandle(requestId, bot.getUUID(), completionFuture);
	}

	public static void stopVideoRecording(MinecraftServer server, UUID requestId) {
		if (server == null || requestId == null) {
			return;
		}
		PendingVideoRecording recording = PENDING_VIDEO_RECORDINGS.get(requestId);
		if (recording == null) {
			return;
		}
		ServerPlayer bot = server.getPlayerList().getPlayer(recording.botUuid());
		if (bot == null || !ServerPlayNetworking.canSend(bot, RendererBotPayloads.RendererBotVideoRecordingStopS2CPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(bot, new RendererBotPayloads.RendererBotVideoRecordingStopS2CPayload(requestId));
	}

	public static boolean hasReadyBot(MinecraftServer server) {
		return server != null && selectBot(server) != null;
	}

	public static ServerPlayer readyBot(MinecraftServer server) {
		return server == null ? null : selectBot(server);
	}

	private static void applyTimeout(UUID requestId, PendingCapture capture, long timeoutMillis) {
		capture.previewFuture()
				.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
				.exceptionally(throwable -> {
					failPending(requestId, capture, throwable);
					return null;
				});
		capture.fullFuture()
				.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
				.exceptionally(throwable -> {
					failPending(requestId, capture, throwable);
					return null;
				});
	}

	private static void failPending(UUID requestId, PendingCapture capture, Throwable throwable) {
		PENDING_CAPTURES.remove(requestId);
		if (!capture.previewFuture().isDone()) {
			capture.previewFuture().completeExceptionally(throwable);
		}
		if (!capture.fullFuture().isDone()) {
			capture.fullFuture().completeExceptionally(throwable);
		}
		releaseBotCameraIfNeeded(capture.server(), capture.botUuid(), capture.resetCameraOnFinish());
	}

	private static void cleanupIfFinished(UUID requestId, PendingCapture capture) {
		if (capture.previewFuture().isDone() && capture.fullFuture().isDone()) {
			PENDING_CAPTURES.remove(requestId);
			releaseBotCameraIfNeeded(capture.server(), capture.botUuid(), capture.resetCameraOnFinish());
		}
	}

	private static void failVideoRecordingsForBot(UUID botUuid, String message) {
		if (botUuid == null) {
			return;
		}
		IllegalStateException failure = new IllegalStateException(message);
		for (Map.Entry<UUID, PendingVideoRecording> entry : PENDING_VIDEO_RECORDINGS.entrySet()) {
			PendingVideoRecording recording = entry.getValue();
			if (recording == null || !botUuid.equals(recording.botUuid())) {
				continue;
			}
			if (PENDING_VIDEO_RECORDINGS.remove(entry.getKey(), recording)) {
				recording.completionFuture().completeExceptionally(failure);
				releaseBotCameraIfNeeded(recording.server(), recording.botUuid(), recording.resetCameraOnFinish());
			}
		}
	}

	private static void failLiveStreamsForBot(UUID botUuid, String message) {
		if (botUuid == null) {
			return;
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !botUuid.equals(stream.botUuid())) {
				continue;
			}
			stopLiveStreamInternal(stream, message, true);
		}
	}

	private static void stopLiveStreamForBot(UUID botUuid, String message, boolean notifyFailure) {
		if (botUuid == null) {
			return;
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !botUuid.equals(stream.botUuid())) {
				continue;
			}
			stopLiveStreamInternal(stream, message, notifyFailure);
		}
	}

	private static void stopLiveStreamInternal(ActiveLiveStream stream, String message, boolean notifyFailure) {
		if (stream == null) {
			return;
		}
		ACTIVE_LIVE_STREAMS.remove(stream.streamId(), stream);
		LIVE_STREAMS_BY_OWNER.remove(stream.ownerKey(), stream.streamId());
		ServerPlayer bot = stream.server().getPlayerList().getPlayer(stream.botUuid());
		if (bot != null && ServerPlayNetworking.canSend(bot, RendererBotPayloads.RendererBotLiveStreamStopS2CPayload.TYPE)) {
			ServerPlayNetworking.send(bot, new RendererBotPayloads.RendererBotLiveStreamStopS2CPayload(stream.streamId()));
		}
		if (notifyFailure) {
			stream.onFailure().accept(message);
		}
	}

	private static void failCapturesForBot(UUID botUuid, String message) {
		if (botUuid == null) {
			return;
		}
		IllegalStateException failure = new IllegalStateException(message);
		for (Map.Entry<UUID, PendingCapture> entry : PENDING_CAPTURES.entrySet()) {
			PendingCapture capture = entry.getValue();
			if (capture == null || !botUuid.equals(capture.botUuid())) {
				continue;
			}
			if (PENDING_CAPTURES.remove(entry.getKey(), capture)) {
				capture.previewFuture().completeExceptionally(failure);
				capture.fullFuture().completeExceptionally(failure);
			}
		}
	}

	private static ServerPlayer selectBot(MinecraftServer server) {
		String configuredName = Lg2Config.get().cameraRendererBotPlayerName;
		if (configuredName == null || configuredName.isBlank()) {
			return null;
		}

		String trimmedName = configuredName.trim();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.getScoreboardName().equalsIgnoreCase(trimmedName)) {
				continue;
			}
			if (!READY_BOTS.containsKey(player.getUUID())) {
				continue;
			}
			if (!ServerPlayNetworking.canSend(player, RendererBotPayloads.RendererBotCaptureRequestS2CPayload.TYPE)) {
				continue;
			}
			return player;
		}

		return null;
	}

	private static void prepareBotForStaticView(
			ServerPlayer bot,
			ServerLevel level,
			double x,
			double y,
			double z,
			float yaw,
			float pitch
	) {
		if (bot == null || level == null) {
			return;
		}
		if (bot.getCamera() != bot) {
			bot.setCamera(bot);
		}
		level.getChunkAt(net.minecraft.core.BlockPos.containing(x, y, z));
		bot.teleportTo(
				level,
				x,
				y,
				z,
				ABSOLUTE_TELEPORT,
				yaw,
				pitch,
				false
		);
		bot.fallDistance = 0.0F;
	}

	private static void prepareBotToFollowEntity(
			ServerPlayer bot,
			ServerLevel level,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			Entity target
	) {
		prepareBotForStaticView(bot, level, x, y, z, yaw, pitch);
		if (bot != null && target != null && target.level() == level) {
			bot.setCamera(target);
		}
	}

	private static void releaseBotCameraIfNeeded(MinecraftServer server, UUID botUuid, boolean resetCameraOnFinish) {
		if (!resetCameraOnFinish || server == null || botUuid == null) {
			return;
		}
		ServerPlayer bot = server.getPlayerList().getPlayer(botUuid);
		if (bot != null && bot.getCamera() != bot) {
			bot.setCamera(bot);
		}
	}

	public record ClientCaptureHandle(
			UUID requestId,
			CompletableFuture<byte[]> previewFuture,
			CompletableFuture<byte[]> fullFuture
	) {
		public byte[] awaitPreview() {
			return await(this.previewFuture);
		}

		public byte[] awaitFull() {
			return await(this.fullFuture);
		}

		private static byte[] await(CompletableFuture<byte[]> future) {
			try {
				return future.get();
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Renderer bot capture interrupted", interruptedException);
			} catch (ExecutionException executionException) {
				Throwable cause = executionException.getCause();
				throw new IllegalStateException(
						cause != null ? cause.getMessage() : "Renderer bot capture failed",
						cause != null ? cause : executionException
				);
			}
		}
	}

	private record BotHandshake(UUID playerUuid, String playerName) {
	}

	private record PendingCapture(
			UUID requestId,
			MinecraftServer server,
			UUID botUuid,
			boolean resetCameraOnFinish,
			CompletableFuture<byte[]> previewFuture,
			CompletableFuture<byte[]> fullFuture
	) {
	}

	private record PendingVideoRecording(
			UUID requestId,
			MinecraftServer server,
			UUID botUuid,
			boolean resetCameraOnFinish,
			CompletableFuture<VideoRecordingResult> completionFuture
	) {
	}

	private record LiveStreamSpec(
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
	) {
	}

	private static final class ActiveLiveStream {
		private final MinecraftServer server;
		private final UUID streamId;
		private final String ownerKey;
		private final UUID botUuid;
		private final LiveStreamSpec spec;
		private final Consumer<byte[]> onFrame;
		private final Consumer<String> onFailure;
		private final long startedAtMillis;
		private volatile long lastFrameAtMillis;

		private ActiveLiveStream(
				MinecraftServer server,
				UUID streamId,
				String ownerKey,
				UUID botUuid,
				LiveStreamSpec spec,
				Consumer<byte[]> onFrame,
				Consumer<String> onFailure
		) {
			this.server = server;
			this.streamId = streamId;
			this.ownerKey = ownerKey;
			this.botUuid = botUuid;
			this.spec = spec;
			this.onFrame = onFrame;
			this.onFailure = onFailure;
			this.startedAtMillis = System.currentTimeMillis();
			this.lastFrameAtMillis = 0L;
		}

		private MinecraftServer server() {
			return this.server;
		}

		private UUID streamId() {
			return this.streamId;
		}

		private String ownerKey() {
			return this.ownerKey;
		}

		private UUID botUuid() {
			return this.botUuid;
		}

		private LiveStreamSpec spec() {
			return this.spec;
		}

		private Consumer<byte[]> onFrame() {
			return this.onFrame;
		}

		private Consumer<String> onFailure() {
			return this.onFailure;
		}

		private void markFrameReceived() {
			this.lastFrameAtMillis = System.currentTimeMillis();
		}

		private boolean isStale() {
			long referenceTime = this.lastFrameAtMillis > 0L ? this.lastFrameAtMillis : this.startedAtMillis;
			return System.currentTimeMillis() - referenceTime > LIVE_STREAM_STALE_MS;
		}
	}

	public record VideoRecordingResult(
			long durationMs,
			int fps,
			byte[] previewPixels,
			byte[] fullPixels
	) {
	}

	public record VideoRecordingHandle(
			UUID requestId,
			UUID botUuid,
			CompletableFuture<VideoRecordingResult> completionFuture
	) {
	}
}
