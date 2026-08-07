package com.lostglade.network;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Lg2Payloads {
	private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

	private Lg2Payloads() {
	}

	public static void registerPayloadTypes() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}

		PayloadTypeRegistry.playS2C().register(MilkPocketVoidFadeS2CPayload.TYPE, MilkPocketVoidFadeS2CPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RaceAbilityC2SPayload.TYPE, RaceAbilityC2SPayload.STREAM_CODEC);
	}

	/** A request from the optional LG2 client UI to activate one of the four race actions. */
	public record RaceAbilityC2SPayload(int slot) implements CustomPacketPayload {
		public static final Type<RaceAbilityC2SPayload> TYPE = new Type<>(id("race_ability"));
		public static final StreamCodec<FriendlyByteBuf, RaceAbilityC2SPayload> STREAM_CODEC =
				CustomPacketPayload.codec(RaceAbilityC2SPayload::write, RaceAbilityC2SPayload::new);

		public RaceAbilityC2SPayload(FriendlyByteBuf buffer) {
			this(buffer.readVarInt());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeVarInt(this.slot);
		}

		@Override
		public Type<RaceAbilityC2SPayload> type() {
			return TYPE;
		}
	}

	public record MilkPocketVoidFadeS2CPayload(float alpha) implements CustomPacketPayload {
		public static final Type<MilkPocketVoidFadeS2CPayload> TYPE = new Type<>(id("milk_pocket_void_fade"));
		public static final StreamCodec<FriendlyByteBuf, MilkPocketVoidFadeS2CPayload> STREAM_CODEC =
				CustomPacketPayload.codec(MilkPocketVoidFadeS2CPayload::write, MilkPocketVoidFadeS2CPayload::new);

		public MilkPocketVoidFadeS2CPayload(FriendlyByteBuf buffer) {
			this(buffer.readFloat());
		}

		private void write(FriendlyByteBuf buffer) {
			buffer.writeFloat(this.alpha);
		}

		@Override
		public Type<MilkPocketVoidFadeS2CPayload> type() {
			return TYPE;
		}
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(Lg2.MOD_ID, path);
	}
}
