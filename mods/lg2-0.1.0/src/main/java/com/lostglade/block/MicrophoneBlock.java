package com.lostglade.block;

import com.lostglade.server.MicrophoneSystem;
import com.lostglade.server.BluetoothLinkSystem;
import eu.pb4.polymer.blocks.api.BlockModelType;
import eu.pb4.polymer.blocks.api.PolymerBlockModel;
import eu.pb4.polymer.blocks.api.PolymerBlockResourceUtils;
import eu.pb4.polymer.blocks.api.PolymerTexturedBlock;
import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

public final class MicrophoneBlock extends SimplePolymerBlock implements PolymerTexturedBlock {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath("lg2", "block/microphone");
	private final BlockState polymerState;
	private final BlockState fallbackState;

	public MicrophoneBlock(BlockBehaviour.Properties properties) {
		super(properties, Blocks.SEA_PICKLE);
		this.polymerState = requestLanternState(MODEL_ID);
		this.fallbackState = Blocks.SEA_PICKLE.defaultBlockState()
				.setValue(SeaPickleBlock.PICKLES, 1)
				.setValue(BlockStateProperties.WATERLOGGED, false);
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return this.fallbackState;
		}
		return this.polymerState;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState();
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.fallbackState.getShape(level, pos, context);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.fallbackState.getCollisionShape(level, pos, context);
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return this.fallbackState.getShape(level, pos);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		return List.of(new ItemStack(this));
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel serverLevel) {
			MicrophoneSystem.trackMicrophone(serverLevel, pos);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean notify) {
		super.neighborChanged(state, level, pos, block, orientation, notify);
		if (level instanceof ServerLevel serverLevel) {
			MicrophoneSystem.onMicrophoneStateChanged(serverLevel, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		BluetoothLinkSystem.removeBlockEndpoint(level, BluetoothLinkSystem.EndpointType.MICROPHONE, pos);
		MicrophoneSystem.untrackMicrophone(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	public static void applyFallbackName(ItemStack out, PacketContext context) {
		if (PolymerResourcePackUtils.hasMainPack(context)) {
			return;
		}
		String language = context.getPlayer() != null ? context.getPlayer().clientInformation().language() : "";
		String normalized = language == null ? "" : language.toLowerCase();
		String name = "Microphone";
		if (normalized.startsWith("rpr")) {
			name = "Микрофонъ";
		} else if (normalized.startsWith("ru")) {
			name = "Микрофон";
		} else if (normalized.startsWith("uk")) {
			name = "Мікрофон";
		} else if (normalized.startsWith("ja")) {
			name = "マイク";
		}
		out.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
	}

	private static BlockState requestLanternState(Identifier modelId) {
		BlockState state = PolymerBlockResourceUtils.requestBlock(BlockModelType.LANTERN, PolymerBlockModel.of(modelId));
		if (state == null) {
			throw new IllegalStateException("Unable to allocate lantern polymer block state for model " + modelId);
		}
		return state;
	}
}
