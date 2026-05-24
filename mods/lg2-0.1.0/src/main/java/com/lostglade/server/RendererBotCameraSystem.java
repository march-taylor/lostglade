package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
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

import java.nio.charset.StandardCharsets;
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
	private static final int HOTBAR_SIZE = 9;
	private static final int STATIC_CAMERA_SESSION_CLUSTER_CHUNKS = 2;
	private static final int CAMERA_HOTBAR_WARMUP_FPS = 2;
	private static final int CAMERA_HOTBAR_WARMUP_SIZE = 1;
	private static final int CAMERA_HOTBAR_WARMUP_FOV_DEGREES = 70;
	private static final int CAMERA_CHUNK_TICKET_UNIQUE_FLAG = 128;
	private static final int SHADOW_VIEW_DISTANCE_MARGIN_CHUNKS = 6;
	private static final int SHADOW_REAR_VIEW_CHUNKS = 2;
	private static final long LIVE_STREAM_STALE_MS = 1_500L;
	private static final long LIVE_STREAM_ORPHAN_CLEANUP_MS = 15_000L;
	private static final long PHOTO_CAPTURE_RETRY_INTERVAL_MS = 50L;
	private static final double SHADOW_FORWARD_HALF_FOV_DEGREES = 80.0D;
	private static final double SHADOW_NEAR_OMNI_RADIUS_CHUNKS = 3.0D;
	private static final double SHADOW_SIDE_SAFETY_MARGIN_CHUNKS = 1.5D;
	// Static screen cameras keep a wider local buffer so large angle changes stay smooth without full-circle loading.
	private static final double STATIC_CAMERA_FORWARD_HALF_FOV_DEGREES = 90.0D;
	private static final double STATIC_CAMERA_NEAR_OMNI_RADIUS_CHUNKS = 6.0D;
	private static final int STATIC_CAMERA_REAR_VIEW_CHUNKS = 4;
	private static final double STATIC_CAMERA_SIDE_SAFETY_MARGIN_CHUNKS = 2.0D;
	private static final double SHARED_RENDER_RADIUS_BLOCKS = 96.0D;
	private static final double SHARED_RENDER_RADIUS_SQ = SHARED_RENDER_RADIUS_BLOCKS * SHARED_RENDER_RADIUS_BLOCKS;
	private static final TicketType CAMERA_CHUNK_TICKET_TYPE = new TicketType(
			0L,
			TicketType.FLAG_LOADING | CAMERA_CHUNK_TICKET_UNIQUE_FLAG
	);
	private static final Map<UUID, BotHandshake> READY_BOTS = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingCapture> PENDING_CAPTURES = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingVideoRecording> PENDING_VIDEO_RECORDINGS = new ConcurrentHashMap<>();
	private static final Map<UUID, ActiveLiveStream> ACTIVE_LIVE_STREAMS = new ConcurrentHashMap<>();
	private static final Map<String, UUID> LIVE_STREAMS_BY_OWNER = new ConcurrentHashMap<>();
	private static final Map<CameraChunkTicketKey, Integer> ACTIVE_CAMERA_CHUNK_TICKETS = new HashMap<>();
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
					ActiveLiveStream current = ACTIVE_LIVE_STREAMS.get(payload.streamId());
					if (current == null || !current.botUuid().equals(context.player().getUUID())) {
						return;
					}
					try {
						current.onFrame().accept(payload.pixels());
					} catch (Exception exception) {
						Lg2.LOGGER.warn("Renderer bot live stream frame callback failed for {}", payload.streamId(), exception);
					}
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
		UUID renderSessionId = resolveRenderSessionId(
				level.dimension(),
				null,
				x,
				z,
				followTarget != null ? followTarget.getUUID() : null
		);
		long timeoutMillis = Math.max(500L, Lg2Config.get().cameraRendererBotTimeoutMs);
		CompletableFuture<byte[]> previewFuture = new CompletableFuture<>();
		CompletableFuture<byte[]> fullFuture = new CompletableFuture<>();
		PendingCapture pending = new PendingCapture(
				requestId,
				renderSessionId,
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
						renderSessionId,
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
		return ensureLiveStream(
				ownerKey,
				level,
				cameraPos,
				x,
				y,
				z,
				yaw,
				pitch,
				null,
				Set.of(),
				false,
				fullWidth,
				fullHeight,
				fovDegrees,
				targetFps,
				onFrame,
				onFailure
		);
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
			UUID followEntityUuid,
			Set<UUID> hiddenEntityUuids,
			boolean omnidirectionalChunkLoading,
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
				resolveRenderSessionId(level.dimension(), cameraPos, x, z, followEntityUuid),
				level.dimension(),
				cameraPos == null ? null : cameraPos.immutable(),
				x,
				y,
				z,
				yaw,
				pitch,
				followEntityUuid,
				hiddenEntityUuids == null ? Set.of() : Set.copyOf(hiddenEntityUuids),
				omnidirectionalChunkLoading,
				Math.max(1, fullWidth),
				Math.max(1, fullHeight),
				Math.max(1, fovDegrees),
				Math.clamp(Math.max(1, targetFps), 1, MAX_LIVE_STREAM_FPS)
		);
		UUID existingStreamId = LIVE_STREAMS_BY_OWNER.get(ownerKey);
		if (existingStreamId != null) {
			ActiveLiveStream existing = ACTIVE_LIVE_STREAMS.get(existingStreamId);
			if (canReuseLiveStream(existing, bot, desiredSpec)) {
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
						desiredSpec.renderSessionId(),
						desiredSpec.dimension().identifier().toString(),
						desiredSpec.expectedX(),
						desiredSpec.expectedY(),
						desiredSpec.expectedZ(),
						desiredSpec.expectedYaw(),
						desiredSpec.expectedPitch(),
						desiredSpec.followEntityUuid(),
						desiredSpec.fullWidth(),
						desiredSpec.fullHeight(),
						desiredSpec.fovDegrees(),
						desiredSpec.targetFps()
				)
		);
		return true;
	}

	private static boolean canReuseLiveStream(ActiveLiveStream existing, ServerPlayer bot, LiveStreamSpec desiredSpec) {
		if (existing == null || bot == null || desiredSpec == null || existing.isStale() || !existing.botUuid().equals(bot.getUUID())) {
			return false;
		}
		LiveStreamSpec existingSpec = existing.spec();
		if (existingSpec == null) {
			return false;
		}
		if (desiredSpec.followEntityUuid() != null) {
			return Objects.equals(existingSpec.dimension(), desiredSpec.dimension())
					&& Objects.equals(existingSpec.cameraPos(), desiredSpec.cameraPos())
					&& Objects.equals(existingSpec.followEntityUuid(), desiredSpec.followEntityUuid())
					&& Objects.equals(existingSpec.hiddenEntityUuids(), desiredSpec.hiddenEntityUuids())
					&& existingSpec.omnidirectionalChunkLoading() == desiredSpec.omnidirectionalChunkLoading()
					&& existingSpec.fullWidth() == desiredSpec.fullWidth()
					&& existingSpec.fullHeight() == desiredSpec.fullHeight()
					&& existingSpec.fovDegrees() == desiredSpec.fovDegrees()
					&& existingSpec.targetFps() == desiredSpec.targetFps();
		}
		return existingSpec.equals(desiredSpec);
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

	public static boolean hasHealthyLiveStreamFollowingEntity(UUID entityUuid) {
		if (entityUuid == null) {
			return false;
		}
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null || stream.isStale()) {
				continue;
			}
			LiveStreamSpec spec = stream.spec();
			if (spec != null && entityUuid.equals(spec.followEntityUuid())) {
				return true;
			}
		}
		return false;
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
		UUID renderSessionId = resolveRenderSessionId(
				requester.level().dimension(),
				null,
				requester.getX(),
				requester.getZ(),
				requester.getUUID()
		);
		long timeoutMillis = Math.max(5_000L, Lg2Config.get().cameraRendererBotTimeoutMs);
		CompletableFuture<VideoRecordingResult> completionFuture = new CompletableFuture<>();
		PendingVideoRecording pending = new PendingVideoRecording(
				requestId,
				renderSessionId,
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
						renderSessionId,
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
		int viewDistance = resolveShadowViewDistance(bot);
		ChunkTrackingView positionedView = createPositionedVirtualChunkTrackingView(bot, viewDistance);
		if (positionedView != null) {
			return positionedView;
		}
		LongSet virtualChunks = collectVirtualTrackedChunks(bot, viewDistance);
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

	public static boolean shouldReceiveNearbyWebcam(ServerPlayer viewer, Entity source, double horizontalRangeBlocks) {
		if (viewer == null
				|| source == null
				|| horizontalRangeBlocks <= 0.0D
				|| !RendererBotPresenceSystem.isRendererBot(viewer)
				|| !(source.level() instanceof ServerLevel sourceLevel)) {
			return false;
		}
		MinecraftServer server = sourceLevel.getServer();
		if (server == null) {
			return false;
		}
		return isEntityWithinAnyTrackingTarget(server, viewer.getUUID(), source, horizontalRangeBlocks * horizontalRangeBlocks);
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
		syncCameraHotbarWarmupStreams(server);
		cleanupOrphanedLiveStreams(server);
		if (!hasActiveShadowSyncWork(server)) {
			return;
		}
		syncShadowWorlds(server);
	}

	private static boolean hasActiveShadowSyncWork(MinecraftServer server) {
		return !ACTIVE_LIVE_STREAMS.isEmpty()
				|| !PENDING_CAPTURES.isEmpty()
				|| !PENDING_VIDEO_RECORDINGS.isEmpty()
				|| !ACTIVE_SHADOW_SYNC_STATES.isEmpty()
				|| !ACTIVE_CAMERA_CHUNK_TICKETS.isEmpty()
				|| !DIRTY_SHADOW_CHUNKS.isEmpty()
				|| hasCameraHotbarWarmupTargets(server);
	}

	private static boolean hasCameraHotbarWarmupTargets(MinecraftServer server) {
		if (server == null || server.getPlayerList() == null) {
			return false;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null
					|| !player.isAlive()
					|| DroneSystem.isCameraBlockedByDroneControl(player)
					|| !(player.level() instanceof ServerLevel)
					|| !playerHasCameraInActiveSlot(player)) {
				continue;
			}
			return true;
		}
		return false;
	}

	private static boolean playerHasCameraInActiveSlot(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		int selected = player.getInventory().getSelectedSlot();
		if (selected < 0 || selected >= HOTBAR_SIZE) {
			return false;
		}
		return player.getInventory().getItem(selected).is(ModItems.CAMERA);
	}

	private static String cameraHotbarWarmupOwnerKey(UUID playerUuid) {
		return playerUuid == null ? null : "lg2:camera_hotbar_warmup:" + playerUuid;
	}

	public static void stopCameraHotbarWarmupForPlayer(UUID playerUuid) {
		String ownerKey = cameraHotbarWarmupOwnerKey(playerUuid);
		if (ownerKey == null) {
			return;
		}
		stopLiveStream(ownerKey);
	}

	private static void syncCameraHotbarWarmupStreams(MinecraftServer server) {
		if (server == null || server.getPlayerList() == null) {
			return;
		}
		Set<String> desiredOwnerKeys = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player == null) {
				continue;
			}
			String ownerKey = cameraHotbarWarmupOwnerKey(player.getUUID());
			if (ownerKey == null) {
				continue;
			}
			if (!player.isAlive()
					|| DroneSystem.isCameraBlockedByDroneControl(player)
					|| !(player.level() instanceof ServerLevel playerLevel)
					|| !playerHasCameraInActiveSlot(player)) {
				stopLiveStream(ownerKey);
				continue;
			}
			desiredOwnerKeys.add(ownerKey);
			ensureLiveStream(
					ownerKey,
					playerLevel,
					null,
					player.getX(),
					player.getY(),
					player.getZ(),
					player.getYRot(),
					player.getXRot(),
					player.getUUID(),
					Set.of(),
					true,
					CAMERA_HOTBAR_WARMUP_SIZE,
					CAMERA_HOTBAR_WARMUP_SIZE,
					CAMERA_HOTBAR_WARMUP_FOV_DEGREES,
					CAMERA_HOTBAR_WARMUP_FPS,
					pixels -> {
					},
					error -> {
					}
			);
		}

		for (String ownerKey : new ArrayList<>(LIVE_STREAMS_BY_OWNER.keySet())) {
			if (ownerKey == null || !ownerKey.startsWith("lg2:camera_hotbar_warmup:") || desiredOwnerKeys.contains(ownerKey)) {
				continue;
			}
			stopLiveStream(ownerKey);
		}
	}

	private static void cleanupOrphanedLiveStreams(MinecraftServer server) {
		if (server == null || ACTIVE_LIVE_STREAMS.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (ActiveLiveStream stream : new ArrayList<>(ACTIVE_LIVE_STREAMS.values())) {
			if (stream == null) {
				continue;
			}
			UUID expectedStreamId = LIVE_STREAMS_BY_OWNER.get(stream.ownerKey());
			boolean ownerMismatch = !Objects.equals(expectedStreamId, stream.streamId());
			if (ownerMismatch) {
				stopLiveStreamInternal(stream, "Renderer bot live stream orphaned", false);
				continue;
			}
			boolean botUnavailable = !READY_BOTS.containsKey(stream.botUuid())
					|| server.getPlayerList() == null
					|| server.getPlayerList().getPlayer(stream.botUuid()) == null;
			if (botUnavailable) {
				stopLiveStreamInternal(stream, "Renderer bot live stream target is unavailable", true);
				continue;
			}
			if (now - stream.lastActivityAtMillis() > LIVE_STREAM_ORPHAN_CLEANUP_MS) {
				stopLiveStreamInternal(stream, "Renderer bot live stream timed out", true);
			}
		}
	}

	private static UUID resolveRenderSessionId(
			ResourceKey<Level> dimension,
			BlockPos cameraPos,
			double x,
			double z,
			UUID followEntityUuid
	) {
		if (dimension == null) {
			return UUID.randomUUID();
		}
		if (followEntityUuid != null) {
			return UUID.nameUUIDFromBytes(
					("lg2:render-session:follow:" + dimension.identifier() + ":" + followEntityUuid).getBytes(StandardCharsets.UTF_8)
			);
		}
		int chunkX = SectionPos.blockToSectionCoord(cameraPos != null ? cameraPos.getX() : Mth.floor(x));
		int chunkZ = SectionPos.blockToSectionCoord(cameraPos != null ? cameraPos.getZ() : Mth.floor(z));
		int clusterX = Math.floorDiv(chunkX, STATIC_CAMERA_SESSION_CLUSTER_CHUNKS);
		int clusterZ = Math.floorDiv(chunkZ, STATIC_CAMERA_SESSION_CLUSTER_CHUNKS);
		return UUID.nameUUIDFromBytes(
				("lg2:render-session:static:" + dimension.identifier() + ":" + clusterX + ":" + clusterZ).getBytes(StandardCharsets.UTF_8)
		);
	}

	private static int resolveViewDistance(ServerPlayer bot) {
		MinecraftServer server = bot != null && bot.level() != null ? bot.level().getServer() : null;
		return Mth.clamp(
				server != null && server.getPlayerList() != null ? server.getPlayerList().getViewDistance() : 2,
				2,
				32
		);
	}

	private static int resolveShadowViewDistance(ServerPlayer bot) {
		return Mth.clamp(resolveViewDistance(bot) + SHADOW_VIEW_DISTANCE_MARGIN_CHUNKS, 2, 32);
	}

	public static int resolveCameraShadowViewDistance(ServerPlayer viewer) {
		return resolveShadowViewDistance(viewer);
	}

	public static int resolveCameraShadowViewDistance(MinecraftServer server) {
		ServerPlayer bot = selectBot(server);
		return bot == null ? 2 : resolveShadowViewDistance(bot);
	}

	public static ChunkTrackingView createCameraChunkTrackingView(
			double x,
			double z,
			float yaw,
			int viewDistance,
			boolean omnidirectionalChunkLoading,
			boolean staticCameraChunkBuffer
	) {
		int clampedViewDistance = Mth.clamp(viewDistance, 2, 32);
		if (omnidirectionalChunkLoading) {
			return ChunkTrackingView.of(chunkPosAt(x, z), clampedViewDistance);
		}
		LongSet chunks = collectCameraViewChunks(x, z, yaw, viewDistance, omnidirectionalChunkLoading, staticCameraChunkBuffer);
		return chunks.isEmpty() ? ChunkTrackingView.EMPTY : new VirtualChunkTrackingView(chunks);
	}

	public static LongSet collectCameraViewChunks(
			double x,
			double z,
			float yaw,
			int viewDistance,
			boolean omnidirectionalChunkLoading,
			boolean staticCameraChunkBuffer
	) {
		return computeVirtualCameraChunks(
				x,
				z,
				yaw,
				Mth.clamp(viewDistance, 2, 32),
				omnidirectionalChunkLoading,
				staticCameraChunkBuffer
		);
	}

	private static boolean canBotRenderLevel(ServerPlayer bot, ServerLevel level) {
		return bot != null && level != null && READY_BOTS.containsKey(bot.getUUID());
	}

	private static ChunkTrackingView createPositionedVirtualChunkTrackingView(ServerPlayer bot, int viewDistance) {
		if (bot == null || !(bot.level() instanceof ServerLevel botLevel)) {
			return null;
		}
		MinecraftServer server = botLevel.getServer();
		if (server == null) {
			return null;
		}
		UUID botUuid = bot.getUUID();
		ChunkPos center = null;

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
			center = mergePositionedVirtualCenter(center, botLevel, target);
			if (center == null && target != null) {
				return null;
			}
		}

		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture == null || !botUuid.equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, capture.dimension(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), capture.followEntityUuid());
			center = mergePositionedVirtualCenter(center, botLevel, target);
			if (center == null && target != null) {
				return null;
			}
		}

		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording == null || !botUuid.equals(recording.botUuid()) || recording.stopRequested() || recording.completionFuture().isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, recording.dimension(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), recording.followEntityUuid());
			center = mergePositionedVirtualCenter(center, botLevel, target);
			if (center == null && target != null) {
				return null;
			}
		}

		return center == null ? null : ChunkTrackingView.of(center, Mth.clamp(viewDistance, 2, 32));
	}

	private static ChunkPos mergePositionedVirtualCenter(ChunkPos current, ServerLevel expectedLevel, ScheduledServiceTarget target) {
		if (target == null || target.level() != expectedLevel) {
			return current;
		}
		ChunkPos candidate = chunkPosAt(target.x(), target.z());
		if (current == null || current.equals(candidate)) {
			return candidate;
		}
		return null;
	}

	private static ChunkPos chunkPosAt(double x, double z) {
		return new ChunkPos(
				SectionPos.blockToSectionCoord(Mth.floor(x)),
				SectionPos.blockToSectionCoord(Mth.floor(z))
		);
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
			appendVirtualTargetChunks(
					chunks,
					botLevel,
					target,
					viewDistance,
					spec.omnidirectionalChunkLoading(),
					spec.cameraPos() != null && spec.followEntityUuid() == null
			);
		}
		for (PendingCapture capture : PENDING_CAPTURES.values()) {
			if (capture == null || !botUuid.equals(capture.botUuid()) || capture.isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, capture.dimension(), capture.x(), capture.y(), capture.z(), capture.yaw(), capture.pitch(), capture.followEntityUuid());
			if (target == null || target.level() != botLevel) {
				continue;
			}
			appendVirtualTargetChunks(chunks, botLevel, target, viewDistance, false, false);
		}
		for (PendingVideoRecording recording : PENDING_VIDEO_RECORDINGS.values()) {
			if (recording == null || !botUuid.equals(recording.botUuid()) || recording.stopRequested() || recording.completionFuture().isDone()) {
				continue;
			}
			ScheduledServiceTarget target = resolveServiceTarget(server, recording.dimension(), recording.x(), recording.y(), recording.z(), recording.yaw(), recording.pitch(), recording.followEntityUuid());
			if (target == null || target.level() != botLevel) {
				continue;
			}
			appendVirtualTargetChunks(chunks, botLevel, target, viewDistance, false, false);
		}
		return chunks;
	}

	private static void appendVirtualTargetChunks(
			LongSet chunks,
			ServerLevel level,
			ScheduledServiceTarget target,
			int viewDistance,
			boolean omnidirectionalChunkLoading,
			boolean staticCameraChunkBuffer
	) {
		if (chunks == null || level == null || target == null || target.level() != level) {
			return;
		}
		appendVirtualCameraChunks(
				chunks,
				target.x(),
				target.z(),
				target.yaw(),
				viewDistance,
				omnidirectionalChunkLoading,
				staticCameraChunkBuffer
		);
	}

	private static void appendVirtualCameraChunks(
			LongSet target,
			double x,
			double z,
			float yaw,
			int viewDistance,
			boolean omnidirectionalChunkLoading,
			boolean staticCameraChunkBuffer
	) {
		if (target == null || viewDistance <= 0) {
			return;
		}
		LongSet chunks = computeVirtualCameraChunks(x, z, yaw, viewDistance, omnidirectionalChunkLoading, staticCameraChunkBuffer);
		LongIterator iterator = chunks.iterator();
		while (iterator.hasNext()) {
			target.add(iterator.nextLong());
		}
	}

	private static LongSet computeVirtualCameraChunks(
			double x,
			double z,
			float yaw,
			int viewDistance,
			boolean omnidirectionalChunkLoading,
			boolean staticCameraChunkBuffer
	) {
		if (omnidirectionalChunkLoading) {
			return computeOmnidirectionalCameraChunks(x, z, viewDistance);
		}
		if (staticCameraChunkBuffer) {
			return computeDirectionalCameraChunks(
					x,
					z,
					yaw,
					viewDistance,
					STATIC_CAMERA_FORWARD_HALF_FOV_DEGREES,
					STATIC_CAMERA_NEAR_OMNI_RADIUS_CHUNKS,
					STATIC_CAMERA_REAR_VIEW_CHUNKS,
					STATIC_CAMERA_SIDE_SAFETY_MARGIN_CHUNKS
			);
		}
		return computeDirectionalCameraChunks(
				x,
				z,
				yaw,
				viewDistance,
				SHADOW_FORWARD_HALF_FOV_DEGREES,
				SHADOW_NEAR_OMNI_RADIUS_CHUNKS,
				SHADOW_REAR_VIEW_CHUNKS,
				SHADOW_SIDE_SAFETY_MARGIN_CHUNKS
		);
	}

	private static LongSet computeDirectionalCameraChunks(
			double x,
			double z,
			float yaw,
			int viewDistance,
			double halfFovDegrees,
			double nearOmniRadiusChunks,
			int rearViewChunks,
			double sideSafetyMarginChunks
	) {
		LongOpenHashSet chunks = new LongOpenHashSet();
		int centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(x));
		int centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(z));
		double yawRadians = Math.toRadians(yaw);
		double forwardX = -Math.sin(yawRadians);
		double forwardZ = Math.cos(yawRadians);
		double halfFovRadians = Math.toRadians(halfFovDegrees);
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
				if (horizontalDistanceChunks <= nearOmniRadiusChunks) {
					chunks.add(new ChunkPos(chunkX, chunkZ).toLong());
					continue;
				}
				double forwardDistance = deltaChunkX * forwardX + deltaChunkZ * forwardZ;
				if (forwardDistance < -rearViewChunks || forwardDistance > viewDistance + sideSafetyMarginChunks) {
					continue;
				}
				double sideDistance = Math.abs(deltaChunkX * forwardZ - deltaChunkZ * forwardX);
				double allowedSideDistance = forwardDistance <= 0.0D
						? nearOmniRadiusChunks + sideSafetyMarginChunks
						: forwardDistance * tangentLimit + sideSafetyMarginChunks;
				if (sideDistance > allowedSideDistance) {
					continue;
				}
				chunks.add(new ChunkPos(chunkX, chunkZ).toLong());
			}
		}
		return chunks;
	}

	private static LongSet computeOmnidirectionalCameraChunks(double x, double z, int viewDistance) {
		LongOpenHashSet chunks = new LongOpenHashSet();
		int centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(x));
		int centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(z));
		for (int dx = -viewDistance; dx <= viewDistance; dx++) {
			for (int dz = -viewDistance; dz <= viewDistance; dz++) {
				int chunkX = centerChunkX + dx;
				int chunkZ = centerChunkZ + dz;
				if (!ChunkTrackingView.isInViewDistance(centerChunkX, centerChunkZ, viewDistance, chunkX, chunkZ)) {
					continue;
				}
				chunks.add(new ChunkPos(chunkX, chunkZ).toLong());
			}
		}
		return chunks;
	}

	private static boolean updateVirtualCameraChunkTickets(MinecraftServer server) {
		Map<CameraChunkTicketKey, Integer> desiredRefs = new HashMap<>();
		for (ActiveLiveStream stream : ACTIVE_LIVE_STREAMS.values()) {
			if (stream == null) {
				continue;
			}
			LiveStreamSpec spec = stream.spec();
			if (spec == null || spec.dimension() == null) {
				continue;
			}
			ServerLevel level = server.getLevel(spec.dimension());
			if (level == null || (spec.cameraPos() != null && !isCameraPlayerLoaded(level, spec.cameraPos()))) {
				continue;
			}
			ServerPlayer bot = server.getPlayerList().getPlayer(stream.botUuid());
			if (bot == null || !READY_BOTS.containsKey(bot.getUUID())) {
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
			if (target == null || target.level() == null) {
				continue;
			}
			int viewDistance = resolveShadowViewDistance(bot);
			addCameraChunkTicket(desiredRefs, target.level().dimension(), chunkPosAt(target.x(), target.z()), viewDistance);
		}

		if (Objects.equals(ACTIVE_CAMERA_CHUNK_TICKETS, desiredRefs)) {
			return false;
		}

		Set<CameraChunkTicketKey> keys = new LinkedHashSet<>();
		keys.addAll(ACTIVE_CAMERA_CHUNK_TICKETS.keySet());
		keys.addAll(desiredRefs.keySet());
		for (CameraChunkTicketKey key : keys) {
			int current = ACTIVE_CAMERA_CHUNK_TICKETS.getOrDefault(key, 0);
			int desired = desiredRefs.getOrDefault(key, 0);
			if (current <= 0 && desired > 0) {
				ServerLevel level = server.getLevel(key.dimension());
				if (level != null) {
					level.getChunkSource().addTicketWithRadius(CAMERA_CHUNK_TICKET_TYPE, new ChunkPos(key.chunkLong()), key.radius());
				}
			} else if (current > 0 && desired <= 0) {
				ServerLevel level = server.getLevel(key.dimension());
				if (level != null) {
					level.getChunkSource().removeTicketWithRadius(CAMERA_CHUNK_TICKET_TYPE, new ChunkPos(key.chunkLong()), key.radius());
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
		for (CameraChunkTicketKey key : new ArrayList<>(ACTIVE_CAMERA_CHUNK_TICKETS.keySet())) {
			ServerLevel level = server.getLevel(key.dimension());
			if (level != null) {
				level.getChunkSource().removeTicketWithRadius(CAMERA_CHUNK_TICKET_TYPE, new ChunkPos(key.chunkLong()), key.radius());
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
		Set<ChunkTicketKey> trackedChunks = collectTrackedShadowChunks(desiredStates.values());
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
		if (trackedChunks.isEmpty()) {
			DIRTY_SHADOW_CHUNKS.clear();
			return;
		}
		DIRTY_SHADOW_CHUNKS.removeIf(key -> key == null || !trackedChunks.contains(key));
	}

	private static Set<ChunkTicketKey> collectTrackedShadowChunks(Iterable<ShadowDesiredState> desiredStates) {
		if (desiredStates == null) {
			return Set.of();
		}
		Set<ChunkTicketKey> tracked = new HashSet<>();
		for (ShadowDesiredState desiredState : desiredStates) {
			if (desiredState == null || desiredState.level() == null) {
				continue;
			}
			ResourceKey<Level> dimension = desiredState.level().dimension();
			LongIterator iterator = desiredState.trackedChunks().iterator();
			while (iterator.hasNext()) {
				tracked.add(new ChunkTicketKey(dimension, iterator.nextLong()));
			}
		}
		return tracked;
	}

	private static void syncShadowChunkTickets(MinecraftServer server, Iterable<ShadowDesiredState> desiredStates) {
		Map<CameraChunkTicketKey, Integer> desiredRefs = new HashMap<>();
		if (server == null) {
			ACTIVE_CAMERA_CHUNK_TICKETS.clear();
			return;
		}
		if (desiredStates != null) {
			for (ShadowDesiredState desiredState : desiredStates) {
				if (desiredState == null || desiredState.level() == null) {
					continue;
				}
				for (Map.Entry<CameraChunkTicketKey, Integer> entry : desiredState.chunkTickets().entrySet()) {
					CameraChunkTicketKey key = entry.getKey();
					int refs = entry.getValue() == null ? 0 : entry.getValue();
					if (key == null || refs <= 0) {
						continue;
					}
					desiredRefs.merge(key, refs, Integer::sum);
				}
			}
		}
		if (Objects.equals(ACTIVE_CAMERA_CHUNK_TICKETS, desiredRefs)) {
			return;
		}
		Set<CameraChunkTicketKey> keys = new LinkedHashSet<>();
		keys.addAll(ACTIVE_CAMERA_CHUNK_TICKETS.keySet());
		keys.addAll(desiredRefs.keySet());
		for (CameraChunkTicketKey key : keys) {
			int current = ACTIVE_CAMERA_CHUNK_TICKETS.getOrDefault(key, 0);
			int desired = desiredRefs.getOrDefault(key, 0);
			ServerLevel level = server.getLevel(key.dimension());
			if (level == null) {
				continue;
			}
			if (current <= 0 && desired > 0) {
				level.getChunkSource().addTicketWithRadius(CAMERA_CHUNK_TICKET_TYPE, new ChunkPos(key.chunkLong()), key.radius());
			} else if (current > 0 && desired <= 0) {
				level.getChunkSource().removeTicketWithRadius(CAMERA_CHUNK_TICKET_TYPE, new ChunkPos(key.chunkLong()), key.radius());
			}
		}
		ACTIVE_CAMERA_CHUNK_TICKETS.clear();
		ACTIVE_CAMERA_CHUNK_TICKETS.putAll(desiredRefs);
	}

	private static void addCameraChunkTicket(
			Map<CameraChunkTicketKey, Integer> desiredRefs,
			ResourceKey<Level> dimension,
			ChunkPos center,
			int radius
	) {
		if (desiredRefs == null || dimension == null || center == null) {
			return;
		}
		int clampedRadius = Mth.clamp(radius, 2, 32);
		desiredRefs.merge(new CameraChunkTicketKey(dimension, center.toLong(), clampedRadius), 1, Integer::sum);
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
			accumulateShadowDesiredState(
					desiredStates,
					botUuid,
					stream.spec().renderSessionId(),
					target,
					viewDistance,
					spec.hiddenEntityUuids(),
					spec.omnidirectionalChunkLoading(),
					spec.cameraPos() != null && spec.followEntityUuid() == null
			);
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
			accumulateShadowDesiredState(desiredStates, botUuid, capture.renderSessionId(), target, viewDistance, Set.of(), false, false);
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
			accumulateShadowDesiredState(desiredStates, botUuid, recording.renderSessionId(), target, viewDistance, Set.of(), false, false);
		}

		if (server.getPlayerList() != null) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player == null
						|| !player.isAlive()
						|| DroneSystem.isCameraBlockedByDroneControl(player)
						|| !(player.level() instanceof ServerLevel playerLevel)
						|| !playerHasCameraInActiveSlot(player)) {
					continue;
				}
				accumulateShadowDesiredState(
						desiredStates,
						botUuid,
						resolveRenderSessionId(playerLevel.dimension(), null, player.getX(), player.getZ(), player.getUUID()),
						new ScheduledServiceTarget(
								playerLevel,
								player.getX(),
								player.getEyeY(),
								player.getZ(),
								player.getYRot(),
								player.getXRot(),
								player
						),
						viewDistance,
						Set.of(),
						true,
						false
				);
			}
		}
		return desiredStates;
	}

	private static void accumulateShadowDesiredState(
			Map<ShadowSyncKey, ShadowDesiredState> desiredStates,
			UUID botUuid,
			UUID sessionId,
			ScheduledServiceTarget target,
			int viewDistance,
			Set<UUID> hiddenEntityUuids,
			boolean omnidirectionalChunkLoading,
			boolean staticCameraChunkBuffer
	) {
		if (desiredStates == null || botUuid == null || sessionId == null || target == null || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		ShadowSyncKey key = new ShadowSyncKey(botUuid, sessionId);
		ShadowDesiredState desiredState = desiredStates.get(key);
		if (desiredState == null) {
			desiredState = new ShadowDesiredState(sessionId, level);
			desiredStates.put(key, desiredState);
		}
		if (desiredState.level() != level) {
			return;
		}
		desiredState.addTarget(
				target,
				viewDistance,
				hiddenEntityUuids == null ? Set.of() : Set.copyOf(hiddenEntityUuids),
				omnidirectionalChunkLoading,
				staticCameraChunkBuffer
		);
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
		ServerLevel level = desiredState != null ? desiredState.level() : null;
		if (level == null || desiredState == null || desiredState.trackedChunks().isEmpty()) {
			return new AABB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
		}
		return new AABB(
				desiredState.minChunkX() * 16.0D,
				level.getMinY(),
				desiredState.minChunkZ() * 16.0D,
				desiredState.maxChunkX() * 16.0D + 16.0D,
				level.getMaxY() + 1.0D,
				desiredState.maxChunkZ() * 16.0D + 16.0D
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
		if (desiredState.hiddenEntityUuids().contains(entity.getUUID())) {
			return false;
		}
		if (entity instanceof ServerPlayer player && RendererBotPresenceSystem.isRendererBot(player)) {
			return false;
		}
		double entityRangeBlocks = Math.max(16.0D, entity.getType().clientTrackingRange() * 16.0D);
		double entityRangeSq = entityRangeBlocks * entityRangeBlocks;
		for (ScheduledServiceTarget target : desiredState.targets()) {
			if (target == null || target.level() != desiredState.level()) {
				continue;
			}
			double dx = entity.getX() - target.x();
			double dz = entity.getZ() - target.z();
			if (dx * dx + dz * dz <= entityRangeSq) {
				return true;
			}
		}
		return false;
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

	private record CameraChunkTicketKey(ResourceKey<Level> dimension, long chunkLong, int radius) {
	}

	private record ShadowSyncKey(UUID botUuid, UUID sessionId) {
	}

	private static final class ShadowDesiredState {
		private final UUID sessionId;
		private final ServerLevel level;
		private final List<ScheduledServiceTarget> targets = new ArrayList<>();
		private final Set<UUID> hiddenEntityUuids = new HashSet<>();
		private final LongOpenHashSet trackedChunks = new LongOpenHashSet();
		private final Map<CameraChunkTicketKey, Integer> chunkTickets = new HashMap<>();
		private int requestedViewDistance;
		private int viewDistance = 2;
		private int centerChunkX;
		private int centerChunkZ;
		private int minChunkX;
		private int minChunkZ;
		private int maxChunkX;
		private int maxChunkZ;

		private ShadowDesiredState(UUID sessionId, ServerLevel level) {
			this.sessionId = sessionId;
			this.level = level;
			this.centerChunkX = 0;
			this.centerChunkZ = 0;
			this.minChunkX = 0;
			this.minChunkZ = 0;
			this.maxChunkX = 0;
			this.maxChunkZ = 0;
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

		private List<ScheduledServiceTarget> targets() {
			return this.targets;
		}

		private Set<UUID> hiddenEntityUuids() {
			return this.hiddenEntityUuids;
		}

		private LongOpenHashSet trackedChunks() {
			return this.trackedChunks;
		}

		private Map<CameraChunkTicketKey, Integer> chunkTickets() {
			return this.chunkTickets;
		}

		private int minChunkX() {
			return this.minChunkX;
		}

		private int minChunkZ() {
			return this.minChunkZ;
		}

		private int maxChunkX() {
			return this.maxChunkX;
		}

		private int maxChunkZ() {
			return this.maxChunkZ;
		}

		private void addTarget(
				ScheduledServiceTarget target,
				int viewDistance,
				Set<UUID> hiddenEntityUuids,
				boolean omnidirectionalChunkLoading,
				boolean staticCameraChunkBuffer
		) {
			if (target == null || target.level() != this.level) {
				return;
			}
			this.targets.add(target);
			this.requestedViewDistance = Math.max(this.requestedViewDistance, Math.max(2, viewDistance));
			if (hiddenEntityUuids != null && !hiddenEntityUuids.isEmpty()) {
				this.hiddenEntityUuids.addAll(hiddenEntityUuids);
			}
			appendVirtualTargetChunks(
					this.trackedChunks,
					this.level,
					target,
					Math.max(2, viewDistance),
					omnidirectionalChunkLoading,
					staticCameraChunkBuffer
			);
			addCameraChunkTicket(
					this.chunkTickets,
					this.level.dimension(),
					chunkPosAt(target.x(), target.z()),
					Math.max(2, viewDistance)
			);
			recomputeViewWindow(target);
		}

		private void recomputeViewWindow(ScheduledServiceTarget fallbackTarget) {
			if (this.trackedChunks.isEmpty()) {
				ScheduledServiceTarget target = fallbackTarget;
				if (target != null) {
					this.centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(target.x()));
					this.centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(target.z()));
				}
				this.minChunkX = this.centerChunkX;
				this.minChunkZ = this.centerChunkZ;
				this.maxChunkX = this.centerChunkX;
				this.maxChunkZ = this.centerChunkZ;
				this.viewDistance = Math.max(2, this.requestedViewDistance);
				return;
			}

			int minX = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			LongIterator iterator = this.trackedChunks.iterator();
			while (iterator.hasNext()) {
				ChunkPos pos = new ChunkPos(iterator.nextLong());
				minX = Math.min(minX, pos.x);
				minZ = Math.min(minZ, pos.z);
				maxX = Math.max(maxX, pos.x);
				maxZ = Math.max(maxZ, pos.z);
			}
			this.minChunkX = minX;
			this.minChunkZ = minZ;
			this.maxChunkX = maxX;
			this.maxChunkZ = maxZ;
			this.centerChunkX = Math.floorDiv(minX + maxX, 2);
			this.centerChunkZ = Math.floorDiv(minZ + maxZ, 2);

			int requiredRadius = 0;
			iterator = this.trackedChunks.iterator();
			while (iterator.hasNext()) {
				ChunkPos pos = new ChunkPos(iterator.nextLong());
				requiredRadius = Math.max(
						requiredRadius,
						Math.max(Math.abs(pos.x - this.centerChunkX), Math.abs(pos.z - this.centerChunkZ))
				);
			}
			this.viewDistance = Mth.clamp(Math.max(this.requestedViewDistance, requiredRadius), 2, 32);
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
		private final UUID renderSessionId;
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
				UUID renderSessionId,
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
			this.renderSessionId = renderSessionId;
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

		private UUID renderSessionId() {
			return this.renderSessionId;
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
		private final UUID renderSessionId;
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
				UUID renderSessionId,
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
			this.renderSessionId = renderSessionId;
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

		private UUID renderSessionId() {
			return this.renderSessionId;
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
			UUID renderSessionId,
			ResourceKey<Level> dimension,
			BlockPos cameraPos,
			double expectedX,
			double expectedY,
			double expectedZ,
			float expectedYaw,
			float expectedPitch,
			UUID followEntityUuid,
			Set<UUID> hiddenEntityUuids,
			boolean omnidirectionalChunkLoading,
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

		private long lastActivityAtMillis() {
			long referenceTime = this.startedAtMillis;
			if (this.lastDispatchAtMillis > referenceTime) {
				referenceTime = this.lastDispatchAtMillis;
			}
			if (this.lastFrameAtMillis > referenceTime) {
				referenceTime = this.lastFrameAtMillis;
			}
			return referenceTime;
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
