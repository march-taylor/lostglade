package com.lostglade.server;

import com.lostglade.server.monitor.MonitorMediaApp;
import com.lostglade.server.monitor.MonitorYoutubeRelayClient;
import com.lostglade.server.progress.TaskProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

record RenderCacheKey(boolean powered, ScreenViewMode viewMode, int launcherPage, int width, int height) {
}

record OverlayWindowCacheKey(MediaOverlayWindowSnapshot snapshot, int width, int height, int unit) {
}

record OverlayWindowFamilyKey(MediaOverlayWindowType type, int width, int height, int unit) {
}

record MediaVisualSnapshot(
		ScreenViewMode mode,
		long version,
		BufferedImage frame,
		BufferedImage backgroundFrame,
		BufferedImage playerBackgroundFrame,
		boolean hasMedia,
		boolean galleryBrowser,
		boolean galleryPickerMode,
		boolean galleryCurrentSaved,
		boolean galleryBackedYoutube,
		boolean musicPlayerLayout,
		boolean streamPlayback,
		boolean playbackControlsVisible,
		boolean loading,
		boolean waitingForLink,
		boolean timelineVisible,
		boolean centerPlayPauseVisible,
		boolean droneControlVisible,
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
		MediaScaleMode playerBackgroundScaleMode,
		PlayerBackgroundMode playerBackgroundMode,
		boolean galleryBackgroundAvailable,
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
		boolean youtubeMusicShuffleEnabled,
		boolean youtubeQueueOpen,
		int mediaListScroll,
		int currentMediaListIndex,
		MediaOverlayWindowSnapshot overlayWindow
) {
}

record WallpaperVisualSnapshot(
		BufferedImage frame,
		MediaScaleMode scaleMode,
		PlayerBackgroundMode backgroundMode
) {
}

record YandexMapsVisualSnapshot(
		long version,
		BufferedImage frame,
		String statusText,
		String dimensionLabel,
		double centerX,
		double centerZ,
		double zoomBlocks,
		java.util.List<MonitorYandexMapsBlueMapRenderer.DisplayOverlay> displayOverlays,
		boolean healthy
) {
}

record RenderTileTarget(
		int tileIndex,
		MapId mapId,
		byte scale,
		boolean locked,
		byte[] baselineFrame
) {
}

record MediaOverlayWindowSnapshot(
		MediaOverlayWindowType type,
		String title,
		String subtitle,
		List<YoutubeQueueItemSnapshot> items,
		int scroll,
		int currentIndex,
		boolean shuffleEnabled,
		boolean repeatOneEnabled,
		PlayerBackgroundMode playerBackgroundMode,
		boolean galleryBackgroundAvailable,
		MediaScaleMode playerBackgroundScaleMode
) {
}

record RenderWork(
		ScreenRuntimeKey runtimeKey,
		boolean powered,
		ScreenViewMode viewMode,
		int launcherPage,
		int width,
		int height,
		long mediaVersion,
		MediaVisualSnapshot mediaSnapshot,
		CameraAppVisualSnapshot cameraAppSnapshot,
		MaxVisualSnapshot maxSnapshot,
		YandexMapsVisualSnapshot yandexMapsSnapshot,
		WallpaperVisualSnapshot wallpaperSnapshot,
		boolean transparentOutput,
		List<RenderTileTarget> tileTargets
) {
}

record CameraAppVisualSnapshot(
		long version,
		BufferedImage previewFrame,
		List<CameraAppDeviceSnapshot> cameras,
		List<CameraAppDeviceSnapshot> microphones,
		int selectedCameraIndex,
		int selectedMicrophoneIndex,
		CameraAppCaptureMode captureMode,
		boolean recording,
		boolean paused,
		long elapsedMs,
		boolean deviceMenuOpen,
		String statusText
) {
	boolean dynamic() {
		return this.recording && !this.paused;
	}
}

