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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

	public static boolean isProcessRunning() {
		synchronized (LOCK) {
			return process != null && process.isAlive();
		}
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

		Path gradlew = resolveGradleLauncher(modDir);
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
		Path cameraCacheRoot = server.getServerDirectory().resolve("cache").resolve("lg2-camera").toAbsolutePath().normalize();
		terminateStaleBotProcesses(modDir, botName.trim(), serverAddress);
		List<String> command = new ArrayList<>();
		command.add(gradlew.toString());
		command.add("--console=plain");
		command.add("-Dlg2.rendererBotName=" + botName.trim());
		command.add("-Dlg2.rendererBotServer=" + serverAddress);
		command.add("-Dlg2.cameraMediaCacheRoot=" + cameraCacheRoot);
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

		terminateProcessTree(runningProcess.toHandle(), STOP_TIMEOUT_MS);
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
		return Files.isRegularFile(directory.resolve("build.gradle")) && Files.isRegularFile(resolveGradleLauncher(directory));
	}

	private static Path resolveGradleLauncher(Path directory) {
		if (isWindows()) {
			Path gradlewBat = directory.resolve("gradlew.bat");
			if (Files.isRegularFile(gradlewBat)) {
				return gradlewBat;
			}
		}
		return directory.resolve("gradlew");
	}

	private static boolean isWindows() {
		String osName = System.getProperty("os.name", "");
		return osName.toLowerCase().contains("win");
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static void terminateStaleBotProcesses(Path modDir, String botName, String serverAddress) {
		if (modDir == null || isBlank(botName) || isBlank(serverAddress)) {
			return;
		}
		long currentPid = ProcessHandle.current().pid();
		String modDirToken = modDir.toAbsolutePath().normalize().toString();
		String botNameToken = "-Dlg2.rendererBotName=" + botName;
		String serverAddressToken = "-Dlg2.rendererBotServer=" + serverAddress;
		List<ProcessHandle> staleProcesses = ProcessHandle.allProcesses()
				.filter(handle -> handle.pid() != currentPid)
				.filter(ProcessHandle::isAlive)
				.filter(handle -> matchesRendererBotProcess(handle, modDirToken, botNameToken, serverAddressToken))
				.sorted(Comparator.comparingLong(ProcessHandle::pid))
				.collect(Collectors.toList());
		if (staleProcesses.isEmpty()) {
			return;
		}
		Lg2.LOGGER.warn(
				"Stopping stale renderer bot process tree(s) for '{}' on {}: {}",
				botName,
				serverAddress,
				staleProcesses.stream().map(handle -> Long.toString(handle.pid())).collect(Collectors.joining(", "))
		);
		for (ProcessHandle handle : staleProcesses) {
			terminateProcessTree(handle, STOP_TIMEOUT_MS);
		}
	}

	private static boolean matchesRendererBotProcess(ProcessHandle handle, String modDirToken, String botNameToken, String serverAddressToken) {
		if (handle == null) {
			return false;
		}
		Optional<String> commandLine = handle.info().commandLine();
		if (commandLine.isEmpty()) {
			return false;
		}
		String line = commandLine.get();
		return line.contains(modDirToken)
				&& line.contains("runRendererBotClient")
				&& line.contains(botNameToken)
				&& line.contains(serverAddressToken);
	}

	private static void terminateProcessTree(ProcessHandle rootHandle, long timeoutMillis) {
		if (rootHandle == null) {
			return;
		}
		List<ProcessHandle> handles = new ArrayList<>(rootHandle.descendants()
				.sorted(Comparator.comparingLong(ProcessHandle::pid).reversed())
				.collect(Collectors.toList()));
		handles.add(rootHandle);
		for (ProcessHandle handle : handles) {
			if (handle.isAlive()) {
				handle.destroy();
			}
		}
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(250L, timeoutMillis));
		for (ProcessHandle handle : handles) {
			if (!handle.isAlive()) {
				continue;
			}
			long remainingMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
			try {
				handle.onExit().get(Math.max(1L, remainingMs), TimeUnit.MILLISECONDS);
			} catch (Exception ignored) {
				if (handle.isAlive()) {
					handle.destroyForcibly();
				}
			}
		}
	}
}
