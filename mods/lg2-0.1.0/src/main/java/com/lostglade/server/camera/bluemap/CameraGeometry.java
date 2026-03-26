package com.lostglade.server.camera.bluemap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

final class CameraGeometry {
	private static final float PX = 1.0F / 16.0F;

	private CameraGeometry() {
	}

	static void addPlane(RenderContext context, Matrix4f transform, float x, float y, float z, float width, float height, int material) {
		addTexturedPlane(context, transform, x, y, z, width, height, 0.0F, 0.0F, 1.0F, 1.0F, material, 1.0F, 1.0F, 1.0F);
	}

	static void addDoubleSidedPlane(RenderContext context, Matrix4f transform, float x, float y, float z, float width, float height, int material) {
		addTexturedDoubleSidedPlane(context, transform, x, y, z, width, height, 0.0F, 0.0F, 1.0F, 1.0F, material, 1.0F, 1.0F, 1.0F);
	}

	static void addTexturedDoubleSidedPlane(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float u0,
			float v0,
			float u1,
			float v1,
			int material,
			float red,
			float green,
			float blue
	) {
		addTexturedPlane(context, transform, x, y, z, width, height, u0, v0, u1, v1, material, red, green, blue);
		Matrix4f back = new Matrix4f(transform).rotateY((float) Math.PI);
		addTexturedPlane(context, back, -(x + width), y, -z, width, height, u0, v0, u1, v1, material, red, green, blue);
	}

	static void addSeparatedTexturedDoubleSidedPlane(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float centerZ,
			float width,
			float height,
			float halfThickness,
			float u0,
			float v0,
			float u1,
			float v1,
			int material,
			float red,
			float green,
			float blue
	) {
		addTexturedPlane(context, transform, x, y, centerZ + halfThickness, width, height, u0, v0, u1, v1, material, red, green, blue);
		Matrix4f back = new Matrix4f(transform).rotateY((float) Math.PI);
		addTexturedPlane(context, back, -(x + width), y, -(centerZ - halfThickness), width, height, u0, v0, u1, v1, material, red, green, blue);
	}

	static void addTexturedPlane(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float texU0,
			float texV0,
			float texU1,
			float texV1,
			int material,
			float red,
			float green,
			float blue
	) {
		Vector3f p0 = transformPosition(transform, x, y, z);
		Vector3f p1 = transformPosition(transform, x + width, y, z);
		Vector3f p2 = transformPosition(transform, x + width, y + height, z);
		Vector3f p3 = transformPosition(transform, x, y + height, z);
		LightSample lightSample = context.lightAt((p0.x + p2.x) * 0.5F, (p0.y + p2.y) * 0.5F, (p0.z + p2.z) * 0.5F);
		addQuad(context, p0, p1, p2, p3, texU0, texU1, texV0, texV1, material, lightSample.sky(), lightSample.block(), red, green, blue);
	}

	static void addBox(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float depth,
			int texU,
			int texV,
			int texWidth,
			int texHeight,
			int material,
			boolean mirror,
			float inflate
	) {
		addBox(context, transform, x, y, z, width, height, depth, texU, texV, texWidth, texHeight, material, mirror, inflate, -1, -1);
	}

