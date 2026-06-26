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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class PlacedDeviceNameStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "lg2-device-names.json";
	private static final Map<DeviceKey, String> NAMES = new HashMap<>();

	private static boolean loaded = false;
	private static boolean dirty = false;

	private PlacedDeviceNameStore() {
	}

	public static void register() {
		loaded = false;
		dirty = false;
		NAMES.clear();
		ServerLifecycleEvents.SERVER_STARTED.register(PlacedDeviceNameStore::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(PlacedDeviceNameStore::save);
	}

	public static void rememberPlacedCameraName(ServerLevel level, BlockPos pos, ItemStack stack) {
		rememberPlacedName(level, DeviceType.CAMERA, pos, stack);
	}

	public static void rememberPlacedMicrophoneName(ServerLevel level, BlockPos pos, ItemStack stack) {
		rememberPlacedName(level, DeviceType.MICROPHONE, pos, stack);
	}

	public static void removeCameraName(ServerLevel level, BlockPos pos) {
		remove(level, DeviceType.CAMERA, pos);
	}

	public static void removeMicrophoneName(ServerLevel level, BlockPos pos) {
		remove(level, DeviceType.MICROPHONE, pos);
	}

	public static String cameraName(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos, String fallback) {
		return name(server, DeviceType.CAMERA, dimension, pos, fallback);
	}

	public static String microphoneName(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos, String fallback) {
		return name(server, DeviceType.MICROPHONE, dimension, pos, fallback);
	}

	private static void rememberPlacedName(ServerLevel level, DeviceType type, BlockPos pos, ItemStack stack) {
		if (level == null || type == null || pos == null) {
			return;
		}
		ensureLoaded(level.getServer());
		Component customName = stack != null ? stack.get(DataComponents.CUSTOM_NAME) : null;
		String normalized = normalizeName(customName);
		DeviceKey key = new DeviceKey(type, level.dimension(), pos.immutable());
		if (normalized == null) {
			if (NAMES.remove(key) != null) {
				dirty = true;
			}
			return;
		}
		String previous = NAMES.put(key, normalized);
		if (!Objects.equals(previous, normalized)) {
			dirty = true;
		}
	}

	private static void remove(ServerLevel level, DeviceType type, BlockPos pos) {
		if (level == null || type == null || pos == null) {
			return;
		}
		ensureLoaded(level.getServer());
		if (NAMES.remove(new DeviceKey(type, level.dimension(), pos.immutable())) != null) {
			dirty = true;
		}
	}

	private static String name(MinecraftServer server, DeviceType type, ResourceKey<Level> dimension, BlockPos pos, String fallback) {
		if (server == null || type == null || dimension == null || pos == null) {
			return fallback;
		}
		ensureLoaded(server);
		return NAMES.getOrDefault(new DeviceKey(type, dimension, pos.immutable()), fallback);
	}

	private static String normalizeName(Component name) {
		if (name == null) {
			return null;
		}
		String text = name.getString();
		if (text == null) {
			return null;
		}
		text = text.trim();
		return text.isEmpty() ? null : text;
	}

	private static void ensureLoaded(MinecraftServer server) {
		if (!loaded) {
			load(server);
		}
	}

	private static void load(MinecraftServer server) {
		NAMES.clear();
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
				DeviceType type = DeviceType.fromSerializedName(object.has("type") ? object.get("type").getAsString() : "");
				ResourceKey<Level> dimension = resolveDimension(object.has("dimension") ? object.get("dimension").getAsString() : "");
				String name = object.has("name") ? object.get("name").getAsString() : "";
				if (type == null || dimension == null || name.isBlank()) {
					continue;
				}
				BlockPos pos = new BlockPos(
						object.has("x") ? object.get("x").getAsInt() : 0,
						object.has("y") ? object.get("y").getAsInt() : 0,
						object.has("z") ? object.get("z").getAsInt() : 0
				);
				NAMES.put(new DeviceKey(type, dimension, pos), name);
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to load placed device names", exception);
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
			for (Map.Entry<DeviceKey, String> entry : NAMES.entrySet()) {
				if (entry.getValue() == null || entry.getValue().isBlank()) {
					continue;
				}
				JsonObject object = new JsonObject();
				object.addProperty("type", entry.getKey().type().serializedName());
				object.addProperty("dimension", entry.getKey().dimension().identifier().toString());
				object.addProperty("x", entry.getKey().pos().getX());
				object.addProperty("y", entry.getKey().pos().getY());
				object.addProperty("z", entry.getKey().pos().getZ());
				object.addProperty("name", entry.getValue());
				entries.add(object);
			}
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(entries, writer);
			}
			dirty = false;
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to save placed device names", exception);
		}
	}

	private static Path statePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
	}

	private static ResourceKey<Level> resolveDimension(String rawId) {
		if (rawId == null || rawId.isBlank()) {
			return null;
		}
		Identifier identifier = Identifier.tryParse(rawId);
		if (identifier == null) {
			return null;
		}
		return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, identifier);
	}

	private enum DeviceType {
		CAMERA("camera"),
		MICROPHONE("microphone");

		private final String serializedName;

		DeviceType(String serializedName) {
			this.serializedName = serializedName;
		}

		private String serializedName() {
			return this.serializedName;
		}

		private static DeviceType fromSerializedName(String name) {
			for (DeviceType type : values()) {
				if (type.serializedName.equals(name)) {
					return type;
				}
			}
			return null;
		}
	}

	private record DeviceKey(DeviceType type, ResourceKey<Level> dimension, BlockPos pos) {
	}
}
