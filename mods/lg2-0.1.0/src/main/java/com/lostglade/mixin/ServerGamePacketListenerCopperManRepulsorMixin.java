package com.lostglade.mixin;

import com.lostglade.server.CopperManRepulsorSystem;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCopperManRepulsorMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
	private void lg2$fireCopperRepulsorInAir(ServerboundUseItemPacket packet, CallbackInfo ci) {
		if (CopperManRepulsorSystem.handleUseInteraction(this.player, packet.getHand())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
	private void lg2$fireCopperRepulsorOnEntity(ServerboundInteractPacket packet, CallbackInfo ci) {
		Entity target = packet.getTarget((ServerLevel) this.player.level());
		if (!CopperManRepulsorSystem.isAirTriggerEntity(this.player, target)) {
			return;
		}

		final boolean[] cancel = new boolean[]{false};
		packet.dispatch(new ServerboundInteractPacket.Handler() {
			@Override
			public void onInteraction(InteractionHand hand) {
				cancel[0] = CopperManRepulsorSystem.handleUseInteraction(player, hand);
			}

			@Override
			public void onInteraction(InteractionHand hand, Vec3 location) {
				cancel[0] = CopperManRepulsorSystem.handleUseInteraction(player, hand);
			}

			@Override
			public void onAttack() {
			}
		});
		if (cancel[0]) {
			ci.cancel();
		}
	}
}
