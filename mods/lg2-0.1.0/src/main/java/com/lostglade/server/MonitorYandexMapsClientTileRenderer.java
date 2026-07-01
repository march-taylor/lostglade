package com.lostglade.server;

import com.lostglade.Lg2;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class MonitorYandexMapsClientTileRenderer {
	private static final int TILE_SIZE = MonitorScreenSystem.MAP_SIZE;
	private static final int MIN_LOD = 0;
	private static final int MAX_LOD = 9;
	private static final int MIN_ZOOM_EXPONENT = -4;
	private static final int MAX_ZOOM_EXPONENT = MAX_LOD;
	private static final int DIRECT_RENDER_MAX_LOD = 0;
	private static final double BASE_BLOCKS_PER_PIXEL = 1.0D / 16.0D;
	private static final int BASE_TILE_BLOCK_SPAN = 8;
	private static final int RGB_BYTES_PER_PIXEL = 3;
	private static final int TILE_RGB_BYTES = TILE_SIZE * TILE_SIZE * RGB_BYTES_PER_PIXEL;
	private static final int MAX_CACHED_TILES_PER_DIMENSION = 4_096;
	private static final int MAX_MISSING_TILES_PER_DIMENSION = 65_536;
	private static final int MAX_BASE_TILE_REQUESTS_PER_FRAME = 4;
	private static final long BACKGROUND_REFRESH_INTERVAL_TICKS = 20L;
	private static final int BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MIN = Math.max(1, Integer.getInteger("lg2.yandexMapBackgroundBaseTileRequestsPerPassMin", 2));
	private static final int BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MAX = Math.max(BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MIN, Integer.getInteger("lg2.yandexMapBackgroundBaseTileRequestsPerPassMax", 4));
	private static final int BACKGROUND_DISCOVERY_BACKLOG_THRESHOLD = Math.max(128, Integer.getInteger("lg2.yandexMapBackgroundDiscoveryBacklogThreshold", 1_024));
	private static final long BACKGROUND_STALE_BACKLOG_MS = 90L * 60_000L;
	private static final long ACTIVE_VIEW_HINT_TTL_MS = 20_000L;
	private static final int ACTIVE_VIEW_PRIORITY_TILE_MARGIN = 2;
	private static final int MAX_DIRTY_BASE_TILES_PER_DIMENSION = 32_768;
	private static final long BASE_TILE_REFRESH_MS = 30L * 60_000L;
	private static final String CACHE_DIR_NAME = "lg2-yandex-map-client-tiles-v12";
	private static final int MISSING_RGB = 0x18242B;
	private static final Object LOCK = new Object();
	private static final Map<WorldCacheKey, DimensionTileCache> CACHES = new LinkedHashMap<>(8, 0.75F, true);
	private static final Map<ScreenRuntimeKey, ActiveViewHint> ACTIVE_VIEW_HINTS = new ConcurrentHashMap<>();
	private static volatile Path persistentRoot;

	private MonitorYandexMapsClientTileRenderer() {
	}

	static void configure(MinecraftServer server) {
		if (server == null) {
			return;
		}
		persistentRoot = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(CACHE_DIR_NAME);
	}

	static void tick(MinecraftServer server) {
		if (server == null
				|| !RendererBotCameraSystem.hasReadyBot(server)
				|| Math.floorMod(server.getTickCount(), BACKGROUND_REFRESH_INTERVAL_TICKS) != 0) {
			return;
		}
		List<DimensionTileCache> caches;
		synchronized (LOCK) {
			if (CACHES.isEmpty()) {
				return;
			}
			caches = new ArrayList<>(CACHES.values());
		}
		caches.sort(Comparator.comparingLong(DimensionTileCache::oldestPendingBaseTileAt));
		int pendingBaseTiles = 0;
		long oldestPendingAt = Long.MAX_VALUE;
		for (DimensionTileCache cache : caches) {
			if (cache == null) {
				continue;
			}
			pendingBaseTiles += cache.pendingBaseTileCount();
			oldestPendingAt = Math.min(oldestPendingAt, cache.oldestPendingBaseTileAt());
		}
		TileRequestBudget budget = new TileRequestBudget(backgroundBaseTileRequestsPerPass(server, pendingBaseTiles, oldestPendingAt));
		for (DimensionTileCache cache : caches) {
			if (cache == null || !budget.hasRemaining()) {
				break;
			}
			cache.refreshDirtyBaseTiles(server, budget, true);
		}
		boolean progressed;
		do {
			progressed = false;
			for (DimensionTileCache cache : caches) {
				if (cache == null || !budget.hasRemaining()) {
					break;
				}
				progressed |= cache.refreshDirtyBaseTiles(server, budget, false);
			}
		} while (progressed && budget.hasRemaining());
	}

	static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null || !chunk.isUnsaved()) {
			return;
		}
		markChunkDirty(level, chunk.getPos(), true);
	}

	static void markChunkDirty(ServerLevel level, ChunkPos pos) {
		markChunkDirty(level, pos, false);
	}

	static void deactivateView(ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		ACTIVE_VIEW_HINTS.remove(key);
	}

	static void clear(ResourceKey<Level> dimension) {
		synchronized (LOCK) {
			if (dimension == null) {
				CACHES.clear();
				ACTIVE_VIEW_HINTS.clear();
			} else {
				CACHES.keySet().removeIf(key -> dimension.equals(key.dimension()));
				ACTIVE_VIEW_HINTS.entrySet().removeIf(entry -> entry.getKey() == null || dimension.equals(entry.getKey().dimension()));
			}
		}
	}

	static void clearPersistentCache(MinecraftServer server) {
		Path root = server != null
				? server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(CACHE_DIR_NAME)
				: persistentRoot;
		if (root != null) {
			deleteDirectory(root);
		}
		if (server != null) {
			Path dataRoot = server.getWorldPath(LevelResource.ROOT).resolve("data");
			deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v1"));
			deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v2"));
			deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v3"));
			deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v4"));
			deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v5"));
			deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v6"));
			deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v7"));
				deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v8"));
				deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v9"));
				deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v10"));
				deleteDirectory(dataRoot.resolve("lg2-yandex-map-client-tiles-v11"));
			}
		clear(null);
	}

	private static void markChunkDirty(ServerLevel level, ChunkPos pos, boolean discoveryOnly) {
		if (level == null || pos == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}
		configure(server);
		cacheFor(worldCacheKey(server, level)).markChunkDirty(pos, System.currentTimeMillis(), discoveryOnly);
	}

	private static void deleteDirectory(Path root) {
		if (root == null || !Files.exists(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ignored) {
				}
			});
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to clear Yandex client tile cache {}", root, exception);
		}
	}

	static Frame render(
			MinecraftServer server,
			ServerLevel level,
			double centerX,
			double centerZ,
			int width,
			int height,
			double blocksPerPixel,
			Runnable onTileReady
	) {
		return render(server, level, centerX, centerZ, width, height, blocksPerPixel, onTileReady, null);
	}

	static Frame render(
			MinecraftServer server,
			ServerLevel level,
			double centerX,
			double centerZ,
			int width,
			int height,
			double blocksPerPixel,
			Runnable onTileReady,
			ScreenRuntimeKey activeViewKey
	) {
		if (server == null || level == null || width <= 0 || height <= 0 || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
			return Frame.failure(null, "Карта недоступна");
		}
		configure(server);
		WorldCacheKey worldKey = worldCacheKey(server, level);
		double safeBlocksPerPixel = snapBlocksPerPixel(blocksPerPixel);
		recordActiveView(worldKey, activeViewKey, centerX, centerZ, width, height, safeBlocksPerPixel);
		int lod = lodForBlocksPerPixel(safeBlocksPerPixel);
		double tileBlocksPerPixel = blocksPerPixelForLod(lod);
		double worldLeft = centerX - width * safeBlocksPerPixel * 0.5D;
		double worldTop = centerZ - height * safeBlocksPerPixel * 0.5D;
		DimensionTileCache cache = cacheFor(worldKey);
		List<TileKey> visibleTiles = visibleTiles(lod, tileBlocksPerPixel, worldLeft, worldTop, width, height, safeBlocksPerPixel, centerX, centerZ);
		TileRequestBudget budget = new TileRequestBudget(MAX_BASE_TILE_REQUESTS_PER_FRAME);
		boolean activeView = activeViewKey != null;
		int missingTiles = 0;
		for (TileKey key : visibleTiles) {
			boolean hasTile = cache.hasUsableTile(key);
			if (hasTile) {
				cache.refreshIfStale(server, level, key, budget, onTileReady, 1, activeView);
				continue;
			}
			missingTiles++;
			cache.requestTile(server, level, key, budget, onTileReady, 2, activeView);
		}
		if (budget.hasRemaining()) {
			for (TileKey key : prefetchTiles(visibleTiles, tileBlocksPerPixel, centerX, centerZ)) {
				if (!budget.hasRemaining()) {
					break;
				}
				if (!cache.hasUsableTile(key)) {
					cache.requestTile(server, level, key, budget, onTileReady, 2, activeView);
				}
			}
		}

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int missingPixels = 0;
		for (int screenY = 0; screenY < height; screenY++) {
			double worldZ = worldTop + (screenY + 0.5D) * safeBlocksPerPixel;
			int row = screenY * width;
			for (int screenX = 0; screenX < width; screenX++) {
				double worldX = worldLeft + (screenX + 0.5D) * safeBlocksPerPixel;
				int rgb = cache.sample(worldX, worldZ, lod);
				if (rgb < 0) {
					rgb = MISSING_RGB;
					missingPixels++;
				}
				pixels[row + screenX] = 0xFF000000 | rgb;
			}
		}
		String status;
		boolean needsCamera = missingTiles > 0 || missingPixels > 0;
		if (!RendererBotCameraSystem.hasReadyBot(server) && needsCamera) {
			status = "Нет клиента камеры";
		} else if (needsCamera) {
			status = "Рендер карты: " + Math.max(0, budget.used()) + " тайл.";
		} else {
			status = "Client camera tiles";
		}
		boolean healthy = missingPixels < width * height || !visibleTiles.isEmpty();
		return new Frame(image, status, healthy);
	}

	static double snapBlocksPerPixel(double blocksPerPixel) {
		if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
			return blocksPerPixelForZoomExponent(0);
		}
		return blocksPerPixelForZoomExponent(zoomExponentForBlocksPerPixel(blocksPerPixel));
	}

	static int zoomExponentForBlocksPerPixel(double blocksPerPixel) {
		if (!Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
			return 0;
		}
		return Mth.clamp((int) Math.round(Math.log(blocksPerPixel / BASE_BLOCKS_PER_PIXEL) / Math.log(2.0D)), MIN_ZOOM_EXPONENT, MAX_ZOOM_EXPONENT);
	}

	static double blocksPerPixelForZoomExponent(int exponent) {
		int clamped = Mth.clamp(exponent, MIN_ZOOM_EXPONENT, MAX_ZOOM_EXPONENT);
		return Math.scalb(BASE_BLOCKS_PER_PIXEL, clamped);
	}

	private static DimensionTileCache cacheFor(WorldCacheKey key) {
		synchronized (LOCK) {
			return CACHES.computeIfAbsent(key, ignored -> new DimensionTileCache(key));
		}
	}

	private static WorldCacheKey worldCacheKey(MinecraftServer server, ServerLevel level) {
		return new WorldCacheKey(
				server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(),
				level.dimension()
		);
	}

	private static int backgroundBaseTileRequestsPerPass(MinecraftServer server, int pendingBaseTiles, long oldestPendingAt) {
		int budget = BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MIN;
		int playerCount = server != null ? Math.max(0, server.getPlayerCount()) : 0;
		if (playerCount >= 6) {
			budget++;
		}
		if (playerCount >= 10) {
			budget++;
		}
		if (pendingBaseTiles >= BACKGROUND_DISCOVERY_BACKLOG_THRESHOLD) {
			budget++;
		}
		long now = System.currentTimeMillis();
		if (oldestPendingAt != Long.MAX_VALUE && now - oldestPendingAt >= BACKGROUND_STALE_BACKLOG_MS) {
			budget++;
		}
		return Mth.clamp(budget, BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MIN, BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MAX);
	}

	private static void recordActiveView(
			WorldCacheKey worldKey,
			ScreenRuntimeKey activeViewKey,
			double centerX,
			double centerZ,
			int width,
			int height,
			double blocksPerPixel
	) {
		if (worldKey == null
				|| activeViewKey == null
				|| width <= 0
				|| height <= 0
				|| !Double.isFinite(centerX)
				|| !Double.isFinite(centerZ)
				|| !Double.isFinite(blocksPerPixel)
				|| blocksPerPixel <= 0.0D) {
			return;
		}
		long now = System.currentTimeMillis();
		pruneExpiredActiveViews(now);
		ACTIVE_VIEW_HINTS.put(
				activeViewKey,
				new ActiveViewHint(worldKey, centerX, centerZ, width, height, blocksPerPixel, now + ACTIVE_VIEW_HINT_TTL_MS)
		);
	}

	private static void pruneExpiredActiveViews(long now) {
		if (ACTIVE_VIEW_HINTS.isEmpty()) {
			return;
		}
		ACTIVE_VIEW_HINTS.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAtMillis() < now);
	}

	private static List<ActiveViewHint> activeViewHintsFor(WorldCacheKey worldKey) {
		if (worldKey == null || ACTIVE_VIEW_HINTS.isEmpty()) {
			return List.of();
		}
		long now = System.currentTimeMillis();
		pruneExpiredActiveViews(now);
		List<ActiveViewHint> hints = new ArrayList<>();
		for (ActiveViewHint hint : ACTIVE_VIEW_HINTS.values()) {
			if (hint != null && worldKey.equals(hint.worldKey())) {
				hints.add(hint);
			}
		}
		return hints;
	}

	static int lodForBlocksPerPixel(double blocksPerPixel) {
		return Mth.clamp((int) Math.round(Math.log(Math.max(BASE_BLOCKS_PER_PIXEL, blocksPerPixel) / BASE_BLOCKS_PER_PIXEL) / Math.log(2.0D)), MIN_LOD, MAX_LOD);
	}

	private static double blocksPerPixelForLod(int lod) {
		return blocksPerPixelForZoomExponent(lod);
	}

	private static double tileWorldSize(int lod) {
		return TILE_SIZE * blocksPerPixelForLod(lod);
	}

	private static long floorToLong(double value) {
		long floor = (long) value;
		return value < floor ? floor - 1L : floor;
	}

	private static List<TileKey> visibleTiles(
			int lod,
			double tileBlocksPerPixel,
			double worldLeft,
			double worldTop,
			int width,
			int height,
			double blocksPerPixel,
			double centerX,
			double centerZ
	) {
		double tileSize = TILE_SIZE * tileBlocksPerPixel;
		long minTileX = floorToLong(worldLeft / tileSize);
		long minTileZ = floorToLong(worldTop / tileSize);
		long maxTileX = floorToLong((worldLeft + width * blocksPerPixel) / tileSize);
		long maxTileZ = floorToLong((worldTop + height * blocksPerPixel) / tileSize);
		List<TileKey> keys = new ArrayList<>();
		for (long tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
			for (long tileX = minTileX; tileX <= maxTileX; tileX++) {
				keys.add(new TileKey(lod, tileX, tileZ));
			}
		}
		keys.sort(Comparator.comparingDouble(key -> {
			double tileCenterX = (key.tileX() + 0.5D) * tileSize;
			double tileCenterZ = (key.tileZ() + 0.5D) * tileSize;
			double dx = tileCenterX - centerX;
			double dz = tileCenterZ - centerZ;
			return dx * dx + dz * dz;
		}));
		return keys;
	}

	private static List<TileKey> prefetchTiles(List<TileKey> visibleTiles, double tileBlocksPerPixel, double centerX, double centerZ) {
		if (visibleTiles == null || visibleTiles.isEmpty()) {
			return List.of();
		}
		Set<TileKey> seen = new HashSet<>(visibleTiles);
		List<TileKey> keys = new ArrayList<>();
		for (TileKey visible : visibleTiles) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					if (dx == 0 && dz == 0) {
						continue;
					}
					TileKey candidate = new TileKey(visible.lod(), visible.tileX() + dx, visible.tileZ() + dz);
					if (seen.add(candidate)) {
						keys.add(candidate);
					}
				}
			}
		}
		double tileSize = TILE_SIZE * tileBlocksPerPixel;
		keys.sort(Comparator.comparingDouble(key -> {
			double tileCenterX = (key.tileX() + 0.5D) * tileSize;
			double tileCenterZ = (key.tileZ() + 0.5D) * tileSize;
			double dx = tileCenterX - centerX;
			double dz = tileCenterZ - centerZ;
			return dx * dx + dz * dz;
		}));
		return keys;
	}

	record Frame(BufferedImage image, String status, boolean healthy) {
		static Frame failure(BufferedImage image, String status) {
			return new Frame(image, status, false);
		}
	}

	private record WorldCacheKey(Path root, ResourceKey<Level> dimension) {
	}

	private record TileKey(int lod, long tileX, long tileZ) {
	}

	private record TileImage(byte[] pixels, long renderedAt) {
		private boolean valid() {
			return this.pixels != null && this.pixels.length >= TILE_RGB_BYTES;
		}

		private int sample(int x, int y) {
			if (!valid()) {
				return -1;
			}
			int safeX = Mth.clamp(x, 0, TILE_SIZE - 1);
			int safeY = Mth.clamp(y, 0, TILE_SIZE - 1);
			int offset = (safeY * TILE_SIZE + safeX) * RGB_BYTES_PER_PIXEL;
			return (Byte.toUnsignedInt(this.pixels[offset]) << 16)
					| (Byte.toUnsignedInt(this.pixels[offset + 1]) << 8)
					| Byte.toUnsignedInt(this.pixels[offset + 2]);
		}
	}

	private static final class DimensionTileCache {
		private final WorldCacheKey worldKey;
		private final LinkedHashMap<TileKey, TileImage> tiles = new LinkedHashMap<>(512, 0.75F, true);
		private final Map<TileKey, TileImage> tileLookup = new ConcurrentHashMap<>();
		private final Set<TileKey> missingTiles = ConcurrentHashMap.newKeySet();
		private final Set<TileKey> inFlight = ConcurrentHashMap.newKeySet();
		private final Map<TileKey, Long> dirtyAfter = new ConcurrentHashMap<>();
		private final LinkedHashMap<TileKey, DirtyTileState> pendingBaseTiles = new LinkedHashMap<>();

		private DimensionTileCache(WorldCacheKey worldKey) {
			this.worldKey = worldKey;
		}

		private boolean hasUsableTile(TileKey key) {
			TileImage image = imageFor(key);
			return image != null && image.valid();
		}

		private void refreshIfStale(MinecraftServer server, ServerLevel level, TileKey key, TileRequestBudget budget, Runnable onTileReady, int priorityScore, boolean activeView) {
			TileImage image = imageFor(key);
			long now = System.currentTimeMillis();
			if (image == null || !isStale(key, image, now)) {
				return;
			}
			requestTile(server, level, key, budget, onTileReady, priorityScore, activeView);
		}

		private boolean refreshDirtyBaseTiles(MinecraftServer server, TileRequestBudget budget, boolean prioritizeActiveViews) {
			if (server == null || budget == null || !budget.hasRemaining()) {
				return false;
			}
			ServerLevel level = server.getLevel(this.worldKey.dimension());
			if (level == null) {
				return false;
			}
			PendingBaseTile pending = pollNextPendingBaseTile(prioritizeActiveViews);
			if (pending == null) {
				return false;
			}
			TileKey key = pending.key();
			if (key == null) {
				return false;
			}
			if (this.inFlight.contains(key)) {
				queuePendingBaseTile(key, this.dirtyAfter.getOrDefault(key, System.currentTimeMillis()), pending.discoveryOnly());
				return false;
			}
			if (hasUsableTile(key) && !isStale(key)) {
				clearDirtyMarkerIfCovered(key, System.currentTimeMillis());
				return true;
			}
			int usedBefore = budget.used();
			requestTile(server, level, key, budget, null, pending.priorityScore(), pending.activeView());
			if (budget.used() == usedBefore) {
				queuePendingBaseTile(key, this.dirtyAfter.getOrDefault(key, System.currentTimeMillis()), pending.discoveryOnly());
				return false;
			}
			return true;
		}

		private void requestTile(MinecraftServer server, ServerLevel level, TileKey key, TileRequestBudget budget, Runnable onTileReady, int priorityScore, boolean activeView) {
			if (server == null || level == null || key == null || budget == null) {
				return;
			}
			if (key.lod() > DIRECT_RENDER_MAX_LOD) {
				if (tryBuildFromChildren(key, System.currentTimeMillis())) {
					notifyReady(onTileReady);
					return;
				}
				for (TileKey child : childKeys(key)) {
					if (!budget.hasRemaining()) {
						return;
					}
					requestTile(server, level, child, budget, onTileReady, priorityScore, activeView);
				}
				return;
			}
			if (hasUsableTile(key) && !isStale(key)) {
				return;
			}
			if (!budget.tryConsume() || !this.inFlight.add(key)) {
				return;
			}
			long requestStartedAt = System.currentTimeMillis();
			double bpp = blocksPerPixelForLod(key.lod());
			double tileSize = TILE_SIZE * bpp;
			double centerX = (key.tileX() + 0.5D) * tileSize;
			double centerZ = (key.tileZ() + 0.5D) * tileSize;
			CompletableFuture<byte[]> future = RendererBotCameraSystem.requestTopDownMapTile(
					level,
					key.lod(),
					key.tileX(),
					key.tileZ(),
					centerX,
					centerZ,
					TILE_SIZE,
					bpp,
					Math.max(0, priorityScore),
					activeView
			);
			future.whenComplete((pixels, throwable) -> {
				try {
					if (throwable != null) {
						Lg2.LOGGER.debug("Yandex map tile render failed at lod {} {},{}: {}", key.lod(), key.tileX(), key.tileZ(), throwable.toString());
						return;
					}
					if (pixels == null || pixels.length < TILE_RGB_BYTES) {
						return;
					}
					TileImage image = new TileImage(Arrays.copyOf(pixels, TILE_RGB_BYTES), requestStartedAt);
					storeTile(key, image, requestStartedAt);
					rebuildAncestors(key);
					Long remainingDirtyAt = remainingDirtyAt(key, requestStartedAt);
					if (remainingDirtyAt != null && key.lod() == MIN_LOD) {
						queuePendingBaseTile(key, remainingDirtyAt, false);
					}
				} finally {
					this.inFlight.remove(key);
					notifyReady(onTileReady);
				}
			});
		}

		private boolean isStale(TileKey key) {
			TileImage image = imageFor(key);
			return isStale(key, image, System.currentTimeMillis());
		}

		private boolean isStale(TileKey key, TileImage image, long now) {
			if (image == null || !image.valid()) {
				return true;
			}
			Long dirtyAt = this.dirtyAfter.get(key);
			return (dirtyAt != null && image.renderedAt() < dirtyAt)
					|| now - image.renderedAt() > BASE_TILE_REFRESH_MS;
		}

		private TileImage imageFor(TileKey key) {
			TileImage cached = this.tileLookup.get(key);
			if (cached != null) {
				synchronized (LOCK) {
					this.tiles.get(key);
				}
				return cached;
			}
			TileImage loaded = loadTile(key);
			if (loaded == null) {
				markMissing(key);
				return null;
			}
			cacheTile(key, loaded, false, Long.MIN_VALUE);
			return loaded;
		}

		private int sample(double worldX, double worldZ, int lod) {
			return sampleAtLod(worldX, worldZ, lod);
		}

		private int sampleAtLod(double worldX, double worldZ, int lod) {
			if (lod < MIN_LOD || lod > MAX_LOD) {
				return -1;
			}
			TileKey key = tileKeyAt(lod, worldX, worldZ);
			TileImage image = imageFor(key);
			if (image == null) {
				return -1;
			}
			double tileSize = tileWorldSize(lod);
			double tileBlocksPerPixel = blocksPerPixelForLod(lod);
			int localX = Mth.floor((worldX - key.tileX() * tileSize) / tileBlocksPerPixel);
			int localZ = Mth.floor((worldZ - key.tileZ() * tileSize) / tileBlocksPerPixel);
			return image.sample(localX, localZ);
		}

		private TileKey tileKeyAt(int lod, double worldX, double worldZ) {
			double size = tileWorldSize(lod);
			return new TileKey(lod, floorToLong(worldX / size), floorToLong(worldZ / size));
		}

		private boolean tryBuildFromChildren(TileKey key, long buildStartedAt) {
			if (key == null || key.lod() <= MIN_LOD) {
				return false;
			}
			TileImage[] children = new TileImage[4];
			int index = 0;
			for (TileKey child : childKeys(key)) {
				TileImage image = imageFor(child);
				if (image == null || !image.valid() || isStale(child, image, buildStartedAt)) {
					return false;
				}
				children[index++] = image;
			}
			byte[] pixels = new byte[TILE_RGB_BYTES];
			int[] colors = new int[4];
			for (int y = 0; y < TILE_SIZE; y++) {
				for (int x = 0; x < TILE_SIZE; x++) {
					int count = 0;
					for (int dy = 0; dy < 2; dy++) {
						for (int dx = 0; dx < 2; dx++) {
							int combinedX = x * 2 + dx;
							int combinedY = y * 2 + dy;
							int childX = combinedX >= TILE_SIZE ? 1 : 0;
							int childY = combinedY >= TILE_SIZE ? 1 : 0;
							TileImage child = children[childY * 2 + childX];
							colors[count++] = child.sample(combinedX & (TILE_SIZE - 1), combinedY & (TILE_SIZE - 1));
						}
					}
					writeRgb(pixels, y * TILE_SIZE + x, averageRgb(colors, count));
				}
			}
			storeTile(key, new TileImage(pixels, buildStartedAt), buildStartedAt);
			return true;
		}

		private void rebuildAncestors(TileKey key) {
			long buildStartedAt = System.currentTimeMillis();
			TileKey parent = parentKey(key);
			while (parent != null && tryBuildFromChildren(parent, buildStartedAt)) {
				parent = parentKey(parent);
			}
		}

		private List<TileKey> childKeys(TileKey key) {
			if (key == null || key.lod() <= MIN_LOD) {
				return List.of();
			}
			int childLod = key.lod() - 1;
			long baseX = key.tileX() * 2L;
			long baseZ = key.tileZ() * 2L;
			return List.of(
					new TileKey(childLod, baseX, baseZ),
					new TileKey(childLod, baseX + 1L, baseZ),
					new TileKey(childLod, baseX, baseZ + 1L),
					new TileKey(childLod, baseX + 1L, baseZ + 1L)
			);
		}

		private TileKey parentKey(TileKey key) {
			if (key == null || key.lod() >= MAX_LOD) {
				return null;
			}
			return new TileKey(key.lod() + 1, Math.floorDiv(key.tileX(), 2L), Math.floorDiv(key.tileZ(), 2L));
		}

		private void storeTile(TileKey key, TileImage image, long freshnessCutoffMillis) {
			cacheTile(key, image, true, freshnessCutoffMillis);
		}

		private void cacheTile(TileKey key, TileImage image, boolean persist, long freshnessCutoffMillis) {
			if (key == null || image == null || !image.valid()) {
				return;
			}
			synchronized (LOCK) {
				this.tiles.put(key, image);
				this.tileLookup.put(key, image);
				this.missingTiles.remove(key);
				if (key.lod() == MIN_LOD) {
					DirtyTileState pending = this.pendingBaseTiles.get(key);
					if (pending != null && pending.lastMarkedAt() <= freshnessCutoffMillis) {
						this.pendingBaseTiles.remove(key);
					}
				}
				trimToBudget();
			}
			clearDirtyMarkerIfCovered(key, freshnessCutoffMillis);
			if (persist) {
				persistTile(key, image);
			}
		}

		private void trimToBudget() {
			while (this.tiles.size() > MAX_CACHED_TILES_PER_DIMENSION) {
				Iterator<Map.Entry<TileKey, TileImage>> iterator = this.tiles.entrySet().iterator();
				if (!iterator.hasNext()) {
					return;
				}
				Map.Entry<TileKey, TileImage> eldest = iterator.next();
				iterator.remove();
				this.tileLookup.remove(eldest.getKey(), eldest.getValue());
			}
		}

		private void markMissing(TileKey key) {
			if (key == null || this.inFlight.contains(key)) {
				return;
			}
			this.missingTiles.add(key);
			if (this.missingTiles.size() > MAX_MISSING_TILES_PER_DIMENSION) {
				this.missingTiles.clear();
			}
		}

		private TileImage loadTile(TileKey key) {
			if (key == null || this.missingTiles.contains(key)) {
				return null;
			}
			Path path = tilePath(this.worldKey, key);
			if (path == null || !Files.isRegularFile(path)) {
				return null;
			}
			try {
				byte[] pixels = Files.readAllBytes(path);
				if (pixels.length < TILE_RGB_BYTES) {
					return null;
				}
				long modifiedAt = Files.getLastModifiedTime(path).toMillis();
				return new TileImage(Arrays.copyOf(pixels, TILE_RGB_BYTES), modifiedAt);
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to load Yandex map tile {}", path, exception);
				return null;
			}
		}

		private void persistTile(TileKey key, TileImage image) {
			Path path = tilePath(this.worldKey, key);
			if (path == null || image == null || image.pixels() == null) {
				return;
			}
			try {
				Files.createDirectories(path.getParent());
				Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
				Files.write(tmp, image.pixels());
				try {
					Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				} catch (IOException ignored) {
					Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
				}
				Files.setLastModifiedTime(path, FileTime.fromMillis(Math.max(0L, image.renderedAt())));
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to persist Yandex map tile {}", path, exception);
			}
		}

		private int pendingBaseTileCount() {
			synchronized (LOCK) {
				return this.pendingBaseTiles.size();
			}
		}

		private long oldestPendingBaseTileAt() {
			synchronized (LOCK) {
				long oldest = Long.MAX_VALUE;
				for (DirtyTileState state : this.pendingBaseTiles.values()) {
					if (state != null) {
						oldest = Math.min(oldest, state.firstMarkedAt());
					}
				}
				return oldest;
			}
		}

		private void markChunkDirty(ChunkPos pos, long dirtyAt, boolean discoveryOnly) {
			if (pos == null) {
				return;
			}
			long minTileX = floorToLong(((double) (pos.x << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileX = floorToLong(((double) ((pos.x << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			long minTileZ = floorToLong(((double) (pos.z << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileZ = floorToLong(((double) ((pos.z << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			for (long tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
				for (long tileX = minTileX; tileX <= maxTileX; tileX++) {
					markTileDirtyCascade(new TileKey(MIN_LOD, tileX, tileZ), dirtyAt, discoveryOnly);
				}
			}
		}

		private void markTileDirtyCascade(TileKey key, long dirtyAt, boolean discoveryOnly) {
			TileKey current = key;
			while (current != null) {
				this.dirtyAfter.merge(current, dirtyAt, Math::max);
				current = parentKey(current);
			}
			queuePendingBaseTile(key, dirtyAt, discoveryOnly);
		}

		private void queuePendingBaseTile(TileKey key, long dirtyAt, boolean discoveryOnly) {
			if (key == null || key.lod() != MIN_LOD) {
				return;
			}
			synchronized (LOCK) {
				DirtyTileState existing = this.pendingBaseTiles.get(key);
				boolean renderedBefore = existing != null ? existing.renderedBefore() : tileWasRenderedBefore(key);
				if (existing == null) {
					this.pendingBaseTiles.put(key, new DirtyTileState(dirtyAt, dirtyAt, discoveryOnly, renderedBefore));
				} else {
					this.pendingBaseTiles.put(
							key,
							new DirtyTileState(
									Math.min(existing.firstMarkedAt(), dirtyAt),
									Math.max(existing.lastMarkedAt(), dirtyAt),
									existing.discoveryOnly() && discoveryOnly,
									existing.renderedBefore() || renderedBefore
							)
					);
				}
				trimPendingBaseTilesLocked();
			}
		}

		private void trimPendingBaseTilesLocked() {
			while (this.pendingBaseTiles.size() > MAX_DIRTY_BASE_TILES_PER_DIMENSION) {
				TileKey discoveryKey = null;
				for (Map.Entry<TileKey, DirtyTileState> entry : this.pendingBaseTiles.entrySet()) {
					DirtyTileState state = entry.getValue();
					if (state != null && state.discoveryOnly()) {
						discoveryKey = entry.getKey();
						break;
					}
				}
				if (discoveryKey != null) {
					this.pendingBaseTiles.remove(discoveryKey);
					continue;
				}
				Iterator<Map.Entry<TileKey, DirtyTileState>> iterator = this.pendingBaseTiles.entrySet().iterator();
				if (!iterator.hasNext()) {
					return;
				}
				iterator.next();
				iterator.remove();
			}
		}

		private PendingBaseTile pollNextPendingBaseTile(boolean prioritizeActiveViews) {
			synchronized (LOCK) {
				List<ActiveViewHint> activeViews = prioritizeActiveViews ? activeViewHintsFor(this.worldKey) : List.of();
				if (prioritizeActiveViews && activeViews.isEmpty()) {
					return null;
				}
				TileKey bestKey = null;
				DirtyTileState bestState = null;
				boolean bestActive = false;
				int bestScore = Integer.MIN_VALUE;
				Iterator<Map.Entry<TileKey, DirtyTileState>> iterator = this.pendingBaseTiles.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<TileKey, DirtyTileState> entry = iterator.next();
					TileKey key = entry.getKey();
					DirtyTileState state = entry.getValue();
					if (key == null || state == null) {
						iterator.remove();
						continue;
					}
					boolean active = !activeViews.isEmpty() && intersectsActiveView(key, activeViews);
					if (prioritizeActiveViews && !active) {
						continue;
					}
					int score = tilePriorityScore(state, active);
					if (bestKey == null
							|| score > bestScore
							|| (score == bestScore && active && !bestActive)
							|| (score == bestScore && active == bestActive && state.firstMarkedAt() < bestState.firstMarkedAt())) {
						bestKey = key;
						bestState = state;
						bestActive = active;
						bestScore = score;
					}
				}
				TileKey selectedKey = bestKey;
				DirtyTileState selectedState = bestState;
				if (selectedKey == null || selectedState == null) {
					return null;
				}
				this.pendingBaseTiles.remove(selectedKey);
				return new PendingBaseTile(selectedKey, selectedState.discoveryOnly(), bestActive, bestScore);
			}
		}

		private int tilePriorityScore(DirtyTileState state, boolean active) {
			if (state == null) {
				return Integer.MIN_VALUE;
			}
			int score = state.renderedBefore() ? 1 : 2;
			if (active) {
				score++;
			}
			return score;
		}

		private boolean intersectsActiveView(TileKey key, List<ActiveViewHint> activeViews) {
			if (key == null || key.lod() != MIN_LOD || activeViews == null || activeViews.isEmpty()) {
				return false;
			}
			double tileMinX = key.tileX() * BASE_TILE_BLOCK_SPAN;
			double tileMaxX = tileMinX + BASE_TILE_BLOCK_SPAN;
			double tileMinZ = key.tileZ() * BASE_TILE_BLOCK_SPAN;
			double tileMaxZ = tileMinZ + BASE_TILE_BLOCK_SPAN;
			double marginBlocks = BASE_TILE_BLOCK_SPAN * ACTIVE_VIEW_PRIORITY_TILE_MARGIN;
			for (ActiveViewHint hint : activeViews) {
				if (hint == null) {
					continue;
				}
				double halfWidth = hint.width() * hint.blocksPerPixel() * 0.5D + marginBlocks;
				double halfHeight = hint.height() * hint.blocksPerPixel() * 0.5D + marginBlocks;
				double viewMinX = hint.centerX() - halfWidth;
				double viewMaxX = hint.centerX() + halfWidth;
				double viewMinZ = hint.centerZ() - halfHeight;
				double viewMaxZ = hint.centerZ() + halfHeight;
				if (tileMaxX > viewMinX && tileMinX < viewMaxX && tileMaxZ > viewMinZ && tileMinZ < viewMaxZ) {
					return true;
				}
			}
			return false;
		}

		private boolean tileWasRenderedBefore(TileKey key) {
			if (key == null) {
				return false;
			}
			TileImage cached = this.tileLookup.get(key);
			if (cached != null && cached.valid()) {
				return true;
			}
			if (this.missingTiles.contains(key)) {
				return false;
			}
			Path path = tilePath(this.worldKey, key);
			return path != null && Files.isRegularFile(path);
		}

		private void clearDirtyMarkerIfCovered(TileKey key, long freshnessCutoffMillis) {
			if (key == null) {
				return;
			}
			this.dirtyAfter.compute(key, (ignored, dirtyAt) -> dirtyAt == null || dirtyAt <= freshnessCutoffMillis ? null : dirtyAt);
			if (key.lod() != MIN_LOD) {
				return;
			}
			synchronized (LOCK) {
				DirtyTileState pending = this.pendingBaseTiles.get(key);
				if (pending != null && pending.lastMarkedAt() <= freshnessCutoffMillis) {
					this.pendingBaseTiles.remove(key);
				}
			}
		}

		private Long remainingDirtyAt(TileKey key, long freshnessCutoffMillis) {
			Long dirtyAt = this.dirtyAfter.get(key);
			return dirtyAt != null && dirtyAt > freshnessCutoffMillis ? dirtyAt : null;
		}
	}

	private record DirtyTileState(long firstMarkedAt, long lastMarkedAt, boolean discoveryOnly, boolean renderedBefore) {
	}

	private record PendingBaseTile(TileKey key, boolean discoveryOnly, boolean activeView, int priorityScore) {
	}

	private record ActiveViewHint(
			WorldCacheKey worldKey,
			double centerX,
			double centerZ,
			int width,
			int height,
			double blocksPerPixel,
			long expiresAtMillis
	) {
	}

	private static Path tilePath(WorldCacheKey worldKey, TileKey key) {
		Path root = persistentRoot;
		if (root == null || worldKey == null || worldKey.dimension() == null || key == null) {
			return null;
		}
		String dimension = sanitizePathPart(worldKey.dimension().identifier().toString());
		return root.resolve(dimension)
				.resolve("lod-" + key.lod())
				.resolve(Long.toString(Math.floorDiv(key.tileX(), 256L)))
				.resolve(key.tileX() + "_" + key.tileZ() + ".bin");
	}

	private static String sanitizePathPart(String raw) {
		if (raw == null || raw.isBlank()) {
			return "unknown";
		}
		return raw.replaceAll("[^A-Za-z0-9._-]+", "_");
	}

	private static int averageRgb(int[] colors, int count) {
		if (colors == null || count <= 0) {
			return MISSING_RGB;
		}
		int red = 0;
		int green = 0;
		int blue = 0;
		for (int i = 0; i < Math.min(count, colors.length); i++) {
			int rgb = colors[i];
			red += (rgb >> 16) & 0xFF;
			green += (rgb >> 8) & 0xFF;
			blue += rgb & 0xFF;
		}
		int safeCount = Math.min(count, colors.length);
		return ((red / safeCount) << 16) | ((green / safeCount) << 8) | (blue / safeCount);
	}

	private static void writeRgb(byte[] pixels, int pixelIndex, int rgb) {
		if (pixels == null || pixelIndex < 0) {
			return;
		}
		int offset = pixelIndex * RGB_BYTES_PER_PIXEL;
		if (offset + 2 >= pixels.length) {
			return;
		}
		pixels[offset] = (byte) ((rgb >> 16) & 0xFF);
		pixels[offset + 1] = (byte) ((rgb >> 8) & 0xFF);
		pixels[offset + 2] = (byte) (rgb & 0xFF);
	}

	private static void notifyReady(Runnable onTileReady) {
		if (onTileReady == null) {
			return;
		}
		try {
			onTileReady.run();
		} catch (RuntimeException ignored) {
		}
	}

	private static final class TileRequestBudget {
		private final int max;
		private int used;

		private TileRequestBudget(int max) {
			this.max = Math.max(0, max);
		}

		private boolean hasRemaining() {
			return this.used < this.max;
		}

		private boolean tryConsume() {
			if (!hasRemaining()) {
				return false;
			}
			this.used++;
			return true;
		}

		private int used() {
			return this.used;
		}
	}
}
