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
	private static final String DISPLAY_ROOT_TAG = "lg2_drone_display";
	private static final String DISPLAY_ONLY_TAG = "display_only";
	private static final String DATA_ROOT_TAG = "lg2_drone";
	private static final String KAMIKAZE_POWER_TAG = "kamikaze_power";
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
		return isDroneDisplayStack(itemStack) ? this.displayModelId : this.modelId;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDroneDisplayStack(original) || PolymerResourcePackUtils.hasMainPack(context)) {
			return;
		}
		out.set(DataComponents.CUSTOM_NAME, localizedName(context, this.kamikaze, getKamikazePower(original)).withStyle(style -> style.withItalic(false)));
	}

	@Override
	public Component getName(ItemStack stack) {
		if (!this.kamikaze) {
			return super.getName(stack);
		}
		int power = getKamikazePower(stack);
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
		return createDisplayStack(ModItems.DRONE, MIN_KAMIKAZE_POWER);
	}

	public static ItemStack createDisplayStack(Item droneItem, int kamikazePower) {
		ItemStack stack = new ItemStack(droneItem == null ? ModItems.DRONE : droneItem);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var displayTag = tag.getCompoundOrEmpty(DISPLAY_ROOT_TAG);
			displayTag.putBoolean(DISPLAY_ONLY_TAG, true);
			tag.put(DISPLAY_ROOT_TAG, displayTag);
		});
		if (isKamikazeDroneStack(stack)) {
			setKamikazePower(stack, kamikazePower);
		}
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
		return stack != null && !stack.isEmpty() && stack.getItem() == ModItems.DRONE_KAMIKAZE;
	}

	public static int getKamikazePower(ItemStack stack) {
		if (!isKamikazeDroneStack(stack)) {
			return MIN_KAMIKAZE_POWER;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return MIN_KAMIKAZE_POWER;
		}
		int rawPower = customData.copyTag()
				.getCompoundOrEmpty(DATA_ROOT_TAG)
				.getIntOr(KAMIKAZE_POWER_TAG, MIN_KAMIKAZE_POWER);
		return net.minecraft.util.Mth.clamp(rawPower, MIN_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER);
	}

	public static void setKamikazePower(ItemStack stack, int power) {
		if (!isKamikazeDroneStack(stack)) {
			return;
		}
		int clamped = net.minecraft.util.Mth.clamp(power, MIN_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var droneTag = tag.getCompoundOrEmpty(DATA_ROOT_TAG);
			droneTag.putInt(KAMIKAZE_POWER_TAG, clamped);
			tag.put(DATA_ROOT_TAG, droneTag);
		});
	}

	public static ItemStack createKamikazeStack(int power) {
		ItemStack stack = new ItemStack(ModItems.DRONE_KAMIKAZE);
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
}
