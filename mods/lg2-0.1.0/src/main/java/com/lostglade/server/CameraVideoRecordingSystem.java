package com.lostglade.server;

import com.lostglade.item.CameraPhotoSettings;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraVideoRecordingSystem {
	private static final int MAX_TARGET_FPS = 20;
	private static final int TARGET_FPS = 10;
	private static final int MAX_DURATION_SECONDS = 10 * 60;
	private static final long MIN_STOP_DELAY_MS = 1_000L;
	private static final Map<UUID, ActiveRecording> RECORDINGS_BY_PLAYER = new ConcurrentHashMap<>();

	private CameraVideoRecordingSystem() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> RECORDINGS_BY_PLAYER.clear());
	}

	public static boolean isRecording(UUID playerId) {
		return playerId != null && RECORDINGS_BY_PLAYER.containsKey(playerId);
	}

	public static boolean hasAnyRecording() {
		return !RECORDINGS_BY_PLAYER.isEmpty();
	}

	public static boolean toggleRecording(ServerPlayer player, CameraPhotoSettings settings) {
		if (player == null || settings == null) {
			return false;
		}
		ActiveRecording current = RECORDINGS_BY_PLAYER.get(player.getUUID());
		if (current != null) {
			long elapsedMs = System.currentTimeMillis() - current.startedAtMs();
			if (elapsedMs < MIN_STOP_DELAY_MS) {
				player.displayClientMessage(Component.literal("Запись видео запускается..."), true);
				return true;
			}
			RendererBotCameraSystem.stopVideoRecording(player.level().getServer(), current.requestId());
			player.displayClientMessage(Component.literal("Останавливаю запись видео..."), true);
			return true;
		}

		if (!RECORDINGS_BY_PLAYER.isEmpty()) {
			player.displayClientMessage(Component.literal("Камера уже записывает другое видео."), true);
			return false;
		}

		RendererBotCameraSystem.VideoRecordingHandle handle = RendererBotCameraSystem.startVideoRecording(
				player,
				settings.mapsWide(),
				settings.mapsHigh(),
				Math.min(MAX_TARGET_FPS, TARGET_FPS),
				MAX_DURATION_SECONDS
		);
		if (handle == null) {
			player.displayClientMessage(Component.literal("Нет активного renderer-клиента для записи видео."), true);
			return false;
		}

		ActiveRecording state = new ActiveRecording(
				player.getUUID(),
				handle.requestId(),
				(ServerLevel) player.level(),
				player.position().x,
				player.position().y,
				player.position().z,
				settings.mapsWide(),
				settings.mapsHigh(),
				System.currentTimeMillis()
		);
		RECORDINGS_BY_PLAYER.put(player.getUUID(), state);
		handle.completionFuture().whenComplete((result, throwable) -> {
			MinecraftServer server = player.level().getServer();
			if (server != null) {
				server.execute(() -> finishRecording(server, state, result, throwable));
			}
		});
		player.displayClientMessage(Component.literal("Запись видео началась. Нажми камеру ещё раз, чтобы остановить."), true);
		return true;
	}

	private static void finishRecording(
			MinecraftServer server,
			ActiveRecording state,
			RendererBotCameraSystem.VideoRecordingResult result,
			Throwable throwable
	) {
		if (state == null) {
			return;
		}
		RECORDINGS_BY_PLAYER.remove(state.playerId(), state);
		ServerPlayer player = server.getPlayerList().getPlayer(state.playerId());
		if (throwable != null || result == null) {
			if (player != null) {
				player.displayClientMessage(Component.literal("Запись видео не удалась."), true);
			}
			return;
		}

		if (player == null) {
			return;
		}

		ItemStack videoItem = CameraAnimatedMapPlaybackSystem.createVideoItem(
				player,
				CameraCaptureSystem.createCompletedPhotoName(server),
				state.mapsWide(),
				state.mapsHigh(),
				state.requestId().toString(),
				result.durationMs(),
				Math.max(1, result.fps()),
				result.previewPixels(),
				result.fullPixels()
		);
		if (videoItem.isEmpty()) {
			if (player != null) {
				player.displayClientMessage(Component.literal("Не удалось собрать видео-карту."), true);
			}
			return;
		}

		giveOrDrop(player, videoItem);
		ServerMechanicsGateSystem.syncPlayerInventory(player);
		player.displayClientMessage(Component.literal("Видео готово."), true);
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty()) {
			return;
		}
		boolean inserted = player.getInventory().add(stack);
		if (!inserted) {
			ItemEntity itemEntity = player.drop(stack, false);
			if (itemEntity != null) {
				itemEntity.setPickUpDelay(0);
			}
		}
	}

	private record ActiveRecording(
			UUID playerId,
			UUID requestId,
			ServerLevel level,
			double x,
			double y,
			double z,
			int mapsWide,
			int mapsHigh,
			long startedAtMs
	) {
	}
}
