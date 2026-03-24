package com.lostglade.server.camera.bluemap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lostglade.Lg2;
import com.lostglade.mixin.PlayerTrackedDataAccessor;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
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
	private static final Identifier SHEEP_WOOL_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/sheep/sheep_wool");
	private static final Identifier ARMOR_STAND_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/armorstand/wood");
	private static final Identifier VILLAGER_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/villager/villager");
	private static final Identifier EXPERIENCE_ORB_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/experience_orb");
	private static final Identifier FISHING_HOOK_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/fishing_hook");
	private static final Identifier LEASH_SEGMENT_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "block/brown_wool");
	private static final Identifier FISHING_LINE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "block/light_gray_wool");
	private static final Map<String, BlueMapCameraRenderer.TextureMaterial> STATIC_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, BlueMapCameraRenderer.TextureMaterial> PLAYER_SKIN_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Identifier> ITEM_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, ItemVisual> ITEM_VISUAL_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, ClientModelResolver> VANILLA_CLIENT_MODEL_RULES = buildVanillaClientModelRules();

	private CameraEntityRenderer() {
	}

	interface MaterialResolver {
		int materialForTexture(Identifier textureId);

		int materialForPlayerSkin(PlayerSkinSnapshot skinSnapshot);
	}

	interface EntitySnapshot {
		Vec3 position();
	}

	record HumanoidSnapshot(
			Vec3 position,
			float yaw,
			float bodyYaw,
			float headYaw,
			float pitch,
			float walkPos,
			float walkSpeed,
			float attackAnim,
			float swimAmount,
			Pose pose,
			Direction sleepingDirection,
			boolean crouching,
			boolean swimming,
			boolean fallFlying,
			boolean passenger,
			boolean baby,
			boolean aggressive,
			boolean usingItem,
			HumanoidArm mainArm,
			HumanoidKind kind,
			Identifier texture,
			Identifier[] overlayTextures,
			ItemStack leftHandItem,
			ItemStack rightHandItem,
			PlayerSkinSnapshot playerSkin,
			byte playerModelBits
	) implements EntitySnapshot {
	}

	record QuadrupedSnapshot(
			Vec3 position,
			float yaw,
			float bodyYaw,
			float headYaw,
			float pitch,
			float walkPos,
			float walkSpeed,
			boolean baby,
			QuadrupedKind kind,
			Identifier texture,
			Identifier overlayTexture,
			boolean sheared,
			float headEatPositionScale,
			float headEatAngleScale
	) implements EntitySnapshot {
	}

	record ChickenSnapshot(
			Vec3 position,
			float yaw,
			float bodyYaw,
			float headYaw,
			float pitch,
			float walkPos,
			float walkSpeed,
			boolean baby,
			Identifier texture,
			float flap
	) implements EntitySnapshot {
	}

	record CreeperSnapshot(
			Vec3 position,
			float bodyYaw,
			float headYaw,
			float pitch,
			float walkPos,
			float walkSpeed,
			boolean baby,
			float swelling
	) implements EntitySnapshot {
	}

	record SpiderSnapshot(
			Vec3 position,
			float yaw,
			float walkPos,
			float walkSpeed
	) implements EntitySnapshot {
	}

	record ArmorStandSnapshot(
			Vec3 position,
			float yaw,
			boolean small,
			boolean showArms,
			boolean showBasePlate,
			Rotations headPose,
			Rotations bodyPose,
			Rotations leftArmPose,
			Rotations rightArmPose,
			Rotations leftLegPose,
			Rotations rightLegPose
	) implements EntitySnapshot {
	}

	record ItemSnapshot(
			Vec3 position,
			float spin,
			ItemVisual visual
	) implements EntitySnapshot {
	}

	record ExperienceOrbSnapshot(
			Vec3 position,
			int icon,
			float ageInTicks
	) implements EntitySnapshot {
	}

	record FishingHookSnapshot(
			Vec3 position,
			Vec3 lineStartOffset
	) implements EntitySnapshot {
	}

	record LineSnapshot(
			Vec3 position,
			Vec3 start,
			Vec3 end,
			float thickness,
			float sag,
			Identifier texture
	) implements EntitySnapshot {
	}

	record CompositeSnapshot(
			Vec3 position,
			EntitySnapshot primary,
			EntitySnapshot[] attachments
	) implements EntitySnapshot {
	}

	record ClientLayerSnapshot(
			String modelClassName,
			Identifier texture,
			PlayerSkinSnapshot playerSkin,
			int tintRgb,
			boolean emissive,
			String layerFactoryMethodName,
			float cubeDeformation,
			boolean modelFlag,
			float renderScale
	) {
		ClientLayerSnapshot(String modelClassName, Identifier texture, int tintRgb, boolean emissive) {
			this(modelClassName, texture, null, tintRgb, emissive, null, 0.0F, false, 1.0F);
		}

		ClientLayerSnapshot withFactory(String layerFactoryMethodName) {
			return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.tintRgb, this.emissive, layerFactoryMethodName, this.cubeDeformation, this.modelFlag, this.renderScale);
		}

		ClientLayerSnapshot withCubeDeformation(float cubeDeformation) {
			return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.tintRgb, this.emissive, this.layerFactoryMethodName, cubeDeformation, this.modelFlag, this.renderScale);
		}

		ClientLayerSnapshot withModelFlag(boolean modelFlag) {
			return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, modelFlag, this.renderScale);
		}

		ClientLayerSnapshot withRenderScale(float renderScale) {
			return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, this.modelFlag, renderScale);
		}

		ClientLayerSnapshot withPlayerSkin(PlayerSkinSnapshot playerSkin) {
			return new ClientLayerSnapshot(this.modelClassName, this.texture, playerSkin, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, this.modelFlag, this.renderScale);
		}
	}

	enum ClientModelTransformKind {
		LIVING,
		BOAT
	}

	record ClientModelSnapshot(
			Vec3 position,
			float rootYaw,
			float rootYawOffsetDegrees,
			float rootScale,
			ClientModelTransformKind transformKind,
			Map<String, Object> stateFields,
			ClientLayerSnapshot[] layers
	) implements EntitySnapshot {
	}

	record PlayerSkinSnapshot(
			String cacheKey,
			String url,
			Identifier fallbackTexture,
			boolean slim
	) {
	}

	record ItemVisual(
			Identifier flatTexture,
			ResolvedItemModel model
	) {
	}

	private record ResolvedItemModel(
			Map<String, String> textures,
			List<ItemModelElement> elements
	) {
	}

	private enum ItemDisplayTransformContext {
		THIRD_PERSON_RIGHT_HAND,
		THIRD_PERSON_LEFT_HAND
	}

	private record ItemModelElement(
			Vec3 from,
			Vec3 to,
			Map<Direction, ItemModelFace> faces,
			ElementRotation rotation
	) {
	}

	private record ItemModelFace(
			String texture,
			double[] uv,
			int rotation
	) {
	}

	private record ElementRotation(
			Vec3 origin,
			Direction.Axis axis,
			float angle,
			boolean rescale,
			Matrix4f transform
	) {
	}

	@FunctionalInterface
	private interface ClientModelResolver {
		ClientModelSnapshot capture(LivingEntity livingEntity);
	}

	enum HumanoidKind {
		PLAYER(64, 64, 4.0F, 4.0F, true, false, 1.0F),
		PLAYER_SLIM(64, 64, 3.0F, 4.0F, true, false, 1.0F),
		ZOMBIE(64, 64, 4.0F, 4.0F, false, false, 1.0F),
		SKELETON(64, 32, 2.0F, 2.0F, false, false, 1.0F),
		ENDERMAN(64, 32, 2.0F, 2.0F, false, false, 1.45F),
		VILLAGER(64, 64, 0.0F, 4.0F, false, true, 1.0F);

		final int textureWidth;
		final int textureHeight;
		final float armWidth;
		final float legWidth;
		final boolean outerLayers;
		final boolean villager;
		final float scale;

		HumanoidKind(int textureWidth, int textureHeight, float armWidth, float legWidth, boolean outerLayers, boolean villager, float scale) {
			this.textureWidth = textureWidth;
			this.textureHeight = textureHeight;
			this.armWidth = armWidth;
			this.legWidth = legWidth;
			this.outerLayers = outerLayers;
			this.villager = villager;
			this.scale = scale;
		}
	}

	enum QuadrupedKind {
		COW,
		PIG,
		SHEEP
	}

	static EntitySnapshot captureEntity(Entity entity) {
		try {
			return attachAuxiliarySnapshots(entity, captureEntityUnsafe(entity));
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Failed to capture camera snapshot for entity {}", entity, throwable);
			return null;
		}
	}

	private static EntitySnapshot captureEntityUnsafe(Entity entity) {
		if (!entity.isAlive() || entity.isInvisible()) {
			return null;
		}
		if (entity instanceof AbstractBoat boat && VanillaClientModels.isAvailable()) {
			return captureBoatClientModel(boat);
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
					false,
					false,
					livingEntity.isUsingItem(),
					player.getMainArm(),
					kind,
					playerSkin == null ? PLAYER_WIDE_FALLBACK : playerSkin.fallbackTexture(),
					new Identifier[0],
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
					armorStand.getRightLegPose()
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

		if (entity instanceof Zombie) {
			if (VanillaClientModels.isAvailable()) {
				return captureZombieClientModel((Zombie) entity);
			}
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
					leftHandItem(livingEntity),
					rightHandItem(livingEntity),
					null,
					(byte) 0
			);
		}

		if (entity instanceof AbstractSkeleton) {
			return humanoidSnapshot(livingEntity, HumanoidKind.SKELETON, SKELETON_TEXTURE, new Identifier[0]);
		}

		if (entity instanceof EnderMan) {
			return humanoidSnapshot(livingEntity, HumanoidKind.ENDERMAN, ENDERMAN_TEXTURE, new Identifier[]{ENDERMAN_EYES_TEXTURE});
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
			ItemVisual visual = resolveItemVisual(itemEntity.getItem());
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

	private static HumanoidSnapshot humanoidSnapshot(LivingEntity livingEntity, HumanoidKind kind, Identifier texture, Identifier[] overlays) {
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
				leftHandItem(livingEntity),
				rightHandItem(livingEntity),
				null,
				(byte) 0
		);
	}

	private static ItemStack rightHandItem(LivingEntity livingEntity) {
		if (livingEntity == null) {
			return ItemStack.EMPTY;
		}
		return livingEntity.getMainArm() == HumanoidArm.RIGHT
				? livingEntity.getItemInHand(InteractionHand.MAIN_HAND).copy()
				: livingEntity.getItemInHand(InteractionHand.OFF_HAND).copy();
	}

	private static ItemStack leftHandItem(LivingEntity livingEntity) {
		if (livingEntity == null) {
			return ItemStack.EMPTY;
		}
		return livingEntity.getMainArm() == HumanoidArm.RIGHT
				? livingEntity.getItemInHand(InteractionHand.OFF_HAND).copy()
				: livingEntity.getItemInHand(InteractionHand.MAIN_HAND).copy();
	}

	private static EntitySnapshot attachAuxiliarySnapshots(Entity entity, EntitySnapshot primary) {
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
		if (attachments.isEmpty()) {
			return primary;
		}
		return new CompositeSnapshot(primary.position(), primary, attachments.toArray(EntitySnapshot[]::new));
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

	private static ClientModelSnapshot livingClientModelSnapshot(
			LivingEntity livingEntity,
			float rootYaw,
			Map<String, Object> stateFields,
			ClientLayerSnapshot[] layers
	) {
		return clientModelSnapshot(livingEntity.position(), rootYaw, 180.0F, livingEntity.getScale(), ClientModelTransformKind.LIVING, stateFields, layers);
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

	private static void addClientLayerIfPresent(List<ClientLayerSnapshot> layers, String modelClassName, Identifier texture, int tintRgb, boolean emissive) {
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

	private static Map<String, Object> livingStateFields(LivingEntity livingEntity) {
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
		state.put("leftHandItemStack", livingEntity.getItemInHand(InteractionHand.OFF_HAND).copy());
		state.put("rightHandItemStack", livingEntity.getItemInHand(InteractionHand.MAIN_HAND).copy());
		state.put("headEquipment", livingEntity.getItemBySlot(EquipmentSlot.HEAD).copy());
		state.put("chestEquipment", livingEntity.getItemBySlot(EquipmentSlot.CHEST).copy());
		state.put("legsEquipment", livingEntity.getItemBySlot(EquipmentSlot.LEGS).copy());
		state.put("feetEquipment", livingEntity.getItemBySlot(EquipmentSlot.FEET).copy());
		String leftArmPose = !livingEntity.getItemInHand(InteractionHand.OFF_HAND).isEmpty() ? "ITEM" : "EMPTY";
		String rightArmPose = !livingEntity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() ? "ITEM" : "EMPTY";
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
		registerSimpleClientRule(rules, "cave_spider", "net.minecraft.client.model.monster.spider.SpiderModel", minecraftTexture("entity/spider/cave_spider"));
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

	private static Identifier minecraftTexture(String path) {
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
		Identifier assetId = assetIdOverride != null ? assetIdOverride : equipmentAssetId(stack);
		if (assetId == null) {
			return;
		}
		JsonObject equipment = ASSETS.loadJsonAsset("assets/" + assetId.getNamespace() + "/equipment/" + assetId.getPath() + ".json");
		if (equipment == null || !equipment.has("layers") || !equipment.get("layers").isJsonObject()) {
			return;
		}
		JsonObject layerMap = equipment.getAsJsonObject("layers");
		if (!layerMap.has(layerType) || !layerMap.get(layerType).isJsonArray()) {
			return;
		}
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
			layers.add(
					new ClientLayerSnapshot(
							modelClassName,
							Identifier.fromNamespaceAndPath(textureId.getNamespace(), "entity/equipment/" + layerType + "/" + textureId.getPath()),
							tintRgb,
							false
					).withRenderScale(renderScale)
			);
		}
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

		var skinData = PlayerUtils.getSkinUrl(property);
		String url = skinData == null ? null : skinData.left();
		boolean slim = skinData != null
				&& skinData.right() != null
				&& "slim".equalsIgnoreCase(skinData.right().toString());
		Identifier fallback = slim ? PLAYER_SLIM_FALLBACK : PLAYER_WIDE_FALLBACK;
		return new PlayerSkinSnapshot(property.value(), url, fallback, slim);
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
		} else if (entitySnapshot instanceof ExperienceOrbSnapshot experienceOrbSnapshot) {
			renderExperienceOrb(context, experienceOrbSnapshot);
		} else if (entitySnapshot instanceof FishingHookSnapshot fishingHookSnapshot) {
			renderFishingHook(context, fishingHookSnapshot);
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
				? context.materialResolver.materialForPlayerSkin(snapshot.playerSkin())
				: context.materialResolver.materialForTexture(snapshot.texture());
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
		if (snapshot.aggressive()) {
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
			int overlayMaterial = context.materialResolver.materialForTexture(overlayTexture);
			if (snapshot.kind().villager) {
				renderVillagerFeatures(context, snapshot, root, overlayMaterial, headYaw, headPitch, rightLegPitch, leftLegPitch);
			} else if (snapshot.kind() == HumanoidKind.ENDERMAN && overlayTexture.equals(ENDERMAN_EYES_TEXTURE)) {
				renderEndermanEyes(context, snapshot, root, overlayMaterial, headYaw, headPitch);
			} else {
				renderHumanBase(context, snapshot, root, overlayMaterial, snapshot.kind().textureWidth, snapshot.kind().textureHeight, headYaw, headPitch, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll, rightLegPitch, leftLegPitch, rightLegYaw, leftLegYaw, rightLegRoll, leftLegRoll);
			}
		}

		renderHumanoidHeldItems(context, snapshot, root, rightArmPitch, leftArmPitch, rightArmYaw, leftArmYaw, rightArmRoll, leftArmRoll);
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
			addHumanoidBox(context, snapshot, leftArm, 4.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, snapshot.kind() == HumanoidKind.SKELETON || snapshot.kind() == HumanoidKind.ENDERMAN ? 40 : 32, snapshot.kind() == HumanoidKind.SKELETON || snapshot.kind() == HumanoidKind.ENDERMAN ? 16 : 48, texWidth, texHeight, material, snapshot.kind() != HumanoidKind.PLAYER && snapshot.kind() != HumanoidKind.PLAYER_SLIM, 0.0F);
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
		renderHumanoidHeldItem(context, snapshot.rightHandItem(), humanoidHandTransform(root, snapshot, HumanoidArm.RIGHT, rightArmPitch, rightArmYaw, rightArmRoll), ItemDisplayTransformContext.THIRD_PERSON_RIGHT_HAND);
		renderHumanoidHeldItem(context, snapshot.leftHandItem(), humanoidHandTransform(root, snapshot, HumanoidArm.LEFT, leftArmPitch, leftArmYaw, leftArmRoll), ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND);
	}

	private static void renderHumanoidHeldItem(RenderContext context, ItemStack stack, Matrix4f handTransform, ItemDisplayTransformContext transformContext) {
		if (stack == null || stack.isEmpty() || handTransform == null) {
			return;
		}
		ItemVisual visual = resolveItemVisual(stack);
		if (visual == null) {
			return;
		}
		Matrix4f itemTransform = new Matrix4f(handTransform)
				.rotateX(radians(-90.0F))
				.rotateY(radians(180.0F))
				.translate(transformContext == ItemDisplayTransformContext.THIRD_PERSON_LEFT_HAND ? -1.0F / 16.0F : 1.0F / 16.0F, 0.125F, -0.625F);
		renderItemVisual(context, itemTransform, visual, transformContext);
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
		Matrix4f head = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
		addBox(context, head, -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.1F, 15, 15);
	}

	private static void renderQuadruped(RenderContext context, QuadrupedSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(snapshot.texture());
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
			int overlayMaterial = context.materialResolver.materialForTexture(snapshot.overlayTexture());
			addBox(context, head, -4.0F, 8.0F, -14.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, overlayMaterial, false, 0.8F);
			addBox(context, root, -5.0F, 6.0F, -8.0F, 10.0F, 8.0F, 16.0F, 28, 8, 64, 32, overlayMaterial, false, 0.8F);
			addBox(context, rightFront, -5.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, false, 0.6F);
			addBox(context, leftFront, 1.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, true, 0.6F);
			addBox(context, rightBack, -5.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, false, 0.6F);
			addBox(context, leftBack, 1.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, overlayMaterial, true, 0.6F);
		}
	}

	private static void renderChicken(RenderContext context, ChickenSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(snapshot.texture());
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
		int material = context.materialResolver.materialForTexture(CREEPER_TEXTURE);
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
		int material = context.materialResolver.materialForTexture(SPIDER_TEXTURE);
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

		int eyeMaterial = context.materialResolver.materialForTexture(SPIDER_EYES_TEXTURE);
		addBox(context, root, -4.0F, 8.0F, -10.0F, 8.0F, 8.0F, 8.0F, 32, 4, 64, 32, eyeMaterial, false, 0.05F, 15, 15);
	}

	private static void renderArmorStand(RenderContext context, ArmorStandSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.yaw()))
				.scale(snapshot.small() ? 0.55F : 1.0F);
		int material = context.materialResolver.materialForTexture(ARMOR_STAND_TEXTURE);

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
	}

	private static void renderItem(RenderContext context, ItemSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y + 0.125F, (float) snapshot.position().z)
				.rotateY(snapshot.spin());
		if (snapshot.visual().model() != null && !snapshot.visual().model().elements().isEmpty()) {
			renderItemModel(context, root, snapshot.visual().model());
			return;
		}
		if (snapshot.visual().flatTexture() == null) {
			return;
		}
		int material = context.materialResolver.materialForTexture(snapshot.visual().flatTexture());
		addDoubleSidedPlane(context, root, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
		Matrix4f crossed = new Matrix4f(root).rotateY((float) Math.PI * 0.5F);
		addDoubleSidedPlane(context, crossed, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
	}

	private static void renderExperienceOrb(RenderContext context, ExperienceOrbSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(EXPERIENCE_ORB_TEXTURE);
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
		int material = context.materialResolver.materialForTexture(FISHING_HOOK_TEXTURE);
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

	private static void renderLine(RenderContext context, LineSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(snapshot.texture());
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

	private static void renderItemVisual(RenderContext context, Matrix4f root, ItemVisual visual, ItemDisplayTransformContext transformContext) {
		if (visual == null) {
			return;
		}
		if (visual.model() != null && !visual.model().elements().isEmpty()) {
			renderItemModel(context, root, visual.model());
			return;
		}
		if (visual.flatTexture() == null) {
			return;
		}
		int material = context.materialResolver.materialForTexture(visual.flatTexture());
		addDoubleSidedPlane(context, root, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
		Matrix4f crossed = new Matrix4f(root).rotateY((float) Math.PI * 0.5F);
		addDoubleSidedPlane(context, crossed, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
	}

	private static void renderItemModel(RenderContext context, Matrix4f root, ResolvedItemModel model) {
		Matrix4f transform = new Matrix4f(root).translate(-0.25F, 0.0F, -0.25F).scale(0.5F);
		for (ItemModelElement element : model.elements()) {
			renderItemModelElement(context, transform, model, element);
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
		addTexturedPlane(context, transform, x, y, z, width, height, 0.0F, 0.0F, 1.0F, 1.0F, material, 1.0F, 1.0F, 1.0F);
	}

	private static void addDoubleSidedPlane(RenderContext context, Matrix4f transform, float x, float y, float z, float width, float height, int material) {
		addTexturedDoubleSidedPlane(context, transform, x, y, z, width, height, 0.0F, 0.0F, 1.0F, 1.0F, material, 1.0F, 1.0F, 1.0F);
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
		addTexturedPlane(context, transform, x, y, z, width, height, u0, v0, u1, v1, material, red, green, blue);
		Matrix4f back = new Matrix4f(transform).rotateY((float) Math.PI);
		addTexturedPlane(context, back, -(x + width), y, -z, width, height, u0, v0, u1, v1, material, red, green, blue);
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
		Vector3f p0 = transformPosition(transform, x, y, z);
		Vector3f p1 = transformPosition(transform, x + width, y, z);
		Vector3f p2 = transformPosition(transform, x + width, y + height, z);
		Vector3f p3 = transformPosition(transform, x, y + height, z);
		LightSample lightSample = context.lightAt((p0.x + p2.x) * 0.5F, (p0.y + p2.y) * 0.5F, (p0.z + p2.z) * 0.5F);
		addQuad(context, p0, p1, p2, p3, texU0, texU1, texV1, texV0, material, lightSample.sky(), lightSample.block(), red, green, blue);
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
		addBox(context, transform, x, y, z, width, height, depth, texU, texV, texWidth, texHeight, material, mirror, inflate, -1, -1);
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
		float minX = x - inflate;
		float minY = y - inflate;
		float minZ = z - inflate;
		float maxX = x + width + inflate;
		float maxY = y + height + inflate;
		float maxZ = z + depth + inflate;
		if (mirror) {
			float swap = minX;
			minX = maxX;
			maxX = swap;
		}

		Vector3f nnn = transformPosition(transform, minX * PX, minY * PX, minZ * PX);
		Vector3f pnn = transformPosition(transform, maxX * PX, minY * PX, minZ * PX);
		Vector3f ppn = transformPosition(transform, maxX * PX, maxY * PX, minZ * PX);
		Vector3f npn = transformPosition(transform, minX * PX, maxY * PX, minZ * PX);
		Vector3f nnp = transformPosition(transform, minX * PX, minY * PX, maxZ * PX);
		Vector3f pnp = transformPosition(transform, maxX * PX, minY * PX, maxZ * PX);
		Vector3f ppp = transformPosition(transform, maxX * PX, maxY * PX, maxZ * PX);
		Vector3f npp = transformPosition(transform, minX * PX, maxY * PX, maxZ * PX);

		LightSample lightSample = context.lightAt((nnn.x + ppp.x) * 0.5F, (nnn.y + ppp.y) * 0.5F, (nnn.z + ppp.z) * 0.5F);
		int skyLight = lightSample.sky();
		int blockLight = lightSample.block();

		float u0 = texU;
		float v0 = texV;
		float u1 = u0 + depth;
		float u2 = u1 + width;
		float u3 = u2 + depth;
		float u4 = u3 + width;
		float v1 = v0 + depth;
		float v2 = v1 + height;

		addQuad(context, pnp, nnp, npp, ppp, uv(u1, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnn, pnn, ppn, npn, uv(u3, texWidth), uv(u4, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnp, nnn, npn, npp, uv(u1, texWidth), uv(u0, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, pnp, ppp, ppn, uv(u3, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, nnn, nnp, pnp, uv(u1, texWidth), uv(u2, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, npn, ppn, ppp, npp, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
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
		float minX = x - inflate;
		float minY = y - inflate;
		float minZ = z - inflate;
		float maxX = x + width + inflate;
		float maxY = y + height + inflate;
		float maxZ = z + depth + inflate;
		if (mirror) {
			float swap = minX;
			minX = maxX;
			maxX = swap;
		}

		Vector3f nnn = transformPosition(transform, minX * PX, minY * PX, minZ * PX);
		Vector3f pnn = transformPosition(transform, maxX * PX, minY * PX, minZ * PX);
		Vector3f ppn = transformPosition(transform, maxX * PX, maxY * PX, minZ * PX);
		Vector3f npn = transformPosition(transform, minX * PX, maxY * PX, minZ * PX);
		Vector3f nnp = transformPosition(transform, minX * PX, minY * PX, maxZ * PX);
		Vector3f pnp = transformPosition(transform, maxX * PX, minY * PX, maxZ * PX);
		Vector3f ppp = transformPosition(transform, maxX * PX, maxY * PX, maxZ * PX);
		Vector3f npp = transformPosition(transform, minX * PX, maxY * PX, maxZ * PX);

		LightSample lightSample = context.lightAt((nnn.x + ppp.x) * 0.5F, (nnn.y + ppp.y) * 0.5F, (nnn.z + ppp.z) * 0.5F);
		int skyLight = overrideSkyLight >= 0 ? overrideSkyLight : lightSample.sky();
		int blockLight = overrideBlockLight >= 0 ? overrideBlockLight : lightSample.block();

		float u0 = texU;
		float v0 = texV;
		float u1 = u0 + depth;
		float u2 = u1 + width;
		float u3 = u2 + depth;
		float u4 = u3 + width;
		float v1 = v0 + depth;
		float v2 = v1 + height;

		addQuad(context, pnp, nnp, npp, ppp, uv(u1, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnn, pnn, ppn, npn, uv(u3, texWidth), uv(u4, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnp, nnn, npn, npp, uv(u0, texWidth), uv(u1, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, pnp, ppp, ppn, uv(u2, texWidth), uv(u3, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, nnn, nnp, pnp, uv(u1, texWidth), uv(u2, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, npn, ppn, ppp, npp, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
	}

	private static float uv(float value, int size) {
		return value / size;
	}

	private static Vector3f transformPosition(Matrix4f transform, float x, float y, float z) {
		return transform.transformPosition(new Vector3f(x, y, z));
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
		int triangle = context.model.add(2);
		context.model
				.setPositions(triangle, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z)
				.setUvs(triangle, u0, v1, u1, v1, u1, v0)
				.setAOs(triangle, 1.0F, 1.0F, 1.0F)
				.setColor(triangle, red, green, blue)
				.setSunlight(triangle, skyLight)
				.setBlocklight(triangle, blockLight)
				.setMaterialIndex(triangle, material);
		context.model
				.setPositions(triangle + 1, a.x, a.y, a.z, c.x, c.y, c.z, d.x, d.y, d.z)
				.setUvs(triangle + 1, u0, v1, u1, v0, u0, v0)
				.setAOs(triangle + 1, 1.0F, 1.0F, 1.0F)
				.setColor(triangle + 1, red, green, blue)
				.setSunlight(triangle + 1, skyLight)
				.setBlocklight(triangle + 1, blockLight)
				.setMaterialIndex(triangle + 1, material);
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
		int triangle = context.model.add(2);
		context.model
				.setPositions(triangle, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z)
				.setUvs(triangle, au, av, bu, bv, cu, cv)
				.setAOs(triangle, 1.0F, 1.0F, 1.0F)
				.setColor(triangle, red, green, blue)
				.setSunlight(triangle, skyLight)
				.setBlocklight(triangle, blockLight)
				.setMaterialIndex(triangle, material);
		context.model
				.setPositions(triangle + 1, a.x, a.y, a.z, c.x, c.y, c.z, d.x, d.y, d.z)
				.setUvs(triangle + 1, au, av, cu, cv, du, dv)
				.setAOs(triangle + 1, 1.0F, 1.0F, 1.0F)
				.setColor(triangle + 1, red, green, blue)
				.setSunlight(triangle + 1, skyLight)
				.setBlocklight(triangle + 1, blockLight)
				.setMaterialIndex(triangle + 1, material);
	}

	private static void renderItemModelElement(RenderContext context, Matrix4f root, ResolvedItemModel model, ItemModelElement element) {
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
					context.materialResolver.materialForTexture(texture),
					light.sky(),
					light.block(),
					1.0F,
					1.0F,
					1.0F
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

	private static Identifier holderTexture(Holder<?> holder, String prefix) {
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

	private static Identifier professionLevelTexture(int level) {
		return switch (Mth.clamp(level, 1, 5)) {
			case 1 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/stone");
			case 2 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/iron");
			case 3 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/gold");
			case 4 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/emerald");
			default -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/diamond");
		};
	}

	private static ItemVisual resolveItemVisual(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) {
			return null;
		}
		return ITEM_VISUAL_CACHE.computeIfAbsent(itemId.toString(), ignored -> resolveItemVisualInternal(stack, itemId));
	}

	private static ItemVisual resolveItemVisualInternal(ItemStack stack, Identifier itemId) {
		ResolvedItemModel model = resolveItemModel(itemId.withPrefix("item/"), new HashSet<>());
		if ((model == null || model.elements().isEmpty()) && stack.getItem() instanceof BlockItem blockItem) {
			Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
			if (blockId != null) {
				model = resolveItemModel(blockId.withPrefix("block/"), new HashSet<>());
			}
		}
		Identifier flatTexture = resolveItemTexture(stack);
		if (flatTexture == null && model != null) {
			flatTexture = primaryTexture(model);
		}
		if ((model == null || model.elements().isEmpty()) && flatTexture == null) {
			return null;
		}
		return new ItemVisual(flatTexture, model);
	}

	private static Identifier resolveItemTexture(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (itemId == null) {
			return null;
		}
		return ITEM_TEXTURE_CACHE.computeIfAbsent(itemId.toString(), ignored -> resolveItemTextureInternal(itemId));
	}

	private static Identifier resolveItemTextureInternal(Identifier itemId) {
		return resolveModelTexture(itemId.withPrefix("item/"), Set.of());
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
		ITEM_VISUAL_CACHE.putIfAbsent(cacheKey, new ItemVisual(primaryTexture(resolved), resolved));
		return resolved;
	}

	private static ResolvedItemModel doResolveItemModel(Identifier modelId, Set<String> resolving) {
		JsonObject json = ASSETS.loadModel(modelId);
		if (json == null) {
			return null;
		}

		Map<String, String> textures = new HashMap<>();
		List<ItemModelElement> elements = new ArrayList<>();
		if (json.has("parent")) {
			Identifier parentId = Identifier.tryParse(json.get("parent").getAsString());
			if (parentId != null && !"builtin/generated".equals(parentId.toString())) {
				ResolvedItemModel parent = resolveItemModel(parentId, resolving);
				if (parent != null) {
					textures.putAll(parent.textures());
					elements.addAll(parent.elements());
				}
			}
		}
		if (json.has("textures") && json.get("textures").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
				textures.put(entry.getKey(), entry.getValue().getAsString());
			}
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
							faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0
					));
				}
				elements.add(new ItemModelElement(from, to, faces, rotation));
			}
		}
		return new ResolvedItemModel(Map.copyOf(textures), List.copyOf(elements));
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
						? context.materialResolver.materialForPlayerSkin(layer.playerSkin())
						: context.materialResolver.materialForTexture(layer.texture());
				renderLayer(bridge, context, root, snapshot.stateFields(), baby, layer, material);
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
					float red = ((layer.tintRgb() >> 16) & 0xFF) / 255.0F;
					float green = ((layer.tintRgb() >> 8) & 0xFF) / 255.0F;
					float blue = (layer.tintRgb() & 0xFF) / 255.0F;
					Matrix4f layerRoot = Mth.equal(layer.renderScale(), 1.0F) ? root : new Matrix4f(root).scale(layer.renderScale());
					renderPartTree(bridge, context, adapter.rootPart, layerRoot, material, red, green, blue, layer.emissive());
				}
			} catch (Exception ignored) {
			}
		}

		private static ModelCacheKey modelCacheKey(ClientLayerSnapshot layer, boolean baby) {
			BufferedImage texture = ASSETS.loadTexture(layer.texture());
			int width = texture == null ? 64 : texture.getWidth();
			int height = texture == null ? 64 : texture.getHeight();
			return new ModelCacheKey(layer.modelClassName(), baby, width, height, layer.layerFactoryMethodName(), layer.cubeDeformation(), layer.modelFlag());
		}

		private static Matrix4f rootTransform(ClientModelSnapshot snapshot) {
			return switch (snapshot.transformKind()) {
				case BOAT -> boatRootTransform(snapshot);
				case LIVING -> new Matrix4f()
						.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
						.scale(snapshot.rootScale())
						.rotateY(radians(snapshot.rootYawOffsetDegrees() - snapshot.rootYaw()))
						.scale(-1.0F, -1.0F, 1.0F)
						.translate(0.0F, -1.501F, 0.0F);
			};
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

		private static boolean stateBoolean(Map<String, Object> stateFields, String key, boolean fallback) {
			Object value = stateFields.get(key);
			return value instanceof Boolean bool ? bool : fallback;
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
				boolean modelFlag
		) {
			try {
				Class<?> modelClass = Class.forName(modelClassName, true, bridge.classLoader);
				Object layerDefinition = createLayerDefinition(bridge, modelClass, textureWidth, textureHeight, layerFactoryMethodName, cubeDeformation, modelFlag);
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
				Constructor<?> constructor = modelClass.getConstructor(bridge.modelPartClass);
				return constructor.newInstance(rootPart);
			}
		}

		private static Object createLayerDefinition(
				RuntimeBridge bridge,
				Class<?> modelClass,
				int textureWidth,
				int textureHeight,
				String layerFactoryMethodName,
				float cubeDeformation,
				boolean modelFlag
		) throws ReflectiveOperationException {
			List<Method> candidates = new ArrayList<>();
			for (Class<?> cursor = modelClass; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
				for (Method method : cursor.getDeclaredMethods()) {
					if ((method.getModifiers() & java.lang.reflect.Modifier.STATIC) == 0) {
						continue;
					}
					if (!bridge.layerDefinitionClass.equals(method.getReturnType()) && !bridge.meshDefinitionClass.equals(method.getReturnType())) {
						continue;
					}
					if (layerFactoryMethodName != null && !layerFactoryMethodName.equals(method.getName())) {
						continue;
					}
					if (isSupportedLayerFactory(bridge, method)) {
						candidates.add(method);
					}
				}
			}
			candidates.sort(Comparator.comparingInt(VanillaClientModels::layerMethodPriority));
			for (Method method : candidates) {
				method.setAccessible(true);
				Object result = invokeLayerFactory(bridge, method, cubeDeformation, modelFlag);
				if (result == null) {
					continue;
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

		private static boolean isSupportedLayerFactory(RuntimeBridge bridge, Method method) {
			Class<?>[] parameterTypes = method.getParameterTypes();
			return switch (parameterTypes.length) {
				case 0 -> true;
				case 1 -> bridge.cubeDeformationClass.equals(parameterTypes[0]) || parameterTypes[0] == boolean.class;
				case 2 -> bridge.cubeDeformationClass.equals(parameterTypes[0]) && parameterTypes[1] == boolean.class;
				default -> false;
			};
		}

		private static Object invokeLayerFactory(RuntimeBridge bridge, Method method, float cubeDeformation, boolean modelFlag) throws ReflectiveOperationException {
			Class<?>[] parameterTypes = method.getParameterTypes();
			return switch (parameterTypes.length) {
				case 0 -> method.invoke(null);
				case 1 -> {
					if (bridge.cubeDeformationClass.equals(parameterTypes[0])) {
						yield method.invoke(null, cubeDeformationObject(bridge, cubeDeformation));
					}
					yield method.invoke(null, modelFlag);
				}
				case 2 -> method.invoke(null, cubeDeformationObject(bridge, cubeDeformation), modelFlag);
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
				case "createBoatModel" -> 8;
				case "createChestBoatModel" -> 9;
				case "createRaftModel" -> 10;
				case "createChestRaftModel" -> 11;
				case "createMesh" -> 12;
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
				Method polygonVerticesMethod,
				Field vertexXField,
				Field vertexYField,
				Field vertexZField,
				Field vertexUField,
				Field vertexVField
		) {
		}
	}

	private record RenderContext(
			BlueMapCameraRenderer.WorldSnapshot snapshot,
			ArrayTileModel model,
			MaterialResolver materialResolver
	) {
		private LightSample lightAt(float x, float y, float z) {
			LightData lightData = this.snapshot.sampleLight(BlockPos.containing(x, y, z));
			return new LightSample(
					lightData == null ? DEFAULT_LIGHT : lightData.getSkyLight(),
					lightData == null ? 0 : lightData.getBlockLight()
			);
		}
	}

	private record LightSample(int sky, int block) {
	}
}
