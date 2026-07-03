package com.lostglade.mixin;

import com.lostglade.server.BrownBedDisplaySystem;
import com.lostglade.server.ServerMechanicsGateSystem;
import com.lostglade.server.ServerMilkPocketDimensionSystem;
import com.lostglade.server.ServerRaceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementGateMixin {
	private static final ThreadLocal<BlockPos> LG2_CARTEL_FERN_PLACEMENT_POS = new ThreadLocal<>();

	@Shadow
	public abstract BlockPlaceContext updatePlacementContext(BlockPlaceContext context);

	@Shadow
	protected abstract BlockState getPlacementState(BlockPlaceContext context);

	@Shadow
	protected abstract boolean canPlace(BlockPlaceContext context, BlockState state);

	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void lg2$blockLockedMechanicPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context == null) {
			return;
		}

		BlockItem blockItem = (BlockItem) (Object) this;
		if (lg2$shouldCancelMilkPocketPlacement(context)) {
			if (!context.getLevel().isClientSide() && context.getPlayer() instanceof ServerPlayer serverPlayer) {
				ServerMechanicsGateSystem.syncPlayerInventory(serverPlayer);
			}
			cir.setReturnValue(InteractionResult.FAIL);
			return;
		}

		if (context.getLevel().isClientSide()) {
			return;
		}

		if (blockItem.getBlock() == Blocks.FERN) {
			LG2_CARTEL_FERN_PLACEMENT_POS.set(lg2$resolvePlacementPos(context));
		}
		boolean golemHead = ServerMechanicsGateSystem.isGolemHeadBlock(blockItem.getBlock());
		if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
			BlockState clickedState = context.getLevel().getBlockState(context.getClickedPos());
			if (!serverPlayer.isSecondaryUseActive()
					&& ServerRaceSystem.canLittleDictatorBypassInteraction(serverPlayer, clickedState)
					&& (clickedState.is(Blocks.IRON_DOOR) || clickedState.is(Blocks.IRON_TRAPDOOR))) {
				ServerMechanicsGateSystem.syncPlayerInventory(serverPlayer);
				cir.setReturnValue(InteractionResult.FAIL);
				return;
			}
			if (golemHead) {
				if (ServerMechanicsGateSystem.shouldCancelPlayerGolemHeadPlacement(serverPlayer, context, blockItem.getBlock())) {
					ServerMechanicsGateSystem.syncPlayerInventory(serverPlayer);
					cir.setReturnValue(InteractionResult.FAIL);
					return;
				}
				ServerMechanicsGateSystem.beginTrackedGolemHeadPlacement(serverPlayer, blockItem);
				return;
			}
			if (ServerMilkPocketDimensionSystem.isPhantomFloorBlockPlacement(context)
					&& !lg2$canPlaceOnMilkPocketPhantomFloor(context)) {
				ServerMechanicsGateSystem.syncPlayerInventory(serverPlayer);
				cir.setReturnValue(InteractionResult.FAIL);
				return;
			}
			if (!ServerMechanicsGateSystem.canPlaceBlock(serverPlayer, context, blockItem.getBlock())) {
				cir.setReturnValue(InteractionResult.FAIL);
			}
			return;
		}

		if (golemHead && ServerMechanicsGateSystem.shouldCancelAutomatedGolemHeadPlacement(context, blockItem.getBlock())) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "place", at = @At("RETURN"))
	private void lg2$completeTrackedGolemPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		BlockItem blockItem = (BlockItem) (Object) this;
		if (ServerMechanicsGateSystem.isGolemHeadBlock(blockItem.getBlock())) {
			ServerMechanicsGateSystem.completeTrackedGolemHeadPlacement();
		}
		try {
			if (context != null
					&& !context.getLevel().isClientSide()
					&& context.getPlayer() instanceof ServerPlayer serverPlayer
					&& ServerMilkPocketDimensionSystem.isPhantomFloorBlockPlacement(context)
					&& !cir.getReturnValue().consumesAction()) {
				ServerMechanicsGateSystem.syncPlayerInventory(serverPlayer);
			}
			if (blockItem.getBlock() == Blocks.FERN
					&& cir.getReturnValue().consumesAction()
					&& context != null
					&& context.getPlayer() instanceof ServerPlayer serverPlayer) {
				BlockPos placedPos = LG2_CARTEL_FERN_PLACEMENT_POS.get();
				if (placedPos != null) {
					ServerRaceSystem.onFernPlaced(serverPlayer, placedPos);
				}
			}
			if (blockItem.getBlock() == Blocks.BROWN_BED
					&& cir.getReturnValue().consumesAction()
					&& context != null) {
				BrownBedDisplaySystem.onPotentialBrownBedPlacement(context.getLevel(), lg2$resolvePlacementPos(context));
			}
		} finally {
			LG2_CARTEL_FERN_PLACEMENT_POS.remove();
		}
	}

	private static BlockPos lg2$resolvePlacementPos(BlockPlaceContext context) {
		if (context == null) {
			return null;
		}
		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		if (level == null || clickedPos == null) {
			return null;
		}
		if (level.getBlockState(clickedPos).canBeReplaced(context)) {
			return clickedPos;
		}
		return clickedPos.relative(context.getClickedFace());
	}

	private boolean lg2$canPlaceOnMilkPocketPhantomFloor(BlockPlaceContext context) {
		if (context == null || !context.canPlace()) {
			return false;
		}
		BlockPlaceContext updatedContext = this.updatePlacementContext(context);
		if (updatedContext == null || !ServerMilkPocketDimensionSystem.isPhantomFloorBlockPlacement(updatedContext)) {
			return false;
		}
		BlockState placementState = this.getPlacementState(updatedContext);
		return placementState != null
				&& this.canPlace(updatedContext, placementState)
				&& ServerMilkPocketDimensionSystem.canPlaceBlockOnPhantomFloor(updatedContext, placementState);
	}

	private boolean lg2$shouldCancelMilkPocketPlacement(BlockPlaceContext context) {
		if (context == null || !ServerMilkPocketDimensionSystem.isMilkPocket(context.getLevel())) {
			return false;
		}

		BlockPlaceContext updatedContext = this.updatePlacementContext(context);
		if (updatedContext == null) {
			return false;
		}
		BlockState placementState = this.getPlacementState(updatedContext);
		if (placementState == null) {
			return false;
		}
		return !ServerMilkPocketDimensionSystem.canPlaceBlockInBuildZone(updatedContext, placementState);
	}
}
