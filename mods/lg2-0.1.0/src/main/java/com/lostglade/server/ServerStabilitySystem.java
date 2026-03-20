package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerStabilitySystem {
	private static final String STABILITY_SYMBOL = "\uE903";
	private static final int TITLE_COLOR = 0xF2CD26;
	private static final int PACK_SYMBOL_COLOR = 0xFFFFFF;
	private static final int STABILITY_PARTICLE_COLOR = 0xFFFFD24D;
	private static final ColorParticleOption STABILITY_POTION_PARTICLE = ColorParticleOption.create(
			ParticleTypes.ENTITY_EFFECT,
			STABILITY_PARTICLE_COLOR
	);

	private static final Map<UUID, ServerBossEvent> PLAYER_HUDS = new HashMap<>();
	private static final Map<UUID, ServerBossEvent> PLAYER_SPACER_HUDS = new HashMap<>();
	private static final Map<String, Set<String>> TRACKED_SERVER_ANCHORS = new HashMap<>();
	private static final Set<ItemEntity> TRACKED_BITCOIN_ITEMS = ConcurrentHashMap.newKeySet();
	private static final int BITCOIN_FEED_SCAN_RADIUS = 1;
	private static final double BITCOIN_FEED_RADIUS = 0.3D;
	private static final long FEED_SOUND_BASE_DURATION_TICKS = 240L;
	private static final float FEED_MUSIC_SOUND_VOLUME = 0.55F;
	private static final float FEED_XP_SOUND_VOLUME = 1.0F;
	private static final SimpleParticleType FEED_PARTICLE = resolveFeedParticle();
	private static final Identifier FEED_MUSIC_SOUND_ID = BuiltInRegistries.SOUND_EVENT.getKey(SoundEvents.MUSIC_DISC_13.value());
	private static final Gson STABILITY_STATE_GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String STABILITY_STATE_FILE_NAME = "lg2-stability.json";

	private static int stability = 100;
	private static long decayTickCounter = 0L;
	private static double pendingStabilityFraction = 0.0D;
	private static long nextFeedSoundAllowedTick = Long.MIN_VALUE;
	private static boolean stabilityStateLoaded = false;
	private static boolean stabilityDirty = false;

	private static final class StabilityState {
		int stability;
		Map<String, Set<String>> trackedServerAnchors;
	}

	private ServerStabilitySystem() {
	}

	public static void register() {
		stabilityStateLoaded = false;
		stabilityDirty = false;
		setStability(getMaxStability());
		decayTickCounter = 0L;
		pendingStabilityFraction = 0.0D;
		nextFeedSoundAllowedTick = Long.MIN_VALUE;
		TRACKED_SERVER_ANCHORS.clear();

		ServerLifecycleEvents.SERVER_STARTED.register(ServerStabilitySystem::loadPersistedStability);
		ServerLifecycleEvents.SERVER_STOPPING.register(ServerStabilitySystem::savePersistedStability);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(
						Commands.literal("serverstability")
								.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
								.executes(context -> {
									int value = getStability();
									int max = getMaxStability();
									context.getSource().sendSuccess(
											() -> Component.literal("Server stability: " + value + "/" + max),
											false
									);
									return value;
								})
								.then(Commands.argument("value", IntegerArgumentType.integer(0))
										.executes(context -> {
											int value = IntegerArgumentType.getInteger(context, "value");
											setStability(value);
											int current = getStability();
											int max = getMaxStability();
											context.getSource().sendSuccess(
													() -> Component.literal("Set server stability to " + current + "/" + max),
													true
											);
											return 1;
										}))
				)
		);

		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().is(ModItems.BITCOIN)) {
				TRACKED_BITCOIN_ITEMS.add(itemEntity);
			}
		});

		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity itemEntity) {
				TRACKED_BITCOIN_ITEMS.remove(itemEntity);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tickStabilityDecay(server);
			tickBitcoinOfferings();

			Set<UUID> online = new HashSet<>();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				online.add(player.getUUID());
				spawnStabilityPotionParticles(player);

				boolean shouldShowStabilityHud = isLookingAtServerBlock(player) || hasStabilityPotionVision(player);
				ServerBossBarVisibilitySystem.setServerHudFocus(player, shouldShowStabilityHud);

				if (!shouldShowStabilityHud) {
					hideHud(player);
					continue;
				}

				showHud(player, PolymerResourcePackUtils.hasMainPack(player));
				ServerBossBarVisibilitySystem.ensureServerHudPriority(player);
			}

			PLAYER_HUDS.entrySet().removeIf(entry -> {
				if (online.contains(entry.getKey())) {
					return false;
				}
				entry.getValue().removeAllPlayers();
				return true;
			});
			PLAYER_SPACER_HUDS.entrySet().removeIf(entry -> {
				if (online.contains(entry.getKey())) {
					return false;
				}
				entry.getValue().removeAllPlayers();
				return true;
			});
		});
	}

	private static void showHud(ServerPlayer player, boolean hasPack) {
		boolean spacerBecameVisible = hasPack && showSpacerHud(player);
		if (!hasPack) {
			hideSpacerHud(player);
		}

		ServerBossEvent hud = PLAYER_HUDS.computeIfAbsent(player.getUUID(), id -> createHudEvent());
		float progress = (float) getStability() / (float) getMaxStability();
		progress = Math.max(0.0F, Math.min(1.0F, progress));

		hud.setName(getHudTitle(player, hasPack));
		hud.setColor(BossEvent.BossBarColor.YELLOW);
		hud.setProgress(progress);

		if (!hud.getPlayers().contains(player)) {
			hud.addPlayer(player);
		} else if (spacerBecameVisible) {
			hud.removePlayer(player);
			hud.addPlayer(player);
		}

		hud.setVisible(true);
	}

	private static void hideHud(ServerPlayer player) {
		ServerBossEvent hud = PLAYER_HUDS.get(player.getUUID());
		if (hud != null) {
			hud.removePlayer(player);
			if (hud.getPlayers().isEmpty()) {
				PLAYER_HUDS.remove(player.getUUID());
			}
		}

		ServerBossEvent spacer = PLAYER_SPACER_HUDS.get(player.getUUID());
		if (spacer != null) {
			spacer.removePlayer(player);
			if (spacer.getPlayers().isEmpty()) {
				PLAYER_SPACER_HUDS.remove(player.getUUID());
			}
		}
	}

	private static ServerBossEvent createHudEvent() {
		ServerBossEvent event = new ServerBossEvent(
				Component.empty(),
				BossEvent.BossBarColor.YELLOW,
				BossEvent.BossBarOverlay.PROGRESS
		);
		event.setDarkenScreen(false);
		event.setPlayBossMusic(false);
		event.setCreateWorldFog(false);
		return event;
	}

	private static ServerBossEvent createSpacerHudEvent() {
		ServerBossEvent event = new ServerBossEvent(
				Component.empty(),
				BossEvent.BossBarColor.GREEN,
				BossEvent.BossBarOverlay.PROGRESS
		);
		event.setDarkenScreen(false);
		event.setPlayBossMusic(false);
		event.setCreateWorldFog(false);
		event.setProgress(0.0F);
		return event;
	}

	private static boolean showSpacerHud(ServerPlayer player) {
		ServerBossEvent spacer = PLAYER_SPACER_HUDS.computeIfAbsent(player.getUUID(), id -> createSpacerHudEvent());
		spacer.setName(Component.empty());
		spacer.setColor(BossEvent.BossBarColor.GREEN);
		spacer.setProgress(0.0F);
		spacer.setVisible(true);

		if (spacer.getPlayers().contains(player)) {
			return false;
		}

		spacer.addPlayer(player);
		return true;
	}

	private static void hideSpacerHud(ServerPlayer player) {
		ServerBossEvent spacer = PLAYER_SPACER_HUDS.get(player.getUUID());
		if (spacer == null) {
			return;
		}

		spacer.removePlayer(player);
		if (spacer.getPlayers().isEmpty()) {
			PLAYER_SPACER_HUDS.remove(player.getUUID());
		}
	}

	private static Component getHudTitle(ServerPlayer player, boolean hasPack) {
		if (hasPack) {
			return Component.literal(STABILITY_SYMBOL).withStyle(ServerStabilitySystem::applyPackStyle);
		}

		String language = player.clientInformation().language();
		if (language == null) {
			return textTitle("Stability");
		}

		String normalized = language.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("ru")) {
			return textTitle("Стабильность");
		}
		if (normalized.startsWith("uk")) {
			return textTitle("Стабільність");
		}
		if (normalized.startsWith("ja")) {
			return textTitle("サーバー安定性");
		}
		if (normalized.startsWith("rpr")) {
			return textTitle("Послушанiя жѣлѣзнаго разума");
		}
		return textTitle("Stability");
	}

	private static Component textTitle(String title) {
		return Component.literal(title).withStyle(ServerStabilitySystem::applyTextStyle);
	}

	private static Style applyTextStyle(Style style) {
		return style.withColor(TITLE_COLOR).withBold(true).withItalic(false);
	}

	private static Style applyPackStyle(Style style) {
		return style.withColor(PACK_SYMBOL_COLOR).withBold(false).withItalic(false);
	}

	public static int getStability() {
		return stability;
	}

	public static double getStabilityPercent() {
		int max = getMaxStability();
		if (max <= 0) {
			return 0.0D;
		}

		double percent = ((double) stability / (double) max) * 100.0D;
		return Math.max(0.0D, Math.min(100.0D, percent));
	}

	public static void setStability(int value) {
		int clamped = clamp(value);
		if (stability == clamped) {
			return;
		}

		stability = clamped;
		if (stabilityStateLoaded) {
			stabilityDirty = true;
		}
	}

	private static void tickStabilityDecay(MinecraftServer server) {
		long intervalTicks = Math.max(1L, (long) getEffectiveDecayIntervalSeconds(server) * 20L);
		int maxStability = getMaxStability();
		long tickNow = server.overworld().getGameTime();
		decayTickCounter++;

		while (decayTickCounter >= intervalTicks) {
			decayTickCounter -= intervalTicks;
			int before = getStability();
			setStability(before - 1);
			int after = getStability();
			if (before >= maxStability && after < maxStability) {
				// Leaving max should not force an immediate replay; keep at most one full-track cooldown.
				long relaxedCooldownTick = tickNow + FEED_SOUND_BASE_DURATION_TICKS;
				if (nextFeedSoundAllowedTick > relaxedCooldownTick) {
					nextFeedSoundAllowedTick = relaxedCooldownTick;
				}
			}
		}
	}

	private static void tickBitcoinOfferings() {
		for (ItemEntity itemEntity : TRACKED_BITCOIN_ITEMS) {
			if (itemEntity.isRemoved() || !itemEntity.isAlive()) {
				TRACKED_BITCOIN_ITEMS.remove(itemEntity);
				continue;
			}

			ItemStack stack = itemEntity.getItem();
			if (!stack.is(ModItems.BITCOIN) || stack.isEmpty()) {
				TRACKED_BITCOIN_ITEMS.remove(itemEntity);
				continue;
			}

			if (!(itemEntity.level() instanceof ServerLevel serverLevel)) {
				continue;
			}

			if (!isOnServerBlock(itemEntity, serverLevel)) {
				continue;
			}

			consumeBitcoinOffering(itemEntity, serverLevel);

			if (itemEntity.isRemoved()
					|| itemEntity.getItem().isEmpty()
					|| !itemEntity.getItem().is(ModItems.BITCOIN)) {
				TRACKED_BITCOIN_ITEMS.remove(itemEntity);
			}
		}
	}

	private static boolean consumeBitcoinOffering(ItemEntity itemEntity, ServerLevel level) {
		int max = getMaxStability();
		int before = getStability();
		int missing = max - before;
		if (missing <= 0) {
			// Prevent very long pitch-based cooldowns from blocking playback after stability drops from max.
			long relaxedCooldownTick = level.getGameTime() + FEED_SOUND_BASE_DURATION_TICKS;
			if (nextFeedSoundAllowedTick > relaxedCooldownTick) {
				nextFeedSoundAllowedTick = relaxedCooldownTick;
			}
			return false;
		}

		ItemStack stack = itemEntity.getItem();
		if (!stack.is(ModItems.BITCOIN) || stack.isEmpty()) {
			return false;
		}

		int availableBitcoins = stack.getCount();
		double bitcoinsPerStability = getBitcoinsPerStability();
		int bitcoinsToConsume = availableBitcoins;

		double requiredGain = missing - pendingStabilityFraction;
		if (requiredGain > 0.0D) {
			int neededToFill = (int) Math.ceil(requiredGain * bitcoinsPerStability - 1.0E-9D);
			bitcoinsToConsume = Math.min(availableBitcoins, Math.max(1, neededToFill));
		}

		double totalGain = pendingStabilityFraction + (bitcoinsToConsume / bitcoinsPerStability);
		int gainedPoints = Math.min((int) Math.floor(totalGain), missing);
		double nextFraction = totalGain - gainedPoints;

		if (gainedPoints > 0) {
			setStability(before + gainedPoints);
		}

		if (getStability() >= max) {
			pendingStabilityFraction = 0.0D;
		} else {
			pendingStabilityFraction = nextFraction;
		}

		if (bitcoinsToConsume >= availableBitcoins) {
			itemEntity.discard();
		} else {
			stack.shrink(bitcoinsToConsume);
			itemEntity.setItem(stack);
		}

		handleFeedFeedback(level, itemEntity);
		return true;
	}

	private static void handleFeedFeedback(ServerLevel level, ItemEntity itemEntity) {
		double x = itemEntity.getX();
		double y = itemEntity.getY();
		double z = itemEntity.getZ();

		long tickNow = level.getGameTime();
		level.sendParticles(FEED_PARTICLE, x + 0.1D, y + 1.0D, z + 0.1D, 10, 0.1D, 0.0D, 0.1D, 0.0D);

		float pitch = getFeedSoundPitch();
		if (pitch <= 0.01F) {
			return;
		}

		// Cap stale low-pitch cooldowns so quick follow-up feedings do not lock playback for too long.
		long maxAllowedTick = tickNow + FEED_SOUND_BASE_DURATION_TICKS;
		if (nextFeedSoundAllowedTick > maxAllowedTick) {
			nextFeedSoundAllowedTick = maxAllowedTick;
		}

		boolean canPlayPackMusic = tickNow >= nextFeedSoundAllowedTick;
		if (canPlayPackMusic) {
			nextFeedSoundAllowedTick = tickNow + getFeedSoundCooldownTicks(pitch);
		}

		long seed = level.getRandom().nextLong();
		for (ServerPlayer player : level.players()) {
			if (PolymerResourcePackUtils.hasMainPack(player)) {
				if (canPlayPackMusic) {
					stopFeedMusicForPlayer(player);
					sendSoundToPlayer(player, SoundEvents.MUSIC_DISC_13, SoundSource.RECORDS, x, y, z, FEED_MUSIC_SOUND_VOLUME, pitch, seed);
				}
			} else {
				// No resource pack: use vanilla xp sound and allow overlaps on every feeding tick.
				sendSoundToPlayer(
						player,
						BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP),
						SoundSource.PLAYERS,
						x,
						y,
						z,
						FEED_XP_SOUND_VOLUME,
						pitch,
						seed
				);
			}
		}
	}

	private static void sendSoundToPlayer(
			ServerPlayer player,
			Holder<net.minecraft.sounds.SoundEvent> sound,
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

	private static void stopFeedMusicForPlayer(ServerPlayer player) {
		if (FEED_MUSIC_SOUND_ID == null) {
			return;
		}
		player.connection.send(new ClientboundStopSoundPacket(FEED_MUSIC_SOUND_ID, SoundSource.RECORDS));
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

	private static boolean isOnServerBlock(ItemEntity itemEntity, ServerLevel level) {
		double itemX = itemEntity.getX();
		double itemY = itemEntity.getY();
		double itemZ = itemEntity.getZ();
		double maxDistanceSq = BITCOIN_FEED_RADIUS * BITCOIN_FEED_RADIUS;
		BlockPos entityPos = itemEntity.blockPosition();

		for (int dx = -BITCOIN_FEED_SCAN_RADIUS; dx <= BITCOIN_FEED_SCAN_RADIUS; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -BITCOIN_FEED_SCAN_RADIUS; dz <= BITCOIN_FEED_SCAN_RADIUS; dz++) {
					BlockPos candidatePos = entityPos.offset(dx, dy, dz);
					if (!level.getBlockState(candidatePos).is(ModBlocks.SERVER)) {
						continue;
					}

					double minX = candidatePos.getX();
					double minY = candidatePos.getY();
					double minZ = candidatePos.getZ();
					double maxX = minX + 1.0D;
					double maxY = minY + 1.0D;
					double maxZ = minZ + 1.0D;

					double deltaX = axisDistance(itemX, minX, maxX);
					double deltaY = axisDistance(itemY, minY, maxY);
					double deltaZ = axisDistance(itemZ, minZ, maxZ);
					double distanceSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

					if (distanceSq <= maxDistanceSq) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static double axisDistance(double value, double min, double max) {
		if (value < min) {
			return min - value;
		}
		if (value > max) {
			return value - max;
		}
		return 0.0D;
	}

	private static boolean isLookingAtServerBlock(ServerPlayer player) {
		HitResult hit = player.pick(6.0D, 0.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}

		BlockHitResult blockHit = (BlockHitResult) hit;
		return player.level().getBlockState(blockHit.getBlockPos()).is(ModBlocks.SERVER);
	}

	public static void activateStabilityPotion(ServerPlayer player, int durationTicks, boolean anyWorldVisibility) {
		if (durationTicks <= 0) {
			return;
		}
		player.addEffect(createStabilityPotionEffect(durationTicks, anyWorldVisibility));
	}

	public static MobEffectInstance createStabilityPotionEffect(int durationTicks, boolean anyWorldVisibility) {
		return new MobEffectInstance(MobEffects.UNLUCK, durationTicks, anyWorldVisibility ? 1 : 0, false, false, true);
	}

	public static void onServerStructurePlaced(ServerLevel level, BlockPos anchor) {
		if (trackServerAnchor(level, anchor)) {
			stabilityDirty = true;
		}
	}

	public static void onServerStructureRemoved(ServerLevel level, BlockPos anchor) {
		if (untrackServerAnchor(level, anchor)) {
			stabilityDirty = true;
		}
	}

	private static boolean hasStabilityPotionVision(ServerPlayer player) {
		MobEffectInstance effect = getStabilityPotionEffect(player);
		if (effect == null) {
			return false;
		}
		if (effect.getAmplifier() >= 1) {
			return true;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		return hasTrackedServerInWorld(level);
	}

	private static MobEffectInstance getStabilityPotionEffect(ServerPlayer player) {
		return player.getEffect(MobEffects.UNLUCK);
	}

	private static void spawnStabilityPotionParticles(ServerPlayer player) {
		MobEffectInstance effect = getStabilityPotionEffect(player);
		if (effect == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		long gameTime = level.getGameTime();
		if ((gameTime + player.getId()) % 5L != 0L) {
			return;
		}

		double halfWidth = player.getBbWidth() * 0.35D;
		double height = player.getBbHeight();
		level.sendParticles(
				STABILITY_POTION_PARTICLE,
				player.getX(),
				player.getY() + height * 0.5D,
				player.getZ(),
				2,
				halfWidth,
				height * 0.35D,
				halfWidth,
				0.0D
		);
	}

	private static boolean hasTrackedServerInWorld(ServerLevel level) {
		String dimensionId = dimensionId(level.dimension());
		Set<String> anchors = TRACKED_SERVER_ANCHORS.get(dimensionId);
		if (anchors != null && !anchors.isEmpty()) {
			return true;
		}
		return discoverLoadedServerAnchors(level);
	}

	private static boolean discoverLoadedServerAnchors(ServerLevel level) {
		boolean discoveredAny = false;
		for (var entity : level.getAllEntities()) {
			if (!ServerStructureBreakSystem.isServerStructureDisplay(entity)) {
				continue;
			}
			var anchor = ServerStructureBreakSystem.getServerStructureDisplayAnchor(entity);
			if (anchor.isEmpty()) {
				continue;
			}
			discoveredAny |= trackServerAnchor(level, anchor.get());
		}
		if (discoveredAny) {
			stabilityDirty = true;
		}
		Set<String> anchors = TRACKED_SERVER_ANCHORS.get(dimensionId(level.dimension()));
		return anchors != null && !anchors.isEmpty();
	}

	private static boolean trackServerAnchor(ServerLevel level, BlockPos anchor) {
		return TRACKED_SERVER_ANCHORS
				.computeIfAbsent(dimensionId(level.dimension()), ignored -> new HashSet<>())
				.add(serializeBlockPos(anchor));
	}

	private static boolean untrackServerAnchor(ServerLevel level, BlockPos anchor) {
		String dimensionId = dimensionId(level.dimension());
		Set<String> anchors = TRACKED_SERVER_ANCHORS.get(dimensionId);
		if (anchors == null) {
			return false;
		}
		boolean removed = anchors.remove(serializeBlockPos(anchor));
		if (anchors.isEmpty()) {
			TRACKED_SERVER_ANCHORS.remove(dimensionId);
		}
		return removed;
	}

	private static void bootstrapTrackedServerAnchors(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			discoverLoadedServerAnchors(level);
		}
	}

	private static String dimensionId(net.minecraft.resources.ResourceKey<Level> dimension) {
		return dimension.identifier().toString();
	}

	private static String serializeBlockPos(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public static boolean isHudBossBar(ServerPlayer player, UUID bossBarId) {
		ServerBossEvent hud = PLAYER_HUDS.get(player.getUUID());
		if (hud != null && hud.getId().equals(bossBarId)) {
			return true;
		}

		ServerBossEvent spacer = PLAYER_SPACER_HUDS.get(player.getUUID());
		return spacer != null && spacer.getId().equals(bossBarId);
	}

	private static int getMaxStability() {
		return Math.max(1, Lg2Config.get().stabilityMax);
	}

	private static int getDecayIntervalSeconds() {
		return Math.max(1, Lg2Config.get().stabilityDecayIntervalSeconds);
	}

	private static int getDecayIntervalSecondsPerPlayer() {
		return Math.max(0, Lg2Config.get().stabilityDecayIntervalSecondsPerPlayer);
	}

	private static int getEffectiveDecayIntervalSeconds(MinecraftServer server) {
		int baseIntervalSeconds = getDecayIntervalSeconds();
		int onlinePlayers = server.getPlayerList().getPlayerCount();
		long reductionSeconds = (long) onlinePlayers * getDecayIntervalSecondsPerPlayer();
		long effectiveIntervalSeconds = (long) baseIntervalSeconds - reductionSeconds;
		return (int) Math.max(1L, effectiveIntervalSeconds);
	}

	private static double getBitcoinsPerStability() {
		double value = Lg2Config.get().bitcoinsPerStability;
		if (!Double.isFinite(value) || value <= 0.0D) {
			return 1.0D;
		}
		return value;
	}

	private static float getFeedSoundPitch() {
		int max = getMaxStability();
		if (max <= 0) {
			return 1.0F;
		}

		float normalized = (float) getStability() / (float) max;
		if (normalized >= 0.5F) {
			return 1.0F;
		}

		float pitch = normalized / 0.5F;
		return Math.max(0.0F, Math.min(1.0F, pitch));
	}

	private static long getFeedSoundCooldownTicks(float pitch) {
		if (pitch <= 0.01F) {
			return FEED_SOUND_BASE_DURATION_TICKS;
		}

		long scaled = (long) Math.ceil(FEED_SOUND_BASE_DURATION_TICKS / pitch);
		return Math.max(FEED_SOUND_BASE_DURATION_TICKS, scaled);
	}

	private static int clamp(int value) {
		return Math.max(0, Math.min(getMaxStability(), value));
	}

	private static Path getStabilityStatePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(STABILITY_STATE_FILE_NAME);
	}

	private static void loadPersistedStability(MinecraftServer server) {
		Path path = getStabilityStatePath(server);
		boolean dirtyAfterLoad = false;
		TRACKED_SERVER_ANCHORS.clear();

		if (!Files.exists(path)) {
			setStability(getMaxStability());
			bootstrapTrackedServerAnchors(server);
			stabilityStateLoaded = true;
			stabilityDirty = true;
			return;
		}

		try (Reader reader = Files.newBufferedReader(path)) {
			StabilityState state = STABILITY_STATE_GSON.fromJson(reader, StabilityState.class);
			if (state == null) {
				setStability(getMaxStability());
				dirtyAfterLoad = true;
			} else {
				setStability(state.stability);
				if (state.trackedServerAnchors != null) {
					for (Map.Entry<String, Set<String>> entry : state.trackedServerAnchors.entrySet()) {
						Set<String> anchors = entry.getValue();
						if (anchors == null || anchors.isEmpty()) {
							continue;
						}
						TRACKED_SERVER_ANCHORS.put(entry.getKey(), new HashSet<>(anchors));
					}
				}
			}

			if (TRACKED_SERVER_ANCHORS.isEmpty()) {
				bootstrapTrackedServerAnchors(server);
				if (!TRACKED_SERVER_ANCHORS.isEmpty()) {
					dirtyAfterLoad = true;
				}
			}

			stabilityStateLoaded = true;
			stabilityDirty = dirtyAfterLoad;
		} catch (Exception e) {
			Lg2.LOGGER.warn("Failed to read persisted stability from {}", path, e);
			setStability(getMaxStability());
			bootstrapTrackedServerAnchors(server);
			stabilityStateLoaded = true;
			stabilityDirty = true;
		}
	}

	private static void savePersistedStability(MinecraftServer server) {
		if (!stabilityStateLoaded || !stabilityDirty) {
			return;
		}

		Path path = getStabilityStatePath(server);
		StabilityState state = new StabilityState();
		state.stability = getStability();
		if (!TRACKED_SERVER_ANCHORS.isEmpty()) {
			state.trackedServerAnchors = new HashMap<>();
			for (Map.Entry<String, Set<String>> entry : TRACKED_SERVER_ANCHORS.entrySet()) {
				if (entry.getValue().isEmpty()) {
					continue;
				}
				state.trackedServerAnchors.put(entry.getKey(), new HashSet<>(entry.getValue()));
			}
		}

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				STABILITY_STATE_GSON.toJson(state, writer);
			}
			stabilityDirty = false;
		} catch (IOException e) {
			Lg2.LOGGER.warn("Failed to save persisted stability to {}", path, e);
		}
	}
}
