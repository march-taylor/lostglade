package com.lostglade.block;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import xyz.nucleoid.packettweaker.PacketContext;

public final class RainbowBedItem extends BedItem implements PolymerItem {
	private static final String DISPLAY_ONLY_TAG = "lg2_rainbow_bed_display_only";
	private final Identifier modelId;
	private final Identifier displayModelId;
	private final String englishName;
	private final String russianName;
	private final String rprName;
	private final String ukrainianName;
	private final String japaneseName;

	public RainbowBedItem(
			Block block,
			Item.Properties settings,
			Identifier modelId,
			Identifier displayModelId,
			String englishName,
			String russianName,
			String rprName,
			String ukrainianName,
			String japaneseName
	) {
		super(block, settings);
		this.modelId = modelId;
		this.displayModelId = displayModelId;
		this.englishName = englishName;
		this.russianName = russianName;
		this.rprName = rprName;
		this.ukrainianName = ukrainianName;
		this.japaneseName = japaneseName;
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		if (isDisplayOnly(stack) && !PolymerResourcePackUtils.hasMainPack(context)) {
			return Items.AIR;
		}
		return Items.WHITE_BED;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		return isRainbowBedDisplayOnly(stack) ? this.displayModelId : this.modelId;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context) && !isDisplayOnly(original)) {
			out.set(
					DataComponents.CUSTOM_NAME,
					getLocalizedName(context).withStyle(style -> style.withItalic(false))
			);
		}
	}

	@Override
	protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
		boolean placed = super.placeBlock(context, state);
		if (placed) {
			Level level = context.getLevel();
			BlockPos pos = context.getClickedPos();
			if (level instanceof ServerLevel serverLevel) {
				RainbowBedDisplayHelper.spawnOrUpdate(serverLevel, pos, serverLevel.getBlockState(pos));
			}
		}
		return placed;
	}

	static ItemStack createDisplayStack() {
		ItemStack stack = new ItemStack(ModBlocks.RAINBOW_BED_ITEM);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(DISPLAY_ONLY_TAG, true));
		return stack;
	}

	private static boolean isDisplayOnly(ItemStack stack) {
		return isRainbowBedDisplayOnly(stack);
	}

	private static boolean isRainbowBedDisplayOnly(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return false;
		}
		return customData.copyTag().getBooleanOr(DISPLAY_ONLY_TAG, false);
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
