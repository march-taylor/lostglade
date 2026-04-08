package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.network.RendererBotPayloads;
import com.lostglade.network.RendererBotShadowPacketCodec;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	private static final int SHADOW_VIEW_DISTANCE_MARGIN_CHUNKS = 6;
	private static final int SHADOW_REAR_VIEW_CHUNKS = 2;
	private static final long LIVE_STREAM_STALE_MS = 1_500L;
	private static final long PHOTO_CAPTURE_RETRY_INTERVAL_MS = 50L;
	private static final double SHADOW_FORWARD_HALF_FOV_DEGREES = 80.0D;
	private static final double SHADOW_NEAR_OMNI_RADIUS_CHUNKS = 3.0D;
	private static final double SHADOW_SIDE_SAFETY_MARGIN_CHUNKS = 1.5D;
	private static final double SHARED_RENDER_RADIUS_BLOCKS = 96.0D;
	private static final double SHARED_RENDER_RADIUS_SQ = SHARED_RENDER_RADIUS_BLOCKS * SHARED_RENDER_RADIUS_BLOCKS;
	private static final Map<UUID, BotHandshake> READY_BOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingCapture> PENDING_CAPTURES = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingVideoRecording> PENDING_VIDEO_RECORDINGS = new ConcurrentHashMap<>();
	private static final Map<UUID, ActiveLiveStream> ACTIVE_LIVE_STREAMS = new ConcurrentHashMap<>();
	private static final Map<String, UUID> LIVE_STREAMS_BY_OWNER = new ConcurrentHashMap<>();
	private static final Map<ChunkTicketKey, Integer> ACTIVE_CAMERA_CHUNK_TICKETS = new HashMap<>();
	private static final Map<ShadowSyncKey, ShadowDimensionSyncState> ACTIVE_SHADOW_SYNC_STATES = new HashMap<>();
	private static final Set<ChunkTicketKey> DIRTY_SHADOW_CHUNKS = ConcurrentHashMap.newKeySet();

	private RendererBotCameraSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RendererBotCameraSystem::tickVirtualCameraState);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID botUuid = handler.player.getUUID();
			clearShadowSyncState(server, botUuid, false);
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
			releaseAllVirtualCameraChunkTickets(server);
			clearAllShadowSyncStates(server, false);
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
		if (!canBotRenderLevel(bot, level)) {
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
				level.dimension(),
				x,
				y,
				z,
				yaw,
				pitch,
				Math.max(1, fovDegrees),
				followTarget != null ? followTarget.getUUID() : null,
				previewFuture,
				fullFuture
		);
		PENDING_CAPTURES.put(requestId, pending);
		applyTimeout(requestId, pending, timeoutMillis);
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
						followTarget != null ? followTarget.getUUID() : null,
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
			BlockPos cameraPos,
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
		if (!canBotRenderLevel(bot, level)) {
			onFailure.accept("Клиент камеры не может рендерить этот мир");
			return false;
		}

		LiveStreamSpec desiredSpec = new LiveStreamSpec(
				level.dimension(),
				cameraPos == null ? null : cameraPos.immutable(),
				x,
				y,
				z,
				yaw,
				pitch,
				null,
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

		UUID streamId = UUID.randomUUID();
		ActiveLiveStream stream = new ActiveLiveStream(server, streamId, ownerKey, bot.getUUID(), desiredSpec, onFrame, onFailure);
		ACTIVE_LIVE_STREAMS.put(streamId, stream);
		LIVE_STREAMS_BY_OWNER.put(ownerKey, streamId);
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotLiveStreamStartS2CPayload(
						streamId,
						desiredSpec.dimension().identifier().toString(),
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

		int previewWidth = 128;
		int previewHeight = 128;
		int fullWidth = Math.max(1, mapsWide) * 128;
		int fullHeight = Math.max(1, mapsHigh) * 128;
		int clampedTargetFps = Math.clamp(Math.max(1, targetFps), 1, MAX_VIDEO_RECORDING_FPS);
		if (!canBotRenderLevel(bot, (ServerLevel) requester.level())) {
			return null;
		}
		UUID requestId = UUID.randomUUID();
		long timeoutMillis = Math.max(5_000L, Lg2Config.get().cameraRendererBotTimeoutMs);
		CompletableFuture<VideoRecordingResult> completionFuture = new CompletableFuture<>();
		PendingVideoRecording pending = new PendingVideoRecording(
				requestId,
				server,
				bot.getUUID(),
				true,
				requester.level().dimension(),
				requester.getX(),
				requester.getY(),
				requester.getZ(),
				requester.getYRot(),
				requester.getXRot(),
				70,
				requester.getUUID(),
				clampedTargetFps,
				completionFuture
		);
		PENDING_VIDEO_RECORDINGS.put(requestId, pending);
		completionFuture.orTimeout(Math.max(timeoutMillis, maxDurationSeconds * 1_000L + 30_000L), TimeUnit.MILLISECONDS)
				.exceptionally(throwable -> {
					if (PENDING_VIDEO_RECORDINGS.remove(requestId, pending)) {
						releaseBotCameraIfNeeded(pending.server(), pending.botUuid(), pending.resetCameraOnFinish());
						completionFuture.completeExceptionally(throwable);
					}
					return null;
				});

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
						requester.getUUID(),
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
		recording.markStopRequested();
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

	public static List<ServerPlayer> activeBotsRenderingLevel(ServerLevel level) {
		if (level == null) {
			return List.of();
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return List.of();
		}
		List<ServerPlayer> recipients = new ArrayList<>();
		for (UUID botUuid : READY_BOTS.keySet()) {
			ServerPlayer bot = server.getPlayerList().getPlayer(botUuid);
			if (bot == null || !RendererBotPresenceSystem.isRendererBot(bot)) {
				continue;
			}
			if (isLevelActivelyRenderedByBot(server, botUuid, level.dimension())) {
				recipients.add(bot);
			}
		}
		return recipients;
	}

	public static boolean isCameraPlayerLoaded(ServerLevel level, BlockPos cameraPos) {
		if (level == null || cameraPos == null) {
			return false;
		}
		if (!level.hasChunkAt(cameraPos)) {
			return false;
		}
		return level.getBlockState(cameraPos).is(ModBlocks.CAMERA);
	}

	public static ChunkTrackingView createVirtualChunkTrackingView(ServerPlayer bot) {
		if (bot == null) {
			return ChunkTrackingView.EMPTY;
		}
		if (!RendererBotPresenceSystem.isRendererBot(bot)) {
			ChunkPos realCenter = bot.chunkPosition();
			return ChunkTrackingView.of(realCenter, resolveViewDistance(bot));
		}
		LongSet virtualChunks = collectVirtualTrackedChunks(bot, resolveShadowViewDistance(bot));
		return virtualChunks.isEmpty() ? ChunkTrackingView.EMPTY : new VirtualChunkTrackingView(virtualChunks);
	}

	public static boolean isEntityWithinAnyVirtualTrackingRange(ServerPlayer viewer, Entity entity, double horizontalRangeBlocks) {
		if (viewer == null
				|| entity == null
				|| horizontalRangeBlocks <= 0.0D
				|| !RendererBotPresenceSystem.isRendererBot(viewer)) {
			return false;
		}
		if (viewer.level() != entity.level()) {
			return false;
		}
		ServerLevel viewerLevel = viewer.level();
		MinecraftServer server = viewerLevel != null ? viewerLevel.getServer() : null;
		if (server == null) {
			return false;
		}
		return isEntityWithinAnyTrackingTarget(server, viewer.getUUID(), entity, horizontalRangeBlocks * horizontalRangeBlocks);
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

	private static boolean isLevelActivelyRenderedByBot(MinecraftServer server, UUID botUuid, ResourceKey<Level> dimension) {
		if (server == null || botUuid == null || dimension == null) {
			return false;
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream != null
					&& botUuid.equals(stream.botUuid())
					&& dimension.equals(stream.spec().dimension())) {
				return true;
			}
		}
		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture != null
					&& botUuid.equals(capture.botUuid())
					&& !capture.isDone()
					&& dimension.equals(capture.dimension())) {
				return true;
			}
		}
		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording != null
					&& botUuid.equals(recording.botUuid())
					&& !recording.stopRequested()
					&& !recording.completionFuture().isDone()
					&& dimension.equals(recording.dimension())) {
				return true;
			}
		}
		return false;
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
		releaseBotCameraIfNeeded(stream.server(), stream.botUuid(), true);
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

	private static void anchorBotIfNeeded(
			ServerPlayer bot,
			ServerLevel level,
			double x,
			double y,
			double z,
			float yaw,
			float pitch
	) {
		if (bot == null || level == null || botHasActiveJobs(bot.getUUID())) {
			return;
		}
		prepareBotForStaticView(bot, level, x, y, z, yaw, pitch);
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
		if (botHasActiveJobs(botUuid)) {
			return;
		}
		ServerPlayer bot = server.getPlayerList().getPlayer(botUuid);
		if (bot != null && bot.getCamera() != bot) {
			bot.setCamera(bot);
		}
	}

	private static void refreshVirtualEntityTracking(ServerPlayer bot) {
		if (bot == null || !(bot.level() instanceof ServerLevel level)) {
			return;
		}
		level.getChunkSource().move(bot);
	}

	private static void tickVirtualCameraState(MinecraftServer server) {
		if (server == null) {
			return;
		}
		if (!hasActiveShadowSyncWork()) {
			return;
		}
		syncShadowWorlds(server);
	}

	private static boolean hasActiveShadowSyncWork() {
		return !ACTIVE_LIVE_STREAMS.isEmpty()
				|| !PENDING_CAPTURES.isEmpty()
				|| !PENDING_VIDEO_RECORDINGS.isEmpty()
				|| !ACTIVE_SHADOW_SYNC_STATES.isEmpty()
				|| !ACTIVE_CAMERA_CHUNK_TICKETS.isEmpty()
				|| !DIRTY_SHADOW_CHUNKS.isEmpty();
	}

	private static int resolveViewDistance(ServerPlayer bot) {
		MinecraftServer server = bot != null && bot.level() != null ? bot.level().getServer() : null;
		return Mth.clamp(
				bot != null ? bot.requestedViewDistance() : 2,
				2,
				Math.max(2, server != null ? server.getPlayerList().getViewDistance() : 2)
		);
	}

	private static int resolveShadowViewDistance(ServerPlayer bot) {
		return Mth.clamp(resolveViewDistance(bot) + SHADOW_VIEW_DISTANCE_MARGIN_CHUNKS, 2, 32);
	}

	private static boolean canBotRenderLevel(ServerPlayer bot, ServerLevel level) {
		return bot != null && level != null && READY_BOTS.containsKey(bot.getUUID());
	}

	private static LongSet collectVirtualTrackedChunks(ServerPlayer bot, int viewDistance) {
		LongOpenHashSet chunks = new LongOpenHashSet();
		if (bot == null || !(bot.level() instanceof ServerLevel botLevel)) {
			return chunks;
		}
		MinecraftServer server = botLevel.getServer();
		if (server == null) {
			return chunks;
		}
		UUID botUuid = bot.getUUID();

		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !botUuid.equals(stream.botUuid())) {
				continue;
			}
			LiveStreamSpec spec = stream.spec();
			if (spec == null || spec.dimension() == null || !botLevel.dimension().equals(spec.dimension())) {
				continue;
			}
			if (spec.cameraPos() != null && !isCameraPlayerLoaded(botLevel, spec.cameraPos())) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(
					server,
					spec.dimension(),
					spec.expectedX(),
					spec.expectedY(),
					spec.expectedZ(),
					spec.expectedYaw(),
					spec.expectedPitch(),
					spec.followEntityUuid()
			);
			appendVirtualTargetChunks(chunks, botLevel, target, viewDistance);
		}
		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture == null || !botUuid.equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, capture.dimension(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), capture.followEntityUuid());
			if (target == null || target.level() != botLevel) {
				continue;
			}
			appendVirtualTargetChunks(chunks, botLevel, target, viewDistance);
		}
		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording == null || !botUuid.equals(recording.botUuid()) || recording.stopRequested() || recording.completionFuture().isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, recording.dimension(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), recording.followEntityUuid());
			if (target == null || target.level() != botLevel) {
				continue;
			}
			appendVirtualTargetChunks(chunks, botLevel, target, viewDistance);
		}
		return chunks;
	}

	private static void appendVirtualTargetChunks(LongSet chunks, ServerLevel level, ScheduledServiceTarget target, int viewDistance) {
		if (chunks == null || level == null || target == null || target.level() != level) {
			return;
		}
		appendVirtualCameraChunks(chunks, target.x(), target.z(), target.yaw(), viewDistance);
	}

	private static void appendVirtualCameraChunks(LongSet target, double x, double z, float yaw, int viewDistance) {
		if (target == null || viewDistance <= 0) {
			return;
		}
		LongSet chunks = computeVirtualCameraChunks(x, z, yaw, viewDistance);
		LongIterator iterator = chunks.iterator();
		while (iterator.hasNext()) {
			target.add(iterator.nextLong());
		}
	}

	private static LongSet computeVirtualCameraChunks(double x, double z, float yaw, int viewDistance) {
		LongOpenHashSet chunks = new LongOpenHashSet();
		int centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(x));
		int centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(z));
		double yawRadians = Math.toRadians(yaw);
		double forwardX = -Math.sin(yawRadians);
		double forwardZ = Math.cos(yawRadians);
		double halfFovRadians = Math.toRadians(SHADOW_FORWARD_HALF_FOV_DEGREES);
		double tangentLimit = Math.tan(halfFovRadians);
		for (int dx = -viewDistance; dx <= viewDistance; dx++) {
			for (int dz = -viewDistance; dz <= viewDistance; dz++) {
				int chunkX = centerChunkX + dx;
				int chunkZ = centerChunkZ + dz;
				if (!ChunkTrackingView.isInViewDistance(centerChunkX, centerChunkZ, viewDistance, chunkX, chunkZ)) {
					continue;
				}
				double chunkCenterX = chunkX * 16.0D + 8.0D;
				double chunkCenterZ = chunkZ * 16.0D + 8.0D;
				double deltaChunkX = (chunkCenterX - x) / 16.0D;
				double deltaChunkZ = (chunkCenterZ - z) / 16.0D;
				double horizontalDistanceChunks = Math.sqrt(deltaChunkX * deltaChunkX + deltaChunkZ * deltaChunkZ);
				if (horizontalDistanceChunks <= SHADOW_NEAR_OMNI_RADIUS_CHUNKS) {
					chunks.add(new ChunkPos(chunkX, chunkZ).toLong());
					continue;
				}
				double forwardDistance = deltaChunkX * forwardX + deltaChunkZ * forwardZ;
				if (forwardDistance < -SHADOW_REAR_VIEW_CHUNKS || forwardDistance > viewDistance + SHADOW_SIDE_SAFETY_MARGIN_CHUNKS) {
					continue;
				}
				double sideDistance = Math.abs(deltaChunkX * forwardZ - deltaChunkZ * forwardX);
				double allowedSideDistance = forwardDistance <= 0.0D
						? SHADOW_NEAR_OMNI_RADIUS_CHUNKS + SHADOW_SIDE_SAFETY_MARGIN_CHUNKS
						: forwardDistance * tangentLimit + SHADOW_SIDE_SAFETY_MARGIN_CHUNKS;
				if (sideDistance > allowedSideDistance) {
					continue;
				}
				chunks.add(new ChunkPos(chunkX, chunkZ).toLong());
			}
		}
		return chunks;
	}

	private static boolean updateVirtualCameraChunkTickets(MinecraftServer server) {
		Map<ChunkTicketKey, Integer> desiredRefs = new HashMap<>();
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null) {
				continue;
			}
			LiveStreamSpec spec = stream.spec();
			if (spec == null || spec.cameraPos() == null || spec.dimension() == null) {
				continue;
			}
			ServerLevel level = server.getLevel(spec.dimension());
			if (level == null || !isCameraPlayerLoaded(level, spec.cameraPos())) {
				continue;
			}
			ServerPlayer bot = server.getPlayerList().getPlayer(stream.botUuid());
			if (bot == null || !READY_BOTS.containsKey(bot.getUUID())) {
				continue;
			}
			int viewDistance = resolveShadowViewDistance(bot);
			LongSet chunks = computeVirtualCameraChunks(spec.expectedX(), spec.expectedZ(), spec.expectedYaw(), viewDistance);
			LongIterator iterator = chunks.iterator();
			while (iterator.hasNext()) {
				long packed = iterator.nextLong();
				desiredRefs.merge(new ChunkTicketKey(spec.dimension(), packed), 1, Integer::sum);
			}
		}

		if (Objects.equals(ACTIVE_CAMERA_CHUNK_TICKETS, desiredRefs)) {
			return false;
		}

		Set<ChunkTicketKey> keys = new LinkedHashSet<>();
		keys.addAll(ACTIVE_CAMERA_CHUNK_TICKETS.keySet());
		keys.addAll(desiredRefs.keySet());
		for (ChunkTicketKey key : keys) {
			int current = ACTIVE_CAMERA_CHUNK_TICKETS.getOrDefault(key, 0);
			int desired = desiredRefs.getOrDefault(key, 0);
			if (current <= 0 && desired > 0) {
				ServerLevel level = server.getLevel(key.dimension());
				if (level != null) {
					level.getChunkSource().addTicketWithRadius(TicketType.UNKNOWN, new ChunkPos(key.chunkLong()), 0);
				}
			} else if (current > 0 && desired <= 0) {
				ServerLevel level = server.getLevel(key.dimension());
				if (level != null) {
					level.getChunkSource().removeTicketWithRadius(TicketType.UNKNOWN, new ChunkPos(key.chunkLong()), 0);
				}
			}
		}
		ACTIVE_CAMERA_CHUNK_TICKETS.clear();
		ACTIVE_CAMERA_CHUNK_TICKETS.putAll(desiredRefs);
		return true;
	}

	private static void releaseAllVirtualCameraChunkTickets(MinecraftServer server) {
		if (server == null || ACTIVE_CAMERA_CHUNK_TICKETS.isEmpty()) {
			ACTIVE_CAMERA_CHUNK_TICKETS.clear();
			return;
		}
		for (ChunkTicketKey key : new ArrayList<>(ACTIVE_CAMERA_CHUNK_TICKETS.keySet())) {
			ServerLevel level = server.getLevel(key.dimension());
			if (level != null) {
				level.getChunkSource().removeTicketWithRadius(TicketType.UNKNOWN, new ChunkPos(key.chunkLong()), 0);
			}
		}
		ACTIVE_CAMERA_CHUNK_TICKETS.clear();
	}

	public static void markShadowChunkDirty(ServerLevel level, ChunkPos pos) {
		if (level == null || pos == null) {
			return;
		}
		DIRTY_SHADOW_CHUNKS.add(new ChunkTicketKey(level.dimension(), pos.toLong()));
	}

	private static void syncShadowWorlds(MinecraftServer server) {
		if (server == null) {
			return;
		}
		Map<ShadowSyncKey, ShadowDesiredState> desiredStates = collectDesiredShadowStates(server);
		syncShadowChunkTickets(server, desiredStates.values());
		Set<ChunkTicketKey> consumedDirtyChunks = new HashSet<>();
		for (Map.Entry<ShadowSyncKey, ShadowDesiredState> entry : desiredStates.entrySet()) {
			ShadowSyncKey key = entry.getKey();
			ServerPlayer bot = server.getPlayerList().getPlayer(key.botUuid());
			if (bot == null || !READY_BOTS.containsKey(bot.getUUID())) {
				continue;
			}
			syncShadowState(server, bot, key, entry.getValue(), consumedDirtyChunks);
		}

		Set<ShadowSyncKey> staleKeys = new LinkedHashSet<>(ACTIVE_SHADOW_SYNC_STATES.keySet());
		staleKeys.removeAll(desiredStates.keySet());
		for (ShadowSyncKey staleKey : staleKeys) {
			removeShadowSyncState(server, staleKey, true);
		}
		DIRTY_SHADOW_CHUNKS.removeAll(consumedDirtyChunks);
	}

	private static void syncShadowChunkTickets(MinecraftServer server, Iterable<ShadowDesiredState> desiredStates) {
		Map<ChunkTicketKey, Integer> desiredRefs = new HashMap<>();
		if (server == null) {
			ACTIVE_CAMERA_CHUNK_TICKETS.clear();
			return;
		}
		if (desiredStates != null) {
			for (ShadowDesiredState desiredState : desiredStates) {
				if (desiredState == null || desiredState.level() == null) {
					continue;
				}
				LongIterator iterator = desiredState.trackedChunks().iterator();
				while (iterator.hasNext()) {
					long chunkLong = iterator.nextLong();
					desiredRefs.merge(new ChunkTicketKey(desiredState.level().dimension(), chunkLong), 1, Integer::sum);
				}
			}
		}
		if (Objects.equals(ACTIVE_CAMERA_CHUNK_TICKETS, desiredRefs)) {
			return;
		}
		Set<ChunkTicketKey> keys = new LinkedHashSet<>();
		keys.addAll(ACTIVE_CAMERA_CHUNK_TICKETS.keySet());
		keys.addAll(desiredRefs.keySet());
		for (ChunkTicketKey key : keys) {
			int current = ACTIVE_CAMERA_CHUNK_TICKETS.getOrDefault(key, 0);
			int desired = desiredRefs.getOrDefault(key, 0);
			ServerLevel level = server.getLevel(key.dimension());
			if (level == null) {
				continue;
			}
			if (current <= 0 && desired > 0) {
				level.getChunkSource().addTicketWithRadius(TicketType.UNKNOWN, new ChunkPos(key.chunkLong()), 0);
			} else if (current > 0 && desired <= 0) {
				level.getChunkSource().removeTicketWithRadius(TicketType.UNKNOWN, new ChunkPos(key.chunkLong()), 0);
			}
		}
		ACTIVE_CAMERA_CHUNK_TICKETS.clear();
		ACTIVE_CAMERA_CHUNK_TICKETS.putAll(desiredRefs);
	}

	private static Map<ShadowSyncKey, ShadowDesiredState> collectDesiredShadowStates(MinecraftServer server) {
		Map<ShadowSyncKey, ShadowDesiredState> desiredStates = new HashMap<>();
		ServerPlayer bot = selectBot(server);
		if (server == null || bot == null) {
			return desiredStates;
		}
		UUID botUuid = bot.getUUID();
		int viewDistance = resolveShadowViewDistance(bot);

		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !botUuid.equals(stream.botUuid())) {
				continue;
			}
			LiveStreamSpec spec = stream.spec();
			ScheduledServiceTarget target = resolveServiceTarget(
					server,
					spec.dimension(),
					spec.expectedX(),
					spec.expectedY(),
					spec.expectedZ(),
					spec.expectedYaw(),
					spec.expectedPitch(),
					spec.followEntityUuid()
			);
			if (target == null || target.level() == null) {
				stopLiveStreamInternal(stream, "Renderer bot live stream target is unavailable", true);
				continue;
			}
			if (spec.cameraPos() != null && !isCameraPlayerLoaded(target.level(), spec.cameraPos())) {
				continue;
			}
			accumulateShadowDesiredState(desiredStates, botUuid, stream.streamId(), target, viewDistance);
		}

		for (Map.Entry<UUID, PendingCapture> entry : PENDING_CAPTURES.entrySet()) {
			PendingCapture capture = entry.getValue();
			if (capture == null || !botUuid.equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, capture.dimension(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), capture.followEntityUuid());
			if (target == null || target.level() == null) {
				failCapture(entry.getKey(), capture, "Renderer bot capture target is unavailable");
				continue;
			}
			accumulateShadowDesiredState(desiredStates, botUuid, capture.requestId(), target, viewDistance);
		}

		for (Map.Entry<UUID, PendingVideoRecording> entry : PENDING_VIDEO_RECORDINGS.entrySet()) {
			PendingVideoRecording recording = entry.getValue();
			if (recording == null || !botUuid.equals(recording.botUuid()) || recording.stopRequested() || recording.completionFuture().isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, recording.dimension(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), recording.followEntityUuid());
			if (target == null || target.level() == null) {
				failVideoRecording(entry.getKey(), recording, "Renderer bot recording target is unavailable");
				continue;
			}
			accumulateShadowDesiredState(desiredStates, botUuid, recording.requestId(), target, viewDistance);
		}
		return desiredStates;
	}

	private static void accumulateShadowDesiredState(
			Map<ShadowSyncKey, ShadowDesiredState> desiredStates,
			UUID botUuid,
			UUID sessionId,
			ScheduledServiceTarget target,
			int viewDistance
	) {
		if (desiredStates == null || botUuid == null || sessionId == null || target == null || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		ShadowSyncKey key = new ShadowSyncKey(botUuid, sessionId);
		ShadowDesiredState desiredState = new ShadowDesiredState(sessionId, level, viewDistance, target);
		appendVirtualTargetChunks(desiredState.trackedChunks(), level, target, viewDistance);
		desiredStates.put(key, desiredState);
	}

	private static void syncShadowState(
			MinecraftServer server,
			ServerPlayer bot,
			ShadowSyncKey key,
			ShadowDesiredState desiredState,
			Set<ChunkTicketKey> consumedDirtyChunks
	) {
		if (server == null || bot == null || key == null || desiredState == null || desiredState.level() == null) {
			return;
		}
		if (!ServerPlayNetworking.canSend(bot, RendererBotPayloads.RendererBotShadowLevelInitS2CPayload.TYPE)) {
			return;
		}
		ShadowDimensionSyncState activeState = ACTIVE_SHADOW_SYNC_STATES.computeIfAbsent(
				key,
				ignored -> new ShadowDimensionSyncState(key.botUuid(), key.sessionId(), desiredState.level().dimension())
		);
		syncShadowLevelInit(bot, desiredState, activeState);
		syncShadowView(bot, desiredState, activeState);
		syncShadowLevelState(bot, desiredState.level(), activeState);
		syncShadowChunks(bot, desiredState, activeState, consumedDirtyChunks);
		syncShadowEntities(bot, desiredState, activeState);
	}

	private static void syncShadowLevelInit(ServerPlayer bot, ShadowDesiredState desiredState, ShadowDimensionSyncState activeState) {
		ServerLevel level = desiredState.level();
		String dimensionTypeId = level.dimensionTypeRegistration()
				.unwrapKey()
				.orElseThrow()
				.identifier()
				.toString();
		if (activeState.initialized()
				&& Objects.equals(activeState.dimensionTypeId(), dimensionTypeId)
				&& activeState.seed() == level.getSeed()
				&& activeState.viewDistance() == desiredState.viewDistance()) {
			return;
		}
		activeState.setInitialized(true);
		activeState.setDimensionTypeId(dimensionTypeId);
		activeState.setSeed(level.getSeed());
		activeState.setViewDistance(desiredState.viewDistance());
		activeState.trackedChunks().clear();
		activeState.trackedEntities().clear();
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotShadowLevelInitS2CPayload(
						desiredState.sessionId(),
						level.dimension().identifier().toString(),
						dimensionTypeId,
						level.getSeed(),
						level.isDebug(),
						level.isFlat(),
						level.getServer().getWorldData().isHardcore(),
						level.getDifficulty().ordinal(),
						level.getGameTime(),
						level.getDayTime(),
						level.getGameRules().get(GameRules.ADVANCE_TIME),
						level.isRaining(),
						level.getRainLevel(1.0F),
						level.getThunderLevel(1.0F),
						level.getSeaLevel(),
						desiredState.viewDistance(),
						Math.max(2, level.getServer().getPlayerList().getSimulationDistance())
				)
		);
	}

	private static void syncShadowView(ServerPlayer bot, ShadowDesiredState desiredState, ShadowDimensionSyncState activeState) {
		if (bot == null || desiredState == null || activeState == null) {
			return;
		}
		int centerChunkX = desiredState.centerChunkX();
		int centerChunkZ = desiredState.centerChunkZ();
		if (activeState.lastCenterChunkX() == centerChunkX
				&& activeState.lastCenterChunkZ() == centerChunkZ
				&& activeState.viewDistance() == desiredState.viewDistance()) {
			return;
		}
		activeState.setLastCenterChunkX(centerChunkX);
		activeState.setLastCenterChunkZ(centerChunkZ);
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotShadowViewS2CPayload(
						desiredState.sessionId(),
						centerChunkX,
						centerChunkZ,
						desiredState.viewDistance()
				)
		);
	}

	private static void syncShadowLevelState(ServerPlayer bot, ServerLevel level, ShadowDimensionSyncState activeState) {
		long gameTime = level.getGameTime();
		long dayTime = level.getDayTime();
		boolean tickDayTime = level.getGameRules().get(GameRules.ADVANCE_TIME);
		boolean raining = level.isRaining();
		float rainLevel = level.getRainLevel(1.0F);
		float thunderLevel = level.getThunderLevel(1.0F);
		if (activeState.lastGameTime() == gameTime
				&& activeState.lastDayTime() == dayTime
				&& activeState.lastTickDayTime() == tickDayTime
				&& activeState.lastRaining() == raining
				&& Float.compare(activeState.lastRainLevel(), rainLevel) == 0
				&& Float.compare(activeState.lastThunderLevel(), thunderLevel) == 0) {
			return;
		}
		activeState.setLastGameTime(gameTime);
		activeState.setLastDayTime(dayTime);
		activeState.setLastTickDayTime(tickDayTime);
		activeState.setLastRaining(raining);
		activeState.setLastRainLevel(rainLevel);
		activeState.setLastThunderLevel(thunderLevel);
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotShadowLevelStateS2CPayload(
						activeState.sessionId(),
						level.dimension().identifier().toString(),
						gameTime,
						dayTime,
						tickDayTime,
						raining,
						rainLevel,
						thunderLevel
				)
		);
	}

	private static void syncShadowChunks(
			ServerPlayer bot,
			ShadowDesiredState desiredState,
			ShadowDimensionSyncState activeState,
			Set<ChunkTicketKey> consumedDirtyChunks
	) {
		ServerLevel level = desiredState.level();
		LongSet previousChunks = new LongOpenHashSet(activeState.trackedChunks());
		LongSet newTrackedChunks = new LongOpenHashSet();

		LongIterator desiredIterator = desiredState.trackedChunks().iterator();
		while (desiredIterator.hasNext()) {
			long chunkLong = desiredIterator.nextLong();
			ChunkPos pos = new ChunkPos(chunkLong);
			ChunkTicketKey dirtyKey = new ChunkTicketKey(level.dimension(), chunkLong);
			boolean alreadyTracked = previousChunks.contains(chunkLong);
			boolean dirty = DIRTY_SHADOW_CHUNKS.contains(dirtyKey);
			previousChunks.remove(chunkLong);
			net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
			if (chunk == null) {
				if (alreadyTracked) {
					newTrackedChunks.add(chunkLong);
				}
				continue;
			}
			if (!alreadyTracked || dirty) {
				ClientboundLevelChunkWithLightPacket packet = PacketContext.supplyWithContext(
						bot.connection,
						() -> new ClientboundLevelChunkWithLightPacket(chunk, level.getChunkSource().getLightEngine(), null, null)
				);
				ServerPlayNetworking.send(
						bot,
						new RendererBotPayloads.RendererBotShadowChunkDataS2CPayload(
								desiredState.sessionId(),
								level.dimension().identifier().toString(),
								RendererBotShadowPacketCodec.encodeChunkPacket(level.registryAccess(), bot.connection, packet)
						)
				);
				if (dirty) {
					consumedDirtyChunks.add(dirtyKey);
				}
			}
			newTrackedChunks.add(chunkLong);
		}

		LongIterator removedIterator = previousChunks.iterator();
		while (removedIterator.hasNext()) {
			ChunkPos removedPos = new ChunkPos(removedIterator.nextLong());
			ServerPlayNetworking.send(
					bot,
					new RendererBotPayloads.RendererBotShadowForgetChunkS2CPayload(
							desiredState.sessionId(),
							level.dimension().identifier().toString(),
							removedPos.x,
							removedPos.z
					)
			);
		}

		activeState.trackedChunks().clear();
		activeState.trackedChunks().addAll(newTrackedChunks);
	}

	private static void syncShadowEntities(ServerPlayer bot, ShadowDesiredState desiredState, ShadowDimensionSyncState activeState) {
		ServerLevel level = desiredState.level();
		Map<Integer, ShadowTrackedEntity> trackedEntities = activeState.trackedEntities();
		Set<Integer> desiredEntityIds = new HashSet<>();
		List<Packet<? extends ClientGamePacketListener>> packets = new ArrayList<>();
		AABB searchBox = shadowEntitySearchBox(desiredState);

		PacketContext.runWithContext(bot.connection, () -> {
			for (Entity entity : level.getEntities((Entity) null, searchBox, entity -> true)) {
				if (!shouldShadowTrackEntity(entity, desiredState)) {
					continue;
				}
				desiredEntityIds.add(entity.getId());
				ShadowTrackedEntity trackedEntity = trackedEntities.get(entity.getId());
				if (trackedEntity == null || trackedEntity.entity() != entity) {
					trackedEntity = createShadowTrackedEntity(level, entity, bot);
					trackedEntities.put(entity.getId(), trackedEntity);
					trackedEntity.serverEntity().sendPairingData(bot, packets::add);
				}
				trackedEntity.collector().clear();
				trackedEntity.serverEntity().sendChanges();
				packets.addAll(trackedEntity.collector().drain());
			}
		});

		if (!trackedEntities.isEmpty()) {
			List<Integer> removals = new ArrayList<>();
			for (Integer entityId : new ArrayList<>(trackedEntities.keySet())) {
				if (desiredEntityIds.contains(entityId)) {
					continue;
				}
				removals.add(entityId);
				trackedEntities.remove(entityId);
			}
			if (!removals.isEmpty()) {
				int[] removalIds = removals.stream().mapToInt(Integer::intValue).toArray();
				packets.add(new ClientboundRemoveEntitiesPacket(removalIds));
			}
		}

		if (packets.isEmpty()) {
			return;
		}
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotShadowEntityPacketsS2CPayload(
						desiredState.sessionId(),
						level.dimension().identifier().toString(),
						RendererBotShadowPacketCodec.encodePacketList(level.registryAccess(), bot.connection, packets)
				)
		);
	}

	private static AABB shadowEntitySearchBox(ShadowDesiredState desiredState) {
		ScheduledServiceTarget target = desiredState != null ? desiredState.target() : null;
		ServerLevel level = desiredState != null ? desiredState.level() : null;
		if (target == null || level == null) {
			return new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
		}
		double radius = desiredState.viewDistance() * 16.0D + 32.0D;
		return new AABB(
				target.x() - radius,
				level.getMinY(),
				target.z() - radius,
				target.x() + radius,
				level.getMaxY() + 1.0D,
				target.z() + radius
		);
	}

	public static void mirrorTransientEntityPacket(Entity entity, Packet<? super ClientGamePacketListener> packet) {
		if (!(entity != null && entity.level() instanceof ServerLevel level) || !isShadowTransientEntityPacket(packet)) {
			return;
		}
		@SuppressWarnings("unchecked")
		Packet<? extends ClientGamePacketListener> clientPacket = (Packet<? extends ClientGamePacketListener>) packet;
		for (ShadowDimensionSyncState activeState : ACTIVE_SHADOW_SYNC_STATES.values()) {
			if (activeState == null
					|| !activeState.initialized()
					|| !activeState.dimension().equals(level.dimension())
					|| !activeState.trackedEntities().containsKey(entity.getId())) {
				continue;
			}
			sendShadowTransientPacket(level, activeState, clientPacket);
		}
	}

	public static void mirrorTransientBlockEvent(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block block, int paramA, int paramB) {
		if (level == null || pos == null || block == null) {
			return;
		}
		mirrorTransientLevelPacket(level, pos, new ClientboundBlockEventPacket(pos, block, paramA, paramB));
	}

	public static void mirrorTransientBlockDestruction(ServerLevel level, int breakerId, BlockPos pos, int progress) {
		if (level == null || pos == null) {
			return;
		}
		mirrorTransientLevelPacket(level, pos, new ClientboundBlockDestructionPacket(breakerId, pos, progress));
	}

	public static void mirrorTransientLevelEvent(ServerLevel level, int type, BlockPos pos, int data, boolean globalEvent) {
		if (level == null || pos == null) {
			return;
		}
		mirrorTransientLevelPacket(level, pos, new ClientboundLevelEventPacket(type, pos, data, globalEvent));
	}

	public static void mirrorTransientParticles(
			ServerLevel level,
			net.minecraft.core.particles.ParticleOptions particle,
			boolean overrideLimiter,
			boolean alwaysShow,
			double x,
			double y,
			double z,
			int count,
			double xDist,
			double yDist,
			double zDist,
			double maxSpeed
	) {
		if (level == null || particle == null) {
			return;
		}
		ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
				particle,
				overrideLimiter,
				alwaysShow,
				x,
				y,
				z,
				(float) xDist,
				(float) yDist,
				(float) zDist,
				(float) maxSpeed,
				count
		);
		mirrorTransientLevelPacket(level, BlockPos.containing(x, y, z), packet);
	}

	private static void mirrorTransientLevelPacket(ServerLevel level, BlockPos pos, Packet<? extends ClientGamePacketListener> packet) {
		if (level == null || pos == null || packet == null) {
			return;
		}
		long chunkLong = new ChunkPos(pos).toLong();
		for (ShadowDimensionSyncState activeState : ACTIVE_SHADOW_SYNC_STATES.values()) {
			if (activeState == null
					|| !activeState.initialized()
					|| !activeState.dimension().equals(level.dimension())
					|| !activeState.trackedChunks().contains(chunkLong)) {
				continue;
			}
			sendShadowTransientPacket(level, activeState, packet);
		}
	}

	private static void sendShadowTransientPacket(
			ServerLevel level,
			ShadowDimensionSyncState activeState,
			Packet<? extends ClientGamePacketListener> packet
	) {
		if (level == null || activeState == null || packet == null) {
			return;
		}
		ServerPlayer bot = level.getServer().getPlayerList().getPlayer(activeState.botUuid());
		if (bot == null
				|| bot.connection == null
				|| !READY_BOTS.containsKey(bot.getUUID())
				|| !ServerPlayNetworking.canSend(bot, RendererBotPayloads.RendererBotShadowEntityPacketsS2CPayload.TYPE)) {
			return;
		}
		RendererBotPayloads.ShadowPacketData encoded = RendererBotShadowPacketCodec.encodePacket(level.registryAccess(), bot.connection, packet);
		if (encoded == null) {
			return;
		}
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotShadowEntityPacketsS2CPayload(
						activeState.sessionId(),
						level.dimension().identifier().toString(),
						List.of(encoded)
				)
		);
	}

	private static boolean isShadowTransientEntityPacket(Packet<?> packet) {
		return packet instanceof ClientboundAnimatePacket
				|| packet instanceof ClientboundEntityEventPacket
				|| packet instanceof ClientboundHurtAnimationPacket
				|| packet instanceof ClientboundDamageEventPacket
				|| packet instanceof ClientboundSetEntityDataPacket
				|| packet instanceof ClientboundSetEquipmentPacket
				|| packet instanceof ClientboundUpdateAttributesPacket
				|| packet instanceof ClientboundSetPassengersPacket
				|| packet instanceof ClientboundSetEntityLinkPacket;
	}

	private static boolean shouldShadowTrackEntity(Entity entity, ShadowDesiredState desiredState) {
		if (entity == null
				|| desiredState == null
				|| desiredState.level() == null
				|| entity.isRemoved()
				|| entity.level() != desiredState.level()
				|| !desiredState.trackedChunks().contains(entity.chunkPosition().toLong())) {
			return false;
		}
		if (entity instanceof ServerPlayer player && RendererBotPresenceSystem.isRendererBot(player)) {
			return false;
		}
		double entityRangeBlocks = Math.max(16.0D, entity.getType().clientTrackingRange() * 16.0D);
		double entityRangeSq = entityRangeBlocks * entityRangeBlocks;
		ScheduledServiceTarget target = desiredState.target();
		if (target == null || target.level() != desiredState.level()) {
			return false;
		}
		double dx = entity.getX() - target.x();
		double dz = entity.getZ() - target.z();
		return dx * dx + dz * dz <= entityRangeSq;
	}

	private static ShadowTrackedEntity createShadowTrackedEntity(ServerLevel level, Entity entity, ServerPlayer bot) {
		ShadowPacketCollector collector = new ShadowPacketCollector(bot);
		return new ShadowTrackedEntity(
				entity,
				new net.minecraft.server.level.ServerEntity(
						level,
						entity,
						1,
						true,
						collector
				),
				collector
		);
	}

	private static void clearAllShadowSyncStates(MinecraftServer server, boolean notifyClient) {
		for (ShadowSyncKey key : new ArrayList<>(ACTIVE_SHADOW_SYNC_STATES.keySet())) {
			removeShadowSyncState(server, key, notifyClient);
		}
		DIRTY_SHADOW_CHUNKS.clear();
	}

	private static void clearShadowSyncState(MinecraftServer server, UUID botUuid, boolean notifyClient) {
		if (botUuid == null) {
			return;
		}
		for (ShadowSyncKey key : new ArrayList<>(ACTIVE_SHADOW_SYNC_STATES.keySet())) {
			if (!botUuid.equals(key.botUuid())) {
				continue;
			}
			removeShadowSyncState(server, key, notifyClient);
		}
	}

	private static void removeShadowSyncState(MinecraftServer server, ShadowSyncKey key, boolean notifyClient) {
		ShadowDimensionSyncState removed = ACTIVE_SHADOW_SYNC_STATES.remove(key);
		if (removed == null || !notifyClient || server == null) {
			return;
		}
		ServerPlayer bot = server.getPlayerList().getPlayer(key.botUuid());
		if (bot == null || !ServerPlayNetworking.canSend(bot, RendererBotPayloads.RendererBotShadowLevelDestroyS2CPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(
				bot,
				new RendererBotPayloads.RendererBotShadowLevelDestroyS2CPayload(key.sessionId())
		);
	}

	private static void tickBotJobs(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ServerPlayer bot = selectBot(server);
		if (bot == null) {
			return;
		}
		long now = System.currentTimeMillis();
		ScheduledServiceTarget selectedTarget = null;
		long selectedDueAt = Long.MAX_VALUE;
		int selectedPriority = Integer.MAX_VALUE;

		for (Map.Entry<UUID, PendingCapture> entry : PENDING_CAPTURES.entrySet()) {
			PendingCapture capture = entry.getValue();
			if (capture == null || !bot.getUUID().equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, capture.dimension(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), capture.followEntityUuid());
			if (target == null) {
				failCapture(entry.getKey(), capture, "Renderer bot capture target is unavailable");
				continue;
			}
			long dueAt = capture.nextDueAtMillis();
			if (isBetterCandidate(dueAt, 0, selectedDueAt, selectedPriority)) {
				selectedTarget = target;
				selectedDueAt = dueAt;
				selectedPriority = 0;
			}
		}

		for (Map.Entry<UUID, PendingVideoRecording> entry : PENDING_VIDEO_RECORDINGS.entrySet()) {
			PendingVideoRecording recording = entry.getValue();
			if (recording == null || !bot.getUUID().equals(recording.botUuid()) || recording.completionFuture().isDone() || recording.stopRequested()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, recording.dimension(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), recording.followEntityUuid());
			if (target == null) {
				failVideoRecording(entry.getKey(), recording, "Renderer bot recording target is unavailable");
				continue;
			}
			long dueAt = recording.nextDueAtMillis();
			if (isBetterCandidate(dueAt, 1, selectedDueAt, selectedPriority)) {
				selectedTarget = target;
				selectedDueAt = dueAt;
				selectedPriority = 1;
			}
		}

		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !bot.getUUID().equals(stream.botUuid())) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(
					server,
					stream.spec().dimension(),
					stream.spec().expectedX(),
					stream.spec().expectedY(),
					stream.spec().expectedZ(),
					stream.spec().expectedYaw(),
					stream.spec().expectedPitch(),
					stream.spec().followEntityUuid()
			);
			if (target == null) {
				stopLiveStreamInternal(stream, "Renderer bot live stream target is unavailable", true);
				continue;
			}
			long dueAt = stream.nextDueAtMillis();
			if (isBetterCandidate(dueAt, 2, selectedDueAt, selectedPriority)) {
				selectedTarget = target;
				selectedDueAt = dueAt;
				selectedPriority = 2;
			}
		}

		if (selectedTarget == null || selectedDueAt > now) {
			return;
		}
		applyServiceTarget(bot, selectedTarget);
		markDispatchedForMatchingJobs(bot.getUUID(), selectedTarget, now);
	}

	private static boolean isBetterCandidate(long dueAt, int priority, long selectedDueAt, int selectedPriority) {
		return dueAt < selectedDueAt || (dueAt == selectedDueAt && priority < selectedPriority);
	}

	private static void applyServiceTarget(ServerPlayer bot, ScheduledServiceTarget target) {
		if (bot == null || target == null || target.level() == null) {
			return;
		}
		if (target.followTarget() != null) {
			prepareBotToFollowEntity(
					bot,
					target.level(),
					target.x(),
					target.y(),
					target.z(),
					target.yaw(),
					target.pitch(),
					target.followTarget()
			);
			return;
		}
		prepareBotForStaticView(bot, target.level(), target.x(), target.y(), target.z(), target.yaw(), target.pitch());
	}

	private static void markDispatchedForMatchingJobs(UUID botUuid, ScheduledServiceTarget target, long now) {
		if (botUuid == null || target == null) {
			return;
		}
		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture == null || !botUuid.equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			if (matchesTarget(capture.dimension(), capture.followEntityUuid(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), target)) {
				capture.markDispatched(now);
			}
		}
		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording == null || !botUuid.equals(recording.botUuid()) || recording.stopRequested() || recording.completionFuture().isDone()) {
				continue;
			}
			if (matchesTarget(recording.dimension(), recording.followEntityUuid(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), target)) {
				recording.markDispatched(now);
			}
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !botUuid.equals(stream.botUuid())) {
				continue;
			}
			LiveStreamSpec spec = stream.spec();
			if (matchesTarget(spec.dimension(), spec.followEntityUuid(), spec.expectedX(), spec.expectedY(), spec.expectedZ(), spec.expectedYaw(), spec.expectedPitch(), target)) {
				stream.markDispatched(now);
			}
		}
	}

	private static boolean matchesTarget(
			ResourceKey<Level> dimension,
			UUID followEntityUuid,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			ScheduledServiceTarget target
	) {
		if (target == null) {
			return false;
		}
		if (followEntityUuid != null) {
			Entity followTarget = target.followTarget();
			return followTarget != null && followEntityUuid.equals(followTarget.getUUID());
		}
		if (target.followTarget() != null || target.level() == null || dimension == null || !target.level().dimension().equals(dimension)) {
			return false;
		}
		return Math.abs(target.x() - x) <= 0.01D
				&& Math.abs(target.y() - y) <= 0.01D
				&& Math.abs(target.z() - z) <= 0.01D
				&& Math.abs(target.yaw() - yaw) <= 0.1F
				&& Math.abs(target.pitch() - pitch) <= 0.1F;
	}

	private static ScheduledServiceTarget resolveServiceTarget(
			MinecraftServer server,
			ResourceKey<Level> dimension,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			UUID followEntityUuid
	) {
		if (server == null) {
			return null;
		}
		if (followEntityUuid != null) {
			Entity target = resolveFollowEntity(server, dimension, followEntityUuid);
			if (target == null || !(target.level() instanceof ServerLevel targetLevel)) {
				return null;
			}
			return new ScheduledServiceTarget(targetLevel, target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot(), target);
		}
		ServerLevel level = dimension != null ? server.getLevel(dimension) : null;
		if (level == null) {
			return null;
		}
		return new ScheduledServiceTarget(level, x, y, z, yaw, pitch, null);
	}

	private static Entity resolveFollowEntity(MinecraftServer server, ResourceKey<Level> dimension, UUID followEntityUuid) {
		if (server == null || followEntityUuid == null) {
			return null;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(followEntityUuid);
		if (player != null) {
			return player;
		}
		ServerLevel level = dimension != null ? server.getLevel(dimension) : null;
		return level == null ? null : level.getEntity(followEntityUuid);
	}

	private static void failCapture(UUID requestId, PendingCapture capture, String message) {
		if (capture == null || requestId == null) {
			return;
		}
		IllegalStateException failure = new IllegalStateException(message);
		if (PENDING_CAPTURES.remove(requestId, capture)) {
			capture.previewFuture().completeExceptionally(failure);
			capture.fullFuture().completeExceptionally(failure);
			releaseBotCameraIfNeeded(capture.server(), capture.botUuid(), capture.resetCameraOnFinish());
		}
	}

	private static void failVideoRecording(UUID requestId, PendingVideoRecording recording, String message) {
		if (recording == null || requestId == null) {
			return;
		}
		IllegalStateException failure = new IllegalStateException(message);
		if (PENDING_VIDEO_RECORDINGS.remove(requestId, recording)) {
			recording.completionFuture().completeExceptionally(failure);
			releaseBotCameraIfNeeded(recording.server(), recording.botUuid(), recording.resetCameraOnFinish());
		}
	}

	private static boolean botHasActiveJobs(UUID botUuid) {
		if (botUuid == null) {
			return false;
		}
		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture != null && botUuid.equals(capture.botUuid()) && !capture.isDone()) {
				return true;
			}
		}
		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording != null && botUuid.equals(recording.botUuid()) && !recording.completionFuture().isDone()) {
				return true;
			}
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream != null && botUuid.equals(stream.botUuid())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isEntityWithinAnyTrackingTarget(MinecraftServer server, UUID botUuid, Entity entity, double horizontalRangeSq) {
		if (server == null || botUuid == null || entity == null || horizontalRangeSq <= 0.0D) {
			return false;
		}

		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture == null || !botUuid.equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, capture.dimension(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), capture.followEntityUuid());
			if (isEntityWithinTrackingTarget(entity, target, horizontalRangeSq)) {
				return true;
			}
		}
		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording == null || !botUuid.equals(recording.botUuid()) || recording.stopRequested() || recording.completionFuture().isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, recording.dimension(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), recording.followEntityUuid());
			if (isEntityWithinTrackingTarget(entity, target, horizontalRangeSq)) {
				return true;
			}
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !botUuid.equals(stream.botUuid())) {
				continue;
			}
			LiveStreamSpec spec = stream.spec();
			ScheduledServiceTarget target = resolveServiceTarget(
					server,
					spec.dimension(),
					spec.expectedX(),
					spec.expectedY(),
					spec.expectedZ(),
					spec.expectedYaw(),
					spec.expectedPitch(),
					spec.followEntityUuid()
			);
			if (isEntityWithinTrackingTarget(entity, target, horizontalRangeSq)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isEntityWithinTrackingTarget(Entity entity, ScheduledServiceTarget target, double horizontalRangeSq) {
		if (entity == null || target == null || target.level() == null || entity.level() != target.level()) {
			return false;
		}
		double dx = entity.getX() - target.x();
		double dz = entity.getZ() - target.z();
		return dx * dx + dz * dz <= horizontalRangeSq;
	}

	private static boolean isWithinActiveBotZone(MinecraftServer server, UUID botUuid, ServerLevel level, double x, double y, double z) {
		if (server == null || botUuid == null || level == null) {
			return false;
		}
		ServerPlayer bot = server.getPlayerList().getPlayer(botUuid);
		if (bot == null) {
			return false;
		}
		if (!botHasActiveJobs(botUuid)) {
			return true;
		}
		if (!(bot.level() instanceof ServerLevel botLevel) || !botLevel.dimension().equals(level.dimension())) {
			return false;
		}
		double dx = bot.getX() - x;
		double dy = bot.getY() - y;
		double dz = bot.getZ() - z;
		return dx * dx + dy * dy + dz * dz <= SHARED_RENDER_RADIUS_SQ;
	}

	private static boolean isWithinLiveStreamBotZone(MinecraftServer server, UUID botUuid, ServerLevel level, BlockPos cameraPos, double x, double y, double z) {
		if (cameraPos == null) {
			return isWithinActiveBotZone(server, botUuid, level, x, y, z);
		}
		if (server == null || botUuid == null || level == null) {
			return false;
		}
		ServerPlayer bot = server.getPlayerList().getPlayer(botUuid);
		if (bot == null) {
			return false;
		}
		if (!botHasActiveJobs(botUuid)) {
			return true;
		}
		return bot.level() == level;
	}

	private static ScheduledServiceTarget resolveAnyActiveBotTarget(MinecraftServer server, UUID botUuid) {
		if (server == null || botUuid == null) {
			return null;
		}
		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording == null || !botUuid.equals(recording.botUuid()) || recording.completionFuture().isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, recording.dimension(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), recording.followEntityUuid());
			if (target != null) {
				return target;
			}
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || !botUuid.equals(stream.botUuid())) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, stream.spec().dimension(), stream.spec().expectedX(), stream.spec().expectedY(), stream.spec().expectedZ(), stream.spec().expectedYaw(), stream.spec().expectedPitch(), stream.spec().followEntityUuid());
			if (target != null) {
				return target;
			}
		}
		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture == null || !botUuid.equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, capture.dimension(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), capture.followEntityUuid());
			if (target != null) {
				return target;
			}
		}
		return null;
	}

	private record VirtualChunkTrackingView(LongSet chunks) implements ChunkTrackingView {
		private VirtualChunkTrackingView(LongSet chunks) {
			this.chunks = chunks == null ? new LongOpenHashSet() : new LongOpenHashSet(chunks);
		}

		@Override
		public boolean contains(int x, int z, boolean includeEdge) {
			return this.chunks.contains(new ChunkPos(x, z).toLong());
		}

		@Override
		public void forEach(Consumer<ChunkPos> consumer) {
			if (consumer == null || this.chunks.isEmpty()) {
				return;
			}
			LongIterator iterator = this.chunks.iterator();
			while (iterator.hasNext()) {
				consumer.accept(new ChunkPos(iterator.nextLong()));
			}
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof VirtualChunkTrackingView other)) {
				return false;
			}
			return Objects.equals(this.chunks, other.chunks);
		}

		@Override
		public int hashCode() {
			return this.chunks.hashCode();
		}
	}

	private record ChunkTicketKey(ResourceKey<Level> dimension, long chunkLong) {
	}

	private record ShadowSyncKey(UUID botUuid, UUID sessionId) {
	}

	private static final class ShadowDesiredState {
		private final UUID sessionId;
		private final ServerLevel level;
		private final int viewDistance;
		private final int centerChunkX;
		private final int centerChunkZ;
		private final ScheduledServiceTarget target;
		private final LongOpenHashSet trackedChunks = new LongOpenHashSet();

		private ShadowDesiredState(UUID sessionId, ServerLevel level, int viewDistance, ScheduledServiceTarget target) {
			this.sessionId = sessionId;
			this.level = level;
			this.viewDistance = viewDistance;
			this.centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(target.x()));
			this.centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(target.z()));
			this.target = target;
		}

		private UUID sessionId() {
			return this.sessionId;
		}

		private ServerLevel level() {
			return this.level;
		}

		private int viewDistance() {
			return this.viewDistance;
		}

		private int centerChunkX() {
			return this.centerChunkX;
		}

		private int centerChunkZ() {
			return this.centerChunkZ;
		}

		private ScheduledServiceTarget target() {
			return this.target;
		}

		private LongOpenHashSet trackedChunks() {
			return this.trackedChunks;
		}
	}

	private static final class ShadowDimensionSyncState {
		private final UUID botUuid;
		private final UUID sessionId;
		private final ResourceKey<Level> dimension;
		private final LongOpenHashSet trackedChunks = new LongOpenHashSet();
		private final Map<Integer, ShadowTrackedEntity> trackedEntities = new HashMap<>();
		private boolean initialized;
		private String dimensionTypeId;
		private long seed;
		private int viewDistance;
		private long lastGameTime = Long.MIN_VALUE;
		private long lastDayTime = Long.MIN_VALUE;
		private boolean lastTickDayTime;
		private boolean lastRaining;
		private float lastRainLevel = Float.NaN;
		private float lastThunderLevel = Float.NaN;
		private int lastCenterChunkX = Integer.MIN_VALUE;
		private int lastCenterChunkZ = Integer.MIN_VALUE;

		private ShadowDimensionSyncState(UUID botUuid, UUID sessionId, ResourceKey<Level> dimension) {
			this.botUuid = botUuid;
			this.sessionId = sessionId;
			this.dimension = dimension;
		}

		private UUID botUuid() {
			return this.botUuid;
		}

		private UUID sessionId() {
			return this.sessionId;
		}

		private ResourceKey<Level> dimension() {
			return this.dimension;
		}

		private LongOpenHashSet trackedChunks() {
			return this.trackedChunks;
		}

		private Map<Integer, ShadowTrackedEntity> trackedEntities() {
			return this.trackedEntities;
		}

		private boolean initialized() {
			return this.initialized;
		}

		private void setInitialized(boolean initialized) {
			this.initialized = initialized;
		}

		private String dimensionTypeId() {
			return this.dimensionTypeId;
		}

		private void setDimensionTypeId(String dimensionTypeId) {
			this.dimensionTypeId = dimensionTypeId;
		}

		private long seed() {
			return this.seed;
		}

		private void setSeed(long seed) {
			this.seed = seed;
		}

		private int viewDistance() {
			return this.viewDistance;
		}

		private void setViewDistance(int viewDistance) {
			this.viewDistance = viewDistance;
		}

		private long lastGameTime() {
			return this.lastGameTime;
		}

		private void setLastGameTime(long lastGameTime) {
			this.lastGameTime = lastGameTime;
		}

		private long lastDayTime() {
			return this.lastDayTime;
		}

		private void setLastDayTime(long lastDayTime) {
			this.lastDayTime = lastDayTime;
		}

		private boolean lastTickDayTime() {
			return this.lastTickDayTime;
		}

		private void setLastTickDayTime(boolean lastTickDayTime) {
			this.lastTickDayTime = lastTickDayTime;
		}

		private boolean lastRaining() {
			return this.lastRaining;
		}

		private void setLastRaining(boolean lastRaining) {
			this.lastRaining = lastRaining;
		}

		private float lastRainLevel() {
			return this.lastRainLevel;
		}

		private void setLastRainLevel(float lastRainLevel) {
			this.lastRainLevel = lastRainLevel;
		}

		private float lastThunderLevel() {
			return this.lastThunderLevel;
		}

		private void setLastThunderLevel(float lastThunderLevel) {
			this.lastThunderLevel = lastThunderLevel;
		}

		private int lastCenterChunkX() {
			return this.lastCenterChunkX;
		}

		private void setLastCenterChunkX(int lastCenterChunkX) {
			this.lastCenterChunkX = lastCenterChunkX;
		}

		private int lastCenterChunkZ() {
			return this.lastCenterChunkZ;
		}

		private void setLastCenterChunkZ(int lastCenterChunkZ) {
			this.lastCenterChunkZ = lastCenterChunkZ;
		}
	}

	private record ShadowTrackedEntity(
			Entity entity,
			net.minecraft.server.level.ServerEntity serverEntity,
			ShadowPacketCollector collector
	) {
	}

	private static final class ShadowPacketCollector implements net.minecraft.server.level.ServerEntity.Synchronizer {
		private final ServerPlayer bot;
		private final List<Packet<? extends ClientGamePacketListener>> packets = new ArrayList<>();

		private ShadowPacketCollector(ServerPlayer bot) {
			this.bot = bot;
		}

		@Override
		public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
			add(packet);
		}

		@Override
		public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
			add(packet);
		}

		@Override
		public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, java.util.function.Predicate<ServerPlayer> predicate) {
			if (predicate == null || this.bot == null || predicate.test(this.bot)) {
				add(packet);
			}
		}

		private void clear() {
			this.packets.clear();
		}

		private List<Packet<? extends ClientGamePacketListener>> drain() {
			List<Packet<? extends ClientGamePacketListener>> drained = new ArrayList<>(this.packets);
			this.packets.clear();
			return drained;
		}

		@SuppressWarnings("unchecked")
		private void add(Packet<? super ClientGamePacketListener> packet) {
			if (packet == null) {
				return;
			}
			this.packets.add((Packet<? extends ClientGamePacketListener>) packet);
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

	private static final class PendingCapture {
		private final UUID requestId;
		private final MinecraftServer server;
		private final UUID botUuid;
		private final boolean resetCameraOnFinish;
		private final ResourceKey<Level> dimension;
		private final double x;
		private final double y;
		private final double z;
		private final float yaw;
		private final float pitch;
		private final int fovDegrees;
		private final UUID followEntityUuid;
		private final CompletableFuture<byte[]> previewFuture;
		private final CompletableFuture<byte[]> fullFuture;
		private volatile long lastDispatchAtMillis;

		private PendingCapture(
				UUID requestId,
				MinecraftServer server,
				UUID botUuid,
				boolean resetCameraOnFinish,
				ResourceKey<Level> dimension,
				double x,
				double y,
				double z,
				float yaw,
				float pitch,
				int fovDegrees,
				UUID followEntityUuid,
				CompletableFuture<byte[]> previewFuture,
				CompletableFuture<byte[]> fullFuture
		) {
			this.requestId = requestId;
			this.server = server;
			this.botUuid = botUuid;
			this.resetCameraOnFinish = resetCameraOnFinish;
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.pitch = pitch;
			this.fovDegrees = fovDegrees;
			this.followEntityUuid = followEntityUuid;
			this.previewFuture = previewFuture;
			this.fullFuture = fullFuture;
			this.lastDispatchAtMillis = 0L;
		}

		private UUID requestId() {
			return this.requestId;
		}

		private MinecraftServer server() {
			return this.server;
		}

		private UUID botUuid() {
			return this.botUuid;
		}

		private boolean resetCameraOnFinish() {
			return this.resetCameraOnFinish;
		}

		private ResourceKey<Level> dimension() {
			return this.dimension;
		}

		private double x() {
			return this.x;
		}

		private double y() {
			return this.y;
		}

		private double z() {
			return this.z;
		}

		private float yaw() {
			return this.yaw;
		}

		private float pitch() {
			return this.pitch;
		}

		private int fovDegrees() {
			return this.fovDegrees;
		}

		private UUID followEntityUuid() {
			return this.followEntityUuid;
		}

		private CompletableFuture<byte[]> previewFuture() {
			return this.previewFuture;
		}

		private CompletableFuture<byte[]> fullFuture() {
			return this.fullFuture;
		}

		private boolean isDone() {
			return this.previewFuture.isDone() && this.fullFuture.isDone();
		}

		private long nextDueAtMillis() {
			return this.lastDispatchAtMillis <= 0L ? 0L : this.lastDispatchAtMillis + PHOTO_CAPTURE_RETRY_INTERVAL_MS;
		}

		private void markDispatched(long now) {
			this.lastDispatchAtMillis = now;
		}
	}

	private static final class PendingVideoRecording {
		private final UUID requestId;
		private final MinecraftServer server;
		private final UUID botUuid;
		private final boolean resetCameraOnFinish;
		private final ResourceKey<Level> dimension;
		private final double x;
		private final double y;
		private final double z;
		private final float yaw;
		private final float pitch;
		private final int fovDegrees;
		private final UUID followEntityUuid;
		private final int targetFps;
		private final CompletableFuture<VideoRecordingResult> completionFuture;
		private volatile long lastDispatchAtMillis;
		private volatile boolean stopRequested;

		private PendingVideoRecording(
				UUID requestId,
				MinecraftServer server,
				UUID botUuid,
				boolean resetCameraOnFinish,
				ResourceKey<Level> dimension,
				double x,
				double y,
				double z,
				float yaw,
				float pitch,
				int fovDegrees,
				UUID followEntityUuid,
				int targetFps,
				CompletableFuture<VideoRecordingResult> completionFuture
		) {
			this.requestId = requestId;
			this.server = server;
			this.botUuid = botUuid;
			this.resetCameraOnFinish = resetCameraOnFinish;
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.yaw = yaw;
			this.pitch = pitch;
			this.fovDegrees = fovDegrees;
			this.followEntityUuid = followEntityUuid;
			this.targetFps = targetFps;
			this.completionFuture = completionFuture;
			this.lastDispatchAtMillis = 0L;
			this.stopRequested = false;
		}

		private UUID requestId() {
			return this.requestId;
		}

		private MinecraftServer server() {
			return this.server;
		}

		private UUID botUuid() {
			return this.botUuid;
		}

		private boolean resetCameraOnFinish() {
			return this.resetCameraOnFinish;
		}

		private ResourceKey<Level> dimension() {
			return this.dimension;
		}

		private double x() {
			return this.x;
		}

		private double y() {
			return this.y;
		}

		private double z() {
			return this.z;
		}

		private float yaw() {
			return this.yaw;
		}

		private float pitch() {
			return this.pitch;
		}

		private int fovDegrees() {
			return this.fovDegrees;
		}

		private UUID followEntityUuid() {
			return this.followEntityUuid;
		}

		private int targetFps() {
			return this.targetFps;
		}

		private CompletableFuture<VideoRecordingResult> completionFuture() {
			return this.completionFuture;
		}

		private boolean stopRequested() {
			return this.stopRequested;
		}

		private void markStopRequested() {
			this.stopRequested = true;
		}

		private long nextDueAtMillis() {
			long interval = Math.max(1L, 1_000L / Math.max(1, this.targetFps));
			return this.lastDispatchAtMillis <= 0L ? 0L : this.lastDispatchAtMillis + interval;
		}

		private void markDispatched(long now) {
			this.lastDispatchAtMillis = now;
		}
	}

	private record LiveStreamSpec(
			ResourceKey<Level> dimension,
			BlockPos cameraPos,
			double expectedX,
			double expectedY,
			double expectedZ,
			float expectedYaw,
			float expectedPitch,
			UUID followEntityUuid,
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
		private volatile long lastDispatchAtMillis;

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
			this.lastDispatchAtMillis = 0L;
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

		private long nextDueAtMillis() {
			long interval = Math.max(1L, 1_000L / Math.max(1, this.spec.targetFps()));
			return this.lastDispatchAtMillis <= 0L ? 0L : this.lastDispatchAtMillis + interval;
		}

		private void markDispatched(long now) {
			this.lastDispatchAtMillis = now;
		}

		private boolean isStale() {
			long referenceTime = this.startedAtMillis;
			if (this.lastDispatchAtMillis > referenceTime) {
				referenceTime = this.lastDispatchAtMillis;
			}
			if (this.lastFrameAtMillis > referenceTime) {
				referenceTime = this.lastFrameAtMillis;
			}
			return System.currentTimeMillis() - referenceTime > LIVE_STREAM_STALE_MS;
		}
	}

	private record ScheduledServiceTarget(
			ServerLevel level,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			Entity followTarget
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
