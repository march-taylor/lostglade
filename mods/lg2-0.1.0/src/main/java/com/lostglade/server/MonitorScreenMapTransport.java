package com.lostglade.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.lostglade.server.MonitorScreenLiveCameraPlayback.*;
import static com.lostglade.server.MonitorScreenSystem.*;

final class MonitorScreenMapTransport {
	static final int MAP_SIZE = 128;
	private static final Map<Integer, byte[]> LAST_RENDERED_MAP_FRAMES = new ConcurrentHashMap<>();

	private MonitorScreenMapTransport() {
	}

	static byte[] lastRenderedMapFrame(int mapId) {
		return LAST_RENDERED_MAP_FRAMES.get(mapId);
	}

	static void clearRenderedMapFrames() {
		LAST_RENDERED_MAP_FRAMES.clear();
	}

	static boolean renderedMapFrameCacheEmpty() {
		return LAST_RENDERED_MAP_FRAMES.isEmpty();
	}

	static void retainRenderedMapFrames(Set<Integer> activeMapIds) {
		if (activeMapIds == null || activeMapIds.isEmpty()) {
			LAST_RENDERED_MAP_FRAMES.clear();
			return;
		}
		LAST_RENDERED_MAP_FRAMES.entrySet().removeIf(entry -> entry == null || !activeMapIds.contains(entry.getKey()));
	}

	static void applyFrameToMap(MapItemSavedData mapData, byte[] frame) {
		if (mapData == null || frame == null || frame.length < MAP_SIZE * MAP_SIZE || mapData.colors == null || mapData.colors.length < MAP_SIZE * MAP_SIZE) {
			return;
		}
		System.arraycopy(frame, 0, mapData.colors, 0, MAP_SIZE * MAP_SIZE);
		mapData.setDirty();
	}

	static void applyPatchToMap(MapItemSavedData mapData, MapPacketUpdate update) {
		if (mapData == null || update == null || mapData.colors == null || update.frame() == null) {
			return;
		}
		if (update.width() <= 0 || update.height() <= 0) {
			return;
		}
		for (int row = 0; row < update.height(); row++) {
			int sourceOffset = row * update.width();
			int targetOffset = (update.startY() + row) * MAP_SIZE + update.startX();
			System.arraycopy(update.frame(), sourceOffset, mapData.colors, targetOffset, update.width());
		}
		mapData.setDirty();
	}

	static List<PreparedMapUpdate> prepareRenderedMapUpdates(RenderWork work, byte[][] renderedTiles) {
		if (work == null || renderedTiles == null || work.tileTargets() == null || work.tileTargets().isEmpty()) {
			return List.of();
		}
		List<PreparedMapUpdate> updates = new ArrayList<>();
		for (RenderTileTarget target : work.tileTargets()) {
			if (target == null || target.tileIndex() < 0 || target.tileIndex() >= renderedTiles.length) {
				continue;
			}
			byte[] tileFrame = renderedTiles[target.tileIndex()];
			if (tileFrame == null || tileFrame.length < MAP_SIZE * MAP_SIZE) {
				continue;
			}
			MapPacketUpdate packetUpdate = buildMapUpdate(
					target.mapId(),
					target.scale(),
					target.locked(),
					target.baselineFrame(),
					tileFrame
			);
			if (packetUpdate == null) {
				continue;
			}
			updates.add(new PreparedMapUpdate(
					target.mapId(),
					target.scale(),
					target.locked(),
					packetUpdate.startX(),
					packetUpdate.startY(),
					packetUpdate.width(),
					packetUpdate.height(),
					packetUpdate.frame(),
					tileFrame,
					target.baselineFrame()
			));
		}
		return updates.isEmpty() ? List.of() : List.copyOf(updates);
	}

	static void applyRenderedTiles(ServerLevel level, ScreenComponent component, RenderedTileBatch renderedBatch) {
		if (level == null || component == null || renderedBatch == null || renderedBatch.renderedTiles() == null) {
			return;
		}
		if (renderedBatch.updates() == null || renderedBatch.updates().isEmpty()) {
			return;
		}
		ServerLevel mapStorageLevel = photoMapLevel(level.getServer(), level);
		List<MapPacketUpdate> changedUpdates = new ArrayList<>();
		Set<Integer> currentMapIds = currentComponentMapIds(component);
		for (PreparedMapUpdate prepared : renderedBatch.updates()) {
			if (prepared == null || prepared.mapId() == null || !currentMapIds.contains(prepared.mapId().id())) {
				continue;
			}
			MapItemSavedData mapData = mapStorageLevel.getMapData(prepared.mapId());
			if (mapData == null) {
				continue;
			}
			MapPacketUpdate update;
			if (renderBaselineMatches(prepared.mapId(), prepared.baselineFrame())) {
				update = new MapPacketUpdate(
						prepared.mapId(),
						mapData.scale,
						mapData.locked,
						prepared.startX(),
						prepared.startY(),
						prepared.width(),
						prepared.height(),
						prepared.frame()
				);
				LAST_RENDERED_MAP_FRAMES.put(prepared.mapId().id(), prepared.fullFrame());
			} else {
				update = buildRenderedMapUpdate(prepared.mapId(), mapData.scale, mapData.locked, prepared.fullFrame());
			}
			if (update == null) {
				continue;
			}
			applyPatchToMap(mapData, update);
			changedUpdates.add(update);
		}
		sendMapToPlayers(level, component, changedUpdates);
	}

