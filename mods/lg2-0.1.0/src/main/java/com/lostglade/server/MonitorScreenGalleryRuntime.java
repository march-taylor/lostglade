package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
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

final class MonitorScreenGalleryRuntime {
	private MonitorScreenGalleryRuntime() {
	}

	static void scheduleGalleryItemLoad(MinecraftServer server, ScreenRuntimeKey key, String title, String url, String localMediaKey, GalleryItemKind kind, boolean openWhenReady, int preferredIndex) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		TaskProgress progress = null;
		long sessionGeneration;
		synchronized (state) {
			if (state.galleryLoadingUrls.contains(url)) {
				if (openWhenReady) {
					beginGalleryPendingOpenLocked(state, url, preferredIndex);
					state.loading = true;
					state.waitingForLink = false;
					state.overlayMode = MediaOverlayMode.CONTROLS;
					state.statusText = "BUFFERING";
					replaceProgressTrackerLocked(state);
					state.progress.setIndeterminate("LOADING");
					state.version++;
				}
				return;
			}
			state.galleryLoadingUrls.add(url);
			sessionGeneration = state.sessionGeneration;
			if (openWhenReady) {
				beginGalleryPendingOpenLocked(state, url, preferredIndex);
				state.loading = true;
				state.waitingForLink = false;
				state.overlayMode = MediaOverlayMode.CONTROLS;
				state.statusText = "BUFFERING";
				replaceProgressTrackerLocked(state);
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
						if (resolvedKind == GalleryItemKind.AUDIO) {
							if (MonitorYoutubeMusicCache.looksLikeSupportedUrl(url) && (localMediaKey == null || localMediaKey.isBlank())) {
								MonitorYoutubeMusicCache.LoadedTrack track = MonitorYoutubeMusicCache.load(url, finalProgress);
								return new GalleryItemLoadResult(
										key,
										track.title(),
										track.artist(),
										url,
										localMediaKey,
										resolvedKind,
										null,
										track.video(),
										openWhenReady,
										preferredIndex,
										sessionGeneration,
										null
								);
							}
							MonitorMediaApp.LoadedAudioTrack track = localMediaKey != null && !localMediaKey.isBlank()
									? MonitorMediaApp.loadSavedGalleryAudio(localMediaKey, finalProgress)
									: MonitorMediaApp.loadAudioFromUrl(url, finalProgress);
							return new GalleryItemLoadResult(
									key,
									track.title(),
									track.artist(),
									url,
									localMediaKey,
									resolvedKind,
									null,
									track.video(),
									openWhenReady,
									preferredIndex,
									sessionGeneration,
									null
							);
						}
						return new GalleryItemLoadResult(
								key,
								title,
								"",
								url,
								localMediaKey,
								resolvedKind,
								resolvedKind == GalleryItemKind.VIDEO
										? null
										: loadGalleryMedia(url, localMediaKey, finalProgress),
								resolvedKind == GalleryItemKind.VIDEO
										? loadGalleryVideo(url, localMediaKey, finalProgress)
										: null,
								openWhenReady,
								preferredIndex,
								sessionGeneration,
								null
						);
					} catch (Exception exception) {
						return new GalleryItemLoadResult(key, title, "", url, localMediaKey, kind != null ? kind : GalleryItemKind.MEDIA, null, null, openWhenReady, preferredIndex, sessionGeneration, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyGalleryItemLoadResult(server, result)));
	}

	static MonitorMediaApp.LoadedMedia loadGalleryMedia(String url, String localMediaKey, TaskProgress progress) throws IOException {
		if (isCameraGalleryVideoUrl(url)) {
			return loadCameraGalleryVideoMedia(url, localMediaKey, progress);
		}
		return localMediaKey != null && !localMediaKey.isBlank()
				? MonitorMediaApp.loadSavedGalleryMedia(localMediaKey, progress)
				: MonitorMediaApp.loadFromUrl(url, progress);
	}

	static MonitorMediaApp.LoadedVideo loadGalleryVideo(String url, String localMediaKey, TaskProgress progress) throws IOException {
		if (localMediaKey != null && !localMediaKey.isBlank()) {
			return MonitorMediaApp.loadSavedGalleryVideo(localMediaKey, progress);
		}
		String sourceKey = cameraGallerySourceKey(url, "video");
		if (!sourceKey.isBlank()) {
			return MonitorMediaApp.loadLocalVideo(CameraMediaCache.videoSourcePath(sourceKey), progress);
		}
		return MonitorMediaApp.loadVideoFromUrl(url, progress);
	}

