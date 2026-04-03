package com.lostglade.mixin;

import com.lostglade.server.CartelSecretRecipeBookSystem;
import com.lostglade.server.CopperManGogglesSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;
import java.util.List;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerCartelRecipeBookMixin {
	@ModifyVariable(method = "awardRecipes", at = @At("HEAD"), argsOnly = true)
	private Collection<RecipeHolder<?>> lg2$filterCartelSecretRecipeAwards(Collection<RecipeHolder<?>> recipes) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		recipes = CartelSecretRecipeBookSystem.filterAwardedRecipes(player, recipes);
		return CopperManGogglesSystem.filterAwardedRecipes(player, recipes);
	}

	@ModifyVariable(method = "awardRecipesByKey", at = @At("HEAD"), argsOnly = true)
	private List<ResourceKey<Recipe<?>>> lg2$filterCartelSecretRecipeAwardKeys(List<ResourceKey<Recipe<?>>> recipeKeys) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		recipeKeys = CartelSecretRecipeBookSystem.filterAwardedRecipeKeys(player, recipeKeys);
		return CopperManGogglesSystem.filterAwardedRecipeKeys(player, recipeKeys);
	}
}
