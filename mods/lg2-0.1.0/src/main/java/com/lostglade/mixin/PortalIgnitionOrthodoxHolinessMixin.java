package com.lostglade.mixin;

import com.lostglade.server.OrthodoxHolinessSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({FlintAndSteelItem.class, FireChargeItem.class})
public abstract class PortalIgnitionOrthodoxHolinessMixin {
	@Inject(method = "useOn", at = @At("RETURN"))
	private void lg2$recordOrthodoxNetherPortalOpening(
			UseOnContext context,
			CallbackInfoReturnable<InteractionResult> cir
	) {
		if (cir.getReturnValue() != InteractionResult.SUCCESS || !(context.getPlayer() instanceof ServerPlayer player)) return;
		BlockPos clicked = context.getClickedPos();
		BlockPos placed = clicked.relative(context.getClickedFace());
		if (context.getLevel().getBlockState(clicked).is(Blocks.NETHER_PORTAL)
				|| context.getLevel().getBlockState(placed).is(Blocks.NETHER_PORTAL)) {
			OrthodoxHolinessSystem.onNetherPortalOpened(player);
		}
	}
}
