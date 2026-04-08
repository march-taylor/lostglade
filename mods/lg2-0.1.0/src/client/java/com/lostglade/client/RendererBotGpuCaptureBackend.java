package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class RendererBotGpuCaptureBackend {
	private static final boolean GPU_CAPTURE_ENABLED = !Boolean.getBoolean("lg2.rendererBotDisableGpuCapture");
	private static final int PALETTE_LOOKUP_SIZE = 256;
	private static final Identifier SCREENQUAD_SHADER = Objects.requireNonNull(Identifier.tryParse("minecraft:core/screenquad"));
	private static final Identifier MAP_QUANTIZE_SHADER = Objects.requireNonNull(Identifier.tryParse("lg2:core/renderer_bot/map_quantize"));
	private static final RenderPipeline MAP_QUANTIZE_PIPELINE = createPipeline("renderer_bot_map_quantize", false);
	private static final RenderPipeline MAP_QUANTIZE_DITHER_PIPELINE = createPipeline("renderer_bot_map_quantize_dither", true);

	private static final Object LOCK = new Object();
	private static PaletteTextureState paletteTextureState;
	private static boolean disabledAfterFailure;

	private RendererBotGpuCaptureBackend() {
	}

	public static boolean isAvailable() {
		if (!GPU_CAPTURE_ENABLED || disabledAfterFailure) {
			return false;
		}
		return RenderSystem.tryGetDevice() != null;
	}

	public static CompletableFuture<byte[]> captureQuantizedFrame(RenderTarget sourceTarget, int outputWidth, int outputHeight, boolean dither) {
		CompletableFuture<byte[]> future = new CompletableFuture<>();
		if (!isAvailable()) {
			future.completeExceptionally(new IllegalStateException("GPU capture backend is unavailable"));
			return future;
		}
		if (sourceTarget == null) {
			future.completeExceptionally(new IllegalArgumentException("Source render target is null"));
			return future;
		}
		if (sourceTarget.getColorTextureView() == null) {
			future.completeExceptionally(new IllegalStateException("Source render target has no color texture"));
			return future;
		}

		RenderSystem.assertOnRenderThread();
		QuantizeJob job = null;
		try {
			PaletteTextureState paletteState = ensurePaletteTextureState();
			job = new QuantizeJob(sourceTarget.width, sourceTarget.height, outputWidth, outputHeight);
			renderQuantized(job, sourceTarget, paletteState, dither);
			QuantizeJob activeJob = job;
			Screenshot.takeScreenshot(job.outputTarget(), image -> {
				try (image) {
					future.complete(extractMapBytes(image));
				} catch (Throwable throwable) {
					future.completeExceptionally(throwable);
				} finally {
					activeJob.close();
				}
			});
			return future;
		} catch (Throwable throwable) {
			if (job != null) {
				job.close();
			}
			disableAfterFailure(throwable);
			future.completeExceptionally(throwable);
			return future;
		}
	}

	private static void renderQuantized(
			QuantizeJob job,
			RenderTarget sourceTarget,
			PaletteTextureState paletteState,
			boolean dither
	) {
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = encoder.createRenderPass(
				() -> "lg2_renderer_bot_gpu_quantize",
				job.outputTarget().getColorTextureView(),
				java.util.OptionalInt.empty()
		)) {
			pass.setPipeline(dither ? MAP_QUANTIZE_DITHER_PIPELINE : MAP_QUANTIZE_PIPELINE);
			pass.bindTexture(
					"InSampler",
					sourceTarget.getColorTextureView(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
			);
			pass.bindTexture(
					"PaletteSampler",
					paletteState.textureView(),
					RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
			);
			pass.setUniform("Lg2CaptureParams", job.paramsBuffer());
			pass.draw(0, 3);
		}
	}

	private static PaletteTextureState ensurePaletteTextureState() {
		synchronized (LOCK) {
			if (paletteTextureState != null) {
				return paletteTextureState;
			}

			GpuDevice device = RenderSystem.getDevice();
			GpuTexture texture = device.createTexture(
					() -> "lg2_renderer_bot_palette_lookup",
					GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
					com.mojang.blaze3d.textures.TextureFormat.RGBA8,
					PALETTE_LOOKUP_SIZE,
					PALETTE_LOOKUP_SIZE,
					1,
					1
			);
			GpuTextureView textureView = device.createTextureView(texture);
			NativeImage lookupImage = buildPaletteLookupImage();
			try (lookupImage) {
				device.createCommandEncoder().writeToTexture(texture, lookupImage);
			}
			paletteTextureState = new PaletteTextureState(texture, textureView);
			return paletteTextureState;
		}
	}

	private static NativeImage buildPaletteLookupImage() {
		NativeImage image = new NativeImage(PALETTE_LOOKUP_SIZE, PALETTE_LOOKUP_SIZE, false);
		for (int key = 0; key < 65_536; key++) {
			int red5 = (key >> 11) & 0x1F;
			int green6 = (key >> 5) & 0x3F;
			int blue5 = key & 0x1F;
			int red = (red5 << 3) | (red5 >> 2);
			int green = (green6 << 2) | (green6 >> 4);
			int blue = (blue5 << 3) | (blue5 >> 2);
			int paletteId = Byte.toUnsignedInt(MapPaletteQuantizer.quantize((red << 16) | (green << 8) | blue));
			int x = key & 0xFF;
			int y = (key >> 8) & 0xFF;
			int argb = 0xFF000000 | (paletteId << 16) | (paletteId << 8) | paletteId;
			image.setPixel(x, y, argb);
		}
		return image;
	}

	private static byte[] extractMapBytes(NativeImage image) {
		int[] pixels = image.makePixelArray();
		byte[] output = new byte[pixels.length];
		for (int i = 0; i < pixels.length; i++) {
			output[i] = (byte) (pixels[i] & 0xFF);
		}
		return output;
	}

	private static void disableAfterFailure(Throwable throwable) {
		if (disabledAfterFailure) {
			return;
		}
		disabledAfterFailure = true;
		Lg2.LOGGER.warn("Renderer bot GPU capture backend disabled after failure, CPU fallback will be used", throwable);
	}

	private static RenderPipeline createPipeline(String location, boolean dither) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Objects.requireNonNull(Identifier.tryParse("lg2:" + location)))
				.withVertexShader(SCREENQUAD_SHADER)
				.withFragmentShader(MAP_QUANTIZE_SHADER)
				.withSampler("InSampler")
				.withSampler("PaletteSampler")
				.withUniform("Lg2CaptureParams", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
				.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
				.withCull(false)
				.withDepthWrite(false)
				.withoutBlend();
		if (dither) {
			builder.withShaderDefine("LG2_DITHER");
		}
		return builder.build();
	}

	private static final class QuantizeJob implements AutoCloseable {
		private final TextureTarget outputTarget;
		private final GpuBuffer paramsBuffer;

		private QuantizeJob(int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
			int safeWidth = Math.max(1, outputWidth);
			int safeHeight = Math.max(1, outputHeight);
			this.outputTarget = new TextureTarget("lg2_renderer_bot_quantized_capture", safeWidth, safeHeight, false);
			this.paramsBuffer = createParamsBuffer(sourceWidth, sourceHeight, safeWidth, safeHeight);
		}

		private TextureTarget outputTarget() {
			return this.outputTarget;
		}

		private GpuBuffer paramsBuffer() {
			return this.paramsBuffer;
		}

		@Override
		public void close() {
			this.paramsBuffer.close();
			this.outputTarget.destroyBuffers();
		}

		private GpuBuffer createParamsBuffer(int sourceWidth, int sourceHeight, int outputWidth, int outputHeight) {
			double sourceAspect = sourceWidth / (double) Math.max(1, sourceHeight);
			double targetAspect = outputWidth / (double) Math.max(1, outputHeight);
			double cropX = 0.0D;
			double cropY = 0.0D;
			double cropWidth = sourceWidth;
			double cropHeight = sourceHeight;
			if (sourceAspect > targetAspect) {
				cropWidth = sourceHeight * targetAspect;
				cropX = (sourceWidth - cropWidth) * 0.5D;
			} else if (sourceAspect < targetAspect) {
				cropHeight = sourceWidth / targetAspect;
				cropY = (sourceHeight - cropHeight) * 0.5D;
			}
			float uvX = (float) (cropX / Math.max(1.0D, sourceWidth));
			float uvY = (float) (cropY / Math.max(1.0D, sourceHeight));
			float uvWidth = (float) (cropWidth / Math.max(1.0D, sourceWidth));
			float uvHeight = (float) (cropHeight / Math.max(1.0D, sourceHeight));
			ByteBuffer data = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
			data.putFloat(uvX);
			data.putFloat(uvY);
			data.putFloat(uvWidth);
			data.putFloat(uvHeight);
			data.putFloat(outputWidth);
			data.putFloat(outputHeight);
			data.putFloat(0.0F);
			data.putFloat(0.0F);
			data.flip();
			return RenderSystem.getDevice().createBuffer(
					() -> "lg2_renderer_bot_capture_params",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
					data
			);
		}
	}

	private record PaletteTextureState(GpuTexture texture, GpuTextureView textureView) {
	}
}
