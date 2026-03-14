package com.lostglade.block;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class ExitSignSoundHelper {
	private static final double MAX_SOUND_DISTANCE_SQR = 32.0D * 32.0D;
	private static final float SOUND_VOLUME = 1.0F;
	private static final float SOUND_PITCH = 1.0F;

	private ExitSignSoundHelper() {
	}

	public static void playPlaceSound(ServerLevel level, BlockPos pos) {
		playSound(level, pos, SoundEvents.STONE_PLACE, SoundEvents.WOOD_PLACE);
	}

	public static void playBreakSound(ServerLevel level, BlockPos pos) {
		playSound(level, pos, SoundEvents.STONE_BREAK, SoundEvents.WOOD_BREAK);
	}

	public static void playHitSound(ServerLevel level, BlockPos pos) {
		playSound(level, pos, SoundEvents.STONE_HIT, SoundEvents.WOOD_HIT);
	}

	private static void playSound(ServerLevel level, BlockPos pos, SoundEvent packSound, SoundEvent fallbackSound) {
		double x = pos.getX() + 0.5D;
		double y = pos.getY() + 0.5D;
		double z = pos.getZ() + 0.5D;
		long seed = level.getRandom().nextLong();

		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(x, y, z) > MAX_SOUND_DISTANCE_SQR) {
				continue;
			}

			SoundEvent sound = PolymerResourcePackUtils.hasMainPack(player) ? packSound : fallbackSound;
			Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
			player.connection.send(new ClientboundSoundPacket(holder, SoundSource.BLOCKS, x, y, z, SOUND_VOLUME, SOUND_PITCH, seed));
		}
	}
}
