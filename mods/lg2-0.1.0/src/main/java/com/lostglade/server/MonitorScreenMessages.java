package com.lostglade.server;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

final class MonitorScreenMessages {
	private MonitorScreenMessages() {
	}

	static String linkPromptStatus(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return "ВСТАВЬ YOUTUBE В ЧАТ";
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return "ВСТАВЬ YT MUSIC В ЧАТ";
		}
		return "ВСТАВЬ ССЫЛКУ В ЧАТ";
	}

	static boolean isMediaPromptStatus(String status) {
		if (status == null) {
			return false;
		}
		return "ВСТАВЬ ССЫЛКУ В ЧАТ".equalsIgnoreCase(status)
				|| "ВСТАВЬ YOUTUBE В ЧАТ".equalsIgnoreCase(status)
				|| "ВСТАВЬ YT MUSIC В ЧАТ".equalsIgnoreCase(status)
				|| "ССЫЛКА В ЧАТ".equalsIgnoreCase(status)
				|| "SEND LINK IN CHAT".equalsIgnoreCase(status);
	}

	static boolean isMediaLoadingStatus(String status) {
		if (status == null) {
			return false;
		}
		return "ЗАГРУЖАЮ...".equalsIgnoreCase(status)
				|| "ПОДКЛЮЧАЮ YOUTUBE...".equalsIgnoreCase(status)
				|| "ПОДКЛЮЧАЮ YT MUSIC...".equalsIgnoreCase(status)
				|| "LOADING...".equalsIgnoreCase(status)
				|| "BUFFERING".equalsIgnoreCase(status)
				|| "LOADING".equalsIgnoreCase(status)
				|| "CONNECTING".equalsIgnoreCase(status);
	}

	static String loadingStatus(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return "ПОДКЛЮЧАЮ YOUTUBE...";
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return "ПОДКЛЮЧАЮ YT MUSIC...";
		}
		return "ЗАГРУЖАЮ...";
	}

	static String localizedProgressStage(String stage) {
		if (stage == null || stage.isBlank()) {
			return "ЗАГРУЗКА";
		}
		return switch (stage.toUpperCase(Locale.ROOT)) {
			case "CONNECTING" -> "ПОДКЛЮЧЕНИЕ";
			case "DOWNLOADING", "LOADING" -> "ЗАГРУЗКА";
			case "DECODING" -> "ОБРАБОТКА";
			case "READY" -> "ГОТОВО";
			case "RENDER ERROR" -> "ОШИБКА РЕНДЕРА";
			case "LOAD FAILED" -> "ОШИБКА ЗАГРУЗКИ";
			default -> stage;
		};
	}

	static String sanitizeMediaError(String error) {
		if (error == null || error.isBlank()) {
			return "LOAD FAILED";
		}
		String normalized = error.trim().replace('\n', ' ').replace('\r', ' ');
		if (normalized.length() > 28) {
			normalized = normalized.substring(0, 28).trim();
		}
		return normalized.toUpperCase(Locale.ROOT);
	}

	static Component linkPromptMessage(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Скинь в чат YouTube ссылку");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Скинь в чат YouTube или YouTube Music ссылку");
		}
		return literal("Скинь в чат ссылку на картинку, гифку, видео или музыку");
	}

	static Component loadingMessage(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Подключаю YouTube...");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Подключаю YouTube Music...");
		}
		return literal("Открываю медиа...");
	}

	static Component mediaLoadedMessage(ServerPlayer player, boolean animated) {
		return literal(animated ? "Гифка добавлена в галерею" : "Картинка добавлена в галерею");
	}

	static Component youtubeLoadedMessage(ServerPlayer player, boolean live) {
		return literal(live ? "YouTube стрим подключён" : "YouTube видео подключено");
	}

	static Component youtubeMusicLoadedMessage(ServerPlayer player) {
		return literal("YouTube Music трек подключён");
	}

	static Component youtubeQueueAddedMessage(ServerPlayer player, String title, int addedCount, boolean playlist) {
		String safeTitle = title == null || title.isBlank() ? "YouTube" : title;
		String locale = locale(player);
		if (locale.startsWith("ja")) {
			return literal((playlist ? "再生リスト" : "動画") + "を " + addedCount + " 件キューに追加: " + safeTitle);
		}
		if (locale.startsWith("uk")) {
			return literal("Додано в чергу " + addedCount + " " + (playlist ? "треків" : "відео") + ": " + safeTitle);
		}
		if (locale.startsWith("rpr")) {
			return literal("Въ очередь прибавлено " + addedCount + " " + (playlist ? "пѣсней" : "видѣво") + ": " + safeTitle);
		}
		if (locale.startsWith("ru")) {
			return literal("Добавлено в очередь " + addedCount + " " + (playlist ? "треков" : "видео") + ": " + safeTitle);
		}
		return literal("Queued " + addedCount + " " + (playlist ? "items" : "video") + ": " + safeTitle);
	}

	static Component mediaLoadFailedMessage(ServerPlayer player, String error) {
		String reason = error == null || error.isBlank() ? "LOAD FAILED" : error;
		return literal("Не удалось загрузить: " + reason);
	}

	static Component mediaCancelledMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Этот экран уже не ждёт YouTube ссылку");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Этот экран уже не ждёт YouTube Music ссылку");
		}
		return literal("Этот экран уже не ждёт ссылку");
	}

	static Component mediaInvalidLinkMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Нужна нормальная YouTube ссылка");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Нужна нормальная YouTube или YouTube Music ссылка");
		}
		return literal("Пустая ссылка не подходит");
	}

	static Component wallOnlyMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Экранъ ставится токмо на стену");
		}
		if (locale.startsWith("uk")) {
			return literal("Екран ставиться лише на стіну");
		}
		if (locale.startsWith("ja")) {
			return literal("モニターは壁にのみ設置できます");
		}
		if (locale.startsWith("ru")) {
			return literal("Экран ставится только на стену");
		}
		return literal("The monitor can only be placed on a wall");
	}

	static Component occupiedMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Тутъ уже занято");
		}
		if (locale.startsWith("uk")) {
			return literal("Тут уже зайнято");
		}
		if (locale.startsWith("ja")) {
			return literal("ここには設置できません");
		}
		if (locale.startsWith("ru")) {
			return literal("Тут уже занято");
		}
		return literal("That spot is already occupied");
	}

	static String locale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "en_us";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	static Component literal(String value) {
		return Component.literal(value).withStyle(style -> style.withItalic(false));
	}
}
