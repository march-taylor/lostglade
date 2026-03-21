package com.lostglade.server;

import com.lostglade.item.ModItems;
import com.lostglade.server.map.BlockTintProvider;
import com.lostglade.server.map.BlockTextureRaycaster;
import com.lostglade.server.map.MapImageRenderSystem;
import com.lostglade.server.map.MapPixelProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class CameraCaptureSystem {
	private static final String IT_CAMERA = "it_camera";
	private static final Component PHOTO_NAME = Component.literal("фотография").withStyle(style -> style.withItalic(false));
	private static final int CAMERA_COOLDOWN_TICKS = 40;
	private static final double MAX_DISTANCE = 96.0D;
	private static final float FOV_DEGREES = 70.0F;
	private static final float ENTITY_MARGIN = 0.35F;
	private static final int MAX_TRANSPARENT_STEPS = 12;

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

		boolean started = MapImageRenderSystem.startRender(player, PHOTO_NAME, CameraPixelProvider.capture(player));
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
		private final Vec3 eyePosition;
		private final Vec3 forward;
		private final Vec3 right;
		private final Vec3 up;

		private CameraPixelProvider(UUID playerId, ResourceKey<Level> dimension, Vec3 eyePosition, Vec3 forward, Vec3 right, Vec3 up) {
			this.playerId = playerId;
			this.dimension = dimension;
			this.eyePosition = eyePosition;
			this.forward = forward;
			this.right = right;
			this.up = up;
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
			return new CameraPixelProvider(
					player.getUUID(),
					player.level().dimension(),
					player.getEyePosition(),
					forward,
					right,
					up
			);
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
		public PreparedPixel preparePixel(MinecraftServer server, int x, int y) {
			ServerPlayer player = server.getPlayerList().getPlayer(this.playerId);
			ServerLevel level = server.getLevel(this.dimension);
			if (player == null || level == null) {
				return new PreparedPixel(x, y, new SkySample(0));
			}

			Vec3 direction = computeRayDirection(x, y);
			int sky = skyColor(level, direction);
			return new PreparedPixel(x, y, captureSample(level, player, direction, sky));
		}

		@Override
		public byte renderPreparedPixel(PreparedPixel pixel) {
			Object payload = pixel.payload();
			if (payload instanceof SkySample skySample) {
				return toPackedColor(skySample.rgb(), distanceBrightness(0.0D));
			}
			if (payload instanceof EntitySample entitySample) {
				return toPackedColor(entitySample.rgb(), distanceBrightness(entitySample.distance()));
			}
			if (payload instanceof BlockRaySample blockSample) {
				for (BlockCandidate candidate : blockSample.candidates()) {
					BlockTextureRaycaster.BlockTraceResult trace = BlockTextureRaycaster.trace(
							candidate.state(),
							candidate.blockPos(),
							candidate.traceOrigin(),
							blockSample.direction(),
							candidate.tintLayers()
					);
					if (trace != null) {
						int lit = applySnapshotLighting(trace.rgb(), trace.face(), candidate.canSeeSkyAbove(), candidate.distance());
						return toPackedColor(lit, distanceBrightness(candidate.distance()));
					}
				}
				if (blockSample.entityRgb() != null) {
					return toPackedColor(blockSample.entityRgb(), distanceBrightness(blockSample.entityDistance()));
				}
				return toPackedColor(blockSample.skyRgb(), distanceBrightness(0.0D));
			}
			return 0;
		}

		@Override
		public Component completedMessage() {
			return Component.literal("Снимок готов.");
		}

		private Vec3 computeRayDirection(int pixelX, int pixelY) {
			double aspect = 1.0D;
			double tanHalfFov = Math.tan(Math.toRadians(FOV_DEGREES * 0.5D));
			double sensorX = ((pixelX + 0.5D) / 128.0D * 2.0D - 1.0D) * tanHalfFov * aspect;
			double sensorY = (1.0D - (pixelY + 0.5D) / 128.0D * 2.0D) * tanHalfFov;
			return this.forward
					.add(this.right.scale(sensorX))
					.add(this.up.scale(sensorY))
					.normalize();
		}

		private Object captureSample(ServerLevel level, ServerPlayer viewer, Vec3 direction, int skyRgb) {
			Vec3 current = this.eyePosition;
			EntityHitResult nearestEntity = traceEntity(level, viewer, this.eyePosition, direction);
			double nearestEntityDistance = nearestEntity == null ? Double.MAX_VALUE : this.eyePosition.distanceTo(nearestEntity.getLocation());
			Integer entityRgb = nearestEntity == null ? null : entityRgb(nearestEntity.getEntity());
			BlockCandidate[] candidates = new BlockCandidate[MAX_TRANSPARENT_STEPS];
			int candidateCount = 0;
			for (int steps = 0; steps < MAX_TRANSPARENT_STEPS; steps++) {
				Vec3 end = this.eyePosition.add(direction.scale(MAX_DISTANCE));
				BlockHitResult blockHit = level.clip(new ClipContext(current, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, viewer));
				if (blockHit.getType() == HitResult.Type.MISS) {
					if (candidateCount == 0) {
						return entityRgb != null ? new EntitySample(entityRgb, nearestEntityDistance) : new SkySample(skyRgb);
					}
					break;
				}
				double traveled = this.eyePosition.distanceTo(blockHit.getLocation());
				if (nearestEntity != null && nearestEntityDistance < traveled) {
					if (candidateCount == 0) {
						return new EntitySample(entityRgb, nearestEntityDistance);
					}
					break;
				}
				net.minecraft.world.level.block.state.BlockState hitState = level.getBlockState(blockHit.getBlockPos());
				candidates[candidateCount++] = new BlockCandidate(
						hitState,
						blockHit.getBlockPos().immutable(),
						current,
						traveled,
						level.canSeeSky(blockHit.getBlockPos().above()),
						BlockTintProvider.capture(level, blockHit.getBlockPos(), hitState)
				);
				current = blockHit.getLocation().add(direction.scale(0.002D));
			}
			if (candidateCount == 0) {
				return entityRgb != null ? new EntitySample(entityRgb, nearestEntityDistance) : new SkySample(skyRgb);
			}
			BlockCandidate[] resolved = new BlockCandidate[candidateCount];
			System.arraycopy(candidates, 0, resolved, 0, candidateCount);
			return new BlockRaySample(direction, skyRgb, entityRgb, nearestEntityDistance, resolved);
		}

		private EntityHitResult traceEntity(ServerLevel level, ServerPlayer viewer, Vec3 start, Vec3 direction) {
			Vec3 end = this.eyePosition.add(direction.scale(MAX_DISTANCE));
			AABB box = viewer.getBoundingBox()
					.move(start.subtract(viewer.position()))
					.expandTowards(direction.scale(MAX_DISTANCE))
					.inflate(ENTITY_MARGIN);
			return ProjectileUtil.getEntityHitResult(
					viewer,
					start,
					end,
					box,
					entity -> entity.isPickable() && entity != viewer && !entity.isSpectator(),
					MAX_DISTANCE * MAX_DISTANCE
			);
		}

		private int applySnapshotLighting(int rgb, net.minecraft.core.Direction face, boolean canSeeSkyAbove, double distance) {
			float shade = switch (face) {
				case UP -> 1.0F;
				case DOWN -> 0.55F;
				default -> 0.82F;
			};
			if (!canSeeSkyAbove) {
				shade *= 0.72F;
			}
			if (distance > 64.0D) {
				shade *= 0.75F;
			}
			int r = Mth.clamp((int) (((rgb >> 16) & 0xFF) * shade), 0, 255);
			int g = Mth.clamp((int) (((rgb >> 8) & 0xFF) * shade), 0, 255);
			int b = Mth.clamp((int) ((rgb & 0xFF) * shade), 0, 255);
			return (r << 16) | (g << 8) | b;
		}

		private record SkySample(int rgb) {
		}

		private record EntitySample(int rgb, double distance) {
		}

		private record BlockRaySample(Vec3 direction, int skyRgb, Integer entityRgb, double entityDistance, BlockCandidate[] candidates) {
		}

		private record BlockCandidate(net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos blockPos, Vec3 traceOrigin, double distance, boolean canSeeSkyAbove, int[] tintLayers) {
		}
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

	private static int skyColor(ServerLevel level, Vec3 direction) {
		if (level.dimension() == Level.NETHER) {
			return 0xA54B2A;
		}
		if (level.dimension() == Level.END) {
			return 0x6A5E8F;
		}
		float daylight = 1.0F - level.getSkyDarken() / 15.0F;
		float vertical = (float) Mth.clamp((direction.y + 1.0D) * 0.5D, 0.0D, 1.0D);
		int top = 0x8FC9FF;
		int horizon = 0xD8F0FF;
		int rgb = lerpRgb(horizon, top, daylight * (0.45F + vertical * 0.55F));
		if (level.isRaining()) {
			rgb = lerpRgb(rgb, 0x7286A0, 0.45F);
		}
		return rgb;
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

	private static byte toPackedColor(int rgb, net.minecraft.world.level.material.MapColor.Brightness brightness) {
		net.minecraft.world.level.material.MapColor nearest = net.minecraft.world.level.material.MapColor.NONE;
		int bestDistance = Integer.MAX_VALUE;
		for (int i = 0; i < 64; i++) {
			net.minecraft.world.level.material.MapColor candidate = net.minecraft.world.level.material.MapColor.byId(i);
			int candidateRgb = candidate.calculateARGBColor(net.minecraft.world.level.material.MapColor.Brightness.NORMAL) & 0xFFFFFF;
			int distance = colorDistance(rgb, candidateRgb);
			if (distance < bestDistance) {
				bestDistance = distance;
				nearest = candidate;
			}
		}
		return nearest.getPackedId(brightness);
	}

	private static net.minecraft.world.level.material.MapColor.Brightness distanceBrightness(double distance) {
		if (distance > 64.0D) {
			return net.minecraft.world.level.material.MapColor.Brightness.LOWEST;
		}
		if (distance > 42.0D) {
			return net.minecraft.world.level.material.MapColor.Brightness.LOW;
		}
		return net.minecraft.world.level.material.MapColor.Brightness.NORMAL;
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
}
