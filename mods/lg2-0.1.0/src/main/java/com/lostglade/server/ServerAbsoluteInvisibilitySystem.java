package com.lostglade.server;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ServerAbsoluteInvisibilitySystem {
	private static final int DURATION_TICKS = 8 * 60 * 20;
	private static final Map<UUID, Long> ACTIVE_UNTIL_TICK = new HashMap<>();
	private static final Map<UUID, RecentBlockAction> RECENT_BLOCK_ACTIONS = new HashMap<>();
	private static final int BLOCK_ACTION_SOUND_WINDOW_TICKS = 2;

	private ServerAbsoluteInvisibilitySystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ServerAbsoluteInvisibilitySystem::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACTIVE_UNTIL_TICK.clear();
			RECENT_BLOCK_ACTIONS.clear();
		});
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
				recordBlockAction(serverPlayer, hitResult.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				recordBlockAction(serverPlayer, pos);
			}
		});
	}

	public static void activate(ServerPlayer player) {
		ACTIVE_UNTIL_TICK.put(player.getUUID(), ((ServerLevel) player.level()).getGameTime() + DURATION_TICKS);
	}

	public static MobEffectInstance createEffectInstance() {
		return new MobEffectInstance(MobEffects.INVISIBILITY, DURATION_TICKS, 0, false, false, true);
	}

	public static boolean isActive(Entity entity) {
		if (!(entity instanceof ServerPlayer player) || player.level().isClientSide()) {
			return false;
		}

		Long untilTick = ACTIVE_UNTIL_TICK.get(player.getUUID());
		if (untilTick == null) {
			return false;
		}

		long nowTick = ((ServerLevel) player.level()).getGameTime();
		if (nowTick >= untilTick || !player.isAlive() || !player.hasEffect(MobEffects.INVISIBILITY)) {
			ACTIVE_UNTIL_TICK.remove(player.getUUID());
			return false;
		}

		return true;
	}

	public static boolean shouldSuppressOutgoingPacket(ServerPlayer receiver, Packet<?> packet) {
		if (packet instanceof ClientboundSoundEntityPacket soundEntityPacket) {
			return shouldSuppressSoundEntityPacketFor(receiver, soundEntityPacket);
		}

		if (packet instanceof ClientboundSoundPacket soundPacket) {
			return shouldSuppressSoundPacketFor(receiver, soundPacket);
		}

		if (packet instanceof ClientboundLevelParticlesPacket particlesPacket) {
			return shouldSuppressParticlePacketFor(
					receiver,
					particlesPacket.getParticle(),
					particlesPacket.getX(),
					particlesPacket.getY(),
					particlesPacket.getZ(),
					particlesPacket.getCount()
			);
		}

		return false;
	}

	public static boolean shouldSuppressParticlePacketFor(ServerPlayer viewer, ParticleOptions particle, double x, double y, double z, int count) {
		if (!isMovementParticleCandidate(particle, count)) {
			return false;
		}

		ServerLevel viewerLevel = (ServerLevel) viewer.level();
		for (ServerPlayer hiddenPlayer : viewerLevel.players()) {
			if (hiddenPlayer == viewer || !isActive(hiddenPlayer)) {
				continue;
			}

			if (isNearMovementParticleOrigin(hiddenPlayer, x, y, z)) {
				return true;
			}
		}

		return false;
	}

	private static boolean shouldSuppressSoundEntityPacketFor(ServerPlayer viewer, ClientboundSoundEntityPacket packet) {
		if (packet.getSource() != SoundSource.PLAYERS) {
			return false;
		}

		ServerLevel viewerLevel = (ServerLevel) viewer.level();
		Entity emitter = viewerLevel.getEntity(packet.getId());
		if (!(emitter instanceof ServerPlayer hiddenPlayer) || hiddenPlayer == viewer || !isActive(hiddenPlayer)) {
			return false;
		}

		return shouldSuppressHalf(hiddenPlayer, packet.getSeed());
	}

	private static boolean shouldSuppressSoundPacketFor(ServerPlayer viewer, ClientboundSoundPacket packet) {
		if (packet.getSource() == SoundSource.PLAYERS) {
			ServerLevel viewerLevel = (ServerLevel) viewer.level();
			for (ServerPlayer hiddenPlayer : viewerLevel.players()) {
				if (hiddenPlayer == viewer || !isActive(hiddenPlayer)) {
					continue;
				}

				if (isNearMovementSoundOrigin(hiddenPlayer, packet.getX(), packet.getY(), packet.getZ())) {
					return shouldSuppressHalf(hiddenPlayer, packet.getSeed());
				}
			}

			return false;
		}

		if (packet.getSource() == SoundSource.BLOCKS) {
			return shouldSuppressBlockActionSoundPacketFor(viewer, packet);
		}

		return false;
	}

	private static boolean shouldSuppressBlockActionSoundPacketFor(ServerPlayer viewer, ClientboundSoundPacket packet) {
		ServerLevel viewerLevel = (ServerLevel) viewer.level();
		long nowTick = viewerLevel.getGameTime();
		for (ServerPlayer hiddenPlayer : viewerLevel.players()) {
			if (hiddenPlayer == viewer || !isActive(hiddenPlayer)) {
				continue;
			}

			RecentBlockAction action = RECENT_BLOCK_ACTIONS.get(hiddenPlayer.getUUID());
			if (action == null || action.untilTick < nowTick || !action.dimension.equals(viewerLevel.dimension())) {
				continue;
			}

			if (isNearBlockActionSoundOrigin(action.pos, packet.getX(), packet.getY(), packet.getZ())) {
				return shouldSuppressHalf(hiddenPlayer, packet.getSeed());
			}
		}

		return false;
	}

	private static boolean isMovementParticleCandidate(ParticleOptions particle, int count) {
		return particle instanceof BlockParticleOption && count > 0;
	}

	private static boolean isNearMovementParticleOrigin(ServerPlayer player, double x, double y, double z) {
		double dx = x - player.getX();
		double dz = z - player.getZ();
		double horizontalDistanceSqr = dx * dx + dz * dz;
		if (horizontalDistanceSqr > 6.25D) {
			return false;
		}

		double minY = player.getY() - 1.75D;
		double maxY = player.getY() + 2.25D;
		return y >= minY && y <= maxY;
	}

	private static boolean isNearMovementSoundOrigin(ServerPlayer player, double x, double y, double z) {
		double dx = x - player.getX();
		double dz = z - player.getZ();
		double horizontalDistanceSqr = dx * dx + dz * dz;
		if (horizontalDistanceSqr > 9.0D) {
			return false;
		}

		double minY = player.getY() - 1.5D;
		double maxY = player.getY() + 2.0D;
		return y >= minY && y <= maxY;
	}

	private static boolean isNearBlockActionSoundOrigin(BlockPos pos, double x, double y, double z) {
		double centerX = pos.getX() + 0.5D;
		double centerY = pos.getY() + 0.5D;
		double centerZ = pos.getZ() + 0.5D;
		double dx = x - centerX;
		double dy = y - centerY;
		double dz = z - centerZ;
		return (dx * dx + dy * dy + dz * dz) <= 6.25D;
	}

	private static boolean shouldSuppressHalf(ServerPlayer player, long seed) {
		long value = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits() ^ seed;
		return (value & 1L) == 0L;
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE_UNTIL_TICK.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, Long>> iterator = ACTIVE_UNTIL_TICK.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Long> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || nowTick >= entry.getValue() || !player.isAlive() || !player.hasEffect(MobEffects.INVISIBILITY)) {
				iterator.remove();
			}
		}

		Iterator<Map.Entry<UUID, RecentBlockAction>> blockActionIterator = RECENT_BLOCK_ACTIONS.entrySet().iterator();
		while (blockActionIterator.hasNext()) {
			Map.Entry<UUID, RecentBlockAction> entry = blockActionIterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			RecentBlockAction action = entry.getValue();
			if (player == null || !player.isAlive() || !isActive(player) || nowTick > action.untilTick) {
				blockActionIterator.remove();
			}
		}
	}

	private static void recordBlockAction(ServerPlayer player, BlockPos pos) {
		ServerLevel level = (ServerLevel) player.level();
		RECENT_BLOCK_ACTIONS.put(
				player.getUUID(),
				new RecentBlockAction(level.dimension(), pos.immutable(), level.getGameTime() + BLOCK_ACTION_SOUND_WINDOW_TICKS)
		);
	}

	private record RecentBlockAction(ResourceKey<Level> dimension, BlockPos pos, long untilTick) {
	}
}
