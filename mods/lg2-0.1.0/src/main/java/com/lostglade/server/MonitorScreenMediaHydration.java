package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenBackgroundLoader.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenTickScheduler.*;

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

final class MonitorScreenMediaHydration {
	private MonitorScreenMediaHydration() {
	}

	static void ensureGalleryStateHydrated(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		List<PersistedGalleryItem> persistedItems = List.of();
		YoutubeQueuePreloadDiff preloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		boolean shouldRender = false;
		boolean alreadyHydrated = false;
		synchronized (state) {
			if (state.galleryHydrated) {
				alreadyHydrated = true;
				if (hasLiveCameraItemsLocked(state)) {
					persistedItems = resolvePersistedGalleryState(resolveScreenComponent(server, key));
					state.galleryItems.clear();
					state.galleryItems.addAll(displayGalleryItemsFromPersisted(persistedItems));
					state.galleryIndex = resolveGalleryItemIndex(state, state.sourceUrl, state.galleryIndex);
					state.galleryScroll = 0;
					if (state.galleryIndex < 0) {
						clearGallerySelectionLocked(state);
						state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
					}
					if (state.galleryItems.isEmpty()) {
						state.galleryIndex = -1;
						state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
					}
					preloadDiff = syncYoutubeQueuePreloadsLocked(state);
					state.version++;
					shouldRender = true;
				}
			} else if (!state.galleryItems.isEmpty() && !hasLiveCameraItemsLocked(state)) {
				state.galleryHydrated = true;
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
			} else {
				ScreenComponent component = resolveScreenComponent(server, key);
				persistedItems = resolvePersistedGalleryState(component);
				state.galleryHydrated = true;
				if (!persistedItems.isEmpty()) {
					state.galleryItems.clear();
					state.galleryItems.addAll(displayGalleryItemsFromPersisted(persistedItems));
					state.galleryIndex = -1;
					state.galleryScroll = 0;
					state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
					state.version++;
					shouldRender = true;
				}
				preloadDiff = syncYoutubeQueuePreloadsLocked(state);
			}
		}
		if (alreadyHydrated && !shouldRender && persistedItems.isEmpty()) {
			return;
		}
		applyYoutubeQueuePreloadDiff(preloadDiff);
		scheduleGalleryPreloadStatusRefreshes(server, key);
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
	}

