package com.lostglade.server.glitch;

import com.lostglade.config.GlitchConfig;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

public interface ChatMessageGlitchHandler extends ServerGlitchHandler {
	boolean triggerChat(
			MinecraftServer server,
			RandomSource random,
			GlitchConfig.GlitchEntry entry,
			double stabilityPercent,
			ServerPlayer sender,
			PlayerChatMessage message,
			ChatType.Bound params
	);
}
