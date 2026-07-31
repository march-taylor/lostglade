package com.lostglade.mixin;

import com.lostglade.server.SeasonStartSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ServerPlayer uses its three-argument drop overload for the Q-key path.  The
 * Player mixin only sees the two-argument overload, so it cannot protect the
 * recovered tutorial bitcoin on a dedicated server.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerGuidedBitcoinDropMixin {
	@Inject(
			method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void lg2$keepSecondRecoveredBitcoinInLockedSlot(
			ItemStack stack,
			boolean throwRandomly,
			boolean retainOwnership,
			CallbackInfoReturnable<ItemEntity> cir
	) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (SeasonStartSystem.shouldBlockLockedGuidedBitcoinDrop(self, stack)) {
			cir.setReturnValue(null);
		}
	}
}
