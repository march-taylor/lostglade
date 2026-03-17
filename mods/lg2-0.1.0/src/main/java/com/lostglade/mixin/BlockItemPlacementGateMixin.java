package com.lostglade.mixin;

import com.lostglade.server.ServerMechanicsGateSystem;
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
	@Inject(method = "place", at = @At("HEAD"), cancellable = true)
	private void lg2$blockLockedMechanicPlacement(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (context == null || !(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
			return;
		}

		BlockItem blockItem = (BlockItem) (Object) this;
		if (!ServerMechanicsGateSystem.canPlaceBlock(serverPlayer, context, blockItem.getBlock())) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
