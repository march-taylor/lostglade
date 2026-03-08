package com.lostglade.server.glitch;

import com.google.gson.JsonObject;
import com.lostglade.config.GlitchConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlackoutGlitch implements ServerGlitchHandler {
	private static final String MIN_TARGET_PLAYERS = "minTargetPlayers";
	private static final String MAX_TARGET_PLAYERS = "maxTargetPlayers";
	private static final String MIN_DURATION_SECONDS = "minDurationSeconds";
	private static final String MAX_DURATION_SECONDS = "maxDurationSeconds";
	private static final int BLINDNESS_AMPLIFIER = 3;
	private static final int DARKNESS_AMPLIFIER = 1;

	@Override
	public String id() {
		return "blackout";
	}

	@Override
	public GlitchConfig.GlitchEntry defaultEntry() {
		GlitchConfig.GlitchEntry entry = new GlitchConfig.GlitchEntry();
		entry.enabled = true;
		entry.minStabilityPercent = 0.0D;
		entry.maxStabilityPercent = 70.0D;
		entry.chancePerCheck = 0.30D;
		entry.stabilityInfluence = 1.0D;
		entry.minCooldownTicks = 80;
		entry.maxCooldownTicks = 1200;

		JsonObject settings = new JsonObject();
		settings.addProperty(MIN_TARGET_PLAYERS, 1);
		settings.addProperty(MAX_TARGET_PLAYERS, 3);
		settings.addProperty(MIN_DURATION_SECONDS, 4);
		settings.addProperty(MAX_DURATION_SECONDS, 12);
		entry.settings = settings;
		return entry;
	}

	@Override
	public boolean sanitizeSettings(GlitchConfig.GlitchEntry entry) {
		if (entry.settings == null) {
			entry.settings = new JsonObject();
		}

		boolean changed = false;
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MIN_TARGET_PLAYERS, 1, 1, 100);
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MAX_TARGET_PLAYERS, 3, 1, 100);
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MIN_DURATION_SECONDS, 4, 1, 120);
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MAX_DURATION_SECONDS, 12, 1, 120);

		int minTargets = GlitchSettingsHelper.getInt(entry.settings, MIN_TARGET_PLAYERS, 1);
		int maxTargets = GlitchSettingsHelper.getInt(entry.settings, MAX_TARGET_PLAYERS, 3);
		boolean minTargetExpression = GlitchSettingsHelper.isExpression(entry.settings, MIN_TARGET_PLAYERS);
		boolean maxTargetExpression = GlitchSettingsHelper.isExpression(entry.settings, MAX_TARGET_PLAYERS);
		if (!minTargetExpression && !maxTargetExpression && maxTargets < minTargets) {
			entry.settings.addProperty(MAX_TARGET_PLAYERS, minTargets);
			changed = true;
		}

		int minDuration = GlitchSettingsHelper.getInt(entry.settings, MIN_DURATION_SECONDS, 4);
		int maxDuration = GlitchSettingsHelper.getInt(entry.settings, MAX_DURATION_SECONDS, 12);
		if (maxDuration < minDuration) {
			entry.settings.addProperty(MAX_DURATION_SECONDS, minDuration);
			changed = true;
		}

		return changed;
	}

	@Override
	public boolean trigger(MinecraftServer server, RandomSource random, GlitchConfig.GlitchEntry entry, double stabilityPercent) {
		List<ServerPlayer> targets = collectTargets(server);
		if (targets.isEmpty()) {
			return false;
		}

		JsonObject settings = entry.settings == null ? new JsonObject() : entry.settings;
		double instability = getRangeInstabilityFactor(stabilityPercent, entry.minStabilityPercent, entry.maxStabilityPercent);

		int minTargets = GlitchSettingsHelper.getInt(settings, MIN_TARGET_PLAYERS, 1);
		int maxTargets = GlitchSettingsHelper.getInt(settings, MAX_TARGET_PLAYERS, 3);
		int targetCount = interpolateInt(minTargets, maxTargets, instability);
		targetCount = Math.max(1, Math.min(targets.size(), targetCount));

		int minDurationSeconds = GlitchSettingsHelper.getInt(settings, MIN_DURATION_SECONDS, 4);
		int maxDurationSeconds = GlitchSettingsHelper.getInt(settings, MAX_DURATION_SECONDS, 12);
		int durationSeconds = interpolateInt(minDurationSeconds, maxDurationSeconds, instability);
		int durationTicks = Math.max(20, durationSeconds * 20);

		shuffle(targets, random);
		boolean appliedAny = false;
		for (int i = 0; i < targetCount; i++) {
			ServerPlayer player = targets.get(i);
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, durationTicks, BLINDNESS_AMPLIFIER, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, durationTicks, DARKNESS_AMPLIFIER, false, false, false));
			appliedAny = true;
		}

		return appliedAny;
	}

	private static List<ServerPlayer> collectTargets(MinecraftServer server) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			players.add(player);
		}
		return players;
	}

	private static int interpolateInt(int min, int max, double factor) {
		if (max <= min) {
			return min;
		}
		double clamped = Math.max(0.0D, Math.min(1.0D, factor));
		return min + (int) Math.round((max - min) * clamped);
	}

	private static double getRangeInstabilityFactor(double stabilityPercent, double minStabilityPercent, double maxStabilityPercent) {
		double range = maxStabilityPercent - minStabilityPercent;
		if (range <= 1.0E-9D) {
			return 1.0D;
		}
		double normalized = (stabilityPercent - minStabilityPercent) / range;
		return Math.max(0.0D, Math.min(1.0D, 1.0D - normalized));
	}

	private static void shuffle(List<ServerPlayer> list, RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			int swapWith = random.nextInt(i + 1);
			Collections.swap(list, i, swapWith);
		}
	}
}
