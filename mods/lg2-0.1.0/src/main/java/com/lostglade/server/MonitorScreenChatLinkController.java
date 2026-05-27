package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMediaLoadResults.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenMediaFrameRuntime.*;
import static com.lostglade.server.MonitorScreenMediaSessionLifecycle.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenYoutubeQueueRuntime.*;

import com.lostglade.server.monitor.MonitorMediaApp;
import com.lostglade.server.monitor.MonitorYoutubeRelayClient;
import com.lostglade.server.monitor.MonitorYoutubeMusicCache;
import com.lostglade.server.progress.TaskProgress;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

final class MonitorScreenChatLinkController {
	private MonitorScreenChatLinkController() {
	}

	static boolean onAllowChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
		if (sender == null || message == null) {
			return true;
		}
		MinecraftServer server = sender.level().getServer();
		if (server == null) {
			return true;
		}
		if (!MonitorMaxRuntime.onAllowChatMessage(message, sender, params)) {
			return false;
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
				replaceProgressTrackerLocked(state);
				state.progress.setIndeterminate("LOADING");
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
		TaskProgress linkLoadProgress;
		long sessionGeneration;
		synchronized (state) {
			linkLoadProgress = state.progress;
			sessionGeneration = state.sessionGeneration;
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
									sessionGeneration,
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
									sessionGeneration,
									sanitizeMediaError(exception.getMessage())
							);
						}
					}, mediaIoExecutor)
					.thenAccept(result -> server.execute(() -> applyYoutubeQueueResolveResult(server, result)));
		} else {
			CompletableFuture
					.supplyAsync(() -> {
						try {
							if (pending.mode() == ScreenViewMode.GALLERY && MonitorYoutubeMusicCache.looksLikeSupportedUrl(url)) {
								MonitorYoutubeMusicCache.LoadedTrack track = MonitorYoutubeMusicCache.load(url, linkLoadProgress);
								return new MediaLoadResult(
										pending.screenKey(),
										sender.getUUID(),
										url,
										track.title(),
										track.artist(),
										GalleryItemKind.AUDIO,
										null,
										track.video(),
										sessionGeneration,
										null
								);
							}
							if (MonitorMediaApp.looksLikeDirectAudioUrl(url)) {
								MonitorMediaApp.LoadedAudioTrack track = MonitorMediaApp.loadAudioFromUrl(url, linkLoadProgress);
								return new MediaLoadResult(
										pending.screenKey(),
										sender.getUUID(),
										url,
										track.title(),
										track.artist(),
										GalleryItemKind.AUDIO,
										null,
										track.video(),
										sessionGeneration,
										null
								);
							}
							if (MonitorMediaApp.looksLikeDirectVideoUrl(url)) {
								return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, null, "", GalleryItemKind.VIDEO, null, MonitorMediaApp.loadVideoFromUrl(url, linkLoadProgress), sessionGeneration, null);
							}
							return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, null, "", GalleryItemKind.MEDIA, MonitorMediaApp.loadFromUrl(url, linkLoadProgress), null, sessionGeneration, null);
						} catch (Exception exception) {
							return new MediaLoadResult(pending.screenKey(), sender.getUUID(), url, null, "", GalleryItemKind.MEDIA, null, null, sessionGeneration, sanitizeMediaError(exception.getMessage()));
						}
					}, mediaIoExecutor)
					.thenAccept(result -> server.execute(() -> applyMediaLoadResult(server, result)));
		}
		return false;
	}
}
