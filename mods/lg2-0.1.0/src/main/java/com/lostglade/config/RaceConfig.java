package com.lostglade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RaceConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Lg2.MOD_ID + "-races.json");

	private static ConfigData data = ConfigData.defaults();

	private RaceConfig() {
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
			ConfigData parsed = ConfigVariableResolver.fromJsonWithVariables(GSON, reader, ConfigData.class);
			if (parsed == null) {
				Lg2.LOGGER.warn("Race config {} is empty, resetting to defaults", PATH);
				return ConfigData.defaults();
			}
			return parsed;
		} catch (Exception exception) {
			Lg2.LOGGER.warn("Failed to read race config {}, using defaults", PATH, exception);
			return ConfigData.defaults();
		}
	}

	private static void write(ConfigData configData) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(configData, writer);
			}
		} catch (IOException exception) {
			Lg2.LOGGER.error("Failed to write race config {}", PATH, exception);
		}
	}

	private static boolean sanitize(ConfigData configData) {
		boolean changed = false;
		if (configData.races == null) {
			configData.races = new ArrayList<>();
			changed = true;
		}

		for (int i = 0; i < configData.races.size(); i++) {
			PlayerRaceConfig race = configData.races.get(i);
			if (race == null) {
				configData.races.set(i, PlayerRaceConfig.template());
				changed = true;
				continue;
			}
			changed |= sanitizeRace(race);
		}
		return changed;
	}

	private static boolean sanitizeRace(PlayerRaceConfig race) {
		boolean changed = false;
		changed |= normalizeString(race.id, "example_race", value -> race.id = value);
		changed |= normalizeString(race.displayName, "Пример Расы", value -> race.displayName = value);
		changed |= normalizeString(race.ownerNickname, "", value -> race.ownerNickname = value);
		changed |= normalizeString(race.description, "", value -> race.description = value);
		changed |= ensureAbility(race, RaceAbilitySlot.ATTACK);
		changed |= ensureAbility(race, RaceAbilitySlot.DEFENSE);
		changed |= ensureAbility(race, RaceAbilitySlot.UNIQUE_ABILITY);
		changed |= ensureAbility(race, RaceAbilitySlot.SHNYAGA);
		changed |= ensureAbility(race, RaceAbilitySlot.STOCK);
		changed |= sanitizeAbility(race.attack, RaceAbilitySlot.ATTACK);
		changed |= sanitizeAbility(race.defense, RaceAbilitySlot.DEFENSE);
		changed |= sanitizeAbility(race.uniqueAbility, RaceAbilitySlot.UNIQUE_ABILITY);
		changed |= sanitizeAbility(race.shnyaga, RaceAbilitySlot.SHNYAGA);
		changed |= sanitizeAbility(race.stock, RaceAbilitySlot.STOCK);
		return changed;
	}

	private static boolean ensureAbility(PlayerRaceConfig race, RaceAbilitySlot slot) {
		RaceAbilityConfig current = switch (slot) {
			case ATTACK -> race.attack;
			case DEFENSE -> race.defense;
			case UNIQUE_ABILITY -> race.uniqueAbility;
			case SHNYAGA -> race.shnyaga;
			case STOCK -> race.stock;
		};
		if (current != null) {
			return false;
		}

		RaceAbilityConfig replacement = RaceAbilityConfig.defaults(slot);
		switch (slot) {
			case ATTACK -> race.attack = replacement;
			case DEFENSE -> race.defense = replacement;
			case UNIQUE_ABILITY -> race.uniqueAbility = replacement;
			case SHNYAGA -> race.shnyaga = replacement;
			case STOCK -> race.stock = replacement;
		}
		return true;
	}

	private static boolean sanitizeAbility(RaceAbilityConfig ability, RaceAbilitySlot slot) {
		boolean changed = false;
		changed |= normalizeString(ability.abilityId, slot.defaultAbilityId, value -> ability.abilityId = value);
		changed |= normalizeString(ability.name, slot.defaultDisplayName, value -> ability.name = value);
		changed |= normalizeString(ability.description, "", value -> ability.description = value);
		changed |= clampNonNegative(ability.cooldownSeconds, value -> ability.cooldownSeconds = value);
		changed |= clampNonNegative(ability.durationSeconds, value -> ability.durationSeconds = value);
		changed |= clampNonNegative(ability.rangeBlocks, value -> ability.rangeBlocks = value);
		changed |= clampChance(ability.chance, value -> ability.chance = value);
		if (ability.flags == null) {
			ability.flags = new LinkedHashMap<>();
			changed = true;
		}
		if (ability.numbers == null) {
			ability.numbers = new LinkedHashMap<>();
			changed = true;
		}
		if (ability.strings == null) {
			ability.strings = new LinkedHashMap<>();
			changed = true;
		}
		return changed;
	}

	private static boolean normalizeString(String value, String fallback, java.util.function.Consumer<String> setter) {
		String normalized = value == null ? fallback : value.trim();
		if (normalized.isEmpty() && !fallback.isEmpty()) {
			normalized = fallback;
		}
		if (value != null && value.equals(normalized)) {
			return false;
		}
		if (value == null && fallback.isEmpty()) {
			setter.accept("");
			return true;
		}
		setter.accept(normalized);
		return true;
	}

	private static boolean clampNonNegative(double value, java.util.function.DoubleConsumer setter) {
		double sanitized = Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
		if (Double.compare(sanitized, value) == 0) {
			return false;
		}
		setter.accept(sanitized);
		return true;
	}

	private static boolean clampChance(double value, java.util.function.DoubleConsumer setter) {
		double sanitized = Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
		if (Double.compare(sanitized, value) == 0) {
			return false;
		}
		setter.accept(sanitized);
		return true;
	}

	public enum RaceAbilitySlot {
		ATTACK("attack_template", "Атака"),
		DEFENSE("defense_template", "Защита"),
		UNIQUE_ABILITY("unique_ability_template", "Уникальная способность"),
		SHNYAGA("shnyaga_template", "Шняга"),
		STOCK("stock_template", "Сток");

		public final String defaultAbilityId;
		public final String defaultDisplayName;

		RaceAbilitySlot(String defaultAbilityId, String defaultDisplayName) {
			this.defaultAbilityId = defaultAbilityId;
			this.defaultDisplayName = defaultDisplayName;
		}
	}

	public static final class ConfigData {
		public List<PlayerRaceConfig> races = new ArrayList<>();

		private ConfigData() {
		}

		public static ConfigData defaults() {
			ConfigData data = new ConfigData();
			data.races.add(PlayerRaceConfig.template());
			return data;
		}
	}

	public static final class PlayerRaceConfig {
		public boolean enabled = false;
		public String id = "example_race";
		public String displayName = "Пример Расы";
		public String ownerNickname = "PlayerNickname";
		public String description = "Шаблон персональной расы. Включи запись и настрой 5 категорий способностей.";
		public RaceAbilityConfig attack = RaceAbilityConfig.defaults(RaceAbilitySlot.ATTACK);
		public RaceAbilityConfig defense = RaceAbilityConfig.defaults(RaceAbilitySlot.DEFENSE);
		public RaceAbilityConfig uniqueAbility = RaceAbilityConfig.defaults(RaceAbilitySlot.UNIQUE_ABILITY);
		public RaceAbilityConfig shnyaga = RaceAbilityConfig.defaults(RaceAbilitySlot.SHNYAGA);
		public RaceAbilityConfig stock = RaceAbilityConfig.defaults(RaceAbilitySlot.STOCK);

		private PlayerRaceConfig() {
		}

		public static PlayerRaceConfig template() {
			return new PlayerRaceConfig();
		}
	}

	public static final class RaceAbilityConfig {
		public boolean enabled = true;
		public String abilityId;
		public String name;
		public String description = "";
		public double cooldownSeconds = 0.0D;
		public double durationSeconds = 0.0D;
		public double rangeBlocks = 0.0D;
		public double chance = 1.0D;
		public int amplifier = 0;
		public double primaryValue = 0.0D;
		public double secondaryValue = 0.0D;
		public double tertiaryValue = 0.0D;
		public Map<String, Boolean> flags = new LinkedHashMap<>();
		public Map<String, Double> numbers = new LinkedHashMap<>();
		public Map<String, String> strings = new LinkedHashMap<>();

		private RaceAbilityConfig() {
		}

		public static RaceAbilityConfig defaults(RaceAbilitySlot slot) {
			RaceAbilityConfig config = new RaceAbilityConfig();
			config.abilityId = slot.defaultAbilityId;
			config.name = slot.defaultDisplayName;
			return config;
		}
	}
}
