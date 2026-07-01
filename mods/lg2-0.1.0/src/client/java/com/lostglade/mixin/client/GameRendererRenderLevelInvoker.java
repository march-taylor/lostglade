package com.lostglade.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererRenderLevelInvoker {
	@Accessor("fogRenderer")
	FogRenderer lg2$getFogRenderer();

	@Accessor("mainCamera")
	@Mutable
	void lg2$setMainCamera(Camera camera);

	@Accessor("levelProjectionMatrixBuffer")
	PerspectiveProjectionMatrixBuffer lg2$getLevelProjectionMatrixBuffer();

	@Accessor("guiRenderState")
	GuiRenderState lg2$getGuiRenderState();

	@Accessor("guiRenderer")
	GuiRenderer lg2$getGuiRenderer();

	@Invoker("getProjectionMatrixForCulling")
	Matrix4f lg2$getProjectionMatrixForCulling(float fovDegrees);

	@Invoker("getDarkenWorldAmount")
	float lg2$getDarkenWorldAmount(float partialTick);

	@Invoker("extractCamera")
	void lg2$extractCamera(float partialTick);
}
