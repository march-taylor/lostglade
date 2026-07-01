package com.lostglade.server;

import com.lostglade.Lg2;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

final class MonitorYandexMapsClientTileRenderer {
	private static final int TILE_SIZE = MonitorScreenSystem.MAP_SIZE;
	private static final int MIN_LOD = 0;
	private static final int MAX_LOD = 9;
	private static final int MIN_ZOOM_EXPONENT = MIN_LOD;
	private static final int MAX_ZOOM_EXPONENT = MAX_LOD;
	private static final int DIRECT_RENDER_MAX_LOD = 0;
	private static final double BASE_BLOCKS_PER_PIXEL = 1.0D / 16.0D;
	private static final int RGB_BYTES_PER_PIXEL = 3;
	private static final int TILE_RGB_BYTES = TILE_SIZE * TILE_SIZE * RGB_BYTES_PER_PIXEL;
	private static final int MAX_CACHED_TILES_PER_DIMENSION = 4_096;
	private static final int MAX_MISSING_TILES_PER_DIMENSION = 65_536;
	private static final int MAX_BASE_TILE_REQUESTS_PER_FRAME = 4;
	private static final long BASE_TILE_REFRESH_MS = 30L * 60_000L;
	private static final String CACHE_DIR_NAME = "lg2-yandex-map-client-tiles-v10";
	private static final int MISSING_RGB = 0x18242B;
	private static final Object LOCK = new Object();
	private static final Map<WorldCacheKey, DimensionTileCache> CACHES = new LinkedHashMap<>(8, 0.75F, true);
	private static volatile Path persistentRoot;

	private MonitorYandexMapsClientTileRenderer() {
	}

	static void configure(MinecraftServer server) {
		if (server == null) {
			return;
		}
		persistentRoot = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(CACHE_DIR_NAME);
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
		}
		clear(null);
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
		if (server == null || level == null || width <= 0 || height <= 0 || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
			return Frame.failure(null, "Карта недоступна");
		}
		configure(server);
		double safeBlocksPerPixel = snapBlocksPerPixel(blocksPerPixel);
		int lod = lodForBlocksPerPixel(safeBlocksPerPixel);
		double tileBlocksPerPixel = blocksPerPixelForLod(lod);
		double worldLeft = centerX - width * safeBlocksPerPixel * 0.5D;
		double worldTop = centerZ - height * safeBlocksPerPixel * 0.5D;
		WorldCacheKey cacheKey = new WorldCacheKey(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), level.dimension());
		DimensionTileCache cache = cacheFor(cacheKey);
		List<TileKey> visibleTiles = visibleTiles(lod, tileBlocksPerPixel, worldLeft, worldTop, width, height, safeBlocksPerPixel, centerX, centerZ);
		TileRequestBudget budget = new TileRequestBudget(MAX_BASE_TILE_REQUESTS_PER_FRAME);
		int missingTiles = 0;
		for (TileKey key : visibleTiles) {
			if (cache.hasUsableTile(key)) {
				cache.refreshIfStale(server, level, key, budget, onTileReady);
				continue;
			}
			missingTiles++;
			cache.requestTile(server, level, key, budget, onTileReady);
		}
		if (budget.hasRemaining()) {
			for (TileKey key : prefetchTiles(visibleTiles, tileBlocksPerPixel, centerX, centerZ)) {
				if (!budget.hasRemaining()) {
					break;
				}
				if (!cache.hasUsableTile(key)) {
					cache.requestTile(server, level, key, budget, onTileReady);
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
		return Mth.clamp((int) Math.round(Math.log(Math.max(BASE_BLOCKS_PER_PIXEL, blocksPerPixel) / BASE_BLOCKS_PER_PIXEL) / Math.log(2.0D)), MIN_ZOOM_EXPONENT, MAX_ZOOM_EXPONENT);
	}

	static double blocksPerPixelForZoomExponent(int exponent) {
		int clamped = Mth.clamp(exponent, MIN_ZOOM_EXPONENT, MAX_ZOOM_EXPONENT);
		return BASE_BLOCKS_PER_PIXEL * (1 << clamped);
	}

	private static DimensionTileCache cacheFor(WorldCacheKey key) {
		synchronized (LOCK) {
			return CACHES.computeIfAbsent(key, ignored -> new DimensionTileCache(key));
		}
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

		private DimensionTileCache(WorldCacheKey worldKey) {
			this.worldKey = worldKey;
		}

		private boolean hasUsableTile(TileKey key) {
			TileImage image = imageFor(key);
			return image != null && image.valid();
		}

		private void refreshIfStale(MinecraftServer server, ServerLevel level, TileKey key, TileRequestBudget budget, Runnable onTileReady) {
			TileImage image = imageFor(key);
			if (image == null || System.currentTimeMillis() - image.renderedAt() <= BASE_TILE_REFRESH_MS) {
				return;
			}
			requestTile(server, level, key, budget, onTileReady);
		}

		private void requestTile(MinecraftServer server, ServerLevel level, TileKey key, TileRequestBudget budget, Runnable onTileReady) {
			if (server == null || level == null || key == null || budget == null) {
				return;
			}
			if (key.lod() > DIRECT_RENDER_MAX_LOD) {
				if (tryBuildFromChildren(key)) {
					notifyReady(onTileReady);
					return;
				}
				for (TileKey child : childKeys(key)) {
					if (!budget.hasRemaining()) {
						return;
					}
					requestTile(server, level, child, budget, onTileReady);
				}
				return;
			}
			if (hasUsableTile(key) && !isStale(key)) {
				return;
			}
			if (!budget.tryConsume() || !this.inFlight.add(key)) {
				return;
			}
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
					bpp
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
					TileImage image = new TileImage(Arrays.copyOf(pixels, TILE_RGB_BYTES), System.currentTimeMillis());
					storeTile(key, image);
					rebuildAncestors(key);
				} finally {
					this.inFlight.remove(key);
					notifyReady(onTileReady);
				}
			});
		}

		private boolean isStale(TileKey key) {
			TileImage image = imageFor(key);
			return image == null || System.currentTimeMillis() - image.renderedAt() > BASE_TILE_REFRESH_MS;
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
			cacheTile(key, loaded, false);
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

		private boolean tryBuildFromChildren(TileKey key) {
			if (key == null || key.lod() <= MIN_LOD) {
				return false;
			}
			TileImage[] children = new TileImage[4];
			int index = 0;
			for (TileKey child : childKeys(key)) {
				TileImage image = imageFor(child);
				if (image == null || !image.valid()) {
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
			storeTile(key, new TileImage(pixels, System.currentTimeMillis()));
			return true;
		}

		private void rebuildAncestors(TileKey key) {
			TileKey parent = parentKey(key);
			while (parent != null && tryBuildFromChildren(parent)) {
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

		private void storeTile(TileKey key, TileImage image) {
			cacheTile(key, image, true);
		}

		private void cacheTile(TileKey key, TileImage image, boolean persist) {
			if (key == null || image == null || !image.valid()) {
				return;
			}
			synchronized (LOCK) {
				this.tiles.put(key, image);
				this.tileLookup.put(key, image);
				this.missingTiles.remove(key);
				trimToBudget();
			}
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
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to persist Yandex map tile {}", path, exception);
			}
		}
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
