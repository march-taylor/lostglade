package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import xyz.nucleoid.packettweaker.PacketContext;

public class SpecialPickaxeItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "special_pickaxe");

	public SpecialPickaxeItem(Item.Properties settings) {
		super(settings, Items.NETHERITE_PICKAXE);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.CUSTOM_NAME, getLocalizedName(context).withStyle(style -> style.withItalic(false)));
		}
	}

	private static MutableComponent getLocalizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Special Pickaxe");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Special Pickaxe");
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rdr") || normalized.startsWith("rpr")) {
			return Component.literal("Кудѣсный молотокъ");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Особлива кирка");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Особая кирка");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("特殊なツルハシ");
		}
		return Component.literal("Special Pickaxe");
	}
}
