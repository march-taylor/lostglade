package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenBackgroundLoader.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
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

final class MonitorScreenMediaLoadResults {
	private MonitorScreenMediaLoadResults() {
	}

	static void onMediaProgressChanged(MinecraftServer server, ScreenRuntimeKey key) {
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

	static void refreshMediaRequestActionbars(MinecraftServer server) {
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

	static void clearMediaActionbar(MinecraftServer server, UUID playerId) {
		if (server == null || playerId == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) {
			player.displayClientMessage(Component.empty(), true);
		}
	}

	static void applyMediaLoadResult(MinecraftServer server, MediaLoadResult result) {
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
		String directVideoSubtitle = null;

		synchronized (state) {
			if (result.sessionGeneration() != state.sessionGeneration
					&& !acceptLateResultWhileGalleryStillActive(server, result.screenKey())) {
				return;
			}
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
				schedulePlayback = (animated && result.loadedMedia().frameCount() > 1) || gallerySlideshowPlaybackActiveLocked(state);
			} else if (directVideo != null) {
				directVideoTitle = result.title() != null && !result.title().isBlank()
						? result.title()
						: galleryItemTitle(result.url(), null, state.galleryItems.size() + 1);
				directVideoSubtitle = result.subtitle() != null ? result.subtitle() : "";
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
		}
		requestRuntimeRender(server, result.screenKey());
		if (directVideo != null) {
			startDirectVideoPlayback(server, result.screenKey(), result.requesterUuid(), directVideoTitle, directVideoSubtitle, result.url(), directVideo, -1, ScreenViewMode.GALLERY, false);
			if (result.kind() == GalleryItemKind.AUDIO) {
				scheduleAudioCoverRefresh(server, result.screenKey(), result.url(), "", directVideoTitle, directVideo.audioInput());
			}
			return;
		}
		if (result.loadedMedia() != null) {
			scheduleProgressFadeRenders(server, result.screenKey());
		}
		if (schedulePlayback) {
			scheduleNextMediaFrame(server, result.screenKey());
		}
	}

	static void applyYoutubeLoadResult(MinecraftServer server, YoutubeLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		ServerPlayer requester = server.getPlayerList().getPlayer(result.requesterUuid());
		boolean shouldFadeProgress = false;
		boolean staleResult = false;
		String staleSessionId = null;
		Boolean pendingLoadedPauseAction = null;
		String pendingLoadedPauseSessionId = null;

		synchronized (state) {
			if (!youtubeLoadResultStillCurrentLocked(state, result)) {
				staleResult = true;
				staleSessionId = result.loadResponse() != null ? result.loadResponse().sessionId() : null;
			} else {
				Boolean pendingPauseState = state.pendingAudioPauseState;
				boolean pendingPositionActive = state.pendingAudioPositionActive;
				long pendingPositionMs = state.pendingAudioPositionMs;
				long pendingIssuedAtMillis = state.pendingAudioIssuedAtMillis;
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
					state.mediaSubtitle = result.subtitle() == null ? "" : result.subtitle();
					state.durationMs = result.loadResponse().durationMs();
					state.positionMs = result.loadResponse().initialPositionMs();
					state.bufferedStartMs = result.loadResponse().bufferedStartMs();
					state.bufferedEndMs = result.loadResponse().bufferedEndMs();
					if (result.loadResponse().initialFrame() != null) {
						state.streamFrame = result.loadResponse().initialFrame();
						state.youtubeFrameSequence = result.loadResponse().initialFrameSequence();
					}
					state.liveStream = result.loadResponse().live();
					state.audioPlaceholder = result.streamKind() != PlaybackStreamKind.DIRECT_VIDEO;
					state.loading = !result.loadResponse().ready();
					state.userPaused = false;
					if (pendingPauseState != null || pendingPositionActive) {
						state.pendingAudioPauseState = pendingPauseState;
						state.pendingAudioPositionActive = pendingPositionActive;
						state.pendingAudioPositionMs = pendingPositionMs;
						state.pendingAudioIssuedAtMillis = pendingIssuedAtMillis;
						reconcilePendingAudioTransportLocked(state, false, state.positionMs);
						if (state.pendingAudioPauseState != null && state.relaySessionId != null && !state.relaySessionId.isBlank()) {
							pendingLoadedPauseAction = state.pendingAudioPauseState;
							pendingLoadedPauseSessionId = state.relaySessionId;
						}
					}
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
		}
		if (staleResult) {
			if (result.requesterUuid() != null) {
				IN_FLIGHT_MEDIA_LINKS.remove(result.requesterUuid());
			}
			if (requester != null) {
				ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
				requester.displayClientMessage(Component.empty(), true);
			}
			releaseYoutubeRelaySession(staleSessionId);
			return;
		}

		if (result.requesterUuid() != null) {
			IN_FLIGHT_MEDIA_LINKS.remove(result.requesterUuid());
		}
		if (requester != null) {
			ACTIVE_MEDIA_ACTIONBARS.remove(requester.getUUID());
			requester.displayClientMessage(Component.empty(), true);
		}
		if (pendingLoadedPauseAction != null && pendingLoadedPauseSessionId != null && !pendingLoadedPauseSessionId.isBlank()) {
			boolean shouldPause = pendingLoadedPauseAction;
			String sessionId = pendingLoadedPauseSessionId;
			refreshConnectedSpeakersNow(server, result.screenKey());
			ensureExecutors();
			CompletableFuture.runAsync(() -> {
				try {
					if (shouldPause) {
						MonitorYoutubeRelayClient.pause(sessionId);
					} else {
						MonitorYoutubeRelayClient.resume(sessionId);
					}
				} catch (Exception exception) {
					Lg2.LOGGER.debug("Failed to apply pending {} to loaded session {}", shouldPause ? "pause" : "resume", sessionId, exception);
				}
			}, mediaIoExecutor).thenRun(() -> server.execute(() -> {
				refreshConnectedSpeakersNow(server, result.screenKey());
				scheduleYoutubeRefresh(server, result.screenKey(), 0L);
			}));
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

	static boolean youtubeLoadResultStillCurrentLocked(MediaRuntimeState state, YoutubeLoadResult result) {
		if (state == null || result == null) {
			return false;
		}
		return MonitorMediaSessionPolicy.youtubeLoadResultStillCurrent(
				result.targetMode() == ScreenViewMode.GALLERY,
				state.mode == ScreenViewMode.GALLERY,
				state.gallerySurfaceMode == GallerySurfaceMode.PLAYER,
				state.mode == result.targetMode(),
				state.sourceUrl,
				result.url()
		);
	}
}
