package com.lostglade.item;

import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class ModRecipeSerializers {
	public static final RecipeSerializer<MarkShieldDecorationRecipe> MARK_SHIELD_DECORATION = RecipeSerializer.register(
			"lg2:crafting_special_mark_shield_decoration",
			new CustomRecipe.Serializer<>(MarkShieldDecorationRecipe::new)
	);

	private ModRecipeSerializers() {
	}

	public static void register() {
		// Static initializer registers serializers in the vanilla registry.
	}
}
