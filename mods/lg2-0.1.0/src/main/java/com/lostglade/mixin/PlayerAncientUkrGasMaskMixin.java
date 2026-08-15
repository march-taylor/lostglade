package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerAncientUkrGasMaskMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void lg2$blockAncientUkrGasMaskDrop(ItemStack stack, boolean includeThrowerName, CallbackInfoReturnable<ItemEntity> cir) {
		if (ServerRaceSystem.shouldBlockAncientUkrGasMaskDrop(stack)) {
			cir.setReturnValue(null);
		}
	}
}