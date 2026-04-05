package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.mixin.client.CameraPositionInvoker;
import com.lostglade.mixin.client.GameRendererRenderLevelInvoker;
import com.lostglade.mixin.client.LevelRendererRenderStateAccessor;
import com.lostglade.mixin.client.MinecraftMainRenderTargetAccessor;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class RendererBotOffscreenWorldRenderer {
	private static final Object LOCK = new Object();
	private static final double STATIC_CAMERA_EYE_HEIGHT = 1.62D;
	private static final int MIN_READY_CHUNK_RADIUS = 1;
	private static final long RENDERER_CACHE_TTL_MS = Long.getLong("lg2.rendererBotRendererCacheTtlMs", 15_000L);
	private static final Map<RendererKey, CachedLevelRenderer> CACHED_RENDERERS = new HashMap<>();
	private static boolean offscreenRenderActive;

	private RendererBotOffscreenWorldRenderer() {
	}

	public static boolean isOffscreenRenderActive() {
		synchronized (LOCK) {
			return offscreenRenderActive;
		}
	}

	public static void clearCaches() {
		synchronized (LOCK) {
			closeAllCachedRenderersLocked();
		}
	}

	public static void onBlockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
		if (level == null || pos == null || oldState == null || newState == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().blockChanged(level, pos, oldState, newState, flags)
			);
		}
	}

	public static void onBlockDirty(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
		if (level == null || pos == null || oldState == null || newState == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().setBlockDirty(pos, oldState, newState)
			);
		}
	}

	public static void onSectionDirtyWithNeighbors(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
		if (level == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().setSectionDirtyWithNeighbors(sectionX, sectionY, sectionZ)
			);
		}
	}

	public static void onSectionDirty(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
		if (level == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().setSectionDirty(sectionX, sectionY, sectionZ)
			);
		}
	}

	public static void onSectionRangeDirty(
			ClientLevel level,
			int minSectionX,
			int minSectionY,
			int minSectionZ,
			int maxSectionX,
			int maxSectionY,
			int maxSectionZ
	) {
		if (level == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().setSectionRangeDirty(
							minSectionX,
							minSectionY,
							minSectionZ,
							maxSectionX,
							maxSectionY,
							maxSectionZ
					)
			);
		}
	}

	public static void onSectionBecomingNonEmpty(ClientLevel level, long sectionPos) {
		if (level == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().onSectionBecomingNonEmpty(sectionPos)
			);
		}
	}

	public static void onDestroyBlockProgress(ClientLevel level, int breakerId, BlockPos pos, int progress) {
		if (level == null || pos == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().destroyBlockProgress(breakerId, pos, progress)
			);
		}
	}

	public static void onChunkUnloaded(ClientLevel level, LevelChunk chunk) {
		if (level == null || chunk == null || chunk.getSections().length == 0) {
			return;
		}
		ChunkPos pos = chunk.getPos();
		int minSectionY = chunk.getSectionYFromSectionIndex(0);
		int maxSectionY = chunk.getSectionYFromSectionIndex(chunk.getSections().length - 1);
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().setSectionRangeDirty(
							pos.x,
							minSectionY,
							pos.z,
							pos.x,
							maxSectionY,
							pos.z
					)
			);
		}
	}

	public static void onChunkReadyToRender(ClientLevel level, ChunkPos pos) {
		if (level == null || pos == null) {
			return;
		}
		synchronized (LOCK) {
			forEachCachedRendererLocked(level, cachedRenderer ->
					cachedRenderer.levelRenderer().onChunkReadyToRender(pos)
			);
		}
	}

	public static boolean render(
			Minecraft client,
			RenderRequest request,
			Consumer<NativeImage> imageConsumer
	) {
		if (client == null
				|| request == null
				|| imageConsumer == null
				|| client.gameRenderer == null
				|| client.levelRenderer == null) {
			return false;
		}
		if (client.screen != null || client.getOverlay() != null) {
			return false;
		}
		ClientLevel renderLevel = RendererBotShadowWorldManager.resolveRenderLevel(client, request.dimensionId());
		if (renderLevel == null) {
			return false;
		}

		synchronized (LOCK) {
			if (offscreenRenderActive) {
				return false;
			}
			cleanupCachedRenderersLocked(client, System.currentTimeMillis());

			CameraState cameraState = resolveCameraState(client, renderLevel, request);
			if (cameraState == null) {
				return false;
			}
			if (!isWorldReady(renderLevel, cameraState)) {
				return false;
			}
			CachedLevelRenderer cachedRenderer = getOrCreateCachedRendererLocked(client, renderLevel, request, System.currentTimeMillis());
			if (cachedRenderer == null) {
				return false;
			}
			cachedRenderer.ensureSize(request.renderWidth(), request.renderHeight());
			if (cachedRenderer.levelRenderer().countRenderedSections() == 0) {
				bootstrapLoadedChunks(cachedRenderer.levelRenderer(), renderLevel);
			}

			TextureTarget renderTarget = new TextureTarget(
					"lg2_renderer_bot_offscreen",
					request.renderWidth(),
					request.renderHeight(),
					true
			);
			RenderTarget previousRenderTarget = client.getMainRenderTarget();
			Entity previousCameraEntity = client.getCameraEntity();
			Camera previousMainCamera = client.gameRenderer.getMainCamera();
			boolean screenshotQueued = false;

			try {
				offscreenRenderActive = true;
				RenderSystem.backupProjectionMatrix();
				((MinecraftMainRenderTargetAccessor) client).lg2$setMainRenderTarget(renderTarget);
				client.setCameraEntity(cameraState.camera().entity());
				((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$setMainCamera(cameraState.camera());
				renderOffscreenWorld(client, renderLevel, request, cameraState, cachedRenderer, renderTarget);
				Screenshot.takeScreenshot(renderTarget, image -> {
					try {
						imageConsumer.accept(image);
					} finally {
						client.execute(renderTarget::destroyBuffers);
					}
				});
				screenshotQueued = true;
				return true;
			} catch (Throwable throwable) {
				Lg2.LOGGER.warn("Renderer bot offscreen render failed for {}", request, throwable);
				return false;
			} finally {
				((MinecraftMainRenderTargetAccessor) client).lg2$setMainRenderTarget(previousRenderTarget);
				client.setCameraEntity(previousCameraEntity);
				((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$setMainCamera(previousMainCamera);
				RenderSystem.restoreProjectionMatrix();
				RenderSystem.setShaderFog(((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE));
				offscreenRenderActive = false;
				if (!screenshotQueued) {
					renderTarget.destroyBuffers();
				}
			}
		}
	}

	private static void renderOffscreenWorld(
			Minecraft client,
			ClientLevel renderLevel,
			RenderRequest request,
			CameraState cameraState,
			CachedLevelRenderer cachedRenderer,
			TextureTarget renderTarget
	) {
		GameRendererRenderLevelInvoker gameRendererAccessor = (GameRendererRenderLevelInvoker) client.gameRenderer;
		FogRenderer fogRenderer = gameRendererAccessor.lg2$getFogRenderer();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		client.gameRenderer.lightTexture().updateLightTexture(1.0F);
		cachedRenderer.levelRenderer().tick(cameraState.camera());
		applyLevelRenderCameraState(cachedRenderer.levelRenderer(), cameraState.camera(), partialTick);
		Matrix4f projectionMatrix = client.gameRenderer.getProjectionMatrix(request.fovDegrees());
		Matrix4f cullingMatrix = gameRendererAccessor.lg2$getProjectionMatrixForCulling(request.fovDegrees());
		Matrix4f viewMatrix = new Matrix4f().rotation(new Quaternionf(cameraState.camera().rotation()).conjugate());
		Vector4f fogColor = fogRenderer.setupFog(
				cameraState.camera(),
				client.options.getEffectiveRenderDistance(),
				client.getDeltaTracker(),
				gameRendererAccessor.lg2$getDarkenWorldAmount(partialTick),
				renderLevel
		);
		GpuBufferSlice projectionMatrixSlice = gameRendererAccessor.lg2$getLevelProjectionMatrixBuffer().getBuffer(projectionMatrix);
		GpuBufferSlice fogBuffer = fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);
		double gamma = client.options.gamma().get();
		long dayTime = renderLevel == null ? 0L : renderLevel.getDayTime();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.clearColorAndDepthTextures(renderTarget.getColorTexture(), 0, renderTarget.getDepthTexture(), 1.0D);

		RenderSystem.setProjectionMatrix(projectionMatrixSlice, ProjectionType.PERSPECTIVE);
		client.gameRenderer.getGlobalSettingsUniform().update(
				request.renderWidth(),
				request.renderHeight(),
				gamma,
				dayTime,
				client.getDeltaTracker(),
				client.options.getEffectiveRenderDistance(),
				cameraState.camera(),
				false
		);
		cachedRenderer.levelRenderer().renderLevel(
				GraphicsResourceAllocator.UNPOOLED,
				client.getDeltaTracker(),
				false,
				cameraState.camera(),
				viewMatrix,
				projectionMatrix,
				cullingMatrix,
				fogBuffer,
				fogColor,
				client.gui == null || !client.gui.getBossOverlay().shouldCreateWorldFog()
		);
		cachedRenderer.featureRenderDispatcher().endFrame();
		cachedRenderer.levelRenderer().endFrame();
	}

	private static void applyLevelRenderCameraState(LevelRenderer levelRenderer, Camera camera, float partialTick) {
		if (levelRenderer == null || camera == null) {
			return;
		}
		LevelRenderState levelRenderState = ((LevelRendererRenderStateAccessor) levelRenderer).lg2$getLevelRenderState();
		if (levelRenderState == null || levelRenderState.cameraRenderState == null) {
			return;
		}
		CameraRenderState cameraRenderState = levelRenderState.cameraRenderState;
		Entity cameraEntity = camera.entity();
		cameraRenderState.initialized = true;
		cameraRenderState.pos = camera.position();
		cameraRenderState.blockPos = camera.blockPosition();
		cameraRenderState.entityPos = cameraEntity != null ? cameraEntity.getPosition(partialTick) : camera.position();
		cameraRenderState.orientation = new Quaternionf(camera.rotation());
	}

	private static CameraState resolveCameraState(Minecraft client, ClientLevel renderLevel, RenderRequest request) {
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (renderLevel == null) {
			return null;
		}
		if (request.followEntityUuid() != null) {
			Entity followTarget = renderLevel.getPlayerByUUID(request.followEntityUuid());
			if (followTarget != null) {
				Camera camera = new Camera();
				camera.setup(renderLevel, followTarget, false, false, partialTick);
				((CameraPositionInvoker) camera).lg2$setPosition(followTarget.getEyePosition(partialTick));
				return new CameraState(camera);
			}
			for (Entity entity : renderLevel.entitiesForRendering()) {
				if (request.followEntityUuid().equals(entity.getUUID())) {
					Camera camera = new Camera();
					camera.setup(renderLevel, entity, false, false, partialTick);
					((CameraPositionInvoker) camera).lg2$setPosition(entity.getEyePosition(partialTick));
					return new CameraState(camera);
				}
			}
			return null;
		}

		Vec3 eyePosition = new Vec3(request.x(), request.y() + STATIC_CAMERA_EYE_HEIGHT, request.z());
		Marker anchor = new Marker(EntityType.MARKER, renderLevel);
		anchor.snapTo(eyePosition, request.yaw(), request.pitch());
		anchor.setOldPosAndRot(eyePosition, request.yaw(), request.pitch());
		anchor.noPhysics = true;
		anchor.setNoGravity(true);
		Camera camera = new Camera();
		camera.setup(renderLevel, anchor, false, false, partialTick);
		return new CameraState(camera);
	}

	private static boolean isWorldReady(ClientLevel renderLevel, CameraState cameraState) {
		if (renderLevel == null || cameraState == null || cameraState.camera() == null) {
			return false;
		}
		Vec3 position = cameraState.camera().position();
		int centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(position.x));
		int centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(position.z));
		for (int dx = -MIN_READY_CHUNK_RADIUS; dx <= MIN_READY_CHUNK_RADIUS; dx++) {
			for (int dz = -MIN_READY_CHUNK_RADIUS; dz <= MIN_READY_CHUNK_RADIUS; dz++) {
				LevelChunk chunk = renderLevel.getChunkSource().getChunk(centerChunkX + dx, centerChunkZ + dz, ChunkStatus.FULL, false);
				if (chunk == null) {
					return false;
				}
			}
		}
		return true;
	}

	private static void forEachCachedRendererLocked(ClientLevel level, Consumer<CachedLevelRenderer> consumer) {
		if (level == null || consumer == null || CACHED_RENDERERS.isEmpty()) {
			return;
		}
		for (CachedLevelRenderer cachedRenderer : CACHED_RENDERERS.values()) {
			if (cachedRenderer == null || cachedRenderer.level() != level) {
				continue;
			}
			consumer.accept(cachedRenderer);
		}
	}

	private static CachedLevelRenderer getOrCreateCachedRendererLocked(Minecraft client, ClientLevel renderLevel, RenderRequest request, long now) {
		if (client == null || renderLevel == null || request == null) {
			return null;
		}
		RendererKey key = RendererKey.from(request);
		CachedLevelRenderer cachedRenderer = CACHED_RENDERERS.get(key);
		if (cachedRenderer != null) {
			if (cachedRenderer.level() == renderLevel) {
				cachedRenderer.touch(now);
				return cachedRenderer;
			}
			closeCachedRendererLocked(cachedRenderer);
			CACHED_RENDERERS.remove(key);
		}

		RenderBuffers renderBuffers = client.renderBuffers();
		FeatureRenderDispatcher featureRenderDispatcher = new FeatureRenderDispatcher(
				new SubmitNodeStorage(),
				client.getBlockRenderer(),
				renderBuffers.bufferSource(),
				client.getAtlasManager(),
				renderBuffers.outlineBufferSource(),
				renderBuffers.crumblingBufferSource(),
				client.font
		);
		LevelRenderer levelRenderer = new LevelRenderer(
				client,
				client.getEntityRenderDispatcher(),
				client.getBlockEntityRenderDispatcher(),
				renderBuffers,
				new LevelRenderState(),
				featureRenderDispatcher
		);
		levelRenderer.onResourceManagerReload(client.getResourceManager());
		levelRenderer.setLevel(renderLevel);
		levelRenderer.resize(request.renderWidth(), request.renderHeight());
		bootstrapLoadedChunks(levelRenderer, renderLevel);
		CachedLevelRenderer created = new CachedLevelRenderer(
				renderLevel,
				levelRenderer,
				featureRenderDispatcher,
				request.renderWidth(),
				request.renderHeight(),
				now
		);
		CACHED_RENDERERS.put(key, created);
		return created;
	}

	private static void bootstrapLoadedChunks(LevelRenderer levelRenderer, ClientLevel renderLevel) {
		if (levelRenderer == null || renderLevel == null) {
			return;
		}
		if (!(renderLevel.getChunkSource() instanceof RendererBotVirtualChunkAccess chunkAccess)) {
			return;
		}
		for (LevelChunk chunk : chunkAccess.lg2$getLoadedVirtualChunksSnapshot()) {
			if (chunk == null) {
				continue;
			}
			ChunkPos pos = chunk.getPos();
			levelRenderer.onChunkReadyToRender(pos);
			LevelChunkSection[] sections = chunk.getSections();
			for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
				LevelChunkSection section = sections[sectionIndex];
				if (section == null || section.hasOnlyAir()) {
					continue;
				}
				levelRenderer.setSectionDirtyWithNeighbors(pos.x, chunk.getSectionYFromSectionIndex(sectionIndex), pos.z);
			}
		}
		levelRenderer.needsUpdate();
	}

	private static void cleanupCachedRenderersLocked(Minecraft client, long now) {
		if (CACHED_RENDERERS.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<RendererKey, CachedLevelRenderer>> iterator = CACHED_RENDERERS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<RendererKey, CachedLevelRenderer> entry = iterator.next();
			CachedLevelRenderer cachedRenderer = entry.getValue();
			if (cachedRenderer == null
					|| !RendererBotShadowWorldManager.isManagedLevel(client, cachedRenderer.level())
					|| now - cachedRenderer.lastUsedAtMillis() > RENDERER_CACHE_TTL_MS) {
				closeCachedRendererLocked(cachedRenderer);
				iterator.remove();
			}
		}
	}

	private static void closeAllCachedRenderersLocked() {
		for (CachedLevelRenderer cachedRenderer : CACHED_RENDERERS.values()) {
			closeCachedRendererLocked(cachedRenderer);
		}
		CACHED_RENDERERS.clear();
	}

	private static void closeCachedRendererLocked(CachedLevelRenderer cachedRenderer) {
		if (cachedRenderer == null) {
			return;
		}
		try {
			cachedRenderer.levelRenderer().setLevel(null);
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Renderer bot failed to detach cached level renderer cleanly", throwable);
		}
		try {
			cachedRenderer.levelRenderer().close();
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Renderer bot failed to close cached level renderer cleanly", throwable);
		}
		try {
			cachedRenderer.featureRenderDispatcher().close();
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Renderer bot failed to close cached feature dispatcher cleanly", throwable);
		}
	}

	public record RenderRequest(
			String dimensionId,
			UUID followEntityUuid,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			int fovDegrees,
			int renderWidth,
			int renderHeight
	) {
	}

	private record CameraState(Camera camera) {
	}

	private record RendererKey(
			String dimensionId,
			UUID followEntityUuid,
			double x,
			double y,
			double z,
			float yaw,
			float pitch
	) {
		private static RendererKey from(RenderRequest request) {
			if (request.followEntityUuid() != null) {
				return new RendererKey(request.dimensionId(), request.followEntityUuid(), 0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
			}
			return new RendererKey(
					request.dimensionId(),
					null,
					request.x(),
					request.y(),
					request.z(),
					request.yaw(),
					request.pitch()
			);
		}
	}

	private static final class CachedLevelRenderer {
		private final ClientLevel level;
		private final LevelRenderer levelRenderer;
		private final FeatureRenderDispatcher featureRenderDispatcher;
		private int width;
		private int height;
		private long lastUsedAtMillis;

		private CachedLevelRenderer(
				ClientLevel level,
				LevelRenderer levelRenderer,
				FeatureRenderDispatcher featureRenderDispatcher,
				int width,
				int height,
				long lastUsedAtMillis
		) {
			this.level = level;
			this.levelRenderer = levelRenderer;
			this.featureRenderDispatcher = featureRenderDispatcher;
			this.width = width;
			this.height = height;
			this.lastUsedAtMillis = lastUsedAtMillis;
		}

		private ClientLevel level() {
			return this.level;
		}

		private LevelRenderer levelRenderer() {
			return this.levelRenderer;
		}

		private FeatureRenderDispatcher featureRenderDispatcher() {
			return this.featureRenderDispatcher;
		}

		private long lastUsedAtMillis() {
			return this.lastUsedAtMillis;
		}

		private void touch(long now) {
			this.lastUsedAtMillis = now;
		}

		private void ensureSize(int width, int height) {
			this.touch(System.currentTimeMillis());
			if (this.width == width && this.height == height) {
				return;
			}
			this.levelRenderer.resize(width, height);
			this.width = width;
			this.height = height;
		}
	}
}
