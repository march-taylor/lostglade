package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import xyz.nucleoid.packettweaker.PacketContext;

public final class CopperJetpackItem extends Item implements PolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_jetpack");
	private static final String ROOT_TAG = "lg2_copper_jetpack";
	private static final String DISPLAY_ONLY_TAG = "display_only";

	public CopperJetpackItem(Properties properties) {
		super(properties);
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		if (isDisplayOnly(stack) && !PolymerResourcePackUtils.hasMainPack(context)) {
			return Items.AIR;
		}
		return Items.PAPER;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDisplayOnly(original)) {
			return;
		}
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.CUSTOM_NAME, Component.literal("Jetpack").withStyle(style -> style.withItalic(false)));
		}
	}

	public static ItemStack createDisplayStack() {
		ItemStack stack = new ItemStack(ModItems.COPPER_JETPACK);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var root = tag.getCompoundOrEmpty(ROOT_TAG);
			root.putBoolean(DISPLAY_ONLY_TAG, true);
			tag.put(ROOT_TAG, root);
		});
		return stack;
	}

	private static boolean isDisplayOnly(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(ROOT_TAG)
				.getBooleanOr(DISPLAY_ONLY_TAG, false);
	}
}
