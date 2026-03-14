package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.datafixers.util.Pair;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.lostglade.Lg2;
import com.lostglade.config.UpgradeUiConfig;
import com.lostglade.item.ModItems;
import com.lostglade.server.glitch.InventoryTextureShuffleGlitch;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.LevelResource;
import xyz.nucleoid.packettweaker.PacketContext;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ServerUpgradeUiSystem {
	private static final String STATE_FILE_NAME = "lg2-upgrade-state.json";
	private static final String TITLE_OVERLAY_RESET = "\ue940\ue940\ue941\ue943";
	private static final Identifier TOOLTIP_STYLE_ID = Objects.requireNonNull(Identifier.tryParse("lg2:upgrade_card"));
	private static final FontDescription TOOLTIP_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:upgrade_tooltip"))
	);
	private static final String TOOLTIP_ICON_INFO = "\ue980";
	private static final String TOOLTIP_ICON_COIN = "\ue981";
	private static final String NO_PACK_COIN = "₿";
	private static final int MAIN_BALANCE_CENTER_X = 127;
	private static final int BALANCE_DIGIT_WIDTH = 6;
	private static final int TOOLTIP_DESCRIPTION_WRAP = 42;
	private static final int TOOLTIP_DESCRIPTION_WRAP_CJK = 22;
	private static final int TOOLTIP_REQUIREMENTS_WRAP = 46;
	private static final int TOOLTIP_REQUIREMENTS_WRAP_CJK = 24;
	private static final Gson STATE_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Map<String, Integer>> PLAYER_UPGRADE_LEVELS = new HashMap<>();
	private static final Map<String, Integer> LEGACY_GLOBAL_UPGRADE_LEVELS = new HashMap<>();
	private static boolean stateLoaded = false;
	private static boolean stateDirty = false;

	private ServerUpgradeUiSystem() {
	}

	public static void register() {
		UpgradeUiConfig.load();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			loadState(server);
			UpgradeUiConfig.load();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(ServerUpgradeUiSystem::saveState);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						Commands.literal("serverupgradeui")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.then(Commands.literal("reload")
										.executes(context -> {
											UpgradeUiConfig.load();
											context.getSource().sendSuccess(() -> Component.literal("Reloaded upgrade UI config"), true);
											return 1;
										}))
								.then(Commands.literal("reset")
										.then(Commands.argument("targets", EntityArgument.players())
												.then(Commands.literal("all")
														.executes(context -> {
															int changed = resetAllPurchases(
																	context.getSource().getServer(),
																	EntityArgument.getPlayers(context, "targets")
															);
															context.getSource().sendSuccess(
																	() -> Component.literal("Reset all purchases for " + changed + " player(s)"),
																	true
															);
															return changed;
														}))
												.then(Commands.argument("upgrade_id", StringArgumentType.word())
														.suggests((context, builder) -> SharedSuggestionProvider.suggest(collectKnownUpgradeIds(), builder))
														.executes(context -> {
															String upgradeId = StringArgumentType.getString(context, "upgrade_id");
															int changed = resetSinglePurchase(
																	context.getSource().getServer(),
																	EntityArgument.getPlayers(context, "targets"),
																	upgradeId
															);
															context.getSource().sendSuccess(
																	() -> Component.literal("Reset '" + upgradeId + "' for " + changed + " player(s)"),
																	true
															);
															return changed;
														}))))
				)
		);
	}

	public static boolean openRootScreen(ServerPlayer player) {
		UpgradeUiConfig.ConfigData config = UpgradeUiConfig.get();
		if (!config.enabled) {
			sendPlayerMessage(player, localizeSystem(player, "Upgrade interface is disabled.", "Интерфейс прокачки отключён."));
			return false;
		}

		return openScreen(player, config.rootScreenId);
	}

	public static boolean openScreen(ServerPlayer player, String screenId) {
		if (player == null || screenId == null || screenId.isBlank()) {
			return false;
		}

		UpgradeUiConfig.ConfigData config = UpgradeUiConfig.get();
		UpgradeUiConfig.ScreenConfig screen = config.screens.get(screenId);
		if (screen == null || !screen.enabled) {
			sendPlayerMessage(player, localizeSystem(player, "Screen not found.", "Экран не найден."));
			return false;
		}

		UpgradeUiConfig.ThemeConfig theme = config.themes.get(screen.theme);
		final UpgradeUiConfig.ThemeConfig resolvedTheme = theme == null || !theme.enabled
				? UpgradeUiConfig.ThemeConfig.defaultTheme()
				: theme;

		boolean hasPack = PolymerResourcePackUtils.hasMainPack(player);
		InventoryTextureShuffleGlitch.onUpgradeMenuOpened(player);
		Component title = buildTitle(player, screenId, hasPack, screen, resolvedTheme);
		player.openMenu(new SimpleMenuProvider(
				(syncId, inventory, menuPlayer) -> createMenu(syncId, inventory, player, screenId, screen, resolvedTheme, hasPack),
				title
		));
		hideLowerInventoryVisuals(player, player.containerMenu);
		return true;
	}

	public static boolean isUpgradeMenu(AbstractContainerMenu menu) {
		return menu instanceof UpgradeMenu;
	}

	public static boolean isUpgradeMenuOpen(ServerPlayer player) {
		return player != null && isUpgradeMenu(player.containerMenu);
	}

	public static boolean hasUpgrade(ServerPlayer player, String upgradeId) {
		return getUpgradeLevel(player, upgradeId) >= 1;
	}

	private static int resetAllPurchases(MinecraftServer server, Collection<ServerPlayer> targets) {
		if (server == null || targets == null || targets.isEmpty()) {
			return 0;
		}

		int changedPlayers = 0;
		for (ServerPlayer player : targets) {
			if (player == null) {
				continue;
			}

			Map<String, Integer> removed = PLAYER_UPGRADE_LEVELS.remove(player.getUUID().toString());
			if (removed == null || removed.isEmpty()) {
				continue;
			}

			changedPlayers++;
			stateDirty = true;
			refreshUpgradeMenu(player);
		}

		saveState(server);
		return changedPlayers;
	}

	private static int resetSinglePurchase(MinecraftServer server, Collection<ServerPlayer> targets, String upgradeId) {
		if (server == null || targets == null || targets.isEmpty() || upgradeId == null || upgradeId.isBlank()) {
			return 0;
		}

		int changedPlayers = 0;
		for (ServerPlayer player : targets) {
			if (player == null) {
				continue;
			}

			Map<String, Integer> levels = PLAYER_UPGRADE_LEVELS.get(player.getUUID().toString());
			if (levels == null || levels.remove(upgradeId) == null) {
				continue;
			}

			if (levels.isEmpty()) {
				PLAYER_UPGRADE_LEVELS.remove(player.getUUID().toString());
			}
			changedPlayers++;
			stateDirty = true;
			refreshUpgradeMenu(player);
		}

		saveState(server);
		return changedPlayers;
	}

	private static Iterable<String> collectKnownUpgradeIds() {
		LinkedHashSet<String> ids = new LinkedHashSet<>();
		for (UpgradeUiConfig.ScreenConfig screen : UpgradeUiConfig.get().screens.values()) {
			if (screen == null || screen.buttons == null) {
				continue;
			}
			for (UpgradeUiConfig.ButtonConfig button : screen.buttons.values()) {
				if (button == null || button.upgradeId == null || button.upgradeId.isBlank()) {
					continue;
				}
				ids.add(button.upgradeId);
			}
		}
		return ids;
	}

	private static void refreshUpgradeMenu(ServerPlayer player) {
		if (!(player.containerMenu instanceof UpgradeMenu menu)) {
			return;
		}
		openScreen(player, menu.screenId);
	}

	private static ChestMenu createMenu(
			int syncId,
			Inventory inventory,
			ServerPlayer viewer,
			String screenId,
			UpgradeUiConfig.ScreenConfig screen,
			UpgradeUiConfig.ThemeConfig theme,
			boolean hasPack
	) {
		SimpleContainer container = new SimpleContainer(screen.rows * 9);
		fillContainer(container, viewer, screenId, screen, theme, hasPack);
		return new UpgradeMenu(syncId, inventory, container, screenId, screen.rows, viewer, hasPack);
	}

	private static void fillContainer(
			SimpleContainer container,
			ServerPlayer viewer,
			String screenId,
			UpgradeUiConfig.ScreenConfig screen,
			UpgradeUiConfig.ThemeConfig theme,
			boolean hasPack
	) {
		if (theme.fillEmptySlots) {
			ItemStack filler = buildFillerStack(theme.fillerIcon, hasPack);
			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				container.setItem(slot, filler.copy());
			}
		}

		for (Map.Entry<String, UpgradeUiConfig.ButtonConfig> entry : screen.buttons.entrySet()) {
			String buttonId = entry.getKey();
			UpgradeUiConfig.ButtonConfig button = entry.getValue();
			if (button == null || !button.enabled || button.slot < 0 || button.slot >= container.getContainerSize()) {
				continue;
			}
			if (shouldHideButtonVisual(screenId, buttonId)) {
				continue;
			}
			if (isPureDecorButton(button)) {
				continue;
			}
			ItemStack stack = buildButtonStack(viewer, screenId, buttonId, button, hasPack);
			container.setItem(getDisplaySlot(button), stack);
		}
	}

	private static int getDisplaySlot(UpgradeUiConfig.ButtonConfig button) {
		if (button == null) {
			return 0;
		}

		int width = Math.max(1, button.hitboxWidth);
		int height = Math.max(1, button.hitboxHeight);
		int startColumn = button.slot % 9;
		int startRow = button.slot / 9;
		int centerColumn = startColumn + ((width - 1) / 2);
		int centerRow = startRow + ((height - 1) / 2);
		return (centerRow * 9) + centerColumn;
	}

	private static ItemStack buildFillerStack(UpgradeUiConfig.IconConfig icon, boolean hasPack) {
		ItemStack stack = createBaseStack(icon, hasPack);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return stack;
	}

	private static ItemStack buildButtonStack(
			ServerPlayer viewer,
			String screenId,
			String buttonId,
			UpgradeUiConfig.ButtonConfig button,
			boolean hasPack
	) {
		ButtonState state = getButtonState(viewer, button);
		UpgradeUiConfig.IconConfig icon = switch (state) {
			case LOCKED -> button.lockedIcon;
			case MAXED -> button.maxedIcon;
			case ACTIVE -> button.icon;
		};

		ItemStack stack = createBaseStack(icon, hasPack);
		Map<String, String> placeholders = buildPlaceholders(viewer, screenId, buttonId, button, state);
		stack.set(DataComponents.CUSTOM_NAME, buildTooltipNameComponent(viewer, button, state, placeholders, hasPack));

		List<Component> loreLines = buildTooltipLore(viewer, button, state, placeholders, hasPack);
		if (!loreLines.isEmpty()) {
			ItemLore lore = ItemLore.EMPTY;
			for (Component line : loreLines) {
				lore = lore.withLineAdded(line);
			}
			stack.set(DataComponents.LORE, lore);
		}
		if (hasPack) {
			stack.set(DataComponents.TOOLTIP_STYLE, TOOLTIP_STYLE_ID);
		}

		return stack;
	}

	private static Component buildTooltipNameComponent(
			ServerPlayer viewer,
			UpgradeUiConfig.ButtonConfig button,
			ButtonState state,
			Map<String, String> placeholders,
			boolean hasPack
	) {
		String name = applyPlaceholders(resolveLocalizedText(viewer, button.name.values), placeholders);
		int color = switch (state) {
			case LOCKED -> 0xB6C0D0;
			case MAXED -> 0xA8F2C0;
			case ACTIVE -> 0xF4E3BA;
		};
		return styledTooltipText(name, color, true, hasPack);
	}

	private static List<Component> buildTooltipLore(
			ServerPlayer viewer,
			UpgradeUiConfig.ButtonConfig button,
			ButtonState state,
			Map<String, String> placeholders,
			boolean hasPack
	) {
		List<Component> result = new ArrayList<>();
		List<String> description = compactLocalizedLines(
				resolveLocalizedLines(viewer, button.lore.values),
				placeholders,
				tooltipWrapWidth(viewer, TOOLTIP_DESCRIPTION_WRAP, TOOLTIP_DESCRIPTION_WRAP_CJK),
				2
		);
		for (int i = 0; i < description.size(); i++) {
			String prefix = i == 0 ? tooltipIconOrFallback(hasPack, TOOLTIP_ICON_INFO, "i") : "";
			result.add(buildTooltipLine(
					prefix,
					0x91B8D9,
					description.get(i),
					0xD8E0EA,
					hasPack
			));
		}

		UpgradeUiConfig.ButtonType type = UpgradeUiConfig.ButtonType.byId(button.type);
		if (type == UpgradeUiConfig.ButtonType.NONE) {
			return result;
		}

		if (type == UpgradeUiConfig.ButtonType.PURCHASE_UPGRADE) {
			result.add(buildPriceLine(viewer, button, state, hasPack));
		}
		return result;
	}

	private static Component buildPriceLine(
			ServerPlayer viewer,
			UpgradeUiConfig.ButtonConfig button,
			ButtonState state,
			boolean hasPack
	) {
		if (button.pricesBitcoins == null || button.pricesBitcoins.isEmpty()) {
			return styledTooltipText("", 0xFFFFFF, false, hasPack);
		}

		int currentLevel = getUpgradeLevel(viewer, button.upgradeId);
		int index = Math.max(0, Math.min(button.pricesBitcoins.size() - 1, state == ButtonState.MAXED ? button.pricesBitcoins.size() - 1 : currentLevel));
		int price = button.pricesBitcoins.get(index);
		boolean affordable = countBitcoins(viewer) >= price;
		int priceColor = state == ButtonState.MAXED
				? 0xBFAE89
				: (affordable ? 0xF0D39B : 0x8E98A3);

		if (!hasPack) {
			MutableComponent line = Component.empty();
			line.append(styledTooltipText(NO_PACK_COIN + " ", priceColor, false, false));
			line.append(styledTooltipText(Integer.toString(price), priceColor, true, false, state == ButtonState.MAXED));
			return line;
		}

		MutableComponent line = Component.empty();
		line.append(styledTooltipText(TOOLTIP_ICON_COIN + " ", 0xFFFFFF, false, true));
		line.append(styledTooltipText(Integer.toString(price), priceColor, true, true, state == ButtonState.MAXED));
		return line;
	}

	private static Component buildTooltipLine(
			String prefix,
			int prefixColor,
			String text,
			int textColor,
			boolean hasPack
	) {
		MutableComponent line = Component.empty();
		if (prefix != null && !prefix.isBlank()) {
			line.append(styledTooltipText(prefix + " ", prefixColor, false, hasPack));
		}
		line.append(styledTooltipText(text, textColor, false, hasPack));
		return line;
	}

	private static Component styledTooltipText(String text, int color, boolean bold, boolean hasPack) {
		return styledTooltipText(text, color, bold, hasPack, false);
	}

	private static Component styledTooltipText(String text, int color, boolean bold, boolean hasPack, boolean strikethrough) {
		return Component.literal(safeString(text)).withStyle(style -> {
			style = style.withColor(color).withItalic(false).withBold(bold).withStrikethrough(strikethrough);
			if (hasPack) {
				style = style.withFont(TOOLTIP_FONT);
			}
			return style;
		});
	}

	private static List<String> compactLocalizedLines(
			List<String> lines,
			Map<String, String> placeholders,
			int wrapWidth,
			int maxLines
	) {
		List<String> cleaned = new ArrayList<>();
		if (lines != null) {
			for (String line : lines) {
				String resolved = applyPlaceholders(safeString(line).trim(), placeholders);
				if (!resolved.isBlank()) {
					cleaned.add(resolved);
				}
			}
		}
		if (cleaned.isEmpty()) {
			return List.of();
		}
		return wrapText(String.join(" ", cleaned), wrapWidth, maxLines);
	}

	private static List<String> wrapText(String text, int maxWidth, int maxLines) {
		String normalized = safeString(text).replaceAll("\\s+", " ").trim();
		if (normalized.isEmpty()) {
			return List.of();
		}

		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : normalized.split(" ")) {
			if (current.isEmpty()) {
				current.append(word);
				continue;
			}
			if (current.length() + 1 + word.length() <= Math.max(8, maxWidth)) {
				current.append(' ').append(word);
				continue;
			}
			lines.add(current.toString());
			if (lines.size() >= maxLines) {
				return trimWrappedLines(lines, current.toString(), word, normalized);
			}
			current = new StringBuilder(word);
		}
		if (!current.isEmpty() && lines.size() < maxLines) {
			lines.add(current.toString());
		}
		return lines;
	}

	private static List<String> trimWrappedLines(
			List<String> lines,
			String overflowedLine,
			String currentWord,
			String originalText
	) {
		if (lines.isEmpty()) {
			return List.of(ellipsis(originalText));
		}
		int lastIndex = lines.size() - 1;
		lines.set(lastIndex, ellipsis(lines.get(lastIndex)));
		return lines;
	}

	private static String ellipsis(String value) {
		String trimmed = safeString(value).trim();
		if (trimmed.isEmpty() || trimmed.endsWith("...")) {
			return trimmed;
		}
		if (trimmed.endsWith(".")) {
			return trimmed.substring(0, trimmed.length() - 1) + "...";
		}
		return trimmed + "...";
	}

	private static int tooltipWrapWidth(ServerPlayer player, int regularWidth, int cjkWidth) {
		String locale = normalizeLocale(player);
		return locale.startsWith("ja") || locale.startsWith("ko") || locale.startsWith("zh") ? cjkWidth : regularWidth;
	}

	private static String tooltipIconOrFallback(boolean hasPack, String icon, String fallback) {
		return hasPack ? icon : fallback;
	}

	private static String buildRequirementSummary(ServerPlayer player, UpgradeUiConfig.ButtonConfig button) {
		List<String> parts = new ArrayList<>();
		for (UpgradeUiConfig.RequirementConfig requirement : button.requirements) {
			if (requirement == null || requirement.upgradeId == null || requirement.upgradeId.isBlank()) {
				continue;
			}
			parts.add(resolveUpgradeDisplayName(player, requirement.upgradeId));
		}
		for (UpgradeUiConfig.RequirementGroupConfig group : button.requirementGroups) {
			String summary = summarizeRequirementGroup(player, group);
			if (!summary.isBlank()) {
				parts.add(summary);
			}
		}
		return String.join("; ", parts);
	}

	private static String summarizeRequirementGroup(ServerPlayer player, UpgradeUiConfig.RequirementGroupConfig group) {
		if (group == null || group.requirements == null || group.requirements.isEmpty()) {
			return "";
		}

		List<String> names = new ArrayList<>();
		for (UpgradeUiConfig.RequirementConfig requirement : group.requirements) {
			if (requirement == null || requirement.upgradeId == null || requirement.upgradeId.isBlank()) {
				continue;
			}
			names.add(resolveUpgradeDisplayName(player, requirement.upgradeId));
		}
		if (names.isEmpty()) {
			return "";
		}

		if (UpgradeUiConfig.RequirementMode.AT_LEAST.id.equals(group.mode)) {
			return localizeTooltip(
					player,
					group.minSatisfiedRequirements + " of: " + String.join(", ", names),
					group.minSatisfiedRequirements + " из: " + String.join(", ", names),
					group.minSatisfiedRequirements + " з: " + String.join(", ", names),
					group.minSatisfiedRequirements + " 個: " + String.join("、", names),
					group.minSatisfiedRequirements + " изъ: " + String.join(", ", names)
			);
		}
		return String.join(", ", names);
	}

	private static String resolveUpgradeDisplayName(ServerPlayer player, String upgradeId) {
		if (upgradeId == null || upgradeId.isBlank()) {
			return "";
		}
		for (UpgradeUiConfig.ScreenConfig screen : UpgradeUiConfig.get().screens.values()) {
			if (screen == null || screen.buttons == null) {
				continue;
			}
			for (UpgradeUiConfig.ButtonConfig candidate : screen.buttons.values()) {
				if (candidate == null || !upgradeId.equals(candidate.upgradeId) || candidate.name == null || candidate.name.values == null) {
					continue;
				}
				String localized = resolveLocalizedText(player, candidate.name.values);
				if (!localized.isBlank()) {
					return localized;
				}
			}
		}
		return upgradeId.replace('_', ' ');
	}

	private static ItemStack createBaseStack(UpgradeUiConfig.IconConfig icon, boolean hasPack) {
		Identifier itemId = Identifier.tryParse(icon.fallbackItem);
		Item fallbackItem = itemId == null ? Items.PAPER : BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.PAPER);
		ItemStack stack = new ItemStack(fallbackItem, Math.max(1, Math.min(64, icon.count)));

		if (hasPack && icon.packModel != null && !icon.packModel.isBlank()) {
			Identifier modelId = Identifier.tryParse(icon.packModel);
			if (modelId != null) {
				stack.set(DataComponents.ITEM_MODEL, modelId);
			}
		}

		if (icon.foil) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}

		return stack;
	}

	private static Map<String, String> buildPlaceholders(
			ServerPlayer player,
			String screenId,
			String buttonId,
			UpgradeUiConfig.ButtonConfig button,
			ButtonState state
	) {
		Map<String, String> placeholders = new LinkedHashMap<>();
		int currentLevel = getUpgradeLevel(player, button.upgradeId);
		int maxLevel = button.pricesBitcoins == null ? 0 : button.pricesBitcoins.size();
		int cost = getCurrentCost(player, button);
		int bitcoins = countBitcoins(player);

		placeholders.put("%screen_id%", safeString(screenId));
		placeholders.put("%button_id%", safeString(buttonId));
		placeholders.put("%target_screen%", safeString(button.targetScreenId));
		placeholders.put("%upgrade_id%", safeString(button.upgradeId));
		placeholders.put("%level%", Integer.toString(currentLevel));
		placeholders.put("%next_level%", Integer.toString(Math.min(maxLevel, currentLevel + 1)));
		placeholders.put("%max_level%", Integer.toString(maxLevel));
		placeholders.put("%cost%", Integer.toString(cost));
		placeholders.put("%bitcoins%", Integer.toString(bitcoins));
		placeholders.put("%status%", getLocalizedStatus(player, state, cost, bitcoins));
		return placeholders;
	}

	private static ButtonState getButtonState(ServerPlayer player, UpgradeUiConfig.ButtonConfig button) {
		if (!meetsRequirements(player, button)) {
			return ButtonState.LOCKED;
		}
		if (UpgradeUiConfig.ButtonType.PURCHASE_UPGRADE.id.equals(button.type)
				&& getUpgradeLevel(player, button.upgradeId) >= button.pricesBitcoins.size()) {
			return ButtonState.MAXED;
		}
		return ButtonState.ACTIVE;
	}

	private static boolean handleTopSlotClick(ServerPlayer player, String screenId, int slot, boolean hasPack) {
		UpgradeUiConfig.ScreenConfig screen = UpgradeUiConfig.get().screens.get(screenId);
		if (screen == null) {
			return false;
		}

		Map.Entry<String, UpgradeUiConfig.ButtonConfig> matched = null;
		for (Map.Entry<String, UpgradeUiConfig.ButtonConfig> entry : screen.buttons.entrySet()) {
			UpgradeUiConfig.ButtonConfig button = entry.getValue();
			if (button != null
					&& button.enabled
					&& !isPureDecorButton(button)
					&& !shouldHideButtonVisual(screenId, entry.getKey())
					&& isWithinButtonHitbox(slot, button, hasPack)) {
				matched = entry;
				break;
			}
		}

		if (matched == null) {
			return false;
		}

		String buttonId = matched.getKey();
		UpgradeUiConfig.ButtonConfig button = matched.getValue();
		UpgradeUiConfig.ButtonType type = UpgradeUiConfig.ButtonType.byId(button.type);
		if (type == null) {
			return false;
		}
		boolean suppressClickSound = shouldSuppressClickSound(screenId, buttonId);

		ButtonState state = getButtonState(player, button);

		switch (type) {
			case NONE -> {
				playUiClick(player, false, suppressClickSound);
				return true;
			}
			case CLOSE -> {
				player.closeContainer();
				playUiClick(player, true, suppressClickSound);
				return true;
			}
			case OPEN_SCREEN -> {
				if (state == ButtonState.LOCKED) {
					playUiClick(player, false, suppressClickSound);
					sendPlayerMessage(player, localizeSystem(player, "This section is locked.", "Этот раздел пока заблокирован."));
					return true;
				}
				if (button.targetScreenId == null || button.targetScreenId.isBlank()) {
					playUiClick(player, false, suppressClickSound);
					return true;
				}
				playUiClick(player, true, suppressClickSound);
				return openScreen(player, button.targetScreenId);
			}
			case PURCHASE_UPGRADE -> {
				return handlePurchaseClick(player, screenId, buttonId, button);
			}
			default -> {
				return false;
			}
		}
	}

	private static boolean shouldSuppressClickSound(String screenId, String buttonId) {
		return "main".equals(screenId) && "balance".equals(buttonId);
	}

	private static boolean handlePurchaseClick(
			ServerPlayer player,
			String screenId,
			String buttonId,
			UpgradeUiConfig.ButtonConfig button
	) {
		if (!meetsRequirements(player, button)) {
			playUiClick(player, false);
			sendPlayerMessage(player, localizeSystem(player, "This upgrade is locked.", "Это улучшение пока заблокировано."));
			return true;
		}

		int currentLevel = getUpgradeLevel(player, button.upgradeId);
		if (currentLevel >= button.pricesBitcoins.size()) {
			playUiClick(player, false);
			sendPlayerMessage(player, localizeSystem(player, "This upgrade is already maxed.", "Это улучшение уже прокачано до максимума."));
			return true;
		}

		int cost = button.pricesBitcoins.get(currentLevel);
		if (!consumeBitcoins(player, cost)) {
			playUiClick(player, false);
			sendPlayerMessage(player, localizeSystem(player, "Not enough bitcoins.", "Недостаточно биткоинов."));
			return true;
		}

		setUpgradeLevel(player.level().getServer(), player, button.upgradeId, currentLevel + 1);
		playPurchaseSound(player);
		sendPlayerMessage(player, localizeSystem(player, "Upgrade purchased.", "Улучшение куплено."));

		if (button.closeAfterClick) {
			player.closeContainer();
		} else {
			openScreen(player, screenId);
		}

		return true;
	}

	private static boolean meetsRequirements(ServerPlayer player, UpgradeUiConfig.ButtonConfig button) {
		for (UpgradeUiConfig.RequirementConfig requirement : button.requirements) {
			if (requirement == null || requirement.upgradeId == null || requirement.upgradeId.isBlank()) {
				continue;
			}
			if (getUpgradeLevel(player, requirement.upgradeId) < Math.max(1, requirement.minLevel)) {
				return false;
			}
		}
		for (UpgradeUiConfig.RequirementGroupConfig group : button.requirementGroups) {
			if (!meetsRequirementGroup(player, group)) {
				return false;
			}
		}
		return true;
	}

	private static boolean meetsRequirementGroup(ServerPlayer player, UpgradeUiConfig.RequirementGroupConfig group) {
		if (group == null || group.requirements == null || group.requirements.isEmpty()) {
			return true;
		}

		int validRequirements = 0;
		int satisfiedRequirements = 0;
		for (UpgradeUiConfig.RequirementConfig requirement : group.requirements) {
			if (requirement == null || requirement.upgradeId == null || requirement.upgradeId.isBlank()) {
				continue;
			}

			validRequirements++;
			if (getUpgradeLevel(player, requirement.upgradeId) >= Math.max(1, requirement.minLevel)) {
				satisfiedRequirements++;
			}
		}

		if (validRequirements <= 0) {
			return true;
		}

		UpgradeUiConfig.RequirementMode mode = UpgradeUiConfig.RequirementMode.byId(group.mode);
		if (mode == UpgradeUiConfig.RequirementMode.AT_LEAST) {
			int required = Math.max(1, Math.min(validRequirements, group.minSatisfiedRequirements));
			return satisfiedRequirements >= required;
		}
		return satisfiedRequirements >= validRequirements;
	}

	private static int getCurrentCost(ServerPlayer player, UpgradeUiConfig.ButtonConfig button) {
		if (!UpgradeUiConfig.ButtonType.PURCHASE_UPGRADE.id.equals(button.type)
				|| button.pricesBitcoins == null
				|| button.pricesBitcoins.isEmpty()) {
			return 0;
		}
		int currentLevel = getUpgradeLevel(player, button.upgradeId);
		if (currentLevel < 0 || currentLevel >= button.pricesBitcoins.size()) {
			return 0;
		}
		return button.pricesBitcoins.get(currentLevel);
	}

	private static int countBitcoins(Player player) {
		if (player == null) {
			return 0;
		}

		int total = 0;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.is(ModItems.BITCOIN)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static boolean consumeBitcoins(Player player, int amount) {
		if (amount <= 0) {
			return true;
		}
		if (countBitcoins(player) < amount) {
			return false;
		}

		int remaining = amount;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.is(ModItems.BITCOIN) || stack.isEmpty()) {
				continue;
			}

			int remove = Math.min(remaining, stack.getCount());
			stack.shrink(remove);
			if (stack.isEmpty()) {
				inventory.setItem(slot, ItemStack.EMPTY);
			}
			remaining -= remove;
		}

		inventory.setChanged();
		return remaining <= 0;
	}

	private static boolean isWithinButtonHitbox(int slot, UpgradeUiConfig.ButtonConfig button, boolean hasPack) {
		if (button == null || slot < 0) {
			return false;
		}

		int anchorSlot = button.slot;
		int width = Math.max(1, button.hitboxWidth);
		int height = Math.max(1, button.hitboxHeight);
		int startColumn = anchorSlot % 9;
		int startRow = anchorSlot / 9;
		int targetColumn = slot % 9;
		int targetRow = slot / 9;

		return targetColumn >= startColumn
				&& targetColumn < startColumn + width
				&& targetRow >= startRow
				&& targetRow < startRow + height;
	}

	private static boolean isPureDecorButton(UpgradeUiConfig.ButtonConfig button) {
		if (button == null || !UpgradeUiConfig.ButtonType.NONE.id.equals(button.type)) {
			return false;
		}
		if (button.name != null && button.name.values != null) {
			for (String value : button.name.values.values()) {
				if (value != null && !value.isBlank()) {
					return false;
				}
			}
		}
		if (button.lore != null && button.lore.values != null) {
			for (List<String> lines : button.lore.values.values()) {
				if (lines == null) {
					continue;
				}
				for (String line : lines) {
					if (line != null && !line.isBlank()) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static boolean shouldHideButtonVisual(String screenId, String buttonId) {
		return "main".equals(screenId) && "balance".equals(buttonId);
	}

	private static void hideLowerInventoryVisuals(ServerPlayer player, AbstractContainerMenu menu) {
		sendLowerInventoryVisuals(player, menu, true);
		syncHeldEquipmentVisuals(player, true);
	}

	private static void restoreLowerInventoryVisuals(ServerPlayer player, AbstractContainerMenu menu) {
		sendLowerInventoryVisuals(player, menu, false);
		if (player != null && player.inventoryMenu != null && player.inventoryMenu != menu) {
			sendLowerInventoryVisuals(player, player.inventoryMenu, false);
		}
		syncHeldEquipmentVisuals(player, false);
	}

	private static void sendLowerInventoryVisuals(ServerPlayer player, AbstractContainerMenu menu, boolean hide) {
		if (player == null || menu == null) {
			return;
		}

		Inventory inventory = player.getInventory();
		PacketContext.NotNullWithPlayer context = PacketContext.create(player);
		int stateId = menu.incrementStateId();
		for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
			Slot slot = menu.getSlot(menuSlot);
			if (slot.container != inventory) {
				continue;
			}

			int inventorySlot = slot.getContainerSlot();
			ItemStack visual = hide ? ItemStack.EMPTY : inventory.getItem(inventorySlot).copy();
			player.connection.send(new ClientboundContainerSetSlotPacket(
					menu.containerId,
					stateId,
					menuSlot,
					toClientVisualStack(visual, context)
			));
		}
	}

	private static void syncHeldEquipmentVisuals(ServerPlayer player, boolean hide) {
		if (player == null) {
			return;
		}

		PacketContext.NotNullWithPlayer context = PacketContext.create(player);
		ItemStack mainHand = hide ? ItemStack.EMPTY : toClientVisualStack(player.getMainHandItem().copy(), context);
		ItemStack offHand = hide ? ItemStack.EMPTY : toClientVisualStack(player.getOffhandItem().copy(), context);
		player.connection.send(new ClientboundSetEquipmentPacket(
				player.getId(),
				List.of(
						Pair.of(EquipmentSlot.MAINHAND, mainHand),
						Pair.of(EquipmentSlot.OFFHAND, offHand)
				)
		));
	}

	private static ItemStack toClientVisualStack(ItemStack stack, PacketContext.NotNullWithPlayer context) {
		if (stack == null || stack.isEmpty()) {
			return ItemStack.EMPTY;
		}

		ItemStack clientStack = PolymerItemUtils.getClientItemStack(stack, context);
		return clientStack.isEmpty() ? stack.copy() : clientStack.copy();
	}

	private static Component buildTitle(
			ServerPlayer player,
			String screenId,
			boolean hasPack,
			UpgradeUiConfig.ScreenConfig screen,
			UpgradeUiConfig.ThemeConfig theme
	) {
		String localizedTitle = resolveLocalizedText(player, screen.title.values);
		if (!hasPack) {
			if ("main".equals(screenId)) {
				return Component.literal(buildNoPackMainTitle(player, localizedTitle));
			}
			return Component.literal(localizedTitle);
		}

		MutableComponent title = Component.empty();
		if (theme.packTitlePrefix != null && !theme.packTitlePrefix.isBlank()) {
			title.append(packStyledLiteral(theme.packTitlePrefix));
		}
		if ("main".equals(screenId)) {
			title.append(packStyledLiteral(TITLE_OVERLAY_RESET));
			title.append(packStyledLiteral(buildHorizontalAdvance(centeredBalanceStartX(countBitcoins(player)))));
			title.append(packStyledLiteral(toPackDigitString(countBitcoins(player))));
		}
		if (!screen.hideTitleTextWhenPack && !localizedTitle.isBlank()) {
			if (!title.getString().isBlank()) {
				title.append(packStyledLiteral(" "));
			}
			title.append(packStyledLiteral(localizedTitle));
		}
		if (theme.packTitleSuffix != null && !theme.packTitleSuffix.isBlank()) {
			title.append(packStyledLiteral(theme.packTitleSuffix));
		}
		return title.getString().isBlank() ? Component.literal(localizedTitle) : title;
	}

	private static String buildNoPackMainTitle(ServerPlayer player, String localizedTitle) {
		String baseTitle = safeString(localizedTitle).isBlank() ? "Server" : localizedTitle;
		String balanceText = NO_PACK_COIN + " " + countBitcoins(player);
		int targetColumns = noPackTitleColumns(player);
		int spaces = Math.max(2, targetColumns - baseTitle.length() - balanceText.length());
		return baseTitle + " ".repeat(spaces) + balanceText;
	}

	private static int noPackTitleColumns(ServerPlayer player) {
		String locale = normalizeLocale(player);
		if (locale.startsWith("ja") || locale.startsWith("ko") || locale.startsWith("zh")) {
			return 29;
		}
		return 35;
	}

	private static MutableComponent packStyledLiteral(String value) {
		return Component.literal(value).withStyle(style -> style.withColor(0xFFFFFF).withItalic(false));
	}

	private static int centeredBalanceStartX(int bitcoinCount) {
		int digits = Integer.toString(Math.max(0, bitcoinCount)).length();
		int width = digits * BALANCE_DIGIT_WIDTH;
		return Math.max(0, MAIN_BALANCE_CENTER_X - (width / 2));
	}

	private static String buildHorizontalAdvance(int pixels) {
		if (pixels == 0) {
			return "";
		}

		int remaining = pixels;
		StringBuilder result = new StringBuilder();
		int[] values = remaining > 0
				? new int[]{64, 32, 16, 8, 4, 2, 1}
				: new int[]{-64, -32, -16, -8, -4, -2, -1};
		String[] glyphs = remaining > 0
				? new String[]{"\ue94d", "\ue94c", "\ue94b", "\ue94a", "\ue949", "\ue948", "\ue947"}
				: new String[]{"\ue940", "\ue941", "\ue942", "\ue943", "\ue944", "\ue945", "\ue946"};

		for (int index = 0; index < values.length; index++) {
			int step = values[index];
			while ((remaining > 0 && remaining >= step) || (remaining < 0 && remaining <= step)) {
				result.append(glyphs[index]);
				remaining -= step;
			}
		}
		return result.toString();
	}

	private static String toPackDigitString(int value) {
		String digits = Integer.toString(Math.max(0, value));
		StringBuilder result = new StringBuilder(digits.length());
		for (int i = 0; i < digits.length(); i++) {
			char digit = digits.charAt(i);
			if (digit >= '0' && digit <= '9') {
				result.append((char) ('\ue920' + (digit - '0')));
			}
		}
		return result.toString();
	}

	private static String resolveLocalizedText(ServerPlayer player, Map<String, String> values) {
		if (values == null || values.isEmpty()) {
			return "";
		}
		String locale = normalizeLocale(player);
		String direct = values.get(locale);
		if (direct != null) {
			return direct;
		}

		for (Map.Entry<String, String> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key != null && locale.startsWith(key.toLowerCase(Locale.ROOT))) {
				return entry.getValue();
			}
		}

		String defaultValue = values.get("default");
		if (defaultValue != null) {
			return defaultValue;
		}
		String enUsValue = values.get("en_us");
		if (enUsValue != null) {
			return enUsValue;
		}
		return values.values().iterator().next();
	}

	private static List<String> resolveLocalizedLines(ServerPlayer player, Map<String, List<String>> values) {
		if (values == null || values.isEmpty()) {
			return List.of();
		}
		String locale = normalizeLocale(player);
		List<String> direct = values.get(locale);
		if (direct != null) {
			return direct;
		}

		for (Map.Entry<String, List<String>> entry : values.entrySet()) {
			String key = entry.getKey();
			if (key != null && locale.startsWith(key.toLowerCase(Locale.ROOT))) {
				return entry.getValue();
			}
		}

		List<String> defaultValue = values.get("default");
		if (defaultValue != null) {
			return defaultValue;
		}
		List<String> enUs = values.get("en_us");
		if (enUs != null) {
			return enUs;
		}
		return values.values().iterator().next();
	}

	private static String applyPlaceholders(String value, Map<String, String> placeholders) {
		if (value == null || value.isEmpty() || placeholders.isEmpty()) {
			return safeString(value);
		}
		String result = value;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			result = result.replace(entry.getKey(), safeString(entry.getValue()));
		}
		return result;
	}

	private static String normalizeLocale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "en_us";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	private static String getLocalizedStatus(ServerPlayer player, ButtonState state, int cost, int bitcoins) {
		String locale = normalizeLocale(player);
		if (locale.startsWith("rpr")) {
			return switch (state) {
				case LOCKED -> "Подъ замкомъ";
				case MAXED -> "До предѣла";
				case ACTIVE -> cost > bitcoins ? "Монѣтъ недостаётъ" : "Можно взять";
			};
		}
		if (locale.startsWith("uk")) {
			return switch (state) {
				case LOCKED -> "Заблоковано";
				case MAXED -> "Максимум";
				case ACTIVE -> cost > bitcoins ? "Недостатньо біткоїнів" : "Можна купити";
			};
		}
		if (locale.startsWith("ja")) {
			return switch (state) {
				case LOCKED -> "ロック中";
				case MAXED -> "最大";
				case ACTIVE -> cost > bitcoins ? "ビットコイン不足" : "購入可能";
			};
		}
		return switch (state) {
			case LOCKED -> localizeSystem(player, "Locked", "Заблокировано");
			case MAXED -> localizeSystem(player, "Maxed", "Максимум");
			case ACTIVE -> cost > bitcoins
					? localizeSystem(player, "Not enough bitcoins", "Недостаточно биткоинов")
					: localizeSystem(player, "Ready to purchase", "Можно купить");
		};
	}

	private static String localizeTooltip(
			ServerPlayer player,
			String enUs,
			String ruRu,
			String ukUa,
			String jaJp,
			String rpr
	) {
		String locale = normalizeLocale(player);
		if (locale.startsWith("rpr")) {
			return rpr;
		}
		if (locale.startsWith("uk")) {
			return ukUa;
		}
		if (locale.startsWith("ja")) {
			return jaJp;
		}
		if (locale.startsWith("ru")) {
			return ruRu;
		}
		return enUs;
	}

	private static String localizeSystem(ServerPlayer player, String enUs, String ruRu) {
		String locale = normalizeLocale(player);
		if (locale.startsWith("ru") || locale.startsWith("rpr")) {
			return ruRu;
		}
		return enUs;
	}

	private static void sendPlayerMessage(ServerPlayer player, String message) {
		player.displayClientMessage(Component.literal(message), true);
	}

	private static void playUiClick(ServerPlayer player, boolean success) {
		playUiClick(player, success, false);
	}

	private static void playUiClick(ServerPlayer player, boolean success, boolean suppress) {
		if (suppress) {
			return;
		}
		player.level().playSound(
				null,
				player.blockPosition(),
				success ? SoundEvents.UI_BUTTON_CLICK.value() : SoundEvents.VILLAGER_NO,
				SoundSource.PLAYERS,
				0.8F,
				success ? 1.0F : 0.8F
		);
	}

	private static void playPurchaseSound(ServerPlayer player) {
		player.level().playSound(
				null,
				player.blockPosition(),
				SoundEvents.PLAYER_LEVELUP,
				SoundSource.PLAYERS,
				0.7F,
				1.2F
		);
	}

	private static int getUpgradeLevel(ServerPlayer player, String upgradeId) {
		if (player == null || upgradeId == null || upgradeId.isBlank()) {
			return 0;
		}
		Map<String, Integer> levels = PLAYER_UPGRADE_LEVELS.get(player.getUUID().toString());
		return levels == null ? 0 : Math.max(0, levels.getOrDefault(upgradeId, 0));
	}

	private static void setUpgradeLevel(MinecraftServer server, ServerPlayer player, String upgradeId, int level) {
		if (player == null || upgradeId == null || upgradeId.isBlank()) {
			return;
		}
		String playerKey = player.getUUID().toString();
		int normalizedLevel = Math.max(0, level);
		if (normalizedLevel <= 0) {
			Map<String, Integer> levels = PLAYER_UPGRADE_LEVELS.get(playerKey);
			if (levels != null && levels.remove(upgradeId) != null) {
				if (levels.isEmpty()) {
					PLAYER_UPGRADE_LEVELS.remove(playerKey);
				}
				stateDirty = true;
			}
		} else if (!Objects.equals(
				PLAYER_UPGRADE_LEVELS.computeIfAbsent(playerKey, ignored -> new LinkedHashMap<>()).put(upgradeId, normalizedLevel),
				normalizedLevel
		)) {
			stateDirty = true;
		}
		saveState(server);
	}

	private static Path getStatePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(STATE_FILE_NAME);
	}

	private static void loadState(MinecraftServer server) {
		PLAYER_UPGRADE_LEVELS.clear();
		LEGACY_GLOBAL_UPGRADE_LEVELS.clear();
		Path path = getStatePath(server);
		if (!Files.exists(path)) {
			stateLoaded = true;
			stateDirty = false;
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			StateData state = STATE_GSON.fromJson(reader, StateData.class);
			if (state != null && state.playerLevels != null) {
				for (Map.Entry<String, Map<String, Integer>> playerEntry : state.playerLevels.entrySet()) {
					String playerKey = playerEntry.getKey();
					Map<String, Integer> loadedLevels = playerEntry.getValue();
					if (playerKey == null || playerKey.isBlank() || loadedLevels == null || loadedLevels.isEmpty()) {
						continue;
					}

					Map<String, Integer> sanitizedLevels = new LinkedHashMap<>();
					for (Map.Entry<String, Integer> levelEntry : loadedLevels.entrySet()) {
						String id = levelEntry.getKey();
						Integer level = levelEntry.getValue();
						if (id == null || id.isBlank() || level == null || level <= 0) {
							continue;
						}
						sanitizedLevels.put(id, level);
					}
					if (!sanitizedLevels.isEmpty()) {
						PLAYER_UPGRADE_LEVELS.put(playerKey, sanitizedLevels);
					}
				}
			}
			if (state != null && state.levels != null) {
				for (Map.Entry<String, Integer> entry : state.levels.entrySet()) {
					String id = entry.getKey();
					Integer level = entry.getValue();
					if (id == null || id.isBlank() || level == null || level <= 0) {
						continue;
					}
					LEGACY_GLOBAL_UPGRADE_LEVELS.put(id, level);
				}
			}
			stateLoaded = true;
			stateDirty = false;
		} catch (Exception e) {
			Lg2.LOGGER.warn("Failed to load upgrade UI state from {}", path, e);
			stateLoaded = true;
			stateDirty = false;
		}
	}

	private static void saveState(MinecraftServer server) {
		if (server == null || !stateLoaded || !stateDirty) {
			return;
		}

		Path path = getStatePath(server);
		StateData state = new StateData();
		for (Map.Entry<String, Map<String, Integer>> entry : PLAYER_UPGRADE_LEVELS.entrySet()) {
			state.playerLevels.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
		}
		state.levels.putAll(LEGACY_GLOBAL_UPGRADE_LEVELS);

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				STATE_GSON.toJson(state, writer);
			}
			stateDirty = false;
		} catch (IOException e) {
			Lg2.LOGGER.warn("Failed to save upgrade UI state to {}", path, e);
		}
	}

	private static String safeString(String value) {
		return value == null ? "" : value;
	}

	private enum ButtonState {
		ACTIVE,
		LOCKED,
		MAXED
	}

	private static final class StateData {
		private final Map<String, Map<String, Integer>> playerLevels = new LinkedHashMap<>();
		private final Map<String, Integer> levels = new LinkedHashMap<>();
	}

	private static final class UpgradeMenu extends ChestMenu {
		private final int topSlotCount;
		private final String screenId;
		private final ServerPlayer viewer;
		private final boolean hasPack;

		private UpgradeMenu(int syncId, Inventory inventory, Container container, String screenId, int rows, ServerPlayer viewer, boolean hasPack) {
			super(resolveMenuType(rows), syncId, inventory, container, rows);
			this.topSlotCount = rows * 9;
			this.screenId = screenId;
			this.viewer = viewer;
			this.hasPack = hasPack;
		}

		@Override
		public void clicked(int slotId, int button, ClickType clickType, Player player) {
			if (slotId >= 0 && slotId < this.topSlotCount) {
				if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP) {
					handleTopSlotClick(this.viewer, this.screenId, slotId, this.hasPack);
				}
				return;
			}
			// Hidden lower inventory stays non-interactive while this UI is open.
			return;
		}

		@Override
		public ItemStack quickMoveStack(Player player, int index) {
			return ItemStack.EMPTY;
		}

		@Override
		public void broadcastChanges() {
			super.broadcastChanges();
			hideLowerInventoryVisuals(this.viewer, this);
		}

		@Override
		public void broadcastFullState() {
			super.broadcastFullState();
			hideLowerInventoryVisuals(this.viewer, this);
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			restoreLowerInventoryVisuals(this.viewer, this);
		}

		@Override
		public boolean stillValid(Player player) {
			return player.isAlive();
		}

		private static MenuType<?> resolveMenuType(int rows) {
			return switch (rows) {
				case 3 -> MenuType.GENERIC_9x3;
				case 4 -> MenuType.GENERIC_9x4;
				case 5 -> MenuType.GENERIC_9x5;
				default -> throw new IllegalArgumentException("Unsupported upgrade menu rows: " + rows);
			};
		}
	}
}
