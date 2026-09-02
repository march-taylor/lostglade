package com.lostglade.mixin;

import com.lostglade.server.OrthodoxStockSystem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineOrthodoxLightMixin {
	@Inject(method = "getEmission", at = @At("RETURN"), cancellable = true)
	private void lg2$applyOrthodoxDynamicLight(long packedPos, BlockState state, CallbackInfoReturnable<Integer> cir) {
		LightChunkGetter chunkSource = ((LightEngineAccessor) (Object) this).lg2$getChunkSource();
		int dynamicEmission = OrthodoxStockSystem.getDynamicLightEmission(chunkSource.getLevel(), packedPos);
		if (dynamicEmission > cir.getReturnValueI()) cir.setReturnValue(dynamicEmission);
	}
}
