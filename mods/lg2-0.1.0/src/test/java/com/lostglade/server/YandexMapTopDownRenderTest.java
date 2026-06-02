package com.lostglade.server;

import com.lostglade.server.map.BlockTextureRaycaster;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

public final class YandexMapTopDownRenderTest {
	private YandexMapTopDownRenderTest() {
	}

	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		logUsesTopFace();
		grassBlockUsesTopTexture();
		redstoneWireProducesTopPixel();
		leverProducesTopPixel();
		buttonProducesTopPixel();
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
		System.out.println("Yandex map top-down render checks passed");
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

	private static void buttonProducesTopPixel() {
		BlockState state = Blocks.STONE_BUTTON.defaultBlockState()
				.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR)
				.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
		BlockTextureRaycaster.BlockTraceResult result = traceTop(state, 0.5D, 0.5D);
		require(result != null && result.alpha() > 8, "floor button must produce a visible top-down pixel");
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

	private static int colorDistance(int left, int right) {
		int dr = ((left >> 16) & 0xFF) - ((right >> 16) & 0xFF);
		int dg = ((left >> 8) & 0xFF) - ((right >> 8) & 0xFF);
		int db = (left & 0xFF) - (right & 0xFF);
		return Math.abs(dr) + Math.abs(dg) + Math.abs(db);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
