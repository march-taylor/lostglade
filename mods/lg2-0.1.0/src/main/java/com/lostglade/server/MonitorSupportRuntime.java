package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lostglade.Lg2;
import com.lostglade.server.monitor.MonitorApp;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.lostglade.server.MonitorScreenSystem.*;

public final class MonitorSupportRuntime {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String STATE_FILE_NAME = "lg2-support.json";
	private static final String TOKEN_ENV_NAME = "LG2_SUPPORT_TELEGRAM_BOT_TOKEN";
	private static final String TOKEN_PROPERTY_NAME = "lg2.supportTelegramBotToken";
	private static final Path TOKEN_FILE = Path.of("server-secrets", "telegram", "support-bot-token.txt");
	private static final int MAX_VISIBLE_MESSAGES = 12;
	private static final int MAX_UI_MESSAGE_LENGTH = 700;
	private static final int MAX_TELEGRAM_MESSAGE_LENGTH = 3900;
	private static final long TELEGRAM_POLL_INTERVAL_MS = 1800L;
	private static final long TELEGRAM_REQUEST_TIMEOUT_SECONDS = 12L;
	private static final Color TINKOFF_BLUE = new Color(18, 154, 235);
	private static final Color TINKOFF_LIGHT_BLUE = new Color(84, 203, 246);
	private static final Color SCREEN_BACKGROUND = new Color(247, 249, 252);
	private static final Color USER_BUBBLE = new Color(198, 238, 255);
	private static final Color SUPPORT_BUBBLE = new Color(235, 239, 244);
	private static final Color PRIMARY_TEXT = new Color(24, 27, 31);
	private static final Color SECONDARY_TEXT = new Color(132, 141, 153);

