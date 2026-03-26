package com.lostglade.server.camera.bluemap;

import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.world.LightData;
import net.minecraft.core.BlockPos;

record RenderContext(
		BlueMapCameraRenderer.WorldSnapshot snapshot,
		ArrayTileModel model,
		MaterialResolver materialResolver
) {
	private static final int DEFAULT_SKY_LIGHT = 15;

	LightSample lightAt(float x, float y, float z) {
		LightData lightData = this.snapshot.sampleLight(BlockPos.containing(x, y, z));
		return new LightSample(
				lightData == null ? DEFAULT_SKY_LIGHT : lightData.getSkyLight(),
				lightData == null ? 0 : lightData.getBlockLight()
		);
	}
}

record LightSample(int sky, int block) {
}
