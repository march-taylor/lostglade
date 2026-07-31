package com.lostglade.mixin;

import com.lostglade.server.SeasonStartSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseSeasonStartPhysicsMixin {
	@Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
	private void lg2$freezeSeasonStartBoundaryPhysics(
			LevelReader level,
			ScheduledTickAccess scheduledTickAccess,
			BlockPos pos,
			Direction direction,
			BlockPos neighborPos,
			BlockState neighborState,
			RandomSource random,
			CallbackInfoReturnable<BlockState> cir
	) {
		if (level instanceof Level gameLevel && SeasonStartSystem.shouldFreezeSceneBoundaryPhysics(gameLevel, pos, neighborPos)) {
			cir.setReturnValue((BlockState) (Object) this);
		}
	}
}
