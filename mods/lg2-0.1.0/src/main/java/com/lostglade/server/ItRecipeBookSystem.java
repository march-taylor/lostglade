package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.lostglade.item.ModItems;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps IT crafting recipes unavailable until the corresponding personal upgrade is bought. */
public final class ItRecipeBookSystem {
	private static final Map<Identifier, String> REQUIRED_UPGRADE_BY_RECIPE = new LinkedHashMap<>();
	private static final Map<Item, String> REQUIRED_UPGRADE_BY_OUTPUT = new LinkedHashMap<>();

	static {
		registerRule("camera", ModItems.CAMERA, "it_camera");
		registerRule("speaker", ModBlocks.SPEAKER_ITEM, "it_speaker");
		registerRule("monitor", ModItems.MONITOR, "it_screen");
		registerRule("microphone", ModBlocks.MICROPHONE_ITEM, "it_microphone");
		registerRule("drone", ModItems.DRONE, "it_drone_scout");
		registerRule("bluetooth_adapter", ModItems.BLUETOOTH_ADAPTER, "it_bluetooth_adapter");
	}

	private ItRecipeBookSystem() {
	}

	public static void syncPlayerRecipeBook(ServerPlayer player) {
		if (player == null) {
			return;
		}

		Collection<RecipeHolder<?>> restrictedRecipes = collectRecipeHolders(player.level().getServer());
		if (restrictedRecipes.isEmpty()) {
			return;
		}

		List<RecipeHolder<?>> available = new ArrayList<>();
		List<RecipeHolder<?>> locked = new ArrayList<>();
		for (RecipeHolder<?> holder : restrictedRecipes) {
			if (holder != null && canSeeRecipe(player, holder.id())) {
				available.add(holder);
			} else {
				locked.add(holder);
			}
		}

		if (!available.isEmpty()) {
			player.awardRecipes(available);
		}
		if (!locked.isEmpty()) {
			player.resetRecipes(locked);
		}
		player.getRecipeBook().sendInitialRecipeBook(player);
	}

	public static Collection<RecipeHolder<?>> filterAwardedRecipes(ServerPlayer player, Collection<RecipeHolder<?>> recipes) {
		if (player == null || recipes == null || recipes.isEmpty()) {
			return recipes;
		}

		List<RecipeHolder<?>> filtered = new ArrayList<>(recipes.size());
		for (RecipeHolder<?> holder : recipes) {
			if (holder == null || canSeeRecipe(player, holder.id())) {
				filtered.add(holder);
			}
		}
		return filtered;
	}

	public static List<ResourceKey<Recipe<?>>> filterAwardedRecipeKeys(ServerPlayer player, List<ResourceKey<Recipe<?>>> recipeKeys) {
		if (player == null || recipeKeys == null || recipeKeys.isEmpty()) {
			return recipeKeys;
		}

		List<ResourceKey<Recipe<?>>> filtered = new ArrayList<>(recipeKeys.size());
		for (ResourceKey<Recipe<?>> recipeKey : recipeKeys) {
			if (recipeKey == null || canSeeRecipe(player, recipeKey)) {
				filtered.add(recipeKey);
			}
		}
		return filtered;
	}

	public static boolean canShowCraftingResult(ServerPlayer player, RecipeHolder<CraftingRecipe> recipeHolder, ItemStack stack) {
		if (player == null) {
			return true;
		}
		String requirement = recipeHolder == null ? null : REQUIRED_UPGRADE_BY_RECIPE.get(recipeHolder.id().identifier());
		if (requirement == null && stack != null && !stack.isEmpty()) {
			requirement = REQUIRED_UPGRADE_BY_OUTPUT.get(stack.getItem());
		}
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	public static boolean canTakeCraftResult(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return true;
		}
		String requirement = REQUIRED_UPGRADE_BY_OUTPUT.get(stack.getItem());
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	public static boolean canAutoCraft(RecipeHolder<?> recipeHolder) {
		return recipeHolder == null || !REQUIRED_UPGRADE_BY_RECIPE.containsKey(recipeHolder.id().identifier());
	}

	private static Collection<RecipeHolder<?>> collectRecipeHolders(MinecraftServer server) {
		if (server == null) {
			return List.of();
		}

		RecipeManager recipeManager = server.getRecipeManager();
		List<RecipeHolder<?>> holders = new ArrayList<>();
		for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
			if (holder != null && REQUIRED_UPGRADE_BY_RECIPE.containsKey(holder.id().identifier())) {
				holders.add(holder);
			}
		}
		return holders;
	}

	private static boolean canSeeRecipe(ServerPlayer player, ResourceKey<Recipe<?>> recipeKey) {
		if (recipeKey == null) {
			return true;
		}
		String requirement = REQUIRED_UPGRADE_BY_RECIPE.get(recipeKey.identifier());
		return requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement);
	}

	private static void registerRule(String recipePath, Item output, String requiredUpgrade) {
		REQUIRED_UPGRADE_BY_RECIPE.put(Identifier.fromNamespaceAndPath(Lg2.MOD_ID, recipePath), requiredUpgrade);
		REQUIRED_UPGRADE_BY_OUTPUT.put(output, requiredUpgrade);
	}
}
