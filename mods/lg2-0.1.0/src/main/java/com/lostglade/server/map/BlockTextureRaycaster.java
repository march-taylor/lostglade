package com.lostglade.server.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockTextureRaycaster {
	private static final double EPSILON = 1.0E-4D;
	private static final TextureAssetManager ASSETS = TextureAssetManager.get();
	private static final Map<String, ResolvedVariant> VARIANT_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, ResolvedModel> MODEL_CACHE = new ConcurrentHashMap<>();

	private BlockTextureRaycaster() {
	}

	public static BlockTraceResult trace(BlockState state, BlockPos pos, Vec3 worldOrigin, Vec3 worldDirection, int[] tintColors) {
		ResolvedVariant variant = resolveVariant(state);
		if (variant == null || variant.parts().isEmpty()) {
			return null;
		}

		return traceVariant(variant, pos, worldOrigin, worldDirection, tintColors, false);
	}

	public static BlockTraceResult traceTopDown(BlockState state, BlockPos pos, Vec3 worldOrigin, Vec3 worldDirection, int[] tintColors) {
		ResolvedVariant variant = resolveVariant(state);
		if (variant == null || variant.parts().isEmpty()) {
			return null;
		}

		return traceVariant(variant, pos, worldOrigin, worldDirection, tintColors, true);
	}

	public static boolean hitsTopDownGeometry(BlockState state, BlockPos pos, Vec3 worldOrigin, Vec3 worldDirection) {
		ResolvedVariant variant = resolveVariant(state);
		if (variant == null || variant.parts().isEmpty()) {
			return false;
		}

		return traceVariant(variant, pos, worldOrigin, worldDirection, null, true, true) != null;
	}

	public static BlockTraceResult traceModelTopDown(Identifier modelId, Vec3 localOrigin, Vec3 localDirection, int[] tintColors) {
		ResolvedModel model = resolveModel(modelId);
		if (model == null || model.elements().isEmpty()) {
			return null;
		}
		ModelBounds bounds = boundsFor(model);
		if (bounds == null) {
			return null;
		}
		ResolvedVariant variant = new ResolvedVariant(List.of(new ModelPart(model, new ModelTransform(0, 0, false))));
		return traceVariant(
				variant,
				BlockPos.ZERO,
				localOrigin,
				localDirection,
				tintColors,
				true,
				bounds
		);
	}

	public static BlockTraceResult traceModelTopDownNormalized(Identifier modelId, double normalizedX, double normalizedZ, int[] tintColors) {
		ResolvedModel model = resolveModel(modelId);
		if (model == null || model.elements().isEmpty()) {
			return null;
		}
		ModelBounds bounds = boundsFor(model);
		if (bounds == null) {
			return null;
		}
		double localX = Mth.lerp(Mth.clamp(normalizedX, 0.0D, 1.0D), bounds.minX(), bounds.maxX());
		double localZ = Mth.lerp(Mth.clamp(normalizedZ, 0.0D, 1.0D), bounds.minZ(), bounds.maxZ());
		ResolvedVariant variant = new ResolvedVariant(List.of(new ModelPart(model, new ModelTransform(0, 0, false))));
		return traceVariant(
				variant,
				BlockPos.ZERO,
				new Vec3(localX, bounds.maxY() + 64.0D, localZ),
				new Vec3(0.0D, -1.0D, 0.0D),
				tintColors,
				true,
				bounds
		);
	}

	private static BlockTraceResult traceVariant(ResolvedVariant variant, BlockPos pos, Vec3 worldOrigin, Vec3 worldDirection, int[] tintColors, boolean topDownOnly) {
		return traceVariant(variant, pos, worldOrigin, worldDirection, tintColors, topDownOnly, false);
	}

	private static BlockTraceResult traceVariant(ResolvedVariant variant, BlockPos pos, Vec3 worldOrigin, Vec3 worldDirection, int[] tintColors, boolean topDownOnly, boolean geometryOnly) {
		return traceVariant(
				variant,
				pos,
				worldOrigin.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0D),
				worldDirection.scale(16.0D),
				tintColors,
				topDownOnly,
				new ModelBounds(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
				geometryOnly
		);
	}

	private static BlockTraceResult traceVariant(
			ResolvedVariant variant,
			BlockPos pos,
			Vec3 localOrigin,
			Vec3 localDirection,
			int[] tintColors,
			boolean topDownOnly,
			ModelBounds bounds
	) {
		return traceVariant(variant, pos, localOrigin, localDirection, tintColors, topDownOnly, bounds, false);
	}

	private static BlockTraceResult traceVariant(
			ResolvedVariant variant,
			BlockPos pos,
			Vec3 localOrigin,
			Vec3 localDirection,
			int[] tintColors,
			boolean topDownOnly,
			ModelBounds bounds,
			boolean geometryOnly
	) {
		Ray localRay = new Ray(localOrigin, localDirection);
		DoubleRange blockRange = intersectAabb(
				localRay.origin(),
				localRay.direction(),
				bounds.minX(),
				bounds.minY(),
				bounds.minZ(),
				bounds.maxX(),
				bounds.maxY(),
				bounds.maxZ()
		);
		if (blockRange == null) {
			return null;
		}

		double minT = Math.max(0.0D, blockRange.min());
		double maxT = blockRange.max();
		for (int passes = 0; passes < 8 && minT <= maxT; passes++) {
			FaceHit nearest = null;
			for (ModelPart part : variant.parts()) {
				if (part.model() == null || part.model().elements().isEmpty()) {
					continue;
				}
				Ray partRay = inverseRotateVariant(localRay.origin(), localRay.direction(), part.transform());
				for (ModelElement element : part.model().elements()) {
					FaceHit candidate = firstFaceHit(element, partRay, minT, maxT, part.model(), part.transform(), topDownOnly);
					if (candidate != null && (nearest == null || candidate.t() < nearest.t())) {
						nearest = candidate;
					}
				}
			}
			if (nearest == null) {
				break;
			}

			int argb = geometryOnly ? 0xFFFFFFFF : sampleTexture(nearest, tintColors);
			if (geometryOnly || ((argb >>> 24) & 0xFF) > 8) {
				Vec3 blockLocalHit = rotateVariantPointForward(nearest.hitPoint(), nearest.transform());
				Vec3 worldHit = new Vec3(
						pos.getX() + blockLocalHit.x / 16.0D,
						pos.getY() + blockLocalHit.y / 16.0D,
						pos.getZ() + blockLocalHit.z / 16.0D
				);
				Direction shadeDirection = rotateDirectionForward(nearest.direction(), nearest.transform(), nearest.elementRotation());
				return new BlockTraceResult(argb, worldHit, shadeDirection, nearest.shade());
			}
			minT = nearest.t() + EPSILON;
		}
		return null;
	}

	public static boolean hasResolvableModel(BlockState state) {
		ResolvedVariant variant = resolveVariant(state);
		return variant != null && !variant.parts().isEmpty();
	}

	private static int sampleTexture(FaceHit hit, int[] tintColors) {
		String textureRef = hit.face().texture();
		if (textureRef == null || textureRef.isBlank()) {
			return 0;
		}
		Identifier textureId = Identifier.tryParse(textureRef);
		if (textureId == null) {
			return 0;
		}
		BufferedImage texture = ASSETS.loadTexture(textureId);
		if (texture == null) {
			return 0;
		}

		double u = wrapUv(hit.u());
		double v = wrapUv(hit.v());
		int x = Mth.clamp((int) Math.floor(u / 16.0D * texture.getWidth()), 0, texture.getWidth() - 1);
		int y = Mth.clamp((int) Math.floor(v / 16.0D * texture.getHeight()), 0, texture.getHeight() - 1);
		int argb = texture.getRGB(x, y);
		int tintIndex = hit.face().tintIndex();
		if (tintIndex >= 0 && tintColors != null && tintIndex < tintColors.length && tintColors[tintIndex] != -1) {
			argb = applyTint(argb, tintColors[tintIndex]);
		}
		return argb;
	}

	private static int applyTint(int argb, int tintRgb) {
		int alpha = (argb >>> 24) & 0xFF;
		int red = ((argb >> 16) & 0xFF) * ((tintRgb >> 16) & 0xFF) / 255;
		int green = ((argb >> 8) & 0xFF) * ((tintRgb >> 8) & 0xFF) / 255;
		int blue = (argb & 0xFF) * (tintRgb & 0xFF) / 255;
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static double wrapUv(double value) {
		double wrapped = value % 16.0D;
		return wrapped < 0.0D ? wrapped + 16.0D : wrapped;
	}

	private static FaceHit firstFaceHit(
			ModelElement element,
			Ray ray,
			double minT,
			double maxT,
			ResolvedModel model,
			ModelTransform transform,
			boolean topDownOnly
	) {
		Ray elementRay = inverseRotateElement(ray, element.rotation());
		FaceHit nearest = null;
		for (Map.Entry<Direction, ModelFace> entry : element.faces().entrySet()) {
			if (topDownOnly && !isTopVisible(entry.getKey(), transform, element.rotation())) {
				continue;
			}
			FaceHit candidate = intersectFace(element, entry.getKey(), entry.getValue(), ray, elementRay, minT, maxT, model, transform);
			if (candidate != null && (nearest == null || candidate.t() < nearest.t())) {
				nearest = candidate;
			}
		}
		return nearest;
	}

	private static boolean isTopVisible(Direction direction, ModelTransform transform, ElementRotation elementRotation) {
		Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
		normal = rotateElementVector(normal, elementRotation);
		normal = rotateAroundX(rotateAroundY(normal, transform.y()), transform.x());
		return normal.y > EPSILON;
	}

	private static FaceHit intersectFace(
			ModelElement element,
			Direction direction,
			ModelFace face,
			Ray ray,
			Ray elementRay,
			double minT,
			double maxT,
			ResolvedModel model,
			ModelTransform transform
	) {
		Vec3 from = element.from();
		Vec3 to = element.to();
		double plane;
		double t;
		double x;
		double y;
		double z;
		switch (direction) {
			case NORTH -> {
				if (Math.abs(elementRay.direction().z) < EPSILON) {
					return null;
				}
				plane = from.z;
				t = (plane - elementRay.origin().z) / elementRay.direction().z;
				x = elementRay.origin().x + elementRay.direction().x * t;
				y = elementRay.origin().y + elementRay.direction().y * t;
				z = plane;
				if (!between(x, from.x, to.x) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case SOUTH -> {
				if (Math.abs(elementRay.direction().z) < EPSILON) {
					return null;
				}
				plane = to.z;
				t = (plane - elementRay.origin().z) / elementRay.direction().z;
				x = elementRay.origin().x + elementRay.direction().x * t;
				y = elementRay.origin().y + elementRay.direction().y * t;
				z = plane;
				if (!between(x, from.x, to.x) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case WEST -> {
				if (Math.abs(elementRay.direction().x) < EPSILON) {
					return null;
				}
				plane = from.x;
				t = (plane - elementRay.origin().x) / elementRay.direction().x;
				x = plane;
				y = elementRay.origin().y + elementRay.direction().y * t;
				z = elementRay.origin().z + elementRay.direction().z * t;
				if (!between(z, from.z, to.z) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case EAST -> {
				if (Math.abs(elementRay.direction().x) < EPSILON) {
					return null;
				}
				plane = to.x;
				t = (plane - elementRay.origin().x) / elementRay.direction().x;
				x = plane;
				y = elementRay.origin().y + elementRay.direction().y * t;
				z = elementRay.origin().z + elementRay.direction().z * t;
				if (!between(z, from.z, to.z) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case UP -> {
				if (Math.abs(elementRay.direction().y) < EPSILON) {
					return null;
				}
				plane = to.y;
				t = (plane - elementRay.origin().y) / elementRay.direction().y;
				x = elementRay.origin().x + elementRay.direction().x * t;
				y = plane;
				z = elementRay.origin().z + elementRay.direction().z * t;
				if (!between(x, from.x, to.x) || !between(z, from.z, to.z)) {
					return null;
				}
			}
			case DOWN -> {
				if (Math.abs(elementRay.direction().y) < EPSILON) {
					return null;
				}
				plane = from.y;
				t = (plane - elementRay.origin().y) / elementRay.direction().y;
				x = elementRay.origin().x + elementRay.direction().x * t;
				y = plane;
				z = elementRay.origin().z + elementRay.direction().z * t;
				if (!between(x, from.x, to.x) || !between(z, from.z, to.z)) {
					return null;
				}
			}
			default -> {
				return null;
			}
		}

		double[] uv = face.uv() == null ? defaultUv(direction, from, to) : face.uv();
		UvPoint point = uvFor(direction, x, y, z, from, to, uv, face.rotation());
		String resolvedTexture = resolveTextureRef(model.textures(), face.texture());
		if (resolvedTexture == null) {
			return null;
		}
		Vec3 hitPoint = transformElementPoint(new Vec3(x, y, z), element.rotation());
		double rayT = projectRayParameter(ray, hitPoint);
		if (rayT < minT || rayT > maxT) {
			return null;
		}
		return new FaceHit(rayT, direction, new ModelFace(resolvedTexture, uv, face.rotation(), face.tintIndex()), point.u(), point.v(), hitPoint, element.shade(), element.rotation(), transform);
	}

	private static double[] defaultUv(Direction direction, Vec3 from, Vec3 to) {
		return switch (direction) {
			case DOWN -> new double[]{from.x, 16.0D - to.z, to.x, 16.0D - from.z};
			case UP -> new double[]{from.x, from.z, to.x, to.z};
			case NORTH -> new double[]{16.0D - to.x, 16.0D - to.y, 16.0D - from.x, 16.0D - from.y};
			case SOUTH -> new double[]{from.x, 16.0D - to.y, to.x, 16.0D - from.y};
			case WEST -> new double[]{from.z, 16.0D - to.y, to.z, 16.0D - from.y};
			case EAST -> new double[]{16.0D - to.z, 16.0D - to.y, 16.0D - from.z, 16.0D - from.y};
		};
	}

	private static UvPoint uvFor(
			Direction direction,
			double x,
			double y,
			double z,
			Vec3 from,
			Vec3 to,
			double[] uv,
			int rotation
	) {
		double uAxis;
		double vAxis;
		switch (direction) {
			case NORTH -> {
				uAxis = 16.0D - x;
				vAxis = 16.0D - y;
			}
			case SOUTH -> {
				uAxis = x;
				vAxis = 16.0D - y;
			}
			case WEST -> {
				uAxis = z;
				vAxis = 16.0D - y;
			}
			case EAST -> {
				uAxis = 16.0D - z;
				vAxis = 16.0D - y;
			}
			case UP -> {
				uAxis = x;
				vAxis = z;
			}
			case DOWN -> {
				uAxis = x;
				vAxis = 16.0D - z;
			}
			default -> throw new IllegalStateException("Unexpected face " + direction);
		}
		double u = Mth.lerp(normalizeAxis(uAxis, uv[0], uv[2]), uv[0], uv[2]);
		double v = Mth.lerp(normalizeAxis(vAxis, uv[1], uv[3]), uv[1], uv[3]);
		return rotateUv(u, v, uv, rotation);
	}

	private static double normalizeAxis(double axisValue, double min, double max) {
		double span = Math.max(EPSILON, Math.abs(max - min));
		return Mth.clamp((axisValue - Math.min(min, max)) / span, 0.0D, 1.0D);
	}

	private static UvPoint rotateUv(double u, double v, double[] uv, int rotation) {
		double centerU = (uv[0] + uv[2]) * 0.5D;
		double centerV = (uv[1] + uv[3]) * 0.5D;
		double localU = u - centerU;
		double localV = v - centerV;
		return switch ((rotation % 360 + 360) % 360) {
			case 90 -> new UvPoint(centerU - localV, centerV + localU);
			case 180 -> new UvPoint(centerU - localU, centerV - localV);
			case 270 -> new UvPoint(centerU + localV, centerV - localU);
			default -> new UvPoint(u, v);
		};
	}

	private static boolean between(double value, double min, double max) {
		return value >= Math.min(min, max) - EPSILON && value <= Math.max(min, max) + EPSILON;
	}

	private static double projectRayParameter(Ray ray, Vec3 point) {
		Vec3 offset = point.subtract(ray.origin());
		double lengthSqr = ray.direction().lengthSqr();
		if (lengthSqr < EPSILON) {
			return Double.POSITIVE_INFINITY;
		}
		return offset.dot(ray.direction()) / lengthSqr;
	}

	private static Ray inverseRotateVariant(Vec3 origin, Vec3 direction, ModelTransform transform) {
		Vec3 center = new Vec3(8.0D, 8.0D, 8.0D);
		Vec3 rotatedOrigin = rotateAroundY(rotateAroundX(origin.subtract(center), -transform.x()), -transform.y()).add(center);
		Vec3 rotatedDirection = rotateAroundY(rotateAroundX(direction, -transform.x()), -transform.y());
		return new Ray(rotatedOrigin, rotatedDirection);
	}

	private static Vec3 rotateVariantPointForward(Vec3 point, ModelTransform transform) {
		Vec3 center = new Vec3(8.0D, 8.0D, 8.0D);
		return rotateAroundX(rotateAroundY(point.subtract(center), transform.y()), transform.x()).add(center);
	}

	private static Direction rotateDirectionForward(Direction direction, ModelTransform transform, ElementRotation elementRotation) {
		Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
		normal = rotateElementVector(normal, elementRotation);
		normal = rotateAroundX(rotateAroundY(normal, transform.y()), transform.x());
		return dominantDirection(normal);
	}

	private static Direction dominantDirection(Vec3 vector) {
		double absX = Math.abs(vector.x);
		double absY = Math.abs(vector.y);
		double absZ = Math.abs(vector.z);
		if (absY >= absX && absY >= absZ) {
			return vector.y >= 0.0D ? Direction.UP : Direction.DOWN;
		}
		if (absX >= absZ) {
			return vector.x >= 0.0D ? Direction.EAST : Direction.WEST;
		}
		return vector.z >= 0.0D ? Direction.SOUTH : Direction.NORTH;
	}

	private static Ray inverseRotateElement(Ray ray, ElementRotation rotation) {
		if (rotation == null || Math.abs(rotation.angle()) < EPSILON) {
			return ray;
		}
		Vector3f origin = new Vector3f(
				(float) (ray.origin().x - rotation.origin().x),
				(float) (ray.origin().y - rotation.origin().y),
				(float) (ray.origin().z - rotation.origin().z)
		);
		rotation.inverse().transformPosition(origin);
		origin.add((float) rotation.origin().x, (float) rotation.origin().y, (float) rotation.origin().z);
		Vector3f direction = new Vector3f((float) ray.direction().x, (float) ray.direction().y, (float) ray.direction().z);
		rotation.inverse().transformDirection(direction);
		return new Ray(
				new Vec3(origin.x, origin.y, origin.z),
				new Vec3(direction.x, direction.y, direction.z)
		);
	}

	private static Vec3 transformElementPoint(Vec3 point, ElementRotation rotation) {
		if (rotation == null || Math.abs(rotation.angle()) < EPSILON) {
			return point;
		}
		Vector3f vector = new Vector3f(
				(float) (point.x - rotation.origin().x),
				(float) (point.y - rotation.origin().y),
				(float) (point.z - rotation.origin().z)
		);
		rotation.transform().transformPosition(vector);
		vector.add((float) rotation.origin().x, (float) rotation.origin().y, (float) rotation.origin().z);
		return new Vec3(vector.x, vector.y, vector.z);
	}

	private static Vec3 rotateElementVector(Vec3 vector, ElementRotation rotation) {
		if (rotation == null || Math.abs(rotation.angle()) < EPSILON) {
			return vector;
		}
		Vector3f transformed = new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
		buildRotationMatrix(rotation.axis(), rotation.angle()).transformDirection(transformed);
		return new Vec3(transformed.x, transformed.y, transformed.z);
	}

	private static ElementRotation buildElementRotation(Vec3 origin, Direction.Axis axis, float angle, boolean rescale) {
		Matrix4f transform = buildRotationMatrix(axis, angle);
		if (rescale && Math.abs(angle) >= EPSILON) {
			Vector3f scale = computeRescale(transform);
			transform.scale(scale);
		}
		Matrix4f inverse = new Matrix4f(transform).invert();
		return new ElementRotation(origin, axis, angle, rescale, transform, inverse);
	}

	private static Matrix4f buildRotationMatrix(Direction.Axis axis, float angle) {
		Vector3f axisVector = switch (axis) {
			case X -> new Vector3f(1.0F, 0.0F, 0.0F);
			case Y -> new Vector3f(0.0F, 1.0F, 0.0F);
			case Z -> new Vector3f(0.0F, 0.0F, 1.0F);
		};
		return new Matrix4f().rotation((float) Math.toRadians(angle), axisVector);
	}

	private static Vector3f computeRescale(Matrix4f transform) {
		return new Vector3f(
				axisRescale(transform, new Vector3f(1.0F, 0.0F, 0.0F)),
				axisRescale(transform, new Vector3f(0.0F, 1.0F, 0.0F)),
				axisRescale(transform, new Vector3f(0.0F, 0.0F, 1.0F))
		);
	}

	private static float axisRescale(Matrix4f transform, Vector3f axisVector) {
		Vector3f transformed = new Vector3f(axisVector);
		transform.transformDirection(transformed);
		float max = Math.max(Math.max(Math.abs(transformed.x), Math.abs(transformed.y)), Math.abs(transformed.z));
		return max <= 1.0E-6F ? 1.0F : 1.0F / max;
	}

	private static Vec3 rotateAroundX(Vec3 vector, int degrees) {
		if (degrees == 0) {
			return vector;
		}
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(vector.x, vector.y * cos - vector.z * sin, vector.y * sin + vector.z * cos);
	}

	private static Vec3 rotateAroundY(Vec3 vector, int degrees) {
		if (degrees == 0) {
			return vector;
		}
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(vector.x * cos + vector.z * sin, vector.y, -vector.x * sin + vector.z * cos);
	}

	private static DoubleRange intersectAabb(Vec3 origin, Vec3 direction, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		double tMin = 0.0D;
		double tMax = Double.MAX_VALUE;
		double[] origins = {origin.x, origin.y, origin.z};
		double[] directions = {direction.x, direction.y, direction.z};
		double[] mins = {minX, minY, minZ};
		double[] maxs = {maxX, maxY, maxZ};
		for (int axis = 0; axis < 3; axis++) {
			double dir = directions[axis];
			if (Math.abs(dir) < EPSILON) {
				if (origins[axis] < mins[axis] || origins[axis] > maxs[axis]) {
					return null;
				}
				continue;
			}
			double inv = 1.0D / dir;
			double t1 = (mins[axis] - origins[axis]) * inv;
			double t2 = (maxs[axis] - origins[axis]) * inv;
			if (t1 > t2) {
				double temp = t1;
				t1 = t2;
				t2 = temp;
			}
			tMin = Math.max(tMin, t1);
			tMax = Math.min(tMax, t2);
			if (tMax < tMin) {
				return null;
			}
		}
		return new DoubleRange(tMin, tMax);
	}

	private static ResolvedVariant resolveVariant(BlockState state) {
		Block block = state.getBlock();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
		if (blockId == null) {
			return null;
		}
		String cacheKey = blockId + "|" + state;
		ResolvedVariant cached = VARIANT_CACHE.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		ResolvedVariant resolved = doResolveVariant(blockId, state);
		if (resolved != null) {
			VARIANT_CACHE.putIfAbsent(cacheKey, resolved);
		}
		return resolved;
	}

	private static ResolvedVariant doResolveVariant(Identifier blockId, BlockState state) {
		JsonObject blockStateJson = ASSETS.loadBlockState(blockId);
		if (blockStateJson == null) {
			ResolvedModel directModel = resolveModel(Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath()));
			return directModel == null ? null : singleModelVariant(directModel, 0, 0, false);
		}
		if (blockStateJson.has("multipart")) {
			return resolveMultipart(blockStateJson.getAsJsonArray("multipart"), state);
		}
		if (!blockStateJson.has("variants")) {
			ResolvedModel directModel = resolveModel(Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath()));
			return directModel == null ? null : singleModelVariant(directModel, 0, 0, false);
		}
		JsonObject variants = blockStateJson.getAsJsonObject("variants");
		for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
			if (!matchesVariantKey(state, entry.getKey())) {
				continue;
			}
			JsonObject variantObject = firstVariantObject(entry.getValue());
			if (variantObject == null) {
				continue;
			}
			Identifier modelId = Identifier.tryParse(variantObject.get("model").getAsString());
			if (modelId == null) {
				continue;
			}
			ResolvedModel model = resolveModel(modelId);
			if (model == null) {
				continue;
			}
			int x = variantObject.has("x") ? variantObject.get("x").getAsInt() : 0;
			int y = variantObject.has("y") ? variantObject.get("y").getAsInt() : 0;
			boolean uvlock = variantObject.has("uvlock") && variantObject.get("uvlock").getAsBoolean();
			return singleModelVariant(model, x, y, uvlock);
		}
		return null;
	}

	private static ResolvedVariant resolveMultipart(JsonArray multipart, BlockState state) {
		List<ModelPart> parts = new ArrayList<>();
		for (JsonElement element : multipart) {
			JsonObject part = element.getAsJsonObject();
			if (part.has("when") && !matchesMultipartCondition(state, part.get("when"))) {
				continue;
			}
			JsonElement apply = part.get("apply");
			if (apply == null) {
				continue;
			}
			JsonObject applyObject = firstVariantObject(apply);
			if (applyObject == null) {
				continue;
			}
			ModelPart resolvedPart = resolveModelPart(applyObject);
			if (resolvedPart != null) {
				parts.add(resolvedPart);
			}
		}
		return parts.isEmpty() ? null : new ResolvedVariant(parts);
	}

	private static ResolvedVariant singleModelVariant(ResolvedModel model, int x, int y, boolean uvlock) {
		return new ResolvedVariant(List.of(new ModelPart(model, new ModelTransform(x, y, uvlock))));
	}

	private static ModelBounds boundsFor(ResolvedModel model) {
		if (model == null || model.elements().isEmpty()) {
			return null;
		}
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (ModelElement element : model.elements()) {
			if (element == null) {
				continue;
			}
			double fromX = Math.min(element.from().x, element.to().x);
			double fromY = Math.min(element.from().y, element.to().y);
			double fromZ = Math.min(element.from().z, element.to().z);
			double toX = Math.max(element.from().x, element.to().x);
			double toY = Math.max(element.from().y, element.to().y);
			double toZ = Math.max(element.from().z, element.to().z);
			double[] xs = {fromX, toX};
			double[] ys = {fromY, toY};
			double[] zs = {fromZ, toZ};
			for (double x : xs) {
				for (double y : ys) {
					for (double z : zs) {
						Vec3 corner = transformElementPoint(new Vec3(x, y, z), element.rotation());
						minX = Math.min(minX, corner.x);
						minY = Math.min(minY, corner.y);
						minZ = Math.min(minZ, corner.z);
						maxX = Math.max(maxX, corner.x);
						maxY = Math.max(maxY, corner.y);
						maxZ = Math.max(maxZ, corner.z);
					}
				}
			}
		}
		if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
				|| !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
			return null;
		}
		return new ModelBounds(minX - EPSILON, minY - EPSILON, minZ - EPSILON, maxX + EPSILON, maxY + EPSILON, maxZ + EPSILON);
	}

	private static ModelPart resolveModelPart(JsonObject applyObject) {
		if (!applyObject.has("model")) {
			return null;
		}
		Identifier modelId = Identifier.tryParse(applyObject.get("model").getAsString());
		if (modelId == null) {
			return null;
		}
		ResolvedModel model = resolveModel(modelId);
		if (model == null) {
			return null;
		}
		int x = applyObject.has("x") ? applyObject.get("x").getAsInt() : 0;
		int y = applyObject.has("y") ? applyObject.get("y").getAsInt() : 0;
		boolean uvlock = applyObject.has("uvlock") && applyObject.get("uvlock").getAsBoolean();
		return new ModelPart(model, new ModelTransform(x, y, uvlock));
	}

	private static JsonObject firstVariantObject(JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return null;
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			if (array.isEmpty()) {
				return null;
			}
			JsonElement first = array.get(0);
			return first.isJsonObject() ? first.getAsJsonObject() : null;
		}
		return element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	private static boolean matchesMultipartCondition(BlockState state, JsonElement whenElement) {
		if (whenElement == null || whenElement.isJsonNull()) {
			return true;
		}
		if (whenElement.isJsonObject()) {
			JsonObject object = whenElement.getAsJsonObject();
			if (object.has("OR")) {
				for (JsonElement option : object.getAsJsonArray("OR")) {
					if (matchesMultipartCondition(state, option)) {
						return true;
					}
				}
				return false;
			}
			if (object.has("AND")) {
				for (JsonElement option : object.getAsJsonArray("AND")) {
					if (!matchesMultipartCondition(state, option)) {
						return false;
					}
				}
				return true;
			}
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
				if (property == null) {
					return false;
				}
				String expected = entry.getValue().getAsString();
				boolean matched = false;
				for (String value : expected.split("\\|")) {
					if (matchesPropertyValue(state, property, value)) {
						matched = true;
						break;
					}
				}
				if (!matched) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	private static boolean matchesVariantKey(BlockState state, String key) {
		if (key == null || key.isBlank()) {
			return true;
		}
		for (String token : key.split(",")) {
			String[] parts = token.split("=", 2);
			if (parts.length != 2) {
				return false;
			}
			Property<?> property = state.getBlock().getStateDefinition().getProperty(parts[0]);
			if (property == null) {
				return false;
			}
			if (!matchesPropertyValue(state, property, parts[1])) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static boolean matchesPropertyValue(BlockState state, Property property, String expected) {
		Comparable value = state.getValue(property);
		return property.getName(value).equals(expected);
	}

	private static ResolvedModel resolveModel(Identifier modelId) {
		return resolveModel(modelId, new HashSet<>());
	}

	private static ResolvedModel resolveModel(Identifier modelId, HashSet<String> resolving) {
		String cacheKey = modelId.toString();
		ResolvedModel cached = MODEL_CACHE.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		if (!resolving.add(cacheKey)) {
			return null;
		}
		ResolvedModel resolved;
		try {
			resolved = doResolveModel(modelId, resolving);
		} finally {
			resolving.remove(cacheKey);
		}
		if (resolved != null) {
			MODEL_CACHE.putIfAbsent(cacheKey, resolved);
		}
		return resolved;
	}

	private static ResolvedModel doResolveModel(Identifier modelId, HashSet<String> resolving) {
		JsonObject json = ASSETS.loadModel(modelId);
		if (json == null) {
			return null;
		}

		Map<String, String> textures = new HashMap<>();
		List<ModelElement> elements = new ArrayList<>();
		if (json.has("parent")) {
			Identifier parentId = Identifier.tryParse(json.get("parent").getAsString());
			if (parentId != null && !"builtin/generated".equals(parentId.toString())) {
				ResolvedModel parent = resolveModel(parentId, resolving);
				if (parent != null) {
					textures.putAll(parent.textures());
					elements.addAll(parent.elements());
				}
			}
		}
		if (json.has("textures")) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
				textures.put(entry.getKey(), entry.getValue().getAsString());
			}
		}
		if (json.has("elements")) {
			elements.clear();
			JsonArray array = json.getAsJsonArray("elements");
			for (JsonElement elementJson : array) {
				JsonObject object = elementJson.getAsJsonObject();
				Vec3 from = readVec3(object.getAsJsonArray("from"));
				Vec3 to = readVec3(object.getAsJsonArray("to"));
				ElementRotation elementRotation = readElementRotation(object);
				boolean shade = !object.has("shade") || object.get("shade").getAsBoolean();
				Map<Direction, ModelFace> faces = new HashMap<>();
				JsonObject facesJson = object.getAsJsonObject("faces");
				for (Map.Entry<String, JsonElement> faceEntry : facesJson.entrySet()) {
					Direction direction = Direction.byName(faceEntry.getKey());
					if (direction == null) {
						continue;
					}
					JsonObject faceJson = faceEntry.getValue().getAsJsonObject();
					double[] uv = faceJson.has("uv") ? readUv(faceJson.getAsJsonArray("uv")) : null;
					int faceRotation = faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0;
					int tintIndex = faceJson.has("tintindex") ? faceJson.get("tintindex").getAsInt() : -1;
					faces.put(direction, new ModelFace(faceJson.get("texture").getAsString(), uv, faceRotation, tintIndex));
				}
				elements.add(new ModelElement(from, to, faces, elementRotation, shade));
			}
		}
		return new ResolvedModel(textures, elements);
	}

	private static ElementRotation readElementRotation(JsonObject object) {
		if (!object.has("rotation")) {
			return null;
		}
		JsonObject rotationJson = object.getAsJsonObject("rotation");
		Vec3 origin = rotationJson.has("origin") ? readVec3(rotationJson.getAsJsonArray("origin")) : new Vec3(8.0D, 8.0D, 8.0D);
		Direction.Axis axis;
		float angle;
		if (rotationJson.has("axis") && rotationJson.has("angle")) {
			String axisName = rotationJson.get("axis").getAsString();
			axis = switch (axisName) {
				case "x" -> Direction.Axis.X;
				case "y" -> Direction.Axis.Y;
				case "z" -> Direction.Axis.Z;
				default -> null;
			};
			angle = rotationJson.get("angle").getAsFloat();
		} else {
			AxisAngle blockbenchRotation = firstBlockbenchAxisAngle(rotationJson);
			if (blockbenchRotation == null) {
				return null;
			}
			axis = blockbenchRotation.axis();
			angle = blockbenchRotation.angle();
		}
		if (axis == null) {
			return null;
		}
		boolean rescale = rotationJson.has("rescale") && rotationJson.get("rescale").getAsBoolean();
		return buildElementRotation(origin, axis, angle, rescale);
	}

	private static AxisAngle firstBlockbenchAxisAngle(JsonObject rotationJson) {
		float x = rotationJson.has("x") ? rotationJson.get("x").getAsFloat() : 0.0F;
		float y = rotationJson.has("y") ? rotationJson.get("y").getAsFloat() : 0.0F;
		float z = rotationJson.has("z") ? rotationJson.get("z").getAsFloat() : 0.0F;
		if (Math.abs(x) > EPSILON) {
			return new AxisAngle(Direction.Axis.X, x);
		}
		if (Math.abs(y) > EPSILON) {
			return new AxisAngle(Direction.Axis.Y, y);
		}
		if (Math.abs(z) > EPSILON) {
			return new AxisAngle(Direction.Axis.Z, z);
		}
		return null;
	}

	private static Vec3 readVec3(JsonArray array) {
		return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
	}

	private static double[] readUv(JsonArray array) {
		return new double[]{
				array.get(0).getAsDouble(),
				array.get(1).getAsDouble(),
				array.get(2).getAsDouble(),
				array.get(3).getAsDouble()
		};
	}

	private static String resolveTextureRef(Map<String, String> textures, String ref) {
		if (ref == null) {
			return null;
		}
		String current = ref;
		for (int i = 0; i < 8 && current.startsWith("#"); i++) {
			current = textures.get(current.substring(1));
			if (current == null) {
				return null;
			}
		}
		if (current.indexOf(':') < 0) {
			return "minecraft:" + current;
		}
		return current;
	}

	public record BlockTraceResult(int argb, Vec3 worldHit, Direction face, boolean shade) {
		public int rgb() {
			return this.argb & 0xFFFFFF;
		}

		public int alpha() {
			return (this.argb >>> 24) & 0xFF;
		}
	}

	private record AxisAngle(Direction.Axis axis, float angle) {
	}

	private record ResolvedVariant(List<ModelPart> parts) {
	}

	private record ModelPart(ResolvedModel model, ModelTransform transform) {
	}

	private record ModelTransform(int x, int y, boolean uvlock) {
	}

	private record ResolvedModel(Map<String, String> textures, List<ModelElement> elements) {
	}

	private record ModelElement(Vec3 from, Vec3 to, Map<Direction, ModelFace> faces, ElementRotation rotation, boolean shade) {
	}

	private record ModelFace(String texture, double[] uv, int rotation, int tintIndex) {
	}

	private record FaceHit(double t, Direction direction, ModelFace face, double u, double v, Vec3 hitPoint, boolean shade, ElementRotation elementRotation, ModelTransform transform) {
	}

	private record ElementRotation(Vec3 origin, Direction.Axis axis, float angle, boolean rescale, Matrix4f transform, Matrix4f inverse) {
	}

	private record ModelBounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
	}

	private record UvPoint(double u, double v) {
	}

	private record Ray(Vec3 origin, Vec3 direction) {
	}

	private record DoubleRange(double min, double max) {
	}
}
