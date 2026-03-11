package com.lostglade.block;

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
	private final Identifier modelId;
	private final String englishName;
	private final String slavicName;
	private final String japaneseName;

	public BackroomsBlockItem(
			Block block,
			Item.Properties settings,
			Item polymerItem,
			boolean polymerUseModel,
			Identifier modelId,
			String englishName,
			String slavicName,
			String japaneseName
	) {
		super(block, settings, polymerItem, polymerUseModel);
		this.modelId = modelId;
		this.englishName = englishName;
		this.slavicName = slavicName;
		this.japaneseName = japaneseName;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? this.modelId : null;
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

	private MutableComponent getLocalizedFallbackName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal(this.englishName);
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal(this.englishName);
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("ru") || normalized.startsWith("uk")) {
			return Component.literal(this.slavicName);
		}
		if (normalized.startsWith("ja")) {
			return Component.literal(this.japaneseName);
		}
		return Component.literal(this.englishName);
	}
}
