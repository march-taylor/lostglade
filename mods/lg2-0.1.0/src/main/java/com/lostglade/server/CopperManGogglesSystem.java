package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import com.lostglade.item.ModItems;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CopperManGogglesSystem {
	private static final String COPPER_MAN_RACE_ID = "copper_man";
	private static final Identifier HEAD_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_goggles_head");
	private static final Identifier RECIPE_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "copper_goggles");
	private static final Map<UUID, Boolean> LAST_VISUAL_STATES = new ConcurrentHashMap<>();

	private CopperManGogglesSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CopperManGogglesSystem::tickVisuals);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				server.execute(() -> {
					syncViewer(handler.player);
					refreshVisual(handler.player);
				})
		);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_VISUAL_STATES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_VISUAL_STATES.clear());
	}

	public static void syncPlayerRecipeBook(ServerPlayer player) {
		if (player == null) {
			return;
		}

		Collection<RecipeHolder<?>> holders = collectRecipeHolders(player.level().getServer());
		if (holders.isEmpty()) {
			return;
		}

		if (canSeeRecipe(player)) {
			player.awardRecipes(holders);
		} else {
			player.resetRecipes(holders);
		}
		ServerRecipeBook recipeBook = player.getRecipeBook();
		recipeBook.sendInitialRecipeBook(player);
	}

	public static Collection<RecipeHolder<?>> filterAwardedRecipes(ServerPlayer player, Collection<RecipeHolder<?>> recipes) {
		if (player == null || recipes == null || recipes.isEmpty() || canSeeRecipe(player)) {
			return recipes;
		}

		List<RecipeHolder<?>> filtered = new ArrayList<>(recipes.size());
		for (RecipeHolder<?> holder : recipes) {
			if (holder == null || !isCopperGogglesRecipe(holder.id())) {
				filtered.add(holder);
			}
		}
		return filtered;
	}

	public static List<ResourceKey<Recipe<?>>> filterAwardedRecipeKeys(ServerPlayer player, List<ResourceKey<Recipe<?>>> recipeKeys) {
		if (player == null || recipeKeys == null || recipeKeys.isEmpty() || canSeeRecipe(player)) {
			return recipeKeys;
		}

		List<ResourceKey<Recipe<?>>> filtered = new ArrayList<>(recipeKeys.size());
		for (ResourceKey<Recipe<?>> recipeKey : recipeKeys) {
			if (recipeKey == null || !isCopperGogglesRecipe(recipeKey)) {
				filtered.add(recipeKey);
			}
		}
		return filtered;
	}

	public static boolean canTakeCraftResult(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || stack.getItem() != ModItems.COPPER_GOGGLES) {
			return true;
		}
		return canSeeRecipe(player);
	}

	public static void refreshVisual(ServerPlayer player) {
		if (player == null) {
			return;
		}
		boolean shouldSpoof = shouldSpoofVisual(player);
		LAST_VISUAL_STATES.put(player.getUUID(), shouldSpoof);
		syncWearerToAllViewers(player, shouldSpoof);
	}

	public static boolean canSeeRecipe(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
		if (raceOptional.isEmpty()) {
			return false;
		}

		PlayerRaceConfig race = raceOptional.get();
		String raceId = race.id == null ? "" : race.id.trim().toLowerCase(Locale.ROOT);
		return COPPER_MAN_RACE_ID.equals(raceId) && ServerRaceSystem.hasUnlockedAbility(player, RaceAbilitySlot.SHNYAGA);
	}

	private static void tickVisuals(MinecraftServer server) {
		if (server == null) {
			return;
		}

		Set<UUID> online = ConcurrentHashMap.newKeySet();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			online.add(player.getUUID());
			boolean shouldSpoof = shouldSpoofVisual(player);
			Boolean previous = LAST_VISUAL_STATES.put(player.getUUID(), shouldSpoof);
			if (previous == null || previous.booleanValue() != shouldSpoof) {
				syncWearerToAllViewers(player, shouldSpoof);
			}
		}
		LAST_VISUAL_STATES.keySet().removeIf(uuid -> !online.contains(uuid));
	}

	private static void syncViewer(ServerPlayer viewer) {
		MinecraftServer server = viewer == null ? null : viewer.level().getServer();
		if (viewer == null || server == null) {
			return;
		}

		for (ServerPlayer wearer : server.getPlayerList().getPlayers()) {
			if (wearer == null) {
				continue;
			}
			syncWearerToViewer(wearer, viewer, shouldSpoofVisual(wearer));
		}
	}

	private static void syncWearerToAllViewers(ServerPlayer wearer, boolean spoofVisual) {
		MinecraftServer server = wearer == null ? null : wearer.level().getServer();
		if (wearer == null || server == null) {
			return;
		}

		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			syncWearerToViewer(wearer, viewer, spoofVisual);
		}
	}

	private static void syncWearerToViewer(ServerPlayer wearer, ServerPlayer viewer, boolean spoofVisual) {
		if (wearer == null || viewer == null) {
			return;
		}

		ItemStack actualHead = wearer.getItemBySlot(EquipmentSlot.HEAD).copy();
		ItemStack stack = actualHead.copy();
		if (spoofVisual && PolymerResourcePackUtils.hasMainPack(viewer) && !stack.isEmpty()) {
			stack.set(DataComponents.ITEM_MODEL, HEAD_MODEL_ID);
			preserveVisibleName(stack, actualHead);
		}
		viewer.connection.send(new ClientboundSetEquipmentPacket(
				wearer.getId(),
				List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.HEAD, stack.copy()))
		));
	}

	private static void preserveVisibleName(ItemStack visualStack, ItemStack originalStack) {
		if (visualStack == null || visualStack.isEmpty() || originalStack == null || originalStack.isEmpty()) {
			return;
		}

		Component customName = originalStack.get(DataComponents.CUSTOM_NAME);
		if (customName != null) {
			visualStack.set(DataComponents.CUSTOM_NAME, customName.copy().withStyle(style -> style.withItalic(false)));
			return;
		}

		visualStack.set(
				DataComponents.CUSTOM_NAME,
				originalStack.getItem().getName(originalStack).copy().withStyle(style -> style.withItalic(false))
		);
	}

	private static boolean shouldSpoofVisual(ServerPlayer player) {
		return player != null
				&& player.isAlive()
				&& player.getItemBySlot(EquipmentSlot.HEAD).getItem() == ModItems.COPPER_GOGGLES
				&& !ServerRaceSystem.isCopperManJetpackActive(player);
	}

	private static Collection<RecipeHolder<?>> collectRecipeHolders(MinecraftServer server) {
		if (server == null) {
			return List.of();
		}

		RecipeManager recipeManager = server.getRecipeManager();
		List<RecipeHolder<?>> holders = new ArrayList<>();
		for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
			if (holder != null && isCopperGogglesRecipe(holder.id())) {
				holders.add(holder);
			}
		}
		return holders;
	}

	private static boolean isCopperGogglesRecipe(ResourceKey<Recipe<?>> recipeKey) {
		return recipeKey != null && RECIPE_ID.equals(recipeKey.identifier());
	}
}
