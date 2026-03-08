package com.lostglade.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.lostglade.Lg2;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ConfigVariableResolver {
	private static final String WHITELIST_COUNT_TOKEN = "whitelistCount";
	private static final Path WHITELIST_PATH = FabricLoader.getInstance().getGameDir().resolve("whitelist.json");
	private static final Pattern TOKEN_WRAPPED = Pattern.compile("(?i)\\$\\{\\s*whitelistcount\\s*\\}");
	private static final Pattern TOKEN_DOLLAR = Pattern.compile("(?i)\\$whitelistcount");
	private static final Pattern TOKEN_PLAIN = Pattern.compile("(?i)\\bwhitelistcount\\b");

	private ConfigVariableResolver() {
	}

	public static <T> T fromJsonWithVariables(Gson gson, Reader reader, Class<T> type) {
		JsonElement root = JsonParser.parseReader(reader);
		int whitelistCount = readWhitelistCount();
		resolveVariablesInPlace(root, whitelistCount);
		return gson.fromJson(root, type);
	}

	public static Double evaluateStringExpression(String rawValue) {
		if (rawValue == null) {
			return null;
		}

		String value = rawValue.trim();
		if (value.isEmpty()) {
			return null;
		}

		if (isWhitelistToken(value)) {
			return (double) readWhitelistCount();
		}

		if (!containsWhitelistToken(value)) {
			try {
				double parsed = Double.parseDouble(value);
				return Double.isFinite(parsed) ? parsed : null;
			} catch (Exception ignored) {
				return null;
			}
		}

		int whitelistCount = readWhitelistCount();
		String expression = replaceWhitelistToken(value, whitelistCount);
		try {
			double evaluated = ExpressionParser.evaluate(expression);
			return Double.isFinite(evaluated) ? evaluated : null;
		} catch (Exception e) {
			Lg2.LOGGER.warn("Failed to evaluate config expression '{}'", rawValue, e);
			return null;
		}
	}

	private static void resolveVariablesInPlace(JsonElement element, int whitelistCount) {
		if (element == null || element.isJsonNull()) {
			return;
		}

		if (element.isJsonObject()) {
			JsonObject object = element.getAsJsonObject();
			for (String key : object.keySet()) {
				JsonElement child = object.get(key);
				JsonPrimitive resolved = resolvePrimitive(child, whitelistCount);
				if (resolved != null) {
					object.add(key, resolved);
					continue;
				}
				resolveVariablesInPlace(child, whitelistCount);
			}
			return;
		}

		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				JsonElement child = array.get(i);
				JsonPrimitive resolved = resolvePrimitive(child, whitelistCount);
				if (resolved != null) {
					array.set(i, resolved);
					continue;
				}
				resolveVariablesInPlace(child, whitelistCount);
			}
		}
	}

	private static JsonPrimitive resolvePrimitive(JsonElement element, int whitelistCount) {
		if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
			return null;
		}

		String value = primitive.getAsString().trim();
		if (isWhitelistToken(value)) {
			return new JsonPrimitive(whitelistCount);
		}

		if (containsWhitelistToken(value)) {
			String expression = replaceWhitelistToken(value, whitelistCount);
			try {
				double evaluated = ExpressionParser.evaluate(expression);
				if (!Double.isFinite(evaluated)) {
					return null;
				}

				if (isWholeNumber(evaluated)) {
					return new JsonPrimitive((long) Math.round(evaluated));
				}
				return new JsonPrimitive(evaluated);
			} catch (Exception e) {
				Lg2.LOGGER.warn("Failed to evaluate config expression '{}'", value, e);
			}
		}

		return null;
	}

	private static boolean isWhitelistToken(String value) {
		return value.equalsIgnoreCase(WHITELIST_COUNT_TOKEN)
				|| value.equalsIgnoreCase("$" + WHITELIST_COUNT_TOKEN)
				|| value.equalsIgnoreCase("${" + WHITELIST_COUNT_TOKEN + "}");
	}

	private static boolean containsWhitelistToken(String value) {
		String lower = value.toLowerCase(Locale.ROOT);
		return lower.contains(WHITELIST_COUNT_TOKEN.toLowerCase(Locale.ROOT));
	}

	private static String replaceWhitelistToken(String expression, int whitelistCount) {
		String value = String.valueOf(whitelistCount);
		String replaced = TOKEN_WRAPPED.matcher(expression).replaceAll(value);
		replaced = TOKEN_DOLLAR.matcher(replaced).replaceAll(value);
		replaced = TOKEN_PLAIN.matcher(replaced).replaceAll(value);
		return replaced;
	}

	private static boolean isWholeNumber(double value) {
		return Math.abs(value - Math.rint(value)) <= 1.0E-9D;
	}

	private static int readWhitelistCount() {
		if (!Files.exists(WHITELIST_PATH)) {
			return 0;
		}

		try (Reader reader = Files.newBufferedReader(WHITELIST_PATH)) {
			JsonElement root = JsonParser.parseReader(reader);
			if (!(root instanceof JsonArray array)) {
				return 0;
			}
			return array.size();
		} catch (Exception e) {
			Lg2.LOGGER.warn("Failed to read whitelist count from {}", WHITELIST_PATH, e);
			return 0;
		}
	}

	private static final class ExpressionParser {
		private final String source;
		private int index;

		private ExpressionParser(String source) {
			this.source = source;
			this.index = 0;
		}

		static double evaluate(String expression) {
			ExpressionParser parser = new ExpressionParser(expression);
			double value = parser.parseExpression();
			parser.skipWhitespace();
			if (!parser.isEnd()) {
				throw new IllegalArgumentException("Unexpected token at position " + parser.index);
			}
			return value;
		}

		private double parseExpression() {
			double value = parseTerm();
			while (true) {
				skipWhitespace();
				if (match('+')) {
					value += parseTerm();
					continue;
				}
				if (match('-')) {
					value -= parseTerm();
					continue;
				}
				return value;
			}
		}

		private double parseTerm() {
			double value = parseUnary();
			while (true) {
				skipWhitespace();
				if (match('*')) {
					value *= parseUnary();
					continue;
				}
				if (match('/')) {
					value /= parseUnary();
					continue;
				}
				return value;
			}
		}

		private double parseUnary() {
			skipWhitespace();
			if (match('+')) {
				return parseUnary();
			}
			if (match('-')) {
				return -parseUnary();
			}
			return parsePrimary();
		}

		private double parsePrimary() {
			skipWhitespace();
			if (match('(')) {
				double value = parseExpression();
				expect(')');
				return value;
			}

			if (isIdentifierStart(peek())) {
				String function = parseIdentifier().toLowerCase(Locale.ROOT);
				skipWhitespace();
				expect('(');
				double argument = parseExpression();
				expect(')');
				return applyFunction(function, argument);
			}

			return parseNumber();
		}

		private double parseNumber() {
			skipWhitespace();
			int start = index;
			boolean dotUsed = false;

			while (!isEnd()) {
				char current = source.charAt(index);
				if (Character.isDigit(current)) {
					index++;
					continue;
				}
				if (current == '.' && !dotUsed) {
					dotUsed = true;
					index++;
					continue;
				}
				break;
			}

			if (start == index) {
				throw new IllegalArgumentException("Expected number at position " + index);
			}

			return Double.parseDouble(source.substring(start, index));
		}

		private String parseIdentifier() {
			int start = index;
			while (!isEnd() && isIdentifierPart(source.charAt(index))) {
				index++;
			}
			if (start == index) {
				throw new IllegalArgumentException("Expected identifier at position " + index);
			}
			return source.substring(start, index);
		}

		private double applyFunction(String function, double argument) {
			return switch (function) {
				case "round" -> Math.round(argument);
				case "floor" -> Math.floor(argument);
				case "ceil", "ceiling" -> Math.ceil(argument);
				default -> throw new IllegalArgumentException("Unsupported function: " + function);
			};
		}

		private boolean match(char expected) {
			if (isEnd() || source.charAt(index) != expected) {
				return false;
			}
			index++;
			return true;
		}

		private void expect(char expected) {
			if (!match(expected)) {
				throw new IllegalArgumentException("Expected '" + expected + "' at position " + index);
			}
		}

		private void skipWhitespace() {
			while (!isEnd() && Character.isWhitespace(source.charAt(index))) {
				index++;
			}
		}

		private char peek() {
			return isEnd() ? '\0' : source.charAt(index);
		}

		private boolean isEnd() {
			return index >= source.length();
		}

		private static boolean isIdentifierStart(char value) {
			return Character.isLetter(value) || value == '_';
		}

		private static boolean isIdentifierPart(char value) {
			return Character.isLetterOrDigit(value) || value == '_';
		}
	}
}
