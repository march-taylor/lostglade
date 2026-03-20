package com.lostglade.mixin;

import com.lostglade.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityStabilityMixin {
	@Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
	private void lg2$allowStabilityPotionInputs(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (slot >= 0 && slot < 3 && ModItems.isStabilityPotion(stack)) {
			BrewingStandBlockEntity self = (BrewingStandBlockEntity) (Object) this;
			cir.setReturnValue(self.getItem(slot).isEmpty());
		}
	}
}
