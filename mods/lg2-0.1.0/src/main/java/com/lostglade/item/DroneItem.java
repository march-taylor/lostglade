package com.lostglade.item;

import com.lostglade.Lg2;
import com.lostglade.server.DroneSystem;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import xyz.nucleoid.packettweaker.PacketContext;

public final class DroneItem extends SimplePolymerItem {
	private static final Identifier DEFAULT_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone");
	private static final Identifier DEFAULT_DISPLAY_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_display");
	private static final Identifier KAMIKAZE_DISPLAY_MODEL_ID_LEVEL_1 = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_kamikaze_display_1");
	private static final Identifier KAMIKAZE_DISPLAY_MODEL_ID_LEVEL_2 = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_kamikaze_display_2");
	private static final Identifier KAMIKAZE_DISPLAY_MODEL_ID_LEVEL_3 = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_kamikaze_display_3");
	private static final String DISPLAY_ROOT_TAG = "lg2_drone_display";
	private static final String DISPLAY_ONLY_TAG = "display_only";
	private static final String DATA_ROOT_TAG = "lg2_drone";
	private static final String KAMIKAZE_POWER_TAG = "kamikaze_power";
	private static final int NO_KAMIKAZE_POWER = 0;
	private static final int MIN_KAMIKAZE_POWER = 1;
	private static final int MAX_KAMIKAZE_POWER = 3;
	private final Identifier modelId;
	private final Identifier displayModelId;
	private final boolean kamikaze;

	public DroneItem(Item.Properties settings) {
		this(settings, DEFAULT_MODEL_ID, DEFAULT_DISPLAY_MODEL_ID, false);
	}

	public DroneItem(Item.Properties settings, Identifier modelId, Identifier displayModelId, boolean kamikaze) {
		super(settings, Items.FIREWORK_ROCKET);
		this.modelId = modelId == null ? DEFAULT_MODEL_ID : modelId;
		this.displayModelId = displayModelId == null ? DEFAULT_DISPLAY_MODEL_ID : displayModelId;
		this.kamikaze = kamikaze;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		if (!isDroneDisplayStack(itemStack)) {
			return this.modelId;
		}
		int kamikazePower = getKamikazePower(itemStack);
		if (kamikazePower > 0) {
			return kamikazeDisplayModelForPower(kamikazePower);
		}
		return this.displayModelId;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDroneDisplayStack(original) || PolymerResourcePackUtils.hasMainPack(context)) {
			return;
		}
		int power = getKamikazePower(original);
		out.set(DataComponents.CUSTOM_NAME, localizedName(context, power > 0, power).withStyle(style -> style.withItalic(false)));
	}

	@Override
	public Component getName(ItemStack stack) {
		int power = getKamikazePower(stack);
		if (power <= 0) {
			return super.getName(stack);
		}
		return Component.translatable(
				"item.lg2.drone_kamikaze.with_power",
				Component.translatable("item.lg2.drone_kamikaze.power." + power)
		);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return DroneSystem.placeDrone(context);
	}

	public static ItemStack createDisplayStack() {
		return createDisplayStack(ModItems.DRONE, NO_KAMIKAZE_POWER);
	}

	public static ItemStack createDisplayStack(Item droneItem, int kamikazePower) {
		ItemStack stack = new ItemStack(droneItem == null ? ModItems.DRONE : droneItem);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var displayTag = tag.getCompoundOrEmpty(DISPLAY_ROOT_TAG);
			displayTag.putBoolean(DISPLAY_ONLY_TAG, true);
			tag.put(DISPLAY_ROOT_TAG, displayTag);
		});
		setKamikazePower(stack, kamikazePower);
		return stack;
	}

	public static boolean isDroneDisplayStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(DISPLAY_ROOT_TAG)
				.getBooleanOr(DISPLAY_ONLY_TAG, false);
	}

	public static boolean isKamikazeDroneStack(ItemStack stack) {
		return getKamikazePower(stack) > 0;
	}

	public static int getKamikazePower(ItemStack stack) {
		if (!isDroneCompatibleStack(stack)) {
			return NO_KAMIKAZE_POWER;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return NO_KAMIKAZE_POWER;
		}
		int rawPower = customData.copyTag()
				.getCompoundOrEmpty(DATA_ROOT_TAG)
				.getIntOr(KAMIKAZE_POWER_TAG, NO_KAMIKAZE_POWER);
		return net.minecraft.util.Mth.clamp(rawPower, NO_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER);
	}

	public static void setKamikazePower(ItemStack stack, int power) {
		if (!isDroneCompatibleStack(stack)) {
			return;
		}
		int clamped = net.minecraft.util.Mth.clamp(power, NO_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (clamped <= 0) {
				var droneTag = tag.getCompoundOrEmpty(DATA_ROOT_TAG);
				droneTag.remove(KAMIKAZE_POWER_TAG);
				if (droneTag.isEmpty()) {
					tag.remove(DATA_ROOT_TAG);
				} else {
					tag.put(DATA_ROOT_TAG, droneTag);
				}
				return;
			}
			var droneTag = tag.getCompoundOrEmpty(DATA_ROOT_TAG);
			droneTag.putInt(KAMIKAZE_POWER_TAG, clamped);
			tag.put(DATA_ROOT_TAG, droneTag);
		});
	}

	public static ItemStack createKamikazeStack(int power) {
		ItemStack stack = new ItemStack(ModItems.DRONE);
		setKamikazePower(stack, power);
		return stack;
	}

	private static MutableComponent localizedName(PacketContext context, boolean kamikaze, int power) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return Component.literal(kamikaze ? "Kamikaze Drone " + romanPower(power) : "Drone");
		}
		String language = player.clientInformation().language();
		String normalized = language == null ? "" : language.toLowerCase();
		if (normalized.startsWith("rpr")) {
			return Component.literal(kamikaze ? "Дронъ-камикадзе " + romanPower(power) : "Дронъ");
		}
		if (normalized.startsWith("ru")) {
			return Component.literal(kamikaze ? "Дрон-камикадзе " + romanPower(power) : "Дрон");
		}
		if (normalized.startsWith("uk")) {
			return Component.literal(kamikaze ? "Дрон-камікадзе " + romanPower(power) : "Дрон");
		}
		if (normalized.startsWith("ja")) {
			return Component.literal(kamikaze ? "カミカゼドローン " + romanPower(power) : "ドローン");
		}
		return Component.literal(kamikaze ? "Kamikaze Drone " + romanPower(power) : "Drone");
	}

	private static String romanPower(int power) {
		return switch (net.minecraft.util.Mth.clamp(power, MIN_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER)) {
			case 1 -> "I";
			case 2 -> "II";
			default -> "III";
		};
	}

	private static Identifier kamikazeDisplayModelForPower(int power) {
		return switch (net.minecraft.util.Mth.clamp(power, MIN_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER)) {
			case 1 -> KAMIKAZE_DISPLAY_MODEL_ID_LEVEL_1;
			case 2 -> KAMIKAZE_DISPLAY_MODEL_ID_LEVEL_2;
			default -> KAMIKAZE_DISPLAY_MODEL_ID_LEVEL_3;
		};
	}

	private static boolean isDroneCompatibleStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() == ModItems.DRONE;
	}
}
