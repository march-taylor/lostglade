package com.lostglade.server.glitch;

import com.lostglade.config.GlitchConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

public interface RespawnGlitchHandler extends ServerGlitchHandler {
	default boolean triggerOnCopyFrom(
			MinecraftServer server,
			RandomSource random,
			GlitchConfig.GlitchEntry entry,
			double stabilityPercent,
			ServerPlayer oldPlayer,
			ServerPlayer newPlayer,
			boolean alive
	) {
		return false;
	}

	default void onAfterRespawn(
			MinecraftServer server,
			ServerPlayer oldPlayer,
			ServerPlayer newPlayer,
			boolean alive
	) {
	}
}
