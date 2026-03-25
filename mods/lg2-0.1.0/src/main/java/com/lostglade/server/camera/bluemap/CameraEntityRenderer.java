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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.Equippable;
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

		int materialForImage(String cacheKey, BufferedImage image);
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

	record FixedItemSnapshot(
			Vec3 position,
			float yaw,
			float pitch,
			float roll,
			float scale,
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

	record ItemFrameSnapshot(
			Vec3 position,
			Direction direction,
			boolean glow,
			boolean invisible,
			int rotation,
			boolean mapFrame,
			ItemVisual item,
			FramedMapSnapshot map
	) implements EntitySnapshot {
	}

	record FramedMapSnapshot(
			int mapId,
			byte[] colors
	) {
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

	record ImagePlaneSnapshot(
			Vec3 position,
			Matrix4f transform,
			float x,
			float y,
			float z,
			float width,
			float height,
			String materialKey,
			BufferedImage image,
			boolean emissive
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
		BOAT,
		BLOCK_ENTITY
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
			List<ItemModelElement> elements,
			Map<ItemDisplayTransformContext, ItemModelTransform> transforms
		) {
	}

	private record ItemModelTransform(
			float rotationX,
			float rotationY,
			float rotationZ,
			float translationX,
			float translationY,
			float translationZ,
			float scaleX,
			float scaleY,
			float scaleZ
	) {
		private static final ItemModelTransform IDENTITY = new ItemModelTransform(
				0.0F,
				0.0F,
				0.0F,
				0.0F,
				0.0F,
				0.0F,
				1.0F,
				1.0F,
				1.0F
		);
	}

	private enum ItemDisplayTransformContext {
		THIRD_PERSON_RIGHT_HAND,
		THIRD_PERSON_LEFT_HAND,
		GROUND,
		FIXED,
		FRAMED
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
		ItemVisual item = resolveItemVisual(itemFrame.getItem());
		if (item == null) {
			item = new ItemVisual(null, null);
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

	private static EntitySnapshot captureBlockEntityAsFixedItem(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
		net.minecraft.world.level.block.state.BlockState state = blockEntity.getBlockState();
		if (state == null) {
			return null;
		}
		Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		if (blockId == null || !blockEntityShouldUseItemFallback(blockId)) {
			return null;
		}
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(state.getBlock());
		ItemVisual visual = resolveItemVisual(stack);
		if (visual == null) {
			return null;
		}
		Vec3 position = Vec3.atCenterOf(blockEntity.getBlockPos()).add(0.0D, -0.25D, 0.0D);
		return new FixedItemSnapshot(position, 180.0F, 0.0F, 0.0F, 1.0F, visual);
	}

	private static boolean blockEntityShouldUseItemFallback(Identifier blockId) {
		String path = blockId.getPath();
		return path.equals("decorated_pot")
				|| path.equals("bell")
				|| path.equals("conduit")
				|| path.equals("spawner")
				|| path.equals("vault")
				|| path.equals("trial_spawner")
				|| path.endsWith("shulker_box");
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
		} else if (entitySnapshot instanceof FixedItemSnapshot fixedItemSnapshot) {
			renderFixedItem(context, fixedItemSnapshot);
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
		int material = context.materialResolver.materialForImage(snapshot.materialKey(), snapshot.image());
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
		int mapMaterial = context.materialResolver.materialForImage("framed_map:" + map.mapId(), framedMapImage(map));
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
		if (visual.model() != null && !visual.model().elements().isEmpty()) {
			if (transformContext == ItemDisplayTransformContext.FRAMED || transformContext == ItemDisplayTransformContext.GROUND) {
				renderDisplayedItemModel(context, root, visual.model(), transformContext);
			} else {
				renderItemModel(context, root, visual.model());
			}
			return;
		}
		if (visual.flatTexture() == null) {
			return;
		}
		int material = context.materialResolver.materialForTexture(visual.flatTexture());
		if (transformContext == ItemDisplayTransformContext.FRAMED) {
			Matrix4f transformedRoot = applyFlatFramedItemTransform(root, visual.model());
			renderFlatItemLayers(context, transformedRoot, visual, 0.0625F, 0.0625F, 0.5F, 0.875F, 0.875F);
			return;
		}
		if (transformContext == ItemDisplayTransformContext.GROUND) {
			Matrix4f transformedRoot = applyItemDisplayTransform(root, visual.model(), transformContext);
			renderFlatItemLayers(context, transformedRoot, visual, 0.0F, 0.0F, 0.5F, 1.0F, 1.0F);
			return;
		}
		addDoubleSidedPlane(context, root, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
		if (transformContext == ItemDisplayTransformContext.FIXED) {
			return;
		}
		Matrix4f crossed = new Matrix4f(root).rotateY((float) Math.PI * 0.5F);
		addDoubleSidedPlane(context, crossed, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
	}

	private static void renderItemModel(RenderContext context, Matrix4f root, ResolvedItemModel model) {
		Matrix4f transform = new Matrix4f(root).translate(-0.25F, 0.0F, -0.25F).scale(0.5F);
		for (ItemModelElement element : model.elements()) {
			renderItemModelElement(context, transform, model, element);
		}
	}

	private static void renderDisplayedItemModel(RenderContext context, Matrix4f root, ResolvedItemModel model, ItemDisplayTransformContext transformContext) {
		Matrix4f transform = applyItemDisplayTransform(root, model, transformContext);
		for (ItemModelElement element : model.elements()) {
			renderItemModelElement(context, transform, model, element);
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
		float halfThickness = 1.0F / 64.0F;
		float startCenterZ = centerZ - (layers.size() - 1) * layerSpacing * 0.5F;
		for (int i = 0; i < layers.size(); i++) {
			Identifier layer = layers.get(i);
			int material = context.materialResolver.materialForTexture(layer);
			float layerCenterZ = startCenterZ + i * layerSpacing;
			addSeparatedTexturedDoubleSidedPlane(
					context,
					transform,
					x,
					y,
					layerCenterZ,
					width,
					height,
					halfThickness,
					0.0F,
					0.0F,
					1.0F,
					1.0F,
					material,
					1.0F,
					1.0F,
					1.0F
			);
		}
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
		return ItemModelTransform.IDENTITY;
	}

	private static void renderBlockEntityModel(RenderContext context, Matrix4f root, ResolvedItemModel model) {
		Matrix4f transform = new Matrix4f(root).translate(-0.5F, -0.5F, -0.5F);
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
		addTexturedPlane(context, transform, x, y, centerZ + halfThickness, width, height, u0, v0, u1, v1, material, red, green, blue);
		Matrix4f back = new Matrix4f(transform).rotateY((float) Math.PI);
		addTexturedPlane(context, back, -(x + width), y, -(centerZ - halfThickness), width, height, u0, v0, u1, v1, material, red, green, blue);
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
		addQuad(context, p0, p1, p2, p3, texU0, texU1, texV0, texV1, material, lightSample.sky(), lightSample.block(), red, green, blue);
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

		addQuad(context, pnp, nnp, npp, ppp, uv(u2, texWidth), uv(u1, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnn, pnn, ppn, npn, uv(u4, texWidth), uv(u3, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, nnp, nnn, npn, npp, uv(u1, texWidth), uv(u0, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, pnp, ppp, ppn, uv(u3, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, pnn, nnn, nnp, pnp, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, npn, ppn, ppp, npp, uv(u1, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v0, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
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
		addQuad(context, pnn, nnn, nnp, pnp, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
		addQuad(context, npn, ppn, ppp, npp, uv(u1, texWidth), uv(u2, texWidth), uv(v0, texHeight), uv(v1, texHeight), material, skyLight, blockLight, 1.0F, 1.0F, 1.0F);
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
		Identifier rootModelId = resolveItemRootModelId(itemId);
		ResolvedItemModel model = rootModelId == null ? null : resolveItemModel(rootModelId, new HashSet<>());
		Identifier flatTexture = resolveItemTexture(stack);
		if (flatTexture == null && model != null) {
			flatTexture = primaryTexture(model);
		}
		boolean hasRenderableItemVisual = flatTexture != null || (model != null && !model.elements().isEmpty());
		if (!hasRenderableItemVisual && stack.getItem() instanceof BlockItem blockItem) {
			Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
			if (blockId != null) {
				model = resolveItemModel(blockId.withPrefix("block/"), new HashSet<>());
			}
		}
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
		Identifier rootModelId = resolveItemRootModelId(itemId);
		return rootModelId == null ? null : resolveModelTexture(rootModelId, Set.of());
	}

	private static Identifier resolveItemRootModelId(Identifier itemId) {
		JsonObject itemDefinition = ASSETS.loadJsonAsset("assets/" + itemId.getNamespace() + "/items/" + itemId.getPath() + ".json");
		if (itemDefinition != null && itemDefinition.has("model") && itemDefinition.get("model").isJsonObject()) {
			Identifier definitionModel = resolveItemDefinitionModelId(itemDefinition.getAsJsonObject("model"));
			if (definitionModel != null) {
				return definitionModel;
			}
		}
		return itemId.withPrefix("item/");
	}

	private static Identifier resolveItemDefinitionModelId(JsonObject modelObject) {
		if (modelObject == null || !modelObject.has("type")) {
			return null;
		}
		String type = modelObject.get("type").getAsString();
		if ("minecraft:model".equals(type) && modelObject.has("model")) {
			return Identifier.tryParse(modelObject.get("model").getAsString());
		}
		if ("minecraft:condition".equals(type)) {
			if (modelObject.has("on_false") && modelObject.get("on_false").isJsonObject()) {
				Identifier falseModel = resolveItemDefinitionModelId(modelObject.getAsJsonObject("on_false"));
				if (falseModel != null) {
					return falseModel;
				}
			}
			if (modelObject.has("on_true") && modelObject.get("on_true").isJsonObject()) {
				return resolveItemDefinitionModelId(modelObject.getAsJsonObject("on_true"));
			}
		}
		return null;
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
							faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0
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
		readItemModelTransform(displayJson, "ground", ItemDisplayTransformContext.GROUND, transforms);
		readItemModelTransform(displayJson, "fixed", ItemDisplayTransformContext.FIXED, transforms);
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
					if (!bridge.layerDefinitionClass.equals(method.getReturnType()) && !bridge.meshDefinitionClass.equals(method.getReturnType())) {
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
				Object result = invokeLayerFactory(bridge, method, cubeDeformation, modelFlag, factoryDescriptor.argument());
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

		private static boolean isSupportedLayerFactory(RuntimeBridge bridge, Method method, String explicitArgument) {
			Class<?>[] parameterTypes = method.getParameterTypes();
			return switch (parameterTypes.length) {
				case 0 -> true;
				case 1 -> bridge.cubeDeformationClass.equals(parameterTypes[0])
						|| parameterTypes[0] == boolean.class
						|| (parameterTypes[0].isEnum() && explicitArgument != null);
				case 2 -> (bridge.cubeDeformationClass.equals(parameterTypes[0]) && parameterTypes[1] == boolean.class)
						|| (parameterTypes[0] == boolean.class && bridge.cubeDeformationClass.equals(parameterTypes[1]));
				default -> false;
			};
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		private static Object invokeLayerFactory(RuntimeBridge bridge, Method method, float cubeDeformation, boolean modelFlag, String explicitArgument) throws ReflectiveOperationException {
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
					Object first = bridge.cubeDeformationClass.equals(parameterTypes[0])
							? cubeDeformationObject(bridge, cubeDeformation)
							: modelFlag;
					Object second = bridge.cubeDeformationClass.equals(parameterTypes[1])
							? cubeDeformationObject(bridge, cubeDeformation)
							: modelFlag;
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
