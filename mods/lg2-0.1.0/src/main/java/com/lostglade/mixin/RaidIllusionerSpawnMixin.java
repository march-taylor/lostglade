package com.lostglade.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Raid.class)
public abstract class RaidIllusionerSpawnMixin {
	@Shadow
	public abstract void joinRaid(ServerLevel level, int wave, Raider raider, BlockPos pos, boolean existing);

	@Redirect(
			method = "spawnGroup",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/raid/Raid;joinRaid(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/entity/raid/Raider;Lnet/minecraft/core/BlockPos;Z)V"
			)
	)
	private void lg2$joinRaidAndAddIllusioner(
			Raid raid,
			ServerLevel level,
			int wave,
			Raider raider,
			BlockPos pos,
			boolean existing
	) {
		this.joinRaid(level, wave, raider, pos, existing);
		if (!(raider instanceof Evoker)) {
			return;
		}

		Illusioner illusioner = EntityType.ILLUSIONER.create(level, EntitySpawnReason.EVENT);
		if (illusioner == null) {
			return;
		}

		illusioner.snapTo(pos, 0.0F, 0.0F);
		DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
		illusioner.finalizeSpawn(level, difficulty, EntitySpawnReason.EVENT, null);
		this.joinRaid(level, wave, illusioner, pos, false);
		level.addFreshEntity(illusioner);
	}
}
