package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilityConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class OrthodoxStockSystem {
	private static final String RACE_ID = "orthodox";
	private static final double MAX_HEALTH_HEARTS = 12.0D;
	private static final int LIGHT_LEVEL = 12;
	private static final double FIRST_BLESSING_HEIGHT = 120.0D;
	private static final double SECOND_BLESSING_HEIGHT = 240.0D;
	private static final Identifier MAX_HEALTH_MODIFIER_ID =
			Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "orthodox_stock_max_health");

	private static final ManagedInfiniteEffect HEIGHT_REGENERATION =
			new ManagedInfiniteEffect(MobEffects.REGENERATION, false, false, true);
	private static final ManagedInfiniteEffect HEIGHT_RESISTANCE =
			new ManagedInfiniteEffect(MobEffects.RESISTANCE, false, false, true);
	private static final ManagedInfiniteEffect ENCHANTED_NAUSEA =
			new ManagedInfiniteEffect(MobEffects.NAUSEA, false, true, true);
	private static final ManagedInfiniteEffect ENCHANTED_SLOWNESS =
			new ManagedInfiniteEffect(MobEffects.SLOWNESS, false, true, true);
	private static final ManagedInfiniteEffect ENCHANTED_WEAKNESS =
			new ManagedInfiniteEffect(MobEffects.WEAKNESS, false, true, true);
	private static final ManagedInfiniteEffect ENCHANTED_MINING_FATIGUE =
			new ManagedInfiniteEffect(MobEffects.MINING_FATIGUE, false, true, true);
	private static final ManagedInfiniteEffect ENCHANTED_POISON =
			new ManagedInfiniteEffect(MobEffects.POISON, false, true, true);

	private static final Map<UUID, LightKey> PLAYER_LIGHTS = new HashMap<>();
	private static final Map<LightKey, ManagedLight> MANAGED_LIGHTS = new HashMap<>();

	private OrthodoxStockSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(OrthodoxStockSystem::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearPlayer(server, handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(OrthodoxStockSystem::clearAll);
	}

	private static void tick(MinecraftServer server) {
		if (server == null) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (getStockAbility(player) == null) {
				clearPlayer(server, player);
				continue;
			}
			syncMaxHealth(player);
			syncLight(server, player);
			syncHeightBlessing(player);
			syncEnchantedPenalty(player);
		}
	}

	private static void syncMaxHealth(ServerPlayer player) {
		AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
		if (attribute == null) return;
		double amount = MAX_HEALTH_HEARTS * 2.0D - 20.0D;
		AttributeModifier current = attribute.getModifier(MAX_HEALTH_MODIFIER_ID);
		if (current == null || current.operation() != AttributeModifier.Operation.ADD_VALUE
				|| Math.abs(current.amount() - amount) > 1.0E-9D) {
			if (current != null) attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
			attribute.addTransientModifier(new AttributeModifier(
					MAX_HEALTH_MODIFIER_ID,
					amount,
					AttributeModifier.Operation.ADD_VALUE
			));
		}
		if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
	}

	private static void syncHeightBlessing(ServerPlayer player) {
		int amplifier = player.getY() >= SECOND_BLESSING_HEIGHT ? 1
				: player.getY() >= FIRST_BLESSING_HEIGHT ? 0 : -1;
		if (amplifier >= 0) {
			HEIGHT_REGENERATION.ensure(player, amplifier);
			HEIGHT_RESISTANCE.ensure(player, amplifier);
		} else {
			HEIGHT_REGENERATION.clear(player);
			HEIGHT_RESISTANCE.clear(player);
		}
	}

	private static void syncEnchantedPenalty(ServerPlayer player) {
		if (hasEnchantedItem(player)) {
			ENCHANTED_NAUSEA.ensure(player, 0);
			ENCHANTED_SLOWNESS.ensure(player, 1);
			ENCHANTED_WEAKNESS.ensure(player, 0);
			ENCHANTED_MINING_FATIGUE.ensure(player, 0);
			ENCHANTED_POISON.ensure(player, 0);
		} else {
			clearEnchantedPenalty(player);
		}
	}

	private static boolean hasEnchantedItem(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (!stack.isEmpty() && stack.isEnchanted()) return true;
		}
		return false;
	}

	private static void syncLight(MinecraftServer server, ServerPlayer player) {
		LightKey desired = findLightPosition(player);
		LightKey current = PLAYER_LIGHTS.get(player.getUUID());
		if (desired != null && desired.equals(current)) return;
		if (current != null) releaseLight(server, player.getUUID(), current);
		if (desired != null && acquireLight(player.level(), player.getUUID(), desired)) {
			PLAYER_LIGHTS.put(player.getUUID(), desired);
		} else {
			PLAYER_LIGHTS.remove(player.getUUID());
		}
	}

	private static LightKey findLightPosition(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.35D, player.getZ());
		BlockPos eye = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		BlockPos[] candidates = feet.equals(eye) ? new BlockPos[]{feet} : new BlockPos[]{feet, eye};
		for (BlockPos pos : candidates) {
			if (!level.isInWorldBounds(pos) || !level.hasChunkAt(pos)) continue;
			LightKey key = new LightKey(level.dimension(), pos.immutable());
			BlockState state = level.getBlockState(pos);
			if (MANAGED_LIGHTS.containsKey(key) || state.isAir() || state.is(Blocks.WATER)) return key;
		}
		return null;
	}

	private static boolean acquireLight(ServerLevel level, UUID playerId, LightKey key) {
		ManagedLight existing = MANAGED_LIGHTS.get(key);
		if (existing != null) {
			existing.owners.add(playerId);
			return true;
		}
		BlockState original = level.getBlockState(key.pos);
		if (!original.isAir() && !original.is(Blocks.WATER)) return false;
		BlockState light = Blocks.LIGHT.defaultBlockState()
				.setValue(LightBlock.LEVEL, LIGHT_LEVEL)
				.setValue(LightBlock.WATERLOGGED, original.is(Blocks.WATER));
		if (!level.setBlock(key.pos, light, Block.UPDATE_ALL)) return false;
		ManagedLight managed = new ManagedLight(original, light);
		managed.owners.add(playerId);
		MANAGED_LIGHTS.put(key, managed);
		return true;
	}

	private static void releaseLight(MinecraftServer server, UUID playerId, LightKey key) {
		PLAYER_LIGHTS.remove(playerId, key);
		ManagedLight managed = MANAGED_LIGHTS.get(key);
		if (managed == null || !managed.owners.remove(playerId) || !managed.owners.isEmpty()) return;
		MANAGED_LIGHTS.remove(key);
		ServerLevel level = server.getLevel(key.dimension);
		if (level != null && level.getBlockState(key.pos).equals(managed.lightState)) {
			level.setBlock(key.pos, managed.originalState, Block.UPDATE_ALL);
		}
	}

	private static RaceAbilityConfig getStockAbility(ServerPlayer player) {
		if (player == null) return null;
		Optional<PlayerRaceConfig> race = ServerRaceSystem.getRace(player);
		if (race.isEmpty() || !RACE_ID.equalsIgnoreCase(race.get().id)
				|| !ServerRaceSystem.hasUnlockedAbility(player, RaceAbilitySlot.STOCK)) return null;
		RaceAbilityConfig ability = ServerRaceSystem.getAbility(race.get(), RaceAbilitySlot.STOCK);
		return ability != null && ability.enabled ? ability : null;
	}

	private static void clearEnchantedPenalty(ServerPlayer player) {
		ENCHANTED_NAUSEA.clear(player);
		ENCHANTED_SLOWNESS.clear(player);
		ENCHANTED_WEAKNESS.clear(player);
		ENCHANTED_MINING_FATIGUE.clear(player);
		ENCHANTED_POISON.clear(player);
	}

	private static void clearPlayer(MinecraftServer server, ServerPlayer player) {
		if (player == null) return;
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null && maxHealth.getModifier(MAX_HEALTH_MODIFIER_ID) != null) {
			maxHealth.removeModifier(MAX_HEALTH_MODIFIER_ID);
			if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
		}
		HEIGHT_REGENERATION.clear(player);
		HEIGHT_RESISTANCE.clear(player);
		clearEnchantedPenalty(player);
		LightKey light = PLAYER_LIGHTS.get(player.getUUID());
		if (light != null) releaseLight(server, player.getUUID(), light);
	}

	private static void clearAll(MinecraftServer server) {
		if (server != null) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) clearPlayer(server, player);
			for (Map.Entry<LightKey, ManagedLight> entry : Map.copyOf(MANAGED_LIGHTS).entrySet()) {
				ServerLevel level = server.getLevel(entry.getKey().dimension);
				ManagedLight managed = entry.getValue();
				if (level != null && level.getBlockState(entry.getKey().pos).equals(managed.lightState)) {
					level.setBlock(entry.getKey().pos, managed.originalState, Block.UPDATE_ALL);
				}
			}
		}
		PLAYER_LIGHTS.clear();
		MANAGED_LIGHTS.clear();
		HEIGHT_REGENERATION.clearAll(server);
		HEIGHT_RESISTANCE.clearAll(server);
		ENCHANTED_NAUSEA.clearAll(server);
		ENCHANTED_SLOWNESS.clearAll(server);
		ENCHANTED_WEAKNESS.clearAll(server);
		ENCHANTED_MINING_FATIGUE.clearAll(server);
		ENCHANTED_POISON.clearAll(server);
	}

	private record LightKey(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final class ManagedLight {
		private final BlockState originalState;
		private final BlockState lightState;
		private final Set<UUID> owners = new HashSet<>();

		private ManagedLight(BlockState originalState, BlockState lightState) {
			this.originalState = originalState;
			this.lightState = lightState;
		}
	}
}
