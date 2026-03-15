package com.lostglade.mixin;

import com.lostglade.server.ServerBackroomsSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerBackroomsRespawnMixin {
	@Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
	private void lg2$redirectBackroomsRespawn(boolean alive, TeleportTransition.PostTeleportTransition postTeleportTransition, CallbackInfoReturnable<TeleportTransition> cir) {
		TeleportTransition transition = ServerBackroomsSystem.createImmediateBackroomsRespawnTransition(
				(ServerPlayer) (Object) this,
				postTeleportTransition
		);
		if (transition != null) {
			cir.setReturnValue(transition);
		}
	}
}
