package com.lostglade.server;

import com.lostglade.server.monitor.MonitorYoutubeMusicCache;
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
		youtubeMusicNeedsCompleteMarkerBeforeReportingFullCache();
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

			MonitorYoutubeMusicCache.LoadedTrack loaded = MonitorYoutubeMusicCache.load(url, new TaskProgress());
			String expectedPath = entryDir.resolve("audio.source").toAbsolutePath().toString();
			require(
					expectedPath.equals(loaded.video().playbackInput()) && expectedPath.equals(loaded.video().audioInput()),
					"partially cached YouTube Music track must open from the local growing cache file"
			);
		} catch (IOException exception) {
			throw new AssertionError("Failed to prepare partial YouTube Music cache test", exception);
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
