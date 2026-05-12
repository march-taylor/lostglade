package com.lostglade.server;

import com.lostglade.server.monitor.MonitorYoutubeMusicCache;
import com.lostglade.server.monitor.MonitorAppRegistry;
import com.lostglade.server.monitor.MonitorAppRole;
import com.lostglade.server.monitor.MonitorBackgroundPlaybackPolicy;
import com.lostglade.server.monitor.MonitorSberDronesCatalog;
import com.lostglade.server.progress.TaskProgress;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class MonitorMediaStateMachineTest {
	private MonitorMediaStateMachineTest() {
	}

	public static void main(String[] args) {
		youtubeAppendKeepsLoadingQueueEntries();
		galleryOpenRequestRejectsStaleLoads();
		youtubeRelayRejectsStaleResults();
		youtubeMusicRejectsStaleQueueResults();
		youtubeMusicPartialCacheUsesLocalTrackAndRealProgress();
		youtubeMusicCompleteCacheUsesLocalTrackPlayback();
		youtubeMusicRelayFallbackSkipsIncompleteCache();
		youtubeMusicPartialCacheDoesNotPretendToBeActive();
		youtubeMusicNeedsCompleteMarkerBeforeReportingFullCache();
		youtubeMusicRejectsLegacyMarkerWithoutVerifiedFinalSize();
		youtubeMusicDirectThumbnailUsesStableYoutubeCoverUrl();
		sberDronesUsesDedicatedLiveCameraCatalog();
		galleryRuntimePolicyIgnoresLiveCameraOnlyCollections();
		galleryRuntimePolicyRetainsOnlyActiveDecodedMedia();
		backgroundPlaybackPolicyIgnoresViewVisibility();
		directAudioSourcesDoNotResyncFromStaleMonitorPosition();
		System.out.println("Monitor media state-machine checks passed");
	}

	private static void youtubeAppendKeepsLoadingQueueEntries() {
		require(
				MonitorMediaSessionPolicy.shouldAppendYoutubeRequest(true, false, true, false, false, false, false, 0L),
				"a non-empty YouTube queue must append even before the first frame is displayable"
		);
		require(
				MonitorMediaSessionPolicy.shouldAppendYoutubeRequest(true, false, false, true, false, false, false, 0L),
				"a loading YouTube item must append instead of replacing the queue"
		);
		require(
				!MonitorMediaSessionPolicy.shouldAppendYoutubeRequest(false, true, true, true, true, true, true, 1000L),
				"non-YouTube modes must not use YouTube queue append policy"
		);
	}

	private static void galleryOpenRequestRejectsStaleLoads() {
		int requestId = MonitorMediaSessionPolicy.nextGalleryOpenRequestId(0);
		require(
				MonitorMediaSessionPolicy.galleryOpenRequestShouldApply(true, "gallery://a", 2, requestId, "gallery://a"),
				"matching gallery request id should apply"
		);
		require(
				!MonitorMediaSessionPolicy.galleryOpenRequestShouldApply(false, "gallery://a", 2, requestId, "gallery://a"),
				"gallery result must not apply after the UI leaves gallery mode"
		);
		require(
				!MonitorMediaSessionPolicy.galleryOpenRequestShouldApply(true, "gallery://b", 3, requestId + 1, "gallery://a"),
				"old gallery result must not apply over a newer selection for another URL"
		);
		require(
				MonitorMediaSessionPolicy.galleryOpenRequestShouldApply(true, "gallery://a", 2, requestId + 1, "gallery://a"),
				"an old future may satisfy a newer pending open for the same URL"
		);
		require(
				MonitorMediaSessionPolicy.galleryOpenRequestShouldApply(true, "gallery://a", 2, requestId, "gallery://a"),
				"an already-running preload may satisfy the latest pending gallery open"
		);
	}

	private static void youtubeRelayRejectsStaleResults() {
		require(
				MonitorMediaSessionPolicy.youtubeLoadResultStillCurrent(false, false, false, true, "https://youtube.test/a", "https://youtube.test/a"),
				"matching YouTube relay result should apply"
		);
		require(
				!MonitorMediaSessionPolicy.youtubeLoadResultStillCurrent(false, false, false, true, "https://youtube.test/b", "https://youtube.test/a"),
				"relay result for an old source URL must not apply"
		);
		require(
				!MonitorMediaSessionPolicy.youtubeLoadResultStillCurrent(true, false, false, false, "gallery://video", "gallery://video"),
				"gallery-backed relay result must not apply after leaving gallery player surface"
		);
		require(
				MonitorMediaSessionPolicy.youtubeLoadResultStillCurrent(true, true, true, false, "gallery://video", "gallery://video"),
				"gallery-backed relay result should apply while the gallery player surface is still current"
		);
	}

	private static void youtubeMusicRejectsStaleQueueResults() {
		require(
				MonitorMediaSessionPolicy.youtubeMusicLoadResultStillCurrent(true, "music://a", "music://a", 1, 1),
				"matching YouTube Music queue result should apply"
		);
		require(
				!MonitorMediaSessionPolicy.youtubeMusicLoadResultStillCurrent(true, "music://b", "music://a", 1, 1),
				"YouTube Music result for an old source URL must not apply"
		);
		require(
				!MonitorMediaSessionPolicy.youtubeMusicLoadResultStillCurrent(true, "music://a", "music://a", 2, 1),
				"YouTube Music result for an old queue index must not apply"
		);
		require(
				!MonitorMediaSessionPolicy.youtubeMusicLoadResultStillCurrent(false, "music://a", "music://a", 1, 1),
				"YouTube Music result must not apply after leaving YouTube Music mode"
		);
	}

	private static void youtubeMusicPartialCacheUsesLocalTrackAndRealProgress() {
		Path tempRoot = null;
		String url = "https://www.youtube.com/watch?v=partial-cache";
		Path originalCacheRoot = defaultYoutubeMusicCacheRoot();
		try {
			tempRoot = Files.createTempDirectory("lg2-ytmusic-partial-test");
			MonitorYoutubeMusicCache.setCacheDirectory(tempRoot);
			MonitorYoutubeMusicCache.deletePersistentTrack(url);
			Path entryDir = youtubeMusicEntryDir(tempRoot, url);
			Files.createDirectories(entryDir);
			writeCover(entryDir.resolve("cover.png"));
			Files.write(entryDir.resolve("audio.source"), new byte[25]);
			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Partial","artist":"Cache","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);

			MonitorYoutubeMusicCache.QueueEntryCacheStatus status = MonitorYoutubeMusicCache.queueEntryCacheStatus(url);
			require(!status.complete(), "partial YouTube Music cache must not report itself as complete");
			require(status.fraction() >= 0.24F && status.fraction() <= 0.26F, "partial YouTube Music cache fraction must come from real downloaded bytes");
		} catch (IOException exception) {
			throw new AssertionError("Failed to prepare partial YouTube Music cache test", exception);
		} finally {
			MonitorYoutubeMusicCache.setCacheDirectory(originalCacheRoot);
			deleteDirectoryQuietly(tempRoot);
		}
	}

	private static void youtubeMusicCompleteCacheUsesLocalTrackPlayback() {
		Path tempRoot = null;
		String url = "https://www.youtube.com/watch?v=complete-local";
		Path originalCacheRoot = defaultYoutubeMusicCacheRoot();
		try {
			tempRoot = Files.createTempDirectory("lg2-ytmusic-complete-local-test");
			MonitorYoutubeMusicCache.setCacheDirectory(tempRoot);
			MonitorYoutubeMusicCache.deletePersistentTrack(url);
			Path entryDir = youtubeMusicEntryDir(tempRoot, url);
			Files.createDirectories(entryDir);
			writeCover(entryDir.resolve("cover.png"));
			Files.write(entryDir.resolve("audio.source"), new byte[100]);
			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Complete","artist":"Local","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100,"completedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);
			Files.writeString(entryDir.resolve("complete.marker"), "ready", StandardCharsets.UTF_8);

			MonitorYoutubeMusicCache.LoadedTrack loaded = MonitorYoutubeMusicCache.load(url, new TaskProgress());
			String expectedPath = entryDir.resolve("audio.source").toAbsolutePath().toString();
			require(
					expectedPath.equals(loaded.video().playbackInput()) && expectedPath.equals(loaded.video().audioInput()),
					"fully cached YouTube Music track must open from the local cache file"
			);
		} catch (IOException exception) {
			throw new AssertionError("Failed to prepare complete local YouTube Music cache test", exception);
		} finally {
			MonitorYoutubeMusicCache.setCacheDirectory(originalCacheRoot);
			deleteDirectoryQuietly(tempRoot);
		}
	}

	private static void youtubeMusicRelayFallbackSkipsIncompleteCache() {
		Path tempRoot = null;
		String url = "https://www.youtube.com/watch?v=incomplete-relay-fallback";
		Path originalCacheRoot = defaultYoutubeMusicCacheRoot();
		try {
			tempRoot = Files.createTempDirectory("lg2-ytmusic-incomplete-fallback-test");
			MonitorYoutubeMusicCache.setCacheDirectory(tempRoot);
			MonitorYoutubeMusicCache.deletePersistentTrack(url);
			Path entryDir = youtubeMusicEntryDir(tempRoot, url);
			Files.createDirectories(entryDir);
			writeCover(entryDir.resolve("cover.png"));
			Files.write(entryDir.resolve("audio.source"), new byte[55]);
			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Incomplete","artist":"Fallback","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);

			MonitorYoutubeMusicCache.LoadedTrack cached = MonitorYoutubeMusicCache.loadCompleteTrackIfPresent(url, new TaskProgress());
			require(cached == null, "YouTube Music playback startup must not treat incomplete cache files as the primary playable track");

			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Complete","artist":"Fallback","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100,"completedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);
			Files.write(entryDir.resolve("audio.source"), new byte[100]);
			Files.writeString(entryDir.resolve("complete.marker"), "ready", StandardCharsets.UTF_8);
			MonitorYoutubeMusicCache.LoadedTrack complete = MonitorYoutubeMusicCache.loadCompleteTrackIfPresent(url, new TaskProgress());
			require(complete != null, "YouTube Music playback startup should still use a verified complete local cache");
		} catch (IOException exception) {
			throw new AssertionError("Failed to prepare incomplete YouTube Music fallback test", exception);
		} finally {
			MonitorYoutubeMusicCache.setCacheDirectory(originalCacheRoot);
			deleteDirectoryQuietly(tempRoot);
		}
	}

	private static void youtubeMusicPartialCacheDoesNotPretendToBeActive() {
		Path tempRoot = null;
		String url = "https://www.youtube.com/watch?v=partial-inactive";
		Path originalCacheRoot = defaultYoutubeMusicCacheRoot();
		try {
			tempRoot = Files.createTempDirectory("lg2-ytmusic-partial-inactive-test");
			MonitorYoutubeMusicCache.setCacheDirectory(tempRoot);
			MonitorYoutubeMusicCache.deletePersistentTrack(url);
			Path entryDir = youtubeMusicEntryDir(tempRoot, url);
			Files.createDirectories(entryDir);
			writeCover(entryDir.resolve("cover.png"));
			Files.write(entryDir.resolve("audio.source"), new byte[40]);
			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Partial","artist":"Inactive","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);

			MonitorYoutubeMusicCache.QueueEntryCacheStatus status = MonitorYoutubeMusicCache.queueEntryCacheStatus(url);
			require(!status.active(), "partial YouTube Music cache without an active downloader must stay visually inactive");
			require(!status.complete(), "partial inactive YouTube Music cache must stay incomplete");
		} catch (IOException exception) {
			throw new AssertionError("Failed to prepare inactive partial YouTube Music cache test", exception);
		} finally {
			MonitorYoutubeMusicCache.setCacheDirectory(originalCacheRoot);
			deleteDirectoryQuietly(tempRoot);
		}
	}

	private static void youtubeMusicNeedsCompleteMarkerBeforeReportingFullCache() {
		Path tempRoot = null;
		String url = "https://www.youtube.com/watch?v=complete-marker";
		Path originalCacheRoot = defaultYoutubeMusicCacheRoot();
		try {
			tempRoot = Files.createTempDirectory("lg2-ytmusic-marker-test");
			MonitorYoutubeMusicCache.setCacheDirectory(tempRoot);
			MonitorYoutubeMusicCache.deletePersistentTrack(url);
			Path entryDir = youtubeMusicEntryDir(tempRoot, url);
			Files.createDirectories(entryDir);
			writeCover(entryDir.resolve("cover.png"));
			Files.write(entryDir.resolve("audio.source"), new byte[100]);
			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Complete","artist":"Marker","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);

			MonitorYoutubeMusicCache.QueueEntryCacheStatus incomplete = MonitorYoutubeMusicCache.queueEntryCacheStatus(url);
			require(!incomplete.complete(), "cache without completion marker must stay incomplete");
			require(incomplete.fraction() >= 0.98F && incomplete.fraction() <= 0.99F, "cache without completion marker must stay below 100 percent");

			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Complete","artist":"Marker","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100,"completedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);
			Files.writeString(entryDir.resolve("complete.marker"), "ready", StandardCharsets.UTF_8);
			MonitorYoutubeMusicCache.QueueEntryCacheStatus complete = MonitorYoutubeMusicCache.queueEntryCacheStatus(url);
			require(complete.complete(), "cache with completion marker must report itself as complete");
			require(complete.fraction() == 1.0F, "completed cache must report full progress");
		} catch (IOException exception) {
			throw new AssertionError("Failed to prepare completion marker YouTube Music cache test", exception);
		} finally {
			MonitorYoutubeMusicCache.setCacheDirectory(originalCacheRoot);
			deleteDirectoryQuietly(tempRoot);
		}
	}

	private static void youtubeMusicRejectsLegacyMarkerWithoutVerifiedFinalSize() {
		Path tempRoot = null;
		String url = "https://www.youtube.com/watch?v=legacy-marker";
		Path originalCacheRoot = defaultYoutubeMusicCacheRoot();
		try {
			tempRoot = Files.createTempDirectory("lg2-ytmusic-legacy-marker-test");
			MonitorYoutubeMusicCache.setCacheDirectory(tempRoot);
			MonitorYoutubeMusicCache.deletePersistentTrack(url);
			Path entryDir = youtubeMusicEntryDir(tempRoot, url);
			Files.createDirectories(entryDir);
			writeCover(entryDir.resolve("cover.png"));
			Files.write(entryDir.resolve("audio.source"), new byte[35]);
			Files.writeString(
					entryDir.resolve("meta.json"),
					"""
					{"title":"Legacy","artist":"Marker","durationMs":1234,"thumbnailUrl":"","fallbackCover":false,"expectedAudioBytes":100}
					""".trim(),
					StandardCharsets.UTF_8
			);
			Files.writeString(entryDir.resolve("complete.marker"), "ready", StandardCharsets.UTF_8);

			MonitorYoutubeMusicCache.QueueEntryCacheStatus status = MonitorYoutubeMusicCache.queueEntryCacheStatus(url);
			require(!status.complete(), "legacy completion marker without verified final size must not show a cached checkmark");
			require(status.fraction() >= 0.34F && status.fraction() <= 0.36F, "legacy completion marker must keep using real downloaded bytes for progress");
		} catch (IOException exception) {
			throw new AssertionError("Failed to prepare legacy completion marker YouTube Music cache test", exception);
		} finally {
			MonitorYoutubeMusicCache.setCacheDirectory(originalCacheRoot);
			deleteDirectoryQuietly(tempRoot);
		}
	}

	private static void youtubeMusicDirectThumbnailUsesStableYoutubeCoverUrl() {
		require(
				"https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg".equals(
						MonitorYoutubeMusicCache.directThumbnailUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL123")
				),
				"watch URLs must resolve to the stable direct YouTube thumbnail"
		);
		require(
				"https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg".equals(
						MonitorYoutubeMusicCache.directThumbnailUrl("https://youtu.be/dQw4w9WgXcQ?si=test")
				),
				"youtu.be URLs must resolve to the stable direct YouTube thumbnail"
		);
		require(
				"https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg".equals(
						MonitorYoutubeMusicCache.directThumbnailUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=test")
				),
				"YouTube Music URLs must resolve to the stable direct YouTube thumbnail"
		);
	}

	private static void sberDronesUsesDedicatedLiveCameraCatalog() {
		require(
				MonitorAppRegistry.findById("sberdrones").role() == MonitorAppRole.SBER_DRONES,
				"Sber Drones must be routed through the dedicated live-camera app role"
		);
		MonitorSberDronesCatalog.Source camera = MonitorSberDronesCatalog.Source.camera("minecraft:overworld", 1, 64, -3);
		String cameraUrl = MonitorSberDronesCatalog.url(camera);
		MonitorSberDronesCatalog.Source parsedCamera = MonitorSberDronesCatalog.parseUrl(cameraUrl, "");
		require(
				MonitorSberDronesCatalog.sameIdentity(camera, parsedCamera),
				"Sber Drones camera links must round-trip without using gallery media identity"
		);
		require(
				MonitorSberDronesCatalog.card(camera, true).subtitle().contains("online"),
				"Sber Drones cards must carry live-camera status metadata"
		);
	}

	private static void galleryRuntimePolicyIgnoresLiveCameraOnlyCollections() {
		require(
				!MonitorGalleryRuntimePolicy.hasSavedGalleryItems(ScreenViewMode.SBER_DRONES, 4, 4),
				"Sber Drones runtime state must not treat live camera cards as saved gallery entries"
		);
		require(
				!MonitorGalleryRuntimePolicy.hasSavedGalleryItems(ScreenViewMode.GALLERY, 3, 3),
				"a live-camera-only gallery list must stay lightweight and non-persisted"
		);
		require(
				MonitorGalleryRuntimePolicy.hasSavedGalleryItems(ScreenViewMode.GALLERY, 5, 2),
				"real saved gallery entries must still be detected inside mixed collections"
		);
	}

	private static void galleryRuntimePolicyRetainsOnlyActiveDecodedMedia() {
		require(
				MonitorGalleryRuntimePolicy.shouldRetainDecodedMedia(
						GalleryItemKind.MEDIA,
						"gallery://selected",
						2,
						2,
						"",
						"",
						""
				),
				"the selected gallery card must keep its decoded media"
		);
		require(
				MonitorGalleryRuntimePolicy.shouldRetainDecodedMedia(
						GalleryItemKind.MEDIA,
						"gallery://wallpaper",
						0,
						2,
						"",
						"gallery://wallpaper",
						""
				),
				"wallpaper media must survive gallery compaction"
		);
		require(
				!MonitorGalleryRuntimePolicy.shouldRetainDecodedMedia(
						GalleryItemKind.MEDIA,
						"gallery://stale",
						0,
						2,
						"gallery://selected",
						"gallery://wallpaper",
						"gallery://background"
				),
				"unused decoded gallery media should be eligible for eviction"
		);
	}

	private static void backgroundPlaybackPolicyIgnoresViewVisibility() {
		require(
				MonitorBackgroundPlaybackPolicy.animatedMediaActive(true, true, 2),
				"animated gallery backgrounds must stay active independently from the currently opened player UI"
		);
		require(
				MonitorBackgroundPlaybackPolicy.nextFrameDeadlineMillis(1200L, 1000L, 16) == 1200L,
				"existing background frame deadlines must not be pulled backward by unrelated playback refreshes"
		);
		require(
				MonitorBackgroundPlaybackPolicy.earliestPositiveDeadlineMillis(0L, 1400L, 1200L) == 1200L,
				"background scheduler must choose the closest real frame deadline"
		);
	}

	private static void directAudioSourcesDoNotResyncFromStaleMonitorPosition() {
		require(
				!SpeakerAudioPlaybackPolicy.shouldResyncPosition(false, false, false, 1800L, 100L, 500L),
				"local direct audio must not restart just because the monitor snapshot position is stale"
		);
		require(
				SpeakerAudioPlaybackPolicy.shouldResyncPosition(false, false, true, 1800L, 100L, 500L),
				"authoritative stream positions should still resync when drift exceeds tolerance"
		);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static Path defaultYoutubeMusicCacheRoot() {
		return Path.of(System.getProperty("user.dir"), "cache", "lg2-monitor", "youtube-music");
	}

	private static Path youtubeMusicEntryDir(Path root, String url) {
		return root.resolve(sha256(url));
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Missing SHA-256", exception);
		}
	}

	private static void writeCover(Path targetPath) throws IOException {
		BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setRGB(x, y, new Color(120, 140, 160, 255).getRGB());
			}
		}
		ImageIO.write(image, "png", targetPath.toFile());
	}

	private static void deleteDirectoryQuietly(Path directory) {
		if (directory == null) {
			return;
		}
		try (var paths = Files.walk(directory)) {
			for (Path path : (Iterable<Path>) paths.sorted((first, second) -> second.getNameCount() - first.getNameCount())::iterator) {
				Files.deleteIfExists(path);
			}
		} catch (IOException ignored) {
		}
	}
}
