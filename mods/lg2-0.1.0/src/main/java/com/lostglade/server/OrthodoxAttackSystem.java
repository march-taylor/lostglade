package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.util.ItemDisplayHitboxHelper;
import com.mojang.math.Transformation;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Brightness;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OrthodoxAttackSystem {
	private static final double EYE_HEIGHT_OFFSET_BLOCKS = 140.0D;
	private static final float EYE_WIDTH_BLOCKS = 24.0F;
	private static final float EYE_DEPTH_BLOCKS = 12.0F;
	private static final float DISTANCE_FADE_BLOCKS = 5.0F;
	private static final float VIEW_SCALE_STEP = 0.10F;
	private static final float OPEN_STEP = 0.05F;
	private static final Identifier EYE_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "orthodox_divine_eye");
	private static final Identifier EYE_OPEN_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "orthodox_eye_open");
	private static final Identifier EYE_CLOSE_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "orthodox_eye_close");
	private static final Holder<SoundEvent> EYE_OPEN_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(EYE_OPEN_SOUND_ID));
	private static final Holder<SoundEvent> EYE_CLOSE_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(EYE_CLOSE_SOUND_ID));
	private static final Set<Relative> ABSOLUTE_TELEPORT = EnumSet.noneOf(Relative.class);
	private static final Map<UUID, DivineGazeSession> SESSIONS = new HashMap<>();

	private OrthodoxAttackSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(OrthodoxAttackSystem::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeViewer(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(OrthodoxAttackSystem::clearAll);
	}

	public static boolean isObserving(UUID casterId) {
		DivineGazeSession session = casterId == null ? null : SESSIONS.get(casterId);
		return session != null && !session.terminating;
	}

	public static boolean activate(
			ServerPlayer caster,
			ServerPlayer target,
			long durationTicks,
			double visibilityRadius,
			double remainingHealthHearts,
			long blindnessTicks
	) {
		if (caster == null || target == null || caster == target || durationTicks <= 0L || visibilityRadius <= 0.0D) {
			return false;
		}
		if (!isGazeDimension(target.level())) return false;
		DivineGazeSession old = SESSIONS.remove(caster.getUUID());
		if (old != null) {
			MinecraftServer server = caster.level().getServer();
			ServerPlayer oldTarget = server == null ? null : server.getPlayerList().getPlayer(old.targetId);
			if (old.soundOpen) playEyeTransitionSound(oldTarget, false);
			clearViews(old);
		}
		DivineGazeSession session = new DivineGazeSession(
				caster.getUUID(),
				target.getUUID(),
				durationTicks,
				visibilityRadius,
				Math.max(0.0D, remainingHealthHearts) * 2.0D,
				Math.max(1L, blindnessTicks),
				target.position(),
				calculateEyeY(target)
		);
		session.soundOpen = true;
		SESSIONS.put(caster.getUUID(), session);
		ServerLevel level = target.level();
		playEyeTransitionSound(target, true);
		level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 28, 0.5D, 0.8D, 0.5D, 0.025D);
		return true;
	}

	public static void onSuccessfulDamage(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
		if (!isGazeDimension(level) || victim == null || source == null || amount <= 0.0F || SESSIONS.isEmpty()) return;
		Entity attacker = source.getEntity();
		if (!(attacker instanceof LivingEntity) || attacker == victim) return;
		UUID victimId = victim.getUUID();
		UUID attackerId = attacker.getUUID();
		for (DivineGazeSession session : SESSIONS.values()) {
			if (session.terminating) continue;
			if (session.targetId.equals(victimId)) {
				session.attackedFirstByTarget.putIfAbsent(attackerId, false);
			} else if (session.targetId.equals(attackerId)) {
				session.attackedFirstByTarget.putIfAbsent(victimId, true);
			}
		}
	}

	public static void onLivingDeath(ServerLevel level, LivingEntity victim, DamageSource source) {
		if (!isGazeDimension(level) || victim == null || source == null || SESSIONS.isEmpty()) return;
		Entity killer = source.getEntity();
		if (!(killer instanceof ServerPlayer watchedPlayer)) return;
		List<DivineGazeSession> violations = new ArrayList<>();
		for (DivineGazeSession session : SESSIONS.values()) {
			if (session.terminating || !session.targetId.equals(watchedPlayer.getUUID())) continue;
			boolean targetAttackedFirst = session.attackedFirstByTarget.getOrDefault(victim.getUUID(), true);
			if (targetAttackedFirst) violations.add(session);
		}
		if (violations.isEmpty()) return;
		punish(level, watchedPlayer, violations.getFirst());
		for (DivineGazeSession session : violations) session.terminating = true;
	}

	private static void tick(MinecraftServer server) {
		if (server == null || SESSIONS.isEmpty()) return;
		Iterator<DivineGazeSession> iterator = SESSIONS.values().iterator();
		while (iterator.hasNext()) {
			DivineGazeSession session = iterator.next();
			ServerPlayer target = server.getPlayerList().getPlayer(session.targetId);
			boolean activeWorld = target != null && target.isAlive() && isGazeDimension(target.level());

			if (!session.terminating && activeWorld) {
				session.remainingTicks--;
				session.lastTargetPosition = target.position();
				session.lastEyeY = calculateEyeY(target);
				if (session.remainingTicks <= 0L) session.terminating = true;
			}
			float desiredOpen = !session.terminating && activeWorld ? 1.0F : 0.0F;
			boolean shouldSoundOpen = desiredOpen > 0.5F;
			if (session.soundOpen != shouldSoundOpen) {
				playEyeTransitionSound(target, shouldSoundOpen);
				session.soundOpen = shouldSoundOpen;
			}
			session.openProgress = approach(session.openProgress, desiredOpen, OPEN_STEP);
			updateViews(server, session, activeWorld ? target : null);

			if (session.terminating && session.openProgress <= 0.001F) {
				clearViews(session);
				iterator.remove();
			}
		}
	}

	private static void updateViews(MinecraftServer server, DivineGazeSession session, ServerPlayer target) {
		if (target != null) {
			for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
				if (viewer.level() != target.level()) continue;
				double dx = viewer.getX() - target.getX();
				double dz = viewer.getZ() - target.getZ();
				double distance = Math.sqrt(dx * dx + dz * dz);
				if (distance <= session.visibilityRadius || session.views.containsKey(viewer.getUUID())) {
					EyeView view = session.views.computeIfAbsent(viewer.getUUID(), id -> spawnView(viewer, target, session.lastEyeY));
					if (view != null) updateView(viewer, view, session, target.position(), proximityScale(distance, session.visibilityRadius));
				}
			}
		}

		Iterator<Map.Entry<UUID, EyeView>> views = session.views.entrySet().iterator();
		while (views.hasNext()) {
			Map.Entry<UUID, EyeView> entry = views.next();
			ServerPlayer viewer = server.getPlayerList().getPlayer(entry.getKey());
			EyeView view = entry.getValue();
			boolean validDimension = viewer != null && viewer.level().dimension().equals(view.level.dimension());
			if (!validDimension) {
				if (viewer != null) removeView(viewer, view);
				views.remove();
				continue;
			}
			if (target == null || viewer.level() != target.level()) {
				updateView(viewer, view, session, session.lastTargetPosition, 0.0F);
			}
			if (view.currentScale <= 0.001F && (target == null || horizontalDistance(viewer, target) > session.visibilityRadius)) {
				removeView(viewer, view);
				views.remove();
			}
		}
	}

	private static EyeView spawnView(ServerPlayer viewer, ServerPlayer target, double eyeY) {
		if (viewer == null || target == null || viewer.connection == null) return null;
		Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, target.level());
		display.setItemStack(createEyeStack());
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setBrightnessOverride(Brightness.FULL_BRIGHT);
		display.setWidth(EYE_WIDTH_BLOCKS + 4.0F);
		display.setHeight(4.0F);
		display.setViewRange(1_000_000.0F);
		display.setPosRotInterpolationDuration(2);
		display.setTransformationInterpolationDelay(0);
		display.setTransformationInterpolationDuration(2);
		display.setPos(target.getX(), eyeY, target.getZ());
		display.setTransformation(eyeTransformation(0.0F, 0.0F));
		ItemDisplayHitboxHelper.clear(display);
		sendSpawn(viewer, display);
		return new EyeView(display, 0.0F, target.level());
	}

	private static void updateView(ServerPlayer viewer, EyeView view, DivineGazeSession session, Vec3 targetPosition, float desiredScale) {
		if (viewer == null || view == null || targetPosition == null) return;
		view.currentScale = approach(view.currentScale, desiredScale, VIEW_SCALE_STEP);
		Display.ItemDisplay display = view.display;
		display.setPos(targetPosition.x, session.lastEyeY, targetPosition.z);
		display.setTransformation(eyeTransformation(view.currentScale, session.openProgress));
		sendFrame(viewer, display);
	}

	private static Transformation eyeTransformation(float proximity, float open) {
		float visible = Mth.clamp(proximity, 0.0F, 1.0F);
		float opening = Mth.clamp(open, 0.0F, 1.0F);
		float x = Math.max(0.001F, EYE_WIDTH_BLOCKS * visible);
		// The eye is hundreds of blocks above the viewer; exaggerating its vertical
		// depth keeps the lens, eyelids and halo visibly three-dimensional from below.
		float y = Math.max(0.001F, 3.5F * visible);
		float z = Math.max(0.001F, EYE_DEPTH_BLOCKS * visible * opening);
		return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(x, y, z), new Quaternionf());
	}

	private static double calculateEyeY(ServerPlayer target) {
		int blockX = Mth.floor(target.getX());
		int blockZ = Mth.floor(target.getZ());
		int surfaceY = target.level().getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ);
		double anchorY = target.getY() >= surfaceY - 0.01D ? target.getY() : surfaceY;
		return anchorY + EYE_HEIGHT_OFFSET_BLOCKS;
	}

	private static boolean isGazeDimension(Level level) {
		return level != null && (Level.OVERWORLD.equals(level.dimension()) || Level.END.equals(level.dimension()));
	}

	private static void playEyeTransitionSound(ServerPlayer target, boolean opening) {
		if (target == null || target.connection == null) return;
		boolean hasPack = PolymerResourcePackUtils.hasMainPack(target);
		Holder<SoundEvent> sound = hasPack
				? (opening ? EYE_OPEN_SOUND : EYE_CLOSE_SOUND)
				: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(opening ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE);
		target.connection.send(new ClientboundSoundPacket(
				sound,
				SoundSource.PLAYERS,
				target.getX(),
				target.getY(),
				target.getZ(),
				1.0F,
				1.0F,
				target.getRandom().nextLong()
		));
	}

	private static float proximityScale(double distance, double radius) {
		if (distance >= radius) return 0.0F;
		double fadeStart = Math.max(0.0D, radius - Math.min(DISTANCE_FADE_BLOCKS, radius));
		if (distance <= fadeStart) return 1.0F;
		float value = (float) ((radius - distance) / Math.max(0.001D, radius - fadeStart));
		return value * value * (3.0F - 2.0F * value);
	}

	private static double horizontalDistance(ServerPlayer viewer, ServerPlayer target) {
		double dx = viewer.getX() - target.getX();
		double dz = viewer.getZ() - target.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static void punish(ServerLevel level, ServerPlayer target, DivineGazeSession session) {
		float remainingHealth = (float) Math.max(0.1D, session.remainingHealthPoints);
		if (target.getHealth() > remainingHealth) {
			float damage = target.getHealth() - remainingHealth;
			target.hurtServer(level, level.damageSources().magic(), damage);
			if (target.isAlive() && target.getHealth() > remainingHealth) target.setHealth(remainingHealth);
		}
		int blindnessDuration = (int) Math.min(Integer.MAX_VALUE, session.blindnessTicks);
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, blindnessDuration, 1, false, true, true));
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
		level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 90, 0.8D, 1.0D, 0.8D, 0.08D);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 60, 0.65D, 0.9D, 0.65D, 0.12D);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.8F, 1.55F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2F, 1.75F);
	}

	private static ItemStack createEyeStack() {
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, EYE_MODEL_ID);
		return stack;
	}

	@SuppressWarnings("unchecked")
	private static void sendSpawn(ServerPlayer viewer, Display.ItemDisplay display) {
		ServerEntity tracker = new ServerEntity((ServerLevel) display.level(), display, 1, false, NOOP_SYNCHRONIZER);
		tracker.sendPairingData(viewer, packet -> viewer.connection.send((Packet<? super ClientGamePacketListener>) packet));
		List<SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
		if (values != null && !values.isEmpty()) viewer.connection.send(new ClientboundSetEntityDataPacket(display.getId(), values));
	}

	private static void sendFrame(ServerPlayer viewer, Display.ItemDisplay display) {
		if (viewer.connection == null) return;
		PositionMoveRotation pose = new PositionMoveRotation(display.position(), Vec3.ZERO, 0.0F, 0.0F);
		viewer.connection.send(ClientboundTeleportEntityPacket.teleport(display.getId(), pose, ABSOLUTE_TELEPORT, false));
		List<SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
		if (values != null && !values.isEmpty()) viewer.connection.send(new ClientboundSetEntityDataPacket(display.getId(), values));
	}

	private static void removeView(ServerPlayer viewer, EyeView view) {
		if (viewer != null && viewer.connection != null && view != null) {
			viewer.connection.send(new ClientboundRemoveEntitiesPacket(view.display.getId()));
		}
	}

	private static void removeViewer(UUID viewerId) {
		if (viewerId == null) return;
		for (DivineGazeSession session : SESSIONS.values()) session.views.remove(viewerId);
	}

	private static void clearViews(DivineGazeSession session) {
		if (session == null) return;
		MinecraftServer server = session.views.values().stream()
				.map(view -> view.level.getServer())
				.filter(java.util.Objects::nonNull)
				.findFirst().orElse(null);
		if (server != null) {
			for (Map.Entry<UUID, EyeView> entry : session.views.entrySet()) {
				removeView(server.getPlayerList().getPlayer(entry.getKey()), entry.getValue());
			}
		}
		session.views.clear();
	}

	private static void clearAll(MinecraftServer server) {
		for (DivineGazeSession session : SESSIONS.values()) clearViews(session);
		SESSIONS.clear();
	}

	private static float approach(float value, float target, float step) {
		if (value < target) return Math.min(target, value + step);
		return Math.max(target, value - step);
	}

	private static final class DivineGazeSession {
		private final UUID casterId;
		private final UUID targetId;
		private long remainingTicks;
		private final double visibilityRadius;
		private final double remainingHealthPoints;
		private final long blindnessTicks;
		private final Map<UUID, Boolean> attackedFirstByTarget = new HashMap<>();
		private final Map<UUID, EyeView> views = new HashMap<>();
		private Vec3 lastTargetPosition;
		private double lastEyeY;
		private float openProgress;
		private boolean soundOpen;
		private boolean terminating;

		private DivineGazeSession(UUID casterId, UUID targetId, long remainingTicks, double visibilityRadius,
				double remainingHealthPoints, long blindnessTicks, Vec3 lastTargetPosition, double lastEyeY) {
			this.casterId = casterId;
			this.targetId = targetId;
			this.remainingTicks = remainingTicks;
			this.visibilityRadius = visibilityRadius;
			this.remainingHealthPoints = remainingHealthPoints;
			this.blindnessTicks = blindnessTicks;
			this.lastTargetPosition = lastTargetPosition;
			this.lastEyeY = lastEyeY;
		}
	}

	private static final class EyeView {
		private final Display.ItemDisplay display;
		private float currentScale;
		private final ServerLevel level;

		private EyeView(Display.ItemDisplay display, float currentScale, ServerLevel level) {
			this.display = display;
			this.currentScale = currentScale;
			this.level = level;
		}
	}

	private static final ServerEntity.Synchronizer NOOP_SYNCHRONIZER = new ServerEntity.Synchronizer() {
		@Override
		public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
		}

		@Override
		public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
		}

		@Override
		public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, java.util.function.Predicate<ServerPlayer> predicate) {
		}
	};
}
