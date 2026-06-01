package com.lostglade.server;

import com.flowpowered.math.vector.Vector3i;
import com.flowpowered.math.vector.Vector2i;
import com.lostglade.Lg2;
import com.lostglade.server.map.BlockTextureRaycaster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class MonitorYandexMapsBlueMapRenderer {
	private static final int TILE_SIZE = 128;
	private static final int MIN_LOD = -9;
	private static final int MAX_LOD = 16;
	private static final int MAX_CACHED_TILES_PER_DIMENSION = 8192;
	private static final int MAX_TILE_RENDERS_PER_FRAME = 8;
	private static final long TILE_TTL_MS = 10 * 60_000L;
	private static final long REGION_INDEX_TTL_MS = 60_000L;
	private static final int MAX_SURFACE_SCAN_BLOCKS = 96;
	private static final int[] NO_TINTS = new int[0];
	private static final int NO_TINT = -1;
	private static final int MISSING_RGB = 0x18242B;
	private static final int GRASS_TINT_RGB = 0x73B84A;
	private static final int FOLIAGE_TINT_RGB = 0x4CA33B;
	private static final int DRY_FOLIAGE_TINT_RGB = 0x9B8F4A;
	private static final int WATER_TINT_RGB = 0x3F76E4;
	private static final int ATTACHED_STEM_RGB = 0xE0C71C;
	private static final int LILY_PAD_RGB = 0x208030;
	private static final Path BLUEMAP_JAR = Path.of("/home/mart/Downloads/bluemap-5.16-fabric.jar");
	private static final Path VANILLA_CLIENT_JAR = Path.of(System.getProperty("user.home"), ".gradle", "caches", "fabric-loom", "1.21.11", "minecraft-client.jar");
	private static final Path VANILLA_COMMON_JAR = Path.of(System.getProperty("user.home"), ".gradle", "caches", "fabric-loom", "1.21.11", "minecraft-common.jar");
	private static final Object LOCK = new Object();
	private static final Map<WorldCacheKey, DimensionTileCache> CACHES = new LinkedHashMap<>(8, 0.75F, true);
	private static final Map<String, BlockState> VANILLA_STATE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Integer> STATE_COLOR_CACHE = new ConcurrentHashMap<>();
	private static volatile BlueMapBridge bridge;
	private static volatile String bridgeError;

	private MonitorYandexMapsBlueMapRenderer() {
	}

	static Frame render(MinecraftServer server, ServerLevel level, double centerX, double centerZ, int width, int height, double blocksPerPixel) {
		if (server == null || level == null || width <= 0 || height <= 0 || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
			return Frame.failure(null, "Карта недоступна");
		}
		BlueMapBridge blueMap = bridge(server);
		if (blueMap == null) {
			String status = bridgeError == null || bridgeError.isBlank() ? "BlueMap renderer недоступен" : bridgeError;
			return Frame.failure(null, status);
		}
		Object world = blueMap.world(server, level);
		if (world == null) {
			String status = blueMap.lastWorldError == null || blueMap.lastWorldError.isBlank() ? "MCA world недоступен" : blueMap.lastWorldError;
			return Frame.failure(null, status);
		}

		double safeBlocksPerPixel = Mth.clamp(blocksPerPixel, 1.0D / 512.0D, 4096.0D);
		int lod = lodForBlocksPerPixel(safeBlocksPerPixel);
		double tileBlocksPerPixel = blocksPerPixelForLod(lod);
		double worldLeft = centerX - width * safeBlocksPerPixel * 0.5D;
		double worldTop = centerZ - height * safeBlocksPerPixel * 0.5D;
		WorldCacheKey cacheKey = new WorldCacheKey(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), level.dimension());
		DimensionTileCache cache = cacheFor(cacheKey);
		List<TileKey> visibleTiles = visibleTiles(lod, tileBlocksPerPixel, worldLeft, worldTop, width, height, safeBlocksPerPixel, centerX, centerZ);
		long now = System.currentTimeMillis();
		int renderedTiles = 0;
		for (TileKey key : visibleTiles) {
			if (cache.peekFresh(key, now) != null) {
				continue;
			}
			if (renderedTiles >= MAX_TILE_RENDERS_PER_FRAME) {
				continue;
			}
			cache.tile(blueMap, world, level, key, tileBlocksPerPixel, now);
			renderedTiles++;
		}

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = image.getRaster().getDataBuffer() instanceof DataBufferInt dataBuffer ? dataBuffer.getData() : null;
		int missing = 0;
		for (int screenY = 0; screenY < height; screenY++) {
			double worldZ = worldTop + (screenY + 0.5D) * safeBlocksPerPixel;
			for (int screenX = 0; screenX < width; screenX++) {
				double worldX = worldLeft + (screenX + 0.5D) * safeBlocksPerPixel;
				int rgb = cache.sample(worldX, worldZ, lod);
				if (rgb == MISSING_RGB) {
					missing++;
				}
				int argb = 0xFF000000 | rgb;
				int index = screenY * width + screenX;
				if (pixels != null) {
					pixels[index] = argb;
				} else {
					image.setRGB(screenX, screenY, argb);
				}
			}
		}
		boolean healthy = missing < width * height;
		return new Frame(image, healthy ? "BlueMap top-down" : "Нет сгенерированных тайлов", healthy);
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

	private static BlueMapBridge bridge(MinecraftServer server) {
		BlueMapBridge ready = bridge;
		if (ready != null) {
			return ready;
		}
		synchronized (LOCK) {
			if (bridge != null) {
				return bridge;
			}
			try {
				bridge = BlueMapBridge.create(server);
				bridgeError = null;
				return bridge;
			} catch (Exception exception) {
				bridgeError = "BlueMap init: " + exception.getClass().getSimpleName();
				Lg2.LOGGER.error("Failed to initialize isolated BlueMap renderer for monitor maps", exception);
				return null;
			}
		}
	}

	private static DimensionTileCache cacheFor(WorldCacheKey key) {
		synchronized (LOCK) {
			return CACHES.computeIfAbsent(key, ignored -> new DimensionTileCache());
		}
	}

	private static int lodForBlocksPerPixel(double blocksPerPixel) {
		return Mth.clamp((int) Math.round(Math.log(blocksPerPixel) / Math.log(2.0D)), MIN_LOD, MAX_LOD);
	}

	private static double blocksPerPixelForLod(int lod) {
		return Math.pow(2.0D, Mth.clamp(lod, MIN_LOD, MAX_LOD));
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

	private record TileImage(int[] pixels, long renderedAt) {
		int sample(int x, int y) {
			if (this.pixels == null || this.pixels.length == 0) {
				return MISSING_RGB;
			}
			int safeX = Mth.clamp(x, 0, TILE_SIZE - 1);
			int safeY = Mth.clamp(y, 0, TILE_SIZE - 1);
			return this.pixels[safeY * TILE_SIZE + safeX] & 0xFFFFFF;
		}
	}

	private static final class DimensionTileCache {
		private final LinkedHashMap<TileKey, TileImage> tiles = new LinkedHashMap<>(256, 0.75F, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<TileKey, TileImage> eldest) {
				return this.size() > MAX_CACHED_TILES_PER_DIMENSION;
			}
		};

		private TileImage tile(BlueMapBridge blueMap, Object world, ServerLevel level, TileKey key, double tileBlocksPerPixel, long now) {
			synchronized (LOCK) {
				TileImage cached = this.tiles.get(key);
				if (cached != null && now - cached.renderedAt() <= TILE_TTL_MS) {
					return cached;
				}
			}
			TileImage rendered = renderTile(blueMap, world, level, key, tileBlocksPerPixel, now);
			synchronized (LOCK) {
				this.tiles.put(key, rendered);
			}
			return rendered;
		}

		private TileImage peekFresh(TileKey key, long now) {
			synchronized (LOCK) {
				TileImage cached = this.tiles.get(key);
				if (cached != null && now - cached.renderedAt() <= TILE_TTL_MS) {
					return cached;
				}
			}
			return null;
		}

		private int sample(double worldX, double worldZ, int lod) {
			TileKey key = tileKeyAt(lod, worldX, worldZ);
			TileImage image;
			synchronized (LOCK) {
				image = this.tiles.get(key);
			}
			if (image == null) {
				return MISSING_RGB;
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

		private TileImage renderTile(BlueMapBridge blueMap, Object world, ServerLevel level, TileKey key, double tileBlocksPerPixel, long now) {
			int[] pixels = new int[TILE_SIZE * TILE_SIZE];
			for (int i = 0; i < pixels.length; i++) {
				pixels[i] = MISSING_RGB;
			}
			double tileSize = TILE_SIZE * tileBlocksPerPixel;
			double worldLeft = key.tileX() * tileSize;
			double worldTop = key.tileZ() * tileSize;
			try {
				renderSurfaceTile(blueMap, world, level, worldLeft, worldTop, tileBlocksPerPixel, pixels);
			} catch (Exception exception) {
				Lg2.LOGGER.warn("BlueMap monitor tile render failed at lod {} {},{}", key.lod(), key.tileX(), key.tileZ(), exception);
			}
			return new TileImage(pixels, now);
		}

		private void renderSurfaceTile(BlueMapBridge blueMap, Object world, ServerLevel level, double worldLeft, double worldTop, double tileBlocksPerPixel, int[] pixels) throws ReflectiveOperationException {
			Map<Long, Object> chunkCache = new HashMap<>();
			for (int y = 0; y < TILE_SIZE; y++) {
				double sampleZ = worldTop + (y + 0.5D) * tileBlocksPerPixel;
				int blockZ = Mth.floor(sampleZ);
				for (int x = 0; x < TILE_SIZE; x++) {
					double sampleX = worldLeft + (x + 0.5D) * tileBlocksPerPixel;
					int blockX = Mth.floor(sampleX);
					int index = y * TILE_SIZE + x;
					SurfaceSample sample = blueMap.surfaceAt(world, chunkCache, blockX, blockZ, level.getMinY(), level.getMaxY() - 1);
					if (sample != null) {
						double fracX = sampleX - Math.floor(sampleX);
						double fracZ = sampleZ - Math.floor(sampleZ);
						pixels[index] = colorForSurface(sample, blockX, blockZ, fracX, fracZ, tileBlocksPerPixel);
					}
				}
			}
		}
	}

	private static int colorForSurface(SurfaceSample sample, int blockX, int blockZ, double fracX, double fracZ, double blocksPerPixel) {
		BlockState state = sample.state();
		if (state == null || state.isAir()) {
			return MISSING_RGB;
		}
		if (blocksPerPixel >= 1.0D) {
			return STATE_COLOR_CACHE.computeIfAbsent(sample.cacheKey(), ignored -> sampleStateColor(state, blockX, sample.y(), blockZ, 0.5D, 0.5D));
		}
		return sampleStateColor(state, blockX, sample.y(), blockZ, fracX, fracZ);
	}

	private static int sampleStateColor(BlockState state, int blockX, int blockY, int blockZ, double fracX, double fracZ) {
		int[] tintColors = defaultTints(state);
		BlockTextureRaycaster.BlockTraceResult result = BlockTextureRaycaster.trace(
				state,
				new BlockPos(blockX, blockY, blockZ),
				new Vec3(blockX + Mth.clamp(fracX, 0.0D, 0.999D), blockY + 1.999D, blockZ + Mth.clamp(fracZ, 0.0D, 0.999D)),
				new Vec3(0.0D, -1.0D, 0.0D),
				tintColors
		);
		if (result != null) {
			return shadeByFace(result.rgb(), result.face(), result.shade());
		}
		return fallbackMapColor(state);
	}

	private static int[] defaultTints(BlockState state) {
		if (state == null) {
			return NO_TINTS;
		}
		Block block = state.getBlock();
		int[] tintLayers = null;
		if (block == Blocks.GRASS_BLOCK
				|| block == Blocks.FERN
				|| block == Blocks.SHORT_GRASS
				|| block == Blocks.POTTED_FERN
				|| block == Blocks.BUSH
				|| block == Blocks.SUGAR_CANE
				|| block == Blocks.LARGE_FERN
				|| block == Blocks.TALL_GRASS) {
			tintLayers = tintArray();
			tintLayers[0] = GRASS_TINT_RGB;
			return tintLayers;
		}
		if (block == Blocks.PINK_PETALS || block == Blocks.WILDFLOWERS) {
			tintLayers = tintArray();
			tintLayers[1] = GRASS_TINT_RGB;
			return tintLayers;
		}
		if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN || block == Blocks.WATER_CAULDRON) {
			tintLayers = tintArray();
			tintLayers[0] = WATER_TINT_RGB;
			return tintLayers;
		}
		if (block == Blocks.REDSTONE_WIRE) {
			tintLayers = tintArray();
			tintLayers[0] = RedStoneWireBlock.getColorForPower(state.getValue(RedStoneWireBlock.POWER));
			return tintLayers;
		}
		if (block == Blocks.ATTACHED_MELON_STEM || block == Blocks.ATTACHED_PUMPKIN_STEM) {
			tintLayers = tintArray();
			tintLayers[0] = ATTACHED_STEM_RGB;
			return tintLayers;
		}
		if (block == Blocks.MELON_STEM || block == Blocks.PUMPKIN_STEM) {
			int age = state.getValue(StemBlock.AGE);
			tintLayers = tintArray();
			tintLayers[0] = ((age * 32) << 16) | ((255 - age * 8) << 8) | (age * 4);
			return tintLayers;
		}
		if (block == Blocks.LILY_PAD) {
			tintLayers = tintArray();
			tintLayers[0] = LILY_PAD_RGB;
			return tintLayers;
		}
		if (block == Blocks.LEAF_LITTER) {
			tintLayers = tintArray();
			tintLayers[0] = DRY_FOLIAGE_TINT_RGB;
			return tintLayers;
		}
		if (block == Blocks.SPRUCE_LEAVES) {
			tintLayers = tintArray();
			tintLayers[0] = 0x619961;
			return tintLayers;
		}
		if (block == Blocks.BIRCH_LEAVES) {
			tintLayers = tintArray();
			tintLayers[0] = 0x80A755;
			return tintLayers;
		}
		if (block == Blocks.OAK_LEAVES
				|| block == Blocks.JUNGLE_LEAVES
				|| block == Blocks.ACACIA_LEAVES
				|| block == Blocks.DARK_OAK_LEAVES
				|| block == Blocks.VINE
				|| block == Blocks.MANGROVE_LEAVES
				|| block instanceof LeavesBlock) {
			tintLayers = tintArray();
			tintLayers[0] = FOLIAGE_TINT_RGB;
			return tintLayers;
		}
		return NO_TINTS;
	}

	private static int[] tintArray() {
		int[] tintLayers = new int[4];
		Arrays.fill(tintLayers, NO_TINT);
		return tintLayers;
	}

	private static int shadeByFace(int rgb, net.minecraft.core.Direction face, boolean shade) {
		if (!shade || face == null) {
			return rgb & 0xFFFFFF;
		}
		double factor = switch (face) {
			case DOWN -> 0.50D;
			case NORTH, SOUTH -> 0.80D;
			case WEST, EAST -> 0.60D;
			default -> 1.0D;
		};
		return multiplyRgb(rgb, factor);
	}

	private static int fallbackMapColor(BlockState state) {
		try {
			MapColor color = state.getMapColor(null, BlockPos.ZERO);
			if (color != null && color != MapColor.NONE) {
				return color.calculateARGBColor(MapColor.Brightness.NORMAL) & 0xFFFFFF;
			}
		} catch (RuntimeException ignored) {
			// Some dynamic map-color providers expect a real level; keep the map responsive.
		}
		Block block = state.getBlock();
		if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN) {
			return WATER_TINT_RGB;
		}
		if (block == Blocks.GRASS_BLOCK) {
			return GRASS_TINT_RGB;
		}
		return 0x77746A;
	}

	private static int multiplyRgb(int rgb, double factor) {
		int red = Mth.clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
		int green = Mth.clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
		int blue = Mth.clamp((int) Math.round((rgb & 0xFF) * factor), 0, 255);
		return (red << 16) | (green << 8) | blue;
	}

	private static BlockState vanillaStateFor(String cacheKey, String idString, Map<String, String> properties) {
		if (cacheKey == null || idString == null) {
			return Blocks.AIR.defaultBlockState();
		}
		return VANILLA_STATE_CACHE.computeIfAbsent(cacheKey, ignored -> buildVanillaState(idString, properties));
	}

	private static BlockState buildVanillaState(String idString, Map<String, String> properties) {
		Identifier id = Identifier.tryParse(idString);
		if (id == null) {
			return Blocks.AIR.defaultBlockState();
		}
		Block block = BuiltInRegistries.BLOCK.getValue(id);
		if (block == null) {
			return Blocks.AIR.defaultBlockState();
		}
		BlockState state = block.defaultBlockState();
		if (properties == null || properties.isEmpty()) {
			return state;
		}
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
			if (property != null) {
				state = setPropertyValue(state, property, entry.getValue());
			}
		}
		return state;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState setPropertyValue(BlockState state, Property property, String value) {
		Optional<? extends Comparable> parsed = property.getValue(value);
		if (parsed.isEmpty()) {
			return state;
		}
		return state.setValue(property, parsed.get());
	}

	@FunctionalInterface
	private interface TilePixelConsumer {
		void set(int x, int z, int rgb);
	}

	private record SurfaceSample(BlockState state, int y, String cacheKey) {
	}

	private record RegionIndex(Set<Long> regions, long loadedAt) {
	}

	private static final class BlueMapBridge {
		private final URLClassLoader classLoader;
		private final Class<?> packVersionClass;
		private final Class<?> resourcePackClass;
		private final Class<?> textureGalleryClass;
		private final Class<?> dataPackClass;
		private final Class<?> keyClass;
		private final Class<?> mcaWorldClass;
		private final Class<?> worldClass;
		private final Class<?> chunkClass;
		private final Class<?> blueBlockStateClass;
		private final Class<?> renderSettingsClass;
		private final Class<?> blockRenderPassClass;
		private final Class<?> tileModelViewClass;
		private final Class<?> tileModelClass;
		private final Class<?> tileMetaConsumerClass;
		private final Class<?> resourcePoolClass;
		private final Method resourcePackLoadResources;
		private final Method resourcePackGetTextures;
		private final Method textureGalleryPut;
		private final Method dataPackLoadResources;
		private final Method dataPackBake;
		private final Method keyParse;
		private final Method keyGetFormatted;
		private final Method mcaWorldLoad;
		private final Method worldGetChunkAtBlock;
		private final Method worldListRegions;
		private final Method chunkIsGenerated;
		private final Method chunkGetBlockState;
		private final Method chunkGetMaxY;
		private final Method chunkGetMinY;
		private final Method chunkHasWorldSurfaceHeights;
		private final Method chunkGetWorldSurfaceY;
		private final Method blueBlockStateGetId;
		private final Method blueBlockStateGetProperties;
		private final Method blueBlockStateIsAir;
		private final Method blockRenderPassRender;
		private final Method colorFlatten;
		private final Method colorGetInt;
		private final Constructor<?> blockRenderPassConstructor;
		private final Constructor<?> tileModelViewConstructor;
		private final Object resourcePack;
		private final Object textureGallery;
		private final Object dataPack;
		private final Object renderSettings;
		private final Object voidTileModel;
		private final ThreadLocal<Object> renderPass;
		private final ThreadLocal<Object> tileModelView;
		private final Map<WorldCacheKey, Object> worlds = new LinkedHashMap<>();
		private final Map<Object, RegionIndex> generatedRegions = new LinkedHashMap<>();
		private String lastWorldError;

		private BlueMapBridge(MinecraftServer server) throws Exception {
			if (!Files.isRegularFile(BLUEMAP_JAR)) {
				throw new IOException("BlueMap jar not found: " + BLUEMAP_JAR);
			}
			this.classLoader = new URLClassLoader(new URL[]{BLUEMAP_JAR.toUri().toURL()}, MonitorYandexMapsBlueMapRenderer.class.getClassLoader());
			this.packVersionClass = load("de.bluecolored.bluemap.core.resources.pack.PackVersion");
			this.resourcePackClass = load("de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack");
			this.textureGalleryClass = load("de.bluecolored.bluemap.core.map.TextureGallery");
			this.dataPackClass = load("de.bluecolored.bluemap.core.resources.pack.datapack.DataPack");
			this.keyClass = load("de.bluecolored.bluemap.core.util.Key");
			this.mcaWorldClass = load("de.bluecolored.bluemap.core.world.mca.MCAWorld");
			this.worldClass = load("de.bluecolored.bluemap.core.world.World");
			this.chunkClass = load("de.bluecolored.bluemap.core.world.Chunk");
			this.blueBlockStateClass = load("de.bluecolored.bluemap.core.world.BlockState");
			this.renderSettingsClass = load("de.bluecolored.bluemap.core.map.hires.RenderSettings");
			this.blockRenderPassClass = load("de.bluecolored.bluemap.core.map.hires.block.BlockRenderPass");
			this.tileModelViewClass = load("de.bluecolored.bluemap.core.map.hires.TileModelView");
			this.tileModelClass = load("de.bluecolored.bluemap.core.map.hires.TileModel");
			this.tileMetaConsumerClass = load("de.bluecolored.bluemap.core.map.TileMetaConsumer");
			this.resourcePoolClass = load("de.bluecolored.bluemap.core.resources.pack.ResourcePool");
			Class<?> maskClass = load("de.bluecolored.bluemap.core.map.mask.Mask");
			Class<?> voidTileModelClass = load("de.bluecolored.bluemap.core.map.hires.VoidTileModel");
			Class<?> colorClass = load("de.bluecolored.bluemap.core.util.math.Color");

			this.resourcePackLoadResources = this.resourcePackClass.getMethod("loadResources", Iterable.class);
			this.resourcePackGetTextures = this.resourcePackClass.getMethod("getTextures");
			this.textureGalleryPut = this.textureGalleryClass.getMethod("put", this.resourcePoolClass);
			this.dataPackLoadResources = this.dataPackClass.getMethod("loadResources", Iterable.class);
			this.dataPackBake = this.dataPackClass.getMethod("bake");
			this.keyParse = this.keyClass.getMethod("parse", String.class);
			this.keyGetFormatted = this.keyClass.getMethod("getFormatted");
			this.mcaWorldLoad = this.mcaWorldClass.getMethod("load", Path.class, this.keyClass, this.dataPackClass);
			this.worldGetChunkAtBlock = this.worldClass.getMethod("getChunkAtBlock", int.class, int.class);
			this.worldListRegions = this.worldClass.getMethod("listRegions");
			this.chunkIsGenerated = this.chunkClass.getMethod("isGenerated");
			this.chunkGetBlockState = this.chunkClass.getMethod("getBlockState", int.class, int.class, int.class);
			this.chunkGetMaxY = this.chunkClass.getMethod("getMaxY", int.class, int.class);
			this.chunkGetMinY = this.chunkClass.getMethod("getMinY", int.class, int.class);
			this.chunkHasWorldSurfaceHeights = this.chunkClass.getMethod("hasWorldSurfaceHeights");
			this.chunkGetWorldSurfaceY = this.chunkClass.getMethod("getWorldSurfaceY", int.class, int.class);
			this.blueBlockStateGetId = this.blueBlockStateClass.getMethod("getId");
			this.blueBlockStateGetProperties = this.blueBlockStateClass.getMethod("getProperties");
			this.blueBlockStateIsAir = this.blueBlockStateClass.getMethod("isAir");
			this.blockRenderPassRender = this.blockRenderPassClass.getMethod(
					"render",
					this.worldClass,
					Vector3i.class,
					Vector3i.class,
					Vector3i.class,
					this.tileModelViewClass,
					this.tileMetaConsumerClass
			);
			this.colorFlatten = colorClass.getMethod("flatten");
			this.colorGetInt = colorClass.getMethod("getInt");
			this.blockRenderPassConstructor = this.blockRenderPassClass.getConstructor(this.resourcePackClass, this.textureGalleryClass, this.renderSettingsClass);
			this.tileModelViewConstructor = this.tileModelViewClass.getConstructor(this.tileModelClass);

			this.resourcePack = this.resourcePackClass.getConstructor(this.packVersionClass).newInstance(packVersion(75, 0));
			this.resourcePackLoadResources.invoke(this.resourcePack, resourcePackRoots(server));
			this.textureGallery = this.textureGalleryClass.getConstructor().newInstance();
			this.textureGalleryPut.invoke(this.textureGallery, this.resourcePackGetTextures.invoke(this.resourcePack));
			this.dataPack = this.dataPackClass.getConstructor(this.packVersionClass).newInstance(packVersion(94, 1));
			this.dataPackLoadResources.invoke(this.dataPack, dataPackRoots(server));
			this.dataPackBake.invoke(this.dataPack);
			Object maskAll = staticField(maskClass, "ALL");
			this.renderSettings = renderSettingsProxy(maskAll);
			this.voidTileModel = staticField(voidTileModelClass, "INSTANCE");
			this.renderPass = ThreadLocal.withInitial(() -> newUnchecked(this.blockRenderPassConstructor, this.resourcePack, this.textureGallery, this.renderSettings));
			this.tileModelView = ThreadLocal.withInitial(() -> newUnchecked(this.tileModelViewConstructor, this.voidTileModel));
		}

		private static BlueMapBridge create(MinecraftServer server) throws Exception {
			return new BlueMapBridge(server);
		}

		private Class<?> load(String name) throws ClassNotFoundException {
			return Class.forName(name, true, this.classLoader);
		}

		private Object packVersion(int major, int minor) throws ReflectiveOperationException {
			return this.packVersionClass.getConstructor(int.class, int.class).newInstance(major, minor);
		}

		private Object staticField(Class<?> type, String name) throws ReflectiveOperationException {
			Field field = type.getField(name);
			return field.get(null);
		}

		private Object renderSettingsProxy(Object maskAll) {
			return Proxy.newProxyInstance(this.classLoader, new Class[]{this.renderSettingsClass}, (proxy, method, args) -> switch (method.getName()) {
				case "getRemoveCavesBelowY", "getCaveDetectionOceanFloor" -> Integer.MIN_VALUE;
				case "isCaveDetectionUsesBlockLight", "isSaveHiresLayer" -> false;
				case "isRenderTopOnly", "isIgnoreMissingLightData", "isRenderEdges", "isInsideRenderBoundaries" -> true;
				case "getAmbientLight" -> 0.08F;
				case "getEdgeLightStrength" -> 15;
				case "getRenderMask" -> maskAll;
				case "getCellRenderBoundariesFilter" -> (Predicate<Object>) ignored -> true;
				case "toString" -> "LostGladeBlueMapTopDownSettings";
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == args[0];
				default -> defaultValue(method.getReturnType());
			});
		}

		private Object world(MinecraftServer server, ServerLevel level) {
			WorldCacheKey key = new WorldCacheKey(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize(), level.dimension());
			synchronized (LOCK) {
				Object cached = this.worlds.get(key);
				if (cached != null) {
					return cached;
				}
			}
			try {
				Object dimension = this.keyParse.invoke(null, level.dimension().identifier().toString());
				Object loaded = this.mcaWorldLoad.invoke(null, key.root(), dimension, this.dataPack);
				synchronized (LOCK) {
					this.worlds.put(key, loaded);
				}
				this.lastWorldError = null;
				return loaded;
			} catch (IllegalAccessException | InvocationTargetException exception) {
				Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
						? invocation.getCause()
						: exception;
				this.lastWorldError = "MCAWorld: " + cause.getClass().getSimpleName();
				Lg2.LOGGER.error("Failed to load BlueMap MCA world for {}", level.dimension().identifier(), cause);
				return null;
			}
		}

		private SurfaceSample surfaceAt(Object world, Map<Long, Object> chunkCache, int blockX, int blockZ, int levelMinY, int levelMaxY) throws ReflectiveOperationException {
			if (!hasGeneratedRegion(world, blockX, blockZ)) {
				return null;
			}
			Object chunk = cachedChunk(world, chunkCache, blockX, blockZ);
			if (chunk == null || !((Boolean) this.chunkIsGenerated.invoke(chunk))) {
				return null;
			}
			int localX = blockX & 15;
			int localZ = blockZ & 15;
			int chunkMinY = ((Number) this.chunkGetMinY.invoke(chunk, blockX, blockZ)).intValue();
			int chunkMaxY = ((Number) this.chunkGetMaxY.invoke(chunk, blockX, blockZ)).intValue();
			int minY = Math.max(levelMinY, chunkMinY);
			int maxY = Math.min(levelMaxY, chunkMaxY);
			if (maxY < minY) {
				return null;
			}
			int startY = maxY;
			if ((Boolean) this.chunkHasWorldSurfaceHeights.invoke(chunk)) {
				startY = ((Number) this.chunkGetWorldSurfaceY.invoke(chunk, localX, localZ)).intValue();
				startY = Mth.clamp(startY, minY, maxY);
			}
			int endY = Math.max(minY, startY - MAX_SURFACE_SCAN_BLOCKS);
			SurfaceSample sample = findSurfaceInRange(chunk, blockX, blockZ, startY, endY);
			if (sample != null) {
				return sample;
			}
			if (endY > minY) {
				return findSurfaceInRange(chunk, blockX, blockZ, endY - 1, minY);
			}
			return null;
		}

		private boolean hasGeneratedRegion(Object world, int blockX, int blockZ) throws ReflectiveOperationException {
			Set<Long> regions = generatedRegions(world);
			if (regions.isEmpty()) {
				return false;
			}
			return regions.contains(regionKey(blockX >> 9, blockZ >> 9));
		}

		private Set<Long> generatedRegions(Object world) throws ReflectiveOperationException {
			long now = System.currentTimeMillis();
			synchronized (LOCK) {
				RegionIndex cached = this.generatedRegions.get(world);
				if (cached != null && now - cached.loadedAt() <= REGION_INDEX_TTL_MS) {
					return cached.regions();
				}
			}
			Object rawRegions = this.worldListRegions.invoke(world);
			Set<Long> regions = new HashSet<>();
			if (rawRegions instanceof Iterable<?> iterable) {
				for (Object rawRegion : iterable) {
					if (rawRegion instanceof Vector2i region) {
						regions.add(regionKey(region.getX(), region.getY()));
					}
				}
			}
			Set<Long> immutable = Set.copyOf(regions);
			synchronized (LOCK) {
				this.generatedRegions.put(world, new RegionIndex(immutable, now));
			}
			return immutable;
		}

		private static long regionKey(int regionX, int regionZ) {
			return (((long) regionX) << 32) ^ (regionZ & 0xFFFFFFFFL);
		}

		private Object cachedChunk(Object world, Map<Long, Object> chunkCache, int blockX, int blockZ) throws ReflectiveOperationException {
			long key = (((long) (blockX >> 4)) << 32) ^ ((blockZ >> 4) & 0xFFFFFFFFL);
			Object cached = chunkCache.get(key);
			if (cached != null) {
				return cached;
			}
			Object loaded = this.worldGetChunkAtBlock.invoke(world, blockX, blockZ);
			chunkCache.put(key, loaded);
			return loaded;
		}

		private SurfaceSample findSurfaceInRange(Object chunk, int blockX, int blockZ, int startY, int endY) throws ReflectiveOperationException {
			for (int y = startY; y >= endY; y--) {
				Object blueState = this.chunkGetBlockState.invoke(chunk, blockX, y, blockZ);
				if (blueState == null || (Boolean) this.blueBlockStateIsAir.invoke(blueState)) {
					continue;
				}
				String cacheKey = blueState.toString();
				String id = blueBlockStateId(blueState);
				Map<String, String> properties = blueBlockStateProperties(blueState);
				BlockState state = vanillaStateFor(cacheKey, id, properties);
				if (state == null || state.isAir()) {
					continue;
				}
				return new SurfaceSample(state, y, cacheKey);
			}
			return null;
		}

		private String blueBlockStateId(Object blueState) throws ReflectiveOperationException {
			Object key = this.blueBlockStateGetId.invoke(blueState);
			if (key == null) {
				return null;
			}
			Object formatted = this.keyGetFormatted.invoke(key);
			return formatted == null ? key.toString() : formatted.toString();
		}

		@SuppressWarnings("unchecked")
		private Map<String, String> blueBlockStateProperties(Object blueState) throws ReflectiveOperationException {
			Object properties = this.blueBlockStateGetProperties.invoke(blueState);
			if (properties instanceof Map<?, ?> rawMap) {
				Map<String, String> result = new HashMap<>();
				for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
					if (entry.getKey() != null && entry.getValue() != null) {
						result.put(entry.getKey().toString(), entry.getValue().toString());
					}
				}
				return result;
			}
			return Map.of();
		}

		private void renderColumns(Object world, ServerLevel level, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, TilePixelConsumer consumer) throws ReflectiveOperationException {
			Object tileConsumer = Proxy.newProxyInstance(this.classLoader, new Class[]{this.tileMetaConsumerClass}, (proxy, method, args) -> {
				if ("set".equals(method.getName()) && args != null && args.length >= 5) {
					consumer.set((Integer) args[0], (Integer) args[1], colorToRgb(args[2]));
				}
				return null;
			});
			try {
				this.blockRenderPassRender.invoke(
						this.renderPass.get(),
						world,
						new Vector3i(minBlockX, level.getMinY(), minBlockZ),
						new Vector3i(maxBlockX, level.getMaxY() - 1, maxBlockZ),
						new Vector3i(minBlockX, level.getMinY(), minBlockZ),
						this.tileModelView.get(),
						tileConsumer
				);
			} catch (InvocationTargetException exception) {
				Throwable cause = exception.getCause();
				if (cause instanceof RuntimeException runtimeException) {
					throw runtimeException;
				}
				if (cause instanceof Error error) {
					throw error;
				}
				throw exception;
			}
		}

		private int colorToRgb(Object color) throws ReflectiveOperationException {
			if (color == null) {
				return MISSING_RGB;
			}
			Object flattened = this.colorFlatten.invoke(color);
			int argb = ((Number) this.colorGetInt.invoke(flattened)).intValue();
			int alpha = (argb >>> 24) & 0xFF;
			return alpha <= 1 ? MISSING_RGB : argb & 0xFFFFFF;
		}

		private static Object newUnchecked(Constructor<?> constructor, Object... args) {
			try {
				return constructor.newInstance(args);
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException(exception);
			}
		}

		private static Object defaultValue(Class<?> type) {
			if (type == boolean.class) {
				return false;
			}
			if (type == int.class || type == short.class || type == byte.class || type == long.class) {
				return 0;
			}
			if (type == float.class || type == double.class) {
				return 0.0F;
			}
			return null;
		}

		private static List<Path> resourcePackRoots(MinecraftServer server) {
			List<Path> roots = new ArrayList<>();
			addIfExists(roots, VANILLA_CLIENT_JAR);
			addIfExists(roots, server.getServerDirectory().resolve("mods").resolve("lg2-0.1.0").resolve("src").resolve("main").resolve("resources"));
			addIfExists(roots, server.getServerDirectory().resolve("mods").resolve("lg2-0.1.0").resolve("resourcepack"));
			addIfExists(roots, server.getServerDirectory().resolve("polymer").resolve("source_assets"));
			return roots;
		}

		private static List<Path> dataPackRoots(MinecraftServer server) {
			List<Path> roots = new ArrayList<>();
			addIfExists(roots, VANILLA_COMMON_JAR);
			addIfExists(roots, server.getWorldPath(LevelResource.DATAPACK_DIR));
			addIfExists(roots, server.getServerDirectory().resolve("mods").resolve("lg2-0.1.0").resolve("src").resolve("main").resolve("resources"));
			return roots;
		}

		private static void addIfExists(List<Path> roots, Path path) {
			if (path != null && Files.exists(path)) {
				roots.add(path.toAbsolutePath().normalize());
			}
		}
	}
}
