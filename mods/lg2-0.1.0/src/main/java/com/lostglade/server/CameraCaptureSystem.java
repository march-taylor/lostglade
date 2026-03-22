package com.lostglade.server;

import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
import com.lostglade.server.camera.bluemap.BlueMapCameraRenderer;
import com.lostglade.server.map.MapImageRenderSystem;
import com.lostglade.server.map.MapPixelProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class CameraCaptureSystem {
	private static final String IT_CAMERA = "it_camera";
	private static final Component PHOTO_NAME = Component.literal("фотография").withStyle(style -> style.withItalic(false));
	private static final int CAMERA_COOLDOWN_TICKS = 40;
	private static final double MAX_DISTANCE = 96.0D;
	private static final float FOV_DEGREES = 70.0F;

	private CameraCaptureSystem() {
	}

	public static void register() {
	}

	public static boolean tryCapture(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || !stack.is(ModItems.CAMERA)) {
			return false;
		}
		if (!ServerUpgradeUiSystem.hasUpgrade(player, IT_CAMERA)) {
			player.displayClientMessage(Component.literal("Сначала открой технологию Камера."), true);
			return false;
		}
		if (MapImageRenderSystem.hasActiveRender(player.getUUID())) {
			player.displayClientMessage(Component.literal("Камера уже обрабатывает предыдущий снимок."), true);
			return false;
		}

		MapPixelProvider provider;
		try {
			provider = CameraPixelProvider.capture(player);
		} catch (Exception exception) {
			player.displayClientMessage(Component.literal("Не удалось подготовить снимок."), true);
			return false;
		}

		boolean started = MapImageRenderSystem.startRender(player, PHOTO_NAME, provider);
		if (!started) {
			return false;
		}

		player.getCooldowns().addCooldown(stack, CAMERA_COOLDOWN_TICKS);
		player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.35F);
		return true;
	}

	private static final class CameraPixelProvider implements MapPixelProvider {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final BlueMapCameraRenderer.PreparedFrame preparedFrame;

		private CameraPixelProvider(UUID playerId, ResourceKey<Level> dimension, BlueMapCameraRenderer.PreparedFrame preparedFrame) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.preparedFrame = preparedFrame;
		}

		private static CameraPixelProvider capture(ServerPlayer player) {
			Vec3 forward = player.getLookAngle().normalize();
			Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
			Vec3 right = forward.cross(worldUp);
			if (right.lengthSqr() < 1.0E-4D) {
				right = new Vec3(1.0D, 0.0D, 0.0D);
			} else {
				right = right.normalize();
			}
			Vec3 up = right.cross(forward).normalize();
			int supersampling = Mth.clamp(Lg2Config.get().cameraRenderSamplesPerAxis, 1, 4);
			BlueMapCameraRenderer.PreparedFrame preparedFrame = BlueMapCameraRenderer.capture(
					player,
					forward,
					right,
					up,
					MAX_DISTANCE,
					FOV_DEGREES,
					supersampling
			);
			return new CameraPixelProvider(player.getUUID(), player.level().dimension(), preparedFrame);
		}

		@Override
		public UUID ownerId() {
			return this.playerId;
		}

		@Override
		public ResourceKey<Level> dimension() {
			return this.dimension;
		}

		@Override
		public boolean prefersWholeFrameRendering() {
			return true;
		}

		@Override
		public Object prepareFrame(MinecraftServer server) {
			return this.preparedFrame;
		}

		@Override
		public byte[] renderPreparedFrame(Object preparedFrame) {
			return BlueMapCameraRenderer.render((BlueMapCameraRenderer.PreparedFrame) preparedFrame);
		}

		@Override
		public boolean isValid(MinecraftServer server) {
			return server.getPlayerList().getPlayer(this.playerId) != null && server.getLevel(this.dimension) != null;
		}

		@Override
		public Component completedMessage() {
			return Component.literal("Снимок готов.");
		}
	}
}
