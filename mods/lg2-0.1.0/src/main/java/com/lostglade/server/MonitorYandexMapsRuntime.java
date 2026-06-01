package com.lostglade.server;

import com.lostglade.server.monitor.MonitorApp;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.lostglade.server.MonitorScreenSystem.*;

final class MonitorYandexMapsRuntime {
	private static final double DEFAULT_BLOCKS_PER_PIXEL = 2.0D;
	private static final double MIN_BLOCKS_PER_PIXEL = 1.0D / 512.0D;
	private static final double MAX_BLOCKS_PER_PIXEL = 4096.0D;
	private static final double BUTTON_ZOOM_FACTOR = 2.0D;
	private static final double WHEEL_ZOOM_FACTOR = 1.25D;
	private static final long PAN_ANIMATION_MS = 520L;
	private static final int AUTO_REFRESH_TICKS = 20;
	private static final Map<ScreenRuntimeKey, YandexMapState> STATES = new ConcurrentHashMap<>();

	private MonitorYandexMapsRuntime() {
	}

	static YandexMapsVisualSnapshot captureSnapshot(MinecraftServer server, ScreenComponent component) {
		if (server == null || component == null) {
			return emptySnapshot();
		}
		YandexMapState state = STATES.computeIfAbsent(component.runtimeKey(), ignored -> new YandexMapState());
		double centerX;
		double centerZ;
		double blocksPerPixel;
		long version;
		String status;
		long now = System.currentTimeMillis();
		synchronized (state) {
			initializeStateLocked(state, component);
			if (applyPanAnimationLocked(state, now)) {
				state.version++;
			}
			centerX = state.centerX;
			centerZ = state.centerZ;
			blocksPerPixel = state.blocksPerPixel;
			version = state.version;
			status = state.streamStatus;
		}
		ServerLevel level = server.getLevel(component.runtimeKey().dimension());
		if (level == null) {
			return new YandexMapsVisualSnapshot(
					version,
					fallbackMapFrame(component, "Мир недоступен"),
					"Мир недоступен",
					dimensionLabel(component.runtimeKey().dimension()),
					(int) Math.round(centerX),
					(int) Math.round(centerZ),
					blocksPerPixel,
					false
			);
		}
		return new YandexMapsVisualSnapshot(
				version,
				null,
				status == null || status.isBlank() ? "BlueMap top-down" : status,
				dimensionLabel(level.dimension()),
				(int) Math.round(centerX),
				(int) Math.round(centerZ),
				blocksPerPixel,
				true
		);
	}

	static void drawScreen(Graphics2D graphics, UiLayout layout, MonitorApp app, YandexMapsVisualSnapshot snapshot, MinecraftServer server, ScreenRuntimeKey runtimeKey) {
		if (graphics == null || layout == null) {
			return;
		}
		UiRect canvas = mediaCanvasRect(layout);
		YandexMapsVisualSnapshot effectiveSnapshot = snapshot;
		BufferedImage frame = snapshot != null ? snapshot.frame() : null;
		if (frame == null && snapshot != null && server != null && runtimeKey != null) {
			ServerLevel level = server.getLevel(runtimeKey.dimension());
			if (level != null) {
				MonitorYandexMapsBlueMapRenderer.Frame rendered = MonitorYandexMapsBlueMapRenderer.render(
						server,
						level,
						snapshot.centerX(),
						snapshot.centerZ(),
						Math.max(1, layout.canvasWidth()),
						Math.max(1, layout.canvasHeight()),
						snapshot.zoomBlocks()
				);
				frame = rendered.image();
				effectiveSnapshot = new YandexMapsVisualSnapshot(
						snapshot.version(),
						frame,
						rendered.status(),
						snapshot.dimensionLabel(),
						snapshot.centerX(),
						snapshot.centerZ(),
						snapshot.zoomBlocks(),
						rendered.healthy()
				);
			}
		}
		if (frame != null) {
			graphics.drawImage(frame, canvas.x(), canvas.y(), canvas.width(), canvas.height(), null);
		} else {
			graphics.setPaint(new GradientPaint(canvas.x(), canvas.y(), new Color(0x182026), canvas.right(), canvas.bottom(), new Color(0x071014)));
			graphics.fillRect(canvas.x(), canvas.y(), canvas.width(), canvas.height());
			String statusText = effectiveSnapshot != null && effectiveSnapshot.statusText() != null && !effectiveSnapshot.statusText().isBlank()
					? effectiveSnapshot.statusText()
					: "BlueMap renderer недоступен";
			drawCenteredText(graphics, statusText, canvas, new Color(230, 238, 244), Font.BOLD, Math.max(12, Math.min(canvas.width(), canvas.height()) / 12));
		}
		drawMapHeader(graphics, layout, app, effectiveSnapshot);
		drawZoomControls(graphics, layout);
		drawCenterReticle(graphics, layout);
	}

