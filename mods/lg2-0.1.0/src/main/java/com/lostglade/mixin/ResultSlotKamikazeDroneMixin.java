package com.lostglade.mixin;

import com.lostglade.item.DroneItem;
import com.lostglade.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotKamikazeDroneMixin {
	@Shadow
	@Final
	private CraftingContainer craftSlots;

	@Inject(method = "onTake", at = @At("TAIL"))
	private void lg2$consumeExtraTntForKamikazeCraft(Player player, ItemStack crafted, CallbackInfo ci) {
		if (crafted == null || crafted.isEmpty() || !crafted.is(ModItems.DRONE_KAMIKAZE) || this.craftSlots == null) {
			return;
		}
		int extraTntToConsume = DroneItem.getKamikazePower(crafted) - 1;
		if (extraTntToConsume <= 0) {
			return;
		}

		int centerSlot = this.craftSlots.getContainerSize() >= 9 ? 4 : this.craftSlots.getContainerSize() / 2;
		if (centerSlot < 0 || centerSlot >= this.craftSlots.getContainerSize()) {
			return;
		}
		ItemStack center = this.craftSlots.getItem(centerSlot);
		if (center == null || center.isEmpty() || !center.is(Items.TNT)) {
			return;
		}

		int removed = Math.min(extraTntToConsume, center.getCount());
		if (removed <= 0) {
			return;
		}
		center.shrink(removed);
		if (center.isEmpty()) {
			this.craftSlots.setItem(centerSlot, ItemStack.EMPTY);
		}
		this.craftSlots.setChanged();
	}
}
