package com.lostglade.mixin;

import com.lostglade.util.ItemDisplayHitboxHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerGamePacketListenerImpl.class, priority = 1200)
public abstract class ServerGamePacketListenerItemDisplayHitboxMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
	private void lg2$ignoreZeroHitboxItemDisplayInteraction(ServerboundInteractPacket packet, CallbackInfo ci) {
		if (packet == null || this.player == null || !(this.player.level() instanceof ServerLevel level)) {
			return;
		}
		Entity target = packet.getTarget(level);
		if (ItemDisplayHitboxHelper.isZeroHitboxDisplay(target)) {
			ci.cancel();
		}
	}
}