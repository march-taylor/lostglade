package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.nucleoid.packettweaker.PacketContext;

/** A resource-pack visual used only by a server-side display entity. */
public final class StartupRaceVisualItem extends SimplePolymerItem {
	private final Identifier modelId;

	public StartupRaceVisualItem(Item.Properties properties, String modelPath, Item fallbackItem) {
		super(properties, fallbackItem);
		this.modelId = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, modelPath);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? modelId : null;
	}
}
