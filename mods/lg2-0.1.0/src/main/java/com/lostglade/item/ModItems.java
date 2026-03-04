package com.lostglade.item;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public final class ModItems {
	private static final Identifier BITCOIN_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bitcoin");
	private static final ResourceKey<Item> BITCOIN_KEY = ResourceKey.create(Registries.ITEM, BITCOIN_ID);
	private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("minecraft", "ingredients")
	);

	public static final Item BITCOIN = Registry.register(
			BuiltInRegistries.ITEM,
			BITCOIN_ID,
			new BitcoinItem(new Item.Properties().setId(BITCOIN_KEY))
	);

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(INGREDIENTS_TAB).register(entries -> entries.prepend(BITCOIN));
	}
}
