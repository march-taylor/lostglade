package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
	private static final ThreadLocal<GeneratorCache> GENERATOR_CACHE = ThreadLocal.withInitial(GeneratorCache::new);

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
		int firstLevelIndex = BackroomsLayout.getLevelIndex(minY);
		int lastLevelIndex = BackroomsLayout.getLevelIndex(maxY);

		for (int localX = 0; localX < 16; localX++) {
			int worldX = minBlockX + localX;
			for (int localZ = 0; localZ < 16; localZ++) {
				int worldZ = minBlockZ + localZ;
				for (int levelIndex = firstLevelIndex; levelIndex <= lastLevelIndex; levelIndex++) {
					int floorY = BackroomsLayout.FLOOR_Y + levelIndex * BackroomsLayout.LEVEL_HEIGHT;
					int ceilingY = floorY + BackroomsLayout.LEVEL_HEIGHT - 1;
					if (ceilingY < minY || floorY > maxY) {
						continue;
					}

					ColumnLayout layout = getColumnLayout(worldX, worldZ, levelIndex, columnCache);
					Direction exitSignFacing = layout.corridor
							? null
							: getExitSignFacingAt(worldX, worldZ, levelIndex, columnCache);
					BackroomsSpecialRooms.ColumnStates columnStates = BackroomsSpecialRooms.createBaseStates(
							layout.doorPlacement,
							layout.corridor,
							layout.ceilingLight,
							worldX,
							worldZ,
							floorY,
							ceilingY,
							exitSignFacing,
							BACKROOMS_LIGHT_BLOCK,
							AIR
					);
					BackroomsSpecialRooms.applyColumnOverrides(layout.specialRoom, worldX, worldZ, floorY, ceilingY, columnStates);

					if (floorY >= minY) {
						setChunkBlock(chunk, mutablePos, localX, floorY, localZ, columnStates.floor());
					}

					if (floorY + 1 <= maxY) {
						BlockState state = columnStates.lower();
						if (!state.isAir()) {
							setChunkBlock(chunk, mutablePos, localX, floorY + 1, localZ, state);
						}
					}

					if (floorY + 2 <= maxY) {
						BlockState state = columnStates.upper();
						if (!state.isAir()) {
							setChunkBlock(chunk, mutablePos, localX, floorY + 2, localZ, state);
						}
					}

					if (floorY + 3 <= maxY) {
						BlockState state = columnStates.top();
						if (!state.isAir()) {
							setChunkBlock(chunk, mutablePos, localX, floorY + 3, localZ, state);
						}
					}

					if (ceilingY <= maxY) {
						setChunkBlock(chunk, mutablePos, localX, ceilingY, localZ, columnStates.ceiling());
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
		int topY = levelHeightAccessor.getMinY() + levelHeightAccessor.getHeight() - 1;
		int topLevelIndex = BackroomsLayout.getLevelIndex(topY);
		int ceilingY = BackroomsLayout.FLOOR_Y + topLevelIndex * BackroomsLayout.LEVEL_HEIGHT + (BackroomsLayout.LEVEL_HEIGHT - 1);
		if (ceilingY > topY) {
			ceilingY -= BackroomsLayout.LEVEL_HEIGHT;
		}
		if (ceilingY >= levelHeightAccessor.getMinY()) {
			return ceilingY + 1;
		}

		return levelHeightAccessor.getMinY();
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor levelHeightAccessor, RandomState randomState) {
		int minY = levelHeightAccessor.getMinY();
		int height = levelHeightAccessor.getHeight();
		Map<Long, ColumnLayout> columnCache = new HashMap<>();
		BlockState[] states = new BlockState[height];
		Arrays.fill(states, AIR);

		int maxY = minY + height - 1;
		int firstLevelIndex = BackroomsLayout.getLevelIndex(minY);
		int lastLevelIndex = BackroomsLayout.getLevelIndex(maxY);
		for (int levelIndex = firstLevelIndex; levelIndex <= lastLevelIndex; levelIndex++) {
			int floorY = BackroomsLayout.FLOOR_Y + levelIndex * BackroomsLayout.LEVEL_HEIGHT;
			int ceilingY = floorY + BackroomsLayout.LEVEL_HEIGHT - 1;
			if (ceilingY < minY || floorY > maxY) {
				continue;
			}

			ColumnLayout layout = getColumnLayout(x, z, levelIndex, columnCache);
			Direction exitSignFacing = layout.corridor
					? null
							: getExitSignFacingAt(x, z, levelIndex, columnCache);
			BackroomsSpecialRooms.ColumnStates columnStates = BackroomsSpecialRooms.createBaseStates(
					layout.doorPlacement,
					layout.corridor,
					layout.ceilingLight,
					x,
					z,
					floorY,
					ceilingY,
					exitSignFacing,
					BACKROOMS_LIGHT_BLOCK,
					AIR
			);
			BackroomsSpecialRooms.applyColumnOverrides(layout.specialRoom, x, z, floorY, ceilingY, columnStates);

			setColumnState(states, minY, floorY, columnStates.floor());
			setColumnState(states, minY, floorY + 1, columnStates.lower());
			setColumnState(states, minY, floorY + 2, columnStates.upper());
			setColumnState(states, minY, floorY + 3, columnStates.top());
			setColumnState(states, minY, ceilingY, columnStates.ceiling());
		}

		return new NoiseColumn(minY, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
		lines.add("Backrooms Generator");
		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(pos.getX(), pos.getZ(), BackroomsLayout.getLevelIndex(pos.getY()));
		lines.add("Backrooms Zone: " + zone.debugName);
		BackroomsLayout.SpecialRoomPlacement specialRoom = BackroomsLayout.getSpecialRoomAt(zone, pos.getX(), pos.getZ(), BackroomsLayout.getLevelIndex(pos.getY()));
		if (specialRoom != null) {
			lines.add("Backrooms Special Room: " + specialRoom.type().id);
		}
	}

	private static ColumnLayout getColumnLayout(int x, int z, int levelIndex, Map<Long, ColumnLayout> columnCache) {
		long key = BlockPos.asLong(x, levelIndex, z);
		ColumnLayout cached = columnCache.get(key);
		if (cached != null) {
			return cached;
		}

		GeneratorCache generatorCache = generatorCache();
		ColumnLayout sharedCached = generatorCache.columnLayoutCache.get(key);
		if (sharedCached != null) {
			columnCache.put(key, sharedCached);
			return sharedCached;
		}

		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		boolean corridor = BackroomsLayout.isCorridor(zone, x, z, levelIndex);
		BackroomsLayout.SpecialRoomPlacement specialRoom = BackroomsLayout.getSpecialRoomAt(zone, x, z, levelIndex);
		ColumnLayout layout = new ColumnLayout(
				zone,
				corridor ? null : BackroomsLayout.getRoomDoorAt(zone, x, z, levelIndex),
				corridor,
				corridor && BackroomsLayout.hasCeilingLight(zone, x, z, levelIndex),
				specialRoom
		);
		columnCache.put(key, layout);
		generatorCache.columnLayoutCache.put(key, layout);
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
		long key = BlockPos.asLong(x, levelIndex, z);
		GeneratorCache generatorCache = generatorCache();
		Optional<Direction> cached = generatorCache.exitSignCache.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}

		for (Direction corridorDirection : Direction.Plane.HORIZONTAL) {
			int doorX = x - corridorDirection.getStepX();
			int doorZ = z - corridorDirection.getStepZ();
			BackroomsLayout.DoorPlacement placement = getColumnLayout(doorX, doorZ, levelIndex, columnCache).doorPlacement;
			if (placement != null && placement.facing().getOpposite() == corridorDirection) {
				generatorCache.exitSignCache.put(key, Optional.of(corridorDirection));
				return corridorDirection;
			}
		}

		generatorCache.exitSignCache.put(key, Optional.empty());
		return null;
	}

	private static GeneratorCache generatorCache() {
		GeneratorCache cache = GENERATOR_CACHE.get();
		cache.trimIfNeeded();
		return cache;
	}

	private static BlockState createExitSignState(Direction facing) {
		return EXIT_SIGN_WALL_BLOCK.setValue(WallSignBlock.FACING, facing);
	}

	private static void setChunkBlock(ChunkAccess chunk, BlockPos.MutableBlockPos mutablePos, int localX, int y, int localZ, BlockState state) {
		BlockPos worldPos = new BlockPos(chunk.getPos().getMinBlockX() + localX, y, chunk.getPos().getMinBlockZ() + localZ);
		chunk.setBlockState(mutablePos.set(localX, y, localZ), state);
		if (state.getBlock() instanceof EntityBlock entityBlock
				&& (state.is(ModBlocks.EXIT_SIGN) || state.is(ModBlocks.EXIT_WALL_SIGN) || state.is(Blocks.CHEST))) {
			BlockEntity blockEntity = entityBlock.newBlockEntity(worldPos, state);
			if (blockEntity != null) {
				if (blockEntity instanceof ChestBlockEntity chestBlockEntity) {
					populateTrashChestLoot(chestBlockEntity, worldPos);
				}
				chunk.setBlockEntity(blockEntity);
			}
		}
	}

	private static void populateTrashChestLoot(ChestBlockEntity chest, BlockPos pos) {
		RandomSource random = RandomSource.create(BlockPos.asLong(pos.getX(), pos.getY(), pos.getZ()) ^ 0x54524153484C4F4FL);
		int entries = 3 + random.nextInt(6);
		for (int i = 0; i < entries; i++) {
			int slot = random.nextInt(chest.getContainerSize());
			if (!chest.getItem(slot).isEmpty()) {
				continue;
			}
			chest.setItem(slot, createTrashLootItem(random));
		}
	}

	private static ItemStack createTrashLootItem(RandomSource random) {
		int roll = random.nextInt(100);
		if (roll < 42) {
			return new ItemStack(Items.STICK, 1 + random.nextInt(8));
		}
		if (roll < 57) {
			return new ItemStack(Items.ROTTEN_FLESH, 1 + random.nextInt(4));
		}
		if (roll < 66) {
			return new ItemStack(Items.STRING, 1 + random.nextInt(5));
		}
		if (roll < 74) {
			return new ItemStack(Items.PAPER, 1 + random.nextInt(6));
		}
		if (roll < 80) {
			return new ItemStack(Items.COAL, 1 + random.nextInt(3));
		}
		if (roll < 84) {
			return new ItemStack(Items.BREAD, 1 + random.nextInt(2));
		}
		if (roll < 87) {
			return new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(2));
		}
		if (roll < 90) {
			return new ItemStack(Items.GOLD_INGOT, 1);
		}
		if (roll < 93) {
			return new ItemStack(Items.OBSIDIAN, 1 + random.nextInt(2));
		}
		if (roll < 96) {
			return new ItemStack(Items.WRITABLE_BOOK, 1);
		}
		return new ItemStack(Items.DIAMOND, 1);
	}

	private static void setColumnState(BlockState[] states, int minY, int y, BlockState state) {
		int index = y - minY;
		if (index >= 0 && index < states.length) {
			states[index] = state;
		}
	}

	private record ColumnLayout(
			BackroomsLayout.ZoneType zone,
			BackroomsLayout.DoorPlacement doorPlacement,
			boolean corridor,
			boolean ceilingLight,
			BackroomsLayout.SpecialRoomPlacement specialRoom
	) {
	}

	private static final class GeneratorCache {
		private static final int MAX_COLUMN_LAYOUT_CACHE = 65536;
		private static final int MAX_EXIT_SIGN_CACHE = 65536;

		final Map<Long, ColumnLayout> columnLayoutCache = new HashMap<>();
		final Map<Long, Optional<Direction>> exitSignCache = new HashMap<>();

		void trimIfNeeded() {
			if (this.columnLayoutCache.size() > MAX_COLUMN_LAYOUT_CACHE) {
				this.columnLayoutCache.clear();
			}
			if (this.exitSignCache.size() > MAX_EXIT_SIGN_CACHE) {
				this.exitSignCache.clear();
			}
		}
	}
}
