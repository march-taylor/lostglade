package com.lostglade.server.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lostglade.server.progress.TaskProgress;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class MonitorMediaApp implements MonitorApp {
	private static final Gson GSON = new Gson();
	private static final int MAX_DOWNLOAD_BYTES = 64 * 1024 * 1024;
	private static final long MAX_VIDEO_SAVE_BYTES = 1024L * 1024L * 1024L;
	private static final int CONNECT_TIMEOUT_MS = 4000;
	private static final int READ_TIMEOUT_MS = 12000;
	private static final int MAX_DIMENSION = 1024;
	private static final int COMMAND_TIMEOUT_SEC = 20;
	private static final String DEFAULT_FFMPEG_BIN = "ffmpeg";
	private static final String DEFAULT_FFPROBE_BIN = "ffprobe";
	private static final int VIDEO_PREVIEW_WIDTH = 480;
	private static final Set<String> DIRECT_VIDEO_EXTENSIONS = Set.of(".mp4", ".m4v", ".mov", ".webm");
	private static volatile Path cacheDirectory = Path.of("cache", "lg2-monitor", "media");

	@Override
	public String id() {
		return "gallery";
	}

	@Override
	public String title() {
		return "GALLERY";
	}

	@Override
	public String iconResourcePath() {
		return "/assets/lg2/textures/monitor/media_app.png";
	}

	@Override
	public int accentStartRgb() {
		return 0x40ADFF;
	}

	@Override
	public int accentEndRgb() {
		return 0x1054BC;
	}

	@Override
	public int panelRgb() {
		return 0x10161C;
	}

	@Override
	public String screenTitle() {
		return "Галерея";
	}

	@Override
	public String screenHint() {
		return "Картинки, гифки и видео по ссылке";
	}

	public static LoadedMedia loadFromUrl(String rawUrl) throws IOException {
		return loadFromUrl(rawUrl, null);
	}

	public static LoadedMedia loadFromUrl(String rawUrl, TaskProgress progress) throws IOException {
		URI uri = validateUri(rawUrl);
		return decode(loadCachedMedia(uri, progress).bytes(), progress);
	}

	public static boolean looksLikeDirectVideoUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return false;
		}
		URI uri;
		try {
			uri = validateUri(rawUrl);
		} catch (IOException exception) {
			return false;
		}
		String extension = cacheExtension(uri);
		if (DIRECT_VIDEO_EXTENSIONS.contains(extension)) {
			return true;
		}
		String contentType = probeContentType(uri);
		return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("video/");
	}

	public static LoadedVideo loadVideoFromUrl(String rawUrl, TaskProgress progress) throws IOException {
		URI uri = validateUri(rawUrl);
		return probeVideo(uri.toString(), uri.toString(), progress);
	}

	public static String persistSavedGalleryMedia(String rawUrl) throws IOException {
		URI uri = validateUri(rawUrl);
		return loadCachedMedia(uri, null).mediaKey();
	}

	public static String persistSavedGalleryVideo(String rawUrl, TaskProgress progress) throws IOException {
		URI uri = validateUri(rawUrl);
		String existingKey = cachedMediaKey(uri);
		if (existingKey != null && !existingKey.isBlank()) {
			return existingKey;
		}
		return downloadVideoToCache(uri, progress);
	}

	public static LoadedMedia loadSavedGalleryMedia(String mediaKey, TaskProgress progress) throws IOException {
		Path savedPath = savedGalleryMediaPath(mediaKey);
		if (savedPath == null || !Files.isRegularFile(savedPath)) {
			throw new IOException("Saved gallery media is missing");
		}
		if (progress != null) {
			progress.setIndeterminate("LOADING LOCAL");
		}
		byte[] bytes = Files.readAllBytes(savedPath);
		return decode(bytes, progress);
	}

	public static LoadedVideo loadSavedGalleryVideo(String mediaKey, TaskProgress progress) throws IOException {
		Path savedPath = savedGalleryMediaPath(mediaKey);
		if (savedPath == null || !Files.isRegularFile(savedPath)) {
			throw new IOException("Saved gallery video is missing");
		}
		if (progress != null) {
			progress.setIndeterminate("PROBING VIDEO");
		}
		return probeVideo(savedPath.toAbsolutePath().toString(), savedPath.toAbsolutePath().toString(), progress);
	}

	public static void setCacheDirectory(Path directory) {
		if (directory != null) {
			cacheDirectory = directory;
		}
	}

	public static void deleteSavedGalleryMedia(String mediaKey) {
		deleteFileQuietly(savedGalleryMediaPath(mediaKey), cacheDirectory);
	}

	public static void deleteCachedUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return;
		}
		try {
			URI uri = validateUri(rawUrl);
			deleteFileQuietly(urlReferencePath(uri), cacheDirectory);
		} catch (IOException ignored) {
		}
	}

	private static URI validateUri(String rawUrl) throws IOException {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw new IOException("Empty URL");
		}
		try {
			URI uri = URI.create(rawUrl.trim());
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
				throw new IOException("Only http/https URLs are supported");
			}
			return uri;
		} catch (IllegalArgumentException exception) {
			throw new IOException("Invalid URL", exception);
		}
	}

	private static CachedMediaBytes loadCachedMedia(URI uri, TaskProgress progress) throws IOException {
		String cachedMediaKey = cachedMediaKey(uri);
		Path savedPath = savedGalleryMediaPath(cachedMediaKey);
		if (cachedMediaKey != null && savedPath != null && Files.isRegularFile(savedPath)) {
			if (progress != null) {
				progress.setIndeterminate("LOADING CACHE");
			}
			return new CachedMediaBytes(Files.readAllBytes(savedPath), cachedMediaKey);
		}
		DownloadedMediaBytes downloaded = download(uri, progress);
		byte[] bytes = downloaded.bytes();
		String mediaKey = hashBytes(bytes) + downloaded.extension();
		persistCacheBytes(savedGalleryMediaPath(mediaKey), bytes);
		persistUrlReference(uri, mediaKey);
		return new CachedMediaBytes(bytes, mediaKey);
	}

	private static String downloadVideoToCache(URI uri, TaskProgress progress) throws IOException {
		DownloadToFileResult result = downloadToFile(uri, progress);
		Path targetPath = savedGalleryMediaPath(result.mediaKey());
		if (targetPath == null) {
			deleteFileQuietly(result.tempPath(), cacheDirectory);
			throw new IOException("Invalid media key");
		}
		try {
			Path parent = targetPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			if (Files.isRegularFile(targetPath)) {
				deleteFileQuietly(result.tempPath(), cacheDirectory);
			} else {
				try {
					Files.move(result.tempPath(), targetPath, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException ignored) {
					Files.move(result.tempPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			persistUrlReference(uri, result.mediaKey());
			if (progress != null) {
				progress.complete("READY");
			}
			return result.mediaKey();
		} catch (IOException exception) {
			deleteFileQuietly(result.tempPath(), cacheDirectory);
			throw exception;
		}
	}

	private static LoadedVideo probeVideo(String displayUrl, String input, TaskProgress progress) throws IOException {
		if (input == null || input.isBlank()) {
			throw new IOException("Video input is missing");
		}
		if (progress != null) {
			progress.setIndeterminate("PROBING VIDEO");
		}
		VideoMetadata metadata = probeVideoMetadata(input);
		if (progress != null) {
			progress.setIndeterminate("CAPTURING FRAME");
		}
		BufferedImage preview = captureVideoPreview(input);
		if (progress != null) {
			progress.complete("READY");
		}
		return new LoadedVideo(
				preview,
				Math.max(0L, metadata.durationMs()),
				Math.max(1, metadata.width()),
				Math.max(1, metadata.height()),
				input,
				input
		);
	}

	private static void persistCacheBytes(Path cachePath, byte[] bytes) {
		if (cachePath == null || bytes == null || bytes.length == 0) {
			return;
		}
		try {
			Path parent = cachePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path tempPath = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
			Files.write(tempPath, bytes);
			try {
				Files.move(tempPath, cachePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignored) {
				Files.move(tempPath, cachePath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ignored) {
		}
	}

	private static String cachedMediaKey(URI uri) {
		if (uri == null) {
			return null;
		}
		Path referencePath = urlReferencePath(uri);
		if (referencePath != null && Files.isRegularFile(referencePath)) {
			try {
				String mediaKey = Files.readString(referencePath).trim();
				if (!mediaKey.isBlank() && Files.isRegularFile(savedGalleryMediaPath(mediaKey))) {
					return mediaKey;
				}
				deleteFileQuietly(referencePath, cacheDirectory);
			} catch (IOException ignored) {
			}
		}
		return null;
	}

	private static void persistUrlReference(URI uri, String mediaKey) {
		Path referencePath = urlReferencePath(uri);
		if (referencePath == null || mediaKey == null || mediaKey.isBlank()) {
			return;
		}
		try {
			Path parent = referencePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path tempPath = referencePath.resolveSibling(referencePath.getFileName() + ".tmp");
			Files.writeString(tempPath, mediaKey);
			try {
				Files.move(tempPath, referencePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException ignored) {
				Files.move(tempPath, referencePath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ignored) {
		}
	}

	private static Path urlReferencePath(URI uri) {
		if (uri == null) {
			return null;
		}
		String cacheKey = hashString(uri.toString());
		if (cacheKey == null || cacheKey.isBlank()) {
			return null;
		}
		return cacheDirectory.resolve("url-index").resolve(cacheKey + ".ref");
	}

	private static Path savedGalleryMediaPath(String mediaKey) {
		if (mediaKey == null || mediaKey.isBlank()) {
			return null;
		}
		String normalized = mediaKey.trim();
		if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
			return null;
		}
		return cacheDirectory.resolve("blobs").resolve(normalized);
	}

	private static String probeContentType(URI uri) {
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) uri.toURL().openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
			connection.setReadTimeout(READ_TIMEOUT_MS);
			connection.setRequestProperty("User-Agent", "LostGladeMonitor/1.0");
			connection.setRequestMethod("HEAD");
			connection.connect();
			int status = connection.getResponseCode();
			if (status >= 200 && status < 400) {
				String contentType = connection.getContentType();
				if (contentType != null && !contentType.isBlank()) {
					return contentType;
				}
			}
		} catch (IOException ignored) {
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}

		try {
			connection = (HttpURLConnection) uri.toURL().openConnection();
			connection.setInstanceFollowRedirects(true);
			connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
			connection.setReadTimeout(READ_TIMEOUT_MS);
			connection.setRequestProperty("User-Agent", "LostGladeMonitor/1.0");
			connection.connect();
			int status = connection.getResponseCode();
			return status >= 200 && status < 400 ? connection.getContentType() : null;
		} catch (IOException ignored) {
			return null;
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	private static String extensionFromContentType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return null;
		}
		String normalized = contentType.toLowerCase(Locale.ROOT);
		int separator = normalized.indexOf(';');
		if (separator >= 0) {
			normalized = normalized.substring(0, separator).trim();
		}
		return switch (normalized) {
			case "image/png" -> ".png";
			case "image/jpeg", "image/jpg" -> ".jpg";
			case "image/gif" -> ".gif";
			case "image/webp" -> ".webp";
			case "image/bmp" -> ".bmp";
			case "image/avif" -> ".avif";
			case "image/x-icon", "image/vnd.microsoft.icon" -> ".ico";
			case "video/mp4" -> ".mp4";
			case "video/webm" -> ".webm";
			case "video/quicktime" -> ".mov";
			case "video/x-m4v" -> ".m4v";
			default -> null;
		};
	}

	private static String sniffExtension(byte[] bytes) {
		if (bytes == null || bytes.length < 12) {
			return null;
		}
		if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
			return ".png";
		}
		if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
			return ".jpg";
		}
		if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
			return ".gif";
		}
		if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
				&& bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
			return ".webp";
		}
		if (bytes[0] == 'B' && bytes[1] == 'M') {
			return ".bmp";
		}
		if (bytes.length >= 12
				&& bytes[4] == 'f'
				&& bytes[5] == 't'
				&& bytes[6] == 'y'
				&& bytes[7] == 'p') {
			return ".mp4";
		}
		return null;
	}

	private static String resolvedExtension(URI uri, String contentType, byte[] bytes) {
		String extension = extensionFromContentType(contentType);
		if (extension != null) {
			return extension;
		}
		extension = sniffExtension(bytes);
		if (extension != null) {
			return extension;
		}
		return cacheExtension(uri);
	}

	private static void deleteFileQuietly(Path path, Path stopDirectory) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
			pruneEmptyParents(path.getParent(), stopDirectory);
		} catch (IOException ignored) {
		}
	}

	private static void pruneEmptyParents(Path directory, Path stopDirectory) {
		Path current = directory;
		while (current != null && stopDirectory != null && current.startsWith(stopDirectory) && !current.equals(stopDirectory)) {
			try (Stream<Path> children = Files.list(current)) {
				if (children.findAny().isPresent()) {
					return;
				}
			} catch (IOException exception) {
				return;
			}
			try {
				Files.deleteIfExists(current);
			} catch (IOException exception) {
				return;
			}
			current = current.getParent();
		}
	}

	private record CachedMediaBytes(byte[] bytes, String mediaKey) {
	}

	private record DownloadedMediaBytes(byte[] bytes, String extension) {
	}

	private static String cacheExtension(URI uri) {
		String path = uri != null ? uri.getPath() : null;
		if (path == null || path.isBlank()) {
			return ".bin";
		}
		int dotIndex = path.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex >= path.length() - 1) {
			return ".bin";
		}
		String extension = path.substring(dotIndex).toLowerCase(Locale.ROOT);
		return extension.matches("\\.[a-z0-9]{1,6}") ? extension : ".bin";
	}

	private static String hashString(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			return Integer.toHexString(value.hashCode());
		}
	}

	private static String hashBytes(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(bytes));
		} catch (NoSuchAlgorithmException exception) {
			return Integer.toHexString(java.util.Arrays.hashCode(bytes));
		}
	}

	private static DownloadedMediaBytes download(URI uri, TaskProgress progress) throws IOException {
		IOException lastException = null;
		for (URI candidate : downloadCandidates(uri)) {
			try {
				return downloadCandidate(candidate.toURL(), progress);
			} catch (IOException exception) {
				lastException = exception;
			}
		}
		throw lastException != null ? lastException : new IOException("Failed to download media");
	}

	private static List<URI> downloadCandidates(URI uri) {
		List<URI> candidates = new ArrayList<>();
		if (uri == null) {
			return candidates;
		}
		candidates.add(uri);
		String host = uri.getHost();
		if (host != null && host.equalsIgnoreCase("media.discordapp.net")) {
			try {
				candidates.add(new URI(
						uri.getScheme(),
						uri.getUserInfo(),
						"cdn.discordapp.com",
						uri.getPort(),
						uri.getPath(),
						null,
						null
				));
			} catch (URISyntaxException ignored) {
			}
		}
		return candidates;
	}

	private static DownloadedMediaBytes downloadCandidate(URL url, TaskProgress progress) throws IOException {
		if (progress != null) {
			progress.setIndeterminate("CONNECTING");
		}
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setInstanceFollowRedirects(true);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setRequestProperty("User-Agent", "LostGladeMonitor/1.0");
		connection.connect();

		int status = connection.getResponseCode();
		if (status < 200 || status >= 300) {
			throw new IOException("HTTP " + status);
		}
		long contentLength = connection.getContentLengthLong();
		if (contentLength > MAX_DOWNLOAD_BYTES) {
			throw new IOException("File is too large");
		}
		if (progress != null) {
			if (contentLength > 0L) {
				progress.setProgress("DOWNLOADING", 0L, contentLength);
			} else {
				progress.setIndeterminate("DOWNLOADING");
			}
		}

		try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int total = 0;
			int read;
			while ((read = input.read(buffer)) >= 0) {
				total += read;
				if (total > MAX_DOWNLOAD_BYTES) {
					throw new IOException("File is too large");
				}
				output.write(buffer, 0, read);
				if (progress != null) {
					if (contentLength > 0L) {
						progress.setProgress("DOWNLOADING", total, contentLength);
					} else {
						progress.setStage("DOWNLOADING");
					}
				}
			}
			byte[] bytes = output.toByteArray();
			return new DownloadedMediaBytes(bytes, resolvedExtension(toUri(url), connection.getContentType(), bytes));
		} finally {
			connection.disconnect();
		}
	}

	private static DownloadToFileResult downloadToFile(URI uri, TaskProgress progress) throws IOException {
		IOException lastException = null;
		for (URI candidate : downloadCandidates(uri)) {
			try {
				return downloadCandidateToFile(candidate.toURL(), progress);
			} catch (IOException exception) {
				lastException = exception;
			}
		}
		throw lastException != null ? lastException : new IOException("Failed to download video");
	}

	private static DownloadToFileResult downloadCandidateToFile(URL url, TaskProgress progress) throws IOException {
		if (progress != null) {
			progress.setIndeterminate("DOWNLOADING");
		}
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setInstanceFollowRedirects(true);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setRequestProperty("User-Agent", "LostGladeMonitor/1.0");
		connection.connect();

		int status = connection.getResponseCode();
		if (status < 200 || status >= 300) {
			throw new IOException("HTTP " + status);
		}
		long contentLength = connection.getContentLengthLong();
		if (contentLength > MAX_VIDEO_SAVE_BYTES) {
			throw new IOException("Video is too large");
		}
		String extension = resolvedExtension(toUri(url), connection.getContentType(), new byte[0]);
		if (extension == null || extension.isBlank()) {
			extension = ".mp4";
		}
		Files.createDirectories(cacheDirectory);
		Path tempPath = Files.createTempFile(cacheDirectory, "video-", extension + ".tmp");
		MessageDigest digest = sha256Digest();
		long total = 0L;
		try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(tempPath.toFile())) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				total += read;
				if (total > MAX_VIDEO_SAVE_BYTES) {
					throw new IOException("Video is too large");
				}
				output.write(buffer, 0, read);
				digest.update(buffer, 0, read);
				if (progress != null) {
					if (contentLength > 0L) {
						progress.setProgress("DOWNLOADING", total, contentLength);
					} else {
						progress.setStage("DOWNLOADING");
					}
				}
			}
			String mediaKey = HexFormat.of().formatHex(digest.digest()) + extension;
			return new DownloadToFileResult(tempPath, mediaKey);
		} catch (IOException exception) {
			deleteFileQuietly(tempPath, cacheDirectory);
			throw exception;
		} finally {
			connection.disconnect();
		}
	}

	private static LoadedMedia decode(byte[] bytes, TaskProgress progress) throws IOException {
		if (progress != null) {
			progress.setIndeterminate("DECODING");
		}
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
			if (input == null) {
				return decodeWithFfmpegFallback(bytes, progress);
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				return decodeWithFfmpegFallback(bytes, progress);
			}

			ImageReader reader = readers.next();
			try {
				reader.setInput(input, false, false);
				String format = reader.getFormatName();
				if (format != null && format.equalsIgnoreCase("gif")) {
					return decodeGif(reader, progress);
				}
				BufferedImage image = scaleDown(toArgb(reader.read(0)));
				if (progress != null) {
					progress.complete("READY");
				}
				return new LoadedMedia(List.of(image), List.of(1000), image.getWidth(), image.getHeight(), false);
			} catch (IOException exception) {
				return decodeWithFfmpegFallback(bytes, progress);
			} finally {
				reader.dispose();
			}
		}
	}

	private static LoadedMedia decodeWithFfmpegFallback(byte[] bytes, TaskProgress progress) throws IOException {
		if (progress != null) {
			progress.setIndeterminate("DECODING FALLBACK");
		}
		Path tempDirectory = Files.createTempDirectory("lg2-media-decode-");
		Path inputPath = tempDirectory.resolve("input" + resolvedExtension(null, null, bytes));
		Path framesDirectory = tempDirectory.resolve("frames");
		try {
			Files.write(inputPath, bytes);
			Files.createDirectories(framesDirectory);
			runCommand(List.of(
					ffmpegBin(),
					"-hide_banner",
					"-loglevel",
					"error",
					"-nostdin",
					"-i",
					inputPath.toString(),
					"-vsync",
					"0",
					framesDirectory.resolve("frame_%06d.png").toString()
			), COMMAND_TIMEOUT_SEC);
			Map<Integer, Integer> frameDurations = probeFrameDurations(inputPath);
			List<BufferedImage> frames = new ArrayList<>();
			List<Integer> delays = new ArrayList<>();
			try (Stream<Path> paths = Files.list(framesDirectory)) {
				List<Path> framePaths = paths.filter(Files::isRegularFile).sorted().toList();
				if (framePaths.isEmpty()) {
					throw new IOException("Unsupported image");
				}
				if (progress != null) {
					progress.setProgress("DECODING", 0L, framePaths.size());
				}
				for (int index = 0; index < framePaths.size(); index++) {
					BufferedImage frame = ImageIO.read(framePaths.get(index).toFile());
					if (frame == null) {
						continue;
					}
					frames.add(scaleDown(toArgb(frame)));
					delays.add(Math.max(20, frameDurations.getOrDefault(index, 100)));
					if (progress != null) {
						progress.setProgress("DECODING", index + 1L, framePaths.size());
					}
				}
			}
			if (frames.isEmpty()) {
				throw new IOException("Unsupported image");
			}
			if (progress != null) {
				progress.complete("READY");
			}
			BufferedImage first = frames.get(0);
			return new LoadedMedia(frames, delays, first.getWidth(), first.getHeight(), frames.size() > 1);
		} finally {
			deleteRecursivelyQuietly(tempDirectory);
		}
	}

	private static LoadedMedia decodeGif(ImageReader reader, TaskProgress progress) throws IOException {
		int frameCount = Math.max(1, reader.getNumImages(true));
		if (progress != null) {
			progress.setProgress("DECODING", 0L, frameCount);
		}
		BufferedImage canvas = null;
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();

		for (int index = 0; index < frameCount; index++) {
			BufferedImage rawFrame = toArgb(reader.read(index));
			IIOMetadata metadata = reader.getImageMetadata(index);
			GifFrameInfo frameInfo = gifFrameInfo(metadata, rawFrame.getWidth(), rawFrame.getHeight());
			if (canvas == null) {
				canvas = new BufferedImage(frameInfo.canvasWidth(), frameInfo.canvasHeight(), BufferedImage.TYPE_INT_ARGB);
			}

			BufferedImage previous = copyImage(canvas);
			Graphics2D graphics = canvas.createGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			graphics.drawImage(rawFrame, frameInfo.left(), frameInfo.top(), null);
			graphics.dispose();

			frames.add(scaleDown(copyImage(canvas)));
			delays.add(frameInfo.delayMillis());
			if (progress != null) {
				progress.setProgress("DECODING", index + 1L, frameCount);
			}

			if ("restoreToBackgroundColor".equals(frameInfo.disposalMethod())) {
				clearRect(canvas, frameInfo.left(), frameInfo.top(), rawFrame.getWidth(), rawFrame.getHeight());
			} else if ("restoreToPrevious".equals(frameInfo.disposalMethod())) {
				canvas = previous;
			}
		}

		if (frames.isEmpty()) {
			throw new IOException("GIF has no frames");
		}
		if (progress != null) {
			progress.complete("READY");
		}
		BufferedImage first = frames.get(0);
		return new LoadedMedia(frames, delays, first.getWidth(), first.getHeight(), frames.size() > 1);
	}

	private static GifFrameInfo gifFrameInfo(IIOMetadata metadata, int fallbackWidth, int fallbackHeight) {
		int left = 0;
		int top = 0;
		int canvasWidth = fallbackWidth;
		int canvasHeight = fallbackHeight;
		int delayMillis = 100;
		String disposalMethod = "none";

		if (metadata != null) {
			String nativeFormat = metadata.getNativeMetadataFormatName();
			if (nativeFormat != null) {
				Node root = metadata.getAsTree(nativeFormat);
				NodeList children = root.getChildNodes();
				for (int i = 0; i < children.getLength(); i++) {
					Node child = children.item(i);
					if ("ImageDescriptor".equals(child.getNodeName())) {
						NamedNodeMap attrs = child.getAttributes();
						left = parseInt(attrs, "imageLeftPosition", left);
						top = parseInt(attrs, "imageTopPosition", top);
						canvasWidth = Math.max(canvasWidth, parseInt(attrs, "imageWidth", fallbackWidth) + left);
						canvasHeight = Math.max(canvasHeight, parseInt(attrs, "imageHeight", fallbackHeight) + top);
					} else if ("GraphicControlExtension".equals(child.getNodeName())) {
						NamedNodeMap attrs = child.getAttributes();
						int delayHundredths = parseInt(attrs, "delayTime", 10);
						delayMillis = Math.max(20, delayHundredths * 10);
						Node disposalNode = attrs.getNamedItem("disposalMethod");
						if (disposalNode != null) {
							disposalMethod = disposalNode.getNodeValue();
						}
					}
				}
			}
		}

		return new GifFrameInfo(left, top, canvasWidth, canvasHeight, delayMillis, disposalMethod);
	}

	private static int parseInt(NamedNodeMap attributes, String key, int fallback) {
		if (attributes == null) {
			return fallback;
		}
		Node node = attributes.getNamedItem(key);
		if (node == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(node.getNodeValue());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static BufferedImage scaleDown(BufferedImage image) {
		if (image.getWidth() <= MAX_DIMENSION && image.getHeight() <= MAX_DIMENSION) {
			return image;
		}
		double scale = Math.min(MAX_DIMENSION / (double) image.getWidth(), MAX_DIMENSION / (double) image.getHeight());
		int targetWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int targetHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = scaled.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
		graphics.dispose();
		return scaled;
	}

	private static BufferedImage copyImage(BufferedImage source) {
		BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = copy.createGraphics();
		graphics.drawImage(source, 0, 0, null);
		graphics.dispose();
		return copy;
	}

	private static void clearRect(BufferedImage image, int x, int y, int width, int height) {
		for (int py = Math.max(0, y); py < Math.min(image.getHeight(), y + height); py++) {
			for (int px = Math.max(0, x); px < Math.min(image.getWidth(), x + width); px++) {
				image.setRGB(px, py, 0x00000000);
			}
		}
	}

	private static BufferedImage toArgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}
		BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = converted.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return converted;
	}

	private static Map<Integer, Integer> probeFrameDurations(Path inputPath) {
		Map<Integer, Integer> durations = new TreeMap<>();
		if (inputPath == null) {
			return durations;
		}
		try {
			String output = runCommand(List.of(
					ffprobeBin(),
					"-v",
					"error",
					"-select_streams",
					"v:0",
					"-show_entries",
					"frame=best_effort_timestamp_time,pkt_duration_time",
					"-of",
					"csv=p=0",
					inputPath.toString()
			), COMMAND_TIMEOUT_SEC);
			String[] lines = output.split("\\R");
			double previousTimestampSeconds = -1.0D;
			int frameIndex = 0;
			for (String line : lines) {
				if (line == null || line.isBlank()) {
					continue;
				}
				String[] parts = line.split(",");
				double timestampSeconds = parseDoubleOr(parts.length > 0 ? parts[0] : "", -1.0D);
				double durationSeconds = parseDoubleOr(parts.length > 1 ? parts[1] : "", -1.0D);
				if (durationSeconds <= 0.0D && timestampSeconds >= 0.0D && previousTimestampSeconds >= 0.0D) {
					durationSeconds = timestampSeconds - previousTimestampSeconds;
				}
				if (timestampSeconds >= 0.0D) {
					previousTimestampSeconds = timestampSeconds;
				}
				durations.put(frameIndex++, durationSeconds > 0.0D ? Math.max(20, (int) Math.round(durationSeconds * 1000.0D)) : 100);
			}
		} catch (IOException ignored) {
		}
		return durations;
	}

	private static VideoMetadata probeVideoMetadata(String input) throws IOException {
		String output = runCommand(List.of(
				ffprobeBin(),
				"-v",
				"error",
				"-select_streams",
				"v:0",
				"-show_entries",
				"stream=width,height:format=duration",
				"-of",
				"json",
				input
		), COMMAND_TIMEOUT_SEC);
		JsonObject root = GSON.fromJson(output, JsonObject.class);
		JsonArray streams = root != null && root.has("streams") && root.get("streams").isJsonArray()
				? root.getAsJsonArray("streams")
				: null;
		int width = 0;
		int height = 0;
		if (streams != null && !streams.isEmpty() && streams.get(0).isJsonObject()) {
			JsonObject stream = streams.get(0).getAsJsonObject();
			width = getInt(stream, "width", 0);
			height = getInt(stream, "height", 0);
		}
		JsonObject format = root != null && root.has("format") && root.get("format").isJsonObject()
				? root.getAsJsonObject("format")
				: null;
		long durationMs = Math.round(getDouble(format, "duration", 0.0D) * 1000.0D);
		if (width <= 0 || height <= 0) {
			throw new IOException("Unsupported video");
		}
		return new VideoMetadata(width, height, Math.max(0L, durationMs));
	}

	private static BufferedImage captureVideoPreview(String input) throws IOException {
		return decodeImageBytes(runBinaryCommand(List.of(
				ffmpegBin(),
				"-hide_banner",
				"-loglevel",
				"error",
				"-nostdin",
				"-i",
				input,
				"-frames:v",
				"1",
				"-an",
				"-vf",
				"scale=w=" + VIDEO_PREVIEW_WIDTH + ":h=-2:force_original_aspect_ratio=decrease",
				"-q:v",
				"4",
				"-f",
				"image2pipe",
				"-vcodec",
				"mjpeg",
				"-"
		), COMMAND_TIMEOUT_SEC));
	}

	private static String runCommand(List<String> command, int timeoutSeconds) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();
		try (InputStream input = process.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IOException("Timed out running " + command.get(0));
			}
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				output.write(buffer, 0, read);
			}
			String text = output.toString(java.nio.charset.StandardCharsets.UTF_8);
			if (process.exitValue() != 0) {
				throw new IOException(text == null || text.isBlank() ? "Command failed: " + String.join(" ", command) : text.trim());
			}
			return text;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("Interrupted while decoding media", exception);
		} finally {
			process.destroy();
		}
	}

	private static byte[] runBinaryCommand(List<String> command, int timeoutSeconds) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.redirectErrorStream(true);
		Process process = builder.start();
		try {
			byte[] output = readProcessOutput(process, timeoutSeconds, "Timed out running " + command.get(0));
			if (process.exitValue() != 0) {
				throw new IOException("Command failed: " + String.join(" ", command));
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

	private static byte[] readProcessOutput(Process process, int timeoutSeconds, String timeoutMessage) throws IOException, InterruptedException {
		ProcessOutputReader reader = new ProcessOutputReader(process.getInputStream());
		Thread thread = new Thread(reader, "lg2-media-process-output");
		thread.setDaemon(true);
		thread.start();
		boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			thread.join(1000L);
			throw new IOException(timeoutMessage);
		}
		thread.join(1000L);
		if (reader.exception() != null) {
			throw reader.exception();
		}
		return reader.bytes();
	}

	private static BufferedImage decodeImageBytes(byte[] bytes) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
		if (image == null) {
			throw new IOException("Unsupported preview frame");
		}
		return toArgb(image);
	}

	private static String ffmpegBin() {
		return readStringSetting("FFMPEG_BIN", "lg2.youtube.ffmpegBin", DEFAULT_FFMPEG_BIN);
	}

	private static String ffprobeBin() {
		return readStringSetting("FFPROBE_BIN", "lg2.media.ffprobeBin", DEFAULT_FFPROBE_BIN);
	}

	private static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Missing SHA-256", exception);
		}
	}

	private static int getInt(JsonObject object, String key, int fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback;
	}

	private static double getDouble(JsonObject object, String key, double fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsDouble() : fallback;
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

	private static void deleteRecursivelyQuietly(Path root) {
		if (root == null || !Files.exists(root)) {
			return;
		}
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : (Iterable<Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator) {
				Files.deleteIfExists(path);
			}
		} catch (IOException ignored) {
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

	private static double parseDoubleOr(String value, double fallback) {
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static URI toUri(URL url) {
		if (url == null) {
			return null;
		}
		try {
			return url.toURI();
		} catch (URISyntaxException exception) {
			return null;
		}
	}

	public record LoadedMedia(
			List<BufferedImage> frames,
			List<Integer> frameDurationsMillis,
			int width,
			int height,
			boolean animated
	) {
		public BufferedImage frame(int index) {
			if (this.frames.isEmpty()) {
				return null;
			}
			return this.frames.get(Math.floorMod(index, this.frames.size()));
		}

		public int delayMillis(int index) {
			if (this.frameDurationsMillis.isEmpty()) {
				return 100;
			}
			return Math.max(20, this.frameDurationsMillis.get(Math.floorMod(index, this.frameDurationsMillis.size())));
		}

		public int frameCount() {
			return this.frames.size();
		}
	}

	public record LoadedVideo(
			BufferedImage preview,
			long durationMs,
			int width,
			int height,
			String playbackInput,
			String audioInput
	) {
	}

	private record DownloadToFileResult(Path tempPath, String mediaKey) {
	}

	private record VideoMetadata(int width, int height, long durationMs) {
	}

	private record GifFrameInfo(
			int left,
			int top,
			int canvasWidth,
			int canvasHeight,
			int delayMillis,
			String disposalMethod
	) {
	}
}
