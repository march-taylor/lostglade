package com.lostglade.server.camera.bluemap;

import com.lostglade.Lg2;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.lostglade.server.map.BlockTintProvider;
import com.lostglade.server.map.TextureAssetManager;
import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModelAccess;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.map.hires.TileModelView;
import de.bluecolored.bluemap.core.map.hires.block.BlockRendererType;
import de.bluecolored.bluemap.core.map.hires.block.BlockStateModelRenderer;
import de.bluecolored.bluemap.core.map.hires.block.LiquidModelRenderer;
import de.bluecolored.bluemap.core.map.mask.Mask;
import de.bluecolored.bluemap.core.resources.BlockColorCalculatorFactory;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.PackVersion;
import de.bluecolored.bluemap.core.resources.pack.datapack.DataPack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.Variant;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.Model;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.model.TextureVariable;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.AnimationMeta;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix3f;
import org.joml.Vector3f;

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
	private static final float DEG_TO_RAD = (float) (Math.PI / 180.0D);
	private static final float HALF_CELESTIAL_QUAD_SIZE = 0.30F;
	private static final Identifier CLOUD_TEXTURE_ID = Identifier.fromNamespaceAndPath("minecraft", "environment/clouds");
	private static final float CLOUD_CELL_SIZE = 12.0F;
	private static final float CLOUD_THICKNESS = 4.0F;
	private static final float CLOUD_SCROLL_PER_TICK = 0.030000001F;
	private static final float CLOUD_Z_OFFSET = 3.9600000381469727F;
	private static final float CLOUD_TRACE_EPSILON = 1.0E-3F;
	private static final float CLOUD_FADE_START_DISTANCE = 352.0F;
	private static final float CLOUD_FADE_END_DISTANCE = 640.0F;
	private static final float STAR_DISC_RADIUS = 0.0019F;
	private static final float SUNRISE_HORIZON_BAND = 0.42F;
	private static final float SUN_MASK_INNER_RADIUS = 0.78F;
	private static final float SUN_MASK_OUTER_RADIUS = 0.86F;
	private static final float MOON_MASK_INNER_RADIUS = 0.92F;
	private static final float MOON_MASK_OUTER_RADIUS = 1.02F;
	private static final int SUN_GLOW_RGB = 0xFFE3A2;
	private static final Identifier SUN_TEXTURE_ID = Identifier.fromNamespaceAndPath("minecraft", "environment/celestial/sun");
	private static final Identifier END_SKY_TEXTURE_ID = Identifier.fromNamespaceAndPath("minecraft", "environment/end_sky");
	private static final float[] SRGB_TO_LINEAR = buildSrgbToLinear();
	private static final Variant VANILLA_WATER_LIQUID_VARIANT = createLiquidVariant("camera_water", "block/water_still", "block/water_flow");
	private static final Variant VANILLA_LAVA_LIQUID_VARIANT = createLiquidVariant("camera_lava", "block/lava_still", "block/lava_flow");
	private static final Map<net.minecraft.world.level.block.state.BlockState, BlockState> BLOCK_STATE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, TextureMaterial> SKY_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final CloudField CLOUD_FIELD = CloudField.create();
	private static final StarField STAR_FIELD = StarField.create();
	private static volatile RenderResources renderResources;

	private BlueMapCameraRenderer() {
	}

	public static PreparedFrame capture(ServerPlayer player, Vec3 forward, Vec3 right, Vec3 up, double maxDistance, float fovDegrees, int supersampling) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eyePosition = player.getEyePosition();
		CameraFrustum frustum = CameraFrustum.create(eyePosition, forward, right, up, maxDistance, fovDegrees);
		RenderResources resources = getRenderResources();
		WorldSnapshot snapshot = WorldSnapshot.capture(level, frustum, resources);
		List<EntitySnapshot> entities = new ArrayList<>();
		entities.addAll(captureEntities(player, level, frustum, forward, right, up));
		entities.addAll(captureBlockEntities(level, frustum));
		FrameEnvironment environment = FrameEnvironment.capture(level, eyePosition);
		return new PreparedFrame(eyePosition, forward, right, up, maxDistance, fovDegrees, supersampling, snapshot, entities, environment);
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
			ensureTexturePresent(resourcePack, Identifier.fromNamespaceAndPath("minecraft", "block/water_still"));
			ensureTexturePresent(resourcePack, Identifier.fromNamespaceAndPath("minecraft", "block/water_flow"));
			ensureTexturePresent(resourcePack, Identifier.fromNamespaceAndPath("minecraft", "block/lava_still"));
			ensureTexturePresent(resourcePack, Identifier.fromNamespaceAndPath("minecraft", "block/lava_flow"));

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

	private static void ensureTexturePresent(ResourcePack resourcePack, Identifier textureId) throws IOException {
		ResourcePath<Texture> resourcePath = new ResourcePath<>(textureId.getNamespace(), textureId.getPath());
		if (resourcePack.getTextures().get(resourcePath) != null) {
			return;
		}
		BufferedImage image = TextureAssetManager.get().loadTexture(textureId);
		if (image == null) {
			return;
		}
		resourcePack.getTextures().put(resourcePath, Texture.from(resourcePath, image));
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

	private static TextureMaterial skyMaterial(Identifier textureId) {
		if (textureId == null) {
			return TextureMaterial.missing();
		}
		return SKY_TEXTURE_CACHE.computeIfAbsent(textureId.toString(), ignored -> CameraEntityRenderer.loadTextureMaterial(textureId));
	}

	private static Identifier moonTextureId(MoonPhase moonPhase) {
		MoonPhase resolvedPhase = moonPhase == null ? MoonPhase.FULL_MOON : moonPhase;
		return Identifier.fromNamespaceAndPath("minecraft", "environment/celestial/moon/" + resolvedPhase.getSerializedName());
	}

	private static Vec3 celestialDirection(float angle) {
		Vector3f vector = new Vector3f(0.0F, 1.0F, 0.0F);
		new Matrix3f()
				.rotateY((float) (-Math.PI / 2.0D))
				.rotateX(angle)
				.transform(vector);
		return new Vec3(vector.x(), vector.y(), vector.z()).normalize();
	}

	private static Vec3 inverseCelestialDirection(Vec3 direction, float angle) {
		Vector3f vector = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
		new Matrix3f()
				.rotateX(-angle)
				.rotateY((float) (Math.PI / 2.0D))
				.transform(vector);
		return new Vec3(vector.x(), vector.y(), vector.z()).normalize();
	}

	private static BlockState toBlueMapState(net.minecraft.world.level.block.state.BlockState state) {
		return BLOCK_STATE_CACHE.computeIfAbsent(state, BlueMapCameraRenderer::convertBlockState);
	}

	private static Variant createLiquidVariant(String modelName, String stillTexturePath, String flowTexturePath) {
		ResourcePath<Model> modelPath = new ResourcePath<>("lg2", "block/" + modelName);
		Map<String, TextureVariable> textures = new HashMap<>();
		textures.put("still", new TextureVariable(new ResourcePath<>("minecraft", stillTexturePath)));
		textures.put("flow", new TextureVariable(new ResourcePath<>("minecraft", flowTexturePath)));
		textures.put("particle", new TextureVariable(new ResourcePath<>("minecraft", stillTexturePath)));
		modelPath.setResource(new Model(textures));
		Variant variant = new Variant(modelPath);
		variant.setRenderer(BlockRendererType.LIQUID);
		return variant;
	}

	private static Variant vanillaLiquidVariant(BlockState blockState) {
		if (blockState == null) {
			return null;
		}
		Key id = blockState.getId();
		if (!Key.MINECRAFT_NAMESPACE.equals(id.getNamespace())) {
			return null;
		}
		return switch (id.getValue()) {
			case "water" -> VANILLA_WATER_LIQUID_VARIANT;
			case "lava" -> VANILLA_LAVA_LIQUID_VARIANT;
			default -> null;
		};
	}

	private static boolean isStandaloneLiquidBlock(BlockState blockState) {
		return vanillaLiquidVariant(blockState) != null;
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
			List<EntitySnapshot> entities,
			FrameEnvironment environment
	) {
	}

	private record FrameEnvironment(
			DimensionType dimensionType,
			boolean raining,
			boolean skylight,
			float ambientLight,
			float sunlightStrength,
			float skyLightFactor,
			net.minecraft.world.level.dimension.DimensionType.Skybox skybox,
			int skyColor,
			int cloudColor,
			float cloudHeight,
			float skyLightRed,
			float skyLightGreen,
			float skyLightBlue,
			int sunriseSunsetColor,
			float sunAngle,
			float moonAngle,
			float starAngle,
			float rainBrightness,
			float starBrightness,
			MoonPhase moonPhase,
			long gameTime
	) {
		private static FrameEnvironment capture(ServerLevel level, Vec3 cameraPosition) {
			boolean skylight = level.dimensionType().hasSkyLight();
			float ambient = Mth.clamp(level.dimensionType().ambientLight(), 0.0F, 1.0F);
			float sunlight = skylight ? Mth.clamp(1.0F - level.getSkyDarken() / 15.0F, 0.0F, 1.0F) : 0.0F;
			int skyLightColor = level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, cameraPosition);
			float skyLightFactor = Mth.clamp(level.environmentAttributes().getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, cameraPosition), 0.0F, 1.0F);
			float rainBrightness = 1.0F - level.getRainLevel(0.0F);
			return new FrameEnvironment(
					toBlueMapDimension(level),
					level.isRaining(),
					skylight,
					ambient,
					sunlight,
					skyLightFactor,
					level.dimensionType().skybox(),
					level.environmentAttributes().getValue(EnvironmentAttributes.SKY_COLOR, cameraPosition),
					level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_COLOR, cameraPosition),
					level.environmentAttributes().getValue(EnvironmentAttributes.CLOUD_HEIGHT, cameraPosition),
					toLinear((skyLightColor >> 16) & 0xFF),
					toLinear((skyLightColor >> 8) & 0xFF),
					toLinear(skyLightColor & 0xFF),
					level.environmentAttributes().getValue(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, cameraPosition),
					level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, cameraPosition) * DEG_TO_RAD,
					level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, cameraPosition) * DEG_TO_RAD,
					level.environmentAttributes().getValue(EnvironmentAttributes.STAR_ANGLE, cameraPosition) * DEG_TO_RAD,
					Mth.clamp(rainBrightness, 0.0F, 1.0F),
					Mth.clamp(level.environmentAttributes().getValue(EnvironmentAttributes.STAR_BRIGHTNESS, cameraPosition), 0.0F, 1.0F),
					level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, cameraPosition),
					level.getGameTime()
			);
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

	static final class WorldSnapshot {
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
			long spanX = (long) bounds.maxX() - bounds.minX() + 1L;
			long spanY = (long) bounds.maxY() - bounds.minY() + 1L;
			long spanZ = (long) bounds.maxZ() - bounds.minZ() + 1L;
			long estimatedVolume = Math.max(1L, spanX * spanY * spanZ);
			int estimatedBlockCapacity = (int) Math.min(Integer.MAX_VALUE - 8L, Math.max(256L, estimatedVolume / 3L));
			int estimatedAirContextCapacity = (int) Math.min(Integer.MAX_VALUE - 8L, Math.max(512L, estimatedVolume));
			Long2ObjectOpenHashMap<SnapshotBlock> blocks = new Long2ObjectOpenHashMap<>(estimatedBlockCapacity);
			LongOpenHashSet airContext = new LongOpenHashSet(estimatedAirContextCapacity);
			Map<String, Biome> biomeCache = new HashMap<>(32);
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
						boolean sectionFullyInsideFrustum = frustum.containsAabb(
								chunkMinX,
								sectionMinY,
								chunkMinZ,
								chunkMinX + 16.0D,
								sectionMinY + 16.0D,
								chunkMinZ + 16.0D
						);
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
									if (!sectionFullyInsideFrustum && !frustum.intersectsAabb(worldX, worldY, worldZ, worldX + 1.0D, worldY + 1.0D, worldZ + 1.0D)) {
										continue;
									}

									cursor.set(worldX, worldY, worldZ);
									Biome biome = resolveBiome(level, cursor, biomeCache, resources.dataPack());
									LightData light = new LightData(
											level.getBrightness(LightLayer.SKY, cursor),
											level.getBrightness(LightLayer.BLOCK, cursor)
									);
									BlockState fluidOverlayState = captureFluidOverlayState(state);
									int primaryTintRgb = firstTint(BlockTintProvider.capture(level, cursor, state));
									int waterTintRgb = state.getFluidState().is(FluidTags.WATER)
											? firstTint(BlockTintProvider.capture(level, cursor, Blocks.WATER.defaultBlockState()))
											: NO_TINT_RGB;
									blocks.put(
											BlockPos.asLong(worldX, worldY, worldZ),
											new SnapshotBlock(toBlueMapState(state), fluidOverlayState, light, biome, primaryTintRgb, waterTintRgb)
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
				blocks.put(packedPos, new SnapshotBlock(BlockState.AIR, null, light, biome, NO_TINT_RGB, NO_TINT_RGB));
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

		LightData sampleLight(BlockPos pos) {
			SnapshotBlock block = blockAt(pos.getX(), pos.getY(), pos.getZ());
			return block == null ? new LightData(15, 0) : block.light();
		}

		private Iterable<Long2ObjectMap.Entry<SnapshotBlock>> entries() {
			return this.blocks.long2ObjectEntrySet();
		}
	}

	private record SnapshotBlock(BlockState state, BlockState fluidState, LightData light, Biome biome, int primaryTintRgb, int waterTintRgb) {
	}

	private static BlockState captureFluidOverlayState(net.minecraft.world.level.block.state.BlockState state) {
		if (state == null || state.getBlock() == Blocks.WATER_CAULDRON) {
			return null;
		}
		FluidState fluidState = state.getFluidState();
		if (fluidState == null || fluidState.isEmpty()) {
			return null;
		}
		return toBlueMapState(fluidState.createLegacyBlock());
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

	static final class TextureMaterial {
		private static final float TINTED_GRAYSCALE_CONTRAST_BOOST = 1.40F;
		private static final float TINTED_BIOME_BOOST = 1.45F;
		private final int width;
		private final int height;
		private final int[] pixels;
		private final boolean transparent;
		private final boolean cutout;
		private final boolean grayscale;
		private final float averageLuma;
		private final float detailContrast;

		private TextureMaterial(int width, int height, int[] pixels, boolean transparent, boolean cutout, boolean grayscale, float averageLuma, float detailContrast) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
			this.transparent = transparent;
			this.cutout = cutout;
			this.grayscale = grayscale;
			this.averageLuma = averageLuma;
			this.detailContrast = detailContrast;
		}

		private static TextureMaterial from(Texture texture) {
			try {
				BufferedImage image = texture.getTextureImage();
				if (image == null) {
					return missing();
				}
				return fromImage(selectRenderableFrame(texture, image));
			} catch (IOException exception) {
				return missing();
			}
		}

		static TextureMaterial missing() {
			return new TextureMaterial(1, 1, new int[]{0xFFFF00FF}, false, false, false, 1.0F, 1.0F);
		}

		static TextureMaterial fromImage(BufferedImage image) {
			if (image == null) {
				return missing();
			}
			int width = Math.max(1, image.getWidth());
			int height = Math.max(1, image.getHeight());
			int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
			boolean transparent = false;
			boolean partialAlpha = false;
			boolean grayscale = true;
			float lumaSum = 0.0F;
			float minLuma = 1.0F;
			float maxLuma = 0.0F;
			int opaqueSamples = 0;
			for (int pixel : pixels) {
				int alpha = (pixel >>> 24) & 0xFF;
				if (alpha < 255) {
					transparent = true;
				}
				if (alpha > 8 && alpha < 247) {
					partialAlpha = true;
				}
				if (alpha <= 8) {
					continue;
				}
				float red = ((pixel >> 16) & 0xFF) / 255.0F;
				float green = ((pixel >> 8) & 0xFF) / 255.0F;
				float blue = (pixel & 0xFF) / 255.0F;
				if (grayscale) {
					float maxChannelDelta = Math.max(Math.abs(red - green), Math.max(Math.abs(red - blue), Math.abs(green - blue)));
					if (maxChannelDelta > 0.025F) {
						grayscale = false;
					}
				}
				float luma = (red + green + blue) / 3.0F;
				lumaSum += luma;
				minLuma = Math.min(minLuma, luma);
				maxLuma = Math.max(maxLuma, luma);
				opaqueSamples++;
			}
			float averageLuma = opaqueSamples > 0 ? lumaSum / opaqueSamples : 1.0F;
			float detailContrast = 1.0F;
			if (grayscale && opaqueSamples > 0 && maxLuma - minLuma < 0.42F) {
				detailContrast = TINTED_GRAYSCALE_CONTRAST_BOOST;
			}
			boolean cutout = transparent && !partialAlpha;
			return new TextureMaterial(width, height, pixels, transparent, cutout, grayscale, averageLuma, detailContrast);
		}

		private static BufferedImage selectRenderableFrame(Texture texture, BufferedImage image) {
			int imageWidth = Math.max(1, image.getWidth());
			int imageHeight = Math.max(1, image.getHeight());
			AnimationMeta animation = texture.getAnimation();
			int frameWidth = resolveFrameDimension(animation == null ? 0 : animation.getWidth(), imageWidth, imageHeight);
			int frameHeight = resolveFrameDimension(animation == null ? 0 : animation.getHeight(), imageHeight, imageWidth);
			if (frameWidth <= 0 || frameHeight <= 0) {
				int fallbackFrameSize = Math.min(imageWidth, imageHeight);
				frameWidth = fallbackFrameSize;
				frameHeight = fallbackFrameSize;
			}

			frameWidth = Math.min(frameWidth, imageWidth);
			frameHeight = Math.min(frameHeight, imageHeight);
			boolean looksLikeVerticalFrameStrip = imageHeight > imageWidth && imageHeight % imageWidth == 0;
			boolean hasExplicitAnimationFrames = animation != null && animation.getFrames() != null && !animation.getFrames().isEmpty();
			boolean usesAnimationGrid = frameWidth < imageWidth || frameHeight < imageHeight;
			if (!looksLikeVerticalFrameStrip && !hasExplicitAnimationFrames && !usesAnimationGrid) {
				return image;
			}

			int columns = Math.max(1, imageWidth / frameWidth);
			int rows = Math.max(1, imageHeight / frameHeight);
			if (columns * rows <= 1) {
				return image;
			}

			int frameIndex = 0;
			if (hasExplicitAnimationFrames) {
				frameIndex = Math.max(0, animation.getFrames().get(0).getIndex());
			}
			frameIndex = Mth.clamp(frameIndex, 0, columns * rows - 1);
			int frameX = (frameIndex % columns) * frameWidth;
			int frameY = (frameIndex / columns) * frameHeight;
			return image.getSubimage(frameX, frameY, frameWidth, frameHeight);
		}

		private static int resolveFrameDimension(int explicitSize, int imagePrimary, int imageSecondary) {
			if (explicitSize > 1 && explicitSize <= imagePrimary) {
				return explicitSize;
			}
			if (imagePrimary > imageSecondary && imagePrimary % imageSecondary == 0) {
				return imageSecondary;
			}
			return imagePrimary;
		}

		private boolean shouldEnhanceTintedDetail(float tintR, float tintG, float tintB) {
			if (!this.grayscale || this.detailContrast <= 1.0F) {
				return false;
			}
			float epsilon = 1.0F / 255.0F + 1.0E-4F;
			return Math.abs(tintR - tintG) > epsilon
					|| Math.abs(tintR - tintB) > epsilon
					|| Math.abs(tintG - tintB) > epsilon;
		}

		private int enhanceTintedSample(int argb, float tintR, float tintG, float tintB) {
			if (!this.grayscale || this.detailContrast <= 1.0F) {
				return argb;
			}
			int alpha = (argb >>> 24) & 0xFF;
			if (alpha <= 8) {
				return argb;
			}
			float luma = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / (255.0F * 3.0F);
			float adjusted = Mth.clamp(this.averageLuma + (luma - this.averageLuma) * this.detailContrast * TINTED_BIOME_BOOST, 0.0F, 1.0F);
			if (!this.transparent) {
				float blockTopBias = Mth.clamp((this.averageLuma - 0.42F) * 0.40F, 0.0F, 0.08F);
				adjusted = Mth.clamp(adjusted - blockTopBias, 0.0F, 1.0F);
			}
			int channel = Mth.clamp(Math.round(adjusted * 255.0F), 0, 255);
			return (alpha << 24) | (channel << 16) | (channel << 8) | channel;
		}

		private int sample(float u, float v) {
			float wrappedU = u - (float) Math.floor(u);
			float wrappedV = v - (float) Math.floor(v);
			int x = Mth.clamp(Mth.floor(wrappedU * this.width), 0, this.width - 1);
			int y = Mth.clamp(Mth.floor(wrappedV * this.height), 0, this.height - 1);
			return this.pixels[y * this.width + x];
		}
	}

	private static final class FrameRenderer {
		private final PreparedFrame frame;
		private final RenderResources resources;
		private final int internalSize;
		private final float tanHalfFov;
		private final Int2ObjectOpenHashMap<TextureMaterial> dynamicMaterials;
		private final Map<String, Integer> dynamicMaterialIds;
		private int nextDynamicMaterialId;
		private final float[] red;
		private final float[] green;
		private final float[] blue;
		private final float[] opaqueDepth;

		private FrameRenderer(PreparedFrame frame, RenderResources resources) {
			this.frame = frame;
			this.resources = resources;
			this.internalSize = MAP_SIZE * Mth.clamp(frame.supersampling(), 1, 4);
			this.tanHalfFov = (float) Math.tan(Math.toRadians(frame.fovDegrees() * 0.5D));
			this.dynamicMaterials = new Int2ObjectOpenHashMap<>();
			this.dynamicMaterialIds = new HashMap<>();
			this.nextDynamicMaterialId = -1;
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
					int index = y * this.internalSize + x;
					renderSkyPixel(index, rayDirection(x + 0.5D, y + 0.5D));
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

		private void renderSkyPixel(int index, Vec3 direction) {
			FrameEnvironment environment = this.frame.environment();
			int baseRgb = switch (environment.skybox()) {
				case END -> sampleEndSky(direction);
				case NONE -> caveSkyColor(direction);
				case OVERWORLD -> overworldSkyColor(direction);
			};
			float redLinear = toLinear((baseRgb >> 16) & 0xFF);
			float greenLinear = toLinear((baseRgb >> 8) & 0xFF);
			float blueLinear = toLinear(baseRgb & 0xFF);

			if (environment.skybox() == net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD && environment.skylight()) {
				int sunriseSunsetColor = environment.sunriseSunsetColor();
				float sunriseAlpha = sunriseSunsetAlpha(direction);
				if (sunriseAlpha > 0.0F) {
					redLinear = blendLinear(redLinear, toLinear((sunriseSunsetColor >> 16) & 0xFF), sunriseAlpha);
					greenLinear = blendLinear(greenLinear, toLinear((sunriseSunsetColor >> 8) & 0xFF), sunriseAlpha);
					blueLinear = blendLinear(blueLinear, toLinear(sunriseSunsetColor & 0xFF), sunriseAlpha);
				}

				Vec3 sunDirection = celestialDirection(environment.sunAngle());
				Vec3 moonDirection = celestialDirection(environment.moonAngle());
				float rainBrightness = environment.rainBrightness();
				float haloBaseRed = redLinear;
				float haloBaseGreen = greenLinear;
				float haloBaseBlue = blueLinear;
				float sunGlow = sampleSunGlow(direction, sunDirection) * rainBrightness * 0.34F;
				if (sunGlow > 0.0F) {
					float glowRed = toLinear((SUN_GLOW_RGB >> 16) & 0xFF);
					float glowGreen = toLinear((SUN_GLOW_RGB >> 8) & 0xFF);
					float glowBlue = toLinear(SUN_GLOW_RGB & 0xFF);
					float haloTargetRed = haloTarget(haloBaseRed, glowRed);
					float haloTargetGreen = haloTarget(haloBaseGreen, glowGreen);
					float haloTargetBlue = haloTarget(haloBaseBlue, glowBlue);
					float haloBlend = sunGlow * 1.55F;
					float haloLift = sunGlow * 0.70F;
					redLinear = blendLinear(redLinear, haloTargetRed, haloBlend);
					greenLinear = blendLinear(greenLinear, haloTargetGreen, haloBlend);
					blueLinear = blendLinear(blueLinear, haloTargetBlue, haloBlend);
					redLinear = screenLinear(redLinear, haloTargetRed, haloLift);
					greenLinear = screenLinear(greenLinear, haloTargetGreen, haloLift);
					blueLinear = screenLinear(blueLinear, haloTargetBlue, haloLift);
				}
				int sunArgb = sampleSunTexture(direction, sunDirection);
				float sunAlpha = ((sunArgb >>> 24) & 0xFF) / 255.0F * rainBrightness;
				if (sunAlpha > 0.0F) {
					float sunRed = toLinear((sunArgb >> 16) & 0xFF);
					float sunGreen = toLinear((sunArgb >> 8) & 0xFF);
					float sunBlue = toLinear(sunArgb & 0xFF);
					float sunBrightness = Math.max(sunRed, Math.max(sunGreen, sunBlue));
					float coreFactor = smoothstep(0.12F, 0.52F, sunBrightness);
					float coreAlpha = sunAlpha * coreFactor;
					if (coreAlpha > 0.0F) {
						redLinear = blendLinear(redLinear, sunRed, coreAlpha);
						greenLinear = blendLinear(greenLinear, sunGreen, coreAlpha);
						blueLinear = blendLinear(blueLinear, sunBlue, coreAlpha);
					}
				}

				TextureMaterial moonMaterial = skyMaterial(moonTextureId(environment.moonPhase()));
				int moonArgb = sampleMoonTexture(direction, moonDirection, moonMaterial);
				float moonAlpha = ((moonArgb >>> 24) & 0xFF) / 255.0F * rainBrightness;
				if (moonAlpha > 0.0F) {
					redLinear = blendLinear(redLinear, toLinear((moonArgb >> 16) & 0xFF), moonAlpha);
					greenLinear = blendLinear(greenLinear, toLinear((moonArgb >> 8) & 0xFF), moonAlpha);
					blueLinear = blendLinear(blueLinear, toLinear(moonArgb & 0xFF), moonAlpha);
				}

				float stars = sampleStarBrightness(direction);
				if (stars > 0.0F) {
					float starAlpha = stars * environment.starBrightness() * rainBrightness;
					redLinear = blendLinear(redLinear, 1.0F, starAlpha);
					greenLinear = blendLinear(greenLinear, 1.0F, starAlpha);
					blueLinear = blendLinear(blueLinear, 1.0F, starAlpha);
				}

				CloudSample cloudSample = sampleCloud(direction);
				if (cloudSample != null && cloudSample.alpha() > 0.0F) {
					redLinear = blendLinear(redLinear, cloudSample.red(), cloudSample.alpha());
					greenLinear = blendLinear(greenLinear, cloudSample.green(), cloudSample.alpha());
					blueLinear = blendLinear(blueLinear, cloudSample.blue(), cloudSample.alpha());
				}
			}

			this.red[index] = redLinear;
			this.green[index] = greenLinear;
			this.blue[index] = blueLinear;
		}

		private int caveSkyColor(Vec3 direction) {
			if (this.frame.environment().dimensionType() == DimensionType.NETHER) {
				return 0xA54B2A;
			}
			float ambient = Mth.clamp(this.frame.environment().ambientLight() + 0.18F, 0.15F, 0.45F);
			float horizon = 1.0F - Mth.clamp((float) Math.abs(direction.y), 0.0F, 1.0F);
			return scaleRgb(0x2B313A, ambient * (0.90F + horizon * 0.18F));
		}

		private int overworldSkyColor(Vec3 direction) {
			int skyRgb = this.frame.environment().skyColor();
			float vertical = Mth.clamp((float) ((direction.y + 1.0D) * 0.5D), 0.0F, 1.0F);
			float zenithMix = (float) Math.pow(vertical, 0.65D);
			int horizonRgb = scaleRgb(skyRgb, 0.82F);
			int zenithRgb = lerpRgb(scaleRgb(skyRgb, 0.96F), 0xFFFFFF, 0.08F * this.frame.environment().sunlightStrength());
			int rgb = lerpRgb(horizonRgb, zenithRgb, zenithMix);
			if (direction.y < 0.0D) {
				float belowHorizon = Mth.clamp((float) (-direction.y), 0.0F, 1.0F);
				rgb = lerpRgb(rgb, scaleRgb(skyRgb, 0.22F), belowHorizon * 0.88F);
			}
			return rgb;
		}

		private int sampleEndSky(Vec3 direction) {
			TextureMaterial material = skyMaterial(END_SKY_TEXTURE_ID);
			double ax = Math.abs(direction.x);
			double ay = Math.abs(direction.y);
			double az = Math.abs(direction.z);
			float u;
			float v;
			if (ax >= ay && ax >= az) {
				double inverse = 0.5D / ax;
				u = (float) ((direction.z * inverse) + 0.5D);
				v = (float) ((direction.y * inverse) + 0.5D);
			} else if (ay >= az) {
				double inverse = 0.5D / ay;
				u = (float) ((direction.x * inverse) + 0.5D);
				v = (float) ((direction.z * inverse) + 0.5D);
			} else {
				double inverse = 0.5D / az;
				u = (float) ((direction.x * inverse) + 0.5D);
				v = (float) ((direction.y * inverse) + 0.5D);
			}
			int argb = material.sample(u, v);
			return argb & 0xFFFFFF;
		}

		private float sunriseSunsetAlpha(Vec3 direction) {
			int argb = this.frame.environment().sunriseSunsetColor();
			float attributeAlpha = ((argb >>> 24) & 0xFF) / 255.0F;
			if (attributeAlpha <= 0.0F) {
				return 0.0F;
			}
			Vec3 sunDirection = celestialDirection(this.frame.environment().sunAngle());
			float horizonMask = 1.0F - Mth.clamp(Math.abs((float) direction.y) / SUNRISE_HORIZON_BAND, 0.0F, 1.0F);
			float facingMask = Mth.clamp((float) direction.dot(sunDirection), 0.0F, 1.0F);
			facingMask = facingMask * facingMask;
			return attributeAlpha * horizonMask * facingMask;
		}

		private float sampleStarBrightness(Vec3 direction) {
			if (direction.y <= 0.0D) {
				return 0.0F;
			}
			Vec3 localDirection = inverseCelestialDirection(direction, this.frame.environment().starAngle());
			return STAR_FIELD.sample(localDirection);
		}

		private CloudSample sampleCloud(Vec3 direction) {
			FrameEnvironment environment = this.frame.environment();
			if (environment.skybox() != net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD || !environment.skylight()) {
				return null;
			}
			if (CLOUD_FIELD == null || CLOUD_FIELD.isEmpty()) {
				return null;
			}
			float cloudHeight = environment.cloudHeight();
			if (!Float.isFinite(cloudHeight)) {
				return null;
			}

			double dy = direction.y;
			double bottomY = cloudHeight;
			double topY = cloudHeight + CLOUD_THICKNESS;
			double eyeY = this.frame.eyePosition().y;

			double tEnter;
			double tExit;
			boolean startsInsideLayer;
			if (Math.abs(dy) < 1.0E-6D) {
				if (eyeY < bottomY || eyeY > topY) {
					return null;
				}
				tEnter = 0.0D;
				tExit = Double.POSITIVE_INFINITY;
				startsInsideLayer = true;
			} else {
				double t0 = (bottomY - eyeY) / dy;
				double t1 = (topY - eyeY) / dy;
				tEnter = Math.min(t0, t1);
				tExit = Math.max(t0, t1);
				if (tExit <= 0.0D) {
					return null;
				}
				startsInsideLayer = tEnter < 0.0D;
			}

			double startT = Math.max(tEnter, 0.0D);
			double endT = tExit;
			if (!(endT > startT)) {
				return null;
			}

			double cloudOffsetX = cloudScrollOffset(environment.gameTime(), CLOUD_FIELD.width());
			double originX = this.frame.eyePosition().x + cloudOffsetX;
			double originZ = this.frame.eyePosition().z + CLOUD_Z_OFFSET;

			if (startsInsideLayer) {
				int insideCellX = Mth.floor(originX / CLOUD_CELL_SIZE);
				int insideCellZ = Mth.floor(originZ / CLOUD_CELL_SIZE);
				if (CLOUD_FIELD.isFilled(insideCellX, insideCellZ)) {
					return applyCloudDistanceFade(cloudColorSample(CLOUD_FIELD.argb(insideCellX, insideCellZ), environment.cloudColor(), CloudFace.INSIDE), 0.0D);
				}
			}

			double entryProbeT = Math.min(endT - CLOUD_TRACE_EPSILON, startT + CLOUD_TRACE_EPSILON);
			if (entryProbeT >= startT) {
				double entryX = originX + direction.x * entryProbeT;
				double entryZ = originZ + direction.z * entryProbeT;
				int entryCellX = Mth.floor(entryX / CLOUD_CELL_SIZE);
				int entryCellZ = Mth.floor(entryZ / CLOUD_CELL_SIZE);
				if (CLOUD_FIELD.isFilled(entryCellX, entryCellZ)) {
					CloudFace verticalFace = direction.y > 0.0D ? CloudFace.BOTTOM : CloudFace.TOP;
					return applyCloudDistanceFade(cloudColorSample(CLOUD_FIELD.argb(entryCellX, entryCellZ), environment.cloudColor(), verticalFace), entryProbeT);
				}
			}

			return traceCloudSides(originX, originZ, direction, startT, endT, environment.cloudColor());
		}

		private CloudSample traceCloudSides(double originX, double originZ, Vec3 direction, double startT, double endT, int cloudColorRgb) {
			double dx = direction.x;
			double dz = direction.z;
			double segmentStart = startT + CLOUD_TRACE_EPSILON;
			if (!(segmentStart < endT)) {
				return null;
			}
			double currentX = originX + dx * segmentStart;
			double currentZ = originZ + dz * segmentStart;
			int cellX = Mth.floor(currentX / CLOUD_CELL_SIZE);
			int cellZ = Mth.floor(currentZ / CLOUD_CELL_SIZE);

			int stepX = dx > 1.0E-6D ? 1 : dx < -1.0E-6D ? -1 : 0;
			int stepZ = dz > 1.0E-6D ? 1 : dz < -1.0E-6D ? -1 : 0;

			double nextBoundaryX = stepX > 0
					? (cellX + 1) * CLOUD_CELL_SIZE
					: stepX < 0 ? cellX * CLOUD_CELL_SIZE : Double.POSITIVE_INFINITY;
			double nextBoundaryZ = stepZ > 0
					? (cellZ + 1) * CLOUD_CELL_SIZE
					: stepZ < 0 ? cellZ * CLOUD_CELL_SIZE : Double.POSITIVE_INFINITY;
			double nextTX = stepX == 0 ? Double.POSITIVE_INFINITY : segmentStart + (nextBoundaryX - currentX) / dx;
			double nextTZ = stepZ == 0 ? Double.POSITIVE_INFINITY : segmentStart + (nextBoundaryZ - currentZ) / dz;
			double deltaTX = stepX == 0 ? Double.POSITIVE_INFINITY : CLOUD_CELL_SIZE / Math.abs(dx);
			double deltaTZ = stepZ == 0 ? Double.POSITIVE_INFINITY : CLOUD_CELL_SIZE / Math.abs(dz);

			int maxSteps = 16;
			for (int step = 0; step < maxSteps; step++) {
				if (nextTX >= endT && nextTZ >= endT) {
					return null;
				}
				CloudFace face;
				if (nextTX <= nextTZ) {
					cellX += stepX;
					face = stepX > 0 ? CloudFace.WEST : CloudFace.EAST;
					nextTX += deltaTX;
				} else {
					cellZ += stepZ;
					face = stepZ > 0 ? CloudFace.NORTH : CloudFace.SOUTH;
					nextTZ += deltaTZ;
				}
				if (CLOUD_FIELD.isFilled(cellX, cellZ)) {
					double hitDistance = Math.min(nextTX, nextTZ);
					return applyCloudDistanceFade(cloudColorSample(CLOUD_FIELD.argb(cellX, cellZ), cloudColorRgb, face), hitDistance);
				}
			}
			return null;
		}

		private CloudSample cloudColorSample(int textureArgb, int cloudColorRgb, CloudFace face) {
			float alpha = ((textureArgb >>> 24) & 0xFF) / 255.0F;
			if (alpha <= 0.0F) {
				return null;
			}
			float shade = switch (face) {
				case TOP -> 1.0F;
				case BOTTOM -> 0.72F;
				case NORTH, SOUTH -> 0.90F;
				case EAST, WEST -> 0.82F;
				case INSIDE -> 0.88F;
			};
			float textureRed = toLinear((textureArgb >> 16) & 0xFF);
			float textureGreen = toLinear((textureArgb >> 8) & 0xFF);
			float textureBlue = toLinear(textureArgb & 0xFF);
			float cloudRed = toLinear((cloudColorRgb >> 16) & 0xFF);
			float cloudGreen = toLinear((cloudColorRgb >> 8) & 0xFF);
			float cloudBlue = toLinear(cloudColorRgb & 0xFF);
			float red = Mth.clamp(cloudRed * textureRed * shade, 0.0F, 1.0F);
			float green = Mth.clamp(cloudGreen * textureGreen * shade, 0.0F, 1.0F);
			float blue = Mth.clamp(cloudBlue * textureBlue * shade, 0.0F, 1.0F);
			float cloudAlpha = Mth.clamp(alpha * (face == CloudFace.INSIDE ? 0.92F : 1.0F), 0.0F, 1.0F);
			return new CloudSample(red, green, blue, cloudAlpha);
		}

		private CloudSample applyCloudDistanceFade(CloudSample sample, double distance) {
			if (sample == null) {
				return null;
			}
			float fade = cloudDistanceFade(distance);
			if (fade <= 0.0F) {
				return null;
			}
			return new CloudSample(sample.red(), sample.green(), sample.blue(), sample.alpha() * fade);
		}

		private static float cloudDistanceFade(double distance) {
			if (!(distance > 0.0D)) {
				return 1.0F;
			}
			float clampedDistance = (float) Math.max(0.0D, distance);
			if (clampedDistance <= CLOUD_FADE_START_DISTANCE) {
				return 1.0F;
			}
			if (clampedDistance >= CLOUD_FADE_END_DISTANCE) {
				return 0.0F;
			}
			float progress = (clampedDistance - CLOUD_FADE_START_DISTANCE) / (CLOUD_FADE_END_DISTANCE - CLOUD_FADE_START_DISTANCE);
			return 1.0F - smoothstep(0.0F, 1.0F, progress);
		}

		private static double cloudScrollOffset(long gameTime, int cloudWidth) {
			if (cloudWidth <= 0) {
				return 0.0D;
			}
			long wrappedTicks = Math.floorMod(gameTime, (long) cloudWidth * 400L);
			return wrappedTicks * CLOUD_SCROLL_PER_TICK;
		}

		private static float blendLinear(float base, float overlay, float alpha) {
			float clampedAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
			return overlay * clampedAlpha + base * (1.0F - clampedAlpha);
		}

		private static float addLinear(float base, float overlay, float alpha) {
			float clampedAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
			return Mth.clamp(base + overlay * clampedAlpha, 0.0F, 1.0F);
		}

		private static float screenLinear(float base, float overlay, float alpha) {
			float clampedAlpha = Mth.clamp(alpha, 0.0F, 1.0F);
			return Mth.clamp(base + (1.0F - base) * overlay * clampedAlpha, 0.0F, 1.0F);
		}

		private static float haloTarget(float skyBase, float glowColor) {
			float tinted = Mth.lerp(0.58F, skyBase, glowColor);
			float lifted = screenLinear(skyBase, glowColor, 0.38F);
			return Mth.clamp(Mth.lerp(0.55F, tinted, lifted), 0.0F, 1.0F);
		}

		private static float sampleSunGlow(Vec3 direction, Vec3 centerDirection) {
			float planeRadius = celestialPlaneRadius(direction, centerDirection);
			if (!Float.isFinite(planeRadius)) {
				return 0.0F;
			}
			float innerFade = smoothstep(0.08F, 0.30F, planeRadius);
			float outerFade = 1.0F - smoothstep(0.62F, 1.72F, planeRadius);
			if (innerFade <= 0.0F || outerFade <= 0.0F) {
				return 0.0F;
			}
			float gaussian = (float) Math.exp(-Math.pow(planeRadius / 0.98F, 1.85D));
			return innerFade * outerFade * gaussian;
		}

		private static int sampleSunTexture(Vec3 direction, Vec3 centerDirection) {
			return sampleCelestialTexture(direction, centerDirection, skyMaterial(SUN_TEXTURE_ID), HALF_CELESTIAL_QUAD_SIZE, SUN_MASK_INNER_RADIUS, SUN_MASK_OUTER_RADIUS);
		}

		private static int sampleMoonTexture(Vec3 direction, Vec3 centerDirection, TextureMaterial material) {
			return sampleCelestialTexture(direction, centerDirection, material, HALF_CELESTIAL_QUAD_SIZE, MOON_MASK_INNER_RADIUS, MOON_MASK_OUTER_RADIUS);
		}

		private static int sampleCelestialTexture(
				Vec3 direction,
				Vec3 centerDirection,
				TextureMaterial material,
				float halfSize,
				float radialMaskInner,
				float radialMaskOuter
		) {
			if (material == null) {
				return 0;
			}
			double alignment = direction.dot(centerDirection);
			if (alignment <= 0.0D) {
				return 0;
			}
			Vec3 upHint = Math.abs(centerDirection.y) > 0.98D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 1.0D, 0.0D);
			Vec3 basisRight = upHint.cross(centerDirection).normalize();
			Vec3 basisUp = centerDirection.cross(basisRight).normalize();
			float planeX = (float) (direction.dot(basisRight) / alignment);
			float planeY = (float) (direction.dot(basisUp) / alignment);
			if (Math.abs(planeX) > halfSize || Math.abs(planeY) > halfSize) {
				return 0;
			}
			float u = planeX / (halfSize * 2.0F) + 0.5F;
			float v = 0.5F - planeY / (halfSize * 2.0F);
			int argb = material.sample(u, v);
			int alpha = (argb >>> 24) & 0xFF;
			if (alpha <= 0) {
				return 0;
			}

			float radialX = u * 2.0F - 1.0F;
			float radialY = v * 2.0F - 1.0F;
			float radialDistance = Mth.sqrt(radialX * radialX + radialY * radialY);
			float discMask = 1.0F - smoothstep(radialMaskInner, radialMaskOuter, radialDistance);
			if (discMask <= 0.0F) {
				return 0;
			}

			float maxChannel = Math.max(((argb >> 16) & 0xFF) / 255.0F, Math.max(((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F));
			float keyMask = smoothstep(0.015F, 0.09F, maxChannel);
			float resolvedAlpha = alpha / 255.0F * discMask * keyMask;
			if (resolvedAlpha <= 0.0F) {
				return 0;
			}
			return (Mth.clamp(Math.round(resolvedAlpha * 255.0F), 0, 255) << 24) | (argb & 0xFFFFFF);
		}

		private static float celestialPlaneRadius(Vec3 direction, Vec3 centerDirection) {
			double alignment = direction.dot(centerDirection);
			if (alignment <= 0.0D) {
				return Float.POSITIVE_INFINITY;
			}
			Vec3 upHint = Math.abs(centerDirection.y) > 0.98D ? new Vec3(0.0D, 0.0D, 1.0D) : new Vec3(0.0D, 1.0D, 0.0D);
			Vec3 basisRight = upHint.cross(centerDirection).normalize();
			Vec3 basisUp = centerDirection.cross(basisRight).normalize();
			float planeX = (float) (direction.dot(basisRight) / alignment);
			float planeY = (float) (direction.dot(basisUp) / alignment);
			return Mth.sqrt(planeX * planeX + planeY * planeY) / HALF_CELESTIAL_QUAD_SIZE;
		}

		private static float smoothstep(float edge0, float edge1, float value) {
			if (edge0 == edge1) {
				return value < edge0 ? 0.0F : 1.0F;
			}
			float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
			return t * t * (3.0F - 2.0F * t);
		}

		private ArrayTileModel buildGeometry() {
			WorldSnapshot snapshot = this.frame.snapshot();
			RenderSettings renderSettings = new StaticRenderSettings(snapshot);
			ArrayTileModel model = new ArrayTileModel(8192);
			TileModelView tileModelView = new TileModelView(model);
			BlockStateModelRenderer blockRenderer = new BlockStateModelRenderer(this.resources.resourcePack(), this.resources.textureGallery(), renderSettings);
			LiquidModelRenderer liquidRenderer = new LiquidModelRenderer(this.resources.resourcePack(), this.resources.textureGallery(), renderSettings);
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
				if (!isStandaloneLiquidBlock(snapshotBlock.state())) {
					blockRenderer.render(neighborhood, tileModelView, scratchColor);
				}
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
				renderFluidOverlay(snapshotBlock, neighborhood, tileModelView, liquidRenderer, scratchColor, x, y, z);
			}

			CameraEntityRenderer.renderEntities(this.frame.entities(), snapshot, model, new MaterialResolver() {
				@Override
				public int materialForTexture(Identifier textureId) {
					return FrameRenderer.this.materialForTexture(textureId);
				}

				@Override
				public int materialForPlayerSkin(PlayerSkinSnapshot skinSnapshot) {
					return FrameRenderer.this.materialForPlayerSkin(skinSnapshot);
				}

				@Override
				public int materialForImage(String cacheKey, BufferedImage image) {
					return FrameRenderer.this.registerDynamicMaterial(cacheKey, image == null ? TextureMaterial.missing() : TextureMaterial.fromImage(image));
				}
			});

			return model;
		}

		private void renderFluidOverlay(
				SnapshotBlock snapshotBlock,
				BlockNeighborhood neighborhood,
				TileModelView tileModelView,
				LiquidModelRenderer liquidRenderer,
				Color scratchColor,
				int x,
				int y,
				int z
		) {
			BlockState fluidState = snapshotBlock.fluidState();
			if (fluidState == null) {
				return;
			}

			int triangleStart = tileModelView.getStart();
			Variant syntheticVariant = vanillaLiquidVariant(fluidState);
			if (syntheticVariant != null) {
				liquidRenderer.render(neighborhood, syntheticVariant, tileModelView, scratchColor);
			} else {
				de.bluecolored.bluemap.core.resources.pack.resourcepack.blockstate.BlockState resourceBlockState = this.resources.resourcePack().getBlockState(fluidState);
				if (resourceBlockState == null) {
					return;
				}
				resourceBlockState.forEach(fluidState, x, y, z, variant -> liquidRenderer.render(neighborhood, variant, tileModelView, scratchColor));
			}
			if (tileModelView.getSize() > 0) {
				tileModelView.translate(x, y, z);
			} else {
				tileModelView.initialize(triangleStart);
			}
		}

		private int materialForTexture(Identifier textureId) {
			return registerDynamicMaterial(textureId == null ? "missing" : textureId.toString(), CameraEntityRenderer.loadTextureMaterial(textureId));
		}

		private int materialForPlayerSkin(PlayerSkinSnapshot skinSnapshot) {
			String key = skinSnapshot == null ? "player:missing" : "player:" + skinSnapshot.cacheKey();
			return registerDynamicMaterial(key, CameraEntityRenderer.loadPlayerSkinMaterial(skinSnapshot));
		}

		private int registerDynamicMaterial(String key, TextureMaterial material) {
			Integer cachedId = this.dynamicMaterialIds.get(key);
			if (cachedId != null) {
				return cachedId;
			}
			int materialId = this.nextDynamicMaterialId--;
			this.dynamicMaterialIds.put(key, materialId);
			this.dynamicMaterials.put(materialId, material == null ? TextureMaterial.missing() : material);
			return materialId;
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
				TextureMaterial material = this.dynamicMaterials.get(materials[triangleIndex]);
				if (material == null) {
					material = this.resources.materials().getOrDefault(materials[triangleIndex], this.resources.materials().get(0));
				}
				List<RasterTriangle> clippedTriangles = buildTriangles(triangleIndex, positions, uvs, aos, colors, sunlight, blocklight, material);
				if (clippedTriangles.isEmpty()) {
					continue;
				}
				if (material.transparent && !material.cutout) {
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
			float triangleSunlight = Byte.toUnsignedInt(sunlight[triangleIndex]);
			float triangleBlocklight = Byte.toUnsignedInt(blocklight[triangleIndex]);
			FaceNormal faceNormal = faceNormal(
					positions[positionBase],
					positions[positionBase + 1],
					positions[positionBase + 2],
					positions[positionBase + 3],
					positions[positionBase + 4],
					positions[positionBase + 5],
					positions[positionBase + 6],
					positions[positionBase + 7],
					positions[positionBase + 8]
			);
			for (int vertexIndex = 0; vertexIndex < 3; vertexIndex++) {
				float worldX = positions[positionBase + vertexIndex * 3];
				float worldY = positions[positionBase + vertexIndex * 3 + 1];
				float worldZ = positions[positionBase + vertexIndex * 3 + 2];
				Vec3 relative = new Vec3(worldX, worldY, worldZ).subtract(this.frame.eyePosition());
				float cameraX = (float) relative.dot(this.frame.right());
				float cameraY = (float) relative.dot(this.frame.up());
				float cameraZ = (float) relative.dot(this.frame.forward());
				depthSum += cameraZ;
				VertexLight vertexLight = sampleVertexLight(
						worldX,
						worldY,
						worldZ,
						faceNormal.x(),
						faceNormal.y(),
						faceNormal.z(),
						triangleSunlight / 15.0F,
						triangleBlocklight / 15.0F
				);
				vertices[vertexIndex] = new Vertex(
						cameraX,
						cameraY,
						cameraZ,
						uvs[uvBase + vertexIndex * 2],
						uvs[uvBase + vertexIndex * 2 + 1],
						aos[aoBase + vertexIndex],
						vertexLight.skyLight(),
						vertexLight.blockLight()
				);
			}

			List<Vertex> polygon = clipAgainstNearPlane(vertices);
			if (polygon.size() < 3) {
				return List.of();
			}

			float faceShade = faceShadeFor(
					positions[positionBase],
					positions[positionBase + 1],
					positions[positionBase + 2],
					positions[positionBase + 3],
					positions[positionBase + 4],
					positions[positionBase + 5],
					positions[positionBase + 6],
					positions[positionBase + 7],
					positions[positionBase + 8]
			);
			float colorR = toLinear(colors[colorBase]);
			float colorG = toLinear(colors[colorBase + 1]);
			float colorB = toLinear(colors[colorBase + 2]);
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
						faceShade,
						material,
						depthSum / 3.0F
					));
			}
			return result;
		}

		private VertexLight sampleVertexLight(
				float worldX,
				float worldY,
				float worldZ,
				float normalX,
				float normalY,
				float normalZ,
				float fallbackSkyLight,
				float fallbackBlockLight
		) {
			float sampleX = worldX + normalX * 0.18F;
			float sampleY = worldY + normalY * 0.18F;
			float sampleZ = worldZ + normalZ * 0.18F;
			int baseX = Mth.floor(sampleX);
			int baseY = Mth.floor(sampleY);
			int baseZ = Mth.floor(sampleZ);
			float fracX = sampleX - baseX;
			float fracY = sampleY - baseY;
			float fracZ = sampleZ - baseZ;
			float weightedSky = 0.0F;
			float weightedBlock = 0.0F;
			float totalWeight = 0.0F;
			for (int offsetX = 0; offsetX <= 1; offsetX++) {
				float weightX = offsetX == 0 ? 1.0F - fracX : fracX;
				for (int offsetY = 0; offsetY <= 1; offsetY++) {
					float weightY = offsetY == 0 ? 1.0F - fracY : fracY;
					for (int offsetZ = 0; offsetZ <= 1; offsetZ++) {
						float weightZ = offsetZ == 0 ? 1.0F - fracZ : fracZ;
						float weight = weightX * weightY * weightZ;
						if (weight <= 1.0E-4F) {
							continue;
						}
						SnapshotBlock block = this.frame.snapshot().blockAt(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
						if (block == null) {
							continue;
						}
						float openness = block.state().isAir() ? 1.0F : 0.72F;
						float adjustedWeight = weight * openness;
						weightedSky += (block.light().getSkyLight() / 15.0F) * adjustedWeight;
						weightedBlock += (block.light().getBlockLight() / 15.0F) * adjustedWeight;
						totalWeight += adjustedWeight;
					}
				}
			}
			if (totalWeight <= 1.0E-4F) {
				return new VertexLight(fallbackSkyLight, fallbackBlockLight);
			}
			float sampledSky = weightedSky / totalWeight;
			float sampledBlock = weightedBlock / totalWeight;
			return new VertexLight(
					Mth.lerp(0.22F, fallbackSkyLight, sampledSky),
					Mth.lerp(0.18F, fallbackBlockLight, sampledBlock)
			);
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
					} else if (depth > this.opaqueDepth[index] + 1.0E-4F) {
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
					float sunlightLevel = (w0 * triangle.a().skyLight() * triangle.a().inverseZ()
							+ w1 * triangle.b().skyLight() * triangle.b().inverseZ()
							+ w2 * triangle.c().skyLight() * triangle.c().inverseZ()) / inverseZ;
					float blocklightLevel = (w0 * triangle.a().blockLight() * triangle.a().inverseZ()
							+ w1 * triangle.b().blockLight() * triangle.b().inverseZ()
							+ w2 * triangle.c().blockLight() * triangle.c().inverseZ()) / inverseZ;

					TextureMaterial material = triangle.material();
					int argb = material.sample(u, v);
					if (material.shouldEnhanceTintedDetail(triangle.colorR(), triangle.colorG(), triangle.colorB())) {
						argb = material.enhanceTintedSample(argb, triangle.colorR(), triangle.colorG(), triangle.colorB());
					}
					float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
					if (alpha <= 0.01F) {
						continue;
					}
					if (material.cutout) {
						if (alpha < 0.5F) {
							continue;
						}
						alpha = 1.0F;
					}

					float redLinear;
					float greenLinear;
					float blueLinear;
					if (material.transparent && !material.cutout) {
						float skylightMix = sunlightLevel * Mth.lerp(this.frame.environment().sunlightStrength(), 0.18F, 1.0F);
						float blocklightMix = blocklightLevel <= 0.0F ? 0.0F : Mth.lerp(blocklightLevel, 0.15F, 1.0F);
						float ambientFloor = Mth.clamp(this.frame.environment().ambientLight() * 0.45F + 0.22F, 0.22F, 0.42F);
						float lightMix = Math.max(Math.max(skylightMix, blocklightMix), ambientFloor);
						float aoShade = Mth.lerp(Mth.clamp(ao, 0.0F, 1.0F), 0.58F, 1.0F);
						float shade = triangle.faceShade() * quantizeLight(lightMix) * aoShade;
						if (depth > 64.0F && this.frame.maxDistance() > 64.0D) {
							float fade = Mth.clamp((depth - 64.0F) / (float) (this.frame.maxDistance() - 64.0D), 0.0F, 1.0F);
							shade *= Mth.lerp(fade, 1.0F, 0.78F);
						}
						shade = Mth.clamp(shade, 0.0F, 1.0F);
						redLinear = toLinear((argb >> 16) & 0xFF) * triangle.colorR() * shade;
						greenLinear = toLinear((argb >> 8) & 0xFF) * triangle.colorG() * shade;
						blueLinear = toLinear(argb & 0xFF) * triangle.colorB() * shade;
					} else {
						float blockBrightness = vanillaLightBrightness(blocklightLevel) * 1.5F;
						float skyBrightness = vanillaLightBrightness(sunlightLevel) * this.frame.environment().skyLightFactor();
						float lightRed = Mth.lerp(this.frame.environment().ambientLight(), blockBrightness, 1.0F);
						float lightGreen = Mth.lerp(this.frame.environment().ambientLight(), blockBrightness * ((blockBrightness * 0.6F + 0.4F) * 0.6F + 0.4F), 1.0F);
						float lightBlue = Mth.lerp(this.frame.environment().ambientLight(), blockBrightness * (blockBrightness * blockBrightness * 0.6F + 0.4F), 1.0F);
						lightRed += this.frame.environment().skyLightRed() * skyBrightness;
						lightGreen += this.frame.environment().skyLightGreen() * skyBrightness;
						lightBlue += this.frame.environment().skyLightBlue() * skyBrightness;
						lightRed = Mth.lerp(0.04F, lightRed, 0.75F);
						lightGreen = Mth.lerp(0.04F, lightGreen, 0.75F);
						lightBlue = Mth.lerp(0.04F, lightBlue, 0.75F);
						float maxComponent = Math.max(lightRed, Math.max(lightGreen, lightBlue));
						if (maxComponent > 1.0E-4F) {
							float maxInverted = 1.0F - maxComponent;
							float maxScaled = 1.0F - maxInverted * maxInverted * maxInverted * maxInverted;
							float scale = maxScaled / maxComponent;
							lightRed = Mth.lerp(0.12F, lightRed, lightRed * scale);
							lightGreen = Mth.lerp(0.12F, lightGreen, lightGreen * scale);
							lightBlue = Mth.lerp(0.12F, lightBlue, lightBlue * scale);
						}
						lightRed = Mth.clamp(Mth.lerp(0.04F, lightRed, 0.75F), 0.0F, 1.0F);
						lightGreen = Mth.clamp(Mth.lerp(0.04F, lightGreen, 0.75F), 0.0F, 1.0F);
						lightBlue = Mth.clamp(Mth.lerp(0.04F, lightBlue, 0.75F), 0.0F, 1.0F);
						float aoShade = Mth.lerp(Mth.clamp(ao, 0.0F, 1.0F), 0.74F, 1.0F);
						float shade = triangle.faceShade() * aoShade;
						if (depth > 64.0F && this.frame.maxDistance() > 64.0D) {
							float fade = Mth.clamp((depth - 64.0F) / (float) (this.frame.maxDistance() - 64.0D), 0.0F, 1.0F);
							shade *= Mth.lerp(fade, 1.0F, 0.78F);
						}
						shade = Mth.clamp(shade, 0.0F, 1.0F);
						redLinear = toLinear((argb >> 16) & 0xFF) * triangle.colorR() * shade * lightRed;
						greenLinear = toLinear((argb >> 8) & 0xFF) * triangle.colorG() * shade * lightGreen;
						blueLinear = toLinear(argb & 0xFF) * triangle.colorB() * shade * lightBlue;
					}

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
				for (int mapY = 0; mapY < MAP_SIZE; mapY++) {
					for (int mapX = 0; mapX < MAP_SIZE; mapX++) {
						int index = mapY * MAP_SIZE + mapX;
						int rgb = (toSrgb(this.red[index]) << 16) | (toSrgb(this.green[index]) << 8) | toSrgb(this.blue[index]);
						output[index] = MapPaletteQuantizer.quantizeDithered(rgb, mapX, mapY);
					}
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
						int averagedRgb = MapPaletteQuantizer.averageRgb(samples, sampleIndex);
						int centerX = startX + scale / 2;
						int centerY = startY + scale / 2;
						int centerIndex = centerY * this.internalSize + centerX;
						int centerRgb = (toSrgb(this.red[centerIndex]) << 16)
								| (toSrgb(this.green[centerIndex]) << 8)
								| toSrgb(this.blue[centerIndex]);
						int detailRgb = lerpRgb(averagedRgb, centerRgb, 0.50F);
						output[mapY * MAP_SIZE + mapX] = MapPaletteQuantizer.quantizeDithered(detailRgb, mapX, mapY);
					}
				}
				return output;
		}

		private static float edge(float ax, float ay, float bx, float by, float px, float py) {
			return (px - ax) * (by - ay) - (py - ay) * (bx - ax);
		}

		private static float faceShadeFor(
				float ax, float ay, float az,
				float bx, float by, float bz,
				float cx, float cy, float cz
		) {
			float abx = bx - ax;
			float aby = by - ay;
			float abz = bz - az;
			float acx = cx - ax;
			float acy = cy - ay;
			float acz = cz - az;
			float nx = aby * acz - abz * acy;
			float ny = abz * acx - abx * acz;
			float nz = abx * acy - aby * acx;
			float lengthSquared = nx * nx + ny * ny + nz * nz;
			if (lengthSquared <= 1.0E-6F) {
				return 0.8F;
			}
			float invLength = 1.0F / Mth.sqrt(lengthSquared);
			float normalizedX = nx * invLength;
			float normalizedY = ny * invLength;
			float normalizedZ = nz * invLength;
			float absX = Math.abs(normalizedX);
			float absY = Math.abs(normalizedY);
			float absZ = Math.abs(normalizedZ);
			if (absY >= absX && absY >= absZ) {
				if (normalizedY > 0.0F) {
					return 1.0F;
				}
				return 0.55F;
			}
			if (absZ >= absX) {
				return 0.8F;
			}
			return 0.6F;
		}

		private FaceNormal faceNormal(
				float ax, float ay, float az,
				float bx, float by, float bz,
				float cx, float cy, float cz
		) {
			float abx = bx - ax;
			float aby = by - ay;
			float abz = bz - az;
			float acx = cx - ax;
			float acy = cy - ay;
			float acz = cz - az;
			float nx = aby * acz - abz * acy;
			float ny = abz * acx - abx * acz;
			float nz = abx * acy - aby * acx;
			float lengthSquared = nx * nx + ny * ny + nz * nz;
			if (lengthSquared <= 1.0E-6F) {
				return new FaceNormal(0.0F, 1.0F, 0.0F);
			}
			float inverseLength = 1.0F / Mth.sqrt(lengthSquared);
			return new FaceNormal(nx * inverseLength, ny * inverseLength, nz * inverseLength);
		}

		private static float vanillaLightBrightness(float lightLevel) {
			float clamped = Mth.clamp(lightLevel, 0.0F, 1.0F);
			return clamped / (4.0F - 3.0F * clamped);
		}

		private static float quantizeLight(float light) {
			float clamped = Mth.clamp(light, 0.0F, 1.0F);
			if (clamped < 0.625F) {
				return 135.0F / 255.0F;
			}
			if (clamped < 0.785F) {
				return 180.0F / 255.0F;
			}
			if (clamped < 0.93F) {
				return 220.0F / 255.0F;
			}
			return 1.0F;
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
			float skyLight,
			float blockLight,
			float screenX,
			float screenY,
			float inverseZ
	) {
		private Vertex(float cameraX, float cameraY, float cameraZ, float u, float v, float ao, float skyLight, float blockLight) {
			this(cameraX, cameraY, cameraZ, u, v, ao, skyLight, blockLight, 0.0F, 0.0F, 0.0F);
		}

		private Vertex lerp(Vertex other, float delta) {
			return new Vertex(
					Mth.lerp(delta, this.cameraX, other.cameraX),
					Mth.lerp(delta, this.cameraY, other.cameraY),
					Mth.lerp(delta, this.cameraZ, other.cameraZ),
					Mth.lerp(delta, this.u, other.u),
					Mth.lerp(delta, this.v, other.v),
					Mth.lerp(delta, this.ao, other.ao),
					Mth.lerp(delta, this.skyLight, other.skyLight),
					Mth.lerp(delta, this.blockLight, other.blockLight)
			);
		}

		private Vertex withProjection(float screenX, float screenY, float inverseZ) {
			return new Vertex(this.cameraX, this.cameraY, this.cameraZ, this.u, this.v, this.ao, this.skyLight, this.blockLight, screenX, screenY, inverseZ);
		}
	}

	private record RasterTriangle(
			Vertex a,
			Vertex b,
			Vertex c,
			float colorR,
			float colorG,
			float colorB,
			float faceShade,
			TextureMaterial material,
			float sortDepth
	) {
	}

	private record VertexLight(float skyLight, float blockLight) {
	}

	private record FaceNormal(float x, float y, float z) {
	}

	private record CloudSample(float red, float green, float blue, float alpha) {
	}

	private enum CloudFace {
		TOP,
		BOTTOM,
		NORTH,
		SOUTH,
		EAST,
		WEST,
		INSIDE
	}

	private static final class CloudField {
		private final int width;
		private final int height;
		private final int[] pixels;

		private CloudField(int width, int height, int[] pixels) {
			this.width = width;
			this.height = height;
			this.pixels = pixels;
		}

		private static CloudField create() {
			BufferedImage image = TextureAssetManager.get().loadTexture(CLOUD_TEXTURE_ID);
			if (image == null) {
				return new CloudField(0, 0, new int[0]);
			}
			int width = Math.max(1, image.getWidth());
			int height = Math.max(1, image.getHeight());
			return new CloudField(width, height, image.getRGB(0, 0, width, height, null, 0, width));
		}

		private boolean isEmpty() {
			return this.pixels.length == 0;
		}

		private int width() {
			return this.width;
		}

		private boolean isFilled(int cellX, int cellZ) {
			if (this.isEmpty()) {
				return false;
			}
			int alpha = (this.argb(cellX, cellZ) >>> 24) & 0xFF;
			return alpha >= 10;
		}

		private int argb(int cellX, int cellZ) {
			if (this.isEmpty()) {
				return 0;
			}
			int wrappedX = wrap(cellX, this.width);
			int wrappedZ = wrap(cellZ, this.height);
			return this.pixels[wrappedZ * this.width + wrappedX];
		}

		private static int wrap(int value, int modulo) {
			int wrapped = value % modulo;
			return wrapped < 0 ? wrapped + modulo : wrapped;
		}
	}

	private static final class StarField {
		private static final int CELL_U = 192;
		private static final int CELL_V = 96;
		private final long[] seeds;

		private StarField(long[] seeds) {
			this.seeds = seeds;
		}

		private static StarField create() {
			long[] seeds = new long[CELL_U * CELL_V];
			RandomSource random = RandomSource.create(10842L);
			for (int i = 0; i < seeds.length; i++) {
				seeds[i] = random.nextLong();
			}
			return new StarField(seeds);
		}

		private float sample(Vec3 direction) {
			float u = (float) (Math.atan2(direction.z, direction.x) / (Math.PI * 2.0D) + 0.5D);
			float v = (float) (Math.asin(Mth.clamp(direction.y, -1.0D, 1.0D)) / Math.PI + 0.5D);
			float cellU = u * CELL_U;
			float cellV = v * CELL_V;
			int baseU = Mth.floor(cellU);
			int baseV = Mth.floor(cellV);
			float brightness = 0.0F;
			for (int offsetV = -1; offsetV <= 1; offsetV++) {
				int wrappedV = wrap(baseV + offsetV, CELL_V);
				for (int offsetU = -1; offsetU <= 1; offsetU++) {
					int wrappedU = wrap(baseU + offsetU, CELL_U);
					long seed = this.seeds[wrappedV * CELL_U + wrappedU];
					float chance = unitFloat(seed);
					if (chance < 0.949F) {
						continue;
					}
					float centerU = wrappedU + unitFloat(seed >> 16);
					float centerV = wrappedV + unitFloat(seed >> 32);
					float du = periodicDistance(cellU, centerU, CELL_U);
					float dv = periodicDistance(cellV, centerV, CELL_V);
					float size = STAR_DISC_RADIUS * Mth.lerp(unitFloat(seed >> 48), 0.85F, 1.25F);
					float distance = Mth.sqrt(du * du + dv * dv) / Math.max(size, 1.0E-4F);
					if (distance >= 1.0F) {
						continue;
					}
					float local = 1.0F - distance;
					local = local * local * (1.15F - 0.15F * distance);
					float starStrength = Mth.lerp(unitFloat(seed ^ 0x5DEECE66DL), 0.35F, 0.95F);
					brightness = Math.max(brightness, local * starStrength);
				}
			}
			return brightness;
		}

		private static int wrap(int value, int modulo) {
			int wrapped = value % modulo;
			return wrapped < 0 ? wrapped + modulo : wrapped;
		}

		private static float periodicDistance(float sample, float center, int modulo) {
			float distance = Math.abs(sample - center);
			return Math.min(distance, modulo - distance) / modulo;
		}

		private static float unitFloat(long seed) {
			long mixed = seed;
			mixed ^= mixed >>> 33;
			mixed *= 0xff51afd7ed558ccdL;
			mixed ^= mixed >>> 33;
			mixed *= 0xc4ceb9fe1a85ec53L;
			mixed ^= mixed >>> 33;
			return (float) ((mixed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
		}
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

		private boolean containsAabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			for (Plane plane : this.planes) {
				double nx = plane.normalX >= 0.0D ? minX : maxX;
				double ny = plane.normalY >= 0.0D ? minY : maxY;
				double nz = plane.normalZ >= 0.0D ? minZ : maxZ;
				if (plane.distance(nx, ny, nz) < 0.0D) {
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

	private static List<EntitySnapshot> captureEntities(ServerPlayer viewer, ServerLevel level, CameraFrustum frustum, Vec3 forward, Vec3 right, Vec3 up) {
		BlockBounds bounds = frustum.bounds();
		AABB searchBox = new AABB(
				bounds.minX(),
				bounds.minY(),
				bounds.minZ(),
				bounds.maxX() + 1.0D,
				bounds.maxY() + 1.0D,
				bounds.maxZ() + 1.0D
		);
		List<Entity> entities = level.getEntities(viewer, searchBox, entity -> entity != null && entity.isAlive() && !entity.isInvisible());
		if (entities.isEmpty()) {
			return List.of();
		}
		List<EntitySnapshot> result = new ArrayList<>(entities.size());
		for (Entity entity : entities) {
			try {
				AABB box = entity.getBoundingBox();
				if (!frustum.intersectsAabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
					continue;
				}
				EntitySnapshot snapshot = CameraEntityRenderer.captureEntity(viewer, forward, right, up, entity);
				if (snapshot != null) {
					result.add(snapshot);
				}
			} catch (Throwable throwable) {
				Lg2.LOGGER.debug("Failed to prepare camera entity {}", entity, throwable);
			}
		}
		return result;
	}

	private static List<EntitySnapshot> captureBlockEntities(ServerLevel level, CameraFrustum frustum) {
		BlockBounds bounds = frustum.bounds();
		int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
		int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
		int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
		int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());
		int chunkSpanX = maxChunkX - minChunkX + 1;
		int chunkSpanZ = maxChunkZ - minChunkZ + 1;
		List<EntitySnapshot> result = new ArrayList<>(Math.max(16, chunkSpanX * chunkSpanZ * 2));
		for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
			for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				boolean chunkFullyInsideFrustum = frustum.containsAabb(
						chunk.getPos().getMinBlockX(),
						level.getMinY(),
						chunk.getPos().getMinBlockZ(),
						chunk.getPos().getMaxBlockX() + 1.0D,
						level.getMaxY(),
						chunk.getPos().getMaxBlockZ() + 1.0D
				);
				for (var entry : chunk.getBlockEntities().entrySet()) {
					try {
						var blockEntity = entry.getValue();
						if (blockEntity == null || blockEntity.isRemoved()) {
							continue;
						}
						BlockPos blockPos = entry.getKey();
						if (!chunkFullyInsideFrustum && !frustum.intersectsAabb(
								blockPos.getX(),
								blockPos.getY(),
								blockPos.getZ(),
								blockPos.getX() + 1.0D,
								blockPos.getY() + 1.0D,
								blockPos.getZ() + 1.0D
						)) {
							continue;
						}
						EntitySnapshot snapshot = CameraEntityRenderer.captureBlockEntity(blockEntity);
						if (snapshot != null) {
							result.add(snapshot);
						}
					} catch (Throwable throwable) {
						Lg2.LOGGER.debug("Failed to prepare camera block entity {}", entry.getValue(), throwable);
					}
				}
			}
		}
		return result;
	}
}
