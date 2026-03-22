package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilityConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ServerRaceSystem {
	private static final Map<String, PlayerRaceConfig> RACES_BY_NICKNAME = new LinkedHashMap<>();

	private ServerRaceSystem() {
	}

	public static void register() {
		rebuildCache();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			RaceConfig.load();
			rebuildCache();
			Lg2.LOGGER.info("Loaded {} configured personal races", RACES_BY_NICKNAME.size());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RACES_BY_NICKNAME.clear());
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
		for (PlayerRaceConfig race : RaceConfig.get().races) {
			if (race == null || !race.enabled || race.ownerNickname == null || race.ownerNickname.isBlank()) {
				continue;
			}

			String normalizedNickname = normalizeNickname(race.ownerNickname);
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
}
