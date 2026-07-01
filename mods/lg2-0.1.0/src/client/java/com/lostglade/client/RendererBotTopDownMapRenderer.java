package com.lostglade.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;
import java.util.function.Consumer;

final class RendererBotTopDownMapRenderer {
	private static final double MIN_BLOCKS_PER_PIXEL = 1.0D / 16.0D;
	private static final double MAX_BLOCKS_PER_PIXEL = 512.0D;
	private static final double TOP_DOWN_CAMERA_HEADROOM_BLOCKS = 16.0D;

	private RendererBotTopDownMapRenderer() {
	}

	static void clearCaches() {
	}

	static void invalidateBlock(ClientLevel level, BlockPos pos) {
	}

	static void invalidateChunk(ClientLevel level, int chunkX, int chunkZ) {
	}

	static boolean hasRequiredChunks(Minecraft client, TileRequest request) {
		ClientLevel level = resolveLevel(client, request);
		if (level == null || request == null) {
			return false;
		}
		double blocksPerPixel = safeBlocksPerPixel(request.blocksPerPixel());
		double halfWidth = request.width() * blocksPerPixel * 0.5D;
		double halfHeight = request.height() * blocksPerPixel * 0.5D;
		int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(request.centerX() - halfWidth));
		int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(request.centerX() + halfWidth - 1.0E-6D));
		int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(request.centerZ() - halfHeight));
		int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(request.centerZ() + halfHeight - 1.0E-6D));
		for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				if (level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}
		return true;
	}

	static boolean renderToTarget(Minecraft client, TileRequest request, Consumer<RenderTarget> renderTargetConsumer) {
		ClientLevel level = resolveLevel(client, request);
		if (level == null
				|| request == null
				|| renderTargetConsumer == null
				|| request.width() <= 0
				|| request.height() <= 0
				|| request.blocksPerPixel() <= 0.0D) {
			return false;
		}
		int width = Math.max(1, request.width());
		int height = Math.max(1, request.height());
		double blocksPerPixel = safeBlocksPerPixel(request.blocksPerPixel());
		double worldWidth = width * blocksPerPixel;
		double worldHeight = height * blocksPerPixel;
		double cameraY = cameraYForTile(level, request, blocksPerPixel);
		return RendererBotOffscreenWorldRenderer.renderToTarget(
				client,
				new RendererBotOffscreenWorldRenderer.RenderRequest(
						request.sessionId(),
						request.dimensionId(),
						null,
						request.centerX(),
						cameraY,
						request.centerZ(),
						0.0F,
						90.0F,
						70,
						width,
						height,
						true,
						true,
						worldWidth,
						worldHeight
				),
				renderTargetConsumer
		);
	}

	private static ClientLevel resolveLevel(Minecraft client, TileRequest request) {
		if (client == null || request == null) {
			return null;
		}
		RendererBotShadowWorldManager.ShadowRenderSession session = RendererBotShadowWorldManager.resolveRenderSession(request.sessionId());
		return session == null ? null : session.level();
	}

	private static double safeBlocksPerPixel(double blocksPerPixel) {
		return Mth.clamp(blocksPerPixel, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
	}

	private static double cameraYForTile(ClientLevel level, TileRequest request, double blocksPerPixel) {
		if (level == null || request == null) {
			return 256.0D;
		}
		double halfWidth = request.width() * blocksPerPixel * 0.5D;
		double halfHeight = request.height() * blocksPerPixel * 0.5D;
		int minBlockX = Mth.floor(request.centerX() - halfWidth);
		int maxBlockX = Mth.floor(request.centerX() + halfWidth - 1.0E-6D);
		int minBlockZ = Mth.floor(request.centerZ() - halfHeight);
		int maxBlockZ = Mth.floor(request.centerZ() + halfHeight - 1.0E-6D);
		int minChunkX = SectionPos.blockToSectionCoord(minBlockX);
		int maxChunkX = SectionPos.blockToSectionCoord(maxBlockX);
		int minChunkZ = SectionPos.blockToSectionCoord(minBlockZ);
		int maxChunkZ = SectionPos.blockToSectionCoord(maxBlockZ);
		double highestY = level.getMinY();
		for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				int fromX = Math.max(minBlockX, SectionPos.sectionToBlockCoord(chunkX));
				int toX = Math.min(maxBlockX, SectionPos.sectionToBlockCoord(chunkX) + 15);
				int fromZ = Math.max(minBlockZ, SectionPos.sectionToBlockCoord(chunkZ));
				int toZ = Math.min(maxBlockZ, SectionPos.sectionToBlockCoord(chunkZ) + 15);
				for (int z = fromZ; z <= toZ; z++) {
					for (int x = fromX; x <= toX; x++) {
						highestY = Math.max(highestY, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
					}
				}
			}
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == null) {
				continue;
			}
			double entityX = entity.getX();
			double entityZ = entity.getZ();
			if (entityX >= minBlockX - 1.0D
					&& entityX <= maxBlockX + 1.0D
					&& entityZ >= minBlockZ - 1.0D
					&& entityZ <= maxBlockZ + 1.0D) {
				highestY = Math.max(highestY, entity.getBoundingBox().maxY);
			}
		}
		double minimumY = level.getMinY() + 1.0D;
		double maximumY = level.getMaxY() + TOP_DOWN_CAMERA_HEADROOM_BLOCKS;
		return Mth.clamp(highestY + TOP_DOWN_CAMERA_HEADROOM_BLOCKS, minimumY, maximumY);
	}

	record TileRequest(UUID sessionId, String dimensionId, double centerX, double centerZ, int width, int height, double blocksPerPixel) {
	}
}
