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

		PayloadTypeRegistry.playC2S().register(DroneCollisionSampleC2SPayload.TYPE, DroneCollisionSampleC2SPayload.STREAM_CODEC);
	}

	public record DroneCollisionSampleC2SPayload(
			double intendedX,
			double intendedY,
			double intendedZ,
			double actualX,
			double actualY,
			double actualZ,
			boolean horizontalCollision,
			boolean verticalCollision,
			boolean onGround
	) implements CustomPacketPayload {
		public static final Type<DroneCollisionSampleC2SPayload> TYPE = new Type<>(id("drone_collision_sample"));
		public static final StreamCodec<FriendlyByteBuf, DroneCollisionSampleC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(DroneCollisionSampleC2SPayload::write, DroneCollisionSampleC2SPayload::new);

		public DroneCollisionSampleC2SPayload(FriendlyByteBuf buffer) {
			this(
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readDouble(),
					buffer.readBoolean(),
					buffer.readBoolean(),
					buffer.readBoolean()
			);
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeDouble(this.intendedX);
			buffer.writeDouble(this.intendedY);
			buffer.writeDouble(this.intendedZ);
			buffer.writeDouble(this.actualX);
			buffer.writeDouble(this.actualY);
			buffer.writeDouble(this.actualZ);
			buffer.writeBoolean(this.horizontalCollision);
			buffer.writeBoolean(this.verticalCollision);
			buffer.writeBoolean(this.onGround);
		}

		@Override
		public Type<DroneCollisionSampleC2SPayload> type() {
			return TYPE;
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, path);
	}
}
