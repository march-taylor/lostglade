package com.lostglade.server;

import me.neznamy.tab.shared.chat.component.TabComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class ServerTabPlaceholders {
	private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
	private static final int TAB_LOGO_GLYPHS_BASE = 0xF100;
	private static final int TAB_LOGO_FRAME_COUNT = 48;
	private static final int TAB_LOGO_FRAME_TICKS = 2;
	private static final int TPS_SAMPLE_WINDOW = 120;
	private static final long MAX_TPS_SAMPLE_NANOS = 5_000_000_000L;
	private static final long[] TICK_INTERVAL_SAMPLES_NANOS = new long[TPS_SAMPLE_WINDOW];
	private static int tickIntervalSampleCount;
	private static int nextTickIntervalSample;
	private static long tickIntervalSampleTotalNanos;
	private static long previousTickStartNanos = Long.MIN_VALUE;

	private ServerTabPlaceholders() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			resetTpsMeasurement();
			refreshAllHeaders(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> resetTpsMeasurement());
		ServerTickEvents.START_SERVER_TICK.register(server -> recordTickStart());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if ((server.getTickCount() % TAB_LOGO_FRAME_TICKS) == 0) {
				refreshAllHeaders(server);
			}
		});
		ServerTabIntegration.registerPlayerLoadHandler(player -> {
			MinecraftServer server = player == null || player.level() == null ? null : player.level().getServer();
			if (server != null) {
				refreshHeader(server, player);
			}
		});
	}

	private static void refreshAllHeaders(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshHeader(server, player);
		}
	}

	private static void refreshHeader(MinecraftServer server, ServerPlayer player) {
		if (server == null || player == null) {
			return;
		}

		TabComponent header = TabComponent.fromColoredText(buildHeaderText(server));
		TabComponent footer = TabComponent.fromColoredText(buildFooterText(server, player));
		applyNoShadow(header);
		applyNoShadow(footer);
		ServerTabIntegration.setHeaderFooter(player, header, footer);
	}

	private static String buildHeaderText(MinecraftServer server) {
		String dateTime = toSmallFont(DATE_TIME_FORMATTER.format(ZonedDateTime.now(MOSCOW_ZONE)));
		return "\n"
				+ "§f" + tabLogoGlyph(server) + "\n"
				+ "§7" + dateTime;
	}

	private static String buildFooterText(MinecraftServer server, ServerPlayer player) {
		String pingValue = formatPing(player);
		String tpsValue = formatTps();
		return "\n§a\uED81 §7" + pingValue
				+ " §8• §a\uED82 §7" + tpsValue;
	}

	private static String tabLogoGlyph(MinecraftServer server) {
		long tick = server == null ? 0L : server.getTickCount();
		int frame = (int) Math.floorMod(tick / TAB_LOGO_FRAME_TICKS, TAB_LOGO_FRAME_COUNT);
		return String.valueOf((char) (TAB_LOGO_GLYPHS_BASE + frame));
	}

	private static String formatPing(ServerPlayer player) {
		if (player == null || player.connection == null) {
			return "-";
		}
		int latencyMillis = player.connection.latency();
		return latencyMillis < 0 ? "-" : latencyMillis + " мс";
	}

	private static String formatTps() {
		if (tickIntervalSampleCount == 0 || tickIntervalSampleTotalNanos <= 0L) {
			return "-";
		}
		double averageTickIntervalNanos = tickIntervalSampleTotalNanos / (double) tickIntervalSampleCount;
		double tps = Math.min(20.0D, 1_000_000_000.0D / averageTickIntervalNanos);
		if (Math.abs(tps - Math.rint(tps)) < 0.05D) {
			return Long.toString(Math.round(tps));
		}
		return String.format(Locale.ROOT, "%.1f", tps);
	}

	/**
	 * Measures the period between actual server tick starts. Unlike the vanilla average
	 * tick-work-time metric, this includes the scheduler delay between ticks and therefore
	 * reports the TPS players are actually receiving.
	 */
	private static void recordTickStart() {
		long now = System.nanoTime();
		long previous = previousTickStartNanos;
		previousTickStartNanos = now;
		if (previous == Long.MIN_VALUE) {
			return;
		}

		long elapsedNanos = now - previous;
		if (elapsedNanos <= 0L || elapsedNanos > MAX_TPS_SAMPLE_NANOS) {
			resetTpsSamples();
			return;
		}

		if (tickIntervalSampleCount == TPS_SAMPLE_WINDOW) {
			tickIntervalSampleTotalNanos -= TICK_INTERVAL_SAMPLES_NANOS[nextTickIntervalSample];
		} else {
			tickIntervalSampleCount++;
		}
		TICK_INTERVAL_SAMPLES_NANOS[nextTickIntervalSample] = elapsedNanos;
		tickIntervalSampleTotalNanos += elapsedNanos;
		nextTickIntervalSample = (nextTickIntervalSample + 1) % TPS_SAMPLE_WINDOW;
	}

	private static void resetTpsMeasurement() {
		previousTickStartNanos = Long.MIN_VALUE;
		resetTpsSamples();
	}

	private static void resetTpsSamples() {
		tickIntervalSampleCount = 0;
		nextTickIntervalSample = 0;
		tickIntervalSampleTotalNanos = 0L;
	}

	private static void applyNoShadow(TabComponent component) {
		if (component == null) {
			return;
		}
		component.getModifier().setShadowColor(0x00000000);
		for (TabComponent extra : component.getExtra()) {
			applyNoShadow(extra);
		}
	}

	private static String toSmallFont(String value) {
		StringBuilder builder = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			builder.append(mapSmallFontChar(value.charAt(index)));
		}
		return builder.toString();
	}

	private static char mapSmallFontChar(char character) {
		return switch (character) {
			case '0' -> '\uED90';
			case '1' -> '\uED91';
			case '2' -> '\uED92';
			case '3' -> '\uED93';
			case '4' -> '\uED94';
			case '5' -> '\uED95';
			case '6' -> '\uED96';
			case '7' -> '\uED97';
			case '8' -> '\uED98';
			case '9' -> '\uED99';
			case '.' -> '\uED9A';
			case ':' -> '\uED9B';
			case '/' -> '\uED9C';
			case '-' -> '\uED9D';
			default -> character;
		};
	}
}
