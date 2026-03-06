package com.lostglade.server.glitch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public final class GlitchSettingsHelper {
	private GlitchSettingsHelper() {
	}

	public static int getInt(JsonObject settings, String key, int fallback) {
		JsonElement element = settings.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			return fallback;
		}

		try {
			return primitive.getAsInt();
		} catch (Exception ignored) {
			return fallback;
		}
	}

	public static double getDouble(JsonObject settings, String key, double fallback) {
		JsonElement element = settings.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
			return fallback;
		}

		try {
			double value = primitive.getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	public static boolean sanitizeInt(
			JsonObject settings,
			String key,
			int defaultValue,
			int minInclusive,
			int maxInclusive
	) {
		int value = getInt(settings, key, defaultValue);
		int clamped = Math.max(minInclusive, Math.min(maxInclusive, value));
		if (value == clamped && settings.has(key)) {
			return false;
		}

		settings.addProperty(key, clamped);
		return true;
	}

	public static boolean sanitizeDouble(
			JsonObject settings,
			String key,
			double defaultValue,
			double minInclusive,
			double maxInclusive
	) {
		double value = getDouble(settings, key, defaultValue);
		double clamped = Math.max(minInclusive, Math.min(maxInclusive, value));
		if (Double.compare(value, clamped) == 0 && settings.has(key)) {
			return false;
		}

		settings.addProperty(key, clamped);
		return true;
	}
}
