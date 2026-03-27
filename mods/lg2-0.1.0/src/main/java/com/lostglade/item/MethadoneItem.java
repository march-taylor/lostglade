package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.server.ServerRaceSystem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    private static final Consumable CLIENT_USE_CONSUMABLE = Consumable.builder()
            .consumeSeconds(0.5F)
            .animation(ItemUseAnimation.TOOT_HORN)
            .sound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY))
            .hasConsumeParticles(false)
            .build();
    private static final Map<UUID, MethadoneAddictionState> ADDICTION_STATES = new HashMap<>();

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

        long nowTick = server.overworld().getGameTime();
        ADDICTION_STATES.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            MethadoneAddictionState state = entry.getValue();
            if (playerId == null || state == null) {
                return true;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (state.expireTick <= nowTick) {
                if (player != null) {
                    clearWithdrawalEffects(player);
                }
                return true;
            }

            if (player != null && nowTick >= state.withdrawalStartTick) {
                ensureWithdrawalEffects(player);
            }
            return false;
        });
    }

    private static void applyUseEffects(ServerPlayer player) {
        if (isMrCartel(player)) {
            applyCartelBuffs(player, CARTEL_EFFECT_DURATION_TICKS);
            return;
        }

        long nowTick = ((ServerLevel) player.level()).getGameTime();
        MethadoneAddictionState previousState = ADDICTION_STATES.get(player.getUUID());
        boolean stillAddicted = previousState != null && previousState.expireTick > nowTick;
        int doseCount = stillAddicted ? previousState.doseCount + 1 : 1;
        long addictionDurationTicks = resolveAddictionDurationTicks();
        long withdrawalRemainingTicks = resolveWithdrawalStartRemainingTicks(addictionDurationTicks);
        long expireTick = nowTick + addictionDurationTicks;
        long withdrawalStartTick = expireTick - withdrawalRemainingTicks;

        ADDICTION_STATES.put(player.getUUID(), new MethadoneAddictionState(expireTick, withdrawalStartTick, doseCount));
        clearWithdrawalEffects(player);

        if (doseCount <= 3) {
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

    private static boolean isMrCartel(ServerPlayer player) {
        Optional<RaceConfig.PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
        return raceOptional.isPresent() && MISTER_CARTEL_49_RACE_ID.equals(raceOptional.get().id);
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

    private record MethadoneAddictionState(long expireTick, long withdrawalStartTick, int doseCount) {
    }
}
