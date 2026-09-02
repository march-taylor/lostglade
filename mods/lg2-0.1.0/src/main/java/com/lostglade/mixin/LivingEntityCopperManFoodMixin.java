package com.lostglade.mixin;

import com.lostglade.server.CopperManRepulsorSystem;
import com.lostglade.server.ServerRaceSystem;
import com.lostglade.server.OrthodoxHolinessSystem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityCopperManFoodMixin {
	private boolean lg2$pendingCopperFoodAdjustment;
	private int lg2$copperFoodLevelBeforeUse;
	private float lg2$copperSaturationBeforeUse;
	private boolean lg2$pendingCopperIngotRestore;
	private boolean lg2$pendingWomanUniqueFoodUnlock;
	private boolean lg2$pendingMarkStockFoodVoid;
	private int lg2$markFoodLevelBeforeUse;
	private float lg2$markSaturationBeforeUse;
	private boolean lg2$pendingAncientUkrPorkAdjustment;
	private int lg2$ancientUkrFoodLevelBeforeUse;
	private float lg2$ancientUkrSaturationBeforeUse;
	private int lg2$ancientUkrPorkNutrition;
	private float lg2$ancientUkrPorkSaturation;
	private ItemStack lg2$orthodoxConsumedItem = ItemStack.EMPTY;
	private int lg2$orthodoxFoodLevelBeforeUse;

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void lg2$captureCopperManFoodState(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayer player)) {
			this.lg2$pendingCopperFoodAdjustment = false;
			this.lg2$pendingWomanUniqueFoodUnlock = false;
			this.lg2$pendingMarkStockFoodVoid = false;
			return;
		}

		ItemStack useItem = self.getUseItem();
		this.lg2$orthodoxConsumedItem = useItem.copy();
		this.lg2$orthodoxFoodLevelBeforeUse = player.getFoodData().getFoodLevel();
		ServerRaceSystem.beginMilkStockEnchantedGoldenAppleEffects(player, useItem);
		this.lg2$pendingWomanUniqueFoodUnlock = !useItem.isEmpty() && useItem.get(DataComponents.FOOD) != null;
		this.lg2$pendingMarkStockFoodVoid = ServerRaceSystem.shouldVoidMarkStockFood(player, useItem);
		this.lg2$pendingAncientUkrPorkAdjustment = ServerRaceSystem.shouldAdjustAncientUkrPork(player, useItem);
		if (this.lg2$pendingAncientUkrPorkAdjustment) {
			this.lg2$ancientUkrFoodLevelBeforeUse = player.getFoodData().getFoodLevel();
			this.lg2$ancientUkrSaturationBeforeUse = player.getFoodData().getSaturationLevel();
			FoodProperties food = useItem.get(DataComponents.FOOD);
			this.lg2$ancientUkrPorkNutrition = food == null ? 0 : food.nutrition();
			this.lg2$ancientUkrPorkSaturation = food == null ? 0.0F : food.saturation();
		}
		if (this.lg2$pendingMarkStockFoodVoid) {
			this.lg2$markFoodLevelBeforeUse = player.getFoodData().getFoodLevel();
			this.lg2$markSaturationBeforeUse = player.getFoodData().getSaturationLevel();
		}
		if (!ServerRaceSystem.isCopperManStockEnabled(player)) {
			this.lg2$pendingCopperFoodAdjustment = false;
			return;
		}

		this.lg2$pendingCopperIngotRestore = useItem.is(Items.COPPER_INGOT);
		if (useItem.isEmpty() || useItem.is(Items.COPPER_INGOT) || useItem.get(DataComponents.FOOD) == null) {
			this.lg2$pendingCopperFoodAdjustment = false;
			return;
		}

		this.lg2$pendingCopperFoodAdjustment = true;
		this.lg2$copperFoodLevelBeforeUse = player.getFoodData().getFoodLevel();
		this.lg2$copperSaturationBeforeUse = player.getFoodData().getSaturationLevel();
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void lg2$recordOrthodoxConsumption(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer player && !this.lg2$orthodoxConsumedItem.isEmpty()) {
			OrthodoxHolinessSystem.onItemConsumed(player, this.lg2$orthodoxConsumedItem, this.lg2$orthodoxFoodLevelBeforeUse);
		}
		this.lg2$orthodoxConsumedItem = ItemStack.EMPTY;
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void lg2$adjustCopperManFoodRestore(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!this.lg2$pendingCopperFoodAdjustment || !(self instanceof ServerPlayer player)) {
			this.lg2$pendingCopperFoodAdjustment = false;
			return;
		}

		this.lg2$pendingCopperFoodAdjustment = false;
		ServerRaceSystem.adjustCopperManFoodAfterEating(player, this.lg2$copperFoodLevelBeforeUse, this.lg2$copperSaturationBeforeUse);
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void lg2$voidMarkStockFoodRestore(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!this.lg2$pendingMarkStockFoodVoid || !(self instanceof ServerPlayer player)) {
			this.lg2$pendingMarkStockFoodVoid = false;
			return;
		}

		this.lg2$pendingMarkStockFoodVoid = false;
		ServerRaceSystem.restoreMarkStockFoodAfterEating(player, this.lg2$markFoodLevelBeforeUse, this.lg2$markSaturationBeforeUse);
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void lg2$adjustAncientUkrPorkRestore(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!this.lg2$pendingAncientUkrPorkAdjustment || !(self instanceof ServerPlayer player)) {
			this.lg2$pendingAncientUkrPorkAdjustment = false;
			return;
		}
		this.lg2$pendingAncientUkrPorkAdjustment = false;
		ServerRaceSystem.adjustAncientUkrPorkAfterEating(player, this.lg2$ancientUkrFoodLevelBeforeUse, this.lg2$ancientUkrSaturationBeforeUse, this.lg2$ancientUkrPorkNutrition, this.lg2$ancientUkrPorkSaturation);
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void lg2$restoreRepulsorChargesAfterCopperIngot(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!this.lg2$pendingCopperIngotRestore || !(self instanceof ServerPlayer player)) {
			this.lg2$pendingCopperIngotRestore = false;
			return;
		}

		this.lg2$pendingCopperIngotRestore = false;
		CopperManRepulsorSystem.onCopperIngotConsumed(player);
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void lg2$unlockWomanUniqueHealingAfterEating(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!this.lg2$pendingWomanUniqueFoodUnlock || !(self instanceof ServerPlayer player)) {
			this.lg2$pendingWomanUniqueFoodUnlock = false;
			return;
		}

		this.lg2$pendingWomanUniqueFoodUnlock = false;
		ServerRaceSystem.onPlayerConsumedFood(player);
	}

	@Inject(method = "completeUsingItem", at = @At("TAIL"))
	private void lg2$endMilkStockEnchantedGoldenAppleEffects(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof ServerPlayer player) {
			ServerRaceSystem.endMilkStockEnchantedGoldenAppleEffects(player);
		}
	}
}
