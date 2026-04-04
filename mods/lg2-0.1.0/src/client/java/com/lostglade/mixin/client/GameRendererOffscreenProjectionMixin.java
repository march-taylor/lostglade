package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotOffscreenWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererOffscreenProjectionMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract float getDepthFar();

	@Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
	private void lg2$useOffscreenTargetAspect(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
		if (!RendererBotOffscreenWorldRenderer.isOffscreenRenderActive() || this.minecraft == null) {
			return;
		}
		int width = Math.max(1, this.minecraft.getMainRenderTarget().width);
		int height = Math.max(1, this.minecraft.getMainRenderTarget().height);
		Matrix4f projection = new Matrix4f().perspective(
				fovDegrees * 0.017453292F,
				width / (float) height,
				0.05F,
				this.getDepthFar()
		);
		cir.setReturnValue(projection);
	}
}
