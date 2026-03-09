package com.lostglade.server.glitch;

import com.lostglade.config.GlitchConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public interface FurnaceSmeltGlitchHandler extends ServerGlitchHandler {
	boolean triggerOnFurnaceSmelt(
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
	);
}
