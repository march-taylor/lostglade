package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.nucleoid.packettweaker.PacketContext;

public final class LittleDictatorDecreeItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "little_dictator_decree");

	public LittleDictatorDecreeItem(Item.Properties settings) {
		super(settings, Items.PAPER);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			Component customName = original.get(DataComponents.CUSTOM_NAME);
			if (customName != null) {
				out.set(DataComponents.CUSTOM_NAME, customName.copy().withStyle(style -> style.withItalic(false)));
			}
		}
	}
}
