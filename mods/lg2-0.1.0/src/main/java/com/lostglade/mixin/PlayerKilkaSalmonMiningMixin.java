package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerKilkaSalmonMiningMixin {
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void lg2$removeKilkaSalmonFloatingMiningPenalty(CallbackInfoReturnable<Float> cir) {
		Player player = (Player) (Object) this;
		if (player instanceof ServerPlayer serverPlayer
				&& ServerRaceSystem.shouldIgnoreKilkaAirborneMiningPenalty(serverPlayer)
				&& !serverPlayer.onGround()) {
			cir.setReturnValue(cir.getReturnValueF() * 5.0F);
		}
	}
}