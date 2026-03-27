package com.lostglade.server;

import com.lostglade.config.Lg2Config;
import com.lostglade.item.CameraPhotoSettings;
import com.lostglade.item.ModItems;
import com.lostglade.server.camera.bluemap.BlueMapCameraRenderer;
import com.lostglade.server.map.MapImageRenderSystem;
import com.lostglade.server.map.MapPixelProvider;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

public final class CameraCaptureSystem {
	private static final String IT_CAMERA = "it_camera";
	private static final Identifier CAMERA_SHUTTER_SOUND_ID = Identifier.fromNamespaceAndPath("lg2", "camera_shutter");
	private static final Holder<SoundEvent> CAMERA_SHUTTER_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(CAMERA_SHUTTER_SOUND_ID));
	private static final int CAMERA_COOLDOWN_TICKS = 40;
	private static final double MAX_DISTANCE = 96.0D;
	private static final float FOV_DEGREES = 70.0F;
	private static final double MAX_SHUTTER_SOUND_DISTANCE_SQR = 24.0D * 24.0D;
	private static final float SHUTTER_SOUND_VOLUME = 0.45F;
	private static final float SHUTTER_SOUND_PITCH = 1.0F;
	private static final DateTimeFormatter PHOTO_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
			player.displayClientMessage(cameraBusyMessage(player), true);
			return false;
		}

		MapPixelProvider provider;
		try {
			provider = CameraPixelProvider.capture(player);
		} catch (Exception exception) {
			player.displayClientMessage(capturePrepareFailedMessage(player), true);
			return false;
		}

		boolean started = MapImageRenderSystem.startRender(player, createQueuedPhotoName(0), provider);
		if (!started) {
			return false;
		}

		player.getCooldowns().addCooldown(stack, CAMERA_COOLDOWN_TICKS);
		playShutterFeedback(player);
		return true;
	}

	public static Component createQueuedPhotoName(int progressPercent) {
		int clamped = Mth.clamp(progressPercent, 0, 100);
		return Component.literal(clamped + "%").withStyle(style -> style.withItalic(false));
	}

	public static Component createCompletedPhotoName() {
		String timestamp = LocalDateTime.now(ZoneId.systemDefault()).format(PHOTO_NAME_FORMATTER);
		return Component.literal(timestamp).withStyle(style -> style.withItalic(false));
	}

	private static Component cameraBusyMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return Component.literal("Камѣра уже обрабатываеть предыдущее снимокъ.");
		}
		if (locale.startsWith("uk")) {
			return Component.literal("Камера вже обробляє попередній знімок.");
		}
		if (locale.startsWith("ja")) {
			return Component.literal("カメラは前の写真をまだ処理中です。");
		}
		if (locale.startsWith("ru")) {
			return Component.literal("Камера уже обрабатывает предыдущий снимок.");
		}
		return Component.literal("The camera is still processing the previous photo.");
	}

	private static Component capturePrepareFailedMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return Component.literal("Не удалось подготовить снимокъ.");
		}
		if (locale.startsWith("uk")) {
			return Component.literal("Не вдалося підготувати знімок.");
		}
		if (locale.startsWith("ja")) {
			return Component.literal("写真の準備に失敗しました。");
		}
		if (locale.startsWith("ru")) {
			return Component.literal("Не удалось подготовить снимок.");
		}
		return Component.literal("Couldn't prepare the photo.");
	}

	public static Component captureCompletedMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return Component.literal("Снимокъ готовъ.");
		}
		if (locale.startsWith("uk")) {
			return Component.literal("Знімок готовий.");
		}
		if (locale.startsWith("ja")) {
			return Component.literal("写真の準備ができました。");
		}
		if (locale.startsWith("ru")) {
			return Component.literal("Снимок готов.");
		}
		return Component.literal("Photo ready.");
	}

	public static Component queuedForRenderMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return Component.literal("Снимокъ поставленъ въ рендеръ.");
		}
		if (locale.startsWith("uk")) {
			return Component.literal("Знімок поставлено в рендер.");
		}
		if (locale.startsWith("ja")) {
			return Component.literal("写真をレンダーに送信しました。");
		}
		if (locale.startsWith("ru")) {
			return Component.literal("Снимок поставлен в рендер.");
		}
		return Component.literal("Photo sent to render.");
	}

	public static Component addedToRenderQueueMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return Component.literal("Снимокъ добавленъ въ очередь рендера.");
		}
		if (locale.startsWith("uk")) {
			return Component.literal("Знімок додано в чергу рендера.");
		}
		if (locale.startsWith("ja")) {
			return Component.literal("写真をレンダー待ちキューに追加しました。");
		}
		if (locale.startsWith("ru")) {
			return Component.literal("Снимок добавлен в очередь рендера.");
		}
		return Component.literal("Photo added to the render queue.");
	}

	private static String locale(ServerPlayer player) {
		if (player == null || player.clientInformation() == null || player.clientInformation().language() == null) {
			return "en_us";
		}
		return player.clientInformation().language().toLowerCase(Locale.ROOT);
	}

	private static void playShutterFeedback(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 origin = player.getEyePosition().add(player.getLookAngle().normalize().scale(0.35D));
		long seed = level.getRandom().nextLong();

		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(origin.x, origin.y, origin.z) > MAX_SHUTTER_SOUND_DISTANCE_SQR) {
				continue;
			}

			Holder<SoundEvent> sound = PolymerResourcePackUtils.hasMainPack(viewer)
					? CAMERA_SHUTTER_SOUND
					: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.UI_BUTTON_CLICK.value());
			float pitch = PolymerResourcePackUtils.hasMainPack(viewer) ? SHUTTER_SOUND_PITCH : 1.2F;
			viewer.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, origin.x, origin.y, origin.z, SHUTTER_SOUND_VOLUME, pitch, seed));
		}
	}

	private static final class CameraPixelProvider implements MapPixelProvider {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final int mapsWide;
		private final int mapsHigh;
		private final BlueMapCameraRenderer.PreparedFrame previewPreparedFrame;
		private final BlueMapCameraRenderer.PreparedFrame preparedFrame;

		private CameraPixelProvider(UUID playerId, ResourceKey<Level> dimension, int mapsWide, int mapsHigh, BlueMapCameraRenderer.PreparedFrame previewPreparedFrame, BlueMapCameraRenderer.PreparedFrame preparedFrame) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.mapsWide = mapsWide;
			this.mapsHigh = mapsHigh;
			this.previewPreparedFrame = previewPreparedFrame;
			this.preparedFrame = preparedFrame;
		}

		private static CameraPixelProvider capture(ServerPlayer player) {
			CameraPhotoSettings settings = CameraPhotoSettings.read(player.getMainHandItem());
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
			BlueMapCameraRenderer.PreparedFrame previewPreparedFrame = BlueMapCameraRenderer.capture(
					player,
					forward,
					right,
					up,
					MAX_DISTANCE,
					FOV_DEGREES,
					supersampling,
					1,
					1
			);
			BlueMapCameraRenderer.PreparedFrame preparedFrame = settings.mapsWide() == 1 && settings.mapsHigh() == 1
					? previewPreparedFrame
					: BlueMapCameraRenderer.capture(
							player,
							forward,
							right,
							up,
							MAX_DISTANCE,
							FOV_DEGREES,
							supersampling,
							settings.mapsWide(),
							settings.mapsHigh()
					);
			return new CameraPixelProvider(player.getUUID(), player.level().dimension(), settings.mapsWide(), settings.mapsHigh(), previewPreparedFrame, preparedFrame);
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
		public byte[] renderImmediatePreview(MinecraftServer server) {
			return BlueMapCameraRenderer.render(this.previewPreparedFrame);
		}

		@Override
		public int mapTilesWide() {
			return this.mapsWide;
		}

		@Override
		public int mapTilesHigh() {
			return this.mapsHigh;
		}

		@Override
		public boolean isValid(MinecraftServer server) {
			return server.getPlayerList().getPlayer(this.playerId) != null && server.getLevel(this.dimension) != null;
		}

		@Override
		public Component completedMessage() {
			return Component.literal("Photo ready.");
		}
	}
}
