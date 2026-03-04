package com.lostglade.block;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import xyz.nucleoid.packettweaker.PacketContext;

public class BitcoinOreBlockItem extends PolymerBlockItem {
	public BitcoinOreBlockItem(Block block, Item.Properties settings, Item polymerItem, boolean polymerUseModel) {
		super(block, settings, polymerItem, polymerUseModel);
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			BlockItem blockItem = (BlockItem) original.getItem();
			out.set(
					DataComponents.CUSTOM_NAME,
					getLocalizedFallbackName(context, blockItem.getBlock()).withStyle(style -> style.withItalic(false))
			);
		}
	}

	private static MutableComponent getLocalizedFallbackName(PacketContext context, Block block) {
		boolean deepslate = block.getDescriptionId().contains("deepslate_bitcoin_ore");

		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal(deepslate ? "Deepslate Bitcoin Ore" : "Bitcoin Ore");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal(deepslate ? "Deepslate Bitcoin Ore" : "Bitcoin Ore");
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal(deepslate ? "золотыя монѣта въ черномъ камнѣ" : "золотыя монѣта въ камнѣ");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal(deepslate ? "Глибинносланцева біткоїнова руда" : "Біткоїнова руда");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal(deepslate ? "Глубинносланцевая биткоиновая руда" : "Биткоиновая руда");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal(deepslate ? "深層ビットコイン鉱石" : "ビットコイン鉱石");
		}
		return Component.literal(deepslate ? "Deepslate Bitcoin Ore" : "Bitcoin Ore");
	}
}
