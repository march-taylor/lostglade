package com.lostglade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.RandomSource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Lg2Config {
	private static final int MAX_ORES_PER_CHUNK = 128;
	private static final int MAX_VEIN_SIZE = 64;
	private static final int MAX_DROP = 64;
	private static final int MAX_XP = 100;
	private static final int MIN_WORLD_Y = -64;
	private static final int MAX_WORLD_Y = 320;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Lg2.MOD_ID + ".json");

	private static ConfigData data = ConfigData.defaults();

	private Lg2Config() {
	}

	public static synchronized void load() {
		ConfigData loaded = readOrCreate();
		boolean changed = sanitize(loaded);
		data = loaded;

		if (changed) {
			write(data);
		}
	}

	public static ConfigData get() {
		return data;
	}

	private static ConfigData readOrCreate() {
		if (!Files.exists(PATH)) {
			ConfigData defaults = ConfigData.defaults();
			write(defaults);
			return defaults;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			ConfigData parsed = GSON.fromJson(reader, ConfigData.class);
			if (parsed == null) {
				Lg2.LOGGER.warn("Config {} is empty, resetting to defaults", PATH);
				return ConfigData.defaults();
			}
			return parsed;
		} catch (Exception e) {
			Lg2.LOGGER.warn("Failed to read config {}, using defaults", PATH, e);
			return ConfigData.defaults();
		}
	}

	private static void write(ConfigData configData) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(configData, writer);
			}
		} catch (IOException e) {
			Lg2.LOGGER.error("Failed to write config {}", PATH, e);
		}
	}

	private static boolean sanitize(ConfigData configData) {
		boolean changed = false;

		changed |= clampRange(configData, RangeType.ORES_PER_CHUNK, 0, MAX_ORES_PER_CHUNK);
		changed |= clampRange(configData, RangeType.VEIN_SIZE, 0, MAX_VEIN_SIZE);
		changed |= clampRange(configData, RangeType.DROP, 0, MAX_DROP);
		changed |= clampRange(configData, RangeType.XP, 0, MAX_XP);
		changed |= clampRange(configData, RangeType.SPAWN_Y, MIN_WORLD_Y, MAX_WORLD_Y);

		return changed;
	}

	private static boolean clampRange(ConfigData configData, RangeType type, int minLimit, int maxLimit) {
		int min;
		int max;

		switch (type) {
			case ORES_PER_CHUNK -> {
				min = configData.oresPerChunkMin;
				max = configData.oresPerChunkMax;
			}
			case VEIN_SIZE -> {
				min = configData.veinSizeMin;
				max = configData.veinSizeMax;
			}
			case DROP -> {
				min = configData.dropMin;
				max = configData.dropMax;
			}
			case XP -> {
				min = configData.xpMin;
				max = configData.xpMax;
			}
			case SPAWN_Y -> {
				min = configData.spawnMinY;
				max = configData.spawnMaxY;
			}
			default -> throw new IllegalStateException("Unexpected value: " + type);
		}

		int newMin = clamp(min, minLimit, maxLimit);
		int newMax = clamp(max, minLimit, maxLimit);
		if (newMax < newMin) {
			newMax = newMin;
		}

		boolean changed = newMin != min || newMax != max;
		if (!changed) {
			return false;
		}

		switch (type) {
			case ORES_PER_CHUNK -> {
				configData.oresPerChunkMin = newMin;
				configData.oresPerChunkMax = newMax;
			}
			case VEIN_SIZE -> {
				configData.veinSizeMin = newMin;
				configData.veinSizeMax = newMax;
			}
			case DROP -> {
				configData.dropMin = newMin;
				configData.dropMax = newMax;
			}
			case XP -> {
				configData.xpMin = newMin;
				configData.xpMax = newMax;
			}
			case SPAWN_Y -> {
				configData.spawnMinY = newMin;
				configData.spawnMaxY = newMax;
			}
			default -> throw new IllegalStateException("Unexpected value: " + type);
		}

		return true;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private enum RangeType {
		ORES_PER_CHUNK,
		VEIN_SIZE,
		DROP,
		XP,
		SPAWN_Y
	}

	public static final class ConfigData {
		public int oresPerChunkMin = 8;
		public int oresPerChunkMax = 14;
		public int veinSizeMin = 3;
		public int veinSizeMax = 8;
		public int dropMin = 1;
		public int dropMax = 1;
		public int xpMin = 1;
		public int xpMax = 3;
		public int spawnMinY = -64;
		public int spawnMaxY = 32;
		public boolean silkTouchEnabled = true;
		public boolean fortuneEnabled = true;

		private ConfigData() {
		}

		public static ConfigData defaults() {
			return new ConfigData();
		}

		public int sampleOresPerChunk(RandomSource random) {
			return sampleInRange(random, this.oresPerChunkMin, this.oresPerChunkMax);
		}

		public int sampleVeinSize(RandomSource random) {
			return sampleInRange(random, this.veinSizeMin, this.veinSizeMax);
		}

		public int sampleDrop(RandomSource random) {
			return sampleInRange(random, this.dropMin, this.dropMax);
		}

		public int sampleXp(RandomSource random) {
			return sampleInRange(random, this.xpMin, this.xpMax);
		}

		public int sampleSpawnY(RandomSource random) {
			return sampleInRange(random, this.spawnMinY, this.spawnMaxY);
		}

		private static int sampleInRange(RandomSource random, int min, int max) {
			if (max <= min) {
				return min;
			}
			return min + random.nextInt(max - min + 1);
		}
	}
}
