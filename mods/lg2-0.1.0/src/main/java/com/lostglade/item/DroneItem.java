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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DroneItem extends SimplePolymerItem {
	public enum DroneType {
		NORMAL,
		KAMIKAZE,
		COMBAT
	}

	public record DisplayLayer(String key, Identifier modelId) {
	}

	public static final String BODY_LAYER_KEY = "body";
	public static final String CAMERA_LAYER_KEY = "camera";
	public static final String NIGHT_VISION_LAYER_KEY = "night_vision";
	public static final String MICROPHONE_LAYER_KEY = "microphone";
	public static final String RIGHT_FRONT_PROPELLER_LAYER_KEY = "propeller_right_front";
	public static final String RIGHT_BACK_PROPELLER_LAYER_KEY = "propeller_right_back";
	public static final String LEFT_FRONT_PROPELLER_LAYER_KEY = "propeller_left_front";
	public static final String LEFT_BACK_PROPELLER_LAYER_KEY = "propeller_left_back";
	public static final String KAMIKAZE_LAYER_KEY = "kamikaze";
	public static final String TURRET_LAYER_KEY = "turret";
	public static final String AUTO_AIM_BASE_LAYER_KEY = "auto_aim";
	public static final String AUTO_AIM_RIGHT_FRONT_LAYER_KEY = "auto_aim_right_front";
	public static final String AUTO_AIM_RIGHT_BOTTOM_LAYER_KEY = "auto_aim_right_bottom";
	public static final String AUTO_AIM_LEFT_FRONT_LAYER_KEY = "auto_aim_left_front";
	public static final String AUTO_AIM_LEFT_BOTTOM_LAYER_KEY = "auto_aim_left_bottom";
	private static final Identifier DEFAULT_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone");
	private static final Identifier NORMAL_DISPLAY_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_display");
	private static final Identifier KAMIKAZE_LAYER_MODEL_ID_LEVEL_0 = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_kamikaze_0");
	private static final Identifier KAMIKAZE_LAYER_MODEL_ID_LEVEL_1 = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_kamikaze_1");
	private static final Identifier KAMIKAZE_LAYER_MODEL_ID_LEVEL_2 = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_kamikaze_2");
	private static final Identifier KAMIKAZE_LAYER_MODEL_ID_LEVEL_3 = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_kamikaze_3");
	private static final Identifier NIGHT_VISION_LAYER_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_night_vision");
	private static final Identifier MICROPHONE_LAYER_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_microphone");
	private static final Identifier AUTO_AIM_LAYER_MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_auto_aim");
	private static final int AUTO_AIM_FRAME_COUNT = 8;
	private static final String DISPLAY_ROOT_TAG = "lg2_drone_display";
	private static final String DISPLAY_ONLY_TAG = "display_only";
	private static final String DISPLAY_MODEL_OVERRIDE_TAG = "model_override";
	private static final String DATA_ROOT_TAG = "lg2_drone";
	private static final String TYPE_TAG = "type";
	private static final String KAMIKAZE_POWER_TAG = "kamikaze_power";
	private static final String NIGHT_VISION_TAG = "night_vision";
	private static final String AUTO_AIM_TAG = "auto_aim";
	private static final String MICROPHONE_TAG = "microphone";
	private static final String PAINT_COLOR_TAG = "paint";
	private static final int NO_KAMIKAZE_POWER = 0;
	private static final int MIN_KAMIKAZE_POWER = 1;
	private static final int MAX_KAMIKAZE_POWER = 3;
	private static final DyeColor DEFAULT_DISPLAY_COLOR = DyeColor.WHITE;
	private final Identifier modelId;

	public DroneItem(Item.Properties settings) {
		this(settings, DEFAULT_MODEL_ID);
	}

	public DroneItem(Item.Properties settings, Identifier modelId) {
		super(settings, Items.FIREWORK_ROCKET);
		this.modelId = modelId == null ? DEFAULT_MODEL_ID : modelId;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack itemStack, PacketContext context) {
		if (!PolymerResourcePackUtils.hasMainPack(context)) {
			return null;
		}
		if (!isDroneDisplayStack(itemStack)) {
			return this.modelId;
		}
		Identifier overrideModel = getDisplayModelOverride(itemStack);
		if (overrideModel != null) {
			return overrideModel;
		}
		return displayModelForType(getDroneType(itemStack));
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		if (isDroneDisplayStack(original) || PolymerResourcePackUtils.hasMainPack(context)) {
			return;
		}
		DroneType type = getDroneType(original);
		int power = getKamikazePower(original);
		out.set(DataComponents.CUSTOM_NAME, localizedName(context, type, power).withStyle(style -> style.withItalic(false)));
	}

	@Override
	public Component getName(ItemStack stack) {
		DroneType type = getDroneType(stack);
		if (type == DroneType.COMBAT) {
			return Component.literal("Combat Drone");
		}
		if (type == DroneType.KAMIKAZE) {
			int power = getKamikazePower(stack);
			if (power > 0) {
				return Component.translatable(
						"item.lg2.drone_kamikaze.with_power",
						Component.translatable("item.lg2.drone_kamikaze.power." + power)
				);
			}
			return Component.translatable("item.lg2.drone_kamikaze");
		}
		return super.getName(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		return DroneSystem.placeDrone(context);
	}

	public static ItemStack createDisplayStack() {
		return createDisplayStack(ModItems.DRONE, DroneType.NORMAL, NO_KAMIKAZE_POWER, false, false, false, null);
	}

	public static ItemStack createDisplayStack(Item droneItem, int kamikazePower) {
		DroneType type = kamikazePower > 0 ? DroneType.KAMIKAZE : DroneType.NORMAL;
		return createDisplayStack(droneItem, type, kamikazePower, false, false, false, null);
	}

	public static ItemStack createDisplayStack(
			Item droneItem,
			DroneType type,
			int kamikazePower,
			boolean nightVision,
			boolean autoAim,
			DyeColor paintColor
	) {
		return createDisplayStack(droneItem, type, kamikazePower, nightVision, autoAim, false, paintColor);
	}

	public static ItemStack createDisplayStack(
			Item droneItem,
			DroneType type,
			int kamikazePower,
			boolean nightVision,
			boolean autoAim,
			boolean microphone,
			DyeColor paintColor
	) {
		return createDroneStack(droneItem, true, null, type, kamikazePower, nightVision, autoAim, microphone, paintColor);
	}

	public static ItemStack createConfiguredStack(
			Item droneItem,
			DroneType type,
			int kamikazePower,
			boolean nightVision,
			boolean autoAim,
			DyeColor paintColor
	) {
		return createConfiguredStack(droneItem, type, kamikazePower, nightVision, autoAim, false, paintColor);
	}

	public static ItemStack createConfiguredStack(
			Item droneItem,
			DroneType type,
			int kamikazePower,
			boolean nightVision,
			boolean autoAim,
			boolean microphone,
			DyeColor paintColor
	) {
		return createDroneStack(droneItem, false, null, type, kamikazePower, nightVision, autoAim, microphone, paintColor);
	}

	public static ItemStack createDisplayLayerStack(Identifier modelId) {
		return createDroneStack(ModItems.DRONE, true, modelId, DroneType.NORMAL, NO_KAMIKAZE_POWER, false, false, false, null);
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

	public static DroneType getDroneType(ItemStack stack) {
		if (!isDroneCompatibleStack(stack)) {
			return DroneType.NORMAL;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		String rawType = customData == null
				? ""
				: customData.copyTag().getCompoundOrEmpty(DATA_ROOT_TAG).getStringOr(TYPE_TAG, "");
		if (rawType != null && !rawType.isBlank()) {
			try {
				return DroneType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
			}
		}
		return getKamikazePower(stack) > 0 ? DroneType.KAMIKAZE : DroneType.NORMAL;
	}

	public static void setDroneType(ItemStack stack, DroneType type) {
		if (!isDroneCompatibleStack(stack)) {
			return;
		}
		DroneType resolvedType = type == null ? DroneType.NORMAL : type;
		updateDroneData(stack, droneTag -> {
			if (resolvedType == DroneType.NORMAL) {
				droneTag.remove(TYPE_TAG);
			} else {
				droneTag.putString(TYPE_TAG, resolvedType.name().toLowerCase(Locale.ROOT));
			}
		});
	}

	public static boolean isKamikazeDroneStack(ItemStack stack) {
		return getDroneType(stack) == DroneType.KAMIKAZE;
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
		updateDroneData(stack, droneTag -> {
			if (clamped <= 0) {
				droneTag.remove(KAMIKAZE_POWER_TAG);
				return;
			}
			droneTag.putInt(KAMIKAZE_POWER_TAG, clamped);
		});
		if (clamped > 0 && getDroneType(stack) != DroneType.KAMIKAZE) {
			setDroneType(stack, DroneType.KAMIKAZE);
		}
	}

	public static boolean hasNightVisionModule(ItemStack stack) {
		return getBooleanModule(stack, NIGHT_VISION_TAG);
	}

	public static void setNightVisionModule(ItemStack stack, boolean enabled) {
		setBooleanModule(stack, NIGHT_VISION_TAG, enabled);
	}

	public static boolean hasAutoAimModule(ItemStack stack) {
		return getBooleanModule(stack, AUTO_AIM_TAG);
	}

	public static void setAutoAimModule(ItemStack stack, boolean enabled) {
		setBooleanModule(stack, AUTO_AIM_TAG, enabled);
	}

	public static boolean hasMicrophoneModule(ItemStack stack) {
		return getBooleanModule(stack, MICROPHONE_TAG);
	}

	public static void setMicrophoneModule(ItemStack stack, boolean enabled) {
		setBooleanModule(stack, MICROPHONE_TAG, enabled);
	}

	public static DyeColor getPaintColor(ItemStack stack) {
		if (!isDroneCompatibleStack(stack)) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		String rawColor = customData.copyTag()
				.getCompoundOrEmpty(DATA_ROOT_TAG)
				.getStringOr(PAINT_COLOR_TAG, "");
		if (rawColor == null || rawColor.isBlank()) {
			return null;
		}
		for (DyeColor color : DyeColor.values()) {
			if (color.getName().equalsIgnoreCase(rawColor)) {
				return normalizeStoredPaintColor(color);
			}
		}
		return null;
	}

	public static void setPaintColor(ItemStack stack, DyeColor color) {
		if (!isDroneCompatibleStack(stack)) {
			return;
		}
		DyeColor normalized = normalizeStoredPaintColor(color);
		updateDroneData(stack, droneTag -> {
			if (normalized == null) {
				droneTag.remove(PAINT_COLOR_TAG);
			} else {
				droneTag.putString(PAINT_COLOR_TAG, normalized.getName());
			}
		});
	}

	public static ItemStack createKamikazeStack(int power) {
		return createConfiguredStack(ModItems.DRONE, DroneType.KAMIKAZE, power, false, false, false, null);
	}

	public static Identifier getDisplayModelOverride(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		String rawId = customData.copyTag()
				.getCompoundOrEmpty(DISPLAY_ROOT_TAG)
				.getStringOr(DISPLAY_MODEL_OVERRIDE_TAG, "");
		if (rawId == null || rawId.isBlank()) {
			return null;
		}
		return Identifier.tryParse(rawId);
	}

	public static List<DisplayLayer> resolveDisplayLayers(
			DroneType type,
			int kamikazePower,
			boolean nightVision,
			boolean autoAim,
			boolean microphone,
			DyeColor paintColor
	) {
		List<DisplayLayer> layers = new ArrayList<>();
		layers.add(new DisplayLayer(BODY_LAYER_KEY, bodyLayerModelForColor(paintColor)));
		layers.add(new DisplayLayer(CAMERA_LAYER_KEY, cameraLayerModelForAngle(90)));
		if (nightVision) {
			layers.add(new DisplayLayer(NIGHT_VISION_LAYER_KEY, NIGHT_VISION_LAYER_MODEL_ID));
		}
		if (microphone) {
			layers.add(new DisplayLayer(MICROPHONE_LAYER_KEY, MICROPHONE_LAYER_MODEL_ID));
		}
		if (type == DroneType.KAMIKAZE) {
			layers.add(new DisplayLayer(KAMIKAZE_LAYER_KEY, kamikazeLayerModelForPower(clampedKamikazePower(kamikazePower))));
		}
		if (type == DroneType.COMBAT) {
			layers.add(new DisplayLayer(TURRET_LAYER_KEY, turretLayerModelForAngle(90)));
		}
		layers.add(new DisplayLayer(RIGHT_FRONT_PROPELLER_LAYER_KEY, propellerLayerModel(RIGHT_FRONT_PROPELLER_LAYER_KEY, paintColor, 0)));
		layers.add(new DisplayLayer(RIGHT_BACK_PROPELLER_LAYER_KEY, propellerLayerModel(RIGHT_BACK_PROPELLER_LAYER_KEY, paintColor, 0)));
		layers.add(new DisplayLayer(LEFT_FRONT_PROPELLER_LAYER_KEY, propellerLayerModel(LEFT_FRONT_PROPELLER_LAYER_KEY, paintColor, 0)));
		layers.add(new DisplayLayer(LEFT_BACK_PROPELLER_LAYER_KEY, propellerLayerModel(LEFT_BACK_PROPELLER_LAYER_KEY, paintColor, 0)));
		if (autoAim) {
			layers.add(new DisplayLayer(AUTO_AIM_BASE_LAYER_KEY, AUTO_AIM_LAYER_MODEL_ID));
			layers.add(new DisplayLayer(AUTO_AIM_RIGHT_FRONT_LAYER_KEY, autoAimTentacleLayerModel(AUTO_AIM_RIGHT_FRONT_LAYER_KEY, 0)));
			layers.add(new DisplayLayer(AUTO_AIM_LEFT_BOTTOM_LAYER_KEY, autoAimTentacleLayerModel(AUTO_AIM_LEFT_BOTTOM_LAYER_KEY, 1)));
			layers.add(new DisplayLayer(AUTO_AIM_LEFT_FRONT_LAYER_KEY, autoAimTentacleLayerModel(AUTO_AIM_LEFT_FRONT_LAYER_KEY, 2)));
			layers.add(new DisplayLayer(AUTO_AIM_RIGHT_BOTTOM_LAYER_KEY, autoAimTentacleLayerModel(AUTO_AIM_RIGHT_BOTTOM_LAYER_KEY, 3)));
		}
		return layers;
	}

	public static Identifier displayModelForType(DroneType type) {
		// Type-specific visuals now belong in additive display layers so the base rig stays consistent.
		return NORMAL_DISPLAY_MODEL_ID;
	}

	public static Identifier bodyLayerModelForColor(DyeColor color) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_body_" + resolveDisplayColorName(color));
	}

	public static Identifier cameraLayerModelForAngle(int angle) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_camera_pitch_" + net.minecraft.util.Mth.clamp(angle, 0, 90));
	}

	public static Identifier nightVisionLayerModelForAngle(int angle) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_night_vision_pitch_" + net.minecraft.util.Mth.clamp(angle, 0, 90));
	}

	public static Identifier propellerLayerModel(String layerKey, DyeColor color, int state) {
		if (!isPropellerLayerKey(layerKey)) {
			return null;
		}
		return Identifier.fromNamespaceAndPath(
				Lg2.MOD_ID,
				"drone_propeller_"
						+ layerKey.substring("propeller_".length())
						+ "_"
						+ resolveDisplayColorName(color)
						+ "_"
						+ net.minecraft.util.Mth.clamp(state, 0, 1)
		);
	}

	public static Identifier kamikazeLayerModelForPower(int power) {
		return switch (net.minecraft.util.Mth.clamp(power, NO_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER)) {
			case 0 -> KAMIKAZE_LAYER_MODEL_ID_LEVEL_0;
			case 1 -> KAMIKAZE_LAYER_MODEL_ID_LEVEL_1;
			case 2 -> KAMIKAZE_LAYER_MODEL_ID_LEVEL_2;
			default -> KAMIKAZE_LAYER_MODEL_ID_LEVEL_3;
		};
	}

	public static Identifier turretLayerModelForAngle(int angle) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "drone_module_turret_pitch_" + net.minecraft.util.Mth.clamp(angle, 0, 90));
	}

	public static Identifier autoAimTentacleLayerModel(String layerKey, int frameIndex) {
		String tentacleName = autoAimTentacleName(layerKey);
		if (tentacleName == null) {
			return null;
		}
		return Identifier.fromNamespaceAndPath(
				Lg2.MOD_ID,
				"drone_module_auto_aim_" + tentacleName + "_" + net.minecraft.util.Mth.clamp(frameIndex, 0, AUTO_AIM_FRAME_COUNT - 1)
		);
	}

	public static boolean isKamikazeLayerKey(String layerKey) {
		return KAMIKAZE_LAYER_KEY.equals(layerKey);
	}

	public static boolean isTurretLayerKey(String layerKey) {
		return TURRET_LAYER_KEY.equals(layerKey);
	}

	public static boolean isPropellerLayerKey(String layerKey) {
		if (layerKey == null || layerKey.isBlank()) {
			return false;
		}
		return RIGHT_FRONT_PROPELLER_LAYER_KEY.equals(layerKey)
				|| RIGHT_BACK_PROPELLER_LAYER_KEY.equals(layerKey)
				|| LEFT_FRONT_PROPELLER_LAYER_KEY.equals(layerKey)
				|| LEFT_BACK_PROPELLER_LAYER_KEY.equals(layerKey);
	}

	public static boolean isAutoAimTentacleLayerKey(String layerKey) {
		return autoAimTentacleName(layerKey) != null;
	}

	public static boolean isNightVisionLayerKey(String layerKey) {
		return NIGHT_VISION_LAYER_KEY.equals(layerKey);
	}

	private static String autoAimTentacleName(String layerKey) {
		if (AUTO_AIM_RIGHT_FRONT_LAYER_KEY.equals(layerKey)) {
			return "right_front";
		}
		if (AUTO_AIM_RIGHT_BOTTOM_LAYER_KEY.equals(layerKey)) {
			return "right_bottom";
		}
		if (AUTO_AIM_LEFT_FRONT_LAYER_KEY.equals(layerKey)) {
			return "left_front";
		}
		if (AUTO_AIM_LEFT_BOTTOM_LAYER_KEY.equals(layerKey)) {
			return "left_bottom";
		}
		return null;
	}

	private static ItemStack createDroneStack(
			Item droneItem,
			boolean displayOnly,
			Identifier displayModelOverride,
			DroneType type,
			int kamikazePower,
			boolean nightVision,
			boolean autoAim,
			boolean microphone,
			DyeColor paintColor
	) {
		ItemStack stack = new ItemStack(droneItem == null ? ModItems.DRONE : droneItem);
		if (displayOnly || displayModelOverride != null) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
				var displayTag = tag.getCompoundOrEmpty(DISPLAY_ROOT_TAG);
				displayTag.putBoolean(DISPLAY_ONLY_TAG, displayOnly);
				if (displayModelOverride == null) {
					displayTag.remove(DISPLAY_MODEL_OVERRIDE_TAG);
				} else {
					displayTag.putString(DISPLAY_MODEL_OVERRIDE_TAG, displayModelOverride.toString());
				}
				tag.put(DISPLAY_ROOT_TAG, displayTag);
			});
		}
		setDroneType(stack, type);
		setKamikazePower(stack, kamikazePower);
		setNightVisionModule(stack, nightVision);
		setAutoAimModule(stack, autoAim);
		setMicrophoneModule(stack, microphone);
		setPaintColor(stack, paintColor);
		return stack;
	}

	private static MutableComponent localizedName(PacketContext context, DroneType type, int power) {
		ServerPlayer player = context.getPlayer();
		if (player == null) {
			return fallbackEnglishName(type, power);
		}
		String language = player.clientInformation().language();
		String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("rpr")) {
			return switch (type) {
				case COMBAT -> Component.literal("Боевой дронъ");
				case KAMIKAZE -> Component.literal(power > 0 ? "Дронъ-камикадзе " + romanPower(power) : "Дронъ-камикадзе");
				default -> Component.literal("Дронъ");
			};
		}
		if (normalized.startsWith("ru")) {
			return switch (type) {
				case COMBAT -> Component.literal("Боевой дрон");
				case KAMIKAZE -> Component.literal(power > 0 ? "Дрон-камикадзе " + romanPower(power) : "Дрон-камикадзе");
				default -> Component.literal("Дрон");
			};
		}
		if (normalized.startsWith("uk")) {
			return switch (type) {
				case COMBAT -> Component.literal("Бойовий дрон");
				case KAMIKAZE -> Component.literal(power > 0 ? "Дрон-камікадзе " + romanPower(power) : "Дрон-камікадзе");
				default -> Component.literal("Дрон");
			};
		}
		if (normalized.startsWith("ja")) {
			return switch (type) {
				case COMBAT -> Component.literal("戦闘ドローン");
				case KAMIKAZE -> Component.literal(power > 0 ? "カミカゼドローン " + romanPower(power) : "カミカゼドローン");
				default -> Component.literal("ドローン");
			};
		}
		return fallbackEnglishName(type, power);
	}

	private static MutableComponent fallbackEnglishName(DroneType type, int power) {
		return switch (type == null ? DroneType.NORMAL : type) {
			case COMBAT -> Component.literal("Combat Drone");
			case KAMIKAZE -> Component.literal(power > 0 ? "Kamikaze Drone " + romanPower(power) : "Kamikaze Drone");
			default -> Component.literal("Drone");
		};
	}

	private static String romanPower(int power) {
		return switch (net.minecraft.util.Mth.clamp(power, MIN_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER)) {
			case 1 -> "I";
			case 2 -> "II";
			default -> "III";
		};
	}

	private static int clampedKamikazePower(int power) {
		return net.minecraft.util.Mth.clamp(power, NO_KAMIKAZE_POWER, MAX_KAMIKAZE_POWER);
	}

	private static String resolveDisplayColorName(DyeColor color) {
		DyeColor resolved = color == null ? DEFAULT_DISPLAY_COLOR : color;
		return resolved.getName();
	}

	private static DyeColor normalizeStoredPaintColor(DyeColor color) {
		return color == DEFAULT_DISPLAY_COLOR ? null : color;
	}

	private static void setBooleanModule(ItemStack stack, String key, boolean enabled) {
		if (!isDroneCompatibleStack(stack) || key == null || key.isBlank()) {
			return;
		}
		updateDroneData(stack, droneTag -> {
			if (enabled) {
				droneTag.putBoolean(key, true);
			} else {
				droneTag.remove(key);
			}
		});
	}

	private static boolean getBooleanModule(ItemStack stack, String key) {
		if (!isDroneCompatibleStack(stack) || key == null || key.isBlank()) {
			return false;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return false;
		}
		return customData.copyTag()
				.getCompoundOrEmpty(DATA_ROOT_TAG)
				.getBooleanOr(key, false);
	}

	private static void updateDroneData(ItemStack stack, java.util.function.Consumer<net.minecraft.nbt.CompoundTag> updater) {
		if (!isDroneCompatibleStack(stack) || updater == null) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			var droneTag = tag.getCompoundOrEmpty(DATA_ROOT_TAG);
			updater.accept(droneTag);
			if (droneTag.isEmpty()) {
				tag.remove(DATA_ROOT_TAG);
			} else {
				tag.put(DATA_ROOT_TAG, droneTag);
			}
		});
	}

	private static boolean isDroneCompatibleStack(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		return stack.getItem() == ModItems.DRONE;
	}
}
