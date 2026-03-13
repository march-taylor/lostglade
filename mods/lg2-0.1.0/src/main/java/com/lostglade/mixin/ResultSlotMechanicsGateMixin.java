package com.lostglade.mixin;

import com.lostglade.server.ServerMechanicsGateSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class ResultSlotMechanicsGateMixin {
	@Shadow
	public abstract ItemStack getItem();

	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void lg2$blockLockedMechanicCrafts(Player player, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof ResultSlot) || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		if (!ServerMechanicsGateSystem.canTakeCraftResult(serverPlayer, this.getItem())) {
			cir.setReturnValue(false);
		}
	}
}
