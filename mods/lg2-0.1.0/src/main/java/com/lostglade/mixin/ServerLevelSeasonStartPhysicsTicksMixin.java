package com.lostglade.mixin;

import com.lostglade.server.SeasonStartSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The central scheduled-tick dispatch catches every block type, not only sand
 * and water. This keeps reveal placement inert until a player opens a local
 * physics chain by breaking a block.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSeasonStartPhysicsTicksMixin {
	@Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
	private void lg2$freezeRevealScheduledBlockTicks(BlockPos pos, Block block, CallbackInfo ci) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (SeasonStartSystem.shouldFreezeSceneBoundaryPhysics(level, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "tickFluid", at = @At("HEAD"), cancellable = true)
	private void lg2$freezeRevealScheduledFluidTicks(BlockPos pos, Fluid fluid, CallbackInfo ci) {
		ServerLevel level = (ServerLevel) (Object) this;
		if (SeasonStartSystem.shouldFreezeSceneBoundaryPhysics(level, pos)) {
			ci.cancel();
		}
	}
}
