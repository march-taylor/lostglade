package com.lostglade.mixin;

import com.lostglade.server.CustomItemDeathMessageSystem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DamageSource.class)
public abstract class DamageSourceCustomItemDeathMessageMixin {
	@Redirect(
			method = "getLocalizedDeathMessage",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;has(Lnet/minecraft/core/component/DataComponentType;)Z")
	)
	private boolean lg2$hideLg2ItemNameInDeathMessage(ItemStack stack, DataComponentType<?> componentType) {
		return !CustomItemDeathMessageSystem.shouldSuppressWeaponName(stack) && stack.has(componentType);
	}
}
