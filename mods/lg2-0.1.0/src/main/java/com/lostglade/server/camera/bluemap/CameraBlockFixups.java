package com.lostglade.server.camera.bluemap;

import de.bluecolored.bluemap.core.map.TextureGallery;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.resources.ResourcePath;
import de.bluecolored.bluemap.core.resources.pack.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Key;
import de.bluecolored.bluemap.core.world.BlockState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

final class CameraBlockFixups {
	record GlassMaterialProfile(boolean enabled, float alphaScale, float shadeFloor, float aoFloor) {
		static final GlassMaterialProfile DISABLED = new GlassMaterialProfile(false, 1.0F, 0.0F, 0.0F);
	}

	record TransparentMaterialLight(float alpha, float shade) {
	}

	private CameraBlockFixups() {
	}

	static boolean shouldUseFixedItemFallback(Identifier blockId) {
		if (blockId == null) {
			return false;
		}
		String path = blockId.getPath();
		return path.equals("decorated_pot")
				|| path.equals("bell")
				|| path.equals("conduit")
				|| path.equals("spawner")
				|| path.equals("vault")
				|| path.equals("trial_spawner")
				|| path.endsWith("shulker_box");
	}

	static GlassMaterialProfile glassProfile(String texturePath) {
		if (texturePath == null || !texturePath.startsWith("block/")) {
			return GlassMaterialProfile.DISABLED;
		}
		if (texturePath.equals("block/glass") || texturePath.endsWith("_stained_glass")) {
			return new GlassMaterialProfile(true, 0.24F, 0.94F, 0.97F);
		}
		if (texturePath.equals("block/tinted_glass")) {
			return new GlassMaterialProfile(true, 0.38F, 0.88F, 0.94F);
		}
		return GlassMaterialProfile.DISABLED;
	}

	static boolean isCameraFriendlyGlassBlock(BlockState blockState) {
		if (blockState == null) {
			return false;
		}
		Key id = blockState.getId();
		if (!Key.MINECRAFT_NAMESPACE.equals(id.getNamespace())) {
			return false;
		}
		String value = id.getValue();
		return "glass".equals(value)
				|| "tinted_glass".equals(value)
				|| value.endsWith("_stained_glass");
	}

