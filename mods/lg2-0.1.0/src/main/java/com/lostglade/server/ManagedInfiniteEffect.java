package com.lostglade.server;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Owns one indefinitely displayed effect without clobbering an effect supplied by a potion,
 * beacon, or another system. This follows the ownership pattern used by the race goggles.
 */
public final class ManagedInfiniteEffect {
	private final Holder<MobEffect> effect;
	private final boolean ambient;
	private final boolean visible;
	private final boolean showIcon;
	private final Set<UUID> managedPlayers = new HashSet<>();

	public ManagedInfiniteEffect(Holder<MobEffect> effect, boolean ambient, boolean visible, boolean showIcon) {
		this.effect = effect;
		this.ambient = ambient;
		this.visible = visible;
		this.showIcon = showIcon;
	}

	/** Ensures our effect is present, but leaves a same-type external effect untouched. */
	public void ensure(ServerPlayer player, int amplifier) {
		if (player == null) {
			return;
		}
		MobEffectInstance current = player.getEffect(effect);
		if (current != null && !isManaged(current)) {
			managedPlayers.remove(player.getUUID());
			return;
		}
		if (current == null || current.getAmplifier() != amplifier) {
			if (current != null) {
				player.removeEffect(effect);
			}
			player.addEffect(new MobEffectInstance(
					effect,
					MobEffectInstance.INFINITE_DURATION,
					amplifier,
					ambient,
					visible,
					showIcon
			));
		}
		managedPlayers.add(player.getUUID());
	}

	/** Removes the effect only when the currently active instance is ours. */
	public void clear(ServerPlayer player) {
		if (player == null || !managedPlayers.remove(player.getUUID())) {
			return;
		}
		MobEffectInstance current = player.getEffect(effect);
		if (isManaged(current)) {
			player.removeEffect(effect);
		}
	}

	public void clearAll(MinecraftServer server) {
		if (server == null) {
			managedPlayers.clear();
			return;
		}
		for (UUID playerId : Set.copyOf(managedPlayers)) {
			clear(server.getPlayerList().getPlayer(playerId));
		}
		managedPlayers.clear();
	}

	private boolean isManaged(MobEffectInstance instance) {
		return instance != null
				&& instance.isInfiniteDuration()
				&& instance.isAmbient() == ambient
				&& instance.isVisible() == visible
				&& instance.showIcon() == showIcon;
	}
}
