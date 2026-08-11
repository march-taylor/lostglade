package com.lostglade.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

public final class MonitorScreenTouchProjectionTest {
	private MonitorScreenTouchProjectionTest() {
	}

	public static void main(String[] args) {
		screenUseEventsRequireMainHandPreciseHit();
		angledRayProjectsToScreenPlane();
		actualHitPointOverridesStaleLookAngle();
		allHorizontalFacingsProjectConsistently();
		multiTileOffsetIsPreserved();
		mediaCanvasRespectsViewportSafeInset();
		launcherUses2x2GridOnSmallestScreen();
		mediaTimelineWidthTracksVisibleButtons();
		timelineCounterWidthTracksLabelLength();
		compactMusicPlayerKeepsControlsOnScreen();
		compactVideoTimelineStaysThinAndReachable();
		conditionalPlayerActionsStayCentered();
		System.out.println("Monitor screen touch projection checks passed");
	}

	private static void screenUseEventsRequireMainHandPreciseHit() {
		require(MonitorScreenSystem.shouldHandleScreenUse(InteractionHand.MAIN_HAND, true), "main-hand precise hits should drive monitor touches");
		require(!MonitorScreenSystem.shouldHandleScreenUse(InteractionHand.OFF_HAND, true), "offhand should not replay monitor touches");
		require(!MonitorScreenSystem.shouldHandleScreenUse(InteractionHand.MAIN_HAND, false), "generic entity interact should not replay monitor touches");
	}

	private static void angledRayProjectsToScreenPlane() {
		BlockPos framePos = new BlockPos(10, 64, 10);
		Direction facing = Direction.EAST;
		TileCoord tile = new TileCoord(0, 0);
		double u = 0.50D;
		double v = 0.25D;
		Vec3 target = screenPlanePoint(framePos, facing, u, v);
		Vec3 eye = target.add(2.5D, -1.0D, 0.35D);
		Vec3 ray = target.subtract(eye).normalize();

		UiPoint projected = MonitorScreenSystem.screenTouchPoint(framePos, facing, eye, ray, tile, 1, 1, 6.0D);
		require(projected != null, "angled ray must intersect the monitor plane");
		require(projected.equals(expectedPoint(tile, u, v, 1, 1)), "angled ray must resolve to the intended screen pixel");

		Vec3 hitboxPointInFrontOfPlane = target.subtract(ray.scale(0.08D));
		UiPoint hitboxPoint = MonitorScreenSystem.screenTouchPoint(framePos, facing, hitboxPointInFrontOfPlane, tile, 1, 1);
		require(
				hitboxPoint != null && !hitboxPoint.equals(projected),
				"synthetic hitbox point must demonstrate the parallax that ray-plane projection removes"
		);
	}

	private static void allHorizontalFacingsProjectConsistently() {
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			BlockPos framePos = new BlockPos(3, 72, -5);
			TileCoord tile = new TileCoord(0, 0);
			double u = 0.50D;
			double v = 0.50D;
			Vec3 target = screenPlanePoint(framePos, facing, u, v);
			Vec3 eye = target.add(new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()).scale(3.0D)).add(0.15D, -0.65D, -0.2D);
			Vec3 ray = target.subtract(eye).normalize();

