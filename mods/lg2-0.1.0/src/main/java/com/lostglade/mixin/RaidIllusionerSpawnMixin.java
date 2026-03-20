package com.lostglade.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raid.class)
public abstract class RaidIllusionerSpawnMixin {
	@Unique private static final long LG2_ILLUSIONER_RAID_SEED = 0x49C17A4C2D13B65AL;
	@Unique private static final int[] LG2_EVOKER_SPAWNS_PER_WAVE = {0, 0, 0, 0, 0, 1, 1, 2};

	@Shadow private int numGroups;
	@Shadow private int raidOmenLevel;

	@Unique private int lg2$pendingWave = -1;
	@Unique private boolean lg2$pendingBonusWave;
	@Unique private BlockPos lg2$pendingSpawnPos;

	@Shadow
	public abstract int getGroupsSpawned();

	@Shadow
	public abstract void joinRaid(ServerLevel level, int wave, Raider raider, BlockPos pos, boolean existing);

	@Shadow
	public abstract int getTotalRaidersAlive();

	@Inject(method = "spawnGroup", at = @At("HEAD"))
	private void lg2$captureVanillaRaidContext(ServerLevel level, BlockPos pos, CallbackInfo ci) {
		this.lg2$pendingWave = this.getGroupsSpawned() + 1;
		this.lg2$pendingBonusWave = this.getGroupsSpawned() == this.numGroups
				&& this.getTotalRaidersAlive() == 0
				&& this.raidOmenLevel > 1;
		this.lg2$pendingSpawnPos = pos.immutable();
	}

	@Inject(method = "spawnGroup", at = @At("TAIL"))
	private void lg2$spawnIllusionersWithEvokerRules(ServerLevel level, BlockPos pos, CallbackInfo ci) {
		if (this.lg2$pendingWave < 0 || this.lg2$pendingSpawnPos == null) {
			return;
		}

		int wave = this.lg2$pendingWave;
		boolean bonusWave = this.lg2$pendingBonusWave;
		BlockPos spawnPos = this.lg2$pendingSpawnPos;

		this.lg2$pendingWave = -1;
		this.lg2$pendingBonusWave = false;
		this.lg2$pendingSpawnPos = null;

		RandomSource illusionerRandom = RandomSource.create(
				LG2_ILLUSIONER_RAID_SEED
						^ spawnPos.asLong()
						^ ((long) wave << 32)
						^ ((long) this.numGroups << 48)
		);
		int count = bonusWave
				? LG2_EVOKER_SPAWNS_PER_WAVE[this.numGroups]
				: LG2_EVOKER_SPAWNS_PER_WAVE[Math.min(wave, LG2_EVOKER_SPAWNS_PER_WAVE.length - 1)];
		for (int i = 0; i < count; i++) {
			Illusioner illusioner = EntityType.ILLUSIONER.create(level, EntitySpawnReason.EVENT);
			if (illusioner == null) {
				continue;
			}

			BlockPos candidatePos = this.lg2$findNearbySpawnPos(level, spawnPos, illusioner, illusionerRandom, 4);
			this.joinRaid(level, wave, illusioner, candidatePos, false);
		}
	}

	@Unique
	private BlockPos lg2$findNearbySpawnPos(
			ServerLevel level,
			BlockPos origin,
			Illusioner illusioner,
			RandomSource random,
			int radius
	) {
		BlockPos fallback = origin;
		for (int attempt = 0; attempt < 18; attempt++) {
			int dx = random.nextInt(radius * 2 + 1) - radius;
			int dz = random.nextInt(radius * 2 + 1) - radius;
			int dy = random.nextInt(3) - 1;
			BlockPos candidate = origin.offset(dx, dy, dz);
			if (!this.lg2$canStandAt(level, candidate, illusioner)) {
				continue;
			}
			return candidate;
		}
		return fallback;
	}

	@Unique
	private boolean lg2$canStandAt(ServerLevel level, BlockPos pos, Illusioner illusioner) {
		BlockState feetState = level.getBlockState(pos);
		BlockState headState = level.getBlockState(pos.above());
		BlockState belowState = level.getBlockState(pos.below());
		if (!feetState.canBeReplaced() || !headState.canBeReplaced() || !belowState.blocksMotion()) {
			return false;
		}

		illusioner.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		return level.noCollision(illusioner);
	}
}
