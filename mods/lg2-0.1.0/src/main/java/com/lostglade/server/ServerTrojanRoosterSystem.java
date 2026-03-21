package com.lostglade.server;

import com.lostglade.entity.TrojanChickenAccess;
import com.lostglade.item.ModItems;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerTrojanRoosterSystem {
	private static final int TROJAN_THRESHOLD = 64;
	private static final double TOUCH_EPSILON = 0.01D;
	private static final int AWAKENING_TICKS = 20;
	private static final int AMBUSH_TIMEOUT_TICKS = 20 * 90;
	private static final int AMBUSH_REVEAL_TICKS = 20 * 3;
	private static final double AMBUSH_BEHIND_DISTANCE = 5.0D;
	private static final double CHASE_SPEED_PER_TICK = 0.23D * 1.5D;
	private static final double LOOK_DOT_THRESHOLD = 0.72D;
	private static final int AMBUSH_SPAWN_STEPS = 8;
	private static final String DISPLAY_ROOT_TAG = "lg2_trojan_rooster_display";
	private static final String BAN_REASON = "Вага-бага-ждуби-ду >:D";
	private static final SimpleParticleType FEED_PARTICLE = resolveFeedParticle();
	private static final Identifier TROJAN_THEME_SOUND_ID = Identifier.fromNamespaceAndPath("minecraft", "custom.trojan_rooster_theme");
	private static final Holder<SoundEvent> TROJAN_THEME_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(TROJAN_THEME_SOUND_ID));
	private static final Identifier LAVA_CHICKEN_SOUND_ID = BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.MUSIC_DISC_LAVA_CHICKEN.value());
	private static final Set<ItemEntity> TRACKED_BITCOIN_ITEMS = ConcurrentHashMap.newKeySet();
	private static final Set<Chicken> TRACKED_TROJAN_CHICKENS = ConcurrentHashMap.newKeySet();
	private static final ConcurrentHashMap<UUID, TrojanRoosterState> ACTIVE_STATES = new ConcurrentHashMap<>();

	private ServerTrojanRoosterSystem() {
	}

	public static void register() {
		TRACKED_BITCOIN_ITEMS.clear();
		TRACKED_TROJAN_CHICKENS.clear();
		ACTIVE_STATES.clear();

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity itemEntity && isBitcoinItem(itemEntity)) {
				TRACKED_BITCOIN_ITEMS.add(itemEntity);
				return;
			}

			if (entity instanceof Display.ItemDisplay display && display.getTags().contains(DISPLAY_ROOT_TAG)) {
				display.discard();
				return;
			}

			if (entity instanceof Chicken chicken) {
				TrojanChickenAccess access = (TrojanChickenAccess) chicken;
				if (access.lg2$isTrojanRooster()) {
					TRACKED_TROJAN_CHICKENS.add(chicken);
					prepareTrojanChicken(chicken);
				}
			}
		});

		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity itemEntity) {
				TRACKED_BITCOIN_ITEMS.remove(itemEntity);
				return;
			}

			if (entity instanceof Chicken chicken) {
				TRACKED_TROJAN_CHICKENS.remove(chicken);
				if (chicken.isRemoved() || !chicken.isAlive()) {
					stopTheme(ACTIVE_STATES.remove(chicken.getUUID()), world instanceof ServerLevel serverLevel ? serverLevel : null);
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickBitcoinOfferings();
			tickTrojanChickens();
		});
	}

	private static void tickBitcoinOfferings() {
		for (ItemEntity itemEntity : TRACKED_BITCOIN_ITEMS) {
			if (itemEntity.isRemoved() || !itemEntity.isAlive() || !isBitcoinItem(itemEntity)) {
				TRACKED_BITCOIN_ITEMS.remove(itemEntity);
				continue;
			}

			if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
				continue;
			}

			Chicken chicken = findNearestAdultChicken(serverLevel, itemEntity);
			if (chicken == null) {
				continue;
			}

			feedChicken(serverLevel, chicken, itemEntity);
			if (itemEntity.isRemoved() || !itemEntity.isAlive() || !isBitcoinItem(itemEntity)) {
				TRACKED_BITCOIN_ITEMS.remove(itemEntity);
			}
		}
	}

	private static void tickTrojanChickens() {
		for (Chicken chicken : TRACKED_TROJAN_CHICKENS) {
			if (chicken.isRemoved() || !chicken.isAlive()) {
				TRACKED_TROJAN_CHICKENS.remove(chicken);
				stopTheme(ACTIVE_STATES.remove(chicken.getUUID()), null);
				continue;
			}

			if (!(chicken.level() instanceof ServerLevel level)) {
				TRACKED_TROJAN_CHICKENS.remove(chicken);
				stopTheme(ACTIVE_STATES.remove(chicken.getUUID()), null);
				continue;
			}

			TrojanChickenAccess access = (TrojanChickenAccess) chicken;
			if (!access.lg2$isTrojanRooster()) {
				TRACKED_TROJAN_CHICKENS.remove(chicken);
				stopTheme(ACTIVE_STATES.remove(chicken.getUUID()), level);
				continue;
			}

			prepareTrojanChicken(chicken);
			TrojanRoosterState state = ACTIVE_STATES.computeIfAbsent(chicken.getUUID(), ignored -> createAwakeningState(level, chicken));
			tickTrojanState(level, chicken, state);
		}
	}

	private static Chicken findNearestAdultChicken(ServerLevel level, ItemEntity itemEntity) {
		AABB itemBox = itemEntity.getBoundingBox().inflate(TOUCH_EPSILON);
		List<Chicken> chickens = level.getEntities(
				EntityType.CHICKEN,
				itemBox,
				chicken -> chicken.isAlive()
						&& !chicken.isBaby()
						&& !((TrojanChickenAccess) chicken).lg2$isTrojanRooster()
						&& chicken.getBoundingBox().intersects(itemBox)
		);

		Chicken best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Chicken chicken : chickens) {
			double distance = chicken.distanceToSqr(itemEntity);
			if (distance < bestDistance) {
				best = chicken;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static void feedChicken(ServerLevel level, Chicken chicken, ItemEntity itemEntity) {
		ItemStack stack = itemEntity.getItem();
		if (stack.isEmpty() || !stack.is(ModItems.BITCOIN)) {
			return;
		}

		TrojanChickenAccess access = (TrojanChickenAccess) chicken;
		if (access.lg2$isTrojanRooster()) {
			return;
		}

		int currentBitcoins = access.lg2$getStoredBitcoins();
		int missingBitcoins = Math.max(0, TROJAN_THRESHOLD - currentBitcoins);
		if (missingBitcoins <= 0) {
			transformChicken(level, chicken);
			return;
		}

		int consumedBitcoins = Math.min(missingBitcoins, stack.getCount());
		int updatedBitcoins = currentBitcoins + consumedBitcoins;
		access.lg2$setStoredBitcoins(updatedBitcoins);

		boolean transformed = updatedBitcoins >= TROJAN_THRESHOLD;
		if (transformed) {
			access.lg2$setTrojanRooster(true);
			TRACKED_TROJAN_CHICKENS.add(chicken);
		}

		if (consumedBitcoins >= stack.getCount()) {
			itemEntity.discard();
		} else {
			stack.shrink(consumedBitcoins);
			itemEntity.setItem(stack);
		}

		spawnFeedFeedback(level, chicken, transformed);

		if (transformed) {
			transformChicken(level, chicken);
		}
	}

	private static void transformChicken(ServerLevel level, Chicken chicken) {
		TrojanChickenAccess access = (TrojanChickenAccess) chicken;
		access.lg2$setTrojanRooster(true);
		TRACKED_TROJAN_CHICKENS.add(chicken);
		prepareTrojanChicken(chicken);
		ACTIVE_STATES.put(chicken.getUUID(), createAwakeningState(level, chicken));
	}

	private static void prepareTrojanChicken(Chicken chicken) {
		if (!chicken.isInvulnerable()) {
			chicken.setInvulnerable(true);
		}
		if (!chicken.isPersistenceRequired()) {
			chicken.setPersistenceRequired();
		}
		if (chicken.getAttribute(Attributes.MOVEMENT_SPEED) != null
				&& chicken.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() != CHASE_SPEED_PER_TICK) {
			chicken.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(CHASE_SPEED_PER_TICK);
		}
	}

	private static TrojanRoosterState createAwakeningState(ServerLevel level, Chicken chicken) {
		ServerPlayer nearest = findNearestPlayer(level, chicken.position());
		long now = level.getGameTime();
		return new TrojanRoosterState(
				nearest == null ? null : nearest.getUUID(),
				TrojanPhase.AWAKENING,
				now,
				now + AWAKENING_TICKS
		);
	}

	private static void tickTrojanState(ServerLevel level, Chicken chicken, TrojanRoosterState state) {
		chicken.setNoAi(state.phase != TrojanPhase.CHASING);
		switch (state.phase) {
			case AWAKENING -> tickAwakening(level, chicken, state);
			case AMBUSH_IDLE -> tickAmbushIdle(level, chicken, state);
			case AMBUSH_SEEN_DELAY -> tickAmbushSeenDelay(level, chicken, state);
			case CHASING -> tickChasing(level, chicken, state);
		}
	}

	private static void tickAwakening(ServerLevel level, Chicken chicken, TrojanRoosterState state) {
		long now = level.getGameTime();
		ServerPlayer nearest = resolvePlayer(level, state.targetPlayerId);
		if (nearest == null) {
			nearest = findNearestPlayer(level, chicken.position());
			state.targetPlayerId = nearest == null ? null : nearest.getUUID();
		}

		chicken.setDeltaMovement(Vec3.ZERO);
		if (nearest != null) {
			faceChickenTowards(chicken, nearest.getX(), nearest.getY() + nearest.getBbHeight() * 0.5D, nearest.getZ());
			float nodPitch = 10.0F + (float) (Math.sin((now - state.phaseStartTick) * 0.9D) * 18.0D);
			chicken.setXRot(nodPitch);
		}

		if (now < state.phaseEndTick) {
			return;
		}

		ServerPlayer ambushTarget = pickRandomPlayer(level);
		if (ambushTarget == null) {
			vanish(level, chicken, state);
			return;
		}

		spawnCloud(level, chicken.position());
		teleportBehindTarget(chicken, ambushTarget);
		spawnCloud(level, chicken.position());
		playTheme(ambushTarget, chicken.position());

		state.targetPlayerId = ambushTarget.getUUID();
		state.themePlaying = true;
		state.phase = TrojanPhase.AMBUSH_IDLE;
		state.phaseStartTick = now;
		state.phaseEndTick = now + AMBUSH_TIMEOUT_TICKS;
		state.despawnTick = now + AMBUSH_TIMEOUT_TICKS;

		chicken.setDeltaMovement(Vec3.ZERO);
		chicken.setXRot(0.0F);
		chicken.setYRot(ambushTarget.getYRot());
		chicken.setYHeadRot(ambushTarget.getYRot());
		chicken.setYBodyRot(ambushTarget.getYRot());
	}

	private static void tickAmbushIdle(ServerLevel level, Chicken chicken, TrojanRoosterState state) {
		long now = level.getGameTime();
		ServerPlayer target = resolvePlayer(level, state.targetPlayerId);
		if (target == null) {
			vanish(level, chicken, state);
			return;
		}

		if (now >= state.despawnTick) {
			vanish(level, chicken, state);
			return;
		}

		chicken.setDeltaMovement(Vec3.ZERO);
		chicken.setXRot(0.0F);
		chicken.setYRot(target.getYRot());
		chicken.setYHeadRot(target.getYRot());
		chicken.setYBodyRot(target.getYRot());

		if (isTargetLookingAtChicken(target, chicken)) {
			state.phase = TrojanPhase.AMBUSH_SEEN_DELAY;
			state.phaseStartTick = now;
			state.phaseEndTick = now + AMBUSH_REVEAL_TICKS;
			return;
		}

	}

	private static void tickAmbushSeenDelay(ServerLevel level, Chicken chicken, TrojanRoosterState state) {
		long now = level.getGameTime();
		ServerPlayer target = resolvePlayer(level, state.targetPlayerId);
		if (target == null) {
			vanish(level, chicken, state);
			return;
		}

		if (now >= state.despawnTick) {
			vanish(level, chicken, state);
			return;
		}

		chicken.setDeltaMovement(Vec3.ZERO);
		faceChickenTowards(chicken, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
		chicken.setXRot(0.0F);

		if (now >= state.phaseEndTick) {
			state.phase = TrojanPhase.CHASING;
			state.phaseStartTick = now;
			state.phaseEndTick = Long.MAX_VALUE;
		}
	}

	private static void tickChasing(ServerLevel level, Chicken chicken, TrojanRoosterState state) {
		long now = level.getGameTime();
		ServerPlayer target = resolvePlayer(level, state.targetPlayerId);
		if (target == null) {
			vanish(level, chicken, state);
			return;
		}

		if (now >= state.despawnTick) {
			vanish(level, chicken, state);
			return;
		}

		if (chicken.getBoundingBox().inflate(0.02D).intersects(target.getBoundingBox())) {
			banTarget(level, target, state);
			vanish(level, chicken, state);
			return;
		}

		double targetX = target.getX();
		double targetY = target.getY() + target.getBbHeight() * 0.3D;
		double targetZ = target.getZ();
		faceChickenTowards(chicken, targetX, targetY, targetZ);
		chicken.setXRot(0.0F);
		if (now >= state.nextNavigationRetargetTick || hasTargetMovedEnough(targetX, targetY, targetZ, state)) {
			chicken.getNavigation().moveTo(target, 1.0D);
			state.lastTargetX = targetX;
			state.lastTargetY = targetY;
			state.lastTargetZ = targetZ;
			state.nextNavigationRetargetTick = now + 3L;
		}
		chicken.fallDistance = 0.0F;
	}

	private static void vanish(ServerLevel level, Chicken chicken, TrojanRoosterState state) {
		stopTheme(state, level);
		ACTIVE_STATES.remove(chicken.getUUID());
		TRACKED_TROJAN_CHICKENS.remove(chicken);
		spawnCloud(level, chicken.position());
		chicken.discard();
	}

	private static void spawnFeedFeedback(ServerLevel level, Chicken chicken, boolean transformed) {
		double x = chicken.getX();
		double y = chicken.getY() + chicken.getBbHeight() * 0.7D;
		double z = chicken.getZ();
		level.sendParticles(FEED_PARTICLE, x + 0.1D, y, z + 0.1D, transformed ? 12 : 10, 0.1D, 0.0D, 0.1D, 0.0D);
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6F, transformed ? 0.8F : 1.2F);
	}

	private static void spawnCloud(ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.3D, pos.z, 18, 0.25D, 0.2D, 0.25D, 0.01D);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHICKEN_HURT, SoundSource.HOSTILE, 0.6F, 0.6F);
	}

	private static void teleportBehindTarget(Chicken chicken, ServerPlayer target) {
		Vec3 look = target.getLookAngle();
		Vec3 backward = new Vec3(-look.x, 0.0D, -look.z);
		if (backward.lengthSqr() < 1.0E-6D) {
			backward = new Vec3(0.0D, 0.0D, 1.0D);
		}

		Vec3 offset = backward.normalize().scale(AMBUSH_BEHIND_DISTANCE);
		Vec3 desiredPos = new Vec3(target.getX() + offset.x, target.getY(), target.getZ() + offset.z);
		Vec3 resolvedPos = findNearestReachableSpawnPos((ServerLevel) target.level(), chicken, desiredPos, target.position());
		chicken.teleportTo(resolvedPos.x, resolvedPos.y, resolvedPos.z);
	}

	private static Vec3 findNearestReachableSpawnPos(ServerLevel level, Chicken chicken, Vec3 desiredPos, Vec3 playerPos) {
		Vec3 towardPlayer = playerPos.subtract(desiredPos);
		if (towardPlayer.lengthSqr() < 1.0E-6D) {
			towardPlayer = new Vec3(0.0D, 0.0D, 1.0D);
		}

		Vec3 horizontalTowardPlayer = new Vec3(towardPlayer.x, 0.0D, towardPlayer.z);
		if (horizontalTowardPlayer.lengthSqr() < 1.0E-6D) {
			horizontalTowardPlayer = new Vec3(0.0D, 0.0D, 1.0D);
		}
		horizontalTowardPlayer = horizontalTowardPlayer.normalize();

		for (int step = 0; step <= AMBUSH_SPAWN_STEPS; step++) {
			double distance = (AMBUSH_BEHIND_DISTANCE / AMBUSH_SPAWN_STEPS) * step;
			Vec3 candidate = desiredPos.add(horizontalTowardPlayer.scale(distance));
			Vec3 grounded = snapToSpawnableGround(level, chicken, candidate);
			if (grounded != null) {
				return grounded;
			}
		}

		Vec3 fallback = snapToSpawnableGround(level, chicken, playerPos);
		return fallback != null ? fallback : desiredPos;
	}

	private static Vec3 snapToSpawnableGround(ServerLevel level, Chicken chicken, Vec3 candidate) {
		BlockPos basePos = BlockPos.containing(candidate.x, candidate.y, candidate.z);
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos feetPos = basePos.offset(0, dy, 0);
			BlockPos belowPos = feetPos.below();
			if (!level.getBlockState(belowPos).entityCanStandOn(level, belowPos, chicken)) {
				continue;
			}
			if (!level.getBlockState(feetPos).canBeReplaced() || !level.getBlockState(feetPos.above()).canBeReplaced()) {
				continue;
			}

			AABB movedBox = chicken.getBoundingBox().move(
					(feetPos.getX() + 0.5D) - chicken.getX(),
					feetPos.getY() - chicken.getY(),
					(feetPos.getZ() + 0.5D) - chicken.getZ()
			);
			if (level.noCollision(chicken, movedBox) && !level.containsAnyLiquid(movedBox)) {
				return new Vec3(feetPos.getX() + 0.5D, feetPos.getY(), feetPos.getZ() + 0.5D);
			}
		}
		return null;
	}

	private static void faceChickenTowards(Chicken chicken, double targetX, double targetY, double targetZ) {
		double dx = targetX - chicken.getX();
		double dz = targetZ - chicken.getZ();
		float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
		chicken.setYRot(yaw);
		chicken.setYHeadRot(yaw);
		chicken.setYBodyRot(yaw);
	}

	private static boolean isTargetLookingAtChicken(ServerPlayer target, Chicken chicken) {
		if (target.level() != chicken.level() || !target.isAlive() || target.isSpectator()) {
			return false;
		}

		Vec3 eyePos = target.getEyePosition();
		double toChickenX = chicken.getX() - eyePos.x;
		double toChickenY = (chicken.getY() + chicken.getBbHeight() * 0.7D) - eyePos.y;
		double toChickenZ = chicken.getZ() - eyePos.z;
		double toChickenLengthSqr = toChickenX * toChickenX + toChickenY * toChickenY + toChickenZ * toChickenZ;
		if (toChickenLengthSqr < 1.0E-6D) {
			return true;
		}

		double inverseLength = Mth.invSqrt(toChickenLengthSqr);
		Vec3 viewVector = target.getViewVector(1.0F);
		double dot = viewVector.x * (toChickenX * inverseLength)
				+ viewVector.y * (toChickenY * inverseLength)
				+ viewVector.z * (toChickenZ * inverseLength);
		return dot >= LOOK_DOT_THRESHOLD && target.hasLineOfSight(chicken);
	}

	private static boolean hasTargetMovedEnough(double targetX, double targetY, double targetZ, TrojanRoosterState state) {
		double dx = targetX - state.lastTargetX;
		double dy = targetY - state.lastTargetY;
		double dz = targetZ - state.lastTargetZ;
		return dx * dx + dy * dy + dz * dz >= 0.25D;
	}

	private static ServerPlayer findNearestPlayer(ServerLevel level, Vec3 origin) {
		ServerPlayer best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (!isEligiblePlayer(player)) {
				continue;
			}

			double distance = player.position().distanceToSqr(origin);
			if (distance < bestDistance) {
				best = player;
				bestDistance = distance;
			}
		}
		return best;
	}

	private static ServerPlayer pickRandomPlayer(ServerLevel level) {
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (isEligiblePlayer(player)) {
				players.add(player);
			}
		}

		if (players.isEmpty()) {
			return null;
		}
		return players.get(level.random.nextInt(players.size()));
	}

	private static ServerPlayer resolvePlayer(ServerLevel level, UUID playerId) {
		if (playerId == null) {
			return null;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
		return player != null && player.level() == level && isEligiblePlayer(player) ? player : null;
	}

	private static boolean isEligiblePlayer(ServerPlayer player) {
		return player != null && player.isAlive() && !player.isSpectator();
	}

	private static void playTheme(ServerPlayer player, Vec3 pos) {
		stopThemeForPlayer(player);
		if (PolymerResourcePackUtils.hasMainPack(player)) {
			sendSoundToPlayer(player, TROJAN_THEME_SOUND, SoundSource.RECORDS, pos.x, pos.y, pos.z, 0.8F, 1.0F, player.getUUID().getLeastSignificantBits());
			return;
		}

		sendSoundToPlayer(player, SoundEvents.MUSIC_DISC_LAVA_CHICKEN, SoundSource.RECORDS, pos.x, pos.y, pos.z, 1.0F, 1.0F, player.getUUID().getLeastSignificantBits());
	}

	private static void stopTheme(TrojanRoosterState state, ServerLevel level) {
		if (state == null || !state.themePlaying || level == null || state.targetPlayerId == null) {
			return;
		}

		ServerPlayer player = resolvePlayer(level, state.targetPlayerId);
		if (player != null) {
			stopThemeForPlayer(player);
		}
		state.themePlaying = false;
	}

	private static void banTarget(ServerLevel level, ServerPlayer target, TrojanRoosterState state) {
		int minutes = 3 + level.random.nextInt(3);
		Date expiresAt = new Date(System.currentTimeMillis() + (minutes * 60L * 1000L));
		UserBanListEntry entry = new UserBanListEntry(
				new NameAndId(target.getGameProfile()),
				new Date(),
				"LG2 Trojan Rooster",
				expiresAt,
				BAN_REASON
		);
		level.getServer().getPlayerList().getBans().add(entry);
		stopThemeForPlayer(target);
		target.connection.disconnect(Component.literal(BAN_REASON));
		state.themePlaying = false;
	}

	private static void stopThemeForPlayer(ServerPlayer player) {
		player.connection.send(new ClientboundStopSoundPacket(TROJAN_THEME_SOUND_ID, SoundSource.RECORDS));
		if (LAVA_CHICKEN_SOUND_ID != null) {
			player.connection.send(new ClientboundStopSoundPacket(LAVA_CHICKEN_SOUND_ID, SoundSource.RECORDS));
		}
	}

	private static void sendSoundToPlayer(
			ServerPlayer player,
			Holder<SoundEvent> sound,
			SoundSource source,
			double x,
			double y,
			double z,
			float volume,
			float pitch,
			long seed
	) {
		player.connection.send(new ClientboundSoundPacket(sound, source, x, y, z, volume, pitch, seed));
	}

	private static boolean isBitcoinItem(ItemEntity itemEntity) {
		ItemStack stack = itemEntity.getItem();
		return !stack.isEmpty() && stack.is(ModItems.BITCOIN);
	}

	private static SimpleParticleType resolveFeedParticle() {
		ParticleType<?> byId = BuiltInRegistries.PARTICLE_TYPE.getValue(
				Identifier.fromNamespaceAndPath("minecraft", "trial_spawner_detection")
		);
		if (byId instanceof SimpleParticleType simpleParticleType) {
			return simpleParticleType;
		}
		return ParticleTypes.TRIAL_SPAWNER_DETECTED_PLAYER;
	}

	private enum TrojanPhase {
		AWAKENING,
		AMBUSH_IDLE,
		AMBUSH_SEEN_DELAY,
		CHASING
	}

	private static final class TrojanRoosterState {
		private UUID targetPlayerId;
		private TrojanPhase phase;
		private long phaseStartTick;
		private long phaseEndTick;
		private long despawnTick = Long.MAX_VALUE;
		private boolean themePlaying;
		private long nextNavigationRetargetTick;
		private double lastTargetX = Double.NaN;
		private double lastTargetY = Double.NaN;
		private double lastTargetZ = Double.NaN;

		private TrojanRoosterState(UUID targetPlayerId, TrojanPhase phase, long phaseStartTick, long phaseEndTick) {
			this.targetPlayerId = targetPlayerId;
			this.phase = phase;
			this.phaseStartTick = phaseStartTick;
			this.phaseEndTick = phaseEndTick;
		}
	}
}
