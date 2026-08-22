package com.lostglade.mixin;

import com.lostglade.server.OrthodoxDefenseSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbOrthodoxDefenseMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void lg2$blockOrthodoxDefenseExperiencePickup(Player player, CallbackInfo ci) {
		if (player instanceof ServerPlayer serverPlayer && OrthodoxDefenseSystem.isActive(serverPlayer)) ci.cancel();
	}
}
