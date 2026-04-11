package com.lostglade.mixin;

import com.lostglade.item.DroneItem;
import com.lostglade.item.ModItems;
import com.lostglade.server.CopperManGogglesSystem;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
		if (CopperManGogglesSystem.canShowCraftingResult(serverPlayer, recipeHolder, result)) {
			ItemStack adjusted = lg2$applyKamikazePowerToCraftResult(craftingContainer, result);
			if (result.getCount() == adjusted.getCount() && ItemStack.isSameItemSameComponents(result, adjusted)) {
				return;
			}
			resultContainer.setItem(0, adjusted);
			menu.setRemoteSlot(0, adjusted);
			serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
					menu.containerId,
					menu.incrementStateId(),
					0,
					adjusted
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

	private static ItemStack lg2$applyKamikazePowerToCraftResult(CraftingContainer craftingContainer, ItemStack result) {
		if (craftingContainer == null || result == null || result.isEmpty() || !result.is(ModItems.DRONE_KAMIKAZE)) {
			return result;
		}
		int centerSlot = craftingContainer.getContainerSize() >= 9 ? 4 : craftingContainer.getContainerSize() / 2;
		if (centerSlot < 0 || centerSlot >= craftingContainer.getContainerSize()) {
			return result;
		}
		ItemStack center = craftingContainer.getItem(centerSlot);
		if (center == null || center.isEmpty() || !center.is(Items.TNT)) {
			return result;
		}
		int kamikazePower = net.minecraft.util.Mth.clamp(center.getCount(), 1, 3);
		ItemStack adjusted = result.copy();
		DroneItem.setKamikazePower(adjusted, kamikazePower);
		return adjusted;
	}
}
