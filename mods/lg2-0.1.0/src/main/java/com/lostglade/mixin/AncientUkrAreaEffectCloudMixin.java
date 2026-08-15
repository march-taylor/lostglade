package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AreaEffectCloud.class)
public abstract class AncientUkrAreaEffectCloudMixin {
	@Inject(method = "serverTick", at = @At("HEAD"))
	private void lg2$beginAncientUkrGasContext(ServerLevel level, CallbackInfo ci) {
		ServerRaceSystem.beginAncientUkrGasEffectContext();
	}

	@Inject(method = "serverTick", at = @At("RETURN"))
	private void lg2$endAncientUkrGasContext(ServerLevel level, CallbackInfo ci) {
		ServerRaceSystem.endAncientUkrGasEffectContext();
	}
}