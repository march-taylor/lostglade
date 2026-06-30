package com.lostglade.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lostglade.server.map.TextureAssetManager;
import com.lostglade.server.monitor.MonitorApp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.lostglade.server.MonitorScreenSystem.*;

final class MonitorYandexMapsRuntime {
	private static final double DEFAULT_BLOCKS_PER_PIXEL = 2.0D;
	private static final double MIN_BLOCKS_PER_PIXEL = 1.0D / 512.0D;
	private static final double MAX_BLOCKS_PER_PIXEL = 4096.0D;
	private static final double BUTTON_ZOOM_FACTOR = 2.0D;
	private static final double WHEEL_ZOOM_FACTOR = 1.25D;
	private static final long PAN_ANIMATION_MS = 320L;
	private static final long PAN_FRAME_DELAY_MS = 40L;
	private static final long TILE_READY_RENDER_DEBOUNCE_MS = 35L;
	private static final long DISPLAY_OVERLAY_CACHE_MS = 750L;
	private static final int DISPLAY_OVERLAY_CAPTURE_PADDING_PX = MAP_SIZE;
	private static final int MAP_MARKER_ICON_SIZE = 32;
	private static final int STATE_CLEANUP_INTERVAL_TICKS = 40;
	private static final Map<ScreenRuntimeKey, YandexMapState> STATES = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> ITEM_MARKER_ICON_CACHE = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingMarkerTitleRequest> PENDING_MARKER_TITLES = new ConcurrentHashMap<>();
	private static final TextureAssetManager MAP_ASSETS = TextureAssetManager.get();
	private static final BufferedImage EMPTY_MARKER_ICON = new BufferedImage(MAP_MARKER_ICON_SIZE, MAP_MARKER_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);

	private MonitorYandexMapsRuntime() {
	}

	static void clearRuntime() {
		STATES.clear();
		PENDING_MARKER_TITLES.clear();
		MonitorYandexMapsBlueMapRenderer.clear(null);
	}

	static void deactivateRuntime(ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		STATES.remove(key);
		clearPendingMarkerTitleRequests(key);
	}

	static boolean consumeMarkerTitleChatMessage(MinecraftServer server, PlayerChatMessage message, ServerPlayer sender) {
		if (server == null || message == null || sender == null) {
			return false;
		}
		PendingMarkerTitleRequest pending = PENDING_MARKER_TITLES.remove(sender.getUUID());
		if (pending == null) {
			return false;
		}
		UUID markerId = pending.markerId();
		ScreenRuntimeKey runtimeKey = pending.screenKey();
		YandexMapMarkerStore.YandexMapMarker marker = markerId == null ? null : YandexMapMarkerStore.marker(markerId);
		if (runtimeKey == null || marker == null) {
			sender.displayClientMessage(Component.empty(), true);
			return true;
		}
		String nextTitle = sanitizeMarkerTitle(message.signedContent(), sender);
		YandexMapMarkerStore.YandexMapMarker updated = YandexMapMarkerStore.updateTitle(server, markerId, nextTitle);
		if (updated == null) {
			sender.displayClientMessage(Component.empty(), true);
			return true;
		}
		YandexMapState state = STATES.get(runtimeKey);
		if (state != null) {
			synchronized (state) {
				if (Objects.equals(state.editorMarkerId, markerId)) {
					state.version++;
				}
			}
		}
		requestRuntimeRender(server, runtimeKey);
		sender.displayClientMessage(Component.empty(), true);
		return true;
	}

	private static void requestMarkerTitlePrompt(ServerPlayer player, ScreenRuntimeKey key, UUID markerId) {
		if (player == null || key == null || markerId == null) {
			return;
		}
		PENDING_MEDIA_LINKS.remove(player.getUUID());
		PENDING_GALLERY_RENAMES.remove(player.getUUID());
		PENDING_MARKER_TITLES.put(player.getUUID(), new PendingMarkerTitleRequest(key, markerId));
		player.displayClientMessage(Component.literal(markerTitlePromptMessage(player)), true);
	}

	private static void clearPendingMarkerTitleRequests(ScreenRuntimeKey key) {
		if (key == null || PENDING_MARKER_TITLES.isEmpty()) {
			return;
		}
		PENDING_MARKER_TITLES.entrySet().removeIf(entry -> Objects.equals(entry.getValue().screenKey(), key));
	}

	private static String markerTitlePromptMessage(ServerPlayer player) {
		String locale = MonitorScreenMessages.locale(player);
		if (locale.startsWith("uk")) {
			return "Я.Карти: напиши назву мітки в чат";
		}
		if (locale.startsWith("ja")) {
			return "ヤンデックス地図: マーカー名をチャットに入力して";
		}
		if (locale.startsWith("ru")) {
			return "Я.Карты: напиши название метки в чат";
		}
		return "Yandex Maps: type the marker title in chat";
	}

	private static String defaultMarkerTitle(ServerPlayer player) {
		String locale = MonitorScreenMessages.locale(player);
		if (locale.startsWith("uk")) {
			return "Нова мітка";
		}
		if (locale.startsWith("ja")) {
			return "新しいマーカー";
		}
		if (locale.startsWith("ru")) {
			return "Новая метка";
		}
		return "New marker";
	}

