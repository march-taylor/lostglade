package com.lostglade.server.camera.bluemap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.Bogged;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CameraEntityFixups {
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
}