	static MonitorMediaApp.LoadedMedia loadCameraGalleryVideoMedia(String url, String localMediaKey, TaskProgress progress) throws IOException {
		IOException primaryException = null;
		if (localMediaKey != null && !localMediaKey.isBlank()) {
			try {
				return MonitorMediaApp.loadSavedGalleryVideoAsMedia(localMediaKey, progress);
			} catch (IOException exception) {
				primaryException = exception;
			}
		}
		String sourceKey = cameraGallerySourceKey(url, "video");
		if (!sourceKey.isBlank()) {
			try {
				return MonitorMediaApp.loadLocalVideoAsMedia(CameraMediaCache.videoSourcePath(sourceKey), progress);
			} catch (IOException exception) {
				if (primaryException != null) {
					primaryException.addSuppressed(exception);
					throw primaryException;
				}
				throw exception;
			}
		}
		if (primaryException != null) {
			throw primaryException;
		}
		throw new IOException("Camera video source is missing");
	}

	static void applyGalleryItemLoadResult(MinecraftServer server, GalleryItemLoadResult result) {
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
		boolean shouldPersistLocalAudio = false;
		MonitorMediaApp.LoadedVideo loadedVideoToOpen = null;
		String loadedVideoTitle = null;
		String loadedVideoSubtitle = null;
		int loadedVideoIndex = -1;
		synchronized (state) {
			state.galleryLoadingUrls.remove(result.url());
			if (result.sessionGeneration() != state.sessionGeneration
					&& !acceptLateResultWhileGalleryStillActive(server, result.screenKey())) {
				return;
			}
			boolean pendingOpenMatches = Objects.equals(state.galleryPendingOpenUrl, result.url())
					&& state.galleryPendingOpenIndex >= 0
					&& state.galleryPendingOpenRequestId > 0;
			boolean galleryUiActive = state.mode == ScreenViewMode.GALLERY;
			boolean openWhenReady = MonitorMediaSessionPolicy.galleryOpenRequestShouldApply(
					galleryUiActive,
					state.galleryPendingOpenUrl,
					state.galleryPendingOpenIndex,
					state.galleryPendingOpenRequestId,
					result.url()
			);
			int preferredIndex = pendingOpenMatches ? state.galleryPendingOpenIndex : result.preferredIndex();
			if (pendingOpenMatches) {
				clearGalleryPendingOpenLocked(state);
			}
			int targetIndex = resolveGalleryItemIndex(state, result.url(), preferredIndex);
			if (result.loadedMedia() != null && targetIndex >= 0 && targetIndex < state.galleryItems.size()) {
				GalleryItem existing = state.galleryItems.get(targetIndex);
				state.galleryItems.set(
						targetIndex,
						new GalleryItem(
								(existing != null && existing.title() != null && !existing.title().isBlank()) ? existing.title() : result.title(),
								existing != null && existing.subtitle() != null ? existing.subtitle() : "",
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
				if (Objects.equals(state.playerBackgroundUrl, result.url())) {
					state.playerBackgroundMedia = result.loadedMedia();
					state.playerBackgroundFrameIndex = 0;
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
						: (result.title() == null || result.title().isBlank() ? "Audio" : result.title());
				String resolvedSubtitle = result.subtitle() != null && !result.subtitle().isBlank()
						? result.subtitle()
						: existing != null && existing.subtitle() != null ? existing.subtitle() : "";
				state.galleryItems.set(
						targetIndex,
						new GalleryItem(
								resolvedTitle,
								resolvedSubtitle,
								result.url(),
								result.localMediaKey() != null && !result.localMediaKey().isBlank() ? result.localMediaKey() : existing != null ? existing.localMediaKey() : null,
								null,
								result.loadedVideo().preview() != null ? result.loadedVideo().preview() : (existing != null ? existing.preview() : null),
								result.kind()
						)
				);
				if (openWhenReady) {
					loadedVideoToOpen = result.loadedVideo();
					loadedVideoTitle = resolvedTitle;
					loadedVideoSubtitle = resolvedSubtitle;
					loadedVideoIndex = targetIndex;
				} else if (galleryUiActive && state.galleryLoadingUrls.isEmpty()) {
					state.loading = false;
					state.statusText = "";
					state.progress.complete("READY");
				}
				state.version++;
				shouldRender = true;
				shouldPersistLocalVideo = result.kind() == GalleryItemKind.VIDEO && (result.localMediaKey() == null || result.localMediaKey().isBlank());
				shouldPersistLocalAudio = result.kind() == GalleryItemKind.AUDIO
						&& (result.localMediaKey() == null || result.localMediaKey().isBlank())
						&& !MonitorYoutubeMusicCache.looksLikeSupportedUrl(result.url());
			} else if (openWhenReady) {
				state.loading = false;
				state.statusText = sanitizeMediaError(result.error());
				state.progress.clear();
				state.version++;
				shouldRender = true;
			}
			compactGalleryRuntimeMediaLocked(state);
		}
		if (shouldRender) {
			requestRuntimeRender(server, result.screenKey());
		}
		if (shouldAnimate) {
			scheduleNextMediaFrame(server, result.screenKey());
		}
		if (loadedVideoToOpen != null) {
			startDirectVideoPlayback(server, result.screenKey(), null, loadedVideoTitle, loadedVideoSubtitle, result.url(), loadedVideoToOpen, loadedVideoIndex, ScreenViewMode.GALLERY, false);
		}
		if (shouldPersistLocalMedia) {
			scheduleGalleryLocalMediaPersistence(server, result.screenKey(), result.url());
		}
		if (shouldPersistLocalVideo) {
			scheduleGalleryLocalVideoPersistence(server, result.screenKey(), result.url());
		}
		if (shouldPersistLocalAudio) {
			scheduleGalleryLocalAudioPersistence(server, result.screenKey(), result.url());
		}
	}

	static void scheduleGalleryLocalMediaPersistence(MinecraftServer server, ScreenRuntimeKey key, String url) {
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

	static void applyGalleryLocalMediaPersistence(MinecraftServer server, ScreenRuntimeKey key, SavedGalleryMediaPersistResult result) {
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
					new GalleryItem(item.title(), item.subtitle(), item.url(), result.savedMediaKey(), item.media(), item.preview(), item.kind())
			);
			changed = true;
		}
		if (changed) {
			persistGalleryState(server, key, state);
		}
	}

	static void scheduleGalleryLocalVideoPersistence(MinecraftServer server, ScreenRuntimeKey key, String url) {
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

	static void applyGalleryLocalVideoPersistence(MinecraftServer server, ScreenRuntimeKey key, SavedGalleryMediaPersistResult result) {
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
					new GalleryItem(item.title(), item.subtitle(), item.url(), result.savedMediaKey(), item.media(), item.preview(), GalleryItemKind.VIDEO)
			);
			changed = true;
		}
		if (changed) {
			persistGalleryState(server, key, state);
		}
	}

	static void scheduleGalleryLocalAudioPersistence(MinecraftServer server, ScreenRuntimeKey key, String url) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new SavedGalleryMediaPersistResult(url, MonitorMediaApp.persistSavedGalleryAudio(url, null), null);
					} catch (Exception exception) {
						return new SavedGalleryMediaPersistResult(url, null, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyGalleryLocalAudioPersistence(server, key, result)));
	}

	static void applyGalleryLocalAudioPersistence(MinecraftServer server, ScreenRuntimeKey key, SavedGalleryMediaPersistResult result) {
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
					|| effectiveGalleryItemKind(item) != GalleryItemKind.AUDIO
					|| item.localMediaKey() != null && !item.localMediaKey().isBlank()) {
				return;
			}
			state.galleryItems.set(
					index,
					new GalleryItem(item.title(), item.subtitle(), item.url(), result.savedMediaKey(), item.media(), item.preview(), GalleryItemKind.AUDIO)
			);
			changed = true;
		}
		if (changed) {
			persistGalleryState(server, key, state);
		}
	}

	static int resolveGalleryItemIndex(MediaRuntimeState state, String url, int preferredIndex) {
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

	static boolean looksLikeDirectVideoReference(String value) {
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

	static boolean looksLikeDirectAudioReference(String value) {
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
		return GALLERY_AUDIO_EXTENSIONS.contains(extension);
	}

	static GalleryItemKind effectiveGalleryItemKind(String url, String localMediaKey, GalleryItemKind kind) {
		if (kind == GalleryItemKind.LIVE_CAMERA || (url != null && url.startsWith(LIVE_CAMERA_GALLERY_URL_PREFIX))) {
			return GalleryItemKind.LIVE_CAMERA;
		}
		if (isCameraGalleryVideoUrl(url)) {
			return GalleryItemKind.VIDEO;
		}
		if (kind == GalleryItemKind.AUDIO || looksLikeDirectAudioReference(localMediaKey) || looksLikeDirectAudioReference(url)) {
			return GalleryItemKind.AUDIO;
		}
		if (looksLikeDirectVideoReference(localMediaKey) || looksLikeDirectVideoReference(url)) {
			return GalleryItemKind.VIDEO;
		}
		if (kind == GalleryItemKind.YOUTUBE || MonitorYoutubeRelayClient.looksLikeYoutubeUrl(url)) {
			return GalleryItemKind.YOUTUBE;
		}
		return kind == GalleryItemKind.VIDEO ? GalleryItemKind.VIDEO : GalleryItemKind.MEDIA;
	}

	static GalleryItemKind effectiveGalleryItemKind(GalleryItem item) {
		if (item == null) {
			return GalleryItemKind.MEDIA;
		}
		return effectiveGalleryItemKind(item.url(), item.localMediaKey(), item.kind());
	}

	static GalleryItemKind effectiveGalleryItemKind(PersistedGalleryItem item) {
		if (item == null) {
			return GalleryItemKind.MEDIA;
		}
		return effectiveGalleryItemKind(item.url(), item.localMediaKey(), item.kind());
	}

	static GalleryCacheCandidate galleryCacheCandidate(GalleryItem item) {
		if (item == null) {
			return null;
		}
		if (effectiveGalleryItemKind(item) == GalleryItemKind.LIVE_CAMERA) {
			return null;
		}
		String url = item.url() != null ? item.url().trim() : "";
		String localMediaKey = item.localMediaKey() != null ? item.localMediaKey().trim() : "";
		if (url.isBlank() && localMediaKey.isBlank()) {
			return null;
		}
		return new GalleryCacheCandidate(url, localMediaKey, effectiveGalleryItemKind(item));
	}

	static List<GalleryCacheCandidate> galleryCacheCandidatesForRemovedComponent(ScreenComponent component, MediaRuntimeState runtimeState) {
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

	static GalleryCacheCandidate galleryCacheCandidate(PersistedGalleryItem item) {
		if (item == null) {
			return null;
		}
		if (effectiveGalleryItemKind(item) == GalleryItemKind.LIVE_CAMERA) {
			return null;
		}
		String url = item.url() != null ? item.url().trim() : "";
		String localMediaKey = item.localMediaKey() != null ? item.localMediaKey().trim() : "";
		if (url.isBlank() && localMediaKey.isBlank()) {
			return null;
		}
		return new GalleryCacheCandidate(url, localMediaKey, effectiveGalleryItemKind(item));
	}

	static void scheduleGalleryCacheRelease(MinecraftServer server, List<GalleryCacheCandidate> candidates, ScreenRuntimeKey excludedRuntimeKey) {
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

	static GalleryCacheReferenceSnapshot collectGalleryCacheReferences(MinecraftServer server, ScreenRuntimeKey excludedRuntimeKey) {
		Set<String> localMediaKeys = new HashSet<>();
		Set<String> galleryMediaUrls = new HashSet<>();
		Set<String> galleryMusicUrls = new HashSet<>();
		Set<String> galleryYoutubeUrls = new HashSet<>();
		Set<String> activeMediaUrls = new HashSet<>();
		Set<String> activeMusicUrls = new HashSet<>();
		Set<String> activeYoutubeUrls = new HashSet<>();
		for (Map.Entry<ScreenRuntimeKey, MediaRuntimeState> entry : List.copyOf(MEDIA_STATES.entrySet())) {
			if (entry == null || entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			if (excludedRuntimeKey != null && excludedRuntimeKey.equals(entry.getKey())) {
				continue;
			}
			synchronized (entry.getValue()) {
				collectGalleryCacheReferencesLocked(entry.getValue(), localMediaKeys, galleryMediaUrls, galleryMusicUrls, galleryYoutubeUrls, activeMediaUrls, activeMusicUrls, activeYoutubeUrls);
			}
		}
		for (MonitorLevelState levelState : LEVEL_STATES.values()) {
			if (levelState == null) {
				continue;
			}
			ServerLevel level = server.getLevel(levelState.dimension());
			for (ScreenComponent component : List.copyOf(levelState.components().values())) {
				if (component == null || (excludedRuntimeKey != null && excludedRuntimeKey.equals(component.runtimeKey()))) {
					continue;
				}
				for (ItemFrame frame : component.frameCoords().keySet()) {
					List<PersistedGalleryItem> persistedItems = readPersistedGalleryState(frame.getItem());
					if (persistedItems.isEmpty()) {
						continue;
					}
					collectPersistedGalleryCacheReferences(persistedItems, localMediaKeys, galleryMediaUrls, galleryMusicUrls, galleryYoutubeUrls);
					break;
				}
			}
			if (level == null) {
				continue;
			}
			for (ScreenKey frameKey : List.copyOf(levelState.knownFrames())) {
				if (frameKey == null) {
					continue;
				}
				ItemFrame frame = findScreenFrame(level, frameKey.pos(), frameKey.direction());
				if (frame == null) {
					continue;
				}
				collectPersistedGalleryCacheReferences(readPersistedGalleryState(frame.getItem()), localMediaKeys, galleryMediaUrls, galleryMusicUrls, galleryYoutubeUrls);
			}
		}
		return new GalleryCacheReferenceSnapshot(
				Set.copyOf(localMediaKeys),
				Set.copyOf(galleryMediaUrls),
				Set.copyOf(galleryMusicUrls),
				Set.copyOf(galleryYoutubeUrls),
				Set.copyOf(activeMediaUrls),
				Set.copyOf(activeMusicUrls),
				Set.copyOf(activeYoutubeUrls)
		);
	}

	static void collectGalleryCacheReferencesLocked(
			MediaRuntimeState state,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryMusicUrls,
			Set<String> galleryYoutubeUrls,
			Set<String> activeMediaUrls,
			Set<String> activeMusicUrls,
			Set<String> activeYoutubeUrls
	) {
		if (state == null) {
			return;
		}
		for (GalleryItem item : state.galleryItems) {
			collectGalleryCacheReference(item, localMediaKeys, galleryMediaUrls, galleryMusicUrls, galleryYoutubeUrls);
		}
		collectActiveMediaUrl(state.sourceUrl, activeMediaUrls, activeMusicUrls, activeYoutubeUrls);
		collectActiveMediaUrl(state.downloadTargetUrl, activeMediaUrls, activeMusicUrls, activeYoutubeUrls);
		for (String url : state.galleryLoadingUrls) {
			collectActiveMediaUrl(url, activeMediaUrls, activeMusicUrls, activeYoutubeUrls);
		}
		for (YoutubeQueueItem item : state.youtubeQueue) {
			if (item == null) {
				continue;
			}
			collectActiveMediaUrl(item.url(), activeMediaUrls, activeMusicUrls, activeYoutubeUrls);
		}
		for (String url : state.retainedYoutubePreloadUrls) {
			collectActiveMediaUrl(url, activeMediaUrls, activeMusicUrls, activeYoutubeUrls);
		}
		for (String url : state.retainedYoutubeMusicUrls) {
			collectActiveMediaUrl(url, activeMediaUrls, activeMusicUrls, activeYoutubeUrls);
		}
	}

	static void collectPersistedGalleryCacheReferences(
			List<PersistedGalleryItem> persistedItems,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryMusicUrls,
			Set<String> galleryYoutubeUrls
	) {
		if (persistedItems == null || persistedItems.isEmpty()) {
			return;
		}
		for (PersistedGalleryItem item : persistedItems) {
			collectGalleryCacheReference(item, localMediaKeys, galleryMediaUrls, galleryMusicUrls, galleryYoutubeUrls);
		}
	}

	static void collectGalleryCacheReference(
			GalleryItem item,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryMusicUrls,
			Set<String> galleryYoutubeUrls
	) {
		if (item == null) {
			return;
		}
		collectGalleryCacheReference(item.url(), item.localMediaKey(), item.kind(), localMediaKeys, galleryMediaUrls, galleryMusicUrls, galleryYoutubeUrls);
	}

	static void collectGalleryCacheReference(
			PersistedGalleryItem item,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryMusicUrls,
			Set<String> galleryYoutubeUrls
	) {
		if (item == null) {
			return;
		}
		collectGalleryCacheReference(item.url(), item.localMediaKey(), item.kind(), localMediaKeys, galleryMediaUrls, galleryMusicUrls, galleryYoutubeUrls);
	}

	static void collectGalleryCacheReference(
			String url,
			String localMediaKey,
			GalleryItemKind kind,
			Set<String> localMediaKeys,
			Set<String> galleryMediaUrls,
			Set<String> galleryMusicUrls,
			Set<String> galleryYoutubeUrls
	) {
		String normalizedUrl = url != null ? url.trim() : "";
		String normalizedLocalMediaKey = localMediaKey != null ? localMediaKey.trim() : "";
		GalleryItemKind resolvedKind = effectiveGalleryItemKind(normalizedUrl, normalizedLocalMediaKey, kind);
		if (resolvedKind == GalleryItemKind.AUDIO && MonitorYoutubeMusicCache.looksLikeSupportedUrl(normalizedUrl)) {
			if (!normalizedUrl.isBlank()) {
				galleryMusicUrls.add(normalizedUrl);
			}
			return;
		}
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

	static void collectActiveMediaUrl(String url, Set<String> activeMediaUrls, Set<String> activeMusicUrls, Set<String> activeYoutubeUrls) {
		if (url == null || url.isBlank()) {
			return;
		}
		String normalizedUrl = url.trim();
		if (MonitorYoutubeMusicCache.looksLikeSupportedUrl(normalizedUrl)) {
			activeMusicUrls.add(normalizedUrl);
		}
		if (MonitorYoutubeRelayClient.looksLikeYoutubeUrl(normalizedUrl)) {
			activeYoutubeUrls.add(normalizedUrl);
		} else {
			activeMediaUrls.add(normalizedUrl);
		}
	}

	static void applyGalleryCacheRelease(List<GalleryCacheCandidate> candidates, GalleryCacheReferenceSnapshot refs) {
		if (candidates == null || candidates.isEmpty() || refs == null) {
			return;
		}
		Set<String> deletedLocalMediaKeys = new HashSet<>();
		Set<String> deletedMediaUrls = new HashSet<>();
		Set<String> deletedMusicUrls = new HashSet<>();
		Set<String> deletedYoutubeUrls = new HashSet<>();
		for (GalleryCacheCandidate candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			String url = candidate.url() != null ? candidate.url().trim() : "";
			String localMediaKey = candidate.localMediaKey() != null ? candidate.localMediaKey().trim() : "";
			if (candidate.kind() == GalleryItemKind.AUDIO && MonitorYoutubeMusicCache.looksLikeSupportedUrl(url)) {
				if (url.isBlank()
						|| refs.galleryMusicUrls().contains(url)
						|| refs.activeMusicUrls().contains(url)
						|| !deletedMusicUrls.add(url)) {
					continue;
				}
				MonitorYoutubeMusicCache.deletePersistentTrack(url);
				continue;
			}
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

	static void persistGalleryState(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component == null) {
			return;
		}
		List<GalleryItem> galleryItems;
		PersistedWallpaperState wallpaperState;
		PersistedPlayerBackgroundState playerBackgroundState;
		PlayerBackgroundMode playerBackgroundMode;
		synchronized (state) {
			galleryItems = List.copyOf(state.galleryItems);
			wallpaperState = persistedWallpaperStateLocked(state);
			playerBackgroundState = persistedPlayerBackgroundStateLocked(state);
			playerBackgroundMode = persistedPlayerBackgroundModeLocked(state);
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			ItemStack stack = frame.getItem();
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			ItemStack updated = stack.copy();
			writePersistedGalleryState(updated, galleryItems, wallpaperState, playerBackgroundState, playerBackgroundMode);
			frame.setItem(updated, false);
		}
	}
}
