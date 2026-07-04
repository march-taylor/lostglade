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

final class MonitorScreenPlaybackScheduler {
	private MonitorScreenPlaybackScheduler() {
	}

	static boolean wallpaperVisibleForCurrentViewLocked(MediaRuntimeState state) {
		if (state == null || state.wallpaperMedia == null || state.wallpaperUrl == null || state.wallpaperUrl.isBlank()) {
			return false;
		}
		return wallpaperVisibleForViewMode(state.mode, state);
	}

	static boolean playerBackgroundVisibleForCurrentViewLocked(MediaRuntimeState state) {
		if (state == null || state.playerBackgroundMedia == null || state.playerBackgroundUrl == null || state.playerBackgroundUrl.isBlank()) {
			return false;
		}
		return playerBackgroundVisibleForViewMode(state.mode, state);
	}

	static boolean wallpaperVisibleForViewMode(ScreenViewMode viewMode, MediaRuntimeState state) {
		if (state == null || state.wallpaperMedia == null || state.wallpaperUrl == null || state.wallpaperUrl.isBlank()) {
			return false;
		}
		if (viewMode == null || viewMode == ScreenViewMode.HOME) {
			return true;
		}
		if (!isPlayerMode(viewMode)) {
			return true;
		}
		if (isYoutubeMenuSurfaceLocked(viewMode, state)) {
			return true;
		}
		if ((state.waitingForLink || state.loading)
				&& state.streamFrame == null
				&& state.loadingBackdropFrame == null) {
			return true;
		}
		if (isLibraryAppMode(viewMode)) {
			return state.gallerySurfaceMode == GallerySurfaceMode.BROWSER;
		}
		return state.sourceUrl == null
				&& state.relaySessionId == null
				&& !state.loading;
	}

	static boolean playerBackgroundVisibleForViewMode(ScreenViewMode viewMode, MediaRuntimeState state) {
		if (state == null || state.playerBackgroundMedia == null || state.playerBackgroundUrl == null || state.playerBackgroundUrl.isBlank()) {
			return false;
		}
		if (viewMode == null || !isPlayerMode(viewMode)) {
			return false;
		}
		if (resolvedPlayerBackgroundModeLocked(state) != PlayerBackgroundMode.GALLERY) {
			return false;
		}
		if (isYoutubeMenuSurfaceLocked(viewMode, state)) {
			return false;
		}
		if (isLibraryAppMode(viewMode)) {
			return state.gallerySurfaceMode != GallerySurfaceMode.BROWSER;
		}
		return true;
	}

	static boolean shouldPreserveWallpaperPlaybackOnTransitionLocked(MediaRuntimeState state, ScreenViewMode nextMode) {
		return state != null
				&& (wallpaperAnimationActiveLocked(state)
				|| playerBackgroundAnimationActiveLocked(state)
				|| (wallpaperVisibleForCurrentViewLocked(state) && wallpaperVisibleForViewMode(nextMode, state))
				|| (playerBackgroundVisibleForCurrentViewLocked(state) && playerBackgroundVisibleForViewMode(nextMode, state)));
	}

	static boolean wallpaperAnimationActiveLocked(MediaRuntimeState state) {
		return state != null
				&& state.wallpaperMedia != null
				&& state.wallpaperUrl != null
				&& !state.wallpaperUrl.isBlank()
				&& MonitorBackgroundPlaybackPolicy.animatedMediaActive(true, state.wallpaperMedia.animated(), state.wallpaperMedia.frameCount());
	}

	static boolean playerBackgroundAnimationActiveLocked(MediaRuntimeState state) {
		return state != null
				&& state.playerBackgroundMedia != null
				&& state.playerBackgroundUrl != null
				&& !state.playerBackgroundUrl.isBlank()
				&& resolvedPlayerBackgroundModeLocked(state) == PlayerBackgroundMode.GALLERY
				&& MonitorBackgroundPlaybackPolicy.animatedMediaActive(true, state.playerBackgroundMedia.animated(), state.playerBackgroundMedia.frameCount());
	}

