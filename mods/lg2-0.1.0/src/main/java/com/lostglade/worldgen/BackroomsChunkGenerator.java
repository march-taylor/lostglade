package com.lostglade.worldgen;

import com.lostglade.block.ModBlocks;
import com.lostglade.block.ExitSignBlock;
import com.lostglade.block.ExitWallSignBlock;
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
import net.minecraft.world.level.block.entity.SignBlockEntity;
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
import java.util.LinkedHashMap;
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
	private static final int CHUNK_LAYOUT_BORDER = 1;
	private static final int CHUNK_LAYOUT_SIZE = 16 + (CHUNK_LAYOUT_BORDER * 2);
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
		int minBlockX = chunk.getPos().getMinBlockX();
		int minBlockZ = chunk.getPos().getMinBlockZ();
		int minY = chunk.getMinY();
		int maxY = minY + chunk.getHeight() - 1;
		int firstLevelIndex = BackroomsLayout.getLevelIndex(minY);
		int lastLevelIndex = BackroomsLayout.getLevelIndex(maxY);
		ChunkLayoutGrid layoutGrid = new ChunkLayoutGrid(minBlockX, minBlockZ, firstLevelIndex, lastLevelIndex);

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

					ColumnLayout layout = layoutGrid.get(localX, localZ, levelIndex);
					Direction exitSignFacing = layout.corridor
							? layoutGrid.getExitSignFacing(localX, localZ, levelIndex)
							: null;
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
					BackroomsSpecialRooms.applyColumnOverrides(layout.specialRoom, layout.ladderRoom, worldX, worldZ, floorY, ceilingY, columnStates);

					if (floorY >= minY && !columnStates.floor().isAir()) {
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

					if (ceilingY <= maxY && !columnStates.ceiling().isAir()) {
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

			ColumnLayout layout = getSharedColumnLayout(x, z, levelIndex);
			Direction exitSignFacing = layout.corridor
					? getExitSignFacingAt(x, z, levelIndex)
					: null;
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
			BackroomsSpecialRooms.applyColumnOverrides(layout.specialRoom, layout.ladderRoom, x, z, floorY, ceilingY, columnStates);

			if (!columnStates.floor().isAir()) {
				setColumnState(states, minY, floorY, columnStates.floor());
			}
			if (!columnStates.lower().isAir()) {
				setColumnState(states, minY, floorY + 1, columnStates.lower());
			}
			if (!columnStates.upper().isAir()) {
				setColumnState(states, minY, floorY + 2, columnStates.upper());
			}
			if (!columnStates.top().isAir()) {
				setColumnState(states, minY, floorY + 3, columnStates.top());
			}
			if (!columnStates.ceiling().isAir()) {
				setColumnState(states, minY, ceilingY, columnStates.ceiling());
			}
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

	private static ColumnLayout getSharedColumnLayout(int x, int z, int levelIndex) {
		long key = BlockPos.asLong(x, levelIndex, z);
		GeneratorCache generatorCache = generatorCache();
		ColumnLayout cached = generatorCache.columnLayoutCache.get(key);
		if (cached != null) {
			return cached;
		}

		BackroomsLayout.ZoneType zone = BackroomsLayout.getZoneAtBlock(x, z, levelIndex);
		boolean corridor = BackroomsLayout.isCorridor(zone, x, z, levelIndex);
		BackroomsLayout.SpecialRoomPlacement specialRoom = BackroomsLayout.getSpecialRoomAt(zone, x, z, levelIndex);
		BackroomsLayout.LadderRoomPlacement ladderRoom = specialRoom == null
				? BackroomsLayout.getLadderRoomAt(x, z, levelIndex)
				: null;
		ColumnLayout layout = new ColumnLayout(
				zone,
				corridor ? null : BackroomsLayout.getRoomDoorAt(zone, x, z, levelIndex),
				corridor,
				corridor && BackroomsLayout.hasCeilingLight(zone, x, z, levelIndex),
				specialRoom,
				ladderRoom
		);
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

	private static Direction getExitSignFacingAt(int x, int z, int levelIndex) {
		long key = BlockPos.asLong(x, levelIndex, z);
		GeneratorCache generatorCache = generatorCache();
		Optional<Direction> cached = generatorCache.exitSignCache.get(key);
		if (cached != null) {
			return cached.orElse(null);
		}

		for (Direction supportDirection : Direction.Plane.HORIZONTAL) {
			int supportX = x + supportDirection.getStepX();
			int supportZ = z + supportDirection.getStepZ();
			BackroomsLayout.DoorPlacement placement = getSharedColumnLayout(supportX, supportZ, levelIndex).doorPlacement;
			if (placement != null && placement.facing() == supportDirection) {
				Direction facing = supportDirection.getOpposite();
				generatorCache.exitSignCache.put(key, Optional.of(facing));
				return facing;
			}
		}

		generatorCache.exitSignCache.put(key, Optional.empty());
		return null;
	}

	private static GeneratorCache generatorCache() {
		return GENERATOR_CACHE.get();
	}

	private static BlockState createExitSignState(Direction facing) {
		return EXIT_SIGN_WALL_BLOCK.setValue(WallSignBlock.FACING, facing);
	}

	private static void setChunkBlock(ChunkAccess chunk, BlockPos.MutableBlockPos mutablePos, int localX, int y, int localZ, BlockState state) {
		chunk.setBlockState(mutablePos.set(localX, y, localZ), state);
		if (state.getBlock() instanceof EntityBlock entityBlock) {
			BlockPos worldPos = new BlockPos(chunk.getPos().getMinBlockX() + localX, y, chunk.getPos().getMinBlockZ() + localZ);
			BlockEntity blockEntity = entityBlock.newBlockEntity(worldPos, state);
			if (blockEntity != null) {
				if (blockEntity instanceof ChestBlockEntity chestBlockEntity) {
					populateTrashChestLoot(chestBlockEntity, worldPos);
				} else if ((state.getBlock() instanceof ExitSignBlock || state.getBlock() instanceof ExitWallSignBlock)
						&& blockEntity instanceof SignBlockEntity signBlockEntity) {
					ExitSignBlock.applyFixedText(signBlockEntity);
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
			BackroomsLayout.SpecialRoomPlacement specialRoom,
			BackroomsLayout.LadderRoomPlacement ladderRoom
	) {
	}

	private static final class ChunkLayoutGrid {
		private final int minBlockX;
		private final int minBlockZ;
		private final int firstLevelIndex;
		private final int levelCount;
		private final ColumnLayout[] layouts;

		ChunkLayoutGrid(int minBlockX, int minBlockZ, int firstLevelIndex, int lastLevelIndex) {
			this.minBlockX = minBlockX;
			this.minBlockZ = minBlockZ;
			this.firstLevelIndex = firstLevelIndex;
			this.levelCount = lastLevelIndex - firstLevelIndex + 1;
			this.layouts = new ColumnLayout[CHUNK_LAYOUT_SIZE * CHUNK_LAYOUT_SIZE * this.levelCount];

			for (int levelOffset = 0; levelOffset < this.levelCount; levelOffset++) {
				int levelIndex = this.firstLevelIndex + levelOffset;
				for (int gridX = 0; gridX < CHUNK_LAYOUT_SIZE; gridX++) {
					int worldX = this.minBlockX + gridX - CHUNK_LAYOUT_BORDER;
					for (int gridZ = 0; gridZ < CHUNK_LAYOUT_SIZE; gridZ++) {
						int worldZ = this.minBlockZ + gridZ - CHUNK_LAYOUT_BORDER;
						this.layouts[index(gridX, gridZ, levelOffset)] = getSharedColumnLayout(worldX, worldZ, levelIndex);
					}
				}
			}
		}

		ColumnLayout get(int localX, int localZ, int levelIndex) {
			return this.layouts[index(localX + CHUNK_LAYOUT_BORDER, localZ + CHUNK_LAYOUT_BORDER, levelIndex - this.firstLevelIndex)];
		}

		Direction getExitSignFacing(int localX, int localZ, int levelIndex) {
			int levelOffset = levelIndex - this.firstLevelIndex;
			int gridX = localX + CHUNK_LAYOUT_BORDER;
			int gridZ = localZ + CHUNK_LAYOUT_BORDER;
			for (Direction supportDirection : Direction.Plane.HORIZONTAL) {
				ColumnLayout support = this.layouts[index(
						gridX + supportDirection.getStepX(),
						gridZ + supportDirection.getStepZ(),
						levelOffset
				)];
				if (support.doorPlacement != null && support.doorPlacement.facing() == supportDirection) {
					return supportDirection.getOpposite();
				}
			}
			return null;
		}

		private int index(int gridX, int gridZ, int levelOffset) {
			return (levelOffset * CHUNK_LAYOUT_SIZE * CHUNK_LAYOUT_SIZE) + (gridX * CHUNK_LAYOUT_SIZE) + gridZ;
		}
	}

	private static final class GeneratorCache {
		private static final int MAX_COLUMN_LAYOUT_CACHE = 262144;
		private static final int MAX_EXIT_SIGN_CACHE = 131072;

		final Map<Long, ColumnLayout> columnLayoutCache = createCappedCache(MAX_COLUMN_LAYOUT_CACHE);
		final Map<Long, Optional<Direction>> exitSignCache = createCappedCache(MAX_EXIT_SIGN_CACHE);

		private static <K, V> Map<K, V> createCappedCache(int maxSize) {
			return new LinkedHashMap<>(256, 0.75F, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
					return this.size() > maxSize;
				}
			};
		}
	}
}
