package com.lostglade.mixin;

import com.lostglade.block.ModBlocks;
import com.lostglade.server.CameraRelocationSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Commits retained camera state only after vanilla has put the moved block down. */
@Mixin(PistonMovingBlockEntity.class)
public abstract class PistonMovingBlockEntityCameraRelocationMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private static void lg2$advanceCameraRelocation(
			Level level,
			BlockPos destination,
			BlockState state,
			PistonMovingBlockEntity self,
			CallbackInfo ci
	) {
		if (!self.getMovedState().is(ModBlocks.CAMERA) || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockPos source = destination.relative(self.getPushDirection().getOpposite());
		if (serverLevel.getBlockState(destination).is(ModBlocks.CAMERA)) {
			// CameraBlock#onPlace is the primary completion point. It can retag
			// the ItemDisplay while the source and destination are both known.
			return;
		}
		if (self.getProgress(1.0F) >= 1.0F && !serverLevel.getBlockState(destination).is(Blocks.MOVING_PISTON)) {
			CameraRelocationSystem.cancelPistonMove(serverLevel, source);
		} else {
			// At TAIL the moving block entity has already advanced. A zero partial
			// tick asks for progressO (the previous tick), pinning a display at the
			// middle of the piston path instead of its actual destination.
			CameraRelocationSystem.advancePistonMove(serverLevel, source, destination, self.getProgress(1.0F));
		}
	}

	@Inject(method = "finalTick", at = @At("TAIL"))
	private void lg2$finishCameraRelocation(CallbackInfo ci) {
		PistonMovingBlockEntity self = (PistonMovingBlockEntity) (Object) this;
		if (!self.getMovedState().is(ModBlocks.CAMERA)) {
			return;
		}
		Level level = self.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		BlockPos destination = self.getBlockPos();
		BlockPos source = destination.relative(self.getPushDirection().getOpposite());
		if (!serverLevel.getBlockState(destination).is(ModBlocks.CAMERA)) {
			CameraRelocationSystem.cancelPistonMove(serverLevel, source);
		}
	}
}