	static Set<Integer> currentComponentMapIds(ScreenComponent component) {
		if (component == null || component.frameCoords().isEmpty()) {
			return Set.of();
		}
		Set<Integer> ids = new java.util.HashSet<>();
		for (ItemFrame frame : component.frameCoords().keySet()) {
			if (frame == null || !frame.isAlive()) {
				continue;
			}
			MapId mapId = frame.getItem().get(DataComponents.MAP_ID);
			if (mapId != null) {
				ids.add(mapId.id());
			}
		}
		return ids;
	}

	static boolean renderBaselineMatches(MapId mapId, byte[] expectedBaseline) {
		if (mapId == null) {
			return false;
		}
		byte[] currentBaseline = LAST_RENDERED_MAP_FRAMES.get(mapId.id());
		if (currentBaseline == expectedBaseline) {
			return true;
		}
		if (currentBaseline == null || expectedBaseline == null) {
			return false;
		}
		return Arrays.equals(currentBaseline, expectedBaseline);
	}

	static void applyPreparedRenderedTiles(ServerLevel level, ScreenComponent component, MediaRuntimeState mediaState, PreparedRenderedTiles preparedTiles) {
		if (level == null || component == null || preparedTiles == null || preparedTiles.renderedTiles() == null) {
			return;
		}
		ServerLevel mapStorageLevel = photoMapLevel(level.getServer(), level);
		List<MapPacketUpdate> changedUpdates = new ArrayList<>();
		byte[][] renderedTiles = preparedTiles.renderedTiles();
		TileFramePatch[] tilePatches = resolvePreparedRenderedTilePatches(mediaState, preparedTiles);
		boolean changed = false;
		for (Map.Entry<ItemFrame, TileCoord> entry : component.frameCoords().entrySet()) {
			ItemFrame frame = entry.getKey();
			ItemStack frameStack = frame.getItem();
			ScreenTileState tileState = readScreenState(frameStack);
			MapId mapId = frameStack.get(DataComponents.MAP_ID);
			if (tileState == null || mapId == null) {
				continue;
			}
			MapItemSavedData mapData = mapStorageLevel.getMapData(mapId);
			if (mapData == null) {
				continue;
			}
			int tileIndex = tileState.tileY() * component.width() + tileState.tileX();
			if (tileIndex < 0 || tileIndex >= renderedTiles.length) {
				continue;
			}
			byte[] tileFrame = renderedTiles[tileIndex];
			if (tileFrame == null || tileFrame.length < MAP_SIZE * MAP_SIZE) {
				continue;
			}
			TileFramePatch tilePatch = tilePatches != null && tileIndex < tilePatches.length ? tilePatches[tileIndex] : null;
			if (tilePatch == null || tilePatch.frame() == null || tilePatch.width() <= 0 || tilePatch.height() <= 0) {
				continue;
			}
			MapPacketUpdate update = new MapPacketUpdate(
					mapId,
					mapData.scale,
					mapData.locked,
					tilePatch.startX(),
					tilePatch.startY(),
					tilePatch.width(),
					tilePatch.height(),
					tilePatch.frame()
			);
			changed = true;
			LAST_RENDERED_MAP_FRAMES.put(mapId.id(), tileFrame);
			applyPatchToMap(mapData, update);
			changedUpdates.add(update);
		}
		if (mediaState != null) {
			synchronized (mediaState) {
				mediaState.liveCameraBufferedTiles = renderedTiles;
				mediaState.liveCameraDisplayedTiles = renderedTiles;
				if (changed) {
					mediaState.liveCameraDisplayedGeneration++;
				}
			}
		}
		sendMapToPlayers(level, component, changedUpdates);
	}

	static TileFramePatch[] resolvePreparedRenderedTilePatches(MediaRuntimeState state, PreparedRenderedTiles preparedTiles) {
		if (preparedTiles == null || preparedTiles.renderedTiles() == null) {
			return new TileFramePatch[0];
		}
		TileFramePatch[] preparedPatches = preparedTiles.tilePatches();
		boolean hasCompletePreparedPatchSet = preparedPatches != null && preparedPatches.length == preparedTiles.renderedTiles().length;
		if (state == null) {
			return hasCompletePreparedPatchSet ? preparedPatches : fullFrameTilePatches(preparedTiles.renderedTiles());
		}
		long displayedGeneration;
		synchronized (state) {
			displayedGeneration = state.liveCameraDisplayedGeneration;
		}
		if (displayedGeneration == preparedTiles.baselineGeneration() && hasCompletePreparedPatchSet) {
			return preparedPatches;
		}
		return fullFrameTilePatches(preparedTiles.renderedTiles());
	}

