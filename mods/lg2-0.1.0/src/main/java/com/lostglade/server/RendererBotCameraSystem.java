package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.Lg2Config;
import com.lostglade.network.RendererBotPayloads;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public final class RendererBotCameraSystem {
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final int MAX_VIDEO_RECORDING_FPS = 20;
	private static final Map<UUID, BotHandshake> READY_BOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingCapture> PENDING_CAPTURES = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingVideoRecording> PENDING_VIDEO_RECORDINGS = new ConcurrentHashMap<>();

	private RendererBotCameraSystem() {
	}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID botUuid = handler.player.getUUID();
			READY_BOTS.remove(botUuid);
			failCapturesForBot(botUuid, "Renderer bot disconnected during capture");
			failVideoRecordingsForBot(botUuid, "Renderer bot disconnected during video recording");
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
						return;
					}
					IllegalStateException failure = new IllegalStateException(payload.message());
					capture.previewFuture().completeExceptionally(failure);
					capture.fullFuture().completeExceptionally(failure);
					PENDING_CAPTURES.remove(payload.requestId());
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
		});
	}

	public static ClientCaptureHandle requestPhotoCapture(ServerPlayer requester, int mapsWide, int mapsHigh) {
		MinecraftServer server = requester == null || requester.level() == null ? null : requester.level().getServer();
		if (server == null) {
			return null;
		}

		ServerPlayer bot = selectBot(server);
		if (bot == null) {
			return null;
		}

		int previewWidth = 128;
		int previewHeight = 128;
		int fullWidth = Math.max(1, mapsWide) * 128;
		int fullHeight = Math.max(1, mapsHigh) * 128;
		UUID requestId = UUID.randomUUID();
		long timeoutMillis = Math.max(500L, Lg2Config.get().cameraRendererBotTimeoutMs);
		CompletableFuture<byte[]> previewFuture = new CompletableFuture<>();
		CompletableFuture<byte[]> fullFuture = new CompletableFuture<>();
		PendingCapture pending = new PendingCapture(requestId, bot.getUUID(), previewFuture, fullFuture);
		PENDING_CAPTURES.put(requestId, pending);
		applyTimeout(requestId, pending, timeoutMillis);

		requester.level().getChunkAt(requester.blockPosition());
		bot.teleportTo(
				(net.minecraft.server.level.ServerLevel) requester.level(),
				requester.getX(),
				requester.getY(),
				requester.getZ(),
				ABSOLUTE_TELEPORT,
				requester.getYRot(),
				requester.getXRot(),
				false
		);
		bot.fallDistance = 0.0F;

		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotCaptureRequestS2CPayload(
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
						70
				)
		);

		return new ClientCaptureHandle(requestId, previewFuture, fullFuture);
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

		int previewWidth = 128;
		int previewHeight = 128;
		int fullWidth = Math.max(1, mapsWide) * 128;
		int fullHeight = Math.max(1, mapsHigh) * 128;
		UUID requestId = UUID.randomUUID();
		long timeoutMillis = Math.max(5_000L, Lg2Config.get().cameraRendererBotTimeoutMs);
		CompletableFuture<VideoRecordingResult> completionFuture = new CompletableFuture<>();
		PendingVideoRecording pending = new PendingVideoRecording(requestId, bot.getUUID(), completionFuture);
		PENDING_VIDEO_RECORDINGS.put(requestId, pending);
		completionFuture.orTimeout(Math.max(timeoutMillis, maxDurationSeconds * 1_000L + 30_000L), TimeUnit.MILLISECONDS)
				.exceptionally(throwable -> {
					if (PENDING_VIDEO_RECORDINGS.remove(requestId, pending)) {
						completionFuture.completeExceptionally(throwable);
					}
					return null;
				});

		requester.level().getChunkAt(requester.blockPosition());
		bot.teleportTo(
				(net.minecraft.server.level.ServerLevel) requester.level(),
				requester.getX(),
				requester.getY(),
				requester.getZ(),
				ABSOLUTE_TELEPORT,
				requester.getYRot(),
				requester.getXRot(),
				false
		);
		bot.fallDistance = 0.0F;

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
	}

	private static void cleanupIfFinished(UUID requestId, PendingCapture capture) {
		if (capture.previewFuture().isDone() && capture.fullFuture().isDone()) {
			PENDING_CAPTURES.remove(requestId);
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
			}
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
			UUID botUuid,
			CompletableFuture<byte[]> previewFuture,
			CompletableFuture<byte[]> fullFuture
	) {
	}

	private record PendingVideoRecording(
			UUID requestId,
			UUID botUuid,
			CompletableFuture<VideoRecordingResult> completionFuture
	) {
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