record CameraAppDeviceSnapshot(
		String title,
		String subtitle,
		String url,
		boolean selected,
		boolean online
) {
}

record MaxVisualSnapshot(
		long version,
		String accountCode,
		BufferedImage avatarFrame,
		List<MaxContactSnapshot> contacts,
		MaxCallVisualSnapshot call,
		List<MaxAvatarCandidateSnapshot> avatarCandidates,
		List<MaxRingtoneCandidateSnapshot> ringtoneCandidates,
		boolean avatarPickerOpen,
		boolean ringtonePickerOpen,
		boolean ringtonePreviewPlaying,
		String statusText
) {
	boolean dynamic() {
		return (this.call != null && this.call.dynamic()) || this.ringtonePreviewPlaying;
	}
}

record MaxContactSnapshot(
		String code,
		BufferedImage avatarFrame,
		boolean online,
		boolean ringing,
		boolean active
) {
}

record MaxCallVisualSnapshot(
		MaxCallPhase phase,
		String peerCode,
		BufferedImage peerAvatarFrame,
		BufferedImage localPreviewFrame,
		BufferedImage remoteFrame,
		List<MaxCallParticipantSnapshot> participants,
		String focusedParticipantCode,
		String statusText,
		boolean cameraEnabled,
		boolean microphoneEnabled,
		List<MaxCameraOptionSnapshot> cameras,
		int selectedCameraIndex,
		int microphoneCount,
		int selectedMicrophoneIndex,
		boolean menuOpen,
		boolean cameraPickerOpen,
		boolean contactPickerOpen,
		boolean selfFocused,
		boolean peerFocused,
		long elapsedMillis
) {
	boolean dynamic() {
		return this.phase == MaxCallPhase.OUTGOING
				|| this.phase == MaxCallPhase.INCOMING
				|| this.phase == MaxCallPhase.ACTIVE
				|| this.remoteFrame != null
				|| this.participants.stream().anyMatch(participant -> participant != null && participant.videoFrame() != null);
	}
}

record MaxCallParticipantSnapshot(
		String code,
		BufferedImage avatarFrame,
		BufferedImage videoFrame,
		boolean self,
		boolean cameraEnabled,
		boolean microphoneEnabled,
		boolean ringing
) {
}

record MaxCameraOptionSnapshot(
		String title,
		String subtitle,
		String url,
		BufferedImage preview,
		boolean selected,
		boolean online
) {
}

record MaxAvatarCandidateSnapshot(
		String title,
		String url,
		String localMediaKey,
		BufferedImage preview
) {
}

record MaxRingtoneCandidateSnapshot(
		String title,
		String subtitle,
		String url,
		String localMediaKey,
		boolean selected,
		boolean playing,
		float timelineFraction
) {
}

record MediaDispatchKey(
		boolean powered,
		ScreenViewMode viewMode,
		int launcherPage,
		int width,
		int height
) {
}

record PendingMediaLinkRequest(ScreenRuntimeKey screenKey, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
}

record InFlightMediaLinkRequest(ScreenRuntimeKey screenKey, ScreenViewMode mode, YoutubeLinkRequestAction youtubeAction) {
}

record YoutubeQueueItemSnapshot(
		int queueIndex,
		String title,
		String subtitle,
		long durationMs,
		boolean current,
		float cacheFraction,
		boolean cacheActive,
		boolean cacheComplete
) {
}

record YoutubeQueuePreloadDiff(List<String> retainUrls, List<String> releaseUrls) {
	static final YoutubeQueuePreloadDiff EMPTY = new YoutubeQueuePreloadDiff(List.of(), List.of());

	boolean isEmpty() {
		return this.retainUrls.isEmpty() && this.releaseUrls.isEmpty();
	}
}

record YoutubeMusicQueuePreloadDiff(List<String> retainUrls, List<String> releaseUrls) {
	static final YoutubeMusicQueuePreloadDiff EMPTY = new YoutubeMusicQueuePreloadDiff(List.of(), List.of());

