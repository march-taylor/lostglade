package com.lostglade.mixin;

import com.lostglade.server.CopperManRepulsorSystem;
import com.lostglade.server.ServerRaceSystem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

	@Inject(method = "completeUsingItem", at = @At("HEAD"))
	private void lg2$captureCopperManFoodState(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!(self instanceof ServerPlayer player) || !ServerRaceSystem.isCopperManStockEnabled(player)) {
			this.lg2$pendingCopperFoodAdjustment = false;
			return;
		}

		ItemStack useItem = self.getUseItem();
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
	private void lg2$restoreRepulsorChargesAfterCopperIngot(CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!this.lg2$pendingCopperIngotRestore || !(self instanceof ServerPlayer player)) {
			this.lg2$pendingCopperIngotRestore = false;
			return;
		}

		this.lg2$pendingCopperIngotRestore = false;
		CopperManRepulsorSystem.onCopperIngotConsumed(player);
	}
}
