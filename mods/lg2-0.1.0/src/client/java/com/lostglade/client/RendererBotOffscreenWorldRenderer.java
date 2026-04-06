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

import java.util.UUID;
import java.util.function.Consumer;

public final class RendererBotOffscreenWorldRenderer {
	private static final Object LOCK = new Object();
	private static final double STATIC_CAMERA_EYE_HEIGHT = 1.62D;
	private static final int MIN_READY_CHUNK_RADIUS = 1;
	private static boolean offscreenRenderActive;

	private RendererBotOffscreenWorldRenderer() {
	}

	public static boolean isOffscreenRenderActive() {
		synchronized (LOCK) {
			return offscreenRenderActive;
		}
	}

	public static void clearCaches() {
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

			CameraState cameraState = resolveCameraState(client, renderLevel, request);
			if (cameraState == null || !isWorldReady(renderLevel, cameraState)) {
				return false;
			}

			levelRenderer.resize(request.renderWidth(), request.renderHeight());
			TextureTarget renderTarget = new TextureTarget(
					"lg2_renderer_bot_offscreen",
					request.renderWidth(),
					request.renderHeight(),
					true
			);
			RenderTarget previousRenderTarget = client.getMainRenderTarget();
			MinecraftOffscreenWorldAccessor worldAccessor = (MinecraftOffscreenWorldAccessor) client;
			ClientLevel previousLevel = worldAccessor.lg2$getLevel();
			LevelRenderer previousLevelRenderer = worldAccessor.lg2$getLevelRenderer();
			Entity previousCameraEntity = client.getCameraEntity();
			Camera previousMainCamera = client.gameRenderer.getMainCamera();
			boolean screenshotQueued = false;

			try {
				offscreenRenderActive = true;
				RenderSystem.backupProjectionMatrix();
				((MinecraftMainRenderTargetAccessor) client).lg2$setMainRenderTarget(renderTarget);
				worldAccessor.lg2$setLevel(renderLevel);
				worldAccessor.lg2$setLevelRenderer(levelRenderer);
				client.setCameraEntity(cameraState.camera().entity());
				((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$setMainCamera(cameraState.camera());
				renderOffscreenWorld(client, renderLevel, levelRenderer, session.featureRenderDispatcher(), request, cameraState, renderTarget);
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
				worldAccessor.lg2$setLevel(previousLevel);
				worldAccessor.lg2$setLevelRenderer(previousLevelRenderer);
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
			int renderHeight
	) {
	}

	private record CameraState(Camera camera) {
	}
}
