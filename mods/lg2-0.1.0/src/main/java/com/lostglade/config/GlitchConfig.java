package com.lostglade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GlitchConfig {
	private static final int DEFAULT_COOLDOWN_TICKS = 100;
	private static final int MIN_CHECK_INTERVAL_TICKS = 1;
	private static final int MAX_CHECK_INTERVAL_TICKS = 72_000;
	private static final int MIN_MAX_ACTIVATIONS_PER_CHECK = 1;
	private static final int MAX_MAX_ACTIVATIONS_PER_CHECK = 64;
	private static final int MIN_COOLDOWN_TICKS = 0;
	private static final int MAX_COOLDOWN_TICKS = 72_000;
	private static final double MIN_STABILITY_PERCENT = 0.0D;
	private static final double MAX_STABILITY_PERCENT = 100.0D;
	private static final double MIN_CHANCE = 0.0D;
	private static final double MAX_CHANCE = 1.0D;
	private static final double MIN_STABILITY_INFLUENCE = 0.0D;
	private static final double MAX_STABILITY_INFLUENCE = 1.0D;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Lg2.MOD_ID + "-glitches.json");

	private static ConfigData data = ConfigData.defaults();

	private GlitchConfig() {
	}

	public static synchronized void load(Map<String, GlitchEntry> defaultsById) {
		ConfigData loaded = readOrCreate(defaultsById);
		boolean changed = sanitize(loaded, defaultsById);
		data = loaded;

		if (changed) {
			write(data);
		}
	}

	public static synchronized void save() {
		write(data);
	}

	public static ConfigData get() {
		return data;
	}

	private static ConfigData readOrCreate(Map<String, GlitchEntry> defaultsById) {
		if (!Files.exists(PATH)) {
			ConfigData defaults = ConfigData.defaults(defaultsById);
			write(defaults);
			return defaults;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			ConfigData parsed = GSON.fromJson(reader, ConfigData.class);
			if (parsed == null) {
				Lg2.LOGGER.warn("Glitch config {} is empty, resetting to defaults", PATH);
				return ConfigData.defaults(defaultsById);
			}
			return parsed;
		} catch (Exception e) {
			Lg2.LOGGER.warn("Failed to read glitch config {}, using defaults", PATH, e);
			return ConfigData.defaults(defaultsById);
		}
	}

	private static void write(ConfigData configData) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(configData, writer);
			}
		} catch (IOException e) {
			Lg2.LOGGER.error("Failed to write glitch config {}", PATH, e);
		}
	}

	private static boolean sanitize(ConfigData configData, Map<String, GlitchEntry> defaultsById) {
		boolean changed = false;

		if (configData.glitches == null) {
			configData.glitches = new LinkedHashMap<>();
			changed = true;
		}

		int sanitizedInterval = clampInt(configData.checkIntervalTicks, MIN_CHECK_INTERVAL_TICKS, MAX_CHECK_INTERVAL_TICKS);
		if (sanitizedInterval != configData.checkIntervalTicks) {
			configData.checkIntervalTicks = sanitizedInterval;
			changed = true;
		}

		int sanitizedMaxActivations = clampInt(
				configData.maxActivationsPerCheck,
				MIN_MAX_ACTIVATIONS_PER_CHECK,
				MAX_MAX_ACTIVATIONS_PER_CHECK
		);
		if (sanitizedMaxActivations != configData.maxActivationsPerCheck) {
			configData.maxActivationsPerCheck = sanitizedMaxActivations;
			changed = true;
		}

		Map<String, GlitchEntry> sanitizedEntries = new LinkedHashMap<>();
		for (Map.Entry<String, GlitchEntry> mapEntry : configData.glitches.entrySet()) {
			String id = mapEntry.getKey();
			if (id == null || id.isBlank()) {
				changed = true;
				continue;
			}

			GlitchEntry entry = mapEntry.getValue();
			if (entry == null) {
				GlitchEntry fallback = defaultsById.get(id);
				sanitizedEntries.put(id, fallback == null ? new GlitchEntry() : copyEntry(fallback));
				changed = true;
				continue;
			}

			changed |= sanitizeEntry(entry);
			sanitizedEntries.put(id, entry);
		}
		configData.glitches = sanitizedEntries;

		for (Map.Entry<String, GlitchEntry> defaultEntry : defaultsById.entrySet()) {
			if (configData.glitches.containsKey(defaultEntry.getKey())) {
				continue;
			}

			configData.glitches.put(defaultEntry.getKey(), copyEntry(defaultEntry.getValue()));
			changed = true;
		}

		return changed;
	}

	private static boolean sanitizeEntry(GlitchEntry entry) {
		boolean changed = false;

		if (entry.settings == null) {
			entry.settings = new JsonObject();
			changed = true;
		}

		double sanitizedMinPercent = sanitizeFiniteDouble(entry.minStabilityPercent, MIN_STABILITY_PERCENT);
		sanitizedMinPercent = clampDouble(sanitizedMinPercent, MIN_STABILITY_PERCENT, MAX_STABILITY_PERCENT);
		if (Double.compare(sanitizedMinPercent, entry.minStabilityPercent) != 0) {
			entry.minStabilityPercent = sanitizedMinPercent;
			changed = true;
		}

		double sanitizedMaxPercent = sanitizeFiniteDouble(entry.maxStabilityPercent, MAX_STABILITY_PERCENT);
		sanitizedMaxPercent = clampDouble(sanitizedMaxPercent, MIN_STABILITY_PERCENT, MAX_STABILITY_PERCENT);
		if (Double.compare(sanitizedMaxPercent, entry.maxStabilityPercent) != 0) {
			entry.maxStabilityPercent = sanitizedMaxPercent;
			changed = true;
		}

		if (entry.maxStabilityPercent < entry.minStabilityPercent) {
			entry.maxStabilityPercent = entry.minStabilityPercent;
			changed = true;
		}

		double sanitizedChance = sanitizeFiniteDouble(entry.chancePerCheck, MIN_CHANCE);
		sanitizedChance = clampDouble(sanitizedChance, MIN_CHANCE, MAX_CHANCE);
		if (Double.compare(sanitizedChance, entry.chancePerCheck) != 0) {
			entry.chancePerCheck = sanitizedChance;
			changed = true;
		}

		double sanitizedInfluence = sanitizeFiniteDouble(entry.stabilityInfluence, MAX_STABILITY_INFLUENCE);
		sanitizedInfluence = clampDouble(sanitizedInfluence, MIN_STABILITY_INFLUENCE, MAX_STABILITY_INFLUENCE);
		if (Double.compare(sanitizedInfluence, entry.stabilityInfluence) != 0) {
			entry.stabilityInfluence = sanitizedInfluence;
			changed = true;
		}

		int legacyCooldown = entry.cooldownTicks == null
				? DEFAULT_COOLDOWN_TICKS
				: entry.cooldownTicks;

		if (entry.minCooldownTicks == null) {
			entry.minCooldownTicks = legacyCooldown;
			changed = true;
		}
		if (entry.maxCooldownTicks == null) {
			entry.maxCooldownTicks = legacyCooldown;
			changed = true;
		}

		int sanitizedMinCooldown = clampInt(entry.minCooldownTicks, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
		if (sanitizedMinCooldown != entry.minCooldownTicks) {
			entry.minCooldownTicks = sanitizedMinCooldown;
			changed = true;
		}

		int sanitizedMaxCooldown = clampInt(entry.maxCooldownTicks, MIN_COOLDOWN_TICKS, MAX_COOLDOWN_TICKS);
		if (sanitizedMaxCooldown != entry.maxCooldownTicks) {
			entry.maxCooldownTicks = sanitizedMaxCooldown;
			changed = true;
		}

		if (entry.maxCooldownTicks < entry.minCooldownTicks) {
			entry.maxCooldownTicks = entry.minCooldownTicks;
			changed = true;
		}

		return changed;
	}

	private static GlitchEntry copyEntry(GlitchEntry source) {
		GlitchEntry copy = new GlitchEntry();
		copy.enabled = source.enabled;
		copy.minStabilityPercent = source.minStabilityPercent;
		copy.maxStabilityPercent = source.maxStabilityPercent;
		copy.chancePerCheck = source.chancePerCheck;
		copy.stabilityInfluence = source.stabilityInfluence;
		copy.minCooldownTicks = source.minCooldownTicks;
		copy.maxCooldownTicks = source.maxCooldownTicks;
		copy.cooldownTicks = source.cooldownTicks;
		copy.settings = source.settings == null ? new JsonObject() : source.settings.deepCopy();
		return copy;
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double sanitizeFiniteDouble(double value, double fallback) {
		return Double.isFinite(value) ? value : fallback;
	}

	public static final class ConfigData {
		public boolean enabled = true;
		public int checkIntervalTicks = 20;
		public int maxActivationsPerCheck = 1;
		public Map<String, GlitchEntry> glitches = new LinkedHashMap<>();

		private ConfigData() {
		}

		public static ConfigData defaults() {
			return new ConfigData();
		}

		public static ConfigData defaults(Map<String, GlitchEntry> defaultsById) {
			ConfigData defaults = defaults();
			for (Map.Entry<String, GlitchEntry> mapEntry : defaultsById.entrySet()) {
				defaults.glitches.put(mapEntry.getKey(), copyEntry(mapEntry.getValue()));
			}
			return defaults;
		}
	}

	public static final class GlitchEntry {
		public boolean enabled = true;
		public double minStabilityPercent = 0.0D;
		public double maxStabilityPercent = 100.0D;
		public double chancePerCheck = 0.2D;
		public double stabilityInfluence = 1.0D;
		public Integer minCooldownTicks;
		public Integer maxCooldownTicks;
		// Legacy field for migration from old configs.
		public Integer cooldownTicks;
		public JsonObject settings = new JsonObject();
	}
}
