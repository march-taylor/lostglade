package com.lostglade.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class ServerAbsoluteInvisibilitySystem {
	private static final int DURATION_TICKS = 8 * 60 * 20;
	private static final Map<UUID, Long> ACTIVE_UNTIL_TICK = new HashMap<>();

	private ServerAbsoluteInvisibilitySystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ServerAbsoluteInvisibilitySystem::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ACTIVE_UNTIL_TICK.clear());
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

	public static boolean shouldSuppressSound(Entity entity) {
		return isActive(entity) && entity.getRandom().nextBoolean();
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
	}
}
