package com.lostglade.block;

import com.lostglade.Lg2;
import com.lostglade.server.MonitorScreenSystem;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.nucleoid.packettweaker.PacketContext;

public final class CameraBlock extends SimplePolymerBlock implements PolymerTexturedBlock {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "block/camera");
	private final BlockState polymerState;
	private final BlockState fallbackState;

	public CameraBlock(BlockBehaviour.Properties properties) {
		super(properties, Blocks.PLAYER_HEAD);
		this.polymerState = requestHeadState(MODEL_ID);
		this.fallbackState = Blocks.PLAYER_HEAD.defaultBlockState();
		this.registerDefaultState(this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING)));
	}

	@Override
	public BlockState getPolymerBlockState(BlockState state, PacketContext context) {
		int rotation = rotationFromFacing(state.getValue(HorizontalDirectionalBlock.FACING));
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return this.fallbackState.setValue(SkullBlock.ROTATION, rotation);
		}
		return this.polymerState.setValue(SkullBlock.ROTATION, rotation);
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
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			MonitorScreenSystem.onCameraNetworkChanged(serverLevel, pos);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, Orientation orientation, boolean notify) {
		super.neighborChanged(state, level, pos, block, orientation, notify);
		if (level instanceof ServerLevel serverLevel) {
			MonitorScreenSystem.onCameraNetworkChanged(serverLevel, pos);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		MonitorScreenSystem.onCameraNetworkChanged(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	public static void applyFallbackName(ItemStack out, PacketContext context) {
		if (PolymerResourcePackUtils.hasMainPack(context)) {
			return;
		}
		String language = context.getPlayer() != null ? context.getPlayer().clientInformation().language() : "";
		String normalized = language == null ? "" : language.toLowerCase();
		String name = "Camera";
		if (normalized.startsWith("rpr")) {
			name = "Камѣра";
		} else if (normalized.startsWith("ru")) {
			name = "Камера";
		} else if (normalized.startsWith("uk")) {
			name = "Камера";
		} else if (normalized.startsWith("ja")) {
			name = "カメラ";
		}
		out.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
	}

	private static BlockState requestHeadState(Identifier modelId) {
		BlockState state = PolymerBlockResourceUtils.requestBlock(BlockModelType.HEAD, PolymerBlockModel.of(modelId));
		if (state == null) {
			throw new IllegalStateException("Unable to allocate head polymer block state for model " + modelId);
		}
		return state;
	}

	private static int rotationFromFacing(net.minecraft.core.Direction direction) {
		return switch (direction) {
			case NORTH -> 8;
			case EAST -> 12;
			case SOUTH -> 0;
			case WEST -> 4;
			default -> 0;
		};
	}
}
