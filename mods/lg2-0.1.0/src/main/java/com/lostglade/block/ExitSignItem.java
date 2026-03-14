package com.lostglade.block;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.packettweaker.PacketContext;

public final class ExitSignItem extends SignItem implements PolymerItem {
	private final Item fallbackItem;
	private final String englishName;
	private final String russianName;
	private final String rprName;
	private final String ukrainianName;
	private final String japaneseName;

	public ExitSignItem(
			Block standingBlock,
			Block wallBlock,
			Item.Properties settings,
			Item fallbackItem,
			String englishName,
			String russianName,
			String rprName,
			String ukrainianName,
			String japaneseName
	) {
		super(standingBlock, wallBlock, settings);
		this.fallbackItem = fallbackItem;
		this.englishName = englishName;
		this.russianName = russianName;
		this.rprName = rprName;
		this.ukrainianName = ukrainianName;
		this.japaneseName = japaneseName;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return this.fallbackItem;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		out.set(
				DataComponents.CUSTOM_NAME,
				getLocalizedName(context).withStyle(style -> style.withItalic(false))
		);
	}

	@Override
	protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState state) {
		if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
			ExitSignBlock.applyFixedText(sign);
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
			return true;
		}
		return false;
	}

	private MutableComponent getLocalizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal(this.englishName);
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal(this.englishName);
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal(this.rprName);
		}
		if (normalized.startsWith("ru")) {
			return Component.literal(this.russianName);
		}
		if (normalized.startsWith("uk")) {
			return Component.literal(this.ukrainianName);
		}
		if (normalized.startsWith("ja")) {
			return Component.literal(this.japaneseName);
		}
		return Component.literal(this.englishName);
	}
}