	private static final ConcurrentMap<ScreenRuntimeKey, SupportRuntimeState> STATES = new ConcurrentHashMap<>();
	private static final ConcurrentMap<UUID, ScreenRuntimeKey> PENDING_INPUTS = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Long, SupportTicket> TICKETS = new ConcurrentHashMap<>();
	private static final ConcurrentMap<TelegramMessageKey, Long> TELEGRAM_MESSAGE_TICKETS = new ConcurrentHashMap<>();
	private static final Set<UUID> MINECRAFT_SUPPORT_OPERATORS = ConcurrentHashMap.newKeySet();
	private static final Set<Long> TELEGRAM_CHAT_IDS = ConcurrentHashMap.newKeySet();
	private static final AtomicLong NEXT_TICKET_ID = new AtomicLong(1L);
	private static final Object PERSISTENCE_LOCK = new Object();
	private static final HttpClient TELEGRAM_HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(TELEGRAM_REQUEST_TIMEOUT_SECONDS))
			.build();

	private static volatile boolean loaded;
	private static volatile String telegramToken = "";
	private static volatile long telegramOffset;
	private static volatile ScheduledExecutorService telegramExecutor;
	private static volatile MinecraftServer activeServer;

	private MonitorSupportRuntime() {
	}

	public static void register() {
		loaded = false;
		ServerLifecycleEvents.SERVER_STARTED.register(MonitorSupportRuntime::start);
		ServerLifecycleEvents.SERVER_STOPPING.register(MonitorSupportRuntime::stop);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						Commands.literal("support")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.literal("on")
										.executes(context -> enableMinecraftSupport(context.getSource())))
								.then(Commands.literal("off")
										.executes(context -> disableMinecraftSupport(context.getSource())))
								.then(Commands.literal("status")
										.executes(context -> sendSupportStatus(context.getSource())))
								.then(Commands.literal("reply")
										.then(Commands.argument("ticket", LongArgumentType.longArg(1L))
												.then(Commands.argument("message", StringArgumentType.greedyString())
														.executes(context -> replyFromMinecraft(
																context.getSource(),
																LongArgumentType.getLong(context, "ticket"),
																StringArgumentType.getString(context, "message")
														)))))
				)
		);
	}

	static SupportVisualSnapshot captureSnapshot(MinecraftServer server, ScreenComponent component) {
		if (component == null) {
			return new SupportVisualSnapshot(0L, List.of(), false, telegramConfigured(), receiverCount(server), "");
		}
		SupportRuntimeState state = ensureState(component.runtimeKey());
		List<SupportMessageSnapshot> messages;
		long version;
		String statusText;
		synchronized (state) {
			ensureGreetingLocked(state);
			int from = Math.max(0, state.messages.size() - MAX_VISIBLE_MESSAGES);
			messages = state.messages.subList(from, state.messages.size()).stream()
					.map(message -> new SupportMessageSnapshot(
							message.fromSupport(),
							message.author(),
							message.text(),
							message.createdAtMillis()
					))
					.toList();
			version = state.version;
			statusText = state.statusText;
		}
		return new SupportVisualSnapshot(
				version,
				messages,
				waitingForInput(component.runtimeKey()),
				telegramConfigured(),
				receiverCount(server),
				statusText
		);
	}

	static void drawScreen(Graphics2D graphics, UiLayout layout, MonitorApp app, ScreenRuntimeKey key, SupportVisualSnapshot snapshot) {
		if (graphics == null || layout == null) {
			return;
		}
		SupportVisualSnapshot safeSnapshot = snapshot == null
				? new SupportVisualSnapshot(0L, List.of(), false, telegramConfigured(), 0, "")
				: snapshot;
		UiRect canvas = mediaCanvasRect(layout);
		fillRoundedRect(graphics, canvas, 0, SCREEN_BACKGROUND);
		drawHeader(graphics, layout, app);
		drawMessages(graphics, layout, app, safeSnapshot);
		drawInputBar(graphics, layout, safeSnapshot);
	}

	static boolean handleTouch(MinecraftServer server, ServerPlayer player, ScreenComponent component, UiLayout layout, UiPoint touchPoint) {
		if (server == null || player == null || component == null || layout == null || touchPoint == null) {
			return true;
		}
		if (supportCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			PENDING_INPUTS.remove(player.getUUID());
			return false;
		}
		if (supportAttachRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			player.displayClientMessage(Component.literal("Вложения в поддержку пока недоступны. Опиши баг или идею текстом."), true);
			return true;
		}
		if (supportInputRect(layout).contains(touchPoint.x(), touchPoint.y())
				|| supportSendRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			PENDING_INPUTS.put(player.getUUID(), component.runtimeKey());
			SupportRuntimeState state = ensureState(component.runtimeKey());
			synchronized (state) {
				state.statusText = "Ждём сообщение в чате";
				state.version++;
			}
			player.displayClientMessage(Component.literal("Напиши сообщение поддержки в чат. Оно не будет отправлено всем игрокам."), true);
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		return true;
	}

	static boolean consumeSupportChatMessage(MinecraftServer server, PlayerChatMessage message, ServerPlayer sender) {
		if (server == null || message == null || sender == null) {
			return false;
		}
		ScreenRuntimeKey key = PENDING_INPUTS.remove(sender.getUUID());
		if (key == null) {
			return false;
		}
		String text = message.signedContent() == null ? "" : message.signedContent().trim();
		if (text.isBlank()) {
			PENDING_INPUTS.put(sender.getUUID(), key);
			sender.displayClientMessage(Component.literal("Сообщение пустое. Напиши текст обращения в чат."), true);
			requestRuntimeRender(server, key);
			return true;
		}
		SupportRuntimeState state = ensureState(key);
		long ticketId = NEXT_TICKET_ID.getAndIncrement();
		String playerName = playerName(sender);
		SupportTicket ticket = new SupportTicket(ticketId, key, sender.getUUID(), playerName, System.currentTimeMillis());
		TICKETS.put(ticketId, ticket);
		synchronized (state) {
			ensureGreetingLocked(state);
			state.messages.add(new SupportChatMessage(false, playerName, truncateUiMessage(text), System.currentTimeMillis()));
			state.messages.add(new SupportChatMessage(true, "Поддержка", "Приняли обращение #" + ticketId + ". Ответ появится здесь.", System.currentTimeMillis()));
			state.statusText = "Обращение #" + ticketId + " отправлено";
			state.version++;
		}
		sender.displayClientMessage(Component.literal("Обращение #" + ticketId + " отправлено в поддержку."), true);
		notifyMinecraftOperators(server, ticket, text);
		notifyTelegramOperators(ticket, text);
		requestRuntimeRender(server, key);
		return true;
	}

	private static void start(MinecraftServer server) {
		activeServer = server;
		load(server);
		telegramToken = readConfiguredTelegramToken();
		if (telegramConfigured()) {
			startTelegramPolling(server);
		} else {
			Lg2.LOGGER.info("Support Telegram bot token is not configured");
		}
	}

	private static void stop(MinecraftServer server) {
		activeServer = null;
		save(server);
		ScheduledExecutorService executor = telegramExecutor;
		telegramExecutor = null;
		if (executor != null) {
			executor.shutdownNow();
		}
		PENDING_INPUTS.clear();
	}

	private static int enableMinecraftSupport(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ensureLoaded(source.getServer());
		MINECRAFT_SUPPORT_OPERATORS.add(player.getUUID());
		save(source.getServer());
		source.sendSuccess(() -> Component.literal("Приём обращений поддержки включён для " + playerName(player) + "."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int disableMinecraftSupport(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ensureLoaded(source.getServer());
		MINECRAFT_SUPPORT_OPERATORS.remove(player.getUUID());
		save(source.getServer());
		source.sendSuccess(() -> Component.literal("Приём обращений поддержки выключен для " + playerName(player) + "."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int sendSupportStatus(CommandSourceStack source) {
		ensureLoaded(source.getServer());
		int minecraftOperators = MINECRAFT_SUPPORT_OPERATORS.size();
		int onlineMinecraftOperators = onlineMinecraftOperatorCount(source.getServer());
		int telegramChats = TELEGRAM_CHAT_IDS.size();
		source.sendSuccess(
				() -> Component.literal(
						"Support: bot="
								+ (telegramConfigured() ? "configured" : "not configured")
								+ ", telegram chats="
								+ telegramChats
								+ ", minecraft operators="
								+ minecraftOperators
								+ " (online "
								+ onlineMinecraftOperators
								+ ")"
				),
				false
		);
		return Command.SINGLE_SUCCESS;
	}

	private static int replyFromMinecraft(CommandSourceStack source, long ticketId, String message) {
		ensureLoaded(source.getServer());
		SupportTicket ticket = TICKETS.get(ticketId);
		String text = message == null ? "" : message.trim();
		if (ticket == null) {
			source.sendFailure(Component.literal("Обращение #" + ticketId + " не найдено в текущей сессии."));
			return 0;
		}
		if (text.isBlank()) {
			source.sendFailure(Component.literal("Ответ поддержки пустой."));
			return 0;
		}
		addSupportReply(source.getServer(), ticketId, supportAuthorName(source), text);
		source.sendSuccess(() -> Component.literal("Ответ отправлен в обращение #" + ticketId + "."), false);
		return Command.SINGLE_SUCCESS;
	}

	private static void notifyMinecraftOperators(MinecraftServer server, SupportTicket ticket, String message) {
		if (server == null || ticket == null) {
			return;
		}
		String text = "Поддержка #" + ticket.id() + " от " + ticket.playerName() + ": " + truncateForChat(message, 220)
				+ " Ответ: /support reply " + ticket.id() + " <текст>";
		for (UUID operatorId : MINECRAFT_SUPPORT_OPERATORS) {
			ServerPlayer operator = server.getPlayerList().getPlayer(operatorId);
			if (operator != null) {
				operator.displayClientMessage(Component.literal(text), false);
			}
		}
	}

	private static void notifyTelegramOperators(SupportTicket ticket, String message) {
		if (ticket == null || !telegramConfigured() || TELEGRAM_CHAT_IDS.isEmpty()) {
			return;
		}
		String payload = formatTelegramTicket(ticket, message);
		for (Long chatId : TELEGRAM_CHAT_IDS) {
			if (chatId == null) {
				continue;
			}
			runTelegramTask(() -> {
				int messageId = sendTelegramMessageInternal(chatId, payload);
				if (messageId > 0) {
					TELEGRAM_MESSAGE_TICKETS.put(new TelegramMessageKey(chatId, messageId), ticket.id());
				}
			});
		}
	}

	private static void addSupportReply(MinecraftServer server, long ticketId, String author, String message) {
		SupportTicket ticket = TICKETS.get(ticketId);
		if (ticket == null) {
			return;
		}
		SupportRuntimeState state = ensureState(ticket.key());
		synchronized (state) {
			ensureGreetingLocked(state);
			state.messages.add(new SupportChatMessage(true, author == null || author.isBlank() ? "Поддержка" : author, truncateUiMessage(message), System.currentTimeMillis()));
			state.statusText = "Ответ поддержки";
			state.version++;
		}
		if (server != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(ticket.playerUuid());
			if (player != null) {
				player.displayClientMessage(Component.literal("Поддержка ответила на обращение #" + ticketId + "."), true);
			}
			requestRuntimeRender(server, ticket.key());
		}
	}

	private static void startTelegramPolling(MinecraftServer server) {
		if (server == null || !telegramConfigured()) {
			return;
		}
		ScheduledExecutorService current = telegramExecutor;
		if (current != null && !current.isShutdown()) {
			return;
		}
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "lg2-support-telegram");
			thread.setDaemon(true);
			thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
			return thread;
		});
		telegramExecutor = executor;
		executor.scheduleWithFixedDelay(
				() -> pollTelegramUpdates(server),
				400L,
				TELEGRAM_POLL_INTERVAL_MS,
				TimeUnit.MILLISECONDS
		);
	}

	private static void pollTelegramUpdates(MinecraftServer server) {
		if (server == null || !telegramConfigured()) {
			return;
		}
		try {
			String query = "timeout=0&offset=" + Math.max(0L, telegramOffset);
			HttpRequest request = HttpRequest.newBuilder(telegramApiUri("getUpdates?" + query))
					.timeout(Duration.ofSeconds(TELEGRAM_REQUEST_TIMEOUT_SECONDS))
					.GET()
					.build();
			HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return;
			}
			JsonElement parsed = JsonParser.parseString(response.body());
			if (!(parsed instanceof JsonObject root) || !root.has("ok") || !root.get("ok").getAsBoolean()) {
				return;
			}
			JsonArray result = root.getAsJsonArray("result");
			if (result == null) {
				return;
			}
			for (JsonElement element : result) {
				if (!(element instanceof JsonObject update)) {
					continue;
				}
				long updateId = readLong(update, "update_id", -1L);
				if (updateId >= 0L) {
					telegramOffset = Math.max(telegramOffset, updateId + 1L);
				}
				JsonObject telegramMessage = update.getAsJsonObject("message");
				if (telegramMessage != null) {
					handleTelegramMessage(server, telegramMessage);
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to poll support Telegram bot", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException exception) {
			Lg2.LOGGER.warn("Failed to process support Telegram update", exception);
		}
	}

	private static void handleTelegramMessage(MinecraftServer server, JsonObject message) {
		long chatId = telegramChatId(message);
		if (chatId == Long.MIN_VALUE) {
			return;
		}
		String text = readString(message, "text").trim();
		if (text.isBlank()) {
			return;
		}
		String lower = firstCommandToken(text).toLowerCase(Locale.ROOT);
		if (lower.equals("/start") || lower.equals("/help")) {
			sendTelegramMessage(chatId, "Команды поддержки: /support_on, /support_off, /support_status. Ответить игроку: /reply <номер> <текст> или reply на сообщение обращения.");
			return;
		}
		if (isSupportOnCommand(text)) {
			ensureLoaded(server);
			TELEGRAM_CHAT_IDS.add(chatId);
			save(server);
			sendTelegramMessage(chatId, "Приём обращений сервера включён для этого чата.");
			return;
		}
		if (isSupportOffCommand(text)) {
			ensureLoaded(server);
			TELEGRAM_CHAT_IDS.remove(chatId);
			save(server);
			sendTelegramMessage(chatId, "Приём обращений сервера выключен для этого чата.");
			return;
		}
		if (isSupportStatusCommand(text)) {
			sendTelegramMessage(chatId, telegramStatusText(server, chatId));
			return;
		}
		if (!TELEGRAM_CHAT_IDS.contains(chatId)) {
			sendTelegramMessage(chatId, "Сначала включи приём обращений командой /support_on.");
			return;
		}
		TelegramReply reply = parseTelegramReply(chatId, message, text);
		if (reply == null) {
			return;
		}
		String author = telegramAuthor(message);
		MinecraftServer targetServer = activeServer != null ? activeServer : server;
		if (targetServer == null) {
			return;
		}
		targetServer.execute(() -> {
			SupportTicket ticket = TICKETS.get(reply.ticketId());
			if (ticket == null) {
				sendTelegramMessage(chatId, "Обращение #" + reply.ticketId() + " не найдено в текущей сессии сервера.");
				return;
			}
			addSupportReply(targetServer, reply.ticketId(), author, reply.message());
			sendTelegramMessage(chatId, "Ответ отправлен в обращение #" + reply.ticketId() + ".");
		});
	}

	private static TelegramReply parseTelegramReply(long chatId, JsonObject message, String text) {
		String trimmed = text == null ? "" : text.trim();
		String lower = firstCommandToken(trimmed).toLowerCase(Locale.ROOT);
		if (lower.equals("/reply")) {
			String rest = trimmed.length() > 6 ? trimmed.substring(6).trim() : "";
			int split = firstWhitespace(rest);
			if (split > 0) {
				Long ticketId = parseLong(rest.substring(0, split));
				String replyText = rest.substring(split + 1).trim();
				if (ticketId != null && !replyText.isBlank()) {
					return new TelegramReply(ticketId, replyText);
				}
			}
		}
		JsonObject replyTo = message.getAsJsonObject("reply_to_message");
		if (replyTo == null) {
			return null;
		}
		long ticketId = -1L;
		int replyMessageId = readInt(replyTo, "message_id", -1);
		Long mappedTicket = TELEGRAM_MESSAGE_TICKETS.get(new TelegramMessageKey(chatId, replyMessageId));
		if (mappedTicket != null) {
			ticketId = mappedTicket;
		} else {
			ticketId = parseTicketIdFromText(readString(replyTo, "text"));
		}
		if (ticketId <= 0L || trimmed.isBlank() || trimmed.startsWith("/")) {
			return null;
		}
		return new TelegramReply(ticketId, trimmed);
	}

	private static void sendTelegramMessage(long chatId, String text) {
		if (!telegramConfigured()) {
			return;
		}
		runTelegramTask(() -> sendTelegramMessageInternal(chatId, text));
	}

	private static int sendTelegramMessageInternal(long chatId, String text) {
		if (!telegramConfigured()) {
			return -1;
		}
		try {
			String body = formBody(Map.of(
					"chat_id", Long.toString(chatId),
					"text", truncateTelegramMessage(text)
			));
			HttpRequest request = HttpRequest.newBuilder(telegramApiUri("sendMessage"))
					.timeout(Duration.ofSeconds(TELEGRAM_REQUEST_TIMEOUT_SECONDS))
					.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
					.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return -1;
			}
			JsonElement parsed = JsonParser.parseString(response.body());
			if (!(parsed instanceof JsonObject root) || !root.has("ok") || !root.get("ok").getAsBoolean()) {
				return -1;
			}
			JsonObject result = root.getAsJsonObject("result");
			return result != null ? readInt(result, "message_id", -1) : -1;
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to send support Telegram message", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException exception) {
			Lg2.LOGGER.warn("Failed to build support Telegram message", exception);
		}
		return -1;
	}

	private static void runTelegramTask(Runnable runnable) {
		ScheduledExecutorService executor = telegramExecutor;
		if (executor == null || executor.isShutdown()) {
			MinecraftServer server = activeServer;
			if (server != null && telegramConfigured()) {
				startTelegramPolling(server);
				executor = telegramExecutor;
			}
		}
		if (executor == null || executor.isShutdown()) {
			return;
		}
		executor.execute(runnable);
	}

	private static URI telegramApiUri(String method) {
		return URI.create("https://api.telegram.org/bot" + telegramToken + "/" + method);
	}

	private static String formBody(Map<String, String> values) {
		StringJoiner joiner = new StringJoiner("&");
		for (Map.Entry<String, String> entry : values.entrySet()) {
			joiner.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
					+ "="
					+ URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
		}
		return joiner.toString();
	}

	private static void drawHeader(Graphics2D graphics, UiLayout layout, MonitorApp app) {
		UiRect canvas = mediaCanvasRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		UiRect close = supportCloseRect(layout);
		fillRoundedRect(graphics, close, close.height(), TINKOFF_LIGHT_BLUE);
		drawPlayerUiIcon(graphics, close.inset(Math.max(2, close.width() / 4)), PlayerUiIcon.CLOSE, Color.WHITE);

		int logoSize = ultra ? clampInt(layout.unit() * 2 + 2, 14, 18) : clampInt(layout.unit() * 3, 28, 44);
		int logoX = close.right() + (ultra ? 3 : inset);
		int logoY = close.y() + (close.height() - logoSize) / 2;
		UiRect logo = new UiRect(logoX, logoY, logoSize, logoSize);
		drawAppIcon(graphics, app, logo, 0);

		int textX = logo.right() + (ultra ? 4 : clampInt(layout.unit() / 2, 4, 9));
		int titleSize = ultra ? clampInt(layout.unit() + 1, 8, 11) : clampInt(layout.unit() + 6, 16, 24);
		int subtitleSize = ultra ? clampInt(layout.unit() - 1, 6, 8) : clampInt(layout.unit() + 1, 11, 16);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, titleSize));
		graphics.setColor(PRIMARY_TEXT);
		int titleY = close.y() + (ultra ? close.height() / 2 : close.height() / 2 - 1);
		graphics.drawString("Поддержка", textX, titleY);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, subtitleSize));
		graphics.setColor(SECONDARY_TEXT);
		int subtitleY = titleY + (ultra ? subtitleSize + 1 : subtitleSize + 5);
		if (!ultra && subtitleY < canvas.height()) {
			graphics.drawString("Мы рядом 24/7", textX, subtitleY);
		}
	}

	private static void drawMessages(Graphics2D graphics, UiLayout layout, MonitorApp app, SupportVisualSnapshot snapshot) {
		UiRect area = supportMessagesRect(layout);
		List<SupportMessageSnapshot> messages = snapshot.messages() == null ? List.of() : snapshot.messages();
		int gap = compactGap(layout);
		int y = area.bottom();
		for (int index = messages.size() - 1; index >= 0; index--) {
			SupportMessageSnapshot message = messages.get(index);
			if (message == null || message.text() == null || message.text().isBlank()) {
				continue;
			}
			int height = messageBubbleHeight(graphics, layout, area, message);
			y -= height;
			if (y < area.y()) {
				break;
			}
			drawMessageBubble(graphics, layout, app, area, message, y, height);
			y -= gap;
		}
	}

	private static void drawMessageBubble(Graphics2D graphics, UiLayout layout, MonitorApp app, UiRect area, SupportMessageSnapshot message, int y, int height) {
		boolean support = message.fromSupport();
		boolean ultra = ultraCompactScreenLayout(layout);
		int avatarSize = support ? (ultra ? clampInt(layout.unit() * 2, 12, 16) : clampInt(layout.unit() * 3, 24, 36)) : 0;
		int avatarGap = support ? Math.max(2, layout.unit() / 3) : 0;
		int maxBubbleWidth = support
				? Math.max(12, area.width() * 66 / 100 - avatarSize - avatarGap)
				: Math.max(12, area.width() * 68 / 100);
		int padX = ultra ? 4 : clampInt(layout.unit(), 7, 12);
		int textWidth = Math.max(8, maxBubbleWidth - padX * 2);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, messageFontSize(layout)));
		List<String> lines = wrapText(graphics.getFontMetrics(), message.text(), textWidth, maxMessageLines(layout));
		int lineHeight = graphics.getFontMetrics().getHeight();
		int bubbleWidth = Math.min(maxBubbleWidth, widestLineWidth(graphics, lines) + padX * 2);
		int bubbleX = support ? area.x() + avatarSize + avatarGap : area.right() - bubbleWidth;
		UiRect bubble = new UiRect(bubbleX, y, bubbleWidth, height);
		fillRoundedRect(graphics, bubble, ultra ? 7 : clampInt(layout.unit() * 2, 13, 22), support ? SUPPORT_BUBBLE : USER_BUBBLE);
		if (support) {
			drawSupportAvatar(graphics, app, new UiRect(area.x(), y + Math.max(0, height - avatarSize), avatarSize, avatarSize));
		}
		graphics.setColor(PRIMARY_TEXT);
		int textX = bubble.x() + padX;
		int textY = bubble.y() + (height - lineHeight * lines.size()) / 2 + graphics.getFontMetrics().getAscent();
		for (String line : lines) {
			graphics.drawString(line, textX, textY);
			textY += lineHeight;
		}
	}

	private static void drawSupportAvatar(Graphics2D graphics, MonitorApp app, UiRect rect) {
		if (rect.width() <= 0 || rect.height() <= 0) {
			return;
		}
		Shape previousClip = graphics.getClip();
		fillRoundedRect(graphics, rect, rect.height(), Color.WHITE);
		graphics.setClip(new Ellipse2D.Float(rect.x(), rect.y(), rect.width(), rect.height()));
		drawAppIcon(graphics, app, rect.inset(Math.max(0, rect.width() / 12)), 0);
		graphics.setClip(previousClip);
	}

	private static void drawInputBar(Graphics2D graphics, UiLayout layout, SupportVisualSnapshot snapshot) {
		UiRect input = supportInputBarRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		fillRoundedRect(graphics, input, 0, SCREEN_BACKGROUND);

		UiRect attach = supportAttachRect(layout);
		drawPaperclip(graphics, attach, TINKOFF_LIGHT_BLUE);

		UiRect field = supportInputRect(layout);
		fillRoundedRect(graphics, field, field.height(), new Color(232, 236, 241));
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, ultra ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() + 2, 12, 17)));
		graphics.setColor(snapshot.waitingForInput() ? new Color(88, 98, 112) : new Color(148, 156, 168));
		String placeholder = snapshot.waitingForInput() ? "Напишите в чат..." : "Сообщение";
		int textX = field.x() + Math.max(5, field.height() / 3);
		int textY = field.y() + (field.height() - graphics.getFontMetrics().getHeight()) / 2 + graphics.getFontMetrics().getAscent();
		graphics.drawString(placeholder, textX, textY);

		UiRect send = supportSendRect(layout);
		Color sendFill = snapshot.waitingForInput() ? TINKOFF_BLUE : new Color(159, 219, 244);
		fillRoundedRect(graphics, send, send.height(), sendFill);
		drawArrowUp(graphics, send, snapshot.waitingForInput() ? Color.WHITE : new Color(255, 255, 255, 178));
	}

	private static void drawPaperclip(Graphics2D graphics, UiRect rect, Color color) {
		Stroke previous = graphics.getStroke();
		Object previousAntialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(color);
			graphics.setStroke(new BasicStroke(Math.max(1.4F, rect.width() / 9.0F), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			float x = rect.x() + rect.width() * 0.38F;
			float y = rect.y() + rect.height() * 0.28F;
			float w = rect.width() * 0.35F;
			float h = rect.height() * 0.50F;
			Path2D.Float clip = new Path2D.Float();
			clip.moveTo(x + w, y + h * 0.18F);
			clip.lineTo(x + w, y + h * 0.72F);
			clip.curveTo(x + w, y + h, x, y + h, x, y + h * 0.72F);
			clip.lineTo(x, y + h * 0.22F);
			clip.curveTo(x, y - h * 0.08F, x + w * 0.75F, y - h * 0.08F, x + w * 0.75F, y + h * 0.22F);
			clip.lineTo(x + w * 0.75F, y + h * 0.68F);
			graphics.draw(clip);
		} finally {
			graphics.setStroke(previous);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialias);
		}
	}

	private static void drawArrowUp(Graphics2D graphics, UiRect rect, Color color) {
		Stroke previous = graphics.getStroke();
		Object previousAntialias = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			graphics.setColor(color);
			graphics.setStroke(new BasicStroke(Math.max(1.6F, rect.width() / 10.0F), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			float centerX = rect.x() + rect.width() / 2.0F;
			float top = rect.y() + rect.height() * 0.28F;
			float bottom = rect.y() + rect.height() * 0.70F;
			float wing = rect.width() * 0.18F;
			Path2D.Float arrow = new Path2D.Float();
			arrow.moveTo(centerX, bottom);
			arrow.lineTo(centerX, top);
			arrow.moveTo(centerX - wing, top + wing);
			arrow.lineTo(centerX, top);
			arrow.lineTo(centerX + wing, top + wing);
			graphics.draw(arrow);
		} finally {
			graphics.setStroke(previous);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, previousAntialias);
		}
	}

	private static int messageBubbleHeight(Graphics2D graphics, UiLayout layout, UiRect area, SupportMessageSnapshot message) {
		boolean support = message.fromSupport();
		boolean ultra = ultraCompactScreenLayout(layout);
		int avatarSize = support ? (ultra ? clampInt(layout.unit() * 2, 12, 16) : clampInt(layout.unit() * 3, 24, 36)) : 0;
		int avatarGap = support ? Math.max(2, layout.unit() / 3) : 0;
		int maxBubbleWidth = support
				? Math.max(12, area.width() * 66 / 100 - avatarSize - avatarGap)
				: Math.max(12, area.width() * 68 / 100);
		int padY = ultra ? 3 : clampInt(layout.unit() / 2, 5, 9);
		int padX = ultra ? 4 : clampInt(layout.unit(), 7, 12);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, messageFontSize(layout)));
		List<String> lines = wrapText(graphics.getFontMetrics(), message.text(), Math.max(8, maxBubbleWidth - padX * 2), maxMessageLines(layout));
		return Math.max(avatarSize, Math.max(1, lines.size()) * graphics.getFontMetrics().getHeight() + padY * 2);
	}

	private static UiRect supportCloseRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2 + 2, 12, 14) : clampInt(layout.unit() * 2 + 4, 22, 34);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		return new UiRect(canvas.x() + inset, canvas.y() + inset, size, size);
	}

	private static UiRect supportMessagesRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? Math.max(3, layout.unit() / 2) : clampInt(layout.unit(), 8, 18);
		int top = supportCloseRect(layout).bottom() + (ultra ? 3 : clampInt(layout.unit(), 8, 14));
		int bottom = supportInputBarRect(layout).y() - (ultra ? 2 : clampInt(layout.unit() / 2, 5, 9));
		return new UiRect(
				canvas.x() + inset,
				top,
				Math.max(1, canvas.width() - inset * 2),
				Math.max(1, bottom - top)
		);
	}

	private static UiRect supportInputBarRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int height = ultra ? clampInt(layout.unit() * 3, 20, 26) : clampInt(layout.unit() * 4, 40, 58);
		return new UiRect(canvas.x(), canvas.bottom() - height, canvas.width(), height);
	}

	private static UiRect supportAttachRect(UiLayout layout) {
		UiRect bar = supportInputBarRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2, 12, 17) : clampInt(layout.unit() * 2 + 4, 24, 34);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		return new UiRect(bar.x() + inset, bar.y() + (bar.height() - size) / 2, size, size);
	}

	private static UiRect supportSendRect(UiLayout layout) {
		UiRect bar = supportInputBarRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2, 12, 17) : clampInt(layout.unit() * 2 + 4, 24, 34);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		return new UiRect(bar.right() - inset - size, bar.y() + (bar.height() - size) / 2, size, size);
	}

	private static UiRect supportInputRect(UiLayout layout) {
		UiRect bar = supportInputBarRect(layout);
		UiRect attach = supportAttachRect(layout);
		UiRect send = supportSendRect(layout);
		int gap = compactGap(layout);
		int x = attach.right() + gap;
		int right = send.x() - gap;
		int height = ultraCompactScreenLayout(layout) ? clampInt(layout.unit() * 2, 14, 18) : clampInt(layout.unit() * 2 + 4, 28, 38);
		return new UiRect(x, bar.y() + (bar.height() - height) / 2, Math.max(1, right - x), height);
	}

	private static int compactGap(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit() / 2, 5, 10);
	}

	private static int messageFontSize(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? clampInt(layout.unit() - 1, 6, 8) : clampInt(layout.unit() + 1, 11, 16);
	}

	private static int maxMessageLines(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? 2 : 4;
	}

	private static int widestLineWidth(Graphics2D graphics, List<String> lines) {
		int width = 1;
		for (String line : lines) {
			width = Math.max(width, graphics.getFontMetrics().stringWidth(line == null ? "" : line));
		}
		return width;
	}

	private static void ensureGreetingLocked(SupportRuntimeState state) {
		if (state.greetingAdded) {
			return;
		}
		state.greetingAdded = true;
		state.messages.add(new SupportChatMessage(true, "Поддержка", "Здравствуйте! Опишите баг или идею по серверу.", System.currentTimeMillis()));
		state.version++;
	}

	private static SupportRuntimeState ensureState(ScreenRuntimeKey key) {
		return STATES.computeIfAbsent(key, ignored -> new SupportRuntimeState());
	}

	private static boolean waitingForInput(ScreenRuntimeKey key) {
		if (key == null) {
			return false;
		}
		for (ScreenRuntimeKey pendingKey : PENDING_INPUTS.values()) {
			if (Objects.equals(key, pendingKey)) {
				return true;
			}
		}
		return false;
	}

	private static int receiverCount(MinecraftServer server) {
		return TELEGRAM_CHAT_IDS.size() + onlineMinecraftOperatorCount(server);
	}

	private static int onlineMinecraftOperatorCount(MinecraftServer server) {
		if (server == null) {
			return 0;
		}
		int count = 0;
		for (UUID operatorId : MINECRAFT_SUPPORT_OPERATORS) {
			if (server.getPlayerList().getPlayer(operatorId) != null) {
				count++;
			}
		}
		return count;
	}

	private static boolean telegramConfigured() {
		return telegramToken != null && !telegramToken.isBlank();
	}

	private static String readConfiguredTelegramToken() {
		String env = System.getenv(TOKEN_ENV_NAME);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		String property = System.getProperty(TOKEN_PROPERTY_NAME);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		try {
			if (Files.exists(TOKEN_FILE)) {
				String fileToken = Files.readString(TOKEN_FILE, StandardCharsets.UTF_8).trim();
				if (!fileToken.isBlank()) {
					return fileToken;
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to read support Telegram bot token file", exception);
		}
		return "";
	}

	private static void ensureLoaded(MinecraftServer server) {
		if (!loaded) {
			load(server);
		}
	}

	private static void load(MinecraftServer server) {
		synchronized (PERSISTENCE_LOCK) {
			MINECRAFT_SUPPORT_OPERATORS.clear();
			TELEGRAM_CHAT_IDS.clear();
			loaded = true;
			if (server == null) {
				return;
			}
			Path path = statePath(server);
			if (!Files.exists(path)) {
				return;
			}
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (!(parsed instanceof JsonObject root)) {
					return;
				}
				JsonArray minecraftOperators = root.getAsJsonArray("minecraft_operators");
				if (minecraftOperators != null) {
					for (JsonElement element : minecraftOperators) {
						UUID id = parseUuid(element.getAsString());
						if (id != null) {
							MINECRAFT_SUPPORT_OPERATORS.add(id);
						}
					}
				}
				JsonArray telegramChats = root.getAsJsonArray("telegram_chats");
				if (telegramChats != null) {
					for (JsonElement element : telegramChats) {
						try {
							TELEGRAM_CHAT_IDS.add(element.getAsLong());
						} catch (RuntimeException ignored) {
						}
					}
				}
			} catch (IOException exception) {
				Lg2.LOGGER.warn("Failed to load support settings", exception);
			}
		}
	}

	private static void save(MinecraftServer server) {
		synchronized (PERSISTENCE_LOCK) {
			if (server == null || !loaded) {
				return;
			}
			Path path = statePath(server);
			try {
				Files.createDirectories(path.getParent());
				JsonObject root = new JsonObject();
				JsonArray minecraftOperators = new JsonArray();
				MINECRAFT_SUPPORT_OPERATORS.stream()
						.sorted(Comparator.comparing(UUID::toString))
						.forEach(id -> minecraftOperators.add(id.toString()));
				root.add("minecraft_operators", minecraftOperators);
				JsonArray telegramChats = new JsonArray();
				TELEGRAM_CHAT_IDS.stream()
						.sorted()
						.forEach(telegramChats::add);
				root.add("telegram_chats", telegramChats);
				try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
					GSON.toJson(root, writer);
				}
			} catch (IOException exception) {
				Lg2.LOGGER.warn("Failed to save support settings", exception);
			}
		}
	}

	private static Path statePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE_NAME);
	}

	private static String formatTelegramTicket(SupportTicket ticket, String message) {
		BlockPos pos = ticket.key().pos();
		String dimension = ticket.key().dimension() == null ? "unknown" : ticket.key().dimension().identifier().toString();
		return "Support #" + ticket.id()
				+ "\nИгрок: " + ticket.playerName()
				+ "\nЭкран: " + dimension + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
				+ "\n\nСообщение:\n" + truncateTelegramMessage(message)
				+ "\n\nОтвет: /reply " + ticket.id() + " текст";
	}

	private static String telegramStatusText(MinecraftServer server, long chatId) {
		return "Support: "
				+ (TELEGRAM_CHAT_IDS.contains(chatId) ? "приём включён" : "приём выключен")
				+ ", chats="
				+ TELEGRAM_CHAT_IDS.size()
				+ ", minecraft online="
				+ onlineMinecraftOperatorCount(server)
				+ ".";
	}

	private static String supportAuthorName(CommandSourceStack source) {
		try {
			ServerPlayer player = source.getPlayer();
			if (player != null) {
				return playerName(player);
			}
		} catch (RuntimeException ignored) {
		}
		return "Поддержка";
	}

	private static String playerName(ServerPlayer player) {
		if (player == null || player.getGameProfile() == null || player.getGameProfile().name() == null) {
			return "Игрок";
		}
		String name = player.getGameProfile().name().trim();
		return name.isBlank() ? "Игрок" : name;
	}

	private static String telegramAuthor(JsonObject message) {
		JsonObject from = message.getAsJsonObject("from");
		if (from == null) {
			return "Поддержка";
		}
		String firstName = readString(from, "first_name").trim();
		String username = readString(from, "username").trim();
		if (!firstName.isBlank()) {
			return firstName;
		}
		if (!username.isBlank()) {
			return "@" + username;
		}
		return "Поддержка";
	}

	private static long telegramChatId(JsonObject message) {
		JsonObject chat = message.getAsJsonObject("chat");
		return chat == null ? Long.MIN_VALUE : readLong(chat, "id", Long.MIN_VALUE);
	}

	private static boolean isSupportOnCommand(String text) {
		String normalized = normalizeTelegramCommand(text);
		return normalized.equals("/support_on") || normalized.equals("/on") || normalized.equals("/support on");
	}

	private static boolean isSupportOffCommand(String text) {
		String normalized = normalizeTelegramCommand(text);
		return normalized.equals("/support_off") || normalized.equals("/off") || normalized.equals("/support off");
	}

	private static boolean isSupportStatusCommand(String text) {
		String normalized = normalizeTelegramCommand(text);
		return normalized.equals("/support_status") || normalized.equals("/status") || normalized.equals("/support status");
	}

	private static String normalizeTelegramCommand(String text) {
		String trimmed = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
		int newline = trimmed.indexOf('\n');
		if (newline >= 0) {
			trimmed = trimmed.substring(0, newline).trim();
		}
		String first = firstCommandToken(trimmed);
		int at = first.indexOf('@');
		if (at >= 0) {
			first = first.substring(0, at);
		}
		if (first.equals("/support")) {
			String rest = trimmed.length() > 8 ? trimmed.substring(8).trim() : "";
			return "/support " + firstCommandToken(rest);
		}
		return first;
	}

	private static String firstCommandToken(String text) {
		String trimmed = text == null ? "" : text.trim();
		int split = firstWhitespace(trimmed);
		return split >= 0 ? trimmed.substring(0, split) : trimmed;
	}

	private static int firstWhitespace(String text) {
		if (text == null) {
			return -1;
		}
		for (int index = 0; index < text.length(); index++) {
			if (Character.isWhitespace(text.charAt(index))) {
				return index;
			}
		}
		return -1;
	}

	private static long parseTicketIdFromText(String text) {
		String marker = "Support #";
		int start = text == null ? -1 : text.indexOf(marker);
		if (start < 0) {
			return -1L;
		}
		int numberStart = start + marker.length();
		int numberEnd = numberStart;
		while (numberEnd < text.length() && Character.isDigit(text.charAt(numberEnd))) {
			numberEnd++;
		}
		Long value = parseLong(text.substring(numberStart, numberEnd));
		return value == null ? -1L : value;
	}

	private static Long parseLong(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(raw.trim());
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(raw.trim());
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static String truncateUiMessage(String value) {
		return truncateForChat(value, MAX_UI_MESSAGE_LENGTH);
	}

	private static String truncateTelegramMessage(String value) {
		return truncateForChat(value, MAX_TELEGRAM_MESSAGE_LENGTH);
	}

	private static String truncateForChat(String value, int maxLength) {
		String normalized = value == null ? "" : value.trim().replace('\r', ' ');
		if (normalized.length() <= maxLength) {
			return normalized;
		}
		return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
	}

	private static String readString(JsonObject object, String key) {
		if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
			return "";
		}
		try {
			return object.get(key).getAsString();
		} catch (RuntimeException ignored) {
			return "";
		}
	}

	private static int readInt(JsonObject object, String key, int fallback) {
		if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return object.get(key).getAsInt();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static long readLong(JsonObject object, String key, long fallback) {
		if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return object.get(key).getAsLong();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static final class SupportRuntimeState {
		private final List<SupportChatMessage> messages = new ArrayList<>();
		private long version;
		private boolean greetingAdded;
		private String statusText = "";
	}

	private record SupportChatMessage(boolean fromSupport, String author, String text, long createdAtMillis) {
	}

	private record SupportTicket(long id, ScreenRuntimeKey key, UUID playerUuid, String playerName, long createdAtMillis) {
	}

	private record TelegramMessageKey(long chatId, int messageId) {
	}

	private record TelegramReply(long ticketId, String message) {
	}
}
