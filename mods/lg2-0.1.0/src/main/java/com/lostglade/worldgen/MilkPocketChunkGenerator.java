package com.lostglade.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.server.level.WorldGenRegion;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class MilkPocketChunkGenerator extends ChunkGenerator {
	public static final MapCodec<MilkPocketChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome)
			).apply(instance, MilkPocketChunkGenerator::new)
	);

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private final Holder<Biome> biome;

	public MilkPocketChunkGenerator(Holder<Biome> biome) {
		super(new FixedBiomeSource(biome));
		this.biome = biome;
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
		Heightmap.primeHeightmaps(
				chunk,
				SetHolder.HEIGHTMAP_TYPES
		);
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
		return 0;
	}

	@Override
	public int getMinY() {
		return -64;
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor levelHeightAccessor) {
		return 1;
	}

	@Override
	public int getBaseHeight(
			int x,
			int z,
			Heightmap.Types heightmapType,
			LevelHeightAccessor levelHeightAccessor,
			RandomState randomState
	) {
		return levelHeightAccessor.getMinY();
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
		BlockState[] states = new BlockState[levelHeightAccessor.getHeight()];
		Arrays.fill(states, AIR);
		return new NoiseColumn(levelHeightAccessor.getMinY(), states);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
	}

	private static final class SetHolder {
		private static final java.util.Set<Heightmap.Types> HEIGHTMAP_TYPES = java.util.Set.of(
				Heightmap.Types.WORLD_SURFACE_WG,
				Heightmap.Types.OCEAN_FLOOR_WG,
				Heightmap.Types.WORLD_SURFACE,
				Heightmap.Types.OCEAN_FLOOR,
				Heightmap.Types.MOTION_BLOCKING,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
		);
	}
}
