package com.lostglade.server.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.server.progress.TaskProgress;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.TreeMap;

public final class MonitorYoutubeRelayClient {
	private static final Gson GSON = new Gson();
	private static final Map<String, RelaySession> SESSIONS = new ConcurrentHashMap<>();
	private static final ScheduledExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("lg2-youtube-cleanup"));
	private static final String DEFAULT_YT_DLP_BIN = "yt-dlp";
	private static final String DEFAULT_FFMPEG_BIN = "ffmpeg";
	private static final double FRAME_RATE = readDoubleSetting("LG2_YT_FRAME_RATE", "lg2.youtube.frameRate", 10.0D);
	private static final int FRAME_WIDTH = readIntSetting("LG2_YT_FRAME_WIDTH", "lg2.youtube.frameWidth", 480);
	private static final long SESSION_IDLE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(readIntSetting("LG2_YT_IDLE_TIMEOUT_SEC", "lg2.youtube.idleTimeoutSec", 600));
	private static final int COMMAND_TIMEOUT_SEC = readIntSetting("LG2_YT_COMMAND_TIMEOUT_SEC", "lg2.youtube.commandTimeoutSec", 45);
	private static final int STREAM_START_TIMEOUT_SEC = readIntSetting("LG2_YT_STREAM_START_TIMEOUT_SEC", "lg2.youtube.streamStartTimeoutSec", 20);
	private static final long PREVIEW_CACHE_BUCKET_MS = 1000L;
	private static final int MAX_CACHED_PREVIEW_FRAMES = 120;
	private static final long CACHE_LOOKUP_TOLERANCE_MS = 1500L;
	private static final long CONTIGUOUS_BUFFER_TOLERANCE_MS = PREVIEW_CACHE_BUCKET_MS + CACHE_LOOKUP_TOLERANCE_MS;
	private static final long PROCESS_STOP_TIMEOUT_MS = 250L;

	static {
		CLEANUP_EXECUTOR.scheduleAtFixedRate(MonitorYoutubeRelayClient::cleanupExpiredSessions, 30L, 30L, TimeUnit.SECONDS);
	}

	private MonitorYoutubeRelayClient() {
	}

	public static SessionLoadResponse load(String sessionId, String rawUrl, TaskProgress progress) throws IOException {
		validateYoutubeUrl(rawUrl);
		if (progress != null) {
			progress.setIndeterminate("CONNECTING");
		}
		RelaySession session = SESSIONS.computeIfAbsent(sessionId, RelaySession::new);
		session.touch();
		SessionLoadResponse response = session.load(rawUrl.trim());
		if (progress != null) {
			progress.setIndeterminate("LOADING");
		}
		return response;
	}

	public static SessionSnapshot snapshot(String sessionId, long knownFrameSequence) throws IOException {
		RelaySession session = requireSession(sessionId);
		session.touch();
		return session.snapshotWithPrefetch(knownFrameSequence);
	}

	public static void pause(String sessionId) throws IOException {
		requireSession(sessionId).pause();
	}

	public static void resume(String sessionId) throws IOException {
		requireSession(sessionId).resume();
	}

	public static void seek(String sessionId, long positionMs) throws IOException {
		requireSession(sessionId).seek(Math.max(0L, positionMs));
	}

	public static void close(String sessionId) throws IOException {
		RelaySession session = SESSIONS.remove(sessionId);
		if (session == null) {
			return;
		}
		session.close();
	}

	public static void shutdown() {
		for (RelaySession session : List.copyOf(SESSIONS.values())) {
			session.closeQuietly();
		}
		SESSIONS.clear();
		CLEANUP_EXECUTOR.shutdownNow();
	}

	public static boolean looksLikeYoutubeUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return false;
		}
		try {
			java.net.URI uri = java.net.URI.create(rawUrl.trim());
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
				return false;
			}
			String host = uri.getHost();
			if (host == null) {
				return false;
			}
			String normalizedHost = host.toLowerCase(Locale.ROOT);
			return normalizedHost.equals("youtu.be")
					|| normalizedHost.endsWith(".youtu.be")
					|| normalizedHost.equals("youtube.com")
					|| normalizedHost.endsWith(".youtube.com")
					|| normalizedHost.equals("youtube-nocookie.com")
					|| normalizedHost.endsWith(".youtube-nocookie.com");
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static RelaySession requireSession(String sessionId) throws IOException {
		if (sessionId == null || sessionId.isBlank()) {
			throw new IOException("sessionId is required");
		}
		RelaySession session = SESSIONS.get(sessionId);
		if (session == null) {
			throw new IOException("unknown session");
		}
		return session;
	}

	private static void cleanupExpiredSessions() {
		long now = System.currentTimeMillis();
		for (Map.Entry<String, RelaySession> entry : List.copyOf(SESSIONS.entrySet())) {
			RelaySession session = entry.getValue();
			if (session == null || now - session.lastAccessAtMillis() <= SESSION_IDLE_TIMEOUT_MS) {
				continue;
			}
			if (SESSIONS.remove(entry.getKey(), session)) {
				session.closeQuietly();
			}
		}
	}

	private static void validateYoutubeUrl(String rawUrl) throws IOException {
		if (!looksLikeYoutubeUrl(rawUrl)) {
			throw new IOException("Only YouTube links are supported");
		}
	}

	private static ResolvedYoutube resolveYoutube(String url) throws IOException {
		String metadataJson = runTextCommand(List.of(ytDlpBin(), "--dump-single-json", "--no-playlist", url), COMMAND_TIMEOUT_SEC);
		JsonObject metadata = GSON.fromJson(metadataJson, JsonObject.class);
		String title = getString(metadata, "title", "YouTube");
		long durationMs = Math.round(getDouble(metadata, "duration", 0.0D) * 1000.0D);
		boolean isLive = getBoolean(metadata, "is_live", false)
				|| "is_live".equalsIgnoreCase(getString(metadata, "live_status", ""));
		String streamUrl = firstOutputLine(runTextCommand(List.of(
				ytDlpBin(),
				"-g",
				"-f",
				"best[height<=480]/best",
				"--no-playlist",
				url
		), COMMAND_TIMEOUT_SEC));
		String audioStreamUrl;
		try {
			audioStreamUrl = firstOutputLine(runTextCommand(List.of(
					ytDlpBin(),
					"-g",
					"-f",
					"bestaudio/best",
					"--no-playlist",
					url
			), COMMAND_TIMEOUT_SEC));
		} catch (IOException ignored) {
			audioStreamUrl = streamUrl;
		}
		return new ResolvedYoutube(title, durationMs, isLive, streamUrl, audioStreamUrl);
	}

	private static String firstOutputLine(String output) throws IOException {
		String normalized = output == null ? "" : output.trim();
		if (normalized.isBlank()) {
			throw new IOException("Empty yt-dlp response");
		}
		String[] lines = normalized.split("\\R");
		return lines[0].trim();
	}

	private static String runTextCommand(List<String> command, int timeoutSeconds) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();
		try {
			byte[] outputBytes = readProcessOutput(process, timeoutSeconds, "Timed out: " + String.join(" ", command));
			String output = new String(outputBytes, StandardCharsets.UTF_8);
			if (process.exitValue() != 0) {
				String message = output.trim();
				if (message.isBlank()) {
					message = "Command failed: " + String.join(" ", command);
				}
				throw new IOException(message);
			}
			return output;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("Interrupted while running command", exception);
		} finally {
			process.destroy();
		}
	}

	private static BufferedImage runPreviewFrameCommand(List<String> command, int timeoutSeconds) throws IOException {
		return decodeImageBytes(runPreviewFrameCommandBytes(command, timeoutSeconds));
	}

	private static byte[] runPreviewFrameCommandBytes(List<String> command, int timeoutSeconds) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectError(ProcessBuilder.Redirect.DISCARD);
		Process process = builder.start();
		try {
			byte[] bytes = readProcessOutput(process, timeoutSeconds, "Timed out waiting for ffmpeg preview frame");
			if (process.exitValue() != 0 || bytes.length == 0) {
				throw new IOException("Failed to capture YouTube preview frame");
			}
			return bytes;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("Interrupted while capturing preview frame", exception);
		} finally {
			process.destroy();
		}
	}

	private static BufferedImage decodeImageBytes(byte[] bytes) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
		if (image == null) {
			throw new IOException("ffmpeg returned an unreadable preview frame");
		}
		return image;
	}

	private static byte[] readProcessOutput(Process process, int timeoutSeconds, String timeoutMessage) throws IOException, InterruptedException {
		ProcessOutputReader reader = new ProcessOutputReader(process.getInputStream());
		Thread readerThread = new Thread(reader, "lg2-youtube-process-output");
		readerThread.setDaemon(true);
		readerThread.start();
		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			readerThread.join(1000L);
			throw new IOException(timeoutMessage);
		}
		readerThread.join(1000L);
		if (reader.exception() != null) {
			throw reader.exception();
		}
		return reader.bytes();
	}

	private static List<String> previewFrameCommand(String streamUrl, long seekMs) {
		List<String> command = new ArrayList<>();
		command.add(ffmpegBin());
		command.add("-hide_banner");
		command.add("-loglevel");
		command.add("error");
		command.add("-nostdin");
		if (seekMs > 0L) {
			command.add("-ss");
			command.add(String.format(Locale.ROOT, "%.3f", seekMs / 1000.0D));
		}
		command.add("-i");
		command.add(streamUrl);
		command.add("-frames:v");
		command.add("1");
		command.add("-an");
		command.add("-vf");
		command.add("scale=w=" + FRAME_WIDTH + ":h=-2:force_original_aspect_ratio=decrease");
		command.add("-q:v");
		command.add("5");
		command.add("-f");
		command.add("image2pipe");
		command.add("-vcodec");
		command.add("mjpeg");
		command.add("-");
		return command;
	}

	private static List<String> sequentialPrefetchCommand(String streamUrl) {
		List<String> command = new ArrayList<>();
		command.add(ffmpegBin());
		command.add("-hide_banner");
		command.add("-loglevel");
		command.add("error");
		command.add("-nostdin");
		command.add("-threads");
		command.add("1");
		command.add("-i");
		command.add(streamUrl);
		command.add("-an");
		command.add("-vf");
		command.add("fps=1,scale=w=" + FRAME_WIDTH + ":h=-2:force_original_aspect_ratio=decrease");
		command.add("-q:v");
		command.add("6");
		command.add("-f");
		command.add("image2pipe");
		command.add("-vcodec");
		command.add("mjpeg");
		command.add("-");
		return command;
	}

	private static void readJpegFrames(InputStream stream, FrameConsumer consumer) throws IOException {
		byte[] readBuffer = new byte[8192];
		byte[] frameBuffer = new byte[16384];
		int size = 0;
		int read;
		while ((read = stream.read(readBuffer)) >= 0) {
			if (size + read > frameBuffer.length) {
				frameBuffer = Arrays.copyOf(frameBuffer, Math.max(frameBuffer.length * 2, size + read));
			}
			System.arraycopy(readBuffer, 0, frameBuffer, size, read);
			size += read;
			while (true) {
				int start = indexOf(frameBuffer, size, 0, (byte) 0xFF, (byte) 0xD8);
				if (start < 0) {
					if (size > 2) {
						frameBuffer[0] = frameBuffer[size - 2];
						frameBuffer[1] = frameBuffer[size - 1];
						size = 2;
					}
					break;
				}
				int end = indexOf(frameBuffer, size, start + 2, (byte) 0xFF, (byte) 0xD9);
				if (end < 0) {
					if (start > 0) {
						System.arraycopy(frameBuffer, start, frameBuffer, 0, size - start);
						size -= start;
					}
					break;
				}
				consumer.accept(Arrays.copyOfRange(frameBuffer, start, end + 2));
				int remaining = size - (end + 2);
				if (remaining > 0) {
					System.arraycopy(frameBuffer, end + 2, frameBuffer, 0, remaining);
				}
				size = remaining;
			}
		}
	}

	private static int indexOf(byte[] buffer, int size, int fromIndex, byte first, byte second) {
		for (int index = Math.max(0, fromIndex); index <= size - 2; index++) {
			if (buffer[index] == first && buffer[index + 1] == second) {
				return index;
			}
		}
		return -1;
	}

	private static ThreadFactory daemonThreadFactory(String namePrefix) {
		return runnable -> {
			Thread thread = new Thread(runnable, namePrefix);
			thread.setDaemon(true);
			return thread;
		};
	}

	private static String ytDlpBin() {
		return readStringSetting("YT_DLP_BIN", "lg2.youtube.ytDlpBin", DEFAULT_YT_DLP_BIN);
	}

	private static String ffmpegBin() {
		return readStringSetting("FFMPEG_BIN", "lg2.youtube.ffmpegBin", DEFAULT_FFMPEG_BIN);
	}

	private static String readStringSetting(String envKey, String propertyKey, String fallback) {
		String property = System.getProperty(propertyKey);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		String environment = System.getenv(envKey);
		if (environment != null && !environment.isBlank()) {
			return environment.trim();
		}
		return fallback;
	}

	private static int readIntSetting(String envKey, String propertyKey, int fallback) {
		String raw = readStringSetting(envKey, propertyKey, Integer.toString(fallback));
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static double readDoubleSetting(String envKey, String propertyKey, double fallback) {
		String raw = readStringSetting(envKey, propertyKey, Double.toString(fallback));
		try {
			return Double.parseDouble(raw.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
	}

	public record SessionLoadResponse(
			String sessionId,
			String title,
			long durationMs,
			boolean live,
			String status,
			String audioStreamUrl
	) {
	}

	public record SessionSnapshot(
			String sessionId,
			String title,
			BufferedImage frame,
			long frameSequence,
			long positionMs,
			long durationMs,
			long bufferedStartMs,
			long bufferedEndMs,
			boolean paused,
			boolean live,
			boolean audioPlaceholder,
			boolean ready,
			String status
	) {
	}

	private record ResolvedYoutube(
			String title,
			long durationMs,
			boolean live,
			String streamUrl,
			String audioStreamUrl
	) {
	}

	@FunctionalInterface
	private interface FrameConsumer {
		void accept(byte[] frameBytes) throws IOException;
	}

	private static final class ProcessOutputReader implements Runnable {
		private final InputStream inputStream;
		private volatile byte[] bytes = new byte[0];
		private volatile IOException exception = null;

		private ProcessOutputReader(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		@Override
		public void run() {
			try (InputStream stream = this.inputStream) {
				this.bytes = stream.readAllBytes();
			} catch (IOException exception) {
				this.exception = exception;
			}
		}

		private byte[] bytes() {
			return this.bytes;
		}

		private IOException exception() {
			return this.exception;
		}
	}

	private static final class RelaySession {
		private final String sessionId;
		private final Object lock = new Object();
		private String title = "YouTube";
		private String sourceUrl = "";
		private String streamUrl = "";
		private String audioStreamUrl = "";
		private long durationMs = 0L;
		private long positionMs = 0L;
		private boolean live = false;
		private boolean paused = false;
		private boolean audioPlaceholder = true;
		private String status = "IDLE";
		private BufferedImage latestFrame = null;
		private long frameSequence = 0L;
		private long lastAccessAtMillis = System.currentTimeMillis();
		private Process process = null;
		private Thread readerThread = null;
		private Process prefetchProcess = null;
		private Thread prefetchReaderThread = null;
		private long playStartedAtNanos = 0L;
		private long playBasePositionMs = 0L;
		private long bufferedStartMs = 0L;
		private long bufferedEndMs = 0L;
		private boolean bufferedFromStart = false;
		private boolean prefetchCompleted = false;
		private final NavigableMap<Long, byte[]> cachedPreviewFrames = new TreeMap<>();

		private RelaySession(String sessionId) {
			this.sessionId = sessionId;
		}

		private long lastAccessAtMillis() {
			synchronized (this.lock) {
				return this.lastAccessAtMillis;
			}
		}

		private void touch() {
			synchronized (this.lock) {
				this.lastAccessAtMillis = System.currentTimeMillis();
			}
		}

		private SessionLoadResponse load(String url) throws IOException {
			ResolvedYoutube resolved = resolveYoutube(url);
			synchronized (this.lock) {
				stopPrefetchLocked();
				stopLocked();
				this.title = resolved.title();
				this.sourceUrl = url;
				this.streamUrl = resolved.streamUrl();
				this.audioStreamUrl = resolved.audioStreamUrl();
				this.durationMs = resolved.durationMs();
				this.positionMs = 0L;
				this.live = resolved.live();
				this.paused = false;
				this.audioPlaceholder = true;
				this.status = "BUFFERING";
				this.latestFrame = null;
				this.frameSequence = 0L;
				this.bufferedStartMs = 0L;
				this.bufferedEndMs = 0L;
				this.bufferedFromStart = false;
				this.prefetchCompleted = false;
				this.cachedPreviewFrames.clear();
				this.lastAccessAtMillis = System.currentTimeMillis();
			}
			startStream();
			ensurePrefetchStarted();
			synchronized (this.lock) {
				return new SessionLoadResponse(this.sessionId, this.title, this.durationMs, this.live, this.status, this.audioStreamUrl);
			}
		}

		private SessionSnapshot snapshot(long knownFrameSequence) {
			boolean shouldEnsurePrefetch;
			synchronized (this.lock) {
				if (!this.live && !this.paused && this.process != null) {
					this.positionMs = currentPositionMsLocked();
				}
				shouldEnsurePrefetch = !this.live && !this.prefetchCompleted && this.prefetchProcess == null && !closedOrUnloadedLocked();
				BufferedImage frame = this.frameSequence != knownFrameSequence ? this.latestFrame : null;
				boolean ready = this.latestFrame != null;
				SessionSnapshot snapshot = new SessionSnapshot(
						this.sessionId,
						this.title,
						frame,
						this.frameSequence,
						this.positionMs,
						this.durationMs,
						this.bufferedStartMs,
						this.bufferedEndMs,
						this.paused,
						this.live,
						this.audioPlaceholder,
						ready,
						this.status
				);
				if (shouldEnsurePrefetch) {
					// Restart the low-FPS sequential cache reader if it died unexpectedly.
					// The actual process launch happens outside the state lock.
				}
				return snapshot;
			}
		}

		private SessionSnapshot snapshotWithPrefetch(long knownFrameSequence) {
			SessionSnapshot snapshot = snapshot(knownFrameSequence);
			ensurePrefetchStarted();
			return snapshot;
		}

		private void pause() {
			synchronized (this.lock) {
				if (this.paused) {
					return;
				}
				this.positionMs = currentPositionMsLocked();
				this.paused = true;
				this.status = "PAUSED";
				stopLocked();
			}
		}

		private void resume() throws IOException {
			synchronized (this.lock) {
				if (this.sourceUrl.isBlank() || this.streamUrl.isBlank()) {
					throw new IOException("session is not loaded");
				}
				applyCachedPreviewLocked(this.positionMs);
				this.paused = false;
				this.status = "BUFFERING";
			}
			startStream();
			ensurePrefetchStarted();
		}

		private void seek(long targetPositionMs) throws IOException {
			boolean capturePreview;
			synchronized (this.lock) {
				if (this.live) {
					throw new IOException("live stream is not seekable");
				}
				long clamped = Math.max(0L, this.durationMs > 0L ? Math.min(targetPositionMs, this.durationMs) : targetPositionMs);
				this.positionMs = clamped;
				boolean hadCachedPreview = applyCachedPreviewLocked(clamped);
				capturePreview = this.paused && !hadCachedPreview;
				this.status = this.paused ? "PAUSED" : "BUFFERING";
				stopLocked();
			}
			if (capturePreview) {
				capturePreviewFrame();
			} else {
				synchronized (this.lock) {
					if (this.paused) {
						return;
					}
				}
				startStream();
			}
			ensurePrefetchStarted();
		}

		private void close() {
			synchronized (this.lock) {
				stopPrefetchLocked();
				stopLocked();
				this.status = "CLOSED";
			}
		}

		private void closeQuietly() {
			try {
				close();
			} catch (Exception ignored) {
			}
		}

		private void capturePreviewFrame() throws IOException {
			String targetStreamUrl;
			long seekMs;
			synchronized (this.lock) {
				if (this.streamUrl.isBlank()) {
					return;
				}
				targetStreamUrl = this.streamUrl;
				seekMs = this.positionMs;
			}
			byte[] previewBytes = runPreviewFrameCommandBytes(previewFrameCommand(targetStreamUrl, seekMs), STREAM_START_TIMEOUT_SEC);
			BufferedImage preview = decodeImageBytes(previewBytes);
			synchronized (this.lock) {
				this.latestFrame = preview;
				this.frameSequence++;
				cachePreviewFrameLocked(seekMs, previewBytes, false);
			}
		}

		private void startStream() throws IOException {
			Process startedProcess;
			synchronized (this.lock) {
				if (this.streamUrl.isBlank()) {
					throw new IOException("session is not loaded");
				}
				stopLocked();
				List<String> command = new ArrayList<>();
				command.add(ffmpegBin());
				command.add("-hide_banner");
				command.add("-loglevel");
				command.add("error");
				command.add("-nostdin");
				if (!this.live && this.positionMs > 0L) {
					command.add("-ss");
					command.add(String.format(Locale.ROOT, "%.3f", this.positionMs / 1000.0D));
				}
				command.add("-re");
				command.add("-i");
				command.add(this.streamUrl);
				command.add("-an");
				command.add("-vf");
				command.add("fps=" + FRAME_RATE + ",scale=w=" + FRAME_WIDTH + ":h=-2:force_original_aspect_ratio=decrease");
				command.add("-q:v");
				command.add("5");
				command.add("-f");
				command.add("image2pipe");
				command.add("-vcodec");
				command.add("mjpeg");
				command.add("-");

				ProcessBuilder builder = new ProcessBuilder(command);
				builder.redirectError(ProcessBuilder.Redirect.DISCARD);
				startedProcess = builder.start();
				this.process = startedProcess;
				this.playBasePositionMs = this.positionMs;
				this.playStartedAtNanos = System.nanoTime();
				this.lastAccessAtMillis = System.currentTimeMillis();
				Thread thread = new Thread(() -> readFrames(startedProcess), "lg2-youtube-" + this.sessionId);
				thread.setDaemon(true);
				this.readerThread = thread;
				thread.start();
			}
		}

		private void readFrames(Process processToRead) {
			try (InputStream stream = processToRead.getInputStream()) {
				readJpegFrames(stream, frameBytes -> {
					BufferedImage image = ImageIO.read(new ByteArrayInputStream(frameBytes));
					if (image == null) {
						return;
					}
					synchronized (this.lock) {
						if (this.process != processToRead) {
							return;
						}
						long framePositionMs = currentPositionMsLocked();
						this.latestFrame = image;
						this.frameSequence++;
						cachePreviewFrameLocked(framePositionMs, frameBytes, shouldExtendBufferedRangeLocked(framePositionMs));
						if (this.paused) {
							this.status = "PAUSED";
						} else if (this.live) {
							this.status = "LIVE";
						} else {
							this.status = "PLAYING";
						}
					}
				});
			} catch (IOException exception) {
				if (!isExpectedStreamShutdown(processToRead, exception, false)) {
					Lg2.LOGGER.warn("YouTube frame reader stopped for session {}", this.sessionId, exception);
				}
			} finally {
				synchronized (this.lock) {
					if (this.process == processToRead) {
						if (!this.live && !this.paused) {
							this.positionMs = currentPositionMsLocked();
						}
						if (!Objects.equals(this.status, "PAUSED") && !Objects.equals(this.status, "CLOSED")) {
							this.status = this.sourceUrl.isBlank() ? "IDLE" : "BUFFERING";
						}
						this.process = null;
						this.readerThread = null;
					}
				}
				processToRead.destroy();
			}
		}

		private void ensurePrefetchStarted() {
			try {
				synchronized (this.lock) {
					startPrefetchLocked();
				}
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to start YouTube prefetch for session {}", this.sessionId, exception);
			}
		}

		private void startPrefetchLocked() throws IOException {
			if (this.live || this.streamUrl.isBlank() || closedOrUnloadedLocked() || this.prefetchCompleted) {
				return;
			}
			if (this.prefetchProcess != null || (this.prefetchReaderThread != null && this.prefetchReaderThread.isAlive())) {
				return;
			}
			ProcessBuilder builder = new ProcessBuilder(sequentialPrefetchCommand(this.streamUrl));
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process startedProcess = builder.start();
			this.prefetchProcess = startedProcess;
			Thread thread = new Thread(() -> readPrefetchFrames(startedProcess), "lg2-youtube-prefetch-" + this.sessionId);
			thread.setDaemon(true);
			this.prefetchReaderThread = thread;
			thread.start();
		}

		private void readPrefetchFrames(Process processToRead) {
			long[] nextPositionMs = {0L};
			boolean completedNormally = false;
			try (InputStream stream = processToRead.getInputStream()) {
				readJpegFrames(stream, frameBytes -> {
					long positionMs = nextPositionMs[0];
					nextPositionMs[0] += PREVIEW_CACHE_BUCKET_MS;
					synchronized (this.lock) {
						if (this.prefetchProcess != processToRead) {
							return;
						}
						cachePreviewFrameLocked(positionMs, frameBytes, true);
					}
				});
				completedNormally = true;
			} catch (IOException exception) {
				if (!isExpectedStreamShutdown(processToRead, exception, true)) {
					Lg2.LOGGER.debug("YouTube prefetch reader stopped for session {}", this.sessionId, exception);
				}
			} finally {
				synchronized (this.lock) {
					if (this.prefetchProcess == processToRead) {
						this.prefetchProcess = null;
						this.prefetchReaderThread = null;
						if (completedNormally) {
							this.prefetchCompleted = true;
						}
					}
				}
				processToRead.destroy();
			}
		}

		private long currentPositionMsLocked() {
			if (this.live || this.paused || this.process == null) {
				return this.positionMs;
			}
			long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.playStartedAtNanos);
			long position = this.playBasePositionMs + elapsedMs;
			if (this.durationMs > 0L) {
				position = Math.min(position, this.durationMs);
			}
			return Math.max(0L, position);
		}

		private boolean applyCachedPreviewLocked(long positionMs) throws IOException {
			byte[] previewBytes = findNearestCachedPreviewBytesLocked(positionMs);
			if (previewBytes == null) {
				return false;
			}
			this.latestFrame = decodeImageBytes(previewBytes);
			this.frameSequence++;
			return true;
		}

		private byte[] findNearestCachedPreviewBytesLocked(long targetPositionMs) {
			if (this.cachedPreviewFrames.isEmpty()) {
				return null;
			}
			Map.Entry<Long, byte[]> floor = this.cachedPreviewFrames.floorEntry(targetPositionMs);
			Map.Entry<Long, byte[]> ceiling = this.cachedPreviewFrames.ceilingEntry(targetPositionMs);
			Map.Entry<Long, byte[]> candidate = null;
			if (floor != null && ceiling != null) {
				long floorDistance = Math.abs(targetPositionMs - floor.getKey());
				long ceilingDistance = Math.abs(ceiling.getKey() - targetPositionMs);
				candidate = floorDistance <= ceilingDistance ? floor : ceiling;
			} else if (floor != null) {
				candidate = floor;
			} else if (ceiling != null) {
				candidate = ceiling;
			}
			if (candidate == null || Math.abs(candidate.getKey() - targetPositionMs) > CACHE_LOOKUP_TOLERANCE_MS) {
				return null;
			}
			return candidate.getValue();
		}

		private boolean shouldExtendBufferedRangeLocked(long positionMs) {
			long bucketMs = normalizeBucketMs(positionMs);
			if (!this.bufferedFromStart) {
				return bucketMs == 0L;
			}
			return bucketMs <= this.bufferedEndMs + CONTIGUOUS_BUFFER_TOLERANCE_MS;
		}

		private void cachePreviewFrameLocked(long positionMs, byte[] frameBytes, boolean extendBufferedRange) {
			if (this.live || frameBytes == null || frameBytes.length == 0) {
				return;
			}
			long bucketMs = normalizeBucketMs(positionMs);
			if (!this.cachedPreviewFrames.containsKey(bucketMs)) {
				this.cachedPreviewFrames.put(bucketMs, Arrays.copyOf(frameBytes, frameBytes.length));
			}
			while (this.cachedPreviewFrames.size() > MAX_CACHED_PREVIEW_FRAMES) {
				this.cachedPreviewFrames.pollFirstEntry();
			}
			if (extendBufferedRange) {
				advanceBufferedRangeLocked(bucketMs);
			}
		}

		private void advanceBufferedRangeLocked(long bucketMs) {
			if (!this.bufferedFromStart) {
				if (bucketMs != 0L) {
					return;
				}
				this.bufferedFromStart = true;
				this.bufferedStartMs = 0L;
				this.bufferedEndMs = 0L;
				return;
			}
			if (bucketMs <= this.bufferedEndMs + CONTIGUOUS_BUFFER_TOLERANCE_MS) {
				this.bufferedStartMs = 0L;
				this.bufferedEndMs = Math.max(this.bufferedEndMs, bucketMs);
			}
		}

		private boolean closedOrUnloadedLocked() {
			return Objects.equals(this.status, "CLOSED") || this.streamUrl.isBlank();
		}

		private long normalizeBucketMs(long positionMs) {
			return Math.max(0L, (positionMs / PREVIEW_CACHE_BUCKET_MS) * PREVIEW_CACHE_BUCKET_MS);
		}

		private boolean isExpectedStreamShutdown(Process processToRead, IOException exception, boolean prefetch) {
			String message = exception.getMessage();
			synchronized (this.lock) {
				if (Thread.currentThread().isInterrupted()) {
					return true;
				}
				if (message != null && message.contains("Stream closed")) {
					return true;
				}
				if (prefetch) {
					return this.prefetchProcess != processToRead || Objects.equals(this.status, "CLOSED");
				}
				return this.process != processToRead || Objects.equals(this.status, "PAUSED") || Objects.equals(this.status, "CLOSED");
			}
		}

		private void stopLocked() {
			Process running = this.process;
			this.process = null;
			this.readerThread = null;
			if (running == null) {
				return;
			}
			running.destroy();
			try {
				if (!running.waitFor(PROCESS_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
					running.destroyForcibly();
					running.waitFor(PROCESS_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				running.destroyForcibly();
			}
		}

		private void stopPrefetchLocked() {
			Process running = this.prefetchProcess;
			this.prefetchProcess = null;
			this.prefetchReaderThread = null;
			if (running == null) {
				return;
			}
			running.destroy();
			try {
				if (!running.waitFor(PROCESS_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
					running.destroyForcibly();
					running.waitFor(PROCESS_STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				running.destroyForcibly();
			}
		}
	}
}
