package com.lostglade.item;

import com.lostglade.Lg2;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import xyz.nucleoid.packettweaker.PacketContext;

import java.util.Locale;

public final class RainbowHarnessItem extends Item implements PolymerItem {
	private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "rainbow_harness");
	private static final ResourceKey<EquipmentAsset> EQUIPMENT_ASSET = ResourceKey.create(
			EquipmentAssets.ROOT_ID,
			Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "rainbow_harness")
	);
	private static final int COLOR_FRAME_TICKS = 25;
	private static final String[] FRAME_ASSET_PATHS = {
			"rainbow_harness",
			"rainbow_harness_orange",
			"rainbow_harness_magenta",
			"rainbow_harness_light_blue",
			"rainbow_harness_yellow",
			"rainbow_harness_lime",
			"rainbow_harness_pink",
			"rainbow_harness_gray",
			"rainbow_harness_light_gray",
			"rainbow_harness_cyan",
			"rainbow_harness_purple",
			"rainbow_harness_blue",
			"rainbow_harness_brown",
			"rainbow_harness_green",
			"rainbow_harness_red",
			"rainbow_harness_black"
	};
	private static final Equippable[] RAINBOW_EQUIPPABLE_FRAMES = createEquippableFrames();
	private static final Equippable RAINBOW_EQUIPPABLE = createEquippable(EQUIPMENT_ASSET);
	private static final Equippable FALLBACK_EQUIPPABLE = Items.WHITE_HARNESS.components().get(DataComponents.EQUIPPABLE);

	public RainbowHarnessItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable(this.getDescriptionId());
	}

	@Override
	public Item getPolymerItem(ItemStack stack, PacketContext context) {
		return Items.WHITE_HARNESS;
	}

	@Override
	public Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
		return PolymerResourcePackUtils.hasMainPack(context) ? MODEL_ID : null;
	}

	@Override
	public void modifyBasePolymerItemStack(ItemStack out, ItemStack original, PacketContext context) {
		out.remove(DataComponents.DYED_COLOR);
		if (PolymerResourcePackUtils.hasMainPack(context)) {
			out.set(DataComponents.EQUIPPABLE, frameEquippableOrDefault(original));
			return;
		}
		if (FALLBACK_EQUIPPABLE != null) {
			out.set(DataComponents.EQUIPPABLE, FALLBACK_EQUIPPABLE);
		}
		out.set(DataComponents.CUSTOM_NAME, localizedName(context).withStyle(style -> style.withItalic(false)));
	}

	public static Equippable createEquippable() {
		return RAINBOW_EQUIPPABLE;
	}

	public static Equippable frameEquippableForGameTime(long gameTime) {
		return RAINBOW_EQUIPPABLE_FRAMES[frameIndexForGameTime(gameTime)];
	}

	public static boolean hasAnimatedFrame(ItemStack stack, Equippable frameEquippable) {
		Equippable current = stack.get(DataComponents.EQUIPPABLE);
		return current != null && current.assetId().equals(frameEquippable.assetId());
	}

	public static void setAnimatedFrame(ItemStack stack, Equippable frameEquippable) {
		stack.set(DataComponents.EQUIPPABLE, frameEquippable);
		stack.remove(DataComponents.DYED_COLOR);
	}

	private static Equippable createEquippable(ResourceKey<EquipmentAsset> assetId) {
		HolderGetter<EntityType<?>> entityTypes = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
		return Equippable.builder(EquipmentSlot.BODY)
				.setEquipSound(SoundEvents.HARNESS_EQUIP)
				.setAsset(assetId)
				.setAllowedEntities(entityTypes.getOrThrow(EntityTypeTags.CAN_EQUIP_HARNESS))
				.setEquipOnInteract(true)
				.setCanBeSheared(true)
				.setShearingSound(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.HARNESS_UNEQUIP))
				.build();
	}

	private static Equippable[] createEquippableFrames() {
		Equippable[] frames = new Equippable[FRAME_ASSET_PATHS.length];
		for (int i = 0; i < FRAME_ASSET_PATHS.length; i++) {
			ResourceKey<EquipmentAsset> asset = ResourceKey.create(
					EquipmentAssets.ROOT_ID,
					Identifier.fromNamespaceAndPath(Lg2.MOD_ID, FRAME_ASSET_PATHS[i])
			);
			frames[i] = createEquippable(asset);
		}
		return frames;
	}

	private static int frameIndexForGameTime(long gameTime) {
		return (int) Math.floorMod(gameTime / COLOR_FRAME_TICKS, RAINBOW_EQUIPPABLE_FRAMES.length);
	}

	private static Equippable frameEquippableOrDefault(ItemStack stack) {
		Equippable current = stack.get(DataComponents.EQUIPPABLE);
		if (current != null) {
			for (Equippable frame : RAINBOW_EQUIPPABLE_FRAMES) {
				if (frame.assetId().equals(current.assetId())) {
					return frame;
				}
			}
		}
		return RAINBOW_EQUIPPABLE;
	}

	private static MutableComponent localizedName(PacketContext context) {
		ServerPlayer player = context.getPlayer();
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return Component.literal("Rainbow Harness");
		}
		String language = player.clientInformation().language().toLowerCase(Locale.ROOT);
		if (language.startsWith("rpr")) {
			return Component.literal("Радужная упряжь");
		}
		if (language.startsWith("uk")) {
			return Component.literal("Різнокольорова упряж");
		}
		if (language.startsWith("ru")) {
			return Component.literal("Разноцветная упряжь");
		}
		if (language.startsWith("ja")) {
			return Component.literal("レインボーハーネス");
		}
		return Component.literal("Rainbow Harness");
	}
}
