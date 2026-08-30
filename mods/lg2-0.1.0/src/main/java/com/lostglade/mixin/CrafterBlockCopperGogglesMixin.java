package com.lostglade.mixin;

import com.lostglade.server.CopperManGogglesSystem;
import com.lostglade.server.ItRecipeBookSystem;
import com.lostglade.server.MarkShieldRecipeSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlockCopperGogglesMixin {
	@Inject(method = "getPotentialResults", at = @At("RETURN"), cancellable = true)
	private static void lg2$blockCopperGogglesAutocraft(
			ServerLevel level,
			CraftingInput craftingInput,
			CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir
	) {
		Optional<RecipeHolder<CraftingRecipe>> result = cir.getReturnValue();
		if (result.isPresent()
				&& (!CopperManGogglesSystem.canAutoCraft(result.get())
						|| !MarkShieldRecipeSystem.canAutoCraft(result.get())
						|| !ItRecipeBookSystem.canAutoCraft(result.get()))) {
			cir.setReturnValue(Optional.empty());
		}
	}
}
