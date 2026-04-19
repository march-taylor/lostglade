package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FoodData.class)
public abstract class FoodDataWomanUniqueMixin {
	@Redirect(
			method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V", ordinal = 0)
	)
	private void lg2$blockWomanUniqueSaturationHeal(ServerPlayer player, float amount) {
		if (!ServerRaceSystem.shouldBlockWomanUniqueNaturalHealing(player)) {
			player.heal(amount);
		}
	}

	@Redirect(
			method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V", ordinal = 1)
	)
	private void lg2$blockWomanUniqueFoodHeal(ServerPlayer player, float amount) {
		if (!ServerRaceSystem.shouldBlockWomanUniqueNaturalHealing(player)) {
			player.heal(amount);
		}
	}

	@Redirect(
			method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V", ordinal = 0)
	)
	private void lg2$blockWomanUniqueSaturationExhaustion(FoodData foodData, float amount, ServerPlayer player) {
		if (!ServerRaceSystem.shouldBlockWomanUniqueNaturalHealing(player)) {
			foodData.addExhaustion(amount);
		}
	}

	@Redirect(
			method = "tick",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V", ordinal = 1)
	)
	private void lg2$blockWomanUniqueFoodExhaustion(FoodData foodData, float amount, ServerPlayer player) {
		if (!ServerRaceSystem.shouldBlockWomanUniqueNaturalHealing(player)) {
			foodData.addExhaustion(amount);
		}
	}
}
