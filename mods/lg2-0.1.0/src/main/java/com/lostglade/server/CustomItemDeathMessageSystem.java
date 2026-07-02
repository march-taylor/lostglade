package com.lostglade.server;

import com.lostglade.Lg2;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class CustomItemDeathMessageSystem {
	private CustomItemDeathMessageSystem() {
	}

	public static boolean shouldSuppressWeaponName(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return itemId != null && Lg2.MOD_ID.equals(itemId.getNamespace());
	}
}
