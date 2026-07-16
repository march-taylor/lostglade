package com.lostglade.server;

import com.lostglade.Lg2;
import net.minecraft.core.SectionPos;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
	private static final int RENDERER_BOT_MAP_TILE_PRIORITY_SCORE = 0;
	private static final int VISIBLE_DISCOVERY_CHUNK_SCAN_LIMIT = Math.max(16, Integer.getInteger("lg2.yandexMapVisibleDiscoveryChunkScanLimit", 512));
	private static final long BACKGROUND_REFRESH_INTERVAL_TICKS = 20L;
	private static final int BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MIN = Math.max(1, Integer.getInteger("lg2.yandexMapBackgroundBaseTileRequestsPerPassMin", 2));
	private static final int BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MAX = Math.max(BACKGROUND_BASE_TILE_REQUESTS_PER_PASS_MIN, Integer.getInteger("lg2.yandexMapBackgroundBaseTileRequestsPerPassMax", 4));
	private static final int BACKGROUND_DISCOVERY_BACKLOG_THRESHOLD = Math.max(128, Integer.getInteger("lg2.yandexMapBackgroundDiscoveryBacklogThreshold", 1_024));
	private static final long BACKGROUND_STALE_BACKLOG_MS = 90L * 60_000L;
	private static final int MAX_DIRTY_BASE_TILES_PER_DIMENSION = 32_768;
	private static final long BASE_TILE_REFRESH_MS = 30L * 60_000L;
	private static final long PROGRESS_LOG_INTERVAL_MS = 60_000L;
	// A base tile can make several ancestors dirty in a burst.  Coalescing the
	// work briefly turns those bursts into one downsample per LOD key instead of
	// repeatedly rebuilding the same 128x128 image.
	private static final long LOD_BUILD_COALESCE_MS = Math.max(0L, Long.getLong("lg2.yandexMapLodBuildCoalesceMs", 125L));
	// LOD synthesis is independent of the game thread.  Keep two cores for
	// ticking/networking, then use up to eight low-priority workers so distant
	// zoom levels catch up alongside base-tile captures.  Servers can tune this
	// with -Dlg2.yandexMapLodBuildThreads=N.
	private static final int LOD_BUILD_THREADS = Mth.clamp(
			Integer.getInteger(
					"lg2.yandexMapLodBuildThreads",
					Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 2))
			),
			1,
			8
	);
	private static final ExecutorService LOD_BUILD_EXECUTOR = Executors.newFixedThreadPool(LOD_BUILD_THREADS, runnable -> {
		Thread thread = new Thread(runnable, "lg2-yandex-map-lod");
		thread.setDaemon(true);
		thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
		return thread;
	});
	private static final String CACHE_DIR_NAME = "lg2-yandex-map-client-tiles";
	private static final int MISSING_RGB = 0x18242B;
	private static final Object LOCK = new Object();
	private static final Map<WorldCacheKey, DimensionTileCache> CACHES = new LinkedHashMap<>(8, 0.75F, true);
	private static volatile Path persistentRoot;
	// These immutable snapshots are published by the server thread in
	// configure().  The monitor compositor runs on a separate executor and
	// must not read MinecraftServer/ServerLevel state just to find its cache.
	private static volatile Path configuredWorldRoot;
	private static volatile boolean rendererBotReady;

	private MonitorYandexMapsClientTileRenderer() {
	}

	static void configure(MinecraftServer server) {
		if (server == null) {
			return;
		}
		Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		configuredWorldRoot = worldRoot;
		persistentRoot = worldRoot.resolve("data").resolve(CACHE_DIR_NAME);
	}

	static void tick(MinecraftServer server) {
		if (server == null) {
			rendererBotReady = false;
			return;
		}
		configure(server);
		rendererBotReady = RendererBotCameraSystem.hasReadyBot(server);
		if (!rendererBotReady || Math.floorMod(server.getTickCount(), BACKGROUND_REFRESH_INTERVAL_TICKS) != 0) {
			return;
		}
		List<DimensionTileCache> caches;
		synchronized (LOCK) {
			if (CACHES.isEmpty()) {
				return;
			}
			caches = new ArrayList<>(CACHES.values());
		}
		caches.removeIf(cache -> cache == null || !isMapDimension(cache.worldKey.dimension()));
		if (caches.isEmpty()) {
			return;
		}
		for (DimensionTileCache cache : caches) {
			cache.releaseStartupDeferredBaseTiles();
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
		boolean progressed;
		do {
			progressed = false;
			for (DimensionTileCache cache : caches) {
				if (cache == null || !budget.hasRemaining()) {
					break;
				}
				progressed |= cache.refreshDirtyBaseTiles(server, budget);
			}
		} while (progressed && budget.hasRemaining());
		for (DimensionTileCache cache : caches) {
			cache.logProgressIfDue();
		}
	}

	static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null || !isMapDimension(level.dimension())) {
			return;
		}
		markChunkDiscovered(level, chunk.getPos());
	}

	static void markChunkDirty(ServerLevel level, ChunkPos pos) {
		markChunkDirty(level, pos, false);
	}

	static void deactivateView(ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		synchronized (LOCK) {
			for (DimensionTileCache cache : CACHES.values()) {
				if (cache != null) {
					cache.removeActiveView(key);
				}
			}
		}
	}

	static void clear(ResourceKey<Level> dimension) {
		synchronized (LOCK) {
			if (dimension == null) {
				CACHES.clear();
			} else {
				CACHES.keySet().removeIf(key -> dimension.equals(key.dimension()));
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
		clear(null);
	}

	private static void markChunkDirty(ServerLevel level, ChunkPos pos, boolean discoveryOnly) {
		if (level == null || pos == null || !isMapDimension(level.dimension())) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}
		configure(server);
		WorldCacheKey worldKey = worldCacheKey(server, level);
		if (worldKey != null) {
			cacheFor(worldKey).markChunkDirty(pos, System.currentTimeMillis(), discoveryOnly);
		}
	}

	private static void markChunkDiscovered(ServerLevel level, ChunkPos pos) {
		if (level == null || pos == null || !isMapDimension(level.dimension())) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}
		configure(server);
		WorldCacheKey worldKey = worldCacheKey(server, level);
		if (worldKey != null) {
			cacheFor(worldKey).markChunkDiscovered(pos, System.currentTimeMillis());
		}
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
			ResourceKey<Level> dimension,
			double centerX,
			double centerZ,
			int width,
			int height,
			double blocksPerPixel,
			Runnable onTileReady,
			ScreenRuntimeKey activeViewKey
	) {
		if (server == null || dimension == null || width <= 0 || height <= 0 || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
			return Frame.failure(null, "Карта недоступна");
		}
		if (!isMapDimension(dimension)) {
			return Frame.failure(null, "Карта доступна только в верхнем мире");
		}
		WorldCacheKey worldKey = configuredWorldCacheKey(dimension);
		if (worldKey == null) {
			return Frame.failure(null, "Карта инициализируется");
		}
		double safeBlocksPerPixel = snapBlocksPerPixel(blocksPerPixel);
		int lod = lodForBlocksPerPixel(safeBlocksPerPixel);
		double tileBlocksPerPixel = blocksPerPixelForLod(lod);
		double worldLeft = centerX - width * safeBlocksPerPixel * 0.5D;
		double worldTop = centerZ - height * safeBlocksPerPixel * 0.5D;
		DimensionTileCache cache = cacheFor(worldKey);
		cache.registerActiveView(activeViewKey, onTileReady);
		List<TileKey> visibleTiles = visibleTiles(lod, tileBlocksPerPixel, worldLeft, worldTop, width, height, safeBlocksPerPixel, centerX, centerZ);
		// render() is called from MonitorScreenSystem's render executor.  Only
		// publish its immutable viewport here; discovery, chunk tickets and bot
		// requests are drained on the server thread below.
		cache.enqueueVisibleWork(server, new VisibleWork(
				activeViewKey,
				worldLeft,
				worldTop,
				width,
				height,
				safeBlocksPerPixel,
				List.copyOf(visibleTiles),
				onTileReady
		));
		// A screen can have hundreds of thousands of pixels but usually only a
		// handful of tiles. Resolve those tiles once, then keep the compositor
		// entirely lock-free and free of repeated disk/missing-cache probes.
		Map<TileKey, TileImage> frameTiles = cache.snapshotTiles(visibleTiles);

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int missingPixels = 0;
		for (int screenY = 0; screenY < height; screenY++) {
			double worldZ = worldTop + (screenY + 0.5D) * safeBlocksPerPixel;
			int row = screenY * width;
			for (int screenX = 0; screenX < width; screenX++) {
				double worldX = worldLeft + (screenX + 0.5D) * safeBlocksPerPixel;
				int rgb = cache.sampleSnapshot(frameTiles, worldX, worldZ, lod);
				if (rgb < 0) {
					rgb = MISSING_RGB;
					missingPixels++;
				}
				pixels[row + screenX] = 0xFF000000 | rgb;
			}
		}
		String status;
		boolean needsCamera = missingPixels > 0 || cache.pendingBaseTileCount() > 0;
		if (!rendererBotReady && needsCamera) {
			status = "Нет клиента камеры";
		} else if (needsCamera) {
			status = "Очередь карты: " + cache.pendingBaseTileCount() + " тайл.";
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

	static boolean isMapDimension(ResourceKey<Level> dimension) {
		return Level.OVERWORLD.equals(dimension);
	}

	static int rendererBotMapTilePriorityScore(int internalPriorityScore) {
		// This value orders only map-tile work on the renderer client.  It is not
		// used to raise the priority of the renderer bot's other camera work.
		return Math.max(RENDERER_BOT_MAP_TILE_PRIORITY_SCORE, internalPriorityScore);
	}

	static boolean rendererBotMapTileActiveView(boolean activeView) {
		return false;
	}

	private static DimensionTileCache cacheFor(WorldCacheKey key) {
		synchronized (LOCK) {
			return CACHES.computeIfAbsent(key, ignored -> new DimensionTileCache(key));
		}
	}

	private static WorldCacheKey worldCacheKey(MinecraftServer server, ServerLevel level) {
		return server == null || level == null ? null : worldCacheKey(server, level.dimension());
	}

	private static WorldCacheKey worldCacheKey(MinecraftServer server, ResourceKey<Level> dimension) {
		if (server == null || dimension == null) {
			return null;
		}
		return new WorldCacheKey(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), dimension);
	}

	private static WorldCacheKey configuredWorldCacheKey(ResourceKey<Level> dimension) {
		Path worldRoot = configuredWorldRoot;
		return worldRoot == null || dimension == null ? null : new WorldCacheKey(worldRoot, dimension);
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

	record Frame(BufferedImage image, String status, boolean healthy) {
		static Frame failure(BufferedImage image, String status) {
			return new Frame(image, status, false);
		}
	}

	private record WorldCacheKey(Path root, ResourceKey<Level> dimension) {
	}

	private record TileKey(int lod, long tileX, long tileZ) {
	}

	private enum TileBuildResult {
		NONE,
		PARTIAL,
		COMPLETE
	}

	private record TileImage(byte[] pixels, long renderedAt, long availableAt, boolean complete, boolean needsCaptureVerification) {
		private TileImage(byte[] pixels, long renderedAt) {
			this(pixels, renderedAt, renderedAt, true, false);
		}

		private TileImage(byte[] pixels, long renderedAt, boolean complete) {
			this(pixels, renderedAt, renderedAt, complete, false);
		}

		private TileImage(byte[] pixels, long renderedAt, long availableAt, boolean complete) {
			this(pixels, renderedAt, availableAt, complete, false);
		}

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

	private static boolean isUniformRgbFrame(byte[] pixels) {
		if (pixels == null || pixels.length < RGB_BYTES_PER_PIXEL) {
			return false;
		}
		byte red = pixels[0];
		byte green = pixels[1];
		byte blue = pixels[2];
		for (int offset = RGB_BYTES_PER_PIXEL; offset + 2 < pixels.length; offset += RGB_BYTES_PER_PIXEL) {
			if (pixels[offset] != red || pixels[offset + 1] != green || pixels[offset + 2] != blue) {
				return false;
			}
		}
		return true;
	}

	private static final class DimensionTileCache {
		private final WorldCacheKey worldKey;
		private final LinkedHashMap<TileKey, TileImage> tiles = new LinkedHashMap<>(512, 0.75F, true);
		private final Map<TileKey, TileImage> tileLookup = new ConcurrentHashMap<>();
		private final Set<TileKey> missingTiles = ConcurrentHashMap.newKeySet();
		private final Set<TileKey> inFlight = ConcurrentHashMap.newKeySet();
		// A lower-priority refresh must not slip through merely because every new
		// tile is currently waiting on the renderer bot or on its retry backoff.
		private final Set<TileKey> inFlightNewBaseTiles = ConcurrentHashMap.newKeySet();
		// Startup-scene chunks are remembered outside the priority queue.  Keeping
		// them in pendingBaseTiles would make their central position win every
		// poll and starve the real saved terrain until the scene ends.
		private final Map<TileKey, PendingBaseTile> startupDeferredBaseTiles = new ConcurrentHashMap<>();
		private final Object lodBuildLock = new Object();
		private final Set<TileKey> lodBuildsInFlight = ConcurrentHashMap.newKeySet();
		private final Set<TileKey> lodBuildsDirty = ConcurrentHashMap.newKeySet();
		// Every saved base tile registers its small parent chain here.  It lets a
		// visible coarse tile request only the branches which actually contain
		// world data, instead of recursively probing all 4^lod possible leaves.
		private final Set<TileKey> knownLodCoverage = ConcurrentHashMap.newKeySet();
		// This is a read-only inventory of chunk locations actually present in
		// MCA region files (plus chunks observed loaded by the server).  Map
		// captures receive only these source chunks; they must never ask the
		// normal server chunk loader to create terrain for a visual tile.
		private final Set<Long> knownSavedChunks = ConcurrentHashMap.newKeySet();
		private final Map<TileKey, Long> dirtyAfter = new ConcurrentHashMap<>();
		private final Map<TileKey, Long> retryAfter = new ConcurrentHashMap<>();
		private final Map<TileKey, Integer> failedAttempts = new ConcurrentHashMap<>();
		private final LinkedHashMap<TileKey, DirtyTileState> pendingBaseTiles = new LinkedHashMap<>();
		private final Set<ChunkPos> deferredChunkDiscoveries = ConcurrentHashMap.newKeySet();
		private final Map<ScreenRuntimeKey, Runnable> activeViewCallbacks = new ConcurrentHashMap<>();
		// The screen compositor may render several monitors concurrently.  Keep
		// only each monitor's newest viewport, then consume that compact batch on
		// the server thread.  This is the boundary between thread-safe image/cache
		// sampling and all Minecraft world/network access.
		private final Object visibleWorkLock = new Object();
		private final Map<ScreenRuntimeKey, VisibleWork> pendingVisibleWorkByView = new LinkedHashMap<>();
		private VisibleWork pendingAnonymousVisibleWork;
		private boolean visibleWorkDrainQueued;
		private volatile boolean savedTileQueueInitialized;
		private final AtomicLong savedChunksDiscovered = new AtomicLong();
		private final AtomicLong newBaseTilesQueued = new AtomicLong();
		private final AtomicLong newBaseTilesRendered = new AtomicLong();
		private volatile long lastProgressLogAt;

		private DimensionTileCache(WorldCacheKey worldKey) {
			this.worldKey = worldKey;
			this.savedTileQueueInitialized = false;
			CompletableFuture.runAsync(this::discoverSavedBaseTiles);
		}

		private void registerActiveView(ScreenRuntimeKey key, Runnable onTileReady) {
			if (key != null && onTileReady != null) {
				this.activeViewCallbacks.put(key, onTileReady);
			}
		}

		private void removeActiveView(ScreenRuntimeKey key) {
			if (key != null) {
				this.activeViewCallbacks.remove(key);
				synchronized (this.visibleWorkLock) {
					this.pendingVisibleWorkByView.remove(key);
				}
			}
		}

		private void notifyActiveViews() {
			for (Runnable callback : List.copyOf(this.activeViewCallbacks.values())) {
				notifyReady(callback);
			}
		}

		private void enqueueVisibleWork(MinecraftServer server, VisibleWork work) {
			if (server == null || work == null) {
				return;
			}
			boolean scheduleDrain = false;
			synchronized (this.visibleWorkLock) {
				if (work.viewKey() == null) {
					this.pendingAnonymousVisibleWork = work;
				} else {
					this.pendingVisibleWorkByView.put(work.viewKey(), work);
				}
				if (!this.visibleWorkDrainQueued) {
					this.visibleWorkDrainQueued = true;
					scheduleDrain = true;
				}
			}
			if (scheduleDrain) {
				server.execute(() -> drainVisibleWork(server));
			}
		}

		private void drainVisibleWork(MinecraftServer server) {
			List<VisibleWork> workItems;
			synchronized (this.visibleWorkLock) {
				workItems = new ArrayList<>(this.pendingVisibleWorkByView.values());
				this.pendingVisibleWorkByView.clear();
				if (this.pendingAnonymousVisibleWork != null) {
					workItems.add(this.pendingAnonymousVisibleWork);
					this.pendingAnonymousVisibleWork = null;
				}
			}
			try {
				if (server == null || workItems.isEmpty()) {
					return;
				}
				ServerLevel level = server.getLevel(this.worldKey.dimension());
				if (level == null) {
					return;
				}
				releaseStartupDeferredBaseTiles();
				List<Runnable> readyCallbacks = new ArrayList<>(workItems.size());
				for (VisibleWork work : workItems) {
					if (work == null) {
						continue;
					}
					discoverLoadedViewportBaseTiles(
							level,
							work.worldLeft(),
							work.worldTop(),
							work.width(),
							work.height(),
							work.blocksPerPixel(),
							System.currentTimeMillis()
					);
					buildVisibleTilesFromAvailableChildren(work.visibleTiles(), work.onTileReady());
					if (work.onTileReady() != null) {
						readyCallbacks.add(work.onTileReady());
					}
				}
				Runnable readyCallback = readyCallbacks.isEmpty() ? null : () -> {
					for (Runnable callback : readyCallbacks) {
						notifyReady(callback);
					}
				};
				TileRequestBudget budget = new TileRequestBudget(MAX_BASE_TILE_REQUESTS_PER_FRAME);
				boolean progressed;
				do {
					progressed = refreshDirtyBaseTiles(server, budget, readyCallback);
				} while (progressed && budget.hasRemaining());
			} finally {
				boolean reschedule;
				synchronized (this.visibleWorkLock) {
					reschedule = this.pendingAnonymousVisibleWork != null || !this.pendingVisibleWorkByView.isEmpty();
					if (!reschedule) {
						this.visibleWorkDrainQueued = false;
					}
				}
				if (reschedule && server != null) {
					server.execute(() -> drainVisibleWork(server));
				}
			}
		}

		private boolean hasUsableTile(TileKey key) {
			TileImage image = imageFor(key);
			return image != null && image.valid();
		}

		private boolean needsTileCompletion(TileKey key) {
			TileImage image = imageFor(key);
			return image == null || !image.valid() || !image.complete();
		}

		private boolean refreshDirtyBaseTiles(MinecraftServer server, TileRequestBudget budget) {
			return refreshDirtyBaseTiles(server, budget, null);
		}

		private boolean refreshDirtyBaseTiles(MinecraftServer server, TileRequestBudget budget, Runnable onTileReady) {
			if (server == null || budget == null || !budget.hasRemaining()) {
				return false;
			}
			ServerLevel level = server.getLevel(this.worldKey.dimension());
			if (level == null) {
				return false;
			}
			PendingBaseTile pending = pollNextPendingBaseTile();
			if (pending == null) {
				return false;
			}
			TileKey key = pending.key();
			if (key == null) {
				return false;
			}
			if (shouldDeferStartupBaseTile(key)) {
				deferStartupBaseTile(pending);
				return true;
			}
			if (this.inFlight.contains(key)) {
				queuePendingBaseTile(key, this.dirtyAfter.getOrDefault(key, System.currentTimeMillis()), pending.discoveryOnly(), pending.countedAsNew());
				return false;
			}
			if (hasUsableTile(key) && !isStale(key)) {
				this.retryAfter.remove(key);
				this.failedAttempts.remove(key);
				if (pending.countedAsNew()) {
					this.newBaseTilesRendered.incrementAndGet();
				}
				clearDirtyMarkerIfCovered(key, System.currentTimeMillis());
				return true;
			}
			int usedBefore = budget.used();
			requestTile(server, level, key, budget, onTileReady, pending, false);
			if (budget.used() == usedBefore) {
				queuePendingBaseTile(key, this.dirtyAfter.getOrDefault(key, System.currentTimeMillis()), pending.discoveryOnly(), pending.countedAsNew());
				return false;
			}
			return true;
		}

		private void requestTile(MinecraftServer server, ServerLevel level, TileKey key, TileRequestBudget budget, Runnable onTileReady, PendingBaseTile pending, boolean activeView) {
			if (server == null || level == null || key == null || budget == null || pending == null) {
				return;
			}
			if (key.lod() > DIRECT_RENDER_MAX_LOD) {
				scheduleLodBuild(key, onTileReady);
				return;
			}
			if (hasUsableTile(key) && !isStale(key)) {
				return;
			}
			List<ChunkPos> sourceChunks = savedChunkSourcesForBaseTile(key);
			if (sourceChunks.isEmpty()) {
				// The inventory can only shrink if a world file was changed outside
				// the running server.  Leave the tile pending rather than ever
				// turning an absent location into freshly generated terrain.
				return;
			}
			if (!budget.tryConsume() || !this.inFlight.add(key)) {
				return;
			}
			if (!pending.renderedBefore()) {
				this.inFlightNewBaseTiles.add(key);
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
					sourceChunks,
					rendererBotMapTilePriorityScore(pending.priorityScore()),
					rendererBotMapTileActiveView(activeView)
			);
			future.whenComplete((pixels, throwable) -> {
				try {
					if (throwable != null) {
						Lg2.LOGGER.debug("Yandex map tile render failed at lod {} {},{}: {}", key.lod(), key.tileX(), key.tileZ(), throwable.toString());
						requeueFailedBaseTile(pending);
						return;
					}
					if (pixels == null || pixels.length < TILE_RGB_BYTES) {
						requeueFailedBaseTile(pending);
						return;
					}
					long completedAt = System.currentTimeMillis();
					TileImage image = new TileImage(Arrays.copyOf(pixels, TILE_RGB_BYTES), requestStartedAt, completedAt, true);
					storeTile(key, image, requestStartedAt);
					this.retryAfter.remove(key);
					this.failedAttempts.remove(key);
					if (pending.countedAsNew()) {
						this.newBaseTilesRendered.incrementAndGet();
					}
					rebuildAncestors(key, onTileReady);
					Long remainingDirtyAt = remainingDirtyAt(key, requestStartedAt);
					if (remainingDirtyAt != null && key.lod() == MIN_LOD) {
						queuePendingBaseTile(key, remainingDirtyAt, false);
					}
				} finally {
					this.inFlight.remove(key);
					this.inFlightNewBaseTiles.remove(key);
					notifyReady(onTileReady);
					notifyActiveViews();
				}
			});
		}

		private void requeueFailedBaseTile(PendingBaseTile pending) {
			if (pending == null || pending.key() == null || pending.key().lod() != MIN_LOD) {
				return;
			}
			TileKey key = pending.key();
			int attempt = this.failedAttempts.merge(key, 1, Integer::sum);
			long retryDelay = Math.min(30_000L, 1_000L << Math.min(5, Math.max(0, attempt - 1)));
			long now = System.currentTimeMillis();
			this.retryAfter.put(key, now + retryDelay);
			queuePendingBaseTile(key, now, pending.discoveryOnly(), pending.countedAsNew());
		}

		private boolean isStale(TileKey key) {
			TileImage image = imageFor(key);
			return isStale(key, image, System.currentTimeMillis());
		}

		private boolean isStale(TileKey key, TileImage image, long now) {
			if (image == null || !image.valid()) {
				return true;
			}
			if (image.needsCaptureVerification()) {
				return true;
			}
			if (!image.complete()) {
				return true;
			}
			return isOutdated(key, image, now);
		}

		private boolean isOutdated(TileKey key, TileImage image, long now) {
			Long dirtyAt = this.dirtyAfter.get(key);
			return (dirtyAt != null && image.renderedAt() < dirtyAt)
					|| now - image.availableAt() > BASE_TILE_REFRESH_MS;
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

		private Map<TileKey, TileImage> snapshotTiles(List<TileKey> keys) {
			if (keys == null || keys.isEmpty()) {
				return Map.of();
			}
			Map<TileKey, TileImage> snapshot = new HashMap<>(keys.size());
			for (TileKey key : keys) {
				if (key == null) {
					continue;
				}
				TileImage image = imageFor(key);
				if (image != null && image.valid()) {
					snapshot.put(key, image);
				}
			}
			return snapshot.isEmpty() ? Map.of() : Map.copyOf(snapshot);
		}

		private int sampleSnapshot(Map<TileKey, TileImage> snapshot, double worldX, double worldZ, int lod) {
			if (snapshot == null || snapshot.isEmpty() || lod < MIN_LOD || lod > MAX_LOD) {
				return -1;
			}
			TileKey key = tileKeyAt(lod, worldX, worldZ);
			TileImage image = snapshot.get(key);
			if (image == null || !image.valid()) {
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

		private TileBuildResult buildFromChildren(TileKey key, long buildStartedAt) {
			if (key == null || key.lod() <= MIN_LOD) {
				return TileBuildResult.NONE;
			}
			TileImage[] children = new TileImage[4];
			int availableChildren = 0;
			boolean complete = true;
			int index = 0;
			for (TileKey child : childKeys(key)) {
				TileImage image = imageFor(child);
				if (image == null || !image.valid()) {
					// Once the region header scan has completed, an absent branch that
					// is not in the coverage index is known-empty, not "still loading".
					// That lets sparse but stable far LODs become persistable without
					// writing transient partial tiles to disk.
					if (!this.savedTileQueueInitialized || this.knownLodCoverage.contains(child)) {
						complete = false;
					}
				} else {
					children[index] = image;
					availableChildren++;
					if (!image.complete() || isOutdated(child, image, buildStartedAt)) {
						complete = false;
					}
				}
				index++;
			}
			if (availableChildren <= 0) {
				return TileBuildResult.NONE;
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
							if (child != null) {
								colors[count++] = child.sample(combinedX & (TILE_SIZE - 1), combinedY & (TILE_SIZE - 1));
							}
						}
					}
					writeRgb(pixels, y * TILE_SIZE + x, averageRgb(colors, count));
				}
			}
			TileImage existing = this.tileLookup.get(key);
			if (complete || existing == null || !existing.valid() || !existing.complete()) {
				storeTile(key, new TileImage(pixels, buildStartedAt, complete), buildStartedAt);
			}
			return complete ? TileBuildResult.COMPLETE : TileBuildResult.PARTIAL;
		}

		private void rebuildAncestors(TileKey key, Runnable onTileReady) {
			scheduleLodBuild(parentKey(key), onTileReady);
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

		private boolean cacheTile(TileKey key, TileImage image, boolean persist, long freshnessCutoffMillis) {
			if (key == null || image == null || !image.valid()) {
				return false;
			}
			synchronized (LOCK) {
				TileImage existing = this.tileLookup.get(key);
				// A background partial LOD must never replace a complete tile which
				// finished while that background task was sampling its children.
				// Keeping the older complete image is preferable to making a far zoom
				// regress into a hole; the next complete build will replace it.
				if (!image.complete() && existing != null && existing.valid() && existing.complete()) {
					return false;
				}
				if (image.complete()
						&& existing != null
						&& existing.valid()
						&& existing.complete()
						&& (existing.renderedAt() > image.renderedAt()
								|| (existing.renderedAt() == image.renderedAt() && existing.availableAt() >= image.availableAt()))) {
					return false;
				}
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
			if (image.complete()) {
				clearDirtyMarkerIfCovered(key, freshnessCutoffMillis);
			}
			if (persist && image.complete()) {
				persistTile(key, image);
			}
			return true;
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
			if (!isStoredTileFileUsable(path)) {
				return null;
			}
			try {
				byte[] pixels = Files.readAllBytes(path);
				if (pixels.length < TILE_RGB_BYTES) {
					return null;
				}
				long modifiedAt = Files.getLastModifiedTime(path).toMillis();
				byte[] frame = Arrays.copyOf(pixels, TILE_RGB_BYTES);
				// The legacy raw cache has no capture-validity metadata. Verify an
				// exactly uniform base frame once with the depth-aware client path:
				// it might be sky, but may equally be a real flat block/display.
				boolean needsCaptureVerification = key.lod() == MIN_LOD && isUniformRgbFrame(frame);
				return new TileImage(frame, modifiedAt, modifiedAt, true, needsCaptureVerification);
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
				Files.setLastModifiedTime(path, FileTime.fromMillis(Math.max(0L, image.availableAt())));
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

		private void discoverLoadedViewportBaseTiles(
				ServerLevel level,
				double worldLeft,
				double worldTop,
				int width,
				int height,
				double blocksPerPixel,
				long discoveredAt
		) {
			if (level == null
					|| width <= 0
					|| height <= 0
					|| !Double.isFinite(worldLeft)
					|| !Double.isFinite(worldTop)
					|| !Double.isFinite(blocksPerPixel)
					|| blocksPerPixel <= 0.0D) {
				return;
			}
			int minBlockX = Mth.floor(worldLeft);
			int maxBlockX = Mth.floor(worldLeft + width * blocksPerPixel - 1.0E-6D);
			int minBlockZ = Mth.floor(worldTop);
			int maxBlockZ = Mth.floor(worldTop + height * blocksPerPixel - 1.0E-6D);
			int minChunkX = SectionPos.blockToSectionCoord(minBlockX);
			int maxChunkX = SectionPos.blockToSectionCoord(maxBlockX);
			int minChunkZ = SectionPos.blockToSectionCoord(minBlockZ);
			int maxChunkZ = SectionPos.blockToSectionCoord(maxBlockZ);
			if (maxChunkX < minChunkX || maxChunkZ < minChunkZ) {
				return;
			}
			Set<ChunkPos> chunks = discoverLoadedChunksNearWorldCenter(level, minChunkX, maxChunkX, minChunkZ, maxChunkZ);
			for (ChunkPos chunk : chunks) {
				markChunkDiscovered(chunk, discoveredAt);
			}
		}

		private Set<ChunkPos> discoverLoadedChunksNearWorldCenter(ServerLevel level, int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
			Set<ChunkPos> chunks = new HashSet<>();
			int centerChunkX = Mth.clamp(0, minChunkX, maxChunkX);
			int centerChunkZ = Mth.clamp(0, minChunkZ, maxChunkZ);
			long chunkColumns = (long) maxChunkX - minChunkX + 1L;
			long chunkRows = (long) maxChunkZ - minChunkZ + 1L;
			if (chunkColumns <= VISIBLE_DISCOVERY_CHUNK_SCAN_LIMIT
					&& chunkRows <= VISIBLE_DISCOVERY_CHUNK_SCAN_LIMIT
					&& chunkColumns <= VISIBLE_DISCOVERY_CHUNK_SCAN_LIMIT / Math.max(1L, chunkRows)) {
				for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
					for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
						addLoadedDiscoveryChunk(level, chunks, chunkX, chunkZ);
					}
				}
				return chunks;
			}
			int maxRadius = Math.max(
					Math.max(Math.abs(centerChunkX - minChunkX), Math.abs(centerChunkX - maxChunkX)),
					Math.max(Math.abs(centerChunkZ - minChunkZ), Math.abs(centerChunkZ - maxChunkZ))
			);
			int checked = 0;
			for (int radius = 0; radius <= maxRadius && checked < VISIBLE_DISCOVERY_CHUNK_SCAN_LIMIT; radius++) {
				for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius && checked < VISIBLE_DISCOVERY_CHUNK_SCAN_LIMIT; chunkZ++) {
					for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius && checked < VISIBLE_DISCOVERY_CHUNK_SCAN_LIMIT; chunkX++) {
						if (radius > 0 && chunkX > centerChunkX - radius && chunkX < centerChunkX + radius && chunkZ > centerChunkZ - radius && chunkZ < centerChunkZ + radius) {
							continue;
						}
						if (chunkX < minChunkX || chunkX > maxChunkX || chunkZ < minChunkZ || chunkZ > maxChunkZ) {
							continue;
						}
						checked++;
						addLoadedDiscoveryChunk(level, chunks, chunkX, chunkZ);
					}
				}
			}
			return chunks;
		}

		private void addLoadedDiscoveryChunk(ServerLevel level, Set<ChunkPos> chunks, int chunkX, int chunkZ) {
			if (level != null && chunks != null && level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
				chunks.add(new ChunkPos(chunkX, chunkZ));
			}
		}

		private void buildVisibleTilesFromAvailableChildren(List<TileKey> visibleTiles, Runnable onTileReady) {
			if (visibleTiles == null || visibleTiles.isEmpty()) {
				return;
			}
			for (TileKey key : visibleTiles) {
				if (key != null && key.lod() > MIN_LOD && needsTileCompletion(key)) {
					scheduleLodBuild(key, onTileReady);
				}
			}
		}

		private void scheduleLodBuild(TileKey key, Runnable onTileReady) {
			if (key == null || key.lod() <= MIN_LOD) {
				return;
			}
			synchronized (this.lodBuildLock) {
				if (!this.lodBuildsInFlight.add(key)) {
					this.lodBuildsDirty.add(key);
					return;
				}
			}
			CompletableFuture.runAsync(() -> {
				try {
					TileBuildResult result = buildFromChildren(key, System.currentTimeMillis());
					if (result != TileBuildResult.COMPLETE) {
						// A cached partial parent must keep asking its known descendants
						// to improve.  Without this, eviction of an intermediate LOD made
						// a far-away map turn black again until the user zoomed in.
						scheduleMissingKnownLodChildren(key, onTileReady);
					}
					if (result != TileBuildResult.NONE) {
						notifyReady(onTileReady);
						notifyActiveViews();
						scheduleLodBuild(parentKey(key), onTileReady);
					}
				} finally {
					boolean requeue;
					synchronized (this.lodBuildLock) {
						this.lodBuildsInFlight.remove(key);
						requeue = this.lodBuildsDirty.remove(key);
					}
					if (requeue) {
						scheduleLodBuild(key, onTileReady);
					}
				}
			}, CompletableFuture.delayedExecutor(LOD_BUILD_COALESCE_MS, TimeUnit.MILLISECONDS, LOD_BUILD_EXECUTOR));
		}

		private void scheduleMissingKnownLodChildren(TileKey key, Runnable onTileReady) {
			if (key == null || key.lod() <= MIN_LOD) {
				return;
			}
			for (TileKey child : childKeys(key)) {
				if (child == null || child.lod() <= MIN_LOD || !this.knownLodCoverage.contains(child)) {
					continue;
				}
				TileImage image = imageFor(child);
				// A valid partial child already has all currently-known pixels.  It
				// will be rebuilt by the base-tile completion that changes it; asking
				// for it again here creates an LOD parent/child ping-pong forever.
				if (image == null || !image.valid()) {
					scheduleLodBuild(child, onTileReady);
				}
			}
		}

		private void markChunkDirty(ChunkPos pos, long dirtyAt, boolean discoveryOnly) {
			if (pos == null) {
				return;
			}
			this.knownSavedChunks.add(pos.toLong());
			if (shouldDeferStartupChunk(pos)) {
				deferStartupChunkBaseTiles(pos, discoveryOnly);
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

		private void markChunkDiscovered(ChunkPos pos, long discoveredAt) {
			if (pos == null) {
				return;
			}
			this.knownSavedChunks.add(pos.toLong());
			synchronized (LOCK) {
				// Keep discoveries that occur before the region files are fully
				// inventoried so the final merge cannot lose a just-generated chunk.
				// They are also admitted immediately below: waiting for a large MCA
				// scan used to guarantee a black map during startup.
				if (!this.savedTileQueueInitialized) {
					this.deferredChunkDiscoveries.add(pos);
				}
			}
			if (shouldDeferStartupChunk(pos)) {
				deferStartupChunkBaseTiles(pos, true);
				return;
			}
			queueChunkDiscovered(pos, discoveredAt);
		}

		private void queueChunkDiscovered(ChunkPos pos, long discoveredAt) {
			if (pos == null) {
				return;
			}
			long minTileX = floorToLong(((double) (pos.x << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileX = floorToLong(((double) ((pos.x << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			long minTileZ = floorToLong(((double) (pos.z << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileZ = floorToLong(((double) ((pos.z << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			for (long tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
				for (long tileX = minTileX; tileX <= maxTileX; tileX++) {
					TileKey key = new TileKey(MIN_LOD, tileX, tileZ);
					if (!queueMissingBaseTile(key, discoveredAt)) {
						queueLoadedStaleBaseTile(key, discoveredAt);
					}
				}
			}
		}

		private boolean shouldDeferStartupChunk(ChunkPos chunk) {
			return SeasonStartSystem.shouldDeferYandexMapChunk(this.worldKey.dimension(), chunk);
		}

		private boolean shouldDeferStartupBaseTile(TileKey key) {
			ChunkPos source = sourceChunkForBaseTile(key);
			return source != null && shouldDeferStartupChunk(source);
		}

		private void deferStartupChunkBaseTiles(ChunkPos chunk, boolean discoveryOnly) {
			if (chunk == null) {
				return;
			}
			long minTileX = floorToLong(((double) (chunk.x << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileX = floorToLong(((double) ((chunk.x << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			long minTileZ = floorToLong(((double) (chunk.z << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileZ = floorToLong(((double) ((chunk.z << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			for (long tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
				for (long tileX = minTileX; tileX <= maxTileX; tileX++) {
					TileKey key = new TileKey(MIN_LOD, tileX, tileZ);
					// Chunk discovery can happen before the MCA inventory.  Still
					// publish its coverage now, otherwise an LOD parent could be
					// incorrectly considered empty until the startup scene finishes.
					rememberKnownBaseTile(key);
					boolean renderedBefore = tileWasRenderedBefore(key);
					deferStartupBaseTile(new PendingBaseTile(
							key,
							discoveryOnly,
							renderedBefore,
							!renderedBefore,
							0
					));
				}
			}
		}

		private void deferStartupBaseTile(PendingBaseTile pending) {
			if (pending == null || pending.key() == null || pending.key().lod() != MIN_LOD) {
				return;
			}
			this.startupDeferredBaseTiles.merge(pending.key(), pending, (first, second) -> new PendingBaseTile(
					first.key(),
					first.discoveryOnly() && second.discoveryOnly(),
					first.renderedBefore() || second.renderedBefore(),
					first.countedAsNew() || second.countedAsNew(),
					Math.max(first.priorityScore(), second.priorityScore())
			));
		}

		private void releaseStartupDeferredBaseTiles() {
			if (this.startupDeferredBaseTiles.isEmpty()) {
				return;
			}
			long now = System.currentTimeMillis();
			for (Map.Entry<TileKey, PendingBaseTile> entry : List.copyOf(this.startupDeferredBaseTiles.entrySet())) {
				TileKey key = entry.getKey();
				PendingBaseTile pending = entry.getValue();
				if (key == null || pending == null || shouldDeferStartupBaseTile(key)
						|| !this.startupDeferredBaseTiles.remove(key, pending)) {
					continue;
				}
				if (!queueMissingBaseTile(key, now)) {
					queuePendingBaseTile(key, now, pending.discoveryOnly(), pending.countedAsNew());
				}
			}
		}

		private void addChunkBaseTiles(ChunkPos pos, Set<TileKey> target) {
			if (pos == null || target == null) {
				return;
			}
			long minTileX = floorToLong(((double) (pos.x << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileX = floorToLong(((double) ((pos.x << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			long minTileZ = floorToLong(((double) (pos.z << 4)) / BASE_TILE_BLOCK_SPAN);
			long maxTileZ = floorToLong(((double) ((pos.z << 4) + 15)) / BASE_TILE_BLOCK_SPAN);
			for (long tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
				for (long tileX = minTileX; tileX <= maxTileX; tileX++) {
					target.add(new TileKey(MIN_LOD, tileX, tileZ));
				}
			}
		}

		private List<ChunkPos> savedChunkSourcesForBaseTile(TileKey key) {
			if (key == null || key.lod() != MIN_LOD) {
				return List.of();
			}
			try {
				// A base tile covers 8x8 blocks, while a Minecraft chunk covers
				// 16x16.  The two grids are aligned at zero, including negative
				// coordinates, so every direct map capture belongs to exactly one
				// source chunk.
				ChunkPos source = sourceChunkForBaseTile(key);
				if (source == null) {
					return List.of();
				}
				return this.knownSavedChunks.contains(source.toLong()) ? List.of(source) : List.of();
			} catch (ArithmeticException ignored) {
				return List.of();
			}
		}

		private static ChunkPos sourceChunkForBaseTile(TileKey key) {
			if (key == null || key.lod() != MIN_LOD) {
				return null;
			}
			try {
				return new ChunkPos(
						Math.toIntExact(Math.floorDiv(key.tileX(), 2L)),
						Math.toIntExact(Math.floorDiv(key.tileZ(), 2L))
				);
			} catch (ArithmeticException ignored) {
				return null;
			}
		}

		private void markTileDirtyCascade(TileKey key, long dirtyAt, boolean discoveryOnly) {
			rememberKnownBaseTile(key);
			TileKey current = key;
			while (current != null) {
				this.dirtyAfter.merge(current, dirtyAt, Math::max);
				current = parentKey(current);
			}
			queuePendingBaseTile(key, dirtyAt, discoveryOnly);
		}

		private boolean queueMissingBaseTile(TileKey key, long discoveredAt) {
			rememberKnownBaseTile(key);
			if (key == null || key.lod() != MIN_LOD) {
				return false;
			}
			TileImage image = imageFor(key);
			boolean needsCaptureVerification = image != null && image.needsCaptureVerification();
			if ((!needsCaptureVerification && hasUsableTile(key))
					|| (!needsCaptureVerification && tileWasRenderedBefore(key))) {
				return false;
			}
			synchronized (LOCK) {
				DirtyTileState existing = this.pendingBaseTiles.get(key);
				if (existing != null) {
					// A dirty/load event may have enqueued this tile while the region
					// inventory was still running.  Preserve its history but make the
					// progress accounting reflect that it is genuinely new terrain.
					if (!existing.countedAsNew()) {
						this.pendingBaseTiles.put(
								key,
								new DirtyTileState(
										existing.firstMarkedAt(),
										Math.max(existing.lastMarkedAt(), discoveredAt),
										existing.discoveryOnly(),
										false,
										true
								)
						);
						this.newBaseTilesQueued.incrementAndGet();
					}
					return true;
				}
				this.pendingBaseTiles.put(key, new DirtyTileState(discoveredAt, discoveredAt, true, false, true));
				this.newBaseTilesQueued.incrementAndGet();
			}
			return true;
		}

		private void queueLoadedStaleBaseTile(TileKey key, long discoveredAt) {
			rememberKnownBaseTile(key);
			if (key == null || key.lod() != MIN_LOD) {
				return;
			}
			TileImage image = imageFor(key);
			if (image == null || !image.valid() || !isStale(key, image, discoveredAt)) {
				return;
			}
			Long dirtyAt = this.dirtyAfter.get(key);
			long queuedAt = dirtyAt != null && dirtyAt > image.renderedAt() ? dirtyAt : image.availableAt();
			queuePendingBaseTile(key, queuedAt, true);
		}

		private void queuePendingBaseTile(TileKey key, long dirtyAt, boolean discoveryOnly) {
			queuePendingBaseTile(key, dirtyAt, discoveryOnly, false);
		}

		private void queuePendingBaseTile(TileKey key, long dirtyAt, boolean discoveryOnly, boolean countedAsNew) {
			if (key == null || key.lod() != MIN_LOD) {
				return;
			}
			synchronized (LOCK) {
				DirtyTileState existing = this.pendingBaseTiles.get(key);
				boolean renderedBefore = existing != null ? existing.renderedBefore() : tileWasRenderedBefore(key);
				if (existing == null) {
					this.pendingBaseTiles.put(key, new DirtyTileState(dirtyAt, dirtyAt, discoveryOnly, renderedBefore, countedAsNew));
				} else {
					this.pendingBaseTiles.put(
							key,
							new DirtyTileState(
									Math.min(existing.firstMarkedAt(), dirtyAt),
									Math.max(existing.lastMarkedAt(), dirtyAt),
									existing.discoveryOnly() && discoveryOnly,
									existing.renderedBefore() || renderedBefore,
									existing.countedAsNew() || countedAsNew
							)
					);
				}
				if (renderedBefore) {
					trimPendingBaseTilesLocked();
				}
			}
		}

		private void rememberKnownBaseTile(TileKey key) {
			if (key == null || key.lod() != MIN_LOD) {
				return;
			}
			TileKey current = key;
			while (current != null) {
				this.knownLodCoverage.add(current);
				current = parentKey(current);
			}
		}

		private void trimPendingBaseTilesLocked() {
			while (this.pendingBaseTiles.size() > MAX_DIRTY_BASE_TILES_PER_DIMENSION) {
				TileKey renderedKey = null;
				for (Map.Entry<TileKey, DirtyTileState> entry : this.pendingBaseTiles.entrySet()) {
					DirtyTileState state = entry.getValue();
					if (state != null && state.renderedBefore()) {
						renderedKey = entry.getKey();
						break;
					}
				}
				if (renderedKey != null) {
					this.pendingBaseTiles.remove(renderedKey);
					continue;
				}
				// New world tiles are never discarded. They are the source of truth
				// for a full map; losing one here used to leave a permanent hole.
				return;
			}
		}

		private PendingBaseTile pollNextPendingBaseTile() {
			synchronized (LOCK) {
				long now = System.currentTimeMillis();
				boolean unresolvedNewTile = !this.inFlightNewBaseTiles.isEmpty();
				if (!unresolvedNewTile) {
					for (DirtyTileState state : this.pendingBaseTiles.values()) {
						if (state != null && !state.renderedBefore()) {
							unresolvedNewTile = true;
							break;
						}
					}
				}
				TileKey bestKey = null;
				DirtyTileState bestState = null;
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
					Long retryAt = this.retryAfter.get(key);
					if (retryAt != null && retryAt > now) {
						continue;
					}
					if (retryAt != null) {
						this.retryAfter.remove(key, retryAt);
					}
					int score = tilePriorityScore(state);
					// Strict admission barrier: new world geometry must finish (or
					// explicitly retry) before a changed/maintenance tile can become
					// visible.  This is stronger than merely sorting the current map.
					if (unresolvedNewTile && score < 2) {
						continue;
					}
					if (bestKey == null
							|| score > bestScore
							|| (score == bestScore && shouldPreferPendingTile(key, state, bestKey, bestState))) {
						bestKey = key;
						bestState = state;
						bestScore = score;
					}
				}
				TileKey selectedKey = bestKey;
				DirtyTileState selectedState = bestState;
				if (selectedKey == null || selectedState == null) {
					return null;
				}
				this.pendingBaseTiles.remove(selectedKey);
				return new PendingBaseTile(
						selectedKey,
						selectedState.discoveryOnly(),
						selectedState.renderedBefore(),
						selectedState.countedAsNew(),
						bestScore
				);
			}
		}

		private int tilePriorityScore(DirtyTileState state) {
			if (state == null) {
				return Integer.MIN_VALUE;
			}
			if (!state.renderedBefore()) {
				return 2;
			}
			// A dirty notification represents an actual world change.  A
			// discovery-only entry is the 30-minute maintenance refresh.
			return state.discoveryOnly() ? 0 : 1;
		}

		private boolean shouldPreferPendingTile(TileKey key, DirtyTileState state, TileKey bestKey, DirtyTileState bestState) {
			if (state == null) {
				return false;
			}
			if (bestKey == null || bestState == null) {
				return true;
			}
			boolean newTile = !state.renderedBefore();
			boolean bestNewTile = !bestState.renderedBefore();
			if (newTile && bestNewTile) {
				double distance = baseTileDistanceSquaredToWorldCenter(key);
				double bestDistance = baseTileDistanceSquaredToWorldCenter(bestKey);
				if (Double.compare(distance, bestDistance) != 0) {
					return distance < bestDistance;
				}
			}
			double distance = baseTileDistanceSquaredToWorldCenter(key);
			double bestDistance = baseTileDistanceSquaredToWorldCenter(bestKey);
			int priority = tilePriorityScore(state);
			if (priority >= 2) {
				// The initial map grows as an exact, deterministic radius around
				// world centre; time of filesystem discovery cannot disturb it.
				if (Double.compare(distance, bestDistance) != 0) {
					return distance < bestDistance;
				}
			} else if (priority == 1) {
				// For updates, render the change that has been quiet longest.  It
				// avoids wasting a renderer pass on a chunk that is still changing
				// and naturally coalesces bursts into one final image.
				if (state.lastMarkedAt() != bestState.lastMarkedAt()) {
					return state.lastMarkedAt() < bestState.lastMarkedAt();
				}
				if (Double.compare(distance, bestDistance) != 0) {
					return distance < bestDistance;
				}
			} else {
				// Maintenance is oldest-first, with a deterministic central tie-break.
				if (state.firstMarkedAt() != bestState.firstMarkedAt()) {
					return state.firstMarkedAt() < bestState.firstMarkedAt();
				}
				if (Double.compare(distance, bestDistance) != 0) {
					return distance < bestDistance;
				}
			}
			if (key.tileZ() != bestKey.tileZ()) {
				return key.tileZ() < bestKey.tileZ();
			}
			return key.tileX() < bestKey.tileX();
		}

		private void discoverSavedBaseTiles() {
			Lg2.LOGGER.info("Yandex map: scanning saved world regions for unrendered tiles");
			Set<TileKey> savedBaseTiles = new HashSet<>();
			try {
				Path regionFolder = regionFolder(this.worldKey);
				if (regionFolder != null && Files.isDirectory(regionFolder)) {
					try (DirectoryStream<Path> files = Files.newDirectoryStream(regionFolder, "r.*.*.mca")) {
						for (Path regionFile : files) {
							RegionCoordinates region = parseRegionCoordinates(regionFile.getFileName().toString());
							if (region != null) {
								discoverRegionBaseTiles(regionFile, region, savedBaseTiles);
							}
						}
					}
					catch (IOException exception) {
						Lg2.LOGGER.debug("Failed to scan Yandex map region directory {}", regionFolder, exception);
					}
				}
			} finally {
				finishInitialBaseTileInventory(savedBaseTiles);
				logProgress(true);
				notifyActiveViews();
			}
		}

		private void finishInitialBaseTileInventory(Set<TileKey> savedBaseTiles) {
			queueInitialBaseTiles(savedBaseTiles);
			while (true) {
				Set<ChunkPos> deferred;
				synchronized (LOCK) {
					deferred = new HashSet<>(this.deferredChunkDiscoveries);
					this.deferredChunkDiscoveries.clear();
				}
				if (!deferred.isEmpty()) {
					Set<TileKey> deferredTiles = new HashSet<>();
					for (ChunkPos pos : deferred) {
						addChunkBaseTiles(pos, deferredTiles);
					}
					queueInitialBaseTiles(deferredTiles);
					continue;
				}
				synchronized (LOCK) {
					if (this.deferredChunkDiscoveries.isEmpty()) {
						this.savedTileQueueInitialized = true;
						return;
					}
				}
			}
		}

		private void queueInitialBaseTiles(Set<TileKey> baseTiles) {
			if (baseTiles == null || baseTiles.isEmpty()) {
				return;
			}
			List<TileKey> orderedTiles = new ArrayList<>(baseTiles);
			orderedTiles.sort(Comparator
					.comparingDouble(this::baseTileDistanceSquaredToWorldCenter)
					.thenComparingLong(TileKey::tileZ)
					.thenComparingLong(TileKey::tileX));
			long discoveredAt = System.currentTimeMillis();
			for (TileKey key : orderedTiles) {
				if (shouldDeferStartupBaseTile(key)) {
					boolean renderedBefore = tileWasRenderedBefore(key);
					deferStartupBaseTile(new PendingBaseTile(key, true, renderedBefore, !renderedBefore, 0));
					continue;
				}
				if (!queueMissingBaseTile(key, discoveredAt)) {
					queueLoadedStaleBaseTile(key, discoveredAt);
				}
			}
		}

		private void discoverRegionBaseTiles(Path regionFile, RegionCoordinates region, Set<TileKey> savedBaseTiles) {
			try (FileChannel channel = FileChannel.open(regionFile, StandardOpenOption.READ)) {
				if (channel.size() < 4096L) {
					return;
				}
				ByteBuffer header = ByteBuffer.allocate(4096).order(ByteOrder.BIG_ENDIAN);
				while (header.hasRemaining() && channel.read(header) >= 0) {
					// Read the complete region location table.
				}
				if (header.hasRemaining()) {
					return;
				}
				header.flip();
				for (int index = 0; index < 1024; index++) {
					int location = header.getInt();
					if ((location >>> 8) == 0 || (location & 0xFF) == 0) {
						continue;
					}
					int chunkX = (region.x() << 5) + (index & 31);
					int chunkZ = (region.z() << 5) + (index >> 5);
					this.knownSavedChunks.add(ChunkPos.asLong(chunkX, chunkZ));
					this.savedChunksDiscovered.incrementAndGet();
					addChunkBaseTiles(new ChunkPos(chunkX, chunkZ), savedBaseTiles);
				}
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to scan Yandex map region file {}", regionFile, exception);
			}
		}

		private void logProgressIfDue() {
			long now = System.currentTimeMillis();
			if (now - this.lastProgressLogAt >= PROGRESS_LOG_INTERVAL_MS) {
				logProgress(false);
			}
		}

		private void logProgress(boolean force) {
			long now = System.currentTimeMillis();
			synchronized (LOCK) {
				if (!force && now - this.lastProgressLogAt < PROGRESS_LOG_INTERVAL_MS) {
					return;
				}
				long queuedNew = 0;
				long queuedChanges = 0;
				long queuedMaintenance = 0;
				for (DirtyTileState state : this.pendingBaseTiles.values()) {
					if (state == null) {
						continue;
					}
					if (!state.renderedBefore()) {
						queuedNew++;
					} else if (state.discoveryOnly()) {
						queuedMaintenance++;
					} else {
						queuedChanges++;
					}
				}
				if (!force && queuedNew == 0 && queuedChanges == 0 && queuedMaintenance == 0) {
					return;
				}
				this.lastProgressLogAt = now;
				long queuedTotal = this.newBaseTilesQueued.get();
				long renderedTotal = this.newBaseTilesRendered.get();
				int activeTotal = this.inFlight.size();
				int activeNew = this.inFlightNewBaseTiles.size();
				int percent = queuedTotal <= 0 ? 100 : (int) Math.min(100L, renderedTotal * 100L / queuedTotal);
				Lg2.LOGGER.info(
						"Yandex map: {} saved chunks found; new tiles {}/{} ({}%), queued: {} new, {} changed, {} maintenance; active: {} ({} new)",
						this.savedChunksDiscovered.get(), renderedTotal, queuedTotal, percent,
						queuedNew, queuedChanges, queuedMaintenance, activeTotal, activeNew
				);
			}
		}

		private double baseTileDistanceSquaredToWorldCenter(TileKey key) {
			if (key == null) {
				return Double.POSITIVE_INFINITY;
			}
			double tileSize = tileWorldSize(key.lod());
			double centerX = (key.tileX() + 0.5D) * tileSize;
			double centerZ = (key.tileZ() + 0.5D) * tileSize;
			return centerX * centerX + centerZ * centerZ;
		}

		private boolean tileWasRenderedBefore(TileKey key) {
			if (key == null) {
				return false;
			}
			TileImage cached = this.tileLookup.get(key);
			if (cached != null && cached.valid()) {
				if (cached.needsCaptureVerification()) {
					return false;
				}
				return true;
			}
			if (this.missingTiles.contains(key)) {
				return false;
			}
			Path path = tilePath(this.worldKey, key);
			return isStoredTileFileUsable(path);
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

	private record DirtyTileState(
			long firstMarkedAt,
			long lastMarkedAt,
			boolean discoveryOnly,
			boolean renderedBefore,
			boolean countedAsNew
	) {
	}

	private record PendingBaseTile(
			TileKey key,
			boolean discoveryOnly,
			boolean renderedBefore,
			boolean countedAsNew,
			int priorityScore
	) {
	}

	private record VisibleWork(
			ScreenRuntimeKey viewKey,
			double worldLeft,
			double worldTop,
			int width,
			int height,
			double blocksPerPixel,
			List<TileKey> visibleTiles,
			Runnable onTileReady
	) {
	}

	private record RegionCoordinates(int x, int z) {
	}

	private static Path regionFolder(WorldCacheKey worldKey) {
		if (worldKey == null || worldKey.root() == null || worldKey.dimension() == null) {
			return null;
		}
		if (Level.OVERWORLD.equals(worldKey.dimension())) {
			return worldKey.root().resolve("region");
		}
		var id = worldKey.dimension().identifier();
		return worldKey.root().resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("region");
	}

	private static RegionCoordinates parseRegionCoordinates(String fileName) {
		if (fileName == null) {
			return null;
		}
		String[] parts = fileName.split("\\.");
		if (parts.length != 4 || !"r".equals(parts[0]) || !"mca".equals(parts[3])) {
			return null;
		}
		try {
			return new RegionCoordinates(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static Path tilePath(WorldCacheKey worldKey, TileKey key) {
		if (worldKey == null || worldKey.root() == null || worldKey.dimension() == null || key == null) {
			return null;
		}
		Path root = worldKey.root().resolve("data").resolve(CACHE_DIR_NAME);
		String dimension = sanitizePathPart(worldKey.dimension().identifier().toString());
		return root.resolve(dimension)
				.resolve("lod-" + key.lod())
				.resolve(Long.toString(Math.floorDiv(key.tileX(), 256L)))
				.resolve(key.tileX() + "_" + key.tileZ() + ".bin");
	}

	private static boolean isStoredTileFileUsable(Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return false;
		}
		try {
			return Files.size(path) >= TILE_RGB_BYTES;
		} catch (IOException ignored) {
			return false;
		}
	}

	private static String sanitizePathPart(String raw) {
		if (raw == null || raw.isBlank()) {
			return "unknown";
		}
		StringBuilder sanitized = new StringBuilder(raw.length());
		for (int index = 0; index < raw.length(); index++) {
			char ch = raw.charAt(index);
			if ((ch >= 'A' && ch <= 'Z')
					|| (ch >= 'a' && ch <= 'z')
					|| (ch >= '0' && ch <= '9')
					|| ch == '.'
					|| ch == '_'
					|| ch == '-') {
				sanitized.append(ch);
			} else {
				sanitized.append('_');
			}
		}
		return sanitized.toString();
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
