package com.lostglade.mixin;

import com.lostglade.server.ServerMechanicsGateSystem;
import com.lostglade.server.ServerUpgradeUiSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarvedPumpkinBlock.class)
public abstract class CarvedPumpkinBlockGolemGateMixin {
	@Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
	private void lg2$blockLockedGolemSpawn(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
		if (level.isClientSide() || oldState.is(state.getBlock())) {
			return;
		}

		ServerPlayer player = ServerMechanicsGateSystem.getTrackedGolemHeadPlacer();
		if (player == null) {
			return;
		}

		String requirement = ServerMechanicsGateSystem.requiredUpgradeForGolemSpawn(level, pos);
		if (requirement == null || ServerUpgradeUiSystem.hasUpgrade(player, requirement)) {
			return;
		}

		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		ServerMechanicsGateSystem.queueTrackedGolemHeadRefund();
		ci.cancel();
	}
}
