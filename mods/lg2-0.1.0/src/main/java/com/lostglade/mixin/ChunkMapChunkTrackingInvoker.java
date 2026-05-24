package com.lostglade.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface ChunkMapChunkTrackingInvoker {
	@Invoker("applyChunkTrackingView")
	void lg2$applyChunkTrackingView(ServerPlayer player, ChunkTrackingView chunkTrackingView);
}