	static boolean handleTouch(ServerPlayer player, ServerLevel level, ScreenComponent component, UiLayout layout, UiPoint touchPoint) {
		if (level == null || component == null || layout == null || touchPoint == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return false;
		}
		if (mediaCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			applyTransientComponentViewState(server, level, component, ScreenViewMode.HOME, component.launcherPage());
			return true;
		}
		YandexMapState state = STATES.computeIfAbsent(component.runtimeKey(), ignored -> new YandexMapState());
		if (yandexZoomInRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				initializeStateLocked(state, component);
				zoomLocked(state, 1.0D / BUTTON_ZOOM_FACTOR);
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (yandexZoomOutRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				initializeStateLocked(state, component);
				zoomLocked(state, BUTTON_ZOOM_FACTOR);
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (yandexGeoRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				initializeStateLocked(state, component);
				applyPanAnimationLocked(state, System.currentTimeMillis());
				beginPanLocked(state, player.getX(), player.getZ(), System.currentTimeMillis());
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (yandexWorldCenterRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				initializeStateLocked(state, component);
				applyPanAnimationLocked(state, System.currentTimeMillis());
				beginPanLocked(state, 0.0D, 0.0D, System.currentTimeMillis());
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		UiRect canvas = mediaCanvasRect(layout);
		if (canvas.contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				initializeStateLocked(state, component);
				applyPanAnimationLocked(state, System.currentTimeMillis());
				double zoom = state.blocksPerPixel;
				double nextX = state.centerX + (touchPoint.x() - canvas.x() - canvas.width() / 2.0D) * zoom;
				double nextZ = state.centerZ + (touchPoint.y() - canvas.y() - canvas.height() / 2.0D) * zoom;
				beginPanLocked(state, nextX, nextZ, System.currentTimeMillis());
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		return true;
	}

	static void tick(MinecraftServer server) {
		if (server == null || STATES.isEmpty()) {
			return;
		}
		int tick = server.getTickCount();
		for (ScreenRuntimeKey key : List.copyOf(STATES.keySet())) {
			ScreenComponent component = resolveScreenComponent(server, key);
			if (component == null || component.viewMode() != ScreenViewMode.YANDEX_MAPS || !component.powered()) {
				if (component == null) {
					STATES.remove(key);
				}
				continue;
			}
			ServerLevel level = server.getLevel(key.dimension());
			if (level == null || !hasNearbyMediaViewer(level, component)) {
				continue;
			}
			YandexMapState state = STATES.get(key);
			boolean animating;
			if (state == null) {
				animating = false;
			} else {
				synchronized (state) {
					animating = state.panStartedAtMillis > 0L;
				}
			}
			if (animating || tick % AUTO_REFRESH_TICKS == 0) {
				requestRuntimeRender(server, key);
			}
		}
	}

	static boolean onPlayerHotbarScroll(ServerPlayer player, int previousSlot, int currentSlot) {
		if (player == null) {
			return false;
		}
		ScreenComponent component = findObservedScrollableComponent(player);
		if (component == null || component.viewMode() != ScreenViewMode.YANDEX_MAPS) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		int delta = normalizeHotbarDelta(previousSlot, currentSlot);
		if (delta == 0) {
			return false;
		}
		YandexMapState state = STATES.computeIfAbsent(component.runtimeKey(), ignored -> new YandexMapState());
		synchronized (state) {
			initializeStateLocked(state, component);
			zoomLocked(state, Math.pow(WHEEL_ZOOM_FACTOR, -delta));
		}
		requestRuntimeRender(server, component.runtimeKey());
		return true;
	}

	private static void drawMapHeader(Graphics2D graphics, UiLayout layout, MonitorApp app, YandexMapsVisualSnapshot snapshot) {
		UiRect canvas = mediaCanvasRect(layout);
		UiRect header = new UiRect(
				canvas.x() + clampInt(layout.unit() / 2, 4, 10),
				canvas.y() + clampInt(layout.unit() / 2, 4, 10),
				Math.min(canvas.width() - clampInt(layout.unit(), 8, 20), clampInt(layout.unit() * 24, 168, 310)),
				clampInt(layout.unit() * 3, 30, 46)
		);
		fillRoundedRect(graphics, header, header.height(), new Color(250, 252, 248, 226));
		strokeRoundedRect(graphics, header, header.height(), 1.0F, new Color(0, 0, 0, 36));
		UiRect iconRect = new UiRect(header.x() + 4, header.y() + 4, header.height() - 8, header.height() - 8);
		drawAppIcon(graphics, app, iconRect, 0);
		String coords = snapshot != null
				? snapshot.centerX() + ", " + snapshot.centerZ() + "  |  " + formatZoom(snapshot.zoomBlocks())
				: "Карта загружается";
		drawVerticalText(graphics, "Яндекс Карты", new UiRect(iconRect.right() + 6, header.y() + 1, header.right() - iconRect.right() - 10, header.height() / 2), new Color(30, 34, 36), Font.BOLD, clampInt(layout.unit() - 1, 9, 13));
		drawVerticalText(graphics, coords, new UiRect(iconRect.right() + 6, header.y() + header.height() / 2 - 2, header.right() - iconRect.right() - 10, header.height() / 2), new Color(78, 86, 92), Font.PLAIN, clampInt(layout.unit() - 3, 7, 10));
		drawMediaCloseButton(graphics, mediaCloseRect(layout), layout);
		UiRect status = new UiRect(canvas.right() - Math.min(canvas.width() / 2, clampInt(layout.unit() * 20, 120, 260)) - clampInt(layout.unit() / 2, 4, 10), header.y(), Math.min(canvas.width() / 2, clampInt(layout.unit() * 20, 120, 260)), header.height());
		if (snapshot != null && snapshot.statusText() != null && !snapshot.statusText().isBlank() && status.x() > header.right() + 4) {
			fillRoundedRect(graphics, status, status.height(), new Color(8, 14, 18, 128));
			drawCenteredTextFitted(graphics, snapshot.statusText(), status.inset(4), new Color(244, 250, 255, 230), Font.BOLD, clampInt(layout.unit() - 2, 7, 11), 6);
		}
	}

	private static void drawZoomControls(Graphics2D graphics, UiLayout layout) {
		drawZoomButton(graphics, yandexZoomInRect(layout), "+", layout);
		drawZoomButton(graphics, yandexZoomOutRect(layout), "-", layout);
		drawMapIconButton(graphics, yandexGeoRect(layout), PlayerUiIcon.LOCATION, layout);
		drawMapIconButton(graphics, yandexWorldCenterRect(layout), PlayerUiIcon.TARGET, layout);
	}

	private static void drawZoomButton(Graphics2D graphics, UiRect rect, String label, UiLayout layout) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit(), 8, 14), new Color(250, 252, 248, 232));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit(), 8, 14), 1.0F, new Color(0, 0, 0, 42));
		drawCenteredTextFitted(graphics, label, rect, new Color(20, 24, 26), Font.BOLD, clampInt(layout.unit() * 2, 16, 28), 10);
	}

