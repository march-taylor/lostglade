package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import com.lostglade.item.ModItems;
import com.lostglade.item.MarkShieldItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MarkShieldRecipeSystem {
	private static final String MARK_RACE_ID = "mark_potroshitel";
	private static final String ERA_STONE = "era_stone";
	private static final String ERA_COPPER = "era_copper";
	private static final String ERA_IRON_GOLD = "era_iron_gold";
	private static final String ERA_DIAMOND = "era_diamond";
	private static final String ERA_NETHERITE = "era_netherite";

	private static final Identifier VANILLA_IRON_SHIELD_RECIPE_ID = Identifier.fromNamespaceAndPath("minecraft", "shield");
	private static final Identifier MARK_IRON_SHIELD_RECIPE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "iron_shield");
	private static final Identifier MARK_SHIELD_DECORATION_RECIPE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "mark_shield_decoration");
	private static final String MARK_CRAFTED_IRON_SHIELD_TRANSLATION_KEY = "item.lg2.iron_shield";
	private static final Component MARK_CRAFTED_IRON_SHIELD_NAME = Component
			.translatableWithFallback(MARK_CRAFTED_IRON_SHIELD_TRANSLATION_KEY, "Железный щит")
			.withStyle(style -> style.withItalic(false));
	private static final Map<Identifier, ShieldRecipeRule> RULES_BY_RECIPE_ID = new LinkedHashMap<>();
	private static final Map<Item, ShieldRecipeRule> RULES_BY_OUTPUT_ITEM = new LinkedHashMap<>();

	static {
		registerRule(Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "wooden_shield"), ModItems.WOODEN_SHIELD, null, false);
		registerRule(VANILLA_IRON_SHIELD_RECIPE_ID, Items.SHIELD, ERA_IRON_GOLD, true, false);
		registerRule(MARK_IRON_SHIELD_RECIPE_ID, Items.SHIELD, ERA_IRON_GOLD, true, true);
		registerRule(Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "stone_shield"), ModItems.STONE_SHIELD, ERA_STONE, false);
		registerRule(Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_shield"), ModItems.COPPER_SHIELD, ERA_COPPER, false);
		registerRule(Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "golden_shield"), ModItems.GOLDEN_SHIELD, ERA_IRON_GOLD, false);
		registerRule(Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "diamond_shield"), ModItems.DIAMOND_SHIELD, ERA_DIAMOND, false);
		registerRule(Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "netherite_shield"), ModItems.NETHERITE_SHIELD, ERA_NETHERITE, false);
	}

	private MarkShieldRecipeSystem() {
	}

	public static void syncJoinedPlayer(ServerPlayer player) {
		syncPlayerRecipeBook(player, true);
	}

	public static void syncPlayerRecipeBook(ServerPlayer player) {
		syncPlayerRecipeBook(player, true);
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

	public static boolean canTakeCraftResult(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return true;
		}
		ShieldRecipeRule rule = RULES_BY_OUTPUT_ITEM.get(stack.getItem());
		if (rule == null) {
			return true;
		}
		if (isDecoratedMarkShield(stack)) {
			return true;
		}
		return canCraftRule(player, rule);
	}

	public static boolean canShowCraftingResult(ServerPlayer player, RecipeHolder<CraftingRecipe> recipeHolder, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return true;
		}
		ShieldRecipeRule recipeRule = recipeHolder == null ? null : RULES_BY_RECIPE_ID.get(recipeHolder.id().identifier());
		if (isDecoratedMarkShield(stack)) {
			return true;
		}
		if (recipeRule != null) {
			return canCraftRule(player, recipeRule);
		}
		ShieldRecipeRule outputRule = RULES_BY_OUTPUT_ITEM.get(stack.getItem());
		return outputRule == null || canCraftRule(player, outputRule);
	}

	public static ItemStack decorateCraftingResult(ServerPlayer player, RecipeHolder<CraftingRecipe> recipeHolder, ItemStack stack) {
		if (player == null || recipeHolder == null || stack == null || stack.isEmpty()) {
			return stack;
		}
		ShieldRecipeRule rule = RULES_BY_RECIPE_ID.get(recipeHolder.id().identifier());
		if (rule == null || !rule.vanillaIronShield() || !isMarkPlayer(player) || !stack.is(Items.SHIELD)) {
			return stack;
		}

		ItemStack result = stack.copy();
		result.set(DataComponents.CUSTOM_NAME, MARK_CRAFTED_IRON_SHIELD_NAME);
		return result;
	}

	public static boolean canAutoCraft(RecipeHolder<?> recipeHolder) {
		return recipeHolder == null || !RULES_BY_RECIPE_ID.containsKey(recipeHolder.id().identifier());
	}

	private static void syncPlayerRecipeBook(ServerPlayer player, boolean resendWholeBook) {
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

		if (resendWholeBook) {
			ServerRecipeBook recipeBook = player.getRecipeBook();
			recipeBook.sendInitialRecipeBook(player);
		}
	}

	private static Collection<RecipeHolder<?>> collectRecipeHolders(MinecraftServer server) {
		if (server == null) {
			return List.of();
		}

		RecipeManager recipeManager = server.getRecipeManager();
		List<RecipeHolder<?>> holders = new ArrayList<>();
		for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
			if (holder != null && RULES_BY_RECIPE_ID.containsKey(holder.id().identifier())) {
				holders.add(holder);
			}
		}
		return holders;
	}

	private static boolean canSeeRecipe(ServerPlayer player, ResourceKey<Recipe<?>> recipeKey) {
		return recipeKey == null || canSeeRecipe(player, recipeKey.identifier());
	}

	private static boolean canSeeRecipe(ServerPlayer player, Identifier recipeId) {
		ShieldRecipeRule rule = RULES_BY_RECIPE_ID.get(recipeId);
		if (rule == null) {
			return true;
		}
		if (rule.markOnlyIronShield()) {
			return isMarkPlayer(player) && hasMarkShnyaga(player);
		}
		if (rule.vanillaIronShield()) {
			return !isMarkPlayer(player) && hasEra(player, ERA_IRON_GOLD);
		}
		return hasMarkShnyaga(player);
	}

	private static boolean canCraftRule(ServerPlayer player, ShieldRecipeRule rule) {
		if (player == null || rule == null) {
			return false;
		}
		if (rule.vanillaIronShield()) {
			if (isMarkPlayer(player) && !hasMarkShnyaga(player)) {
				return false;
			}
			return hasEra(player, rule.requiredEraId());
		}
		return hasMarkShnyaga(player) && hasEra(player, rule.requiredEraId());
	}

	private static boolean hasMarkShnyaga(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
		if (raceOptional.isEmpty()) {
			return false;
		}

		PlayerRaceConfig race = raceOptional.get();
		return isMarkRace(race)
				&& race.shnyaga != null
				&& race.shnyaga.enabled
				&& ServerRaceSystem.hasUnlockedAbility(player, RaceAbilitySlot.SHNYAGA);
	}

	private static boolean isMarkPlayer(ServerPlayer player) {
		return player != null && ServerRaceSystem.getRace(player).map(MarkShieldRecipeSystem::isMarkRace).orElse(false);
	}

	private static boolean isMarkRace(PlayerRaceConfig race) {
		String raceId = race == null || race.id == null ? "" : race.id.trim().toLowerCase(Locale.ROOT);
		return MARK_RACE_ID.equals(raceId);
	}

	private static boolean hasEra(ServerPlayer player, String requiredEraId) {
		return requiredEraId == null || requiredEraId.isBlank() || ServerUpgradeUiSystem.hasUpgrade(player, requiredEraId);
	}

	private static boolean isDecoratedMarkShield(ItemStack stack) {
		if (stack == null || !(stack.getItem() instanceof MarkShieldItem)) {
			return false;
		}
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		return !patterns.layers().isEmpty() || stack.has(DataComponents.BASE_COLOR);
	}

	private static void registerRule(Identifier recipeId, Item outputItem, String requiredEraId, boolean vanillaIronShield) {
		registerRule(recipeId, outputItem, requiredEraId, vanillaIronShield, false);
	}

	private static void registerRule(Identifier recipeId, Item outputItem, String requiredEraId, boolean vanillaIronShield, boolean markOnlyIronShield) {
		ShieldRecipeRule rule = new ShieldRecipeRule(recipeId, outputItem, requiredEraId, vanillaIronShield, markOnlyIronShield);
		RULES_BY_RECIPE_ID.put(recipeId, rule);
		RULES_BY_OUTPUT_ITEM.put(outputItem, rule);
	}

	private record ShieldRecipeRule(Identifier recipeId, Item outputItem, String requiredEraId, boolean vanillaIronShield, boolean markOnlyIronShield) {
	}
}
