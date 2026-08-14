package com.lostglade.block;

import com.lostglade.item.ModItems;
import com.lostglade.server.MonitorScreenSystem;
import com.lostglade.server.RocketLaunchEventSystem;
import com.lostglade.server.CameraOrientationStore;
import com.lostglade.server.BluetoothLinkSystem;
import com.lostglade.server.PlacedDeviceNameStore;
import eu.pb4.polymer.core.api.block.SimplePolymerBlock;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.List;

public final class CameraBlock extends SimplePolymerBlock {
	private final BlockState hitboxState;

	public CameraBlock(BlockBehaviour.Properties properties) {
		super(properties, Blocks.STRUCTURE_VOID);
		this.hitboxState = Blocks.PLAYER_HEAD.defaultBlockState();
		this.registerDefaultState(this.stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HorizontalDirectionalBlock.FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		net.minecraft.core.Direction facing = context.getHorizontalDirection().getOpposite();
		if (context.getPlayer() != null) {
			float yaw = yawTo(captureBaseOrigin(context.getClickedPos()), context.getPlayer().getEyePosition());
			facing = net.minecraft.core.Direction.fromYRot(yaw);
		}
		return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
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
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return Blocks.STRUCTURE_VOID.defaultBlockState();
		}
		// The resource pack already makes vanilla brown beds transparent for the
		// display-based bed implementation.  Reuse that invisible, colliding
		// surrogate instead of a player head whose base skin becomes black.
		return Blocks.BROWN_BED.defaultBlockState()
				.setValue(BedBlock.FACING, state.getValue(HorizontalDirectionalBlock.FACING))
				.setValue(BedBlock.PART, BedPart.FOOT)
				.setValue(BedBlock.OCCUPIED, false);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.hitboxState.getShape(level, pos, context);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.hitboxState.getCollisionShape(level, pos, context);
	}

	@Override
	protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return this.hitboxState.getShape(level, pos);
	}

	@Override
	protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
		return List.of(new ItemStack(ModItems.CAMERA));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (level instanceof ServerLevel serverLevel) {
			CameraOrientationStore.CameraPose pose = CameraOrientationStore.get(serverLevel, pos);
			float yaw = pose != null ? pose.yaw() : state.getValue(HorizontalDirectionalBlock.FACING).toYRot();
			float pitch = pose != null ? pose.pitch() : 0.0F;
			CameraDisplayHelper.spawnOrUpdate(serverLevel, pos, yaw, pitch);
			MonitorScreenSystem.onCameraNetworkChanged(serverLevel, pos);
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		PlacedDeviceNameStore.rememberPlacedCameraName(serverLevel, pos, stack);
		if (placer == null) {
			return;
		}
		aimAt(serverLevel, pos, placer.getEyePosition());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		return this.aimAtPlayer(level, pos, player);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
		return this.aimAtPlayer(level, pos, player);
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
		if (RocketLaunchEventSystem.isLaunchedMountedDevice(level, pos)) {
			// A rocket keeps its device identity while the physical block becomes a
			// moving display/anchor.  Do not run any normal removal work: apart from
			// erasing the pose/link it can trigger secondary network cleanup after
			// the endpoint has already been preserved.
			return;
		}
		CameraDisplayHelper.remove(level, pos);
		CameraOrientationStore.remove(level, pos);
		PlacedDeviceNameStore.removeCameraName(level, pos);
		BluetoothLinkSystem.removeBlockEndpoint(level, BluetoothLinkSystem.EndpointType.CAMERA, pos);
		MonitorScreenSystem.onCameraNetworkChanged(level, pos);
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	public static void applyFallbackName(ItemStack out, PacketContext context) {
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

	public static Vec3 captureBaseOrigin(BlockPos cameraPos) {
		return new Vec3(cameraPos.getX() + 0.5D, cameraPos.getY() + 0.25D, cameraPos.getZ() + 0.5D);
	}

	public static Vec3 captureOrigin(BlockPos cameraPos, float yaw, float pitch) {
		Vec3 base = captureBaseOrigin(cameraPos);
		Vec3 forward = forwardVector(yaw, pitch).scale(0.24D);
		return base.add(forward);
	}

	public static float yawTo(Vec3 origin, Vec3 target) {
		Vec3 delta = target.subtract(origin);
		return (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
	}

	public static float pitchTo(Vec3 origin, Vec3 target) {
		Vec3 delta = target.subtract(origin);
		double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		return (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
	}

	private static Vec3 forwardVector(float yaw, float pitch) {
		float yawRadians = yaw * ((float) Math.PI / 180.0F);
		float pitchRadians = pitch * ((float) Math.PI / 180.0F);
		float x = -Mth.sin(yawRadians) * Mth.cos(pitchRadians);
		float y = -Mth.sin(pitchRadians);
		float z = Mth.cos(yawRadians) * Mth.cos(pitchRadians);
		return new Vec3(x, y, z);
	}

	private InteractionResult aimAtPlayer(Level level, BlockPos pos, Player player) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		boolean changed = aimAt(serverLevel, pos, serverPlayer.getEyePosition());
		return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
	}

	private static boolean aimAt(ServerLevel level, BlockPos pos, Vec3 target) {
		if (level == null || pos == null || target == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CameraBlock)) {
			return false;
		}
		Vec3 origin = captureBaseOrigin(pos);
		float yaw = yawTo(origin, target);
		float pitch = pitchTo(origin, target);
		net.minecraft.core.Direction facing = net.minecraft.core.Direction.fromYRot(yaw);
		BlockState updatedState = state.setValue(HorizontalDirectionalBlock.FACING, facing);
		if (updatedState != state) {
			level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
		}
		CameraOrientationStore.set(level, pos, yaw, pitch);
		CameraDisplayHelper.spawnOrUpdate(level, pos, yaw, pitch);
		MonitorScreenSystem.onCameraNetworkChanged(level, pos);
		return true;
	}
}
