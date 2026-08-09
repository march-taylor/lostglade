package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenMediaSessionLifecycle.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenTickScheduler.*;
import static com.lostglade.server.MonitorScreenWireConnectivity.*;

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

final class MonitorScreenLiveSources {
	private static final int[] MAP_PACKED_COLOR_ARGB = createMapPackedColorArgbLookup();

	private MonitorScreenLiveSources() {
	}

	private static int[] createMapPackedColorArgbLookup() {
		int[] lookup = new int[256];
		for (int packedId = 0; packedId < lookup.length; packedId++) {
			lookup[packedId] = 0xFF000000 | (MapColor.getColorFromPackedId(packedId) & 0xFFFFFF);
		}
		return lookup;
	}

	static boolean syncConnectedLiveCameraGalleryState(MinecraftServer server, ScreenComponent component, MediaRuntimeState state, List<LiveCameraReference> connectedCameraPositions) {
		if (component == null || state == null) {
			return false;
		}
		List<LiveCameraReference> resolvedPositions = connectedCameraPositions != null ? connectedCameraPositions : List.of();
		synchronized (state) {
			boolean relevant = state.mode == ScreenViewMode.SBER_DRONES
					&& (state.gallerySurfaceMode == GallerySurfaceMode.BROWSER
					|| state.streamKind == PlaybackStreamKind.LIVE_CAMERA
					|| hasLiveCameraItemsLocked(state));
			if (!relevant) {
				return false;
			}
			Map<String, GalleryItem> existingLiveItems = new LinkedHashMap<>();
			List<GalleryItem> rebuilt = new ArrayList<>(resolvedPositions.size());
			for (GalleryItem item : state.galleryItems) {
				if (isLiveCameraGalleryItem(item)) {
					existingLiveItems.put(item.url(), item);
				}
			}
			for (LiveCameraReference cameraRef : resolvedPositions) {
				boolean online = isLiveCameraOnline(server, cameraRef);
				String url = liveCameraGalleryUrl(cameraRef);
				ResourceKey<Level> displayDimension = cameraRef != null ? cameraRef.dimension() : null;
				BlockPos displayPos = cameraRef != null ? cameraRef.pos() : null;
				if (cameraRef != null && cameraRef.sourceType() == LiveCameraSourceType.DRONE && cameraRef.sourceUuid() != null) {
					DroneSystem.DroneLiveFeedState droneState = DroneSystem.resolveLiveFeedState(server, cameraRef.sourceUuid(), cameraRef.dimension(), cameraRef.pos());
					if (droneState != null) {
						if (droneState.dimension() != null) {
							displayDimension = droneState.dimension();
						}
						if (droneState.pos() != null) {
							displayPos = droneState.pos();
						}
						online = droneState.online();
					}
				}
				String title = liveSourceDisplayTitle(server, cameraRef, displayPos);
				String subtitle = formatLiveSourceCoordinates(displayPos);
				GalleryItem existing = existingLiveItems.get(url);
				if (existing != null
						&& Objects.equals(existing.title(), title)
						&& Objects.equals(existing.subtitle(), subtitle)
						&& existing.kind() == GalleryItemKind.LIVE_CAMERA) {
					rebuilt.add(existing);
				} else {
					BufferedImage preview = createLiveCameraPlaceholderPreview(title, subtitle, online, cameraRef != null ? cameraRef.sourceType() : LiveCameraSourceType.CAMERA);
					rebuilt.add(new GalleryItem(title, subtitle, url, null, null, preview, GalleryItemKind.LIVE_CAMERA));
				}
			}

			if (galleryItemsEqual(state.galleryItems, rebuilt)) {
				return false;
			}

			String currentSourceUrl = state.sourceUrl;
			boolean currentLivePlayback = state.streamKind == PlaybackStreamKind.LIVE_CAMERA;
			state.galleryItems.clear();
			state.galleryItems.addAll(rebuilt);

			if (currentSourceUrl != null && !currentSourceUrl.isBlank()) {
				int currentIndex = resolveGalleryItemIndex(state, currentSourceUrl, -1);
				if (currentIndex >= 0) {
					state.galleryIndex = currentIndex;
				} else if (currentLivePlayback) {
					int replacementIndex = rebuilt.isEmpty() ? -1 : clampInt(state.galleryIndex, 0, rebuilt.size() - 1);
					if (replacementIndex >= 0 && selectGalleryItemLocked(state, replacementIndex, createUiLayout(component.width(), component.height()))) {
						state.statusText = "";
					} else {
						cancelPlaybackLocked(state);
						clearYoutubePlaybackLocked(state);
						clearGallerySelectionLocked(state);
						state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
						state.galleryIndex = -1;
						state.loading = false;
						state.statusText = "";
					}
				}
			} else if (state.galleryIndex >= state.galleryItems.size()) {
				state.galleryIndex = state.galleryItems.isEmpty() ? -1 : state.galleryItems.size() - 1;
			}

			int totalRows = galleryTotalRowsPreview(state.galleryItems.size(), createUiLayout(component.width(), component.height()));
			int visibleRows = galleryVisibleRowsPreview(createUiLayout(component.width(), component.height()));
			state.galleryScroll = clampInt(state.galleryScroll, 0, Math.max(0, totalRows - visibleRows));
			state.version++;
			return true;
		}
	}

