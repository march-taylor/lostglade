package com.lostglade.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ServerBossBarVisibilitySystem {
	private static final ThreadLocal<Boolean> BYPASS_PACKET_FILTER = ThreadLocal.withInitial(() -> false);
	private static final Map<UUID, PlayerBossBarState> PLAYER_STATES = new HashMap<>();

	private ServerBossBarVisibilitySystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ServerBossBarVisibilitySystem::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PLAYER_STATES.clear());
	}

	public static boolean shouldBypassPacketFilter() {
		return BYPASS_PACKET_FILTER.get();
	}

	public static void setServerHudFocus(ServerPlayer player, boolean focused) {
		PlayerBossBarState state = PLAYER_STATES.computeIfAbsent(player.getUUID(), id -> new PlayerBossBarState());
		if (state.serverHudFocused == focused) {
			return;
		}

		state.serverHudFocused = focused;
		if (focused) {
			hideTrackedBossBars(player, state);
			return;
		}

		restoreTrackedBossBars(player, state);
		if (state.activeBossBars.isEmpty()) {
			PLAYER_STATES.remove(player.getUUID());
		}
	}

	public static boolean handleOutgoingBossEventPacket(ServerPlayer receiver, ClientboundBossEventPacket packet) {
		BossBarPacketUpdate update = BossBarPacketUpdate.from(packet);
		if (update == null) {
			return false;
		}

		boolean reservedHud = ServerStabilitySystem.isHudBossBar(receiver, update.id);
		PlayerBossBarState state = PLAYER_STATES.computeIfAbsent(receiver.getUUID(), id -> new PlayerBossBarState());

		if (!reservedHud) {
			applyPacketUpdate(state, update);
		}

		if (!state.serverHudFocused) {
			if (state.activeBossBars.isEmpty()) {
				PLAYER_STATES.remove(receiver.getUUID());
			}
			return false;
		}

		return !reservedHud;
	}

	private static void applyPacketUpdate(PlayerBossBarState state, BossBarPacketUpdate update) {
		switch (update.type) {
			case ADD -> state.activeBossBars.put(
					update.id,
					new TrackedBossBar(
							update.id,
							copyComponent(update.name),
							update.progress == null ? 0.0F : update.progress,
							update.color == null ? BossEvent.BossBarColor.WHITE : update.color,
							update.overlay == null ? BossEvent.BossBarOverlay.PROGRESS : update.overlay,
							Boolean.TRUE.equals(update.darkenScreen),
							Boolean.TRUE.equals(update.playBossMusic),
							Boolean.TRUE.equals(update.createWorldFog)
					)
			);
			case REMOVE -> state.activeBossBars.remove(update.id);
			case UPDATE_PROGRESS -> {
				TrackedBossBar bossBar = state.activeBossBars.get(update.id);
				if (bossBar != null && update.progress != null) {
					bossBar.progress = update.progress;
				}
			}
			case UPDATE_NAME -> {
				TrackedBossBar bossBar = state.activeBossBars.get(update.id);
				if (bossBar != null && update.name != null) {
					bossBar.name = copyComponent(update.name);
				}
			}
			case UPDATE_STYLE -> {
				TrackedBossBar bossBar = state.activeBossBars.get(update.id);
				if (bossBar != null) {
					if (update.color != null) {
						bossBar.color = update.color;
					}
					if (update.overlay != null) {
						bossBar.overlay = update.overlay;
					}
				}
			}
			case UPDATE_PROPERTIES -> {
				TrackedBossBar bossBar = state.activeBossBars.get(update.id);
				if (bossBar != null) {
					if (update.darkenScreen != null) {
						bossBar.darkenScreen = update.darkenScreen;
					}
					if (update.playBossMusic != null) {
						bossBar.playBossMusic = update.playBossMusic;
					}
					if (update.createWorldFog != null) {
						bossBar.createWorldFog = update.createWorldFog;
					}
				}
			}
		}
	}

	private static void hideTrackedBossBars(ServerPlayer player, PlayerBossBarState state) {
		for (UUID bossBarId : state.activeBossBars.keySet()) {
			sendSyntheticPacket(player, ClientboundBossEventPacket.createRemovePacket(bossBarId));
		}
	}

	private static void restoreTrackedBossBars(ServerPlayer player, PlayerBossBarState state) {
		for (TrackedBossBar bossBar : state.activeBossBars.values()) {
			sendSyntheticPacket(player, ClientboundBossEventPacket.createAddPacket(bossBar.toBossEvent()));
		}
	}

	private static void sendSyntheticPacket(ServerPlayer player, ClientboundBossEventPacket packet) {
		// Synthetic packets must bypass the filter so hidden bars stay tracked server-side.
		BYPASS_PACKET_FILTER.set(true);
		try {
			player.connection.send(packet);
		} finally {
			BYPASS_PACKET_FILTER.remove();
		}
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, PlayerBossBarState>> iterator = PLAYER_STATES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, PlayerBossBarState> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				iterator.remove();
				continue;
			}

			PlayerBossBarState state = entry.getValue();
			if (!state.serverHudFocused && state.activeBossBars.isEmpty()) {
				iterator.remove();
			}
		}
	}

	private static Component copyComponent(Component component) {
		return component == null ? Component.empty() : component.copy();
	}

	private enum BossBarPacketType {
		ADD,
		REMOVE,
		UPDATE_PROGRESS,
		UPDATE_NAME,
		UPDATE_STYLE,
		UPDATE_PROPERTIES
	}

	private static final class PlayerBossBarState {
		private boolean serverHudFocused;
		private final Map<UUID, TrackedBossBar> activeBossBars = new LinkedHashMap<>();
	}

	private static final class TrackedBossBar {
		private final UUID id;
		private Component name;
		private float progress;
		private BossEvent.BossBarColor color;
		private BossEvent.BossBarOverlay overlay;
		private boolean darkenScreen;
		private boolean playBossMusic;
		private boolean createWorldFog;

		private TrackedBossBar(
				UUID id,
				Component name,
				float progress,
				BossEvent.BossBarColor color,
				BossEvent.BossBarOverlay overlay,
				boolean darkenScreen,
				boolean playBossMusic,
				boolean createWorldFog
		) {
			this.id = id;
			this.name = name;
			this.progress = progress;
			this.color = color;
			this.overlay = overlay;
			this.darkenScreen = darkenScreen;
			this.playBossMusic = playBossMusic;
			this.createWorldFog = createWorldFog;
		}

		private BossEvent toBossEvent() {
			BossEvent bossEvent = new BossEvent(this.id, copyComponent(this.name), this.color, this.overlay) {
			};
			bossEvent.setProgress(this.progress);
			bossEvent.setDarkenScreen(this.darkenScreen);
			bossEvent.setPlayBossMusic(this.playBossMusic);
			bossEvent.setCreateWorldFog(this.createWorldFog);
			return bossEvent;
		}
	}

	private static final class BossBarPacketUpdate {
		private final UUID id;
		private final BossBarPacketType type;
		private final Component name;
		private final Float progress;
		private final BossEvent.BossBarColor color;
		private final BossEvent.BossBarOverlay overlay;
		private final Boolean darkenScreen;
		private final Boolean playBossMusic;
		private final Boolean createWorldFog;

		private BossBarPacketUpdate(
				UUID id,
				BossBarPacketType type,
				Component name,
				Float progress,
				BossEvent.BossBarColor color,
				BossEvent.BossBarOverlay overlay,
				Boolean darkenScreen,
				Boolean playBossMusic,
				Boolean createWorldFog
		) {
			this.id = id;
			this.type = type;
			this.name = name;
			this.progress = progress;
			this.color = color;
			this.overlay = overlay;
			this.darkenScreen = darkenScreen;
			this.playBossMusic = playBossMusic;
			this.createWorldFog = createWorldFog;
		}

		private static BossBarPacketUpdate from(ClientboundBossEventPacket packet) {
			BossBarPacketUpdate[] holder = new BossBarPacketUpdate[1];
			packet.dispatch(new ClientboundBossEventPacket.Handler() {
				@Override
				public void add(
						UUID id,
						Component name,
						float progress,
						BossEvent.BossBarColor color,
						BossEvent.BossBarOverlay overlay,
						boolean darkenScreen,
						boolean playMusic,
						boolean createWorldFog
				) {
					holder[0] = new BossBarPacketUpdate(
							id,
							BossBarPacketType.ADD,
							name,
							progress,
							color,
							overlay,
							darkenScreen,
							playMusic,
							createWorldFog
					);
				}

				@Override
				public void remove(UUID id) {
					holder[0] = new BossBarPacketUpdate(
							id,
							BossBarPacketType.REMOVE,
							null,
							null,
							null,
							null,
							null,
							null,
							null
					);
				}

				@Override
				public void updateProgress(UUID id, float progress) {
					holder[0] = new BossBarPacketUpdate(
							id,
							BossBarPacketType.UPDATE_PROGRESS,
							null,
							progress,
							null,
							null,
							null,
							null,
							null
					);
				}

				@Override
				public void updateName(UUID id, Component name) {
					holder[0] = new BossBarPacketUpdate(
							id,
							BossBarPacketType.UPDATE_NAME,
							name,
							null,
							null,
							null,
							null,
							null,
							null
					);
				}

				@Override
				public void updateStyle(UUID id, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay) {
					holder[0] = new BossBarPacketUpdate(
							id,
							BossBarPacketType.UPDATE_STYLE,
							null,
							null,
							color,
							overlay,
							null,
							null,
							null
					);
				}

				@Override
				public void updateProperties(UUID id, boolean darkenScreen, boolean playMusic, boolean createWorldFog) {
					holder[0] = new BossBarPacketUpdate(
							id,
							BossBarPacketType.UPDATE_PROPERTIES,
							null,
							null,
							null,
							null,
							darkenScreen,
							playMusic,
							createWorldFog
					);
				}
			});
			return holder[0];
		}
	}
}
