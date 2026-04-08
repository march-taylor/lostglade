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

public final class BluetoothAdapterItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "bluetooth_adapter");

	public BluetoothAdapterItem(Item.Properties settings) {
		super(settings, Items.AMETHYST_SHARD);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.CUSTOM_NAME, localizedName(context).withStyle(style -> style.withItalic(false)));
		}
	}

	private static MutableComponent localizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Bluetooth Adapter");
		}
		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Bluetooth Adapter");
		}
		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal("Блютусный адаптеръ");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Bluetooth адаптер");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Bluetooth адаптер");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("Bluetoothアダプター");
		}
		return Component.literal("Bluetooth Adapter");
	}
}
