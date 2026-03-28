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
	private static final Map<UUID, UUID> TARGET_BY_MIRRORED_SOURCE = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<UUID>> MIRRORED_SOURCES_BY_TARGET = new ConcurrentHashMap<>();

	private CartelWebcamBridge() {
	}

	public static void beginDisguise(UUID cartelId, UUID targetId) {
		beginSourceMirror(cartelId, targetId);
	}

	public static void endDisguise(UUID cartelId) {
		endSourceMirror(cartelId);
	}

	public static void beginSourceMirror(UUID mirroredSourceId, UUID targetSourceId) {
		if (mirroredSourceId == null || targetSourceId == null || mirroredSourceId.equals(targetSourceId)) {
			return;
		}

		removeMapping(mirroredSourceId);
		TARGET_BY_MIRRORED_SOURCE.put(mirroredSourceId, targetSourceId);
		MIRRORED_SOURCES_BY_TARGET.computeIfAbsent(targetSourceId, ignored -> ConcurrentHashMap.newKeySet()).add(mirroredSourceId);
		ServerWebcamFrameCache.beginMirror(mirroredSourceId, targetSourceId);
		closeMirroredSource(mirroredSourceId);
	}

	public static void endSourceMirror(UUID mirroredSourceId) {
		if (mirroredSourceId == null) {
			return;
		}

		removeMapping(mirroredSourceId);
		ServerWebcamFrameCache.endMirror(mirroredSourceId);
		closeMirroredSource(mirroredSourceId);
	}

	public static void clearAll() {
		TARGET_BY_MIRRORED_SOURCE.clear();
		MIRRORED_SOURCES_BY_TARGET.clear();
		ServerWebcamFrameCache.clearMirrors();
	}

	public static void handlePlayerDisconnected(UUID playerId) {
		if (playerId == null) {
			return;
		}
		ServerWebcamFrameCache.removeSource(playerId);
		ServerWebcamFrameCache.endMirror(playerId);
		endSourceMirror(playerId);

		Set<UUID> mirroredSources = MIRRORED_SOURCES_BY_TARGET.get(playerId);
		if (mirroredSources == null || mirroredSources.isEmpty()) {
			return;
		}

		for (UUID mirroredSourceId : Set.copyOf(mirroredSources)) {
			endSourceMirror(mirroredSourceId);
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
			if (TARGET_BY_MIRRORED_SOURCE.containsKey(senderId)) {
				return true;
			}

			ServerWebcamFrameCache.handleVideoPacket(senderId, videoPacket);
			mirrorVideoPacket(webcamServer, senderId, videoPacket);
			return false;
		}

		if (packet instanceof CloseSourceC2SPacket) {
			ServerWebcamFrameCache.removeSource(senderId);
			mirrorClosePacket(webcamServer, senderId);
		}

		return false;
	}

	private static void mirrorVideoPacket(WebcamServer webcamServer, UUID targetId, VideoC2SPacket videoPacket) {
		Set<UUID> mirroredSources = MIRRORED_SOURCES_BY_TARGET.getOrDefault(targetId, Collections.emptySet());
		if (mirroredSources.isEmpty() || videoPacket == null) {
			return;
		}

		ServerConfig config = getServerConfig();
		if (config == null) {
			return;
		}

		for (UUID mirroredSourceId : mirroredSources) {
			VideoSource source = createSource(mirroredSourceId, config);
			sendToNearbyPlayers(
					webcamServer,
					mirroredSourceId,
					new VideoS2CPacket(source, videoPacket.nal()),
					config.maxDisplayDistance(),
					config.displaySelfWebcam(),
					false
			);
		}
	}

	private static void mirrorClosePacket(WebcamServer webcamServer, UUID targetId) {
		Set<UUID> mirroredSources = MIRRORED_SOURCES_BY_TARGET.getOrDefault(targetId, Collections.emptySet());
		if (mirroredSources.isEmpty()) {
			return;
		}

		for (UUID mirroredSourceId : mirroredSources) {
			closeMirroredSource(webcamServer, mirroredSourceId);
		}
	}

	private static void closeMirroredSource(UUID mirroredSourceId) {
		closeMirroredSource(WebcamServer.getInstance(), mirroredSourceId);
	}

	private static void closeMirroredSource(WebcamServer webcamServer, UUID mirroredSourceId) {
		if (webcamServer == null || mirroredSourceId == null) {
			return;
		}

		ServerConfig config = getServerConfig();
		if (config == null) {
			return;
		}

		sendToNearbyPlayers(
				webcamServer,
				mirroredSourceId,
				new CloseSourceS2CPacket(mirroredSourceId),
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

	private static void removeMapping(UUID mirroredSourceId) {
		UUID previousTarget = TARGET_BY_MIRRORED_SOURCE.remove(mirroredSourceId);
		if (previousTarget == null) {
			return;
		}

		Set<UUID> mirroredSources = MIRRORED_SOURCES_BY_TARGET.get(previousTarget);
		if (mirroredSources == null) {
			return;
		}

		mirroredSources.remove(mirroredSourceId);
		if (mirroredSources.isEmpty()) {
			MIRRORED_SOURCES_BY_TARGET.remove(previousTarget);
		}
	}
}
