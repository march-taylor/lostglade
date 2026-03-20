package com.lostglade.item;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import xyz.nucleoid.packettweaker.PacketContext;

public final class AbsoluteInvisibilityPotionItem extends PotionItem implements PolymerItem {
	private static final PotionContents CONTENTS = new PotionContents(Potions.LONG_INVISIBILITY);

	public AbsoluteInvisibilityPotionItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable(this.getDescriptionId());
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.POTION;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return null;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		Player player = entity instanceof Player usedByPlayer ? usedByPlayer : null;
		if (player instanceof ServerPlayer serverPlayer) {
			CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
		}

		if (!level.isClientSide()) {
			MobEffectInstance effect = ServerAbsoluteInvisibilitySystem.createEffectInstance();
			entity.addEffect(effect);
			if (player instanceof ServerPlayer serverPlayer) {
				ServerAbsoluteInvisibilitySystem.activate(serverPlayer);
			}
		}

		if (player != null) {
			player.awardStat(Stats.ITEM_USED.get(this));
		}

		if (player == null) {
			stack.consume(1, entity);
			return stack.isEmpty() ? new ItemStack(Items.GLASS_BOTTLE) : stack;
		}

		return ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE));
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		out.set(DataComponents.POTION_CONTENTS, CONTENTS);
		out.set(DataComponents.CUSTOM_NAME, Component.translatable(this.getDescriptionId()).withStyle(style -> style.withItalic(false)));
	}
}
