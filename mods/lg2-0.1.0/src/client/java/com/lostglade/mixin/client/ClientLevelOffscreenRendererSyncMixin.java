package com.lostglade.mixin.client;

import com.lostglade.client.RendererBotClientMode;
import com.lostglade.client.RendererBotOffscreenWorldRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelOffscreenRendererSyncMixin {
	@Inject(method = "sendBlockUpdated", at = @At("TAIL"))
	private void lg2$syncOffscreenBlockChanged(
			BlockPos pos,
			BlockState oldState,
			BlockState newState,
			int flags,
			CallbackInfo ci
	) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onBlockChanged((ClientLevel) (Object) this, pos, oldState, newState, flags);
	}

	@Inject(method = "setBlocksDirty", at = @At("TAIL"))
	private void lg2$syncOffscreenBlockDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onBlockDirty((ClientLevel) (Object) this, pos, oldState, newState);
	}

	@Inject(method = "setSectionDirtyWithNeighbors", at = @At("TAIL"))
	private void lg2$syncOffscreenSectionDirtyWithNeighbors(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onSectionDirtyWithNeighbors((ClientLevel) (Object) this, sectionX, sectionY, sectionZ);
	}

	@Inject(method = "setSectionRangeDirty", at = @At("TAIL"))
	private void lg2$syncOffscreenSectionRangeDirty(
			int minSectionX,
			int minSectionY,
			int minSectionZ,
			int maxSectionX,
			int maxSectionY,
			int maxSectionZ,
			CallbackInfo ci
	) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onSectionRangeDirty(
				(ClientLevel) (Object) this,
				minSectionX,
				minSectionY,
				minSectionZ,
				maxSectionX,
				maxSectionY,
				maxSectionZ
		);
	}

	@Inject(method = "onSectionBecomingNonEmpty", at = @At("TAIL"))
	private void lg2$syncOffscreenSectionBecomingNonEmpty(long sectionPos, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onSectionBecomingNonEmpty((ClientLevel) (Object) this, sectionPos);
	}

	@Inject(method = "destroyBlockProgress", at = @At("TAIL"))
	private void lg2$syncOffscreenBlockBreakProgress(int breakerId, BlockPos pos, int progress, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onDestroyBlockProgress((ClientLevel) (Object) this, breakerId, pos, progress);
	}

	@Inject(method = "unload", at = @At("TAIL"))
	private void lg2$syncOffscreenChunkUnload(LevelChunk chunk, CallbackInfo ci) {
		if (!RendererBotClientMode.isEnabled()) {
			return;
		}
		RendererBotOffscreenWorldRenderer.onChunkUnloaded((ClientLevel) (Object) this, chunk);
	}
}
