package com.lostglade.item;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

public final class MarkShieldDecorationRecipe extends CustomRecipe {
	public MarkShieldDecorationRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (input.ingredientCount() != 2) {
			return false;
		}

		boolean hasBanner = false;
		boolean hasShield = false;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}

			if (stack.getItem() instanceof BannerItem) {
				if (hasBanner) {
					return false;
				}
				hasBanner = true;
				continue;
			}

			if (stack.getItem() instanceof MarkShieldItem) {
				if (hasShield || hasBannerPatterns(stack)) {
					return false;
				}
				hasShield = true;
				continue;
			}

			return false;
		}
		return hasBanner && hasShield;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		ItemStack banner = ItemStack.EMPTY;
		ItemStack shield = ItemStack.EMPTY;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.getItem() instanceof BannerItem) {
				banner = stack;
			} else if (stack.getItem() instanceof MarkShieldItem) {
				shield = stack.copy();
			}
		}

		if (banner.isEmpty() || shield.isEmpty() || !(banner.getItem() instanceof BannerItem bannerItem)) {
			return ItemStack.EMPTY;
		}

		shield.set(DataComponents.BANNER_PATTERNS, banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
		shield.set(DataComponents.BASE_COLOR, bannerItem.getColor());
		return shield;
	}

	@Override
	public RecipeSerializer<? extends CustomRecipe> getSerializer() {
		return ModRecipeSerializers.MARK_SHIELD_DECORATION;
	}

	private static boolean hasBannerPatterns(ItemStack stack) {
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		return !patterns.layers().isEmpty();
	}
}
