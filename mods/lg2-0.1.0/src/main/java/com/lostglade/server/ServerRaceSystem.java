package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilityConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.literal;

public final class ServerRaceSystem {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final String DIALOG_NAMESPACE = "lg2";
	private static final String DIALOG_PATH_PREFIX = "race_menu_";
	private static final String DIALOGS_PACK_DIR = "lg2_race_dialogs";
	private static final String DIALOGS_PACK_ID = "file/" + DIALOGS_PACK_DIR;
	private static final String DIALOGS_PACK_DESCRIPTION = "LG2 generated race dialogs";
	private static final String QUICK_ACTIONS_ROUTER_DIALOG_ID = "lg2:race_quick_actions";
	private static final int DEFAULT_DIALOG_COLUMNS = 2;
	private static final int DEFAULT_ACTION_WIDTH = 220;
	private static final int EXIT_ACTION_WIDTH = 200;

	private static final Map<String, PlayerRaceConfig> RACES_BY_NICKNAME = new LinkedHashMap<>();
	private static final Map<String, String> DIALOG_ID_BY_NICKNAME = new LinkedHashMap<>();
	private static final Map<String, String> GENERATED_DIALOG_JSON_BY_PATH = new LinkedHashMap<>();
	private static final Map<UUID, EnumMap<RaceAbilitySlot, Long>> COOLDOWNS = new HashMap<>();

	private ServerRaceSystem() {
	}

