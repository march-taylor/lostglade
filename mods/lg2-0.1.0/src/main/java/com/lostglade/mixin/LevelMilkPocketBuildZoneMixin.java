package com.lostglade.mixin;

import com.lostglade.block.ModBlocks;
import com.lostglade.server.ServerMilkPocketDimensionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMilkPocketBuildZoneMixin {
	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$rejectMilkPocketBlockWriteOutsideZone(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
		if (ServerMilkPocketDimensionSystem.shouldReplaceAirWithPhantomFloor((Level) (Object) this, pos, state)) {
			cir.setReturnValue(((Level) (Object) this).setBlock(pos, ModBlocks.MILK_POCKET_PHANTOM_FLOOR.defaultBlockState(), flags));
			return;
		}
		if (ServerMilkPocketDimensionSystem.shouldRejectBlockStateWrite((Level) (Object) this, pos, state)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
	private void lg2$rejectMilkPocketBlockWriteOutsideZone(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
		if (ServerMilkPocketDimensionSystem.shouldReplaceAirWithPhantomFloor((Level) (Object) this, pos, state)) {
			cir.setReturnValue(((Level) (Object) this).setBlock(pos, ModBlocks.MILK_POCKET_PHANTOM_FLOOR.defaultBlockState(), flags, recursionLeft));
			return;
		}
		if (ServerMilkPocketDimensionSystem.shouldRejectBlockStateWrite((Level) (Object) this, pos, state)) {
			cir.setReturnValue(false);
		}
	}
}