	boolean isEmpty() {
		return this.retainUrls.isEmpty() && this.releaseUrls.isEmpty();
	}
}

record GalleryItem(
		String title,
		String subtitle,
		String url,
		String localMediaKey,
		MonitorMediaApp.LoadedMedia media,
		BufferedImage preview,
		GalleryItemKind kind
) {
}

record PersistedGalleryItem(String title, String subtitle, String url, GalleryItemKind kind, String localMediaKey) {
}

record GalleryRemovalResult(GalleryItem removedItem, boolean selectionRetained) {
}

record GalleryCacheCandidate(String url, String localMediaKey, GalleryItemKind kind) {
}

record GalleryCacheReferenceSnapshot(
		Set<String> localMediaKeys,
		Set<String> galleryMediaUrls,
		Set<String> galleryMusicUrls,
		Set<String> galleryYoutubeUrls,
		Set<String> activeMediaUrls,
		Set<String> activeMusicUrls,
		Set<String> activeYoutubeUrls
) {
}

record PersistedWallpaperState(String url, MediaScaleMode scaleMode, PlayerBackgroundMode backgroundMode) {
}

record PersistedPlayerBackgroundState(String url, MediaScaleMode scaleMode) {
}

record GalleryItemLoadResult(
		ScreenRuntimeKey screenKey,
		String title,
		String subtitle,
		String url,
		String localMediaKey,
		GalleryItemKind kind,
		MonitorMediaApp.LoadedMedia loadedMedia,
		MonitorMediaApp.LoadedVideo loadedVideo,
		boolean openWhenReady,
		int preferredIndex,
		long sessionGeneration,
		String error
) {
}

record SavedGalleryMediaPersistResult(
		String url,
		String savedMediaKey,
		String error
) {
}

record WallpaperLoadResult(
		ScreenRuntimeKey screenKey,
		String url,
		String localMediaKey,
		MonitorMediaApp.LoadedMedia loadedMedia,
		long sessionGeneration,
		String error
) {
}

record PlayerBackgroundLoadResult(
		ScreenRuntimeKey screenKey,
		String url,
		String localMediaKey,
		MonitorMediaApp.LoadedMedia loadedMedia,
		long sessionGeneration,
		String error
) {
}

record GalleryCardSnapshot(
		int index,
		String title,
		String subtitle,
		String tertiary,
		String statusLabel,
		boolean statusActive,
		String sourceLabel,
		boolean metadataVisible,
		GalleryItemKind kind,
		boolean animatedMedia,
		boolean animated,
		BufferedImage preview,
		boolean current,
		boolean loaded,
		boolean disconnectVisible
) {
}

final class OverlayWindowRenderState {
	volatile BufferedImage image;
	volatile CompletableFuture<BufferedImage> future;
	volatile long lastAccessNanos;
}

record PlayerMediaFocus(ScreenRuntimeKey screenKey, long expiresAtMillis) {
}

record PlacementNeighbor(BlockPos pos, int connectionMask, double distance) {
	PlacementNeighbor(BlockPos pos, int connectionMask) {
		this(pos, connectionMask, 0.0D);
	}
}

record MediaLoadResult(
		ScreenRuntimeKey screenKey,
		UUID requesterUuid,
		String url,
		String title,
		String subtitle,
		GalleryItemKind kind,
		MonitorMediaApp.LoadedMedia loadedMedia,
		MonitorMediaApp.LoadedVideo loadedVideo,
		long sessionGeneration,
		String error
) {
}

record YoutubeLoadResult(
		ScreenRuntimeKey screenKey,
		UUID requesterUuid,
		String url,
		ScreenViewMode targetMode,
		PlaybackStreamKind streamKind,
		String subtitle,
		MonitorYoutubeRelayClient.SessionLoadResponse loadResponse,
		String error
) {
}

