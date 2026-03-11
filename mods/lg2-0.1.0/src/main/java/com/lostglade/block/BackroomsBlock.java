package com.lostglade.block;

import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.BlockResourceCreator;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import xyz.nucleoid.packettweaker.PacketContext;

import java.lang.reflect.Field;
import java.util.List;

public class BackroomsBlock extends SimplePolymerBlock implements PolymerTexturedBlock {
	private final BlockState polymerPackState;

	public BackroomsBlock(BlockBehaviour.Properties settings, Identifier modelId, Block preferredPolymerBlock) {
		super(settings, Blocks.STRIPPED_BIRCH_LOG);
		this.polymerPackState = requestTargetState(modelId, preferredPolymerBlock);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context)
				? this.polymerPackState
				: Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		return List.of(new ItemStack(this));
	}

	private static BlockState requestTargetState(Identifier modelId, Block preferredPolymerBlock) {
		PolymerBlockModel model = PolymerBlockModel.of(modelId);

		BlockState targetState = requestStateWithPredicate(model, preferredPolymerBlock);
		if (targetState != null) {
			return targetState;
		}

		BlockState fallback = PolymerBlockResourceUtils.requestBlock(BlockModelType.FULL_BLOCK, model);
		if (fallback == null) {
			throw new IllegalStateException("Unable to allocate polymer block state for model " + modelId);
		}
		return fallback;
	}

	private static BlockState requestStateWithPredicate(PolymerBlockModel model, Block preferredPolymerBlock) {
		try {
			Field creatorField = PolymerBlockResourceUtils.class.getDeclaredField("CREATOR");
			creatorField.setAccessible(true);
			BlockResourceCreator creator = (BlockResourceCreator) creatorField.get(null);

			return creator.requestBlock(
					BlockModelType.FULL_BLOCK,
					state -> state.is(preferredPolymerBlock),
					model
			);
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}
}