	static void addPlayerSkinBox(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float depth,
			int texU,
			int texV,
			int texWidth,
			int texHeight,
			int material,
			boolean mirror,
			float inflate
	) {
		float minX = x - inflate;
		float minY = y - inflate;
		float minZ = z - inflate;
		float maxX = x + width + inflate;
		float maxY = y + height + inflate;
		float maxZ = z + depth + inflate;
		if (mirror) {
			float swap = minX;
			minX = maxX;
			maxX = swap;
		}

		Vector3f nnn = transformPosition(transform, minX * PX, minY * PX, minZ * PX);
		Vector3f pnn = transformPosition(transform, maxX * PX, minY * PX, minZ * PX);
		Vector3f ppn = transformPosition(transform, maxX * PX, maxY * PX, minZ * PX);
		Vector3f npn = transformPosition(transform, minX * PX, maxY * PX, minZ * PX);
		Vector3f nnp = transformPosition(transform, minX * PX, minY * PX, maxZ * PX);
		Vector3f pnp = transformPosition(transform, maxX * PX, minY * PX, maxZ * PX);
		Vector3f ppp = transformPosition(transform, maxX * PX, maxY * PX, maxZ * PX);
		Vector3f npp = transformPosition(transform, minX * PX, maxY * PX, maxZ * PX);

		LightSample lightSample = context.lightAt((nnn.x + ppp.x) * 0.5F, (nnn.y + ppp.y) * 0.5F, (nnn.z + ppp.z) * 0.5F);
		int skyLight = lightSample.sky();
		int blockLight = lightSample.block();

		float u0 = texU;
		float v0 = texV;
		float u1 = u0 + depth;
		float u2 = u1 + width;
		float u3 = u2 + depth;
		float u4 = u3 + width;
		float v1 = v0 + depth;
		float v2 = v1 + height;

		addQuad(context, pnp, nnp, npp, ppp, uv(u2, texWidth), uv(u1, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnn, pnn, ppn, npn, uv(u4, texWidth), uv(u3, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnp, nnn, npn, npp, uv(u1, texWidth), uv(u0, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, pnp, ppp, ppn, uv(u3, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, nnn, nnp, pnp, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, npn, ppn, ppp, npp, uv(u1, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v0, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
	}

	static void addBox(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float depth,
			int texU,
			int texV,
			int texWidth,
			int texHeight,
			int material,
			boolean mirror,
			float inflate,
			int overrideSkyLight,
			int overrideBlockLight
	) {
		float minX = x - inflate;
		float minY = y - inflate;
		float minZ = z - inflate;
		float maxX = x + width + inflate;
		float maxY = y + height + inflate;
		float maxZ = z + depth + inflate;
		if (mirror) {
			float swap = minX;
			minX = maxX;
			maxX = swap;
		}

		Vector3f nnn = transformPosition(transform, minX * PX, minY * PX, minZ * PX);
		Vector3f pnn = transformPosition(transform, maxX * PX, minY * PX, minZ * PX);
		Vector3f ppn = transformPosition(transform, maxX * PX, maxY * PX, minZ * PX);
		Vector3f npn = transformPosition(transform, minX * PX, maxY * PX, minZ * PX);
		Vector3f nnp = transformPosition(transform, minX * PX, minY * PX, maxZ * PX);
		Vector3f pnp = transformPosition(transform, maxX * PX, minY * PX, maxZ * PX);
		Vector3f ppp = transformPosition(transform, maxX * PX, maxY * PX, maxZ * PX);
		Vector3f npp = transformPosition(transform, minX * PX, maxY * PX, maxZ * PX);

		LightSample lightSample = context.lightAt((nnn.x + ppp.x) * 0.5F, (nnn.y + ppp.y) * 0.5F, (nnn.z + ppp.z) * 0.5F);
		int skyLight = overrideSkyLight >= 0 ? overrideSkyLight : lightSample.sky();
		int blockLight = overrideBlockLight >= 0 ? overrideBlockLight : lightSample.block();

		float u0 = texU;
		float v0 = texV;
		float u1 = u0 + depth;
		float u2 = u1 + width;
		float u3 = u2 + depth;
		float u4 = u3 + width;
		float v1 = v0 + depth;
		float v2 = v1 + height;

		addQuad(context, pnp, nnp, npp, ppp, uv(u1, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnn, pnn, ppn, npn, uv(u3, texWidth), uv(u4, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnp, nnn, npn, npp, uv(u0, texWidth), uv(u1, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, pnp, ppp, ppn, uv(u2, texWidth), uv(u3, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, nnn, nnp, pnp, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, npn, ppn, ppp, npp, uv(u1, texWidth), uv(u2, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
	}

	static float uv(float value, int size) {
		return value / size;
	}

	static Vector3f transformPosition(Matrix4f transform, float x, float y, float z) {
		return transform.transformPosition(new Vector3f(x, y, z));
	}

	static void addQuad(
			RenderContext context,
			Vector3f a,
			Vector3f b,
			Vector3f c,
			Vector3f d,
			float u0,
			float u1,
			float v0,
			float v1,
			int material,
			int skyLight,
			int blockLight,
			float red,
			float green,
			float blue
	) {
		int triangle = context.model().add(2);
		context.model()
				.setPositions(triangle, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z)
				.setUvs(triangle, u0, v1, u1, v1, u1, v0)
				.setAOs(triangle, 1.0F, 1.0F, 1.0F)
				.setColor(triangle, red, green, blue)
				.setSunlight(triangle, skyLight)
				.setBlocklight(triangle, blockLight)
				.setMaterialIndex(triangle, material);
		context.model()
				.setPositions(triangle + 1, a.x, a.y, a.z, c.x, c.y, c.z, d.x, d.y, d.z)
				.setUvs(triangle + 1, u0, v1, u1, v0, u0, v0)
				.setAOs(triangle + 1, 1.0F, 1.0F, 1.0F)
				.setColor(triangle + 1, red, green, blue)
				.setSunlight(triangle + 1, skyLight)
				.setBlocklight(triangle + 1, blockLight)
				.setMaterialIndex(triangle + 1, material);
	}

	static void addQuadExact(
			RenderContext context,
			Vector3f a,
			Vector3f b,
			Vector3f c,
			Vector3f d,
			float au,
			float av,
			float bu,
			float bv,
			float cu,
			float cv,
			float du,
			float dv,
			int material,
			int skyLight,
			int blockLight,
			float red,
			float green,
			float blue
	) {
		int triangle = context.model().add(2);
		context.model()
				.setPositions(triangle, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z)
				.setUvs(triangle, au, av, bu, bv, cu, cv)
				.setAOs(triangle, 1.0F, 1.0F, 1.0F)
				.setColor(triangle, red, green, blue)
				.setSunlight(triangle, skyLight)
				.setBlocklight(triangle, blockLight)
				.setMaterialIndex(triangle, material);
		context.model()
				.setPositions(triangle + 1, a.x, a.y, a.z, c.x, c.y, c.z, d.x, d.y, d.z)
				.setUvs(triangle + 1, au, av, cu, cv, du, dv)
				.setAOs(triangle + 1, 1.0F, 1.0F, 1.0F)
				.setColor(triangle + 1, red, green, blue)
				.setSunlight(triangle + 1, skyLight)
				.setBlocklight(triangle + 1, blockLight)
				.setMaterialIndex(triangle + 1, material);
	}
}
