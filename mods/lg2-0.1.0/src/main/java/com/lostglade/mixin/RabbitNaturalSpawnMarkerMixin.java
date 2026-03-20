package com.lostglade.mixin;

import com.lostglade.config.Lg2Config;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Rabbit.class)
public abstract class RabbitNaturalSpawnMarkerMixin {
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void lg2$markNaturalRabbitForKillerReplacement(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData spawnGroupData,
			CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Rabbit self = (Rabbit) (Object) this;
		boolean naturalLikeSpawn = spawnReason == EntitySpawnReason.NATURAL || spawnReason == EntitySpawnReason.CHUNK_GENERATION;
		if (!naturalLikeSpawn || self.getType() != EntityType.RABBIT || self.getVariant() == Rabbit.Variant.EVIL) {
			return;
		}

		int replacementChance = Math.max(1, Lg2Config.get().killerRabbitReplacementChance);
		if (replacementChance > 1 && self.getRandom().nextInt(replacementChance) != 0) {
			return;
		}

		((RabbitAccessor) self).lg2$setVariant(Rabbit.Variant.EVIL);
	}
}