record YoutubeQueueResolveResult(
		ScreenRuntimeKey screenKey,
		UUID requesterUuid,
		ScreenViewMode mode,
		String url,
		MonitorYoutubeRelayClient.QueueResolveResponse queueResponse,
		YoutubeLinkRequestAction action,
		long sessionGeneration,
		String error
) {
}

record YoutubeMusicLoadResult(
		ScreenRuntimeKey screenKey,
		UUID requesterUuid,
		String url,
		String title,
		String artist,
		MonitorMediaApp.LoadedVideo loadedVideo,
		MonitorYoutubeRelayClient.SessionLoadResponse relayLoadResponse,
		int queueIndex,
		String error
) {
}

record YoutubeSnapshotResult(
		ScreenRuntimeKey screenKey,
		MonitorYoutubeRelayClient.SessionSnapshot snapshot,
		String error
) {
}

record LiveCameraSnapshotResult(
		ScreenRuntimeKey screenKey,
		String url,
		BufferedImage previewFrame,
		BufferedImage fullFrame,
		String error
) {
}

enum MediaBottomAction {
	WALLPAPER,
	PRIMARY,
	QUEUE,
	SCALE
}

enum PlayerUiIcon {
	SEARCH("/assets/lg2/textures/monitor/ui_icons/search.png"),
	SHUFFLE("/assets/lg2/textures/monitor/ui_icons/shuffle.png"),
	REPEAT_ONE("/assets/lg2/textures/monitor/ui_icons/repeat_one.png"),
	DROPDOWN("/assets/lg2/textures/monitor/ui_icons/dropdown.png"),
	MENU("/assets/lg2/textures/monitor/ui_icons/menu.png"),
	QUEUE("/assets/lg2/textures/monitor/ui_icons/queue.png"),
	DOWNLOAD("/assets/lg2/textures/monitor/ui_icons/download.png"),
	TRASH("/assets/lg2/textures/monitor/ui_icons/trash.png"),
	WALLPAPER("/assets/lg2/textures/monitor/ui_icons/wallpaper.png"),
	CHECK("/assets/lg2/textures/monitor/ui_icons/check.png"),
	PLAY("/assets/lg2/textures/monitor/ui_icons/play.png"),
	PAUSE("/assets/lg2/textures/monitor/ui_icons/pause.png"),
	FILE_MUSIC("/assets/lg2/textures/monitor/ui_icons/file_music.png"),
	MEDIA_VIDEO("/assets/lg2/textures/monitor/ui_icons/media_video.png"),
	MEDIA_IMAGE("/assets/lg2/textures/monitor/ui_icons/media_image.png"),
	MEDIA_GIF("/assets/lg2/textures/monitor/ui_icons/media_gif.png"),
	MEDIA_AUDIO("/assets/lg2/textures/monitor/ui_icons/media_audio.png"),
	FIT("/assets/lg2/textures/monitor/ui_icons/fit.png"),
	FILL("/assets/lg2/textures/monitor/ui_icons/fill.png"),
	STRETCH("/assets/lg2/textures/monitor/ui_icons/stretch.png"),
	CLOSE("/assets/lg2/textures/monitor/ui_icons/close.png"),
	BACK("/assets/lg2/textures/monitor/ui_icons/back.png"),
	DRONE("/assets/lg2/textures/monitor/ui_icons/drone.png"),
	CAMERA("/assets/lg2/textures/monitor/ui_icons/camera.png"),
	CALL_ACCEPT("/assets/lg2/textures/monitor/ui_icons/call_accept.png"),
	CALL_DECLINE("/assets/lg2/textures/monitor/ui_icons/call_decline.png"),
	MIC("/assets/lg2/textures/monitor/ui_icons/mic.png"),
	MIC_OFF("/assets/lg2/textures/monitor/ui_icons/mic_off.png"),
	VIDEO_CAMERA("/assets/lg2/textures/monitor/ui_icons/video_camera.png"),
	VIDEO_CAMERA_OFF("/assets/lg2/textures/monitor/ui_icons/video_camera_off.png"),
	DEVICE_SELECT("/assets/lg2/textures/monitor/ui_icons/device_select.png"),
	CONTACT_ADD("/assets/lg2/textures/monitor/ui_icons/contact_add.png"),
	FULLSCREEN_EXIT("/assets/lg2/textures/monitor/ui_icons/fullscreen_exit.png"),
	SIGNAL("/assets/lg2/textures/monitor/ui_icons/signal.png"),
	OFFLINE("/assets/lg2/textures/monitor/ui_icons/offline.png"),
	UNLINK("/assets/lg2/textures/monitor/ui_icons/unlink.png"),
	LOCATION("/assets/lg2/textures/monitor/ui_icons/location.png"),
	AIMING_2("/assets/lg2/textures/monitor/ui_icons/aiming_2.png"),
	ADD("/assets/lg2/textures/monitor/ui_icons/add.png"),
	MINUS("/assets/lg2/textures/monitor/ui_icons/minus.png"),
	TARGET("/assets/lg2/textures/monitor/ui_icons/target.png");

