package com.lostglade.block;

import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

public class BitcoinOreBlock extends SimplePolymerBlock {
	private final BlockState polymerPackState;

	public BitcoinOreBlock(BlockBehaviour.Properties settings, Identifier modelId) {
		super(settings, Blocks.RAW_GOLD_BLOCK);
		this.polymerPackState = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, PolymerBlockModel.of(modelId));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context)
				? this.polymerPackState
				: Blocks.RAW_GOLD_BLOCK.defaultBlockState();
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		ServerLevel level = builder.getLevel();
		Lg2Config.ConfigData config = Lg2Config.get();
		ItemStack tool = builder.getOptionalParameter(LootContextParams.TOOL);
		if (tool == null) {
			tool = ItemStack.EMPTY;
		}

		if (shouldDropSelf(level, tool, config)) {
			return List.of(new ItemStack(this));
		}

		int count = config.sampleDrop(level.getRandom());
		if (config.fortuneEnabled) {
			int fortuneLevel = getEnchantmentLevel(level, tool, Enchantments.FORTUNE);
			count = applyFortuneBonus(level.getRandom(), count, fortuneLevel);
		}

		if (count <= 0) {
			return List.of();
		}

		return List.of(new ItemStack(ModItems.BITCOIN, count));
	}

	@Override
	protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
		super.spawnAfterBreak(state, level, pos, tool, dropExperience);
		if (!dropExperience) {
			return;
		}

		Lg2Config.ConfigData config = Lg2Config.get();
		if (shouldDropSelf(level, tool, config)) {
			return;
		}

		int xp = config.sampleXp(level.getRandom());
		if (xp > 0) {
			this.popExperience(level, pos, xp);
		}
	}

	private static boolean shouldDropSelf(ServerLevel level, ItemStack tool, Lg2Config.ConfigData config) {
		if (!config.silkTouchEnabled || tool.isEmpty()) {
			return false;
		}
		return getEnchantmentLevel(level, tool, Enchantments.SILK_TOUCH) > 0;
	}

	private static int getEnchantmentLevel(ServerLevel level, ItemStack tool, ResourceKey<Enchantment> key) {
		Holder<Enchantment> holder = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(key);
		return EnchantmentHelper.getItemEnchantmentLevel(holder, tool);
	}

	private static int applyFortuneBonus(RandomSource random, int baseCount, int fortuneLevel) {
		if (baseCount <= 0 || fortuneLevel <= 0) {
			return baseCount;
		}

		int bonus = random.nextInt(fortuneLevel + 2) - 1;
		if (bonus < 0) {
			bonus = 0;
		}
		return baseCount * (bonus + 1);
	}
}
