package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientMode;
import com.lostglade.client.RendererBotOffscreenWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererOffscreenSyncMixin {
	@Shadow
	private ClientLevel level;

	@Inject(method = "setSectionDirty(III)V", at = @At("TAIL"))
	private void lg2$syncOffscreenSectionDirty(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.levelRenderer != (Object) this || this.level == null) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onSectionDirty(this.level, sectionX, sectionY, sectionZ);
	}

	@Inject(method = "onChunkReadyToRender(Lnet/minecraft/world/level/ChunkPos;)V", at = @At("TAIL"))
	private void lg2$syncOffscreenChunkReady(ChunkPos pos, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.levelRenderer != (Object) this || this.level == null) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onChunkReadyToRender(this.level, pos);
	}
}
