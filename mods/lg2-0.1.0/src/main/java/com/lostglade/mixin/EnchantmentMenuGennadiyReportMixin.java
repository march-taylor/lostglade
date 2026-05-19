package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import java.util.Arrays;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuGennadiyReportMixin {
	@Shadow
	@Final
	private Container enchantSlots;

	@Shadow
	@Final
	public int[] costs;

	@Shadow
	@Final
	public int[] enchantClue;

	@Shadow
	@Final
	public int[] levelClue;

	@Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
	private void lg2$blockReportEnchant(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
		if (ServerRaceSystem.isGennadiyReportItem(this.enchantSlots.getItem(0))) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "slotsChanged", at = @At("TAIL"))
	private void lg2$hideReportEnchantOptions(Container container, CallbackInfo ci) {
		if (!ServerRaceSystem.isGennadiyReportItem(this.enchantSlots.getItem(0))) {
			return;
		}
		Arrays.fill(this.costs, 0);
		Arrays.fill(this.enchantClue, -1);
		Arrays.fill(this.levelClue, -1);
	}
}
