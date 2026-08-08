package com.lostglade.raceclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class Lg2RaceClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PayloadTypeRegistry.playC2S().register(RaceAbilityPayload.TYPE, RaceAbilityPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(RaceAbilityStateRequestPayload.TYPE, RaceAbilityStateRequestPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(RaceAbilityStatePayload.TYPE, RaceAbilityStatePayload.STREAM_CODEC);
		ClientPlayNetworking.registerGlobalReceiver(
				RaceAbilityStatePayload.TYPE,
				(payload, context) -> context.client().execute(() -> RaceAbilityState.update(payload.unlockedMask()))
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RaceAbilityState.clear());
		RaceClientControls.register();
	}
}
