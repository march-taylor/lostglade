package com.lostglade.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class StabilityPotionBrewing {
	private StabilityPotionBrewing() {
	}

	public static boolean isCustomIngredient(ItemStack stack) {
		return stack.is(ModItems.BITCOIN);
	}

	public static boolean hasCustomMix(ItemStack ingredient, ItemStack input) {
		return !resolveCustomMix(ingredient, input).isEmpty();
	}

	public static ItemStack resolveCustomMix(ItemStack ingredient, ItemStack input) {
		if (ingredient.isEmpty() || input.isEmpty()) {
			return ItemStack.EMPTY;
		}

		if (ingredient.is(ModItems.BITCOIN) && isAwkwardVanillaPotion(input)) {
			return new ItemStack(ModItems.STABILITY_POTION);
		}
		if (ingredient.is(Items.REDSTONE) && input.is(ModItems.STABILITY_POTION)) {
			return new ItemStack(ModItems.LONG_STABILITY_POTION);
		}
		if (ingredient.is(Items.GLOWSTONE_DUST) && input.is(ModItems.STABILITY_POTION)) {
			return new ItemStack(ModItems.GREATER_STABILITY_POTION);
		}

		return ItemStack.EMPTY;
	}

	private static boolean isAwkwardVanillaPotion(ItemStack stack) {
		if (!stack.is(Items.POTION)) {
			return false;
		}
		PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return contents.is(Potions.AWKWARD);
	}
}
