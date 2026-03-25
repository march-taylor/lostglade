package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.config.RaceConfig;
import com.lostglade.server.ServerRaceSystem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Optional;

public final class TubochkaItem extends SimplePolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "tubochka");
	private static final Identifier LIT_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "tubochka_lit");
	private static final Identifier FALLBACK_LIT_MODEL_ID = Identifier.fromNamespaceAndPath("minecraft", "torch");
	private static final String MISTER_CARTEL_49_RACE_ID = "mister_cartel_49";
	private static final String META_TAG = "lg2_tubochka";
	private static final String LIT_TAG = "lit";
	private static final String START_TICK_TAG = "start_tick";
	private static final String END_TICK_TAG = "end_tick";
	private static final String BAR_WIDTH_TAG = "bar_width";
	private static final String LAST_TICK_TAG = "last_tick";
	private static final String TOTAL_TICKS_TAG = "total_ticks";
	private static final String REMAINING_TICKS_TAG = "remaining_ticks";
	private static final double DEFAULT_BURN_SECONDS = 120.0D;
	private static final int HELD_SMOKE_INTERVAL_TICKS = 8;
	private static final int CHARGE_TICKS_PER_RELEASE_PARTICLE = 10;
	private static final int DEFAULT_MAX_RELEASE_SMOKE_PARTICLES = 8;
	private static final int BAR_COLOR = 0xFF9A00;
	private static final int BAR_SEGMENTS = 13;

	public TubochkaItem(Item.Properties settings) {
		super(settings, Items.STICK);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.literal("Tubochka");
	}

	@Override
	public Item getPolymerItem(ItemStack itemStack, PacketContext context) {
		return isLit(itemStack) ? Items.BOW : Items.STICK;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		return isLit(itemStack) ? LIT_MODEL_ID : MODEL_ID;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		out.set(DataComponents.CUSTOM_NAME, getLocalizedName(context).withStyle(style -> style.withItalic(false)));
		if (isLit(original)) {
			if (!PolymerResourcePackUtils.hasMainPack(context)) {
				out.set(DataComponents.ITEM_MODEL, FALLBACK_LIT_MODEL_ID);
			}
			out.remove(DataComponents.INSTRUMENT);
			Integer maxDamage = original.get(DataComponents.MAX_DAMAGE);
			Integer damage = original.get(DataComponents.DAMAGE);
			if (maxDamage != null && maxDamage > 0) {
				out.set(DataComponents.MAX_DAMAGE, maxDamage);
				out.set(DataComponents.DAMAGE, damage == null ? 0 : Math.max(0, Math.min(maxDamage, damage)));
			} else {
				out.remove(DataComponents.MAX_DAMAGE);
				out.remove(DataComponents.DAMAGE);
			}
		} else {
			out.remove(DataComponents.INSTRUMENT);
			out.remove(DataComponents.MAX_DAMAGE);
			out.remove(DataComponents.DAMAGE);
		}
	}

	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return !(oldStack.is(ModItems.TUBOCHKA) && newStack.is(ModItems.TUBOCHKA) && isLit(oldStack) && isLit(newStack));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (isLit(stack)) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player != null && isLit(context.getItemInHand())) {
			player.startUsingItem(context.getHand());
			return InteractionResult.CONSUME;
		}
		return InteractionResult.FAIL;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return isLit(stack) ? ItemUseAnimation.BOW : ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return isLit(stack) ? 72000 : 0;
	}

	@Override
	public boolean useOnRelease(ItemStack stack) {
		return isLit(stack);
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeChargedLeft) {
		if (!(level instanceof ServerLevel serverLevel) || !isLit(stack)) {
			return false;
		}

		int usedTicks = Math.max(0, getUseDuration(stack, entity) - timeChargedLeft);
		int particleCount = Math.min(resolveMaxReleaseSmokeParticles(entity), usedTicks / CHARGE_TICKS_PER_RELEASE_PARTICLE);
		if (particleCount <= 0) {
			return true;
		}

		Vec3 origin = getHeldSmokeOrigin(entity, resolveTubochkaHandSlot(entity, stack), true);
		serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, origin.x, origin.y, origin.z, particleCount, 0.05D, 0.02D, 0.05D, 0.01D);
		return true;
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		return stack;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return isLit(stack) && stack.has(DataComponents.MAX_DAMAGE);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return isLit(stack) ? computeBarWidth(getRemainingTicks(stack, 0L), getLongMetadata(stack, TOTAL_TICKS_TAG)) : 0;
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR_COLOR;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		super.inventoryTick(stack, level, entity, slot);
		if (!isLit(stack)) {
			return;
		}

		long nowTick = level.getGameTime();
		migrateLegacyLitState(stack, nowTick);
		long remainingTicks = getRemainingTicks(stack, nowTick);
		if (remainingTicks <= 0L) {
			if (entity instanceof LivingEntity livingEntity && livingEntity.getUseItem() == stack) {
				livingEntity.stopUsingItem();
			}
			stack.shrink(1);
			return;
		}

		int barWidth = computeBarWidth(remainingTicks, getLongMetadata(stack, TOTAL_TICKS_TAG));
		if (barWidth != getIntMetadata(stack, BAR_WIDTH_TAG)) {
			updateSyncedBarState(stack, barWidth);
		}

		if (entity instanceof LivingEntity livingEntity && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)) {
			emitHeldSmoke(level, livingEntity, slot);
		}
	}

	public static boolean tryLightTubochka(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		if (player.getMainHandItem().is(ModItems.TUBOCHKA) && player.getOffhandItem().is(Items.FLINT_AND_STEEL)) {
			return tryLightTubochka(player, InteractionHand.MAIN_HAND);
		}
		if (player.getOffhandItem().is(ModItems.TUBOCHKA) && player.getMainHandItem().is(Items.FLINT_AND_STEEL)) {
			return tryLightTubochka(player, InteractionHand.OFF_HAND);
		}
		return false;
	}

	private static boolean tryLightTubochka(ServerPlayer player, InteractionHand tubochkaHand) {
		Optional<RaceConfig.PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
		if (raceOptional.isEmpty()) {
			return false;
		}

		RaceConfig.PlayerRaceConfig race = raceOptional.get();
		if (!MISTER_CARTEL_49_RACE_ID.equals(race.id) || race.shnyaga == null || !race.shnyaga.enabled) {
			return false;
		}

		ItemStack tubochkaStack = player.getItemInHand(tubochkaHand);
		if (!tubochkaStack.is(ModItems.TUBOCHKA) || isLit(tubochkaStack)) {
			return false;
		}

		InteractionHand lighterHand = tubochkaHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack lighterStack = player.getItemInHand(lighterHand);
		if (!lighterStack.is(Items.FLINT_AND_STEEL)) {
			return false;
		}

		long totalTicks = Math.max(1L, Math.round((race.shnyaga.tubochkaBurnSeconds > 0.0D ? race.shnyaga.tubochkaBurnSeconds : DEFAULT_BURN_SECONDS) * 20.0D));
		if (tubochkaStack.getCount() > 1) {
			ItemStack remainder = tubochkaStack.copyWithCount(tubochkaStack.getCount() - 1);
			tubochkaStack.setCount(1);
			setLitState(tubochkaStack, totalTicks, player.level().getGameTime());
			if (!player.getInventory().add(remainder)) {
				player.drop(remainder, false);
			}
		} else {
			setLitState(tubochkaStack, totalTicks, player.level().getGameTime());
		}

		lighterStack.hurtAndBreak(1, player, lighterHand);
		player.containerMenu.broadcastChanges();
		return true;
	}

	private static boolean isLit(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CompoundTag tubochkaTag = getTubochkaTag(stack);
		return tubochkaTag != null && tubochkaTag.getBooleanOr(LIT_TAG, false);
	}

	private static void setLitState(ItemStack stack, long totalTicks, long nowTick) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag tubochkaTag = tag.getCompoundOrEmpty(META_TAG);
			tubochkaTag.putBoolean(LIT_TAG, true);
			tubochkaTag.putLong(START_TICK_TAG, nowTick);
			tubochkaTag.putLong(END_TICK_TAG, nowTick + totalTicks);
			tubochkaTag.putLong(TOTAL_TICKS_TAG, totalTicks);
			tubochkaTag.putInt(BAR_WIDTH_TAG, BAR_SEGMENTS);
			tag.put(META_TAG, tubochkaTag);
		});
		setStackBarComponents(stack, BAR_SEGMENTS);
	}

	private static void clearLitState(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(META_TAG));
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData != null && customData.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		}
		stack.remove(DataComponents.MAX_DAMAGE);
		stack.remove(DataComponents.DAMAGE);
	}

	private static long getLongMetadata(ItemStack stack, String key) {
		CompoundTag tubochkaTag = getTubochkaTag(stack);
		return tubochkaTag == null ? 0L : tubochkaTag.getLongOr(key, 0L);
	}

	private static int getIntMetadata(ItemStack stack, String key) {
		CompoundTag tubochkaTag = getTubochkaTag(stack);
		return tubochkaTag == null ? 0 : tubochkaTag.getIntOr(key, 0);
	}

	private static CompoundTag getTubochkaTag(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || customData.isEmpty()) {
			return null;
		}

		CompoundTag rootTag = customData.copyTag();
		if (!rootTag.contains(META_TAG)) {
			return null;
		}
		return rootTag.getCompoundOrEmpty(META_TAG);
	}

	private static long getRemainingTicks(ItemStack stack, long nowTick) {
		long endTick = getLongMetadata(stack, END_TICK_TAG);
		if (endTick > 0L) {
			return Math.max(0L, endTick - nowTick);
		}
		return Math.max(0L, getLongMetadata(stack, REMAINING_TICKS_TAG));
	}

	private static int computeBarWidth(long remainingTicks, long totalTicks) {
		long normalizedTotal = Math.max(1L, totalTicks);
		if (remainingTicks <= 0L) {
			return 0;
		}
		return Math.max(1, Math.min(BAR_SEGMENTS, (int) Math.round((double) BAR_SEGMENTS * (double) remainingTicks / (double) normalizedTotal)));
	}

	private static void updateSyncedBarState(ItemStack stack, int barWidth) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag tubochkaTag = tag.getCompoundOrEmpty(META_TAG);
			tubochkaTag.putInt(BAR_WIDTH_TAG, Math.max(0, Math.min(BAR_SEGMENTS, barWidth)));
			tag.put(META_TAG, tubochkaTag);
		});
		setStackBarComponents(stack, barWidth);
	}

	private static void setStackBarComponents(ItemStack stack, int barWidth) {
		int clampedBarWidth = Math.max(0, Math.min(BAR_SEGMENTS, barWidth));
		stack.set(DataComponents.MAX_DAMAGE, BAR_SEGMENTS);
		stack.set(DataComponents.DAMAGE, BAR_SEGMENTS - clampedBarWidth);
	}

	private static void migrateLegacyLitState(ItemStack stack, long nowTick) {
		if (getLongMetadata(stack, END_TICK_TAG) > 0L) {
			return;
		}
		long totalTicks = Math.max(1L, getLongMetadata(stack, TOTAL_TICKS_TAG));
		long remainingTicks = Math.max(0L, getLongMetadata(stack, REMAINING_TICKS_TAG));
		if (remainingTicks <= 0L) {
			return;
		}
		int barWidth = computeBarWidth(remainingTicks, totalTicks);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag tubochkaTag = tag.getCompoundOrEmpty(META_TAG);
			tubochkaTag.putLong(START_TICK_TAG, nowTick - (totalTicks - remainingTicks));
			tubochkaTag.putLong(END_TICK_TAG, nowTick + remainingTicks);
			tubochkaTag.putLong(TOTAL_TICKS_TAG, totalTicks);
			tubochkaTag.putInt(BAR_WIDTH_TAG, barWidth);
			tubochkaTag.remove(LAST_TICK_TAG);
			tubochkaTag.remove(REMAINING_TICKS_TAG);
			tag.put(META_TAG, tubochkaTag);
		});
		setStackBarComponents(stack, barWidth);
	}

	private static void emitHeldSmoke(ServerLevel level, LivingEntity livingEntity, EquipmentSlot slot) {
		long nowTick = level.getGameTime();
		if ((nowTick + livingEntity.getId() + slot.ordinal()) % HELD_SMOKE_INTERVAL_TICKS != 0L) {
			return;
		}

		Vec3 origin = getHeldSmokeOrigin(livingEntity, slot, false);
		level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 1, 0.015D, 0.02D, 0.015D, 0.003D);
	}

	private static Vec3 getHeldSmokeOrigin(LivingEntity livingEntity, EquipmentSlot slot, boolean raisedUsePose) {
		Vec3 look = livingEntity.getLookAngle();
		if (look.lengthSqr() < 1.0E-6D) {
			look = new Vec3(0.0D, 0.0D, 1.0D);
		} else {
			look = look.normalize();
		}

		Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(look);
		if (right.lengthSqr() < 1.0E-6D) {
			right = new Vec3(1.0D, 0.0D, 0.0D);
		} else {
			right = right.normalize();
		}

		HumanoidArm hand = slot == EquipmentSlot.MAINHAND
				? livingEntity.getMainArm()
				: (livingEntity.getMainArm() == HumanoidArm.RIGHT ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
		double sideOffset = hand == HumanoidArm.RIGHT ? 0.18D : -0.18D;
		double forwardOffset = raisedUsePose ? 0.26D : 0.34D;
		double verticalOffset = raisedUsePose ? -0.28D : -0.42D;
		double raisedSideScale = raisedUsePose ? 0.72D : 1.0D;
		return livingEntity.getEyePosition().add(look.scale(forwardOffset)).add(right.scale(sideOffset * raisedSideScale)).add(0.0D, verticalOffset, 0.0D);
	}

	private static EquipmentSlot resolveTubochkaHandSlot(LivingEntity livingEntity, ItemStack stack) {
		return livingEntity.getOffhandItem() == stack ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
	}

	private static int resolveMaxReleaseSmokeParticles(LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			Optional<RaceConfig.PlayerRaceConfig> raceOptional = ServerRaceSystem.getRace(player);
			if (raceOptional.isPresent() && raceOptional.get().shnyaga != null) {
				return Math.max(0, (int) Math.round(raceOptional.get().shnyaga.tubochkaMaxReleaseSmokeParticles));
			}
		}
		return DEFAULT_MAX_RELEASE_SMOKE_PARTICLES;
	}

	private static MutableComponent getLocalizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal("Tubochka");
		}

		String lang = player.clientInformation().language();
		if (lang == null) {
			return Component.literal("Tubochka");
		}

		String normalized = lang.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal("Трубочка");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal("Трубочка");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal("Трубочка");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal("筒");
		}
		return Component.literal("Tubochka");
	}
}
