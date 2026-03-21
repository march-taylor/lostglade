package com.lostglade.server;

import com.lostglade.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CameraCaptureSystem {
	private static final String IT_CAMERA = "it_camera";
	private static final Component PHOTO_NAME = Component.literal("фотография").copy().withStyle(style -> style.withItalic(false));
	private static final int MAP_SIZE = 128;
	private static final int PIXELS_PER_TICK = 192;
	private static final double MAX_DISTANCE = 96.0D;
	private static final float FOV_DEGREES = 70.0F;
	private static final float ENTITY_MARGIN = 0.35F;
	private static final int CAMERA_COOLDOWN_TICKS = 40;
	private static final Map<UUID, RenderJob> ACTIVE_JOBS = new HashMap<>();
	private static final PaletteColor[] PALETTE = buildPalette();

	private CameraCaptureSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CameraCaptureSystem::tick);
	}

	public static boolean tryCapture(ServerPlayer player, ItemStack stack) {
		if (player == null || stack == null || stack.isEmpty() || !stack.is(ModItems.CAMERA)) {
			return false;
		}
		if (!ServerUpgradeUiSystem.hasUpgrade(player, IT_CAMERA)) {
			player.displayClientMessage(Component.literal("Сначала открой технологию Камера."), true);
			return false;
		}
		if (ACTIVE_JOBS.containsKey(player.getUUID())) {
			player.displayClientMessage(Component.literal("Камера уже обрабатывает предыдущий снимок."), true);
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		ItemStack photo = createPhoto(level);
		MapId mapId = photo.get(DataComponents.MAP_ID);
		if (mapId == null) {
			return false;
		}

		boolean inserted = player.getInventory().add(photo);
		if (!inserted) {
			ItemEntity itemEntity = player.drop(photo, false);
			if (itemEntity != null) {
				itemEntity.setPickUpDelay(0);
			}
		}
		ServerMechanicsGateSystem.syncPlayerInventory(player);
		player.getCooldowns().addCooldown(stack, CAMERA_COOLDOWN_TICKS);
		player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5F, 1.35F);

		ACTIVE_JOBS.put(player.getUUID(), RenderJob.capture(player, mapId));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE_JOBS.isEmpty()) {
			return;
		}

		var iterator = ACTIVE_JOBS.entrySet().iterator();
		while (iterator.hasNext()) {
			RenderJob job = iterator.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(job.playerId());
			ServerLevel level = server.getLevel(job.dimension());
			if (player == null || level == null) {
				iterator.remove();
				continue;
			}

			MapItemSavedData data = level.getMapData(job.mapId());
			if (data == null) {
				iterator.remove();
				continue;
			}

			int processed = 0;
			while (processed < PIXELS_PER_TICK && job.nextPixel() < MAP_SIZE * MAP_SIZE) {
				int pixelIndex = job.nextPixel();
				int x = pixelIndex % MAP_SIZE;
				int y = pixelIndex / MAP_SIZE;
				data.setColor(x, y, renderPixel(level, player, job, x, y));
				job.advance();
				processed++;
			}

			if (job.isDone()) {
				player.displayClientMessage(Component.literal("Снимок готов."), true);
				player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4F, 1.4F);
				iterator.remove();
			}
		}
	}

	private static ItemStack createPhoto(ServerLevel level) {
		ItemStack photo = MapItem.create(level, 0, 0, (byte) 0, false, false);
		photo.set(DataComponents.CUSTOM_NAME, PHOTO_NAME);
		return photo;
	}

	private static byte renderPixel(ServerLevel level, ServerPlayer player, RenderJob job, int pixelX, int pixelY) {
		Vec3 direction = computeRayDirection(job, pixelX, pixelY);
		Vec3 start = job.eyePosition();
		Vec3 end = start.add(direction.scale(MAX_DISTANCE));

		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
		double blockDistance = blockHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : start.distanceToSqr(blockHit.getLocation());

		AABB searchBox = player.getBoundingBox().move(start.subtract(player.position())).expandTowards(direction.scale(MAX_DISTANCE)).inflate(ENTITY_MARGIN);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
				player,
				start,
				end,
				searchBox,
				entity -> entity.isPickable() && entity != player && !entity.isSpectator(),
				MAX_DISTANCE * MAX_DISTANCE
		);
		double entityDistance = entityHit == null ? Double.MAX_VALUE : start.distanceToSqr(entityHit.getLocation());

		if (entityHit != null && entityDistance < blockDistance) {
			return toPackedColor(entityRgb(entityHit.getEntity()), distanceBrightness(entityDistance));
		}
		if (blockHit.getType() == HitResult.Type.MISS) {
			return skyColor(level, direction);
		}
		return blockColor(level, blockHit, blockDistance);
	}

	private static Vec3 computeRayDirection(RenderJob job, int pixelX, int pixelY) {
		double aspect = 1.0D;
		double tanHalfFov = Math.tan(Math.toRadians(FOV_DEGREES * 0.5D));
		double sensorX = ((pixelX + 0.5D) / MAP_SIZE * 2.0D - 1.0D) * tanHalfFov * aspect;
		double sensorY = (1.0D - (pixelY + 0.5D) / MAP_SIZE * 2.0D) * tanHalfFov;
		return job.forward()
				.add(job.right().scale(sensorX))
				.add(job.up().scale(sensorY))
				.normalize();
	}

	private static byte blockColor(ServerLevel level, BlockHitResult hit, double distanceSqr) {
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		MapColor mapColor = state.getMapColor(level, pos);
		if (mapColor == MapColor.NONE) {
			return skyColor(level, Vec3.ZERO);
		}

		MapColor.Brightness brightness = shadeForBlock(level, pos, hit, distanceSqr);
		return mapColor.getPackedId(brightness);
	}

	private static MapColor.Brightness shadeForBlock(ServerLevel level, BlockPos pos, BlockHitResult hit, double distanceSqr) {
		MapColor.Brightness faceBrightness = switch (hit.getDirection()) {
			case UP -> MapColor.Brightness.HIGH;
			case DOWN -> MapColor.Brightness.LOWEST;
			default -> MapColor.Brightness.NORMAL;
		};
		if (distanceSqr > 60.0D * 60.0D) {
			faceBrightness = darker(faceBrightness);
		}
		if (!level.canSeeSky(pos.above())) {
			faceBrightness = darker(faceBrightness);
		}
		return faceBrightness;
	}

	private static byte skyColor(ServerLevel level, Vec3 direction) {
		int rgb;
		if (level.dimension() == Level.NETHER) {
			rgb = 0xA54B2A;
		} else if (level.dimension() == Level.END) {
			rgb = 0x6A5E8F;
		} else {
			float daylight = 1.0F - level.getSkyDarken() / 15.0F;
			float vertical = (float) Mth.clamp((direction.y + 1.0D) * 0.5D, 0.0D, 1.0D);
			int top = 0x8FC9FF;
			int horizon = 0xD8F0FF;
			rgb = lerpRgb(horizon, top, daylight * (0.45F + vertical * 0.55F));
			if (level.isRaining()) {
				rgb = lerpRgb(rgb, 0x7286A0, 0.45F);
			}
		}
		return toPackedColor(rgb, MapColor.Brightness.NORMAL);
	}

	private static int entityRgb(Entity entity) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		if (id == null) {
			return 0xD8D8D8;
		}
		int hash = id.toString().hashCode();
		float hue = (hash & 0xFFFF) / 65535.0F;
		float saturation = 0.35F + (((hash >>> 16) & 0xFF) / 255.0F) * 0.4F;
		float brightness = 0.7F + (((hash >>> 24) & 0x7F) / 127.0F) * 0.2F;
		return Mth.hsvToRgb(hue, saturation, brightness);
	}

	private static MapColor.Brightness distanceBrightness(double distanceSqr) {
		double distance = Math.sqrt(distanceSqr);
		if (distance > 64.0D) {
			return MapColor.Brightness.LOWEST;
		}
		if (distance > 42.0D) {
			return MapColor.Brightness.LOW;
		}
		return MapColor.Brightness.NORMAL;
	}

	private static MapColor.Brightness darker(MapColor.Brightness brightness) {
		return switch (brightness) {
			case HIGH -> MapColor.Brightness.NORMAL;
			case NORMAL -> MapColor.Brightness.LOW;
			case LOW -> MapColor.Brightness.LOWEST;
			case LOWEST -> MapColor.Brightness.LOWEST;
		};
	}

	private static byte toPackedColor(int rgb, MapColor.Brightness brightness) {
		PaletteColor nearest = PALETTE[0];
		int bestDistance = Integer.MAX_VALUE;
		for (PaletteColor candidate : PALETTE) {
			int distance = colorDistance(rgb, candidate.rgb());
			if (distance < bestDistance) {
				bestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest.mapColor().getPackedId(brightness);
	}

	private static int colorDistance(int a, int b) {
		int ar = (a >> 16) & 0xFF;
		int ag = (a >> 8) & 0xFF;
		int ab = a & 0xFF;
		int br = (b >> 16) & 0xFF;
		int bg = (b >> 8) & 0xFF;
		int bb = b & 0xFF;
		int dr = ar - br;
		int dg = ag - bg;
		int db = ab - bb;
		return dr * dr + dg * dg + db * db;
	}

	private static int lerpRgb(int from, int to, float delta) {
		int fr = (from >> 16) & 0xFF;
		int fg = (from >> 8) & 0xFF;
		int fb = from & 0xFF;
		int tr = (to >> 16) & 0xFF;
		int tg = (to >> 8) & 0xFF;
		int tb = to & 0xFF;
		int r = Mth.floor(Mth.lerp(delta, fr, tr));
		int g = Mth.floor(Mth.lerp(delta, fg, tg));
		int b = Mth.floor(Mth.lerp(delta, fb, tb));
		return (r << 16) | (g << 8) | b;
	}

	private static PaletteColor[] buildPalette() {
		PaletteColor[] colors = new PaletteColor[64];
		for (int i = 0; i < colors.length; i++) {
			MapColor mapColor = MapColor.byId(i);
			colors[i] = new PaletteColor(mapColor, mapColor.calculateARGBColor(MapColor.Brightness.NORMAL) & 0xFFFFFF);
		}
		return colors;
	}

	private record PaletteColor(MapColor mapColor, int rgb) {
	}

	private static final class RenderJob {
		private final UUID playerId;
		private final net.minecraft.resources.ResourceKey<Level> dimension;
		private final MapId mapId;
		private final Vec3 eyePosition;
		private final Vec3 forward;
		private final Vec3 right;
		private final Vec3 up;
		private int nextPixel;

		private RenderJob(
				UUID playerId,
				net.minecraft.resources.ResourceKey<Level> dimension,
				MapId mapId,
				Vec3 eyePosition,
				Vec3 forward,
				Vec3 right,
				Vec3 up
		) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.mapId = mapId;
			this.eyePosition = eyePosition;
			this.forward = forward;
			this.right = right;
			this.up = up;
		}

		private static RenderJob capture(ServerPlayer player, MapId mapId) {
			Vec3 forward = player.getLookAngle().normalize();
			Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
			Vec3 right = forward.cross(worldUp);
			if (right.lengthSqr() < 1.0E-4D) {
				right = new Vec3(1.0D, 0.0D, 0.0D);
			} else {
				right = right.normalize();
			}
			Vec3 up = right.cross(forward).normalize();
			return new RenderJob(
					player.getUUID(),
					player.level().dimension(),
					mapId,
					player.getEyePosition(),
					forward,
					right,
					up
			);
		}

		private UUID playerId() {
			return this.playerId;
		}

		private net.minecraft.resources.ResourceKey<Level> dimension() {
			return this.dimension;
		}

		private MapId mapId() {
			return this.mapId;
		}

		private Vec3 eyePosition() {
			return this.eyePosition;
		}

		private Vec3 forward() {
			return this.forward;
		}

		private Vec3 right() {
			return this.right;
		}

		private Vec3 up() {
			return this.up;
		}

		private int nextPixel() {
			return this.nextPixel;
		}

		private void advance() {
			this.nextPixel++;
		}

		private boolean isDone() {
			return this.nextPixel >= MAP_SIZE * MAP_SIZE;
		}
	}
}
