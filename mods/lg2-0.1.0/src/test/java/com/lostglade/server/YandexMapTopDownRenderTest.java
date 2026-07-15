package com.lostglade.server;

import com.lostglade.server.map.BlockTextureRaycaster;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class YandexMapTopDownRenderTest {
	private YandexMapTopDownRenderTest() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		yandexMapTilesStayInOverworld();
		yandexMapTilesUseNeutralRendererBotPriority();
		chunkDiscoveryQueuesOnlyUnrenderedBaseTiles();
		uniformCachedBaseTileIsAdmittedForVerification();
		mapDepthReadbackDistinguishesSkyFromGeometry();
		deferredChunkDiscoveryIsReplayedAfterInitialInventory();
		pendingTileSchedulerPrefersNewTilesNearWorldCenter();
		retryingNewTileKeepsLowerPriorityWorkBlocked();
		cameraMapAdmissionSerializesAndCentersNewTiles();
		coarseLodBuildsImmediatelyFromBaseTiles();
		knownBaseCoverageIncludesEveryLodAncestor();
		knownEmptyCoverageMakesSparseLodComplete();
		completeLodTileCannotBeOverwrittenByPartial();
		truncatedTileFileIsNotTreatedAsRendered();
		tileCachePathIsBoundToItsWorld();
		queuedTileUsesCompletionTimeForRefreshAge();
		highLodBuildDoesNotScanUnboundedBaseDescendants();
		logUsesTopFace();
		grassBlockUsesTopTexture();
		redstoneWireProducesTopPixel();
		leverProducesTopPixel();
		wallLeverProjectionUsesItsAttachedSide();
		wallLeverProjectionCoversAllAttachedSides();
		ceilingLeverKeepsBodyProjection();
		buttonProducesTopPixel();
		redstoneLineKeepsTexturedProjection();
		fenceHasTransparentProjectionGaps();
		fenceGapDoesNotFallbackToOpaqueMapColor();
		coarseFenceKeepsLowerLayerVisible();
		coarseThinBlocksStayVisible();
		coarseLeavesStayVisible();
		leafLayerStaysVisibleAboveLog();
		waterIsAVisibleTransparentLayer();
		flowingWaterDoesNotCollapseToMissingBlack();
		transparentBlocksKeepTheirOwnColorWhenUnbacked();
		rotatedDisplayOverlayCoversAllQuarterTurns();
		mapVisibleItemDisplayModelsProduceTopPixels();
		serverDisplayModelUsesTopFacingPixels();
		System.out.println("Yandex map top-down render checks passed");
	}

	private static void yandexMapTilesStayInOverworld() {
		require(MonitorYandexMapsClientTileRenderer.isMapDimension(Level.OVERWORLD), "Yandex map tiles must allow overworld rendering");
		require(!MonitorYandexMapsClientTileRenderer.isMapDimension(Level.NETHER), "Yandex map tiles must not load the nether");
		require(!MonitorYandexMapsClientTileRenderer.isMapDimension(Level.END), "Yandex map tiles must not load non-overworld dimensions");
	}

	private static void yandexMapTilesUseNeutralRendererBotPriority() {
		require(MonitorYandexMapsClientTileRenderer.rendererBotMapTilePriorityScore(10) == 10, "Yandex tile requests must preserve their internal map ordering priority");
		require(!MonitorYandexMapsClientTileRenderer.rendererBotMapTileActiveView(true), "Yandex tile requests must not mark renderer-bot active view priority");
	}

	private static void chunkDiscoveryQueuesOnlyUnrenderedBaseTiles() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Class<?> tileImageClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileImage");

		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-discovery"), Level.OVERWORLD);

		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Field savedTileQueueInitialized = cacheClass.getDeclaredField("savedTileQueueInitialized");
		savedTileQueueInitialized.setAccessible(true);
		savedTileQueueInitialized.setBoolean(cache, true);

		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Constructor<?> tileImageConstructor = tileImageClass.getDeclaredConstructor(byte[].class, long.class, boolean.class);
		tileImageConstructor.setAccessible(true);
		Method cacheTile = cacheClass.getDeclaredMethod("cacheTile", tileKeyClass, tileImageClass, boolean.class, long.class);
		cacheTile.setAccessible(true);
		Object renderedKey = tileKeyConstructor.newInstance(0, 0L, 0L);
		Object renderedImage = tileImageConstructor.newInstance(solidRgbTile(0x336699), 20L, true);
		cacheTile.invoke(cache, renderedKey, renderedImage, false, 20L);

		Method markChunkDiscovered = cacheClass.getDeclaredMethod("markChunkDiscovered", ChunkPos.class, long.class);
		markChunkDiscovered.setAccessible(true);
		markChunkDiscovered.invoke(cache, new ChunkPos(0, 0), 25L);

		Field pendingBaseTilesField = cacheClass.getDeclaredField("pendingBaseTiles");
		pendingBaseTilesField.setAccessible(true);
		Object pendingBaseTiles = pendingBaseTilesField.get(cache);
		require(pendingBaseTiles instanceof Map<?, ?> pendingMap && pendingMap.size() == 3,
				"chunk discovery must queue only missing base tiles, pending=" + (pendingBaseTiles instanceof Map<?, ?> map ? map.size() : -1));

		Field dirtyAfterField = cacheClass.getDeclaredField("dirtyAfter");
		dirtyAfterField.setAccessible(true);
		Object dirtyAfter = dirtyAfterField.get(cache);
		require(dirtyAfter instanceof Map<?, ?> dirtyMap && dirtyMap.isEmpty(), "chunk discovery must not mark tiles dirty");
	}

	private static void uniformCachedBaseTileIsAdmittedForVerification() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");

		Path worldRoot = Files.createTempDirectory("lg2-yandex-uniform-cache-");
		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(worldRoot, Level.OVERWORLD);
		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Object tileKey = tileKeyConstructor.newInstance(0, 8L, -19L);
		Method tilePath = MonitorYandexMapsClientTileRenderer.class.getDeclaredMethod("tilePath", worldKeyClass, tileKeyClass);
		tilePath.setAccessible(true);
		Path tileFile = (Path) tilePath.invoke(null, worldKey, tileKey);
		Files.createDirectories(tileFile.getParent());
		Files.write(tileFile, solidRgbTile(0xAECbFF));

		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Field savedTileQueueInitialized = cacheClass.getDeclaredField("savedTileQueueInitialized");
		savedTileQueueInitialized.setAccessible(true);
		savedTileQueueInitialized.setBoolean(cache, true);
		Method queueMissingBaseTile = cacheClass.getDeclaredMethod("queueMissingBaseTile", tileKeyClass, long.class);
		queueMissingBaseTile.setAccessible(true);
		require((Boolean) queueMissingBaseTile.invoke(cache, tileKey, 1L),
				"a uniform legacy tile must be re-captured instead of being trusted as rendered");

		Field pendingBaseTiles = cacheClass.getDeclaredField("pendingBaseTiles");
		pendingBaseTiles.setAccessible(true);
		require(pendingBaseTiles.get(cache) instanceof Map<?, ?> pending && pending.size() == 1,
				"uniform legacy tile must enter the new-terrain queue for verification");
	}

	private static void mapDepthReadbackDistinguishesSkyFromGeometry() throws Exception {
		Class<?> captureClass = Class.forName("com.lostglade.client.RendererBotClientCapture");
		Method hasWrittenDepth = captureClass.getDeclaredMethod("hasWrittenDepth", ByteBuffer.class);
		hasWrittenDepth.setAccessible(true);
		ByteBuffer clearDepth = ByteBuffer.allocate(8);
		clearDepth.putInt(0, 0x3F800000);
		clearDepth.putInt(4, 0x0000803F);
		require(!(Boolean) hasWrittenDepth.invoke(null, clearDepth),
				"a clear depth attachment must not be treated as rendered map geometry");
		ByteBuffer geometryDepth = ByteBuffer.allocate(8);
		geometryDepth.putInt(0, 0x3F800000);
		geometryDepth.putInt(4, 0x3F000000);
		require((Boolean) hasWrittenDepth.invoke(null, geometryDepth),
				"a depth write must distinguish real terrain from an all-sky frame");
	}

	private static void pendingTileSchedulerPrefersNewTilesNearWorldCenter() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Class<?> tileImageClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileImage");
		Class<?> pendingBaseTileClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$PendingBaseTile");

		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-scheduler"), Level.OVERWORLD);

		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Field savedTileQueueInitialized = cacheClass.getDeclaredField("savedTileQueueInitialized");
		savedTileQueueInitialized.setAccessible(true);
		savedTileQueueInitialized.setBoolean(cache, true);

		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Constructor<?> tileImageConstructor = tileImageClass.getDeclaredConstructor(byte[].class, long.class, boolean.class);
		tileImageConstructor.setAccessible(true);
		Method cacheTile = cacheClass.getDeclaredMethod("cacheTile", tileKeyClass, tileImageClass, boolean.class, long.class);
		cacheTile.setAccessible(true);

		Object renderedOldKey = tileKeyConstructor.newInstance(0, 0L, 0L);
		Object renderedOldImage = tileImageConstructor.newInstance(solidRgbTile(0x552211), 1L, true);
		cacheTile.invoke(cache, renderedOldKey, renderedOldImage, false, 1L);
		Object renderedDirtyKey = tileKeyConstructor.newInstance(0, 2L, 0L);
		cacheTile.invoke(cache, renderedDirtyKey, renderedOldImage, false, 1L);

		Method queueLoadedStaleBaseTile = cacheClass.getDeclaredMethod("queueLoadedStaleBaseTile", tileKeyClass, long.class);
		queueLoadedStaleBaseTile.setAccessible(true);
		queueLoadedStaleBaseTile.invoke(cache, renderedOldKey, 2_000_000L);
		Method queuePendingBaseTile = cacheClass.getDeclaredMethod("queuePendingBaseTile", tileKeyClass, long.class, boolean.class);
		queuePendingBaseTile.setAccessible(true);
		queuePendingBaseTile.invoke(cache, renderedDirtyKey, 21L, false);

		Method queueMissingBaseTile = cacheClass.getDeclaredMethod("queueMissingBaseTile", tileKeyClass, long.class);
		queueMissingBaseTile.setAccessible(true);
		Object farNewKey = tileKeyConstructor.newInstance(0, 20L, 0L);
		Object nearNewKey = tileKeyConstructor.newInstance(0, 1L, 0L);
		queueMissingBaseTile.invoke(cache, farNewKey, 10L);
		queueMissingBaseTile.invoke(cache, nearNewKey, 11L);

		Method pollNextPendingBaseTile = cacheClass.getDeclaredMethod("pollNextPendingBaseTile");
		pollNextPendingBaseTile.setAccessible(true);
		Method pendingKey = pendingBaseTileClass.getDeclaredMethod("key");
		pendingKey.setAccessible(true);
		Method tileX = tileKeyClass.getDeclaredMethod("tileX");
		tileX.setAccessible(true);

		Object first = pendingKey.invoke(pollNextPendingBaseTile.invoke(cache));
		Object second = pendingKey.invoke(pollNextPendingBaseTile.invoke(cache));
		Object third = pendingKey.invoke(pollNextPendingBaseTile.invoke(cache));
		Object fourth = pendingKey.invoke(pollNextPendingBaseTile.invoke(cache));
		require(((Long) tileX.invoke(first)) == 1L, "new base tile nearest the world center must render first");
		require(((Long) tileX.invoke(second)) == 20L, "farther new base tile must render before rendered stale updates");
		require(((Long) tileX.invoke(third)) == 2L, "changed rendered base tile must refresh before age-only maintenance");
		require(((Long) tileX.invoke(fourth)) == 0L, "rendered stale base tile must wait until new tiles and changes are exhausted");
	}

	private static void deferredChunkDiscoveryIsReplayedAfterInitialInventory() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");

		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-deferred"), Level.OVERWORLD);
		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);

		Field savedTileQueueInitialized = cacheClass.getDeclaredField("savedTileQueueInitialized");
		savedTileQueueInitialized.setAccessible(true);
		awaitBooleanField(savedTileQueueInitialized, cache, true, 2_000L, "empty inventory scan must finish before testing deferred discovery");
		savedTileQueueInitialized.setBoolean(cache, false);
		Method markChunkDiscovered = cacheClass.getDeclaredMethod("markChunkDiscovered", ChunkPos.class, long.class);
		markChunkDiscovered.setAccessible(true);
		markChunkDiscovered.invoke(cache, new ChunkPos(0, 0), 1L);

		Field deferredChunkDiscoveries = cacheClass.getDeclaredField("deferredChunkDiscoveries");
		deferredChunkDiscoveries.setAccessible(true);
		require(deferredChunkDiscoveries.get(cache) instanceof Set<?> deferred && deferred.size() == 1,
				"chunk discovered during inventory must be buffered");
		Field pendingBaseTiles = cacheClass.getDeclaredField("pendingBaseTiles");
		pendingBaseTiles.setAccessible(true);
		require(pendingBaseTiles.get(cache) instanceof Map<?, ?> pending && pending.size() == 4,
				"loaded discovery must be admitted immediately instead of waiting for the MCA scan");
		Method finishInitialBaseTileInventory = cacheClass.getDeclaredMethod("finishInitialBaseTileInventory", Set.class);
		finishInitialBaseTileInventory.setAccessible(true);
		finishInitialBaseTileInventory.invoke(cache, Set.of());

		require(savedTileQueueInitialized.getBoolean(cache), "inventory gate must open after deferred discoveries are merged");
		require(pendingBaseTiles.get(cache) instanceof Map<?, ?> pending && pending.size() == 4,
				"one discovered chunk must replay all four missing base tiles");
	}

	private static void retryingNewTileKeepsLowerPriorityWorkBlocked() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Class<?> tileImageClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileImage");
		Class<?> pendingBaseTileClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$PendingBaseTile");

		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-retry"), Level.OVERWORLD);
		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Field savedTileQueueInitialized = cacheClass.getDeclaredField("savedTileQueueInitialized");
		savedTileQueueInitialized.setAccessible(true);
		savedTileQueueInitialized.setBoolean(cache, true);

		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Constructor<?> tileImageConstructor = tileImageClass.getDeclaredConstructor(byte[].class, long.class, boolean.class);
		tileImageConstructor.setAccessible(true);
		Method cacheTile = cacheClass.getDeclaredMethod("cacheTile", tileKeyClass, tileImageClass, boolean.class, long.class);
		cacheTile.setAccessible(true);
		Object changedKey = tileKeyConstructor.newInstance(0, 2L, 0L);
		cacheTile.invoke(cache, changedKey, tileImageConstructor.newInstance(solidRgbTile(0x224466), 1L, true), false, 1L);
		Method queuePendingBaseTile = cacheClass.getDeclaredMethod("queuePendingBaseTile", tileKeyClass, long.class, boolean.class);
		queuePendingBaseTile.setAccessible(true);
		queuePendingBaseTile.invoke(cache, changedKey, 2L, false);
		Object newKey = tileKeyConstructor.newInstance(0, 0L, 0L);
		Method queueMissingBaseTile = cacheClass.getDeclaredMethod("queueMissingBaseTile", tileKeyClass, long.class);
		queueMissingBaseTile.setAccessible(true);
		queueMissingBaseTile.invoke(cache, newKey, 3L);

		Method pollNextPendingBaseTile = cacheClass.getDeclaredMethod("pollNextPendingBaseTile");
		pollNextPendingBaseTile.setAccessible(true);
		Object newPending = pollNextPendingBaseTile.invoke(cache);
		Method pendingKey = pendingBaseTileClass.getDeclaredMethod("key");
		pendingKey.setAccessible(true);
		require(pendingKey.invoke(newPending).equals(newKey), "new tile must be admitted before a change");
		Method requeueFailedBaseTile = cacheClass.getDeclaredMethod("requeueFailedBaseTile", pendingBaseTileClass);
		requeueFailedBaseTile.setAccessible(true);
		requeueFailedBaseTile.invoke(cache, newPending);
		require(pollNextPendingBaseTile.invoke(cache) == null,
				"a retrying new tile must block changed work until its backoff expires");

		Field retryAfter = cacheClass.getDeclaredField("retryAfter");
		retryAfter.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<Object, Long> retries = (Map<Object, Long>) retryAfter.get(cache);
		retries.put(newKey, 0L);
		require(pendingKey.invoke(pollNextPendingBaseTile.invoke(cache)).equals(newKey),
				"new tile must re-enter admission after retry backoff");
	}

	private static void cameraMapAdmissionSerializesAndCentersNewTiles() throws Exception {
		Class<?> captureClass = Class.forName("com.lostglade.server.RendererBotCameraSystem$PendingMapTileCapture");
		Constructor<?> constructor = captureClass.getDeclaredConstructor(
				UUID.class, UUID.class, net.minecraft.server.MinecraftServer.class, UUID.class, ResourceKey.class,
				double.class, double.class, int.class, long.class, long.class, int.class, double.class,
				int.class, boolean.class, long.class, long.class, double.class, CompletableFuture.class
		);
		constructor.setAccessible(true);
		UUID botUuid = UUID.randomUUID();
		CompletableFuture<byte[]> farFuture = new CompletableFuture<>();
		Object far = constructor.newInstance(
				UUID.randomUUID(), UUID.randomUUID(), null, botUuid, Level.OVERWORLD,
				80.0D, 0.0D, 0, 10L, 0L, 128, 1.0D / 16.0D,
				2, false, 1L, 1L, 6_400.0D, farFuture
		);
		CompletableFuture<byte[]> nearFuture = new CompletableFuture<>();
		Object near = constructor.newInstance(
				UUID.randomUUID(), UUID.randomUUID(), null, botUuid, Level.OVERWORLD,
				8.0D, 0.0D, 0, 1L, 0L, 128, 1.0D / 16.0D,
				2, false, 2L, 2L, 64.0D, nearFuture
		);
		Object changed = constructor.newInstance(
				UUID.randomUUID(), UUID.randomUUID(), null, botUuid, Level.OVERWORLD,
				0.0D, 0.0D, 0, 0L, 0L, 128, 1.0D / 16.0D,
				1, false, 0L, 0L, 0.0D, new CompletableFuture<byte[]>()
		);
		Method compare = RendererBotCameraSystem.class.getDeclaredMethod("compareMapTileCapturePriority", captureClass, captureClass);
		compare.setAccessible(true);
		require((Integer) compare.invoke(null, near, far) < 0,
				"camera admission must prefer a central new tile over a farther new tile");
		require((Integer) compare.invoke(null, near, changed) < 0,
				"camera admission must prefer a new tile over an already-rendered change");

		Field pendingCaptures = RendererBotCameraSystem.class.getDeclaredField("PENDING_MAP_TILE_CAPTURES");
		pendingCaptures.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<UUID, Object> captures = (Map<UUID, Object>) pendingCaptures.get(null);
		captures.clear();
		try {
			Method requestId = captureClass.getDeclaredMethod("requestId");
			requestId.setAccessible(true);
			captures.put((UUID) requestId.invoke(far), far);
			captures.put((UUID) requestId.invoke(near), near);
			Method activeTargets = RendererBotCameraSystem.class.getDeclaredMethod("activeMapTileShadowTargets", UUID.class);
			activeTargets.setAccessible(true);
			Object active = activeTargets.invoke(null, botUuid);
			require(active instanceof List<?> list && list.size() == 1 && list.getFirst() == near,
					"only the most preferred map tile may own the shared shadow session");
			Method failExcept = RendererBotCameraSystem.class.getDeclaredMethod("failMapTileCapturesExceptBot", UUID.class, String.class);
			failExcept.setAccessible(true);
			failExcept.invoke(null, UUID.randomUUID(), "replacement bot selected");
			require(farFuture.isCompletedExceptionally() && nearFuture.isCompletedExceptionally() && captures.isEmpty(),
					"changing renderer bot without a disconnect must release all captures owned by the old bot");
		} finally {
			captures.clear();
		}
	}

	private static void coarseLodBuildsImmediatelyFromBaseTiles() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Class<?> tileImageClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileImage");

		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test"), Level.OVERWORLD);

		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);

		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Constructor<?> tileImageConstructor = tileImageClass.getDeclaredConstructor(byte[].class, long.class, boolean.class);
		tileImageConstructor.setAccessible(true);
		Method cacheTile = cacheClass.getDeclaredMethod("cacheTile", tileKeyClass, tileImageClass, boolean.class, long.class);
		cacheTile.setAccessible(true);

		for (long tileZ = 0; tileZ < 4; tileZ++) {
			for (long tileX = 0; tileX < 4; tileX++) {
				int rgb = ((int) tileX * 48 + 32) << 16 | (((int) tileZ * 48 + 32) << 8) | 96;
				Object baseKey = tileKeyConstructor.newInstance(0, tileX, tileZ);
				Object baseImage = tileImageConstructor.newInstance(solidRgbTile(rgb), 1L, true);
				cacheTile.invoke(cache, baseKey, baseImage, false, 1L);
			}
		}

		Method buildFromChildren = cacheClass.getDeclaredMethod("buildFromChildren", tileKeyClass, long.class);
		buildFromChildren.setAccessible(true);
		for (long tileZ = 0; tileZ < 2; tileZ++) {
			for (long tileX = 0; tileX < 2; tileX++) {
				Object lodOneKey = tileKeyConstructor.newInstance(1, tileX, tileZ);
				Object lodOneResult = buildFromChildren.invoke(cache, lodOneKey, 2L);
				require(lodOneResult instanceof Enum<?> result && "COMPLETE".equals(result.name()),
						"first LOD must build from its four base children");
			}
		}
		Object parentKey = tileKeyConstructor.newInstance(2, 0L, 0L);
		Object result = buildFromChildren.invoke(cache, parentKey, 2L);
		require(result instanceof Enum<?> buildResult && "COMPLETE".equals(buildResult.name()), "coarse LOD must build from ready immediate children");

		Method imageFor = cacheClass.getDeclaredMethod("imageFor", tileKeyClass);
		imageFor.setAccessible(true);
		Object parentImage = imageFor.invoke(cache, parentKey);
		Method valid = tileImageClass.getDeclaredMethod("valid");
		Method complete = tileImageClass.getDeclaredMethod("complete");
		valid.setAccessible(true);
		complete.setAccessible(true);
		require(parentImage != null && (Boolean) valid.invoke(parentImage), "coarse LOD image must be cached after recursive build");
		require((Boolean) complete.invoke(parentImage), "coarse LOD image must be complete after recursive build");
	}

	private static void knownBaseCoverageIncludesEveryLodAncestor() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-known-coverage"), Level.OVERWORLD);
		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Object baseKey = tileKeyConstructor.newInstance(0, 37L, -21L);
		Method queueMissingBaseTile = cacheClass.getDeclaredMethod("queueMissingBaseTile", tileKeyClass, long.class);
		queueMissingBaseTile.setAccessible(true);
		queueMissingBaseTile.invoke(cache, baseKey, 1L);

		Field knownLodCoverage = cacheClass.getDeclaredField("knownLodCoverage");
		knownLodCoverage.setAccessible(true);
		require(knownLodCoverage.get(cache) instanceof Set<?> known && known.contains(tileKeyConstructor.newInstance(1, 18L, -11L)),
				"known base data must index its immediate LOD ancestor");
		require(knownLodCoverage.get(cache) instanceof Set<?> known && known.contains(tileKeyConstructor.newInstance(9, 0L, -1L)),
				"known base data must index its coarse LOD ancestor without a global scan");
	}

	private static void completeLodTileCannotBeOverwrittenByPartial() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Class<?> tileImageClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileImage");
		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-complete-wins"), Level.OVERWORLD);
		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Constructor<?> tileImageConstructor = tileImageClass.getDeclaredConstructor(byte[].class, long.class, boolean.class);
		tileImageConstructor.setAccessible(true);
		Method cacheTile = cacheClass.getDeclaredMethod("cacheTile", tileKeyClass, tileImageClass, boolean.class, long.class);
		cacheTile.setAccessible(true);
		Object key = tileKeyConstructor.newInstance(3, 0L, 0L);
		cacheTile.invoke(cache, key, tileImageConstructor.newInstance(solidRgbTile(0x117744), 10L, true), false, 10L);
		cacheTile.invoke(cache, key, tileImageConstructor.newInstance(solidRgbTile(0x661133), 11L, false), false, 11L);

		Method imageFor = cacheClass.getDeclaredMethod("imageFor", tileKeyClass);
		imageFor.setAccessible(true);
		Object retained = imageFor.invoke(cache, key);
		Method complete = tileImageClass.getDeclaredMethod("complete");
		Method sample = tileImageClass.getDeclaredMethod("sample", int.class, int.class);
		complete.setAccessible(true);
		sample.setAccessible(true);
		require((Boolean) complete.invoke(retained), "a complete LOD tile must win over a racing partial build");
		require((Integer) sample.invoke(retained, 0, 0) == 0x117744, "partial LOD build must not overwrite a complete tile's pixels");
	}

	private static void knownEmptyCoverageMakesSparseLodComplete() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Class<?> tileImageClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileImage");
		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-known-empty"), Level.OVERWORLD);
		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Field savedTileQueueInitialized = cacheClass.getDeclaredField("savedTileQueueInitialized");
		savedTileQueueInitialized.setAccessible(true);
		savedTileQueueInitialized.setBoolean(cache, true);
		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Constructor<?> tileImageConstructor = tileImageClass.getDeclaredConstructor(byte[].class, long.class, boolean.class);
		tileImageConstructor.setAccessible(true);
		Method queueMissingBaseTile = cacheClass.getDeclaredMethod("queueMissingBaseTile", tileKeyClass, long.class);
		queueMissingBaseTile.setAccessible(true);
		Object knownBase = tileKeyConstructor.newInstance(0, 0L, 0L);
		queueMissingBaseTile.invoke(cache, knownBase, 1L);
		Method cacheTile = cacheClass.getDeclaredMethod("cacheTile", tileKeyClass, tileImageClass, boolean.class, long.class);
		cacheTile.setAccessible(true);
		cacheTile.invoke(cache, knownBase, tileImageConstructor.newInstance(solidRgbTile(0x4488CC), 1L, true), false, 1L);
		Method buildFromChildren = cacheClass.getDeclaredMethod("buildFromChildren", tileKeyClass, long.class);
		buildFromChildren.setAccessible(true);
		Object sparseParent = tileKeyConstructor.newInstance(1, 0L, 0L);
		Object result = buildFromChildren.invoke(cache, sparseParent, 2L);
		require(result instanceof Enum<?> buildResult && "COMPLETE".equals(buildResult.name()),
				"after the MCA inventory, absent coverage branches must be treated as known empty terrain");
	}

	private static void tileCachePathIsBoundToItsWorld() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Method tilePath = MonitorYandexMapsClientTileRenderer.class.getDeclaredMethod("tilePath", worldKeyClass, tileKeyClass);
		tilePath.setAccessible(true);
		Path worldRoot = Path.of("/tmp/lg2-yandex-cache-world-a");
		Path path = (Path) tilePath.invoke(null, worldKeyConstructor.newInstance(worldRoot, Level.OVERWORLD), tileKeyConstructor.newInstance(0, 7L, -3L));
		require(path != null && path.startsWith(worldRoot.resolve("data").resolve("lg2-yandex-map-client-tiles-v13")),
				"an asynchronous cache task must retain the root of its own world instead of a mutable global root");
	}

	private static void truncatedTileFileIsNotTreatedAsRendered() throws Exception {
		Method usableFile = MonitorYandexMapsClientTileRenderer.class.getDeclaredMethod("isStoredTileFileUsable", Path.class);
		usableFile.setAccessible(true);
		Path file = Files.createTempFile("lg2-yandex-truncated-", ".bin");
		try {
			Files.write(file, new byte[7]);
			require(!(Boolean) usableFile.invoke(null, file),
					"a truncated cached tile must return to the render queue rather than becoming a permanent hole");
		} finally {
			Files.deleteIfExists(file);
		}
	}

	private static void queuedTileUsesCompletionTimeForRefreshAge() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");
		Class<?> tileImageClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileImage");
		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test-queue-age"), Level.OVERWORLD);
		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);
		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Constructor<?> tileImageConstructor = tileImageClass.getDeclaredConstructor(byte[].class, long.class, long.class, boolean.class);
		tileImageConstructor.setAccessible(true);
		long now = System.currentTimeMillis();
		Object image = tileImageConstructor.newInstance(solidRgbTile(0x225577), 1L, now, true);
		Method isOutdated = cacheClass.getDeclaredMethod("isOutdated", tileKeyClass, tileImageClass, long.class);
		isOutdated.setAccessible(true);
		require(!(Boolean) isOutdated.invoke(cache, tileKeyConstructor.newInstance(0, 0L, 0L), image, now + 1L),
				"a tile that waited in the renderer queue must age from completion, not request enqueue time");
	}

	private static void highLodBuildDoesNotScanUnboundedBaseDescendants() throws Exception {
		Class<?> worldKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$WorldCacheKey");
		Class<?> cacheClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$DimensionTileCache");
		Class<?> tileKeyClass = Class.forName("com.lostglade.server.MonitorYandexMapsClientTileRenderer$TileKey");

		Constructor<?> worldKeyConstructor = worldKeyClass.getDeclaredConstructor(Path.class, ResourceKey.class);
		worldKeyConstructor.setAccessible(true);
		Object worldKey = worldKeyConstructor.newInstance(Path.of("/tmp/lg2-yandex-test"), Level.OVERWORLD);

		Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructor(worldKeyClass);
		cacheConstructor.setAccessible(true);
		Object cache = cacheConstructor.newInstance(worldKey);

		Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, long.class, long.class);
		tileKeyConstructor.setAccessible(true);
		Object highLodKey = tileKeyConstructor.newInstance(9, 0L, 0L);

		Method buildFromChildren = cacheClass.getDeclaredMethod("buildFromChildren", tileKeyClass, long.class);
		buildFromChildren.setAccessible(true);
		Object result = buildFromChildren.invoke(cache, highLodKey, 2L);
		require(result instanceof Enum<?> buildResult && "NONE".equals(buildResult.name()), "empty high LOD should not build from missing descendants");

		Field missingTilesField = cacheClass.getDeclaredField("missingTiles");
		missingTilesField.setAccessible(true);
		Object missingTiles = missingTilesField.get(cache);
		require(missingTiles instanceof Set<?> missingSet && missingSet.size() == 4, "empty high LOD must only probe immediate children, missing=" + (missingTiles instanceof Set<?> set ? set.size() : -1));
	}

	private static void logUsesTopFace() {
		BlockTextureRaycaster.BlockTraceResult result = traceTop(Blocks.OAK_LOG.defaultBlockState(), 0.5D, 0.5D);
		require(result != null, "oak log top-down trace must hit the model");
		require(result.face() == Direction.UP, "oak log must render its top face from top-down view, not side bark");
	}

	private static void grassBlockUsesTopTexture() throws Exception {
		int argb = sampleState(Blocks.GRASS_BLOCK.defaultBlockState(), 0.5D, 0.5D);
		int red = (argb >> 16) & 0xFF;
		int green = (argb >> 8) & 0xFF;
		int blue = argb & 0xFF;
		require(((argb >>> 24) & 0xFF) > 8, "grass block must render a visible top-down pixel");
		require(green > red && green > blue, "grass block top-down pixel must be grass-tinted, not brown dirt");
	}

	private static void redstoneWireProducesTopPixel() {
		BlockTextureRaycaster.BlockTraceResult result = traceTop(Blocks.REDSTONE_WIRE.defaultBlockState(), 0.5D, 0.5D);
		require(result != null && result.alpha() > 8, "redstone wire must produce a visible top-down pixel");
	}

	private static void leverProducesTopPixel() {
		BlockState state = Blocks.LEVER.defaultBlockState()
				.setValue(LeverBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH);
		BlockTextureRaycaster.BlockTraceResult result = traceTop(state, 0.5D, 0.5D);
		require(result != null && result.alpha() > 8, "floor lever must produce a visible top-down pixel");
	}

	private static void wallLeverProjectionUsesItsAttachedSide() {
		BlockState westWall = Blocks.LEVER.defaultBlockState()
				.setValue(LeverBlock.FACE, AttachFace.WALL)
				.setValue(LeverBlock.FACING, Direction.WEST);
		BlockState northWall = Blocks.LEVER.defaultBlockState()
				.setValue(LeverBlock.FACE, AttachFace.WALL)
				.setValue(LeverBlock.FACING, Direction.NORTH);
		int westStrip = geometryHitsInArea(westWall, 20, 0.0D, 0.32D, 0.0D, 1.0D);
		int eastStrip = geometryHitsInArea(westWall, 20, 0.68D, 1.0D, 0.0D, 1.0D);
		int northStrip = geometryHitsInArea(northWall, 20, 0.0D, 1.0D, 0.0D, 0.32D);
		int southStrip = geometryHitsInArea(northWall, 20, 0.0D, 1.0D, 0.68D, 1.0D);
		require(eastStrip > westStrip, "wall lever facing west must project on its vanilla support side, west=" + westStrip + " east=" + eastStrip);
		require(southStrip > northStrip, "wall lever facing north must project on its vanilla support side, north=" + northStrip + " south=" + southStrip);
	}

	private static void wallLeverProjectionCoversAllAttachedSides() {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState state = Blocks.LEVER.defaultBlockState()
					.setValue(LeverBlock.FACE, AttachFace.WALL)
					.setValue(LeverBlock.FACING, direction);
			int facingHits = stripHits(state, direction);
			int supportHits = stripHits(state, direction.getOpposite());
			require(supportHits > facingHits,
					"wall lever facing " + direction + " must keep its top-down projection on the vanilla support side, support="
							+ supportHits + " facing=" + facingHits);
		}
	}

	private static void ceilingLeverKeepsBodyProjection() {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockState state = Blocks.LEVER.defaultBlockState()
					.setValue(LeverBlock.FACE, AttachFace.CEILING)
					.setValue(LeverBlock.FACING, direction);
			int totalHits = geometryHits(state, 28);
			int bodyHits = geometryHitsInArea(state, 28, 0.28D, 0.72D, 0.28D, 0.72D);
			require(totalHits > 40, "ceiling lever facing " + direction + " must keep visible top-down geometry, hits=" + totalHits);
			require(bodyHits > 10, "ceiling lever facing " + direction + " must keep its body/base visible, body=" + bodyHits);
		}
	}

	private static void buttonProducesTopPixel() {
		BlockState state = Blocks.STONE_BUTTON.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		BlockTextureRaycaster.BlockTraceResult result = traceTop(state, 0.5D, 0.5D);
		require(result != null && result.alpha() > 8, "floor button must produce a visible top-down pixel");
	}

	private static void redstoneLineKeepsTexturedProjection() throws Exception {
		BlockState state = Blocks.REDSTONE_WIRE.defaultBlockState()
				.setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
				.setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE);
		int center = sampleState(state, 0.5D, 0.5D);
		int corner = sampleState(state, 0.08D, 0.08D);
		require(((center >>> 24) & 0xFF) > 8, "redstone line center must stay visible");
		require(((corner >>> 24) & 0xFF) <= 8, "redstone line corners must stay transparent instead of becoming a flat fill");
	}

	private static void fenceHasTransparentProjectionGaps() {
		BlockState state = Blocks.OAK_FENCE.defaultBlockState();
		BlockTextureRaycaster.BlockTraceResult center = traceTop(state, 0.5D, 0.5D);
		BlockTextureRaycaster.BlockTraceResult corner = traceTop(state, 0.08D, 0.08D);
		require(center != null && center.alpha() > 8, "fence post must produce a top-down pixel in the center");
		require(corner == null || corner.alpha() <= 8, "fence corner must stay transparent so lower blocks can show through");
	}

	private static void fenceGapDoesNotFallbackToOpaqueMapColor() throws Exception {
		int argb = sampleState(Blocks.OAK_FENCE.defaultBlockState(), 0.08D, 0.08D);
		require(((argb >>> 24) & 0xFF) <= 8, "empty fence projection pixels must not fall back to an opaque map-color fill");
	}

	private static void coarseFenceKeepsLowerLayerVisible() throws Exception {
		int argb = sampleCoveredState(Blocks.OAK_FENCE.defaultBlockState());
		int alpha = (argb >>> 24) & 0xFF;
		require(alpha > 8, "coarse fence LOD must keep the fence visible");
		require(alpha < 245, "coarse fence LOD must not become an opaque fill over lower blocks");
	}

	private static void coarseThinBlocksStayVisible() throws Exception {
		BlockState lever = Blocks.LEVER.defaultBlockState()
				.setValue(LeverBlock.FACE, AttachFace.FLOOR)
				.setValue(LeverBlock.FACING, Direction.NORTH);
		BlockState button = Blocks.STONE_BUTTON.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		require(((sampleCoveredState(Blocks.REDSTONE_WIRE.defaultBlockState()) >>> 24) & 0xFF) > 8, "coarse redstone LOD must stay visible");
		require(((sampleCoveredState(lever) >>> 24) & 0xFF) > 8, "coarse lever LOD must stay visible");
		require(((sampleCoveredState(button) >>> 24) & 0xFF) > 8, "coarse button LOD must stay visible");
	}

	private static void coarseLeavesStayVisible() throws Exception {
		int argb = sampleCoveredState(Blocks.OAK_LEAVES.defaultBlockState());
		int alpha = (argb >>> 24) & 0xFF;
		int green = (argb >> 8) & 0xFF;
		int red = (argb >> 16) & 0xFF;
		int geometryHits = geometryHits(Blocks.OAK_LEAVES.defaultBlockState(), 12);
		require(alpha > 32, "coarse leaves LOD must keep foliage visible instead of dropping to air");
		require(geometryHits > 120, "oak leaves must have almost full top-down model geometry coverage, hits=" + geometryHits);
		require(green >= red, "coarse leaves LOD must stay foliage-colored");
	}

	private static void leafLayerStaysVisibleAboveLog() throws Exception {
		int rgb = colorForLayers(
				List.of(
						surfaceLayer(Blocks.OAK_LEAVES.defaultBlockState(), 65, "minecraft:oak_leaves[persistent=false]"),
						surfaceLayer(Blocks.OAK_LOG.defaultBlockState(), 64, "minecraft:oak_log[axis=y]")
				),
				2.0D
		);
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		require(green >= red, "leaf layer over a log must not collapse into bare brown trunk color: rgb=" + Integer.toHexString(rgb));
	}

	private static void waterIsAVisibleTransparentLayer() throws Exception {
		Method sampleStateArgb = MonitorYandexMapsBlueMapRenderer.class.getDeclaredMethod(
				"sampleStateArgb",
				BlockState.class,
				int.class,
				int.class,
				int.class,
				double.class,
				double.class,
				double.class
		);
		sampleStateArgb.setAccessible(true);
		int argb = (Integer) sampleStateArgb.invoke(
				null,
				Blocks.WATER.defaultBlockState(),
				0,
				64,
				0,
				0.5D,
				0.5D,
				0.25D
		);
		int alpha = (argb >>> 24) & 0xFF;
		require(alpha > 8 && alpha < 255, "water must be visible but transparent enough to composite lower layers");
	}

	private static void flowingWaterDoesNotCollapseToMissingBlack() throws Exception {
		BlockState flowingWater = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7);
		int rgb = colorForSingleLayer(flowingWater, "minecraft:water[level=7]");
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		require(blue > red && blue > green / 2, "unbacked flowing water must stay water-colored, not black/missing");
		require(red + green + blue > 80, "unbacked flowing water must not collapse to the dark missing color");
	}

	private static void transparentBlocksKeepTheirOwnColorWhenUnbacked() throws Exception {
		int slime = colorForSingleLayer(Blocks.SLIME_BLOCK.defaultBlockState(), "minecraft:slime_block");
		int fence = colorForSingleLayer(Blocks.OAK_FENCE.defaultBlockState(), "minecraft:oak_fence");
		require(((slime >> 8) & 0xFF) > ((slime >> 16) & 0xFF), "unbacked slime must keep its green transparent color");
		require(colorDistance(fence, 0x18242B) > 20, "unbacked fence coverage must not become the dark missing color");
	}

	private static void rotatedDisplayOverlayCoversAllQuarterTurns() throws Exception {
		for (double yaw : new double[]{0.0D, Math.PI * 0.5D, Math.PI, Math.PI * 1.5D}) {
			int painted = paintOverlayPixelCount(Identifier.fromNamespaceAndPath("lg2", "item/monitor_display"), yaw);
			require(painted > 0, "monitor item display overlay must paint pixels at yaw " + yaw);
		}
	}

	private static void mapVisibleItemDisplayModelsProduceTopPixels() {
		requireModelTopPixel(Identifier.fromNamespaceAndPath("lg2", "item/monitor_display"), "monitor item display");
		requireModelTopPixel(Identifier.fromNamespaceAndPath("lg2", "item/server"), "server item display");
		requireModelTopPixel(Identifier.fromNamespaceAndPath("lg2", "item/exit_sign"), "exit sign item display");
	}

	private static void serverDisplayModelUsesTopFacingPixels() {
		Identifier serverModel = Identifier.fromNamespaceAndPath("lg2", "item/server");
		double[] samples = {0.18D, 0.32D, 0.5D, 0.68D, 0.82D};
		for (double sampleX : samples) {
			for (double sampleZ : samples) {
				BlockTextureRaycaster.BlockTraceResult result = BlockTextureRaycaster.traceModelTopDownNormalized(
						serverModel,
						sampleX,
						sampleZ,
						new int[]{-1, -1, -1, -1}
				);
				if (result != null && result.alpha() > 8 && result.face() == Direction.UP) {
					return;
				}
			}
		}
		throw new AssertionError("server item display must expose real upward-facing model pixels on the map");
	}

	private static void requireModelTopPixel(Identifier modelId, String label) {
		double[] samples = {0.25D, 0.5D, 0.75D};
		for (double sampleX : samples) {
			for (double sampleZ : samples) {
				BlockTextureRaycaster.BlockTraceResult result = BlockTextureRaycaster.traceModelTopDownNormalized(
						modelId,
						sampleX,
						sampleZ,
						new int[]{-1, -1, -1, -1}
				);
				if (result != null && result.alpha() > 8) {
					return;
				}
			}
		}
		throw new AssertionError(label + " model must produce at least one top-down pixel");
	}

	private static BlockTextureRaycaster.BlockTraceResult traceTop(BlockState state, double fracX, double fracZ) {
		return BlockTextureRaycaster.traceTopDown(
				state,
				BlockPos.ZERO,
				new Vec3(fracX, 1.999D, fracZ),
				new Vec3(0.0D, -1.0D, 0.0D),
				new int[]{-1, -1, -1, -1}
		);
	}

	private static byte[] solidRgbTile(int rgb) {
		byte[] pixels = new byte[MonitorScreenSystem.MAP_SIZE * MonitorScreenSystem.MAP_SIZE * 3];
		for (int offset = 0; offset + 2 < pixels.length; offset += 3) {
			pixels[offset] = (byte) ((rgb >> 16) & 0xFF);
			pixels[offset + 1] = (byte) ((rgb >> 8) & 0xFF);
			pixels[offset + 2] = (byte) (rgb & 0xFF);
		}
		return pixels;
	}

	private static int sampleState(BlockState state, double fracX, double fracZ) throws Exception {
		Method sampleStateArgb = MonitorYandexMapsBlueMapRenderer.class.getDeclaredMethod(
				"sampleStateArgb",
				BlockState.class,
				int.class,
				int.class,
				int.class,
				double.class,
				double.class,
				double.class
		);
		sampleStateArgb.setAccessible(true);
		return (Integer) sampleStateArgb.invoke(null, state, 0, 64, 0, fracX, fracZ, 0.25D);
	}

	private static int sampleCoveredState(BlockState state) throws Exception {
		Method sampleCoveredStateArgb = MonitorYandexMapsBlueMapRenderer.class.getDeclaredMethod(
				"sampleCoveredStateArgb",
				BlockState.class,
				int.class,
				int.class,
				int.class,
				double.class
		);
		sampleCoveredStateArgb.setAccessible(true);
		return (Integer) sampleCoveredStateArgb.invoke(null, state, 0, 64, 0, 2.0D);
	}

	private static int colorForSingleLayer(BlockState state, String cacheKey) throws Exception {
		return colorForLayers(List.of(surfaceLayer(state, 64, cacheKey)), 2.0D);
	}

	private static int colorForLayers(List<Object> layers, double blocksPerPixel) throws Exception {
		Method colorForLayers = MonitorYandexMapsBlueMapRenderer.class.getDeclaredMethod(
				"colorForLayers",
				List.class,
				int.class,
				int.class,
				double.class,
				double.class,
				double.class
		);
		colorForLayers.setAccessible(true);
		return (Integer) colorForLayers.invoke(null, layers, 0, 0, 0.5D, 0.5D, blocksPerPixel);
	}

	private static Object surfaceLayer(BlockState state, int y, String cacheKey) throws Exception {
		Class<?> surfaceLayerClass = Class.forName("com.lostglade.server.MonitorYandexMapsBlueMapRenderer$SurfaceLayer");
		Constructor<?> constructor = surfaceLayerClass.getDeclaredConstructor(BlockState.class, int.class, String.class);
		constructor.setAccessible(true);
		return constructor.newInstance(state, y, cacheKey);
	}

	private static int paintOverlayPixelCount(Identifier modelId, double yawRadians) throws Exception {
		Class<?> overlayClass = Class.forName("com.lostglade.server.MonitorYandexMapsBlueMapRenderer$DisplayOverlay");
		Constructor<?> constructor = overlayClass.getDeclaredConstructor(double.class, double.class, double.class, double.class, double.class, Identifier.class);
		constructor.setAccessible(true);
		Object overlay = constructor.newInstance(3.2D, 3.2D, 1.15D, 0.16D, yawRadians, modelId);
		Method drawDisplayModelOverlay = MonitorYandexMapsBlueMapRenderer.class.getDeclaredMethod(
				"drawDisplayModelOverlay",
				overlayClass,
				double.class,
				double.class,
				double.class,
				int[].class
		);
		drawDisplayModelOverlay.setAccessible(true);
		int[] pixels = new int[128 * 128];
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = 0x18242B;
		}
		drawDisplayModelOverlay.invoke(null, overlay, 0.0D, 0.0D, 0.05D, pixels);
		int painted = 0;
		for (int pixel : pixels) {
			if ((pixel & 0xFFFFFF) != 0x18242B) {
				painted++;
			}
		}
		return painted;
	}

	private static int geometryHits(BlockState state, int samplesPerAxis) {
		int hits = 0;
		for (int z = 0; z < samplesPerAxis; z++) {
			double fracZ = (z + 0.5D) / samplesPerAxis;
			for (int x = 0; x < samplesPerAxis; x++) {
				double fracX = (x + 0.5D) / samplesPerAxis;
				if (BlockTextureRaycaster.hitsTopDownGeometry(
						state,
						BlockPos.ZERO,
						new Vec3(fracX, 1.999D, fracZ),
						new Vec3(0.0D, -1.0D, 0.0D)
				)) {
					hits++;
				}
			}
		}
		return hits;
	}

	private static int geometryHitsInArea(BlockState state, int samplesPerAxis, double minX, double maxX, double minZ, double maxZ) {
		int hits = 0;
		for (int z = 0; z < samplesPerAxis; z++) {
			double fracZ = minZ + (z + 0.5D) / samplesPerAxis * (maxZ - minZ);
			for (int x = 0; x < samplesPerAxis; x++) {
				double fracX = minX + (x + 0.5D) / samplesPerAxis * (maxX - minX);
				if (BlockTextureRaycaster.hitsTopDownGeometry(
						state,
						BlockPos.ZERO,
						new Vec3(fracX, 1.999D, fracZ),
						new Vec3(0.0D, -1.0D, 0.0D)
				)) {
					hits++;
				}
			}
		}
		return hits;
	}

	private static int stripHits(BlockState state, Direction direction) {
		return switch (direction) {
			case NORTH -> geometryHitsInArea(state, 24, 0.0D, 1.0D, 0.0D, 0.34D);
			case SOUTH -> geometryHitsInArea(state, 24, 0.0D, 1.0D, 0.66D, 1.0D);
			case WEST -> geometryHitsInArea(state, 24, 0.0D, 0.34D, 0.0D, 1.0D);
			case EAST -> geometryHitsInArea(state, 24, 0.66D, 1.0D, 0.0D, 1.0D);
			default -> 0;
		};
	}

	private static int colorDistance(int left, int right) {
		int dr = ((left >> 16) & 0xFF) - ((right >> 16) & 0xFF);
		int dg = ((left >> 8) & 0xFF) - ((right >> 8) & 0xFF);
		int db = (left & 0xFF) - (right & 0xFF);
		return Math.abs(dr) + Math.abs(dg) + Math.abs(db);
	}

	private static void awaitBooleanField(Field field, Object target, boolean expected, long timeoutMillis, String message) throws Exception {
		long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
		while (System.nanoTime() < deadline) {
			if (field.getBoolean(target) == expected) {
				return;
			}
			Thread.sleep(2L);
		}
		require(field.getBoolean(target) == expected, message);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
