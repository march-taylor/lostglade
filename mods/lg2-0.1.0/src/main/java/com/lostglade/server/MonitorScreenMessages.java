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
			return Lg2Messages.tr("message.lg2.monitor.youtube_link_prompt");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return Lg2Messages.tr("message.lg2.monitor.youtube_music_link_prompt");
		}
		return Lg2Messages.tr("message.lg2.monitor.link_prompt");
	}

	static Component loadingMessage(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return Lg2Messages.tr("message.lg2.monitor.youtube_loading");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return Lg2Messages.tr("message.lg2.monitor.youtube_music_loading");
		}
		return Lg2Messages.tr("message.lg2.monitor.loading");
	}

	static Component mediaLoadedMessage(ServerPlayer player, boolean animated) {
		return Lg2Messages.tr(animated ? "message.lg2.monitor.media_loaded.gif" : "message.lg2.monitor.media_loaded.image");
	}

	static Component youtubeLoadedMessage(ServerPlayer player, boolean live) {
		return Lg2Messages.tr(live ? "message.lg2.monitor.youtube_loaded.live" : "message.lg2.monitor.youtube_loaded.video");
	}

	static Component youtubeMusicLoadedMessage(ServerPlayer player) {
		return Lg2Messages.tr("message.lg2.monitor.youtube_music_loaded");
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
		return Lg2Messages.tr("message.lg2.monitor.media_load_failed", reason);
	}

	static Component mediaCancelledMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return Lg2Messages.tr("message.lg2.monitor.media_cancelled.youtube");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return Lg2Messages.tr("message.lg2.monitor.media_cancelled.youtube_music");
		}
		return Lg2Messages.tr("message.lg2.monitor.media_cancelled.generic");
	}

	static Component mediaInvalidLinkMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return Lg2Messages.tr("message.lg2.monitor.invalid_link.youtube");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return Lg2Messages.tr("message.lg2.monitor.invalid_link.youtube_music");
		}
		return Lg2Messages.tr("message.lg2.monitor.invalid_link.generic");
	}

	static Component wallOnlyMessage(ServerPlayer player) {
		return Lg2Messages.tr("message.lg2.monitor.wall_only");
	}

	static Component occupiedMessage(ServerPlayer player) {
		return Lg2Messages.tr("message.lg2.monitor.occupied");
	}

	static Component galleryRenamePromptMessage(ServerPlayer player) {
		return Lg2Messages.tr("message.lg2.monitor.gallery.rename_prompt");
	}

	static Component galleryRenameNotFoundMessage(ServerPlayer player) {
		return Lg2Messages.tr("message.lg2.monitor.gallery.rename_not_found");
	}

	static Component galleryRenamedMessage(ServerPlayer player) {
		return Lg2Messages.tr("message.lg2.monitor.gallery.renamed");
	}

	static Component galleryRenameUnchangedMessage(ServerPlayer player) {
		return Lg2Messages.tr("message.lg2.monitor.gallery.rename_unchanged");
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
