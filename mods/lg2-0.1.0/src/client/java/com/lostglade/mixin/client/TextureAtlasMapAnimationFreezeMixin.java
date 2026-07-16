package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotOffscreenWorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the shared animated sprite atlas on one frame during map rendering. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMapAnimationFreezeMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void lg2$freezeAnimatedSpritesForMap(CallbackInfo ci) {
		if (RendererBotOffscreenWorldRenderer.isMapTextureAnimationFrozen()) {
			ci.cancel();
		}
	}
}
