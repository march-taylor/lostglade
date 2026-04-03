package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lostglade.Lg2;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class CameraOrientationStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "lg2-camera-orientations.json";
	private static final Map<CameraKey, CameraPose> POSES = new HashMap<>();

	private static boolean loaded = false;
	private static boolean dirty = false;

	private CameraOrientationStore() {
	}

	public static void register() {
		loaded = false;
		dirty = false;
		POSES.clear();
		ServerLifecycleEvents.SERVER_STARTED.register(CameraOrientationStore::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(CameraOrientationStore::save);
	}

	public static CameraPose get(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return null;
		}
		ensureLoaded(level.getServer());
		return POSES.get(new CameraKey(level.dimension(), pos.immutable()));
	}

	public static void set(ServerLevel level, BlockPos pos, float yaw, float pitch) {
		if (level == null || pos == null) {
			return;
		}
		ensureLoaded(level.getServer());
		POSES.put(new CameraKey(level.dimension(), pos.immutable()), new CameraPose(yaw, pitch));
		dirty = true;
	}

	public static void remove(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		ensureLoaded(level.getServer());
		if (POSES.remove(new CameraKey(level.dimension(), pos.immutable())) != null) {
			dirty = true;
		}
	}

	private static void ensureLoaded(MinecraftServer server) {
		if (!loaded) {
			load(server);
		}
	}

	private static void load(MinecraftServer server) {
		POSES.clear();
		loaded = true;
		dirty = false;
		if (server == null) {
			return;
		}
		Path path = statePath(server);
		if (!Files.exists(path)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(path)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!(parsed instanceof JsonArray entries)) {
				return;
			}
			for (JsonElement element : entries) {
				if (!(element instanceof JsonObject object)) {
					continue;
				}
				String dimensionId = object.has("dimension") ? object.get("dimension").getAsString() : "";
				ResourceKey<Level> dimension = resolveDimension(dimensionId);
				if (dimension == null) {
					continue;
				}
				BlockPos pos = new BlockPos(
						object.get("x").getAsInt(),
						object.get("y").getAsInt(),
						object.get("z").getAsInt()
				);
				float yaw = object.get("yaw").getAsFloat();
				float pitch = object.get("pitch").getAsFloat();
				POSES.put(new CameraKey(dimension, pos), new CameraPose(yaw, pitch));
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to load camera orientation state", exception);
		}
	}

	private static void save(MinecraftServer server) {
		if (!loaded || !dirty || server == null) {
			return;
		}
		Path path = statePath(server);
		try {
			Files.createDirectories(path.getParent());
			JsonArray entries = new JsonArray();
			for (Map.Entry<CameraKey, CameraPose> entry : POSES.entrySet()) {
				JsonObject object = new JsonObject();
				object.addProperty("dimension", entry.getKey().dimension().identifier().toString());
				object.addProperty("x", entry.getKey().pos().getX());
				object.addProperty("y", entry.getKey().pos().getY());
				object.addProperty("z", entry.getKey().pos().getZ());
				object.addProperty("yaw", entry.getValue().yaw());
				object.addProperty("pitch", entry.getValue().pitch());
				entries.add(object);
			}
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(entries, writer);
			}
			dirty = false;
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to save camera orientation state", exception);
		}
	}

	private static Path statePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
	}

	private static ResourceKey<Level> resolveDimension(String rawId) {
		if (rawId == null || rawId.isBlank()) {
			return null;
		}
		var identifier = net.minecraft.resources.Identifier.tryParse(rawId);
		if (identifier == null) {
			return null;
		}
		return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, identifier);
	}

	public record CameraPose(float yaw, float pitch) {
	}

	private record CameraKey(ResourceKey<Level> dimension, BlockPos pos) {
	}
}
