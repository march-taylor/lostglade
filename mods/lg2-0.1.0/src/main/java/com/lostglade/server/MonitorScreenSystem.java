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
import net.minecraft.world.level.chunk.LevelChunk;
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
import java.util.concurrent.ConcurrentLinkedQueue;
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
	private static final String PERSISTED_MEDIA_ROOT_TAG = "lg2_monitor_media";
	private static final String PERSISTED_GALLERY_COUNT_TAG = "gallery_count";
	private static final String PERSISTED_GALLERY_ITEM_PREFIX = "gallery_item_";
	private static final String PERSISTED_GALLERY_KIND_TAG = "kind";
	private static final String PERSISTED_GALLERY_TITLE_TAG = "title";
	private static final String PERSISTED_GALLERY_URL_TAG = "url";
	private static final String PERSISTED_GALLERY_LOCAL_MEDIA_TAG = "local_media";
	private static final String PERSISTED_WALLPAPER_URL_TAG = "wallpaper_url";
	private static final String PERSISTED_WALLPAPER_SCALE_TAG = "wallpaper_scale";
	private static final String CACHE_ROOT_DIR_NAME = "cache";
	private static final String CACHE_NAMESPACE_DIR_NAME = "lg2-monitor";
	private static final String POS_TAG_PREFIX = "lg2_monitor_display_pos:";
	private static final String FACING_TAG_PREFIX = "lg2_monitor_display_facing:";
	private static final int MAP_SIZE = 128;
	private static final int PHOTO_MAP_CENTER = 30_000_000;
	private static final int CONNECTION_LEFT = 1;
	private static final int CONNECTION_RIGHT = 2;
	private static final int CONNECTION_UP = 4;
	private static final int CONNECTION_DOWN = 8;
	private static final int CONNECTION_ALL = CONNECTION_LEFT | CONNECTION_RIGHT | CONNECTION_UP | CONNECTION_DOWN;
	private static final double DISPLAY_SEARCH_RADIUS = 0.8D;
	private static final double DISPLAY_PLANE_OFFSET = 0.49D;
	private static final double TOUCH_TOLERANCE = 0.08D;
	private static final String SCREEN_OFF_RESOURCE = "/assets/lg2/textures/monitor/screen_off.png";
	private static final String SCREEN_ON_RESOURCE = "/assets/lg2/textures/monitor/screen_on.png";
	private static final long PROGRESS_RENDER_INTERVAL_MS = 300L;
	private static final int PROGRESS_FADE_RENDER_STEPS = 5;
	private static final long MEDIA_SCROLL_FOCUS_TIMEOUT_MS = 6000L;
	private static final double MEDIA_CONTROL_DISTANCE = 6.0D;
	private static final long YOUTUBE_SCROLL_SEEK_MS = 5000L;
	private static final int YOUTUBE_PRELOAD_PREVIOUS_COUNT = 4;
	private static final int YOUTUBE_PRELOAD_NEXT_COUNT = 8;
	private static final long MEDIA_ACTION_SPINNER_MIN_MILLIS = 150L;
	private static final Set<String> GALLERY_VIDEO_EXTENSIONS = Set.of(".mp4", ".m4v", ".mov", ".webm");
	private static final long MEDIA_ACTION_COMPLETE_VISIBLE_MILLIS = 1600L;
	private static final long YOUTUBE_FULLY_BUFFERED_TOLERANCE_MS = 1500L;
	private static final long WALLPAPER_IDLE_VISIBILITY_RECHECK_MS = 500L;
	private static final int MAX_SCREEN_SYNC_OPERATIONS_PER_TICK = 24;
	private static final int MAX_POWER_REFRESHES_PER_TICK = 16;
	private static final long MEDIA_SESSION_CLEANUP_INTERVAL_TICKS = 40L;
	private static final long MEDIA_ACTIONBAR_REFRESH_INTERVAL_TICKS = 20L;
	private static final long MEDIA_FOCUS_CLEANUP_INTERVAL_TICKS = 20L;
	private static final Map<RenderCacheKey, byte[][]> TILE_CACHE = new ConcurrentHashMap<>();
	private static final Map<OverlayWindowCacheKey, OverlayWindowRenderState> OVERLAY_WINDOW_CACHE = new ConcurrentHashMap<>();
	private static final Map<OverlayWindowFamilyKey, BufferedImage> OVERLAY_WINDOW_FAMILY_CACHE = new ConcurrentHashMap<>();
	private static final Map<OverlayWindowFamilyKey, BufferedImage> OVERLAY_WINDOW_PLACEHOLDER_CACHE = new ConcurrentHashMap<>();
	private static final Map<Integer, byte[]> LAST_RENDERED_MAP_FRAMES = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> APP_ICON_CACHE = new ConcurrentHashMap<>();
	private static final Map<PlayerUiIcon, BufferedImage> PLAYER_UI_ICON_CACHE = new ConcurrentHashMap<>();
	private static final Map<PlayerUiIconTintKey, BufferedImage> PLAYER_UI_ICON_TINT_CACHE = new ConcurrentHashMap<>();
	private static final Map<ScreenRuntimeKey, MediaRuntimeState> MEDIA_STATES = new ConcurrentHashMap<>();
	private static final Map<UUID, PendingMediaLinkRequest> PENDING_MEDIA_LINKS = new ConcurrentHashMap<>();
	private static final Map<UUID, InFlightMediaLinkRequest> IN_FLIGHT_MEDIA_LINKS = new ConcurrentHashMap<>();
	private static final Map<UUID, ScreenRuntimeKey> ACTIVE_MEDIA_ACTIONBARS = new ConcurrentHashMap<>();
	private static final Map<UUID, PlayerMediaFocus> PLAYER_MEDIA_FOCUS = new ConcurrentHashMap<>();
	private static final Map<ResourceKey<Level>, MonitorLevelState> LEVEL_STATES = new ConcurrentHashMap<>();
	private static volatile ExecutorService renderExecutor;
	private static volatile ExecutorService quantizeExecutor;
	private static volatile ExecutorService mediaIoExecutor;
	private static volatile ExecutorService overlayWindowExecutor;
	private static volatile ScheduledExecutorService mediaScheduler;
	private static volatile BufferedImage offBaseImage;
	private static volatile BufferedImage onBaseImage;

	private MonitorScreenSystem() {
	}

	public static void register() {
		ensureExecutors();
		UseEntityCallback.EVENT.register(MonitorScreenSystem::onUseEntity);
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(MonitorScreenSystem::onAllowChatMessage);
		ServerEntityEvents.ENTITY_LOAD.register(MonitorScreenSystem::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(MonitorScreenSystem::onEntityUnload);
		ServerChunkEvents.CHUNK_LOAD.register(MonitorScreenSystem::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(MonitorScreenSystem::onChunkUnload);
		ServerTickEvents.END_SERVER_TICK.register(MonitorScreenSystem::tick);
		ServerLifecycleEvents.SERVER_STARTED.register(MonitorScreenSystem::configureCacheDirectories);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MonitorYoutubeRelayClient.shutdown();
			MonitorYoutubeMusicCache.shutdown();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearMonitorRuntime());
	}

	private static void configureCacheDirectories(MinecraftServer server) {
		if (server == null) {
			return;
		}
		Path cacheRoot = monitorCacheRoot();
		MonitorMediaApp.setCacheDirectory(cacheRoot.resolve("media"));
		MonitorYoutubeRelayClient.setCacheDirectory(cacheRoot.resolve("youtube-preload"));
		MonitorYoutubeMusicCache.setCacheDirectory(cacheRoot.resolve("youtube-music"));
	}

	private static Path monitorCacheRoot() {
		return Path.of(System.getProperty("user.dir"), CACHE_ROOT_DIR_NAME, CACHE_NAMESPACE_DIR_NAME);
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
		if (overlayWindowExecutor == null) {
			overlayWindowExecutor = Executors.newFixedThreadPool(monitorOverlayWindowThreads(), daemonThreadFactory("lg2-monitor-window"));
		}
		if (mediaScheduler == null) {
			mediaScheduler = Executors.newScheduledThreadPool(monitorMediaSchedulerThreads(), daemonThreadFactory("lg2-monitor-scheduler"));
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

	private static int monitorMediaSchedulerThreads() {
		return Math.max(2, Math.min(8, monitorMediaIoThreads()));
	}

	private static int monitorOverlayWindowThreads() {
		return Math.max(1, Math.min(4, Math.max(1, monitorRenderThreads() / 2)));
	}

	private static int monitorMapUpdateRadiusBlocks() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(16, config.monitorMapUpdateRadiusBlocks) : 128;
	}

	private static long youtubePollActiveIntervalMs() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(33L, config.monitorYoutubePollActiveIntervalMs) : 50L;
	}

	private static long youtubePollIdleIntervalMs() {
		Lg2Config.ConfigData config = Lg2Config.get();
		return config != null ? Math.max(100L, config.monitorYoutubePollIdleIntervalMs) : 200L;
	}

	private static ThreadFactory daemonThreadFactory(String baseName) {
		return runnable -> {
			Thread thread = new Thread(runnable, baseName);
			thread.setDaemon(true);
			return thread;
		};
	}

	private static MonitorLevelState levelState(ResourceKey<Level> dimension) {
		return LEVEL_STATES.computeIfAbsent(dimension, MonitorLevelState::new);
	}

	private static void clearMonitorRuntime() {
		LEVEL_STATES.clear();
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (level == null || entity == null) {
			return;
		}
		if (entity instanceof ItemFrame frame && readScreenState(frame.getItem()) != null) {
			trackScreenFrame(level, frame);
			return;
		}
		if (entity instanceof Display.ItemDisplay display && display.getTags().contains(DISPLAY_ROOT_TAG)) {
			BlockPos pos = parsePositionTag(display.getTags());
			Direction facing = parseFacingTag(display.getTags());
			if (pos == null || facing == null || findScreenFrame(level, pos, facing) == null) {
				display.discard();
			}
		}
	}

	private static void onEntityUnload(Entity entity, ServerLevel level) {
		if (level == null || !(entity instanceof ItemFrame frame) || readScreenState(frame.getItem()) == null) {
			return;
		}
		untrackScreenFrame(level, new ScreenKey(frame.blockPosition(), frame.getDirection()), false);
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		scanChunkForScreenFrames(level, chunk);
		cleanupChunkDisplays(level, chunk);
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		int chunkX = chunk.getPos().x;
		int chunkZ = chunk.getPos().z;
		MonitorLevelState state = levelState(level.dimension());
		for (ScreenKey key : new ArrayList<>(state.knownFrames())) {
			if (SectionPos.blockToSectionCoord(key.pos().getX()) != chunkX || SectionPos.blockToSectionCoord(key.pos().getZ()) != chunkZ) {
				continue;
			}
			untrackScreenFrame(level, key, false);
		}
	}

	private static void scanChunkForScreenFrames(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		AABB box = chunkEntityBox(level, chunk);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box, candidate -> readScreenState(candidate.getItem()) != null)) {
			trackScreenFrame(level, frame);
		}
	}

	private static void cleanupChunkDisplays(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		AABB box = chunkEntityBox(level, chunk);
		for (Display.ItemDisplay display : level.getEntitiesOfClass(Display.ItemDisplay.class, box, candidate -> candidate.getTags().contains(DISPLAY_ROOT_TAG))) {
			BlockPos pos = parsePositionTag(display.getTags());
			Direction facing = parseFacingTag(display.getTags());
			if (pos == null || facing == null || findScreenFrame(level, pos, facing) == null) {
				display.discard();
			}
		}
	}

	private static AABB chunkEntityBox(ServerLevel level, LevelChunk chunk) {
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		return new AABB(
				minX - 1,
				level.getMinY(),
				minZ - 1,
				minX + 17,
				level.getMaxY(),
				minZ + 17
		);
	}

	private static void trackScreenFrame(ServerLevel level, ItemFrame frame) {
		if (level == null || frame == null || readScreenState(frame.getItem()) == null) {
			return;
		}
		MonitorLevelState state = levelState(level.dimension());
		ScreenKey key = new ScreenKey(frame.blockPosition(), frame.getDirection());
		state.knownFrames().add(key);
		enqueueScreenSync(level, key);
	}

	private static void untrackScreenFrame(ServerLevel level, ScreenKey key, boolean permanentRemoval) {
		if (level == null || key == null) {
			return;
		}
		MonitorLevelState state = levelState(level.dimension());
		state.knownFrames().remove(key);
		ScreenRuntimeKey runtimeKey = state.frameToRuntime().remove(key);
		if (runtimeKey != null) {
			invalidateCachedRuntime(level, runtimeKey, key, permanentRemoval);
		} else {
			enqueueNeighborSync(level, key.pos(), key.direction());
		}
	}

	private static void enqueueScreenSync(ServerLevel level, ScreenKey key) {
		if (level == null || key == null) {
			return;
		}
		levelState(level.dimension()).enqueueDirtyFrame(key);
	}

	private static void enqueueComponentSync(ServerLevel level, ScreenRuntimeKey key) {
		if (level == null || key == null) {
			return;
		}
		levelState(level.dimension()).enqueueDirtyRuntime(key);
	}

	private static void enqueueNeighborSync(ServerLevel level, BlockPos pos, Direction facing) {
		if (level == null || pos == null || facing == null) {
			return;
		}
		Direction right = frameRight(facing);
		for (BlockPos candidatePos : List.of(
				pos,
				pos.relative(right),
				pos.relative(right.getOpposite()),
				pos.above(),
				pos.below()
		)) {
			enqueueScreenSync(level, new ScreenKey(candidatePos, facing));
		}
	}

	private static void cacheComponent(ServerLevel level, ScreenComponent component) {
		if (level == null || component == null) {
			return;
		}
		MonitorLevelState state = levelState(level.dimension());
		ScreenComponent previousComponent = state.components().get(component.runtimeKey());
		Set<ScreenRuntimeKey> replacedRuntimes = new HashSet<>();
		for (ItemFrame frame : component.frameCoords().keySet()) {
			ScreenKey frameKey = new ScreenKey(frame.blockPosition(), frame.getDirection());
			state.knownFrames().add(frameKey);
			ScreenRuntimeKey previous = state.frameToRuntime().put(frameKey, component.runtimeKey());
			if (previous != null && !previous.equals(component.runtimeKey())) {
				replacedRuntimes.add(previous);
			}
		}
		for (ScreenRuntimeKey replaced : replacedRuntimes) {
			removeCachedRuntime(state, replaced, component.runtimeKey());
		}
		if (previousComponent != null) {
			Set<ScreenKey> currentKeys = new HashSet<>();
			for (ItemFrame frame : component.frameCoords().keySet()) {
				currentKeys.add(new ScreenKey(frame.blockPosition(), frame.getDirection()));
			}
			for (ItemFrame frame : previousComponent.frameCoords().keySet()) {
				ScreenKey frameKey = new ScreenKey(frame.blockPosition(), frame.getDirection());
				if (currentKeys.contains(frameKey)) {
					continue;
				}
				state.frameToRuntime().remove(frameKey, component.runtimeKey());
				if (state.knownFrames().contains(frameKey)) {
					enqueueScreenSync(level, frameKey);
				}
			}
		}
		state.components().put(component.runtimeKey(), component);
		state.enqueuePowerRuntime(component.runtimeKey());
	}

	private static void invalidateCachedRuntime(ServerLevel level, ScreenRuntimeKey runtimeKey, ScreenKey removedFrameKey, boolean permanentRemoval) {
		if (level == null || runtimeKey == null) {
			return;
		}
		ScreenComponent removed = removeCachedRuntime(levelState(level.dimension()), runtimeKey, null);
		if (removed == null) {
			if (removedFrameKey != null) {
				enqueueNeighborSync(level, removedFrameKey.pos(), removedFrameKey.direction());
			}
			return;
		}
		if (permanentRemoval) {
			List<GalleryCacheCandidate> removedCacheCandidates = galleryCacheCandidatesForRemovedComponent(removed, MEDIA_STATES.get(runtimeKey));
			closeMediaSession(level.getServer(), runtimeKey);
			if (!removedCacheCandidates.isEmpty()) {
				scheduleGalleryCacheRelease(level.getServer(), removedCacheCandidates, runtimeKey);
			}
		}
		for (ItemFrame frame : removed.frameCoords().keySet()) {
			ScreenKey frameKey = new ScreenKey(frame.blockPosition(), frame.getDirection());
			if (removedFrameKey != null && removedFrameKey.equals(frameKey)) {
				continue;
			}
			if (levelState(level.dimension()).knownFrames().contains(frameKey)) {
				enqueueScreenSync(level, frameKey);
			}
		}
		if (removedFrameKey != null) {
			enqueueNeighborSync(level, removedFrameKey.pos(), removedFrameKey.direction());
		}
	}

	private static ScreenComponent removeCachedRuntime(MonitorLevelState state, ScreenRuntimeKey runtimeKey, ScreenRuntimeKey replacementKey) {
		if (state == null || runtimeKey == null) {
			return null;
		}
		ScreenComponent removed = state.components().remove(runtimeKey);
		if (removed == null) {
			return null;
		}
		for (ItemFrame frame : removed.frameCoords().keySet()) {
			ScreenKey frameKey = new ScreenKey(frame.blockPosition(), frame.getDirection());
			if (replacementKey != null && replacementKey.equals(state.frameToRuntime().get(frameKey))) {
				continue;
			}
			state.frameToRuntime().remove(frameKey, runtimeKey);
		}
		return removed;
	}

	private static ScreenComponent resolveScreenComponent(ServerLevel level, ItemFrame frame) {
		if (level == null || frame == null) {
			return null;
		}
		MonitorLevelState state = levelState(level.dimension());
		ScreenRuntimeKey runtimeKey = state.frameToRuntime().get(new ScreenKey(frame.blockPosition(), frame.getDirection()));
		if (runtimeKey != null) {
			ScreenComponent cached = state.components().get(runtimeKey);
			if (cached != null) {
				return cached;
			}
		}
		ScreenComponent component = collectComponent(level, frame, null);
		if (component != null) {
			cacheComponent(level, component);
		}
		return component;
	}

	private static List<ScreenComponent> cachedComponents(ServerLevel level) {
		if (level == null) {
			return List.of();
		}
		return new ArrayList<>(levelState(level.dimension()).components().values());
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
		trackScreenFrame(level, frame);

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
		untrackScreenFrame(level, new ScreenKey(framePos, facing), true);
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
		Map<ScreenRuntimeKey, ScreenComponent> connectedComponents = collectConnectedSpeakerComponents(level, speakerPos);
		if (connectedComponents.isEmpty()) {
			return List.of();
		}

		List<SpeakerAudioSource> sources = new ArrayList<>();
		for (ScreenComponent component : connectedComponents.values()) {
			if (!component.powered()) {
				continue;
			}
			MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
			if (state == null) {
				continue;
			}
			synchronized (state) {
				boolean allowWhileLoading = isYoutubeMusicMode(state.mode)
						|| state.streamKind == PlaybackStreamKind.DIRECT_VIDEO
						|| state.streamFrame != null
						|| state.bufferedEndMs > state.positionMs + 100L;
				if (!isStreamPlaybackLocked(state)
						|| state.relaySessionId == null
						|| state.audioStreamUrl == null
						|| state.audioStreamUrl.isBlank()
						|| state.waitingForLink
						|| state.userPaused
						|| (state.loading && !allowWhileLoading)) {
					continue;
				}
				sources.add(new SpeakerAudioSource(
						componentGroupId(component.runtimeKey()),
						state.relaySessionId,
						state.audioStreamUrl,
						state.positionMs,
						state.audioSyncToken,
						state.loading,
						state.userPaused,
						state.liveStream
				));
			}
		}
		return sources;
	}

	public static boolean hasPoweredConnectedMonitor(ServerLevel level, BlockPos speakerPos) {
		return collectConnectedSpeakerComponents(level, speakerPos).values().stream().anyMatch(ScreenComponent::powered);
	}

	private static Map<ScreenRuntimeKey, ScreenComponent> collectConnectedSpeakerComponents(ServerLevel level, BlockPos speakerPos) {
		if (level == null || speakerPos == null || !level.hasChunkAt(speakerPos)) {
			return Map.of();
		}
		Set<BlockPos> wireNetwork = collectSpeakerWireNetwork(level, speakerPos);
		Map<ScreenRuntimeKey, ScreenComponent> connectedComponents = new HashMap<>();
		for (ScreenComponent component : cachedComponents(level)) {
			if (!isSpeakerConnectedToComponent(speakerPos, component, wireNetwork)) {
				continue;
			}
			connectedComponents.putIfAbsent(component.runtimeKey(), component);
		}
		return connectedComponents;
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

		boolean preservePlaybackDuringPrompt;
		synchronized (state) {
			preservePlaybackDuringPrompt = shouldKeepPlaybackWhilePromptingLocked(state, pending.mode(), pending.youtubeAction());
		}
		String url = message.signedContent() != null ? message.signedContent().trim() : "";
		if (url.isEmpty()) {
			synchronized (state) {
				if (preservePlaybackDuringPrompt) {
					state.overlayMode = MediaOverlayMode.CONTROLS;
				} else {
					state.waitingForLink = true;
					state.loading = false;
					state.statusText = linkPromptStatus(pending.mode(), sender);
				}
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
			state.overlayMode = MediaOverlayMode.CONTROLS;
			if (preservePlaybackDuringPrompt) {
				state.waitingForLink = false;
			} else {
				state.waitingForLink = false;
				state.loading = true;
				state.statusText = loadingStatus(pending.mode(), sender);
			}
			state.version++;
		}
		IN_FLIGHT_MEDIA_LINKS.put(sender.getUUID(), new InFlightMediaLinkRequest(pending.screenKey(), pending.mode(), pending.youtubeAction()));
		ACTIVE_MEDIA_ACTIONBARS.put(sender.getUUID(), pending.screenKey());
		sender.displayClientMessage(loadingMessage(pending.mode(), sender), true);
		requestRuntimeRender(server, pending.screenKey());
		if (!preservePlaybackDuringPrompt) {
			resumeMediaPlaybackIfNeeded(server, pending.screenKey());
		}

			if (isYoutubeFamilyMode(pending.mode())) {
				CompletableFuture
					.supplyAsync(() -> {
						try {
							return new YoutubeQueueResolveResult(
									pending.screenKey(),
									sender.getUUID(),
									pending.mode(),
									url,
									pending.mode() == ScreenViewMode.YOUTUBE_MUSIC
											? MonitorYoutubeMusicCache.resolveQueue(url)
											: MonitorYoutubeRelayClient.resolveQueue(url),
									pending.youtubeAction(),
									null
							);
						} catch (Exception exception) {
							return new YoutubeQueueResolveResult(
									pending.screenKey(),
									sender.getUUID(),
									pending.mode(),
									url,
									null,
									pending.youtubeAction(),
									sanitizeMediaError(exception.getMessage())
							);
						}
					}, mediaIoExecutor)
					.thenAccept(result -> server.execute(() -> applyYoutubeQueueResolveResult(server, result)));
		} else {
			CompletableFuture
					.supplyAsync(() -> {
						try {
							if (MonitorMediaApp.looksLikeDirectVideoUrl(url)) {
								return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, null, MonitorMediaApp.loadVideoFromUrl(url, state.progress), null);
							}
							return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, MonitorMediaApp.loadFromUrl(url, state.progress), null, null);
						} catch (Exception exception) {
							return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, null, null, sanitizeMediaError(exception.getMessage()));
						}
					}, mediaIoExecutor)
					.thenAccept(result -> server.execute(() -> applyMediaLoadResult(server, result)));
		}
		return false;
	}

	private static InteractionResult handleTouch(ServerPlayer player, ServerLevel level, ItemFrame frame, EntityHitResult hitResult) {
		ScreenComponent component = resolveScreenComponent(level, frame);
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
		boolean galleryLoadRequest = false;
		boolean persistGallery = false;
		Integer galleryDeferredLoadIndex = null;
		Boolean youtubePauseAction = null;
		Long youtubeSeekTargetMs = null;
		Integer youtubeQueuePlayIndex = null;
		boolean galleryDownloadRequested = false;
		boolean galleryWallpaperRequested = false;
		boolean youtubeDownloadRequested = false;
		boolean returnToGalleryAfterDelete = false;
		String releasedRelaySessionId = null;
		GalleryCacheCandidate deletedGalleryCacheCandidate = null;
		String galleryYoutubeUrl = null;
		String galleryYoutubeTitle = null;
		Integer galleryYoutubeIndex = null;
		List<String> youtubeQueueReleasedUrls = List.of();
		YoutubeQueuePreloadDiff youtubeQueuePreloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		if (component.viewMode() == ScreenViewMode.HOME) {
			List<MonitorApp> visibleApps = visibleHomeApps(layout, component.launcherPage());
			for (int index = 0; index < visibleApps.size(); index++) {
				UiRect appRect = homeAppCardRect(layout, component.launcherPage(), index);
				if (appRect.contains(touchPoint.x(), touchPoint.y())) {
					nextMode = ScreenViewMode.fromTag(visibleApps.get(index).id());
					break;
				}
			}
			int visibleRows = homeRowsPerPage(layout);
			int totalRows = homeTotalRows(layout);
			if (nextMode == null
					&& scrollbarVisible(visibleRows, totalRows)
					&& homeScrollbarTrackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				nextLauncherPage = scrollValueForTrack(
						homeScrollbarTrackRect(layout),
						visibleRows,
						totalRows,
						touchPoint.y()
				);
			}
		} else if (isPlayerMode(component.viewMode())) {
			markMediaFocus(player, component.runtimeKey());
			MediaRuntimeState mediaState = MEDIA_STATES.computeIfAbsent(
					component.runtimeKey(),
					ignored -> MediaRuntimeState.fresh(component.viewMode(), "", () -> onMediaProgressChanged(level.getServer(), component.runtimeKey()))
			);
			if (component.viewMode() == ScreenViewMode.GALLERY) {
				ensureGalleryStateHydrated(level.getServer(), component.runtimeKey(), mediaState);
			}
			MediaOverlayMode overlayMode;
			boolean hasMedia;
			boolean galleryBrowser;
			boolean galleryDeleteConfirmOpen;
			boolean playerUiVisible;
			boolean controlsWereHidden = false;
			synchronized (mediaState) {
				overlayMode = mediaState.overlayMode;
				hasMedia = hasDisplayableMediaLocked(mediaState);
				galleryBrowser = mediaState.mode == ScreenViewMode.GALLERY && mediaState.gallerySurfaceMode == GallerySurfaceMode.BROWSER;
				galleryDeleteConfirmOpen = mediaState.galleryDeleteConfirmOpen;
				playerUiVisible = mediaControlUiVisibleLocked(mediaState);
			}
			if (!galleryBrowser && (playerUiVisible || mediaState.loading) && overlayMode == MediaOverlayMode.VIEW) {
				synchronized (mediaState) {
					mediaState.overlayMode = MediaOverlayMode.CONTROLS;
					mediaState.version++;
				}
				rerenderCurrent = true;
				controlsWereHidden = true;
			}
			if (galleryDeleteConfirmOpen) {
				synchronized (mediaState) {
					if (!galleryDeleteConfirmPanelRect(layout).contains(touchPoint.x(), touchPoint.y())
							|| galleryDeleteConfirmCloseRect(layout).contains(touchPoint.x(), touchPoint.y())
							|| galleryDeleteConfirmCancelRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.galleryDeleteConfirmOpen = false;
						mediaState.version++;
					} else if (galleryDeleteConfirmConfirmRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						cancelPlaybackLocked(mediaState);
						if (isGalleryBackedYoutubeLocked(mediaState)) {
							returnToGalleryAfterDelete = true;
						}
						GalleryRemovalResult removal = removeGalleryItemLocked(mediaState, mediaState.galleryIndex >= 0 ? mediaState.galleryIndex : 0, layout);
						GalleryItem deletedItem = removal.removedItem();
						boolean stillSelected = removal.selectionRetained();
						deletedGalleryCacheCandidate = galleryCacheCandidate(deletedItem);
						if (deletedItem != null && deletedItem.url() != null && Objects.equals(deletedItem.url(), mediaState.wallpaperUrl)) {
							clearWallpaperLocked(mediaState);
						}
						if (!stillSelected) {
							mediaState.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
						}
						mediaState.galleryDeleteConfirmOpen = false;
						mediaState.statusText = "";
						mediaState.version++;
						persistGallery = true;
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible && isYoutubeFamilyMode(mediaState.mode) && mediaState.youtubeQueueOpen) {
				synchronized (mediaState) {
					int visibleRows = mediaQueueVisibleRows(layout);
					int maxScroll = Math.max(0, mediaState.youtubeQueue.size() - visibleRows);
					mediaState.youtubeQueueScroll = clampInt(mediaState.youtubeQueueScroll, 0, maxScroll);
					if (!mediaQueuePanelRect(layout).contains(touchPoint.x(), touchPoint.y())
							|| mediaQueueCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.youtubeQueueOpen = false;
					} else if (scrollbarVisible(visibleRows, mediaState.youtubeQueue.size())
							&& mediaQueueScrollbarTrackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.youtubeQueueScroll = scrollValueForTrack(
								mediaQueueScrollbarTrackRect(layout),
								visibleRows,
								mediaState.youtubeQueue.size(),
								touchPoint.y()
						);
					} else {
						int rowCount = Math.min(visibleRows, Math.max(0, mediaState.youtubeQueue.size() - mediaState.youtubeQueueScroll));
						for (int visibleIndex = 0; visibleIndex < rowCount; visibleIndex++) {
							UiRect rowRect = mediaQueueRowRect(layout, visibleIndex);
							if (!rowRect.contains(touchPoint.x(), touchPoint.y())) {
								continue;
							}
							int queueIndex = mediaState.youtubeQueueScroll + visibleIndex;
							if (queueIndex < 0 || queueIndex >= mediaState.youtubeQueue.size()) {
								break;
							}
							UiRect removeRect = mediaQueueRemoveRect(rowRect, layout);
							if (removeRect.contains(touchPoint.x(), touchPoint.y())) {
								boolean removedCurrent = queueIndex == mediaState.youtubeQueueIndex;
								mediaState.youtubeQueue.remove(queueIndex);
								if (mediaState.youtubeQueue.isEmpty()) {
									cancelPlaybackLocked(mediaState);
									clearYoutubePlaybackLocked(mediaState);
									mediaState.statusText = "";
									mediaState.progress.clear();
									mediaState.userPaused = false;
									mediaState.loading = false;
									mediaState.youtubeQueueIndex = -1;
									mediaState.youtubeQueueScroll = 0;
									mediaState.youtubeQueueOpen = false;
									youtubeQueueReleasedUrls = retainedYoutubePreloadUrlsLocked(mediaState);
									mediaState.retainedYoutubePreloadUrls.clear();
								} else {
									if (queueIndex < mediaState.youtubeQueueIndex) {
										mediaState.youtubeQueueIndex--;
									} else if (removedCurrent) {
										mediaState.youtubeQueueIndex = Math.min(queueIndex, mediaState.youtubeQueue.size() - 1);
										youtubeQueuePlayIndex = mediaState.youtubeQueueIndex;
									}
									int nextMaxScroll = Math.max(0, mediaState.youtubeQueue.size() - visibleRows);
									mediaState.youtubeQueueScroll = clampInt(mediaState.youtubeQueueScroll, 0, nextMaxScroll);
									if (youtubeQueuePlayIndex == null) {
										youtubeQueuePreloadDiff = syncYoutubeQueuePreloadsLocked(mediaState);
									}
								}
							} else {
								mediaState.youtubeQueueIndex = queueIndex;
								if (queueIndex < mediaState.youtubeQueue.size()) {
									YoutubeQueueItem selectedItem = mediaState.youtubeQueue.get(queueIndex);
									if (selectedItem != null && !Objects.equals(selectedItem.url(), mediaState.sourceUrl)) {
										youtubeQueuePlayIndex = queueIndex;
									}
								}
							}
							break;
						}
					}
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (galleryBrowser && mediaGalleryBrowserCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				nextMode = ScreenViewMode.HOME;
			} else if (galleryBrowser && mediaGalleryBrowserLinkRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				galleryLoadRequest = true;
				rerenderCurrent = true;
			} else if (galleryBrowser && mediaGalleryGridRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					int columns = mediaGalleryColumns(layout);
					int visibleRows = mediaGalleryVisibleRows(layout);
					int totalRows = mediaGalleryTotalRows(mediaState.galleryItems.size(), layout);
					int maxScroll = Math.max(0, totalRows - visibleRows);
					mediaState.galleryScroll = clampInt(mediaState.galleryScroll, 0, maxScroll);
					if (scrollbarVisible(visibleRows, totalRows)
							&& mediaGalleryBrowserScrollbarTrackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.galleryScroll = scrollValueForTrack(
								mediaGalleryBrowserScrollbarTrackRect(layout),
								visibleRows,
								totalRows,
								touchPoint.y()
						);
					} else {
						int rowCount = Math.min(visibleRows, Math.max(0, totalRows - mediaState.galleryScroll));
						for (int visibleRow = 0; visibleRow < rowCount; visibleRow++) {
							for (int column = 0; column < columns; column++) {
								int galleryIndex = (mediaState.galleryScroll + visibleRow) * columns + column;
								if (galleryIndex < 0 || galleryIndex >= mediaState.galleryItems.size()) {
									continue;
								}
								UiRect cardRect = mediaGalleryCardRect(layout, visibleRow, column);
								if (!cardRect.contains(touchPoint.x(), touchPoint.y())) {
									continue;
								}
								GalleryItem item = mediaState.galleryItems.get(galleryIndex);
								GalleryItemKind itemKind = effectiveGalleryItemKind(item);
								if (item != null && itemKind == GalleryItemKind.YOUTUBE && item.url() != null && !item.url().isBlank()) {
									mediaState.galleryIndex = galleryIndex;
									galleryYoutubeIndex = galleryIndex;
									galleryYoutubeUrl = item.url();
									galleryYoutubeTitle = item.title();
									mediaState.version++;
									visibleRow = rowCount;
									break;
								}
								if (selectGalleryItemLocked(mediaState, galleryIndex, layout)) {
									mediaState.statusText = "";
									mediaState.overlayMode = MediaOverlayMode.CONTROLS;
									mediaState.version++;
								} else {
									galleryDeferredLoadIndex = galleryIndex;
								}
								visibleRow = rowCount;
								break;
							}
						}
					}
				}
				rerenderCurrent = true;
			} else if (mediaCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				if (mediaState.mode == ScreenViewMode.GALLERY && !galleryBrowser) {
					synchronized (mediaState) {
						if (mediaState.relaySessionId != null && !mediaState.relaySessionId.isBlank()) {
							releasedRelaySessionId = mediaState.relaySessionId;
						}
						cancelPlaybackLocked(mediaState);
						clearYoutubePlaybackLocked(mediaState);
						mediaState.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
						mediaState.overlayMode = MediaOverlayMode.CONTROLS;
						mediaState.statusText = "";
						mediaState.version++;
					}
					rerenderCurrent = true;
				} else {
					boolean returnToGallery = false;
					synchronized (mediaState) {
						if (mediaState.mode == ScreenViewMode.YOUTUBE && mediaState.youtubeReturnToGallery) {
							mediaState.youtubeReturnToGallery = false;
							returnToGallery = true;
						}
					}
					nextMode = returnToGallery ? ScreenViewMode.GALLERY : ScreenViewMode.HOME;
				}
			} else if (!galleryBrowser && mediaState.mode == ScreenViewMode.GALLERY && mediaGalleryPlayerActionRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (currentGalleryItemSavedLocked(mediaState)) {
						mediaState.galleryDeleteConfirmOpen = true;
					} else {
						galleryDownloadRequested = true;
					}
					mediaState.statusText = "";
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (!galleryBrowser
					&& mediaState.mode == ScreenViewMode.GALLERY
					&& mediaDownloadRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (currentGalleryItemCanBeWallpaperLocked(mediaState)) {
						galleryWallpaperRequested = true;
						mediaState.statusText = "";
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& canTogglePlaybackLocked(mediaState)
					&& (mediaCenterPlayPauseRect(layout, mediaState.mode).contains(touchPoint.x(), touchPoint.y())
					|| mediaPlayPauseRect(layout, mediaState.mode).contains(touchPoint.x(), touchPoint.y()))) {
				synchronized (mediaState) {
					if (isStreamPlaybackLocked(mediaState) && mediaState.relaySessionId != null) {
						boolean shouldPause = !isPlaybackPausedLocked(mediaState);
						cancelPlaybackLocked(mediaState);
						mediaState.userPaused = shouldPause;
						bumpAudioSyncTokenLocked(mediaState);
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
			} else if (playerUiVisible && mediaCenterBackRect(layout, mediaState.mode).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (isYoutubeFamilyMode(mediaState.mode) && !mediaState.youtubeQueue.isEmpty()) {
						youtubeQueuePlayIndex = normalizeYoutubeQueueIndexLocked(
								mediaState,
								mediaState.youtubeQueueIndex >= 0 ? mediaState.youtubeQueueIndex - 1 : mediaState.youtubeQueue.size() - 1
						);
					} else if (mediaState.mode == ScreenViewMode.GALLERY && !mediaState.galleryItems.isEmpty()) {
						if (!selectGalleryItemLocked(
								mediaState,
								mediaState.galleryIndex >= 0 ? mediaState.galleryIndex - 1 : mediaState.galleryItems.size() - 1,
								layout
						)) {
							galleryDeferredLoadIndex = normalizeGalleryIndexLocked(
									mediaState,
									mediaState.galleryIndex >= 0 ? mediaState.galleryIndex - 1 : mediaState.galleryItems.size() - 1
							);
						}
						mediaState.version++;
					} else if (mediaState.loadedMedia != null && mediaState.loadedMedia.frameCount() > 1) {
						int seekFrames = Math.max(1, mediaState.loadedMedia.frameCount() / 20);
						mediaState.frameIndex = Math.max(0, mediaState.frameIndex - seekFrames);
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible && mediaCenterForwardRect(layout, mediaState.mode).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (isYoutubeFamilyMode(mediaState.mode) && !mediaState.youtubeQueue.isEmpty()) {
						youtubeQueuePlayIndex = normalizeYoutubeQueueIndexLocked(
								mediaState,
								mediaState.youtubeQueueIndex >= 0 ? mediaState.youtubeQueueIndex + 1 : 0
						);
					} else if (mediaState.mode == ScreenViewMode.GALLERY && !mediaState.galleryItems.isEmpty()) {
						if (!selectGalleryItemLocked(
								mediaState,
								mediaState.galleryIndex >= 0 ? mediaState.galleryIndex + 1 : 0,
								layout
						)) {
							galleryDeferredLoadIndex = normalizeGalleryIndexLocked(
									mediaState,
									mediaState.galleryIndex >= 0 ? mediaState.galleryIndex + 1 : 0
							);
						}
						mediaState.version++;
					} else if (mediaState.loadedMedia != null && mediaState.loadedMedia.frameCount() > 1) {
						int seekFrames = Math.max(1, mediaState.loadedMedia.frameCount() / 20);
						mediaState.frameIndex = Math.min(Math.max(0, mediaState.loadedMedia.frameCount() - 1), mediaState.frameIndex + seekFrames);
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible && mediaTimelineHitRect(layout, mediaState.mode).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (isStreamPlaybackLocked(mediaState) && canSeekTimelineLocked(mediaState)) {
						youtubeSeekTargetMs = youtubePositionForFraction(mediaState, mediaTimelineFraction(layout, touchPoint, mediaState.mode));
						mediaState.positionMs = youtubeSeekTargetMs;
						bumpAudioSyncTokenLocked(mediaState);
					} else if (mediaState.loadedMedia != null && mediaState.loadedMedia.frameCount() > 1) {
						mediaState.frameIndex = mediaFrameIndexForFraction(mediaState.loadedMedia, mediaTimelineFraction(layout, touchPoint, mediaState.mode));
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& mediaState.mode == ScreenViewMode.YOUTUBE
					&& isGalleryBackedYoutubeLocked(mediaState)
					&& mediaDownloadRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.galleryDeleteConfirmOpen = true;
					mediaState.statusText = "";
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (playerUiVisible && mediaState.mode == ScreenViewMode.YOUTUBE && mediaDownloadRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					youtubeDownloadRequested = true;
					mediaState.statusText = "";
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (playerUiVisible && mediaScaleRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.scaleMode = mediaState.scaleMode.next();
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if ((playerUiVisible || isYoutubeMusicMode(mediaState.mode))
					&& isYoutubeFamilyMode(mediaState.mode)
					&& !isGalleryBackedYoutubeLocked(mediaState)
					&& mediaQueueToggleRect(layout, mediaState.mode).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.youtubeQueueOpen = !mediaState.youtubeQueueOpen;
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (isYoutubeMusicMode(mediaState.mode)
					&& !isYoutubeHomePromptLocked(mediaState)
					&& !isGalleryBackedYoutubeLocked(mediaState)
					&& mediaYoutubeMusicSearchRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				requestMediaLink(
						player,
						component.runtimeKey(),
						false,
						component.viewMode(),
						hasMedia ? YoutubeLinkRequestAction.APPEND_QUEUE : YoutubeLinkRequestAction.REPLACE_QUEUE
				);
				rerenderCurrent = true;
			} else if (isYoutubeFamilyMode(mediaState.mode)
					&& !isGalleryBackedYoutubeLocked(mediaState)
					&& (!isYoutubeMusicMode(mediaState.mode) || isYoutubeHomePromptLocked(mediaState))
					&& mediaLinkRect(layout, playerUiVisible).contains(touchPoint.x(), touchPoint.y())) {
				requestMediaLink(
						player,
						component.runtimeKey(),
						false,
						component.viewMode(),
						isYoutubeFamilyMode(component.viewMode()) && hasMedia ? YoutubeLinkRequestAction.APPEND_QUEUE : YoutubeLinkRequestAction.REPLACE_QUEUE
				);
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& mediaState.mode != ScreenViewMode.YOUTUBE_MUSIC
					&& !controlsWereHidden
					&& !mediaState.loading) {
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

		if (!youtubeQueueReleasedUrls.isEmpty()) {
			releaseYoutubeQueuePreloads(youtubeQueueReleasedUrls);
		}
		if (releasedRelaySessionId != null && !releasedRelaySessionId.isBlank()) {
			releaseYoutubeRelaySession(releasedRelaySessionId);
			clearMediaSessionBindings(level.getServer(), component.runtimeKey());
		}
		if (!youtubeQueuePreloadDiff.isEmpty() && youtubeQueuePlayIndex == null) {
			applyYoutubeQueuePreloadDiff(youtubeQueuePreloadDiff);
		}
		if (returnToGalleryAfterDelete) {
			nextMode = ScreenViewMode.GALLERY;
			rerenderCurrent = false;
		}

		if ((nextMode != null && nextMode != component.viewMode())
				|| (nextLauncherPage != null && nextLauncherPage != component.launcherPage())) {
			if (isPlayerMode(component.viewMode()) && nextMode != component.viewMode()) {
				MediaRuntimeState currentState = MEDIA_STATES.get(component.runtimeKey());
				boolean preserveWallpaperPlayback = false;
				List<String> preservedReleasedQueueUrls = List.of();
				List<String> preservedReleasedMusicQueueUrls = List.of();
				if (currentState != null) {
					synchronized (currentState) {
						preserveWallpaperPlayback = shouldPreserveWallpaperPlaybackOnTransitionLocked(currentState, nextMode);
						if (preserveWallpaperPlayback) {
							preservedReleasedQueueUrls = retainedYoutubePreloadUrlsLocked(currentState);
							preservedReleasedMusicQueueUrls = retainedYoutubeMusicPreloadUrlsLocked(currentState);
							currentState.retainedYoutubePreloadUrls.clear();
							currentState.retainedYoutubeMusicUrls.clear();
							clearTransientPlaybackStateLocked(currentState, true);
							currentState.mode = nextMode != null ? nextMode : ScreenViewMode.HOME;
							currentState.overlayMode = MediaOverlayMode.VIEW;
							currentState.statusText = "";
							currentState.version++;
						}
					}
				}
				if (preserveWallpaperPlayback) {
					releaseYoutubeQueuePreloads(preservedReleasedQueueUrls);
					releaseYoutubeMusicQueuePreloads(preservedReleasedMusicQueueUrls);
					clearMediaSessionBindings(level.getServer(), component.runtimeKey());
				} else {
					deactivateMediaSession(level.getServer(), component.runtimeKey());
				}
			}
			if (isPlayerMode(nextMode) && component.viewMode() != nextMode) {
				openMediaSession(player, component.runtimeKey(), nextMode);
				markMediaFocus(player, component.runtimeKey());
			}
			synchronizeConnectedScreens(level, frame, null, nextMode, nextLauncherPage);
		} else if (rerenderCurrent) {
			requestComponentRender(level.getServer(), component, component.viewMode(), component.launcherPage());
			if (isPlayerMode(component.viewMode())) {
				resumeMediaPlaybackIfNeeded(level.getServer(), component.runtimeKey());
			}
		}
		if (galleryLoadRequest) {
			requestMediaLink(player, component.runtimeKey(), false, ScreenViewMode.GALLERY, YoutubeLinkRequestAction.REPLACE_QUEUE);
		}
		if (server != null && galleryDownloadRequested) {
			beginGalleryDownload(server, component.runtimeKey(), player.getUUID(), layout);
		}
		if (server != null && galleryWallpaperRequested) {
			applyGalleryWallpaper(server, component.runtimeKey(), player.getUUID());
		}
		if (persistGallery && server != null) {
			MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
			if (state != null) {
				persistGalleryState(server, component.runtimeKey(), state);
			}
			if (deletedGalleryCacheCandidate != null) {
				scheduleGalleryCacheRelease(server, List.of(deletedGalleryCacheCandidate), component.runtimeKey());
			}
		}
		if (galleryDeferredLoadIndex != null && server != null) {
			MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
			if (state != null) {
				String deferredTitle = null;
				String deferredUrl = null;
				String deferredLocalMediaKey = null;
				GalleryItemKind deferredKind = GalleryItemKind.MEDIA;
				Integer deferredGalleryIndex = null;
				synchronized (state) {
					int index = normalizeGalleryIndexLocked(state, galleryDeferredLoadIndex);
						if (index >= 0 && index < state.galleryItems.size()) {
							GalleryItem item = state.galleryItems.get(index);
							if (item != null && item.url() != null && !item.url().isBlank()) {
								deferredTitle = item.title();
								deferredUrl = item.url();
								deferredLocalMediaKey = item.localMediaKey();
								deferredGalleryIndex = index;
								deferredKind = effectiveGalleryItemKind(item);
							}
						}
					}
				if (deferredKind == GalleryItemKind.YOUTUBE && deferredUrl != null) {
					startGalleryYoutubePlayback(
							server,
							component.runtimeKey(),
							player.getUUID(),
							deferredTitle,
							deferredUrl,
							deferredGalleryIndex
					);
				} else if (deferredUrl != null) {
					scheduleGalleryItemLoad(server, component.runtimeKey(), deferredTitle, deferredUrl, deferredLocalMediaKey, deferredKind, true, galleryDeferredLoadIndex);
				}
			}
		}
		if (server != null && youtubePauseAction != null) {
			boolean shouldPause = youtubePauseAction;
			refreshConnectedSpeakersNow(server, component.runtimeKey());
			ensureExecutors();
			CompletableFuture.runAsync(() -> {
				try {
					if (shouldPause) {
						MonitorYoutubeRelayClient.pause(relaySessionId(component.runtimeKey()));
					} else {
						MonitorYoutubeRelayClient.resume(relaySessionId(component.runtimeKey()));
					}
				} catch (Exception exception) {
					Lg2.LOGGER.debug("Failed to {} YouTube session {}", shouldPause ? "pause" : "resume", component.runtimeKey(), exception);
				}
			}, mediaIoExecutor).thenRun(() -> server.execute(() -> {
				refreshConnectedSpeakersNow(server, component.runtimeKey());
				scheduleYoutubeRefresh(server, component.runtimeKey(), 0L);
			}));
		}
		if (server != null && youtubeSeekTargetMs != null) {
			long seekTargetMs = youtubeSeekTargetMs;
			refreshConnectedSpeakersNow(server, component.runtimeKey());
			ensureExecutors();
			CompletableFuture.runAsync(() -> {
				try {
					MonitorYoutubeRelayClient.seek(relaySessionId(component.runtimeKey()), seekTargetMs);
				} catch (Exception exception) {
					Lg2.LOGGER.debug("Failed to seek YouTube session {} to {}", component.runtimeKey(), seekTargetMs, exception);
				}
			}, mediaIoExecutor).thenRun(() -> server.execute(() -> {
				refreshConnectedSpeakersNow(server, component.runtimeKey());
				scheduleYoutubeRefresh(server, component.runtimeKey(), 0L);
			}));
		}
		if (server != null && youtubeQueuePlayIndex != null) {
			startYoutubeQueuePlayback(server, component.runtimeKey(), player.getUUID(), youtubeQueuePlayIndex);
		}
		if (server != null && youtubeDownloadRequested) {
			beginYoutubeDownload(server, component.runtimeKey(), player.getUUID());
		}
		if (server != null && galleryYoutubeUrl != null) {
			startGalleryYoutubePlayback(server, component.runtimeKey(), player.getUUID(), galleryYoutubeTitle, galleryYoutubeUrl, galleryYoutubeIndex);
		}
		return InteractionResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		processPendingScreenSyncs(server);
		processPendingComponentSyncs(server);
		processPowerRefreshes(server);
		if ((server.getTickCount() % MEDIA_FOCUS_CLEANUP_INTERVAL_TICKS) == 0L) {
			cleanupExpiredMediaFocus();
		}
		if ((server.getTickCount() % MEDIA_ACTIONBAR_REFRESH_INTERVAL_TICKS) == 0L) {
			refreshMediaRequestActionbars(server);
		}
		if ((server.getTickCount() % MEDIA_SESSION_CLEANUP_INTERVAL_TICKS) == 0L) {
			cleanupMediaSessions(server);
		}
	}

	private static void processPendingScreenSyncs(MinecraftServer server) {
		if (server == null) {
			return;
		}
		int remaining = MAX_SCREEN_SYNC_OPERATIONS_PER_TICK;
		for (MonitorLevelState state : LEVEL_STATES.values()) {
			if (remaining <= 0) {
				break;
			}
			while (remaining > 0) {
				ScreenKey key = state.pollDirtyFrame();
				if (key == null) {
					break;
				}
				processPendingScreenSync(server, state, key);
				remaining--;
			}
		}
	}

	private static void processPendingComponentSyncs(MinecraftServer server) {
		if (server == null) {
			return;
		}
		int remaining = Math.max(4, MAX_SCREEN_SYNC_OPERATIONS_PER_TICK / 2);
		for (MonitorLevelState state : LEVEL_STATES.values()) {
			if (remaining <= 0) {
				break;
			}
			while (remaining > 0) {
				ScreenRuntimeKey runtimeKey = state.pollDirtyRuntime();
				if (runtimeKey == null) {
					break;
				}
				dispatchRuntimeRender(server, runtimeKey);
				remaining--;
			}
		}
	}

	private static void processPendingScreenSync(MinecraftServer server, MonitorLevelState state, ScreenKey key) {
		if (server == null || state == null || key == null) {
			return;
		}
		ServerLevel level = server.getLevel(state.dimension());
		if (level == null || !level.hasChunkAt(key.pos())) {
			return;
		}
		ItemFrame frame = findScreenFrame(level, key.pos(), key.direction());
		if (frame == null || readScreenState(frame.getItem()) == null) {
			state.knownFrames().remove(key);
			ScreenRuntimeKey runtimeKey = state.frameToRuntime().remove(key);
			if (runtimeKey != null) {
				invalidateCachedRuntime(level, runtimeKey, key, true);
			}
			return;
		}
		synchronizeConnectedScreens(level, frame, null, null, null);
	}

	private static void processPowerRefreshes(MinecraftServer server) {
		if (server == null) {
			return;
		}
		int remaining = MAX_POWER_REFRESHES_PER_TICK;
		for (MonitorLevelState state : LEVEL_STATES.values()) {
			if (remaining <= 0) {
				break;
			}
			ServerLevel level = server.getLevel(state.dimension());
			if (level == null) {
				continue;
			}
			while (remaining > 0) {
				ScreenRuntimeKey runtimeKey = state.pollPowerRuntime();
				if (runtimeKey == null) {
					for (ScreenRuntimeKey cachedKey : state.components().keySet()) {
						state.enqueuePowerRuntime(cachedKey);
					}
					runtimeKey = state.pollPowerRuntime();
					if (runtimeKey == null) {
						break;
					}
				}
				refreshComponentPower(level, runtimeKey);
				if (state.components().containsKey(runtimeKey)) {
					state.enqueuePowerRuntime(runtimeKey);
				}
				remaining--;
			}
		}
	}

	private static void refreshComponentPower(ServerLevel level, ScreenRuntimeKey runtimeKey) {
		if (level == null || runtimeKey == null) {
			return;
		}
		ScreenComponent component = resolveScreenComponent(level.getServer(), runtimeKey);
		if (component == null) {
			return;
		}
		boolean poweredNow = component.frameCoords().keySet().stream()
				.anyMatch(frame -> frame != null && frame.isAlive() && isPowered(level, frame));
		if (poweredNow != component.powered()) {
			ItemFrame rootFrame = findScreenFrame(level, runtimeKey.pos(), runtimeKey.facing());
			if (rootFrame != null) {
				synchronizeConnectedScreens(level, rootFrame, null, null, null);
			}
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
		MediaRuntimeState state = MEDIA_STATES.computeIfAbsent(key, ignored -> MediaRuntimeState.fresh(mode, "", () -> onMediaProgressChanged(server, key)));
		synchronized (state) {
			state.mode = mode;
			if (isYoutubeFamilyMode(mode)
					&& !hasDisplayableMediaLocked(state)
					&& !state.loading
					&& (isYoutubeMusicMode(mode) || !isStreamPlaybackLocked(state))) {
				cancelPlaybackLocked(state);
				clearYoutubePlaybackLocked(state);
				clearGallerySelectionLocked(state);
				state.loading = false;
				state.waitingForLink = false;
				state.statusText = "";
				state.overlayMode = MediaOverlayMode.CONTROLS;
				state.youtubeQueueOpen = false;
				state.youtubeReturnToGallery = false;
				state.version++;
			}
		}
		if (mode == ScreenViewMode.GALLERY) {
			ensureGalleryStateHydrated(server, key, state);
		}
	}

	private static void beginGalleryDownload(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid, UiLayout layout) {
		if (server == null || key == null || layout == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureGalleryStateHydrated(server, key, state);
		MonitorMediaApp.LoadedMedia media;
		BufferedImage directVideoPreview;
		String url;
		String title;
		boolean directVideo;
		synchronized (state) {
			if ((state.loadedMedia == null && !isDirectVideoPlaybackLocked(state)) || state.sourceUrl == null || state.sourceUrl.isBlank()) {
				return;
			}
			if (currentGalleryItemSavedLocked(state)) {
				return;
			}
			if (state.downloadInProgress && Objects.equals(state.downloadTargetUrl, state.sourceUrl)) {
				return;
			}
			markDownloadStartedLocked(state, state.sourceUrl, requesterUuid);
			state.statusText = "";
			state.version++;
			media = state.loadedMedia;
			directVideoPreview = copyBufferedImage(state.streamFrame);
			url = state.sourceUrl;
			title = state.mediaTitle;
			directVideo = isDirectVideoPlaybackLocked(state);
		}
		requestRuntimeRender(server, key);
		ensureExecutors();
		mediaScheduler.schedule(
				() -> CompletableFuture
						.supplyAsync(() -> {
							try {
								return new SavedGalleryMediaPersistResult(
										url,
										directVideo ? MonitorMediaApp.persistSavedGalleryVideo(url, null) : MonitorMediaApp.persistSavedGalleryMedia(url),
										null
								);
							} catch (Exception exception) {
								return new SavedGalleryMediaPersistResult(url, null, sanitizeMediaError(exception.getMessage()));
							}
						}, mediaIoExecutor)
						.thenAccept(result -> server.execute(() -> {
							if (directVideo) {
								finishGalleryVideoDownload(server, key, title, url, directVideoPreview, result.savedMediaKey(), result.error(), layout);
							} else {
								finishGalleryDownload(server, key, title, url, media, result.savedMediaKey(), result.error(), layout);
							}
						})),
				MEDIA_ACTION_SPINNER_MIN_MILLIS,
				TimeUnit.MILLISECONDS
		);
	}

	private static void finishGalleryDownload(
			MinecraftServer server,
			ScreenRuntimeKey key,
			String title,
			String url,
			MonitorMediaApp.LoadedMedia media,
			String savedMediaKey,
			String saveError,
			UiLayout layout
	) {
		if (server == null || key == null || url == null || url.isBlank() || media == null || layout == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureGalleryStateHydrated(server, key, state);
		UUID requesterUuid;
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !Objects.equals(state.downloadTargetUrl, url)) {
				return;
			}
			requesterUuid = state.downloadRequesterUuid;
			if (savedMediaKey == null || savedMediaKey.isBlank()) {
				clearDownloadStateLocked(state);
				state.statusText = saveError != null && !saveError.isBlank() ? saveError : "SAVE FAILED";
				state.version++;
				requestRuntimeRender(server, key);
				return;
			}
			int index = upsertGalleryItemLocked(
					state,
					title,
					url,
					savedMediaKey,
					media,
					media.frameCount() > 0 ? media.frame(0) : null,
					GalleryItemKind.MEDIA
			);
			saved = index >= 0;
			if (saved) {
				selectGalleryItemLocked(state, index, layout);
				markDownloadCompletedLocked(state, url);
			} else {
				clearDownloadStateLocked(state);
			}
			state.statusText = "";
			state.version++;
		}
		if (!saved) {
			requestRuntimeRender(server, key);
			return;
		}
		persistGalleryState(server, key, state);
		requestRuntimeRender(server, key);
		scheduleActionCompletionReset(server, key);
		ServerPlayer requester = requesterUuid != null ? server.getPlayerList().getPlayer(requesterUuid) : null;
		if (requester != null) {
			requester.sendSystemMessage(Component.literal("Медиа сохранено в галерею"));
		}
	}

	private static void finishGalleryVideoDownload(
			MinecraftServer server,
			ScreenRuntimeKey key,
			String title,
			String url,
			BufferedImage preview,
			String savedMediaKey,
			String saveError,
			UiLayout layout
	) {
		if (server == null || key == null || url == null || url.isBlank() || layout == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureGalleryStateHydrated(server, key, state);
		UUID requesterUuid;
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !Objects.equals(state.downloadTargetUrl, url)) {
				return;
			}
			requesterUuid = state.downloadRequesterUuid;
			if (savedMediaKey == null || savedMediaKey.isBlank()) {
				clearDownloadStateLocked(state);
				state.statusText = saveError != null && !saveError.isBlank() ? saveError : "SAVE FAILED";
				state.version++;
				requestRuntimeRender(server, key);
				return;
			}
			int index = upsertGalleryItemLocked(
					state,
					title,
					url,
					savedMediaKey,
					null,
					preview,
					GalleryItemKind.VIDEO
			);
			saved = index >= 0;
			if (saved) {
				if (Objects.equals(state.sourceUrl, url)) {
					state.galleryIndex = index;
					state.gallerySurfaceMode = GallerySurfaceMode.PLAYER;
				}
				markDownloadCompletedLocked(state, url);
			} else {
				clearDownloadStateLocked(state);
			}
			state.statusText = "";
			state.version++;
		}
		if (!saved) {
			requestRuntimeRender(server, key);
			return;
		}
		persistGalleryState(server, key, state);
		requestRuntimeRender(server, key);
		scheduleActionCompletionReset(server, key);
		ServerPlayer requester = requesterUuid != null ? server.getPlayerList().getPlayer(requesterUuid) : null;
		if (requester != null) {
			requester.sendSystemMessage(Component.literal("Видео сохранено в галерею"));
		}
	}

	private static void applyGalleryWallpaper(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean shouldAnimate = false;
		synchronized (state) {
			if (!currentGalleryItemCanBeWallpaperLocked(state)) {
				return;
			}
			GalleryItem item = currentGalleryItemLocked(state);
			if (item == null || item.media() == null || item.url() == null || item.url().isBlank()) {
				return;
			}
			state.wallpaperUrl = item.url();
			state.wallpaperMedia = item.media();
			state.wallpaperScaleMode = state.scaleMode != null ? state.scaleMode : MediaScaleMode.FIT;
			state.wallpaperFrameIndex = 0;
			state.wallpaperHydrated = true;
			state.version++;
			shouldAnimate = wallpaperVisibleForCurrentViewLocked(state) && item.media().animated();
		}
		persistGalleryState(server, key, state);
		requestRuntimeRender(server, key);
		if (shouldAnimate) {
			resumeMediaPlaybackIfNeeded(server, key);
		}
		ServerPlayer requester = requesterUuid != null ? server.getPlayerList().getPlayer(requesterUuid) : null;
		if (requester != null) {
			requester.sendSystemMessage(Component.literal("Обои обновлены"));
		}
	}

	private static void beginYoutubeDownload(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureGalleryStateHydrated(server, key, state);
		boolean readyNow = false;
		synchronized (state) {
			if (state.mode != ScreenViewMode.YOUTUBE || state.sourceUrl == null || state.sourceUrl.isBlank()) {
				return;
			}
			if (hasGalleryItemForUrlLocked(state, state.sourceUrl)) {
				state.version++;
				return;
			}
			if (state.downloadInProgress && Objects.equals(state.downloadTargetUrl, state.sourceUrl)) {
				return;
			}
			markDownloadStartedLocked(state, state.sourceUrl, requesterUuid);
			state.statusText = "";
			state.version++;
			readyNow = isYoutubeGalleryDownloadReadyLocked(state);
		}
		requestRuntimeRender(server, key);
		if (readyNow) {
			finishYoutubeDownload(server, key);
			return;
		}
		maybeCompleteYoutubeDownload(server, key);
	}

	private static void maybeCompleteYoutubeDownload(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		long delayMillis;
		synchronized (state) {
			if (!state.downloadInProgress || !isYoutubeGalleryDownloadReadyLocked(state)) {
				return;
			}
			delayMillis = remainingDownloadSpinnerMillisLocked(state);
		}
		if (delayMillis > 0L) {
			ensureExecutors();
			mediaScheduler.schedule(() -> server.execute(() -> finishYoutubeDownload(server, key)), delayMillis, TimeUnit.MILLISECONDS);
			return;
		}
		finishYoutubeDownload(server, key);
	}

	private static void finishYoutubeDownload(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureGalleryStateHydrated(server, key, state);
		UUID requesterUuid;
		String savedUrl = null;
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !isYoutubeGalleryDownloadReadyLocked(state)) {
				return;
			}
			if (state.sourceUrl == null || state.sourceUrl.isBlank() || !Objects.equals(state.sourceUrl, state.downloadTargetUrl)) {
				return;
			}
			requesterUuid = state.downloadRequesterUuid;
			saved = saveCurrentYoutubeToGalleryLocked(state);
			if (saved) {
				savedUrl = state.sourceUrl;
				markDownloadCompletedLocked(state, state.sourceUrl);
			} else {
				clearDownloadStateLocked(state);
			}
			state.statusText = "";
			state.version++;
		}
		if (!saved) {
			requestRuntimeRender(server, key);
			return;
		}
		if (savedUrl != null && !savedUrl.isBlank()) {
			try {
				MonitorYoutubeRelayClient.persistQueueEntryFromSession(relaySessionId(key), savedUrl);
			} catch (Exception exception) {
				Lg2.LOGGER.debug("Failed to persist gallery YouTube preload for {}", savedUrl, exception);
			}
		}
		persistGalleryState(server, key, state);
		YoutubeQueuePreloadDiff preloadDiff;
		synchronized (state) {
			preloadDiff = syncYoutubeQueuePreloadsLocked(state);
		}
		applyYoutubeQueuePreloadDiff(preloadDiff);
		scheduleGalleryPreloadStatusRefreshes(server, key);
		requestRuntimeRender(server, key);
		scheduleActionCompletionReset(server, key);
		ServerPlayer requester = requesterUuid != null ? server.getPlayerList().getPlayer(requesterUuid) : null;
		if (requester != null) {
			requester.sendSystemMessage(Component.literal("Видео сохранено в галерею"));
		}
	}

	private static void startStandaloneYoutubePlayback(
			MinecraftServer server,
			ScreenRuntimeKey key,
			UUID requesterUuid,
			String title,
			String url,
			boolean returnToGallery
	) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			cancelPlaybackLocked(state);
			clearYoutubePlaybackLocked(state);
			clearYoutubeQueueLocked(state);
			state.mode = ScreenViewMode.YOUTUBE;
			state.streamKind = PlaybackStreamKind.YOUTUBE;
			state.youtubeReturnToGallery = returnToGallery;
			state.sourceUrl = url;
			state.mediaTitle = title == null || title.isBlank() ? "YouTube" : title;
			state.waitingForLink = false;
			state.loading = true;
			state.userPaused = false;
			state.youtubeQueueOpen = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			state.statusText = "BUFFERING";
			state.progress.setIndeterminate("LOADING");
			state.version++;
		}
		requestRuntimeRender(server, key);
		resumeMediaPlaybackIfNeeded(server, key);
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new YoutubeLoadResult(
								key,
								requesterUuid,
								url,
								ScreenViewMode.YOUTUBE,
								PlaybackStreamKind.YOUTUBE,
								MonitorYoutubeRelayClient.load(relaySessionId(key), url, state.progress),
								null
						);
					} catch (Exception exception) {
						return new YoutubeLoadResult(key, requesterUuid, url, ScreenViewMode.YOUTUBE, PlaybackStreamKind.YOUTUBE, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
	}

	private static void startGalleryYoutubePlayback(
			MinecraftServer server,
			ScreenRuntimeKey key,
			UUID requesterUuid,
			String title,
			String url,
			Integer galleryIndex
	) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			cancelPlaybackLocked(state);
			clearYoutubePlaybackLocked(state);
			clearYoutubeQueueLocked(state);
			state.mode = ScreenViewMode.GALLERY;
			state.streamKind = PlaybackStreamKind.YOUTUBE;
			state.youtubeReturnToGallery = false;
			state.galleryDeleteConfirmOpen = false;
			state.gallerySurfaceMode = GallerySurfaceMode.PLAYER;
			state.galleryIndex = normalizeGalleryIndexLocked(state, galleryIndex == null ? -1 : galleryIndex);
			state.sourceUrl = url;
			state.mediaTitle = title == null || title.isBlank() ? "YouTube" : title;
			state.waitingForLink = false;
			state.loading = true;
			state.userPaused = false;
			state.youtubeQueueOpen = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			state.statusText = "BUFFERING";
			state.progress.setIndeterminate("LOADING");
			state.version++;
		}
		requestRuntimeRender(server, key);
		resumeMediaPlaybackIfNeeded(server, key);
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new YoutubeLoadResult(
								key,
								requesterUuid,
								url,
								ScreenViewMode.GALLERY,
								PlaybackStreamKind.YOUTUBE,
								MonitorYoutubeRelayClient.load(relaySessionId(key), url, state.progress),
								null
						);
					} catch (Exception exception) {
						return new YoutubeLoadResult(key, requesterUuid, url, ScreenViewMode.GALLERY, PlaybackStreamKind.YOUTUBE, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
	}

	private static void startDirectVideoPlayback(
			MinecraftServer server,
			ScreenRuntimeKey key,
			UUID requesterUuid,
			String title,
			String subtitle,
			String url,
			MonitorMediaApp.LoadedVideo video,
			int selectionIndex,
			ScreenViewMode targetMode,
			boolean preserveQueue
	) {
		if (server == null || key == null || url == null || url.isBlank() || video == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			cancelPlaybackLocked(state);
			clearYoutubePlaybackLocked(state);
			if (!preserveQueue) {
				clearYoutubeQueueLocked(state);
			}
			state.mode = targetMode;
			state.streamKind = PlaybackStreamKind.DIRECT_VIDEO;
			state.youtubeReturnToGallery = false;
			state.galleryDeleteConfirmOpen = false;
			if (targetMode == ScreenViewMode.GALLERY) {
				state.gallerySurfaceMode = GallerySurfaceMode.PLAYER;
				state.galleryIndex = selectionIndex >= 0 ? normalizeGalleryIndexLocked(state, selectionIndex) : -1;
			} else {
				state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
				state.galleryIndex = -1;
				state.youtubeQueueIndex = normalizeYoutubeQueueIndexLocked(state, selectionIndex);
				state.youtubeQueueOpen = false;
			}
			state.sourceUrl = url;
			state.mediaTitle = title == null || title.isBlank() ? "Video" : title;
			state.mediaSubtitle = subtitle == null ? "" : subtitle;
			state.streamFrame = video.preview();
			state.loadingBackdropFrame = null;
			state.waitingForLink = false;
			state.loading = true;
			state.userPaused = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			state.statusText = "BUFFERING";
			state.durationMs = Math.max(0L, video.durationMs());
			state.positionMs = 0L;
			state.bufferedStartMs = 0L;
			state.bufferedEndMs = 0L;
			state.audioStreamUrl = video.audioInput();
			state.progress.setIndeterminate("LOADING");
			state.version++;
		}
		requestRuntimeRender(server, key);
		resumeMediaPlaybackIfNeeded(server, key);
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new YoutubeLoadResult(
								key,
								requesterUuid,
								url,
								targetMode,
								PlaybackStreamKind.DIRECT_VIDEO,
								MonitorYoutubeRelayClient.loadDirect(
										relaySessionId(key),
										url,
										title,
										video.playbackInput(),
										video.audioInput(),
										video.durationMs(),
										targetMode == ScreenViewMode.YOUTUBE_MUSIC ? video.preview() : null,
										state.progress
								),
								null
						);
					} catch (Exception exception) {
						return new YoutubeLoadResult(key, requesterUuid, url, targetMode, PlaybackStreamKind.DIRECT_VIDEO, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
	}

	private static void startDirectVideoPlayback(
			MinecraftServer server,
			ScreenRuntimeKey key,
			UUID requesterUuid,
			String title,
			String url,
			MonitorMediaApp.LoadedVideo video,
			int selectionIndex,
			ScreenViewMode targetMode,
			boolean preserveQueue
	) {
		startDirectVideoPlayback(server, key, requesterUuid, title, "", url, video, selectionIndex, targetMode, preserveQueue);
	}

	private static void requestMediaLink(ServerPlayer player, ScreenRuntimeKey key, boolean clearCurrentMedia, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
		if (player == null || key == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		InFlightMediaLinkRequest inFlight = IN_FLIGHT_MEDIA_LINKS.get(player.getUUID());
		if (inFlight != null) {
			ACTIVE_MEDIA_ACTIONBARS.put(player.getUUID(), inFlight.screenKey());
			player.displayClientMessage(loadingMessage(inFlight.mode(), player), true);
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.computeIfAbsent(key, ignored -> MediaRuntimeState.fresh(mode, linkPromptStatus(mode, player), () -> onMediaProgressChanged(server, key)));
		if (mode == ScreenViewMode.GALLERY) {
			ensureGalleryStateHydrated(server, key, state);
		}
		synchronized (state) {
			boolean preservePlayback = shouldKeepPlaybackWhilePromptingLocked(state, mode, youtubeAction);
			state.mode = mode;
			if (!(isYoutubeFamilyMode(mode) && youtubeAction == YoutubeLinkRequestAction.APPEND_QUEUE)) {
				cancelPlaybackLocked(state);
			}
			if (clearCurrentMedia && !preservePlayback) {
				clearLoadedContentLocked(state);
			}
			if (!preservePlayback) {
				state.userPaused = false;
				state.waitingForLink = true;
				state.loading = false;
				state.statusText = linkPromptStatus(mode, player);
				state.progress.clear();
			} else {
				state.waitingForLink = false;
			}
			state.youtubeReturnToGallery = false;
			if (mode == ScreenViewMode.GALLERY) {
				state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
			}
			state.overlayMode = MediaOverlayMode.CONTROLS;
			state.version++;
		}
		PENDING_MEDIA_LINKS.put(player.getUUID(), new PendingMediaLinkRequest(key, mode, youtubeAction));
		ACTIVE_MEDIA_ACTIONBARS.put(player.getUUID(), key);
		player.displayClientMessage(linkPromptMessage(mode, player), true);
		requestRuntimeRender(server, key);
	}

	private static boolean shouldKeepPlaybackWhilePromptingLocked(MediaRuntimeState state, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
		if (state == null || !isYoutubeFamilyMode(mode) || youtubeAction != YoutubeLinkRequestAction.APPEND_QUEUE) {
			return false;
		}
		return isStreamPlaybackLocked(state)
				&& (state.loading
				|| (state.sourceUrl != null && !state.sourceUrl.isBlank())
				|| state.relaySessionId != null
				|| state.streamFrame != null
				|| state.durationMs > 0L);
	}

	private static void ensureGalleryStateHydrated(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		List<PersistedGalleryItem> persistedItems = List.of();
		YoutubeQueuePreloadDiff preloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		boolean shouldRender = false;
		synchronized (state) {
			if (state.galleryHydrated) {
				return;
			}
			if (!state.galleryItems.isEmpty()) {
				state.galleryHydrated = true;
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
			} else {
				ScreenComponent component = resolveScreenComponent(server, key);
				persistedItems = resolvePersistedGalleryState(component);
				state.galleryHydrated = true;
				if (!persistedItems.isEmpty()) {
					state.galleryItems.clear();
					state.galleryItems.addAll(galleryItemsFromPersisted(persistedItems));
					state.galleryIndex = -1;
					state.galleryScroll = 0;
					state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
					state.version++;
					shouldRender = true;
				}
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
			}
		}
		applyYoutubeQueuePreloadDiff(preloadDiff);
		scheduleGalleryPreloadStatusRefreshes(server, key);
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
		for (PersistedGalleryItem item : persistedItems) {
			GalleryItemKind resolvedKind = effectiveGalleryItemKind(item);
			if (resolvedKind == GalleryItemKind.MEDIA || resolvedKind == GalleryItemKind.VIDEO) {
				scheduleGalleryItemLoad(server, key, item.title(), item.url(), item.localMediaKey(), resolvedKind, false, -1);
			}
		}
	}

	private static void ensureWallpaperStateHydrated(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		String wallpaperUrl = null;
		String wallpaperLocalMediaKey = null;
		boolean shouldRender = false;
		synchronized (state) {
			if (state.wallpaperHydrated) {
				return;
			}
			PersistedWallpaperState persisted = null;
			PersistedGalleryItem persistedWallpaperItem = null;
			ScreenComponent component = resolveScreenComponent(server, key);
			if (component != null) {
				persisted = resolvePersistedWallpaperState(component);
				if (persisted != null && persisted.url() != null && !persisted.url().isBlank()) {
					for (PersistedGalleryItem galleryItem : resolvePersistedGalleryState(component)) {
						if (galleryItem != null && Objects.equals(galleryItem.url(), persisted.url())) {
							persistedWallpaperItem = galleryItem;
							break;
						}
					}
				}
			}
			state.wallpaperHydrated = true;
			state.wallpaperFrameIndex = 0;
			if (persisted == null || persisted.url() == null || persisted.url().isBlank()) {
				clearWallpaperLocked(state);
				return;
			}
			state.wallpaperUrl = persisted.url();
			state.wallpaperScaleMode = persisted.scaleMode() != null ? persisted.scaleMode() : MediaScaleMode.FIT;
			state.wallpaperMedia = currentGalleryItemMatchingUrlLocked(state, state.wallpaperUrl)
					.map(GalleryItem::media)
					.orElse(null);
			wallpaperLocalMediaKey = persistedWallpaperItem != null ? persistedWallpaperItem.localMediaKey() : null;
			wallpaperUrl = state.wallpaperMedia == null ? state.wallpaperUrl : null;
			shouldRender = state.wallpaperMedia != null;
			state.version++;
		}
		if (wallpaperUrl != null) {
			scheduleWallpaperLoad(server, key, wallpaperUrl, wallpaperLocalMediaKey);
		}
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
	}

	private static void scheduleGalleryPreloadStatusRefreshes(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		ensureExecutors();
		for (int refreshIndex = 1; refreshIndex <= 24; refreshIndex++) {
			long delayMillis = refreshIndex * 1250L;
			mediaScheduler.schedule(() -> server.execute(() -> {
				MediaRuntimeState state = MEDIA_STATES.get(key);
				if (state == null) {
					return;
				}
				synchronized (state) {
					if (state.mode != ScreenViewMode.GALLERY || state.gallerySurfaceMode != GallerySurfaceMode.BROWSER) {
						return;
					}
				}
				requestRuntimeRender(server, key);
			}), delayMillis, TimeUnit.MILLISECONDS);
		}
	}

	private static void scheduleGalleryItemLoad(MinecraftServer server, ScreenRuntimeKey key, String title, String url, String localMediaKey, GalleryItemKind kind, boolean openWhenReady, int preferredIndex) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		TaskProgress progress = null;
		synchronized (state) {
			if (state.galleryLoadingUrls.contains(url)) {
				if (openWhenReady) {
					state.galleryPendingOpenUrl = url;
					state.galleryPendingOpenIndex = preferredIndex;
					state.loading = true;
					state.waitingForLink = false;
					state.overlayMode = MediaOverlayMode.CONTROLS;
					state.statusText = "BUFFERING";
					state.progress.setIndeterminate("LOADING");
					state.version++;
				}
				return;
			}
			state.galleryLoadingUrls.add(url);
			if (openWhenReady) {
				state.galleryPendingOpenUrl = url;
				state.galleryPendingOpenIndex = preferredIndex;
				state.loading = true;
				state.waitingForLink = false;
				state.overlayMode = MediaOverlayMode.CONTROLS;
				state.statusText = "BUFFERING";
				state.progress.setIndeterminate("LOADING");
				state.version++;
				progress = state.progress;
			}
		}
		if (openWhenReady) {
			requestRuntimeRender(server, key);
		}
		TaskProgress finalProgress = progress;
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						GalleryItemKind resolvedKind = effectiveGalleryItemKind(url, localMediaKey, kind);
						return new GalleryItemLoadResult(
								key,
								title,
								url,
								localMediaKey,
								resolvedKind,
								resolvedKind == GalleryItemKind.VIDEO
										? null
										: localMediaKey != null && !localMediaKey.isBlank()
										? MonitorMediaApp.loadSavedGalleryMedia(localMediaKey, finalProgress)
										: MonitorMediaApp.loadFromUrl(url, finalProgress),
								resolvedKind == GalleryItemKind.VIDEO
										? localMediaKey != null && !localMediaKey.isBlank()
										? MonitorMediaApp.loadSavedGalleryVideo(localMediaKey, finalProgress)
										: MonitorMediaApp.loadVideoFromUrl(url, finalProgress)
										: null,
								openWhenReady,
								preferredIndex,
								null
						);
					} catch (Exception exception) {
						return new GalleryItemLoadResult(key, title, url, localMediaKey, kind != null ? kind : GalleryItemKind.MEDIA, null, null, openWhenReady, preferredIndex, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyGalleryItemLoadResult(server, result)));
	}

	private static void applyGalleryItemLoadResult(MinecraftServer server, GalleryItemLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		ScreenComponent component = resolveScreenComponent(server, result.screenKey());
		UiLayout layout = component != null ? createUiLayout(component.width(), component.height()) : null;
		boolean shouldRender = false;
		boolean shouldAnimate = false;
		boolean shouldPersistLocalMedia = false;
		boolean shouldPersistLocalVideo = false;
		MonitorMediaApp.LoadedVideo loadedVideoToOpen = null;
		String loadedVideoTitle = null;
		int loadedVideoIndex = -1;
		synchronized (state) {
			state.galleryLoadingUrls.remove(result.url());
			boolean openWhenReady = result.openWhenReady()
					|| (Objects.equals(state.galleryPendingOpenUrl, result.url()) && state.galleryPendingOpenIndex >= 0);
			int preferredIndex = openWhenReady && !result.openWhenReady() ? state.galleryPendingOpenIndex : result.preferredIndex();
			if (Objects.equals(state.galleryPendingOpenUrl, result.url())) {
				state.galleryPendingOpenUrl = null;
				state.galleryPendingOpenIndex = -1;
			}
			int targetIndex = resolveGalleryItemIndex(state, result.url(), preferredIndex);
			if (result.loadedMedia() != null && targetIndex >= 0 && targetIndex < state.galleryItems.size()) {
				GalleryItem existing = state.galleryItems.get(targetIndex);
				state.galleryItems.set(
						targetIndex,
						new GalleryItem(
								(existing != null && existing.title() != null && !existing.title().isBlank()) ? existing.title() : result.title(),
								result.url(),
								result.localMediaKey() != null && !result.localMediaKey().isBlank() ? result.localMediaKey() : existing != null ? existing.localMediaKey() : null,
								result.loadedMedia(),
								result.loadedMedia().frameCount() > 0 ? result.loadedMedia().frame(0) : (existing != null ? existing.preview() : null),
								GalleryItemKind.MEDIA
						)
				);
				if (Objects.equals(state.wallpaperUrl, result.url())) {
					state.wallpaperMedia = result.loadedMedia();
					state.wallpaperFrameIndex = 0;
				}
				if (openWhenReady) {
					state.loading = false;
					state.statusText = "";
					state.progress.complete("READY");
					if (selectGalleryItemLocked(state, targetIndex, layout)) {
						state.overlayMode = MediaOverlayMode.CONTROLS;
						shouldAnimate = result.loadedMedia().animated() && result.loadedMedia().frameCount() > 1;
					}
				}
				state.version++;
				shouldRender = true;
				shouldPersistLocalMedia = (result.localMediaKey() == null || result.localMediaKey().isBlank());
			} else if (result.loadedVideo() != null && targetIndex >= 0 && targetIndex < state.galleryItems.size()) {
				GalleryItem existing = state.galleryItems.get(targetIndex);
				String resolvedTitle = (existing != null && existing.title() != null && !existing.title().isBlank())
						? existing.title()
						: result.title();
				state.galleryItems.set(
						targetIndex,
						new GalleryItem(
								resolvedTitle,
								result.url(),
								result.localMediaKey() != null && !result.localMediaKey().isBlank() ? result.localMediaKey() : existing != null ? existing.localMediaKey() : null,
								null,
								result.loadedVideo().preview() != null ? result.loadedVideo().preview() : (existing != null ? existing.preview() : null),
								GalleryItemKind.VIDEO
						)
				);
				if (openWhenReady) {
					loadedVideoToOpen = result.loadedVideo();
					loadedVideoTitle = resolvedTitle;
					loadedVideoIndex = targetIndex;
				} else {
					state.loading = false;
					state.statusText = "";
					state.progress.complete("READY");
				}
				state.version++;
				shouldRender = true;
				shouldPersistLocalVideo = (result.localMediaKey() == null || result.localMediaKey().isBlank());
			} else if (openWhenReady) {
				state.loading = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
				state.version++;
				shouldRender = true;
			}
		}
		if (shouldRender) {
			requestRuntimeRender(server, result.screenKey());
		}
		if (shouldAnimate) {
			scheduleNextMediaFrame(server, result.screenKey());
		}
		if (loadedVideoToOpen != null) {
			startDirectVideoPlayback(server, result.screenKey(), null, loadedVideoTitle, result.url(), loadedVideoToOpen, loadedVideoIndex, ScreenViewMode.GALLERY, false);
		}
		if (shouldPersistLocalMedia) {
			scheduleGalleryLocalMediaPersistence(server, result.screenKey(), result.url());
		}
		if (shouldPersistLocalVideo) {
			scheduleGalleryLocalVideoPersistence(server, result.screenKey(), result.url());
		}
	}

	private static void scheduleGalleryLocalMediaPersistence(MinecraftServer server, ScreenRuntimeKey key, String url) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new SavedGalleryMediaPersistResult(url, MonitorMediaApp.persistSavedGalleryMedia(url), null);
					} catch (Exception exception) {
						return new SavedGalleryMediaPersistResult(url, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyGalleryLocalMediaPersistence(server, key, result)));
	}

	private static void applyGalleryLocalMediaPersistence(MinecraftServer server, ScreenRuntimeKey key, SavedGalleryMediaPersistResult result) {
		if (server == null || key == null || result == null || result.savedMediaKey() == null || result.savedMediaKey().isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean changed = false;
		synchronized (state) {
			int index = resolveGalleryItemIndex(state, result.url(), -1);
			if (index < 0 || index >= state.galleryItems.size()) {
				return;
			}
			GalleryItem item = state.galleryItems.get(index);
			if (item == null || item.kind() != GalleryItemKind.MEDIA || item.localMediaKey() != null && !item.localMediaKey().isBlank()) {
				return;
			}
			state.galleryItems.set(
					index,
					new GalleryItem(item.title(), item.url(), result.savedMediaKey(), item.media(), item.preview(), item.kind())
			);
			changed = true;
		}
		if (changed) {
			persistGalleryState(server, key, state);
		}
	}

	private static void scheduleGalleryLocalVideoPersistence(MinecraftServer server, ScreenRuntimeKey key, String url) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new SavedGalleryMediaPersistResult(url, MonitorMediaApp.persistSavedGalleryVideo(url, null), null);
					} catch (Exception exception) {
						return new SavedGalleryMediaPersistResult(url, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyGalleryLocalVideoPersistence(server, key, result)));
	}

	private static void applyGalleryLocalVideoPersistence(MinecraftServer server, ScreenRuntimeKey key, SavedGalleryMediaPersistResult result) {
		if (server == null || key == null || result == null || result.savedMediaKey() == null || result.savedMediaKey().isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean changed = false;
		synchronized (state) {
			int index = resolveGalleryItemIndex(state, result.url(), -1);
			if (index < 0 || index >= state.galleryItems.size()) {
				return;
			}
			GalleryItem item = state.galleryItems.get(index);
			if (item == null
					|| effectiveGalleryItemKind(item) != GalleryItemKind.VIDEO
					|| item.localMediaKey() != null && !item.localMediaKey().isBlank()) {
				return;
			}
			state.galleryItems.set(
					index,
					new GalleryItem(item.title(), item.url(), result.savedMediaKey(), item.media(), item.preview(), GalleryItemKind.VIDEO)
			);
			changed = true;
		}
		if (changed) {
			persistGalleryState(server, key, state);
		}
	}

	private static int resolveGalleryItemIndex(MediaRuntimeState state, String url, int preferredIndex) {
		if (state == null || url == null || url.isBlank() || state.galleryItems.isEmpty()) {
			return -1;
		}
		if (preferredIndex >= 0 && preferredIndex < state.galleryItems.size()) {
			GalleryItem preferred = state.galleryItems.get(preferredIndex);
			if (preferred != null && Objects.equals(preferred.url(), url)) {
				return preferredIndex;
			}
		}
		for (int index = 0; index < state.galleryItems.size(); index++) {
			GalleryItem item = state.galleryItems.get(index);
			if (item != null && Objects.equals(item.url(), url)) {
				return index;
			}
		}
		return -1;
	}

	private static boolean looksLikeDirectVideoReference(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		String normalized = value.trim();
		int queryIndex = normalized.indexOf('?');
		if (queryIndex >= 0) {
			normalized = normalized.substring(0, queryIndex);
		}
		int fragmentIndex = normalized.indexOf('#');
		if (fragmentIndex >= 0) {
			normalized = normalized.substring(0, fragmentIndex);
		}
		int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
		String tail = slashIndex >= 0 && slashIndex + 1 < normalized.length() ? normalized.substring(slashIndex + 1) : normalized;
		int dotIndex = tail.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex >= tail.length() - 1) {
			return false;
		}
		String extension = tail.substring(dotIndex).toLowerCase(Locale.ROOT);
		return GALLERY_VIDEO_EXTENSIONS.contains(extension);
	}

	private static GalleryItemKind effectiveGalleryItemKind(String url, String localMediaKey, GalleryItemKind kind) {
		if (looksLikeDirectVideoReference(localMediaKey) || looksLikeDirectVideoReference(url)) {
			return GalleryItemKind.VIDEO;
		}
		if (kind == GalleryItemKind.YOUTUBE || MonitorYoutubeRelayClient.looksLikeYoutubeUrl(url)) {
			return GalleryItemKind.YOUTUBE;
		}
		return kind == GalleryItemKind.VIDEO ? GalleryItemKind.VIDEO : GalleryItemKind.MEDIA;
	}

	private static GalleryItemKind effectiveGalleryItemKind(GalleryItem item) {
		if (item == null) {
			return GalleryItemKind.MEDIA;
		}
		return effectiveGalleryItemKind(item.url(), item.localMediaKey(), item.kind());
	}

	private static GalleryItemKind effectiveGalleryItemKind(PersistedGalleryItem item) {
		if (item == null) {
			return GalleryItemKind.MEDIA;
		}
		return effectiveGalleryItemKind(item.url(), item.localMediaKey(), item.kind());
	}

	private static GalleryCacheCandidate galleryCacheCandidate(GalleryItem item) {
		if (item == null) {
			return null;
		}
		String url = item.url() != null ? item.url().trim() : "";
		String localMediaKey = item.localMediaKey() != null ? item.localMediaKey().trim() : "";
		if (url.isBlank() && localMediaKey.isBlank()) {
			return null;
		}
		return new GalleryCacheCandidate(url, localMediaKey, effectiveGalleryItemKind(item));
	}

	private static List<GalleryCacheCandidate> galleryCacheCandidatesForRemovedComponent(ScreenComponent component, MediaRuntimeState runtimeState) {
		Set<GalleryCacheCandidate> candidates = new LinkedHashSet<>();
		if (runtimeState != null) {
			synchronized (runtimeState) {
				for (GalleryItem item : runtimeState.galleryItems) {
					GalleryCacheCandidate candidate = galleryCacheCandidate(item);
					if (candidate != null) {
						candidates.add(candidate);
					}
				}
			}
		}
		if (component != null) {
			for (ItemFrame frame : component.frameCoords().keySet()) {
				List<PersistedGalleryItem> persistedItems = readPersistedGalleryState(frame.getItem());
				if (persistedItems.isEmpty()) {
					continue;
				}
				for (PersistedGalleryItem item : persistedItems) {
					GalleryCacheCandidate candidate = galleryCacheCandidate(item);
					if (candidate != null) {
						candidates.add(candidate);
					}
				}
				break;
			}
		}
		return List.copyOf(candidates);
	}

	private static GalleryCacheCandidate galleryCacheCandidate(PersistedGalleryItem item) {
		if (item == null) {
			return null;
		}
		String url = item.url() != null ? item.url().trim() : "";
		String localMediaKey = item.localMediaKey() != null ? item.localMediaKey().trim() : "";
		if (url.isBlank() && localMediaKey.isBlank()) {
			return null;
		}
		return new GalleryCacheCandidate(url, localMediaKey, effectiveGalleryItemKind(item));
	}

	private static void scheduleGalleryCacheRelease(MinecraftServer server, List<GalleryCacheCandidate> candidates, ScreenRuntimeKey excludedRuntimeKey) {
		if (server == null || candidates == null || candidates.isEmpty()) {
			return;
		}
		List<GalleryCacheCandidate> normalizedCandidates = candidates.stream()
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		if (normalizedCandidates.isEmpty()) {
			return;
		}
		GalleryCacheReferenceSnapshot refs = collectGalleryCacheReferences(server, excludedRuntimeKey);
		ensureExecutors();
		CompletableFuture.runAsync(() -> applyGalleryCacheRelease(normalizedCandidates, refs), mediaIoExecutor);
	}

	private static GalleryCacheReferenceSnapshot collectGalleryCacheReferences(MinecraftServer server, ScreenRuntimeKey excludedRuntimeKey) {
		Set<String> localMediaKeys = new HashSet<>();
		Set<String> galleryMediaUrls = new HashSet<>();
		Set<String> galleryYoutubeUrls = new HashSet<>();
		Set<String> activeMediaUrls = new HashSet<>();
		Set<String> activeYoutubeUrls = new HashSet<>();
		for (Map.Entry<ScreenRuntimeKey, MediaRuntimeState> entry : List.copyOf(MEDIA_STATES.entrySet())) {
			if (entry == null || entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			if (excludedRuntimeKey != null && excludedRuntimeKey.equals(entry.getKey())) {
				continue;
			}
			synchronized (entry.getValue()) {
				collectGalleryCacheReferencesLocked(entry.getValue(), localMediaKeys, galleryMediaUrls, galleryYoutubeUrls, activeMediaUrls, activeYoutubeUrls);
			}
		}
		for (MonitorLevelState levelState : LEVEL_STATES.values()) {
			if (levelState == null) {
				continue;
			}
			for (ScreenComponent component : List.copyOf(levelState.components().values())) {
				if (component == null || (excludedRuntimeKey != null && excludedRuntimeKey.equals(component.runtimeKey()))) {
					continue;
				}
				for (ItemFrame frame : component.frameCoords().keySet()) {
					List<PersistedGalleryItem> persistedItems = readPersistedGalleryState(frame.getItem());
					if (persistedItems.isEmpty()) {
						continue;
					}
					collectPersistedGalleryCacheReferences(persistedItems, localMediaKeys, galleryMediaUrls, galleryYoutubeUrls);
					break;
				}
			}
		}
		return new GalleryCacheReferenceSnapshot(
				Set.copyOf(localMediaKeys),
				Set.copyOf(galleryMediaUrls),
				Set.copyOf(galleryYoutubeUrls),
				Set.copyOf(activeMediaUrls),
				Set.copyOf(activeYoutubeUrls)
		);
	}

	private static void collectGalleryCacheReferencesLocked(
			MediaRuntimeState state,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryYoutubeUrls,
			Set<String> activeMediaUrls,
			Set<String> activeYoutubeUrls
	) {
		if (state == null) {
			return;
		}
		for (GalleryItem item : state.galleryItems) {
			collectGalleryCacheReference(item, localMediaKeys, galleryMediaUrls, galleryYoutubeUrls);
		}
		collectActiveMediaUrl(state.sourceUrl, activeMediaUrls, activeYoutubeUrls);
		collectActiveMediaUrl(state.downloadTargetUrl, activeMediaUrls, activeYoutubeUrls);
		for (String url : state.galleryLoadingUrls) {
			collectActiveMediaUrl(url, activeMediaUrls, activeYoutubeUrls);
		}
		for (YoutubeQueueItem item : state.youtubeQueue) {
			if (item == null) {
				continue;
			}
			collectActiveMediaUrl(item.url(), activeMediaUrls, activeYoutubeUrls);
		}
		for (String url : state.retainedYoutubePreloadUrls) {
			collectActiveMediaUrl(url, activeMediaUrls, activeYoutubeUrls);
		}
	}

	private static void collectPersistedGalleryCacheReferences(
			List<PersistedGalleryItem> persistedItems,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryYoutubeUrls
	) {
		if (persistedItems == null || persistedItems.isEmpty()) {
			return;
		}
		for (PersistedGalleryItem item : persistedItems) {
			collectGalleryCacheReference(item, localMediaKeys, galleryMediaUrls, galleryYoutubeUrls);
		}
	}

	private static void collectGalleryCacheReference(
			GalleryItem item,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryYoutubeUrls
	) {
		if (item == null) {
			return;
		}
		collectGalleryCacheReference(item.url(), item.localMediaKey(), item.kind(), localMediaKeys, galleryMediaUrls, galleryYoutubeUrls);
	}

	private static void collectGalleryCacheReference(
			PersistedGalleryItem item,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryYoutubeUrls
	) {
		if (item == null) {
			return;
		}
		collectGalleryCacheReference(item.url(), item.localMediaKey(), item.kind(), localMediaKeys, galleryMediaUrls, galleryYoutubeUrls);
	}

	private static void collectGalleryCacheReference(
			String url,
			String localMediaKey,
			GalleryItemKind kind,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryYoutubeUrls
	) {
		String normalizedUrl = url != null ? url.trim() : "";
		String normalizedLocalMediaKey = localMediaKey != null ? localMediaKey.trim() : "";
		GalleryItemKind resolvedKind = effectiveGalleryItemKind(normalizedUrl, normalizedLocalMediaKey, kind);
		if (resolvedKind == GalleryItemKind.YOUTUBE) {
			if (!normalizedUrl.isBlank()) {
				galleryYoutubeUrls.add(normalizedUrl);
			}
			return;
		}
		if (!normalizedUrl.isBlank()) {
			galleryMediaUrls.add(normalizedUrl);
		}
		if (!normalizedLocalMediaKey.isBlank()) {
			localMediaKeys.add(normalizedLocalMediaKey);
		}
	}

	private static void collectActiveMediaUrl(String url, Set<String> activeMediaUrls, Set<String> activeYoutubeUrls) {
		if (url == null || url.isBlank()) {
			return;
		}
		String normalizedUrl = url.trim();
		if (MonitorYoutubeRelayClient.looksLikeYoutubeUrl(normalizedUrl)) {
			activeYoutubeUrls.add(normalizedUrl);
		} else {
			activeMediaUrls.add(normalizedUrl);
		}
	}

	private static void applyGalleryCacheRelease(List<GalleryCacheCandidate> candidates, GalleryCacheReferenceSnapshot refs) {
		if (candidates == null || candidates.isEmpty() || refs == null) {
			return;
		}
		Set<String> deletedLocalMediaKeys = new HashSet<>();
		Set<String> deletedMediaUrls = new HashSet<>();
		Set<String> deletedYoutubeUrls = new HashSet<>();
		for (GalleryCacheCandidate candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			String url = candidate.url() != null ? candidate.url().trim() : "";
			String localMediaKey = candidate.localMediaKey() != null ? candidate.localMediaKey().trim() : "";
			if (candidate.kind() == GalleryItemKind.YOUTUBE) {
				if (url.isBlank()
						|| refs.galleryYoutubeUrls().contains(url)
						|| refs.activeYoutubeUrls().contains(url)
						|| !deletedYoutubeUrls.add(url)) {
					continue;
				}
				MonitorYoutubeRelayClient.deletePersistentQueueEntry(url);
				continue;
			}
			if (!localMediaKey.isBlank()
					&& !refs.localMediaKeys().contains(localMediaKey)
					&& deletedLocalMediaKeys.add(localMediaKey)) {
				MonitorMediaApp.deleteSavedGalleryMedia(localMediaKey);
			}
			if (!url.isBlank()
					&& !refs.galleryMediaUrls().contains(url)
					&& !refs.activeMediaUrls().contains(url)
					&& deletedMediaUrls.add(url)) {
				MonitorMediaApp.deleteCachedUrl(url);
			}
		}
	}

	private static void persistGalleryState(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component == null) {
			return;
		}
		List<GalleryItem> galleryItems;
		PersistedWallpaperState wallpaperState;
		synchronized (state) {
			galleryItems = List.copyOf(state.galleryItems);
			wallpaperState = persistedWallpaperStateLocked(state);
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			ItemStack stack = frame.getItem();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			ItemStack updated = stack.copy();
			writePersistedGalleryState(updated, galleryItems, wallpaperState);
			frame.setItem(updated, false);
		}
	}

	private static void scheduleWallpaperLoad(MinecraftServer server, ScreenRuntimeKey key, String url, String localMediaKey) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			if (state.wallpaperLoading || (state.wallpaperMedia != null && Objects.equals(state.wallpaperUrl, url))) {
				return;
			}
			state.wallpaperLoading = true;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new WallpaperLoadResult(
								key,
								url,
								localMediaKey,
								localMediaKey != null && !localMediaKey.isBlank()
										? MonitorMediaApp.loadSavedGalleryMedia(localMediaKey, null)
										: MonitorMediaApp.loadFromUrl(url),
								null
						);
					} catch (Exception exception) {
						return new WallpaperLoadResult(key, url, localMediaKey, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyWallpaperLoadResult(server, result)));
	}

	private static void applyWallpaperLoadResult(MinecraftServer server, WallpaperLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		boolean shouldRender = false;
		boolean shouldAnimate = false;
		synchronized (state) {
			state.wallpaperLoading = false;
			if (!Objects.equals(state.wallpaperUrl, result.url())) {
				return;
			}
			if (result.loadedMedia() == null) {
				Lg2.LOGGER.debug("Failed to load monitor wallpaper {}: {}", result.url(), result.error());
				return;
			}
			state.wallpaperMedia = result.loadedMedia();
			state.wallpaperFrameIndex = 0;
			int galleryIndex = resolveGalleryItemIndex(state, result.url(), -1);
			if (galleryIndex >= 0 && galleryIndex < state.galleryItems.size()) {
				GalleryItem existing = state.galleryItems.get(galleryIndex);
				if (existing != null && existing.kind() == GalleryItemKind.MEDIA) {
					state.galleryItems.set(
							galleryIndex,
							new GalleryItem(
									existing.title(),
									existing.url(),
									existing.localMediaKey(),
									result.loadedMedia(),
									result.loadedMedia().frameCount() > 0 ? result.loadedMedia().frame(0) : existing.preview(),
									existing.kind()
							)
					);
				}
			}
			state.version++;
			shouldRender = true;
			shouldAnimate = wallpaperVisibleForCurrentViewLocked(state) && result.loadedMedia().animated();
		}
		if (shouldRender) {
			requestRuntimeRender(server, result.screenKey());
		}
		if (shouldAnimate) {
			resumeMediaPlaybackIfNeeded(server, result.screenKey());
		}
	}

	private static void applyYoutubeQueueResolveResult(MinecraftServer server, YoutubeQueueResolveResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		ServerPlayer requester = result.requesterUuid() != null ? server.getPlayerList().getPlayer(result.requesterUuid()) : null;
		boolean shouldStartPlayback = false;
		int startQueueIndex = -1;
		int addedCount = 0;
		List<String> releasedQueueUrls = List.of();
		List<String> releasedMusicQueueUrls = List.of();
		YoutubeQueuePreloadDiff preloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		YoutubeMusicQueuePreloadDiff musicPreloadDiff = YoutubeMusicQueuePreloadDiff.EMPTY;

		synchronized (state) {
			state.mode = result.mode();
			state.waitingForLink = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			state.loading = false;

			if (result.queueResponse() == null || result.queueResponse().entries() == null || result.queueResponse().entries().isEmpty()) {
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
				state.version++;
			} else {
				List<MonitorYoutubeRelayClient.QueueEntry> resolvedEntries = result.queueResponse().entries();
				addedCount = resolvedEntries.size();
				if (result.action() == YoutubeLinkRequestAction.REPLACE_QUEUE) {
					releasedQueueUrls = retainedYoutubePreloadUrlsLocked(state);
					releasedMusicQueueUrls = retainedYoutubeMusicPreloadUrlsLocked(state);
					state.retainedYoutubePreloadUrls.clear();
					state.retainedYoutubeMusicUrls.clear();
					cancelPlaybackLocked(state);
					clearTransientPlaybackStateLocked(state, true);
				} else {
					ensureYoutubeQueueCurrentEntryLocked(state);
				}
				int appendStartIndex = state.youtubeQueue.size();
				for (MonitorYoutubeRelayClient.QueueEntry entry : resolvedEntries) {
					if (entry == null || entry.url() == null || entry.url().isBlank()) {
						continue;
					}
					String title = entry.title() == null || entry.title().isBlank()
							? (result.mode() == ScreenViewMode.YOUTUBE_MUSIC ? "Track" : "YouTube")
							: entry.title();
					state.youtubeQueue.add(new YoutubeQueueItem(title, entry.url()));
				}
				if (result.action() == YoutubeLinkRequestAction.REPLACE_QUEUE) {
					state.youtubeQueueIndex = state.youtubeQueue.isEmpty() ? -1 : 0;
					state.youtubeQueueScroll = 0;
					state.youtubeQueueOpen = !state.youtubeQueue.isEmpty();
					shouldStartPlayback = !state.youtubeQueue.isEmpty();
					startQueueIndex = 0;
				} else if (!state.youtubeQueue.isEmpty() && (state.sourceUrl == null || state.sourceUrl.isBlank())) {
					state.youtubeQueueIndex = Math.max(0, Math.min(appendStartIndex, state.youtubeQueue.size() - 1));
					state.youtubeQueueOpen = true;
					shouldStartPlayback = true;
					startQueueIndex = state.youtubeQueueIndex;
				} else {
					state.youtubeQueueOpen = true;
					state.youtubeQueueScroll = Math.max(0, state.youtubeQueue.size() - youtubeQueueVisibleRowsPreview(state));
				}
				state.statusText = "";
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
				musicPreloadDiff = syncYoutubeMusicQueuePreloadsLocked(state);
				state.version++;
			}
		}
		releaseYoutubeQueuePreloads(releasedQueueUrls);
		releaseYoutubeMusicQueuePreloads(releasedMusicQueueUrls);
		applyYoutubeQueuePreloadDiff(preloadDiff);
		applyYoutubeMusicQueuePreloadDiff(musicPreloadDiff);

		if (result.requesterUuid() != null) {
			IN_FLIGHT_MEDIA_LINKS.remove(result.requesterUuid());
		}
		if (requester != null && (result.queueResponse() == null || result.queueResponse().entries() == null || result.queueResponse().entries().isEmpty())) {
			ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
			requester.displayClientMessage(Component.empty(), true);
		}
		requestRuntimeRender(server, result.screenKey());
		if (shouldStartPlayback) {
			if (result.mode() == ScreenViewMode.YOUTUBE_MUSIC) {
				startYoutubeMusicQueuePlayback(server, result.screenKey(), result.requesterUuid(), startQueueIndex);
			} else {
				startYoutubeQueuePlayback(server, result.screenKey(), result.requesterUuid(), startQueueIndex);
			}
			return;
		}
		if (requester != null && addedCount > 0) {
			ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
			requester.displayClientMessage(Component.empty(), true);
		}
	}

	private static void startYoutubeQueuePlayback(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid, int queueIndex) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		String url;
		YoutubeQueuePreloadDiff preloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		YoutubeMusicQueuePreloadDiff musicPreloadDiff = YoutubeMusicQueuePreloadDiff.EMPTY;
		synchronized (state) {
			int resolvedIndex = normalizeYoutubeQueueIndexLocked(state, queueIndex);
			if (resolvedIndex < 0) {
				state.loading = false;
				state.statusText = "";
				state.progress.clear();
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
				musicPreloadDiff = syncYoutubeMusicQueuePreloadsLocked(state);
				state.version++;
				url = null;
			} else {
				YoutubeQueueItem item = state.youtubeQueue.get(resolvedIndex);
				if (item == null || item.url() == null || item.url().isBlank()) {
					return;
				}
				cancelPlaybackLocked(state);
				clearYoutubePlaybackLocked(state);
				state.mode = ScreenViewMode.YOUTUBE;
				state.streamKind = PlaybackStreamKind.YOUTUBE;
				state.youtubeReturnToGallery = false;
				state.sourceUrl = item.url();
				state.youtubeQueueIndex = resolvedIndex;
				state.youtubeQueueOpen = false;
				state.waitingForLink = false;
				state.loading = true;
				state.userPaused = false;
				state.statusText = "BUFFERING";
				state.progress.setIndeterminate("LOADING");
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
				musicPreloadDiff = syncYoutubeMusicQueuePreloadsLocked(state);
				state.version++;
				url = item.url();
			}
		}
		applyYoutubeQueuePreloadDiff(preloadDiff);
		applyYoutubeMusicQueuePreloadDiff(musicPreloadDiff);
		if (url == null || url.isBlank()) {
			requestRuntimeRender(server, key);
			return;
		}
		requestRuntimeRender(server, key);
		resumeMediaPlaybackIfNeeded(server, key);
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new YoutubeLoadResult(
								key,
								requesterUuid,
								url,
								ScreenViewMode.YOUTUBE,
								PlaybackStreamKind.YOUTUBE,
								MonitorYoutubeRelayClient.load(relaySessionId(key), url, state.progress),
								null
						);
					} catch (Exception exception) {
						return new YoutubeLoadResult(key, requesterUuid, url, ScreenViewMode.YOUTUBE, PlaybackStreamKind.YOUTUBE, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
	}

	private static void startYoutubeMusicQueuePlayback(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid, int queueIndex) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		String url;
		String title;
		int resolvedIndex;
		YoutubeQueuePreloadDiff preloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		YoutubeMusicQueuePreloadDiff musicPreloadDiff = YoutubeMusicQueuePreloadDiff.EMPTY;
		synchronized (state) {
			resolvedIndex = normalizeYoutubeQueueIndexLocked(state, queueIndex);
			if (resolvedIndex < 0) {
				state.loading = false;
				state.statusText = "";
				state.progress.clear();
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
				musicPreloadDiff = syncYoutubeMusicQueuePreloadsLocked(state);
				state.version++;
				url = null;
				title = null;
			} else {
				YoutubeQueueItem item = state.youtubeQueue.get(resolvedIndex);
				if (item == null || item.url() == null || item.url().isBlank()) {
					return;
				}
				BufferedImage preservedBackdrop = currentYoutubeMusicBackdropLocked(state);
				cancelPlaybackLocked(state);
				clearYoutubePlaybackLocked(state);
				state.mode = ScreenViewMode.YOUTUBE_MUSIC;
				state.streamKind = PlaybackStreamKind.NONE;
				state.loadingBackdropFrame = preservedBackdrop;
				state.sourceUrl = item.url();
				state.mediaTitle = item.title() == null || item.title().isBlank() ? "Track" : item.title();
				state.mediaSubtitle = "";
				state.youtubeQueueIndex = resolvedIndex;
				state.youtubeQueueOpen = false;
				state.waitingForLink = false;
				state.loading = true;
				state.userPaused = false;
				state.statusText = "BUFFERING";
				state.progress.setIndeterminate("LOADING");
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
				musicPreloadDiff = syncYoutubeMusicQueuePreloadsLocked(state);
				state.version++;
				url = item.url();
				title = state.mediaTitle;
			}
		}
		applyYoutubeQueuePreloadDiff(preloadDiff);
		applyYoutubeMusicQueuePreloadDiff(musicPreloadDiff);
		if (url == null || url.isBlank()) {
			requestRuntimeRender(server, key);
			return;
		}
		requestRuntimeRender(server, key);
		resumeMediaPlaybackIfNeeded(server, key);
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						MonitorYoutubeMusicCache.LoadedTrack track = MonitorYoutubeMusicCache.load(url, state.progress);
						return new YoutubeMusicLoadResult(
								key,
								requesterUuid,
								url,
								track.title(),
								track.artist(),
								track.video(),
								resolvedIndex,
								null
						);
					} catch (Exception exception) {
						return new YoutubeMusicLoadResult(key, requesterUuid, url, title, "", null, resolvedIndex, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeMusicLoadResult(server, result)));
	}

	private static void applyYoutubeMusicLoadResult(MinecraftServer server, YoutubeMusicLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		if (result.loadedVideo() == null) {
			MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
			if (state != null) {
				synchronized (state) {
					state.loading = false;
					state.statusText = sanitizeMediaError(result.error());
					state.progress.clear();
					state.version++;
				}
			}
			requestRuntimeRender(server, result.screenKey());
			return;
		}
		startDirectVideoPlayback(
				server,
				result.screenKey(),
				result.requesterUuid(),
				result.title(),
				result.artist(),
				result.url(),
				result.loadedVideo(),
				result.queueIndex(),
				ScreenViewMode.YOUTUBE_MUSIC,
				true
		);
	}

	private static void closeMediaSession(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		MediaRuntimeState removed = MEDIA_STATES.remove(key);
		String relaySessionId = null;
		List<String> releasedQueueUrls = List.of();
		List<String> releasedMusicQueueUrls = List.of();
		if (removed != null) {
			synchronized (removed) {
				cancelPlaybackLocked(removed);
				removed.progress.clear();
				releasedQueueUrls = retainedYoutubePreloadUrlsLocked(removed);
				releasedMusicQueueUrls = retainedYoutubeMusicPreloadUrlsLocked(removed);
				removed.retainedYoutubePreloadUrls.clear();
				removed.retainedYoutubeMusicUrls.clear();
				if (isStreamPlaybackLocked(removed) || removed.relaySessionId != null) {
					relaySessionId = removed.relaySessionId;
				}
			}
		}
		releaseYoutubeQueuePreloads(releasedQueueUrls);
		releaseYoutubeMusicQueuePreloads(releasedMusicQueueUrls);
		releaseYoutubeRelaySession(relaySessionId);
		clearMediaSessionBindings(server, key);
	}

	private static void deactivateMediaSession(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		String relaySessionId = null;
		List<String> releasedQueueUrls = List.of();
		List<String> releasedMusicQueueUrls = List.of();
		if (state != null) {
			synchronized (state) {
				cancelPlaybackLocked(state);
				state.progress.clear();
				releasedQueueUrls = retainedYoutubePreloadUrlsLocked(state);
				releasedMusicQueueUrls = retainedYoutubeMusicPreloadUrlsLocked(state);
				state.retainedYoutubePreloadUrls.clear();
				state.retainedYoutubeMusicUrls.clear();
				if (state.relaySessionId != null && !state.relaySessionId.isBlank()) {
					relaySessionId = state.relaySessionId;
				}
				clearTransientPlaybackStateLocked(state, true);
				state.activeRenderJobs = 0;
				state.lastDispatchKey = null;
				state.rerenderRequested = false;
				state.overlayMode = MediaOverlayMode.VIEW;
				state.statusText = "";
				state.version++;
			}
		}
		releaseYoutubeQueuePreloads(releasedQueueUrls);
		releaseYoutubeMusicQueuePreloads(releasedMusicQueueUrls);
		releaseYoutubeRelaySession(relaySessionId);
		clearMediaSessionBindings(server, key);
	}

	private static void releaseYoutubeRelaySession(String relaySessionId) {
		if (relaySessionId == null || relaySessionId.isBlank()) {
			return;
		}
		ensureExecutors();
		String finalRelaySessionId = relaySessionId;
		CompletableFuture.runAsync(() -> {
			try {
				MonitorYoutubeRelayClient.close(finalRelaySessionId);
			} catch (Exception ignored) {
			}
		}, mediaIoExecutor);
	}

	private static void clearMediaSessionBindings(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		PENDING_MEDIA_LINKS.entrySet().removeIf(entry -> entry.getValue().screenKey().equals(key));
		IN_FLIGHT_MEDIA_LINKS.entrySet().removeIf(entry -> entry.getValue().screenKey().equals(key));
		for (Map.Entry<UUID, ScreenRuntimeKey> entry : List.copyOf(ACTIVE_MEDIA_ACTIONBARS.entrySet())) {
			if (entry.getValue().equals(key)) {
				ACTIVE_MEDIA_ACTIONBARS.remove(entry.getKey());
				if (server != null) {
					clearMediaActionbar(server, entry.getKey());
				}
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

	private static long bumpAudioSyncTokenLocked(MediaRuntimeState state) {
		if (state == null) {
			return 0L;
		}
		state.audioSyncToken++;
		if (state.audioSyncToken == Long.MIN_VALUE) {
			state.audioSyncToken = 1L;
		}
		return state.audioSyncToken;
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
		if (server == null) {
			return;
		}
		for (Map.Entry<UUID, ScreenRuntimeKey> entry : List.copyOf(ACTIVE_MEDIA_ACTIONBARS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			MediaRuntimeState state = MEDIA_STATES.get(entry.getValue());
			if (player == null || state == null) {
				if (player != null) {
					player.displayClientMessage(Component.empty(), true);
				}
				IN_FLIGHT_MEDIA_LINKS.remove(entry.getKey());
				ACTIVE_MEDIA_ACTIONBARS.remove(entry.getKey());
				continue;
			}
			Component message = null;
			PendingMediaLinkRequest pending = PENDING_MEDIA_LINKS.get(entry.getKey());
			if (pending != null && pending.screenKey().equals(entry.getValue())) {
				message = linkPromptMessage(pending.mode(), player);
			}
			InFlightMediaLinkRequest inFlight = IN_FLIGHT_MEDIA_LINKS.get(entry.getKey());
			if (message == null && inFlight != null && inFlight.screenKey().equals(entry.getValue())) {
				message = loadingMessage(inFlight.mode(), player);
			}
			synchronized (state) {
				if (message == null && state.waitingForLink) {
					message = linkPromptMessage(state.mode, player);
				} else if (message == null && state.loading) {
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
		List<String> releasedQueueUrls = List.of();
		List<String> releasedMusicQueueUrls = List.of();
		MonitorMediaApp.LoadedVideo directVideo = result.loadedVideo();
		String directVideoTitle = null;

		synchronized (state) {
			ScreenViewMode previousMode = state.mode;
				state.mode = ScreenViewMode.GALLERY;
				if (isYoutubeFamilyMode(previousMode) || !state.youtubeQueue.isEmpty()) {
					releasedQueueUrls = retainedYoutubePreloadUrlsLocked(state);
					releasedMusicQueueUrls = retainedYoutubeMusicPreloadUrlsLocked(state);
					state.retainedYoutubePreloadUrls.clear();
					state.retainedYoutubeMusicUrls.clear();
					clearYoutubePlaybackLocked(state);
					clearYoutubeQueueLocked(state);
				}
			state.loading = false;
			state.waitingForLink = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			cancelPlaybackLocked(state);

			if (result.loadedMedia() != null) {
				String title = galleryItemTitle(result.url(), result.loadedMedia(), state.galleryItems.size() + 1);
				openTransientGalleryItemLocked(state, title, result.url(), result.loadedMedia());
				state.statusText = "";
				state.progress.complete("READY");
				animated = result.loadedMedia().animated();
				schedulePlayback = animated && result.loadedMedia().frameCount() > 1;
			} else if (directVideo != null) {
				directVideoTitle = galleryItemTitle(result.url(), null, state.galleryItems.size() + 1);
				state.statusText = "";
			} else {
				state.userPaused = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
			}
			state.version++;
		}
		releaseYoutubeQueuePreloads(releasedQueueUrls);
		releaseYoutubeMusicQueuePreloads(releasedMusicQueueUrls);

		if (result.requesterUuid() != null) {
			IN_FLIGHT_MEDIA_LINKS.remove(result.requesterUuid());
		}
		if (requester != null) {
			ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
			requester.displayClientMessage(Component.empty(), true);
			if (result.loadedMedia() != null) {
				requester.sendSystemMessage(mediaLoadedMessage(requester, animated));
			} else if (directVideo != null) {
				requester.sendSystemMessage(literal("Видео открыто"));
			} else {
				requester.sendSystemMessage(mediaLoadFailedMessage(requester, sanitizeMediaError(result.error())));
			}
		}
		requestRuntimeRender(server, result.screenKey());
		if (directVideo != null) {
			startDirectVideoPlayback(server, result.screenKey(), result.requesterUuid(), directVideoTitle, result.url(), directVideo, -1, ScreenViewMode.GALLERY, false);
			return;
		}
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
		boolean shouldFadeProgress = false;

		synchronized (state) {
			boolean youtubeStream = result.streamKind() == PlaybackStreamKind.YOUTUBE;
			boolean galleryBacked = result.targetMode() == ScreenViewMode.GALLERY
					&& state.gallerySurfaceMode == GallerySurfaceMode.PLAYER
					&& result.url() != null
					&& !result.url().isBlank()
					&& (youtubeStream
					? hasGalleryItemForUrlLocked(state, result.url())
					: state.galleryIndex >= 0);
			clearTransientPlaybackStateLocked(state, false);
			state.mode = galleryBacked ? ScreenViewMode.GALLERY : result.targetMode();
			state.streamKind = result.streamKind();
			if (galleryBacked) {
				state.gallerySurfaceMode = GallerySurfaceMode.PLAYER;
				if (youtubeStream) {
					state.galleryIndex = resolveGalleryItemIndex(state, result.url(), state.galleryIndex);
				}
			}
			state.waitingForLink = false;
			state.overlayMode = MediaOverlayMode.CONTROLS;
			cancelPlaybackLocked(state);

			if (result.loadResponse() != null) {
				state.sourceUrl = result.url();
				state.relaySessionId = result.loadResponse().sessionId();
				state.audioStreamUrl = result.loadResponse().audioStreamUrl();
				state.mediaTitle = result.loadResponse().title();
				state.durationMs = result.loadResponse().durationMs();
				state.positionMs = result.loadResponse().initialPositionMs();
				state.bufferedStartMs = result.loadResponse().bufferedStartMs();
				state.bufferedEndMs = result.loadResponse().bufferedEndMs();
				if (result.loadResponse().initialFrame() != null) {
					state.streamFrame = result.loadResponse().initialFrame();
					state.youtubeFrameSequence = result.loadResponse().initialFrameSequence();
				}
				state.liveStream = result.loadResponse().live();
				state.audioPlaceholder = true;
				state.loading = !result.loadResponse().ready();
				state.userPaused = false;
				state.statusText = result.loadResponse().status();
				bumpAudioSyncTokenLocked(state);
				if (result.loadResponse().ready()) {
					state.progress.complete("READY");
					shouldFadeProgress = true;
				} else {
					state.progress.setIndeterminate(result.loadResponse().live() ? "LIVE" : "LOADING");
				}
				if (youtubeStream && isYoutubeFamilyMode(state.mode)) {
					ensureYoutubeQueueCurrentEntryLocked(state);
				}
			} else {
				state.loading = false;
				state.userPaused = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
			}
			state.version++;
		}

		if (result.requesterUuid() != null) {
			IN_FLIGHT_MEDIA_LINKS.remove(result.requesterUuid());
		}
		if (requester != null) {
			ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
			requester.displayClientMessage(Component.empty(), true);
			if (result.loadResponse() != null) {
				if (result.streamKind() == PlaybackStreamKind.YOUTUBE) {
					requester.sendSystemMessage(
							result.targetMode() == ScreenViewMode.YOUTUBE_MUSIC
									? youtubeMusicLoadedMessage(requester)
									: youtubeLoadedMessage(requester, result.loadResponse().live())
					);
				} else {
					requester.sendSystemMessage(
							result.targetMode() == ScreenViewMode.YOUTUBE_MUSIC
									? youtubeMusicLoadedMessage(requester)
									: literal("Видео подключено")
					);
				}
			} else {
				requester.sendSystemMessage(mediaLoadFailedMessage(requester, sanitizeMediaError(result.error())));
			}
		}
		requestRuntimeRender(server, result.screenKey());
		refreshConnectedSpeakersNow(server, result.screenKey());
		if (shouldFadeProgress) {
			scheduleProgressFadeRenders(server, result.screenKey());
		}
		if (result.loadResponse() != null) {
			scheduleYoutubeRefresh(server, result.screenKey(), 0L);
		}
	}

	private static boolean wallpaperVisibleForCurrentViewLocked(MediaRuntimeState state) {
		if (state == null || state.wallpaperMedia == null || state.wallpaperUrl == null || state.wallpaperUrl.isBlank()) {
			return false;
		}
		return wallpaperVisibleForViewMode(state.mode, state);
	}

	private static boolean wallpaperVisibleForViewMode(ScreenViewMode viewMode, MediaRuntimeState state) {
		if (state == null || state.wallpaperMedia == null || state.wallpaperUrl == null || state.wallpaperUrl.isBlank()) {
			return false;
		}
		if (viewMode == null || viewMode == ScreenViewMode.HOME) {
			return true;
		}
		if (!isPlayerMode(viewMode)) {
			return true;
		}
		if (viewMode == ScreenViewMode.YOUTUBE_MUSIC
				&& state.loading
				&& state.streamFrame == null
				&& state.loadingBackdropFrame == null) {
			return true;
		}
		if (viewMode == ScreenViewMode.GALLERY) {
			return state.gallerySurfaceMode == GallerySurfaceMode.BROWSER;
		}
		return state.sourceUrl == null
				&& state.relaySessionId == null
				&& !state.loading;
	}

	private static boolean shouldPreserveWallpaperPlaybackOnTransitionLocked(MediaRuntimeState state, ScreenViewMode nextMode) {
		return state != null
				&& wallpaperVisibleForCurrentViewLocked(state)
				&& wallpaperVisibleForViewMode(nextMode, state);
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

	private static ScheduledFuture<?> scheduleWallpaperVisibilityRecheck(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return null;
		}
		return mediaScheduler.schedule(() -> server.execute(() -> refreshWallpaperPlayback(server, key)), WALLPAPER_IDLE_VISIBILITY_RECHECK_MS, TimeUnit.MILLISECONDS);
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
				if (hasActiveStreamPlaybackLocked(state)) {
					if (state.waitingForLink) {
						return;
					}
					if (state.relaySessionId == null) {
						if (!state.loading) {
							return;
						}
						state.playbackFuture = mediaScheduler.schedule(() -> refreshLoadingUi(server, key), youtubePollActiveIntervalMs(), TimeUnit.MILLISECONDS);
						return;
					}
					long delayMillis = effectiveYoutubePollDelayMs(server, key, isPlaybackPausedLocked(state));
					state.playbackFuture = mediaScheduler.schedule(() -> refreshYoutubeSnapshot(server, key), delayMillis, TimeUnit.MILLISECONDS);
					return;
				}
				if (state.loading) {
					state.playbackFuture = mediaScheduler.schedule(() -> refreshLoadingUi(server, key), youtubePollActiveIntervalMs(), TimeUnit.MILLISECONDS);
					return;
				}
				if (wallpaperVisibleForCurrentViewLocked(state) && state.wallpaperMedia != null && state.wallpaperMedia.animated()) {
					if (!hasNearbyMediaViewer(server, key)) {
						state.playbackFuture = scheduleWallpaperVisibilityRecheck(server, key);
						return;
					}
					int delayMillis = state.wallpaperMedia.delayMillis(state.wallpaperFrameIndex);
					state.playbackFuture = mediaScheduler.schedule(() -> advanceMediaFrame(server, key), delayMillis, TimeUnit.MILLISECONDS);
					return;
				}
				if (state.loadedMedia == null || !state.loadedMedia.animated() || state.waitingForLink || state.loading || isPlaybackPausedLocked(state)) {
					return;
				}
			int delayMillis = state.loadedMedia.delayMillis(state.frameIndex);
			state.playbackFuture = mediaScheduler.schedule(() -> advanceMediaFrame(server, key), delayMillis, TimeUnit.MILLISECONDS);
		}
	}

	private static void refreshLoadingUi(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.playbackFuture = null;
			if (!state.loading || state.waitingForLink) {
				return;
			}
			if (hasActiveStreamPlaybackLocked(state) && state.relaySessionId != null) {
				return;
			}
		}
		requestRuntimeRender(server, key);
		resumeMediaPlaybackIfNeeded(server, key);
	}

	private static void refreshWallpaperPlayback(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean visibleAnimatedWallpaper;
		synchronized (state) {
			state.playbackFuture = null;
			visibleAnimatedWallpaper = wallpaperVisibleForCurrentViewLocked(state)
					&& state.wallpaperMedia != null
					&& state.wallpaperMedia.animated();
		}
		if (!visibleAnimatedWallpaper) {
			return;
		}
		if (hasNearbyMediaViewer(server, key)) {
			requestRuntimeRender(server, key);
		}
		resumeMediaPlaybackIfNeeded(server, key);
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
			if (hasActiveStreamPlaybackLocked(state)) {
				state.playbackFuture = null;
				return;
			}
			if (wallpaperVisibleForCurrentViewLocked(state) && state.wallpaperMedia != null && state.wallpaperMedia.animated()) {
				state.wallpaperFrameIndex = (state.wallpaperFrameIndex + 1) % state.wallpaperMedia.frameCount();
				state.playbackFuture = null;
				shouldContinue = state.wallpaperMedia.frameCount() > 1;
			} else {
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
				if (state.loading) {
					// Keep loading spinners animating even before the relay session is fully connected.
				} else if (hasActiveStreamPlaybackLocked(state)) {
					if (state.relaySessionId == null) {
						return;
					}
				} else if (wallpaperVisibleForCurrentViewLocked(state) && state.wallpaperMedia != null && state.wallpaperMedia.animated()) {
					// Animated wallpaper uses the same scheduler path as gallery GIF playback.
				} else if (state.loadedMedia == null || !state.loadedMedia.animated() || isPlaybackPausedLocked(state)) {
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
			if (!isStreamPlaybackLocked(state) || state.relaySessionId == null || state.waitingForLink) {
				return;
			}
			sessionId = state.relaySessionId;
			knownFrameSequence = state.youtubeFrameSequence;
		}
		YoutubeSnapshotResult result;
		try {
			result = new YoutubeSnapshotResult(key, MonitorYoutubeRelayClient.snapshot(sessionId, knownFrameSequence), null);
		} catch (Exception exception) {
			result = new YoutubeSnapshotResult(key, null, sanitizeMediaError(exception.getMessage()));
		}
		YoutubeSnapshotResult finalResult = result;
		server.execute(() -> applyYoutubeSnapshotResult(server, finalResult));
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
		boolean shouldBumpVersion = false;
		boolean speakerRefreshNeeded = false;
		Integer queueAdvanceIndex = null;
		synchronized (state) {
			if (!isStreamPlaybackLocked(state)) {
				return;
			}
			if (result.snapshot() != null) {
				boolean wasLoading = state.loading;
				long previousPositionMs = state.positionMs;
				long previousDurationMs = state.durationMs;
				long previousBufferedStartMs = state.bufferedStartMs;
				long previousBufferedEndMs = state.bufferedEndMs;
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
				state.bufferedStartMs = result.snapshot().bufferedStartMs();
				state.bufferedEndMs = result.snapshot().bufferedEndMs();
				state.liveStream = result.snapshot().live();
				state.audioPlaceholder = result.snapshot().audioPlaceholder();
				state.userPaused = result.snapshot().paused();
				state.statusText = result.snapshot().status();
				state.loading = !result.snapshot().ready();
				if (previousDurationMs != state.durationMs
						|| previousBufferedStartMs != state.bufferedStartMs
						|| previousBufferedEndMs != state.bufferedEndMs
						|| previousLiveStream != state.liveStream
						|| previousAudioPlaceholder != state.audioPlaceholder
						|| previousPaused != state.userPaused
						|| !Objects.equals(previousStatusText, state.statusText)
						|| wasLoading != state.loading) {
					shouldRender = true;
				}
				if (wasLoading != state.loading
						|| previousPaused != state.userPaused
						|| previousAudioPlaceholder != state.audioPlaceholder
						|| previousLiveStream != state.liveStream
						|| !Objects.equals(previousStatusText, state.statusText)) {
					speakerRefreshNeeded = true;
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
				// Keep YouTube playback on the GIF-like path: new frames should queue the next render,
				// not invalidate an in-flight large-screen render job every poll.
				shouldReschedule = true;
				if (isYoutubeFamilyMode(state.mode) && result.snapshot().ended() && !state.youtubeQueue.isEmpty()) {
					queueAdvanceIndex = normalizeYoutubeQueueIndexLocked(state, state.youtubeQueueIndex >= 0 ? state.youtubeQueueIndex + 1 : 0);
					shouldReschedule = false;
				}
			} else {
				state.loading = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
				shouldBumpVersion = true;
				shouldRender = true;
				speakerRefreshNeeded = true;
			}
			if (shouldBumpVersion) {
				state.version++;
			}
		}
		maybeCompleteYoutubeDownload(server, result.screenKey());
		if (queueAdvanceIndex != null) {
			if (state.mode == ScreenViewMode.YOUTUBE_MUSIC) {
				startYoutubeMusicQueuePlayback(server, result.screenKey(), null, queueAdvanceIndex);
			} else {
				startYoutubeQueuePlayback(server, result.screenKey(), null, queueAdvanceIndex);
			}
			return;
		}
		if (speakerRefreshNeeded) {
			refreshConnectedSpeakersNow(server, result.screenKey());
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
		IN_FLIGHT_MEDIA_LINKS.entrySet().removeIf(entry -> !isMediaSessionAlive(server, entry.getValue().screenKey()));
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

	public static boolean onPlayerHotbarScroll(ServerPlayer player, int previousSlot, int currentSlot) {
		if (player == null) {
			return false;
		}
		ScreenComponent component = findObservedScrollableComponent(player);
		if (component == null) {
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return false;
		}
		if (component.viewMode() == ScreenViewMode.HOME) {
			UiLayout layout = createUiLayout(component.width(), component.height());
			int delta = normalizeHotbarDelta(previousSlot, currentSlot);
			if (delta == 0) {
				return false;
			}
			int maxScroll = homeMaxScroll(layout);
			if (maxScroll <= 0) {
				return false;
			}
			int nextScroll = clampInt(component.launcherPage() - delta, 0, maxScroll);
			if (nextScroll == component.launcherPage()) {
				return false;
			}
			ItemFrame anchor = component.frameCoords().keySet().stream().filter(Entity::isAlive).findFirst().orElse(null);
			if (anchor == null || !(player.level() instanceof ServerLevel serverLevel)) {
				return false;
			}
			synchronizeConnectedScreens(serverLevel, anchor, null, null, nextScroll);
			return true;
		}
		MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
		if (state == null) {
			return false;
		}

		int delta = normalizeHotbarDelta(previousSlot, currentSlot);
		if (delta == 0) {
			return false;
		}

		boolean handled = false;
		Long youtubeSeekTargetMs = null;
		synchronized (state) {
			if (state.overlayMode == MediaOverlayMode.CONTROLS
					&& !state.loading
					&& !state.waitingForLink) {
				if (state.mode == ScreenViewMode.GALLERY && state.gallerySurfaceMode == GallerySurfaceMode.BROWSER && !state.galleryItems.isEmpty()) {
					UiLayout layout = createUiLayout(component.width(), component.height());
					int visibleRows = mediaGalleryVisibleRows(layout);
					int totalRows = mediaGalleryTotalRows(state.galleryItems.size(), layout);
					int maxScroll = Math.max(0, totalRows - visibleRows);
					if (maxScroll > 0) {
						state.galleryScroll = clampInt(state.galleryScroll - delta, 0, maxScroll);
						state.version++;
						handled = true;
					}
				} else if (isYoutubeFamilyMode(state.mode) && state.youtubeQueueOpen && !state.youtubeQueue.isEmpty()) {
					int visibleRows = youtubeQueueVisibleRowsPreview(state);
					int maxScroll = Math.max(0, state.youtubeQueue.size() - visibleRows);
					if (maxScroll > 0) {
						state.youtubeQueueScroll = clampInt(state.youtubeQueueScroll - delta, 0, maxScroll);
						state.version++;
						handled = true;
					}
				} else if (isStreamPlaybackLocked(state) && canSeekTimelineLocked(state)) {
					cancelPlaybackLocked(state);
					long duration = Math.max(1L, state.durationMs);
					long seekStep = Math.max(YOUTUBE_SCROLL_SEEK_MS, duration / 120L);
					youtubeSeekTargetMs = clampLong(state.positionMs + delta * seekStep, 0L, duration);
					state.positionMs = youtubeSeekTargetMs;
					bumpAudioSyncTokenLocked(state);
					handled = true;
				} else if (state.loadedMedia != null && state.loadedMedia.animated() && state.loadedMedia.frameCount() > 1) {
					cancelPlaybackLocked(state);
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

		requestComponentRender(server, component, component.viewMode(), component.launcherPage());
		if (youtubeSeekTargetMs != null) {
			long seekTargetMs = youtubeSeekTargetMs;
			refreshConnectedSpeakersNow(server, component.runtimeKey());
			ensureExecutors();
			CompletableFuture.runAsync(() -> {
				try {
					MonitorYoutubeRelayClient.seek(relaySessionId(component.runtimeKey()), seekTargetMs);
				} catch (Exception ignored) {
				}
			}, mediaIoExecutor).thenRun(() -> server.execute(() -> {
				refreshConnectedSpeakersNow(server, component.runtimeKey());
				scheduleYoutubeRefresh(server, component.runtimeKey(), 0L);
			}));
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
		ScreenComponent nearest = null;
		double nearestDistanceSqr = Double.POSITIVE_INFINITY;

		for (ScreenComponent component : cachedComponents(level)) {
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

	private static ScreenComponent findObservedScrollableComponent(ServerPlayer player) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 rayEnd = eye.add(player.getLookAngle().scale(MEDIA_CONTROL_DISTANCE));
		ScreenComponent nearest = null;
		double nearestDistanceSqr = Double.POSITIVE_INFINITY;

		for (ScreenComponent component : cachedComponents(level)) {
			if (component == null || !component.powered()) {
				continue;
			}
			if (component.viewMode() != ScreenViewMode.HOME && !isPlayerMode(component.viewMode())) {
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

	private static int normalizeHotbarDelta(int previousSlot, int currentSlot) {
		if (previousSlot == currentSlot) {
			return 0;
		}
		int upwardSteps = Math.floorMod(previousSlot - currentSlot, 9);
		if (upwardSteps >= 1 && upwardSteps <= 2) {
			return upwardSteps;
		}
		int downwardSteps = Math.floorMod(currentSlot - previousSlot, 9);
		if (downwardSteps >= 1 && downwardSteps <= 2) {
			return -downwardSteps;
		}
		return 0;
	}

	private static boolean isMediaSessionAlive(MinecraftServer server, ScreenRuntimeKey key) {
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component == null || !component.runtimeKey().equals(key)) {
			return false;
		}
		if (isPlayerMode(component.viewMode())) {
			return true;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return false;
		}
		synchronized (state) {
			return (state.wallpaperUrl != null && !state.wallpaperUrl.isBlank()) || !state.galleryItems.isEmpty();
		}
	}

	private static void requestRuntimeRender(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) {
			return;
		}
		enqueueComponentSync(level, key);
	}

	private static void dispatchRuntimeRender(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) {
			return;
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component == null) {
			closeMediaSession(server, key);
			return;
		}
		if (isPlayerMode(component.viewMode()) && !hasNearbyMediaViewer(level, component)) {
			return;
		}
		if (!isPlayerMode(component.viewMode())) {
			MediaRuntimeState state = MEDIA_STATES.get(key);
			if (state != null) {
				synchronized (state) {
					if (wallpaperVisibleForCurrentViewLocked(state) && !hasNearbyMediaViewer(level, component)) {
						return;
					}
				}
			}
		}
		requestComponentRender(server, component, component.viewMode(), component.launcherPage());
	}

	private static void requestComponentRender(MinecraftServer server, ScreenComponent component, ScreenViewMode viewMode, int launcherPage) {
		if (server == null || component == null) {
			return;
		}
		RenderWork work;
		MediaRuntimeState mediaState = MEDIA_STATES.get(component.runtimeKey());
		if (mediaState == null && (isPlayerMode(viewMode) || resolvePersistedWallpaperState(component) != null)) {
			mediaState = MEDIA_STATES.computeIfAbsent(
					component.runtimeKey(),
					ignored -> MediaRuntimeState.fresh(viewMode, "", () -> onMediaProgressChanged(server, component.runtimeKey()))
			);
		}
		if (mediaState != null) {
			synchronized (mediaState) {
				mediaState.mode = viewMode;
			}
			ensureWallpaperStateHydrated(server, component.runtimeKey(), mediaState);
		}
		if (isPlayerMode(viewMode)) {
			if (viewMode == ScreenViewMode.GALLERY) {
				ensureGalleryStateHydrated(server, component.runtimeKey(), mediaState);
			}
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
			work = createRenderWork(component, viewMode, launcherPage, mediaState);
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
		if (mediaState != null && (isPlayerMode(viewMode) || wallpaperVisibleForViewMode(viewMode, mediaState))) {
			resumeMediaPlaybackIfNeeded(server, component.runtimeKey());
		}
	}

	private static void submitRenderWork(MinecraftServer server, RenderWork work) {
		if (server == null || work == null) {
			return;
		}
		ensureExecutors();
		renderExecutor.submit(() -> {
			try {
				byte[][] renderedTiles = renderTiles(server, work);
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
			ScreenComponent component = resolveScreenComponent(server, work.runtimeKey());
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
			deactivateMediaSession(level.getServer(), component.runtimeKey());
		}
		int effectiveLauncherPage = viewMode == ScreenViewMode.HOME
				? clampInt(launcherPage, 0, homeMaxScroll(createUiLayout(component.width(), component.height())))
				: launcherPage;
		ScreenComponent renderedComponent = new ScreenComponent(
				component.runtimeKey(),
				component.facing(),
				component.right(),
				component.width(),
				component.height(),
				powered,
				viewMode,
				effectiveLauncherPage,
				component.frameCoords(),
				component.byCoord()
		);
		boolean immediateRenderRequested = forcedViewMode != null || forcedLauncherPage != null;
		ServerLevel mapLevel = photoMapLevel(level.getServer(), level);
		List<PersistedGalleryItem> persistedGallery = resolvePersistedGalleryState(component);
		PersistedWallpaperState persistedWallpaper = resolvePersistedWallpaperState(component);
		boolean rerenderMaps = false;

		cacheComponent(level, renderedComponent);
		if (immediateRenderRequested) {
			requestComponentRender(level.getServer(), renderedComponent, viewMode, effectiveLauncherPage);
		}

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
					effectiveLauncherPage,
					componentGroupId(component.runtimeKey())
			);

			if (missingMap || !currentState.sameRenderState(updatedState)) {
				rerenderMaps = true;
			}
			List<PersistedGalleryItem> currentGalleryState = readPersistedGalleryState(ensured);
			PersistedWallpaperState currentWallpaperState = readPersistedWallpaperState(ensured);
			boolean galleryChanged = !Objects.equals(currentGalleryState, persistedGallery);
			boolean wallpaperChanged = !Objects.equals(currentWallpaperState, persistedWallpaper);
			if (galleryChanged || wallpaperChanged) {
				rerenderMaps = true;
			}
			if (!currentState.equals(updatedState) || galleryChanged || wallpaperChanged) {
				ItemStack updated = ensured.copy();
				writeScreenState(updated, updatedState);
				writePersistedGalleryState(updated, galleryItemsFromPersisted(persistedGallery));
				writePersistedWallpaperState(updated, persistedWallpaper);
				frame.setItem(updated, false);
			}
			ensureDisplay(level, frame, connectionMask);
		}

		if (!rerenderMaps || immediateRenderRequested) {
			return;
		}
		requestComponentRender(level.getServer(), renderedComponent, viewMode, effectiveLauncherPage);
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
			ScreenComponent component = resolveScreenComponent(level, neighbor);
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
		ServerLevel level = null;
		for (ItemFrame frame : component.frameCoords().keySet()) {
			if (frame.level() instanceof ServerLevel serverLevel) {
				level = serverLevel;
			}
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
		if (level != null) {
			invalidateCachedRuntime(level, component.runtimeKey(), null, false);
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

	private static List<PersistedGalleryItem> resolvePersistedGalleryState(ScreenComponent component) {
		if (component == null) {
			return List.of();
		}
		MediaRuntimeState runtimeState = MEDIA_STATES.get(component.runtimeKey());
		if (runtimeState != null) {
			synchronized (runtimeState) {
				List<PersistedGalleryItem> fromRuntime = persistedGalleryItems(runtimeState.galleryItems);
				if (!fromRuntime.isEmpty()) {
					return fromRuntime;
				}
			}
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			List<PersistedGalleryItem> fromFrame = readPersistedGalleryState(frame.getItem());
			if (!fromFrame.isEmpty()) {
				return fromFrame;
			}
		}
		return List.of();
	}

	private static PersistedWallpaperState resolvePersistedWallpaperState(ScreenComponent component) {
		if (component == null) {
			return null;
		}
		MediaRuntimeState runtimeState = MEDIA_STATES.get(component.runtimeKey());
		if (runtimeState != null) {
			synchronized (runtimeState) {
				if (runtimeState.wallpaperUrl != null && !runtimeState.wallpaperUrl.isBlank()) {
					return new PersistedWallpaperState(runtimeState.wallpaperUrl, runtimeState.wallpaperScaleMode);
				}
			}
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			PersistedWallpaperState state = readPersistedWallpaperState(frame.getItem());
			if (state != null && state.url() != null && !state.url().isBlank()) {
				return state;
			}
		}
		return null;
	}

	private static List<GalleryItem> galleryItemsFromPersisted(List<PersistedGalleryItem> persistedItems) {
		if (persistedItems == null || persistedItems.isEmpty()) {
			return List.of();
		}
		List<GalleryItem> items = new ArrayList<>(persistedItems.size());
		for (PersistedGalleryItem item : persistedItems) {
			if (item == null || item.url() == null || item.url().isBlank()) {
				continue;
			}
			GalleryItemKind resolvedKind = effectiveGalleryItemKind(item);
			BufferedImage preview = resolvedKind == GalleryItemKind.YOUTUBE
					? MonitorYoutubeRelayClient.queueEntryPreview(item.url())
					: null;
			items.add(new GalleryItem(item.title(), item.url(), item.localMediaKey(), null, preview, resolvedKind));
		}
		return List.copyOf(items);
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
			byte[][] tiles = renderTiles(level.getServer(), new RenderWork(null, state.powered(), state.viewMode(), state.launcherPage(), 1, 1, 0L, null, null));
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

	private static List<PersistedGalleryItem> readPersistedGalleryState(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return List.of();
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return List.of();
		}
		CompoundTag root = customData.copyTag();
		if (!root.contains(PERSISTED_MEDIA_ROOT_TAG)) {
			return List.of();
		}
		CompoundTag mediaTag = root.getCompoundOrEmpty(PERSISTED_MEDIA_ROOT_TAG);
		int count = Math.max(0, mediaTag.getIntOr(PERSISTED_GALLERY_COUNT_TAG, 0));
		if (count <= 0) {
			return List.of();
		}
		List<PersistedGalleryItem> items = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			CompoundTag itemTag = mediaTag.getCompoundOrEmpty(PERSISTED_GALLERY_ITEM_PREFIX + index);
			String url = itemTag.getStringOr(PERSISTED_GALLERY_URL_TAG, "").trim();
			if (url.isBlank()) {
				continue;
			}
			String title = itemTag.getStringOr(PERSISTED_GALLERY_TITLE_TAG, "").trim();
			String localMediaKey = itemTag.getStringOr(PERSISTED_GALLERY_LOCAL_MEDIA_TAG, "").trim();
			GalleryItemKind kind = effectiveGalleryItemKind(
					url,
					localMediaKey,
					GalleryItemKind.fromPersisted(itemTag.getStringOr(PERSISTED_GALLERY_KIND_TAG, ""), url)
			);
			items.add(new PersistedGalleryItem(title, url, kind, localMediaKey));
		}
		return List.copyOf(items);
	}

	private static PersistedWallpaperState readPersistedWallpaperState(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		CompoundTag root = customData.copyTag();
		if (!root.contains(PERSISTED_MEDIA_ROOT_TAG)) {
			return null;
		}
		CompoundTag mediaTag = root.getCompoundOrEmpty(PERSISTED_MEDIA_ROOT_TAG);
		String url = mediaTag.getStringOr(PERSISTED_WALLPAPER_URL_TAG, "").trim();
		if (url.isBlank()) {
			return null;
		}
		return new PersistedWallpaperState(
				url,
				parsePersistedScaleMode(mediaTag.getStringOr(PERSISTED_WALLPAPER_SCALE_TAG, MediaScaleMode.FIT.name()))
		);
	}

	private static MediaScaleMode parsePersistedScaleMode(String value) {
		if (value == null || value.isBlank()) {
			return MediaScaleMode.FIT;
		}
		try {
			return MediaScaleMode.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return MediaScaleMode.FIT;
		}
	}

	private static void writePersistedGalleryState(ItemStack stack, List<GalleryItem> galleryItems) {
		writePersistedGalleryState(stack, galleryItems, null);
	}

	private static void writePersistedGalleryState(ItemStack stack, List<GalleryItem> galleryItems, PersistedWallpaperState wallpaperState) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		List<PersistedGalleryItem> persistedItems = persistedGalleryItems(galleryItems);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.remove(PERSISTED_MEDIA_ROOT_TAG);
			if (persistedItems.isEmpty() && (wallpaperState == null || wallpaperState.url() == null || wallpaperState.url().isBlank())) {
				return;
			}
			CompoundTag mediaTag = new CompoundTag();
			mediaTag.putInt(PERSISTED_GALLERY_COUNT_TAG, persistedItems.size());
			for (int index = 0; index < persistedItems.size(); index++) {
				PersistedGalleryItem item = persistedItems.get(index);
				CompoundTag itemTag = new CompoundTag();
				itemTag.putString(PERSISTED_GALLERY_TITLE_TAG, item.title() == null ? "" : item.title());
				itemTag.putString(PERSISTED_GALLERY_URL_TAG, item.url() == null ? "" : item.url());
				itemTag.putString(PERSISTED_GALLERY_KIND_TAG, item.kind() != null ? item.kind().persistedName() : GalleryItemKind.MEDIA.persistedName());
				if (item.localMediaKey() != null && !item.localMediaKey().isBlank()) {
					itemTag.putString(PERSISTED_GALLERY_LOCAL_MEDIA_TAG, item.localMediaKey());
				}
				mediaTag.put(PERSISTED_GALLERY_ITEM_PREFIX + index, itemTag);
			}
			if (wallpaperState != null && wallpaperState.url() != null && !wallpaperState.url().isBlank()) {
				mediaTag.putString(PERSISTED_WALLPAPER_URL_TAG, wallpaperState.url());
				mediaTag.putString(
						PERSISTED_WALLPAPER_SCALE_TAG,
						(wallpaperState.scaleMode() != null ? wallpaperState.scaleMode() : MediaScaleMode.FIT).name().toLowerCase(Locale.ROOT)
				);
			}
			tag.put(PERSISTED_MEDIA_ROOT_TAG, mediaTag);
		});
	}

	private static void writePersistedWallpaperState(ItemStack stack, PersistedWallpaperState wallpaperState) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		List<PersistedGalleryItem> persistedItems = readPersistedGalleryState(stack);
		List<GalleryItem> galleryItems = galleryItemsFromPersisted(persistedItems);
		writePersistedGalleryState(stack, galleryItems, wallpaperState);
	}

	private static List<PersistedGalleryItem> persistedGalleryItems(List<GalleryItem> galleryItems) {
		if (galleryItems == null || galleryItems.isEmpty()) {
			return List.of();
		}
		List<PersistedGalleryItem> items = new ArrayList<>(galleryItems.size());
		for (GalleryItem item : galleryItems) {
			if (item == null || item.url() == null || item.url().isBlank()) {
				continue;
			}
			items.add(new PersistedGalleryItem(
					item.title() == null ? "" : item.title(),
					item.url().trim(),
					item.kind() != null ? item.kind() : GalleryItemKind.MEDIA,
					item.localMediaKey() == null ? "" : item.localMediaKey().trim()
			));
		}
		return List.copyOf(items);
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
		WallpaperVisualSnapshot wallpaperSnapshot = captureWallpaperSnapshot(mediaState, viewMode);
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
				mediaSnapshot,
				wallpaperSnapshot
		);
	}

	private static MediaVisualSnapshot captureMediaSnapshot(MediaRuntimeState state) {
		if (state == null) {
			return new MediaVisualSnapshot(ScreenViewMode.GALLERY, 0L, null, null, false, true, false, false, false, false, false, false, false, false, 0, 0, 0.0F, 0.0F, 0.0F, "", false, MediaOverlayMode.CONTROLS, MediaScaleMode.FIT, "", "ВСТАВЬ URL", "", "", null, List.of(), List.of(), false, MediaActionGlyph.DOWNLOAD, MediaActionVisualState.IDLE, false, MediaActionGlyph.WALLPAPER, MediaActionVisualState.IDLE, false, 0, -1, null);
		}
		boolean youtubeMode = state.mode == ScreenViewMode.YOUTUBE;
		boolean youtubeMusicMode = state.mode == ScreenViewMode.YOUTUBE_MUSIC;
		boolean youtubeFamilyMode = youtubeMode || youtubeMusicMode;
		boolean galleryMode = state.mode == ScreenViewMode.GALLERY;
		boolean galleryBrowser = galleryMode && state.gallerySurfaceMode == GallerySurfaceMode.BROWSER;
		boolean galleryBackedYoutube = isGalleryBackedYoutubeLocked(state);
		boolean streamPlayback = isStreamPlaybackLocked(state);
		BufferedImage frame = streamPlayback
				? state.streamFrame
				: !galleryBrowser && state.loadedMedia != null ? state.loadedMedia.frame(state.frameIndex) : null;
		BufferedImage backgroundFrame = youtubeMusicMode && state.loading && state.loadingBackdropFrame != null
				? state.loadingBackdropFrame
				: frame;
		boolean hasMedia = streamPlayback
				? hasDisplayableMediaLocked(state)
				: galleryBrowser ? !state.galleryItems.isEmpty() : state.loadedMedia != null;
		boolean playbackControlsVisible = mediaControlUiVisibleLocked(state);
		boolean timelineVisible = streamPlayback || (!galleryBrowser && state.loadedMedia != null && state.loadedMedia.frameCount() > 1);
		boolean centerPlayPauseVisible = streamPlayback || (!galleryBrowser && state.loadedMedia != null && state.loadedMedia.animated());
		boolean timelineSeekable = streamPlayback
				? state.durationMs > 0L && !state.liveStream
				: !galleryBrowser && state.loadedMedia != null && state.loadedMedia.frameCount() > 1;
		int timelineIndex = streamPlayback
				? (int) Math.min(Integer.MAX_VALUE, state.positionMs)
				: !galleryBrowser && state.loadedMedia != null ? Math.floorMod(state.frameIndex, Math.max(1, state.loadedMedia.frameCount())) : 0;
		int timelineCount = streamPlayback
				? (int) Math.min(Integer.MAX_VALUE, state.durationMs)
				: !galleryBrowser && state.loadedMedia != null ? state.loadedMedia.frameCount() : 0;
		float timelineFraction = streamPlayback
				? youtubeTimelineFraction(state)
				: !galleryBrowser && state.loadedMedia != null && state.loadedMedia.frameCount() > 1
				? (float) timelineIndex / (float) Math.max(1, state.loadedMedia.frameCount() - 1)
				: 0.0F;
		float bufferedStartFraction = streamPlayback ? youtubeBufferedFraction(state, state.bufferedStartMs) : 0.0F;
		float bufferedEndFraction = streamPlayback ? youtubeBufferedFraction(state, state.bufferedEndMs) : 0.0F;
		String timelineLabel = streamPlayback
				? state.liveStream ? "LIVE" : formatPlaybackTime(state.positionMs) + " / " + formatPlaybackTime(state.durationMs)
				: (!galleryBrowser && timelineCount > 0 ? (timelineIndex + 1) + "/" + Math.max(1, timelineCount) : "");
		List<YoutubeQueueItemSnapshot> queueItems = youtubeFamilyMode ? youtubeQueueSnapshots(state) : galleryMode ? galleryItemSnapshots(state) : List.of();
		List<GalleryCardSnapshot> galleryCards = galleryMode ? galleryCardSnapshots(state) : List.of();
		MediaActionGlyph actionGlyph = resolvedActionGlyph(state);
		MediaActionVisualState actionState = resolvedActionVisualState(state);
		boolean actionVisible = resolvedActionVisible(state);
		boolean wallpaperActionVisible = galleryMode && currentGalleryItemCanBeWallpaperLocked(state);
		MediaActionGlyph wallpaperActionGlyph = currentGalleryItemIsWallpaperLocked(state) ? MediaActionGlyph.CHECK : MediaActionGlyph.WALLPAPER;
		MediaActionVisualState wallpaperActionState = currentGalleryItemIsWallpaperLocked(state) ? MediaActionVisualState.COMPLETE : MediaActionVisualState.IDLE;
		MediaOverlayWindowSnapshot overlayWindow = youtubeFamilyMode && state.youtubeQueueOpen
				? youtubeQueueWindowSnapshot(state, queueItems)
				: galleryMode && state.galleryDeleteConfirmOpen
				? galleryDeleteConfirmWindowSnapshot(state)
				: null;
		return new MediaVisualSnapshot(
				state.mode,
				state.version,
				frame,
				backgroundFrame,
				hasMedia,
				galleryBrowser,
				galleryMode && currentGalleryItemSavedLocked(state),
				galleryBackedYoutube,
				streamPlayback,
				playbackControlsVisible,
				state.loading,
				timelineVisible,
				centerPlayPauseVisible,
				timelineSeekable,
				timelineIndex,
				timelineCount,
				timelineFraction,
				bufferedStartFraction,
				bufferedEndFraction,
				timelineLabel,
				isPlaybackPausedLocked(state),
				state.overlayMode,
				state.scaleMode,
				state.statusText,
				youtubeMusicMode ? "YT MUSIC URL" : youtubeMode ? "YOUTUBE URL" : "URL",
				state.mediaTitle != null ? state.mediaTitle : "",
				state.mediaSubtitle != null ? state.mediaSubtitle : "",
				state.progress.snapshot(),
				queueItems,
				galleryCards,
				actionVisible,
				actionGlyph,
				actionState,
				wallpaperActionVisible,
				wallpaperActionGlyph,
				wallpaperActionState,
				youtubeFamilyMode && state.youtubeQueueOpen,
				youtubeFamilyMode ? state.youtubeQueueScroll : galleryMode ? state.galleryScroll : 0,
				youtubeFamilyMode ? state.youtubeQueueIndex : galleryMode ? state.galleryIndex : -1,
				overlayWindow
		);
	}

	private static WallpaperVisualSnapshot captureWallpaperSnapshot(MediaRuntimeState state, ScreenViewMode viewMode) {
		if (state == null || !wallpaperVisibleForViewMode(viewMode, state) || state.wallpaperMedia == null) {
			return null;
		}
		BufferedImage frame = state.wallpaperMedia.frame(state.wallpaperFrameIndex);
		if (frame == null) {
			return null;
		}
		return new WallpaperVisualSnapshot(
				frame,
				state.wallpaperScaleMode != null ? state.wallpaperScaleMode : MediaScaleMode.FIT
		);
	}

	private static byte[][] renderTiles(MinecraftServer server, RenderWork work) {
		if (work == null) {
			return new byte[0][];
		}
		if (!isPlayerMode(work.viewMode()) && work.wallpaperSnapshot() == null) {
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
		if (work.powered() && work.wallpaperSnapshot() != null && work.wallpaperSnapshot().frame() != null) {
			drawScaledImage(
					graphics,
					work.wallpaperSnapshot().frame(),
					mediaCanvasRect(createUiLayout(work.width(), work.height())),
					work.wallpaperSnapshot().scaleMode()
			);
		}
		if (work.powered()) {
			UiLayout layout = createUiLayout(work.width(), work.height());
			if (work.viewMode() == ScreenViewMode.HOME) {
				drawHomeScreen(graphics, layout, work.launcherPage());
			} else {
				drawAppScreen(graphics, layout, appForViewMode(work.viewMode()), work.runtimeKey(), server, work.mediaSnapshot());
			}
		}
		graphics.dispose();

		int[] rgbPixels = canvas.getRaster().getDataBuffer() instanceof DataBufferInt dataBuffer
				? dataBuffer.getData()
				: canvas.getRGB(0, 0, pixelWidth, pixelHeight, null, 0, pixelWidth);
		byte[][] tiles = new byte[work.width() * work.height()][MAP_SIZE * MAP_SIZE];
		quantizeTiles(work, rgbPixels, pixelWidth, tiles);

		if (!isPlayerMode(work.viewMode()) && work.wallpaperSnapshot() == null) {
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
					new Color(190, 196, 202, 60),
					0.0F,
					pixelHeight,
					new Color(112, 122, 132, 44)
			));
			graphics.fillRect(0, 0, pixelWidth, pixelHeight);
			graphics.setColor(new Color(96, 104, 114, 40));
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
		fillRoundedRect(graphics, header, clampInt(layout.unit() * 2, 12, 36), new Color(18, 24, 30, 196));
		strokeRoundedRect(graphics, header, clampInt(layout.unit() * 2, 12, 36), 1.0F, new Color(255, 255, 255, 66));
		drawCenteredTextFitted(graphics, "ПРИЛОЖЕНИЯ", header, new Color(248, 250, 252), Font.BOLD, clampInt(layout.unit() * 2, 10, 34), clampInt(layout.unit(), 8, 18));

		List<MonitorApp> visibleApps = visibleHomeApps(layout, launcherPage);
		for (int index = 0; index < visibleApps.size(); index++) {
			drawHomeAppCard(graphics, layout, homeAppCardRect(layout, launcherPage, index), visibleApps.get(index));
		}

		int totalRows = homeTotalRows(layout);
		int visibleRows = homeRowsPerPage(layout);
		int maxScroll = Math.max(0, totalRows - visibleRows);
		if (maxScroll > 0) {
			UiRect footer = homeFooterRect(layout, panel);
			fillRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 32), new Color(18, 24, 30, 184));
			strokeRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 32), 1.0F, new Color(255, 255, 255, 52));
			drawCenteredTextFitted(
					graphics,
					(launcherPage + 1) + "/" + (maxScroll + 1),
					new UiRect(footer.x() + footer.width() / 4, footer.y(), footer.width() / 2, footer.height()),
					new Color(248, 251, 255),
					Font.BOLD,
					clampInt(layout.unit() * 2 - 1, 10, 28),
					clampInt(layout.unit(), 8, 16)
			);
			drawScrollbar(graphics, homeScrollbarTrackRect(layout), launcherPage, visibleRows, totalRows, layout, homeScrollbarThumbRect(layout, launcherPage, visibleRows, totalRows));
		}
	}

	private static void drawAppScreen(Graphics2D graphics, UiLayout layout, MonitorApp app, ScreenRuntimeKey runtimeKey, MinecraftServer server, MediaVisualSnapshot mediaSnapshot) {
		if (app == null) {
			drawHomeScreen(graphics, layout, 0);
			return;
		}
		if ("gallery".equalsIgnoreCase(app.id())
				|| "youtube".equalsIgnoreCase(app.id())
				|| "youtubemusic".equalsIgnoreCase(app.id())) {
			drawMediaScreen(graphics, layout, runtimeKey, server, mediaSnapshot);
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

	private static void drawMediaScreen(Graphics2D graphics, UiLayout layout, ScreenRuntimeKey runtimeKey, MinecraftServer server, MediaVisualSnapshot state) {
		if (state != null && state.mode() == ScreenViewMode.GALLERY && state.galleryBrowser()) {
			drawGalleryBrowserScreen(graphics, layout, state);
			return;
		}

		UiRect canvasRect = mediaCanvasRect(layout);
		UiRect closeRect = mediaCloseRect(layout);
		BufferedImage mediaFrame = state != null ? state.frame() : null;
		BufferedImage mediaBackgroundFrame = state != null && state.backgroundFrame() != null ? state.backgroundFrame() : mediaFrame;
		boolean hasMedia = state != null && state.hasMedia();
		boolean controlUi = state != null && (state.hasMedia() || state.playbackControlsVisible());
		boolean youtubeMode = state != null && state.mode() == ScreenViewMode.YOUTUBE;
		boolean youtubeMusicMode = state != null && state.mode() == ScreenViewMode.YOUTUBE_MUSIC;
		boolean youtubeFamilyMode = youtubeMode || youtubeMusicMode;
		boolean galleryMode = state != null && state.mode() == ScreenViewMode.GALLERY;
		boolean galleryBackedYoutube = state != null && state.galleryBackedYoutube();
		boolean youtubeHomePrompt = isYoutubeHomePrompt(state);
		boolean showQueueButton = state != null && youtubeFamilyMode && !galleryBackedYoutube;
		boolean showPrimaryActionButton = state != null && !youtubeMusicMode && state.actionVisible();
		boolean showWallpaperActionButton = state != null && galleryMode && state.wallpaperActionVisible();
		MediaButtonSegment scaleButtonSegment = MediaButtonSegment.SINGLE;
		MediaButtonSegment primaryActionSegment = MediaButtonSegment.SINGLE;
		MediaButtonSegment wallpaperActionSegment = MediaButtonSegment.SINGLE;
		MediaButtonSegment queueButtonSegment = MediaButtonSegment.SINGLE;
		MediaButtonSegment youtubeMusicSearchSegment = MediaButtonSegment.SINGLE;
		if (!youtubeMusicMode) {
			int smallButtonCount = 1 + (showWallpaperActionButton ? 1 : 0) + (showPrimaryActionButton ? 1 : 0) + (showQueueButton ? 1 : 0);
			int smallButtonIndex = 0;
			if (showWallpaperActionButton) {
				wallpaperActionSegment = mediaButtonSegment(smallButtonIndex++, smallButtonCount);
			}
			if (showPrimaryActionButton) {
				primaryActionSegment = mediaButtonSegment(smallButtonIndex++, smallButtonCount);
			}
			if (showQueueButton) {
				queueButtonSegment = mediaButtonSegment(smallButtonIndex++, smallButtonCount);
			}
			scaleButtonSegment = mediaButtonSegment(smallButtonIndex, smallButtonCount);
		} else if (showQueueButton) {
			youtubeMusicSearchSegment = MediaButtonSegment.LEFT;
			queueButtonSegment = MediaButtonSegment.RIGHT;
		}
		UiRect titleRect = galleryMode ? mediaGalleryPlayerTitleRect(layout) : mediaLinkRect(layout, controlUi);
		UiRect scaleRect = mediaScaleRect(layout);
		UiRect downloadRect = mediaDownloadRect(layout);
		UiRect queueToggleRect = mediaQueueToggleRect(layout, state != null ? state.mode() : ScreenViewMode.HOME);
		UiRect timelineRect = mediaTimelineRect(layout, state != null ? state.mode() : ScreenViewMode.HOME);
		boolean darkPlayerSurface = usesDarkMediaPlayerSurface(state);

		if (darkPlayerSurface && !youtubeMusicMode) {
			graphics.setColor(Color.BLACK);
			graphics.fillRect(canvasRect.x(), canvasRect.y(), canvasRect.width(), canvasRect.height());
		}
		if (youtubeMusicMode && !youtubeHomePrompt) {
			if (mediaBackgroundFrame != null) {
				drawYoutubeMusicArtworkBackground(
						graphics,
						canvasRect,
						mediaBackgroundFrame,
						secondaryArtworkScaleMode(state != null ? state.scaleMode() : MediaScaleMode.FIT)
				);
				graphics.setPaint(new GradientPaint(
						canvasRect.x(),
						canvasRect.y(),
						new Color(6, 8, 10, 122),
						canvasRect.right(),
						canvasRect.bottom(),
						new Color(10, 10, 14, 164)
				));
				graphics.fillRect(canvasRect.x(), canvasRect.y(), canvasRect.width(), canvasRect.height());
			}
			if (mediaFrame != null) {
				drawYoutubeMusicArtworkCard(graphics, layout, mediaFrame, state != null ? state.scaleMode() : MediaScaleMode.FIT);
			}
		} else if (mediaFrame != null) {
			drawScaledImage(graphics, mediaFrame, canvasRect, state.scaleMode());
		} else if (!youtubeHomePrompt) {
			if (darkPlayerSurface) {
				graphics.setPaint(new GradientPaint(
						canvasRect.x(),
						canvasRect.y(),
						new Color(6, 8, 12, 222),
						canvasRect.right(),
						canvasRect.bottom(),
						new Color(14, 18, 24, 248)
				));
				graphics.fillRect(canvasRect.x(), canvasRect.y(), canvasRect.width(), canvasRect.height());
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
		}

		boolean controlsActive = state != null
				&& (state.overlayMode() == MediaOverlayMode.CONTROLS
				|| state.loading()
				|| (youtubeFamilyMode && !hasMedia));
		if (controlsActive) {
			if (!youtubeHomePrompt) {
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
			}

			if (galleryMode) {
				drawMediaBackButton(graphics, closeRect, layout);
			} else {
				drawMediaCloseButton(graphics, closeRect, layout);
			}
				if (controlUi) {
					boolean titleBarMode = galleryMode
							|| galleryBackedYoutube
							|| (youtubeMusicMode && (hasMedia
							|| state.loading()
							|| (state.mediaTitle() != null && !state.mediaTitle().isBlank())));
					if (titleBarMode && !youtubeMusicMode) {
						drawMediaTitleBar(graphics, titleRect, state != null ? state.mediaTitle() : "", layout);
						if (showPrimaryActionButton && galleryMode) {
							drawGalleryPlayerActionButton(graphics, mediaGalleryPlayerActionRect(layout), state, layout, primaryActionSegment);
						}
						if (showWallpaperActionButton && galleryMode) {
							drawGalleryWallpaperActionButton(graphics, downloadRect, state, layout, wallpaperActionSegment);
						}
					} else if (youtubeMusicMode) {
						if (hasMedia
								|| state.loading()
								|| (state.mediaTitle() != null && !state.mediaTitle().isBlank())) {
							drawYoutubeMusicTrackInfo(graphics, layout, state);
						}
					} else {
						drawMediaSearchBar(
								graphics,
								titleRect,
								state != null ? state.linkPlaceholder() : "ВСТАВЬ URL",
							true,
							layout
					);
				}
				if (!youtubeMusicMode) {
					drawMediaScaleButton(graphics, scaleRect, state != null ? state.scaleMode() : MediaScaleMode.FIT, layout, scaleButtonSegment);
				}
				if (youtubeFamilyMode) {
					if (showPrimaryActionButton && !youtubeMusicMode) {
						drawYoutubePlayerActionButton(graphics, downloadRect, state, layout, primaryActionSegment);
					}
					if (showQueueButton && !youtubeMusicMode) {
						drawMediaQueueToggleButton(graphics, queueToggleRect, state.youtubeQueueOpen(), layout, queueButtonSegment);
					}
				}
				drawMediaTimeline(graphics, timelineRect, state, layout);
				drawMediaCenterControls(graphics, layout, state);
			}
			if (youtubeMusicMode && !galleryBackedYoutube && !youtubeHomePrompt) {
				drawYoutubeMusicSearchButton(graphics, mediaYoutubeMusicSearchRect(layout), layout, youtubeMusicSearchSegment);
				if (showQueueButton) {
					drawMediaQueueToggleButton(graphics, queueToggleRect, state.youtubeQueueOpen(), layout, queueButtonSegment);
				}
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

		if (progress != null && progress.visible() && !controlUi) {
			UiRect progressRect = mediaProgressRect(layout);
			drawProgressBar(graphics, progressRect, progress, layout);
		}

		if (!controlUi && youtubeFamilyMode && (!youtubeMusicMode || youtubeHomePrompt)) {
			drawMediaSearchBar(
					graphics,
					titleRect,
					state != null ? state.linkPlaceholder() : "ВСТАВЬ URL",
					false,
					layout
			);
		}
		if (state != null && state.overlayWindow() != null) {
			drawMediaOverlayWindow(graphics, layout, runtimeKey, server, state.overlayWindow());
		}
	}

	private static boolean usesDarkMediaPlayerSurface(MediaVisualSnapshot state) {
		if (state == null) {
			return false;
		}
		if (isYoutubeHomePrompt(state)) {
			return false;
		}
		return switch (state.mode()) {
			case GALLERY -> !state.galleryBrowser();
			case YOUTUBE, YOUTUBE_MUSIC -> state.hasMedia() || state.loading() || state.playbackControlsVisible();
			default -> false;
		};
	}

	private static boolean isYoutubeHomePrompt(MediaVisualSnapshot state) {
		if (state == null || !isYoutubeFamilyMode(state.mode())) {
			return false;
		}
		return !state.loading()
				&& !state.hasMedia()
				&& !state.playbackControlsVisible();
	}

	private static void drawGalleryBrowserScreen(Graphics2D graphics, UiLayout layout, MediaVisualSnapshot state) {
		UiRect closeRect = mediaGalleryBrowserCloseRect(layout);
		UiRect linkRect = mediaGalleryBrowserLinkRect(layout);
		UiRect gridRect = mediaGalleryGridRect(layout);
		UiRect scrollbarTrackRect = mediaGalleryBrowserScrollbarTrackRect(layout);

		drawMediaCloseButton(graphics, closeRect, layout);
		drawMediaSearchBar(graphics, linkRect, state != null ? state.linkPlaceholder() : "ВСТАВЬ URL", true, layout);

		List<GalleryCardSnapshot> cards = state != null ? state.galleryCards() : List.of();
		int columns = mediaGalleryColumns(layout);
		int visibleRows = mediaGalleryVisibleRows(layout);
		int totalRows = mediaGalleryTotalRows(cards.size(), layout);
		int scroll = state != null ? clampInt(state.mediaListScroll(), 0, Math.max(0, totalRows - visibleRows)) : 0;
		if (cards.isEmpty()) {
			drawCenteredText(graphics, "Галерея пуста", gridRect, new Color(228, 234, 240, 214), Font.BOLD, clampInt(layout.unit(), 8, 14));
		} else {
			int rowCount = Math.min(visibleRows, Math.max(0, totalRows - scroll));
			for (int visibleRow = 0; visibleRow < rowCount; visibleRow++) {
				for (int column = 0; column < columns; column++) {
					int index = (scroll + visibleRow) * columns + column;
					if (index < 0 || index >= cards.size()) {
						continue;
					}
					drawGalleryCard(graphics, layout, mediaGalleryCardRect(layout, visibleRow, column), cards.get(index));
				}
			}
			drawGalleryScrollbar(graphics, scrollbarTrackRect, scroll, visibleRows, totalRows, layout);
		}

		TaskProgress.Snapshot progress = state != null ? state.progress() : null;
		if (progress != null && progress.visible()) {
			drawProgressBar(graphics, mediaProgressRect(layout), progress, layout);
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
				withAlpha(app.accentStartRgb(), 88),
				cardRect.right(),
				cardRect.bottom(),
				withAlpha(app.accentEndRgb(), 88)
		));
		fillRoundedRect(graphics, cardRect, clampInt(layout.unit() * 2, 12, 36), null);
		strokeRoundedRect(graphics, cardRect, clampInt(layout.unit() * 2, 12, 36), 1.0F, new Color(255, 255, 255, 38));

		UiRect iconRect = homeAppIconRect(cardRect, layout);
		drawAppIcon(graphics, app, iconRect, clampInt(layout.unit() / 3, 2, 12));

		UiRect labelRect = homeAppLabelRect(layout, cardRect);
		int textSize = clampInt(layout.unit() * 2 - 1, 9, 26);
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, textSize));
		var metrics = graphics.getFontMetrics();
		int textBgWidth = Math.min(labelRect.width(), metrics.stringWidth(app.title()) + clampInt(layout.unit(), 10, 28));
		UiRect textBgRect = new UiRect(
				labelRect.x() + (labelRect.width() - textBgWidth) / 2,
				labelRect.y(),
				textBgWidth,
				labelRect.height()
		);
		fillRoundedRect(graphics, textBgRect, clampInt(layout.unit(), 8, 24), new Color(12, 16, 20, 112));
		drawCenteredTextFitted(graphics, app.title(), labelRect, new Color(248, 251, 255), Font.BOLD, textSize, clampInt(layout.unit() - 1, 7, 14));
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

	private static void drawMediaBackButton(Graphics2D graphics, UiRect rect, UiLayout layout) {
		drawRoundMediaButtonBase(graphics, rect);
		drawBackArrow(graphics, rect.inset(Math.max(1, layout.unit() / 5)), new Color(248, 251, 255));
	}

	private static void drawMediaScaleButton(Graphics2D graphics, UiRect rect, MediaScaleMode scaleMode, UiLayout layout, MediaButtonSegment segment) {
		float strokeWidth = mediaChromeStrokeWidth(rect);
		Color iconColor = drawSmallMediaButtonBase(graphics, rect, segment, false, strokeWidth);
		UiRect iconRect = mediaChromeIconRect(rect, layout);
		switch (scaleMode != null ? scaleMode : MediaScaleMode.FIT) {
			case FILL -> drawMediaFillGlyph(graphics, iconRect, iconColor, strokeWidth);
			case STRETCH -> drawMediaStretchGlyph(graphics, iconRect, iconColor, strokeWidth);
			case FIT -> drawMediaFitGlyph(graphics, iconRect, iconColor, strokeWidth);
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
		drawRoundMediaButtonBase(graphics, rect, new Color(12, 16, 20, 214));
	}

	private static void drawRoundMediaButtonBase(Graphics2D graphics, UiRect rect, Color fill) {
		int arc = Math.min(rect.width(), rect.height());
		fillRoundedRect(graphics, rect, arc, fill);
		strokeRoundedRect(graphics, rect, arc, 1.0F, new Color(255, 255, 255, 44));
	}

	private static Color drawSmallMediaButtonBase(Graphics2D graphics, UiRect rect, MediaButtonSegment segment, boolean active, float strokeWidth) {
		Shape shape = mediaButtonShape(rect, segment);
		Color outline = active ? new Color(255, 255, 255, 76) : new Color(255, 255, 255, 76);
		Color fill = active ? new Color(248, 246, 246, 242) : null;
		if (fill != null) {
			fillShape(graphics, shape, fill);
		}
		strokeShape(graphics, shape, Math.max(0.75F, strokeWidth * 0.5F), outline);
		return active ? new Color(24, 22, 24, 238) : new Color(244, 232, 236, 188);
	}

	private static UiRect mediaChromeIconRect(UiRect rect, UiLayout layout) {
		int inset = clampInt(layout.unit() / 2, 4, 8);
		int iconSize = clampInt(layout.unit() + 3, 11, 15);
		iconSize = Math.min(iconSize, Math.max(8, Math.min(rect.width() - inset * 2, rect.height() - inset * 2)));
		int offsetX = Math.max(0, iconSize / 14);
		return new UiRect(
				rect.x() + (rect.width() - iconSize) / 2 + offsetX,
				rect.y() + (rect.height() - iconSize) / 2,
				iconSize,
				iconSize
		);
	}

	private static float mediaChromeStrokeWidth(UiRect rect) {
		return clampFloat(Math.min(rect.width(), rect.height()) / 12.0F, 1.5F, 2.2F);
	}

	private static MediaButtonSegment mediaButtonSegment(int index, int total) {
		if (total <= 1) {
			return MediaButtonSegment.SINGLE;
		}
		if (index <= 0) {
			return MediaButtonSegment.LEFT;
		}
		if (index >= total - 1) {
			return MediaButtonSegment.RIGHT;
		}
		return MediaButtonSegment.MIDDLE;
	}

	private static Shape mediaButtonShape(UiRect rect, MediaButtonSegment segment) {
		int outer = clampInt(Math.min(rect.width(), rect.height()) / 2, 9, 18);
		int inner = clampInt(Math.min(rect.width(), rect.height()) / 6, 3, 8);
		return switch (segment != null ? segment : MediaButtonSegment.SINGLE) {
			case LEFT -> roundedRectShape(rect, outer, inner, inner, outer);
			case MIDDLE -> roundedRectShape(rect, inner, inner, inner, inner);
			case RIGHT -> roundedRectShape(rect, inner, outer, outer, inner);
			case SINGLE -> roundedRectShape(rect, outer, outer, outer, outer);
		};
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
				rect.right() - iconRect.right() - clampInt(layout.unit() * 4, 20, 52),
				rect.height()
		);
		drawVerticalText(graphics, placeholder, textRect, new Color(248, 251, 255, compact ? 214 : 236), Font.BOLD, clampInt(layout.unit() - (compact ? 1 : 0), 9, compact ? 14 : 18));
	}

	private static void drawMediaActionButton(Graphics2D graphics, UiRect rect, boolean deleteMode, UiLayout layout) {
		drawMediaIconActionButton(
				graphics,
				rect,
				deleteMode ? new Color(66, 18, 24, 222) : new Color(20, 58, 94, 222),
				layout,
				deleteMode ? MediaActionGlyph.TRASH : MediaActionGlyph.DOWNLOAD,
				MediaActionVisualState.IDLE,
				MediaButtonSegment.SINGLE
		);
	}

	private static void drawMediaTitleBar(Graphics2D graphics, UiRect rect, String title, UiLayout layout) {
		int arc = Math.min(rect.height(), rect.width());
		graphics.setPaint(new GradientPaint(
				rect.x(),
				rect.y(),
				new Color(12, 16, 20, 228),
				rect.right(),
				rect.bottom(),
				new Color(22, 30, 38, 204)
		));
		fillRoundedRect(graphics, rect, arc, null);
		strokeRoundedRect(graphics, rect, arc, 1.0F, new Color(255, 255, 255, 64));
		drawVerticalText(
				graphics,
				(title == null || title.isBlank()) ? "ГАЛЕРЕЯ" : title,
				new UiRect(
						rect.x() + clampInt(layout.unit() / 2, 4, 8),
						rect.y(),
						rect.width() - clampInt(layout.unit(), 8, 16),
						rect.height()
				),
				new Color(248, 251, 255, 236),
				Font.BOLD,
				clampInt(layout.unit() - (compactScreenLayout(layout) ? 1 : 0), 8, 16)
		);
	}

	private static void drawGalleryPlayerActionButton(Graphics2D graphics, UiRect rect, MediaVisualSnapshot state, UiLayout layout, MediaButtonSegment segment) {
		if (state == null) {
			return;
		}
		drawMediaIconActionButton(
				graphics,
				rect,
				state.actionGlyph() == MediaActionGlyph.TRASH ? new Color(70, 20, 28, 214) : new Color(18, 70, 42, 214),
				layout,
				state.actionGlyph(),
				state.actionState(),
				segment
		);
	}

	private static void drawGalleryWallpaperActionButton(Graphics2D graphics, UiRect rect, MediaVisualSnapshot state, UiLayout layout, MediaButtonSegment segment) {
		if (state == null) {
			return;
		}
		drawMediaIconActionButton(
				graphics,
				rect,
				new Color(74, 54, 18, 214),
				layout,
				state.wallpaperActionGlyph(),
				state.wallpaperActionState(),
				segment
		);
	}

	private static void drawYoutubePlayerActionButton(Graphics2D graphics, UiRect rect, MediaVisualSnapshot state, UiLayout layout, MediaButtonSegment segment) {
		if (state == null) {
			return;
		}
		drawMediaIconActionButton(
				graphics,
				rect,
				new Color(20, 58, 94, 222),
				layout,
				state.actionGlyph(),
				state.actionState(),
				segment
		);
	}

	private static void drawMediaIconActionButton(Graphics2D graphics, UiRect rect, Color fill, UiLayout layout, MediaActionGlyph glyph, MediaActionVisualState visualState, MediaButtonSegment segment) {
		boolean active = visualState == MediaActionVisualState.COMPLETE || visualState == MediaActionVisualState.DOWNLOADING;
		float strokeWidth = mediaChromeStrokeWidth(rect);
		Color iconColor = drawSmallMediaButtonBase(graphics, rect, segment, active, strokeWidth);
		UiRect iconRect = mediaChromeIconRect(rect, layout);
		if (visualState == MediaActionVisualState.DOWNLOADING) {
			drawLoadingSpinner(graphics, iconRect, iconColor, Math.max(1.6F, strokeWidth));
			return;
		}
		if (visualState == MediaActionVisualState.COMPLETE) {
			drawCheckGlyph(graphics, iconRect, iconColor, strokeWidth);
			return;
		}
		switch (glyph) {
			case TRASH -> drawTrashGlyph(graphics, iconRect, iconColor, strokeWidth);
			case DOWNLOAD -> drawDownloadGlyph(graphics, iconRect, iconColor, strokeWidth);
			case CHECK -> drawCheckGlyph(graphics, iconRect, iconColor, strokeWidth);
			case WALLPAPER -> drawWallpaperGlyph(graphics, iconRect, iconColor, strokeWidth);
		}
	}

	private static void drawMediaTimeline(Graphics2D graphics, UiRect rect, MediaVisualSnapshot state, UiLayout layout) {
		if (state == null || !state.timelineVisible()) {
			return;
		}
		boolean youtubeMusicMode = isYoutubeMusicMode(state.mode());
		UiRect trackRect = mediaTimelineTrackRect(layout, state.mode());
		if (trackRect.width() <= 0 || trackRect.height() <= 0) {
			return;
		}
		UiRect counterRect = mediaTimelineCounterRect(layout, state.mode());
		String timelineLabel = resolvedTimelineLabel(state, layout);
		Color trackFill = youtubeMusicMode ? new Color(82, 64, 70, 212) : new Color(255, 255, 255, 40);
		Color bufferedFill = youtubeMusicMode ? new Color(158, 138, 146, 164) : new Color(255, 255, 255, 90);
		Color playedFill = new Color(248, 251, 255, 242);
		int trackArc = Math.min(trackRect.height(), trackRect.width());
		fillRoundedRect(graphics, trackRect, trackArc, trackFill);
		float bufferedStart = clampFloat(state.bufferedStartFraction(), 0.0F, 1.0F);
		float bufferedEnd = clampFloat(Math.max(state.bufferedEndFraction(), state.timelineFraction()), 0.0F, 1.0F);
		if (bufferedEnd > bufferedStart) {
			int bufferedX = trackRect.x() + Math.round(trackRect.width() * bufferedStart);
			int bufferedWidth = Math.max(trackRect.height(), Math.round(trackRect.width() * (bufferedEnd - bufferedStart)));
			fillRoundedRect(
					graphics,
					new UiRect(bufferedX, trackRect.y(), Math.min(trackRect.right() - bufferedX, bufferedWidth), trackRect.height()),
					trackArc,
					bufferedFill
			);
		}
		float fraction = state.timelineFraction();
		int progressWidth = Math.max(trackRect.height(), Math.round(trackRect.width() * fraction));
		fillRoundedRect(graphics, new UiRect(trackRect.x(), trackRect.y(), Math.min(trackRect.width(), progressWidth), trackRect.height()), trackArc, playedFill);
		if (state.timelineSeekable()) {
			int markerWidth = clampInt(Math.max(4, trackRect.height() / 2), 4, 8);
			int markerHeight = clampInt(trackRect.height() + layout.unit() + 2, trackRect.height() + 8, trackRect.height() + 24);
			int markerCenterX = trackRect.x() + Math.round(trackRect.width() * fraction);
			int markerX = clampInt(markerCenterX - markerWidth / 2, trackRect.x(), Math.max(trackRect.x(), trackRect.right() - markerWidth));
			int markerY = trackRect.y() + (trackRect.height() - markerHeight) / 2;
			fillRoundedRect(
					graphics,
					new UiRect(markerX, markerY, markerWidth, markerHeight),
					markerWidth,
					new Color(248, 251, 255, 248)
			);
		}
		if (youtubeMusicMode) {
			int timeFont = ultraCompactScreenLayout(layout)
					? clampInt(layout.unit(), 7, 10)
					: compactScreenLayout(layout)
					? clampInt(layout.unit() + 1, 9, 12)
					: clampInt(layout.unit() + 1, 11, 15);
			drawVerticalText(
					graphics,
					timelineLeadingLabel(state),
					mediaYoutubeMusicCurrentTimeRect(layout),
					new Color(244, 232, 236, 228),
					Font.PLAIN,
					timeFont
			);
			drawRightAlignedText(
					graphics,
					timelineTrailingLabel(state),
					mediaYoutubeMusicTotalTimeRect(layout),
					new Color(244, 232, 236, 228),
					Font.PLAIN,
					timeFont
			);
		} else if (!timelineLabel.isBlank() && counterRect.width() > 0) {
			drawCenteredTextFitted(
					graphics,
					timelineLabel,
					counterRect,
					new Color(248, 251, 255, 214),
					Font.BOLD,
					clampInt(layout.unit() - 1, 8, 14),
					clampInt(layout.unit() - 4, 6, 10)
			);
		}
	}

	private static void drawGalleryCard(Graphics2D graphics, UiLayout layout, UiRect rect, GalleryCardSnapshot card) {
		if (graphics == null || layout == null || rect == null || card == null) {
			return;
		}
		Color fill = card.current() ? new Color(86, 188, 255, 82) : new Color(255, 255, 255, 14);
		Color stroke = card.current() ? new Color(140, 220, 255, 116) : new Color(255, 255, 255, 28);
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), fill);
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), 1.0F, stroke);

		UiRect previewRect = mediaGalleryCardPreviewRect(rect, layout);
		if (card.preview() != null) {
			drawScaledImage(graphics, card.preview(), previewRect, MediaScaleMode.FILL);
		} else {
			fillRoundedRect(graphics, previewRect, clampInt(layout.unit() * 2, 8, 14), new Color(255, 255, 255, 10));
		}

		if (card.animated()) {
			UiRect playBadge = mediaGalleryCardPlayBadgeRect(previewRect, layout);
			drawRoundMediaButtonBase(graphics, playBadge, new Color(12, 16, 20, 196));
			drawPlayGlyph(graphics, playBadge.inset(Math.max(2, layout.unit() / 5)), new Color(248, 251, 255));
		}
	}

	private static void drawMediaQueueToggleButton(Graphics2D graphics, UiRect rect, boolean open, UiLayout layout, MediaButtonSegment segment) {
		float strokeWidth = mediaChromeStrokeWidth(rect);
		Color iconColor = drawSmallMediaButtonBase(graphics, rect, segment, open, strokeWidth);
		drawQueueGlyph(graphics, mediaChromeIconRect(rect, layout), iconColor, strokeWidth);
	}

	private static void drawYoutubeMusicSearchButton(Graphics2D graphics, UiRect rect, UiLayout layout, MediaButtonSegment segment) {
		float strokeWidth = mediaChromeStrokeWidth(rect);
		Color iconColor = drawSmallMediaButtonBase(graphics, rect, segment, false, strokeWidth);
		drawSearchGlyph(graphics, mediaChromeIconRect(rect, layout), iconColor);
	}

	private static void drawQueueScrollButton(Graphics2D graphics, UiRect rect, String label, boolean active, UiLayout layout) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), active ? new Color(255, 255, 255, 18) : new Color(255, 255, 255, 8));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 10, 18), 1.0F, new Color(255, 255, 255, active ? 34 : 18));
		drawCenteredText(graphics, label, rect, new Color(248, 251, 255, active ? 228 : 90), Font.BOLD, clampInt(layout.unit() + 1, 10, 16));
	}

	private static void drawMediaOverlayWindow(Graphics2D graphics, UiLayout layout, ScreenRuntimeKey runtimeKey, MinecraftServer server, MediaOverlayWindowSnapshot window) {
		if (graphics == null || layout == null || window == null) {
			return;
		}
		BufferedImage image = overlayWindowImage(server, runtimeKey, window, layout);
		UiRect rect = overlayWindowRect(layout, window.type());
		graphics.drawImage(image, rect.x(), rect.y(), null);
	}

	private static BufferedImage overlayWindowImage(MinecraftServer server, ScreenRuntimeKey runtimeKey, MediaOverlayWindowSnapshot window, UiLayout layout) {
		UiRect rect = overlayWindowRect(layout, window.type());
		OverlayWindowCacheKey key = new OverlayWindowCacheKey(window, rect.width(), rect.height(), layout.unit());
		OverlayWindowFamilyKey familyKey = new OverlayWindowFamilyKey(window.type(), rect.width(), rect.height(), layout.unit());
		OverlayWindowRenderState cached = OVERLAY_WINDOW_CACHE.computeIfAbsent(key, ignored -> new OverlayWindowRenderState());
		cached.lastAccessNanos = System.nanoTime();
		BufferedImage ready = cached.image;
		if (ready != null) {
			return ready;
		}
		scheduleOverlayWindowRender(server, runtimeKey, window, layout, rect, key, familyKey, cached);
		BufferedImage familyFallback = OVERLAY_WINDOW_FAMILY_CACHE.get(familyKey);
		if (familyFallback != null) {
			return familyFallback;
		}
		return OVERLAY_WINDOW_PLACEHOLDER_CACHE.computeIfAbsent(
				familyKey,
				ignored -> renderOverlayWindowPlaceholder(window.type(), layout, rect)
		);
	}

	private static void scheduleOverlayWindowRender(
			MinecraftServer server,
			ScreenRuntimeKey runtimeKey,
			MediaOverlayWindowSnapshot window,
			UiLayout layout,
			UiRect rect,
			OverlayWindowCacheKey key,
			OverlayWindowFamilyKey familyKey,
			OverlayWindowRenderState cacheState
	) {
		if (window == null || layout == null || rect == null || cacheState == null) {
			return;
		}
		ensureExecutors();
		synchronized (cacheState) {
			if (cacheState.image != null) {
				return;
			}
			if (cacheState.future != null && !cacheState.future.isDone()) {
				return;
			}
			cacheState.future = CompletableFuture
					.supplyAsync(() -> renderOverlayWindowImage(window, layout, rect), overlayWindowExecutor)
					.whenComplete((rendered, throwable) -> {
						if (throwable != null) {
							OVERLAY_WINDOW_CACHE.remove(key, cacheState);
							Lg2.LOGGER.debug("Failed to render overlay window {}", window.type(), throwable);
							return;
						}
						cacheState.image = rendered;
						cacheState.lastAccessNanos = System.nanoTime();
						OVERLAY_WINDOW_FAMILY_CACHE.put(familyKey, rendered);
						trimOverlayWindowCaches();
						if (server != null && runtimeKey != null) {
							server.execute(() -> requestRuntimeRender(server, runtimeKey));
						}
					});
		}
	}

	private static void trimOverlayWindowCaches() {
		if (OVERLAY_WINDOW_CACHE.size() <= 256 && OVERLAY_WINDOW_FAMILY_CACHE.size() <= 24 && OVERLAY_WINDOW_PLACEHOLDER_CACHE.size() <= 24) {
			return;
		}
		OVERLAY_WINDOW_CACHE.entrySet().removeIf(entry -> {
			OverlayWindowRenderState state = entry.getValue();
			return state == null
					|| (state.image != null && System.nanoTime() - state.lastAccessNanos > TimeUnit.MINUTES.toNanos(3));
		});
		if (OVERLAY_WINDOW_CACHE.size() > 320) {
			OVERLAY_WINDOW_CACHE.clear();
		}
		if (OVERLAY_WINDOW_FAMILY_CACHE.size() > 32) {
			OVERLAY_WINDOW_FAMILY_CACHE.clear();
		}
		if (OVERLAY_WINDOW_PLACEHOLDER_CACHE.size() > 32) {
			OVERLAY_WINDOW_PLACEHOLDER_CACHE.clear();
		}
	}

	private static BufferedImage renderOverlayWindowImage(MediaOverlayWindowSnapshot window, UiLayout layout, UiRect rect) {
		BufferedImage image = new BufferedImage(Math.max(1, rect.width()), Math.max(1, rect.height()), BufferedImage.TYPE_INT_ARGB);
		Graphics2D overlayGraphics = image.createGraphics();
		configureUiGraphics(overlayGraphics);
		overlayGraphics.translate(-rect.x(), -rect.y());
		switch (window.type()) {
			case YOUTUBE_QUEUE -> drawYoutubeQueueWindow(overlayGraphics, layout, window);
			case GALLERY_DELETE_CONFIRM -> drawGalleryDeleteConfirmWindow(overlayGraphics, layout, window);
		}
		overlayGraphics.dispose();
		return image;
	}

	private static BufferedImage renderOverlayWindowPlaceholder(MediaOverlayWindowType type, UiLayout layout, UiRect rect) {
		BufferedImage image = new BufferedImage(Math.max(1, rect.width()), Math.max(1, rect.height()), BufferedImage.TYPE_INT_ARGB);
		Graphics2D overlayGraphics = image.createGraphics();
		configureUiGraphics(overlayGraphics);
		overlayGraphics.translate(-rect.x(), -rect.y());
		switch (type) {
			case YOUTUBE_QUEUE -> drawYoutubeQueueWindowPlaceholder(overlayGraphics, layout);
			case GALLERY_DELETE_CONFIRM -> drawGalleryDeleteConfirmWindowPlaceholder(overlayGraphics, layout);
		}
		overlayGraphics.dispose();
		return image;
	}

	private static void drawOverlayModalBase(Graphics2D graphics, UiLayout layout, UiRect panel, UiRect header, UiRect closeRect, String title, String subtitle) {
		if (graphics == null || layout == null || panel == null || header == null || closeRect == null) {
			return;
		}
		graphics.setPaint(new GradientPaint(
				panel.x(),
				panel.y(),
				new Color(8, 12, 18, 242),
				panel.right(),
				panel.bottom(),
				new Color(20, 26, 34, 236)
		));
		fillRoundedRect(graphics, panel, clampInt(layout.unit() * 3, 16, 28), null);
		strokeRoundedRect(graphics, panel, clampInt(layout.unit() * 3, 16, 28), 1.2F, new Color(255, 255, 255, 62));

		fillRoundedRect(graphics, header, clampInt(layout.unit() * 2, 12, 22), new Color(255, 255, 255, 14));
		strokeRoundedRect(graphics, header, clampInt(layout.unit() * 2, 12, 22), 1.0F, new Color(255, 255, 255, 34));
		drawVerticalText(
				graphics,
				title,
				new UiRect(
						header.x() + clampInt(layout.unit(), 8, 14),
						header.y(),
						Math.max(16, closeRect.x() - header.x() - layout.unit() * 2),
						header.height() / 2 + 2
				),
				new Color(248, 251, 255),
				Font.BOLD,
				compactScreenLayout(layout) ? clampInt(layout.unit() + (ultraCompactScreenLayout(layout) ? 1 : 2), 9, 16) : clampInt(layout.unit() + 4, 14, 24)
		);
		if (subtitle != null && !subtitle.isBlank()) {
			drawVerticalText(
					graphics,
					subtitle,
					new UiRect(
							header.x() + clampInt(layout.unit(), 8, 14),
							header.y() + header.height() / 2 - 1,
							Math.max(16, closeRect.x() - header.x() - layout.unit() * 2),
							Math.max(12, header.bottom() - (header.y() + header.height() / 2 - 1) - 2)
					),
					new Color(178, 194, 212, 220),
					Font.PLAIN,
					compactScreenLayout(layout) ? clampInt(layout.unit(), 8, 11) : clampInt(layout.unit() + 1, 10, 15)
			);
		}
		drawMediaCloseButton(graphics, closeRect, layout);
	}

	private static void drawYoutubeQueueWindow(Graphics2D graphics, UiLayout layout, MediaOverlayWindowSnapshot window) {
		boolean compact = compactScreenLayout(layout);
		boolean ultraCompact = ultraCompactScreenLayout(layout);
		UiRect panel = mediaQueuePanelRect(layout);
		UiRect header = mediaQueueHeaderRect(layout);
		UiRect closeRect = mediaQueueCloseRect(layout);
		UiRect subtitleRect = mediaQueueSubtitleRect(layout);
		UiRect list = mediaQueueListRect(layout);
		UiRect footer = mediaQueueFooterRect(layout);
		UiRect footerInfoRect = mediaQueueFooterInfoRect(layout);
		UiRect scrollbarTrackRect = mediaQueueScrollbarTrackRect(layout);
		int visibleRows = mediaQueueVisibleRows(layout);
		int scroll = clampInt(window.scroll(), 0, Math.max(0, window.items().size() - visibleRows));

		drawOverlayModalBase(graphics, layout, panel, header, closeRect, window.title(), window.subtitle());

		if (window.items().isEmpty()) {
			drawCenteredText(graphics, "Очередь пуста", list, new Color(210, 218, 226, 214), Font.PLAIN, compact ? clampInt(layout.unit() + 1, 9, 13) : clampInt(layout.unit() + 2, 12, 18));
		} else {
			int rowCount = Math.min(visibleRows, Math.max(0, window.items().size() - scroll));
			for (int visibleIndex = 0; visibleIndex < rowCount; visibleIndex++) {
				YoutubeQueueItemSnapshot item = window.items().get(scroll + visibleIndex);
				UiRect rowRect = mediaQueueRowRect(layout, visibleIndex);
				UiRect removeRect = mediaQueueRemoveRect(rowRect, layout);
				UiRect badgeRect = mediaQueueIndexRect(rowRect, layout);
				UiRect titleRect = mediaQueueTitleRect(rowRect, removeRect, badgeRect, layout);
				Color fill = item.current() ? new Color(86, 188, 255, 84) : new Color(255, 255, 255, 18);
				Color stroke = item.current() ? new Color(140, 220, 255, 118) : new Color(255, 255, 255, 34);
				fillRoundedRect(graphics, rowRect, clampInt(layout.unit() * 2, 12, 18), fill);
				strokeRoundedRect(graphics, rowRect, clampInt(layout.unit() * 2, 12, 18), 1.0F, stroke);
				fillRoundedRect(graphics, badgeRect, clampInt(layout.unit() * 2, 10, 18), new Color(12, 16, 22, 196));
				drawCenteredText(graphics, Integer.toString(item.queueIndex() + 1), badgeRect, new Color(248, 251, 255), Font.BOLD, compact ? clampInt(layout.unit(), 8, 12) : clampInt(layout.unit() + 2, 11, 18));
				drawWrappedText(graphics, item.title(), titleRect, new Color(248, 251, 255, 232), item.current() ? Font.BOLD : Font.PLAIN, compact ? clampInt(layout.unit() + (ultraCompact ? 0 : 1), 8, 12) : clampInt(layout.unit() + 2, 12, 18), compact ? 2 : 3);
				fillRoundedRect(graphics, removeRect, clampInt(layout.unit() * 2, 10, 18), new Color(30, 18, 24, 214));
				strokeRoundedRect(graphics, removeRect, clampInt(layout.unit() * 2, 10, 18), 1.0F, new Color(255, 255, 255, 28));
				drawCloseGlyph(graphics, removeRect.inset(Math.max(2, layout.unit() / 5)), new Color(255, 232, 238));
			}
			drawQueueScrollbar(graphics, scrollbarTrackRect, scroll, visibleRows, window.items().size(), layout);
		}

		fillRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 18), new Color(255, 255, 255, 10));
		strokeRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 18), 1.0F, new Color(255, 255, 255, 24));
		drawCenteredText(
				graphics,
				window.items().isEmpty() ? "0/0" : ((window.currentIndex() >= 0 ? window.currentIndex() + 1 : 0) + "/" + window.items().size()),
				footerInfoRect,
				new Color(232, 238, 244, 204),
				Font.BOLD,
				compact ? clampInt(layout.unit(), 8, 12) : clampInt(layout.unit() + 1, 11, 16)
		);
	}

	private static void drawGalleryDeleteConfirmWindow(Graphics2D graphics, UiLayout layout, MediaOverlayWindowSnapshot window) {
		UiRect panel = galleryDeleteConfirmPanelRect(layout);
		UiRect header = galleryDeleteConfirmHeaderRect(layout);
		UiRect closeRect = galleryDeleteConfirmCloseRect(layout);
		UiRect bodyRect = galleryDeleteConfirmBodyRect(layout);
		UiRect cancelRect = galleryDeleteConfirmCancelRect(layout);
		UiRect confirmRect = galleryDeleteConfirmConfirmRect(layout);

		drawOverlayModalBase(graphics, layout, panel, header, closeRect, window.title(), window.subtitle());
		drawCenteredText(
				graphics,
				"Это удалит медиа из галереи этого экрана.",
				bodyRect,
				new Color(230, 236, 244, 232),
				Font.PLAIN,
				clampInt(layout.unit() + (compactScreenLayout(layout) ? 0 : 1), 9, 15)
		);
		drawOverlayModalActionButton(graphics, cancelRect, "ОТМЕНА", false, layout);
		drawOverlayModalActionButton(graphics, confirmRect, "УДАЛИТЬ", true, layout);
	}

	private static void drawGalleryDeleteConfirmWindowPlaceholder(Graphics2D graphics, UiLayout layout) {
		drawGalleryDeleteConfirmWindow(
				graphics,
				layout,
				new MediaOverlayWindowSnapshot(
						MediaOverlayWindowType.GALLERY_DELETE_CONFIRM,
						"УДАЛИТЬ?",
						"Подтверждение",
						List.of(),
						0,
						-1
				)
		);
	}

	private static void drawOverlayModalActionButton(Graphics2D graphics, UiRect rect, String label, boolean destructive, UiLayout layout) {
		fillRoundedRect(
				graphics,
				rect,
				clampInt(layout.unit() * 2, 10, 18),
				destructive ? new Color(118, 30, 42, 228) : new Color(34, 44, 56, 222)
		);
		strokeRoundedRect(
				graphics,
				rect,
				clampInt(layout.unit() * 2, 10, 18),
				1.0F,
				destructive ? new Color(255, 170, 186, 86) : new Color(255, 255, 255, 38)
		);
		drawCenteredText(
				graphics,
				label,
				rect,
				new Color(248, 251, 255),
				Font.BOLD,
				clampInt(layout.unit() + (compactScreenLayout(layout) ? 0 : 1), 8, 14)
		);
	}

	private static void drawYoutubeQueueWindowPlaceholder(Graphics2D graphics, UiLayout layout) {
		boolean compact = compactScreenLayout(layout);
		UiRect panel = mediaQueuePanelRect(layout);
		UiRect header = mediaQueueHeaderRect(layout);
		UiRect list = mediaQueueListRect(layout);
		UiRect footer = mediaQueueFooterRect(layout);
		UiRect footerInfoRect = mediaQueueFooterInfoRect(layout);
		UiRect scrollbarTrackRect = mediaQueueScrollbarTrackRect(layout);
		int arc = clampInt(layout.unit() * 3, 16, 28);

		graphics.setPaint(new GradientPaint(
				panel.x(),
				panel.y(),
				new Color(8, 12, 18, 238),
				panel.right(),
				panel.bottom(),
				new Color(20, 26, 34, 232)
		));
		fillRoundedRect(graphics, panel, arc, null);
		strokeRoundedRect(graphics, panel, arc, 1.0F, new Color(255, 255, 255, 36));

		fillRoundedRect(graphics, header, clampInt(layout.unit() * 2, 12, 22), new Color(255, 255, 255, 12));
		drawVerticalText(graphics, "Очередь", new UiRect(header.x() + clampInt(layout.unit(), 8, 14), header.y(), header.width(), header.height() / 2), new Color(248, 251, 255, 232), Font.BOLD, compact ? clampInt(layout.unit() + 2, 9, 16) : clampInt(layout.unit() + 4, 14, 24));
		drawVerticalText(graphics, "Загрузка окна...", mediaQueueSubtitleRect(layout), new Color(188, 198, 212, 176), Font.PLAIN, compact ? clampInt(layout.unit(), 8, 11) : clampInt(layout.unit() + 1, 10, 15));
		drawMediaCloseButton(graphics, mediaQueueCloseRect(layout), layout);

		int visibleRows = Math.min(3, mediaQueueVisibleRows(layout));
		for (int rowIndex = 0; rowIndex < visibleRows; rowIndex++) {
			UiRect rowRect = mediaQueueRowRect(layout, rowIndex);
			UiRect badgeRect = mediaQueueIndexRect(rowRect, layout);
			UiRect titleRect = mediaQueueTitleRect(rowRect, mediaQueueRemoveRect(rowRect, layout), badgeRect, layout);
			fillRoundedRect(graphics, rowRect, clampInt(layout.unit() * 2, 12, 18), new Color(255, 255, 255, 12));
			fillRoundedRect(graphics, badgeRect, clampInt(layout.unit() * 2, 10, 18), new Color(12, 16, 22, 180));
			fillRoundedRect(graphics, new UiRect(titleRect.x(), titleRect.y(), Math.max(18, titleRect.width() * 3 / 4), clampInt(layout.unit(), 10, 14)), clampInt(layout.unit(), 8, 12), new Color(255, 255, 255, 24));
			fillRoundedRect(graphics, new UiRect(titleRect.x(), titleRect.y() + clampInt(layout.unit() + 2, 10, 16), Math.max(16, titleRect.width() / 2), clampInt(layout.unit(), 10, 14)), clampInt(layout.unit(), 8, 12), new Color(255, 255, 255, 18));
		}
		fillRoundedRect(graphics, scrollbarTrackRect, clampInt(layout.unit(), 6, 10), new Color(255, 255, 255, 12));
		fillRoundedRect(graphics, mediaQueueScrollbarThumbRect(layout, 0, 1, 1), clampInt(layout.unit(), 6, 10), new Color(255, 255, 255, 42));

		fillRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 18), new Color(255, 255, 255, 10));
		strokeRoundedRect(graphics, footer, clampInt(layout.unit() * 2, 12, 18), 1.0F, new Color(255, 255, 255, 20));
		drawCenteredText(graphics, "1/1", footerInfoRect, new Color(232, 238, 244, 116), Font.BOLD, clampInt(layout.unit() + 1, 11, 16));
	}

	private static void drawQueueScrollbar(Graphics2D graphics, UiRect trackRect, int scroll, int visibleRows, int totalRows, UiLayout layout) {
		drawScrollbar(graphics, trackRect, scroll, visibleRows, totalRows, layout, mediaQueueScrollbarThumbRect(layout, scroll, visibleRows, totalRows));
	}

	private static void drawGalleryScrollbar(Graphics2D graphics, UiRect trackRect, int scroll, int visibleRows, int totalRows, UiLayout layout) {
		drawScrollbar(graphics, trackRect, scroll, visibleRows, totalRows, layout, mediaGalleryBrowserScrollbarThumbRect(layout, scroll, visibleRows, totalRows));
	}

	private static int scrollbarWidth(UiLayout layout) {
		return clampInt(layout.unit() / 2, 6, 10);
	}

	private static int scrollbarGap(UiLayout layout) {
		return clampInt(layout.unit() / 3, 3, 6);
	}

	private static int scrollbarGutterWidth(UiLayout layout) {
		return scrollbarWidth(layout) + scrollbarGap(layout);
	}

	private static UiRect scrollContentRect(UiRect viewport, UiLayout layout) {
		return new UiRect(
				viewport.x(),
				viewport.y(),
				Math.max(18, viewport.width() - scrollbarGutterWidth(layout)),
				viewport.height()
		);
	}

	private static UiRect scrollTrackRect(UiRect viewport, UiLayout layout) {
		int width = scrollbarWidth(layout);
		return new UiRect(
				viewport.right() - width,
				viewport.y(),
				width,
				viewport.height()
		);
	}

	private static void drawScrollbar(Graphics2D graphics, UiRect trackRect, int scroll, int visibleRows, int totalRows, UiLayout layout, UiRect thumbRect) {
		if (graphics == null || trackRect.width() <= 0 || trackRect.height() <= 0 || !scrollbarVisible(visibleRows, totalRows)) {
			return;
		}
		fillRoundedRect(graphics, trackRect, clampInt(layout.unit(), 6, 10), new Color(255, 255, 255, 12));
		strokeRoundedRect(graphics, trackRect, clampInt(layout.unit(), 6, 10), 1.0F, new Color(255, 255, 255, 20));
		fillRoundedRect(graphics, thumbRect, clampInt(layout.unit(), 6, 10), new Color(255, 255, 255, 72));
	}

	private static boolean scrollbarVisible(int visibleRows, int totalRows) {
		return totalRows > visibleRows && visibleRows > 0;
	}

	private static int scrollValueForTrack(UiRect trackRect, int visibleRows, int totalRows, int pointerY) {
		if (trackRect == null || !scrollbarVisible(visibleRows, totalRows)) {
			return 0;
		}
		int maxScroll = Math.max(0, totalRows - visibleRows);
		if (maxScroll <= 0) {
			return 0;
		}
		double fraction = clampDouble((pointerY - trackRect.y()) / (double) Math.max(1, trackRect.height() - 1), 0.0D, 1.0D);
		return clampInt((int) Math.round(fraction * maxScroll), 0, maxScroll);
	}

	private static void drawScaledImage(Graphics2D graphics, BufferedImage image, UiRect rect, MediaScaleMode scaleMode) {
		if (image == null || rect.width() <= 0 || rect.height() <= 0) {
			return;
		}
		Shape previousClip = graphics.getClip();
		if (previousClip == null) {
			graphics.setClip(rect.x(), rect.y(), rect.width(), rect.height());
		} else {
			graphics.clipRect(rect.x(), rect.y(), rect.width(), rect.height());
		}
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

	private static void drawRoundedScaledImage(Graphics2D graphics, BufferedImage image, UiRect rect, MediaScaleMode scaleMode, int arc) {
		if (graphics == null || image == null || rect == null || rect.width() <= 0 || rect.height() <= 0) {
			return;
		}
		Shape previousClip = graphics.getClip();
		RoundRectangle2D.Float clip = new RoundRectangle2D.Float(rect.x(), rect.y(), rect.width(), rect.height(), arc, arc);
		graphics.setClip(clip);
		drawScaledImage(graphics, image, rect, scaleMode);
		graphics.setClip(previousClip);
	}

	private static void drawYoutubeMusicArtworkBackground(Graphics2D graphics, UiRect rect, BufferedImage image, MediaScaleMode scaleMode) {
		if (graphics == null || rect == null || image == null || rect.width() <= 0 || rect.height() <= 0) {
			return;
		}
		Composite previousComposite = graphics.getComposite();
		graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.26F));
		drawScaledImage(graphics, image, rect, scaleMode);
		graphics.setComposite(previousComposite);
	}

	private static MediaScaleMode secondaryArtworkScaleMode(MediaScaleMode scaleMode) {
		MediaScaleMode normalized = scaleMode != null ? scaleMode : MediaScaleMode.FIT;
		if (normalized == MediaScaleMode.STRETCH) {
			return MediaScaleMode.FILL;
		}
		return normalized == MediaScaleMode.FILL ? MediaScaleMode.FIT : MediaScaleMode.FILL;
	}

	private static void drawYoutubeMusicArtworkCard(Graphics2D graphics, UiLayout layout, BufferedImage image, MediaScaleMode scaleMode) {
		UiRect artworkRect = mediaYoutubeMusicArtworkRect(layout);
		int arc = clampInt(layout.unit() * 2, 12, 28);
		drawRoundedScaledImage(
				graphics,
				image,
				artworkRect,
				scaleMode == MediaScaleMode.STRETCH ? MediaScaleMode.FILL : scaleMode,
				arc
		);
	}

	private static UiRect mediaYoutubeMusicInfoRect(UiLayout layout) {
		UiRect artworkRect = mediaYoutubeMusicArtworkRect(layout);
		int gap = clampInt(layout.unit(), 8, 16);
		if (youtubeMusicLandscapeLayout(layout)) {
			UiRect closeRect = mediaCloseRect(layout);
			int sideInset = clampInt(layout.unit() * 2, 12, 28);
			int right = artworkRect.x() - clampInt(layout.unit() * 2, 12, 24);
			return new UiRect(
					sideInset,
					closeRect.bottom() + clampInt(layout.unit(), 6, 14),
					Math.max(48, right - sideInset),
					mediaYoutubeMusicInfoHeight(layout)
			);
		}
		return new UiRect(
				artworkRect.x(),
				artworkRect.bottom() + gap,
				artworkRect.width(),
				mediaYoutubeMusicInfoHeight(layout)
		);
	}

	private static UiRect mediaYoutubeMusicTitleRect(UiLayout layout) {
		UiRect infoRect = mediaYoutubeMusicInfoRect(layout);
		int titleHeight = Math.max(clampInt(layout.unit() * 2, 14, 28), (int) Math.round(infoRect.height() * 0.66D));
		return new UiRect(infoRect.x(), infoRect.y(), infoRect.width(), Math.min(infoRect.height(), titleHeight));
	}

	private static UiRect mediaYoutubeMusicArtistRect(UiLayout layout) {
		UiRect infoRect = mediaYoutubeMusicInfoRect(layout);
		UiRect titleRect = mediaYoutubeMusicTitleRect(layout);
		int gap = clampInt(layout.unit() / 5, 2, 4);
		return new UiRect(
				infoRect.x(),
				Math.min(infoRect.bottom(), titleRect.bottom() + gap),
				infoRect.width(),
				Math.max(10, infoRect.bottom() - titleRect.bottom() - gap)
		);
	}

	private static int mediaYoutubeMusicInfoHeight(UiLayout layout) {
		return ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 3 + 2, 18, 28)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 4, 24, 40)
				: clampInt(layout.unit() * 5, 34, 60);
	}

	private static int mediaYoutubeMusicTrackHeight(UiLayout layout) {
		return ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() / 2 + 2, 8, 10)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() / 2 + 3, 9, 12)
				: clampInt(layout.unit() / 2 + 4, 10, 14);
	}

	private static int mediaYoutubeMusicTimeRowHeight(UiLayout layout) {
		return ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 14, 18)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 1, 16, 22)
				: clampInt(layout.unit() * 2 + 2, 18, 26);
	}

	private static int mediaYoutubeMusicControlsRowHeight(UiLayout layout) {
		return ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 4, 24, 30)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 4 + 2, 30, 40)
				: clampInt(layout.unit() * 5, 38, 54);
	}

	private static int mediaYoutubeMusicActionsRowHeight(UiLayout layout) {
		return ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 18, 22)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 2, 20, 28)
				: clampInt(layout.unit() * 2 + 4, 24, 34);
	}

	private static UiRect mediaYoutubeMusicCurrentTimeRect(UiLayout layout) {
		UiRect timelineRect = mediaTimelineRect(layout, ScreenViewMode.YOUTUBE_MUSIC);
		int top = timelineRect.bottom() + clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(timelineRect.x(), top, Math.max(16, timelineRect.width() / 2), mediaYoutubeMusicTimeRowHeight(layout));
	}

	private static UiRect mediaYoutubeMusicTotalTimeRect(UiLayout layout) {
		UiRect timelineRect = mediaTimelineRect(layout, ScreenViewMode.YOUTUBE_MUSIC);
		UiRect leftRect = mediaYoutubeMusicCurrentTimeRect(layout);
		return new UiRect(leftRect.right(), leftRect.y(), Math.max(16, timelineRect.right() - leftRect.right()), leftRect.height());
	}

	private static UiRect mediaYoutubeMusicControlsRowRect(UiLayout layout) {
		UiRect timelineRect = mediaTimelineRect(layout, ScreenViewMode.YOUTUBE_MUSIC);
		UiRect totalTimeRect = mediaYoutubeMusicTotalTimeRect(layout);
		int top = totalTimeRect.bottom() + clampInt(layout.unit(), 8, 16);
		return new UiRect(timelineRect.x(), top, timelineRect.width(), mediaYoutubeMusicControlsRowHeight(layout));
	}

	private static UiRect mediaYoutubeMusicActionsRowRect(UiLayout layout) {
		if (youtubeMusicLandscapeLayout(layout)) {
			UiRect artworkRect = mediaYoutubeMusicArtworkRect(layout);
			int top = artworkRect.bottom() + clampInt(layout.unit(), 6, 14);
			return new UiRect(artworkRect.x(), top, artworkRect.width(), mediaYoutubeMusicActionsRowHeight(layout));
		}
		UiRect controlsRect = mediaYoutubeMusicControlsRowRect(layout);
		int top = controlsRect.bottom() + clampInt(layout.unit(), 8, 16);
		return new UiRect(controlsRect.x(), top, controlsRect.width(), mediaYoutubeMusicActionsRowHeight(layout));
	}

	private static void drawYoutubeMusicTrackInfo(Graphics2D graphics, UiLayout layout, MediaVisualSnapshot state) {
		if (graphics == null || layout == null || state == null) {
			return;
		}
		String title = state.mediaTitle();
		if (title == null || title.isBlank()) {
			title = "Track";
		}
		drawWrappedText(
				graphics,
				title,
				mediaYoutubeMusicTitleRect(layout),
				new Color(248, 251, 255, 242),
				Font.BOLD,
				ultraCompactScreenLayout(layout)
						? clampInt(layout.unit() + 1, 9, 12)
						: compactScreenLayout(layout)
						? clampInt(layout.unit() + 2, 11, 15)
						: clampInt(layout.unit() + 3, 14, 20),
				2
		);
		if (state.mediaSubtitle() != null && !state.mediaSubtitle().isBlank()) {
			drawWrappedText(
					graphics,
					state.mediaSubtitle(),
					mediaYoutubeMusicArtistRect(layout),
					new Color(214, 221, 230, 204),
					Font.PLAIN,
					ultraCompactScreenLayout(layout)
							? clampInt(layout.unit(), 8, 10)
							: compactScreenLayout(layout)
							? clampInt(layout.unit() + 1, 9, 12)
							: clampInt(layout.unit() + 1, 11, 15),
					1
			);
		}
	}

	private static void drawCloseGlyph(Graphics2D graphics, UiRect rect, Color color) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.CLOSE, color);
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

	private static void drawSeekGlyph(Graphics2D graphics, UiRect rect, Color color, boolean backward) {
		int padX = Math.max(2, rect.width() / 6);
		int padY = Math.max(2, rect.height() / 6);
		int barWidth = Math.max(2, rect.width() / 8);
		int triangleWidth = Math.max(6, rect.width() - padX * 2 - barWidth - Math.max(2, rect.width() / 10));
		int gap = Math.max(2, rect.width() / 10);
		UiRect barRect;
		UiRect triangleRect;
		if (backward) {
			barRect = new UiRect(rect.x() + padX, rect.y() + padY, barWidth, Math.max(8, rect.height() - padY * 2));
			triangleRect = new UiRect(barRect.right() + gap, rect.y() + padY, triangleWidth, Math.max(8, rect.height() - padY * 2));
		} else {
			triangleRect = new UiRect(rect.x() + padX, rect.y() + padY, triangleWidth, Math.max(8, rect.height() - padY * 2));
			barRect = new UiRect(triangleRect.right() + gap, rect.y() + padY, barWidth, Math.max(8, rect.height() - padY * 2));
		}
		graphics.setColor(color);
		graphics.fillRoundRect(barRect.x(), barRect.y(), barRect.width(), barRect.height(), barRect.width(), barRect.width());
		if (backward) {
			graphics.fillPolygon(
					new int[]{triangleRect.right(), triangleRect.right(), triangleRect.x()},
					new int[]{triangleRect.y(), triangleRect.bottom(), triangleRect.y() + triangleRect.height() / 2},
					3
			);
		} else {
			graphics.fillPolygon(
					new int[]{triangleRect.x(), triangleRect.x(), triangleRect.right()},
					new int[]{triangleRect.y(), triangleRect.bottom(), triangleRect.y() + triangleRect.height() / 2},
					3
			);
		}
	}

	private static void drawLoadingSpinner(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		Stroke previous = graphics.getStroke();
		graphics.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		long tick = System.currentTimeMillis() / 12L;
		int startAngle = (int) (-tick % 360L);
		graphics.setColor(color);
		graphics.drawArc(rect.x(), rect.y(), rect.width() - 1, rect.height() - 1, startAngle, 280);
		graphics.setStroke(previous);
	}

	private static void drawMediaFitGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.FIT, color);
	}

	private static void drawMediaFillGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.FILL, color);
	}

	private static void drawMediaStretchGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.STRETCH, color);
	}

	private static void drawSearchGlyph(Graphics2D graphics, UiRect rect, Color color) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.SEARCH, color);
	}

	private static void drawQueueGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.QUEUE, color);
	}

	private static void drawTrashGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.TRASH, color);
	}

	private static void drawDownloadGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.DOWNLOAD, color);
	}

	private static void drawCheckGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.CHECK, color);
	}

	private static void drawWallpaperGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.WALLPAPER, color);
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

	private static void drawMediaCenterControls(Graphics2D graphics, UiLayout layout, MediaVisualSnapshot state) {
		if (graphics == null || layout == null || state == null || !state.playbackControlsVisible()) {
			return;
		}
		drawMediaTransportButton(graphics, mediaCenterBackRect(layout, state.mode()), TransportButtonKind.BACK, false, state.paused(), state.mode(), layout);
		if (state.centerPlayPauseVisible()) {
			drawMediaTransportButton(graphics, mediaCenterPlayPauseRect(layout, state.mode()), TransportButtonKind.PLAY_PAUSE, state.loading(), state.paused(), state.mode(), layout);
		}
		drawMediaTransportButton(graphics, mediaCenterForwardRect(layout, state.mode()), TransportButtonKind.FORWARD, false, state.paused(), state.mode(), layout);
	}

	private static void drawMediaTransportButton(Graphics2D graphics, UiRect rect, TransportButtonKind kind, boolean loading, boolean paused, ScreenViewMode mode, UiLayout layout) {
		Color fill = kind == TransportButtonKind.PLAY_PAUSE
				? new Color(248, 246, 246, 242)
				: new Color(50, 36, 42, 184);
		int arc = kind == TransportButtonKind.PLAY_PAUSE ? rect.height() : Math.min(rect.width(), rect.height());
		fillRoundedRect(graphics, rect, arc, fill);
		if (kind != TransportButtonKind.PLAY_PAUSE) {
			strokeRoundedRect(graphics, rect, arc, 1.0F, new Color(255, 255, 255, 22));
		}
		double iconScale = kind == TransportButtonKind.PLAY_PAUSE ? 0.54D : 0.44D;
		int iconSize = clampInt(
				(int) Math.round(Math.min(rect.width(), rect.height()) * iconScale),
				10,
				Math.max(10, Math.min(rect.width(), rect.height()) - Math.max(6, layout.unit()))
		);
		UiRect iconRect = new UiRect(
				rect.x() + (rect.width() - iconSize) / 2,
				rect.y() + (rect.height() - iconSize) / 2,
				iconSize,
				iconSize
		);
		Color iconColor = kind == TransportButtonKind.PLAY_PAUSE
				? new Color(20, 18, 20, 244)
				: new Color(248, 251, 255);
		if (loading && kind == TransportButtonKind.PLAY_PAUSE) {
			drawLoadingSpinner(graphics, iconRect, iconColor, 2.6F);
			return;
		}
		switch (kind) {
			case BACK -> drawSeekGlyph(graphics, iconRect, iconColor, true);
			case FORWARD -> drawSeekGlyph(graphics, iconRect, iconColor, false);
			case PLAY_PAUSE -> {
				if (paused) {
					drawPlayGlyph(graphics, iconRect, iconColor);
				} else {
					drawPauseGlyph(graphics, iconRect, iconColor);
				}
			}
		}
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
		drawPlayerUiIcon(graphics, rect, PlayerUiIcon.BACK, color);
	}

	private static UiLayout createUiLayout(int width, int height) {
		int canvasWidth = width * MAP_SIZE;
		int canvasHeight = height * MAP_SIZE;
		int viewportWidth = canvasWidth;
		int viewportHeight = canvasHeight;
		int viewportX = 0;
		int viewportY = 0;
		int minSpan = Math.max(1, Math.min(width, height));
		double scale = 1.0D + Math.max(0, minSpan - 1) * 0.5D;
		int margin = clampInt((int) Math.round(4.0D * scale), 4, 32);
		int unit = clampInt((int) Math.round(5.0D * scale), 5, 32);
		return new UiLayout(canvasWidth, canvasHeight, viewportX, viewportY, viewportWidth, viewportHeight, margin, unit);
	}

	private static int smallestScreenTileSpan(UiLayout layout) {
		if (layout == null) {
			return 1;
		}
		return Math.max(1, Math.min(layout.canvasWidth() / MAP_SIZE, layout.canvasHeight() / MAP_SIZE));
	}

	private static boolean compactScreenLayout(UiLayout layout) {
		return smallestScreenTileSpan(layout) <= 2;
	}

	private static boolean ultraCompactScreenLayout(UiLayout layout) {
		return smallestScreenTileSpan(layout) <= 1;
	}

	private static boolean youtubeMusicLandscapeLayout(UiLayout layout) {
		return false;
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
		int width = clampInt((int) Math.round(panel.width() * 0.62D), 52, Math.max(52, panel.width() - layout.unit() * 2));
		return new UiRect(
				panel.x() + (panel.width() - width) / 2,
				panel.y() + layout.unit() / 2,
				width,
				homeHeaderHeight(layout)
		);
	}

	private static UiRect homeFooterRect(UiLayout layout, UiRect panel) {
		int height = clampInt((int) Math.round(layout.unit() * 2.2D), 18, 40);
		int width = clampInt((int) Math.round(panel.width() * 0.46D), 52, Math.max(52, panel.width() - layout.unit() * 2));
		return new UiRect(
				panel.x() + (panel.width() - width) / 2,
				panel.bottom() - height - layout.unit() / 2,
				width,
				height
		);
	}

	private static UiRect homeContentRect(UiLayout layout, UiRect panel) {
		int contentTop = homeHeaderRect(layout, panel).bottom() + clampInt(layout.unit(), 6, 18);
		int contentBottom = homeFooterRect(layout, panel).y() - clampInt(layout.unit(), 6, 18);
		return new UiRect(
				panel.x() + clampInt(layout.unit() / 2, 4, 14),
				contentTop,
				panel.width() - clampInt(layout.unit(), 8, 28),
				Math.max(24, contentBottom - contentTop)
		);
	}

	private static UiRect homeGridRect(UiLayout layout, UiRect panel) {
		return scrollContentRect(homeContentRect(layout, panel), layout);
	}

	private static UiRect homeScrollbarTrackRect(UiLayout layout) {
		return scrollTrackRect(homeContentRect(layout, homePanelRect(layout)), layout);
	}

	private static UiRect homeScrollbarThumbRect(UiLayout layout, int scroll, int visibleRows, int totalRows) {
		UiRect track = homeScrollbarTrackRect(layout);
		if (track.height() <= 0 || totalRows <= 0) {
			return track;
		}
		int safeVisible = Math.max(1, Math.min(visibleRows, totalRows));
		int maxScroll = Math.max(0, totalRows - safeVisible);
		int thumbHeight = Math.max(track.width(), Math.round(track.height() * (safeVisible / (float) totalRows)));
		int travel = Math.max(0, track.height() - thumbHeight);
		int thumbY = maxScroll <= 0 ? track.y() : track.y() + Math.round(travel * (clampInt(scroll, 0, maxScroll) / (float) maxScroll));
		return new UiRect(track.x(), thumbY, track.width(), thumbHeight);
	}

	private static UiRect homeAppCardRect(UiLayout layout, int launcherPage, int slotIndex) {
		UiRect panel = homePanelRect(layout);
		int columns = homeColumns(layout);
		int row = slotIndex / columns;
		int column = slotIndex % columns;
		int gap = homeAppGap(layout);
		int cardWidth = homeAppCardWidth(layout);
		int cardHeight = homeAppCardHeight(layout);
		int rows = homeRowsPerPage(layout);
		int cardsWidth = columns * cardWidth + Math.max(0, columns - 1) * gap;
		int cardsHeight = rows * cardHeight + Math.max(0, rows - 1) * gap;
		UiRect content = homeGridRect(layout, panel);
		int startX = content.x() + Math.max(0, content.width() - cardsWidth) / 2;
		int startY = content.y() + Math.max(0, content.height() - cardsHeight) / 2;
		return new UiRect(
				startX + column * (cardWidth + gap),
				startY + row * (cardHeight + gap),
				cardWidth,
				cardHeight
		);
	}

	private static UiRect homeAppIconRect(UiRect cardRect, UiLayout layout) {
		int maxSize = Math.max(18, Math.min(cardRect.width() - layout.unit(), cardRect.height() - homeAppLabelHeight(layout) - layout.unit()));
		int size = clampInt(Math.round(Math.min(cardRect.width(), cardRect.height()) * 0.48F), 18, maxSize);
		return new UiRect(
				cardRect.x() + (cardRect.width() - size) / 2,
				cardRect.y() + layout.unit() / 2,
				size,
				size
		);
	}

	private static UiRect homeAppLabelRect(UiLayout layout, UiRect cardRect) {
		int labelHeight = homeAppLabelHeight(layout);
		return new UiRect(
				cardRect.x() + clampInt(layout.unit() / 2, 4, 8),
				cardRect.bottom() - labelHeight - clampInt(layout.unit() / 2, 4, 8),
				cardRect.width() - clampInt(layout.unit(), 8, 14),
				labelHeight
		);
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
		return new UiRect(
				0,
				0,
				Math.max(16, layout.canvasWidth()),
				Math.max(16, layout.canvasHeight())
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

	private static UiRect mediaActionRect(UiLayout layout, boolean hasMedia) {
		UiRect linkRect = mediaLinkRect(layout, hasMedia);
		int inset = clampInt(layout.unit() / 4, 2, 4);
		int width = clampInt(linkRect.height() * 2 + layout.unit() / 2, 24, 42);
		return new UiRect(
				linkRect.right() - width - inset,
				linkRect.y() + inset,
				width,
				linkRect.height() - inset * 2
		);
	}

	private static UiRect mediaGalleryBrowserCloseRect(UiLayout layout) {
		return mediaCloseRect(layout);
	}

	private static UiRect mediaGalleryBrowserLinkRect(UiLayout layout) {
		UiRect closeRect = mediaGalleryBrowserCloseRect(layout);
		UiRect canvas = mediaCanvasRect(layout);
		int height = clampInt(layout.unit() * 2, 18, 28);
		int x = closeRect.right() + clampInt(layout.unit() / 2, 5, 10);
		int width = Math.max(48, canvas.right() - x - layout.unit() / 2);
		return new UiRect(x, canvas.y() + layout.unit() / 2, width, height);
	}

	private static UiRect mediaGalleryPlayerTitleRect(UiLayout layout) {
		UiRect closeRect = mediaCloseRect(layout);
		UiRect canvas = mediaCanvasRect(layout);
		UiRect actionRect = mediaGalleryPlayerActionRect(layout);
		int height = clampInt(layout.unit() * 2, 18, 28);
		int x = closeRect.right() + clampInt(layout.unit() / 2, 5, 10);
		int width = Math.max(48, actionRect.x() - x - clampInt(layout.unit() / 2, 4, 8));
		return new UiRect(x, canvas.y() + layout.unit() / 2, width, height);
	}

	private static UiRect mediaGalleryPlayerActionRect(UiLayout layout) {
		return mediaQueueToggleRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaScaleRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int size = clampInt(layout.unit() * 2, 18, 28);
		return new UiRect(canvas.right() - size - layout.unit() / 2, canvas.bottom() - size - layout.unit() / 2, size, size);
	}

	private static UiRect mediaDownloadRect(UiLayout layout) {
		UiRect queueRect = mediaQueueToggleRect(layout, ScreenViewMode.HOME);
		int gap = clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(queueRect.x() - queueRect.width() - gap, queueRect.y(), queueRect.width(), queueRect.height());
	}

	private static UiRect mediaQueueToggleRect(UiLayout layout) {
		return mediaQueueToggleRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaQueueToggleRect(UiLayout layout, ScreenViewMode mode) {
		if (isYoutubeMusicMode(mode) && youtubeMusicLandscapeLayout(layout)) {
			UiRect artworkRect = mediaYoutubeMusicArtworkRect(layout);
			int size = clampInt(mediaYoutubeMusicActionsRowHeight(layout), 20, 38);
			int y = artworkRect.bottom() + clampInt(layout.unit(), 6, 14);
			return new UiRect(
					artworkRect.x() + (artworkRect.width() - size) / 2,
					y,
					size,
					size
			);
		}
		if (isYoutubeMusicMode(mode)) {
			UiRect actionsRow = mediaYoutubeMusicActionsRowRect(layout);
			int size = mediaYoutubeMusicActionButtonSize(layout);
			int gap = mediaYoutubeMusicActionButtonsGap(layout);
			int totalWidth = size * 2 + gap;
			int startX = actionsRow.x() + (actionsRow.width() - totalWidth) / 2;
			return new UiRect(
					startX + size + gap,
					actionsRow.y() + (actionsRow.height() - size) / 2,
					size,
					size
			);
		}
		UiRect scaleRect = mediaScaleRect(layout);
		int gap = clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(scaleRect.x() - scaleRect.width() - gap, scaleRect.y(), scaleRect.width(), scaleRect.height());
	}

	private static int mediaYoutubeMusicActionButtonSize(UiLayout layout) {
		return clampInt(mediaYoutubeMusicActionsRowHeight(layout), 20, 38);
	}

	private static int mediaYoutubeMusicActionButtonsGap(UiLayout layout) {
		return clampInt(layout.unit() / 2, 4, 8);
	}

	private static UiRect mediaYoutubeMusicSearchRect(UiLayout layout) {
		UiRect actionsRow = mediaYoutubeMusicActionsRowRect(layout);
		int size = mediaYoutubeMusicActionButtonSize(layout);
		int gap = mediaYoutubeMusicActionButtonsGap(layout);
		int totalWidth = size * 2 + gap;
		int startX = actionsRow.x() + (actionsRow.width() - totalWidth) / 2;
		return new UiRect(
				startX,
				actionsRow.y() + (actionsRow.height() - size) / 2,
				size,
				size
		);
	}

	private static UiRect mediaTimelineRect(UiLayout layout) {
		return mediaTimelineRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaTimelineRect(UiLayout layout, ScreenViewMode mode) {
		if (isYoutubeMusicMode(mode)) {
			UiRect infoRect = mediaYoutubeMusicInfoRect(layout);
			int gap = clampInt(layout.unit(), 8, 18);
			int height = mediaYoutubeMusicTrackHeight(layout);
			return new UiRect(
					infoRect.x(),
					infoRect.bottom() + gap,
					infoRect.width(),
					height
			);
		}
		UiRect canvas = mediaCanvasRect(layout);
		UiRect scaleRect = mediaScaleRect(layout);
		UiRect downloadRect = mediaDownloadRect(layout);
		UiRect queueToggleRect = mediaQueueToggleRect(layout, mode);
		int left = canvas.x() + layout.unit() / 2;
		int right = Math.min(Math.min(scaleRect.x(), queueToggleRect.x()), downloadRect.x()) - clampInt(layout.unit() / 2, 4, 8);
		int height = clampInt(layout.unit() * 2, 18, 28);
		return new UiRect(
				left,
				canvas.bottom() - height - layout.unit() / 2,
				Math.max(44, right - left),
				height
		);
	}

	private static UiRect mediaGalleryGridRect(UiLayout layout) {
		UiRect linkRect = mediaGalleryBrowserLinkRect(layout);
		UiRect canvas = mediaCanvasRect(layout);
		int inset = clampInt(layout.unit() / 2, 4, 8);
		UiRect viewport = new UiRect(
				canvas.x() + inset,
				linkRect.bottom() + inset,
				canvas.width() - inset * 2,
				Math.max(18, canvas.bottom() - linkRect.bottom() - inset * 2)
		);
		return scrollContentRect(viewport, layout);
	}

	private static int mediaGalleryFooterRectHeight(UiLayout layout) {
		return ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 16, 22)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 1, 18, 26)
				: clampInt(layout.unit() * 2 + 2, 22, 30);
	}

	private static int mediaGalleryVisibleRows(UiLayout layout) {
		UiRect grid = mediaGalleryGridRect(layout);
		int stride = mediaGalleryCardHeight(layout) + mediaGalleryCardGap(layout);
		return Math.max(1, (grid.height() + mediaGalleryCardGap(layout)) / Math.max(1, stride));
	}

	private static int mediaGalleryColumns(UiLayout layout) {
		UiRect grid = mediaGalleryGridRect(layout);
		int gap = mediaGalleryCardGap(layout);
		int desiredCardWidth = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 9, 48, 64)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 9, 66, 92)
				: clampInt(layout.unit() * 10, 86, 132);
		int columns = Math.max(1, (grid.width() + gap) / Math.max(1, desiredCardWidth + gap));
		return Math.max(2, columns);
	}

	private static int mediaGalleryCardGap(UiLayout layout) {
		return clampInt(layout.unit() / 2, 4, 8);
	}

	private static int mediaGalleryTotalRows(int itemCount, UiLayout layout) {
		int columns = Math.max(1, mediaGalleryColumns(layout));
		return Math.max(0, (itemCount + columns - 1) / columns);
	}

	private static int mediaGalleryCardHeight(UiLayout layout) {
		UiRect grid = mediaGalleryGridRect(layout);
		int gap = mediaGalleryCardGap(layout);
		int columns = mediaGalleryColumns(layout);
		int cardWidth = Math.max(18, (grid.width() - gap * Math.max(0, columns - 1)) / Math.max(1, columns));
		return clampInt((int) Math.round(cardWidth * 0.74D), clampInt(layout.unit() * 5, 28, 42), clampInt(layout.unit() * 12, 64, 150));
	}

	private static UiRect mediaGalleryCardRect(UiLayout layout, int visibleRow, int column) {
		UiRect grid = mediaGalleryGridRect(layout);
		int gap = mediaGalleryCardGap(layout);
		int columns = mediaGalleryColumns(layout);
		int width = Math.max(18, (grid.width() - gap * Math.max(0, columns - 1)) / columns);
		int height = mediaGalleryCardHeight(layout);
		return new UiRect(
				grid.x() + column * (width + gap),
				grid.y() + visibleRow * (height + gap),
				width,
				height
		);
	}

	private static UiRect mediaGalleryCardPreviewRect(UiRect cardRect, UiLayout layout) {
		int inset = clampInt(layout.unit() / 3, 3, 6);
		return new UiRect(
				cardRect.x() + inset,
				cardRect.y() + inset,
				cardRect.width() - inset * 2,
				cardRect.height() - inset * 2
		);
	}

	private static UiRect mediaGalleryCardTitleRect(UiRect cardRect, UiLayout layout) {
		UiRect preview = mediaGalleryCardPreviewRect(cardRect, layout);
		int inset = clampInt(layout.unit() / 3, 3, 6);
		return new UiRect(
				cardRect.x() + inset,
				preview.bottom() + inset / 2,
				cardRect.width() - inset * 2,
				Math.max(12, cardRect.bottom() - preview.bottom() - inset * 2)
		);
	}

	private static UiRect mediaGalleryCardPlayBadgeRect(UiRect previewRect, UiLayout layout) {
		int size = clampInt(layout.unit() * 2, 18, 30);
		return new UiRect(previewRect.right() - size - clampInt(layout.unit() / 3, 3, 6), previewRect.y() + clampInt(layout.unit() / 3, 3, 6), size, size);
	}

	private static UiRect mediaGalleryBrowserScrollbarTrackRect(UiLayout layout) {
		UiRect linkRect = mediaGalleryBrowserLinkRect(layout);
		UiRect canvas = mediaCanvasRect(layout);
		int inset = clampInt(layout.unit() / 2, 4, 8);
		UiRect viewport = new UiRect(
				canvas.x() + inset,
				linkRect.bottom() + inset,
				canvas.width() - inset * 2,
				Math.max(18, canvas.bottom() - linkRect.bottom() - inset * 2)
		);
		return scrollTrackRect(viewport, layout);
	}

	private static UiRect mediaGalleryBrowserScrollbarThumbRect(UiLayout layout, int scroll, int visibleRows, int totalRows) {
		UiRect track = mediaGalleryBrowserScrollbarTrackRect(layout);
		if (track.height() <= 0 || totalRows <= 0) {
			return track;
		}
		int safeVisible = Math.max(1, Math.min(visibleRows, totalRows));
		int maxScroll = Math.max(0, totalRows - safeVisible);
		int thumbHeight = Math.max(track.width(), Math.round(track.height() * (safeVisible / (float) totalRows)));
		int travel = Math.max(0, track.height() - thumbHeight);
		int thumbY = maxScroll <= 0 ? track.y() : track.y() + Math.round(travel * (clampInt(scroll, 0, maxScroll) / (float) maxScroll));
		return new UiRect(track.x(), thumbY, track.width(), thumbHeight);
	}

	private static UiRect mediaYoutubeMusicArtworkRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		UiRect closeRect = mediaCloseRect(layout);
		int topInset = closeRect.bottom() + clampInt(layout.unit(), 6, 16);
		int minSize = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 7, 38, 54)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 8, 54, 86)
				: clampInt(layout.unit() * 10, 88, 176);
		if (youtubeMusicLandscapeLayout(layout)) {
			int sideInset = clampInt(layout.unit() * 2, 12, 28);
			int bottomInset = clampInt(layout.unit() * 2, 12, 26);
			int availableHeight = Math.max(
					minSize,
					canvas.bottom() - topInset - mediaYoutubeMusicActionsRowHeight(layout) - bottomInset - clampInt(layout.unit(), 6, 16)
			);
			int maxWidth = Math.max(minSize, canvas.width() / 3);
			int preferredSize = (int) Math.round(Math.min(canvas.width() * 0.28D, availableHeight));
			int size = clampInt(preferredSize, minSize, Math.max(minSize, Math.min(maxWidth, availableHeight)));
			return new UiRect(
					canvas.right() - sideInset - size,
					topInset + Math.max(0, (availableHeight - size) / 2),
					size,
					size
			);
		}
		int sideInset = clampInt(layout.unit() * 2, 12, 28);
		int reservedBottom = mediaYoutubeMusicInfoHeight(layout)
				+ mediaYoutubeMusicTrackHeight(layout)
				+ mediaYoutubeMusicTimeRowHeight(layout)
				+ mediaYoutubeMusicControlsRowHeight(layout)
				+ mediaYoutubeMusicActionsRowHeight(layout)
				+ clampInt(layout.unit() * 5, 28, 74);
		int maxWidth = Math.max(minSize, canvas.width() - sideInset * 2);
		int availableHeight = Math.max(minSize, canvas.height() - topInset - reservedBottom);
		int preferredSize = (int) Math.round(Math.min(maxWidth, canvas.height() * 0.36D));
		int size = clampInt(preferredSize, minSize, Math.max(minSize, Math.min(maxWidth, availableHeight)));
		return new UiRect(
				canvas.x() + (canvas.width() - size) / 2,
				topInset + Math.max(0, (availableHeight - size) / 3),
				size,
				size
		);
	}

	private static UiRect mediaQueuePanelRect(UiLayout layout) {
		return centeredOverlayPanelRect(
				layout,
				ultraCompactScreenLayout(layout) ? 13.0D / 16.0D : compactScreenLayout(layout) ? 4.0D / 5.0D : 5.0D / 6.0D,
				ultraCompactScreenLayout(layout) ? 5.0D / 8.0D : compactScreenLayout(layout) ? 11.0D / 16.0D : 4.0D / 5.0D,
				86,
				62
		);
	}

	private static UiRect galleryDeleteConfirmPanelRect(UiLayout layout) {
		return centeredOverlayPanelRect(
				layout,
				ultraCompactScreenLayout(layout) ? 3.0D / 4.0D : compactScreenLayout(layout) ? 11.0D / 16.0D : 5.0D / 8.0D,
				ultraCompactScreenLayout(layout) ? 7.0D / 18.0D : compactScreenLayout(layout) ? 2.0D / 5.0D : 11.0D / 24.0D,
				84,
				54
		);
	}

	private static UiRect centeredOverlayPanelRect(UiLayout layout, double widthFraction, double heightFraction, int minWidth, int minHeight) {
		UiRect canvas = mediaCanvasRect(layout);
		int width = clampInt((int) Math.round(canvas.width() * widthFraction), minWidth, canvas.width() - layout.unit() * 2);
		int height = clampInt((int) Math.round(canvas.height() * heightFraction), minHeight, canvas.height() - layout.unit() * 2);
		return new UiRect(
				canvas.x() + (canvas.width() - width) / 2,
				canvas.y() + (canvas.height() - height) / 2,
				width,
				height
		);
	}

	private static UiRect overlayWindowRect(UiLayout layout, MediaOverlayWindowType type) {
		return switch (type) {
			case YOUTUBE_QUEUE -> mediaQueuePanelRect(layout);
			case GALLERY_DELETE_CONFIRM -> galleryDeleteConfirmPanelRect(layout);
		};
	}

	private static UiRect mediaQueueHeaderRect(UiLayout layout) {
		UiRect panel = overlayWindowRect(layout, MediaOverlayWindowType.YOUTUBE_QUEUE);
		int inset = clampInt(layout.unit() / 2, 4, 8);
		int height = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 18, 24)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 2, 22, 30)
				: clampInt(layout.unit() * 3, 26, 40);
		return new UiRect(panel.x() + inset, panel.y() + inset, panel.width() - inset * 2, height);
	}

	private static UiRect mediaQueueCloseRect(UiLayout layout) {
		UiRect header = mediaQueueHeaderRect(layout);
		int size = Math.max(16, header.height() - clampInt(layout.unit() / 2, 4, 8));
		return new UiRect(header.right() - size - clampInt(layout.unit() / 3, 3, 6), header.y() + (header.height() - size) / 2, size, size);
	}

	private static UiRect mediaQueueSubtitleRect(UiLayout layout) {
		UiRect header = mediaQueueHeaderRect(layout);
		UiRect closeRect = mediaQueueCloseRect(layout);
		int x = header.x() + clampInt(layout.unit(), 8, 14);
		int width = Math.max(16, closeRect.x() - x - clampInt(layout.unit(), 8, 14));
		int y = header.y() + header.height() / 2 - 1;
		return new UiRect(x, y, width, Math.max(12, header.bottom() - y - 2));
	}

	private static UiRect mediaQueueListRect(UiLayout layout) {
		UiRect panel = overlayWindowRect(layout, MediaOverlayWindowType.YOUTUBE_QUEUE);
		UiRect header = mediaQueueHeaderRect(layout);
		UiRect footer = mediaQueueFooterRect(layout);
		int top = header.bottom() + clampInt(layout.unit() / 2, 4, 8);
		int bottom = footer.y() - clampInt(layout.unit() / 2, 4, 8);
		UiRect viewport = new UiRect(panel.x() + clampInt(layout.unit() / 2, 4, 8), top, panel.width() - clampInt(layout.unit(), 8, 16), Math.max(20, bottom - top));
		return scrollContentRect(viewport, layout);
	}

	private static UiRect mediaQueueFooterRect(UiLayout layout) {
		UiRect panel = overlayWindowRect(layout, MediaOverlayWindowType.YOUTUBE_QUEUE);
		int inset = clampInt(layout.unit() / 2, 4, 8);
		int height = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 18, 22)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 1, 20, 28)
				: clampInt(layout.unit() * 3, 24, 38);
		return new UiRect(panel.x() + inset, panel.bottom() - height - inset, panel.width() - inset * 2, height);
	}

	private static UiRect mediaQueueScrollUpRect(UiLayout layout) {
		UiRect footer = mediaQueueFooterRect(layout);
		int width = clampInt(footer.width() / 4, 28, 52);
		return new UiRect(footer.x() + clampInt(layout.unit() / 3, 3, 6), footer.y() + clampInt(layout.unit() / 4, 2, 4), width, footer.height() - clampInt(layout.unit() / 2, 4, 8));
	}

	private static UiRect mediaQueueScrollDownRect(UiLayout layout) {
		UiRect footer = mediaQueueFooterRect(layout);
		int width = clampInt(footer.width() / 4, 28, 52);
		return new UiRect(footer.right() - width - clampInt(layout.unit() / 3, 3, 6), footer.y() + clampInt(layout.unit() / 4, 2, 4), width, footer.height() - clampInt(layout.unit() / 2, 4, 8));
	}

	private static UiRect mediaQueueFooterInfoRect(UiLayout layout) {
		UiRect footer = mediaQueueFooterRect(layout);
		return new UiRect(
				footer.x() + clampInt(layout.unit() / 2, 4, 8),
				footer.y(),
				footer.width() - clampInt(layout.unit(), 8, 16),
				footer.height()
		);
	}

	private static UiRect galleryDeleteConfirmHeaderRect(UiLayout layout) {
		UiRect panel = overlayWindowRect(layout, MediaOverlayWindowType.GALLERY_DELETE_CONFIRM);
		int inset = clampInt(layout.unit() / 2, 4, 8);
		int height = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 18, 24)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 2, 22, 30)
				: clampInt(layout.unit() * 3, 26, 38);
		return new UiRect(panel.x() + inset, panel.y() + inset, panel.width() - inset * 2, height);
	}

	private static UiRect galleryDeleteConfirmCloseRect(UiLayout layout) {
		UiRect header = galleryDeleteConfirmHeaderRect(layout);
		int size = Math.max(16, header.height() - clampInt(layout.unit() / 2, 4, 8));
		return new UiRect(header.right() - size - clampInt(layout.unit() / 3, 3, 6), header.y() + (header.height() - size) / 2, size, size);
	}

	private static UiRect galleryDeleteConfirmBodyRect(UiLayout layout) {
		UiRect panel = overlayWindowRect(layout, MediaOverlayWindowType.GALLERY_DELETE_CONFIRM);
		UiRect header = galleryDeleteConfirmHeaderRect(layout);
		UiRect buttons = galleryDeleteConfirmButtonsRowRect(layout);
		int sideInset = clampInt(layout.unit(), 8, 14);
		int top = header.bottom() + clampInt(layout.unit() / 2, 4, 8);
		int bottom = buttons.y() - clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(panel.x() + sideInset, top, panel.width() - sideInset * 2, Math.max(18, bottom - top));
	}

	private static UiRect galleryDeleteConfirmButtonsRowRect(UiLayout layout) {
		UiRect panel = overlayWindowRect(layout, MediaOverlayWindowType.GALLERY_DELETE_CONFIRM);
		int inset = clampInt(layout.unit(), 8, 14);
		int height = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 1, 18, 24)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2 + 2, 22, 28)
				: clampInt(layout.unit() * 2 + 4, 26, 34);
		return new UiRect(panel.x() + inset, panel.bottom() - height - inset, panel.width() - inset * 2, height);
	}

	private static UiRect galleryDeleteConfirmCancelRect(UiLayout layout) {
		UiRect row = galleryDeleteConfirmButtonsRowRect(layout);
		int gap = clampInt(layout.unit() / 2, 4, 8);
		int width = Math.max(28, (row.width() - gap) / 2);
		return new UiRect(row.x(), row.y(), width, row.height());
	}

	private static UiRect galleryDeleteConfirmConfirmRect(UiLayout layout) {
		UiRect row = galleryDeleteConfirmButtonsRowRect(layout);
		UiRect cancel = galleryDeleteConfirmCancelRect(layout);
		int gap = clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(cancel.right() + gap, row.y(), Math.max(28, row.right() - cancel.right() - gap), row.height());
	}

	private static UiRect mediaQueueScrollbarTrackRect(UiLayout layout) {
		UiRect panel = overlayWindowRect(layout, MediaOverlayWindowType.YOUTUBE_QUEUE);
		UiRect header = mediaQueueHeaderRect(layout);
		UiRect footer = mediaQueueFooterRect(layout);
		int top = header.bottom() + clampInt(layout.unit() / 2, 4, 8);
		int bottom = footer.y() - clampInt(layout.unit() / 2, 4, 8);
		UiRect viewport = new UiRect(panel.x() + clampInt(layout.unit() / 2, 4, 8), top, panel.width() - clampInt(layout.unit(), 8, 16), Math.max(20, bottom - top));
		return scrollTrackRect(viewport, layout);
	}

	private static UiRect mediaQueueScrollbarThumbRect(UiLayout layout, int scroll, int visibleRows, int totalRows) {
		UiRect track = mediaQueueScrollbarTrackRect(layout);
		if (track.height() <= 0 || totalRows <= 0) {
			return track;
		}
		int safeVisible = Math.max(1, Math.min(visibleRows, totalRows));
		int maxScroll = Math.max(0, totalRows - safeVisible);
		int thumbHeight = Math.max(track.width(), Math.round(track.height() * (safeVisible / (float) totalRows)));
		int travel = Math.max(0, track.height() - thumbHeight);
		int thumbY = maxScroll <= 0
				? track.y()
				: track.y() + Math.round(travel * (clampInt(scroll, 0, maxScroll) / (float) maxScroll));
		return new UiRect(track.x(), thumbY, track.width(), thumbHeight);
	}

	private static int mediaQueueVisibleRows(UiLayout layout) {
		UiRect list = mediaQueueListRect(layout);
		int rowHeight = mediaQueueRowHeight(layout);
		return Math.max(1, list.height() / Math.max(1, rowHeight));
	}

	private static int mediaQueueRowHeight(UiLayout layout) {
		if (ultraCompactScreenLayout(layout)) {
			return clampInt(layout.unit() * 4, 24, 32);
		}
		if (compactScreenLayout(layout)) {
			return clampInt(layout.unit() * 4, 30, 42);
		}
		return clampInt(layout.unit() * 5, 44, 72);
	}

	private static UiRect mediaQueueRowRect(UiLayout layout, int visibleIndex) {
		UiRect list = mediaQueueListRect(layout);
		int rowHeight = mediaQueueRowHeight(layout);
		int gap = clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(list.x(), list.y() + visibleIndex * rowHeight, list.width(), Math.max(24, rowHeight - gap));
	}

	private static UiRect mediaQueueRemoveRect(UiRect rowRect, UiLayout layout) {
		int size = clampInt(rowRect.height() - clampInt(layout.unit(), 10, 16), 18, 30);
		int rightInset = clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(rowRect.right() - size - rightInset, rowRect.y() + (rowRect.height() - size) / 2, size, size);
	}

	private static UiRect mediaQueueIndexRect(UiRect rowRect, UiLayout layout) {
		int width = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 3, 18, 24)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 3 + 2, 22, 32)
				: clampInt(layout.unit() * 4, 26, 42);
		int height = ultraCompactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 14, 18)
				: compactScreenLayout(layout)
				? clampInt(layout.unit() * 2, 16, 22)
				: clampInt(layout.unit() * 2, 18, 26);
		return new UiRect(rowRect.x() + clampInt(layout.unit() / 2, 4, 8), rowRect.y() + (rowRect.height() - height) / 2, width, height);
	}

	private static UiRect mediaQueueTitleRect(UiRect rowRect, UiRect removeRect, UiRect badgeRect, UiLayout layout) {
		int x = badgeRect.right() + clampInt(layout.unit() / 2, 4, 8);
		int top = rowRect.y() + clampInt(layout.unit() / 2, 4, 8);
		int bottom = rowRect.bottom() - clampInt(layout.unit() / 2, 4, 8);
		int width = Math.max(18, removeRect.x() - x - clampInt(layout.unit(), 8, 12));
		return new UiRect(x, top, width, Math.max(18, bottom - top));
	}

	private static UiRect mediaPlayPauseRect(UiLayout layout) {
		return mediaPlayPauseRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaPlayPauseRect(UiLayout layout, ScreenViewMode mode) {
		UiRect timeline = mediaTimelineRect(layout, mode);
		if (isYoutubeMusicMode(mode)) {
			return new UiRect(timeline.x(), timeline.y(), 0, 0);
		}
		int size = Math.max(12, timeline.height() - 4);
		return new UiRect(timeline.x() + 2, timeline.y() + (timeline.height() - size) / 2, size, size);
	}

	private static UiRect mediaTimelineCounterRect(UiLayout layout) {
		return mediaTimelineCounterRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaTimelineCounterRect(UiLayout layout, ScreenViewMode mode) {
		if (isYoutubeMusicMode(mode)) {
			UiRect timeline = mediaTimelineRect(layout, mode);
			return new UiRect(timeline.right(), timeline.y(), 0, timeline.height());
		}
		UiRect timeline = mediaTimelineRect(layout, mode);
		int width = timelineCounterReservedWidth(layout);
		int inset = clampInt(layout.unit() / 2, 4, 8);
		return new UiRect(timeline.right() - width - inset, timeline.y(), width, timeline.height());
	}

	private static UiRect mediaTimelineTrackRect(UiLayout layout) {
		return mediaTimelineTrackRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaTimelineTrackRect(UiLayout layout, ScreenViewMode mode) {
		UiRect timeline = mediaTimelineRect(layout, mode);
		UiRect counter = mediaTimelineCounterRect(layout, mode);
		int sideInset = clampInt(layout.unit() / 2, 4, 8);
		int x;
		int right;
		if (isYoutubeMusicMode(mode)) {
			x = timeline.x() + sideInset;
			right = counter.width() > 0
					? counter.x() - sideInset
					: timeline.right() - sideInset;
		} else {
			UiRect playPause = mediaPlayPauseRect(layout, mode);
			x = playPause.right() + sideInset;
			right = counter.width() > 0
					? counter.x() - sideInset
					: timeline.right() - sideInset;
		}
		int height = clampInt(layout.unit() / 2, 6, 10);
		return new UiRect(
				x,
				timeline.y() + timeline.height() / 2 - height / 2,
				Math.max(12, right - x),
				height
		);
	}

	private static int timelineCounterReservedWidth(UiLayout layout) {
		return switch (timelineCounterDetailLevel(layout)) {
			case NONE -> 0;
			case COMPACT -> clampInt((int) Math.round(layout.unit() * 4.2D), 24, 48);
			case FULL -> clampInt((int) Math.round(layout.unit() * 6.8D), 46, 88);
		};
	}

	private static UiRect mediaTimelineHitRect(UiLayout layout) {
		return mediaTimelineHitRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaTimelineHitRect(UiLayout layout, ScreenViewMode mode) {
		if (isYoutubeMusicMode(mode)) {
			UiRect timeline = mediaTimelineRect(layout, mode);
			int extra = clampInt(layout.unit() / 2, 4, 8);
			return new UiRect(timeline.x(), timeline.y() - extra / 2, timeline.width(), timeline.height() + extra);
		}
		return mediaTimelineTrackRect(layout, mode);
	}

	private static UiRect mediaCenterPlayPauseRect(UiLayout layout) {
		return mediaCenterPlayPauseRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaCenterPlayPauseRect(UiLayout layout, ScreenViewMode mode) {
		if (isYoutubeMusicMode(mode)) {
			UiRect controls = mediaYoutubeMusicControlsRowRect(layout);
			int height = controls.height();
			int width = clampInt(
					(int) Math.round(controls.width() * (youtubeMusicLandscapeLayout(layout) ? 0.46D : 0.54D)),
					ultraCompactScreenLayout(layout) ? 48 : 72,
					Math.max(56, controls.width() - clampInt(layout.unit() * 4, 24, 80))
			);
			return new UiRect(
					controls.x() + (controls.width() - width) / 2,
					controls.y(),
					width,
					height
			);
		}
		UiRect canvas = mediaCanvasRect(layout);
		int height;
		if (ultraCompactScreenLayout(layout)) {
			height = clampInt(layout.unit() * 5, 24, 34);
		} else if (compactScreenLayout(layout)) {
			height = clampInt(layout.unit() * 5, 30, 52);
		} else {
			height = clampInt(layout.unit() * 6, 46, 92);
		}
		int width = clampInt(
				(int) Math.round(height * 2.05D),
				Math.max(44, height + 16),
				Math.max(52, canvas.width() - clampInt(layout.unit() * 10, 40, 120))
		);
		return new UiRect(
				canvas.x() + (canvas.width() - width) / 2,
				canvas.y() + (canvas.height() - height) / 2,
				width,
				height
		);
	}

	private static UiRect mediaCenterBackRect(UiLayout layout) {
		return mediaCenterBackRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaCenterBackRect(UiLayout layout, ScreenViewMode mode) {
		UiRect center = mediaCenterPlayPauseRect(layout, mode);
		int size;
		int gap;
		if (isYoutubeMusicMode(mode)) {
			size = clampInt(center.height(), ultraCompactScreenLayout(layout) ? 22 : 28, 54);
			gap = clampInt(layout.unit(), 8, 18);
		} else if (ultraCompactScreenLayout(layout)) {
			size = clampInt(center.height(), 18, 24);
			gap = clampInt(layout.unit(), 8, 12);
		} else if (compactScreenLayout(layout)) {
			size = clampInt(center.height(), 24, 34);
			gap = clampInt(layout.unit() + 2, 10, 18);
		} else {
			size = clampInt(center.height(), 34, 68);
			gap = clampInt(layout.unit() * 2, 14, 28);
		}
		return new UiRect(center.x() - size - gap, center.y() + (center.height() - size) / 2, size, size);
	}

	private static UiRect mediaCenterForwardRect(UiLayout layout) {
		return mediaCenterForwardRect(layout, ScreenViewMode.HOME);
	}

	private static UiRect mediaCenterForwardRect(UiLayout layout, ScreenViewMode mode) {
		UiRect center = mediaCenterPlayPauseRect(layout, mode);
		int size;
		int gap;
		if (isYoutubeMusicMode(mode)) {
			size = clampInt(center.height(), ultraCompactScreenLayout(layout) ? 22 : 28, 54);
			gap = clampInt(layout.unit(), 8, 18);
		} else if (ultraCompactScreenLayout(layout)) {
			size = clampInt(center.height(), 18, 24);
			gap = clampInt(layout.unit(), 8, 12);
		} else if (compactScreenLayout(layout)) {
			size = clampInt(center.height(), 24, 34);
			gap = clampInt(layout.unit() + 2, 10, 18);
		} else {
			size = clampInt(center.height(), 34, 68);
			gap = clampInt(layout.unit() * 2, 14, 28);
		}
		return new UiRect(center.right() + gap, center.y() + (center.height() - size) / 2, size, size);
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
		return clampInt((int) Math.round(layout.unit() * 2.0D), 12, 44);
	}

	private static int homeRowsPerPage(UiLayout layout) {
		UiRect content = homeGridRect(layout, homePanelRect(layout));
		int gap = homeAppGap(layout);
		int desiredHeight = homeDesiredCardHeight(layout);
		return Math.max(1, (content.height() + gap) / Math.max(1, desiredHeight + gap));
	}

	private static int homeAppGap(UiLayout layout) {
		return clampInt((int) Math.round(layout.unit() * 0.8D), 4, 18);
	}

	private static int homeColumns(UiLayout layout) {
		UiRect content = homeGridRect(layout, homePanelRect(layout));
		int gap = homeAppGap(layout);
		int desiredWidth = homeDesiredCardWidth(layout);
		return Math.max(2, (content.width() + gap) / Math.max(1, desiredWidth + gap));
	}

	private static int homeAppLabelHeight(UiLayout layout) {
		return clampInt((int) Math.round(layout.unit() * 1.9D), 12, 34);
	}

	private static int homeDesiredCardWidth(UiLayout layout) {
		return clampInt((int) Math.round(layout.unit() * 8.5D), 42, 220);
	}

	private static int homeDesiredCardHeight(UiLayout layout) {
		return homeDesiredCardWidth(layout) + homeAppLabelHeight(layout) + clampInt(layout.unit(), 4, 20);
	}

	private static int homeAppCardWidth(UiLayout layout) {
		UiRect content = homeGridRect(layout, homePanelRect(layout));
		int columns = homeColumns(layout);
		int gap = homeAppGap(layout);
		return Math.max(32, (content.width() - Math.max(0, columns - 1) * gap) / Math.max(1, columns));
	}

	private static int homeAppCardHeight(UiLayout layout) {
		UiRect content = homeGridRect(layout, homePanelRect(layout));
		int rows = homeRowsPerPage(layout);
		int gap = homeAppGap(layout);
		int maxHeight = Math.max(32, (content.height() - Math.max(0, rows - 1) * gap) / Math.max(1, rows));
		int desiredHeight = homeAppCardWidth(layout) + homeAppLabelHeight(layout) + clampInt(layout.unit(), 4, 20);
		return Math.max(32, Math.min(maxHeight, desiredHeight));
	}

	private static int homePageCapacity(UiLayout layout) {
		return Math.max(1, homeRowsPerPage(layout) * homeColumns(layout));
	}

	private static int homeTotalRows(UiLayout layout) {
		int columns = Math.max(1, homeColumns(layout));
		return Math.max(0, (MonitorAppRegistry.apps().size() + columns - 1) / columns);
	}

	private static int homeMaxScroll(UiLayout layout) {
		return Math.max(0, homeTotalRows(layout) - homeRowsPerPage(layout));
	}

	private static int homePageCount(UiLayout layout) {
		return Math.max(1, homeMaxScroll(layout) + 1);
	}

	private static List<MonitorApp> visibleHomeApps(UiLayout layout, int launcherPage) {
		List<MonitorApp> apps = MonitorAppRegistry.apps();
		int scroll = clampInt(launcherPage, 0, homeMaxScroll(layout));
		int fromIndex = scroll * homeColumns(layout);
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

	private static Shape roundedRectShape(UiRect rect, int topLeft, int topRight, int bottomRight, int bottomLeft) {
		float x = rect.x();
		float y = rect.y();
		float width = rect.width();
		float height = rect.height();
		float right = x + width;
		float bottom = y + height;
		float tl = clampFloat(topLeft, 0.0F, Math.min(width, height) / 2.0F);
		float tr = clampFloat(topRight, 0.0F, Math.min(width, height) / 2.0F);
		float br = clampFloat(bottomRight, 0.0F, Math.min(width, height) / 2.0F);
		float bl = clampFloat(bottomLeft, 0.0F, Math.min(width, height) / 2.0F);
		Path2D.Float path = new Path2D.Float();
		path.moveTo(x + tl, y);
		path.lineTo(right - tr, y);
		if (tr > 0.0F) {
			path.quadTo(right, y, right, y + tr);
		} else {
			path.lineTo(right, y);
		}
		path.lineTo(right, bottom - br);
		if (br > 0.0F) {
			path.quadTo(right, bottom, right - br, bottom);
		} else {
			path.lineTo(right, bottom);
		}
		path.lineTo(x + bl, bottom);
		if (bl > 0.0F) {
			path.quadTo(x, bottom, x, bottom - bl);
		} else {
			path.lineTo(x, bottom);
		}
		path.lineTo(x, y + tl);
		if (tl > 0.0F) {
			path.quadTo(x, y, x + tl, y);
		} else {
			path.lineTo(x, y);
		}
		path.closePath();
		return path;
	}

	private static void fillShape(Graphics2D graphics, Shape shape, Color color) {
		if (graphics == null || shape == null || color == null) {
			return;
		}
		Color previous = graphics.getColor();
		graphics.setColor(color);
		graphics.fill(shape);
		graphics.setColor(previous);
	}

	private static void strokeShape(Graphics2D graphics, Shape shape, float width, Color color) {
		if (graphics == null || shape == null || color == null) {
			return;
		}
		Stroke previousStroke = graphics.getStroke();
		Color previousColor = graphics.getColor();
		graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(color);
		graphics.draw(shape);
		graphics.setStroke(previousStroke);
		graphics.setColor(previousColor);
	}

	private static void drawCenteredText(Graphics2D graphics, String text, UiRect rect, Color color, int style, int size) {
		graphics.setColor(color);
		graphics.setFont(new Font(Font.SANS_SERIF, style, size));
		var metrics = graphics.getFontMetrics();
		int textX = rect.x() + (rect.width() - metrics.stringWidth(text)) / 2;
		int textY = rect.y() + (rect.height() - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(text, textX, textY);
	}

	private static void drawCenteredTextFitted(Graphics2D graphics, String text, UiRect rect, Color color, int style, int maxSize, int minSize) {
		if (graphics == null || rect == null || rect.width() <= 0 || rect.height() <= 0 || text == null || text.isBlank()) {
			return;
		}
		int resolvedMinSize = Math.max(1, Math.min(minSize, maxSize));
		int size = maxSize;
		java.awt.Font font = new Font(Font.SANS_SERIF, style, size);
		graphics.setColor(color);
		graphics.setFont(font);
		var metrics = graphics.getFontMetrics();
		while (size > resolvedMinSize && metrics.stringWidth(text) > rect.width() - 2) {
			size--;
			font = new Font(Font.SANS_SERIF, style, size);
			graphics.setFont(font);
			metrics = graphics.getFontMetrics();
		}
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

	private static void drawRightAlignedText(Graphics2D graphics, String text, UiRect rect, Color color, int style, int size) {
		if (graphics == null || rect == null || text == null || text.isBlank()) {
			return;
		}
		graphics.setColor(color);
		graphics.setFont(new Font(Font.SANS_SERIF, style, size));
		var metrics = graphics.getFontMetrics();
		int textY = rect.y() + (rect.height() - metrics.getHeight()) / 2 + metrics.getAscent();
		int textX = rect.right() - metrics.stringWidth(text);
		graphics.drawString(text, textX, textY);
	}

	private static void drawWrappedText(Graphics2D graphics, String text, UiRect rect, Color color, int style, int size, int maxLines) {
		if (graphics == null || rect.width() <= 0 || rect.height() <= 0 || maxLines <= 0) {
			return;
		}
		String normalized = text == null ? "" : text.trim().replace('\n', ' ').replace('\r', ' ');
		graphics.setColor(color);
		graphics.setFont(new Font(Font.SANS_SERIF, style, size));
		var metrics = graphics.getFontMetrics();
		List<String> lines = wrapText(metrics, normalized, rect.width(), maxLines);
		if (lines.isEmpty()) {
			return;
		}
		int lineHeight = metrics.getHeight();
		int totalHeight = lineHeight * lines.size();
		int y = rect.y() + Math.max(metrics.getAscent(), (rect.height() - totalHeight) / 2 + metrics.getAscent());
		for (String line : lines) {
			graphics.drawString(line, rect.x(), y);
			y += lineHeight;
		}
	}

	private static List<String> wrapText(java.awt.FontMetrics metrics, String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		if (metrics == null || maxWidth <= 0 || maxLines <= 0) {
			return lines;
		}
		String normalized = text == null ? "" : text.trim();
		if (normalized.isBlank()) {
			lines.add("");
			return lines;
		}
		String[] words = normalized.split("\\s+");
		StringBuilder current = new StringBuilder();
		for (String word : words) {
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (metrics.stringWidth(candidate) <= maxWidth) {
				current.setLength(0);
				current.append(candidate);
				continue;
			}
			if (!current.isEmpty()) {
				lines.add(current.toString());
				if (lines.size() >= maxLines - 1) {
					lines.add(truncateWithEllipsis(metrics, word, maxWidth));
					return lines;
				}
				current.setLength(0);
				current.append(word);
				continue;
			}
			lines.add(truncateWithEllipsis(metrics, word, maxWidth));
			if (lines.size() >= maxLines) {
				return lines;
			}
		}
		if (!current.isEmpty()) {
			lines.add(truncateWithEllipsis(metrics, current.toString(), maxWidth));
		}
		if (lines.size() > maxLines) {
			return lines.subList(0, maxLines);
		}
		return lines;
	}

	private static String truncateWithEllipsis(java.awt.FontMetrics metrics, String text, int maxWidth) {
		if (text == null || text.isEmpty() || metrics == null || maxWidth <= 0) {
			return "";
		}
		if (metrics.stringWidth(text) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisWidth = metrics.stringWidth(ellipsis);
		StringBuilder builder = new StringBuilder();
		for (int index = 0; index < text.length(); index++) {
			char current = text.charAt(index);
			if (metrics.stringWidth(builder.toString() + current) + ellipsisWidth > maxWidth) {
				break;
			}
			builder.append(current);
		}
		if (builder.isEmpty()) {
			return ellipsis;
		}
		return builder + ellipsis;
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

	private static BufferedImage loadPlayerUiIcon(PlayerUiIcon icon) {
		if (icon == null) {
			return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		}
		return PLAYER_UI_ICON_CACHE.computeIfAbsent(
				icon,
				key -> loadPngImage(key.resourcePath(), new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB))
		);
	}

	private static BufferedImage tintedPlayerUiIcon(PlayerUiIcon icon, Color tint) {
		if (icon == null || tint == null) {
			return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		}
		return PLAYER_UI_ICON_TINT_CACHE.computeIfAbsent(
				new PlayerUiIconTintKey(icon, tint.getRGB()),
				key -> colorizePlayerUiIcon(loadPlayerUiIcon(key.icon()), tint)
		);
	}

	private static BufferedImage colorizePlayerUiIcon(BufferedImage source, Color tint) {
		if (source == null || tint == null) {
			return new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		}
		BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int tintAlpha = tint.getAlpha();
		int tintRgb = tint.getRGB() & 0x00FFFFFF;
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int argb = source.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha <= 0) {
					continue;
				}
				int finalAlpha = alpha * tintAlpha / 255;
				tinted.setRGB(x, y, (finalAlpha << 24) | tintRgb);
			}
		}
		return tinted;
	}

	private static void drawPlayerUiIcon(Graphics2D graphics, UiRect rect, PlayerUiIcon icon, Color tint) {
		if (graphics == null || rect == null || rect.width() <= 0 || rect.height() <= 0 || icon == null || tint == null) {
			return;
		}
		BufferedImage tinted = tintedPlayerUiIcon(icon, tint);
		graphics.drawImage(tinted, rect.x(), rect.y(), rect.width(), rect.height(), null);
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

	private static void applyPatchToMap(MapItemSavedData mapData, MapPacketUpdate update) {
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
			MapPacketUpdate update = buildRenderedMapUpdate(mapId, mapData.scale, mapData.locked, tileFrame);
			if (update == null) {
				continue;
			}
			applyPatchToMap(mapData, update);
			changedUpdates.add(update);
		}
		sendMapToPlayers(level, component, changedUpdates);
	}

	private static MapPacketUpdate buildRenderedMapUpdate(MapId mapId, byte scale, boolean locked, byte[] tileFrame) {
		if (mapId == null || tileFrame == null || tileFrame.length < MAP_SIZE * MAP_SIZE) {
			return null;
		}
		byte[] previous = LAST_RENDERED_MAP_FRAMES.get(mapId.id());
		LAST_RENDERED_MAP_FRAMES.put(mapId.id(), tileFrame.clone());
		if (previous == null || previous.length < MAP_SIZE * MAP_SIZE) {
			return new MapPacketUpdate(mapId, scale, locked, 0, 0, MAP_SIZE, MAP_SIZE, tileFrame.clone());
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
		byte[] patch = new byte[patchWidth * patchHeight];
		for (int row = 0; row < patchHeight; row++) {
			int sourceOffset = (minY + row) * MAP_SIZE + minX;
			System.arraycopy(tileFrame, sourceOffset, patch, row * patchWidth, patchWidth);
		}
		return new MapPacketUpdate(mapId, scale, locked, minX, minY, patchWidth, patchHeight, patch);
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
					new MapItemSavedData.MapPatch(update.startX(), update.startY(), update.width(), update.height(), update.frame())
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
		if (!hasNearbyMediaViewer(server, key)) {
			return Math.max(baseDelay, paused ? youtubePollIdleIntervalMs() * 2L : youtubePollIdleIntervalMs());
		}
		return baseDelay;
	}

	private static long effectiveYoutubeUiRefreshThresholdMs(MinecraftServer server, ScreenRuntimeKey key) {
		return Math.max(50L, effectiveYoutubePollDelayMs(server, key, false));
	}

	private static ScreenComponent resolveScreenComponent(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return null;
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) {
			return null;
		}
		MonitorLevelState state = levelState(level.dimension());
		ScreenComponent cached = state.components().get(key);
		if (cached != null) {
			return cached;
		}
		ItemFrame rootFrame = findScreenFrame(level, key.pos(), key.facing());
		if (rootFrame == null) {
			return null;
		}
		ScreenComponent component = collectComponent(level, rootFrame, null);
		if (component != null) {
			cacheComponent(level, component);
		}
		return component;
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
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return "ВСТАВЬ YT MUSIC В ЧАТ";
		}
		return "ВСТАВЬ ССЫЛКУ В ЧАТ";
	}

	private static boolean isMediaPromptStatus(String status) {
		if (status == null) {
			return false;
		}
		return "ВСТАВЬ ССЫЛКУ В ЧАТ".equalsIgnoreCase(status)
				|| "ВСТАВЬ YOUTUBE В ЧАТ".equalsIgnoreCase(status)
				|| "ВСТАВЬ YT MUSIC В ЧАТ".equalsIgnoreCase(status)
				|| "ССЫЛКА В ЧАТ".equalsIgnoreCase(status)
				|| "SEND LINK IN CHAT".equalsIgnoreCase(status);
	}

	private static boolean isMediaLoadingStatus(String status) {
		if (status == null) {
			return false;
		}
		return "ЗАГРУЖАЮ...".equalsIgnoreCase(status)
				|| "ПОДКЛЮЧАЮ YOUTUBE...".equalsIgnoreCase(status)
				|| "ПОДКЛЮЧАЮ YT MUSIC...".equalsIgnoreCase(status)
				|| "LOADING...".equalsIgnoreCase(status)
				|| "BUFFERING".equalsIgnoreCase(status)
				|| "LOADING".equalsIgnoreCase(status)
				|| "CONNECTING".equalsIgnoreCase(status);
	}

	private static String loadingStatus(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return "ПОДКЛЮЧАЮ YOUTUBE...";
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return "ПОДКЛЮЧАЮ YT MUSIC...";
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
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Скинь в чат YouTube или YouTube Music ссылку");
		}
		return literal("Скинь в чат ссылку на картинку, гифку или видео");
	}

	private static Component loadingMessage(ScreenViewMode mode, ServerPlayer player) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Подключаю YouTube...");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Подключаю YouTube Music...");
		}
		return literal("Открываю медиа...");
	}

	private static Component mediaLoadedMessage(ServerPlayer player, boolean animated) {
		return literal(animated ? "Гифка добавлена в галерею" : "Картинка добавлена в галерею");
	}

	private static Component youtubeLoadedMessage(ServerPlayer player, boolean live) {
		return literal(live ? "YouTube стрим подключён" : "YouTube видео подключено");
	}

	private static Component youtubeMusicLoadedMessage(ServerPlayer player) {
		return literal("YouTube Music трек подключён");
	}

	private static Component youtubeQueueAddedMessage(ServerPlayer player, String title, int addedCount, boolean playlist) {
		String safeTitle = title == null || title.isBlank() ? "YouTube" : title;
		String locale = locale(player);
		if (locale.startsWith("ja")) {
			return literal((playlist ? "再生リスト" : "動画") + "を " + addedCount + " 件キューに追加: " + safeTitle);
		}
		if (locale.startsWith("uk")) {
			return literal("Додано в чергу " + addedCount + " " + (playlist ? "треків" : "відео") + ": " + safeTitle);
		}
		if (locale.startsWith("rpr")) {
			return literal("Въ очередь прибавлено " + addedCount + " " + (playlist ? "пѣсней" : "видѣво") + ": " + safeTitle);
		}
		if (locale.startsWith("ru")) {
			return literal("Добавлено в очередь " + addedCount + " " + (playlist ? "треков" : "видео") + ": " + safeTitle);
		}
		return literal("Queued " + addedCount + " " + (playlist ? "items" : "video") + ": " + safeTitle);
	}

	private static Component mediaLoadFailedMessage(ServerPlayer player, String error) {
		String reason = error == null || error.isBlank() ? "LOAD FAILED" : error;
		return literal("Не удалось загрузить: " + reason);
	}

	private static Component mediaCancelledMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Этот экран уже не ждёт YouTube ссылку");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Этот экран уже не ждёт YouTube Music ссылку");
		}
		return literal("Этот экран уже не ждёт ссылку");
	}

	private static Component mediaInvalidLinkMessage(ServerPlayer player, ScreenViewMode mode) {
		if (mode == ScreenViewMode.YOUTUBE) {
			return literal("Нужна нормальная YouTube ссылка");
		}
		if (mode == ScreenViewMode.YOUTUBE_MUSIC) {
			return literal("Нужна нормальная YouTube или YouTube Music ссылка");
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

	private static float clampFloat(float value, float min, float max) {
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
		return mediaTimelineFraction(layout, point, ScreenViewMode.HOME);
	}

	private static float mediaTimelineFraction(UiLayout layout, UiPoint point, ScreenViewMode mode) {
		UiRect trackRect = mediaTimelineTrackRect(layout, mode);
		if (trackRect.width() <= 0) {
			return 0.0F;
		}
		return (float) clampDouble((point.x() - trackRect.x()) / (double) Math.max(1, trackRect.width() - 1), 0.0D, 1.0D);
	}

	private static int mediaFrameIndexForFraction(MonitorMediaApp.LoadedMedia loadedMedia, float fraction) {
		if (loadedMedia == null || loadedMedia.frameCount() <= 1) {
			return 0;
		}
		return clampInt((int) Math.round(clampDouble(fraction, 0.0D, 1.0D) * (loadedMedia.frameCount() - 1)), 0, loadedMedia.frameCount() - 1);
	}

	private static boolean isPlayerMode(ScreenViewMode mode) {
		return mode == ScreenViewMode.GALLERY || mode == ScreenViewMode.YOUTUBE || mode == ScreenViewMode.YOUTUBE_MUSIC;
	}

	private static int youtubeQueueVisibleRowsPreview(MediaRuntimeState state) {
		return 5;
	}

	private static int galleryColumnsPreview(UiLayout layout) {
		return layout != null ? Math.max(1, mediaGalleryColumns(layout)) : 2;
	}

	private static int galleryVisibleRowsPreview(UiLayout layout) {
		return layout != null ? Math.max(1, mediaGalleryVisibleRows(layout)) : 2;
	}

	private static int galleryTotalRowsPreview(int itemCount, UiLayout layout) {
		if (itemCount <= 0) {
			return 0;
		}
		return layout != null
				? mediaGalleryTotalRows(itemCount, layout)
				: Math.max(0, (itemCount + galleryColumnsPreview(null) - 1) / galleryColumnsPreview(null));
	}

	private static int galleryRowForIndexPreview(int index, UiLayout layout) {
		return Math.max(0, index / Math.max(1, galleryColumnsPreview(layout)));
	}

	private static List<YoutubeQueueItemSnapshot> youtubeQueueSnapshots(MediaRuntimeState state) {
		if (state == null || state.youtubeQueue.isEmpty()) {
			return List.of();
		}
		List<YoutubeQueueItemSnapshot> items = new ArrayList<>(state.youtubeQueue.size());
		for (int index = 0; index < state.youtubeQueue.size(); index++) {
			YoutubeQueueItem item = state.youtubeQueue.get(index);
			items.add(new YoutubeQueueItemSnapshot(index, item != null ? item.title() : "YouTube", index == state.youtubeQueueIndex));
		}
		return items;
	}

	private static List<YoutubeQueueItemSnapshot> galleryItemSnapshots(MediaRuntimeState state) {
		if (state == null || state.galleryItems.isEmpty()) {
			return List.of();
		}
		List<YoutubeQueueItemSnapshot> items = new ArrayList<>(state.galleryItems.size());
		for (int index = 0; index < state.galleryItems.size(); index++) {
			GalleryItem item = state.galleryItems.get(index);
			items.add(new YoutubeQueueItemSnapshot(index, item != null ? item.title() : "Gallery", index == state.galleryIndex));
		}
		return items;
	}

	private static List<GalleryCardSnapshot> galleryCardSnapshots(MediaRuntimeState state) {
		if (state == null || state.galleryItems.isEmpty()) {
			return List.of();
		}
		List<GalleryCardSnapshot> items = new ArrayList<>(state.galleryItems.size());
		for (int index = 0; index < state.galleryItems.size(); index++) {
			GalleryItem item = state.galleryItems.get(index);
			MonitorMediaApp.LoadedMedia media = item != null ? item.media() : null;
			BufferedImage preview = media != null ? media.frame(0) : item != null ? item.preview() : null;
			GalleryItemKind itemKind = effectiveGalleryItemKind(item);
			boolean youtubeLoaded = item != null
					&& itemKind == GalleryItemKind.YOUTUBE
					&& item.url() != null
					&& !item.url().isBlank()
					&& MonitorYoutubeRelayClient.isQueueEntryLoaded(item.url());
			items.add(new GalleryCardSnapshot(
					index,
					item != null ? item.title() : "Gallery",
					(item != null && (itemKind == GalleryItemKind.YOUTUBE || itemKind == GalleryItemKind.VIDEO))
							|| (media != null && media.animated()),
					preview,
					index == state.galleryIndex,
					youtubeLoaded
			));
		}
		return items;
	}

	private static MediaOverlayWindowSnapshot youtubeQueueWindowSnapshot(MediaRuntimeState state, List<YoutubeQueueItemSnapshot> items) {
		if (state == null) {
			return null;
		}
		int totalItems = items != null ? items.size() : 0;
		boolean youtubeMusicMode = isYoutubeMusicMode(state.mode);
		String subtitle = totalItems <= 0
				? "Очередь пуста"
				: totalItems + " " + pluralizeQueueItems(totalItems, state.mode);
		return new MediaOverlayWindowSnapshot(
				MediaOverlayWindowType.YOUTUBE_QUEUE,
				youtubeMusicMode ? "ТРЕКИ" : "ОЧЕРЕДЬ",
				subtitle,
				items != null ? List.copyOf(items) : List.of(),
				Math.max(0, state.youtubeQueueScroll),
				Math.max(-1, state.youtubeQueueIndex)
		);
	}

	private static MediaOverlayWindowSnapshot galleryDeleteConfirmWindowSnapshot(MediaRuntimeState state) {
		if (state == null) {
			return null;
		}
		String target = state.mediaTitle != null && !state.mediaTitle.isBlank() ? state.mediaTitle : "это медиа";
		return new MediaOverlayWindowSnapshot(
				MediaOverlayWindowType.GALLERY_DELETE_CONFIRM,
				"УДАЛИТЬ?",
				target,
				List.of(),
				0,
				-1
		);
	}

	private static String pluralizeQueueItems(int count, ScreenViewMode mode) {
		if (isYoutubeMusicMode(mode)) {
			return pluralizeTracks(count);
		}
		return pluralizeVideos(count);
	}

	private static String pluralizeVideos(int count) {
		int mod100 = Math.floorMod(count, 100);
		int mod10 = Math.floorMod(count, 10);
		if (mod100 >= 11 && mod100 <= 19) {
			return "видео";
		}
		if (mod10 == 1) {
			return "видео";
		}
		if (mod10 >= 2 && mod10 <= 4) {
			return "видео";
		}
		return "видео";
	}

	private static String pluralizeTracks(int count) {
		int mod100 = Math.floorMod(count, 100);
		int mod10 = Math.floorMod(count, 10);
		if (mod100 >= 11 && mod100 <= 19) {
			return "треков";
		}
		if (mod10 == 1) {
			return "трек";
		}
		if (mod10 >= 2 && mod10 <= 4) {
			return "трека";
		}
		return "треков";
	}

	private static List<String> youtubeQueueUrlsLocked(MediaRuntimeState state) {
		if (state == null || state.youtubeQueue.isEmpty()) {
			return List.of();
		}
		List<String> urls = new ArrayList<>(state.youtubeQueue.size());
		for (YoutubeQueueItem item : state.youtubeQueue) {
			if (item != null && item.url() != null && !item.url().isBlank()) {
				urls.add(item.url());
			}
		}
		return urls;
	}

	private static List<String> retainedYoutubePreloadUrlsLocked(MediaRuntimeState state) {
		if (state == null || state.retainedYoutubePreloadUrls.isEmpty()) {
			return List.of();
		}
		return List.copyOf(state.retainedYoutubePreloadUrls);
	}

	private static List<String> retainedYoutubeMusicPreloadUrlsLocked(MediaRuntimeState state) {
		if (state == null || state.retainedYoutubeMusicUrls.isEmpty()) {
			return List.of();
		}
		return List.copyOf(state.retainedYoutubeMusicUrls);
	}

	private static Set<String> desiredYoutubeQueueWindowUrlsLocked(MediaRuntimeState state) {
		Set<String> desired = new LinkedHashSet<>();
		if (state == null || state.youtubeQueue.isEmpty()) {
			return desired;
		}
		int anchorIndex = state.youtubeQueueIndex >= 0 && state.youtubeQueueIndex < state.youtubeQueue.size() ? state.youtubeQueueIndex : -1;
		if (anchorIndex < 0) {
			for (int index = 0; index < Math.min(state.youtubeQueue.size(), YOUTUBE_PRELOAD_NEXT_COUNT); index++) {
				YoutubeQueueItem item = state.youtubeQueue.get(index);
				if (item != null && item.url() != null && !item.url().isBlank()) {
					desired.add(item.url());
				}
			}
			return desired;
		}
		YoutubeQueueItem current = state.youtubeQueue.get(anchorIndex);
		if (current != null && current.url() != null && !current.url().isBlank()) {
			desired.add(current.url());
		}
		for (int index = anchorIndex + 1; index <= Math.min(state.youtubeQueue.size() - 1, anchorIndex + YOUTUBE_PRELOAD_NEXT_COUNT); index++) {
			YoutubeQueueItem item = state.youtubeQueue.get(index);
			if (item != null && item.url() != null && !item.url().isBlank()) {
				desired.add(item.url());
			}
		}
		for (int index = Math.max(0, anchorIndex - YOUTUBE_PRELOAD_PREVIOUS_COUNT); index < anchorIndex; index++) {
			YoutubeQueueItem item = state.youtubeQueue.get(index);
			if (item != null && item.url() != null && !item.url().isBlank()) {
				desired.add(item.url());
			}
		}
		return desired;
	}

	private static YoutubeQueuePreloadDiff syncYoutubeQueuePreloadsLocked(MediaRuntimeState state) {
		if (state == null) {
			return YoutubeQueuePreloadDiff.EMPTY;
		}
		Set<String> desired = desiredYoutubeQueueWindowUrlsLocked(state);
		if (!state.galleryItems.isEmpty()) {
			for (GalleryItem item : state.galleryItems) {
				if (item == null || effectiveGalleryItemKind(item) != GalleryItemKind.YOUTUBE || item.url() == null || item.url().isBlank()) {
					continue;
				}
				desired.add(item.url());
			}
		}
		List<String> toRelease = new ArrayList<>();
		for (String url : List.copyOf(state.retainedYoutubePreloadUrls)) {
			if (!desired.contains(url)) {
				state.retainedYoutubePreloadUrls.remove(url);
				toRelease.add(url);
			}
		}
		List<String> toRetain = new ArrayList<>();
		for (String url : desired) {
			if (state.retainedYoutubePreloadUrls.add(url)) {
				toRetain.add(url);
			}
		}
		if (toRetain.isEmpty() && toRelease.isEmpty()) {
			return YoutubeQueuePreloadDiff.EMPTY;
		}
		return new YoutubeQueuePreloadDiff(List.copyOf(toRetain), List.copyOf(toRelease));
	}

	private static YoutubeMusicQueuePreloadDiff syncYoutubeMusicQueuePreloadsLocked(MediaRuntimeState state) {
		if (state == null) {
			return YoutubeMusicQueuePreloadDiff.EMPTY;
		}
		Set<String> desired = isYoutubeMusicMode(state.mode) ? desiredYoutubeQueueWindowUrlsLocked(state) : Set.of();
		List<String> toRelease = new ArrayList<>();
		for (String url : List.copyOf(state.retainedYoutubeMusicUrls)) {
			if (!desired.contains(url)) {
				state.retainedYoutubeMusicUrls.remove(url);
				toRelease.add(url);
			}
		}
		List<String> toRetain = new ArrayList<>();
		for (String url : desired) {
			if (state.retainedYoutubeMusicUrls.add(url)) {
				toRetain.add(url);
			}
		}
		if (toRetain.isEmpty() && toRelease.isEmpty()) {
			return YoutubeMusicQueuePreloadDiff.EMPTY;
		}
		return new YoutubeMusicQueuePreloadDiff(List.copyOf(toRetain), List.copyOf(toRelease));
	}

	private static void applyYoutubeQueuePreloadDiff(YoutubeQueuePreloadDiff diff) {
		if (diff == null || diff.isEmpty()) {
			return;
		}
		releaseYoutubeQueuePreloads(diff.releaseUrls());
		retainYoutubeQueuePreloads(diff.retainUrls());
	}

	private static void applyYoutubeMusicQueuePreloadDiff(YoutubeMusicQueuePreloadDiff diff) {
		if (diff == null || diff.isEmpty()) {
			return;
		}
		releaseYoutubeMusicQueuePreloads(diff.releaseUrls());
		retainYoutubeMusicQueuePreloads(diff.retainUrls());
	}

	private static void retainYoutubeQueuePreloads(List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			return;
		}
		ensureExecutors();
		List<String> snapshot = List.copyOf(urls);
		CompletableFuture.runAsync(() -> {
			for (String url : snapshot) {
				try {
					MonitorYoutubeRelayClient.retainQueueEntry(url);
				} catch (Exception exception) {
					Lg2.LOGGER.debug("Failed to retain YouTube queue preload for {}", url, exception);
				}
			}
		}, mediaIoExecutor);
	}

	private static void releaseYoutubeQueuePreloads(List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			return;
		}
		ensureExecutors();
		List<String> snapshot = List.copyOf(urls);
		CompletableFuture.runAsync(() -> {
			for (String url : snapshot) {
				try {
					MonitorYoutubeRelayClient.releaseQueueEntry(url);
				} catch (Exception exception) {
					Lg2.LOGGER.debug("Failed to release YouTube queue preload for {}", url, exception);
				}
			}
		}, mediaIoExecutor);
	}

	private static void retainYoutubeMusicQueuePreloads(List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			return;
		}
		ensureExecutors();
		List<String> snapshot = List.copyOf(urls);
		CompletableFuture.runAsync(() -> {
			for (String url : snapshot) {
				try {
					MonitorYoutubeMusicCache.retainQueueEntry(url);
				} catch (Exception exception) {
					Lg2.LOGGER.debug("Failed to retain YouTube Music queue preload for {}", url, exception);
				}
			}
		}, mediaIoExecutor);
	}

	private static void releaseYoutubeMusicQueuePreloads(List<String> urls) {
		if (urls == null || urls.isEmpty()) {
			return;
		}
		ensureExecutors();
		List<String> snapshot = List.copyOf(urls);
		CompletableFuture.runAsync(() -> {
			for (String url : snapshot) {
				try {
					MonitorYoutubeMusicCache.releaseQueueEntry(url);
				} catch (Exception exception) {
					Lg2.LOGGER.debug("Failed to release YouTube Music queue preload for {}", url, exception);
				}
			}
		}, mediaIoExecutor);
	}

	private static void clearYoutubeQueueLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.youtubeQueue.clear();
		state.youtubeQueueIndex = -1;
		state.youtubeQueueScroll = 0;
		state.youtubeQueueOpen = false;
	}

	private static void clearYoutubePlaybackLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		clearDownloadStateLocked(state);
		state.streamKind = PlaybackStreamKind.NONE;
		state.loadedMedia = null;
		state.streamFrame = null;
		state.youtubeFrameSequence = 0L;
		state.sourceUrl = null;
		state.relaySessionId = null;
		state.audioStreamUrl = null;
		state.mediaTitle = "";
		state.mediaSubtitle = "";
		state.frameIndex = 0;
		state.positionMs = 0L;
		state.durationMs = 0L;
		state.bufferedStartMs = 0L;
		state.bufferedEndMs = 0L;
		state.liveStream = false;
		state.audioPlaceholder = true;
		state.loadingBackdropFrame = null;
	}

	private static void clearGalleryLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		clearDownloadStateLocked(state);
		state.galleryItems.clear();
		state.galleryLoadingUrls.clear();
		state.galleryDeleteConfirmOpen = false;
		state.galleryHydrated = false;
		state.galleryPendingOpenUrl = null;
		state.galleryPendingOpenIndex = -1;
		state.galleryIndex = -1;
		state.galleryScroll = 0;
		state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
		clearGallerySelectionLocked(state);
	}

	private static void clearWallpaperLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.wallpaperUrl = null;
		state.wallpaperMedia = null;
		state.wallpaperScaleMode = MediaScaleMode.FIT;
		state.wallpaperFrameIndex = 0;
		state.wallpaperLoading = false;
	}

	private static void clearGallerySelectionLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.streamKind = PlaybackStreamKind.NONE;
		clearDownloadStateLocked(state);
		state.loadedMedia = null;
		state.sourceUrl = null;
		state.mediaTitle = "";
		state.mediaSubtitle = "";
		state.frameIndex = 0;
		state.positionMs = 0L;
		state.durationMs = 0L;
		state.bufferedStartMs = 0L;
		state.bufferedEndMs = 0L;
		state.liveStream = false;
		state.audioPlaceholder = true;
	}

	private static void clearLoadedContentLocked(MediaRuntimeState state) {
		clearLoadedContentLocked(state, true);
	}

	private static void clearLoadedContentLocked(MediaRuntimeState state, boolean clearYoutubeQueue) {
		clearYoutubePlaybackLocked(state);
		clearGalleryLocked(state);
		if (clearYoutubeQueue) {
			clearYoutubeQueueLocked(state);
		}
	}

	private static void clearTransientPlaybackStateLocked(MediaRuntimeState state, boolean clearYoutubeQueue) {
		if (state == null) {
			return;
		}
		clearYoutubePlaybackLocked(state);
		clearDownloadStateLocked(state);
		state.galleryDeleteConfirmOpen = false;
		state.galleryPendingOpenUrl = null;
		state.galleryPendingOpenIndex = -1;
		state.loading = false;
		state.waitingForLink = false;
		state.youtubeReturnToGallery = false;
		clearGallerySelectionLocked(state);
		if (clearYoutubeQueue) {
			clearYoutubeQueueLocked(state);
		}
	}

	private static GalleryItem currentGalleryItemLocked(MediaRuntimeState state) {
		if (state == null || state.galleryIndex < 0 || state.galleryIndex >= state.galleryItems.size()) {
			return null;
		}
		return state.galleryItems.get(state.galleryIndex);
	}

	private static BufferedImage currentYoutubeMusicBackdropLocked(MediaRuntimeState state) {
		if (state == null) {
			return null;
		}
		if (state.streamFrame != null) {
			return state.streamFrame;
		}
		if (state.loadedMedia != null) {
			return state.loadedMedia.frame(state.frameIndex);
		}
		return state.loadingBackdropFrame;
	}

	private static Optional<GalleryItem> currentGalleryItemMatchingUrlLocked(MediaRuntimeState state, String url) {
		if (state == null || url == null || url.isBlank()) {
			return Optional.empty();
		}
		for (GalleryItem item : state.galleryItems) {
			if (item != null && Objects.equals(item.url(), url)) {
				return Optional.of(item);
			}
		}
		return Optional.empty();
	}

	private static boolean currentGalleryItemSavedLocked(MediaRuntimeState state) {
		return currentGalleryItemLocked(state) != null;
	}

	private static boolean currentGalleryItemCanBeWallpaperLocked(MediaRuntimeState state) {
		GalleryItem item = currentGalleryItemLocked(state);
		return item != null
				&& effectiveGalleryItemKind(item) == GalleryItemKind.MEDIA
				&& item.media() != null
				&& item.url() != null
				&& !item.url().isBlank();
	}

	private static boolean currentGalleryItemIsWallpaperLocked(MediaRuntimeState state) {
		GalleryItem item = currentGalleryItemLocked(state);
		return item != null
				&& item.url() != null
				&& !item.url().isBlank()
				&& Objects.equals(item.url(), state.wallpaperUrl);
	}

	private static PersistedWallpaperState persistedWallpaperStateLocked(MediaRuntimeState state) {
		if (state == null || state.wallpaperUrl == null || state.wallpaperUrl.isBlank()) {
			return null;
		}
		return new PersistedWallpaperState(
				state.wallpaperUrl,
				state.wallpaperScaleMode != null ? state.wallpaperScaleMode : MediaScaleMode.FIT
		);
	}

	private static BufferedImage copyBufferedImage(BufferedImage source) {
		if (source == null) {
			return null;
		}
		BufferedImage copy = new BufferedImage(
				Math.max(1, source.getWidth()),
				Math.max(1, source.getHeight()),
				BufferedImage.TYPE_INT_ARGB
		);
		Graphics2D graphics = copy.createGraphics();
		try {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(source, 0, 0, null);
		} finally {
			graphics.dispose();
		}
		return copy;
	}

	private static int normalizeGalleryIndexLocked(MediaRuntimeState state, int requestedIndex) {
		if (state == null || state.galleryItems.isEmpty()) {
			return -1;
		}
		return Math.floorMod(requestedIndex, state.galleryItems.size());
	}

	private static boolean selectGalleryItemLocked(MediaRuntimeState state, int requestedIndex, UiLayout layout) {
		int resolvedIndex = normalizeGalleryIndexLocked(state, requestedIndex);
		if (resolvedIndex < 0) {
			clearGallerySelectionLocked(state);
			state.galleryIndex = -1;
			state.galleryScroll = 0;
			state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
			return false;
		}
		GalleryItem item = state.galleryItems.get(resolvedIndex);
		if (item == null || item.kind() == GalleryItemKind.YOUTUBE || item.media() == null) {
			return false;
		}
		cancelPlaybackLocked(state);
		clearYoutubePlaybackLocked(state);
		state.galleryIndex = resolvedIndex;
		state.galleryDeleteConfirmOpen = false;
		state.loadedMedia = item.media();
		state.sourceUrl = item.url();
		state.mediaTitle = item.title();
		state.frameIndex = 0;
		state.userPaused = false;
		state.gallerySurfaceMode = GallerySurfaceMode.PLAYER;
		state.positionMs = 0L;
		state.durationMs = 0L;
		state.bufferedStartMs = 0L;
		state.bufferedEndMs = 0L;
		state.liveStream = false;
		state.audioPlaceholder = true;
		int visibleRows = galleryVisibleRowsPreview(layout);
		int totalRows = galleryTotalRowsPreview(state.galleryItems.size(), layout);
		int maxScroll = Math.max(0, totalRows - visibleRows);
		int targetRow = galleryRowForIndexPreview(resolvedIndex, layout);
		if (targetRow < state.galleryScroll) {
			state.galleryScroll = targetRow;
		} else if (targetRow >= state.galleryScroll + visibleRows) {
			state.galleryScroll = targetRow - visibleRows + 1;
		}
		state.galleryScroll = clampInt(state.galleryScroll, 0, maxScroll);
		return true;
	}

	private static boolean openTransientGalleryItemLocked(MediaRuntimeState state, String title, String url, MonitorMediaApp.LoadedMedia media) {
		if (state == null || media == null) {
			return false;
		}
		cancelPlaybackLocked(state);
		clearYoutubePlaybackLocked(state);
		state.mode = ScreenViewMode.GALLERY;
		state.loadedMedia = media;
		state.galleryDeleteConfirmOpen = false;
		state.sourceUrl = url;
		state.mediaTitle = title == null || title.isBlank() ? galleryItemTitle(url, media, state.galleryItems.size() + 1) : title;
		state.frameIndex = 0;
		state.userPaused = false;
		state.galleryIndex = -1;
		state.gallerySurfaceMode = GallerySurfaceMode.PLAYER;
		state.positionMs = 0L;
		state.durationMs = 0L;
		state.bufferedStartMs = 0L;
		state.bufferedEndMs = 0L;
		state.liveStream = false;
		state.audioPlaceholder = true;
		return true;
	}

	private static boolean saveCurrentGalleryItemLocked(MediaRuntimeState state, UiLayout layout) {
		if (state == null || state.loadedMedia == null || state.sourceUrl == null || state.sourceUrl.isBlank()) {
			return false;
		}
		if (currentGalleryItemSavedLocked(state)) {
			return true;
		}
		String title = state.mediaTitle == null || state.mediaTitle.isBlank()
				? galleryItemTitle(state.sourceUrl, state.loadedMedia, state.galleryItems.size() + 1)
				: state.mediaTitle;
		int index = upsertGalleryItemLocked(
				state,
				title,
				state.sourceUrl,
				null,
				state.loadedMedia,
				state.loadedMedia.frameCount() > 0 ? state.loadedMedia.frame(0) : null,
				GalleryItemKind.MEDIA
		);
		return index >= 0 && selectGalleryItemLocked(state, index, layout);
	}

	private static boolean saveCurrentYoutubeToGalleryLocked(MediaRuntimeState state) {
		if (state == null || state.sourceUrl == null || state.sourceUrl.isBlank()) {
			return false;
		}
		String title = state.mediaTitle == null || state.mediaTitle.isBlank() ? "YouTube" : state.mediaTitle;
		return upsertGalleryItemLocked(
				state,
				title,
				state.sourceUrl,
				null,
				null,
				copyBufferedImage(state.streamFrame),
				GalleryItemKind.YOUTUBE
		) >= 0;
	}

	private static int upsertGalleryItemLocked(
			MediaRuntimeState state,
			String title,
			String url,
			String localMediaKey,
			MonitorMediaApp.LoadedMedia media,
			BufferedImage preview,
			GalleryItemKind kind
	) {
		if (state == null || url == null || url.isBlank()) {
			return -1;
		}
		int existingIndex = resolveGalleryItemIndex(state, url, -1);
		String resolvedTitle = title == null || title.isBlank()
				? (media != null ? galleryItemTitle(url, media, state.galleryItems.size() + 1) : "Media")
				: title;
		if (existingIndex >= 0 && existingIndex < state.galleryItems.size()) {
			GalleryItem existing = state.galleryItems.get(existingIndex);
			state.galleryItems.set(
					existingIndex,
					new GalleryItem(
							resolvedTitle,
							url,
							localMediaKey != null && !localMediaKey.isBlank() ? localMediaKey : existing != null ? existing.localMediaKey() : null,
							media != null ? media : existing != null ? existing.media() : null,
							preview != null ? preview : existing != null ? existing.preview() : null,
							kind != null ? kind : existing != null ? existing.kind() : GalleryItemKind.MEDIA
					)
			);
			return existingIndex;
		}
		state.galleryItems.add(new GalleryItem(
				resolvedTitle,
				url,
				localMediaKey,
				media,
				preview,
				kind != null ? kind : GalleryItemKind.MEDIA
		));
		return state.galleryItems.size() - 1;
	}

	private static void markDownloadStartedLocked(MediaRuntimeState state, String url, UUID requesterUuid) {
		if (state == null) {
			return;
		}
		state.downloadInProgress = true;
		state.downloadTargetUrl = url;
		state.downloadRequesterUuid = requesterUuid;
		state.downloadStartedAtMillis = System.currentTimeMillis();
		state.downloadCompletedUrl = null;
		state.downloadCompletedUntilMillis = 0L;
	}

	private static void markDownloadCompletedLocked(MediaRuntimeState state, String url) {
		if (state == null) {
			return;
		}
		state.downloadInProgress = false;
		state.downloadTargetUrl = null;
		state.downloadRequesterUuid = null;
		state.downloadStartedAtMillis = 0L;
		state.downloadCompletedUrl = url;
		state.downloadCompletedUntilMillis = System.currentTimeMillis() + MEDIA_ACTION_COMPLETE_VISIBLE_MILLIS;
	}

	private static void clearDownloadStateLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.downloadInProgress = false;
		state.downloadTargetUrl = null;
		state.downloadRequesterUuid = null;
		state.downloadStartedAtMillis = 0L;
	}

	private static long remainingDownloadSpinnerMillisLocked(MediaRuntimeState state) {
		if (state == null || state.downloadStartedAtMillis <= 0L) {
			return 0L;
		}
		long elapsed = System.currentTimeMillis() - state.downloadStartedAtMillis;
		return Math.max(0L, MEDIA_ACTION_SPINNER_MIN_MILLIS - elapsed);
	}

	private static void scheduleActionCompletionReset(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		ensureExecutors();
		mediaScheduler.schedule(() -> server.execute(() -> requestRuntimeRender(server, key)), MEDIA_ACTION_COMPLETE_VISIBLE_MILLIS, TimeUnit.MILLISECONDS);
	}

	private static boolean isYoutubeFullyBufferedLocked(MediaRuntimeState state) {
		if (state == null || state.liveStream || state.durationMs <= 0L) {
			return false;
		}
		return state.bufferedStartMs <= YOUTUBE_FULLY_BUFFERED_TOLERANCE_MS
				&& state.bufferedEndMs >= Math.max(0L, state.durationMs - YOUTUBE_FULLY_BUFFERED_TOLERANCE_MS);
	}

	private static boolean isYoutubeGalleryDownloadReadyLocked(MediaRuntimeState state) {
		if (state == null || state.liveStream || state.sourceUrl == null || state.sourceUrl.isBlank()) {
			return false;
		}
		if (MonitorYoutubeRelayClient.isQueueEntryLoaded(state.sourceUrl)) {
			return true;
		}
		long preloadTargetMs = MonitorYoutubeRelayClient.queuePreloadDurationMs();
		if (state.durationMs > 0L) {
			preloadTargetMs = Math.min(preloadTargetMs, state.durationMs);
		}
		long requiredBufferedEndMs = Math.max(0L, preloadTargetMs - YOUTUBE_FULLY_BUFFERED_TOLERANCE_MS);
		return state.bufferedStartMs <= YOUTUBE_FULLY_BUFFERED_TOLERANCE_MS
				&& state.bufferedEndMs >= requiredBufferedEndMs;
	}

	private static GalleryRemovalResult removeGalleryItemLocked(MediaRuntimeState state, int requestedIndex, UiLayout layout) {
		if (state == null || state.galleryItems.isEmpty()) {
			clearGallerySelectionLocked(state);
			state.galleryIndex = -1;
			state.galleryScroll = 0;
			return new GalleryRemovalResult(null, false);
		}
		int resolvedIndex = clampInt(requestedIndex, 0, state.galleryItems.size() - 1);
		GalleryItem removed = state.galleryItems.remove(resolvedIndex);
		if (removed != null && removed.url() != null && Objects.equals(removed.url(), state.wallpaperUrl)) {
			clearWallpaperLocked(state);
		}
		if (state.galleryItems.isEmpty()) {
			clearGallerySelectionLocked(state);
			state.galleryIndex = -1;
			state.galleryScroll = 0;
			return new GalleryRemovalResult(removed, false);
		}
		int nextIndex = Math.min(resolvedIndex, state.galleryItems.size() - 1);
		int visibleRows = galleryVisibleRowsPreview(layout);
		int totalRows = galleryTotalRowsPreview(state.galleryItems.size(), layout);
		state.galleryScroll = clampInt(state.galleryScroll, 0, Math.max(0, totalRows - visibleRows));
		return new GalleryRemovalResult(removed, selectGalleryItemLocked(state, nextIndex, layout));
	}

	private static String galleryItemTitle(String url, MonitorMediaApp.LoadedMedia media, int fallbackIndex) {
		String normalized = url == null ? "" : url.trim();
		int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
		String tail = slash >= 0 && slash + 1 < normalized.length() ? normalized.substring(slash + 1) : normalized;
		int query = tail.indexOf('?');
		if (query >= 0) {
			tail = tail.substring(0, query);
		}
		int hash = tail.indexOf('#');
		if (hash >= 0) {
			tail = tail.substring(0, hash);
		}
		if (tail == null || tail.isBlank()) {
			if (MonitorMediaApp.looksLikeDirectVideoUrl(url)) {
				return "VIDEO " + fallbackIndex;
			}
			return media != null && media.animated() ? "GIF " + fallbackIndex : "IMAGE " + fallbackIndex;
		}
		return tail.length() > 48 ? tail.substring(0, 48) : tail;
	}

	private static void ensureYoutubeQueueCurrentEntryLocked(MediaRuntimeState state) {
		if (state == null || !isYoutubeFamilyMode(state.mode) || state.sourceUrl == null || state.sourceUrl.isBlank()) {
			return;
		}
		if (state.youtubeQueueIndex >= 0 && state.youtubeQueueIndex < state.youtubeQueue.size()) {
			YoutubeQueueItem current = state.youtubeQueue.get(state.youtubeQueueIndex);
			if (current != null && Objects.equals(current.url(), state.sourceUrl)) {
				String nextTitle = state.mediaTitle != null && !state.mediaTitle.isBlank() ? state.mediaTitle : current.title();
				state.youtubeQueue.set(state.youtubeQueueIndex, new YoutubeQueueItem(nextTitle, state.sourceUrl));
				return;
			}
		}
		for (int index = 0; index < state.youtubeQueue.size(); index++) {
			YoutubeQueueItem item = state.youtubeQueue.get(index);
			if (item != null && Objects.equals(item.url(), state.sourceUrl)) {
				String nextTitle = state.mediaTitle != null && !state.mediaTitle.isBlank() ? state.mediaTitle : item.title();
				state.youtubeQueue.set(index, new YoutubeQueueItem(nextTitle, state.sourceUrl));
				state.youtubeQueueIndex = index;
				return;
			}
		}
		String title = state.mediaTitle != null && !state.mediaTitle.isBlank() ? state.mediaTitle : "YouTube";
		state.youtubeQueue.add(new YoutubeQueueItem(title, state.sourceUrl));
		state.youtubeQueueIndex = state.youtubeQueue.size() - 1;
	}

	private static int normalizeYoutubeQueueIndexLocked(MediaRuntimeState state, int requestedIndex) {
		if (state == null || state.youtubeQueue.isEmpty()) {
			return -1;
		}
		return Math.floorMod(requestedIndex, state.youtubeQueue.size());
	}

	private static boolean hasDisplayableMediaLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (isStreamPlaybackLocked(state)) {
			return state.sourceUrl != null || state.streamFrame != null;
		}
		return state.loadedMedia != null || !state.galleryItems.isEmpty();
	}

	private static boolean playbackControlsVisibleLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (isStreamPlaybackLocked(state)) {
			return state.loading
					|| state.sourceUrl != null
					|| state.relaySessionId != null
					|| (state.streamKind == PlaybackStreamKind.YOUTUBE && state.mode == ScreenViewMode.YOUTUBE && !state.youtubeQueue.isEmpty());
		}
		return state.loadedMedia != null || !state.galleryItems.isEmpty();
	}

	private static boolean mediaControlUiVisibleLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (state.mode == ScreenViewMode.GALLERY && state.gallerySurfaceMode == GallerySurfaceMode.BROWSER) {
			return false;
		}
		if (isStreamPlaybackLocked(state)) {
			return hasDisplayableMediaLocked(state) || playbackControlsVisibleLocked(state);
		}
		return state.loadedMedia != null
				|| state.loading
				|| (state.sourceUrl != null && !state.sourceUrl.isBlank());
	}

	private static boolean isYoutubeHomePromptLocked(MediaRuntimeState state) {
		if (state == null || !isYoutubeFamilyMode(state.mode)) {
			return false;
		}
		return !state.loading
				&& !hasDisplayableMediaLocked(state)
				&& (state.sourceUrl == null || state.sourceUrl.isBlank())
				&& state.relaySessionId == null;
	}

	private static boolean resolvedActionVisible(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (isGalleryBackedYoutubeLocked(state)) {
			return true;
		}
		if (state.mode == ScreenViewMode.YOUTUBE) {
			return state.sourceUrl != null && !state.sourceUrl.isBlank();
		}
		return state.mode == ScreenViewMode.GALLERY && state.gallerySurfaceMode == GallerySurfaceMode.PLAYER;
	}

	private static MediaActionGlyph resolvedActionGlyph(MediaRuntimeState state) {
		if (state == null) {
			return MediaActionGlyph.DOWNLOAD;
		}
		if (isGalleryBackedYoutubeLocked(state)) {
			return MediaActionGlyph.TRASH;
		}
		if (state.mode == ScreenViewMode.GALLERY && currentGalleryItemSavedLocked(state)) {
			return MediaActionGlyph.TRASH;
		}
		return MediaActionGlyph.DOWNLOAD;
	}

	private static MediaActionVisualState resolvedActionVisualState(MediaRuntimeState state) {
		if (state == null) {
			return MediaActionVisualState.IDLE;
		}
		if (isGalleryBackedYoutubeLocked(state)) {
			return MediaActionVisualState.IDLE;
		}
		String currentUrl = state.sourceUrl;
		if (state.downloadInProgress && currentUrl != null && Objects.equals(currentUrl, state.downloadTargetUrl)) {
			return MediaActionVisualState.DOWNLOADING;
		}
		if (currentUrl != null
				&& state.downloadCompletedUrl != null
				&& Objects.equals(currentUrl, state.downloadCompletedUrl)
				&& System.currentTimeMillis() < state.downloadCompletedUntilMillis) {
			return MediaActionVisualState.COMPLETE;
		}
		if (state.mode == ScreenViewMode.YOUTUBE && currentUrl != null && hasGalleryItemForUrlLocked(state, currentUrl)) {
			return MediaActionVisualState.COMPLETE;
		}
		return MediaActionVisualState.IDLE;
	}

	private static boolean hasGalleryItemForUrlLocked(MediaRuntimeState state, String url) {
		return resolveGalleryItemIndex(state, url, -1) >= 0;
	}

	private static boolean isGalleryBackedYoutubeLocked(MediaRuntimeState state) {
		return state != null
				&& state.mode == ScreenViewMode.GALLERY
				&& state.gallerySurfaceMode == GallerySurfaceMode.PLAYER
				&& state.sourceUrl != null
				&& !state.sourceUrl.isBlank()
				&& state.streamKind == PlaybackStreamKind.YOUTUBE
				&& currentGalleryItemLocked(state) != null
				&& effectiveGalleryItemKind(currentGalleryItemLocked(state)) == GalleryItemKind.YOUTUBE
				&& hasGalleryItemForUrlLocked(state, state.sourceUrl);
	}

	private static boolean isGalleryBackedDirectVideoLocked(MediaRuntimeState state) {
		return state != null
				&& state.mode == ScreenViewMode.GALLERY
				&& state.gallerySurfaceMode == GallerySurfaceMode.PLAYER
				&& state.sourceUrl != null
				&& !state.sourceUrl.isBlank()
				&& state.streamKind == PlaybackStreamKind.DIRECT_VIDEO
				&& currentGalleryItemLocked(state) != null
				&& effectiveGalleryItemKind(currentGalleryItemLocked(state)) == GalleryItemKind.VIDEO
				&& hasGalleryItemForUrlLocked(state, state.sourceUrl);
	}

	private static boolean isStreamPlaybackLocked(MediaRuntimeState state) {
		return state != null && state.streamKind != PlaybackStreamKind.NONE;
	}

	private static boolean isYoutubePlaybackLocked(MediaRuntimeState state) {
		return isStreamPlaybackLocked(state) && state.streamKind == PlaybackStreamKind.YOUTUBE;
	}

	private static boolean isDirectVideoPlaybackLocked(MediaRuntimeState state) {
		return isStreamPlaybackLocked(state) && state.streamKind == PlaybackStreamKind.DIRECT_VIDEO;
	}

	private static boolean hasActiveStreamPlaybackLocked(MediaRuntimeState state) {
		if (!isStreamPlaybackLocked(state)) {
			return false;
		}
		return isGalleryBackedYoutubeLocked(state)
				|| isGalleryBackedDirectVideoLocked(state)
				|| state.loading
				|| (state.sourceUrl != null && !state.sourceUrl.isBlank())
				|| (state.relaySessionId != null && !state.relaySessionId.isBlank())
				|| (state.streamKind == PlaybackStreamKind.YOUTUBE && !state.youtubeQueue.isEmpty());
	}

	private static boolean canSeekTimelineLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (isStreamPlaybackLocked(state)) {
			return state.durationMs > 0L && !state.liveStream;
		}
		return state.loadedMedia != null && state.loadedMedia.frameCount() > 1;
	}

	private static boolean canTogglePlaybackLocked(MediaRuntimeState state) {
		if (state == null) {
			return false;
		}
		if (isStreamPlaybackLocked(state)) {
			return state.relaySessionId != null || state.loading;
		}
		return state.loadedMedia != null && state.loadedMedia.animated();
	}

	private static String resolvedTimelineLabel(MediaVisualSnapshot state, UiLayout layout) {
		if (state == null || layout == null || !state.timelineVisible()) {
			return "";
		}
		return switch (timelineCounterDetailLevel(layout)) {
			case NONE -> "";
			case COMPACT -> {
				if (state.streamPlayback()) {
					yield state.frameCount() <= 0 ? state.timelineLabel() : formatPlaybackTime(state.frameIndex());
				}
				yield state.timelineLabel();
			}
			case FULL -> state.timelineLabel();
		};
	}

	private static String timelineLeadingLabel(MediaVisualSnapshot state) {
		if (state == null || state.timelineLabel() == null || state.timelineLabel().isBlank()) {
			return "";
		}
		String label = state.timelineLabel();
		int wideDivider = label.indexOf(" / ");
		if (wideDivider >= 0) {
			return label.substring(0, wideDivider).trim();
		}
		if (state.streamPlayback()) {
			return label;
		}
		int slash = label.indexOf('/');
		if (slash > 0) {
			return label.substring(0, slash).trim();
		}
		return label;
	}

	private static String timelineTrailingLabel(MediaVisualSnapshot state) {
		if (state == null || state.timelineLabel() == null || state.timelineLabel().isBlank()) {
			return "";
		}
		String label = state.timelineLabel();
		int wideDivider = label.indexOf(" / ");
		if (wideDivider >= 0 && wideDivider + 3 <= label.length()) {
			return label.substring(wideDivider + 3).trim();
		}
		if (state.streamPlayback()) {
			return "";
		}
		int slash = label.indexOf('/');
		if (slash >= 0 && slash + 1 <= label.length()) {
			return label.substring(slash + 1).trim();
		}
		return "";
	}

	private static TimelineCounterDetailLevel timelineCounterDetailLevel(UiLayout layout) {
		int minSpan = smallestScreenTileSpan(layout);
		if (minSpan <= 1) {
			return TimelineCounterDetailLevel.NONE;
		}
		if (minSpan == 2) {
			return TimelineCounterDetailLevel.COMPACT;
		}
		return TimelineCounterDetailLevel.FULL;
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

	private static float youtubeBufferedFraction(MediaRuntimeState state, long positionMs) {
		if (state == null || state.durationMs <= 0L || positionMs <= 0L) {
			return 0.0F;
		}
		return (float) clampDouble((double) positionMs / (double) state.durationMs, 0.0D, 1.0D);
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
			if (!isStreamPlaybackLocked(state) || state.relaySessionId == null || state.waitingForLink) {
				return;
			}
			cancelPlaybackLocked(state);
			state.playbackFuture = mediaScheduler.schedule(() -> refreshYoutubeSnapshot(server, key), Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
		}
	}

	private static void refreshConnectedSpeakersNow(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null) {
			return;
		}
		SpeakerSystem.refreshConnectedSpeakersNow(server, level, key.pos());
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

	private static boolean isYoutubeFamilyMode(ScreenViewMode mode) {
		return mode == ScreenViewMode.YOUTUBE || mode == ScreenViewMode.YOUTUBE_MUSIC;
	}

	private static boolean isYoutubeMusicMode(ScreenViewMode mode) {
		return mode == ScreenViewMode.YOUTUBE_MUSIC;
	}

	private enum ScreenViewMode {
		HOME("home"),
		GALLERY("gallery"),
		MAX("max"),
		YOUTUBE("youtube"),
		YOUTUBE_MUSIC("youtubemusic");

		private final String serializedName;

		ScreenViewMode(String serializedName) {
			this.serializedName = serializedName;
		}

		String serializedName() {
			return this.serializedName;
		}

		static ScreenViewMode fromTag(String value) {
			if ("media".equalsIgnoreCase(value)) {
				return GALLERY;
			}
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

	private enum GallerySurfaceMode {
		BROWSER,
		PLAYER
	}

	private enum MediaOverlayWindowType {
		YOUTUBE_QUEUE,
		GALLERY_DELETE_CONFIRM
	}

	private enum YoutubeLinkRequestAction {
		REPLACE_QUEUE,
		APPEND_QUEUE
	}

	private enum PlaybackStreamKind {
		NONE,
		YOUTUBE,
		DIRECT_VIDEO
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

	private enum TransportButtonKind {
		BACK,
		PLAY_PAUSE,
		FORWARD
	}

	private enum MediaButtonSegment {
		SINGLE,
		LEFT,
		MIDDLE,
		RIGHT
	}

	private enum MediaActionGlyph {
		TRASH,
		DOWNLOAD,
		CHECK,
		WALLPAPER
	}

	private enum MediaActionVisualState {
		IDLE,
		DOWNLOADING,
		COMPLETE
	}

	private enum GalleryItemKind {
		MEDIA,
		VIDEO,
		YOUTUBE;

		private static GalleryItemKind fromPersisted(String value, String url) {
			if ("youtube".equalsIgnoreCase(value)) {
				return YOUTUBE;
			}
			if ("video".equalsIgnoreCase(value)) {
				return VIDEO;
			}
			return MonitorYoutubeRelayClient.looksLikeYoutubeUrl(url) ? YOUTUBE : MEDIA;
		}

		private String persistedName() {
			return switch (this) {
				case YOUTUBE -> "youtube";
				case VIDEO -> "video";
				case MEDIA -> "media";
			};
		}
	}

	private enum TimelineCounterDetailLevel {
		NONE,
		COMPACT,
		FULL
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

	private static final class MonitorLevelState {
		private final ResourceKey<Level> dimension;
		private final Set<ScreenKey> knownFrames = ConcurrentHashMap.newKeySet();
		private final Map<ScreenKey, ScreenRuntimeKey> frameToRuntime = new ConcurrentHashMap<>();
		private final Map<ScreenRuntimeKey, ScreenComponent> components = new ConcurrentHashMap<>();
		private final Set<ScreenKey> dirtyFramesSet = ConcurrentHashMap.newKeySet();
		private final ConcurrentLinkedQueue<ScreenKey> dirtyFrames = new ConcurrentLinkedQueue<>();
		private final Set<ScreenRuntimeKey> dirtyRuntimeSet = ConcurrentHashMap.newKeySet();
		private final ConcurrentLinkedQueue<ScreenRuntimeKey> dirtyRuntimes = new ConcurrentLinkedQueue<>();
		private final Set<ScreenRuntimeKey> powerRuntimeSet = ConcurrentHashMap.newKeySet();
		private final ConcurrentLinkedQueue<ScreenRuntimeKey> powerRuntimes = new ConcurrentLinkedQueue<>();

		private MonitorLevelState(ResourceKey<Level> dimension) {
			this.dimension = dimension;
		}

		private ResourceKey<Level> dimension() {
			return this.dimension;
		}

		private Set<ScreenKey> knownFrames() {
			return this.knownFrames;
		}

		private Map<ScreenKey, ScreenRuntimeKey> frameToRuntime() {
			return this.frameToRuntime;
		}

		private Map<ScreenRuntimeKey, ScreenComponent> components() {
			return this.components;
		}

		private void enqueueDirtyFrame(ScreenKey key) {
			if (key != null && this.dirtyFramesSet.add(key)) {
				this.dirtyFrames.add(key);
			}
		}

		private ScreenKey pollDirtyFrame() {
			ScreenKey key = this.dirtyFrames.poll();
			if (key != null) {
				this.dirtyFramesSet.remove(key);
			}
			return key;
		}

		private void enqueueDirtyRuntime(ScreenRuntimeKey key) {
			if (key != null && this.dirtyRuntimeSet.add(key)) {
				this.dirtyRuntimes.add(key);
			}
		}

		private ScreenRuntimeKey pollDirtyRuntime() {
			ScreenRuntimeKey key = this.dirtyRuntimes.poll();
			if (key != null) {
				this.dirtyRuntimeSet.remove(key);
			}
			return key;
		}

		private void enqueuePowerRuntime(ScreenRuntimeKey key) {
			if (key != null && this.powerRuntimeSet.add(key)) {
				this.powerRuntimes.add(key);
			}
		}

		private ScreenRuntimeKey pollPowerRuntime() {
			ScreenRuntimeKey key = this.powerRuntimes.poll();
			if (key != null) {
				this.powerRuntimeSet.remove(key);
			}
			return key;
		}
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

	private record OverlayWindowCacheKey(MediaOverlayWindowSnapshot snapshot, int width, int height, int unit) {
	}

	private record OverlayWindowFamilyKey(MediaOverlayWindowType type, int width, int height, int unit) {
	}

	private record MediaVisualSnapshot(
			ScreenViewMode mode,
			long version,
			BufferedImage frame,
			BufferedImage backgroundFrame,
			boolean hasMedia,
			boolean galleryBrowser,
			boolean galleryCurrentSaved,
			boolean galleryBackedYoutube,
			boolean streamPlayback,
			boolean playbackControlsVisible,
			boolean loading,
			boolean timelineVisible,
			boolean centerPlayPauseVisible,
			boolean timelineSeekable,
			int frameIndex,
			int frameCount,
			float timelineFraction,
			float bufferedStartFraction,
			float bufferedEndFraction,
			String timelineLabel,
			boolean paused,
			MediaOverlayMode overlayMode,
			MediaScaleMode scaleMode,
			String statusText,
			String linkPlaceholder,
			String mediaTitle,
			String mediaSubtitle,
			TaskProgress.Snapshot progress,
			List<YoutubeQueueItemSnapshot> mediaListItems,
			List<GalleryCardSnapshot> galleryCards,
			boolean actionVisible,
			MediaActionGlyph actionGlyph,
			MediaActionVisualState actionState,
			boolean wallpaperActionVisible,
			MediaActionGlyph wallpaperActionGlyph,
			MediaActionVisualState wallpaperActionState,
			boolean youtubeQueueOpen,
			int mediaListScroll,
			int currentMediaListIndex,
			MediaOverlayWindowSnapshot overlayWindow
		) {
	}

	private record WallpaperVisualSnapshot(
			BufferedImage frame,
			MediaScaleMode scaleMode
	) {
	}

	private record MediaOverlayWindowSnapshot(
			MediaOverlayWindowType type,
			String title,
			String subtitle,
			List<YoutubeQueueItemSnapshot> items,
			int scroll,
			int currentIndex
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
			MediaVisualSnapshot mediaSnapshot,
			WallpaperVisualSnapshot wallpaperSnapshot
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

	private record PendingMediaLinkRequest(ScreenRuntimeKey screenKey, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
	}

	private record InFlightMediaLinkRequest(ScreenRuntimeKey screenKey, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
	}

	private record YoutubeQueueItemSnapshot(int queueIndex, String title, boolean current) {
	}

	private record YoutubeQueuePreloadDiff(List<String> retainUrls, List<String> releaseUrls) {
		private static final YoutubeQueuePreloadDiff EMPTY = new YoutubeQueuePreloadDiff(List.of(), List.of());

		private boolean isEmpty() {
			return this.retainUrls.isEmpty() && this.releaseUrls.isEmpty();
		}
	}

	private record YoutubeMusicQueuePreloadDiff(List<String> retainUrls, List<String> releaseUrls) {
		private static final YoutubeMusicQueuePreloadDiff EMPTY = new YoutubeMusicQueuePreloadDiff(List.of(), List.of());

		private boolean isEmpty() {
			return this.retainUrls.isEmpty() && this.releaseUrls.isEmpty();
		}
	}

	private record GalleryItem(String title, String url, String localMediaKey, MonitorMediaApp.LoadedMedia media, BufferedImage preview, GalleryItemKind kind) {
	}

	private record PersistedGalleryItem(String title, String url, GalleryItemKind kind, String localMediaKey) {
	}

	private record GalleryRemovalResult(GalleryItem removedItem, boolean selectionRetained) {
	}

	private record GalleryCacheCandidate(String url, String localMediaKey, GalleryItemKind kind) {
	}

	private record GalleryCacheReferenceSnapshot(
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryYoutubeUrls,
			Set<String> activeMediaUrls,
			Set<String> activeYoutubeUrls
	) {
	}

	private record PersistedWallpaperState(String url, MediaScaleMode scaleMode) {
	}

	private record GalleryItemLoadResult(
			ScreenRuntimeKey screenKey,
			String title,
			String url,
			String localMediaKey,
			GalleryItemKind kind,
			MonitorMediaApp.LoadedMedia loadedMedia,
			MonitorMediaApp.LoadedVideo loadedVideo,
			boolean openWhenReady,
			int preferredIndex,
			String error
	) {
	}

	private record SavedGalleryMediaPersistResult(
			String url,
			String savedMediaKey,
			String error
	) {
	}

	private record WallpaperLoadResult(
			ScreenRuntimeKey screenKey,
			String url,
			String localMediaKey,
			MonitorMediaApp.LoadedMedia loadedMedia,
			String error
	) {
	}

	private record GalleryCardSnapshot(int index, String title, boolean animated, BufferedImage preview, boolean current, boolean loaded) {
	}

	private static final class OverlayWindowRenderState {
		private volatile BufferedImage image;
		private volatile CompletableFuture<BufferedImage> future;
		private volatile long lastAccessNanos;
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
			MonitorMediaApp.LoadedVideo loadedVideo,
			String error
	) {
	}

	private record YoutubeLoadResult(
			ScreenRuntimeKey screenKey,
			UUID requesterUuid,
			String url,
			ScreenViewMode targetMode,
			PlaybackStreamKind streamKind,
			MonitorYoutubeRelayClient.SessionLoadResponse loadResponse,
			String error
	) {
	}

	private record YoutubeQueueResolveResult(
			ScreenRuntimeKey screenKey,
			UUID requesterUuid,
			ScreenViewMode mode,
			String url,
			MonitorYoutubeRelayClient.QueueResolveResponse queueResponse,
			YoutubeLinkRequestAction action,
			String error
	) {
	}

	private record YoutubeMusicLoadResult(
			ScreenRuntimeKey screenKey,
			UUID requesterUuid,
			String url,
			String title,
			String artist,
			MonitorMediaApp.LoadedVideo loadedVideo,
			int queueIndex,
			String error
	) {
	}

	private record YoutubeSnapshotResult(
			ScreenRuntimeKey screenKey,
			MonitorYoutubeRelayClient.SessionSnapshot snapshot,
			String error
	) {
	}

	private enum PlayerUiIcon {
		SEARCH("/assets/lg2/textures/monitor/ui_icons/search.png"),
		QUEUE("/assets/lg2/textures/monitor/ui_icons/queue.png"),
		DOWNLOAD("/assets/lg2/textures/monitor/ui_icons/download.png"),
		TRASH("/assets/lg2/textures/monitor/ui_icons/trash.png"),
		WALLPAPER("/assets/lg2/textures/monitor/ui_icons/wallpaper.png"),
		CHECK("/assets/lg2/textures/monitor/ui_icons/check.png"),
		FIT("/assets/lg2/textures/monitor/ui_icons/fit.png"),
		FILL("/assets/lg2/textures/monitor/ui_icons/fill.png"),
		STRETCH("/assets/lg2/textures/monitor/ui_icons/stretch.png"),
		CLOSE("/assets/lg2/textures/monitor/ui_icons/close.png"),
		BACK("/assets/lg2/textures/monitor/ui_icons/back.png");

		private final String resourcePath;

		PlayerUiIcon(String resourcePath) {
			this.resourcePath = resourcePath;
		}

		private String resourcePath() {
			return this.resourcePath;
		}
	}

	private record PlayerUiIconTintKey(PlayerUiIcon icon, int argb) {
	}

	public record SpeakerAudioSource(
			String sourceKey,
			String relaySessionId,
			String audioStreamUrl,
			long positionMs,
			long audioSyncToken,
			boolean loading,
			boolean paused,
			boolean liveStream
	) {
	}

	private record MapPacketUpdate(
			MapId mapId,
			byte scale,
			boolean locked,
			int startX,
			int startY,
			int width,
			int height,
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
		private PlaybackStreamKind streamKind;
		private MonitorMediaApp.LoadedMedia loadedMedia;
		private BufferedImage streamFrame;
		private BufferedImage loadingBackdropFrame;
		private String sourceUrl;
		private String relaySessionId;
		private String audioStreamUrl;
		private String mediaTitle;
		private String mediaSubtitle;
		private int frameIndex;
		private long youtubeFrameSequence;
		private long positionMs;
		private long durationMs;
		private long bufferedStartMs;
		private long bufferedEndMs;
		private long audioSyncToken;
		private long version;
		private MediaOverlayMode overlayMode;
		private MediaScaleMode scaleMode;
		private GallerySurfaceMode gallerySurfaceMode;
		private boolean liveStream;
		private boolean audioPlaceholder;
		private boolean userPaused;
		private boolean waitingForLink;
		private boolean loading;
		private boolean galleryDeleteConfirmOpen;
		private String statusText;
		private boolean galleryHydrated;
		private boolean wallpaperHydrated;
		private final List<GalleryItem> galleryItems;
		private final Set<String> galleryLoadingUrls;
		private MonitorMediaApp.LoadedMedia wallpaperMedia;
		private String wallpaperUrl;
		private MediaScaleMode wallpaperScaleMode;
		private int wallpaperFrameIndex;
		private boolean wallpaperLoading;
		private String galleryPendingOpenUrl;
		private int galleryPendingOpenIndex;
		private int galleryIndex;
		private int galleryScroll;
		private boolean downloadInProgress;
		private String downloadTargetUrl;
		private UUID downloadRequesterUuid;
		private long downloadStartedAtMillis;
		private String downloadCompletedUrl;
		private long downloadCompletedUntilMillis;
		private final List<YoutubeQueueItem> youtubeQueue;
		private final Set<String> retainedYoutubePreloadUrls;
		private final Set<String> retainedYoutubeMusicUrls;
		private int youtubeQueueIndex;
		private int youtubeQueueScroll;
		private boolean youtubeQueueOpen;
		private boolean youtubeReturnToGallery;
		private int activeRenderJobs;
		private boolean rerenderRequested;
		private MediaDispatchKey lastDispatchKey;
		private ScheduledFuture<?> playbackFuture;
		private long nextProgressRenderAtMillis;
		private final TaskProgress progress;

		private MediaRuntimeState(ScreenViewMode mode, Runnable progressListener) {
			this.mode = mode;
			this.streamKind = PlaybackStreamKind.NONE;
			this.overlayMode = MediaOverlayMode.CONTROLS;
			this.scaleMode = MediaScaleMode.FIT;
			this.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
			this.liveStream = false;
			this.audioPlaceholder = true;
			this.userPaused = false;
			this.waitingForLink = false;
			this.loading = false;
			this.galleryDeleteConfirmOpen = false;
			this.galleryHydrated = false;
			this.wallpaperHydrated = false;
			this.version = 0L;
			this.statusText = "";
			this.mediaSubtitle = "";
			this.loadingBackdropFrame = null;
			this.galleryItems = new ArrayList<>();
			this.galleryLoadingUrls = new HashSet<>();
			this.wallpaperMedia = null;
			this.wallpaperUrl = null;
			this.wallpaperScaleMode = MediaScaleMode.FIT;
			this.wallpaperFrameIndex = 0;
			this.wallpaperLoading = false;
			this.galleryPendingOpenUrl = null;
			this.galleryPendingOpenIndex = -1;
			this.galleryIndex = -1;
			this.galleryScroll = 0;
			this.downloadInProgress = false;
			this.downloadTargetUrl = null;
			this.downloadRequesterUuid = null;
			this.downloadStartedAtMillis = 0L;
			this.downloadCompletedUrl = null;
			this.downloadCompletedUntilMillis = 0L;
			this.youtubeQueue = new ArrayList<>();
			this.retainedYoutubePreloadUrls = new HashSet<>();
			this.retainedYoutubeMusicUrls = new HashSet<>();
			this.youtubeQueueIndex = -1;
			this.youtubeQueueScroll = 0;
			this.youtubeQueueOpen = false;
			this.youtubeReturnToGallery = false;
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

	private record YoutubeQueueItem(String title, String url) {
	}
}
