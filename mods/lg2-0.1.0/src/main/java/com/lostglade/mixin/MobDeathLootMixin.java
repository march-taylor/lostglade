package com.lostglade.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobDeathLootMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void lg2$breakLeavesForGiants(CallbackInfo ci) {
		if (!((Object) this instanceof Giant giant) || !(giant.level() instanceof ServerLevel level)) {
			return;
		}
		if (!giant.horizontalCollision || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}

		boolean brokeAny = false;
		AABB area = giant.getBoundingBox().inflate(0.2D);
		for (BlockPos pos : BlockPos.betweenClosed(
				Mth.floor(area.minX),
				Mth.floor(area.minY),
				Mth.floor(area.minZ),
				Mth.floor(area.maxX),
				Mth.floor(area.maxY),
				Mth.floor(area.maxZ)
		)) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof LeavesBlock && level.destroyBlock(pos, true, giant)) {
				brokeAny = true;
			}
		}

		if (!brokeAny && giant.onGround()) {
			giant.jumpFromGround();
		}
	}

	@Inject(method = "dropCustomDeathLoot", at = @At("TAIL"))
	private void lg2$dropGiantRottenFlesh(ServerLevel level, DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
		if (!((Object) this instanceof Giant giant)) {
			return;
		}

		int count = 24 + giant.getRandom().nextInt(9);
		giant.spawnAtLocation(level, new ItemStack(Items.ROTTEN_FLESH, count));
	}
}
