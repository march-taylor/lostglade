package com.lostglade.server.monitor;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class MonitorMediaApp implements MonitorApp {
	private static final int MAX_DOWNLOAD_BYTES = 16 * 1024 * 1024;
	private static final int CONNECT_TIMEOUT_MS = 4000;
	private static final int READ_TIMEOUT_MS = 12000;
	private static final int MAX_DIMENSION = 1024;

	@Override
	public String id() {
		return "media";
	}

	@Override
	public String title() {
		return "MEDIA";
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
		return "Local media";
	}

	@Override
	public String screenHint() {
		return "Future gallery and image feed";
	}

	public static LoadedMedia loadFromUrl(String rawUrl) throws IOException {
		URI uri = validateUri(rawUrl);
		byte[] bytes = download(uri.toURL());
		return decode(bytes);
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

	private static byte[] download(URL url) throws IOException {
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
			}
			return output.toByteArray();
		} finally {
			connection.disconnect();
		}
	}

	private static LoadedMedia decode(byte[] bytes) throws IOException {
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
					return decodeGif(reader);
				}
				BufferedImage image = scaleDown(toArgb(reader.read(0)));
				return new LoadedMedia(List.of(image), List.of(1), image.getWidth(), image.getHeight(), false);
			} finally {
				reader.dispose();
			}
		}
	}

	private static LoadedMedia decodeGif(ImageReader reader) throws IOException {
		int frameCount = Math.max(1, reader.getNumImages(true));
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
			delays.add(frameInfo.delayTicks());

			if ("restoreToBackgroundColor".equals(frameInfo.disposalMethod())) {
				clearRect(canvas, frameInfo.left(), frameInfo.top(), rawFrame.getWidth(), rawFrame.getHeight());
			} else if ("restoreToPrevious".equals(frameInfo.disposalMethod())) {
				canvas = previous;
			}
		}

		if (frames.isEmpty()) {
			throw new IOException("GIF has no frames");
		}
		BufferedImage first = frames.get(0);
		return new LoadedMedia(frames, delays, first.getWidth(), first.getHeight(), frames.size() > 1);
	}

	private static GifFrameInfo gifFrameInfo(IIOMetadata metadata, int fallbackWidth, int fallbackHeight) {
		int left = 0;
		int top = 0;
		int canvasWidth = fallbackWidth;
		int canvasHeight = fallbackHeight;
		int delayTicks = 2;
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
						delayTicks = Math.max(1, Math.round(delayHundredths / 2.0F));
						Node disposalNode = attrs.getNamedItem("disposalMethod");
						if (disposalNode != null) {
							disposalMethod = disposalNode.getNodeValue();
						}
					}
				}
			}
		}

		return new GifFrameInfo(left, top, canvasWidth, canvasHeight, delayTicks, disposalMethod);
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
			List<Integer> frameDurationsTicks,
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

		public int delayTicks(int index) {
			if (this.frameDurationsTicks.isEmpty()) {
				return 2;
			}
			return Math.max(1, this.frameDurationsTicks.get(Math.floorMod(index, this.frameDurationsTicks.size())));
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
			int delayTicks,
			String disposalMethod
	) {
	}
}
