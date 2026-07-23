package com.lostglade.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
		// Bossbar ids belong to a single client connection. Keeping the previous
		// connection's tracked bars after a reconnect lets a later synthetic
		// UPDATE_NAME target an id the new client has never received an ADD for;
		// vanilla then throws an NPE while handling the packet.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PLAYER_STATES.remove(handler.player.getUUID()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				PLAYER_STATES.remove(handler.player.getUUID()));
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
			state.needsReorder = true;
			return;
		}

		state.needsReorder = false;
		if (state.activeBossBars.isEmpty()) {
			PLAYER_STATES.remove(player.getUUID());
		}
	}

	public static void ensureServerHudPriority(ServerPlayer player) {
		PlayerBossBarState state = PLAYER_STATES.get(player.getUUID());
		if (state == null || !state.serverHudFocused || !state.needsReorder) {
			return;
		}

		reorderTrackedBossBarsBelowServerHud(player, state);
		state.needsReorder = false;
	}

	public static void reorderTrackedBossBarsBelowReservedHud(ServerPlayer player) {
		if (player == null) {
			return;
		}
		PlayerBossBarState state = PLAYER_STATES.get(player.getUUID());
		if (state == null || state.activeBossBars.isEmpty()) {
			return;
		}
		reorderTrackedBossBarsBelowServerHud(player, state);
		state.needsReorder = false;
	}

	public static ClientboundBossEventPacket rewriteOutgoingBossEventPacket(ServerPlayer receiver, ClientboundBossEventPacket packet) {
		BossBarPacketUpdate update = BossBarPacketUpdate.from(packet);
		if (update == null) {
			return packet;
		}

		boolean reservedHud = ServerStabilitySystem.isHudBossBar(receiver, update.id)
				|| ServerRaceSystem.isMarkRageBossBar(receiver, update.id)
				|| DroneSystem.isHudBossBar(receiver, update.id);
		PlayerBossBarState state = PLAYER_STATES.computeIfAbsent(receiver.getUUID(), id -> new PlayerBossBarState());

		if (reservedHud) {
			return packet;
		}

		applyPacketUpdate(state, update);
		Component overlayTitle = DroneSystem.getHudOverlayTitle(receiver);
		if (overlayTitle == null) {
			return packet;
		}

		DroneSystem.suspendHudOverlayForExternalBossBar(receiver);
		if (update.type == BossBarPacketType.REMOVE && state.activeBossBars.isEmpty()) {
			// Re-add on the following server tick, after the client has processed the
			// real bar's removal. This avoids a one-line vertical jump.
			state.restoreDroneOverlayNextTick = true;
			return packet;
		}

		if (update.type == BossBarPacketType.ADD || update.type == BossBarPacketType.UPDATE_NAME) {
			TrackedBossBar bossBar = state.activeBossBars.get(update.id);
			if (bossBar != null && state.clientVisibleBossBars.contains(update.id)) {
				BossEvent rewrittenBossBar = bossBar.toBossEvent(withDroneOverlayTitle(bossBar.name, overlayTitle));
				return update.type == BossBarPacketType.ADD
						? ClientboundBossEventPacket.createAddPacket(rewrittenBossBar)
						: ClientboundBossEventPacket.createUpdateNamePacket(rewrittenBossBar);
			}
		}

		return packet;
	}

	/**
	 * Installs the current drone title into every real bossbar already visible
	 * to this player. The real bar remains on its original line; only its name
	 * packet is replaced, so no empty bossbar row or vertical spacing appears.
	 */
	public static boolean refreshDroneHudOverlay(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		PlayerBossBarState state = PLAYER_STATES.get(player.getUUID());
		Component overlayTitle = DroneSystem.getHudOverlayTitle(player);
		if (state == null || state.activeBossBars.isEmpty() || overlayTitle == null) {
			return false;
		}

		DroneSystem.suspendHudOverlayForExternalBossBar(player);
		state.restoreDroneOverlayNextTick = false;
		for (TrackedBossBar bossBar : state.activeBossBars.values()) {
			if (!state.clientVisibleBossBars.contains(bossBar.id)) {
				continue;
			}
			sendSyntheticPacket(player, ClientboundBossEventPacket.createUpdateNamePacket(
					bossBar.toBossEvent(withDroneOverlayTitle(bossBar.name, overlayTitle))
			));
		}
		return true;
	}

	/** Restores unmodified titles when the drone HUD is closed. */
	public static void clearDroneHudOverlay(ServerPlayer player) {
		if (player == null) {
			return;
		}
		PlayerBossBarState state = PLAYER_STATES.get(player.getUUID());
		if (state == null) {
			return;
		}
		state.restoreDroneOverlayNextTick = false;
		for (TrackedBossBar bossBar : state.activeBossBars.values()) {
			if (!state.clientVisibleBossBars.contains(bossBar.id)) {
				continue;
			}
			sendSyntheticPacket(player, ClientboundBossEventPacket.createUpdateNamePacket(bossBar.toBossEvent()));
		}
	}

	private static void applyPacketUpdate(PlayerBossBarState state, BossBarPacketUpdate update) {
		switch (update.type) {
			case ADD -> {
				state.activeBossBars.put(
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
				state.clientVisibleBossBars.add(update.id);
			}
			case REMOVE -> {
				state.activeBossBars.remove(update.id);
				state.clientVisibleBossBars.remove(update.id);
			}
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

	private static void reorderTrackedBossBarsBelowServerHud(ServerPlayer player, PlayerBossBarState state) {
		for (UUID bossBarId : state.activeBossBars.keySet()) {
			sendSyntheticPacket(player, ClientboundBossEventPacket.createRemovePacket(bossBarId));
		}
		for (TrackedBossBar bossBar : state.activeBossBars.values()) {
			sendSyntheticPacket(player, ClientboundBossEventPacket.createAddPacket(bossBar.toBossEvent()));
		}
	}

	private static void sendSyntheticPacket(ServerPlayer player, ClientboundBossEventPacket packet) {
		// Synthetic packets must bypass the filter so hidden bars stay tracked server-side.
		if (player == null || packet == null) {
			return;
		}
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
			if (state.restoreDroneOverlayNextTick && state.activeBossBars.isEmpty()) {
				state.restoreDroneOverlayNextTick = false;
				DroneSystem.restoreHudOverlayWithoutExternalBossBar(player);
			}
			if (!state.serverHudFocused && state.activeBossBars.isEmpty()) {
				iterator.remove();
			}
		}
	}

	private static Component copyComponent(Component component) {
		return component == null ? Component.empty() : component.copy();
	}

	private static Component withDroneOverlayTitle(Component realTitle, Component overlayTitle) {
		Component safeRealTitle = copyComponent(realTitle);
		Component safeOverlayTitle = copyComponent(overlayTitle);
		int overlayOffset = (estimatedVanillaTextWidth(safeRealTitle) + 1) / 2 + 24;
		String shiftLeft = buildSpaceAdvance(-overlayOffset);
		String shiftRight = buildSpaceAdvance(overlayOffset);
		return Component.empty()
				.append(safeRealTitle)
				.append(Component.literal(shiftLeft))
				.append(safeOverlayTitle)
				.append(Component.literal("\uE940\uE94B\uE946" + shiftRight));
	}

	private static int estimatedVanillaTextWidth(Component component) {
		if (component == null) {
			return 0;
		}
		String text = component.getString();
		int width = 0;
		for (int index = 0; index < text.length(); ) {
			int codePoint = text.codePointAt(index);
			width += estimatedVanillaGlyphWidth(codePoint);
			index += Character.charCount(codePoint);
		}
		return width;
	}

	private static int estimatedVanillaGlyphWidth(int codePoint) {
		if (codePoint == ' ') {
			return 4;
		}
		return switch (codePoint) {
			case '!', '\'', '.', ',', ':', ';', '|', 'i', 'l', 'I' -> 2;
			case '"', '(', ')', '[', ']', '{', '}', '*', 't', 'f', 'k' -> 4;
			case 'M', 'W', 'm', 'w', '@', '%', '&' -> 7;
			default -> codePoint > 0x7F ? 6 : 6;
		};
	}

	private static String buildSpaceAdvance(int amount) {
		if (amount == 0) {
			return "";
		}
		int[] values = {64, 32, 16, 8, 4, 2, 1};
		int codePointBase = amount < 0 ? 0xE940 : 0xE947;
		int remaining = Math.abs(amount);
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < values.length; index++) {
			while (remaining >= values[index]) {
				builder.appendCodePoint(codePointBase + index);
				remaining -= values[index];
			}
		}
		return builder.toString();
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
		private boolean needsReorder;
		private boolean restoreDroneOverlayNextTick;
		private final Map<UUID, TrackedBossBar> activeBossBars = new LinkedHashMap<>();
		private final java.util.Set<UUID> clientVisibleBossBars = new java.util.HashSet<>();
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
			return toBossEvent(this.name);
		}

		private BossEvent toBossEvent(Component name) {
			BossEvent bossEvent = new BossEvent(this.id, copyComponent(name), this.color, this.overlay) {
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
