package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenBackgroundLoader.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveCameraPlayback.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenMediaFrameRuntime.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
import static com.lostglade.server.MonitorScreenMediaLoadResults.*;
import static com.lostglade.server.MonitorScreenMediaSessionLifecycle.*;
import static com.lostglade.server.MonitorScreenPlaybackScheduler.*;
import static com.lostglade.server.MonitorScreenTickScheduler.*;
import static com.lostglade.server.MonitorScreenYoutubeQueueRuntime.*;

import com.lostglade.Lg2;
import com.lostglade.block.CameraBlock;
import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
import com.lostglade.item.MonitorItem;
import com.lostglade.item.PhotoPrintData;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.lostglade.server.monitor.MonitorApp;
import com.lostglade.server.monitor.MonitorAppRole;
import com.lostglade.server.monitor.MonitorAppRegistry;
import com.lostglade.server.monitor.MonitorBackgroundPlaybackPolicy;
import com.lostglade.server.monitor.MonitorMediaApp;
import com.lostglade.server.monitor.MonitorSberDronesCatalog;
import com.lostglade.server.monitor.MonitorYoutubeRelayClient;
import com.lostglade.server.monitor.MonitorYoutubeMusicCache;
import com.lostglade.server.progress.TaskProgress;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class MonitorScreenWireConnectivity {
	private MonitorScreenWireConnectivity() {
	}

	static Map<ScreenRuntimeKey, ScreenComponent> collectConnectedSpeakerComponents(ServerLevel level, BlockPos speakerPos) {
		return collectConnectedComponentsForWireSource(level, speakerPos);
	}

	public static void onCameraNetworkChanged(ServerLevel level, BlockPos cameraPos) {
		if (level == null || cameraPos == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return;
		}
		LiveCameraReference changedCamera = LiveCameraReference.camera(level.dimension(), cameraPos.immutable());
		Set<ScreenRuntimeKey> targets = new LinkedHashSet<>(collectConnectedComponentsForWireSource(level, cameraPos).keySet());
		for (MonitorLevelState monitorState : LEVEL_STATES.values()) {
			for (Map.Entry<ScreenRuntimeKey, List<LiveCameraReference>> entry : monitorState.connectedCameraPositions().entrySet()) {
				ScreenRuntimeKey runtimeKey = entry.getKey();
				List<LiveCameraReference> connectedCameraPositions = entry.getValue();
				if (runtimeKey == null
						|| connectedCameraPositions == null
						|| connectedCameraPositions.stream().noneMatch(candidate -> sameLiveCameraIdentity(candidate, changedCamera))) {
					continue;
				}
				targets.add(runtimeKey);
			}
		}
		triggerLiveCameraTargets(server, targets);
	}

	public static void onDroneNetworkChanged(MinecraftServer server, BluetoothLinkSystem.Endpoint endpoint) {
		if (server == null || endpoint == null || endpoint.type() != BluetoothLinkSystem.EndpointType.DRONE || endpoint.deviceUuid() == null) {
			return;
		}
		DroneSystem.DroneLiveFeedState droneState = DroneSystem.resolveLiveFeedState(server, endpoint);
		LiveCameraReference changedDrone = droneState != null
				? LiveCameraReference.drone(droneState.dimension(), droneState.pos(), droneState.droneUuid())
				: LiveCameraReference.drone(endpoint.dimension(), endpoint.pos(), endpoint.deviceUuid());
		Set<ScreenRuntimeKey> targets = new LinkedHashSet<>();
		for (MonitorLevelState monitorState : LEVEL_STATES.values()) {
			for (Map.Entry<ScreenRuntimeKey, List<LiveCameraReference>> entry : monitorState.connectedCameraPositions().entrySet()) {
				ScreenRuntimeKey runtimeKey = entry.getKey();
				List<LiveCameraReference> connectedCameraPositions = entry.getValue();
				if (runtimeKey == null
						|| connectedCameraPositions == null
						|| connectedCameraPositions.stream().noneMatch(candidate -> sameLiveCameraIdentity(candidate, changedDrone))) {
					continue;
				}
				targets.add(runtimeKey);
			}
		}
		triggerLiveCameraTargets(server, targets);
	}

	public static List<DroneSystem.DroneScreenStreamReference> collectActiveDroneScreenStreams(MinecraftServer server) {
		if (server == null) {
			return List.of();
		}
		Map<UUID, DroneSystem.DroneScreenStreamReference> streams = new LinkedHashMap<>();
		collectPoweredLinkedDroneStreams(server, streams);
		for (Map.Entry<ScreenRuntimeKey, MediaRuntimeState> entry : MEDIA_STATES.entrySet()) {
			ScreenRuntimeKey runtimeKey = entry.getKey();
			MediaRuntimeState state = entry.getValue();
			if (runtimeKey == null || state == null) {
				continue;
			}
			ServerLevel screenLevel = server.getLevel(runtimeKey.dimension());
			if (screenLevel == null) {
				continue;
			}
			ScreenComponent component = resolveScreenComponent(server, runtimeKey);
			if (component == null
					|| !component.powered()
					|| component.viewMode() != ScreenViewMode.SBER_DRONES
					|| !hasNearbyMediaViewer(screenLevel, component)) {
				continue;
			}
			String sourceUrl;
			synchronized (state) {
				if (state.mode != ScreenViewMode.SBER_DRONES
						|| state.streamKind != PlaybackStreamKind.LIVE_CAMERA
						|| state.sourceUrl == null
						|| state.sourceUrl.isBlank()) {
					continue;
				}
				sourceUrl = state.sourceUrl;
			}
			LiveCameraReference cameraRef = liveCameraGalleryReference(sourceUrl, runtimeKey.dimension());
			if (cameraRef == null
					|| cameraRef.sourceType() != LiveCameraSourceType.DRONE
					|| cameraRef.sourceUuid() == null) {
				continue;
			}
			addDroneScreenStreamReference(streams, server, cameraRef.sourceUuid(), cameraRef.dimension(), cameraRef.pos(), runtimeKey.dimension());
		}
		return List.copyOf(streams.values());
	}

	private static void collectPoweredLinkedDroneStreams(
			MinecraftServer server,
			Map<UUID, DroneSystem.DroneScreenStreamReference> streams
	) {
		if (server == null || streams == null || LEVEL_STATES.isEmpty()) {
			return;
		}
		for (MonitorLevelState monitorState : LEVEL_STATES.values()) {
			if (monitorState == null) {
				continue;
			}
			ServerLevel level = server.getLevel(monitorState.dimension());
			if (level == null) {
				continue;
			}
			for (ScreenComponent component : monitorState.components().values()) {
				if (component == null || !component.powered()) {
					continue;
				}
				BluetoothLinkSystem.Endpoint screenEndpoint = bluetoothScreenEndpoint(level, component);
				if (screenEndpoint == null) {
					continue;
				}
				for (BluetoothLinkSystem.Endpoint linked : BluetoothLinkSystem.linkedEndpoints(screenEndpoint)) {
					if (linked == null || linked.type() != BluetoothLinkSystem.EndpointType.DRONE || linked.deviceUuid() == null) {
						continue;
					}
					addDroneScreenStreamReference(streams, server, linked.deviceUuid(), linked.dimension(), linked.pos(), level.dimension());
				}
			}
		}
	}

	private static void addDroneScreenStreamReference(
			Map<UUID, DroneSystem.DroneScreenStreamReference> streams,
			MinecraftServer server,
			UUID droneUuid,
			ResourceKey<Level> fallbackDimension,
			BlockPos fallbackPos,
			ResourceKey<Level> defaultDimension
	) {
		if (streams == null || server == null || droneUuid == null || streams.containsKey(droneUuid)) {
			return;
		}
		DroneSystem.DroneLiveFeedState droneState = DroneSystem.resolveLiveFeedState(server, droneUuid, fallbackDimension, fallbackPos);
		ResourceKey<Level> dimension = droneState != null && droneState.dimension() != null
				? droneState.dimension()
				: fallbackDimension != null ? fallbackDimension : defaultDimension;
		BlockPos pos = droneState != null && droneState.pos() != null
				? droneState.pos().immutable()
				: fallbackPos != null ? fallbackPos.immutable() : BlockPos.ZERO;
		if (dimension == null) {
			return;
		}
		streams.put(droneUuid, new DroneSystem.DroneScreenStreamReference(droneUuid, dimension, pos));
	}

	static void triggerLiveCameraTargets(MinecraftServer server, Set<ScreenRuntimeKey> targets) {
		if (server == null || targets == null || targets.isEmpty()) {
			return;
		}
		for (ScreenRuntimeKey target : targets) {
			MediaRuntimeState state = MEDIA_STATES.get(target);
			if (state != null) {
				synchronized (state) {
					state.nextLiveCameraGallerySyncAtMillis = 0L;
				}
			}
			ServerLevel targetLevel = server.getLevel(target.dimension());
			if (targetLevel != null) {
				enqueueCameraRefresh(targetLevel, target);
			}
		}
	}

	static Map<ScreenRuntimeKey, ScreenComponent> collectConnectedComponentsForWireSource(ServerLevel level, BlockPos originPos) {
		if (level == null || originPos == null || !level.hasChunkAt(originPos)) {
			return Map.of();
		}
		Set<BlockPos> wireNetwork = collectSpeakerWireNetwork(level, originPos);
		BluetoothLinkSystem.Endpoint originEndpoint = BluetoothLinkSystem.resolveBlockEndpoint(level, originPos);
		Map<ScreenRuntimeKey, ScreenComponent> connectedComponents = new HashMap<>();
		for (ScreenComponent component : cachedComponents(level)) {
			boolean connected = isSpeakerConnectedToComponent(originPos, component, wireNetwork);
			if (!connected && originEndpoint != null) {
				BluetoothLinkSystem.Endpoint screenEndpoint = bluetoothScreenEndpoint(level, component);
				connected = screenEndpoint != null && BluetoothLinkSystem.areLinked(originEndpoint, screenEndpoint);
			}
			if (!connected) {
				continue;
			}
			connectedComponents.putIfAbsent(component.runtimeKey(), component);
		}
		if (originEndpoint != null && level.getServer() != null) {
			for (BluetoothLinkSystem.Endpoint linked : BluetoothLinkSystem.linkedEndpoints(originEndpoint)) {
				if (linked.type() != BluetoothLinkSystem.EndpointType.SCREEN) {
					continue;
				}
				ServerLevel linkedLevel = level.getServer().getLevel(linked.dimension());
				if (linkedLevel == null) {
					continue;
				}
				ScreenComponent linkedComponent = resolveBluetoothScreenComponent(linkedLevel, linked);
				if (linkedComponent != null) {
					connectedComponents.putIfAbsent(linkedComponent.runtimeKey(), linkedComponent);
				}
			}
		}
		return connectedComponents;
	}

	static AABB speakerSearchBox(BlockPos speakerPos, Set<BlockPos> wireNetwork) {
		int minX = speakerPos.getX();
		int minY = speakerPos.getY();
		int minZ = speakerPos.getZ();
		int maxX = speakerPos.getX();
		int maxY = speakerPos.getY();
		int maxZ = speakerPos.getZ();
		for (BlockPos wirePos : wireNetwork) {
			minX = Math.min(minX, wirePos.getX());
			minY = Math.min(minY, wirePos.getY());
			minZ = Math.min(minZ, wirePos.getZ());
			maxX = Math.max(maxX, wirePos.getX());
			maxY = Math.max(maxY, wirePos.getY());
			maxZ = Math.max(maxZ, wirePos.getZ());
		}
		return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D).inflate(2.25D);
	}

	static Set<BlockPos> collectSpeakerWireNetwork(ServerLevel level, BlockPos speakerPos) {
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (BlockPos touchPos : redstoneTouchPoints(speakerPos)) {
			if (!isRedstoneWire(level, touchPos) || !visited.add(touchPos.immutable())) {
				continue;
			}
			queue.add(touchPos.immutable());
		}
		while (!queue.isEmpty()) {
			BlockPos current = queue.removeFirst();
			for (BlockPos neighbor : redstoneWireNeighbors(current)) {
				if (!isRedstoneWire(level, neighbor) || !visited.add(neighbor.immutable())) {
					continue;
				}
				queue.add(neighbor.immutable());
			}
		}
		return visited;
	}

	static boolean isRedstoneWire(ServerLevel level, BlockPos pos) {
		return level != null && pos != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.REDSTONE_WIRE);
	}

	static List<BlockPos> redstoneTouchPoints(BlockPos pos) {
		return List.of(
				pos,
				pos.above(),
				pos.below(),
				pos.north(),
				pos.south(),
				pos.east(),
				pos.west(),
				pos.above().north(),
				pos.above().south(),
				pos.above().east(),
				pos.above().west(),
				pos.below().north(),
				pos.below().south(),
				pos.below().east(),
				pos.below().west()
		);
	}

	static List<BlockPos> redstoneWireNeighbors(BlockPos pos) {
		List<BlockPos> neighbors = new ArrayList<>(14);
		neighbors.add(pos.north());
		neighbors.add(pos.south());
		neighbors.add(pos.east());
		neighbors.add(pos.west());
		neighbors.add(pos.above());
		neighbors.add(pos.below());
		neighbors.add(pos.above().north());
		neighbors.add(pos.above().south());
		neighbors.add(pos.above().east());
		neighbors.add(pos.above().west());
		neighbors.add(pos.below().north());
		neighbors.add(pos.below().south());
		neighbors.add(pos.below().east());
		neighbors.add(pos.below().west());
		return neighbors;
	}

	static boolean isSpeakerConnectedToComponent(BlockPos speakerPos, ScreenComponent component, Set<BlockPos> wireNetwork) {
		if (speakerPos == null || component == null) {
			return false;
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			BlockPos framePos = frame.blockPosition();
			BlockPos supportPos = framePos.relative(frame.getDirection().getOpposite());
			if (areBlocksAdjacent(speakerPos, framePos) || areBlocksAdjacent(speakerPos, supportPos)) {
				return true;
			}
			if (wireNetwork.isEmpty()) {
				continue;
			}
			for (BlockPos touchPos : redstoneTouchPoints(framePos)) {
				if (wireNetwork.contains(touchPos)) {
					return true;
				}
			}
			for (BlockPos touchPos : redstoneTouchPoints(supportPos)) {
				if (wireNetwork.contains(touchPos)) {
					return true;
				}
			}
		}
		return false;
	}

	static boolean areBlocksAdjacent(BlockPos first, BlockPos second) {
		if (first == null || second == null) {
			return false;
		}
		return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ()) <= 1;
	}










}
