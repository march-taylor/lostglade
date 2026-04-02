package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.network.RendererBotPayloads;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RendererBotClientCapture {
	private static final Object LOCK = new Object();
	private static final long LOCAL_CAPTURE_TIMEOUT_MS = Long.getLong("lg2.rendererBotLocalCaptureTimeoutMs", 8_000L);
	private static final ExecutorService CAPTURE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "lg2-renderer-bot-capture");
		thread.setDaemon(true);
		return thread;
	});

	private static PendingCapture pendingCapture;

	private RendererBotClientCapture() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ClientPlayNetworking.send(new RendererBotPayloads.RendererBotHelloC2SPayload(RendererBotPayloads.PROTOCOL_VERSION))
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearPendingCapture(null));
		ClientTickEvents.END_CLIENT_TICK.register(RendererBotClientCapture::onClientTick);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotCaptureRequestS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> beginCapture(payload, context.client()))
		);
		WorldRenderEvents.END_MAIN.register(context -> onEndMain(Minecraft.getInstance()));
	}

	private static void beginCapture(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, Minecraft client) {
		PendingCapture previous;
		synchronized (LOCK) {
			previous = pendingCapture;
			pendingCapture = new PendingCapture(payload, 6, client.options.fov().get(), System.currentTimeMillis(), false);
			client.options.fov().set(payload.fovDegrees());
		}
		Lg2.LOGGER.info(
				"Renderer bot received capture request {} preview={}x{} full={}x{}",
				payload.requestId(),
				payload.previewWidth(),
				payload.previewHeight(),
				payload.fullWidth(),
				payload.fullHeight()
		);

		if (previous != null) {
			sendFailure(previous.payload(), "Renderer bot capture was replaced by a newer request");
		}
	}

	private static void onClientTick(Minecraft client) {
		PendingCapture capture;
		synchronized (LOCK) {
			capture = pendingCapture;
		}
		if (capture == null || capture.screenshotRequested()) {
			return;
		}
		if (System.currentTimeMillis() - capture.requestStartedAt() < LOCAL_CAPTURE_TIMEOUT_MS) {
			return;
		}
		sendFailure(capture.payload(), "Renderer bot client did not produce a rendered frame in time");
		clearPendingCapture(capture.payload().requestId());
	}

	private static void onEndMain(Minecraft client) {
		PendingCapture capture;
		synchronized (LOCK) {
			capture = pendingCapture;
		}
		if (capture == null || capture.screenshotRequested()) {
			return;
		}
		if (!isCaptureWorldReady(client, capture.payload())) {
			return;
		}
		if (capture.remainingWarmupFrames() > 0) {
			capture.decrementWarmupFrames();
			return;
		}
		capture.markScreenshotRequested();
		Lg2.LOGGER.info("Renderer bot capturing rendered frame for {}", capture.payload().requestId());
		Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> handleScreenshot(client, capture, image));
	}

	private static boolean isCaptureWorldReady(Minecraft client, RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			return false;
		}
		if (client.screen != null || client.getOverlay() != null) {
			return false;
		}
		Identifier expectedDimension = Identifier.tryParse(payload.dimensionId());
		return expectedDimension != null && client.level.dimension().identifier().equals(expectedDimension);
	}

	private static void handleScreenshot(Minecraft client, PendingCapture capture, NativeImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.makePixelArray();
		image.close();
		Lg2.LOGGER.info("Renderer bot captured {}x{} frame for {}", width, height, capture.payload().requestId());
		client.execute(() -> restoreFov(capture));
		CAPTURE_EXECUTOR.submit(() -> processCaptureAsync(capture.payload(), pixels, width, height));
	}

	private static void processCaptureAsync(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, int[] pixels, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		try {
			byte[] previewPixels = quantizeScaledFrame(pixels, width, height, payload.previewWidth(), payload.previewHeight());
			client.execute(() -> ClientPlayNetworking.send(new RendererBotPayloads.RendererBotPreviewFrameC2SPayload(payload.requestId(), previewPixels)));

			byte[] fullPixels;
			if (payload.previewWidth() == payload.fullWidth() && payload.previewHeight() == payload.fullHeight()) {
				fullPixels = previewPixels;
			} else {
				fullPixels = quantizeScaledFrame(pixels, width, height, payload.fullWidth(), payload.fullHeight());
			}
			Lg2.LOGGER.info("Renderer bot sending preview/full frames for {}", payload.requestId());
			client.execute(() -> ClientPlayNetworking.send(new RendererBotPayloads.RendererBotFullFrameC2SPayload(payload.requestId(), fullPixels)));
		} catch (Throwable throwable) {
			client.execute(() -> sendFailure(payload, throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName()));
			Lg2.LOGGER.error("Renderer bot capture failed", throwable);
		} finally {
			client.execute(() -> clearPendingCapture(payload.requestId()));
		}
	}

	private static byte[] quantizeScaledFrame(int[] sourcePixels, int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
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
		for (int y = 0; y < outputHeight; y++) {
			double sampleY = cropY + ((y + 0.5D) / outputHeight) * cropHeight;
			int sourceY = Mth.clamp((int) Math.floor(sampleY), 0, sourceHeight - 1);
			for (int x = 0; x < outputWidth; x++) {
				double sampleX = cropX + ((x + 0.5D) / outputWidth) * cropWidth;
				int sourceX = Mth.clamp((int) Math.floor(sampleX), 0, sourceWidth - 1);
				int rgb = sourcePixels[sourceY * sourceWidth + sourceX] & 0xFFFFFF;
				output[y * outputWidth + x] = MapPaletteQuantizer.quantizeDithered(rgb, x, y);
			}
		}
		return output;
	}

	private static void restoreFov(PendingCapture capture) {
		synchronized (LOCK) {
			if (pendingCapture == null || pendingCapture.payload().requestId().equals(capture.payload().requestId())) {
				Minecraft.getInstance().options.fov().set(capture.originalFov());
			}
		}
	}

	private static void clearPendingCapture(java.util.UUID requestId) {
		synchronized (LOCK) {
			if (pendingCapture == null) {
				return;
			}
			if (requestId == null || pendingCapture.payload().requestId().equals(requestId)) {
				Minecraft.getInstance().options.fov().set(pendingCapture.originalFov());
				pendingCapture = null;
			}
		}
	}

	private static void sendFailure(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, String message) {
		Lg2.LOGGER.warn("Renderer bot failing capture {}: {}", payload.requestId(), message);
		ClientPlayNetworking.send(new RendererBotPayloads.RendererBotCaptureFailureC2SPayload(
				payload.requestId(),
				message == null || message.isBlank() ? "Renderer bot capture failed" : message
		));
	}

	private static final class PendingCapture {
		private final RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload;
		private final int originalFov;
		private final long requestStartedAt;
		private int remainingWarmupFrames;
		private boolean screenshotRequested;

		private PendingCapture(RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload, int remainingWarmupFrames, int originalFov, long requestStartedAt, boolean screenshotRequested) {
			this.payload = payload;
			this.remainingWarmupFrames = remainingWarmupFrames;
			this.originalFov = originalFov;
			this.requestStartedAt = requestStartedAt;
			this.screenshotRequested = screenshotRequested;
		}

		private RendererBotPayloads.RendererBotCaptureRequestS2CPayload payload() {
			return this.payload;
		}

		private int remainingWarmupFrames() {
			return this.remainingWarmupFrames;
		}

		private void decrementWarmupFrames() {
			if (this.remainingWarmupFrames > 0) {
				this.remainingWarmupFrames--;
			}
		}

		private int originalFov() {
			return this.originalFov;
		}

		private long requestStartedAt() {
			return this.requestStartedAt;
		}

		private boolean screenshotRequested() {
			return this.screenshotRequested;
		}

		private void markScreenshotRequested() {
			this.screenshotRequested = true;
		}
	}
}
