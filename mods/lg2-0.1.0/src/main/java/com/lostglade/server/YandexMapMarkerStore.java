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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class YandexMapMarkerStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "lg2-yandex-map-markers.json";
	private static final Map<UUID, YandexMapMarker> MARKERS_BY_ID = new LinkedHashMap<>();

	private static volatile Map<ResourceKey<Level>, List<YandexMapMarker>> MARKERS_BY_DIMENSION = Map.of();
	private static boolean loaded = false;
	private static boolean dirty = false;

	private YandexMapMarkerStore() {
	}

	public static void register() {
		loaded = false;
		dirty = false;
		MARKERS_BY_ID.clear();
		MARKERS_BY_DIMENSION = Map.of();
		ServerLifecycleEvents.SERVER_STARTED.register(YandexMapMarkerStore::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(YandexMapMarkerStore::save);
	}

	public static List<YandexMapMarker> markers(ResourceKey<Level> dimension) {
		if (dimension == null) {
			return List.of();
		}
		return MARKERS_BY_DIMENSION.getOrDefault(dimension, List.of());
	}

	public static YandexMapMarker marker(UUID markerId) {
		if (markerId == null) {
			return null;
		}
		synchronized (MARKERS_BY_ID) {
			return MARKERS_BY_ID.get(markerId);
		}
	}

	public static YandexMapMarker create(ServerLevel level, BlockPos pos, ServerPlayer creator, String title, String iconItemId) {
		if (level == null || pos == null || creator == null) {
			return null;
		}
		ensureLoaded(level.getServer());
		YandexMapMarker marker = new YandexMapMarker(
				UUID.randomUUID(),
				level.dimension(),
				pos.getX(),
				pos.getY(),
				pos.getZ(),
				normalizeTitle(title),
				normalizeIconItemId(iconItemId),
				creator.getUUID(),
				safeCreatorName(creator)
		);
		synchronized (MARKERS_BY_ID) {
			MARKERS_BY_ID.put(marker.markerId(), marker);
			rebuildDimensionSnapshotsLocked();
			dirty = true;
		}
		return marker;
	}

	public static YandexMapMarker updateTitle(MinecraftServer server, UUID markerId, String title) {
		if (server == null || markerId == null) {
			return null;
		}
		ensureLoaded(server);
		synchronized (MARKERS_BY_ID) {
			YandexMapMarker current = MARKERS_BY_ID.get(markerId);
			if (current == null) {
				return null;
			}
			YandexMapMarker updated = current.withTitle(normalizeTitle(title));
			if (Objects.equals(current, updated)) {
				return current;
			}
			MARKERS_BY_ID.put(markerId, updated);
			rebuildDimensionSnapshotsLocked();
			dirty = true;
			return updated;
		}
	}

	public static YandexMapMarker updateIcon(MinecraftServer server, UUID markerId, String iconItemId) {
		if (server == null || markerId == null) {
			return null;
		}
		ensureLoaded(server);
		synchronized (MARKERS_BY_ID) {
			YandexMapMarker current = MARKERS_BY_ID.get(markerId);
			if (current == null) {
				return null;
			}
			YandexMapMarker updated = current.withIconItemId(normalizeIconItemId(iconItemId));
			if (Objects.equals(current, updated)) {
				return current;
			}
			MARKERS_BY_ID.put(markerId, updated);
			rebuildDimensionSnapshotsLocked();
			dirty = true;
			return updated;
		}
	}

	public static boolean remove(MinecraftServer server, UUID markerId) {
		if (server == null || markerId == null) {
			return false;
		}
		ensureLoaded(server);
		synchronized (MARKERS_BY_ID) {
			if (MARKERS_BY_ID.remove(markerId) == null) {
				return false;
			}
			rebuildDimensionSnapshotsLocked();
			dirty = true;
			return true;
		}
	}

	private static void ensureLoaded(MinecraftServer server) {
		if (!loaded) {
			load(server);
		}
	}

	private static void load(MinecraftServer server) {
		synchronized (MARKERS_BY_ID) {
			MARKERS_BY_ID.clear();
			MARKERS_BY_DIMENSION = Map.of();
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
					UUID markerId = parseUuid(readString(object, "id"));
					ResourceKey<Level> dimension = resolveDimension(readString(object, "dimension"));
					UUID creatorId = parseUuid(readString(object, "creator_uuid"));
					String creatorName = readString(object, "creator_name");
					String title = normalizeTitle(readString(object, "title"));
					if (markerId == null || dimension == null || creatorId == null || creatorName.isBlank()) {
						continue;
					}
					YandexMapMarker marker = new YandexMapMarker(
							markerId,
							dimension,
							readInt(object, "x"),
							readInt(object, "y"),
							readInt(object, "z"),
							title,
							normalizeIconItemId(readString(object, "icon_item_id")),
							creatorId,
							creatorName
					);
					MARKERS_BY_ID.put(marker.markerId(), marker);
				}
				rebuildDimensionSnapshotsLocked();
			} catch (IOException exception) {
				Lg2.LOGGER.warn("Failed to load Yandex map markers", exception);
			}
		}
	}

	private static void save(MinecraftServer server) {
		synchronized (MARKERS_BY_ID) {
			if (!loaded || !dirty || server == null) {
				return;
			}
			Path path = statePath(server);
			try {
				Files.createDirectories(path.getParent());
				JsonArray entries = new JsonArray();
				for (YandexMapMarker marker : MARKERS_BY_ID.values()) {
					if (marker == null || marker.dimension() == null || marker.creatorUuid() == null) {
						continue;
					}
					JsonObject object = new JsonObject();
					object.addProperty("id", marker.markerId().toString());
					object.addProperty("dimension", marker.dimension().identifier().toString());
					object.addProperty("x", marker.blockX());
					object.addProperty("y", marker.blockY());
					object.addProperty("z", marker.blockZ());
					object.addProperty("title", marker.title());
					object.addProperty("icon_item_id", marker.iconItemId());
					object.addProperty("creator_uuid", marker.creatorUuid().toString());
					object.addProperty("creator_name", marker.creatorName());
					entries.add(object);
				}
				try (Writer writer = Files.newBufferedWriter(path)) {
					GSON.toJson(entries, writer);
				}
				dirty = false;
			} catch (IOException exception) {
				Lg2.LOGGER.warn("Failed to save Yandex map markers", exception);
			}
		}
	}

	private static void rebuildDimensionSnapshotsLocked() {
		Map<ResourceKey<Level>, List<YandexMapMarker>> grouped = new HashMap<>();
		for (YandexMapMarker marker : MARKERS_BY_ID.values()) {
			if (marker == null || marker.dimension() == null) {
				continue;
			}
			grouped.computeIfAbsent(marker.dimension(), ignored -> new ArrayList<>()).add(marker);
		}
		Map<ResourceKey<Level>, List<YandexMapMarker>> immutable = new HashMap<>();
		for (Map.Entry<ResourceKey<Level>, List<YandexMapMarker>> entry : grouped.entrySet()) {
			immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		MARKERS_BY_DIMENSION = Map.copyOf(immutable);
	}

	private static String safeCreatorName(ServerPlayer player) {
		if (player == null || player.getGameProfile() == null || player.getGameProfile().name() == null) {
			return "player";
		}
		String name = player.getGameProfile().name().trim();
		return name.isEmpty() ? "player" : name;
	}

	private static String normalizeTitle(String title) {
		if (title == null) {
			return "Marker";
		}
		String normalized = title.trim().replaceAll("\\s+", " ");
		if (normalized.isEmpty()) {
			return "Marker";
		}
		if (normalized.length() <= 64) {
			return normalized;
		}
		return normalized.substring(0, 64).trim();
	}

	private static String normalizeIconItemId(String iconItemId) {
		if (iconItemId == null) {
			return "";
		}
		String normalized = iconItemId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty()) {
			return "";
		}
		Identifier identifier = Identifier.tryParse(normalized);
		return identifier == null ? "" : identifier.toString();
	}

	private static String readString(JsonObject object, String key) {
		return object != null && object.has(key) ? object.get(key).getAsString() : "";
	}

	private static int readInt(JsonObject object, String key) {
		return object != null && object.has(key) ? object.get(key).getAsInt() : 0;
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ignored) {
			return null;
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
		return ResourceKey.create(Registries.DIMENSION, identifier);
	}

	public record YandexMapMarker(
			UUID markerId,
			ResourceKey<Level> dimension,
			int blockX,
			int blockY,
			int blockZ,
			String title,
			String iconItemId,
			UUID creatorUuid,
			String creatorName
	) {
		public YandexMapMarker withTitle(String nextTitle) {
			return new YandexMapMarker(
					this.markerId,
					this.dimension,
					this.blockX,
					this.blockY,
					this.blockZ,
					nextTitle,
					this.iconItemId,
					this.creatorUuid,
					this.creatorName
			);
		}

		public YandexMapMarker withIconItemId(String nextIconItemId) {
			return new YandexMapMarker(
					this.markerId,
					this.dimension,
					this.blockX,
					this.blockY,
					this.blockZ,
					this.title,
					nextIconItemId,
					this.creatorUuid,
					this.creatorName
			);
		}

		public double centerX() {
			return this.blockX + 0.5D;
		}

		public double centerZ() {
			return this.blockZ + 0.5D;
		}
	}
}
