package com.lostglade.server.glitch;

import com.google.gson.JsonObject;
import com.lostglade.config.GlitchConfig;
import com.lostglade.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class BitcoinOvercookGlitch implements FurnaceSmeltGlitchHandler {
	private static final int RESULT_SLOT = 2;
	private static final String EXPERIENCE = "experience";
	private static final Item TECHNICAL_RESULT_ITEM = Items.BARRIER;
	private static final List<Item> VANILLA_ITEM_POOL = collectVanillaItemPool();

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

		return GlitchSettingsHelper.sanitizeDouble(entry.settings, EXPERIENCE, 0.1D, 0.0D, 100.0D);
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
		if (!(furnace instanceof FurnaceBlockEntity) || input == null || VANILLA_ITEM_POOL.isEmpty()) {
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

		Item randomItem = VANILLA_ITEM_POOL.get(random.nextInt(VANILLA_ITEM_POOL.size()));
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

	private static List<Item> collectVanillaItemPool() {
		List<Item> items = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			if (item == Items.AIR || item == TECHNICAL_RESULT_ITEM) {
				continue;
			}

			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (id != null && "minecraft".equals(id.getNamespace())) {
				items.add(item);
			}
		}
		return items;
	}
}
