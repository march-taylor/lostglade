package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.config.RaceConfig.RaceAbilitySlot;
import com.lostglade.server.CocaineHallucinationSystem;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CocaineItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "cocaine");
	private static final Identifier COCAINE_CONSUME_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "cocaine_consume");
	private static final Holder<SoundEvent> COCAINE_CONSUME_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(COCAINE_CONSUME_SOUND_ID));
	private static final String MISTER_CARTEL_49_RACE_ID = "mister_cartel_49";
	private static final int USE_DURATION_TICKS = 20;
	private static final int CARTEL_SPEED_TICKS = 20 * 20;
	private static final int CARTEL_REGEN_TICKS = 15 * 20;
	private static final int CARTEL_AFTERMATH_HUNGER_TICKS = 10 * 20;
	private static final int OTHER_NAUSEA_TICKS = 20 * 20;
	private static final int OTHER_HUNGER_TICKS = 15 * 20;
	private static final double DEFAULT_COCAINE_HALLUCINATION_CHANCE = 0.25D;
	private static final int FALLBACK_CONSUME_SOUND_DELAY_TICKS = 4;
	private static final int PACK_CONSUME_SOUND_DELAY_TICKS = 8;
	private static final int SOUND_MAX_DISTANCE_SQR = 3 * 3;
	private static final float SOUND_VOLUME = 0.27F;
	private static final float PACK_SOUND_PITCH = 1.0F;
	private static final float FALLBACK_SOUND_PITCH = 0.95F;
	private static final double HALLUCINATION_SOUND_MIN_DISTANCE = 4.0D;
	private static final double HALLUCINATION_SOUND_MAX_DISTANCE = 12.0D;
	private static final float HALLUCINATION_SOUND_VOLUME = 1.0F;
	private static final float HALLUCINATION_SOUND_PITCH_MIN = 0.70F;
	private static final float HALLUCINATION_SOUND_PITCH_MAX = 1.25F;
	private static final Consumable CLIENT_ANIM_CONSUMABLE = Consumable.builder()
			.consumeSeconds(1.0F)
			.animation(ItemUseAnimation.EAT)
			.sound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY))
			.hasConsumeParticles(false)
			.build();
	private static final List<SoundEvent> HALLUCINATION_SOUND_POOL = collectHallucinationSoundPool();
	private static final Map<UUID, Long> CARTEL_AFTERMATH_HUNGER_TICKS_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Long> CARTEL_COCAINE_SPRINT_TICKS_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Long> PENDING_FALLBACK_CONSUME_SOUND_TICKS_BY_PLAYER = new HashMap<>();
	private static final Map<UUID, Long> PENDING_PACK_CONSUME_SOUND_TICKS_BY_PLAYER = new HashMap<>();

	public CocaineItem(Item.Properties settings) {
		super(settings, Items.SUGAR);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Cocaine");
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		out.set(DataComponents.CUSTOM_NAME, getLocalizedName(context).withStyle(style -> style.withItalic(false)));
		out.set(DataComponents.CONSUMABLE, CLIENT_ANIM_CONSUMABLE);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
			long nowTick = serverLevel.getGameTime();
			PENDING_FALLBACK_CONSUME_SOUND_TICKS_BY_PLAYER.put(serverPlayer.getUUID(), nowTick + FALLBACK_CONSUME_SOUND_DELAY_TICKS);
			PENDING_PACK_CONSUME_SOUND_TICKS_BY_PLAYER.put(serverPlayer.getUUID(), nowTick + PACK_CONSUME_SOUND_DELAY_TICKS);
		}
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.EAT;
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
		}

		if (!level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
			applyConsumptionEffects(serverPlayer);
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

		CocaineHallucinationSystem.tick(server);
		long nowTick = server.overworld().getGameTime();
		CARTEL_COCAINE_SPRINT_TICKS_BY_PLAYER.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= nowTick);
		PENDING_FALLBACK_CONSUME_SOUND_TICKS_BY_PLAYER.entrySet().removeIf(entry -> {
			UUID playerId = entry.getKey();
			Long dueTick = entry.getValue();
			if (playerId == null || dueTick == null) {
				return true;
			}
			if (dueTick > nowTick) {
				return false;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof CocaineItem)) {
				return true;
			}

			playConsumeSound(player, false);
			return true;
		});
		PENDING_PACK_CONSUME_SOUND_TICKS_BY_PLAYER.entrySet().removeIf(entry -> {
			UUID playerId = entry.getKey();
			Long dueTick = entry.getValue();
			if (playerId == null || dueTick == null) {
				return true;
			}
			if (dueTick > nowTick) {
				return false;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof CocaineItem)) {
				return true;
			}

			playConsumeSound(player, true);
			return true;
		});
		if (CARTEL_AFTERMATH_HUNGER_TICKS_BY_PLAYER.isEmpty()) {
			return;
		}
		CARTEL_AFTERMATH_HUNGER_TICKS_BY_PLAYER.entrySet().removeIf(entry -> {
			UUID playerId = entry.getKey();
			Long dueTick = entry.getValue();
			if (playerId == null || dueTick == null) {
				return true;
			}
			if (dueTick > nowTick) {
				return false;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				return false;
			}
			if (player.hasEffect(MobEffects.SPEED) || player.hasEffect(MobEffects.REGENERATION)) {
				entry.setValue(nowTick + 1L);
				return false;
			}

			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, CARTEL_AFTERMATH_HUNGER_TICKS, 29));
			return true;
		});
	}

	private static void applyConsumptionEffects(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return;
		}

		if (isMrCartel(player)) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, CARTEL_SPEED_TICKS, 1));
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, CARTEL_REGEN_TICKS, 0));
			long nowTick = ((ServerLevel) player.level()).getGameTime();
			CARTEL_COCAINE_SPRINT_TICKS_BY_PLAYER.put(player.getUUID(), nowTick + CARTEL_SPEED_TICKS);
			CARTEL_AFTERMATH_HUNGER_TICKS_BY_PLAYER.put(
					player.getUUID(),
					nowTick + Math.max(CARTEL_SPEED_TICKS, CARTEL_REGEN_TICKS)
			);
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, OTHER_NAUSEA_TICKS, 1));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, OTHER_HUNGER_TICKS, 49));
		}

		tryTriggerHallucination(player, level);
	}

	private static void playConsumeSound(ServerPlayer consumer, boolean packViewers) {
		if (consumer == null || !(consumer.level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 origin = consumer.getEyePosition().add(consumer.getLookAngle().normalize().scale(0.15D));
		long seed = level.getRandom().nextLong();
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(origin.x, origin.y, origin.z) > SOUND_MAX_DISTANCE_SQR) {
				continue;
			}

			boolean hasPack = PolymerResourcePackUtils.hasMainPack(viewer);
			if (hasPack != packViewers) {
				continue;
			}

			Holder<SoundEvent> sound = packViewers
					? COCAINE_CONSUME_SOUND
					: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SNIFFER_SCENTING);
			float pitch = hasPack ? PACK_SOUND_PITCH : FALLBACK_SOUND_PITCH;
			viewer.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, origin.x, origin.y, origin.z, SOUND_VOLUME, pitch, seed));
		}
	}

	private static boolean isMrCartel(ServerPlayer player) {
		Optional<RaceConfig.PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
		return raceOptional.isPresent()
				&& MISTER_CARTEL_49_RACE_ID.equals(raceOptional.get().id)
				&& ServerRaceSystem.hasUnlockedAbility(player, RaceAbilitySlot.SHNYAGA);
	}

	private static void tryTriggerHallucination(ServerPlayer player, ServerLevel level) {
		double chance = resolveHallucinationChance();
		if (chance <= 0.0D || level.random.nextDouble() >= chance) {
			return;
		}

		CocaineHallucinationSystem.spawn(player, level.random);
		playHallucinationSound(player, level);
	}

	private static double resolveHallucinationChance() {
		RaceConfig.RaceAbilityConfig config = getCartelShnyagaConfig();
		if (config != null) {
			return config.cocaineHallucinationChance;
		}
		return DEFAULT_COCAINE_HALLUCINATION_CHANCE;
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

	private static void playHallucinationSound(ServerPlayer player, ServerLevel level) {
		if (player == null || level == null || HALLUCINATION_SOUND_POOL.isEmpty()) {
			return;
		}

		SoundEvent sound = HALLUCINATION_SOUND_POOL.get(level.random.nextInt(HALLUCINATION_SOUND_POOL.size()));
		double angle = level.random.nextDouble() * Math.PI * 2.0D;
		double distance = sampleRange(level.random.nextDouble(), HALLUCINATION_SOUND_MIN_DISTANCE, HALLUCINATION_SOUND_MAX_DISTANCE);
		double x = player.getX() + Math.cos(angle) * distance;
		double z = player.getZ() + Math.sin(angle) * distance;
		double y = player.getY() + sampleRange(level.random.nextDouble(), -1.5D, 1.5D);
		float pitch = (float) sampleRange(level.random.nextDouble(), HALLUCINATION_SOUND_PITCH_MIN, HALLUCINATION_SOUND_PITCH_MAX);
		player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
				SoundSource.MASTER,
				x,
				y,
				z,
				HALLUCINATION_SOUND_VOLUME,
				pitch,
				level.random.nextLong()
		));
	}

	private static List<SoundEvent> collectHallucinationSoundPool() {
		List<SoundEvent> sounds = new ArrayList<>();
		for (SoundEvent soundEvent : BuiltInRegistries.SOUND_EVENT) {
			Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(soundEvent);
			if (id != null && "minecraft".equals(id.getNamespace())) {
				sounds.add(soundEvent);
			}
		}
		return sounds;
	}

	private static double sampleRange(double normalized, double min, double max) {
		if (max <= min) {
			return min;
		}
		return min + (Math.max(0.0D, Math.min(1.0D, normalized)) * (max - min));
	}

	public static boolean canCartelSprintDespiteHunger(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
			return false;
		}
		Long untilTick = CARTEL_COCAINE_SPRINT_TICKS_BY_PLAYER.get(serverPlayer.getUUID());
		return untilTick != null && untilTick > level.getGameTime();
	}

	private static MutableComponent getLocalizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Cocaine");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Cocaine");
		}

		String normalized = lang.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("rpr")) {
			return Component.literal("Прахъ оживляющій");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Кокаїн");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Кокаин");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("コカイン");
		}
		return Component.literal("Cocaine");
	}
}
