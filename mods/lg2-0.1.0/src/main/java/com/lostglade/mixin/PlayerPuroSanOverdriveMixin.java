package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerPuroSanOverdriveMixin {
	@ModifyArg(
			method = "actuallyHurt",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setHealth(F)V"),
			index = 0
	)
	private float lg2$absorbPuroSanOverdriveDamage(float newHealth) {
		Player player = (Player) (Object) this;
		float currentHealth = player.getHealth();
		float healthDamage = Math.max(0.0F, currentHealth - newHealth);
		float remainingDamage = ServerRaceSystem.absorbPuroSanOverdriveDamage(player, healthDamage);
		return currentHealth - remainingDamage;
	}

	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
	private void lg2$preventPuroSanOverdriveSprintExhaustion(float amount, CallbackInfo ci) {
		if (ServerRaceSystem.shouldPreventPuroSanOverdriveSprintExhaustion((Player) (Object) this)) {
			ci.cancel();
		}
	}
}