	static boolean hasLiveCameraItemsLocked(MediaRuntimeState state) {
		return countLiveCameraItemsLocked(state) > 0;
	}

	static int countLiveCameraItemsLocked(MediaRuntimeState state) {
		if (state == null || state.galleryItems.isEmpty()) {
			return 0;
		}
		int liveCameraItems = 0;
		for (GalleryItem item : state.galleryItems) {
			if (isLiveCameraGalleryItem(item)) {
				liveCameraItems++;
			}
		}
		return liveCameraItems;
	}

	static boolean hasNonLiveCameraItemsLocked(MediaRuntimeState state) {
		if (state == null || state.galleryItems.isEmpty()) {
			return false;
		}
		for (GalleryItem item : state.galleryItems) {
			if (!isLiveCameraGalleryItem(item)) {
				return true;
			}
		}
		return false;
	}

	static boolean hasSavedGalleryItemsLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		return MonitorGalleryRuntimePolicy.hasSavedGalleryItems(
				state.mode,
				state.galleryItems.size(),
				countLiveCameraItemsLocked(state)
		);
	}

	static List<LiveCameraReference> collectConnectedCameraPositions(ServerLevel level, ScreenComponent component) {
		if (level == null || component == null) {
			return List.of();
		}
		Set<BlockPos> wireNetwork = collectComponentWireNetwork(level, component);
		Set<LiveCameraReference> cameraPositions = new LinkedHashSet<>();
		for (ItemFrame frame : component.frameCoords().keySet()) {
			BlockPos framePos = frame.blockPosition();
			BlockPos supportPos = framePos.relative(frame.getDirection().getOpposite());
			collectCameraTouchPoints(level, framePos, cameraPositions);
			collectCameraTouchPoints(level, supportPos, cameraPositions);
		}
		BluetoothLinkSystem.Endpoint screenEndpoint = bluetoothScreenEndpoint(level, component);
		for (BluetoothLinkSystem.Endpoint linked : BluetoothLinkSystem.linkedEndpoints(screenEndpoint)) {
			if (linked.type() == BluetoothLinkSystem.EndpointType.CAMERA && linked.dimension() != null && linked.pos() != null) {
				cameraPositions.add(LiveCameraReference.camera(linked.dimension(), linked.pos().immutable()));
				continue;
			}
			if (linked.type() == BluetoothLinkSystem.EndpointType.DRONE && linked.deviceUuid() != null) {
				DroneSystem.DroneLiveFeedState droneState = DroneSystem.resolveLiveFeedState(level.getServer(), linked);
				if (droneState != null) {
					cameraPositions.add(LiveCameraReference.drone(droneState.dimension(), droneState.pos(), droneState.droneUuid()));
				} else if (linked.dimension() != null) {
					cameraPositions.add(LiveCameraReference.drone(linked.dimension(), linked.pos(), linked.deviceUuid()));
				}
			}
		}
		for (BlockPos wirePos : wireNetwork) {
			collectCameraTouchPoints(level, wirePos, cameraPositions);
		}
		List<LiveCameraReference> ordered = new ArrayList<>(cameraPositions);
		ordered.sort((first, second) -> MonitorSberDronesCatalog.compare(toSberDronesSource(first), toSberDronesSource(second)));
		return List.copyOf(ordered);
	}

	static Set<BlockPos> collectComponentWireNetwork(ServerLevel level, ScreenComponent component) {
		if (level == null || component == null) {
			return Set.of();
		}
		Set<BlockPos> visited = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		for (ItemFrame frame : component.frameCoords().keySet()) {
			BlockPos framePos = frame.blockPosition();
			BlockPos supportPos = framePos.relative(frame.getDirection().getOpposite());
			seedWireNetwork(level, framePos, visited, queue);
			seedWireNetwork(level, supportPos, visited, queue);
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
		return Set.copyOf(visited);
	}

	static void seedWireNetwork(ServerLevel level, BlockPos originPos, Set<BlockPos> visited, ArrayDeque<BlockPos> queue) {
		if (level == null || originPos == null) {
			return;
		}
		for (BlockPos touchPos : redstoneTouchPoints(originPos)) {
			if (!isRedstoneWire(level, touchPos) || !visited.add(touchPos.immutable())) {
				continue;
			}
			queue.add(touchPos.immutable());
		}
	}

	static void collectCameraTouchPoints(ServerLevel level, BlockPos originPos, Set<LiveCameraReference> cameraPositions) {
		if (level == null || originPos == null || cameraPositions == null) {
			return;
		}
		for (BlockPos touchPos : redstoneTouchPoints(originPos)) {
			if (!isCameraBlock(level, touchPos)) {
				continue;
			}
			cameraPositions.add(LiveCameraReference.camera(level.dimension(), touchPos.immutable()));
		}
	}

	static boolean isCameraBlock(ServerLevel level, BlockPos pos) {
		return level != null && pos != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(ModBlocks.CAMERA);
	}

	static boolean isLiveCameraGalleryItem(GalleryItem item) {
		return item != null && effectiveGalleryItemKind(item) == GalleryItemKind.LIVE_CAMERA;
	}

	static boolean galleryItemsEqual(List<GalleryItem> first, List<GalleryItem> second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null || first.size() != second.size()) {
			return false;
		}
		for (int index = 0; index < first.size(); index++) {
			if (!Objects.equals(first.get(index), second.get(index))) {
				return false;
			}
		}
		return true;
	}

	static boolean sameLiveCameraIdentity(LiveCameraReference first, LiveCameraReference second) {
		return MonitorSberDronesCatalog.sameIdentity(toSberDronesSource(first), toSberDronesSource(second));
	}

	static MonitorSberDronesCatalog.Source toSberDronesSource(LiveCameraReference cameraRef) {
		if (cameraRef == null) {
			return null;
		}
		String dimensionId = cameraRef.dimension() != null ? cameraRef.dimension().identifier().toString() : "";
		BlockPos pos = cameraRef.pos();
		if (cameraRef.sourceType() == LiveCameraSourceType.DRONE) {
			return MonitorSberDronesCatalog.Source.drone(
					dimensionId,
					pos != null ? pos.getX() : null,
					pos != null ? pos.getY() : null,
					pos != null ? pos.getZ() : null,
					cameraRef.sourceUuid()
			);
		}
		if (pos == null) {
			return null;
		}
		return MonitorSberDronesCatalog.Source.camera(dimensionId, pos.getX(), pos.getY(), pos.getZ());
	}

	static LiveCameraReference fromSberDronesSource(MonitorSberDronesCatalog.Source source) {
		if (source == null) {
			return null;
		}
		ResourceKey<Level> dimension = null;
		if (source.dimensionId() != null && !source.dimensionId().isBlank()) {
			Identifier dimensionId = Identifier.tryParse(source.dimensionId());
			if (dimensionId != null) {
				dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
			}
		}
		if (source.sourceType() == MonitorSberDronesCatalog.SourceType.DRONE) {
			return LiveCameraReference.drone(
					dimension,
					source.hasPosition() ? new BlockPos(source.x(), source.y(), source.z()) : null,
					source.sourceUuid()
			);
		}
		if (dimension == null || !source.hasPosition()) {
			return null;
		}
		return LiveCameraReference.camera(dimension, new BlockPos(source.x(), source.y(), source.z()));
	}

	static String liveCameraGalleryUrl(LiveCameraReference cameraRef) {
		return MonitorSberDronesCatalog.url(toSberDronesSource(cameraRef));
	}

	static LiveCameraReference liveCameraGalleryReference(String url, ResourceKey<Level> fallbackDimension) {
		MonitorSberDronesCatalog.Source source = MonitorSberDronesCatalog.parseUrl(
				url,
				fallbackDimension != null ? fallbackDimension.identifier().toString() : ""
		);
		return fromSberDronesSource(source);
	}

	static String liveCameraGalleryTitle(LiveCameraReference cameraRef) {
		return MonitorSberDronesCatalog.title(toSberDronesSource(cameraRef));
	}

	static String liveCameraGallerySubtitle(LiveCameraReference cameraRef, boolean online) {
		return MonitorSberDronesCatalog.subtitle(toSberDronesSource(cameraRef), online);
	}

	static String compactDimensionLabel(ResourceKey<Level> dimension) {
		if (dimension == null) {
			return "UNKNOWN";
		}
		Identifier id = dimension.identifier();
		String namespace = id.getNamespace();
		String path = id.getPath();
		if ("minecraft".equals(namespace)) {
			return switch (path) {
				case "overworld" -> "OVERWORLD";
				case "the_nether" -> "NETHER";
				case "the_end" -> "END";
				default -> path.replace('_', ' ').toUpperCase(Locale.ROOT);
			};
		}
		return (namespace + ":" + path).replace('_', ' ').toUpperCase(Locale.ROOT);
	}

	static String minimalDimensionLabel(ResourceKey<Level> dimension) {
		if (dimension == null) {
			return "unknown";
		}
		Identifier id = dimension.identifier();
		String namespace = id.getNamespace();
		String path = id.getPath();
		if ("minecraft".equals(namespace)) {
			return switch (path) {
				case "overworld" -> "overworld";
				case "the_nether" -> "nether";
				case "the_end" -> "end";
				default -> path.replace('_', ' ');
			};
		}
		return namespace + ":" + path.replace('_', ' ');
	}

	static String formatLiveSourceCoordinates(BlockPos pos) {
		if (pos == null) {
			return "X --  Y --  Z --";
		}
		return "X " + String.format(Locale.ROOT, "%+d", pos.getX())
				+ "  Y " + String.format(Locale.ROOT, "%+d", pos.getY())
				+ "  Z " + String.format(Locale.ROOT, "%+d", pos.getZ());
	}

	static String liveCameraDeviceTitle(MinecraftServer server, LiveCameraReference cameraRef) {
		String fallback = cameraRef != null && cameraRef.sourceType() == LiveCameraSourceType.DRONE ? "Дрон" : "Камера";
		return resolveLiveCameraDisplayTitle(server, cameraRef, fallback);
	}

	static String liveSourceDisplayTitle(MinecraftServer server, LiveCameraReference cameraRef, BlockPos pos) {
		if (cameraRef == null) {
			return "SOURCE";
		}
		String fallback = cameraRef.sourceType() == LiveCameraSourceType.DRONE
				? "UAV " + shortLiveSourceToken(cameraRef.sourceUuid())
				: pos == null ? "NODE" : "NODE " + Math.abs(pos.getX()) + ":" + Math.abs(pos.getZ());
		return resolveLiveCameraDisplayTitle(server, cameraRef, fallback);
	}

	private static String resolveLiveCameraDisplayTitle(MinecraftServer server, LiveCameraReference cameraRef, String fallback) {
		if (cameraRef == null || cameraRef.sourceType() == LiveCameraSourceType.DRONE) {
			return fallback;
		}
		if (server == null || cameraRef.dimension() == null || cameraRef.pos() == null) {
			return fallback;
		}
		return PlacedDeviceNameStore.cameraName(server, cameraRef.dimension(), cameraRef.pos(), fallback);
	}

	static String shortLiveSourceToken(UUID uuid) {
		if (uuid == null) {
			return "----";
		}
		String compact = uuid.toString().replace("-", "").toUpperCase(Locale.ROOT);
		return compact.substring(0, Math.min(4, compact.length()));
	}

	static String liveCameraStreamOwnerId(ScreenRuntimeKey key) {
		if (key == null) {
			return "";
		}
		return key.dimension() + "|" + key.pos().asLong() + "|" + key.facing().getSerializedName();
	}

	static boolean isLiveCameraOnline(MinecraftServer server, LiveCameraReference cameraRef) {
		if (server == null || cameraRef == null) {
			return false;
		}
		if (cameraRef.sourceType() == LiveCameraSourceType.DRONE) {
			if (cameraRef.sourceUuid() == null) {
				return false;
			}
			DroneSystem.DroneLiveFeedState droneState = DroneSystem.resolveLiveFeedState(server, cameraRef.sourceUuid(), cameraRef.dimension(), cameraRef.pos());
			return droneState != null && droneState.online();
		}
		if (cameraRef.dimension() == null || cameraRef.pos() == null) {
			return false;
		}
		ServerLevel cameraLevel = server.getLevel(cameraRef.dimension());
		if (cameraLevel == null) {
			return false;
		}
		BlockPos cameraPos = cameraRef.pos();
		if (RocketLaunchEventSystem.launchedCameraFeed(cameraLevel, cameraPos) != null) {
			// Bluetooth keeps the source's original block coordinate while the
			// camera itself follows the launched rocket through its anchor entity.
			return true;
		}
		return cameraLevel.hasChunkAt(cameraPos)
				&& isCameraBlock(cameraLevel, cameraPos)
				&& RendererBotCameraSystem.isCameraPlayerLoaded(cameraLevel, cameraPos);
	}

	static boolean isBluetoothLinkedLiveCamera(ServerLevel level, ScreenComponent component, LiveCameraReference cameraRef) {
		return linkedLiveCameraEndpoint(level, component, cameraRef) != null;
	}

	static boolean unlinkLiveCameraFromScreen(ServerLevel level, ScreenComponent component, LiveCameraReference cameraRef) {
		if (level == null || component == null || cameraRef == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return false;
		}
		BluetoothLinkSystem.Endpoint screenEndpoint = bluetoothScreenEndpoint(level, component);
		BluetoothLinkSystem.Endpoint sourceEndpoint = linkedLiveCameraEndpoint(level, component, cameraRef);
		if (screenEndpoint == null || sourceEndpoint == null) {
			return false;
		}
		return BluetoothLinkSystem.unlinkEndpoints(server, screenEndpoint, sourceEndpoint);
	}

	static BluetoothLinkSystem.Endpoint linkedLiveCameraEndpoint(ServerLevel level, ScreenComponent component, LiveCameraReference cameraRef) {
		if (level == null || component == null || cameraRef == null) {
			return null;
		}
		BluetoothLinkSystem.Endpoint screenEndpoint = bluetoothScreenEndpoint(level, component);
		if (screenEndpoint == null) {
			return null;
		}
		for (BluetoothLinkSystem.Endpoint linked : BluetoothLinkSystem.linkedEndpoints(screenEndpoint)) {
			if (matchesLiveCameraEndpoint(linked, cameraRef)) {
				return linked;
			}
		}
		return null;
	}

	static boolean matchesLiveCameraEndpoint(BluetoothLinkSystem.Endpoint endpoint, LiveCameraReference cameraRef) {
		if (endpoint == null || cameraRef == null) {
			return false;
		}
		if (cameraRef.sourceType() == LiveCameraSourceType.DRONE) {
			return endpoint.type() == BluetoothLinkSystem.EndpointType.DRONE
					&& cameraRef.sourceUuid() != null
					&& Objects.equals(cameraRef.sourceUuid(), endpoint.deviceUuid());
		}
		return endpoint.type() == BluetoothLinkSystem.EndpointType.CAMERA
				&& cameraRef.dimension() != null
				&& Objects.equals(cameraRef.dimension(), endpoint.dimension())
				&& cameraRef.pos() != null
				&& Objects.equals(cameraRef.pos(), endpoint.pos());
	}

	static BufferedImage createLiveCameraPlaceholderPreview(String title, String subtitle, boolean online, LiveCameraSourceType sourceType) {
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			configureUiGraphics(graphics);
			graphics.setColor(new Color(0x06080C));
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.setPaint(new GradientPaint(0, 0, new Color(0x10151C), image.getWidth(), image.getHeight(), new Color(0x06090D)));
			graphics.fillRoundRect(12, 12, image.getWidth() - 24, image.getHeight() - 24, 28, 28);
			graphics.setColor(new Color(255, 255, 255, 10));
			for (int step = 28; step < image.getWidth(); step += 24) {
				graphics.drawLine(step, 12, step, image.getHeight() - 12);
				graphics.drawLine(12, step, image.getWidth() - 12, step);
			}
				graphics.setColor(new Color(255, 255, 255, 24));
				graphics.drawRoundRect(12, 12, image.getWidth() - 24, image.getHeight() - 24, 28, 28);
				drawPlayerUiIcon(
						graphics,
						new UiRect(76, 76, 104, 104),
						sourceType == LiveCameraSourceType.DRONE ? PlayerUiIcon.DRONE : PlayerUiIcon.CAMERA,
						new Color(248, 251, 255, 210)
				);
			} finally {
				graphics.dispose();
			}
		return image;
	}

	static BufferedImage mapPaletteImage(byte[] pixels, int width, int height) {
		if (pixels == null || pixels.length == 0 || width <= 0 || height <= 0) {
			return null;
		}
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		int pixelCount = Math.min(argb.length, pixels.length);
		for (int index = 0; index < pixelCount; index++) {
			argb[index] = MAP_PACKED_COLOR_ARGB[Byte.toUnsignedInt(pixels[index])];
		}
		return image;
	}

	static BufferedImage mapPaletteImage(byte[][] renderedTiles, int tilesWide, int tilesHigh) {
		if (renderedTiles == null
				|| renderedTiles.length == 0
				|| tilesWide <= 0
				|| tilesHigh <= 0
				|| renderedTiles.length < tilesWide * tilesHigh) {
			return null;
		}
		int width = tilesWide * MAP_SIZE;
		int height = tilesHigh * MAP_SIZE;
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		for (int tileY = 0; tileY < tilesHigh; tileY++) {
			for (int tileX = 0; tileX < tilesWide; tileX++) {
				byte[] tile = renderedTiles[tileY * tilesWide + tileX];
				if (tile == null || tile.length < MAP_SIZE * MAP_SIZE) {
					return null;
				}
				for (int row = 0; row < MAP_SIZE; row++) {
					int tileOffset = row * MAP_SIZE;
					int imageOffset = (tileY * MAP_SIZE + row) * width + tileX * MAP_SIZE;
					for (int column = 0; column < MAP_SIZE; column++) {
						argb[imageOffset + column] = MAP_PACKED_COLOR_ARGB[Byte.toUnsignedInt(tile[tileOffset + column])];
					}
				}
			}
		}
		return image;
	}

	static LiveCameraPose liveCameraCapturePose(ServerLevel level, BlockPos cameraPos, BlockState cameraState) {
		float yaw = cameraState.getValue(HorizontalDirectionalBlock.FACING).toYRot();
		float pitch = 0.0F;
		CameraOrientationStore.CameraPose pose = CameraOrientationStore.get(level, cameraPos);
		if (pose != null) {
			yaw = pose.yaw();
			pitch = pose.pitch();
		}
		return new LiveCameraPose(CameraBlock.captureOrigin(cameraPos, yaw, pitch), yaw, pitch);
	}
}

record LiveCameraPose(Vec3 origin, float yaw, float pitch) {
}
