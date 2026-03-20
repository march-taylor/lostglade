package com.lostglade.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$WoodlandMansionPiece")
public abstract class WoodlandMansionIllusionerSpawnMixin {
	@Unique private static final int[][] LG2_MANSION_OFFSETS = {
			{2, 0}, {-2, 0}, {0, 2}, {0, -2},
			{3, 0}, {-3, 0}, {0, 3}, {0, -3},
			{2, 2}, {2, -2}, {-2, 2}, {-2, -2},
			{1, 2}, {-1, 2}, {1, -2}, {-1, -2}
	};

	@Redirect(
			method = "handleDataMarker",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"
			)
	)
	private void lg2$addMansionEntityAndMaybeIllusioner(
			ServerLevelAccessor world,
			Entity entity,
			String function,
			BlockPos pos,
			ServerLevelAccessor methodWorld,
			RandomSource random,
			BoundingBox boundingBox
	) {
		world.addFreshEntityWithPassengers(entity);
		if (!"Mage".equals(function) || !(entity instanceof Evoker)) {
			return;
		}

		ServerLevel level = world.getLevel();
		Illusioner illusioner = EntityType.ILLUSIONER.create(level, EntitySpawnReason.STRUCTURE);
		if (illusioner == null) {
			return;
		}

		BlockPos spawnPos = this.lg2$pickSpawnPosInsideMarkerRoom(pos, random, boundingBox);
		illusioner.snapTo(spawnPos, 0.0F, 0.0F);
		illusioner.finalizeSpawn(world, world.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.STRUCTURE, null);
		illusioner.spawnAnim();
		world.addFreshEntityWithPassengers(illusioner);
	}

	@Unique
	private BlockPos lg2$pickSpawnPosInsideMarkerRoom(BlockPos origin, RandomSource random, BoundingBox boundingBox) {
		int start = random.nextInt(LG2_MANSION_OFFSETS.length);
		for (int i = 0; i < LG2_MANSION_OFFSETS.length; i++) {
			int[] offset = LG2_MANSION_OFFSETS[(start + i) % LG2_MANSION_OFFSETS.length];
			BlockPos candidate = origin.offset(offset[0], 0, offset[1]);
			if (!boundingBox.isInside(candidate)) {
				continue;
			}
			return candidate;
		}
		return origin;
	}
}