	private static String sanitizeMarkerTitle(String rawTitle, ServerPlayer player) {
		if (rawTitle == null) {
			return defaultMarkerTitle(player);
		}
		String normalized = rawTitle.trim().replaceAll("\\s+", " ");
		if (normalized.isEmpty()) {
			return defaultMarkerTitle(player);
		}
		if (normalized.length() <= 64) {
			return normalized;
		}
		String truncated = normalized.substring(0, 64).trim();
		return truncated.isEmpty() ? defaultMarkerTitle(player) : truncated;
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
					centerX,
					centerZ,
					blocksPerPixel,
					List.of(),
					false
			);
		}
		List<MonitorYandexMapsBlueMapRenderer.DisplayOverlay> displayOverlays = captureDisplayOverlays(
				component,
				state,
				level,
				centerX,
				centerZ,
				blocksPerPixel,
				now
		);
		return new YandexMapsVisualSnapshot(
				version,
				null,
				status == null || status.isBlank() ? "BlueMap top-down" : status,
				dimensionLabel(level.dimension()),
				centerX,
				centerZ,
				blocksPerPixel,
				displayOverlays,
				true
		);
	}

	static boolean beginRender(ScreenRuntimeKey key, YandexMapsVisualSnapshot snapshot) {
		if (key == null || snapshot == null) {
			return true;
		}
		YandexMapState state = STATES.computeIfAbsent(key, ignored -> new YandexMapState());
		synchronized (state) {
			if (state.activeRenderJobs > 0) {
				state.rerenderRequested = true;
				return false;
			}
			state.activeRenderJobs = 1;
			state.rerenderRequested = false;
			return true;
		}
	}

	static boolean acceptRenderedSnapshot(ScreenRuntimeKey key, YandexMapsVisualSnapshot snapshot) {
		if (key == null || snapshot == null) {
			return true;
		}
		YandexMapState state = STATES.get(key);
		if (state == null) {
			return true;
		}
		synchronized (state) {
			if (snapshot.version() < state.lastAppliedRenderVersion) {
				return false;
			}
			state.lastAppliedRenderVersion = snapshot.version();
			return true;
		}
	}

	static boolean finishRender(ScreenRuntimeKey key, YandexMapsVisualSnapshot snapshot) {
		if (key == null || snapshot == null) {
			return false;
		}
		YandexMapState state = STATES.get(key);
		if (state == null) {
			return false;
		}
		synchronized (state) {
			state.activeRenderJobs = 0;
			boolean rerender = state.rerenderRequested || state.version != snapshot.version();
			state.rerenderRequested = false;
			return rerender;
		}
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
						snapshot.zoomBlocks(),
						snapshot.displayOverlays(),
						() -> notifyTileReady(server, runtimeKey)
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
						snapshot.displayOverlays(),
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
		drawMarkers(graphics, layout, effectiveSnapshot, runtimeKey);
		drawMapHeader(graphics, layout, app, effectiveSnapshot);
		drawZoomControls(graphics, layout);
		drawCenterReticle(graphics, layout);
		drawMarkerEditor(graphics, layout, runtimeKey);
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
			deactivateRuntime(component.runtimeKey());
			applyTransientComponentViewState(server, level, component, ScreenViewMode.HOME, component.launcherPage());
			return true;
		}
		YandexMapState state = STATES.computeIfAbsent(component.runtimeKey(), ignored -> new YandexMapState());
		if (handleMarkerEditorTouch(player, server, component, layout, touchPoint, state)) {
			return true;
		}
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
				BlockPos screenPos = component.runtimeKey().pos();
				beginPanLocked(state, screenPos.getX() + 0.5D, screenPos.getZ() + 0.5D, System.currentTimeMillis());
			}
			requestRuntimeRender(server, component.runtimeKey());
			schedulePanAnimationFrame(server, component.runtimeKey());
			return true;
		}
		if (yandexWorldCenterRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				initializeStateLocked(state, component);
				applyPanAnimationLocked(state, System.currentTimeMillis());
				beginPanLocked(state, 0.0D, 0.0D, System.currentTimeMillis());
			}
			requestRuntimeRender(server, component.runtimeKey());
			schedulePanAnimationFrame(server, component.runtimeKey());
			return true;
		}
		if (yandexAddMarkerRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				initializeStateLocked(state, component);
				state.editorMarkerId = createMarkerAtCenter(level, player, state);
				state.version++;
			}
			clearPendingMarkerTitleRequests(component.runtimeKey());
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		ProjectedMarker tappedMarker;
		synchronized (state) {
			initializeStateLocked(state, component);
			tappedMarker = markerAtTouch(
					component.runtimeKey().dimension(),
					layout,
					state.centerX,
					state.centerZ,
					state.blocksPerPixel,
					state.editorMarkerId,
					touchPoint
			);
		}
		if (tappedMarker != null) {
			synchronized (state) {
				state.editorMarkerId = tappedMarker.markerId();
				state.version++;
			}
			clearPendingMarkerTitleRequests(component.runtimeKey());
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
			schedulePanAnimationFrame(server, component.runtimeKey());
			return true;
		}
		return true;
	}

	static void tick(MinecraftServer server) {
		if (server == null || STATES.isEmpty()) {
			return;
		}
		if (Math.floorMod(server.getTickCount(), STATE_CLEANUP_INTERVAL_TICKS) != 0) {
			return;
		}
		for (ScreenRuntimeKey key : List.copyOf(STATES.keySet())) {
			ScreenComponent component = resolveScreenComponent(server, key);
			if (component == null || component.viewMode() != ScreenViewMode.YANDEX_MAPS || !component.powered()) {
				deactivateRuntime(key);
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

	static void notifyTileReady(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		YandexMapState state = STATES.get(key);
		if (state == null) {
			requestRuntimeRender(server, key);
			return;
		}
		synchronized (state) {
			if (state.tileReadyRenderScheduled) {
				return;
			}
			state.tileReadyRenderScheduled = true;
		}
		scheduleRuntimeCallback(server, TILE_READY_RENDER_DEBOUNCE_MS, () -> {
			YandexMapState current = STATES.get(key);
			if (current != null) {
				synchronized (current) {
					current.tileReadyRenderScheduled = false;
				}
			}
			requestRuntimeRender(server, key);
		});
	}

	private static void drawMapHeader(Graphics2D graphics, UiLayout layout, MonitorApp app, YandexMapsVisualSnapshot snapshot) {
		UiRect canvas = mediaCanvasRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit() / 2, 4, 10);
		int height = ultra ? clampInt(layout.unit() * 2 + 4, 12, 16) : clampInt(layout.unit() * 3, 30, 46);
		int headerX = ultra ? mediaCloseRect(layout).right() + mediaHeaderControlGap(layout) : canvas.x() + inset;
		UiRect header = new UiRect(
				headerX,
				canvas.y() + inset,
				Math.min(canvas.right() - headerX - inset, ultra ? clampInt(layout.unit() * 24, 88, 124) : clampInt(layout.unit() * 24, 168, 310)),
				height
		);
		fillRoundedRect(graphics, header, header.height(), new Color(250, 252, 248, 226));
		strokeRoundedRect(graphics, header, header.height(), 1.0F, new Color(0, 0, 0, 36));
		int iconInset = ultra ? Math.max(2, header.height() / 7) : 4;
		UiRect iconRect = new UiRect(header.x() + iconInset, header.y() + iconInset, header.height() - iconInset * 2, header.height() - iconInset * 2);
		drawAppIcon(graphics, app, iconRect, 0);
		String coords = snapshot != null
				? Math.round(snapshot.centerX()) + ", " + Math.round(snapshot.centerZ()) + "  |  " + formatZoom(snapshot.zoomBlocks())
				: "Карта загружается";
		if (ultra) {
			drawVerticalText(graphics, coords, new UiRect(iconRect.right() + 3, header.y(), Math.max(8, header.right() - iconRect.right() - 6), header.height()), new Color(58, 64, 68), Font.BOLD, clampInt(layout.unit() + 1, 6, 8));
		} else {
			drawVerticalText(graphics, "Яндекс Карты", new UiRect(iconRect.right() + 6, header.y() + 1, header.right() - iconRect.right() - 10, header.height() / 2), new Color(30, 34, 36), Font.BOLD, clampInt(layout.unit() - 1, 9, 13));
			drawVerticalText(graphics, coords, new UiRect(iconRect.right() + 6, header.y() + header.height() / 2 - 2, header.right() - iconRect.right() - 10, header.height() / 2), new Color(78, 86, 92), Font.PLAIN, clampInt(layout.unit() - 3, 7, 10));
		}
		drawMediaCloseButton(graphics, mediaCloseRect(layout), layout);
	}

	private static void drawZoomControls(Graphics2D graphics, UiLayout layout) {
		drawMapIconButton(graphics, yandexZoomInRect(layout), PlayerUiIcon.ADD, layout);
		drawMapIconButton(graphics, yandexZoomOutRect(layout), PlayerUiIcon.MINUS, layout);
		drawMapIconButton(graphics, yandexGeoRect(layout), PlayerUiIcon.AIMING_2, layout);
		drawMapIconButton(graphics, yandexWorldCenterRect(layout), PlayerUiIcon.LOCATION, layout);
		drawMapIconButton(graphics, yandexAddMarkerRect(layout), PlayerUiIcon.DIRECTIONS_2_LINE, layout);
	}

	private static void drawMapIconButton(Graphics2D graphics, UiRect rect, PlayerUiIcon icon, UiLayout layout) {
		boolean ultra = ultraCompactScreenLayout(layout);
		fillRoundedRect(graphics, rect, ultra ? clampInt(layout.unit(), 5, 8) : clampInt(layout.unit(), 8, 14), new Color(250, 252, 248, 232));
		strokeRoundedRect(graphics, rect, ultra ? clampInt(layout.unit(), 5, 8) : clampInt(layout.unit(), 8, 14), ultra ? 0.85F : 1.0F, new Color(0, 0, 0, 42));
		int inset = ultra ? clampInt(rect.width() / 5, 2, 3) : clampInt(rect.width() / 5, 5, 9);
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
		UiRect minus = yandexZoomOutRect(layout);
		int gap = ultraCompactScreenLayout(layout) ? Math.max(1, layout.unit() / 5) : Math.max(2, layout.unit() / 3);
		return new UiRect(minus.x(), minus.y() - gap - minus.height(), minus.width(), minus.height());
	}

	private static UiRect yandexZoomOutRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2 + 4, 12, 16) : clampInt(layout.unit() * 3, 28, 42);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		return new UiRect(canvas.right() - inset - size, canvas.bottom() - inset - size, size, size);
	}

	private static UiRect yandexGeoRect(UiLayout layout) {
		UiRect plus = yandexZoomInRect(layout);
		int gap = ultraCompactScreenLayout(layout) ? Math.max(1, layout.unit() / 5) : Math.max(2, layout.unit() / 3);
		return new UiRect(plus.x(), plus.y() - gap - plus.height(), plus.width(), plus.height());
	}

	private static UiRect yandexWorldCenterRect(UiLayout layout) {
		UiRect current = yandexGeoRect(layout);
		int gap = ultraCompactScreenLayout(layout) ? Math.max(1, layout.unit() / 5) : Math.max(2, layout.unit() / 3);
		return new UiRect(current.x(), current.y() - gap - current.height(), current.width(), current.height());
	}

	private static UiRect yandexAddMarkerRect(UiLayout layout) {
		UiRect current = yandexWorldCenterRect(layout);
		int gap = ultraCompactScreenLayout(layout) ? Math.max(1, layout.unit() / 5) : Math.max(2, layout.unit() / 3);
		return new UiRect(current.x(), current.y() - gap - current.height(), current.width(), current.height());
	}

	private static UUID createMarkerAtCenter(ServerLevel level, ServerPlayer player, YandexMapState state) {
		if (level == null || player == null || state == null) {
			return null;
		}
		int blockX = net.minecraft.util.Mth.floor(state.centerX);
		int blockZ = net.minecraft.util.Mth.floor(state.centerZ);
		int blockY = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
		YandexMapMarkerStore.YandexMapMarker marker = YandexMapMarkerStore.create(
				level,
				new BlockPos(blockX, blockY, blockZ),
				player,
				defaultMarkerTitle(player),
				""
		);
		return marker != null ? marker.markerId() : null;
	}

	private static boolean handleMarkerEditorTouch(
			ServerPlayer player,
			MinecraftServer server,
			ScreenComponent component,
			UiLayout layout,
			UiPoint touchPoint,
			YandexMapState state
	) {
		if (player == null || server == null || component == null || layout == null || touchPoint == null || state == null) {
			return false;
		}
		UUID markerId;
		synchronized (state) {
			markerId = state.editorMarkerId;
		}
		if (markerId == null) {
			return false;
		}
		YandexMapMarkerStore.YandexMapMarker marker = YandexMapMarkerStore.marker(markerId);
		if (marker == null) {
			synchronized (state) {
				state.editorMarkerId = null;
				state.version++;
			}
			clearPendingMarkerTitleRequests(component.runtimeKey());
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		UiRect panel = markerEditorPanelRect(layout);
		UiRect close = markerEditorCloseRect(layout);
		if (close.contains(touchPoint.x(), touchPoint.y()) || !panel.contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.editorMarkerId = null;
				state.version++;
			}
			PENDING_MARKER_TITLES.remove(player.getUUID());
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (markerEditorTitleRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			requestMarkerTitlePrompt(player, component.runtimeKey(), markerId);
			return true;
		}
		if (markerEditorIconRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			YandexMapMarkerStore.updateIcon(server, markerId, resolveHeldMarkerIconItemId(player.getMainHandItem()));
			synchronized (state) {
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (markerEditorDeleteRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			YandexMapMarkerStore.remove(server, markerId);
			synchronized (state) {
				state.editorMarkerId = null;
				state.version++;
			}
			PENDING_MARKER_TITLES.remove(player.getUUID());
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		return true;
	}

	private static void drawMarkers(Graphics2D graphics, UiLayout layout, YandexMapsVisualSnapshot snapshot, ScreenRuntimeKey runtimeKey) {
		if (graphics == null || layout == null || snapshot == null || runtimeKey == null) {
			return;
		}
		UUID editorMarkerId = null;
		YandexMapState state = STATES.get(runtimeKey);
		if (state != null) {
			synchronized (state) {
				editorMarkerId = state.editorMarkerId;
			}
		}
		for (ProjectedMarker marker : projectVisibleMarkers(
				runtimeKey.dimension(),
				layout,
				snapshot.centerX(),
				snapshot.centerZ(),
				snapshot.zoomBlocks()
		)) {
			drawMarker(graphics, layout, marker, Objects.equals(editorMarkerId, marker.markerId()));
		}
	}

	private static void drawMarker(Graphics2D graphics, UiLayout layout, ProjectedMarker projected, boolean editorSelected) {
		if (graphics == null || layout == null || projected == null || projected.marker() == null) {
			return;
		}
		boolean ultra = ultraCompactScreenLayout(layout);
		int fontSize = ultra ? clampInt(layout.unit() + 1, 6, 8) : clampInt(layout.unit(), 9, 13);
		Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
		var previousFont = graphics.getFont();
		graphics.setFont(font);
		var metrics = graphics.getFontMetrics();
		String title = truncateWithEllipsis(metrics, projected.marker().title(), ultra ? 78 : 152);
		boolean expanded = editorSelected || projected.distanceToReticlePx() <= (ultra ? 18.0D : 28.0D);
		UiRect iconRect = projected.iconRect();
		UiRect chipRect = iconRect;
		if (expanded) {
			int pad = ultra ? 2 : clampInt(layout.unit() / 2, 4, 8);
			int titleWidth = Math.max(18, metrics.stringWidth(title));
			chipRect = new UiRect(
					iconRect.x(),
					iconRect.y(),
					iconRect.width() + pad + titleWidth + pad * 2,
					iconRect.height()
			);
		}
		fillRoundedRect(graphics, chipRect, chipRect.height(), new Color(250, 252, 248, 234));
		strokeRoundedRect(graphics, chipRect, chipRect.height(), 1.0F, new Color(0, 0, 0, 44));
		drawMarkerIcon(graphics, iconRect, projected.marker());
		if (expanded) {
			int gap = ultra ? 2 : Math.max(4, layout.unit() / 2);
			UiRect titleRect = new UiRect(
					iconRect.right() + gap,
					chipRect.y(),
					Math.max(1, chipRect.right() - iconRect.right() - gap - (ultra ? 4 : 8)),
					chipRect.height()
			);
			drawVerticalText(graphics, title, titleRect, new Color(26, 30, 34, 244), Font.BOLD, fontSize);
		}
		graphics.setFont(previousFont);
	}

	private static void drawMarkerIcon(Graphics2D graphics, UiRect rect, YandexMapMarkerStore.YandexMapMarker marker) {
		if (graphics == null || rect == null || marker == null) {
			return;
		}
		int inset = Math.max(2, rect.width() / 6);
		BufferedImage icon = markerItemIcon(marker.iconItemId());
		if (icon != null) {
			drawContainedImage(graphics, icon, rect, inset);
			return;
		}
		drawPlayerUiIcon(graphics, rect.inset(inset), PlayerUiIcon.DIRECTIONS_2_LINE, new Color(20, 24, 26, 236));
	}

	private static void drawMarkerEditor(Graphics2D graphics, UiLayout layout, ScreenRuntimeKey runtimeKey) {
		if (graphics == null || layout == null || runtimeKey == null) {
			return;
		}
		YandexMapState state = STATES.get(runtimeKey);
		if (state == null) {
			return;
		}
		UUID markerId;
		synchronized (state) {
			markerId = state.editorMarkerId;
		}
		if (markerId == null) {
			return;
		}
		YandexMapMarkerStore.YandexMapMarker marker = YandexMapMarkerStore.marker(markerId);
		if (marker == null) {
			return;
		}
		UiRect panel = markerEditorPanelRect(layout);
		fillRoundedRect(graphics, panel, compactScreenLayout(layout) ? clampInt(layout.unit(), 8, 12) : clampInt(layout.unit() + 2, 10, 16), new Color(248, 250, 246, 238));
		strokeRoundedRect(graphics, panel, compactScreenLayout(layout) ? clampInt(layout.unit(), 8, 12) : clampInt(layout.unit() + 2, 10, 16), 1.0F, new Color(0, 0, 0, 46));
		drawMediaCloseButton(graphics, markerEditorCloseRect(layout), layout);
		int titleFontSize = compactScreenLayout(layout) ? clampInt(layout.unit() + 1, 8, 12) : clampInt(layout.unit() + 2, 10, 16);
		String fittedTitle = truncateWithEllipsis(
				graphics.getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, titleFontSize)),
				marker.title(),
				Math.max(24, markerEditorTitleRect(layout).width() - Math.max(12, layout.unit() * 2))
		);
		drawMediaTitleBar(graphics, markerEditorTitleRect(layout), fittedTitle, layout, MediaButtonSegment.SINGLE);
		UiRect iconRect = markerEditorIconRect(layout);
		fillRoundedRect(graphics, iconRect, Math.min(iconRect.width(), iconRect.height()) / 3, new Color(255, 255, 255, 210));
		strokeRoundedRect(graphics, iconRect, Math.min(iconRect.width(), iconRect.height()) / 3, 1.0F, new Color(0, 0, 0, 34));
		drawMarkerIcon(graphics, iconRect, marker);
		UiRect creatorRect = markerEditorCreatorRect(layout);
		fillRoundedRect(graphics, creatorRect, compactScreenLayout(layout) ? clampInt(layout.unit(), 8, 12) : clampInt(layout.unit() + 1, 10, 14), new Color(255, 255, 255, 186));
		drawVerticalText(
				graphics,
				truncateWithEllipsis(graphics.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, clampInt(layout.unit(), 8, 12))), marker.creatorName(), creatorRect.width() - Math.max(6, layout.unit())),
				creatorRect.inset(Math.max(3, layout.unit() / 2)),
				new Color(58, 64, 70, 216),
				Font.PLAIN,
				clampInt(layout.unit(), 8, 12)
		);
		Color deleteColor = drawMediaHeaderControlBase(graphics, markerEditorDeleteRect(layout), MediaButtonSegment.SINGLE);
		drawPlayerUiIcon(graphics, mediaChromeIconRect(markerEditorDeleteRect(layout), layout), PlayerUiIcon.TRASH, deleteColor);
	}

	private static List<ProjectedMarker> projectVisibleMarkers(
			ResourceKey<Level> dimension,
			UiLayout layout,
			double centerX,
			double centerZ,
			double zoomBlocks
	) {
		if (dimension == null || layout == null || !Double.isFinite(centerX) || !Double.isFinite(centerZ) || !Double.isFinite(zoomBlocks) || zoomBlocks <= 0.0D) {
			return List.of();
		}
		UiRect canvas = mediaCanvasRect(layout);
		if (canvas.width() <= 0 || canvas.height() <= 0) {
			return List.of();
		}
		int iconSize = ultraCompactScreenLayout(layout) ? clampInt(layout.unit() * 2 + 2, 12, 16) : clampInt(layout.unit() * 2 + 8, 18, 26);
		double horizontalMargin = iconSize * zoomBlocks * 1.5D;
		double verticalMargin = iconSize * zoomBlocks * 1.5D;
		double minX = centerX - canvas.width() * zoomBlocks * 0.5D - horizontalMargin;
		double maxX = centerX + canvas.width() * zoomBlocks * 0.5D + horizontalMargin;
		double minZ = centerZ - canvas.height() * zoomBlocks * 0.5D - verticalMargin;
		double maxZ = centerZ + canvas.height() * zoomBlocks * 0.5D + verticalMargin;
		int centerScreenX = canvas.x() + canvas.width() / 2;
		int centerScreenY = canvas.y() + canvas.height() / 2;
		List<ProjectedMarker> projected = new ArrayList<>();
		for (YandexMapMarkerStore.YandexMapMarker marker : YandexMapMarkerStore.markers(dimension)) {
			if (marker == null || marker.centerX() < minX || marker.centerX() > maxX || marker.centerZ() < minZ || marker.centerZ() > maxZ) {
				continue;
			}
			int markerScreenX = net.minecraft.util.Mth.floor(centerScreenX + (marker.centerX() - centerX) / zoomBlocks);
			int markerScreenY = net.minecraft.util.Mth.floor(centerScreenY + (marker.centerZ() - centerZ) / zoomBlocks);
			UiRect iconRect = new UiRect(markerScreenX - iconSize / 2, markerScreenY - iconSize / 2, iconSize, iconSize);
			double dx = markerScreenX - centerScreenX;
			double dy = markerScreenY - centerScreenY;
			projected.add(new ProjectedMarker(marker.markerId(), marker, iconRect, Math.sqrt(dx * dx + dy * dy)));
		}
		return projected;
	}

	private static ProjectedMarker markerAtTouch(
			ResourceKey<Level> dimension,
			UiLayout layout,
			double centerX,
			double centerZ,
			double zoomBlocks,
			UUID editorMarkerId,
			UiPoint touchPoint
	) {
		if (touchPoint == null) {
			return null;
		}
		List<ProjectedMarker> projectedMarkers = projectVisibleMarkers(dimension, layout, centerX, centerZ, zoomBlocks);
		ProjectedMarker fallback = null;
		for (ProjectedMarker projected : projectedMarkers) {
			int expandX = Math.max(2, layout.unit() / 3);
			int expandY = Math.max(2, layout.unit() / 3);
			UiRect hitRect = new UiRect(
					projected.iconRect().x() - expandX,
					projected.iconRect().y() - expandY,
					projected.iconRect().width() + expandX * 2,
					projected.iconRect().height() + expandY * 2
			);
			if (!hitRect.contains(touchPoint.x(), touchPoint.y())) {
				continue;
			}
			if (fallback == null || Objects.equals(projected.markerId(), editorMarkerId)) {
				fallback = projected;
			}
		}
		return fallback;
	}

	private static UiRect markerEditorPanelRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		if (ultraCompactScreenLayout(layout)) {
			return canvas;
		}
		int pad = clampInt(layout.unit(), 6, 12);
		int width = clampInt(canvas.width() / 3 + layout.unit() * 2, 168, 276);
		int height = clampInt(layout.unit() * 10, 116, 164);
		return new UiRect(
				canvas.right() - width - pad,
				canvas.y() + pad,
				Math.min(width, canvas.width() - pad * 2),
				Math.min(height, canvas.height() - pad * 2)
		);
	}

	private static UiRect markerEditorCloseRect(UiLayout layout) {
		UiRect panel = markerEditorPanelRect(layout);
		int size = compactScreenLayout(layout) ? clampInt(layout.unit() * 2 + 4, 14, 18) : clampInt(layout.unit() * 2 + 8, 18, 24);
		int pad = Math.max(4, layout.unit() / 2);
		return new UiRect(panel.right() - pad - size, panel.y() + pad, size, size);
	}

	private static UiRect markerEditorTitleRect(UiLayout layout) {
		UiRect panel = markerEditorPanelRect(layout);
		UiRect close = markerEditorCloseRect(layout);
		int pad = Math.max(4, layout.unit() / 2);
		int height = compactScreenLayout(layout) ? clampInt(layout.unit() * 2 + 6, 18, 22) : clampInt(layout.unit() * 3, 24, 34);
		return new UiRect(panel.x() + pad, panel.y() + pad, Math.max(1, close.x() - panel.x() - pad * 2), height);
	}

	private static UiRect markerEditorIconRect(UiLayout layout) {
		UiRect title = markerEditorTitleRect(layout);
		UiRect panel = markerEditorPanelRect(layout);
		int pad = Math.max(4, layout.unit() / 2);
		int size = Math.min(panel.height() - (title.height() + pad * 4), compactScreenLayout(layout) ? clampInt(layout.unit() * 4, 28, 42) : clampInt(layout.unit() * 5, 40, 56));
		return new UiRect(panel.x() + pad, title.bottom() + pad, Math.max(18, size), Math.max(18, size));
	}

	private static UiRect markerEditorDeleteRect(UiLayout layout) {
		UiRect panel = markerEditorPanelRect(layout);
		int size = compactScreenLayout(layout) ? clampInt(layout.unit() * 2 + 6, 16, 20) : clampInt(layout.unit() * 2 + 10, 20, 26);
		int pad = Math.max(4, layout.unit() / 2);
		return new UiRect(panel.right() - pad - size, panel.bottom() - pad - size, size, size);
	}

	private static UiRect markerEditorCreatorRect(UiLayout layout) {
		UiRect icon = markerEditorIconRect(layout);
		UiRect delete = markerEditorDeleteRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		return new UiRect(
				icon.right() + gap,
				icon.y(),
				Math.max(1, delete.x() - icon.right() - gap * 2),
				icon.height()
		);
	}

	private static String resolveHeldMarkerIconItemId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return itemId == null ? "" : itemId.toString();
	}

	private static BufferedImage markerItemIcon(String iconItemId) {
		if (iconItemId == null || iconItemId.isBlank()) {
			return null;
		}
		BufferedImage cached = ITEM_MARKER_ICON_CACHE.computeIfAbsent(
				iconItemId,
				key -> {
					BufferedImage loaded = loadItemMarkerIcon(key);
					return loaded != null ? loaded : EMPTY_MARKER_ICON;
				}
		);
		return cached == EMPTY_MARKER_ICON ? null : cached;
	}

	private static BufferedImage loadItemMarkerIcon(String iconItemId) {
		Identifier itemId = Identifier.tryParse(iconItemId);
		if (itemId == null) {
			return null;
		}
		Identifier modelId = Identifier.fromNamespaceAndPath(itemId.getNamespace(), "item/" + itemId.getPath());
		ItemModelResolution resolution = resolveItemModel(modelId, new HashSet<>());
		if (resolution == null) {
			return null;
		}
		List<BufferedImage> layers = new ArrayList<>();
		for (int layerIndex = 0; layerIndex < 5; layerIndex++) {
			Identifier textureId = resolveItemModelTexture(resolution, "layer" + layerIndex, new HashSet<>());
			if (textureId == null) {
				continue;
			}
			BufferedImage texture = MAP_ASSETS.loadTexture(textureId);
			if (texture != null) {
				layers.add(texture);
			}
		}
		if (layers.isEmpty()) {
			Identifier fallbackTexture = resolveFallbackItemTexture(resolution);
			if (fallbackTexture != null) {
				BufferedImage texture = MAP_ASSETS.loadTexture(fallbackTexture);
				if (texture != null) {
					layers.add(texture);
				}
			}
		}
		if (layers.isEmpty()) {
			return null;
		}
		BufferedImage icon = new BufferedImage(MAP_MARKER_ICON_SIZE, MAP_MARKER_ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		configureMapGraphics(graphics);
		for (BufferedImage layer : layers) {
			graphics.drawImage(layer, 0, 0, MAP_MARKER_ICON_SIZE, MAP_MARKER_ICON_SIZE, null);
		}
		graphics.dispose();
		return icon;
	}

	private static ItemModelResolution resolveItemModel(Identifier modelId, Set<String> resolving) {
		if (modelId == null || resolving == null) {
			return null;
		}
		String cacheKey = modelId.toString();
		if (!resolving.add(cacheKey)) {
			return null;
		}
		try {
			JsonObject json = MAP_ASSETS.loadModel(modelId);
			if (json == null) {
				return null;
			}
			Map<String, String> textures = new HashMap<>();
			Identifier parentId = null;
			if (json.has("parent")) {
				parentId = Identifier.tryParse(json.get("parent").getAsString());
				if (parentId != null && !"builtin/generated".equals(parentId.toString())) {
					ItemModelResolution parent = resolveItemModel(parentId, resolving);
					if (parent != null) {
						textures.putAll(parent.textures());
					}
				}
			}
			if (json.has("textures")) {
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
					textures.put(entry.getKey(), entry.getValue().getAsString());
				}
			}
			return new ItemModelResolution(modelId.getNamespace(), textures);
		} finally {
			resolving.remove(cacheKey);
		}
	}

	private static Identifier resolveItemModelTexture(ItemModelResolution resolution, String key, Set<String> resolving) {
		if (resolution == null || key == null || key.isBlank()) {
			return null;
		}
		if (!resolving.add(key)) {
			return null;
		}
		try {
			String texture = resolution.textures().get(key);
			if (texture == null || texture.isBlank()) {
				return null;
			}
			if (texture.startsWith("#")) {
				return resolveItemModelTexture(resolution, texture.substring(1), resolving);
			}
			return textureIdentifier(resolution.namespace(), texture);
		} finally {
			resolving.remove(key);
		}
	}

	private static Identifier resolveFallbackItemTexture(ItemModelResolution resolution) {
		if (resolution == null) {
			return null;
		}
		Identifier particle = resolveItemModelTexture(resolution, "particle", new HashSet<>());
		if (particle != null) {
			return particle;
		}
		for (String key : List.of("all", "side", "top", "front", "end", "texture")) {
			Identifier resolved = resolveItemModelTexture(resolution, key, new HashSet<>());
			if (resolved != null) {
				return resolved;
			}
		}
		for (String key : resolution.textures().keySet()) {
			Identifier resolved = resolveItemModelTexture(resolution, key, new HashSet<>());
			if (resolved != null) {
				return resolved;
			}
		}
		return null;
	}

	private static Identifier textureIdentifier(String defaultNamespace, String rawTexture) {
		if (rawTexture == null || rawTexture.isBlank()) {
			return null;
		}
		if (rawTexture.contains(":")) {
			return Identifier.tryParse(rawTexture);
		}
		return Identifier.fromNamespaceAndPath(defaultNamespace == null || defaultNamespace.isBlank() ? "minecraft" : defaultNamespace, rawTexture);
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

	private static List<MonitorYandexMapsBlueMapRenderer.DisplayOverlay> captureDisplayOverlays(
			ScreenComponent component,
			YandexMapState state,
			ServerLevel level,
			double centerX,
			double centerZ,
			double blocksPerPixel,
			long now
	) {
		if (component == null || state == null || level == null) {
			return List.of();
		}
		int visiblePixelWidth = Math.max(1, component.width() * MAP_SIZE);
		int visiblePixelHeight = Math.max(1, component.height() * MAP_SIZE);
		synchronized (state) {
			if (displayOverlayCacheCoversLocked(state, centerX, centerZ, visiblePixelWidth, visiblePixelHeight, blocksPerPixel, now)) {
				return state.cachedDisplayOverlays;
			}
		}
		int capturePixelWidth = visiblePixelWidth + DISPLAY_OVERLAY_CAPTURE_PADDING_PX * 2;
		int capturePixelHeight = visiblePixelHeight + DISPLAY_OVERLAY_CAPTURE_PADDING_PX * 2;
		List<MonitorYandexMapsBlueMapRenderer.DisplayOverlay> overlays = MonitorYandexMapsBlueMapRenderer.captureDisplayOverlays(
				level,
				centerX,
				centerZ,
				capturePixelWidth,
				capturePixelHeight,
				blocksPerPixel
		);
		synchronized (state) {
			state.cachedDisplayOverlays = overlays;
			state.displayOverlayCapturedAtMs = now;
			state.displayOverlayCenterX = centerX;
			state.displayOverlayCenterZ = centerZ;
			state.displayOverlayHalfWidth = capturePixelWidth * blocksPerPixel * 0.5D;
			state.displayOverlayHalfHeight = capturePixelHeight * blocksPerPixel * 0.5D;
		}
		return overlays;
	}

	private static boolean displayOverlayCacheCoversLocked(
			YandexMapState state,
			double centerX,
			double centerZ,
			int visiblePixelWidth,
			int visiblePixelHeight,
			double blocksPerPixel,
			long now
	) {
		if (state.cachedDisplayOverlays == null || now - state.displayOverlayCapturedAtMs > DISPLAY_OVERLAY_CACHE_MS) {
			return false;
		}
		double visibleHalfWidth = visiblePixelWidth * blocksPerPixel * 0.5D;
		double visibleHalfHeight = visiblePixelHeight * blocksPerPixel * 0.5D;
		double marginX = state.displayOverlayHalfWidth - visibleHalfWidth - 8.0D;
		double marginZ = state.displayOverlayHalfHeight - visibleHalfHeight - 8.0D;
		return marginX >= 0.0D
				&& marginZ >= 0.0D
				&& Math.abs(centerX - state.displayOverlayCenterX) <= marginX
				&& Math.abs(centerZ - state.displayOverlayCenterZ) <= marginZ;
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

	private static void schedulePanAnimationFrame(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		YandexMapState state = STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			if (state.panStartedAtMillis <= 0L || state.panFrameScheduled) {
				return;
			}
			state.panFrameScheduled = true;
		}
		scheduleRuntimeCallback(server, PAN_FRAME_DELAY_MS, () -> {
			YandexMapState current = STATES.get(key);
			if (current == null) {
				return;
			}
			boolean keepAnimating;
			synchronized (current) {
				current.panFrameScheduled = false;
				keepAnimating = current.panStartedAtMillis > 0L;
			}
			if (!keepAnimating) {
				return;
			}
			requestRuntimeRender(server, key);
			schedulePanAnimationFrame(server, key);
		});
	}

	private static void scheduleRuntimeCallback(MinecraftServer server, long delayMillis, Runnable callback) {
		if (server == null || callback == null) {
			return;
		}
		ensureExecutors();
		if (mediaScheduler == null) {
			server.execute(callback);
			return;
		}
		try {
			mediaScheduler.schedule(() -> server.execute(callback), Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException ignored) {
			server.execute(callback);
		}
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
		return new YandexMapsVisualSnapshot(0L, null, "", "", 0, 0, DEFAULT_BLOCKS_PER_PIXEL, List.of(), false);
	}

	private record ProjectedMarker(
			UUID markerId,
			YandexMapMarkerStore.YandexMapMarker marker,
			UiRect iconRect,
			double distanceToReticlePx
	) {
	}

	private record ItemModelResolution(
			String namespace,
			Map<String, String> textures
	) {
	}

	private record PendingMarkerTitleRequest(
			ScreenRuntimeKey screenKey,
			UUID markerId
	) {
	}

	private static final class YandexMapState {
		private boolean initialized;
		private long version;
		private long lastAppliedRenderVersion;
		private int activeRenderJobs;
		private boolean rerenderRequested;
		private double centerX;
		private double centerZ;
		private double targetX;
		private double targetZ;
		private double panStartX;
		private double panStartZ;
		private long panStartedAtMillis;
		private double blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL;
		private String streamStatus = "Ожидание карты";
		private boolean panFrameScheduled;
		private boolean tileReadyRenderScheduled;
		private long displayOverlayCapturedAtMs;
		private double displayOverlayCenterX;
		private double displayOverlayCenterZ;
		private double displayOverlayHalfWidth;
		private double displayOverlayHalfHeight;
		private List<MonitorYandexMapsBlueMapRenderer.DisplayOverlay> cachedDisplayOverlays = List.of();
		private UUID editorMarkerId;
	}
}
