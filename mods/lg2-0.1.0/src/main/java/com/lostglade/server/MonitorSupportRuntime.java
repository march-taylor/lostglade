package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lostglade.Lg2;
import com.lostglade.server.monitor.MonitorApp;
import com.lostglade.server.monitor.MonitorMediaApp;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
	private static final int MAX_STORED_MESSAGES = 200;
	private static final int MAX_SNAPSHOT_MESSAGES = 120;
	private static final int MAX_UI_MESSAGE_LENGTH = 700;
	private static final int MAX_TELEGRAM_MESSAGE_LENGTH = 3900;
	private static final int MAX_TELEGRAM_CAPTION_LENGTH = 1000;
	private static final int MAX_PENDING_ATTACHMENTS = 5;
	private static final long TELEGRAM_POLL_INTERVAL_MS = 1800L;
	private static final long TELEGRAM_REQUEST_TIMEOUT_SECONDS = 12L;
	private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
	private static final DateTimeFormatter MESSAGE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm").withZone(MOSCOW_ZONE);
	private static final Color TINKOFF_BLUE = new Color(66, 139, 249);
	private static final Color TINKOFF_LIGHT_BLUE = new Color(102, 163, 255);
	private static final Color SCREEN_BACKGROUND = new Color(246, 247, 248);
	private static final Color USER_BUBBLE = new Color(219, 236, 255);
	private static final Color SUPPORT_BUBBLE = new Color(242, 244, 247);
	private static final Color INPUT_BACKGROUND = new Color(234, 236, 238);
	private static final Color PRIMARY_TEXT = new Color(18, 18, 18);
	private static final Color SECONDARY_TEXT = new Color(104, 112, 123);
	private static final Color META_TEXT = new Color(134, 143, 154);

	private static final ConcurrentMap<ScreenRuntimeKey, SupportRuntimeState> STATES = new ConcurrentHashMap<>();
	private static final ConcurrentMap<UUID, ScreenRuntimeKey> PENDING_INPUTS = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Long, SupportTicket> TICKETS = new ConcurrentHashMap<>();
	private static final ConcurrentMap<TelegramMessageKey, Long> TELEGRAM_MESSAGE_TICKETS = new ConcurrentHashMap<>();
	private static final ConcurrentMap<Long, Long> LAST_TELEGRAM_TICKETS = new ConcurrentHashMap<>();
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
			return new SupportVisualSnapshot(0L, List.of(), List.of(), List.of(), false, false, telegramConfigured(), receiverCount(server), "", 0, 0, 0);
		}
		SupportRuntimeState state = ensureState(component.runtimeKey());
		List<SupportMessageSnapshot> messages;
		List<SupportAttachmentSnapshot> pendingAttachments;
		List<SupportAttachment> pendingAttachmentModels;
		long version;
		String statusText;
		int scrollOffset;
		int attachmentPickerScroll;
		int totalMessageCount;
		boolean attachmentPickerOpen;
		synchronized (state) {
			ensureGreetingLocked(state);
			trimHistoryLocked(state);
			totalMessageCount = state.messages.size();
			state.scrollOffset = clampInt(state.scrollOffset, 0, Math.max(0, totalMessageCount - 1));
			state.attachmentPickerScroll = Math.max(0, state.attachmentPickerScroll);
			int from = Math.max(0, state.messages.size() - MAX_SNAPSHOT_MESSAGES);
			messages = state.messages.subList(from, state.messages.size()).stream()
					.map(message -> new SupportMessageSnapshot(
							message.fromSupport(),
							message.author(),
							message.text(),
							message.createdAtMillis(),
							message.ticketId(),
							message.attachments().stream()
									.map(MonitorSupportRuntime::attachmentSnapshot)
									.toList(),
							message.delivered(),
							message.read()
					))
					.toList();
			pendingAttachmentModels = List.copyOf(state.pendingAttachments);
			pendingAttachments = state.pendingAttachments.stream()
					.map(MonitorSupportRuntime::attachmentSnapshot)
					.toList();
			version = state.version;
			statusText = state.statusText;
			scrollOffset = state.scrollOffset;
			attachmentPickerScroll = state.attachmentPickerScroll;
			attachmentPickerOpen = state.attachmentPickerOpen;
		}
		List<SupportGalleryFileSnapshot> galleryFiles = attachmentPickerOpen
				? supportGalleryFilesSnapshot(server, component.runtimeKey(), pendingAttachmentModels)
				: List.of();
		return new SupportVisualSnapshot(
				version,
				messages,
				pendingAttachments,
				galleryFiles,
				attachmentPickerOpen,
				waitingForInput(component.runtimeKey()),
				telegramConfigured(),
				receiverCount(server),
				statusText,
				scrollOffset,
				attachmentPickerScroll,
				totalMessageCount
		);
	}

	static void drawScreen(Graphics2D graphics, UiLayout layout, MonitorApp app, ScreenRuntimeKey key, SupportVisualSnapshot snapshot) {
		if (graphics == null || layout == null) {
			return;
		}
		SupportVisualSnapshot safeSnapshot = snapshot == null
				? new SupportVisualSnapshot(0L, List.of(), List.of(), List.of(), false, false, telegramConfigured(), 0, "", 0, 0, 0)
				: snapshot;
		UiRect canvas = mediaCanvasRect(layout);
		fillRoundedRect(graphics, canvas, 0, SCREEN_BACKGROUND);
		UiRect shell = supportShellRect(layout);
		drawMessengerShell(graphics, shell);
		Shape previousClip = graphics.getClip();
		graphics.setClip(new RoundRectangle2D.Float(shell.x(), shell.y(), shell.width(), shell.height(), 12, 12));
		try {
			drawHeader(graphics, layout, app);
			drawMessages(graphics, layout, app, key, safeSnapshot);
			if (safeSnapshot.attachmentPickerOpen()) {
				drawAttachmentPicker(graphics, layout, key, safeSnapshot);
			}
			drawInputBar(graphics, layout, safeSnapshot);
		} finally {
			graphics.setClip(previousClip);
		}
	}

	static boolean handleTouch(MinecraftServer server, ServerPlayer player, ScreenComponent component, UiLayout layout, UiPoint touchPoint) {
		if (server == null || player == null || component == null || layout == null || touchPoint == null) {
			return true;
		}
		if (supportCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			PENDING_INPUTS.remove(player.getUUID());
			return false;
		}
		SupportRuntimeState state = ensureState(component.runtimeKey());
		List<SupportAttachment> pendingAttachments;
		boolean attachmentPickerOpen;
		synchronized (state) {
			pendingAttachments = List.copyOf(state.pendingAttachments);
			attachmentPickerOpen = state.attachmentPickerOpen;
		}
		boolean inputExpanded = !pendingAttachments.isEmpty();
		for (int index = 0; index < pendingAttachments.size(); index++) {
			if (supportPendingAttachmentRemoveRect(layout, pendingAttachments.size(), index).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (state) {
					if (index >= 0 && index < state.pendingAttachments.size()) {
						state.pendingAttachments.remove(index);
						state.statusText = "";
						state.version++;
					}
				}
				save(server);
				requestRuntimeRender(server, component.runtimeKey());
				return true;
			}
		}
		if (supportAttachRect(layout, inputExpanded).contains(touchPoint.x(), touchPoint.y())) {
			toggleAttachmentPicker(server, component.runtimeKey(), state);
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (attachmentPickerOpen) {
			if (supportAttachmentPickerCloseRect(layout, inputExpanded).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (state) {
					state.attachmentPickerOpen = false;
					state.statusText = "";
					state.version++;
				}
				save(server);
				requestRuntimeRender(server, component.runtimeKey());
				return true;
			}
			int galleryIndex = supportGalleryFileIndexAt(server, component.runtimeKey(), layout, inputExpanded, touchPoint);
			if (galleryIndex >= 0) {
				if (toggleGalleryAttachment(server, player, component.runtimeKey(), state, galleryIndex)) {
					requestRuntimeRender(server, component.runtimeKey());
				}
				return true;
			}
			if (supportAttachmentPickerRect(layout, inputExpanded).contains(touchPoint.x(), touchPoint.y())) {
				return true;
			}
		}
		if (supportSendRect(layout, inputExpanded).contains(touchPoint.x(), touchPoint.y())) {
			boolean hasAttachments;
			synchronized (state) {
				hasAttachments = !state.pendingAttachments.isEmpty();
			}
			if (hasAttachments) {
				submitSupportMessage(server, player, component.runtimeKey(), "");
				return true;
			}
			PENDING_INPUTS.put(player.getUUID(), component.runtimeKey());
			synchronized (state) {
				state.statusText = "Ждём сообщение в чате";
				state.version++;
			}
			player.displayClientMessage(Component.literal("Напиши сообщение поддержки в чат. Оно не будет отправлено всем игрокам."), true);
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (supportInputRect(layout, inputExpanded).contains(touchPoint.x(), touchPoint.y())) {
			PENDING_INPUTS.put(player.getUUID(), component.runtimeKey());
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

	static boolean onPlayerHotbarScroll(ServerPlayer player, int previousSlot, int currentSlot) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ObservedSupportUiTarget target = findObservedSupportUiTarget(player, level);
		if (target == null) {
			return false;
		}
		int delta = normalizeHotbarDelta(previousSlot, currentSlot);
		if (delta == 0) {
			return false;
		}
		SupportRuntimeState state = ensureState(target.component().runtimeKey());
		boolean changed;
		synchronized (state) {
			if (state.attachmentPickerOpen) {
				int galleryCount = supportGalleryFileCount(level.getServer(), target.component().runtimeKey());
				int maxScroll = supportAttachmentPickerMaxScroll(target.layout(), galleryCount, !state.pendingAttachments.isEmpty());
				int previous = state.attachmentPickerScroll;
				state.attachmentPickerScroll = clampInt(state.attachmentPickerScroll + delta, 0, maxScroll);
				changed = previous != state.attachmentPickerScroll;
			} else {
				int previous = state.scrollOffset;
				state.scrollOffset = clampInt(state.scrollOffset + delta, 0, Math.max(0, state.messages.size() - 1));
				changed = previous != state.scrollOffset;
			}
			if (changed) {
				state.version++;
			}
		}
		if (changed) {
			MinecraftServer server = level.getServer();
			save(server);
			requestRuntimeRender(server, target.component().runtimeKey());
		}
		return changed;
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
			SupportRuntimeState state = ensureState(key);
			boolean hasAttachments;
			synchronized (state) {
				hasAttachments = !state.pendingAttachments.isEmpty();
			}
			if (!hasAttachments) {
				PENDING_INPUTS.put(sender.getUUID(), key);
				sender.displayClientMessage(Component.literal("Сообщение пустое. Напиши текст обращения в чат."), true);
				requestRuntimeRender(server, key);
				return true;
			}
		}
		submitSupportMessage(server, sender, key, text);
		return true;
	}

	private static void submitSupportMessage(MinecraftServer server, ServerPlayer sender, ScreenRuntimeKey key, String text) {
		if (server == null || sender == null || key == null) {
			return;
		}
		SupportRuntimeState state = ensureState(key);
		List<SupportAttachment> attachments;
		long ticketId = NEXT_TICKET_ID.getAndIncrement();
		String playerName = playerName(sender);
		SupportTicket ticket = new SupportTicket(ticketId, key, sender.getUUID(), playerName, System.currentTimeMillis());
		TICKETS.put(ticketId, ticket);
		synchronized (state) {
			ensureGreetingLocked(state);
			attachments = List.copyOf(state.pendingAttachments);
			state.pendingAttachments.clear();
			state.attachmentPickerOpen = false;
			state.messages.add(new SupportChatMessage(
					false,
					playerName,
					truncateUiMessage(text),
					System.currentTimeMillis(),
					ticketId,
					attachments,
					true,
					false
			));
			state.scrollOffset = 0;
			state.statusText = "";
			trimHistoryLocked(state);
			state.version++;
		}
		sender.displayClientMessage(Component.literal("Обращение отправлено в поддержку."), true);
		notifyMinecraftOperators(server, ticket, text, attachments);
		notifyTelegramOperators(server, ticket, text, attachments);
		save(server);
		requestRuntimeRender(server, key);
	}

	private static void toggleAttachmentPicker(MinecraftServer server, ScreenRuntimeKey key, SupportRuntimeState state) {
		ensureSupportGalleryState(server, key);
		synchronized (state) {
			state.attachmentPickerOpen = !state.attachmentPickerOpen;
			state.statusText = "";
			state.version++;
		}
		save(server);
	}

	private static boolean toggleGalleryAttachment(MinecraftServer server, ServerPlayer player, ScreenRuntimeKey key, SupportRuntimeState state, int galleryIndex) {
		GalleryItem item = supportGalleryItem(server, key, galleryIndex);
		SupportAttachment attachment = attachmentFromGalleryItem(galleryIndex, item);
		if (attachment == null) {
			if (player != null) {
				player.displayClientMessage(Component.literal("Этот элемент галереи нельзя прикрепить как файл."), true);
			}
			return true;
		}
		synchronized (state) {
			int existingIndex = existingAttachmentIndexLocked(state, attachment);
			if (existingIndex >= 0) {
				state.pendingAttachments.remove(existingIndex);
				state.statusText = "";
				state.version++;
				save(server);
				return true;
			}
			if (state.pendingAttachments.size() >= MAX_PENDING_ATTACHMENTS) {
				if (player != null) {
					player.displayClientMessage(Component.literal("Можно прикрепить до " + MAX_PENDING_ATTACHMENTS + " файлов."), true);
				}
				return false;
			}
			state.pendingAttachments.add(attachment);
			state.statusText = "";
			state.version++;
		}
		save(server);
		return true;
	}

	private static MediaRuntimeState ensureSupportGalleryState(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return null;
		}
		MediaRuntimeState mediaState = MEDIA_STATES.computeIfAbsent(
				key,
				ignored -> MediaRuntimeState.fresh(ScreenViewMode.GALLERY, "", () -> MonitorScreenMediaLoadResults.onMediaProgressChanged(server, key))
		);
		MonitorScreenMediaHydration.ensureGalleryStateHydrated(server, key, mediaState);
		return mediaState;
	}

	private static List<SupportGalleryFileSnapshot> supportGalleryFilesSnapshot(MinecraftServer server, ScreenRuntimeKey key, List<SupportAttachment> pendingAttachments) {
		MediaRuntimeState mediaState = ensureSupportGalleryState(server, key);
		if (mediaState == null) {
			return List.of();
		}
		List<SupportGalleryFileSnapshot> files = new ArrayList<>();
		synchronized (mediaState) {
			for (int index = 0; index < mediaState.galleryItems.size(); index++) {
				GalleryItem item = mediaState.galleryItems.get(index);
				if (!supportGalleryAttachable(item)) {
					continue;
				}
				GalleryItemKind kind = MonitorScreenGalleryRuntime.effectiveGalleryItemKind(item);
				files.add(new SupportGalleryFileSnapshot(
						index,
						safeAttachmentTitle(item.title(), index),
						item.subtitle() == null ? "" : item.subtitle(),
						item.url() == null ? "" : item.url(),
						item.localMediaKey() == null ? "" : item.localMediaKey(),
						kind,
						item.preview(),
						pendingAttachments != null && pendingAttachments.stream().anyMatch(attachment -> attachmentMatchesGalleryItem(attachment, item))
				));
			}
		}
		return files;
	}

	private static int supportGalleryFileCount(MinecraftServer server, ScreenRuntimeKey key) {
		MediaRuntimeState mediaState = ensureSupportGalleryState(server, key);
		if (mediaState == null) {
			return 0;
		}
		int count = 0;
		synchronized (mediaState) {
			for (GalleryItem item : mediaState.galleryItems) {
				if (supportGalleryAttachable(item)) {
					count++;
				}
			}
		}
		return count;
	}

	private static GalleryItem supportGalleryItem(MinecraftServer server, ScreenRuntimeKey key, int galleryIndex) {
		MediaRuntimeState mediaState = ensureSupportGalleryState(server, key);
		if (mediaState == null) {
			return null;
		}
		synchronized (mediaState) {
			if (galleryIndex < 0 || galleryIndex >= mediaState.galleryItems.size()) {
				return null;
			}
			return mediaState.galleryItems.get(galleryIndex);
		}
	}

	private static boolean supportGalleryAttachable(GalleryItem item) {
		if (item == null) {
			return false;
		}
		GalleryItemKind kind = MonitorScreenGalleryRuntime.effectiveGalleryItemKind(item);
		if (kind == GalleryItemKind.LIVE_CAMERA) {
			return false;
		}
		return (item.localMediaKey() != null && !item.localMediaKey().isBlank())
				|| (item.url() != null && !item.url().isBlank());
	}

	private static int existingAttachmentIndexLocked(SupportRuntimeState state, SupportAttachment attachment) {
		if (state == null || attachment == null) {
			return -1;
		}
		for (int index = 0; index < state.pendingAttachments.size(); index++) {
			if (sameAttachment(state.pendingAttachments.get(index), attachment)) {
				return index;
			}
		}
		return -1;
	}

	private static boolean attachmentMatchesGalleryItem(SupportAttachment attachment, GalleryItem item) {
		return attachment != null && item != null && Objects.equals(attachmentIdentity(attachment), galleryItemIdentity(item));
	}

	private static boolean sameAttachment(SupportAttachment left, SupportAttachment right) {
		return left != null && right != null && Objects.equals(attachmentIdentity(left), attachmentIdentity(right));
	}

	private static String attachmentIdentity(SupportAttachment attachment) {
		if (attachment == null) {
			return "";
		}
		if (attachment.localMediaKey() != null && !attachment.localMediaKey().isBlank()) {
			return "local:" + attachment.localMediaKey().trim();
		}
		if (attachment.url() != null && !attachment.url().isBlank()) {
			return "url:" + attachment.url().trim();
		}
		return attachment.id() == null ? "" : "id:" + attachment.id();
	}

	private static String galleryItemIdentity(GalleryItem item) {
		if (item == null) {
			return "";
		}
		if (item.localMediaKey() != null && !item.localMediaKey().isBlank()) {
			return "local:" + item.localMediaKey().trim();
		}
		if (item.url() != null && !item.url().isBlank()) {
			return "url:" + item.url().trim();
		}
		return "";
	}

	private static void start(MinecraftServer server) {
		activeServer = server;
		load(server);
		telegramToken = readConfiguredTelegramToken();
		if (telegramConfigured()) {
			startTelegramPolling(server);
			publishTelegramCommands();
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

	private static void notifyMinecraftOperators(MinecraftServer server, SupportTicket ticket, String message, List<SupportAttachment> attachments) {
		if (server == null || ticket == null) {
			return;
		}
		String attachmentText = attachments == null || attachments.isEmpty() ? "" : " +" + attachments.size() + " влож.";
		String text = "Поддержка от " + ticket.playerName() + attachmentText + ": " + truncateForChat(message, 220)
				+ " Ответ: /support reply " + ticket.id() + " <текст>";
		for (UUID operatorId : MINECRAFT_SUPPORT_OPERATORS) {
			ServerPlayer operator = server.getPlayerList().getPlayer(operatorId);
			if (operator != null) {
				operator.displayClientMessage(Component.literal(text), false);
			}
		}
	}

	private static void notifyTelegramOperators(MinecraftServer server, SupportTicket ticket, String message, List<SupportAttachment> attachments) {
		if (ticket == null || !telegramConfigured() || TELEGRAM_CHAT_IDS.isEmpty()) {
			return;
		}
		List<SupportAttachment> safeAttachments = attachments == null ? List.of() : List.copyOf(attachments);
		for (Long chatId : TELEGRAM_CHAT_IDS) {
			if (chatId == null) {
				continue;
			}
			runTelegramTask(() -> {
				List<Integer> messageIds = sendTelegramTicketInternal(chatId, ticket, message, safeAttachments);
				boolean stored = false;
				for (Integer messageId : messageIds) {
					if (messageId != null && messageId > 0) {
						TELEGRAM_MESSAGE_TICKETS.put(new TelegramMessageKey(chatId, messageId), ticket.id());
						stored = true;
					}
				}
				if (stored) {
					LAST_TELEGRAM_TICKETS.put(chatId, ticket.id());
					save(server);
				}
			});
		}
	}

	private static void addSupportReply(MinecraftServer server, long ticketId, String author, String message) {
		SupportTicket ticket = TICKETS.get(ticketId);
		if (ticket == null) {
			return;
		}
		markTicketRead(server, ticketId);
		SupportRuntimeState state = ensureState(ticket.key());
		synchronized (state) {
			ensureGreetingLocked(state);
			state.messages.add(new SupportChatMessage(
					true,
					author == null || author.isBlank() ? "Поддержка" : author,
					truncateUiMessage(message),
					System.currentTimeMillis(),
					ticketId,
					List.of(),
					true,
					true
			));
			state.scrollOffset = 0;
			state.statusText = "Ответ поддержки";
			trimHistoryLocked(state);
			state.version++;
		}
		if (server != null) {
			ServerPlayer player = server.getPlayerList().getPlayer(ticket.playerUuid());
			if (player != null) {
				player.displayClientMessage(Component.literal("Поддержка ответила на обращение #" + ticketId + "."), true);
			}
			requestRuntimeRender(server, ticket.key());
			save(server);
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
		if (lower.equals("/start") || isSupportOnCommand(text)) {
			ensureLoaded(server);
			TELEGRAM_CHAT_IDS.add(chatId);
			save(server);
			return;
		}
		if (lower.equals("/stop") || isSupportOffCommand(text)) {
			ensureLoaded(server);
			TELEGRAM_CHAT_IDS.remove(chatId);
			LAST_TELEGRAM_TICKETS.remove(chatId);
			save(server);
			return;
		}
		if (lower.equals("/help")) {
			return;
		}
		if (isSupportStatusCommand(text)) {
			sendTelegramMessage(chatId, telegramStatusText(server, chatId));
			return;
		}
		if (!TELEGRAM_CHAT_IDS.contains(chatId)) {
			return;
		}
		TelegramReply reply = parseTelegramReply(chatId, message, text);
		if (reply == null) {
			return;
		}
		String author = telegramAuthor(message);
		int incomingMessageId = readInt(message, "message_id", -1);
		MinecraftServer targetServer = activeServer != null ? activeServer : server;
		if (targetServer == null) {
			return;
		}
		targetServer.execute(() -> {
			SupportTicket ticket = TICKETS.get(reply.ticketId());
			if (ticket == null) {
				return;
			}
			addSupportReply(targetServer, reply.ticketId(), author, reply.message());
			if (incomingMessageId > 0) {
				setTelegramMessageReaction(chatId, incomingMessageId);
			}
		});
	}

	private static TelegramReply parseTelegramReply(long chatId, JsonObject message, String text) {
		String trimmed = text == null ? "" : text.trim();
		if (trimmed.isBlank() || trimmed.startsWith("/")) {
			return null;
		}
		JsonObject replyTo = message.getAsJsonObject("reply_to_message");
		long ticketId = -1L;
		if (replyTo != null) {
			int replyMessageId = readInt(replyTo, "message_id", -1);
			Long mappedTicket = TELEGRAM_MESSAGE_TICKETS.get(new TelegramMessageKey(chatId, replyMessageId));
			if (mappedTicket != null) {
				ticketId = mappedTicket;
			}
		}
		if (ticketId <= 0L) {
			Long lastTicket = LAST_TELEGRAM_TICKETS.get(chatId);
			if (lastTicket != null) {
				ticketId = lastTicket;
			}
		}
		if (ticketId <= 0L) {
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

	private static List<Integer> sendTelegramTicketInternal(long chatId, SupportTicket ticket, String message, List<SupportAttachment> attachments) {
		List<SupportAttachment> safeAttachments = attachments == null ? List.of() : attachments;
		List<SupportAttachment> localAttachments = safeAttachments.stream()
				.filter(attachment -> telegramAttachmentPath(attachment) != null)
				.toList();
		if (localAttachments.isEmpty()) {
			return List.of(sendTelegramMessageInternal(chatId, formatTelegramTicket(ticket, message, safeAttachments)));
		}
		List<Integer> messageIds = new ArrayList<>();
		boolean captionSent = false;
		for (SupportAttachment attachment : localAttachments) {
			Path file = telegramAttachmentPath(attachment);
			if (file == null) {
				continue;
			}
			String caption = captionSent ? "" : truncateTelegramCaption(formatTelegramTicket(ticket, message, List.of()));
			int messageId = sendTelegramDocumentInternal(chatId, file, telegramFileName(attachment, file), caption);
			if (messageId > 0) {
				messageIds.add(messageId);
				captionSent = true;
			}
		}
		if (messageIds.isEmpty()) {
			return List.of(sendTelegramMessageInternal(chatId, formatTelegramTicket(ticket, message, safeAttachments)));
		}
		return messageIds;
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

	private static int sendTelegramDocumentInternal(long chatId, Path file, String fileName, String caption) {
		if (!telegramConfigured() || file == null || !Files.isRegularFile(file)) {
			return -1;
		}
		String boundary = "----lg2-support-" + UUID.randomUUID();
		try {
			ByteArrayOutputStream body = new ByteArrayOutputStream();
			writeMultipartField(body, boundary, "chat_id", Long.toString(chatId));
			if (caption != null && !caption.isBlank()) {
				writeMultipartField(body, boundary, "caption", truncateTelegramCaption(caption));
			}
			writeMultipartFile(body, boundary, "document", file, fileName);
			body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
			HttpRequest request = HttpRequest.newBuilder(telegramApiUri("sendDocument"))
					.timeout(Duration.ofSeconds(TELEGRAM_REQUEST_TIMEOUT_SECONDS))
					.header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
					.build();
			HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return -1;
			}
			return readTelegramMessageId(response.body());
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to send support Telegram attachment", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException exception) {
			Lg2.LOGGER.warn("Failed to build support Telegram attachment", exception);
		}
		return -1;
	}

	private static void writeMultipartField(ByteArrayOutputStream body, String boundary, String name, String value) throws IOException {
		body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(("Content-Disposition: form-data; name=\"" + escapeMultipartToken(name) + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
		body.write("\r\n".getBytes(StandardCharsets.UTF_8));
	}

	private static void writeMultipartFile(ByteArrayOutputStream body, String boundary, String name, Path file, String fileName) throws IOException {
		String contentType = Files.probeContentType(file);
		if (contentType == null || contentType.isBlank()) {
			contentType = "application/octet-stream";
		}
		body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(("Content-Disposition: form-data; name=\"" + escapeMultipartToken(name)
				+ "\"; filename=\"" + escapeMultipartToken(fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		body.write(Files.readAllBytes(file));
		body.write("\r\n".getBytes(StandardCharsets.UTF_8));
	}

	private static String escapeMultipartToken(String value) {
		return (value == null || value.isBlank() ? "file" : value)
				.replace("\\", "_")
				.replace("\"", "_")
				.replace("\r", "_")
				.replace("\n", "_");
	}

	private static int readTelegramMessageId(String body) {
		try {
			JsonElement parsed = JsonParser.parseString(body);
			if (!(parsed instanceof JsonObject root) || !root.has("ok") || !root.get("ok").getAsBoolean()) {
				return -1;
			}
			JsonObject result = root.getAsJsonObject("result");
			return result != null ? readInt(result, "message_id", -1) : -1;
		} catch (RuntimeException ignored) {
			return -1;
		}
	}

	private static void publishTelegramCommands() {
		if (!telegramConfigured()) {
			return;
		}
		runTelegramTask(() -> postTelegramForm(
				"setMyCommands",
				Map.of("commands", "[{\"command\":\"start\",\"description\":\"Подписаться\"},{\"command\":\"stop\",\"description\":\"Отписаться\"}]")
		));
	}

	private static void setTelegramMessageReaction(long chatId, int messageId) {
		if (!telegramConfigured() || messageId <= 0) {
			return;
		}
		runTelegramTask(() -> postTelegramForm(
				"setMessageReaction",
				Map.of(
						"chat_id", Long.toString(chatId),
						"message_id", Integer.toString(messageId),
						"reaction", "[{\"type\":\"emoji\",\"emoji\":\"\u2705\"}]"
				)
		));
	}

	private static JsonObject postTelegramForm(String method, Map<String, String> values) {
		if (!telegramConfigured()) {
			return null;
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(telegramApiUri(method))
					.timeout(Duration.ofSeconds(TELEGRAM_REQUEST_TIMEOUT_SECONDS))
					.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
					.POST(HttpRequest.BodyPublishers.ofString(formBody(values), StandardCharsets.UTF_8))
					.build();
			HttpResponse<String> response = TELEGRAM_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return null;
			}
			JsonElement parsed = JsonParser.parseString(response.body());
			if (parsed instanceof JsonObject root && root.has("ok") && root.get("ok").getAsBoolean()) {
				return root;
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to call support Telegram bot API", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException exception) {
			Lg2.LOGGER.warn("Failed to build support Telegram bot API request", exception);
		}
		return null;
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

	private static void drawMessengerShell(Graphics2D graphics, UiRect shell) {
		if (graphics == null || shell == null) {
			return;
		}
		fillRoundedRect(graphics, offsetRect(shell, 4, 0), 12, new Color(0, 0, 0, 18));
		fillRoundedRect(graphics, offsetRect(shell, 0, 3), 12, new Color(0, 0, 0, 10));
		fillRoundedRect(graphics, shell, 12, Color.WHITE);
	}

	private static void drawHeader(Graphics2D graphics, UiLayout layout, MonitorApp app) {
		UiRect shell = supportShellRect(layout);
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
		if (!ultra && subtitleY < shell.bottom()) {
			graphics.drawString("Мы рядом 24/7", textX, subtitleY);
		}
	}

	private static void drawMessages(Graphics2D graphics, UiLayout layout, MonitorApp app, ScreenRuntimeKey key, SupportVisualSnapshot snapshot) {
		UiRect area = supportMessagesRect(layout, !snapshot.pendingAttachments().isEmpty());
		List<SupportMessageSnapshot> messages = snapshot.messages() == null ? List.of() : snapshot.messages();
		int gap = compactGap(layout);
		int maxScroll = Math.max(0, messages.size() - 1);
		MonitorScrollAnimationSystem.ScrollVisualState visualScroll = MonitorScrollAnimationSystem.sample(
				key,
				MonitorScrollAnimationSystem.ScrollChannel.SUPPORT_CHAT,
				snapshot.scrollOffset(),
				maxScroll
		);
		int anchor = visualScroll.anchorIndex();
		int newestIndex = messages.size() - 1 - clampInt(anchor, 0, maxScroll);
		int y = area.bottom();
		if (newestIndex >= 0 && newestIndex < messages.size() && visualScroll.animated()) {
			int currentHeight = messageBubbleHeight(graphics, layout, area, messages.get(newestIndex));
			y += (int) Math.round(visualScroll.fraction() * (currentHeight + gap));
		}
		for (int index = newestIndex; index >= 0; index--) {
			SupportMessageSnapshot message = messages.get(index);
			if (!messageVisible(message)) {
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
		drawChatScrollbar(graphics, layout, area, messages.size(), visualScroll.displayValue());
	}

	private static void drawAttachmentPicker(Graphics2D graphics, UiLayout layout, ScreenRuntimeKey key, SupportVisualSnapshot snapshot) {
		boolean expanded = snapshot.pendingAttachments() != null && !snapshot.pendingAttachments().isEmpty();
		UiRect panel = supportAttachmentPickerRect(layout, expanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		int arc = ultra ? 8 : clampInt(layout.unit() * 2, 12, 18);
		fillRoundedRect(graphics, panel, arc, new Color(255, 255, 255, 246));
		strokeRoundedRect(graphics, panel, arc, 1.0F, new Color(0, 16, 36, 18));

		UiRect header = supportAttachmentPickerHeaderRect(layout, expanded);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, ultra ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() + 3, 13, 18)));
		graphics.setColor(PRIMARY_TEXT);
		int titleX = header.x() + (ultra ? 4 : clampInt(layout.unit(), 8, 14));
		int titleY = header.y() + (header.height() - graphics.getFontMetrics().getHeight()) / 2 + graphics.getFontMetrics().getAscent();
		graphics.drawString("Галерея", titleX, titleY);
		UiRect close = supportAttachmentPickerCloseRect(layout, expanded);
		fillRoundedRect(graphics, close, close.height(), new Color(244, 247, 250));
		drawPlayerUiIcon(graphics, close.inset(Math.max(2, close.width() / 4)), PlayerUiIcon.CLOSE, TINKOFF_BLUE);

		List<SupportGalleryFileSnapshot> files = snapshot.galleryFiles() == null ? List.of() : snapshot.galleryFiles();
		UiRect grid = supportAttachmentPickerGridRect(layout, expanded);
		int columns = supportAttachmentPickerColumns(layout);
		int visibleRows = supportAttachmentPickerVisibleRows(layout, expanded);
		int totalRows = supportAttachmentPickerTotalRows(files.size(), layout);
		int maxScroll = Math.max(0, totalRows - visibleRows);
		MonitorScrollAnimationSystem.ScrollVisualState visualScroll = MonitorScrollAnimationSystem.sample(
				key,
				MonitorScrollAnimationSystem.ScrollChannel.SUPPORT_ATTACHMENT_PICKER,
				snapshot.attachmentPickerScroll(),
				maxScroll
		);
		int scroll = clampInt(visualScroll.anchorIndex(), 0, maxScroll);
		if (files.isEmpty()) {
			drawCenteredText(graphics, "Галерея пуста", grid, SECONDARY_TEXT, Font.BOLD, ultra ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() + 1, 12, 15));
			return;
		}

		Shape previousClip = graphics.getClip();
		graphics.clipRect(grid.x(), grid.y(), grid.width(), grid.height());
		try {
			int rowStep = supportGalleryFileCardHeight(layout, expanded) + supportAttachmentPickerGap(layout);
			int rowOffset = -(int) Math.round(visualScroll.fraction() * rowStep);
			int rowCount = Math.min(Math.max(0, totalRows - scroll), visibleRows + (visualScroll.animated() && scroll + visibleRows < totalRows ? 1 : 0));
			for (int row = 0; row < rowCount; row++) {
				for (int column = 0; column < columns; column++) {
					int position = (scroll + row) * columns + column;
					if (position < 0 || position >= files.size()) {
						continue;
					}
					drawSupportGalleryFileCard(graphics, layout, offsetRect(supportGalleryFileCardRect(layout, expanded, row, column), 0, rowOffset), files.get(position));
				}
			}
		} finally {
			graphics.setClip(previousClip);
		}
		drawAttachmentPickerScrollbar(graphics, layout, grid, visualScroll.displayValue(), visibleRows, totalRows);
	}

	private static void drawSupportGalleryFileCard(Graphics2D graphics, UiLayout layout, UiRect rect, SupportGalleryFileSnapshot file) {
		boolean ultra = ultraCompactScreenLayout(layout);
		int arc = ultra ? 6 : clampInt(layout.unit() + 4, 10, 16);
		Color fill = file.selected() ? new Color(219, 236, 255) : new Color(246, 247, 248);
		fillRoundedRect(graphics, rect, arc, fill);
		strokeRoundedRect(graphics, rect, arc, file.selected() ? 1.6F : 1.0F, file.selected() ? TINKOFF_BLUE : new Color(0, 16, 36, 18));

		UiRect preview = supportGalleryFilePreviewRect(layout, rect);
		if (file.preview() != null) {
			drawScaledImage(graphics, file.preview(), preview, MediaScaleMode.FILL);
		} else {
			fillRoundedRect(graphics, preview, arc, Color.WHITE);
			drawPlayerUiIcon(graphics, preview.inset(Math.max(4, preview.width() / 4)), supportGalleryFileIcon(file.kind()), new Color(126, 138, 150));
		}
		UiRect badge = supportGalleryFileTypeBadgeRect(layout, preview);
		fillRoundedRect(graphics, badge, badge.height(), new Color(8, 12, 16, 150));
		drawPlayerUiIcon(graphics, badge.inset(Math.max(1, badge.width() / 5)), supportGalleryFileIcon(file.kind()), Color.WHITE);
		if (file.selected()) {
			UiRect check = supportGalleryFileSelectedRect(layout, preview);
			fillRoundedRect(graphics, check, check.height(), TINKOFF_BLUE);
			drawPlayerUiIcon(graphics, check.inset(Math.max(1, check.width() / 5)), PlayerUiIcon.CHECK, Color.WHITE);
		}

		UiRect title = supportGalleryFileTitleRect(layout, rect);
		drawWrappedText(
				graphics,
				file.title(),
				title,
				PRIMARY_TEXT,
				Font.BOLD,
				ultra ? clampInt(layout.unit() - 1, 6, 8) : clampInt(layout.unit(), 10, 13),
				1
		);
	}

	private static void drawAttachmentPickerScrollbar(Graphics2D graphics, UiLayout layout, UiRect grid, double scroll, int visibleRows, int totalRows) {
		if (totalRows <= visibleRows) {
			return;
		}
		int width = ultraCompactScreenLayout(layout) ? 2 : 3;
		UiRect track = new UiRect(grid.right() - width, grid.y(), width, grid.height());
		fillRoundedRect(graphics, track, width, new Color(0, 16, 36, 18));
		int thumbHeight = clampInt(grid.height() * visibleRows / Math.max(visibleRows, totalRows), Math.max(8, layout.unit()), Math.max(8, grid.height()));
		double maxScroll = Math.max(1.0D, totalRows - visibleRows);
		int y = track.y() + (int) Math.round((track.height() - thumbHeight) * clampDouble(scroll, 0.0D, maxScroll) / maxScroll);
		fillRoundedRect(graphics, new UiRect(track.x(), y, track.width(), thumbHeight), width, new Color(66, 139, 249, 130));
	}

	private static void drawMessageBubble(Graphics2D graphics, UiLayout layout, MonitorApp app, UiRect area, SupportMessageSnapshot message, int y, int height) {
		boolean support = message.fromSupport();
		boolean ultra = ultraCompactScreenLayout(layout);
		SupportBubbleMetrics metrics = supportBubbleMetrics(graphics, layout, area, message);
		int bubbleX = support ? area.x() + metrics.avatarSize() + metrics.avatarGap() : area.right() - metrics.bubbleWidth();
		UiRect bubble = new UiRect(bubbleX, y, metrics.bubbleWidth(), height);
		fillRoundedRect(graphics, bubble, ultra ? 7 : clampInt(layout.unit() * 2, 13, 22), support ? SUPPORT_BUBBLE : USER_BUBBLE);
		if (support) {
			drawSupportAvatar(graphics, app, new UiRect(area.x(), y + Math.max(0, height - metrics.avatarSize()), metrics.avatarSize(), metrics.avatarSize()));
		}
		graphics.setColor(PRIMARY_TEXT);
		int textX = bubble.x() + metrics.padX();
		int cursorY = bubble.y() + metrics.padY();
		if (metrics.attachmentHeight() > 0) {
			drawMessageAttachments(graphics, layout, message.attachments(), new UiRect(textX, cursorY, Math.max(1, bubble.width() - metrics.padX() * 2), metrics.attachmentHeight()));
			cursorY += metrics.attachmentHeight() + metrics.attachmentGap();
		}
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, messageFontSize(layout)));
		int textY = cursorY + graphics.getFontMetrics().getAscent();
		int lastBaseline = textY;
		for (String line : metrics.lines()) {
			graphics.drawString(line, textX, textY);
			lastBaseline = textY;
			textY += metrics.lineHeight();
		}
		int contentRight = bubble.right() - metrics.padX();
		if (metrics.metaInline() && !metrics.lines().isEmpty()) {
			drawMessageMeta(graphics, layout, message, contentRight, lastBaseline);
		} else {
			int metaBaseline = bubble.bottom() - metrics.padY();
			drawMessageMeta(graphics, layout, message, contentRight, metaBaseline);
		}
	}

	private static void drawSupportAvatar(Graphics2D graphics, MonitorApp app, UiRect rect) {
		if (rect.width() <= 0 || rect.height() <= 0) {
			return;
		}
		drawAppIcon(graphics, app, rect, 0);
	}

	private static void drawInputBar(Graphics2D graphics, UiLayout layout, SupportVisualSnapshot snapshot) {
		List<SupportAttachmentSnapshot> pendingAttachments = snapshot.pendingAttachments() == null ? List.of() : snapshot.pendingAttachments();
		boolean expanded = !pendingAttachments.isEmpty();
		UiRect input = supportInputBarRect(layout, expanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		fillRoundedRect(graphics, input, 0, Color.WHITE);

		UiRect attach = supportAttachRect(layout, expanded);
		drawPaperclip(graphics, attach, TINKOFF_LIGHT_BLUE);

		if (expanded) {
			drawPendingAttachments(graphics, layout, pendingAttachments);
		}

		UiRect field = supportInputRect(layout, expanded);
		fillRoundedRect(graphics, field, field.height(), INPUT_BACKGROUND);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, ultra ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() + 2, 12, 17)));
		graphics.setColor(snapshot.waitingForInput() ? new Color(88, 98, 112) : new Color(148, 156, 168));
		String placeholder = snapshot.waitingForInput() ? "Напишите в чат..." : "Сообщение";
		int textX = field.x() + Math.max(5, field.height() / 3);
		int textY = field.y() + (field.height() - graphics.getFontMetrics().getHeight()) / 2 + graphics.getFontMetrics().getAscent();
		graphics.drawString(placeholder, textX, textY);

		UiRect send = supportSendRect(layout, expanded);
		Color sendFill = snapshot.waitingForInput() || expanded ? TINKOFF_BLUE : new Color(159, 219, 244);
		fillRoundedRect(graphics, send, send.height(), sendFill);
		drawArrowUp(graphics, send, snapshot.waitingForInput() || expanded ? Color.WHITE : new Color(255, 255, 255, 178));
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
		SupportBubbleMetrics metrics = supportBubbleMetrics(graphics, layout, area, message);
		return Math.max(metrics.avatarSize(), metrics.bubbleHeight());
	}

	private static SupportBubbleMetrics supportBubbleMetrics(Graphics2D graphics, UiLayout layout, UiRect area, SupportMessageSnapshot message) {
		boolean support = message.fromSupport();
		boolean ultra = ultraCompactScreenLayout(layout);
		int avatarSize = support ? (ultra ? clampInt(layout.unit() * 2, 12, 16) : clampInt(layout.unit() * 3, 24, 36)) : 0;
		int avatarGap = support ? Math.max(2, layout.unit() / 3) : 0;
		int maxBubbleWidth = support
				? Math.max(12, area.width() * 66 / 100 - avatarSize - avatarGap)
				: Math.max(12, area.width() * 68 / 100);
		int padX = ultra ? 4 : clampInt(layout.unit(), 7, 12);
		int padY = ultra ? 3 : clampInt(layout.unit() / 2, 5, 9);
		int maxContentWidth = Math.max(8, maxBubbleWidth - padX * 2);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, messageFontSize(layout)));
		List<String> lines = wrapMessageText(graphics.getFontMetrics(), message.text(), maxContentWidth, maxMessageLines(layout));
		int lineHeight = graphics.getFontMetrics().getHeight();
		int widestLine = widestLineWidth(graphics, lines);
		int lastLineWidth = lines.isEmpty() ? 0 : graphics.getFontMetrics().stringWidth(lines.get(lines.size() - 1));
		int attachmentHeight = messageAttachmentsHeight(layout, message.attachments());
		int attachmentGap = attachmentHeight > 0 && !lines.isEmpty() ? Math.max(2, layout.unit() / 3) : 0;
		int attachmentWidth = messageAttachmentWidth(layout, message.attachments());
		int metaWidth = messageMetaWidth(graphics, layout, message);
		int metaGap = messageMetaInlineGap(layout);
		boolean metaInline = !lines.isEmpty() && lastLineWidth + metaGap + metaWidth <= maxContentWidth;
		int contentWidth = Math.max(widestLine, attachmentWidth);
		if (metaInline) {
			contentWidth = Math.max(contentWidth, lastLineWidth + metaGap + metaWidth);
		} else {
			contentWidth = Math.max(contentWidth, metaWidth);
		}
		int bubbleWidth = Math.min(maxBubbleWidth, Math.max(1, contentWidth) + padX * 2);
		int extraMetaHeight = metaInline ? 0 : messageMetaHeight(layout);
		int bubbleHeight = attachmentHeight
				+ attachmentGap
				+ lines.size() * lineHeight
				+ extraMetaHeight
				+ padY * 2;
		return new SupportBubbleMetrics(
				avatarSize,
				avatarGap,
				padX,
				padY,
				bubbleWidth,
				Math.max(1, bubbleHeight),
				attachmentHeight,
				attachmentGap,
				lineHeight,
				List.copyOf(lines),
				metaInline
		);
	}

	private static boolean messageVisible(SupportMessageSnapshot message) {
		return message != null
				&& ((message.text() != null && !message.text().isBlank())
				|| (message.attachments() != null && !message.attachments().isEmpty()));
	}

	private static List<String> wrapMessageText(java.awt.FontMetrics metrics, String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		if (metrics == null || maxWidth <= 0 || maxLines <= 0) {
			return lines;
		}
		String normalized = text == null ? "" : text.replace('\r', '\n');
		if (normalized.isBlank()) {
			return lines;
		}
		for (String rawLine : normalized.split("\n", -1)) {
			if (lines.size() >= maxLines) {
				break;
			}
			if (rawLine.isBlank()) {
				lines.add("");
				continue;
			}
			lines.addAll(wrapText(metrics, rawLine, maxWidth, maxLines - lines.size()));
		}
		return lines.size() > maxLines ? lines.subList(0, maxLines) : lines;
	}

	private static int messageAttachmentsHeight(UiLayout layout, List<SupportAttachmentSnapshot> attachments) {
		if (attachments == null || attachments.isEmpty()) {
			return 0;
		}
		return ultraCompactScreenLayout(layout) ? clampInt(layout.unit() * 2 + 2, 14, 18) : clampInt(layout.unit() * 4, 34, 54);
	}

	private static int messageAttachmentWidth(UiLayout layout, List<SupportAttachmentSnapshot> attachments) {
		if (attachments == null || attachments.isEmpty()) {
			return 0;
		}
		int size = messageAttachmentsHeight(layout, attachments);
		int gap = Math.max(2, layout.unit() / 3);
		return Math.min(attachments.size(), MAX_PENDING_ATTACHMENTS) * size + Math.max(0, Math.min(attachments.size(), MAX_PENDING_ATTACHMENTS) - 1) * gap;
	}

	private static int messageMetaHeight(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? clampInt(layout.unit(), 6, 8) : clampInt(layout.unit() + 1, 10, 14);
	}

	private static int messageMetaWidth(Graphics2D graphics, UiLayout layout, SupportMessageSnapshot message) {
		int size = messageMetaFontSize(layout);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
		String time = messageTime(message.createdAtMillis());
		int ticks = message.fromSupport() ? 0 : messageMetaTickGap(size) + deliveryTicksWidth(size, message.read());
		return graphics.getFontMetrics().stringWidth(time) + ticks;
	}

	private static void drawMessageAttachments(Graphics2D graphics, UiLayout layout, List<SupportAttachmentSnapshot> attachments, UiRect rect) {
		if (attachments == null || attachments.isEmpty()) {
			return;
		}
		int size = Math.min(rect.height(), rect.width());
		int gap = Math.max(2, layout.unit() / 3);
		int x = rect.x();
		for (SupportAttachmentSnapshot attachment : attachments) {
			if (attachment == null || x + size > rect.right()) {
				break;
			}
			UiRect tile = new UiRect(x, rect.y(), size, size);
			drawSupportAttachmentPreviewTile(graphics, layout, tile, attachment);
			x += size + gap;
		}
	}

	private static void drawMessageMeta(Graphics2D graphics, UiLayout layout, SupportMessageSnapshot message, int right, int baselineY) {
		int size = messageMetaFontSize(layout);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, size));
		graphics.setColor(META_TEXT);
		String time = messageTime(message.createdAtMillis());
		int metaWidth = messageMetaWidth(graphics, layout, message);
		int timeWidth = graphics.getFontMetrics().stringWidth(time);
		int x = right - metaWidth;
		graphics.drawString(time, x, baselineY);
		if (!message.fromSupport()) {
			drawDeliveryTicks(graphics, x + timeWidth + messageMetaTickGap(size), baselineY - size / 2, size, message.read());
		}
	}

	private static int messageMetaFontSize(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? clampInt(layout.unit() - 1, 6, 8) : clampInt(layout.unit(), 9, 12);
	}

	private static int messageMetaInlineGap(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? Math.max(4, layout.unit() / 2) : clampInt(layout.unit(), 8, 12);
	}

	private static int messageMetaTickGap(int size) {
		return Math.max(3, size / 3);
	}

	private static int deliveryTicksWidth(int size, boolean read) {
		return read ? size + Math.max(3, size / 3) : size;
	}

	private static void drawDeliveryTicks(Graphics2D graphics, int x, int y, int size, boolean read) {
		Stroke previous = graphics.getStroke();
		try {
			graphics.setStroke(new BasicStroke(Math.max(1.0F, size / 7.0F), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.setColor(TINKOFF_BLUE);
			drawSmallTick(graphics, x, y, size);
			if (read) {
				drawSmallTick(graphics, x + Math.max(3, size / 3), y, size);
			}
		} finally {
			graphics.setStroke(previous);
		}
	}

	private static void drawSmallTick(Graphics2D graphics, int x, int y, int size) {
		Path2D.Float path = new Path2D.Float();
		path.moveTo(x, y + size / 2.0F);
		path.lineTo(x + size / 3.0F, y + size * 0.78F);
		path.lineTo(x + size, y);
		graphics.draw(path);
	}

	private static void drawChatScrollbar(Graphics2D graphics, UiLayout layout, UiRect area, int totalMessages, double scrollOffset) {
		if (totalMessages <= 6) {
			return;
		}
		int width = ultraCompactScreenLayout(layout) ? 2 : 3;
		UiRect track = new UiRect(area.right() - width, area.y(), width, area.height());
		fillRoundedRect(graphics, track, width, new Color(0, 16, 36, 18));
		int thumbHeight = clampInt(area.height() * 5 / Math.max(6, totalMessages), Math.max(8, layout.unit()), Math.max(8, area.height() / 2));
		double maxOffset = Math.max(1.0D, totalMessages - 1);
		int y = track.bottom() - thumbHeight - (int) Math.round((track.height() - thumbHeight) * clampDouble(scrollOffset, 0.0D, maxOffset) / maxOffset);
		fillRoundedRect(graphics, new UiRect(track.x(), y, track.width(), thumbHeight), width, new Color(66, 139, 249, 120));
	}

	private static void drawPendingAttachments(Graphics2D graphics, UiLayout layout, List<SupportAttachmentSnapshot> attachments) {
		for (int index = 0; index < attachments.size(); index++) {
			UiRect tile = supportPendingAttachmentRect(layout, attachments.size(), index);
			SupportAttachmentSnapshot attachment = attachments.get(index);
			drawSupportAttachmentPreviewTile(graphics, layout, tile, attachment);
			UiRect remove = supportPendingAttachmentRemoveRect(layout, attachments.size(), index);
			fillRoundedRect(graphics, remove, remove.height(), new Color(0, 16, 36, 98));
			drawPlayerUiIcon(graphics, remove.inset(Math.max(1, remove.width() / 4)), PlayerUiIcon.CLOSE, Color.WHITE);
		}
	}

	private static void drawSupportAttachmentPreviewTile(Graphics2D graphics, UiLayout layout, UiRect tile, SupportAttachmentSnapshot attachment) {
		int arc = clampInt(tile.height() / 4, 4, 14);
		fillRoundedRect(graphics, tile, arc, new Color(246, 247, 248));
		if (attachment != null && attachment.preview() != null) {
			Shape previousClip = graphics.getClip();
			graphics.setClip(new RoundRectangle2D.Float(tile.x(), tile.y(), tile.width(), tile.height(), arc, arc));
			try {
				drawScaledImage(graphics, attachment.preview(), tile, MediaScaleMode.FILL);
			} finally {
				graphics.setClip(previousClip);
			}
		} else {
			drawPlayerUiIcon(graphics, tile.inset(Math.max(3, tile.width() / 4)), supportGalleryFileIcon(attachment == null ? GalleryItemKind.MEDIA : attachment.kind()), new Color(126, 138, 150));
		}
		UiRect badge = supportAttachmentTypeBadgeRect(layout, tile);
		fillRoundedRect(graphics, badge, badge.height(), new Color(8, 12, 16, 150));
		drawPlayerUiIcon(graphics, badge.inset(Math.max(1, badge.width() / 5)), supportGalleryFileIcon(attachment == null ? GalleryItemKind.MEDIA : attachment.kind()), Color.WHITE);
	}

	private static UiRect supportAttachmentTypeBadgeRect(UiLayout layout, UiRect tile) {
		int size = ultraCompactScreenLayout(layout) ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() + 3, 12, 18);
		int inset = Math.max(2, size / 5);
		return new UiRect(tile.x() + inset, tile.y() + inset, size, size);
	}

	private static UiRect supportPendingAttachmentRect(UiLayout layout, int total, int index) {
		UiRect bar = supportInputBarRect(layout, true);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		int size = ultra ? clampInt(layout.unit() * 3, 18, 26) : clampInt(layout.unit() * 5, 44, 68);
		int gap = Math.max(2, layout.unit() / 2);
		int y = bar.y() + (ultra ? 2 : clampInt(layout.unit() / 2, 4, 8));
		int x = bar.x() + inset + index * (size + gap);
		return new UiRect(x, y, size, size);
	}

	private static UiRect supportPendingAttachmentRemoveRect(UiLayout layout, int total, int index) {
		UiRect tile = supportPendingAttachmentRect(layout, total, index);
		int size = Math.max(8, tile.height() / 2);
		return new UiRect(tile.x() + (tile.width() - size) / 2, tile.y() + (tile.height() - size) / 2, size, size);
	}

	private static UiRect supportShellRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? 0 : clampInt(layout.unit() / 2, 3, 8);
		return canvas.inset(inset);
	}

	private static UiRect supportCloseRect(UiLayout layout) {
		UiRect shell = supportShellRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2 + 2, 12, 14) : clampInt(layout.unit() * 2 + 4, 22, 34);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		return new UiRect(shell.x() + inset, shell.y() + inset, size, size);
	}

	private static UiRect supportMessagesRect(UiLayout layout, boolean inputExpanded) {
		UiRect shell = supportShellRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? Math.max(3, layout.unit() / 2) : clampInt(layout.unit(), 8, 18);
		int top = supportCloseRect(layout).bottom() + (ultra ? 3 : clampInt(layout.unit(), 8, 14));
		int bottom = supportInputBarRect(layout, inputExpanded).y() - (ultra ? 2 : clampInt(layout.unit() / 2, 5, 9));
		return new UiRect(
				shell.x() + inset,
				top,
				Math.max(1, shell.width() - inset * 2),
				Math.max(1, bottom - top)
		);
	}

	private static UiRect supportInputBarRect(UiLayout layout, boolean expanded) {
		UiRect shell = supportShellRect(layout);
		boolean ultra = ultraCompactScreenLayout(layout);
		int height = ultra ? clampInt(layout.unit() * (expanded ? 6 : 3), expanded ? 40 : 20, expanded ? 56 : 26) : clampInt(layout.unit() * (expanded ? 9 : 4), expanded ? 86 : 40, expanded ? 128 : 58);
		return new UiRect(shell.x(), shell.bottom() - height, shell.width(), height);
	}

	private static UiRect supportAttachmentPickerRect(UiLayout layout, boolean inputExpanded) {
		UiRect messages = supportMessagesRect(layout, inputExpanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? 0 : Math.max(0, layout.unit() / 4);
		return messages.inset(inset);
	}

	private static UiRect supportAttachmentPickerHeaderRect(UiLayout layout, boolean inputExpanded) {
		UiRect panel = supportAttachmentPickerRect(layout, inputExpanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		int height = ultra ? clampInt(layout.unit() * 2, 14, 18) : clampInt(layout.unit() * 3, 30, 44);
		return new UiRect(panel.x(), panel.y(), panel.width(), Math.min(panel.height(), height));
	}

	private static UiRect supportAttachmentPickerCloseRect(UiLayout layout, boolean inputExpanded) {
		UiRect header = supportAttachmentPickerHeaderRect(layout, inputExpanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2 - 1, 11, 15) : clampInt(layout.unit() * 2 + 2, 24, 32);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 12);
		return new UiRect(header.right() - inset - size, header.y() + (header.height() - size) / 2, size, size);
	}

	private static UiRect supportAttachmentPickerGridRect(UiLayout layout, boolean inputExpanded) {
		UiRect panel = supportAttachmentPickerRect(layout, inputExpanded);
		UiRect header = supportAttachmentPickerHeaderRect(layout, inputExpanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		int inset = ultra ? Math.max(3, layout.unit() / 2) : clampInt(layout.unit(), 8, 14);
		int top = header.bottom() + (ultra ? 2 : Math.max(4, layout.unit() / 2));
		return new UiRect(
				panel.x() + inset,
				top,
				Math.max(1, panel.width() - inset * 2),
				Math.max(1, panel.bottom() - top - inset)
		);
	}

	private static int supportAttachmentPickerColumns(UiLayout layout) {
		UiRect grid = supportAttachmentPickerGridRect(layout, false);
		if (ultraCompactScreenLayout(layout)) {
			return grid.width() < 54 ? 2 : 3;
		}
		return grid.width() >= 360 ? 4 : 3;
	}

	private static int supportAttachmentPickerGap(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit() / 2, 5, 9);
	}

	private static int supportGalleryFileCardHeight(UiLayout layout, boolean inputExpanded) {
		UiRect grid = supportAttachmentPickerGridRect(layout, inputExpanded);
		int columns = supportAttachmentPickerColumns(layout);
		int gap = supportAttachmentPickerGap(layout);
		int width = Math.max(1, (grid.width() - gap * Math.max(0, columns - 1)) / Math.max(1, columns));
		return width + (ultraCompactScreenLayout(layout) ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() * 2, 18, 28));
	}

	private static int supportAttachmentPickerVisibleRows(UiLayout layout, boolean inputExpanded) {
		UiRect grid = supportAttachmentPickerGridRect(layout, inputExpanded);
		int step = supportGalleryFileCardHeight(layout, inputExpanded) + supportAttachmentPickerGap(layout);
		return Math.max(1, (grid.height() + supportAttachmentPickerGap(layout)) / Math.max(1, step));
	}

	private static int supportAttachmentPickerTotalRows(int fileCount, UiLayout layout) {
		int columns = supportAttachmentPickerColumns(layout);
		return Math.max(0, (Math.max(0, fileCount) + columns - 1) / Math.max(1, columns));
	}

	private static int supportAttachmentPickerMaxScroll(UiLayout layout, int fileCount, boolean inputExpanded) {
		return Math.max(0, supportAttachmentPickerTotalRows(fileCount, layout) - supportAttachmentPickerVisibleRows(layout, inputExpanded));
	}

	private static UiRect supportGalleryFileCardRect(UiLayout layout, boolean inputExpanded, int visibleRow, int column) {
		UiRect grid = supportAttachmentPickerGridRect(layout, inputExpanded);
		int columns = supportAttachmentPickerColumns(layout);
		int gap = supportAttachmentPickerGap(layout);
		int width = Math.max(1, (grid.width() - gap * Math.max(0, columns - 1)) / Math.max(1, columns));
		int height = supportGalleryFileCardHeight(layout, inputExpanded);
		return new UiRect(grid.x() + column * (width + gap), grid.y() + visibleRow * (height + gap), width, height);
	}

	private static UiRect supportGalleryFilePreviewRect(UiLayout layout, UiRect card) {
		int inset = ultraCompactScreenLayout(layout) ? 2 : Math.max(3, layout.unit() / 3);
		int size = Math.max(1, card.width() - inset * 2);
		return new UiRect(card.x() + inset, card.y() + inset, size, Math.min(size, Math.max(1, card.height() - inset * 2)));
	}

	private static UiRect supportGalleryFileTitleRect(UiLayout layout, UiRect card) {
		UiRect preview = supportGalleryFilePreviewRect(layout, card);
		int inset = ultraCompactScreenLayout(layout) ? 2 : Math.max(3, layout.unit() / 3);
		return new UiRect(card.x() + inset, preview.bottom() + Math.max(1, inset / 2), Math.max(1, card.width() - inset * 2), Math.max(1, card.bottom() - preview.bottom() - inset));
	}

	private static UiRect supportGalleryFileTypeBadgeRect(UiLayout layout, UiRect preview) {
		int size = ultraCompactScreenLayout(layout) ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() + 4, 13, 18);
		int inset = Math.max(2, size / 5);
		return new UiRect(preview.x() + inset, preview.y() + inset, size, size);
	}

	private static UiRect supportGalleryFileSelectedRect(UiLayout layout, UiRect preview) {
		int size = ultraCompactScreenLayout(layout) ? clampInt(layout.unit(), 7, 10) : clampInt(layout.unit() + 4, 13, 18);
		int inset = Math.max(2, size / 5);
		return new UiRect(preview.right() - inset - size, preview.y() + inset, size, size);
	}

	private static int supportGalleryFileIndexAt(MinecraftServer server, ScreenRuntimeKey key, UiLayout layout, boolean inputExpanded, UiPoint touchPoint) {
		if (server == null || key == null || layout == null || touchPoint == null) {
			return -1;
		}
		UiRect grid = supportAttachmentPickerGridRect(layout, inputExpanded);
		if (!grid.contains(touchPoint.x(), touchPoint.y())) {
			return -1;
		}
		SupportRuntimeState state = ensureState(key);
		int scroll;
		synchronized (state) {
			scroll = state.attachmentPickerScroll;
		}
		List<SupportGalleryFileSnapshot> files = supportGalleryFilesSnapshot(server, key, List.of());
		int columns = supportAttachmentPickerColumns(layout);
		int visibleRows = supportAttachmentPickerVisibleRows(layout, inputExpanded);
		int totalRows = supportAttachmentPickerTotalRows(files.size(), layout);
		scroll = clampInt(scroll, 0, Math.max(0, totalRows - visibleRows));
		for (int row = 0; row < visibleRows; row++) {
			for (int column = 0; column < columns; column++) {
				int position = (scroll + row) * columns + column;
				if (position < 0 || position >= files.size()) {
					continue;
				}
				if (supportGalleryFileCardRect(layout, inputExpanded, row, column).contains(touchPoint.x(), touchPoint.y())) {
					return files.get(position).index();
				}
			}
		}
		return -1;
	}

	private static PlayerUiIcon supportGalleryFileIcon(GalleryItemKind kind) {
		return switch (kind == null ? GalleryItemKind.MEDIA : kind) {
			case AUDIO -> PlayerUiIcon.MEDIA_AUDIO;
			case VIDEO, YOUTUBE -> PlayerUiIcon.MEDIA_VIDEO;
			case LIVE_CAMERA -> PlayerUiIcon.VIDEO_CAMERA;
			case MEDIA -> PlayerUiIcon.MEDIA_IMAGE;
		};
	}

	private static UiRect supportAttachRect(UiLayout layout, boolean expanded) {
		UiRect bar = supportInputBarRect(layout, expanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2, 12, 17) : clampInt(layout.unit() * 2 + 4, 24, 34);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		int bottomInset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit() / 2, 5, 10);
		int fieldHeight = ultra ? clampInt(layout.unit() * 2, 14, 18) : clampInt(layout.unit() * 2 + 4, 28, 38);
		int rowY = bar.bottom() - bottomInset - fieldHeight;
		int y = rowY + (fieldHeight - size) / 2;
		return new UiRect(bar.x() + inset, y, size, size);
	}

	private static UiRect supportSendRect(UiLayout layout, boolean expanded) {
		UiRect bar = supportInputBarRect(layout, expanded);
		boolean ultra = ultraCompactScreenLayout(layout);
		int size = ultra ? clampInt(layout.unit() * 2, 12, 17) : clampInt(layout.unit() * 2 + 4, 24, 34);
		int inset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit(), 8, 16);
		int bottomInset = ultra ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit() / 2, 5, 10);
		int fieldHeight = ultra ? clampInt(layout.unit() * 2, 14, 18) : clampInt(layout.unit() * 2 + 4, 28, 38);
		int rowY = bar.bottom() - bottomInset - fieldHeight;
		int y = rowY + (fieldHeight - size) / 2;
		return new UiRect(bar.right() - inset - size, y, size, size);
	}

	private static UiRect supportInputRect(UiLayout layout, boolean expanded) {
		UiRect bar = supportInputBarRect(layout, expanded);
		UiRect attach = supportAttachRect(layout, expanded);
		UiRect send = supportSendRect(layout, expanded);
		int gap = compactGap(layout);
		int x = attach.right() + gap;
		int right = send.x() - gap;
		int height = ultraCompactScreenLayout(layout) ? clampInt(layout.unit() * 2, 14, 18) : clampInt(layout.unit() * 2 + 4, 28, 38);
		int bottomInset = ultraCompactScreenLayout(layout) ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit() / 2, 5, 10);
		return new UiRect(x, bar.bottom() - bottomInset - height, Math.max(1, right - x), height);
	}

	private static int compactGap(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? Math.max(2, layout.unit() / 3) : clampInt(layout.unit() / 2, 5, 10);
	}

	private static int messageFontSize(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? clampInt(layout.unit() - 1, 6, 8) : clampInt(layout.unit() + 1, 11, 16);
	}

	private static int maxMessageLines(UiLayout layout) {
		return ultraCompactScreenLayout(layout) ? 4 : 9;
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
		state.messages.add(new SupportChatMessage(true, "Поддержка", "Здравствуйте! Опишите баг или идею по серверу.", System.currentTimeMillis(), 0L, List.of(), true, true));
		state.version++;
	}

	private static String messageTime(long createdAtMillis) {
		long safeMillis = createdAtMillis > 0L ? createdAtMillis : System.currentTimeMillis();
		return MESSAGE_TIME_FORMAT.format(Instant.ofEpochMilli(safeMillis));
	}

	private static SupportAttachment attachmentFromGalleryItem(int index, GalleryItem item) {
		if (!supportGalleryAttachable(item)) {
			return null;
		}
		GalleryItemKind kind = MonitorScreenGalleryRuntime.effectiveGalleryItemKind(item);
		return new SupportAttachment(
				UUID.randomUUID().toString(),
				safeAttachmentTitle(item.title(), index),
				item.subtitle() == null ? "" : item.subtitle(),
				item.url() == null ? "" : item.url(),
				item.localMediaKey() == null ? "" : item.localMediaKey(),
				kind,
				item.preview()
		);
	}

	private static SupportAttachmentSnapshot attachmentSnapshot(SupportAttachment attachment) {
		return new SupportAttachmentSnapshot(
				attachment == null || attachment.id() == null ? "" : attachment.id(),
				attachment == null || attachment.title() == null ? "" : attachment.title(),
				attachment == null || attachment.subtitle() == null ? "" : attachment.subtitle(),
				attachment == null || attachment.url() == null ? "" : attachment.url(),
				attachment == null || attachment.localMediaKey() == null ? "" : attachment.localMediaKey(),
				attachment == null || attachment.kind() == null ? GalleryItemKind.MEDIA : attachment.kind(),
				attachment == null ? null : attachment.preview()
		);
	}

	private static String safeAttachmentTitle(String title, int index) {
		if (title != null && !title.isBlank()) {
			return title.trim();
		}
		return "Файл " + Math.max(1, index + 1);
	}

	private static void trimHistoryLocked(SupportRuntimeState state) {
		while (state.messages.size() > MAX_STORED_MESSAGES) {
			state.messages.remove(0);
		}
		state.scrollOffset = clampInt(state.scrollOffset, 0, Math.max(0, state.messages.size() - 1));
	}

	private static void markTicketRead(MinecraftServer server, long ticketId) {
		SupportTicket ticket = TICKETS.get(ticketId);
		if (ticket == null) {
			return;
		}
		SupportRuntimeState state = STATES.get(ticket.key());
		if (state == null) {
			return;
		}
		synchronized (state) {
			for (int index = 0; index < state.messages.size(); index++) {
				SupportChatMessage message = state.messages.get(index);
				if (!message.fromSupport() && message.ticketId() == ticketId && !message.read()) {
					state.messages.set(index, message.withRead(true));
					state.version++;
				}
			}
		}
		if (server != null) {
			requestRuntimeRender(server, ticket.key());
		}
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

	private static ObservedSupportUiTarget findObservedSupportUiTarget(ServerPlayer player, ServerLevel level) {
		if (player == null || level == null) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 rayEnd = eye.add(player.getLookAngle().scale(MEDIA_CONTROL_DISTANCE));
		ScreenComponent nearestComponent = null;
		ItemFrame nearestFrame = null;
		TileCoord nearestTile = null;
		Vec3 nearestHit = null;
		double nearestDistanceSqr = Double.POSITIVE_INFINITY;
		for (ScreenComponent component : cachedComponents(level)) {
			if (component == null || !component.powered() || component.viewMode() != ScreenViewMode.SUPPORT) {
				continue;
			}
			for (Map.Entry<ItemFrame, TileCoord> entry : component.frameCoords().entrySet()) {
				ItemFrame frame = entry.getKey();
				if (frame == null || !frame.isAlive()) {
					continue;
				}
				Optional<Vec3> hit = frame.getBoundingBox().inflate(0.08D).clip(eye, rayEnd);
				if (hit.isEmpty() || hit.get().distanceToSqr(eye) > MEDIA_CONTROL_DISTANCE * MEDIA_CONTROL_DISTANCE) {
					continue;
				}
				double hitDistanceSqr = eye.distanceToSqr(hit.get());
				if (hitDistanceSqr < nearestDistanceSqr) {
					nearestDistanceSqr = hitDistanceSqr;
					nearestComponent = component;
					nearestFrame = frame;
					nearestTile = entry.getValue();
					nearestHit = hit.get();
				}
			}
		}
		if (nearestComponent == null || nearestFrame == null || nearestTile == null || nearestHit == null) {
			return null;
		}
		UiPoint touchPoint = screenTouchPoint(nearestFrame, player, nearestHit, nearestTile, nearestComponent.width(), nearestComponent.height());
		return touchPoint == null ? null : new ObservedSupportUiTarget(nearestComponent, createUiLayout(nearestComponent.width(), nearestComponent.height()), touchPoint);
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
			STATES.clear();
			TICKETS.clear();
			TELEGRAM_MESSAGE_TICKETS.clear();
			LAST_TELEGRAM_TICKETS.clear();
			NEXT_TICKET_ID.set(1L);
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
				NEXT_TICKET_ID.set(Math.max(1L, readLong(root, "next_ticket_id", 1L)));
				JsonArray screens = root.getAsJsonArray("screens");
				if (screens != null) {
					for (JsonElement element : screens) {
						if (element instanceof JsonObject screenObject) {
							loadScreenState(screenObject);
						}
					}
				}
				JsonArray tickets = root.getAsJsonArray("tickets");
				if (tickets != null) {
					for (JsonElement element : tickets) {
						if (element instanceof JsonObject ticketObject) {
							loadTicket(ticketObject);
						}
					}
				}
				JsonArray telegramMappings = root.getAsJsonArray("telegram_message_tickets");
				if (telegramMappings != null) {
					for (JsonElement element : telegramMappings) {
						if (element instanceof JsonObject mappingObject) {
							long chatId = readLong(mappingObject, "chat_id", Long.MIN_VALUE);
							int messageId = readInt(mappingObject, "message_id", -1);
							long ticketId = readLong(mappingObject, "ticket_id", -1L);
							if (chatId != Long.MIN_VALUE && messageId > 0 && ticketId > 0L) {
								TELEGRAM_MESSAGE_TICKETS.put(new TelegramMessageKey(chatId, messageId), ticketId);
							}
						}
					}
				}
				JsonArray lastTickets = root.getAsJsonArray("last_telegram_tickets");
				if (lastTickets != null) {
					for (JsonElement element : lastTickets) {
						if (element instanceof JsonObject lastObject) {
							long chatId = readLong(lastObject, "chat_id", Long.MIN_VALUE);
							long ticketId = readLong(lastObject, "ticket_id", -1L);
							if (chatId != Long.MIN_VALUE && ticketId > 0L) {
								LAST_TELEGRAM_TICKETS.put(chatId, ticketId);
							}
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
				root.addProperty("next_ticket_id", NEXT_TICKET_ID.get());
				JsonArray screens = new JsonArray();
				STATES.entrySet().stream()
						.sorted(Comparator.comparing(entry -> screenKeyId(entry.getKey())))
						.forEach(entry -> screens.add(writeScreenState(entry.getKey(), entry.getValue())));
				root.add("screens", screens);
				JsonArray tickets = new JsonArray();
				TICKETS.values().stream()
						.sorted(Comparator.comparingLong(SupportTicket::id))
						.forEach(ticket -> tickets.add(writeTicket(ticket)));
				root.add("tickets", tickets);
				JsonArray telegramMappings = new JsonArray();
				TELEGRAM_MESSAGE_TICKETS.entrySet().stream()
						.sorted(Comparator.comparing(entry -> entry.getKey().chatId() + ":" + entry.getKey().messageId()))
						.forEach(entry -> {
							JsonObject object = new JsonObject();
							object.addProperty("chat_id", entry.getKey().chatId());
							object.addProperty("message_id", entry.getKey().messageId());
							object.addProperty("ticket_id", entry.getValue());
							telegramMappings.add(object);
						});
				root.add("telegram_message_tickets", telegramMappings);
				JsonArray lastTickets = new JsonArray();
				LAST_TELEGRAM_TICKETS.entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.forEach(entry -> {
							JsonObject object = new JsonObject();
							object.addProperty("chat_id", entry.getKey());
							object.addProperty("ticket_id", entry.getValue());
							lastTickets.add(object);
						});
				root.add("last_telegram_tickets", lastTickets);
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

	private static void loadScreenState(JsonObject object) {
		ScreenRuntimeKey key = readScreenKey(object.getAsJsonObject("key"));
		if (key == null) {
			return;
		}
		SupportRuntimeState state = new SupportRuntimeState();
		state.greetingAdded = readBoolean(object, "greeting_added", false);
		state.scrollOffset = readInt(object, "scroll_offset", 0);
		state.statusText = readString(object, "status_text");
		JsonArray pending = object.getAsJsonArray("pending_attachments");
		if (pending != null) {
			for (JsonElement element : pending) {
				if (element instanceof JsonObject attachmentObject) {
					state.pendingAttachments.add(readAttachment(attachmentObject));
				}
			}
		}
		JsonArray messages = object.getAsJsonArray("messages");
		if (messages != null) {
			for (JsonElement element : messages) {
				if (element instanceof JsonObject messageObject) {
					state.messages.add(readSupportMessage(messageObject));
				}
			}
		}
		trimHistoryLocked(state);
		STATES.put(key, state);
	}

	private static JsonObject writeScreenState(ScreenRuntimeKey key, SupportRuntimeState state) {
		JsonObject object = new JsonObject();
		object.add("key", writeScreenKey(key));
		synchronized (state) {
			object.addProperty("greeting_added", state.greetingAdded);
			object.addProperty("scroll_offset", state.scrollOffset);
			object.addProperty("status_text", state.statusText == null ? "" : state.statusText);
			JsonArray pending = new JsonArray();
			for (SupportAttachment attachment : state.pendingAttachments) {
				pending.add(writeAttachment(attachment));
			}
			object.add("pending_attachments", pending);
			JsonArray messages = new JsonArray();
			for (SupportChatMessage message : state.messages) {
				messages.add(writeSupportMessage(message));
			}
			object.add("messages", messages);
		}
		return object;
	}

	private static void loadTicket(JsonObject object) {
		long ticketId = readLong(object, "id", -1L);
		ScreenRuntimeKey key = readScreenKey(object.getAsJsonObject("key"));
		UUID playerUuid = parseUuid(readString(object, "player_uuid"));
		String playerName = readString(object, "player_name");
		if (ticketId <= 0L || key == null || playerUuid == null) {
			return;
		}
		TICKETS.put(ticketId, new SupportTicket(ticketId, key, playerUuid, playerName.isBlank() ? "Игрок" : playerName, readLong(object, "created_at", System.currentTimeMillis())));
		NEXT_TICKET_ID.set(Math.max(NEXT_TICKET_ID.get(), ticketId + 1L));
	}

	private static JsonObject writeTicket(SupportTicket ticket) {
		JsonObject object = new JsonObject();
		object.addProperty("id", ticket.id());
		object.add("key", writeScreenKey(ticket.key()));
		object.addProperty("player_uuid", ticket.playerUuid() == null ? "" : ticket.playerUuid().toString());
		object.addProperty("player_name", ticket.playerName() == null ? "" : ticket.playerName());
		object.addProperty("created_at", ticket.createdAtMillis());
		return object;
	}

	private static SupportChatMessage readSupportMessage(JsonObject object) {
		List<SupportAttachment> attachments = new ArrayList<>();
		JsonArray attachmentArray = object.getAsJsonArray("attachments");
		if (attachmentArray != null) {
			for (JsonElement element : attachmentArray) {
				if (element instanceof JsonObject attachmentObject) {
					attachments.add(readAttachment(attachmentObject));
				}
			}
		}
		return new SupportChatMessage(
				readBoolean(object, "from_support", false),
				readString(object, "author"),
				readString(object, "text"),
				readLong(object, "created_at", System.currentTimeMillis()),
				readLong(object, "ticket_id", 0L),
				List.copyOf(attachments),
				readBoolean(object, "delivered", true),
				readBoolean(object, "read", false)
		);
	}

	private static JsonObject writeSupportMessage(SupportChatMessage message) {
		JsonObject object = new JsonObject();
		object.addProperty("from_support", message.fromSupport());
		object.addProperty("author", message.author() == null ? "" : message.author());
		object.addProperty("text", message.text() == null ? "" : message.text());
		object.addProperty("created_at", message.createdAtMillis());
		object.addProperty("ticket_id", message.ticketId());
		object.addProperty("delivered", message.delivered());
		object.addProperty("read", message.read());
		JsonArray attachments = new JsonArray();
		for (SupportAttachment attachment : message.attachments()) {
			attachments.add(writeAttachment(attachment));
		}
		object.add("attachments", attachments);
		return object;
	}

	private static SupportAttachment readAttachment(JsonObject object) {
		String url = readString(object, "url");
		String localMediaKey = readString(object, "local_media");
		GalleryItemKind kind = GalleryItemKind.fromPersisted(readString(object, "kind"), url);
		return new SupportAttachment(
				readString(object, "id"),
				readString(object, "title"),
				readString(object, "subtitle"),
				url,
				localMediaKey,
				kind,
				null
		);
	}

	private static JsonObject writeAttachment(SupportAttachment attachment) {
		JsonObject object = new JsonObject();
		object.addProperty("id", attachment == null || attachment.id() == null ? "" : attachment.id());
		object.addProperty("title", attachment == null || attachment.title() == null ? "" : attachment.title());
		object.addProperty("subtitle", attachment == null || attachment.subtitle() == null ? "" : attachment.subtitle());
		object.addProperty("url", attachment == null || attachment.url() == null ? "" : attachment.url());
		object.addProperty("local_media", attachment == null || attachment.localMediaKey() == null ? "" : attachment.localMediaKey());
		object.addProperty("kind", attachment == null || attachment.kind() == null ? GalleryItemKind.MEDIA.persistedName() : attachment.kind().persistedName());
		return object;
	}

	private static JsonObject writeScreenKey(ScreenRuntimeKey key) {
		JsonObject object = new JsonObject();
		if (key == null) {
			return object;
		}
		object.addProperty("dimension", key.dimension() == null ? "" : key.dimension().identifier().toString());
		BlockPos pos = key.pos();
		object.addProperty("x", pos == null ? 0 : pos.getX());
		object.addProperty("y", pos == null ? 0 : pos.getY());
		object.addProperty("z", pos == null ? 0 : pos.getZ());
		object.addProperty("facing", key.facing() == null ? "" : key.facing().getName());
		return object;
	}

	private static ScreenRuntimeKey readScreenKey(JsonObject object) {
		if (object == null) {
			return null;
		}
		ResourceKey<Level> dimension = readDimension(readString(object, "dimension"));
		Direction facing = Direction.byName(readString(object, "facing"));
		if (dimension == null || facing == null) {
			return null;
		}
		return new ScreenRuntimeKey(
				dimension,
				new BlockPos(readInt(object, "x", 0), readInt(object, "y", 0), readInt(object, "z", 0)),
				facing
		);
	}

	private static ResourceKey<Level> readDimension(String rawId) {
		Identifier identifier = rawId == null || rawId.isBlank() ? null : Identifier.tryParse(rawId);
		return identifier == null ? null : ResourceKey.create(Registries.DIMENSION, identifier);
	}

	private static String screenKeyId(ScreenRuntimeKey key) {
		if (key == null) {
			return "";
		}
		BlockPos pos = key.pos();
		return (key.dimension() == null ? "" : key.dimension().identifier())
				+ ":"
				+ (pos == null ? "0,0,0" : pos.getX() + "," + pos.getY() + "," + pos.getZ())
				+ ":"
				+ key.facing();
	}

	private static String formatTelegramTicket(SupportTicket ticket, String message, List<SupportAttachment> attachments) {
		StringBuilder builder = new StringBuilder();
		builder.append("Игрок: ").append(ticket.playerName());
		String safeMessage = truncateTelegramMessage(message);
		if (!safeMessage.isBlank()) {
			builder.append("\n\n").append(safeMessage);
		}
		if (attachments != null && !attachments.isEmpty()) {
			builder.append("\n\nВложения:");
			for (SupportAttachment attachment : attachments) {
				if (attachment == null) {
					continue;
				}
				builder.append("\n").append(attachment.title());
				if (attachment.subtitle() != null && !attachment.subtitle().isBlank()) {
					builder.append(" (").append(attachment.subtitle()).append(")");
				}
				if (attachment.url() != null && !attachment.url().isBlank()) {
					builder.append("\n").append(attachment.url());
				}
			}
		}
		return truncateTelegramMessage(builder.toString());
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

	private static String truncateTelegramCaption(String value) {
		return truncateForChat(value, MAX_TELEGRAM_CAPTION_LENGTH);
	}

	private static Path telegramAttachmentPath(SupportAttachment attachment) {
		if (attachment == null || attachment.localMediaKey() == null || attachment.localMediaKey().isBlank()) {
			return null;
		}
		Path path = MonitorMediaApp.savedGalleryMediaPath(attachment.localMediaKey().trim());
		return path != null && Files.isRegularFile(path) ? path : null;
	}

	private static String telegramFileName(SupportAttachment attachment, Path file) {
		String title = attachment == null || attachment.title() == null ? "" : attachment.title().trim();
		String fallback = file == null || file.getFileName() == null ? "attachment" : file.getFileName().toString();
		String name = title.isBlank() ? fallback : title;
		name = name
				.replace("/", "_")
				.replace("\\", "_")
				.replace("\u0000", "_")
				.trim();
		if (name.isBlank()) {
			name = fallback;
		}
		String extension = fileExtension(file);
		if (!extension.isBlank() && !name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
			name += extension;
		}
		return name.length() > 120 ? name.substring(0, 120) : name;
	}

	private static String fileExtension(Path file) {
		if (file == null || file.getFileName() == null) {
			return "";
		}
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		if (dot < 0 || dot == name.length() - 1) {
			return "";
		}
		return name.substring(dot);
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

	private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
		if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
			return fallback;
		}
		try {
			return object.get(key).getAsBoolean();
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static final class SupportRuntimeState {
		private final List<SupportChatMessage> messages = new ArrayList<>();
		private final List<SupportAttachment> pendingAttachments = new ArrayList<>();
		private long version;
		private boolean greetingAdded;
		private int scrollOffset;
		private int attachmentPickerScroll;
		private boolean attachmentPickerOpen;
		private String statusText = "";
	}

	private record SupportChatMessage(
			boolean fromSupport,
			String author,
			String text,
			long createdAtMillis,
			long ticketId,
			List<SupportAttachment> attachments,
			boolean delivered,
			boolean read
	) {
		private SupportChatMessage withRead(boolean nextRead) {
			return new SupportChatMessage(
					this.fromSupport,
					this.author,
					this.text,
					this.createdAtMillis,
					this.ticketId,
					this.attachments,
					this.delivered,
					nextRead
			);
		}
	}

	private record SupportAttachment(String id, String title, String subtitle, String url, String localMediaKey, GalleryItemKind kind, BufferedImage preview) {
	}

	private record SupportBubbleMetrics(
			int avatarSize,
			int avatarGap,
			int padX,
			int padY,
			int bubbleWidth,
			int bubbleHeight,
			int attachmentHeight,
			int attachmentGap,
			int lineHeight,
			List<String> lines,
			boolean metaInline
	) {
	}

	private record SupportTicket(long id, ScreenRuntimeKey key, UUID playerUuid, String playerName, long createdAtMillis) {
	}

	private record TelegramMessageKey(long chatId, int messageId) {
	}

	private record TelegramReply(long ticketId, String message) {
	}

	private record ObservedSupportUiTarget(ScreenComponent component, UiLayout layout, UiPoint touchPoint) {
	}
}
