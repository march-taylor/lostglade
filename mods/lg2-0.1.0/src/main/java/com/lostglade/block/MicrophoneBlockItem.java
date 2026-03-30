package com.lostglade.block;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import xyz.nucleoid.packettweaker.PacketContext;

public final class MicrophoneBlockItem extends PolymerBlockItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "microphone");

	public MicrophoneBlockItem(MicrophoneBlock block, Item.Properties settings, Item polymerItem, boolean polymerUseModel) {
		super(block, settings, polymerItem, polymerUseModel);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		MicrophoneBlock.applyFallbackName(out, context);
	}
}
