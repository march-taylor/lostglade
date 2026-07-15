package com.lostglade.mixin;

import com.lostglade.server.RendererBotCameraSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheRendererBotShadowDirtyMixin {
	@Shadow
	public abstract Level getLevel();

	@Inject(method = "blockChanged", at = @At("TAIL"))
	private void lg2$mirrorRendererBotShadowBlockUpdate(BlockPos pos, CallbackInfo ci) {
		if (!(this.getLevel() instanceof ServerLevel level)) {
			return;
		}
		if (pos == null) {
			return;
		}
		RendererBotCameraSystem.mirrorShadowBlockUpdate(level, pos);
	}

	@Inject(method = "onLightUpdate", at = @At("TAIL"))
	private void lg2$mirrorRendererBotShadowLightUpdate(LightLayer type, SectionPos pos, CallbackInfo ci) {
		if (!(this.getLevel() instanceof ServerLevel level) || pos == null) {
			return;
		}
		RendererBotCameraSystem.mirrorShadowLightUpdate(level, new ChunkPos(pos.x(), pos.z()));
	}
}
