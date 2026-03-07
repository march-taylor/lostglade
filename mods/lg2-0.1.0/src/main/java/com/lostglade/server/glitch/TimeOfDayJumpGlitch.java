package com.lostglade.server.glitch;

import com.google.gson.JsonObject;
import com.lostglade.config.GlitchConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

public final class TimeOfDayJumpGlitch implements ServerGlitchHandler {
	private static final String MIN_JUMP_TICKS = "minJumpTicks";
	private static final String MAX_JUMP_TICKS = "maxJumpTicks";

	@Override
	public String id() {
		return "time_of_day_jumps";
	}

	@Override
	public GlitchConfig.GlitchEntry defaultEntry() {
		GlitchConfig.GlitchEntry entry = new GlitchConfig.GlitchEntry();
		entry.enabled = true;
		entry.minStabilityPercent = 0.0D;
		entry.maxStabilityPercent = 100.0D;
		entry.chancePerCheck = 0.30D;
		entry.stabilityInfluence = 0.90D;
		entry.minCooldownTicks = 80;
		entry.maxCooldownTicks = 2400;

		JsonObject settings = new JsonObject();
		settings.addProperty(MIN_JUMP_TICKS, 80);
		settings.addProperty(MAX_JUMP_TICKS, 12000);
		entry.settings = settings;
		return entry;
	}

	@Override
	public boolean sanitizeSettings(GlitchConfig.GlitchEntry entry) {
		if (entry.settings == null) {
			entry.settings = new JsonObject();
		}

		boolean changed = false;
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MIN_JUMP_TICKS, 80, 1, 24000);
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MAX_JUMP_TICKS, 12000, 1, 120000);

		int minJump = GlitchSettingsHelper.getInt(entry.settings, MIN_JUMP_TICKS, 80);
		int maxJump = GlitchSettingsHelper.getInt(entry.settings, MAX_JUMP_TICKS, 12000);
		if (maxJump < minJump) {
			entry.settings.addProperty(MAX_JUMP_TICKS, minJump);
			changed = true;
		}

		return changed;
	}

	@Override
	public boolean trigger(MinecraftServer server, RandomSource random, GlitchConfig.GlitchEntry entry, double stabilityPercent) {
		ServerLevel overworld = server.overworld();
		if (overworld == null) {
			return false;
		}

		JsonObject settings = entry.settings == null ? new JsonObject() : entry.settings;
		int minJump = GlitchSettingsHelper.getInt(settings, MIN_JUMP_TICKS, 80);
		int maxJump = GlitchSettingsHelper.getInt(settings, MAX_JUMP_TICKS, 12000);
		double instabilityFactor = getRangeInstabilityFactor(
				stabilityPercent,
				entry.minStabilityPercent,
				entry.maxStabilityPercent
		);

		int scaledMaxJump = minJump + (int) Math.round((maxJump - minJump) * clamp01(instabilityFactor));
		if (scaledMaxJump < minJump) {
			scaledMaxJump = minJump;
		}

		int jumpTicks = sampleRangeInt(random, minJump, scaledMaxJump);
		if (jumpTicks <= 0) {
			return false;
		}

		long newDayTime = overworld.getDayTime() + jumpTicks;
		for (ServerLevel level : server.getAllLevels()) {
			level.setDayTime(newDayTime);
		}
		return true;
	}

	private static int sampleRangeInt(RandomSource random, int min, int max) {
		if (max <= min) {
			return min;
		}
		return min + random.nextInt(max - min + 1);
	}

	private static double getRangeInstabilityFactor(double stabilityPercent, double minStabilityPercent, double maxStabilityPercent) {
		double range = maxStabilityPercent - minStabilityPercent;
		if (range <= 1.0E-9D) {
			return 1.0D;
		}

		double normalized = (stabilityPercent - minStabilityPercent) / range;
		return clamp01(1.0D - normalized);
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}
}
