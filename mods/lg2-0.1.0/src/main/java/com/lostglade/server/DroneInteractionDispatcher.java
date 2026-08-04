package com.lostglade.server;

import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Decodes an entity-interaction packet for controlled drones.
 *
 * <p>This deliberately lives outside the packet-listener mixin.  Mixin has to
 * copy anonymous classes declared in a mixin into the target Minecraft class;
 * that made the old anonymous packet handler unavailable at runtime and
 * disconnected the player.  A normal named server class is loaded directly
 * and cannot be rewritten into the packet listener.</p>
 */
public final class DroneInteractionDispatcher {
	private DroneInteractionDispatcher() {
	}

	public static boolean handle(ServerPlayer player, ServerboundInteractPacket packet) {
		if (player == null || packet == null) {
			return false;
		}
		Handler handler = new Handler(player, packet);
		packet.dispatch(handler);
		return handler.handled;
	}

	private static final class Handler implements ServerboundInteractPacket.Handler {
		private final ServerPlayer player;
		private final ServerboundInteractPacket packet;
		private boolean handled;

		private Handler(ServerPlayer player, ServerboundInteractPacket packet) {
			this.player = player;
			this.packet = packet;
		}

		@Override
		public void onInteraction(InteractionHand hand) {
			this.handled = DroneSystem.handleControlledUseItem(this.player, hand);
		}

		@Override
		public void onInteraction(InteractionHand hand, Vec3 location) {
			this.handled = DroneSystem.handleControlledUseItem(this.player, hand);
		}

		@Override
		public void onAttack() {
			this.handled = DroneSystem.handleControlledAttackInteraction(this.player, this.packet);
		}
	}
}
