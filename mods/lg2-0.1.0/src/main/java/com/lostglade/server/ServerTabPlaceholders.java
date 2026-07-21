package com.lostglade.server;

import me.neznamy.tab.shared.chat.component.TabComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ServerTabPlaceholders {
	private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
	private static final int DEFAULT_CHAR_ADVANCE = 6;
	private static final int SPACE_ADVANCE = 4;
	private static final int INFO_LINE_ADVANCE = 197;
	private static final int INFO_LINE_CENTER_SHIFT = 44;
	private static final int PING_ICON_ADVANCE = 9;
	private static final int TPS_ICON_ADVANCE = 9;
	private static final int TAB_LOGO_GLYPHS_BASE = 0xF100;
	private static final int TAB_LOGO_FRAME_COUNT = 48;
	private static final int TAB_LOGO_FRAME_TICKS = 2;
	private static final String INFO_LINE_SHIFT = buildAdvanceString(INFO_LINE_CENTER_SHIFT);
	private static final String VALUE_COLOR = "#b8e7c1";
	private static final String PING_LABEL = "Пинг: ";
	private static final String TPS_LABEL = "TPS: ";
	private static final Map<Character, Integer> TAB_TEXT_ADVANCES = createTabTextAdvances();

	private ServerTabPlaceholders() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(ServerTabPlaceholders::refreshAllHeaders);
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

		TabComponent header = TabComponent.fromColoredText(buildHeaderText(server, player));
		applyNoShadow(header);
		ServerTabIntegration.setHeaderFooter(player, header, TabComponent.empty());
	}

	private static String buildHeaderText(MinecraftServer server, ServerPlayer player) {
		String dateTime = toSmallFont(DATE_TIME_FORMATTER.format(ZonedDateTime.now(MOSCOW_ZONE)));
		String pingValue = Math.max(0, player.connection.latency()) + " мс";
		String tpsValue = formatTps(server);
		return "\n"
				+ "§f" + tabLogoGlyph(server) + "\n"
				+ "§7" + dateTime + "\n\n"
				+ buildInfoLine('\uED81', PING_LABEL, pingValue, PING_ICON_ADVANCE) + "\n"
				+ buildInfoLine('\uED82', TPS_LABEL, tpsValue, TPS_ICON_ADVANCE);
	}

	private static String tabLogoGlyph(MinecraftServer server) {
		long tick = server == null ? 0L : server.getTickCount();
		int frame = (int) Math.floorMod(tick / TAB_LOGO_FRAME_TICKS, TAB_LOGO_FRAME_COUNT);
		return String.valueOf((char) (TAB_LOGO_GLYPHS_BASE + frame));
	}

	private static String buildInfoLine(char icon, String label, String value, int iconAdvance) {
		return INFO_LINE_SHIFT
				+ "§a" + icon
				+ " §7" + label
				+ VALUE_COLOR + value
				+ buildInfoFill(iconAdvance, label, value);
	}

	private static String formatTps(MinecraftServer server) {
		double mspt = Math.max(0.0D, server.getAverageTickTimeNanos() / 1_000_000.0D);
		double tps = mspt <= 0.0D ? 20.0D : Math.min(20.0D, 1000.0D / mspt);
		if (Math.abs(tps - Math.rint(tps)) < 0.05D) {
			return Long.toString(Math.round(tps));
		}
		return String.format(Locale.ROOT, "%.1f", tps);
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

	private static String buildInfoFill(int iconAdvance, String label, String value) {
		int contentAdvance = iconAdvance + SPACE_ADVANCE + measureTabTextWidth(label) + measureTabTextWidth(value);
		return buildAdvanceString(Math.max(0, INFO_LINE_ADVANCE - INFO_LINE_CENTER_SHIFT - contentAdvance));
	}

	private static int measureTabTextWidth(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}

		int width = 0;
		for (int index = 0; index < text.length(); index++) {
			width += TAB_TEXT_ADVANCES.getOrDefault(text.charAt(index), DEFAULT_CHAR_ADVANCE);
		}
		return width;
	}

	private static String buildAdvanceString(int advance) {
		if (advance == 0) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		int remaining = Math.abs(advance);
		int[] steps = {64, 32, 16, 8, 4, 2, 1};
		char[] positiveChars = {'\uE94D', '\uE94C', '\uE94B', '\uE94A', '\uE949', '\uE948', '\uE947'};
		char[] negativeChars = {'\uE940', '\uE941', '\uE942', '\uE943', '\uE944', '\uE945', '\uE946'};
		char[] chars = advance > 0 ? positiveChars : negativeChars;
		for (int index = 0; index < steps.length; index++) {
			while (remaining >= steps[index]) {
				builder.append(chars[index]);
				remaining -= steps[index];
			}
		}
		return builder.toString();
	}

	private static Map<Character, Integer> createTabTextAdvances() {
		Map<Character, Integer> advances = new HashMap<>();

		putChars(advances, "ABCDEFGHJKLMNOPQRSTUVWXYZ", 6);
		advances.put('I', 4);

		putChars(advances, "abcdeghjmnopqrsuvwxyz", 6);
		advances.put('f', 5);
		advances.put('i', 2);
		advances.put('k', 5);
		advances.put('l', 3);
		advances.put('t', 4);
		advances.put('_', 6);
		putChars(advances, "0123456789", 6);

		advances.put(' ', SPACE_ADVANCE);
		advances.put(':', 2);
		advances.put('.', 2);

		putChars(advances, "ПингмсTPS", 6);
		advances.put('г', 5);

		return advances;
	}

	private static void putChars(Map<Character, Integer> advances, String characters, int advance) {
		for (int index = 0; index < characters.length(); index++) {
			advances.put(characters.charAt(index), advance);
		}
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