	static void ensureSberDronesStateHydrated(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component == null) {
			return;
		}
		boolean initialSyncNeeded;
		boolean shouldRender = false;
		synchronized (state) {
			boolean hadForeignGalleryItems = hasNonLiveCameraItemsLocked(state);
			initialSyncNeeded = !state.galleryHydrated || hadForeignGalleryItems;
			if (hadForeignGalleryItems) {
				state.galleryItems.removeIf(item -> !isLiveCameraGalleryItem(item));
				state.galleryIndex = resolveGalleryItemIndex(state, state.sourceUrl, state.galleryIndex);
				if (state.galleryIndex < 0 && state.streamKind != PlaybackStreamKind.LIVE_CAMERA) {
					clearGallerySelectionLocked(state);
				}
				state.galleryScroll = 0;
				state.version++;
				shouldRender = true;
			}
			state.galleryHydrated = true;
			if (state.streamKind != PlaybackStreamKind.LIVE_CAMERA) {
				state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
			}
			if (state.playerBackgroundGalleryPickerOpen) {
				clearPlayerBackgroundGalleryPickerLocked(state);
				state.version++;
				shouldRender = true;
			}
		}
		ServerLevel level = server.getLevel(key.dimension());
		if (initialSyncNeeded && level != null) {
			enqueueCameraRefresh(level, key);
		}
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
	}


	static void ensureWallpaperStateHydrated(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		String wallpaperUrl = null;
		String wallpaperLocalMediaKey = null;
		boolean shouldRender = false;
		synchronized (state) {
			if (state.wallpaperHydrated) {
				if (shouldRetryWallpaperLoadLocked(state)) {
					wallpaperUrl = state.wallpaperUrl;
					wallpaperLocalMediaKey = currentGalleryItemMatchingUrlLocked(state, wallpaperUrl)
							.map(GalleryItem::localMediaKey)
							.orElse(null);
				}
			} else {
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
				state.wallpaperBackgroundMode = safeWallpaperBackgroundMode(persisted.backgroundMode());
				state.wallpaperMedia = currentGalleryItemMatchingUrlLocked(state, state.wallpaperUrl)
						.map(GalleryItem::media)
						.orElse(null);
				wallpaperLocalMediaKey = persistedWallpaperItem != null ? persistedWallpaperItem.localMediaKey() : null;
				wallpaperUrl = state.wallpaperMedia == null ? state.wallpaperUrl : null;
				shouldRender = state.wallpaperMedia != null;
				state.version++;
			}
		}
		if (wallpaperUrl != null) {
			scheduleWallpaperLoad(server, key, wallpaperUrl, wallpaperLocalMediaKey);
		}
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
	}

	static boolean shouldRetryWallpaperLoadLocked(MediaRuntimeState state) {
		return state != null
				&& state.wallpaperHydrated
				&& state.wallpaperUrl != null
				&& !state.wallpaperUrl.isBlank()
				&& state.wallpaperMedia == null
				&& !state.wallpaperLoading;
	}

	static void ensurePlayerBackgroundModeHydrated(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		synchronized (state) {
			if (state.playerBackgroundModeHydrated) {
				return;
			}
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		PlayerBackgroundMode persisted = component != null ? resolvePersistedPlayerBackgroundMode(component) : null;
		synchronized (state) {
			if (state.playerBackgroundModeHydrated) {
				return;
			}
			state.playerBackgroundMode = persisted;
			state.playerBackgroundModeHydrated = true;
		}
	}

	static void ensurePlayerBackgroundStateHydrated(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		String playerBackgroundUrl = null;
		String playerBackgroundLocalMediaKey = null;
		boolean shouldRender = false;
		synchronized (state) {
			if (state.playerBackgroundHydrated) {
				if (shouldRetryPlayerBackgroundLoadLocked(state)) {
					playerBackgroundUrl = state.playerBackgroundUrl;
					playerBackgroundLocalMediaKey = currentGalleryItemMatchingUrlLocked(state, playerBackgroundUrl)
							.map(GalleryItem::localMediaKey)
							.orElse(null);
				}
			} else {
				PersistedPlayerBackgroundState persisted = null;
				PersistedGalleryItem persistedBackgroundItem = null;
				ScreenComponent component = resolveScreenComponent(server, key);
				if (component != null) {
					persisted = resolvePersistedPlayerBackgroundState(component);
					if (persisted != null && persisted.url() != null && !persisted.url().isBlank()) {
						for (PersistedGalleryItem galleryItem : resolvePersistedGalleryState(component)) {
							if (galleryItem != null && Objects.equals(galleryItem.url(), persisted.url())) {
								persistedBackgroundItem = galleryItem;
								break;
							}
						}
					}
				}
				state.playerBackgroundHydrated = true;
				state.playerBackgroundFrameIndex = 0;
				if (persisted == null || persisted.url() == null || persisted.url().isBlank()) {
					clearPlayerBackgroundLocked(state);
					return;
				}
				state.playerBackgroundUrl = persisted.url();
				state.playerBackgroundScaleMode = persisted.scaleMode() != null ? persisted.scaleMode() : MediaScaleMode.FILL;
				state.playerBackgroundMedia = currentGalleryItemMatchingUrlLocked(state, state.playerBackgroundUrl)
						.map(GalleryItem::media)
						.orElse(null);
				playerBackgroundLocalMediaKey = persistedBackgroundItem != null ? persistedBackgroundItem.localMediaKey() : null;
				playerBackgroundUrl = state.playerBackgroundMedia == null ? state.playerBackgroundUrl : null;
				shouldRender = state.playerBackgroundMedia != null;
				state.version++;
			}
		}
		if (playerBackgroundUrl != null) {
			schedulePlayerBackgroundLoad(server, key, playerBackgroundUrl, playerBackgroundLocalMediaKey);
		}
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
	}

	static boolean shouldRetryPlayerBackgroundLoadLocked(MediaRuntimeState state) {
		return state != null
				&& state.playerBackgroundHydrated
				&& state.playerBackgroundUrl != null
				&& !state.playerBackgroundUrl.isBlank()
				&& state.playerBackgroundMedia == null
				&& !state.playerBackgroundLoading;
	}

	static void scheduleGalleryPreloadStatusRefreshes(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			if (state.galleryPreloadStatusRefreshScheduled
					|| state.mode != ScreenViewMode.GALLERY
					|| state.gallerySurfaceMode != GallerySurfaceMode.BROWSER
					|| state.galleryItems.isEmpty()) {
				return;
			}
			state.galleryPreloadStatusRefreshScheduled = true;
			state.galleryPreloadStatusRefreshStep = 0;
		}
		ensureExecutors();
		scheduleNextGalleryPreloadStatusRefresh(server, key);
	}

	static void scheduleNextGalleryPreloadStatusRefresh(MinecraftServer server, ScreenRuntimeKey key) {
		mediaScheduler.schedule(() -> server.execute(() -> refreshGalleryPreloadStatus(server, key)), 1250L, TimeUnit.MILLISECONDS);
	}

	static void refreshGalleryPreloadStatus(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean shouldRender = false;
		boolean shouldContinue = false;
		synchronized (state) {
			if (!state.galleryPreloadStatusRefreshScheduled) {
				return;
			}
			boolean active = state.mode == ScreenViewMode.GALLERY
					&& state.gallerySurfaceMode == GallerySurfaceMode.BROWSER
					&& !state.galleryItems.isEmpty();
			if (!active) {
				state.galleryPreloadStatusRefreshScheduled = false;
				state.galleryPreloadStatusRefreshStep = 0;
				return;
			}
			state.galleryPreloadStatusRefreshStep++;
			shouldRender = true;
			shouldContinue = state.galleryPreloadStatusRefreshStep < 24;
			if (!shouldContinue) {
				state.galleryPreloadStatusRefreshScheduled = false;
				state.galleryPreloadStatusRefreshStep = 0;
			}
		}
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
		if (shouldContinue) {
			scheduleNextGalleryPreloadStatusRefresh(server, key);
		}
	}

	static void scheduleYoutubeQueueCacheStatusRefreshes(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			if (state.youtubeQueueCacheStatusRefreshScheduled
					|| !isYoutubeFamilyMode(state.mode)
					|| !state.youtubeQueueOpen
					|| state.youtubeQueue.isEmpty()) {
				return;
			}
			state.youtubeQueueCacheStatusRefreshScheduled = true;
		}
		ensureExecutors();
		mediaScheduler.schedule(() -> server.execute(() -> refreshYoutubeQueueCacheStatus(server, key)), 250L, TimeUnit.MILLISECONDS);
	}

	static void refreshYoutubeQueueCacheStatus(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean shouldRender = false;
		boolean shouldContinue = false;
		synchronized (state) {
			shouldContinue = youtubeQueueCacheStatusActiveLocked(state);
			if (shouldContinue) {
				state.version++;
				shouldRender = true;
			} else {
				state.youtubeQueueCacheStatusRefreshScheduled = false;
			}
		}
		if (shouldRender) {
			requestRuntimeRender(server, key);
		}
		if (shouldContinue) {
			mediaScheduler.schedule(() -> server.execute(() -> refreshYoutubeQueueCacheStatus(server, key)), 250L, TimeUnit.MILLISECONDS);
		}
	}

	static boolean youtubeQueueCacheStatusActiveLocked(MediaRuntimeState state) {
		if (state == null || !isYoutubeFamilyMode(state.mode) || !state.youtubeQueueOpen || state.youtubeQueue.isEmpty()) {
			return false;
		}
		boolean youtubeMusicQueue = isYoutubeMusicMode(state.mode);
		for (YoutubeQueueItem item : state.youtubeQueue) {
			String url = item != null ? item.url() : "";
			if (url == null || url.isBlank()) {
				continue;
			}
			boolean retained = youtubeMusicQueue
					? state.retainedYoutubeMusicUrls.contains(url)
					: state.retainedYoutubePreloadUrls.contains(url);
			if (youtubeMusicQueue) {
				MonitorYoutubeMusicCache.QueueEntryCacheStatus status = MonitorYoutubeMusicCache.queueEntryCacheStatus(url);
				if (!status.complete() && status.active()) {
					return true;
				}
			} else {
				MonitorYoutubeRelayClient.QueueEntryCacheStatus status = MonitorYoutubeRelayClient.queueEntryCacheStatus(url);
				if (!status.complete() && (status.active() || retained)) {
					return true;
				}
			}
		}
		return false;
	}
}
