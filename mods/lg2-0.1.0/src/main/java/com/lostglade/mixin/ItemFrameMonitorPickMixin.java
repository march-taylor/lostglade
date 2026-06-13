package com.lostglade.mixin;

import com.lostglade.item.ModItems;
import com.lostglade.server.MonitorScreenSystem;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class ItemFrameMonitorPickMixin {
	@Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
	private void lg2$returnMonitorForPickBlock(CallbackInfoReturnable<ItemStack> cir) {
		if (MonitorScreenSystem.isMonitorFrame((ItemFrame) (Object) this)) {
			cir.setReturnValue(new ItemStack(ModItems.MONITOR));
		}
	}
}
