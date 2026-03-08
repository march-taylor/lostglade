package com.lostglade.server.glitch;

import com.lostglade.config.GlitchConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;

public interface EntityUseGlitchHandler extends ServerGlitchHandler {
	boolean triggerOnUseEntity(
			MinecraftServer server,
			RandomSource random,
			GlitchConfig.GlitchEntry entry,
			double stabilityPercent,
			ServerPlayer player,
			Entity entity,
			InteractionHand hand,
			EntityHitResult hitResult
	);
}
