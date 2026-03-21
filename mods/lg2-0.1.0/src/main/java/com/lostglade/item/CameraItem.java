package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.server.CameraCaptureSystem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import xyz.nucleoid.packettweaker.PacketContext;

public final class CameraItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "camera");

	public CameraItem(Item.Properties settings) {
		super(settings, Items.GOAT_HORN);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Camera");
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

	@Override
	public InteractionResult use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		return CameraCaptureSystem.tryCapture(serverPlayer, player.getItemInHand(hand))
				? InteractionResult.SUCCESS
				: InteractionResult.FAIL;
	}

	private static MutableComponent getLocalizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Camera");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Camera");
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal("Камѣра");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Камера");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Камера");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("カメラ");
		}
		return Component.literal("Camera");
	}
}
