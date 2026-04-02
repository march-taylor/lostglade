package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientMode;
import com.mojang.blaze3d.platform.GLX;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.LongSupplier;

@Mixin(GLX.class)
public abstract class RendererBotGlfwMixin {
	@Inject(
			method = "_initGlfw",
			at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwInit()Z")
	)
	private static void lg2$configureHeadlessGlfw(CallbackInfoReturnable<LongSupplier> cir) {
		if (!RendererBotClientMode.useHeadlessGlfw()) {
			return;
		}
		GLFW.glfwInitHint(GLFW.GLFW_PLATFORM, GLFW.GLFW_PLATFORM_NULL);
	}
}
