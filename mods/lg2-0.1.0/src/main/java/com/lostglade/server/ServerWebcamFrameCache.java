package com.lostglade.server;

import com.lostglade.Lg2;
import ru.dimaskama.javah264.DecodeResult;
import ru.dimaskama.javah264.H264Decoder;
import ru.dimaskama.webcam.Webcam;
import ru.dimaskama.webcam.client.VideoPacketBuffer;
import ru.dimaskama.webcam.config.ServerConfig;
import ru.dimaskama.webcam.config.VideoDisplayShape;
import ru.dimaskama.webcam.net.packet.VideoC2SPacket;
import ru.dimaskama.webcam.server.PlayerState;
import ru.dimaskama.webcam.server.WebcamServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerWebcamFrameCache {
	private static final long FRAME_TTL_MILLIS = 5_000L;
	private static final int PACKET_BUFFER_SIZE = 64;
	private static final int DISPLAY_IMAGE_SIZE = 128;
	private static final Map<UUID, WebcamStreamState> STREAMS = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> MIRROR_TARGET_BY_SOURCE = new ConcurrentHashMap<>();
	private static volatile boolean decoderUnavailable;

	private ServerWebcamFrameCache() {
	}

	public static void handleVideoPacket(UUID sourceId, VideoC2SPacket packet) {
		if (sourceId == null || packet == null || decoderUnavailable) {
			return;
		}
		WebcamStreamState state = STREAMS.get(sourceId);
		if (state == null) {
			WebcamStreamState created = createStreamState();
			if (created == null) {
				return;
			}
			WebcamStreamState existing = STREAMS.putIfAbsent(sourceId, created);
			if (existing != null) {
				created.close();
				state = existing;
			} else {
				state = created;
			}
		}
		state.accept(packet);
	}

	public static void removeSource(UUID sourceId) {
		if (sourceId == null) {
			return;
		}
		WebcamStreamState state = STREAMS.remove(sourceId);
		if (state != null) {
			state.close();
		}
	}

	public static void beginMirror(UUID mirroredSourceId, UUID targetSourceId) {
		if (mirroredSourceId == null || targetSourceId == null || mirroredSourceId.equals(targetSourceId)) {
			return;
		}
		MIRROR_TARGET_BY_SOURCE.put(mirroredSourceId, targetSourceId);
	}

	public static void endMirror(UUID mirroredSourceId) {
		if (mirroredSourceId == null) {
			return;
		}
		MIRROR_TARGET_BY_SOURCE.remove(mirroredSourceId);
	}

	public static void clearMirrors() {
		MIRROR_TARGET_BY_SOURCE.clear();
	}

	public static void clearAll() {
		clearMirrors();
		for (WebcamStreamState state : STREAMS.values()) {
			state.close();
		}
		STREAMS.clear();
	}

	public static WebcamDisplay getAboveHeadDisplay(ServerPlayer viewer, Player player) {
		if (viewer == null || player == null || !ServerWebcamIntegration.isLoaded()) {
			return null;
		}
		ServerConfig config = currentConfig();
		if (config == null || config.displayOnFace()) {
			return null;
		}
		UUID logicalSourceId = player.getUUID();
		if (!canViewerSeeSource(viewer, player, logicalSourceId, config)) {
			return null;
		}
		UUID frameSourceId = MIRROR_TARGET_BY_SOURCE.getOrDefault(logicalSourceId, logicalSourceId);
		WebcamStreamState state = STREAMS.get(frameSourceId);
		if (state == null) {
			return null;
		}
		WebcamFrame frame = state.latestFrame();
		if (frame == null) {
			return null;
		}
		BufferedImage displayImage = shapeDisplayImage(frame.image(), config.displayShape());
		String materialKey = "webcam:" + logicalSourceId + ":" + frame.version() + ":" + config.displayShape().name().toLowerCase(Locale.ROOT);
		return new WebcamDisplay(displayImage, materialKey, config.displayShape(), config.displayOffsetY(), config.displaySize());
	}

	private static boolean canViewerSeeSource(ServerPlayer viewer, Player sourcePlayer, UUID logicalSourceId, ServerConfig config) {
		if (viewer == null || sourcePlayer == null || logicalSourceId == null || config == null) {
			return false;
		}
		double maxDistance = config.maxDisplayDistance();
		boolean withinPhysicalDistance = viewer.position().distanceToSqr(sourcePlayer.position()) <= maxDistance * maxDistance;
		if (!withinPhysicalDistance && !RendererBotCameraSystem.shouldReceiveNearbyWebcam(viewer, sourcePlayer, maxDistance)) {
			return false;
		}
		WebcamServer webcamServer = WebcamServer.getInstance();
		if (webcamServer == null) {
			return false;
		}
		PlayerState viewerState = webcamServer.getPlayerState(viewer.getUUID());
		return viewerState != null
				&& viewerState.isAuthenticated()
				&& viewerState.hasViewPermission()
				&& viewerState.canShowWebcams()
				&& (config.displaySelfWebcam() || !viewer.getUUID().equals(logicalSourceId))
				&& !viewerState.isSourceBlocked(logicalSourceId);
	}

	private static BufferedImage shapeDisplayImage(BufferedImage sourceImage, VideoDisplayShape shape) {
		BufferedImage result = new BufferedImage(DISPLAY_IMAGE_SIZE, DISPLAY_IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = result.createGraphics();
		try {
			graphics.setComposite(AlphaComposite.Clear);
			graphics.fillRect(0, 0, DISPLAY_IMAGE_SIZE, DISPLAY_IMAGE_SIZE);
			graphics.setComposite(AlphaComposite.Src);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			if (shape == VideoDisplayShape.ROUND) {
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				graphics.setClip(new Ellipse2D.Float(0.0F, 0.0F, DISPLAY_IMAGE_SIZE, DISPLAY_IMAGE_SIZE));
			}
			graphics.drawImage(sourceImage, 0, 0, DISPLAY_IMAGE_SIZE, DISPLAY_IMAGE_SIZE, null);
		} finally {
			graphics.dispose();
		}
		return result;
	}

	private static WebcamStreamState createStreamState() {
		try {
			return new WebcamStreamState();
		} catch (Throwable throwable) {
			decoderUnavailable = true;
			Lg2.LOGGER.warn("Failed to initialize server-side webcam decoder for camera photos", throwable);
			return null;
		}
	}

	private static ServerConfig currentConfig() {
		if (Webcam.getServerConfig() == null) {
			return null;
		}
		Object data = Webcam.getServerConfig().getData();
		return data instanceof ServerConfig config ? config : null;
	}

	private static BufferedImage decodeImage(DecodeResult result) {
		BufferedImage image = new BufferedImage(result.getWidth(), result.getHeight(), BufferedImage.TYPE_INT_ARGB);
		byte[] rgba = result.getImage();
		int width = result.getWidth();
		int height = result.getHeight();
		int[] pixels = new int[width * height];
		for (int i = 0, pixel = 0; pixel < pixels.length && i + 3 < rgba.length; i += 4, pixel++) {
			int red = rgba[i] & 0xFF;
			int green = rgba[i + 1] & 0xFF;
			int blue = rgba[i + 2] & 0xFF;
			int alpha = rgba[i + 3] & 0xFF;
			pixels[pixel] = (alpha << 24) | (red << 16) | (green << 8) | blue;
		}
		image.setRGB(0, 0, width, height, pixels, 0, width);
		return image;
	}

	public record WebcamDisplay(
			BufferedImage image,
			String materialKey,
			VideoDisplayShape shape,
			float offsetY,
			float size
	) {
	}

	private record WebcamFrame(
			BufferedImage image,
			long version,
			long updatedAtMillis
	) {
	}

	private static final class WebcamStreamState implements AutoCloseable {
		private final H264Decoder decoder;
		private final VideoPacketBuffer buffer;
		private volatile WebcamFrame latestFrame;
		private long versionCounter;

		private WebcamStreamState() throws Exception {
			this.decoder = H264Decoder.builder().flushBehavior(H264Decoder.FlushBehavior.NoFlush).build();
			this.buffer = new VideoPacketBuffer(PACKET_BUFFER_SIZE, this::acceptPacketBytes);
		}

		private synchronized void accept(VideoC2SPacket packet) {
			if (packet == null || packet.nal() == null || packet.nal().data() == null) {
				return;
			}
			this.buffer.receivePacket(packet.nal().sequenceNumber(), packet.nal().data());
		}

		private synchronized WebcamFrame latestFrame() {
			WebcamFrame frame = this.latestFrame;
			if (frame == null) {
				return null;
			}
			return System.currentTimeMillis() - frame.updatedAtMillis() <= FRAME_TTL_MILLIS ? frame : null;
		}

		private void acceptPacketBytes(byte[] data) {
			if (data == null || data.length == 0) {
				return;
			}
			DecodeResult result;
			synchronized (this.decoder) {
				result = this.decoder.decodeRGBA(data);
			}
			if (result == null || result.getWidth() <= 0 || result.getHeight() <= 0 || result.getImage() == null) {
				return;
			}
			BufferedImage image = decodeImage(result);
			synchronized (this) {
				this.latestFrame = new WebcamFrame(image, ++this.versionCounter, System.currentTimeMillis());
			}
		}

		@Override
		public synchronized void close() {
			this.latestFrame = null;
			this.decoder.close();
		}
	}
}
