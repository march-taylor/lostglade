package com.lostglade.server;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.lostglade.mixin.EntityTrackedDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.mojang.datafixers.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ServerAbsoluteInvisibilitySystem {
	private static final int DURATION_TICKS = 8 * 60 * 20;
	private static final byte SPRINTING_FLAG_MASK = 0x08;
	private static final long COMBAT_REVEAL_GRACE_TICKS = 100L;
	private static final Map<UUID, Long> ACTIVE_UNTIL_TICK = new HashMap<>();
	private static final Map<UUID, RecentBlockAction> RECENT_BLOCK_ACTIONS = new HashMap<>();
	private static final Map<UUID, Long> PENDING_SELF_INVENTORY_RESYNC = new HashMap<>();
	private static final Map<UUID, Map<UUID, Long>> COMBAT_REVEALS = new HashMap<>();
	private static final Map<UUID, Set<UUID>> TEMPT_REVEALS = new HashMap<>();
	private static final int BLOCK_ACTION_SOUND_WINDOW_TICKS = 2;

	private ServerAbsoluteInvisibilitySystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ServerAbsoluteInvisibilitySystem::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ACTIVE_UNTIL_TICK.clear();
			RECENT_BLOCK_ACTIONS.clear();
			PENDING_SELF_INVENTORY_RESYNC.clear();
			COMBAT_REVEALS.clear();
			TEMPT_REVEALS.clear();
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
		markSharedFlagsDirty(player);
		broadcastArmorVisibilityUpdate(player, true);
		scheduleSelfInventoryResync(player, 1L);
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
		return nowTick < untilTick && player.isAlive() && player.hasEffect(MobEffects.INVISIBILITY);
	}

	public static boolean suppressesMobDetection(Entity entity) {
		if (isActive(entity)) {
			return true;
		}
		if (!(entity instanceof LivingEntity livingEntity)) {
			return false;
		}

		MobEffectInstance effect = livingEntity.getEffect(MobEffects.INVISIBILITY);
		return effect != null && !effect.isVisible();
	}

	public static boolean shouldSuppressMobDetection(Mob mob, LivingEntity target) {
		return suppressesMobDetection(target) && !isRevealedToMob(mob, target);
	}

	public static void revealMobToPlayer(Mob mob, ServerPlayer player) {
		if (!suppressesMobDetection(player)) {
			return;
		}

		ServerLevel level = (ServerLevel) player.level();
		COMBAT_REVEALS
				.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
				.put(mob.getUUID(), level.getGameTime() + COMBAT_REVEAL_GRACE_TICKS);
	}

	public static void beginTemptReveal(Mob mob, ServerPlayer player) {
		if (!suppressesMobDetection(player)) {
			return;
		}

		TEMPT_REVEALS.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>()).add(mob.getUUID());
	}

	public static void endTemptReveal(Mob mob, ServerPlayer player) {
		Set<UUID> mobIds = TEMPT_REVEALS.get(player.getUUID());
		if (mobIds == null) {
			return;
		}
		mobIds.remove(mob.getUUID());
		if (mobIds.isEmpty()) {
			TEMPT_REVEALS.remove(player.getUUID());
		}
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

	public static void maskSprintingMetadataForViewer(ServerPlayer receiver, ClientboundSetEntityDataPacket packet) {
		ServerLevel viewerLevel = (ServerLevel) receiver.level();
		Entity entity = viewerLevel.getEntity(packet.id());
		if (!(entity instanceof ServerPlayer hiddenPlayer) || hiddenPlayer == receiver || !isActive(hiddenPlayer)) {
			return;
		}

		List<SynchedEntityData.DataValue<?>> items = packet.packedItems();
		EntityDataAccessor<Byte> sharedFlagsAccessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		for (int i = 0; i < items.size(); i++) {
			SynchedEntityData.DataValue<?> value = items.get(i);
			if (value.id() != sharedFlagsAccessor.id() || !(value.value() instanceof Byte flags)) {
				continue;
			}

			byte maskedFlags = (byte) (flags & ~SPRINTING_FLAG_MASK);
			if (maskedFlags != flags) {
				items.set(i, SynchedEntityData.DataValue.create(sharedFlagsAccessor, maskedFlags));
			}
			return;
		}
	}

	public static ClientboundSetEquipmentPacket maskArmorEquipmentForViewer(ServerPlayer receiver, ClientboundSetEquipmentPacket packet) {
		ServerLevel viewerLevel = (ServerLevel) receiver.level();
		Entity entity = viewerLevel.getEntity(packet.getEntity());
		if (!(entity instanceof ServerPlayer hiddenPlayer) || !isActive(hiddenPlayer)) {
			return packet;
		}

		List<Pair<EquipmentSlot, ItemStack>> originalSlots = packet.getSlots();
		List<Pair<EquipmentSlot, ItemStack>> rewrittenSlots = null;
		for (int i = 0; i < originalSlots.size(); i++) {
			Pair<EquipmentSlot, ItemStack> slot = originalSlots.get(i);
			if (!isArmorSlot(slot.getFirst())) {
				continue;
			}

			if (rewrittenSlots == null) {
				rewrittenSlots = new ArrayList<>(originalSlots);
			}
			rewrittenSlots.set(i, Pair.of(slot.getFirst(), ItemStack.EMPTY));
		}

		if (rewrittenSlots != null && hiddenPlayer == receiver) {
			scheduleSelfInventoryResync(receiver, 1L);
		}

		return rewrittenSlots == null ? packet : new ClientboundSetEquipmentPacket(packet.getEntity(), rewrittenSlots);
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

		return shouldSuppressMostly(hiddenPlayer, packet.getSeed());
	}

	private static boolean shouldSuppressSoundPacketFor(ServerPlayer viewer, ClientboundSoundPacket packet) {
		if (packet.getSource() == SoundSource.PLAYERS) {
			ServerLevel viewerLevel = (ServerLevel) viewer.level();
			for (ServerPlayer hiddenPlayer : viewerLevel.players()) {
				if (hiddenPlayer == viewer || !isActive(hiddenPlayer)) {
					continue;
				}

				if (isNearMovementSoundOrigin(hiddenPlayer, packet.getX(), packet.getY(), packet.getZ())) {
					return shouldSuppressMostly(hiddenPlayer, packet.getSeed());
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
				return shouldSuppressMostly(hiddenPlayer, packet.getSeed());
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

	private static boolean shouldSuppressMostly(ServerPlayer player, long seed) {
		long value = player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits() ^ seed;
		return Math.floorMod(value, 10L) < 8L;
	}

	private static boolean isArmorSlot(EquipmentSlot slot) {
		return slot == EquipmentSlot.HEAD
				|| slot == EquipmentSlot.CHEST
				|| slot == EquipmentSlot.LEGS
				|| slot == EquipmentSlot.FEET;
	}

	private static void broadcastArmorVisibilityUpdate(ServerPlayer player, boolean hidden) {
		ServerLevel level = (ServerLevel) player.level();
		for (ServerPlayer viewer : level.players()) {
			viewer.connection.send(createArmorVisibilityPacket(player, hidden));
		}
	}

	private static ClientboundSetEquipmentPacket createArmorVisibilityPacket(ServerPlayer player, boolean hidden) {
		List<Pair<EquipmentSlot, ItemStack>> slots = new ArrayList<>(4);
		slots.add(Pair.of(EquipmentSlot.HEAD, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.HEAD).copy()));
		slots.add(Pair.of(EquipmentSlot.CHEST, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.CHEST).copy()));
		slots.add(Pair.of(EquipmentSlot.LEGS, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.LEGS).copy()));
		slots.add(Pair.of(EquipmentSlot.FEET, hidden ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.FEET).copy()));
		return new ClientboundSetEquipmentPacket(player.getId(), slots);
	}

	private static void markSharedFlagsDirty(ServerPlayer player) {
		EntityDataAccessor<Byte> accessor = EntityTrackedDataAccessor.lg2$getDataSharedFlagsId();
		byte current = player.getEntityData().get(accessor);
		player.getEntityData().set(accessor, current, true);
	}

	private static void tick(MinecraftServer server) {
		long nowTick = server.overworld().getGameTime();
		if (!ACTIVE_UNTIL_TICK.isEmpty()) {
			Iterator<Map.Entry<UUID, Long>> iterator = ACTIVE_UNTIL_TICK.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, Long> entry = iterator.next();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null || nowTick >= entry.getValue() || !player.isAlive() || !player.hasEffect(MobEffects.INVISIBILITY)) {
					iterator.remove();
					if (player != null) {
						markSharedFlagsDirty(player);
						broadcastArmorVisibilityUpdate(player, false);
						scheduleSelfInventoryResync(player, 0L);
					}
					COMBAT_REVEALS.remove(entry.getKey());
					TEMPT_REVEALS.remove(entry.getKey());
				} else if ((nowTick & 7L) == 0L) {
					clearNearbyMobTargets(player);
				}
			}
		}

		if (!COMBAT_REVEALS.isEmpty()) {
			Iterator<Map.Entry<UUID, Map<UUID, Long>>> iterator = COMBAT_REVEALS.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, Map<UUID, Long>> playerEntry = iterator.next();
				ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
				if (player == null || !player.isAlive() || !suppressesMobDetection(player)) {
					iterator.remove();
					continue;
				}

				Iterator<Map.Entry<UUID, Long>> revealIterator = playerEntry.getValue().entrySet().iterator();
				while (revealIterator.hasNext()) {
					Map.Entry<UUID, Long> revealEntry = revealIterator.next();
					Mob mob = findMob(server, revealEntry.getKey());
					if (mob == null || !mob.isAlive() || mob.level() != player.level()) {
						revealIterator.remove();
						continue;
					}

					if (mob.getTarget() == player) {
						revealEntry.setValue(nowTick + COMBAT_REVEAL_GRACE_TICKS);
						continue;
					}

					if (nowTick > revealEntry.getValue()) {
						revealIterator.remove();
					}
				}

				if (playerEntry.getValue().isEmpty()) {
					iterator.remove();
				}
			}
		}

		if (!TEMPT_REVEALS.isEmpty()) {
			Iterator<Map.Entry<UUID, Set<UUID>>> iterator = TEMPT_REVEALS.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, Set<UUID>> entry = iterator.next();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player == null || !player.isAlive() || !suppressesMobDetection(player)) {
					iterator.remove();
					continue;
				}

				entry.getValue().removeIf(mobId -> {
					Mob mob = findMob(server, mobId);
					return mob == null || !mob.isAlive() || mob.level() != player.level();
				});
				if (entry.getValue().isEmpty()) {
					iterator.remove();
				}
			}
		}

		if (!RECENT_BLOCK_ACTIONS.isEmpty()) {
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

		if (!PENDING_SELF_INVENTORY_RESYNC.isEmpty()) {
			Iterator<Map.Entry<UUID, Long>> iterator = PENDING_SELF_INVENTORY_RESYNC.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, Long> entry = iterator.next();
				if (nowTick < entry.getValue()) {
					continue;
				}

				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					syncPlayerInventory(player);
				}
				iterator.remove();
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

	private static void scheduleSelfInventoryResync(ServerPlayer player, long delayTicks) {
		ServerLevel level = (ServerLevel) player.level();
		PENDING_SELF_INVENTORY_RESYNC.put(player.getUUID(), level.getGameTime() + Math.max(0L, delayTicks));
	}

	private static void syncPlayerInventory(ServerPlayer player) {
		player.getInventory().setChanged();
		player.inventoryMenu.broadcastFullState();
		player.inventoryMenu.sendAllDataToRemote();
		if (player.containerMenu != player.inventoryMenu) {
			player.containerMenu.broadcastFullState();
			player.containerMenu.sendAllDataToRemote();
		}
	}

	private static void clearNearbyMobTargets(ServerPlayer hiddenPlayer) {
		ServerLevel level = (ServerLevel) hiddenPlayer.level();
		AABB searchArea = hiddenPlayer.getBoundingBox().inflate(96.0D, 32.0D, 96.0D);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, searchArea)) {
			if (mob.getTarget() != hiddenPlayer || isRevealedToMob(mob, hiddenPlayer)) {
				continue;
			}
			mob.setTarget(null);
		}
	}

	private static boolean isRevealedToMob(Mob mob, LivingEntity target) {
		if (!(target instanceof ServerPlayer player)) {
			return false;
		}

		Set<UUID> temptMobIds = TEMPT_REVEALS.get(player.getUUID());
		if (temptMobIds != null && temptMobIds.contains(mob.getUUID())) {
			return true;
		}

		Map<UUID, Long> combatMobIds = COMBAT_REVEALS.get(player.getUUID());
		return combatMobIds != null && combatMobIds.containsKey(mob.getUUID());
	}

	private static Mob findMob(MinecraftServer server, UUID mobId) {
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(mobId);
			if (entity instanceof Mob mob) {
				return mob;
			}
		}
		return null;
	}

	private record RecentBlockAction(ResourceKey<Level> dimension, BlockPos pos, long untilTick) {
	}
}