			UiPoint projected = MonitorScreenSystem.screenTouchPoint(framePos, facing, eye, ray, tile, 1, 1, 6.0D);
			require(projected != null, "ray must intersect " + facing + " monitor plane");
			require(projected.equals(expectedPoint(tile, u, v, 1, 1)), facing + " monitor must resolve center consistently");
		}
	}

	private static void actualHitPointOverridesStaleLookAngle() {
		BlockPos framePos = new BlockPos(10, 64, 10);
		Direction facing = Direction.SOUTH;
		TileCoord tile = new TileCoord(0, 0);
		Vec3 target = screenPlanePoint(framePos, facing, 0.88D, 0.12D);
		Vec3 eye = target.add(0.42D, -0.16D, 2.6D);
		Vec3 staleLook = screenPlanePoint(framePos, facing, 0.12D, 0.84D).subtract(eye).normalize();
		Vec3 eventRay = target.subtract(eye).normalize();
		Vec3 hitboxPointInFrontOfPlane = target.subtract(eventRay.scale(0.08D));

		UiPoint staleProjected = MonitorScreenSystem.screenTouchPoint(framePos, facing, eye, staleLook, tile, 1, 1, 6.0D);
		UiPoint eventProjected = MonitorScreenSystem.screenTouchPoint(framePos, facing, eye, hitboxPointInFrontOfPlane.subtract(eye).normalize(), tile, 1, 1, 6.0D);
		require(staleProjected != null && !staleProjected.equals(expectedPoint(tile, 0.88D, 0.12D, 1, 1)), "stale look angle should miss the intended small control");
		require(eventProjected != null && eventProjected.equals(expectedPoint(tile, 0.88D, 0.12D, 1, 1)), "event hit ray should resolve the intended small control");
	}

	private static void multiTileOffsetIsPreserved() {
		BlockPos framePos = new BlockPos(-4, 80, 12);
		TileCoord tile = new TileCoord(1, 2);
		UiPoint point = MonitorScreenSystem.screenTouchPoint(
				framePos,
				Direction.SOUTH,
				screenPlanePoint(framePos, Direction.SOUTH, 0.1D, 0.9D),
				tile,
				3,
				3
		);
		require(point != null, "direct plane hit must produce a screen point");
		require(point.equals(expectedPoint(tile, 0.1D, 0.9D, 3, 3)), "tile offset must be included in the final screen coordinate");
	}

	private static void mediaCanvasRespectsViewportSafeInset() {
		UiLayout layout = MonitorScreenSystem.createUiLayout(1, 1);
		UiRect canvas = MonitorScreenSystem.mediaCanvasRect(layout);
		require(layout.viewportX() == MonitorScreenSystem.MONITOR_VIEWPORT_SAFE_INSET, "small screens should reserve bezel-safe horizontal padding");
		require(layout.viewportY() == MonitorScreenSystem.MONITOR_VIEWPORT_SAFE_INSET, "small screens should reserve bezel-safe vertical padding");
		require(canvas.x() == layout.viewportX() && canvas.y() == layout.viewportY(), "media canvas should start inside the safe viewport");
		require(canvas.width() == layout.viewportWidth() && canvas.height() == layout.viewportHeight(), "media canvas should match the safe viewport size");
	}

	private static void launcherUses2x2GridOnSmallestScreen() {
		UiLayout layout = MonitorScreenSystem.createUiLayout(1, 1);
		require(MonitorScreenSystem.homeColumns(layout) == 2, "1x1 launcher must retain two columns");
		require(MonitorScreenSystem.homeRowsPerPage(layout) == 2, "1x1 launcher must fit two rows");
		require(MonitorScreenSystem.homePageCapacity(layout) >= 4, "1x1 launcher must show four apps without scrolling");

		UiRect card = MonitorScreenSystem.homeAppCardRect(layout, 0, 0);
		UiRect icon = MonitorScreenSystem.homeAppIconRect(card, layout);
		UiRect label = MonitorScreenSystem.homeAppLabelRect(layout, card);
		require(label.y() - icon.bottom() <= 2, "launcher labels must stay close to their icons on 1x1 screens");
	}

	private static void mediaTimelineWidthTracksVisibleButtons() {
		UiLayout layout = MonitorScreenSystem.createUiLayout(2, 1);
		UiRect scaleOnly = MonitorScreenSystem.mediaTimelineRect(layout, ScreenViewMode.YOUTUBE, false, false, false, false, true);
		UiRect repeatAndScale = MonitorScreenSystem.mediaTimelineRect(layout, ScreenViewMode.YOUTUBE, false, false, false, true, true);
		UiRect noButtons = MonitorScreenSystem.mediaTimelineRect(layout, ScreenViewMode.YOUTUBE, false, false, false, false, false);

		require(
				repeatAndScale.width() < scaleOnly.width(),
				"adding the repeat button should reduce the visible timeline width instead of leaving stale empty space"
		);
		require(
				noButtons.width() > scaleOnly.width(),
				"removing right-side buttons should give the timeline that space back"
		);
	}

	private static void timelineCounterWidthTracksLabelLength() {
		UiLayout layout = MonitorScreenSystem.createUiLayout(3, 2);
		int shortLabelWidth = MonitorScreenSystem.timelineCounterReservedWidth(layout, "3/20");
		int longLabelWidth = MonitorScreenSystem.timelineCounterReservedWidth(layout, "10:00 / 120:00");
		require(
				shortLabelWidth < longLabelWidth,
				"timeline counter width should shrink for compact frame labels instead of reserving the full video timer width"
		);
	}

	private static void compactMusicPlayerKeepsControlsOnScreen() {
		UiLayout square = MonitorScreenSystem.createUiLayout(1, 1);
		UiLayout portrait = MonitorScreenSystem.createUiLayout(1, 2);
		UiLayout landscape = MonitorScreenSystem.createUiLayout(2, 1);

		require(!MonitorScreenSystem.youtubeMusicLandscapeLayout(square), "1x1 music player must use the micro layout");
		require(MonitorScreenSystem.youtubeMusicMicroLayout(square), "1x1 music player must use the dense micro layout");
		require(!MonitorScreenSystem.youtubeMusicLandscapeLayout(portrait), "1x2 music player must remain vertical");
		require(MonitorScreenSystem.youtubeMusicLandscapeLayout(landscape), "2x1 music player must use the horizontal layout");

		for (UiLayout layout : new UiLayout[]{square, portrait, landscape}) {
			UiRect canvas = MonitorScreenSystem.mediaCanvasRect(layout);
			UiRect artwork = MonitorScreenSystem.mediaYoutubeMusicArtworkRect(layout);
			UiRect info = MonitorScreenSystem.mediaYoutubeMusicInfoRect(layout);
			UiRect timeline = MonitorScreenSystem.mediaTimelineRect(layout, ScreenViewMode.YOUTUBE_MUSIC);
			UiRect controls = MonitorScreenSystem.mediaYoutubeMusicControlsRowRect(layout);
			UiRect actions = MonitorScreenSystem.mediaYoutubeMusicActionsRowRect(layout);

			require(contains(canvas, artwork), "music artwork must remain on screen");
			require(
					Math.abs((artwork.y() - canvas.y()) - (canvas.bottom() - actions.bottom())) <= 1,
					"music player must keep equal top and bottom insets"
			);
			require(contains(canvas, info), "track metadata must remain on screen");
			require(contains(canvas, timeline), "music timeline must remain on screen");
			require(contains(canvas, controls), "music transport controls must remain on screen");
			require(contains(canvas, actions), "music action controls must remain on screen");
			require(
					contains(canvas, MonitorScreenSystem.mediaCenterPlayPauseRect(layout, ScreenViewMode.YOUTUBE_MUSIC)),
					"music pause button must remain on screen"
			);
			UiRect musicPlayPause = MonitorScreenSystem.mediaCenterPlayPauseRect(layout, ScreenViewMode.YOUTUBE_MUSIC);
			UiRect musicBack = MonitorScreenSystem.mediaCenterBackRect(layout, ScreenViewMode.YOUTUBE_MUSIC);
			UiRect musicForward = MonitorScreenSystem.mediaCenterForwardRect(layout, ScreenViewMode.YOUTUBE_MUSIC);
			require(musicPlayPause.width() >= musicPlayPause.height(), "music pause button must never be vertically squeezed");
			require(
					musicBack.height() == musicPlayPause.height() && musicForward.height() == musicPlayPause.height(),
					"music transport controls must keep one shared height"
			);
			require(
					contains(canvas, MonitorScreenSystem.mediaYoutubeMusicDownloadRect(layout)),
					"music download button must remain on screen"
			);
			require(
					MonitorScreenSystem.mediaTimelineTrackRect(layout, ScreenViewMode.YOUTUBE_MUSIC).height() <= 2,
					"music timeline must stay one or two pixels thick"
			);
		}
	}

	private static void compactVideoTimelineStaysThinAndReachable() {
		UiLayout layout = MonitorScreenSystem.createUiLayout(1, 1);
		UiRect canvas = MonitorScreenSystem.mediaCanvasRect(layout);
		UiRect track = MonitorScreenSystem.mediaTimelineTrackRect(layout, ScreenViewMode.YOUTUBE);
		UiRect playPause = MonitorScreenSystem.mediaCenterPlayPauseRect(layout, ScreenViewMode.YOUTUBE);
		require(track.height() <= 2, "video timeline must stay one or two pixels thick");
		require(contains(canvas, track), "video timeline must remain on screen");
		require(contains(canvas, playPause), "video pause button must remain on screen");

		UiLayout compact = MonitorScreenSystem.createUiLayout(2, 2);
		UiLayout standard = MonitorScreenSystem.createUiLayout(3, 3);
		require(compact.unit() == 7, "2x2 layout should be only slightly denser than the original scale");
		require(standard.unit() == 10, "3x3 layout should keep the original scale");
		require(standard.margin() == 8, "3x3 layout should keep the original margins");
		UiRect standardMusicPlayPause = MonitorScreenSystem.mediaCenterPlayPauseRect(standard, ScreenViewMode.YOUTUBE_MUSIC);
		require(standardMusicPlayPause.width() > standardMusicPlayPause.height(), "large music pause button should use available horizontal space");
		require(
				MonitorScreenSystem.mediaCenterBackRect(standard, ScreenViewMode.YOUTUBE_MUSIC).height() == standardMusicPlayPause.height()
						&& MonitorScreenSystem.mediaCenterForwardRect(standard, ScreenViewMode.YOUTUBE_MUSIC).height() == standardMusicPlayPause.height(),
				"large music transport controls must keep one shared height"
		);
	}

	private static void conditionalPlayerActionsStayCentered() {
		UiLayout layout = MonitorScreenSystem.createUiLayout(1, 1);
		UiRect row = MonitorScreenSystem.mediaYoutubeMusicActionsRowRect(layout);
		UiRect galleryDelete = MonitorScreenSystem.mediaGalleryMusicPrimaryActionRect(layout, false);
		require(centerDistance(row, galleryDelete) <= 1, "a lone gallery delete button must be centered");

		UiRect youtubeSearch = MonitorScreenSystem.mediaYoutubeMusicSearchRect(layout, false);
		UiRect youtubeQueue = MonitorScreenSystem.mediaYoutubeMusicQueueRect(layout, false);
		require(
					Math.abs((youtubeSearch.x() + youtubeQueue.right()) - (row.x() + row.right())) <= 1,
					"the four YouTube Music actions must be centered when download is unavailable"
		);

		UiRect fileMenu = MonitorScreenSystem.galleryFileMenuBodyRect(layout);
		UiRect finalFourAction = MonitorScreenSystem.galleryFileMenuActionRect(layout, 3, 4);
		require(contains(fileMenu, finalFourAction), "the four-action file menu must fit its panel");
	}

	private static int centerDistance(UiRect left, UiRect right) {
		return Math.abs((left.x() + left.right()) - (right.x() + right.right()));
	}

	private static boolean contains(UiRect outer, UiRect inner) {
		return inner.x() >= outer.x()
				&& inner.y() >= outer.y()
				&& inner.right() <= outer.right()
				&& inner.bottom() <= outer.bottom();
	}

	private static Vec3 screenPlanePoint(BlockPos framePos, Direction facing, double u, double v) {
		Vec3 center = MonitorScreenSystem.screenPlaneCenter(framePos, facing);
		double localY = 1.0D - v;
		return switch (facing) {
			case SOUTH -> new Vec3(framePos.getX() + u, framePos.getY() + localY, center.z);
			case NORTH -> new Vec3(framePos.getX() + 1.0D - u, framePos.getY() + localY, center.z);
			case EAST -> new Vec3(center.x, framePos.getY() + localY, framePos.getZ() + 1.0D - u);
			case WEST -> new Vec3(center.x, framePos.getY() + localY, framePos.getZ() + u);
			default -> throw new IllegalArgumentException("Unsupported facing " + facing);
		};
	}

	private static UiPoint expectedPoint(TileCoord tile, double u, double v, int gridWidth, int gridHeight) {
		int x = tile.x() * MonitorScreenSystem.MAP_SIZE + (int) Math.floor(u * (MonitorScreenSystem.MAP_SIZE - 1));
		int y = tile.y() * MonitorScreenSystem.MAP_SIZE + (int) Math.floor(v * (MonitorScreenSystem.MAP_SIZE - 1));
		int maxX = Math.max(0, gridWidth * MonitorScreenSystem.MAP_SIZE - 1);
		int maxY = Math.max(0, gridHeight * MonitorScreenSystem.MAP_SIZE - 1);
		return new UiPoint(MonitorScreenSystem.clampInt(x, 0, maxX), MonitorScreenSystem.clampInt(y, 0, maxY));
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