	private final String resourcePath;

	PlayerUiIcon(String resourcePath) {
		this.resourcePath = resourcePath;
	}

	String resourcePath() {
		return this.resourcePath;
	}
}

record PlayerUiIconTintKey(PlayerUiIcon icon, int argb) {
}

record MapPacketUpdate(
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

record PreparedMapUpdate(
		MapId mapId,
		byte scale,
		boolean locked,
		int startX,
		int startY,
		int width,
		int height,
		byte[] frame,
		byte[] fullFrame,
		byte[] baselineFrame
) {
}

record RenderedTileBatch(byte[][] renderedTiles, List<PreparedMapUpdate> updates) {
}

record TileFramePatch(int startX, int startY, int width, int height, byte[] frame) {
}

record PreparedRenderedTiles(byte[][] renderedTiles, TileFramePatch[] tilePatches, long baselineGeneration) {
}

final class MediaRuntimeState {
	ScreenViewMode mode;
	PlaybackStreamKind streamKind;
	MonitorMediaApp.LoadedMedia loadedMedia;
	BufferedImage streamFrame;
	BufferedImage loadingBackdropFrame;
	String sourceUrl;
	String relaySessionId;
	String audioStreamUrl;
	String mediaTitle;
	String mediaSubtitle;
	int frameIndex;
	long youtubeFrameSequence;
	long positionMs;
	long durationMs;
	long bufferedStartMs;
	long bufferedEndMs;
	long audioSyncToken;
	long version;
	MediaOverlayMode overlayMode;
	MediaScaleMode scaleMode;
	GallerySurfaceMode gallerySurfaceMode;
	PlayerBackgroundMode playerBackgroundMode;
	boolean liveStream;
	boolean audioPlaceholder;
	boolean userPaused;
	boolean waitingForLink;
	boolean loading;
	boolean galleryDeleteConfirmOpen;
	boolean playerBackgroundMenuOpen;
	String statusText;
	boolean galleryHydrated;
	boolean wallpaperHydrated;
	boolean playerBackgroundHydrated;
	boolean playerBackgroundModeHydrated;
	final List<GalleryItem> galleryItems;
	final Set<String> galleryLoadingUrls;
	MonitorMediaApp.LoadedMedia wallpaperMedia;
	String wallpaperUrl;
	MediaScaleMode wallpaperScaleMode;
	PlayerBackgroundMode wallpaperBackgroundMode;
	int wallpaperFrameIndex;
	boolean wallpaperLoading;
	MonitorMediaApp.LoadedMedia playerBackgroundMedia;
	String playerBackgroundUrl;
	MediaScaleMode playerBackgroundScaleMode;
	int playerBackgroundFrameIndex;
	boolean playerBackgroundLoading;
	String galleryPendingOpenUrl;
	int galleryPendingOpenIndex;
	int galleryPendingOpenRequestId;
	int galleryNextOpenRequestId;
	int galleryIndex;
	int galleryScroll;
	boolean galleryPreloadStatusRefreshScheduled;
	int galleryPreloadStatusRefreshStep;
	boolean playerBackgroundGalleryPickerOpen;
	ScreenViewMode playerBackgroundGalleryPickerReturnMode;
	GallerySurfaceMode playerBackgroundGalleryPickerReturnSurfaceMode;
	boolean preserveRuntimeOnNextViewModeTransition;
	boolean downloadInProgress;
	String downloadTargetUrl;
	UUID downloadRequesterUuid;
	long downloadStartedAtMillis;
	String downloadCompletedUrl;
	long downloadCompletedUntilMillis;
	final List<YoutubeQueueItem> youtubeQueue;
	final Set<String> retainedYoutubePreloadUrls;
	final Set<String> retainedYoutubeMusicUrls;
	boolean youtubeMusicShuffleEnabled;
	boolean youtubeRepeatOneEnabled;
	final List<Integer> youtubeMusicShuffleOrder;
	int youtubeMusicShuffleCursor;
	int youtubeQueueIndex;
	int youtubeQueueScroll;
	boolean youtubeQueueOpen;
	boolean youtubeQueueCacheStatusRefreshScheduled;
	boolean youtubeReturnToGallery;
	boolean liveCameraCaptureInFlight;
	byte[] pendingLiveCameraPixels;
	boolean liveCameraDecodeScheduled;
	long liveCameraLastFrameAtMillis;
	byte[][] liveCameraBufferedTiles;
	byte[][] liveCameraDisplayedTiles;
	long liveCameraDisplayedGeneration;
	long nextLiveCameraPreviewDecodeAtMillis;
	PreparedRenderedTiles pendingLiveCameraPreparedTiles;
	String pendingLiveCameraApplyUrl;
	boolean liveCameraApplyScheduled;
	long nextLiveCameraGallerySyncAtMillis;
	long nextLoadedMediaFrameAtMillis;
	long nextWallpaperFrameAtMillis;
	long nextPlayerBackgroundFrameAtMillis;
	int activeRenderJobs;
	boolean rerenderRequested;
	MediaDispatchKey lastDispatchKey;
	ScheduledFuture<?> playbackFuture;
	ScheduledFuture<?> backgroundFuture;
	long nextProgressRenderAtMillis;
	long sessionGeneration;
	Boolean pendingAudioPauseState;
	boolean pendingAudioPositionActive;
	long pendingAudioPositionMs;
	long pendingAudioIssuedAtMillis;
	final Runnable progressListener;
	TaskProgress progress;

	MediaRuntimeState(ScreenViewMode mode, Runnable progressListener) {
		this.mode = mode;
		this.streamKind = PlaybackStreamKind.NONE;
		this.overlayMode = MediaOverlayMode.CONTROLS;
		this.scaleMode = MediaScaleMode.FIT;
		this.gallerySurfaceMode = GallerySurfaceMode.BROWSER;
		this.playerBackgroundMode = null;
		this.liveStream = false;
		this.audioPlaceholder = true;
		this.userPaused = false;
		this.waitingForLink = false;
		this.loading = false;
		this.galleryDeleteConfirmOpen = false;
		this.playerBackgroundMenuOpen = false;
		this.pendingAudioPauseState = null;
		this.pendingAudioPositionActive = false;
		this.pendingAudioPositionMs = 0L;
		this.pendingAudioIssuedAtMillis = 0L;
		this.galleryHydrated = false;
		this.wallpaperHydrated = false;
		this.playerBackgroundHydrated = false;
		this.playerBackgroundModeHydrated = false;
		this.version = 0L;
		this.statusText = "";
		this.mediaSubtitle = "";
		this.loadingBackdropFrame = null;
		this.galleryItems = new ArrayList<>();
		this.galleryLoadingUrls = new HashSet<>();
		this.wallpaperMedia = null;
		this.wallpaperUrl = null;
		this.wallpaperScaleMode = MediaScaleMode.FIT;
		this.wallpaperBackgroundMode = PlayerBackgroundMode.EMPTY;
		this.wallpaperFrameIndex = 0;
		this.wallpaperLoading = false;
		this.playerBackgroundMedia = null;
		this.playerBackgroundUrl = null;
		this.playerBackgroundScaleMode = MediaScaleMode.FIT;
		this.playerBackgroundFrameIndex = 0;
		this.playerBackgroundLoading = false;
		this.galleryPendingOpenUrl = null;
		this.galleryPendingOpenIndex = -1;
		this.galleryPendingOpenRequestId = 0;
		this.galleryNextOpenRequestId = 0;
		this.galleryIndex = -1;
		this.galleryScroll = 0;
		this.galleryPreloadStatusRefreshScheduled = false;
		this.galleryPreloadStatusRefreshStep = 0;
		this.playerBackgroundGalleryPickerOpen = false;
		this.playerBackgroundGalleryPickerReturnMode = null;
		this.playerBackgroundGalleryPickerReturnSurfaceMode = null;
		this.preserveRuntimeOnNextViewModeTransition = false;
		this.downloadInProgress = false;
		this.downloadTargetUrl = null;
		this.downloadRequesterUuid = null;
		this.downloadStartedAtMillis = 0L;
		this.downloadCompletedUrl = null;
		this.downloadCompletedUntilMillis = 0L;
		this.youtubeQueue = new ArrayList<>();
		this.retainedYoutubePreloadUrls = new HashSet<>();
		this.retainedYoutubeMusicUrls = new HashSet<>();
		this.youtubeMusicShuffleEnabled = false;
		this.youtubeRepeatOneEnabled = false;
		this.youtubeMusicShuffleOrder = new ArrayList<>();
		this.youtubeMusicShuffleCursor = -1;
		this.youtubeQueueIndex = -1;
		this.youtubeQueueScroll = 0;
		this.youtubeQueueOpen = false;
		this.youtubeQueueCacheStatusRefreshScheduled = false;
		this.youtubeReturnToGallery = false;
		this.liveCameraCaptureInFlight = false;
		this.pendingLiveCameraPixels = null;
		this.liveCameraDecodeScheduled = false;
		this.liveCameraLastFrameAtMillis = 0L;
		this.liveCameraBufferedTiles = null;
		this.liveCameraDisplayedTiles = null;
		this.liveCameraDisplayedGeneration = 0L;
		this.nextLiveCameraPreviewDecodeAtMillis = 0L;
		this.pendingLiveCameraPreparedTiles = null;
		this.pendingLiveCameraApplyUrl = null;
		this.liveCameraApplyScheduled = false;
		this.nextLiveCameraGallerySyncAtMillis = 0L;
		this.nextLoadedMediaFrameAtMillis = 0L;
		this.nextWallpaperFrameAtMillis = 0L;
		this.nextPlayerBackgroundFrameAtMillis = 0L;
		this.activeRenderJobs = 0;
		this.nextProgressRenderAtMillis = 0L;
		this.backgroundFuture = null;
		this.sessionGeneration = 1L;
		this.progressListener = progressListener;
		this.progress = new TaskProgress(progressListener);
	}

	static MediaRuntimeState fresh(ScreenViewMode mode, String statusText, Runnable progressListener) {
		MediaRuntimeState state = new MediaRuntimeState(mode, progressListener);
		state.statusText = statusText;
		return state;
	}
}

record YoutubeQueueItem(String title, String subtitle, long durationMs, String url) {
}
