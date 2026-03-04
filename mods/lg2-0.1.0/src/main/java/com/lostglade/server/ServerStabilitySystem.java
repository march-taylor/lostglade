package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerStabilitySystem {
	private static final String STABILITY_SYMBOL = "\uE903";
	private static final int TITLE_COLOR = 0xF2CD26;

	private static final Map<UUID, ServerBossEvent> PLAYER_HUDS = new HashMap<>();

	private static int stability = 100;

	private ServerStabilitySystem() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						Commands.literal("serverstability")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(context -> {
									int value = getStability();
									context.getSource().sendSuccess(
											() -> Component.literal("Server stability: " + value),
											false
									);
									return value;
								})
								.then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
										.executes(context -> {
											int value = IntegerArgumentType.getInteger(context, "value");
											setStability(value);
											context.getSource().sendSuccess(
													() -> Component.literal("Set server stability to " + value),
													true
											);
											return 1;
										}))
				)
		);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Set<UUID> online = new HashSet<>();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				online.add(player.getUUID());

				if (!isLookingAtServerBlock(player)) {
					hideHud(player);
					continue;
				}

				showHud(player, PolymerResourcePackUtils.hasMainPack(player));
			}

			PLAYER_HUDS.entrySet().removeIf(entry -> {
				if (online.contains(entry.getKey())) {
					return false;
				}
				entry.getValue().removeAllPlayers();
				return true;
			});
		});
	}

	private static void showHud(ServerPlayer player, boolean hasPack) {
		ServerBossEvent hud = PLAYER_HUDS.computeIfAbsent(player.getUUID(), id -> createHudEvent());
		float progress = getStability() / 100.0f;

		hud.setName(getHudTitle(player, hasPack));
		hud.setColor(BossEvent.BossBarColor.YELLOW);
		hud.setProgress(progress);

		if (!hud.getPlayers().contains(player)) {
			hud.addPlayer(player);
		}

		hud.setVisible(true);
	}

	private static void hideHud(ServerPlayer player) {
		ServerBossEvent hud = PLAYER_HUDS.get(player.getUUID());
		if (hud == null) {
			return;
		}

		hud.removePlayer(player);
		if (hud.getPlayers().isEmpty()) {
			PLAYER_HUDS.remove(player.getUUID());
		}
	}

	private static ServerBossEvent createHudEvent() {
		ServerBossEvent event = new ServerBossEvent(
				Component.empty(),
				BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.PROGRESS
		);
		event.setDarkenScreen(false);
		event.setPlayBossMusic(false);
		event.setCreateWorldFog(false);
		return event;
	}

	private static Component getHudTitle(ServerPlayer player, boolean hasPack) {
		if (hasPack) {
			return Component.literal(STABILITY_SYMBOL).withStyle(ServerStabilitySystem::applyPackStyle);
		}

		String language = player.clientInformation().language();
		if (language == null) {
			return textTitle("Stability");
		}

		String normalized = language.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("ru")) {
			return textTitle("Стабильность");
		}
		if (normalized.startsWith("uk")) {
			return textTitle("Стабільність");
		}
		if (normalized.startsWith("ja")) {
			return textTitle("サーバー安定性");
		}
		if (normalized.startsWith("rdr") || normalized.startsWith("rpr")) {
			return textTitle("Послушанiя жѣлѣзнаго разума");
		}
		return textTitle("Stability");
	}

	private static Component textTitle(String title) {
		return Component.literal(title).withStyle(ServerStabilitySystem::applyTextStyle);
	}

	private static Style applyTextStyle(Style style) {
		return style.withColor(TITLE_COLOR).withBold(true).withItalic(false);
	}

	private static Style applyPackStyle(Style style) {
		return style.withColor(TITLE_COLOR).withBold(false).withItalic(false);
	}

	public static int getStability() {
		return stability;
	}

	public static void setStability(int value) {
		stability = clamp(value);
	}

	private static boolean isLookingAtServerBlock(ServerPlayer player) {
		HitResult hit = player.pick(6.0D, 0.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}

		BlockHitResult blockHit = (BlockHitResult) hit;
		return player.level().getBlockState(blockHit.getBlockPos()).is(ModBlocks.SERVER);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(100, value));
	}
}
