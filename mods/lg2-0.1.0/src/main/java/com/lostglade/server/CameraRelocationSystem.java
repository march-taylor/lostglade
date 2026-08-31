package com.lostglade.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lostglade.Lg2;
import com.lostglade.block.CameraBlock;
import com.lostglade.block.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A mobile camera keeps one stable Bluetooth identity while its physical block
 * is moved by a piston. Rendering follows a tiny Interaction anchor that moves
 * at the same progress as vanilla's PistonMovingBlockEntity.
 */
public final class CameraRelocationSystem {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "lg2-mobile-camera-identities.json";
	private static final String CAMERA_ANCHOR_TAG = "lg2_piston_camera_anchor";
	private static final long PERSISTENCE_DEBOUNCE_TICKS = 100L;

	private static final Map<CameraKey, MobileCamera> CAMERAS_BY_IDENTITY = new LinkedHashMap<>();
	private static final Map<CameraKey, MobileCamera> CAMERAS_BY_PHYSICAL_POSITION = new HashMap<>();
	private static final Map<CameraKey, PistonCameraMotion> ACTIVE_MOTIONS_BY_SOURCE = new HashMap<>();
	private static boolean loaded;
	private static boolean dirty;
	private static long nextPersistenceAttemptTick;

	private CameraRelocationSystem() {
	}

