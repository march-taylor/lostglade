package com.lostglade.server.glitch;

import com.lostglade.config.GlitchConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public interface BlockUseGlitchHandler extends ServerGlitchHandler {
	boolean triggerOnUseBlock(
			MinecraftServer server,
			RandomSource random,
			GlitchConfig.GlitchEntry entry,
			double stabilityPercent,
			ServerPlayer player,
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			InteractionHand hand,
			BlockHitResult hitResult
	);
}
