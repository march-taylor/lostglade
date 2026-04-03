package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.mixin.client.GameRendererRenderLevelInvoker;
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
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.UUID;
import java.util.function.Consumer;

public final class RendererBotOffscreenWorldRenderer {
	private static final Object LOCK = new Object();
	private static final double STATIC_CAMERA_EYE_HEIGHT = 1.62D;
	private static boolean offscreenRenderActive;

	private RendererBotOffscreenWorldRenderer() {
	}

	public static boolean isOffscreenRenderActive() {
		synchronized (LOCK) {
			return offscreenRenderActive;
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
				|| client.level == null
				|| client.gameRenderer == null
				|| client.levelRenderer == null) {
			return false;
		}
		if (client.screen != null || client.getOverlay() != null) {
			return false;
		}
		Identifier currentDimension = client.level.dimension().identifier();
		if (!currentDimension.toString().equals(request.dimensionId())) {
			return false;
		}

		synchronized (LOCK) {
			if (offscreenRenderActive) {
				return false;
			}

			CameraState cameraState = resolveCameraState(client, request);
			if (cameraState == null) {
				return false;
			}

			TextureTarget renderTarget = new TextureTarget(
					"lg2_renderer_bot_offscreen",
					request.renderWidth(),
					request.renderHeight(),
					true
			);
			RenderTarget previousRenderTarget = client.getMainRenderTarget();
			int previousWidth = previousRenderTarget.width;
			int previousHeight = previousRenderTarget.height;
			boolean screenshotQueued = false;

			try {
				offscreenRenderActive = true;
				RenderSystem.backupProjectionMatrix();
				((MinecraftMainRenderTargetAccessor) client).lg2$setMainRenderTarget(renderTarget);
				if (previousWidth != request.renderWidth() || previousHeight != request.renderHeight()) {
					client.levelRenderer.resize(request.renderWidth(), request.renderHeight());
				}
				renderOffscreenWorld(client, request, cameraState, renderTarget);
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
				if (previousWidth != request.renderWidth() || previousHeight != request.renderHeight()) {
					client.levelRenderer.resize(previousWidth, previousHeight);
				}
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
			RenderRequest request,
			CameraState cameraState,
			TextureTarget renderTarget
	) {
		GameRendererRenderLevelInvoker gameRendererAccessor = (GameRendererRenderLevelInvoker) client.gameRenderer;
		FogRenderer fogRenderer = gameRendererAccessor.lg2$getFogRenderer();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Matrix4f projectionMatrix = client.gameRenderer.getProjectionMatrix(request.fovDegrees());
		Matrix4f cullingMatrix = gameRendererAccessor.lg2$getProjectionMatrixForCulling(request.fovDegrees());
		Matrix4f viewMatrix = new Matrix4f().rotation(new Quaternionf(cameraState.camera().rotation()).conjugate());
		Vector4f fogColor = fogRenderer.setupFog(
				cameraState.camera(),
				client.options.getEffectiveRenderDistance(),
				client.getDeltaTracker(),
				gameRendererAccessor.lg2$getDarkenWorldAmount(partialTick),
				client.level
		);
		GpuBufferSlice projectionMatrixSlice = gameRendererAccessor.lg2$getLevelProjectionMatrixBuffer().getBuffer(projectionMatrix);
		GpuBufferSlice fogBuffer = fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);
		double gamma = client.options.gamma().get();
		long dayTime = client.level == null ? 0L : client.level.getDayTime();
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
			client.levelRenderer.renderLevel(
					GraphicsResourceAllocator.UNPOOLED,
					client.getDeltaTracker(),
					false,
				cameraState.camera(),
				viewMatrix,
				projectionMatrix,
				cullingMatrix,
				fogBuffer,
					fogColor,
					false
			);
			client.gameRenderer.getSubmitNodeStorage().endFrame();
			client.gameRenderer.getFeatureRenderDispatcher().endFrame();
			client.levelRenderer.endFrame();
	}

	private static CameraState resolveCameraState(Minecraft client, RenderRequest request) {
		if (request.followEntityUuid() != null) {
			Entity followTarget = client.level.getPlayerByUUID(request.followEntityUuid());
			if (followTarget != null) {
				Camera camera = new Camera();
				camera.setup(client.level, followTarget, false, false, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
				return new CameraState(camera);
			}
			for (Entity entity : client.level.entitiesForRendering()) {
				if (request.followEntityUuid().equals(entity.getUUID())) {
					Camera camera = new Camera();
					camera.setup(client.level, entity, false, false, client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
					return new CameraState(camera);
				}
			}
			return null;
		}

		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 eyePosition = new Vec3(request.x(), request.y() + STATIC_CAMERA_EYE_HEIGHT, request.z());
		Marker anchor = new Marker(EntityType.MARKER, client.level);
		anchor.snapTo(eyePosition, request.yaw(), request.pitch());
		anchor.setOldPosAndRot(eyePosition, request.yaw(), request.pitch());
		anchor.noPhysics = true;
		anchor.setNoGravity(true);
		Camera camera = new Camera();
		camera.setup(client.level, anchor, false, false, partialTick);
		return new CameraState(camera);
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
}
