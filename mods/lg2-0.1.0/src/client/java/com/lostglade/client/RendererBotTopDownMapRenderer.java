package com.lostglade.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
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
	private static final float TOP_DOWN_YAW = 180.0F;
	private static final float TOP_DOWN_PITCH = 90.0F;
	private static final int TOP_DOWN_TILE_RENDER_SCALE = Mth.clamp(Integer.getInteger("lg2.rendererBotMapTileRenderScale", 4), 1, 8);
	private static final int TOP_DOWN_TILE_MAX_RENDER_PIXELS = Math.max(
			16_384,
			Integer.getInteger("lg2.rendererBotMapTileMaxRenderPixels", 1_048_576)
	);

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

	/**
	 * A chunk packet being present does not mean that its section mesh has made
	 * it through the client's asynchronous renderer yet.  Capturing in that
	 * small window produces a perfectly valid screenshot of just the sky.  For
	 * a top-down tile, wait until each non-air surface section in the requested
	 * footprint is compiled and visible in the shadow renderer.
	 */
	static boolean isTerrainReadyForCapture(Minecraft client, TileRequest request) {
		return terrainReadiness(client, request) != TerrainReadiness.NOT_READY;
	}

	/**
	 * Used after a capture to distinguish an expected empty/void view from a
	 * frame in which real terrain did not reach the render target at all.
	 */
	static boolean expectsTerrain(Minecraft client, TileRequest request) {
		TerrainReadiness readiness = terrainReadiness(client, request);
		return readiness == TerrainReadiness.READY || readiness == TerrainReadiness.NOT_READY;
	}

	static void rebuildTerrain(Minecraft client, TileRequest request) {
		if (client == null || request == null) {
			return;
		}
		RendererBotShadowWorldManager.ShadowRenderSession session = RendererBotShadowWorldManager.resolveRenderSession(request.sessionId());
		if (session == null || session.levelRenderer() == null) {
			return;
		}
		// This is intentionally reserved for a capture whose depth buffer proves
		// that the world geometry was absent.  It is much cheaper than accepting a
		// corrupt map tile and leaves normal map capture on the incremental path.
		session.levelRenderer().allChanged();
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
		double centerX = snapCenterToPixelGrid(request.centerX(), width, blocksPerPixel);
		double centerZ = snapCenterToPixelGrid(request.centerZ(), height, blocksPerPixel);
		double worldWidth = width * blocksPerPixel;
		double worldHeight = height * blocksPerPixel;
		double cameraY = cameraYForTile(level, centerX, centerZ, width, height, blocksPerPixel);
		int renderScale = renderScaleForTile(width, height);
		int renderWidth = Math.max(1, width * renderScale);
		int renderHeight = Math.max(1, height * renderScale);
		return RendererBotOffscreenWorldRenderer.renderToTarget(
				client,
				new RendererBotOffscreenWorldRenderer.RenderRequest(
						request.sessionId(),
						request.dimensionId(),
						null,
						centerX,
						cameraY,
						centerZ,
						TOP_DOWN_YAW,
						TOP_DOWN_PITCH,
						70,
						renderWidth,
						renderHeight,
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

	private static TerrainReadiness terrainReadiness(Minecraft client, TileRequest request) {
		if (client == null || request == null) {
			return TerrainReadiness.NOT_READY;
		}
		RendererBotShadowWorldManager.ShadowRenderSession session = RendererBotShadowWorldManager.resolveRenderSession(request.sessionId());
		ClientLevel level = session == null ? null : session.level();
		LevelRenderer levelRenderer = session == null ? null : session.levelRenderer();
		if (level == null || levelRenderer == null) {
			return TerrainReadiness.NOT_READY;
		}
		double blocksPerPixel = safeBlocksPerPixel(request.blocksPerPixel());
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
		boolean foundSurface = false;
		for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
			for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
				LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk == null) {
					return TerrainReadiness.NOT_READY;
				}
				int fromX = Math.max(minBlockX, SectionPos.sectionToBlockCoord(chunkX));
				int toX = Math.min(maxBlockX, SectionPos.sectionToBlockCoord(chunkX) + 15);
				int fromZ = Math.max(minBlockZ, SectionPos.sectionToBlockCoord(chunkZ));
				int toZ = Math.min(maxBlockZ, SectionPos.sectionToBlockCoord(chunkZ) + 15);
				for (int z = fromZ; z <= toZ; z++) {
					for (int x = fromX; x <= toX; x++) {
						int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) - 1;
						if (surfaceY < level.getMinY() || surfaceY >= level.getMaxY()) {
							continue;
						}
						BlockPos surface = new BlockPos(x, surfaceY, z);
						if (level.getBlockState(surface).isAir()) {
							continue;
						}
						foundSurface = true;
						if (!levelRenderer.isSectionCompiledAndVisible(surface)) {
							return TerrainReadiness.NOT_READY;
						}
					}
				}
			}
		}
		return foundSurface ? TerrainReadiness.READY : TerrainReadiness.EMPTY;
	}

	private static double safeBlocksPerPixel(double blocksPerPixel) {
		return Mth.clamp(blocksPerPixel, MIN_BLOCKS_PER_PIXEL, MAX_BLOCKS_PER_PIXEL);
	}

	private static double snapCenterToPixelGrid(double center, int pixels, double blocksPerPixel) {
		if (!Double.isFinite(center) || !Double.isFinite(blocksPerPixel) || blocksPerPixel <= 0.0D) {
			return center;
		}
		double worldSize = pixels * blocksPerPixel;
		double left = center - worldSize * 0.5D;
		return Math.rint(left / blocksPerPixel) * blocksPerPixel + worldSize * 0.5D;
	}

	private static int renderScaleForTile(int width, int height) {
		int scale = TOP_DOWN_TILE_RENDER_SCALE;
		while (scale > 1 && (long) width * scale * height * scale > TOP_DOWN_TILE_MAX_RENDER_PIXELS) {
			scale--;
		}
		return Math.max(1, scale);
	}

	private static double cameraYForTile(ClientLevel level, double centerX, double centerZ, int width, int height, double blocksPerPixel) {
		if (level == null) {
			return 256.0D;
		}
		double halfWidth = width * blocksPerPixel * 0.5D;
		double halfHeight = height * blocksPerPixel * 0.5D;
		int minBlockX = Mth.floor(centerX - halfWidth);
		int maxBlockX = Mth.floor(centerX + halfWidth - 1.0E-6D);
		int minBlockZ = Mth.floor(centerZ - halfHeight);
		int maxBlockZ = Mth.floor(centerZ + halfHeight - 1.0E-6D);
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

	private enum TerrainReadiness {
		NOT_READY,
		EMPTY,
		READY
	}
}
