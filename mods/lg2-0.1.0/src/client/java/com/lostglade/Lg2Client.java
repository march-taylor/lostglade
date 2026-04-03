package com.lostglade;

import com.lostglade.client.RendererBotClientCapture;
import com.lostglade.client.RendererBotClientMode;
import com.lostglade.client.RendererBotClientVideoRecording;
import com.lostglade.network.RendererBotPayloads;
import net.fabricmc.api.ClientModInitializer;

public class Lg2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RendererBotPayloads.registerPayloadTypes();
		RendererBotClientMode.register();
		RendererBotClientCapture.register();
		RendererBotClientVideoRecording.register();
	}
}