	private static void drawMapIconButton(Graphics2D graphics, UiRect rect, PlayerUiIcon icon, UiLayout layout) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit(), 8, 14), new Color(250, 252, 248, 232));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit(), 8, 14), 1.0F, new Color(0, 0, 0, 42));
		int inset = clampInt(rect.width() / 5, 5, 9);
		drawPlayerUiIcon(graphics, rect.inset(inset), icon, new Color(20, 24, 26, 238));
	}

	private static void drawCenterReticle(Graphics2D graphics, UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int x = canvas.x() + canvas.width() / 2;
		int y = canvas.y() + canvas.height() / 2;
		Stroke previous = graphics.getStroke();
		graphics.setStroke(new BasicStroke(1.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(new Color(255, 65, 52, 208));
		graphics.drawLine(x - 7, y, x - 2, y);
		graphics.drawLine(x + 2, y, x + 7, y);
		graphics.drawLine(x, y - 7, x, y - 2);
		graphics.drawLine(x, y + 2, x, y + 7);
		graphics.setStroke(previous);
	}

	private static UiRect yandexZoomInRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int size = clampInt(layout.unit() * 3, 28, 42);
		int gap = Math.max(2, layout.unit() / 3);
		int inset = clampInt(layout.unit(), 8, 16);
		return new UiRect(canvas.x() + inset, canvas.bottom() - inset - size * 2 - gap, size, size);
	}

	private static UiRect yandexZoomOutRect(UiLayout layout) {
		UiRect plus = yandexZoomInRect(layout);
		return new UiRect(plus.x(), plus.bottom() + Math.max(2, layout.unit() / 3), plus.width(), plus.height());
	}

	private static UiRect yandexGeoRect(UiLayout layout) {
		UiRect plus = yandexZoomInRect(layout);
		int gap = Math.max(2, layout.unit() / 3);
		return new UiRect(plus.right() + gap, plus.y(), plus.width(), plus.height());
	}

	private static UiRect yandexWorldCenterRect(UiLayout layout) {
		UiRect minus = yandexZoomOutRect(layout);
		int gap = Math.max(2, layout.unit() / 3);
		return new UiRect(minus.right() + gap, minus.y(), minus.width(), minus.height());
	}

	private static String formatZoom(double blocksPerPixel) {
		double zoom = clampDouble(blocksPerPixel, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
		if (zoom < 1.0D) {
			return "x" + Math.max(1, Math.round(1.0D / zoom));
		}
		if (Math.abs(zoom - Math.rint(zoom)) < 0.05D) {
			return Math.round(zoom) + " бл/px";
		}
		return String.format(Locale.ROOT, "%.1f бл/px", zoom);
	}

	private static void initializeStateLocked(YandexMapState state, ScreenComponent component) {
		if (state.initialized || component == null || component.runtimeKey() == null) {
			return;
		}
		BlockPos pos = component.runtimeKey().pos();
		state.centerX = pos.getX() + 0.5D;
		state.centerZ = pos.getZ() + 0.5D;
		state.targetX = state.centerX;
		state.targetZ = state.centerZ;
		state.blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
		state.streamStatus = "Ожидание карты";
		state.initialized = true;
		state.version++;
	}

	private static void zoomLocked(YandexMapState state, double factor) {
		if (state == null || !Double.isFinite(factor) || factor <= 0.0D) {
			return;
		}
		double next = clampDouble(state.blocksPerPixel * factor, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
		if (Math.abs(next - state.blocksPerPixel) <= 0.001D) {
			return;
		}
		state.blocksPerPixel = next;
		state.streamStatus = "Карта online";
		state.version++;
	}

	private static void beginPanLocked(YandexMapState state, double targetX, double targetZ, long now) {
		if (state == null || !Double.isFinite(targetX) || !Double.isFinite(targetZ)) {
			return;
		}
		state.panStartX = state.centerX;
		state.panStartZ = state.centerZ;
		state.targetX = targetX;
		state.targetZ = targetZ;
		state.panStartedAtMillis = now;
		state.streamStatus = "Карта online";
		state.version++;
	}

	private static boolean applyPanAnimationLocked(YandexMapState state, long now) {
		if (state == null || state.panStartedAtMillis <= 0L) {
			return false;
		}
		double previousX = state.centerX;
		double previousZ = state.centerZ;
		long elapsed = Math.max(0L, now - state.panStartedAtMillis);
		if (elapsed >= PAN_ANIMATION_MS) {
			state.centerX = state.targetX;
			state.centerZ = state.targetZ;
			state.panStartedAtMillis = 0L;
		} else {
			double t = elapsed / (double) PAN_ANIMATION_MS;
			double eased = t * t * (3.0D - 2.0D * t);
			state.centerX = lerp(state.panStartX, state.targetX, eased);
			state.centerZ = lerp(state.panStartZ, state.targetZ, eased);
		}
		return Math.abs(previousX - state.centerX) > 0.01D || Math.abs(previousZ - state.centerZ) > 0.01D;
	}

	private static double lerp(double from, double to, double amount) {
		return from + (to - from) * clampDouble(amount, 0.0D, 1.0D);
	}

	private static String dimensionLabel(ResourceKey<Level> dimension) {
		if (dimension == null || dimension.identifier() == null) {
			return "unknown";
		}
		String path = dimension.identifier().getPath();
		if ("overworld".equals(path)) {
			return "overworld";
		}
		if ("the_nether".equals(path)) {
			return "nether";
		}
		if ("the_end".equals(path)) {
			return "end";
		}
		return path.toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private static BufferedImage fallbackMapFrame(ScreenComponent component, String text) {
		int width = Math.max(1, component != null ? component.width() * MAP_SIZE : MAP_SIZE);
		int height = Math.max(1, component != null ? component.height() * MAP_SIZE : MAP_SIZE);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		configureMapGraphics(graphics);
		graphics.setPaint(new GradientPaint(0, 0, new Color(0x172027), width, height, new Color(0x061016)));
		graphics.fillRect(0, 0, width, height);
		drawCenteredText(graphics, text, new UiRect(0, 0, width, height), new Color(230, 238, 244), Font.BOLD, Math.max(12, Math.min(width, height) / 12));
		graphics.dispose();
		return image;
	}

	private static void configureMapGraphics(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private static YandexMapsVisualSnapshot emptySnapshot() {
		return new YandexMapsVisualSnapshot(0L, null, "", "", 0, 0, DEFAULT_BLOCKS_PER_PIXEL, false);
	}

	private static final class YandexMapState {
		private boolean initialized;
		private long version;
		private double centerX;
		private double centerZ;
		private double targetX;
		private double targetZ;
		private double panStartX;
		private double panStartZ;
		private long panStartedAtMillis;
		private double blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
		private String streamStatus = "Ожидание карты";
	}
}