	private static TileFramePatch[] fullFrameTilePatches(byte[][] renderedTiles) {
		if (renderedTiles == null || renderedTiles.length == 0) {
			return new TileFramePatch[0];
		}
		TileFramePatch[] patches = new TileFramePatch[renderedTiles.length];
		for (int tileIndex = 0; tileIndex < renderedTiles.length; tileIndex++) {
			byte[] tileFrame = renderedTiles[tileIndex];
			if (tileFrame == null || tileFrame.length < MAP_SIZE * MAP_SIZE) {
				continue;
			}
			patches[tileIndex] = new TileFramePatch(0, 0, MAP_SIZE, MAP_SIZE, tileFrame);
		}
		return patches;
	}

	static MapPacketUpdate buildRenderedMapUpdate(MapId mapId, byte scale, boolean locked, byte[] tileFrame) {
		if (mapId == null || tileFrame == null || tileFrame.length < MAP_SIZE * MAP_SIZE) {
			return null;
		}
		byte[] previous = LAST_RENDERED_MAP_FRAMES.get(mapId.id());
		MapPacketUpdate update = buildMapUpdate(mapId, scale, locked, previous, tileFrame);
		if (update != null) {
			LAST_RENDERED_MAP_FRAMES.put(mapId.id(), tileFrame);
		}
		return update;
	}

	static MapPacketUpdate buildMapUpdate(MapId mapId, byte scale, boolean locked, byte[] previous, byte[] tileFrame) {
		if (mapId == null || tileFrame == null || tileFrame.length < MAP_SIZE * MAP_SIZE) {
			return null;
		}
		if (previous == null || previous.length < MAP_SIZE * MAP_SIZE) {
			return new MapPacketUpdate(mapId, scale, locked, 0, 0, MAP_SIZE, MAP_SIZE, tileFrame);
		}

		int minX = MAP_SIZE;
		int minY = MAP_SIZE;
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < MAP_SIZE; y++) {
			int rowStart = y * MAP_SIZE;
			for (int x = 0; x < MAP_SIZE; x++) {
				int index = rowStart + x;
				if (previous[index] == tileFrame[index]) {
					continue;
				}
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}
		if (maxX < minX || maxY < minY) {
			return null;
		}

		int patchWidth = maxX - minX + 1;
		int patchHeight = maxY - minY + 1;
		if (patchWidth == MAP_SIZE && patchHeight == MAP_SIZE) {
			return new MapPacketUpdate(mapId, scale, locked, 0, 0, MAP_SIZE, MAP_SIZE, tileFrame);
		}
		byte[] patch = new byte[patchWidth * patchHeight];
		for (int row = 0; row < patchHeight; row++) {
			int sourceOffset = (minY + row) * MAP_SIZE + minX;
			System.arraycopy(tileFrame, sourceOffset, patch, row * patchWidth, patchWidth);
		}
		return new MapPacketUpdate(mapId, scale, locked, minX, minY, patchWidth, patchHeight, patch);
	}

	static void forgetRenderedMapFrame(ItemStack stack) {
		if (stack == null) {
			return;
		}
		MapId mapId = stack.get(DataComponents.MAP_ID);
		if (mapId != null) {
			LAST_RENDERED_MAP_FRAMES.remove(mapId.id());
		}
	}

	static void sendMapToPlayers(ServerLevel level, ScreenComponent component, List<MapPacketUpdate> changedUpdates) {
		if (level == null || component == null || changedUpdates == null || changedUpdates.isEmpty()) {
			return;
		}
		List<ServerPlayer> recipients = collectMapRecipients(level, component);
		if (recipients.isEmpty()) {
			return;
		}
		for (MapPacketUpdate update : changedUpdates) {
			ClientboundMapItemDataPacket packet = new ClientboundMapItemDataPacket(
					update.mapId(),
					update.scale(),
					update.locked(),
					List.of(),
					new MapItemSavedData.MapPatch(update.startX(), update.startY(), update.width(), update.height(), update.frame())
			);
			for (ServerPlayer player : recipients) {
				player.connection.send(packet);
			}
		}
	}

	static List<ServerPlayer> collectMapRecipients(ServerLevel level, ScreenComponent component) {
		if (level == null || component == null) {
			return List.of();
		}
		LinkedHashSet<ServerPlayer> recipients = new LinkedHashSet<>(RendererBotCameraSystem.activeBotsRenderingLevel(level));
		if (!isPlayerMode(component.viewMode())) {
			recipients.addAll(level.players());
			return new ArrayList<>(recipients);
		}
		double radiusBlocks = monitorMapUpdateRadiusBlocks();
		double radiusSquared = radiusBlocks * radiusBlocks;
		for (ServerPlayer player : level.players()) {
			if (isPlayerNearScreenComponent(player, component, radiusSquared)) {
				recipients.add(player);
			}
		}
		return new ArrayList<>(recipients);
	}
}
