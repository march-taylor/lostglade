package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.CameraPhotoSettings;
import com.lostglade.item.ModItems;
import com.lostglade.server.map.MapImageRenderSystem;
import com.lostglade.server.map.MapPixelProvider;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.UUID;

public final class CameraCaptureSystem {
	private static final String IT_CAMERA = "it_camera";
	private static final Identifier CAMERA_SHUTTER_SOUND_ID = Identifier.fromNamespaceAndPath("lg2", "camera_shutter");
	private static final Holder<SoundEvent> CAMERA_SHUTTER_SOUND = Holder.direct(SoundEvent.createVariableRangeEvent(CAMERA_SHUTTER_SOUND_ID));
	private static final int CAMERA_COOLDOWN_TICKS = 40;
	private static final double MAX_SHUTTER_SOUND_DISTANCE_SQR = 24.0D * 24.0D;
	private static final float SHUTTER_SOUND_VOLUME = 0.45F;
	private static final float SHUTTER_SOUND_PITCH = 1.0F;
	private static final long TICKS_PER_DAY = 24_000L;
	private static final long TICKS_PER_HOUR = 1_000L;
	private static final long MINUTES_PER_DAY = 24L * 60L;

	private CameraCaptureSystem() {
	}

	public static void register() {
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.PASS;
			}
			if (!isLeftClickCameraTrigger(serverPlayer, hand)) {
				return InteractionResult.PASS;
			}
			tryCapture(serverPlayer, serverPlayer.getItemInHand(hand));
			return InteractionResult.SUCCESS;
		});
		AttackEntityCallback.EVENT.register(CameraCaptureSystem::onAttackEntity);
	}

	public static boolean handleLeftClickAir(ServerPlayer player, InteractionHand hand) {
		if (!isLeftClickCameraTrigger(player, hand)) {
			return false;
		}
		if (hasAttackTarget(player)) {
			return false;
		}
		tryCapture(player, player.getItemInHand(hand));
		return true;
	}

	public static boolean tryCapture(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || !stack.is(ModItems.CAMERA)) {
			return false;
		}
		if (!ServerUpgradeUiSystem.hasUpgrade(player, IT_CAMERA)) {
			player.displayClientMessage(Component.literal("Сначала открой технологию Камера."), true);
			return false;
		}
		CameraPhotoSettings settings = CameraPhotoSettings.read(stack);
		if (settings.isVideoMode()) {
			if (MapImageRenderSystem.hasActiveRender(player.getUUID())) {
				player.displayClientMessage(cameraBusyMessage(player), true);
				return false;
			}
			return CameraVideoRecordingSystem.toggleRecording(player, settings);
		}
		if (MapImageRenderSystem.hasActiveRender(player.getUUID())) {
			player.displayClientMessage(cameraBusyMessage(player), true);
			return false;
		}
		MinecraftServer server = player.level().getServer();
		if (server == null || !RendererBotCameraSystem.hasReadyBot(server)) {
			player.displayClientMessage(noActiveRendererClientMessage(player), true);
			return false;
		}

		MapPixelProvider provider;
		try {
			provider = createPixelProvider(player, settings);
		} catch (Exception exception) {
			player.displayClientMessage(capturePrepareFailedMessage(player), true);
			return false;
		}
		playShutterFeedback(player);

		boolean started = MapImageRenderSystem.startRender(player, createCompletedPhotoName(player.level().getServer()), provider);
		if (!started) {
			return false;
		}

		player.getCooldowns().addCooldown(stack, CAMERA_COOLDOWN_TICKS);
		return true;
	}

	public static Component createQueuedPhotoName(int progressPercent) {
		int clamped = Mth.clamp(progressPercent, 0, 100);
		return Component.literal(clamped + "%").withStyle(style -> style.withItalic(false));
	}

	public static Component createCompletedPhotoName(MinecraftServer server) {
		long dayTime = 0L;
		if (server != null && server.overworld() != null) {
			dayTime = Math.max(0L, server.overworld().getDayTime());
		}
		long dayNumber = Math.floorDiv(dayTime, TICKS_PER_DAY) + 1L;
		long ticksInDay = Math.floorMod(dayTime, TICKS_PER_DAY);
		int hour = (int) ((ticksInDay / TICKS_PER_HOUR + 6L) % 24L);
		int minute = (int) (((ticksInDay % TICKS_PER_HOUR) * MINUTES_PER_DAY) / TICKS_PER_DAY);
		String timestamp = String.format(Locale.ROOT, "%d - %02d:%02d", dayNumber, hour, minute);
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

	private static Component noActiveRendererClientMessage(ServerPlayer player) {
		String locale = locale(player);
		if (locale.startsWith("rpr")) {
			return Component.literal("Нѣтъ активнаго рендеръ-кліента для снимка.");
		}
		if (locale.startsWith("uk")) {
			return Component.literal("Немає активного рендер-клієнта для знімка.");
		}
		if (locale.startsWith("ja")) {
			return Component.literal("写真用のアクティブなレンダークライアントがいません。");
		}
		if (locale.startsWith("ru")) {
			return Component.literal("Нет активного рендер-клиента для снимка.");
		}
		return Component.literal("No active renderer client is available for photos.");
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

	private static MapPixelProvider createPixelProvider(ServerPlayer player, CameraPhotoSettings settings) {
		RendererBotCameraSystem.ClientCaptureHandle captureHandle = RendererBotCameraSystem.requestPhotoCapture(
				player,
				settings.mapsWide(),
				settings.mapsHigh()
		);
		if (captureHandle == null) {
			throw new IllegalStateException("No active renderer client is available");
		}
		return new RendererBotPixelProvider(
				player.getUUID(),
				player.level().dimension(),
				settings.mapsWide(),
				settings.mapsHigh(),
				captureHandle
		);
	}

	private static InteractionResult onAttackEntity(
			net.minecraft.world.entity.player.Player player,
			Level world,
			InteractionHand hand,
			Entity entity,
			EntityHitResult hitResult
	) {
		if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}
		if (!isLeftClickCameraTrigger(serverPlayer, hand)) {
			return InteractionResult.PASS;
		}
		tryCapture(serverPlayer, serverPlayer.getItemInHand(hand));
		return InteractionResult.SUCCESS;
	}

	private static boolean isLeftClickCameraTrigger(ServerPlayer player, InteractionHand hand) {
		return player != null
				&& hand == InteractionHand.MAIN_HAND
				&& player.isAlive()
				&& !player.isSpectator()
				&& player.getItemInHand(hand).is(ModItems.CAMERA);
	}

	private static boolean hasAttackTarget(ServerPlayer player) {
		double reach = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
		HitResult hitResult = player.pick(reach, 1.0F, false);
		return hitResult != null && hitResult.getType() != HitResult.Type.MISS;
	}

	private static final class RendererBotPixelProvider implements MapPixelProvider {
		private final UUID playerId;
		private final ResourceKey<Level> dimension;
		private final int mapsWide;
		private final int mapsHigh;
		private final RendererBotCameraSystem.ClientCaptureHandle captureHandle;
		private final String sourceKey;

		private RendererBotPixelProvider(UUID playerId, ResourceKey<Level> dimension, int mapsWide, int mapsHigh, RendererBotCameraSystem.ClientCaptureHandle captureHandle) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.mapsWide = mapsWide;
			this.mapsHigh = mapsHigh;
			this.captureHandle = captureHandle;
			this.sourceKey = captureHandle.requestId().toString();
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
			return this.captureHandle;
		}

		@Override
		public byte[] renderPreparedFrame(Object preparedFrame) {
			return ((RendererBotCameraSystem.ClientCaptureHandle) preparedFrame).awaitFull();
		}

		@Override
		public byte[] renderImmediatePreviewFromPreparedFrame(Object preparedFrame) {
			return ((RendererBotCameraSystem.ClientCaptureHandle) preparedFrame).awaitPreview();
		}

		@Override
		public byte[] renderImmediatePreview(MinecraftServer server) {
			return this.captureHandle.awaitPreview();
		}

		@Override
		public boolean immediatePreviewMatchesPrimaryFrame() {
			return this.mapsWide == 1 && this.mapsHigh == 1;
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
		public String sourceKey() {
			return this.sourceKey;
		}
	}
}
