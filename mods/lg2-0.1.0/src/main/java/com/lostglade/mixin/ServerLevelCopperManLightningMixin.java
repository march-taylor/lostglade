package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelCopperManLightningMixin {
	@Inject(method = "findLightningTargetAround", at = @At("RETURN"), cancellable = true)
	private void lg2$redirectLightningToCopperMan(BlockPos origin, CallbackInfoReturnable<BlockPos> cir) {
		ServerLevel level = (ServerLevel) (Object) this;
		BlockPos target = ServerRaceSystem.findCopperManLightningTarget(level, origin);
		if (target != null) {
			cir.setReturnValue(target);
		}
	}
}
