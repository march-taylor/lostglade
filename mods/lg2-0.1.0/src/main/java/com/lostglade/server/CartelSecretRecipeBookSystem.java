package com.lostglade.server;

import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CartelSecretRecipeBookSystem {
	private static final String CARTEL_RACE_ID = "mister_cartel_49";

	private CartelSecretRecipeBookSystem() {
	}

	public static void syncJoinedPlayer(ServerPlayer player) {
		syncPlayerRecipeBook(player, true);
	}

	public static void syncPlayerRecipeBook(ServerPlayer player) {
		syncPlayerRecipeBook(player, true);
	}

	public static Collection<RecipeHolder<?>> filterAwardedRecipes(ServerPlayer player, Collection<RecipeHolder<?>> recipes) {
		if (player == null || recipes == null || recipes.isEmpty() || canSeeSecretRecipes(player)) {
			return recipes;
		}

		List<RecipeHolder<?>> filtered = new ArrayList<>(recipes.size());
		for (RecipeHolder<?> holder : recipes) {
			if (holder == null || !isSecretCartelRecipe(holder.id())) {
				filtered.add(holder);
			}
		}
		return filtered;
	}

	public static List<ResourceKey<Recipe<?>>> filterAwardedRecipeKeys(ServerPlayer player, List<ResourceKey<Recipe<?>>> recipeKeys) {
		if (player == null || recipeKeys == null || recipeKeys.isEmpty() || canSeeSecretRecipes(player)) {
			return recipeKeys;
		}

		List<ResourceKey<Recipe<?>>> filtered = new ArrayList<>(recipeKeys.size());
		for (ResourceKey<Recipe<?>> recipeKey : recipeKeys) {
			if (recipeKey == null || !isSecretCartelRecipe(recipeKey)) {
				filtered.add(recipeKey);
			}
		}
		return filtered;
	}

	private static void syncPlayerRecipeBook(ServerPlayer player, boolean resendWholeBook) {
		if (player == null) {
			return;
		}

		Collection<RecipeHolder<?>> restrictedRecipes = collectSecretRecipeHolders(player.level().getServer());
		if (restrictedRecipes.isEmpty()) {
			return;
		}

		if (canSeeSecretRecipes(player)) {
			player.awardRecipes(restrictedRecipes);
		} else {
			player.resetRecipes(restrictedRecipes);
		}

		if (resendWholeBook) {
			ServerRecipeBook recipeBook = player.getRecipeBook();
			recipeBook.sendInitialRecipeBook(player);
		}
	}

	private static Collection<RecipeHolder<?>> collectSecretRecipeHolders(MinecraftServer server) {
		if (server == null) {
			return List.of();
		}

		RecipeManager recipeManager = server.getRecipeManager();
		List<RecipeHolder<?>> holders = new ArrayList<>();
		for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
			if (holder != null && isSecretCartelRecipe(holder.id())) {
				holders.add(holder);
			}
		}
		return holders;
	}

	private static boolean canSeeSecretRecipes(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
		if (raceOptional.isEmpty()) {
			return false;
		}

		PlayerRaceConfig race = raceOptional.get();
		String raceId = race.id == null ? "" : race.id.trim().toLowerCase(Locale.ROOT);
		return CARTEL_RACE_ID.equals(raceId) && ServerRaceSystem.hasUnlockedAbility(player, RaceAbilitySlot.SHNYAGA);
	}

	private static boolean isSecretCartelRecipe(ResourceKey<Recipe<?>> recipeKey) {
		if (recipeKey == null) {
			return false;
		}

		Identifier id = recipeKey.identifier();
		if (id == null || !"lg2".equals(id.getNamespace())) {
			return false;
		}

		String path = id.getPath();
		return path.endsWith("dried_travka_from_smelting")
				|| path.endsWith("dried_travka_from_smoking")
				|| path.endsWith("tubochka")
				|| path.endsWith("methadone");
	}
}
