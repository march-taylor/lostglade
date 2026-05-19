package com.lostglade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import com.lostglade.Lg2;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class RaceConfig {
	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.registerTypeAdapter(RaceAbilityConfig.class, new RaceAbilityConfigSerializer())
			.create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve(Lg2.MOD_ID + "-races.json");
	private static final int MAX_PRICE_BITCOINS = 1_000_000;
	private static final String NO_RACE_ID = "no_race";
	public static final double INFINITE_COOLDOWN_SECONDS = -1.0D;

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
		boolean preserveBlankAbilityNames = NO_RACE_ID.equals(race.id == null ? "" : race.id.trim());
		changed |= sanitizeAbility(race.attack, RaceAbilitySlot.ATTACK, preserveBlankAbilityNames);
		changed |= sanitizeAbility(race.defense, RaceAbilitySlot.DEFENSE, preserveBlankAbilityNames);
		changed |= sanitizeAbility(race.uniqueAbility, RaceAbilitySlot.UNIQUE_ABILITY, preserveBlankAbilityNames);
		changed |= sanitizeAbility(race.shnyaga, RaceAbilitySlot.SHNYAGA, preserveBlankAbilityNames);
		changed |= sanitizeAbility(race.stock, RaceAbilitySlot.STOCK, preserveBlankAbilityNames);
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

	private static boolean sanitizeAbility(RaceAbilityConfig ability, RaceAbilitySlot slot, boolean preserveBlankName) {
		boolean changed = false;
		changed |= normalizeString(ability.abilityId, slot.defaultAbilityId, value -> ability.abilityId = value);
		changed |= normalizeString(ability.name, preserveBlankName ? "" : slot.defaultDisplayName, value -> ability.name = value);
		changed |= normalizeString(ability.description, "", value -> ability.description = value);
		changed |= normalizePrice(ability.priceBitcoins, value -> ability.priceBitcoins = value);
		changed |= normalizeCooldownSeconds(ability.cooldownSeconds, value -> ability.cooldownSeconds = value);
		changed |= normalizeNonNegative(ability.activationRangeBlocks, value -> ability.activationRangeBlocks = value);
		changed |= normalizeNonNegative(ability.durationSeconds, value -> ability.durationSeconds = value);
		changed |= normalizeNonNegative(ability.innerMinDistanceBlocks, value -> ability.innerMinDistanceBlocks = value);
		changed |= normalizeNonNegative(ability.followMaxDistanceBlocks, value -> ability.followMaxDistanceBlocks = value);
		changed |= normalizeNonNegative(ability.maxOutsideAreaSeconds, value -> ability.maxOutsideAreaSeconds = value);
		changed |= normalizeNonNegative(ability.healthPoints, value -> ability.healthPoints = value);
		changed |= normalizeNonNegative(ability.reflectedDamageRatio, value -> ability.reflectedDamageRatio = value);
		changed |= normalizeNonNegative(ability.summonLifetimeSeconds, value -> ability.summonLifetimeSeconds = value);
		changed |= normalizeNonNegative(ability.summonAfterKillSeconds, value -> ability.summonAfterKillSeconds = value);
		changed |= normalizeNonNegative(ability.minGrowthSeconds, value -> ability.minGrowthSeconds = value);
		changed |= normalizeNonNegative(ability.maxGrowthSeconds, value -> ability.maxGrowthSeconds = value);
		changed |= normalizeNonNegative(ability.tubochkaBurnSeconds, value -> ability.tubochkaBurnSeconds = value);
		changed |= normalizeNonNegative(ability.tubochkaMaxReleaseSmokeParticles, value -> ability.tubochkaMaxReleaseSmokeParticles = value);
		changed |= normalizeNonNegative(ability.methadoneAddictionSeconds, value -> ability.methadoneAddictionSeconds = value);
		changed |= normalizeNonNegative(ability.methadoneWithdrawalStartSeconds, value -> ability.methadoneWithdrawalStartSeconds = value);
		changed |= normalizeChance(ability.cocaineHallucinationChance, value -> ability.cocaineHallucinationChance = value);
		changed |= normalizeNonNegative(ability.foodRestoreMultiplier, value -> ability.foodRestoreMultiplier = value);
		changed |= normalizeNonNegative(ability.copperGolemNoticeRangeBlocks, value -> ability.copperGolemNoticeRangeBlocks = value);
		changed |= normalizeNonNegative(ability.copperGogglesScanCooldownSeconds, value -> ability.copperGogglesScanCooldownSeconds = value);
		changed |= normalizeNonNegative(ability.copperGogglesOreSearchRadiusBlocks, value -> ability.copperGogglesOreSearchRadiusBlocks = value);
		changed |= normalizeNonNegative(ability.copperGogglesOreSearchHighlightSeconds, value -> ability.copperGogglesOreSearchHighlightSeconds = value);
		changed |= normalizeNonNegative(ability.copperGogglesTrackingRadiusBlocks, value -> ability.copperGogglesTrackingRadiusBlocks = value);
		changed |= normalizeNonNegative(ability.copperGogglesTrackingHighlightSeconds, value -> ability.copperGogglesTrackingHighlightSeconds = value);
		changed |= normalizeNonNegative(ability.womanFlowerCooldownSeconds, value -> ability.womanFlowerCooldownSeconds = value);
		changed |= normalizeNonNegative(ability.womanAnimalBreedCooldownSeconds, value -> ability.womanAnimalBreedCooldownSeconds = value);
		changed |= normalizeNonNegative(ability.womanAttackChargeRadiusBlocks, value -> ability.womanAttackChargeRadiusBlocks = value);
		changed |= normalizeNonNegative(ability.womanAttackRangeBlocks, value -> ability.womanAttackRangeBlocks = value);
		changed |= normalizeNonNegative(ability.womanAttackDamage, value -> ability.womanAttackDamage = value);
		changed |= normalizeNonNegative(ability.womanAttackFollowSeconds, value -> ability.womanAttackFollowSeconds = value);
		changed |= normalizeNonNegative(ability.womanUniqueDropMinSeconds, value -> ability.womanUniqueDropMinSeconds = value);
		changed |= normalizeNonNegative(ability.womanUniqueDropMaxSeconds, value -> ability.womanUniqueDropMaxSeconds = value);
		changed |= normalizeChance(ability.womanUniqueDropChance, value -> ability.womanUniqueDropChance = value);
		changed |= normalizeNonNegative(ability.womanUniqueTradePriceIncrease, value -> ability.womanUniqueTradePriceIncrease = value);
		changed |= normalizeNonNegative(ability.womanUniqueAbsorptionHearts, value -> ability.womanUniqueAbsorptionHearts = value);
		changed |= normalizeNonNegative(ability.womanShnyagaTransferHearts, value -> ability.womanShnyagaTransferHearts = value);
		changed |= normalizeNonNegative(ability.womanShnyagaBuffRangeBlocks, value -> ability.womanShnyagaBuffRangeBlocks = value);
		changed |= normalizeNonNegative(ability.womanShnyagaRejectDamageHearts, value -> ability.womanShnyagaRejectDamageHearts = value);
		changed |= normalizeNonNegative(ability.womanShnyagaRejectDebuffSeconds, value -> ability.womanShnyagaRejectDebuffSeconds = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyHealthPoints, value -> ability.gennadiyDonkeyHealthPoints = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyArmorDivider, value -> ability.gennadiyDonkeyArmorDivider = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyHealthRegenSeconds, value -> ability.gennadiyDonkeyHealthRegenSeconds = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyBulletDamage, value -> ability.gennadiyDonkeyBulletDamage = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyBulletRangeBlocks, value -> ability.gennadiyDonkeyBulletRangeBlocks = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyFollowMaxDistanceBlocks, value -> ability.gennadiyDonkeyFollowMaxDistanceBlocks = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyAmmoRegenSeconds, value -> ability.gennadiyDonkeyAmmoRegenSeconds = value);
		changed |= normalizeNonNegative(ability.gennadiyDefenseDurationSeconds, value -> ability.gennadiyDefenseDurationSeconds = value);
		changed |= normalizeNonNegative(ability.gennadiyDefenseKnockbackBlocksPerDamage, value -> ability.gennadiyDefenseKnockbackBlocksPerDamage = value);
		changed |= normalizeNonNegative(ability.gennadiyDefenseMaxKnockbackBlocks, value -> ability.gennadiyDefenseMaxKnockbackBlocks = value);
		changed |= normalizeNonNegative(ability.gennadiyDefenseWaveRangeBlocks, value -> ability.gennadiyDefenseWaveRangeBlocks = value);
		changed |= normalizeNonNegative(ability.gennadiyDefenseMinDamage, value -> ability.gennadiyDefenseMinDamage = value);
		changed |= normalizeNonNegative(ability.gennadiyDefenseMaxDamage, value -> ability.gennadiyDefenseMaxDamage = value);
		changed |= normalizeNonNegative(ability.gennadiyHookRangeBlocks, value -> ability.gennadiyHookRangeBlocks = value);
		changed |= normalizeNonNegative(ability.gennadiyHookDamage, value -> ability.gennadiyHookDamage = value);
		changed |= normalizeNonNegative(ability.gennadiyHookSlownessSeconds, value -> ability.gennadiyHookSlownessSeconds = value);
		changed |= normalizeNonNegative(ability.gennadiyRageHealthThresholdRatio, value -> ability.gennadiyRageHealthThresholdRatio = value);
		changed |= normalizeNonNegative(ability.gennadiyRageMeleeDamageBonusRatio, value -> ability.gennadiyRageMeleeDamageBonusRatio = value);
		changed |= normalizeNonNegative(ability.gennadiyReportCooldownSeconds, value -> ability.gennadiyReportCooldownSeconds = value);
		changed |= normalizeNonNegative(ability.jetpackMaxRiseBlocks, value -> ability.jetpackMaxRiseBlocks = value);
		changed |= normalizeChance(ability.repulsorNaturalLightningChargeChance, value -> ability.repulsorNaturalLightningChargeChance = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyMaxAmmo, (java.util.function.IntConsumer) value -> ability.gennadiyDonkeyMaxAmmo = value);
		changed |= normalizeNonNegative(ability.gennadiyDonkeyAmmoRegenAmount, (java.util.function.IntConsumer) value -> ability.gennadiyDonkeyAmmoRegenAmount = value);
		changed |= normalizeNonNegative(ability.gennadiyRageHasteLevel, (java.util.function.IntConsumer) value -> ability.gennadiyRageHasteLevel = value);
		changed |= normalizeNonNegative(ability.copperIngotFoodPoints, (java.util.function.IntConsumer) value -> ability.copperIngotFoodPoints = value);
		changed |= normalizeNonNegative(ability.repulsorMaxCharges, (java.util.function.IntConsumer) value -> ability.repulsorMaxCharges = value);
		changed |= normalizeNonNegative(ability.repulsorCopperIngotChargeRestore, (java.util.function.IntConsumer) value -> ability.repulsorCopperIngotChargeRestore = value);
		changed |= normalizeNonNegative(ability.repulsorNaturalLightningChargeRestore, (java.util.function.IntConsumer) value -> ability.repulsorNaturalLightningChargeRestore = value);
		changed |= normalizeChance(ability.chance, value -> ability.chance = value);
		if (ability.womanUniqueDropMaxSeconds < ability.womanUniqueDropMinSeconds) {
			ability.womanUniqueDropMaxSeconds = ability.womanUniqueDropMinSeconds;
			changed = true;
		}
		if (ability.maxGrowthSeconds < ability.minGrowthSeconds) {
			ability.maxGrowthSeconds = ability.minGrowthSeconds;
			changed = true;
		}
		if (ability.methadoneAddictionSeconds > 0.0D && ability.methadoneWithdrawalStartSeconds > ability.methadoneAddictionSeconds) {
			ability.methadoneWithdrawalStartSeconds = ability.methadoneAddictionSeconds;
			changed = true;
		}
		if (ability.gennadiyDefenseMaxDamage < ability.gennadiyDefenseMinDamage) {
			ability.gennadiyDefenseMaxDamage = ability.gennadiyDefenseMinDamage;
			changed = true;
		}
		return changed;
	}

	private static boolean normalizeCooldownSeconds(double value, java.util.function.DoubleConsumer setter) {
		double normalized;
		if (Double.isNaN(value)) {
			normalized = 0.0D;
		} else if (Double.compare(value, INFINITE_COOLDOWN_SECONDS) == 0) {
			normalized = INFINITE_COOLDOWN_SECONDS;
		} else {
			normalized = Math.max(0.0D, value);
		}
		if (Double.compare(value, normalized) == 0) {
			return false;
		}
		setter.accept(normalized);
		return true;
	}

	private static boolean normalizeNonNegative(double value, java.util.function.DoubleConsumer setter) {
		double normalized = Double.isNaN(value) ? 0.0D : Math.max(0.0D, value);
		if (Double.compare(value, normalized) == 0) {
			return false;
		}
		setter.accept(normalized);
		return true;
	}

	private static boolean normalizeChance(double value, java.util.function.DoubleConsumer setter) {
		double normalized = Double.isNaN(value) ? 0.0D : Math.max(0.0D, Math.min(1.0D, value));
		if (Double.compare(value, normalized) == 0) {
			return false;
		}
		setter.accept(normalized);
		return true;
	}

	private static boolean normalizePrice(int value, java.util.function.IntConsumer setter) {
		int normalized = Math.max(0, Math.min(MAX_PRICE_BITCOINS, value));
		if (value == normalized) {
			return false;
		}
		setter.accept(normalized);
		return true;
	}

	private static boolean normalizeNonNegative(int value, java.util.function.IntConsumer setter) {
		int normalized = Math.max(0, value);
		if (value == normalized) {
			return false;
		}
		setter.accept(normalized);
		return true;
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

	private static final class RaceAbilityConfigSerializer implements JsonSerializer<RaceAbilityConfig> {
		@Override
		public JsonElement serialize(RaceAbilityConfig ability, Type type, JsonSerializationContext context) {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", ability.enabled);
			addString(json, "abilityId", ability.abilityId);
			addString(json, "name", ability.name);
			addString(json, "description", ability.description);
			addIntIfNonZero(json, "priceBitcoins", ability.priceBitcoins);
			addDoubleIfNonZero(json, "cooldownSeconds", ability.cooldownSeconds);
			addDoubleIfNonZero(json, "activationRangeBlocks", ability.activationRangeBlocks);
			addDoubleIfNonZero(json, "durationSeconds", ability.durationSeconds);
			addDoubleIfNonZero(json, "innerMinDistanceBlocks", ability.innerMinDistanceBlocks);
			addDoubleIfNonZero(json, "followMaxDistanceBlocks", ability.followMaxDistanceBlocks);
			addDoubleIfNonZero(json, "maxOutsideAreaSeconds", ability.maxOutsideAreaSeconds);
			addDoubleIfNonZero(json, "healthPoints", ability.healthPoints);
			addDoubleIfNonZero(json, "reflectedDamageRatio", ability.reflectedDamageRatio);
			addDoubleIfNonZero(json, "summonLifetimeSeconds", ability.summonLifetimeSeconds);
			addDoubleIfNonZero(json, "summonAfterKillSeconds", ability.summonAfterKillSeconds);
			addDoubleIfNonZero(json, "minGrowthSeconds", ability.minGrowthSeconds);
			addDoubleIfNonZero(json, "maxGrowthSeconds", ability.maxGrowthSeconds);
			addDoubleIfNonZero(json, "tubochkaBurnSeconds", ability.tubochkaBurnSeconds);
			if (Double.compare(ability.tubochkaMaxReleaseSmokeParticles, 8.0D) != 0) {
				addDoubleIfNonZero(json, "tubochkaMaxReleaseSmokeParticles", ability.tubochkaMaxReleaseSmokeParticles);
			}
			addDoubleIfNonZero(json, "methadoneAddictionSeconds", ability.methadoneAddictionSeconds);
			addDoubleIfNonZero(json, "methadoneWithdrawalStartSeconds", ability.methadoneWithdrawalStartSeconds);
			addDoubleIfNonZero(json, "cocaineHallucinationChance", ability.cocaineHallucinationChance);
			addDoubleIfNonZero(json, "foodRestoreMultiplier", ability.foodRestoreMultiplier);
			addIntIfNonZero(json, "copperIngotFoodPoints", ability.copperIngotFoodPoints);
			addDoubleIfNonZero(json, "copperGolemNoticeRangeBlocks", ability.copperGolemNoticeRangeBlocks);
			addDoubleIfNonZero(json, "copperGogglesScanCooldownSeconds", ability.copperGogglesScanCooldownSeconds);
			addDoubleIfNonZero(json, "copperGogglesOreSearchRadiusBlocks", ability.copperGogglesOreSearchRadiusBlocks);
			addDoubleIfNonZero(json, "copperGogglesOreSearchHighlightSeconds", ability.copperGogglesOreSearchHighlightSeconds);
			addDoubleIfNonZero(json, "copperGogglesTrackingRadiusBlocks", ability.copperGogglesTrackingRadiusBlocks);
			addDoubleIfNonZero(json, "copperGogglesTrackingHighlightSeconds", ability.copperGogglesTrackingHighlightSeconds);
			addDoubleIfNonZero(json, "womanFlowerCooldownSeconds", ability.womanFlowerCooldownSeconds);
			addDoubleIfNonZero(json, "womanAnimalBreedCooldownSeconds", ability.womanAnimalBreedCooldownSeconds);
			addDoubleIfNonZero(json, "womanAttackChargeRadiusBlocks", ability.womanAttackChargeRadiusBlocks);
			addDoubleIfNonZero(json, "womanAttackRangeBlocks", ability.womanAttackRangeBlocks);
			addDoubleIfNonZero(json, "womanAttackDamage", ability.womanAttackDamage);
			addDoubleIfNonZero(json, "womanAttackFollowSeconds", ability.womanAttackFollowSeconds);
			addDoubleIfNonZero(json, "womanUniqueDropMinSeconds", ability.womanUniqueDropMinSeconds);
			addDoubleIfNonZero(json, "womanUniqueDropMaxSeconds", ability.womanUniqueDropMaxSeconds);
			addDoubleIfNonZero(json, "womanUniqueDropChance", ability.womanUniqueDropChance);
			addDoubleIfNonZero(json, "womanUniqueTradePriceIncrease", ability.womanUniqueTradePriceIncrease);
			addDoubleIfNonZero(json, "womanUniqueAbsorptionHearts", ability.womanUniqueAbsorptionHearts);
			addDoubleIfNonZero(json, "womanShnyagaTransferHearts", ability.womanShnyagaTransferHearts);
			addDoubleIfNonZero(json, "womanShnyagaBuffRangeBlocks", ability.womanShnyagaBuffRangeBlocks);
			addDoubleIfNonZero(json, "womanShnyagaRejectDamageHearts", ability.womanShnyagaRejectDamageHearts);
			addDoubleIfNonZero(json, "womanShnyagaRejectDebuffSeconds", ability.womanShnyagaRejectDebuffSeconds);
			addDoubleIfNonZero(json, "gennadiyDonkeyHealthPoints", ability.gennadiyDonkeyHealthPoints);
			addDoubleIfNonZero(json, "gennadiyDonkeyArmorDivider", ability.gennadiyDonkeyArmorDivider);
			addDoubleIfNonZero(json, "gennadiyDonkeyHealthRegenSeconds", ability.gennadiyDonkeyHealthRegenSeconds);
			addIntIfNonZero(json, "gennadiyDonkeyMaxAmmo", ability.gennadiyDonkeyMaxAmmo);
			addIntIfNonZero(json, "gennadiyDonkeyAmmoRegenAmount", ability.gennadiyDonkeyAmmoRegenAmount);
			addDoubleIfNonZero(json, "gennadiyDonkeyAmmoRegenSeconds", ability.gennadiyDonkeyAmmoRegenSeconds);
			addDoubleIfNonZero(json, "gennadiyDonkeyBulletDamage", ability.gennadiyDonkeyBulletDamage);
			addDoubleIfNonZero(json, "gennadiyDonkeyBulletRangeBlocks", ability.gennadiyDonkeyBulletRangeBlocks);
			addDoubleIfNonZero(json, "gennadiyDonkeyFollowMaxDistanceBlocks", ability.gennadiyDonkeyFollowMaxDistanceBlocks);
			addDoubleIfNonZero(json, "gennadiyDefenseDurationSeconds", ability.gennadiyDefenseDurationSeconds);
			addDoubleIfNonZero(json, "gennadiyDefenseKnockbackBlocksPerDamage", ability.gennadiyDefenseKnockbackBlocksPerDamage);
			addDoubleIfNonZero(json, "gennadiyDefenseMaxKnockbackBlocks", ability.gennadiyDefenseMaxKnockbackBlocks);
			addDoubleIfNonZero(json, "gennadiyDefenseWaveRangeBlocks", ability.gennadiyDefenseWaveRangeBlocks);
			addDoubleIfNonZero(json, "gennadiyDefenseMinDamage", ability.gennadiyDefenseMinDamage);
			addDoubleIfNonZero(json, "gennadiyDefenseMaxDamage", ability.gennadiyDefenseMaxDamage);
			addDoubleIfNonZero(json, "gennadiyHookRangeBlocks", ability.gennadiyHookRangeBlocks);
			addDoubleIfNonZero(json, "gennadiyHookDamage", ability.gennadiyHookDamage);
			addDoubleIfNonZero(json, "gennadiyHookSlownessSeconds", ability.gennadiyHookSlownessSeconds);
			addDoubleIfNonZero(json, "gennadiyRageHealthThresholdRatio", ability.gennadiyRageHealthThresholdRatio);
			addIntIfNonZero(json, "gennadiyRageHasteLevel", ability.gennadiyRageHasteLevel);
			addDoubleIfNonZero(json, "gennadiyRageMeleeDamageBonusRatio", ability.gennadiyRageMeleeDamageBonusRatio);
			addDoubleIfNonZero(json, "gennadiyReportCooldownSeconds", ability.gennadiyReportCooldownSeconds);
			addDoubleIfNonZero(json, "jetpackMaxRiseBlocks", ability.jetpackMaxRiseBlocks);
			addIntIfNonZero(json, "repulsorMaxCharges", ability.repulsorMaxCharges);
			addIntIfNonZero(json, "repulsorCopperIngotChargeRestore", ability.repulsorCopperIngotChargeRestore);
			addDoubleIfNonZero(json, "repulsorNaturalLightningChargeChance", ability.repulsorNaturalLightningChargeChance);
			addIntIfNonZero(json, "repulsorNaturalLightningChargeRestore", ability.repulsorNaturalLightningChargeRestore);
			addDoubleIfNonZero(json, "chance", ability.chance);
			return json;
		}

		private static void addString(JsonObject json, String key, String value) {
			if (value != null) {
				json.addProperty(key, value);
			}
		}

		private static void addIntIfNonZero(JsonObject json, String key, int value) {
			if (value != 0) {
				json.addProperty(key, value);
			}
		}

		private static void addDoubleIfNonZero(JsonObject json, String key, double value) {
			if (Double.compare(value, 0.0D) != 0) {
				json.addProperty(key, value);
			}
		}
	}

	public enum RaceAbilitySlot {
		ATTACK("attack_template", "Атака", 300),
		DEFENSE("defense_template", "Защита", 600),
		UNIQUE_ABILITY("unique_ability_template", "Уникальная способность", 1000),
		SHNYAGA("shnyaga_template", "Шняга", 1500),
		STOCK("stock_template", "Сток", 0);

		public final String defaultAbilityId;
		public final String defaultDisplayName;
		public final int defaultPriceBitcoins;

		RaceAbilitySlot(String defaultAbilityId, String defaultDisplayName, int defaultPriceBitcoins) {
			this.defaultAbilityId = defaultAbilityId;
			this.defaultDisplayName = defaultDisplayName;
			this.defaultPriceBitcoins = defaultPriceBitcoins;
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
		public int priceBitcoins = 0;
		public double cooldownSeconds = 0.0D;
		public double activationRangeBlocks = 0.0D;
		public double durationSeconds = 0.0D;
		public double innerMinDistanceBlocks = 0.0D;
		public double followMaxDistanceBlocks = 0.0D;
		public double maxOutsideAreaSeconds = 0.0D;
		public double healthPoints = 0.0D;
		public double reflectedDamageRatio = 0.0D;
		public double summonLifetimeSeconds = 0.0D;
		public double summonAfterKillSeconds = 0.0D;
		public double minGrowthSeconds = 0.0D;
		public double maxGrowthSeconds = 0.0D;
		public double tubochkaBurnSeconds = 0.0D;
		public double tubochkaMaxReleaseSmokeParticles = 8.0D;
		public double methadoneAddictionSeconds = 0.0D;
		public double methadoneWithdrawalStartSeconds = 0.0D;
		public double cocaineHallucinationChance = 0.0D;
		public double foodRestoreMultiplier = 0.0D;
		public int copperIngotFoodPoints = 0;
		public double copperGolemNoticeRangeBlocks = 0.0D;
		@SerializedName(value = "copperGogglesScanCooldownSeconds", alternate = {"copperGogglesOreSearchCooldownSeconds"})
		public double copperGogglesScanCooldownSeconds = 0.0D;
		public double copperGogglesOreSearchRadiusBlocks = 0.0D;
		public double copperGogglesOreSearchHighlightSeconds = 0.0D;
		public double copperGogglesTrackingRadiusBlocks = 0.0D;
		public double copperGogglesTrackingHighlightSeconds = 0.0D;
		public double womanFlowerCooldownSeconds = 0.0D;
		public double womanAnimalBreedCooldownSeconds = 0.0D;
		public double womanAttackChargeRadiusBlocks = 0.0D;
		public double womanAttackRangeBlocks = 0.0D;
		public double womanAttackDamage = 0.0D;
		public double womanAttackFollowSeconds = 0.0D;
		public double womanUniqueDropMinSeconds = 0.0D;
		public double womanUniqueDropMaxSeconds = 0.0D;
		public double womanUniqueDropChance = 0.0D;
		public double womanUniqueTradePriceIncrease = 0.0D;
		public double womanUniqueAbsorptionHearts = 0.0D;
		public double womanShnyagaTransferHearts = 0.0D;
		public double womanShnyagaBuffRangeBlocks = 0.0D;
		public double womanShnyagaRejectDamageHearts = 0.0D;
		public double womanShnyagaRejectDebuffSeconds = 0.0D;
		public double gennadiyDonkeyHealthPoints = 0.0D;
		public double gennadiyDonkeyArmorDivider = 0.0D;
		public double gennadiyDonkeyHealthRegenSeconds = 0.0D;
		public int gennadiyDonkeyMaxAmmo = 0;
		public int gennadiyDonkeyAmmoRegenAmount = 0;
		public double gennadiyDonkeyAmmoRegenSeconds = 0.0D;
		public double gennadiyDonkeyBulletDamage = 0.0D;
		public double gennadiyDonkeyBulletRangeBlocks = 0.0D;
		public double gennadiyDonkeyFollowMaxDistanceBlocks = 0.0D;
		public double gennadiyDefenseDurationSeconds = 0.0D;
		public double gennadiyDefenseKnockbackBlocksPerDamage = 0.0D;
		public double gennadiyDefenseMaxKnockbackBlocks = 0.0D;
		public double gennadiyDefenseWaveRangeBlocks = 0.0D;
		public double gennadiyDefenseMinDamage = 0.0D;
		public double gennadiyDefenseMaxDamage = 0.0D;
		public double gennadiyHookRangeBlocks = 0.0D;
		public double gennadiyHookDamage = 0.0D;
		public double gennadiyHookSlownessSeconds = 0.0D;
		public double gennadiyRageHealthThresholdRatio = 0.0D;
		public int gennadiyRageHasteLevel = 0;
		public double gennadiyRageMeleeDamageBonusRatio = 0.0D;
		public double gennadiyReportCooldownSeconds = 0.0D;
		public double jetpackMaxRiseBlocks = 0.0D;
		public int repulsorMaxCharges = 0;
		public int repulsorCopperIngotChargeRestore = 0;
		public double repulsorNaturalLightningChargeChance = 0.0D;
		public int repulsorNaturalLightningChargeRestore = 0;
		public double chance = 0.0D;

		private RaceAbilityConfig() {
		}

		public static RaceAbilityConfig defaults(RaceAbilitySlot slot) {
			RaceAbilityConfig config = new RaceAbilityConfig();
			config.abilityId = slot.defaultAbilityId;
			config.name = slot.defaultDisplayName;
			config.priceBitcoins = slot.defaultPriceBitcoins;
			return config;
		}
	}
}
