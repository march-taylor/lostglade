package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenMediaFrameRuntime.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
import static com.lostglade.server.MonitorScreenMediaLoadResults.*;
import static com.lostglade.server.MonitorScreenMediaSessionLifecycle.*;
import static com.lostglade.server.MonitorScreenPlaybackScheduler.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenTickScheduler.*;
import static com.lostglade.server.MonitorScreenYoutubeQueueRuntime.*;

import com.lostglade.Lg2;
import com.lostglade.server.monitor.MonitorApp;
import com.lostglade.server.monitor.MonitorMediaApp;
import com.lostglade.server.monitor.MonitorYoutubeRelayClient;
import com.lostglade.server.monitor.MonitorYoutubeMusicCache;
import com.lostglade.server.progress.TaskProgress;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.EntityHitResult;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

final class MonitorScreenInputController {
	private MonitorScreenInputController() {
	}

	static InteractionResult handleTouch(ServerPlayer player, ServerLevel level, ItemFrame frame, EntityHitResult hitResult) {
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
		if (MonitorMaxRuntime.handleGlobalTouch(player, level, component, layout, touchPoint)) {
			return InteractionResult.SUCCESS;
		}
		MinecraftServer server = level.getServer();
		ScreenViewMode nextMode = null;
		Integer nextLauncherPage = null;
		boolean rerenderCurrent = false;
		boolean galleryLoadRequest = false;
		boolean galleryPhotoPrintImportRequested = false;
		boolean persistGallery = false;
		Integer galleryDeferredLoadIndex = null;
		Boolean youtubePauseAction = null;
		Long youtubeSeekTargetMs = null;
		Integer youtubeQueuePlayIndex = null;
		boolean galleryDownloadRequested = false;
		boolean galleryWallpaperRequested = false;
		Integer galleryPlayerBackgroundRequestedIndex = null;
		boolean galleryBackgroundPickerOpened = false;
		boolean restartPlayback = false;
		boolean youtubeDownloadRequested = false;
		LiveCameraReference droneControlRequested = null;
		LiveCameraReference galleryDisconnectRequested = null;
		boolean returnToGalleryAfterDelete = false;
		String releasedRelaySessionId = null;
		List<GalleryCacheCandidate> deletedGalleryCacheCandidates = List.of();
		String galleryYoutubeUrl = null;
		String galleryYoutubeTitle = null;
		Integer galleryYoutubeIndex = null;
		List<String> youtubeQueueReleasedUrls = List.of();
		YoutubeQueuePreloadDiff youtubeQueuePreloadDiff = YoutubeQueuePreloadDiff.EMPTY;
		YoutubeMusicQueuePreloadDiff youtubeMusicQueuePreloadDiff = YoutubeMusicQueuePreloadDiff.EMPTY;
		boolean youtubeQueueStatusRefreshRequested = false;
		boolean restoreLiveCameraView = false;
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
			} else if (component.viewMode() == ScreenViewMode.SBER_DRONES) {
				ensureSberDronesStateHydrated(level.getServer(), component.runtimeKey(), mediaState);
			}
			ensurePlayerBackgroundModeHydrated(level.getServer(), component.runtimeKey(), mediaState);
			ensurePlayerBackgroundStateHydrated(level.getServer(), component.runtimeKey(), mediaState);
			MediaOverlayMode overlayMode;
			boolean youtubeShouldAppendQueue;
			boolean galleryBrowser;
			boolean galleryDeleteConfirmOpen;
			boolean playerBackgroundMenuOpen;
			boolean playerUiVisible;
			boolean controlsWereHidden = false;
			synchronized (mediaState) {
				overlayMode = mediaState.overlayMode;
				youtubeShouldAppendQueue = shouldAppendYoutubeRequestLocked(mediaState);
				galleryBrowser = isLibraryAppMode(mediaState.mode) && mediaState.gallerySurfaceMode == GallerySurfaceMode.BROWSER;
				galleryDeleteConfirmOpen = mediaState.galleryDeleteConfirmOpen;
				playerBackgroundMenuOpen = mediaState.playerBackgroundMenuOpen;
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
						GalleryCacheCandidate deletedGalleryCacheCandidate = galleryCacheCandidate(deletedItem);
						deletedGalleryCacheCandidates = deletedGalleryCacheCandidate != null ? List.of(deletedGalleryCacheCandidate) : List.of();
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
			} else if (playerBackgroundMenuOpen) {
				synchronized (mediaState) {
					if (!playerBackgroundPanelRect(layout).contains(touchPoint.x(), touchPoint.y())
							|| playerBackgroundCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.playerBackgroundMenuOpen = false;
						mediaState.version++;
					} else {
						PlayerBackgroundMode currentBackgroundMode = resolvedPlayerBackgroundModeLocked(mediaState);
						if (playerBackgroundScaleButtonContains(layout, currentBackgroundMode, touchPoint)) {
							if (currentBackgroundMode == PlayerBackgroundMode.ARTWORK) {
								mediaState.scaleMode = mediaState.scaleMode != null ? mediaState.scaleMode.next() : MediaScaleMode.FILL;
							} else if (currentBackgroundMode == PlayerBackgroundMode.GALLERY) {
								mediaState.playerBackgroundScaleMode = mediaState.playerBackgroundScaleMode != null
										? mediaState.playerBackgroundScaleMode.next()
										: MediaScaleMode.FILL;
								persistGallery = true;
								restartPlayback = true;
							}
							mediaState.version++;
						} else {
							PlayerBackgroundMode selectedMode = playerBackgroundModeForTouch(layout, touchPoint);
							if (selectedMode != null) {
								mediaState.playerBackgroundMode = selectedMode;
								mediaState.playerBackgroundModeHydrated = true;
								mediaState.playerBackgroundMenuOpen = false;
								if (selectedMode == PlayerBackgroundMode.GALLERY) {
									beginPlayerBackgroundGalleryPickerLocked(mediaState);
									galleryBackgroundPickerOpened = true;
									nextMode = ScreenViewMode.GALLERY;
								}
								mediaState.version++;
								persistGallery = true;
								restartPlayback = true;
							}
						}
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
					} else if (mediaQueueShuffleRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.youtubeMusicShuffleEnabled = !mediaState.youtubeMusicShuffleEnabled;
						syncYoutubeMusicShuffleStateLocked(mediaState, false);
						youtubeQueuePreloadDiff = syncYoutubeQueuePreloadsLocked(mediaState);
						youtubeMusicQueuePreloadDiff = syncYoutubeMusicQueuePreloadsLocked(mediaState);
					} else if (mediaQueueRepeatRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.youtubeRepeatOneEnabled = !mediaState.youtubeRepeatOneEnabled;
					} else if (scrollbarVisible(visibleRows, mediaState.youtubeQueue.size())
							&& mediaQueueScrollbarTrackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
						mediaState.youtubeQueueScroll = scrollValueForTrack(
								mediaQueueScrollbarTrackRect(layout),
								visibleRows,
								mediaState.youtubeQueue.size(),
								touchPoint.y()
						);
					} else {
						int rowCount = Math.min(visibleRows + 1, Math.max(0, mediaState.youtubeQueue.size() - mediaState.youtubeQueueScroll));
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
									youtubeMusicQueuePreloadDiff = syncYoutubeMusicQueuePreloadsLocked(mediaState);
									clearYoutubeMusicShuffleOrderLocked(mediaState);
								} else {
									if (queueIndex < mediaState.youtubeQueueIndex) {
										mediaState.youtubeQueueIndex--;
									} else if (removedCurrent) {
										mediaState.youtubeQueueIndex = Math.min(queueIndex, mediaState.youtubeQueue.size() - 1);
										youtubeQueuePlayIndex = mediaState.youtubeQueueIndex;
									}
									syncYoutubeMusicShuffleStateLocked(mediaState, true);
									int nextMaxScroll = Math.max(0, mediaState.youtubeQueue.size() - visibleRows);
									mediaState.youtubeQueueScroll = clampInt(mediaState.youtubeQueueScroll, 0, nextMaxScroll);
									if (youtubeQueuePlayIndex == null) {
										youtubeQueuePreloadDiff = syncYoutubeQueuePreloadsLocked(mediaState);
										youtubeMusicQueuePreloadDiff = syncYoutubeMusicQueuePreloadsLocked(mediaState);
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
				synchronized (mediaState) {
					nextMode = restorePlayerBackgroundGalleryPickerLocked(mediaState) ? mediaState.mode : ScreenViewMode.HOME;
					clearGalleryBulkSelectionLocked(mediaState);
					mediaState.version++;
				}
			} else if (galleryBrowser
					&& mediaState.mode == ScreenViewMode.GALLERY
					&& !mediaState.playerBackgroundGalleryPickerOpen
					&& mediaGalleryBrowserSelectionRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					setGalleryBulkSelectionModeLocked(mediaState, !mediaState.galleryBulkSelectionMode);
					mediaState.statusText = "";
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (galleryBrowser
					&& mediaState.mode == ScreenViewMode.GALLERY
					&& !mediaState.playerBackgroundGalleryPickerOpen
					&& mediaState.galleryBulkSelectionMode
					&& mediaGalleryBrowserBulkDeleteRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					int selectedCount = mediaState.galleryBulkSelectedKeys.size();
					deletedGalleryCacheCandidates = removeGalleryBulkSelectionLocked(mediaState, layout);
					if (!deletedGalleryCacheCandidates.isEmpty()) {
						persistGallery = true;
						mediaState.statusText = "Удалено: " + selectedCount;
					} else {
						mediaState.statusText = "Ничего не выбрано";
					}
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (galleryBrowser
					&& mediaState.mode == ScreenViewMode.GALLERY
					&& !mediaState.playerBackgroundGalleryPickerOpen
					&& !mediaState.galleryBulkSelectionMode
					&& mediaGalleryBrowserLinkRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				galleryPhotoPrintImportRequested = canImportHeldPhotoPrintToGallery(player);
				galleryLoadRequest = !galleryPhotoPrintImportRequested;
				rerenderCurrent = true;
			} else if (galleryBrowser
					&& mediaGalleryBrowserScrollbarTrackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					List<Integer> visibleGalleryIndexes = galleryBrowserVisibleIndexesLocked(mediaState);
					int visibleRows = mediaGalleryVisibleRows(layout);
					int totalRows = mediaGalleryTotalRows(visibleGalleryIndexes.size(), layout);
					int maxScroll = Math.max(0, totalRows - visibleRows);
					mediaState.galleryScroll = clampInt(mediaState.galleryScroll, 0, maxScroll);
					if (scrollbarVisible(visibleRows, totalRows)) {
						int nextScroll = scrollValueForTrack(
								mediaGalleryBrowserScrollbarTrackRect(layout),
								visibleRows,
								totalRows,
								touchPoint.y()
						);
						if (nextScroll != mediaState.galleryScroll) {
							mediaState.galleryScroll = nextScroll;
							mediaState.version++;
						}
					}
				}
				rerenderCurrent = true;
			} else if (galleryBrowser && mediaGalleryGridRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				boolean galleryGridHandled = false;
				synchronized (mediaState) {
					List<Integer> visibleGalleryIndexes = galleryBrowserVisibleIndexesLocked(mediaState);
					int columns = mediaGalleryColumns(layout);
					int visibleRows = mediaGalleryVisibleRows(layout);
					int totalRows = mediaGalleryTotalRows(visibleGalleryIndexes.size(), layout);
					int maxScroll = Math.max(0, totalRows - visibleRows);
					mediaState.galleryScroll = clampInt(mediaState.galleryScroll, 0, maxScroll);
					int rowCount = Math.min(visibleRows, Math.max(0, totalRows - mediaState.galleryScroll));
					for (int visibleRow = 0; visibleRow < rowCount; visibleRow++) {
						for (int column = 0; column < columns; column++) {
							int visibleIndex = (mediaState.galleryScroll + visibleRow) * columns + column;
							if (visibleIndex < 0 || visibleIndex >= visibleGalleryIndexes.size()) {
								continue;
							}
							int galleryIndex = visibleGalleryIndexes.get(visibleIndex);
							UiRect cardRect = mediaGalleryCardRect(layout, visibleRow, column);
							if (!cardRect.contains(touchPoint.x(), touchPoint.y())) {
								continue;
							}
							galleryGridHandled = true;
							GalleryItem item = mediaState.galleryItems.get(galleryIndex);
							if (mediaState.galleryBulkSelectionMode
									&& mediaState.mode == ScreenViewMode.GALLERY
									&& !mediaState.playerBackgroundGalleryPickerOpen) {
								toggleGalleryBulkItemLocked(mediaState, galleryIndex);
								mediaState.statusText = "";
								mediaState.version++;
								visibleRow = rowCount;
								break;
							}
							if (mediaState.playerBackgroundGalleryPickerOpen) {
								if (galleryItemCanBePlayerBackgroundCandidate(item)) {
									galleryPlayerBackgroundRequestedIndex = galleryIndex;
									restorePlayerBackgroundGalleryPickerLocked(mediaState);
									nextMode = mediaState.mode;
									mediaState.statusText = "";
									mediaState.version++;
								}
								visibleRow = rowCount;
								break;
							}
							GalleryItemKind itemKind = effectiveGalleryItemKind(item);
							if (mediaState.mode == ScreenViewMode.SBER_DRONES
									&& item != null
									&& itemKind == GalleryItemKind.LIVE_CAMERA) {
								LiveCameraReference cameraRef = liveCameraGalleryReference(
										item.url(),
										component.runtimeKey() != null ? component.runtimeKey().dimension() : level.dimension()
								);
								if (cameraRef != null
										&& mediaGalleryCardDisconnectRect(cardRect, layout).contains(touchPoint.x(), touchPoint.y())
										&& isBluetoothLinkedLiveCamera(level, component, cameraRef)) {
									galleryDisconnectRequested = cameraRef;
									visibleRow = rowCount;
									break;
								}
							}
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
								mediaState.version++;
							} else {
								galleryDeferredLoadIndex = galleryIndex;
							}
							visibleRow = rowCount;
							break;
						}
					}
				}
				if (galleryDisconnectRequested != null) {
					unlinkLiveCameraFromScreen(level, component, galleryDisconnectRequested);
				}
				if (!galleryGridHandled
						&& mediaState.mode == ScreenViewMode.GALLERY
						&& !mediaState.playerBackgroundGalleryPickerOpen
						&& !mediaState.galleryBulkSelectionMode
						&& canImportHeldPhotoPrintToGallery(player)) {
					galleryPhotoPrintImportRequested = true;
				}
				rerenderCurrent = true;
			} else if (mediaCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				if (isLibraryAppMode(mediaState.mode) && !galleryBrowser) {
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
						if (restorePlayerBackgroundGalleryPickerLocked(mediaState)) {
							nextMode = mediaState.mode;
						} else if (mediaState.mode == ScreenViewMode.YOUTUBE && mediaState.youtubeReturnToGallery) {
							mediaState.youtubeReturnToGallery = false;
							returnToGallery = true;
							nextMode = null;
						} else {
							nextMode = null;
						}
					}
					if (nextMode == null) {
						nextMode = returnToGallery ? ScreenViewMode.GALLERY : ScreenViewMode.HOME;
					}
				}
			} else if (!galleryBrowser
					&& mediaState.mode == ScreenViewMode.GALLERY
					&& (usesMusicPlayerLayoutLocked(mediaState)
					? mediaYoutubeMusicActionButtonRect(layout, 0, 1)
					: mediaPrimaryActionRect(layout, mediaState)).contains(touchPoint.x(), touchPoint.y())) {
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
					&& mediaWallpaperActionRect(layout, mediaState).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (currentGalleryItemCanBeWallpaperLocked(mediaState)) {
						galleryWallpaperRequested = true;
						mediaState.statusText = "";
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& mediaState.mode == ScreenViewMode.SBER_DRONES
					&& mediaCenterPlayPauseRect(layout, mediaChromeMode(mediaState)).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (currentDroneControlActionVisibleLocked(mediaState)) {
						droneControlRequested = liveCameraGalleryReference(mediaState.sourceUrl, component.runtimeKey().dimension());
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& canTogglePlaybackLocked(mediaState)
					&& (mediaCenterPlayPauseRect(layout, mediaChromeMode(mediaState)).contains(touchPoint.x(), touchPoint.y())
					|| mediaPlayPauseRect(layout, mediaChromeMode(mediaState)).contains(touchPoint.x(), touchPoint.y()))) {
				synchronized (mediaState) {
					if (isStreamPlaybackLocked(mediaState) && mediaState.relaySessionId != null) {
						boolean shouldPause = !isPlaybackPausedLocked(mediaState);
						cancelPlaybackLocked(mediaState);
						markPendingAudioPauseLocked(mediaState, shouldPause);
						markPendingAudioPositionLocked(mediaState, mediaState.positionMs);
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
			} else if (playerUiVisible && mediaCenterBackRect(layout, mediaChromeMode(mediaState)).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (isYoutubeFamilyMode(mediaState.mode) && !mediaState.youtubeQueue.isEmpty()) {
						youtubeQueuePlayIndex = adjacentYoutubeQueueIndexLocked(mediaState, -1);
					} else if (isLibraryAppMode(mediaState.mode) && !mediaState.galleryItems.isEmpty()) {
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
			} else if (playerUiVisible && mediaCenterForwardRect(layout, mediaChromeMode(mediaState)).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (isYoutubeFamilyMode(mediaState.mode) && !mediaState.youtubeQueue.isEmpty()) {
						youtubeQueuePlayIndex = adjacentYoutubeQueueIndexLocked(mediaState, 1);
					} else if (isLibraryAppMode(mediaState.mode) && !mediaState.galleryItems.isEmpty()) {
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
			} else if (playerUiVisible && mediaTimelineHitRect(layout, mediaChromeMode(mediaState)).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					if (isStreamPlaybackLocked(mediaState) && canSeekTimelineLocked(mediaState)) {
						youtubeSeekTargetMs = youtubePositionForFraction(mediaState, mediaTimelineFraction(layout, touchPoint, mediaChromeMode(mediaState)));
						markPendingAudioPositionLocked(mediaState, youtubeSeekTargetMs);
						bumpAudioSyncTokenLocked(mediaState);
					} else if (mediaState.loadedMedia != null && mediaState.loadedMedia.frameCount() > 1) {
						mediaState.frameIndex = mediaFrameIndexForFraction(mediaState.loadedMedia, mediaTimelineFraction(layout, touchPoint, mediaChromeMode(mediaState)));
						mediaState.version++;
					}
				}
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& mediaState.mode == ScreenViewMode.YOUTUBE
					&& isGalleryBackedYoutubeLocked(mediaState)
					&& mediaPrimaryActionRect(layout, mediaState).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.galleryDeleteConfirmOpen = true;
					mediaState.statusText = "";
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& mediaState.mode == ScreenViewMode.YOUTUBE_MUSIC
					&& !isGalleryBackedYoutubeLocked(mediaState)
					&& !isYoutubeHomePromptLocked(mediaState)
					&& mediaYoutubeMusicDownloadRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					youtubeDownloadRequested = true;
					mediaState.statusText = "";
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (playerUiVisible && mediaState.mode == ScreenViewMode.YOUTUBE && mediaPrimaryActionRect(layout, mediaState).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					youtubeDownloadRequested = true;
					mediaState.statusText = "";
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (!galleryBrowser
					&& playerBackgroundMenuButtonVisibleLocked(mediaState)
					&& mediaPlayerMenuRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.playerBackgroundMenuOpen = !mediaState.playerBackgroundMenuOpen;
					if (mediaState.playerBackgroundMenuOpen) {
						mediaState.galleryDeleteConfirmOpen = false;
						mediaState.youtubeQueueOpen = false;
					}
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (playerUiVisible && mediaScaleActionRect(layout, mediaState).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.scaleMode = mediaState.scaleMode.next();
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if ((playerUiVisible || isYoutubeMusicMode(mediaState.mode))
					&& isYoutubeFamilyMode(mediaState.mode)
					&& !isGalleryBackedYoutubeLocked(mediaState)
					&& (isYoutubeMusicMode(mediaState.mode)
					? mediaQueueToggleRect(layout, mediaState.mode)
					: mediaQueueActionRect(layout, mediaState)).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.youtubeQueueOpen = !mediaState.youtubeQueueOpen;
					if (mediaState.youtubeQueueOpen) {
						alignYoutubeQueueScrollToCurrentTopLocked(mediaState, mediaQueueVisibleRows(layout));
						youtubeQueueStatusRefreshRequested = true;
					}
					mediaState.version++;
				}
				rerenderCurrent = true;
			} else if (isYoutubeMusicMode(mediaState.mode)
					&& !isYoutubeHomePromptLocked(mediaState)
					&& !isGalleryBackedYoutubeLocked(mediaState)
					&& mediaYoutubeMusicShuffleRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				synchronized (mediaState) {
					mediaState.youtubeMusicShuffleEnabled = !mediaState.youtubeMusicShuffleEnabled;
					if (mediaState.youtubeMusicShuffleEnabled) {
						syncYoutubeMusicShuffleStateLocked(mediaState, false);
					} else {
						clearYoutubeMusicShuffleOrderLocked(mediaState);
					}
					youtubeMusicQueuePreloadDiff = syncYoutubeMusicQueuePreloadsLocked(mediaState);
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
						youtubeShouldAppendQueue ? YoutubeLinkRequestAction.APPEND_QUEUE : YoutubeLinkRequestAction.REPLACE_QUEUE
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
						isYoutubeFamilyMode(component.viewMode()) && youtubeShouldAppendQueue ? YoutubeLinkRequestAction.APPEND_QUEUE : YoutubeLinkRequestAction.REPLACE_QUEUE
				);
				rerenderCurrent = true;
			} else if (playerUiVisible
					&& !isYoutubeMusicMode(mediaChromeMode(mediaState))
					&& !controlsWereHidden
					&& !mediaState.loading) {
				boolean liveCameraPlayback;
				synchronized (mediaState) {
					liveCameraPlayback = mediaState.streamKind == PlaybackStreamKind.LIVE_CAMERA;
					setMediaOverlayModeLocked(mediaState, MediaOverlayMode.VIEW);
					mediaState.version++;
				}
				restoreLiveCameraView = liveCameraPlayback;
				rerenderCurrent = !liveCameraPlayback;
			}
		} else if (component.viewMode() == ScreenViewMode.CAMERA_APP) {
			if (!MonitorCameraRuntime.handleTouch(server, component, layout, touchPoint)) {
				nextMode = ScreenViewMode.HOME;
			}
		} else if (component.viewMode() == ScreenViewMode.MAX) {
			MonitorMaxRuntime.handleTouch(player, level, component, layout, touchPoint);
		} else if (component.viewMode() == ScreenViewMode.YANDEX_MAPS) {
			MonitorYandexMapsRuntime.handleTouch(player, level, component, layout, touchPoint);
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
			youtubeQueueStatusRefreshRequested = true;
		}
		if (!youtubeMusicQueuePreloadDiff.isEmpty() && youtubeQueuePlayIndex == null) {
			applyYoutubeMusicQueuePreloadDiff(youtubeMusicQueuePreloadDiff);
			youtubeQueueStatusRefreshRequested = true;
		}
		if (youtubeQueueStatusRefreshRequested && server != null) {
			scheduleYoutubeQueueCacheStatusRefreshes(server, component.runtimeKey());
		}
		if (returnToGalleryAfterDelete) {
			nextMode = ScreenViewMode.GALLERY;
			rerenderCurrent = false;
		}

		if ((nextMode != null && nextMode != component.viewMode())
				|| (nextLauncherPage != null && nextLauncherPage != component.launcherPage())) {
			if (isPlayerMode(component.viewMode()) && nextMode != component.viewMode()) {
				MediaRuntimeState currentState = MEDIA_STATES.get(component.runtimeKey());
				boolean preserveRuntimeTransition = false;
				boolean preserveWallpaperPlayback = false;
				List<String> preservedReleasedQueueUrls = List.of();
				List<String> preservedReleasedMusicQueueUrls = List.of();
				if (currentState != null) {
					synchronized (currentState) {
						preserveRuntimeTransition = consumePreservedRuntimeTransitionLocked(currentState, nextMode);
						preserveWallpaperPlayback = !preserveRuntimeTransition
								&& shouldPreserveWallpaperPlaybackOnTransitionLocked(currentState, nextMode);
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
					scheduleBackgroundPlaybackIfNeeded(level.getServer(), component.runtimeKey());
				} else if (!preserveRuntimeTransition) {
					deactivateMediaSession(level.getServer(), component.runtimeKey());
				}
			}
			if (isPlayerMode(nextMode) && component.viewMode() != nextMode) {
				openMediaSession(player, component.runtimeKey(), nextMode);
				markMediaFocus(player, component.runtimeKey());
			}
			applyTransientComponentViewState(
					level.getServer(),
					level,
					component,
					nextMode != null ? nextMode : component.viewMode(),
					nextLauncherPage != null ? nextLauncherPage : component.launcherPage()
			);
		} else if (rerenderCurrent) {
			requestComponentRender(level.getServer(), component, component.viewMode(), component.launcherPage());
			if (isPlayerMode(component.viewMode())) {
				if (restartPlayback) {
					restartMediaPlaybackIfNeeded(level.getServer(), component.runtimeKey());
				} else {
					resumeMediaPlaybackIfNeeded(level.getServer(), component.runtimeKey());
				}
			}
		}
		if (restoreLiveCameraView) {
			restoreLiveCameraViewFromBufferedTiles(level.getServer(), component.runtimeKey());
		}
		if (galleryLoadRequest) {
			requestMediaLink(player, component.runtimeKey(), false, ScreenViewMode.GALLERY, YoutubeLinkRequestAction.REPLACE_QUEUE);
		}
		if (galleryBackgroundPickerOpened && server != null) {
			MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
			if (state != null) {
				ensureGalleryStateHydrated(server, component.runtimeKey(), state);
			}
		}
		if (server != null && galleryPhotoPrintImportRequested) {
			importHeldPhotoPrintToGallery(server, component.runtimeKey(), player, layout);
		}
		if (server != null && galleryDownloadRequested) {
			beginGalleryDownload(server, component.runtimeKey(), player.getUUID(), layout);
		}
		if (server != null && galleryWallpaperRequested) {
			applyGalleryWallpaper(server, component.runtimeKey(), player.getUUID());
		}
		if (server != null && galleryPlayerBackgroundRequestedIndex != null) {
			applyGalleryPlayerBackground(server, component.runtimeKey(), player.getUUID(), galleryPlayerBackgroundRequestedIndex);
		}
		if (persistGallery && server != null) {
			MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
			if (state != null) {
				persistGalleryState(server, component.runtimeKey(), state);
			}
			if (!deletedGalleryCacheCandidates.isEmpty()) {
				scheduleGalleryCacheRelease(server, deletedGalleryCacheCandidates, component.runtimeKey());
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
			MediaRuntimeState state = MEDIA_STATES.get(component.runtimeKey());
			if (state != null && isYoutubeMusicMode(state.mode)) {
				startYoutubeMusicQueuePlayback(server, component.runtimeKey(), player.getUUID(), youtubeQueuePlayIndex);
			} else {
				startYoutubeQueuePlayback(server, component.runtimeKey(), player.getUUID(), youtubeQueuePlayIndex);
			}
		}
		if (server != null && youtubeDownloadRequested) {
			beginYoutubeDownload(server, component.runtimeKey(), player.getUUID());
		}
		if (server != null && galleryYoutubeUrl != null) {
			startGalleryYoutubePlayback(server, component.runtimeKey(), player.getUUID(), galleryYoutubeTitle, galleryYoutubeUrl, galleryYoutubeIndex);
		}
			if (server != null && droneControlRequested != null && droneControlRequested.sourceType() == LiveCameraSourceType.DRONE) {
				DroneSystem.tryStartControllingDrone(
						player,
						droneControlRequested.sourceUuid(),
						droneControlRequested.dimension() != null ? droneControlRequested.dimension() : component.runtimeKey().dimension(),
						droneControlRequested.pos()
				);
			}
		return InteractionResult.SUCCESS;
	}
}
