package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import xyz.nucleoid.packettweaker.PacketContext;

public final class BattleDonkeyTurretItem extends Item implements PolymerItem {
	private static final Identifier TURRET_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "battle_donkey_turret");
	private static final Identifier HELMET_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "battle_donkey_helmet");
	private static final String ROOT_TAG = "lg2_battle_donkey_turret";
	private static final String DISPLAY_ONLY_TAG = "display_only";
	private static final String HELMET_DISPLAY_TAG = "helmet";

	public BattleDonkeyTurretItem(Properties properties) {
		super(properties);
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		if (isDisplayOnly(stack) && !PolymerResourcePackUtils.hasMainPack(context)) {
			return Items.IRON_HORSE_ARMOR;
		}
		return Items.PAPER;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		return isHelmetDisplay(stack) ? HELMET_MODEL_ID : TURRET_MODEL_ID;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDisplayOnly(original)) {
			return;
		}
		out.remove(DataComponents.CUSTOM_NAME);
	}

	public static ItemStack createDisplayStack() {
		return createDisplayStack(false);
	}

	public static ItemStack createHelmetDisplayStack() {
		return createDisplayStack(true);
	}

	private static ItemStack createDisplayStack(boolean helmet) {
		ItemStack stack = new ItemStack(ModItems.BATTLE_DONKEY_TURRET);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var root = tag.getCompoundOrEmpty(ROOT_TAG);
			root.putBoolean(DISPLAY_ONLY_TAG, true);
			root.putBoolean(HELMET_DISPLAY_TAG, helmet);
			tag.put(ROOT_TAG, root);
		});
		return stack;
	}

	private static boolean isDisplayOnly(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(ROOT_TAG)
				.getBooleanOr(DISPLAY_ONLY_TAG, false);
	}

	private static boolean isHelmetDisplay(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(ROOT_TAG)
				.getBooleanOr(HELMET_DISPLAY_TAG, false);
	}
}
