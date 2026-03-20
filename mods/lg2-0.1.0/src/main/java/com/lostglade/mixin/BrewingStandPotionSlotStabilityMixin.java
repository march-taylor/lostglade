package com.lostglade.mixin;

import com.lostglade.item.ModItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public class BrewingStandPotionSlotStabilityMixin {
	@Inject(method = "mayPlaceItem", at = @At("HEAD"), cancellable = true)
	private static void lg2$allowStabilityPotions(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (ModItems.isStabilityPotion(stack)) {
			cir.setReturnValue(true);
		}
	}
}
