package com.lostglade.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$WoodlandMansionPiece")
public abstract class WoodlandMansionIllusionerSpawnMixin {
	@Inject(method = "handleDataMarker", at = @At("TAIL"))
	private void lg2$spawnAdditionalMansionIllusioner(
			String function,
			BlockPos pos,
			ServerLevelAccessor world,
			RandomSource random,
			BoundingBox boundingBox,
			CallbackInfo ci
	) {
		if (!"Mage".equals(function)) {
			return;
		}

		ServerLevel level = world.getLevel();
		Illusioner illusioner = EntityType.ILLUSIONER.create(level, EntitySpawnReason.STRUCTURE);
		if (illusioner == null) {
			return;
		}

		illusioner.snapTo(pos, 0.0F, 0.0F);
		illusioner.finalizeSpawn(world, world.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
		illusioner.spawnAnim();
		world.addFreshEntityWithPassengers(illusioner);
	}
}
