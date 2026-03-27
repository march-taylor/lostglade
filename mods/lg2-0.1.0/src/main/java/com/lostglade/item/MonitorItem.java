package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.server.MonitorScreenSystem;
import eu.pb4.polymer.core.api.item.PolymerItem;
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

public final class MonitorItem extends Item implements PolymerItem {
	private static final Identifier BASE_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "monitor");
	private static final String DISPLAY_ROOT_TAG = "lg2_monitor_display";
	private static final String DISPLAY_ONLY_TAG = "display_only";
	private static final String CONNECTION_MASK_TAG = "connection_mask";

	public MonitorItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return MonitorScreenSystem.tryPlaceScreen(context);
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		if (isDisplayOnly(stack) && !PolymerResourcePackUtils.hasMainPack(context)) {
			return Items.AIR;
		}
		return Items.GLOW_ITEM_FRAME;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		if (!isDisplayOnly(stack)) {
			return BASE_MODEL_ID;
		}
		int connectionMask = mirrorHorizontalMask(readConnectionMask(stack));
		return connectionMask == 0
				? Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "monitor_display")
				: Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "monitor_display_" + connectionMask);
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDisplayOnly(original)) {
			return;
		}
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.CUSTOM_NAME, localizedName(context).withStyle(style -> style.withItalic(false)));
		}
	}

	public static ItemStack createDisplayStack(int connectionMask) {
		ItemStack stack = new ItemStack(ModItems.MONITOR);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var displayTag = tag.getCompoundOrEmpty(DISPLAY_ROOT_TAG);
			displayTag.putBoolean(DISPLAY_ONLY_TAG, true);
			displayTag.putInt(CONNECTION_MASK_TAG, Math.max(0, connectionMask));
			tag.put(DISPLAY_ROOT_TAG, displayTag);
		});
		return stack;
	}

	private static int readConnectionMask(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return 0;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(DISPLAY_ROOT_TAG)
				.getIntOr(CONNECTION_MASK_TAG, 0) & 0xF;
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

	private static int mirrorHorizontalMask(int mask) {
		int mirrored = mask & ~(1 | 2);
		if ((mask & 1) != 0) {
			mirrored |= 2;
		}
		if ((mask & 2) != 0) {
			mirrored |= 1;
		}
		return mirrored;
	}

	private static MutableComponent localizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Monitor");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Monitor");
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal("Экранъ");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Екран");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Экран");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("モニター");
		}
		return Component.literal("Monitor");
	}
}
