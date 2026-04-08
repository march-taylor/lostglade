package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.server.DroneSystem;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import xyz.nucleoid.packettweaker.PacketContext;

public final class DroneItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone");
	private static final Identifier DISPLAY_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_display");
	private static final String DISPLAY_ROOT_TAG = "lg2_drone_display";
	private static final String DISPLAY_ONLY_TAG = "display_only";

	public DroneItem(Item.Properties settings) {
		super(settings, Items.FIREWORK_ROCKET);
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		return isDisplayOnly(itemStack) ? DISPLAY_MODEL_ID : MODEL_ID;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDisplayOnly(original) || PolymerResourcePackUtils.hasMainPack(context)) {
			return;
		}
		out.set(DataComponents.CUSTOM_NAME, localizedName(context).withStyle(style -> style.withItalic(false)));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return DroneSystem.placeDrone(context);
	}

	public static ItemStack createDisplayStack() {
		ItemStack stack = new ItemStack(ModItems.DRONE);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var displayTag = tag.getCompoundOrEmpty(DISPLAY_ROOT_TAG);
			displayTag.putBoolean(DISPLAY_ONLY_TAG, true);
			tag.put(DISPLAY_ROOT_TAG, displayTag);
		});
		return stack;
	}

	private static boolean isDisplayOnly(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(DISPLAY_ROOT_TAG)
				.getBooleanOr(DISPLAY_ONLY_TAG, false);
	}

	private static MutableComponent localizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Drone");
		}
		String language = player.clientInformation().language();
		String normalized = language == null ? "" : language.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal("Дронъ");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Дрон");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Дрон");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("ドローン");
		}
		return Component.literal("Drone");
	}
}
