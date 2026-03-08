package com.lostglade.server.glitch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lostglade.config.ConfigVariableResolver;

public final class GlitchSettingsHelper {
	private GlitchSettingsHelper() {
	}

	public static int getInt(JsonObject settings, String key, int fallback) {
		JsonElement element = settings.get(key);
		if (!(element instanceof JsonPrimitive primitive)) {
			return fallback;
		}

		try {
			if (primitive.isNumber()) {
				return primitive.getAsInt();
			}
			if (primitive.isString()) {
				Double evaluated = ConfigVariableResolver.evaluateStringExpression(primitive.getAsString());
				return evaluated == null ? fallback : (int) Math.round(evaluated);
			}
			return fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	public static double getDouble(JsonObject settings, String key, double fallback) {
		JsonElement element = settings.get(key);
		if (!(element instanceof JsonPrimitive primitive)) {
			return fallback;
		}

		try {
			double value;
			if (primitive.isNumber()) {
				value = primitive.getAsDouble();
			} else if (primitive.isString()) {
				Double evaluated = ConfigVariableResolver.evaluateStringExpression(primitive.getAsString());
				if (evaluated == null) {
					return fallback;
				}
				value = evaluated;
			} else {
				return fallback;
			}
			return Double.isFinite(value) ? value : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	public static boolean isExpression(JsonObject settings, String key) {
		JsonElement element = settings.get(key);
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
			return false;
		}
		return ConfigVariableResolver.evaluateStringExpression(primitive.getAsString()) != null;
	}

	public static boolean sanitizeInt(
			JsonObject settings,
			String key,
			int defaultValue,
			int minInclusive,
			int maxInclusive
	) {
		JsonElement current = settings.get(key);
		if (current instanceof JsonPrimitive primitive && primitive.isString()) {
			if (ConfigVariableResolver.evaluateStringExpression(primitive.getAsString()) != null) {
				return false;
			}
		}

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
		JsonElement current = settings.get(key);
		if (current instanceof JsonPrimitive primitive && primitive.isString()) {
			if (ConfigVariableResolver.evaluateStringExpression(primitive.getAsString()) != null) {
				return false;
			}
		}

		double value = getDouble(settings, key, defaultValue);
		double clamped = Math.max(minInclusive, Math.min(maxInclusive, value));
		if (Double.compare(value, clamped) == 0 && settings.has(key)) {
			return false;
		}

		settings.addProperty(key, clamped);
		return true;
	}
}
