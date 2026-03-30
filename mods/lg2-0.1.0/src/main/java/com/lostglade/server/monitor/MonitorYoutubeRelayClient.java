package com.lostglade.server.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.server.progress.TaskProgress;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.TreeMap;

public final class MonitorYoutubeRelayClient {
	private static final Gson GSON = new Gson();
	private static final Map<String, RelaySession> SESSIONS = new ConcurrentHashMap<>();
	private static final Map<String, QueuePreloadState> QUEUE_PRELOADS = new ConcurrentHashMap<>();
	private static final ScheduledExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("lg2-youtube-cleanup"));
	private static final ExecutorService PRELOAD_EXECUTOR = Executors.newFixedThreadPool(2, daemonThreadFactory("lg2-youtube-queue"));
	private static final String DEFAULT_YT_DLP_BIN = "yt-dlp";
	private static final String DEFAULT_FFMPEG_BIN = "ffmpeg";
	private static final double FRAME_RATE = readDoubleSetting("LG2_YT_FRAME_RATE", "lg2.youtube.frameRate", 12.0D);
	private static final int FRAME_WIDTH = readIntSetting("LG2_YT_FRAME_WIDTH", "lg2.youtube.frameWidth", 480);
	private static final long SESSION_IDLE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(readIntSetting("LG2_YT_IDLE_TIMEOUT_SEC", "lg2.youtube.idleTimeoutSec", 600));
	private static final int COMMAND_TIMEOUT_SEC = readIntSetting("LG2_YT_COMMAND_TIMEOUT_SEC", "lg2.youtube.commandTimeoutSec", 45);
	private static final int STREAM_START_TIMEOUT_SEC = readIntSetting("LG2_YT_STREAM_START_TIMEOUT_SEC", "lg2.youtube.streamStartTimeoutSec", 20);
	private static final long PREVIEW_CACHE_BUCKET_MS = Math.max(1L, Math.round(1000.0D / Math.max(1.0D, FRAME_RATE)));
	private static final int MAX_CACHED_PREVIEW_FRAMES = 3600;
	private static final long QUEUE_PRELOAD_DURATION_MS = TimeUnit.SECONDS.toMillis(readIntSetting("LG2_YT_QUEUE_PRELOAD_SEC", "lg2.youtube.queuePreloadSec", 20));
	private static final long CURRENT_VIDEO_CACHE_BEHIND_MS = TimeUnit.MINUTES.toMillis(readIntSetting("LG2_YT_CURRENT_CACHE_BEHIND_MIN", "lg2.youtube.currentCacheBehindMin", 30));
	private static final long CURRENT_VIDEO_CACHE_AHEAD_MS = TimeUnit.MINUTES.toMillis(readIntSetting("LG2_YT_CURRENT_CACHE_AHEAD_MIN", "lg2.youtube.currentCacheAheadMin", 30));
	private static final int MAX_SESSION_CACHED_PREVIEW_FRAMES = Math.max(
			MAX_CACHED_PREVIEW_FRAMES,
			(int) Math.ceil((CURRENT_VIDEO_CACHE_BEHIND_MS + CURRENT_VIDEO_CACHE_AHEAD_MS) / (double) PREVIEW_CACHE_BUCKET_MS) + 512
	);
	private static final long CACHE_LOOKUP_TOLERANCE_MS = Math.max(100L, PREVIEW_CACHE_BUCKET_MS * 2L);
	private static final long CONTIGUOUS_BUFFER_TOLERANCE_MS = PREVIEW_CACHE_BUCKET_MS + CACHE_LOOKUP_TOLERANCE_MS;
	private static final long PROCESS_STOP_TIMEOUT_MS = 250L;
	private static final Path PERSISTENT_PRELOAD_CACHE_ROOT = Path.of(System.getProperty("user.dir"), ".lg2-cache", "youtube-preload");

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

	public static QueueResolveResponse resolveQueue(String rawUrl) throws IOException {
		validateYoutubeUrl(rawUrl);
		return resolveYoutubeQueue(rawUrl.trim());
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

	public static void requestFullCache(String sessionId) throws IOException {
		requireSession(sessionId).requestFullCache();
	}

	public static long queuePreloadDurationMs() {
		return QUEUE_PRELOAD_DURATION_MS;
	}

	public static void persistQueueEntryFromSession(String sessionId, String rawUrl) throws IOException {
		if (!looksLikeYoutubeUrl(rawUrl)) {
			return;
		}
		requireSession(sessionId).persistQueuePreload(rawUrl.trim());
	}

	public static void close(String sessionId) throws IOException {
		RelaySession session = SESSIONS.remove(sessionId);
		if (session == null) {
			return;
		}
		session.close();
	}

	public static void retainQueueEntry(String rawUrl) {
		if (!looksLikeYoutubeUrl(rawUrl)) {
			return;
		}
		String url = rawUrl.trim();
		QUEUE_PRELOADS.compute(url, (ignored, existing) -> {
			QueuePreloadState state = existing != null ? existing : new QueuePreloadState(url);
			state.retain();
			return state;
		});
	}

	public static void releaseQueueEntry(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return;
		}
		String url = rawUrl.trim();
		QueuePreloadState state = QUEUE_PRELOADS.get(url);
		if (state == null) {
			return;
		}
		if (state.release()) {
			QUEUE_PRELOADS.remove(url, state);
		}
	}

	public static boolean isQueueEntryLoaded(String rawUrl) {
		if (!looksLikeYoutubeUrl(rawUrl)) {
			return false;
		}
		String url = rawUrl.trim();
		QueuePreloadState state = QUEUE_PRELOADS.get(url);
		if (state != null) {
			return state.isLoaded();
		}
		return isQueuePreloadReady(loadPersistentQueuePreload(url));
	}

	public static BufferedImage queueEntryPreview(String rawUrl) {
		if (!looksLikeYoutubeUrl(rawUrl)) {
			return null;
		}
		QueuePreloadSnapshot snapshot = snapshotQueuePreload(rawUrl.trim());
		if (snapshot == null || snapshot.cachedPreviewFrames() == null || snapshot.cachedPreviewFrames().isEmpty()) {
			return null;
		}
		CachedPreviewFrame frame = snapshot.cachedPreviewFrames().firstEntry().getValue();
		if (frame == null) {
			return null;
		}
		BufferedImage cachedImage = frame.imageRef() != null ? frame.imageRef().get() : null;
		if (cachedImage != null) {
			return cachedImage;
		}
		try {
			return frame.bytes() != null && frame.bytes().length > 0 ? decodeImageBytes(frame.bytes()) : null;
		} catch (IOException exception) {
			Lg2.LOGGER.debug("Failed to decode cached YouTube gallery preview for {}", rawUrl, exception);
			return null;
		}
	}

	public static void shutdown() {
		for (RelaySession session : List.copyOf(SESSIONS.values())) {
			session.closeQuietly();
		}
		SESSIONS.clear();
		for (QueuePreloadState preload : List.copyOf(QUEUE_PRELOADS.values())) {
			preload.closeQuietly();
		}
		QUEUE_PRELOADS.clear();
		CLEANUP_EXECUTOR.shutdownNow();
		PRELOAD_EXECUTOR.shutdownNow();
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

	private static QueuePreloadSnapshot snapshotQueuePreload(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		QueuePreloadState state = QUEUE_PRELOADS.get(url.trim());
		return state != null ? state.snapshot() : loadPersistentQueuePreload(url.trim());
	}

	private static boolean isQueuePreloadReady(QueuePreloadSnapshot snapshot) {
		if (snapshot == null || snapshot.resolved() == null || snapshot.resolved().live()) {
			return false;
		}
		return snapshot.bufferedFromStart()
				&& snapshot.bufferedEndMs() + CONTIGUOUS_BUFFER_TOLERANCE_MS >= Math.max(0L, QUEUE_PRELOAD_DURATION_MS - PREVIEW_CACHE_BUCKET_MS);
	}

	private static Path persistentQueuePreloadDir(String url) {
		return PERSISTENT_PRELOAD_CACHE_ROOT.resolve(urlCacheKey(url));
	}

	private static String urlCacheKey(String url) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Missing SHA-256", exception);
		}
	}

	private static QueuePreloadSnapshot loadPersistentQueuePreload(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		Path cacheDir = persistentQueuePreloadDir(url);
		Path metadataPath = cacheDir.resolve("meta.json");
		if (!Files.isRegularFile(metadataPath)) {
			return null;
		}
		try {
			JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
			if (metadata == null || !url.equals(getString(metadata, "url", ""))) {
				return null;
			}
			ResolvedYoutube resolved = new ResolvedYoutube(
					getString(metadata, "title", "YouTube"),
					Math.max(0L, getLong(metadata, "durationMs", 0L)),
					getBoolean(metadata, "live", false),
					getString(metadata, "streamUrl", ""),
					getString(metadata, "audioStreamUrl", "")
			);
			JsonArray framesArray = metadata.getAsJsonArray("frames");
			if (framesArray == null || framesArray.isEmpty()) {
				return null;
			}
			NavigableMap<Long, CachedPreviewFrame> frames = new TreeMap<>();
			for (JsonElement element : framesArray) {
				if (element == null || !element.isJsonObject()) {
					continue;
				}
				JsonObject frameObject = element.getAsJsonObject();
				long bucketMs = Math.max(0L, getLong(frameObject, "bucketMs", -1L));
				String fileName = getString(frameObject, "file", "").trim();
				if (bucketMs < 0L || fileName.isBlank()) {
					continue;
				}
				Path framePath = cacheDir.resolve(fileName);
				if (!Files.isRegularFile(framePath)) {
					continue;
				}
				byte[] bytes = Files.readAllBytes(framePath);
				if (bytes.length == 0) {
					continue;
				}
				frames.put(bucketMs, new CachedPreviewFrame(bytes, null));
			}
			if (frames.isEmpty()) {
				return null;
			}
			QueueBufferRange range = computeQueueBufferRange(frames);
			return new QueuePreloadSnapshot(
					resolved,
					frames,
					range.bufferedStartMs(),
					range.bufferedEndMs(),
					range.bufferedFromStart()
			);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to load persistent YouTube preload cache for {}", url, exception);
			return null;
		}
	}

	private static void persistQueuePreloadSnapshot(String url, QueuePreloadSnapshot snapshot) {
		if (url == null || url.isBlank() || snapshot == null || snapshot.resolved() == null || snapshot.resolved().live()) {
			return;
		}
		Path cacheDir = persistentQueuePreloadDir(url);
		try {
			Files.createDirectories(cacheDir);
			JsonArray framesArray = new JsonArray();
			for (Map.Entry<Long, CachedPreviewFrame> entry : snapshot.cachedPreviewFrames().entrySet()) {
				CachedPreviewFrame frame = entry.getValue();
				if (frame == null || frame.bytes() == null || frame.bytes().length == 0) {
					continue;
				}
				long bucketMs = entry.getKey();
				String fileName = bucketMs + ".jpg";
				Files.write(cacheDir.resolve(fileName), frame.bytes());
				JsonObject frameObject = new JsonObject();
				frameObject.addProperty("bucketMs", bucketMs);
				frameObject.addProperty("file", fileName);
				framesArray.add(frameObject);
			}
			if (framesArray.isEmpty()) {
				return;
			}
			JsonObject metadata = new JsonObject();
			metadata.addProperty("url", url);
			metadata.addProperty("title", snapshot.resolved().title());
			metadata.addProperty("durationMs", snapshot.resolved().durationMs());
			metadata.addProperty("live", snapshot.resolved().live());
			metadata.addProperty("streamUrl", snapshot.resolved().streamUrl());
			metadata.addProperty("audioStreamUrl", snapshot.resolved().audioStreamUrl());
			metadata.addProperty("bufferedStartMs", snapshot.bufferedStartMs());
			metadata.addProperty("bufferedEndMs", snapshot.bufferedEndMs());
			metadata.addProperty("bufferedFromStart", snapshot.bufferedFromStart());
			metadata.add("frames", framesArray);
			Files.writeString(cacheDir.resolve("meta.json"), GSON.toJson(metadata), StandardCharsets.UTF_8);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to persist YouTube preload cache for {}", url, exception);
		}
	}

	private static QueueBufferRange computeQueueBufferRange(NavigableMap<Long, CachedPreviewFrame> frames) {
		if (frames == null || frames.isEmpty()) {
			return new QueueBufferRange(0L, 0L, false);
		}
		long startMs = frames.firstKey();
		long endMs = startMs;
		for (Long candidateMs : frames.tailMap(startMs, false).keySet()) {
			if (candidateMs > endMs + CONTIGUOUS_BUFFER_TOLERANCE_MS) {
				break;
			}
			endMs = candidateMs;
		}
		return new QueueBufferRange(startMs, endMs, startMs == 0L);
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

	private static QueueResolveResponse resolveYoutubeQueue(String url) throws IOException {
		String metadataJson = runTextCommand(List.of(ytDlpBin(), "--dump-single-json", "--flat-playlist", url), COMMAND_TIMEOUT_SEC);
		JsonObject metadata = GSON.fromJson(metadataJson, JsonObject.class);
		String containerTitle = getString(metadata, "title", "YouTube");
		JsonArray entriesArray = metadata != null && metadata.has("entries") && metadata.get("entries").isJsonArray()
				? metadata.getAsJsonArray("entries")
				: null;
		List<QueueEntry> entries = new ArrayList<>();
		if (entriesArray != null && !entriesArray.isEmpty()) {
			for (JsonElement element : entriesArray) {
				if (element == null || !element.isJsonObject()) {
					continue;
				}
				JsonObject entry = element.getAsJsonObject();
				String entryUrl = resolveQueueEntryUrl(entry);
				if (entryUrl == null || entryUrl.isBlank()) {
					continue;
				}
				String entryTitle = getString(entry, "title", "YouTube");
				entries.add(new QueueEntry(entryTitle, entryUrl));
			}
			if (!entries.isEmpty()) {
				return new QueueResolveResponse(containerTitle, entries, true);
			}
		}
		String canonicalUrl = getString(metadata, "webpage_url", url);
		return new QueueResolveResponse(containerTitle, List.of(new QueueEntry(containerTitle, canonicalUrl)), false);
	}

	private static String resolveQueueEntryUrl(JsonObject entry) {
		String webpageUrl = getString(entry, "webpage_url", "");
		if (!webpageUrl.isBlank()) {
			return webpageUrl;
		}
		String rawUrl = getString(entry, "url", "");
		if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
			return rawUrl;
		}
		String id = getString(entry, "id", rawUrl);
		if (id == null || id.isBlank()) {
			return "";
		}
		return "https://www.youtube.com/watch?v=" + id.trim();
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

	private static List<String> sequentialPrefetchCommand(String streamUrl, long startMs, long durationMs) {
		List<String> command = new ArrayList<>();
		command.add(ffmpegBin());
		command.add("-hide_banner");
		command.add("-loglevel");
		command.add("error");
		command.add("-nostdin");
		command.add("-threads");
		command.add("1");
		if (startMs > 0L) {
			command.add("-ss");
			command.add(String.format(Locale.ROOT, "%.3f", startMs / 1000.0D));
		}
		if (durationMs > 0L) {
			command.add("-t");
			command.add(String.format(Locale.ROOT, "%.3f", durationMs / 1000.0D));
		}
		command.add("-i");
		command.add(streamUrl);
		command.add("-an");
		command.add("-vf");
		command.add("fps=" + FRAME_RATE + ",scale=w=" + FRAME_WIDTH + ":h=-2:force_original_aspect_ratio=decrease");
		command.add("-q:v");
		command.add("6");
		command.add("-f");
		command.add("image2pipe");
		command.add("-vcodec");
		command.add("mjpeg");
		command.add("-");
		return command;
	}

	private static List<String> queuePreloadCommand(String streamUrl) {
		List<String> command = new ArrayList<>();
		command.add(ffmpegBin());
		command.add("-hide_banner");
		command.add("-loglevel");
		command.add("error");
		command.add("-nostdin");
		command.add("-threads");
		command.add("1");
		command.add("-t");
		command.add(String.format(Locale.ROOT, "%.3f", QUEUE_PRELOAD_DURATION_MS / 1000.0D));
		command.add("-i");
		command.add(streamUrl);
		command.add("-an");
		command.add("-vf");
		command.add("fps=" + FRAME_RATE + ",scale=w=" + FRAME_WIDTH + ":h=-2:force_original_aspect_ratio=decrease");
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

	private static long normalizeBucketMs(long positionMs) {
		return Math.max(0L, (positionMs / PREVIEW_CACHE_BUCKET_MS) * PREVIEW_CACHE_BUCKET_MS);
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

	private static long getLong(JsonObject object, String key, long fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
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

	public record QueueResolveResponse(
			String title,
			List<QueueEntry> entries,
			boolean playlist
	) {
	}

	public record QueueEntry(
			String title,
			String url
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
			boolean ended,
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

	private record CachedPreviewFrame(byte[] bytes, SoftReference<BufferedImage> imageRef) {
	}

	private record QueuePreloadSnapshot(
			ResolvedYoutube resolved,
			NavigableMap<Long, CachedPreviewFrame> cachedPreviewFrames,
			long bufferedStartMs,
			long bufferedEndMs,
			boolean bufferedFromStart
	) {
	}

	private record QueueBufferRange(long bufferedStartMs, long bufferedEndMs, boolean bufferedFromStart) {
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

	private static final class QueuePreloadState {
		private final String sourceUrl;
		private final Object lock = new Object();
		private int retainCount = 0;
		private ResolvedYoutube resolved = null;
		private Process process = null;
		private boolean started = false;
		private boolean preloadComplete = false;
		private boolean bufferedFromStart = false;
		private long bufferedStartMs = 0L;
		private long bufferedEndMs = 0L;
		private final NavigableMap<Long, CachedPreviewFrame> cachedPreviewFrames = new TreeMap<>();

		private QueuePreloadState(String sourceUrl) {
			this.sourceUrl = sourceUrl;
		}

		private void retain() {
			boolean shouldStart = false;
			synchronized (this.lock) {
				loadPersistentCacheLocked();
				this.retainCount++;
				if (!this.started && !this.preloadComplete) {
					this.started = true;
					shouldStart = true;
				}
			}
			if (shouldStart) {
				PRELOAD_EXECUTOR.execute(this::runPreload);
			}
		}

		private boolean release() {
			synchronized (this.lock) {
				this.retainCount = Math.max(0, this.retainCount - 1);
				if (this.retainCount > 0) {
					return false;
				}
				stopLocked();
				this.cachedPreviewFrames.clear();
				this.resolved = null;
				this.bufferedStartMs = 0L;
				this.bufferedEndMs = 0L;
				this.bufferedFromStart = false;
				this.preloadComplete = false;
				this.started = false;
				return true;
			}
		}

		private void closeQuietly() {
			synchronized (this.lock) {
				this.retainCount = 0;
				stopLocked();
				this.cachedPreviewFrames.clear();
				this.resolved = null;
				this.bufferedStartMs = 0L;
				this.bufferedEndMs = 0L;
				this.bufferedFromStart = false;
				this.preloadComplete = false;
				this.started = false;
			}
		}

		private QueuePreloadSnapshot snapshot() {
			synchronized (this.lock) {
				loadPersistentCacheLocked();
				if (this.resolved == null) {
					return null;
				}
				return new QueuePreloadSnapshot(
						this.resolved,
						new TreeMap<>(this.cachedPreviewFrames),
						this.bufferedStartMs,
						this.bufferedEndMs,
						this.bufferedFromStart
				);
			}
		}

		private boolean isLoaded() {
			synchronized (this.lock) {
				loadPersistentCacheLocked();
				return isQueuePreloadReady(this.resolved == null ? null : new QueuePreloadSnapshot(
						this.resolved,
						new TreeMap<>(this.cachedPreviewFrames),
						this.bufferedStartMs,
						this.bufferedEndMs,
						this.bufferedFromStart
				));
			}
		}

		private void runPreload() {
			Process startedProcess = null;
			boolean preloadFinished = false;
			QueuePreloadSnapshot snapshotToPersist = null;
			try {
				ResolvedYoutube resolvedLocal = resolveYoutube(this.sourceUrl);
				synchronized (this.lock) {
					if (this.retainCount <= 0) {
						this.started = false;
						return;
					}
					this.resolved = resolvedLocal;
					if (resolvedLocal.live()) {
						this.preloadComplete = true;
						this.started = false;
						return;
					}
				}

				ProcessBuilder builder = new ProcessBuilder(queuePreloadCommand(resolvedLocal.streamUrl()));
				builder.redirectError(ProcessBuilder.Redirect.DISCARD);
				startedProcess = builder.start();
				Process processRef = startedProcess;
				synchronized (this.lock) {
					if (this.retainCount <= 0) {
						processRef.destroyForcibly();
						this.started = false;
						return;
					}
					this.process = processRef;
				}

				long[] nextPositionMs = {0L};
				try (InputStream stream = processRef.getInputStream()) {
					readJpegFrames(stream, frameBytes -> {
						BufferedImage image = decodeImageBytes(frameBytes);
						long positionMs = nextPositionMs[0];
						nextPositionMs[0] += PREVIEW_CACHE_BUCKET_MS;
						synchronized (this.lock) {
							if (this.process != processRef || this.retainCount <= 0) {
								return;
							}
							cachePreviewFrameLocked(positionMs, frameBytes, image);
						}
					});
				}
				preloadFinished = true;
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to preload YouTube queue entry {}", this.sourceUrl, exception);
			} finally {
				synchronized (this.lock) {
					if (this.process == startedProcess) {
						this.process = null;
					}
					this.preloadComplete = preloadFinished;
					if (preloadFinished && this.resolved != null) {
						QueuePreloadSnapshot snapshot = new QueuePreloadSnapshot(
								this.resolved,
								new TreeMap<>(this.cachedPreviewFrames),
								this.bufferedStartMs,
								this.bufferedEndMs,
								this.bufferedFromStart
						);
						if (isQueuePreloadReady(snapshot)) {
							snapshotToPersist = snapshot;
						}
					}
					this.started = false;
				}
				if (startedProcess != null) {
					startedProcess.destroy();
				}
				if (snapshotToPersist != null) {
					persistQueuePreloadSnapshot(this.sourceUrl, snapshotToPersist);
				}
			}
		}

		private void cachePreviewFrameLocked(long positionMs, byte[] frameBytes, BufferedImage decodedImage) {
			if (frameBytes == null || frameBytes.length == 0) {
				return;
			}
			long bucketMs = normalizeBucketMs(positionMs);
			this.cachedPreviewFrames.putIfAbsent(
					bucketMs,
					new CachedPreviewFrame(
							Arrays.copyOf(frameBytes, frameBytes.length),
							decodedImage != null ? new SoftReference<>(decodedImage) : null
					)
			);
			while (this.cachedPreviewFrames.size() > MAX_CACHED_PREVIEW_FRAMES) {
				this.cachedPreviewFrames.pollFirstEntry();
			}
			recomputeBufferedRangeLocked();
		}

		private void recomputeBufferedRangeLocked() {
			QueueBufferRange range = computeQueueBufferRange(this.cachedPreviewFrames);
			this.bufferedStartMs = range.bufferedStartMs();
			this.bufferedEndMs = range.bufferedEndMs();
			this.bufferedFromStart = range.bufferedFromStart();
		}

		private void loadPersistentCacheLocked() {
			if ((this.resolved != null && !this.cachedPreviewFrames.isEmpty()) || this.started) {
				return;
			}
			QueuePreloadSnapshot snapshot = loadPersistentQueuePreload(this.sourceUrl);
			if (snapshot == null || snapshot.resolved() == null) {
				return;
			}
			this.resolved = snapshot.resolved();
			this.cachedPreviewFrames.clear();
			this.cachedPreviewFrames.putAll(snapshot.cachedPreviewFrames());
			this.bufferedStartMs = snapshot.bufferedStartMs();
			this.bufferedEndMs = snapshot.bufferedEndMs();
			this.bufferedFromStart = snapshot.bufferedFromStart();
			this.preloadComplete = isQueuePreloadReady(snapshot);
		}

		private void stopLocked() {
			Process running = this.process;
			this.process = null;
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
		private long latestFrameBucketMs = Long.MIN_VALUE;
		private long lastAccessAtMillis = System.currentTimeMillis();
		private Process process = null;
		private Thread readerThread = null;
		private Process prefetchProcess = null;
		private Thread prefetchReaderThread = null;
		private boolean sourceRefreshInProgress = false;
		private long playStartedAtNanos = 0L;
		private long playBasePositionMs = 0L;
		private long bufferedStartMs = 0L;
		private long bufferedEndMs = 0L;
		private boolean bufferedFromStart = false;
		private boolean prefetchCompleted = false;
		private boolean fullCacheRequested = false;
		private final NavigableMap<Long, CachedPreviewFrame> cachedPreviewFrames = new TreeMap<>();

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
			QueuePreloadSnapshot queuePreload = snapshotQueuePreload(url);
			ResolvedYoutube resolved = queuePreload != null && queuePreload.resolved() != null
					? queuePreload.resolved()
					: resolveYoutube(url);
			boolean loadedFromQueuePreload = queuePreload != null && queuePreload.resolved() != null;
			boolean hadPreloadedFrame = false;
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
				this.latestFrameBucketMs = Long.MIN_VALUE;
				this.bufferedStartMs = 0L;
				this.bufferedEndMs = 0L;
				this.bufferedFromStart = false;
				this.prefetchCompleted = false;
				this.fullCacheRequested = false;
				this.cachedPreviewFrames.clear();
				if (queuePreload != null && !resolved.live()) {
					this.cachedPreviewFrames.putAll(queuePreload.cachedPreviewFrames());
					this.bufferedStartMs = queuePreload.bufferedStartMs();
					this.bufferedEndMs = queuePreload.bufferedEndMs();
					this.bufferedFromStart = queuePreload.bufferedFromStart();
					try {
						hadPreloadedFrame = applyCachedPreviewLocked(0L);
					} catch (IOException exception) {
						Lg2.LOGGER.debug("Failed to apply preloaded YouTube queue frame for {}", url, exception);
						this.latestFrame = null;
					}
				}
				this.playBasePositionMs = 0L;
				this.playStartedAtNanos = System.nanoTime();
				this.lastAccessAtMillis = System.currentTimeMillis();
			}
			if (resolved.live()) {
				startStream();
			} else {
				if (!hadPreloadedFrame) {
					tryCapturePreviewFrame("load");
				}
				synchronized (this.lock) {
					if (this.latestFrame != null) {
						this.status = "PLAYING";
					}
				}
			}
			if (loadedFromQueuePreload && !resolved.live()) {
				refreshResolvedSourceAsync(url);
			}
			ensurePrefetchStarted();
			synchronized (this.lock) {
				return new SessionLoadResponse(this.sessionId, this.title, this.durationMs, this.live, this.status, this.audioStreamUrl);
			}
		}

		private void refreshResolvedSourceAsync(String url) {
			if (url == null || url.isBlank()) {
				return;
			}
			synchronized (this.lock) {
				if (this.sourceRefreshInProgress || closedOrUnloadedLocked() || !Objects.equals(this.sourceUrl, url)) {
					return;
				}
				this.sourceRefreshInProgress = true;
			}
			PRELOAD_EXECUTOR.execute(() -> {
				ResolvedYoutube refreshed = null;
				try {
					refreshed = resolveYoutube(url);
				} catch (IOException exception) {
					Lg2.LOGGER.debug("Failed to refresh YouTube source for session {}", this.sessionId, exception);
				}
				boolean shouldRestartPrefetch = false;
				synchronized (this.lock) {
					this.sourceRefreshInProgress = false;
					if (closedOrUnloadedLocked() || !Objects.equals(this.sourceUrl, url) || refreshed == null) {
						return;
					}
					boolean streamChanged = !Objects.equals(this.streamUrl, refreshed.streamUrl());
					this.title = refreshed.title();
					this.durationMs = refreshed.durationMs();
					this.live = refreshed.live();
					this.streamUrl = refreshed.streamUrl();
					this.audioStreamUrl = refreshed.audioStreamUrl();
					if (!this.live) {
						this.prefetchCompleted = false;
						if (streamChanged && this.prefetchProcess != null) {
							stopPrefetchLocked();
						}
						shouldRestartPrefetch = this.prefetchProcess == null;
					}
				}
				if (shouldRestartPrefetch) {
					ensurePrefetchStarted();
				}
			});
		}

		private SessionSnapshot snapshot(long knownFrameSequence) {
			boolean shouldEnsurePrefetch;
			boolean ended = false;
			boolean ready;
			synchronized (this.lock) {
				if (this.live) {
					ready = this.latestFrame != null && !Objects.equals(this.status, "BUFFERING");
				} else if (this.paused) {
					boolean hasCachedFrame;
					try {
						hasCachedFrame = applyCachedPreviewLocked(this.positionMs);
					} catch (IOException exception) {
						Lg2.LOGGER.debug("Failed to decode cached YouTube frame for paused session {}", this.sessionId, exception);
						hasCachedFrame = false;
					}
					this.status = hasCachedFrame ? "PAUSED" : "BUFFERING";
					ready = hasCachedFrame;
				} else {
					long targetPositionMs = currentPositionMsLocked();
					if (this.durationMs > 0L && targetPositionMs >= this.durationMs) {
						targetPositionMs = this.durationMs;
						ended = true;
					}
					boolean hasCachedFrame;
					try {
						hasCachedFrame = applyCachedPreviewLocked(targetPositionMs);
					} catch (IOException exception) {
						Lg2.LOGGER.debug("Failed to decode cached YouTube frame for session {}", this.sessionId, exception);
						hasCachedFrame = false;
					}
					if (hasCachedFrame) {
						this.positionMs = targetPositionMs;
						trimCachedPreviewWindowLocked();
						if (ended) {
							this.paused = true;
							this.status = "PAUSED";
						} else {
							this.status = "PLAYING";
						}
						ready = true;
					} else {
						freezePlaybackClockLocked();
						this.status = "BUFFERING";
						ready = false;
					}
				}
					if (!this.live && this.prefetchProcess == null && !closedOrUnloadedLocked()) {
						if (this.bufferedEndMs + CONTIGUOUS_BUFFER_TOLERANCE_MS < desiredCacheMaxMsLocked()) {
							this.prefetchCompleted = false;
						}
					}
					if (!this.live && !ready && !this.paused && this.prefetchProcess == null && !closedOrUnloadedLocked()) {
						this.prefetchCompleted = false;
					}
					shouldEnsurePrefetch = !this.live && !this.prefetchCompleted && this.prefetchProcess == null && !closedOrUnloadedLocked();
				BufferedImage frame = this.frameSequence != knownFrameSequence ? this.latestFrame : null;
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
						ended,
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
				if (this.live) {
					this.positionMs = currentPositionMsLocked();
				} else {
					long targetPositionMs = currentPositionMsLocked();
					boolean hasCachedFrame = false;
					try {
						hasCachedFrame = applyCachedPreviewLocked(targetPositionMs);
					} catch (IOException exception) {
						Lg2.LOGGER.debug("Failed to decode cached YouTube frame while pausing session {}", this.sessionId, exception);
					}
					if (hasCachedFrame) {
						this.positionMs = targetPositionMs;
						trimCachedPreviewWindowLocked();
					} else {
						freezePlaybackClockLocked();
					}
				}
				this.paused = true;
				this.status = this.live || hasCachedFrameForPositionLocked(this.positionMs) ? "PAUSED" : "BUFFERING";
				stopLocked();
			}
		}

		private void resume() throws IOException {
			boolean shouldStartLiveStream = false;
			synchronized (this.lock) {
				if (this.sourceUrl.isBlank() || this.streamUrl.isBlank()) {
					throw new IOException("session is not loaded");
				}
				applyCachedPreviewLocked(this.positionMs);
				this.playBasePositionMs = this.positionMs;
				this.playStartedAtNanos = System.nanoTime();
				this.paused = false;
				this.status = this.live ? "BUFFERING" : (hasCachedFrameForPositionLocked(this.positionMs) ? "PLAYING" : "BUFFERING");
				shouldStartLiveStream = this.live;
			}
			if (shouldStartLiveStream) {
				startStream();
			}
			ensurePrefetchStarted();
		}

		private void seek(long targetPositionMs) throws IOException {
			boolean capturePreview;
			boolean shouldStartLiveStream = false;
			synchronized (this.lock) {
				if (this.live) {
					throw new IOException("live stream is not seekable");
				}
					long clamped = Math.max(0L, this.durationMs > 0L ? Math.min(targetPositionMs, this.durationMs) : targetPositionMs);
					this.positionMs = clamped;
					this.playBasePositionMs = clamped;
					this.playStartedAtNanos = System.nanoTime();
					trimCachedPreviewWindowLocked();
					boolean hadCachedPreview = applyCachedPreviewLocked(clamped);
				capturePreview = !hadCachedPreview;
				this.status = this.paused ? "PAUSED" : (hadCachedPreview ? "PLAYING" : "BUFFERING");
				stopLocked();
				shouldStartLiveStream = this.live && !this.paused;
			}
			if (capturePreview) {
				boolean captured = tryCapturePreviewFrame("seek");
				synchronized (this.lock) {
					if (captured) {
						this.status = this.paused ? "PAUSED" : "PLAYING";
					} else if (this.paused) {
						return;
					}
				}
			} else {
				synchronized (this.lock) {
					if (this.paused) {
						return;
					}
				}
				if (shouldStartLiveStream) {
					startStream();
				}
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
				long bucketMs = normalizeBucketMs(seekMs);
				this.latestFrame = preview;
				this.latestFrameBucketMs = bucketMs;
				this.frameSequence++;
				cachePreviewFrameLocked(seekMs, previewBytes, preview, false);
			}
		}

		private boolean tryCapturePreviewFrame(String reason) {
			try {
				capturePreviewFrame();
				return true;
			} catch (IOException exception) {
				synchronized (this.lock) {
					boolean hasCachedFrame = !this.live && hasCachedFrameForPositionLocked(this.positionMs);
					if (this.paused) {
						this.status = hasCachedFrame ? "PAUSED" : "BUFFERING";
					} else if (!this.live) {
						this.status = hasCachedFrame ? "PLAYING" : "BUFFERING";
					}
				}
				if (shouldLogPreviewFailure(reason, exception)) {
					Lg2.LOGGER.debug("Failed to capture YouTube preview frame during {} for session {}", reason, this.sessionId, exception);
				}
				return false;
			}
		}

		private boolean shouldLogPreviewFailure(String reason, IOException exception) {
			if ("seek".equals(reason)) {
				String message = exception.getMessage();
				if (message == null) {
					return false;
				}
				return !message.contains("Timed out waiting for ffmpeg preview frame")
						&& !message.contains("Failed to capture YouTube preview frame")
						&& !message.contains("ffmpeg returned an unreadable preview frame");
			}
			return true;
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
						this.latestFrameBucketMs = normalizeBucketMs(framePositionMs);
						this.frameSequence++;
						cachePreviewFrameLocked(framePositionMs, frameBytes, image, shouldExtendBufferedRangeLocked(framePositionMs));
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
			long startMs = nextPrefetchStartMsLocked();
			long maxTargetMs = desiredCacheMaxMsLocked();
			long durationMs = Math.max(0L, maxTargetMs - startMs + PREVIEW_CACHE_BUCKET_MS);
			if (this.durationMs > 0L && startMs >= this.durationMs) {
				this.prefetchCompleted = true;
				return;
			}
			if (durationMs <= 0L) {
				this.prefetchCompleted = true;
				return;
			}
			ProcessBuilder builder = new ProcessBuilder(sequentialPrefetchCommand(this.streamUrl, startMs, durationMs));
			builder.redirectError(ProcessBuilder.Redirect.DISCARD);
			Process startedProcess = builder.start();
			this.prefetchProcess = startedProcess;
			Thread thread = new Thread(() -> readPrefetchFrames(startedProcess, startMs), "lg2-youtube-prefetch-" + this.sessionId);
			thread.setDaemon(true);
			this.prefetchReaderThread = thread;
			thread.start();
		}

		private void readPrefetchFrames(Process processToRead, long startPositionMs) {
			long[] nextPositionMs = {Math.max(0L, normalizeBucketMs(startPositionMs))};
			boolean completedNormally = false;
			try (InputStream stream = processToRead.getInputStream()) {
				readJpegFrames(stream, frameBytes -> {
					long positionMs = nextPositionMs[0];
					nextPositionMs[0] += PREVIEW_CACHE_BUCKET_MS;
					BufferedImage image = decodeImageBytes(frameBytes);
					synchronized (this.lock) {
						if (this.prefetchProcess != processToRead) {
							return;
						}
						cachePreviewFrameLocked(positionMs, frameBytes, image, true);
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
			if (this.live || this.paused || this.playStartedAtNanos == 0L) {
				return this.positionMs;
			}
			long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - this.playStartedAtNanos);
			long position = this.playBasePositionMs + elapsedMs;
			if (this.durationMs > 0L) {
				position = Math.min(position, this.durationMs);
			}
			return Math.max(0L, position);
		}

		private void freezePlaybackClockLocked() {
			this.playBasePositionMs = this.positionMs;
			this.playStartedAtNanos = System.nanoTime();
		}

		private void persistQueuePreload(String url) {
			QueuePreloadSnapshot snapshot = null;
			synchronized (this.lock) {
				if (this.live || this.sourceUrl == null || !this.sourceUrl.equals(url) || this.cachedPreviewFrames.isEmpty()) {
					return;
				}
				NavigableMap<Long, CachedPreviewFrame> frames = new TreeMap<>();
				long maxBucketMs = Math.max(0L, QUEUE_PRELOAD_DURATION_MS - PREVIEW_CACHE_BUCKET_MS);
				for (Map.Entry<Long, CachedPreviewFrame> entry : this.cachedPreviewFrames.entrySet()) {
					if (entry.getKey() > maxBucketMs) {
						break;
					}
					CachedPreviewFrame frame = entry.getValue();
					if (frame == null || frame.bytes() == null || frame.bytes().length == 0) {
						continue;
					}
					frames.put(
							entry.getKey(),
							new CachedPreviewFrame(
									Arrays.copyOf(frame.bytes(), frame.bytes().length),
									null
							)
					);
				}
				if (frames.isEmpty()) {
					return;
				}
				QueueBufferRange range = computeQueueBufferRange(frames);
				snapshot = new QueuePreloadSnapshot(
						new ResolvedYoutube(this.title, this.durationMs, this.live, this.streamUrl, this.audioStreamUrl),
						frames,
						range.bufferedStartMs(),
						range.bufferedEndMs(),
						range.bufferedFromStart()
				);
				if (!isQueuePreloadReady(snapshot)) {
					snapshot = null;
				}
			}
			if (snapshot != null) {
				persistQueuePreloadSnapshot(url, snapshot);
			}
		}

		private boolean applyCachedPreviewLocked(long positionMs) throws IOException {
			Map.Entry<Long, CachedPreviewFrame> previewEntry = findNearestCachedPreviewEntryLocked(positionMs);
			if (previewEntry == null) {
				return false;
			}
			long bucketMs = previewEntry.getKey();
			if (this.latestFrame != null && this.latestFrameBucketMs == bucketMs) {
				return true;
			}
			CachedPreviewFrame previewFrame = previewEntry.getValue();
			if (previewFrame == null || previewFrame.bytes() == null) {
				return false;
			}
			BufferedImage cachedImage = previewFrame.imageRef() != null ? previewFrame.imageRef().get() : null;
			if (cachedImage == null) {
				cachedImage = decodeImageBytes(previewFrame.bytes());
				this.cachedPreviewFrames.put(bucketMs, new CachedPreviewFrame(previewFrame.bytes(), new SoftReference<>(cachedImage)));
			}
			this.latestFrame = cachedImage;
			this.latestFrameBucketMs = bucketMs;
			this.frameSequence++;
			return true;
		}

		private boolean hasCachedFrameForPositionLocked(long targetPositionMs) {
			return findNearestCachedPreviewEntryLocked(targetPositionMs) != null;
		}

		private Map.Entry<Long, CachedPreviewFrame> findNearestCachedPreviewEntryLocked(long targetPositionMs) {
			if (this.cachedPreviewFrames.isEmpty()) {
				return null;
			}
			Map.Entry<Long, CachedPreviewFrame> floor = this.cachedPreviewFrames.floorEntry(targetPositionMs);
			Map.Entry<Long, CachedPreviewFrame> ceiling = this.cachedPreviewFrames.ceilingEntry(targetPositionMs);
			Map.Entry<Long, CachedPreviewFrame> candidate = null;
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
			return candidate;
		}

		private boolean shouldExtendBufferedRangeLocked(long positionMs) {
			long bucketMs = normalizeBucketMs(positionMs);
			if (!this.bufferedFromStart) {
				return bucketMs == 0L;
			}
			return bucketMs <= this.bufferedEndMs + CONTIGUOUS_BUFFER_TOLERANCE_MS;
		}

		private long nextPrefetchStartMsLocked() {
			if (this.fullCacheRequested && !this.bufferedFromStart) {
				return 0L;
			}
			if (this.cachedPreviewFrames.isEmpty()) {
				return 0L;
			}
			long currentBucketMs = normalizeBucketMs(this.positionMs);
			if (currentBucketMs < this.bufferedStartMs - CONTIGUOUS_BUFFER_TOLERANCE_MS
					|| currentBucketMs > this.bufferedEndMs + CONTIGUOUS_BUFFER_TOLERANCE_MS) {
				return currentBucketMs;
			}
			return Math.max(0L, normalizeBucketMs(this.bufferedEndMs + PREVIEW_CACHE_BUCKET_MS));
		}

		private void cachePreviewFrameLocked(long positionMs, byte[] frameBytes, BufferedImage decodedImage, boolean extendBufferedRange) {
			if (this.live || frameBytes == null || frameBytes.length == 0) {
				return;
			}
			long bucketMs = normalizeBucketMs(positionMs);
			if (!this.cachedPreviewFrames.containsKey(bucketMs)) {
				this.cachedPreviewFrames.put(
						bucketMs,
						new CachedPreviewFrame(
								Arrays.copyOf(frameBytes, frameBytes.length),
								decodedImage != null ? new SoftReference<>(decodedImage) : null
						)
				);
			}
			trimCachedPreviewWindowLocked();
			recomputeBufferedRangeLocked();
		}

		private void recomputeBufferedRangeLocked() {
			if (this.cachedPreviewFrames.isEmpty()) {
				this.bufferedFromStart = false;
				this.bufferedStartMs = 0L;
				this.bufferedEndMs = 0L;
				return;
			}
			long startMs = this.cachedPreviewFrames.firstKey();
			long endMs = startMs;
			for (Long candidateMs : this.cachedPreviewFrames.tailMap(startMs, false).keySet()) {
				if (candidateMs > endMs + CONTIGUOUS_BUFFER_TOLERANCE_MS) {
					break;
				}
				endMs = candidateMs;
			}
			this.bufferedStartMs = startMs;
			this.bufferedEndMs = endMs;
			this.bufferedFromStart = startMs == 0L;
		}

		private long desiredCacheMinMsLocked() {
			if (this.fullCacheRequested) {
				return 0L;
			}
			return Math.max(0L, normalizeBucketMs(this.positionMs - CURRENT_VIDEO_CACHE_BEHIND_MS));
		}

		private long desiredCacheMaxMsLocked() {
			if (this.fullCacheRequested && this.durationMs > 0L) {
				return Math.max(0L, normalizeBucketMs(this.durationMs));
			}
			long maxMs = normalizeBucketMs(this.positionMs + CURRENT_VIDEO_CACHE_AHEAD_MS);
			if (this.durationMs > 0L) {
				maxMs = Math.min(normalizeBucketMs(this.durationMs), maxMs);
			}
			return Math.max(0L, maxMs);
		}

		private void requestFullCache() throws IOException {
			synchronized (this.lock) {
				if (this.live || closedOrUnloadedLocked()) {
					return;
				}
				this.fullCacheRequested = true;
				this.prefetchCompleted = false;
				if (this.prefetchProcess == null) {
					startPrefetchLocked();
				}
			}
		}

		private void trimCachedPreviewWindowLocked() {
			if (this.cachedPreviewFrames.isEmpty()) {
				return;
			}
			long keepMinMs = desiredCacheMinMsLocked();
			long keepMaxMs = desiredCacheMaxMsLocked();
			while (!this.cachedPreviewFrames.isEmpty() && this.cachedPreviewFrames.firstKey() < keepMinMs) {
				this.cachedPreviewFrames.pollFirstEntry();
			}
			while (!this.cachedPreviewFrames.isEmpty() && this.cachedPreviewFrames.lastKey() > keepMaxMs) {
				this.cachedPreviewFrames.pollLastEntry();
			}
			while (this.cachedPreviewFrames.size() > MAX_SESSION_CACHED_PREVIEW_FRAMES) {
				long firstDistance = Math.abs(this.cachedPreviewFrames.firstKey() - this.positionMs);
				long lastDistance = Math.abs(this.cachedPreviewFrames.lastKey() - this.positionMs);
				if (firstDistance >= lastDistance) {
					this.cachedPreviewFrames.pollFirstEntry();
				} else {
					this.cachedPreviewFrames.pollLastEntry();
				}
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
