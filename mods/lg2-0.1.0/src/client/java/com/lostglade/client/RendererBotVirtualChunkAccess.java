package com.lostglade.client;

import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Collection;

public interface RendererBotVirtualChunkAccess {
	Collection<LevelChunk> lg2$getLoadedVirtualChunksSnapshot();
}
