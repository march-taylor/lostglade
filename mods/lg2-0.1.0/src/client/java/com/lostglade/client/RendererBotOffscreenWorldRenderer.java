package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.mixin.client.CameraPositionInvoker;
import com.lostglade.mixin.client.GameRendererRenderLevelInvoker;
import com.lostglade.mixin.client.LevelRendererRenderStateAccessor;
import com.lostglade.mixin.client.MinecraftMainRenderTargetAccessor;
import com.lostglade.mixin.client.MinecraftOffscreenWorldAccessor;
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
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class RendererBotOffscreenWorldRenderer {
	private static final Object LOCK = new Object();
	private static final double STATIC_CAMERA_EYE_HEIGHT = 1.62D;
	private static final int MIN_READY_CHUNK_RADIUS = 2;
	private static final Map<UUID, OffscreenSessionState> SESSION_STATES = new HashMap<>();
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
			for (OffscreenSessionState state : SESSION_STATES.values()) {
				closeSessionState(state);
			}
			SESSION_STATES.clear();
		}
		RendererBotCoolElytraCompat.clearCaches();
	}

	public static void releaseSession(UUID sessionId) {
		if (sessionId == null) {
			return;
		}
		synchronized (LOCK) {
			closeSessionState(SESSION_STATES.remove(sessionId));
		}
		RendererBotCoolElytraCompat.releaseSession(sessionId);
	}

	public static void onBlockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState, int flags) {
	}

	public static void onBlockDirty(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
	}

	public static void onSectionDirtyWithNeighbors(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
	}

	public static void onSectionDirty(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
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
	}

	public static void onSectionBecomingNonEmpty(ClientLevel level, long sectionPos) {
	}

	public static void onDestroyBlockProgress(ClientLevel level, int breakerId, BlockPos pos, int progress) {
	}

	public static void onChunkUnloaded(ClientLevel level, LevelChunk chunk) {
	}

	public static void onChunkReadyToRender(ClientLevel level, ChunkPos pos) {
	}

	public static boolean render(Minecraft client, RenderRequest request, Consumer<NativeImage> imageConsumer) {
		return renderToTarget(client, request, renderTarget -> Screenshot.takeScreenshot(renderTarget, imageConsumer));
	}

	public static boolean renderToTarget(Minecraft client, RenderRequest request, Consumer<RenderTarget> renderTargetConsumer) {
		if (client == null
				|| request == null
				|| renderTargetConsumer == null
				|| client.gameRenderer == null
				|| client.levelRenderer == null) {
			return false;
		}
		if (client.screen != null || client.getOverlay() != null) {
			return false;
		}
		RendererBotShadowWorldManager.ShadowRenderSession session = RendererBotShadowWorldManager.resolveRenderSession(request.sessionId());
		if (session == null || session.level() == null || session.levelRenderer() == null) {
			return false;
		}

		ClientLevel renderLevel = session.level();
		LevelRenderer levelRenderer = session.levelRenderer();
		synchronized (LOCK) {
			if (offscreenRenderActive) {
				return false;
			}

			OffscreenSessionState sessionState = SESSION_STATES.computeIfAbsent(request.sessionId(), ignored -> new OffscreenSessionState());
			TextureTarget renderTarget = ensureRenderTarget(sessionState, request.renderWidth(), request.renderHeight());
			Entity followTarget = resolveFollowTarget(renderLevel, request.followEntityUuid());
			RendererBotCoolElytraCompat.RollOverride rollOverride =
					RendererBotCoolElytraCompat.beginCameraRoll(request.sessionId(), followTarget);
			try {
				CameraState cameraState = resolveCameraState(client, renderLevel, request, sessionState, followTarget);
				if (cameraState == null || !isWorldReady(renderLevel, cameraState, request)) {
					sessionState.renderInProgress = false;
					return false;
				}

				levelRenderer.resize(request.renderWidth(), request.renderHeight());
				RenderTarget previousRenderTarget = client.getMainRenderTarget();
				MinecraftOffscreenWorldAccessor worldAccessor = (MinecraftOffscreenWorldAccessor) client;
				ClientLevel previousLevel = worldAccessor.lg2$getLevel();
				LevelRenderer previousLevelRenderer = worldAccessor.lg2$getLevelRenderer();
				ParticleEngine previousParticleEngine = worldAccessor.lg2$getParticleEngine();
				Entity previousCameraEntity = client.getCameraEntity();
				Camera previousMainCamera = client.gameRenderer.getMainCamera();
				boolean screenshotQueued = false;

				try {
					offscreenRenderActive = true;
					RenderSystem.backupProjectionMatrix();
					((MinecraftMainRenderTargetAccessor) client).lg2$setMainRenderTarget(renderTarget);
					worldAccessor.lg2$setLevel(renderLevel);
					worldAccessor.lg2$setLevelRenderer(levelRenderer);
					worldAccessor.lg2$setParticleEngine(session.particleEngine());
					client.setCameraEntity(cameraState.camera().entity());
					((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$setMainCamera(cameraState.camera());
					RendererBotShadowWorldManager.updateCameraContext(request.sessionId(), cameraState.camera());
					renderOffscreenWorld(client, renderLevel, levelRenderer, session.featureRenderDispatcher(), request, cameraState, renderTarget);
					renderTargetConsumer.accept(renderTarget);
					screenshotQueued = true;
					return true;
				} catch (Throwable throwable) {
					Lg2.LOGGER.warn("Renderer bot offscreen render failed for {}", request, throwable);
					return false;
				} finally {
					((MinecraftMainRenderTargetAccessor) client).lg2$setMainRenderTarget(previousRenderTarget);
					worldAccessor.lg2$setLevel(previousLevel);
					worldAccessor.lg2$setLevelRenderer(previousLevelRenderer);
					worldAccessor.lg2$setParticleEngine(previousParticleEngine);
					client.setCameraEntity(previousCameraEntity);
					((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$setMainCamera(previousMainCamera);
					RenderSystem.restoreProjectionMatrix();
					RenderSystem.setShaderFog(((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$getFogRenderer().getBuffer(FogRenderer.FogMode.NONE));
					offscreenRenderActive = false;
					sessionState.renderInProgress = false;
				}
			} finally {
				RendererBotCoolElytraCompat.endCameraRoll(rollOverride);
			}
		}
	}

	private static void renderOffscreenWorld(
			Minecraft client,
			ClientLevel renderLevel,
			LevelRenderer levelRenderer,
			net.minecraft.client.renderer.feature.FeatureRenderDispatcher featureRenderDispatcher,
			RenderRequest request,
			CameraState cameraState,
			TextureTarget renderTarget
	) {
		GameRendererRenderLevelInvoker gameRendererAccessor = (GameRendererRenderLevelInvoker) client.gameRenderer;
		FogRenderer fogRenderer = gameRendererAccessor.lg2$getFogRenderer();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		cameraState.camera().tick();
		client.gameRenderer.lightTexture().updateLightTexture(1.0F);
		gameRendererAccessor.lg2$extractCamera(partialTick);
		levelRenderer.tick(cameraState.camera());
		applyLevelRenderCameraState(levelRenderer, cameraState.camera(), partialTick);
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
		long dayTime = renderLevel.getDayTime();
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.clearColorAndDepthTextures(renderTarget.getColorTexture(), 0, renderTarget.getDepthTexture(), 1.0D);

		RenderSystem.setProjectionMatrix(projectionMatrixSlice, ProjectionType.PERSPECTIVE);
		client.gameRenderer.getGlobalSettingsUniform().update(
				request.renderWidth(),
				request.renderHeight(),
				client.options.glintStrength().get(),
				renderLevel.getGameTime(),
				client.getDeltaTracker(),
				client.options.getMenuBackgroundBlurriness(),
				cameraState.camera(),
				client.options.textureFiltering().get() == TextureFilteringMethod.RGSS
		);
		levelRenderer.renderLevel(
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
		featureRenderDispatcher.endFrame();
		levelRenderer.endFrame();
		fogRenderer.endFrame();
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

	private static CameraState resolveCameraState(
			Minecraft client,
			ClientLevel renderLevel,
			RenderRequest request,
			OffscreenSessionState sessionState,
			Entity followTarget
	) {
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (renderLevel == null) {
			return null;
		}
		if (request.followEntityUuid() != null) {
			if (followTarget == null) {
				return null;
			}
			Camera camera = sessionState.followCamera;
			camera.setup(renderLevel, followTarget, false, false, partialTick);
			((CameraPositionInvoker) camera).lg2$setPosition(followTarget.getEyePosition(partialTick));
			return new CameraState(camera);
		}

		Vec3 eyePosition = request.absoluteCameraPosition()
				? new Vec3(request.x(), request.y(), request.z())
				: new Vec3(request.x(), request.y() + STATIC_CAMERA_EYE_HEIGHT, request.z());
		Marker anchor = sessionState.ensureStaticAnchor(renderLevel);
		anchor.snapTo(eyePosition, request.yaw(), request.pitch());
		anchor.setOldPosAndRot(eyePosition, request.yaw(), request.pitch());
		anchor.noPhysics = true;
		anchor.setNoGravity(true);
		Camera camera = sessionState.staticCamera;
		camera.setup(renderLevel, anchor, false, false, partialTick);
		return new CameraState(camera);
	}

	private static Entity resolveFollowTarget(ClientLevel renderLevel, UUID followEntityUuid) {
		if (renderLevel == null || followEntityUuid == null) {
			return null;
		}
		Entity followTarget = renderLevel.getPlayerByUUID(followEntityUuid);
		if (followTarget != null) {
			return followTarget;
		}
		for (Entity entity : renderLevel.entitiesForRendering()) {
			if (followEntityUuid.equals(entity.getUUID())) {
				return entity;
			}
		}
		return null;
	}

	private static TextureTarget ensureRenderTarget(OffscreenSessionState sessionState, int width, int height) {
		if (sessionState == null) {
			return null;
		}
		int safeWidth = Math.max(1, width);
		int safeHeight = Math.max(1, height);
		if (sessionState.renderTarget == null
				|| sessionState.renderWidth != safeWidth
				|| sessionState.renderHeight != safeHeight) {
			if (sessionState.renderTarget != null) {
				sessionState.renderTarget.destroyBuffers();
			}
			sessionState.renderTarget = new TextureTarget("lg2_renderer_bot_offscreen", safeWidth, safeHeight, true);
			sessionState.renderWidth = safeWidth;
			sessionState.renderHeight = safeHeight;
		}
		sessionState.renderInProgress = true;
		return sessionState.renderTarget;
	}

	private static void closeSessionState(OffscreenSessionState state) {
		if (state == null) {
			return;
		}
		if (state.renderTarget != null) {
			state.renderTarget.destroyBuffers();
			state.renderTarget = null;
		}
		state.staticAnchor = null;
	}

	private static boolean isWorldReady(ClientLevel renderLevel, CameraState cameraState, RenderRequest request) {
		if (renderLevel == null || cameraState == null || cameraState.camera() == null) {
			return false;
		}
		Vec3 position = cameraState.camera().position();
		int centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(position.x));
		int centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(position.z));
		int readyChunkRadius = request != null && request.absoluteCameraPosition() ? 0 : MIN_READY_CHUNK_RADIUS;
		for (int dx = -readyChunkRadius; dx <= readyChunkRadius; dx++) {
			for (int dz = -readyChunkRadius; dz <= readyChunkRadius; dz++) {
				LevelChunk chunk = renderLevel.getChunkSource().getChunk(centerChunkX + dx, centerChunkZ + dz, ChunkStatus.FULL, false);
				if (chunk == null) {
					return false;
				}
			}
		}
		return true;
	}

	public record RenderRequest(
			UUID sessionId,
			String dimensionId,
			UUID followEntityUuid,
			double x,
			double y,
			double z,
			float yaw,
			float pitch,
			int fovDegrees,
			int renderWidth,
			int renderHeight,
			boolean absoluteCameraPosition
	) {
	}

	private record CameraState(Camera camera) {
	}

	private static final class OffscreenSessionState {
		private TextureTarget renderTarget;
		private int renderWidth;
		private int renderHeight;
		private Marker staticAnchor;
		private final Camera staticCamera = new Camera();
		private final Camera followCamera = new Camera();
		private boolean renderInProgress;

		private Marker ensureStaticAnchor(ClientLevel level) {
			if (this.staticAnchor == null || this.staticAnchor.level() != level) {
				this.staticAnchor = new Marker(EntityType.MARKER, level);
				this.staticAnchor.noPhysics = true;
				this.staticAnchor.setNoGravity(true);
			}
			return this.staticAnchor;
		}
	}
}
