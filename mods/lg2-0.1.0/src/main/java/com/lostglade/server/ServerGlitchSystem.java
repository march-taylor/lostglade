package com.lostglade.server;

import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.config.GlitchConfig;
import com.lostglade.server.glitch.PhantomSoundGlitch;
import com.lostglade.server.glitch.ServerGlitchHandler;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ServerGlitchSystem {
	private static final Map<String, ServerGlitchHandler> HANDLERS = new LinkedHashMap<>();
	private static final Map<String, Long> NEXT_ALLOWED_TICKS = new HashMap<>();
	private static final Set<String> UNKNOWN_CONFIG_IDS_LOGGED = new HashSet<>();

	private static long checkTicker = 0L;

	private ServerGlitchSystem() {
	}

	public static void register() {
		HANDLERS.clear();
		NEXT_ALLOWED_TICKS.clear();
		UNKNOWN_CONFIG_IDS_LOGGED.clear();
		checkTicker = 0L;

		// Add new glitch types here: implement ServerGlitchHandler and register once.
		registerHandler(new PhantomSoundGlitch());
		reloadConfig();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			checkTicker = 0L;
			NEXT_ALLOWED_TICKS.clear();
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						Commands.literal("serverglitch")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.literal("reload")
										.executes(context -> {
											reloadConfig();
											context.getSource().sendSuccess(
													() -> Component.literal("Reloaded glitch config: " + HANDLERS.size() + " registered glitches"),
													true
											);
											return 1;
										}))
				)
		);

		ServerTickEvents.END_SERVER_TICK.register(ServerGlitchSystem::tick);
	}

	private static void registerHandler(ServerGlitchHandler handler) {
		ServerGlitchHandler previous = HANDLERS.put(handler.id(), handler);
		if (previous != null) {
			Lg2.LOGGER.warn("Duplicate glitch handler id '{}': {} replaced by {}",
					handler.id(), previous.getClass().getName(), handler.getClass().getName());
		}
	}

	private static void reloadConfig() {
		GlitchConfig.load(buildDefaultEntries());
		boolean changed = sanitizeHandlerSpecificSettings();
		if (changed) {
			GlitchConfig.save();
		}
	}

	private static Map<String, GlitchConfig.GlitchEntry> buildDefaultEntries() {
		Map<String, GlitchConfig.GlitchEntry> defaults = new LinkedHashMap<>();
		for (Map.Entry<String, ServerGlitchHandler> entry : HANDLERS.entrySet()) {
			defaults.put(entry.getKey(), copyEntry(entry.getValue().defaultEntry()));
		}
		return defaults;
	}

	private static boolean sanitizeHandlerSpecificSettings() {
		GlitchConfig.ConfigData config = GlitchConfig.get();
		if (config.glitches == null) {
			config.glitches = new LinkedHashMap<>();
		}

		boolean changed = false;
		for (Map.Entry<String, ServerGlitchHandler> handlerEntry : HANDLERS.entrySet()) {
			String id = handlerEntry.getKey();
			ServerGlitchHandler handler = handlerEntry.getValue();

			GlitchConfig.GlitchEntry entry = config.glitches.get(id);
			if (entry == null) {
				config.glitches.put(id, copyEntry(handler.defaultEntry()));
				changed = true;
				continue;
			}

			changed |= handler.sanitizeSettings(entry);
		}

		return changed;
	}

	private static void tick(MinecraftServer server) {
		GlitchConfig.ConfigData config = GlitchConfig.get();
		if (!config.enabled) {
			return;
		}

		if (server.getPlayerList().getPlayerCount() <= 0) {
			return;
		}

		checkTicker++;
		int interval = Math.max(1, config.checkIntervalTicks);
		if (checkTicker < interval) {
			return;
		}
		checkTicker = 0L;

		triggerGlitches(server, config);
	}

	private static void triggerGlitches(MinecraftServer server, GlitchConfig.ConfigData config) {
		if (config.glitches == null || config.glitches.isEmpty()) {
			return;
		}

		double stabilityPercent = ServerStabilitySystem.getStabilityPercent();
		RandomSource random = server.overworld().getRandom();
		long gameTime = server.overworld().getGameTime();
		int remainingActivations = Math.max(1, config.maxActivationsPerCheck);

		List<Map.Entry<String, GlitchConfig.GlitchEntry>> entries = new ArrayList<>(config.glitches.entrySet());
		shuffle(entries, random);

		for (Map.Entry<String, GlitchConfig.GlitchEntry> mapEntry : entries) {
			if (remainingActivations <= 0) {
				break;
			}

			String id = mapEntry.getKey();
			GlitchConfig.GlitchEntry entry = mapEntry.getValue();
			ServerGlitchHandler handler = HANDLERS.get(id);

			if (handler == null) {
				logUnknownGlitchId(id);
				continue;
			}

			if (entry == null || !entry.enabled) {
				continue;
			}

			if (stabilityPercent < entry.minStabilityPercent || stabilityPercent > entry.maxStabilityPercent) {
				continue;
			}

			long nextAllowedTick = NEXT_ALLOWED_TICKS.getOrDefault(id, Long.MIN_VALUE);
			if (gameTime < nextAllowedTick) {
				continue;
			}

			double baseChance = clamp01(entry.chancePerCheck);
			double influence = clamp01(entry.stabilityInfluence);
			double rangeInstabilityFactor = getRangeInstabilityFactor(
					stabilityPercent,
					entry.minStabilityPercent,
					entry.maxStabilityPercent
			);
			double effectiveChance = baseChance * ((1.0D - influence) + (rangeInstabilityFactor * influence));
			if (effectiveChance <= 0.0D) {
				continue;
			}

			if (random.nextDouble() > effectiveChance) {
				continue;
			}

			boolean triggered = handler.trigger(server, random, entry, stabilityPercent);
			if (!triggered) {
				continue;
			}

			long cooldownTicks = Math.max(
					0L,
					getCooldownTicksForStability(entry, stabilityPercent)
			);
			NEXT_ALLOWED_TICKS.put(id, gameTime + cooldownTicks);
			remainingActivations--;
		}
	}

	private static void shuffle(List<Map.Entry<String, GlitchConfig.GlitchEntry>> list, RandomSource random) {
		for (int i = list.size() - 1; i > 0; i--) {
			int swapWith = random.nextInt(i + 1);
			Collections.swap(list, i, swapWith);
		}
	}

	private static void logUnknownGlitchId(String id) {
		if (!UNKNOWN_CONFIG_IDS_LOGGED.add(id)) {
			return;
		}

		Lg2.LOGGER.warn("Unknown glitch id '{}' in lg2-glitches config. Entry will be ignored.", id);
	}

	private static GlitchConfig.GlitchEntry copyEntry(GlitchConfig.GlitchEntry source) {
		GlitchConfig.GlitchEntry copy = new GlitchConfig.GlitchEntry();
		copy.enabled = source.enabled;
		copy.minStabilityPercent = source.minStabilityPercent;
		copy.maxStabilityPercent = source.maxStabilityPercent;
		copy.chancePerCheck = source.chancePerCheck;
		copy.stabilityInfluence = source.stabilityInfluence;
		copy.minCooldownTicks = source.minCooldownTicks;
		copy.maxCooldownTicks = source.maxCooldownTicks;
		copy.cooldownTicks = source.cooldownTicks;
		copy.settings = source.settings == null ? new JsonObject() : source.settings.deepCopy();
		return copy;
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	private static double getRangeInstabilityFactor(double stabilityPercent, double minStabilityPercent, double maxStabilityPercent) {
		double range = maxStabilityPercent - minStabilityPercent;
		if (range <= 1.0E-9D) {
			return 1.0D;
		}

		double normalized = (stabilityPercent - minStabilityPercent) / range;
		return clamp01(1.0D - normalized);
	}

	private static int getCooldownTicksForStability(GlitchConfig.GlitchEntry entry, double stabilityPercent) {
		int minCooldown = entry.minCooldownTicks == null ? 0 : Math.max(0, entry.minCooldownTicks);
		int maxCooldown = entry.maxCooldownTicks == null ? minCooldown : Math.max(0, entry.maxCooldownTicks);
		if (maxCooldown < minCooldown) {
			maxCooldown = minCooldown;
		}

		double startStability = Math.max(0.0D, entry.maxStabilityPercent);
		if (startStability <= 1.0E-9D) {
			return minCooldown;
		}

		double factor = clamp01(stabilityPercent / startStability);
		double cooldown = minCooldown + ((double) (maxCooldown - minCooldown) * factor);
		return (int) Math.round(cooldown);
	}
}
