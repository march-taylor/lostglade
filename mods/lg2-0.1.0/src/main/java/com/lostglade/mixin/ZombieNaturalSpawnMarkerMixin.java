package com.lostglade.mixin;

import com.lostglade.server.ServerUnusedMobSpawnSystem;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieNaturalSpawnMarkerMixin {
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void lg2$markNaturalZombieForGiantReplacement(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData spawnGroupData,
			CallbackInfoReturnable<SpawnGroupData> cir
	) {
		Zombie self = (Zombie) (Object) this;
		if (spawnReason == EntitySpawnReason.NATURAL && self.getType() == EntityType.ZOMBIE) {
			ServerUnusedMobSpawnSystem.markNaturalZombieCandidate(self);
		}
	}
}
