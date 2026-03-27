package com.lostglade.server.camera.bluemap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CameraEntityFixups {
	private static final Identifier ENDERMAN_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/enderman/enderman");
	private static final Identifier ENDERMAN_EYES_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/enderman/enderman_eyes");
	private static final Identifier ZOMBIFIED_PIGLIN_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/piglin/zombified_piglin");
	private static final Identifier ZOMBIE_VILLAGER_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/zombie_villager/zombie_villager");
	private static final Identifier STRAY_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/skeleton/stray");
	private static final Identifier STRAY_OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/skeleton/stray_overlay");
	private static final Identifier WITHER_SKELETON_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/skeleton/wither_skeleton");
	private static final Identifier BOGGED_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/skeleton/bogged");
	private static final Identifier BOGGED_OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "entity/skeleton/bogged_overlay");

	private CameraEntityFixups() {
	}

	static EntitySnapshot captureManualEntityFixup(Entity entity, LivingEntity livingEntity) {
		if (livingEntity == null) {
			return null;
		}
		if (entity instanceof EnderMan enderMan) {
			return captureEndermanManual(enderMan);
		}
		if (entity instanceof ZombieVillager zombieVillager) {
			return captureZombieVillagerManual(zombieVillager, livingEntity);
		}
		if (entity instanceof ZombifiedPiglin) {
			return CameraEntityRenderer.humanoidSnapshot(livingEntity, HumanoidKind.ZOMBIE, ZOMBIFIED_PIGLIN_TEXTURE, new Identifier[0]);
		}
		if (entity instanceof Bogged bogged) {
			Identifier[] overlays = bogged.isSheared() ? new Identifier[0] : new Identifier[]{BOGGED_OVERLAY_TEXTURE};
			return CameraEntityRenderer.humanoidSnapshot(livingEntity, HumanoidKind.SKELETON, BOGGED_TEXTURE, overlays);
		}
		if (entity instanceof net.minecraft.world.entity.monster.skeleton.Stray) {
			return CameraEntityRenderer.humanoidSnapshot(livingEntity, HumanoidKind.SKELETON, STRAY_TEXTURE, new Identifier[]{STRAY_OVERLAY_TEXTURE});
		}
		if (entity instanceof net.minecraft.world.entity.monster.skeleton.WitherSkeleton) {
			return CameraEntityRenderer.humanoidSnapshot(livingEntity, HumanoidKind.SKELETON, WITHER_SKELETON_TEXTURE, new Identifier[0]);
		}
		return null;
	}

	static boolean usesManualHumanoidBase(LivingEntity livingEntity) {
		if (livingEntity == null) {
			return false;
		}
		if (livingEntity instanceof net.minecraft.world.entity.player.Player || livingEntity instanceof net.minecraft.world.entity.monster.EnderMan) {
			return true;
		}
		if (livingEntity instanceof Zombie && shouldUseManualZombieFamily(livingEntity)) {
			return true;
		}
		return livingEntity instanceof net.minecraft.world.entity.monster.skeleton.AbstractSkeleton && shouldUseManualSkeletonFamily(livingEntity);
	}

	static boolean shouldUseManualZombieFamily(Entity entity) {
		return switch (entityTypePath(entity)) {
			case "zombie", "husk", "drowned", "giant" -> true;
			default -> false;
		};
	}

	static boolean shouldUseManualSkeletonFamily(Entity entity) {
		return "skeleton".equals(entityTypePath(entity));
	}

	static void registerClientModelRules(Map<String, ClientModelResolver> rules) {
		rules.put("bogged", livingEntity -> captureBoggedClientModel((Bogged) livingEntity));
		rules.put("zombie_villager", livingEntity -> captureZombieVillagerClientModel((ZombieVillager) livingEntity));
		rules.put("zombified_piglin", livingEntity -> captureZombifiedPiglinClientModel((ZombifiedPiglin) livingEntity));
	}

	static boolean renderHumanoidBaseFixup(
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
		if (snapshot.kind() != HumanoidKind.ENDERMAN) {
			return false;
		}

		Matrix4f head = CameraEntityRenderer.rotateAround(root, 0.0F, 38.0F, 0.0F, headPitch, headYaw, 0.0F);
		CameraEntityRenderer.addBox(context, head, -4.0F, 38.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.0F);

		CameraEntityRenderer.addBox(context, root, -4.0F, 27.0F, -2.0F, 8.0F, 12.0F, 4.0F, 32, 16, 64, 32, material, false, 0.0F);

		Matrix4f rightArm = CameraEntityRenderer.rotateAround(root, -5.0F, 37.0F, 0.0F, rightArmPitch * 0.5F, rightArmYaw, rightArmRoll);
		Matrix4f leftArm = CameraEntityRenderer.rotateAround(root, 5.0F, 37.0F, 0.0F, leftArmPitch * 0.5F, leftArmYaw, leftArmRoll);
		CameraEntityRenderer.addBox(context, rightArm, -6.0F, 9.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, false, 0.0F);
		CameraEntityRenderer.addBox(context, leftArm, 4.0F, 9.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, true, 0.0F);

		Matrix4f rightLeg = CameraEntityRenderer.rotateAround(root, -2.0F, 30.0F, 0.0F, rightLegPitch * 0.5F, rightLegYaw, rightLegRoll);
		Matrix4f leftLeg = CameraEntityRenderer.rotateAround(root, 2.0F, 30.0F, 0.0F, leftLegPitch * 0.5F, leftLegYaw, leftLegRoll);
		CameraEntityRenderer.addBox(context, rightLeg, -3.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, false, 0.0F);
		CameraEntityRenderer.addBox(context, leftLeg, 1.0F, 0.0F, -1.0F, 2.0F, 30.0F, 2.0F, 56, 0, 64, 32, material, true, 0.0F);
		return true;
	}

	static boolean renderHumanoidOverlayFixup(RenderContext context, HumanoidSnapshot snapshot, Matrix4f root, int material, Identifier overlayTexture, float headYaw, float headPitch) {
		if (snapshot.kind() != HumanoidKind.ENDERMAN || !ENDERMAN_EYES_TEXTURE.equals(overlayTexture)) {
			return false;
		}
		Matrix4f head = CameraEntityRenderer.rotateAround(root, 0.0F, 38.0F, 0.0F, headPitch, headYaw, 0.0F);
		CameraEntityRenderer.addBox(context, head, -4.0F, 38.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, material, false, 0.1F, 15, 15);
		return true;
	}

	static boolean renderHumanoidHeldItemsFixup(RenderContext context, HumanoidSnapshot snapshot, Matrix4f root) {
		if (snapshot.kind() != HumanoidKind.ENDERMAN) {
			return false;
		}
		HeldItemSnapshot heldItem = snapshot.rightHandItem();
		if (heldItem == null || heldItem.isEmpty()) {
			return true;
		}
		ItemVisual visual = CameraEntityRenderer.resolveItemVisual(heldItem, ItemDisplayTransformContext.FIXED);
		if (visual == null) {
			return true;
		}
		Matrix4f itemRoot = new Matrix4f(root)
				.translate(0.0F, 0.6875F, -0.75F)
				.rotateX(CameraEntityRenderer.radians(20.0F))
				.rotateY(CameraEntityRenderer.radians(45.0F))
				.translate(0.25F, 0.1875F, 0.25F)
				.scale(-0.5F, -0.5F, 0.5F)
				.rotateY(CameraEntityRenderer.radians(90.0F));
		CameraEntityRenderer.renderItemVisual(context, itemRoot, visual, ItemDisplayTransformContext.FIXED);
		return true;
	}

	private static EntitySnapshot captureZombieVillagerManual(ZombieVillager zombieVillager, LivingEntity livingEntity) {
		VillagerData villagerData = zombieVillager.getVillagerData();
		List<Identifier> overlays = new ArrayList<>();
		overlays.add(CameraEntityRenderer.holderTexture(villagerData.type(), "entity/zombie_villager/type/"));
		if (!zombieVillager.isBaby() && !villagerData.profession().is(VillagerProfession.NONE)) {
			overlays.add(CameraEntityRenderer.holderTexture(villagerData.profession(), "entity/zombie_villager/profession/"));
			if (!villagerData.profession().is(VillagerProfession.NITWIT)) {
				overlays.add(CameraEntityRenderer.professionLevelTexture(villagerData.level(), "entity/zombie_villager/profession_level/"));
			}
		}
		return CameraEntityRenderer.humanoidSnapshot(
				livingEntity,
				HumanoidKind.ZOMBIE,
				ZOMBIE_VILLAGER_TEXTURE,
				overlays.stream().filter(identifier -> identifier != null).toArray(Identifier[]::new)
		);
	}

	private static ClientModelSnapshot captureZombieVillagerClientModel(ZombieVillager zombieVillager) {
		VillagerData villagerData = zombieVillager.getVillagerData();
		Map<String, Object> state = new HashMap<>(CameraEntityRenderer.livingStateFields(zombieVillager));
		state.put("villagerData", villagerData);
		state.put("isAggressive", zombieVillager.isAggressive());
		state.put("isConverting", zombieVillager.isConverting());

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		String modelClassName = "net.minecraft.client.model.monster.zombie.ZombieVillagerModel";
		layers.add(new ClientLayerSnapshot(modelClassName, CameraEntityRenderer.minecraftTexture("entity/zombie_villager/zombie_villager"), 0xFFFFFF, false));
		CameraEntityRenderer.addClientLayerIfPresent(layers, modelClassName, CameraEntityRenderer.holderTexture(villagerData.type(), "entity/zombie_villager/type/"), 0xFFFFFF, false);
		if (!zombieVillager.isBaby() && !villagerData.profession().is(VillagerProfession.NONE)) {
			CameraEntityRenderer.addClientLayerIfPresent(layers, modelClassName, CameraEntityRenderer.holderTexture(villagerData.profession(), "entity/zombie_villager/profession/"), 0xFFFFFF, false);
			if (!villagerData.profession().is(VillagerProfession.NITWIT)) {
				CameraEntityRenderer.addClientLayerIfPresent(layers, modelClassName, CameraEntityRenderer.professionLevelTexture(villagerData.level(), "entity/zombie_villager/profession_level/"), 0xFFFFFF, false);
			}
		}

		return CameraEntityRenderer.livingClientModelSnapshot(zombieVillager, zombieVillager.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static ClientModelSnapshot captureZombifiedPiglinClientModel(ZombifiedPiglin zombifiedPiglin) {
		Map<String, Object> state = new HashMap<>(CameraEntityRenderer.livingStateFields(zombifiedPiglin));
		state.put("isAggressive", zombifiedPiglin.isAggressive());
		return CameraEntityRenderer.livingClientModelSnapshot(
				zombifiedPiglin,
				zombifiedPiglin.yBodyRot,
				state,
				new ClientLayerSnapshot[]{
						new ClientLayerSnapshot(
								"net.minecraft.client.model.monster.piglin.ZombifiedPiglinModel",
								CameraEntityRenderer.minecraftTexture("entity/piglin/zombified_piglin"),
								0xFFFFFF,
								false
						)
				}
		);
	}

	private static ClientModelSnapshot captureBoggedClientModel(Bogged bogged) {
		Map<String, Object> state = new HashMap<>(CameraEntityRenderer.livingStateFields(bogged));
		state.put("isSheared", bogged.isSheared());

		List<ClientLayerSnapshot> layers = new ArrayList<>();
		String modelClassName = "net.minecraft.client.model.monster.skeleton.BoggedModel";
		layers.add(new ClientLayerSnapshot(modelClassName, CameraEntityRenderer.minecraftTexture("entity/skeleton/bogged"), 0xFFFFFF, false));
		if (!bogged.isSheared()) {
			CameraEntityRenderer.addClientLayerIfPresent(layers, modelClassName, CameraEntityRenderer.minecraftTexture("entity/skeleton/bogged_overlay"), 0xFFFFFF, false);
		}
		return CameraEntityRenderer.livingClientModelSnapshot(bogged, bogged.yBodyRot, state, layers.toArray(ClientLayerSnapshot[]::new));
	}

	private static String entityTypePath(Entity entity) {
		if (entity == null) {
			return "";
		}
		Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		return typeId == null ? "" : typeId.getPath();
	}

	private static EntitySnapshot captureEndermanManual(EnderMan enderMan) {
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
				CameraEntityRenderer.sleepingDirection(enderMan),
				enderMan.isCrouching(),
				enderMan.isVisuallySwimming(),
				enderMan.isFallFlying(),
				enderMan.isPassenger(),
				false,
				enderMan.isCreepy(),
				false,
				enderMan.getMainArm() == null ? HumanoidArm.RIGHT : enderMan.getMainArm(),
				HumanoidKind.ENDERMAN,
				ENDERMAN_TEXTURE,
				new Identifier[]{ENDERMAN_EYES_TEXTURE},
				CameraEntityRenderer.captureArmorEquipment(enderMan),
				CameraEntityRenderer.leftHandItem(enderMan),
				CameraEntityRenderer.heldItemSnapshot(enderMan, carriedBlockStack),
				null,
				(byte) 0
		);
	}
}
