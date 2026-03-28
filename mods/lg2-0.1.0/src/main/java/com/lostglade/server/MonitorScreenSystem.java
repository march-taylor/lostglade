package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
import com.lostglade.item.MonitorItem;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.lostglade.server.monitor.MonitorApp;
import com.lostglade.server.monitor.MonitorAppRegistry;
import com.lostglade.server.monitor.MonitorMediaApp;
import com.lostglade.server.monitor.MonitorYoutubeRelayClient;
import com.lostglade.server.progress.TaskProgress;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
import java.util.concurrent.TimeUnit;

public final class MonitorScreenSystem {
	private static final String SCREEN_ROOT_TAG = "lg2_monitor_screen";
	private static final String LINK_LOCKED_TAG = "link_locked";
	private static final String ATTACHMENT_MASK_TAG = "attachment_mask";
	private static final String GROUP_ID_TAG = "group_id";
	private static final String GRID_WIDTH_TAG = "grid_width";
	private static final String GRID_HEIGHT_TAG = "grid_height";
	private static final String TILE_X_TAG = "tile_x";
	private static final String TILE_Y_TAG = "tile_y";
	private static final String CONNECTION_MASK_TAG = "connection_mask";
	private static final String POWERED_TAG = "powered";
	private static final String VIEW_MODE_TAG = "view_mode";
	private static final String LAUNCHER_PAGE_TAG = "launcher_page";
	private static final String DISPLAY_ROOT_TAG = "lg2_monitor_display";
	private static final String POS_TAG_PREFIX = "lg2_monitor_display_pos:";
	private static final String FACING_TAG_PREFIX = "lg2_monitor_display_facing:";
	private static final int MAP_SIZE = 128;
	private static final int PHOTO_MAP_CENTER = 30_000_000;
	private static final int CONNECTION_LEFT = 1;
	private static final int CONNECTION_RIGHT = 2;
	private static final int CONNECTION_UP = 4;
	private static final int CONNECTION_DOWN = 8;
	private static final int CONNECTION_ALL = CONNECTION_LEFT | CONNECTION_RIGHT | CONNECTION_UP | CONNECTION_DOWN;
	private static final int MAX_UI_TILES = 2;
	private static final long RESCAN_INTERVAL_TICKS = 4L;
	private static final double DISPLAY_SEARCH_RADIUS = 0.8D;
	private static final double DISPLAY_PLANE_OFFSET = 0.49D;
	private static final double TOUCH_TOLERANCE = 0.08D;
	private static final String SCREEN_OFF_RESOURCE = "/assets/lg2/textures/monitor/screen_off.png";
	private static final String SCREEN_ON_RESOURCE = "/assets/lg2/textures/monitor/screen_on.png";
	private static final int LAUNCHER_COLUMNS = 2;
	private static final long PROGRESS_RENDER_INTERVAL_MS = 300L;
	private static final int PROGRESS_FADE_RENDER_STEPS = 5;
	private static final long MEDIA_SCROLL_FOCUS_TIMEOUT_MS = 6000L;
	private static final double MEDIA_CONTROL_DISTANCE = 6.0D;
	private static final long YOUTUBE_SCROLL_SEEK_MS = 5000L;
	private static final Map<RenderCacheKey, byte[][]> TILE_CACHE = new ConcurrentHashMap<>();
	private static final Map<Integer, byte[]> LAST_RENDERED_MAP_FRAMES = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> APP_ICON_CACHE = new ConcurrentHashMap<>();
	private static final Map<ScreenRuntimeKey, MediaRuntimeState> MEDIA_STATES = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingMediaLinkRequest> PENDING_MEDIA_LINKS = new ConcurrentHashMap<>();
	private static final Map<UUID, ScreenRuntimeKey> ACTIVE_MEDIA_ACTIONBARS = new ConcurrentHashMap<>();
	private static final Map<UUID, PlayerMediaFocus> PLAYER_MEDIA_FOCUS = new ConcurrentHashMap<>();
	private static volatile ExecutorService renderExecutor;
	private static volatile ExecutorService quantizeExecutor;
	private static volatile ExecutorService mediaIoExecutor;
	private static volatile ScheduledExecutorService mediaScheduler;
	private static volatile BufferedImage offBaseImage;
	private static volatile BufferedImage onBaseImage;

	private MonitorScreenSystem() {
	}

	public static void register() {
		ensureExecutors();
		UseEntityCallback.EVENT.register(MonitorScreenSystem::onUseEntity);
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(MonitorScreenSystem::onAllowChatMessage);
		ServerTickEvents.END_SERVER_TICK.register(MonitorScreenSystem::tick);
	}

