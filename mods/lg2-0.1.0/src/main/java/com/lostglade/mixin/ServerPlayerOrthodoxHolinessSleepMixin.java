package com.lostglade.mixin;

import com.lostglade.server.OrthodoxHolinessSystem;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerOrthodoxHolinessSleepMixin {
	@Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
	private void lg2$preventSleepAtZeroHoliness(
			BlockPos pos,
			CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> cir
	) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (OrthodoxHolinessSystem.preventSleep(self)) {
			cir.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
		}
	}
}
