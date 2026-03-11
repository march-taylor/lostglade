package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class BackroomsChunkGenerator extends ChunkGenerator {
	public static final MapCodec<BackroomsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					BackroomsBiomeSource.CODEC.codec().optionalFieldOf("biome_source").forGetter(generator -> Optional.of(generator.backroomsBiomeSource)),
					Biome.CODEC.optionalFieldOf("biome").forGetter(generator -> Optional.empty())
			).apply(instance, BackroomsChunkGenerator::fromCodec)
	);

	private static final Set<Heightmap.Types> HEIGHTMAP_TYPES = Set.of(
			Heightmap.Types.WORLD_SURFACE_WG,
			Heightmap.Types.OCEAN_FLOOR_WG,
			Heightmap.Types.WORLD_SURFACE,
			Heightmap.Types.OCEAN_FLOOR,
			Heightmap.Types.MOTION_BLOCKING,
			Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
	);

	private static final BlockState BACKROOMS_BLOCK = ModBlocks.BACKROOMS_BLOCK.defaultBlockState();
	private static final BlockState BACKROOMS_LIGHT_BLOCK = ModBlocks.BACKROOMS_LIGHTBLOCK.defaultBlockState();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private final BackroomsBiomeSource backroomsBiomeSource;

	public BackroomsChunkGenerator(BackroomsBiomeSource biomeSource) {
		super(biomeSource);
		this.backroomsBiomeSource = biomeSource;
	}

	private static BackroomsChunkGenerator fromCodec(Optional<BackroomsBiomeSource> biomeSource, Optional<Holder<Biome>> legacyBiome) {
		BackroomsBiomeSource resolvedBiomeSource = biomeSource.orElseGet(() -> legacyBiome
				.map(BackroomsChunkGenerator::createLegacyBiomeSource)
				.orElseThrow(() -> new IllegalStateException("Backrooms chunk generator requires biome_source or biome")));
		return new BackroomsChunkGenerator(resolvedBiomeSource);
	}

	private static BackroomsBiomeSource createLegacyBiomeSource(Holder<Biome> biome) {
		return new BackroomsBiomeSource(biome, biome, biome, biome, biome, biome);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
		return ChunkGeneratorStructureState.createForFlat(randomState, seed, this.biomeSource, Stream.empty());
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(
			Blender blender,
			RandomState randomState,
			StructureManager structureManager,
			ChunkAccess chunk
	) {
		BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
		int minBlockX = chunk.getPos().getMinBlockX();
		int minBlockZ = chunk.getPos().getMinBlockZ();

		for (int localX = 0; localX < 16; localX++) {
			int worldX = minBlockX + localX;
			for (int localZ = 0; localZ < 16; localZ++) {
				int worldZ = minBlockZ + localZ;
				for (int y = BackroomsLayout.FLOOR_Y; y <= BackroomsLayout.CEILING_Y; y++) {
					BlockState state = getBlockState(worldX, y, worldZ);
					if (state.isAir()) {
						continue;
					}

					chunk.setBlockState(mutablePos.set(localX, y, localZ), state);
				}
			}
		}

		Heightmap.primeHeightmaps(chunk, HEIGHTMAP_TYPES);
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
	}

	@Override
	public void applyCarvers(
			WorldGenRegion region,
			long seed,
			RandomState randomState,
			net.minecraft.world.level.biome.BiomeManager biomeManager,
			StructureManager structureManager,
			ChunkAccess chunk
	) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
	}

	@Override
	public int getGenDepth() {
		return 384;
	}

	@Override
	public int getSeaLevel() {
		return BackroomsLayout.FLOOR_Y;
	}

	@Override
	public int getMinY() {
		return 0;
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor levelHeightAccessor) {
		return BackroomsLayout.FLOOR_Y + 1;
	}

	@Override
	public int getBaseHeight(
			int x,
			int z,
			Heightmap.Types heightmapType,
			LevelHeightAccessor levelHeightAccessor,
			RandomState randomState
	) {
		for (int y = BackroomsLayout.CEILING_Y; y >= BackroomsLayout.FLOOR_Y; y--) {
			BlockState state = getBlockState(x, y, z);
			if (heightmapType.isOpaque().test(state)) {
				return y + 1;
			}
		}

		return levelHeightAccessor.getMinY();
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
		int minY = levelHeightAccessor.getMinY();
		int height = levelHeightAccessor.getHeight();
		BlockState[] states = new BlockState[height];
		for (int index = 0; index < height; index++) {
			int y = minY + index;
			states[index] = getBlockState(x, y, z);
		}

		return new NoiseColumn(minY, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
		lines.add("Backrooms Generator");
		lines.add("Backrooms Zone: " + BackroomsLayout.getZoneAtBlock(pos.getX(), pos.getZ()).debugName);
	}

	private static BlockState getBlockState(int x, int y, int z) {
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z);
		if (y == BackroomsLayout.FLOOR_Y) {
			return BACKROOMS_BLOCK;
		}

		if (y == BackroomsLayout.CEILING_Y) {
			return BackroomsLayout.hasCeilingLight(zone, x, z) ? BACKROOMS_LIGHT_BLOCK : BACKROOMS_BLOCK;
		}

		if (y >= BackroomsLayout.WALL_MIN_Y && y <= BackroomsLayout.WALL_MAX_Y && !BackroomsLayout.isCorridor(zone, x, z)) {
			return BACKROOMS_BLOCK;
		}

		return AIR;
	}
}
