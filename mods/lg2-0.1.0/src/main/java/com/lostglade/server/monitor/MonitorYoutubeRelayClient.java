package com.lostglade.server.monitor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lostglade.server.progress.TaskProgress;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class MonitorYoutubeRelayClient {
	private static final Gson GSON = new Gson();
	private static final String DEFAULT_RELAY_BASE_URL = "http://127.0.0.1:18888";
	private static final int CONNECT_TIMEOUT_MS = 2500;
	private static final int READ_TIMEOUT_MS = 15000;

	private MonitorYoutubeRelayClient() {
	}

	public static SessionLoadResponse load(String sessionId, String rawUrl, TaskProgress progress) throws IOException {
		validateYoutubeUrl(rawUrl);
		if (progress != null) {
			progress.setIndeterminate("CONNECTING");
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("sessionId", sessionId);
		payload.addProperty("url", rawUrl.trim());
		JsonObject response = postJson("/api/session/load", payload);
		if (progress != null) {
			progress.setIndeterminate("LOADING");
		}
		if (response.has("error") && !response.get("error").isJsonNull()) {
			throw new IOException(response.get("error").getAsString());
		}
		return new SessionLoadResponse(
				getString(response, "sessionId", sessionId),
				getString(response, "title", "YOUTUBE"),
				getLong(response, "durationMs", 0L),
				getBoolean(response, "live", false),
				getString(response, "status", "BUFFERING"),
				getString(response, "audioStreamUrl", "")
		);
	}

	public static SessionSnapshot snapshot(String sessionId, long knownFrameSequence) throws IOException {
		String encodedSession = URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
		JsonObject response = getJson("/api/session/snapshot?sessionId=" + encodedSession + "&knownFrameSequence=" + Math.max(-1L, knownFrameSequence));
		if (response.has("error") && !response.get("error").isJsonNull()) {
			throw new IOException(response.get("error").getAsString());
		}

		long frameSequence = getLong(response, "frameSequence", 0L);
		BufferedImage frame = null;
		String frameBase64 = getString(response, "frameBase64", "");
		if (frameSequence != knownFrameSequence && !frameBase64.isBlank()) {
			byte[] bytes = Base64.getDecoder().decode(frameBase64);
			frame = ImageIO.read(new ByteArrayInputStream(bytes));
		}

		return new SessionSnapshot(
				getString(response, "sessionId", sessionId),
				getString(response, "title", "YOUTUBE"),
				frame,
				frameSequence,
				getLong(response, "positionMs", 0L),
				getLong(response, "durationMs", 0L),
				getBoolean(response, "paused", false),
				getBoolean(response, "live", false),
				getBoolean(response, "audioPlaceholder", true),
				getBoolean(response, "ready", frame != null),
				getString(response, "status", "")
		);
	}

	public static void pause(String sessionId) throws IOException {
		control(sessionId, "pause", null);
	}

	public static void resume(String sessionId) throws IOException {
		control(sessionId, "resume", null);
	}

	public static void seek(String sessionId, long positionMs) throws IOException {
		control(sessionId, "seek", Math.max(0L, positionMs));
	}

	public static void close(String sessionId) throws IOException {
		control(sessionId, "close", null);
	}

	public static String relayBaseUrl() {
		String property = System.getProperty("lg2.youtubeRelayUrl");
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		String environment = System.getenv("LG2_YOUTUBE_RELAY_URL");
		if (environment != null && !environment.isBlank()) {
			return environment.trim();
		}
		return DEFAULT_RELAY_BASE_URL;
	}

	public static boolean looksLikeYoutubeUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return false;
		}
		try {
			URI uri = URI.create(rawUrl.trim());
			String scheme = uri.getScheme();
			if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
				return false;
			}
			String host = uri.getHost();
			if (host == null) {
				return false;
			}
			String normalizedHost = host.toLowerCase(Locale.ROOT);
			return normalizedHost.equals("youtu.be")
					|| normalizedHost.endsWith(".youtu.be")
					|| normalizedHost.equals("youtube.com")
					|| normalizedHost.endsWith(".youtube.com")
					|| normalizedHost.equals("youtube-nocookie.com")
					|| normalizedHost.endsWith(".youtube-nocookie.com");
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static void validateYoutubeUrl(String rawUrl) throws IOException {
		if (!looksLikeYoutubeUrl(rawUrl)) {
			throw new IOException("Only YouTube links are supported");
		}
	}

	private static void control(String sessionId, String action, Long positionMs) throws IOException {
		JsonObject payload = new JsonObject();
		payload.addProperty("sessionId", sessionId);
		payload.addProperty("action", action);
		if (positionMs != null) {
			payload.addProperty("positionMs", positionMs);
		}
		JsonObject response = postJson("/api/session/control", payload);
		if (response.has("error") && !response.get("error").isJsonNull()) {
			throw new IOException(response.get("error").getAsString());
		}
	}

	private static JsonObject getJson(String path) throws IOException {
		HttpURLConnection connection = openConnection(path, "GET");
		connection.connect();
		return readJsonResponse(connection);
	}

	private static JsonObject postJson(String path, JsonObject payload) throws IOException {
		HttpURLConnection connection = openConnection(path, "POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
			GSON.toJson(payload, writer);
		}
		return readJsonResponse(connection);
	}

	private static HttpURLConnection openConnection(String path, String method) throws IOException {
		URI uri = URI.create(relayBaseUrl() + path);
		HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
		connection.setRequestMethod(method);
		connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
		connection.setReadTimeout(READ_TIMEOUT_MS);
		connection.setRequestProperty("Accept", "application/json");
		connection.setRequestProperty("User-Agent", "LostGladeYoutubeRelay/1.0");
		return connection;
	}

	private static JsonObject readJsonResponse(HttpURLConnection connection) throws IOException {
		int status = connection.getResponseCode();
		InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
		if (stream == null) {
			throw new IOException("HTTP " + status);
		}
		try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				output.write(buffer, 0, read);
			}
			String json = output.toString(StandardCharsets.UTF_8);
			JsonObject object = GSON.fromJson(json, JsonObject.class);
			if (status < 200 || status >= 300) {
				String error = object != null && object.has("error") && !object.get("error").isJsonNull()
						? object.get("error").getAsString()
						: "HTTP " + status;
				throw new IOException(error);
			}
			if (object == null) {
				throw new IOException("Empty relay response");
			}
			return object;
		} finally {
			connection.disconnect();
		}
	}

	private static String getString(JsonObject object, String key, String fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
	}

	private static long getLong(JsonObject object, String key, long fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : fallback;
	}

	private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
		return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback;
	}

	public record SessionLoadResponse(
			String sessionId,
			String title,
			long durationMs,
			boolean live,
			String status,
			String audioStreamUrl
	) {
	}

	public record SessionSnapshot(
			String sessionId,
			String title,
			BufferedImage frame,
			long frameSequence,
			long positionMs,
			long durationMs,
			boolean paused,
			boolean live,
			boolean audioPlaceholder,
			boolean ready,
			String status
	) {
	}
}
