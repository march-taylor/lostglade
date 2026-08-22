package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.mixin.ArmorStandAccessor;
import com.lostglade.mixin.EntityPassengerAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrthodoxDefenseSystem {
	private static final String ORTHODOX_RACE_ID = "orthodox";
	private static final String WINGS_TAG = "lg2.orthodox_angel_wings";
	private static final String WINGS_OWNER_TAG_PREFIX = "lg2.orthodox_angel_wings_owner:";
	private static final Identifier WINGS_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "orthodox_angel_wings");
	private static final Map<UUID, DefenseSession> SESSIONS = new HashMap<>();

	private OrthodoxDefenseSystem() {
	}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
				!world.isClientSide() && player instanceof ServerPlayer serverPlayer && isActive(serverPlayer)
						? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
				!world.isClientSide() && player instanceof ServerPlayer serverPlayer && isActive(serverPlayer)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
				!world.isClientSide() && player instanceof ServerPlayer serverPlayer && isActive(serverPlayer)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) ->
				!world.isClientSide() && player instanceof ServerPlayer serverPlayer && isActive(serverPlayer)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) ->
				!world.isClientSide() && player instanceof ServerPlayer serverPlayer && isActive(serverPlayer)
						? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
				!(player instanceof ServerPlayer serverPlayer) || !isActive(serverPlayer));

		ServerTickEvents.END_SERVER_TICK.register(OrthodoxDefenseSystem::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> deactivate(handler.player, false));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			deactivate(oldPlayer, false);
			newPlayer.onUpdateAbilities();
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(OrthodoxDefenseSystem::clearAll);
	}

	public static boolean activate(ServerPlayer player, long durationTicks) {
		if (player == null || !player.isAlive() || player.isSpectator() || durationTicks <= 0L || isActive(player)) {
			return false;
		}

		Abilities abilities = player.getAbilities();
		DefenseSession session = new DefenseSession(
				player.level().getServer().overworld().getGameTime() + durationTicks,
				player.gameMode.getGameModeForPlayer(),
				abilities.invulnerable,
				abilities.mayfly,
				abilities.flying,
				abilities.getFlyingSpeed()
		);
		SESSIONS.put(player.getUUID(), session);
		player.stopUsingItem();
		player.closeContainer();
		player.setGameMode(GameType.ADVENTURE);
		applyFlight(player);
		ensureWings(player, session);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.35F);
		player.level().sendParticles(
				ParticleTypes.END_ROD,
				player.getX(), player.getY() + 1.0D, player.getZ(),
				42, 0.7D, 1.0D, 0.7D, 0.035D
		);
		return true;
	}

	public static boolean isActive(ServerPlayer player) {
		return player != null && SESSIONS.containsKey(player.getUUID());
	}

	public static boolean shouldCancelDamage(LivingEntity victim) {
		return victim instanceof ServerPlayer player && isActive(player);
	}

	public static float protectHealthChange(LivingEntity entity, float requestedHealth) {
		if (!(entity instanceof ServerPlayer player) || !isActive(player)) return requestedHealth;
		return Math.max(entity.getHealth(), requestedHealth);
	}

	public static boolean shouldBlockWorldInteraction(ServerPlayer player) {
		return isActive(player);
	}

	private static void tick(MinecraftServer server) {
		if (server == null || SESSIONS.isEmpty()) return;
		long nowTick = server.overworld().getGameTime();
		for (Map.Entry<UUID, DefenseSession> entry : new ArrayList<>(SESSIONS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			DefenseSession session = entry.getValue();
			if (player == null) continue;
			if (!player.isAlive() || nowTick >= session.endTick || !isOrthodox(player)) {
				deactivate(player, true);
				continue;
			}

			if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) player.setGameMode(GameType.ADVENTURE);
			Abilities abilities = player.getAbilities();
			boolean changed = !abilities.invulnerable || !abilities.mayfly;
			abilities.invulnerable = true;
			abilities.mayfly = true;
			if (changed) player.onUpdateAbilities();
			player.fallDistance = 0.0F;
			ensureWings(player, session);
			updateWings(player, session);
			emitFlightSounds(player, session, nowTick);
			if ((nowTick & 7L) == 0L) emitWingGlow(player);
		}
	}

	private static void emitFlightSounds(ServerPlayer player, DefenseSession session, long nowTick) {
		if (!player.getAbilities().flying || player.onGround()) {
			session.nextWingFlapTick = nowTick;
			return;
		}
		if (nowTick < session.nextWingFlapTick) return;
		float pitchVariation = (player.getRandom().nextFloat() - 0.5F) * 0.10F;
		player.level().playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
				SoundSource.PLAYERS, 0.68F, 1.48F + pitchVariation);
		player.level().playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
				SoundSource.PLAYERS, 0.20F, 1.25F + pitchVariation);
		session.nextWingFlapTick = nowTick + 11L + player.getRandom().nextInt(4);
	}

	private static boolean isOrthodox(ServerPlayer player) {
		return ServerRaceSystem.getRace(player)
				.map(race -> race.id != null && ORTHODOX_RACE_ID.equalsIgnoreCase(race.id.trim()))
				.orElse(false);
	}

	private static void applyFlight(ServerPlayer player) {
		Abilities abilities = player.getAbilities();
		abilities.invulnerable = true;
		abilities.mayfly = true;
		abilities.flying = true;
		player.fallDistance = 0.0F;
		player.onUpdateAbilities();
	}

	private static void deactivate(ServerPlayer player, boolean effects) {
		if (player == null) return;
		DefenseSession session = SESSIONS.remove(player.getUUID());
		if (session == null) return;
		removeWings(player, session);
		player.setGameMode(session.previousGameType);

		Abilities abilities = player.getAbilities();
		abilities.invulnerable = session.previousInvulnerable;
		abilities.mayfly = session.previousMayfly;
		abilities.flying = session.previousFlying && session.previousMayfly;
		abilities.setFlyingSpeed(session.previousFlyingSpeed);
		player.fallDistance = 0.0F;
		player.onUpdateAbilities();
		if (effects && player.level() instanceof ServerLevel level) {
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.85F, 1.4F);
			level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0D, player.getZ(), 24, 0.6D, 0.8D, 0.6D, 0.02D);
		}
	}

	private static void clearAll(MinecraftServer server) {
		if (server != null) {
			for (UUID playerId : new ArrayList<>(SESSIONS.keySet())) {
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				if (player != null) deactivate(player, false);
			}
			for (ServerLevel level : server.getAllLevels()) {
				for (Entity entity : level.getAllEntities()) {
					if (entity.getTags().contains(WINGS_TAG)) entity.discard();
				}
			}
		}
		SESSIONS.clear();
	}

	private static void ensureWings(ServerPlayer player, DefenseSession session) {
		ArmorStand wings = findWings(player, session);
		if (wings != null) {
			configureWings(wings);
			attachPassenger(player, wings);
			return;
		}

		removeWingsEntity(player.level().getServer(), session.displayId);
		ArmorStand stand = EntityType.ARMOR_STAND.create(player.level(), EntitySpawnReason.TRIGGERED);
		if (stand == null) return;
		stand.addTag(WINGS_TAG);
		stand.addTag(WINGS_OWNER_TAG_PREFIX + player.getUUID());
		configureWings(stand);
		stand.setPos(player.getX(), player.getY(), player.getZ());
		player.level().addFreshEntity(stand);
		attachPassenger(player, stand);
		session.displayId = stand.getUUID();
		syncEquipment(stand);
	}

	private static ArmorStand findWings(ServerPlayer player, DefenseSession session) {
		for (Entity passenger : player.getPassengers()) {
			if (passenger instanceof ArmorStand stand && stand.getTags().contains(WINGS_TAG)) {
				session.displayId = stand.getUUID();
				return stand;
			}
		}
		if (session.displayId != null) {
			Entity entity = player.level().getEntity(session.displayId);
			if (entity instanceof ArmorStand stand && stand.getTags().contains(WINGS_TAG)) return stand;
		}
		return null;
	}

	private static void configureWings(ArmorStand stand) {
		stand.setInvisible(true);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		stand.setNoGravity(true);
		stand.setGlowingTag(true);
		stand.setItemSlot(EquipmentSlot.HEAD, createWingsStack());
		((ArmorStandAccessor) stand).lg2$setSmall(false);
		((ArmorStandAccessor) stand).lg2$setMarker(true);
	}

	private static ItemStack createWingsStack() {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, WINGS_MODEL_ID);
		return stack;
	}

	private static void attachPassenger(Entity vehicle, Entity passenger) {
		if (passenger.getVehicle() == vehicle && vehicle.hasPassenger(passenger)) return;
		if (passenger.isPassenger()) passenger.stopRiding();
		((EntityPassengerAccessor) passenger).lg2$setVehicle(vehicle);
		((EntityPassengerAccessor) vehicle).lg2$addPassenger(passenger);
		vehicle.positionRider(passenger);
		if (vehicle.level() instanceof ServerLevel level) {
			ClientboundSetPassengersPacket packet = new ClientboundSetPassengersPacket(vehicle);
			for (ServerPlayer viewer : level.players()) viewer.connection.send(packet);
		}
	}

	private static void updateWings(ServerPlayer player, DefenseSession session) {
		ArmorStand stand = findWings(player, session);
		if (stand == null) return;
		stand.setYRot(player.getYRot());
		stand.setYHeadRot(player.getYRot());
		stand.yBodyRot = player.getYRot();
		stand.yBodyRotO = player.getYRot();
	}

	private static void emitWingGlow(ServerPlayer player) {
		double yaw = Math.toRadians(player.getYRot());
		double backX = Math.sin(yaw) * 0.28D;
		double backZ = -Math.cos(yaw) * 0.28D;
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.END_ROD, player.getX() + backX, player.getY() + 1.15D, player.getZ() + backZ, 3, 0.65D, 0.65D, 0.20D, 0.005D);
	}

	private static void syncEquipment(ArmorStand stand) {
		if (!(stand.level() instanceof ServerLevel level)) return;
		ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(
				stand.getId(),
				List.of(com.mojang.datafixers.util.Pair.of(EquipmentSlot.HEAD, stand.getItemBySlot(EquipmentSlot.HEAD).copy()))
		);
		for (ServerPlayer viewer : level.players()) viewer.connection.send(packet);
	}

	private static void removeWings(ServerPlayer player, DefenseSession session) {
		if (player != null) {
			for (Entity passenger : new ArrayList<>(player.getPassengers())) {
				if (passenger.getTags().contains(WINGS_TAG)) passenger.discard();
			}
		}
		removeWingsEntity(player == null ? null : player.level().getServer(), session.displayId);
		session.displayId = null;
	}

	private static void removeWingsEntity(MinecraftServer server, UUID displayId) {
		if (server == null || displayId == null) return;
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(displayId);
			if (entity != null) {
				entity.discard();
				return;
			}
		}
	}

	private static final class DefenseSession {
		private final long endTick;
		private final GameType previousGameType;
		private final boolean previousInvulnerable;
		private final boolean previousMayfly;
		private final boolean previousFlying;
		private final float previousFlyingSpeed;
		private UUID displayId;
		private long nextWingFlapTick;

		private DefenseSession(long endTick, GameType previousGameType, boolean previousInvulnerable, boolean previousMayfly,
				boolean previousFlying, float previousFlyingSpeed) {
			this.endTick = endTick;
			this.previousGameType = previousGameType;
			this.previousInvulnerable = previousInvulnerable;
			this.previousMayfly = previousMayfly;
			this.previousFlying = previousFlying;
			this.previousFlyingSpeed = previousFlyingSpeed;
		}
	}
}
