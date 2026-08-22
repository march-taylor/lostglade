package com.lostglade.mixin;

import com.lostglade.server.OrthodoxDefenseSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerOrthodoxDefenseMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
	private void lg2$blockOrthodoxDefenseStackDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
			CallbackInfoReturnable<ItemEntity> cir) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (OrthodoxDefenseSystem.isActive(player)) cir.setReturnValue(null);
	}

	@Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
	private void lg2$blockOrthodoxDefenseSelectedDrop(boolean dropAll, CallbackInfo ci) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (OrthodoxDefenseSystem.isActive(player)) ci.cancel();
	}
}
