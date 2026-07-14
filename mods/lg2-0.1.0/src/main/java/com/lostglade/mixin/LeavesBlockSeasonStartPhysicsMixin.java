package com.lostglade.mixin;

import com.lostglade.server.SeasonStartSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LeavesBlock.class)
public abstract class LeavesBlockSeasonStartPhysicsMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void lg2$freezeSeasonStartLeafTick(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			RandomSource random,
			CallbackInfo ci
	) {
		if (SeasonStartSystem.shouldFreezeSceneBoundaryPhysics(level, pos)) {
			ci.cancel();
		}
	}

	@Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
	private void lg2$freezeSeasonStartLeafRandomTick(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			RandomSource random,
			CallbackInfo ci
	) {
		if (SeasonStartSystem.shouldFreezeSceneBoundaryPhysics(level, pos)) {
			ci.cancel();
		}
	}
}
