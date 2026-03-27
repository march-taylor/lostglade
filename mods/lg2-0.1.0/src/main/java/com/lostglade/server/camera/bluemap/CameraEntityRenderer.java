package com.lostglade.server.camera.bluemap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
import com.lostglade.server.ServerWebcamFrameCache;
import com.lostglade.server.map.TextureAssetManager;
import com.mojang.authlib.properties.Property;
import de.bluecolored.bluemap.core.map.hires.ArrayTileModel;
import de.bluecolored.bluemap.core.world.LightData;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.skin.SkinStorage;
import net.lionarius.skinrestorer.skin.SkinValue;
import net.lionarius.skinrestorer.util.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Markings;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class CameraEntityRenderer {
	private static final TextureAssetManager ASSETS = TextureAssetManager.get();
	private static final Map<String, Boolean> SEPARATE_LEFT_ARM_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final float PX = 1.0F / 16.0F;
	private static final int DEFAULT_LIGHT = 15;
	private static final Identifier PLAYER_WIDE_FALLBACK = Identifier.fromNamespaceAndPath("minecraft", "entity/player/wide/steve");
	private static final Identifier PLAYER_SLIM_FALLBACK = Identifier.fromNamespaceAndPath("minecraft", "entity/player/slim/alex");
	private static final Identifier ZOMBIE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/zombie/zombie");
	private static final Identifier HUSK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/zombie/husk");
	private static final Identifier DROWNED_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/zombie/drowned");
	private static final Identifier SKELETON_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/skeleton/skeleton");
	private static final Identifier CREEPER_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/creeper/creeper");
	private static final Identifier SPIDER_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/spider/spider");
	private static final Identifier SPIDER_EYES_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/spider_eyes");
	private static final Identifier ENDERMAN_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/enderman/enderman");
	private static final Identifier ENDERMAN_EYES_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/enderman/enderman_eyes");
	private static final Identifier SHEEP_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/sheep/sheep");
	private static final Identifier SHEEP_WOOL_UNDERCOAT_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/sheep/sheep_wool_undercoat");
	private static final Identifier SHEEP_WOOL_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/sheep/sheep_wool");
	private static final Identifier ARMOR_STAND_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/armorstand/wood");
	private static final Identifier VILLAGER_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/villager/villager");
	private static final Identifier EXPERIENCE_ORB_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/experience_orb");
	private static final Identifier FISHING_HOOK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/fishing_hook");
	private static final Identifier LEASH_SEGMENT_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "block/brown_wool");
	private static final Identifier FISHING_LINE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "block/light_gray_wool");
	private static final Identifier ARMOR_TRIM_PALETTE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "trims/color_palettes/trim_palette");
	private static final Map<String, BlueMapCameraRenderer.TextureMaterial> STATIC_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, BlueMapCameraRenderer.TextureMaterial> PLAYER_SKIN_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, PlayerSkinSnapshot> PLAYER_SKIN_SNAPSHOT_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Identifier> ITEM_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, ItemVisual> ITEM_VISUAL_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, FlatSpriteMesh> FLAT_SPRITE_MESH_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> ARMOR_TRIM_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> EQUIPMENT_TINT_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, ClientModelResolver> VANILLA_CLIENT_MODEL_RULES = buildVanillaClientModelRules();

	private CameraEntityRenderer() {
	}

	static EntitySnapshot captureEntity(Entity entity) {
		return captureEntity(null, null, null, null, entity);
	}

	static EntitySnapshot captureEntity(ServerPlayer viewer, Vec3 cameraForward, Vec3 cameraRight, Vec3 cameraUp, Entity entity) {
		try {
			return attachAuxiliarySnapshots(viewer, cameraForward, cameraRight, cameraUp, entity, captureEntityUnsafe(entity));
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Failed to capture camera snapshot for entity {}", entity, throwable);
			return null;
		}
	}

	static EntitySnapshot captureBlockEntity(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (blockEntity == null || blockEntity.isRemoved()) {
			return null;
		}
		try {
			return captureBlockEntityUnsafe(blockEntity);
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Failed to capture camera snapshot for block entity {}", blockEntity, throwable);
			return null;
		}
	}

	private static EntitySnapshot captureBlockEntityUnsafe(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		if (blockEntity instanceof net.minecraft.world.level.block.entity.BedBlockEntity bedBlockEntity && VanillaClientModels.isAvailable()) {
			return captureBedBlockEntity(bedBlockEntity);
		}
		if (blockEntity instanceof net.minecraft.world.level.block.entity.HangingSignBlockEntity hangingSignBlockEntity && VanillaClientModels.isAvailable()) {
			return withSignText(captureHangingSignBlockEntity(hangingSignBlockEntity), hangingSignBlockEntity, true);
		}
		if (blockEntity instanceof net.minecraft.world.level.block.entity.SignBlockEntity signBlockEntity && VanillaClientModels.isAvailable()) {
			return withSignText(captureStandingOrWallSignBlockEntity(signBlockEntity), signBlockEntity, false);
		}
		if (blockEntity instanceof net.minecraft.world.level.block.entity.BannerBlockEntity bannerBlockEntity && VanillaClientModels.isAvailable()) {
			return captureBannerBlockEntity(bannerBlockEntity);
		}
		if (blockEntity instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity enderChestBlockEntity && VanillaClientModels.isAvailable()) {
			return captureChestBlockEntity(enderChestBlockEntity);
		}
		if (blockEntity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chestBlockEntity && VanillaClientModels.isAvailable()) {
			return captureChestBlockEntity(chestBlockEntity);
		}
		if (blockEntity instanceof net.minecraft.world.level.block.entity.SkullBlockEntity skullBlockEntity && VanillaClientModels.isAvailable()) {
			return captureSkullBlockEntity(skullBlockEntity);
		}
		return captureBlockEntityAsFixedItem(blockEntity);
	}

	private static EntitySnapshot captureEntityUnsafe(Entity entity) {
		if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame itemFrame) {
			return captureItemFrameSnapshot(itemFrame);
		}
		if (!entity.isAlive() || entity.isInvisible()) {
			return null;
		}
		if (entity instanceof AbstractBoat boat && VanillaClientModels.isAvailable()) {
			return captureBoatClientModel(boat);
		}
		if (entity instanceof net.minecraft.world.entity.Display.ItemDisplay itemDisplay) {
			return captureItemDisplaySnapshot(itemDisplay);
		}
		LivingEntity livingEntity = entity instanceof LivingEntity living ? living : null;
		if (livingEntity == null && !(entity instanceof ItemEntity)) {
			return null;
		}

		if (entity instanceof Player player) {
			PlayerSkinSnapshot playerSkin = capturePlayerSkin(player);
			HumanoidKind kind = playerSkin != null && playerSkin.slim() ? HumanoidKind.PLAYER_SLIM : HumanoidKind.PLAYER;
			byte modelBits = player instanceof net.minecraft.world.entity.Avatar avatar
					? avatar.getEntityData().get(PlayerTrackedDataAccessor.lg2$getDataPlayerModeCustomisation())
					: 0;
			float playerBodyYaw = entity.getYRot();
			float playerHeadYaw = livingEntity.yHeadRot;
			return new HumanoidSnapshot(
					entity.position(),
					entity.getYRot(),
					playerBodyYaw,
					playerHeadYaw,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					livingEntity.getAttackAnim(0.0F),
					livingEntity.getSwimAmount(0.0F),
					entity.getPose(),
					sleepingDirection(livingEntity),
					entity.isCrouching(),
					entity.isVisuallySwimming(),
					livingEntity.isFallFlying(),
					entity.isPassenger(),
					false,
					false,
					livingEntity.isUsingItem(),
					player.getMainArm(),
					kind,
					playerSkin == null ? PLAYER_WIDE_FALLBACK : playerSkin.fallbackTexture(),
					new Identifier[0],
					captureArmorEquipment(livingEntity),
					leftHandItem(livingEntity),
					rightHandItem(livingEntity),
					playerSkin,
					modelBits
			);
		}

		if (entity instanceof ArmorStand armorStand) {
			if (VanillaClientModels.isAvailable()) {
				ClientModelSnapshot snapshot = captureArmorStandClientModel(armorStand);
				if (snapshot != null) {
					return snapshot;
				}
			}
			return new ArmorStandSnapshot(
					entity.position(),
					entity.getYRot(),
					armorStand.isSmall(),
					armorStand.showArms(),
					armorStand.showBasePlate(),
					armorStand.getHeadPose(),
					armorStand.getBodyPose(),
					armorStand.getLeftArmPose(),
					armorStand.getRightArmPose(),
					armorStand.getLeftLegPose(),
					armorStand.getRightLegPose(),
					captureArmorEquipment(armorStand)
			);
		}

		if (entity instanceof ExperienceOrb experienceOrb) {
			return new ExperienceOrbSnapshot(
					experienceOrb.position(),
					experienceOrb.getIcon(),
					experienceOrb.tickCount
			);
		}

		if (entity instanceof FishingHook fishingHook) {
			return captureFishingHookSnapshot(fishingHook);
		}

		if (entity instanceof Villager villager) {
			if (VanillaClientModels.isAvailable()) {
				return captureVillagerClientModel(villager);
			}
			VillagerData villagerData = villager.getVillagerData();
			Identifier[] overlays = new Identifier[]{
					holderTexture(villagerData.type(), "entity/villager/type/"),
					holderTexture(villagerData.profession(), "entity/villager/profession/"),
					professionLevelTexture(villagerData.level())
			};
			return humanoidSnapshot(livingEntity, HumanoidKind.VILLAGER, VILLAGER_TEXTURE, overlays);
		}

		EntitySnapshot specialEntityFixup = CameraEntityFixups.captureManualEntityFixup(entity, livingEntity);
		if (specialEntityFixup != null) {
			return specialEntityFixup;
		}

		if (entity instanceof Zombie && CameraEntityFixups.shouldUseManualZombieFamily(entity)) {
			Identifier texture = ZOMBIE_TEXTURE;
			HumanoidKind kind = HumanoidKind.ZOMBIE;
			Identifier[] overlays = new Identifier[0];
			if (entity instanceof Husk) {
				texture = HUSK_TEXTURE;
			} else if (entity instanceof Drowned) {
				texture = DROWNED_TEXTURE;
			}
			return new HumanoidSnapshot(
					entity.position(),
					entity.getYRot(),
					livingEntity.yBodyRot,
					livingEntity.yHeadRot,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					livingEntity.getAttackAnim(0.0F),
					livingEntity.getSwimAmount(0.0F),
					entity.getPose(),
					sleepingDirection(livingEntity),
					entity.isCrouching(),
					entity.isVisuallySwimming(),
					livingEntity.isFallFlying(),
					entity.isPassenger(),
					((Zombie) entity).isBaby(),
					entity instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive(),
					livingEntity.isUsingItem(),
					livingEntity.getMainArm(),
					kind,
					texture,
					overlays,
					captureArmorEquipment(livingEntity),
					leftHandItem(livingEntity),
					rightHandItem(livingEntity),
					null,
					(byte) 0
			);
		}

		if (entity instanceof AbstractSkeleton && CameraEntityFixups.shouldUseManualSkeletonFamily(entity)) {
			return humanoidSnapshot(livingEntity, HumanoidKind.SKELETON, SKELETON_TEXTURE, new Identifier[0]);
		}

		if (entity instanceof EnderMan enderMan) {
			return captureEndermanSnapshot(enderMan);
		}

		if (entity instanceof Creeper creeper) {
			return new CreeperSnapshot(
					entity.position(),
					livingEntity.yBodyRot,
					livingEntity.yHeadRot,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					false,
					creeper.getSwelling(0.0F)
			);
		}

		if (entity instanceof Spider) {
			if (VanillaClientModels.isAvailable()) {
				ClientModelSnapshot snapshot = captureVanillaClientModel(livingEntity);
				if (snapshot != null) {
					return snapshot;
				}
			}
			return new SpiderSnapshot(
					entity.position(),
					entity.getYRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed()
			);
		}

		if (entity instanceof Sheep sheep) {
			if (VanillaClientModels.isAvailable()) {
				return captureSheepClientModel(sheep);
			}
			return new QuadrupedSnapshot(
					entity.position(),
					entity.getYRot(),
					livingEntity.yBodyRot,
					livingEntity.yHeadRot,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					sheep.isBaby(),
					QuadrupedKind.SHEEP,
					SHEEP_TEXTURE,
					SHEEP_WOOL_TEXTURE,
					sheep.isSheared(),
					sheep.getHeadEatPositionScale(0.0F),
					sheep.getHeadEatAngleScale(0.0F)
			);
		}

		if (entity instanceof MushroomCow mushroomCow && VanillaClientModels.isAvailable()) {
			return captureMushroomCowClientModel(mushroomCow);
		}

		if (entity instanceof Cow cow) {
			if (VanillaClientModels.isAvailable()) {
				return captureCowClientModel(cow);
			}
			return new QuadrupedSnapshot(
					entity.position(),
					entity.getYRot(),
					livingEntity.yBodyRot,
					livingEntity.yHeadRot,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					cow.isBaby(),
					QuadrupedKind.COW,
					variantTexture(cow.getVariant(), Identifier.fromNamespaceAndPath("minecraft", "entity/cow/temperate_cow")),
					null,
					false,
					0.0F,
					0.0F
			);
		}

		if (entity instanceof Pig pig) {
			if (VanillaClientModels.isAvailable()) {
				return capturePigClientModel(pig);
			}
			return new QuadrupedSnapshot(
					entity.position(),
					entity.getYRot(),
					livingEntity.yBodyRot,
					livingEntity.yHeadRot,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					pig.isBaby(),
					QuadrupedKind.PIG,
					variantTexture(pig.getVariant(), Identifier.fromNamespaceAndPath("minecraft", "entity/pig/temperate_pig")),
					null,
					false,
					0.0F,
					0.0F
			);
		}

		if (entity instanceof Chicken chicken) {
			if (VanillaClientModels.isAvailable()) {
				return captureChickenClientModel(chicken);
			}
			return new ChickenSnapshot(
					entity.position(),
					entity.getYRot(),
					livingEntity.yBodyRot,
					livingEntity.yHeadRot,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					chicken.isBaby(),
					variantTexture(chicken.getVariant(), Identifier.fromNamespaceAndPath("minecraft", "entity/chicken/temperate_chicken")),
					chicken.flap
			);
		}

		if (entity instanceof Goat goat && VanillaClientModels.isAvailable()) {
			return captureGoatClientModel(goat);
		}

		if (entity instanceof net.minecraft.world.entity.animal.happyghast.HappyGhast happyGhast && VanillaClientModels.isAvailable()) {
			return captureHappyGhastClientModel(happyGhast);
		}

		if (entity instanceof Llama llama && VanillaClientModels.isAvailable()) {
			return captureLlamaClientModel(llama);
		}

		if (entity instanceof Armadillo armadillo && VanillaClientModels.isAvailable()) {
			return captureSimpleClientModel(
					armadillo,
					"net.minecraft.client.model.animal.armadillo.ArmadilloModel",
					Identifier.fromNamespaceAndPath("minecraft", "entity/armadillo")
			);
		}

		if (entity instanceof Cat cat && VanillaClientModels.isAvailable()) {
			return captureSimpleClientModel(
					cat,
					"net.minecraft.client.model.animal.feline.CatModel",
					catTexture(cat)
			);
		}

		if (entity instanceof Ocelot ocelot && VanillaClientModels.isAvailable()) {
			return captureSimpleClientModel(
					ocelot,
					"net.minecraft.client.model.animal.feline.OcelotModel",
					Identifier.fromNamespaceAndPath("minecraft", "entity/cat/ocelot")
			);
		}

		if (entity instanceof Horse horse && VanillaClientModels.isAvailable()) {
			return captureHorseClientModel(horse);
		}

		if (entity instanceof Fox fox && VanillaClientModels.isAvailable()) {
			return captureSimpleClientModel(
					fox,
					"net.minecraft.client.model.animal.fox.FoxModel",
					foxTexture(fox)
			);
		}

		if (entity instanceof Rabbit rabbit && VanillaClientModels.isAvailable()) {
			return captureSimpleClientModel(
					rabbit,
					"net.minecraft.client.model.animal.rabbit.RabbitModel",
					rabbitTexture(rabbit)
			);
		}

		if (entity instanceof Panda panda && VanillaClientModels.isAvailable()) {
			return captureSimpleClientModel(
					panda,
					"net.minecraft.client.model.animal.panda.PandaModel",
					pandaTexture(panda)
			);
		}

		if (entity instanceof PolarBear polarBear && VanillaClientModels.isAvailable()) {
			return captureSimpleClientModel(
					polarBear,
					"net.minecraft.client.model.animal.polarbear.PolarBearModel",
					Identifier.fromNamespaceAndPath("minecraft", "entity/bear/polarbear")
			);
		}

		if (entity instanceof net.minecraft.world.entity.animal.golem.IronGolem ironGolem && VanillaClientModels.isAvailable()) {
			return captureIronGolemClientModel(ironGolem);
		}

		if (livingEntity != null && VanillaClientModels.isAvailable()) {
			ClientModelSnapshot vanillaClientSnapshot = captureVanillaClientModel(livingEntity);
			if (vanillaClientSnapshot != null) {
				return vanillaClientSnapshot;
			}
		}

		if (entity instanceof ItemEntity itemEntity) {
			ItemVisual visual = resolveItemVisual(itemEntity.getItem(), ItemDisplayTransformContext.GROUND);
			if (visual == null || (visual.flatTexture() == null && (visual.model() == null || visual.model().elements().isEmpty()))) {
				return null;
			}
			return new ItemSnapshot(
					itemEntity.position(),
					ItemEntity.getSpin(0.0F, itemEntity.bobOffs),
					visual
			);
		}

		return null;
	}

	static HumanoidSnapshot humanoidSnapshot(LivingEntity livingEntity, HumanoidKind kind, Identifier texture, Identifier[] overlays) {
		return new HumanoidSnapshot(
				livingEntity.position(),
				livingEntity.getYRot(),
				livingEntity.yBodyRot,
				livingEntity.yHeadRot,
				livingEntity.getXRot(),
				livingEntity.walkAnimation.position(),
				livingEntity.walkAnimation.speed(),
				livingEntity.getAttackAnim(0.0F),
				livingEntity.getSwimAmount(0.0F),
				livingEntity.getPose(),
				sleepingDirection(livingEntity),
				livingEntity.isCrouching(),
				livingEntity.isVisuallySwimming(),
				livingEntity.isFallFlying(),
				livingEntity.isPassenger(),
				livingEntity.isBaby(),
				livingEntity instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive(),
				livingEntity.isUsingItem(),
				livingEntity.getMainArm(),
				kind,
				texture,
				overlays,
				captureArmorEquipment(livingEntity),
				leftHandItem(livingEntity),
				rightHandItem(livingEntity),
				null,
				(byte) 0
		);
	}

	private static ArmorEquipmentSnapshot captureArmorEquipment(LivingEntity livingEntity) {
		if (livingEntity == null) {
			return new ArmorEquipmentSnapshot(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);
		}
		return new ArmorEquipmentSnapshot(
				livingEntity.getItemBySlot(EquipmentSlot.HEAD).copy(),
				livingEntity.getItemBySlot(EquipmentSlot.CHEST).copy(),
				livingEntity.getItemBySlot(EquipmentSlot.LEGS).copy(),
				livingEntity.getItemBySlot(EquipmentSlot.FEET).copy()
		);
	}

	private static HeldItemSnapshot rightHandItem(LivingEntity livingEntity) {
		return heldItemSnapshot(livingEntity, HumanoidArm.RIGHT);
	}

	private static HeldItemSnapshot leftHandItem(LivingEntity livingEntity) {
		return heldItemSnapshot(livingEntity, HumanoidArm.LEFT);
	}

	private static HeldItemSnapshot heldItemSnapshot(LivingEntity livingEntity, HumanoidArm arm) {
		if (livingEntity == null) {
			return new HeldItemSnapshot(ItemStack.EMPTY, false, 0.0F, false, null, 0L, 0L);
		}
		InteractionHand interactionHand = physicalHand(livingEntity, arm);
		ItemStack stack = livingEntity.getItemInHand(interactionHand).copy();
		boolean usingItem = livingEntity.isUsingItem() && livingEntity.getUsedItemHand() == interactionHand;
		float useTicks = usingItem ? livingEntity.getTicksUsingItem() : 0.0F;
		boolean fishingRodCast = stack.getItem() instanceof net.minecraft.world.item.FishingRodItem
				&& livingEntity instanceof Player player
				&& player.fishing != null
				&& physicalHand(player, arm) == interactionHand;
		String contextDimensionId = livingEntity.level() == null ? null : livingEntity.level().dimension().identifier().toString();
		long gameTime = livingEntity.level() == null ? 0L : livingEntity.level().getGameTime();
		long dayTime = livingEntity.level() == null ? 0L : livingEntity.level().getDayTime();
		return new HeldItemSnapshot(stack, usingItem, useTicks, fishingRodCast, contextDimensionId, gameTime, dayTime);
	}

	private static HeldItemSnapshot heldItemSnapshot(LivingEntity livingEntity, ItemStack stack) {
		if (livingEntity == null || stack == null || stack.isEmpty()) {
			return new HeldItemSnapshot(ItemStack.EMPTY, false, 0.0F, false, null, 0L, 0L);
		}
		String contextDimensionId = livingEntity.level() == null ? null : livingEntity.level().dimension().identifier().toString();
		long gameTime = livingEntity.level() == null ? 0L : livingEntity.level().getGameTime();
		long dayTime = livingEntity.level() == null ? 0L : livingEntity.level().getDayTime();
		return new HeldItemSnapshot(stack.copy(), false, 0.0F, false, contextDimensionId, gameTime, dayTime);
	}

	private static HumanoidSnapshot captureEndermanSnapshot(EnderMan enderMan) {
		ItemStack carriedBlockStack = ItemStack.EMPTY;
		BlockState carriedBlock = enderMan.getCarriedBlock();
		if (carriedBlock != null) {
			Item carriedItem = carriedBlock.getBlock().asItem();
			if (carriedItem != Items.AIR) {
				carriedBlockStack = new ItemStack(carriedItem);
			}
		}
		return new HumanoidSnapshot(
				enderMan.position(),
				enderMan.getYRot(),
				enderMan.yBodyRot,
				enderMan.yHeadRot,
				enderMan.getXRot(),
				enderMan.walkAnimation.position(),
				enderMan.walkAnimation.speed(),
				enderMan.getAttackAnim(0.0F),
				enderMan.getSwimAmount(0.0F),
				enderMan.getPose(),
				sleepingDirection(enderMan),
				enderMan.isCrouching(),
				enderMan.isVisuallySwimming(),
				enderMan.isFallFlying(),
				enderMan.isPassenger(),
				false,
				enderMan.isCreepy(),
				false,
				enderMan.getMainArm(),
				HumanoidKind.ENDERMAN,
				ENDERMAN_TEXTURE,
				new Identifier[]{ENDERMAN_EYES_TEXTURE},
				captureArmorEquipment(enderMan),
				leftHandItem(enderMan),
				heldItemSnapshot(enderMan, carriedBlockStack),
				null,
				(byte) 0
		);
	}

	private static InteractionHand physicalHand(LivingEntity livingEntity, HumanoidArm arm) {
		return livingEntity.getMainArm() == arm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	private static EntitySnapshot attachAuxiliarySnapshots(
			ServerPlayer viewer,
			Vec3 cameraForward,
			Vec3 cameraRight,
			Vec3 cameraUp,
			Entity entity,
			EntitySnapshot primary
	) {
		if (primary == null) {
			return null;
		}
		List<EntitySnapshot> attachments = new ArrayList<>();
		LineSnapshot[] leashLines = captureLeashLines(entity);
		if (leashLines != null) {
			for (LineSnapshot leashLine : leashLines) {
				if (leashLine != null) {
					attachments.add(leashLine);
				}
			}
		}
		if (entity instanceof Player player) {
			ImagePlaneSnapshot webcamOverlay = capturePlayerWebcamOverlay(viewer, cameraForward, cameraRight, cameraUp, player);
			if (webcamOverlay != null) {
				attachments.add(webcamOverlay);
			}
		}
		if (attachments.isEmpty()) {
			return primary;
		}
		return new CompositeSnapshot(primary.position(), primary, attachments.toArray(EntitySnapshot[]::new));
	}

	private static ImagePlaneSnapshot capturePlayerWebcamOverlay(
			ServerPlayer viewer,
			Vec3 cameraForward,
			Vec3 cameraRight,
			Vec3 cameraUp,
			Player player
	) {
		if (viewer == null || cameraForward == null || cameraRight == null || cameraUp == null || player == null) {
			return null;
		}
		ServerWebcamFrameCache.WebcamDisplay display = ServerWebcamFrameCache.getAboveHeadDisplay(viewer, player);
		if (display == null || display.image() == null || display.size() <= 0.0F) {
			return null;
		}
		float size = display.size();
		float halfSize = size * 0.5F;
		// Webcam's living-entity layer is rendered from a higher local root than the raw
		// server entity base position we use for snapshots. An extra small clearance keeps
		// the webcam quad fully above the head instead of letting its lower edge clip it.
		Vec3 anchor = player.position().add(0.0D, 1.5D + display.offsetY(), 0.0D);
		Matrix4f transform = billboardTransform(anchor, cameraRight, cameraUp, cameraForward);
		return new ImagePlaneSnapshot(
				anchor,
				transform,
				-halfSize,
				-halfSize,
				0.0F,
				size,
				size,
				display.materialKey(),
				display.image(),
				false
		);
	}

	private static Matrix4f billboardTransform(Vec3 center, Vec3 right, Vec3 up, Vec3 forward) {
		return new Matrix4f()
				.m00((float) right.x).m01((float) right.y).m02((float) right.z).m03(0.0F)
				.m10((float) up.x).m11((float) up.y).m12((float) up.z).m13(0.0F)
				.m20((float) (-forward.x)).m21((float) (-forward.y)).m22((float) (-forward.z)).m23(0.0F)
				.m30((float) center.x).m31((float) center.y).m32((float) center.z).m33(1.0F);
	}

	private static ClientModelSnapshot capturePlayerClientModel(Player player, PlayerSkinSnapshot playerSkin, byte modelBits) {
		Map<String, Object> state = livingStateFields(player);
		state.put("showHat", showPlayerPart(modelBits, PlayerModelPart.HAT));
		state.put("showJacket", showPlayerPart(modelBits, PlayerModelPart.JACKET));
		state.put("showLeftPants", showPlayerPart(modelBits, PlayerModelPart.LEFT_PANTS_LEG));
		state.put("showRightPants", showPlayerPart(modelBits, PlayerModelPart.RIGHT_PANTS_LEG));
		state.put("showLeftSleeve", showPlayerPart(modelBits, PlayerModelPart.LEFT_SLEEVE));
		state.put("showRightSleeve", showPlayerPart(modelBits, PlayerModelPart.RIGHT_SLEEVE));
		state.put("showCape", showPlayerPart(modelBits, PlayerModelPart.CAPE));
		state.put("isSpectator", false);
		state.put("showExtraEars", false);
		state.put("id", player.getId());

		ClientLayerSnapshot layer = new ClientLayerSnapshot(
				"net.minecraft.client.model.player.PlayerModel",
				playerSkin == null ? PLAYER_WIDE_FALLBACK : playerSkin.fallbackTexture(),
				0xFFFFFF,
				false
		).withFactory("createMesh").withModelFlag(playerSkin != null && playerSkin.slim());
		if (playerSkin != null) {
			layer = layer.withPlayerSkin(playerSkin);
		}
		return livingClientModelSnapshot(player, player.yBodyRot, state, new ClientLayerSnapshot[]{layer});
	}

	private static ClientModelSnapshot captureArmorStandClientModel(ArmorStand armorStand) {
		Map<String, Object> state = livingStateFields(armorStand);
		state.put("yRot", armorStand.getYRot());
		state.put("isMarker", armorStand.isMarker());
		state.put("isSmall", armorStand.isSmall());
		state.put("showArms", armorStand.showArms());
		state.put("showBasePlate", armorStand.showBasePlate());
		state.put("bodyPose", armorStand.getBodyPose());
		state.put("headPose", armorStand.getHeadPose());
		state.put("leftArmPose", armorStand.getLeftArmPose());
		state.put("rightArmPose", armorStand.getRightArmPose());
		state.put("leftLegPose", armorStand.getLeftLegPose());
		state.put("rightLegPose", armorStand.getRightLegPose());
		state.put("wiggle", 0.0F);
		return livingClientModelSnapshot(
				armorStand,
				armorStand.getYRot(),
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot("net.minecraft.client.model.object.armorstand.ArmorStandModel", ARMOR_STAND_TEXTURE, 0xFFFFFF, false)
				}
		);
	}

	private static ClientModelSnapshot captureLlamaClientModel(Llama llama) {
		Map<String, Object> state = livingStateFields(llama);
		state.put("variant", llama.getVariant());
		state.put("hasChest", !llama.isBaby() && llama.hasChest());
		state.put("bodyItem", llama.getBodyArmorItem().copy());
		state.put("isTraderLlama", llama.isTraderLlama());

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		String modelClassName = "net.minecraft.client.model.animal.llama.LlamaModel";
		layers.add(new ClientLayerSnapshot(modelClassName, llamaTexture(llama), 0xFFFFFF, false));
		addEquipmentLayers(layers, modelClassName, "llama_body", llama.getBodyArmorItem(), null, 1.0F);
		if (llama.isTraderLlama() && llama.getBodyArmorItem().isEmpty()) {
			addEquipmentLayers(layers, modelClassName, "llama_body", ItemStack.EMPTY, Identifier.fromNamespaceAndPath("minecraft", "trader_llama"), 1.0F);
		}
		return livingClientModelSnapshot(llama, llama.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static ClientModelSnapshot captureHappyGhastClientModel(net.minecraft.world.entity.animal.happyghast.HappyGhast happyGhast) {
		Map<String, Object> state = livingStateFields(happyGhast);
		ItemStack bodyItem = happyGhast.getItemBySlot(EquipmentSlot.BODY).copy();
		boolean baby = happyGhast.isBaby();
		state.put("bodyItem", bodyItem);
		state.put("isRidden", happyGhast.isVehicle());
		state.put("isLeashHolder", happyGhast.isLeashHolder());

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		layers.add(
				new ClientLayerSnapshot(
						"net.minecraft.client.model.animal.ghast.HappyGhastModel",
						baby ? minecraftTexture("entity/ghast/happy_ghast_baby") : minecraftTexture("entity/ghast/happy_ghast"),
						0xFFFFFF,
						false
				).withFactory("createBodyLayer").withModelFlag(baby)
		);
		if (!bodyItem.isEmpty()) {
			addEquipmentLayers(
					layers,
					"net.minecraft.client.model.animal.ghast.HappyGhastHarnessModel",
					"happy_ghast_body",
					bodyItem,
					null,
					1.0F
			);
			layers.replaceAll(layer -> layer.modelClassName().equals("net.minecraft.client.model.animal.ghast.HappyGhastHarnessModel")
					? layer.withFactory("createHarnessLayer").withModelFlag(baby)
					: layer);
		}
		return livingClientModelSnapshot(happyGhast, happyGhast.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static FishingHookSnapshot captureFishingHookSnapshot(FishingHook fishingHook) {
		Player owner = fishingHook.getPlayerOwner();
		if (owner == null) {
			return null;
		}
		float attackAnim = owner.getAttackAnim(0.0F);
		float swing = Mth.sin(Mth.sqrt(attackAnim) * (float) Math.PI);
		Vec3 lineStart = fishingHookHandPosition(owner, swing);
		Vec3 hookPos = fishingHook.getPosition(0.0F).add(0.0D, 0.25D, 0.0D);
		return new FishingHookSnapshot(
				fishingHook.position(),
				lineStart.subtract(hookPos)
		);
	}

	private static EntitySnapshot captureItemFrameSnapshot(net.minecraft.world.entity.decoration.ItemFrame itemFrame) {
		ItemStack stack = itemFrame.getItem();
		ItemVisual item = resolveItemVisual(itemFrame.getItem(), ItemDisplayTransformContext.FRAMED);
		if (item == null) {
			item = new ItemVisual(null, null, null, null);
		}
		return new ItemFrameSnapshot(
				itemFrame.position(),
				itemFrame.getDirection(),
				itemFrame instanceof net.minecraft.world.entity.decoration.GlowItemFrame,
				itemFrame.isInvisible(),
				itemFrame.getRotation(),
				itemFrame.hasFramedMap(),
				item,
				captureFramedMap(itemFrame, stack)
		);
	}

	private static EntitySnapshot captureItemDisplaySnapshot(net.minecraft.world.entity.Display.ItemDisplay itemDisplay) {
		ItemStack stack = itemDisplay.getItemStack();
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		ItemDisplayTransformContext transformContext = itemDisplayTransformContext(itemDisplay.getItemTransform());
		ItemVisual visual = resolveItemVisual(stack, transformContext);
		if (visual == null) {
			return null;
		}
		return new DisplayItemSnapshot(
				itemDisplay.position(),
				itemDisplay.getYRot(),
				itemDisplay.getXRot(),
				0.0F,
				1.0F,
				transformContext,
				visual
		);
	}

	private static FramedMapSnapshot captureFramedMap(net.minecraft.world.entity.decoration.ItemFrame itemFrame, ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(itemFrame.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		MapId mapId = stack.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return null;
		}
		MapItemSavedData mapData = serverLevel.getMapData(mapId);
		if (mapData == null || mapData.colors == null || mapData.colors.length == 0) {
			return null;
		}
		return new FramedMapSnapshot(mapId.id(), mapData.colors.clone());
	}

	private static ItemDisplayTransformContext itemDisplayTransformContext(ItemDisplayContext transformContext) {
		if (transformContext == null) {
			return ItemDisplayTransformContext.NONE;
		}
		return switch (transformContext) {
			case NONE -> ItemDisplayTransformContext.NONE;
			case THIRD_PERSON_LEFT_HAND -> ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND;
			case THIRD_PERSON_RIGHT_HAND -> ItemDisplayTransformContext.THIRD_PERSON_RIGHT_HAND;
			case FIRST_PERSON_LEFT_HAND -> ItemDisplayTransformContext.FIRST_PERSON_LEFT_HAND;
			case FIRST_PERSON_RIGHT_HAND -> ItemDisplayTransformContext.FIRST_PERSON_RIGHT_HAND;
			case HEAD -> ItemDisplayTransformContext.HEAD;
			case GUI -> ItemDisplayTransformContext.GUI;
			case GROUND -> ItemDisplayTransformContext.GROUND;
			case FIXED -> ItemDisplayTransformContext.FIXED;
			default -> ItemDisplayTransformContext.FIXED;
		};
	}

	private static EntitySnapshot captureBlockEntityAsFixedItem(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		net.minecraft.world.level.block.state.BlockState state = blockEntity.getBlockState();
		if (state == null) {
			return null;
		}
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (blockId == null || !CameraBlockFixups.shouldUseFixedItemFallback(blockId)) {
			return null;
		}
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(state.getBlock());
		ItemVisual visual = resolveItemVisual(stack, ItemDisplayTransformContext.FIXED);
		if (visual == null) {
			return null;
		}
		Vec3 position = Vec3.atCenterOf(blockEntity.getBlockPos()).add(0.0D, -0.25D, 0.0D);
		return new FixedItemSnapshot(position, 180.0F, 0.0F, 0.0F, 1.0F, visual);
	}

	private static ClientModelSnapshot captureBedBlockEntity(net.minecraft.world.level.block.entity.BedBlockEntity bedBlockEntity) {
		net.minecraft.world.level.block.state.BlockState state = bedBlockEntity.getBlockState();
		Direction facing = state.getValue(net.minecraft.world.level.block.BedBlock.FACING);
		boolean head = state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.HEAD;
		Map<String, Object> rootState = blockEntityRootState(blockEntityPos(bedBlockEntity));
		rootState.put("rootTranslateY", 9.0F * PX);
		rootState.put("rootRotateX", 90.0F);
		rootState.put("rootMidTranslateX", 0.5F);
		rootState.put("rootMidTranslateY", 0.5F);
		rootState.put("rootMidTranslateZ", 0.5F);
		rootState.put("rootRotate2Z", 180.0F + facing.toYRot());
		rootState.put("rootPostTranslateX", -0.5F);
		rootState.put("rootPostTranslateY", -0.5F);
		rootState.put("rootPostTranslateZ", -0.5F);

		Identifier texture = minecraftTexture("entity/bed/" + bedBlockEntity.getColor().getSerializedName());
		String factory = head
				? "net.minecraft.client.renderer.blockentity.BedRenderer#createHeadLayer"
				: "net.minecraft.client.renderer.blockentity.BedRenderer#createFootLayer";
		return clientModelSnapshot(
				blockEntityPos(bedBlockEntity),
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				rootState,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot("net.minecraft.client.model.Model$Simple", texture, 0xFFFFFF, false).withFactory(factory)
				}
		);
	}

	private static ClientModelSnapshot captureStandingOrWallSignBlockEntity(net.minecraft.world.level.block.entity.SignBlockEntity signBlockEntity) {
		net.minecraft.world.level.block.state.BlockState state = signBlockEntity.getBlockState();
		net.minecraft.world.level.block.SignBlock signBlock = (net.minecraft.world.level.block.SignBlock) state.getBlock();
		net.minecraft.world.level.block.state.properties.WoodType woodType = signBlock.type();
		boolean standing = state.getBlock() instanceof net.minecraft.world.level.block.StandingSignBlock;
		Map<String, Object> rootState = blockEntityRootState(blockEntityPos(signBlockEntity));
		rootState.put("rootTranslateX", 0.5F);
		rootState.put("rootTranslateY", 0.5F);
		rootState.put("rootTranslateZ", 0.5F);
		rootState.put("rootRotateY", -signBlock.getYRotationDegrees(state));
		if (!standing) {
			rootState.put("rootPostTranslateY", -0.3125F);
			rootState.put("rootPostTranslateZ", -0.4375F);
		}
		rootState.put("rootScaleX", 0.6666667F);
		rootState.put("rootScaleY", -0.6666667F);
		rootState.put("rootScaleZ", -0.6666667F);

		return clientModelSnapshot(
				blockEntityPos(signBlockEntity),
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				rootState,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.Model$Simple",
								minecraftTexture("entity/signs/" + woodType.name()),
								0xFFFFFF,
								false
						).withFactory("net.minecraft.client.renderer.blockentity.SignRenderer#createSignLayer:" + standing)
				}
		);
	}

	private static ClientModelSnapshot captureHangingSignBlockEntity(net.minecraft.world.level.block.entity.HangingSignBlockEntity signBlockEntity) {
		net.minecraft.world.level.block.state.BlockState state = signBlockEntity.getBlockState();
		net.minecraft.world.level.block.SignBlock signBlock = (net.minecraft.world.level.block.SignBlock) state.getBlock();
		net.minecraft.world.level.block.state.properties.WoodType woodType = signBlock.type();
		String attachmentType = hangingSignAttachmentType(state);
		Map<String, Object> rootState = blockEntityRootState(blockEntityPos(signBlockEntity));
		rootState.put("rootTranslateX", 0.5F);
		rootState.put("rootTranslateY", 0.9375F);
		rootState.put("rootTranslateZ", 0.5F);
		rootState.put("rootRotateY", -signBlock.getYRotationDegrees(state));
		rootState.put("rootPostTranslateY", -0.3125F);
		rootState.put("rootScaleX", 1.0F);
		rootState.put("rootScaleY", -1.0F);
		rootState.put("rootScaleZ", -1.0F);

		return clientModelSnapshot(
				blockEntityPos(signBlockEntity),
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				rootState,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.Model$Simple",
								minecraftTexture("entity/signs/hanging/" + woodType.name()),
								0xFFFFFF,
								false
						).withFactory("net.minecraft.client.renderer.blockentity.HangingSignRenderer#createHangingSignLayer:" + attachmentType)
				}
		);
	}

	private static EntitySnapshot withSignText(EntitySnapshot primary, net.minecraft.world.level.block.entity.SignBlockEntity signBlockEntity, boolean hanging) {
		if (primary == null) {
			return null;
		}
		List<EntitySnapshot> attachments = new ArrayList<>();
		ImagePlaneSnapshot front = captureSignTextPlane(signBlockEntity, signBlockEntity.getFrontText(), true, hanging);
		if (front != null) {
			attachments.add(front);
		}
		ImagePlaneSnapshot back = captureSignTextPlane(signBlockEntity, signBlockEntity.getBackText(), false, hanging);
		if (back != null) {
			attachments.add(back);
		}
		if (attachments.isEmpty()) {
			return primary;
		}
		return new CompositeSnapshot(primary.position(), primary, attachments.toArray(EntitySnapshot[]::new));
	}

	private static ImagePlaneSnapshot captureSignTextPlane(
			net.minecraft.world.level.block.entity.SignBlockEntity signBlockEntity,
			net.minecraft.world.level.block.entity.SignText signText,
			boolean front,
			boolean hanging
	) {
		BufferedImage image = signTextImage(signBlockEntity, signText);
		if (image == null) {
			return null;
		}
		Matrix4f transform = signTextTransform(signBlockEntity, front, hanging);
		String materialKey = "sign_text:" + signBlockEntity.getBlockPos().asLong() + ":" + (hanging ? "hanging" : "sign") + ":" + (front ? "front" : "back");
		return new ImagePlaneSnapshot(
				blockEntityPos(signBlockEntity),
				transform,
				-image.getWidth() * 0.5F,
				-image.getHeight() * 0.5F + 1.0F,
				0.0F,
				image.getWidth(),
				image.getHeight(),
				materialKey,
				image,
				signText.hasGlowingText()
		);
	}

	private static Matrix4f signTextTransform(net.minecraft.world.level.block.entity.SignBlockEntity signBlockEntity, boolean front, boolean hanging) {
		net.minecraft.world.level.block.state.BlockState state = signBlockEntity.getBlockState();
		net.minecraft.world.level.block.SignBlock signBlock = (net.minecraft.world.level.block.SignBlock) state.getBlock();
		Matrix4f transform = new Matrix4f()
				.translate((float) signBlockEntity.getBlockPos().getX(), (float) signBlockEntity.getBlockPos().getY(), (float) signBlockEntity.getBlockPos().getZ());
		if (hanging) {
			transform.translate(0.5F, 0.9375F, 0.5F)
					.rotateY(radians(-signBlock.getYRotationDegrees(state)))
					.translate(0.0F, -0.3125F, 0.0F);
		} else {
			transform.translate(0.5F, 0.5F, 0.5F)
					.rotateY(radians(-signBlock.getYRotationDegrees(state)));
			if (!(state.getBlock() instanceof net.minecraft.world.level.block.StandingSignBlock)) {
				transform.translate(0.0F, -0.3125F, -0.4375F);
			}
		}
		if (!front) {
			transform.rotateY((float) Math.PI);
		}
		Vec3 offset = hanging
				? new Vec3(0.0D, -0.3199999928474426D, 0.0729999989271164D)
				: new Vec3(0.0D, 0.3333333432674408D, 0.046666666865348816D);
		float textScale = (1.0F / 64.0F) * (hanging ? 0.9F : 0.6666667F);
		return transform
				.translate((float) offset.x, (float) offset.y, (float) offset.z)
				.translate(0.0F, 0.0F, 1.0F / 256.0F)
				.scale(textScale, textScale, textScale);
	}

	private static BufferedImage signTextImage(
			net.minecraft.world.level.block.entity.SignBlockEntity signBlockEntity,
			net.minecraft.world.level.block.entity.SignText signText
	) {
		if (signText == null) {
			return null;
		}
		String[] lines = new String[4];
		boolean hasVisibleText = false;
		for (int i = 0; i < lines.length; i++) {
			lines[i] = signText.getMessage(i, false).getString();
			if (!lines[i].isBlank()) {
				hasVisibleText = true;
			}
		}
		if (!hasVisibleText) {
			return null;
		}
		int width = Math.max(1, signBlockEntity.getMaxTextLineWidth());
		int lineHeight = Math.max(1, signBlockEntity.getTextLineHeight());
		int height = lineHeight * 4;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setComposite(AlphaComposite.SrcOver);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

			int fontSize = Math.max(6, lineHeight);
			Font font = new Font(Font.MONOSPACED, Font.PLAIN, fontSize);
			graphics.setFont(font);
			FontMetrics metrics = graphics.getFontMetrics(font);
			int maxWidth = Math.max(1, width - 2);
			while (fontSize > 4 && maxLineWidth(lines, metrics) > maxWidth) {
				fontSize--;
				font = font.deriveFont((float) fontSize);
				graphics.setFont(font);
				metrics = graphics.getFontMetrics(font);
			}

			int mainRgb = signTextMainColor(signText);
			int outlineRgb = signTextOutlineColor(signText);
			for (int i = 0; i < lines.length; i++) {
				String line = lines[i];
				if (line.isBlank()) {
					continue;
				}
				int textWidth = metrics.stringWidth(line);
				int x = (width - textWidth) / 2;
				int boxTop = i * lineHeight;
				int baseline = boxTop + Math.max(metrics.getAscent(), (lineHeight - metrics.getHeight()) / 2 + metrics.getAscent());
				if (signText.hasGlowingText()) {
					graphics.setColor(new Color(outlineRgb, true));
					for (int dx = -1; dx <= 1; dx++) {
						for (int dy = -1; dy <= 1; dy++) {
							if (dx == 0 && dy == 0) {
								continue;
							}
							graphics.drawString(line, x + dx, baseline + dy);
						}
					}
				}
				graphics.setColor(new Color(mainRgb, true));
				graphics.drawString(line, x, baseline);
			}
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static int maxLineWidth(String[] lines, FontMetrics metrics) {
		int max = 0;
		for (String line : lines) {
			max = Math.max(max, metrics.stringWidth(line));
		}
		return max;
	}

	private static int signTextMainColor(net.minecraft.world.level.block.entity.SignText signText) {
		int rgb = signText.getColor().getTextColor();
		if (signText.hasGlowingText()) {
			return 0xFF000000 | rgb;
		}
		return 0xFF000000 | scaleRgb(rgb, 0.4F);
	}

	private static int signTextOutlineColor(net.minecraft.world.level.block.entity.SignText signText) {
		int rgb = signText.getColor().getTextColor();
		if (rgb == net.minecraft.world.item.DyeColor.BLACK.getTextColor() && signText.hasGlowingText()) {
			return 0xFFF0EBCC;
		}
		return 0xFF000000 | scaleRgb(rgb, 0.4F);
	}

	private static int scaleRgb(int rgb, float factor) {
		int red = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
		int green = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
		int blue = Mth.clamp(Math.round((rgb & 0xFF) * factor), 0, 255);
		return (red << 16) | (green << 8) | blue;
	}

	private static ClientModelSnapshot captureBannerBlockEntity(net.minecraft.world.level.block.entity.BannerBlockEntity bannerBlockEntity) {
		net.minecraft.world.level.block.state.BlockState state = bannerBlockEntity.getBlockState();
		boolean standing = state.getBlock() instanceof net.minecraft.world.level.block.BannerBlock;
		float angle = standing
				? -net.minecraft.world.level.block.state.properties.RotationSegment.convertToDegrees(state.getValue(net.minecraft.world.level.block.BannerBlock.ROTATION))
				: -state.getValue(net.minecraft.world.level.block.WallBannerBlock.FACING).toYRot();
		long gameTime = bannerBlockEntity.getLevel() == null ? 0L : bannerBlockEntity.getLevel().getGameTime();
		BlockPos pos = bannerBlockEntity.getBlockPos();
		float phase = (Math.floorMod((long) pos.getX() * 7L + (long) pos.getY() * 9L + (long) pos.getZ() * 13L + gameTime, 100L)) / 100.0F;

		Map<String, Object> rootState = blockEntityRootState(blockEntityPos(bannerBlockEntity));
		rootState.put("rootTranslateX", 0.5F);
		rootState.put("rootTranslateZ", 0.5F);
		rootState.put("rootRotateY", angle);
		rootState.put("rootScaleX", 0.6666667F);
		rootState.put("rootScaleY", -0.6666667F);
		rootState.put("rootScaleZ", -0.6666667F);
		rootState.put("modelState", phase);

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		layers.add(
				new ClientLayerSnapshot(
						"net.minecraft.client.model.Model$Simple",
						minecraftTexture("entity/banner_base"),
						0xFFFFFF,
						false
				).withFactory("net.minecraft.client.model.object.banner.BannerModel#createBodyLayer:" + standing)
		);
		layers.add(
				new ClientLayerSnapshot(
						"net.minecraft.client.model.object.banner.BannerFlagModel",
						minecraftTexture("entity/banner/base"),
						bannerBlockEntity.getBaseColor().getTextureDiffuseColor(),
						false
				).withFactory("net.minecraft.client.model.object.banner.BannerFlagModel#createFlagLayer:" + standing)
		);
		for (net.minecraft.world.level.block.entity.BannerPatternLayers.Layer layer : bannerBlockEntity.getPatterns().layers()) {
			Identifier patternTexture = layer.pattern().unwrapKey()
					.map(resourceKey -> minecraftTexture("entity/banner/" + resourceKey.identifier().getPath()))
					.orElse(null);
			if (patternTexture == null) {
				continue;
			}
			layers.add(
					new ClientLayerSnapshot(
							"net.minecraft.client.model.object.banner.BannerFlagModel",
							patternTexture,
							layer.color().getTextureDiffuseColor(),
							false
					).withFactory("net.minecraft.client.model.object.banner.BannerFlagModel#createFlagLayer:" + standing)
			);
		}
		return clientModelSnapshot(
				blockEntityPos(bannerBlockEntity),
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				rootState,
				layers.toArray(ClientLayerSnapshot[]::new)
		);
	}

	private static ClientModelSnapshot captureChestBlockEntity(net.minecraft.world.level.block.entity.BlockEntity chestBlockEntity) {
		net.minecraft.world.level.block.state.BlockState state = chestBlockEntity.getBlockState();
		net.minecraft.world.level.block.state.properties.ChestType chestType = state.hasProperty(net.minecraft.world.level.block.ChestBlock.TYPE)
				? state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
				: net.minecraft.world.level.block.state.properties.ChestType.SINGLE;
		Direction facing = chestFacing(state);
		float open = chestBlockEntity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
				? chest.getOpenNess(0.0F)
				: chestBlockEntity instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity enderChest
				? enderChest.getOpenNess(0.0F)
				: 0.0F;
		float easedOpen = 1.0F - (float) Math.pow(1.0F - open, 3.0D);

		Map<String, Object> rootState = blockEntityRootState(blockEntityPos(chestBlockEntity));
		rootState.put("rootTranslateX", 0.5F);
		rootState.put("rootTranslateY", 0.5F);
		rootState.put("rootTranslateZ", 0.5F);
		rootState.put("rootRotateY", -facing.toYRot());
		rootState.put("rootPostTranslateX", -0.5F);
		rootState.put("rootPostTranslateY", -0.5F);
		rootState.put("rootPostTranslateZ", -0.5F);
		rootState.put("modelState", easedOpen);

		return clientModelSnapshot(
				blockEntityPos(chestBlockEntity),
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				rootState,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.object.chest.ChestModel",
								chestTexture(chestBlockEntity, chestType),
								0xFFFFFF,
								false
						).withFactory(chestLayerFactory(chestType))
				}
		);
	}

	private static ClientModelSnapshot captureSkullBlockEntity(net.minecraft.world.level.block.entity.SkullBlockEntity skullBlockEntity) {
		net.minecraft.world.level.block.state.BlockState state = skullBlockEntity.getBlockState();
		boolean wall = state.getBlock() instanceof net.minecraft.world.level.block.WallSkullBlock;
		Direction direction = wall ? state.getValue(net.minecraft.world.level.block.WallSkullBlock.FACING) : null;
		int rotationSegment = wall
				? net.minecraft.world.level.block.state.properties.RotationSegment.convertToSegment(direction.getOpposite())
				: state.getValue(net.minecraft.world.level.block.SkullBlock.ROTATION);
		float rotationDegrees = net.minecraft.world.level.block.state.properties.RotationSegment.convertToDegrees(rotationSegment);

		Map<String, Object> rootState = blockEntityRootState(blockEntityPos(skullBlockEntity));
		if (direction == null) {
			rootState.put("rootTranslateX", 0.5F);
			rootState.put("rootTranslateZ", 0.5F);
		} else {
			rootState.put("rootTranslateX", 0.5F - direction.getStepX() * 0.25F);
			rootState.put("rootTranslateY", 0.25F);
			rootState.put("rootTranslateZ", 0.5F - direction.getStepZ() * 0.25F);
		}
		rootState.put("rootScaleX", -1.0F);
		rootState.put("rootScaleY", -1.0F);
		rootState.put("rootScaleZ", 1.0F);
		rootState.put("animationPos", skullBlockEntity.getAnimation(0.0F));
		rootState.put("yRot", rotationDegrees);
		rootState.put("xRot", 0.0F);

		net.minecraft.world.level.block.SkullBlock.Type skullType = ((net.minecraft.world.level.block.AbstractSkullBlock) state.getBlock()).getType();
		ClientLayerSnapshot layer = skullLayer(skullType, skullBlockEntity);
		if (layer == null) {
			return null;
		}
		return clientModelSnapshot(
				blockEntityPos(skullBlockEntity),
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				rootState,
				new ClientLayerSnapshot[]{layer}
		);
	}

	private static LineSnapshot[] captureLeashLines(Entity entity) {
		if (!(entity instanceof Leashable leashable)) {
			return null;
		}
		Entity holder = leashable.getLeashHolder();
		if (holder == null) {
			return null;
		}

		float entityYawRadians = entity.getPreciseBodyRotation(0.0F) * ((float) Math.PI / 180.0F);
		Vec3 leashOffset = leashable.getLeashOffset(0.0F);
		int blockLightStart = blockLight(entity, entity.getEyePosition(0.0F));
		int blockLightEnd = blockLight(holder, holder.getEyePosition(0.0F));
		int skyLightStart = skyLight(entity.level(), entity.getEyePosition(0.0F));
		int skyLightEnd = skyLight(holder.level(), holder.getEyePosition(0.0F));

		if (holder.supportQuadLeashAsHolder() && leashable.supportQuadLeash()) {
			float holderYawRadians = holder.getPreciseBodyRotation(0.0F) * ((float) Math.PI / 180.0F);
			Vec3[] offsets = leashable.getQuadLeashOffsets();
			Vec3[] holderOffsets = holder.getQuadLeashHolderOffsets();
			Vec3 holderPos = holder.getPosition(0.0F);
			int count = Math.min(offsets.length, holderOffsets.length);
			LineSnapshot[] lines = new LineSnapshot[count];
			for (int i = 0; i < count; i++) {
				Vec3 startOffset = offsets[i].yRot(-entityYawRadians);
				Vec3 start = entity.getPosition(0.0F).add(startOffset);
				Vec3 end = holderPos.add(holderOffsets[i].yRot(-holderYawRadians));
				lines[i] = new LineSnapshot(
						entity.position(),
						start,
						end,
						0.7F,
						0.02F,
						LEASH_SEGMENT_TEXTURE
				);
			}
			return lines;
		}

		Vec3 start = entity.getPosition(0.0F).add(leashOffset.yRot(-entityYawRadians));
		Vec3 end = holder.getRopeHoldPosition(0.0F);
		return new LineSnapshot[]{
				new LineSnapshot(entity.position(), start, end, 0.7F, 0.02F, LEASH_SEGMENT_TEXTURE)
		};
	}

	private static int blockLight(Entity entity, Vec3 position) {
		if (entity.isOnFire()) {
			return 15;
		}
		return entity.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, BlockPos.containing(position));
	}

	private static int skyLight(net.minecraft.world.level.Level level, Vec3 position) {
		return level.getBrightness(net.minecraft.world.level.LightLayer.SKY, BlockPos.containing(position));
	}

	private static Vec3 fishingHookHandPosition(Player player, float swing) {
		HumanoidArm arm = player.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem
				? player.getMainArm()
				: player.getMainArm().getOpposite();
		double armSide = arm == HumanoidArm.RIGHT ? -1.0D : 1.0D;
		Vec3 bodyOffset = new Vec3(armSide * 0.35D, 0.8D * player.getScale(), 0.0D).yRot(-radians(player.getYRot()));
		Vec3 hand = player.getEyePosition().add(bodyOffset);
		if (swing == 0.0F) {
			return hand;
		}
		Vec3 swingOffset = new Vec3(armSide * 0.2D * swing, -0.05D * swing, 0.15D * swing).yRot(-radians(player.getYRot()));
		return hand.add(swingOffset);
	}

	private static ClientModelSnapshot captureVillagerClientModel(Villager villager) {
		VillagerData villagerData = villager.getVillagerData();
		Map<String, Object> state = livingStateFields(villager);
		state.put("villagerData", villagerData);

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		String modelClassName = "net.minecraft.client.model.npc.VillagerModel";
		layers.add(new ClientLayerSnapshot(modelClassName, VILLAGER_TEXTURE, 0xFFFFFF, false));
		addClientLayerIfPresent(layers, modelClassName, holderTexture(villagerData.type(), "entity/villager/type/"), 0xFFFFFF, false);
		if (!villager.isBaby() && !villagerData.profession().is(VillagerProfession.NONE)) {
			addClientLayerIfPresent(layers, modelClassName, holderTexture(villagerData.profession(), "entity/villager/profession/"), 0xFFFFFF, false);
			if (!villagerData.profession().is(VillagerProfession.NITWIT)) {
				addClientLayerIfPresent(layers, modelClassName, professionLevelTexture(villagerData.level()), 0xFFFFFF, false);
			}
		}

		return livingClientModelSnapshot(villager, villager.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static ClientModelSnapshot captureZombieClientModel(Zombie zombie) {
		Map<String, Object> state = livingStateFields(zombie);
		state.put("isAggressive", zombie.isAggressive());
		state.put("isConverting", zombie.isUnderWaterConverting());

		String modelClassName = zombie instanceof Drowned
				? "net.minecraft.client.model.monster.zombie.DrownedModel"
				: "net.minecraft.client.model.monster.zombie.ZombieModel";
		Identifier texture = zombie instanceof Husk
				? HUSK_TEXTURE
				: zombie instanceof Drowned ? DROWNED_TEXTURE : ZOMBIE_TEXTURE;
		return livingClientModelSnapshot(
				zombie,
				zombie.yBodyRot,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(modelClassName, texture, 0xFFFFFF, false)
				}
		);
	}

	private static ClientModelSnapshot captureSheepClientModel(Sheep sheep) {
		Map<String, Object> state = livingStateFields(sheep);
		state.put("headEatPositionScale", sheep.getHeadEatPositionScale(0.0F));
		state.put("headEatAngleScale", sheep.getHeadEatAngleScale(0.0F));
		state.put("isSheared", sheep.isSheared());
		state.put("woolColor", sheep.getColor());
		state.put("isJebSheep", sheep.hasCustomName() && "jeb_".equals(sheep.getName().getString()));

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		layers.add(new ClientLayerSnapshot(
				"net.minecraft.client.model.animal.sheep.SheepModel",
				SHEEP_TEXTURE,
				0xFFFFFF,
				false
		));
		if (!sheep.isSheared()) {
			boolean coloredUndercoat = sheep.hasCustomName()
					&& "jeb_".equals(sheep.getName().getString())
					|| sheep.getColor() != net.minecraft.world.item.DyeColor.WHITE;
			if (coloredUndercoat) {
				layers.add(new ClientLayerSnapshot(
						"net.minecraft.client.model.animal.sheep.SheepModel",
						SHEEP_WOOL_UNDERCOAT_TEXTURE,
						sheep.getColor().getTextureDiffuseColor(),
						false
				).withFactory("createBodyLayer").withRenderScale(1.001F));
			}
			layers.add(new ClientLayerSnapshot(
					"net.minecraft.client.model.animal.sheep.SheepFurModel",
					SHEEP_WOOL_TEXTURE,
					sheep.getColor().getTextureDiffuseColor(),
					false
			).withRenderScale(1.01F));
		}
		return livingClientModelSnapshot(sheep, sheep.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static ClientModelSnapshot captureCowClientModel(Cow cow) {
		Identifier texture = variantTexture(cow.getVariant(), Identifier.fromNamespaceAndPath("minecraft", "entity/cow/temperate_cow"));
		return livingClientModelSnapshot(
				cow,
				cow.yBodyRot,
				livingStateFields(cow),
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(cowModelClass(texture), texture, 0xFFFFFF, false)
				}
		);
	}

	private static ClientModelSnapshot capturePigClientModel(Pig pig) {
		Identifier texture = variantTexture(pig.getVariant(), Identifier.fromNamespaceAndPath("minecraft", "entity/pig/temperate_pig"));
		Map<String, Object> state = livingStateFields(pig);
		ItemStack saddle = pig.getItemBySlot(EquipmentSlot.SADDLE).copy();
		state.put("saddle", saddle);
		state.put("variant", pig.getVariant().value());

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		String modelClassName = pigModelClass(texture);
		layers.add(new ClientLayerSnapshot(modelClassName, texture, 0xFFFFFF, false));
		addEquipmentLayers(layers, modelClassName, "pig_saddle", saddle, null, 1.01F);

		return livingClientModelSnapshot(pig, pig.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static ClientModelSnapshot captureChickenClientModel(Chicken chicken) {
		Identifier texture = variantTexture(chicken.getVariant(), Identifier.fromNamespaceAndPath("minecraft", "entity/chicken/temperate_chicken"));
		Map<String, Object> state = livingStateFields(chicken);
		state.put("flap", chicken.flap);
		state.put("flapSpeed", chicken.oFlapSpeed + (chicken.flapSpeed - chicken.oFlapSpeed));
		state.put("variant", chicken.getVariant().value());
		return livingClientModelSnapshot(
				chicken,
				chicken.yBodyRot,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(chickenModelClass(texture), texture, 0xFFFFFF, false)
				}
		);
	}

	private static ClientModelSnapshot captureMushroomCowClientModel(MushroomCow mushroomCow) {
		Identifier texture = switch (mushroomCow.getVariant()) {
			case BROWN -> Identifier.fromNamespaceAndPath("minecraft", "entity/cow/brown_mooshroom");
			default -> Identifier.fromNamespaceAndPath("minecraft", "entity/cow/red_mooshroom");
		};
		return captureSimpleClientModel(
				mushroomCow,
				"net.minecraft.client.model.animal.cow.CowModel",
				texture
		);
	}

	private static ClientModelSnapshot captureGoatClientModel(Goat goat) {
		Map<String, Object> state = livingStateFields(goat);
		state.put("hasLeftHorn", goat.hasLeftHorn());
		state.put("hasRightHorn", goat.hasRightHorn());
		state.put("rammingXHeadRot", goat.getRammingXHeadRot());
		return livingClientModelSnapshot(
				goat,
				goat.yBodyRot,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.animal.goat.GoatModel",
								Identifier.fromNamespaceAndPath("minecraft", "entity/goat/goat"),
								0xFFFFFF,
								false
						)
				}
		);
	}

	private static ClientModelSnapshot captureHorseClientModel(Horse horse) {
		Map<String, Object> state = livingStateFields(horse);
		ItemStack saddle = horse.getItemBySlot(EquipmentSlot.SADDLE).copy();
		state.put("saddle", saddle);
		state.put("bodyArmorItem", horse.getBodyArmorItem().copy());
		state.put("isRidden", horse.isVehicle());
		state.put("animateTail", horse.tailCounter > 0);
		state.put("eatAnimation", horse.getEatAnim(0.0F));
		state.put("standAnimation", horse.getStandAnim(0.0F));
		state.put("feedingAnimation", horse.getMouthAnim(0.0F));
		state.put("variant", horse.getVariant());
		state.put("markings", horse.getMarkings());

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		layers.add(new ClientLayerSnapshot(
				"net.minecraft.client.model.animal.equine.HorseModel",
				horseTexture(horse),
				0xFFFFFF,
				false
		));
		Identifier markingsTexture = horseMarkingsTexture(horse.getMarkings());
		if (markingsTexture != null) {
			layers.add(new ClientLayerSnapshot(
					"net.minecraft.client.model.animal.equine.HorseModel",
					markingsTexture,
					0xFFFFFF,
					false
			));
		}
		addEquipmentLayers(
				layers,
				"net.minecraft.client.model.animal.equine.EquineSaddleModel",
				"horse_saddle",
				saddle,
				null,
				1.01F
		);
		addEquipmentLayers(
				layers,
				"net.minecraft.client.model.animal.equine.HorseModel",
				"horse_body",
				horse.getBodyArmorItem(),
				null,
				1.01F
		);
		return livingClientModelSnapshot(
				horse,
				horse.yBodyRot,
				state,
				layers.toArray(ClientLayerSnapshot[]::new)
		);
	}

	private static ClientModelSnapshot captureSpiderClientModel(LivingEntity livingEntity, Identifier texture) {
		return livingClientModelSnapshot(
				livingEntity,
				livingEntity.yBodyRot,
				livingStateFields(livingEntity),
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.spider.SpiderModel",
								texture,
								0xFFFFFF,
								false
						),
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.spider.SpiderModel",
								SPIDER_EYES_TEXTURE,
								0xFFFFFF,
								true
						)
				}
		);
	}

	private static ClientModelSnapshot captureEndermanClientModel(EnderMan enderMan) {
		Map<String, Object> state = livingStateFields(enderMan);
		state.put("carriedBlock", enderMan.getCarriedBlock());
		state.put("creepy", enderMan.isCreepy());

		return livingClientModelSnapshot(
				enderMan,
				enderMan.yBodyRot,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.enderman.EndermanModel",
								ENDERMAN_TEXTURE,
								0xFFFFFF,
								false
						),
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.enderman.EndermanModel",
								ENDERMAN_EYES_TEXTURE,
								0xFFFFFF,
								true
						)
				}
		);
	}

	private static ClientModelSnapshot captureIronGolemClientModel(net.minecraft.world.entity.animal.golem.IronGolem ironGolem) {
		Map<String, Object> state = livingStateFields(ironGolem);
		state.put("attackTicksRemaining", Math.max(ironGolem.getAttackAnimationTick(), 0));
		state.put("offerFlowerTick", ironGolem.getOfferFlowerTick());
		state.put("crackiness", ironGolem.getCrackiness());

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		String modelClassName = "net.minecraft.client.model.animal.golem.IronGolemModel";
		layers.add(new ClientLayerSnapshot(modelClassName, minecraftTexture("entity/iron_golem/iron_golem"), 0xFFFFFF, false));
		addClientLayerIfPresent(layers, modelClassName, ironGolemCrackinessTexture(ironGolem.getCrackiness()), 0xFFFFFF, false);
		return livingClientModelSnapshot(ironGolem, ironGolem.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static ClientModelSnapshot captureBoatClientModel(AbstractBoat boat) {
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(boat.getType());
		if (typeId == null || !"minecraft".equals(typeId.getNamespace())) {
			return null;
		}

		String typePath = typeId.getPath();
		String woodType = boatWoodType(typePath);
		if (woodType == null) {
			return null;
		}

		boolean chest = typePath.contains("chest");
		boolean raft = typePath.contains("raft");
		String modelClassName = raft
				? "net.minecraft.client.model.object.boat.RaftModel"
				: "net.minecraft.client.model.object.boat.BoatModel";
		String layerFactoryMethodName = raft
				? chest ? "createChestRaftModel" : "createRaftModel"
				: chest ? "createChestBoatModel" : "createBoatModel";
		Identifier texture = minecraftTexture((chest ? "entity/chest_boat/" : "entity/boat/") + woodType);

		Map<String, Object> state = entityStateFields(boat);
		state.put("yRot", boat.getYRot(0.0F));
		state.put("hurtTime", boat.getHurtTime());
		state.put("hurtDir", boat.getHurtDir());
		state.put("damageTime", boat.getDamage());
		state.put("bubbleAngle", boat.getBubbleAngle(0.0F));
		state.put("isUnderWater", boat.isUnderWater());
		state.put("rowingTimeLeft", boat.getRowingTime(0, 0.0F));
		state.put("rowingTimeRight", boat.getRowingTime(1, 0.0F));

		return clientModelSnapshot(
				boat.position(),
				boat.getYRot(0.0F),
				0.0F,
				1.0F,
				ClientModelTransformKind.BOAT,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(modelClassName, texture, 0xFFFFFF, false).withFactory(layerFactoryMethodName)
				}
		);
	}

	private static ClientModelSnapshot captureSimpleClientModel(LivingEntity livingEntity, String modelClassName, Identifier texture) {
		return captureSimpleClientModel(livingEntity, livingEntity.yBodyRot, modelClassName, texture);
	}

	private static ClientModelSnapshot captureSimpleClientModel(LivingEntity livingEntity, float rootYaw, String modelClassName, Identifier texture) {
		return livingClientModelSnapshot(
				livingEntity,
				rootYaw,
				livingStateFields(livingEntity),
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(modelClassName, texture, 0xFFFFFF, false)
				}
		);
	}

	static ClientModelSnapshot livingClientModelSnapshot(
			LivingEntity livingEntity,
			float rootYaw,
			Map<String, Object> stateFields,
			ClientLayerSnapshot[] layers
	) {
		return clientModelSnapshot(
				livingEntity.position(),
				rootYaw,
				180.0F,
				livingEntity.getScale(),
				ClientModelTransformKind.LIVING,
				stateFields,
				appendLivingEquipmentLayers(livingEntity, layers)
		);
	}

	private static ClientLayerSnapshot[] appendLivingEquipmentLayers(LivingEntity livingEntity, ClientLayerSnapshot[] baseLayers) {
		if (livingEntity == null) {
			return baseLayers;
		}
		List<ClientLayerSnapshot> layers = new ArrayList<>();
		if (baseLayers != null) {
			for (ClientLayerSnapshot layer : baseLayers) {
				if (layer != null) {
					layers.add(layer);
				}
			}
		}
		if (layers.isEmpty()) {
			return baseLayers;
		}

		if (livingEntity instanceof ArmorStand) {
			addExactArmorLayers(layers, livingEntity, "net.minecraft.client.model.object.armorstand.ArmorStandArmorModel", "createArmorLayerSet");
		} else if (supportsExactHumanoidArmor(livingEntity, layers.get(0).modelClassName())) {
			addExactArmorLayers(layers, livingEntity, "net.minecraft.client.model.HumanoidModel", "createArmorMeshSet");
		}
		return layers.toArray(ClientLayerSnapshot[]::new);
	}

	private static boolean supportsExactHumanoidArmor(LivingEntity livingEntity, String baseModelClassName) {
		if (livingEntity == null || baseModelClassName == null) {
			return false;
		}
		if (CameraEntityFixups.usesManualHumanoidBase(livingEntity)) {
			return false;
		}
		if (!hasAnyHumanoidArmor(livingEntity)) {
			return false;
		}
		return baseModelClassName.contains("Humanoid")
				|| baseModelClassName.contains("PiglinModel")
				|| baseModelClassName.contains("IllagerModel")
				|| baseModelClassName.contains("ZombieModel")
				|| baseModelClassName.contains("SkeletonModel")
				|| baseModelClassName.contains("PlayerModel");
	}

	private static boolean hasAnyHumanoidArmor(LivingEntity livingEntity) {
		return shouldRenderHumanoidArmor(livingEntity.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD)
				|| shouldRenderHumanoidArmor(livingEntity.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST)
				|| shouldRenderHumanoidArmor(livingEntity.getItemBySlot(EquipmentSlot.LEGS), EquipmentSlot.LEGS)
				|| shouldRenderHumanoidArmor(livingEntity.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET);
	}

	private static void addExactArmorLayers(List<ClientLayerSnapshot> layers, LivingEntity livingEntity, String modelClassName, String layerFactoryMethodName) {
		addExactArmorLayers(layers, livingEntity.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD, modelClassName, layerFactoryMethodName);
		addExactArmorLayers(layers, livingEntity.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST, modelClassName, layerFactoryMethodName);
		addExactArmorLayers(layers, livingEntity.getItemBySlot(EquipmentSlot.LEGS), EquipmentSlot.LEGS, modelClassName, layerFactoryMethodName);
		addExactArmorLayers(layers, livingEntity.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET, modelClassName, layerFactoryMethodName);
	}

	private static void addExactArmorLayers(List<ClientLayerSnapshot> layers, ItemStack stack, EquipmentSlot slot, String modelClassName, String layerFactoryMethodName) {
		for (EquipmentVisualLayer visualLayer : collectHumanoidArmorVisualLayers(stack, slot)) {
			ClientLayerSnapshot layer = new ClientLayerSnapshot(
					modelClassName,
					visualLayer.texture(),
					visualLayer.tintRgb(),
					false
			)
					.withFactory(layerFactoryMethodName + ":" + slot.name())
					.withCubeDeformation(0.25F)
					.withSecondaryCubeDeformation(0.5F)
					.withRenderScale(visualLayer.dynamicImage() == null ? 1.0F : 1.001F);
			if (visualLayer.dynamicImage() != null) {
				layer = layer.withDynamicImage(visualLayer.dynamicImageKey(), visualLayer.dynamicImage());
			}
			layers.add(layer);
		}
	}

	private static ClientModelSnapshot clientModelSnapshot(
			Vec3 position,
			float rootYaw,
			float rootYawOffsetDegrees,
			float rootScale,
			ClientModelTransformKind transformKind,
			Map<String, Object> stateFields,
			ClientLayerSnapshot[] layers
	) {
		return new ClientModelSnapshot(position, rootYaw, rootYawOffsetDegrees, rootScale, transformKind, stateFields, layers);
	}

	static void addClientLayerIfPresent(List<ClientLayerSnapshot> layers, String modelClassName, Identifier texture, int tintRgb, boolean emissive) {
		if (texture != null) {
			layers.add(new ClientLayerSnapshot(modelClassName, texture, tintRgb, emissive));
		}
	}

	private static Map<String, Object> entityStateFields(Entity entity) {
		Map<String, Object> state = new HashMap<>();
		state.put("entityType", entity.getType());
		state.put("x", entity.getX());
		state.put("y", entity.getY());
		state.put("z", entity.getZ());
		state.put("ageInTicks", (float) entity.tickCount);
		state.put("boundingBoxWidth", entity.getBbWidth());
		state.put("boundingBoxHeight", entity.getBbHeight());
		state.put("eyeHeight", entity.getEyeHeight());
		state.put("isInvisible", entity.isInvisible());
		state.put("isDiscrete", entity.isDiscrete());
		state.put("displayFireAnimation", entity.displayFireAnimation());
		state.put("outlineColor", 0);
		state.put("lightCoords", 0);
		return state;
	}

	static Map<String, Object> livingStateFields(LivingEntity livingEntity) {
		Map<String, Object> state = entityStateFields(livingEntity);
		state.put("bodyRot", livingEntity.yBodyRot);
		state.put("yRot", wrapDegrees(livingEntity.yHeadRot - livingEntity.yBodyRot));
		state.put("xRot", livingEntity.getXRot());
		state.put("walkAnimationPos", livingEntity.walkAnimation.position());
		state.put("walkAnimationSpeed", Mth.clamp(livingEntity.walkAnimation.speed(), 0.0F, 1.0F));
		state.put("scale", livingEntity.getScale());
		state.put("ageScale", livingEntity.getAgeScale());
		state.put("baby", livingEntity.isBaby());
		state.put("isBaby", livingEntity.isBaby());
		state.put("isInWater", livingEntity.isInWater());
		state.put("isAutoSpinAttack", livingEntity.isAutoSpinAttack());
		state.put("bedOrientation", sleepingDirection(livingEntity));
		state.put("pose", livingEntity.getPose());
		state.put("mainArm", livingEntity.getMainArm());
		state.put("attackTime", livingEntity.getAttackAnim(0.0F));
		state.put("swimAmount", livingEntity.getSwimAmount(0.0F));
		state.put("isCrouching", livingEntity.isCrouching());
		state.put("isFallFlying", livingEntity.isFallFlying());
		state.put("isVisuallySwimming", livingEntity.isVisuallySwimming());
		state.put("isPassenger", livingEntity.isPassenger());
		state.put("isUsingItem", livingEntity.isUsingItem());
		state.put("ticksUsingItem", livingEntity.isUsingItem() ? (float) livingEntity.getTicksUsingItem() : 0.0F);
		state.put("speedValue", Mth.clamp(livingEntity.walkAnimation.speed(), 0.0F, 1.0F));
		state.put("distanceToCameraSq", 0.0D);
		state.put("eyePosition", livingEntity.getEyePosition());
		state.put("lookDirection", livingEntity.getLookAngle());
		state.put("lookAtPosition", livingEntity.getEyePosition().add(livingEntity.getLookAngle()));
		state.put("attackTargetPosition", livingEntity.getEyePosition().add(livingEntity.getLookAngle()));
		state.put("mainArm", livingEntity.getMainArm());
		state.put("attackArm", livingEntity.getMainArm());
		state.put("useItemHand", livingEntity.isUsingItem() ? livingEntity.getUsedItemHand() : InteractionHand.MAIN_HAND);
		ItemStack leftHandStack = livingEntity.getItemInHand(physicalHand(livingEntity, HumanoidArm.LEFT)).copy();
		ItemStack rightHandStack = livingEntity.getItemInHand(physicalHand(livingEntity, HumanoidArm.RIGHT)).copy();
		state.put("leftHandItemStack", leftHandStack);
		state.put("rightHandItemStack", rightHandStack);
		state.put("headEquipment", livingEntity.getItemBySlot(EquipmentSlot.HEAD).copy());
		state.put("chestEquipment", livingEntity.getItemBySlot(EquipmentSlot.CHEST).copy());
		state.put("legsEquipment", livingEntity.getItemBySlot(EquipmentSlot.LEGS).copy());
		state.put("feetEquipment", livingEntity.getItemBySlot(EquipmentSlot.FEET).copy());
		String leftArmPose = !leftHandStack.isEmpty() ? "ITEM" : "EMPTY";
		String rightArmPose = !rightHandStack.isEmpty() ? "ITEM" : "EMPTY";
		if (livingEntity.isUsingItem()) {
			if (livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND) {
				rightArmPose = livingEntity.getMainArm() == HumanoidArm.RIGHT ? "ITEM" : rightArmPose;
				leftArmPose = livingEntity.getMainArm() == HumanoidArm.LEFT ? "ITEM" : leftArmPose;
			} else {
				rightArmPose = livingEntity.getMainArm() == HumanoidArm.LEFT ? "ITEM" : rightArmPose;
				leftArmPose = livingEntity.getMainArm() == HumanoidArm.RIGHT ? "ITEM" : leftArmPose;
			}
		}
		state.put("leftArmPose", leftArmPose);
		state.put("rightArmPose", rightArmPose);
		boolean rightUsingItem = livingEntity.isUsingItem() && livingEntity.getUsedItemHand() == physicalHand(livingEntity, HumanoidArm.RIGHT);
		boolean leftUsingItem = livingEntity.isUsingItem() && livingEntity.getUsedItemHand() == physicalHand(livingEntity, HumanoidArm.LEFT);
		state.put("rightHandUsingItem", rightUsingItem);
		state.put("leftHandUsingItem", leftUsingItem);
		state.put("rightHandUseTicks", rightUsingItem ? (float) livingEntity.getTicksUsingItem() : 0.0F);
		state.put("leftHandUseTicks", leftUsingItem ? (float) livingEntity.getTicksUsingItem() : 0.0F);
		boolean fishingRodCast = livingEntity instanceof Player player && player.fishing != null;
		state.put("rightFishingRodCast", fishingRodCast && !rightHandStack.isEmpty() && rightHandStack.getItem() instanceof net.minecraft.world.item.FishingRodItem);
		state.put("leftFishingRodCast", fishingRodCast && !leftHandStack.isEmpty() && leftHandStack.getItem() instanceof net.minecraft.world.item.FishingRodItem);
		state.put("contextDimensionId", livingEntity.level() == null ? null : livingEntity.level().dimension().identifier().toString());
		state.put("gameTime", livingEntity.level() == null ? 0L : livingEntity.level().getGameTime());
		state.put("dayTime", livingEntity.level() == null ? 0L : livingEntity.level().getDayTime());
		state.put("beamOffset", Vec3.ZERO);
		state.put("renderOffset", Vec3.ZERO);
		state.put("attachFace", Direction.DOWN);
		return state;
	}

	private static Map<String, ClientModelResolver> buildVanillaClientModelRules() {
		Map<String, ClientModelResolver> rules = new HashMap<>();

		rules.put("sheep", livingEntity -> captureSheepClientModel((Sheep) livingEntity));
		rules.put("cow", livingEntity -> captureCowClientModel((Cow) livingEntity));
		rules.put("pig", livingEntity -> capturePigClientModel((Pig) livingEntity));
		rules.put("chicken", livingEntity -> captureChickenClientModel((Chicken) livingEntity));
		rules.put("enderman", livingEntity -> captureEndermanClientModel((EnderMan) livingEntity));
		rules.put("spider", livingEntity -> captureSpiderClientModel(livingEntity, SPIDER_TEXTURE));
		rules.put("cave_spider", livingEntity -> captureSpiderClientModel(livingEntity, minecraftTexture("entity/spider/cave_spider")));
		rules.put("mooshroom", livingEntity -> captureMushroomCowClientModel((MushroomCow) livingEntity));
		rules.put("goat", livingEntity -> captureGoatClientModel((Goat) livingEntity));
		rules.put("armadillo", livingEntity -> captureSimpleClientModel(
				livingEntity,
				livingEntity.yBodyRot,
				"net.minecraft.client.model.animal.armadillo.ArmadilloModel",
				minecraftTexture("entity/armadillo")
		));
		rules.put("cat", livingEntity -> captureSimpleClientModel(
				livingEntity,
				livingEntity.yBodyRot,
				"net.minecraft.client.model.animal.feline.CatModel",
				catTexture((Cat) livingEntity)
		));
		rules.put("ocelot", livingEntity -> captureSimpleClientModel(
				livingEntity,
				livingEntity.yBodyRot,
				"net.minecraft.client.model.animal.feline.OcelotModel",
				minecraftTexture("entity/cat/ocelot")
		));
		rules.put("horse", livingEntity -> captureHorseClientModel((Horse) livingEntity));
		rules.put("fox", livingEntity -> captureSimpleClientModel(
				livingEntity,
				livingEntity.yBodyRot,
				"net.minecraft.client.model.animal.fox.FoxModel",
				foxTexture((Fox) livingEntity)
		));
		rules.put("rabbit", livingEntity -> captureSimpleClientModel(
				livingEntity,
				livingEntity.yBodyRot,
				"net.minecraft.client.model.animal.rabbit.RabbitModel",
				rabbitTexture((Rabbit) livingEntity)
		));
		rules.put("panda", livingEntity -> captureSimpleClientModel(
				livingEntity,
				livingEntity.yBodyRot,
				"net.minecraft.client.model.animal.panda.PandaModel",
				pandaTexture((Panda) livingEntity)
		));
		rules.put("polar_bear", livingEntity -> captureSimpleClientModel(
				livingEntity,
				livingEntity.yBodyRot,
				"net.minecraft.client.model.animal.polarbear.PolarBearModel",
				minecraftTexture("entity/bear/polarbear")
		));

		registerSimpleClientRule(rules, "allay", "net.minecraft.client.model.animal.allay.AllayModel", simpleMobTexture("allay"));
		registerSimpleClientRule(rules, "axolotl", "net.minecraft.client.model.animal.axolotl.AxolotlModel", minecraftTexture("entity/axolotl/axolotl_blue"));
		registerSimpleClientRule(rules, "bat", "net.minecraft.client.model.ambient.BatModel", minecraftTexture("entity/bat"));
		registerSimpleClientRule(rules, "bee", "net.minecraft.client.model.animal.bee.BeeModel", minecraftTexture("entity/bee/bee"));
		registerSimpleClientRule(rules, "camel", "net.minecraft.client.model.animal.camel.CamelModel", simpleMobTexture("camel"));
		registerSimpleClientRule(rules, "cod", "net.minecraft.client.model.animal.fish.CodModel", minecraftTexture("entity/fish/cod"));
		registerSimpleClientRule(rules, "creaking", "net.minecraft.client.model.monster.creaking.CreakingModel", minecraftTexture("entity/creaking/creaking"));
		registerSimpleClientRule(rules, "dolphin", "net.minecraft.client.model.animal.dolphin.DolphinModel", minecraftTexture("entity/dolphin"));
		registerSimpleClientRule(rules, "donkey", "net.minecraft.client.model.animal.equine.DonkeyModel", minecraftTexture("entity/horse/donkey"));
		registerSimpleClientRule(rules, "elder_guardian", "net.minecraft.client.model.monster.guardian.GuardianModel", minecraftTexture("entity/guardian_elder"));
		registerSimpleClientRule(rules, "endermite", "net.minecraft.client.model.monster.endermite.EndermiteModel", minecraftTexture("entity/endermite"));
		registerSimpleClientRule(rules, "evoker", "net.minecraft.client.model.monster.illager.IllagerModel", minecraftTexture("entity/illager/evoker"));
		registerSimpleClientRule(rules, "frog", "net.minecraft.client.model.animal.frog.FrogModel", minecraftTexture("entity/frog/temperate_frog"));
		rules.put("iron_golem", livingEntity -> captureIronGolemClientModel((net.minecraft.world.entity.animal.golem.IronGolem) livingEntity));
		registerSimpleClientRule(rules, "illusioner", "net.minecraft.client.model.monster.illager.IllagerModel", minecraftTexture("entity/illager/illusioner"));
		rules.put("llama", livingEntity -> captureLlamaClientModel((Llama) livingEntity));
		registerSimpleClientRule(rules, "magma_cube", "net.minecraft.client.model.monster.slime.MagmaCubeModel", minecraftTexture("entity/slime/magmacube"));
		registerSimpleClientRule(rules, "mule", "net.minecraft.client.model.animal.equine.DonkeyModel", minecraftTexture("entity/horse/mule"));
		registerSimpleClientRule(rules, "parrot", "net.minecraft.client.model.animal.parrot.ParrotModel", minecraftTexture("entity/parrot/parrot_red_blue"));
		registerSimpleClientRule(rules, "phantom", "net.minecraft.client.model.monster.phantom.PhantomModel", simpleMobTexture("phantom"));
		registerSimpleClientRule(rules, "piglin", "net.minecraft.client.model.monster.piglin.PiglinModel", minecraftTexture("entity/piglin/piglin"));
		registerSimpleClientRule(rules, "piglin_brute", "net.minecraft.client.model.monster.piglin.PiglinModel", minecraftTexture("entity/piglin/piglin_brute"));
		registerSimpleClientRule(rules, "pillager", "net.minecraft.client.model.monster.illager.IllagerModel", minecraftTexture("entity/illager/pillager"));
		registerSimpleClientRule(rules, "pufferfish", "net.minecraft.client.model.animal.fish.PufferfishBigModel", minecraftTexture("entity/fish/pufferfish"));
		registerSimpleClientRule(rules, "ravager", "net.minecraft.client.model.monster.ravager.RavagerModel", minecraftTexture("entity/illager/ravager"));
		registerSimpleClientRule(rules, "salmon", "net.minecraft.client.model.animal.fish.SalmonModel", minecraftTexture("entity/fish/salmon"));
		registerSimpleClientRule(rules, "skeleton_horse", "net.minecraft.client.model.animal.equine.HorseModel", minecraftTexture("entity/horse/horse_skeleton"));
		registerSimpleClientRule(rules, "snow_golem", "net.minecraft.client.model.animal.golem.SnowGolemModel", minecraftTexture("entity/snow_golem"));
		registerSimpleClientRule(rules, "stray", "net.minecraft.client.model.monster.skeleton.SkeletonModel", minecraftTexture("entity/skeleton/stray"));
		registerSimpleClientRule(rules, "tadpole", "net.minecraft.client.model.animal.frog.TadpoleModel", minecraftTexture("entity/frog/tadpole"));
		rules.put("trader_llama", livingEntity -> captureLlamaClientModel((Llama) livingEntity));
		registerSimpleClientRule(rules, "turtle", "net.minecraft.client.model.animal.turtle.TurtleModel", minecraftTexture("entity/turtle/big_sea_turtle"));
		registerSimpleClientRule(rules, "vex", "net.minecraft.client.model.monster.vex.VexModel", minecraftTexture("entity/illager/vex"));
		registerSimpleClientRule(rules, "vindicator", "net.minecraft.client.model.monster.illager.IllagerModel", minecraftTexture("entity/illager/vindicator"));
		registerSimpleClientRule(rules, "wandering_trader", "net.minecraft.client.model.npc.VillagerModel", minecraftTexture("entity/wandering_trader"));
		registerSimpleClientRule(rules, "wither_skeleton", "net.minecraft.client.model.monster.skeleton.SkeletonModel", minecraftTexture("entity/skeleton/wither_skeleton"));
		registerSimpleClientRule(rules, "zoglin", "net.minecraft.client.model.monster.hoglin.HoglinModel", minecraftTexture("entity/hoglin/zoglin"));
		registerSimpleClientRule(rules, "zombie_horse", "net.minecraft.client.model.animal.equine.HorseModel", minecraftTexture("entity/horse/horse_zombie"));
		CameraEntityFixups.registerClientModelRules(rules);

		rules.put("ender_dragon", CameraEntityRenderer::captureEnderDragonClientModel);
		rules.put("wither", CameraEntityRenderer::captureWitherClientModel);

		return Map.copyOf(rules);
	}

	private static void registerSimpleClientRule(Map<String, ClientModelResolver> rules, String typePath, String modelClassName, Identifier texture) {
		rules.put(typePath, livingEntity -> captureSimpleClientModel(livingEntity, livingEntity.yBodyRot, modelClassName, texture));
	}

	private static ClientModelSnapshot captureVanillaClientModel(LivingEntity livingEntity) {
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(livingEntity.getType());
		if (typeId == null || !"minecraft".equals(typeId.getNamespace())) {
			return null;
		}

		ClientModelResolver directResolver = VANILLA_CLIENT_MODEL_RULES.get(typeId.getPath());
		if (directResolver != null) {
			return directResolver.capture(livingEntity);
		}
		return captureConventionClientModel(livingEntity, typeId.getPath());
	}

	private static ClientModelSnapshot captureConventionClientModel(LivingEntity livingEntity, String typePath) {
		Identifier texture = guessVanillaEntityTexture(livingEntity, typePath);
		if (texture == null) {
			return null;
		}

		for (String modelClassName : guessVanillaModelClasses(livingEntity, typePath)) {
			if (VanillaClientModels.hasModelClass(modelClassName)) {
				return captureSimpleClientModel(livingEntity, livingEntity.yBodyRot, modelClassName, texture);
			}
		}
		return null;
	}

	private static List<String> guessVanillaModelClasses(LivingEntity livingEntity, String typePath) {
		String serverPackage = livingEntity.getClass().getPackageName();
		if (!serverPackage.startsWith("net.minecraft.world.entity.")) {
			return List.of();
		}

		String simpleName = livingEntity.getClass().getSimpleName();
		String packageSuffix = serverPackage.substring("net.minecraft.world.entity.".length());
		String typePackage = typePath.replace("_", "");
		List<String> candidates = new ArrayList<>();
		candidates.add("net.minecraft.client.model." + packageSuffix + "." + simpleName + "Model");
		candidates.add("net.minecraft.client.model." + packageSuffix + "." + typePackage + "." + simpleName + "Model");

		int firstDot = packageSuffix.indexOf('.');
		if (firstDot > 0) {
			String rootCategory = packageSuffix.substring(0, firstDot);
			candidates.add("net.minecraft.client.model." + rootCategory + "." + typePackage + "." + simpleName + "Model");
		}
		return candidates;
	}

	private static Identifier guessVanillaEntityTexture(LivingEntity livingEntity, String typePath) {
		String serverPackage = livingEntity.getClass().getPackageName();
		String packageSuffix = serverPackage.startsWith("net.minecraft.world.entity.")
				? serverPackage.substring("net.minecraft.world.entity.".length())
				: "";
		String lastSegment = packageSuffix.isBlank()
				? ""
				: packageSuffix.substring(packageSuffix.lastIndexOf('.') + 1);

		List<String> candidates = new ArrayList<>();
		candidates.add("entity/" + typePath);
		candidates.add("entity/" + typePath + "/" + typePath);
		if (!lastSegment.isBlank()) {
			candidates.add("entity/" + lastSegment + "/" + typePath);
		}
		return firstExistingMinecraftTexture(candidates);
	}

	private static ClientModelSnapshot captureEnderDragonClientModel(LivingEntity livingEntity) {
		Map<String, Object> state = livingStateFields(livingEntity);
		state.put("flapTime", (float) livingEntity.tickCount);
		state.put("beamOffset", Vec3.ZERO);
		state.put("deathTime", 0.0F);

		return clientModelSnapshot(
				livingEntity.position(),
				livingEntity.yBodyRot,
				0.0F,
				livingEntity.getScale(),
				ClientModelTransformKind.LIVING,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.dragon.EnderDragonModel",
								minecraftTexture("entity/enderdragon/dragon"),
								0xFFFFFF,
								false
						),
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.dragon.EnderDragonModel",
								minecraftTexture("entity/enderdragon/dragon_eyes"),
								0xFFFFFF,
								true
						)
				}
		);
	}

	private static ClientModelSnapshot captureWitherClientModel(LivingEntity livingEntity) {
		Map<String, Object> state = livingStateFields(livingEntity);
		state.put("xHeadRots", new float[]{0.0F, 0.0F});
		state.put("yHeadRots", new float[]{0.0F, 0.0F});
		state.put("invulnerableTicks", 0.0F);
		state.put("isPowered", false);
		return livingClientModelSnapshot(
				livingEntity,
				livingEntity.yBodyRot,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.wither.WitherBossModel",
								minecraftTexture("entity/wither/wither"),
								0xFFFFFF,
								false
						)
				}
		);
	}

	private static Identifier simpleMobTexture(String typePath) {
		return firstExistingMinecraftTexture(List.of(
				"entity/" + typePath,
				"entity/" + typePath + "/" + typePath
		));
	}

	static Identifier minecraftTexture(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}

	private static Identifier firstExistingMinecraftTexture(List<String> candidates) {
		for (String candidate : candidates) {
			Identifier texture = minecraftTexture(candidate);
			if (ASSETS.loadTexture(texture) != null) {
				return texture;
			}
		}
		return null;
	}

	private static String cowModelClass(Identifier texture) {
		if (texture != null && texture.getPath().contains("warm")) {
			return "net.minecraft.client.model.animal.cow.WarmCowModel";
		}
		if (texture != null && texture.getPath().contains("cold")) {
			return "net.minecraft.client.model.animal.cow.ColdCowModel";
		}
		return "net.minecraft.client.model.animal.cow.CowModel";
	}

	private static String pigModelClass(Identifier texture) {
		if (texture != null && texture.getPath().contains("cold")) {
			return "net.minecraft.client.model.animal.pig.ColdPigModel";
		}
		return "net.minecraft.client.model.animal.pig.PigModel";
	}

	private static String chickenModelClass(Identifier texture) {
		if (texture != null && texture.getPath().contains("cold")) {
			return "net.minecraft.client.model.animal.chicken.ColdChickenModel";
		}
		return "net.minecraft.client.model.animal.chicken.ChickenModel";
	}

	private static Identifier foxTexture(Fox fox) {
		return fox.getVariant() == Fox.Variant.SNOW
				? Identifier.fromNamespaceAndPath("minecraft", "entity/fox/snow_fox")
				: Identifier.fromNamespaceAndPath("minecraft", "entity/fox/fox");
	}

	private static Identifier rabbitTexture(Rabbit rabbit) {
		if (rabbit.hasCustomName() && "Toast".equals(rabbit.getName().getString())) {
			return Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/toast");
		}
		return switch (rabbit.getVariant()) {
			case WHITE -> Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/white");
			case BLACK -> Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/black");
			case WHITE_SPLOTCHED -> Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/white_splotched");
			case GOLD -> Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/gold");
			case SALT -> Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/salt");
			case EVIL -> Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/caerbannog");
			default -> Identifier.fromNamespaceAndPath("minecraft", "entity/rabbit/brown");
		};
	}

	private static Identifier pandaTexture(Panda panda) {
		return switch (panda.getVariant()) {
			case LAZY -> Identifier.fromNamespaceAndPath("minecraft", "entity/panda/lazy_panda");
			case WORRIED -> Identifier.fromNamespaceAndPath("minecraft", "entity/panda/worried_panda");
			case PLAYFUL -> Identifier.fromNamespaceAndPath("minecraft", "entity/panda/playful_panda");
			case BROWN -> Identifier.fromNamespaceAndPath("minecraft", "entity/panda/brown_panda");
			case WEAK -> Identifier.fromNamespaceAndPath("minecraft", "entity/panda/weak_panda");
			case AGGRESSIVE -> Identifier.fromNamespaceAndPath("minecraft", "entity/panda/aggressive_panda");
			default -> Identifier.fromNamespaceAndPath("minecraft", "entity/panda/panda");
		};
	}

	private static Identifier catTexture(Cat cat) {
		Identifier texture = holderTexture(cat.getVariant(), "entity/cat/");
		return texture != null ? texture : Identifier.fromNamespaceAndPath("minecraft", "entity/cat/tabby");
	}

	private static Identifier llamaTexture(Llama llama) {
		return switch (llama.getVariant()) {
			case WHITE -> minecraftTexture("entity/llama/white");
			case BROWN -> minecraftTexture("entity/llama/brown");
			case GRAY -> minecraftTexture("entity/llama/gray");
			default -> minecraftTexture("entity/llama/creamy");
		};
	}

	private static Identifier horseTexture(Horse horse) {
		return Identifier.fromNamespaceAndPath(
				"minecraft",
				"entity/horse/horse_" + horse.getVariant().getSerializedName()
		);
	}

	private static Identifier horseMarkingsTexture(Markings markings) {
		return switch (markings) {
			case WHITE -> Identifier.fromNamespaceAndPath("minecraft", "entity/horse/horse_markings_white");
			case WHITE_FIELD -> Identifier.fromNamespaceAndPath("minecraft", "entity/horse/horse_markings_whitefield");
			case WHITE_DOTS -> Identifier.fromNamespaceAndPath("minecraft", "entity/horse/horse_markings_whitedots");
			case BLACK_DOTS -> Identifier.fromNamespaceAndPath("minecraft", "entity/horse/horse_markings_blackdots");
			default -> null;
		};
	}

	private static void addEquipmentLayers(
			List<ClientLayerSnapshot> layers,
			String modelClassName,
			String layerType,
			ItemStack stack,
			Identifier assetIdOverride,
			float renderScale
	) {
		for (EquipmentVisualLayer visualLayer : collectEquipmentVisualLayers(layerType, stack, assetIdOverride)) {
			ClientLayerSnapshot layer = new ClientLayerSnapshot(
					modelClassName,
					visualLayer.texture(),
					visualLayer.tintRgb(),
					false
			).withRenderScale(renderScale);
			if (visualLayer.dynamicImage() != null) {
				layer = layer.withDynamicImage(visualLayer.dynamicImageKey(), visualLayer.dynamicImage());
			}
			layers.add(layer);
		}
	}

	private static List<EquipmentVisualLayer> collectEquipmentVisualLayers(String layerType, ItemStack stack, Identifier assetIdOverride) {
		Identifier assetId = assetIdOverride != null ? assetIdOverride : equipmentAssetId(stack);
		if (assetId == null) {
			return List.of();
		}
		JsonObject equipment = ASSETS.loadJsonAsset("assets/" + assetId.getNamespace() + "/equipment/" + assetId.getPath() + ".json");
		if (equipment == null || !equipment.has("layers") || !equipment.get("layers").isJsonObject()) {
			return List.of();
		}
		JsonObject layerMap = equipment.getAsJsonObject("layers");
		if (!layerMap.has(layerType) || !layerMap.get(layerType).isJsonArray()) {
			return List.of();
		}

		List<EquipmentVisualLayer> layers = new ArrayList<>();
		for (JsonElement element : layerMap.getAsJsonArray(layerType)) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject layerJson = element.getAsJsonObject();
			if (!layerJson.has("texture")) {
				continue;
			}
			Identifier textureId = Identifier.tryParse(layerJson.get("texture").getAsString());
			if (textureId == null) {
				continue;
			}
			int tintRgb = 0xFFFFFF;
			if (layerJson.has("dyeable") && layerJson.get("dyeable").isJsonObject()) {
				JsonObject dyeable = layerJson.getAsJsonObject("dyeable");
				int fallback = dyeable.has("color_when_undyed") ? dyeable.get("color_when_undyed").getAsInt() : 0xFFFFFF;
				tintRgb = DyedItemColor.getOrDefault(stack, fallback) & 0xFFFFFF;
			}
			layers.add(new EquipmentVisualLayer(
					Identifier.fromNamespaceAndPath(textureId.getNamespace(), "entity/equipment/" + layerType + "/" + textureId.getPath()),
					null,
					null,
					tintRgb
			));
		}
		return layers;
	}

	private static List<EquipmentVisualLayer> collectHumanoidArmorVisualLayers(ItemStack stack, EquipmentSlot slot) {
		if (!shouldRenderHumanoidArmor(stack, slot)) {
			return List.of();
		}
		String layerType = humanoidArmorLayerType(slot);
		List<EquipmentVisualLayer> layers = new ArrayList<>(collectEquipmentVisualLayers(layerType, stack, null));
		EquipmentVisualLayer trimLayer = collectHumanoidTrimVisualLayer(stack, layerType);
		if (trimLayer != null) {
			layers.add(trimLayer);
		}
		return layers;
	}

	private static boolean shouldRenderHumanoidArmor(ItemStack stack, EquipmentSlot slot) {
		if (stack == null || stack.isEmpty() || slot == null) {
			return false;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) {
			return false;
		}
		return hasEquipmentLayer(humanoidArmorLayerType(slot), stack, null);
	}

	private static boolean hasEquipmentLayer(String layerType, ItemStack stack, Identifier assetIdOverride) {
		Identifier assetId = assetIdOverride != null ? assetIdOverride : equipmentAssetId(stack);
		if (assetId == null) {
			return false;
		}
		JsonObject equipment = ASSETS.loadJsonAsset("assets/" + assetId.getNamespace() + "/equipment/" + assetId.getPath() + ".json");
		if (equipment == null || !equipment.has("layers") || !equipment.get("layers").isJsonObject()) {
			return false;
		}
		JsonObject layerMap = equipment.getAsJsonObject("layers");
		return layerMap.has(layerType) && layerMap.get(layerType).isJsonArray() && !layerMap.getAsJsonArray(layerType).isEmpty();
	}

	private static String humanoidArmorLayerType(EquipmentSlot slot) {
		return slot == EquipmentSlot.LEGS ? "humanoid_leggings" : "humanoid";
	}

	private static EquipmentVisualLayer collectHumanoidTrimVisualLayer(ItemStack stack, String layerType) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		ArmorTrim trim = stack.get(DataComponents.TRIM);
		ResourceKey<EquipmentAsset> assetKey = equipmentAssetKey(stack);
		if (trim == null || assetKey == null) {
			return null;
		}

		Identifier trimPatternTexture = armorTrimPatternTexture(trim, layerType);
		Identifier paletteTexture = armorTrimPaletteTexture(trim, assetKey);
		if (trimPatternTexture == null || paletteTexture == null) {
			return null;
		}
		String cacheKey = "armor_trim:" + trimPatternTexture + "|" + paletteTexture;
		BufferedImage image = ARMOR_TRIM_TEXTURE_CACHE.computeIfAbsent(cacheKey, ignored -> recolorArmorTrimTexture(trimPatternTexture, paletteTexture));
		if (image == null) {
			return null;
		}
		return new EquipmentVisualLayer(null, cacheKey, image, 0xFFFFFF);
	}

	private static Identifier armorTrimPatternTexture(ArmorTrim trim, String layerType) {
		if (trim == null || trim.pattern() == null || trim.pattern().value() == null) {
			return null;
		}
		Identifier assetId = trim.pattern().value().assetId();
		if (assetId == null) {
			return null;
		}
		return Identifier.fromNamespaceAndPath(assetId.getNamespace(), "trims/entity/" + layerType + "/" + assetId.getPath());
	}

	private static Identifier armorTrimPaletteTexture(ArmorTrim trim, ResourceKey<EquipmentAsset> assetKey) {
		if (trim == null || trim.material() == null || trim.material().value() == null || assetKey == null) {
			return null;
		}
		Identifier materialId = trim.material()
				.unwrapKey()
				.map(ResourceKey::identifier)
				.orElse(Identifier.fromNamespaceAndPath("minecraft", "quartz"));
		String suffix = trim.material().value().assets().assetId(assetKey).suffix();
		return Identifier.fromNamespaceAndPath(materialId.getNamespace(), "trims/color_palettes/" + suffix);
	}

	private static BufferedImage recolorArmorTrimTexture(Identifier trimTextureId, Identifier paletteTextureId) {
		BufferedImage trimTexture = ASSETS.loadTexture(trimTextureId);
		BufferedImage sourcePalette = ASSETS.loadTexture(ARMOR_TRIM_PALETTE_TEXTURE);
		BufferedImage targetPalette = ASSETS.loadTexture(paletteTextureId);
		if (trimTexture == null || sourcePalette == null || targetPalette == null) {
			return null;
		}

		int[] sourceColors = paletteColors(sourcePalette);
		int[] targetColors = paletteColors(targetPalette);
		if (sourceColors.length == 0 || targetColors.length == 0) {
			return null;
		}

		BufferedImage recolored = new BufferedImage(trimTexture.getWidth(), trimTexture.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < trimTexture.getHeight(); y++) {
			for (int x = 0; x < trimTexture.getWidth(); x++) {
				int argb = trimTexture.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				int rgb = argb & 0xFFFFFF;
				int index = paletteIndex(sourceColors, rgb);
				int targetRgb = targetColors[Mth.clamp(index, 0, targetColors.length - 1)] & 0xFFFFFF;
				recolored.setRGB(x, y, (alpha << 24) | targetRgb);
			}
		}
		return recolored;
	}

	private static int[] paletteColors(BufferedImage palette) {
		if (palette == null) {
			return new int[0];
		}
		int count = Math.max(1, palette.getWidth() * palette.getHeight());
		int[] colors = new int[count];
		int index = 0;
		for (int y = 0; y < palette.getHeight(); y++) {
			for (int x = 0; x < palette.getWidth(); x++) {
				colors[index++] = palette.getRGB(x, y);
			}
		}
		return colors;
	}

	private static int paletteIndex(int[] sourceColors, int rgb) {
		int bestIndex = 0;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < sourceColors.length; i++) {
			int candidate = sourceColors[i] & 0xFFFFFF;
			if (candidate == rgb) {
				return i;
			}
			int dr = ((candidate >> 16) & 0xFF) - ((rgb >> 16) & 0xFF);
			int dg = ((candidate >> 8) & 0xFF) - ((rgb >> 8) & 0xFF);
			int db = (candidate & 0xFF) - (rgb & 0xFF);
			int distance = dr * dr + dg * dg + db * db;
			if (distance < bestDistance) {
				bestDistance = distance;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	private record ItemVisualBounds(
			float minY,
			float maxY
	) {
		static final ItemVisualBounds EMPTY = new ItemVisualBounds(0.0F, 0.0F);
	}

	private static Identifier equipmentAssetId(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return null;
		}
		return equippable.assetId().map(ResourceKey::identifier).orElse(null);
	}

	private static ResourceKey<EquipmentAsset> equipmentAssetKey(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		if (equippable == null) {
			return null;
		}
		return equippable.assetId().orElse(null);
	}

	private static Identifier ironGolemCrackinessTexture(net.minecraft.world.entity.Crackiness.Level crackiness) {
		return switch (crackiness) {
			case LOW -> minecraftTexture("entity/iron_golem/iron_golem_crackiness_low");
			case MEDIUM -> minecraftTexture("entity/iron_golem/iron_golem_crackiness_medium");
			case HIGH -> minecraftTexture("entity/iron_golem/iron_golem_crackiness_high");
			default -> null;
		};
	}

	private static String boatWoodType(String typePath) {
		if (typePath == null || typePath.isBlank()) {
			return null;
		}
		if (typePath.endsWith("_chest_boat")) {
			return typePath.substring(0, typePath.length() - "_chest_boat".length());
		}
		if (typePath.endsWith("_boat")) {
			return typePath.substring(0, typePath.length() - "_boat".length());
		}
		if (typePath.endsWith("_chest_raft")) {
			return typePath.substring(0, typePath.length() - "_chest_raft".length());
		}
		if (typePath.endsWith("_raft")) {
			return typePath.substring(0, typePath.length() - "_raft".length());
		}
		return null;
	}

	private static Direction sleepingDirection(LivingEntity livingEntity) {
		return livingEntity.isSleeping() ? livingEntity.getBedOrientation() : null;
	}

	private static PlayerSkinSnapshot capturePlayerSkin(Player player) {
		Property property = PlayerUtils.getPlayerSkin(player.getGameProfile());
		if (property == null && player instanceof ServerPlayer serverPlayer) {
			try {
				SkinStorage skinStorage = SkinRestorer.getSkinStorage();
				if (skinStorage != null) {
					SkinValue skinValue = skinStorage.getSkin(serverPlayer.getUUID());
					if (skinValue != null) {
						property = skinValue.value();
					}
				}
			} catch (Exception ignored) {
			}
		}

		if (property == null) {
			return null;
		}
		Property resolvedProperty = property;
		return PLAYER_SKIN_SNAPSHOT_CACHE.computeIfAbsent(resolvedProperty.value(), ignored -> {
			var skinData = PlayerUtils.getSkinUrl(resolvedProperty);
			String url = skinData == null ? null : skinData.left();
			boolean slim = skinData != null
					&& skinData.right() != null
					&& "slim".equalsIgnoreCase(skinData.right().toString());
			Identifier fallback = slim ? PLAYER_SLIM_FALLBACK : PLAYER_WIDE_FALLBACK;
			return new PlayerSkinSnapshot(resolvedProperty.value(), url, fallback, slim);
		});
	}

	static void renderEntities(
			List<EntitySnapshot> entities,
			BlueMapCameraRenderer.WorldSnapshot snapshot,
			ArrayTileModel model,
			MaterialResolver materialResolver
	) {
		RenderContext context = new RenderContext(snapshot, model, materialResolver);
		for (EntitySnapshot entitySnapshot : entities) {
			try {
				renderEntitySnapshot(context, entitySnapshot);
			} catch (Throwable throwable) {
				Lg2.LOGGER.debug("Failed to render camera snapshot {}", entitySnapshot.getClass().getSimpleName(), throwable);
			}
		}
	}

	private static void renderEntitySnapshot(RenderContext context, EntitySnapshot entitySnapshot) {
		if (entitySnapshot instanceof CompositeSnapshot compositeSnapshot) {
			renderEntitySnapshot(context, compositeSnapshot.primary());
			for (EntitySnapshot attachment : compositeSnapshot.attachments()) {
				renderEntitySnapshot(context, attachment);
			}
			return;
		}
		if (entitySnapshot instanceof HumanoidSnapshot humanoidSnapshot) {
			renderHumanoid(context, humanoidSnapshot);
		} else if (entitySnapshot instanceof ClientModelSnapshot clientModelSnapshot) {
			VanillaClientModels.render(context, clientModelSnapshot);
		} else if (entitySnapshot instanceof QuadrupedSnapshot quadrupedSnapshot) {
			renderQuadruped(context, quadrupedSnapshot);
		} else if (entitySnapshot instanceof ChickenSnapshot chickenSnapshot) {
			renderChicken(context, chickenSnapshot);
		} else if (entitySnapshot instanceof CreeperSnapshot creeperSnapshot) {
			renderCreeper(context, creeperSnapshot);
		} else if (entitySnapshot instanceof SpiderSnapshot spiderSnapshot) {
			renderSpider(context, spiderSnapshot);
		} else if (entitySnapshot instanceof ArmorStandSnapshot armorStandSnapshot) {
			renderArmorStand(context, armorStandSnapshot);
		} else if (entitySnapshot instanceof ItemSnapshot itemSnapshot) {
			renderItem(context, itemSnapshot);
		} else if (entitySnapshot instanceof FixedItemSnapshot fixedItemSnapshot) {
			renderFixedItem(context, fixedItemSnapshot);
		} else if (entitySnapshot instanceof DisplayItemSnapshot displayItemSnapshot) {
			renderDisplayItem(context, displayItemSnapshot);
		} else if (entitySnapshot instanceof ExperienceOrbSnapshot experienceOrbSnapshot) {
			renderExperienceOrb(context, experienceOrbSnapshot);
		} else if (entitySnapshot instanceof FishingHookSnapshot fishingHookSnapshot) {
			renderFishingHook(context, fishingHookSnapshot);
		} else if (entitySnapshot instanceof ItemFrameSnapshot itemFrameSnapshot) {
			renderItemFrame(context, itemFrameSnapshot);
		} else if (entitySnapshot instanceof ImagePlaneSnapshot imagePlaneSnapshot) {
			renderImagePlane(context, imagePlaneSnapshot);
		} else if (entitySnapshot instanceof LineSnapshot lineSnapshot) {
			renderLine(context, lineSnapshot);
		}
	}

	static BlueMapCameraRenderer.TextureMaterial loadTextureMaterial(Identifier textureId) {
		if (textureId == null) {
			return BlueMapCameraRenderer.TextureMaterial.missing();
		}
		return STATIC_TEXTURE_CACHE.computeIfAbsent(textureId.toString(), ignored -> {
			BufferedImage image = ASSETS.loadTexture(textureId);
			return image == null ? BlueMapCameraRenderer.TextureMaterial.missing() : BlueMapCameraRenderer.TextureMaterial.fromImage(image);
		});
	}

	static BlueMapCameraRenderer.TextureMaterial loadPlayerSkinMaterial(PlayerSkinSnapshot skinSnapshot) {
		if (skinSnapshot == null) {
			return loadTextureMaterial(PLAYER_WIDE_FALLBACK);
		}
		return PLAYER_SKIN_CACHE.computeIfAbsent(skinSnapshot.cacheKey(), ignored -> {
			BufferedImage image = loadSkinImage(skinSnapshot);
			if (image == null) {
				return loadTextureMaterial(skinSnapshot.fallbackTexture());
			}
			return BlueMapCameraRenderer.TextureMaterial.fromImage(image);
		});
	}

	private static BufferedImage loadSkinImage(PlayerSkinSnapshot skinSnapshot) {
		if (skinSnapshot.url() == null || skinSnapshot.url().isBlank()) {
			return null;
		}
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(skinSnapshot.url()).openConnection();
			connection.setConnectTimeout(1500);
			connection.setReadTimeout(1500);
			connection.setUseCaches(true);
			try (InputStream inputStream = connection.getInputStream()) {
				BufferedImage image = ImageIO.read(inputStream);
				if (image == null) {
					return null;
				}
				return normalizeSkinImage(toArgb(image));
			}
		} catch (IOException ignored) {
			return null;
		}
	}

	private static void renderHumanoid(RenderContext context, HumanoidSnapshot snapshot) {
		int baseMaterial = snapshot.playerSkin() != null
				? context.materialResolver().materialForPlayerSkin(snapshot.playerSkin())
				: context.materialResolver().materialForTexture(snapshot.texture());
		int texWidth = snapshot.kind().textureWidth;
		int texHeight = snapshot.kind().textureHeight;
		float rootScale = snapshot.kind().scale * (snapshot.baby() ? 0.5F : 1.0F);
		Matrix4f root = humanoidRoot(snapshot.position(), snapshot.bodyYaw(), snapshot.pose(), snapshot.sleepingDirection(), rootScale);

		float walkPhase = snapshot.walkPos() * 0.6662F;
		float walkAmount = Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F);
		float headYaw = radians(wrapDegrees(snapshot.headYaw() - snapshot.bodyYaw()));
		float headPitch = radians(snapshot.pitch());
		float rightArmPitch = Mth.cos(walkPhase + (float) Math.PI) * 1.4F * walkAmount;
		float leftArmPitch = Mth.cos(walkPhase) * 1.4F * walkAmount;
		float rightLegPitch = Mth.cos(walkPhase) * 1.4F * walkAmount;
		float leftLegPitch = Mth.cos(walkPhase + (float) Math.PI) * 1.4F * walkAmount;
		float rightArmYaw = 0.0F;
		float leftArmYaw = 0.0F;
		float rightArmRoll = 0.0F;
		float leftArmRoll = 0.0F;
		float rightLegYaw = 0.0F;
		float leftLegYaw = 0.0F;
		float rightLegRoll = 0.0F;
		float leftLegRoll = 0.0F;
		if (snapshot.kind() == HumanoidKind.ZOMBIE) {
			float[] zombieArmPose = zombieArmPose(snapshot);
			rightArmPitch = zombieArmPose[0];
			leftArmPitch = zombieArmPose[1];
			rightArmYaw = zombieArmPose[2];
			leftArmYaw = zombieArmPose[3];
			rightArmRoll = zombieArmPose[4];
			leftArmRoll = zombieArmPose[5];
		} else if (snapshot.aggressive()) {
			rightArmPitch = -1.2F;
			leftArmPitch = -1.2F;
		}
		if (snapshot.usingItem()) {
			if (snapshot.mainArm() == HumanoidArm.RIGHT) {
				rightArmPitch = -1.4F;
			} else {
				leftArmPitch = -1.4F;
			}
		}
		if (snapshot.passenger()) {
			rightArmPitch -= (float) Math.PI / 5.0F;
			leftArmPitch -= (float) Math.PI / 5.0F;
			rightLegPitch = -1.4137167F;
			leftLegPitch = -1.4137167F;
			rightLegYaw = (float) Math.PI / 10.0F;
			leftLegYaw = -((float) Math.PI / 10.0F);
			rightLegRoll = 0.07853982F;
			leftLegRoll = -0.07853982F;
		}

		renderHumanBase(context, snapshot, root, baseMaterial, texWidth, texHeight, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);

		if (snapshot.kind().outerLayers) {
			renderPlayerOuterLayers(context, snapshot, root, baseMaterial, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
		}

		if (snapshot.kind().villager) {
			renderVillagerFeatures(context, snapshot, root, baseMaterial, headYaw, headPitch, rightLegPitch, leftLegPitch);
		}

		for (Identifier overlayTexture : snapshot.overlayTextures()) {
			if (overlayTexture == null) {
				continue;
			}
			int overlayMaterial = context.materialResolver().materialForTexture(overlayTexture);
			if (snapshot.kind().villager) {
				renderVillagerFeatures(context, snapshot, root, overlayMaterial, headYaw, headPitch, rightLegPitch, leftLegPitch);
			} else if (snapshot.kind() == HumanoidKind.ENDERMAN && overlayTexture.equals(ENDERMAN_EYES_TEXTURE)) {
				renderEndermanEyes(context, snapshot, root, overlayMaterial, headYaw, headPitch);
			} else {
				renderHumanBase(context, snapshot, root, overlayMaterial, snapshot.kind().textureWidth, snapshot.kind().textureHeight, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
			}
		}

		boolean renderedExactArmor = renderExactHumanoidArmor(
				context,
				snapshot,
				headYaw,
				headPitch,
				rightArmPitch,
				leftArmPitch,
				rightArmYaw,
				leftArmYaw,
				rightArmRoll,
				leftArmRoll,
				rightLegPitch,
				leftLegPitch,
				rightLegYaw,
				leftLegYaw,
				rightLegRoll,
				leftLegRoll
		);
		if (!renderedExactArmor) {
			renderHumanoidArmor(
					context,
					snapshot,
					root,
					headYaw,
					headPitch,
					rightArmPitch,
					leftArmPitch,
					rightArmYaw,
					leftArmYaw,
					rightArmRoll,
					leftArmRoll,
					rightLegPitch,
					leftLegPitch,
					rightLegYaw,
					leftLegYaw,
					rightLegRoll,
					leftLegRoll
			);
		}

		renderHumanoidHeadEquipment(context, snapshot, root, headYaw, headPitch);
		renderHumanoidHeldItems(context, snapshot, root, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll);
	}

	private static boolean renderExactHumanoidArmor(
			RenderContext context,
			HumanoidSnapshot snapshot,
			float headYaw,
			float headPitch,
			float rightArmPitch,
			float leftArmPitch,
			float rightArmYaw,
			float leftArmYaw,
			float rightArmRoll,
			float leftArmRoll,
			float rightLegPitch,
			float leftLegPitch,
			float rightLegYaw,
			float leftLegYaw,
			float rightLegRoll,
			float leftLegRoll
	) {
		if (!VanillaClientModels.isAvailable()) {
			return false;
		}
		ClientModelSnapshot armorSnapshot = captureHumanoidArmorClientModel(snapshot);
		if (armorSnapshot == null || armorSnapshot.layers() == null || armorSnapshot.layers().length == 0) {
			return false;
		}
		VanillaClientModels.renderHumanoidArmor(
				context,
				armorSnapshot,
				headYaw,
				headPitch,
				rightArmPitch,
				leftArmPitch,
				rightArmYaw,
				leftArmYaw,
				rightArmRoll,
				leftArmRoll,
				rightLegPitch,
				leftLegPitch,
				rightLegYaw,
				leftLegYaw,
				rightLegRoll,
				leftLegRoll
		);
		return true;
	}

	private static ClientModelSnapshot captureHumanoidArmorClientModel(HumanoidSnapshot snapshot) {
		if (snapshot == null || snapshot.armor() == null || snapshot.armor().isEmpty() || snapshot.kind().villager) {
			return null;
		}

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		addHumanoidArmorClientLayers(layers, snapshot.armor().head(), EquipmentSlot.HEAD, snapshot.kind() == HumanoidKind.PLAYER_SLIM);
		addHumanoidArmorClientLayers(layers, snapshot.armor().chest(), EquipmentSlot.CHEST, snapshot.kind() == HumanoidKind.PLAYER_SLIM);
		addHumanoidArmorClientLayers(layers, snapshot.armor().legs(), EquipmentSlot.LEGS, snapshot.kind() == HumanoidKind.PLAYER_SLIM);
		addHumanoidArmorClientLayers(layers, snapshot.armor().feet(), EquipmentSlot.FEET, snapshot.kind() == HumanoidKind.PLAYER_SLIM);
		if (layers.isEmpty()) {
			return null;
		}

		return clientModelSnapshot(
				snapshot.position(),
				snapshot.bodyYaw(),
				180.0F,
				snapshot.kind().scale,
				ClientModelTransformKind.LIVING,
				humanoidArmorStateFields(snapshot),
				layers.toArray(ClientLayerSnapshot[]::new)
		);
	}

	private static void addHumanoidArmorClientLayers(List<ClientLayerSnapshot> layers, ItemStack stack, EquipmentSlot slot, boolean slimPlayerModel) {
		for (EquipmentVisualLayer visualLayer : collectHumanoidArmorVisualLayers(stack, slot)) {
			ClientLayerSnapshot layer = new ClientLayerSnapshot(
					humanoidArmorModelClass(),
					visualLayer.texture(),
					visualLayer.tintRgb(),
					false
			)
					.withFactory("createArmorMeshSet:" + slot.name())
					.withCubeDeformation(0.25F)
					.withSecondaryCubeDeformation(0.5F)
					.withModelFlag(slimPlayerModel)
					.withRenderScale(visualLayer.dynamicImage() == null ? 1.0F : 1.001F);
			if (visualLayer.dynamicImage() != null) {
				layer = layer.withDynamicImage(visualLayer.dynamicImageKey(), visualLayer.dynamicImage());
			}
			layers.add(layer);
		}
	}

	private static String humanoidArmorModelClass() {
		return "net.minecraft.client.model.HumanoidModel";
	}

	private static Map<String, Object> humanoidArmorStateFields(HumanoidSnapshot snapshot) {
		Map<String, Object> state = new HashMap<>();
		float walkSpeed = Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F);
		Vec3 eyePosition = snapshot.position().add(0.0D, 1.62D * snapshot.kind().scale * (snapshot.baby() ? 0.5D : 1.0D), 0.0D);
		Vec3 lookDirection = Vec3.directionFromRotation(snapshot.pitch(), snapshot.headYaw());
		ItemStack leftHand = snapshot.leftHandItem() == null || snapshot.leftHandItem().stack() == null ? ItemStack.EMPTY : snapshot.leftHandItem().stack().copy();
		ItemStack rightHand = snapshot.rightHandItem() == null || snapshot.rightHandItem().stack() == null ? ItemStack.EMPTY : snapshot.rightHandItem().stack().copy();
		ArmorEquipmentSnapshot armor = snapshot.armor();
		state.put("bodyRot", snapshot.bodyYaw());
		state.put("yRot", wrapDegrees(snapshot.headYaw() - snapshot.bodyYaw()));
		state.put("xRot", snapshot.pitch());
		state.put("walkAnimationPos", snapshot.walkPos());
		state.put("walkAnimationSpeed", walkSpeed);
		state.put("scale", snapshot.kind().scale);
		state.put("ageScale", snapshot.baby() ? 0.5F : 1.0F);
		state.put("baby", snapshot.baby());
		state.put("isBaby", snapshot.baby());
		state.put("isInWater", snapshot.swimming());
		state.put("isAutoSpinAttack", false);
		state.put("bedOrientation", snapshot.sleepingDirection());
		state.put("pose", snapshot.pose());
		state.put("mainArm", snapshot.mainArm());
		state.put("attackTime", snapshot.attackAnim());
		state.put("swimAmount", snapshot.swimAmount());
		state.put("isCrouching", snapshot.crouching());
		state.put("isFallFlying", snapshot.fallFlying());
		state.put("isVisuallySwimming", snapshot.swimming());
		state.put("isPassenger", snapshot.passenger());
		boolean rightUsingItem = snapshot.rightHandItem() != null && snapshot.rightHandItem().usingItem();
		boolean leftUsingItem = snapshot.leftHandItem() != null && snapshot.leftHandItem().usingItem();
		state.put("isUsingItem", rightUsingItem || leftUsingItem);
		state.put("ticksUsingItem", rightUsingItem ? snapshot.rightHandItem().useTicks() : leftUsingItem ? snapshot.leftHandItem().useTicks() : 0.0F);
		state.put("speedValue", walkSpeed);
		state.put("distanceToCameraSq", 0.0D);
		state.put("eyePosition", eyePosition);
		state.put("lookDirection", lookDirection);
		state.put("lookAtPosition", eyePosition.add(lookDirection));
		state.put("attackTargetPosition", eyePosition.add(lookDirection));
		state.put("attackArm", snapshot.mainArm());
		state.put("useItemHand", rightUsingItem
				? (snapshot.mainArm() == HumanoidArm.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND)
				: leftUsingItem
				? (snapshot.mainArm() == HumanoidArm.LEFT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND)
				: InteractionHand.MAIN_HAND);
		state.put("leftHandItemStack", leftHand);
		state.put("rightHandItemStack", rightHand);
		state.put("headEquipment", armor == null || armor.head() == null ? ItemStack.EMPTY : armor.head().copy());
		state.put("chestEquipment", armor == null || armor.chest() == null ? ItemStack.EMPTY : armor.chest().copy());
		state.put("legsEquipment", armor == null || armor.legs() == null ? ItemStack.EMPTY : armor.legs().copy());
		state.put("feetEquipment", armor == null || armor.feet() == null ? ItemStack.EMPTY : armor.feet().copy());
		state.put("leftArmPose", leftHand.isEmpty() ? "EMPTY" : "ITEM");
		state.put("rightArmPose", rightHand.isEmpty() ? "EMPTY" : "ITEM");
		state.put("beamOffset", Vec3.ZERO);
		state.put("isAggressive", snapshot.aggressive());
		state.put("isConverting", false);
		state.put("showHat", false);
		state.put("showJacket", false);
		state.put("showLeftPants", false);
		state.put("showRightPants", false);
		state.put("showLeftSleeve", false);
		state.put("showRightSleeve", false);
		state.put("showCape", false);
		state.put("isSpectator", false);
		state.put("showExtraEars", false);
		state.put("id", 0);
		return state;
	}

	private static float[] zombieArmPose(HumanoidSnapshot snapshot) {
		float attackTime = Mth.clamp(snapshot.attackAnim(), 0.0F, 1.0F);
		float swing = Mth.sin(attackTime * (float) Math.PI);
		float easedSwing = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
		float basePitch = -((float) Math.PI) / (snapshot.aggressive() ? 1.5F : 2.25F);
		float yawOffset = 0.1F - swing * 0.6F;
		float armPitch = basePitch + swing * 1.2F - easedSwing * 0.4F;
		float animationTime = snapshot.walkPos();
		float idleRoll = Mth.cos(animationTime * 0.09F) * 0.05F + 0.05F;
		float idlePitch = Mth.sin(animationTime * 0.067F) * 0.05F;
		return new float[]{
				armPitch + idlePitch,
				armPitch - idlePitch,
				-yawOffset,
				yawOffset,
				idleRoll,
				-idleRoll
		};
	}

	private static Matrix4f humanoidRoot(Vec3 position, float yaw, Pose pose, Direction sleepingDirection, float scale) {
		Matrix4f root = new Matrix4f().translate((float) position.x, (float) position.y, (float) position.z);
		if (sleepingDirection != null) {
			root.translate(0.0F, 0.2F, 0.0F);
			root.rotateY(radians(-yaw));
			switch (sleepingDirection) {
				case NORTH -> root.rotateZ((float) Math.PI * 0.5F);
				case SOUTH -> root.rotateZ((float) -Math.PI * 0.5F);
				case WEST -> root.rotateX((float) -Math.PI * 0.5F);
				case EAST -> root.rotateX((float) Math.PI * 0.5F);
				default -> {
				}
			}
		} else {
			root.rotateY(radians(-yaw));
			if (pose == Pose.CROUCHING) {
				root.translate(0.0F, -2.0F * PX, 2.0F * PX);
			} else if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING) {
				root.translate(0.0F, 10.0F * PX, 0.0F);
				root.rotateX((float) -Math.PI * 0.5F);
				root.translate(0.0F, -10.0F * PX, 0.0F);
			}
		}
		root.scale(scale);
		return root;
	}

	private static void renderHumanBase(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f root,
			int material,
			int texWidth,
			int texHeight,
			float headYaw,
			float headPitch,
			float rightArmPitch,
			float leftArmPitch,
			float rightArmYaw,
			float leftArmYaw,
			float rightArmRoll,
			float leftArmRoll,
			float rightLegPitch,
			float leftLegPitch,
			float rightLegYaw,
			float leftLegYaw,
			float rightLegRoll,
			float leftLegRoll
	) {
		if (snapshot.kind().villager) {
			renderVillagerBody(context, root, material, texWidth, texHeight, headYaw, headPitch, rightLegPitch, leftLegPitch);
			return;
		}
		if (snapshot.kind() == HumanoidKind.ENDERMAN) {
			renderEndermanBody(context, snapshot, root, material, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
			return;
		}

		float armWidth = snapshot.kind().armWidth;
		float legWidth = snapshot.kind().legWidth;

		Matrix4f head = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
		addHumanoidBox(context, snapshot, head, -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, texWidth, texHeight, material, false, 0.0F);

		addHumanoidBox(context, snapshot, root, -4.0F, 12.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 16, texWidth, texHeight, material, false, 0.0F);

		Matrix4f rightArm = rotateAround(root, -5.0F, 22.0F, 0.0F, rightArmPitch, rightArmYaw, rightArmRoll);
		Matrix4f leftArm = rotateAround(root, 5.0F, 22.0F, 0.0F, leftArmPitch, leftArmYaw, leftArmRoll);
		addHumanoidBox(context, snapshot, rightArm, -8.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 40, 16, texWidth, texHeight, material, false, 0.0F);
		if (snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			addHumanoidBox(context, snapshot, leftArm, 5.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 32, 48, texWidth, texHeight, material, false, 0.0F);
		} else {
			boolean mirroredLeftArm = snapshot.kind() == HumanoidKind.SKELETON
					|| snapshot.kind() == HumanoidKind.ENDERMAN
					|| !hasSeparateLeftArmTexture(snapshot);
			addHumanoidBox(
					context,
					snapshot,
					leftArm,
					4.0F,
					12.0F,
					-2.0F,
					armWidth,
					12.0F,
					4.0F,
					mirroredLeftArm ? 40 : 32,
					mirroredLeftArm ? 16 : 48,
					texWidth,
					texHeight,
					material,
					mirroredLeftArm,
					0.0F
			);
		}

		Matrix4f rightLeg = rotateAround(root, -2.0F, 12.0F, 0.0F, rightLegPitch, rightLegYaw, rightLegRoll);
		Matrix4f leftLeg = rotateAround(root, 2.0F, 12.0F, 0.0F, leftLegPitch, leftLegYaw, leftLegRoll);
		addHumanoidBox(context, snapshot, rightLeg, -4.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, false, 0.0F);
		if (snapshot.kind() == HumanoidKind.PLAYER || snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			addHumanoidBox(context, snapshot, leftLeg, 0.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 16, 48, texWidth, texHeight, material, false, 0.0F);
		} else {
			addHumanoidBox(context, snapshot, leftLeg, 0.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, true, 0.0F);
		}
	}

	private static void renderPlayerOuterLayers(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f root,
			int material,
			float headYaw,
			float headPitch,
			float rightArmPitch,
			float leftArmPitch,
			float rightArmYaw,
			float leftArmYaw,
			float rightArmRoll,
			float leftArmRoll,
			float rightLegPitch,
			float leftLegPitch,
			float rightLegYaw,
			float leftLegYaw,
			float rightLegRoll,
			float leftLegRoll
	) {
		byte bits = snapshot.playerModelBits();
		if (showPlayerPart(bits, PlayerModelPart.HAT)) {
			Matrix4f head = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
			addHumanoidBox(context, snapshot, head, -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 32, 0, 64, 64, material, false, 0.5F);
		}
		if (showPlayerPart(bits, PlayerModelPart.JACKET)) {
			addHumanoidBox(context, snapshot, root, -4.0F, 12.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 32, 64, 64, material, false, 0.25F);
		}

		float armWidth = snapshot.kind() == HumanoidKind.PLAYER_SLIM ? 3.0F : 4.0F;
		Matrix4f rightArm = rotateAround(root, -5.0F, 22.0F, 0.0F, rightArmPitch, rightArmYaw, rightArmRoll);
		Matrix4f leftArm = rotateAround(root, 5.0F, 22.0F, 0.0F, leftArmPitch, leftArmYaw, leftArmRoll);
		if (showPlayerPart(bits, PlayerModelPart.RIGHT_SLEEVE)) {
			addHumanoidBox(context, snapshot, rightArm, -8.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 40, 32, 64, 64, material, false, 0.25F);
		}
		if (showPlayerPart(bits, PlayerModelPart.LEFT_SLEEVE)) {
			addHumanoidBox(context, snapshot, leftArm, snapshot.kind() == HumanoidKind.PLAYER_SLIM ? 5.0F : 4.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 48, 48, 64, 64, material, false, 0.25F);
		}

		Matrix4f rightLeg = rotateAround(root, -2.0F, 12.0F, 0.0F, rightLegPitch, rightLegYaw, rightLegRoll);
		Matrix4f leftLeg = rotateAround(root, 2.0F, 12.0F, 0.0F, leftLegPitch, leftLegYaw, leftLegRoll);
		if (showPlayerPart(bits, PlayerModelPart.RIGHT_PANTS_LEG)) {
			addHumanoidBox(context, snapshot, rightLeg, -4.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 32, 64, 64, material, false, 0.25F);
		}
		if (showPlayerPart(bits, PlayerModelPart.LEFT_PANTS_LEG)) {
			addHumanoidBox(context, snapshot, leftLeg, 0.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 48, 64, 64, material, false, 0.25F);
		}
	}

	private static void renderHumanoidHeldItems(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f root,
			float rightArmPitch,
			float leftArmPitch,
			float rightArmYaw,
			float leftArmYaw,
			float rightArmRoll,
			float leftArmRoll
	) {
		if (snapshot.kind() == HumanoidKind.ENDERMAN) {
			renderEndermanCarriedBlock(context, snapshot, root);
			return;
		}
		renderHumanoidHeldItem(context, snapshot.rightHandItem(), humanoidHandTransform(root, snapshot, HumanoidArm.RIGHT, rightArmPitch, rightArmYaw, rightArmRoll), ItemDisplayTransformContext.THIRD_PERSON_RIGHT_HAND);
		renderHumanoidHeldItem(context, snapshot.leftHandItem(), humanoidHandTransform(root, snapshot, HumanoidArm.LEFT, leftArmPitch, leftArmYaw, leftArmRoll), ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND);
	}

	private static void renderHumanoidHeadEquipment(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f root,
			float headYaw,
			float headPitch
	) {
		if (snapshot == null || snapshot.armor() == null) {
			return;
		}
		ItemStack stack = snapshot.armor().head();
		if (!shouldRenderHeadEquipmentItem(stack)) {
			return;
		}
		Matrix4f headTransform = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
		renderHeadEquipmentItem(context, stack, manualHeadItemRoot(headTransform));
	}

	private static void renderHumanoidArmor(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f root,
			float headYaw,
			float headPitch,
			float rightArmPitch,
			float leftArmPitch,
			float rightArmYaw,
			float leftArmYaw,
			float rightArmRoll,
			float leftArmRoll,
			float rightLegPitch,
			float leftLegPitch,
			float rightLegYaw,
			float leftLegYaw,
			float rightLegRoll,
			float leftLegRoll
	) {
		ArmorEquipmentSnapshot armor = snapshot.armor();
		if (armor == null || armor.isEmpty() || snapshot.kind().villager) {
			return;
		}
		renderHumanoidArmorSlot(context, snapshot, armor.head(), EquipmentSlot.HEAD, root, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
		renderHumanoidArmorSlot(context, snapshot, armor.chest(), EquipmentSlot.CHEST, root, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
		renderHumanoidArmorSlot(context, snapshot, armor.legs(), EquipmentSlot.LEGS, root, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
		renderHumanoidArmorSlot(context, snapshot, armor.feet(), EquipmentSlot.FEET, root, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
	}

	private static void renderHumanoidArmorSlot(
			RenderContext context,
			HumanoidSnapshot snapshot,
			ItemStack stack,
			EquipmentSlot slot,
			Matrix4f root,
			float headYaw,
			float headPitch,
			float rightArmPitch,
			float leftArmPitch,
			float rightArmYaw,
			float leftArmYaw,
			float rightArmRoll,
			float leftArmRoll,
			float rightLegPitch,
			float leftLegPitch,
			float rightLegYaw,
			float leftLegYaw,
			float rightLegRoll,
			float leftLegRoll
	) {
		List<EquipmentVisualLayer> layers = collectHumanoidArmorVisualLayers(stack, slot);
		if (layers.isEmpty()) {
			return;
		}

		float armWidth = snapshot.kind().armWidth;
		float legWidth = snapshot.kind().legWidth;
		boolean slim = snapshot.kind() == HumanoidKind.PLAYER_SLIM;
		float baseInflate = slot == EquipmentSlot.LEGS ? 0.25F : 0.5F;

		Matrix4f head = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
		Matrix4f rightArm = rotateAround(root, -5.0F, 22.0F, 0.0F, rightArmPitch, rightArmYaw, rightArmRoll);
		Matrix4f leftArm = rotateAround(root, 5.0F, 22.0F, 0.0F, leftArmPitch, leftArmYaw, leftArmRoll);
		Matrix4f rightLeg = rotateAround(root, -2.0F, 12.0F, 0.0F, rightLegPitch, rightLegYaw, rightLegRoll);
		Matrix4f leftLeg = rotateAround(root, 2.0F, 12.0F, 0.0F, leftLegPitch, leftLegYaw, leftLegRoll);

		for (int i = 0; i < layers.size(); i++) {
			EquipmentVisualLayer layer = layers.get(i);
			int material = armorMaterial(context, layer);
			if (material < 0) {
				continue;
			}
			float inflate = baseInflate + (i * 0.015F);
			int[] textureSize = armorTextureSize(layer);
			switch (slot) {
				case HEAD -> addBox(context, head, -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, textureSize[0], textureSize[1], material, false, inflate);
				case CHEST -> {
					addBox(context, root, -4.0F, 12.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 16, textureSize[0], textureSize[1], material, false, inflate);
					addBox(context, rightArm, -8.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 40, 16, textureSize[0], textureSize[1], material, false, inflate);
					addBox(context, leftArm, slim ? 5.0F : 4.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 40, 16, textureSize[0], textureSize[1], material, true, inflate);
				}
				case LEGS -> {
					addBox(context, root, -4.0F, 12.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 16, textureSize[0], textureSize[1], material, false, inflate);
					addBox(context, rightLeg, -4.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, textureSize[0], textureSize[1], material, false, inflate);
					addBox(context, leftLeg, 0.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, textureSize[0], textureSize[1], material, true, inflate);
				}
				case FEET -> {
					addBox(context, rightLeg, -4.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, textureSize[0], textureSize[1], material, false, inflate);
					addBox(context, leftLeg, 0.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, textureSize[0], textureSize[1], material, true, inflate);
				}
				default -> {
				}
			}
		}
	}

	private static int armorMaterial(RenderContext context, EquipmentVisualLayer layer) {
		if (layer == null) {
			return -1;
		}
		if (layer.dynamicImage() == null && layer.texture() != null && layer.tintRgb() != 0xFFFFFF) {
			String cacheKey = "equipment_tint:" + layer.texture() + "|" + layer.tintRgb();
			BufferedImage tinted = EQUIPMENT_TINT_TEXTURE_CACHE.computeIfAbsent(cacheKey, ignored -> tintEquipmentTexture(layer.texture(), layer.tintRgb()));
			if (tinted != null) {
				return context.materialResolver().materialForImage(cacheKey, tinted);
			}
		}
		return layer.dynamicImage() != null
				? context.materialResolver().materialForImage(layer.dynamicImageKey(), layer.dynamicImage())
				: context.materialResolver().materialForTexture(layer.texture());
	}

	private static int[] armorTextureSize(EquipmentVisualLayer layer) {
		BufferedImage image = layer == null
				? null
				: layer.dynamicImage() != null ? layer.dynamicImage() : ASSETS.loadTexture(layer.texture());
		if (image == null) {
			return new int[]{64, 32};
		}
		return new int[]{image.getWidth(), image.getHeight()};
	}

	private static BufferedImage tintEquipmentTexture(Identifier textureId, int tintRgb) {
		BufferedImage source = ASSETS.loadTexture(textureId);
		if (source == null) {
			return null;
		}
		float tintRed = ((tintRgb >> 16) & 0xFF) / 255.0F;
		float tintGreen = ((tintRgb >> 8) & 0xFF) / 255.0F;
		float tintBlue = (tintRgb & 0xFF) / 255.0F;
		BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int argb = source.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha == 0) {
					continue;
				}
				int red = Mth.clamp(Math.round(((argb >> 16) & 0xFF) * tintRed), 0, 255);
				int green = Mth.clamp(Math.round(((argb >> 8) & 0xFF) * tintGreen), 0, 255);
				int blue = Mth.clamp(Math.round((argb & 0xFF) * tintBlue), 0, 255);
				tinted.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
			}
		}
		return tinted;
	}

	private static void renderHumanoidHeldItem(RenderContext context, HeldItemSnapshot heldItem, Matrix4f handTransform, ItemDisplayTransformContext transformContext) {
		if (heldItem == null || heldItem.isEmpty() || handTransform == null) {
			return;
		}
		ItemVisual visual = resolveItemVisual(heldItem, transformContext);
		if (visual == null) {
			return;
		}
		Matrix4f itemTransform = manualHeldItemRoot(handTransform, transformContext == ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND);
		renderItemVisual(context, itemTransform, visual, transformContext);
	}

	private static boolean shouldRenderHeadEquipmentItem(ItemStack stack) {
		return stack != null
				&& !stack.isEmpty()
				&& !shouldRenderHumanoidArmor(stack, EquipmentSlot.HEAD);
	}

	private static void renderHeadEquipmentItem(RenderContext context, ItemStack stack, Matrix4f itemRoot) {
		if (!shouldRenderHeadEquipmentItem(stack) || itemRoot == null) {
			return;
		}
		ItemVisual visual = resolveItemVisual(stack, ItemDisplayTransformContext.HEAD);
		if (visual == null) {
			return;
		}
		renderItemVisual(context, itemRoot, visual, ItemDisplayTransformContext.HEAD);
	}

	private static Matrix4f manualHeldItemRoot(Matrix4f handTransform, boolean leftHand) {
		return new Matrix4f(handTransform)
				.rotateX(radians(90.0F))
				.rotateY(radians(180.0F))
				.translate(leftHand ? -1.0F / 16.0F : 1.0F / 16.0F, 0.0F, -0.625F);
	}

	private static Matrix4f manualHeadItemRoot(Matrix4f headTransform) {
		return new Matrix4f(headTransform)
				// Manual humanoid rendering already lives in world-space, so it needs a
				// simple head-center anchor instead of the mirrored client PoseStack basis
				// used by CustomHeadLayer.
				.translate(0.0F, 28.0F * PX, 0.0F)
				.rotateY((float) Math.PI)
				.scale(0.625F, 0.625F, 0.625F);
	}

	private static Matrix4f exactHeadItemRoot(Matrix4f headTransform) {
		return new Matrix4f(headTransform)
				.translate(0.0F, -0.25F, 0.0F)
				.rotateY((float) Math.PI)
				.scale(0.625F, -0.625F, -0.625F);
	}

	private static Matrix4f humanoidHandTransform(
			Matrix4f root,
			HumanoidSnapshot snapshot,
			HumanoidArm arm,
			float armPitch,
			float armYaw,
			float armRoll
	) {
		float pivotX = arm == HumanoidArm.RIGHT ? -5.0F : 5.0F;
		if (snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			pivotX += arm == HumanoidArm.RIGHT ? 0.5F : -0.5F;
		}
		return new Matrix4f(root)
				.translate(pivotX * PX, 22.0F * PX, 0.0F)
				.rotateZ(armRoll)
				.rotateY(armYaw)
				.rotateX(armPitch);
	}

	private static void addHumanoidBox(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float depth,
			int texU,
			int texV,
			int texWidth,
			int texHeight,
			int material,
			boolean mirror,
			float inflate
	) {
		if (snapshot.kind() == HumanoidKind.PLAYER || snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			addPlayerSkinBox(context, transform, x, y, z, width, height, depth, texU, texV, texWidth, texHeight, material, mirror, inflate);
			return;
		}
		addBox(context, transform, x, y, z, width, height, depth, texU, texV, texWidth, texHeight, material, mirror, inflate);
	}

	private static boolean hasSeparateLeftArmTexture(HumanoidSnapshot snapshot) {
		if (snapshot == null || snapshot.texture() == null) {
			return false;
		}
		if (snapshot.kind() == HumanoidKind.PLAYER || snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			return true;
		}
		if (snapshot.kind() != HumanoidKind.ZOMBIE) {
			return false;
		}
		return SEPARATE_LEFT_ARM_TEXTURE_CACHE.computeIfAbsent(
				snapshot.texture().toString(),
				ignored -> textureRegionHasVisiblePixels(snapshot.texture(), 32, 48, 16, 16)
		);
	}

	private static boolean textureRegionHasVisiblePixels(Identifier texture, int x, int y, int width, int height) {
		BufferedImage image = ASSETS.loadTexture(texture);
		if (image == null) {
			return true;
		}
		int startX = Math.max(0, x);
		int startY = Math.max(0, y);
		int endX = Math.min(image.getWidth(), x + width);
		int endY = Math.min(image.getHeight(), y + height);
		for (int sampleY = startY; sampleY < endY; sampleY++) {
			for (int sampleX = startX; sampleX < endX; sampleX++) {
				if (((image.getRGB(sampleX, sampleY) >>> 24) & 0xFF) > 8) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean showPlayerPart(byte bits, PlayerModelPart modelPart) {
		return (bits & modelPart.getMask()) != 0;
	}

	private static void renderVillagerBody(
			RenderContext context,
			Matrix4f root,
			int material,
			int texWidth,
			int texHeight,
			float headYaw,
			float headPitch,
			float rightLegPitch,
			float leftLegPitch
	) {
		Matrix4f head = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
		addBox(context, head, -4.0F, 24.0F, -4.0F, 8.0F, 10.0F, 8.0F, 0, 0, texWidth, texHeight, material, false, 0.0F);
		addBox(context, head, -1.0F, 20.0F, -6.0F, 2.0F, 4.0F, 2.0F, 24, 0, texWidth, texHeight, material, false, 0.0F);

		addBox(context, root, -4.0F, 12.0F, -3.0F, 8.0F, 12.0F, 6.0F, 16, 20, texWidth, texHeight, material, false, 0.0F);

		Matrix4f arms = rotateAround(root, 0.0F, 22.0F, 0.0F, -0.75F, 0.0F, 0.0F);
		addBox(context, arms, -8.0F, 14.0F, -2.0F, 8.0F, 4.0F, 4.0F, 44, 22, texWidth, texHeight, material, false, 0.0F);

		Matrix4f rightLeg = rotateAround(root, -2.0F, 12.0F, 0.0F, rightLegPitch, 0.0F, 0.0F);
		Matrix4f leftLeg = rotateAround(root, 2.0F, 12.0F, 0.0F, leftLegPitch, 0.0F, 0.0F);
		addBox(context, rightLeg, -4.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 22, texWidth, texHeight, material, false, 0.0F);
		addBox(context, leftLeg, 0.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 22, texWidth, texHeight, material, true, 0.0F);
	}

	private static void renderVillagerFeatures(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f root,
			int material,
			float headYaw,
			float headPitch,
			float rightLegPitch,
			float leftLegPitch
	) {
		renderVillagerBody(context, root, material, 64, 64, headYaw, headPitch, rightLegPitch, leftLegPitch);
	}

	private static void renderEndermanEyes(RenderContext context, HumanoidSnapshot snapshot, Matrix4f root, int material, float headYaw, float headPitch) {
		Matrix4f head = rotateAround(root, 0.0F, 38.0F, 0.0F, headPitch, headYaw, 0.0F);
		addBox(context, head, -4.0F, 38.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.1F, 15, 15);
	}

	private static void renderEndermanBody(
			RenderContext context,
			HumanoidSnapshot snapshot,
			Matrix4f root,
			int material,
			float headYaw,
			float headPitch,
			float rightArmPitch,
			float leftArmPitch,
			float rightArmYaw,
			float leftArmYaw,
			float rightArmRoll,
			float leftArmRoll,
			float rightLegPitch,
			float leftLegPitch,
			float rightLegYaw,
			float leftLegYaw,
			float rightLegRoll,
			float leftLegRoll
	) {
		Matrix4f head = rotateAround(root, 0.0F, 38.0F, 0.0F, headPitch, headYaw, 0.0F);
		addBox(context, head, -4.0F, 38.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.0F);

		addBox(context, root, -4.0F, 27.0F, -2.0F, 8.0F, 12.0F, 4.0F, 32, 16, 64, 32, material, false, 0.0F);

		Matrix4f rightArm = rotateAround(root, -5.0F, 37.0F, 0.0F, rightArmPitch * 0.5F, rightArmYaw, rightArmRoll);
		Matrix4f leftArm = rotateAround(root, 5.0F, 37.0F, 0.0F, leftArmPitch * 0.5F, leftArmYaw, leftArmRoll);
		addBox(context, rightArm, -6.0F, 9.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, false, 0.0F);
		addBox(context, leftArm, 4.0F, 9.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, true, 0.0F);

		Matrix4f rightLeg = rotateAround(root, -2.0F, 30.0F, 0.0F, rightLegPitch * 0.5F, rightLegYaw, rightLegRoll);
		Matrix4f leftLeg = rotateAround(root, 2.0F, 30.0F, 0.0F, leftLegPitch * 0.5F, leftLegYaw, leftLegRoll);
		addBox(context, rightLeg, -3.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, false, 0.0F);
		addBox(context, leftLeg, 1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, true, 0.0F);
	}

	private static void renderEndermanCarriedBlock(RenderContext context, HumanoidSnapshot snapshot, Matrix4f root) {
		HeldItemSnapshot heldItem = snapshot.rightHandItem();
		if (heldItem == null || heldItem.isEmpty()) {
			return;
		}
		ItemVisual visual = resolveItemVisual(heldItem, ItemDisplayTransformContext.FIXED);
		if (visual == null) {
			return;
		}
		Matrix4f itemRoot = new Matrix4f(root)
				.translate(0.0F, 0.6875F, -0.75F)
				.rotateX(radians(20.0F))
				.rotateY(radians(45.0F))
				.translate(0.25F, 0.1875F, 0.25F)
				.scale(-0.5F, -0.5F, 0.5F)
				.rotateY(radians(90.0F));
		renderItemVisual(context, itemRoot, visual, ItemDisplayTransformContext.FIXED);
	}

	private static void renderQuadruped(RenderContext context, QuadrupedSnapshot snapshot) {
		int material = context.materialResolver().materialForTexture(snapshot.texture());
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.bodyYaw()))
				.scale(snapshot.baby() ? 0.5F : 1.0F);

		float walkPhase = snapshot.walkPos() * 0.6662F;
		float walkAmount = Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F);
		float frontLegPitch = Mth.cos(walkPhase) * 1.2F * walkAmount;
		float backLegPitch = Mth.cos(walkPhase + (float) Math.PI) * 1.2F * walkAmount;
		float headYaw = radians(wrapDegrees(snapshot.headYaw() - snapshot.bodyYaw()));
		float headPitch = radians(snapshot.pitch()) + snapshot.headEatAngleScale();

		Matrix4f head = rotateAround(root, 0.0F, 12.0F, -6.0F, headPitch, headYaw, 0.0F);
		if (snapshot.headEatPositionScale() > 0.0F) {
			head.translate(0.0F, snapshot.headEatPositionScale() * 4.0F * PX, snapshot.headEatPositionScale() * 2.0F * PX);
		}
		addBox(context, head, -4.0F, 8.0F, -14.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.0F);
		if (snapshot.kind() == QuadrupedKind.PIG) {
			addBox(context, head, -2.0F, 10.0F, -15.0F, 4.0F, 3.0F, 1.0F, 16, 16, 64, 32, material, false, 0.0F);
		}

		addBox(context, root, -5.0F, 6.0F, -8.0F, 10.0F, 8.0F, 16.0F, 28, 8, 64, 32, material, false, 0.0F);

		Matrix4f rightFront = rotateAround(root, -3.0F, 12.0F, -5.0F, frontLegPitch, 0.0F, 0.0F);
		Matrix4f leftFront = rotateAround(root, 3.0F, 12.0F, -5.0F, backLegPitch, 0.0F, 0.0F);
		Matrix4f rightBack = rotateAround(root, -3.0F, 12.0F, 7.0F, backLegPitch, 0.0F, 0.0F);
		Matrix4f leftBack = rotateAround(root, 3.0F, 12.0F, 7.0F, frontLegPitch, 0.0F, 0.0F);
		addBox(context, rightFront, -5.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, material, false, 0.0F);
		addBox(context, leftFront, 1.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, material, true, 0.0F);
		addBox(context, rightBack, -5.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, material, false, 0.0F);
		addBox(context, leftBack, 1.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, material, true, 0.0F);

		if (snapshot.kind() == QuadrupedKind.SHEEP && !snapshot.sheared() && snapshot.overlayTexture() != null) {
			int overlayMaterial = context.materialResolver().materialForTexture(snapshot.overlayTexture());
			addBox(context, head, -4.0F, 8.0F, -14.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, overlayMaterial, false, 0.8F);
			addBox(context, root, -5.0F, 6.0F, -8.0F, 10.0F, 8.0F, 16.0F, 28, 8, 64, 32, overlayMaterial, false, 0.8F);
			addBox(context, rightFront, -5.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, false, 0.6F);
			addBox(context, leftFront, 1.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, true, 0.6F);
			addBox(context, rightBack, -5.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, false, 0.6F);
			addBox(context, leftBack, 1.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, true, 0.6F);
		}
	}

	private static void renderChicken(RenderContext context, ChickenSnapshot snapshot) {
		int material = context.materialResolver().materialForTexture(snapshot.texture());
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.bodyYaw()))
				.scale(snapshot.baby() ? 0.5F : 1.0F);
		float headYaw = radians(wrapDegrees(snapshot.headYaw() - snapshot.bodyYaw()));
		float headPitch = radians(snapshot.pitch());
		float walkPhase = snapshot.walkPos() * 0.6662F;
		float walkAmount = Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F);
		float rightLegPitch = Mth.cos(walkPhase) * 1.4F * walkAmount;
		float leftLegPitch = Mth.cos(walkPhase + (float) Math.PI) * 1.4F * walkAmount;
		float wingRoll = Mth.sin(snapshot.flap()) * 0.6F;

		Matrix4f head = rotateAround(root, 0.0F, 15.0F, -4.0F, headPitch, headYaw, 0.0F);
		addBox(context, head, -2.0F, 13.0F, -6.0F, 4.0F, 6.0F, 3.0F, 0, 0, 32, 32, material, false, 0.0F);
		addBox(context, head, -1.0F, 14.0F, -7.0F, 2.0F, 2.0F, 1.0F, 14, 0, 32, 32, material, false, 0.0F);
		addBox(context, head, -1.0F, 12.0F, -7.0F, 2.0F, 2.0F, 1.0F, 14, 4, 32, 32, material, false, 0.0F);

		Matrix4f body = rotateAround(root, 0.0F, 10.0F, 0.0F, (float) Math.PI * 0.5F, 0.0F, 0.0F);
		addBox(context, body, -3.0F, 7.0F, -3.0F, 6.0F, 8.0F, 6.0F, 0, 9, 32, 32, material, false, 0.0F);

		Matrix4f rightWing = rotateAround(root, -3.0F, 13.0F, 0.0F, 0.0F, 0.0F, wingRoll);
		Matrix4f leftWing = rotateAround(root, 3.0F, 13.0F, 0.0F, 0.0F, 0.0F, -wingRoll);
		addBox(context, rightWing, -4.0F, 9.0F, -3.0F, 1.0F, 4.0F, 6.0F, 24, 13, 32, 32, material, false, 0.0F);
		addBox(context, leftWing, 3.0F, 9.0F, -3.0F, 1.0F, 4.0F, 6.0F, 24, 13, 32, 32, material, true, 0.0F);

		Matrix4f rightLeg = rotateAround(root, -1.0F, 5.0F, 1.0F, rightLegPitch, 0.0F, 0.0F);
		Matrix4f leftLeg = rotateAround(root, 1.0F, 5.0F, 1.0F, leftLegPitch, 0.0F, 0.0F);
		addBox(context, rightLeg, -1.5F, 0.0F, 0.0F, 3.0F, 5.0F, 3.0F, 26, 0, 32, 32, material, false, 0.0F);
		addBox(context, leftLeg, -1.5F, 0.0F, 0.0F, 3.0F, 5.0F, 3.0F, 26, 0, 32, 32, material, false, 0.0F);
	}

	private static void renderCreeper(RenderContext context, CreeperSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.bodyYaw()))
				.scale(snapshot.baby() ? 0.5F : 1.0F);
		float swellScale = 1.0F + snapshot.swelling() * 0.15F;
		root.scale(swellScale);
		int material = context.materialResolver().materialForTexture(CREEPER_TEXTURE);
		float walkPhase = snapshot.walkPos() * 0.6662F;
		float walkAmount = Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F);
		float frontLegPitch = Mth.cos(walkPhase) * 1.4F * walkAmount;
		float backLegPitch = Mth.cos(walkPhase + (float) Math.PI) * 1.4F * walkAmount;
		float headYaw = radians(wrapDegrees(snapshot.headYaw() - snapshot.bodyYaw()));
		float headPitch = radians(snapshot.pitch());

		Matrix4f head = rotateAround(root, 0.0F, 18.0F, 0.0F, headPitch, headYaw, 0.0F);
		addBox(context, head, -4.0F, 18.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.0F);
		addBox(context, root, -4.0F, 6.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 16, 64, 32, material, false, 0.0F);

		Matrix4f rightFront = rotateAround(root, -2.0F, 6.0F, -2.0F, frontLegPitch, 0.0F, 0.0F);
		Matrix4f leftFront = rotateAround(root, 2.0F, 6.0F, -2.0F, backLegPitch, 0.0F, 0.0F);
		Matrix4f rightBack = rotateAround(root, -2.0F, 6.0F, 2.0F, backLegPitch, 0.0F, 0.0F);
		Matrix4f leftBack = rotateAround(root, 2.0F, 6.0F, 2.0F, frontLegPitch, 0.0F, 0.0F);
		addBox(context, rightFront, -4.0F, 0.0F, -4.0F, 4.0F, 6.0F, 4.0F, 0, 16, 64, 32, material, false, 0.0F);
		addBox(context, leftFront, 0.0F, 0.0F, -4.0F, 4.0F, 6.0F, 4.0F, 0, 16, 64, 32, material, false, 0.0F);
		addBox(context, rightBack, -4.0F, 0.0F, 0.0F, 4.0F, 6.0F, 4.0F, 0, 16, 64, 32, material, false, 0.0F);
		addBox(context, leftBack, 0.0F, 0.0F, 0.0F, 4.0F, 6.0F, 4.0F, 0, 16, 64, 32, material, false, 0.0F);
	}

	private static void renderSpider(RenderContext context, SpiderSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.yaw()));
		int material = context.materialResolver().materialForTexture(SPIDER_TEXTURE);
		float walkPhase = snapshot.walkPos() * 0.9F;
		float walkAmount = Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F);
		addBox(context, root, -4.0F, 8.0F, -10.0F, 8.0F, 8.0F, 8.0F, 32, 4, 64, 32, material, false, 0.0F);
		addBox(context, root, -3.0F, 8.0F, -2.0F, 6.0F, 6.0F, 6.0F, 0, 0, 64, 32, material, false, 0.0F);
		addBox(context, root, -5.0F, 6.0F, 4.0F, 10.0F, 8.0F, 12.0F, 0, 12, 64, 32, material, false, 0.0F);

		for (int leg = 0; leg < 4; leg++) {
			float z = -6.0F + leg * 4.0F;
			float yawSwing = Mth.cos(walkPhase + leg * 0.5F) * 0.35F * walkAmount;
			float roll = 0.55F + leg * 0.08F;
			Matrix4f rightLeg = rotateAround(root, -3.0F, 10.0F, z, 0.0F, yawSwing, roll);
			Matrix4f leftLeg = rotateAround(root, 3.0F, 10.0F, z, 0.0F, -yawSwing, -roll);
			addBox(context, rightLeg, -15.0F, 9.5F, z - 0.5F, 12.0F, 1.0F, 1.0F, 18, 0, 64, 32, material, false, 0.0F);
			addBox(context, leftLeg, 3.0F, 9.5F, z - 0.5F, 12.0F, 1.0F, 1.0F, 18, 0, 64, 32, material, false, 0.0F);
		}

		int eyeMaterial = context.materialResolver().materialForTexture(SPIDER_EYES_TEXTURE);
		addBox(context, root, -4.0F, 8.0F, -10.0F, 8.0F, 8.0F, 8.0F, 32, 4, 64, 32, eyeMaterial, false, 0.05F, 15, 15);
	}

	private static void renderArmorStand(RenderContext context, ArmorStandSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.yaw()))
				.scale(snapshot.small() ? 0.55F : 1.0F);
		int material = context.materialResolver().materialForTexture(ARMOR_STAND_TEXTURE);

		addBox(context, applyPose(root, snapshot.bodyPose(), 0.0F, 12.0F, 0.0F), -4.0F, 12.0F, -2.0F, 8.0F, 12.0F, 4.0F, 0, 16, 64, 64, material, false, 0.0F);
		addBox(context, applyPose(root, snapshot.headPose(), 0.0F, 24.0F, 0.0F), -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 64, material, false, 0.0F);

		if (snapshot.showArms()) {
			addBox(context, applyPose(root, snapshot.rightArmPose(), -5.0F, 22.0F, 0.0F), -8.0F, 12.0F, -2.0F, 4.0F, 12.0F, 4.0F, 40, 16, 64, 64, material, false, 0.0F);
			addBox(context, applyPose(root, snapshot.leftArmPose(), 5.0F, 22.0F, 0.0F), 4.0F, 12.0F, -2.0F, 4.0F, 12.0F, 4.0F, 40, 16, 64, 64, material, true, 0.0F);
		}

		addBox(context, applyPose(root, snapshot.rightLegPose(), -2.0F, 12.0F, 0.0F), -4.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 64, material, false, 0.0F);
		addBox(context, applyPose(root, snapshot.leftLegPose(), 2.0F, 12.0F, 0.0F), 0.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 64, material, true, 0.0F);

		if (snapshot.showBasePlate()) {
			addBox(context, root, -6.0F, 0.0F, -6.0F, 12.0F, 1.0F, 12.0F, 0, 32, 64, 64, material, false, 0.0F);
		}

		if (snapshot.armor() != null && shouldRenderHeadEquipmentItem(snapshot.armor().head())) {
			renderHeadEquipmentItem(context, snapshot.armor().head(), manualHeadItemRoot(applyPose(root, snapshot.headPose(), 0.0F, 24.0F, 0.0F)));
		}
	}

	private static void renderItem(RenderContext context, ItemSnapshot snapshot) {
		ItemVisualBounds bounds = itemVisualBounds(snapshot.visual(), ItemDisplayTransformContext.GROUND);
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y + (0.0625F - bounds.minY()), (float) snapshot.position().z)
				.rotateY(snapshot.spin());
		renderItemVisual(context, root, snapshot.visual(), ItemDisplayTransformContext.GROUND);
	}

	private static void renderFixedItem(RenderContext context, FixedItemSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(snapshot.yaw()))
				.rotateX(radians(snapshot.pitch()))
				.rotateZ(radians(snapshot.roll()))
				.scale(snapshot.scale());
		renderItemVisual(context, root, snapshot.visual(), ItemDisplayTransformContext.FIXED);
	}

	private static void renderDisplayItem(RenderContext context, DisplayItemSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.yaw()))
				.rotateX(radians(snapshot.pitch()))
				.rotateZ(radians(snapshot.roll()))
				.rotateY((float) Math.PI)
				.scale(snapshot.scale());
		renderItemVisual(context, root, snapshot.visual(), snapshot.transformContext());
	}

	private static void renderExperienceOrb(RenderContext context, ExperienceOrbSnapshot snapshot) {
		int material = context.materialResolver().materialForTexture(EXPERIENCE_ORB_TEXTURE);
		int icon = Math.max(snapshot.icon(), 0);
		float u0 = (icon % 4) * 16.0F / 64.0F;
		float u1 = u0 + 16.0F / 64.0F;
		float v0 = (icon / 4) * 16.0F / 64.0F;
		float v1 = v0 + 16.0F / 64.0F;
		float phase = snapshot.ageInTicks() * 0.5F;
		float red = (Mth.sin(phase) + 1.0F) * 0.5F;
		float blue = (Mth.sin(phase + 4.1887903F) + 1.0F) * 0.1F;
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y + 0.1F, (float) snapshot.position().z)
				.scale(0.3F);
		addTexturedDoubleSidedPlane(context, root, -0.5F, -0.25F, 0.0F, 1.0F, 1.0F, u0, v0, u1, v1, material, red, 1.0F, blue);
		Matrix4f crossed = new Matrix4f(root).rotateY((float) Math.PI * 0.5F);
		addTexturedDoubleSidedPlane(context, crossed, -0.5F, -0.25F, 0.0F, 1.0F, 1.0F, u0, v0, u1, v1, material, red, 1.0F, blue);
	}

	private static void renderFishingHook(RenderContext context, FishingHookSnapshot snapshot) {
		int material = context.materialResolver().materialForTexture(FISHING_HOOK_TEXTURE);
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.scale(0.25F);
		addTexturedDoubleSidedPlane(context, root, -0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 0.0625F, 0.0F, 0.125F, 0.0625F, material, 1.0F, 1.0F, 1.0F);
		Matrix4f crossed = new Matrix4f(root).rotateY((float) Math.PI * 0.5F);
		addTexturedDoubleSidedPlane(context, crossed, -0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 0.0625F, 0.0F, 0.125F, 0.0625F, material, 1.0F, 1.0F, 1.0F);
		renderLine(
				context,
				new LineSnapshot(
						snapshot.position(),
						snapshot.position().add(0.0D, 0.25D, 0.0D),
						snapshot.position().add(snapshot.lineStartOffset()),
						0.55F,
						0.03F,
						FISHING_LINE_TEXTURE
				)
		);
	}

	private static void renderItemFrame(RenderContext context, ItemFrameSnapshot snapshot) {
		Matrix4f root = itemFrameRoot(snapshot);
		if (!snapshot.invisible()) {
			ResolvedItemModel frameModel = resolveItemModel(
					Identifier.fromNamespaceAndPath(
							"minecraft",
							"block/" + (snapshot.glow() ? "glow_item_frame" : "item_frame") + (snapshot.mapFrame() ? "_map" : "")
					),
					new HashSet<>()
			);
			if (frameModel != null && !frameModel.elements().isEmpty()) {
				renderBlockEntityModel(context, root, frameModel);
			}
		}
		if (snapshot.map() != null) {
			renderFramedMap(context, root, snapshot);
			return;
		}
		if (snapshot.item() == null || (snapshot.item().flatTexture() == null && (snapshot.item().model() == null || snapshot.item().model().elements().isEmpty()))) {
			return;
		}
		float zOffset = snapshot.invisible() ? 0.5F : 0.4375F;
		Matrix4f itemRoot = new Matrix4f(root)
				.translate(0.0F, 0.0F, zOffset)
				.rotateZ(radians(snapshot.rotation() * 45.0F))
				.rotateY((float) Math.PI)
				.scale(0.5F);
		renderItemVisual(context, itemRoot, snapshot.item(), ItemDisplayTransformContext.FRAMED);
	}

	private static void renderImagePlane(RenderContext context, ImagePlaneSnapshot snapshot) {
		if (snapshot.image() == null) {
			return;
		}
		int material = context.materialResolver().materialForImage(snapshot.materialKey(), snapshot.image());
		Vector3f p0 = transformPosition(snapshot.transform(), snapshot.x(), snapshot.y(), snapshot.z());
		Vector3f p1 = transformPosition(snapshot.transform(), snapshot.x() + snapshot.width(), snapshot.y(), snapshot.z());
		Vector3f p2 = transformPosition(snapshot.transform(), snapshot.x() + snapshot.width(), snapshot.y() + snapshot.height(), snapshot.z());
		Vector3f p3 = transformPosition(snapshot.transform(), snapshot.x(), snapshot.y() + snapshot.height(), snapshot.z());
		LightSample lightSample = snapshot.emissive()
				? new LightSample(15, 15)
				: context.lightAt((p0.x + p2.x) * 0.5F, (p0.y + p2.y) * 0.5F, (p0.z + p2.z) * 0.5F);
		addQuad(context, p0, p1, p2, p3, 0.0F, 1.0F, 0.0F, 1.0F, material, lightSample.sky(), lightSample.block(), 1.0F, 1.0F, 1.0F);
	}

	private static void renderFramedMap(RenderContext context, Matrix4f root, ItemFrameSnapshot snapshot) {
		FramedMapSnapshot map = snapshot.map();
		if (map == null) {
			return;
		}
		float zOffset = snapshot.invisible() ? 0.5F : 0.4375F;
		float rotation = Math.floorMod(snapshot.rotation(), 4) * 90.0F;
		Matrix4f contentRoot = new Matrix4f(root)
				.translate(0.0F, 0.0F, zOffset)
				.rotateZ(radians(rotation))
				.rotateY((float) Math.PI);
		int mapMaterial = context.materialResolver().materialForImage("framed_map:" + map.mapId(), framedMapImage(map));
		addTexturedPlane(context, contentRoot, -0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, mapMaterial, 1.0F, 1.0F, 1.0F);
	}

	private static BufferedImage framedMapImage(FramedMapSnapshot snapshot) {
		BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		byte[] colors = snapshot.colors();
		int[] pixels = new int[128 * 128];
		for (int i = 0; i < pixels.length; i++) {
			int packedId = i < colors.length ? Byte.toUnsignedInt(colors[i]) : 0;
			pixels[i] = MapColor.getColorFromPackedId(packedId);
		}
		image.setRGB(0, 0, 128, 128, pixels, 0, 128);
		return image;
	}

	private static void renderLine(RenderContext context, LineSnapshot snapshot) {
		int material = context.materialResolver().materialForTexture(snapshot.texture());
		int segments = Math.max(6, Mth.ceil((float) snapshot.start().distanceTo(snapshot.end()) * 24.0F));
		for (int i = 0; i < segments; i++) {
			float t = (i + 0.5F) / segments;
			Vec3 point = linePoint(snapshot.start(), snapshot.end(), t, snapshot.sag());
			Matrix4f cube = new Matrix4f().translate((float) point.x, (float) point.y, (float) point.z);
			float half = snapshot.thickness() * 0.5F;
			addBox(context, cube, -half, -half, -half, snapshot.thickness(), snapshot.thickness(), snapshot.thickness(), 0, 0, 16, 16, material, false, 0.0F);
		}
	}

	private static Vec3 linePoint(Vec3 start, Vec3 end, float t, float sag) {
		Vec3 point = start.lerp(end, t);
		if (sag <= 0.0F) {
			return point;
		}
		double sagOffset = sag * 4.0D * t * (1.0D - t);
		return point.add(0.0D, -sagOffset, 0.0D);
	}

	private static Matrix4f itemFrameRoot(ItemFrameSnapshot snapshot) {
		Direction direction = snapshot.direction();
		float xRotation;
		float yRotation;
		Vec3 center = snapshot.position().add(
				direction.getStepX() * 0.46875D,
				direction.getStepY() * 0.46875D,
				direction.getStepZ() * 0.46875D
		);
		if (direction.getAxis().isHorizontal()) {
			xRotation = 0.0F;
			yRotation = 180.0F - direction.toYRot();
		} else {
			xRotation = -90.0F * direction.getAxisDirection().getStep();
			yRotation = 180.0F;
		}
		return new Matrix4f()
				.translate((float) center.x, (float) center.y, (float) center.z)
				.rotateX(radians(xRotation))
				.rotateY(radians(yRotation));
	}

	private static Vec3 blockEntityPos(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		return Vec3.atLowerCornerOf(blockEntity.getBlockPos());
	}

	private static Map<String, Object> blockEntityRootState(Vec3 position) {
		Map<String, Object> state = new HashMap<>();
		state.put("rootTranslateX", 0.0F);
		state.put("rootTranslateY", 0.0F);
		state.put("rootTranslateZ", 0.0F);
		state.put("rootRotateX", 0.0F);
		state.put("rootRotateY", 0.0F);
		state.put("rootRotateZ", 0.0F);
		state.put("rootMidTranslateX", 0.0F);
		state.put("rootMidTranslateY", 0.0F);
		state.put("rootMidTranslateZ", 0.0F);
		state.put("rootRotate2X", 0.0F);
		state.put("rootRotate2Y", 0.0F);
		state.put("rootRotate2Z", 0.0F);
		state.put("rootPostTranslateX", 0.0F);
		state.put("rootPostTranslateY", 0.0F);
		state.put("rootPostTranslateZ", 0.0F);
		state.put("rootScaleX", 1.0F);
		state.put("rootScaleY", 1.0F);
		state.put("rootScaleZ", 1.0F);
		state.put("renderOffset", position);
		return state;
	}

	private static String hangingSignAttachmentType(net.minecraft.world.level.block.state.BlockState state) {
		if (state.getBlock() instanceof net.minecraft.world.level.block.CeilingHangingSignBlock) {
			return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACHED)
					? "CEILING_MIDDLE"
					: "CEILING";
		}
		return "WALL";
	}

	private static String chestLayerFactory(net.minecraft.world.level.block.state.properties.ChestType chestType) {
		return switch (chestType) {
			case LEFT -> "createDoubleBodyLeftLayer";
			case RIGHT -> "createDoubleBodyRightLayer";
			default -> "createSingleBodyLayer";
		};
	}

	private static Direction chestFacing(net.minecraft.world.level.block.state.BlockState state) {
		if (state.hasProperty(net.minecraft.world.level.block.ChestBlock.FACING)) {
			return state.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
		}
		if (state.hasProperty(net.minecraft.world.level.block.EnderChestBlock.FACING)) {
			return state.getValue(net.minecraft.world.level.block.EnderChestBlock.FACING);
		}
		return Direction.SOUTH;
	}

	private static Identifier chestTexture(net.minecraft.world.level.block.entity.BlockEntity chestBlockEntity, net.minecraft.world.level.block.state.properties.ChestType chestType) {
		net.minecraft.world.level.block.Block block = chestBlockEntity.getBlockState().getBlock();
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
		String path = blockId == null ? "chest" : blockId.getPath();
		String base;
		if (chestBlockEntity instanceof net.minecraft.world.level.block.entity.EnderChestBlockEntity) {
			base = "ender";
		} else if (path.contains("trapped_chest")) {
			base = "trapped";
		} else if (path.contains("copper_chest")) {
			base = chestCopperTexturePrefix(path);
		} else {
			base = "normal";
		}
		String suffix = switch (chestType) {
			case LEFT -> "_left";
			case RIGHT -> "_right";
			default -> "";
		};
		return minecraftTexture("entity/chest/" + base + suffix);
	}

	private static String chestCopperTexturePrefix(String blockPath) {
		String normalized = blockPath.startsWith("waxed_") ? blockPath.substring("waxed_".length()) : blockPath;
		if (normalized.startsWith("exposed_")) {
			return "copper_exposed";
		}
		if (normalized.startsWith("weathered_")) {
			return "copper_weathered";
		}
		if (normalized.startsWith("oxidized_")) {
			return "copper_oxidized";
		}
		return "copper";
	}

	private static ClientLayerSnapshot skullLayer(net.minecraft.world.level.block.SkullBlock.Type skullType, net.minecraft.world.level.block.entity.SkullBlockEntity skullBlockEntity) {
		if (!(skullType instanceof net.minecraft.world.level.block.SkullBlock.Types types)) {
			return null;
		}
		return switch (types) {
			case SKELETON -> new ClientLayerSnapshot(
					"net.minecraft.client.model.object.skull.SkullModel",
					minecraftTexture("entity/skeleton/skeleton"),
					0xFFFFFF,
					false
			).withFactory("createHumanoidHeadLayer");
			case WITHER_SKELETON -> new ClientLayerSnapshot(
					"net.minecraft.client.model.object.skull.SkullModel",
					minecraftTexture("entity/skeleton/wither_skeleton"),
					0xFFFFFF,
					false
			).withFactory("createHumanoidHeadLayer");
			case PLAYER -> {
				PlayerSkinSnapshot playerSkin = captureResolvableProfileSkin(skullBlockEntity.getOwnerProfile());
				ClientLayerSnapshot layer = new ClientLayerSnapshot(
						"net.minecraft.client.model.object.skull.SkullModel",
						playerSkin == null ? PLAYER_WIDE_FALLBACK : playerSkin.fallbackTexture(),
						0xFFFFFF,
						false
				).withFactory("createHumanoidHeadLayer");
				yield playerSkin == null ? layer : layer.withPlayerSkin(playerSkin);
			}
			case ZOMBIE -> new ClientLayerSnapshot(
					"net.minecraft.client.model.object.skull.SkullModel",
					ZOMBIE_TEXTURE,
					0xFFFFFF,
					false
			).withFactory("createHumanoidHeadLayer");
			case CREEPER -> new ClientLayerSnapshot(
					"net.minecraft.client.model.object.skull.SkullModel",
					CREEPER_TEXTURE,
					0xFFFFFF,
					false
			).withFactory("createMobHeadLayer");
			case PIGLIN -> new ClientLayerSnapshot(
					"net.minecraft.client.model.object.skull.PiglinHeadModel",
					minecraftTexture("entity/piglin/piglin"),
					0xFFFFFF,
					false
			).withFactory("createHeadModel");
			case DRAGON -> new ClientLayerSnapshot(
					"net.minecraft.client.model.object.skull.DragonHeadModel",
					minecraftTexture("entity/enderdragon/dragon"),
					0xFFFFFF,
					false
			).withFactory("createHeadLayer");
		};
	}

	private static PlayerSkinSnapshot captureResolvableProfileSkin(net.minecraft.world.item.component.ResolvableProfile profile) {
		if (profile == null || profile.partialProfile() == null) {
			return null;
		}
		Property property = PlayerUtils.getPlayerSkin(profile.partialProfile());
		if (property == null) {
			return null;
		}
		var skinData = PlayerUtils.getSkinUrl(property);
		String url = skinData == null ? null : skinData.left();
		boolean slim = skinData != null
				&& skinData.right() != null
				&& "slim".equalsIgnoreCase(skinData.right().toString());
		return new PlayerSkinSnapshot(property.value(), url, slim ? PLAYER_SLIM_FALLBACK : PLAYER_WIDE_FALLBACK, slim);
	}

	private static void renderItemVisual(RenderContext context, Matrix4f root, ItemVisual visual, ItemDisplayTransformContext transformContext) {
		if (visual == null) {
			return;
		}
		if (visual.specialModel() != null) {
			Matrix4f transformedRoot = switch (transformContext) {
				case FRAMED -> applyFlatFramedItemTransform(root, visual.model());
				case GROUND, FIXED, THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND, GUI, HEAD, ON_SHELF ->
						applyItemDisplayTransform(root, visual.model(), transformContext);
				default -> new Matrix4f(root);
			};
			VanillaClientModels.renderWithRoot(context, visual.specialModel(), transformedRoot);
			return;
		}
		if (visual.model() != null && !visual.model().elements().isEmpty()) {
			renderDisplayedItemModel(context, root, visual, transformContext);
			return;
		}
		if (visual.flatTexture() == null) {
			return;
		}
		if (transformContext == ItemDisplayTransformContext.FRAMED) {
			Matrix4f transformedRoot = applyFlatFramedItemTransform(root, visual.model());
			renderFlatItemLayers(context, transformedRoot, visual, 0.0625F, 0.0625F, 0.5F, 0.875F, 0.875F);
			return;
		}
		if (transformContext == ItemDisplayTransformContext.THIRD_PERSON_RIGHT_HAND
				|| transformContext == ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND
				|| transformContext == ItemDisplayTransformContext.FIRST_PERSON_RIGHT_HAND
				|| transformContext == ItemDisplayTransformContext.FIRST_PERSON_LEFT_HAND) {
			Matrix4f transformedRoot = applyItemDisplayTransform(root, visual.model(), transformContext);
			renderFlatItemLayersSingleSided(context, transformedRoot, visual, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F);
			return;
		}
		if (transformContext == ItemDisplayTransformContext.GROUND
				|| transformContext == ItemDisplayTransformContext.FIXED
				|| transformContext == ItemDisplayTransformContext.GUI
				|| transformContext == ItemDisplayTransformContext.HEAD
				|| transformContext == ItemDisplayTransformContext.ON_SHELF) {
			Matrix4f transformedRoot = applyItemDisplayTransform(root, visual.model(), transformContext);
			renderFlatItemLayers(context, transformedRoot, visual, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F);
			return;
		}
		int material = context.materialResolver().materialForTexture(visual.flatTexture());
		addDoubleSidedPlane(context, root, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
		Matrix4f crossed = new Matrix4f(root).rotateY((float) Math.PI * 0.5F);
		addDoubleSidedPlane(context, crossed, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
	}

	private static void renderItemModel(RenderContext context, Matrix4f root, ItemVisual visual) {
		ResolvedItemModel model = visual.model();
		Matrix4f transform = new Matrix4f(root).translate(-0.25F, 0.0F, -0.25F).scale(0.5F);
		for (ItemModelElement element : model.elements()) {
			renderItemModelElement(context, transform, visual, element);
		}
	}

	private static void renderDisplayedItemModel(RenderContext context, Matrix4f root, ItemVisual visual, ItemDisplayTransformContext transformContext) {
		ResolvedItemModel model = visual.model();
		Matrix4f transform = applyItemDisplayTransform(root, model, transformContext);
		for (ItemModelElement element : model.elements()) {
			renderItemModelElement(context, transform, visual, element);
		}
	}

	private static Matrix4f applyItemDisplayTransform(Matrix4f root, ResolvedItemModel model, ItemDisplayTransformContext transformContext) {
		ItemModelTransform transform = itemModelTransform(model, transformContext);
		return applyItemDisplayTransform(root, transform, transformContext == ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND);
	}

	private static Matrix4f applyFlatFramedItemTransform(Matrix4f root, ResolvedItemModel model) {
		return applyItemDisplayTransform(root, model, ItemDisplayTransformContext.FRAMED);
	}

	private static Matrix4f applyItemDisplayTransform(Matrix4f root, ItemModelTransform transform, boolean leftHand) {
		float translationX = leftHand ? -transform.translationX() : transform.translationX();
		float rotationY = leftHand ? -transform.rotationY() : transform.rotationY();
		float rotationZ = leftHand ? -transform.rotationZ() : transform.rotationZ();
		return new Matrix4f(root)
				.translate(translationX, transform.translationY(), transform.translationZ())
				.rotate(new Quaternionf().rotationXYZ(radians(transform.rotationX()), radians(rotationY), radians(rotationZ)))
				.scale(transform.scaleX(), transform.scaleY(), transform.scaleZ())
				.translate(-0.5F, -0.5F, -0.5F);
	}

	private static ItemVisualBounds itemVisualBounds(ItemVisual visual, ItemDisplayTransformContext transformContext) {
		if (visual == null) {
			return ItemVisualBounds.EMPTY;
		}
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		Matrix4f root = applyItemDisplayTransform(new Matrix4f(), visual.model(), transformContext);
		if (visual.model() != null && !visual.model().elements().isEmpty()) {
			for (ItemModelElement element : visual.model().elements()) {
				Matrix4f transform = itemElementTransform(root, element);
				for (float x : new float[]{(float) element.from().x, (float) element.to().x}) {
					for (float y : new float[]{(float) element.from().y, (float) element.to().y}) {
						for (float z : new float[]{(float) element.from().z, (float) element.to().z}) {
							Vector3f point = transformPosition(transform, x * PX, y * PX, z * PX);
							minY = Math.min(minY, point.y);
							maxY = Math.max(maxY, point.y);
						}
					}
				}
			}
		} else if (visual.flatTexture() != null) {
			for (Vector3f point : new Vector3f[]{
					transformPosition(root, 0.0F, 0.0F, 0.5F),
					transformPosition(root, 1.0F, 0.0F, 0.5F),
					transformPosition(root, 1.0F, 1.0F, 0.5F),
					transformPosition(root, 0.0F, 1.0F, 0.5F)
			}) {
				minY = Math.min(minY, point.y);
				maxY = Math.max(maxY, point.y);
			}
		}
		if (!Float.isFinite(minY) || !Float.isFinite(maxY)) {
			return ItemVisualBounds.EMPTY;
		}
		return new ItemVisualBounds(minY, maxY);
	}

	private static Matrix4f itemElementTransform(Matrix4f root, ItemModelElement element) {
		Matrix4f transform = new Matrix4f(root);
		if (element.rotation() != null) {
			transform.translate(
					(float) element.rotation().origin().x * PX,
					(float) element.rotation().origin().y * PX,
					(float) element.rotation().origin().z * PX
			);
			transform.mul(element.rotation().transform());
			transform.translate(
					(float) -element.rotation().origin().x * PX,
					(float) -element.rotation().origin().y * PX,
					(float) -element.rotation().origin().z * PX
			);
		}
		return transform;
	}

	private static void renderFlatItemLayers(
			RenderContext context,
			Matrix4f transform,
			ItemVisual visual,
			float x,
			float y,
			float centerZ,
			float width,
			float height
	) {
		List<Identifier> layers = flatItemLayers(visual);
		if (layers.isEmpty()) {
			if (visual.flatTexture() == null) {
				return;
			}
			layers = List.of(visual.flatTexture());
		}
		float layerSpacing = 1.0F / 128.0F;
		float startCenterZ = centerZ - (layers.size() - 1) * layerSpacing * 0.5F;
		for (int i = 0; i < layers.size(); i++) {
			Identifier layer = layers.get(i);
			int tintRgb = itemTintRgb(visual, i);
			float layerCenterZ = startCenterZ + i * layerSpacing;
			renderFlatItemLayerExtruded(context, transform, layer, tintRgb, x, y, layerCenterZ, width, height);
		}
	}

	private static void renderFlatItemLayersSingleSided(
			RenderContext context,
			Matrix4f transform,
			ItemVisual visual,
			float x,
			float y,
			float centerZ,
			float width,
			float height
	) {
		List<Identifier> layers = flatItemLayers(visual);
		if (layers.isEmpty()) {
			if (visual.flatTexture() == null) {
				return;
			}
			layers = List.of(visual.flatTexture());
		}
		float layerSpacing = 1.0F / 128.0F;
		float startCenterZ = centerZ - (layers.size() - 1) * layerSpacing * 0.5F;
		for (int i = 0; i < layers.size(); i++) {
			Identifier layer = layers.get(i);
			int tintRgb = itemTintRgb(visual, i);
			float layerCenterZ = startCenterZ + i * layerSpacing;
			renderFlatItemLayerHandExtruded(context, transform, layer, tintRgb, x, y, layerCenterZ, width, height);
		}
	}

	private static void renderFlatItemLayerExtruded(
			RenderContext context,
			Matrix4f transform,
			Identifier texture,
			int tintRgb,
			float x,
			float y,
			float centerZ,
			float width,
			float height
	) {
		renderFlatItemLayerExtruded(context, transform, texture, tintRgb, x, y, centerZ, width, height, true);
	}

	private static void renderFlatItemLayerHandExtruded(
			RenderContext context,
			Matrix4f transform,
			Identifier texture,
			int tintRgb,
			float x,
			float y,
			float centerZ,
			float width,
			float height
	) {
		renderFlatItemLayerExtruded(context, transform, texture, tintRgb, x, y, centerZ, width, height, false);
	}

	private static void renderFlatItemLayerExtruded(
			RenderContext context,
			Matrix4f transform,
			Identifier texture,
			int tintRgb,
			float x,
			float y,
			float centerZ,
			float width,
			float height,
			boolean renderBackPlane
	) {
		int material = context.materialResolver().materialForTexture(texture);
		float red = ((tintRgb >> 16) & 0xFF) / 255.0F;
		float green = ((tintRgb >> 8) & 0xFF) / 255.0F;
		float blue = (tintRgb & 0xFF) / 255.0F;
		float halfThickness = Math.min(width, height) / 32.0F;
		float frontZ = centerZ + halfThickness;
		float backZ = centerZ - halfThickness;
		addTexturedPlane(context, transform, x, y, frontZ, width, height, 0.0F, 0.0F, 1.0F, 1.0F, material, red, green, blue);
		if (renderBackPlane) {
			Matrix4f back = new Matrix4f(transform).rotateY((float) Math.PI);
			addTexturedPlane(context, back, -(x + width), y, -backZ, width, height, 0.0F, 0.0F, 1.0F, 1.0F, material, red, green, blue);
		}
		FlatSpriteMesh mesh = flatSpriteMesh(texture);
		if (mesh.isEmpty()) {
			return;
		}
		for (HorizontalSpriteSpan span : mesh.topEdges()) {
			addHorizontalSpriteEdge(context, transform, x, y, width, height, frontZ, backZ, mesh.width(), mesh.height(), span, material, red, green, blue, true);
		}
		for (HorizontalSpriteSpan span : mesh.bottomEdges()) {
			addHorizontalSpriteEdge(context, transform, x, y, width, height, frontZ, backZ, mesh.width(), mesh.height(), span, material, red, green, blue, false);
		}
		for (VerticalSpriteSpan span : mesh.leftEdges()) {
			addVerticalSpriteEdge(context, transform, x, y, width, height, frontZ, backZ, mesh.width(), mesh.height(), span, material, red, green, blue, true);
		}
		for (VerticalSpriteSpan span : mesh.rightEdges()) {
			addVerticalSpriteEdge(context, transform, x, y, width, height, frontZ, backZ, mesh.width(), mesh.height(), span, material, red, green, blue, false);
		}
	}

	private static FlatSpriteMesh flatSpriteMesh(Identifier texture) {
		return FLAT_SPRITE_MESH_CACHE.computeIfAbsent(texture.toString(), ignored -> buildFlatSpriteMesh(texture));
	}

	private static FlatSpriteMesh buildFlatSpriteMesh(Identifier texture) {
		BufferedImage image = renderableSpriteFrame(ASSETS.loadTexture(texture));
		if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
			return FlatSpriteMesh.EMPTY;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		boolean[][] opaque = new boolean[height][width];
		boolean anyOpaque = false;
		for (int py = 0; py < height; py++) {
			for (int px = 0; px < width; px++) {
				boolean solid = ((image.getRGB(px, py) >>> 24) & 0xFF) > 0;
				opaque[py][px] = solid;
				anyOpaque |= solid;
			}
		}
		if (!anyOpaque) {
			return FlatSpriteMesh.EMPTY;
		}
		List<HorizontalSpriteSpan> topEdges = new ArrayList<>();
		List<HorizontalSpriteSpan> bottomEdges = new ArrayList<>();
		for (int py = 0; py < height; py++) {
			addHorizontalSpriteSpans(opaque, width, height, py, true, topEdges);
			addHorizontalSpriteSpans(opaque, width, height, py, false, bottomEdges);
		}
		List<VerticalSpriteSpan> leftEdges = new ArrayList<>();
		List<VerticalSpriteSpan> rightEdges = new ArrayList<>();
		for (int px = 0; px < width; px++) {
			addVerticalSpriteSpans(opaque, width, height, px, true, leftEdges);
			addVerticalSpriteSpans(opaque, width, height, px, false, rightEdges);
		}
		return new FlatSpriteMesh(width, height, topEdges, bottomEdges, leftEdges, rightEdges);
	}

	private static BufferedImage renderableSpriteFrame(BufferedImage image) {
		if (image == null) {
			return null;
		}
		if (image.getHeight() > image.getWidth() && image.getHeight() % image.getWidth() == 0) {
			return image.getSubimage(0, 0, image.getWidth(), image.getWidth());
		}
		return image;
	}

	private static void addHorizontalSpriteSpans(
			boolean[][] opaque,
			int width,
			int height,
			int row,
			boolean topEdge,
			List<HorizontalSpriteSpan> spans
	) {
		int start = -1;
		for (int x = 0; x < width; x++) {
			boolean boundary = opaque[row][x] && (topEdge ? row == 0 || !opaque[row - 1][x] : row == height - 1 || !opaque[row + 1][x]);
			if (boundary) {
				if (start < 0) {
					start = x;
				}
			} else if (start >= 0) {
				spans.add(new HorizontalSpriteSpan(row, start, x));
				start = -1;
			}
		}
		if (start >= 0) {
			spans.add(new HorizontalSpriteSpan(row, start, width));
		}
	}

	private static void addVerticalSpriteSpans(
			boolean[][] opaque,
			int width,
			int height,
			int column,
			boolean leftEdge,
			List<VerticalSpriteSpan> spans
	) {
		int start = -1;
		for (int y = 0; y < height; y++) {
			boolean boundary = opaque[y][column] && (leftEdge ? column == 0 || !opaque[y][column - 1] : column == width - 1 || !opaque[y][column + 1]);
			if (boundary) {
				if (start < 0) {
					start = y;
				}
			} else if (start >= 0) {
				spans.add(new VerticalSpriteSpan(column, start, y));
				start = -1;
			}
		}
		if (start >= 0) {
			spans.add(new VerticalSpriteSpan(column, start, height));
		}
	}

	private static void addHorizontalSpriteEdge(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float width,
			float height,
			float frontZ,
			float backZ,
			int textureWidth,
			int textureHeight,
			HorizontalSpriteSpan span,
			int material,
			float red,
			float green,
			float blue,
			boolean topEdge
	) {
		float x0 = x + width * span.startX() / textureWidth;
		float x1 = x + width * span.endX() / textureWidth;
		float edgeY = topEdge
				? y + height * (1.0F - span.row() / (float) textureHeight)
				: y + height * (1.0F - (span.row() + 1) / (float) textureHeight);
		float u0 = span.startX() / (float) textureWidth;
		float u1 = span.endX() / (float) textureWidth;
		float v0 = span.row() / (float) textureHeight;
		float v1 = (span.row() + 1) / (float) textureHeight;
		Vector3f a = transformPosition(transform, x0, edgeY, topEdge ? backZ : frontZ);
		Vector3f b = transformPosition(transform, x1, edgeY, topEdge ? backZ : frontZ);
		Vector3f c = transformPosition(transform, x1, edgeY, topEdge ? frontZ : backZ);
		Vector3f d = transformPosition(transform, x0, edgeY, topEdge ? frontZ : backZ);
		addTexturedQuadExact(context, a, b, c, d, u0, v0, u1, v0, u1, v1, u0, v1, material, red, green, blue);
	}

	private static void addVerticalSpriteEdge(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float width,
			float height,
			float frontZ,
			float backZ,
			int textureWidth,
			int textureHeight,
			VerticalSpriteSpan span,
			int material,
			float red,
			float green,
			float blue,
			boolean leftEdge
	) {
		float edgeX = leftEdge
				? x + width * span.column() / (float) textureWidth
				: x + width * (span.column() + 1) / (float) textureWidth;
		float yTop = y + height * (1.0F - span.startY() / (float) textureHeight);
		float yBottom = y + height * (1.0F - span.endY() / (float) textureHeight);
		float u0 = span.column() / (float) textureWidth;
		float u1 = (span.column() + 1) / (float) textureWidth;
		float v0 = span.startY() / (float) textureHeight;
		float v1 = span.endY() / (float) textureHeight;
		Vector3f a = transformPosition(transform, edgeX, yBottom, leftEdge ? frontZ : backZ);
		Vector3f b = transformPosition(transform, edgeX, yBottom, leftEdge ? backZ : frontZ);
		Vector3f c = transformPosition(transform, edgeX, yTop, leftEdge ? backZ : frontZ);
		Vector3f d = transformPosition(transform, edgeX, yTop, leftEdge ? frontZ : backZ);
		addTexturedQuadExact(context, a, b, c, d, u0, v1, u1, v1, u1, v0, u0, v0, material, red, green, blue);
	}

	private static int itemTintRgb(ItemVisual visual, int tintIndex) {
		if (visual == null || visual.tintColors() == null || tintIndex < 0 || tintIndex >= visual.tintColors().length) {
			return 0xFFFFFF;
		}
		return visual.tintColors()[tintIndex] & 0xFFFFFF;
	}

	private static List<Identifier> flatItemLayers(ItemVisual visual) {
		List<Identifier> layers = new ArrayList<>();
		if (visual == null || visual.model() == null) {
			return layers;
		}
		for (int i = 0; i < 8; i++) {
			Identifier texture = resolveTextureIdentifier(visual.model().textures(), "#layer" + i);
			if (texture == null) {
				if (i == 0 && visual.flatTexture() != null) {
					texture = visual.flatTexture();
				} else {
					break;
				}
			}
			layers.add(texture);
		}
		return layers;
	}

	private static ItemModelTransform itemModelTransform(ResolvedItemModel model, ItemDisplayTransformContext transformContext) {
		if (model == null || model.transforms().isEmpty()) {
			return ItemModelTransform.IDENTITY;
		}
		ItemModelTransform transform = model.transforms().get(transformContext);
		if (transform != null) {
			return transform;
		}
		if (transformContext == ItemDisplayTransformContext.FRAMED) {
			transform = model.transforms().get(ItemDisplayTransformContext.FIXED);
			if (transform != null) {
				return transform;
			}
		}
		if (transformContext == ItemDisplayTransformContext.GROUND) {
			transform = model.transforms().get(ItemDisplayTransformContext.GROUND);
			if (transform != null) {
				return transform;
			}
		}
		if (transformContext == ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND) {
			transform = model.transforms().get(ItemDisplayTransformContext.THIRD_PERSON_RIGHT_HAND);
			if (transform != null) {
				return transform;
			}
		}
		if (transformContext == ItemDisplayTransformContext.FIRST_PERSON_LEFT_HAND) {
			transform = model.transforms().get(ItemDisplayTransformContext.FIRST_PERSON_RIGHT_HAND);
			if (transform != null) {
				return transform;
			}
		}
		return ItemModelTransform.IDENTITY;
	}

	private static void renderBlockEntityModel(RenderContext context, Matrix4f root, ResolvedItemModel model) {
		ItemVisual visual = new ItemVisual(primaryTexture(model), model, null, null);
		Matrix4f transform = new Matrix4f(root).translate(-0.5F, -0.5F, -0.5F);
		for (ItemModelElement element : model.elements()) {
			renderItemModelElement(context, transform, visual, element);
		}
	}

	private static Matrix4f applyPose(Matrix4f parent, Rotations rotations, float pivotX, float pivotY, float pivotZ) {
		if (rotations == null) {
			return parent;
		}
		return rotateAround(parent, pivotX, pivotY, pivotZ, radians(rotations.x()), radians(rotations.y()), radians(rotations.z()));
	}

	private static Matrix4f rotateAround(Matrix4f parent, float pivotX, float pivotY, float pivotZ, float pitch, float yaw, float roll) {
		return new Matrix4f(parent)
				.translate(pivotX * PX, pivotY * PX, pivotZ * PX)
				.rotateY(yaw)
				.rotateX(pitch)
				.rotateZ(roll)
				.translate(-pivotX * PX, -pivotY * PX, -pivotZ * PX);
	}

	private static float radians(float degrees) {
		return degrees * ((float) Math.PI / 180.0F);
	}

	private static float wrapDegrees(float degrees) {
		return Mth.wrapDegrees(degrees);
	}

	private static void addPlane(RenderContext context, Matrix4f transform, float x, float y, float z, float width, float height, int material) {
		CameraGeometry.addPlane(context, transform, x, y, z, width, height, material);
	}

	private static void addDoubleSidedPlane(RenderContext context, Matrix4f transform, float x, float y, float z, float width, float height, int material) {
		CameraGeometry.addDoubleSidedPlane(context, transform, x, y, z, width, height, material);
	}

	private static void addTexturedDoubleSidedPlane(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float u0,
			float v0,
			float u1,
			float v1,
			int material,
			float red,
			float green,
			float blue
	) {
		CameraGeometry.addTexturedDoubleSidedPlane(context, transform, x, y, z, width, height, u0, v0, u1, v1, material, red, green, blue);
	}

	private static void addSeparatedTexturedDoubleSidedPlane(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float centerZ,
			float width,
			float height,
			float halfThickness,
			float u0,
			float v0,
			float u1,
			float v1,
			int material,
			float red,
			float green,
			float blue
	) {
		CameraGeometry.addSeparatedTexturedDoubleSidedPlane(context, transform, x, y, centerZ, width, height, halfThickness, u0, v0, u1, v1, material, red, green, blue);
	}

	private static void addTexturedPlane(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float texU0,
			float texV0,
			float texU1,
			float texV1,
			int material,
			float red,
			float green,
			float blue
	) {
		CameraGeometry.addTexturedPlane(context, transform, x, y, z, width, height, texU0, texV0, texU1, texV1, material, red, green, blue);
	}

	private static void addBox(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float depth,
			int texU,
			int texV,
			int texWidth,
			int texHeight,
			int material,
			boolean mirror,
			float inflate
	) {
		CameraGeometry.addBox(context, transform, x, y, z, width, height, depth, texU, texV, texWidth, texHeight, material, mirror, inflate);
	}

	private static void addPlayerSkinBox(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float depth,
			int texU,
			int texV,
			int texWidth,
			int texHeight,
			int material,
			boolean mirror,
			float inflate
	) {
		CameraGeometry.addPlayerSkinBox(context, transform, x, y, z, width, height, depth, texU, texV, texWidth, texHeight, material, mirror, inflate);
	}

	private static void addBox(
			RenderContext context,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			float depth,
			int texU,
			int texV,
			int texWidth,
			int texHeight,
			int material,
			boolean mirror,
			float inflate,
			int overrideSkyLight,
			int overrideBlockLight
	) {
		CameraGeometry.addBox(context, transform, x, y, z, width, height, depth, texU, texV, texWidth, texHeight, material, mirror, inflate, overrideSkyLight, overrideBlockLight);
	}

	private static float uv(float value, int size) {
		return CameraGeometry.uv(value, size);
	}

	private static Vector3f transformPosition(Matrix4f transform, float x, float y, float z) {
		return CameraGeometry.transformPosition(transform, x, y, z);
	}

	private static void addQuad(
			RenderContext context,
			Vector3f a,
			Vector3f b,
			Vector3f c,
			Vector3f d,
			float u0,
			float u1,
			float v0,
			float v1,
			int material,
			int skyLight,
			int blockLight,
			float red,
			float green,
			float blue
	) {
		CameraGeometry.addQuad(context, a, b, c, d, u0, u1, v0, v1, material, skyLight, blockLight, red, green, blue);
	}

	private static void addQuadExact(
			RenderContext context,
			Vector3f a,
			Vector3f b,
			Vector3f c,
			Vector3f d,
			float au,
			float av,
			float bu,
			float bv,
			float cu,
			float cv,
			float du,
			float dv,
			int material,
			int skyLight,
			int blockLight,
			float red,
			float green,
			float blue
	) {
		CameraGeometry.addQuadExact(context, a, b, c, d, au, av, bu, bv, cu, cv, du, dv, material, skyLight, blockLight, red, green, blue);
	}

	private static void addTexturedQuadExact(
			RenderContext context,
			Vector3f a,
			Vector3f b,
			Vector3f c,
			Vector3f d,
			float au,
			float av,
			float bu,
			float bv,
			float cu,
			float cv,
			float du,
			float dv,
			int material,
			float red,
			float green,
			float blue
	) {
		LightSample light = context.lightAt(
				(a.x + b.x + c.x + d.x) * 0.25F,
				(a.y + b.y + c.y + d.y) * 0.25F,
				(a.z + b.z + c.z + d.z) * 0.25F
		);
		addQuadExact(context, a, b, c, d, au, av, bu, bv, cu, cv, du, dv, material, light.sky(), light.block(), red, green, blue);
	}

	private static void renderItemModelElement(RenderContext context, Matrix4f root, ItemVisual visual, ItemModelElement element) {
		ResolvedItemModel model = visual.model();
		Matrix4f transform = new Matrix4f(root);
		if (element.rotation() != null) {
			transform.translate(
					(float) element.rotation().origin().x * PX,
					(float) element.rotation().origin().y * PX,
					(float) element.rotation().origin().z * PX
			);
			transform.mul(element.rotation().transform());
			transform.translate(
					(float) -element.rotation().origin().x * PX,
					(float) -element.rotation().origin().y * PX,
					(float) -element.rotation().origin().z * PX
			);
		}

		for (Map.Entry<Direction, ItemModelFace> entry : element.faces().entrySet()) {
			Identifier texture = resolveTextureIdentifier(model.textures(), entry.getValue().texture());
			if (texture == null) {
				continue;
			}
			Vector3f[] vertices = itemFaceVertices(entry.getKey(), element.from(), element.to(), transform);
			double[] uv = entry.getValue().uv() != null ? entry.getValue().uv() : defaultItemFaceUv(entry.getKey(), element.from(), element.to());
			float[] rotatedUv = rotateItemFaceUv(uv, entry.getValue().rotation());
			LightSample light = context.lightAt(
					(vertices[0].x + vertices[1].x + vertices[2].x + vertices[3].x) * 0.25F,
					(vertices[0].y + vertices[1].y + vertices[2].y + vertices[3].y) * 0.25F,
					(vertices[0].z + vertices[1].z + vertices[2].z + vertices[3].z) * 0.25F
			);
			int tintRgb = entry.getValue().tintIndex() >= 0 && visual.tintColors() != null && entry.getValue().tintIndex() < visual.tintColors().length
					? visual.tintColors()[entry.getValue().tintIndex()] & 0xFFFFFF
					: 0xFFFFFF;
			addQuadExact(
					context,
					vertices[0],
					vertices[1],
					vertices[2],
					vertices[3],
					rotatedUv[0],
					rotatedUv[1],
					rotatedUv[2],
					rotatedUv[3],
					rotatedUv[4],
					rotatedUv[5],
					rotatedUv[6],
					rotatedUv[7],
					context.materialResolver().materialForTexture(texture),
					light.sky(),
					light.block(),
					((tintRgb >> 16) & 0xFF) / 255.0F,
					((tintRgb >> 8) & 0xFF) / 255.0F,
					(tintRgb & 0xFF) / 255.0F
			);
		}
	}

	private static Vector3f[] itemFaceVertices(Direction direction, Vec3 from, Vec3 to, Matrix4f transform) {
		return switch (direction) {
			case DOWN -> new Vector3f[]{
					transformPosition(transform, (float) from.x * PX, (float) from.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) from.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) from.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) from.y * PX, (float) from.z * PX)
			};
			case UP -> new Vector3f[]{
					transformPosition(transform, (float) from.x * PX, (float) to.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) to.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) to.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) to.y * PX, (float) to.z * PX)
			};
			case NORTH -> new Vector3f[]{
					transformPosition(transform, (float) to.x * PX, (float) from.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) from.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) to.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) to.y * PX, (float) from.z * PX)
			};
			case SOUTH -> new Vector3f[]{
					transformPosition(transform, (float) from.x * PX, (float) from.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) from.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) to.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) to.y * PX, (float) to.z * PX)
			};
			case WEST -> new Vector3f[]{
					transformPosition(transform, (float) from.x * PX, (float) from.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) from.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) to.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) from.x * PX, (float) to.y * PX, (float) from.z * PX)
			};
			case EAST -> new Vector3f[]{
					transformPosition(transform, (float) to.x * PX, (float) from.y * PX, (float) to.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) from.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) to.y * PX, (float) from.z * PX),
					transformPosition(transform, (float) to.x * PX, (float) to.y * PX, (float) to.z * PX)
			};
		};
	}

	private static double[] defaultItemFaceUv(Direction direction, Vec3 from, Vec3 to) {
		return switch (direction) {
			case DOWN -> new double[]{from.x, 16.0D - to.z, to.x, 16.0D - from.z};
			case UP -> new double[]{from.x, from.z, to.x, to.z};
			case NORTH -> new double[]{16.0D - to.x, 16.0D - to.y, 16.0D - from.x, 16.0D - from.y};
			case SOUTH -> new double[]{from.x, 16.0D - to.y, to.x, 16.0D - from.y};
			case WEST -> new double[]{from.z, 16.0D - to.y, to.z, 16.0D - from.y};
			case EAST -> new double[]{16.0D - to.z, 16.0D - to.y, 16.0D - from.z, 16.0D - from.y};
		};
	}

	private static float[] rotateItemFaceUv(double[] uv, int rotation) {
		float u0 = (float) (uv[0] / 16.0D);
		float v0 = (float) (uv[1] / 16.0D);
		float u1 = (float) (uv[2] / 16.0D);
		float v1 = (float) (uv[3] / 16.0D);
		float[] points = new float[]{
				u0, v1,
				u1, v1,
				u1, v0,
				u0, v0
		};
		int turns = Math.floorMod(rotation / 90, 4);
		for (int i = 0; i < turns; i++) {
			points = new float[]{
					points[6], points[7],
					points[0], points[1],
					points[2], points[3],
					points[4], points[5]
			};
		}
		return points;
	}

	private static Matrix4f rotationMatrix(Direction.Axis axis, float angle, boolean rescale) {
		float radians = radians(angle);
		Matrix4f transform = switch (axis) {
			case X -> new Matrix4f().rotateX(radians);
			case Y -> new Matrix4f().rotateY(radians);
			case Z -> new Matrix4f().rotateZ(radians);
		};
		if (rescale && !Mth.equal(angle, 0.0F)) {
			float scale = 1.0F / Math.max(Math.abs(Mth.cos(radians)), 1.0E-4F);
			switch (axis) {
				case X -> transform.scale(1.0F, scale, scale);
				case Y -> transform.scale(scale, 1.0F, scale);
				case Z -> transform.scale(scale, scale, 1.0F);
			}
		}
		return transform;
	}

	private static Identifier variantTexture(Holder<? extends Object> holder, Identifier fallback) {
		if (holder == null || holder.value() == null) {
			return fallback;
		}
		Object value = holder.value();
		if (value instanceof CowVariant cowVariant) {
			return cowVariant.modelAndTexture().asset().id();
		}
		if (value instanceof PigVariant pigVariant) {
			return pigVariant.modelAndTexture().asset().id();
		}
		if (value instanceof ChickenVariant chickenVariant) {
			return chickenVariant.modelAndTexture().asset().id();
		}
		return fallback;
	}

	static Identifier holderTexture(Holder<?> holder, String prefix) {
		if (holder == null) {
			return null;
		}
		return holder.unwrapKey()
				.map(resourceKey -> {
					Identifier identifier = resourceKey.identifier();
					return Identifier.fromNamespaceAndPath(identifier.getNamespace(), prefix + identifier.getPath());
				})
				.orElse(null);
	}

	static Identifier professionLevelTexture(int level) {
		return professionLevelTexture(level, "entity/villager/profession_level/");
	}

	static Identifier professionLevelTexture(int level, String prefix) {
		return switch (Mth.clamp(level, 1, 5)) {
			case 1 -> Identifier.fromNamespaceAndPath("minecraft", prefix + "stone");
			case 2 -> Identifier.fromNamespaceAndPath("minecraft", prefix + "iron");
			case 3 -> Identifier.fromNamespaceAndPath("minecraft", prefix + "gold");
			case 4 -> Identifier.fromNamespaceAndPath("minecraft", prefix + "emerald");
			default -> Identifier.fromNamespaceAndPath("minecraft", prefix + "diamond");
		};
	}

	private static ItemVisual resolveItemVisual(HeldItemSnapshot heldItem, ItemDisplayTransformContext transformContext) {
		if (heldItem == null || heldItem.isEmpty()) {
			return null;
		}
		return resolveItemVisual(
				heldItem.stack(),
				new ItemDefinitionRenderState(
						transformContext,
						heldItem.usingItem(),
						heldItem.useTicks(),
						heldItem.fishingRodCast(),
						heldItem.contextDimensionId(),
						heldItem.gameTime(),
						heldItem.dayTime()
				)
		);
	}

	private static ItemVisual resolveItemVisual(ItemStack stack, ItemDisplayTransformContext transformContext) {
		return resolveItemVisual(stack, new ItemDefinitionRenderState(transformContext, false, 0.0F, false, null, 0L, 0L));
	}

	private static ItemVisual resolveItemVisual(ItemStack stack, ItemDefinitionRenderState renderState) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) {
			return null;
		}
		return resolveItemVisualInternal(stack, itemId, renderState);
	}

	private static ItemVisual resolveItemVisualInternal(ItemStack stack, Identifier itemId, ItemDefinitionRenderState renderState) {
		ResolvedItemDefinition definition = resolveItemDefinition(stack, itemId, renderState);
		Identifier rootModelId = definition == null || definition.modelId() == null ? itemId.withPrefix("item/") : definition.modelId();
		ResolvedItemModel model = resolveItemModel(rootModelId, new HashSet<>());
		Identifier flatTexture = model == null ? null : primaryTexture(model);
		ClientModelSnapshot specialModel = definition == null ? null : resolveSpecialItemClientModel(stack, definition);
		boolean hasRenderableItemVisual = specialModel != null || flatTexture != null || (model != null && !model.elements().isEmpty());
		boolean preferBlockItemFallback = definition != null
				&& definition.specialType() != null
				&& specialModel == null
				&& stack.getItem() instanceof BlockItem;
		if ((!hasRenderableItemVisual || preferBlockItemFallback) && stack.getItem() instanceof BlockItem blockItem) {
			Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
			if (blockId != null) {
				model = resolveItemModel(blockId.withPrefix("block/"), new HashSet<>());
				flatTexture = model == null ? null : primaryTexture(model);
				hasRenderableItemVisual = specialModel != null || flatTexture != null || (model != null && !model.elements().isEmpty());
			}
		}
		if (!hasRenderableItemVisual) {
			return null;
		}
		return new ItemVisual(flatTexture, model, definition == null ? null : definition.tintColors(), specialModel);
	}

	private static ResolvedItemDefinition resolveItemDefinition(ItemStack stack, Identifier itemId, ItemDefinitionRenderState renderState) {
		JsonObject itemDefinition = ASSETS.loadJsonAsset("assets/" + itemId.getNamespace() + "/items/" + itemId.getPath() + ".json");
		if (itemDefinition != null && itemDefinition.has("model") && itemDefinition.get("model").isJsonObject()) {
			ResolvedItemDefinition resolved = resolveItemDefinitionModel(itemDefinition.getAsJsonObject("model"), stack, renderState);
			if (resolved != null) {
				return resolved;
			}
		}
		return new ResolvedItemDefinition(itemId.withPrefix("item/"), null, null);
	}

	private static ResolvedItemDefinition resolveItemDefinitionModel(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		if (modelObject == null || !modelObject.has("type")) {
			return null;
		}
		String type = modelObject.get("type").getAsString();
		return switch (type) {
			case "minecraft:model" -> modelObject.has("model")
					? new ResolvedItemDefinition(
							Identifier.tryParse(modelObject.get("model").getAsString()),
							resolveItemTintColors(modelObject, stack),
							null
					)
					: null;
			case "minecraft:special" -> modelObject.has("base")
					? new ResolvedItemDefinition(
							Identifier.tryParse(modelObject.get("base").getAsString()),
							resolveItemTintColors(modelObject, stack),
							specialItemType(modelObject)
					)
					: modelObject.has("model") && modelObject.get("model").isJsonObject()
					? resolveItemDefinitionModel(modelObject.getAsJsonObject("model"), stack, renderState)
					: null;
			case "minecraft:composite" -> resolveCompositeItemDefinition(modelObject, stack, renderState);
			case "minecraft:condition" -> resolveConditionalItemDefinition(modelObject, stack, renderState);
			case "minecraft:select" -> resolveSelectedItemDefinition(modelObject, stack, renderState);
			case "minecraft:range_dispatch" -> resolveRangeItemDefinition(modelObject, stack, renderState);
			default -> null;
		};
	}

	private static String specialItemType(JsonObject modelObject) {
		if (modelObject == null || !modelObject.has("model") || !modelObject.get("model").isJsonObject()) {
			return null;
		}
		JsonObject specialModel = modelObject.getAsJsonObject("model");
		return specialModel.has("type") ? specialModel.get("type").getAsString() : null;
	}

	private static int[] resolveItemTintColors(JsonObject modelObject, ItemStack stack) {
		if (modelObject == null || !modelObject.has("tints") || !modelObject.get("tints").isJsonArray()) {
			return null;
		}
		JsonArray tintArray = modelObject.getAsJsonArray("tints");
		if (tintArray.isEmpty()) {
			return null;
		}
		int[] tintColors = new int[tintArray.size()];
		for (int i = 0; i < tintArray.size(); i++) {
			JsonElement tintElement = tintArray.get(i);
			tintColors[i] = tintElement.isJsonObject()
					? resolveItemTintColor(tintElement.getAsJsonObject(), stack)
					: 0xFFFFFF;
		}
		return tintColors;
	}

	private static int resolveItemTintColor(JsonObject tintObject, ItemStack stack) {
		if (tintObject == null || !tintObject.has("type")) {
			return 0xFFFFFF;
		}
		int fallback = tintObject.has("default") ? tintObject.get("default").getAsInt() : tintObject.has("value") ? tintObject.get("value").getAsInt() : 0xFFFFFF;
		return switch (tintObject.get("type").getAsString()) {
			case "minecraft:constant" -> fallback & 0xFFFFFF;
			case "minecraft:dye" -> DyedItemColor.getOrDefault(stack, fallback) & 0xFFFFFF;
			case "minecraft:grass" -> grassTintRgb(tintObject);
			case "minecraft:potion", "minecraft:map_color", "minecraft:firework" -> fallback & 0xFFFFFF;
			default -> fallback & 0xFFFFFF;
		};
	}

	private static int grassTintRgb(JsonObject tintObject) {
		double temperature = tintObject.has("temperature") ? tintObject.get("temperature").getAsDouble() : 0.5D;
		double downfall = tintObject.has("downfall") ? tintObject.get("downfall").getAsDouble() : 1.0D;
		try {
			Class<?> grassColorClass = Class.forName("net.minecraft.world.level.GrassColor");
			Method getMethod = grassColorClass.getMethod("get", double.class, double.class);
			Object color = getMethod.invoke(null, temperature, downfall);
			if (color instanceof Integer integer) {
				return integer & 0xFFFFFF;
			}
		} catch (ReflectiveOperationException ignored) {
		}
		return 0x7FB238;
	}

	private static ClientModelSnapshot resolveSpecialItemClientModel(ItemStack stack, ResolvedItemDefinition definition) {
		if (definition == null || definition.specialType() == null || !VanillaClientModels.isAvailable()) {
			return null;
		}
		return switch (definition.specialType()) {
			case "minecraft:shield" -> specialShieldClientModel();
			case "minecraft:trident" -> specialTridentClientModel();
			default -> null;
		};
	}

	private static ClientModelSnapshot specialShieldClientModel() {
		return clientModelSnapshot(
				Vec3.ZERO,
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				Map.of(),
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot("net.minecraft.client.model.ShieldModel", minecraftTexture("entity/shield/base"), 0xFFFFFF, false)
								.withFactory("createLayer")
				}
		);
	}

	private static ClientModelSnapshot specialTridentClientModel() {
		return clientModelSnapshot(
				Vec3.ZERO,
				0.0F,
				0.0F,
				1.0F,
				ClientModelTransformKind.BLOCK_ENTITY,
				Map.of(),
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot("net.minecraft.client.model.TridentModel", minecraftTexture("item/trident"), 0xFFFFFF, false)
								.withFactory("createLayer")
				}
		);
	}

	private static ResolvedItemDefinition resolveCompositeItemDefinition(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		if (!modelObject.has("models") || !modelObject.get("models").isJsonArray()) {
			return null;
		}
		for (JsonElement element : modelObject.getAsJsonArray("models")) {
			if (!element.isJsonObject()) {
				continue;
			}
			ResolvedItemDefinition resolved = resolveItemDefinitionModel(element.getAsJsonObject(), stack, renderState);
			if (resolved != null && resolved.modelId() != null) {
				return resolved;
			}
		}
		return null;
	}

	private static ResolvedItemDefinition resolveConditionalItemDefinition(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		boolean result = evaluateItemCondition(modelObject, stack, renderState);
		String branch = result ? "on_true" : "on_false";
		if (modelObject.has(branch) && modelObject.get(branch).isJsonObject()) {
			ResolvedItemDefinition resolved = resolveItemDefinitionModel(modelObject.getAsJsonObject(branch), stack, renderState);
			if (resolved != null) {
				return resolved;
			}
		}
		String fallback = result ? "on_false" : "on_true";
		if (modelObject.has(fallback) && modelObject.get(fallback).isJsonObject()) {
			return resolveItemDefinitionModel(modelObject.getAsJsonObject(fallback), stack, renderState);
		}
		return null;
	}

	private static ResolvedItemDefinition resolveSelectedItemDefinition(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		String value = selectedItemPropertyValue(modelObject, stack, renderState);
		if (value != null && modelObject.has("cases") && modelObject.get("cases").isJsonArray()) {
			for (JsonElement caseElement : modelObject.getAsJsonArray("cases")) {
				if (!caseElement.isJsonObject()) {
					continue;
				}
				JsonObject caseObject = caseElement.getAsJsonObject();
				if (!caseObject.has("model") || !caseObject.get("model").isJsonObject()) {
					continue;
				}
				if (!caseObject.has("when") || matchesItemSelection(caseObject.get("when"), value)) {
					ResolvedItemDefinition resolved = resolveItemDefinitionModel(caseObject.getAsJsonObject("model"), stack, renderState);
					if (resolved != null) {
						return resolved;
					}
				}
			}
		}
		if (modelObject.has("fallback") && modelObject.get("fallback").isJsonObject()) {
			return resolveItemDefinitionModel(modelObject.getAsJsonObject("fallback"), stack, renderState);
		}
		return null;
	}

	private static ResolvedItemDefinition resolveRangeItemDefinition(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		float value = rangeDispatchValue(modelObject, stack, renderState);
		ResolvedItemDefinition selected = null;
		float bestThreshold = Float.NEGATIVE_INFINITY;
		if (modelObject.has("entries") && modelObject.get("entries").isJsonArray()) {
			for (JsonElement entryElement : modelObject.getAsJsonArray("entries")) {
				if (!entryElement.isJsonObject()) {
					continue;
				}
				JsonObject entryObject = entryElement.getAsJsonObject();
				if (!entryObject.has("model") || !entryObject.get("model").isJsonObject() || !entryObject.has("threshold")) {
					continue;
				}
				float threshold = entryObject.get("threshold").getAsFloat();
				if (value >= threshold && threshold >= bestThreshold) {
					ResolvedItemDefinition resolved = resolveItemDefinitionModel(entryObject.getAsJsonObject("model"), stack, renderState);
					if (resolved != null) {
						selected = resolved;
						bestThreshold = threshold;
					}
				}
			}
		}
		if (selected != null) {
			return selected;
		}
		if (modelObject.has("fallback") && modelObject.get("fallback").isJsonObject()) {
			return resolveItemDefinitionModel(modelObject.getAsJsonObject("fallback"), stack, renderState);
		}
		return null;
	}

	private static boolean evaluateItemCondition(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		String property = modelObject.has("property") ? modelObject.get("property").getAsString() : "";
		return switch (property) {
			case "minecraft:broken" -> stack.isDamageableItem() && stack.getDamageValue() >= Math.max(0, stack.getMaxDamage() - 1);
			case "minecraft:using_item" -> renderState.usingItem();
			case "minecraft:fishing_rod/cast" -> renderState.fishingRodCast();
			case "minecraft:has_component" -> hasItemComponent(stack, modelObject.has("component") ? modelObject.get("component").getAsString() : null);
			default -> false;
		};
	}

	private static boolean hasItemComponent(ItemStack stack, String componentId) {
		if (stack == null || stack.isEmpty() || componentId == null) {
			return false;
		}
		return switch (componentId) {
			case "minecraft:lodestone_tracker" -> stack.has(DataComponents.LODESTONE_TRACKER);
			case "minecraft:dyed_color" -> stack.has(DataComponents.DYED_COLOR);
			case "minecraft:trim" -> stack.has(DataComponents.TRIM);
			case "minecraft:charged_projectiles" -> stack.has(DataComponents.CHARGED_PROJECTILES);
			default -> false;
		};
	}

	private static String selectedItemPropertyValue(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		String property = modelObject.has("property") ? modelObject.get("property").getAsString() : "";
		return switch (property) {
			case "minecraft:display_context" -> renderState.transformContext().serializedName();
			case "minecraft:context_dimension" -> renderState.contextDimensionId();
			case "minecraft:charge_type" -> itemChargeType(stack);
			case "minecraft:trim_material" -> trimMaterialId(stack);
			case "minecraft:block_state" -> blockStateSelectionValue(stack, modelObject.has("block_state_property") ? modelObject.get("block_state_property").getAsString() : null);
			default -> null;
		};
	}

	private static boolean matchesItemSelection(JsonElement when, String value) {
		if (when == null || value == null) {
			return false;
		}
		if (when.isJsonPrimitive()) {
			return value.equals(when.getAsString());
		}
		if (when.isJsonArray()) {
			for (JsonElement element : when.getAsJsonArray()) {
				if (matchesItemSelection(element, value)) {
					return true;
				}
			}
		}
		return false;
	}

	private static float rangeDispatchValue(JsonObject modelObject, ItemStack stack, ItemDefinitionRenderState renderState) {
		String property = modelObject.has("property") ? modelObject.get("property").getAsString() : "";
		float scale = modelObject.has("scale") ? modelObject.get("scale").getAsFloat() : 1.0F;
		return switch (property) {
			case "minecraft:use_duration" -> renderState.useTicks() * scale;
			case "minecraft:use_cycle" -> {
				float period = modelObject.has("period") ? Math.max(1.0F, modelObject.get("period").getAsFloat()) : 1.0F;
				yield (renderState.useTicks() % period) * scale;
			}
			case "minecraft:crossbow/pull" -> crossbowPullValue(stack, renderState.useTicks());
			case "minecraft:time" -> itemTimeValue(modelObject, renderState, scale);
			default -> 0.0F;
		};
	}

	private static float itemTimeValue(JsonObject modelObject, ItemDefinitionRenderState renderState, float scale) {
		String source = modelObject.has("source") ? modelObject.get("source").getAsString() : "random";
		return switch (source) {
			case "daytime" -> {
				float dayFraction = Mth.positiveModulo((renderState.dayTime() % 24000L) / 24000.0F, 1.0F);
				yield dayFraction * scale;
			}
			default -> {
				long seed = renderState.gameTime() * 17L + 31L;
				yield Mth.positiveModulo((seed & 63L) + ((renderState.gameTime() & 1L) == 0L ? 0.25F : 0.75F), scale);
			}
		};
	}

	private static float crossbowPullValue(ItemStack stack, float useTicks) {
		if (stack == null || !(stack.getItem() instanceof net.minecraft.world.item.CrossbowItem) || useTicks <= 0.0F) {
			return 0.0F;
		}
		int chargeDuration;
		try {
			chargeDuration = net.minecraft.world.item.CrossbowItem.getChargeDuration(stack, null);
		} catch (Throwable ignored) {
			chargeDuration = 25;
		}
		return Mth.clamp(useTicks / Math.max(1.0F, chargeDuration), 0.0F, 1.0F);
	}

	private static String itemChargeType(ItemStack stack) {
		net.minecraft.world.item.component.ChargedProjectiles chargedProjectiles = stack == null ? null : stack.get(DataComponents.CHARGED_PROJECTILES);
		if (chargedProjectiles == null || chargedProjectiles.isEmpty()) {
			return null;
		}
		for (ItemStack charged : chargedProjectiles.getItems()) {
			if (charged.getItem() instanceof net.minecraft.world.item.FireworkRocketItem) {
				return "rocket";
			}
			if (charged.getItem() instanceof net.minecraft.world.item.ArrowItem) {
				return "arrow";
			}
		}
		return null;
	}

	private static String trimMaterialId(ItemStack stack) {
		ArmorTrim trim = stack == null ? null : stack.get(DataComponents.TRIM);
		if (trim == null || trim.material() == null) {
			return null;
		}
		return trim.material()
				.unwrapKey()
				.map(ResourceKey::identifier)
				.map(Identifier::toString)
				.orElse(null);
	}

	private static String blockStateSelectionValue(ItemStack stack, String propertyName) {
		if (stack == null || stack.isEmpty() || propertyName == null || !(stack.getItem() instanceof BlockItem blockItem)) {
			return null;
		}
		net.minecraft.world.level.block.state.BlockState state = blockItem.getBlock().defaultBlockState();
		net.minecraft.world.level.block.state.properties.Property<?> property = state.getProperties().stream()
				.filter(candidate -> propertyName.equals(candidate.getName()))
				.findFirst()
				.orElse(null);
		if (property == null) {
			return null;
		}
		return propertyValueName(state, property);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String propertyValueName(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.state.properties.Property<?> property) {
		return ((net.minecraft.world.level.block.state.properties.Property) property).getName(state.getValue((net.minecraft.world.level.block.state.properties.Property) property));
	}

	private static ResolvedItemModel resolveItemModel(Identifier modelId, Set<String> resolving) {
		String cacheKey = "model:" + modelId;
		ItemVisual cached = ITEM_VISUAL_CACHE.get(cacheKey);
		if (cached != null) {
			return cached.model();
		}
		if (!resolving.add(modelId.toString())) {
			return null;
		}
		ResolvedItemModel resolved;
		try {
			resolved = doResolveItemModel(modelId, resolving);
		} finally {
			resolving.remove(modelId.toString());
		}
		ITEM_VISUAL_CACHE.putIfAbsent(cacheKey, new ItemVisual(primaryTexture(resolved), resolved, null, null));
		return resolved;
	}

	private static ResolvedItemModel doResolveItemModel(Identifier modelId, Set<String> resolving) {
		JsonObject json = ASSETS.loadModel(modelId);
		if (json == null) {
			return null;
		}

		Map<String, String> textures = new HashMap<>();
		List<ItemModelElement> elements = new ArrayList<>();
		Map<ItemDisplayTransformContext, ItemModelTransform> transforms = new HashMap<>();
		if (json.has("parent")) {
			Identifier parentId = Identifier.tryParse(json.get("parent").getAsString());
			if (parentId != null && !"builtin/generated".equals(parentId.toString())) {
				ResolvedItemModel parent = resolveItemModel(parentId, resolving);
				if (parent != null) {
					textures.putAll(parent.textures());
					elements.addAll(parent.elements());
					transforms.putAll(parent.transforms());
				}
			}
		}
		if (json.has("textures") && json.get("textures").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
				textures.put(entry.getKey(), entry.getValue().getAsString());
			}
		}
		if (json.has("display") && json.get("display").isJsonObject()) {
			transforms.putAll(parseItemModelTransforms(json.getAsJsonObject("display")));
		}
		if (json.has("elements") && json.get("elements").isJsonArray()) {
			elements.clear();
			for (JsonElement elementJson : json.getAsJsonArray("elements")) {
				if (!elementJson.isJsonObject()) {
					continue;
				}
				JsonObject object = elementJson.getAsJsonObject();
				Vec3 from = readModelVec3(object.getAsJsonArray("from"));
				Vec3 to = readModelVec3(object.getAsJsonArray("to"));
				ElementRotation rotation = readItemModelRotation(object);
				Map<Direction, ItemModelFace> faces = new HashMap<>();
				JsonObject facesJson = object.getAsJsonObject("faces");
				for (Map.Entry<String, JsonElement> faceEntry : facesJson.entrySet()) {
					Direction direction = Direction.byName(faceEntry.getKey());
					if (direction == null || !faceEntry.getValue().isJsonObject()) {
						continue;
					}
					JsonObject faceJson = faceEntry.getValue().getAsJsonObject();
					faces.put(direction, new ItemModelFace(
							faceJson.get("texture").getAsString(),
							faceJson.has("uv") ? readUv(faceJson.getAsJsonArray("uv")) : null,
							faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0,
							faceJson.has("tintindex") ? faceJson.get("tintindex").getAsInt() : -1
					));
				}
				elements.add(new ItemModelElement(from, to, faces, rotation));
			}
		}
		return new ResolvedItemModel(Map.copyOf(textures), List.copyOf(elements), Map.copyOf(transforms));
	}

	private static Identifier resolveModelTexture(Identifier modelId, Set<String> visited) {
		if (visited.contains(modelId.toString())) {
			return itemTextureFallback(modelId);
		}
		JsonObject model = ASSETS.loadModel(modelId);
		if (model == null) {
			return itemTextureFallback(modelId);
		}

		JsonObject textures = model.has("textures") && model.get("textures").isJsonObject()
				? model.getAsJsonObject("textures")
				: null;
		Identifier direct = firstTexture(textures);
		if (direct != null) {
			return direct;
		}

		if (model.has("parent")) {
			Identifier parent = Identifier.tryParse(model.get("parent").getAsString());
			if (parent != null) {
				List<String> nextVisited = new ArrayList<>(visited);
				nextVisited.add(modelId.toString());
				return resolveModelTexture(parent, Set.copyOf(nextVisited));
			}
		}

		return itemTextureFallback(modelId);
	}

	private static Identifier primaryTexture(ResolvedItemModel model) {
		if (model == null) {
			return null;
		}
		for (String key : List.of("layer0", "particle", "all", "side", "top", "front")) {
			Identifier texture = resolveTextureIdentifier(model.textures(), "#" + key);
			if (texture != null) {
				return texture;
			}
		}
		for (String value : model.textures().values()) {
			Identifier texture = resolveTextureIdentifier(model.textures(), value);
			if (texture != null) {
				return texture;
			}
		}
		for (ItemModelElement element : model.elements()) {
			for (ItemModelFace face : element.faces().values()) {
				Identifier texture = resolveTextureIdentifier(model.textures(), face.texture());
				if (texture != null) {
					return texture;
				}
			}
		}
		return null;
	}

	private static Identifier firstTexture(JsonObject textures) {
		if (textures == null) {
			return null;
		}
		for (String key : List.of("layer0", "particle", "all", "side", "top", "front")) {
			if (!textures.has(key)) {
				continue;
			}
			Identifier texture = parseTextureRef(textures.get(key));
			if (texture != null) {
				return texture;
			}
		}
		for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
			Identifier texture = parseTextureRef(entry.getValue());
			if (texture != null) {
				return texture;
			}
		}
		return null;
	}

	private static Identifier parseTextureRef(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return null;
		}
		String raw = element.getAsString();
		if (raw == null || raw.isBlank() || raw.startsWith("#")) {
			return null;
		}
		return Identifier.tryParse(raw);
	}

	private static Identifier resolveTextureIdentifier(Map<String, String> textures, String ref) {
		String current = ref;
		for (int i = 0; i < 8 && current != null && current.startsWith("#"); i++) {
			current = textures.get(current.substring(1));
		}
		if (current == null || current.isBlank()) {
			return null;
		}
		return current.indexOf(':') >= 0
				? Identifier.tryParse(current)
				: Identifier.fromNamespaceAndPath("minecraft", current);
	}

	private static Vec3 readModelVec3(JsonArray array) {
		return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
	}

	private static double[] readUv(JsonArray array) {
		return new double[]{
				array.get(0).getAsDouble(),
				array.get(1).getAsDouble(),
				array.get(2).getAsDouble(),
				array.get(3).getAsDouble()
		};
	}

	private static ElementRotation readItemModelRotation(JsonObject object) {
		if (!object.has("rotation")) {
			return null;
		}
		JsonObject rotationJson = object.getAsJsonObject("rotation");
		Vec3 origin = readModelVec3(rotationJson.getAsJsonArray("origin"));
		if (rotationJson.has("x") || rotationJson.has("y") || rotationJson.has("z")) {
			float xRot = rotationJson.has("x") ? rotationJson.get("x").getAsFloat() : 0.0F;
			float yRot = rotationJson.has("y") ? rotationJson.get("y").getAsFloat() : 0.0F;
			float zRot = rotationJson.has("z") ? rotationJson.get("z").getAsFloat() : 0.0F;
			Matrix4f transform = new Matrix4f().rotate(new Quaternionf().rotationXYZ(radians(xRot), radians(yRot), radians(zRot)));
			return new ElementRotation(origin, null, 0.0F, false, transform);
		}
		if (!rotationJson.has("axis")) {
			return null;
		}
		Direction.Axis axis = switch (rotationJson.get("axis").getAsString()) {
			case "x" -> Direction.Axis.X;
			case "y" -> Direction.Axis.Y;
			case "z" -> Direction.Axis.Z;
			default -> null;
		};
		if (axis == null) {
			return null;
		}
		float angle = rotationJson.get("angle").getAsFloat();
		boolean rescale = rotationJson.has("rescale") && rotationJson.get("rescale").getAsBoolean();
		return new ElementRotation(origin, axis, angle, rescale, rotationMatrix(axis, angle, rescale));
	}

	private static Map<ItemDisplayTransformContext, ItemModelTransform> parseItemModelTransforms(JsonObject displayJson) {
		Map<ItemDisplayTransformContext, ItemModelTransform> transforms = new HashMap<>();
		readItemModelTransform(displayJson, "thirdperson_righthand", ItemDisplayTransformContext.THIRD_PERSON_RIGHT_HAND, transforms);
		readItemModelTransform(displayJson, "thirdperson_lefthand", ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND, transforms);
		readItemModelTransform(displayJson, "firstperson_righthand", ItemDisplayTransformContext.FIRST_PERSON_RIGHT_HAND, transforms);
		readItemModelTransform(displayJson, "firstperson_lefthand", ItemDisplayTransformContext.FIRST_PERSON_LEFT_HAND, transforms);
		readItemModelTransform(displayJson, "head", ItemDisplayTransformContext.HEAD, transforms);
		readItemModelTransform(displayJson, "gui", ItemDisplayTransformContext.GUI, transforms);
		readItemModelTransform(displayJson, "ground", ItemDisplayTransformContext.GROUND, transforms);
		readItemModelTransform(displayJson, "fixed", ItemDisplayTransformContext.FIXED, transforms);
		readItemModelTransform(displayJson, "on_shelf", ItemDisplayTransformContext.ON_SHELF, transforms);
		return transforms;
	}

	private static void readItemModelTransform(
			JsonObject displayJson,
			String jsonKey,
			ItemDisplayTransformContext context,
			Map<ItemDisplayTransformContext, ItemModelTransform> transforms
	) {
		if (!displayJson.has(jsonKey) || !displayJson.get(jsonKey).isJsonObject()) {
			return;
		}
		JsonObject transformJson = displayJson.getAsJsonObject(jsonKey);
		float[] rotation = readModelTransformVector(transformJson, "rotation", 0.0F, 0.0F, 0.0F);
		float[] translation = readModelTransformVector(transformJson, "translation", 0.0F, 0.0F, 0.0F);
		float[] scale = readModelTransformVector(transformJson, "scale", 1.0F, 1.0F, 1.0F);
		transforms.put(
				context,
				new ItemModelTransform(
						rotation[0],
						rotation[1],
						rotation[2],
						Mth.clamp(translation[0] * PX, -5.0F, 5.0F),
						Mth.clamp(translation[1] * PX, -5.0F, 5.0F),
						Mth.clamp(translation[2] * PX, -5.0F, 5.0F),
						Mth.clamp(scale[0], -4.0F, 4.0F),
						Mth.clamp(scale[1], -4.0F, 4.0F),
						Mth.clamp(scale[2], -4.0F, 4.0F)
				)
		);
	}

	private static float[] readModelTransformVector(JsonObject object, String key, float defaultX, float defaultY, float defaultZ) {
		if (!object.has(key) || !object.get(key).isJsonArray()) {
			return new float[]{defaultX, defaultY, defaultZ};
		}
		JsonArray array = object.getAsJsonArray(key);
		if (array.size() != 3) {
			return new float[]{defaultX, defaultY, defaultZ};
		}
		return new float[]{
				array.get(0).getAsFloat(),
				array.get(1).getAsFloat(),
				array.get(2).getAsFloat()
		};
	}

	private static Identifier itemTextureFallback(Identifier modelId) {
		String path = modelId.getPath();
		if (path.startsWith("item/")) {
			return Identifier.fromNamespaceAndPath(modelId.getNamespace(), path.substring("item/".length()));
		}
		return Identifier.fromNamespaceAndPath(modelId.getNamespace(), path);
	}

	private static BufferedImage normalizeSkinImage(BufferedImage image) {
		if (image.getWidth() == 64 && image.getHeight() == 64) {
			return image;
		}
		BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = normalized.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(image, 0, 0, 64, 64, null);
		graphics.dispose();
		return normalized;
	}

	private static BufferedImage toArgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}
		BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = converted.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return converted;
	}

	private static final class VanillaClientModels {
		private static final Path LOOM_MERGED_ROOT = Path.of(
				System.getProperty("user.home"),
				".gradle",
				"caches",
				"fabric-loom",
				"minecraftMaven",
				"net",
				"minecraft",
				"minecraft-merged"
		);
		private static final Map<ModelCacheKey, ClientModelAdapter> ADAPTER_CACHE = new ConcurrentHashMap<>();
		private static volatile RuntimeBridge runtimeBridge;
		private static volatile boolean runtimeInitialized;

		private VanillaClientModels() {
		}

		static boolean isAvailable() {
			return runtimeBridge() != null;
		}

		static boolean hasModelClass(String modelClassName) {
			RuntimeBridge bridge = runtimeBridge();
			if (bridge == null) {
				return false;
			}
			try {
				Class.forName(modelClassName, false, bridge.classLoader);
				return true;
			} catch (ClassNotFoundException exception) {
				return false;
			}
		}

		static void render(RenderContext context, ClientModelSnapshot snapshot) {
			if (runtimeBridge() == null || snapshot.layers() == null || snapshot.layers().length == 0) {
				return;
			}

			renderWithRoot(context, snapshot, rootTransform(snapshot));
		}

		static void renderWithRoot(RenderContext context, ClientModelSnapshot snapshot, Matrix4f root) {
			RuntimeBridge bridge = runtimeBridge();
			if (bridge == null || snapshot.layers() == null || snapshot.layers().length == 0 || root == null) {
				return;
			}

			boolean baby = Boolean.TRUE.equals(snapshot.stateFields().get("isBaby"))
					|| Boolean.TRUE.equals(snapshot.stateFields().get("baby"));
			for (ClientLayerSnapshot layer : snapshot.layers()) {
				if (layer == null) {
					continue;
				}
				int material = layer.playerSkin() != null
						? context.materialResolver().materialForPlayerSkin(layer.playerSkin())
						: layer.dynamicImage() != null
						? context.materialResolver().materialForImage(layer.dynamicImageKey(), layer.dynamicImage())
						: context.materialResolver().materialForTexture(layer.texture());
				renderLayer(bridge, context, root, snapshot.stateFields(), baby, layer, material);
			}
			renderHeadEquipment(bridge, context, snapshot, root, baby);
			renderHeldItems(bridge, context, snapshot, root, baby);
		}

		private static void renderHeadEquipment(
				RuntimeBridge bridge,
				RenderContext context,
				ClientModelSnapshot snapshot,
				Matrix4f root,
				boolean baby
		) {
			if (snapshot.transformKind() != ClientModelTransformKind.LIVING || snapshot.layers() == null || snapshot.layers().length == 0) {
				return;
			}
			ItemStack headStack = stateItemStack(snapshot.stateFields(), "headEquipment");
			if (!shouldRenderHeadEquipmentItem(headStack)) {
				return;
			}

			ClientLayerSnapshot baseLayer = null;
			for (ClientLayerSnapshot layer : snapshot.layers()) {
				if (layer != null && !layer.modelClassName().contains("Armor")) {
					baseLayer = layer;
					break;
				}
			}
			if (baseLayer == null) {
				return;
			}

			try {
				ClientModelAdapter adapter = ADAPTER_CACHE.computeIfAbsent(
						modelCacheKey(baseLayer, baby),
						key -> createAdapter(
								bridge,
								key.modelClassName(),
								key.baby(),
								key.textureWidth(),
								key.textureHeight(),
								key.layerFactoryMethodName(),
								key.cubeDeformation(),
								key.secondaryCubeDeformation(),
								key.modelFlag()
						)
				);
				if (adapter == null) {
					return;
				}
				synchronized (adapter) {
					Object state = adapter.newState(snapshot.stateFields());
					adapter.resetPose();
					adapter.setupAnim(state);
					Matrix4f headTransform = findPartTransform(bridge, adapter.rootPart, root, "head");
					if (headTransform != null) {
						renderExactHeadItem(context, snapshot.stateFields(), headStack, headTransform);
					}
				}
			} catch (Exception ignored) {
			}
		}

		private static void renderHeldItems(
				RuntimeBridge bridge,
				RenderContext context,
				ClientModelSnapshot snapshot,
				Matrix4f root,
				boolean baby
		) {
			if (snapshot.transformKind() != ClientModelTransformKind.LIVING || snapshot.layers() == null || snapshot.layers().length == 0) {
				return;
			}
			ItemStack rightHandStack = stateItemStack(snapshot.stateFields(), "rightHandItemStack");
			ItemStack leftHandStack = stateItemStack(snapshot.stateFields(), "leftHandItemStack");
			if ((rightHandStack == null || rightHandStack.isEmpty()) && (leftHandStack == null || leftHandStack.isEmpty())) {
				return;
			}

			ClientLayerSnapshot baseLayer = null;
			for (ClientLayerSnapshot layer : snapshot.layers()) {
				if (layer != null && !layer.modelClassName().contains("Armor")) {
					baseLayer = layer;
					break;
				}
			}
			if (baseLayer == null) {
				return;
			}

			try {
				ClientModelAdapter adapter = ADAPTER_CACHE.computeIfAbsent(
						modelCacheKey(baseLayer, baby),
						key -> createAdapter(
								bridge,
								key.modelClassName(),
								key.baby(),
								key.textureWidth(),
								key.textureHeight(),
								key.layerFactoryMethodName(),
								key.cubeDeformation(),
								key.secondaryCubeDeformation(),
								key.modelFlag()
						)
				);
				if (adapter == null) {
					return;
				}
				synchronized (adapter) {
					Object state = adapter.newState(snapshot.stateFields());
					adapter.resetPose();
					adapter.setupAnim(state);
					Matrix4f rightHandTransform = findPartTransform(bridge, adapter.rootPart, root, "right_arm");
					Matrix4f leftHandTransform = findPartTransform(bridge, adapter.rootPart, root, "left_arm");
					renderExactHeldItem(context, snapshot.stateFields(), rightHandStack, rightHandTransform, ItemDisplayTransformContext.THIRD_PERSON_RIGHT_HAND);
					renderExactHeldItem(context, snapshot.stateFields(), leftHandStack, leftHandTransform, ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND);
				}
			} catch (Exception ignored) {
			}
		}

		private static void renderExactHeldItem(
				RenderContext context,
				Map<String, Object> stateFields,
				ItemStack stack,
				Matrix4f handTransform,
				ItemDisplayTransformContext transformContext
		) {
			if (stack == null || stack.isEmpty() || handTransform == null) {
				return;
			}
			boolean leftHand = transformContext == ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND;
			boolean usingItem = stateBoolean(stateFields, leftHand ? "leftHandUsingItem" : "rightHandUsingItem", false);
			float useTicks = stateFloat(stateFields, leftHand ? "leftHandUseTicks" : "rightHandUseTicks", 0.0F);
			boolean fishingRodCast = stateBoolean(stateFields, leftHand ? "leftFishingRodCast" : "rightFishingRodCast", false);
			String contextDimensionId = stateString(stateFields, "contextDimensionId");
			long gameTime = stateLong(stateFields, "gameTime", 0L);
			long dayTime = stateLong(stateFields, "dayTime", 0L);
			ItemVisual visual = resolveItemVisual(
					stack,
					new ItemDefinitionRenderState(transformContext, usingItem, useTicks, fishingRodCast, contextDimensionId, gameTime, dayTime)
			);
			if (visual == null) {
				return;
			}
			Matrix4f itemRoot = new Matrix4f(handTransform)
					.rotateX(radians(-90.0F))
					.rotateY(radians(180.0F))
					.translate(leftHand ? -1.0F / 16.0F : 1.0F / 16.0F, 0.125F, -0.625F);
			renderItemVisual(context, itemRoot, visual, transformContext);
		}

		private static void renderExactHeadItem(
				RenderContext context,
				Map<String, Object> stateFields,
				ItemStack stack,
				Matrix4f headTransform
		) {
			if (!shouldRenderHeadEquipmentItem(stack) || headTransform == null) {
				return;
			}
			String contextDimensionId = stateString(stateFields, "contextDimensionId");
			long gameTime = stateLong(stateFields, "gameTime", 0L);
			long dayTime = stateLong(stateFields, "dayTime", 0L);
			ItemVisual visual = resolveItemVisual(
					stack,
					new ItemDefinitionRenderState(ItemDisplayTransformContext.HEAD, false, 0.0F, false, contextDimensionId, gameTime, dayTime)
			);
			if (visual == null) {
				return;
			}
			renderItemVisual(context, exactHeadItemRoot(headTransform), visual, ItemDisplayTransformContext.HEAD);
		}

		@SuppressWarnings("unchecked")
		private static Matrix4f findPartTransform(RuntimeBridge bridge, Object part, Matrix4f parentTransform, String targetName) throws ReflectiveOperationException {
			Map<String, Object> children = (Map<String, Object>) bridge.partChildrenField.get(part);
			if (children == null || children.isEmpty()) {
				return null;
			}
			for (Map.Entry<String, Object> entry : children.entrySet()) {
				Matrix4f childTransform = childPartTransform(bridge, parentTransform, entry.getValue());
				if (targetName.equals(entry.getKey())) {
					return childTransform;
				}
				Matrix4f nested = findPartTransform(bridge, entry.getValue(), childTransform, targetName);
				if (nested != null) {
					return nested;
				}
			}
			return null;
		}

		private static Matrix4f childPartTransform(RuntimeBridge bridge, Matrix4f parentTransform, Object part) throws ReflectiveOperationException {
			Matrix4f transform = new Matrix4f(parentTransform)
					.translate(
							bridge.partXField.getFloat(part) * PX,
							bridge.partYField.getFloat(part) * PX,
							bridge.partZField.getFloat(part) * PX
					);
			float xRot = bridge.partXRotField.getFloat(part);
			float yRot = bridge.partYRotField.getFloat(part);
			float zRot = bridge.partZRotField.getFloat(part);
			if (xRot != 0.0F || yRot != 0.0F || zRot != 0.0F) {
				transform.rotateZ(zRot).rotateY(yRot).rotateX(xRot);
			}
			float xScale = bridge.partXScaleField.getFloat(part);
			float yScale = bridge.partYScaleField.getFloat(part);
			float zScale = bridge.partZScaleField.getFloat(part);
			if (xScale != 1.0F || yScale != 1.0F || zScale != 1.0F) {
				transform.scale(xScale, yScale, zScale);
			}
			return transform;
		}

		static void renderHumanoidArmor(
				RenderContext context,
				ClientModelSnapshot snapshot,
				float headYaw,
				float headPitch,
				float rightArmPitch,
				float leftArmPitch,
				float rightArmYaw,
				float leftArmYaw,
				float rightArmRoll,
				float leftArmRoll,
				float rightLegPitch,
				float leftLegPitch,
				float rightLegYaw,
				float leftLegYaw,
				float rightLegRoll,
				float leftLegRoll
		) {
			RuntimeBridge bridge = runtimeBridge();
			if (bridge == null || snapshot.layers() == null || snapshot.layers().length == 0) {
				return;
			}

			Matrix4f root = rootTransform(snapshot);
			boolean baby = Boolean.TRUE.equals(snapshot.stateFields().get("isBaby"))
					|| Boolean.TRUE.equals(snapshot.stateFields().get("baby"));
			for (ClientLayerSnapshot layer : snapshot.layers()) {
				if (layer == null) {
					continue;
				}
				int material = layer.playerSkin() != null
						? context.materialResolver().materialForPlayerSkin(layer.playerSkin())
						: layer.dynamicImage() != null
						? context.materialResolver().materialForImage(layer.dynamicImageKey(), layer.dynamicImage())
						: context.materialResolver().materialForTexture(layer.texture());
				renderHumanoidArmorLayer(
						bridge,
						context,
						root,
						snapshot.stateFields(),
						baby,
						layer,
						material,
						headYaw,
						headPitch,
						rightArmPitch,
						leftArmPitch,
						rightArmYaw,
						leftArmYaw,
						rightArmRoll,
						leftArmRoll,
						rightLegPitch,
						leftLegPitch,
						rightLegYaw,
						leftLegYaw,
						rightLegRoll,
						leftLegRoll
				);
			}
		}

		private static void renderLayer(
				RuntimeBridge bridge,
				RenderContext context,
				Matrix4f root,
				Map<String, Object> stateFields,
				boolean baby,
				ClientLayerSnapshot layer,
				int material
		) {
			try {
				ClientModelAdapter adapter = ADAPTER_CACHE.computeIfAbsent(
						modelCacheKey(layer, baby),
						key -> createAdapter(
								bridge,
								key.modelClassName(),
								key.baby(),
								key.textureWidth(),
								key.textureHeight(),
								key.layerFactoryMethodName(),
								key.cubeDeformation(),
								key.secondaryCubeDeformation(),
								key.modelFlag()
						)
				);
				if (adapter == null) {
					return;
				}
				synchronized (adapter) {
					Object state = adapter.newState(stateFields);
					adapter.resetPose();
					adapter.setupAnim(state);
					int tintRgb = layerTintRgb(stateFields, layer);
					float red = ((tintRgb >> 16) & 0xFF) / 255.0F;
					float green = ((tintRgb >> 8) & 0xFF) / 255.0F;
					float blue = (tintRgb & 0xFF) / 255.0F;
					Matrix4f layerRoot = Mth.equal(layer.renderScale(), 1.0F) ? root : new Matrix4f(root).scale(layer.renderScale());
					renderPartTree(bridge, context, adapter.rootPart, layerRoot, material, red, green, blue, layer.emissive());
				}
			} catch (Exception ignored) {
			}
		}

		private static int layerTintRgb(Map<String, Object> stateFields, ClientLayerSnapshot layer) {
			if (layer.texture() != null
					&& (SHEEP_WOOL_TEXTURE.equals(layer.texture()) || SHEEP_WOOL_UNDERCOAT_TEXTURE.equals(layer.texture()))) {
				Object woolColor = stateFields.get("woolColor");
				if (woolColor instanceof net.minecraft.world.item.DyeColor dyeColor) {
					return dyeColor.getTextureDiffuseColor();
				}
			}
			return layer.tintRgb();
		}

		private static void renderHumanoidArmorLayer(
				RuntimeBridge bridge,
				RenderContext context,
				Matrix4f root,
				Map<String, Object> stateFields,
				boolean baby,
				ClientLayerSnapshot layer,
				int material,
				float headYaw,
				float headPitch,
				float rightArmPitch,
				float leftArmPitch,
				float rightArmYaw,
				float leftArmYaw,
				float rightArmRoll,
				float leftArmRoll,
				float rightLegPitch,
				float leftLegPitch,
				float rightLegYaw,
				float leftLegYaw,
				float rightLegRoll,
				float leftLegRoll
		) {
			try {
				ClientModelAdapter adapter = ADAPTER_CACHE.computeIfAbsent(
						modelCacheKey(layer, baby),
						key -> createAdapter(
								bridge,
								key.modelClassName(),
								key.baby(),
								key.textureWidth(),
								key.textureHeight(),
								key.layerFactoryMethodName(),
								key.cubeDeformation(),
								key.secondaryCubeDeformation(),
								key.modelFlag()
						)
				);
				if (adapter == null) {
					return;
				}
				synchronized (adapter) {
					Object state = adapter.newState(stateFields);
					adapter.resetPose();
					adapter.setupAnim(state);
					applyHumanoidArmorPose(
							bridge,
							adapter.rootPart,
							headYaw,
							headPitch,
							rightArmPitch,
							leftArmPitch,
							rightArmYaw,
							leftArmYaw,
							rightArmRoll,
							leftArmRoll,
							rightLegPitch,
							leftLegPitch,
							rightLegYaw,
							leftLegYaw,
							rightLegRoll,
							leftLegRoll
					);
					float red = ((layer.tintRgb() >> 16) & 0xFF) / 255.0F;
					float green = ((layer.tintRgb() >> 8) & 0xFF) / 255.0F;
					float blue = (layer.tintRgb() & 0xFF) / 255.0F;
					Matrix4f layerRoot = Mth.equal(layer.renderScale(), 1.0F) ? root : new Matrix4f(root).scale(layer.renderScale());
					renderPartTree(bridge, context, adapter.rootPart, layerRoot, material, red, green, blue, layer.emissive());
				}
			} catch (Exception ignored) {
			}
		}

		@SuppressWarnings("unchecked")
		private static void applyHumanoidArmorPose(
				RuntimeBridge bridge,
				Object rootPart,
				float headYaw,
				float headPitch,
				float rightArmPitch,
				float leftArmPitch,
				float rightArmYaw,
				float leftArmYaw,
				float rightArmRoll,
				float leftArmRoll,
				float rightLegPitch,
				float leftLegPitch,
				float rightLegYaw,
				float leftLegYaw,
				float rightLegRoll,
				float leftLegRoll
		) throws IllegalAccessException {
			Map<String, Object> children = (Map<String, Object>) bridge.partChildrenField.get(rootPart);
			if (children == null || children.isEmpty()) {
				return;
			}
			setPartPose(bridge, children.get("head"), headPitch, headYaw, 0.0F);
			setPartPose(bridge, children.get("body"), 0.0F, 0.0F, 0.0F);
			setPartPose(bridge, children.get("right_arm"), rightArmPitch, rightArmYaw, rightArmRoll);
			setPartPose(bridge, children.get("left_arm"), leftArmPitch, leftArmYaw, leftArmRoll);
			setPartPose(bridge, children.get("right_leg"), rightLegPitch, rightLegYaw, rightLegRoll);
			setPartPose(bridge, children.get("left_leg"), leftLegPitch, leftLegYaw, leftLegRoll);
		}

		private static void setPartPose(RuntimeBridge bridge, Object part, float xRot, float yRot, float zRot) throws IllegalAccessException {
			if (part == null) {
				return;
			}
			bridge.partVisibleField.setBoolean(part, true);
			bridge.partSkipDrawField.setBoolean(part, false);
			bridge.partXRotField.setFloat(part, xRot);
			bridge.partYRotField.setFloat(part, yRot);
			bridge.partZRotField.setFloat(part, zRot);
		}

		private static ModelCacheKey modelCacheKey(ClientLayerSnapshot layer, boolean baby) {
			BufferedImage texture = layer.dynamicImage() != null ? layer.dynamicImage() : ASSETS.loadTexture(layer.texture());
			int width = texture == null ? 64 : texture.getWidth();
			int height = texture == null ? 64 : texture.getHeight();
			return new ModelCacheKey(layer.modelClassName(), baby, width, height, layer.layerFactoryMethodName(), layer.cubeDeformation(), layer.secondaryCubeDeformation(), layer.modelFlag());
		}

		private static Matrix4f rootTransform(ClientModelSnapshot snapshot) {
			return switch (snapshot.transformKind()) {
				case BOAT -> boatRootTransform(snapshot);
				case BLOCK_ENTITY -> blockEntityRootTransform(snapshot);
				case LIVING -> new Matrix4f()
						.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
						.scale(snapshot.rootScale())
						.rotateY(radians(snapshot.rootYawOffsetDegrees() - snapshot.rootYaw()))
						.scale(-1.0F, -1.0F, 1.0F)
						.translate(0.0F, -1.501F, 0.0F);
			};
		}

		private static Matrix4f blockEntityRootTransform(ClientModelSnapshot snapshot) {
			Map<String, Object> stateFields = snapshot.stateFields();
			Matrix4f root = new Matrix4f()
					.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
					.translate(
							stateFloat(stateFields, "rootTranslateX", 0.0F),
							stateFloat(stateFields, "rootTranslateY", 0.0F),
							stateFloat(stateFields, "rootTranslateZ", 0.0F)
					)
					.rotateY(radians(stateFloat(stateFields, "rootRotateY", 0.0F)))
					.rotateX(radians(stateFloat(stateFields, "rootRotateX", 0.0F)))
					.rotateZ(radians(stateFloat(stateFields, "rootRotateZ", 0.0F)))
					.translate(
							stateFloat(stateFields, "rootMidTranslateX", 0.0F),
							stateFloat(stateFields, "rootMidTranslateY", 0.0F),
							stateFloat(stateFields, "rootMidTranslateZ", 0.0F)
					)
					.rotateY(radians(stateFloat(stateFields, "rootRotate2Y", 0.0F)))
					.rotateX(radians(stateFloat(stateFields, "rootRotate2X", 0.0F)))
					.rotateZ(radians(stateFloat(stateFields, "rootRotate2Z", 0.0F)))
					.translate(
							stateFloat(stateFields, "rootPostTranslateX", 0.0F),
							stateFloat(stateFields, "rootPostTranslateY", 0.0F),
							stateFloat(stateFields, "rootPostTranslateZ", 0.0F)
					)
					.scale(
							snapshot.rootScale() * stateFloat(stateFields, "rootScaleX", 1.0F),
							snapshot.rootScale() * stateFloat(stateFields, "rootScaleY", 1.0F),
							snapshot.rootScale() * stateFloat(stateFields, "rootScaleZ", 1.0F)
					);
			return root;
		}

		private static Matrix4f boatRootTransform(ClientModelSnapshot snapshot) {
			float yRot = stateFloat(snapshot.stateFields(), "yRot", snapshot.rootYaw());
			float hurtTime = Math.max(stateFloat(snapshot.stateFields(), "hurtTime", 0.0F), 0.0F);
			float damageTime = Math.max(stateFloat(snapshot.stateFields(), "damageTime", 0.0F), 0.0F);
			int hurtDir = stateInt(snapshot.stateFields(), "hurtDir", 1);
			float bubbleAngle = stateFloat(snapshot.stateFields(), "bubbleAngle", 0.0F);
			boolean underWater = stateBoolean(snapshot.stateFields(), "isUnderWater", false);

			Matrix4f root = new Matrix4f()
					.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
					.translate(0.0F, 0.375F, 0.0F)
					.rotateY(radians(180.0F - yRot));
			if (hurtTime > 0.0F) {
				root.rotateX(radians(Mth.sin(hurtTime) * hurtTime * damageTime / 10.0F * hurtDir));
			}
			if (!underWater && !Mth.equal(bubbleAngle, 0.0F)) {
				root.rotate(new Quaternionf().setAngleAxis(radians(bubbleAngle), 1.0F, 0.0F, 1.0F));
			}
			root.scale(-snapshot.rootScale(), -snapshot.rootScale(), snapshot.rootScale());
			root.rotateY(radians(90.0F));
			return root;
		}

		private static float stateFloat(Map<String, Object> stateFields, String key, float fallback) {
			Object value = stateFields.get(key);
			return value instanceof Number number ? number.floatValue() : fallback;
		}

		private static int stateInt(Map<String, Object> stateFields, String key, int fallback) {
			Object value = stateFields.get(key);
			return value instanceof Number number ? number.intValue() : fallback;
		}

		private static long stateLong(Map<String, Object> stateFields, String key, long fallback) {
			Object value = stateFields.get(key);
			return value instanceof Number number ? number.longValue() : fallback;
		}

		private static boolean stateBoolean(Map<String, Object> stateFields, String key, boolean fallback) {
			Object value = stateFields.get(key);
			return value instanceof Boolean bool ? bool : fallback;
		}

		private static String stateString(Map<String, Object> stateFields, String key) {
			Object value = stateFields.get(key);
			return value instanceof String string ? string : null;
		}

		private static ItemStack stateItemStack(Map<String, Object> stateFields, String key) {
			Object value = stateFields.get(key);
			return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
		}

		private static void renderPartTree(
				RuntimeBridge bridge,
				RenderContext context,
				Object part,
				Matrix4f parentTransform,
				int material,
				float red,
				float green,
				float blue,
				boolean emissive
		) throws ReflectiveOperationException {
			if (!bridge.partVisibleField.getBoolean(part)) {
				return;
			}

			@SuppressWarnings("unchecked")
			List<Object> cubes = (List<Object>) bridge.partCubesField.get(part);
			@SuppressWarnings("unchecked")
			Map<String, Object> children = (Map<String, Object>) bridge.partChildrenField.get(part);
			if ((cubes == null || cubes.isEmpty()) && (children == null || children.isEmpty())) {
				return;
			}

			Matrix4f transform = new Matrix4f(parentTransform)
					.translate(
							bridge.partXField.getFloat(part) * PX,
							bridge.partYField.getFloat(part) * PX,
							bridge.partZField.getFloat(part) * PX
					);
			float xRot = bridge.partXRotField.getFloat(part);
			float yRot = bridge.partYRotField.getFloat(part);
			float zRot = bridge.partZRotField.getFloat(part);
			if (xRot != 0.0F || yRot != 0.0F || zRot != 0.0F) {
				transform.rotateZ(zRot).rotateY(yRot).rotateX(xRot);
			}
			float xScale = bridge.partXScaleField.getFloat(part);
			float yScale = bridge.partYScaleField.getFloat(part);
			float zScale = bridge.partZScaleField.getFloat(part);
			if (xScale != 1.0F || yScale != 1.0F || zScale != 1.0F) {
				transform.scale(xScale, yScale, zScale);
			}

			if (!bridge.partSkipDrawField.getBoolean(part) && cubes != null) {
				for (Object cube : cubes) {
					renderCube(bridge, context, cube, transform, material, red, green, blue, emissive);
				}
			}

			if (children != null) {
				for (Object child : children.values()) {
					renderPartTree(bridge, context, child, transform, material, red, green, blue, emissive);
				}
			}
		}

		private static void renderCube(
				RuntimeBridge bridge,
				RenderContext context,
				Object cube,
				Matrix4f transform,
				int material,
				float red,
				float green,
				float blue,
				boolean emissive
		) throws ReflectiveOperationException {
			Object[] polygons = (Object[]) bridge.cubePolygonsField.get(cube);
			if (polygons == null) {
				return;
			}
			for (Object polygon : polygons) {
				Object[] vertices = (Object[]) bridge.polygonVerticesMethod.invoke(polygon);
				if (vertices == null || vertices.length < 4) {
					continue;
				}
				Vector3f a = vertexPosition(bridge, transform, vertices[0]);
				Vector3f b = vertexPosition(bridge, transform, vertices[1]);
				Vector3f c = vertexPosition(bridge, transform, vertices[2]);
				Vector3f d = vertexPosition(bridge, transform, vertices[3]);
				LightSample light = emissive
						? new LightSample(15, 15)
						: context.lightAt(
						(a.x + b.x + c.x + d.x) * 0.25F,
						(a.y + b.y + c.y + d.y) * 0.25F,
						(a.z + b.z + c.z + d.z) * 0.25F
				);
				addQuadExact(
						context,
						a,
						b,
						c,
						d,
						bridge.vertexUField.getFloat(vertices[0]),
						bridge.vertexVField.getFloat(vertices[0]),
						bridge.vertexUField.getFloat(vertices[1]),
						bridge.vertexVField.getFloat(vertices[1]),
						bridge.vertexUField.getFloat(vertices[2]),
						bridge.vertexVField.getFloat(vertices[2]),
						bridge.vertexUField.getFloat(vertices[3]),
						bridge.vertexVField.getFloat(vertices[3]),
						material,
						light.sky(),
						light.block(),
						red,
						green,
						blue
				);
			}
		}

		private static Vector3f vertexPosition(RuntimeBridge bridge, Matrix4f transform, Object vertex) throws IllegalAccessException {
			return transformPosition(
					transform,
					bridge.vertexXField.getFloat(vertex) * PX,
					bridge.vertexYField.getFloat(vertex) * PX,
					bridge.vertexZField.getFloat(vertex) * PX
			);
		}

		private static ClientModelAdapter createAdapter(
				RuntimeBridge bridge,
				String modelClassName,
				boolean baby,
				int textureWidth,
				int textureHeight,
				String layerFactoryMethodName,
				float cubeDeformation,
				float secondaryCubeDeformation,
				boolean modelFlag
		) {
			try {
				Class<?> modelClass = Class.forName(modelClassName, true, bridge.classLoader);
				Object layerDefinition = createLayerDefinition(bridge, modelClass, textureWidth, textureHeight, layerFactoryMethodName, cubeDeformation, secondaryCubeDeformation, modelFlag);
				if (layerDefinition == null) {
					return null;
				}
				if (baby) {
					Field transformerField = findField(modelClass, "BABY_TRANSFORMER");
					if (transformerField != null) {
						Object transformer = transformerField.get(null);
						if (transformer != null) {
							layerDefinition = bridge.layerApplyMethod.invoke(layerDefinition, transformer);
						}
					}
				}
				Object rootPart = bridge.layerBakeRootMethod.invoke(layerDefinition);
				Object model = instantiateModel(modelClass, bridge, rootPart, modelFlag);
				Method setupAnim = findSetupAnimMethod(modelClass);
				if (setupAnim == null) {
					return null;
				}
				return new ClientModelAdapter(
						model,
						rootPart,
						bridge.modelResetPoseMethod,
						setupAnim,
						setupAnim.getParameterTypes()[0]
				);
			} catch (Exception exception) {
				return null;
			}
		}

		private static Object instantiateModel(Class<?> modelClass, RuntimeBridge bridge, Object rootPart, boolean modelFlag) throws ReflectiveOperationException {
			try {
				Constructor<?> constructor = modelClass.getConstructor(bridge.modelPartClass, boolean.class);
				return constructor.newInstance(rootPart, modelFlag);
			} catch (NoSuchMethodException ignored) {
				try {
					Constructor<?> constructor = modelClass.getConstructor(bridge.modelPartClass, java.util.function.Function.class);
					return constructor.newInstance(rootPart, (java.util.function.Function<Identifier, Object>) identifier -> null);
				} catch (NoSuchMethodException ignoredAgain) {
					Constructor<?> constructor = modelClass.getConstructor(bridge.modelPartClass);
					return constructor.newInstance(rootPart);
				}
			}
		}

		private static Object createLayerDefinition(
				RuntimeBridge bridge,
				Class<?> modelClass,
				int textureWidth,
				int textureHeight,
				String layerFactoryMethodName,
				float cubeDeformation,
				float secondaryCubeDeformation,
				boolean modelFlag
		) throws ReflectiveOperationException {
			FactoryDescriptor factoryDescriptor = FactoryDescriptor.parse(layerFactoryMethodName);
			Class<?> ownerClass = factoryDescriptor.ownerClassName() == null
					? modelClass
					: Class.forName(factoryDescriptor.ownerClassName(), true, bridge.classLoader);
			List<Method> candidates = new ArrayList<>();
			for (Class<?> cursor = ownerClass; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
				for (Method method : cursor.getDeclaredMethods()) {
					if ((method.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) {
						continue;
					}
					if (!bridge.layerDefinitionClass.equals(method.getReturnType())
							&& !bridge.meshDefinitionClass.equals(method.getReturnType())
							&& !bridge.armorModelSetClass.equals(method.getReturnType())) {
						continue;
					}
					if (factoryDescriptor.methodName() != null && !factoryDescriptor.methodName().equals(method.getName())) {
						continue;
					}
					if (isSupportedLayerFactory(bridge, method, factoryDescriptor.argument())) {
						candidates.add(method);
					}
				}
			}
			candidates.sort(Comparator.comparingInt(VanillaClientModels::layerMethodPriority));
			for (Method method : candidates) {
				method.setAccessible(true);
				Object result = invokeLayerFactory(bridge, method, cubeDeformation, secondaryCubeDeformation, modelFlag, factoryDescriptor.argument());
				if (result == null) {
					continue;
				}
				if (bridge.armorModelSetClass.isInstance(result) && factoryDescriptor.argument() != null) {
					@SuppressWarnings("rawtypes")
					Object slot = Enum.valueOf((Class<? extends Enum>) bridge.equipmentSlotClass.asSubclass(Enum.class), factoryDescriptor.argument());
					result = bridge.armorModelSetGetMethod.invoke(result, slot);
				}
				if (bridge.layerDefinitionClass.isInstance(result)) {
					return result;
				}
				if (bridge.meshDefinitionClass.isInstance(result)) {
					return bridge.layerCreateMethod.invoke(null, result, textureWidth, textureHeight);
				}
			}
			return null;
		}

		private static boolean isSupportedLayerFactory(RuntimeBridge bridge, Method method, String explicitArgument) {
			Class<?>[] parameterTypes = method.getParameterTypes();
			return switch (parameterTypes.length) {
				case 0 -> true;
				case 1 -> bridge.cubeDeformationClass.equals(parameterTypes[0])
						|| parameterTypes[0] == boolean.class
						|| (parameterTypes[0].isEnum() && explicitArgument != null);
				case 2 -> (bridge.cubeDeformationClass.equals(parameterTypes[0]) && parameterTypes[1] == boolean.class)
						|| (parameterTypes[0] == boolean.class && bridge.cubeDeformationClass.equals(parameterTypes[1]))
						|| (bridge.cubeDeformationClass.equals(parameterTypes[0]) && bridge.cubeDeformationClass.equals(parameterTypes[1]));
				default -> false;
			};
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private static Object invokeLayerFactory(RuntimeBridge bridge, Method method, float cubeDeformation, float secondaryCubeDeformation, boolean modelFlag, String explicitArgument) throws ReflectiveOperationException {
			Class<?>[] parameterTypes = method.getParameterTypes();
			return switch (parameterTypes.length) {
				case 0 -> method.invoke(null);
				case 1 -> {
					if (bridge.cubeDeformationClass.equals(parameterTypes[0])) {
						yield method.invoke(null, cubeDeformationObject(bridge, cubeDeformation));
					}
					if (parameterTypes[0].isEnum() && explicitArgument != null) {
						yield method.invoke(null, Enum.valueOf((Class<? extends Enum>) parameterTypes[0].asSubclass(Enum.class), explicitArgument));
					}
					if (explicitArgument != null && parameterTypes[0] == boolean.class) {
						yield method.invoke(null, Boolean.parseBoolean(explicitArgument));
					}
					yield method.invoke(null, modelFlag);
				}
				case 2 -> {
					Object first;
					Object second;
					if (bridge.cubeDeformationClass.equals(parameterTypes[0]) && bridge.cubeDeformationClass.equals(parameterTypes[1])) {
						first = cubeDeformationObject(bridge, cubeDeformation);
						second = cubeDeformationObject(bridge, secondaryCubeDeformation);
					} else {
						first = bridge.cubeDeformationClass.equals(parameterTypes[0])
								? cubeDeformationObject(bridge, cubeDeformation)
								: modelFlag;
						second = bridge.cubeDeformationClass.equals(parameterTypes[1])
								? cubeDeformationObject(bridge, cubeDeformation)
								: modelFlag;
					}
					yield method.invoke(null, first, second);
				}
				default -> null;
			};
		}

		private static Object cubeDeformationObject(RuntimeBridge bridge, float cubeDeformation) throws ReflectiveOperationException {
			if (Mth.equal(cubeDeformation, 0.0F)) {
				return bridge.cubeDeformationNone;
			}
			return bridge.cubeDeformationConstructor.newInstance(cubeDeformation);
		}

		private static int layerMethodPriority(Method method) {
			return switch (method.getName()) {
				case "createBodyLayer" -> 0;
				case "createFurLayer" -> 1;
				case "createInnerBodyLayer" -> 2;
				case "createOuterBodyLayer" -> 3;
				case "createBodyMesh" -> 4;
				case "createBaseChickenModel" -> 5;
				case "createBasePigModel" -> 6;
				case "createBaseCowModel" -> 7;
				case "createArmorMeshSet" -> 8;
				case "createArmorLayerSet" -> 9;
				case "createBoatModel" -> 10;
				case "createChestBoatModel" -> 11;
				case "createRaftModel" -> 12;
				case "createChestRaftModel" -> 13;
				case "createMesh" -> 14;
				default -> 10;
			};
		}

		private static Method findSetupAnimMethod(Class<?> modelClass) {
			Method best = null;
			int bestDepth = -1;
			for (Method method : modelClass.getMethods()) {
				if (!method.getName().equals("setupAnim") || method.getParameterCount() != 1 || method.isBridge()) {
					continue;
				}
				Class<?> parameterType = method.getParameterTypes()[0];
				if (parameterType == Object.class) {
					continue;
				}
				int depth = inheritanceDepth(parameterType);
				if (depth > bestDepth) {
					best = method;
					bestDepth = depth;
				}
			}
			return best;
		}

		private static int inheritanceDepth(Class<?> type) {
			int depth = 0;
			Class<?> cursor = type;
			while (cursor != null) {
				depth++;
				cursor = cursor.getSuperclass();
			}
			return depth;
		}

		private static Field findField(Class<?> type, String name) {
			for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
				try {
					Field field = cursor.getDeclaredField(name);
					field.setAccessible(true);
					return field;
				} catch (NoSuchFieldException ignored) {
				}
			}
			return null;
		}

		private record FactoryDescriptor(
				String ownerClassName,
				String methodName,
				String argument
		) {
			private static FactoryDescriptor parse(String descriptor) {
				if (descriptor == null || descriptor.isBlank()) {
					return new FactoryDescriptor(null, null, null);
				}
				String owner = null;
				String method = descriptor;
				String argument = null;
				int hashIndex = descriptor.indexOf('#');
				if (hashIndex >= 0) {
					owner = descriptor.substring(0, hashIndex);
					method = descriptor.substring(hashIndex + 1);
				}
				int colonIndex = method.indexOf(':');
				if (colonIndex >= 0) {
					argument = method.substring(colonIndex + 1);
					method = method.substring(0, colonIndex);
				}
				return new FactoryDescriptor(owner, method, argument);
			}
		}

		private static RuntimeBridge runtimeBridge() {
			if (runtimeInitialized) {
				return runtimeBridge;
			}
			synchronized (VanillaClientModels.class) {
				if (runtimeInitialized) {
					return runtimeBridge;
				}
				runtimeBridge = createRuntimeBridge();
				runtimeInitialized = true;
				return runtimeBridge;
			}
		}

		private static RuntimeBridge createRuntimeBridge() {
			Path mergedJar = findMergedJar();
			if (mergedJar == null) {
				return null;
			}
			try {
				URLClassLoader classLoader = new URLClassLoader(new URL[]{mergedJar.toUri().toURL()}, CameraEntityRenderer.class.getClassLoader());
				Class<?> modelClass = Class.forName("net.minecraft.client.model.Model", true, classLoader);
				Class<?> modelPartClass = Class.forName("net.minecraft.client.model.geom.ModelPart", true, classLoader);
				Class<?> cubeClass = Class.forName("net.minecraft.client.model.geom.ModelPart$Cube", true, classLoader);
				Class<?> polygonClass = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon", true, classLoader);
				Class<?> vertexClass = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex", true, classLoader);
				Class<?> layerDefinitionClass = Class.forName("net.minecraft.client.model.geom.builders.LayerDefinition", true, classLoader);
				Class<?> meshDefinitionClass = Class.forName("net.minecraft.client.model.geom.builders.MeshDefinition", true, classLoader);
				Class<?> cubeDeformationClass = Class.forName("net.minecraft.client.model.geom.builders.CubeDeformation", true, classLoader);
				Class<?> armorModelSetClass = Class.forName("net.minecraft.client.renderer.entity.ArmorModelSet", true, classLoader);
				Class<?> equipmentSlotClass = Class.forName("net.minecraft.world.entity.EquipmentSlot", true, classLoader);

				Field cubeDeformationNone = cubeDeformationClass.getField("NONE");
				Field cubesField = modelPartClass.getDeclaredField("cubes");
				Field childrenField = modelPartClass.getDeclaredField("children");
				Field cubePolygonsField = cubeClass.getField("polygons");
				Field vertexXField = vertexClass.getDeclaredField("x");
				Field vertexYField = vertexClass.getDeclaredField("y");
				Field vertexZField = vertexClass.getDeclaredField("z");
				Field vertexUField = vertexClass.getDeclaredField("u");
				Field vertexVField = vertexClass.getDeclaredField("v");
				cubesField.setAccessible(true);
				childrenField.setAccessible(true);
				vertexXField.setAccessible(true);
				vertexYField.setAccessible(true);
				vertexZField.setAccessible(true);
				vertexUField.setAccessible(true);
				vertexVField.setAccessible(true);

				return new RuntimeBridge(
						classLoader,
						modelClass,
						modelPartClass,
						layerDefinitionClass,
						meshDefinitionClass,
						armorModelSetClass,
						equipmentSlotClass,
						cubeDeformationClass,
						cubeDeformationClass.getConstructor(float.class),
						cubeDeformationNone.get(null),
						layerDefinitionClass.getMethod("apply", Class.forName("net.minecraft.client.model.geom.builders.MeshTransformer", true, classLoader)),
						layerDefinitionClass.getMethod("create", meshDefinitionClass, int.class, int.class),
						layerDefinitionClass.getMethod("bakeRoot"),
						modelClass.getMethod("resetPose"),
						modelPartClass.getField("x"),
						modelPartClass.getField("y"),
						modelPartClass.getField("z"),
						modelPartClass.getField("xRot"),
						modelPartClass.getField("yRot"),
						modelPartClass.getField("zRot"),
						modelPartClass.getField("xScale"),
						modelPartClass.getField("yScale"),
						modelPartClass.getField("zScale"),
						modelPartClass.getField("visible"),
						modelPartClass.getField("skipDraw"),
						cubesField,
						childrenField,
						cubePolygonsField,
						armorModelSetClass.getMethod("get", equipmentSlotClass),
						polygonClass.getMethod("vertices"),
						vertexXField,
						vertexYField,
						vertexZField,
						vertexUField,
						vertexVField
				);
			} catch (Exception exception) {
				return null;
			}
		}

		private static Path findMergedJar() {
			if (!Files.isDirectory(LOOM_MERGED_ROOT)) {
				return null;
			}
			try (var stream = Files.walk(LOOM_MERGED_ROOT, 3)) {
				return stream
						.filter(Files::isRegularFile)
						.filter(path -> {
							String name = path.getFileName().toString();
							return name.startsWith("minecraft-merged-")
									&& name.endsWith(".jar")
									&& !name.endsWith(".jar.backup")
									&& path.toString().contains("loom.mappings");
						})
						.max(Comparator.comparing(Path::toString))
						.orElse(null);
			} catch (IOException exception) {
				return null;
			}
		}

		private record ModelCacheKey(
				String modelClassName,
				boolean baby,
				int textureWidth,
				int textureHeight,
				String layerFactoryMethodName,
				float cubeDeformation,
				float secondaryCubeDeformation,
				boolean modelFlag
		) {
		}

		private record ClientModelAdapter(
				Object model,
				Object rootPart,
				Method resetPoseMethod,
				Method setupAnimMethod,
				Class<?> stateClass
		) {
			private void resetPose() throws ReflectiveOperationException {
				this.resetPoseMethod.invoke(this.model);
			}

			private void setupAnim(Object state) throws ReflectiveOperationException {
				this.setupAnimMethod.invoke(this.model, state);
			}

			private Object newState(Map<String, Object> stateFields) throws ReflectiveOperationException {
				if (this.stateClass == Float.class || this.stateClass == float.class) {
					Object value = stateFields.get("modelState");
					return value instanceof Number number ? number.floatValue() : 0.0F;
				}
				if ("net.minecraft.util.Unit".equals(this.stateClass.getName())) {
					Field instanceField = this.stateClass.getField("INSTANCE");
					return instanceField.get(null);
				}
				Object state = this.stateClass.getDeclaredConstructor().newInstance();
				Map<String, Field> fieldsByName = new HashMap<>();
				for (Field field : this.stateClass.getFields()) {
					fieldsByName.put(field.getName(), field);
				}
				for (Map.Entry<String, Object> entry : stateFields.entrySet()) {
					Field field = fieldsByName.get(entry.getKey());
					if (field == null || entry.getValue() == null) {
						continue;
					}
					Object convertedValue = convertStateValue(field.getType(), entry.getValue());
					if (convertedValue == null) {
						continue;
					}
					field.set(state, convertedValue);
				}
				return state;
			}

			@SuppressWarnings({"rawtypes", "unchecked"})
			private Object convertStateValue(Class<?> targetType, Object value) {
				if (value == null) {
					return null;
				}
				if (!targetType.isPrimitive() && targetType.isInstance(value)) {
					return value;
				}
				if ((targetType == float.class || targetType == Float.class) && value instanceof Number number) {
					return number.floatValue();
				}
				if ((targetType == double.class || targetType == Double.class) && value instanceof Number number) {
					return number.doubleValue();
				}
				if ((targetType == int.class || targetType == Integer.class) && value instanceof Number number) {
					return number.intValue();
				}
				if ((targetType == long.class || targetType == Long.class) && value instanceof Number number) {
					return number.longValue();
				}
				if ((targetType == boolean.class || targetType == Boolean.class) && value instanceof Boolean bool) {
					return bool;
				}
				if (targetType.isEnum() && value instanceof String enumName) {
					try {
						return Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), enumName);
					} catch (IllegalArgumentException ignored) {
						return null;
					}
				}
				return null;
			}
		}

		private record RuntimeBridge(
				URLClassLoader classLoader,
				Class<?> modelClass,
				Class<?> modelPartClass,
				Class<?> layerDefinitionClass,
				Class<?> meshDefinitionClass,
				Class<?> armorModelSetClass,
				Class<?> equipmentSlotClass,
				Class<?> cubeDeformationClass,
				Constructor<?> cubeDeformationConstructor,
				Object cubeDeformationNone,
				Method layerApplyMethod,
				Method layerCreateMethod,
				Method layerBakeRootMethod,
				Method modelResetPoseMethod,
				Field partXField,
				Field partYField,
				Field partZField,
				Field partXRotField,
				Field partYRotField,
				Field partZRotField,
				Field partXScaleField,
				Field partYScaleField,
				Field partZScaleField,
				Field partVisibleField,
				Field partSkipDrawField,
				Field partCubesField,
				Field partChildrenField,
				Field cubePolygonsField,
				Method armorModelSetGetMethod,
				Method polygonVerticesMethod,
				Field vertexXField,
				Field vertexYField,
				Field vertexZField,
				Field vertexUField,
				Field vertexVField
		) {
		}
	}

}
