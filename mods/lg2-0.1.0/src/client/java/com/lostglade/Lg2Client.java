package com.lostglade;

import com.lostglade.client.RendererBotClientCapture;
import com.lostglade.client.RendererBotClientMode;
import com.lostglade.client.RendererBotClientVideoRecording;
import com.lostglade.network.RendererBotPayloads;
import com.lostglade.server.CameraMediaCache;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class Lg2Client implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CameraMediaCache.initialize(FabricLoader.getInstance().getGameDir());
		RendererBotPayloads.registerPayloadTypes();
		RendererBotClientMode.register();
		RendererBotClientCapture.register();
		RendererBotClientVideoRecording.register();
	}
}
