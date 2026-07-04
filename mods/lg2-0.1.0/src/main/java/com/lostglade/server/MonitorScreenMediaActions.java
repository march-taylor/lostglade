package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenBackgroundLoader.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenMediaFrameRuntime.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
import static com.lostglade.server.MonitorScreenMediaLoadResults.*;
import static com.lostglade.server.MonitorScreenMediaSessionLifecycle.*;
import static com.lostglade.server.MonitorScreenPlaybackScheduler.*;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.concurrent.TimeUnit;

final class MonitorScreenMediaActions {
	private static final Set<String> YOUTUBE_VIDEO_GALLERY_DOWNLOADS = ConcurrentHashMap.newKeySet();

	private MonitorScreenMediaActions() {
	}

	static void openMediaSession(ServerPlayer player, ScreenRuntimeKey key, ScreenViewMode mode) {
		if (player == null || key == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.computeIfAbsent(key, ignored -> MediaRuntimeState.fresh(mode, "", () -> onMediaProgressChanged(server, key)));
		synchronized (state) {
			if (mode == ScreenViewMode.SBER_DRONES && state.mode != ScreenViewMode.SBER_DRONES) {
				clearTransientPlaybackStateLocked(state, false);
				state.galleryItems.clear();
				state.galleryHydrationLoading = false;
				state.galleryHydrationRequestId++;
				state.galleryHydrated = false;
				state.galleryIndex = -1;
				state.galleryScroll = 0;
				state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
			} else if (mode == ScreenViewMode.GALLERY && state.mode == ScreenViewMode.SBER_DRONES) {
				clearTransientPlaybackStateLocked(state, false);
				state.galleryItems.clear();
				state.galleryHydrationLoading = false;
				state.galleryHydrationRequestId++;
				state.galleryHydrated = false;
				state.galleryIndex = -1;
				state.galleryScroll = 0;
				state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
				state.loading = false;
				state.waitingForLink = false;
				state.statusText = "";
				state.overlayMode = MediaOverlayMode.CONTROLS;
			}
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
		} else if (mode == ScreenViewMode.SBER_DRONES) {
			ensureSberDronesStateHydrated(server, key, state);
		}
	}

	static void beginGalleryDownload(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid, UiLayout layout) {
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
		boolean directAudio;
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
			directAudio = directVideo && usesMusicPlayerLayoutLocked(state);
		}
		requestRuntimeRender(server, key);
		ensureExecutors();
		mediaScheduler.schedule(
				() -> CompletableFuture
						.supplyAsync(() -> {
							try {
								return new SavedGalleryMediaPersistResult(
										url,
										directVideo
												? (directAudio ? MonitorMediaApp.persistSavedGalleryAudio(url, null) : MonitorMediaApp.persistSavedGalleryVideo(url, null))
												: MonitorMediaApp.persistSavedGalleryMedia(url),
										null
								);
							} catch (Exception exception) {
								return new SavedGalleryMediaPersistResult(url, null, sanitizeMediaError(exception.getMessage()));
							}
						}, mediaIoExecutor)
						.thenAccept(result -> server.execute(() -> {
							if (directVideo && directAudio) {
								finishGalleryAudioDownload(server, key, title, url, directVideoPreview, result.savedMediaKey(), result.error(), layout);
							} else if (directVideo) {
								finishGalleryVideoDownload(server, key, title, url, directVideoPreview, result.savedMediaKey(), result.error(), layout);
							} else {
								finishGalleryDownload(server, key, title, url, media, result.savedMediaKey(), result.error(), layout);
							}
						})),
				MEDIA_ACTION_SPINNER_MIN_MILLIS,
				TimeUnit.MILLISECONDS
		);
	}

