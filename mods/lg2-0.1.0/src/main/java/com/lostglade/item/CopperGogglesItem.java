package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.server.CopperManGogglesSystem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import xyz.nucleoid.packettweaker.PacketContext;

public final class CopperGogglesItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_goggles");

	public CopperGogglesItem(Item.Properties settings) {
		super(settings, Items.LEATHER_HELMET);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable(this.getDescriptionId());
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.CUSTOM_NAME, getName(original).copy().withStyle(style -> style.withItalic(false)));
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (stack.isEmpty()) {
			return InteractionResult.PASS;
		}

		ItemStack headStack = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!headStack.isEmpty()) {
			return InteractionResult.PASS;
		}

		if (!level.isClientSide()) {
			ItemStack equipped = stack.copyWithCount(1);
			player.setItemSlot(EquipmentSlot.HEAD, equipped);
			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			player.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0F, 1.0F);
			if (player instanceof ServerPlayer serverPlayer) {
				CopperManGogglesSystem.refreshVisual(serverPlayer);
			}
		}

		return InteractionResult.SUCCESS;
	}
}
