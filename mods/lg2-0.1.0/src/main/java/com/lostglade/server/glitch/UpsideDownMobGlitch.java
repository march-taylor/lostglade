package com.lostglade.server.glitch;

import com.google.gson.JsonObject;
import com.lostglade.config.GlitchConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UpsideDownMobGlitch implements ServerGlitchHandler {
	private static final String MIN_MOB_PERCENT = "minMobPercent";
	private static final String MAX_MOB_PERCENT = "maxMobPercent";
	private static final String MIN_DURATION_SECONDS = "minDurationSeconds";
	private static final String MAX_DURATION_SECONDS = "maxDurationSeconds";
	private static final String UPSIDE_DOWN_MOB_TAG = "lg2_upside_down_mob";
	private static final Component UPSIDE_DOWN_NAME = Component.literal("Dinnerbone");
	private static final double VALUE_PRIORITY = 2.4D;
	private static final Map<UUID, ActiveUpsideDownState> ACTIVE_STATES = new HashMap<>();

	public static void tickActiveStates(MinecraftServer server) {
		if (server == null || ACTIVE_STATES.isEmpty()) {
			return;
		}

		long nowTick = server.overworld().getGameTime();
		Iterator<Map.Entry<UUID, ActiveUpsideDownState>> iterator = ACTIVE_STATES.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, ActiveUpsideDownState> mapEntry = iterator.next();
			ActiveUpsideDownState state = mapEntry.getValue();
			Mob mob = findMob(server, state.dimension, state.entityUuid);
			if (mob != null && mob.level() instanceof ServerLevel serverLevel) {
				state.dimension = serverLevel.dimension();
			}

			if (nowTick >= state.endTick) {
				if (mob != null) {
					restoreMob(mob, state);
				}
				iterator.remove();
				continue;
			}

			if (mob == null) {
				continue;
			}

			if (!mob.isAlive() || mob.isRemoved()) {
				iterator.remove();
				continue;
			}

			applyUpsideDown(mob);
		}
	}

	public static void resetRuntimeState() {
		ACTIVE_STATES.clear();
	}

	public static void restoreAll(MinecraftServer server) {
		if (server != null) {
			for (ServerLevel level : server.getAllLevels()) {
				for (Entity entity : level.getAllEntities()) {
					if (entity instanceof Mob mob && mob.getTags().contains(UPSIDE_DOWN_MOB_TAG)) {
						ActiveUpsideDownState state = ACTIVE_STATES.get(mob.getUUID());
						restoreMob(mob, state);
					}
				}
			}
		}

		ACTIVE_STATES.clear();
	}

	public static void handleEntityLoad(Entity entity) {
		if (entity == null) {
			return;
		}

		if (!(entity instanceof Mob mob) || !mob.getTags().contains(UPSIDE_DOWN_MOB_TAG)) {
			return;
		}

		ActiveUpsideDownState state = ACTIVE_STATES.get(mob.getUUID());
		long nowTick = mob.level().getGameTime();
		if (state == null || nowTick >= state.endTick) {
			restoreMob(mob, state);
			return;
		}

		applyUpsideDown(mob);
		if (mob.level() instanceof ServerLevel serverLevel) {
			state.dimension = serverLevel.dimension();
		}
	}

	@Override
	public String id() {
		return "upside_down_mobs";
	}

	@Override
	public GlitchConfig.GlitchEntry defaultEntry() {
		GlitchConfig.GlitchEntry entry = new GlitchConfig.GlitchEntry();
		entry.enabled = true;
		entry.minStabilityPercent = 0.0D;
		entry.maxStabilityPercent = 55.0D;
		entry.chancePerCheck = 0.16D;
		entry.stabilityInfluence = 1.0D;
		entry.minCooldownTicks = 80;
		entry.maxCooldownTicks = 2400;

		JsonObject settings = new JsonObject();
		settings.addProperty(MIN_MOB_PERCENT, 4.0D);
		settings.addProperty(MAX_MOB_PERCENT, 28.0D);
		settings.addProperty(MIN_DURATION_SECONDS, 6);
		settings.addProperty(MAX_DURATION_SECONDS, 24);
		entry.settings = settings;
		return entry;
	}

	@Override
	public boolean sanitizeSettings(GlitchConfig.GlitchEntry entry) {
		if (entry.settings == null) {
			entry.settings = new JsonObject();
		}

		boolean changed = false;
		changed |= GlitchSettingsHelper.sanitizeDouble(entry.settings, MIN_MOB_PERCENT, 4.0D, 0.0D, 100.0D);
		changed |= GlitchSettingsHelper.sanitizeDouble(entry.settings, MAX_MOB_PERCENT, 28.0D, 0.0D, 100.0D);
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MIN_DURATION_SECONDS, 6, 1, 3600);
		changed |= GlitchSettingsHelper.sanitizeInt(entry.settings, MAX_DURATION_SECONDS, 24, 1, 3600);

		double minPercent = GlitchSettingsHelper.getDouble(entry.settings, MIN_MOB_PERCENT, 4.0D);
		double maxPercent = GlitchSettingsHelper.getDouble(entry.settings, MAX_MOB_PERCENT, 28.0D);
		if (maxPercent < minPercent) {
			entry.settings.addProperty(MAX_MOB_PERCENT, minPercent);
			changed = true;
		}

		int minDuration = GlitchSettingsHelper.getInt(entry.settings, MIN_DURATION_SECONDS, 6);
		int maxDuration = GlitchSettingsHelper.getInt(entry.settings, MAX_DURATION_SECONDS, 24);
		if (maxDuration < minDuration) {
			entry.settings.addProperty(MAX_DURATION_SECONDS, minDuration);
			changed = true;
		}

		return changed;
	}

	@Override
	public boolean trigger(MinecraftServer server, RandomSource random, GlitchConfig.GlitchEntry entry, double stabilityPercent) {
		tickActiveStates(server);

		List<Mob> mobs = collectEligibleMobs(server);
		if (mobs.isEmpty()) {
			return false;
		}

		JsonObject settings = entry.settings == null ? new JsonObject() : entry.settings;
		double instability = getRangeInstabilityFactor(stabilityPercent, entry.minStabilityPercent, entry.maxStabilityPercent);
		double minMobPercent = GlitchSettingsHelper.getDouble(settings, MIN_MOB_PERCENT, 4.0D);
		double maxMobPercent = GlitchSettingsHelper.getDouble(settings, MAX_MOB_PERCENT, 28.0D);
		double dynamicMaxMobPercent = interpolateDouble(minMobPercent, maxMobPercent, instability);
		double selectedMobPercent = sampleWeightedRange(random, minMobPercent, dynamicMaxMobPercent, VALUE_PRIORITY);
		int targetCount = getTargetCount(mobs.size(), selectedMobPercent);
		if (targetCount <= 0) {
			return false;
		}

		int minDurationSeconds = GlitchSettingsHelper.getInt(settings, MIN_DURATION_SECONDS, 6);
		int maxDurationSeconds = GlitchSettingsHelper.getInt(settings, MAX_DURATION_SECONDS, 24);
		int dynamicMaxDurationSeconds = interpolateInt(minDurationSeconds, maxDurationSeconds, instability);
		int durationSeconds = sampleWeightedRangeInt(random, minDurationSeconds, dynamicMaxDurationSeconds, VALUE_PRIORITY);
		long durationTicks = Math.max(20L, durationSeconds * 20L);
		long nowTick = server.overworld().getGameTime();

		shuffle(mobs, random);
		boolean appliedAny = false;
		for (int i = 0; i < targetCount; i++) {
			Mob mob = mobs.get(i);
			ActiveUpsideDownState state = new ActiveUpsideDownState(
					mob.getUUID(),
					getDimension(mob),
					nowTick + durationTicks,
					copyComponent(mob.getCustomName()),
					mob.isCustomNameVisible()
			);
			applyUpsideDown(mob);
			ACTIVE_STATES.put(mob.getUUID(), state);
			appliedAny = true;
		}

		return appliedAny;
	}

	private static List<Mob> collectEligibleMobs(MinecraftServer server) {
		List<Mob> mobs = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof Mob mob)) {
					continue;
				}
				if (!isEligibleMob(mob)) {
					continue;
				}
				mobs.add(mob);
			}
		}
		return mobs;
	}

	private static boolean isEligibleMob(Mob mob) {
		if (mob == null || !mob.isAlive() || mob.isRemoved()) {
			return false;
		}
		if (ACTIVE_STATES.containsKey(mob.getUUID())) {
			return false;
		}

		Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return entityId != null && "minecraft".equals(entityId.getNamespace());
	}

	private static void applyUpsideDown(Mob mob) {
		mob.addTag(UPSIDE_DOWN_MOB_TAG);
		mob.setCustomName(UPSIDE_DOWN_NAME.copy());
		mob.setCustomNameVisible(false);
	}

	private static void restoreMob(Mob mob, ActiveUpsideDownState state) {
		mob.removeTag(UPSIDE_DOWN_MOB_TAG);
		if (state != null && state.originalCustomName != null) {
			mob.setCustomName(state.originalCustomName.copy());
			mob.setCustomNameVisible(state.originalCustomNameVisible);
			return;
		}

		if (isUpsideDownName(mob.getCustomName())) {
			mob.setCustomName(null);
		}
		mob.setCustomNameVisible(false);
	}

	private static boolean isUpsideDownName(Component component) {
		if (component == null) {
			return false;
		}
		String value = component.getString();
		return "Dinnerbone".equals(value) || "Grumm".equals(value);
	}

	private static Component copyComponent(Component component) {
		return component == null ? null : component.copy();
	}

	private static Mob findMob(MinecraftServer server, ResourceKey<Level> dimension, UUID uuid) {
		ServerLevel level = server.getLevel(dimension);
		if (level == null) {
			return null;
		}

		Entity entity = level.getEntity(uuid);
		return entity instanceof Mob mob ? mob : null;
	}

	private static ResourceKey<Level> getDimension(Entity entity) {
		return entity.level() instanceof ServerLevel serverLevel ? serverLevel.dimension() : Level.OVERWORLD;
	}

	private static int getTargetCount(int totalCount, double percent) {
		if (totalCount <= 0 || percent <= 0.0D) {
			return 0;
		}

		double clampedPercent = Math.max(0.0D, Math.min(100.0D, percent));
		int count = (int) Math.round(totalCount * (clampedPercent / 100.0D));
		return Math.max(1, Math.min(totalCount, count));
	}

	private static int interpolateInt(int min, int max, double factor) {
		if (max <= min) {
			return min;
		}
		return min + (int) Math.round((max - min) * clamp01(factor));
	}

	private static double interpolateDouble(double min, double max, double factor) {
		if (max <= min) {
			return min;
		}
		return min + ((max - min) * clamp01(factor));
	}

	private static int sampleWeightedRangeInt(RandomSource random, int min, int max, double priority) {
		if (max <= min) {
			return min;
		}
		double weightedRoll = getUpperBiasedRoll(random, priority);
		return min + (int) Math.round((max - min) * weightedRoll);
	}

	private static double sampleWeightedRange(RandomSource random, double min, double max, double priority) {
		if (max <= min) {
			return min;
		}
		double weightedRoll = getUpperBiasedRoll(random, priority);
		return min + ((max - min) * weightedRoll);
	}

	private static double getUpperBiasedRoll(RandomSource random, double priority) {
		double sanitizedPriority = Math.max(1.0D, priority);
		return 1.0D - Math.pow(1.0D - random.nextDouble(), sanitizedPriority);
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	private static double getRangeInstabilityFactor(double stabilityPercent, double minStabilityPercent, double maxStabilityPercent) {
		double range = maxStabilityPercent - minStabilityPercent;
		if (range <= 1.0E-9D) {
			return 1.0D;
		}

		double normalized = (stabilityPercent - minStabilityPercent) / range;
		return clamp01(1.0D - normalized);
	}

	private static void shuffle(List<Mob> list, RandomSource random) {
		Collections.shuffle(list, new java.util.Random(random.nextLong()));
	}

	private static final class ActiveUpsideDownState {
		private final UUID entityUuid;
		private ResourceKey<Level> dimension;
		private final long endTick;
		private final Component originalCustomName;
		private final boolean originalCustomNameVisible;

		private ActiveUpsideDownState(
				UUID entityUuid,
				ResourceKey<Level> dimension,
				long endTick,
				Component originalCustomName,
				boolean originalCustomNameVisible
		) {
			this.entityUuid = entityUuid;
			this.dimension = dimension;
			this.endTick = endTick;
			this.originalCustomName = originalCustomName;
			this.originalCustomNameVisible = originalCustomNameVisible;
		}
	}
}
