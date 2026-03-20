package com.lostglade.item;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumables;

public final class ModItems {
	private static final Identifier BITCOIN_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bitcoin");
	private static final Identifier SPECIAL_PICKAXE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "special_pickaxe");
	private static final Identifier ABSOLUTE_INVISIBILITY_POTION_ID = Identifier.fromNamespaceAndPath(
			Lg2.MOD_ID,
			"absolute_invisibility_potion"
	);
	private static final ResourceKey<Item> BITCOIN_KEY = ResourceKey.create(Registries.ITEM, BITCOIN_ID);
	private static final ResourceKey<Item> SPECIAL_PICKAXE_KEY = ResourceKey.create(Registries.ITEM, SPECIAL_PICKAXE_ID);
	private static final ResourceKey<Item> ABSOLUTE_INVISIBILITY_POTION_KEY = ResourceKey.create(
			Registries.ITEM,
			ABSOLUTE_INVISIBILITY_POTION_ID
	);
	private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("minecraft", "ingredients")
	);

	public static final Item BITCOIN = Registry.register(
			BuiltInRegistries.ITEM,
			BITCOIN_ID,
			new BitcoinItem(new Item.Properties().setId(BITCOIN_KEY))
	);
	public static final Item SPECIAL_PICKAXE = Registry.register(
			BuiltInRegistries.ITEM,
			SPECIAL_PICKAXE_ID,
			new SpecialPickaxeItem(
					new Item.Properties()
							.setId(SPECIAL_PICKAXE_KEY)
							.stacksTo(1)
							.pickaxe(ToolMaterial.NETHERITE, 1.0F, -2.8F)
							.fireResistant()
			)
	);
	public static final Item ABSOLUTE_INVISIBILITY_POTION = Registry.register(
			BuiltInRegistries.ITEM,
			ABSOLUTE_INVISIBILITY_POTION_ID,
			new AbsoluteInvisibilityPotionItem(
					new Item.Properties()
							.setId(ABSOLUTE_INVISIBILITY_POTION_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
							.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
							.component(DataComponents.POTION_CONTENTS, new PotionContents(Potions.LONG_INVISIBILITY))
			)
	);

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(INGREDIENTS_TAB).register(entries -> {
			entries.prepend(ABSOLUTE_INVISIBILITY_POTION);
			entries.prepend(BITCOIN);
		});
	}
}
