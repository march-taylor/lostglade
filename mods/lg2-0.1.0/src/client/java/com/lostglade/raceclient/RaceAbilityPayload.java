package com.lostglade.raceclient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Must stay wire-compatible with lg2:race_ability on the server. */
public record RaceAbilityPayload(int slot) implements CustomPacketPayload {
	public static final Type<RaceAbilityPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lg2", "race_ability"));
	public static final StreamCodec<FriendlyByteBuf, RaceAbilityPayload> STREAM_CODEC =
			CustomPacketPayload.codec(RaceAbilityPayload::write, RaceAbilityPayload::new);

	public RaceAbilityPayload(FriendlyByteBuf buffer) {
		this(buffer.readVarInt());
	}

	private void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(this.slot);
	}

	@Override
	public Type<RaceAbilityPayload> type() {
		return TYPE;
	}
}
