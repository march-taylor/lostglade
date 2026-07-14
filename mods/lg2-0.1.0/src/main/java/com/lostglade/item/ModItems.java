package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.block.CameraBlock;
import com.lostglade.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
	private static final Identifier DRONE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone");
	private static final Identifier MONITOR_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "monitor");
	private static final Identifier BLUETOOTH_ADAPTER_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bluetooth_adapter");
	private static final Identifier COPPER_JETPACK_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_jetpack");
	private static final Identifier COPPER_GOGGLES_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_goggles");
	private static final Identifier BATTLE_DONKEY_TURRET_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "battle_donkey_turret");
	private static final Identifier RAINBOW_HARNESS_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "rainbow_harness");
	private static final Identifier LITTLE_DICTATOR_DECREE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "little_dictator_decree");
	private static final Identifier WOODEN_SHIELD_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "wooden_shield");
	private static final Identifier STONE_SHIELD_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "stone_shield");
	private static final Identifier COPPER_SHIELD_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_shield");
	private static final Identifier GOLDEN_SHIELD_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "golden_shield");
	private static final Identifier DIAMOND_SHIELD_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "diamond_shield");
	private static final Identifier NETHERITE_SHIELD_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "netherite_shield");
	private static final Identifier PHOTO_PRINT_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "photo_print");
	private static final Identifier TRAVKA_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "travka");
	private static final Identifier DRIED_TRAVKA_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "dried_travka");
	private static final Identifier COCAINE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "cocaine");
	private static final Identifier METHADONE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "methadone");
	private static final Identifier TUBOCHKA_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "tubochka");
	private static final Identifier ABSOLUTE_INVISIBILITY_POTION_ID = Identifier.fromNamespaceAndPath(
			Lg2.MOD_ID,
			"absolute_invisibility_potion"
	);
	private static final Identifier STABILITY_POTION_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "stability_potion");
	private static final Identifier LONG_STABILITY_POTION_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "long_stability_potion");
	private static final Identifier GREATER_STABILITY_POTION_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "greater_stability_potion");
	private static final Identifier STARTUP_BALLOON_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "startup_balloon");
	private static final Identifier STARTUP_JACK_CLOWN_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "startup_jack_clown");
	private static final ResourceKey<Item> BITCOIN_KEY = ResourceKey.create(Registries.ITEM, BITCOIN_ID);
	private static final ResourceKey<Item> SPECIAL_PICKAXE_KEY = ResourceKey.create(Registries.ITEM, SPECIAL_PICKAXE_ID);
	private static final ResourceKey<Item> CAMERA_KEY = ResourceKey.create(Registries.ITEM, CAMERA_ID);
	private static final ResourceKey<Item> DRONE_KEY = ResourceKey.create(Registries.ITEM, DRONE_ID);
	private static final ResourceKey<Item> MONITOR_KEY = ResourceKey.create(Registries.ITEM, MONITOR_ID);
	private static final ResourceKey<Item> BLUETOOTH_ADAPTER_KEY = ResourceKey.create(Registries.ITEM, BLUETOOTH_ADAPTER_ID);
	private static final ResourceKey<Item> COPPER_JETPACK_KEY = ResourceKey.create(Registries.ITEM, COPPER_JETPACK_ID);
	private static final ResourceKey<Item> COPPER_GOGGLES_KEY = ResourceKey.create(Registries.ITEM, COPPER_GOGGLES_ID);
	private static final ResourceKey<Item> BATTLE_DONKEY_TURRET_KEY = ResourceKey.create(Registries.ITEM, BATTLE_DONKEY_TURRET_ID);
	private static final ResourceKey<Item> RAINBOW_HARNESS_KEY = ResourceKey.create(Registries.ITEM, RAINBOW_HARNESS_ID);
	private static final ResourceKey<Item> LITTLE_DICTATOR_DECREE_KEY = ResourceKey.create(Registries.ITEM, LITTLE_DICTATOR_DECREE_ID);
	private static final ResourceKey<Item> WOODEN_SHIELD_KEY = ResourceKey.create(Registries.ITEM, WOODEN_SHIELD_ID);
	private static final ResourceKey<Item> STONE_SHIELD_KEY = ResourceKey.create(Registries.ITEM, STONE_SHIELD_ID);
	private static final ResourceKey<Item> COPPER_SHIELD_KEY = ResourceKey.create(Registries.ITEM, COPPER_SHIELD_ID);
	private static final ResourceKey<Item> GOLDEN_SHIELD_KEY = ResourceKey.create(Registries.ITEM, GOLDEN_SHIELD_ID);
	private static final ResourceKey<Item> DIAMOND_SHIELD_KEY = ResourceKey.create(Registries.ITEM, DIAMOND_SHIELD_ID);
	private static final ResourceKey<Item> NETHERITE_SHIELD_KEY = ResourceKey.create(Registries.ITEM, NETHERITE_SHIELD_ID);
	private static final ResourceKey<Item> PHOTO_PRINT_KEY = ResourceKey.create(Registries.ITEM, PHOTO_PRINT_ID);
	private static final ResourceKey<Item> TRAVKA_KEY = ResourceKey.create(Registries.ITEM, TRAVKA_ID);
	private static final ResourceKey<Item> DRIED_TRAVKA_KEY = ResourceKey.create(Registries.ITEM, DRIED_TRAVKA_ID);
	private static final ResourceKey<Item> COCAINE_KEY = ResourceKey.create(Registries.ITEM, COCAINE_ID);
	private static final ResourceKey<Item> METHADONE_KEY = ResourceKey.create(Registries.ITEM, METHADONE_ID);
	private static final ResourceKey<Item> TUBOCHKA_KEY = ResourceKey.create(Registries.ITEM, TUBOCHKA_ID);
	private static final ResourceKey<Item> ABSOLUTE_INVISIBILITY_POTION_KEY = ResourceKey.create(
			Registries.ITEM,
			ABSOLUTE_INVISIBILITY_POTION_ID
	);
	private static final ResourceKey<Item> STABILITY_POTION_KEY = ResourceKey.create(Registries.ITEM, STABILITY_POTION_ID);
	private static final ResourceKey<Item> LONG_STABILITY_POTION_KEY = ResourceKey.create(Registries.ITEM, LONG_STABILITY_POTION_ID);
	private static final ResourceKey<Item> GREATER_STABILITY_POTION_KEY = ResourceKey.create(Registries.ITEM, GREATER_STABILITY_POTION_ID);
	private static final ResourceKey<Item> STARTUP_BALLOON_KEY = ResourceKey.create(Registries.ITEM, STARTUP_BALLOON_ID);
	private static final ResourceKey<Item> STARTUP_JACK_CLOWN_KEY = ResourceKey.create(Registries.ITEM, STARTUP_JACK_CLOWN_ID);
	private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("minecraft", "ingredients")
	);
	private static final ResourceKey<CreativeModeTab> COMBAT_TAB = ResourceKey.create(
			Registries.CREATIVE_MODE_TAB,
			Identifier.fromNamespaceAndPath("minecraft", "combat")
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
					(CameraBlock) ModBlocks.CAMERA,
					new Item.Properties()
							.setId(CAMERA_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item DRONE = Registry.register(
			BuiltInRegistries.ITEM,
			DRONE_ID,
			new DroneItem(
					new Item.Properties()
							.setId(DRONE_KEY)
							.stacksTo(16)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item MONITOR = Registry.register(
			BuiltInRegistries.ITEM,
			MONITOR_ID,
			new MonitorItem(
					new Item.Properties()
							.setId(MONITOR_KEY)
							.stacksTo(16)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item BLUETOOTH_ADAPTER = Registry.register(
			BuiltInRegistries.ITEM,
			BLUETOOTH_ADAPTER_ID,
			new BluetoothAdapterItem(
					new Item.Properties()
							.setId(BLUETOOTH_ADAPTER_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item COPPER_JETPACK = Registry.register(
			BuiltInRegistries.ITEM,
			COPPER_JETPACK_ID,
			new CopperJetpackItem(
					new Item.Properties()
							.setId(COPPER_JETPACK_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item COPPER_GOGGLES = Registry.register(
			BuiltInRegistries.ITEM,
			COPPER_GOGGLES_ID,
			new CopperGogglesItem(
					new Item.Properties()
							.setId(COPPER_GOGGLES_KEY)
							.stacksTo(1)
							.equippable(EquipmentSlot.HEAD)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item BATTLE_DONKEY_TURRET = Registry.register(
			BuiltInRegistries.ITEM,
			BATTLE_DONKEY_TURRET_ID,
			new BattleDonkeyTurretItem(
					new Item.Properties()
							.setId(BATTLE_DONKEY_TURRET_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item RAINBOW_HARNESS = Registry.register(
			BuiltInRegistries.ITEM,
			RAINBOW_HARNESS_ID,
			new RainbowHarnessItem(
					new Item.Properties()
							.setId(RAINBOW_HARNESS_KEY)
							.component(DataComponents.EQUIPPABLE, RainbowHarnessItem.createEquippable())
			)
	);
	public static final Item LITTLE_DICTATOR_DECREE = Registry.register(
			BuiltInRegistries.ITEM,
			LITTLE_DICTATOR_DECREE_ID,
			new LittleDictatorDecreeItem(
					new Item.Properties()
							.setId(LITTLE_DICTATOR_DECREE_KEY)
							.stacksTo(16)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item WOODEN_SHIELD = Registry.register(
			BuiltInRegistries.ITEM,
			WOODEN_SHIELD_ID,
			new MarkShieldItem(
					shieldProperties(WOODEN_SHIELD_KEY, 79),
					"wooden_shield",
					"Wooden Shield",
					"\u0414\u0435\u0440\u0435\u0432\u044f\u043d\u043d\u044b\u0439 \u0449\u0438\u0442",
					"\u0414\u0435\u0440\u0435\u0432'\u044f\u043d\u0438\u0439 \u0449\u0438\u0442",
					"\u0414\u0440\u0435\u0432\u044f\u043d\u044b\u0439 \u0449\u0438\u0442\u044a",
					"\u6728\u306e\u76fe"
			)
	);
	public static final Item STONE_SHIELD = Registry.register(
			BuiltInRegistries.ITEM,
			STONE_SHIELD_ID,
			new MarkShieldItem(
					shieldProperties(STONE_SHIELD_KEY, 176),
					"stone_shield",
					"Stone Shield",
					"\u041a\u0430\u043c\u0435\u043d\u043d\u044b\u0439 \u0449\u0438\u0442",
					"\u041a\u0430\u043c'\u044f\u043d\u0438\u0439 \u0449\u0438\u0442",
					"\u041a\u0430\u043c\u0435\u043d\u043d\u044b\u0439 \u0449\u0438\u0442\u044a",
					"\u77f3\u306e\u76fe"
			)
	);
	public static final Item COPPER_SHIELD = Registry.register(
			BuiltInRegistries.ITEM,
			COPPER_SHIELD_ID,
			new MarkShieldItem(
					shieldProperties(COPPER_SHIELD_KEY, 250),
					"copper_shield",
					"Copper Shield",
					"\u041c\u0435\u0434\u043d\u044b\u0439 \u0449\u0438\u0442",
					"\u041c\u0456\u0434\u043d\u0438\u0439 \u0449\u0438\u0442",
					"\u041c\u0463\u0434\u043d\u044b\u0439 \u0449\u0438\u0442\u044a",
					"\u9285\u306e\u76fe"
			)
	);
	public static final Item GOLDEN_SHIELD = Registry.register(
			BuiltInRegistries.ITEM,
			GOLDEN_SHIELD_ID,
			new MarkShieldItem(
					shieldProperties(GOLDEN_SHIELD_KEY, 43),
					"golden_shield",
					"Golden Shield",
					"\u0417\u043e\u043b\u043e\u0442\u043e\u0439 \u0449\u0438\u0442",
					"\u0417\u043e\u043b\u043e\u0442\u0438\u0439 \u0449\u0438\u0442",
					"\u0417\u043b\u0430\u0442\u043e\u0439 \u0449\u0438\u0442\u044a",
					"\u91d1\u306e\u76fe"
			)
	);
	public static final Item DIAMOND_SHIELD = Registry.register(
			BuiltInRegistries.ITEM,
			DIAMOND_SHIELD_ID,
			new MarkShieldItem(
					shieldProperties(DIAMOND_SHIELD_KEY, 2098),
					"diamond_shield",
					"Diamond Shield",
					"\u0410\u043b\u043c\u0430\u0437\u043d\u044b\u0439 \u0449\u0438\u0442",
					"\u0414\u0456\u0430\u043c\u0430\u043d\u0442\u043e\u0432\u0438\u0439 \u0449\u0438\u0442",
					"\u0410\u043b\u043c\u0430\u0437\u043d\u044b\u0439 \u0449\u0438\u0442\u044a",
					"\u30c0\u30a4\u30e4\u30e2\u30f3\u30c9\u306e\u76fe"
			)
	);
	public static final Item NETHERITE_SHIELD = Registry.register(
			BuiltInRegistries.ITEM,
			NETHERITE_SHIELD_ID,
			new MarkShieldItem(
					shieldProperties(NETHERITE_SHIELD_KEY, 2730),
					"netherite_shield",
					"Netherite Shield",
					"\u041d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u044b\u0439 \u0449\u0438\u0442",
					"\u041d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u0438\u0439 \u0449\u0438\u0442",
					"\u041d\u0435\u0437\u0435\u0440\u0438\u0442\u043e\u0432\u044b\u0439 \u0449\u0438\u0442\u044a",
					"\u30cd\u30b6\u30e9\u30a4\u30c8\u306e\u76fe"
			)
	);
	public static final Item PHOTO_PRINT = Registry.register(
			BuiltInRegistries.ITEM,
			PHOTO_PRINT_ID,
			new PhotoPrintItem(
					new Item.Properties()
							.setId(PHOTO_PRINT_KEY)
							.stacksTo(1)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item TRAVKA = Registry.register(
			BuiltInRegistries.ITEM,
			TRAVKA_ID,
			new TravkaItem(
					new Item.Properties()
							.setId(TRAVKA_KEY)
							.rarity(Rarity.COMMON)
			)
	);
	public static final Item DRIED_TRAVKA = Registry.register(
			BuiltInRegistries.ITEM,
			DRIED_TRAVKA_ID,
			new DriedTravkaItem(
					new Item.Properties()
							.setId(DRIED_TRAVKA_KEY)
							.rarity(Rarity.COMMON)
			)
	);
	public static final Item COCAINE = Registry.register(
			BuiltInRegistries.ITEM,
			COCAINE_ID,
			new CocaineItem(
					new Item.Properties()
							.setId(COCAINE_KEY)
							.rarity(Rarity.COMMON)
			)
	);
	public static final Item METHADONE = Registry.register(
			BuiltInRegistries.ITEM,
			METHADONE_ID,
			new MethadoneItem(
					new Item.Properties()
							.setId(METHADONE_KEY)
							.stacksTo(1)
							.rarity(Rarity.COMMON)
			)
	);
	public static final Item TUBOCHKA = Registry.register(
			BuiltInRegistries.ITEM,
			TUBOCHKA_ID,
			new TubochkaItem(
					new Item.Properties()
							.setId(TUBOCHKA_KEY)
							.stacksTo(64)
							.rarity(Rarity.COMMON)
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
	public static final Item STARTUP_BALLOON = Registry.register(
			BuiltInRegistries.ITEM,
			STARTUP_BALLOON_ID,
			new StartupBalloonItem(
					new Item.Properties()
							.setId(STARTUP_BALLOON_KEY)
							.stacksTo(64)
							.rarity(Rarity.UNCOMMON)
			)
	);
	public static final Item STARTUP_JACK_CLOWN = Registry.register(
			BuiltInRegistries.ITEM,
			STARTUP_JACK_CLOWN_ID,
			new StartupRaceVisualItem(
					new Item.Properties().setId(STARTUP_JACK_CLOWN_KEY),
					"startup_jack_clown",
					Items.CARVED_PUMPKIN
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
			entries.prepend(STARTUP_BALLOON);
			entries.prepend(GREATER_STABILITY_POTION);
			entries.prepend(LONG_STABILITY_POTION);
			entries.prepend(STABILITY_POTION);
			entries.prepend(ABSOLUTE_INVISIBILITY_POTION);
			entries.prepend(TUBOCHKA);
			entries.prepend(METHADONE);
			entries.prepend(COCAINE);
			entries.prepend(DRIED_TRAVKA);
			entries.prepend(TRAVKA);
			entries.prepend(MONITOR);
			entries.prepend(BLUETOOTH_ADAPTER);
			entries.prepend(CAMERA);
			entries.prepend(DRONE);
			entries.prepend(COPPER_GOGGLES);
			entries.prepend(RAINBOW_HARNESS);
			entries.prepend(LITTLE_DICTATOR_DECREE);
			entries.prepend(NETHERITE_SHIELD);
			entries.prepend(DIAMOND_SHIELD);
			entries.prepend(GOLDEN_SHIELD);
			entries.prepend(COPPER_SHIELD);
			entries.prepend(STONE_SHIELD);
			entries.prepend(WOODEN_SHIELD);
			entries.prepend(BITCOIN);
		});
		ItemGroupEvents.modifyEntriesEvent(COMBAT_TAB).register(entries -> entries.prepend(RAINBOW_HARNESS));
	}

	private static Item.Properties shieldProperties(ResourceKey<Item> key, int durability) {
		Item.Properties properties = new Item.Properties()
				.setId(key)
				.durability(durability);
		copyShieldComponent(properties, DataComponents.BANNER_PATTERNS);
		copyShieldComponent(properties, DataComponents.REPAIRABLE);
		copyShieldComponent(properties, DataComponents.EQUIPPABLE);
		copyShieldComponent(properties, DataComponents.BLOCKS_ATTACKS);
		copyShieldComponent(properties, DataComponents.BREAK_SOUND);
		return properties;
	}

	private static <T> void copyShieldComponent(Item.Properties properties, DataComponentType<T> type) {
		T value = Items.SHIELD.components().get(type);
		if (value != null) {
			properties.component(type, value);
		}
	}
}
