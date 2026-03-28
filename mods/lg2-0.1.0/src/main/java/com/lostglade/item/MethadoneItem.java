package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import com.lostglade.server.ServerRaceSystem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

public final class MethadoneItem extends SimplePolymerItem {
    private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "methadone");
    private static final Identifier METHADONE_USE_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "methadone_use");
    private static final Holder<SoundEvent> METHADONE_USE_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(METHADONE_USE_SOUND_ID));
    private static final Identifier FALLBACK_MODEL_ID = Identifier.fromNamespaceAndPath("minecraft", "potion");
    private static final PotionContents FALLBACK_POTION_CONTENTS = new PotionContents(Potions.STRENGTH);
    private static final Holder<MobEffect> MINING_FATIGUE_EFFECT = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
            BuiltInRegistries.MOB_EFFECT.getValue(Identifier.fromNamespaceAndPath("minecraft", "mining_fatigue"))
    );
    private static final Holder<MobEffect> SLOWNESS_EFFECT = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(
            BuiltInRegistries.MOB_EFFECT.getValue(Identifier.fromNamespaceAndPath("minecraft", "slowness"))
    );
    private static final String MISTER_CARTEL_49_RACE_ID = "mister_cartel_49";
    private static final int USE_DURATION_TICKS = 10;
    private static final int CARTEL_EFFECT_DURATION_TICKS = 3 * 60 * 20;
    private static final int NON_CARTEL_EFFECT_DURATION_TICKS = 2 * 60 * 20;
    private static final double DEFAULT_ADDICTION_SECONDS = 60.0D * 60.0D;
    private static final double DEFAULT_WITHDRAWAL_START_SECONDS = 30.0D * 60.0D;
    private static final int WITHDRAWAL_REFRESH_DURATION_TICKS = 100;
    private static final int WITHDRAWAL_REAPPLY_THRESHOLD_TICKS = 40;
    private static final int RELEASE_COOLDOWN_TICKS = 20;
    private static final int SOUND_MAX_DISTANCE_SQR = 24 * 24;
    private static final float SOUND_VOLUME = 0.85F;
    private static final float PACK_SOUND_PITCH = 1.0F;
    private static final float FALLBACK_SOUND_PITCH = 1.0F;
    private static final int ACID_SKY_PHASE_INTERVAL_TICKS = 4;
    private static final int ACID_SKY_TRACKED_CHUNK_REFRESH_INTERVAL_TICKS = 60;
    private static final int ACID_SKY_CHUNK_BATCH_SIZE = 48;
    private static final int METHADONE_FORCED_TIME_INTERVAL_TICKS = 20;
    private static final long METHADONE_FORCED_DAY_TIME = 6000L;
    private static final long[] EMPTY_CHUNK_KEYS = new long[0];
    private static final ChunkPos[] EMPTY_CHUNK_POSITIONS = new ChunkPos[0];
    private static final List<ResourceKey<Biome>> ACID_SKY_BIOME_KEYS = IntStream.range(0, 16)
            .mapToObj(index -> acidSkyBiomeKey("acid_sky_" + String.format(Locale.ROOT, "%02d", index)))
            .toList();
    private static final Consumable CLIENT_USE_CONSUMABLE = Consumable.builder()
            .consumeSeconds(0.5F)
            .animation(ItemUseAnimation.TOOT_HORN)
            .sound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY))
            .hasConsumeParticles(false)
            .build();
    private static final Map<UUID, MethadoneAddictionState> ADDICTION_STATES = new HashMap<>();
    private static final Map<UUID, MethadoneSkyState> ACID_SKY_STATES = new HashMap<>();
    private static final Map<AcidSkyPayloadKey, byte[]> ACID_SKY_PAYLOAD_CACHE = new HashMap<>();

    public MethadoneItem(Item.Properties settings) {
        super(settings, Items.PAPER);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Methadone");
    }

    @Override
    public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
        return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
    }

    @Override
    public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
        out.set(DataComponents.CUSTOM_NAME, getLocalizedName(context).withStyle(style -> style.withItalic(false)));
        out.set(DataComponents.CONSUMABLE, CLIENT_USE_CONSUMABLE);
        TooltipDisplay tooltipDisplay = TooltipDisplay.DEFAULT;
        if (!PolymerResourcePackUtils.hasMainPack(context)) {
            out.set(DataComponents.ITEM_MODEL, FALLBACK_MODEL_ID);
            out.set(DataComponents.POTION_CONTENTS, FALLBACK_POTION_CONTENTS);
            tooltipDisplay = tooltipDisplay.withHidden(DataComponents.POTION_CONTENTS, true);
        }
        out.set(DataComponents.TOOLTIP_DISPLAY, tooltipDisplay);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            playUseSound(serverPlayer);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return use(context.getLevel(), player, context.getHand());
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TOOT_HORN;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player usedByPlayer ? usedByPlayer : null;
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }

        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            player.getCooldowns().addCooldown(stack, RELEASE_COOLDOWN_TICKS);
        }

        if (!level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            applyUseEffects(serverPlayer);
        }

        if (player != null && player.getAbilities().instabuild) {
            return stack;
        }

        stack.consume(1, entity);
        return stack;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        tickAddiction(server);
        tickAcidSky(server);
    }

    private static void tickAddiction(MinecraftServer server) {
        Iterator<Map.Entry<UUID, MethadoneAddictionState>> iterator = ADDICTION_STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, MethadoneAddictionState> entry = iterator.next();
            UUID playerId = entry.getKey();
            MethadoneAddictionState state = entry.getValue();
            if (playerId == null || state == null) {
                iterator.remove();
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }

            state.remainingTicks--;
            if (state.remainingTicks <= 0L) {
                if (state.withdrawalActive) {
                    clearWithdrawalEffects(player);
                }
                iterator.remove();
                continue;
            }

            if (state.remainingTicks <= state.withdrawalStartRemainingTicks) {
                ensureWithdrawalEffects(player);
                state.withdrawalActive = true;
            } else {
                if (state.withdrawalActive) {
                    clearWithdrawalEffects(player);
                    state.withdrawalActive = false;
                }
            }
        }
    }

    private static void tickAcidSky(MinecraftServer server) {
        ACID_SKY_STATES.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            MethadoneSkyState state = entry.getValue();
            if (playerId == null || state == null) {
                return true;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                return false;
            }

            if (!player.isAlive()) {
                restoreOriginalBiomes(player, state);
                return true;
            }

            state.remainingTicks--;
            if (state.remainingTicks <= 0L) {
                restoreOriginalBiomes(player, state);
                return true;
            }

            updateAcidSky(player, state);
            return false;
        });
    }

    private static void applyUseEffects(ServerPlayer player) {
        if (isMrCartel(player)) {
            refreshAcidSky(player, CARTEL_EFFECT_DURATION_TICKS);
            applyCartelBuffs(player, CARTEL_EFFECT_DURATION_TICKS);
            return;
        }

        MethadoneAddictionState previousState = ADDICTION_STATES.get(player.getUUID());
        boolean stillAddicted = previousState != null && previousState.remainingTicks > 0L;
        long addictionDurationTicks = resolveAddictionDurationTicks();
        long withdrawalStartRemainingTicks = resolveWithdrawalStartRemainingTicks(addictionDurationTicks);
        MethadoneAddictionState state = previousState;
        if (state == null) {
            state = new MethadoneAddictionState();
            ADDICTION_STATES.put(player.getUUID(), state);
        }
        state.remainingTicks = addictionDurationTicks;
        state.withdrawalStartRemainingTicks = withdrawalStartRemainingTicks;
        state.doseCount = stillAddicted ? state.doseCount + 1 : 1;
        if (state.withdrawalActive) {
            clearWithdrawalEffects(player);
            state.withdrawalActive = false;
        }
        refreshAcidSky(player, NON_CARTEL_EFFECT_DURATION_TICKS);

        if (state.doseCount <= 3) {
            applyEarlyDoseEffects(player);
            return;
        }

        applyCartelBuffs(player, NON_CARTEL_EFFECT_DURATION_TICKS);
    }

    private static void applyCartelBuffs(ServerPlayer player, int durationTicks) {
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, durationTicks, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, durationTicks, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, durationTicks, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, durationTicks, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, durationTicks, 0, false, true, true));
    }

    private static void applyEarlyDoseEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NON_CARTEL_EFFECT_DURATION_TICKS, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, NON_CARTEL_EFFECT_DURATION_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(MINING_FATIGUE_EFFECT, NON_CARTEL_EFFECT_DURATION_TICKS, 1, false, true, true));
        player.addEffect(new MobEffectInstance(SLOWNESS_EFFECT, NON_CARTEL_EFFECT_DURATION_TICKS, 0, false, true, true));
    }

    private static void ensureWithdrawalEffects(ServerPlayer player) {
        ensureWithdrawalEffect(player, MobEffects.NAUSEA, 0);
        ensureWithdrawalEffect(player, MobEffects.WEAKNESS, 1);
        ensureWithdrawalEffect(player, MINING_FATIGUE_EFFECT, 1);
        ensureWithdrawalEffect(player, SLOWNESS_EFFECT, 0);
    }

    private static void ensureWithdrawalEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > WITHDRAWAL_REAPPLY_THRESHOLD_TICKS) {
            return;
        }
        player.addEffect(new MobEffectInstance(effect, WITHDRAWAL_REFRESH_DURATION_TICKS, amplifier, false, true, true));
    }

    private static void clearWithdrawalEffects(ServerPlayer player) {
        player.removeEffect(MobEffects.NAUSEA);
        player.removeEffect(MobEffects.WEAKNESS);
        player.removeEffect(MINING_FATIGUE_EFFECT);
        player.removeEffect(SLOWNESS_EFFECT);
    }

    private static long resolveAddictionDurationTicks() {
        RaceConfig.RaceAbilityConfig config = getCartelShnyagaConfig();
        double seconds = config != null && config.methadoneAddictionSeconds > 0.0D
                ? config.methadoneAddictionSeconds
                : DEFAULT_ADDICTION_SECONDS;
        return Math.max(1L, Math.round(seconds * 20.0D));
    }

    private static long resolveWithdrawalStartRemainingTicks(long addictionDurationTicks) {
        RaceConfig.RaceAbilityConfig config = getCartelShnyagaConfig();
        double seconds = config != null && config.methadoneWithdrawalStartSeconds > 0.0D
                ? config.methadoneWithdrawalStartSeconds
                : DEFAULT_WITHDRAWAL_START_SECONDS;
        long ticks = Math.max(0L, Math.round(seconds * 20.0D));
        return Math.min(addictionDurationTicks, ticks);
    }

    private static RaceConfig.RaceAbilityConfig getCartelShnyagaConfig() {
        RaceConfig.ConfigData configData = RaceConfig.get();
        if (configData == null || configData.races == null) {
            return null;
        }

        for (RaceConfig.PlayerRaceConfig race : configData.races) {
            if (race != null && MISTER_CARTEL_49_RACE_ID.equals(race.id) && race.shnyaga != null) {
                return race.shnyaga;
            }
        }
        return null;
    }

    private static void playUseSound(ServerPlayer consumer) {
        if (consumer == null || !(consumer.level() instanceof ServerLevel level)) {
            return;
        }

        Vec3 look = consumer.getLookAngle();
        if (look.lengthSqr() < 1.0E-6D) {
            look = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            look = look.normalize();
        }
        Vec3 origin = consumer.getEyePosition().add(look.scale(0.12D));
        long seed = level.getRandom().nextLong();
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(origin.x, origin.y, origin.z) > SOUND_MAX_DISTANCE_SQR) {
                continue;
            }

            boolean hasPack = PolymerResourcePackUtils.hasMainPack(viewer);
            Holder<SoundEvent> sound = hasPack
                    ? METHADONE_USE_SOUND
                    : BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SCULK_BLOCK_CHARGE);
            float pitch = hasPack ? PACK_SOUND_PITCH : FALLBACK_SOUND_PITCH;
            viewer.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, origin.x, origin.y, origin.z, SOUND_VOLUME, pitch, seed));
        }
    }

    private static void refreshAcidSky(ServerPlayer player, int durationTicks) {
        if (player == null || durationTicks <= 0) {
            return;
        }

        MethadoneSkyState state = ACID_SKY_STATES.computeIfAbsent(player.getUUID(), ignored -> new MethadoneSkyState());
        state.remainingTicks = durationTicks;
        state.trackedChunkRefreshTicks = ACID_SKY_TRACKED_CHUNK_REFRESH_INTERVAL_TICKS;
        state.nextForcedTimeTick = Long.MIN_VALUE;
    }

    private static void updateAcidSky(ServerPlayer player, MethadoneSkyState state) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.dimensionType().hasSkyLight()) {
            if (state.timeForcedActive) {
                restoreActualTime(player, level);
                state.timeForcedActive = false;
            }
            state.overriddenDimension = level.dimension();
            state.overriddenChunks.clear();
            state.overriddenChunkKeys = EMPTY_CHUNK_KEYS;
            state.overriddenChunkPositions = EMPTY_CHUNK_POSITIONS;
            state.overriddenSectionCount = 0;
            state.nextChunkBatchIndex = 0;
            state.currentPhaseIndex = 0;
            state.phaseTicks = 0;
            state.lastTrackedCenter = null;
            return;
        }

        tickForcedTime(player, level, state);

        ChunkPos currentCenter = player.chunkPosition();
        boolean dimensionChanged = state.overriddenDimension != null && !state.overriddenDimension.equals(level.dimension());
        boolean centerChanged = state.lastTrackedCenter == null || !state.lastTrackedCenter.equals(currentCenter);
        state.trackedChunkRefreshTicks++;
        boolean shouldRefreshTrackedChunks = dimensionChanged
                || centerChanged
                || state.overriddenChunks.isEmpty()
                || state.trackedChunkRefreshTicks >= ACID_SKY_TRACKED_CHUNK_REFRESH_INTERVAL_TICKS;

        Set<Long> currentChunks = state.overriddenChunks;
        long[] currentChunkKeys = state.overriddenChunkKeys;
        boolean chunkSetChanged = false;
        if (shouldRefreshTrackedChunks) {
            Set<Long> refreshedChunks = collectTrackedChunkKeys(player);
            if (!dimensionChanged) {
                restoreRemovedChunks(player, level, state.overriddenChunks, refreshedChunks);
            } else {
                state.lastTrackedCenter = null;
            }
            chunkSetChanged = !state.overriddenChunks.equals(refreshedChunks);
            if (chunkSetChanged) {
                state.overriddenChunks.clear();
                state.overriddenChunks.addAll(refreshedChunks);
                state.overriddenChunkKeys = toChunkKeyArray(refreshedChunks);
                state.overriddenChunkPositions = toChunkPosArray(state.overriddenChunkKeys, currentCenter);
                state.overriddenSectionCount = level.getSectionsCount();
                state.nextChunkBatchIndex = 0;
            }
            state.lastTrackedCenter = currentCenter;
            state.trackedChunkRefreshTicks = 0;
            currentChunks = state.overriddenChunks;
            currentChunkKeys = state.overriddenChunkKeys;
        }

        if (dimensionChanged) {
            state.currentPhaseIndex = 0;
            state.phaseTicks = 0;
            state.nextChunkBatchIndex = 0;
        }

        state.overriddenDimension = level.dimension();
        if (currentChunkKeys.length == 0) {
            return;
        }

        if (chunkSetChanged && state.nextChunkBatchIndex == 0) {
            state.phaseTicks = 0;
        }

        if (state.nextChunkBatchIndex < state.overriddenChunkPositions.length) {
            state.nextChunkBatchIndex += sendAcidBiomeOverrideBatch(
                    player,
                    level,
                    state.overriddenChunkPositions,
                    state.overriddenSectionCount,
                    ACID_SKY_BIOME_KEYS.get(state.currentPhaseIndex),
                    state.nextChunkBatchIndex,
                    ACID_SKY_CHUNK_BATCH_SIZE
            );
            return;
        }

        state.phaseTicks++;
        if (state.phaseTicks < ACID_SKY_PHASE_INTERVAL_TICKS) {
            return;
        }

        state.phaseTicks = 0;
        state.currentPhaseIndex = (state.currentPhaseIndex + 1) % ACID_SKY_BIOME_KEYS.size();
        state.nextChunkBatchIndex = sendAcidBiomeOverrideBatch(
                player,
                level,
                state.overriddenChunkPositions,
                state.overriddenSectionCount,
                ACID_SKY_BIOME_KEYS.get(state.currentPhaseIndex),
                0,
                ACID_SKY_CHUNK_BATCH_SIZE
        );
    }

    private static int sendAcidBiomeOverrideBatch(
            ServerPlayer player,
            ServerLevel level,
            ChunkPos[] chunkPositions,
            int sectionCount,
            ResourceKey<Biome> biomeKey,
            int startIndex,
            int batchSize
    ) {
        if (chunkPositions.length == 0 || sectionCount <= 0 || startIndex >= chunkPositions.length || batchSize <= 0) {
            return 0;
        }

        byte[] serializedBiomes = getCachedUniformBiomePayload(level, sectionCount, biomeKey);
        if (serializedBiomes.length == 0) {
            return 0;
        }

        int endIndex = Math.min(chunkPositions.length, startIndex + batchSize);
        List<ClientboundChunksBiomesPacket.ChunkBiomeData> biomeData = new ArrayList<>(endIndex - startIndex);
        for (int index = startIndex; index < endIndex; index++) {
            ChunkPos chunkPos = chunkPositions[index];
            biomeData.add(new ClientboundChunksBiomesPacket.ChunkBiomeData(chunkPos, serializedBiomes));
        }
        player.connection.send(new ClientboundChunksBiomesPacket(biomeData));
        return endIndex - startIndex;
    }

    private static void tickForcedTime(ServerPlayer player, ServerLevel level, MethadoneSkyState state) {
        long nowTick = level.getGameTime();
        if (state.timeForcedActive && nowTick < state.nextForcedTimeTick) {
            return;
        }

        sendForcedMethadoneTime(player);
        state.timeForcedActive = true;
        state.nextForcedTimeTick = nowTick + METHADONE_FORCED_TIME_INTERVAL_TICKS;
    }

    private static byte[] getCachedUniformBiomePayload(ServerLevel level, int sectionCount, ResourceKey<Biome> biomeKey) {
        AcidSkyPayloadKey cacheKey = new AcidSkyPayloadKey(sectionCount, biomeKey);
        byte[] cached = ACID_SKY_PAYLOAD_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Holder<Biome> biomeHolder = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biomeKey);
        PalettedContainerFactory containerFactory = PalettedContainerFactory.create(level.registryAccess());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            int biomeSize = 1 << LevelChunkSection.BIOME_CONTAINER_BITS;
            for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
                PalettedContainer<Holder<Biome>> biomes = containerFactory.createForBiomes();
                for (int x = 0; x < biomeSize; x++) {
                    for (int y = 0; y < biomeSize; y++) {
                        for (int z = 0; z < biomeSize; z++) {
                            biomes.set(x, y, z, biomeHolder);
                        }
                    }
                }
                biomes.write(buffer);
            }

            byte[] payload = new byte[buffer.readableBytes()];
            buffer.getBytes(0, payload);
            ACID_SKY_PAYLOAD_CACHE.put(cacheKey, payload);
            return payload;
        } finally {
            buffer.release();
        }
    }

    private static void restoreOriginalBiomes(ServerPlayer player, MethadoneSkyState state) {
        if (player == null || state == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (state.timeForcedActive) {
            restoreActualTime(player, level);
            state.timeForcedActive = false;
        }
        if (state.overriddenDimension == null || !state.overriddenDimension.equals(level.dimension()) || state.overriddenChunks.isEmpty()) {
            return;
        }

        List<LevelChunk> chunks = collectChunksToSend(level, state.overriddenChunkKeys);
        if (!chunks.isEmpty()) {
            player.connection.send(ClientboundChunksBiomesPacket.forChunks(chunks));
        }
    }

    private static void sendForcedMethadoneTime(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        player.connection.send(new ClientboundSetTimePacket(
                level.getGameTime(),
                METHADONE_FORCED_DAY_TIME,
                false
        ));
    }

    private static void restoreActualTime(ServerPlayer player, ServerLevel level) {
        if (player == null || level == null) {
            return;
        }

        player.connection.send(new ClientboundSetTimePacket(
                level.getGameTime(),
                level.getDayTime(),
                level.getGameRules().get(GameRules.ADVANCE_TIME)
        ));
    }

    private static void restoreRemovedChunks(ServerPlayer player, ServerLevel level, Set<Long> previousChunks, Set<Long> currentChunks) {
        if (previousChunks.isEmpty()) {
            return;
        }

        List<LevelChunk> chunksToRestore = new ArrayList<>();
        for (long chunkKey : previousChunks) {
            if (currentChunks.contains(chunkKey)) {
                continue;
            }

            LevelChunk chunk = level.getChunkSource().chunkMap.getChunkToSend(chunkKey);
            if (chunk == null) {
                chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            }
            if (chunk != null) {
                chunksToRestore.add(chunk);
            }
        }

        if (!chunksToRestore.isEmpty()) {
            player.connection.send(ClientboundChunksBiomesPacket.forChunks(chunksToRestore));
        }
    }

    private static List<LevelChunk> collectChunksToSend(ServerLevel level, long[] chunkKeys) {
        if (chunkKeys.length == 0) {
            return List.of();
        }

        List<LevelChunk> chunks = new ArrayList<>(chunkKeys.length);
        for (long chunkKey : chunkKeys) {
            LevelChunk chunk = level.getChunkSource().chunkMap.getChunkToSend(chunkKey);
            if (chunk == null) {
                chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            }
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    private static long[] toChunkKeyArray(Set<Long> chunkKeys) {
        if (chunkKeys.isEmpty()) {
            return EMPTY_CHUNK_KEYS;
        }

        long[] result = new long[chunkKeys.size()];
        int index = 0;
        for (long chunkKey : chunkKeys) {
            result[index++] = chunkKey;
        }
        return result;
    }

    private static ChunkPos[] toChunkPosArray(long[] chunkKeys, ChunkPos center) {
        if (chunkKeys.length == 0) {
            return EMPTY_CHUNK_POSITIONS;
        }

        ChunkPos[] result = new ChunkPos[chunkKeys.length];
        for (int index = 0; index < chunkKeys.length; index++) {
            long chunkKey = chunkKeys[index];
            result[index] = new ChunkPos(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
        }
        if (center != null && result.length > 1) {
            java.util.Arrays.sort(result, java.util.Comparator.comparingInt((ChunkPos chunkPos) -> {
                int dx = chunkPos.x - center.x;
                int dz = chunkPos.z - center.z;
                return (dx * dx) + (dz * dz);
            }).thenComparingInt(chunkPos -> chunkPos.x).thenComparingInt(chunkPos -> chunkPos.z));

            ChunkPos[] interleaved = new ChunkPos[result.length];
            int low = 0;
            int high = result.length - 1;
            int writeIndex = 0;
            while (low <= high) {
                interleaved[writeIndex++] = result[high--];
                if (low <= high) {
                    interleaved[writeIndex++] = result[low++];
                }
            }
            return interleaved;
        }
        return result;
    }

    private static Set<Long> collectTrackedChunkKeys(ServerPlayer player) {
        Set<Long> chunkKeys = new HashSet<>();
        if (player == null) {
            return chunkKeys;
        }

        player.getChunkTrackingView().forEach(chunkPos -> chunkKeys.add(chunkPos.toLong()));
        return chunkKeys;
    }

    private static ResourceKey<Biome> acidSkyBiomeKey(String path) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Lg2.MOD_ID, path));
    }

    private static boolean isMrCartel(ServerPlayer player) {
        Optional<RaceConfig.PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
        return raceOptional.isPresent()
                && MISTER_CARTEL_49_RACE_ID.equals(raceOptional.get().id)
                && ServerRaceSystem.hasUnlockedAbility(player, RaceAbilitySlot.SHNYAGA);
    }

    private static MutableComponent getLocalizedName(PacketContext context) {
        ServerPlayer player = context.getPlayer();
        if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
            return Component.literal("Methadone");
        }

        String normalized = player.clientInformation().language().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("rpr")) {
            return Component.literal("\u0414\u0440\u0435\u043c\u0430\u0442\u0438\u043d\u044a \u0440\u0443\u0441\u0441\u043a\u0456\u0439");
        }
        if (normalized.startsWith("uk")) {
            return Component.literal("\u041c\u0435\u0442\u0430\u0434\u043e\u043d");
        }
        if (normalized.startsWith("ru")) {
            return Component.literal("\u041c\u0435\u0442\u0430\u0434\u043e\u043d");
        }
        if (normalized.startsWith("ja")) {
            return Component.literal("\u30e1\u30bf\u30c9\u30f3");
        }
        return Component.literal("Methadone");
    }

    private static final class MethadoneAddictionState {
        private long remainingTicks;
        private long withdrawalStartRemainingTicks;
        private int doseCount;
        private boolean withdrawalActive;
    }

    private static final class MethadoneSkyState {
        private long remainingTicks;
        private int phaseTicks;
        private int currentPhaseIndex;
        private ResourceKey<Level> overriddenDimension;
        private ChunkPos lastTrackedCenter;
        private int trackedChunkRefreshTicks = ACID_SKY_TRACKED_CHUNK_REFRESH_INTERVAL_TICKS;
        private final Set<Long> overriddenChunks = new HashSet<>();
        private long[] overriddenChunkKeys = EMPTY_CHUNK_KEYS;
        private ChunkPos[] overriddenChunkPositions = EMPTY_CHUNK_POSITIONS;
        private int overriddenSectionCount;
        private int nextChunkBatchIndex;
        private long nextForcedTimeTick = Long.MIN_VALUE;
        private boolean timeForcedActive;
    }

    private record AcidSkyPayloadKey(int sectionCount, ResourceKey<Biome> biomeKey) {
    }
}
