package com.lostglade.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererRenderStateAccessor {
	@Accessor("levelRenderState")
	LevelRenderState lg2$getLevelRenderState();
}
