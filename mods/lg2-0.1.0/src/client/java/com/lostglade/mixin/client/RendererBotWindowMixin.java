package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientMode;
import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class RendererBotWindowMixin {
	@Inject(
			method = "<init>",
			at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J")
	)
	private void lg2$hideRendererBotWindow(
			WindowEventHandler eventHandler,
			ScreenManager screenManager,
			DisplayData displayData,
			String preferredFullscreenVideoMode,
			String title,
			CallbackInfo ci
	) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
		if (RendererBotClientMode.useHeadlessGlfw()) {
			GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_EGL_CONTEXT_API);
		}
	}
}
