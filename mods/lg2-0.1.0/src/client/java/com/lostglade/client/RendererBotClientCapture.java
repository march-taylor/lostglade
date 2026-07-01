package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.network.RendererBotPayloads;
import com.lostglade.server.CameraMediaCache;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RendererBotClientCapture {
	private static final Object LOCK = new Object();
	private static final long LOCAL_CAPTURE_TIMEOUT_MS = Long.getLong("lg2.rendererBotLocalCaptureTimeoutMs", 8_000L);
	private static final long RECENT_FRAME_TTL_MS = Long.getLong("lg2.rendererBotRecentFrameTtlMs", 175L);
	private static final int DEFAULT_WARMUP_FRAMES = Math.max(1, Integer.getInteger("lg2.rendererBotWarmupFrames", 2));
	private static final int CAPTURE_THREADS = Math.max(1, Math.min(
			Integer.getInteger("lg2.rendererBotCaptureThreads", recommendedCaptureThreads()),
			recommendedCaptureThreads()
	));
	private static final double TARGET_RENDER_SCALE = Math.max(1.0D, doubleProperty("lg2.rendererBotPhotoRenderScale", 2.5D));
	private static final int MIN_RENDER_WIDTH = Math.max(128, Integer.getInteger("lg2.rendererBotMinRenderWidth", 1024));
	private static final int MIN_RENDER_HEIGHT = Math.max(128, Integer.getInteger("lg2.rendererBotMinRenderHeight", 768));
	private static final int MAX_RENDER_WIDTH = Math.max(MIN_RENDER_WIDTH, Integer.getInteger("lg2.rendererBotMaxRenderWidth", 3072));
	private static final int MAX_RENDER_HEIGHT = Math.max(MIN_RENDER_HEIGHT, Integer.getInteger("lg2.rendererBotMaxRenderHeight", 2048));
	private static final int MAX_LIVE_STREAM_FPS = 20;
	private static final long MAP_TILE_TIMEOUT_MS = Long.getLong("lg2.rendererBotMapTileTimeoutMs", 60_000L);
	private static final int MAP_TILE_WARMUP_FRAMES = Math.max(0, Integer.getInteger("lg2.rendererBotMapTileWarmupFrames", 8));
	private static final int MAP_TILE_BLANK_RETRY_WARMUP_FRAMES = Math.max(1, Integer.getInteger("lg2.rendererBotMapTileBlankRetryWarmupFrames", 4));
	private static final int MAP_TILE_MAX_BLANK_RETRIES = Math.max(0, Integer.getInteger("lg2.rendererBotMapTileMaxBlankRetries", 8));
	private static final boolean LIVE_STREAM_DITHERING = Boolean.getBoolean("lg2.rendererBotLiveStreamDithering");
	private static final int LIVE_STREAM_PARALLEL_PIXELS_THRESHOLD = Math.max(65_536, Integer.getInteger("lg2.rendererBotLiveParallelPixelsThreshold", 131_072));
	private static final ExecutorService CAPTURE_EXECUTOR = Executors.newFixedThreadPool(CAPTURE_THREADS, runnable -> {
		Thread thread = new Thread(runnable, "lg2-renderer-bot-capture");
		thread.setDaemon(true);
		thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
		return thread;
	});

	private static final Map<UUID, PendingCapture> PENDING_CAPTURES = new HashMap<>();
	private static final Map<UUID, LiveStreamSession> LIVE_STREAM_SESSIONS = new HashMap<>();
	private static final Map<UUID, PendingMapTile> PENDING_MAP_TILES = new HashMap<>();
	private static volatile CapturedFrame latestFrame;

	private RendererBotClientCapture() {
	}

	private static int recommendedCaptureThreads() {
		return Math.max(1, Math.min(2, Math.max(1, (Runtime.getRuntime().availableProcessors() - 1) / 2)));
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ClientPlayNetworking.send(new RendererBotPayloads.RendererBotHelloC2SPayload(RendererBotPayloads.PROTOCOL_VERSION))
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearAllSessions());
		ClientTickEvents.END_CLIENT_TICK.register(RendererBotClientCapture::onClientTick);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotCaptureRequestS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> beginCapture(payload, context.client()))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotLiveStreamStartS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> beginLiveStream(payload, context.client()))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotLiveStreamPoseS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> updateLiveStreamPose(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotLiveStreamStopS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> clearLiveStreamSession(payload.streamId()))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotMapTileRequestS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> beginMapTile(payload))
		);
	}

	private static void beginCapture(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, Minecraft client) {
		long now = System.currentTimeMillis();
		CapturedFrame cached = latestFrame;
		if (cached != null && cached.matches(payload) && cached.capturedAtMillis() + RECENT_FRAME_TTL_MS >= now) {
			Lg2.LOGGER.info("Renderer bot reusing hot cached frame for {}", payload.requestId());
			sendFrame(payload, cached.previewPixels(), cached.fullPixels());
			return;
		}

		int renderWidth = computeRenderWidth(payload);
		int renderHeight = computeRenderHeight(payload, renderWidth);
		int warmupFrames = DEFAULT_WARMUP_FRAMES;
		synchronized (LOCK) {
			PENDING_CAPTURES.put(payload.requestId(), new PendingCapture(payload, warmupFrames, now));
		}
		Lg2.LOGGER.info(
				"Renderer bot received capture request {} preview={}x{} full={}x{} render={}x{} warmup={}",
				payload.requestId(),
				payload.previewWidth(),
				payload.previewHeight(),
				payload.fullWidth(),
				payload.fullHeight(),
				renderWidth,
				renderHeight,
				warmupFrames
		);

	}

	private static void beginLiveStream(RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload, Minecraft client) {
		int renderWidth = computeLiveRenderWidth(payload);
		int renderHeight = computeLiveRenderHeight(payload, renderWidth);
		int warmupFrames = DEFAULT_WARMUP_FRAMES;
		synchronized (LOCK) {
			LIVE_STREAM_SESSIONS.put(payload.streamId(), new LiveStreamSession(
					payload,
					System.currentTimeMillis(),
					warmupFrames
			));
		}
		Lg2.LOGGER.info(
				"Renderer bot started live stream {} at {} fps {}x{} (render {}x{}, warmup={})",
				payload.streamId(),
				Math.clamp(Math.max(1, payload.targetFps()), 1, MAX_LIVE_STREAM_FPS),
				payload.fullWidth(),
				payload.fullHeight(),
				renderWidth,
				renderHeight,
				warmupFrames
		);
	}

	private static void updateLiveStreamPose(RendererBotPayloads.RendererBotLiveStreamPoseS2CPayload payload) {
		if (payload == null || payload.streamId() == null) {
			return;
		}
		synchronized (LOCK) {
			LiveStreamSession session = LIVE_STREAM_SESSIONS.get(payload.streamId());
			if (session != null) {
				session.updatePose(new LiveStreamPose(
						payload.x(),
						payload.y(),
						payload.z(),
						payload.yaw(),
						payload.pitch()
				));
			}
		}
	}

	private static void beginMapTile(RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload) {
		if (payload == null || payload.requestId() == null) {
			return;
		}
		synchronized (LOCK) {
			PENDING_MAP_TILES.put(payload.requestId(), new PendingMapTile(payload, System.currentTimeMillis()));
		}
	}

	private static void onClientTick(Minecraft client) {
		List<PendingCapture> captures;
		List<LiveStreamSession> liveStreams;
		List<PendingMapTile> mapTiles;
		synchronized (LOCK) {
			captures = new ArrayList<>(PENDING_CAPTURES.values());
			liveStreams = new ArrayList<>(LIVE_STREAM_SESSIONS.values());
			mapTiles = new ArrayList<>(PENDING_MAP_TILES.values());
		}
		long now = System.currentTimeMillis();
		for (PendingCapture capture : captures) {
			if (capture != null && !capture.screenshotRequested() && now - capture.requestStartedAt() >= LOCAL_CAPTURE_TIMEOUT_MS) {
				sendFailure(capture.payload(), "Renderer bot client did not produce a rendered frame in time");
				clearPendingCapture(capture.payload().requestId());
			}
		}
		for (LiveStreamSession liveStream : liveStreams) {
			if (liveStream == null || liveStream.frameInFlight()) {
				continue;
			}
			if (now - liveStream.startedAtMillis() >= LOCAL_CAPTURE_TIMEOUT_MS && liveStream.lastFrameAtNanos() == 0L) {
				sendLiveFailure(liveStream.payload(), "Renderer bot live stream did not produce a rendered frame in time");
				clearLiveStreamSession(liveStream.payload().streamId());
			}
		}
		for (PendingMapTile mapTile : mapTiles) {
			if (mapTile == null || mapTile.rendering()) {
				continue;
			}
			if (now - mapTile.requestStartedAt() >= MAP_TILE_TIMEOUT_MS) {
				sendMapTileFailure(mapTile.payload(), "Renderer bot map tile did not become ready in time");
				clearPendingMapTile(mapTile.payload().requestId());
			}
		}
		dispatchReadyMapTileRender(client);
		dispatchReadyRenders(client, System.nanoTime());
	}

	private static void dispatchReadyMapTileRender(Minecraft client) {
		if (client == null || client.level == null || RendererBotOffscreenWorldRenderer.isOffscreenRenderActive()) {
			return;
		}
		PendingMapTile selected = null;
		synchronized (LOCK) {
			int bestPriorityScore = Integer.MIN_VALUE;
			boolean bestActiveView = false;
			for (PendingMapTile candidate : PENDING_MAP_TILES.values()) {
				if (candidate == null || candidate.rendering()) {
					continue;
				}
				RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload = candidate.payload();
				if (payload.priorityScore() > bestPriorityScore
						|| (payload.priorityScore() == bestPriorityScore && payload.activeView() && !bestActiveView)) {
					bestPriorityScore = payload.priorityScore();
					bestActiveView = payload.activeView();
				}
			}
			if (bestPriorityScore == Integer.MIN_VALUE) {
				return;
			}
			for (PendingMapTile candidate : PENDING_MAP_TILES.values()) {
				if (candidate == null || candidate.rendering()) {
					continue;
				}
				RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload = candidate.payload();
				if (payload.priorityScore() != bestPriorityScore || payload.activeView() != bestActiveView) {
					continue;
				}
				RendererBotTopDownMapRenderer.TileRequest request = mapTileRenderRequest(payload);
				if (!RendererBotTopDownMapRenderer.hasRequiredChunks(client, request)) {
					continue;
				}
				if (selected == null || candidate.requestStartedAt() < selected.requestStartedAt()) {
					selected = candidate;
				}
			}
			if (selected == null) {
				return;
			}
			selected.markRendering();
		}
		if (selected == null) {
			return;
		}
		dispatchTopDownMapTile(client, selected);
	}

	private static void dispatchTopDownMapTile(Minecraft client, PendingMapTile mapTile) {
		RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload = mapTile.payload();
		try {
			if (mapTile.remainingWarmupFrames() > 0) {
				boolean warmed = RendererBotTopDownMapRenderer.renderToTarget(client, mapTileRenderRequest(payload), ignored -> {
				});
				if (warmed) {
					mapTile.decrementWarmupFrames();
				}
				clearPendingMapTileRendering(payload.requestId());
				return;
			}
			boolean rendered = RendererBotTopDownMapRenderer.renderToTarget(client, mapTileRenderRequest(payload), renderTarget -> {
				try {
					CompletableFuture<byte[]> pixelsFuture = takeScreenshotFuture(renderTarget).thenApplyAsync(image -> {
						try (image) {
							int[] sourcePixels = image.makePixelArray();
							if (image.getWidth() == payload.tileSize() && image.getHeight() == payload.tileSize()) {
								return encodeExactRgbFrame(sourcePixels, image.getWidth(), image.getHeight());
							}
							return encodeNearestRgbFrame(sourcePixels, image.getWidth(), image.getHeight(), payload.tileSize(), payload.tileSize());
						}
					}, CAPTURE_EXECUTOR);
					pixelsFuture.whenComplete((pixels, throwable) -> client.execute(() -> {
						if (throwable != null) {
							sendMapTileFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
							clearPendingMapTile(payload.requestId());
							return;
						}
						if (isProbablyBlankMapTile(pixels)) {
							if (retryPendingMapTileAfterBlank(payload.requestId())) {
								Lg2.LOGGER.debug("Renderer bot map tile {} lod {} {},{} was still blank; warming up vanilla sections again",
										payload.requestId(), payload.lod(), payload.tileX(), payload.tileZ());
								return;
							}
							sendMapTileFailure(payload, "Renderer bot vanilla top-down tile stayed blank after warmup");
							clearPendingMapTile(payload.requestId());
							return;
						}
						ClientPlayNetworking.send(new RendererBotPayloads.RendererBotMapTileC2SPayload(
								payload.requestId(),
								payload.lod(),
								payload.tileX(),
								payload.tileZ(),
								System.nanoTime(),
								pixels
						));
						clearPendingMapTile(payload.requestId());
					}));
				} catch (Throwable throwable) {
					client.execute(() -> {
						sendMapTileFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
						clearPendingMapTile(payload.requestId());
					});
				}
			});
			if (!rendered) {
				clearPendingMapTileRendering(payload.requestId());
			}
		} catch (Throwable throwable) {
			sendMapTileFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
			clearPendingMapTile(payload.requestId());
		}
	}

	private static void dispatchReadyRenders(Minecraft client, long nowNanos) {
		if (client == null || client.level == null || RendererBotOffscreenWorldRenderer.isOffscreenRenderActive()) {
			return;
		}
		List<PendingCapture> capturesToRender = new ArrayList<>();
		List<LiveStreamSession> liveStreamsToRender = new ArrayList<>();
		synchronized (LOCK) {
			for (PendingCapture capture : PENDING_CAPTURES.values()) {
				if (capture == null || capture.screenshotRequested()) {
					continue;
				}
				if (capture.remainingWarmupFrames() > 0) {
					capture.decrementWarmupFrames();
					continue;
				}
				capturesToRender.add(capture);
			}
			for (LiveStreamSession liveStream : LIVE_STREAM_SESSIONS.values()) {
				if (liveStream == null || liveStream.frameInFlight()) {
					continue;
				}
				if (liveStream.remainingWarmupFrames() > 0) {
					liveStream.decrementWarmupFrames();
					continue;
				}
				long intervalNanos = 1_000_000_000L / Math.clamp(Math.max(1, liveStream.payload().targetFps()), 1, MAX_LIVE_STREAM_FPS);
				if (liveStream.lastFrameAtNanos() != 0L && nowNanos - liveStream.lastFrameAtNanos() < intervalNanos) {
					continue;
				}
				liveStreamsToRender.add(liveStream);
			}
		}
		for (PendingCapture capture : capturesToRender) {
			if (!markPendingCaptureRequested(capture.payload().requestId())) {
				continue;
			}
			boolean rendered = RendererBotGpuCaptureBackend.isAvailable()
					? RendererBotOffscreenWorldRenderer.renderToTarget(
							client,
							captureRenderRequest(capture.payload()),
							renderTarget -> dispatchGpuCapture(client, capture, renderTarget)
					)
					: RendererBotOffscreenWorldRenderer.render(
							client,
							captureRenderRequest(capture.payload()),
							image -> CAPTURE_EXECUTOR.submit(() -> processSharedFrame(client, List.of(capture), List.of(), image))
					);
			if (!rendered) {
				clearPendingCaptureRequested(capture.payload().requestId());
			}
		}
		for (LiveStreamSession liveStream : liveStreamsToRender) {
			if (!markLiveStreamFrameInFlight(liveStream.payload().streamId(), nowNanos)) {
				continue;
			}
			boolean rendered = RendererBotGpuCaptureBackend.isAvailable()
					? RendererBotOffscreenWorldRenderer.renderToTarget(
							client,
							liveRenderRequest(liveStream),
							renderTarget -> dispatchGpuLiveStream(client, liveStream, renderTarget)
					)
					: RendererBotOffscreenWorldRenderer.render(
							client,
							liveRenderRequest(liveStream),
							image -> CAPTURE_EXECUTOR.submit(() -> processSharedFrame(client, List.of(), List.of(liveStream), image))
					);
			if (!rendered) {
				clearLiveStreamFrameInFlight(liveStream.payload().streamId());
			}
		}
	}

	private static void dispatchGpuCapture(Minecraft client, PendingCapture capture, RenderTarget renderTarget) {
		RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload = capture.payload();
		try {
			CompletableFuture<byte[]> previewFuture = RendererBotGpuCaptureBackend.captureQuantizedFrame(
					renderTarget,
					payload.previewWidth(),
					payload.previewHeight(),
					true
			);
			CompletableFuture<byte[]> fullFuture = payload.previewWidth() == payload.fullWidth() && payload.previewHeight() == payload.fullHeight()
					? previewFuture
					: RendererBotGpuCaptureBackend.captureQuantizedFrame(
							renderTarget,
							payload.fullWidth(),
							payload.fullHeight(),
							true
					);
			CompletableFuture<NativeImage> sourceFuture = takeScreenshotFuture(renderTarget);
			CompletableFuture.allOf(previewFuture, fullFuture, sourceFuture).whenComplete((ignored, throwable) -> {
				if (throwable != null) {
					handleGpuCaptureFailure(client, capture, sourceFuture, throwable);
					return;
				}
				CAPTURE_EXECUTOR.submit(() -> completeGpuCapture(client, payload, sourceFuture, previewFuture, fullFuture));
			});
		} catch (Throwable throwable) {
			Lg2.LOGGER.warn("Renderer bot GPU capture path failed before completion, falling back to CPU capture for {}", payload.requestId(), throwable);
			Screenshot.takeScreenshot(renderTarget, image -> CAPTURE_EXECUTOR.submit(() -> processSharedFrame(client, List.of(capture), List.of(), image)));
		}
	}

	private static void dispatchGpuLiveStream(Minecraft client, LiveStreamSession liveStream, RenderTarget renderTarget) {
		RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload = liveStream.payload();
		try {
			RendererBotGpuCaptureBackend.captureQuantizedFrame(
					renderTarget,
					payload.fullWidth(),
					payload.fullHeight(),
					LIVE_STREAM_DITHERING
			).whenComplete((fullPixels, throwable) -> client.execute(() -> {
				if (throwable != null) {
					Lg2.LOGGER.warn("Renderer bot GPU live stream path failed for {}", payload.streamId(), throwable);
					sendLiveFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
					clearLiveStreamSession(payload.streamId());
					return;
				}
				ClientPlayNetworking.send(new RendererBotPayloads.RendererBotLiveFrameC2SPayload(payload.streamId(), liveStream.lastFrameAtNanos(), fullPixels));
				clearLiveStreamFrameInFlight(payload.streamId());
			}));
		} catch (Throwable throwable) {
			Lg2.LOGGER.warn("Renderer bot GPU live stream path failed before completion, falling back to CPU capture for {}", payload.streamId(), throwable);
			Screenshot.takeScreenshot(renderTarget, image -> CAPTURE_EXECUTOR.submit(() -> processSharedFrame(client, List.of(), List.of(liveStream), image)));
		}
	}

	private static void handleGpuCaptureFailure(
			Minecraft client,
			PendingCapture capture,
			CompletableFuture<NativeImage> sourceFuture,
			Throwable throwable
	) {
		if (sourceFuture.isDone() && !sourceFuture.isCompletedExceptionally() && !sourceFuture.isCancelled()) {
			CAPTURE_EXECUTOR.submit(() -> {
				try {
					processSharedFrame(client, List.of(capture), List.of(), sourceFuture.join());
				} catch (Throwable fallbackThrowable) {
					client.execute(() -> {
						sendFailure(capture.payload(), fallbackThrowable.getMessage() != null ? fallbackThrowable.getMessage() : fallbackThrowable.getClass().getSimpleName());
						clearPendingCapture(capture.payload().requestId());
					});
					Lg2.LOGGER.error("Renderer bot GPU capture fallback failed for {}", capture.payload().requestId(), fallbackThrowable);
				}
			});
			return;
		}
		sourceFuture.thenAccept(image -> {
			if (image != null) {
				image.close();
			}
		});
		client.execute(() -> {
			sendFailure(capture.payload(), throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
			clearPendingCapture(capture.payload().requestId());
		});
		Lg2.LOGGER.error("Renderer bot GPU capture failed for {}", capture.payload().requestId(), throwable);
	}

	private static void completeGpuCapture(
			Minecraft client,
			RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload,
			CompletableFuture<NativeImage> sourceFuture,
			CompletableFuture<byte[]> previewFuture,
			CompletableFuture<byte[]> fullFuture
	) {
		try (NativeImage sourceImage = sourceFuture.join()) {
			persistPhotoSource(payload, sourceImage);
			byte[] previewPixels = previewFuture.join();
			byte[] fullPixels = fullFuture.join();
			latestFrame = new CapturedFrame(
					payload.dimensionId(),
					payload.followEntityUuid(),
					payload.expectedX(),
					payload.expectedY(),
					payload.expectedZ(),
					payload.expectedYaw(),
					payload.expectedPitch(),
					payload.previewWidth(),
					payload.previewHeight(),
					payload.fullWidth(),
					payload.fullHeight(),
					payload.fovDegrees(),
					previewPixels,
					fullPixels,
					System.currentTimeMillis()
			);
			client.execute(() -> {
				sendFrame(payload, previewPixels, fullPixels);
				clearPendingCapture(payload.requestId());
			});
		} catch (Throwable throwable) {
			client.execute(() -> {
				sendFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
				clearPendingCapture(payload.requestId());
			});
			Lg2.LOGGER.error("Renderer bot GPU capture completion failed for {}", payload.requestId(), throwable);
		}
	}

	private static CompletableFuture<NativeImage> takeScreenshotFuture(RenderTarget renderTarget) {
		CompletableFuture<NativeImage> future = new CompletableFuture<>();
		try {
			Screenshot.takeScreenshot(renderTarget, future::complete);
		} catch (Throwable throwable) {
			future.completeExceptionally(throwable);
		}
		return future;
	}

	private static void processSharedFrame(Minecraft client, List<PendingCapture> captures, List<LiveStreamSession> liveStreams, NativeImage image) {
		try {
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = image.makePixelArray();

			for (PendingCapture capture : captures) {
				RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload = capture.payload();
				persistPhotoSource(payload, image);
				CompletableFuture<byte[]> previewFuture = CompletableFuture.supplyAsync(
						() -> quantizeFrame(pixels, width, height, payload.previewWidth(), payload.previewHeight()),
						CAPTURE_EXECUTOR
				);
				CompletableFuture<byte[]> fullFuture = payload.previewWidth() == payload.fullWidth() && payload.previewHeight() == payload.fullHeight()
						? previewFuture.thenApply(bytes -> bytes)
						: CompletableFuture.supplyAsync(
								() -> quantizeFrame(pixels, width, height, payload.fullWidth(), payload.fullHeight()),
								CAPTURE_EXECUTOR
						);
				byte[] previewPixels = previewFuture.join();
				byte[] fullPixels = fullFuture.join();
				latestFrame = new CapturedFrame(
						payload.dimensionId(),
						payload.followEntityUuid(),
						payload.expectedX(),
						payload.expectedY(),
						payload.expectedZ(),
						payload.expectedYaw(),
						payload.expectedPitch(),
						payload.previewWidth(),
						payload.previewHeight(),
						payload.fullWidth(),
						payload.fullHeight(),
						payload.fovDegrees(),
						previewPixels,
						fullPixels,
						System.currentTimeMillis()
				);
				client.execute(() -> {
					sendFrame(payload, previewPixels, fullPixels);
					clearPendingCapture(payload.requestId());
				});
			}

			for (LiveStreamSession session : liveStreams) {
				RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload = session.payload();
				byte[] fullPixels = quantizeLiveFrame(pixels, width, height, payload.fullWidth(), payload.fullHeight());
				client.execute(() -> {
					ClientPlayNetworking.send(new RendererBotPayloads.RendererBotLiveFrameC2SPayload(payload.streamId(), session.lastFrameAtNanos(), fullPixels));
					clearLiveStreamFrameInFlight(payload.streamId());
				});
			}
		} catch (Throwable throwable) {
			client.execute(() -> {
				for (PendingCapture capture : captures) {
					RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload = capture.payload();
					sendFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
					clearPendingCapture(payload.requestId());
				}
				for (LiveStreamSession session : liveStreams) {
					RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload = session.payload();
					sendLiveFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName());
					clearLiveStreamSession(payload.streamId());
				}
			});
			Lg2.LOGGER.error("Renderer bot shared capture failed", throwable);
		} finally {
			image.close();
		}
	}

	private static void persistPhotoSource(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, NativeImage image) throws Exception {
		if (payload == null || image == null) {
			return;
		}
		String sourceKey = payload.requestId().toString();
		CameraMediaCache.ensurePhotoParent(sourceKey);
		image.writeToFile(CameraMediaCache.photoSourcePath(sourceKey));
	}

	private static byte[] quantizeFrame(int[] sourcePixels, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
		if (sourceWidth == outputWidth && sourceHeight == outputHeight) {
			return quantizeExactFrame(sourcePixels, outputWidth, outputHeight);
		}
		return quantizeScaledFrame(sourcePixels, sourceWidth, sourceHeight, outputWidth, outputHeight);
	}

	private static byte[] quantizeLiveFrame(int[] sourcePixels, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
		boolean dither = LIVE_STREAM_DITHERING;
		if (sourceWidth == outputWidth && sourceHeight == outputHeight) {
			return quantizeExactFrame(sourcePixels, outputWidth, outputHeight, dither, true);
		}
		return quantizeScaledFrame(sourcePixels, sourceWidth, sourceHeight, outputWidth, outputHeight, dither, true);
	}

	private static byte[] quantizeExactFrame(int[] sourcePixels, int width, int height) {
		return quantizeExactFrame(sourcePixels, width, height, true, false);
	}

	private static byte[] quantizeExactFrame(int[] sourcePixels, int width, int height, boolean dither, boolean allowParallel) {
		byte[] output = new byte[Math.max(1, width * height)];
		if (allowParallel && width * height >= LIVE_STREAM_PARALLEL_PIXELS_THRESHOLD) {
			parallelQuantizeRows(sourcePixels, width, height, output, dither);
			return output;
		}
		for (int y = 0; y < height; y++) {
			int rowStart = y * width;
			for (int x = 0; x < width; x++) {
				int rgb = sourcePixels[rowStart + x] & 0xFFFFFF;
				output[rowStart + x] = dither
						? MapPaletteQuantizer.quantizeDithered(rgb, x, y)
						: MapPaletteQuantizer.quantize(rgb);
			}
		}
		return output;
	}

	private static byte[] quantizeScaledFrame(int[] sourcePixels, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
		return quantizeScaledFrame(sourcePixels, sourceWidth, sourceHeight, outputWidth, outputHeight, true, false);
	}

	private static byte[] encodeExactRgbFrame(int[] sourcePixels, int width, int height) {
		byte[] output = new byte[Math.max(1, width * height * 3)];
		for (int i = 0; i < width * height; i++) {
			writeRgb(output, i, sourcePixels[i] & 0xFFFFFF);
		}
		return output;
	}

	private static byte[] encodeNearestRgbFrame(int[] sourcePixels, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
		byte[] output = new byte[Math.max(1, outputWidth * outputHeight * 3)];
		for (int y = 0; y < outputHeight; y++) {
			int sourceY = Mth.clamp((int) Math.floor(((y + 0.5D) * sourceHeight) / Math.max(1.0D, outputHeight)), 0, sourceHeight - 1);
			for (int x = 0; x < outputWidth; x++) {
				int sourceX = Mth.clamp((int) Math.floor(((x + 0.5D) * sourceWidth) / Math.max(1.0D, outputWidth)), 0, sourceWidth - 1);
				writeRgb(output, y * outputWidth + x, sourcePixels[sourceY * sourceWidth + sourceX] & 0xFFFFFF);
			}
		}
		return output;
	}

	private static void writeRgb(byte[] output, int pixelIndex, int rgb) {
		int offset = pixelIndex * 3;
		output[offset] = (byte) ((rgb >> 16) & 0xFF);
		output[offset + 1] = (byte) ((rgb >> 8) & 0xFF);
		output[offset + 2] = (byte) (rgb & 0xFF);
	}

	private static byte[] quantizeScaledFrame(int[] sourcePixels, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight, boolean dither, boolean allowParallel) {
		double sourceAspect = sourceWidth / (double) Math.max(1, sourceHeight);
		double targetAspect = outputWidth / (double) Math.max(1, outputHeight);
		double cropX = 0.0D;
		double cropY = 0.0D;
		double cropWidth = sourceWidth;
		double cropHeight = sourceHeight;
		if (sourceAspect > targetAspect) {
			cropWidth = sourceHeight * targetAspect;
			cropX = (sourceWidth - cropWidth) * 0.5D;
		} else if (sourceAspect < targetAspect) {
			cropHeight = sourceWidth / targetAspect;
			cropY = (sourceHeight - cropHeight) * 0.5D;
		}

		byte[] output = new byte[Math.max(1, outputWidth * outputHeight)];
		if (allowParallel && outputWidth * outputHeight >= LIVE_STREAM_PARALLEL_PIXELS_THRESHOLD) {
			parallelScaleQuantizeRows(sourcePixels, sourceWidth, sourceHeight, outputWidth, outputHeight, cropX, cropY, cropWidth, cropHeight, output, dither);
			return output;
		}
		double sampleSpanX = cropWidth / Math.max(1.0D, outputWidth);
		double sampleSpanY = cropHeight / Math.max(1.0D, outputHeight);
		for (int y = 0; y < outputHeight; y++) {
			double sampleY = cropY + ((y + 0.5D) / outputHeight) * cropHeight;
			for (int x = 0; x < outputWidth; x++) {
				double sampleX = cropX + ((x + 0.5D) / outputWidth) * cropWidth;
				int rgb = sampleScaledRgb(sourcePixels, sourceWidth, sourceHeight, sampleX, sampleY, sampleSpanX, sampleSpanY);
				output[y * outputWidth + x] = dither
						? MapPaletteQuantizer.quantizeDithered(rgb, x, y)
						: MapPaletteQuantizer.quantize(rgb);
			}
		}
		return output;
	}

	private static void parallelQuantizeRows(int[] sourcePixels, int width, int height, byte[] output, boolean dither) {
		int workerCount = Math.max(1, Math.min(Math.max(1, CAPTURE_THREADS - 1), height));
		if (workerCount <= 1) {
			quantizeRows(sourcePixels, width, output, 0, height, dither);
			return;
		}
		CompletableFuture<?>[] tasks = new CompletableFuture<?>[workerCount];
		for (int worker = 0; worker < workerCount; worker++) {
			int startRow = (height * worker) / workerCount;
			int endRow = (height * (worker + 1)) / workerCount;
			tasks[worker] = CompletableFuture.runAsync(
					() -> quantizeRows(sourcePixels, width, output, startRow, endRow, dither),
					CAPTURE_EXECUTOR
			);
		}
		CompletableFuture.allOf(tasks).join();
	}

	private static void quantizeRows(int[] sourcePixels, int width, byte[] output, int startRow, int endRow, boolean dither) {
		for (int y = startRow; y < endRow; y++) {
			int rowStart = y * width;
			for (int x = 0; x < width; x++) {
				int rgb = sourcePixels[rowStart + x] & 0xFFFFFF;
				output[rowStart + x] = dither
						? MapPaletteQuantizer.quantizeDithered(rgb, x, y)
						: MapPaletteQuantizer.quantize(rgb);
			}
		}
	}

	private static void parallelScaleQuantizeRows(
			int[] sourcePixels,
			int sourceWidth,
			int sourceHeight,
			int outputWidth,
			int outputHeight,
			double cropX,
			double cropY,
			double cropWidth,
			double cropHeight,
			byte[] output,
			boolean dither
	) {
		int workerCount = Math.max(1, Math.min(Math.max(1, CAPTURE_THREADS - 1), outputHeight));
		if (workerCount <= 1) {
			scaleQuantizeRows(sourcePixels, sourceWidth, sourceHeight, outputWidth, outputHeight, cropX, cropY, cropWidth, cropHeight, output, 0, outputHeight, dither);
			return;
		}
		CompletableFuture<?>[] tasks = new CompletableFuture<?>[workerCount];
		for (int worker = 0; worker < workerCount; worker++) {
			int startRow = (outputHeight * worker) / workerCount;
			int endRow = (outputHeight * (worker + 1)) / workerCount;
			tasks[worker] = CompletableFuture.runAsync(
					() -> scaleQuantizeRows(sourcePixels, sourceWidth, sourceHeight, outputWidth, outputHeight, cropX, cropY, cropWidth, cropHeight, output, startRow, endRow, dither),
					CAPTURE_EXECUTOR
			);
		}
		CompletableFuture.allOf(tasks).join();
	}

	private static void scaleQuantizeRows(
			int[] sourcePixels,
			int sourceWidth,
			int sourceHeight,
			int outputWidth,
			int outputHeight,
			double cropX,
			double cropY,
			double cropWidth,
			double cropHeight,
			byte[] output,
			int startRow,
			int endRow,
			boolean dither
	) {
		double sampleSpanX = cropWidth / Math.max(1.0D, outputWidth);
		double sampleSpanY = cropHeight / Math.max(1.0D, outputHeight);
		for (int y = startRow; y < endRow; y++) {
			double sampleY = cropY + ((y + 0.5D) / outputHeight) * cropHeight;
			for (int x = 0; x < outputWidth; x++) {
				double sampleX = cropX + ((x + 0.5D) / outputWidth) * cropWidth;
				int rgb = sampleScaledRgb(sourcePixels, sourceWidth, sourceHeight, sampleX, sampleY, sampleSpanX, sampleSpanY);
				output[y * outputWidth + x] = dither
						? MapPaletteQuantizer.quantizeDithered(rgb, x, y)
						: MapPaletteQuantizer.quantize(rgb);
			}
		}
	}

	private static int sampleScaledRgb(int[] sourcePixels, int sourceWidth, int sourceHeight, double sampleX, double sampleY, double sampleSpanX, double sampleSpanY) {
		if (sampleSpanX > 1.15D || sampleSpanY > 1.15D) {
			double offsetX = sampleSpanX * 0.25D;
			double offsetY = sampleSpanY * 0.25D;
			int rgbA = sampleBilinearRgb(sourcePixels, sourceWidth, sourceHeight, sampleX - offsetX, sampleY - offsetY);
			int rgbB = sampleBilinearRgb(sourcePixels, sourceWidth, sourceHeight, sampleX + offsetX, sampleY - offsetY);
			int rgbC = sampleBilinearRgb(sourcePixels, sourceWidth, sourceHeight, sampleX - offsetX, sampleY + offsetY);
			int rgbD = sampleBilinearRgb(sourcePixels, sourceWidth, sourceHeight, sampleX + offsetX, sampleY + offsetY);
			return averageRgb(rgbA, rgbB, rgbC, rgbD);
		}
		return sampleBilinearRgb(sourcePixels, sourceWidth, sourceHeight, sampleX, sampleY);
	}

	private static int sampleBilinearRgb(int[] sourcePixels, int sourceWidth, int sourceHeight, double sampleX, double sampleY) {
		double clampedX = Mth.clamp(sampleX, 0.0D, Math.max(0, sourceWidth - 1));
		double clampedY = Mth.clamp(sampleY, 0.0D, Math.max(0, sourceHeight - 1));
		int x0 = Mth.clamp((int) Math.floor(clampedX), 0, sourceWidth - 1);
		int y0 = Mth.clamp((int) Math.floor(clampedY), 0, sourceHeight - 1);
		int x1 = Math.min(x0 + 1, sourceWidth - 1);
		int y1 = Math.min(y0 + 1, sourceHeight - 1);
		double tx = clampedX - x0;
		double ty = clampedY - y0;

		int c00 = sourcePixels[y0 * sourceWidth + x0] & 0xFFFFFF;
		int c10 = sourcePixels[y0 * sourceWidth + x1] & 0xFFFFFF;
		int c01 = sourcePixels[y1 * sourceWidth + x0] & 0xFFFFFF;
		int c11 = sourcePixels[y1 * sourceWidth + x1] & 0xFFFFFF;

		int red = bilinearChannel((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, tx, ty);
		int green = bilinearChannel((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, tx, ty);
		int blue = bilinearChannel(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, tx, ty);
		return (red << 16) | (green << 8) | blue;
	}

	private static int bilinearChannel(int c00, int c10, int c01, int c11, double tx, double ty) {
		double top = c00 + (c10 - c00) * tx;
		double bottom = c01 + (c11 - c01) * tx;
		return Mth.clamp((int) Math.round(top + (bottom - top) * ty), 0, 255);
	}

	private static int averageRgb(int a, int b, int c, int d) {
		int red = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF) + ((c >> 16) & 0xFF) + ((d >> 16) & 0xFF) + 2) >> 2;
		int green = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF) + ((c >> 8) & 0xFF) + ((d >> 8) & 0xFF) + 2) >> 2;
		int blue = ((a & 0xFF) + (b & 0xFF) + (c & 0xFF) + (d & 0xFF) + 2) >> 2;
		return (red << 16) | (green << 8) | blue;
	}

	private static double doubleProperty(String key, double fallback) {
		String raw = System.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return Double.parseDouble(raw.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static void sendFrame(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, byte[] previewPixels, byte[] fullPixels) {
		ClientPlayNetworking.send(new RendererBotPayloads.RendererBotPreviewFrameC2SPayload(payload.requestId(), previewPixels));
		ClientPlayNetworking.send(new RendererBotPayloads.RendererBotFullFrameC2SPayload(payload.requestId(), fullPixels));
	}

	private static void sendLiveFailure(RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload, String message) {
		Lg2.LOGGER.warn("Renderer bot failing live stream {}: {}", payload.streamId(), message);
		ClientPlayNetworking.send(new RendererBotPayloads.RendererBotLiveStreamFailureC2SPayload(
				payload.streamId(),
				message == null || message.isBlank() ? "Renderer bot live stream failed" : message
		));
	}

	private static void clearPendingCapture(java.util.UUID requestId) {
		synchronized (LOCK) {
			if (requestId == null) {
				PENDING_CAPTURES.clear();
				return;
			}
			PENDING_CAPTURES.remove(requestId);
		}
	}

	private static boolean markPendingCaptureRequested(UUID requestId) {
		synchronized (LOCK) {
			PendingCapture capture = PENDING_CAPTURES.get(requestId);
			if (capture == null || capture.screenshotRequested()) {
				return false;
			}
			capture.markScreenshotRequested();
			return true;
		}
	}

	private static void clearPendingCaptureRequested(UUID requestId) {
		synchronized (LOCK) {
			PendingCapture capture = PENDING_CAPTURES.get(requestId);
			if (capture != null) {
				capture.clearScreenshotRequested();
			}
		}
	}

	private static void clearLiveStreamSession(UUID streamId) {
		synchronized (LOCK) {
			if (streamId == null) {
				LIVE_STREAM_SESSIONS.clear();
				return;
			}
			LIVE_STREAM_SESSIONS.remove(streamId);
		}
	}

	private static void clearLiveStreamFrameInFlight(UUID streamId) {
		synchronized (LOCK) {
			LiveStreamSession session = LIVE_STREAM_SESSIONS.get(streamId);
			if (session == null) {
				return;
			}
			session.clearFrameInFlight();
		}
	}

	private static void clearPendingMapTile(UUID requestId) {
		synchronized (LOCK) {
			if (requestId == null) {
				PENDING_MAP_TILES.clear();
				return;
			}
			PENDING_MAP_TILES.remove(requestId);
		}
	}

	private static void clearPendingMapTileRendering(UUID requestId) {
		synchronized (LOCK) {
			PendingMapTile pending = PENDING_MAP_TILES.get(requestId);
			if (pending != null) {
				pending.clearRendering();
			}
		}
	}

	private static boolean retryPendingMapTileAfterBlank(UUID requestId) {
		synchronized (LOCK) {
			PendingMapTile pending = PENDING_MAP_TILES.get(requestId);
			if (pending == null || !pending.recordBlankRetry()) {
				return false;
			}
			pending.resetWarmupFrames(MAP_TILE_BLANK_RETRY_WARMUP_FRAMES);
			pending.clearRendering();
			return true;
		}
	}

	private static boolean markLiveStreamFrameInFlight(UUID streamId, long nowNanos) {
		synchronized (LOCK) {
			LiveStreamSession session = LIVE_STREAM_SESSIONS.get(streamId);
			if (session == null || session.frameInFlight()) {
				return false;
			}
			session.markFrameInFlight(nowNanos);
			return true;
		}
	}

	private static void clearAllSessions() {
		synchronized (LOCK) {
			PENDING_CAPTURES.clear();
			LIVE_STREAM_SESSIONS.clear();
			PENDING_MAP_TILES.clear();
			latestFrame = null;
		}
		RendererBotOffscreenWorldRenderer.clearCaches();
	}

	private static void sendFailure(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, String message) {
		Lg2.LOGGER.warn("Renderer bot failing capture {}: {}", payload.requestId(), message);
		ClientPlayNetworking.send(new RendererBotPayloads.RendererBotCaptureFailureC2SPayload(
				payload.requestId(),
				message == null || message.isBlank() ? "Renderer bot capture failed" : message
		));
	}

	private static void sendMapTileFailure(RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload, String message) {
		Lg2.LOGGER.warn("Renderer bot failing map tile {} lod {} {},{}: {}", payload.requestId(), payload.lod(), payload.tileX(), payload.tileZ(), message);
		ClientPlayNetworking.send(new RendererBotPayloads.RendererBotMapTileFailureC2SPayload(
				payload.requestId(),
				message == null || message.isBlank() ? "Renderer bot map tile failed" : message
		));
	}

	private static int computeRenderWidth(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload) {
		int fullWidth = Math.max(1, payload.fullWidth());
		int fullHeight = Math.max(1, payload.fullHeight());
		double minScale = Math.max(MIN_RENDER_WIDTH / (double) fullWidth, MIN_RENDER_HEIGHT / (double) fullHeight);
		double maxScale = Math.min(MAX_RENDER_WIDTH / (double) fullWidth, MAX_RENDER_HEIGHT / (double) fullHeight);
		double scale = Math.max(1.0D, Math.min(maxScale, Math.max(minScale, TARGET_RENDER_SCALE)));
		return Mth.clamp((int) Math.round(fullWidth * scale), 1, MAX_RENDER_WIDTH);
	}

	private static int computeRenderHeight(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, int renderWidth) {
		double aspect = Math.max(1.0D, payload.fullHeight()) / (double) Math.max(1.0D, payload.fullWidth());
		return Mth.clamp((int) Math.round(renderWidth * aspect), 1, MAX_RENDER_HEIGHT);
	}

	private static int computeLiveRenderWidth(RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload) {
		return Mth.clamp(Math.max(1, payload.fullWidth()), 1, MAX_RENDER_WIDTH);
	}

	private static int computeLiveRenderHeight(RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload, int renderWidth) {
		double aspect = Math.max(1.0D, payload.fullHeight()) / (double) Math.max(1.0D, payload.fullWidth());
		return Mth.clamp((int) Math.round(renderWidth * aspect), 1, MAX_RENDER_HEIGHT);
	}

	private static RendererBotOffscreenWorldRenderer.RenderRequest captureRenderRequest(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload) {
		int renderWidth = computeRenderWidth(payload);
		int renderHeight = computeRenderHeight(payload, renderWidth);
		return new RendererBotOffscreenWorldRenderer.RenderRequest(
				payload.renderSessionId(),
				payload.dimensionId(),
				payload.followEntityUuid(),
				payload.expectedX(),
				payload.expectedY(),
				payload.expectedZ(),
				payload.expectedYaw(),
				payload.expectedPitch(),
				payload.fovDegrees(),
				renderWidth,
				renderHeight,
				false,
				false,
				0.0D,
				0.0D
		);
	}

	private static RendererBotOffscreenWorldRenderer.RenderRequest liveRenderRequest(LiveStreamSession session) {
		RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload = session.payload();
		LiveStreamPose pose = session.pose();
		int renderWidth = computeLiveRenderWidth(payload);
		int renderHeight = computeLiveRenderHeight(payload, renderWidth);
		return new RendererBotOffscreenWorldRenderer.RenderRequest(
				payload.renderSessionId(),
				payload.dimensionId(),
				pose == null ? payload.followEntityUuid() : null,
				pose == null ? payload.expectedX() : pose.x(),
				pose == null ? payload.expectedY() : pose.y(),
				pose == null ? payload.expectedZ() : pose.z(),
				pose == null ? payload.expectedYaw() : pose.yaw(),
				pose == null ? payload.expectedPitch() : pose.pitch(),
				payload.fovDegrees(),
				renderWidth,
				renderHeight,
				pose != null,
				false,
				0.0D,
				0.0D
		);
	}

	private static RendererBotTopDownMapRenderer.TileRequest mapTileRenderRequest(RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload) {
		return new RendererBotTopDownMapRenderer.TileRequest(
				payload.renderSessionId(),
				payload.dimensionId(),
				payload.centerX(),
				payload.centerZ(),
				Math.max(1, payload.tileSize()),
				Math.max(1, payload.tileSize()),
				Math.max(1.0D / 16.0D, payload.blocksPerPixel())
		);
	}

	private static boolean isProbablyBlankMapTile(byte[] pixels) {
		if (pixels == null || pixels.length < 1024 * 3) {
			return false;
		}
		Map<Integer, Integer> counts = new HashMap<>();
		int dominantCount = 0;
		int pixelCount = pixels.length / 3;
		for (int i = 0; i < pixelCount; i++) {
			int offset = i * 3;
			int rgb = (Byte.toUnsignedInt(pixels[offset]) << 16)
					| (Byte.toUnsignedInt(pixels[offset + 1]) << 8)
					| Byte.toUnsignedInt(pixels[offset + 2]);
			int count = counts.merge(rgb, 1, Integer::sum);
			if (count > dominantCount) {
				dominantCount = count;
			}
		}
		return dominantCount >= pixelCount * 995L / 1000L
				|| (counts.size() <= 3 && dominantCount >= pixelCount * 980L / 1000L);
	}

	private static final class PendingMapTile {
		private final RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload;
		private final long requestStartedAt;
		private int remainingWarmupFrames;
		private int blankRetries;
		private boolean rendering;

		private PendingMapTile(RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload, long requestStartedAt) {
			this.payload = payload;
			this.requestStartedAt = requestStartedAt;
			this.remainingWarmupFrames = MAP_TILE_WARMUP_FRAMES;
			this.blankRetries = 0;
			this.rendering = false;
		}

		private RendererBotPayloads.RendererBotMapTileRequestS2CPayload payload() {
			return this.payload;
		}

		private long requestStartedAt() {
			return this.requestStartedAt;
		}

		private boolean rendering() {
			return this.rendering;
		}

		private int remainingWarmupFrames() {
			return this.remainingWarmupFrames;
		}

		private void decrementWarmupFrames() {
			this.remainingWarmupFrames = Math.max(0, this.remainingWarmupFrames - 1);
		}

		private void resetWarmupFrames(int warmupFrames) {
			this.remainingWarmupFrames = Math.max(this.remainingWarmupFrames, warmupFrames);
		}

		private boolean recordBlankRetry() {
			if (this.blankRetries >= MAP_TILE_MAX_BLANK_RETRIES) {
				return false;
			}
			this.blankRetries++;
			return true;
		}

		private void markRendering() {
			this.rendering = true;
		}

		private void clearRendering() {
			this.rendering = false;
		}
	}

	private static final class PendingCapture {
		private final RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload;
		private final long requestStartedAt;
		private int remainingWarmupFrames;
		private boolean screenshotRequested;

		private PendingCapture(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, int remainingWarmupFrames, long requestStartedAt) {
			this.payload = payload;
			this.requestStartedAt = requestStartedAt;
			this.remainingWarmupFrames = remainingWarmupFrames;
			this.screenshotRequested = false;
		}

		private RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload() {
			return this.payload;
		}

		private long requestStartedAt() {
			return this.requestStartedAt;
		}

		private int remainingWarmupFrames() {
			return this.remainingWarmupFrames;
		}

		private void decrementWarmupFrames() {
			if (this.remainingWarmupFrames > 0) {
				this.remainingWarmupFrames--;
			}
		}

		private boolean screenshotRequested() {
			return this.screenshotRequested;
		}

		private void markScreenshotRequested() {
			this.screenshotRequested = true;
		}

		private void clearScreenshotRequested() {
			this.screenshotRequested = false;
		}
	}

	private static final class LiveStreamSession {
		private final RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload;
		private final long startedAtMillis;
		private int remainingWarmupFrames;
		private long lastFrameAtNanos;
		private boolean frameInFlight;
		private LiveStreamPose pose;

		private LiveStreamSession(
				RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload,
				long startedAtMillis,
				int remainingWarmupFrames
		) {
			this.payload = payload;
			this.startedAtMillis = startedAtMillis;
			this.remainingWarmupFrames = remainingWarmupFrames;
			this.lastFrameAtNanos = 0L;
			this.frameInFlight = false;
		}

		private RendererBotPayloads.RendererBotLiveStreamStartS2CPayload payload() {
			return this.payload;
		}

		private long startedAtMillis() {
			return this.startedAtMillis;
		}

		private int remainingWarmupFrames() {
			return this.remainingWarmupFrames;
		}

		private void decrementWarmupFrames() {
			if (this.remainingWarmupFrames > 0) {
				this.remainingWarmupFrames--;
			}
		}

		private long lastFrameAtNanos() {
			return this.lastFrameAtNanos;
		}

		private boolean frameInFlight() {
			return this.frameInFlight;
		}

		private LiveStreamPose pose() {
			return this.pose;
		}

		private void updatePose(LiveStreamPose pose) {
			this.pose = pose;
		}

		private void markFrameInFlight(long nowNanos) {
			this.lastFrameAtNanos = nowNanos;
			this.frameInFlight = true;
		}

		private void clearFrameInFlight() {
			this.frameInFlight = false;
		}
	}

	private record LiveStreamPose(double x, double y, double z, float yaw, float pitch) {
	}

	private record CapturedFrame(
			String dimensionId,
			UUID followEntityUuid,
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
			byte[] previewPixels,
			byte[] fullPixels,
			long capturedAtMillis
	) {
		private boolean matches(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload) {
			return Objects.equals(this.dimensionId, payload.dimensionId())
					&& Objects.equals(this.followEntityUuid, payload.followEntityUuid())
					&& Math.abs(this.expectedX - payload.expectedX()) <= 0.01D
					&& Math.abs(this.expectedY - payload.expectedY()) <= 0.01D
					&& Math.abs(this.expectedZ - payload.expectedZ()) <= 0.01D
					&& Math.abs(this.expectedYaw - payload.expectedYaw()) <= 0.1F
					&& Math.abs(this.expectedPitch - payload.expectedPitch()) <= 0.1F
					&& this.previewWidth == payload.previewWidth()
					&& this.previewHeight == payload.previewHeight()
					&& this.fullWidth == payload.fullWidth()
					&& this.fullHeight == payload.fullHeight()
					&& this.fovDegrees == payload.fovDegrees();
		}
	}
}
