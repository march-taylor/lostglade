package com.lostglade.server.monitor;

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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
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
import java.util.stream.Stream;

public final class MonitorMediaApp implements MonitorApp {
	private static final int MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024;
	private static final int CONNECT_TIMEOUT_MS = 4000;
	private static final int READ_TIMEOUT_MS = 12000;
	private static final int MAX_DIMENSION = 1024;
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
		return "Картинки и гифки по ссылке";
	}

	public static LoadedMedia loadFromUrl(String rawUrl) throws IOException {
		return loadFromUrl(rawUrl, null);
	}

	public static LoadedMedia loadFromUrl(String rawUrl, TaskProgress progress) throws IOException {
		URI uri = validateUri(rawUrl);
		return decode(loadCachedMedia(uri, progress).bytes(), progress);
	}

	public static String persistSavedGalleryMedia(String rawUrl) throws IOException {
		URI uri = validateUri(rawUrl);
		return loadCachedMedia(uri, null).mediaKey();
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
		byte[] bytes = download(uri.toURL(), progress);
		String mediaKey = hashBytes(bytes) + cacheExtension(uri);
		persistCacheBytes(savedGalleryMediaPath(mediaKey), bytes);
		persistUrlReference(uri, mediaKey);
		return new CachedMediaBytes(bytes, mediaKey);
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

	private static byte[] download(URL url, TaskProgress progress) throws IOException {
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
			return output.toByteArray();
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
				throw new IOException("Unsupported image");
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IOException("Unsupported image");
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
			} finally {
				reader.dispose();
			}
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
