package com.lostglade.server;

import com.lostglade.config.Lg2Config;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
	private static final String ACTIONBAR_TRANSLATION_KEY = "lg2.respect.actionbar";
	private static final Component OBJECTIVE_TITLE = Component.literal("Р РµСЃРїРµРєС‚С‹");
	private static final double RESPECT_PARTICLE_VIEW_DISTANCE_SQR = 32.0D * 32.0D;
	private static final Map<UUID, Long> NEXT_RESPECT_AT_MILLIS = new HashMap<>();

	private ServerRespectSystem() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(ServerRespectSystem::refreshAllTabSuffixes);
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
			if (getRemainingCooldownMillis(actor) > 0L) {
				return InteractionResult.SUCCESS;
			}

			addRespect(target);
			applyCooldown(actor);
			refreshTabSuffix(target);
			showRespectActionbar(actor);
			showRespectActionbar(target);
			spawnRespectParticles(target);
			playRespectSound(target);
			return InteractionResult.SUCCESS;
		});
	}

	private static void refreshAllTabSuffixes(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			refreshTabSuffix(player);
		}
	}

	private static void refreshTabSuffix(ServerPlayer player) {
		ServerTabIntegration.setTabSuffix(player, " &3[" + getRespectCount(player) + "]");
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

	private static void showRespectActionbar(ServerPlayer player) {
		player.displayClientMessage(
				Component.translatable(ACTIONBAR_TRANSLATION_KEY).withStyle(ChatFormatting.DARK_AQUA),
				true
		);
	}

	private static void spawnRespectParticles(ServerPlayer target) {
		ServerLevel level = target.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		if (level == null) {
			return;
		}

		double x = target.getX();
		double y = target.getY() + target.getBbHeight() * 0.6D;
		double z = target.getZ();
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(x, y, z) > RESPECT_PARTICLE_VIEW_DISTANCE_SQR) {
				continue;
			}
			level.sendParticles(
					viewer,
					ParticleTypes.TOTEM_OF_UNDYING,
					false,
					true,
					x,
					y,
					z,
					32,
					0.55D,
					0.85D,
					0.55D,
					0.08D
			);
		}
	}

	private static void playRespectSound(ServerPlayer target) {
		ServerLevel level = target.level() instanceof ServerLevel serverLevel ? serverLevel : null;
		if (level == null) {
			return;
		}

		target.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP),
				SoundSource.PLAYERS,
				target.getX(),
				target.getY(),
				target.getZ(),
				0.65F,
				1.15F,
				level.getRandom().nextLong()
		));
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

