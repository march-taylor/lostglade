package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
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

	private static final BlockState BACKROOMS_LIGHT_BLOCK = ModBlocks.BACKROOMS_LIGHTBLOCK.defaultBlockState();
	private static final BlockState BACKROOMS_BLOCK = ModBlocks.BACKROOMS_BLOCK.defaultBlockState();
	private static final BlockState BACKROOMS_DOOR_BLOCK = ModBlocks.BACKROOMS_DOOR.defaultBlockState();
	private static final BlockState EXIT_SIGN_WALL_BLOCK = ModBlocks.EXIT_WALL_SIGN.defaultBlockState();
	private static final long BACKROOMS_VARIANT_SALT = 0x4c47324241434b52L;
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
		Map<Long, ColumnLayout> columnCache = new HashMap<>();
		int minBlockX = chunk.getPos().getMinBlockX();
		int minBlockZ = chunk.getPos().getMinBlockZ();
		int minY = chunk.getMinY();
		int maxY = minY + chunk.getHeight() - 1;

		for (int localX = 0; localX < 16; localX++) {
			int worldX = minBlockX + localX;
			for (int localZ = 0; localZ < 16; localZ++) {
				int worldZ = minBlockZ + localZ;
				for (int y = minY; y <= maxY; y++) {
					BlockState state = getBlockState(worldX, y, worldZ, columnCache);
					if (state.isAir()) {
						continue;
					}

					chunk.setBlockState(mutablePos.set(localX, y, localZ), state);
					if ((state.is(ModBlocks.EXIT_SIGN) || state.is(ModBlocks.EXIT_WALL_SIGN))
							&& state.getBlock() instanceof EntityBlock entityBlock) {
						BlockEntity blockEntity = entityBlock.newBlockEntity(new BlockPos(worldX, y, worldZ), state);
						if (blockEntity != null) {
							chunk.setBlockEntity(blockEntity);
						}
					}
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
		Map<Long, ColumnLayout> columnCache = new HashMap<>();
		for (int y = levelHeightAccessor.getMinY() + levelHeightAccessor.getHeight() - 1; y >= levelHeightAccessor.getMinY(); y--) {
			BlockState state = getBlockState(x, y, z, columnCache);
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
		Map<Long, ColumnLayout> columnCache = new HashMap<>();
		BlockState[] states = new BlockState[height];
		for (int index = 0; index < height; index++) {
			int y = minY + index;
			states[index] = getBlockState(x, y, z, columnCache);
		}

		return new NoiseColumn(minY, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
		lines.add("Backrooms Generator");
		lines.add("Backrooms Zone: " + BackroomsLayout.getZoneAtBlock(pos.getX(), pos.getZ(), BackroomsLayout.getLevelIndex(pos.getY())).debugName);
	}

	private static BlockState getBlockState(int x, int y, int z, Map<Long, ColumnLayout> columnCache) {
		int levelIndex = BackroomsLayout.getLevelIndex(y);
		ColumnLayout layout = getColumnLayout(x, z, levelIndex, columnCache);
		int localY = Math.floorMod(y - BackroomsLayout.FLOOR_Y, BackroomsLayout.LEVEL_HEIGHT);
		if (localY == 0) {
			return randomizedBackroomsBlock(x, y, z);
		}

		if (localY == BackroomsLayout.LEVEL_HEIGHT - 1) {
			return layout.ceilingLight ? BACKROOMS_LIGHT_BLOCK : randomizedBackroomsBlock(x, y, z);
		}

		if (layout.doorPlacement != null) {
			if (localY == 1) {
				return createDoorState(layout.doorPlacement, DoubleBlockHalf.LOWER);
			}
			if (localY == 2) {
				return createDoorState(layout.doorPlacement, DoubleBlockHalf.UPPER);
			}
		}

		if (localY == 3) {
			Direction exitSignFacing = getExitSignFacingAt(x, z, levelIndex, columnCache);
			if (exitSignFacing != null) {
				return createExitSignState(exitSignFacing);
			}
		}

		if (localY >= 1 && localY <= BackroomsLayout.WALL_MAX_Y - BackroomsLayout.FLOOR_Y && !layout.corridor) {
			return randomizedBackroomsBlock(x, y, z);
		}

		return AIR;
	}

	private static ColumnLayout getColumnLayout(int x, int z, int levelIndex, Map<Long, ColumnLayout> columnCache) {
		long key = BlockPos.asLong(x, levelIndex, z);
		ColumnLayout cached = columnCache.get(key);
		if (cached != null) {
			return cached;
		}

		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		ColumnLayout layout = new ColumnLayout(
				zone,
				BackroomsLayout.getRoomDoorAt(zone, x, z, levelIndex),
				BackroomsLayout.isCorridor(zone, x, z, levelIndex),
				BackroomsLayout.hasCeilingLight(zone, x, z, levelIndex)
		);
		columnCache.put(key, layout);
		return layout;
	}

	private static BlockState randomizedBackroomsBlock(int x, int y, int z) {
		return ModBlocks.getRandomizedBackroomsBlockState(BlockPos.asLong(x, y, z) ^ BACKROOMS_VARIANT_SALT);
	}

	private static BlockState createDoorState(BackroomsLayout.DoorPlacement placement, DoubleBlockHalf half) {
		return BACKROOMS_DOOR_BLOCK
				.setValue(net.minecraft.world.level.block.DoorBlock.FACING, placement.facing())
				.setValue(net.minecraft.world.level.block.DoorBlock.HINGE, placement.hinge())
				.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false)
				.setValue(net.minecraft.world.level.block.DoorBlock.POWERED, false)
				.setValue(net.minecraft.world.level.block.DoorBlock.HALF, half);
	}

	private static Direction getExitSignFacingAt(int x, int z, int levelIndex, Map<Long, ColumnLayout> columnCache) {
		for (Direction corridorDirection : Direction.Plane.HORIZONTAL) {
			int doorX = x - corridorDirection.getStepX();
			int doorZ = z - corridorDirection.getStepZ();
			BackroomsLayout.DoorPlacement placement = getColumnLayout(doorX, doorZ, levelIndex, columnCache).doorPlacement;
			if (placement != null && placement.facing().getOpposite() == corridorDirection) {
				return corridorDirection;
			}
		}

		return null;
	}

	private static BlockState createExitSignState(Direction facing) {
		return EXIT_SIGN_WALL_BLOCK.setValue(WallSignBlock.FACING, facing);
	}

	private record ColumnLayout(
			BackroomsLayout.ZoneType zone,
			BackroomsLayout.DoorPlacement doorPlacement,
			boolean corridor,
			boolean ceilingLight
	) {
	}
}
