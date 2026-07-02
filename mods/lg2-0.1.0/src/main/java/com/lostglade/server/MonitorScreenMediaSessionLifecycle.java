package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenBackgroundLoader.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
import static com.lostglade.server.MonitorScreenMediaLoadResults.*;
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

final class MonitorScreenMediaSessionLifecycle {
	private MonitorScreenMediaSessionLifecycle() {
	}

	static void closeMediaSession(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		if (!CLOSING_MEDIA_SESSIONS.add(key)) {
			return;
		}
		RendererBotCameraSystem.stopLiveStream(liveCameraStreamOwnerId(key));
		try {
			MediaRuntimeState removed = MEDIA_STATES.remove(key);
			String relaySessionId = null;
			List<String> releasedQueueUrls = List.of();
			List<String> releasedMusicQueueUrls = List.of();
			if (removed != null) {
				synchronized (removed) {
					cancelRuntimePlaybackLocked(removed);
					clearSessionLiveLoadsLocked(removed);
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
			refreshConnectedSpeakersNow(server, key, false);
		} finally {
			CLOSING_MEDIA_SESSIONS.remove(key);
		}
	}

	static void deactivateMediaSession(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		RendererBotCameraSystem.stopLiveStream(liveCameraStreamOwnerId(key));
		MediaRuntimeState state = MEDIA_STATES.get(key);
		String relaySessionId = null;
		List<String> releasedQueueUrls = List.of();
		List<String> releasedMusicQueueUrls = List.of();
		if (state != null) {
			synchronized (state) {
				cancelRuntimePlaybackLocked(state);
				advanceSessionGenerationLocked(state);
				releasedQueueUrls = retainedYoutubePreloadUrlsLocked(state);
				releasedMusicQueueUrls = retainedYoutubeMusicPreloadUrlsLocked(state);
				state.retainedYoutubePreloadUrls.clear();
				state.retainedYoutubeMusicUrls.clear();
				if (state.relaySessionId != null && !state.relaySessionId.isBlank()) {
					relaySessionId = state.relaySessionId;
				}
				clearTransientPlaybackStateLocked(state, true);
				compactGalleryRuntimeMediaLocked(state);
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
		refreshConnectedSpeakersNow(server, key, false);
	}

	static void resetMediaSessionForPowerOff(
			MinecraftServer server,
			ScreenRuntimeKey key,
			List<PersistedGalleryItem> persistedGallery,
			PersistedWallpaperState persistedWallpaper,
			PersistedPlayerBackgroundState persistedPlayerBackground,
			PlayerBackgroundMode persistedPlayerBackgroundMode
	) {
		if (key == null) {
			return;
		}
		RendererBotCameraSystem.stopLiveStream(liveCameraStreamOwnerId(key));
		MediaRuntimeState state = MEDIA_STATES.get(key);
		String relaySessionId = null;
		List<String> releasedQueueUrls = List.of();
		List<String> releasedMusicQueueUrls = List.of();
		if (state != null) {
			synchronized (state) {
				cancelRuntimePlaybackLocked(state);
				advanceSessionGenerationLocked(state);
				releasedQueueUrls = retainedYoutubePreloadUrlsLocked(state);
				releasedMusicQueueUrls = retainedYoutubeMusicPreloadUrlsLocked(state);
				state.retainedYoutubePreloadUrls.clear();
				state.retainedYoutubeMusicUrls.clear();
				if (state.relaySessionId != null && !state.relaySessionId.isBlank()) {
					relaySessionId = state.relaySessionId;
				}
				clearTransientPlaybackStateLocked(state, true);
				compactGalleryRuntimeMediaLocked(state);
				restorePersistedBackgroundStateAfterPowerOffLocked(state, persistedWallpaper, persistedPlayerBackground);
				state.playerBackgroundMode = persistedPlayerBackgroundMode;
				state.playerBackgroundModeHydrated = persistedPlayerBackgroundMode != null;
				state.playerBackgroundMenuOpen = false;
				state.mode = ScreenViewMode.HOME;
				state.overlayMode = MediaOverlayMode.VIEW;
				state.statusText = "";
				state.loading = false;
				state.waitingForLink = false;
				state.userPaused = false;
				state.galleryItems.clear();
				state.galleryHydrationLoading = false;
				state.galleryHydrationRequestId++;
				state.galleryHydrated = persistedGallery == null || persistedGallery.isEmpty();
				state.galleryIndex = -1;
				state.galleryScroll = 0;
				state.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
				state.activeRenderJobs = 0;
				state.lastDispatchKey = null;
				state.rerenderRequested = false;
				state.version++;
			}
		}
		releaseYoutubeQueuePreloads(releasedQueueUrls);
		releaseYoutubeMusicQueuePreloads(releasedMusicQueueUrls);
		releaseYoutubeRelaySession(relaySessionId);
		clearMediaSessionBindings(server, key);
		refreshConnectedSpeakersNow(server, key, false);
	}

	static void restorePersistedBackgroundStateAfterPowerOffLocked(
			MediaRuntimeState state,
			PersistedWallpaperState persistedWallpaper,
			PersistedPlayerBackgroundState persistedPlayerBackground
	) {
		if (state == null) {
			return;
		}
		MonitorMediaApp.LoadedMedia preservedWallpaperMedia = reusableBackgroundMediaLocked(
				state,
				persistedWallpaper != null ? persistedWallpaper.url() : null
		);
		MonitorMediaApp.LoadedMedia preservedPlayerBackgroundMedia = reusableBackgroundMediaLocked(
				state,
				persistedPlayerBackground != null ? persistedPlayerBackground.url() : null
		);
		if (persistedWallpaper != null
				&& persistedWallpaper.url() != null
				&& !persistedWallpaper.url().isBlank()) {
			state.wallpaperUrl = persistedWallpaper.url();
			state.wallpaperScaleMode = persistedWallpaper.scaleMode() != null ? persistedWallpaper.scaleMode() : MediaScaleMode.FIT;
			state.wallpaperBackgroundMode = safeWallpaperBackgroundMode(persistedWallpaper.backgroundMode());
			state.wallpaperMedia = preservedWallpaperMedia;
			state.wallpaperFrameIndex = 0;
			state.wallpaperLoading = false;
			state.wallpaperHydrated = preservedWallpaperMedia != null;
		} else {
			clearWallpaperLocked(state);
			state.wallpaperHydrated = true;
		}
		if (persistedPlayerBackground != null
				&& persistedPlayerBackground.url() != null
				&& !persistedPlayerBackground.url().isBlank()) {
			state.playerBackgroundUrl = persistedPlayerBackground.url();
			state.playerBackgroundScaleMode = persistedPlayerBackground.scaleMode() != null ? persistedPlayerBackground.scaleMode() : MediaScaleMode.FILL;
			state.playerBackgroundMedia = preservedPlayerBackgroundMedia;
			state.playerBackgroundFrameIndex = 0;
			state.playerBackgroundLoading = false;
			state.playerBackgroundHydrated = preservedPlayerBackgroundMedia != null;
		} else {
			clearPlayerBackgroundLocked(state);
			state.playerBackgroundHydrated = true;
		}
	}

	static MonitorMediaApp.LoadedMedia reusableBackgroundMediaLocked(MediaRuntimeState state, String url) {
		if (state == null || url == null || url.isBlank()) {
			return null;
		}
		if (Objects.equals(state.wallpaperUrl, url) && state.wallpaperMedia != null) {
			return state.wallpaperMedia;
		}
		if (Objects.equals(state.playerBackgroundUrl, url) && state.playerBackgroundMedia != null) {
			return state.playerBackgroundMedia;
		}
		for (GalleryItem item : state.galleryItems) {
			if (item != null && Objects.equals(item.url(), url) && item.media() != null) {
				return item.media();
			}
		}
		return null;
	}

	static void releaseYoutubeRelaySession(String relaySessionId) {
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

	static void clearMediaSessionBindings(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		PENDING_MEDIA_LINKS.entrySet().removeIf(entry -> entry.getValue().screenKey().equals(key));
		PENDING_GALLERY_RENAMES.entrySet().removeIf(entry -> entry.getValue().screenKey().equals(key));
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

	static TaskProgress replaceProgressTrackerLocked(MediaRuntimeState state) {
		if (state == null) {
			return new TaskProgress();
		}
		state.progress = new TaskProgress(state.progressListener);
		return state.progress;
	}

	static void clearSessionLiveLoadsLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.galleryLoadingUrls.clear();
		state.galleryHydrationLoading = false;
		state.galleryHydrationRequestId++;
		state.wallpaperLoading = false;
		state.playerBackgroundLoading = false;
	}

	static long advanceSessionGenerationLocked(MediaRuntimeState state) {
		if (state == null) {
			return 0L;
		}
		clearSessionLiveLoadsLocked(state);
		state.sessionGeneration++;
		if (state.sessionGeneration <= 0L) {
			state.sessionGeneration = 1L;
		}
		replaceProgressTrackerLocked(state);
		return state.sessionGeneration;
	}

	static void cancelPlaybackLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		cancelPlaybackFutureLocked(state);
		clearPlaybackFrameScheduleLocked(state);
	}

	static void cancelRuntimePlaybackLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		cancelPlaybackFutureLocked(state);
		cancelBackgroundFutureLocked(state);
		clearAnimatedFrameScheduleLocked(state);
	}

	static void cancelPlaybackFutureLocked(MediaRuntimeState state) {
		if (state == null || state.playbackFuture == null) {
			return;
		}
		state.playbackFuture.cancel(false);
		state.playbackFuture = null;
	}

	static void cancelBackgroundFutureLocked(MediaRuntimeState state) {
		if (state == null || state.backgroundFuture == null) {
			return;
		}
		state.backgroundFuture.cancel(false);
		state.backgroundFuture = null;
	}

	static void clearPlaybackFrameScheduleLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.nextLoadedMediaFrameAtMillis = 0L;
	}

	static void clearBackgroundFrameScheduleLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.nextWallpaperFrameAtMillis = 0L;
		state.nextPlayerBackgroundFrameAtMillis = 0L;
	}

	static void clearAnimatedFrameScheduleLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		clearPlaybackFrameScheduleLocked(state);
		clearBackgroundFrameScheduleLocked(state);
	}

