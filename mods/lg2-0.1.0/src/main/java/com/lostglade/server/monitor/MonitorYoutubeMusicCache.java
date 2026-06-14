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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class MonitorYoutubeMusicCache {
	private static final Gson GSON = new Gson();
	private static final ExecutorService PRELOAD_EXECUTOR = Executors.newFixedThreadPool(2, daemonThreadFactory("lg2-ytmusic-cache"));
	private static final ScheduledExecutorService RETRY_EXECUTOR = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("lg2-ytmusic-cache-retry"));
	private static final Map<String, TrackCacheState> TRACKS = new ConcurrentHashMap<>();
	private static final String DEFAULT_YT_DLP_BIN = "yt-dlp";
	private static final String DEFAULT_FFMPEG_BIN = "ffmpeg";
	private static final String DEFAULT_FFPROBE_BIN = "ffprobe";
	private static final int COMMAND_TIMEOUT_SEC = 1800;
	private static final int AUDIO_TRANSCODE_TIMEOUT_SEC = 600;
	private static final int AUDIO_VALIDATE_TIMEOUT_SEC = 30;
	private static final int COVER_CONNECT_TIMEOUT_MS = 4000;
	private static final int COVER_READ_TIMEOUT_MS = 6000;
	private static final int MAX_COVER_DOWNLOAD_BYTES = 4 * 1024 * 1024;
	private static final int MAX_COVER_DOWNLOAD_ATTEMPTS = 5;
	private static final int COVER_SIZE = 640;
	private static final String FALLBACK_COVER_RESOURCE = "/monitor/youtube_music_fallback_cover.png";
	private static final String AUDIO_FILE_NAME = "audio.mp3";
	private static final String AUDIO_OUTPUT_TEMPLATE_NAME = "audio.%(ext)s";
	private static final String TEMP_DOWNLOAD_DIR_NAME = "download.tmp";
	private static final String LEGACY_AUDIO_FILE_NAME = "audio.source";
	private static final List<String> AUDIO_FILE_CANDIDATE_NAMES = List.of(
			AUDIO_FILE_NAME,
			"audio.m4a",
			"audio.webm",
			"audio.weba",
			"audio.opus",
			"audio.ogg",
			"audio.aac",
			LEGACY_AUDIO_FILE_NAME
	);
	private static final String COVER_FILE_NAME = "cover.png";
	private static final String METADATA_FILE_NAME = "meta.json";
	private static final String COMPLETE_MARKER_FILE_NAME = "complete.marker";
	private static final String DOWNLOAD_PROGRESS_PREFIX = "cache-progress:";
	private static final int PROCESS_OUTPUT_TAIL_LINES = 64;
	private static final long RETAINED_RETRY_BASE_DELAY_MS = 2000L;
	private static final long RETAINED_RETRY_MAX_DELAY_MS = TimeUnit.MINUTES.toMillis(1L);
	private static volatile Path cacheDirectory = Path.of(System.getProperty("user.dir"), "cache", "lg2-monitor", "youtube-music");
	private static volatile BufferedImage fallbackCoverAsset = null;

	private MonitorYoutubeMusicCache() {
	}

	public static void setCacheDirectory(Path directory) {
		if (directory != null) {
			cacheDirectory = directory;
		}
	}

	public static void shutdown() {
		PRELOAD_EXECUTOR.shutdownNow();
		RETRY_EXECUTOR.shutdownNow();
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
		return persistentTrackSnapshot(rawUrl.trim()).complete();
	}

	public static Path completedAudioFile(String rawUrl) throws IOException {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return null;
		}
		String url = rawUrl.trim();
		PersistentTrackSnapshot snapshot = persistentTrackSnapshot(url);
		if (!snapshot.complete()) {
			return null;
		}
		Path trackPath = snapshot.trackPath();
		if (trackPath == null || !Files.isRegularFile(trackPath)) {
			return null;
		}
		long expectedDurationMs = readDurationMs(snapshot.metadataPath());
		try {
			validateCompleteAudioFile(trackPath, expectedDurationMs);
		} catch (IOException exception) {
			deleteCompleteMarkerQuietly(url);
			throw exception;
		}
		if (isMp3File(trackPath)) {
			return trackPath;
		}
		Path entryDir = entryDirectory(url);
		if (entryDir == null) {
			return trackPath;
		}
		Path mp3Path = entryDir.resolve(AUDIO_FILE_NAME);
		if (!Files.isRegularFile(mp3Path)) {
			Path tempMp3Path = mp3Path.resolveSibling(mp3Path.getFileName() + ".transcode.tmp");
			deleteFileQuietly(tempMp3Path);
			runTextCommand(List.of(
					ffmpegBin(),
					"-hide_banner",
					"-loglevel",
					"error",
					"-nostdin",
					"-y",
					"-i",
					trackPath.toString(),
					"-vn",
					"-codec:a",
					"libmp3lame",
					"-q:a",
					"0",
					tempMp3Path.toString()
			), AUDIO_TRANSCODE_TIMEOUT_SEC);
			validateCompleteAudioFile(tempMp3Path, expectedDurationMs);
			moveFileReplacing(tempMp3Path, mp3Path);
		}
		long mp3Bytes = safeFileSize(mp3Path);
		if (mp3Bytes <= 0L) {
			return trackPath;
		}
		updateCompletedAudioBytes(url, mp3Bytes);
		return mp3Path;
	}

	public static QueueEntryCacheStatus queueEntryCacheStatus(String rawUrl) {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return new QueueEntryCacheStatus(0.0F, false, false);
		}
		String url = rawUrl.trim();
		TrackCacheState state = TRACKS.get(url);
		if (state != null) {
			return state.cacheStatus();
		}
		return cacheStatusFromSnapshot(persistentTrackSnapshot(url), false);
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

	public static LoadedTrack loadCompleteTrackIfPresent(String rawUrl, TaskProgress progress) throws IOException {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return null;
		}
		String url = rawUrl.trim();
		LoadedTrack cached = loadCompleteCachedTrackIfPresent(url, progress);
		if (cached != null) {
			TrackCacheState state = TRACKS.computeIfAbsent(url, TrackCacheState::new);
			synchronized (state.lock) {
				state.loadedTrack = cached;
			}
		}
		return cached;
	}

	public static void deletePersistentTrack(String rawUrl) {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return;
		}
		String url = rawUrl.trim();
		TRACKS.remove(url);
		deleteDirectoryQuietly(entryDirectory(url));
	}

	private static final class TrackCacheState {
		private final String url;
		private final Object lock = new Object();
		private final TaskProgress fullCacheProgress = new TaskProgress();
		private int retainCount;
		private boolean quickLoading;
		private boolean fullCacheLoading;
		private LoadedTrack loadedTrack;
		private long downloadedAudioBytes;
		private long expectedAudioBytes;
		private long completedAudioBytes;
		private int fullCacheFailureCount;
		private long nextFullCacheRetryAtMillis;

		private TrackCacheState(String url) {
			this.url = url;
		}

		private void retain() {
			synchronized (this.lock) {
				this.retainCount++;
			}
			ensureFullCacheAsync(true);
		}

		private void release() {
			synchronized (this.lock) {
				if (this.retainCount > 0) {
					this.retainCount--;
				}
			}
		}

		private LoadedTrack load(TaskProgress progress) throws IOException {
			while (true) {
				LoadedTrack cached = loadPlayableTrackIfPresent(this.url, progress);
				if (cached != null) {
					synchronized (this.lock) {
						this.loadedTrack = cached;
					}
					ensureFullCacheAsync(true);
					return cached;
				}
				LoadedTrack existingLoadedTrack = null;
				synchronized (this.lock) {
					if (this.loadedTrack != null) {
						existingLoadedTrack = this.loadedTrack;
					} else if (!this.quickLoading) {
						this.quickLoading = true;
						break;
					} else {
						try {
							this.lock.wait(250L);
						} catch (InterruptedException exception) {
							Thread.currentThread().interrupt();
							throw new IOException("Interrupted while loading YouTube Music track", exception);
						}
					}
				}
				if (existingLoadedTrack != null) {
					ensureFullCacheAsync(true);
					return existingLoadedTrack;
				}
			}
			try {
				LoadedTrack built = buildQuickTrack(this.url, progress);
				synchronized (this.lock) {
					this.loadedTrack = built;
					this.quickLoading = false;
					this.lock.notifyAll();
				}
				ensureFullCacheAsync(true);
				return built;
			} catch (IOException exception) {
				synchronized (this.lock) {
					this.quickLoading = false;
					this.lock.notifyAll();
				}
				throw exception;
			}
		}

		private void ensureFullCacheAsync(boolean force) {
			PersistentTrackSnapshot snapshot = persistentTrackSnapshot(this.url);
			if (!startFullCacheAttemptLocked(snapshot, force)) {
				return;
			}
			PRELOAD_EXECUTOR.execute(() -> {
				try {
					LoadedTrack built = buildFullTrack(this.url, this.fullCacheProgress, this);
					synchronized (this.lock) {
						this.loadedTrack = built;
						this.fullCacheLoading = false;
						this.fullCacheFailureCount = 0;
						this.nextFullCacheRetryAtMillis = 0L;
						this.lock.notifyAll();
					}
				} catch (IOException exception) {
					long retryDelayMillis;
					boolean scheduleRetry;
					synchronized (this.lock) {
						this.fullCacheLoading = false;
						this.fullCacheProgress.clear();
						this.fullCacheFailureCount = Math.min(8, this.fullCacheFailureCount + 1);
						retryDelayMillis = retryDelayMillis(this.fullCacheFailureCount);
						this.nextFullCacheRetryAtMillis = System.currentTimeMillis() + retryDelayMillis;
						scheduleRetry = this.retainCount > 0;
						this.lock.notifyAll();
					}
					if (scheduleRetry) {
						RETRY_EXECUTOR.schedule(() -> ensureFullCacheAsync(false), retryDelayMillis, TimeUnit.MILLISECONDS);
					}
					Lg2.LOGGER.debug("Failed to fully cache YouTube Music track {}", this.url, exception);
				}
			});
		}

		private QueueEntryCacheStatus cacheStatus() {
			ensureFullCacheAsync(false);
			PersistentTrackSnapshot snapshot = persistentTrackSnapshot(this.url);
			synchronized (this.lock) {
				syncPersistentProgressLocked(snapshot);
				if (snapshot.complete()) {
					return new QueueEntryCacheStatus(1.0F, false, true);
				}
				boolean active = this.fullCacheLoading || this.quickLoading;
				long downloaded = Math.max(this.downloadedAudioBytes, snapshot.audioBytes());
				long expected = Math.max(Math.max(this.expectedAudioBytes, snapshot.expectedAudioBytes()), snapshot.completedAudioBytes());
				float fraction = cacheFraction(downloaded, expected, false);
				return new QueueEntryCacheStatus(fraction, active, false);
			}
		}

		private void updateDownloadProgress(long downloadedBytes, long expectedBytes, TaskProgress progress) {
			long normalizedDownloaded = Math.max(0L, downloadedBytes);
			long normalizedExpected = Math.max(0L, expectedBytes);
			synchronized (this.lock) {
				if (normalizedDownloaded > this.downloadedAudioBytes) {
					this.downloadedAudioBytes = normalizedDownloaded;
				}
				if (normalizedExpected > 0L) {
					this.expectedAudioBytes = normalizedExpected;
				}
			}
			if (progress != null) {
				if (normalizedExpected > 0L) {
					progress.setProgress("AUDIO", Math.min(normalizedDownloaded, normalizedExpected), normalizedExpected);
				} else {
					progress.setIndeterminate(normalizedDownloaded > 0L ? "DOWNLOADING AUDIO" : "PREPARING AUDIO");
				}
			}
		}

		private void syncPersistentProgressLocked(PersistentTrackSnapshot snapshot) {
			if (snapshot == null) {
				return;
			}
			this.downloadedAudioBytes = Math.max(this.downloadedAudioBytes, snapshot.audioBytes());
			this.expectedAudioBytes = Math.max(this.expectedAudioBytes, snapshot.expectedAudioBytes());
			this.completedAudioBytes = Math.max(this.completedAudioBytes, snapshot.completedAudioBytes());
		}

		private boolean startFullCacheAttemptLocked(PersistentTrackSnapshot snapshot, boolean force) {
			synchronized (this.lock) {
				syncPersistentProgressLocked(snapshot);
				if (!shouldStartFullCacheLocked(snapshot, force)) {
					return false;
				}
				this.fullCacheLoading = true;
				if (this.expectedAudioBytes > 0L) {
					this.fullCacheProgress.setProgress("AUDIO", Math.min(this.downloadedAudioBytes, this.expectedAudioBytes), this.expectedAudioBytes);
				} else {
					this.fullCacheProgress.setIndeterminate(this.downloadedAudioBytes > 0L ? "RESUMING AUDIO" : "PREPARING");
				}
				return true;
			}
		}

		private boolean shouldStartFullCacheLocked(PersistentTrackSnapshot snapshot, boolean force) {
			if (snapshot != null && snapshot.complete()) {
				return false;
			}
			if (this.fullCacheLoading) {
				return false;
			}
			if (!force && this.retainCount <= 0) {
				return false;
			}
			return force || this.nextFullCacheRetryAtMillis <= System.currentTimeMillis();
		}
	}

	private static LoadedTrack buildQuickTrack(String url, TaskProgress progress) throws IOException {
		LoadedTrack cached = loadPlayableTrackIfPresent(url, progress);
		if (cached != null) {
			return cached;
		}
		JsonObject metadata = resolveMetadata(url);
		String title = getString(metadata, "title", "YouTube Music");
		String artist = resolveArtist(metadata);
		long durationMs = Math.round(getDouble(metadata, "duration", 0.0D) * 1000.0D);
		List<String> thumbnailUrls = resolveThumbnailUrls(url, metadata);
		BufferedImage cover = loadPersistedCoverOrFallback(url);
		persistMetadataAndCover(
				url,
				title,
				artist,
				durationMs,
				cover,
				thumbnailUrls.isEmpty() ? "" : thumbnailUrls.get(0),
				resolveExpectedAudioBytes(metadata)
		);
		LoadedTrack localTrack = loadPlayableTrackIfPresent(url, progress);
		if (localTrack != null) {
			return localTrack;
		}
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

	public static BufferedImage refreshCover(String rawUrl) throws IOException {
		if (!looksLikeSupportedUrl(rawUrl)) {
			return null;
		}
		String url = rawUrl.trim();
		JsonObject metadata = readTrackMetadata(url);
		if (metadata == null) {
			metadata = new JsonObject();
		}
		boolean resolvedFreshMetadata = false;
		if (!hasLikelyAlbumThumbnail(metadata)) {
			try {
				JsonObject resolvedMetadata = resolveMetadata(url);
				if (resolvedMetadata != null) {
					metadata = resolvedMetadata;
					resolvedFreshMetadata = true;
				}
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to refresh YouTube Music metadata before cover download for {}", url, exception);
			}
		}
		List<String> thumbnailUrls = resolveThumbnailUrls(url, metadata);
		BufferedImage cover = downloadCoverFromCandidates(thumbnailUrls, null);
		if (cover == null && !resolvedFreshMetadata) {
			try {
				JsonObject resolvedMetadata = resolveMetadata(url);
				if (resolvedMetadata != null) {
					metadata = resolvedMetadata;
					thumbnailUrls = resolveThumbnailUrls(url, metadata);
					cover = downloadCoverFromCandidates(thumbnailUrls, null);
				}
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to refresh YouTube Music metadata after cover download miss for {}", url, exception);
			}
		}
		if (cover == null || isFallbackCoverImage(cover)) {
			return null;
		}
		persistRefreshedCover(url, cover, metadata, thumbnailUrls);
		return cover;
	}

	private static LoadedTrack buildFullTrack(String url, TaskProgress progress, TrackCacheState state) throws IOException {
		LoadedTrack cached = loadCompleteCachedTrackIfPresent(url, progress);
		if (cached != null) {
			return cached;
		}
		Path targetTrackPath = finalTrackPath(url);
		Path tempDownloadDir = tempDownloadDirectory(url);
		Path outputTemplatePath = trackOutputTemplatePath(url);
		Path targetMetadataPath = metadataPath(url);
		JsonObject metadata = resolveMetadata(url);
		if (progress != null) {
			progress.setProgress("METADATA", 1L, 4L);
		}
		String title = getString(metadata, "title", "YouTube Music");
		String artist = resolveArtist(metadata);
		long durationMs = Math.round(getDouble(metadata, "duration", 0.0D) * 1000.0D);
		long expectedAudioBytes = resolveExpectedAudioBytes(metadata);
		List<String> thumbnailUrls = resolveThumbnailUrls(url, metadata);
		BufferedImage cover = loadPersistedCoverOrFallback(url);
		boolean fallbackCover = isFallbackCoverImage(cover);
		if (progress != null) {
			progress.setProgress("COVER", 2L, 4L);
		}

		Path entryDir = entryDirectory(url);
		if (entryDir == null) {
			throw new IOException("Invalid cache key");
		}
		if (targetTrackPath == null || tempDownloadDir == null || outputTemplatePath == null || targetMetadataPath == null) {
			throw new IOException("Invalid track cache path");
		}
		Files.createDirectories(entryDir);
		deleteDirectoryQuietly(tempDownloadDir);
		Files.createDirectories(tempDownloadDir);
		persistMetadataAndCover(
				url,
				title,
				artist,
				durationMs,
				cover,
				thumbnailUrls.isEmpty() ? "" : thumbnailUrls.get(0),
				expectedAudioBytes
		);
		deleteCompleteMarkerQuietly(url);
		if (state != null) {
			state.updateDownloadProgress(0L, expectedAudioBytes, progress);
		}
		final long[] persistedExpectedAudioBytes = {Math.max(0L, expectedAudioBytes)};
		long finalAudioBytes;
		try {
			runDownloadCommand(
					List.of(
							ytDlpBin(),
							"-f",
							"bestaudio/best",
							"-x",
							"--audio-format",
							"mp3",
							"--audio-quality",
							"0",
							"--no-playlist",
							"--newline",
							"--progress",
							"--progress-template",
							"download:" + DOWNLOAD_PROGRESS_PREFIX + "%(progress.status)s:%(progress.downloaded_bytes)s:%(progress.total_bytes)s:%(progress.total_bytes_estimate)s",
							"-o",
							outputTemplatePath.toString(),
							url
					),
					COMMAND_TIMEOUT_SEC,
					update -> {
						long observedExpected = update.expectedBytes() > 0L ? update.expectedBytes() : expectedAudioBytes;
						long observedDownloaded = Math.max(update.downloadedBytes(), safeFileSize(tempDownloadDir.resolve(AUDIO_FILE_NAME)));
						if (observedExpected > 0L && observedExpected != persistedExpectedAudioBytes[0]) {
							persistTrackMetadata(
									url,
									title,
									artist,
									durationMs,
									thumbnailUrls.isEmpty() ? "" : thumbnailUrls.get(0),
									fallbackCover,
									observedExpected,
									0L
							);
							persistedExpectedAudioBytes[0] = observedExpected;
						}
						if (state != null) {
							state.updateDownloadProgress(observedDownloaded, observedExpected, progress);
						} else if (progress != null) {
							if (observedExpected > 0L) {
								progress.setProgress("AUDIO", Math.min(observedDownloaded, observedExpected), observedExpected);
							} else {
								progress.setIndeterminate(observedDownloaded > 0L ? "DOWNLOADING AUDIO" : "PREPARING AUDIO");
							}
						}
					}
			);
			Path downloadedTrackPath = ensureDownloadedMp3(downloadedTrackPath(tempDownloadDir), tempDownloadDir);
			validateCompleteAudioFile(downloadedTrackPath, durationMs);
			finalAudioBytes = safeFileSize(downloadedTrackPath);
			if (finalAudioBytes <= 0L) {
				throw new IOException("Failed to download audio track");
			}
			moveFileReplacing(downloadedTrackPath, targetTrackPath);
			deleteObsoleteAudioCandidates(entryDir, targetTrackPath);
		} finally {
			deleteDirectoryQuietly(tempDownloadDir);
		}
		persistTrackMetadata(
				url,
				title,
				artist,
				durationMs,
				thumbnailUrls.isEmpty() ? "" : thumbnailUrls.get(0),
				fallbackCover,
				Math.max(expectedAudioBytes, finalAudioBytes),
				finalAudioBytes
		);
		refreshCoverFromResolvedMetadata(url, metadata, thumbnailUrls);
		markTrackComplete(url);
		if (state != null) {
			state.updateDownloadProgress(finalAudioBytes, Math.max(expectedAudioBytes, finalAudioBytes), progress);
		}
		if (progress != null) {
			progress.complete("READY");
		}
		return loadCachedTrack(url, targetTrackPath, coverPath(url), targetMetadataPath, progress);
	}

	private static LoadedTrack loadCompleteCachedTrackIfPresent(String url, TaskProgress progress) throws IOException {
		PersistentTrackSnapshot snapshot = persistentTrackSnapshot(url);
		if (!snapshot.complete()) {
			return null;
		}
		if (snapshot.validatedComplete()) {
			try {
				validateCompleteAudioFile(snapshot.trackPath(), readDurationMs(snapshot.metadataPath()));
			} catch (IOException exception) {
				deleteCompleteMarkerQuietly(url);
				Lg2.LOGGER.debug("Invalidated incomplete YouTube Music cache for {}", url, exception);
				return null;
			}
		}
		return loadCachedTrack(url, snapshot.trackPath(), snapshot.coverPath(), snapshot.metadataPath(), progress);
	}

	private static LoadedTrack loadPlayableTrackIfPresent(String url, TaskProgress progress) throws IOException {
		PersistentTrackSnapshot snapshot = persistentTrackSnapshot(url);
		if (!snapshot.playable()) {
			return null;
		}
		return loadCachedTrack(url, snapshot.trackPath(), snapshot.coverPath(), snapshot.metadataPath(), progress);
	}

	private static LoadedTrack loadCachedTrack(String url, Path trackPath, Path coverPath, Path metadataPath, TaskProgress progress) throws IOException {
		if (trackPath == null || coverPath == null || metadataPath == null) {
			throw new IOException("Invalid cache paths");
		}
		if (progress != null) {
			progress.setIndeterminate("LOADING CACHE");
		}
		JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
		String title = getString(metadata, "title", "YouTube Music");
		String artist = getString(metadata, "artist", "");
		long durationMs = getLong(metadata, "durationMs", 0L);
		BufferedImage cover = ImageIO.read(coverPath.toFile());
		if (cover == null) {
			cover = createFallbackCover();
		} else {
			BufferedImage normalized = normalizeCoverArt(cover);
			if (normalized != cover) {
				cover = normalized;
				ImageIO.write(cover, "png", coverPath.toFile());
			}
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

	public static String directThumbnailUrl(String rawUrl) {
		String videoId = extractYoutubeVideoId(rawUrl);
		if (videoId.isBlank()) {
			return "";
		}
		return "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
	}

	private static List<String> resolveThumbnailUrls(String rawUrl, JsonObject metadata) {
		LinkedHashSet<String> urls = new LinkedHashSet<>();
		appendMetadataThumbnailUrls(urls, metadata, true);
		String persistedThumbnail = getString(metadata, "thumbnailUrl", "");
		if (!persistedThumbnail.isBlank()) {
			urls.add(persistedThumbnail);
		}
		appendDirectThumbnailUrl(urls, rawUrl);
		appendDirectThumbnailUrl(urls, getString(metadata, "webpage_url", ""));
		appendDirectThumbnailUrl(urls, getString(metadata, "original_url", ""));
		appendDirectThumbnailUrl(urls, getString(metadata, "url", ""));
		String metadataId = getString(metadata, "id", "");
		if (!metadataId.isBlank()) {
			urls.add("https://img.youtube.com/vi/" + metadataId.trim() + "/hqdefault.jpg");
		}
		String thumbnail = getString(metadata, "thumbnail", "");
		if (!thumbnail.isBlank()) {
			urls.add(thumbnail);
		}
		appendMetadataThumbnailUrls(urls, metadata, false);
		return List.copyOf(urls);
	}

	private static void appendMetadataThumbnailUrls(LinkedHashSet<String> urls, JsonObject metadata, boolean squareOnly) {
		if (urls == null || metadata == null || !metadata.has("thumbnails") || !metadata.get("thumbnails").isJsonArray()) {
			return;
		}
		JsonArray thumbnails = metadata.getAsJsonArray("thumbnails");
		for (int index = thumbnails.size() - 1; index >= 0; index--) {
			if (!thumbnails.get(index).isJsonObject()) {
				continue;
			}
			JsonObject thumbnail = thumbnails.get(index).getAsJsonObject();
			String url = getString(thumbnail, "url", "");
			if (url.isBlank()) {
				continue;
			}
			if (squareOnly && !isLikelyAlbumThumbnail(url, thumbnail)) {
				continue;
			}
			urls.add(url);
		}
	}

	private static boolean isLikelyAlbumThumbnail(String url, JsonObject thumbnail) {
		if (url != null && url.contains("googleusercontent.com")) {
			return true;
		}
		long width = getLong(thumbnail, "width", 0L);
		long height = getLong(thumbnail, "height", 0L);
		if (width <= 0L || height <= 0L) {
			return false;
		}
		long tolerance = Math.max(2L, Math.max(width, height) / 20L);
		return Math.abs(width - height) <= tolerance;
	}

	private static boolean hasLikelyAlbumThumbnail(JsonObject metadata) {
		if (metadata == null) {
			return false;
		}
		String persistedThumbnail = getString(metadata, "thumbnailUrl", "");
		if (persistedThumbnail.contains("googleusercontent.com")) {
			return true;
		}
		if (!metadata.has("thumbnails") || !metadata.get("thumbnails").isJsonArray()) {
			return false;
		}
		JsonArray thumbnails = metadata.getAsJsonArray("thumbnails");
		for (int index = 0; index < thumbnails.size(); index++) {
			if (!thumbnails.get(index).isJsonObject()) {
				continue;
			}
			JsonObject thumbnail = thumbnails.get(index).getAsJsonObject();
			String url = getString(thumbnail, "url", "");
			if (isLikelyAlbumThumbnail(url, thumbnail)) {
				return true;
			}
		}
		return false;
	}

	private static void appendDirectThumbnailUrl(LinkedHashSet<String> urls, String rawUrl) {
		if (urls == null) {
			return;
		}
		String directThumbnailUrl = directThumbnailUrl(rawUrl);
		if (!directThumbnailUrl.isBlank()) {
			urls.add(directThumbnailUrl);
		}
	}

	private static String extractYoutubeVideoId(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return "";
		}
		try {
			URI uri = URI.create(rawUrl.trim());
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				return "";
			}
			String normalizedHost = host.toLowerCase(Locale.ROOT);
			if (normalizedHost.equals("youtu.be") || normalizedHost.endsWith(".youtu.be")) {
				String path = uri.getPath();
				if (path == null || path.isBlank() || "/".equals(path)) {
					return "";
				}
				return normalizeYoutubeVideoId(path.substring(1));
			}
			if (!normalizedHost.equals("youtube.com")
					&& !normalizedHost.endsWith(".youtube.com")
					&& !normalizedHost.equals("youtube-nocookie.com")
					&& !normalizedHost.endsWith(".youtube-nocookie.com")) {
				return "";
			}
			String queryVideoId = queryParameter(uri, "v");
			if (!queryVideoId.isBlank()) {
				return normalizeYoutubeVideoId(queryVideoId);
			}
			String path = uri.getPath();
			if (path == null || path.isBlank()) {
				return "";
			}
			String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
			String[] segments = normalizedPath.split("/");
			if (segments.length >= 2) {
				String prefix = segments[0].toLowerCase(Locale.ROOT);
				if ("shorts".equals(prefix) || "live".equals(prefix) || "embed".equals(prefix) || "v".equals(prefix)) {
					return normalizeYoutubeVideoId(segments[1]);
				}
			}
			return "";
		} catch (IllegalArgumentException exception) {
			return "";
		}
	}

	private static String queryParameter(URI uri, String name) {
		if (uri == null || name == null || name.isBlank()) {
			return "";
		}
		String query = uri.getRawQuery();
		if (query == null || query.isBlank()) {
			return "";
		}
		for (String entry : query.split("&")) {
			if (entry == null || entry.isBlank()) {
				continue;
			}
			int separator = entry.indexOf('=');
			String key = separator >= 0 ? entry.substring(0, separator) : entry;
			if (!name.equals(key)) {
				continue;
			}
			String value = separator >= 0 && separator + 1 < entry.length() ? entry.substring(separator + 1) : "";
			return normalizeYoutubeVideoId(value);
		}
		return "";
	}

	private static String normalizeYoutubeVideoId(String rawVideoId) {
		if (rawVideoId == null || rawVideoId.isBlank()) {
			return "";
		}
		String normalized = rawVideoId.trim();
		int ampersand = normalized.indexOf('&');
		if (ampersand >= 0) {
			normalized = normalized.substring(0, ampersand);
		}
		int question = normalized.indexOf('?');
		if (question >= 0) {
			normalized = normalized.substring(0, question);
		}
		int slash = normalized.indexOf('/');
		if (slash >= 0) {
			normalized = normalized.substring(0, slash);
		}
		return normalized;
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
		connection.setRequestProperty("Accept", "image/*,*/*;q=0.8");
		int status = connection.getResponseCode();
		if (status < 200 || status >= 400) {
			throw new IOException("Cover request failed: HTTP " + status);
		}
		long contentLength = connection.getContentLengthLong();
		if (contentLength > MAX_COVER_DOWNLOAD_BYTES) {
			throw new IOException("Cover image is too large");
		}
		try (InputStream input = connection.getInputStream()) {
			byte[] bytes = readBoundedBytes(input, MAX_COVER_DOWNLOAD_BYTES);
			if (bytes.length == 0) {
				return null;
			}
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			return normalizeCoverArt(image);
		} finally {
			connection.disconnect();
		}
	}

	private static byte[] readBoundedBytes(InputStream input, int maxBytes) throws IOException {
		if (input == null) {
			return new byte[0];
		}
		ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(8192, Math.max(0, maxBytes)));
		byte[] buffer = new byte[8192];
		int total = 0;
		int read;
		while ((read = input.read(buffer)) >= 0) {
			total += read;
			if (total > maxBytes) {
				throw new IOException("Cover image is too large");
			}
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	private static BufferedImage downloadCoverFromCandidates(List<String> thumbnailUrls, TaskProgress progress) {
		if (thumbnailUrls != null) {
			int attempts = 0;
			for (String thumbnailUrl : thumbnailUrls) {
				if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
					continue;
				}
				if (attempts++ >= MAX_COVER_DOWNLOAD_ATTEMPTS) {
					break;
				}
				try {
					BufferedImage cover = downloadCoverImage(thumbnailUrl, progress);
					if (cover != null) {
						return cover;
					}
				} catch (IOException exception) {
					Lg2.LOGGER.debug("Failed to download YouTube Music cover for {}", thumbnailUrl, exception);
				}
			}
		}
		return null;
	}

	private static BufferedImage refreshCoverFromResolvedMetadata(String url, JsonObject metadata, List<String> thumbnailUrls) {
		try {
			BufferedImage refreshedCover = downloadCoverFromCandidates(thumbnailUrls, null);
			if (refreshedCover == null || isFallbackCoverImage(refreshedCover)) {
				return null;
			}
			persistRefreshedCover(url, refreshedCover, metadata, thumbnailUrls);
			return refreshedCover;
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to refresh YouTube Music cover for {}", url, exception);
			return null;
		}
	}

	private static BufferedImage loadPersistedCoverOrFallback(String url) throws IOException {
		Path existingCoverPath = coverPath(url);
		if (existingCoverPath != null && Files.isRegularFile(existingCoverPath)) {
			BufferedImage persisted = ImageIO.read(existingCoverPath.toFile());
			if (persisted != null) {
				BufferedImage normalized = normalizeCoverArt(persisted);
				if (normalized != persisted) {
					ImageIO.write(normalized, "png", existingCoverPath.toFile());
				}
				return normalized;
			}
		}
		return createFallbackCover();
	}

	private static JsonObject readTrackMetadata(String url) {
		Path metadataPath = metadataPath(url);
		if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
			return null;
		}
		try {
			return GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to read YouTube Music metadata for {}", url, exception);
			return null;
		}
	}

	private static void persistRefreshedCover(String url, BufferedImage cover, JsonObject metadata, List<String> thumbnailUrls) throws IOException {
		Path targetCoverPath = coverPath(url);
		Path targetMetadataPath = metadataPath(url);
		if (targetCoverPath == null || targetMetadataPath == null || cover == null) {
			return;
		}
		BufferedImage normalizedCover = normalizeCoverArt(cover);
		Files.createDirectories(targetCoverPath.getParent());
		Files.createDirectories(targetMetadataPath.getParent());
		ImageIO.write(normalizedCover, "png", targetCoverPath.toFile());
		JsonObject persisted = readTrackMetadata(url);
		if (persisted == null) {
			persisted = new JsonObject();
		}
		if (metadata != null) {
			copyMetadataStringIfMissing(persisted, metadata, "title");
			copyMetadataStringIfMissing(persisted, metadata, "artist");
			if (!persisted.has("durationMs") && metadata.has("durationMs")) {
				persisted.addProperty("durationMs", getLong(metadata, "durationMs", 0L));
			}
		}
		persisted.addProperty("thumbnailUrl", thumbnailUrls != null && !thumbnailUrls.isEmpty() ? thumbnailUrls.get(0) : "");
		persisted.addProperty("fallbackCover", false);
		Files.writeString(targetMetadataPath, GSON.toJson(persisted), StandardCharsets.UTF_8);
	}

	private static void copyMetadataStringIfMissing(JsonObject target, JsonObject source, String key) {
		if (target == null || source == null || key == null || key.isBlank()) {
			return;
		}
		if (target.has(key) && !getString(target, key, "").isBlank()) {
			return;
		}
		String value = getString(source, key, "");
		if (!value.isBlank()) {
			target.addProperty(key, value);
		}
	}

	private static void persistMetadataAndCover(
			String url,
			String title,
			String artist,
			long durationMs,
			BufferedImage cover,
			String thumbnailUrl,
			long expectedAudioBytes
	) throws IOException {
		Path targetCoverPath = coverPath(url);
		Path targetMetadataPath = metadataPath(url);
		if (targetCoverPath == null || targetMetadataPath == null || cover == null) {
			return;
		}
		BufferedImage normalizedCover = normalizeCoverArt(cover);
		boolean fallbackCover = isFallbackCoverImage(normalizedCover);
		if (fallbackCover) {
			BufferedImage existingCover = readPersistedRealCover(url);
			if (existingCover != null) {
				normalizedCover = existingCover;
				fallbackCover = false;
			}
		}
		Files.createDirectories(targetCoverPath.getParent());
		Files.createDirectories(targetMetadataPath.getParent());
		ImageIO.write(normalizedCover, "png", targetCoverPath.toFile());
		persistTrackMetadata(url, title, artist, durationMs, thumbnailUrl, fallbackCover, expectedAudioBytes, 0L);
	}

	private static BufferedImage readPersistedRealCover(String url) {
		Path existingCoverPath = coverPath(url);
		if (existingCoverPath == null || !Files.isRegularFile(existingCoverPath)) {
			return null;
		}
		try {
			BufferedImage existingCover = ImageIO.read(existingCoverPath.toFile());
			if (existingCover == null) {
				return null;
			}
			BufferedImage normalizedCover = normalizeCoverArt(existingCover);
			return isFallbackCoverImage(normalizedCover) ? null : normalizedCover;
		} catch (IOException exception) {
			Lg2.LOGGER.debug("Failed to read persisted YouTube Music cover for {}", url, exception);
			return null;
		}
	}

	private static boolean isFallbackCoverImage(BufferedImage image) {
		BufferedImage fallback = loadFallbackCoverAsset();
		if (image == null || fallback == null) {
			return false;
		}
		if (image.getWidth() != fallback.getWidth() || image.getHeight() != fallback.getHeight()) {
			return false;
		}
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if (image.getRGB(x, y) != fallback.getRGB(x, y)) {
					return false;
				}
			}
		}
		return true;
	}

	private static BufferedImage normalizeCoverArt(BufferedImage image) {
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
			return image;
		}
		if (image.getWidth() == COVER_SIZE && image.getHeight() == COVER_SIZE) {
			return image;
		}
		int side = Math.min(image.getWidth(), image.getHeight());
		int sourceX = Math.max(0, (image.getWidth() - side) / 2);
		int sourceY = Math.max(0, (image.getHeight() - side) / 2);
		BufferedImage normalized = new BufferedImage(COVER_SIZE, COVER_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = normalized.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.drawImage(
					image,
					0,
					0,
					COVER_SIZE,
					COVER_SIZE,
					sourceX,
					sourceY,
					sourceX + side,
					sourceY + side,
					null
			);
		} finally {
			graphics.dispose();
		}
		return normalized;
	}

	private static BufferedImage createFallbackCover() {
		BufferedImage asset = loadFallbackCoverAsset();
		if (asset != null) {
			return asset;
		}
		return createGeneratedFallbackCover();
	}

	private static BufferedImage loadFallbackCoverAsset() {
		BufferedImage cached = fallbackCoverAsset;
		if (cached != null) {
			return cached;
		}
		try (InputStream inputStream = MonitorYoutubeMusicCache.class.getResourceAsStream(FALLBACK_COVER_RESOURCE)) {
			if (inputStream == null) {
				return null;
			}
			BufferedImage image = ImageIO.read(inputStream);
			if (image == null) {
				return null;
			}
			BufferedImage normalized = normalizeCoverArt(image);
			fallbackCoverAsset = normalized;
			return normalized;
		} catch (IOException exception) {
			Lg2.LOGGER.debug("Failed to load YouTube Music fallback cover asset", exception);
			return null;
		}
	}

	private static BufferedImage createGeneratedFallbackCover() {
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
		} finally {
			graphics.dispose();
		}
		return image;
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

	private static void runDownloadCommand(List<String> command, int timeoutSec, DownloadProgressConsumer progressConsumer) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();
		try {
			DownloadProcessReader reader = new DownloadProcessReader(process.getInputStream(), progressConsumer);
			Thread readerThread = new Thread(reader, "lg2-ytmusic-download-output");
			readerThread.setDaemon(true);
			readerThread.start();
			boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
			if (!finished) {
				process.destroyForcibly();
				readerThread.join(1000L);
				throw new IOException("Command timed out");
			}
			readerThread.join(1000L);
			if (reader.exception() != null) {
				throw reader.exception();
			}
			String output = reader.output().trim();
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

	private static QueueEntryCacheStatus cacheStatusFromSnapshot(PersistentTrackSnapshot snapshot, boolean active) {
		if (snapshot == null) {
			return new QueueEntryCacheStatus(0.0F, active, false);
		}
		if (snapshot.complete()) {
			return new QueueEntryCacheStatus(1.0F, false, true);
		}
		long targetBytes = Math.max(snapshot.expectedAudioBytes(), snapshot.completedAudioBytes());
		return new QueueEntryCacheStatus(cacheFraction(snapshot.audioBytes(), targetBytes, false), active, false);
	}

	private static float cacheFraction(long downloadedBytes, long expectedBytes, boolean complete) {
		if (complete) {
			return 1.0F;
		}
		if (downloadedBytes <= 0L || expectedBytes <= 0L) {
			return 0.0F;
		}
		float fraction = Math.max(0.0F, (float) downloadedBytes / (float) expectedBytes);
		return Math.max(0.01F, Math.min(0.99F, fraction));
	}

	private static long retryDelayMillis(int failureCount) {
		int normalizedFailures = Math.max(0, failureCount - 1);
		long multiplier = 1L << Math.min(5, normalizedFailures);
		return Math.min(RETAINED_RETRY_MAX_DELAY_MS, RETAINED_RETRY_BASE_DELAY_MS * multiplier);
	}

	private static long resolveExpectedAudioBytes(JsonObject metadata) {
		long expected = getLong(metadata, "filesize", 0L);
		if (expected > 0L) {
			return expected;
		}
		expected = getLong(metadata, "filesize_approx", 0L);
		if (expected > 0L) {
			return expected;
		}
		JsonArray requestedDownloads = metadata != null && metadata.has("requested_downloads") && metadata.get("requested_downloads").isJsonArray()
				? metadata.getAsJsonArray("requested_downloads")
				: null;
		if (requestedDownloads != null) {
			for (int index = 0; index < requestedDownloads.size(); index++) {
				if (!requestedDownloads.get(index).isJsonObject()) {
					continue;
				}
				expected = getLong(requestedDownloads.get(index).getAsJsonObject(), "filesize", 0L);
				if (expected > 0L) {
					return expected;
				}
				expected = getLong(requestedDownloads.get(index).getAsJsonObject(), "filesize_approx", 0L);
				if (expected > 0L) {
					return expected;
				}
			}
		}
		return 0L;
	}

	private static void persistTrackMetadata(
			String url,
			String title,
			String artist,
			long durationMs,
			String thumbnailUrl,
			boolean fallbackCover,
			long expectedAudioBytes,
			long completedAudioBytes
	) throws IOException {
		Path targetMetadataPath = metadataPath(url);
		if (targetMetadataPath == null) {
			return;
		}
		boolean persistedFallbackCover = fallbackCover && readPersistedRealCover(url) == null;
		Files.createDirectories(targetMetadataPath.getParent());
		JsonObject persisted = new JsonObject();
		persisted.addProperty("title", title);
		persisted.addProperty("artist", artist);
		persisted.addProperty("durationMs", durationMs);
		persisted.addProperty("thumbnailUrl", thumbnailUrl != null ? thumbnailUrl : "");
		persisted.addProperty("fallbackCover", persistedFallbackCover);
		if (expectedAudioBytes > 0L) {
			persisted.addProperty("expectedAudioBytes", expectedAudioBytes);
		}
		if (completedAudioBytes > 0L) {
			persisted.addProperty("completedAudioBytes", completedAudioBytes);
			persisted.addProperty("validatedComplete", true);
		}
		Files.writeString(targetMetadataPath, GSON.toJson(persisted), StandardCharsets.UTF_8);
	}

	private static void markTrackComplete(String url) throws IOException {
		Path markerPath = completeMarkerPath(url);
		if (markerPath == null) {
			return;
		}
		Files.createDirectories(markerPath.getParent());
		Files.writeString(markerPath, "ready", StandardCharsets.UTF_8);
	}

	private static void deleteCompleteMarkerQuietly(String url) {
		Path markerPath = completeMarkerPath(url);
		if (markerPath == null) {
			return;
		}
		try {
			Files.deleteIfExists(markerPath);
		} catch (IOException ignored) {
		}
	}

	private static PersistentTrackSnapshot persistentTrackSnapshot(String url) {
		Path trackPath = trackPath(url);
		Path coverPath = coverPath(url);
		Path metadataPath = metadataPath(url);
		boolean hasCover = coverPath != null && Files.isRegularFile(coverPath);
		boolean hasMetadata = metadataPath != null && Files.isRegularFile(metadataPath);
		long audioBytes = safeFileSize(trackPath);
		long expectedAudioBytes = readExpectedAudioBytes(metadataPath);
		long completedAudioBytes = readCompletedAudioBytes(metadataPath);
		boolean validatedComplete = readValidatedComplete(metadataPath);
		boolean playable = trackPath != null && hasCover && hasMetadata && audioBytes > 0L;
		Path markerPath = completeMarkerPath(url);
		boolean legacyByteComplete = !validatedComplete
				&& completedAudioBytes > 0L
				&& audioBytes >= completedAudioBytes
				&& (expectedAudioBytes <= 0L || completedAudioBytes >= expectedAudioBytes);
		boolean complete = playable
				&& markerPath != null
				&& Files.isRegularFile(markerPath)
				&& (validatedComplete || legacyByteComplete)
				&& completedAudioBytes > 0L
				&& audioBytes >= completedAudioBytes;
		if (complete && expectedAudioBytes <= 0L) {
			expectedAudioBytes = Math.max(audioBytes, completedAudioBytes);
		}
		return new PersistentTrackSnapshot(trackPath, coverPath, metadataPath, audioBytes, expectedAudioBytes, completedAudioBytes, validatedComplete, playable, complete);
	}

	private static long readExpectedAudioBytes(Path metadataPath) {
		if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
			return 0L;
		}
		try {
			JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
			return Math.max(0L, getLong(metadata, "expectedAudioBytes", 0L));
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to read persisted YouTube Music metadata from {}", metadataPath, exception);
			return 0L;
		}
	}

	private static long readCompletedAudioBytes(Path metadataPath) {
		if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
			return 0L;
		}
		try {
			JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
			return Math.max(0L, getLong(metadata, "completedAudioBytes", 0L));
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to read persisted YouTube Music completion metadata from {}", metadataPath, exception);
			return 0L;
		}
	}

	private static boolean readValidatedComplete(Path metadataPath) {
		if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
			return false;
		}
		try {
			JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
			return metadata != null
					&& metadata.has("validatedComplete")
					&& metadata.get("validatedComplete").isJsonPrimitive()
					&& metadata.get("validatedComplete").getAsBoolean();
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to read persisted YouTube Music validation flag from {}", metadataPath, exception);
			return false;
		}
	}

	private static long readDurationMs(Path metadataPath) {
		if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
			return 0L;
		}
		try {
			JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
			return Math.max(0L, getLong(metadata, "durationMs", 0L));
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to read persisted YouTube Music duration from {}", metadataPath, exception);
			return 0L;
		}
	}

	private static void validateCompleteAudioFile(Path audioPath, long expectedDurationMs) throws IOException {
		if (audioPath == null || !Files.isRegularFile(audioPath) || safeFileSize(audioPath) <= 0L) {
			throw new IOException("Completed audio file is missing");
		}
		if (expectedDurationMs <= 0L) {
			return;
		}
		long actualDurationMs = probeAudioDurationMs(audioPath);
		if (actualDurationMs <= 0L) {
			throw new IOException("Completed audio duration is unknown");
		}
		long toleranceMs = Math.min(30_000L, Math.max(7_000L, expectedDurationMs / 50L));
		if (actualDurationMs + toleranceMs < expectedDurationMs) {
			throw new IOException("Completed audio is shorter than expected: " + actualDurationMs + "ms < " + expectedDurationMs + "ms");
		}
	}

	private static long probeAudioDurationMs(Path audioPath) throws IOException {
		String output = runTextCommand(List.of(
				ffprobeBin(),
				"-v",
				"error",
				"-show_entries",
				"format=duration",
				"-of",
				"default=noprint_wrappers=1:nokey=1",
				audioPath.toString()
		), AUDIO_VALIDATE_TIMEOUT_SEC).trim();
		if (output.isBlank()) {
			return 0L;
		}
		String firstLine = output.split("\\R", 2)[0].trim();
		try {
			return Math.max(0L, Math.round(Double.parseDouble(firstLine) * 1000.0D));
		} catch (NumberFormatException exception) {
			throw new IOException("Invalid audio duration: " + firstLine, exception);
		}
	}

	private static long safeFileSize(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return 0L;
		}
		try {
			return Math.max(0L, Files.size(path));
		} catch (IOException exception) {
			return 0L;
		}
	}

	private static boolean isMp3File(Path path) {
		if (path == null || path.getFileName() == null) {
			return false;
		}
		return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3");
	}

	private static void updateCompletedAudioBytes(String url, long completedAudioBytes) throws IOException {
		Path metadataPath = metadataPath(url);
		if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
			return;
		}
		JsonObject metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8), JsonObject.class);
		if (metadata == null) {
			return;
		}
		long expectedAudioBytes = Math.max(getLong(metadata, "expectedAudioBytes", 0L), completedAudioBytes);
		metadata.addProperty("expectedAudioBytes", expectedAudioBytes);
		metadata.addProperty("completedAudioBytes", Math.max(0L, completedAudioBytes));
		if (completedAudioBytes > 0L) {
			metadata.addProperty("validatedComplete", true);
		}
		Files.writeString(metadataPath, GSON.toJson(metadata), StandardCharsets.UTF_8);
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
		if (directory == null) {
			return null;
		}
		for (String candidateName : AUDIO_FILE_CANDIDATE_NAMES) {
			Path candidate = directory.resolve(candidateName);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return directory.resolve(AUDIO_FILE_NAME);
	}

	private static Path finalTrackPath(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve(AUDIO_FILE_NAME) : null;
	}

	private static Path trackOutputTemplatePath(String url) {
		Path directory = tempDownloadDirectory(url);
		return directory != null ? directory.resolve(AUDIO_OUTPUT_TEMPLATE_NAME) : null;
	}

	private static Path tempDownloadDirectory(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve(TEMP_DOWNLOAD_DIR_NAME) : null;
	}

	private static Path downloadedTrackPath(Path directory) throws IOException {
		if (directory == null) {
			throw new IOException("Downloaded audio directory is missing");
		}
		for (String candidateName : AUDIO_FILE_CANDIDATE_NAMES) {
			Path candidate = directory.resolve(candidateName);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		throw new IOException("Downloaded audio file is missing");
	}

	private static Path ensureDownloadedMp3(Path downloadedTrackPath, Path tempDownloadDir) throws IOException {
		if (downloadedTrackPath == null || !Files.isRegularFile(downloadedTrackPath)) {
			throw new IOException("Downloaded audio file is missing");
		}
		if (isMp3File(downloadedTrackPath)) {
			return downloadedTrackPath;
		}
		Path convertedPath = tempDownloadDir != null ? tempDownloadDir.resolve(AUDIO_FILE_NAME) : null;
		if (convertedPath == null) {
			throw new IOException("Invalid mp3 conversion path");
		}
		Path tempConvertedPath = convertedPath.resolveSibling(convertedPath.getFileName() + ".convert.tmp");
		deleteFileQuietly(tempConvertedPath);
		runTextCommand(List.of(
				ffmpegBin(),
				"-hide_banner",
				"-loglevel",
				"error",
				"-nostdin",
				"-y",
				"-i",
				downloadedTrackPath.toString(),
				"-vn",
				"-codec:a",
				"libmp3lame",
				"-q:a",
				"0",
				tempConvertedPath.toString()
		), AUDIO_TRANSCODE_TIMEOUT_SEC);
		moveFileReplacing(tempConvertedPath, convertedPath);
		return convertedPath;
	}

	private static void moveFileReplacing(Path sourcePath, Path targetPath) throws IOException {
		if (sourcePath == null || targetPath == null) {
			throw new IOException("Invalid audio move path");
		}
		Path parent = targetPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try {
			Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException ignored) {
			Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void deleteObsoleteAudioCandidates(Path directory, Path keepPath) {
		if (directory == null) {
			return;
		}
		for (String candidateName : AUDIO_FILE_CANDIDATE_NAMES) {
			Path candidate = directory.resolve(candidateName);
			if (keepPath != null && candidate.equals(keepPath)) {
				continue;
			}
			deleteFileQuietly(candidate);
		}
	}

	private static void deleteFileQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
		}
	}

	private static Path coverPath(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve(COVER_FILE_NAME) : null;
	}

	private static Path metadataPath(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve(METADATA_FILE_NAME) : null;
	}

	private static Path completeMarkerPath(String url) {
		Path directory = entryDirectory(url);
		return directory != null ? directory.resolve(COMPLETE_MARKER_FILE_NAME) : null;
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

	private static String ffmpegBin() {
		return readStringSetting("FFMPEG_BIN", "lg2.media.ffmpegBin", DEFAULT_FFMPEG_BIN);
	}

	private static String ffprobeBin() {
		return readStringSetting("FFPROBE_BIN", "lg2.media.ffprobeBin", DEFAULT_FFPROBE_BIN);
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

	@FunctionalInterface
	private interface DownloadProgressConsumer {
		void accept(DownloadProgressUpdate update) throws IOException;
	}

	private record DownloadProgressUpdate(String status, long downloadedBytes, long expectedBytes) {
	}

	private record PersistentTrackSnapshot(
			Path trackPath,
			Path coverPath,
			Path metadataPath,
			long audioBytes,
			long expectedAudioBytes,
			long completedAudioBytes,
			boolean validatedComplete,
			boolean playable,
			boolean complete
	) {
	}

	private static final class DownloadProcessReader implements Runnable {
		private final InputStream inputStream;
		private final DownloadProgressConsumer progressConsumer;
		private final Deque<String> outputTail = new ArrayDeque<>();
		private volatile IOException exception = null;

		private DownloadProcessReader(InputStream inputStream, DownloadProgressConsumer progressConsumer) {
			this.inputStream = inputStream;
			this.progressConsumer = progressConsumer;
		}

		@Override
		public void run() {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.inputStream, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (handleProgressLine(line)) {
						continue;
					}
					if (this.outputTail.size() >= PROCESS_OUTPUT_TAIL_LINES) {
						this.outputTail.removeFirst();
					}
					this.outputTail.addLast(line);
				}
			} catch (IOException exception) {
				this.exception = exception;
			}
		}

		private boolean handleProgressLine(String line) throws IOException {
			String normalized = line == null ? "" : line.trim();
			if (!normalized.startsWith(DOWNLOAD_PROGRESS_PREFIX)) {
				return false;
			}
			String payload = normalized.substring(DOWNLOAD_PROGRESS_PREFIX.length());
			String[] parts = payload.split(":", 4);
			String status = parts.length > 0 ? parts[0].trim() : "";
			long downloadedBytes = parts.length > 1 ? parseLongSafely(parts[1]) : 0L;
			long totalBytes = parts.length > 2 ? parseLongSafely(parts[2]) : 0L;
			long estimatedBytes = parts.length > 3 ? parseLongSafely(parts[3]) : 0L;
			long expectedBytes = Math.max(totalBytes, estimatedBytes);
			if (this.progressConsumer != null) {
				this.progressConsumer.accept(new DownloadProgressUpdate(status, downloadedBytes, expectedBytes));
			}
			return true;
		}

		private String output() {
			return String.join(System.lineSeparator(), this.outputTail);
		}

		private IOException exception() {
			return this.exception;
		}
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

	private static long parseLongSafely(String value) {
		if (value == null || value.isBlank() || "NA".equalsIgnoreCase(value.trim())) {
			return 0L;
		}
		try {
			return Math.max(0L, Long.parseLong(value.trim()));
		} catch (NumberFormatException exception) {
			return 0L;
		}
	}

	public record LoadedTrack(
			String title,
			String artist,
			MonitorMediaApp.LoadedVideo video
	) {
	}

	public record QueueEntryCacheStatus(float fraction, boolean active, boolean complete) {
	}
}
