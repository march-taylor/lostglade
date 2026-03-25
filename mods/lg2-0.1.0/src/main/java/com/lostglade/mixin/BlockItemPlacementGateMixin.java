package com.lostglade.mixin;

import com.lostglade.server.ServerMechanicsGateSystem;
import com.lostglade.server.ServerRaceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementGateMixin {
	private static final ThreadLocal<BlockPos> LG2_CARTEL_FERN_PLACEMENT_POS = new ThreadLocal<>();

	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void lg2$blockLockedMechanicPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context == null || context.getLevel().isClientSide()) {
			return;
		}

		BlockItem blockItem = (BlockItem) (Object) this;
		if (blockItem.getBlock() == Blocks.FERN) {
			LG2_CARTEL_FERN_PLACEMENT_POS.set(lg2$resolvePlacementPos(context));
		}
		boolean golemHead = ServerMechanicsGateSystem.isGolemHeadBlock(blockItem.getBlock());
		if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
			if (golemHead) {
				if (ServerMechanicsGateSystem.shouldCancelPlayerGolemHeadPlacement(serverPlayer, context, blockItem.getBlock())) {
					ServerMechanicsGateSystem.syncPlayerInventory(serverPlayer);
					cir.setReturnValue(InteractionResult.FAIL);
					return;
				}
				ServerMechanicsGateSystem.beginTrackedGolemHeadPlacement(serverPlayer, blockItem);
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
			if (blockItem.getBlock() == Blocks.FERN
					&& cir.getReturnValue().consumesAction()
					&& context != null
					&& context.getPlayer() instanceof ServerPlayer serverPlayer) {
				BlockPos placedPos = LG2_CARTEL_FERN_PLACEMENT_POS.get();
				if (placedPos != null) {
					ServerRaceSystem.onFernPlaced(serverPlayer, placedPos);
				}
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
}
