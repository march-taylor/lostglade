package com.lostglade.mixin;

import com.lostglade.item.MarkShieldItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShieldDecorationRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShieldDecorationRecipe.class)
public abstract class ShieldDecorationRecipeMarkShieldMixin {
	@Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$matchMarkShield(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
		if (!hasMarkShield(input)) {
			return;
		}
		cir.setReturnValue(matchesMarkShieldDecoration(input));
	}

	@Inject(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
	private void lg2$assembleMarkShield(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
		if (!hasMarkShield(input)) {
			return;
		}

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
			cir.setReturnValue(ItemStack.EMPTY);
			return;
		}

		shield.set(DataComponents.BANNER_PATTERNS, banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
		shield.set(DataComponents.BASE_COLOR, bannerItem.getColor());
		cir.setReturnValue(shield);
	}

	private static boolean matchesMarkShieldDecoration(CraftingInput input) {
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
			} else if (stack.getItem() instanceof MarkShieldItem) {
				if (hasShield || hasBannerPatterns(stack)) {
					return false;
				}
				hasShield = true;
			} else {
				return false;
			}
		}
		return hasBanner && hasShield;
	}

	private static boolean hasMarkShield(CraftingInput input) {
		for (int i = 0; i < input.size(); i++) {
			if (input.getItem(i).getItem() instanceof MarkShieldItem) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasBannerPatterns(ItemStack stack) {
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		return !patterns.layers().isEmpty();
	}
}