	static void finishGalleryDownload(
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
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !Objects.equals(state.downloadTargetUrl, url)) {
				return;
			}
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
					"",
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
	}

	static void finishGalleryVideoDownload(
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
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !Objects.equals(state.downloadTargetUrl, url)) {
				return;
			}
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
					"",
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
	}

	static void finishGalleryAudioDownload(
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
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !Objects.equals(state.downloadTargetUrl, url)) {
				return;
			}
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
					state.mediaSubtitle,
					url,
					savedMediaKey,
					null,
					preview,
					GalleryItemKind.AUDIO
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
	}

	static void applyGalleryWallpaper(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid) {
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
			state.wallpaperBackgroundMode = wallpaperBackgroundModeForCurrentSelectionLocked(state);
			state.wallpaperFrameIndex = 0;
			state.wallpaperHydrated = true;
			state.version++;
			shouldAnimate = wallpaperAnimationActiveLocked(state);
		}
		persistGalleryState(server, key, state);
		requestRuntimeRender(server, key);
		if (shouldAnimate) {
			scheduleBackgroundPlaybackIfNeeded(server, key);
		}
	}

	static void applyGalleryPlayerBackground(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid, int galleryIndex) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		String backgroundUrlToLoad = null;
		String backgroundLocalMediaKeyToLoad = null;
		boolean shouldAnimate = false;
		synchronized (state) {
			if (galleryIndex < 0 || galleryIndex >= state.galleryItems.size()) {
				return;
			}
			GalleryItem item = state.galleryItems.get(galleryIndex);
			if (!galleryItemCanBePlayerBackgroundCandidate(item)) {
				return;
			}
			state.playerBackgroundUrl = item.url();
			state.playerBackgroundScaleMode = state.playerBackgroundScaleMode != null ? state.playerBackgroundScaleMode : MediaScaleMode.FILL;
			state.playerBackgroundFrameIndex = 0;
			state.playerBackgroundHydrated = true;
			if (item.media() != null) {
				state.playerBackgroundMedia = item.media();
				shouldAnimate = playerBackgroundAnimationActiveLocked(state);
			} else {
				state.playerBackgroundMedia = null;
				backgroundUrlToLoad = item.url();
				backgroundLocalMediaKeyToLoad = item.localMediaKey();
			}
			state.version++;
		}
		persistGalleryState(server, key, state);
		requestRuntimeRender(server, key);
		if (backgroundUrlToLoad != null && !backgroundUrlToLoad.isBlank()) {
			schedulePlayerBackgroundLoad(server, key, backgroundUrlToLoad, backgroundLocalMediaKeyToLoad);
		}
		if (shouldAnimate) {
			scheduleBackgroundPlaybackIfNeeded(server, key);
		}
	}

	static void beginYoutubeDownload(MinecraftServer server, ScreenRuntimeKey key, UUID requesterUuid) {
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
			if ((state.mode != ScreenViewMode.YOUTUBE && state.mode != ScreenViewMode.YOUTUBE_MUSIC)
					|| state.sourceUrl == null
					|| state.sourceUrl.isBlank()) {
				return;
			}
			if (hasCompleteGalleryItemForUrlLocked(state, state.sourceUrl)) {
				state.version++;
				return;
			}
			if (state.downloadInProgress && Objects.equals(state.downloadTargetUrl, state.sourceUrl)) {
				return;
			}
			markDownloadStartedLocked(state, state.sourceUrl, requesterUuid);
			state.statusText = "";
			state.version++;
			readyNow = isYoutubeVideoDownloadLocked(state) || isYoutubeGalleryDownloadReadyLocked(state);
		}
		requestRuntimeRender(server, key);
		if (readyNow) {
			finishYoutubeDownload(server, key);
			return;
		}
		maybeCompleteYoutubeDownload(server, key);
	}

	static void maybeCompleteYoutubeDownload(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		long delayMillis;
		synchronized (state) {
			if (!state.downloadInProgress || (!isYoutubeVideoDownloadLocked(state) && !isYoutubeGalleryDownloadReadyLocked(state))) {
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

	static void finishYoutubeDownload(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureGalleryStateHydrated(server, key, state);
		String urlToSave;
		boolean youtubeMusicMode;
		boolean youtubeVideoMode;
		synchronized (state) {
			if (!state.downloadInProgress || (!isYoutubeVideoDownloadLocked(state) && !isYoutubeGalleryDownloadReadyLocked(state))) {
				return;
			}
			if (state.sourceUrl == null || state.sourceUrl.isBlank() || !Objects.equals(state.sourceUrl, state.downloadTargetUrl)) {
				return;
			}
			urlToSave = state.sourceUrl;
			youtubeMusicMode = state.mode == ScreenViewMode.YOUTUBE_MUSIC;
			youtubeVideoMode = state.mode == ScreenViewMode.YOUTUBE;
		}
		String localMediaKey = null;
		if (youtubeVideoMode) {
			startYoutubeVideoGalleryDownload(server, key, urlToSave);
			return;
		}
		if (youtubeMusicMode) {
			try {
				localMediaKey = persistCompletedYoutubeMusicAudio(urlToSave);
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to persist YouTube Music audio for {}", urlToSave, exception);
			}
			if (localMediaKey == null || localMediaKey.isBlank()) {
				synchronized (state) {
					if (state.downloadInProgress && Objects.equals(state.downloadTargetUrl, urlToSave)) {
						clearDownloadStateLocked(state);
						state.statusText = "SAVE FAILED";
						state.version++;
					}
				}
				requestRuntimeRender(server, key);
				return;
			}
		}
		String savedUrl = null;
		boolean savedYoutubeMusic = false;
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !isYoutubeGalleryDownloadReadyLocked(state)) {
				return;
			}
			if (state.sourceUrl == null || state.sourceUrl.isBlank()
					|| !Objects.equals(state.sourceUrl, state.downloadTargetUrl)
					|| !Objects.equals(state.sourceUrl, urlToSave)) {
				return;
			}
			saved = saveCurrentYoutubeToGalleryLocked(state, localMediaKey);
			if (saved) {
				savedUrl = state.sourceUrl;
				savedYoutubeMusic = state.mode == ScreenViewMode.YOUTUBE_MUSIC;
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
		if (!savedYoutubeMusic && savedUrl != null && !savedUrl.isBlank()) {
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
	}

	private static String persistCompletedYoutubeMusicAudio(String url) throws IOException {
		Path audioPath = MonitorYoutubeMusicCache.completedAudioFile(url);
		if (audioPath == null || !Files.isRegularFile(audioPath)) {
			throw new IOException("YouTube Music audio cache is missing");
		}
		String stableKeyBase = "youtube-music-" + UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
		String localMediaKey = MonitorMediaApp.persistLocalGalleryFileReplacing(stableKeyBase, audioPath);
		BufferedImage cover = MonitorYoutubeMusicCache.queueEntryPreview(url);
		if (cover == null) {
			try {
				cover = MonitorYoutubeMusicCache.refreshCover(url);
			} catch (IOException exception) {
				Lg2.LOGGER.debug("Failed to refresh YouTube Music cover while saving {}", url, exception);
			}
		}
		if (cover != null) {
			MonitorMediaApp.persistSavedGalleryAudioCover(localMediaKey, cover);
		} else {
			MonitorMediaApp.loadSavedGalleryAudioPreview(localMediaKey, "YouTube Music");
		}
		return localMediaKey;
	}

	private static boolean isYoutubeVideoDownloadLocked(MediaRuntimeState state) {
		return state != null && state.mode == ScreenViewMode.YOUTUBE;
	}

	private static void startYoutubeVideoGalleryDownload(MinecraftServer server, ScreenRuntimeKey key, String url) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		String normalizedUrl = url.trim();
		String flightKey = key + "|" + normalizedUrl;
		if (!YOUTUBE_VIDEO_GALLERY_DOWNLOADS.add(flightKey)) {
			return;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> persistCompletedYoutubeVideo(normalizedUrl), mediaIoExecutor)
				.whenComplete((result, throwable) -> {
					YOUTUBE_VIDEO_GALLERY_DOWNLOADS.remove(flightKey);
					YoutubeVideoGallerySaveResult resolvedResult = result;
					if (resolvedResult == null) {
						String error = throwable != null ? sanitizeMediaError(throwable.getMessage()) : "SAVE FAILED";
						resolvedResult = new YoutubeVideoGallerySaveResult(normalizedUrl, null, null, null, error);
					}
					YoutubeVideoGallerySaveResult finalResult = resolvedResult;
					server.execute(() -> applyYoutubeVideoGallerySaveResult(server, key, finalResult));
				});
	}

	private static YoutubeVideoGallerySaveResult persistCompletedYoutubeVideo(String url) {
		try (MonitorYoutubeRelayClient.DownloadedGalleryVideo video = MonitorYoutubeRelayClient.downloadGalleryVideo(url)) {
			if (video == null || video.path() == null || !Files.isRegularFile(video.path())) {
				return new YoutubeVideoGallerySaveResult(url, null, null, null, "SAVE FAILED");
			}
			String stableKeyBase = "youtube-video-" + UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8));
			String localMediaKey = MonitorMediaApp.persistLocalGalleryFileReplacing(stableKeyBase, video.path());
			MonitorMediaApp.LoadedVideo localVideo = MonitorMediaApp.loadSavedGalleryVideo(localMediaKey, null);
			return new YoutubeVideoGallerySaveResult(url, localMediaKey, video.title(), localVideo.preview(), null);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to persist YouTube video {}", url, exception);
			return new YoutubeVideoGallerySaveResult(url, null, null, null, sanitizeMediaError(exception.getMessage()));
		}
	}

	private static void applyYoutubeVideoGallerySaveResult(MinecraftServer server, ScreenRuntimeKey key, YoutubeVideoGallerySaveResult result) {
		if (server == null || key == null || result == null || result.url() == null || result.url().isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean saved = false;
		synchronized (state) {
			if (!state.downloadInProgress || !Objects.equals(state.downloadTargetUrl, result.url())) {
				return;
			}
			if (result.localMediaKey() == null || result.localMediaKey().isBlank()) {
				clearDownloadStateLocked(state);
				state.statusText = result.error() != null && !result.error().isBlank() ? result.error() : "SAVE FAILED";
				state.version++;
				requestRuntimeRender(server, key);
				return;
			}
			String title = state.mediaTitle != null && !state.mediaTitle.isBlank()
					? state.mediaTitle
					: result.title() != null && !result.title().isBlank() ? result.title() : "YouTube";
			int index = upsertGalleryItemLocked(
					state,
					title,
					state.mediaSubtitle,
					result.url(),
					result.localMediaKey(),
					null,
					result.preview() != null ? copyBufferedImage(result.preview()) : copyBufferedImage(state.streamFrame),
					GalleryItemKind.VIDEO
			);
			saved = index >= 0;
			if (saved) {
				if (Objects.equals(state.sourceUrl, result.url())) {
					state.galleryIndex = index;
					state.gallerySurfaceMode = GallerySurfaceMode.PLAYER;
				}
				markDownloadCompletedLocked(state, result.url());
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
		YoutubeQueuePreloadDiff preloadDiff;
		synchronized (state) {
			preloadDiff = syncYoutubeQueuePreloadsLocked(state);
		}
		applyYoutubeQueuePreloadDiff(preloadDiff);
		scheduleGalleryPreloadStatusRefreshes(server, key);
		requestRuntimeRender(server, key);
		scheduleActionCompletionReset(server, key);
	}

	private record YoutubeVideoGallerySaveResult(String url, String localMediaKey, String title, BufferedImage preview, String error) {
	}

	static void scheduleAudioCoverRefresh(
			MinecraftServer server,
			ScreenRuntimeKey key,
			String url,
			String localMediaKey,
			String title,
			String audioInput
	) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		String normalizedUrl = url.trim();
		String normalizedLocalMediaKey = localMediaKey != null ? localMediaKey.trim() : "";
		String normalizedTitle = title != null ? title : "";
		String normalizedAudioInput = audioInput != null ? audioInput.trim() : "";
		boolean youtubeMusicCover = MonitorYoutubeMusicCache.looksLikeSupportedUrl(normalizedUrl);
		if (!youtubeMusicCover && normalizedLocalMediaKey.isBlank() && normalizedAudioInput.isBlank()) {
			return;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						BufferedImage cover;
						if (youtubeMusicCover) {
							cover = MonitorYoutubeMusicCache.refreshCover(normalizedUrl);
						} else if (!normalizedLocalMediaKey.isBlank()) {
							cover = MonitorMediaApp.loadSavedGalleryAudioCover(normalizedLocalMediaKey, normalizedTitle);
						} else {
							cover = MonitorMediaApp.loadAudioCover(normalizedAudioInput, normalizedTitle);
						}
						if (cover != null && !normalizedLocalMediaKey.isBlank()) {
							MonitorMediaApp.persistSavedGalleryAudioCover(normalizedLocalMediaKey, cover);
						}
						return cover;
					} catch (Exception exception) {
						Lg2.LOGGER.debug("Failed to refresh audio cover for {}", normalizedUrl, exception);
						return null;
					}
				}, mediaIoExecutor)
				.thenAccept(cover -> {
					if (cover != null) {
						server.execute(() -> applyAudioCoverRefresh(server, key, normalizedUrl, cover));
					}
				});
	}

	private static void applyAudioCoverRefresh(MinecraftServer server, ScreenRuntimeKey key, String url, BufferedImage cover) {
		if (server == null || key == null || url == null || url.isBlank() || cover == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean changed = false;
		String relaySessionIdToUpdate = null;
		String relaySourceUrlToUpdate = null;
		BufferedImage relayCoverToUpdate = null;
		synchronized (state) {
			int index = resolveGalleryItemIndex(state, url, state.galleryIndex);
			if (index >= 0 && index < state.galleryItems.size()) {
				GalleryItem item = state.galleryItems.get(index);
				if (item != null && effectiveGalleryItemKind(item) == GalleryItemKind.AUDIO) {
					state.galleryItems.set(
							index,
							new GalleryItem(
									item.title(),
									item.subtitle(),
									item.url(),
									item.localMediaKey(),
									item.media(),
									copyBufferedImage(cover),
									item.kind()
							)
					);
					changed = true;
				}
			}
			if (Objects.equals(state.sourceUrl, url)
					&& (state.streamKind == PlaybackStreamKind.DIRECT_VIDEO
					|| state.mode == ScreenViewMode.YOUTUBE_MUSIC
					|| MonitorYoutubeMusicCache.looksLikeSupportedUrl(url))) {
				state.streamFrame = copyBufferedImage(cover);
				if (state.mode == ScreenViewMode.YOUTUBE_MUSIC) {
					state.loadingBackdropFrame = copyBufferedImage(cover);
				}
				if (state.relaySessionId != null && !state.relaySessionId.isBlank()) {
					relaySessionIdToUpdate = state.relaySessionId;
					relaySourceUrlToUpdate = state.sourceUrl;
					relayCoverToUpdate = copyBufferedImage(cover);
				}
				changed = true;
			}
			if (changed) {
				state.version++;
			}
		}
		if (relaySessionIdToUpdate != null && relayCoverToUpdate != null) {
			MonitorYoutubeRelayClient.updateStaticFrame(relaySessionIdToUpdate, relaySourceUrlToUpdate, relayCoverToUpdate);
		}
		if (changed) {
			requestRuntimeRender(server, key);
		}
	}

	static void startStandaloneYoutubePlayback(
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
		TaskProgress progress;
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
			replaceProgressTrackerLocked(state);
			state.progress.setIndeterminate("LOADING");
			progress = state.progress;
			state.version++;
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

	static void startGalleryYoutubePlayback(
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
		TaskProgress progress;
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
			replaceProgressTrackerLocked(state);
			state.progress.setIndeterminate("LOADING");
			progress = state.progress;
			state.version++;
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
								ScreenViewMode.GALLERY,
								PlaybackStreamKind.YOUTUBE,
								null,
								MonitorYoutubeRelayClient.load(relaySessionId(key), url, progress),
								null
						);
					} catch (Exception exception) {
						return new YoutubeLoadResult(key, requesterUuid, url, ScreenViewMode.GALLERY, PlaybackStreamKind.YOUTUBE, null, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
	}

	static void startDirectVideoPlayback(
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
		TaskProgress progress;
		BufferedImage staticFrame;
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
				syncYoutubeMusicShuffleStateLocked(state, true);
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
			replaceProgressTrackerLocked(state);
			state.progress.setIndeterminate("LOADING");
			progress = state.progress;
			staticFrame = shouldUseStaticVisualForDirectPlaybackLocked(state, targetMode, url) ? video.preview() : null;
			if (preserveQueue && isYoutubeFamilyMode(targetMode)) {
				ensureYoutubeQueueCurrentEntryLocked(state);
			}
			state.version++;
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
								targetMode,
								PlaybackStreamKind.DIRECT_VIDEO,
								subtitle,
								MonitorYoutubeRelayClient.loadDirect(
										relaySessionId(key),
										url,
										title,
										video.playbackInput(),
										video.audioInput(),
										video.durationMs(),
										staticFrame,
										video.width(),
										progress
								),
								null
						);
					} catch (Exception exception) {
						return new YoutubeLoadResult(key, requesterUuid, url, targetMode, PlaybackStreamKind.DIRECT_VIDEO, subtitle, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyYoutubeLoadResult(server, result)));
	}

	static void startDirectVideoPlayback(
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

	static boolean shouldUseStaticVisualForDirectPlaybackLocked(MediaRuntimeState state, ScreenViewMode targetMode, String url) {
		if (targetMode == ScreenViewMode.YOUTUBE_MUSIC) {
			return true;
		}
		GalleryItem current = currentGalleryItemLocked(state);
		GalleryItemKind currentKind = effectiveGalleryItemKind(current);
		if (currentKind == GalleryItemKind.VIDEO) {
			return false;
		}
		return currentKind == GalleryItemKind.AUDIO || MonitorMediaApp.looksLikeDirectAudioUrl(url);
	}

	static void requestMediaLink(ServerPlayer player, ScreenRuntimeKey key, boolean clearCurrentMedia, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
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
			if (mode != ScreenViewMode.GALLERY) {
				clearGalleryPendingOpenLocked(state);
			}
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
		// Opening the shared link prompt must not freeze animated wallpapers/player backgrounds
		// while the user is focused on text input.
		resumeMediaPlaybackIfNeeded(server, key);
	}

	static boolean shouldKeepPlaybackWhilePromptingLocked(MediaRuntimeState state, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
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
}
