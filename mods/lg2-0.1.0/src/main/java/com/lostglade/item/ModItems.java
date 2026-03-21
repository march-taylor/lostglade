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
import net.minecraft.world.item.component.TooltipDisplay;

public final class ModItems {
	private static final Identifier BITCOIN_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bitcoin");
	private static final Identifier SPECIAL_PICKAXE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "special_pickaxe");
	private static final Identifier CAMERA_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "camera");
	private static final Identifier ABSOLUTE_INVISIBILITY_POTION_ID = Identifier.fromNamespaceAndPath(
			Lg2.MOD_ID,
			"absolute_invisibility_potion"
	);
	private static final Identifier STABILITY_POTION_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "stability_potion");
	private static final Identifier LONG_STABILITY_POTION_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "long_stability_potion");
	private static final Identifier GREATER_STABILITY_POTION_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "greater_stability_potion");
	private static final ResourceKey<Item> BITCOIN_KEY = ResourceKey.create(Registries.ITEM, BITCOIN_ID);
	private static final ResourceKey<Item> SPECIAL_PICKAXE_KEY = ResourceKey.create(Registries.ITEM, SPECIAL_PICKAXE_ID);
	private static final ResourceKey<Item> CAMERA_KEY = ResourceKey.create(Registries.ITEM, CAMERA_ID);
	private static final ResourceKey<Item> ABSOLUTE_INVISIBILITY_POTION_KEY = ResourceKey.create(
			Registries.ITEM,
			ABSOLUTE_INVISIBILITY_POTION_ID
	);
	private static final ResourceKey<Item> STABILITY_POTION_KEY = ResourceKey.create(Registries.ITEM, STABILITY_POTION_ID);
	private static final ResourceKey<Item> LONG_STABILITY_POTION_KEY = ResourceKey.create(Registries.ITEM, LONG_STABILITY_POTION_ID);
	private static final ResourceKey<Item> GREATER_STABILITY_POTION_KEY = ResourceKey.create(Registries.ITEM, GREATER_STABILITY_POTION_ID);
	private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("minecraft", "ingredients")
	);
	private static final Identifier STABILITY_POTION_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "stability_potion");
	private static final int STABILITY_DURATION_TICKS = 8 * 60 * 20;
	private static final int LONG_STABILITY_DURATION_TICKS = 16 * 60 * 20;
	private static final int GREATER_STABILITY_DURATION_TICKS = 8 * 60 * 20;

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
	public static final Item CAMERA = Registry.register(
			BuiltInRegistries.ITEM,
			CAMERA_ID,
			new CameraItem(
					new Item.Properties()
							.setId(CAMERA_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
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
	public static final Item STABILITY_POTION = Registry.register(
			BuiltInRegistries.ITEM,
			STABILITY_POTION_ID,
			new StabilityPotionItem(
					new Item.Properties()
							.setId(STABILITY_POTION_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
							.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
							.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true))
							.component(DataComponents.POTION_CONTENTS, StabilityPotionItem.createPotionContents(STABILITY_DURATION_TICKS, false)),
					STABILITY_POTION_MODEL_ID,
					STABILITY_DURATION_TICKS,
					false,
					"Potion of Stability",
					"Зелье стабильности",
					"Зілля стабільності",
					"Зелье стабильности",
					"安定性のポーション"
			)
	);
	public static final Item LONG_STABILITY_POTION = Registry.register(
			BuiltInRegistries.ITEM,
			LONG_STABILITY_POTION_ID,
			new StabilityPotionItem(
					new Item.Properties()
							.setId(LONG_STABILITY_POTION_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
							.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
							.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true))
							.component(DataComponents.POTION_CONTENTS, StabilityPotionItem.createPotionContents(LONG_STABILITY_DURATION_TICKS, false)),
					STABILITY_POTION_MODEL_ID,
					LONG_STABILITY_DURATION_TICKS,
					false,
					"Potion of Stability +",
					"Зелье стабильности +",
					"Зілля стабільності +",
					"Зелье стабильности +",
					"安定性のポーション+"
			)
	);
	public static final Item GREATER_STABILITY_POTION = Registry.register(
			BuiltInRegistries.ITEM,
			GREATER_STABILITY_POTION_ID,
			new StabilityPotionItem(
					new Item.Properties()
							.setId(GREATER_STABILITY_POTION_KEY)
							.stacksTo(1)
							.rarity(Rarity.RARE)
							.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)
							.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.POTION_CONTENTS, true))
							.component(DataComponents.POTION_CONTENTS, StabilityPotionItem.createPotionContents(GREATER_STABILITY_DURATION_TICKS, true)),
					STABILITY_POTION_MODEL_ID,
					GREATER_STABILITY_DURATION_TICKS,
					true,
					"Potion of Stability II",
					"Зелье стабильности II",
					"Зілля стабільності II",
					"Зелье стабильности II",
					"安定性のポーションII"
			)
	);

	private ModItems() {
	}

	public static boolean isStabilityPotion(Item item) {
		return item == STABILITY_POTION || item == LONG_STABILITY_POTION || item == GREATER_STABILITY_POTION;
	}

	public static boolean isStabilityPotion(net.minecraft.world.item.ItemStack stack) {
		return isStabilityPotion(stack.getItem());
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(INGREDIENTS_TAB).register(entries -> {
			entries.prepend(GREATER_STABILITY_POTION);
			entries.prepend(LONG_STABILITY_POTION);
			entries.prepend(STABILITY_POTION);
			entries.prepend(ABSOLUTE_INVISIBILITY_POTION);
			entries.prepend(CAMERA);
			entries.prepend(BITCOIN);
		});
	}
}
