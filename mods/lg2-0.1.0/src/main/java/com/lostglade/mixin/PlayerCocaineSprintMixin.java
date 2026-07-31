package com.lostglade.mixin;

import com.lostglade.item.CocaineItem;
import com.lostglade.server.SeasonStartSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerCocaineSprintMixin {
	@Inject(method = "canSprint", at = @At("HEAD"), cancellable = true)
	private void lg2$allowCartelCocaineSprint(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (CocaineItem.canCartelSprintDespiteHunger(self)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "attack", at = @At("HEAD"), cancellable = true)
	private void lg2$blockPrivateIntroAttacks(Entity target, CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer serverPlayer
				&& SeasonStartSystem.shouldBlockEntityInteraction(serverPlayer, target)) {
			ci.cancel();
		}
	}

	@Inject(
			method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void lg2$keepSecondRecoveredBitcoinInLockedSlot(ItemStack stack, boolean throwRandomly, CallbackInfoReturnable<ItemEntity> cir) {
		Player self = (Player) (Object) this;
		if (self instanceof ServerPlayer serverPlayer && SeasonStartSystem.shouldBlockLockedGuidedBitcoinDrop(serverPlayer, stack)) {
			cir.setReturnValue(null);
		}
	}
}
