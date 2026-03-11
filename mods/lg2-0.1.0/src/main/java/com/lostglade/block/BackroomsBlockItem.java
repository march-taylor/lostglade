package com.lostglade.block;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import xyz.nucleoid.packettweaker.PacketContext;

public class BackroomsBlockItem extends PolymerBlockItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "backrooms_block");

	public BackroomsBlockItem(Block block, Item.Properties settings, Item polymerItem, boolean polymerUseModel) {
		super(block, settings, polymerItem, polymerUseModel);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(
					DataComponents.CUSTOM_NAME,
					getLocalizedFallbackName(context).withStyle(style -> style.withItalic(false))
			);
		}
	}

	private static MutableComponent getLocalizedFallbackName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Backrooms Block");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Backrooms Block");
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("ru") || normalized.startsWith("uk")) {
			return Component.literal("Блок закулисья");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("バックルームズブロック");
		}
		return Component.literal("Backrooms Block");
	}
}
