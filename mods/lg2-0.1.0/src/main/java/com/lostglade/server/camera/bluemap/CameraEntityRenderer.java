package com.lostglade.server.camera.bluemap;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariant;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.pig.PigVariant;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
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
	private static final Identifier WITHER_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/wither/wither");
	private static final Identifier WITHER_INVULNERABLE_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/wither/wither_invulnerable");
	private static final Identifier ENDER_DRAGON_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/enderdragon/dragon");
	private static final Identifier ENDER_DRAGON_EYES_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/enderdragon/dragon_eyes");
	private static final Identifier WARDEN_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/warden/warden");
	private static final Identifier SHEEP_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/sheep/sheep");
	private static final Identifier SHEEP_WOOL_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/sheep/sheep_wool");
	private static final Identifier ARMOR_STAND_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/armorstand/wood");
	private static final Identifier VILLAGER_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/villager/villager");
	private static final Identifier VILLAGER_TYPE_PLAINS_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/villager/type/plains");
	private static final String VILLAGER_TYPE_TEXTURE_PREFIX = "entity/villager/type/";
	private static final String VILLAGER_PROFESSION_TEXTURE_PREFIX = "entity/villager/profession/";
	private static final Map<String, BlueMapCameraRenderer.TextureMaterial> STATIC_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, BlueMapCameraRenderer.TextureMaterial> PLAYER_SKIN_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Identifier> ITEM_TEXTURE_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, TextureSize> TEXTURE_SIZE_CACHE = new ConcurrentHashMap<>();
	private static final Set<String> UNSUPPORTED_ENTITY_LOGGED = ConcurrentHashMap.newKeySet();
	private static final Set<String> SNAPSHOT_RENDER_ERRORS_LOGGED = ConcurrentHashMap.newKeySet();
	private static final Set<String> VILLAGER_OVERLAY_ISSUES_LOGGED = ConcurrentHashMap.newKeySet();

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
			boolean baby,
			boolean aggressive,
			boolean usingItem,
			HumanoidArm mainArm,
			HumanoidKind kind,
			Identifier texture,
			Identifier[] overlayTextures,
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
			float yaw,
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

	record WitherSnapshot(
			Vec3 position,
			float yaw,
			float pitch,
			float bodyYaw,
			float[] sideHeadYaws,
			float[] sideHeadPitches,
			float ageInTicks,
			int invulnerableTicks
	) implements EntitySnapshot {
	}

	record WardenSnapshot(
			Vec3 position,
			float yaw,
			float bodyYaw,
			float headYaw,
			float pitch,
			float walkPos,
			float walkSpeed,
			float ageInTicks,
			float tendrilAnimation
	) implements EntitySnapshot {
	}

	record EnderDragonSnapshot(
			Vec3 position,
			float yaw,
			float flapTime,
			Vec3 headPos,
			Vec3 neckPos,
			Vec3 bodyPos,
			Vec3 tail1Pos,
			Vec3 tail2Pos,
			Vec3 tail3Pos
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
			Identifier texture
	) implements EntitySnapshot {
	}

	record GenericLivingSnapshot(
			Vec3 position,
			float bodyYaw,
			float headYaw,
			float pitch,
			float width,
			float height,
			Identifier texture,
			String entityTypeId
	) implements EntitySnapshot {
	}

	record PlayerSkinSnapshot(
			String cacheKey,
			String url,
			Identifier fallbackTexture,
			boolean slim
	) {
	}

	private record TextureSize(int width, int height) {
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
		if (entity == null || !entity.isAlive() || entity.isInvisible()) {
			return null;
		}
		try {
			if (entity instanceof ItemEntity itemEntity) {
				return captureItemSnapshot(itemEntity);
			}

			if (entity instanceof LivingEntity livingEntity) {
				EntitySnapshot specialized = captureSpecializedLivingEntity(entity, livingEntity);
				if (specialized != null) {
					return specialized;
				}
				logUnsupportedEntity(entity, "using generic living fallback");
				return captureGenericLivingSnapshot(entity, livingEntity);
			}

			return null;
		} catch (Exception exception) {
			logUnsupportedEntity(entity, "capture failed: " + exception.getClass().getSimpleName());
			if (entity instanceof LivingEntity livingEntity) {
				return captureGenericLivingSnapshot(entity, livingEntity);
			}
			return null;
		}
	}

	private static EntitySnapshot captureSpecializedLivingEntity(Entity entity, LivingEntity livingEntity) {
		if (entity instanceof Player player) {
			PlayerSkinSnapshot playerSkin = capturePlayerSkin(player);
			HumanoidKind kind = playerSkin != null && playerSkin.slim() ? HumanoidKind.PLAYER_SLIM : HumanoidKind.PLAYER;
			float playerYaw = entity.getYRot();
			byte modelBits = player instanceof net.minecraft.world.entity.Avatar avatar
					? avatar.getEntityData().get(PlayerTrackedDataAccessor.lg2$getDataPlayerModeCustomisation())
					: 0;
			return new HumanoidSnapshot(
					entity.position(),
					playerYaw,
					playerYaw,
					playerYaw,
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
					false,
					false,
					livingEntity.isUsingItem(),
					player.getMainArm(),
					kind,
					playerSkin == null ? PLAYER_WIDE_FALLBACK : playerSkin.fallbackTexture(),
					new Identifier[0],
					playerSkin,
					modelBits
			);
		}

		if (entity instanceof ArmorStand armorStand) {
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

		if (entity instanceof Villager villager) {
			Identifier[] overlays = villagerOverlays(villager);
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
					((Zombie) entity).isBaby(),
					entity instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive(),
					livingEntity.isUsingItem(),
					livingEntity.getMainArm(),
					kind,
					texture,
					overlays,
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

		if (entity instanceof WitherBoss witherBoss) {
			return new WitherSnapshot(
					entity.position(),
					entity.getYRot(),
					entity.getXRot(),
					livingEntity.yBodyRot,
					copyHeadRotations(witherBoss.getHeadYRots()),
					copyHeadRotations(witherBoss.getHeadXRots()),
					entity.tickCount,
					witherBoss.getInvulnerableTicks()
			);
		}

		if (entity instanceof Warden warden) {
			return new WardenSnapshot(
					entity.position(),
					entity.getYRot(),
					livingEntity.yBodyRot,
					livingEntity.yHeadRot,
					entity.getXRot(),
					livingEntity.walkAnimation.position(),
					livingEntity.walkAnimation.speed(),
					entity.tickCount,
					warden.getTendrilAnimation(0.0F)
			);
		}

		if (entity instanceof EnderDragon dragon) {
			EnderDragonPart[] parts = dragon.getSubEntities();
			if (parts.length >= 6) {
				return new EnderDragonSnapshot(
						entity.position(),
						entity.getYRot(),
						dragon.flapTime,
						parts[0].position(),
						parts[1].position(),
						parts[2].position(),
						parts[3].position(),
						parts[4].position(),
						parts[5].position()
				);
			}
		}

		if (entity instanceof Creeper creeper) {
			return new CreeperSnapshot(
					entity.position(),
					entity.getYRot(),
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

		if (entity instanceof Cow cow) {
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

		return null;
	}

	private static ItemSnapshot captureItemSnapshot(ItemEntity itemEntity) {
		Identifier texture = resolveItemTexture(itemEntity.getItem());
		if (texture == null) {
			return null;
		}
		return new ItemSnapshot(
				itemEntity.position(),
				ItemEntity.getSpin(0.0F, itemEntity.bobOffs),
				texture
		);
	}

	private static GenericLivingSnapshot captureGenericLivingSnapshot(Entity entity, LivingEntity livingEntity) {
		return new GenericLivingSnapshot(
				entity.position(),
				livingEntity.yBodyRot,
				livingEntity.yHeadRot,
				entity.getXRot(),
				Math.max(0.2F, livingEntity.getBbWidth()),
				Math.max(0.2F, livingEntity.getBbHeight()),
				resolveFallbackEntityTexture(entity),
				entityTypeId(entity)
		);
	}

	private static Identifier resolveFallbackEntityTexture(Entity entity) {
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (typeId == null) {
			return null;
		}
		String namespace = typeId.getNamespace();
		String path = typeId.getPath();
		List<Identifier> candidates = List.of(
				Identifier.fromNamespaceAndPath(namespace, "entity/" + path + "/" + path),
				Identifier.fromNamespaceAndPath(namespace, "entity/" + path),
				Identifier.fromNamespaceAndPath(namespace, "entity/" + path + "/default"),
				Identifier.fromNamespaceAndPath(namespace, "entity/" + path + "/" + path + "_default")
		);
		for (Identifier candidate : candidates) {
			if (ASSETS.loadTexture(candidate) != null) {
				return candidate;
			}
		}
		return null;
	}

	private static String entityTypeId(Entity entity) {
		Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return key == null ? entity.getType().toString() : key.toString();
	}

	private static void logUnsupportedEntity(Entity entity, String reason) {
		String typeId = entityTypeId(entity);
		String key = typeId + "|" + reason;
		if (UNSUPPORTED_ENTITY_LOGGED.add(key)) {
			Lg2.LOGGER.info("Camera entity renderer fallback for {} ({})", typeId, reason);
		}
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
				livingEntity.isBaby(),
				livingEntity instanceof net.minecraft.world.entity.Mob mob && mob.isAggressive(),
				livingEntity.isUsingItem(),
				livingEntity.getMainArm(),
				kind,
				texture,
				overlays,
				null,
				(byte) 0
		);
	}

	private static Identifier[] villagerOverlays(Villager villager) {
		List<Identifier> overlays = new ArrayList<>(3);
		VillagerData villagerData = villager.getVillagerData();
		if (villagerData == null) {
			logVillagerOverlayIssue(villager, "missing VillagerData");
			Identifier fallbackType = requireVillagerOverlay(villager, VILLAGER_TYPE_PLAINS_TEXTURE, "type:plains");
			if (fallbackType != null) {
				overlays.add(fallbackType);
			}
			return overlays.toArray(new Identifier[0]);
		}

		Identifier typeTexture = resolveVillagerTypeTexture(villager, villagerData.type());
		if (typeTexture != null) {
			overlays.add(typeTexture);
		}

		Holder<VillagerProfession> professionHolder = villagerData.profession();
		Identifier professionId = holderIdentifier(professionHolder);
		Identifier professionTexture = resolveVillagerProfessionTexture(villager, professionId);
		if (professionTexture != null) {
			overlays.add(professionTexture);
		}

		if (professionId != null && !isNoProfession(professionId) && !isNitwitProfession(professionId)) {
			Identifier levelTexture = requireVillagerOverlay(villager, professionLevelTexture(villagerData.level()), "profession_level:" + villagerData.level());
			if (levelTexture != null) {
				overlays.add(levelTexture);
			}
		}

		return overlays.toArray(new Identifier[0]);
	}

	private static Identifier resolveVillagerTypeTexture(Villager villager, Holder<VillagerType> typeHolder) {
		Identifier typeId = holderIdentifier(typeHolder);
		if (typeId == null) {
			logVillagerOverlayIssue(villager, "missing villager type key");
			return requireVillagerOverlay(villager, VILLAGER_TYPE_PLAINS_TEXTURE, "type:plains");
		}
		Identifier textureId = Identifier.fromNamespaceAndPath(typeId.getNamespace(), VILLAGER_TYPE_TEXTURE_PREFIX + typeId.getPath());
		Identifier resolved = requireVillagerOverlay(villager, textureId, "type:" + typeId);
		if (resolved != null) {
			return resolved;
		}
		if (!typeId.equals(Identifier.fromNamespaceAndPath("minecraft", "plains"))) {
			Identifier plainsFallback = requireVillagerOverlay(villager, VILLAGER_TYPE_PLAINS_TEXTURE, "type:plains");
			if (plainsFallback != null) {
				logVillagerOverlayIssue(villager, "fallback villager type overlay " + typeId + " -> minecraft:plains");
			}
			return plainsFallback;
		}
		return null;
	}

	private static Identifier resolveVillagerProfessionTexture(Villager villager, Identifier professionId) {
		if (professionId == null) {
			logVillagerOverlayIssue(villager, "missing villager profession key");
			return null;
		}
		if (isNoProfession(professionId)) {
			return null;
		}
		Identifier textureId = Identifier.fromNamespaceAndPath(professionId.getNamespace(), VILLAGER_PROFESSION_TEXTURE_PREFIX + professionId.getPath());
		return requireVillagerOverlay(villager, textureId, "profession:" + professionId);
	}

	private static boolean isNoProfession(Identifier professionId) {
		return "minecraft".equals(professionId.getNamespace()) && "none".equals(professionId.getPath());
	}

	private static boolean isNitwitProfession(Identifier professionId) {
		return "minecraft".equals(professionId.getNamespace()) && "nitwit".equals(professionId.getPath());
	}

	private static Identifier holderIdentifier(Holder<?> holder) {
		if (holder == null) {
			return null;
		}
		Identifier fromHolder = holder.unwrapKey()
				.map(resourceKey -> resourceKey.identifier())
				.orElse(null);
		if (fromHolder != null) {
			return fromHolder;
		}
		Object value = holder.value();
		if (value instanceof VillagerType villagerType) {
			return BuiltInRegistries.VILLAGER_TYPE.getKey(villagerType);
		}
		if (value instanceof VillagerProfession villagerProfession) {
			return BuiltInRegistries.VILLAGER_PROFESSION.getKey(villagerProfession);
		}
		return null;
	}

	private static Identifier requireVillagerOverlay(Villager villager, Identifier textureId, String layer) {
		if (textureId == null) {
			return null;
		}
		if (ASSETS.loadTexture(textureId) != null) {
			return textureId;
		}
		logVillagerOverlayIssue(villager, "missing villager overlay " + layer + " -> " + textureId);
		return null;
	}

	private static void logVillagerOverlayIssue(Villager villager, String reason) {
		String key = entityTypeId(villager) + "|" + reason;
		if (VILLAGER_OVERLAY_ISSUES_LOGGED.add(key)) {
			Lg2.LOGGER.warn("Villager overlay issue: {} ({})", entityTypeId(villager), reason);
		}
	}

	private static float[] copyHeadRotations(float[] rotations) {
		if (rotations == null || rotations.length == 0) {
			return new float[0];
		}
		float[] copy = new float[rotations.length];
		System.arraycopy(rotations, 0, copy, 0, rotations.length);
		return copy;
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
			} catch (Exception exception) {
				String snapshotType = snapshotType(entitySnapshot);
				if (SNAPSHOT_RENDER_ERRORS_LOGGED.add(snapshotType)) {
					Lg2.LOGGER.warn("Camera entity renderer failed for snapshot type {}", snapshotType, exception);
				}
			}
		}
	}

	private static String snapshotType(EntitySnapshot snapshot) {
		if (snapshot == null) {
			return "null";
		}
		if (snapshot instanceof GenericLivingSnapshot genericLivingSnapshot) {
			return "generic_living:" + genericLivingSnapshot.entityTypeId();
		}
		return snapshot.getClass().getName();
	}

	private static void renderEntitySnapshot(RenderContext context, EntitySnapshot entitySnapshot) {
		if (entitySnapshot instanceof HumanoidSnapshot humanoidSnapshot) {
			renderHumanoid(context, humanoidSnapshot);
		} else if (entitySnapshot instanceof QuadrupedSnapshot quadrupedSnapshot) {
			renderQuadruped(context, quadrupedSnapshot);
		} else if (entitySnapshot instanceof ChickenSnapshot chickenSnapshot) {
			renderChicken(context, chickenSnapshot);
		} else if (entitySnapshot instanceof CreeperSnapshot creeperSnapshot) {
			renderCreeper(context, creeperSnapshot);
		} else if (entitySnapshot instanceof SpiderSnapshot spiderSnapshot) {
			renderSpider(context, spiderSnapshot);
		} else if (entitySnapshot instanceof WitherSnapshot witherSnapshot) {
			renderWither(context, witherSnapshot);
		} else if (entitySnapshot instanceof WardenSnapshot wardenSnapshot) {
			renderWarden(context, wardenSnapshot);
		} else if (entitySnapshot instanceof EnderDragonSnapshot enderDragonSnapshot) {
			renderEnderDragon(context, enderDragonSnapshot);
		} else if (entitySnapshot instanceof ArmorStandSnapshot armorStandSnapshot) {
			renderArmorStand(context, armorStandSnapshot);
		} else if (entitySnapshot instanceof ItemSnapshot itemSnapshot) {
			renderItem(context, itemSnapshot);
		} else if (entitySnapshot instanceof GenericLivingSnapshot genericLivingSnapshot) {
			renderGenericLiving(context, genericLivingSnapshot);
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

	private static TextureSize textureSize(Identifier textureId, int fallbackWidth, int fallbackHeight) {
		int safeFallbackWidth = Math.max(1, fallbackWidth);
		int safeFallbackHeight = Math.max(1, fallbackHeight);
		if (textureId == null) {
			return new TextureSize(safeFallbackWidth, safeFallbackHeight);
		}
		TextureSize cached = TEXTURE_SIZE_CACHE.get(textureId.toString());
		if (cached != null) {
			return cached;
		}
		BufferedImage image = ASSETS.loadTexture(textureId);
		TextureSize resolved = image == null
				? new TextureSize(safeFallbackWidth, safeFallbackHeight)
				: new TextureSize(Math.max(1, image.getWidth()), Math.max(1, image.getHeight()));
		TEXTURE_SIZE_CACHE.putIfAbsent(textureId.toString(), resolved);
		return resolved;
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

		renderHumanBase(context, snapshot, root, baseMaterial, texWidth, texHeight, headYaw, headPitch, rightArmPitch, leftArmPitch, rightLegPitch, leftLegPitch);

		if (snapshot.kind().outerLayers) {
			renderPlayerOuterLayers(context, snapshot, root, baseMaterial, headYaw, headPitch, rightArmPitch, leftArmPitch, rightLegPitch, leftLegPitch);
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
				renderHumanBase(context, snapshot, root, overlayMaterial, snapshot.kind().textureWidth, snapshot.kind().textureHeight, headYaw, headPitch, rightArmPitch, leftArmPitch, rightLegPitch, leftLegPitch);
			}
		}
	}

	private static Matrix4f humanoidRoot(Vec3 position, float bodyYaw, Pose pose, Direction sleepingDirection, float scale) {
		Matrix4f root = new Matrix4f().translate((float) position.x, (float) position.y, (float) position.z);
		if (sleepingDirection != null) {
			root.translate(0.0F, 0.2F, 0.0F);
			root.rotateY(radians(-bodyYaw));
			switch (sleepingDirection) {
				case NORTH -> root.rotateZ((float) Math.PI * 0.5F);
				case SOUTH -> root.rotateZ((float) -Math.PI * 0.5F);
				case WEST -> root.rotateX((float) -Math.PI * 0.5F);
				case EAST -> root.rotateX((float) Math.PI * 0.5F);
				default -> {
				}
			}
		} else {
			root.rotateY(radians(-bodyYaw));
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
			float rightLegPitch,
			float leftLegPitch
	) {
		if (snapshot.kind().villager) {
			renderVillagerBody(context, root, material, texWidth, texHeight, headYaw, headPitch, rightLegPitch, leftLegPitch);
			return;
		}

		float armWidth = snapshot.kind().armWidth;
		float legWidth = snapshot.kind().legWidth;

		Matrix4f head = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
		if (snapshot.kind() == HumanoidKind.PLAYER || snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			addPlayerHeadBox(context, head, -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, texWidth, texHeight, material, 0.0F);
		} else {
			addBox(context, head, -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, texWidth, texHeight, material, false, 0.0F);
		}

		addBox(context, root, -4.0F, 12.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 16, texWidth, texHeight, material, false, 0.0F);

		Matrix4f rightArm = rotateAround(root, -5.0F, 22.0F, 0.0F, rightArmPitch, 0.0F, 0.0F);
		Matrix4f leftArm = rotateAround(root, 5.0F, 22.0F, 0.0F, leftArmPitch, 0.0F, 0.0F);
		addBox(context, rightArm, -8.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 40, 16, texWidth, texHeight, material, false, 0.0F);
		if (snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			addBox(context, leftArm, 5.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 32, 48, texWidth, texHeight, material, false, 0.0F);
		} else {
			addBox(context, leftArm, 4.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, snapshot.kind() == HumanoidKind.SKELETON || snapshot.kind() == HumanoidKind.ENDERMAN ? 40 : 32, snapshot.kind() == HumanoidKind.SKELETON || snapshot.kind() == HumanoidKind.ENDERMAN ? 16 : 48, texWidth, texHeight, material, snapshot.kind() != HumanoidKind.PLAYER && snapshot.kind() != HumanoidKind.PLAYER_SLIM, 0.0F);
		}

		Matrix4f rightLeg = rotateAround(root, -2.0F, 12.0F, 0.0F, rightLegPitch, 0.0F, 0.0F);
		Matrix4f leftLeg = rotateAround(root, 2.0F, 12.0F, 0.0F, leftLegPitch, 0.0F, 0.0F);
		addBox(context, rightLeg, -4.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, false, 0.0F);
		if (snapshot.kind() == HumanoidKind.PLAYER || snapshot.kind() == HumanoidKind.PLAYER_SLIM) {
			addBox(context, leftLeg, 0.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 16, 48, texWidth, texHeight, material, false, 0.0F);
		} else {
			addBox(context, leftLeg, 0.0F, 0.0F, -2.0F, legWidth, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, true, 0.0F);
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
			float rightLegPitch,
			float leftLegPitch
	) {
		byte bits = snapshot.playerModelBits();
		if (showPlayerPart(bits, PlayerModelPart.HAT)) {
			Matrix4f head = rotateAround(root, 0.0F, 24.0F, 0.0F, headPitch, headYaw, 0.0F);
			addPlayerHeadBox(context, head, -4.0F, 24.0F, -4.0F, 8.0F, 8.0F, 8.0F, 32, 0, 64, 64, material, 0.5F);
		}
		if (showPlayerPart(bits, PlayerModelPart.JACKET)) {
			addBox(context, root, -4.0F, 12.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 32, 64, 64, material, false, 0.25F);
		}

		float armWidth = snapshot.kind() == HumanoidKind.PLAYER_SLIM ? 3.0F : 4.0F;
		Matrix4f rightArm = rotateAround(root, -5.0F, 22.0F, 0.0F, rightArmPitch, 0.0F, 0.0F);
		Matrix4f leftArm = rotateAround(root, 5.0F, 22.0F, 0.0F, leftArmPitch, 0.0F, 0.0F);
		if (showPlayerPart(bits, PlayerModelPart.RIGHT_SLEEVE)) {
			addBox(context, rightArm, -8.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 40, 32, 64, 64, material, false, 0.25F);
		}
		if (showPlayerPart(bits, PlayerModelPart.LEFT_SLEEVE)) {
			addBox(context, leftArm, snapshot.kind() == HumanoidKind.PLAYER_SLIM ? 5.0F : 4.0F, 12.0F, -2.0F, armWidth, 12.0F, 4.0F, 48, 48, 64, 64, material, false, 0.25F);
		}

		Matrix4f rightLeg = rotateAround(root, -2.0F, 12.0F, 0.0F, rightLegPitch, 0.0F, 0.0F);
		Matrix4f leftLeg = rotateAround(root, 2.0F, 12.0F, 0.0F, leftLegPitch, 0.0F, 0.0F);
		if (showPlayerPart(bits, PlayerModelPart.RIGHT_PANTS_LEG)) {
			addBox(context, rightLeg, -4.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 32, 64, 64, material, false, 0.25F);
		}
		if (showPlayerPart(bits, PlayerModelPart.LEFT_PANTS_LEG)) {
			addBox(context, leftLeg, 0.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 48, 64, 64, material, false, 0.25F);
		}
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
		addBox(context, head, -4.0F, 24.0F, -4.0F, 8.0F, 10.0F, 8.0F, 32, 0, texWidth, texHeight, material, false, 0.51F);
		addBox(context, head, -1.0F, 27.0F, -6.0F, 2.0F, 4.0F, 2.0F, 24, 0, texWidth, texHeight, material, false, 0.0F);
		addBox(context, root, -4.0F, 12.0F, -3.0F, 8.0F, 12.0F, 6.0F, 16, 20, texWidth, texHeight, material, false, 0.0F);
		addBox(context, root, -4.0F, 12.0F, -3.0F, 8.0F, 20.0F, 6.0F, 0, 38, texWidth, texHeight, material, false, 0.5F);

		Matrix4f arms = rotateAround(root, 0.0F, 22.0F, -1.0F, -0.75F, 0.0F, 0.0F);
		addBox(context, arms, -8.0F, 14.0F, -2.0F, 4.0F, 8.0F, 4.0F, 44, 22, texWidth, texHeight, material, false, 0.0F);
		addBox(context, arms, 4.0F, 14.0F, -2.0F, 4.0F, 8.0F, 4.0F, 44, 22, texWidth, texHeight, material, true, 0.0F);
		addBox(context, arms, -4.0F, 18.0F, -2.0F, 8.0F, 4.0F, 4.0F, 40, 38, texWidth, texHeight, material, false, 0.0F);

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
		TextureSize baseTextureSize = textureSize(snapshot.texture(), 64, 32);
		int texWidth = baseTextureSize.width();
		int texHeight = baseTextureSize.height();
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
		addBox(context, head, -4.0F, 8.0F, -14.0F, 8.0F, 8.0F, 8.0F, 0, 0, texWidth, texHeight, material, false, 0.0F);
		if (snapshot.kind() == QuadrupedKind.PIG) {
			addBox(context, head, -2.0F, 10.0F, -15.0F, 4.0F, 3.0F, 1.0F, 16, 16, texWidth, texHeight, material, false, 0.0F);
		}

		addBox(context, root, -5.0F, 6.0F, -8.0F, 10.0F, 8.0F, 16.0F, 28, 8, texWidth, texHeight, material, false, 0.0F);

		Matrix4f rightFront = rotateAround(root, -3.0F, 12.0F, -5.0F, frontLegPitch, 0.0F, 0.0F);
		Matrix4f leftFront = rotateAround(root, 3.0F, 12.0F, -5.0F, backLegPitch, 0.0F, 0.0F);
		Matrix4f rightBack = rotateAround(root, -3.0F, 12.0F, 7.0F, backLegPitch, 0.0F, 0.0F);
		Matrix4f leftBack = rotateAround(root, 3.0F, 12.0F, 7.0F, frontLegPitch, 0.0F, 0.0F);
		addBox(context, rightFront, -5.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, false, 0.0F);
		addBox(context, leftFront, 1.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, true, 0.0F);
		addBox(context, rightBack, -5.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, false, 0.0F);
		addBox(context, leftBack, 1.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, texWidth, texHeight, material, true, 0.0F);

		if (snapshot.kind() == QuadrupedKind.SHEEP && !snapshot.sheared() && snapshot.overlayTexture() != null) {
			int overlayMaterial = context.materialResolver.materialForTexture(snapshot.overlayTexture());
			TextureSize overlayTextureSize = textureSize(snapshot.overlayTexture(), texWidth, texHeight);
			int overlayTexWidth = overlayTextureSize.width();
			int overlayTexHeight = overlayTextureSize.height();
			addBox(context, head, -4.0F, 8.0F, -14.0F, 8.0F, 8.0F, 8.0F, 0, 0, overlayTexWidth, overlayTexHeight, overlayMaterial, false, 0.8F);
			addBox(context, root, -5.0F, 6.0F, -8.0F, 10.0F, 8.0F, 16.0F, 28, 8, overlayTexWidth, overlayTexHeight, overlayMaterial, false, 0.8F);
			addBox(context, rightFront, -5.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, overlayTexWidth, overlayTexHeight, overlayMaterial, false, 0.6F);
			addBox(context, leftFront, 1.0F, 0.0F, -7.0F, 4.0F, 12.0F, 4.0F, 0, 16, overlayTexWidth, overlayTexHeight, overlayMaterial, true, 0.6F);
			addBox(context, rightBack, -5.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, overlayTexWidth, overlayTexHeight, overlayMaterial, false, 0.6F);
			addBox(context, leftBack, 1.0F, 0.0F, 5.0F, 4.0F, 12.0F, 4.0F, 0, 16, overlayTexWidth, overlayTexHeight, overlayMaterial, true, 0.6F);
		}
	}

	private static void renderChicken(RenderContext context, ChickenSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(snapshot.texture());
		TextureSize chickenTextureSize = textureSize(snapshot.texture(), 64, 32);
		int texWidth = chickenTextureSize.width();
		int texHeight = chickenTextureSize.height();
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
		addBox(context, head, -2.0F, 13.0F, -6.0F, 4.0F, 6.0F, 3.0F, 0, 0, texWidth, texHeight, material, false, 0.0F);
		addBox(context, head, -1.0F, 14.0F, -7.0F, 2.0F, 2.0F, 1.0F, 14, 0, texWidth, texHeight, material, false, 0.0F);
		addBox(context, head, -1.0F, 12.0F, -7.0F, 2.0F, 2.0F, 1.0F, 14, 4, texWidth, texHeight, material, false, 0.0F);

		Matrix4f body = rotateAround(root, 0.0F, 10.0F, 0.0F, (float) Math.PI * 0.5F, 0.0F, 0.0F);
		addBox(context, body, -3.0F, 7.0F, -3.0F, 6.0F, 8.0F, 6.0F, 0, 9, texWidth, texHeight, material, false, 0.0F);

		Matrix4f rightWing = rotateAround(root, -3.0F, 13.0F, 0.0F, 0.0F, 0.0F, wingRoll);
		Matrix4f leftWing = rotateAround(root, 3.0F, 13.0F, 0.0F, 0.0F, 0.0F, -wingRoll);
		addBox(context, rightWing, -4.0F, 9.0F, -3.0F, 1.0F, 4.0F, 6.0F, 24, 13, texWidth, texHeight, material, false, 0.0F);
		addBox(context, leftWing, 3.0F, 9.0F, -3.0F, 1.0F, 4.0F, 6.0F, 24, 13, texWidth, texHeight, material, true, 0.0F);

		Matrix4f rightLeg = rotateAround(root, -1.0F, 5.0F, 1.0F, rightLegPitch, 0.0F, 0.0F);
		Matrix4f leftLeg = rotateAround(root, 1.0F, 5.0F, 1.0F, leftLegPitch, 0.0F, 0.0F);
		addBox(context, rightLeg, -1.5F, 0.0F, 0.0F, 3.0F, 5.0F, 3.0F, 26, 0, texWidth, texHeight, material, false, 0.0F);
		addBox(context, leftLeg, -1.5F, 0.0F, 0.0F, 3.0F, 5.0F, 3.0F, 26, 0, texWidth, texHeight, material, false, 0.0F);
	}

	private static void renderCreeper(RenderContext context, CreeperSnapshot snapshot) {
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.yaw()))
				.scale(snapshot.baby() ? 0.5F : 1.0F);
		float swellScale = 1.0F + snapshot.swelling() * 0.15F;
		root.scale(swellScale);
		int material = context.materialResolver.materialForTexture(CREEPER_TEXTURE);
		float walkPhase = snapshot.walkPos() * 0.6662F;
		float walkAmount = Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F);
		float frontLegPitch = Mth.cos(walkPhase) * 1.4F * walkAmount;
		float backLegPitch = Mth.cos(walkPhase + (float) Math.PI) * 1.4F * walkAmount;

		addBox(context, root, -4.0F, 18.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.0F);
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

	private static void renderWither(RenderContext context, WitherSnapshot snapshot) {
		Identifier texture = snapshot.invulnerableTicks() > 0 ? WITHER_INVULNERABLE_TEXTURE : WITHER_TEXTURE;
		int material = context.materialResolver.materialForTexture(texture);
		float scale = 2.0F;
		if (snapshot.invulnerableTicks() > 0) {
			scale -= (snapshot.invulnerableTicks() / 220.0F) * 0.5F;
		}
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y + 1.0F, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.bodyYaw()))
				.scale(scale);

		float sway = Mth.cos(snapshot.ageInTicks() * 0.1F);
		float ribPitch = (0.065F + 0.05F * sway) * (float) Math.PI;
		float tailPitch = (0.265F + 0.1F * sway) * (float) Math.PI;

		addBox(context, root, -10.0F, 3.9F, -1.5F, 20.0F, 3.0F, 3.0F, 0, 16, 64, 64, material, false, 0.0F);

		Matrix4f ribcage = rotateAround(root, -2.0F, 6.9F, -0.5F, ribPitch, 0.0F, 0.0F);
		addBox(context, ribcage, 0.0F, 0.0F, 0.0F, 3.0F, 10.0F, 3.0F, 0, 22, 64, 64, material, false, 0.0F);
		addBox(context, ribcage, -4.0F, 1.5F, 0.5F, 11.0F, 2.0F, 2.0F, 24, 22, 64, 64, material, false, 0.0F);
		addBox(context, ribcage, -4.0F, 4.0F, 0.5F, 11.0F, 2.0F, 2.0F, 24, 22, 64, 64, material, false, 0.0F);
		addBox(context, ribcage, -4.0F, 6.5F, 0.5F, 11.0F, 2.0F, 2.0F, 24, 22, 64, 64, material, false, 0.0F);

		float tailPivotY = 6.9F + Mth.cos(ribPitch) * 10.0F;
		float tailPivotZ = -0.5F + Mth.sin(ribPitch) * 10.0F;
		Matrix4f tail = rotateAround(root, -2.0F, tailPivotY, tailPivotZ, tailPitch, 0.0F, 0.0F);
		addBox(context, tail, 0.0F, 0.0F, 0.0F, 3.0F, 6.0F, 3.0F, 12, 22, 64, 64, material, false, 0.0F);

		Matrix4f centerHead = rotateAround(root, 0.0F, 0.0F, 0.0F, radians(snapshot.pitch()), radians(wrapDegrees(snapshot.yaw() - snapshot.bodyYaw())), 0.0F);
		addBox(context, centerHead, -4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 64, material, false, 0.0F);

		if (snapshot.sideHeadYaws().length > 0 && snapshot.sideHeadPitches().length > 0) {
			Matrix4f rightHead = rotateAround(root, -8.0F, 4.0F, 0.0F, radians(snapshot.sideHeadPitches()[0]), radians(wrapDegrees(snapshot.sideHeadYaws()[0] - snapshot.bodyYaw())), 0.0F);
			addBox(context, rightHead, -4.0F, -4.0F, -4.0F, 6.0F, 6.0F, 6.0F, 32, 0, 64, 64, material, false, 0.0F);
		}
		if (snapshot.sideHeadYaws().length > 1 && snapshot.sideHeadPitches().length > 1) {
			Matrix4f leftHead = rotateAround(root, 10.0F, 4.0F, 0.0F, radians(snapshot.sideHeadPitches()[1]), radians(wrapDegrees(snapshot.sideHeadYaws()[1] - snapshot.bodyYaw())), 0.0F);
			addBox(context, leftHead, -4.0F, -4.0F, -4.0F, 6.0F, 6.0F, 6.0F, 32, 0, 64, 64, material, false, 0.0F);
		}
	}

	private static void renderWarden(RenderContext context, WardenSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(WARDEN_TEXTURE);
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.bodyYaw()));

		float phase = snapshot.walkPos() * 0.8662F;
		float walkStrength = Math.min(0.5F, 3.0F * Mth.clamp(snapshot.walkSpeed(), 0.0F, 1.0F));
		float strideAccent = Math.min(0.35F, walkStrength);
		float age = snapshot.ageInTicks() * 0.1F;
		float phaseCos = Mth.cos(phase);
		float phaseSin = Mth.sin(phase);
		float idleCos = Mth.cos(age);
		float idleSin = Mth.sin(age);

		float headYaw = radians(wrapDegrees(snapshot.headYaw() - snapshot.bodyYaw()));
		float headPitch = radians(snapshot.pitch()) + 0.06F * idleSin + 1.2F * Mth.cos(phase + ((float) Math.PI * 0.5F)) * strideAccent;
		float headRoll = 0.06F * idleCos + 0.3F * phaseSin * walkStrength;
		float bodyPitch = 0.025F * idleCos + phaseCos * strideAccent;
		float bodyRoll = 0.025F * idleSin + 0.1F * phaseSin * walkStrength;
		float leftLegPitch = phaseCos * walkStrength;
		float rightLegPitch = Mth.cos(phase + (float) Math.PI) * walkStrength;
		float leftArmPitch = -0.8F * phaseCos * walkStrength;
		float rightArmPitch = -0.8F * phaseSin * walkStrength;
		float tendrilPitch = snapshot.tendrilAnimation() * (float) (Math.cos(snapshot.ageInTicks() * 2.25F) * Math.PI * 0.1F);

		Matrix4f body = rotateAround(root, 0.0F, 24.0F, 0.0F, bodyPitch, 0.0F, bodyRoll);
		addBox(context, body, -9.0F, 11.0F, -4.0F, 18.0F, 21.0F, 11.0F, 0, 0, 128, 128, material, false, 0.0F);
		addBox(context, body, -9.0F, 12.0F, -4.5F, 2.0F, 21.0F, 1.0F, 90, 11, 128, 128, material, false, 0.0F);
		addBox(context, body, 7.0F, 12.0F, -4.5F, 2.0F, 21.0F, 1.0F, 90, 11, 128, 128, material, true, 0.0F);

		Matrix4f head = rotateAround(body, 0.0F, 30.0F, 0.0F, headPitch, headYaw, headRoll);
		addBox(context, head, -8.0F, 22.0F, -5.0F, 16.0F, 16.0F, 10.0F, 0, 32, 128, 128, material, false, 0.0F);
		addBox(context, head, -16.0F, 21.0F, -0.5F, 8.0F, 16.0F, 1.0F, 52, 32, 128, 128, material, false, 0.0F);
		addBox(context, head, 8.0F, 21.0F, -0.5F, 8.0F, 16.0F, 1.0F, 58, 0, 128, 128, material, false, 0.0F);
		Matrix4f rightTendril = rotateAround(head, -8.0F, 30.0F, 0.0F, -tendrilPitch, 0.0F, 0.0F);
		Matrix4f leftTendril = rotateAround(head, 8.0F, 30.0F, 0.0F, tendrilPitch, 0.0F, 0.0F);
		addBox(context, rightTendril, -16.0F, 18.0F, -0.5F, 8.0F, 16.0F, 1.0F, 52, 32, 128, 128, material, false, 0.0F);
		addBox(context, leftTendril, 8.0F, 18.0F, -0.5F, 8.0F, 16.0F, 1.0F, 58, 0, 128, 128, material, false, 0.0F);

		Matrix4f rightArm = rotateAround(body, -13.0F, 27.0F, 1.0F, rightArmPitch, 0.0F, 0.0F);
		Matrix4f leftArm = rotateAround(body, 13.0F, 27.0F, 1.0F, leftArmPitch, 0.0F, 0.0F);
		addBox(context, rightArm, -17.0F, 13.0F, -3.0F, 8.0F, 28.0F, 8.0F, 44, 50, 128, 128, material, false, 0.0F);
		addBox(context, leftArm, 9.0F, 13.0F, -3.0F, 8.0F, 28.0F, 8.0F, 0, 58, 128, 128, material, false, 0.0F);

		Matrix4f rightLeg = rotateAround(root, -5.9F, 13.0F, 0.0F, rightLegPitch, 0.0F, 0.0F);
		Matrix4f leftLeg = rotateAround(root, 5.9F, 13.0F, 0.0F, leftLegPitch, 0.0F, 0.0F);
		addBox(context, rightLeg, -9.0F, 0.0F, -3.0F, 6.0F, 13.0F, 6.0F, 76, 48, 128, 128, material, false, 0.0F);
		addBox(context, leftLeg, 3.0F, 0.0F, -3.0F, 6.0F, 13.0F, 6.0F, 76, 76, 128, 128, material, false, 0.0F);
	}

	private static void renderEnderDragon(RenderContext context, EnderDragonSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(ENDER_DRAGON_TEXTURE);
		Matrix4f body = new Matrix4f()
				.translate((float) snapshot.bodyPos().x, (float) snapshot.bodyPos().y, (float) snapshot.bodyPos().z)
				.rotateY(radians(-snapshot.yaw()));

		addBox(context, body, -12.0F, -11.0F, -32.0F, 24.0F, 24.0F, 64.0F, 0, 0, 256, 256, material, false, 0.0F);
		addBox(context, body, -1.0F, -5.0F, -10.0F, 2.0F, 6.0F, 12.0F, 220, 53, 256, 256, material, false, 0.0F);
		addBox(context, body, -1.0F, -5.0F, 10.0F, 2.0F, 6.0F, 12.0F, 220, 53, 256, 256, material, false, 0.0F);
		addBox(context, body, -1.0F, -5.0F, 30.0F, 2.0F, 6.0F, 12.0F, 220, 53, 256, 256, material, false, 0.0F);

		float flap = snapshot.flapTime() * ((float) Math.PI * 2.0F);
		float wingXRot = -0.125F - Mth.cos(flap) * 0.2F;
		float wingZRot = -(Mth.sin(flap) + 0.125F) * 0.8F;
		float wingTipZRot = (Mth.sin(flap + 2.0F) + 0.5F) * 0.75F;

		Matrix4f leftWing = rotateAround(body, 12.0F, 2.0F, -6.0F, wingXRot, -0.25F, wingZRot);
		addBox(context, leftWing, 0.0F, -4.0F, -4.0F, 56.0F, 8.0F, 8.0F, 112, 88, 256, 256, material, true, 0.0F);
		addBox(context, leftWing, 0.0F, 0.0F, 2.0F, 56.0F, 1.0F, 56.0F, 112, 88, 256, 256, material, true, 0.0F);
		Matrix4f leftWingTip = rotateAround(leftWing, 56.0F, 0.0F, 0.0F, 0.0F, 0.0F, wingTipZRot);
		addBox(context, leftWingTip, 0.0F, -2.0F, -2.0F, 56.0F, 4.0F, 4.0F, 112, 136, 256, 256, material, true, 0.0F);
		addBox(context, leftWingTip, 0.0F, 0.0F, 2.0F, 56.0F, 1.0F, 56.0F, 112, 136, 256, 256, material, true, 0.0F);

		Matrix4f rightWing = rotateAround(body, -12.0F, 2.0F, -6.0F, wingXRot, 0.25F, -wingZRot);
		addBox(context, rightWing, -56.0F, -4.0F, -4.0F, 56.0F, 8.0F, 8.0F, 112, 88, 256, 256, material, false, 0.0F);
		addBox(context, rightWing, -56.0F, 0.0F, 2.0F, 56.0F, 1.0F, 56.0F, 112, 88, 256, 256, material, false, 0.0F);
		Matrix4f rightWingTip = rotateAround(rightWing, -56.0F, 0.0F, 0.0F, 0.0F, 0.0F, -wingTipZRot);
		addBox(context, rightWingTip, -56.0F, -2.0F, -2.0F, 56.0F, 4.0F, 4.0F, 112, 136, 256, 256, material, false, 0.0F);
		addBox(context, rightWingTip, -56.0F, 0.0F, 2.0F, 56.0F, 1.0F, 56.0F, 112, 136, 256, 256, material, false, 0.0F);

		renderDragonLegs(context, body, material);
		renderDragonSegment(context, snapshot.bodyPos(), snapshot.neckPos(), 10.0F, 10.0F, 192, 104, material);
		renderDragonSegment(context, snapshot.neckPos(), snapshot.headPos(), 10.0F, 10.0F, 192, 104, material);
		renderDragonSegment(context, snapshot.bodyPos(), snapshot.tail1Pos(), 12.0F, 12.0F, 192, 104, material);
		renderDragonSegment(context, snapshot.tail1Pos(), snapshot.tail2Pos(), 10.0F, 10.0F, 192, 104, material);
		renderDragonSegment(context, snapshot.tail2Pos(), snapshot.tail3Pos(), 8.0F, 8.0F, 192, 104, material);

		Vec3 headDirection = snapshot.headPos().subtract(snapshot.neckPos());
		Matrix4f head = orientedTransform(snapshot.headPos(), headDirection, 0.0F);
		addBox(context, head, -6.0F, -1.0F, -24.0F, 12.0F, 5.0F, 16.0F, 176, 44, 256, 256, material, false, 0.0F);
		addBox(context, head, -8.0F, -8.0F, -10.0F, 16.0F, 16.0F, 16.0F, 112, 30, 256, 256, material, false, 0.0F);
		addBox(context, head, -5.0F, -12.0F, -4.0F, 2.0F, 4.0F, 6.0F, 0, 0, 256, 256, material, false, 0.0F);
		addBox(context, head, 3.0F, -12.0F, -4.0F, 2.0F, 4.0F, 6.0F, 0, 0, 256, 256, material, false, 0.0F);
		addBox(context, head, -5.0F, -3.0F, -22.0F, 2.0F, 2.0F, 4.0F, 112, 0, 256, 256, material, false, 0.0F);
		addBox(context, head, 3.0F, -3.0F, -22.0F, 2.0F, 2.0F, 4.0F, 112, 0, 256, 256, material, false, 0.0F);
		Matrix4f jaw = rotateAround(head, 0.0F, 4.0F, -8.0F, (Mth.sin(flap) + 1.0F) * 0.2F, 0.0F, 0.0F);
		addBox(context, jaw, -6.0F, 0.0F, -16.0F, 12.0F, 4.0F, 16.0F, 176, 65, 256, 256, material, false, 0.0F);

		int eyeMaterial = context.materialResolver.materialForTexture(ENDER_DRAGON_EYES_TEXTURE);
		addBox(context, head, -8.0F, -8.0F, -10.0F, 16.0F, 16.0F, 16.0F, 112, 30, 256, 256, eyeMaterial, false, 0.05F, 15, 15);
	}

	private static void renderDragonLegs(RenderContext context, Matrix4f body, int material) {
		Matrix4f leftFrontLeg = rotateAround(body, 12.0F, 17.0F, -6.0F, 1.3F, 0.0F, 0.0F);
		addBox(context, leftFrontLeg, -4.0F, -4.0F, -4.0F, 8.0F, 24.0F, 8.0F, 112, 104, 256, 256, material, false, 0.0F);
		Matrix4f leftFrontTip = rotateAround(leftFrontLeg, 0.0F, 20.0F, -1.0F, -0.5F, 0.0F, 0.0F);
		addBox(context, leftFrontTip, -3.0F, -1.0F, -3.0F, 6.0F, 24.0F, 6.0F, 226, 138, 256, 256, material, false, 0.0F);
		Matrix4f leftFrontFoot = rotateAround(leftFrontTip, 0.0F, 23.0F, 0.0F, 0.75F, 0.0F, 0.0F);
		addBox(context, leftFrontFoot, -4.0F, 0.0F, -12.0F, 8.0F, 4.0F, 16.0F, 144, 104, 256, 256, material, false, 0.0F);

		Matrix4f rightFrontLeg = rotateAround(body, -12.0F, 17.0F, -6.0F, 1.3F, 0.0F, 0.0F);
		addBox(context, rightFrontLeg, -4.0F, -4.0F, -4.0F, 8.0F, 24.0F, 8.0F, 112, 104, 256, 256, material, false, 0.0F);
		Matrix4f rightFrontTip = rotateAround(rightFrontLeg, 0.0F, 20.0F, -1.0F, -0.5F, 0.0F, 0.0F);
		addBox(context, rightFrontTip, -3.0F, -1.0F, -3.0F, 6.0F, 24.0F, 6.0F, 226, 138, 256, 256, material, false, 0.0F);
		Matrix4f rightFrontFoot = rotateAround(rightFrontTip, 0.0F, 23.0F, 0.0F, 0.75F, 0.0F, 0.0F);
		addBox(context, rightFrontFoot, -4.0F, 0.0F, -12.0F, 8.0F, 4.0F, 16.0F, 144, 104, 256, 256, material, false, 0.0F);

		Matrix4f leftRearLeg = rotateAround(body, 16.0F, 13.0F, 34.0F, 1.0F, 0.0F, 0.0F);
		addBox(context, leftRearLeg, -8.0F, -4.0F, -8.0F, 16.0F, 32.0F, 16.0F, 0, 0, 256, 256, material, false, 0.0F);
		Matrix4f leftRearTip = rotateAround(leftRearLeg, 0.0F, 32.0F, -4.0F, 0.5F, 0.0F, 0.0F);
		addBox(context, leftRearTip, -6.0F, -2.0F, 0.0F, 12.0F, 32.0F, 12.0F, 196, 0, 256, 256, material, false, 0.0F);
		Matrix4f leftRearFoot = rotateAround(leftRearTip, 0.0F, 31.0F, 4.0F, 0.75F, 0.0F, 0.0F);
		addBox(context, leftRearFoot, -9.0F, 0.0F, -20.0F, 18.0F, 6.0F, 24.0F, 112, 0, 256, 256, material, false, 0.0F);

		Matrix4f rightRearLeg = rotateAround(body, -16.0F, 13.0F, 34.0F, 1.0F, 0.0F, 0.0F);
		addBox(context, rightRearLeg, -8.0F, -4.0F, -8.0F, 16.0F, 32.0F, 16.0F, 0, 0, 256, 256, material, false, 0.0F);
		Matrix4f rightRearTip = rotateAround(rightRearLeg, 0.0F, 32.0F, -4.0F, 0.5F, 0.0F, 0.0F);
		addBox(context, rightRearTip, -6.0F, -2.0F, 0.0F, 12.0F, 32.0F, 12.0F, 196, 0, 256, 256, material, false, 0.0F);
		Matrix4f rightRearFoot = rotateAround(rightRearTip, 0.0F, 31.0F, 4.0F, 0.75F, 0.0F, 0.0F);
		addBox(context, rightRearFoot, -9.0F, 0.0F, -20.0F, 18.0F, 6.0F, 24.0F, 112, 0, 256, 256, material, false, 0.0F);
	}

	private static void renderDragonSegment(RenderContext context, Vec3 start, Vec3 end, float width, float height, int texU, int texV, int material) {
		Vec3 delta = end.subtract(start);
		if (delta.lengthSqr() < 1.0E-4D) {
			return;
		}
		float length = Math.max(6.0F, (float) (start.distanceTo(end) * 16.0D));
		Matrix4f transform = orientedTransform(midpoint(start, end), delta, 0.0F);
		addBox(context, transform, -width * 0.5F, -height * 0.5F, -length * 0.5F, width, height, length, texU, texV, 256, 256, material, false, 0.0F);
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
		int material = context.materialResolver.materialForTexture(snapshot.texture());
		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y + 0.125F, (float) snapshot.position().z)
				.rotateY(snapshot.spin());
		addPlane(context, root, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
		Matrix4f crossed = new Matrix4f(root).rotateY((float) Math.PI * 0.5F);
		addPlane(context, crossed, -0.25F, 0.0F, 0.0F, 0.5F, 0.5F, material);
	}

	private static void renderGenericLiving(RenderContext context, GenericLivingSnapshot snapshot) {
		int material = context.materialResolver.materialForTexture(snapshot.texture());
		TextureSize textureSize = textureSize(snapshot.texture(), 64, 64);
		int texWidth = textureSize.width();
		int texHeight = textureSize.height();

		float widthPx = Mth.clamp(snapshot.width() * 16.0F, 4.0F, 24.0F);
		float depthPx = widthPx;
		float heightPx = Mth.clamp(snapshot.height() * 16.0F, 6.0F, 48.0F);

		Matrix4f root = new Matrix4f()
				.translate((float) snapshot.position().x, (float) snapshot.position().y, (float) snapshot.position().z)
				.rotateY(radians(-snapshot.bodyYaw()));

		addBox(
				context,
				root,
				-widthPx * 0.5F,
				0.0F,
				-depthPx * 0.5F,
				widthPx,
				heightPx,
				depthPx,
				16,
				16,
				texWidth,
				texHeight,
				material,
				false,
				0.0F
		);

		float headSize = Mth.clamp(widthPx * 0.95F, 4.0F, 12.0F);
		float headPivotY = Math.max(headSize, heightPx * 0.75F);
		Matrix4f head = rotateAround(
				root,
				0.0F,
				headPivotY,
				0.0F,
				radians(snapshot.pitch()),
				radians(wrapDegrees(snapshot.headYaw() - snapshot.bodyYaw())),
				0.0F
		);
		addBox(
				context,
				head,
				-headSize * 0.5F,
				heightPx - headSize * 0.2F,
				-headSize * 0.5F,
				headSize,
				headSize,
				headSize,
				0,
				0,
				texWidth,
				texHeight,
				material,
				false,
				0.0F
		);
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

	private static Matrix4f orientedTransform(Vec3 position, Vec3 direction, float roll) {
		if (direction.lengthSqr() < 1.0E-4D) {
			return new Matrix4f().translate((float) position.x, (float) position.y, (float) position.z);
		}
		double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		float yaw = (float) Math.atan2(-direction.x, direction.z);
		float pitch = (float) -Math.atan2(direction.y, horizontal);
		return new Matrix4f()
				.translate((float) position.x, (float) position.y, (float) position.z)
				.rotateY(yaw)
				.rotateX(pitch)
				.rotateZ(roll);
	}

	private static Vec3 midpoint(Vec3 a, Vec3 b) {
		return new Vec3(
				(a.x + b.x) * 0.5D,
				(a.y + b.y) * 0.5D,
				(a.z + b.z) * 0.5D
		);
	}

	private static void addPlane(RenderContext context, Matrix4f transform, float x, float y, float z, float width, float height, int material) {
		Vector3f v0 = transformPosition(transform, x, y, z);
		Vector3f v1 = transformPosition(transform, x + width, y, z);
		Vector3f v2 = transformPosition(transform, x + width, y + height, z);
		Vector3f v3 = transformPosition(transform, x, y + height, z);
		LightSample lightSample = context.lightAt((v0.x + v2.x) * 0.5F, (v0.y + v2.y) * 0.5F, (v0.z + v2.z) * 0.5F);
		addQuad(context, v0, v1, v2, v3, 0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, material, lightSample.sky(), lightSample.block(), 1.0F, 1.0F, 1.0F);
	}

	// Player head uses the same atlas strip as vanilla, but side faces must stay bound to
	// their dedicated left/right slots for both base head and hat overlay.
	private static void addPlayerHeadBox(
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
			float inflate
	) {
		float minX = x - inflate;
		float minY = y - inflate;
		float minZ = z - inflate;
		float maxX = x + width + inflate;
		float maxY = y + height + inflate;
		float maxZ = z + depth + inflate;

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

		addVanillaQuad(context, new Vector3f[]{pnp, nnp, nnn, pnn}, uv(u1, texWidth), uv(u2, texWidth), uv(v0, texHeight), uv(v1, texHeight), false, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{ppn, npn, npp, ppp}, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v1, texHeight), uv(v0, texHeight), false, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{nnn, nnp, npp, npn}, uv(u2, texWidth), uv(u3, texWidth), uv(v1, texHeight), uv(v2, texHeight), false, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{pnn, nnn, npn, ppn}, uv(u1, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), false, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{pnp, pnn, ppn, ppp}, uv(u0, texWidth), uv(u1, texWidth), uv(v1, texHeight), uv(v2, texHeight), false, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{nnp, pnp, ppp, npp}, uv(u3, texWidth), uv(u4, texWidth), uv(v1, texHeight), uv(v2, texHeight), false, material, skyLight, blockLight);
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

		addVanillaQuad(context, new Vector3f[]{pnp, nnp, nnn, pnn}, uv(u1, texWidth), uv(u2, texWidth), uv(v0, texHeight), uv(v1, texHeight), mirror, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{ppn, npn, npp, ppp}, uv(u2, texWidth), uv(u2 + width, texWidth), uv(v1, texHeight), uv(v0, texHeight), mirror, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{nnn, nnp, npp, npn}, uv(u0, texWidth), uv(u1, texWidth), uv(v1, texHeight), uv(v2, texHeight), mirror, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{pnn, nnn, npn, ppn}, uv(u1, texWidth), uv(u2, texWidth), uv(v1, texHeight), uv(v2, texHeight), mirror, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{pnp, pnn, ppn, ppp}, uv(u2, texWidth), uv(u3, texWidth), uv(v1, texHeight), uv(v2, texHeight), mirror, material, skyLight, blockLight);
		addVanillaQuad(context, new Vector3f[]{nnp, pnp, ppp, npp}, uv(u3, texWidth), uv(u4, texWidth), uv(v1, texHeight), uv(v2, texHeight), mirror, material, skyLight, blockLight);
	}

	private static float uv(float value, int size) {
		return value / size;
	}

	private static Vector3f transformPosition(Matrix4f transform, float x, float y, float z) {
		return transform.transformPosition(new Vector3f(x, y, z));
	}

	private static void addVanillaQuad(
			RenderContext context,
			Vector3f[] vertices,
			float left,
			float right,
			float top,
			float bottom,
			boolean mirror,
			int material,
			int skyLight,
			int blockLight
	) {
		float[] uvs = new float[]{right, left, left, right};
		float[] vvs = new float[]{top, top, bottom, bottom};
		if (mirror) {
			reverse(vertices);
			reverse(uvs);
			reverse(vvs);
		}
		addQuad(
				context,
				vertices[0], vertices[1], vertices[2], vertices[3],
				uvs[0], vvs[0],
				uvs[1], vvs[1],
				uvs[2], vvs[2],
				uvs[3], vvs[3],
				material, skyLight, blockLight,
				1.0F, 1.0F, 1.0F
		);
	}

	private static void reverse(Vector3f[] values) {
		for (int i = 0; i < values.length / 2; i++) {
			Vector3f swap = values[i];
			values[i] = values[values.length - 1 - i];
			values[values.length - 1 - i] = swap;
		}
	}

	private static void reverse(float[] values) {
		for (int i = 0; i < values.length / 2; i++) {
			float swap = values[i];
			values[i] = values[values.length - 1 - i];
			values[values.length - 1 - i] = swap;
		}
	}

	private static void addQuad(
			RenderContext context,
			Vector3f a,
			Vector3f b,
			Vector3f c,
			Vector3f d,
			float ua,
			float va,
			float ub,
			float vb,
			float uc,
			float vc,
			float ud,
			float vd,
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
				.setUvs(triangle, ua, va, ub, vb, uc, vc)
				.setAOs(triangle, 1.0F, 1.0F, 1.0F)
				.setColor(triangle, red, green, blue)
				.setSunlight(triangle, skyLight)
				.setBlocklight(triangle, blockLight)
				.setMaterialIndex(triangle, material);
		context.model
				.setPositions(triangle + 1, a.x, a.y, a.z, c.x, c.y, c.z, d.x, d.y, d.z)
				.setUvs(triangle + 1, ua, va, uc, vc, ud, vd)
				.setAOs(triangle + 1, 1.0F, 1.0F, 1.0F)
				.setColor(triangle + 1, red, green, blue)
				.setSunlight(triangle + 1, skyLight)
				.setBlocklight(triangle + 1, blockLight)
				.setMaterialIndex(triangle + 1, material);
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

	private static Identifier professionLevelTexture(int level) {
		return switch (Mth.clamp(level, 1, 5)) {
			case 1 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/stone");
			case 2 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/iron");
			case 3 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/gold");
			case 4 -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/emerald");
			default -> Identifier.fromNamespaceAndPath("minecraft", "entity/villager/profession_level/diamond");
		};
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
