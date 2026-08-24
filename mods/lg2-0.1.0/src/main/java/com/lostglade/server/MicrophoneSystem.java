package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.block.MicrophoneBlock;
import com.lostglade.block.ModBlocks;
import com.lostglade.block.SpeakerBlock;
import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MicrophoneSystem {
	private static final long REFRESH_INTERVAL_TICKS = 5L;
	private static final int AUDIO_SAMPLE_RATE = 48_000;
	private static final int AUDIO_FRAME_SAMPLES = 960;
	private static final long AUDIO_FRAME_DURATION_MS = AUDIO_FRAME_SAMPLES * 1000L / AUDIO_SAMPLE_RATE;
	private static final long AUDIO_FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(AUDIO_FRAME_DURATION_MS);
	private static final long RECORDER_CAPTURE_LATENCY_NANOS = AUDIO_FRAME_NANOS * 4L;
	private static final long RENDERER_AUDIO_LIVE_MAX_LATE_NANOS = AUDIO_FRAME_NANOS * 2L;
	private static final long RENDERER_AUDIO_CLOCK_RESET_NANOS = TimeUnit.SECONDS.toNanos(10L);
	private static final int FRAME_BUFFER_CAPACITY = 192;
	private static final long MAX_FRAME_AGE = 3L;
	private static final long SENDER_EXPIRE_AFTER_FRAMES = 12L;
	private static final double WHISPER_DISTANCE_FACTOR = 0.5D;
	private static final double MIN_CAPTURE_DISTANCE = 6.0D;
	private static final double VANILLA_AUDIO_CAPTURE_DISTANCE_EXTRA_BLOCKS = 8.0D;
	private static final float MICROPHONE_GAIN_BOOST = 2.4F;
	private static final short[] SILENCE_FRAME = new short[AUDIO_FRAME_SAMPLES];
	private static final UUID RENDERER_AUDIO_SOURCE_UUID = UUID.nameUUIDFromBytes("lg2:renderer-bot-audio".getBytes(StandardCharsets.UTF_8));
	private static final Set<MicrophoneKey> KNOWN_MICROPHONES = ConcurrentHashMap.newKeySet();
	private static final ConcurrentHashMap<MicrophoneKey, MicrophoneRuntime> ACTIVE_MICROPHONES = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<String, CallMicrophoneRouteRuntime> ACTIVE_CALL_ROUTES = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, PlayerMicrophoneFeed> ACTIVE_PLAYER_FEEDS = new ConcurrentHashMap<>();
	private static volatile long tickCounter = 0L;

	private MicrophoneSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MicrophoneSystem::tick);
		ServerChunkEvents.CHUNK_LOAD.register(MicrophoneSystem::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(MicrophoneSystem::onChunkUnload);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdownAll());
	}

	public static void trackMicrophone(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		KNOWN_MICROPHONES.add(new MicrophoneKey(level.dimension(), pos.immutable()));
	}

	public static void untrackMicrophone(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		MicrophoneKey key = new MicrophoneKey(level.dimension(), pos.immutable());
		KNOWN_MICROPHONES.remove(key);
		stopRuntime(key);
	}

	public static void onMicrophoneStateChanged(ServerLevel level, BlockPos pos) {
		trackMicrophone(level, pos);
	}

	public static void onMicrophonePacket(MicrophonePacketEvent event) {
		if (event == null || !ServerVoicechatIntegration.isLoaded()) {
			return;
		}
		VoicechatApi voicechatApi = ServerVoicechatIntegration.getApi();
		if (voicechatApi == null) {
			return;
		}
		VoicechatConnection senderConnection = event.getSenderConnection();
		if (senderConnection == null || senderConnection.getPlayer() == null) {
			return;
		}
		Object rawPlayer = senderConnection.getPlayer().getPlayer();
		if (!(rawPlayer instanceof ServerPlayer senderPlayer)) {
			return;
		}
		byte[] opusData = event.getPacket() != null ? event.getPacket().getOpusEncodedData() : null;
		if (opusData == null || opusData.length == 0) {
			return;
		}
		UUID senderUuid = senderConnection.getPlayer().getUuid();
		PlayerMicrophoneFeed playerFeed = ACTIVE_PLAYER_FEEDS.get(senderUuid);
		if (playerFeed != null) {
			playerFeed.offerVoicePacket(senderUuid, opusData, voicechatApi);
		}
		if (ACTIVE_MICROPHONES.isEmpty()) {
			return;
		}
		Position senderPosition = senderConnection.getPlayer().getPosition();
		if (senderPosition == null) {
			return;
		}
		boolean whispering = event.getPacket().isWhispering();
		ServerLevel level = (ServerLevel) senderPlayer.level();
		for (MicrophoneRuntime runtime : ACTIVE_MICROPHONES.values()) {
			runtime.offerVoicePacket(level, senderPosition, senderUuid, whispering, opusData, voicechatApi);
		}
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		scanChunkForMicrophones(level, chunk);
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		int chunkX = chunk.getPos().x;
		int chunkZ = chunk.getPos().z;
		for (MicrophoneKey key : new ArrayList<>(KNOWN_MICROPHONES)) {
			if (!key.dimension().equals(level.dimension())) {
				continue;
			}
			if (SectionPos.blockToSectionCoord(key.pos().getX()) != chunkX || SectionPos.blockToSectionCoord(key.pos().getZ()) != chunkZ) {
				continue;
			}
			KNOWN_MICROPHONES.remove(key);
			stopRuntime(key);
		}
	}

	private static void scanChunkForMicrophones(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		int chunkMinX = chunk.getPos().getMinBlockX();
		int chunkMinZ = chunk.getPos().getMinBlockZ();
		LevelChunkSection[] sections = chunk.getSections();
		for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
			LevelChunkSection section = sections[sectionIndex];
			if (section == null || section.hasOnlyAir()) {
				continue;
			}
			int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
			int sectionMinY = SectionPos.sectionToBlockCoord(sectionY);
			for (int localY = 0; localY < 16; localY++) {
				for (int localZ = 0; localZ < 16; localZ++) {
					for (int localX = 0; localX < 16; localX++) {
					if (!section.getBlockState(localX, localY, localZ).is(ModBlocks.MICROPHONE)) {
						continue;
					}
					BlockPos microphonePos = new BlockPos(chunkMinX + localX, sectionMinY + localY, chunkMinZ + localZ);
					trackMicrophone(level, microphonePos);
					MicrophoneBlock.ensureDisplay(level, microphonePos);
				}
				}
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		tickCounter++;
		if ((tickCounter % REFRESH_INTERVAL_TICKS) != 0L) {
			return;
		}

		Set<MicrophoneKey> staleKeys = new HashSet<>(ACTIVE_MICROPHONES.keySet());
		for (MicrophoneKey key : new ArrayList<>(KNOWN_MICROPHONES)) {
			boolean keepKnown = refreshMicrophone(server, key);
			if (!keepKnown) {
				KNOWN_MICROPHONES.remove(key);
			}
			staleKeys.remove(key);
		}
		for (MicrophoneKey staleKey : staleKeys) {
			stopRuntime(staleKey);
		}
		synchronizeCallRoutes(server);
	}

	private static boolean refreshMicrophone(MinecraftServer server, MicrophoneKey key) {
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null || !level.hasChunkAt(key.pos())) {
			stopRuntime(key);
			return true;
		}

		Vec3 mountedPosition = RocketLaunchEventSystem.launchedMountedDevicePosition(level, key.pos());
		boolean launched = mountedPosition != null;
		BlockState state = level.getBlockState(key.pos());
		if (!launched && !state.is(ModBlocks.MICROPHONE)) {
			stopRuntime(key);
			return false;
		}

		if (!launched && !hasMicrophonePower(level, key.pos())) {
			stopRuntime(key);
			return true;
		}

		VoicechatApi voicechatApi = ServerVoicechatIntegration.getApi();
		VoicechatServerApi voicechatServerApi = ServerVoicechatIntegration.getServerApi();
		if (!ServerVoicechatIntegration.isLoaded() || voicechatApi == null || voicechatServerApi == null) {
			stopRuntime(key);
			return true;
		}

		Set<ScreenRuntimeKey> connectedScreens = connectedPoweredScreenKeys(level, key.pos(), launched);
		boolean routedToScreen = !connectedScreens.isEmpty();
		List<BlockPos> directlyConnectedSpeakers = SpeakerSystem.findConnectedPoweredSpeakerPositions(level, key.pos());
		List<BlockPos> connectedSpeakers = routedToScreen ? List.of() : directlyConnectedSpeakers;
		if (connectedSpeakers.isEmpty() && !routedToScreen) {
			stopRuntime(key);
			return true;
		}

		MicrophoneRuntime runtime = ACTIVE_MICROPHONES.computeIfAbsent(key, MicrophoneRuntime::new);
		Set<SpeakerOutputTarget> excludedSpeakerSources = excludedSpeakerSources(server, key, directlyConnectedSpeakers, connectedScreens);
		if (!runtime.update(level, mountedPosition, connectedSpeakers, connectedScreens, excludedSpeakerSources, voicechatApi, voicechatServerApi)) {
			stopRuntime(key);
		}
		return true;
	}

	private static Set<ScreenRuntimeKey> connectedPoweredScreenKeys(ServerLevel level, BlockPos microphonePos, boolean launched) {
		if (level == null || microphonePos == null) {
			return Set.of();
		}
		Map<ScreenRuntimeKey, ScreenComponent> components = MonitorScreenWireConnectivity.collectConnectedComponentsForWireSource(level, microphonePos);
		Set<ScreenRuntimeKey> connected = new HashSet<>();
		for (Map.Entry<ScreenRuntimeKey, ScreenComponent> entry : components.entrySet()) {
			ScreenComponent component = entry.getValue();
			if (entry.getKey() != null && component != null && component.powered()) {
				connected.add(entry.getKey());
			}
		}
		if (launched) {
			BluetoothLinkSystem.Endpoint microphone = BluetoothLinkSystem.mountedBlockEndpoint(
					level.dimension(), BluetoothLinkSystem.EndpointType.MICROPHONE, microphonePos
			);
			for (BluetoothLinkSystem.Endpoint endpoint : BluetoothLinkSystem.linkedEndpoints(microphone)) {
				if (endpoint.type() != BluetoothLinkSystem.EndpointType.SCREEN) {
					continue;
				}
				ScreenComponent component = MonitorScreenSystem.resolveBluetoothScreenComponent(level, endpoint);
				if (component != null && component.powered()) {
					connected.add(component.runtimeKey());
				}
			}
		}
		return connected.isEmpty() ? Set.of() : Set.copyOf(connected);
	}

	private static boolean hasMicrophonePower(ServerLevel level, BlockPos pos) {
		return level != null && pos != null
				&& (level.hasNeighborSignal(pos) || level.getBestNeighborSignal(pos) > 0);
	}

	private static void stopRuntime(MicrophoneKey key) {
		MicrophoneRuntime runtime = ACTIVE_MICROPHONES.remove(key);
		if (runtime != null) {
			runtime.close();
		}
	}

	private static void shutdownAll() {
		for (MicrophoneRuntime runtime : ACTIVE_MICROPHONES.values()) {
			runtime.close();
		}
		ACTIVE_MICROPHONES.clear();
		for (CallMicrophoneRouteRuntime runtime : ACTIVE_CALL_ROUTES.values()) {
			runtime.close();
		}
		ACTIVE_CALL_ROUTES.clear();
		for (PlayerMicrophoneFeed feed : ACTIVE_PLAYER_FEEDS.values()) {
			feed.close();
		}
		ACTIVE_PLAYER_FEEDS.clear();
	}

	private static void synchronizeCallRoutes(MinecraftServer server) {
		List<ScreenMicrophoneCallRoute> routes = MonitorMaxRuntime.collectMicrophoneCallRoutes(server);
		if (routes.isEmpty()) {
			for (CallMicrophoneRouteRuntime runtime : ACTIVE_CALL_ROUTES.values()) {
				runtime.close();
			}
			ACTIVE_CALL_ROUTES.clear();
			return;
		}

		Set<String> keep = new HashSet<>();
		for (ScreenMicrophoneCallRoute route : routes) {
			if (route == null || route.routeId() == null || route.routeId().isBlank()) {
				continue;
			}
			keep.add(route.routeId());
			ACTIVE_CALL_ROUTES.compute(route.routeId(), (ignored, existing) -> {
				CallMicrophoneRouteRuntime runtime = existing != null ? existing : new CallMicrophoneRouteRuntime();
				if (!runtime.update(server, route)) {
					runtime.close();
					return null;
				}
				return runtime;
			});
		}
		for (Map.Entry<String, CallMicrophoneRouteRuntime> entry : new ArrayList<>(ACTIVE_CALL_ROUTES.entrySet())) {
			if (keep.contains(entry.getKey())) {
				continue;
			}
			CallMicrophoneRouteRuntime removed = ACTIVE_CALL_ROUTES.remove(entry.getKey());
			if (removed != null) {
				removed.close();
			}
		}
	}

	public static void onMaxCallStateChanged(MinecraftServer server) {
		if (server != null) {
			synchronizeCallRoutes(server);
		}
	}

	private static List<Map.Entry<MicrophoneKey, MicrophoneRuntime>> connectedScreenMicrophones(MinecraftServer server, ScreenRuntimeKey screenKey) {
		if (server == null || screenKey == null) {
			return List.of();
		}
		List<Map.Entry<MicrophoneKey, MicrophoneRuntime>> microphones = new ArrayList<>();
		for (Map.Entry<MicrophoneKey, MicrophoneRuntime> entry : ACTIVE_MICROPHONES.entrySet()) {
			MicrophoneKey key = entry.getKey();
			MicrophoneRuntime runtime = entry.getValue();
			if (key == null || runtime == null || !runtime.captureEnabled || !runtime.connectedScreenKeys.contains(screenKey)) {
				continue;
			}
			microphones.add(entry);
		}
		microphones.sort(Comparator.comparing(entry -> microphoneSortKey(entry.getKey())));
		return microphones;
	}

	public static int connectedMicrophoneCount(MinecraftServer server, ScreenRuntimeKey screenKey) {
		return connectedScreenMicrophones(server, screenKey).size();
	}

	public static List<ScreenMicrophoneDevice> connectedMicrophoneDevices(MinecraftServer server, ScreenRuntimeKey screenKey) {
		List<Map.Entry<MicrophoneKey, MicrophoneRuntime>> microphones = connectedScreenMicrophones(server, screenKey);
		if (microphones.isEmpty()) {
			return List.of();
		}
		List<ScreenMicrophoneDevice> devices = new ArrayList<>(microphones.size());
		for (int index = 0; index < microphones.size(); index++) {
			MicrophoneKey key = microphones.get(index).getKey();
			BlockPos pos = key != null ? key.pos() : null;
			String fallbackTitle = pos != null ? "Микрофон " + (index + 1) : "Микрофон";
			String title = key != null
					? PlacedDeviceNameStore.microphoneName(server, key.dimension(), pos, fallbackTitle)
					: fallbackTitle;
			devices.add(new ScreenMicrophoneDevice(
					index,
					title,
					pos != null ? pos.getX() + " " + pos.getY() + " " + pos.getZ() : "",
					key != null ? key.dimension() : null,
					pos
			));
		}
		return List.copyOf(devices);
	}

	public static MicrophonePcmRecorder startPcmRecorder(MinecraftServer server, ScreenRuntimeKey screenKey, int selectedMicrophoneIndex, Consumer<short[]> frameConsumer) {
		if (frameConsumer == null) {
			return null;
		}
		MicrophonePcmRecorder recorder = startTimedPcmRecorder(
				server,
				screenKey,
				selectedMicrophoneIndex,
				frame -> frameConsumer.accept(frame.samples())
		);
		return recorder;
	}

	public static MicrophonePcmRecorder startTimedPcmRecorder(MinecraftServer server, ScreenRuntimeKey screenKey, int selectedMicrophoneIndex, Consumer<PcmFrame> frameConsumer) {
		return startTimedPcmRecorder(server, screenKey, List.of(selectedMicrophoneIndex), frameConsumer);
	}

	public static MicrophonePcmRecorder startTimedPcmRecorder(MinecraftServer server, ScreenRuntimeKey screenKey, Collection<Integer> selectedMicrophoneIndices, Consumer<PcmFrame> frameConsumer) {
		List<Map.Entry<MicrophoneKey, MicrophoneRuntime>> microphones = connectedScreenMicrophones(server, screenKey);
		if (microphones.isEmpty() || frameConsumer == null) {
			return null;
		}
		List<SharedMicrophoneFeed> feeds = selectedMicrophoneFeeds(microphones, selectedMicrophoneIndices);
		if (feeds.isEmpty()) {
			return null;
		}
		MicrophonePcmRecorder recorder = new MicrophonePcmRecorder(feeds, null, frameConsumer);
		recorder.start();
		return recorder;
	}

	public static MicrophonePcmRecorder startVideoSyncedPcmRecorder(
			MinecraftServer server,
			ScreenRuntimeKey screenKey,
			int selectedMicrophoneIndex,
			Supplier<PcmTimelineAnchor> timelineAnchorSupplier,
			Consumer<PcmFrame> frameConsumer
	) {
		return startVideoSyncedPcmRecorder(server, screenKey, List.of(selectedMicrophoneIndex), timelineAnchorSupplier, frameConsumer);
	}

	public static MicrophonePcmRecorder startVideoSyncedPcmRecorder(
			MinecraftServer server,
			ScreenRuntimeKey screenKey,
			Collection<Integer> selectedMicrophoneIndices,
			Supplier<PcmTimelineAnchor> timelineAnchorSupplier,
			Consumer<PcmFrame> frameConsumer
	) {
		List<Map.Entry<MicrophoneKey, MicrophoneRuntime>> microphones = connectedScreenMicrophones(server, screenKey);
		if (microphones.isEmpty() || timelineAnchorSupplier == null || frameConsumer == null) {
			return null;
		}
		List<SharedMicrophoneFeed> feeds = selectedMicrophoneFeeds(microphones, selectedMicrophoneIndices);
		if (feeds.isEmpty()) {
			return null;
		}
		MicrophonePcmRecorder recorder = new MicrophonePcmRecorder(feeds, timelineAnchorSupplier, frameConsumer);
		recorder.start();
		return recorder;
	}

	public static MicrophonePcmRecorder startPlayerPcmRecorder(ServerPlayer player, Consumer<PcmFrame> frameConsumer) {
		if (player == null || frameConsumer == null || !ServerVoicechatIntegration.isLoaded() || ServerVoicechatIntegration.getApi() == null) {
			return null;
		}
		UUID playerId = player.getUUID();
		if (playerId == null) {
			return null;
		}
		PlayerMicrophoneFeed playerFeed = retainPlayerMicrophoneFeed(playerId);
		if (playerFeed == null) {
			return null;
		}
		MicrophonePcmRecorder recorder = new MicrophonePcmRecorder(
				List.of(playerFeed.feed()),
				null,
				frameConsumer,
				() -> releasePlayerMicrophoneFeed(playerId, playerFeed)
		);
		recorder.start();
		return recorder;
	}

	private static PlayerMicrophoneFeed retainPlayerMicrophoneFeed(UUID playerId) {
		if (playerId == null) {
			return null;
		}
		return ACTIVE_PLAYER_FEEDS.compute(playerId, (ignored, existing) -> {
			PlayerMicrophoneFeed feed = existing != null && !existing.closed() ? existing : new PlayerMicrophoneFeed();
			feed.retain();
			return feed;
		});
	}

	private static void releasePlayerMicrophoneFeed(UUID playerId, PlayerMicrophoneFeed feed) {
		if (playerId == null || feed == null) {
			return;
		}
		ACTIVE_PLAYER_FEEDS.computeIfPresent(playerId, (ignored, existing) -> {
			if (existing != feed) {
				return existing;
			}
			if (!existing.release()) {
				return existing;
			}
			existing.close();
			return null;
		});
	}

	private static List<SharedMicrophoneFeed> selectedMicrophoneFeeds(
			List<Map.Entry<MicrophoneKey, MicrophoneRuntime>> microphones,
			Collection<Integer> selectedMicrophoneIndices
	) {
		if (microphones == null || microphones.isEmpty()) {
			return List.of();
		}
		Set<Integer> resolvedIndices = new LinkedHashSet<>();
		if (selectedMicrophoneIndices != null) {
			for (Integer selectedIndex : selectedMicrophoneIndices) {
				if (selectedIndex != null && selectedIndex >= 0 && selectedIndex < microphones.size()) {
					resolvedIndices.add(selectedIndex);
				}
			}
		}
		if (resolvedIndices.isEmpty()) {
			resolvedIndices.add(0);
		}
		List<SharedMicrophoneFeed> feeds = new ArrayList<>(resolvedIndices.size());
		for (int index : resolvedIndices) {
			MicrophoneRuntime runtime = microphones.get(index).getValue();
			if (runtime != null && !runtime.closed && runtime.feed != null) {
				feeds.add(runtime.feed);
			}
		}
		return feeds.isEmpty() ? List.of() : List.copyOf(feeds);
	}

	private static String microphoneSortKey(MicrophoneKey key) {
		if (key == null || key.pos() == null || key.dimension() == null) {
			return "";
		}
		return key.dimension().identifier() + ":" + key.pos().getX() + ":" + key.pos().getY() + ":" + key.pos().getZ();
	}

	private static String rendererAudioOwnerKey(MicrophoneKey key) {
		return key == null ? null : "lg2:microphone-audio:" + microphoneSortKey(key);
	}

	private static UUID speakerAudioSourceUuid(SpeakerOutputTarget source) {
		if (source == null || source.dimension() == null || source.pos() == null) {
			return null;
		}
		String sourceKey = source.dimension().identifier() + ":" + source.pos().getX() + ":" + source.pos().getY() + ":" + source.pos().getZ();
		return UUID.nameUUIDFromBytes(("lg2:speaker-audio:" + sourceKey).getBytes(StandardCharsets.UTF_8));
	}

	private static float distanceAttenuation(double distance, double maxDistance) {
		if (maxDistance <= 0.0D || distance >= maxDistance) {
			return 0.0F;
		}
		return (float) Math.clamp(1.0D - distance / maxDistance, 0.0D, 1.0D);
	}

	private static List<SpeakerOutputTarget> connectedScreenSpeakers(MinecraftServer server, ScreenRuntimeKey screenKey) {
		if (server == null || screenKey == null) {
			return List.of();
		}
		ServerLevel level = server.getLevel(screenKey.dimension());
		if (level == null) {
			return List.of();
		}
		ScreenComponent component = MonitorScreenSystem.resolveScreenComponent(server, screenKey);
		if (component == null || !component.powered()) {
			return List.of();
		}

		LinkedHashSet<SpeakerOutputTarget> speakers = new LinkedHashSet<>();
		for (ItemFrame frame : component.frameCoords().keySet()) {
			BlockPos framePos = frame.blockPosition();
			BlockPos supportPos = framePos.relative(frame.getDirection().getOpposite());
			for (BlockPos speakerPos : SpeakerSystem.findConnectedPoweredSpeakerPositions(level, framePos)) {
				speakers.add(new SpeakerOutputTarget(level.dimension(), speakerPos.immutable()));
			}
			for (BlockPos speakerPos : SpeakerSystem.findConnectedPoweredSpeakerPositions(level, supportPos)) {
				speakers.add(new SpeakerOutputTarget(level.dimension(), speakerPos.immutable()));
			}
		}

		BluetoothLinkSystem.Endpoint screenEndpoint = MonitorScreenSystem.bluetoothScreenEndpoint(level, component);
		for (BluetoothLinkSystem.Endpoint linked : BluetoothLinkSystem.linkedEndpoints(screenEndpoint)) {
			if (linked.type() != BluetoothLinkSystem.EndpointType.SPEAKER) {
				continue;
			}
			ServerLevel linkedLevel = server.getLevel(linked.dimension());
			if (isUsableSpeaker(linkedLevel, linked.pos())) {
				speakers.add(new SpeakerOutputTarget(linked.dimension(), linked.pos().immutable()));
			}
		}
		return List.copyOf(speakers);
	}

	private static boolean isUsableSpeaker(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null || !level.hasChunkAt(pos)) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.is(ModBlocks.SPEAKER)
				&& (level.hasNeighborSignal(pos) || level.getBestNeighborSignal(pos) > 0)
				&& SpeakerBlock.readVolumePercent(state) > 0;
	}

	static void offerSpeakerAudio(ResourceKey<Level> dimension, BlockPos speakerPos, double audibleDistance, short[] frame) {
		if (dimension == null || speakerPos == null || frame == null || frame.length == 0 || audibleDistance <= 0.0D) {
			return;
		}
		SpeakerOutputTarget source = new SpeakerOutputTarget(dimension, speakerPos.immutable());
		for (MicrophoneRuntime runtime : ACTIVE_MICROPHONES.values()) {
			runtime.offerSpeakerFrame(source, audibleDistance, frame);
		}
	}

	private static Set<SpeakerOutputTarget> excludedSpeakerSources(
			MinecraftServer server,
			MicrophoneKey microphone,
			List<BlockPos> directlyConnectedSpeakers,
			Set<ScreenRuntimeKey> connectedScreens
	) {
		if (server == null || microphone == null) {
			return Set.of();
		}
		Set<SpeakerOutputTarget> excluded = new LinkedHashSet<>();
		if (directlyConnectedSpeakers != null) {
			for (BlockPos speakerPos : directlyConnectedSpeakers) {
				if (speakerPos != null) {
					excluded.add(new SpeakerOutputTarget(microphone.dimension(), speakerPos.immutable()));
				}
			}
		}
		if (connectedScreens != null) {
			for (ScreenRuntimeKey screen : connectedScreens) {
				excluded.addAll(connectedScreenSpeakers(server, screen));
			}
		}
		return excluded.isEmpty() ? Set.of() : Set.copyOf(excluded);
	}

	static record ScreenMicrophoneCallRoute(String routeId, ScreenRuntimeKey sourceScreen, ScreenRuntimeKey outputScreen, int selectedMicrophoneIndex) {
	}

	public static record ScreenMicrophoneDevice(int index, String title, String subtitle, ResourceKey<Level> dimension, BlockPos pos) {
	}

	public static record PcmFrame(short[] samples, long captureNanos) {
	}

	public static record PcmTimelineAnchor(long serverStartNanos, long rendererClientStartNanos) {
	}

	public static final class MicrophonePcmRecorder implements AutoCloseable {
		private final List<SharedMicrophoneFeed> feeds;
		private final Supplier<PcmTimelineAnchor> timelineAnchorSupplier;
		private final Consumer<PcmFrame> frameConsumer;
		private final Runnable closeCallback;
		private volatile boolean finishRequested;
		private volatile long finishAtNanos = Long.MAX_VALUE;
		private volatile boolean closed;
		private volatile boolean closeCallbackRan;
		private Thread thread;

		private MicrophonePcmRecorder(List<SharedMicrophoneFeed> feeds, Supplier<PcmTimelineAnchor> timelineAnchorSupplier, Consumer<PcmFrame> frameConsumer) {
			this(feeds, timelineAnchorSupplier, frameConsumer, null);
		}

		private MicrophonePcmRecorder(List<SharedMicrophoneFeed> feeds, Supplier<PcmTimelineAnchor> timelineAnchorSupplier, Consumer<PcmFrame> frameConsumer, Runnable closeCallback) {
			this.feeds = feeds != null ? feeds : List.of();
			this.timelineAnchorSupplier = timelineAnchorSupplier;
			this.frameConsumer = frameConsumer;
			this.closeCallback = closeCallback;
		}

		private void start() {
			Thread created = new Thread(this::run, "lg2-monitor-microphone-recorder");
			created.setDaemon(true);
			this.thread = created;
			created.start();
		}

		private void run() {
			try {
				PcmTimelineAnchor timelineAnchor = awaitTimelineAnchor();
				if (this.closed || timelineAnchor == null && this.timelineAnchorSupplier != null) {
					return;
				}
				boolean videoSynced = timelineAnchor != null;
				long sourceFrameAt = videoSynced ? timelineAnchor.serverStartNanos() : System.nanoTime();
				long nextEmitAt = Math.max(System.nanoTime(), sourceFrameAt + RECORDER_CAPTURE_LATENCY_NANOS);
				while (!this.closed) {
					if (this.finishRequested && sourceFrameAt >= this.finishAtNanos) {
						return;
					}
					long sleepNanos = nextEmitAt - System.nanoTime();
					if (sleepNanos > 0L) {
						try {
							TimeUnit.NANOSECONDS.sleep(sleepNanos);
						} catch (InterruptedException exception) {
							if (this.closed) {
								Thread.currentThread().interrupt();
								return;
							}
						}
					}
					if (this.closed || this.finishRequested && sourceFrameAt >= this.finishAtNanos) {
						return;
					}
					short[] frame = videoSynced
							? this.videoFrameAt(sourceFrameAt, rendererClientNanosAt(timelineAnchor, sourceFrameAt))
							: this.frameAt(sourceFrameAt);
					this.frameConsumer.accept(new PcmFrame(frame != null ? frame.clone() : SILENCE_FRAME.clone(), sourceFrameAt));
					sourceFrameAt += AUDIO_FRAME_NANOS;
					nextEmitAt += AUDIO_FRAME_NANOS;
					if (!videoSynced && nextEmitAt < System.nanoTime() - AUDIO_FRAME_NANOS * 8L) {
						long now = System.nanoTime();
						sourceFrameAt = now - RECORDER_CAPTURE_LATENCY_NANOS;
						nextEmitAt = now;
					}
				}
			} finally {
				runCloseCallback();
			}
		}

		private PcmTimelineAnchor awaitTimelineAnchor() {
			if (this.timelineAnchorSupplier == null) {
				return null;
			}
			while (!this.closed && !this.finishRequested) {
				PcmTimelineAnchor anchor = this.timelineAnchorSupplier.get();
				if (anchor != null && anchor.serverStartNanos() > 0L && anchor.rendererClientStartNanos() > 0L) {
					return anchor;
				}
				try {
					TimeUnit.MILLISECONDS.sleep(2L);
				} catch (InterruptedException exception) {
					if (this.closed) {
						Thread.currentThread().interrupt();
						return null;
					}
				}
			}
			return null;
		}

		private static long rendererClientNanosAt(PcmTimelineAnchor anchor, long serverFrameNanos) {
			if (anchor == null) {
				return serverFrameNanos;
			}
			long offset = serverFrameNanos - anchor.serverStartNanos();
			if (offset >= 0L) {
				return anchor.rendererClientStartNanos() > Long.MAX_VALUE - offset ? Long.MAX_VALUE : anchor.rendererClientStartNanos() + offset;
			}
			return anchor.rendererClientStartNanos() < Long.MIN_VALUE - offset ? Long.MIN_VALUE : anchor.rendererClientStartNanos() + offset;
		}

		private short[] frameAt(long sourceFrameAt) {
			short[] mixed = null;
			for (SharedMicrophoneFeed feed : this.feeds) {
				if (feed == null) {
					continue;
				}
				mixed = mixRecorderFrame(mixed, feed.frameAt(sourceFrameAt));
			}
			return mixed;
		}

		private short[] videoFrameAt(long serverFrameAt, long rendererClientFrameAt) {
			short[] mixed = null;
			for (SharedMicrophoneFeed feed : this.feeds) {
				if (feed == null) {
					continue;
				}
				mixed = mixRecorderFrame(mixed, feed.videoFrameAt(serverFrameAt, rendererClientFrameAt));
			}
			return mixed;
		}

		private static short[] mixRecorderFrame(short[] mixed, short[] frame) {
			if (frame == null || frame.length == 0) {
				return mixed;
			}
			if (mixed == null) {
				return frame.clone();
			}
			int length = Math.min(mixed.length, frame.length);
			for (int index = 0; index < length; index++) {
				mixed[index] = SpeakerSystem.softLimitSample(mixed[index] + frame[index]);
			}
			return mixed;
		}

		public void finishAndJoin() {
			this.finishAtNanos = System.nanoTime();
			this.finishRequested = true;
			Thread current = this.thread;
			if (current == null) {
				return;
			}
			try {
				current.join(TimeUnit.NANOSECONDS.toMillis(RECORDER_CAPTURE_LATENCY_NANOS + AUDIO_FRAME_NANOS * 12L));
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			if (current.isAlive()) {
				close();
			}
		}

		@Override
		public void close() {
			this.closed = true;
			Thread current = this.thread;
			if (current != null) {
				current.interrupt();
			}
			runCloseCallback();
		}

		private void runCloseCallback() {
			if (this.closeCallback == null || this.closeCallbackRan) {
				return;
			}
			synchronized (this) {
				if (this.closeCallbackRan) {
					return;
				}
				this.closeCallbackRan = true;
			}
			this.closeCallback.run();
		}
	}

	private static final class PlayerMicrophoneFeed {
		private final SharedMicrophoneFeed feed = new SharedMicrophoneFeed();
		private int references;
		private volatile boolean closed;

		private synchronized void retain() {
			if (!this.closed) {
				this.references++;
			}
		}

		private synchronized boolean release() {
			if (this.references > 0) {
				this.references--;
			}
			return this.references <= 0;
		}

		private synchronized boolean closed() {
			return this.closed;
		}

		private SharedMicrophoneFeed feed() {
			return this.feed;
		}

		private void offerVoicePacket(UUID senderUuid, byte[] opusData, VoicechatApi voicechatApi) {
			if (this.closed || senderUuid == null || opusData == null || opusData.length == 0 || voicechatApi == null) {
				return;
			}
			this.feed.offerPacket(senderUuid, opusData, 1.0F, voicechatApi);
		}

		private void close() {
			synchronized (this) {
				if (this.closed) {
					return;
				}
				this.closed = true;
				this.references = 0;
			}
			this.feed.close();
		}
	}

	private record SpeakerOutputTarget(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private record CallOutputKey(MicrophoneKey microphone, SpeakerOutputTarget speaker) {
	}

	private record MicrophoneKey(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final class CallMicrophoneRouteRuntime {
		private final ConcurrentHashMap<CallOutputKey, MicrophoneOutputRuntime> outputs = new ConcurrentHashMap<>();
		private volatile boolean closed;

		private boolean update(MinecraftServer server, ScreenMicrophoneCallRoute route) {
			if (this.closed || server == null || route == null) {
				return false;
			}
			List<Map.Entry<MicrophoneKey, MicrophoneRuntime>> microphones = connectedScreenMicrophones(server, route.sourceScreen());
			if (route.selectedMicrophoneIndex() >= 0 && route.selectedMicrophoneIndex() < microphones.size()) {
				microphones = List.of(microphones.get(route.selectedMicrophoneIndex()));
			}
			List<SpeakerOutputTarget> speakers = connectedScreenSpeakers(server, route.outputScreen());
			Set<CallOutputKey> keep = new HashSet<>();
			for (Map.Entry<MicrophoneKey, MicrophoneRuntime> microphoneEntry : microphones) {
				MicrophoneKey microphoneKey = microphoneEntry.getKey();
				MicrophoneRuntime microphoneRuntime = microphoneEntry.getValue();
				if (microphoneKey == null || microphoneRuntime == null || microphoneRuntime.closed) {
					continue;
				}
				for (SpeakerOutputTarget speaker : speakers) {
					ServerLevel speakerLevel = server.getLevel(speaker.dimension());
					if (!isUsableSpeaker(speakerLevel, speaker.pos())) {
						continue;
					}
					BlockState speakerState = speakerLevel.getBlockState(speaker.pos());
					CallOutputKey outputKey = new CallOutputKey(microphoneKey, speaker);
					keep.add(outputKey);
					this.outputs.compute(outputKey, (ignored, existing) -> {
						MicrophoneOutputRuntime runtime = existing;
						if (runtime == null) {
							runtime = new MicrophoneOutputRuntime(microphoneRuntime.feed, speaker.pos());
							if (!runtime.start(speakerLevel, speakerState, ServerVoicechatIntegration.getApi(), ServerVoicechatIntegration.getServerApi())) {
								runtime.close();
								return null;
							}
							return runtime;
						}
						if (!runtime.update(speakerLevel, speakerState, ServerVoicechatIntegration.getApi(), ServerVoicechatIntegration.getServerApi())) {
							runtime.close();
							return null;
						}
						return runtime;
					});
				}
			}
			for (Map.Entry<CallOutputKey, MicrophoneOutputRuntime> entry : new ArrayList<>(this.outputs.entrySet())) {
				if (keep.contains(entry.getKey())) {
					continue;
				}
				MicrophoneOutputRuntime removed = this.outputs.remove(entry.getKey());
				if (removed != null) {
					removed.close();
				}
			}
			return true;
		}

		private void close() {
			this.closed = true;
			for (MicrophoneOutputRuntime runtime : this.outputs.values()) {
				runtime.close();
			}
			this.outputs.clear();
		}
	}

	private static final class MicrophoneRuntime {
		private final MicrophoneKey key;
		private final SharedMicrophoneFeed feed;
		private final ConcurrentHashMap<BlockPos, MicrophoneOutputRuntime> outputs;
		private volatile boolean closed;
		private volatile boolean captureEnabled;
		private volatile Set<ScreenRuntimeKey> connectedScreenKeys;
		private volatile Set<SpeakerOutputTarget> excludedSpeakerSources;
		private volatile double captureDistanceSq;
		private volatile Vec3 capturePosition;
		private volatile String rendererAudioOwnerKey;

		private MicrophoneRuntime(MicrophoneKey key) {
			this.key = key;
			this.feed = new SharedMicrophoneFeed();
			this.outputs = new ConcurrentHashMap<>();
			this.connectedScreenKeys = Set.of();
			this.excludedSpeakerSources = Set.of();
		}

		private boolean update(
				ServerLevel level,
				Vec3 mountedPosition,
				List<BlockPos> connectedSpeakers,
				Set<ScreenRuntimeKey> connectedScreens,
				Set<SpeakerOutputTarget> excludedSpeakerSources,
				VoicechatApi voicechatApi,
				VoicechatServerApi voicechatServerApi
		) {
			if (this.closed || level == null || voicechatApi == null || voicechatServerApi == null) {
				return false;
			}
			double captureDistance = Math.max(MIN_CAPTURE_DISTANCE, voicechatApi.getVoiceChatDistance());
			this.captureDistanceSq = captureDistance * captureDistance;
			this.capturePosition = mountedPosition != null ? mountedPosition : this.key.pos().getCenter();
			this.connectedScreenKeys = connectedScreens == null || connectedScreens.isEmpty() ? Set.of() : Set.copyOf(connectedScreens);
			this.excludedSpeakerSources = excludedSpeakerSources == null || excludedSpeakerSources.isEmpty() ? Set.of() : Set.copyOf(excludedSpeakerSources);
			this.captureEnabled = !this.connectedScreenKeys.isEmpty() || !connectedSpeakers.isEmpty();
			synchronizeRendererAudioCapture(level, captureDistance + VANILLA_AUDIO_CAPTURE_DISTANCE_EXTRA_BLOCKS);
			synchronizeOutputs(level, connectedSpeakers, voicechatApi, voicechatServerApi);
			return true;
		}

		private void synchronizeRendererAudioCapture(ServerLevel level, double captureDistance) {
			if (this.closed || level == null || !this.captureEnabled) {
				stopRendererAudioCapture();
				return;
			}
			String ownerKey = rendererAudioOwnerKey(this.key);
			this.rendererAudioOwnerKey = ownerKey;
			boolean started = RendererBotCameraSystem.ensureAudioCapture(
					ownerKey,
					level,
					BlockPos.containing(this.capturePosition),
					Math.max(MIN_CAPTURE_DISTANCE, captureDistance),
					this.feed::offerRendererAudioFrame,
					message -> {
					}
			);
			if (!started) {
				stopRendererAudioCapture();
			}
		}

		private void stopRendererAudioCapture() {
			String ownerKey = this.rendererAudioOwnerKey;
			this.rendererAudioOwnerKey = null;
			if (ownerKey != null) {
				RendererBotCameraSystem.stopAudioCapture(ownerKey);
			}
		}

		private void offerVoicePacket(
				ServerLevel senderLevel,
				Position senderPosition,
				UUID senderUuid,
				boolean whispering,
				byte[] opusData,
				VoicechatApi voicechatApi
		) {
			if (this.closed || !this.captureEnabled || senderLevel == null || senderPosition == null || senderUuid == null || opusData == null || opusData.length == 0 || voicechatApi == null) {
				return;
			}
			if (!Objects.equals(senderLevel.dimension(), this.key.dimension())) {
				return;
			}
			Vec3 microphonePosition = this.capturePosition != null ? this.capturePosition : this.key.pos().getCenter();
			double dx = senderPosition.getX() - microphonePosition.x;
			double dy = senderPosition.getY() - microphonePosition.y;
			double dz = senderPosition.getZ() - microphonePosition.z;
			double maxDistanceSq = whispering ? this.captureDistanceSq * (WHISPER_DISTANCE_FACTOR * WHISPER_DISTANCE_FACTOR) : this.captureDistanceSq;
			double distanceSq = (dx * dx) + (dy * dy) + (dz * dz);
			if (distanceSq > maxDistanceSq) {
				return;
			}
			float gain = distanceAttenuation(Math.sqrt(distanceSq), Math.sqrt(maxDistanceSq));
			if (gain <= 0.0F) {
				return;
			}
			this.feed.offerPacket(senderUuid, opusData, gain, voicechatApi);
		}

		private void offerSpeakerFrame(SpeakerOutputTarget source, double speakerAudibleDistance, short[] frame) {
			if (this.closed || !this.captureEnabled || source == null || frame == null || frame.length == 0 || !Objects.equals(source.dimension(), this.key.dimension())) {
				return;
			}
			UUID sourceUuid = speakerAudioSourceUuid(source);
			if (sourceUuid == null) {
				return;
			}
			double dx = source.pos().getX() - this.key.pos().getX();
			double dy = source.pos().getY() - this.key.pos().getY();
			double dz = source.pos().getZ() - this.key.pos().getZ();
			double sourceDistance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
			float gain = MicrophoneSpeakerCapturePolicy.captureGain(
					this.excludedSpeakerSources.contains(source),
					sourceDistance,
					Math.sqrt(this.captureDistanceSq),
					speakerAudibleDistance,
					MICROPHONE_GAIN_BOOST * SpeakerSystem.maximumVolumeFactor()
			);
			if (gain > 0.0F) {
				this.feed.offerSpeakerFrame(sourceUuid, frame, gain);
			}
		}

		private void synchronizeOutputs(ServerLevel level, List<BlockPos> connectedSpeakers, VoicechatApi voicechatApi, VoicechatServerApi voicechatServerApi) {
			Set<BlockPos> keep = new HashSet<>();
			for (BlockPos speakerPos : connectedSpeakers) {
				if (speakerPos == null || !level.hasChunkAt(speakerPos)) {
					continue;
				}
				BlockState speakerState = level.getBlockState(speakerPos);
				if (!speakerState.is(ModBlocks.SPEAKER)) {
					continue;
				}
				BlockPos immutablePos = speakerPos.immutable();
				keep.add(immutablePos);
				this.outputs.compute(immutablePos, (ignored, existing) -> {
					MicrophoneOutputRuntime runtime = existing;
					if (runtime == null) {
						runtime = new MicrophoneOutputRuntime(this.feed, immutablePos);
						if (!runtime.start(level, speakerState, voicechatApi, voicechatServerApi)) {
							runtime.close();
							return null;
						}
						return runtime;
					}
					if (!runtime.update(level, speakerState, voicechatApi, voicechatServerApi)) {
						runtime.close();
						return null;
					}
					return runtime;
				});
			}
			for (Map.Entry<BlockPos, MicrophoneOutputRuntime> entry : new ArrayList<>(this.outputs.entrySet())) {
				if (keep.contains(entry.getKey())) {
					continue;
				}
				MicrophoneOutputRuntime removed = this.outputs.remove(entry.getKey());
				if (removed != null) {
					removed.close();
				}
			}
		}

		private void close() {
			this.closed = true;
			this.captureEnabled = false;
			this.connectedScreenKeys = Set.of();
			this.excludedSpeakerSources = Set.of();
			stopRendererAudioCapture();
			this.feed.close();
			for (MicrophoneOutputRuntime runtime : this.outputs.values()) {
				runtime.close();
			}
			this.outputs.clear();
			this.feed.close();
		}
	}

	private static final class MicrophoneOutputRuntime {
		private final SharedMicrophoneFeed feed;
		private final BlockPos speakerPos;
		private final UUID channelId;
		private volatile ResourceKey<Level> speakerDimension;
		private volatile int volumePercent;
		private volatile boolean closed;
		private OpusEncoder encoder;
		private LocationalAudioChannel channel;
		private AudioPlayer player;

		private MicrophoneOutputRuntime(SharedMicrophoneFeed feed, BlockPos speakerPos) {
			this.feed = feed;
			this.speakerPos = speakerPos;
			this.channelId = UUID.randomUUID();
			this.volumePercent = 50;
		}

		private boolean start(ServerLevel level, BlockState speakerState, VoicechatApi voicechatApi, VoicechatServerApi voicechatServerApi) {
			this.speakerDimension = level != null ? level.dimension() : null;
			this.volumePercent = SpeakerBlock.readVolumePercent(speakerState);
			return ensureVoicechatPlayer(level, voicechatApi, voicechatServerApi);
		}

		private boolean update(ServerLevel level, BlockState speakerState, VoicechatApi voicechatApi, VoicechatServerApi voicechatServerApi) {
			if (this.closed) {
				return false;
			}
			this.speakerDimension = level != null ? level.dimension() : null;
			this.volumePercent = SpeakerBlock.readVolumePercent(speakerState);
			return ensureVoicechatPlayer(level, voicechatApi, voicechatServerApi);
		}

		private boolean ensureVoicechatPlayer(ServerLevel level, VoicechatApi voicechatApi, VoicechatServerApi voicechatServerApi) {
			if (level == null || voicechatApi == null || voicechatServerApi == null) {
				return false;
			}
			SpeakerSystem.ensureSpeakerVolumeCategoryRegistered(voicechatApi, voicechatServerApi);
			if (this.player != null && !this.player.isStopped()) {
				updateChannelProperties(level, voicechatApi);
				return true;
			}
			OpusEncoder createdEncoder = null;
			try {
				createdEncoder = voicechatApi.createEncoder();
				LocationalAudioChannel createdChannel = voicechatServerApi.createLocationalAudioChannel(
						this.channelId,
						voicechatApi.fromServerLevel(level),
						voicechatApi.createPosition(this.speakerPos.getX() + 0.5D, this.speakerPos.getY() + 0.5D, this.speakerPos.getZ() + 0.5D)
				);
				this.encoder = createdEncoder;
				this.channel = createdChannel;
				updateChannelProperties(level, voicechatApi);
				AudioPlayer createdPlayer = voicechatServerApi.createAudioPlayer(createdChannel, createdEncoder, this::nextFrame);
				createdPlayer.setOnStopped(() -> onPlayerStopped(createdPlayer, createdChannel));
				this.player = createdPlayer;
				createdPlayer.startPlaying();
				return true;
			} catch (RuntimeException exception) {
				if (createdEncoder != null && !createdEncoder.isClosed()) {
					createdEncoder.close();
				}
				this.encoder = null;
				this.channel = null;
				this.player = null;
				throw exception;
			}
		}

		private void onPlayerStopped(AudioPlayer stoppedPlayer, LocationalAudioChannel stoppedChannel) {
			boolean clearedPlayer = false;
			if (this.player == stoppedPlayer) {
				this.player = null;
				clearedPlayer = true;
			}
			if (this.channel == stoppedChannel) {
				this.channel = null;
			}
			if (clearedPlayer) {
				this.encoder = null;
			}
		}

		private void updateChannelProperties(ServerLevel level, VoicechatApi voicechatApi) {
			if (this.channel == null) {
				return;
			}
			this.channel.updateLocation(voicechatApi.createPosition(this.speakerPos.getX() + 0.5D, this.speakerPos.getY() + 0.5D, this.speakerPos.getZ() + 0.5D));
			this.channel.setDistance(SpeakerSystem.audibleDistance(this.volumePercent));
			if (SpeakerSystem.isSpeakerVolumeCategoryRegistered()) {
				this.channel.setCategory(SpeakerSystem.speakerVolumeCategoryId());
			}
		}

		private short[] nextFrame() {
			short[] frame = this.feed.liveFrameAt(System.nanoTime());
			if (frame == null) {
				return SILENCE_FRAME;
			}
			float factor = SpeakerSystem.volumeFactor(this.volumePercent) * MICROPHONE_GAIN_BOOST;
			if (factor <= 0.0F) {
				return SILENCE_FRAME;
			}
			short[] output = new short[AUDIO_FRAME_SAMPLES];
			for (int index = 0; index < output.length; index++) {
				output[index] = SpeakerSystem.softLimitSample(frame[index] * factor);
			}
			ResourceKey<Level> dimension = this.speakerDimension;
			if (dimension != null) {
				offerSpeakerAudio(dimension, this.speakerPos, SpeakerSystem.audibleDistance(this.volumePercent), output);
			}
			return output;
		}

		private void close() {
			this.closed = true;
			AudioPlayer currentPlayer = this.player;
			this.player = null;
			this.encoder = null;
			if (currentPlayer != null && !currentPlayer.isStopped()) {
				currentPlayer.stopPlaying();
			}
			this.channel = null;
		}
	}

	private static final class SharedMicrophoneFeed {
		private final Object lock = new Object();
		private final Map<UUID, SenderVoiceBuffer> senderBuffers = new HashMap<>();
		private final Map<UUID, SenderVoiceBuffer> liveSenderBuffers = new HashMap<>();
		private SenderVoiceBuffer rendererClientBuffer;
		private long rendererClientToServerOffsetNanos = Long.MAX_VALUE;
		private long rendererLastClientFrameNanos = Long.MIN_VALUE;
		private volatile boolean closed;

		private void offerPacket(UUID senderUuid, byte[] opusData, float gain, VoicechatApi voicechatApi) {
			if (this.closed || senderUuid == null || opusData == null || opusData.length == 0 || voicechatApi == null) {
				return;
			}
			synchronized (this.lock) {
				if (this.closed) {
					return;
				}
				long baseSequence = System.nanoTime() / AUDIO_FRAME_NANOS;
				SenderVoiceBuffer buffer = this.senderBuffers.computeIfAbsent(senderUuid, ignored -> new SenderVoiceBuffer(voicechatApi.createDecoder()));
				buffer.offer(opusData, baseSequence, gain);
				pruneExpiredLocked(baseSequence);
			}
		}

		private void offerSpeakerFrame(UUID sourceUuid, short[] samples, float gain) {
			if (this.closed || sourceUuid == null || samples == null || samples.length == 0 || gain <= 0.0F) {
				return;
			}
			synchronized (this.lock) {
				if (this.closed) {
					return;
				}
				long baseSequence = System.nanoTime() / AUDIO_FRAME_NANOS;
				SenderVoiceBuffer buffer = this.senderBuffers.computeIfAbsent(sourceUuid, ignored -> new SenderVoiceBuffer(null));
				buffer.offerFrame(samples, baseSequence, gain);
				pruneExpiredLocked(baseSequence);
			}
		}

		private void offerPcmFrame(short[] samples) {
			long nowNanos = System.nanoTime();
			offerRendererAudioFrame(samples, nowNanos, nowNanos);
		}

		private void offerPcmFrame(short[] samples, long captureNanos) {
			offerRendererAudioFrame(samples, captureNanos, captureNanos);
		}

		private void offerRendererAudioFrame(RendererBotCameraSystem.AudioCaptureFrame frame) {
			if (frame == null) {
				return;
			}
			offerRendererAudioFrame(frame.samples(), frame.receivedAtNanos(), frame.clientFrameNanos());
		}

		private void offerRendererAudioFrame(short[] samples, long receivedAtNanos, long clientFrameNanos) {
			if (this.closed || samples == null || samples.length == 0) {
				return;
			}
			long safeReceivedAtNanos = receivedAtNanos > 0L ? receivedAtNanos : System.nanoTime();
			long safeClientFrameNanos = clientFrameNanos > 0L ? clientFrameNanos : safeReceivedAtNanos;
			synchronized (this.lock) {
				if (this.closed) {
					return;
				}
				long serverFrameNanos = safeClientFrameNanos == safeReceivedAtNanos
						? safeReceivedAtNanos
						: this.mapRendererClientFrameToServerLocked(safeReceivedAtNanos, safeClientFrameNanos);
				long baseSequence = serverFrameNanos / AUDIO_FRAME_NANOS;
				SenderVoiceBuffer buffer = this.senderBuffers.computeIfAbsent(RENDERER_AUDIO_SOURCE_UUID, ignored -> new SenderVoiceBuffer(null));
				buffer.offerFrame(samples, baseSequence, 1.0F);
				pruneExpiredLocked(baseSequence);

				long liveSequence = serverFrameNanos / AUDIO_FRAME_NANOS;
				SenderVoiceBuffer liveBuffer = this.liveSenderBuffers.computeIfAbsent(RENDERER_AUDIO_SOURCE_UUID, ignored -> new SenderVoiceBuffer(null));
				liveBuffer.offerFrame(samples, liveSequence, 1.0F);
				pruneExpiredLocked(this.liveSenderBuffers, liveSequence);

				long clientSequence = safeClientFrameNanos / AUDIO_FRAME_NANOS;
				SenderVoiceBuffer clientBuffer = this.rendererClientBuffer;
				if (clientBuffer == null) {
					clientBuffer = new SenderVoiceBuffer(null);
					this.rendererClientBuffer = clientBuffer;
				}
				clientBuffer.offerFrame(samples, clientSequence, 1.0F);
			}
		}

		private long mapRendererClientFrameToServerLocked(long receivedAtNanos, long clientFrameNanos) {
			long observedOffset = receivedAtNanos - clientFrameNanos;
			boolean clientTimelineJumped = this.rendererLastClientFrameNanos != Long.MIN_VALUE
					&& Math.abs(clientFrameNanos - this.rendererLastClientFrameNanos) > RENDERER_AUDIO_CLOCK_RESET_NANOS
					&& this.rendererClientToServerOffsetNanos != Long.MAX_VALUE
					&& Math.abs(observedOffset - this.rendererClientToServerOffsetNanos) > RENDERER_AUDIO_CLOCK_RESET_NANOS;
			if (this.rendererClientToServerOffsetNanos == Long.MAX_VALUE || clientTimelineJumped || observedOffset < this.rendererClientToServerOffsetNanos) {
				this.rendererClientToServerOffsetNanos = observedOffset;
			}
			this.rendererLastClientFrameNanos = clientFrameNanos;
			long mappedNanos = clientFrameNanos + this.rendererClientToServerOffsetNanos;
			if (mappedNanos < receivedAtNanos - RENDERER_AUDIO_LIVE_MAX_LATE_NANOS) {
				return receivedAtNanos - AUDIO_FRAME_NANOS;
			}
			if (mappedNanos > receivedAtNanos + AUDIO_FRAME_NANOS) {
				return receivedAtNanos;
			}
			return mappedNanos;
		}

		private short[] frameAt(long nowNanos) {
			synchronized (this.lock) {
				if (this.closed || this.senderBuffers.isEmpty()) {
					return null;
				}
				long targetSequence = nowNanos / AUDIO_FRAME_NANOS;
				pruneExpiredLocked(targetSequence);
				return mixBuffersLocked(this.senderBuffers, targetSequence, false);
			}
		}

		private short[] liveFrameAt(long nowNanos) {
			synchronized (this.lock) {
				if (this.closed || this.senderBuffers.isEmpty() && this.liveSenderBuffers.isEmpty()) {
					return null;
				}
				long targetSequence = nowNanos / AUDIO_FRAME_NANOS;
				pruneExpiredLocked(this.senderBuffers, targetSequence, true);
				pruneExpiredLocked(this.liveSenderBuffers, targetSequence);
				short[] voiceFrame = mixBuffersLocked(this.senderBuffers, targetSequence, true);
				short[] rendererFrame = mixBuffersLocked(this.liveSenderBuffers, targetSequence, false);
				if (voiceFrame == null) {
					return rendererFrame;
				}
				if (rendererFrame == null) {
					return voiceFrame;
				}
				short[] output = new short[AUDIO_FRAME_SAMPLES];
				for (int index = 0; index < output.length; index++) {
					output[index] = SpeakerSystem.softLimitSample(voiceFrame[index] + rendererFrame[index]);
				}
				return output;
			}
		}

		private short[] videoFrameAt(long serverNanos, long rendererClientNanos) {
			synchronized (this.lock) {
				if (this.closed || this.senderBuffers.isEmpty() && this.rendererClientBuffer == null) {
					return null;
				}
				long serverSequence = serverNanos / AUDIO_FRAME_NANOS;
				pruneExpiredLocked(this.senderBuffers, serverSequence, true);
				short[] voiceFrame = mixBuffersLocked(this.senderBuffers, serverSequence, true);
				short[] rendererFrame = null;
				SenderVoiceBuffer clientBuffer = this.rendererClientBuffer;
				if (clientBuffer != null) {
					long rendererSequence = rendererClientNanos / AUDIO_FRAME_NANOS;
					if (clientBuffer.isExpired(rendererSequence)) {
						clientBuffer.close();
						this.rendererClientBuffer = null;
					} else {
						rendererFrame = clientBuffer.frameAt(rendererSequence);
					}
				}
				if (voiceFrame == null) {
					return rendererFrame;
				}
				if (rendererFrame == null) {
					return voiceFrame;
				}
				short[] output = new short[AUDIO_FRAME_SAMPLES];
				for (int index = 0; index < output.length; index++) {
					output[index] = SpeakerSystem.softLimitSample(voiceFrame[index] + rendererFrame[index]);
				}
				return output;
			}
		}

		private void pruneExpiredLocked(long targetSequence) {
			pruneExpiredLocked(this.senderBuffers, targetSequence);
		}

		private void pruneExpiredLocked(Map<UUID, SenderVoiceBuffer> buffers, long targetSequence) {
			pruneExpiredLocked(buffers, targetSequence, false);
		}

		private void pruneExpiredLocked(Map<UUID, SenderVoiceBuffer> buffers, long targetSequence, boolean skipRendererAudio) {
			Iterator<Map.Entry<UUID, SenderVoiceBuffer>> iterator = buffers.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<UUID, SenderVoiceBuffer> entry = iterator.next();
				if (skipRendererAudio && RENDERER_AUDIO_SOURCE_UUID.equals(entry.getKey())) {
					continue;
				}
				if (!entry.getValue().isExpired(targetSequence)) {
					continue;
				}
				entry.getValue().close();
				iterator.remove();
			}
		}

		private short[] mixBuffersLocked(Map<UUID, SenderVoiceBuffer> buffers, long targetSequence, boolean skipRendererAudio) {
			float[] mixed = null;
			int contributors = 0;
			for (Map.Entry<UUID, SenderVoiceBuffer> entry : buffers.entrySet()) {
				if (skipRendererAudio && RENDERER_AUDIO_SOURCE_UUID.equals(entry.getKey())) {
					continue;
				}
				short[] frame = entry.getValue().frameAt(targetSequence);
				if (frame == null) {
					continue;
				}
				if (mixed == null) {
					mixed = new float[AUDIO_FRAME_SAMPLES];
				}
				for (int index = 0; index < frame.length; index++) {
					mixed[index] += frame[index];
				}
				contributors++;
			}
			if (mixed == null || contributors <= 0) {
				return null;
			}
			short[] output = new short[AUDIO_FRAME_SAMPLES];
			for (int index = 0; index < output.length; index++) {
				output[index] = SpeakerSystem.softLimitSample(mixed[index]);
			}
			return output;
		}

		private void close() {
			synchronized (this.lock) {
				this.closed = true;
				for (SenderVoiceBuffer buffer : this.senderBuffers.values()) {
					buffer.close();
				}
				this.senderBuffers.clear();
				for (SenderVoiceBuffer buffer : this.liveSenderBuffers.values()) {
					buffer.close();
				}
				this.liveSenderBuffers.clear();
				if (this.rendererClientBuffer != null) {
					this.rendererClientBuffer.close();
					this.rendererClientBuffer = null;
				}
			}
		}
	}

	private static final class SenderVoiceBuffer {
		private final OpusDecoder decoder;
		private final NavigableMap<Long, short[]> frames = new TreeMap<>();
		private long lastSequence = Long.MIN_VALUE;
		private boolean closed;

		private SenderVoiceBuffer(OpusDecoder decoder) {
			this.decoder = decoder;
		}

		private void offer(byte[] opusData, long baseSequence, float gain) {
			if (this.closed || this.decoder == null || this.decoder.isClosed()) {
				return;
			}
			short[] decoded;
			try {
				decoded = this.decoder.decode(opusData);
			} catch (RuntimeException exception) {
				Lg2.LOGGER.debug("Failed to decode microphone packet", exception);
				return;
			}
			if (decoded == null || decoded.length == 0) {
				return;
			}
			long nextSequence = Math.max(baseSequence, this.lastSequence + 1L);
			int frameCount = Math.max(1, (decoded.length + AUDIO_FRAME_SAMPLES - 1) / AUDIO_FRAME_SAMPLES);
			for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
				short[] frame = new short[AUDIO_FRAME_SAMPLES];
				int sourceOffset = frameIndex * AUDIO_FRAME_SAMPLES;
				int copyLength = Math.min(AUDIO_FRAME_SAMPLES, Math.max(0, decoded.length - sourceOffset));
				if (copyLength > 0) {
					copyFrame(decoded, sourceOffset, frame, copyLength, gain);
				}
				this.frames.put(nextSequence++, frame);
			}
			this.lastSequence = nextSequence - 1L;
			while (this.frames.size() > FRAME_BUFFER_CAPACITY) {
				this.frames.pollFirstEntry();
			}
		}

		private void offerFrame(short[] samples, long baseSequence, float gain) {
			if (this.closed || samples == null || samples.length == 0) {
				return;
			}
			long nextSequence = Math.max(baseSequence, this.lastSequence + 1L);
			int frameCount = Math.max(1, (samples.length + AUDIO_FRAME_SAMPLES - 1) / AUDIO_FRAME_SAMPLES);
			for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
				short[] frame = new short[AUDIO_FRAME_SAMPLES];
				int sourceOffset = frameIndex * AUDIO_FRAME_SAMPLES;
				int copyLength = Math.min(AUDIO_FRAME_SAMPLES, Math.max(0, samples.length - sourceOffset));
				if (copyLength > 0) {
					copyFrame(samples, sourceOffset, frame, copyLength, gain);
				}
				this.frames.put(nextSequence++, frame);
			}
			this.lastSequence = nextSequence - 1L;
			while (this.frames.size() > FRAME_BUFFER_CAPACITY) {
				this.frames.pollFirstEntry();
			}
		}

		private static void copyFrame(short[] source, int sourceOffset, short[] target, int copyLength, float gain) {
			float safeGain = Math.max(0.0F, gain);
			if (safeGain == 1.0F) {
				System.arraycopy(source, sourceOffset, target, 0, copyLength);
				return;
			}
			for (int index = 0; index < copyLength; index++) {
				target[index] = SpeakerSystem.softLimitSample(source[sourceOffset + index] * safeGain);
			}
		}

		private short[] frameAt(long targetSequence) {
			Map.Entry<Long, short[]> entry = this.frames.floorEntry(targetSequence);
			if (entry == null) {
				return null;
			}
			return targetSequence - entry.getKey() <= MAX_FRAME_AGE ? entry.getValue() : null;
		}

		private boolean isExpired(long targetSequence) {
			Map.Entry<Long, short[]> latestEntry = this.frames.lastEntry();
			return latestEntry == null || targetSequence - latestEntry.getKey() > SENDER_EXPIRE_AFTER_FRAMES;
		}

		private void close() {
			this.closed = true;
			if (this.decoder != null && !this.decoder.isClosed()) {
				this.decoder.close();
			}
			this.frames.clear();
		}
	}
}
