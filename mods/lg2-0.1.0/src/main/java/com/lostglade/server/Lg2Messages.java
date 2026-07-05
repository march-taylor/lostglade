package com.lostglade.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public final class Lg2Messages {
	private Lg2Messages() {
	}

	public static MutableComponent tr(String key, Object... args) {
		return Component.translatable(key, args).withStyle(style -> style.withItalic(false));
	}

	public static MutableComponent tr(ChatFormatting color, String key, Object... args) {
		return Component.translatable(key, args).withStyle(style -> style.withColor(color).withItalic(false));
	}

	public static MutableComponent tr(int color, String key, Object... args) {
		return Component.translatable(key, args).withStyle(style -> style.withColor(color).withItalic(false));
	}

	public static void actionBar(ServerPlayer player, Component message) {
		if (player == null || message == null) {
			return;
		}
		player.displayClientMessage(message, true);
	}

	public static void actionBar(ServerPlayer player, String key, Object... args) {
		actionBar(player, tr(key, args));
	}

	public static void actionBar(ServerPlayer player, ChatFormatting color, String key, Object... args) {
		actionBar(player, tr(color, key, args));
	}

	public static void actionBar(ServerPlayer player, int color, String key, Object... args) {
		actionBar(player, tr(color, key, args));
	}
}
