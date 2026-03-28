package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.block.ModBlocks;
import com.lostglade.block.SpeakerBlock;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class SpeakerSystem {
	private static final int HOTBAR_SLOT_COUNT = 9;
	private static final long REFRESH_INTERVAL_TICKS = 5L;
	private static final int AUDIO_SAMPLE_RATE = 48_000;
	private static final int AUDIO_FRAME_SAMPLES = 960;
	private static final int AUDIO_FRAME_BYTES = AUDIO_FRAME_SAMPLES * 2;
	private static final int AUDIO_QUEUE_CAPACITY = 24;
	private static final long AUDIO_RESYNC_TOLERANCE_MS = 1_500L;
	private static final long PROCESS_SHUTDOWN_TIMEOUT_MS = 200L;
	private static final short[] SILENCE_FRAME = new short[AUDIO_FRAME_SAMPLES];
	private static final Set<SpeakerKey> KNOWN_SPEAKERS = ConcurrentHashMap.newKeySet();
	private static final ConcurrentHashMap<SpeakerKey, SpeakerRuntime> ACTIVE_SPEAKERS = new ConcurrentHashMap<>();
	private static volatile long tickCounter = 0L;

	private SpeakerSystem() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpeakerSystem::tick);
		ServerChunkEvents.CHUNK_LOAD.register(SpeakerSystem::onChunkLoad);
		ServerChunkEvents.CHUNK_UNLOAD.register(SpeakerSystem::onChunkUnload);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> shutdownAll());
	}

	public static void trackSpeaker(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		KNOWN_SPEAKERS.add(new SpeakerKey(level.dimension(), pos.immutable()));
	}

	public static void untrackSpeaker(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		SpeakerKey key = new SpeakerKey(level.dimension(), pos.immutable());
		KNOWN_SPEAKERS.remove(key);
		stopRuntime(key);
	}

	public static void onSpeakerStateChanged(ServerLevel level, BlockPos pos) {
		if (level == null || pos == null) {
			return;
		}
		trackSpeaker(level, pos);
	}

	public static boolean onPlayerHotbarScroll(ServerPlayer player, int previousSlot, int currentSlot) {
		if (player == null || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		HitResult hit = player.pick(6.0D, 0.0F, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = blockHit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.SPEAKER)) {
			return false;
		}
		int delta = normalizeHotbarDelta(previousSlot, currentSlot);
		if (delta == 0 || !SpeakerBlock.adjustVolumeByScroll(level, pos, state, player, delta)) {
			return false;
		}
		return true;
	}

	private static int normalizeHotbarDelta(int previousSlot, int currentSlot) {
		if (previousSlot == currentSlot) {
			return 0;
		}
		int upwardSteps = Math.floorMod(previousSlot - currentSlot, HOTBAR_SLOT_COUNT);
		if (upwardSteps >= 1 && upwardSteps <= 2) {
			return upwardSteps;
		}
		int downwardSteps = Math.floorMod(currentSlot - previousSlot, HOTBAR_SLOT_COUNT);
		if (downwardSteps >= 1 && downwardSteps <= 2) {
			return -downwardSteps;
		}
		return 0;
	}

	private static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		scanChunkForSpeakers(level, chunk);
	}

	private static void onChunkUnload(ServerLevel level, LevelChunk chunk) {
		if (level == null || chunk == null) {
			return;
		}
		int chunkX = chunk.getPos().x;
		int chunkZ = chunk.getPos().z;
		for (SpeakerKey key : new ArrayList<>(KNOWN_SPEAKERS)) {
			if (!key.dimension().equals(level.dimension())) {
				continue;
			}
			if (SectionPos.blockToSectionCoord(key.pos().getX()) != chunkX || SectionPos.blockToSectionCoord(key.pos().getZ()) != chunkZ) {
				continue;
			}
			KNOWN_SPEAKERS.remove(key);
			stopRuntime(key);
		}
	}

	private static void scanChunkForSpeakers(ServerLevel level, LevelChunk chunk) {
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
						if (!section.getBlockState(localX, localY, localZ).is(ModBlocks.SPEAKER)) {
							continue;
						}
						trackSpeaker(level, new BlockPos(chunkMinX + localX, sectionMinY + localY, chunkMinZ + localZ));
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

		Set<SpeakerKey> staleKeys = new HashSet<>(ACTIVE_SPEAKERS.keySet());
		for (SpeakerKey key : new ArrayList<>(KNOWN_SPEAKERS)) {
			boolean keepKnown = refreshSpeaker(server, key);
			if (!keepKnown) {
				KNOWN_SPEAKERS.remove(key);
			}
			staleKeys.remove(key);
		}
		for (SpeakerKey staleKey : staleKeys) {
			stopRuntime(staleKey);
		}
	}

	private static boolean refreshSpeaker(MinecraftServer server, SpeakerKey key) {
		ServerLevel level = server.getLevel(key.dimension());
		if (level == null || !level.hasChunkAt(key.pos())) {
			stopRuntime(key);
			return true;
		}

		BlockState state = level.getBlockState(key.pos());
		if (!state.is(ModBlocks.SPEAKER)) {
			stopRuntime(key);
			return false;
		}

		boolean rawPowered = hasSpeakerPower(level, key.pos());
		List<MonitorScreenSystem.SpeakerAudioSource> connectedSources = rawPowered
				? MonitorScreenSystem.findSpeakerAudioSources(level, key.pos())
				: List.of();
		boolean connectedToScreen = !connectedSources.isEmpty();
		if (SpeakerBlock.isLit(state) != connectedToScreen) {
			state = state.setValue(BlockStateProperties.LIT, connectedToScreen);
			level.setBlock(key.pos(), state, 3);
		}

		if (!rawPowered || SpeakerBlock.readVolumePercent(state) <= 0) {
			stopRuntime(key);
			return true;
		}

		List<MonitorScreenSystem.SpeakerAudioSource> playableSources = connectedSources.stream()
				.filter(source -> source != null && !source.paused() && source.audioStreamUrl() != null && !source.audioStreamUrl().isBlank())
				.toList();
		if (playableSources.isEmpty()) {
			stopRuntime(key);
			return true;
		}

		VoicechatApi voicechatApi = ServerVoicechatIntegration.getApi();
		VoicechatServerApi voicechatServerApi = ServerVoicechatIntegration.getServerApi();
		if (!ServerVoicechatIntegration.isLoaded() || voicechatApi == null || voicechatServerApi == null) {
			stopRuntime(key);
			return true;
		}

		SpeakerRuntime runtime = ACTIVE_SPEAKERS.get(key);
		if (runtime == null) {
			runtime = new SpeakerRuntime(key);
			if (!runtime.start(level, state, playableSources, voicechatApi, voicechatServerApi)) {
				runtime.close();
				return true;
			}
			ACTIVE_SPEAKERS.put(key, runtime);
			return true;
		}

		if (!runtime.update(level, state, playableSources, voicechatApi, voicechatServerApi)) {
			stopRuntime(key);
		}
		return true;
	}

	private static boolean hasSpeakerPower(ServerLevel level, BlockPos pos) {
		return level != null && pos != null
				&& (level.hasNeighborSignal(pos) || level.getBestNeighborSignal(pos) > 0);
	}

	private static void stopRuntime(SpeakerKey key) {
		SpeakerRuntime runtime = ACTIVE_SPEAKERS.remove(key);
		if (runtime != null) {
			runtime.close();
		}
	}

	private static void shutdownAll() {
		for (SpeakerRuntime runtime : ACTIVE_SPEAKERS.values()) {
			runtime.close();
		}
		ACTIVE_SPEAKERS.clear();
	}

	private static String ffmpegBinary() {
		String property = System.getProperty("lg2.ffmpegBin");
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		String environment = System.getenv("FFMPEG_BIN");
		if (environment != null && !environment.isBlank()) {
			return environment.trim();
		}
		return "ffmpeg";
	}

	private static float volumeFactor(int volumePercent) {
		return Math.max(0.0F, Math.min(1.0F, volumePercent / 100.0F));
	}

	private static boolean readFully(InputStream input, byte[] buffer) throws IOException {
		int offset = 0;
		while (offset < buffer.length) {
			int read = input.read(buffer, offset, buffer.length - offset);
			if (read < 0) {
				return false;
			}
			offset += read;
		}
		return true;
	}

	private static short[] decodePcmFrame(byte[] bytes) {
		short[] frame = new short[AUDIO_FRAME_SAMPLES];
		for (int index = 0; index < AUDIO_FRAME_SAMPLES; index++) {
			int byteIndex = index * 2;
			int low = bytes[byteIndex] & 0xFF;
			int high = bytes[byteIndex + 1];
			frame[index] = (short) ((high << 8) | low);
		}
		return frame;
	}

	private record SpeakerKey(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final class SpeakerRuntime {
		private final SpeakerKey key;
		private final UUID channelId;
		private final ConcurrentHashMap<String, SourceFeedRuntime> sourceFeeds;
		private volatile int volumePercent;
		private volatile boolean closed;
		private OpusEncoder encoder;
		private LocationalAudioChannel channel;
		private AudioPlayer player;

		private SpeakerRuntime(SpeakerKey key) {
			this.key = key;
			this.channelId = UUID.randomUUID();
			this.sourceFeeds = new ConcurrentHashMap<>();
			this.volumePercent = 50;
			this.closed = false;
		}

		private boolean start(
				ServerLevel level,
				BlockState state,
				List<MonitorScreenSystem.SpeakerAudioSource> sources,
				VoicechatApi voicechatApi,
				VoicechatServerApi voicechatServerApi
		) {
			this.volumePercent = SpeakerBlock.readVolumePercent(state);
			if (!ensureVoicechatPlayer(level, voicechatApi, voicechatServerApi)) {
				return false;
			}
			synchronizeSources(sources);
			return true;
		}

		private boolean update(
				ServerLevel level,
				BlockState state,
				List<MonitorScreenSystem.SpeakerAudioSource> sources,
				VoicechatApi voicechatApi,
				VoicechatServerApi voicechatServerApi
		) {
			if (this.closed) {
				return false;
			}
			this.volumePercent = SpeakerBlock.readVolumePercent(state);
			if (!ensureVoicechatPlayer(level, voicechatApi, voicechatServerApi)) {
				return false;
			}
			synchronizeSources(sources);
			return true;
		}

		private boolean ensureVoicechatPlayer(ServerLevel level, VoicechatApi voicechatApi, VoicechatServerApi voicechatServerApi) {
			if (this.player != null && !this.player.isStopped()) {
				return true;
			}
			if (voicechatApi == null || voicechatServerApi == null || level == null) {
				return false;
			}
			this.encoder = voicechatApi.createEncoder();
			this.channel = voicechatServerApi.createLocationalAudioChannel(
					this.channelId,
					voicechatApi.fromServerLevel(level),
					voicechatApi.createPosition(this.key.pos().getX() + 0.5D, this.key.pos().getY() + 0.5D, this.key.pos().getZ() + 0.5D)
			);
			this.channel.setDistance((float) Math.max(voicechatApi.getVoiceChatDistance(), voicechatServerApi.getBroadcastRange()));
			this.player = voicechatServerApi.createAudioPlayer(this.channel, this.encoder, this::nextFrame);
			this.player.startPlaying();
			return true;
		}

		private void synchronizeSources(List<MonitorScreenSystem.SpeakerAudioSource> sources) {
			Set<String> keepKeys = new HashSet<>();
			for (MonitorScreenSystem.SpeakerAudioSource source : sources) {
				if (source == null || source.sourceKey() == null || source.sourceKey().isBlank()) {
					continue;
				}
				keepKeys.add(source.sourceKey());
				this.sourceFeeds.compute(source.sourceKey(), (key, existing) -> {
					if (existing == null) {
						SourceFeedRuntime created = new SourceFeedRuntime(this.key, source.sourceKey());
						return created.start(source) ? created : null;
					}
					if (!existing.update(source)) {
						existing.close();
						return null;
					}
					return existing;
				});
			}
			for (Map.Entry<String, SourceFeedRuntime> entry : new ArrayList<>(this.sourceFeeds.entrySet())) {
				if (keepKeys.contains(entry.getKey())) {
					continue;
				}
				SourceFeedRuntime removed = this.sourceFeeds.remove(entry.getKey());
				if (removed != null) {
					removed.close();
				}
			}
		}

		private short[] nextFrame() {
			if (this.sourceFeeds.isEmpty()) {
				return SILENCE_FRAME;
			}
			int[] mixed = null;
			int contributors = 0;
			for (SourceFeedRuntime sourceFeed : this.sourceFeeds.values()) {
				short[] frame = sourceFeed.pollFrame();
				if (frame == null) {
					continue;
				}
				if (mixed == null) {
					mixed = new int[AUDIO_FRAME_SAMPLES];
				}
				for (int index = 0; index < frame.length; index++) {
					mixed[index] += frame[index];
				}
				contributors++;
			}
			if (mixed == null || contributors <= 0) {
				return SILENCE_FRAME;
			}
			float factor = volumeFactor(this.volumePercent);
			if (factor <= 0.0F) {
				return SILENCE_FRAME;
			}
			short[] output = new short[AUDIO_FRAME_SAMPLES];
			for (int index = 0; index < output.length; index++) {
				int averaged = Math.round(mixed[index] / (float) contributors);
				int scaled = Math.round(averaged * factor);
				if (scaled > Short.MAX_VALUE) {
					scaled = Short.MAX_VALUE;
				} else if (scaled < Short.MIN_VALUE) {
					scaled = Short.MIN_VALUE;
				}
				output[index] = (short) scaled;
			}
			return output;
		}

		private void close() {
			this.closed = true;
			for (SourceFeedRuntime sourceFeed : this.sourceFeeds.values()) {
				sourceFeed.close();
			}
			this.sourceFeeds.clear();
			if (this.player != null && !this.player.isStopped()) {
				this.player.stopPlaying();
			}
			this.player = null;
			if (this.encoder != null && !this.encoder.isClosed()) {
				this.encoder.close();
			}
			this.encoder = null;
			this.channel = null;
		}
	}

	private static final class SourceFeedRuntime {
		private final SpeakerKey speakerKey;
		private final String sourceKey;
		private final ArrayBlockingQueue<short[]> frameQueue;
		private volatile boolean closed;
		private Process process;
		private Thread readerThread;
		private String relaySessionId;
		private String audioStreamUrl;
		private boolean liveStream;
		private long processBasePositionMs;
		private long processStartedAtMillis;

		private SourceFeedRuntime(SpeakerKey speakerKey, String sourceKey) {
			this.speakerKey = speakerKey;
			this.sourceKey = sourceKey;
			this.frameQueue = new ArrayBlockingQueue<>(AUDIO_QUEUE_CAPACITY);
			this.closed = false;
		}

		private boolean start(MonitorScreenSystem.SpeakerAudioSource source) {
			restartProcess(source);
			return this.process != null;
		}

		private boolean update(MonitorScreenSystem.SpeakerAudioSource source) {
			if (this.closed) {
				return false;
			}
			if (shouldRestartProcess(source)) {
				restartProcess(source);
			}
			return true;
		}

		private short[] pollFrame() {
			return this.frameQueue.poll();
		}

		private boolean shouldRestartProcess(MonitorScreenSystem.SpeakerAudioSource source) {
			if (source == null) {
				return false;
			}
			if (this.process == null || !this.process.isAlive()) {
				return true;
			}
			if (!Objects.equals(this.relaySessionId, source.relaySessionId())
					|| !Objects.equals(this.audioStreamUrl, source.audioStreamUrl())
					|| this.liveStream != source.liveStream()) {
				return true;
			}
			if (!this.liveStream) {
				long expectedPositionMs = this.processBasePositionMs + Math.max(0L, System.currentTimeMillis() - this.processStartedAtMillis);
				return Math.abs(expectedPositionMs - source.positionMs()) > AUDIO_RESYNC_TOLERANCE_MS;
			}
			return false;
		}

		private void restartProcess(MonitorScreenSystem.SpeakerAudioSource source) {
			stopProcess();
			this.frameQueue.clear();
			this.relaySessionId = source.relaySessionId();
			this.audioStreamUrl = source.audioStreamUrl();
			this.liveStream = source.liveStream();
			this.processBasePositionMs = Math.max(0L, source.positionMs());
			this.processStartedAtMillis = System.currentTimeMillis();

			List<String> command = new ArrayList<>();
			command.add(ffmpegBinary());
			command.add("-hide_banner");
			command.add("-loglevel");
			command.add("error");
			command.add("-nostdin");
			if (!source.liveStream() && source.positionMs() > 0L) {
				command.add("-ss");
				command.add(String.format(java.util.Locale.ROOT, "%.3f", source.positionMs() / 1000.0D));
			}
			command.add("-re");
			command.add("-i");
			command.add(source.audioStreamUrl());
			command.add("-vn");
			command.add("-ac");
			command.add("1");
			command.add("-ar");
			command.add(Integer.toString(AUDIO_SAMPLE_RATE));
			command.add("-f");
			command.add("s16le");
			command.add("-acodec");
			command.add("pcm_s16le");
			command.add("-");

			try {
				this.process = new ProcessBuilder(command)
						.redirectError(ProcessBuilder.Redirect.DISCARD)
						.start();
			} catch (IOException exception) {
				Lg2.LOGGER.warn("Failed to start speaker ffmpeg process for {} source {}", this.speakerKey, this.sourceKey, exception);
				this.process = null;
				return;
			}

			Thread thread = new Thread(this::readLoop, "lg2-speaker-" + this.speakerKey.pos().toShortString() + "-" + Integer.toHexString(this.sourceKey.hashCode()));
			thread.setDaemon(true);
			this.readerThread = thread;
			thread.start();
		}

		private void readLoop() {
			Process currentProcess = this.process;
			if (currentProcess == null || currentProcess.getInputStream() == null) {
				return;
			}
			byte[] buffer = new byte[AUDIO_FRAME_BYTES];
			try (InputStream input = currentProcess.getInputStream()) {
				while (!this.closed && currentProcess == this.process && readFully(input, buffer)) {
					short[] frame = decodePcmFrame(buffer);
					while (!this.frameQueue.offer(frame)) {
						this.frameQueue.poll();
					}
				}
			} catch (IOException ignored) {
			}
		}

		private void stopProcess() {
			Process currentProcess = this.process;
			this.process = null;
			this.readerThread = null;
			if (currentProcess == null) {
				return;
			}
			try {
				currentProcess.destroy();
				if (!currentProcess.waitFor(PROCESS_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
					currentProcess.destroyForcibly();
					currentProcess.waitFor(PROCESS_SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				currentProcess.destroyForcibly();
			} catch (Exception ignored) {
				currentProcess.destroyForcibly();
			}
		}

		private void close() {
			this.closed = true;
			stopProcess();
			this.frameQueue.clear();
		}
	}
}
