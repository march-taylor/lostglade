package com.lostglade.mixin;

import com.lostglade.server.OrthodoxDefenseSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityOrthodoxDefenseMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void lg2$blockOrthodoxDefensePickup(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer && OrthodoxDefenseSystem.isActive(serverPlayer)) ci.cancel();
	}
}
