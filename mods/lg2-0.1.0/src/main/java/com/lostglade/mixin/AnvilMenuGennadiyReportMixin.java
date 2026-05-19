package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuGennadiyReportMixin {
	@Inject(method = "createResult", at = @At("TAIL"))
	private void lg2$blockReportAnvilResult(CallbackInfo ci) {
		ItemCombinerMenuAccessor accessor = (ItemCombinerMenuAccessor) this;
		Container inputSlots = accessor.lg2$getInputSlots();
		ResultContainer resultSlots = accessor.lg2$getResultSlots();
		if (ServerRaceSystem.isGennadiyReportItem(inputSlots.getItem(0))
				|| ServerRaceSystem.isGennadiyReportItem(inputSlots.getItem(1))
				|| ServerRaceSystem.isGennadiyReportItem(resultSlots.getItem(0))) {
			resultSlots.setItem(0, ItemStack.EMPTY);
		}
	}

	@Inject(method = "setItemName", at = @At("HEAD"), cancellable = true)
	private void lg2$blockReportRename(String itemName, CallbackInfoReturnable<Boolean> cir) {
		Container inputSlots = ((ItemCombinerMenuAccessor) this).lg2$getInputSlots();
		if (ServerRaceSystem.isGennadiyReportItem(inputSlots.getItem(0))) {
			cir.setReturnValue(false);
		}
	}
}
