package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.mixin.client.ClientLevelMapDataAccessor;
import com.lostglade.mixin.client.ClientPacketListenerShadowAccessor;
import com.lostglade.mixin.client.MinecraftOffscreenWorldAccessor;
import com.lostglade.mixin.client.CloudRendererReloadInvoker;
import com.lostglade.mixin.client.GameRendererRenderLevelInvoker;
import com.lostglade.network.RendererBotPayloads;
import com.lostglade.network.RendererBotShadowPacketCodec;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RendererBotShadowWorldManager {
	private static final Object LOCK = new Object();
	private static final long ACTIVE_SESSION_TICK_WINDOW_MS = 2_500L;
	private static final Map<UUID, ShadowLevelSession> SHADOW_SESSIONS = new HashMap<>();
	private static final Map<UUID, Long> LAST_RENDER_ACTIVITY_AT = new HashMap<>();

	private RendererBotShadowWorldManager() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientTickEvents.END_CLIENT_TICK.register(RendererBotShadowWorldManager::tickShadowWorlds);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowLevelInitS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyLevelInit(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowLevelStateS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyLevelState(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowViewS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyView(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowLevelDestroyS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> destroyShadowSession(payload.sessionId()))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowChunkDataS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyChunkData(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowForgetChunkS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyForgetChunk(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowEntityPacketsS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyEntityPackets(context.client(), payload))
		);
	}

	public static void onMapDataUpdated(ClientPacketListener connection, net.minecraft.network.protocol.game.ClientboundMapItemDataPacket packet) {
		if (connection == null || packet == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		ClientLevel sourceLevel = client.level;
		if (sourceLevel == null) {
			return;
		}
		MapItemSavedData mapData = sourceLevel.getMapData(packet.mapId());
		if (mapData == null) {
			return;
		}
		synchronized (LOCK) {
			for (ShadowLevelSession session : SHADOW_SESSIONS.values()) {
				if (session == null || session.level() == null) {
					continue;
				}
				session.level().overrideMapData(packet.mapId(), mapData);
			}
		}
	}

	public static ShadowRenderSession resolveRenderSession(UUID sessionId) {
		if (sessionId == null) {
			return null;
		}
		synchronized (LOCK) {
			ShadowLevelSession session = SHADOW_SESSIONS.get(sessionId);
			if (session == null) {
				return null;
			}
			LAST_RENDER_ACTIVITY_AT.put(sessionId, System.currentTimeMillis());
			return new ShadowRenderSession(
					session.sessionId(),
					session.dimensionId(),
					session.level(),
					session.levelRenderer(),
					session.featureRenderDispatcher(),
					session.particleEngine()
			);
		}
	}

	public static void updateCameraContext(UUID sessionId, Camera camera) {
		if (sessionId == null || camera == null) {
			return;
		}
		synchronized (LOCK) {
			ShadowLevelSession session = SHADOW_SESSIONS.get(sessionId);
			if (session == null) {
				return;
			}
			session.setLastCamera(camera);
			LAST_RENDER_ACTIVITY_AT.put(sessionId, System.currentTimeMillis());
		}
	}

	public static boolean isManagedLevel(ClientLevel level) {
		if (level == null) {
			return false;
		}
		synchronized (LOCK) {
			for (ShadowLevelSession session : SHADOW_SESSIONS.values()) {
				if (session != null && session.level() == level) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isManagedRenderer(LevelRenderer renderer, ClientLevel level) {
		if (renderer == null || level == null) {
			return false;
		}
		synchronized (LOCK) {
			for (ShadowLevelSession session : SHADOW_SESSIONS.values()) {
				if (session != null && session.level() == level && session.levelRenderer() == renderer) {
					return true;
				}
			}
		}
		return false;
	}

	public static void clear() {
		synchronized (LOCK) {
			for (ShadowLevelSession session : SHADOW_SESSIONS.values()) {
				closeSession(session);
			}
			SHADOW_SESSIONS.clear();
			LAST_RENDER_ACTIVITY_AT.clear();
		}
	}

	private static void tickShadowWorlds(Minecraft client) {
		if (client == null || client.getConnection() == null) {
			return;
		}
		List<ShadowLevelSession> sessions;
		synchronized (LOCK) {
			if (SHADOW_SESSIONS.isEmpty()) {
				return;
			}
			sessions = new ArrayList<>(SHADOW_SESSIONS.values());
		}
		for (ShadowLevelSession session : sessions) {
			if (session == null || session.level() == null) {
				continue;
			}
			if (!shouldTickSession(session.sessionId())) {
				continue;
			}
			tickShadowSession(client, session);
		}
	}

	private static void tickShadowSession(Minecraft client, ShadowLevelSession session) {
		if (client == null || client.getConnection() == null || session == null || session.level() == null) {
			return;
		}
		runWithShadowSession(client.getConnection(), session, () -> {
			Camera camera = session.lastCamera();
			if (camera != null) {
				camera.tick();
			}
			session.level().tickEntities();
			session.level().tickBlockEntities();
			session.level().tick(() -> true);
			if (camera != null) {
				BlockPos blockPos = camera.blockPosition();
				session.level().animateTick(blockPos.getX(), blockPos.getY(), blockPos.getZ());
			}
			session.particleEngine().tick();
		});
	}

	private static void applyLevelInit(Minecraft client, RendererBotPayloads.RendererBotShadowLevelInitS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null || payload.sessionId() == null) {
			return;
		}
		synchronized (LOCK) {
			ShadowLevelSession existing = SHADOW_SESSIONS.remove(payload.sessionId());
			LAST_RENDER_ACTIVITY_AT.remove(payload.sessionId());
			closeSession(existing);
			ShadowLevelSession created = createShadowSession(client, payload);
			if (created == null) {
				return;
			}
			SHADOW_SESSIONS.put(payload.sessionId(), created);
			LAST_RENDER_ACTIVITY_AT.put(payload.sessionId(), System.currentTimeMillis());
			applyState(
					created.level(),
					payload.gameTime(),
					payload.dayTime(),
					payload.tickDayTime(),
					payload.raining(),
					payload.rainLevel(),
					payload.thunderLevel()
			);
			applyViewState(client.getConnection(), created.level(), payload.viewDistance(), 0, 0);
			Lg2.LOGGER.info("Renderer bot created shadow session {} for {}", payload.sessionId(), payload.dimensionId());
		}
	}

	private static void applyLevelState(Minecraft client, RendererBotPayloads.RendererBotShadowLevelStateS2CPayload payload) {
		if (client == null || payload == null || payload.sessionId() == null) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_SESSIONS.get(payload.sessionId());
		}
		if (session == null) {
			return;
		}
		applyState(
				session.level(),
				payload.gameTime(),
				payload.dayTime(),
				payload.tickDayTime(),
				payload.raining(),
				payload.rainLevel(),
				payload.thunderLevel()
		);
	}

	private static void applyView(Minecraft client, RendererBotPayloads.RendererBotShadowViewS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null || payload.sessionId() == null) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_SESSIONS.get(payload.sessionId());
		}
		if (session == null) {
			return;
		}
		applyViewState(client.getConnection(), session.level(), payload.viewDistance(), payload.centerChunkX(), payload.centerChunkZ());
		session.levelRenderer().needsUpdate();
	}

	private static void applyChunkData(Minecraft client, RendererBotPayloads.RendererBotShadowChunkDataS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null || payload.sessionId() == null) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_SESSIONS.get(payload.sessionId());
		}
		if (session == null) {
			return;
		}
		ClientboundLevelChunkWithLightPacket packet = RendererBotShadowPacketCodec.decodeChunkPacket(client.getConnection().registryAccess(), payload.packetBytes());
		runWithShadowSession(client.getConnection(), session, () -> {
			client.getConnection().handleLevelChunkWithLight(packet);
			session.level().pollLightUpdates();
		});
		session.levelRenderer().onChunkReadyToRender(new ChunkPos(packet.getX(), packet.getZ()));
	}

	private static void applyForgetChunk(Minecraft client, RendererBotPayloads.RendererBotShadowForgetChunkS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null || payload.sessionId() == null) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_SESSIONS.get(payload.sessionId());
		}
		if (session == null) {
			return;
		}
		ClientboundForgetLevelChunkPacket packet = new ClientboundForgetLevelChunkPacket(new ChunkPos(payload.chunkX(), payload.chunkZ()));
		runWithShadowSession(client.getConnection(), session, () -> {
			client.getConnection().handleForgetLevelChunk(packet);
			session.level().pollLightUpdates();
		});
	}

	private static void applyEntityPackets(Minecraft client, RendererBotPayloads.RendererBotShadowEntityPacketsS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null || payload.sessionId() == null || payload.packets() == null || payload.packets().isEmpty()) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_SESSIONS.get(payload.sessionId());
		}
		if (session == null) {
			return;
		}
		runWithShadowSession(client.getConnection(), session, () -> {
			for (RendererBotPayloads.ShadowPacketData packetData : payload.packets()) {
				Packet<ClientGamePacketListener> packet = RendererBotShadowPacketCodec.decodePacket(client.getConnection().registryAccess(), packetData);
				if (packet != null) {
					packet.handle(client.getConnection());
				}
			}
		});
	}

	private static void destroyShadowSession(UUID sessionId) {
		if (sessionId == null) {
			return;
		}
		synchronized (LOCK) {
			ShadowLevelSession removed = SHADOW_SESSIONS.remove(sessionId);
			LAST_RENDER_ACTIVITY_AT.remove(sessionId);
			closeSession(removed);
		}
		RendererBotOffscreenWorldRenderer.releaseSession(sessionId);
	}

	private static ShadowLevelSession createShadowSession(Minecraft client, RendererBotPayloads.RendererBotShadowLevelInitS2CPayload payload) {
		ClientPacketListener connection = client.getConnection();
		if (connection == null) {
			return null;
		}
		ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(payload.dimensionId()));
		ResourceKey<net.minecraft.world.level.dimension.DimensionType> dimensionTypeKey =
				ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(payload.dimensionTypeId()));
		Holder<net.minecraft.world.level.dimension.DimensionType> dimensionType = connection.registryAccess()
				.lookupOrThrow(Registries.DIMENSION_TYPE)
				.getOrThrow(dimensionTypeKey);
		ClientLevel.ClientLevelData levelData = new ClientLevel.ClientLevelData(
				resolveDifficulty(payload.difficultyOrdinal()),
				payload.hardcore(),
				payload.flat()
		);
		RenderBuffers renderBuffers = new RenderBuffers(Math.max(1, Runtime.getRuntime().availableProcessors()));
		FeatureRenderDispatcher featureRenderDispatcher = new FeatureRenderDispatcher(
				new SubmitNodeStorage(),
				client.getBlockRenderer(),
				renderBuffers.bufferSource(),
				client.getAtlasManager(),
				renderBuffers.outlineBufferSource(),
				renderBuffers.crumblingBufferSource(),
				client.font
		);
		LevelRenderer levelRenderer = new LevelRenderer(
				client,
				client.getEntityRenderDispatcher(),
				client.getBlockEntityRenderDispatcher(),
				renderBuffers,
				new LevelRenderState(),
				featureRenderDispatcher
		);
		levelRenderer.onResourceManagerReload(client.getResourceManager());
		initializeShadowCloudRenderer(levelRenderer, client.getResourceManager());
		ClientLevel level = new ClientLevel(
				connection,
				levelData,
				dimensionKey,
				dimensionType,
				Math.max(2, payload.viewDistance()),
				Math.max(2, payload.simulationDistance()),
				levelRenderer,
				payload.debug(),
				payload.seed(),
				payload.seaLevel()
		);
		levelRenderer.setLevel(level);
		levelRenderer.resize(Math.max(1, client.getWindow().getWidth()), Math.max(1, client.getWindow().getHeight()));
		level.setServerSimulationDistance(Math.max(2, payload.simulationDistance()));
		ParticleEngine particleEngine = new ParticleEngine(level, ((MinecraftOffscreenWorldAccessor) client).lg2$getParticleResources());
		copyKnownMapData(client.level, level);
		return new ShadowLevelSession(
				payload.sessionId(),
				payload.dimensionId(),
				payload.dimensionTypeId(),
				level,
				levelRenderer,
				featureRenderDispatcher,
				particleEngine
		);
	}

	private static void initializeShadowCloudRenderer(LevelRenderer levelRenderer, ResourceManager resourceManager) {
		if (levelRenderer == null || resourceManager == null) {
			return;
		}
		CloudRenderer cloudRenderer = levelRenderer.getCloudRenderer();
		if (cloudRenderer == null) {
			return;
		}
		try {
			CloudRendererReloadInvoker invoker = (CloudRendererReloadInvoker) cloudRenderer;
			invoker.lg2$applyPreparedClouds(
					invoker.lg2$prepareCloudTexture(resourceManager, InactiveProfiler.INSTANCE),
					resourceManager,
					InactiveProfiler.INSTANCE
			);
		} catch (Throwable throwable) {
			Lg2.LOGGER.warn("Renderer bot failed to initialize shadow cloud renderer", throwable);
		}
	}

	private static void closeSession(ShadowLevelSession session) {
		if (session == null) {
			return;
		}
		try {
			session.levelRenderer().setLevel(null);
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Renderer bot failed to detach shadow level renderer cleanly", throwable);
		}
		try {
			session.levelRenderer().close();
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Renderer bot failed to close shadow level renderer cleanly", throwable);
		}
		try {
			session.featureRenderDispatcher().close();
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Renderer bot failed to close shadow feature dispatcher cleanly", throwable);
		}
		try {
			session.particleEngine().clearParticles();
		} catch (Throwable throwable) {
			Lg2.LOGGER.debug("Renderer bot failed to clear shadow particle manager cleanly", throwable);
		}
	}

	private static void runWithShadowSession(ClientPacketListener connection, ShadowLevelSession session, Runnable action) {
		if (connection == null || session == null || session.level() == null || action == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		ClientLevel shadowLevel = session.level();
		ClientPacketListenerShadowAccessor accessor = (ClientPacketListenerShadowAccessor) connection;
		ClientLevel previousLevel = accessor.lg2$getLevel();
		ClientLevel.ClientLevelData previousLevelData = accessor.lg2$getLevelData();
		MinecraftOffscreenWorldAccessor worldAccessor = client == null ? null : (MinecraftOffscreenWorldAccessor) client;
		ClientLevel previousClientLevel = worldAccessor == null ? null : worldAccessor.lg2$getLevel();
		LevelRenderer previousLevelRenderer = worldAccessor == null ? null : worldAccessor.lg2$getLevelRenderer();
		ParticleEngine previousParticleEngine = worldAccessor == null ? null : worldAccessor.lg2$getParticleEngine();
		net.minecraft.world.entity.Entity previousCameraEntity = client == null ? null : client.getCameraEntity();
		Camera previousMainCamera = client == null || client.gameRenderer == null ? null : client.gameRenderer.getMainCamera();
		try {
			accessor.lg2$setLevel(shadowLevel);
			accessor.lg2$setLevelData(shadowLevel.getLevelData());
			if (worldAccessor != null) {
				worldAccessor.lg2$setLevel(shadowLevel);
				worldAccessor.lg2$setLevelRenderer(session.levelRenderer());
				worldAccessor.lg2$setParticleEngine(session.particleEngine());
			}
			if (client != null && client.gameRenderer != null && session.lastCamera() != null) {
				client.setCameraEntity(session.lastCamera().entity());
				((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$setMainCamera(session.lastCamera());
			}
			action.run();
		} finally {
			accessor.lg2$setLevel(previousLevel);
			accessor.lg2$setLevelData(previousLevelData);
			if (worldAccessor != null) {
				worldAccessor.lg2$setLevel(previousClientLevel);
				worldAccessor.lg2$setLevelRenderer(previousLevelRenderer);
				worldAccessor.lg2$setParticleEngine(previousParticleEngine);
			}
			if (client != null && client.gameRenderer != null) {
				client.setCameraEntity(previousCameraEntity);
				((GameRendererRenderLevelInvoker) client.gameRenderer).lg2$setMainCamera(previousMainCamera);
			}
		}
	}

	private static void applyViewState(ClientPacketListener connection, ClientLevel level, int viewDistance, int centerChunkX, int centerChunkZ) {
		if (connection == null || level == null) {
			return;
		}
		ShadowLevelSession session = findSession(level);
		if (session == null) {
			return;
		}
		runWithShadowSession(connection, session, () -> {
			connection.handleSetChunkCacheRadius(new ClientboundSetChunkCacheRadiusPacket(Math.max(2, viewDistance)));
			connection.handleSetChunkCacheCenter(new ClientboundSetChunkCacheCenterPacket(centerChunkX, centerChunkZ));
		});
	}

	private static ShadowLevelSession findSession(ClientLevel level) {
		if (level == null) {
			return null;
		}
		synchronized (LOCK) {
			for (ShadowLevelSession session : SHADOW_SESSIONS.values()) {
				if (session != null && session.level() == level) {
					return session;
				}
			}
		}
		return null;
	}

	private static void applyState(
			ClientLevel level,
			long gameTime,
			long dayTime,
			boolean tickDayTime,
			boolean raining,
			float rainLevel,
			float thunderLevel
	) {
		if (level == null) {
			return;
		}
		level.setTimeFromServer(gameTime, dayTime, tickDayTime);
		level.getLevelData().setRaining(raining);
		level.setRainLevel(rainLevel);
		level.setThunderLevel(thunderLevel);
	}

	private static boolean shouldTickSession(UUID sessionId) {
		if (sessionId == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		synchronized (LOCK) {
			Long lastActivity = LAST_RENDER_ACTIVITY_AT.get(sessionId);
			return lastActivity != null && now - lastActivity <= ACTIVE_SESSION_TICK_WINDOW_MS;
		}
	}

	private static void copyKnownMapData(ClientLevel sourceLevel, ClientLevel targetLevel) {
		if (sourceLevel == null || targetLevel == null) {
			return;
		}
		Map<MapId, MapItemSavedData> sourceMapData = ((ClientLevelMapDataAccessor) sourceLevel).lg2$getMapData();
		if (sourceMapData == null || sourceMapData.isEmpty()) {
			return;
		}
		for (Map.Entry<MapId, MapItemSavedData> entry : sourceMapData.entrySet()) {
			MapId mapId = entry.getKey();
			MapItemSavedData mapData = entry.getValue();
			if (mapId == null || mapData == null) {
				continue;
			}
			targetLevel.overrideMapData(mapId, mapData);
		}
	}

	private static Difficulty resolveDifficulty(int ordinal) {
		Difficulty[] values = Difficulty.values();
		if (ordinal < 0 || ordinal >= values.length) {
			return Difficulty.NORMAL;
		}
		return values[ordinal];
	}

	public record ShadowRenderSession(
			UUID sessionId,
			String dimensionId,
			ClientLevel level,
			LevelRenderer levelRenderer,
			FeatureRenderDispatcher featureRenderDispatcher,
			ParticleEngine particleEngine
	) {
	}

	private static final class ShadowLevelSession {
		private final UUID sessionId;
		private final String dimensionId;
		private final String dimensionTypeId;
		private final ClientLevel level;
		private final LevelRenderer levelRenderer;
		private final FeatureRenderDispatcher featureRenderDispatcher;
		private final ParticleEngine particleEngine;
		private Camera lastCamera;

		private ShadowLevelSession(
				UUID sessionId,
				String dimensionId,
				String dimensionTypeId,
				ClientLevel level,
				LevelRenderer levelRenderer,
				FeatureRenderDispatcher featureRenderDispatcher,
				ParticleEngine particleEngine
		) {
			this.sessionId = sessionId;
			this.dimensionId = dimensionId;
			this.dimensionTypeId = dimensionTypeId;
			this.level = level;
			this.levelRenderer = levelRenderer;
			this.featureRenderDispatcher = featureRenderDispatcher;
			this.particleEngine = particleEngine;
		}

		private UUID sessionId() {
			return this.sessionId;
		}

		private String dimensionId() {
			return this.dimensionId;
		}

		@SuppressWarnings("unused")
		private String dimensionTypeId() {
			return this.dimensionTypeId;
		}

		private ClientLevel level() {
			return this.level;
		}

		private LevelRenderer levelRenderer() {
			return this.levelRenderer;
		}

		private FeatureRenderDispatcher featureRenderDispatcher() {
			return this.featureRenderDispatcher;
		}

		private ParticleEngine particleEngine() {
			return this.particleEngine;
		}

		private Camera lastCamera() {
			return this.lastCamera;
		}

		private void setLastCamera(Camera lastCamera) {
			this.lastCamera = lastCamera;
		}
	}
}
