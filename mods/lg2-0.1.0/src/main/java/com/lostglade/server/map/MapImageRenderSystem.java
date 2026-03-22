package com.lostglade.server.map;

import com.lostglade.Lg2;
import com.lostglade.config.Lg2Config;
import com.lostglade.server.ServerMechanicsGateSystem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class MapImageRenderSystem {
	private static final int MAP_SIZE = 128;
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
		ServerLevel level = (ServerLevel) player.level();
		ItemStack map = MapItem.create(level, 0, 0, (byte) 0, false, false);
		MapId mapId = map.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return false;
		}
		map.set(DataComponents.CUSTOM_NAME, itemName);

		boolean inserted = player.getInventory().add(map);
		if (!inserted) {
			ItemEntity itemEntity = player.drop(map, false);
			if (itemEntity != null) {
				itemEntity.setPickUpDelay(0);
			}
		}
		ServerMechanicsGateSystem.syncPlayerInventory(player);
		RenderJob job = new RenderJob(player.getUUID(), mapId, provider);
		PLAYER_JOBS.put(player.getUUID(), job);
		QUEUE.offer(player.getUUID());
		if (activePlayerId == null) {
			activePlayerId = player.getUUID();
			player.displayClientMessage(Component.literal("Снимок поставлен в рендер."), true);
		} else {
			player.displayClientMessage(Component.literal("Снимок добавлен в очередь рендера."), true);
		}
		return true;
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

		MapItemSavedData mapData = level.getMapData(job.mapId());
		if (mapData == null) {
			removeJob(job.playerId());
			pollNextActive();
			return;
		}

		if (job.provider().prefersWholeFrameRendering()) {
			tickWholeFrameJob(server, player, level, mapData, job);
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
				mapData.setColor(result.x(), result.y(), result.color());
			}
			job.finishDispatchedPixel();
			processed++;
		}

		long tickStart = System.nanoTime();
		while (job.canDispatchMore() && job.nextPixel() < MAP_SIZE * MAP_SIZE) {
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
			job.provider().onCompleted(server);
			player.displayClientMessage(job.provider().completedMessage(), true);
			player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
			removeJob(job.playerId());
			pollNextActive();
		}
	}

	private static void tickWholeFrameJob(MinecraftServer server, ServerPlayer player, ServerLevel level, MapItemSavedData mapData, RenderJob job) {
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
		if (frame == null || frame.length < MAP_SIZE * MAP_SIZE) {
			Lg2.LOGGER.error("Map frame render returned invalid frame for player {}", job.playerId());
			player.displayClientMessage(Component.literal("Рендер снимка остановлен: кадр повреждён."), true);
			removeJob(job.playerId());
			pollNextActive();
			return;
		}

		int applied = 0;
		while (applied < FRAME_PIXELS_APPLIED_PER_TICK && job.frameApplyIndex() < MAP_SIZE * MAP_SIZE) {
			int pixelIndex = job.frameApplyIndex();
			mapData.setColor(pixelIndex % MAP_SIZE, pixelIndex / MAP_SIZE, frame[pixelIndex]);
			job.advanceFrameApplyIndex();
			applied++;
		}

		if (job.frameApplyIndex() >= MAP_SIZE * MAP_SIZE) {
			job.provider().onCompleted(server);
			player.displayClientMessage(job.provider().completedMessage(), true);
			player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
			removeJob(job.playerId());
			pollNextActive();
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
		private final MapId mapId;
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

		private RenderJob(UUID playerId, MapId mapId, MapPixelProvider provider) {
			this.playerId = playerId;
			this.mapId = mapId;
			this.provider = provider;
		}

		private UUID playerId() {
			return this.playerId;
		}

		private MapId mapId() {
			return this.mapId;
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
			return this.nextPixel >= MAP_SIZE * MAP_SIZE;
		}
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
