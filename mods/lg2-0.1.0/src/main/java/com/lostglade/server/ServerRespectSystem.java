package com.lostglade.server;

import com.lostglade.config.Lg2Config;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerRespectSystem {
	private static final String OBJECTIVE_NAME = "lg2_respects";
	private static final net.minecraft.network.chat.Component OBJECTIVE_TITLE = net.minecraft.network.chat.Component.literal("Респекты");
	private static final Map<UUID, Long> NEXT_RESPECT_AT_MILLIS = new HashMap<>();

	private ServerRespectSystem() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> refreshAllTabSuffixes(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> NEXT_RESPECT_AT_MILLIS.clear());
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshTabSuffix((ServerPlayer) handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refreshTabSuffix(newPlayer));
		ServerTabIntegration.registerPlayerLoadHandler(ServerRespectSystem::refreshTabSuffix);

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide()
					|| hand != InteractionHand.MAIN_HAND
					|| !(player instanceof ServerPlayer actor)
					|| !(entity instanceof ServerPlayer target)) {
				return InteractionResult.PASS;
			}
			if (!actor.isShiftKeyDown() || actor == target || !actor.isAlive() || !target.isAlive()) {
				return InteractionResult.PASS;
			}

			long remainingCooldownMillis = getRemainingCooldownMillis(actor);
			if (remainingCooldownMillis > 0L) {
				actor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"Следующий респект можно отправить через " + formatRemainingTime(remainingCooldownMillis) + "."
				));
				return InteractionResult.SUCCESS;
			}

			int totalRespects = addRespect(target);
			applyCooldown(actor);
			refreshTabSuffix(target);
			actor.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"Вы добавили респект игроку " + target.getScoreboardName() + ". Всего респектов: " + totalRespects
			));
			target.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					actor.getScoreboardName() + " добавил вам респект. Всего респектов: " + totalRespects
			));
			return InteractionResult.SUCCESS;
		});
	}

	private static void refreshAllTabSuffixes(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshTabSuffix(player);
		}
	}

	private static void refreshTabSuffix(ServerPlayer player) {
		ServerTabIntegration.setTabSuffix(player, " &b[" + getRespectCount(player) + "]");
	}

	private static int addRespect(ServerPlayer target) {
		MinecraftServer server = target.level().getServer();
		if (server == null) {
			return 0;
		}

		Objective objective = getOrCreateObjective(server);
		ScoreAccess score = server.getScoreboard().getOrCreatePlayerScore(target, objective);
		return score.add(1);
	}

	private static int getRespectCount(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return 0;
		}

		Objective objective = getOrCreateObjective(server);
		return server.getScoreboard().getOrCreatePlayerScore(player, objective).get();
	}

	private static void applyCooldown(ServerPlayer actor) {
		int cooldownSeconds = Lg2Config.get().respectCooldownSeconds;
		if (cooldownSeconds <= 0) {
			NEXT_RESPECT_AT_MILLIS.remove(actor.getUUID());
			return;
		}

		long nextUseMillis = System.currentTimeMillis() + cooldownSeconds * 1000L;
		NEXT_RESPECT_AT_MILLIS.put(actor.getUUID(), nextUseMillis);
	}

	private static long getRemainingCooldownMillis(ServerPlayer actor) {
		Long nextUseMillis = NEXT_RESPECT_AT_MILLIS.get(actor.getUUID());
		if (nextUseMillis == null) {
			return 0L;
		}

		long remaining = nextUseMillis - System.currentTimeMillis();
		if (remaining <= 0L) {
			NEXT_RESPECT_AT_MILLIS.remove(actor.getUUID());
			return 0L;
		}

		return remaining;
	}

	private static String formatRemainingTime(long millis) {
		long totalSeconds = Math.max(1L, (millis + 999L) / 1000L);
		long minutes = totalSeconds / 60L;
		long seconds = totalSeconds % 60L;
		if (minutes <= 0L) {
			return seconds + " сек.";
		}
		if (seconds <= 0L) {
			return minutes + " мин.";
		}
		return minutes + " мин. " + seconds + " сек.";
	}

	private static Objective getOrCreateObjective(MinecraftServer server) {
		Scoreboard scoreboard = server.getScoreboard();
		Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
		if (objective != null) {
			return objective;
		}

		return scoreboard.addObjective(
				OBJECTIVE_NAME,
				ObjectiveCriteria.DUMMY,
				OBJECTIVE_TITLE,
				ObjectiveCriteria.RenderType.INTEGER,
				false,
				null
		);
	}
}
