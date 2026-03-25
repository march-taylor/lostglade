package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import xyz.nucleoid.packettweaker.PacketContext;

public final class DriedTravkaItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "dried_travka");
	private static final Identifier FALLBACK_MODEL_ID = Identifier.fromNamespaceAndPath("minecraft", "dead_bush");

	public DriedTravkaItem(Item.Properties settings) {
		super(settings, Items.PAPER);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Dried Travka");
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.CUSTOM_NAME, getLocalizedName(context).withStyle(style -> style.withItalic(false)));
			out.set(DataComponents.ITEM_MODEL, FALLBACK_MODEL_ID);
		}
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return InteractionResult.FAIL;
	}

	private static MutableComponent getLocalizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Dried Travka");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Dried Travka");
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal("Сушёная травка");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Сушена травка");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Сушёная травка");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("乾燥トラフカ");
		}
		return Component.literal("Dried Travka");
	}
}
