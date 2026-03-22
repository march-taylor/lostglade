package com.lostglade.server.camera.bluemap;

import com.lostglade.server.map.MapPaletteQuantizer;
import com.lostglade.server.map.BlockTintProvider;
import com.lostglade.server.map.TextureAssetManager;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModelAccess;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockStateModelRenderer;
import de.bluecolored.bluemap.core.map.mask.Mask;
import de.bluecolored.bluemap.core.resources.BlockColorCalculatorFactory;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.PackVersion;
import de.bluecolored.bluemap.core.resources.pack.datapack.DataPack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.world.BlockState;
import de.bluecolored.bluemap.core.world.DimensionType;
import de.bluecolored.bluemap.core.world.LightData;
import de.bluecolored.bluemap.core.world.biome.Biome;
import de.bluecolored.bluemap.core.world.biome.GrassColorModifier;
import de.bluecolored.bluemap.core.world.block.BlockAccess;
import de.bluecolored.bluemap.core.world.block.BlockNeighborhood;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlueMapCameraRenderer {
	private static final int MAP_SIZE = 128;
	private static final int PACK_VERSION = 75;
	private static final double NEAR_PLANE = 0.05D;
	private static final int SNAPSHOT_MARGIN_BLOCKS = 2;
	private static final int BIOME_CONTEXT_HORIZONTAL_RADIUS = 2;
	private static final int BIOME_CONTEXT_VERTICAL_RADIUS = 1;
	private static final int NO_TINT_RGB = -1;
	private static final float[] SRGB_TO_LINEAR = buildSrgbToLinear();
	private static final Map<net.minecraft.world.level.block.state.BlockState, BlockState> BLOCK_STATE_CACHE = new ConcurrentHashMap<>();
	private static volatile RenderResources renderResources;

	private BlueMapCameraRenderer() {
	}

	public static PreparedFrame capture(ServerPlayer player, Vec3 forward, Vec3 right, Vec3 up, double maxDistance, float fovDegrees, int supersampling) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eyePosition = player.getEyePosition();
		CameraFrustum frustum = CameraFrustum.create(eyePosition, forward, right, up, maxDistance, fovDegrees);
		RenderResources resources = getRenderResources();
		WorldSnapshot snapshot = WorldSnapshot.capture(level, frustum, resources);
		FrameEnvironment environment = FrameEnvironment.capture(level);
		return new PreparedFrame(eyePosition, forward, right, up, maxDistance, fovDegrees, supersampling, snapshot, environment);
	}

	public static byte[] render(PreparedFrame preparedFrame) {
		return new FrameRenderer(preparedFrame, getRenderResources()).render();
	}

	private static RenderResources getRenderResources() {
		RenderResources cached = renderResources;
		if (cached != null) {
			return cached;
		}
		synchronized (BlueMapCameraRenderer.class) {
			cached = renderResources;
			if (cached != null) {
				return cached;
			}
			renderResources = cached = loadRenderResources();
			return cached;
		}
	}

	private static RenderResources loadRenderResources() {
		try {
			ResourcePack resourcePack = new ResourcePack(new PackVersion(PACK_VERSION, PACK_VERSION));
			DataPack dataPack = new DataPack(new PackVersion(PACK_VERSION, PACK_VERSION));
			List<Path> packs = new ArrayList<>();
			Path clientJar = TextureAssetManager.get().clientJarPath();
			if (clientJar != null && Files.exists(clientJar)) {
				packs.add(clientJar);
			}
			Path polymerPack = Path.of("/home/mart/Desktop/lostglade/polymer/source_assets");
			if (Files.isDirectory(polymerPack)) {
				packs.add(polymerPack);
			}
			Path devPack = Path.of("/home/mart/Desktop/lostglade/mods/lg2-0.1.0/resourcepack");
			if (Files.isDirectory(devPack)) {
				packs.add(devPack);
			}
			resourcePack.loadResources(packs);
			dataPack.loadResources(packs);

			TextureGallery textureGallery = new TextureGallery();
			textureGallery.put(resourcePack.getTextures());

			Int2ObjectOpenHashMap<TextureMaterial> materials = new Int2ObjectOpenHashMap<>();
			for (ResourcePath<Texture> path : resourcePack.getTextures().paths()) {
				int materialId = textureGallery.get(path);
				Texture texture = path.getResource();
				if (texture != null) {
					materials.put(materialId, TextureMaterial.from(texture));
				}
			}
			if (!materials.containsKey(0)) {
				materials.put(0, TextureMaterial.missing());
			}

			return new RenderResources(resourcePack, dataPack, textureGallery, materials);
		} catch (IOException | InterruptedException exception) {
			throw new IllegalStateException("Failed to initialize BlueMap render resources", exception);
		}
	}

	private static float[] buildSrgbToLinear() {
		float[] table = new float[256];
		for (int value = 0; value < table.length; value++) {
			float normalized = value / 255.0F;
			table[value] = normalized <= 0.04045F
					? normalized / 12.92F
					: (float) Math.pow((normalized + 0.055F) / 1.055F, 2.4D);
		}
		return table;
	}

	private static float toLinear(int channel) {
		return SRGB_TO_LINEAR[channel & 0xFF];
	}

	private static float toLinear(float srgb) {
		float clamped = Mth.clamp(srgb, 0.0F, 1.0F);
		return clamped <= 0.04045F
				? clamped / 12.92F
				: (float) Math.pow((clamped + 0.055F) / 1.055F, 2.4D);
	}

	private static int toSrgb(float linear) {
		float clamped = Mth.clamp(linear, 0.0F, 1.0F);
		float srgb = clamped <= 0.0031308F
				? clamped * 12.92F
				: 1.055F * (float) Math.pow(clamped, 1.0F / 2.4F) - 0.055F;
		return Mth.clamp(Math.round(srgb * 255.0F), 0, 255);
	}

	private static BlockState toBlueMapState(net.minecraft.world.level.block.state.BlockState state) {
		return BLOCK_STATE_CACHE.computeIfAbsent(state, BlueMapCameraRenderer::convertBlockState);
	}

	private static BlockState convertBlockState(net.minecraft.world.level.block.state.BlockState state) {
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		Map<String, String> properties = new HashMap<>();
		for (Property<?> property : state.getProperties()) {
			properties.put(property.getName(), propertyValueName(state, property));
		}
		return new BlockState(new Key(blockId.getNamespace(), blockId.getPath()), properties);
	}

	@SuppressWarnings("unchecked")
	private static <T extends Comparable<T>> String propertyValueName(net.minecraft.world.level.block.state.BlockState state, Property<?> property) {
		Property<T> typedProperty = (Property<T>) property;
		return typedProperty.getName(state.getValue(typedProperty));
	}

	public record PreparedFrame(
			Vec3 eyePosition,
			Vec3 forward,
			Vec3 right,
			Vec3 up,
			double maxDistance,
			float fovDegrees,
			int supersampling,
			WorldSnapshot snapshot,
			FrameEnvironment environment
	) {
	}

	private record FrameEnvironment(
			DimensionType dimensionType,
			boolean raining,
			boolean skylight,
			float ambientLight,
			float sunlightStrength
	) {
		private static FrameEnvironment capture(ServerLevel level) {
			boolean skylight = level.dimensionType().hasSkyLight();
			float ambient = Mth.clamp(level.dimensionType().ambientLight(), 0.0F, 1.0F);
			float sunlight = skylight ? Mth.clamp(1.0F - level.getSkyDarken() / 15.0F, 0.0F, 1.0F) : 0.0F;
			return new FrameEnvironment(toBlueMapDimension(level), level.isRaining(), skylight, ambient, sunlight);
		}
	}

	private static DimensionType toBlueMapDimension(ServerLevel level) {
		if (level.dimension() == Level.NETHER) {
			return DimensionType.NETHER;
		}
		if (level.dimension() == Level.END) {
			return DimensionType.END;
		}
		return level.dimensionType().hasCeiling() ? DimensionType.OVERWORLD_CAVES : DimensionType.OVERWORLD;
	}

	private static final class WorldSnapshot {
		private final Long2ObjectOpenHashMap<SnapshotBlock> blocks;
		private final int minX;
		private final int minY;
		private final int minZ;
		private final int maxX;
		private final int maxY;
		private final int maxZ;

		private WorldSnapshot(Long2ObjectOpenHashMap<SnapshotBlock> blocks, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
			this.blocks = blocks;
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}

		private static WorldSnapshot capture(ServerLevel level, CameraFrustum frustum, RenderResources resources) {
			BlockBounds bounds = frustum.bounds();
			Long2ObjectOpenHashMap<SnapshotBlock> blocks = new Long2ObjectOpenHashMap<>();
			LongOpenHashSet airContext = new LongOpenHashSet();
			Map<String, Biome> biomeCache = new HashMap<>();
			BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
			int contextMinX = bounds.minX() - BIOME_CONTEXT_HORIZONTAL_RADIUS;
			int contextMaxX = bounds.maxX() + BIOME_CONTEXT_HORIZONTAL_RADIUS;
			int contextMinY = bounds.minY() - BIOME_CONTEXT_VERTICAL_RADIUS;
			int contextMaxY = bounds.maxY() + BIOME_CONTEXT_VERTICAL_RADIUS;
			int contextMinZ = bounds.minZ() - BIOME_CONTEXT_HORIZONTAL_RADIUS;
			int contextMaxZ = bounds.maxZ() + BIOME_CONTEXT_HORIZONTAL_RADIUS;

			int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
			int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
			int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
			int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());

			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
					LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
					if (chunk == null) {
						continue;
					}

					ChunkPos chunkPos = chunk.getPos();
					int chunkMinX = chunkPos.getMinBlockX();
					int chunkMinZ = chunkPos.getMinBlockZ();
					LevelChunkSection[] sections = chunk.getSections();
					for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
						LevelChunkSection section = sections[sectionIndex];
						if (section == null || section.hasOnlyAir()) {
							continue;
						}

						int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
						int sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
						if (!frustum.intersectsAabb(
								chunkMinX,
								sectionMinY,
								chunkMinZ,
								chunkMinX + 16.0D,
								sectionMinY + 16.0D,
								chunkMinZ + 16.0D
						)) {
							continue;
						}

						int startX = Math.max(0, bounds.minX() - chunkMinX);
						int endX = Math.min(15, bounds.maxX() - chunkMinX);
						int startY = Math.max(0, bounds.minY() - sectionMinY);
						int endY = Math.min(15, bounds.maxY() - sectionMinY);
						int startZ = Math.max(0, bounds.minZ() - chunkMinZ);
						int endZ = Math.min(15, bounds.maxZ() - chunkMinZ);
						for (int localY = startY; localY <= endY; localY++) {
							int worldY = sectionMinY + localY;
							for (int localZ = startZ; localZ <= endZ; localZ++) {
								int worldZ = chunkMinZ + localZ;
								for (int localX = startX; localX <= endX; localX++) {
									net.minecraft.world.level.block.state.BlockState state = section.getBlockState(localX, localY, localZ);
									if (state.isAir()) {
										continue;
									}

									int worldX = chunkMinX + localX;
									if (!frustum.intersectsAabb(worldX, worldY, worldZ, worldX + 1.0D, worldY + 1.0D, worldZ + 1.0D)) {
										continue;
									}

									cursor.set(worldX, worldY, worldZ);
									Biome biome = resolveBiome(level, cursor, biomeCache, resources.dataPack());
									LightData light = new LightData(
											level.getBrightness(LightLayer.SKY, cursor),
											level.getBrightness(LightLayer.BLOCK, cursor)
									);
									int primaryTintRgb = firstTint(BlockTintProvider.capture(level, cursor, state));
									int waterTintRgb = state.getFluidState().is(FluidTags.WATER)
											? firstTint(BlockTintProvider.capture(level, cursor, Blocks.WATER.defaultBlockState()))
											: NO_TINT_RGB;
									blocks.put(
											BlockPos.asLong(worldX, worldY, worldZ),
											new SnapshotBlock(toBlueMapState(state), light, biome, primaryTintRgb, waterTintRgb)
									);
									collectAirContext(
											worldX,
											worldY,
											worldZ,
											contextMinX,
											contextMaxX,
											contextMinY,
											contextMaxY,
											contextMinZ,
											contextMaxZ,
											airContext
									);
								}
							}
						}
					}
				}
			}

			for (long packedPos : airContext) {
				if (blocks.containsKey(packedPos)) {
					continue;
				}

				int x = BlockPos.getX(packedPos);
				int y = BlockPos.getY(packedPos);
				int z = BlockPos.getZ(packedPos);
				if (y < level.getMinY() || y >= level.getMaxY()) {
					continue;
				}

				cursor.set(x, y, z);
				Biome biome = resolveBiome(level, cursor, biomeCache, resources.dataPack());
				LightData light = new LightData(
						level.getBrightness(LightLayer.SKY, cursor),
						level.getBrightness(LightLayer.BLOCK, cursor)
				);
				blocks.put(packedPos, new SnapshotBlock(BlockState.AIR, light, biome, NO_TINT_RGB, NO_TINT_RGB));
			}

			return new WorldSnapshot(
					blocks,
					bounds.minX(),
					bounds.minY(),
					bounds.minZ(),
					bounds.maxX(),
					bounds.maxY(),
					bounds.maxZ()
			);
		}

		private static void collectAirContext(
				int x,
				int y,
				int z,
				int minX,
				int maxX,
				int minY,
				int maxY,
				int minZ,
				int maxZ,
				LongOpenHashSet airContext
		) {
			for (int offsetY = -BIOME_CONTEXT_VERTICAL_RADIUS; offsetY <= BIOME_CONTEXT_VERTICAL_RADIUS; offsetY++) {
				int worldY = y + offsetY;
				if (worldY < minY || worldY > maxY) {
					continue;
				}

				for (int offsetZ = -BIOME_CONTEXT_HORIZONTAL_RADIUS; offsetZ <= BIOME_CONTEXT_HORIZONTAL_RADIUS; offsetZ++) {
					int worldZ = z + offsetZ;
					if (worldZ < minZ || worldZ > maxZ) {
						continue;
					}

					for (int offsetX = -BIOME_CONTEXT_HORIZONTAL_RADIUS; offsetX <= BIOME_CONTEXT_HORIZONTAL_RADIUS; offsetX++) {
						if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
							continue;
						}

						int worldX = x + offsetX;
						if (worldX < minX || worldX > maxX) {
							continue;
						}
						airContext.add(BlockPos.asLong(worldX, worldY, worldZ));
					}
				}
			}
		}

		private static int firstTint(int[] tintLayers) {
			for (int tint : tintLayers) {
				if (tint >= 0) {
					return tint;
				}
			}
			return NO_TINT_RGB;
		}

		private SnapshotBlock blockAt(int x, int y, int z) {
			return this.blocks.get(BlockPos.asLong(x, y, z));
		}

		private Iterable<Long2ObjectMap.Entry<SnapshotBlock>> entries() {
			return this.blocks.long2ObjectEntrySet();
		}
	}

	private record SnapshotBlock(BlockState state, LightData light, Biome biome, int primaryTintRgb, int waterTintRgb) {
	}

	private static Biome resolveBiome(ServerLevel level, BlockPos pos, Map<String, Biome> cache, DataPack dataPack) {
		Holder<net.minecraft.world.level.biome.Biome> biomeHolder = level.getBiome(pos);
		net.minecraft.world.level.biome.Biome minecraftBiome = biomeHolder.value();
		String key = biomeHolder.unwrapKey()
				.<String>map(resourceKey -> normalizeBiomeKey(resourceKey.toString()))
				.orElse("minecraft:plains");
		return cache.computeIfAbsent(key, ignored -> {
			Biome dataPackBiome = null;
			try {
				dataPackBiome = dataPack.getBiome(Key.parse(key));
			} catch (IllegalArgumentException ignoredException) {
			}
			return SnapshotBiome.from(key, dataPackBiome, minecraftBiome);
		});
	}

	private static String normalizeBiomeKey(String rawKey) {
		int separator = rawKey.lastIndexOf(" / ");
		if (separator >= 0 && separator + 3 < rawKey.length()) {
			int end = rawKey.endsWith("]") ? rawKey.length() - 1 : rawKey.length();
			return rawKey.substring(separator + 3, end).trim();
		}
		return rawKey;
	}

	private static final class SnapshotBiome implements Biome {
		private final Key key;
		private final float downfall;
		private final float temperature;
		private final Color waterColor;
		private final Color overlayFoliageColor;
		private final Color overlayDryFoliageColor;
		private final Color overlayGrassColor;
		private final GrassColorModifier grassColorModifier;

		private SnapshotBiome(
				Key key,
				float downfall,
				float temperature,
				Color waterColor,
				Color overlayFoliageColor,
				Color overlayDryFoliageColor,
				Color overlayGrassColor,
				GrassColorModifier grassColorModifier
		) {
			this.key = key;
			this.downfall = downfall;
			this.temperature = temperature;
			this.waterColor = waterColor;
			this.overlayFoliageColor = overlayFoliageColor;
			this.overlayDryFoliageColor = overlayDryFoliageColor;
			this.overlayGrassColor = overlayGrassColor;
			this.grassColorModifier = grassColorModifier;
		}

		private static SnapshotBiome from(String key, Biome dataPackBiome, net.minecraft.world.level.biome.Biome minecraftBiome) {
			float temperature = dataPackBiome != null ? dataPackBiome.getTemperature() : invokeFloat(minecraftBiome, "getBaseTemperature", 0.8F);
			float downfall = dataPackBiome != null ? dataPackBiome.getDownfall() : invokeFloat(minecraftBiome, "getDownfall", 0.4F);
			BiomeSpecialEffects effects = minecraftBiome.getSpecialEffects();
			Color runtimeFoliageOverride = resolveOptionalColor(effects, "getFoliageColorOverride");
			Color runtimeDryFoliageOverride = resolveOptionalColor(effects, "getDryFoliageColorOverride");
			Color runtimeGrassOverride = resolveOptionalColor(effects, "getGrassColorOverride");
			GrassColorModifier runtimeGrassModifier = resolveGrassModifier(minecraftBiome);
			Key biomeKey;
			try {
				biomeKey = Key.parse(key);
			} catch (IllegalArgumentException ignored) {
				biomeKey = Key.minecraft("plains");
			}
			return new SnapshotBiome(
					biomeKey,
					downfall,
					temperature,
					colorOf(minecraftBiome.getWaterColor()),
					preferRuntimeColor(runtimeFoliageOverride, dataPackBiome == null ? null : dataPackBiome.getOverlayFoliageColor()),
					preferRuntimeColor(runtimeDryFoliageOverride, dataPackBiome == null ? null : dataPackBiome.getOverlayDryFoliageColor()),
					preferRuntimeColor(runtimeGrassOverride, dataPackBiome == null ? null : dataPackBiome.getOverlayGrassColor()),
					preferRuntimeModifier(runtimeGrassModifier, dataPackBiome == null ? null : dataPackBiome.getGrassColorModifier())
			);
		}

		private static float invokeFloat(Object target, String methodName, float fallback) {
			try {
				Method method = target.getClass().getMethod(methodName);
				Object value = method.invoke(target);
				if (value instanceof Number number) {
					return number.floatValue();
				}
			} catch (ReflectiveOperationException ignored) {
			}
			return fallback;
		}

		private static GrassColorModifier resolveGrassModifier(net.minecraft.world.level.biome.Biome minecraftBiome) {
			try {
				BiomeSpecialEffects effects = minecraftBiome.getSpecialEffects();
				Object modifier = invokeObject(effects, "getGrassColorModifier");
				if (modifier == null) {
					modifier = invokeObject(effects, "grassColorModifier");
				}
				String modifierName = modifier == null ? "" : modifier.toString().toLowerCase(Locale.ROOT);
				return switch (modifierName) {
					case "dark_forest" -> GrassColorModifier.DARK_FOREST;
					case "swamp" -> GrassColorModifier.SWAMP;
					default -> GrassColorModifier.NONE;
				};
			} catch (Exception ignored) {
				return GrassColorModifier.NONE;
			}
		}

		private static Object invokeObject(Object target, String methodName) {
			try {
				Method method = target.getClass().getMethod(methodName);
				return method.invoke(target);
			} catch (ReflectiveOperationException ignored) {
				return null;
			}
		}

		private static Color resolveOptionalColor(Object target, String... methodNames) {
			for (String methodName : methodNames) {
				Color color = optionalColorOf(invokeObject(target, methodName));
				if (color != null) {
					return color;
				}
			}
			return transparentColor();
		}

		private static Color optionalColorOf(Object value) {
			if (value == null) {
				return null;
			}
			if (value instanceof java.util.Optional<?> optional) {
				return optional.map(SnapshotBiome::optionalColorOf).orElseGet(SnapshotBiome::transparentColor);
			}
			if (value instanceof java.util.OptionalInt optionalInt) {
				return optionalInt.isPresent() ? colorOf(optionalInt.getAsInt()) : transparentColor();
			}
			if (value instanceof Number number) {
				return colorOf(number.intValue());
			}
			return null;
		}

		private static Color colorOf(int rgb) {
			return new Color().set(
					((rgb >> 16) & 0xFF) / 255.0F,
					((rgb >> 8) & 0xFF) / 255.0F,
					(rgb & 0xFF) / 255.0F,
					1.0F,
					false
			);
		}

		private static Color transparentColor() {
			return new Color().set(0.0F, 0.0F, 0.0F, 0.0F, false);
		}

		private static Color preferRuntimeColor(Color runtimeColor, Color fallbackColor) {
			if (runtimeColor != null && runtimeColor.a > 0.0F) {
				return runtimeColor;
			}
			return copyColor(fallbackColor);
		}

		private static GrassColorModifier preferRuntimeModifier(GrassColorModifier runtimeModifier, GrassColorModifier fallbackModifier) {
			if (runtimeModifier != null && runtimeModifier != GrassColorModifier.NONE) {
				return runtimeModifier;
			}
			return fallbackModifier == null ? GrassColorModifier.NONE : fallbackModifier;
		}

		private static Color copyColor(Color color) {
			return color == null ? transparentColor() : new Color().set(color);
		}

		@Override
		public Key getKey() {
			return this.key;
		}

		@Override
		public float getDownfall() {
			return this.downfall;
		}

		@Override
		public float getTemperature() {
			return this.temperature;
		}

		@Override
		public Color getWaterColor() {
			return new Color().set(this.waterColor);
		}

		@Override
		public Color getOverlayFoliageColor() {
			return new Color().set(this.overlayFoliageColor);
		}

		@Override
		public Color getOverlayDryFoliageColor() {
			return new Color().set(this.overlayDryFoliageColor);
		}

		@Override
		public Color getOverlayGrassColor() {
			return new Color().set(this.overlayGrassColor);
		}

		@Override
		public GrassColorModifier getGrassColorModifier() {
			return this.grassColorModifier;
		}
	}

	private static final class SnapshotBlockAccess implements BlockAccess {
		private final WorldSnapshot snapshot;
		private int x;
		private int y;
		private int z;

		private SnapshotBlockAccess(WorldSnapshot snapshot, int x, int y, int z) {
			this.snapshot = snapshot;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public void set(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public BlockAccess copy() {
			return new SnapshotBlockAccess(this.snapshot, this.x, this.y, this.z);
		}

		@Override
		public int getX() {
			return this.x;
		}

		@Override
		public int getY() {
			return this.y;
		}

		@Override
		public int getZ() {
			return this.z;
		}

		@Override
		public BlockState getBlockState() {
			SnapshotBlock block = this.snapshot.blockAt(this.x, this.y, this.z);
			return block == null ? BlockState.AIR : block.state();
		}

		@Override
		public LightData getLightData() {
			SnapshotBlock block = this.snapshot.blockAt(this.x, this.y, this.z);
			return block == null ? new LightData(15, 0) : block.light();
		}

		@Override
		public Biome getBiome() {
			SnapshotBlock block = this.snapshot.blockAt(this.x, this.y, this.z);
			return block == null ? Biome.DEFAULT : block.biome();
		}

		@Override
		public de.bluecolored.bluemap.core.world.BlockEntity getBlockEntity() {
			return null;
		}

		@Override
		public boolean hasOceanFloorY() {
			return false;
		}

		@Override
		public int getOceanFloorY() {
			return 0;
		}
	}

	private static final class StaticRenderSettings implements RenderSettings {
		private final WorldSnapshot snapshot;

		private StaticRenderSettings(WorldSnapshot snapshot) {
			this.snapshot = snapshot;
		}

		@Override
		public int getRemoveCavesBelowY() {
			return Integer.MIN_VALUE;
		}

		@Override
		public int getCaveDetectionOceanFloor() {
			return Integer.MIN_VALUE;
		}

		@Override
		public boolean isCaveDetectionUsesBlockLight() {
			return false;
		}

		@Override
		public float getAmbientLight() {
			return 0.10F;
		}

		@Override
		public Mask getRenderMask() {
			return Mask.ALL;
		}

		@Override
		public boolean isInsideRenderBoundaries(int x, int z) {
			return x >= this.snapshot.minX && x <= this.snapshot.maxX && z >= this.snapshot.minZ && z <= this.snapshot.maxZ;
		}

		@Override
		public boolean isInsideRenderBoundaries(int x, int y, int z) {
			return x >= this.snapshot.minX && x <= this.snapshot.maxX
					&& y >= this.snapshot.minY && y <= this.snapshot.maxY
					&& z >= this.snapshot.minZ && z <= this.snapshot.maxZ;
		}

		@Override
		public boolean isSaveHiresLayer() {
			return true;
		}

		@Override
		public boolean isRenderTopOnly() {
			return false;
		}
	}

	private record RenderResources(
			ResourcePack resourcePack,
			DataPack dataPack,
			TextureGallery textureGallery,
			Int2ObjectOpenHashMap<TextureMaterial> materials
	) {
	}

	private static final class TextureMaterial {
		private final int width;
		private final int height;
		private final int[] pixels;
		private final boolean transparent;

		private TextureMaterial(int width, int height, int[] pixels, boolean transparent) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
			this.transparent = transparent;
		}

		private static TextureMaterial from(Texture texture) {
			try {
				BufferedImage image = texture.getTextureImage();
				if (image == null) {
					return missing();
				}
				int width = Math.max(1, image.getWidth());
				int height = Math.max(1, image.getHeight());
				int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
				boolean transparent = false;
				for (int pixel : pixels) {
					if (((pixel >>> 24) & 0xFF) < 255) {
						transparent = true;
						break;
					}
				}
				return new TextureMaterial(width, height, pixels, transparent);
			} catch (IOException exception) {
				return missing();
			}
		}

		private static TextureMaterial missing() {
			return new TextureMaterial(1, 1, new int[]{0xFFFF00FF}, false);
		}

		private int sample(float u, float v) {
			float wrappedU = u - (float) Math.floor(u);
			float wrappedV = v - (float) Math.floor(v);
			float x = wrappedU * (this.width - 1);
			float y = wrappedV * (this.height - 1);
			int x0 = Mth.floor(x);
			int y0 = Mth.floor(y);
			int x1 = (x0 + 1) % this.width;
			int y1 = (y0 + 1) % this.height;
			float tx = x - x0;
			float ty = y - y0;
			int c00 = this.pixels[y0 * this.width + x0];
			int c10 = this.pixels[y0 * this.width + x1];
			int c01 = this.pixels[y1 * this.width + x0];
			int c11 = this.pixels[y1 * this.width + x1];
			float a00 = ((c00 >>> 24) & 0xFF) / 255.0F;
			float a10 = ((c10 >>> 24) & 0xFF) / 255.0F;
			float a01 = ((c01 >>> 24) & 0xFF) / 255.0F;
			float a11 = ((c11 >>> 24) & 0xFF) / 255.0F;
			float r00 = ((c00 >> 16) & 0xFF) / 255.0F;
			float r10 = ((c10 >> 16) & 0xFF) / 255.0F;
			float r01 = ((c01 >> 16) & 0xFF) / 255.0F;
			float r11 = ((c11 >> 16) & 0xFF) / 255.0F;
			float g00 = ((c00 >> 8) & 0xFF) / 255.0F;
			float g10 = ((c10 >> 8) & 0xFF) / 255.0F;
			float g01 = ((c01 >> 8) & 0xFF) / 255.0F;
			float g11 = ((c11 >> 8) & 0xFF) / 255.0F;
			float b00 = (c00 & 0xFF) / 255.0F;
			float b10 = (c10 & 0xFF) / 255.0F;
			float b01 = (c01 & 0xFF) / 255.0F;
			float b11 = (c11 & 0xFF) / 255.0F;

			float a0 = Mth.lerp(tx, a00, a10);
			float a1 = Mth.lerp(tx, a01, a11);
			float r0 = Mth.lerp(tx, r00, r10);
			float r1 = Mth.lerp(tx, r01, r11);
			float g0 = Mth.lerp(tx, g00, g10);
			float g1 = Mth.lerp(tx, g01, g11);
			float b0 = Mth.lerp(tx, b00, b10);
			float b1 = Mth.lerp(tx, b01, b11);

			int alpha = Mth.clamp(Math.round(Mth.lerp(ty, a0, a1) * 255.0F), 0, 255);
			int red = Mth.clamp(Math.round(Mth.lerp(ty, r0, r1) * 255.0F), 0, 255);
			int green = Mth.clamp(Math.round(Mth.lerp(ty, g0, g1) * 255.0F), 0, 255);
			int blue = Mth.clamp(Math.round(Mth.lerp(ty, b0, b1) * 255.0F), 0, 255);
			return (alpha << 24) | (red << 16) | (green << 8) | blue;
		}
	}

	private static final class FrameRenderer {
		private final PreparedFrame frame;
		private final RenderResources resources;
		private final int internalSize;
		private final float tanHalfFov;
		private final float[] red;
		private final float[] green;
		private final float[] blue;
		private final float[] opaqueDepth;

		private FrameRenderer(PreparedFrame frame, RenderResources resources) {
			this.frame = frame;
			this.resources = resources;
			this.internalSize = MAP_SIZE * Mth.clamp(frame.supersampling(), 1, 4);
			this.tanHalfFov = (float) Math.tan(Math.toRadians(frame.fovDegrees() * 0.5D));
			int pixelCount = this.internalSize * this.internalSize;
			this.red = new float[pixelCount];
			this.green = new float[pixelCount];
			this.blue = new float[pixelCount];
			this.opaqueDepth = new float[pixelCount];
			for (int i = 0; i < pixelCount; i++) {
				this.opaqueDepth[i] = Float.POSITIVE_INFINITY;
			}
		}

		private byte[] render() {
			fillBackground();
			ArrayTileModel model = buildGeometry();
			renderModel(model);
			return downsample();
		}

		private void fillBackground() {
			for (int y = 0; y < this.internalSize; y++) {
				for (int x = 0; x < this.internalSize; x++) {
					Vec3 direction = rayDirection(x + 0.5D, y + 0.5D);
					int rgb = skyColor(direction);
					int index = y * this.internalSize + x;
					this.red[index] = toLinear((rgb >> 16) & 0xFF);
					this.green[index] = toLinear((rgb >> 8) & 0xFF);
					this.blue[index] = toLinear(rgb & 0xFF);
				}
			}
		}

		private Vec3 rayDirection(double pixelX, double pixelY) {
			double sensorX = (pixelX / this.internalSize * 2.0D - 1.0D) * this.tanHalfFov;
			double sensorY = (1.0D - pixelY / this.internalSize * 2.0D) * this.tanHalfFov;
			return this.frame.forward()
					.add(this.frame.right().scale(sensorX))
					.add(this.frame.up().scale(sensorY))
					.normalize();
		}

		private int skyColor(Vec3 direction) {
			if (this.frame.environment().dimensionType() == DimensionType.NETHER) {
				return 0xA54B2A;
			}
			if (this.frame.environment().dimensionType() == DimensionType.END) {
				return 0x6A5E8F;
			}

			float sunlight = this.frame.environment().sunlightStrength();
			float ambient = Math.max(this.frame.environment().ambientLight(), sunlight * sunlight);
			float vertical = Mth.clamp((float) ((direction.y + 1.0D) * 0.5D), 0.0F, 1.0F);
			int top = scaleRgb(0x8FC9FF, ambient);
			int horizon = scaleRgb(0xD8F0FF, ambient);
			int nadir = scaleRgb(0xA7C2D9, ambient * 0.9F);
			int rgb = direction.y >= 0.0D
					? lerpRgb(horizon, top, vertical)
					: lerpRgb(nadir, horizon, vertical);
			if (this.frame.environment().raining()) {
				rgb = lerpRgb(rgb, 0x7286A0, 0.45F);
			}
			return rgb;
		}

		private ArrayTileModel buildGeometry() {
			WorldSnapshot snapshot = this.frame.snapshot();
			RenderSettings renderSettings = new StaticRenderSettings(snapshot);
			ArrayTileModel model = new ArrayTileModel(8192);
			TileModelView tileModelView = new TileModelView(model);
			BlockStateModelRenderer blockRenderer = new BlockStateModelRenderer(this.resources.resourcePack(), this.resources.textureGallery(), renderSettings);
			BlockColorCalculatorFactory.BlockColorCalculator colorCalculator = this.resources.resourcePack().getColorCalculatorFactory().createCalculator();
			BlockNeighborhood neighborhood = new BlockNeighborhood(
					new SnapshotBlockAccess(snapshot, 0, 0, 0),
					this.resources.resourcePack(),
					renderSettings,
					this.frame.environment().dimensionType()
			);
			Color scratchColor = new Color();
			Color primaryTintColor = new Color();
			Color waterTintColor = new Color();

			for (Long2ObjectMap.Entry<SnapshotBlock> entry : snapshot.entries()) {
				SnapshotBlock snapshotBlock = entry.getValue();
				if (snapshotBlock == null || snapshotBlock.state().isAir()) {
					continue;
				}

				long packedPos = entry.getLongKey();
				int x = BlockPos.getX(packedPos);
				int y = BlockPos.getY(packedPos);
				int z = BlockPos.getZ(packedPos);
				neighborhood.set(x, y, z);
				tileModelView.initialize();
				int triangleStart = tileModelView.getStart();
				boolean hasPrimaryTintMarker = sampleTintColor(colorCalculator, neighborhood, snapshotBlock.state(), primaryTintColor);
				boolean hasWaterTintMarker = snapshotBlock.waterTintRgb() != NO_TINT_RGB
						&& sampleTintColor(colorCalculator, neighborhood, BlockState.WATER, waterTintColor);
				blockRenderer.render(neighborhood, tileModelView, scratchColor);
				if (tileModelView.getSize() > 0) {
					applyWorldTint(
							model,
							triangleStart,
							tileModelView.getSize(),
							snapshotBlock,
							hasPrimaryTintMarker ? primaryTintColor : null,
							hasWaterTintMarker ? waterTintColor : null
					);
					tileModelView.translate(x, y, z);
				}
			}

			return model;
		}

		private void applyWorldTint(
				ArrayTileModel model,
				int triangleStart,
				int triangleCount,
				SnapshotBlock snapshotBlock,
				Color primaryTintColor,
				Color waterTintColor
		) {
			boolean adjustPrimary = shouldAdjustTint(snapshotBlock.primaryTintRgb(), primaryTintColor);
			boolean adjustWater = shouldAdjustTint(snapshotBlock.waterTintRgb(), waterTintColor);
			if (!adjustPrimary && !adjustWater) {
				return;
			}

			float[] colors = ArrayTileModelAccess.colors(model);
			for (int triangleIndex = triangleStart; triangleIndex < triangleStart + triangleCount; triangleIndex++) {
				int colorBase = triangleIndex * 3;
				if (adjustPrimary && matchesTint(colors, colorBase, primaryTintColor)) {
					applyTintRatio(colors, colorBase, primaryTintColor, snapshotBlock.primaryTintRgb());
					continue;
				}
				if (adjustWater && matchesTint(colors, colorBase, waterTintColor)) {
					applyTintRatio(colors, colorBase, waterTintColor, snapshotBlock.waterTintRgb());
				}
			}
		}

		private static boolean sampleTintColor(
				BlockColorCalculatorFactory.BlockColorCalculator colorCalculator,
				BlockNeighborhood neighborhood,
				BlockState blockState,
				Color tintColor
		) {
			colorCalculator.getBlockColor(neighborhood, blockState, tintColor);
			return !isApproximatelyWhite(tintColor);
		}

		private static boolean shouldAdjustTint(int desiredTintRgb, Color markerTint) {
			return desiredTintRgb != NO_TINT_RGB
					&& markerTint != null
					&& !matchesTintRgb(markerTint, desiredTintRgb);
		}

		private static boolean matchesTint(float[] colors, int colorBase, Color tintColor) {
			float epsilon = 1.0F / 255.0F + 1.0E-4F;
			return Math.abs(colors[colorBase] - tintColor.r) <= epsilon
					&& Math.abs(colors[colorBase + 1] - tintColor.g) <= epsilon
					&& Math.abs(colors[colorBase + 2] - tintColor.b) <= epsilon;
		}

		private static boolean matchesTintRgb(Color tintColor, int rgb) {
			float epsilon = 1.0F / 255.0F + 1.0E-4F;
			return Math.abs(tintColor.r - ((rgb >> 16) & 0xFF) / 255.0F) <= epsilon
					&& Math.abs(tintColor.g - ((rgb >> 8) & 0xFF) / 255.0F) <= epsilon
					&& Math.abs(tintColor.b - (rgb & 0xFF) / 255.0F) <= epsilon;
		}

		private static boolean isApproximatelyWhite(Color tintColor) {
			float epsilon = 1.0F / 255.0F + 1.0E-4F;
			return Math.abs(tintColor.r - 1.0F) <= epsilon
					&& Math.abs(tintColor.g - 1.0F) <= epsilon
					&& Math.abs(tintColor.b - 1.0F) <= epsilon;
		}

		private static void applyTintRatio(float[] colors, int colorBase, Color markerTint, int desiredRgb) {
			float desiredR = ((desiredRgb >> 16) & 0xFF) / 255.0F;
			float desiredG = ((desiredRgb >> 8) & 0xFF) / 255.0F;
			float desiredB = (desiredRgb & 0xFF) / 255.0F;
			colors[colorBase] = scaleTintChannel(colors[colorBase], markerTint.r, desiredR);
			colors[colorBase + 1] = scaleTintChannel(colors[colorBase + 1], markerTint.g, desiredG);
			colors[colorBase + 2] = scaleTintChannel(colors[colorBase + 2], markerTint.b, desiredB);
		}

		private static float scaleTintChannel(float original, float marker, float desired) {
			if (marker <= 1.0E-5F) {
				return original;
			}
			return Mth.clamp(original * (desired / marker), 0.0F, 1.0F);
		}

		private void renderModel(ArrayTileModel model) {
			float[] positions = ArrayTileModelAccess.positions(model);
			float[] uvs = ArrayTileModelAccess.uvs(model);
			float[] aos = ArrayTileModelAccess.aos(model);
			float[] colors = ArrayTileModelAccess.colors(model);
			byte[] sunlight = ArrayTileModelAccess.sunlight(model);
			byte[] blocklight = ArrayTileModelAccess.blocklight(model);
			int[] materials = ArrayTileModelAccess.materialIndices(model);

			List<RasterTriangle> transparentTriangles = new ArrayList<>();
			for (int triangleIndex = 0; triangleIndex < ArrayTileModelAccess.size(model); triangleIndex++) {
				TextureMaterial material = this.resources.materials().getOrDefault(materials[triangleIndex], this.resources.materials().get(0));
				List<RasterTriangle> clippedTriangles = buildTriangles(triangleIndex, positions, uvs, aos, colors, sunlight, blocklight, material);
				if (clippedTriangles.isEmpty()) {
					continue;
				}
				if (material.transparent) {
					transparentTriangles.addAll(clippedTriangles);
				} else {
					for (RasterTriangle triangle : clippedTriangles) {
						rasterize(triangle, true);
					}
				}
			}

			transparentTriangles.sort(Comparator.comparingDouble(RasterTriangle::sortDepth).reversed());
			for (RasterTriangle triangle : transparentTriangles) {
				rasterize(triangle, false);
			}
		}

		private List<RasterTriangle> buildTriangles(
				int triangleIndex,
				float[] positions,
				float[] uvs,
				float[] aos,
				float[] colors,
				byte[] sunlight,
				byte[] blocklight,
				TextureMaterial material
		) {
			int positionBase = triangleIndex * 9;
			int uvBase = triangleIndex * 6;
			int aoBase = triangleIndex * 3;
			int colorBase = triangleIndex * 3;
			Vertex[] vertices = new Vertex[3];
			float depthSum = 0.0F;
			for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
				float worldX = positions[positionBase + vertexIndex * 3];
				float worldY = positions[positionBase + vertexIndex * 3 + 1];
				float worldZ = positions[positionBase + vertexIndex * 3 + 2];
				Vec3 relative = new Vec3(worldX, worldY, worldZ).subtract(this.frame.eyePosition());
				float cameraX = (float) relative.dot(this.frame.right());
				float cameraY = (float) relative.dot(this.frame.up());
				float cameraZ = (float) relative.dot(this.frame.forward());
				depthSum += cameraZ;
				vertices[vertexIndex] = new Vertex(
						cameraX,
						cameraY,
						cameraZ,
						uvs[uvBase + vertexIndex * 2],
						uvs[uvBase + vertexIndex * 2 + 1],
						aos[aoBase + vertexIndex]
				);
			}

			List<Vertex> polygon = clipAgainstNearPlane(vertices);
			if (polygon.size() < 3) {
				return List.of();
			}

			float colorR = toLinear(colors[colorBase]);
			float colorG = toLinear(colors[colorBase + 1]);
			float colorB = toLinear(colors[colorBase + 2]);
			float triangleSunlight = Byte.toUnsignedInt(sunlight[triangleIndex]);
			float triangleBlocklight = Byte.toUnsignedInt(blocklight[triangleIndex]);
			List<RasterTriangle> result = new ArrayList<>(Math.max(1, polygon.size() - 2));
			for (int i = 1; i < polygon.size() - 1; i++) {
				Vertex a = project(polygon.get(0));
				Vertex b = project(polygon.get(i));
				Vertex c = project(polygon.get(i + 1));
				if (a == null || b == null || c == null) {
					continue;
				}
				result.add(new RasterTriangle(
						a,
						b,
						c,
						colorR,
						colorG,
						colorB,
						triangleSunlight,
						triangleBlocklight,
						material,
						depthSum / 3.0F
				));
			}
			return result;
		}

		private List<Vertex> clipAgainstNearPlane(Vertex[] input) {
			List<Vertex> output = new ArrayList<>(4);
			for (int i = 0; i < input.length; i++) {
				Vertex current = input[i];
				Vertex previous = input[(i + input.length - 1) % input.length];
				boolean currentInside = current.cameraZ() >= NEAR_PLANE;
				boolean previousInside = previous.cameraZ() >= NEAR_PLANE;
				if (currentInside != previousInside) {
					float delta = (float) ((NEAR_PLANE - previous.cameraZ()) / (current.cameraZ() - previous.cameraZ()));
					output.add(previous.lerp(current, delta));
				}
				if (currentInside) {
					output.add(current);
				}
			}
			return output;
		}

		private Vertex project(Vertex vertex) {
			if (vertex.cameraZ() <= NEAR_PLANE) {
				return null;
			}
			float inverseZ = 1.0F / vertex.cameraZ();
			float screenX = (float) ((vertex.cameraX() * inverseZ / this.tanHalfFov * 0.5F + 0.5F) * this.internalSize);
			float screenY = (float) ((0.5F - vertex.cameraY() * inverseZ / this.tanHalfFov * 0.5F) * this.internalSize);
			return vertex.withProjection(screenX, screenY, inverseZ);
		}

		private void rasterize(RasterTriangle triangle, boolean opaquePass) {
			float minX = Math.min(triangle.a().screenX(), Math.min(triangle.b().screenX(), triangle.c().screenX()));
			float maxX = Math.max(triangle.a().screenX(), Math.max(triangle.b().screenX(), triangle.c().screenX()));
			float minY = Math.min(triangle.a().screenY(), Math.min(triangle.b().screenY(), triangle.c().screenY()));
			float maxY = Math.max(triangle.a().screenY(), Math.max(triangle.b().screenY(), triangle.c().screenY()));
			int startX = Mth.clamp(Mth.floor(minX), 0, this.internalSize - 1);
			int endX = Mth.clamp(Mth.ceil(maxX), 0, this.internalSize - 1);
			int startY = Mth.clamp(Mth.floor(minY), 0, this.internalSize - 1);
			int endY = Mth.clamp(Mth.ceil(maxY), 0, this.internalSize - 1);
			float area = edge(triangle.a().screenX(), triangle.a().screenY(), triangle.b().screenX(), triangle.b().screenY(), triangle.c().screenX(), triangle.c().screenY());
			if (Math.abs(area) < 1.0E-6F) {
				return;
			}

			for (int y = startY; y <= endY; y++) {
				for (int x = startX; x <= endX; x++) {
					float sampleX = x + 0.5F;
					float sampleY = y + 0.5F;
					float w0 = edge(triangle.b().screenX(), triangle.b().screenY(), triangle.c().screenX(), triangle.c().screenY(), sampleX, sampleY) / area;
					float w1 = edge(triangle.c().screenX(), triangle.c().screenY(), triangle.a().screenX(), triangle.a().screenY(), sampleX, sampleY) / area;
					float w2 = 1.0F - w0 - w1;
					if (w0 < 0.0F || w1 < 0.0F || w2 < 0.0F) {
						continue;
					}

					float inverseZ = w0 * triangle.a().inverseZ() + w1 * triangle.b().inverseZ() + w2 * triangle.c().inverseZ();
					if (inverseZ <= 0.0F) {
						continue;
					}
					float depth = 1.0F / inverseZ;
					int index = y * this.internalSize + x;
					if (opaquePass) {
						if (depth >= this.opaqueDepth[index]) {
							continue;
						}
					} else if (depth >= this.opaqueDepth[index] - 1.0E-4F) {
						continue;
					}

					float u = (w0 * triangle.a().u() * triangle.a().inverseZ()
							+ w1 * triangle.b().u() * triangle.b().inverseZ()
							+ w2 * triangle.c().u() * triangle.c().inverseZ()) / inverseZ;
					float v = (w0 * triangle.a().v() * triangle.a().inverseZ()
							+ w1 * triangle.b().v() * triangle.b().inverseZ()
							+ w2 * triangle.c().v() * triangle.c().inverseZ()) / inverseZ;
					float ao = (w0 * triangle.a().ao() * triangle.a().inverseZ()
							+ w1 * triangle.b().ao() * triangle.b().inverseZ()
							+ w2 * triangle.c().ao() * triangle.c().inverseZ()) / inverseZ;

					int argb = triangle.material().sample(u, v);
					float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
					if (alpha <= 0.01F) {
						continue;
					}

					float light = Mth.lerp(
							this.frame.environment().sunlightStrength(),
							triangle.blocklight(),
							Math.max(triangle.sunlight(), triangle.blocklight())
					);
					float shade = Mth.lerp(light / 15.0F, this.frame.environment().ambientLight(), 1.0F);
					float redLinear = toLinear((argb >> 16) & 0xFF) * triangle.colorR() * ao * shade;
					float greenLinear = toLinear((argb >> 8) & 0xFF) * triangle.colorG() * ao * shade;
					float blueLinear = toLinear(argb & 0xFF) * triangle.colorB() * ao * shade;

					if (opaquePass) {
						this.red[index] = redLinear;
						this.green[index] = greenLinear;
						this.blue[index] = blueLinear;
						this.opaqueDepth[index] = depth;
					} else {
						float invAlpha = 1.0F - alpha;
						this.red[index] = redLinear * alpha + this.red[index] * invAlpha;
						this.green[index] = greenLinear * alpha + this.green[index] * invAlpha;
						this.blue[index] = blueLinear * alpha + this.blue[index] * invAlpha;
					}
				}
			}
		}

		private byte[] downsample() {
			int scale = this.internalSize / MAP_SIZE;
			byte[] output = new byte[MAP_SIZE * MAP_SIZE];
			if (scale <= 1) {
				for (int i = 0; i < output.length; i++) {
					int rgb = (toSrgb(this.red[i]) << 16) | (toSrgb(this.green[i]) << 8) | toSrgb(this.blue[i]);
					output[i] = MapPaletteQuantizer.quantize(rgb);
				}
				return output;
			}

			int sampleCount = scale * scale;
			int[] samples = new int[sampleCount];
			for (int mapY = 0; mapY < MAP_SIZE; mapY++) {
				for (int mapX = 0; mapX < MAP_SIZE; mapX++) {
					int sampleIndex = 0;
					int startY = mapY * scale;
					int startX = mapX * scale;
					for (int dy = 0; dy < scale; dy++) {
						int row = (startY + dy) * this.internalSize + startX;
						for (int dx = 0; dx < scale; dx++) {
							int index = row + dx;
							samples[sampleIndex++] = (toSrgb(this.red[index]) << 16)
									| (toSrgb(this.green[index]) << 8)
									| toSrgb(this.blue[index]);
						}
					}
					output[mapY * MAP_SIZE + mapX] = MapPaletteQuantizer.quantize(MapPaletteQuantizer.averageRgb(samples, sampleIndex));
				}
			}
			return output;
		}

		private static float edge(float ax, float ay, float bx, float by, float px, float py) {
			return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
		}

		private static int lerpRgb(int from, int to, float delta) {
			int fr = (from >> 16) & 0xFF;
			int fg = (from >> 8) & 0xFF;
			int fb = from & 0xFF;
			int tr = (to >> 16) & 0xFF;
			int tg = (to >> 8) & 0xFF;
			int tb = to & 0xFF;
			int r = Mth.floor(Mth.lerp(delta, fr, tr));
			int g = Mth.floor(Mth.lerp(delta, fg, tg));
			int b = Mth.floor(Mth.lerp(delta, fb, tb));
			return (r << 16) | (g << 8) | b;
		}

		private static int scaleRgb(int rgb, float factor) {
			int red = toSrgb(toLinear((rgb >> 16) & 0xFF) * factor);
			int green = toSrgb(toLinear((rgb >> 8) & 0xFF) * factor);
			int blue = toSrgb(toLinear(rgb & 0xFF) * factor);
			return (red << 16) | (green << 8) | blue;
		}
	}

	private record Vertex(
			float cameraX,
			float cameraY,
			float cameraZ,
			float u,
			float v,
			float ao,
			float screenX,
			float screenY,
			float inverseZ
	) {
		private Vertex(float cameraX, float cameraY, float cameraZ, float u, float v, float ao) {
			this(cameraX, cameraY, cameraZ, u, v, ao, 0.0F, 0.0F, 0.0F);
		}

		private Vertex lerp(Vertex other, float delta) {
			return new Vertex(
					Mth.lerp(delta, this.cameraX, other.cameraX),
					Mth.lerp(delta, this.cameraY, other.cameraY),
					Mth.lerp(delta, this.cameraZ, other.cameraZ),
					Mth.lerp(delta, this.u, other.u),
					Mth.lerp(delta, this.v, other.v),
					Mth.lerp(delta, this.ao, other.ao)
			);
		}

		private Vertex withProjection(float screenX, float screenY, float inverseZ) {
			return new Vertex(this.cameraX, this.cameraY, this.cameraZ, this.u, this.v, this.ao, screenX, screenY, inverseZ);
		}
	}

	private record RasterTriangle(
			Vertex a,
			Vertex b,
			Vertex c,
			float colorR,
			float colorG,
			float colorB,
			float sunlight,
			float blocklight,
			TextureMaterial material,
			float sortDepth
	) {
	}

	private record BlockBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	}

	private static final class CameraFrustum {
		private final Vec3 eye;
		private final Vec3 forward;
		private final Plane[] planes;
		private final BlockBounds bounds;

		private CameraFrustum(Vec3 eye, Vec3 forward, Plane[] planes, BlockBounds bounds) {
			this.eye = eye;
			this.forward = forward;
			this.planes = planes;
			this.bounds = bounds;
		}

		private static CameraFrustum create(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up, double maxDistance, float fovDegrees) {
			double tanHalfFov = Math.tan(Math.toRadians(fovDegrees * 0.5D));
			double farHalfWidth = tanHalfFov * maxDistance;
			double farHalfHeight = tanHalfFov * maxDistance;
			Vec3 farCenter = eye.add(forward.scale(maxDistance));
			Vec3 farTopLeft = farCenter.add(up.scale(farHalfHeight)).subtract(right.scale(farHalfWidth));
			Vec3 farTopRight = farCenter.add(up.scale(farHalfHeight)).add(right.scale(farHalfWidth));
			Vec3 farBottomLeft = farCenter.subtract(up.scale(farHalfHeight)).subtract(right.scale(farHalfWidth));
			Vec3 farBottomRight = farCenter.subtract(up.scale(farHalfHeight)).add(right.scale(farHalfWidth));
			Plane[] planes = new Plane[]{
					Plane.fromPointNormal(eye.add(forward.scale(NEAR_PLANE)), forward),
					Plane.fromPointNormal(farCenter, forward.scale(-1.0D)),
					Plane.fromTriangle(eye, farBottomLeft, farTopLeft, forward),
					Plane.fromTriangle(eye, farTopRight, farBottomRight, forward),
					Plane.fromTriangle(eye, farTopLeft, farTopRight, forward),
					Plane.fromTriangle(eye, farBottomRight, farBottomLeft, forward)
			};

			double minX = eye.x;
			double minY = eye.y;
			double minZ = eye.z;
			double maxX = eye.x;
			double maxY = eye.y;
			double maxZ = eye.z;
			for (Vec3 corner : new Vec3[]{farTopLeft, farTopRight, farBottomLeft, farBottomRight}) {
				minX = Math.min(minX, corner.x);
				minY = Math.min(minY, corner.y);
				minZ = Math.min(minZ, corner.z);
				maxX = Math.max(maxX, corner.x);
				maxY = Math.max(maxY, corner.y);
				maxZ = Math.max(maxZ, corner.z);
			}
			BlockBounds bounds = new BlockBounds(
					Mth.floor(minX) - SNAPSHOT_MARGIN_BLOCKS,
					Mth.floor(minY) - SNAPSHOT_MARGIN_BLOCKS,
					Mth.floor(minZ) - SNAPSHOT_MARGIN_BLOCKS,
					Mth.ceil(maxX) + SNAPSHOT_MARGIN_BLOCKS,
					Mth.ceil(maxY) + SNAPSHOT_MARGIN_BLOCKS,
					Mth.ceil(maxZ) + SNAPSHOT_MARGIN_BLOCKS
			);
			return new CameraFrustum(eye, forward, planes, bounds);
		}

		private BlockBounds bounds() {
			return this.bounds;
		}

		private boolean intersectsAabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			for (Plane plane : this.planes) {
				double px = plane.normalX >= 0.0D ? maxX : minX;
				double py = plane.normalY >= 0.0D ? maxY : minY;
				double pz = plane.normalZ >= 0.0D ? maxZ : minZ;
				if (plane.distance(px, py, pz) < 0.0D) {
					return false;
				}
			}
			return true;
		}
	}

	private static final class Plane {
		private final double normalX;
		private final double normalY;
		private final double normalZ;
		private final double distance;

		private Plane(double normalX, double normalY, double normalZ, double distance) {
			this.normalX = normalX;
			this.normalY = normalY;
			this.normalZ = normalZ;
			this.distance = distance;
		}

		private static Plane fromPointNormal(Vec3 point, Vec3 normal) {
			Vec3 normalized = normal.normalize();
			double distance = -(normalized.x * point.x + normalized.y * point.y + normalized.z * point.z);
			return new Plane(normalized.x, normalized.y, normalized.z, distance);
		}

		private static Plane fromTriangle(Vec3 a, Vec3 b, Vec3 c, Vec3 inward) {
			Vec3 ab = b.subtract(a);
			Vec3 ac = c.subtract(a);
			Vec3 normal = ab.cross(ac).normalize();
			if (normal.dot(inward) < 0.0D) {
				normal = normal.scale(-1.0D);
			}
			return fromPointNormal(a, normal);
		}

		private double distance(double x, double y, double z) {
			return this.normalX * x + this.normalY * y + this.normalZ * z + this.distance;
		}
	}
}
