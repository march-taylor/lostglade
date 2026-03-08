package com.lostglade.server;

import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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

	private static final Map<UUID, ServerBossEvent> PLAYER_HUDS = new HashMap<>();
	private static final Map<UUID, ServerBossEvent> PLAYER_SPACER_HUDS = new HashMap<>();
	private static final Set<ItemEntity> TRACKED_BITCOIN_ITEMS = ConcurrentHashMap.newKeySet();
	private static final int BITCOIN_FEED_SCAN_RADIUS = 1;
	private static final double BITCOIN_FEED_HORIZONTAL_RANGE = 1.35D;
	private static final double BITCOIN_FEED_VERTICAL_RANGE = 1.25D;
	private static final long FEED_SOUND_BASE_DURATION_TICKS = 240L;
	private static final float FEED_MUSIC_SOUND_VOLUME = 0.55F;
	private static final float FEED_XP_SOUND_VOLUME = 1.0F;
	private static final SimpleParticleType FEED_PARTICLE = resolveFeedParticle();

	private static int stability = 100;
	private static long decayTickCounter = 0L;
	private static double pendingStabilityFraction = 0.0D;
	private static long nextFeedSoundAllowedTick = Long.MIN_VALUE;

	private ServerStabilitySystem() {
	}

	public static void register() {
		setStability(getMaxStability());
		decayTickCounter = 0L;
		pendingStabilityFraction = 0.0D;
		nextFeedSoundAllowedTick = Long.MIN_VALUE;

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
			tickStabilityDecay();
			tickBitcoinOfferings();

			Set<UUID> online = new HashSet<>();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				online.add(player.getUUID());

				if (!isLookingAtServerBlock(player)) {
					hideHud(player);
					continue;
				}

				showHud(player, PolymerResourcePackUtils.hasMainPack(player));
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
			// Re-add so main bar is rendered below the spacer bar.
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
				BossEvent.BossBarColor.PINK,
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
		spacer.setColor(BossEvent.BossBarColor.PINK);
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
			return textTitle("???????");
		}
		if (normalized.startsWith("rdr") || normalized.startsWith("rpr")) {
			return textTitle("Послушанiя ж?л?знаго разума");
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
		return style.withColor(TITLE_COLOR).withBold(false).withItalic(false);
	}

	public static int getStability() {
		return stability;
	}

	public static void setStability(int value) {
		stability = clamp(value);
	}

	private static void tickStabilityDecay() {
		long intervalTicks = Math.max(1L, (long) getDecayIntervalSeconds() * 20L);
		decayTickCounter++;

		while (decayTickCounter >= intervalTicks) {
			decayTickCounter -= intervalTicks;
			setStability(getStability() - 1);
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

		boolean canPlayPackMusic = tickNow >= nextFeedSoundAllowedTick;
		if (canPlayPackMusic) {
			nextFeedSoundAllowedTick = tickNow + getFeedSoundCooldownTicks(pitch);
		}

		long seed = level.getRandom().nextLong();
		for (ServerPlayer player : level.players()) {
			if (PolymerResourcePackUtils.hasMainPack(player)) {
				if (canPlayPackMusic) {
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
		BlockPos onPos = itemEntity.getOnPos();
		if (level.getBlockState(onPos).is(ModBlocks.SERVER)) {
			return true;
		}

		BlockPos entityPos = itemEntity.blockPosition();
		if (level.getBlockState(entityPos).is(ModBlocks.SERVER)
				|| level.getBlockState(entityPos.below()).is(ModBlocks.SERVER)) {
			return true;
		}

		// The server model is visually larger than one cube, so accept offerings near side faces as well.
		double itemX = itemEntity.getX();
		double itemY = itemEntity.getY();
		double itemZ = itemEntity.getZ();
		double maxHorizontalDistanceSq = BITCOIN_FEED_HORIZONTAL_RANGE * BITCOIN_FEED_HORIZONTAL_RANGE;

		for (int dx = -BITCOIN_FEED_SCAN_RADIUS; dx <= BITCOIN_FEED_SCAN_RADIUS; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -BITCOIN_FEED_SCAN_RADIUS; dz <= BITCOIN_FEED_SCAN_RADIUS; dz++) {
					BlockPos candidatePos = entityPos.offset(dx, dy, dz);
					if (!level.getBlockState(candidatePos).is(ModBlocks.SERVER)) {
						continue;
					}

					double centerX = candidatePos.getX() + 0.5D;
					double centerY = candidatePos.getY() + 0.5D;
					double centerZ = candidatePos.getZ() + 0.5D;
					double horizontalDistanceSq = (itemX - centerX) * (itemX - centerX)
							+ (itemZ - centerZ) * (itemZ - centerZ);

					if (horizontalDistanceSq <= maxHorizontalDistanceSq
							&& Math.abs(itemY - centerY) <= BITCOIN_FEED_VERTICAL_RANGE) {
						return true;
					}
				}
			}
		}

		return false;
	}

	private static boolean isLookingAtServerBlock(ServerPlayer player) {
		HitResult hit = player.pick(6.0D, 0.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}

		BlockHitResult blockHit = (BlockHitResult) hit;
		return player.level().getBlockState(blockHit.getBlockPos()).is(ModBlocks.SERVER);
	}

	private static int getMaxStability() {
		return Math.max(1, Lg2Config.get().stabilityMax);
	}

	private static int getDecayIntervalSeconds() {
		return Math.max(1, Lg2Config.get().stabilityDecayIntervalSeconds);
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
}

