package com.lostglade.mixin;

import com.lostglade.item.StabilityPotionBrewing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PotionBrewing.class)
public class PotionBrewingStabilityMixin {
	@Inject(method = "isIngredient", at = @At("HEAD"), cancellable = true)
	private void lg2$acceptBitcoinIngredient(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (StabilityPotionBrewing.isCustomIngredient(stack)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
	private void lg2$acceptStabilityPotionMixes(ItemStack input, ItemStack ingredient, CallbackInfoReturnable<Boolean> cir) {
		if (StabilityPotionBrewing.hasCustomMix(ingredient, input)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mix", at = @At("HEAD"), cancellable = true)
	private void lg2$brewStabilityPotion(ItemStack ingredient, ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack mixed = StabilityPotionBrewing.resolveCustomMix(ingredient, input);
		if (!mixed.isEmpty()) {
			cir.setReturnValue(mixed);
		}
	}
}
