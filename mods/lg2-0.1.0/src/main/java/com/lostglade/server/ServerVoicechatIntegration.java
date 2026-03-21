package com.lostglade.server;

import com.lostglade.Lg2;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class ServerVoicechatIntegration {

	private static volatile VoicechatApi api;
	private static volatile VoicechatServerApi serverApi;

	private ServerVoicechatIntegration() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!isLoaded()) {
				Lg2.LOGGER.warn("Voicechat is not loaded even though it is listed as a dependency");
				return;
			}
			Lg2.LOGGER.info("Connected Voicechat integration");
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			serverApi = null;
		});
	}

	public static boolean isLoaded() {
		return FabricLoader.getInstance().isModLoaded("voicechat");
	}

	public static VoicechatApi getApi() {
		return api;
	}

	public static VoicechatServerApi getServerApi() {
		return serverApi;
	}

	static void setApi(VoicechatApi api) {
		ServerVoicechatIntegration.api = api;
	}

	static void setServerApi(VoicechatServerApi serverApi) {
		ServerVoicechatIntegration.serverApi = serverApi;
	}
}
