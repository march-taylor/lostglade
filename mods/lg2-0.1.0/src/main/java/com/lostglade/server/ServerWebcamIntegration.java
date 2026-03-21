package com.lostglade.server;

import com.lostglade.Lg2;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import ru.dimaskama.webcam.Webcam;
import ru.dimaskama.webcam.WebcamService;
import ru.dimaskama.webcam.message.Channel;
import ru.dimaskama.webcam.message.Message;
import ru.dimaskama.webcam.message.ServerMessaging;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class ServerWebcamIntegration {

	private ServerWebcamIntegration() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!isLoaded()) {
				Lg2.LOGGER.warn("Webcam is not loaded even though it is listed as a dependency");
				return;
			}
			Lg2.LOGGER.info("Connected Webcam integration");
		});
	}

	public static boolean isLoaded() {
		return FabricLoader.getInstance().isModLoaded("webcam");
	}

	public static WebcamService getService() {
		try {
			return Webcam.getService();
		} catch (Throwable throwable) {
			Lg2.LOGGER.warn("Failed to access Webcam API", throwable);
			return null;
		}
	}

	public static <T extends Message> void registerChannel(Channel<T> channel, ServerMessaging.ServerHandler<T> handler) {
		WebcamService service = getService();
		if (service != null) {
			service.registerChannel(channel, handler);
		}
	}

	public static void sendToPlayer(UUID playerId, Message message) {
		WebcamService service = getService();
		if (service != null) {
			service.sendToPlayer(playerId, message);
		}
	}

	public static void sendSystemMessage(UUID playerId, String message) {
		WebcamService service = getService();
		if (service != null) {
			service.sendSystemMessage(playerId, message);
		}
	}

	public static void acceptForNearbyPlayers(UUID sourcePlayerId, double radius, Consumer<Set<UUID>> consumer) {
		WebcamService service = getService();
		if (service != null) {
			service.acceptForNearbyPlayers(sourcePlayerId, radius, consumer);
		}
	}

	public static boolean checkBroadcastPermission(UUID playerId) {
		WebcamService service = getService();
		return service != null && service.checkWebcamBroadcastPermission(playerId);
	}

	public static boolean checkViewPermission(UUID playerId) {
		WebcamService service = getService();
		return service != null && service.checkWebcamViewPermission(playerId);
	}
}
