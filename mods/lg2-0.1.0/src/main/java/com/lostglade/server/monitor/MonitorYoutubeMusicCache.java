package com.lostglade.server.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.server.progress.TaskProgress;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class MonitorYoutubeMusicCache {
	private static final Gson GSON = new Gson();
	private static final ExecutorService PRELOAD_EXECUTOR = Executors.newFixedThreadPool(2, daemonThreadFactory("lg2-ytmusic-cache"));
	private static final Map<String, TrackCacheState> TRACKS = new ConcurrentHashMap<>();
	private static final String DEFAULT_YT_DLP_BIN = "yt-dlp";
	private static final int COMMAND_TIMEOUT_SEC = 1800;
	private static final int COVER_CONNECT_TIMEOUT_MS = 4000;
	private static final int COVER_READ_TIMEOUT_MS = 15000;
	private static final int COVER_SIZE = 640;
	private static volatile Path cacheDirectory = Path.of(System.getProperty("user.dir"), "cache", "lg2-monitor", "youtube-music");

	private MonitorYoutubeMusicCache() {
	}

	public static void setCacheDirectory(Path directory) {
		if (directory != null) {
			cacheDirectory = directory;
		}
	}

	public static void shutdown() {
		PRELOAD_EXECUTOR.shutdownNow();
		TRACKS.clear();
	}

	public static boolean looksLikeSupportedUrl(String rawUrl) {
		return MonitorYoutubeRelayClient.looksLikeYoutubeUrl(rawUrl);
	}

	public static MonitorYoutubeRelayClient.QueueResolveResponse resolveQueue(String rawUrl) throws IOException {
		return MonitorYoutubeRelayClient.resolveQueue(rawUrl);
	}

	public static void retainQueueEntry(String rawUrl) {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return;
		}
		String url = rawUrl.trim();
		TRACKS.compute(url, (ignored, existing) -> {
			TrackCacheState state = existing != null ? existing : new TrackCacheState(url);
			state.retain();
			return state;
		});
	}

	public static void releaseQueueEntry(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return;
		}
		TrackCacheState state = TRACKS.get(rawUrl.trim());
		if (state != null) {
			state.release();
		}
	}

	public static boolean isQueueEntryLoaded(String rawUrl) {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return false;
		}
		return metadataPath(rawUrl.trim()) != null && Files.isRegularFile(trackPath(rawUrl.trim())) && Files.isRegularFile(coverPath(rawUrl.trim()));
	}

	public static BufferedImage queueEntryPreview(String rawUrl) {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return null;
		}
		Path coverPath = coverPath(rawUrl.trim());
		if (coverPath == null || !Files.isRegularFile(coverPath)) {
			return null;
		}
		try {
			return ImageIO.read(coverPath.toFile());
		} catch (IOException exception) {
			Lg2.LOGGER.debug("Failed to read cached YouTube Music cover for {}", rawUrl, exception);
			return null;
		}
	}

	public static LoadedTrack load(String rawUrl, TaskProgress progress) throws IOException {
		if (!looksLikeSupportedUrl(rawUrl)) {
			throw new IOException("Only YouTube links are supported");
		}
		String url = rawUrl.trim();
		TrackCacheState state = TRACKS.computeIfAbsent(url, TrackCacheState::new);
		return state.load(progress);
	}

	private static final class TrackCacheState {
		private final String url;
		private final Object lock = new Object();
		private int retainCount;
		private boolean quickLoading;
		private boolean fullCacheLoading;
		private LoadedTrack loadedTrack;

		private TrackCacheState(String url) {
			this.url = url;
		}

		private void retain() {
			synchronized (this.lock) {
				this.retainCount++;
			}
			ensureFullCacheAsync(null);
		}

		private void release() {
			synchronized (this.lock) {
				if (this.retainCount > 0) {
					this.retainCount--;
				}
			}
		}

		private LoadedTrack load(TaskProgress progress) throws IOException {
			LoadedTrack cached = loadCachedIfPresent(progress);
			if (cached != null) {
				synchronized (this.lock) {
					this.loadedTrack = cached;
				}
				return cached;
			}
			while (true) {
				synchronized (this.lock) {
					if (this.loadedTrack != null) {
						return this.loadedTrack;
					}
					if (!this.quickLoading) {
						this.quickLoading = true;
						break;
					}
					try {
						this.lock.wait(250L);
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new IOException("Interrupted while loading YouTube Music track", exception);
					}
				}
			}
			try {
				LoadedTrack built = buildQuickTrack(this.url, progress);
				synchronized (this.lock) {
					this.loadedTrack = built;
					this.quickLoading = false;
					this.lock.notifyAll();
				}
				ensureFullCacheAsync(null);
				return built;
			} catch (IOException exception) {
				synchronized (this.lock) {
					this.quickLoading = false;
					this.lock.notifyAll();
				}
				throw exception;
			}
		}

		private LoadedTrack loadCachedIfPresent(TaskProgress progress) throws IOException {
			Path targetTrackPath = trackPath(this.url);
			Path targetCoverPath = coverPath(this.url);
			Path targetMetadataPath = metadataPath(this.url);
			if (targetTrackPath != null
					&& targetCoverPath != null
					&& targetMetadataPath != null
					&& Files.isRegularFile(targetTrackPath)
					&& Files.isRegularFile(targetCoverPath)
					&& Files.isRegularFile(targetMetadataPath)) {
				return loadCachedTrack(targetTrackPath, targetCoverPath, targetMetadataPath, progress);
			}
			return null;
		}

		private void ensureFullCacheAsync(TaskProgress progress) {
			synchronized (this.lock) {
				if (this.fullCacheLoading || isQueueEntryLoaded(this.url)) {
					return;
				}
				this.fullCacheLoading = true;
			}
			PRELOAD_EXECUTOR.execute(() -> {
				try {
					LoadedTrack built = buildFullTrack(this.url, progress);
					synchronized (this.lock) {
						this.loadedTrack = built;
						this.fullCacheLoading = false;
						this.lock.notifyAll();
					}
				} catch (IOException exception) {
					synchronized (this.lock) {
						this.fullCacheLoading = false;
						this.lock.notifyAll();
					}
					Lg2.LOGGER.debug("Failed to fully cache YouTube Music track {}", this.url, exception);
				}
			});
		}
	}

	private static LoadedTrack buildQuickTrack(String url, TaskProgress progress) throws IOException {
		LoadedTrack cached = loadCachedTrackIfPresent(url, progress);
		if (cached != null) {
			return cached;
		}
		JsonObject metadata = resolveMetadata(url);
		String title = getString(metadata, "title", "YouTube Music");
		String artist = resolveArtist(metadata);
		long durationMs = Math.round(getDouble(metadata, "duration", 0.0D) * 1000.0D);
		String thumbnailUrl = resolveThumbnailUrl(metadata);
		BufferedImage cover = downloadOrCreateCover(title, thumbnailUrl, progress);
		persistMetadataAndCover(url, title, artist, durationMs, cover);
		if (progress != null) {
			progress.setIndeterminate("CONNECTING AUDIO");
		}
		String audioStreamUrl = resolveAudioStreamUrl(url);
		if (progress != null) {
			progress.complete("READY");
		}
		return new LoadedTrack(
				title,
				artist,
				new MonitorMediaApp.LoadedVideo(
						cover,
						Math.max(0L, durationMs),
						cover.getWidth(),
						cover.getHeight(),
						audioStreamUrl,
						audioStreamUrl
				)
		);
	}

	private static LoadedTrack buildFullTrack(String url, TaskProgress progress) throws IOException {
		LoadedTrack cached = loadCachedTrackIfPresent(url, progress);
		if (cached != null) {
			return cached;
		}
		Path targetTrackPath = trackPath(url);
		Path targetCoverPath = coverPath(url);
		Path targetMetadataPath = metadataPath(url);
		JsonObject metadata = resolveMetadata(url);
		String title = getString(metadata, "title", "YouTube Music");
		String artist = resolveArtist(metadata);
		long durationMs = Math.round(getDouble(metadata, "duration", 0.0D) * 1000.0D);
		String thumbnailUrl = resolveThumbnailUrl(metadata);
		BufferedImage cover = loadPersistedOrCreateCover(url, title, thumbnailUrl, progress);

		Path entryDir = entryDirectory(url);
		if (entryDir == null) {
			throw new IOException("Invalid cache key");
		}
		Files.createDirectories(entryDir);
		Path tempDir = Files.createTempDirectory(entryDir, "build-");
		try {
			Path tempCoverPath = tempDir.resolve("cover.png");
			ImageIO.write(cover, "png", tempCoverPath.toFile());

			if (progress != null) {
				progress.setIndeterminate("DOWNLOADING AUDIO");
			}
			runCommand(List.of(
					ytDlpBin(),
					"-f",
					"bestaudio/best",
					"--no-playlist",
					"-o",
					tempDir.resolve("audio.%(ext)s").toString(),
					url
			), COMMAND_TIMEOUT_SEC);

			Path downloadedAudio = findDownloadedAudioFile(tempDir, tempCoverPath);
			if (downloadedAudio == null) {
				throw new IOException("Failed to download audio track");
			}

			if (targetCoverPath == null || targetTrackPath == null || targetMetadataPath == null) {
				throw new IOException("Invalid track cache path");
			}
			Files.createDirectories(targetCoverPath.getParent());
			Files.createDirectories(targetTrackPath.getParent());
			Files.move(tempCoverPath, targetCoverPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			Files.move(downloadedAudio, targetTrackPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

			JsonObject persisted = new JsonObject();
			persisted.addProperty("title", title);
			persisted.addProperty("artist", artist);
			persisted.addProperty("durationMs", durationMs);
			Files.writeString(targetMetadataPath, GSON.toJson(persisted), StandardCharsets.UTF_8);

			if (progress != null) {
				progress.complete("READY");
			}
			return new LoadedTrack(
					title,
					artist,
					new MonitorMediaApp.LoadedVideo(
							cover,
							Math.max(0L, durationMs),
							cover.getWidth(),
							cover.getHeight(),
							targetTrackPath.toAbsolutePath().toString(),
							targetTrackPath.toAbsolutePath().toString()
					)
			);
		} finally {
			deleteDirectoryQuietly(tempDir);
		}
	}

	private static LoadedTrack loadCachedTrackIfPresent(String url, TaskProgress progress) throws IOException {
		Path targetTrackPath = trackPath(url);
		Path targetCoverPath = coverPath(url);
		Path targetMetadataPath = metadataPath(url);
		if (targetTrackPath != null
				&& targetCoverPath != null
				&& targetMetadataPath != null
				&& Files.isRegularFile(targetTrackPath)
				&& Files.isRegularFile(targetCoverPath)
				&& Files.isRegularFile(targetMetadataPath)) {
			return loadCachedTrack(targetTrackPath, targetCoverPath, targetMetadataPath, progress);
		}
		return null;
	}

	private static LoadedTrack loadCachedTrack(Path trackPath, Path coverPath, Path metadataPath, TaskProgress progress) throws IOException {
		if (progress != null) {
			progress.setIndeterminate("LOADING CACHE");
		}
		JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
		String title = getString(metadata, "title", "YouTube Music");
		String artist = getString(metadata, "artist", "");
		long durationMs = getLong(metadata, "durationMs", 0L);
		BufferedImage cover = ImageIO.read(coverPath.toFile());
		if (cover == null) {
			cover = createFallbackCover(title);
		}
		if (progress != null) {
			progress.complete("READY");
		}
		String inputPath = trackPath.toAbsolutePath().toString();
		return new LoadedTrack(title, artist, new MonitorMediaApp.LoadedVideo(cover, durationMs, cover.getWidth(), cover.getHeight(), inputPath, inputPath));
	}

	private static String resolveArtist(JsonObject metadata) {
		String artist = getString(metadata, "artist", "");
		if (!artist.isBlank()) {
			return artist;
		}
		artist = getString(metadata, "track_artist", "");
		if (!artist.isBlank()) {
			return artist;
		}
		artist = getString(metadata, "album_artist", "");
		if (!artist.isBlank()) {
			return artist;
		}
		artist = getString(metadata, "creator", "");
		if (!artist.isBlank()) {
			return artist;
		}
		artist = getString(metadata, "uploader", "");
		if (!artist.isBlank()) {
			return artist;
		}
		return getString(metadata, "channel", "");
	}

	private static String resolveAudioStreamUrl(String url) throws IOException {
		String output = runTextCommand(List.of(
				ytDlpBin(),
				"-f",
				"bestaudio/best",
				"-g",
				"--no-playlist",
				url
		), 120);
		String[] lines = output.split("\\R");
		for (String line : lines) {
			String candidate = line == null ? "" : line.trim();
			if (!candidate.isBlank()) {
				return candidate;
			}
		}
		throw new IOException("Failed to resolve audio stream URL");
	}

	private static JsonObject resolveMetadata(String url) throws IOException {
		String metadataJson = runTextCommand(List.of(ytDlpBin(), "--dump-single-json", "--no-playlist", url), 120);
		JsonObject metadata = GSON.fromJson(metadataJson, JsonObject.class);
		if (metadata == null) {
			throw new IOException("Failed to resolve track metadata");
		}
		return metadata;
	}

	private static String resolveThumbnailUrl(JsonObject metadata) {
		String thumbnail = getString(metadata, "thumbnail", "");
		if (!thumbnail.isBlank()) {
			return thumbnail;
		}
		if (metadata != null && metadata.has("thumbnails") && metadata.get("thumbnails").isJsonArray()) {
			JsonArray thumbnails = metadata.getAsJsonArray("thumbnails");
			for (int index = thumbnails.size() - 1; index >= 0; index--) {
				if (!thumbnails.get(index).isJsonObject()) {
					continue;
				}
				String url = getString(thumbnails.get(index).getAsJsonObject(), "url", "");
				if (!url.isBlank()) {
					return url;
				}
			}
		}
		return "";
	}

	private static BufferedImage downloadCoverImage(String thumbnailUrl, TaskProgress progress) throws IOException {
		if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
			return null;
		}
		if (progress != null) {
			progress.setIndeterminate("DOWNLOADING COVER");
		}
		HttpURLConnection connection = (HttpURLConnection) URI.create(thumbnailUrl).toURL().openConnection();
		connection.setInstanceFollowRedirects(true);
		connection.setConnectTimeout(COVER_CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(COVER_READ_TIMEOUT_MS);
		connection.setRequestProperty("User-Agent", "LostGladeMonitor/1.0");
		try (InputStream input = connection.getInputStream()) {
			byte[] bytes = input.readAllBytes();
			if (bytes.length == 0) {
				return null;
			}
			return ImageIO.read(new ByteArrayInputStream(bytes));
		} finally {
			connection.disconnect();
		}
	}

	private static BufferedImage downloadOrCreateCover(String title, String thumbnailUrl, TaskProgress progress) throws IOException {
		BufferedImage cover = downloadCoverImage(thumbnailUrl, progress);
		return cover != null ? cover : createFallbackCover(title);
	}

	private static BufferedImage loadPersistedOrCreateCover(String url, String title, String thumbnailUrl, TaskProgress progress) throws IOException {
		Path existingCoverPath = coverPath(url);
		if (existingCoverPath != null && Files.isRegularFile(existingCoverPath)) {
			BufferedImage persisted = ImageIO.read(existingCoverPath.toFile());
			if (persisted != null) {
				return persisted;
			}
		}
		return downloadOrCreateCover(title, thumbnailUrl, progress);
	}

	private static void persistMetadataAndCover(String url, String title, String artist, long durationMs, BufferedImage cover) throws IOException {
		Path targetCoverPath = coverPath(url);
		Path targetMetadataPath = metadataPath(url);
		if (targetCoverPath == null || targetMetadataPath == null || cover == null) {
			return;
		}
		Files.createDirectories(targetCoverPath.getParent());
		Files.createDirectories(targetMetadataPath.getParent());
		ImageIO.write(cover, "png", targetCoverPath.toFile());
		JsonObject persisted = new JsonObject();
		persisted.addProperty("title", title);
		persisted.addProperty("artist", artist);
		persisted.addProperty("durationMs", durationMs);
		Files.writeString(targetMetadataPath, GSON.toJson(persisted), StandardCharsets.UTF_8);
	}

	private static BufferedImage createFallbackCover(String title) {
		BufferedImage image = new BufferedImage(COVER_SIZE, COVER_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setPaint(new GradientPaint(0.0F, 0.0F, new Color(32, 10, 14), COVER_SIZE, COVER_SIZE, new Color(121, 18, 37)));
			graphics.fillRect(0, 0, COVER_SIZE, COVER_SIZE);
			int circleSize = 280;
			int circleX = (COVER_SIZE - circleSize) / 2;
			int circleY = 110;
			graphics.setColor(new Color(255, 255, 255, 232));
			graphics.fill(new Ellipse2D.Float(circleX, circleY, circleSize, circleSize));
			graphics.setColor(new Color(184, 15, 38));
			graphics.setStroke(new BasicStroke(26.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.draw(new Ellipse2D.Float(circleX + 28, circleY + 28, circleSize - 56, circleSize - 56));
			Path2D play = new Path2D.Float();
			play.moveTo(circleX + 118, circleY + 92);
			play.lineTo(circleX + 118, circleY + 188);
			play.lineTo(circleX + 206, circleY + 140);
			play.closePath();
			graphics.fill(play);
			graphics.setColor(new Color(255, 255, 255, 230));
			graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 38));
			String label = "YT MUSIC";
			int labelWidth = graphics.getFontMetrics().stringWidth(label);
			graphics.drawString(label, (COVER_SIZE - labelWidth) / 2, 470);
			graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
			String subtitle = title == null || title.isBlank() ? "Audio Track" : title;
			if (subtitle.length() > 28) {
				subtitle = subtitle.substring(0, 28).trim();
			}
			int subtitleWidth = graphics.getFontMetrics().stringWidth(subtitle);
			graphics.drawString(subtitle, (COVER_SIZE - subtitleWidth) / 2, 515);
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static Path findDownloadedAudioFile(Path tempDir, Path tempCoverPath) throws IOException {
		try (var files = Files.list(tempDir)) {
			return files
					.filter(Files::isRegularFile)
					.filter(path -> !Objects.equals(path, tempCoverPath))
					.filter(path -> !"audio.source".equalsIgnoreCase(path.getFileName().toString()))
					.findFirst()
					.orElse(null);
		}
	}

	private static String runTextCommand(List<String> command, int timeoutSec) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();
		try {
			byte[] outputBytes = readProcessOutput(process, timeoutSec, "Command timed out");
			String stdout = new String(outputBytes, StandardCharsets.UTF_8).trim();
			if (process.exitValue() != 0) {
				throw new IOException(stdout.isBlank() ? "Command failed" : stdout);
			}
			return stdout;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("Command interrupted", exception);
		} finally {
			process.destroy();
		}
	}

	private static void runCommand(List<String> command, int timeoutSec) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();
		try {
			byte[] outputBytes = readProcessOutput(process, timeoutSec, "Command timed out");
			String output = new String(outputBytes, StandardCharsets.UTF_8).trim();
			if (process.exitValue() != 0) {
				throw new IOException(output.isBlank() ? "Command failed" : output);
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("Command interrupted", exception);
		} finally {
			process.destroy();
		}
	}

	private static byte[] readProcessOutput(Process process, int timeoutSec, String timeoutMessage) throws IOException, InterruptedException {
		ProcessOutputReader reader = new ProcessOutputReader(process.getInputStream());
		Thread readerThread = new Thread(reader, "lg2-ytmusic-process-output");
		readerThread.setDaemon(true);
		readerThread.start();
		boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
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

	private static Path entryDirectory(String url) {
		String key = hashString(url);
		return key == null || key.isBlank() ? null : cacheDirectory.resolve(key);
	}

	private static Path trackPath(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve("audio.source") : null;
	}

	private static Path coverPath(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve("cover.png") : null;
	}

	private static Path metadataPath(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve("meta.json") : null;
	}

	private static String hashString(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			return Integer.toHexString(value.hashCode());
		}
	}

	private static void deleteDirectoryQuietly(Path directory) {
		if (directory == null || !Files.exists(directory)) {
			return;
		}
		try {
			Files.walk(directory)
					.sorted((first, second) -> second.getNameCount() - first.getNameCount())
					.forEach(path -> {
						try {
							Files.deleteIfExists(path);
						} catch (IOException ignored) {
						}
					});
		} catch (IOException ignored) {
		}
	}

	private static String ytDlpBin() {
		return readStringSetting("YT_DLP_BIN", "lg2.youtube.ytDlpBin", DEFAULT_YT_DLP_BIN);
	}

	private static String readStringSetting(String envKey, String propertyKey, String fallback) {
		String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		return fallback;
	}

	private static String getString(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
	}

	private static ThreadFactory daemonThreadFactory(String namePrefix) {
		return runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName(namePrefix + "-" + Integer.toHexString(System.identityHashCode(runnable)));
			thread.setDaemon(true);
			return thread;
		};
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

	public record LoadedTrack(
			String title,
			String artist,
			MonitorMediaApp.LoadedVideo video
	) {
	}
}
