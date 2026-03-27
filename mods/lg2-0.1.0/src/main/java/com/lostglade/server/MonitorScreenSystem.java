package com.lostglade.server;

import com.lostglade.item.ModItems;
import com.lostglade.item.MonitorItem;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
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
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MonitorScreenSystem {
	private static final String SCREEN_ROOT_TAG = "lg2_monitor_screen";
	private static final String LINK_LOCKED_TAG = "link_locked";
	private static final String GRID_WIDTH_TAG = "grid_width";
	private static final String GRID_HEIGHT_TAG = "grid_height";
	private static final String TILE_X_TAG = "tile_x";
	private static final String TILE_Y_TAG = "tile_y";
	private static final String CONNECTION_MASK_TAG = "connection_mask";
	private static final String POWERED_TAG = "powered";
	private static final String VIEW_MODE_TAG = "view_mode";
	private static final String DISPLAY_ROOT_TAG = "lg2_monitor_display";
	private static final String POS_TAG_PREFIX = "lg2_monitor_display_pos:";
	private static final String FACING_TAG_PREFIX = "lg2_monitor_display_facing:";
	private static final int MAP_SIZE = 128;
	private static final int PHOTO_MAP_CENTER = 30_000_000;
	private static final int CONNECTION_LEFT = 1;
	private static final int CONNECTION_RIGHT = 2;
	private static final int CONNECTION_UP = 4;
	private static final int CONNECTION_DOWN = 8;
	private static final int MAX_UI_TILES = 2;
	private static final long RESCAN_INTERVAL_TICKS = 4L;
	private static final double DISPLAY_SEARCH_RADIUS = 0.8D;
	private static final double DISPLAY_PLANE_OFFSET = 0.4453125D;
	private static final double TOUCH_TOLERANCE = 0.08D;
	private static final String SCREEN_OFF_RESOURCE = "/assets/lg2/textures/monitor/screen_off.png";
	private static final String SCREEN_ON_RESOURCE = "/assets/lg2/textures/monitor/screen_on.png";
	private static final Map<RenderCacheKey, byte[][]> TILE_CACHE = new HashMap<>();
	private static BufferedImage offBaseImage;
	private static BufferedImage onBaseImage;

	private MonitorScreenSystem() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(MonitorScreenSystem::onUseEntity);
		ServerTickEvents.END_SERVER_TICK.register(MonitorScreenSystem::tick);
	}

	public static InteractionResult tryPlaceScreen(UseOnContext context) {
		if (context == null) {
			return InteractionResult.PASS;
		}
		if (context.getLevel().isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.PASS;
		}

		Direction facing = context.getClickedFace();
		if (facing == null || !facing.getAxis().isHorizontal()) {
			player.displayClientMessage(wallOnlyMessage(player), true);
			return InteractionResult.FAIL;
		}

		BlockPos framePos = context.getClickedPos().relative(facing);
		if (!level.getBlockState(framePos).canBeReplaced() || findAnyFrame(level, framePos, facing) != null) {
			player.displayClientMessage(occupiedMessage(player), true);
			return InteractionResult.FAIL;
		}

		GlowItemFrame frame = new GlowItemFrame(level, framePos, facing);
		if (!frame.survives()) {
			player.displayClientMessage(wallOnlyMessage(player), true);
			return InteractionResult.FAIL;
		}

		boolean linkLocked = player.isShiftKeyDown();
		ItemStack frameMap = createScreenMap(level, new ScreenTileState(
				linkLocked,
				1,
				1,
				0,
				0,
				0,
				false,
				ScreenViewMode.HOME
		));
		if (frameMap.isEmpty()) {
			return InteractionResult.FAIL;
		}

		frame.setInvisible(true);
		frame.setSilent(true);
		frame.setItem(frameMap, false);
		frame.setRotation(0);
		level.addFreshEntity(frame);

		synchronizeConnectedScreens(level, frame, null, null);
		if (!player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	public static boolean onFrameBroken(ServerLevel level, ItemFrame frame, Entity breaker, boolean shouldDropScreen) {
		if (level == null || frame == null || readScreenState(frame.getItem()) == null) {
			return false;
		}

		removeDisplays(level, frame.blockPosition(), frame.getDirection());
		frame.setItem(ItemStack.EMPTY, false);
		if (shouldDropScreen) {
			frame.spawnAtLocation(level, new ItemStack(ModItems.MONITOR));
		}
		synchronizeNeighborComponents(level, frame.blockPosition(), frame.getDirection());
		return true;
	}

	private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult) {
		if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level) || !(entity instanceof ItemFrame itemFrame)) {
			return InteractionResult.PASS;
		}
		if (readScreenState(itemFrame.getItem()) == null) {
			return InteractionResult.PASS;
		}
		return handleTouch(serverPlayer, level, itemFrame, hitResult);
	}

	private static InteractionResult handleTouch(ServerPlayer player, ServerLevel level, ItemFrame frame, EntityHitResult hitResult) {
		ScreenComponent component = collectComponent(level, frame, null);
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
		ScreenViewMode nextMode = null;
		if (component.viewMode() == ScreenViewMode.HOME) {
			UiRect appRect = homeAppRect(layout);
			if (appRect.contains(touchPoint.x(), touchPoint.y())) {
				nextMode = ScreenViewMode.MEDIA;
			}
		} else if (component.viewMode() == ScreenViewMode.MEDIA) {
			UiRect backRect = mediaBackRect(layout);
			if (backRect.contains(touchPoint.x(), touchPoint.y())) {
				nextMode = ScreenViewMode.HOME;
			}
		}

		if (nextMode != null && nextMode != component.viewMode()) {
			synchronizeConnectedScreens(level, frame, null, nextMode);
		}
		return InteractionResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getGameTime() % RESCAN_INTERVAL_TICKS != 0L) {
				continue;
			}
			Set<ScreenKey> processed = new HashSet<>();
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof ItemFrame frame)) {
					continue;
				}
				if (readScreenState(frame.getItem()) == null) {
					continue;
				}
				ScreenKey key = new ScreenKey(frame.blockPosition(), frame.getDirection());
				if (processed.contains(key)) {
					continue;
				}
				synchronizeConnectedScreens(level, frame, processed, null);
			}
			cleanupOrphanDisplays(level);
		}
	}

	private static void synchronizeConnectedScreens(ServerLevel level, ItemFrame startFrame, Set<ScreenKey> processedKeys) {
		synchronizeConnectedScreens(level, startFrame, processedKeys, null);
	}

	private static void synchronizeConnectedScreens(ServerLevel level, ItemFrame startFrame, Set<ScreenKey> processedKeys, ScreenViewMode forcedViewMode) {
		ScreenComponent component = collectComponent(level, startFrame, processedKeys);
		if (component == null) {
			return;
		}

		ScreenViewMode viewMode = forcedViewMode != null ? forcedViewMode : component.viewMode();
		ServerLevel mapLevel = photoMapLevel(level.getServer(), level);
		boolean rerenderMaps = false;

		for (Map.Entry<ItemFrame, TileCoord> entry : component.frameCoords().entrySet()) {
			ItemFrame frame = entry.getKey();
			TileCoord tileCoord = entry.getValue();
			ScreenFrame screenFrame = component.byCoord().get(tileCoord);
			if (screenFrame == null) {
				continue;
			}

			ItemStack existing = frame.getItem();
			MapId existingMapId = existing.get(DataComponents.MAP_ID);
			boolean missingMap = existingMapId == null || mapLevel.getMapData(existingMapId) == null;
			ItemStack ensured = ensureMap(level, frame, screenFrame.state());
			ScreenTileState currentState = readScreenState(ensured);
			if (currentState == null) {
				continue;
			}

			int connectionMask = connectionMask(component.byCoord(), tileCoord.x(), tileCoord.y());
			ScreenTileState updatedState = new ScreenTileState(
					currentState.linkLocked(),
					component.width(),
					component.height(),
					tileCoord.x(),
					tileCoord.y(),
					connectionMask,
					component.powered(),
					viewMode
			);

			if (missingMap || !currentState.sameRenderState(updatedState)) {
				rerenderMaps = true;
			}
			if (!currentState.equals(updatedState)) {
				ItemStack updated = ensured.copy();
				writeScreenState(updated, updatedState);
				frame.setItem(updated, false);
			}
			ensureDisplay(level, frame, connectionMask);
		}

		if (!rerenderMaps) {
			return;
		}

		byte[][] renderedTiles = renderTiles(component.powered(), viewMode, component.width(), component.height());
		ServerLevel mapStorageLevel = photoMapLevel(level.getServer(), level);
		for (Map.Entry<ItemFrame, TileCoord> entry : component.frameCoords().entrySet()) {
			ItemFrame frame = entry.getKey();
			ItemStack frameStack = frame.getItem();
			ScreenTileState state = readScreenState(frameStack);
			MapId mapId = frameStack.get(DataComponents.MAP_ID);
			if (state == null || mapId == null) {
				continue;
			}
			MapItemSavedData mapData = mapStorageLevel.getMapData(mapId);
			if (mapData == null) {
				continue;
			}
			int tileIndex = state.tileY() * component.width() + state.tileX();
			if (tileIndex < 0 || tileIndex >= renderedTiles.length) {
				continue;
			}
			applyFrameToMap(mapData, renderedTiles[tileIndex]);
			sendMapToPlayers(level, mapId, mapData);
		}
	}

	private static ScreenComponent collectComponent(ServerLevel level, ItemFrame startFrame, Set<ScreenKey> processedKeys) {
		if (level == null || startFrame == null) {
			return null;
		}
		ScreenTileState startState = readScreenState(startFrame.getItem());
		if (startState == null) {
			return null;
		}

		Direction facing = startFrame.getDirection();
		Direction right = frameRight(facing);
		Map<ScreenKey, ScreenFrame> frames = new HashMap<>();
		ArrayDeque<ItemFrame> queue = new ArrayDeque<>();
		queue.add(startFrame);

		while (!queue.isEmpty()) {
			ItemFrame frame = queue.removeFirst();
			ScreenTileState state = readScreenState(frame.getItem());
			if (state == null || frame.getDirection() != facing) {
				continue;
			}

			ScreenKey key = new ScreenKey(frame.blockPosition(), facing);
			if (frames.containsKey(key)) {
				continue;
			}
			frames.put(key, new ScreenFrame(frame, state));
			if (state.linkLocked()) {
				continue;
			}

			for (BlockPos neighborPos : List.of(
					frame.blockPosition().relative(right),
					frame.blockPosition().relative(right.getOpposite()),
					frame.blockPosition().above(),
					frame.blockPosition().below()
			)) {
				ItemFrame neighbor = findScreenFrame(level, neighborPos, facing);
				if (neighbor == null) {
					continue;
				}
				ScreenTileState neighborState = readScreenState(neighbor.getItem());
				if (neighborState == null || neighborState.linkLocked()) {
					continue;
				}
				queue.add(neighbor);
			}
		}

		if (processedKeys != null) {
			processedKeys.addAll(frames.keySet());
		}
		if (frames.isEmpty()) {
			return null;
		}

		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		Map<ScreenFrame, TileCoord> localCoords = new HashMap<>();
		for (ScreenFrame screenFrame : frames.values()) {
			BlockPos pos = screenFrame.frame().blockPosition();
			int localX = pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
			int localY = pos.getY();
			localCoords.put(screenFrame, new TileCoord(localX, localY));
			minX = Math.min(minX, localX);
			maxX = Math.max(maxX, localX);
			minY = Math.min(minY, localY);
			maxY = Math.max(maxY, localY);
		}

		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		boolean powered = frames.values().stream().anyMatch(screenFrame -> isPowered(level, screenFrame.frame()));
		ScreenViewMode viewMode = frames.values().stream()
				.map(ScreenFrame::state)
				.map(ScreenTileState::viewMode)
				.max(ScreenViewMode::compareTo)
				.orElse(ScreenViewMode.HOME);

		Map<ItemFrame, TileCoord> frameCoords = new HashMap<>();
		Map<TileCoord, ScreenFrame> byCoord = new HashMap<>();
		for (Map.Entry<ScreenFrame, TileCoord> entry : localCoords.entrySet()) {
			int tileX = entry.getValue().x() - minX;
			int tileY = maxY - entry.getValue().y();
			TileCoord tileCoord = new TileCoord(tileX, tileY);
			frameCoords.put(entry.getKey().frame(), tileCoord);
			byCoord.put(tileCoord, entry.getKey());
		}
		return new ScreenComponent(facing, right, width, height, powered, viewMode, frameCoords, byCoord);
	}

	private static void synchronizeNeighborComponents(ServerLevel level, BlockPos pos, Direction facing) {
		if (level == null || pos == null || facing == null) {
			return;
		}
		Direction right = frameRight(facing);
		Set<ScreenKey> processed = new HashSet<>();
		for (BlockPos candidatePos : List.of(
				pos.relative(right),
				pos.relative(right.getOpposite()),
				pos.above(),
				pos.below()
		)) {
			ItemFrame frame = findScreenFrame(level, candidatePos, facing);
			if (frame == null) {
				continue;
			}
			ScreenKey key = new ScreenKey(frame.blockPosition(), frame.getDirection());
			if (processed.contains(key)) {
				continue;
			}
			synchronizeConnectedScreens(level, frame, processed, null);
		}
	}

	private static int connectionMask(Map<TileCoord, ScreenFrame> byCoord, int tileX, int tileY) {
		int mask = 0;
		if (byCoord.containsKey(new TileCoord(tileX - 1, tileY))) {
			mask |= CONNECTION_LEFT;
		}
		if (byCoord.containsKey(new TileCoord(tileX + 1, tileY))) {
			mask |= CONNECTION_RIGHT;
		}
		if (byCoord.containsKey(new TileCoord(tileX, tileY - 1))) {
			mask |= CONNECTION_UP;
		}
		if (byCoord.containsKey(new TileCoord(tileX, tileY + 1))) {
			mask |= CONNECTION_DOWN;
		}
		return mask;
	}

	private static ItemStack ensureMap(ServerLevel level, ItemFrame frame, ScreenTileState state) {
		ItemStack stack = frame.getItem();
		MapId mapId = stack.get(DataComponents.MAP_ID);
		ServerLevel mapLevel = photoMapLevel(level.getServer(), level);
		if (mapId != null && mapLevel.getMapData(mapId) != null) {
			return stack;
		}

		ItemStack replacement = createScreenMap(level, state);
		if (!replacement.isEmpty()) {
			frame.setItem(replacement, false);
			return replacement;
		}
		return stack;
	}

	private static ItemStack createScreenMap(ServerLevel level, ScreenTileState state) {
		ServerLevel mapLevel = photoMapLevel(level.getServer(), level);
		ItemStack generated = MapItem.create(mapLevel, PHOTO_MAP_CENTER, PHOTO_MAP_CENTER, (byte) 0, false, false);
		MapId mapId = generated.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return ItemStack.EMPTY;
		}

		ItemStack screenMap = new ItemStack(Items.FILLED_MAP);
		screenMap.set(DataComponents.MAP_ID, mapId);
		writeScreenState(screenMap, state);
		MapItemSavedData mapData = mapLevel.getMapData(mapId);
		if (mapData != null) {
			byte[][] tiles = renderTiles(state.powered(), state.viewMode(), 1, 1);
			applyFrameToMap(mapData, tiles[0]);
		}
		return screenMap;
	}

	private static void writeScreenState(ItemStack stack, ScreenTileState state) {
		if (stack == null || stack.isEmpty() || state == null) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag screenTag = tag.getCompoundOrEmpty(SCREEN_ROOT_TAG);
			screenTag.putBoolean(LINK_LOCKED_TAG, state.linkLocked());
			screenTag.putInt(GRID_WIDTH_TAG, state.gridWidth());
			screenTag.putInt(GRID_HEIGHT_TAG, state.gridHeight());
			screenTag.putInt(TILE_X_TAG, state.tileX());
			screenTag.putInt(TILE_Y_TAG, state.tileY());
			screenTag.putInt(CONNECTION_MASK_TAG, state.connectionMask() & 0xF);
			screenTag.putBoolean(POWERED_TAG, state.powered());
			screenTag.putString(VIEW_MODE_TAG, state.viewMode().serializedName());
			tag.put(SCREEN_ROOT_TAG, screenTag);
		});
	}

	private static ScreenTileState readScreenState(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		CompoundTag root = customData.copyTag();
		if (!root.contains(SCREEN_ROOT_TAG)) {
			return null;
		}
		CompoundTag screenTag = root.getCompoundOrEmpty(SCREEN_ROOT_TAG);
		return new ScreenTileState(
				screenTag.getBooleanOr(LINK_LOCKED_TAG, false),
				Math.max(1, screenTag.getIntOr(GRID_WIDTH_TAG, 1)),
				Math.max(1, screenTag.getIntOr(GRID_HEIGHT_TAG, 1)),
				Math.max(0, screenTag.getIntOr(TILE_X_TAG, 0)),
				Math.max(0, screenTag.getIntOr(TILE_Y_TAG, 0)),
				screenTag.getIntOr(CONNECTION_MASK_TAG, 0) & 0xF,
				screenTag.getBooleanOr(POWERED_TAG, false),
				ScreenViewMode.fromTag(screenTag.getStringOr(VIEW_MODE_TAG, ScreenViewMode.HOME.serializedName()))
		);
	}

	private static boolean isPowered(ServerLevel level, ItemFrame frame) {
		BlockPos supportPos = frame.blockPosition().relative(frame.getDirection().getOpposite());
		return level.hasNeighborSignal(supportPos)
				|| level.getBestNeighborSignal(supportPos) > 0
				|| level.hasNeighborSignal(frame.blockPosition());
	}

	private static byte[][] renderTiles(boolean powered, ScreenViewMode viewMode, int width, int height) {
		RenderCacheKey key = new RenderCacheKey(powered, viewMode, width, height);
		byte[][] cached = TILE_CACHE.get(key);
		if (cached != null) {
			return cached;
		}

		BufferedImage canvas = new BufferedImage(width * MAP_SIZE, height * MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		configureUiGraphics(graphics);
		drawBaseBackground(graphics, width, height, powered);
		if (powered) {
			UiLayout layout = createUiLayout(width, height);
			if (viewMode == ScreenViewMode.MEDIA) {
				drawMediaScreen(graphics, layout);
			} else {
				drawHomeScreen(graphics, layout);
			}
		}
		graphics.dispose();

		byte[][] tiles = new byte[width * height][MAP_SIZE * MAP_SIZE];
		for (int tileY = 0; tileY < height; tileY++) {
			for (int tileX = 0; tileX < width; tileX++) {
				byte[] tile = tiles[tileY * width + tileX];
				for (int localY = 0; localY < MAP_SIZE; localY++) {
					for (int localX = 0; localX < MAP_SIZE; localX++) {
						int globalX = tileX * MAP_SIZE + localX;
						int globalY = tileY * MAP_SIZE + localY;
						int rgb = canvas.getRGB(globalX, globalY) & 0xFFFFFF;
						tile[localY * MAP_SIZE + localX] = MapPaletteQuantizer.quantizeDithered(rgb, globalX, globalY);
					}
				}
			}
		}

		TILE_CACHE.put(key, tiles);
		return tiles;
	}

	private static void configureUiGraphics(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private static void drawBaseBackground(Graphics2D graphics, int width, int height, boolean powered) {
		BufferedImage base = powered ? loadOnBaseImage() : loadOffBaseImage();
		int pixelWidth = width * MAP_SIZE;
		int pixelHeight = height * MAP_SIZE;
		graphics.drawImage(base, 0, 0, pixelWidth, pixelHeight, null);

		if (powered) {
			graphics.setPaint(new GradientPaint(
					0.0F,
					0.0F,
					new Color(255, 255, 255, 44),
					0.0F,
					pixelHeight,
					new Color(200, 224, 255, 16)
			));
			graphics.fillRect(0, 0, pixelWidth, pixelHeight);
			graphics.setColor(new Color(0, 0, 0, 10));
			for (int y = 0; y < pixelHeight; y += 4) {
				graphics.drawLine(0, y, pixelWidth, y);
			}
		} else {
			graphics.setPaint(new GradientPaint(
					0.0F,
					0.0F,
					new Color(0, 0, 0, 84),
					0.0F,
					pixelHeight,
					new Color(8, 10, 12, 168)
			));
			graphics.fillRect(0, 0, pixelWidth, pixelHeight);
		}
	}

	private static void drawHomeScreen(Graphics2D graphics, UiLayout layout) {
		UiRect workspace = workspaceRect(layout);
		int arc = clampInt(layout.unit() * 2, 16, 30);
		fillRoundedRect(graphics, workspace, arc, new Color(245, 248, 252, 220));
		strokeRoundedRect(graphics, workspace, arc, 1.4F, new Color(255, 255, 255, 78));

		UiRect appRect = homeAppRect(layout);
		fillRoundedRect(graphics, appRect, clampInt(layout.unit() * 2, 18, 30), new Color(18, 22, 28, 232));
		strokeRoundedRect(graphics, appRect, clampInt(layout.unit() * 2, 18, 30), 1.2F, new Color(255, 255, 255, 54));

		UiRect iconRect = homeAppIconRect(layout);
		drawMediaAppIcon(graphics, iconRect);

		UiRect labelRect = homeAppLabelRect(layout, appRect);
		fillRoundedRect(graphics, labelRect, clampInt(layout.unit(), 10, 18), new Color(243, 246, 250, 238));
		drawCenteredText(graphics, "MEDIA", labelRect, new Color(18, 24, 30), Font.BOLD, clampInt(layout.unit(), 11, 19));

		UiRect statusRect = homeStatusRect(layout, appRect);
		if (statusRect != null) {
			fillRoundedRect(graphics, statusRect, clampInt(layout.unit() * 2, 16, 26), new Color(14, 18, 23, 220));
			strokeRoundedRect(graphics, statusRect, clampInt(layout.unit() * 2, 16, 26), 1.0F, new Color(255, 255, 255, 44));

			UiRect previewRect = new UiRect(
					statusRect.x() + layout.unit(),
					statusRect.y() + layout.unit(),
					Math.max(40, statusRect.width() - layout.unit() * 2),
					Math.max(36, statusRect.height() / 2)
			);
			fillRoundedRect(graphics, previewRect, clampInt(layout.unit(), 8, 16), new Color(224, 232, 240, 244));
			strokeRoundedRect(graphics, previewRect, clampInt(layout.unit(), 8, 16), 1.0F, new Color(12, 18, 24, 24));

			UiRect thumbRect = new UiRect(
					previewRect.x() + layout.unit(),
					previewRect.y() + layout.unit(),
					Math.max(16, previewRect.width() / 3),
					Math.max(14, previewRect.height() - layout.unit() * 2)
			);
			fillRoundedRect(graphics, thumbRect, clampInt(layout.unit() / 2, 6, 12), new Color(78, 186, 255, 210));

			int lineX = thumbRect.right() + layout.unit();
			int lineWidth = Math.max(18, previewRect.right() - layout.unit() - lineX);
			fillRoundedRect(graphics, new UiRect(lineX, previewRect.y() + layout.unit(), lineWidth, clampInt(layout.unit() - 2, 6, 16)), 6, new Color(40, 50, 60, 150));
			fillRoundedRect(graphics, new UiRect(lineX, previewRect.y() + layout.unit() * 2 + 4, Math.max(12, lineWidth * 3 / 4), clampInt(layout.unit() - 4, 4, 12)), 6, new Color(40, 50, 60, 90));
			fillRoundedRect(graphics, new UiRect(lineX, previewRect.bottom() - layout.unit() - 6, Math.max(12, lineWidth / 2), clampInt(layout.unit() - 4, 4, 12)), 6, new Color(40, 50, 60, 110));
		}

		UiRect footerRect = homeFooterRect(layout, appRect, statusRect);
		if (footerRect != null) {
			fillRoundedRect(graphics, footerRect, clampInt(layout.unit() * 2, 16, 24), new Color(255, 255, 255, 104));
			int chipSize = clampInt(layout.unit(), 12, 22);
			int chipGap = clampInt(layout.unit() / 2, 6, 12);
			int chipY = footerRect.y() + (footerRect.height() - chipSize) / 2;
			for (int i = 0; i < 3; i++) {
				int chipX = footerRect.x() + layout.unit() + i * (chipSize + chipGap);
				fillRoundedRect(graphics, new UiRect(chipX, chipY, chipSize, chipSize), clampInt(chipSize / 2, 6, 10), new Color(16, 22, 28, i == 0 ? 182 : 68));
			}
		}
	}

	private static void drawMediaScreen(Graphics2D graphics, UiLayout layout) {
		UiRect workspace = workspaceRect(layout);
		int arc = clampInt(layout.unit() * 2, 16, 30);
		fillRoundedRect(graphics, workspace, arc, new Color(242, 246, 252, 225));
		strokeRoundedRect(graphics, workspace, arc, 1.2F, new Color(255, 255, 255, 80));

		UiRect headerRect = mediaHeaderRect(layout);
		fillRoundedRect(graphics, headerRect, clampInt(layout.unit() * 2, 14, 24), new Color(16, 20, 26, 235));

		UiRect backRect = mediaBackRect(layout);
		fillRoundedRect(graphics, backRect, clampInt(layout.unit(), 10, 18), new Color(245, 247, 250, 235));
		drawBackArrow(graphics, backRect, new Color(18, 22, 28));

		UiRect titleRect = new UiRect(
				backRect.right() + clampInt(layout.unit() / 2, 6, 12),
				headerRect.y(),
				Math.max(24, headerRect.right() - backRect.right() - layout.unit() * 2),
				headerRect.height()
		);
		drawVerticalText(graphics, "MEDIA", titleRect, new Color(248, 251, 255), Font.BOLD, clampInt(layout.unit(), 11, 18));

		UiRect previewRect = mediaPreviewRect(layout);
		fillRoundedRect(graphics, previewRect, clampInt(layout.unit() * 2, 14, 24), new Color(15, 18, 24, 235));
		strokeRoundedRect(graphics, previewRect, clampInt(layout.unit() * 2, 14, 24), 1.0F, new Color(255, 255, 255, 42));

		UiRect imageRect = previewRect.inset(clampInt(layout.unit(), 10, 18));
		fillRoundedRect(graphics, imageRect, clampInt(layout.unit(), 10, 18), new Color(232, 238, 244, 244));
		drawMediaPlaceholder(graphics, imageRect);

		UiRect controlsRect = mediaControlsRect(layout, previewRect);
		fillRoundedRect(graphics, controlsRect, clampInt(layout.unit() * 2, 12, 20), new Color(255, 255, 255, 112));
		drawMediaControls(graphics, controlsRect, layout);

		UiRect sidebarRect = mediaSidebarRect(layout, previewRect, controlsRect);
		if (sidebarRect != null) {
			fillRoundedRect(graphics, sidebarRect, clampInt(layout.unit() * 2, 14, 22), new Color(18, 22, 28, 216));
			strokeRoundedRect(graphics, sidebarRect, clampInt(layout.unit() * 2, 14, 22), 1.0F, new Color(255, 255, 255, 40));
			int pad = clampInt(layout.unit(), 8, 14);
			int rowHeight = clampInt(layout.unit() * 3, 24, 42);
			for (int i = 0; i < 3; i++) {
				int rowY = sidebarRect.y() + pad + i * (rowHeight + pad);
				UiRect row = new UiRect(sidebarRect.x() + pad, rowY, Math.max(28, sidebarRect.width() - pad * 2), rowHeight);
				fillRoundedRect(graphics, row, clampInt(layout.unit(), 8, 14), new Color(255, 255, 255, i == 0 ? 122 : 54));
				UiRect thumb = new UiRect(row.x() + pad / 2, row.y() + pad / 2, Math.max(12, rowHeight - pad), Math.max(12, rowHeight - pad));
				fillRoundedRect(graphics, thumb, clampInt(layout.unit() / 2, 6, 10), new Color(78, 186, 255, 220));
				int barX = thumb.right() + pad / 2;
				int barWidth = Math.max(10, row.right() - pad - barX);
				fillRoundedRect(graphics, new UiRect(barX, row.y() + pad / 2, barWidth, clampInt(layout.unit() - 4, 4, 12)), 6, new Color(16, 22, 28, 110));
				fillRoundedRect(graphics, new UiRect(barX, row.bottom() - pad - clampInt(layout.unit() - 6, 4, 10), Math.max(8, barWidth * 2 / 3), clampInt(layout.unit() - 6, 4, 10)), 6, new Color(16, 22, 28, 72));
			}
		}
	}

	private static void drawMediaAppIcon(Graphics2D graphics, UiRect rect) {
		graphics.setPaint(new GradientPaint(
				rect.x(),
				rect.y(),
				new Color(64, 173, 255, 255),
				rect.right(),
				rect.bottom(),
				new Color(16, 84, 188, 255)
		));
		fillRoundedRect(graphics, rect, clampInt(rect.width() / 4, 12, 22), null);
		strokeRoundedRect(graphics, rect, clampInt(rect.width() / 4, 12, 22), 1.2F, new Color(255, 255, 255, 88));

		UiRect imageRect = rect.inset(Math.max(8, rect.width() / 7));
		strokeRoundedRect(graphics, imageRect, clampInt(rect.width() / 7, 8, 14), 2.4F, new Color(255, 255, 255, 234));

		int mountainBaseY = imageRect.y() + imageRect.height() * 3 / 4;
		int leftX = imageRect.x() + imageRect.width() / 5;
		int peakX = imageRect.x() + imageRect.width() / 2;
		int rightX = imageRect.right() - imageRect.width() / 6;
		graphics.setColor(new Color(255, 255, 255, 228));
		graphics.fillPolygon(
				new int[]{leftX, peakX, rightX},
				new int[]{mountainBaseY, imageRect.y() + imageRect.height() / 3, mountainBaseY},
				3
		);

		int playSize = Math.max(10, rect.width() / 4);
		int playCenterX = rect.right() - playSize - Math.max(8, rect.width() / 10);
		int playCenterY = rect.bottom() - playSize - Math.max(8, rect.width() / 10);
		fillRoundedRect(graphics, new UiRect(playCenterX - 4, playCenterY - 4, playSize + 8, playSize + 8), clampInt(playSize / 2, 8, 14), new Color(12, 18, 24, 150));
		graphics.fillPolygon(
				new int[]{playCenterX, playCenterX, playCenterX + playSize},
				new int[]{playCenterY, playCenterY + playSize, playCenterY + playSize / 2},
				3
		);
	}

	private static void drawMediaPlaceholder(Graphics2D graphics, UiRect rect) {
		int pad = Math.max(8, rect.width() / 14);
		UiRect horizon = new UiRect(rect.x() + pad, rect.bottom() - pad - Math.max(14, rect.height() / 6), rect.width() - pad * 2, Math.max(14, rect.height() / 6));
		fillRoundedRect(graphics, horizon, clampInt(pad, 6, 16), new Color(188, 212, 236, 255));
		graphics.setColor(new Color(82, 138, 196, 255));
		graphics.fillPolygon(
				new int[]{
						rect.x() + pad,
						rect.x() + rect.width() / 3,
						rect.x() + rect.width() / 2,
						rect.x() + rect.width() * 3 / 4,
						rect.right() - pad
				},
				new int[]{
						horizon.y() + horizon.height(),
						rect.y() + rect.height() / 2,
						rect.y() + rect.height() / 3,
						rect.y() + rect.height() / 2,
						horizon.y() + horizon.height()
				},
				5
		);
		int sunSize = Math.max(10, rect.width() / 8);
		graphics.setColor(new Color(255, 220, 112, 255));
		graphics.fillOval(rect.right() - pad - sunSize * 2, rect.y() + pad, sunSize, sunSize);
	}

	private static void drawMediaControls(Graphics2D graphics, UiRect rect, UiLayout layout) {
		int centerY = rect.y() + rect.height() / 2;
		int left = rect.x() + layout.unit();
		int right = rect.right() - layout.unit();
		int barHeight = clampInt(layout.unit() - 6, 4, 10);
		int playSize = clampInt(layout.unit() + 4, 12, 20);
		int playX = rect.x() + rect.width() / 2 - playSize / 2;
		int playY = centerY - playSize / 2;

		fillRoundedRect(graphics, new UiRect(left, centerY - barHeight / 2, Math.max(12, playX - left - layout.unit()), barHeight), 6, new Color(18, 22, 28, 92));
		fillRoundedRect(graphics, new UiRect(playX + playSize + layout.unit(), centerY - barHeight / 2, Math.max(12, right - playX - playSize - layout.unit()), barHeight), 6, new Color(18, 22, 28, 92));
		fillRoundedRect(graphics, new UiRect(playX, playY, playSize, playSize), clampInt(playSize / 2, 8, 12), new Color(18, 22, 28, 214));

		graphics.setColor(new Color(255, 255, 255, 240));
		int triPad = Math.max(3, playSize / 4);
		graphics.fillPolygon(
				new int[]{playX + triPad, playX + triPad, playX + playSize - triPad},
				new int[]{playY + triPad, playY + playSize - triPad, playY + playSize / 2},
				3
		);
	}

	private static void drawBackArrow(Graphics2D graphics, UiRect rect, Color color) {
		int pad = Math.max(4, rect.width() / 4);
		int midY = rect.y() + rect.height() / 2;
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2.4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.drawLine(rect.right() - pad, midY, rect.x() + pad, midY);
		graphics.drawLine(rect.x() + pad, midY, rect.x() + pad + pad, rect.y() + pad);
		graphics.drawLine(rect.x() + pad, midY, rect.x() + pad + pad, rect.bottom() - pad);
	}

	private static UiLayout createUiLayout(int width, int height) {
		int canvasWidth = width * MAP_SIZE;
		int canvasHeight = height * MAP_SIZE;
		int viewportWidth = Math.min(width, MAX_UI_TILES) * MAP_SIZE;
		int viewportHeight = Math.min(height, MAX_UI_TILES) * MAP_SIZE;
		int viewportX = (canvasWidth - viewportWidth) / 2;
		int viewportY = (canvasHeight - viewportHeight) / 2;
		int margin = clampInt(Math.round(Math.min(viewportWidth, viewportHeight) * 0.10F), 10, 24);
		int unit = clampInt(Math.round(Math.min(viewportWidth, viewportHeight) / 11.0F), 10, 24);
		return new UiLayout(canvasWidth, canvasHeight, viewportX, viewportY, viewportWidth, viewportHeight, margin, unit);
	}

	private static UiRect workspaceRect(UiLayout layout) {
		return new UiRect(
				layout.viewportX() + layout.margin() / 2,
				layout.viewportY() + layout.margin() / 2,
				layout.viewportWidth() - layout.margin(),
				layout.viewportHeight() - layout.margin()
		);
	}

	private static UiRect homeAppRect(UiLayout layout) {
		int iconSize = homeAppIconSize(layout);
		int width = clampInt(iconSize + layout.unit() * 2, 76, 128);
		int labelHeight = clampInt(layout.unit() + 6, 18, 28);
		int height = iconSize + labelHeight + layout.unit() * 2;
		return new UiRect(
				layout.viewportX() + layout.margin(),
				layout.viewportY() + layout.margin(),
				width,
				height
		);
	}

	private static UiRect homeAppIconRect(UiLayout layout) {
		UiRect appRect = homeAppRect(layout);
		int iconSize = homeAppIconSize(layout);
		return new UiRect(
				appRect.x() + (appRect.width() - iconSize) / 2,
				appRect.y() + layout.unit(),
				iconSize,
				iconSize
		);
	}

	private static UiRect homeAppLabelRect(UiLayout layout, UiRect appRect) {
		int labelHeight = clampInt(layout.unit() + 6, 18, 28);
		return new UiRect(
				appRect.x() + layout.unit(),
				appRect.bottom() - labelHeight - layout.unit(),
				appRect.width() - layout.unit() * 2,
				labelHeight
		);
	}

	private static UiRect homeStatusRect(UiLayout layout, UiRect appRect) {
		int x = appRect.right() + layout.margin();
		int availableWidth = layout.viewportX() + layout.viewportWidth() - layout.margin() - x;
		if (availableWidth < 72) {
			return null;
		}
		return new UiRect(
				x,
				appRect.y(),
				availableWidth,
				Math.max(72, appRect.height() - layout.unit())
		);
	}

	private static UiRect homeFooterRect(UiLayout layout, UiRect appRect, UiRect statusRect) {
		UiRect workspace = workspaceRect(layout);
		int top = Math.max(appRect.bottom(), statusRect != null ? statusRect.bottom() : 0) + layout.unit();
		int height = clampInt(layout.unit() * 2, 20, 34);
		int maxTop = workspace.bottom() - height - layout.unit() / 2;
		if (top > maxTop) {
			return null;
		}
		return new UiRect(
				workspace.x() + layout.unit(),
				top,
				workspace.width() - layout.unit() * 2,
				height
		);
	}

	private static UiRect mediaHeaderRect(UiLayout layout) {
		UiRect workspace = workspaceRect(layout);
		return new UiRect(
				workspace.x() + layout.unit() / 2,
				workspace.y() + layout.unit() / 2,
				workspace.width() - layout.unit(),
				clampInt(layout.unit() * 2, 24, 38)
		);
	}

	private static UiRect mediaBackRect(UiLayout layout) {
		UiRect header = mediaHeaderRect(layout);
		int size = clampInt(layout.unit() + 8, 18, 28);
		return new UiRect(
				header.x() + layout.unit() / 2,
				header.y() + (header.height() - size) / 2,
				size,
				size
		);
	}

	private static UiRect mediaPreviewRect(UiLayout layout) {
		UiRect workspace = workspaceRect(layout);
		UiRect header = mediaHeaderRect(layout);
		int x = workspace.x() + layout.unit() / 2;
		int y = header.bottom() + layout.unit();
		int width = workspace.width() - layout.unit();
		if (layout.viewportWidth() >= 220) {
			width = Math.max(72, (workspace.width() * 2) / 3);
		}
		int maxHeight = workspace.bottom() - y - layout.unit() / 2;
		int controlsHeight = clampInt(layout.unit() * 2, 20, 32);
		int height = Math.max(56, maxHeight - controlsHeight - layout.unit());
		return new UiRect(x, y, width, height);
	}

	private static UiRect mediaControlsRect(UiLayout layout, UiRect previewRect) {
		return new UiRect(
				previewRect.x(),
				previewRect.bottom() + layout.unit() / 2,
				previewRect.width(),
				clampInt(layout.unit() * 2, 20, 32)
		);
	}

	private static UiRect mediaSidebarRect(UiLayout layout, UiRect previewRect, UiRect controlsRect) {
		UiRect workspace = workspaceRect(layout);
		int x = previewRect.right() + layout.unit();
		int width = workspace.right() - x - layout.unit() / 2;
		if (width < 56) {
			return null;
		}
		return new UiRect(
				x,
				previewRect.y(),
				width,
				controlsRect.bottom() - previewRect.y()
		);
	}

	private static int homeAppIconSize(UiLayout layout) {
		return clampInt(Math.round(Math.min(layout.viewportWidth(), layout.viewportHeight()) * 0.42F), 56, 96);
	}

	private static void fillRoundedRect(Graphics2D graphics, UiRect rect, int arc, Color color) {
		if (color != null) {
			graphics.setColor(color);
		}
		graphics.fillRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), arc, arc);
	}

	private static void strokeRoundedRect(Graphics2D graphics, UiRect rect, int arc, float width, Color color) {
		Stroke previous = graphics.getStroke();
		graphics.setStroke(new BasicStroke(width));
		graphics.setColor(color);
		graphics.drawRoundRect(rect.x(), rect.y(), rect.width(), rect.height(), arc, arc);
		graphics.setStroke(previous);
	}

	private static void drawCenteredText(Graphics2D graphics, String text, UiRect rect, Color color, int style, int size) {
		graphics.setColor(color);
		graphics.setFont(new Font(Font.SANS_SERIF, style, size));
		var metrics = graphics.getFontMetrics();
		int textX = rect.x() + (rect.width() - metrics.stringWidth(text)) / 2;
		int textY = rect.y() + (rect.height() - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(text, textX, textY);
	}

	private static void drawVerticalText(Graphics2D graphics, String text, UiRect rect, Color color, int style, int size) {
		graphics.setColor(color);
		graphics.setFont(new Font(Font.SANS_SERIF, style, size));
		var metrics = graphics.getFontMetrics();
		int textY = rect.y() + (rect.height() - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(text, rect.x(), textY);
	}

	private static UiPoint screenTouchPoint(ItemFrame frame, Vec3 hitLocation, TileCoord tileCoord, int gridWidth, int gridHeight) {
		if (frame == null || hitLocation == null || tileCoord == null) {
			return null;
		}

		double localX = hitLocation.x - frame.blockPosition().getX();
		double localY = hitLocation.y - frame.blockPosition().getY();
		double localZ = hitLocation.z - frame.blockPosition().getZ();
		if (localY < -TOUCH_TOLERANCE || localY > 1.0D + TOUCH_TOLERANCE) {
			return null;
		}

		double u = switch (frame.getDirection()) {
			case SOUTH -> localX;
			case NORTH -> 1.0D - localX;
			case EAST -> 1.0D - localZ;
			case WEST -> localZ;
			default -> Double.NaN;
		};
		if (!Double.isFinite(u) || u < -TOUCH_TOLERANCE || u > 1.0D + TOUCH_TOLERANCE) {
			return null;
		}

		double v = 1.0D - localY;
		int pixelX = tileCoord.x() * MAP_SIZE + clampInt((int) Math.floor(clampDouble(u, 0.0D, 1.0D) * (MAP_SIZE - 1)), 0, MAP_SIZE - 1);
		int pixelY = tileCoord.y() * MAP_SIZE + clampInt((int) Math.floor(clampDouble(v, 0.0D, 1.0D) * (MAP_SIZE - 1)), 0, MAP_SIZE - 1);
		int maxX = Math.max(0, gridWidth * MAP_SIZE - 1);
		int maxY = Math.max(0, gridHeight * MAP_SIZE - 1);
		return new UiPoint(clampInt(pixelX, 0, maxX), clampInt(pixelY, 0, maxY));
	}

	private static BufferedImage loadOffBaseImage() {
		if (offBaseImage == null) {
			offBaseImage = loadBaseImage(SCREEN_OFF_RESOURCE);
		}
		return offBaseImage;
	}

	private static BufferedImage loadOnBaseImage() {
		if (onBaseImage == null) {
			onBaseImage = loadBaseImage(SCREEN_ON_RESOURCE);
		}
		return onBaseImage;
	}

	private static BufferedImage loadBaseImage(String resourcePath) {
		boolean powered = SCREEN_ON_RESOURCE.equals(resourcePath);
		try (InputStream inputStream = MonitorScreenSystem.class.getResourceAsStream(resourcePath)) {
			if (inputStream == null) {
				return fallbackImage(powered);
			}
			BufferedImage image = ImageIO.read(inputStream);
			return image != null ? image : fallbackImage(powered);
		} catch (IOException ignored) {
			return fallbackImage(powered);
		}
	}

	private static BufferedImage fallbackImage(boolean powered) {
		BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		int base = powered ? 0xF2F5F8 : 0x040404;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int band = ((y / 4) & 1) == 0 ? 4 : -4;
				int vignette = Math.max(0, 18 - Math.abs(128 - x) / 6 - Math.abs(128 - y) / 6);
				int rgb = adjustBrightness(base, powered ? vignette - band : -vignette + band);
				image.setRGB(x, y, 0xFF000000 | rgb);
			}
		}
		return image;
	}

	private static int adjustBrightness(int rgb, int delta) {
		int red = clampInt(((rgb >> 16) & 0xFF) + delta, 0, 255);
		int green = clampInt(((rgb >> 8) & 0xFF) + delta, 0, 255);
		int blue = clampInt((rgb & 0xFF) + delta, 0, 255);
		return (red << 16) | (green << 8) | blue;
	}

	private static void applyFrameToMap(MapItemSavedData mapData, byte[] frame) {
		if (mapData == null || frame == null || frame.length < MAP_SIZE * MAP_SIZE) {
			return;
		}
		for (int pixelIndex = 0; pixelIndex < MAP_SIZE * MAP_SIZE; pixelIndex++) {
			mapData.setColor(pixelIndex % MAP_SIZE, pixelIndex / MAP_SIZE, frame[pixelIndex]);
		}
	}

	private static void sendMapToPlayers(ServerLevel level, MapId mapId, MapItemSavedData mapData) {
		if (level == null || mapId == null || mapData == null || mapData.colors == null || mapData.colors.length < MAP_SIZE * MAP_SIZE) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			ItemStack mapStack = new ItemStack(Items.FILLED_MAP);
			mapStack.set(DataComponents.MAP_ID, mapId);
			mapData.tickCarriedBy(player, mapStack);
			player.connection.send(new ClientboundMapItemDataPacket(
					mapId,
					mapData.scale,
					mapData.locked,
					List.of(),
					new MapItemSavedData.MapPatch(0, 0, MAP_SIZE, MAP_SIZE, mapData.colors.clone())
			));
			Packet<?> packet = mapData.getUpdatePacket(mapId, player);
			if (packet != null) {
				player.connection.send(packet);
			}
		}
	}

	private static void ensureDisplay(ServerLevel level, ItemFrame frame, int connectionMask) {
		List<Display.ItemDisplay> displays = findDisplays(level, frame.blockPosition(), frame.getDirection());
		Display.ItemDisplay display;
		if (displays.isEmpty()) {
			display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
			display.addTag(DISPLAY_ROOT_TAG);
			display.addTag(positionTag(frame.blockPosition()));
			display.addTag(facingTag(frame.getDirection()));
			level.addFreshEntity(display);
		} else {
			display = displays.get(0);
			for (int i = 1; i < displays.size(); i++) {
				displays.get(i).discard();
			}
		}

		Direction facing = frame.getDirection();
		Vec3 center = Vec3.atCenterOf(frame.blockPosition()).add(
				facing.getStepX() * DISPLAY_PLANE_OFFSET,
				facing.getStepY() * DISPLAY_PLANE_OFFSET,
				facing.getStepZ() * DISPLAY_PLANE_OFFSET
		);
		float yRot = facing.toYRot();
		display.setPos(center.x, center.y, center.z);
		display.setYRot(yRot);
		display.setXRot(0.0F);
		display.setYHeadRot(yRot);
		display.setYBodyRot(yRot);
		display.setItemStack(MonitorItem.createDisplayStack(connectionMask));
		display.setItemTransform(ItemDisplayContext.FIXED);
		display.setBillboardConstraints(Display.BillboardConstraints.FIXED);
		display.setTransformation(Transformation.identity());
		display.setNoGravity(true);
		display.setInvulnerable(true);
		display.setSilent(true);
		display.setShadowRadius(0.0F);
		display.setShadowStrength(0.0F);
		display.setViewRange(1.0F);
	}

	private static void cleanupOrphanDisplays(ServerLevel level) {
		for (Entity entity : level.getAllEntities()) {
			if (!(entity instanceof Display.ItemDisplay display) || !display.getTags().contains(DISPLAY_ROOT_TAG)) {
				continue;
			}
			BlockPos pos = parsePositionTag(display.getTags());
			Direction facing = parseFacingTag(display.getTags());
			if (pos == null || facing == null || findScreenFrame(level, pos, facing) == null) {
				display.discard();
			}
		}
	}

	private static void removeDisplays(ServerLevel level, BlockPos pos, Direction facing) {
		for (Display.ItemDisplay display : findDisplays(level, pos, facing)) {
			display.discard();
		}
	}

	private static List<Display.ItemDisplay> findDisplays(ServerLevel level, BlockPos pos, Direction facing) {
		AABB box = new AABB(pos).inflate(DISPLAY_SEARCH_RADIUS);
		String posTag = positionTag(pos);
		String facingTag = facingTag(facing);
		return level.getEntities(
				EntityType.ITEM_DISPLAY,
				box,
				display -> display.getTags().contains(DISPLAY_ROOT_TAG)
						&& display.getTags().contains(posTag)
						&& display.getTags().contains(facingTag)
		);
	}

	private static ItemFrame findAnyFrame(ServerLevel level, BlockPos pos, Direction facing) {
		AABB box = new AABB(pos).inflate(0.6D);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box, candidate -> candidate.blockPosition().equals(pos))) {
			if (frame.getDirection() == facing) {
				return frame;
			}
		}
		return null;
	}

	private static ItemFrame findScreenFrame(ServerLevel level, BlockPos pos, Direction facing) {
		AABB box = new AABB(pos).inflate(0.6D);
		for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box, candidate -> candidate.blockPosition().equals(pos))) {
			if (facing != null && frame.getDirection() != facing) {
				continue;
			}
			if (readScreenState(frame.getItem()) != null) {
				return frame;
			}
		}
		return null;
	}

	private static Direction frameRight(Direction facing) {
		return switch (facing) {
			case NORTH -> Direction.WEST;
			case SOUTH -> Direction.EAST;
			case EAST -> Direction.NORTH;
			case WEST -> Direction.SOUTH;
			default -> Direction.EAST;
		};
	}

	private static ServerLevel photoMapLevel(MinecraftServer server, ServerLevel fallback) {
		if (server == null) {
			return fallback;
		}
		ServerLevel end = server.getLevel(Level.END);
		if (end != null) {
			return end;
		}
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		return overworld != null ? overworld : fallback;
	}

	private static String positionTag(BlockPos pos) {
		return POS_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String facingTag(Direction facing) {
		return FACING_TAG_PREFIX + facing.getName();
	}

	private static BlockPos parsePositionTag(Set<String> tags) {
		for (String tag : tags) {
			if (!tag.startsWith(POS_TAG_PREFIX)) {
				continue;
			}
			String raw = tag.substring(POS_TAG_PREFIX.length());
			String[] parts = raw.split(",");
			if (parts.length != 3) {
				continue;
			}
			try {
				return new BlockPos(
						Integer.parseInt(parts[0]),
						Integer.parseInt(parts[1]),
						Integer.parseInt(parts[2])
				);
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private static Direction parseFacingTag(Set<String> tags) {
		for (String tag : tags) {
			if (tag.startsWith(FACING_TAG_PREFIX)) {
				return Direction.byName(tag.substring(FACING_TAG_PREFIX.length()));
			}
		}
		return null;
	}

	private static Component wallOnlyMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Экранъ ставится токмо на стену");
		}
		if (locale.startsWith("uk")) {
			return literal("Екран ставиться лише на стіну");
		}
		if (locale.startsWith("ja")) {
			return literal("モニターは壁にのみ設置できます");
		}
		if (locale.startsWith("ru")) {
			return literal("Экран ставится только на стену");
		}
		return literal("The monitor can only be placed on a wall");
	}

	private static Component occupiedMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return literal("Тутъ уже занято");
		}
		if (locale.startsWith("uk")) {
			return literal("Тут уже зайнято");
		}
		if (locale.startsWith("ja")) {
			return literal("ここには設置できません");
		}
		if (locale.startsWith("ru")) {
			return literal("Тут уже занято");
		}
		return literal("That spot is already occupied");
	}

	private static String locale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "en_us";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	private static Component literal(String value) {
		return Component.literal(value).withStyle(style -> style.withItalic(false));
	}

	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private enum ScreenViewMode {
		HOME("home"),
		MEDIA("media");

		private final String serializedName;

		ScreenViewMode(String serializedName) {
			this.serializedName = serializedName;
		}

		String serializedName() {
			return this.serializedName;
		}

		static ScreenViewMode fromTag(String value) {
			for (ScreenViewMode mode : values()) {
				if (mode.serializedName.equalsIgnoreCase(value)) {
					return mode;
				}
			}
			return HOME;
		}
	}

	private record ScreenKey(BlockPos pos, Direction direction) {
	}

	private record TileCoord(int x, int y) {
	}

	private record UiPoint(int x, int y) {
	}

	private record UiRect(int x, int y, int width, int height) {
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

	private record UiLayout(
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

	private record ScreenFrame(ItemFrame frame, ScreenTileState state) {
	}

	private record ScreenComponent(
			Direction facing,
			Direction right,
			int width,
			int height,
			boolean powered,
			ScreenViewMode viewMode,
			Map<ItemFrame, TileCoord> frameCoords,
			Map<TileCoord, ScreenFrame> byCoord
	) {
	}

	private record RenderCacheKey(boolean powered, ScreenViewMode viewMode, int width, int height) {
	}

	private record ScreenTileState(
			boolean linkLocked,
			int gridWidth,
			int gridHeight,
			int tileX,
			int tileY,
			int connectionMask,
			boolean powered,
			ScreenViewMode viewMode
	) {
		boolean sameRenderState(ScreenTileState other) {
			return other != null
					&& this.gridWidth == other.gridWidth
					&& this.gridHeight == other.gridHeight
					&& this.tileX == other.tileX
					&& this.tileY == other.tileY
					&& this.powered == other.powered
					&& this.viewMode == other.viewMode;
		}
	}
}
