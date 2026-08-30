package com.lostglade.mixin;

import com.lostglade.server.CopperManGogglesSystem;
import com.lostglade.server.ItRecipeBookSystem;
import com.lostglade.server.MarkShieldRecipeSystem;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuCopperGogglesMixin {
	@Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
	private static void lg2$hideLockedCopperGogglesResult(
			AbstractContainerMenu menu,
			ServerLevel level,
			Player player,
			CraftingContainer craftingContainer,
			ResultContainer resultContainer,
			RecipeHolder<CraftingRecipe> recipeHolder,
			CallbackInfo ci
	) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		ItemStack result = resultContainer.getItem(0);
		if (CopperManGogglesSystem.canShowCraftingResult(serverPlayer, recipeHolder, result)
				&& MarkShieldRecipeSystem.canShowCraftingResult(serverPlayer, recipeHolder, result)
				&& ItRecipeBookSystem.canShowCraftingResult(serverPlayer, recipeHolder, result)) {
			result = MarkShieldRecipeSystem.decorateCraftingResult(serverPlayer, recipeHolder, result);
			resultContainer.setItem(0, result);
			menu.setRemoteSlot(0, result);
			serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
					menu.containerId,
					menu.incrementStateId(),
					0,
					result
			));
			return;
		}

		resultContainer.setItem(0, ItemStack.EMPTY);
		menu.setRemoteSlot(0, ItemStack.EMPTY);
		serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
				menu.containerId,
				menu.incrementStateId(),
				0,
				ItemStack.EMPTY
		));
	}
}
