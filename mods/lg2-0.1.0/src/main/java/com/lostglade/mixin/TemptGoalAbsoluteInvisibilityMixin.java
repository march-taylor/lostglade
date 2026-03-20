package com.lostglade.mixin;

import com.lostglade.server.ServerAbsoluteInvisibilitySystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(TemptGoal.class)
public abstract class TemptGoalAbsoluteInvisibilityMixin {
	@Shadow
	@Final
	protected Mob mob;

	@Shadow
	protected Player player;

	@Shadow
	private int calmDown;

	@Invoker("shouldFollow")
	protected abstract boolean lg2$invokeShouldFollow(LivingEntity entity);

	@Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
	private void lg2$allowTemptingAbsoluteInvisiblePlayers(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() || this.calmDown > 0 || !(this.mob.level() instanceof ServerLevel level)) {
			return;
		}

		double temptRange = this.mob.getAttributeValue(Attributes.TEMPT_RANGE);
		double maxDistanceSqr = temptRange * temptRange;
		AABB searchBox = this.mob.getBoundingBox().inflate(temptRange, temptRange, temptRange);
		List<ServerPlayer> candidates = level.getEntitiesOfClass(
				ServerPlayer.class,
				searchBox,
				candidate -> candidate.isAlive()
						&& !candidate.isSpectator()
						&& ServerAbsoluteInvisibilitySystem.suppressesMobDetection(candidate)
		);

		ServerPlayer closestPlayer = null;
		double closestDistanceSqr = Double.MAX_VALUE;
		for (ServerPlayer candidate : candidates) {
			double distanceSqr = this.mob.distanceToSqr(candidate);
			if (distanceSqr > maxDistanceSqr || !this.lg2$invokeShouldFollow(candidate)) {
				continue;
			}

			if (distanceSqr < closestDistanceSqr) {
				closestDistanceSqr = distanceSqr;
				closestPlayer = candidate;
			}
		}

		if (closestPlayer != null) {
			this.player = closestPlayer;
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "start", at = @At("TAIL"))
	private void lg2$beginTemptRevealForAbsoluteInvisiblePlayers(CallbackInfo ci) {
		if (this.player instanceof ServerPlayer serverPlayer) {
			ServerAbsoluteInvisibilitySystem.beginTemptReveal(this.mob, serverPlayer);
		}
	}

	@Inject(method = "stop", at = @At("HEAD"))
	private void lg2$endTemptRevealForAbsoluteInvisiblePlayers(CallbackInfo ci) {
		if (this.player instanceof ServerPlayer serverPlayer) {
			ServerAbsoluteInvisibilitySystem.endTemptReveal(this.mob, serverPlayer);
		}
	}
}
