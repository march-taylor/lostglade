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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
	private static final Identifier BITCOIN_ORE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bitcoin_ore");
	private static final Identifier DEEPSLATE_BITCOIN_ORE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "deepslate_bitcoin_ore");
	private static final Identifier SERVER_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "server");

	private static final ResourceKey<Block> BITCOIN_ORE_KEY = ResourceKey.create(Registries.BLOCK, BITCOIN_ORE_ID);
	private static final ResourceKey<Block> DEEPSLATE_BITCOIN_ORE_KEY = ResourceKey.create(Registries.BLOCK, DEEPSLATE_BITCOIN_ORE_ID);
	private static final ResourceKey<Block> SERVER_KEY = ResourceKey.create(Registries.BLOCK, SERVER_ID);
	private static final ResourceKey<Item> BITCOIN_ORE_ITEM_KEY = ResourceKey.create(Registries.ITEM, BITCOIN_ORE_ID);
	private static final ResourceKey<Item> DEEPSLATE_BITCOIN_ORE_ITEM_KEY = ResourceKey.create(Registries.ITEM, DEEPSLATE_BITCOIN_ORE_ID);
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
			entries.prepend(DEEPSLATE_BITCOIN_ORE_ITEM);
			entries.prepend(BITCOIN_ORE_ITEM);
		});
		ItemGroupEvents.modifyEntriesEvent(FUNCTIONAL_BLOCKS_TAB).register(entries -> entries.prepend(SERVER_ITEM));
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
}