	public static void register() {
		rebuildCache();
		registerCommands();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RaceConfig.load();
			rebuildCache();
			syncGeneratedDialogs(server, true);
			Lg2.LOGGER.info("Loaded {} configured personal races", RACES_BY_NICKNAME.size());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RACES_BY_NICKNAME.clear();
			DIALOG_ID_BY_NICKNAME.clear();
			GENERATED_DIALOG_JSON_BY_PATH.clear();
			COOLDOWNS.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				getRace(handler.player).ifPresent(race ->
						Lg2.LOGGER.info("Assigned personal race '{}' to {}", race.id, handler.player.getGameProfile().name())
				)
		);
	}

	public static void reload() {
		RaceConfig.load();
		rebuildCache();
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(literal("race")
						.then(literal("menu").executes(ServerRaceSystem::openMenu))
						.then(literal("reload")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(ServerRaceSystem::reloadFromCommand)
						)
						.then(literal("use")
								.then(literal("attack").executes(context -> useAbility(context, RaceAbilitySlot.ATTACK)))
								.then(literal("defense").executes(context -> useAbility(context, RaceAbilitySlot.DEFENSE)))
								.then(literal("ability").executes(context -> useAbility(context, RaceAbilitySlot.UNIQUE_ABILITY)))
								.then(literal("shnyaga").executes(context -> useAbility(context, RaceAbilitySlot.SHNYAGA)))
						)
				)
		);
	}

	private static int reloadFromCommand(CommandContext<CommandSourceStack> context) {
		reload();
		syncGeneratedDialogs(context.getSource().getServer(), true);
		context.getSource().sendSuccess(() -> Component.literal("Race config and race dialogs reloaded"), true);
		return 1;
	}

	private static int openMenu(CommandContext<CommandSourceStack> context) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.lg2.race.player_only"));
			return 0;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_race"));
			return 0;
		}

		String dialogId = DIALOG_ID_BY_NICKNAME.get(normalizeNickname(player.getGameProfile().name()));
		if (dialogId == null || dialogId.isBlank()) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_menu"));
			return 0;
		}

		if (!runDialogCommand(player, "show @s " + dialogId)) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_menu"));
			Lg2.LOGGER.warn("Failed to open race dialog '{}' for {}", dialogId, player.getGameProfile().name());
			return 0;
		}

		return 1;
	}
	private static int useAbility(CommandContext<CommandSourceStack> context, RaceAbilitySlot slot) {
		ServerPlayer player = context.getSource().getPlayer();
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("message.lg2.race.player_only"));
			return 0;
		}

		Optional<PlayerRaceConfig> raceOptional = getRace(player);
		if (raceOptional.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.no_race"));
			return 0;
		}

		PlayerRaceConfig race = raceOptional.get();
		RaceAbilityConfig ability = getAbility(race, slot);
		if (!ability.enabled) {
			player.sendSystemMessage(Component.translatable("message.lg2.race.ability_disabled", Component.literal(ability.name)));
			return 0;
		}

		long now = player.level().getGameTime();
		long readyAt = getCooldownReadyAt(player.getUUID(), slot);
		if (readyAt > now) {
			long ticksLeft = readyAt - now;
			double secondsLeft = ticksLeft / 20.0D;
			player.sendSystemMessage(Component.translatable("message.lg2.race.cooldown", Component.literal(ability.name), Component.literal(String.format(Locale.ROOT, "%.1f", secondsLeft))));
			return 0;
		}

		double cooldownSeconds = Math.max(0.0D, ability.cooldownSeconds);
		if (cooldownSeconds > 0.0D) {
			setCooldownReadyAt(player.getUUID(), slot, now + (long)Math.ceil(cooldownSeconds * 20.0D));
		}

		player.sendSystemMessage(Component.translatable("message.lg2.race.used", Component.literal(ability.name), Component.literal(race.displayName)));
		Lg2.LOGGER.info("Player {} used race ability '{}' from race '{}'", player.getGameProfile().name(), ability.abilityId, race.id);
		return 1;
	}

	private static long getCooldownReadyAt(UUID playerId, RaceAbilitySlot slot) {
		EnumMap<RaceAbilitySlot, Long> cooldowns = COOLDOWNS.get(playerId);
		if (cooldowns == null) {
			return 0L;
		}
		return cooldowns.getOrDefault(slot, 0L);
	}

	private static void setCooldownReadyAt(UUID playerId, RaceAbilitySlot slot, long readyAtTick) {
		COOLDOWNS.computeIfAbsent(playerId, ignored -> new EnumMap<>(RaceAbilitySlot.class)).put(slot, readyAtTick);
	}

	public static Optional<PlayerRaceConfig> getRace(ServerPlayer player) {
		return player == null ? Optional.empty() : getRace(player.getGameProfile().name());
	}

	public static Optional<PlayerRaceConfig> getRace(String nickname) {
		if (nickname == null || nickname.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(RACES_BY_NICKNAME.get(normalizeNickname(nickname)));
	}

	public static Optional<RaceAbilityConfig> getAbility(ServerPlayer player, RaceAbilitySlot slot) {
		return getRace(player).map(race -> getAbility(race, slot));
	}

	public static RaceAbilityConfig getAbility(PlayerRaceConfig race, RaceAbilitySlot slot) {
		return switch (slot) {
			case ATTACK -> race.attack;
			case DEFENSE -> race.defense;
			case UNIQUE_ABILITY -> race.uniqueAbility;
			case SHNYAGA -> race.shnyaga;
			case STOCK -> race.stock;
		};
	}

	public static Collection<PlayerRaceConfig> getAllRaces() {
		return Collections.unmodifiableCollection(RACES_BY_NICKNAME.values());
	}

	private static void rebuildCache() {
		RACES_BY_NICKNAME.clear();
		DIALOG_ID_BY_NICKNAME.clear();
		GENERATED_DIALOG_JSON_BY_PATH.clear();

		Set<String> usedDialogPaths = new HashSet<>();
		for (PlayerRaceConfig race : RaceConfig.get().races) {
			if (race == null || !race.enabled || race.ownerNickname == null || race.ownerNickname.isBlank()) {
				continue;
			}

			String normalizedNickname = normalizeNickname(race.ownerNickname);
			String dialogPath = buildDialogPath(race, usedDialogPaths);
			String dialogId = DIALOG_NAMESPACE + ":" + dialogPath;
			DIALOG_ID_BY_NICKNAME.put(normalizedNickname, dialogId);
			GENERATED_DIALOG_JSON_BY_PATH.put(dialogPath, buildRaceDialogJson(race));

			PlayerRaceConfig previous = RACES_BY_NICKNAME.put(normalizedNickname, race);
			if (previous != null && previous != race) {
				Lg2.LOGGER.warn(
						"Duplicate personal race owner '{}' found. Race '{}' overrides '{}'",
						race.ownerNickname,
						race.id,
						previous.id
				);
			}
		}
	}

	private static String normalizeNickname(String nickname) {
		return nickname.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean runDialogCommand(ServerPlayer player, String dialogCommandTail) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}

		CommandSourceStack source = server.createCommandSourceStack()
				.withPermission(PermissionSet.ALL_PERMISSIONS)
				.withSuppressedOutput();
		String command = "execute as " + player.getGameProfile().name() + " run dialog " + dialogCommandTail;
		try {
			server.getCommands().performPrefixedCommand(source, command);
			return true;
		} catch (Exception exception) {
			Lg2.LOGGER.error("Failed to execute dialog command '{}'", command, exception);
			return false;
		}
	}

	private static void syncGeneratedDialogs(MinecraftServer server, boolean requestReload) {
		boolean changed = writeGeneratedDialogsPack(server);
		if (!requestReload) {
			return;
		}

		List<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
		if (!selected.contains(DIALOGS_PACK_ID)) {
			selected.add(DIALOGS_PACK_ID);
			changed = true;
		}

		if (!changed) {
			return;
		}

		server.reloadResources(selected).whenComplete((unused, throwable) -> {
			if (throwable != null) {
				Lg2.LOGGER.error("Failed to reload generated race dialogs datapack", throwable);
				return;
			}
			Lg2.LOGGER.info("Reloaded generated race dialogs datapack");
		});
	}

	private static boolean writeGeneratedDialogsPack(MinecraftServer server) {
		Path packRoot = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(DIALOGS_PACK_DIR);
		Path dialogDir = packRoot.resolve("data").resolve(DIALOG_NAMESPACE).resolve("dialog");
		Path quickActionsTagFile = packRoot.resolve("data").resolve("minecraft").resolve("tags").resolve("dialog").resolve("quick_actions.json");
		boolean changed = false;

		try {
			Files.createDirectories(dialogDir);
			changed |= writeIfChanged(packRoot.resolve("pack.mcmeta"), buildPackMcmetaJson());
			changed |= writeIfChanged(quickActionsTagFile, buildQuickActionsTagJson());

			Set<String> expectedFileNames = new HashSet<>();
			for (Map.Entry<String, String> entry : GENERATED_DIALOG_JSON_BY_PATH.entrySet()) {
				String fileName = entry.getKey() + ".json";
				expectedFileNames.add(fileName);
				changed |= writeIfChanged(dialogDir.resolve(fileName), entry.getValue());
			}

			try (Stream<Path> files = Files.list(dialogDir)) {
				for (Path file : files.toList()) {
					String fileName = file.getFileName().toString();
					if (!fileName.startsWith(DIALOG_PATH_PREFIX) || !fileName.endsWith(".json")) {
						continue;
					}
					if (!expectedFileNames.contains(fileName)) {
						Files.deleteIfExists(file);
						changed = true;
					}
				}
			}
		} catch (IOException exception) {
			Lg2.LOGGER.error("Failed to write generated race dialogs datapack", exception);
		}

		return changed;
	}

	private static boolean writeIfChanged(Path file, String content) throws IOException {
		String existing = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
		if (existing != null && existing.equals(content)) {
			return false;
		}

		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return true;
	}

	private static String buildPackMcmetaJson() {
		int packFormat = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA).major();
		Map<String, Object> pack = new LinkedHashMap<>();
		pack.put("pack_format", packFormat);
		pack.put("min_format", packFormat);
		pack.put("max_format", packFormat);
		pack.put("description", DIALOGS_PACK_DESCRIPTION);

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("pack", pack);
		return GSON.toJson(root);
	}

	private static String buildQuickActionsTagJson() {
		List<String> values = new ArrayList<>();
		values.add(QUICK_ACTIONS_ROUTER_DIALOG_ID);

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("replace", Boolean.TRUE);
		root.put("values", values);
		return GSON.toJson(root);
	}

	private static String buildRaceDialogJson(PlayerRaceConfig race) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "minecraft:multi_action");
		root.put("title", textComponent(race.displayName));
		root.put("external_title", textComponent(race.displayName));
		root.put("can_close_with_escape", Boolean.TRUE);
		root.put("pause", Boolean.FALSE);
		root.put("columns", DEFAULT_DIALOG_COLUMNS);

		List<Map<String, Object>> actions = new ArrayList<>();
		appendAbilityAction(actions, race.attack, "/race use attack");
		appendAbilityAction(actions, race.defense, "/race use defense");
		appendAbilityAction(actions, race.uniqueAbility, "/race use ability");
		appendAbilityAction(actions, race.shnyaga, "/race use shnyaga");

		if (actions.isEmpty()) {
			Map<String, Object> action = new LinkedHashMap<>();
			action.put("label", textComponent("No active abilities"));
			action.put("tooltip", textComponent("This race has no active ability buttons"));
			action.put("width", DEFAULT_ACTION_WIDTH);
			action.put("action", runCommandAction("/race use ability"));
			actions.add(action);
		}

		root.put("actions", actions);

		Map<String, Object> exitAction = new LinkedHashMap<>();
		exitAction.put("label", translateComponent("gui.back"));
		exitAction.put("width", EXIT_ACTION_WIDTH);
		root.put("exit_action", exitAction);

		return GSON.toJson(root);
	}

	private static void appendAbilityAction(List<Map<String, Object>> actions, RaceAbilityConfig ability, String command) {
		if (ability == null || !ability.enabled) {
			return;
		}

		Map<String, Object> action = new LinkedHashMap<>();
		action.put("label", textComponent(nonBlank(ability.name, "Ability")));
		action.put("tooltip", textComponent(nonBlank(ability.description, nonBlank(ability.name, "Ability"))));
		action.put("width", DEFAULT_ACTION_WIDTH);
		action.put("action", runCommandAction(command));
		actions.add(action);
	}

	private static Map<String, Object> runCommandAction(String command) {
		Map<String, Object> action = new LinkedHashMap<>();
		action.put("type", "minecraft:run_command");
		action.put("command", command);
		return action;
	}

	private static Map<String, Object> textComponent(String text) {
		Map<String, Object> component = new LinkedHashMap<>();
		component.put("text", nonBlank(text, ""));
		return component;
	}

	private static Map<String, Object> translateComponent(String key) {
		Map<String, Object> component = new LinkedHashMap<>();
		component.put("translate", key);
		return component;
	}

	private static String nonBlank(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? fallback : normalized;
	}

	private static String buildDialogPath(PlayerRaceConfig race, Set<String> usedPaths) {
		String base = DIALOG_PATH_PREFIX + sanitizePath(race.id) + "_" + sanitizePath(race.ownerNickname);
		String candidate = base;
		int index = 2;
		while (usedPaths.contains(candidate)) {
			candidate = base + "_" + index;
			index++;
		}
		usedPaths.add(candidate);
		return candidate;
	}

	private static String sanitizePath(String value) {
		if (value == null || value.isBlank()) {
			return "race";
		}

		StringBuilder builder = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char ch = Character.toLowerCase(value.charAt(i));
			if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
				builder.append(ch);
			} else {
				builder.append('_');
			}
		}

		String sanitized = builder.toString().replaceAll("_+", "_");
		sanitized = sanitized.replaceAll("^_+", "");
		sanitized = sanitized.replaceAll("_+$", "");
		return sanitized.isEmpty() ? "race" : sanitized;
	}
}

