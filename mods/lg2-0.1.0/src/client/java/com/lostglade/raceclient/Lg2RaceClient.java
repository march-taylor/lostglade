package com.lostglade.raceclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class Lg2RaceClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PayloadTypeRegistry.playC2S().register(RaceAbilityPayload.TYPE, RaceAbilityPayload.STREAM_CODEC);
		RaceClientControls.register();
	}
}
