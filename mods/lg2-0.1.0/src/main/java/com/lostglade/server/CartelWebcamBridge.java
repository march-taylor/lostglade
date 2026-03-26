package com.lostglade.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import ru.dimaskama.webcam.Webcam;
import ru.dimaskama.webcam.config.ServerConfig;
import ru.dimaskama.webcam.net.VideoSource;
import ru.dimaskama.webcam.net.packet.CloseSourceC2SPacket;
import ru.dimaskama.webcam.net.packet.CloseSourceS2CPacket;
import ru.dimaskama.webcam.net.packet.Packet;
import ru.dimaskama.webcam.net.packet.VideoC2SPacket;
import ru.dimaskama.webcam.net.packet.VideoS2CPacket;
import ru.dimaskama.webcam.server.C2SPacket;
import ru.dimaskama.webcam.server.PlayerState;
import ru.dimaskama.webcam.server.S2CEncodedPacket;
import ru.dimaskama.webcam.server.WebcamServer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CartelWebcamBridge {
	private static final Map<UUID, UUID> TARGET_BY_CARTEL = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<UUID>> CARTELS_BY_TARGET = new ConcurrentHashMap<>();

	private CartelWebcamBridge() {
	}

	public static void beginDisguise(UUID cartelId, UUID targetId) {
		if (cartelId == null || targetId == null || cartelId.equals(targetId)) {
			return;
		}

		removeMapping(cartelId);
		TARGET_BY_CARTEL.put(cartelId, targetId);
		CARTELS_BY_TARGET.computeIfAbsent(targetId, ignored -> ConcurrentHashMap.newKeySet()).add(cartelId);
		closeCartelSource(cartelId);
	}

	public static void endDisguise(UUID cartelId) {
		if (cartelId == null) {
			return;
		}

		removeMapping(cartelId);
		closeCartelSource(cartelId);
	}

	public static void clearAll() {
		TARGET_BY_CARTEL.clear();
		CARTELS_BY_TARGET.clear();
	}

	public static void handlePlayerDisconnected(UUID playerId) {
		if (playerId == null) {
			return;
		}

		Set<UUID> mirroredCartels = CARTELS_BY_TARGET.get(playerId);
		if (mirroredCartels == null || mirroredCartels.isEmpty()) {
			return;
		}

		for (UUID cartelId : mirroredCartels) {
			closeCartelSource(cartelId);
		}
	}

	public static boolean handleIncomingPacket(WebcamServer webcamServer, C2SPacket c2sPacket) {
		if (webcamServer == null || c2sPacket == null || !ServerWebcamIntegration.isLoaded()) {
			return false;
		}

		PlayerState sender = c2sPacket.sender();
		Packet packet = c2sPacket.packet();
		if (sender == null || packet == null) {
			return false;
		}

		UUID senderId = sender.getUuid();
		if (senderId == null) {
			return false;
		}

		if (packet instanceof VideoC2SPacket videoPacket) {
			if (TARGET_BY_CARTEL.containsKey(senderId)) {
				return true;
			}

			mirrorVideoPacket(webcamServer, senderId, videoPacket);
			return false;
		}

		if (packet instanceof CloseSourceC2SPacket) {
			mirrorClosePacket(webcamServer, senderId);
		}

		return false;
	}

	private static void mirrorVideoPacket(WebcamServer webcamServer, UUID targetId, VideoC2SPacket videoPacket) {
		Set<UUID> mirroredCartels = CARTELS_BY_TARGET.getOrDefault(targetId, Collections.emptySet());
		if (mirroredCartels.isEmpty() || videoPacket == null) {
			return;
		}

		ServerConfig config = getServerConfig();
		if (config == null) {
			return;
		}

		for (UUID cartelId : mirroredCartels) {
			VideoSource source = createSource(cartelId, config);
			sendToNearbyPlayers(
					webcamServer,
					cartelId,
					new VideoS2CPacket(source, videoPacket.nal()),
					config.maxDisplayDistance(),
					config.displaySelfWebcam(),
					false
			);
		}
	}

	private static void mirrorClosePacket(WebcamServer webcamServer, UUID targetId) {
		Set<UUID> mirroredCartels = CARTELS_BY_TARGET.getOrDefault(targetId, Collections.emptySet());
		if (mirroredCartels.isEmpty()) {
			return;
		}

		for (UUID cartelId : mirroredCartels) {
			closeCartelSource(webcamServer, cartelId);
		}
	}

	private static void closeCartelSource(UUID cartelId) {
		closeCartelSource(WebcamServer.getInstance(), cartelId);
	}

	private static void closeCartelSource(WebcamServer webcamServer, UUID cartelId) {
		if (webcamServer == null || cartelId == null) {
			return;
		}

		ServerConfig config = getServerConfig();
		if (config == null) {
			return;
		}

		sendToNearbyPlayers(
				webcamServer,
				cartelId,
				new CloseSourceS2CPacket(cartelId),
				config.maxDisplayDistance(),
				config.displaySelfWebcam(),
				true
		);
	}

	private static void sendToNearbyPlayers(
			WebcamServer webcamServer,
			UUID sourceUuid,
			Packet packet,
			double maxDistance,
			boolean includeSelf,
			boolean ignoreViewSettings
	) {
		if (webcamServer == null || sourceUuid == null || packet == null || !ServerWebcamIntegration.isLoaded()) {
			return;
		}

		ByteBuf encoded = Unpooled.buffer(packet.getEstimatedSizeWithId());
		packet.encodeWithId(encoded);
		try {
			ServerWebcamIntegration.acceptForNearbyPlayers(sourceUuid, maxDistance, nearbyPlayers -> {
				if (nearbyPlayers == null || nearbyPlayers.isEmpty()) {
					return;
				}

				boolean sentAny = false;
				for (UUID viewerId : nearbyPlayers) {
					PlayerState viewer = webcamServer.getPlayerState(viewerId);
					if (!canReceive(viewer, sourceUuid, nearbyPlayers, includeSelf, ignoreViewSettings)) {
						continue;
					}

					webcamServer.sendBatching(new S2CEncodedPacket(viewer, encoded.retainedDuplicate()));
					sentAny = true;
				}

				if (sentAny) {
					webcamServer.flushChannel();
				}
			});
		} finally {
			encoded.release();
		}
	}

	private static boolean canReceive(
			PlayerState viewer,
			UUID sourceUuid,
			Set<UUID> nearbyPlayers,
			boolean includeSelf,
			boolean ignoreViewSettings
	) {
		if (viewer == null || !viewer.isAuthenticated() || nearbyPlayers == null || !nearbyPlayers.contains(viewer.getUuid())) {
			return false;
		}

		if (!ignoreViewSettings && (!viewer.hasViewPermission() || !viewer.canShowWebcams() || viewer.isSourceBlocked(sourceUuid))) {
			return false;
		}

		return includeSelf || !viewer.getUuid().equals(sourceUuid);
	}

	private static VideoSource createSource(UUID sourceUuid, ServerConfig config) {
		if (config.displayOnFace()) {
			return new VideoSource.Face(sourceUuid, config.maxDisplayDistance());
		}

		return new VideoSource.AboveHead(
				sourceUuid,
				config.maxDisplayDistance(),
				config.displayShape(),
				config.displayOffsetY(),
				config.displaySize(),
				config.hideNicknames(),
				null
		);
	}

	private static ServerConfig getServerConfig() {
		if (Webcam.getServerConfig() == null) {
			return null;
		}

		Object data = Webcam.getServerConfig().getData();
		return data instanceof ServerConfig config ? config : null;
	}

	private static void removeMapping(UUID cartelId) {
		UUID previousTarget = TARGET_BY_CARTEL.remove(cartelId);
		if (previousTarget == null) {
			return;
		}

		Set<UUID> mirroredCartels = CARTELS_BY_TARGET.get(previousTarget);
		if (mirroredCartels == null) {
			return;
		}

		mirroredCartels.remove(cartelId);
		if (mirroredCartels.isEmpty()) {
			CARTELS_BY_TARGET.remove(previousTarget);
		}
	}
}
