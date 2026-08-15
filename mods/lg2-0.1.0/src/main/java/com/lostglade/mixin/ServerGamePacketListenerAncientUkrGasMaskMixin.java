package com.lostglade.mixin;

import com.lostglade.server.ServerRaceSystem;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerAncientUkrGasMaskMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
	private void lg2$blockAncientUkrGasMaskCreativeMove(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
		if (packet != null && ServerRaceSystem.shouldBlockAncientUkrGasMaskCreativeSlot(this.player, packet.slotNum(), packet.itemStack())) {
			this.player.inventoryMenu.sendAllDataToRemote();
			if (this.player.containerMenu != this.player.inventoryMenu) {
				this.player.containerMenu.sendAllDataToRemote();
			}
			ci.cancel();
		}
	}
}