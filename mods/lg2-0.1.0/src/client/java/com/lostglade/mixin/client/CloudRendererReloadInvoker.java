package com.lostglade.mixin.client;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

@Mixin(CloudRenderer.class)
public interface CloudRendererReloadInvoker {
	@Invoker("prepare")
	Optional<?> lg2$prepareCloudTexture(ResourceManager resourceManager, ProfilerFiller profiler);

	@Invoker("apply")
	void lg2$applyPreparedClouds(Optional<?> prepared, ResourceManager resourceManager, ProfilerFiller profiler);
}
