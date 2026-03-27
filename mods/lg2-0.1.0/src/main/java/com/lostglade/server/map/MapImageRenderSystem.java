package com.lostglade.server.map;

import com.lostglade.Lg2;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.PhotoPrintData;
import com.lostglade.server.CameraCaptureSystem;
import com.lostglade.server.ServerMechanicsGateSystem;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.Holder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class MapImageRenderSystem {
	private static final Identifier CAMERA_PRINT_SOUND_ID = Identifier.fromNamespaceAndPath("lg2", "camera_print");
	private static final Holder<SoundEvent> CAMERA_PRINT_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(CAMERA_PRINT_SOUND_ID));
	private static final float CAMERA_PRINT_VOLUME = 0.55F;
	private static final float CAMERA_PRINT_PITCH = 1.0F;
	private static final int MAP_SIZE = 128;
	private static final int PHOTO_MAP_CENTER = 30_000_000;
	private static final int RESULTS_APPLIED_PER_TICK = 384;
	private static final int FRAME_PIXELS_APPLIED_PER_TICK = 4096;
	private static final int MAX_PIXEL_FAILURES = 64;
	private static final long MAX_PREPARE_NANOS_PER_TICK = 1_000_000L;
	private static final Map<UUID, RenderJob> PLAYER_JOBS = new HashMap<>();
	private static final Queue<UUID> QUEUE = new ArrayDeque<>();
	private static UUID activePlayerId;
	private static ExecutorService executor;

	private MapImageRenderSystem() {
	}

	public static void register() {
		ensureExecutor();
		ServerTickEvents.END_SERVER_TICK.register(MapImageRenderSystem::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> shutdownExecutor());
	}

	public static boolean hasActiveRender(UUID playerId) {
		return playerId != null && PLAYER_JOBS.containsKey(playerId);
	}

	public static boolean startRender(ServerPlayer player, Component itemName, MapPixelProvider provider) {
		if (player == null || provider == null || hasActiveRender(player.getUUID())) {
			return false;
		}
		int mapsWide = Math.max(1, provider.mapTilesWide());
		int mapsHigh = Math.max(1, provider.mapTilesHigh());
		PhotoMapSet photoMapSet = createPhotoMapSet(player, itemName, mapsWide, mapsHigh);
		if (photoMapSet == null) {
			return false;
		}
		PreviewMap previewMap = preparePreviewMap(player, itemName, photoMapSet);
		if (previewMap == null || previewMap.previewMapId() == null) {
			return false;
		}
		PhotoPrintData photoData = new PhotoPrintData(mapsWide, mapsHigh, previewMap.previewMapId().id(), photoMapIdsToRawIds(photoMapSet.mapIds()));
		givePhotoItem(player, PhotoPrintData.createPhotoItem(itemName, mapsWide, mapsHigh, previewMap.previewMapId(), photoMapSet.mapIds()));
		RenderJob job = new RenderJob(player.getUUID(), photoMapSet.mapIds(), photoMapSet.mapDataSet(), mapsWide, mapsHigh, previewMap.previewMapId().id(), provider);
		PLAYER_JOBS.put(player.getUUID(), job);
		QUEUE.offer(player.getUUID());
		ServerMechanicsGateSystem.syncPlayerInventory(player);
		if (activePlayerId == null) {
			activePlayerId = player.getUUID();
			player.displayClientMessage(CameraCaptureSystem.queuedForRenderMessage(player), true);
		} else {
			player.displayClientMessage(CameraCaptureSystem.addedToRenderQueueMessage(player), true);
		}
		renderImmediatePreview(player, provider, previewMap);
		sendMatchingPhotoPreviewMap(player, photoData);
		return true;
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

	private static void tick(MinecraftServer server) {
		ensureExecutor();
		if (PLAYER_JOBS.isEmpty()) {
			activePlayerId = null;
			return;
		}
		normalizeQueue();
		if (activePlayerId == null) {
			return;
		}

		RenderJob job = PLAYER_JOBS.get(activePlayerId);
		if (job == null) {
			pollNextActive();
			return;
		}

		ServerPlayer player = server.getPlayerList().getPlayer(job.playerId());
		ServerLevel level = server.getLevel(job.provider().dimension());
		if (player == null || level == null || !job.provider().isValid(server)) {
			removeJob(job.playerId());
			pollNextActive();
			return;
		}

		if (job.provider().prefersWholeFrameRendering()) {
			tickWholeFrameJob(server, player, level, job);
			return;
		}

		int processed = 0;
		PixelResult result;
		while (processed < RESULTS_APPLIED_PER_TICK && (result = job.pollResult()) != null) {
			if (result.failed()) {
				job.recordFailure();
				if (!job.hasLoggedFailure() && result.error() != null) {
					Lg2.LOGGER.error("Map image render failed for player {}", job.playerId(), result.error());
					job.markFailureLogged();
				}
				if (job.failureCount() >= MAX_PIXEL_FAILURES) {
					player.displayClientMessage(Component.literal("Рендер снимка остановлен: слишком много ошибок."), true);
					removeJob(job.playerId());
					pollNextActive();
					return;
				}
			} else {
				setPhotoColor(job, result.x(), result.y(), result.color());
			}
			job.finishDispatchedPixel();
			processed++;
		}
		updatePhotoProgress(server, job);

		long tickStart = System.nanoTime();
		while (job.canDispatchMore() && job.nextPixel() < job.totalPixels()) {
			int pixelIndex = job.nextPixel();
			int x = pixelIndex % MAP_SIZE;
			int y = pixelIndex / MAP_SIZE;
			MapPixelProvider.PreparedPixel prepared;
			try {
				prepared = job.provider().preparePixel(server, x, y);
			} catch (Exception exception) {
				job.recordFailure();
				if (!job.hasLoggedFailure()) {
					Lg2.LOGGER.error("Map image prepare failed for player {}", job.playerId(), exception);
					job.markFailureLogged();
				}
				if (job.failureCount() >= MAX_PIXEL_FAILURES) {
					player.displayClientMessage(Component.literal("Подготовка снимка остановлена: слишком много ошибок."), true);
					removeJob(job.playerId());
					pollNextActive();
					return;
				}
				job.advance();
				continue;
			}

			job.dispatchPixel();
			job.advance();
			MapPixelProvider provider = job.provider();
			ExecutorService currentExecutor = executor;
			currentExecutor.submit(() -> {
				try {
					byte color = provider.renderPreparedPixel(prepared);
					job.pushResult(PixelResult.success(prepared.x(), prepared.y(), color));
				} catch (Exception exception) {
					job.pushResult(PixelResult.failure(prepared.x(), prepared.y(), exception));
				}
			});

			if (System.nanoTime() - tickStart >= MAX_PREPARE_NANOS_PER_TICK) {
				break;
			}
		}

		if (job.isDone() && !job.hasDispatchedPixels()) {
			lockRenderedPhoto(level, job);
			finalizePhotoDisplay(server, job);
			job.provider().onCompleted(server);
			player.displayClientMessage(CameraCaptureSystem.captureCompletedMessage(player), true);
			playCompletionSound(player);
			removeJob(job.playerId());
			pollNextActive();
		}
	}

	private static void tickWholeFrameJob(MinecraftServer server, ServerPlayer player, ServerLevel level, RenderJob job) {
		if (!job.hasPreparedFrame()) {
			try {
				job.setPreparedFrame(job.provider().prepareFrame(server));
			} catch (Exception exception) {
				Lg2.LOGGER.error("Map frame prepare failed for player {}", job.playerId(), exception);
				player.displayClientMessage(Component.literal("Подготовка снимка остановлена: ошибка кадра."), true);
				removeJob(job.playerId());
				pollNextActive();
				return;
			}
		}

		if (!job.hasDispatchedFrameTask()) {
			job.markFrameTaskDispatched();
			Object preparedFrame = job.preparedFrame();
			MapPixelProvider provider = job.provider();
			executor.submit(() -> {
				try {
					byte[] frame = provider.renderPreparedFrame(preparedFrame);
					job.pushFrameResult(FrameResult.success(frame));
				} catch (Throwable throwable) {
					job.pushFrameResult(FrameResult.failure(throwable));
				}
			});
		}

		FrameResult frameResult = job.frameResult();
		if (frameResult == null) {
			return;
		}
		if (frameResult.failed()) {
			Lg2.LOGGER.error("Map frame render failed for player {}", job.playerId(), frameResult.error());
			player.displayClientMessage(Component.literal("Рендер снимка остановлен: ошибка кадра."), true);
			removeJob(job.playerId());
			pollNextActive();
			return;
		}

		byte[] frame = frameResult.pixels();
		if (frame == null || frame.length < job.totalPixels()) {
			Lg2.LOGGER.error("Map frame render returned invalid frame for player {}", job.playerId());
			player.displayClientMessage(Component.literal("Рендер снимка остановлен: кадр повреждён."), true);
			removeJob(job.playerId());
			pollNextActive();
			return;
		}

		int applied = 0;
		while (applied < FRAME_PIXELS_APPLIED_PER_TICK && job.frameApplyIndex() < job.totalPixels()) {
			int pixelIndex = job.frameApplyIndex();
			setPhotoColor(job, pixelIndex, frame[pixelIndex]);
			job.advanceFrameApplyIndex();
			applied++;
		}
		updatePhotoProgress(server, job);

		if (job.frameApplyIndex() >= job.totalPixels()) {
			lockRenderedPhoto(level, job);
			finalizePhotoDisplay(server, job);
			job.provider().onCompleted(server);
			player.displayClientMessage(CameraCaptureSystem.captureCompletedMessage(player), true);
			playCompletionSound(player);
			removeJob(job.playerId());
			pollNextActive();
		}
	}

	private static void playCompletionSound(ServerPlayer player) {
		Holder<SoundEvent> sound = PolymerResourcePackUtils.hasMainPack(player)
				? CAMERA_PRINT_SOUND
				: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EXPERIENCE_ORB_PICKUP);
		float pitch = PolymerResourcePackUtils.hasMainPack(player) ? CAMERA_PRINT_PITCH : 1.15F;
		player.connection.send(new ClientboundSoundPacket(
				sound,
				SoundSource.PLAYERS,
				player.getX(),
				player.getY(),
				player.getZ(),
				CAMERA_PRINT_VOLUME,
				pitch,
				player.level().getRandom().nextLong()
		));
	}

	private static void lockRenderedPhoto(ServerLevel level, RenderJob job) {
		if (level == null || job == null) {
			return;
		}
		for (int i = 0; i < job.mapIds().length; i++) {
			MapId mapId = job.mapIds()[i];
			MapItemSavedData mapData = job.mapDataSet()[i];
			if (mapId == null || mapData == null || mapData.locked) {
				continue;
			}
			level.setMapData(mapId, mapData.locked());
		}
	}

	private static void updatePhotoProgress(MinecraftServer server, RenderJob job) {
		if (server == null || job == null) {
			return;
		}
		int progress = job.progressPercent();
		if (progress <= job.lastDisplayedProgress() || progress >= 100) {
			return;
		}
		Component name = CameraCaptureSystem.createQueuedPhotoName(progress);
		updatePhotoItemsInInventories(server, job.photoData(), name);
		job.setLastDisplayedProgress(progress);
	}

	private static void finalizePhotoDisplay(MinecraftServer server, RenderJob job) {
		if (server == null || job == null) {
			return;
		}
		Component completedName = CameraCaptureSystem.createCompletedPhotoName();
		updatePhotoItemsInInventories(server, job.photoData(), completedName);
		updatePlacedPhotoFrameNames(server, job.photoData(), completedName);
		job.setLastDisplayedProgress(100);
	}

	private static PhotoMapSet createPhotoMapSet(ServerPlayer player, Component itemName, int mapsWide, int mapsHigh) {
		ServerLevel level = (ServerLevel) player.level();
		ServerLevel mapLevel = photoMapLevel(player.level().getServer(), level);
		MapId[] mapIds = new MapId[mapsWide * mapsHigh];
		MapItemSavedData[] mapDataSet = new MapItemSavedData[mapIds.length];
		for (int i = 0; i < mapIds.length; i++) {
			ItemStack map = MapItem.create(mapLevel, PHOTO_MAP_CENTER, PHOTO_MAP_CENTER, (byte) 0, false, false);
			MapId mapId = map.get(DataComponents.MAP_ID);
			if (mapId == null) {
				return null;
			}
			MapItemSavedData mapData = mapLevel.getMapData(mapId);
			if (mapData != null && !mapData.locked) {
				mapLevel.setMapData(mapId, mapData.locked());
				mapData = mapLevel.getMapData(mapId);
			}
			if (mapData == null) {
				return null;
			}
			map.set(DataComponents.CUSTOM_NAME, itemName);
			mapIds[i] = mapId;
			mapDataSet[i] = mapData;
		}
		return new PhotoMapSet(mapIds, mapDataSet);
	}

	private static void givePhotoItem(ServerPlayer player, ItemStack item) {
		if (player == null || item == null || item.isEmpty()) {
			return;
		}
		boolean inserted = player.getInventory().add(item);
		if (!inserted) {
			ItemEntity itemEntity = player.drop(item, false);
			if (itemEntity != null) {
				itemEntity.setPickUpDelay(0);
			}
		}
	}

	public static void sendPhotoPreviewMap(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return;
		}
		sendPhotoPreviewMap(player, PhotoPrintData.readPhotoItem(stack));
	}

	private static void updatePhotoItemsInInventories(MinecraftServer server, PhotoPrintData photoData, Component name) {
		if (server == null || photoData == null || name == null) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean changed = false;
			for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
				ItemStack stack = player.getInventory().getItem(slot);
				PhotoPrintData stackData = PhotoPrintData.readPhotoItem(stack);
				if (stackData == null || !stackData.samePhoto(photoData)) {
					continue;
				}
				stack.set(DataComponents.CUSTOM_NAME, name.copy());
				changed = true;
			}
			if (changed) {
				ServerMechanicsGateSystem.syncPlayerInventory(player);
				sendMatchingPhotoPreviewMap(player, photoData);
			}
		}
	}

	private static void updatePlacedPhotoFrameNames(MinecraftServer server, PhotoPrintData photoData, Component name) {
		if (server == null || photoData == null || name == null) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (net.minecraft.world.entity.decoration.ItemFrame frame : level.getEntitiesOfClass(
					net.minecraft.world.entity.decoration.ItemFrame.class,
					new net.minecraft.world.phys.AABB(-30_000_000.0D, level.getMinY(), -30_000_000.0D, 30_000_000.0D, level.getMaxY(), 30_000_000.0D)
			)) {
				ItemStack stack = frame.getItem();
				PhotoPrintData.PlacedPhotoFrameData frameData = PhotoPrintData.readFrameTile(stack);
				if (frameData == null || !frameData.samePhoto(photoData)) {
					continue;
				}
				ItemStack updated = stack.copy();
				updated.set(DataComponents.CUSTOM_NAME, name.copy());
				frame.setItem(updated, false);
			}
		}
	}

	private static void setPhotoColor(RenderJob job, int globalPixelIndex, byte color) {
		if (job == null || globalPixelIndex < 0 || globalPixelIndex >= job.totalPixels()) {
			return;
		}
		int outputWidth = job.outputWidth();
		int globalX = globalPixelIndex % outputWidth;
		int globalY = globalPixelIndex / outputWidth;
		setPhotoColor(job, globalX, globalY, color);
	}

	private static void setPhotoColor(RenderJob job, int globalX, int globalY, byte color) {
		if (job == null || globalX < 0 || globalY < 0 || globalX >= job.outputWidth() || globalY >= job.outputHeight()) {
			return;
		}
		int tileX = globalX / MAP_SIZE;
		int tileY = globalY / MAP_SIZE;
		int tileIndex = tileY * job.mapsWide() + tileX;
		if (tileIndex < 0 || tileIndex >= job.mapDataSet().length) {
			return;
		}
		MapItemSavedData mapData = job.mapDataSet()[tileIndex];
		if (mapData == null) {
			return;
		}
		mapData.setColor(globalX % MAP_SIZE, globalY % MAP_SIZE, color);
	}

	private static PreviewMap preparePreviewMap(ServerPlayer player, Component itemName, PhotoMapSet photoMapSet) {
		if (player == null || photoMapSet == null || photoMapSet.mapIds().length == 0 || photoMapSet.mapDataSet().length == 0) {
			return null;
		}
		if (photoMapSet.mapIds().length == 1 && photoMapSet.mapDataSet().length == 1) {
			return new PreviewMap(photoMapSet.mapIds()[0], photoMapSet.mapDataSet()[0]);
		}
		PhotoMapSet previewMapSet = createPhotoMapSet(player, itemName, 1, 1);
		if (previewMapSet == null || previewMapSet.mapIds().length == 0 || previewMapSet.mapDataSet().length == 0) {
			return new PreviewMap(photoMapSet.mapIds()[0], photoMapSet.mapDataSet()[0]);
		}
		return new PreviewMap(previewMapSet.mapIds()[0], previewMapSet.mapDataSet()[0]);
	}

	private static void renderImmediatePreview(ServerPlayer player, MapPixelProvider provider, PreviewMap previewMap) {
		if (player == null || provider == null || previewMap == null || previewMap.previewMapId() == null || previewMap.previewMapData() == null) {
			return;
		}
		byte[] previewPixels;
		try {
			previewPixels = provider.renderImmediatePreview(player.level().getServer());
		} catch (Exception exception) {
			Lg2.LOGGER.error("Immediate photo preview render failed for player {}", player.getUUID(), exception);
			return;
		}
		if (!isValidFrame(previewPixels, MAP_SIZE * MAP_SIZE)) {
			return;
		}
		applyFrameToMap(previewMap.previewMapData(), previewPixels);
		sendPhotoPreviewMap(player, previewMap.previewMapId(), previewMap.previewMapData());
	}

	private static void applyFrameToMap(MapItemSavedData mapData, byte[] frame) {
		if (mapData == null || !isValidFrame(frame, MAP_SIZE * MAP_SIZE)) {
			return;
		}
		for (int pixelIndex = 0; pixelIndex < MAP_SIZE * MAP_SIZE; pixelIndex++) {
			mapData.setColor(pixelIndex % MAP_SIZE, pixelIndex / MAP_SIZE, frame[pixelIndex]);
		}
	}

	private static boolean isValidFrame(byte[] frame, int expectedPixels) {
		return frame != null && frame.length >= expectedPixels;
	}

	private static void sendMatchingPhotoPreviewMap(ServerPlayer player, PhotoPrintData photoData) {
		if (player == null || photoData == null) {
			return;
		}
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			PhotoPrintData stackData = PhotoPrintData.readPhotoItem(stack);
			if (stackData == null || !stackData.samePhoto(photoData)) {
				continue;
			}
			sendPhotoPreviewMap(player, stackData);
			return;
		}
	}

	private static void sendPhotoPreviewMap(ServerPlayer player, PhotoPrintData photoData) {
		if (player == null || photoData == null || photoData.previewMapId() < 0 || !(player.level() instanceof ServerLevel fallbackLevel)) {
			return;
		}
		ServerLevel mapLevel = photoMapLevel(player.level().getServer(), fallbackLevel);
		MapId previewMapId = new MapId(photoData.previewMapId());
		MapItemSavedData previewMapData = mapLevel.getMapData(previewMapId);
		sendPhotoPreviewMap(player, previewMapId, previewMapData);
	}

	private static void sendPhotoPreviewMap(ServerPlayer player, MapId previewMapId, MapItemSavedData previewMapData) {
		if (player == null || previewMapId == null || previewMapData == null || previewMapData.colors == null || previewMapData.colors.length < MAP_SIZE * MAP_SIZE) {
			return;
		}
		ItemStack previewMapStack = new ItemStack(Items.FILLED_MAP);
		previewMapStack.set(DataComponents.MAP_ID, previewMapId);
		previewMapData.tickCarriedBy(player, previewMapStack);
		player.connection.send(new ClientboundMapItemDataPacket(
				previewMapId,
				previewMapData.scale,
				previewMapData.locked,
				List.of(),
				new MapItemSavedData.MapPatch(0, 0, MAP_SIZE, MAP_SIZE, previewMapData.colors.clone())
		));
		Packet<?> packet = previewMapData.getUpdatePacket(previewMapId, player);
		if (packet != null) {
			player.connection.send(packet);
		}
	}

	private static void normalizeQueue() {
		if (activePlayerId != null && !PLAYER_JOBS.containsKey(activePlayerId)) {
			activePlayerId = null;
		}
		if (activePlayerId == null) {
			pollNextActive();
		}
	}

	private static void pollNextActive() {
		activePlayerId = null;
		while (!QUEUE.isEmpty()) {
			UUID candidate = QUEUE.poll();
			if (candidate != null && PLAYER_JOBS.containsKey(candidate)) {
				activePlayerId = candidate;
				break;
			}
		}
	}

	private static void removeJob(UUID playerId) {
		if (playerId == null) {
			return;
		}
		PLAYER_JOBS.remove(playerId);
		if (playerId.equals(activePlayerId)) {
			activePlayerId = null;
		}
		Iterator<UUID> iterator = QUEUE.iterator();
		while (iterator.hasNext()) {
			if (playerId.equals(iterator.next())) {
				iterator.remove();
				break;
			}
		}
	}

	private static void ensureExecutor() {
		if (executor != null) {
			return;
		}
		int threads = Lg2Config.get().cameraRenderThreads;
		ThreadFactory threadFactory = runnable -> {
			Thread thread = new Thread(runnable, "lg2-map-render");
			thread.setDaemon(true);
			return thread;
		};
		executor = Executors.newFixedThreadPool(threads, threadFactory);
	}

	private static void shutdownExecutor() {
		if (executor == null) {
			return;
		}
		executor.shutdownNow();
		executor = null;
	}

	private static final class RenderJob {
		private final UUID playerId;
		private final MapId[] mapIds;
		private final MapItemSavedData[] mapDataSet;
		private final int mapsWide;
		private final int mapsHigh;
		private final int previewMapId;
		private final MapPixelProvider provider;
		private final ConcurrentLinkedQueue<PixelResult> completedResults = new ConcurrentLinkedQueue<>();
		private int nextPixel;
		private int failureCount;
		private boolean failureLogged;
		private int dispatchedPixels;
		private Object preparedFrame;
		private boolean framePrepared;
		private boolean frameTaskDispatched;
		private volatile FrameResult frameResult;
		private int frameApplyIndex;
		private int lastDisplayedProgress = -1;

		private RenderJob(UUID playerId, MapId[] mapIds, MapItemSavedData[] mapDataSet, int mapsWide, int mapsHigh, int previewMapId, MapPixelProvider provider) {
			this.playerId = playerId;
			this.mapIds = mapIds;
			this.mapDataSet = mapDataSet;
			this.mapsWide = mapsWide;
			this.mapsHigh = mapsHigh;
			this.previewMapId = previewMapId;
			this.provider = provider;
		}

		private UUID playerId() {
			return this.playerId;
		}

		private MapId[] mapIds() {
			return this.mapIds;
		}

		private MapItemSavedData[] mapDataSet() {
			return this.mapDataSet;
		}

		private int mapsWide() {
			return this.mapsWide;
		}

		private int mapsHigh() {
			return this.mapsHigh;
		}

		private int outputWidth() {
			return this.mapsWide * MAP_SIZE;
		}

		private int outputHeight() {
			return this.mapsHigh * MAP_SIZE;
		}

		private int totalPixels() {
			return this.outputWidth() * this.outputHeight();
		}

		private PhotoPrintData photoData() {
			return new PhotoPrintData(this.mapsWide, this.mapsHigh, this.previewMapId, photoMapIdsToRawIds(this.mapIds));
		}

		private MapPixelProvider provider() {
			return this.provider;
		}

		private int nextPixel() {
			return this.nextPixel;
		}

		private void advance() {
			this.nextPixel++;
		}

		private void dispatchPixel() {
			this.dispatchedPixels++;
		}

		private void finishDispatchedPixel() {
			if (this.dispatchedPixels > 0) {
				this.dispatchedPixels--;
			}
		}

		private void recordFailure() {
			this.failureCount++;
		}

		private int failureCount() {
			return this.failureCount;
		}

		private boolean hasLoggedFailure() {
			return this.failureLogged;
		}

		private void markFailureLogged() {
			this.failureLogged = true;
		}

		private boolean canDispatchMore() {
			return this.dispatchedPixels < Lg2Config.get().cameraRenderInFlightPixels;
		}

		private boolean hasDispatchedPixels() {
			return this.dispatchedPixels > 0;
		}

		private boolean hasPreparedFrame() {
			return this.framePrepared;
		}

		private void setPreparedFrame(Object preparedFrame) {
			this.preparedFrame = preparedFrame;
			this.framePrepared = true;
		}

		private Object preparedFrame() {
			return this.preparedFrame;
		}

		private boolean hasDispatchedFrameTask() {
			return this.frameTaskDispatched;
		}

		private void markFrameTaskDispatched() {
			this.frameTaskDispatched = true;
		}

		private void pushFrameResult(FrameResult frameResult) {
			this.frameResult = frameResult;
		}

		private FrameResult frameResult() {
			return this.frameResult;
		}

		private int frameApplyIndex() {
			return this.frameApplyIndex;
		}

		private void advanceFrameApplyIndex() {
			this.frameApplyIndex++;
		}

		private void pushResult(PixelResult result) {
			this.completedResults.offer(result);
		}

		private PixelResult pollResult() {
			return this.completedResults.poll();
		}

		private boolean isDone() {
			return this.nextPixel >= totalPixels();
		}

		private int progressPercent() {
			if (this.provider.prefersWholeFrameRendering()) {
				if (this.totalPixels() <= 0) {
					return 100;
				}
				return Math.max(0, Math.min(99, this.frameApplyIndex * 100 / this.totalPixels()));
			}
			if (this.totalPixels() <= 0) {
				return 100;
			}
			return Math.max(0, Math.min(99, this.nextPixel * 100 / this.totalPixels()));
		}

		private int lastDisplayedProgress() {
			return this.lastDisplayedProgress;
		}

		private void setLastDisplayedProgress(int lastDisplayedProgress) {
			this.lastDisplayedProgress = lastDisplayedProgress;
		}
	}

	private static int[] photoMapIdsToRawIds(MapId[] mapIds) {
		if (mapIds == null) {
			return new int[0];
		}
		int[] rawIds = new int[mapIds.length];
		for (int i = 0; i < mapIds.length; i++) {
			rawIds[i] = mapIds[i] == null ? -1 : mapIds[i].id();
		}
		return rawIds;
	}

	private record PhotoMapSet(MapId[] mapIds, MapItemSavedData[] mapDataSet) {
	}

	private record PreviewMap(MapId previewMapId, MapItemSavedData previewMapData) {
	}

	private record PixelResult(int x, int y, byte color, Throwable error) {
		private static PixelResult success(int x, int y, byte color) {
			return new PixelResult(x, y, color, null);
		}

		private static PixelResult failure(int x, int y, Throwable error) {
			return new PixelResult(x, y, (byte) 0, error);
		}

		private boolean failed() {
			return this.error != null;
		}
	}

	private record FrameResult(byte[] pixels, Throwable error) {
		private static FrameResult success(byte[] pixels) {
			return new FrameResult(pixels, null);
		}

		private static FrameResult failure(Throwable error) {
			return new FrameResult(null, error);
		}

		private boolean failed() {
			return this.error != null;
		}
	}
}
