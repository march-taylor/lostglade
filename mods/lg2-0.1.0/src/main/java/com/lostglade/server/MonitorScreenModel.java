package com.lostglade.server;

import com.lostglade.server.monitor.MonitorYoutubeRelayClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

enum ScreenViewMode {
	HOME("home"),
	GALLERY("gallery"),
	SBER_DRONES("sberdrones"),
	CAMERA_APP("cameraapp"),
	MAX("max"),
	YANDEX_MAPS("yandexmaps"),
	YOUTUBE("youtube"),
	YOUTUBE_MUSIC("youtubemusic");

	private final String serializedName;

	ScreenViewMode(String serializedName) {
		this.serializedName = serializedName;
	}

	String serializedName() {
		return this.serializedName;
	}

	static ScreenViewMode fromTag(String value) {
		if ("media".equalsIgnoreCase(value)) {
			return GALLERY;
		}
		for (ScreenViewMode mode : values()) {
			if (mode.serializedName.equalsIgnoreCase(value)) {
				return mode;
			}
		}
		return HOME;
	}
}

enum MediaOverlayMode {
	VIEW,
	CONTROLS
}

enum GallerySurfaceMode {
	BROWSER,
	PLAYER
}

enum PlayerBackgroundMode {
	ARTWORK,
	GALLERY,
	BLACK,
	EMPTY;

