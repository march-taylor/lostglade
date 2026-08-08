package com.lostglade.raceclient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Must stay wire-compatible with lg2:race_ability_state_request on the server. */
public record RaceAbilityStateRequestPayload() implements CustomPacketPayload {
	public static final Type<RaceAbilityStateRequestPayload> TYPE = new Type<>(
			Identifier.fromNamespaceAndPath("lg2", "race_ability_state_request")
	);
	public static final StreamCodec<FriendlyByteBuf, RaceAbilityStateRequestPayload> STREAM_CODEC =
			StreamCodec.unit(new RaceAbilityStateRequestPayload());

	@Override
	public Type<RaceAbilityStateRequestPayload> type() {
		return TYPE;
	}
}
