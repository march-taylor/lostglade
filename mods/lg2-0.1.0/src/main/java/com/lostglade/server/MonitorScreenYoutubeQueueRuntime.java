package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenBackgroundLoader.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenMediaFrameRuntime.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
import static com.lostglade.server.MonitorScreenMediaLoadResults.*;
import static com.lostglade.server.MonitorScreenMediaSessionLifecycle.*;
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

final class MonitorScreenYoutubeQueueRuntime {
	private MonitorScreenYoutubeQueueRuntime() {
	}

	static void applyYoutubeQueueResolveResult(MinecraftServer server, YoutubeQueueResolveResult result) {
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
			if (result.sessionGeneration() != state.sessionGeneration
					&& !acceptLateResultForCurrentView(server, result.screenKey(), result.mode())) {
				return;
			}
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
					state.youtubeQueue.add(new YoutubeQueueItem(
							title,
							entry.subtitle() == null ? "" : entry.subtitle(),
							Math.max(0L, entry.durationMs()),
							entry.url()
					));
				}
				syncYoutubeMusicShuffleStateLocked(state, true);
				if (result.action() == YoutubeLinkRequestAction.REPLACE_QUEUE) {
					state.youtubeQueueIndex = state.youtubeQueue.isEmpty() ? -1 : 0;
					alignYoutubeQueueScrollToCurrentTopLocked(state, youtubeQueueVisibleRowsPreview(state));
					state.youtubeQueueOpen = !state.youtubeQueue.isEmpty();
					shouldStartPlayback = !state.youtubeQueue.isEmpty();
					startQueueIndex = 0;
				} else if (!state.youtubeQueue.isEmpty() && (state.sourceUrl == null || state.sourceUrl.isBlank())) {
					state.youtubeQueueIndex = Math.max(0, Math.min(appendStartIndex, state.youtubeQueue.size() - 1));
					alignYoutubeQueueScrollToCurrentTopLocked(state, youtubeQueueVisibleRowsPreview(state));
					state.youtubeQueueOpen = true;
					shouldStartPlayback = true;
					startQueueIndex = state.youtubeQueueIndex;
				} else {
					state.youtubeQueueOpen = true;
					alignYoutubeQueueScrollToCurrentTopLocked(state, youtubeQueueVisibleRowsPreview(state));
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
		scheduleYoutubeQueueCacheStatusRefreshes(server, result.screenKey());

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

	static void startYoutubeQueuePlayback(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid, int queueIndex) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		String url;
		TaskProgress progress = null;
		YoutubeQueuePreloadDiff preloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		YoutubeMusicQueuePreloadDiff musicPreloadDiff = YoutubeMusicQueuePreloadDiff.EMPTY;
		synchronized (state) {
			int resolvedIndex = normalizeYoutubeQueueIndexLocked(state, queueIndex);
			if (resolvedIndex < 0) {
				state.loading = false;
				state.statusText = "";
				replaceProgressTrackerLocked(state);
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
				replaceProgressTrackerLocked(state);
				state.progress.setIndeterminate("LOADING");
				progress = state.progress;
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
		TaskProgress queuePlaybackProgress = progress;
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new YoutubeLoadResult(
								key,
								requesterUuid,
								url,
								ScreenViewMode.YOUTUBE,
								PlaybackStreamKind.YOUTUBE,
								null,
								MonitorYoutubeRelayClient.load(relaySessionId(key), url, queuePlaybackProgress),
								null
						);
					} catch (Exception exception) {
						return new YoutubeLoadResult(key, requesterUuid, url, ScreenViewMode.YOUTUBE, PlaybackStreamKind.YOUTUBE, null, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
	}

	static void startYoutubeMusicQueuePlayback(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid, int queueIndex) {
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
		TaskProgress progress = null;
		YoutubeQueuePreloadDiff preloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		YoutubeMusicQueuePreloadDiff musicPreloadDiff = YoutubeMusicQueuePreloadDiff.EMPTY;
		synchronized (state) {
			resolvedIndex = normalizeYoutubeQueueIndexLocked(state, queueIndex);
			if (resolvedIndex < 0) {
				state.loading = false;
				state.statusText = "";
				replaceProgressTrackerLocked(state);
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
				syncYoutubeMusicShuffleStateLocked(state, true);
				state.youtubeQueueOpen = false;
				state.waitingForLink = false;
				state.loading = true;
				state.userPaused = false;
				state.statusText = "BUFFERING";
				replaceProgressTrackerLocked(state);
				state.progress.setIndeterminate("LOADING");
				progress = state.progress;
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
		TaskProgress youtubeMusicProgress = progress;
		CompletableFuture
				.supplyAsync(() -> {
					try {
						MonitorYoutubeMusicCache.LoadedTrack track = MonitorYoutubeMusicCache.load(url, youtubeMusicProgress);
						return new YoutubeMusicLoadResult(
								key,
								requesterUuid,
								url,
								track.title(),
								track.artist(),
								track.video(),
								null,
								resolvedIndex,
								null
						);
					} catch (Exception exception) {
						return new YoutubeMusicLoadResult(key, requesterUuid, url, title, "", null, null, resolvedIndex, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeMusicLoadResult(server, result)));
	}

	static void applyYoutubeMusicLoadResult(MinecraftServer server, YoutubeMusicLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		synchronized (state) {
			if (!youtubeMusicLoadResultStillCurrentLocked(state, result)) {
				return;
			}
		}
		if (result.relayLoadResponse() != null) {
			applyYoutubeLoadResult(
					server,
					new YoutubeLoadResult(
							result.screenKey(),
							result.requesterUuid(),
							result.url(),
							ScreenViewMode.YOUTUBE_MUSIC,
							PlaybackStreamKind.YOUTUBE,
							result.artist(),
							result.relayLoadResponse(),
							null
					)
			);
			return;
		}
		if (result.loadedVideo() == null) {
			synchronized (state) {
				state.loading = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
				state.version++;
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
		scheduleAudioCoverRefresh(server, result.screenKey(), result.url(), "", result.title(), result.loadedVideo().audioInput());
	}

	static boolean youtubeMusicLoadResultStillCurrentLocked(MediaRuntimeState state, YoutubeMusicLoadResult result) {
		if (state == null || result == null) {
			return false;
		}
		return MonitorMediaSessionPolicy.youtubeMusicLoadResultStillCurrent(
				state.mode == ScreenViewMode.YOUTUBE_MUSIC,
				state.sourceUrl,
				result.url(),
				state.youtubeQueueIndex,
				result.queueIndex()
		);
	}
}