	private static void ensureExecutors() {
		if (renderExecutor == null) {
			renderExecutor = Executors.newFixedThreadPool(monitorRenderThreads(), daemonThreadFactory("lg2-monitor-render"));
		}
		if (quantizeExecutor == null) {
			quantizeExecutor = Executors.newFixedThreadPool(monitorTileQuantizerThreads(), daemonThreadFactory("lg2-monitor-quantize"));
		}
		if (mediaIoExecutor == null) {
			mediaIoExecutor = Executors.newFixedThreadPool(monitorMediaIoThreads(), daemonThreadFactory("lg2-monitor-io"));
		}
		if (mediaScheduler == null) {
			mediaScheduler = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("lg2-monitor-gif"));
		}
	}

	private static int monitorRenderThreads() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(1, config.monitorRenderThreads) : Math.max(2, Runtime.getRuntime().availableProcessors());
	}

	private static int monitorTileQuantizerThreads() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(1, config.monitorTileQuantizerThreads) : Math.max(2, Runtime.getRuntime().availableProcessors());
	}

	private static int monitorMediaIoThreads() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(1, config.monitorMediaIoThreads) : 2;
	}

	private static int monitorMapUpdateRadiusBlocks() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(16, config.monitorMapUpdateRadiusBlocks) : 128;
	}

	private static long youtubePollActiveIntervalMs() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(33L, config.monitorYoutubePollActiveIntervalMs) : 100L;
	}

	private static long youtubePollIdleIntervalMs() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(100L, config.monitorYoutubePollIdleIntervalMs) : 400L;
	}

	private static ThreadFactory daemonThreadFactory(String baseName) {
		return runnable -> {
			Thread thread = new Thread(runnable, baseName);
			thread.setDaemon(true);
			return thread;
		};
	}

	public static InteractionResult tryPlaceScreen(UseOnContext context) {
		if (context == null) {
			return InteractionResult.PASS;
		}
		if (context.getLevel().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}

		Direction facing = context.getClickedFace();
		if (facing == null || !facing.getAxis().isHorizontal()) {
			player.displayClientMessage(wallOnlyMessage(player), true);
			return InteractionResult.FAIL;
		}

		BlockPos framePos = context.getClickedPos().relative(facing);
		if (!level.getBlockState(framePos).canBeReplaced() || findAnyFrame(level, framePos, facing) != null) {
			player.displayClientMessage(occupiedMessage(player), true);
			return InteractionResult.FAIL;
		}

		GlowItemFrame frame = new GlowItemFrame(level, framePos, facing);
		if (!frame.survives()) {
			player.displayClientMessage(wallOnlyMessage(player), true);
			return InteractionResult.FAIL;
		}

		String groupId = resolvePlacementGroupId(level, framePos, facing, context.getClickLocation(), player.isShiftKeyDown());
		ItemStack frameMap = createScreenMap(level, new ScreenTileState(
				CONNECTION_ALL,
				1,
				1,
				0,
				0,
				0,
				false,
				ScreenViewMode.HOME,
				0,
				groupId
		));
		if (frameMap.isEmpty()) {
			return InteractionResult.FAIL;
		}

		frame.setInvisible(true);
		frame.setSilent(true);
		frame.setItem(frameMap, false);
		frame.setRotation(0);
		level.addFreshEntity(frame);

		synchronizeConnectedScreens(level, frame, null, null, null);
		if (!player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	public static boolean onFrameBroken(ServerLevel level, ItemFrame frame, Entity breaker, boolean shouldDropScreen) {
		if (level == null || frame == null || readScreenState(frame.getItem()) == null) {
			return false;
		}

		BlockPos framePos = frame.blockPosition();
		Direction facing = frame.getDirection();
		removeDisplays(level, frame.blockPosition(), frame.getDirection());
		forgetRenderedMapFrame(frame.getItem());
		frame.setItem(ItemStack.EMPTY, false);
		if (shouldDropScreen) {
			frame.spawnAtLocation(level, new ItemStack(ModItems.MONITOR));
		}
		frame.discard();
		synchronizeNeighborComponents(level, framePos, facing);
		return true;
	}

	private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
		if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level) || !(entity instanceof ItemFrame itemFrame)) {
			return InteractionResult.PASS;
		}
		if (readScreenState(itemFrame.getItem()) == null) {
			return InteractionResult.PASS;
		}
		return handleTouch(serverPlayer, level, itemFrame, hitResult);
	}

	public static List<SpeakerAudioSource> findSpeakerAudioSources(ServerLevel level, BlockPos speakerPos) {
		if (level == null || speakerPos == null || !level.hasChunkAt(speakerPos)) {
			return List.of();
		}
		Set<BlockPos> wireNetwork = collectSpeakerWireNetwork(level, speakerPos);
		AABB searchBox = speakerSearchBox(speakerPos, wireNetwork);
		Map<ScreenRuntimeKey, ScreenComponent> connectedComponents = new HashMap<>();
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, searchBox, candidate -> readScreenState(candidate.getItem()) != null)) {
			ScreenComponent component = collectComponent(level, frame, null);
			if (component == null || component.viewMode() != ScreenViewMode.YOUTUBE || !component.powered()) {
				continue;
			}
			if (!isSpeakerConnectedToComponent(speakerPos, component, wireNetwork)) {
				continue;
			}
			connectedComponents.putIfAbsent(component.runtimeKey(), component);
		}
		if (connectedComponents.isEmpty()) {
			return List.of();
		}

		List<SpeakerAudioSource> sources = new ArrayList<>();
		for (ScreenComponent component : connectedComponents.values()) {
			MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
			if (state == null) {
				continue;
			}
			synchronized (state) {
				if (state.mode != ScreenViewMode.YOUTUBE
						|| state.relaySessionId == null
						|| state.audioStreamUrl == null
						|| state.audioStreamUrl.isBlank()
						|| state.waitingForLink
						|| state.loading) {
					continue;
				}
				sources.add(new SpeakerAudioSource(
						componentGroupId(component.runtimeKey()),
						state.relaySessionId,
						state.audioStreamUrl,
						state.positionMs,
						state.userPaused,
						state.liveStream
				));
			}
		}
		return sources;
	}

	private static AABB speakerSearchBox(BlockPos speakerPos, Set<BlockPos> wireNetwork) {
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

	private static Set<BlockPos> collectSpeakerWireNetwork(ServerLevel level, BlockPos speakerPos) {
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

	private static boolean isRedstoneWire(ServerLevel level, BlockPos pos) {
		return level != null && pos != null && level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.REDSTONE_WIRE);
	}

	private static List<BlockPos> redstoneTouchPoints(BlockPos pos) {
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

	private static List<BlockPos> redstoneWireNeighbors(BlockPos pos) {
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

	private static boolean isSpeakerConnectedToComponent(BlockPos speakerPos, ScreenComponent component, Set<BlockPos> wireNetwork) {
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

	private static boolean areBlocksAdjacent(BlockPos first, BlockPos second) {
		if (first == null || second == null) {
			return false;
		}
		return Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ()) <= 1;
	}

	private static boolean onAllowChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
		if (sender == null || message == null) {
			return true;
		}
		MinecraftServer server = sender.level().getServer();
		if (server == null) {
			return true;
		}
		PendingMediaLinkRequest pending = PENDING_MEDIA_LINKS.remove(sender.getUUID());
		if (pending == null) {
			return true;
		}

		MediaRuntimeState state = MEDIA_STATES.get(pending.screenKey());
		if (state == null) {
			ACTIVE_MEDIA_ACTIONBARS.remove(sender.getUUID());
			sender.displayClientMessage(Component.empty(), true);
			sender.sendSystemMessage(mediaCancelledMessage(sender, pending.mode()));
			return false;
		}

		String url = message.signedContent() != null ? message.signedContent().trim() : "";
		if (url.isEmpty()) {
			synchronized (state) {
				state.waitingForLink = true;
				state.loading = false;
				state.statusText = linkPromptStatus(pending.mode(), sender);
				state.version++;
			}
			PENDING_MEDIA_LINKS.put(sender.getUUID(), pending);
			ACTIVE_MEDIA_ACTIONBARS.put(sender.getUUID(), pending.screenKey());
			sender.displayClientMessage(linkPromptMessage(pending.mode(), sender), true);
			sender.sendSystemMessage(mediaInvalidLinkMessage(sender, pending.mode()));
			requestRuntimeRender(server, pending.screenKey());
			return false;
		}

		synchronized (state) {
			state.mode = pending.mode();
			state.waitingForLink = false;
			state.loading = true;
			state.statusText = loadingStatus(pending.mode(), sender);
			state.overlayMode = MediaOverlayMode.CONTROLS;
			state.version++;
		}
		ACTIVE_MEDIA_ACTIONBARS.put(sender.getUUID(), pending.screenKey());
		sender.displayClientMessage(loadingMessage(pending.mode(), sender), true);
		requestRuntimeRender(server, pending.screenKey());

		if (pending.mode() == ScreenViewMode.YOUTUBE) {
			CompletableFuture
					.supplyAsync(() -> {
						try {
							return new YoutubeLoadResult(
									pending.screenKey(),
									sender.getUUID(),
									url,
									MonitorYoutubeRelayClient.load(relaySessionId(pending.screenKey()), url, state.progress),
									null
							);
						} catch (Exception exception) {
							return new YoutubeLoadResult(pending.screenKey(), sender.getUUID(), url, null, sanitizeMediaError(exception.getMessage()));
						}
					}, mediaIoExecutor)
					.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
		} else {
			CompletableFuture
					.supplyAsync(() -> {
						try {
							return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, MonitorMediaApp.loadFromUrl(url, state.progress), null);
						} catch (Exception exception) {
							return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, null, sanitizeMediaError(exception.getMessage()));
						}
					}, mediaIoExecutor)
					.thenAccept(result -> server.execute(() -> applyMediaLoadResult(server, result)));
		}
		return false;
	}

	private static InteractionResult handleTouch(ServerPlayer player, ServerLevel level, ItemFrame frame, EntityHitResult hitResult) {
		ScreenComponent component = collectComponent(level, frame, null);
		if (component == null) {
			return InteractionResult.SUCCESS;
		}
		if (!component.powered()) {
			return InteractionResult.SUCCESS;
		}

		TileCoord tileCoord = component.frameCoords().get(frame);
		if (tileCoord == null) {
			return InteractionResult.SUCCESS;
		}

		UiPoint touchPoint = screenTouchPoint(frame, hitResult != null ? hitResult.getLocation() : null, tileCoord, component.width(), component.height());
		if (touchPoint == null) {
			return InteractionResult.SUCCESS;
		}

		UiLayout layout = createUiLayout(component.width(), component.height());
		MinecraftServer server = level.getServer();
		ScreenViewMode nextMode = null;
		Integer nextLauncherPage = null;
		boolean rerenderCurrent = false;
		Boolean youtubePauseAction = null;
		Long youtubeSeekTargetMs = null;
		if (component.viewMode() == ScreenViewMode.HOME) {
			List<MonitorApp> visibleApps = visibleHomeApps(layout, component.launcherPage());
			for (int index = 0; index < visibleApps.size(); index++) {
				UiRect appRect = homeAppCardRect(layout, component.launcherPage(), index);
				if (appRect.contains(touchPoint.x(), touchPoint.y())) {
					nextMode = ScreenViewMode.fromTag(visibleApps.get(index).id());
					break;
				}
			}
			int pageCount = homePageCount(layout);
			if (nextMode == null && component.launcherPage() > 0) {
				UiRect upRect = homeScrollUpRect(layout);
				if (upRect != null && upRect.contains(touchPoint.x(), touchPoint.y())) {
					nextLauncherPage = component.launcherPage() - 1;
				}
			}
			if (nextMode == null && component.launcherPage() + 1 < pageCount) {
				UiRect downRect = homeScrollDownRect(layout);
				if (downRect != null && downRect.contains(touchPoint.x(), touchPoint.y())) {
					nextLauncherPage = component.launcherPage() + 1;
				}
			}
		} else if (isPlayerMode(component.viewMode())) {
			markMediaFocus(player, component.runtimeKey());
			MediaRuntimeState mediaState = MEDIA_STATES.computeIfAbsent(
					component.runtimeKey(),
					ignored -> MediaRuntimeState.fresh(component.viewMode(), "", () -> onMediaProgressChanged(level.getServer(), component.runtimeKey()))
			);
			MediaOverlayMode overlayMode;
			boolean hasMedia;
			synchronized (mediaState) {
				overlayMode = mediaState.overlayMode;
				hasMedia = hasDisplayableMediaLocked(mediaState);
			}
			if (hasMedia && overlayMode == MediaOverlayMode.VIEW) {
				synchronized (mediaState) {
					mediaState.overlayMode = MediaOverlayMode.CONTROLS;
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (mediaCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				nextMode = ScreenViewMode.HOME;
			} else if (hasMedia && mediaPlayPauseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (mediaState.mode == ScreenViewMode.YOUTUBE && mediaState.relaySessionId != null) {
						boolean shouldPause = !isPlaybackPausedLocked(mediaState);
						cancelPlaybackLocked(mediaState);
						mediaState.userPaused = shouldPause;
						mediaState.version++;
						youtubePauseAction = shouldPause;
					} else if (mediaState.loadedMedia != null && mediaState.loadedMedia.animated()) {
						if (isPlaybackPausedLocked(mediaState)) {
							mediaState.userPaused = false;
						} else {
							cancelPlaybackLocked(mediaState);
							mediaState.userPaused = true;
						}
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (hasMedia && mediaTimelineTrackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (mediaState.mode == ScreenViewMode.YOUTUBE && canSeekTimelineLocked(mediaState)) {
						youtubeSeekTargetMs = youtubePositionForFraction(mediaState, mediaTimelineFraction(layout, touchPoint));
						mediaState.positionMs = youtubeSeekTargetMs;
						mediaState.version++;
					} else if (mediaState.loadedMedia != null && mediaState.loadedMedia.frameCount() > 1) {
						mediaState.frameIndex = mediaFrameIndexForFraction(mediaState.loadedMedia, mediaTimelineFraction(layout, touchPoint));
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (hasMedia && mediaScaleRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.scaleMode = mediaState.scaleMode.next();
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (mediaLinkRect(layout, hasMedia).contains(touchPoint.x(), touchPoint.y())) {
				requestMediaLink(player, component.runtimeKey(), false, component.viewMode());
				rerenderCurrent = true;
			} else if (hasMedia) {
				synchronized (mediaState) {
					mediaState.overlayMode = MediaOverlayMode.VIEW;
					mediaState.version++;
				}
				rerenderCurrent = true;
			}
		} else {
			UiRect closeRect = genericCloseRect(layout);
			if (closeRect.contains(touchPoint.x(), touchPoint.y())) {
				nextMode = ScreenViewMode.HOME;
			}
		}

		if ((nextMode != null && nextMode != component.viewMode())
				|| (nextLauncherPage != null && nextLauncherPage != component.launcherPage())) {
			if (isPlayerMode(component.viewMode()) && nextMode != component.viewMode()) {
				closeMediaSession(level.getServer(), component.runtimeKey());
			}
			if (isPlayerMode(nextMode) && component.viewMode() != nextMode) {
				openMediaSession(player, component.runtimeKey(), nextMode);
				markMediaFocus(player, component.runtimeKey());
				if (nextMode == ScreenViewMode.YOUTUBE) {
					requestMediaLink(player, component.runtimeKey(), false, ScreenViewMode.YOUTUBE);
				}
			}
			synchronizeConnectedScreens(level, frame, null, nextMode, nextLauncherPage);
		} else if (rerenderCurrent) {
			requestComponentRender(level.getServer(), component, component.viewMode(), component.launcherPage());
			if (isPlayerMode(component.viewMode())) {
				resumeMediaPlaybackIfNeeded(level.getServer(), component.runtimeKey());
			}
		}
		if (server != null && youtubePauseAction != null) {
			boolean shouldPause = youtubePauseAction;
			ensureExecutors();
			CompletableFuture.runAsync(() -> {
				try {
					if (shouldPause) {
						MonitorYoutubeRelayClient.pause(relaySessionId(component.runtimeKey()));
					} else {
						MonitorYoutubeRelayClient.resume(relaySessionId(component.runtimeKey()));
					}
				} catch (Exception ignored) {
				}
			}, mediaIoExecutor).thenRun(() -> server.execute(() -> scheduleYoutubeRefresh(server, component.runtimeKey(), 0L)));
		}
		if (server != null && youtubeSeekTargetMs != null) {
			long seekTargetMs = youtubeSeekTargetMs;
			ensureExecutors();
			CompletableFuture.runAsync(() -> {
				try {
					MonitorYoutubeRelayClient.seek(relaySessionId(component.runtimeKey()), seekTargetMs);
				} catch (Exception ignored) {
				}
			}, mediaIoExecutor).thenRun(() -> server.execute(() -> scheduleYoutubeRefresh(server, component.runtimeKey(), 0L)));
		}
		return InteractionResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		cleanupMediaSessions(server);
		cleanupExpiredMediaFocus();
		refreshMediaRequestActionbars(server);
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getGameTime() % RESCAN_INTERVAL_TICKS != 0L) {
				continue;
			}
			Set<ScreenKey> processed = new HashSet<>();
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof ItemFrame frame)) {
					continue;
				}
				if (readScreenState(frame.getItem()) == null) {
					continue;
				}
				ScreenKey key = new ScreenKey(frame.blockPosition(), frame.getDirection());
				if (processed.contains(key)) {
					continue;
				}
				synchronizeConnectedScreens(level, frame, processed, null, null);
			}
			cleanupOrphanDisplays(level);
		}
	}

	private static void openMediaSession(ServerPlayer player, ScreenRuntimeKey key, ScreenViewMode mode) {
		if (player == null || key == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		MEDIA_STATES.put(key, MediaRuntimeState.fresh(mode, "", () -> onMediaProgressChanged(server, key)));
	}

	private static void requestMediaLink(ServerPlayer player, ScreenRuntimeKey key, boolean clearCurrentMedia, ScreenViewMode mode) {
		if (player == null || key == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.computeIfAbsent(key, ignored -> MediaRuntimeState.fresh(mode, linkPromptStatus(mode, player), () -> onMediaProgressChanged(server, key)));
		synchronized (state) {
			cancelPlaybackLocked(state);
			state.mode = mode;
			if (clearCurrentMedia) {
				clearLoadedContentLocked(state);
			}
			state.userPaused = false;
			state.waitingForLink = true;
			state.loading = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			state.statusText = linkPromptStatus(mode, player);
			state.progress.clear();
			state.version++;
		}
		PENDING_MEDIA_LINKS.put(player.getUUID(), new PendingMediaLinkRequest(key, mode));
		ACTIVE_MEDIA_ACTIONBARS.put(player.getUUID(), key);
		player.displayClientMessage(linkPromptMessage(mode, player), true);
	}

	private static void closeMediaSession(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		MediaRuntimeState removed = MEDIA_STATES.remove(key);
		String relaySessionId = null;
		if (removed != null) {
			synchronized (removed) {
				cancelPlaybackLocked(removed);
				removed.progress.clear();
				if (removed.mode == ScreenViewMode.YOUTUBE) {
					relaySessionId = removed.relaySessionId;
				}
			}
		}
		if (relaySessionId != null && !relaySessionId.isBlank()) {
			ensureExecutors();
			String finalRelaySessionId = relaySessionId;
			CompletableFuture.runAsync(() -> {
				try {
					MonitorYoutubeRelayClient.close(finalRelaySessionId);
				} catch (Exception ignored) {
				}
			}, mediaIoExecutor);
		}
		PENDING_MEDIA_LINKS.entrySet().removeIf(entry -> entry.getValue().screenKey().equals(key));
		for (Map.Entry<UUID, ScreenRuntimeKey> entry : List.copyOf(ACTIVE_MEDIA_ACTIONBARS.entrySet())) {
			if (entry.getValue().equals(key)) {
				ACTIVE_MEDIA_ACTIONBARS.remove(entry.getKey());
				clearMediaActionbar(server, entry.getKey());
			}
		}
		PLAYER_MEDIA_FOCUS.entrySet().removeIf(entry -> entry.getValue().screenKey().equals(key));
	}

	private static void cancelPlaybackLocked(MediaRuntimeState state) {
		if (state == null || state.playbackFuture == null) {
			return;
		}
		state.playbackFuture.cancel(false);
		state.playbackFuture = null;
	}

	private static void onMediaProgressChanged(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean shouldRender = false;
		synchronized (state) {
			if (now >= state.nextProgressRenderAtMillis) {
				state.nextProgressRenderAtMillis = now + PROGRESS_RENDER_INTERVAL_MS;
				state.version++;
				if (state.activeRenderJobs > 0) {
					state.rerenderRequested = true;
				} else {
					shouldRender = true;
				}
			}
		}
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
	}

	private static void refreshMediaRequestActionbars(MinecraftServer server) {
		if (server == null || server.getTickCount() % 20 != 0) {
			return;
		}
		for (Map.Entry<UUID, ScreenRuntimeKey> entry : List.copyOf(ACTIVE_MEDIA_ACTIONBARS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			MediaRuntimeState state = MEDIA_STATES.get(entry.getValue());
			if (player == null || state == null) {
				if (player != null) {
					player.displayClientMessage(Component.empty(), true);
				}
				ACTIVE_MEDIA_ACTIONBARS.remove(entry.getKey());
				continue;
			}
			Component message = null;
			synchronized (state) {
				if (state.waitingForLink) {
					message = linkPromptMessage(state.mode, player);
				} else if (state.loading) {
					message = loadingMessage(state.mode, player);
				}
			}
			if (message == null) {
				ACTIVE_MEDIA_ACTIONBARS.remove(entry.getKey());
				player.displayClientMessage(Component.empty(), true);
				continue;
			}
			player.displayClientMessage(message, true);
		}
	}

	private static void clearMediaActionbar(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) {
			player.displayClientMessage(Component.empty(), true);
		}
	}

	private static void applyMediaLoadResult(MinecraftServer server, MediaLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		ServerPlayer requester = server.getPlayerList().getPlayer(result.requesterUuid());
		boolean schedulePlayback = false;
		boolean animated = false;

		synchronized (state) {
			state.mode = ScreenViewMode.MEDIA;
			clearLoadedContentLocked(state);
			state.loading = false;
			state.waitingForLink = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			cancelPlaybackLocked(state);

			if (result.loadedMedia() != null) {
				state.loadedMedia = result.loadedMedia();
				state.sourceUrl = result.url();
				state.frameIndex = 0;
				state.userPaused = false;
				state.statusText = "";
				state.progress.complete("READY");
				animated = result.loadedMedia().animated();
				schedulePlayback = animated && result.loadedMedia().frameCount() > 1;
			} else {
				state.userPaused = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
			}
			state.version++;
		}

		if (requester != null) {
			ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
			requester.displayClientMessage(Component.empty(), true);
			if (result.loadedMedia() != null) {
				requester.sendSystemMessage(mediaLoadedMessage(requester, animated));
			} else {
				requester.sendSystemMessage(mediaLoadFailedMessage(requester, sanitizeMediaError(result.error())));
			}
		}
		requestRuntimeRender(server, result.screenKey());
		if (result.loadedMedia() != null) {
			scheduleProgressFadeRenders(server, result.screenKey());
		}
		if (schedulePlayback) {
			scheduleNextMediaFrame(server, result.screenKey());
		}
	}

	private static void applyYoutubeLoadResult(MinecraftServer server, YoutubeLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		ServerPlayer requester = server.getPlayerList().getPlayer(result.requesterUuid());

		synchronized (state) {
			state.mode = ScreenViewMode.YOUTUBE;
			clearLoadedContentLocked(state);
			state.waitingForLink = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			cancelPlaybackLocked(state);

			if (result.loadResponse() != null) {
				state.sourceUrl = result.url();
				state.relaySessionId = result.loadResponse().sessionId();
				state.audioStreamUrl = result.loadResponse().audioStreamUrl();
				state.mediaTitle = result.loadResponse().title();
				state.durationMs = result.loadResponse().durationMs();
				state.positionMs = 0L;
				state.liveStream = result.loadResponse().live();
				state.audioPlaceholder = true;
				state.loading = true;
				state.userPaused = false;
				state.statusText = result.loadResponse().status();
				state.progress.setIndeterminate(result.loadResponse().live() ? "LIVE" : "LOADING");
			} else {
				state.loading = false;
				state.userPaused = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
			}
			state.version++;
		}

		if (requester != null) {
			ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
			requester.displayClientMessage(Component.empty(), true);
			if (result.loadResponse() != null) {
				requester.sendSystemMessage(youtubeLoadedMessage(requester, result.loadResponse().live()));
			} else {
				requester.sendSystemMessage(mediaLoadFailedMessage(requester, sanitizeMediaError(result.error())));
			}
		}
		requestRuntimeRender(server, result.screenKey());
		if (result.loadResponse() != null) {
			scheduleYoutubeRefresh(server, result.screenKey(), 0L);
		}
	}

	private static void scheduleProgressFadeRenders(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		ensureExecutors();
		long stepMillis = Math.max(1L, TaskProgress.COMPLETION_VISIBLE_MILLIS / PROGRESS_FADE_RENDER_STEPS);
		for (int index = 1; index <= PROGRESS_FADE_RENDER_STEPS; index++) {
			long delayMillis = stepMillis * index;
			mediaScheduler.schedule(() -> requestRuntimeRender(server, key), delayMillis, TimeUnit.MILLISECONDS);
		}
	}

	private static void scheduleNextMediaFrame(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			cancelPlaybackLocked(state);
			if (state.mode == ScreenViewMode.YOUTUBE) {
				if (state.relaySessionId == null || state.waitingForLink) {
					return;
				}
				long delayMillis = effectiveYoutubePollDelayMs(server, key, isPlaybackPausedLocked(state));
				state.playbackFuture = mediaScheduler.schedule(() -> refreshYoutubeSnapshot(server, key), delayMillis, TimeUnit.MILLISECONDS);
				return;
			}
			if (state.loadedMedia == null || !state.loadedMedia.animated() || state.waitingForLink || state.loading || isPlaybackPausedLocked(state)) {
				return;
			}
			int delayMillis = state.loadedMedia.delayMillis(state.frameIndex);
			state.playbackFuture = mediaScheduler.schedule(() -> advanceMediaFrame(server, key), delayMillis, TimeUnit.MILLISECONDS);
		}
	}

	private static void advanceMediaFrame(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}

		boolean shouldContinue;
		synchronized (state) {
			if (state.mode == ScreenViewMode.YOUTUBE) {
				state.playbackFuture = null;
				return;
			}
			if (state.loadedMedia == null || !state.loadedMedia.animated() || state.waitingForLink || state.loading || isPlaybackPausedLocked(state)) {
				state.playbackFuture = null;
				return;
			}
			// Frame playback must not invalidate an in-flight large-screen render, or the first
			// completed frame can get discarded forever while animation keeps advancing.
			state.frameIndex = (state.frameIndex + 1) % state.loadedMedia.frameCount();
			state.playbackFuture = null;
			shouldContinue = state.loadedMedia.frameCount() > 1;
		}

		requestRuntimeRender(server, key);
		if (shouldContinue) {
			scheduleNextMediaFrame(server, key);
		}
	}

	private static void resumeMediaPlaybackIfNeeded(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			if (state.playbackFuture != null
					|| state.waitingForLink) {
				return;
			}
			if (state.mode == ScreenViewMode.YOUTUBE) {
				if (state.relaySessionId == null) {
					return;
				}
			} else if (state.loadedMedia == null || !state.loadedMedia.animated() || state.loading || isPlaybackPausedLocked(state)) {
				return;
			}
		}
		scheduleNextMediaFrame(server, key);
	}

	private static void refreshYoutubeSnapshot(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		String sessionId;
		long knownFrameSequence;
		synchronized (state) {
			state.playbackFuture = null;
			if (state.mode != ScreenViewMode.YOUTUBE || state.relaySessionId == null || state.waitingForLink) {
				return;
			}
			sessionId = state.relaySessionId;
			knownFrameSequence = state.youtubeFrameSequence;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new YoutubeSnapshotResult(key, MonitorYoutubeRelayClient.snapshot(sessionId, knownFrameSequence), null);
					} catch (Exception exception) {
						return new YoutubeSnapshotResult(key, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeSnapshotResult(server, result)));
	}

	private static void applyYoutubeSnapshotResult(MinecraftServer server, YoutubeSnapshotResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		boolean shouldReschedule = false;
		boolean shouldFadeProgress = false;
		boolean shouldRender = false;
		synchronized (state) {
			if (state.mode != ScreenViewMode.YOUTUBE) {
				return;
			}
			if (result.snapshot() != null) {
				boolean wasLoading = state.loading;
				long previousPositionMs = state.positionMs;
				long previousDurationMs = state.durationMs;
				boolean previousLiveStream = state.liveStream;
				boolean previousAudioPlaceholder = state.audioPlaceholder;
				boolean previousPaused = state.userPaused;
				String previousStatusText = state.statusText;
				long previousFrameSequence = state.youtubeFrameSequence;
				state.mediaTitle = result.snapshot().title();
				if (result.snapshot().frameSequence() != previousFrameSequence) {
					state.youtubeFrameSequence = result.snapshot().frameSequence();
					shouldRender = true;
				}
				if (result.snapshot().frame() != null) {
					state.streamFrame = result.snapshot().frame();
					shouldRender = true;
				}
				state.positionMs = result.snapshot().positionMs();
				state.durationMs = result.snapshot().durationMs();
				state.liveStream = result.snapshot().live();
				state.audioPlaceholder = result.snapshot().audioPlaceholder();
				state.userPaused = result.snapshot().paused();
				state.statusText = result.snapshot().status();
				state.loading = !result.snapshot().ready();
				if (previousDurationMs != state.durationMs
						|| previousLiveStream != state.liveStream
						|| previousAudioPlaceholder != state.audioPlaceholder
						|| previousPaused != state.userPaused
						|| !Objects.equals(previousStatusText, state.statusText)
						|| wasLoading != state.loading) {
					shouldRender = true;
				}
				if (!shouldRender && Math.abs(state.positionMs - previousPositionMs) >= effectiveYoutubeUiRefreshThresholdMs(server, result.screenKey())) {
					shouldRender = true;
				}
				if (result.snapshot().ready()) {
					if (wasLoading) {
						state.progress.complete("READY");
						shouldFadeProgress = true;
						shouldRender = true;
					}
				} else {
					state.progress.setIndeterminate(result.snapshot().live() ? "LIVE" : "LOADING");
					shouldRender = true;
				}
				if (shouldRender) {
					state.version++;
				}
				shouldReschedule = true;
			} else {
				state.loading = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
				state.version++;
				shouldRender = true;
			}
		}
		if (shouldRender && hasNearbyMediaViewer(server, result.screenKey())) {
			requestRuntimeRender(server, result.screenKey());
		}
		if (shouldFadeProgress) {
			scheduleProgressFadeRenders(server, result.screenKey());
		}
		if (shouldReschedule) {
			scheduleNextMediaFrame(server, result.screenKey());
		}
	}

	private static void cleanupMediaSessions(MinecraftServer server) {
		for (ScreenRuntimeKey key : Set.copyOf(MEDIA_STATES.keySet())) {
			if (!isMediaSessionAlive(server, key)) {
				closeMediaSession(server, key);
			}
		}
		PENDING_MEDIA_LINKS.entrySet().removeIf(entry -> !isMediaSessionAlive(server, entry.getValue().screenKey()));
	}

	private static void cleanupExpiredMediaFocus() {
		long now = System.currentTimeMillis();
		PLAYER_MEDIA_FOCUS.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() < now);
	}

	private static void markMediaFocus(ServerPlayer player, ScreenRuntimeKey key) {
		if (player == null || key == null) {
			return;
		}
		PLAYER_MEDIA_FOCUS.put(player.getUUID(), new PlayerMediaFocus(key, System.currentTimeMillis() + MEDIA_SCROLL_FOCUS_TIMEOUT_MS));
	}

	public static boolean onPlayerHotbarScroll(ServerPlayer player, int requestedSlot) {
		if (player == null) {
			return false;
		}
		ScreenComponent component = findObservedMediaComponent(player);
		if (component == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
		if (state == null) {
			return false;
		}

		int currentSlot = player.getInventory().getSelectedSlot();
		int delta = normalizeHotbarDelta(currentSlot, requestedSlot);
		if (delta == 0) {
			return false;
		}

		boolean handled = false;
		Long youtubeSeekTargetMs = null;
		synchronized (state) {
			if (state.overlayMode == MediaOverlayMode.CONTROLS
					&& !state.loading
					&& !state.waitingForLink) {
				cancelPlaybackLocked(state);
				if (state.mode == ScreenViewMode.YOUTUBE && canSeekTimelineLocked(state)) {
					long duration = Math.max(1L, state.durationMs);
					long seekStep = Math.max(YOUTUBE_SCROLL_SEEK_MS, duration / 120L);
					youtubeSeekTargetMs = clampLong(state.positionMs + delta * seekStep, 0L, duration);
					state.positionMs = youtubeSeekTargetMs;
					state.version++;
					handled = true;
				} else if (state.loadedMedia != null && state.loadedMedia.animated() && state.loadedMedia.frameCount() > 1) {
					int seekFrames = Math.max(1, state.loadedMedia.frameCount() / 60);
					state.frameIndex = Math.floorMod(state.frameIndex + delta * seekFrames, state.loadedMedia.frameCount());
					state.version++;
					handled = true;
				}
			}
		}
		if (!handled) {
			return false;
		}

		player.connection.send(new ClientboundSetHeldSlotPacket(currentSlot));
		requestComponentRender(server, component, component.viewMode(), component.launcherPage());
		if (youtubeSeekTargetMs != null) {
			long seekTargetMs = youtubeSeekTargetMs;
			ensureExecutors();
			CompletableFuture.runAsync(() -> {
				try {
					MonitorYoutubeRelayClient.seek(relaySessionId(component.runtimeKey()), seekTargetMs);
				} catch (Exception ignored) {
				}
			}, mediaIoExecutor).thenRun(() -> server.execute(() -> scheduleYoutubeRefresh(server, component.runtimeKey(), 0L)));
		} else {
			resumeMediaPlaybackIfNeeded(server, component.runtimeKey());
		}
		return true;
	}

	private static ScreenComponent findObservedMediaComponent(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 rayEnd = eye.add(player.getLookAngle().scale(MEDIA_CONTROL_DISTANCE));
		AABB searchBox = new AABB(eye, rayEnd).inflate(1.5D);
		Set<ScreenKey> processed = new HashSet<>();
		ScreenComponent nearest = null;
		double nearestDistanceSqr = Double.POSITIVE_INFINITY;

		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, searchBox, candidate -> readScreenState(candidate.getItem()) != null)) {
			ScreenKey key = new ScreenKey(frame.blockPosition(), frame.getDirection());
			if (processed.contains(key)) {
				continue;
			}
			ScreenComponent component = collectComponent(level, frame, processed);
			if (component == null || !isPlayerMode(component.viewMode()) || !component.powered()) {
				continue;
			}
			double hitDistanceSqr = observedComponentHitDistanceSqr(component, eye, rayEnd);
			if (Double.isFinite(hitDistanceSqr) && hitDistanceSqr < nearestDistanceSqr) {
				nearestDistanceSqr = hitDistanceSqr;
				nearest = component;
			}
		}
		return nearest;
	}

	private static double observedComponentHitDistanceSqr(ScreenComponent component, Vec3 start, Vec3 end) {
		if (component == null || start == null || end == null) {
			return Double.POSITIVE_INFINITY;
		}
		double nearestDistanceSqr = Double.POSITIVE_INFINITY;
		for (ItemFrame frame : component.frameCoords().keySet()) {
			if (!frame.isAlive()) {
				continue;
			}
			Optional<Vec3> hit = frame.getBoundingBox().inflate(0.08D).clip(start, end);
			if (hit.isPresent() && hit.get().distanceToSqr(start) <= MEDIA_CONTROL_DISTANCE * MEDIA_CONTROL_DISTANCE) {
				nearestDistanceSqr = Math.min(nearestDistanceSqr, start.distanceToSqr(hit.get()));
			}
		}
		return nearestDistanceSqr;
	}

	private static int normalizeHotbarDelta(int currentSlot, int requestedSlot) {
		int forward = Math.floorMod(requestedSlot - currentSlot, 9);
		int backward = Math.floorMod(currentSlot - requestedSlot, 9);
		if (forward == 0 || backward == 0) {
			return 0;
		}
		if (forward < backward) {
			return forward;
		}
		if (backward < forward) {
			return -backward;
		}
		return forward <= 4 ? forward : -backward;
	}

	private static boolean isMediaSessionAlive(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return false;
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) {
			return false;
		}
		ItemFrame rootFrame = findScreenFrame(level, key.pos(), key.facing());
		if (rootFrame == null) {
			return false;
		}
		ScreenComponent component = collectComponent(level, rootFrame, null);
		return component != null
				&& component.runtimeKey().equals(key)
				&& isPlayerMode(component.viewMode());
	}

	private static void requestRuntimeRender(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		server.execute(() -> {
			ServerLevel level = server.getLevel(key.dimension());
			if (level == null) {
				return;
			}
			ItemFrame rootFrame = findScreenFrame(level, key.pos(), key.facing());
			if (rootFrame == null) {
				closeMediaSession(server, key);
				return;
			}
			ScreenComponent component = collectComponent(level, rootFrame, null);
			if (component == null) {
				return;
			}
			if (isPlayerMode(component.viewMode()) && !hasNearbyMediaViewer(level, component)) {
				return;
			}
			requestComponentRender(server, component, component.viewMode(), component.launcherPage());
		});
	}

	private static void requestComponentRender(MinecraftServer server, ScreenComponent component, ScreenViewMode viewMode, int launcherPage) {
		if (server == null || component == null) {
			return;
		}
		RenderWork work;
		MediaRuntimeState mediaState = null;
		if (isPlayerMode(viewMode)) {
			mediaState = MEDIA_STATES.get(component.runtimeKey());
			if (mediaState != null) {
				MediaDispatchKey dispatchKey = new MediaDispatchKey(component.powered(), viewMode, launcherPage, component.width(), component.height());
				synchronized (mediaState) {
					if (mediaState.activeRenderJobs > 0 && dispatchKey.equals(mediaState.lastDispatchKey)) {
						mediaState.rerenderRequested = true;
						return;
					}
					mediaState.activeRenderJobs++;
					mediaState.lastDispatchKey = dispatchKey;
					mediaState.rerenderRequested = false;
					work = createRenderWork(component, viewMode, launcherPage, mediaState);
				}
			} else {
				work = createRenderWork(component, viewMode, launcherPage, null);
			}
		} else {
			work = createRenderWork(component, viewMode, launcherPage, null);
		}
		if (work == null) {
			if (mediaState != null) {
				synchronized (mediaState) {
					mediaState.activeRenderJobs = Math.max(0, mediaState.activeRenderJobs - 1);
					if (mediaState.activeRenderJobs == 0) {
						mediaState.lastDispatchKey = null;
					}
				}
			}
			return;
		}
		submitRenderWork(server, work);
	}

	private static void submitRenderWork(MinecraftServer server, RenderWork work) {
		if (server == null || work == null) {
			return;
		}
		ensureExecutors();
		renderExecutor.submit(() -> {
			try {
				byte[][] renderedTiles = renderTiles(work);
				server.execute(() -> applyRenderedWork(server, work, renderedTiles));
			} catch (Exception exception) {
				Lg2.LOGGER.error("Monitor render job failed for {}", work.runtimeKey(), exception);
				server.execute(() -> handleRenderFailure(server, work, exception));
			}
		});
	}

	private static void handleRenderFailure(MinecraftServer server, RenderWork work, Exception exception) {
		if (work == null) {
			return;
		}
		if (!isPlayerMode(work.viewMode())) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(work.runtimeKey());
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.activeRenderJobs = Math.max(0, state.activeRenderJobs - 1);
			if (state.activeRenderJobs == 0) {
				state.lastDispatchKey = null;
			}
			state.rerenderRequested = state.activeRenderJobs > 0;
			state.statusText = "RENDER ERROR";
			state.version++;
		}
	}

	private static void applyRenderedWork(MinecraftServer server, RenderWork work, byte[][] renderedTiles) {
		boolean rerenderAgain = false;
		try {
			if (server == null || work == null || renderedTiles == null) {
				return;
			}
			ServerLevel level = server.getLevel(work.runtimeKey().dimension());
			if (level == null) {
				return;
			}
			ItemFrame rootFrame = findScreenFrame(level, work.runtimeKey().pos(), work.runtimeKey().facing());
			if (rootFrame == null) {
				return;
			}
			ScreenComponent component = collectComponent(level, rootFrame, null);
			if (component == null || !matchesCurrentComponent(component, work)) {
				return;
			}
			if (isPlayerMode(work.viewMode())) {
				MediaRuntimeState state = MEDIA_STATES.get(work.runtimeKey());
				if (state == null) {
					return;
				}
				synchronized (state) {
					if (state.version != work.mediaVersion()) {
						return;
					}
				}
			}
			applyRenderedTiles(level, component, renderedTiles);
		} finally {
			if (work != null && isPlayerMode(work.viewMode())) {
				rerenderAgain = finishMediaRender(work.runtimeKey(), work.mediaVersion());
			}
		}
		if (rerenderAgain) {
			requestRuntimeRender(server, work.runtimeKey());
		}
	}

	private static boolean finishMediaRender(ScreenRuntimeKey key, long renderedVersion) {
		if (key == null) {
			return false;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return false;
		}
		synchronized (state) {
			state.activeRenderJobs = Math.max(0, state.activeRenderJobs - 1);
			boolean rerender = state.rerenderRequested || state.version != renderedVersion;
			if (state.activeRenderJobs == 0) {
				state.lastDispatchKey = null;
				state.rerenderRequested = false;
				return rerender;
			}
			return false;
		}
	}

	private static boolean matchesCurrentComponent(ScreenComponent component, RenderWork work) {
		return component != null
				&& work != null
				&& component.runtimeKey().equals(work.runtimeKey())
				&& component.powered() == work.powered()
				&& component.viewMode() == work.viewMode()
				&& component.launcherPage() == work.launcherPage()
				&& component.width() == work.width()
				&& component.height() == work.height();
	}

	private static void synchronizeConnectedScreens(ServerLevel level, ItemFrame startFrame, Set<ScreenKey> processedKeys) {
		synchronizeConnectedScreens(level, startFrame, processedKeys, null, null);
	}

	private static void synchronizeConnectedScreens(
			ServerLevel level,
			ItemFrame startFrame,
			Set<ScreenKey> processedKeys,
			ScreenViewMode forcedViewMode,
			Integer forcedLauncherPage
	) {
		ScreenComponent component = collectComponent(level, startFrame, processedKeys);
		if (component == null) {
			return;
		}

		boolean powered = component.powered();
		ScreenViewMode viewMode = forcedViewMode != null ? forcedViewMode : component.viewMode();
		int launcherPage = forcedLauncherPage != null ? forcedLauncherPage : component.launcherPage();
		if (!powered) {
			viewMode = ScreenViewMode.HOME;
			launcherPage = 0;
			closeMediaSession(level.getServer(), component.runtimeKey());
		}
		ServerLevel mapLevel = photoMapLevel(level.getServer(), level);
		boolean rerenderMaps = false;

		for (Map.Entry<ItemFrame, TileCoord> entry : component.frameCoords().entrySet()) {
			ItemFrame frame = entry.getKey();
			TileCoord tileCoord = entry.getValue();
			ScreenFrame screenFrame = component.byCoord().get(tileCoord);
			if (screenFrame == null) {
				continue;
			}

			ItemStack existing = frame.getItem();
			MapId existingMapId = existing.get(DataComponents.MAP_ID);
			boolean missingMap = existingMapId == null || mapLevel.getMapData(existingMapId) == null;
			ItemStack ensured = ensureMap(level, frame, screenFrame.state());
			ScreenTileState currentState = readScreenState(ensured);
			if (currentState == null) {
				continue;
			}

			int connectionMask = connectionMask(component.byCoord(), tileCoord.x(), tileCoord.y());
			ScreenTileState updatedState = new ScreenTileState(
					CONNECTION_ALL,
					component.width(),
					component.height(),
					tileCoord.x(),
					tileCoord.y(),
					connectionMask,
					powered,
					viewMode,
					viewMode == ScreenViewMode.HOME
							? clampInt(launcherPage, 0, Math.max(0, homePageCount(createUiLayout(component.width(), component.height())) - 1))
							: component.launcherPage(),
					componentGroupId(component.runtimeKey())
			);

			if (missingMap || !currentState.sameRenderState(updatedState)) {
				rerenderMaps = true;
			}
			if (!currentState.equals(updatedState)) {
				ItemStack updated = ensured.copy();
				writeScreenState(updated, updatedState);
				frame.setItem(updated, false);
			}
			ensureDisplay(level, frame, connectionMask);
		}

		if (!rerenderMaps) {
			return;
		}
		requestComponentRender(level.getServer(), component, viewMode, launcherPage);
	}

	private static ScreenComponent collectComponent(ServerLevel level, ItemFrame startFrame, Set<ScreenKey> processedKeys) {
		if (level == null || startFrame == null) {
			return null;
		}
		ScreenTileState startState = readScreenState(startFrame.getItem());
		if (startState == null) {
			return null;
		}
		String targetGroupId = normalizedGroupId(startState);

		Direction facing = startFrame.getDirection();
		Direction right = frameRight(facing);
		Map<ScreenKey, ScreenFrame> frames = new HashMap<>();
		ArrayDeque<ItemFrame> queue = new ArrayDeque<>();
		queue.add(startFrame);

		while (!queue.isEmpty()) {
			ItemFrame frame = queue.removeFirst();
			ScreenTileState state = readScreenState(frame.getItem());
			if (state == null || frame.getDirection() != facing) {
				continue;
			}

			ScreenKey key = new ScreenKey(frame.blockPosition(), facing);
			if (frames.containsKey(key)) {
				continue;
			}
			frames.put(key, new ScreenFrame(frame, state));
			for (PlacementNeighbor candidate : List.of(
					new PlacementNeighbor(frame.blockPosition().relative(right), CONNECTION_RIGHT),
					new PlacementNeighbor(frame.blockPosition().relative(right.getOpposite()), CONNECTION_LEFT),
					new PlacementNeighbor(frame.blockPosition().above(), CONNECTION_UP),
					new PlacementNeighbor(frame.blockPosition().below(), CONNECTION_DOWN)
			)) {
				BlockPos neighborPos = candidate.pos();
				ItemFrame neighbor = findScreenFrame(level, neighborPos, facing);
				if (neighbor == null) {
					continue;
				}
				ScreenTileState neighborState = readScreenState(neighbor.getItem());
				if (neighborState == null) {
					continue;
				}
				if (targetGroupId != null) {
					if (!targetGroupId.equals(normalizedGroupId(neighborState))) {
						continue;
					}
				} else if (!allowsLegacyAttachment(state, candidate.connectionMask())
						|| !allowsLegacyAttachment(neighborState, oppositeConnectionMask(candidate.connectionMask()))) {
					continue;
				}
				queue.add(neighbor);
			}
		}

		if (processedKeys != null) {
			processedKeys.addAll(frames.keySet());
		}
		if (frames.isEmpty()) {
			return null;
		}

		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		Map<ScreenFrame, TileCoord> localCoords = new HashMap<>();
		for (ScreenFrame screenFrame : frames.values()) {
			BlockPos pos = screenFrame.frame().blockPosition();
			int localX = pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
			int localY = pos.getY();
			localCoords.put(screenFrame, new TileCoord(localX, localY));
			minX = Math.min(minX, localX);
			maxX = Math.max(maxX, localX);
			minY = Math.min(minY, localY);
			maxY = Math.max(maxY, localY);
		}

		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		boolean powered = frames.values().stream().anyMatch(screenFrame -> isPowered(level, screenFrame.frame()));
		ScreenViewMode viewMode = frames.values().stream()
				.map(ScreenFrame::state)
				.map(ScreenTileState::viewMode)
				.max(ScreenViewMode::compareTo)
				.orElse(ScreenViewMode.HOME);
		int launcherPage = frames.values().stream()
				.map(ScreenFrame::state)
				.mapToInt(ScreenTileState::launcherPage)
				.max()
				.orElse(0);

		Map<ItemFrame, TileCoord> frameCoords = new HashMap<>();
		Map<TileCoord, ScreenFrame> byCoord = new HashMap<>();
		for (Map.Entry<ScreenFrame, TileCoord> entry : localCoords.entrySet()) {
			int tileX = entry.getValue().x() - minX;
			int tileY = maxY - entry.getValue().y();
			TileCoord tileCoord = new TileCoord(tileX, tileY);
			frameCoords.put(entry.getKey().frame(), tileCoord);
			byCoord.put(tileCoord, entry.getKey());
		}
		ScreenFrame rootFrame = byCoord.get(new TileCoord(0, 0));
		if (rootFrame == null) {
			rootFrame = frames.values().iterator().next();
		}
		return new ScreenComponent(
				new ScreenRuntimeKey(level.dimension(), rootFrame.frame().blockPosition(), facing),
				facing,
				right,
				width,
				height,
				powered,
				viewMode,
				launcherPage,
				frameCoords,
				byCoord
		);
	}

	private static void synchronizeNeighborComponents(ServerLevel level, BlockPos pos, Direction facing) {
		if (level == null || pos == null || facing == null) {
			return;
		}
		Direction right = frameRight(facing);
		Set<ScreenKey> processed = new HashSet<>();
		for (BlockPos candidatePos : List.of(
				pos.relative(right),
				pos.relative(right.getOpposite()),
				pos.above(),
				pos.below()
		)) {
			ItemFrame frame = findScreenFrame(level, candidatePos, facing);
			if (frame == null) {
				continue;
			}
			ScreenKey key = new ScreenKey(frame.blockPosition(), frame.getDirection());
			if (processed.contains(key)) {
				continue;
			}
			synchronizeConnectedScreens(level, frame, processed, null, null);
		}
	}

	private static int connectionMask(Map<TileCoord, ScreenFrame> byCoord, int tileX, int tileY) {
		int mask = 0;
		if (byCoord.containsKey(new TileCoord(tileX - 1, tileY))) {
			mask |= CONNECTION_LEFT;
		}
		if (byCoord.containsKey(new TileCoord(tileX + 1, tileY))) {
			mask |= CONNECTION_RIGHT;
		}
		if (byCoord.containsKey(new TileCoord(tileX, tileY - 1))) {
			mask |= CONNECTION_UP;
		}
		if (byCoord.containsKey(new TileCoord(tileX, tileY + 1))) {
			mask |= CONNECTION_DOWN;
		}
		return mask;
	}

	private static String resolvePlacementGroupId(ServerLevel level, BlockPos framePos, Direction facing, Vec3 clickLocation, boolean shiftPlacement) {
		if (level == null || framePos == null || facing == null) {
			return "";
		}
		Map<Integer, ScreenComponent> adjacentComponents = adjacentComponents(level, framePos, facing);
		if (adjacentComponents.isEmpty()) {
			return standaloneGroupId(level, framePos, facing);
		}
		if (shiftPlacement) {
			int preferredSide = preferredPlacementSide(framePos, facing, clickLocation);
			ScreenComponent chosen = adjacentComponents.get(preferredSide);
			if (chosen == null) {
				return standaloneGroupId(level, framePos, facing);
			}
			String groupId = componentGroupId(chosen.runtimeKey());
			rewriteComponentGroupId(chosen, groupId);
			return groupId;
		}

		ScreenComponent primary = adjacentComponents.values().iterator().next();
		String mergedGroupId = componentGroupId(primary.runtimeKey());
		Set<ScreenRuntimeKey> rewritten = new HashSet<>();
		for (ScreenComponent component : adjacentComponents.values()) {
			if (component == null || !rewritten.add(component.runtimeKey())) {
				continue;
			}
			rewriteComponentGroupId(component, mergedGroupId);
		}
		return mergedGroupId;
	}

	private static PlacementSurfacePoint placementSurfacePoint(BlockPos framePos, Direction facing, Vec3 hitLocation) {
		if (framePos == null || facing == null || hitLocation == null) {
			return null;
		}
		double localX = hitLocation.x - framePos.getX();
		double localY = hitLocation.y - framePos.getY();
		double localZ = hitLocation.z - framePos.getZ();
		double u = switch (facing) {
			case SOUTH -> localX;
			case NORTH -> 1.0D - localX;
			case EAST -> 1.0D - localZ;
			case WEST -> localZ;
			default -> Double.NaN;
		};
		if (!Double.isFinite(u) || !Double.isFinite(localY)) {
			return null;
		}
		return new PlacementSurfacePoint(
				clampDouble(u, 0.0D, 1.0D),
				clampDouble(1.0D - localY, 0.0D, 1.0D)
		);
	}

	private static Map<Integer, ScreenComponent> adjacentComponents(ServerLevel level, BlockPos framePos, Direction facing) {
		if (level == null || framePos == null || facing == null) {
			return Map.of();
		}
		Direction right = frameRight(facing);
		Map<Integer, ScreenComponent> adjacent = new HashMap<>();
		for (PlacementNeighbor candidate : List.of(
				new PlacementNeighbor(framePos.relative(right.getOpposite()), CONNECTION_LEFT),
				new PlacementNeighbor(framePos.relative(right), CONNECTION_RIGHT),
				new PlacementNeighbor(framePos.above(), CONNECTION_UP),
				new PlacementNeighbor(framePos.below(), CONNECTION_DOWN)
		)) {
			ItemFrame neighbor = findScreenFrame(level, candidate.pos(), facing);
			if (neighbor == null) {
				continue;
			}
			ScreenComponent component = collectComponent(level, neighbor, null);
			if (component != null) {
				adjacent.put(candidate.connectionMask(), component);
			}
		}
		return adjacent;
	}

	private static int preferredPlacementSide(BlockPos framePos, Direction facing, Vec3 clickLocation) {
		PlacementSurfacePoint hit = placementSurfacePoint(framePos, facing, clickLocation);
		if (hit == null) {
			return 0;
		}
		Direction right = frameRight(facing);
		return List.of(
				new PlacementNeighbor(framePos.relative(right.getOpposite()), CONNECTION_LEFT, hit.u()),
				new PlacementNeighbor(framePos.relative(right), CONNECTION_RIGHT, 1.0D - hit.u()),
				new PlacementNeighbor(framePos.above(), CONNECTION_UP, hit.v()),
				new PlacementNeighbor(framePos.below(), CONNECTION_DOWN, 1.0D - hit.v())
		).stream()
				.min((leftNeighbor, rightNeighbor) -> Double.compare(leftNeighbor.distance(), rightNeighbor.distance()))
				.map(PlacementNeighbor::connectionMask)
				.orElse(0);
	}

	private static void rewriteComponentGroupId(ScreenComponent component, String groupId) {
		if (component == null || groupId == null || groupId.isBlank()) {
			return;
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			ScreenTileState state = readScreenState(frame.getItem());
			if (state == null || groupId.equals(normalizedGroupId(state))) {
				continue;
			}
			ItemStack updated = frame.getItem().copy();
			writeScreenState(updated, new ScreenTileState(
					CONNECTION_ALL,
					state.gridWidth(),
					state.gridHeight(),
					state.tileX(),
					state.tileY(),
					state.connectionMask(),
					state.powered(),
					state.viewMode(),
					state.launcherPage(),
					groupId
			));
			frame.setItem(updated, false);
		}
	}

	private static boolean allowsLegacyAttachment(ScreenTileState state, int connectionMask) {
		return state != null && (state.attachmentMask() & connectionMask) != 0;
	}

	private static int oppositeConnectionMask(int connectionMask) {
		return switch (connectionMask) {
			case CONNECTION_LEFT -> CONNECTION_RIGHT;
			case CONNECTION_RIGHT -> CONNECTION_LEFT;
			case CONNECTION_UP -> CONNECTION_DOWN;
			case CONNECTION_DOWN -> CONNECTION_UP;
			default -> 0;
		};
	}

	private static String componentGroupId(ScreenRuntimeKey key) {
		if (key == null) {
			return "";
		}
		return key.dimension() + "|" + key.pos().asLong() + "|" + key.facing().getSerializedName();
	}

	private static String standaloneGroupId(ServerLevel level, BlockPos framePos, Direction facing) {
		if (level == null || framePos == null || facing == null) {
			return "";
		}
		return level.dimension() + "|" + framePos.asLong() + "|" + facing.getSerializedName();
	}

	private static String normalizedGroupId(ScreenTileState state) {
		if (state == null || state.groupId() == null) {
			return null;
		}
		String normalized = state.groupId().trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private static ItemStack ensureMap(ServerLevel level, ItemFrame frame, ScreenTileState state) {
		ItemStack stack = frame.getItem();
		MapId mapId = stack.get(DataComponents.MAP_ID);
		ServerLevel mapLevel = photoMapLevel(level.getServer(), level);
		if (mapId != null && mapLevel.getMapData(mapId) != null) {
			return stack;
		}

		ItemStack replacement = createScreenMap(level, state);
		if (!replacement.isEmpty()) {
			frame.setItem(replacement, false);
			return replacement;
		}
		return stack;
	}

	private static ItemStack createScreenMap(ServerLevel level, ScreenTileState state) {
		ServerLevel mapLevel = photoMapLevel(level.getServer(), level);
		ItemStack generated = MapItem.create(mapLevel, PHOTO_MAP_CENTER, PHOTO_MAP_CENTER, (byte) 0, false, false);
		MapId mapId = generated.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return ItemStack.EMPTY;
		}

		ItemStack screenMap = new ItemStack(Items.FILLED_MAP);
		screenMap.set(DataComponents.MAP_ID, mapId);
		writeScreenState(screenMap, state);
		MapItemSavedData mapData = mapLevel.getMapData(mapId);
		if (mapData != null) {
			byte[][] tiles = renderTiles(new RenderWork(null, state.powered(), state.viewMode(), state.launcherPage(), 1, 1, 0L, null));
			applyFrameToMap(mapData, tiles[0]);
		}
		return screenMap;
	}

	private static void writeScreenState(ItemStack stack, ScreenTileState state) {
		if (stack == null || stack.isEmpty() || state == null) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag screenTag = tag.getCompoundOrEmpty(SCREEN_ROOT_TAG);
			screenTag.putBoolean(LINK_LOCKED_TAG, state.attachmentMask() != CONNECTION_ALL);
			screenTag.putInt(ATTACHMENT_MASK_TAG, state.attachmentMask() & 0xF);
			screenTag.putString(GROUP_ID_TAG, state.groupId() == null ? "" : state.groupId());
			screenTag.putInt(GRID_WIDTH_TAG, state.gridWidth());
			screenTag.putInt(GRID_HEIGHT_TAG, state.gridHeight());
			screenTag.putInt(TILE_X_TAG, state.tileX());
			screenTag.putInt(TILE_Y_TAG, state.tileY());
			screenTag.putInt(CONNECTION_MASK_TAG, state.connectionMask() & 0xF);
			screenTag.putBoolean(POWERED_TAG, state.powered());
			screenTag.putString(VIEW_MODE_TAG, state.viewMode().serializedName());
			screenTag.putInt(LAUNCHER_PAGE_TAG, Math.max(0, state.launcherPage()));
			tag.put(SCREEN_ROOT_TAG, screenTag);
		});
	}

	private static ScreenTileState readScreenState(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		CompoundTag root = customData.copyTag();
		if (!root.contains(SCREEN_ROOT_TAG)) {
			return null;
		}
		CompoundTag screenTag = root.getCompoundOrEmpty(SCREEN_ROOT_TAG);
		boolean legacyLinkLocked = screenTag.getBooleanOr(LINK_LOCKED_TAG, false);
		int attachmentMask = screenTag.contains(ATTACHMENT_MASK_TAG)
				? screenTag.getIntOr(ATTACHMENT_MASK_TAG, CONNECTION_ALL)
				: (legacyLinkLocked ? 0 : CONNECTION_ALL);
		return new ScreenTileState(
				attachmentMask & 0xF,
				Math.max(1, screenTag.getIntOr(GRID_WIDTH_TAG, 1)),
				Math.max(1, screenTag.getIntOr(GRID_HEIGHT_TAG, 1)),
				Math.max(0, screenTag.getIntOr(TILE_X_TAG, 0)),
				Math.max(0, screenTag.getIntOr(TILE_Y_TAG, 0)),
				screenTag.getIntOr(CONNECTION_MASK_TAG, 0) & 0xF,
				screenTag.getBooleanOr(POWERED_TAG, false),
				ScreenViewMode.fromTag(screenTag.getStringOr(VIEW_MODE_TAG, ScreenViewMode.HOME.serializedName())),
				Math.max(0, screenTag.getIntOr(LAUNCHER_PAGE_TAG, 0)),
				screenTag.getStringOr(GROUP_ID_TAG, "")
		);
	}

	private static boolean isPowered(ServerLevel level, ItemFrame frame) {
		BlockPos supportPos = frame.blockPosition().relative(frame.getDirection().getOpposite());
		return level.hasNeighborSignal(supportPos)
				|| level.getBestNeighborSignal(supportPos) > 0
				|| level.hasNeighborSignal(frame.blockPosition());
	}

	private static RenderWork createRenderWork(ScreenComponent component, ScreenViewMode viewMode, int launcherPage, MediaRuntimeState mediaState) {
		if (component == null) {
			return null;
		}
		MediaVisualSnapshot mediaSnapshot = null;
		long mediaVersion = 0L;
		if (isPlayerMode(viewMode)) {
			mediaSnapshot = captureMediaSnapshot(mediaState);
			mediaVersion = mediaSnapshot != null ? mediaSnapshot.version() : 0L;
		}
		return new RenderWork(
				component.runtimeKey(),
				component.powered(),
				viewMode,
				launcherPage,
				component.width(),
				component.height(),
				mediaVersion,
				mediaSnapshot
		);
	}

	private static MediaVisualSnapshot captureMediaSnapshot(MediaRuntimeState state) {
		if (state == null) {
			return new MediaVisualSnapshot(0L, null, false, false, false, 0, 0, 0.0F, "", false, MediaOverlayMode.CONTROLS, MediaScaleMode.FIT, "", "ВСТАВЬ URL", null);
		}
		boolean youtubeMode = state.mode == ScreenViewMode.YOUTUBE;
		BufferedImage frame = youtubeMode
				? state.streamFrame
				: state.loadedMedia != null ? state.loadedMedia.frame(state.frameIndex) : null;
		boolean hasMedia = hasDisplayableMediaLocked(state);
		boolean playbackControlsVisible = youtubeMode
				? state.sourceUrl != null || state.relaySessionId != null
				: state.loadedMedia != null && state.loadedMedia.animated();
		boolean timelineSeekable = youtubeMode
				? state.durationMs > 0L && !state.liveStream
				: state.loadedMedia != null && state.loadedMedia.frameCount() > 1;
		int timelineIndex = youtubeMode
				? (int) Math.min(Integer.MAX_VALUE, state.positionMs)
				: state.loadedMedia != null ? Math.floorMod(state.frameIndex, Math.max(1, state.loadedMedia.frameCount())) : 0;
		int timelineCount = youtubeMode
				? (int) Math.min(Integer.MAX_VALUE, state.durationMs)
				: state.loadedMedia != null ? state.loadedMedia.frameCount() : 0;
		float timelineFraction = youtubeMode
				? youtubeTimelineFraction(state)
				: state.loadedMedia != null && state.loadedMedia.frameCount() > 1
				? (float) timelineIndex / (float) Math.max(1, state.loadedMedia.frameCount() - 1)
				: 0.0F;
		String timelineLabel = youtubeMode
				? state.liveStream ? "LIVE" : formatPlaybackTime(state.positionMs) + " / " + formatPlaybackTime(state.durationMs)
				: (timelineIndex + 1) + "/" + Math.max(1, timelineCount);
		return new MediaVisualSnapshot(
				state.version,
				frame,
				hasMedia,
				playbackControlsVisible,
				timelineSeekable,
				timelineIndex,
				timelineCount,
				timelineFraction,
				timelineLabel,
				isPlaybackPausedLocked(state),
				state.overlayMode,
				state.scaleMode,
				state.statusText,
				youtubeMode ? "YOUTUBE URL" : "ВСТАВЬ URL",
				state.progress.snapshot()
		);
	}

	private static byte[][] renderTiles(RenderWork work) {
		if (work == null) {
			return new byte[0][];
		}
		if (!isPlayerMode(work.viewMode())) {
			RenderCacheKey key = new RenderCacheKey(work.powered(), work.viewMode(), work.launcherPage(), work.width(), work.height());
			byte[][] cached = TILE_CACHE.get(key);
			if (cached != null) {
				return cached;
			}
		}

		int pixelWidth = work.width() * MAP_SIZE;
		int pixelHeight = work.height() * MAP_SIZE;
		BufferedImage canvas = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		configureUiGraphics(graphics);
		drawBaseBackground(graphics, work.width(), work.height(), work.powered());
		if (work.powered()) {
			UiLayout layout = createUiLayout(work.width(), work.height());
			if (work.viewMode() == ScreenViewMode.HOME) {
				drawHomeScreen(graphics, layout, work.launcherPage());
			} else {
				drawAppScreen(graphics, layout, appForViewMode(work.viewMode()), work.mediaSnapshot());
			}
		}
		graphics.dispose();

		int[] rgbPixels = canvas.getRGB(0, 0, pixelWidth, pixelHeight, null, 0, pixelWidth);
		byte[][] tiles = new byte[work.width() * work.height()][MAP_SIZE * MAP_SIZE];
		quantizeTiles(work, rgbPixels, pixelWidth, tiles);

		if (!isPlayerMode(work.viewMode())) {
			TILE_CACHE.put(new RenderCacheKey(work.powered(), work.viewMode(), work.launcherPage(), work.width(), work.height()), tiles);
		}
		return tiles;
	}

	private static void configureUiGraphics(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private static void quantizeTiles(RenderWork work, int[] rgbPixels, int pixelWidth, byte[][] tiles) {
		if (work == null || rgbPixels == null || tiles == null) {
			return;
		}
		int tileCount = work.width() * work.height();
		if (tileCount <= 1 || quantizeExecutor == null) {
			for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
				quantizeSingleTile(work.width(), rgbPixels, pixelWidth, tileIndex, tiles[tileIndex]);
			}
			return;
		}

		CompletableFuture<?>[] futures = new CompletableFuture<?>[tileCount];
		for (int tileIndex = 0; tileIndex < tileCount; tileIndex++) {
			final int currentTileIndex = tileIndex;
			futures[tileIndex] = CompletableFuture.runAsync(
					() -> quantizeSingleTile(work.width(), rgbPixels, pixelWidth, currentTileIndex, tiles[currentTileIndex]),
					quantizeExecutor
			);
		}
		CompletableFuture.allOf(futures).join();
	}

	private static void quantizeSingleTile(int tilesWide, int[] rgbPixels, int pixelWidth, int tileIndex, byte[] tile) {
		if (tilesWide <= 0 || rgbPixels == null || tile == null) {
			return;
		}
		int tileX = Math.floorMod(tileIndex, tilesWide);
		int tileY = tileIndex / tilesWide;
		int tileOriginX = tileX * MAP_SIZE;
		int tileOriginY = tileY * MAP_SIZE;
		for (int localY = 0; localY < MAP_SIZE; localY++) {
			int globalY = tileOriginY + localY;
			int rowStart = globalY * pixelWidth + tileOriginX;
			int tileRowStart = localY * MAP_SIZE;
			for (int localX = 0; localX < MAP_SIZE; localX++) {
				int globalX = tileOriginX + localX;
				int rgb = rgbPixels[rowStart + localX] & 0xFFFFFF;
				tile[tileRowStart + localX] = MapPaletteQuantizer.quantizeDithered(rgb, globalX, globalY);
			}
		}
	}

	private static void drawBaseBackground(Graphics2D graphics, int width, int height, boolean powered) {
		BufferedImage base = powered ? loadOnBaseImage() : loadOffBaseImage();
		int pixelWidth = width * MAP_SIZE;
		int pixelHeight = height * MAP_SIZE;
		graphics.drawImage(base, 0, 0, pixelWidth, pixelHeight, null);

		if (powered) {
			graphics.setPaint(new GradientPaint(
					0.0F,
					0.0F,
					new Color(255, 255, 255, 44),
					0.0F,
					pixelHeight,
					new Color(200, 224, 255, 16)
			));
			graphics.fillRect(0, 0, pixelWidth, pixelHeight);
			graphics.setColor(new Color(0, 0, 0, 10));
			for (int y = 0; y < pixelHeight; y += 4) {
				graphics.drawLine(0, y, pixelWidth, y);
			}
		} else {
			graphics.setPaint(new GradientPaint(
					0.0F,
					0.0F,
					new Color(0, 0, 0, 84),
					0.0F,
					pixelHeight,
					new Color(8, 10, 12, 168)
			));
			graphics.fillRect(0, 0, pixelWidth, pixelHeight);
		}
	}

	private static void drawHomeScreen(Graphics2D graphics, UiLayout layout, int launcherPage) {
		UiRect panel = homePanelRect(layout);
		UiRect header = homeHeaderRect(layout, panel);
		fillRoundedRect(graphics, header, clampInt(layout.unit() * 2, 12, 20), new Color(18, 24, 30, 196));
		strokeRoundedRect(graphics, header, clampInt(layout.unit() * 2, 12, 20), 1.0F, new Color(255, 255, 255, 66));
		drawCenteredText(graphics, "ПРИЛОЖЕНИЯ", header, new Color(248, 250, 252), Font.BOLD, clampInt(layout.unit(), 10, 16));

		List<MonitorApp> visibleApps = visibleHomeApps(layout, launcherPage);
		for (int index = 0; index < visibleApps.size(); index++) {
			drawHomeAppCard(graphics, layout, homeAppCardRect(layout, launcherPage, index), visibleApps.get(index));
		}

		int pageCount = homePageCount(layout);
		if (pageCount > 1) {
			UiRect footer = homeFooterRect(layout, panel);
			fillRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 18), new Color(18, 24, 30, 184));
			strokeRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 18), 1.0F, new Color(255, 255, 255, 52));
			drawCenteredText(
					graphics,
					(launcherPage + 1) + "/" + pageCount,
					new UiRect(footer.x() + footer.width() / 4, footer.y(), footer.width() / 2, footer.height()),
					new Color(248, 251, 255),
					Font.BOLD,
					clampInt(layout.unit(), 10, 14)
			);

			if (launcherPage > 0) {
				UiRect upRect = homeScrollUpRect(layout);
				if (upRect != null) {
					drawLauncherArrowButton(graphics, upRect, true);
				}
			}
			if (launcherPage + 1 < pageCount) {
				UiRect downRect = homeScrollDownRect(layout);
				if (downRect != null) {
					drawLauncherArrowButton(graphics, downRect, false);
				}
			}
		}
	}

	private static void drawAppScreen(Graphics2D graphics, UiLayout layout, MonitorApp app, MediaVisualSnapshot mediaSnapshot) {
		if (app == null) {
			drawHomeScreen(graphics, layout, 0);
			return;
		}
		if ("media".equalsIgnoreCase(app.id()) || "youtube".equalsIgnoreCase(app.id())) {
			drawMediaScreen(graphics, layout, mediaSnapshot);
			return;
		}
		drawGenericAppScreen(graphics, layout, app);
	}

	private static void drawGenericAppScreen(Graphics2D graphics, UiLayout layout, MonitorApp app) {
		UiRect headerRect = mediaHeaderRect(layout);
		graphics.setPaint(new GradientPaint(
				headerRect.x(),
				headerRect.y(),
				new Color(app.accentStartRgb()),
				headerRect.right(),
				headerRect.bottom(),
				new Color(app.accentEndRgb())
		));
		fillRoundedRect(graphics, headerRect, clampInt(layout.unit() * 2, 14, 24), null);
		strokeRoundedRect(graphics, headerRect, clampInt(layout.unit() * 2, 14, 24), 1.0F, new Color(255, 255, 255, 82));

		UiRect closeRect = genericCloseRect(layout);
		fillRoundedRect(graphics, closeRect, clampInt(layout.unit() + 2, 10, 18), new Color(15, 18, 24, 224));
		drawCloseGlyph(graphics, closeRect, new Color(248, 251, 255));

		UiRect titleRect = new UiRect(
				closeRect.right() + clampInt(layout.unit() / 2, 6, 12),
				headerRect.y(),
				Math.max(24, headerRect.right() - closeRect.right() - layout.unit() * 2),
				headerRect.height()
		);
		drawVerticalText(graphics, app.title(), titleRect, new Color(248, 251, 255), Font.BOLD, clampInt(layout.unit(), 11, 18));

		UiRect heroRect = genericAppHeroRect(layout);
		fillRoundedRect(graphics, heroRect, clampInt(layout.unit() * 2, 14, 24), withAlpha(app.panelRgb(), 214));
		strokeRoundedRect(graphics, heroRect, clampInt(layout.unit() * 2, 14, 24), 1.0F, new Color(255, 255, 255, 68));

		int iconSize = clampInt(Math.min(heroRect.width(), heroRect.height()) / 2, 48, 96);
		UiRect iconRect = new UiRect(
				heroRect.x() + (heroRect.width() - iconSize) / 2,
				heroRect.y() + layout.unit(),
				iconSize,
				iconSize
		);
		fillRoundedRect(graphics, iconRect, clampInt(layout.unit() * 2, 12, 22), new Color(255, 255, 255, 250));
		drawAppIcon(graphics, app, iconRect, clampInt(layout.unit() / 2, 4, 8));

		UiRect titleTextRect = new UiRect(
				heroRect.x() + layout.unit(),
				iconRect.bottom() + layout.unit() / 2,
				heroRect.width() - layout.unit() * 2,
				clampInt(layout.unit() * 2, 20, 30)
		);
		drawCenteredText(graphics, app.screenTitle(), titleTextRect, new Color(245, 247, 250), Font.BOLD, clampInt(layout.unit() + 1, 11, 17));

		UiRect hintRect = new UiRect(
				heroRect.x() + layout.unit(),
				titleTextRect.bottom(),
				heroRect.width() - layout.unit() * 2,
				clampInt(layout.unit() * 2, 18, 28)
		);
		drawCenteredText(graphics, app.screenHint(), hintRect, new Color(212, 220, 228), Font.PLAIN, clampInt(layout.unit() - 1, 9, 13));
	}

	private static void drawMediaScreen(Graphics2D graphics, UiLayout layout, MediaVisualSnapshot state) {
		UiRect canvasRect = mediaCanvasRect(layout);
		UiRect closeRect = mediaCloseRect(layout);
		BufferedImage mediaFrame = state != null ? state.frame() : null;
		boolean hasMedia = state != null && state.hasMedia();
		UiRect linkRect = mediaLinkRect(layout, hasMedia);
		UiRect scaleRect = mediaScaleRect(layout);
		UiRect timelineRect = mediaTimelineRect(layout);

		if (mediaFrame != null) {
			drawScaledImage(graphics, mediaFrame, canvasRect, state.scaleMode());
		} else {
			graphics.setPaint(new GradientPaint(
					canvasRect.x(),
					canvasRect.y(),
					new Color(230, 236, 242, 24),
					canvasRect.right(),
					canvasRect.bottom(),
					new Color(32, 40, 48, 54)
			));
			fillRoundedRect(graphics, canvasRect, clampInt(layout.unit() * 2, 12, 22), null);
			strokeRoundedRect(graphics, canvasRect, clampInt(layout.unit() * 2, 12, 22), 1.0F, new Color(255, 255, 255, 36));
		}

		if (state != null && state.overlayMode() == MediaOverlayMode.CONTROLS) {
			int shadeHeight = clampInt(layout.unit() * 5, 40, 72);
			graphics.setPaint(new GradientPaint(
					0.0F,
					canvasRect.y(),
					new Color(0, 0, 0, 118),
					0.0F,
					canvasRect.y() + shadeHeight,
					new Color(0, 0, 0, 0)
			));
			graphics.fillRect(canvasRect.x(), canvasRect.y(), canvasRect.width(), shadeHeight);
			graphics.setPaint(new GradientPaint(
					0.0F,
					canvasRect.bottom() - shadeHeight,
					new Color(0, 0, 0, 0),
					0.0F,
					canvasRect.bottom(),
					new Color(0, 0, 0, 126)
			));
			graphics.fillRect(canvasRect.x(), canvasRect.bottom() - shadeHeight, canvasRect.width(), shadeHeight);

			drawMediaCloseButton(graphics, closeRect, layout);
			if (hasMedia) {
				drawMediaSearchBar(
						graphics,
						linkRect,
						state != null ? state.linkPlaceholder() : "ВСТАВЬ URL",
						true,
						layout
				);
				drawMediaScaleButton(graphics, scaleRect, state != null ? state.scaleMode() : MediaScaleMode.FIT, layout);
				drawMediaTimeline(graphics, timelineRect, state, layout);
			}
		}

		TaskProgress.Snapshot progress = state != null ? state.progress() : null;
		String status = state != null ? state.statusText() : "";
		boolean showPromptStatus = status != null
				&& !status.isBlank()
				&& !isMediaPromptStatus(status)
				&& !isMediaLoadingStatus(status)
				&& (progress == null || !progress.visible());
		if (!hasMedia && showPromptStatus) {
			UiRect statusRect = mediaStatusRect(layout);
			fillRoundedRect(graphics, statusRect, clampInt(layout.unit() * 2, 12, 20), new Color(12, 16, 20, 208));
			strokeRoundedRect(graphics, statusRect, clampInt(layout.unit() * 2, 12, 20), 1.0F, new Color(255, 255, 255, 42));
			drawCenteredText(
					graphics,
					status != null && !status.isBlank() ? status : "SEND LINK IN CHAT",
					statusRect,
					new Color(248, 251, 255),
					Font.BOLD,
					clampInt(layout.unit() - 1, 9, 14)
			);
		}

		if (progress != null && progress.visible()) {
			UiRect progressRect = mediaProgressRect(layout);
			drawProgressBar(graphics, progressRect, progress, layout);
		}

		if (!hasMedia) {
			drawMediaSearchBar(
					graphics,
					linkRect,
					state != null ? state.linkPlaceholder() : "ВСТАВЬ URL",
					false,
					layout
			);
		}
	}

	private static void drawProgressBar(Graphics2D graphics, UiRect rect, TaskProgress.Snapshot progress, UiLayout layout) {
		float alpha = progress.alpha();
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), withAlpha(new Color(10, 14, 18, 214), alpha));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), 1.0F, withAlpha(new Color(255, 255, 255, 44), alpha));
		int barHeight = clampInt(layout.unit(), 8, 14);
		UiRect barRect = new UiRect(rect.x() + layout.unit(), rect.bottom() - barHeight - layout.unit() / 2, rect.width() - layout.unit() * 2, barHeight);
		fillRoundedRect(graphics, barRect, clampInt(barHeight, 6, 12), withAlpha(new Color(255, 255, 255, 38), alpha));
		if (progress.determinate()) {
			int fillWidth = Math.max(6, Math.round(barRect.width() * progress.fraction()));
			fillRoundedRect(graphics, new UiRect(barRect.x(), barRect.y(), Math.min(barRect.width(), fillWidth), barRect.height()), clampInt(barHeight, 6, 12), withAlpha(new Color(86, 188, 255, 224), alpha));
		} else {
			int pulseWidth = Math.max(12, barRect.width() / 4);
			long pulse = (System.currentTimeMillis() / 120L) % Math.max(1, barRect.width());
			int pulseX = barRect.x() + (int) Math.min(barRect.width() - pulseWidth, pulse);
			fillRoundedRect(graphics, new UiRect(pulseX, barRect.y(), pulseWidth, barRect.height()), clampInt(barHeight, 6, 12), withAlpha(new Color(86, 188, 255, 224), alpha));
		}
		String label = localizedProgressStage(progress.stage());
		drawCenteredText(graphics, label, new UiRect(rect.x() + layout.unit(), rect.y() + 2, rect.width() - layout.unit() * 2, rect.height() / 2), withAlpha(new Color(248, 251, 255), alpha), Font.BOLD, clampInt(layout.unit() - 1, 8, 13));
	}

	private static void drawHomeAppCard(Graphics2D graphics, UiLayout layout, UiRect cardRect, MonitorApp app) {
		graphics.setPaint(new GradientPaint(
				cardRect.x(),
				cardRect.y(),
				withAlpha(app.accentStartRgb(), 234),
				cardRect.right(),
				cardRect.bottom(),
				withAlpha(app.accentEndRgb(), 234)
		));
		fillRoundedRect(graphics, cardRect, clampInt(layout.unit() * 2, 12, 20), null);
		strokeRoundedRect(graphics, cardRect, clampInt(layout.unit() * 2, 12, 20), 1.0F, new Color(255, 255, 255, 56));

		UiRect iconRect = homeAppIconRect(cardRect, layout);
		drawAppIcon(graphics, app, iconRect, clampInt(layout.unit() / 3, 2, 5));

		UiRect labelRect = homeAppLabelRect(layout, cardRect);
		fillRoundedRect(graphics, labelRect, clampInt(layout.unit(), 8, 14), new Color(12, 16, 20, 176));
		drawCenteredText(graphics, app.title(), labelRect, new Color(248, 251, 255), Font.BOLD, clampInt(layout.unit() - 1, 9, 13));
	}

	private static void drawFloatingButton(Graphics2D graphics, UiRect rect, String label, Color fill, Color text, UiLayout layout) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), fill);
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), 1.0F, new Color(255, 255, 255, 44));
		drawCenteredText(graphics, label, rect, text, Font.BOLD, clampInt(layout.unit() - 1, 8, 13));
	}

	private static void drawMediaCloseButton(Graphics2D graphics, UiRect rect, UiLayout layout) {
		drawRoundMediaButtonBase(graphics, rect);
		drawCloseGlyph(graphics, rect.inset(Math.max(1, layout.unit() / 5)), new Color(248, 251, 255));
	}

	private static void drawMediaScaleButton(Graphics2D graphics, UiRect rect, MediaScaleMode scaleMode, UiLayout layout) {
		drawRoundMediaButtonBase(graphics, rect);
		UiRect iconRect = rect.inset(Math.max(2, layout.unit() / 4));
		switch (scaleMode != null ? scaleMode : MediaScaleMode.FIT) {
			case FILL -> drawMediaFillGlyph(graphics, iconRect, new Color(248, 251, 255));
			case STRETCH -> drawMediaStretchGlyph(graphics, iconRect, new Color(248, 251, 255));
			case FIT -> drawMediaFitGlyph(graphics, iconRect, new Color(248, 251, 255));
		}
	}

	private static void drawMediaPlayPauseButton(Graphics2D graphics, UiRect rect, boolean paused, UiLayout layout) {
		drawRoundMediaButtonBase(graphics, rect);
		UiRect iconRect = rect.inset(Math.max(2, layout.unit() / 4));
		if (paused) {
			drawPlayGlyph(graphics, iconRect, new Color(248, 251, 255));
		} else {
			drawPauseGlyph(graphics, iconRect, new Color(248, 251, 255));
		}
	}

	private static void drawRoundMediaButtonBase(Graphics2D graphics, UiRect rect) {
		int arc = Math.min(rect.width(), rect.height());
		fillRoundedRect(graphics, rect, arc, new Color(12, 16, 20, 214));
		strokeRoundedRect(graphics, rect, arc, 1.0F, new Color(255, 255, 255, 44));
	}

	private static void drawMediaSearchBar(Graphics2D graphics, UiRect rect, String placeholder, boolean compact, UiLayout layout) {
		int arc = Math.min(rect.height(), rect.width());
		graphics.setPaint(new GradientPaint(
				rect.x(),
				rect.y(),
				new Color(12, 16, 20, compact ? 232 : 222),
				rect.right(),
				rect.bottom(),
				new Color(22, 30, 38, compact ? 208 : 198)
		));
		fillRoundedRect(graphics, rect, arc, null);
		strokeRoundedRect(graphics, rect, arc, 1.0F, new Color(255, 255, 255, compact ? 60 : 76));

		UiRect iconRect = new UiRect(
				rect.x() + clampInt(layout.unit() / 2, 5, 10),
				rect.y() + (rect.height() - clampInt(layout.unit() + 2, 10, 16)) / 2,
				clampInt(layout.unit() + 2, 10, 16),
				clampInt(layout.unit() + 2, 10, 16)
		);
		drawSearchGlyph(graphics, iconRect, new Color(248, 251, 255, compact ? 214 : 236));

		UiRect textRect = new UiRect(
				iconRect.right() + clampInt(layout.unit() / 2, 4, 8),
				rect.y(),
				rect.right() - iconRect.right() - clampInt(layout.unit() * 2, 12, 22),
				rect.height()
		);
		drawVerticalText(graphics, placeholder, textRect, new Color(248, 251, 255, compact ? 214 : 236), Font.BOLD, clampInt(layout.unit() - (compact ? 1 : 0), 9, compact ? 14 : 18));
	}

	private static void drawMediaTimeline(Graphics2D graphics, UiRect rect, MediaVisualSnapshot state, UiLayout layout) {
		if (state == null || !state.playbackControlsVisible()) {
			return;
		}
		int arc = Math.min(rect.height(), rect.width());
		fillRoundedRect(graphics, rect, arc, new Color(10, 14, 18, 214));
		strokeRoundedRect(graphics, rect, arc, 1.0F, new Color(255, 255, 255, 44));

		UiRect playPauseRect = mediaPlayPauseRect(layout);
		UiRect counterRect = mediaTimelineCounterRect(layout);
		UiRect trackRect = mediaTimelineTrackRect(layout);
		drawMediaPlayPauseButton(graphics, playPauseRect, state.paused(), layout);
		drawCenteredText(graphics, state.timelineLabel(), counterRect, new Color(248, 251, 255, 214), Font.BOLD, clampInt(layout.unit() - 1, 8, 13));
		fillRoundedRect(graphics, trackRect, Math.min(trackRect.height(), trackRect.width()), new Color(255, 255, 255, 36));
		float fraction = state.timelineFraction();
		int progressWidth = Math.max(trackRect.height(), Math.round(trackRect.width() * fraction));
		fillRoundedRect(graphics, new UiRect(trackRect.x(), trackRect.y(), Math.min(trackRect.width(), progressWidth), trackRect.height()), Math.min(trackRect.height(), trackRect.width()), new Color(86, 188, 255, 224));

		if (state.timelineSeekable()) {
			int knobSize = clampInt(trackRect.height() + 4, 10, 16);
			int knobX = trackRect.x() + Math.round((trackRect.width() - knobSize) * fraction);
			int knobY = trackRect.y() + (trackRect.height() - knobSize) / 2;
			fillRoundedRect(graphics, new UiRect(knobX, knobY, knobSize, knobSize), knobSize, new Color(248, 251, 255, 248));
		}
	}

	private static void drawScaledImage(Graphics2D graphics, BufferedImage image, UiRect rect, MediaScaleMode scaleMode) {
		if (image == null || rect.width() <= 0 || rect.height() <= 0) {
			return;
		}
		Shape previousClip = graphics.getClip();
		graphics.setClip(rect.x(), rect.y(), rect.width(), rect.height());
		if (scaleMode == MediaScaleMode.STRETCH) {
			graphics.drawImage(image, rect.x(), rect.y(), rect.width(), rect.height(), null);
			graphics.setClip(previousClip);
			return;
		}

		double scale = scaleMode == MediaScaleMode.FILL
				? Math.max(rect.width() / (double) image.getWidth(), rect.height() / (double) image.getHeight())
				: Math.min(rect.width() / (double) image.getWidth(), rect.height() / (double) image.getHeight());
		int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		int drawX = rect.x() + (rect.width() - drawWidth) / 2;
		int drawY = rect.y() + (rect.height() - drawHeight) / 2;
		graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
		graphics.setClip(previousClip);
	}

	private static void drawCloseGlyph(Graphics2D graphics, UiRect rect, Color color) {
		int pad = Math.max(4, rect.width() / 4);
		Stroke previous = graphics.getStroke();
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawLine(rect.x() + pad, rect.y() + pad, rect.right() - pad, rect.bottom() - pad);
		graphics.drawLine(rect.right() - pad, rect.y() + pad, rect.x() + pad, rect.bottom() - pad);
		graphics.setStroke(previous);
	}

	private static void drawPlayGlyph(Graphics2D graphics, UiRect rect, Color color) {
		int left = rect.x() + Math.max(1, rect.width() / 5);
		int right = rect.right() - Math.max(1, rect.width() / 6);
		int top = rect.y() + Math.max(1, rect.height() / 6);
		int bottom = rect.bottom() - Math.max(1, rect.height() / 6);
		graphics.setColor(color);
		graphics.fillPolygon(
				new int[]{left, left, right},
				new int[]{top, bottom, rect.y() + rect.height() / 2},
				3
		);
	}

	private static void drawPauseGlyph(Graphics2D graphics, UiRect rect, Color color) {
		int barWidth = Math.max(2, rect.width() / 5);
		int gap = Math.max(2, rect.width() / 7);
		int leftX = rect.x() + Math.max(1, (rect.width() - barWidth * 2 - gap) / 2);
		int topY = rect.y() + Math.max(1, rect.height() / 6);
		int barHeight = Math.max(4, rect.height() - Math.max(2, rect.height() / 3));
		graphics.setColor(color);
		graphics.fillRoundRect(leftX, topY, barWidth, barHeight, barWidth, barWidth);
		graphics.fillRoundRect(leftX + barWidth + gap, topY, barWidth, barHeight, barWidth, barWidth);
	}

	private static void drawMediaFitGlyph(Graphics2D graphics, UiRect rect, Color color) {
		Stroke previous = graphics.getStroke();
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(1.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawRoundRect(rect.x(), rect.y(), rect.width() - 1, rect.height() - 1, 4, 4);
		int insetX = Math.max(2, rect.width() / 5);
		int insetY = Math.max(2, rect.height() / 5);
		graphics.drawRoundRect(
				rect.x() + insetX,
				rect.y() + insetY,
				Math.max(3, rect.width() - insetX * 2 - 1),
				Math.max(3, rect.height() - insetY * 2 - 1),
				4,
				4
		);
		graphics.setStroke(previous);
	}

	private static void drawMediaFillGlyph(Graphics2D graphics, UiRect rect, Color color) {
		Stroke previous = graphics.getStroke();
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(1.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawRoundRect(rect.x(), rect.y(), rect.width() - 1, rect.height() - 1, 4, 4);
		int fillInsetX = Math.max(1, rect.width() / 7);
		int fillInsetY = Math.max(2, rect.height() / 6);
		fillRoundedRect(
				graphics,
				new UiRect(
						rect.x() + fillInsetX,
						rect.y() + fillInsetY,
						Math.max(4, rect.width() - fillInsetX * 2),
						Math.max(4, rect.height() - fillInsetY * 2)
				),
				4,
				withAlpha(color, 0.92F)
		);
		graphics.setStroke(previous);
	}

	private static void drawMediaStretchGlyph(Graphics2D graphics, UiRect rect, Color color) {
		Stroke previous = graphics.getStroke();
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(1.8F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawRoundRect(rect.x(), rect.y(), rect.width() - 1, rect.height() - 1, 4, 4);
		int centerX = rect.x() + rect.width() / 2;
		int centerY = rect.y() + rect.height() / 2;
		int horizontalInset = Math.max(2, rect.width() / 5);
		int verticalInset = Math.max(2, rect.height() / 5);
		graphics.drawLine(rect.x() + horizontalInset, centerY, rect.right() - horizontalInset, centerY);
		graphics.drawLine(centerX, rect.y() + verticalInset, centerX, rect.bottom() - verticalInset);
		graphics.fillPolygon(
				new int[]{rect.right() - horizontalInset, rect.right() - horizontalInset - 3, rect.right() - horizontalInset - 3},
				new int[]{centerY, centerY - 3, centerY + 3},
				3
		);
		graphics.fillPolygon(
				new int[]{rect.x() + horizontalInset, rect.x() + horizontalInset + 3, rect.x() + horizontalInset + 3},
				new int[]{centerY, centerY - 3, centerY + 3},
				3
		);
		graphics.fillPolygon(
				new int[]{centerX, centerX - 3, centerX + 3},
				new int[]{rect.y() + verticalInset, rect.y() + verticalInset + 3, rect.y() + verticalInset + 3},
				3
		);
		graphics.fillPolygon(
				new int[]{centerX, centerX - 3, centerX + 3},
				new int[]{rect.bottom() - verticalInset, rect.bottom() - verticalInset - 3, rect.bottom() - verticalInset - 3},
				3
		);
		graphics.setStroke(previous);
	}

	private static void drawSearchGlyph(Graphics2D graphics, UiRect rect, Color color) {
		int size = Math.min(rect.width(), rect.height());
		int lensSize = Math.max(4, size * 2 / 3);
		int lensX = rect.x();
		int lensY = rect.y() + (rect.height() - lensSize) / 2;
		int handleStartX = lensX + lensSize - 1;
		int handleStartY = lensY + lensSize - 1;
		int handleEndX = rect.right() - 1;
		int handleEndY = rect.bottom() - 1;
		Stroke previous = graphics.getStroke();
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawOval(lensX, lensY, lensSize, lensSize);
		graphics.drawLine(handleStartX, handleStartY, handleEndX, handleEndY);
		graphics.setStroke(previous);
	}

	private static void drawAppIcon(Graphics2D graphics, MonitorApp app, UiRect rect, int padding) {
		if (app == null) {
			return;
		}
		BufferedImage image = loadAppIcon(app);
		if (image == null) {
			return;
		}
		drawContainedImage(graphics, image, rect, padding);
	}

	private static void drawContainedImage(Graphics2D graphics, BufferedImage image, UiRect rect, int padding) {
		if (image == null || rect.width() <= 0 || rect.height() <= 0) {
			return;
		}
		int availableWidth = Math.max(1, rect.width() - padding * 2);
		int availableHeight = Math.max(1, rect.height() - padding * 2);
		double scale = Math.min(availableWidth / (double) image.getWidth(), availableHeight / (double) image.getHeight());
		int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		int drawX = rect.x() + (rect.width() - drawWidth) / 2;
		int drawY = rect.y() + (rect.height() - drawHeight) / 2;
		graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
	}

	private static BufferedImage loadAppIcon(MonitorApp app) {
		if (app == null) {
			return null;
		}
		return APP_ICON_CACHE.computeIfAbsent(
				app.id(),
				ignored -> loadPngImage(app.iconResourcePath(), fallbackAppIcon(app))
		);
	}

	private static BufferedImage fallbackAppIcon(MonitorApp app) {
		BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		configureUiGraphics(graphics);
		graphics.setPaint(new GradientPaint(
				0.0F,
				0.0F,
				new Color(app.accentStartRgb()),
				128.0F,
				128.0F,
				new Color(app.accentEndRgb())
		));
		graphics.fillRoundRect(0, 0, 128, 128, 28, 28);
		graphics.setColor(Color.WHITE);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
		var metrics = graphics.getFontMetrics();
		String text = app.title().length() > 3 ? app.title().substring(0, 3) : app.title();
		int textX = (128 - metrics.stringWidth(text)) / 2;
		int textY = (128 - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(text, textX, textY);
		graphics.dispose();
		return image;
	}

	private static void drawMediaPlaceholder(Graphics2D graphics, UiRect rect) {
		int pad = Math.max(8, rect.width() / 14);
		UiRect horizon = new UiRect(rect.x() + pad, rect.bottom() - pad - Math.max(14, rect.height() / 6), rect.width() - pad * 2, Math.max(14, rect.height() / 6));
		fillRoundedRect(graphics, horizon, clampInt(pad, 6, 16), new Color(188, 212, 236, 255));
		graphics.setColor(new Color(82, 138, 196, 255));
		graphics.fillPolygon(
				new int[]{
						rect.x() + pad,
						rect.x() + rect.width() / 3,
						rect.x() + rect.width() / 2,
						rect.x() + rect.width() * 3 / 4,
						rect.right() - pad
				},
				new int[]{
						horizon.y() + horizon.height(),
						rect.y() + rect.height() / 2,
						rect.y() + rect.height() / 3,
						rect.y() + rect.height() / 2,
						horizon.y() + horizon.height()
				},
				5
		);
		int sunSize = Math.max(10, rect.width() / 8);
		graphics.setColor(new Color(255, 220, 112, 255));
		graphics.fillOval(rect.right() - pad - sunSize * 2, rect.y() + pad, sunSize, sunSize);
	}

	private static void drawMediaControls(Graphics2D graphics, UiRect rect, UiLayout layout) {
		int centerY = rect.y() + rect.height() / 2;
		int left = rect.x() + layout.unit();
		int right = rect.right() - layout.unit();
		int barHeight = clampInt(layout.unit() - 6, 4, 10);
		int playSize = clampInt(layout.unit() + 4, 12, 20);
		int playX = rect.x() + rect.width() / 2 - playSize / 2;
		int playY = centerY - playSize / 2;

		fillRoundedRect(graphics, new UiRect(left, centerY - barHeight / 2, Math.max(12, playX - left - layout.unit()), barHeight), 6, new Color(18, 22, 28, 92));
		fillRoundedRect(graphics, new UiRect(playX + playSize + layout.unit(), centerY - barHeight / 2, Math.max(12, right - playX - playSize - layout.unit()), barHeight), 6, new Color(18, 22, 28, 92));
		fillRoundedRect(graphics, new UiRect(playX, playY, playSize, playSize), clampInt(playSize / 2, 8, 12), new Color(18, 22, 28, 214));

		graphics.setColor(new Color(255, 255, 255, 240));
		int triPad = Math.max(3, playSize / 4);
		graphics.fillPolygon(
				new int[]{playX + triPad, playX + triPad, playX + playSize - triPad},
				new int[]{playY + triPad, playY + playSize - triPad, playY + playSize / 2},
				3
		);
	}

	private static void drawLauncherArrowButton(Graphics2D graphics, UiRect rect, boolean up) {
		fillRoundedRect(graphics, rect, clampInt(rect.width() / 2, 8, 12), new Color(248, 251, 255, 240));
		graphics.setColor(new Color(18, 22, 28));
		int pad = Math.max(4, rect.width() / 4);
		if (up) {
			graphics.fillPolygon(
					new int[]{rect.x() + rect.width() / 2, rect.x() + pad, rect.right() - pad},
					new int[]{rect.y() + pad, rect.bottom() - pad, rect.bottom() - pad},
					3
			);
		} else {
			graphics.fillPolygon(
					new int[]{rect.x() + pad, rect.right() - pad, rect.x() + rect.width() / 2},
					new int[]{rect.y() + pad, rect.y() + pad, rect.bottom() - pad},
					3
			);
		}
	}

	private static void drawBackArrow(Graphics2D graphics, UiRect rect, Color color) {
		int pad = Math.max(4, rect.width() / 4);
		int midY = rect.y() + rect.height() / 2;
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2.4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawLine(rect.right() - pad, midY, rect.x() + pad, midY);
		graphics.drawLine(rect.x() + pad, midY, rect.x() + pad + pad, rect.y() + pad);
		graphics.drawLine(rect.x() + pad, midY, rect.x() + pad + pad, rect.bottom() - pad);
	}

	private static UiLayout createUiLayout(int width, int height) {
		int canvasWidth = width * MAP_SIZE;
		int canvasHeight = height * MAP_SIZE;
		int viewportWidth = Math.min(width, MAX_UI_TILES) * MAP_SIZE;
		int viewportHeight = Math.min(height, MAX_UI_TILES) * MAP_SIZE;
		int viewportX = (canvasWidth - viewportWidth) / 2;
		int viewportY = (canvasHeight - viewportHeight) / 2;
		int margin = clampInt(Math.round(Math.min(viewportWidth, viewportHeight) * 0.065F), 6, 18);
		int unit = clampInt(Math.round(Math.min(viewportWidth, viewportHeight) / 15.5F), 7, 16);
		return new UiLayout(canvasWidth, canvasHeight, viewportX, viewportY, viewportWidth, viewportHeight, margin, unit);
	}

	private static UiRect workspaceRect(UiLayout layout) {
		return new UiRect(
				layout.viewportX() + layout.margin() / 2,
				layout.viewportY() + layout.margin() / 2,
				layout.viewportWidth() - layout.margin(),
				layout.viewportHeight() - layout.margin()
		);
	}

	private static UiRect homePanelRect(UiLayout layout) {
		return new UiRect(
				layout.viewportX() + layout.margin() / 3,
				layout.viewportY() + layout.margin() / 3,
				layout.viewportWidth() - (layout.margin() * 2) / 3,
				layout.viewportHeight() - (layout.margin() * 2) / 3
		);
	}

	private static UiRect homeHeaderRect(UiLayout layout, UiRect panel) {
		int width = clampInt(panel.width() / 2, 52, Math.max(52, panel.width() - layout.unit() * 2));
		return new UiRect(
				panel.x() + (panel.width() - width) / 2,
				panel.y() + layout.unit() / 2,
				width,
				homeHeaderHeight(layout)
		);
	}

	private static UiRect homeFooterRect(UiLayout layout, UiRect panel) {
		int height = clampInt(layout.unit() * 2, 18, 28);
		int width = clampInt(panel.width() / 2, 52, Math.max(52, panel.width() - layout.unit() * 2));
		return new UiRect(
				panel.x() + (panel.width() - width) / 2,
				panel.bottom() - height - layout.unit() / 2,
				width,
				height
		);
	}

	private static UiRect homeAppCardRect(UiLayout layout, int launcherPage, int slotIndex) {
		UiRect panel = homePanelRect(layout);
		int row = slotIndex / LAUNCHER_COLUMNS;
		int column = slotIndex % LAUNCHER_COLUMNS;
		int gap = homeAppGap(layout);
		int cardsWidth = LAUNCHER_COLUMNS * homeAppCardWidth(layout) + (LAUNCHER_COLUMNS - 1) * gap;
		int cardsHeight = homeRowsPerPage(layout) * homeAppCardHeight(layout) + Math.max(0, homeRowsPerPage(layout) - 1) * gap;
		int startX = panel.x() + (panel.width() - cardsWidth) / 2;
		int contentTop = homeHeaderRect(layout, panel).bottom() + clampInt(layout.unit(), 6, 12);
		int contentBottom = homeFooterRect(layout, panel).y() - clampInt(layout.unit(), 6, 12);
		int startY = contentTop + Math.max(0, contentBottom - contentTop - cardsHeight) / 2;
		return new UiRect(
				startX + column * (homeAppCardWidth(layout) + gap),
				startY + row * (homeAppCardHeight(layout) + gap),
				homeAppCardWidth(layout),
				homeAppCardHeight(layout)
		);
	}

	private static UiRect homeAppIconRect(UiRect cardRect, UiLayout layout) {
		int size = homeAppIconSize(layout);
		return new UiRect(
				cardRect.x() + (cardRect.width() - size) / 2,
				cardRect.y() + layout.unit() / 2,
				size,
				size
		);
	}

	private static UiRect homeAppLabelRect(UiLayout layout, UiRect cardRect) {
		int labelHeight = clampInt(layout.unit() + 2, 11, 18);
		return new UiRect(
				cardRect.x() + clampInt(layout.unit() / 2, 4, 8),
				cardRect.bottom() - labelHeight - clampInt(layout.unit() / 2, 4, 8),
				cardRect.width() - clampInt(layout.unit(), 8, 14),
				labelHeight
		);
	}

	private static UiRect homeScrollUpRect(UiLayout layout) {
		if (homePageCount(layout) <= 1) {
			return null;
		}
		UiRect footer = homeFooterRect(layout, homePanelRect(layout));
		int size = Math.max(footer.height() - 6, 12);
		return new UiRect(footer.x() - size - 4, footer.y() + (footer.height() - size) / 2, size, size);
	}

	private static UiRect homeScrollDownRect(UiLayout layout) {
		if (homePageCount(layout) <= 1) {
			return null;
		}
		UiRect footer = homeFooterRect(layout, homePanelRect(layout));
		int size = Math.max(footer.height() - 6, 12);
		return new UiRect(footer.right() + 4, footer.y() + (footer.height() - size) / 2, size, size);
	}

	private static UiRect mediaHeaderRect(UiLayout layout) {
		UiRect workspace = workspaceRect(layout);
		return new UiRect(
				workspace.x() + layout.unit() / 2,
				workspace.y() + layout.unit() / 2,
				workspace.width() - layout.unit(),
				clampInt(layout.unit() * 2, 22, 36)
		);
	}

	private static UiRect genericCloseRect(UiLayout layout) {
		UiRect header = mediaHeaderRect(layout);
		int size = clampInt(layout.unit() + 8, 18, 28);
		return new UiRect(
				header.x() + layout.unit() / 2,
				header.y() + (header.height() - size) / 2,
				size,
				size
		);
	}

	private static UiRect mediaCanvasRect(UiLayout layout) {
		int marginX = clampInt(layout.canvasWidth() / 48, 4, 10);
		int marginY = clampInt(layout.canvasHeight() / 48, 4, 10);
		return new UiRect(
				marginX,
				marginY,
				Math.max(16, layout.canvasWidth() - marginX * 2),
				Math.max(16, layout.canvasHeight() - marginY * 2)
		);
	}

	private static UiRect mediaCloseRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int size = clampInt(layout.unit() * 2, 18, 28);
		return new UiRect(canvas.x() + layout.unit() / 2, canvas.y() + layout.unit() / 2, size, size);
	}

	private static UiRect mediaOverlayToggleRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int width = clampInt(layout.unit() * 4, 34, 58);
		int height = clampInt(layout.unit() * 2, 18, 28);
		return new UiRect(canvas.right() - width - layout.unit() / 2, canvas.y() + layout.unit() / 2, width, height);
	}

	private static UiRect mediaLinkRect(UiLayout layout) {
		return mediaLinkRect(layout, true);
	}

	private static UiRect mediaLinkRect(UiLayout layout, boolean hasMedia) {
		UiRect canvas = mediaCanvasRect(layout);
		if (!hasMedia) {
			int width = clampInt(canvas.width() * 2 / 3, 86, canvas.width() - layout.unit() * 4);
			int height = clampInt(layout.unit() * 3, 28, 44);
			return new UiRect(
					canvas.x() + (canvas.width() - width) / 2,
					canvas.y() + (canvas.height() - height) / 2,
					width,
					height
			);
		}
		UiRect closeRect = mediaCloseRect(layout);
		int height = clampInt(layout.unit() * 2, 18, 28);
		int x = closeRect.right() + clampInt(layout.unit() / 2, 5, 10);
		int width = Math.max(48, canvas.right() - x - layout.unit() / 2);
		return new UiRect(x, canvas.y() + layout.unit() / 2, width, height);
	}

	private static UiRect mediaScaleRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int size = clampInt(layout.unit() * 2, 18, 28);
		return new UiRect(canvas.right() - size - layout.unit() / 2, canvas.bottom() - size - layout.unit() / 2, size, size);
	}

	private static UiRect mediaTimelineRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		UiRect scaleRect = mediaScaleRect(layout);
		int left = canvas.x() + layout.unit() / 2;
		int right = scaleRect.x() - clampInt(layout.unit() / 2, 4, 8);
		int height = clampInt(layout.unit() * 2, 18, 28);
		return new UiRect(
				left,
				canvas.bottom() - height - layout.unit() / 2,
				Math.max(44, right - left),
				height
		);
	}

	private static UiRect mediaPlayPauseRect(UiLayout layout) {
		UiRect timeline = mediaTimelineRect(layout);
		int size = Math.max(12, timeline.height() - 4);
		return new UiRect(timeline.x() + 2, timeline.y() + (timeline.height() - size) / 2, size, size);
	}

	private static UiRect mediaTimelineCounterRect(UiLayout layout) {
		UiRect timeline = mediaTimelineRect(layout);
		int width = clampInt(timeline.width() / 5, 26, 52);
		return new UiRect(timeline.right() - width - layout.unit() / 2, timeline.y(), width, timeline.height());
	}

	private static UiRect mediaTimelineTrackRect(UiLayout layout) {
		UiRect timeline = mediaTimelineRect(layout);
		UiRect playPause = mediaPlayPauseRect(layout);
		UiRect counter = mediaTimelineCounterRect(layout);
		int x = playPause.right() + clampInt(layout.unit() / 2, 4, 8);
		int right = counter.x() - clampInt(layout.unit() / 2, 4, 8);
		int height = clampInt(layout.unit() / 2, 6, 10);
		return new UiRect(
				x,
				timeline.y() + timeline.height() / 2 - height / 2,
				Math.max(12, right - x),
				height
		);
	}

	private static UiRect mediaStatusRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int width = clampInt(canvas.width() * 2 / 3, 72, canvas.width() - layout.unit() * 4);
		int height = clampInt(layout.unit() * 2, 18, 30);
		return new UiRect(canvas.x() + (canvas.width() - width) / 2, canvas.y() + canvas.height() / 2 - height / 2, width, height);
	}

	private static UiRect mediaProgressRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int width = clampInt(canvas.width() * 2 / 3, 84, canvas.width() - layout.unit() * 4);
		int height = clampInt(layout.unit() * 4, 34, 54);
		return new UiRect(canvas.x() + (canvas.width() - width) / 2, canvas.bottom() - height - layout.unit(), width, height);
	}

	private static UiRect mediaPromptRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int width = clampInt(canvas.width() - layout.unit() * 3, 60, canvas.width());
		int height = clampInt(layout.unit() * 5, 42, 76);
		return new UiRect(canvas.x() + (canvas.width() - width) / 2, canvas.y() + (canvas.height() - height) / 2 - layout.unit(), width, height);
	}

	private static UiRect genericAppHeroRect(UiLayout layout) {
		UiRect workspace = workspaceRect(layout);
		UiRect header = mediaHeaderRect(layout);
		return new UiRect(
				workspace.x() + layout.unit(),
				header.bottom() + layout.unit(),
				workspace.width() - layout.unit() * 2,
				workspace.bottom() - header.bottom() - layout.unit() * 2
		);
	}

	private static int homeHeaderHeight(UiLayout layout) {
		return clampInt(layout.unit() + 2, 11, 18);
	}

	private static int homeRowsPerPage(UiLayout layout) {
		return layout.viewportHeight() >= 180 ? 2 : 1;
	}

	private static int homeAppGap(UiLayout layout) {
		return clampInt(layout.unit() - 2, 5, 10);
	}

	private static int homeAppIconSize(UiLayout layout) {
		return clampInt(Math.round(Math.min(layout.viewportWidth(), layout.viewportHeight()) * 0.19F), 20, 40);
	}

	private static int homeAppCardWidth(UiLayout layout) {
		return clampInt(homeAppIconSize(layout) + layout.unit() + 4, 36, 66);
	}

	private static int homeAppCardHeight(UiLayout layout) {
		return homeAppCardWidth(layout) + clampInt(layout.unit() + 2, 11, 18) + clampInt(layout.unit() / 2, 4, 8);
	}

	private static int homePageCapacity(UiLayout layout) {
		return Math.max(1, homeRowsPerPage(layout) * LAUNCHER_COLUMNS);
	}

	private static int homePageCount(UiLayout layout) {
		int capacity = homePageCapacity(layout);
		int totalApps = MonitorAppRegistry.apps().size();
		return Math.max(1, (totalApps + capacity - 1) / capacity);
	}

	private static List<MonitorApp> visibleHomeApps(UiLayout layout, int launcherPage) {
		List<MonitorApp> apps = MonitorAppRegistry.apps();
		int page = clampInt(launcherPage, 0, Math.max(0, homePageCount(layout) - 1));
		int fromIndex = page * homePageCapacity(layout);
		int toIndex = Math.min(apps.size(), fromIndex + homePageCapacity(layout));
		return apps.subList(fromIndex, toIndex);
	}

	private static MonitorApp appForViewMode(ScreenViewMode mode) {
		if (mode == null || mode == ScreenViewMode.HOME) {
			return null;
		}
		return MonitorAppRegistry.findById(mode.serializedName());
	}

	private static void fillRoundedRect(Graphics2D graphics, UiRect rect, int arc, Color color) {
		if (color != null) {
			graphics.setColor(color);
		}
		graphics.fillRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), arc, arc);
	}

	private static void strokeRoundedRect(Graphics2D graphics, UiRect rect, int arc, float width, Color color) {
		Stroke previous = graphics.getStroke();
		graphics.setStroke(new BasicStroke(width));
		graphics.setColor(color);
		graphics.drawRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), arc, arc);
		graphics.setStroke(previous);
	}

	private static void drawCenteredText(Graphics2D graphics, String text, UiRect rect, Color color, int style, int size) {
		graphics.setColor(color);
		graphics.setFont(new Font(Font.SANS_SERIF, style, size));
		var metrics = graphics.getFontMetrics();
		int textX = rect.x() + (rect.width() - metrics.stringWidth(text)) / 2;
		int textY = rect.y() + (rect.height() - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(text, textX, textY);
	}

	private static void drawVerticalText(Graphics2D graphics, String text, UiRect rect, Color color, int style, int size) {
		graphics.setColor(color);
		graphics.setFont(new Font(Font.SANS_SERIF, style, size));
		var metrics = graphics.getFontMetrics();
		int textY = rect.y() + (rect.height() - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(text, rect.x(), textY);
	}

	private static UiPoint screenTouchPoint(ItemFrame frame, Vec3 hitLocation, TileCoord tileCoord, int gridWidth, int gridHeight) {
		if (frame == null || hitLocation == null || tileCoord == null) {
			return null;
		}

		double localX = hitLocation.x - frame.blockPosition().getX();
		double localY = hitLocation.y - frame.blockPosition().getY();
		double localZ = hitLocation.z - frame.blockPosition().getZ();
		if (localY < -TOUCH_TOLERANCE || localY > 1.0D + TOUCH_TOLERANCE) {
			return null;
		}

		double u = switch (frame.getDirection()) {
			case SOUTH -> localX;
			case NORTH -> 1.0D - localX;
			case EAST -> 1.0D - localZ;
			case WEST -> localZ;
			default -> Double.NaN;
		};
		if (!Double.isFinite(u) || u < -TOUCH_TOLERANCE || u > 1.0D + TOUCH_TOLERANCE) {
			return null;
		}

		double v = 1.0D - localY;
		int pixelX = tileCoord.x() * MAP_SIZE + clampInt((int) Math.floor(clampDouble(u, 0.0D, 1.0D) * (MAP_SIZE - 1)), 0, MAP_SIZE - 1);
		int pixelY = tileCoord.y() * MAP_SIZE + clampInt((int) Math.floor(clampDouble(v, 0.0D, 1.0D) * (MAP_SIZE - 1)), 0, MAP_SIZE - 1);
		int maxX = Math.max(0, gridWidth * MAP_SIZE - 1);
		int maxY = Math.max(0, gridHeight * MAP_SIZE - 1);
		return new UiPoint(clampInt(pixelX, 0, maxX), clampInt(pixelY, 0, maxY));
	}

	private static BufferedImage loadOffBaseImage() {
		if (offBaseImage == null) {
			offBaseImage = loadBaseImage(SCREEN_OFF_RESOURCE);
		}
		return offBaseImage;
	}

	private static BufferedImage loadOnBaseImage() {
		if (onBaseImage == null) {
			onBaseImage = loadBaseImage(SCREEN_ON_RESOURCE);
		}
		return onBaseImage;
	}

	private static BufferedImage loadBaseImage(String resourcePath) {
		boolean powered = SCREEN_ON_RESOURCE.equals(resourcePath);
		try (InputStream inputStream = MonitorScreenSystem.class.getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				return fallbackImage(powered);
			}
			BufferedImage image = ImageIO.read(inputStream);
			return image != null ? image : fallbackImage(powered);
		} catch (IOException ignored) {
			return fallbackImage(powered);
		}
	}

	private static BufferedImage loadPngImage(String resourcePath, BufferedImage fallback) {
		try (InputStream inputStream = MonitorScreenSystem.class.getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				return fallback;
			}
			BufferedImage image = ImageIO.read(inputStream);
			return image != null ? image : fallback;
		} catch (IOException ignored) {
			return fallback;
		}
	}

	private static BufferedImage fallbackImage(boolean powered) {
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		int base = powered ? 0xF2F5F8 : 0x040404;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int band = ((y / 4) & 1) == 0 ? 4 : -4;
				int vignette = Math.max(0, 18 - Math.abs(128 - x) / 6 - Math.abs(128 - y) / 6);
				int rgb = adjustBrightness(base, powered ? vignette - band : -vignette + band);
				image.setRGB(x, y, 0xFF000000 | rgb);
			}
		}
		return image;
	}

	private static int adjustBrightness(int rgb, int delta) {
		int red = clampInt(((rgb >> 16) & 0xFF) + delta, 0, 255);
		int green = clampInt(((rgb >> 8) & 0xFF) + delta, 0, 255);
		int blue = clampInt((rgb & 0xFF) + delta, 0, 255);
		return (red << 16) | (green << 8) | blue;
	}

	private static void applyFrameToMap(MapItemSavedData mapData, byte[] frame) {
		if (mapData == null || frame == null || frame.length < MAP_SIZE * MAP_SIZE || mapData.colors == null || mapData.colors.length < MAP_SIZE * MAP_SIZE) {
			return;
		}
		System.arraycopy(frame, 0, mapData.colors, 0, MAP_SIZE * MAP_SIZE);
		mapData.setDirty();
	}

	private static void applyRenderedTiles(ServerLevel level, ScreenComponent component, byte[][] renderedTiles) {
		if (level == null || component == null || renderedTiles == null) {
			return;
		}
		ServerLevel mapStorageLevel = photoMapLevel(level.getServer(), level);
		List<MapPacketUpdate> changedUpdates = new ArrayList<>();
		for (Map.Entry<ItemFrame, TileCoord> entry : component.frameCoords().entrySet()) {
			ItemFrame frame = entry.getKey();
			ItemStack frameStack = frame.getItem();
			ScreenTileState state = readScreenState(frameStack);
			MapId mapId = frameStack.get(DataComponents.MAP_ID);
			if (state == null || mapId == null) {
				continue;
			}
			MapItemSavedData mapData = mapStorageLevel.getMapData(mapId);
			if (mapData == null) {
				continue;
			}
			int tileIndex = state.tileY() * component.width() + state.tileX();
			if (tileIndex < 0 || tileIndex >= renderedTiles.length) {
				continue;
			}
			byte[] tileFrame = renderedTiles[tileIndex];
			if (!hasRenderedMapChanged(mapId, tileFrame)) {
				continue;
			}
			applyFrameToMap(mapData, tileFrame);
			changedUpdates.add(new MapPacketUpdate(mapId, mapData.scale, mapData.locked, tileFrame.clone()));
		}
		sendMapToPlayers(level, component, changedUpdates);
	}

	private static boolean hasRenderedMapChanged(MapId mapId, byte[] tileFrame) {
		if (mapId == null || tileFrame == null || tileFrame.length < MAP_SIZE * MAP_SIZE) {
			return false;
		}
		byte[] previous = LAST_RENDERED_MAP_FRAMES.get(mapId.id());
		if (previous != null && Arrays.equals(previous, tileFrame)) {
			return false;
		}
		LAST_RENDERED_MAP_FRAMES.put(mapId.id(), tileFrame.clone());
		return true;
	}

	private static void forgetRenderedMapFrame(ItemStack stack) {
		if (stack == null) {
			return;
		}
		MapId mapId = stack.get(DataComponents.MAP_ID);
		if (mapId != null) {
			LAST_RENDERED_MAP_FRAMES.remove(mapId.id());
		}
	}

	private static void sendMapToPlayers(ServerLevel level, ScreenComponent component, List<MapPacketUpdate> changedUpdates) {
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
					new MapItemSavedData.MapPatch(0, 0, MAP_SIZE, MAP_SIZE, update.frame())
			);
			for (ServerPlayer player : recipients) {
				player.connection.send(packet);
			}
		}
	}

	private static List<ServerPlayer> collectMapRecipients(ServerLevel level, ScreenComponent component) {
		if (level == null || component == null) {
			return List.of();
		}
		if (!isPlayerMode(component.viewMode())) {
			return new ArrayList<>(level.players());
		}
		double radiusBlocks = monitorMapUpdateRadiusBlocks();
		double radiusSquared = radiusBlocks * radiusBlocks;
		List<ServerPlayer> recipients = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (isPlayerNearScreenComponent(player, component, radiusSquared)) {
				recipients.add(player);
			}
		}
		return recipients;
	}

	private static long effectiveYoutubePollDelayMs(MinecraftServer server, ScreenRuntimeKey key, boolean paused) {
		long baseDelay = paused ? youtubePollIdleIntervalMs() : youtubePollActiveIntervalMs();
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component == null) {
			return baseDelay;
		}

		int tileCount = Math.max(1, component.width() * component.height());
		double tileScale = 1.0D + Math.max(0.0D, Math.sqrt(tileCount) - 1.0D) * 1.25D;
		long scaledDelay = Math.round(baseDelay * tileScale);
		if (!hasNearbyMediaViewer(server.getLevel(key.dimension()), component)) {
			scaledDelay = Math.max(scaledDelay, paused ? youtubePollIdleIntervalMs() * 2L : youtubePollIdleIntervalMs());
		}
		long maxDelay = paused ? 3_000L : 1_200L;
		return Math.max(baseDelay, Math.min(maxDelay, scaledDelay));
	}

	private static long effectiveYoutubeUiRefreshThresholdMs(MinecraftServer server, ScreenRuntimeKey key) {
		return Math.max(250L, effectiveYoutubePollDelayMs(server, key, false) * 2L);
	}

	private static ScreenComponent resolveScreenComponent(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return null;
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) {
			return null;
		}
		ItemFrame rootFrame = findScreenFrame(level, key.pos(), key.facing());
		if (rootFrame == null) {
			return null;
		}
		return collectComponent(level, rootFrame, null);
	}

	private static boolean hasNearbyMediaViewer(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return false;
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) {
			return false;
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		return hasNearbyMediaViewer(level, component);
	}

	private static boolean hasNearbyMediaViewer(ServerLevel level, ScreenComponent component) {
		if (level == null || component == null) {
			return false;
		}
		double radiusBlocks = monitorMapUpdateRadiusBlocks();
		double radiusSquared = radiusBlocks * radiusBlocks;
		for (ServerPlayer player : level.players()) {
			if (isPlayerNearScreenComponent(player, component, radiusSquared)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPlayerNearScreenComponent(ServerPlayer player, ScreenComponent component, double radiusSquared) {
		if (player == null || component == null) {
			return false;
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			if (frame != null && player.distanceToSqr(frame) <= radiusSquared) {
				return true;
			}
		}
		return false;
	}

	private static void ensureDisplay(ServerLevel level, ItemFrame frame, int connectionMask) {
		List<Display.ItemDisplay> displays = findDisplays(level, frame.blockPosition(), frame.getDirection());
		Display.ItemDisplay display;
		if (displays.isEmpty()) {
			display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
			display.addTag(DISPLAY_ROOT_TAG);
			display.addTag(positionTag(frame.blockPosition()));
			display.addTag(facingTag(frame.getDirection()));
			level.addFreshEntity(display);
		} else {
			display = displays.get(0);
			for (int i = 1; i < displays.size(); i++) {
				displays.get(i).discard();
			}
		}

		Direction facing = frame.getDirection();
		Direction mountSide = facing.getOpposite();
		Vec3 center = Vec3.atCenterOf(frame.blockPosition()).add(
				mountSide.getStepX() * DISPLAY_PLANE_OFFSET,
				mountSide.getStepY() * DISPLAY_PLANE_OFFSET,
				mountSide.getStepZ() * DISPLAY_PLANE_OFFSET
		);
		float yRot = facing.toYRot();
		display.setPos(center.x, center.y, center.z);
		display.setYRot(yRot);
		display.setXRot(0.0F);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setItemStack(MonitorItem.createDisplayStack(connectionMask));
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setTransformation(Transformation.identity());
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(1.0F);
	}

	private static void cleanupOrphanDisplays(ServerLevel level) {
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof Display.ItemDisplay display) || !display.getTags().contains(DISPLAY_ROOT_TAG)) {
				continue;
			}
			BlockPos pos = parsePositionTag(display.getTags());
			Direction facing = parseFacingTag(display.getTags());
			if (pos == null || facing == null || findScreenFrame(level, pos, facing) == null) {
				display.discard();
			}
		}
	}

	private static void removeDisplays(ServerLevel level, BlockPos pos, Direction facing) {
		for (Display.ItemDisplay display : findDisplays(level, pos, facing)) {
			display.discard();
		}
	}

	private static List<Display.ItemDisplay> findDisplays(ServerLevel level, BlockPos pos, Direction facing) {
		AABB box = new AABB(pos).inflate(DISPLAY_SEARCH_RADIUS);
		String posTag = positionTag(pos);
		String facingTag = facingTag(facing);
		return level.getEntities(
				EntityType.ITEM_DISPLAY,
				box,
				display -> display.getTags().contains(DISPLAY_ROOT_TAG)
						&& display.getTags().contains(posTag)
						&& display.getTags().contains(facingTag)
		);
	}

	private static ItemFrame findAnyFrame(ServerLevel level, BlockPos pos, Direction facing) {
		AABB box = new AABB(pos).inflate(0.6D);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box, candidate -> candidate.blockPosition().equals(pos))) {
			if (frame.getDirection() == facing) {
				return frame;
			}
		}
		return null;
	}

	private static ItemFrame findScreenFrame(ServerLevel level, BlockPos pos, Direction facing) {
		AABB box = new AABB(pos).inflate(0.6D);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box, candidate -> candidate.blockPosition().equals(pos))) {
			if (facing != null && frame.getDirection() != facing) {
				continue;
			}
			if (readScreenState(frame.getItem()) != null) {
				return frame;
			}
		}
		return null;
	}

	private static Direction frameRight(Direction facing) {
		return switch (facing) {
			case NORTH -> Direction.WEST;
			case SOUTH -> Direction.EAST;
			case EAST -> Direction.NORTH;
			case WEST -> Direction.SOUTH;
			default -> Direction.EAST;
		};
	}

	private static ServerLevel photoMapLevel(MinecraftServer server, ServerLevel fallback) {
		if (server == null) {
			return fallback;
		}
		ServerLevel end = server.getLevel(Level.END);
		if (end != null) {
			return end;
		}
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		return overworld != null ? overworld : fallback;
	}

	private static String positionTag(BlockPos pos) {
		return POS_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String facingTag(Direction facing) {
		return FACING_TAG_PREFIX + facing.getName();
	}

	private static BlockPos parsePositionTag(Set<String> tags) {
		for (String tag : tags) {
			if (!tag.startsWith(POS_TAG_PREFIX)) {
				continue;
			}
			String raw = tag.substring(POS_TAG_PREFIX.length());
			String[] parts = raw.split(",");
			if (parts.length != 3) {
				continue;
			}
			try {
				return new BlockPos(
						Integer.parseInt(parts[0]),
						Integer.parseInt(parts[1]),
						Integer.parseInt(parts[2])
				);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static Direction parseFacingTag(Set<String> tags) {
		for (String tag : tags) {
			if (tag.startsWith(FACING_TAG_PREFIX)) {
				return Direction.byName(tag.substring(FACING_TAG_PREFIX.length()));
			}
		}
		return null;
	}

	private static String linkPromptStatus(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return "ВСТАВЬ YOUTUBE В ЧАТ";
		}
		return "ВСТАВЬ ССЫЛКУ В ЧАТ";
	}

	private static boolean isMediaPromptStatus(String status) {
		if (status == null) {
			return false;
		}
		return "ВСТАВЬ ССЫЛКУ В ЧАТ".equalsIgnoreCase(status)
				|| "ВСТАВЬ YOUTUBE В ЧАТ".equalsIgnoreCase(status)
				|| "ССЫЛКА В ЧАТ".equalsIgnoreCase(status)
				|| "SEND LINK IN CHAT".equalsIgnoreCase(status);
	}

	private static boolean isMediaLoadingStatus(String status) {
		if (status == null) {
			return false;
		}
		return "ЗАГРУЖАЮ...".equalsIgnoreCase(status)
				|| "ПОДКЛЮЧАЮ YOUTUBE...".equalsIgnoreCase(status)
				|| "LOADING...".equalsIgnoreCase(status);
	}

	private static String loadingStatus(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return "ПОДКЛЮЧАЮ YOUTUBE...";
		}
		return "ЗАГРУЖАЮ...";
	}

	private static String localizedProgressStage(String stage) {
		if (stage == null || stage.isBlank()) {
			return "ЗАГРУЗКА";
		}
		return switch (stage.toUpperCase(Locale.ROOT)) {
			case "CONNECTING" -> "ПОДКЛЮЧЕНИЕ";
			case "DOWNLOADING", "LOADING" -> "ЗАГРУЗКА";
			case "DECODING" -> "ОБРАБОТКА";
			case "READY" -> "ГОТОВО";
			case "RENDER ERROR" -> "ОШИБКА РЕНДЕРА";
			case "LOAD FAILED" -> "ОШИБКА ЗАГРУЗКИ";
			default -> stage;
		};
	}

	private static String sanitizeMediaError(String error) {
		if (error == null || error.isBlank()) {
			return "LOAD FAILED";
		}
		String normalized = error.trim().replace('\n', ' ').replace('\r', ' ');
		if (normalized.length() > 28) {
			normalized = normalized.substring(0, 28).trim();
		}
		return normalized.toUpperCase(Locale.ROOT);
	}

	private static Component linkPromptMessage(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Скинь в чат YouTube ссылку");
		}
		return literal("Скинь в чат ссылку на картинку или гифку");
	}

	private static Component loadingMessage(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Подключаю YouTube...");
		}
		return literal("Загружаю медиа...");
	}

	private static Component mediaLoadedMessage(ServerPlayer player, boolean animated) {
		return literal(animated ? "Гифка загружена" : "Картинка загружена");
	}

	private static Component youtubeLoadedMessage(ServerPlayer player, boolean live) {
		return literal(live ? "YouTube стрим подключён" : "YouTube видео подключено");
	}

	private static Component mediaLoadFailedMessage(ServerPlayer player, String error) {
		String reason = error == null || error.isBlank() ? "LOAD FAILED" : error;
		return literal("Не удалось загрузить: " + reason);
	}

	private static Component mediaCancelledMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Этот экран уже не ждёт YouTube ссылку");
		}
		return literal("Этот экран уже не ждёт ссылку");
	}

	private static Component mediaInvalidLinkMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Нужна нормальная YouTube ссылка");
		}
		return literal("Пустая ссылка не подходит");
	}

	private static Component wallOnlyMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Экранъ ставится токмо на стену");
		}
		if (locale.startsWith("uk")) {
			return literal("Екран ставиться лише на стіну");
		}
		if (locale.startsWith("ja")) {
			return literal("モニターは壁にのみ設置できます");
		}
		if (locale.startsWith("ru")) {
			return literal("Экран ставится только на стену");
		}
		return literal("The monitor can only be placed on a wall");
	}

	private static Component occupiedMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Тутъ уже занято");
		}
		if (locale.startsWith("uk")) {
			return literal("Тут уже зайнято");
		}
		if (locale.startsWith("ja")) {
			return literal("ここには設置できません");
		}
		if (locale.startsWith("ru")) {
			return literal("Тут уже занято");
		}
		return literal("That spot is already occupied");
	}

	private static String locale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "en_us";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	private static Component literal(String value) {
		return Component.literal(value).withStyle(style -> style.withItalic(false));
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Color withAlpha(int rgb, int alpha) {
		return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, clampInt(alpha, 0, 255));
	}

	private static Color withAlpha(Color color, float alpha) {
		if (color == null) {
			return null;
		}
		int nextAlpha = clampInt(Math.round(color.getAlpha() * Math.max(0.0F, Math.min(1.0F, alpha))), 0, 255);
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), nextAlpha);
	}

	private static float mediaTimelineFraction(UiLayout layout, UiPoint point) {
		UiRect trackRect = mediaTimelineTrackRect(layout);
		if (trackRect.width() <= 0) {
			return 0.0F;
		}
		return (float) clampDouble((point.x() - trackRect.x()) / (double) Math.max(1, trackRect.width()), 0.0D, 1.0D);
	}

	private static int mediaFrameIndexForFraction(MonitorMediaApp.LoadedMedia loadedMedia, float fraction) {
		if (loadedMedia == null || loadedMedia.frameCount() <= 1) {
			return 0;
		}
		return clampInt((int) Math.round(clampDouble(fraction, 0.0D, 1.0D) * (loadedMedia.frameCount() - 1)), 0, loadedMedia.frameCount() - 1);
	}

	private static boolean isPlayerMode(ScreenViewMode mode) {
		return mode == ScreenViewMode.MEDIA || mode == ScreenViewMode.YOUTUBE;
	}

	private static void clearLoadedContentLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.loadedMedia = null;
		state.streamFrame = null;
		state.youtubeFrameSequence = 0L;
		state.sourceUrl = null;
		state.relaySessionId = null;
		state.audioStreamUrl = null;
		state.mediaTitle = "";
		state.frameIndex = 0;
		state.positionMs = 0L;
		state.durationMs = 0L;
		state.liveStream = false;
		state.audioPlaceholder = true;
	}

	private static boolean hasDisplayableMediaLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (state.mode == ScreenViewMode.YOUTUBE) {
			return state.sourceUrl != null || state.streamFrame != null;
		}
		return state.loadedMedia != null;
	}

	private static boolean canSeekTimelineLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (state.mode == ScreenViewMode.YOUTUBE) {
			return state.durationMs > 0L && !state.liveStream;
		}
		return state.loadedMedia != null && state.loadedMedia.frameCount() > 1;
	}

	private static float youtubeTimelineFraction(MediaRuntimeState state) {
		if (state == null || state.durationMs <= 0L) {
			return 0.0F;
		}
		return (float) clampDouble((double) state.positionMs / (double) state.durationMs, 0.0D, 1.0D);
	}

	private static long youtubePositionForFraction(MediaRuntimeState state, float fraction) {
		if (state == null || state.durationMs <= 0L) {
			return 0L;
		}
		return clampLong(Math.round(clampDouble(fraction, 0.0D, 1.0D) * state.durationMs), 0L, state.durationMs);
	}

	private static String relaySessionId(ScreenRuntimeKey key) {
		if (key == null) {
			return "lostglade-unknown";
		}
		String dimension = key.dimension().identifier().toString().replace(':', '_').replace('/', '_');
		return dimension + "_" + key.pos().getX() + "_" + key.pos().getY() + "_" + key.pos().getZ() + "_" + key.facing().getName();
	}

	private static void scheduleYoutubeRefresh(MinecraftServer server, ScreenRuntimeKey key, long delayMillis) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureExecutors();
		synchronized (state) {
			if (state.mode != ScreenViewMode.YOUTUBE || state.relaySessionId == null || state.waitingForLink) {
				return;
			}
			cancelPlaybackLocked(state);
			state.playbackFuture = mediaScheduler.schedule(() -> refreshYoutubeSnapshot(server, key), Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
		}
	}

	private static long clampLong(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}

	private static String formatPlaybackTime(long millis) {
		long totalSeconds = Math.max(0L, millis / 1000L);
		long hours = totalSeconds / 3600L;
		long minutes = (totalSeconds % 3600L) / 60L;
		long seconds = totalSeconds % 60L;
		if (hours > 0L) {
			return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
		}
		return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
	}

	private static boolean isPlaybackPausedLocked(MediaRuntimeState state) {
		return state != null && state.userPaused;
	}

	private enum ScreenViewMode {
		HOME("home"),
		MEDIA("media"),
		MAX("max"),
		YOUTUBE("youtube");

		private final String serializedName;

		ScreenViewMode(String serializedName) {
			this.serializedName = serializedName;
		}

		String serializedName() {
			return this.serializedName;
		}

		static ScreenViewMode fromTag(String value) {
			for (ScreenViewMode mode : values()) {
				if (mode.serializedName.equalsIgnoreCase(value)) {
					return mode;
				}
			}
			return HOME;
		}
	}

	private enum MediaOverlayMode {
		VIEW,
		CONTROLS
	}

	private enum MediaScaleMode {
		FIT,
		FILL,
		STRETCH;

		MediaScaleMode next() {
			MediaScaleMode[] values = values();
			return values[(this.ordinal() + 1) % values.length];
		}
	}

	private record ScreenKey(BlockPos pos, Direction direction) {
	}

	private record ScreenRuntimeKey(ResourceKey<Level> dimension, BlockPos pos, Direction facing) {
	}

	private record TileCoord(int x, int y) {
	}

	private record UiPoint(int x, int y) {
	}

	private record PlacementSurfacePoint(double u, double v) {
	}

	private record UiRect(int x, int y, int width, int height) {
		boolean contains(int px, int py) {
			return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height;
		}

		int right() {
			return this.x + this.width;
		}

		int bottom() {
			return this.y + this.height;
		}

		UiRect inset(int amount) {
			int nextWidth = Math.max(1, this.width - amount * 2);
			int nextHeight = Math.max(1, this.height - amount * 2);
			return new UiRect(this.x + amount, this.y + amount, nextWidth, nextHeight);
		}
	}

	private record UiLayout(
			int canvasWidth,
			int canvasHeight,
			int viewportX,
			int viewportY,
			int viewportWidth,
			int viewportHeight,
			int margin,
			int unit
	) {
	}

	private record ScreenFrame(ItemFrame frame, ScreenTileState state) {
	}

	private record ScreenComponent(
			ScreenRuntimeKey runtimeKey,
			Direction facing,
			Direction right,
			int width,
			int height,
			boolean powered,
			ScreenViewMode viewMode,
			int launcherPage,
			Map<ItemFrame, TileCoord> frameCoords,
			Map<TileCoord, ScreenFrame> byCoord
	) {
	}

	private record RenderCacheKey(boolean powered, ScreenViewMode viewMode, int launcherPage, int width, int height) {
	}

	private record MediaVisualSnapshot(
			long version,
			BufferedImage frame,
			boolean hasMedia,
			boolean playbackControlsVisible,
			boolean timelineSeekable,
			int frameIndex,
			int frameCount,
			float timelineFraction,
			String timelineLabel,
			boolean paused,
			MediaOverlayMode overlayMode,
			MediaScaleMode scaleMode,
			String statusText,
			String linkPlaceholder,
			TaskProgress.Snapshot progress
	) {
	}

	private record RenderWork(
			ScreenRuntimeKey runtimeKey,
			boolean powered,
			ScreenViewMode viewMode,
			int launcherPage,
			int width,
			int height,
			long mediaVersion,
			MediaVisualSnapshot mediaSnapshot
	) {
	}

	private record MediaDispatchKey(
			boolean powered,
			ScreenViewMode viewMode,
			int launcherPage,
			int width,
			int height
	) {
	}

	private record PendingMediaLinkRequest(ScreenRuntimeKey screenKey, ScreenViewMode mode) {
	}

	private record PlayerMediaFocus(ScreenRuntimeKey screenKey, long expiresAtMillis) {
	}

	private record PlacementNeighbor(BlockPos pos, int connectionMask, double distance) {
		private PlacementNeighbor(BlockPos pos, int connectionMask) {
			this(pos, connectionMask, 0.0D);
		}
	}

	private record MediaLoadResult(
			ScreenRuntimeKey screenKey,
			UUID requesterUuid,
			String url,
			MonitorMediaApp.LoadedMedia loadedMedia,
			String error
	) {
	}

	private record YoutubeLoadResult(
			ScreenRuntimeKey screenKey,
			UUID requesterUuid,
			String url,
			MonitorYoutubeRelayClient.SessionLoadResponse loadResponse,
			String error
	) {
	}

	private record YoutubeSnapshotResult(
			ScreenRuntimeKey screenKey,
			MonitorYoutubeRelayClient.SessionSnapshot snapshot,
			String error
	) {
	}

	public record SpeakerAudioSource(
			String sourceKey,
			String relaySessionId,
			String audioStreamUrl,
			long positionMs,
			boolean paused,
			boolean liveStream
	) {
	}

	private record MapPacketUpdate(
			MapId mapId,
			byte scale,
			boolean locked,
			byte[] frame
	) {
	}

	private record ScreenTileState(
			int attachmentMask,
			int gridWidth,
			int gridHeight,
			int tileX,
			int tileY,
			int connectionMask,
			boolean powered,
			ScreenViewMode viewMode,
			int launcherPage,
			String groupId
	) {
		boolean sameRenderState(ScreenTileState other) {
			return other != null
					&& this.gridWidth == other.gridWidth
					&& this.gridHeight == other.gridHeight
					&& this.tileX == other.tileX
					&& this.tileY == other.tileY
					&& this.powered == other.powered
					&& this.viewMode == other.viewMode
					&& this.launcherPage == other.launcherPage;
		}
	}

	private static final class MediaRuntimeState {
		private ScreenViewMode mode;
		private MonitorMediaApp.LoadedMedia loadedMedia;
		private BufferedImage streamFrame;
		private String sourceUrl;
		private String relaySessionId;
		private String audioStreamUrl;
		private String mediaTitle;
		private int frameIndex;
		private long youtubeFrameSequence;
		private long positionMs;
		private long durationMs;
		private long version;
		private MediaOverlayMode overlayMode;
		private MediaScaleMode scaleMode;
		private boolean liveStream;
		private boolean audioPlaceholder;
		private boolean userPaused;
		private boolean waitingForLink;
		private boolean loading;
		private String statusText;
		private int activeRenderJobs;
		private boolean rerenderRequested;
		private MediaDispatchKey lastDispatchKey;
		private ScheduledFuture<?> playbackFuture;
		private long nextProgressRenderAtMillis;
		private final TaskProgress progress;

		private MediaRuntimeState(ScreenViewMode mode, Runnable progressListener) {
			this.mode = mode;
			this.overlayMode = MediaOverlayMode.CONTROLS;
			this.scaleMode = MediaScaleMode.FIT;
			this.liveStream = false;
			this.audioPlaceholder = true;
			this.userPaused = false;
			this.waitingForLink = false;
			this.loading = false;
			this.version = 0L;
			this.statusText = "";
			this.activeRenderJobs = 0;
			this.nextProgressRenderAtMillis = 0L;
			this.progress = new TaskProgress(progressListener);
		}

		private static MediaRuntimeState fresh(ScreenViewMode mode, String statusText, Runnable progressListener) {
			MediaRuntimeState state = new MediaRuntimeState(mode, progressListener);
			state.statusText = statusText;
			return state;
		}
	}
}
