package com.lostglade.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SeasonStartConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("lg2-season-start.json");
	private static final Path VOICE_ROOT = FabricLoader.getInstance().getConfigDir().resolve("lg2-season-start");

	private static ConfigData data = ConfigData.defaults();

	private SeasonStartConfig() {
	}

	public static synchronized void load() {
		ConfigData loaded = readOrCreate();
		if (sanitize(loaded)) {
			write(loaded);
		}
		data = loaded;
	}

	public static ConfigData get() {
		return data;
	}

	public static Path voiceRoot() {
		return VOICE_ROOT;
	}

	private static ConfigData readOrCreate() {
		if (!Files.exists(PATH)) {
			ConfigData defaults = ConfigData.defaults();
			write(defaults);
			return defaults;
		}

		try (Reader reader = Files.newBufferedReader(PATH)) {
			ConfigData parsed = GSON.fromJson(reader, ConfigData.class);
			if (parsed == null) {
				Lg2.LOGGER.warn("Season start config {} is empty, resetting to defaults", PATH);
				return ConfigData.defaults();
			}
			return parsed;
		} catch (Exception exception) {
			Lg2.LOGGER.warn("Failed to read season start config {}, using defaults", PATH, exception);
			return ConfigData.defaults();
		}
	}

	private static void write(ConfigData configData) {
		try {
			Files.createDirectories(PATH.getParent());
			Files.createDirectories(VOICE_ROOT.resolve("voice"));
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(configData, writer);
			}
		} catch (IOException exception) {
			Lg2.LOGGER.error("Failed to write season start config {}", PATH, exception);
		}
	}

	private static boolean sanitize(ConfigData configData) {
		boolean changed = false;

		int clampedBoxHalfWidth = clamp(configData.boxHalfWidth, 8, 64);
		if (clampedBoxHalfWidth != configData.boxHalfWidth) {
			configData.boxHalfWidth = clampedBoxHalfWidth;
			changed = true;
		}
		int clampedBoxHalfDepth = clamp(configData.boxHalfDepth, 8, 64);
		if (clampedBoxHalfDepth != configData.boxHalfDepth) {
			configData.boxHalfDepth = clampedBoxHalfDepth;
			changed = true;
		}
		int clampedBoxHeight = clamp(configData.boxHeight, 5, 48);
		if (clampedBoxHeight != configData.boxHeight) {
			configData.boxHeight = clampedBoxHeight;
			changed = true;
		}
		int clampedPlayerSlotSpacing = clamp(configData.playerSlotSpacing, 3, 8);
		if (clampedPlayerSlotSpacing != configData.playerSlotSpacing) {
			configData.playerSlotSpacing = clampedPlayerSlotSpacing;
			changed = true;
		}
		int clampedGuidanceRepeatTicks = clamp(configData.guidanceRepeatTicks, 20, 20 * 30);
		if (clampedGuidanceRepeatTicks != configData.guidanceRepeatTicks) {
			configData.guidanceRepeatTicks = clampedGuidanceRepeatTicks;
			changed = true;
		}
		int clampedSharedReminderIntervalTicks = clamp(configData.sharedReminderIntervalTicks, 20, 20 * 300);
		if (clampedSharedReminderIntervalTicks != configData.sharedReminderIntervalTicks) {
			configData.sharedReminderIntervalTicks = clampedSharedReminderIntervalTicks;
			changed = true;
		}
		int clampedIntroBlindnessTicks = clamp(configData.introBlindnessTicks, 20 * 10, 20 * 60 * 60);
		if (clampedIntroBlindnessTicks != configData.introBlindnessTicks) {
			configData.introBlindnessTicks = clampedIntroBlindnessTicks;
			changed = true;
		}
		int clampedServerVoiceDistance = clamp(configData.serverVoiceDistance, 4, 96);
		if (clampedServerVoiceDistance != configData.serverVoiceDistance) {
			configData.serverVoiceDistance = clampedServerVoiceDistance;
			changed = true;
		}

		if (configData.cues == null || configData.cues.isEmpty()) {
			configData.cues = ConfigData.defaultCues();
			changed = true;
		} else {
			List<VoiceCue> sanitized = new ArrayList<>();
			for (VoiceCue cue : configData.cues) {
				if (cue == null || cue.id == null || cue.id.isBlank() || cue.trigger == null || cue.trigger.isBlank()) {
					changed = true;
					continue;
				}
				if (cue.audience == null || cue.audience.isBlank()) {
					cue.audience = "player";
					changed = true;
				}
				if (cue.channel == null || cue.channel.isBlank()) {
					cue.channel = "player";
					changed = true;
				}
				int clampedDelayTicks = clamp(cue.delayTicks, 0, 20 * 300);
				if (clampedDelayTicks != cue.delayTicks) {
					cue.delayTicks = clampedDelayTicks;
					changed = true;
				}
				int clampedDurationTicks = clamp(cue.durationTicks, 1, 20 * 300);
				if (clampedDurationTicks != cue.durationTicks) {
					cue.durationTicks = clampedDurationTicks;
					changed = true;
				}
				if (cue.requires == null) {
					cue.requires = new ArrayList<>();
					changed = true;
				}
				sanitized.add(cue);
			}
			configData.cues = sanitized;
		}

		return changed;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public static final class ConfigData {
		public boolean autoStartOnFirstLaunch = true;
		public int boxHalfWidth = 18;
		public int boxHalfDepth = 18;
		public int boxHeight = 10;
		public int playerSlotSpacing = 4;
		public int introBlindnessTicks = 20 * 60 * 20;
		public int guidanceRepeatTicks = 60;
		public int sharedReminderIntervalTicks = 20 * 30;
		public int serverVoiceDistance = 40;
		public List<VoiceCue> cues = defaultCues();

		public static ConfigData defaults() {
			return new ConfigData();
		}

		private static List<VoiceCue> defaultCues() {
			List<VoiceCue> cues = new ArrayList<>();
			cues.add(VoiceCue.player("intro_boot", "player_intro_assigned", "voice/intro_boot.wav", "Система запускается. Найдите свой первый биткоин.", 0, 90));
			cues.add(VoiceCue.player("intro_mined", "player_mined_intro_bitcoin", "voice/intro_mined.wav", "Отлично. Теперь подойдите к серверу в темноте и запитайте его.", 10, 100)
					.require("intro_boot"));
			cues.add(VoiceCue.player("guide_turn_left", "guide_turn_left", "voice/guide_turn_left.wav", "Поверните налево.", 0, 35));
			cues.add(VoiceCue.player("guide_turn_right", "guide_turn_right", "voice/guide_turn_right.wav", "Поверните направо.", 0, 35));
			cues.add(VoiceCue.player("guide_forward", "guide_forward", "voice/guide_forward.wav", "Идите вперёд.", 0, 35));
			cues.add(VoiceCue.player("guide_back", "guide_back", "voice/guide_back.wav", "Вы ушли слишком далеко. Вернитесь назад.", 0, 45));
			cues.add(VoiceCue.player("guide_drop_coin", "guide_drop_coin", "voice/guide_drop_coin.wav", "Бросьте биткоин прямо в сервер.", 0, 55));
			cues.add(VoiceCue.player("intro_powered_private", "player_powered_server", "voice/intro_powered_private.wav", "Питание восстановлено. Теперь вы видите остальных.", 0, 90)
					.require("intro_mined"));
			cues.add(VoiceCue.shared("shared_first_light", "first_player_shared_phase", "voice/shared_first_light.wav", "Свет есть. Собирайте биткоины и следите за стабильностью сервера.", 20, 120)
					.onceGlobal());
			cues.add(VoiceCue.shared("shared_reminder", "shared_phase_reminder", "voice/shared_reminder.wav", "Биткоины поддерживают стабильность. Несите их к серверу.", 0, 100));
			cues.add(VoiceCue.global("season_finish", "season_finished", "voice/season_finish.wav", "Инициализация завершена. Мир прогружен.", 0, 100)
					.onceGlobal());
			return cues;
		}
	}

	public static final class VoiceCue {
		public String id = "";
		public String trigger = "";
		public String audience = "player";
		public String channel = "player";
		public String audioFile = "";
		public String chatText = "";
		public int delayTicks = 0;
		public int durationTicks = 80;
		public boolean onceGlobal = false;
		public boolean oncePerPlayer = true;
		public List<String> requires = new ArrayList<>();

		public VoiceCue require(String cueId) {
			if (cueId != null && !cueId.isBlank()) {
				this.requires.add(cueId);
			}
			return this;
		}

		public VoiceCue onceGlobal() {
			this.onceGlobal = true;
			this.oncePerPlayer = false;
			return this;
		}

		public static VoiceCue player(String id, String trigger, String audioFile, String chatText, int delayTicks, int durationTicks) {
			VoiceCue cue = base(id, trigger, audioFile, chatText, delayTicks, durationTicks);
			cue.audience = "player";
			cue.channel = "player";
			cue.oncePerPlayer = true;
			return cue;
		}

		public static VoiceCue shared(String id, String trigger, String audioFile, String chatText, int delayTicks, int durationTicks) {
			VoiceCue cue = base(id, trigger, audioFile, chatText, delayTicks, durationTicks);
			cue.audience = "shared";
			cue.channel = "global";
			cue.oncePerPlayer = false;
			return cue;
		}

		public static VoiceCue global(String id, String trigger, String audioFile, String chatText, int delayTicks, int durationTicks) {
			VoiceCue cue = base(id, trigger, audioFile, chatText, delayTicks, durationTicks);
			cue.audience = "all_active";
			cue.channel = "global";
			cue.oncePerPlayer = false;
			return cue;
		}

		private static VoiceCue base(String id, String trigger, String audioFile, String chatText, int delayTicks, int durationTicks) {
			VoiceCue cue = new VoiceCue();
			cue.id = id;
			cue.trigger = trigger;
			cue.audioFile = audioFile;
			cue.chatText = chatText;
			cue.delayTicks = delayTicks;
			cue.durationTicks = durationTicks;
			return cue;
		}
	}
}