	static long bumpAudioSyncTokenLocked(MediaRuntimeState state) {
		if (state == null) {
			return 0L;
		}
		state.audioSyncToken++;
		if (state.audioSyncToken == Long.MIN_VALUE) {
			state.audioSyncToken = 1L;
		}
		return state.audioSyncToken;
	}

	static void clearPendingAudioTransportLocked(MediaRuntimeState state) {
		if (state == null) {
			return;
		}
		state.pendingAudioPauseState = null;
		state.pendingAudioPositionActive = false;
		state.pendingAudioPositionMs = 0L;
		state.pendingAudioIssuedAtMillis = 0L;
	}

	static void markPendingAudioPauseLocked(MediaRuntimeState state, boolean paused) {
		if (state == null) {
			return;
		}
		state.userPaused = paused;
		state.pendingAudioPauseState = paused;
		state.pendingAudioIssuedAtMillis = System.currentTimeMillis();
	}

	static void markPendingAudioPositionLocked(MediaRuntimeState state, long positionMs) {
		if (state == null) {
			return;
		}
		long clampedPositionMs = Math.max(0L, positionMs);
		state.positionMs = clampedPositionMs;
		state.pendingAudioPositionMs = clampedPositionMs;
		state.pendingAudioPositionActive = true;
		state.pendingAudioIssuedAtMillis = System.currentTimeMillis();
	}

	static void markStreamSeekBufferingLocked(MediaRuntimeState state) {
		if (state == null || state.streamKind != PlaybackStreamKind.YOUTUBE) {
			return;
		}
		state.loading = true;
		state.statusText = "BUFFERING";
		state.progress.setIndeterminate("LOADING");
		state.version++;
	}

	static void reconcilePendingAudioTransportLocked(MediaRuntimeState state, boolean snapshotPaused, long snapshotPositionMs) {
		if (state == null) {
			return;
		}
		MonitorAudioTransportPolicy.Resolution resolution = MonitorAudioTransportPolicy.reconcile(
				snapshotPaused,
				snapshotPositionMs,
				state.pendingAudioPauseState,
				state.pendingAudioPositionActive,
				state.pendingAudioPositionMs,
				state.pendingAudioIssuedAtMillis,
				System.currentTimeMillis()
		);
		state.userPaused = resolution.paused();
		state.positionMs = resolution.positionMs();
		state.pendingAudioPauseState = resolution.pendingPauseState();
		state.pendingAudioPositionActive = resolution.pendingPositionActive();
		state.pendingAudioPositionMs = resolution.pendingPositionMs();
		state.pendingAudioIssuedAtMillis = resolution.pendingIssuedAtMillis();
	}
}
