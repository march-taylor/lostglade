package com.lostglade.mixin;

import com.lostglade.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(AbstractCookingRecipe.class)
public abstract class AbstractCookingRecipeMixin {
	private static final String LG2_BITCOIN_OVERCOOK_GROUP = "lg2_bitcoin_overcook";

	@Inject(method = "display", at = @At("HEAD"), cancellable = true)
	private void lg2$overrideBitcoinOvercookDisplay(CallbackInfoReturnable<List<RecipeDisplay>> cir) {
		AbstractCookingRecipe self = (AbstractCookingRecipe) (Object) this;
		if (!LG2_BITCOIN_OVERCOOK_GROUP.equals(self.group())) {
			return;
		}

		cir.setReturnValue(List.of(
				new FurnaceRecipeDisplay(
						self.input().display(),
						SlotDisplay.AnyFuel.INSTANCE,
						new SlotDisplay.ItemStackSlotDisplay(new ItemStack(ModItems.BITCOIN)),
						new SlotDisplay.ItemSlotDisplay(Items.FURNACE),
						self.cookingTime(),
						self.experience()
				)
		));
	}
}
