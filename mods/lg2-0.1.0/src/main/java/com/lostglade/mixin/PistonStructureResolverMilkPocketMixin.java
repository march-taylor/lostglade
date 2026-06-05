package com.lostglade.mixin;

import com.lostglade.server.ServerMilkPocketDimensionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonStructureResolverMilkPocketMixin {
	@Shadow
	@Final
	private Level level;

	@Shadow
	@Final
	private BlockPos pistonPos;

	@Shadow
	@Final
	private Direction pushDirection;

	@Shadow
	@Final
	private Direction pistonDirection;

	@Shadow
	public abstract List<BlockPos> getToPush();

	@Shadow
	public abstract List<BlockPos> getToDestroy();

	@Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
	private void lg2$rejectMilkPocketPistonMoveOutsideZone(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}
		if (ServerMilkPocketDimensionSystem.shouldRejectPistonMove(
				this.level,
				this.pistonPos,
				this.pistonDirection,
				this.pushDirection,
				this.getToPush(),
				this.getToDestroy()
		)) {
			cir.setReturnValue(false);
		}
	}
}
