package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.Lg2Config;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class RendererBotProcessSystem {
	private static final Object LOCK = new Object();
	private static final long STOP_TIMEOUT_MS = 5_000L;
	private static Process process;
	private static boolean stopping;

	private RendererBotProcessSystem() {
	}

	public static void preflightServerProperties() {
		Path propertiesPath = FabricLoader.getInstance().getGameDir().resolve("server.properties");
		if (!Files.isRegularFile(propertiesPath)) {
			return;
		}

		List<String> lines;
		try {
			lines = Files.readAllLines(propertiesPath);
		} catch (IOException e) {
			Lg2.LOGGER.warn("Failed to read {} during renderer bot preflight", propertiesPath, e);
			return;
		}

		int secureProfileIndex = -1;
		Boolean onlineMode = null;
		Boolean enforceSecureProfile = null;
		for (int i = 0; i < lines.size(); i++) {
			String trimmed = lines.get(i).trim();
			if (trimmed.startsWith("online-mode=")) {
				onlineMode = Boolean.parseBoolean(trimmed.substring("online-mode=".length()));
			} else if (trimmed.startsWith("enforce-secure-profile=")) {
				secureProfileIndex = i;
				enforceSecureProfile = Boolean.parseBoolean(trimmed.substring("enforce-secure-profile=".length()));
			}
		}

		String botName = Lg2Config.get().cameraRendererBotPlayerName;
		if (onlineMode != null && onlineMode && botName != null && !botName.isBlank()) {
			Lg2.LOGGER.warn("Renderer bot '{}' is configured while online-mode=true; the dev bot needs an authenticated account in that setup", botName.trim());
		}

		if (onlineMode == null || onlineMode) {
			return;
		}
		if (Boolean.FALSE.equals(enforceSecureProfile)) {
			return;
		}

		if (secureProfileIndex >= 0) {
			lines.set(secureProfileIndex, "enforce-secure-profile=false");
		} else {
			lines.add("enforce-secure-profile=false");
		}

		try {
			Files.write(propertiesPath, lines);
			Lg2.LOGGER.info("Adjusted {}: set enforce-secure-profile=false because online-mode=false", propertiesPath);
		} catch (IOException e) {
			Lg2.LOGGER.warn("Failed to update {} for renderer bot/offline client compatibility", propertiesPath, e);
		}
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(RendererBotProcessSystem::startIfConfigured);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> stopBot());
	}

	private static void startIfConfigured(MinecraftServer server) {
		if (!FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return;
		}

		String botName = Lg2Config.get().cameraRendererBotPlayerName;
		if (botName == null || botName.isBlank()) {
			return;
		}

		synchronized (LOCK) {
			if (process != null && process.isAlive()) {
				return;
			}
			stopping = false;
		}

		Path modDir = locateModProjectDir(server.getServerDirectory());
		if (modDir == null) {
			Lg2.LOGGER.warn("Renderer bot autostart skipped: could not locate lg2 mod project directory from {}", server.getServerDirectory());
			return;
		}

		Path gradlew = modDir.resolve("gradlew");
		if (!Files.isRegularFile(gradlew)) {
			Lg2.LOGGER.warn("Renderer bot autostart skipped: {} is missing", gradlew);
			return;
		}

		Path logFile = server.getServerDirectory().resolve("logs").resolve("renderer-bot.log");
		try {
			Files.createDirectories(logFile.getParent());
		} catch (IOException e) {
			Lg2.LOGGER.warn("Renderer bot autostart skipped: failed to prepare {}", logFile, e);
			return;
		}

		boolean headlessRequested = isBlank(System.getenv("DISPLAY")) && isBlank(System.getenv("WAYLAND_DISPLAY"));
		String serverAddress = "127.0.0.1:" + Math.max(1, server.getPort());
		List<String> command = new ArrayList<>();
		command.add(gradlew.toString());
		command.add("--console=plain");
		command.add("-Dlg2.rendererBotName=" + botName.trim());
		command.add("-Dlg2.rendererBotServer=" + serverAddress);
		if (headlessRequested) {
			command.add("-Dlg2.rendererBotHeadless=true");
		}
		command.add("runRendererBotClient");
		command.add("-x");
		command.add("jar");
		command.add("-x");
		command.add("remapJar");

		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(modDir.toFile());
		builder.redirectErrorStream(true);
		builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

		Map<String, String> environment = builder.environment();
		if (headlessRequested) {
			environment.remove("DISPLAY");
			environment.remove("WAYLAND_DISPLAY");
			environment.remove("XDG_SESSION_TYPE");
			environment.put("LIBGL_ALWAYS_SOFTWARE", "1");
			environment.put("MESA_LOADER_DRIVER_OVERRIDE", "llvmpipe");
		}

		try {
			Process started = builder.start();
			synchronized (LOCK) {
				process = started;
			}
			startExitWatcher(started, botName.trim(), logFile);
			Lg2.LOGGER.info(
					"Started renderer bot '{}' for {}{}; logs: {}",
					botName.trim(),
					serverAddress,
					headlessRequested ? " (headless backend requested)" : "",
					logFile
			);
		} catch (IOException e) {
			Lg2.LOGGER.error("Failed to start renderer bot process", e);
		}
	}

	private static void startExitWatcher(Process watchedProcess, String botName, Path logFile) {
		Thread watcher = new Thread(() -> {
			int exitCode;
			try {
				exitCode = watchedProcess.waitFor();
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				return;
			}

			synchronized (LOCK) {
				if (process == watchedProcess) {
					process = null;
				}
				if (stopping) {
					return;
				}
			}

			Lg2.LOGGER.warn("Renderer bot '{}' exited with code {}. See {}", botName, exitCode, logFile);
		}, "lg2-renderer-bot-process-watch");
		watcher.setDaemon(true);
		watcher.start();
	}

	private static void stopBot() {
		Process runningProcess;
		synchronized (LOCK) {
			stopping = true;
			runningProcess = process;
			process = null;
		}

		if (runningProcess == null) {
			return;
		}

		runningProcess.destroy();
		try {
			if (!runningProcess.waitFor(STOP_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
				runningProcess.destroyForcibly();
			}
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			runningProcess.destroyForcibly();
		}
	}

	private static Path locateModProjectDir(Path serverDir) {
		Path gameDir = serverDir.toAbsolutePath().normalize();
		Path direct = gameDir.resolve("mods").resolve("lg2-0.1.0");
		if (looksLikeModProjectDir(direct)) {
			return direct;
		}

		Path cwd = Path.of("").toAbsolutePath().normalize();
		if (looksLikeModProjectDir(cwd)) {
			return cwd;
		}

		return null;
	}

	private static boolean looksLikeModProjectDir(Path directory) {
		return Files.isRegularFile(directory.resolve("build.gradle")) && Files.isRegularFile(directory.resolve("gradlew"));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
