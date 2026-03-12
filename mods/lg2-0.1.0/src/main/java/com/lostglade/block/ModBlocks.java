package com.lostglade.block;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	private static final Identifier BITCOIN_ORE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bitcoin_ore");
	private static final Identifier DEEPSLATE_BITCOIN_ORE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "deepslate_bitcoin_ore");
	private static final Identifier BACKROOMS_BLOCK_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms_block");
	private static final Identifier BACKROOMS_LIGHTBLOCK_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms_lightblock");
	private static final Identifier BACKROOMS_DOOR_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms_door");
	private static final Identifier SERVER_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "server");

	private static final ResourceKey<Block> BITCOIN_ORE_KEY = ResourceKey.create(Registries.BLOCK, BITCOIN_ORE_ID);
	private static final ResourceKey<Block> DEEPSLATE_BITCOIN_ORE_KEY = ResourceKey.create(Registries.BLOCK, DEEPSLATE_BITCOIN_ORE_ID);
	private static final ResourceKey<Block> BACKROOMS_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, BACKROOMS_BLOCK_ID);
	private static final ResourceKey<Block> BACKROOMS_LIGHTBLOCK_KEY = ResourceKey.create(Registries.BLOCK, BACKROOMS_LIGHTBLOCK_ID);
	private static final ResourceKey<Block> BACKROOMS_DOOR_KEY = ResourceKey.create(Registries.BLOCK, BACKROOMS_DOOR_ID);
	private static final ResourceKey<Block> SERVER_KEY = ResourceKey.create(Registries.BLOCK, SERVER_ID);
	private static final ResourceKey<Item> BITCOIN_ORE_ITEM_KEY = ResourceKey.create(Registries.ITEM, BITCOIN_ORE_ID);
	private static final ResourceKey<Item> DEEPSLATE_BITCOIN_ORE_ITEM_KEY = ResourceKey.create(Registries.ITEM, DEEPSLATE_BITCOIN_ORE_ID);
	private static final ResourceKey<Item> BACKROOMS_BLOCK_ITEM_KEY = ResourceKey.create(Registries.ITEM, BACKROOMS_BLOCK_ID);
	private static final ResourceKey<Item> BACKROOMS_LIGHTBLOCK_ITEM_KEY = ResourceKey.create(Registries.ITEM, BACKROOMS_LIGHTBLOCK_ID);
	private static final ResourceKey<Item> BACKROOMS_DOOR_ITEM_KEY = ResourceKey.create(Registries.ITEM, BACKROOMS_DOOR_ID);
	private static final ResourceKey<Item> SERVER_ITEM_KEY = ResourceKey.create(Registries.ITEM, SERVER_ID);

	private static final ResourceKey<CreativeModeTab> NATURAL_BLOCKS_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("minecraft", "natural_blocks")
	);
	private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("minecraft", "functional_blocks")
	);

	public static final Block BITCOIN_ORE = Registry.register(
			BuiltInRegistries.BLOCK,
			BITCOIN_ORE_ID,
			new BitcoinOreBlock(
					createNormalOreProperties(),
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/bitcoin_ore"),
					net.minecraft.world.level.block.Blocks.INFESTED_STONE
			)
	);

	public static final Block DEEPSLATE_BITCOIN_ORE = Registry.register(
			BuiltInRegistries.BLOCK,
			DEEPSLATE_BITCOIN_ORE_ID,
			new BitcoinOreBlock(
					createDeepslateOreProperties(),
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/deepslate_bitcoin_ore"),
					net.minecraft.world.level.block.Blocks.INFESTED_DEEPSLATE
			)
	);

	public static final Block SERVER = Registry.register(
			BuiltInRegistries.BLOCK,
			SERVER_ID,
			new ServerBlock(createServerProperties())
	);

	public static final Block BACKROOMS_BLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			BACKROOMS_BLOCK_ID,
			new RandomizedBackroomsBlock(
					createBackroomsBlockProperties(),
					new Identifier[] {
							Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/backrooms_block"),
							Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/backrooms_block_1"),
							Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/backrooms_block_2"),
							Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/backrooms_block_3"),
							Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/backrooms_block_4")
					},
					Blocks.STRIPPED_BIRCH_LOG,
					Blocks.STRIPPED_BIRCH_LOG
			)
	);

	public static final Block BACKROOMS_LIGHTBLOCK = Registry.register(
			BuiltInRegistries.BLOCK,
			BACKROOMS_LIGHTBLOCK_ID,
			new BackroomsLightBlock(
					createBackroomsLightBlockProperties(),
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/backrooms_lightblock"),
					Blocks.SEA_LANTERN,
					Blocks.SEA_LANTERN
			)
	);

	public static final Block BACKROOMS_DOOR = Registry.register(
			BuiltInRegistries.BLOCK,
			BACKROOMS_DOOR_ID,
			new BackroomsDoorBlock(
					net.minecraft.world.level.block.state.properties.BlockSetType.BIRCH,
					createBackroomsDoorProperties(),
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/backrooms_door"),
					Blocks.WARPED_DOOR
			)
	);

	public static final Item BITCOIN_ORE_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			BITCOIN_ORE_ID,
			new BitcoinOreBlockItem(
					BITCOIN_ORE,
					new Item.Properties().setId(BITCOIN_ORE_ITEM_KEY).useBlockDescriptionPrefix(),
					Items.RAW_GOLD_BLOCK,
					true
			)
	);

	public static final Item DEEPSLATE_BITCOIN_ORE_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			DEEPSLATE_BITCOIN_ORE_ID,
			new BitcoinOreBlockItem(
					DEEPSLATE_BITCOIN_ORE,
					new Item.Properties().setId(DEEPSLATE_BITCOIN_ORE_ITEM_KEY).useBlockDescriptionPrefix(),
					Items.RAW_GOLD_BLOCK,
					true
			)
	);

	public static final Item BACKROOMS_BLOCK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			BACKROOMS_BLOCK_ID,
			new BackroomsBlockItem(
					BACKROOMS_BLOCK,
					new Item.Properties().setId(BACKROOMS_BLOCK_ITEM_KEY).useBlockDescriptionPrefix(),
					Items.STRIPPED_BIRCH_LOG,
					true,
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms_block"),
					"Backrooms Block",
					"\u0411\u043b\u043e\u043a \u0437\u0430\u043a\u0443\u043b\u0438\u0441\u044c\u044f",
					"\u041a\u043e\u043d\u0441\u0442\u0440\u0443\u043a\u0446i\u044f \u0412\u0463\u0447\u043d\u0430\u0433\u043e \u041a\u043e\u0440\u0438\u0434\u043e\u0440\u0430",
					"\u0411\u043b\u043e\u043a \u0437\u0430\u043a\u0443\u043b\u0456\u0441\u0441\u044f",
					"\u30d0\u30c3\u30af\u30eb\u30fc\u30e0\u30ba\u30d6\u30ed\u30c3\u30af"
			)
	);

	public static final Item BACKROOMS_LIGHTBLOCK_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			BACKROOMS_LIGHTBLOCK_ID,
			new BackroomsBlockItem(
					BACKROOMS_LIGHTBLOCK,
					new Item.Properties().setId(BACKROOMS_LIGHTBLOCK_ITEM_KEY).useBlockDescriptionPrefix(),
					Items.SEA_LANTERN,
					true,
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms_lightblock"),
					"Backrooms Light",
					"\u0421\u0432\u0435\u0442 \u0437\u0430\u043a\u0443\u043b\u0438\u0441\u044c\u044f",
					"\u0421\u0432\u0463\u0442\u044a \u0412\u0463\u0447\u043d\u0430\u0433\u043e \u041a\u043e\u0440\u0438\u0434\u043e\u0440\u0430",
					"\u0421\u0432\u0456\u0442\u043b\u043e \u0437\u0430\u043a\u0443\u043b\u0456\u0441\u0441\u044f",
					"\u30d0\u30c3\u30af\u30eb\u30fc\u30e0\u30ba\u30e9\u30a4\u30c8"
			)
	);

	public static final Item BACKROOMS_DOOR_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			BACKROOMS_DOOR_ID,
			new BackroomsBlockItem(
					BACKROOMS_DOOR,
					new Item.Properties().setId(BACKROOMS_DOOR_ITEM_KEY).useBlockDescriptionPrefix(),
					Items.WARPED_DOOR,
					true,
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms_door"),
					"Backrooms Door",
					"\u0414\u0432\u0435\u0440\u044c \u0438\u0437 \u0437\u0430\u043a\u0443\u043b\u0438\u0441\u044c\u044f",
					"\u0414\u0432\u0463\u0440\u044c \u0412\u0463\u0447\u043d\u0430\u0433\u043e \u041a\u043e\u0440\u0438\u0434\u043e\u0440\u0430",
					"\u0414\u0432\u0435\u0440\u0456 \u0437\u0430\u043a\u0443\u043b\u0456\u0441\u0441\u044f",
					"\u30d0\u30c3\u30af\u30eb\u30fc\u30e0\u30ba\u306e\u6249"
			)
	);

	public static final Item SERVER_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			SERVER_ID,
			new ServerBlockItem(
					SERVER,
					new Item.Properties().setId(SERVER_ITEM_KEY).useBlockDescriptionPrefix().fireResistant(),
					Items.COMMAND_BLOCK,
					true
			)
	);

	private ModBlocks() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(NATURAL_BLOCKS_TAB).register(entries -> {
			entries.prepend(BACKROOMS_LIGHTBLOCK_ITEM);
			entries.prepend(BACKROOMS_BLOCK_ITEM);
			entries.prepend(DEEPSLATE_BITCOIN_ORE_ITEM);
			entries.prepend(BITCOIN_ORE_ITEM);
		});
		ItemGroupEvents.modifyEntriesEvent(FUNCTIONAL_BLOCKS_TAB).register(entries -> {
			entries.prepend(BACKROOMS_DOOR_ITEM);
			entries.prepend(SERVER_ITEM);
		});
	}

	private static BlockBehaviour.Properties createNormalOreProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.STONE)
				.strength(3.0f, 3.0f)
				.sound(SoundType.STONE)
				.noLootTable()
				.setId(BITCOIN_ORE_KEY);
	}

	private static BlockBehaviour.Properties createDeepslateOreProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.DEEPSLATE)
				.strength(4.5f, 3.0f)
				.sound(SoundType.DEEPSLATE)
				.noLootTable()
				.setId(DEEPSLATE_BITCOIN_ORE_KEY);
	}

	private static BlockBehaviour.Properties createServerProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.NONE)
				.strength(-1.0f, 3600000.0f)
				.noLootTable()
				.noOcclusion()
				.setId(SERVER_KEY);
	}

	public static BlockState getRandomizedBackroomsBlockState(long seed) {
		return ((RandomizedBackroomsBlock) BACKROOMS_BLOCK).getRandomizedState(seed);
	}

	private static BlockBehaviour.Properties createBackroomsBlockProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.SAND)
				.strength(30.0f, 1200.0f)
				.sound(SoundType.WOOD)
				.requiresCorrectToolForDrops()
				.noLootTable()
				.setId(BACKROOMS_BLOCK_KEY);
	}

	private static BlockBehaviour.Properties createBackroomsLightBlockProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.SAND)
				.strength(30.0f, 1200.0f)
				.sound(SoundType.GLASS)
				.lightLevel(state -> 15)
				.requiresCorrectToolForDrops()
				.noLootTable()
				.setId(BACKROOMS_LIGHTBLOCK_KEY);
	}

	private static BlockBehaviour.Properties createBackroomsDoorProperties() {
		return BlockBehaviour.Properties.of()
				.mapColor(MapColor.WOOD)
				.strength(30.0f, 1200.0f)
				.sound(SoundType.WOOD)
				.noOcclusion()
				.requiresCorrectToolForDrops()
				.ignitedByLava()
				.setId(BACKROOMS_DOOR_KEY);
	}
}
