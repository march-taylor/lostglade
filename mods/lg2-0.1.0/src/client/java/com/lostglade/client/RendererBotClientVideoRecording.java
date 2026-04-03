package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.network.RendererBotPayloads;
import com.lostglade.server.CameraMediaCache;
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RendererBotClientVideoRecording {
	private static final Object LOCK = new Object();
	private static final ExecutorService RECORD_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "lg2-renderer-bot-video");
		thread.setDaemon(true);
		return thread;
	});
	private static final String DEFAULT_FFMPEG_BIN = "ffmpeg";
	private static final int DEFAULT_WARMUP_FRAMES = Math.max(1, Integer.getInteger("lg2.rendererBotVideoWarmupFrames", 3));
	private static final long FINISH_TIMEOUT_MS = Long.getLong("lg2.rendererBotVideoFinishTimeoutMs", 30_000L);
	private static final int MIN_RECORDING_FRAMES = Math.max(2, Integer.getInteger("lg2.rendererBotVideoMinFrames", 2));
	private static final int MAX_TARGET_FPS = 20;
	private static final double TARGET_RENDER_SCALE = Math.max(1.0D, doubleProperty("lg2.rendererBotVideoRenderScale", 2.0D));
	private static final int MIN_RENDER_WIDTH = Math.max(128, Integer.getInteger("lg2.rendererBotVideoMinRenderWidth", 1024));
	private static final int MIN_RENDER_HEIGHT = Math.max(128, Integer.getInteger("lg2.rendererBotVideoMinRenderHeight", 768));
	private static final int MAX_RENDER_WIDTH = Math.max(MIN_RENDER_WIDTH, Integer.getInteger("lg2.rendererBotVideoMaxRenderWidth", 2048));
	private static final int MAX_RENDER_HEIGHT = Math.max(MIN_RENDER_HEIGHT, Integer.getInteger("lg2.rendererBotVideoMaxRenderHeight", 1536));
	private static final String ENCODER_PRESET = System.getProperty("lg2.rendererBotVideoPreset", "fast").trim();
	private static final int ENCODER_CRF = Math.max(0, Integer.getInteger("lg2.rendererBotVideoCrf", 18));

	private static PendingRecording recording;

	private RendererBotClientVideoRecording() {
	}

	public static void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> abortCurrent("Renderer bot disconnected during video recording"));
		ClientTickEvents.END_CLIENT_TICK.register(RendererBotClientVideoRecording::onClientTick);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> beginRecording(payload, context.client()))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotVideoRecordingStopS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> requestStop(payload.requestId()))
		);
		WorldRenderEvents.END_MAIN.register(context -> onEndMain(Minecraft.getInstance()));
	}

	private static void beginRecording(RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload payload, Minecraft client) {
		abortCurrent("Renderer bot recording was replaced by a newer request");
		try {
			String sourceKey = payload.requestId().toString();
			CameraMediaCache.ensureVideoParent(sourceKey);
			Path tempPath = CameraMediaCache.tempVideoSourcePath(sourceKey);
			Path finalPath = CameraMediaCache.videoSourcePath(sourceKey);
			Files.deleteIfExists(tempPath);
			Files.deleteIfExists(finalPath);

			int captureWidth = computeRenderWidth(payload);
			int captureHeight = computeRenderHeight(payload, captureWidth);
			ensureRenderTargetSize(client, captureWidth, captureHeight);
			int targetFps = Math.clamp(payload.targetFps(), 1, MAX_TARGET_FPS);
			Process process = startEncoderProcess(tempPath, captureWidth, captureHeight, targetFps);
			synchronized (LOCK) {
				recording = new PendingRecording(
						payload,
						client.options.fov().get(),
						process,
						process.getOutputStream(),
						tempPath,
						finalPath,
						captureWidth,
						captureHeight,
						DEFAULT_WARMUP_FRAMES,
						System.currentTimeMillis()
				);
				client.options.fov().set(payload.fovDegrees());
			}
			Lg2.LOGGER.info(
					"Renderer bot started video recording {} at {} fps {}x{} (capture {}x{})",
					payload.requestId(),
					targetFps,
					payload.fullWidth(),
					payload.fullHeight(),
					captureWidth,
					captureHeight
			);
		} catch (Exception exception) {
			sendFailure(payload.requestId(), exception.getMessage());
			Lg2.LOGGER.warn("Renderer bot failed to start video recording {}", payload.requestId(), exception);
		}
	}

	private static void requestStop(UUID requestId) {
		synchronized (LOCK) {
			if (recording == null || !Objects.equals(recording.payload().requestId(), requestId)) {
				return;
			}
			recording.stopRequested = true;
			Lg2.LOGGER.info("Renderer bot received stop request for video recording {} after {} frames", requestId, recording.frameCount);
		}
	}

	private static void onClientTick(Minecraft client) {
		PendingRecording current;
		synchronized (LOCK) {
			current = recording;
		}
		if (current == null) {
			return;
		}
		long elapsedMs = System.currentTimeMillis() - current.startedAtMs();
		if (elapsedMs >= current.payload().maxDurationSeconds() * 1_000L) {
			current.stopRequested = true;
		}
		if (current.stopRequested && !current.frameInFlight && !current.finishScheduled) {
			scheduleFinish(client, current);
		}
	}

	private static void onEndMain(Minecraft client) {
		PendingRecording current;
		synchronized (LOCK) {
			current = recording;
		}
		if (current == null || current.stopRequested || current.frameInFlight || current.finishScheduled) {
			return;
		}
		if (!isWorldReady(client, current.payload())) {
			return;
		}
		if (current.remainingWarmupFrames > 0) {
			current.remainingWarmupFrames--;
			return;
		}
		long intervalNanos = 1_000_000_000L / Math.clamp(current.payload().targetFps(), 1, MAX_TARGET_FPS);
		long nowNanos = System.nanoTime();
		if (current.lastFrameAtNanos != 0L && nowNanos - current.lastFrameAtNanos < intervalNanos) {
			return;
		}
		current.lastFrameAtNanos = nowNanos;
		current.frameInFlight = true;
		Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> handleCapturedFrame(client, current, image));
	}

	private static void handleCapturedFrame(Minecraft client, PendingRecording current, NativeImage image) {
		RECORD_EXECUTOR.submit(() -> processCapturedFrame(client, current, image));
	}

	private static void processCapturedFrame(Minecraft client, PendingRecording current, NativeImage image) {
		try {
			int width = image.getWidth();
			int height = image.getHeight();
			int[] pixels = image.makePixelArray();
			if (current.firstPreviewFrame == null) {
				current.firstPreviewFrame = quantizeScaledFrame(pixels, width, height, current.payload().previewWidth(), current.payload().previewHeight());
				current.firstFullFrame = quantizeScaledFrame(pixels, width, height, current.payload().fullWidth(), current.payload().fullHeight());
			}
			byte[] rgb = argbToRgb(pixels);
			synchronized (LOCK) {
				if (recording == null || !Objects.equals(recording.payload().requestId(), current.payload().requestId()) || current.stopRequested) {
					return;
				}
				current.encoderInput.write(rgb);
				current.encoderInput.flush();
				current.frameCount++;
			}
		} catch (Exception exception) {
			client.execute(() -> {
				sendFailure(current.payload().requestId(), exception.getMessage());
				abortCurrent("Renderer bot failed during video frame encoding");
			});
			Lg2.LOGGER.warn("Renderer bot video frame encode failed for {}", current.payload().requestId(), exception);
		} finally {
			image.close();
			client.execute(() -> {
				synchronized (LOCK) {
					if (recording != null && Objects.equals(recording.payload().requestId(), current.payload().requestId())) {
						recording.frameInFlight = false;
						if (recording.stopRequested && !recording.finishScheduled) {
							scheduleFinish(client, recording);
						}
					}
				}
			});
		}
	}

	private static void scheduleFinish(Minecraft client, PendingRecording current) {
		current.finishScheduled = true;
		RECORD_EXECUTOR.submit(() -> finishRecording(client, current));
	}

	private static void finishRecording(Minecraft client, PendingRecording current) {
		try {
			try {
				current.encoderInput.close();
			} catch (IOException ignored) {
			}
			if (!current.encoderProcess.waitFor(FINISH_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
				current.encoderProcess.destroyForcibly();
				throw new IOException("Renderer bot encoder timed out");
			}
			if (current.encoderProcess.exitValue() != 0) {
				throw new IOException("Renderer bot encoder exited with code " + current.encoderProcess.exitValue());
			}
			if (current.frameCount < MIN_RECORDING_FRAMES || current.firstPreviewFrame == null || current.firstFullFrame == null) {
				throw new IOException("Renderer bot recording captured too few frames: " + current.frameCount);
			}
			Files.move(current.tempPath(), current.finalPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			int targetFps = Math.clamp(current.payload().targetFps(), 1, MAX_TARGET_FPS);
			long durationMs = current.frameCount * 1_000L / targetFps;
			Lg2.LOGGER.info("Renderer bot finished video recording {} with {} frames ({} ms)", current.payload().requestId(), current.frameCount, durationMs);
			client.execute(() -> {
				restoreFov(current);
				ClientPlayNetworking.send(new RendererBotPayloads.RendererBotVideoRecordingCompleteC2SPayload(
						current.payload().requestId(),
						durationMs,
						targetFps,
						current.firstPreviewFrame,
						current.firstFullFrame
				));
				clearIfMatching(current.payload().requestId());
			});
		} catch (Exception exception) {
			client.execute(() -> {
				restoreFov(current);
				sendFailure(current.payload().requestId(), exception.getMessage());
				clearIfMatching(current.payload().requestId());
			});
			Lg2.LOGGER.warn("Renderer bot failed to finish video recording {}", current.payload().requestId(), exception);
		}
	}

	private static void abortCurrent(String message) {
		PendingRecording current;
		synchronized (LOCK) {
			current = recording;
			recording = null;
		}
		if (current == null) {
			return;
		}
		restoreFov(current);
		try {
			current.encoderInput.close();
		} catch (IOException ignored) {
		}
		current.encoderProcess.destroyForcibly();
		try {
			Files.deleteIfExists(current.tempPath());
		} catch (IOException ignored) {
		}
		sendFailure(current.payload().requestId(), message);
	}

	private static void clearIfMatching(UUID requestId) {
		synchronized (LOCK) {
			if (recording != null && Objects.equals(recording.payload().requestId(), requestId)) {
				recording = null;
			}
		}
	}

	private static void restoreFov(PendingRecording current) {
		Minecraft client = Minecraft.getInstance();
		if (client != null && client.options != null) {
			client.options.fov().set(current.originalFov());
		}
	}

	private static boolean isWorldReady(Minecraft client, RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload payload) {
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

	private static Process startEncoderProcess(Path outputPath, int width, int height, int fps) throws IOException {
		List<String> command = new ArrayList<>();
		command.add(ffmpegBinary());
		command.add("-y");
		command.add("-loglevel");
		command.add("error");
		command.add("-f");
		command.add("rawvideo");
		command.add("-pix_fmt");
		command.add("rgb24");
		command.add("-s");
		command.add(width + "x" + height);
		command.add("-r");
		command.add(Integer.toString(Math.max(1, fps)));
		command.add("-i");
		command.add("pipe:0");
		command.add("-an");
		command.add("-c:v");
		command.add("libx264");
		command.add("-preset");
		command.add(ENCODER_PRESET.isBlank() ? "fast" : ENCODER_PRESET);
		command.add("-crf");
		command.add(Integer.toString(ENCODER_CRF));
		command.add("-g");
		command.add(Integer.toString(Math.max(1, fps * 2)));
		command.add("-movflags");
		command.add("+faststart");
		command.add("-pix_fmt");
		command.add("yuv420p");
		command.add(outputPath.toAbsolutePath().toString());
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectError(ProcessBuilder.Redirect.DISCARD);
		return builder.start();
	}

	private static String ffmpegBinary() {
		String property = System.getProperty("lg2.ffmpegBin");
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		return DEFAULT_FFMPEG_BIN;
	}

	private static void ensureRenderTargetSize(Minecraft client, int desiredWidth, int desiredHeight) {
		if (client == null || client.getWindow() == null) {
			return;
		}
		int currentWidth = client.getWindow().getWidth();
		int currentHeight = client.getWindow().getHeight();
		if (Math.abs(currentWidth - desiredWidth) <= 2 && Math.abs(currentHeight - desiredHeight) <= 2) {
			return;
		}
		client.getWindow().setWindowed(desiredWidth, desiredHeight);
		client.resizeDisplay();
	}

	private static int computeRenderWidth(RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload payload) {
		int fullWidth = Math.max(1, payload.fullWidth());
		int fullHeight = Math.max(1, payload.fullHeight());
		double minScale = Math.max(MIN_RENDER_WIDTH / (double) fullWidth, MIN_RENDER_HEIGHT / (double) fullHeight);
		double maxScale = Math.min(MAX_RENDER_WIDTH / (double) fullWidth, MAX_RENDER_HEIGHT / (double) fullHeight);
		double scale = Math.max(1.0D, Math.min(maxScale, Math.max(minScale, TARGET_RENDER_SCALE)));
		return Math.clamp((int) Math.round(fullWidth * scale), 1, MAX_RENDER_WIDTH);
	}

	private static int computeRenderHeight(RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload payload, int renderWidth) {
		double aspect = Math.max(1.0D, payload.fullHeight()) / (double) Math.max(1.0D, payload.fullWidth());
		return Math.clamp((int) Math.round(renderWidth * aspect), 1, MAX_RENDER_HEIGHT);
	}

	private static byte[] argbToRgb(int[] pixels) {
		byte[] rgb = new byte[pixels.length * 3];
		for (int i = 0; i < pixels.length; i++) {
			int pixel = pixels[i];
			int offset = i * 3;
			rgb[offset] = (byte) ((pixel >> 16) & 0xFF);
			rgb[offset + 1] = (byte) ((pixel >> 8) & 0xFF);
			rgb[offset + 2] = (byte) (pixel & 0xFF);
		}
		return rgb;
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
		double sampleSpanX = cropWidth / Math.max(1.0D, outputWidth);
		double sampleSpanY = cropHeight / Math.max(1.0D, outputHeight);
		for (int y = 0; y < outputHeight; y++) {
			double sampleY = cropY + ((y + 0.5D) / outputHeight) * cropHeight;
			for (int x = 0; x < outputWidth; x++) {
				double sampleX = cropX + ((x + 0.5D) / outputWidth) * cropWidth;
				int rgb = sampleScaledRgb(sourcePixels, sourceWidth, sourceHeight, sampleX, sampleY, sampleSpanX, sampleSpanY);
				output[y * outputWidth + x] = MapPaletteQuantizer.quantizeDithered(rgb, x, y);
			}
		}
		return output;
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
		double clampedX = Math.max(0.0D, Math.min(Math.max(0, sourceWidth - 1), sampleX));
		double clampedY = Math.max(0.0D, Math.min(Math.max(0, sourceHeight - 1), sampleY));
		int x0 = Math.clamp((int) Math.floor(clampedX), 0, sourceWidth - 1);
		int y0 = Math.clamp((int) Math.floor(clampedY), 0, sourceHeight - 1);
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
		return Math.clamp((int) Math.round(top + (bottom - top) * ty), 0, 255);
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

	private static void sendFailure(UUID requestId, String message) {
		if (requestId == null) {
			return;
		}
		ClientPlayNetworking.send(new RendererBotPayloads.RendererBotCaptureFailureC2SPayload(
				requestId,
				message == null || message.isBlank() ? "Renderer bot video recording failed" : message
		));
	}

	private static final class PendingRecording {
		private final RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload payload;
		private final int originalFov;
		private final Process encoderProcess;
		private final OutputStream encoderInput;
		private final Path tempPath;
		private final Path finalPath;
		private final int captureWidth;
		private final int captureHeight;
		private final long startedAtMs;
		private byte[] firstPreviewFrame;
		private byte[] firstFullFrame;
		private int remainingWarmupFrames;
		private volatile boolean stopRequested;
		private volatile boolean frameInFlight;
		private volatile boolean finishScheduled;
		private volatile int frameCount;
		private volatile long lastFrameAtNanos;

		private PendingRecording(
				RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload payload,
				int originalFov,
				Process encoderProcess,
				OutputStream encoderInput,
				Path tempPath,
				Path finalPath,
				int captureWidth,
				int captureHeight,
				int remainingWarmupFrames,
				long startedAtMs
		) {
			this.payload = payload;
			this.originalFov = originalFov;
			this.encoderProcess = encoderProcess;
			this.encoderInput = encoderInput;
			this.tempPath = tempPath;
			this.finalPath = finalPath;
			this.captureWidth = captureWidth;
			this.captureHeight = captureHeight;
			this.remainingWarmupFrames = remainingWarmupFrames;
			this.startedAtMs = startedAtMs;
		}

		private RendererBotPayloads.RendererBotVideoRecordingStartS2CPayload payload() {
			return this.payload;
		}

		private int originalFov() {
			return this.originalFov;
		}

		private Path tempPath() {
			return this.tempPath;
		}

		private Path finalPath() {
			return this.finalPath;
		}

		private long startedAtMs() {
			return this.startedAtMs;
		}
	}
}
