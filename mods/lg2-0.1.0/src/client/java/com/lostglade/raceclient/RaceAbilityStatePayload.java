package com.lostglade.raceclient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Must stay wire-compatible with lg2:race_ability_state on the server. */
public record RaceAbilityStatePayload(int unlockedMask) implements CustomPacketPayload {
	public static final Type<RaceAbilityStatePayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath("lg2", "race_ability_state")
	);
	public static final StreamCodec<FriendlyByteBuf, RaceAbilityStatePayload> STREAM_CODEC =
			CustomPacketPayload.codec(RaceAbilityStatePayload::write, RaceAbilityStatePayload::new);

	public RaceAbilityStatePayload(FriendlyByteBuf buffer) {
		this(buffer.readVarInt());
	}

	private void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(this.unlockedMask);
	}

	@Override
	public Type<RaceAbilityStatePayload> type() {
		return TYPE;
	}
}
