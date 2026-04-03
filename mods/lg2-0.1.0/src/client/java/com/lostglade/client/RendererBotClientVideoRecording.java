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

			ensureRenderTargetSize(client, payload.fullWidth(), payload.fullHeight());
			Process process = startEncoderProcess(tempPath, payload.fullWidth(), payload.fullHeight(), payload.targetFps());
			synchronized (LOCK) {
				recording = new PendingRecording(payload, client.options.fov().get(), process, process.getOutputStream(), tempPath, finalPath, DEFAULT_WARMUP_FRAMES, System.currentTimeMillis());
				client.options.fov().set(payload.fovDegrees());
			}
			Lg2.LOGGER.info("Renderer bot started video recording {} at {} fps {}x{}", payload.requestId(), payload.targetFps(), payload.fullWidth(), payload.fullHeight());
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
		long intervalNanos = 1_000_000_000L / Math.max(1, current.payload().targetFps());
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
			long durationMs = current.frameCount * 1_000L / Math.max(1, current.payload().targetFps());
			Lg2.LOGGER.info("Renderer bot finished video recording {} with {} frames ({} ms)", current.payload().requestId(), current.frameCount, durationMs);
			client.execute(() -> {
				restoreFov(current);
				ClientPlayNetworking.send(new RendererBotPayloads.RendererBotVideoRecordingCompleteC2SPayload(
						current.payload().requestId(),
						durationMs,
						current.payload().targetFps(),
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
		command.add("ultrafast");
		command.add("-crf");
		command.add("30");
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
		for (int y = 0; y < outputHeight; y++) {
			double sampleY = cropY + ((y + 0.5D) / outputHeight) * cropHeight;
			int sourceY = Math.clamp((int) Math.floor(sampleY), 0, sourceHeight - 1);
			for (int x = 0; x < outputWidth; x++) {
				double sampleX = cropX + ((x + 0.5D) / outputWidth) * cropWidth;
				int sourceX = Math.clamp((int) Math.floor(sampleX), 0, sourceWidth - 1);
				int rgb = sourcePixels[sourceY * sourceWidth + sourceX] & 0xFFFFFF;
				output[y * outputWidth + x] = MapPaletteQuantizer.quantizeDithered(rgb, x, y);
			}
		}
		return output;
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
				int remainingWarmupFrames,
				long startedAtMs
		) {
			this.payload = payload;
			this.originalFov = originalFov;
			this.encoderProcess = encoderProcess;
			this.encoderInput = encoderInput;
			this.tempPath = tempPath;
			this.finalPath = finalPath;
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
