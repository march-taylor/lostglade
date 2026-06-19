package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperLittleDictatorPropagandaMixin {
	@Inject(method = "getItemEnchantmentLevel", at = @At("RETURN"), cancellable = true)
	private static void lg2$addLittleDictatorPropagandaBonus(
			Holder<Enchantment> enchantment,
			ItemStack stack,
			CallbackInfoReturnable<Integer> cir
	) {
		int bonus = ServerRaceSystem.getLittleDictatorPropagandaEnchantmentBonus(enchantment);
		if (bonus > 0) {
			cir.setReturnValue(cir.getReturnValueI() + bonus);
		}
	}
}
