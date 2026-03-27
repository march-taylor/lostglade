package com.lostglade.server.camera.bluemap;

import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

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
		ArmorEquipmentSnapshot armor,
		HeldItemSnapshot leftHandItem,
		HeldItemSnapshot rightHandItem,
		PlayerSkinSnapshot playerSkin,
		byte playerModelBits
) implements EntitySnapshot {
}

record HeldItemSnapshot(
		ItemStack stack,
		boolean usingItem,
		float useTicks,
		boolean fishingRodCast,
		String contextDimensionId,
		long gameTime,
		long dayTime
) {
	boolean isEmpty() {
		return this.stack == null || this.stack.isEmpty();
	}
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
		Rotations rightLegPose,
		ArmorEquipmentSnapshot armor
) implements EntitySnapshot {
}

record ArmorEquipmentSnapshot(
		ItemStack head,
		ItemStack chest,
		ItemStack legs,
		ItemStack feet
) {
	boolean isEmpty() {
		return (this.head == null || this.head.isEmpty())
				&& (this.chest == null || this.chest.isEmpty())
				&& (this.legs == null || this.legs.isEmpty())
				&& (this.feet == null || this.feet.isEmpty());
	}
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

record DisplayItemSnapshot(
		Vec3 position,
		float yaw,
		float pitch,
		float roll,
		float scale,
		ItemDisplayTransformContext transformContext,
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
		String dynamicImageKey,
		BufferedImage dynamicImage,
		int tintRgb,
		boolean emissive,
		String layerFactoryMethodName,
		float cubeDeformation,
		float secondaryCubeDeformation,
		boolean modelFlag,
		float renderScale
) {
	ClientLayerSnapshot(String modelClassName, Identifier texture, int tintRgb, boolean emissive) {
		this(modelClassName, texture, null, null, null, tintRgb, emissive, null, 0.0F, 0.0F, false, 1.0F);
	}

	ClientLayerSnapshot withFactory(String layerFactoryMethodName) {
		return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.dynamicImageKey, this.dynamicImage, this.tintRgb, this.emissive, layerFactoryMethodName, this.cubeDeformation, this.secondaryCubeDeformation, this.modelFlag, this.renderScale);
	}

	ClientLayerSnapshot withCubeDeformation(float cubeDeformation) {
		return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.dynamicImageKey, this.dynamicImage, this.tintRgb, this.emissive, this.layerFactoryMethodName, cubeDeformation, this.secondaryCubeDeformation, this.modelFlag, this.renderScale);
	}

	ClientLayerSnapshot withSecondaryCubeDeformation(float secondaryCubeDeformation) {
		return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.dynamicImageKey, this.dynamicImage, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, secondaryCubeDeformation, this.modelFlag, this.renderScale);
	}

	ClientLayerSnapshot withModelFlag(boolean modelFlag) {
		return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.dynamicImageKey, this.dynamicImage, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, this.secondaryCubeDeformation, modelFlag, this.renderScale);
	}

	ClientLayerSnapshot withRenderScale(float renderScale) {
		return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, this.dynamicImageKey, this.dynamicImage, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, this.secondaryCubeDeformation, this.modelFlag, renderScale);
	}

	ClientLayerSnapshot withPlayerSkin(PlayerSkinSnapshot playerSkin) {
		return new ClientLayerSnapshot(this.modelClassName, this.texture, playerSkin, this.dynamicImageKey, this.dynamicImage, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, this.secondaryCubeDeformation, this.modelFlag, this.renderScale);
	}

	ClientLayerSnapshot withDynamicImage(String dynamicImageKey, BufferedImage dynamicImage) {
		return new ClientLayerSnapshot(this.modelClassName, this.texture, this.playerSkin, dynamicImageKey, dynamicImage, this.tintRgb, this.emissive, this.layerFactoryMethodName, this.cubeDeformation, this.secondaryCubeDeformation, this.modelFlag, this.renderScale);
	}
}

record EquipmentVisualLayer(
		Identifier texture,
		String dynamicImageKey,
		BufferedImage dynamicImage,
		int tintRgb
) {
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
		boolean slim,
		BufferedImage localImage
) {
}

record ItemVisual(
		Identifier flatTexture,
		ResolvedItemModel model,
		int[] tintColors,
		ClientModelSnapshot specialModel
) {
}

record ResolvedItemDefinition(
		Identifier modelId,
		int[] tintColors,
		String specialType
) {
}

record ItemDefinitionRenderState(
		ItemDisplayTransformContext transformContext,
		boolean usingItem,
		float useTicks,
		boolean fishingRodCast,
		String contextDimensionId,
		long gameTime,
		long dayTime
) {
}

record ResolvedItemModel(
		Map<String, String> textures,
		List<ItemModelElement> elements,
		Map<ItemDisplayTransformContext, ItemModelTransform> transforms
) {
}

record ItemModelTransform(
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
	static final ItemModelTransform IDENTITY = new ItemModelTransform(
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

enum ItemDisplayTransformContext {
	NONE("none"),
	THIRD_PERSON_RIGHT_HAND("thirdperson_righthand"),
	THIRD_PERSON_LEFT_HAND("thirdperson_lefthand"),
	FIRST_PERSON_RIGHT_HAND("firstperson_righthand"),
	FIRST_PERSON_LEFT_HAND("firstperson_lefthand"),
	HEAD("head"),
	GUI("gui"),
	GROUND("ground"),
	FIXED("fixed"),
	ON_SHELF("on_shelf"),
	FRAMED("fixed");

	private final String serializedName;

	ItemDisplayTransformContext(String serializedName) {
		this.serializedName = serializedName;
	}

	String serializedName() {
		return this.serializedName;
	}
}

record ItemModelElement(
		Vec3 from,
		Vec3 to,
		Map<Direction, ItemModelFace> faces,
		ElementRotation rotation
) {
}

record ItemModelFace(
		String texture,
		double[] uv,
		int rotation,
		int tintIndex
) {
}

record FlatSpriteMesh(
		int width,
		int height,
		List<HorizontalSpriteSpan> topEdges,
		List<HorizontalSpriteSpan> bottomEdges,
		List<VerticalSpriteSpan> leftEdges,
		List<VerticalSpriteSpan> rightEdges
) {
	static final FlatSpriteMesh EMPTY = new FlatSpriteMesh(0, 0, List.of(), List.of(), List.of(), List.of());

	boolean isEmpty() {
		return this.width <= 0 || this.height <= 0;
	}
}

record HorizontalSpriteSpan(int row, int startX, int endX) {
}

record VerticalSpriteSpan(int column, int startY, int endY) {
}

record ElementRotation(
		Vec3 origin,
		Direction.Axis axis,
		float angle,
		boolean rescale,
		Matrix4f transform
) {
}

@FunctionalInterface
interface ClientModelResolver {
	ClientModelSnapshot capture(LivingEntity livingEntity);
}

enum HumanoidKind {
	PLAYER(64, 64, 4.0F, 4.0F, true, false, 1.0F),
	PLAYER_SLIM(64, 64, 3.0F, 4.0F, true, false, 1.0F),
	ZOMBIE(64, 64, 4.0F, 4.0F, false, false, 1.0F),
	SKELETON(64, 32, 2.0F, 2.0F, false, false, 1.0F),
	ENDERMAN(64, 32, 2.0F, 2.0F, false, false, 1.0F),
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