	static PlayerBackgroundMode fromPersisted(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if ("wallpaper".equalsIgnoreCase(value.trim())) {
			return GALLERY;
		}
		try {
			return PlayerBackgroundMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	String persistedName() {
		return this == GALLERY ? "gallery" : this.name().toLowerCase(Locale.ROOT);
	}
}

enum MediaOverlayWindowType {
	YOUTUBE_QUEUE,
	GALLERY_DELETE_CONFIRM,
	GALLERY_FILE_MENU,
	PLAYER_BACKGROUND
}

enum YoutubeLinkRequestAction {
	REPLACE_QUEUE,
	APPEND_QUEUE
}

enum PlaybackStreamKind {
	NONE,
	YOUTUBE,
	LIVE_CAMERA,
	DIRECT_VIDEO
}

enum MaxCallPhase {
	IDLE,
	OUTGOING,
	INCOMING,
	ACTIVE
}

enum MediaScaleMode {
	FIT,
	FILL,
	STRETCH;

	MediaScaleMode next() {
		MediaScaleMode[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}

enum TransportButtonKind {
	BACK,
	PLAY_PAUSE,
	FORWARD
}

enum MediaButtonSegment {
	SINGLE,
	LEFT,
	MIDDLE,
	RIGHT
}

enum MediaActionGlyph {
	TRASH,
	DOWNLOAD,
	CHECK,
	WALLPAPER
}

enum MediaActionVisualState {
	IDLE,
	DOWNLOADING,
	COMPLETE
}

enum CameraAppCaptureMode {
	PHOTO,
	VIDEO,
	AUDIO
}

enum GalleryItemKind {
	MEDIA,
	AUDIO,
	VIDEO,
	LIVE_CAMERA,
	YOUTUBE;

	static GalleryItemKind fromPersisted(String value, String url) {
		if ("audio".equalsIgnoreCase(value)) {
			return AUDIO;
		}
		if ("live_camera".equalsIgnoreCase(value)) {
			return LIVE_CAMERA;
		}
		if ("youtube".equalsIgnoreCase(value)) {
			return YOUTUBE;
		}
		if ("video".equalsIgnoreCase(value)) {
			return VIDEO;
		}
		return MonitorYoutubeRelayClient.looksLikeYoutubeUrl(url) ? YOUTUBE : MEDIA;
	}

	String persistedName() {
		return switch (this) {
			case AUDIO -> "audio";
			case LIVE_CAMERA -> "live_camera";
			case YOUTUBE -> "youtube";
			case VIDEO -> "video";
			case MEDIA -> "media";
		};
	}
}

enum TimelineCounterDetailLevel {
	NONE,
	COMPACT,
	FULL
}

record ScreenKey(BlockPos pos, Direction direction) {
}

record ScreenRuntimeKey(ResourceKey<Level> dimension, BlockPos pos, Direction facing) {
}

enum LiveCameraSourceType {
	CAMERA,
	DRONE
}

record LiveCameraReference(LiveCameraSourceType sourceType, ResourceKey<Level> dimension, BlockPos pos, UUID sourceUuid) {
	static LiveCameraReference camera(ResourceKey<Level> dimension, BlockPos pos) {
		return new LiveCameraReference(LiveCameraSourceType.CAMERA, dimension, pos == null ? null : pos.immutable(), null);
	}

	static LiveCameraReference drone(ResourceKey<Level> dimension, BlockPos pos, UUID sourceUuid) {
		return new LiveCameraReference(LiveCameraSourceType.DRONE, dimension, pos == null ? null : pos.immutable(), sourceUuid);
	}
}

record TileCoord(int x, int y) {
}

record UiPoint(int x, int y) {
}

record PlacementSurfacePoint(double u, double v) {
}

record UiRect(int x, int y, int width, int height) {
	boolean contains(int px, int py) {
		return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height;
	}

	int right() {
		return this.x + this.width;
	}

	int bottom() {
		return this.y + this.height;
	}

	UiRect inset(int amount) {
		int nextWidth = Math.max(1, this.width - amount * 2);
		int nextHeight = Math.max(1, this.height - amount * 2);
		return new UiRect(this.x + amount, this.y + amount, nextWidth, nextHeight);
	}
}

record UiLayout(
		int canvasWidth,
		int canvasHeight,
		int viewportX,
		int viewportY,
		int viewportWidth,
		int viewportHeight,
		int margin,
		int unit
) {
}

final class MonitorLevelState {
	private final ResourceKey<Level> dimension;
	private final Set<ScreenKey> knownFrames = ConcurrentHashMap.newKeySet();
	private final Map<ScreenKey, ScreenRuntimeKey> frameToRuntime = new ConcurrentHashMap<>();
	private final Map<ScreenRuntimeKey, ScreenComponent> components = new ConcurrentHashMap<>();
	private final Map<ScreenRuntimeKey, List<LiveCameraReference>> connectedCameraPositions = new ConcurrentHashMap<>();
	private final Set<ScreenKey> dirtyFramesSet = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<ScreenKey> dirtyFrames = new ConcurrentLinkedQueue<>();
	private final Set<ScreenRuntimeKey> dirtyRuntimeSet = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<ScreenRuntimeKey> dirtyRuntimes = new ConcurrentLinkedQueue<>();
	private final Set<ScreenRuntimeKey> cameraRefreshRuntimeSet = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<ScreenRuntimeKey> cameraRefreshRuntimes = new ConcurrentLinkedQueue<>();
	private final Set<ScreenRuntimeKey> powerRuntimeSet = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<ScreenRuntimeKey> powerRuntimes = new ConcurrentLinkedQueue<>();
	private final Set<ScreenRuntimeKey> speakerRefreshRuntimeSet = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<ScreenRuntimeKey> speakerRefreshRuntimes = new ConcurrentLinkedQueue<>();

	MonitorLevelState(ResourceKey<Level> dimension) {
		this.dimension = dimension;
	}

	ResourceKey<Level> dimension() {
		return this.dimension;
	}

	Set<ScreenKey> knownFrames() {
		return this.knownFrames;
	}

	Map<ScreenKey, ScreenRuntimeKey> frameToRuntime() {
		return this.frameToRuntime;
	}

	Map<ScreenRuntimeKey, ScreenComponent> components() {
		return this.components;
	}

	Map<ScreenRuntimeKey, List<LiveCameraReference>> connectedCameraPositions() {
		return this.connectedCameraPositions;
	}

	void enqueueDirtyFrame(ScreenKey key) {
		if (key != null && this.dirtyFramesSet.add(key)) {
			this.dirtyFrames.add(key);
		}
	}

	ScreenKey pollDirtyFrame() {
		ScreenKey key = this.dirtyFrames.poll();
		if (key != null) {
			this.dirtyFramesSet.remove(key);
		}
		return key;
	}

	void enqueueDirtyRuntime(ScreenRuntimeKey key) {
		if (key != null && this.dirtyRuntimeSet.add(key)) {
			this.dirtyRuntimes.add(key);
		}
	}

	ScreenRuntimeKey pollDirtyRuntime() {
		ScreenRuntimeKey key = this.dirtyRuntimes.poll();
		if (key != null) {
			this.dirtyRuntimeSet.remove(key);
		}
		return key;
	}

	void enqueueCameraRefreshRuntime(ScreenRuntimeKey key) {
		if (key != null && this.cameraRefreshRuntimeSet.add(key)) {
			this.cameraRefreshRuntimes.add(key);
		}
	}

	ScreenRuntimeKey pollCameraRefreshRuntime() {
		ScreenRuntimeKey key = this.cameraRefreshRuntimes.poll();
		if (key != null) {
			this.cameraRefreshRuntimeSet.remove(key);
		}
		return key;
	}

	void enqueuePowerRuntime(ScreenRuntimeKey key) {
		if (key != null && this.powerRuntimeSet.add(key)) {
			this.powerRuntimes.add(key);
		}
	}

	void enqueueSpeakerRefreshRuntime(ScreenRuntimeKey key) {
		if (key != null && this.speakerRefreshRuntimeSet.add(key)) {
			this.speakerRefreshRuntimes.add(key);
		}
	}

	ScreenRuntimeKey pollPowerRuntime() {
		ScreenRuntimeKey key = this.powerRuntimes.poll();
		if (key != null) {
			this.powerRuntimeSet.remove(key);
		}
		return key;
	}

	ScreenRuntimeKey pollSpeakerRefreshRuntime() {
		ScreenRuntimeKey key = this.speakerRefreshRuntimes.poll();
		if (key != null) {
			this.speakerRefreshRuntimeSet.remove(key);
		}
		return key;
	}
}

record ScreenComponent(
		ScreenRuntimeKey runtimeKey,
		Direction facing,
		Direction right,
		int width,
		int height,
		boolean powered,
		ScreenViewMode viewMode,
		int launcherPage,
		Map<ItemFrame, TileCoord> frameCoords,
		Map<TileCoord, ScreenFrame> byCoord
) {
}

record ScreenFrame(ItemFrame frame, ScreenTileState state) {
}

record ScreenTileState(
		int attachmentMask,
		int gridWidth,
		int gridHeight,
		int tileX,
		int tileY,
		int connectionMask,
		boolean powered,
		ScreenViewMode viewMode,
		int launcherPage,
		String groupId
) {
	boolean sameRenderState(ScreenTileState other) {
		return other != null
				&& this.gridWidth == other.gridWidth
				&& this.gridHeight == other.gridHeight
				&& this.tileX == other.tileX
				&& this.tileY == other.tileY
				&& this.powered == other.powered
				&& this.viewMode == other.viewMode
				&& this.launcherPage == other.launcherPage;
	}
}