	static boolean loadedMediaAnimationActiveLocked(MediaRuntimeState state) {
		return state != null
				&& state.loadedMedia != null
				&& state.loadedMedia.animated()
				&& state.loadedMedia.frameCount() > 1
				&& !state.waitingForLink
				&& !state.loading
				&& !isPlaybackPausedLocked(state);
	}

	static long sanitizedAnimationDelayMillis(int delayMillis) {
		return MonitorBackgroundPlaybackPolicy.sanitizedDelayMillis(delayMillis);
	}

	static long nextAnimationDeadlineMillis(long currentDeadlineMillis, long nowMillis, int delayMillis) {
		return MonitorBackgroundPlaybackPolicy.nextFrameDeadlineMillis(currentDeadlineMillis, nowMillis, delayMillis);
	}

	static long earliestPositiveDeadlineMillis(long... deadlines) {
		return MonitorBackgroundPlaybackPolicy.earliestPositiveDeadlineMillis(deadlines);
	}

	static void restartMediaPlaybackIfNeeded(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			cancelPlaybackLocked(state);
		}
		resumeMediaPlaybackIfNeeded(server, key);
	}

	static void scheduleProgressFadeRenders(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		ensureExecutors();
		long stepMillis = Math.max(1L, TaskProgress.COMPLETION_VISIBLE_MILLIS / PROGRESS_FADE_RENDER_STEPS);
		for (int index = 1; index <= PROGRESS_FADE_RENDER_STEPS; index++) {
			long delayMillis = stepMillis * index;
			mediaScheduler.schedule(() -> server.execute(() -> requestRuntimeRender(server, key)), delayMillis, TimeUnit.MILLISECONDS);
		}
	}

	static ScheduledFuture<?> scheduleWallpaperVisibilityRecheck(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return null;
		}
		return mediaScheduler.schedule(() -> server.execute(() -> refreshBackgroundPlayback(server, key)), WALLPAPER_IDLE_VISIBILITY_RECHECK_MS, TimeUnit.MILLISECONDS);
	}

	static void scheduleBackgroundPlaybackIfNeeded(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		ensureExecutors();
		synchronized (state) {
			scheduleBackgroundPlaybackLocked(server, key, state);
		}
	}

	static void scheduleBackgroundPlaybackLocked(MinecraftServer server, ScreenRuntimeKey key, MediaRuntimeState state) {
		if (server == null || key == null || state == null || state.backgroundFuture != null) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean wallpaperActive = wallpaperAnimationActiveLocked(state);
		boolean playerBackgroundActive = playerBackgroundAnimationActiveLocked(state);
		if (!wallpaperActive) {
			state.nextWallpaperFrameAtMillis = 0L;
		}
		if (!playerBackgroundActive) {
			state.nextPlayerBackgroundFrameAtMillis = 0L;
		}
		if (!wallpaperActive && !playerBackgroundActive) {
			return;
		}
		if (!hasNearbyMediaViewer(server, key)) {
			clearBackgroundFrameScheduleLocked(state);
			state.backgroundFuture = scheduleWallpaperVisibilityRecheck(server, key);
			return;
		}
		if (wallpaperActive) {
			state.nextWallpaperFrameAtMillis = nextAnimationDeadlineMillis(
					state.nextWallpaperFrameAtMillis,
					now,
					state.wallpaperMedia.delayMillis(state.wallpaperFrameIndex)
			);
		}
		if (playerBackgroundActive) {
			state.nextPlayerBackgroundFrameAtMillis = nextAnimationDeadlineMillis(
					state.nextPlayerBackgroundFrameAtMillis,
					now,
					state.playerBackgroundMedia.delayMillis(state.playerBackgroundFrameIndex)
			);
		}
		long nextDeadlineMillis = earliestPositiveDeadlineMillis(
				state.nextWallpaperFrameAtMillis,
				state.nextPlayerBackgroundFrameAtMillis
		);
		if (nextDeadlineMillis <= 0L) {
			return;
		}
		long delayMillis = Math.max(1L, nextDeadlineMillis - now);
		state.backgroundFuture = mediaScheduler.schedule(() -> server.execute(() -> advanceBackgroundFrame(server, key)), delayMillis, TimeUnit.MILLISECONDS);
	}

	static void scheduleNextMediaFrame(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		scheduleBackgroundPlaybackIfNeeded(server, key);
		synchronized (state) {
			cancelPlaybackFutureLocked(state);
			if (hasActiveStreamPlaybackLocked(state)) {
				if (state.waitingForLink) {
					return;
				}
				if (state.streamKind == PlaybackStreamKind.LIVE_CAMERA) {
					clearPlaybackFrameScheduleLocked(state);
					long delayMillis = state.streamFrame == null ? 1L : LIVE_CAMERA_HEALTH_CHECK_INTERVAL_MS;
					state.playbackFuture = mediaScheduler.schedule(() -> server.execute(() -> refreshLiveCameraStreamHealth(server, key)), delayMillis, TimeUnit.MILLISECONDS);
					return;
				}
				if (state.relaySessionId == null) {
					if (!state.loading) {
						return;
					}
					long delayMillis = Math.max(1L, youtubePollActiveIntervalMs());
					state.playbackFuture = mediaScheduler.schedule(() -> server.execute(() -> refreshLoadingUi(server, key)), delayMillis, TimeUnit.MILLISECONDS);
					return;
				}
				long delayMillis = Math.max(1L, effectiveYoutubePollDelayMs(server, key, isPlaybackPausedLocked(state)));
				state.playbackFuture = mediaScheduler.schedule(() -> server.execute(() -> refreshYoutubeSnapshot(server, key)), delayMillis, TimeUnit.MILLISECONDS);
				return;
			}

			if (state.loading && !state.waitingForLink) {
				long delayMillis = Math.max(1L, youtubePollActiveIntervalMs());
				state.playbackFuture = mediaScheduler.schedule(() -> server.execute(() -> refreshLoadingUi(server, key)), delayMillis, TimeUnit.MILLISECONDS);
				return;
			}

			long now = System.currentTimeMillis();
			boolean mediaActive = loadedMediaAnimationActiveLocked(state);
			boolean slideshowActive = gallerySlideshowPlaybackActiveLocked(state);
			if (!mediaActive) {
				state.nextLoadedMediaFrameAtMillis = 0L;
			}
			if (!slideshowActive) {
				state.gallerySlideshowAdvanceAtMillis = 0L;
			}
			if (!mediaActive && !slideshowActive) {
				return;
			}
			if (mediaActive) {
				state.nextLoadedMediaFrameAtMillis = nextAnimationDeadlineMillis(
						state.nextLoadedMediaFrameAtMillis,
						now,
						state.loadedMedia.delayMillis(state.frameIndex)
				);
			}
			if (slideshowActive) {
				state.gallerySlideshowAdvanceAtMillis = nextGallerySlideshowDeadlineMillisLocked(state, now);
			}
			long nextDeadlineMillis = earliestPositiveDeadlineMillis(
					state.nextLoadedMediaFrameAtMillis,
					state.gallerySlideshowAdvanceAtMillis
			);
			if (nextDeadlineMillis <= 0L) {
				return;
			}
			long delayMillis = Math.max(1L, nextDeadlineMillis - now);
			state.playbackFuture = mediaScheduler.schedule(() -> server.execute(() -> advanceMediaFrame(server, key)), delayMillis, TimeUnit.MILLISECONDS);
		}
	}

	static void refreshLoadingUi(MinecraftServer server, ScreenRuntimeKey key) {
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

	static void refreshBackgroundPlayback(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		boolean activeAnimatedBackground;
		synchronized (state) {
			state.backgroundFuture = null;
			activeAnimatedBackground = wallpaperAnimationActiveLocked(state) || playerBackgroundAnimationActiveLocked(state);
		}
		if (!activeAnimatedBackground) {
			return;
		}
		if (hasNearbyMediaViewer(server, key)) {
			requestRuntimeRender(server, key);
		}
		scheduleBackgroundPlaybackIfNeeded(server, key);
	}
}
