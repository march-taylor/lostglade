package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig.PlayerRaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilityConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import com.mojang.authlib.properties.Property;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import it.unimi.dsi.fastutil.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.mineskin.MineskinService;
import net.lionarius.skinrestorer.skin.SkinService;
import net.lionarius.skinrestorer.skin.SkinStorage;
import net.lionarius.skinrestorer.skin.SkinValue;
import net.lionarius.skinrestorer.skin.SkinVariant;
import net.lionarius.skinrestorer.skin.provider.SkinProvider;
import net.lionarius.skinrestorer.skin.provider.SkinProviderContext;
import net.lionarius.skinrestorer.util.PlayerUtils;
import net.lionarius.skinrestorer.util.Result;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class CopperManRepulsorSystem {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static final String COPPER_MAN_RACE_ID = "copper_man";
	private static final int DEFAULT_MAX_CHARGES = 25;
	private static final int DEFAULT_COPPER_INGOT_RESTORE = 5;
	private static final double DEFAULT_NATURAL_LIGHTNING_RESTORE_CHANCE = 0.10D;
	private static final int DEFAULT_NATURAL_LIGHTNING_RESTORE = 10;
	private static final long AUTO_SHOT_INTERVAL_TICKS = 2L;
	private static final long SINGLE_SHOT_INTERVAL_TICKS = 30L;
	private static final long AUTO_INPUT_GRACE_TICKS = 4L;
	private static final long HUD_UPDATE_INTERVAL_TICKS = 5L;
	private static final long NATURAL_LIGHTNING_RECHARGE_DEDUP_TICKS = 200L;
	private static final double AUTO_RANGE = 10.0D;
	private static final double SINGLE_RANGE = 25.0D;
	private static final double AIR_TRIGGER_RAY_RANGE = 4.5D;
	private static final double AIR_TRIGGER_SPAWN_DISTANCE = 2.0D;
	private static final double AIR_TRIGGER_MOTION_LEAD_SCALE = 0.9D;
	private static final float AIR_TRIGGER_WIDTH = 1.2F;
	private static final float AIR_TRIGGER_HEIGHT = 1.35F;
	private static final float AUTO_DAMAGE = 1.0F;
	private static final float SINGLE_DAMAGE = 4.0F;
	private static final int LASER_PARTICLE_COLOR = 0xFF2A2A;
	private static final float LASER_PARTICLE_SCALE = 0.75F;
	private static final String REPULSOR_SHIFT_GLYPH = "\uef80";
	private static final FontDescription REPULSOR_SHIFT_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:repulsor_ammo_shift"))
	);
	private static final FontDescription REPULSOR_AMMO_FONT = new FontDescription.Resource(
			Objects.requireNonNull(Identifier.tryParse("lg2:repulsor_ammo_small"))
	);
	private static final Map<UUID, RepulsorState> STATES = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> NEXT_MODE_SWITCH_TICKS = new ConcurrentHashMap<>();
	private static final Map<String, Long> PROCESSED_NATURAL_LIGHTNING_HITS = new ConcurrentHashMap<>();

	private CopperManRepulsorSystem() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
			RepulsorState state = state(handler.player);
			state.hudDirty = true;
			updateHud(handler.player, state, true);
		}));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			RepulsorState state = STATES.remove(handler.player.getUUID());
			if (state != null) {
				removeAirTriggerEntity(state);
				clearHud(handler.player, state, true);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CopperManRepulsorSystem::tickServer);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			STATES.values().forEach(CopperManRepulsorSystem::removeAirTriggerEntity);
			STATES.clear();
			NEXT_MODE_SWITCH_TICKS.clear();
			PROCESSED_NATURAL_LIGHTNING_HITS.clear();
		});
	}

	public static int toggleMode(ServerPlayer player) {
		if (player == null) {
			return 0;
		}

		long nowTick = player.level().getGameTime();
		long cooldownTicks = getModeSwitchCooldownTicks(player);
		long nextAllowedTick = NEXT_MODE_SWITCH_TICKS.getOrDefault(player.getUUID(), 0L);
		if (cooldownTicks > 0L && nowTick < nextAllowedTick) {
			double remaining = (nextAllowedTick - nowTick) / 20.0D;
			player.displayClientMessage(
					Component.literal(String.format(Locale.ROOT, "%.1fs", remaining))
							.withStyle(style -> style.withColor(ChatFormatting.RED).withItalic(false)),
					true
			);
			return 0;
		}

		RepulsorState state = state(player);
		state.mode = state.mode == RepulsorMode.AUTOMATIC ? RepulsorMode.SINGLE : RepulsorMode.AUTOMATIC;
		state.hudDirty = true;
		if (cooldownTicks > 0L) {
			NEXT_MODE_SWITCH_TICKS.put(player.getUUID(), nowTick + cooldownTicks);
		}
		player.displayClientMessage(
				Component.literal(localizeModeChanged(player, state.mode))
						.withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false)),
				true
		);
		return 1;
	}

	public static boolean handleUseInteraction(ServerPlayer player, InteractionHand hand) {
		if (!canUseRepulsor(player, hand)) {
			return false;
		}

		RepulsorState state = state(player);
		long nowTick = player.level().getGameTime();
		state.hudDirty = true;
		if (state.mode == RepulsorMode.AUTOMATIC) {
			state.lastAutomaticInputTick = nowTick;
			if (nowTick >= state.nextShotTick) {
				tryFire(player, state, nowTick);
			}
			return true;
		}

		if (state.lastSingleInputTick == nowTick) {
			return true;
		}
		state.lastSingleInputTick = nowTick;
		tryFire(player, state, nowTick);
		return true;
	}

	public static void onCopperIngotConsumed(ServerPlayer player) {
		if (player == null || !isCopperMan(player)) {
			return;
		}
		restoreCharges(player, state(player), getCopperIngotChargeRestore(player));
	}

	public static void onNaturalLightningStrike(ServerPlayer player, Entity lightningEntity) {
		if (player == null || lightningEntity == null || !isCopperMan(player)) {
			return;
		}
		if (!(lightningEntity instanceof net.minecraft.world.entity.LightningBolt lightningBolt) || lightningBolt.getCause() != null) {
			return;
		}
		long nowTick = player.level().getGameTime();
		String dedupKey = player.getUUID() + ":" + lightningBolt.getUUID();
		Long processedUntil = PROCESSED_NATURAL_LIGHTNING_HITS.get(dedupKey);
		if (processedUntil != null && processedUntil >= nowTick) {
			return;
		}
		PROCESSED_NATURAL_LIGHTNING_HITS.put(dedupKey, nowTick + NATURAL_LIGHTNING_RECHARGE_DEDUP_TICKS);
		if (player.getRandom().nextDouble() > getNaturalLightningChargeChance(player)) {
			return;
		}
		restoreCharges(player, state(player), getNaturalLightningChargeRestore(player));
	}

	private static void tickServer(MinecraftServer server) {
		long nowTick = server.overworld().getGameTime();
		if (nowTick % 40L == 0L) {
			PROCESSED_NATURAL_LIGHTNING_HITS.entrySet().removeIf(entry -> entry.getValue() < nowTick);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RepulsorState state = state(player);
			syncAirTriggerEntity(player, state);
			if (state.mode == RepulsorMode.AUTOMATIC
					&& canUseRepulsor(player, InteractionHand.MAIN_HAND)
					&& state.lastAutomaticInputTick + AUTO_INPUT_GRACE_TICKS >= nowTick
					&& nowTick >= state.nextShotTick) {
				tryFire(player, state, nowTick);
			}
			boolean hudHeartbeat = (nowTick + player.getId()) % HUD_UPDATE_INTERVAL_TICKS == 0L;
			if (state.hudDirty || !state.hudVisible || hudHeartbeat) {
				updateHud(player, state, hudHeartbeat);
			}
		}
	}

	private static RepulsorState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), ignored -> new RepulsorState());
	}

	private static boolean canUseRepulsor(ServerPlayer player, InteractionHand hand) {
		return player != null
				&& player.isAlive()
				&& !player.isSpectator()
				&& isCopperMan(player)
				&& isAttackUnlocked(player)
				&& player.getInventory().getSelectedSlot() == 0
				&& player.getMainHandItem().isEmpty();
	}

	private static boolean tryFire(ServerPlayer player, RepulsorState state, long nowTick) {
		if (state.charges <= 0 || nowTick < state.nextShotTick) {
			state.hudDirty = true;
			return false;
		}

		fireLaser(player, state.mode);
		state.charges = Math.max(0, state.charges - 1);
		state.hudDirty = true;
		state.nextShotTick = nowTick + state.mode.intervalTicks;
		return true;
	}

	private static void fireLaser(ServerPlayer player, RepulsorMode mode) {
		ServerLevel level = player.level();
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(mode.range));
		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 blockEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				level,
				player,
				start,
				blockEnd,
				player.getBoundingBox().expandTowards(player.getLookAngle().scale(mode.range)).inflate(0.75D),
				entity -> canLaserHit(player, entity),
				0.25F
		);

		Vec3 particleEnd = blockEnd;
		if (entityHit != null && entityHit.getEntity() != null) {
			particleEnd = entityHit.getLocation();
			damageEntity(level, player, entityHit.getEntity(), mode.damage);
		}

		spawnLaserParticles(level, start, particleEnd);
	}

	private static boolean canLaserHit(ServerPlayer player, Entity entity) {
		if (entity == null || entity == player || !entity.isAlive() || !entity.isPickable() || entity instanceof Interaction) {
			return false;
		}
		var playerTeam = player.getTeam();
		var entityTeam = entity.getTeam();
		if (entity instanceof ServerPlayer otherPlayer) {
			if (player.isAlliedTo(otherPlayer)) {
				return false;
			}
		}
		return playerTeam == null || entityTeam == null || !Objects.equals(entityTeam, playerTeam);
	}

	private static void damageEntity(ServerLevel level, ServerPlayer player, Entity entity, float damage) {
		Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
		arrow.setPos(player.getEyePosition());
		entity.hurtServer(level, level.damageSources().arrow(arrow, player), damage);
		arrow.discard();
	}

	private static void spawnLaserParticles(ServerLevel level, Vec3 start, Vec3 end) {
		Vec3 delta = end.subtract(start);
		double length = delta.length();
		if (length <= 1.0E-6D) {
			level.sendParticles(new DustParticleOptions(LASER_PARTICLE_COLOR, LASER_PARTICLE_SCALE), start.x, start.y, start.z, 1, 0.0, 0.0, 0.0, 0.0);
			return;
		}
		int particles = Math.max(1, (int) Math.ceil(length * 3.0D));
		Vec3 step = delta.scale(1.0D / particles);
		Vec3 current = start;
		for (int i = 0; i <= particles; i++) {
			level.sendParticles(new DustParticleOptions(LASER_PARTICLE_COLOR, LASER_PARTICLE_SCALE), current.x, current.y, current.z, 1, 0.0, 0.0, 0.0, 0.0);
			current = current.add(step);
		}
	}

	private static void restoreCharges(ServerPlayer player, RepulsorState state, int amount) {
		if (player == null || state == null || amount <= 0) {
			return;
		}
		int maxCharges = getMaxCharges(player);
		int restored = Math.min(maxCharges, Math.max(0, state.charges) + amount);
		if (restored != state.charges) {
			state.charges = restored;
			state.hudDirty = true;
		}
	}

	private static void updateHud(ServerPlayer player, RepulsorState state, boolean force) {
		if (!shouldShowHud(player)) {
			clearHud(player, state, force);
			return;
		}

		String hudText = Math.max(0, state.charges) + "/" + getMaxCharges(player);
		boolean hasPack = PolymerResourcePackUtils.hasMainPack(player);
		if (!force && !state.hudDirty && state.hudVisible && Objects.equals(state.lastHudText, hudText) && state.lastHudPack == hasPack) {
			return;
		}

		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 0));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
		Component subtitle = hasPack
				? buildPackAmmoComponent(hudText)
				: Component.literal(hudText).withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		state.hudVisible = true;
		state.lastHudText = hudText;
		state.lastHudPack = hasPack;
		state.hudDirty = false;
	}

	private static Component buildPackAmmoComponent(String text) {
		return Component.empty()
				.append(Component.literal(REPULSOR_SHIFT_GLYPH)
						.withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false).withFont(REPULSOR_SHIFT_FONT)))
				.append(Component.literal(text)
						.withStyle(style -> style.withColor(ChatFormatting.WHITE).withItalic(false).withFont(REPULSOR_AMMO_FONT)));
	}

	private static void clearHud(ServerPlayer player, RepulsorState state, boolean force) {
		if (!force && !state.hudVisible) {
			return;
		}
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
		state.hudVisible = false;
		state.lastHudText = "";
		state.hudDirty = false;
	}

	private static boolean shouldShowHud(ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isSpectator() || !isAttackUnlocked(player)) {
			return false;
		}

		ItemStack mainHand = player.getMainHandItem();
		ItemStack selected = player.getInventory().getItem(player.getInventory().getSelectedSlot());
		ItemStack using = player.getUseItem();
		if (mainHand.is(Items.COPPER_INGOT) || selected.is(Items.COPPER_INGOT) || using.is(Items.COPPER_INGOT)) {
			return true;
		}

		return player.getInventory().getSelectedSlot() == 0 && (mainHand.isEmpty() || selected.isEmpty());
	}

	private static void syncAirTriggerEntity(ServerPlayer player, RepulsorState state) {
		if (!shouldMaintainAirTrigger(player)) {
			removeAirTriggerEntity(state);
			return;
		}
		if (hasAirTriggerObstruction(player, state)) {
			removeAirTriggerEntity(state);
			return;
		}

		Interaction trigger = state.airTriggerEntity;
		if (trigger == null || !trigger.isAlive() || trigger.level() != player.level()) {
			trigger = new Interaction(net.minecraft.world.entity.EntityType.INTERACTION, player.level());
			trigger.setNoGravity(true);
			trigger.setSilent(true);
			trigger.setResponse(false);
			trigger.setWidth(AIR_TRIGGER_WIDTH);
			trigger.setHeight(AIR_TRIGGER_HEIGHT);
			player.level().addFreshEntity(trigger);
			state.airTriggerEntity = trigger;
		}

		Vec3 look = player.getLookAngle().normalize();
		double forwardSpeed = Math.max(0.0D, player.getDeltaMovement().dot(look));
		Vec3 offset = look.scale(AIR_TRIGGER_SPAWN_DISTANCE + (forwardSpeed * AIR_TRIGGER_MOTION_LEAD_SCALE));
		Vec3 pos = player.getEyePosition().add(offset).subtract(0.0D, AIR_TRIGGER_HEIGHT * 0.5D, 0.0D);
		trigger.setPos(pos.x, pos.y, pos.z);
		trigger.setDeltaMovement(Vec3.ZERO);
		trigger.setYRot(player.getYRot());
		trigger.setXRot(player.getXRot());
	}

	private static boolean shouldMaintainAirTrigger(ServerPlayer player) {
		return player != null
				&& player.isAlive()
				&& !player.isSpectator()
				&& isAttackUnlocked(player)
				&& player.getInventory().getSelectedSlot() == 0
				&& player.getMainHandItem().isEmpty();
	}

	private static boolean hasAirTriggerObstruction(ServerPlayer player, RepulsorState state) {
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(player.getLookAngle().scale(AIR_TRIGGER_RAY_RANGE));
		BlockHitResult blockHit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double maxDistance = blockHit.getType() == HitResult.Type.MISS ? AIR_TRIGGER_RAY_RANGE : Math.sqrt(blockHit.getLocation().distanceToSqr(start));
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				player.level(),
				player,
				start,
				start.add(player.getLookAngle().scale(maxDistance)),
				player.getBoundingBox().expandTowards(player.getLookAngle().scale(maxDistance)).inflate(0.6D),
				entity -> entity != state.airTriggerEntity && entity != player && entity.isAlive() && entity.isPickable(),
				0.0F
		);
		return blockHit.getType() != HitResult.Type.MISS || entityHit != null;
	}

	private static void removeAirTriggerEntity(RepulsorState state) {
		if (state.airTriggerEntity != null) {
			state.airTriggerEntity.discard();
			state.airTriggerEntity = null;
		}
	}

	private static String localizeModeChanged(ServerPlayer player, RepulsorMode mode) {
		return switch (locale(player)) {
			case "rpr" -> "Режимъ репульсора: " + localizeModeName(player, mode);
			case "uk", "uk_ua" -> "Режим репульсора: " + localizeModeName(player, mode);
			case "ja", "ja_jp" -> "リパルサー: " + localizeModeName(player, mode);
			case "ru", "ru_ru" -> "Режим репульсора: " + localizeModeName(player, mode);
			default -> "Repulsor mode: " + localizeModeName(player, mode);
		};
	}

	private static String localizeModeName(ServerPlayer player, RepulsorMode mode) {
		return switch (locale(player)) {
			case "rpr" -> mode == RepulsorMode.AUTOMATIC ? "Самострѣльный" : "Одиночный";
			case "uk", "uk_ua" -> mode == RepulsorMode.AUTOMATIC ? "Автоматичний" : "Одиночний";
			case "ja", "ja_jp" -> mode == RepulsorMode.AUTOMATIC ? "オート" : "単発";
			case "ru", "ru_ru" -> mode == RepulsorMode.AUTOMATIC ? "Автоматический" : "Одиночный";
			default -> mode == RepulsorMode.AUTOMATIC ? "Automatic" : "Single";
		};
	}

	private static String locale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "en_us";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	private static boolean isCopperMan(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
		return raceOptional.isPresent() && COPPER_MAN_RACE_ID.equals(sanitizePath(raceOptional.get().id));
	}

	private static boolean isAttackUnlocked(ServerPlayer player) {
		return player != null && ServerRaceSystem.hasUnlockedAbility(player, RaceAbilitySlot.ATTACK);
	}

	private static int getMaxCharges(ServerPlayer player) {
		RaceAbilityConfig ability = ServerRaceSystem.getAbility(player, RaceAbilitySlot.ATTACK).orElse(null);
		return ability != null && ability.repulsorMaxCharges > 0 ? ability.repulsorMaxCharges : DEFAULT_MAX_CHARGES;
	}

	private static int getCopperIngotChargeRestore(ServerPlayer player) {
		RaceAbilityConfig ability = ServerRaceSystem.getAbility(player, RaceAbilitySlot.ATTACK).orElse(null);
		return ability != null && ability.repulsorCopperIngotChargeRestore > 0 ? ability.repulsorCopperIngotChargeRestore : DEFAULT_COPPER_INGOT_RESTORE;
	}

	private static double getNaturalLightningChargeChance(ServerPlayer player) {
		Optional<PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
		if (raceOptional.isPresent() && raceOptional.get().stock != null && raceOptional.get().stock.repulsorNaturalLightningChargeChance > 0.0D) {
			return raceOptional.get().stock.repulsorNaturalLightningChargeChance;
		}

		RaceAbilityConfig ability = ServerRaceSystem.getAbility(player, RaceAbilitySlot.ATTACK).orElse(null);
		return ability != null && ability.repulsorNaturalLightningChargeChance > 0.0D
				? ability.repulsorNaturalLightningChargeChance
				: DEFAULT_NATURAL_LIGHTNING_RESTORE_CHANCE;
	}

	private static int getNaturalLightningChargeRestore(ServerPlayer player) {
		RaceAbilityConfig ability = ServerRaceSystem.getAbility(player, RaceAbilitySlot.ATTACK).orElse(null);
		return ability != null && ability.repulsorNaturalLightningChargeRestore > 0
				? ability.repulsorNaturalLightningChargeRestore
				: DEFAULT_NATURAL_LIGHTNING_RESTORE;
	}

	private static long getModeSwitchCooldownTicks(ServerPlayer player) {
		RaceAbilityConfig ability = ServerRaceSystem.getAbility(player, RaceAbilitySlot.ATTACK).orElse(null);
		if (ability == null || ability.cooldownSeconds <= 0.0D) {
			return 0L;
		}
		return Math.max(0L, Math.round(ability.cooldownSeconds * 20.0D));
	}

	private static String sanitizePath(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private enum RepulsorMode {
		AUTOMATIC(AUTO_RANGE, AUTO_DAMAGE, AUTO_SHOT_INTERVAL_TICKS),
		SINGLE(SINGLE_RANGE, SINGLE_DAMAGE, SINGLE_SHOT_INTERVAL_TICKS);

		private final double range;
		private final float damage;
		private final long intervalTicks;

		RepulsorMode(double range, float damage, long intervalTicks) {
			this.range = range;
			this.damage = damage;
			this.intervalTicks = intervalTicks;
		}
	}

	private static final class RepulsorState {
		private RepulsorMode mode = RepulsorMode.AUTOMATIC;
		private int charges = -1;
		private long nextShotTick = 0L;
		private long lastAutomaticInputTick = Long.MIN_VALUE;
		private long lastSingleInputTick = Long.MIN_VALUE;
		private boolean hudDirty = true;
		private boolean hudVisible = false;
		private String lastHudText = "";
		private boolean lastHudPack = false;
		private Interaction airTriggerEntity;
	}
}
