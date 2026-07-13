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
		int clampedBarrierHalfWidth = clamp(configData.barrierHalfWidth, 4, Math.max(4, clampedBoxHalfWidth - 2));
		if (clampedBarrierHalfWidth != configData.barrierHalfWidth) {
			configData.barrierHalfWidth = clampedBarrierHalfWidth;
			changed = true;
		}
		int clampedBarrierHalfDepth = clamp(configData.barrierHalfDepth, 4, Math.max(4, clampedBoxHalfDepth - 2));
		if (clampedBarrierHalfDepth != configData.barrierHalfDepth) {
			configData.barrierHalfDepth = clampedBarrierHalfDepth;
			changed = true;
		}
		int clampedBarrierHeight = clamp(configData.barrierHeight, 4, Math.max(4, clampedBoxHeight - 1));
		if (clampedBarrierHeight != configData.barrierHeight) {
			configData.barrierHeight = clampedBarrierHeight;
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
		if (configData.startupRaceId == null || configData.startupRaceId.isBlank()) {
			configData.startupRaceId = "startup_race";
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
				if (cue.scriptText == null || cue.scriptText.isBlank()) {
					if (cue.chatText != null && !cue.chatText.isBlank()) {
						cue.scriptText = cue.chatText;
						changed = true;
					} else if (cue.ttsText != null && !cue.ttsText.isBlank()) {
						cue.scriptText = cue.ttsText;
						changed = true;
					}
				}
				if (cue.scriptText == null) {
					cue.scriptText = "";
					changed = true;
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
		public int boxHalfWidth = 35;
		public int boxHalfDepth = 35;
		public int boxHeight = 23;
		public int barrierHalfWidth = 30;
		public int barrierHalfDepth = 30;
		public int barrierHeight = 18;
		public int playerSlotSpacing = 4;
		public int introBlindnessTicks = 20 * 60 * 20;
		public int guidanceRepeatTicks = 60;
		public int sharedReminderIntervalTicks = 20 * 30;
		public int serverVoiceDistance = 40;
		/** Shared placeholder race assigned to every participant while the start tutorial is active. */
		public String startupRaceId = "startup_race";
		public List<VoiceCue> cues = defaultCues();

		public static ConfigData defaults() {
			return new ConfigData();
		}

		private static List<VoiceCue> defaultCues() {
			List<VoiceCue> cues = new ArrayList<>();
			cues.add(VoiceCue.player("phase1_wait_start_prompt_01", "player_waiting_start_prompt_01", "voice/phase1_wait_start_prompt_01.wav", "Когда будете готовы, напишите в чат start. Или, если вам так спокойнее, старт.", 0, 110).repeatable());
			cues.add(VoiceCue.player("phase1_wait_start_prompt_02", "player_waiting_start_prompt_02", "voice/phase1_wait_start_prompt_02.wav", "Катсцена начнётся только после команды. Напишите start. Четыре буквы. Я в вас почти верю.", 0, 120).repeatable());
			cues.add(VoiceCue.player("phase1_wait_start_prompt_03", "player_waiting_start_prompt_03", "voice/phase1_wait_start_prompt_03.wav", "Я подожду. Напишите start. Можно старт. Но давайте без творческой самодеятельности.", 0, 115).repeatable());
			cues.add(VoiceCue.player("phase1_wait_start_wrong_01", "player_waiting_start_wrong_01", "voice/phase1_wait_start_wrong_01.wav", "Нет. Я просил написать start. Или старт. То, что получилось у вас, звучит гораздо хуже.", 0, 120).repeatable());
			cues.add(VoiceCue.player("phase1_wait_start_wrong_02", "player_waiting_start_wrong_02", "voice/phase1_wait_start_wrong_02.wav", "В чат ушло слово с неожиданно честной интонацией. Но я всё ещё жду start.", 0, 105).repeatable());
			cues.add(VoiceCue.player("phase1_wait_start_confirmed", "player_waiting_start_confirmed", "voice/phase1_wait_start_confirmed.wav", "Да. Именно это слово. Теперь можно начинать.", 0, 65));
			cues.add(VoiceCue.player("phase1_intro_welcome", "player_intro_assigned", "voice/phase1_intro_welcome.wav", "Добро пожаловать в Lost Glade.", 50, 50));
			cues.add(VoiceCue.player("phase1_intro_dont_move", "player_intro_assigned", "voice/phase1_intro_dont_move.wav", "Не двигайтесь резко. Хотя, если честно, это мало что изменит.", 140, 95));
			cues.add(VoiceCue.player("phase1_intro_blindness", "player_intro_assigned", "voice/phase1_intro_blindness.wav", "Сейчас вы ничего не видите. Это нормально. Система ещё не решила, стоит ли вам доверять собственному зрению.", 260, 140));
			cues.add(VoiceCue.player("phase1_intro_block", "player_intro_assigned", "voice/phase1_intro_block.wav", "Перед вами блок. Вам придётся поверить мне на слово.", 430, 95));
			cues.add(VoiceCue.player("phase1_intro_break", "player_intro_assigned", "voice/phase1_intro_break.wav", "Сломайте его.", 550, 35));

			cues.add(VoiceCue.player("phase1_mined_here", "player_mined_intro_bitcoin", "voice/phase1_mined_here.wav", "Вот. Первый биткоин.", 0, 60)
					.tts("Вот он. Первый бит-коин.")
					.require("phase1_intro_break"));
			cues.add(VoiceCue.player("phase1_mined_honest_work", "player_mined_intro_bitcoin", "voice/phase1_mined_honest_work.wav", "Добыт честным трудом. Редкий момент. Запомните его.", 110, 85)
					.require("phase1_intro_break"));
			cues.add(VoiceCue.player("phase1_mined_main_resource", "player_mined_intro_bitcoin", "voice/phase1_mined_main_resource.wav", "Биткоин — главный ресурс Lost Glade. Он нужен для развития. И он нужен мне.", 220, 120)
					.tts("Бит-коин — главный ресурс Lost Glade. Он нужен для развития. И он нужен мне.")
					.require("phase1_intro_break"));
			cues.add(VoiceCue.player("phase1_mined_server_continues", "player_mined_intro_bitcoin", "voice/phase1_mined_server_continues.wav", "Если сервер получает биткоины, сезон продолжается. Если сервер не получает биткоины, стабильность падает.", 360, 145)
					.tts("Если сервер получает бит-коины, сезон продолжается. Если сервер не получает бит-коины, стабильность падает.")
					.require("phase1_intro_break"));
			cues.add(VoiceCue.player("phase1_mined_shutdown", "player_mined_intro_bitcoin", "voice/phase1_mined_shutdown.wav", "На нуле сервер выключается, и сезон заканчивается. Советую отнестись к этому буквально.", 555, 125)
					.require("phase1_intro_break"));
			cues.add(VoiceCue.player("phase1_mined_start_recall", "player_mined_intro_bitcoin", "voice/phase1_mined_start_recall.wav", "И да. Вашим первым вкладом в журнал Lost Glade уже стало слово, которое чат превратил в срать. Запись сохранена.", 700, 125)
					.tts("И да. Вашим первым вкладом в журнал Lost Glade уже стало слово, которое чат превратил в срать. Запись сохранена.")
					.require("phase1_intro_break"));

			cues.add(VoiceCue.player("phase1_idle_block_still_there", "intro_phase1_idle", "voice/phase1_idle_block_still_there.wav", "Блок всё ещё перед вами. Я понимаю, ситуация странная. Но странность — не повод бездействовать.", 0, 130).repeatable());
			cues.add(VoiceCue.player("phase1_leave_no", "intro_phase1_leave_attempt", "voice/phase1_leave_no.wav", "Нет. Начнём с блока. Побег от экономики запланирован позже.", 0, 110).repeatable());
			cues.add(VoiceCue.player("phase1_spin_darkness_everywhere", "intro_phase1_spin", "voice/phase1_spin_darkness_everywhere.wav", "Вы можете осматриваться сколько угодно. Темнота, поверьте, везде выглядит одинаково.", 0, 95).repeatable());
			cues.add(VoiceCue.player("phase1_jump_accepted", "intro_phase1_jump", "voice/phase1_jump_accepted.wav", "Прыжок принят. На финансовую ситуацию не повлиял.", 0, 80).repeatable());
			cues.add(VoiceCue.player("phase1_air_punch_almost", "intro_phase1_air_punch", "voice/phase1_air_punch_almost.wav", "Почти. Только теперь попробуйте попасть по блоку, а не по собственной репутации.", 0, 110).repeatable());

			cues.add(VoiceCue.player("guide_turn_left_hard_01", "guide_turn_left_hard_01", "voice/guide_turn_left_hard_01.wav", "Налево. И заметно.", 0, 42).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_left_hard_02", "guide_turn_left_hard_02", "voice/guide_turn_left_hard_02.wav", "Сильно налево. Пока вы разговариваете со стеной, а не со мной.", 0, 82).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_left_hard_03", "guide_turn_left_hard_03", "voice/guide_turn_left_hard_03.wav", "Повернитесь налево. Это ещё не тонкая настройка.", 0, 68).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_left_soft_01", "guide_turn_left_soft_01", "voice/guide_turn_left_soft_01.wav", "Чуть левее.", 0, 32).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_left_soft_02", "guide_turn_left_soft_02", "voice/guide_turn_left_soft_02.wav", "Ещё немного налево.", 0, 40).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_left_soft_03", "guide_turn_left_soft_03", "voice/guide_turn_left_soft_03.wav", "Налево. Да, простите, теперь уже точно налево.", 0, 64).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_right_hard_01", "guide_turn_right_hard_01", "voice/guide_turn_right_hard_01.wav", "Направо. И ощутимо.", 0, 42).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_right_hard_02", "guide_turn_right_hard_02", "voice/guide_turn_right_hard_02.wav", "Сильно направо. Пока что вы промахиваетесь даже мимо темноты.", 0, 84).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_right_hard_03", "guide_turn_right_hard_03", "voice/guide_turn_right_hard_03.wav", "Повернитесь направо. Да, намного больше.", 0, 58).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_right_soft_01", "guide_turn_right_soft_01", "voice/guide_turn_right_soft_01.wav", "Чуть правее.", 0, 32).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_right_soft_02", "guide_turn_right_soft_02", "voice/guide_turn_right_soft_02.wav", "Ещё немного направо.", 0, 40).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_right_soft_03", "guide_turn_right_soft_03", "voice/guide_turn_right_soft_03.wav", "Направо. Нет, в другое право. Да, вот это.", 0, 66).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_around_left_01", "guide_turn_around_left_01", "voice/guide_turn_around_left_01.wav", "Почти полный разворот налево.", 0, 48).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_around_left_02", "guide_turn_around_left_02", "voice/guide_turn_around_left_02.wav", "Развернитесь через левое плечо. Сейчас вы смотрите почти в противоположную жизнь.", 0, 92).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_around_right_01", "guide_turn_around_right_01", "voice/guide_turn_around_right_01.wav", "Почти полный разворот направо.", 0, 48).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_turn_around_right_02", "guide_turn_around_right_02", "voice/guide_turn_around_right_02.wav", "Разворот направо. Сейчас вы удивительно уверенно смотрите не туда.", 0, 80).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_locked_on_01", "guide_locked_on_01", "voice/guide_locked_on_01.wav", "Вот. Идеальное направление.", 0, 42).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_locked_on_02", "guide_locked_on_02", "voice/guide_locked_on_02.wav", "Да. Именно туда. Теперь не испортите.", 0, 54).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_locked_on_03", "guide_locked_on_03", "voice/guide_locked_on_03.wav", "Наконец-то. Сейчас вы смотрите куда нужно.", 0, 60).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_far_01", "guide_forward_far_01", "voice/guide_forward_far_01.wav", "Вперёд. Около двенадцати блоков.", 0, 52).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_far_02", "guide_forward_far_02", "voice/guide_forward_far_02.wav", "Хорошо. Держите курс. Ещё примерно двенадцать блоков.", 0, 72).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_mid_01", "guide_forward_mid_01", "voice/guide_forward_mid_01.wav", "Восемь блоков вперёд.", 0, 40).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_mid_02", "guide_forward_mid_02", "voice/guide_forward_mid_02.wav", "Уже лучше. Около восьми блоков. Вперёд.", 0, 58).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_near_01", "guide_forward_near_01", "voice/guide_forward_near_01.wav", "Осталось около пяти блоков.", 0, 46).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_near_02", "guide_forward_near_02", "voice/guide_forward_near_02.wav", "Пять блоков. Не сворачивайте.", 0, 40).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_close_01", "guide_forward_close_01", "voice/guide_forward_close_01.wav", "Три блока. Почти у цели.", 0, 42).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_forward_close_02", "guide_forward_close_02", "voice/guide_forward_close_02.wav", "Ещё три блока вперёд.", 0, 38).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_stall_01", "guide_stall_01", "voice/guide_stall_01.wav", "Направление верное. Теперь идите.", 0, 44).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_stall_02", "guide_stall_02", "voice/guide_stall_02.wav", "Стоять красиво, но бесполезно. Вперёд.", 0, 50).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_wrong_way_01", "guide_wrong_way_01", "voice/guide_wrong_way_01.wav", "Блять, не туда. Вы удаляетесь от ядра.", 0, 56).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_wrong_way_02", "guide_wrong_way_02", "voice/guide_wrong_way_02.wav", "Стоп. Это уже движение от сервера, а не к нему.", 0, 68).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_wrong_way_03", "guide_wrong_way_03", "voice/guide_wrong_way_03.wav", "Сто-о-ой. Сейчас вы всё портите. Вернитесь к правильному направлению.", 0, 86).repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_drop_coin_01", "guide_drop_coin_01", "voice/guide_drop_coin_01.wav", "Стоп. Перед вами ядро Lost Glade. Бросьте биткоин прямо в сервер. Клавиша кью.", 0, 92)
					.tts("Стоп. Перед вами ядро Lost Glade. Бросьте бит-коин прямо в сервер. Нажмите клавишу кью.")
					.repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_drop_coin_02", "guide_drop_coin_02", "voice/guide_drop_coin_02.wav", "Вот оно. Ядро прямо перед вами. Бросьте биткоин. Клавиша кью.", 0, 76)
					.tts("Вот оно. Ядро прямо перед вами. Бросьте бит-коин. Нажмите клавишу кью.")
					.repeatable().interruptCurrent());
			cues.add(VoiceCue.player("guide_drop_coin_03", "guide_drop_coin_03", "voice/guide_drop_coin_03.wav", "Не проходите мимо. Это ядро. Кью, и биткоин летит в сервер.", 0, 78)
					.tts("Не проходите мимо. Это ядро. Кью, и бит-коин летит в сервер.")
					.repeatable().interruptCurrent());
			cues.add(VoiceCue.player("intro_powered_private", "player_powered_server", "voice/intro_powered_private.wav", "Питание восстановлено. Теперь вы видите остальных.", 0, 90));
			cues.add(VoiceCue.shared("shared_first_light", "first_player_shared_phase", "voice/shared_first_light.wav", "Свет есть. Теперь собирайте биткоины и следите за стабильностью сервера.", 20, 120)
					.tts("Свет есть. Теперь собирайте бит-коины и следите за стабильностью сервера.")
					.onceGlobal());
			cues.add(VoiceCue.shared("shared_reminder", "shared_phase_reminder", "voice/shared_reminder.wav", "Биткоины поддерживают стабильность. Несите их к серверу.", 0, 100)
					.tts("Бит-коины поддерживают стабильность. Несите их к серверу."));
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
		public String scriptText = "";
		public String chatText;
		public String ttsText;
		public int delayTicks = 0;
		public int durationTicks = 80;
		public boolean onceGlobal = false;
		public boolean oncePerPlayer = true;
		public boolean interruptCurrent = false;
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

		public VoiceCue repeatable() {
			this.oncePerPlayer = false;
			this.onceGlobal = false;
			return this;
		}

		public VoiceCue interruptCurrent() {
			this.interruptCurrent = true;
			return this;
		}

		public VoiceCue chat(String text) {
			this.chatText = text;
			return this;
		}

		public VoiceCue tts(String text) {
			this.ttsText = text;
			return this;
		}

		public String resolvedChatText() {
			if (chatText != null && !chatText.isBlank()) {
				return chatText;
			}
			if (scriptText != null && !scriptText.isBlank()) {
				return scriptText;
			}
			return ttsText == null ? "" : ttsText;
		}

		public String resolvedTtsText() {
			if (ttsText != null && !ttsText.isBlank()) {
				return ttsText;
			}
			if (scriptText != null && !scriptText.isBlank()) {
				return scriptText;
			}
			return chatText == null ? "" : chatText;
		}

		public static VoiceCue player(String id, String trigger, String audioFile, String scriptText, int delayTicks, int durationTicks) {
			VoiceCue cue = base(id, trigger, audioFile, scriptText, delayTicks, durationTicks);
			cue.audience = "player";
			cue.channel = "player";
			cue.oncePerPlayer = true;
			return cue;
		}

		public static VoiceCue shared(String id, String trigger, String audioFile, String scriptText, int delayTicks, int durationTicks) {
			VoiceCue cue = base(id, trigger, audioFile, scriptText, delayTicks, durationTicks);
			cue.audience = "shared";
			cue.channel = "global";
			cue.oncePerPlayer = false;
			return cue;
		}

		public static VoiceCue global(String id, String trigger, String audioFile, String scriptText, int delayTicks, int durationTicks) {
			VoiceCue cue = base(id, trigger, audioFile, scriptText, delayTicks, durationTicks);
			cue.audience = "all_active";
			cue.channel = "global";
			cue.oncePerPlayer = false;
			return cue;
		}

		private static VoiceCue base(String id, String trigger, String audioFile, String scriptText, int delayTicks, int durationTicks) {
			VoiceCue cue = new VoiceCue();
			cue.id = id;
			cue.trigger = trigger;
			cue.audioFile = audioFile;
			cue.scriptText = scriptText;
			cue.delayTicks = delayTicks;
			cue.durationTicks = durationTicks;
			return cue;
		}
	}
}
