package com.lostglade.server;

import com.lostglade.Lg2;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
import me.neznamy.tab.api.placeholder.PlaceholderManager;
import me.neznamy.tab.api.placeholder.RelationalPlaceholder;
import me.neznamy.tab.api.tablist.TabListFormatManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ServerTabIntegration {

	private ServerTabIntegration() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TabAPI api = getApi();
			if (api == null) {
				Lg2.LOGGER.warn("TAB API is not available even though TAB is listed as a dependency");
				return;
			}
			Lg2.LOGGER.info("Connected TAB API");
		});
	}

	public static TabAPI getApi() {
		try {
			return TabAPI.getInstance();
		} catch (Throwable throwable) {
			Lg2.LOGGER.warn("Failed to access TAB API", throwable);
			return null;
		}
	}

	public static TabPlayer getPlayer(ServerPlayer player) {
		return getPlayer(player.getUUID());
	}

	public static TabPlayer getPlayer(UUID playerId) {
		TabAPI api = getApi();
		return api == null ? null : api.getPlayer(playerId);
	}

	public static void registerPlayerPlaceholder(String identifier, int refreshMillis, Function<ServerPlayer, String> resolver) {
		TabAPI api = getApi();
		if (api == null) {
			return;
		}

		PlaceholderManager manager = api.getPlaceholderManager();
		manager.unregisterPlaceholder(identifier);
		manager.registerPlayerPlaceholder(identifier, refreshMillis, tabPlayer -> {
			Object rawPlayer = tabPlayer.getPlayer();
			if (rawPlayer instanceof ServerPlayer serverPlayer) {
				return resolver.apply(serverPlayer);
			}
			return "";
		});
	}

	public static void registerServerPlaceholder(String identifier, int refreshMillis, Supplier<String> resolver) {
		TabAPI api = getApi();
		if (api == null) {
			return;
		}

		PlaceholderManager manager = api.getPlaceholderManager();
		manager.unregisterPlaceholder(identifier);
		manager.registerServerPlaceholder(identifier, refreshMillis, resolver);
	}

	public static RelationalPlaceholder registerRelationalPlaceholder(String identifier, int refreshMillis,
			BiFunction<ServerPlayer, ServerPlayer, String> resolver) {
		TabAPI api = getApi();
		if (api == null) {
			return null;
		}

		PlaceholderManager manager = api.getPlaceholderManager();
		manager.unregisterPlaceholder(identifier);
		return manager.registerRelationalPlaceholder(identifier, refreshMillis, (viewer, target) -> {
			Object rawViewer = viewer.getPlayer();
			Object rawTarget = target.getPlayer();
			if (rawViewer instanceof ServerPlayer viewerPlayer && rawTarget instanceof ServerPlayer targetPlayer) {
				return resolver.apply(viewerPlayer, targetPlayer);
			}
			return "";
		});
	}

	public static void setTabSuffix(ServerPlayer player, String suffix) {
		TabPlayer tabPlayer = getPlayer(player);
		if (tabPlayer == null) {
			return;
		}

		TabAPI api = getApi();
		if (api == null) {
			return;
		}

		TabListFormatManager manager = api.getTabListFormatManager();
		if (manager != null) {
			manager.setSuffix(tabPlayer, suffix == null ? "" : suffix);
		}
	}

	public static void registerPlayerLoadHandler(Consumer<ServerPlayer> handler) {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			TabAPI api = getApi();
			if (api == null) {
				return;
			}

			api.getEventBus().register(PlayerLoadEvent.class, event -> {
				Object rawPlayer = event.getPlayer().getPlayer();
				if (rawPlayer instanceof ServerPlayer serverPlayer) {
					handler.accept(serverPlayer);
				}
			});
		});
	}
}
