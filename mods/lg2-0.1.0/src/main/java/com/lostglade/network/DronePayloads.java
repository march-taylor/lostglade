package com.lostglade.network;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DronePayloads {
	private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

	private DronePayloads() {
	}

	public static void registerPayloadTypes() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}

		PayloadTypeRegistry.playC2S().register(DroneKineticCollisionC2SPayload.TYPE, DroneKineticCollisionC2SPayload.STREAM_CODEC);
	}

	public record DroneKineticCollisionC2SPayload(
			double horizontalSpeedBefore,
			double horizontalSpeedAfter
	) implements CustomPacketPayload {
		public static final Type<DroneKineticCollisionC2SPayload> TYPE = new Type<>(id("drone_kinetic_collision"));
		public static final StreamCodec<FriendlyByteBuf, DroneKineticCollisionC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(DroneKineticCollisionC2SPayload::write, DroneKineticCollisionC2SPayload::new);

		public DroneKineticCollisionC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readDouble(), buffer.readDouble());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeDouble(this.horizontalSpeedBefore);
			buffer.writeDouble(this.horizontalSpeedAfter);
		}

		@Override
		public Type<DroneKineticCollisionC2SPayload> type() {
			return TYPE;
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, path);
	}
}
