package com.lostglade.server.glitch;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.config.GlitchConfig;
import com.lostglade.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BitcoinOvercookGlitch implements FurnaceSmeltGlitchHandler {
	private static final int RESULT_SLOT = 2;
	private static final String EXPERIENCE = "experience";
	private static final Item TECHNICAL_RESULT_ITEM = Items.BARRIER;
	private static final String FIRST_TRIGGER_FILE_NAME = "lg2-bitcoin-overcook-first-players.json";
	private static final List<Item> ITEM_POOL = collectItemPool();
	private static final Gson FIRST_TRIGGER_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, UUID> FURNACE_OWNER_BY_KEY = new HashMap<>();
	private static Set<UUID> firstTriggerPlayers;

	@Override
	public String id() {
		return "bitcoin_overcook";
	}

	@Override
	public GlitchConfig.GlitchEntry defaultEntry() {
		GlitchConfig.GlitchEntry entry = new GlitchConfig.GlitchEntry();
		entry.enabled = true;
		entry.minStabilityPercent = 0.0D;
		entry.maxStabilityPercent = 60.0D;
		entry.chancePerCheck = 0.2D;
		entry.stabilityInfluence = 1.0D;
		entry.minCooldownTicks = 0;
		entry.maxCooldownTicks = 0;
		JsonObject settings = new JsonObject();
		settings.addProperty(EXPERIENCE, 0.1D);
		entry.settings = settings;
		return entry;
	}

	@Override
	public boolean sanitizeSettings(GlitchConfig.GlitchEntry entry) {
		if (entry.settings == null) {
			entry.settings = new JsonObject();
		}

		boolean changed = false;
		changed |= GlitchSettingsHelper.sanitizeDouble(entry.settings, EXPERIENCE, 0.1D, 0.0D, 100.0D);
		changed |= entry.settings.remove("vanillaWeight") != null;
		changed |= entry.settings.remove("lg2Weight") != null;
		return changed;
	}

	@Override
	public boolean trigger(MinecraftServer server, RandomSource random, GlitchConfig.GlitchEntry entry, double stabilityPercent) {
		return false;
	}

	@Override
	public boolean triggerOnFurnaceSmelt(
			MinecraftServer server,
			RandomSource random,
			GlitchConfig.GlitchEntry entry,
			double stabilityPercent,
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			AbstractFurnaceBlockEntity furnace,
			RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
			SingleRecipeInput input
	) {
		if (!(furnace instanceof FurnaceBlockEntity) || input == null) {
			return false;
		}

		ItemStack sourceStack = input.item();
		if (sourceStack.isEmpty() || !sourceStack.is(ModItems.BITCOIN)) {
			return false;
		}

		ItemStack resultStack = furnace.getItem(RESULT_SLOT);
		if (resultStack.isEmpty()) {
			return false;
		}

		Item randomItem = selectRandomItem(random, entry);
		if (randomItem == null) {
			return false;
		}

		int resultCount = Math.max(1, Math.min(resultStack.getCount(), randomItem.getDefaultMaxStackSize()));
		furnace.setItem(RESULT_SLOT, new ItemStack(randomItem, resultCount));
		return true;
	}

	public boolean matches(AbstractFurnaceBlockEntity furnace, SingleRecipeInput input) {
		if (!(furnace instanceof FurnaceBlockEntity) || input == null) {
			return false;
		}

		ItemStack sourceStack = input.item();
		return !sourceStack.isEmpty() && sourceStack.is(ModItems.BITCOIN);
	}

	public void clearResult(AbstractFurnaceBlockEntity furnace) {
		furnace.setItem(RESULT_SLOT, ItemStack.EMPTY);
	}

	public boolean isTechnicalResult(ItemStack stack) {
		return !stack.isEmpty() && stack.is(TECHNICAL_RESULT_ITEM);
	}

	public void accumulateExperience(AbstractFurnaceBlockEntity furnace, GlitchConfig.GlitchEntry entry) {
		double experience = GlitchSettingsHelper.getDouble(entry.settings, EXPERIENCE, 0.1D);
		if (experience <= 0.0D) {
			return;
		}

		if (furnace instanceof BitcoinOvercookFurnaceAccess access) {
			access.lg2$addBitcoinOvercookExperience(experience);
		}
	}

	public static void notePotentialOwner(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (level == null || pos == null || player == null) {
			return;
		}
		if (!(level.getBlockEntity(pos) instanceof FurnaceBlockEntity)) {
			return;
		}

		FURNACE_OWNER_BY_KEY.put(getFurnaceKey(level, pos), player.getUUID());
	}

	public UUID getResponsiblePlayerId(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return null;
		}

		return FURNACE_OWNER_BY_KEY.get(getFurnaceKey(level, pos));
	}

	public boolean hasGuaranteedFirstTrigger(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return false;
		}

		return !getFirstTriggerPlayers(server).contains(playerId);
	}

	public void markGuaranteedFirstTriggerUsed(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}

		Set<UUID> players = getFirstTriggerPlayers(server);
		if (!players.add(playerId)) {
			return;
		}

		saveFirstTriggerPlayers(server, players);
	}

	public static void resetRuntimeState() {
		FURNACE_OWNER_BY_KEY.clear();
		firstTriggerPlayers = null;
	}

	private static Item selectRandomItem(RandomSource random, GlitchConfig.GlitchEntry entry) {
		if (ITEM_POOL.isEmpty()) {
			return null;
		}
		return ITEM_POOL.get(random.nextInt(ITEM_POOL.size()));
	}

	private static List<Item> collectItemPool() {
		List<Item> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR || item == TECHNICAL_RESULT_ITEM) {
				continue;
			}

			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (id != null && ("minecraft".equals(id.getNamespace()) || Lg2.MOD_ID.equals(id.getNamespace()))) {
				items.add(item);
			}
		}
		return items;
	}

	private static Set<UUID> getFirstTriggerPlayers(MinecraftServer server) {
		if (firstTriggerPlayers != null) {
			return firstTriggerPlayers;
		}

		Set<UUID> loaded = new HashSet<>();
		Path path = getFirstTriggerPath(server);
		if (!Files.exists(path)) {
			firstTriggerPlayers = loaded;
			return firstTriggerPlayers;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			FirstTriggerState state = FIRST_TRIGGER_GSON.fromJson(reader, FirstTriggerState.class);
			if (state != null && state.playerIds != null) {
				for (String rawId : state.playerIds) {
					if (rawId == null || rawId.isBlank()) {
						continue;
					}

					try {
						loaded.add(UUID.fromString(rawId));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		} catch (Exception e) {
			Lg2.LOGGER.warn("Failed to read bitcoin overcook first-trigger state from {}", path, e);
		}

		firstTriggerPlayers = loaded;
		return firstTriggerPlayers;
	}

	private static void saveFirstTriggerPlayers(MinecraftServer server, Set<UUID> playerIds) {
		Path path = getFirstTriggerPath(server);
		FirstTriggerState state = new FirstTriggerState();
		state.playerIds = new ArrayList<>(playerIds.size());
		for (UUID playerId : playerIds) {
			state.playerIds.add(playerId.toString());
		}
		state.playerIds.sort(String::compareTo);

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				FIRST_TRIGGER_GSON.toJson(state, writer);
			}
		} catch (IOException e) {
			Lg2.LOGGER.warn("Failed to save bitcoin overcook first-trigger state to {}", path, e);
		}
	}

	private static Path getFirstTriggerPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FIRST_TRIGGER_FILE_NAME);
	}

	private static String getFurnaceKey(ServerLevel level, BlockPos pos) {
		return level.dimension().toString() + ":" + pos.asLong();
	}

	private static final class FirstTriggerState {
		private List<String> playerIds = new ArrayList<>();
	}
}
