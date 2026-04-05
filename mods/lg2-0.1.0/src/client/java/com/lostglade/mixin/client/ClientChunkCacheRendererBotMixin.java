package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientMode;
import com.lostglade.client.RendererBotVirtualChunkAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheRendererBotMixin implements RendererBotVirtualChunkAccess {
	@Shadow
	@Final
	private ClientLevel level;

	@Shadow
	@Final
	private LevelChunk emptyChunk;

	@Unique
	private final Long2ObjectOpenHashMap<LevelChunk> lg2$virtualChunks = new Long2ObjectOpenHashMap<>();

	@Unique
	private final LongOpenHashSet lg2$virtualEmptySections = new LongOpenHashSet();

	@Unique
	private final Object lg2$virtualChunkLock = new Object();

	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void lg2$dropVirtualChunk(ChunkPos pos, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled() || pos == null) {
			return;
		}
		LevelChunk removed;
		synchronized (this.lg2$virtualChunkLock) {
			removed = this.lg2$virtualChunks.remove(pos.toLong());
			if (removed != null) {
				lg2$dropEmptySectionsLocked(removed);
			}
		}
		if (removed != null) {
			this.level.unload(removed);
		}
		ci.cancel();
	}

	@Inject(
			method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
			at = @At("HEAD"),
			cancellable = true
	)
	private void lg2$getVirtualChunk(int x, int z, ChunkStatus status, boolean load, CallbackInfoReturnable<LevelChunk> cir) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		LevelChunk chunk;
		synchronized (this.lg2$virtualChunkLock) {
			chunk = this.lg2$virtualChunks.get(new ChunkPos(x, z).toLong());
		}
		if (lg2$isValidChunk(chunk, x, z)) {
			cir.setReturnValue(chunk);
			return;
		}
		cir.setReturnValue(load ? this.emptyChunk : null);
	}

	@Inject(method = "replaceBiomes", at = @At("HEAD"), cancellable = true)
	private void lg2$replaceVirtualBiomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		LevelChunk chunk;
		synchronized (this.lg2$virtualChunkLock) {
			chunk = this.lg2$virtualChunks.get(new ChunkPos(x, z).toLong());
		}
		if (lg2$isValidChunk(chunk, x, z)) {
			chunk.replaceBiomes(buffer);
			lg2$markChunkSectionsDirty(chunk);
		}
		ci.cancel();
	}

	@Inject(method = "replaceWithPacketData", at = @At("HEAD"), cancellable = true)
	private void lg2$storeVirtualChunkPacket(
			int x,
			int z,
			FriendlyByteBuf buffer,
			Map<Heightmap.Types, long[]> heightmaps,
			java.util.function.Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntityConsumer,
			CallbackInfoReturnable<LevelChunk> cir
	) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		ChunkPos pos = new ChunkPos(x, z);
		long key = pos.toLong();
		LevelChunk chunk;
		LevelChunk previousChunk = null;
		synchronized (this.lg2$virtualChunkLock) {
			chunk = this.lg2$virtualChunks.get(key);
			if (!lg2$isValidChunk(chunk, x, z)) {
				previousChunk = chunk;
				chunk = new LevelChunk(this.level, pos);
				this.lg2$virtualChunks.put(key, chunk);
			}
		}
		if (previousChunk != null) {
			synchronized (this.lg2$virtualChunkLock) {
				lg2$dropEmptySectionsLocked(previousChunk);
			}
			this.level.unload(previousChunk);
		}
		chunk.replaceWithPacketData(buffer, heightmaps, blockEntityConsumer);
		synchronized (this.lg2$virtualChunkLock) {
			lg2$refreshEmptySectionsLocked(chunk);
		}
		this.level.onChunkLoaded(pos);
		lg2$markChunkSectionsDirty(chunk);
		cir.setReturnValue(chunk);
	}

	@Inject(method = "onSectionEmptinessChanged", at = @At("HEAD"), cancellable = true)
	private void lg2$trackVirtualSectionEmptiness(int chunkX, int sectionY, int chunkZ, boolean empty, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		long sectionKey = SectionPos.asLong(chunkX, sectionY, chunkZ);
		boolean becameNonEmpty = false;
		synchronized (this.lg2$virtualChunkLock) {
			if (empty) {
				this.lg2$virtualEmptySections.add(sectionKey);
			} else if (this.lg2$virtualEmptySections.remove(sectionKey)) {
				becameNonEmpty = true;
			}
		}
		if (becameNonEmpty) {
			this.level.onSectionBecomingNonEmpty(sectionKey);
		}
		ci.cancel();
	}

	@Inject(method = "getLoadedEmptySections", at = @At("RETURN"), cancellable = true)
	private void lg2$includeVirtualEmptySections(CallbackInfoReturnable<LongOpenHashSet> cir) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		synchronized (this.lg2$virtualChunkLock) {
			cir.setReturnValue(new LongOpenHashSet(this.lg2$virtualEmptySections));
		}
	}

	@Inject(method = "getLoadedChunksCount", at = @At("RETURN"), cancellable = true)
	private void lg2$includeVirtualChunkCount(CallbackInfoReturnable<Integer> cir) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		synchronized (this.lg2$virtualChunkLock) {
			cir.setReturnValue(this.lg2$virtualChunks.size());
		}
	}

	@Unique
	private static boolean lg2$isValidChunk(LevelChunk chunk, int chunkX, int chunkZ) {
		if (chunk == null) {
			return false;
		}
		ChunkPos pos = chunk.getPos();
		return pos.x == chunkX && pos.z == chunkZ;
	}

	@Unique
	private void lg2$dropEmptySectionsLocked(LevelChunk chunk) {
		if (chunk == null) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
			this.lg2$virtualEmptySections.remove(SectionPos.asLong(pos.x, chunk.getSectionYFromSectionIndex(sectionIndex), pos.z));
		}
	}

	@Unique
	private void lg2$refreshEmptySectionsLocked(LevelChunk chunk) {
		if (chunk == null) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
			long sectionKey = SectionPos.asLong(pos.x, chunk.getSectionYFromSectionIndex(sectionIndex), pos.z);
			if (chunk.getSections()[sectionIndex].hasOnlyAir()) {
				this.lg2$virtualEmptySections.add(sectionKey);
			} else if (this.lg2$virtualEmptySections.remove(sectionKey)) {
				this.level.onSectionBecomingNonEmpty(sectionKey);
			}
		}
	}

	@Unique
	private void lg2$markChunkSectionsDirty(LevelChunk chunk) {
		if (chunk == null || this.level == null) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		LevelChunkSection[] sections = chunk.getSections();
		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			LevelChunkSection section = sections[sectionIndex];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			this.level.setSectionDirtyWithNeighbors(pos.x, chunk.getSectionYFromSectionIndex(sectionIndex), pos.z);
		}
	}

	@Override
	public Collection<LevelChunk> lg2$getLoadedVirtualChunksSnapshot() {
		synchronized (this.lg2$virtualChunkLock) {
			return new ArrayList<>(this.lg2$virtualChunks.values());
		}
	}
}
