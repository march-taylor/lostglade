package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public final class BackroomsChunkGenerator extends ChunkGenerator {
	public static final MapCodec<BackroomsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome)
			).apply(instance, BackroomsChunkGenerator::new)
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
	private static final int FLOOR_Y = 64;
	private static final int CEILING_Y = 69;
	private static final int WALL_MIN_Y = FLOOR_Y + 1;
	private static final int WALL_MAX_Y = CEILING_Y - 1;
	private static final int CELL_SIZE = 8;
	private static final int DOOR_WIDTH = 2;
	private final Holder<Biome> biome;

	public BackroomsChunkGenerator(Holder<Biome> biome) {
		super(createBiomeSource(biome));
		this.biome = biome;
	}

	private static BiomeSource createBiomeSource(Holder<Biome> biome) {
		return new FixedBiomeSource(biome);
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
				for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
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
		return FLOOR_Y;
	}

	@Override
	public int getMinY() {
		return 0;
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor levelHeightAccessor) {
		return FLOOR_Y + 1;
	}

	@Override
	public int getBaseHeight(
			int x,
			int z,
			Heightmap.Types heightmapType,
			LevelHeightAccessor levelHeightAccessor,
			RandomState randomState
	) {
		for (int y = CEILING_Y; y >= FLOOR_Y; y--) {
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
	}

	private static BlockState getBlockState(int x, int y, int z) {
		if (y == FLOOR_Y) {
			return BACKROOMS_BLOCK;
		}

		if (y == CEILING_Y) {
			return hasCeilingLight(x, z) ? BACKROOMS_LIGHT_BLOCK : BACKROOMS_BLOCK;
		}

		if (y >= WALL_MIN_Y && y <= WALL_MAX_Y && isWall(x, z)) {
			return BACKROOMS_BLOCK;
		}

		return AIR;
	}

	private static boolean isWall(int x, int z) {
		int localX = Math.floorMod(x, CELL_SIZE);
		int localZ = Math.floorMod(z, CELL_SIZE);
		int cellX = Math.floorDiv(x, CELL_SIZE);
		int cellZ = Math.floorDiv(z, CELL_SIZE);

		boolean verticalWall = localX == 0 && !isOpening(cellX, cellZ, localZ, 0x632BE59BD9B4E019L);
		boolean horizontalWall = localZ == 0 && !isOpening(cellX, cellZ, localX, 0x9E3779B97F4A7C15L);
		boolean pillar = hasPillar(cellX, cellZ)
				&& localX >= 3 && localX <= 4
				&& localZ >= 3 && localZ <= 4;

		return verticalWall || horizontalWall || pillar;
	}

	private static boolean isOpening(int cellX, int cellZ, int localCoordinate, long salt) {
		int maxStart = CELL_SIZE - DOOR_WIDTH - 1;
		if (maxStart <= 1) {
			return false;
		}

		int openingStart = 1 + positiveMod(mix(cellX, cellZ, salt), maxStart - 1);
		return localCoordinate >= openingStart && localCoordinate < openingStart + DOOR_WIDTH;
	}

	private static boolean hasPillar(int cellX, int cellZ) {
		return positiveMod(mix(cellX, cellZ, 0x4F1BBCDCBFA54001L), 5) == 0;
	}

	private static boolean hasCeilingLight(int x, int z) {
		int localX = Math.floorMod(x, CELL_SIZE);
		int localZ = Math.floorMod(z, CELL_SIZE);
		int cellX = Math.floorDiv(x, CELL_SIZE);
		int cellZ = Math.floorDiv(z, CELL_SIZE);

		return localX == (CELL_SIZE / 2)
				&& localZ == (CELL_SIZE / 2)
				&& positiveMod(mix(cellX, cellZ, 0x2C1B3C6D5E7F91A3L), 4) == 0;
	}

	private static int positiveMod(long value, int modulo) {
		return (int) Math.floorMod(value, modulo);
	}

	private static long mix(int a, int b, long salt) {
		long value = salt;
		value ^= (long) a * 341873128712L;
		value ^= (long) b * 132897987541L;
		value ^= value >>> 33;
		value *= 0xff51afd7ed558ccdl;
		value ^= value >>> 33;
		value *= 0xc4ceb9fe1a85ec53L;
		value ^= value >>> 33;
		return value;
	}
}
