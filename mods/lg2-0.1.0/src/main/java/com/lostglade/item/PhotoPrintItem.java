package com.lostglade.item;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import xyz.nucleoid.packettweaker.PacketContext;

public final class PhotoPrintItem extends SimplePolymerItem {
	public PhotoPrintItem(Item.Properties settings) {
		super(settings, Items.FILLED_MAP);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Photo");
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		PhotoPrintData data = PhotoPrintData.readPhotoItem(original);
		if (data != null && data.firstMapId() >= 0) {
			out.set(DataComponents.MAP_ID, new MapId(data.firstMapId()));
		}
		Component customName = original.get(DataComponents.CUSTOM_NAME);
		if (customName != null) {
			out.set(DataComponents.CUSTOM_NAME, customName.copy().withStyle(style -> style.withItalic(false)));
		}
	}
}
