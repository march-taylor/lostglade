package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.DyeColor;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Locale;

public final class MarkShieldItem extends ShieldItem implements PolymerItem {
	private final Identifier modelId;
	private final String englishName;
	private final String russianName;
	private final String ukrainianName;
	private final String archaicRussianName;
	private final String japaneseName;

	public MarkShieldItem(
			Properties properties,
			String modelPath,
			String englishName,
			String russianName,
			String ukrainianName,
			String archaicRussianName,
			String japaneseName
	) {
		super(properties);
		this.modelId = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, modelPath);
		this.englishName = englishName;
		this.russianName = russianName;
		this.ukrainianName = ukrainianName;
		this.archaicRussianName = archaicRussianName;
		this.japaneseName = japaneseName;
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable(this.getDescriptionId());
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.SHIELD;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context) || hasBannerDecoration(stack)) {
			return null;
		}
		return this.modelId;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (hasBannerDecoration(original)) {
			out.set(DataComponents.BANNER_PATTERNS, original.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
			DyeColor baseColor = original.get(DataComponents.BASE_COLOR);
			if (baseColor != null) {
				out.set(DataComponents.BASE_COLOR, baseColor);
			}
		}
		if (!PolymerResourcePackUtils.hasMainPack(context) || hasBannerDecoration(original)) {
			out.set(DataComponents.CUSTOM_NAME, localizedName(context).copy().withStyle(style -> style.withItalic(false)));
		}
	}

	private static boolean hasBannerDecoration(ItemStack stack) {
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		return !patterns.layers().isEmpty() || stack.has(DataComponents.BASE_COLOR);
	}

	private Component localizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return Component.literal(this.englishName);
		}
		String language = player.clientInformation().language().toLowerCase(Locale.ROOT);
		if (language.startsWith("ru")) {
			return Component.literal(this.russianName);
		}
		if (language.startsWith("uk")) {
			return Component.literal(this.ukrainianName);
		}
		if (language.startsWith("rpr")) {
			return Component.literal(this.archaicRussianName);
		}
		if (language.startsWith("ja")) {
			return Component.literal(this.japaneseName);
		}
		return Component.literal(this.englishName);
	}
}
