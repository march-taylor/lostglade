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
		if (variant == null || variant.model() == null || variant.model().elements().isEmpty()) {
			return null;
		}

		Vec3 localOrigin = worldOrigin.subtract(pos.getX(), pos.getY(), pos.getZ()).scale(16.0D);
		Vec3 localDirection = worldDirection.scale(16.0D);
		Ray localRay = inverseRotateVariant(localOrigin, localDirection, variant);
		DoubleRange blockRange = intersectAabb(localRay.origin(), localRay.direction(), 0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
		if (blockRange == null) {
			return null;
		}

		double minT = Math.max(0.0D, blockRange.min());
		double maxT = blockRange.max();
		for (int passes = 0; passes < 8 && minT <= maxT; passes++) {
			FaceHit nearest = null;
			for (ModelElement element : variant.model().elements()) {
				FaceHit candidate = firstFaceHit(element, localRay, minT, maxT, variant.model());
				if (candidate != null && (nearest == null || candidate.t() < nearest.t())) {
					nearest = candidate;
				}
			}
			if (nearest == null) {
				break;
			}

			int argb = sampleTexture(nearest, tintColors);
			double worldT = nearest.t() / 16.0D;
			Vec3 worldHit = worldOrigin.add(worldDirection.scale(worldT));
			if (((argb >>> 24) & 0xFF) > 8) {
				return new BlockTraceResult(argb & 0xFFFFFF, worldHit, nearest.direction());
			}
			minT = nearest.t() + EPSILON;
		}
		return null;
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

	private static FaceHit firstFaceHit(ModelElement element, Ray ray, double minT, double maxT, ResolvedModel model) {
		FaceHit nearest = null;
		for (Map.Entry<Direction, ModelFace> entry : element.faces().entrySet()) {
			FaceHit candidate = intersectFace(element, entry.getKey(), entry.getValue(), ray, minT, maxT, model);
			if (candidate != null && (nearest == null || candidate.t() < nearest.t())) {
				nearest = candidate;
			}
		}
		return nearest;
	}

	private static FaceHit intersectFace(
			ModelElement element,
			Direction direction,
			ModelFace face,
			Ray ray,
			double minT,
			double maxT,
			ResolvedModel model
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
				if (Math.abs(ray.direction().z) < EPSILON) {
					return null;
				}
				plane = from.z;
				t = (plane - ray.origin().z) / ray.direction().z;
				if (t < minT || t > maxT) {
					return null;
				}
				x = ray.origin().x + ray.direction().x * t;
				y = ray.origin().y + ray.direction().y * t;
				z = plane;
				if (!between(x, from.x, to.x) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case SOUTH -> {
				if (Math.abs(ray.direction().z) < EPSILON) {
					return null;
				}
				plane = to.z;
				t = (plane - ray.origin().z) / ray.direction().z;
				if (t < minT || t > maxT) {
					return null;
				}
				x = ray.origin().x + ray.direction().x * t;
				y = ray.origin().y + ray.direction().y * t;
				z = plane;
				if (!between(x, from.x, to.x) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case WEST -> {
				if (Math.abs(ray.direction().x) < EPSILON) {
					return null;
				}
				plane = from.x;
				t = (plane - ray.origin().x) / ray.direction().x;
				if (t < minT || t > maxT) {
					return null;
				}
				x = plane;
				y = ray.origin().y + ray.direction().y * t;
				z = ray.origin().z + ray.direction().z * t;
				if (!between(z, from.z, to.z) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case EAST -> {
				if (Math.abs(ray.direction().x) < EPSILON) {
					return null;
				}
				plane = to.x;
				t = (plane - ray.origin().x) / ray.direction().x;
				if (t < minT || t > maxT) {
					return null;
				}
				x = plane;
				y = ray.origin().y + ray.direction().y * t;
				z = ray.origin().z + ray.direction().z * t;
				if (!between(z, from.z, to.z) || !between(y, from.y, to.y)) {
					return null;
				}
			}
			case UP -> {
				if (Math.abs(ray.direction().y) < EPSILON) {
					return null;
				}
				plane = to.y;
				t = (plane - ray.origin().y) / ray.direction().y;
				if (t < minT || t > maxT) {
					return null;
				}
				x = ray.origin().x + ray.direction().x * t;
				y = plane;
				z = ray.origin().z + ray.direction().z * t;
				if (!between(x, from.x, to.x) || !between(z, from.z, to.z)) {
					return null;
				}
			}
			case DOWN -> {
				if (Math.abs(ray.direction().y) < EPSILON) {
					return null;
				}
				plane = from.y;
				t = (plane - ray.origin().y) / ray.direction().y;
				if (t < minT || t > maxT) {
					return null;
				}
				x = ray.origin().x + ray.direction().x * t;
				y = plane;
				z = ray.origin().z + ray.direction().z * t;
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
		return new FaceHit(t, direction, new ModelFace(resolvedTexture, uv, face.rotation(), face.tintIndex()), point.u(), point.v());
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

	private static Ray inverseRotateVariant(Vec3 origin, Vec3 direction, ResolvedVariant variant) {
		Vec3 center = new Vec3(8.0D, 8.0D, 8.0D);
		Vec3 rotatedOrigin = rotateAroundY(rotateAroundX(origin.subtract(center), -variant.x()), -variant.y()).add(center);
		Vec3 rotatedDirection = rotateAroundY(rotateAroundX(direction, -variant.x()), -variant.y());
		return new Ray(rotatedOrigin, rotatedDirection);
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
			return directModel == null ? null : new ResolvedVariant(directModel, 0, 0);
		}
		if (blockStateJson.has("multipart")) {
			return resolveMultipart(blockStateJson.getAsJsonArray("multipart"), state);
		}
		if (!blockStateJson.has("variants")) {
			ResolvedModel directModel = resolveModel(Identifier.fromNamespaceAndPath(blockId.getNamespace(), "block/" + blockId.getPath()));
			return directModel == null ? null : new ResolvedVariant(directModel, 0, 0);
		}
		JsonObject variants = blockStateJson.getAsJsonObject("variants");
		for (Map.Entry<String, JsonElement> entry : variants.entrySet()) {
			if (!matchesVariantKey(state, entry.getKey())) {
				continue;
			}
			JsonObject variantObject = entry.getValue().isJsonArray()
					? entry.getValue().getAsJsonArray().get(0).getAsJsonObject()
					: entry.getValue().getAsJsonObject();
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
			return new ResolvedVariant(model, x, y);
		}
		return null;
	}

	private static ResolvedVariant resolveMultipart(JsonArray multipart, BlockState state) {
		Map<String, String> textures = new HashMap<>();
		List<ModelElement> elements = new ArrayList<>();
		int modelX = 0;
		int modelY = 0;
		for (JsonElement element : multipart) {
			JsonObject part = element.getAsJsonObject();
			if (part.has("when") && !matchesMultipartCondition(state, part.get("when"))) {
				continue;
			}
			JsonElement apply = part.get("apply");
			if (apply == null) {
				continue;
			}
			JsonObject applyObject = apply.isJsonArray() ? apply.getAsJsonArray().get(0).getAsJsonObject() : apply.getAsJsonObject();
			Identifier modelId = Identifier.tryParse(applyObject.get("model").getAsString());
			if (modelId == null) {
				continue;
			}
			ResolvedModel model = resolveModel(modelId);
			if (model == null) {
				continue;
			}
			textures.putAll(model.textures());
			elements.addAll(model.elements());
			modelX = applyObject.has("x") ? applyObject.get("x").getAsInt() : modelX;
			modelY = applyObject.has("y") ? applyObject.get("y").getAsInt() : modelY;
		}
		return elements.isEmpty() ? null : new ResolvedVariant(new ResolvedModel(textures, elements), modelX, modelY);
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
				Map<Direction, ModelFace> faces = new HashMap<>();
				JsonObject facesJson = object.getAsJsonObject("faces");
				for (Map.Entry<String, JsonElement> faceEntry : facesJson.entrySet()) {
					Direction direction = Direction.byName(faceEntry.getKey());
					if (direction == null) {
						continue;
					}
					JsonObject faceJson = faceEntry.getValue().getAsJsonObject();
					double[] uv = faceJson.has("uv") ? readUv(faceJson.getAsJsonArray("uv")) : null;
					int rotation = faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0;
					int tintIndex = faceJson.has("tintindex") ? faceJson.get("tintindex").getAsInt() : -1;
					faces.put(direction, new ModelFace(faceJson.get("texture").getAsString(), uv, rotation, tintIndex));
				}
				elements.add(new ModelElement(from, to, faces));
			}
		}
		return new ResolvedModel(textures, elements);
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

	public record BlockTraceResult(int rgb, Vec3 worldHit, Direction face) {
	}

	private record ResolvedVariant(ResolvedModel model, int x, int y) {
	}

	private record ResolvedModel(Map<String, String> textures, List<ModelElement> elements) {
	}

	private record ModelElement(Vec3 from, Vec3 to, Map<Direction, ModelFace> faces) {
	}

	private record ModelFace(String texture, double[] uv, int rotation, int tintIndex) {
	}

	private record FaceHit(double t, Direction direction, ModelFace face, double u, double v) {
	}

	private record UvPoint(double u, double v) {
	}

	private record Ray(Vec3 origin, Vec3 direction) {
	}

	private record DoubleRange(double min, double max) {
	}
}