	static void renderCameraFriendlyGlassBlock(
			ArrayTileModel model,
			BlueMapCameraRenderer.WorldSnapshot snapshot,
			BlueMapCameraRenderer.SnapshotBlock snapshotBlock,
			int x,
			int y,
			int z,
			TextureGallery textureGallery,
			Int2ObjectOpenHashMap<BlueMapCameraRenderer.TextureMaterial> materials
	) {
		int materialId = glassMaterialId(snapshotBlock.state(), textureGallery, materials);
		if (materialId < 0) {
			return;
		}
		Key stateId = snapshotBlock.state().getId();
		int skyLight = snapshotBlock.light().getSkyLight();
		int blockLight = snapshotBlock.light().getBlockLight();
		float x0 = x;
		float x1 = x + 1.0F;
		float y0 = y;
		float y1 = y + 1.0F;
		float z0 = z;
		float z1 = z + 1.0F;

		if (!matchesCameraFriendlyGlass(snapshot, stateId, x, y + 1, z)) {
			addGlassQuad(model, materialId, skyLight, blockLight, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		}
		if (!matchesCameraFriendlyGlass(snapshot, stateId, x, y - 1, z)) {
			addGlassQuad(model, materialId, skyLight, blockLight, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		}
		if (!matchesCameraFriendlyGlass(snapshot, stateId, x, y, z + 1)) {
			addGlassQuad(model, materialId, skyLight, blockLight, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
		}
		if (!matchesCameraFriendlyGlass(snapshot, stateId, x, y, z - 1)) {
			addGlassQuad(model, materialId, skyLight, blockLight, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
		}
		if (!matchesCameraFriendlyGlass(snapshot, stateId, x + 1, y, z)) {
			addGlassQuad(model, materialId, skyLight, blockLight, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
		}
		if (!matchesCameraFriendlyGlass(snapshot, stateId, x - 1, y, z)) {
			addGlassQuad(model, materialId, skyLight, blockLight, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
		}
	}

	static TransparentMaterialLight cameraFriendlyGlassLight(
			float alpha,
			float sunlightLevel,
			float blocklightLevel,
			float ao,
			float faceShade,
			float sunlightStrength,
			BlueMapCameraRenderer.TextureMaterial material
	) {
		float sourceAlpha = alpha;
		float edgeWeight = smoothstep(0.44F, 0.62F, sourceAlpha);
		float skylightMix = sunlightLevel * Mth.lerp(sunlightStrength, 0.72F, 1.0F);
		float blocklightMix = blocklightLevel <= 0.0F ? 0.0F : Mth.lerp(blocklightLevel, 0.55F, 1.0F);
		float transmission = Math.max(Math.max(skylightMix, blocklightMix), material.cameraShadeFloor());
		float aoShade = Math.max(Mth.clamp(ao, 0.0F, 1.0F), material.cameraAoFloor());
		float shadedFace = Math.max(faceShade, material.cameraShadeFloor());
		float shade = Mth.clamp(shadedFace * aoShade * transmission, material.cameraShadeFloor(), 1.0F);
		float adjustedAlpha = sourceAlpha * Mth.lerp(material.cameraAlphaScale(), 0.78F, edgeWeight);
		return new TransparentMaterialLight(adjustedAlpha, shade);
	}

	private static boolean matchesCameraFriendlyGlass(BlueMapCameraRenderer.WorldSnapshot snapshot, Key stateId, int x, int y, int z) {
		BlueMapCameraRenderer.SnapshotBlock neighbor = snapshot.blockAt(x, y, z);
		return neighbor != null && stateId.equals(neighbor.state().getId());
	}

	private static int glassMaterialId(BlockState blockState, TextureGallery textureGallery, Int2ObjectOpenHashMap<BlueMapCameraRenderer.TextureMaterial> materials) {
		if (blockState == null) {
			return -1;
		}
		Key id = blockState.getId();
		Identifier textureId = Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getValue());
		int materialId = textureGallery.get(new ResourcePath<Texture>(textureId.getNamespace(), textureId.getPath()));
		return materials.containsKey(materialId) ? materialId : -1;
	}

	private static void addGlassQuad(
			ArrayTileModel model,
			int materialId,
			int skyLight,
			int blockLight,
			float ax,
			float ay,
			float az,
			float bx,
			float by,
			float bz,
			float cx,
			float cy,
			float cz,
			float dx,
			float dy,
			float dz
	) {
		int firstTriangle = model.add(2);
		model.setPositions(firstTriangle, ax, ay, az, bx, by, bz, cx, cy, cz)
				.setUvs(firstTriangle, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F)
				.setAOs(firstTriangle, 1.0F, 1.0F, 1.0F)
				.setColor(firstTriangle, 1.0F, 1.0F, 1.0F)
				.setSunlight(firstTriangle, skyLight)
				.setBlocklight(firstTriangle, blockLight)
				.setMaterialIndex(firstTriangle, materialId);
		int secondTriangle = firstTriangle + 1;
		model.setPositions(secondTriangle, ax, ay, az, cx, cy, cz, dx, dy, dz)
				.setUvs(secondTriangle, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F)
				.setAOs(secondTriangle, 1.0F, 1.0F, 1.0F)
				.setColor(secondTriangle, 1.0F, 1.0F, 1.0F)
				.setSunlight(secondTriangle, skyLight)
				.setBlocklight(secondTriangle, blockLight)
				.setMaterialIndex(secondTriangle, materialId);
	}

	private static float smoothstep(float edge0, float edge1, float value) {
		if (edge0 == edge1) {
			return value < edge0 ? 0.0F : 1.0F;
		}
		float t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}
