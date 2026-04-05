package com.lostglade.client;

import com.lostglade.Lg2;
import com.lostglade.mixin.client.ClientPacketListenerShadowAccessor;
import com.lostglade.network.RendererBotPayloads;
import com.lostglade.network.RendererBotShadowPacketCodec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public final class RendererBotShadowWorldManager {
	private static final Object LOCK = new Object();
	private static final Map<String, ShadowLevelSession> SHADOW_LEVELS = new HashMap<>();

	private RendererBotShadowWorldManager() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowLevelInitS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyLevelInit(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowLevelStateS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> applyLevelState(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
				RendererBotPayloads.RendererBotShadowLevelDestroyS2CPayload.TYPE,
				(payload, context) -> context.client().execute(() -> destroyShadowLevel(payload.dimensionId()))
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

	public static ClientLevel resolveRenderLevel(Minecraft client, String dimensionId) {
		if (client == null || dimensionId == null || dimensionId.isBlank()) {
			return null;
		}
		if (client.level != null && dimensionId.equals(client.level.dimension().identifier().toString())) {
			return client.level;
		}
		synchronized (LOCK) {
			ShadowLevelSession session = SHADOW_LEVELS.get(dimensionId);
			return session == null ? null : session.level();
		}
	}

	public static boolean isManagedLevel(Minecraft client, ClientLevel level) {
		if (level == null) {
			return false;
		}
		if (client != null && client.level == level) {
			return true;
		}
		synchronized (LOCK) {
			for (ShadowLevelSession session : SHADOW_LEVELS.values()) {
				if (session != null && session.level() == level) {
					return true;
				}
			}
		}
		return false;
	}

	public static void clear() {
		synchronized (LOCK) {
			for (ShadowLevelSession session : SHADOW_LEVELS.values()) {
				closeSession(session);
			}
			SHADOW_LEVELS.clear();
		}
	}

	private static void applyLevelInit(Minecraft client, RendererBotPayloads.RendererBotShadowLevelInitS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null || payload.dimensionId() == null || payload.dimensionId().isBlank()) {
			return;
		}
		if (client.level != null && payload.dimensionId().equals(client.level.dimension().identifier().toString())) {
			return;
		}
		synchronized (LOCK) {
			ShadowLevelSession existing = SHADOW_LEVELS.get(payload.dimensionId());
			if (existing != null) {
				closeSession(existing);
				SHADOW_LEVELS.remove(payload.dimensionId());
			}
			ShadowLevelSession created = createShadowSession(client, payload);
			if (created == null) {
				return;
			}
			SHADOW_LEVELS.put(payload.dimensionId(), created);
			applyState(created.level(), payload.gameTime(), payload.dayTime(), payload.tickDayTime(), payload.raining());
			created.level().setServerSimulationDistance(Math.max(2, payload.simulationDistance()));
		}
	}

	private static void applyLevelState(Minecraft client, RendererBotPayloads.RendererBotShadowLevelStateS2CPayload payload) {
		if (client == null || payload == null) {
			return;
		}
		synchronized (LOCK) {
			ShadowLevelSession session = SHADOW_LEVELS.get(payload.dimensionId());
			if (session == null) {
				return;
			}
			applyState(session.level(), payload.gameTime(), payload.dayTime(), payload.tickDayTime(), payload.raining());
		}
	}

	private static void applyChunkData(Minecraft client, RendererBotPayloads.RendererBotShadowChunkDataS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_LEVELS.get(payload.dimensionId());
		}
		if (session == null) {
			return;
		}
		ClientboundLevelChunkWithLightPacket packet = RendererBotShadowPacketCodec.decodeChunkPacket(client.getConnection().registryAccess(), payload.packetBytes());
		runWithShadowLevel(client.getConnection(), session.level(), () -> {
			client.getConnection().handleLevelChunkWithLight(packet);
			session.level().pollLightUpdates();
		});
		RendererBotOffscreenWorldRenderer.onChunkReadyToRender(session.level(), new ChunkPos(packet.getX(), packet.getZ()));
	}

	private static void applyForgetChunk(Minecraft client, RendererBotPayloads.RendererBotShadowForgetChunkS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_LEVELS.get(payload.dimensionId());
		}
		if (session == null) {
			return;
		}
		ClientboundForgetLevelChunkPacket packet = new ClientboundForgetLevelChunkPacket(new ChunkPos(payload.chunkX(), payload.chunkZ()));
		runWithShadowLevel(client.getConnection(), session.level(), () -> {
			client.getConnection().handleForgetLevelChunk(packet);
			session.level().pollLightUpdates();
		});
	}

	private static void applyEntityPackets(Minecraft client, RendererBotPayloads.RendererBotShadowEntityPacketsS2CPayload payload) {
		if (client == null || client.getConnection() == null || payload == null || payload.packets() == null || payload.packets().isEmpty()) {
			return;
		}
		ShadowLevelSession session;
		synchronized (LOCK) {
			session = SHADOW_LEVELS.get(payload.dimensionId());
		}
		if (session == null) {
			return;
		}
		runWithShadowLevel(client.getConnection(), session.level(), () -> {
			for (RendererBotPayloads.ShadowPacketData packetData : payload.packets()) {
				Packet<ClientGamePacketListener> packet = RendererBotShadowPacketCodec.decodePacket(client.getConnection().registryAccess(), packetData);
				if (packet != null) {
					packet.handle(client.getConnection());
				}
			}
		});
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
		RenderBuffers renderBuffers = client.renderBuffers();
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
		applyState(level, payload.gameTime(), payload.dayTime(), payload.tickDayTime(), payload.raining());
		Lg2.LOGGER.info("Renderer bot created shadow level {} ({})", payload.dimensionId(), payload.dimensionTypeId());
		return new ShadowLevelSession(payload.dimensionId(), payload.dimensionTypeId(), level, levelRenderer, featureRenderDispatcher);
	}

	private static void destroyShadowLevel(String dimensionId) {
		if (dimensionId == null || dimensionId.isBlank()) {
			return;
		}
		synchronized (LOCK) {
			ShadowLevelSession removed = SHADOW_LEVELS.remove(dimensionId);
			closeSession(removed);
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
	}

	private static void runWithShadowLevel(ClientPacketListener connection, ClientLevel shadowLevel, Runnable action) {
		if (connection == null || shadowLevel == null || action == null) {
			return;
		}
		ClientPacketListenerShadowAccessor accessor = (ClientPacketListenerShadowAccessor) connection;
		ClientLevel previousLevel = accessor.lg2$getLevel();
		ClientLevel.ClientLevelData previousLevelData = accessor.lg2$getLevelData();
		try {
			accessor.lg2$setLevel(shadowLevel);
			accessor.lg2$setLevelData(shadowLevel.getLevelData());
			action.run();
		} finally {
			accessor.lg2$setLevel(previousLevel);
			accessor.lg2$setLevelData(previousLevelData);
		}
	}

	private static void applyState(ClientLevel level, long gameTime, long dayTime, boolean tickDayTime, boolean raining) {
		if (level == null) {
			return;
		}
		level.setTimeFromServer(gameTime, dayTime, tickDayTime);
		level.getLevelData().setRaining(raining);
	}

	private static Difficulty resolveDifficulty(int ordinal) {
		Difficulty[] values = Difficulty.values();
		if (ordinal < 0 || ordinal >= values.length) {
			return Difficulty.NORMAL;
		}
		return values[ordinal];
	}

	private record ShadowLevelSession(
			String dimensionId,
			String dimensionTypeId,
			ClientLevel level,
			LevelRenderer levelRenderer,
			FeatureRenderDispatcher featureRenderDispatcher
	) {
	}
}