	public static void register() {
		loaded = false;
		dirty = false;
		CAMERAS_BY_IDENTITY.clear();
		CAMERAS_BY_PHYSICAL_POSITION.clear();
		ACTIVE_MOTIONS_BY_SOURCE.clear();
		nextPersistenceAttemptTick = 0L;
		ServerLifecycleEvents.SERVER_STARTED.register(CameraRelocationSystem::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save(server);
			clearRuntimeState();
		});
		ServerTickEvents.END_SERVER_TICK.register(CameraRelocationSystem::tick);
	}

	/** Registers all camera blocks before vanilla replaces them with moving-piston blocks. */
	public static void preparePistonMove(Level level, Direction movementDirection, List<BlockPos> pushedBlocks) {
		if (!(level instanceof ServerLevel serverLevel)
				|| movementDirection == null
				|| pushedBlocks == null
				|| pushedBlocks.isEmpty()) {
			return;
		}
		ensureLoaded(serverLevel.getServer());
		for (BlockPos source : pushedBlocks) {
			if (source == null || !serverLevel.getBlockState(source).is(ModBlocks.CAMERA)) {
				continue;
			}
			BlockPos immutableSource = source.immutable();
			BlockPos destination = source.relative(movementDirection).immutable();
			MobileCamera camera = resolveOrCreateCamera(serverLevel, immutableSource);
			if (camera == null || camera.anchorUuid() == null) {
				continue;
			}
			UUID displayUuid = CameraBlock.getCameraDisplayEntityUuids(serverLevel, immutableSource)
					.stream()
					.findFirst()
					.orElse(null);
			PistonCameraMotion motion = new PistonCameraMotion(camera, immutableSource, destination, displayUuid);
			ACTIVE_MOTIONS_BY_SOURCE.put(new CameraKey(serverLevel.dimension(), immutableSource), motion);
			camera.setPhysicalPosition(immutableSource);
			updateCameraTransform(serverLevel, camera, CameraBlock.captureBaseOrigin(immutableSource));
			RendererBotCameraSystem.handoffLiveCameraStreamsToEntity(
					serverLevel,
					camera.identityPosition(),
					camera.anchorUuid(),
					camera.anchor().getX(),
					camera.anchor().getY(),
					camera.anchor().getZ(),
					camera.anchor().getYRot(),
					camera.anchor().getXRot()
			);
		}
	}

	/** Advances the render anchor and ItemDisplay using vanilla's 0..1 piston progress. */
	public static void advancePistonMove(ServerLevel level, BlockPos source, BlockPos destination, float progress) {
		if (level == null || source == null || destination == null) {
			return;
		}
		PistonCameraMotion motion = ACTIVE_MOTIONS_BY_SOURCE.get(new CameraKey(level.dimension(), source));
		if (motion == null || !motion.destination().equals(destination)) {
			return;
		}
		float clampedProgress = Math.clamp(progress, 0.0F, 1.0F);
		Vec3 position = CameraBlock.captureBaseOrigin(motion.source()).lerp(CameraBlock.captureBaseOrigin(motion.destination()), clampedProgress);
		updateCameraTransform(level, motion.camera(), position);
		CameraBlock.movePistonCameraDisplay(level, motion.source(), motion.destination(), motion.displayUuid(), position, motion.camera().yaw(), motion.camera().pitch());
	}

	/** Completes the move only after vanilla has restored the camera block at the target. */
	public static boolean finishPistonMove(ServerLevel level, BlockPos source, BlockPos destination) {
		if (level == null || source == null || destination == null || !level.getBlockState(destination).is(ModBlocks.CAMERA)) {
			return false;
		}
		PistonCameraMotion motion = ACTIVE_MOTIONS_BY_SOURCE.remove(new CameraKey(level.dimension(), source));
		if (motion == null || !motion.destination().equals(destination)) {
			return false;
		}
		MobileCamera camera = motion.camera();
		CameraKey oldPhysicalKey = new CameraKey(level.dimension(), camera.physicalPosition());
		if (CAMERAS_BY_PHYSICAL_POSITION.get(oldPhysicalKey) == camera) {
			CAMERAS_BY_PHYSICAL_POSITION.remove(oldPhysicalKey);
		}
		camera.setPhysicalPosition(destination.immutable());
		CAMERAS_BY_PHYSICAL_POSITION.put(new CameraKey(level.dimension(), camera.physicalPosition()), camera);
		markDirty();
		updateCameraTransform(level, camera, CameraBlock.captureBaseOrigin(destination));
		CameraBlock.finishPistonCameraDisplay(level, source, destination, motion.displayUuid(), camera.yaw(), camera.pitch());
		MonitorScreenSystem.onCameraNetworkChanged(level, camera.identityPosition());
		return true;
	}

	/**
	 * Completes a move from the block-placement path.  Sticky retraction reaches
	 * its final {@code CameraBlock#onPlace} before the moving-piston ticker has
	 * performed its final callback, so this is the authoritative place to retag
	 * the model at the destination instead of leaving it with the old position
	 * tag until the next player interaction.
	 */
	public static boolean finishPistonMoveAt(ServerLevel level, BlockPos destination) {
		if (level == null || destination == null || !level.getBlockState(destination).is(ModBlocks.CAMERA)) {
			return false;
		}
		PistonCameraMotion completedMotion = null;
		for (PistonCameraMotion motion : ACTIVE_MOTIONS_BY_SOURCE.values()) {
			if (motion != null
					&& motion.camera().dimension().equals(level.dimension())
					&& motion.destination().equals(destination)) {
				completedMotion = motion;
				break;
			}
		}
		if (completedMotion == null) {
			return false;
		}
		return finishPistonMove(level, completedMotion.source(), destination);
	}

	/** Cleans up a retained identity when vanilla cannot place the moved block at
	 * its target (for example because a survival check turned it into air). */
	public static void cancelPistonMove(ServerLevel level, BlockPos source) {
		if (level == null || source == null) {
			return;
		}
		PistonCameraMotion motion = ACTIVE_MOTIONS_BY_SOURCE.remove(new CameraKey(level.dimension(), source));
		if (motion == null) {
			return;
		}
		MobileCamera camera = motion.camera();
		CameraKey physicalKey = new CameraKey(level.dimension(), camera.physicalPosition());
		if (CAMERAS_BY_PHYSICAL_POSITION.get(physicalKey) == camera) {
			CAMERAS_BY_PHYSICAL_POSITION.remove(physicalKey);
		}
		CAMERAS_BY_IDENTITY.remove(new CameraKey(level.dimension(), camera.identityPosition()));
		if (camera.anchor() != null) {
			camera.anchor().discard();
		}
		CameraBlock.discardPistonCameraDisplay(level, motion.source());
		CameraOrientationStore.remove(level, camera.identityPosition());
		PlacedDeviceNameStore.removeCameraName(level, camera.identityPosition());
		BluetoothLinkSystem.removeBlockEndpoint(level, BluetoothLinkSystem.EndpointType.CAMERA, camera.identityPosition());
		markDirty();
		MonitorScreenSystem.onCameraNetworkChanged(level, camera.identityPosition());
	}

	public static boolean isPistonCameraMovePending(ServerLevel level, BlockPos source) {
		return level != null && source != null
				&& ACTIVE_MOTIONS_BY_SOURCE.containsKey(new CameraKey(level.dimension(), source));
	}

	public static boolean isPistonCameraMoveDestinationPending(ServerLevel level, BlockPos destination) {
		if (level == null || destination == null) {
			return false;
		}
		for (PistonCameraMotion motion : ACTIVE_MOTIONS_BY_SOURCE.values()) {
			if (motion != null
					&& motion.camera().dimension().equals(level.dimension())
					&& motion.destination().equals(destination)) {
				return true;
			}
		}
		return false;
	}

	/** Resolves a physical camera position back to its stable Bluetooth identity. */
	public static BlockPos logicalCameraPosition(ServerLevel level, BlockPos physicalPosition) {
		if (level == null || physicalPosition == null) {
			return physicalPosition;
		}
		ensureLoaded(level.getServer());
		MobileCamera camera = CAMERAS_BY_PHYSICAL_POSITION.get(new CameraKey(level.dimension(), physicalPosition));
		if (camera != null) {
			return camera.identityPosition();
		}
		for (PistonCameraMotion motion : ACTIVE_MOTIONS_BY_SOURCE.values()) {
			if (motion != null
					&& motion.camera().dimension().equals(level.dimension())
					&& motion.destination().equals(physicalPosition)) {
				return motion.camera().identityPosition();
			}
		}
		return physicalPosition;
	}

	/** Resolves a stable Bluetooth identity back to its current physical block. */
	public static BlockPos physicalCameraPosition(ServerLevel level, BlockPos identityPosition) {
		if (level == null || identityPosition == null) {
			return identityPosition;
		}
		ensureLoaded(level.getServer());
		MobileCamera camera = CAMERAS_BY_IDENTITY.get(new CameraKey(level.dimension(), identityPosition));
		return camera != null ? camera.physicalPosition() : identityPosition;
	}

	/** Resolves the moving feed by its logical Bluetooth source coordinate. */
	public static MobileCameraFeed mobileCameraFeed(ServerLevel level, BlockPos identityPosition) {
		if (level == null || identityPosition == null) {
			return null;
		}
		ensureLoaded(level.getServer());
		MobileCamera camera = CAMERAS_BY_IDENTITY.get(new CameraKey(level.dimension(), identityPosition));
		if (camera == null || !isCameraPresentOrMoving(level, camera) || !ensureCameraAnchor(level, camera)) {
			return null;
		}
		Set<UUID> hiddenEntityUuids = new LinkedHashSet<>(CameraBlock.getCameraDisplayEntityUuids(level, camera.physicalPosition()));
		hiddenEntityUuids.add(camera.anchorUuid());
		Entity anchor = camera.anchor();
		return new MobileCameraFeed(
				anchor.getUUID(),
				camera.physicalPosition(),
				anchor.getX(), anchor.getY(), anchor.getZ(),
				anchor.getYRot(), anchor.getXRot(),
				Set.copyOf(hiddenEntityUuids)
		);
	}

	/** Removes an identity after its camera block was actually destroyed rather than moved. */
	public static BlockPos removeCameraIdentity(ServerLevel level, BlockPos physicalPosition) {
		if (level == null || physicalPosition == null) {
			return physicalPosition;
		}
		ensureLoaded(level.getServer());
		CameraKey physicalKey = new CameraKey(level.dimension(), physicalPosition);
		MobileCamera camera = CAMERAS_BY_PHYSICAL_POSITION.remove(physicalKey);
		if (camera == null) {
			return physicalPosition;
		}
		CAMERAS_BY_IDENTITY.remove(new CameraKey(level.dimension(), camera.identityPosition()));
		if (camera.anchor() != null) {
			camera.anchor().discard();
		}
		ACTIVE_MOTIONS_BY_SOURCE.entrySet().removeIf(entry -> entry.getValue() != null && entry.getValue().camera() == camera);
		markDirty();
		return camera.identityPosition();
	}

	/** Updates an already mobile camera anchor after the player turns its physical block. */
	public static void updateCameraOrientation(ServerLevel level, BlockPos physicalPosition, float yaw, float pitch) {
		if (level == null || physicalPosition == null) {
			return;
		}
		MobileCamera camera = CAMERAS_BY_PHYSICAL_POSITION.get(new CameraKey(level.dimension(), physicalPosition));
		if (camera == null) {
			return;
		}
		camera.setOrientation(yaw, pitch);
		updateCameraTransform(level, camera, CameraBlock.captureBaseOrigin(physicalPosition));
		markDirty();
	}

	private static MobileCamera resolveOrCreateCamera(ServerLevel level, BlockPos physicalPosition) {
		MobileCamera existing = CAMERAS_BY_PHYSICAL_POSITION.get(new CameraKey(level.dimension(), physicalPosition));
		if (existing != null) {
			return ensureCameraAnchor(level, existing) ? existing : null;
		}
		BlockPos identityPosition = physicalPosition.immutable();
		CameraOrientationStore.CameraPose pose = CameraOrientationStore.get(level, identityPosition);
		float yaw = pose != null ? pose.yaw() : level.getBlockState(physicalPosition)
				.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot();
		float pitch = pose != null ? pose.pitch() : 0.0F;
		MobileCamera camera = new MobileCamera(level.dimension(), identityPosition, physicalPosition.immutable(), yaw, pitch);
		CAMERAS_BY_IDENTITY.put(new CameraKey(level.dimension(), identityPosition), camera);
		CAMERAS_BY_PHYSICAL_POSITION.put(new CameraKey(level.dimension(), physicalPosition), camera);
		markDirty();
		return ensureCameraAnchor(level, camera) ? camera : null;
	}

	private static boolean ensureCameraAnchor(ServerLevel level, MobileCamera camera) {
		if (level == null || camera == null) {
			return false;
		}
		if (!ensureCameraAnchorForTransform(level, camera)) {
			return false;
		}
		if (level.hasChunkAt(camera.physicalPosition()) && level.getBlockState(camera.physicalPosition()).is(ModBlocks.CAMERA)) {
			updateCameraTransform(level, camera, CameraBlock.captureBaseOrigin(camera.physicalPosition()));
		}
		return true;
	}

	/** A persisted identity is not a virtual camera: its physical block must still
	 * exist, except for the brief interval in which vanilla has replaced it with
	 * a moving-piston block. */
	private static boolean isCameraPresentOrMoving(ServerLevel level, MobileCamera camera) {
		if (level == null || camera == null) {
			return false;
		}
		for (PistonCameraMotion motion : ACTIVE_MOTIONS_BY_SOURCE.values()) {
			if (motion != null && motion.camera() == camera) {
				return true;
			}
		}
		return level.hasChunkAt(camera.physicalPosition())
				&& level.getBlockState(camera.physicalPosition()).is(ModBlocks.CAMERA);
	}

	private static void updateCameraTransform(ServerLevel level, MobileCamera camera, Vec3 position) {
		if (level == null || camera == null || position == null || !ensureCameraAnchorForTransform(level, camera)) {
			return;
		}
		Entity anchor = camera.anchor();
		// Follow-stream pose packets are absolute camera coordinates. Keep this
		// anchor at the physical lens, not inside the polymer PLAYER_HEAD block
		// that supplies the placed camera's collision box.
		Vec3 lensPosition = CameraBlock.captureOrigin(position, camera.yaw(), camera.pitch());
		anchor.setPos(lensPosition.x, lensPosition.y, lensPosition.z);
		anchor.setYRot(camera.yaw());
		anchor.setXRot(camera.pitch());
		anchor.yRotO = camera.yaw();
		anchor.xRotO = camera.pitch();
	}

	private static boolean ensureCameraAnchorForTransform(ServerLevel level, MobileCamera camera) {
		Entity anchor = camera.anchor();
		if (anchor != null && !anchor.isRemoved() && anchor.level() == level && anchor.getTags().contains(CAMERA_ANCHOR_TAG)) {
			return true;
		}
		Interaction created = new Interaction(EntityType.INTERACTION, level);
		created.addTag(CAMERA_ANCHOR_TAG);
		created.setNoGravity(true);
		created.setInvulnerable(true);
		created.setSilent(true);
		created.setResponse(false);
		created.setWidth(0.01F);
		created.setHeight(0.01F);
		level.addFreshEntity(created);
		camera.setAnchor(created);
		return true;
	}

	/** Identifies an anchor whose server position is already the exact lens pose,
	 * rather than an entity whose eye position should be used. */
	public static boolean isMobileCameraAnchor(Entity entity) {
		return entity != null && entity.getTags().contains(CAMERA_ANCHOR_TAG);
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ensureLoaded(server);
		// This is intentionally a second completion path.  It covers a retained
		// moving-piston block entity being discarded in the same tick as the final
		// block update (notably sticky-piston retraction on a busy server).
		for (PistonCameraMotion motion : List.copyOf(ACTIVE_MOTIONS_BY_SOURCE.values())) {
			if (motion == null) {
				continue;
			}
			ServerLevel level = server.getLevel(motion.camera().dimension());
			if (level != null && level.hasChunkAt(motion.destination()) && level.getBlockState(motion.destination()).is(ModBlocks.CAMERA)) {
				finishPistonMove(level, motion.source(), motion.destination());
			}
		}
		for (MobileCamera camera : CAMERAS_BY_IDENTITY.values()) {
			ServerLevel level = server.getLevel(camera.dimension());
			if (level == null || !level.hasChunkAt(camera.physicalPosition()) || !level.getBlockState(camera.physicalPosition()).is(ModBlocks.CAMERA)) {
				continue;
			}
			ensureCameraAnchor(level, camera);
		}
		if (dirty && server.overworld().getGameTime() >= nextPersistenceAttemptTick) {
			save(server);
			nextPersistenceAttemptTick = server.overworld().getGameTime() + PERSISTENCE_DEBOUNCE_TICKS;
		}
	}

	private static void clearRuntimeState() {
		for (MobileCamera camera : CAMERAS_BY_IDENTITY.values()) {
			if (camera.anchor() != null) {
				camera.anchor().discard();
			}
		}
		CAMERAS_BY_IDENTITY.clear();
		CAMERAS_BY_PHYSICAL_POSITION.clear();
		ACTIVE_MOTIONS_BY_SOURCE.clear();
		loaded = false;
		dirty = false;
		nextPersistenceAttemptTick = 0L;
	}

	private static void markDirty() {
		dirty = true;
	}

	private static void ensureLoaded(MinecraftServer server) {
		if (!loaded) {
			load(server);
		}
	}

	private static void load(MinecraftServer server) {
		CAMERAS_BY_IDENTITY.clear();
		CAMERAS_BY_PHYSICAL_POSITION.clear();
		ACTIVE_MOTIONS_BY_SOURCE.clear();
		loaded = true;
		dirty = false;
		nextPersistenceAttemptTick = 0L;
		if (server == null || !Files.exists(statePath(server))) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(statePath(server))) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!(parsed instanceof JsonArray entries)) {
				return;
			}
			for (JsonElement element : entries) {
				if (!(element instanceof JsonObject object)) {
					continue;
				}
				ResourceKey<Level> dimension = resolveDimension(object.has("dimension") ? object.get("dimension").getAsString() : "");
				BlockPos identity = readPosition(object, "identity_");
				BlockPos physical = readPosition(object, "physical_");
				if (dimension == null || identity == null || physical == null) {
					continue;
				}
				MobileCamera camera = new MobileCamera(
						dimension,
						identity,
						physical,
						object.has("yaw") ? object.get("yaw").getAsFloat() : 0.0F,
						object.has("pitch") ? object.get("pitch").getAsFloat() : 0.0F
				);
				CAMERAS_BY_IDENTITY.put(new CameraKey(dimension, identity), camera);
				CAMERAS_BY_PHYSICAL_POSITION.put(new CameraKey(dimension, physical), camera);
			}
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to load mobile camera identity state", exception);
		}
	}

	private static void save(MinecraftServer server) {
		if (!loaded || !dirty || server == null) {
			return;
		}
		Path path = statePath(server);
		Path temporaryPath = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			JsonArray entries = new JsonArray();
			for (MobileCamera camera : CAMERAS_BY_IDENTITY.values()) {
				if (camera == null) {
					continue;
				}
				JsonObject object = new JsonObject();
				object.addProperty("dimension", camera.dimension().identifier().toString());
				writePosition(object, "identity_", camera.identityPosition());
				writePosition(object, "physical_", camera.physicalPosition());
				object.addProperty("yaw", camera.yaw());
				object.addProperty("pitch", camera.pitch());
				entries.add(object);
			}
			try (Writer writer = Files.newBufferedWriter(temporaryPath)) {
				GSON.toJson(entries, writer);
			}
			try {
				Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(temporaryPath, path, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException exception) {
			Lg2.LOGGER.warn("Failed to save mobile camera identity state", exception);
		}
	}

	private static Path statePath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
	}

	private static ResourceKey<Level> resolveDimension(String rawId) {
		Identifier identifier = Identifier.tryParse(rawId);
		return identifier == null ? null : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, identifier);
	}

	private static BlockPos readPosition(JsonObject object, String prefix) {
		if (object == null || !object.has(prefix + "x") || !object.has(prefix + "y") || !object.has(prefix + "z")) {
			return null;
		}
		return new BlockPos(object.get(prefix + "x").getAsInt(), object.get(prefix + "y").getAsInt(), object.get(prefix + "z").getAsInt());
	}

	private static void writePosition(JsonObject object, String prefix, BlockPos position) {
		object.addProperty(prefix + "x", position.getX());
		object.addProperty(prefix + "y", position.getY());
		object.addProperty(prefix + "z", position.getZ());
	}

	public record MobileCameraFeed(
			UUID followEntityUuid,
			BlockPos physicalCameraPosition,
			double expectedX,
			double expectedY,
			double expectedZ,
			float yaw,
			float pitch,
			Set<UUID> hiddenEntityUuids
	) {
	}

	private record CameraKey(ResourceKey<Level> dimension, BlockPos position) {
		private CameraKey {
			position = position.immutable();
		}
	}

	private record PistonCameraMotion(MobileCamera camera, BlockPos source, BlockPos destination, UUID displayUuid) {
		private PistonCameraMotion {
			source = source.immutable();
			destination = destination.immutable();
		}
	}

	private static final class MobileCamera {
		private final ResourceKey<Level> dimension;
		private final BlockPos identityPosition;
		private BlockPos physicalPosition;
		private float yaw;
		private float pitch;
		private Entity anchor;

		private MobileCamera(ResourceKey<Level> dimension, BlockPos identityPosition, BlockPos physicalPosition, float yaw, float pitch) {
			this.dimension = dimension;
			this.identityPosition = identityPosition.immutable();
			this.physicalPosition = physicalPosition.immutable();
			this.yaw = yaw;
			this.pitch = pitch;
		}

		private ResourceKey<Level> dimension() { return this.dimension; }
		private BlockPos identityPosition() { return this.identityPosition; }
		private BlockPos physicalPosition() { return this.physicalPosition; }
		private void setPhysicalPosition(BlockPos position) { this.physicalPosition = position.immutable(); }
		private float yaw() { return this.yaw; }
		private float pitch() { return this.pitch; }
		private void setOrientation(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
		private Entity anchor() { return this.anchor; }
		private UUID anchorUuid() { return this.anchor != null ? this.anchor.getUUID() : null; }
		private void setAnchor(Entity anchor) { this.anchor = anchor; }
	}
}
