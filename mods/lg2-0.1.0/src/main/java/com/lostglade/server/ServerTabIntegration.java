package com.lostglade.server;

import me.neznamy.tab.shared.chat.component.TabComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ServerTabIntegration {
	private ServerTabIntegration() {
	}

	private static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded("tab");
	}

	public static void register() {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.register();
		ServerTabPlaceholders.register();
	}

	public static List<ServerPlayer> getOnlinePlayers() {
		if (!isAvailable()) {
			return List.of();
		}
		return ServerTabIntegrationTabImpl.getOnlinePlayers();
	}

	public static void registerPlayerPlaceholder(String identifier, int refreshMillis, Function<ServerPlayer, String> resolver) {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.registerPlayerPlaceholder(identifier, refreshMillis, resolver);
	}

	public static void registerServerPlaceholder(String identifier, int refreshMillis, Supplier<String> resolver) {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.registerServerPlaceholder(identifier, refreshMillis, resolver);
	}

	public static void registerRelationalPlaceholder(String identifier, int refreshMillis,
			BiFunction<ServerPlayer, ServerPlayer, String> resolver) {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.registerRelationalPlaceholder(identifier, refreshMillis, resolver);
	}

	public static void setTabSuffix(ServerPlayer player, String suffix) {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.setTabSuffix(player, suffix);
	}

	public static void registerPlayerLoadHandler(Consumer<ServerPlayer> handler) {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.registerPlayerLoadHandler(handler);
	}

	public static void registerRendererBotVanishIntegration(Predicate<ServerPlayer> hiddenPredicate) {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.registerRendererBotVanishIntegration(hiddenPredicate);
	}

	public static void setHeaderFooter(ServerPlayer player, TabComponent header, TabComponent footer) {
		if (!isAvailable()) {
			return;
		}
		ServerTabIntegrationTabImpl.setHeaderFooter(player, header, footer);
	}
}
