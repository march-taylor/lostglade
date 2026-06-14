package com.lostglade.server;

import com.lostglade.Lg2;
import com.lostglade.block.CameraBlock;
import com.lostglade.server.monitor.MonitorApp;
import com.lostglade.server.monitor.MonitorMediaApp;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;

import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
import static com.lostglade.server.MonitorScreenMediaLoadResults.*;
import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenSystem.*;

final class MonitorMaxRuntime {
	private static final String PERSISTED_MAX_ROOT_TAG = "lg2_max";
	private static final String MAX_ACCOUNT_CODE_TAG = "account_code";
	private static final String MAX_AVATAR_URL_TAG = "avatar_url";
	private static final String MAX_AVATAR_LOCAL_MEDIA_TAG = "avatar_local_media";
	private static final String MAX_SELECTED_CAMERA_URL_TAG = "selected_camera_url";
	private static final String MAX_SELECTED_MICROPHONE_KEY_TAG = "selected_microphone_key";
	private static final String MAX_SELECTED_MICROPHONE_INDEX_TAG = "selected_microphone_index";
	private static final String MAX_CAMERA_ENABLED_TAG = "camera_enabled";
	private static final String MAX_MICROPHONE_ENABLED_TAG = "microphone_enabled";
	private static final String MAX_RINGTONE_URL_TAG = "ringtone_url";
	private static final String MAX_RINGTONE_LOCAL_MEDIA_TAG = "ringtone_local_media";
	private static final String MAX_RINGTONE_TITLE_TAG = "ringtone_title";
	private static final String MAX_CONTACT_COUNT_TAG = "contact_count";
	private static final String MAX_CONTACT_PREFIX = "contact_";
	private static final String MAX_INCOMING_FILE_COUNT_TAG = "incoming_file_count";
	private static final String MAX_INCOMING_FILE_PREFIX = "incoming_file_";
	private static final String MAX_INCOMING_FILE_ID_TAG = "id";
	private static final String MAX_INCOMING_FILE_SENDER_TAG = "sender";
	private static final String MAX_INCOMING_FILE_TITLE_TAG = "title";
	private static final String MAX_INCOMING_FILE_SUBTITLE_TAG = "subtitle";
	private static final String MAX_INCOMING_FILE_URL_TAG = "url";
	private static final String MAX_INCOMING_FILE_LOCAL_MEDIA_TAG = "local_media";
	private static final String MAX_INCOMING_FILE_KIND_TAG = "kind";
	private static final String MAX_INCOMING_FILE_CREATED_TAG = "created";
	private static final String MAX_PENDING_ADD_STATUS = "Введите 6 цифр MAX в чат";
	private static final String MAX_RINGTONE_SOURCE_PREFIX = "max:ring:";
	private static final String MAX_RINGTONE_PREVIEW_SOURCE_PREFIX = "max:ring-preview:";
	private static final String MAX_DEFAULT_RINGTONE_URL = "max:default-ringtone";
	private static final String MAX_TRANSFER_URL_PREFIX = "max:file:";
	private static final String MAX_TRANSFER_LOCAL_KEY_PREFIX = "max_transfer_";
	private static final Identifier MAX_NOTIFICATION_SOUND_ID = Identifier.fromNamespaceAndPath(Lg2.MOD_ID, "max_notification");
	private static final SoundEvent MAX_NOTIFICATION_SOUND = SoundEvent.createVariableRangeEvent(MAX_NOTIFICATION_SOUND_ID);
	private static final long MAX_RINGTONE_PREVIEW_TIMELINE_MS = 30_000L;
	private static final long MAX_AVATAR_ANIMATION_RENDER_DELAY_MS = 80L;
	private static final Path DEFAULT_PROJECT_RINGTONE = Path.of(System.getProperty("user.dir"), "server-assets", "max", "default-ringtone.mp3");
	private static final Path DEFAULT_SOURCE_RINGTONE = Path.of("/home/mart/Downloads/Rington_-_na_zvonok_(SkySound.cc).mp3");
	private static final Map<ScreenRuntimeKey, MaxRuntimeState> MAX_STATES = new ConcurrentHashMap<>();
	private static final Map<String, ScreenRuntimeKey> ACCOUNT_INDEX = new ConcurrentHashMap<>();
	private static final Map<UUID, ScreenRuntimeKey> PENDING_CONTACT_CODE_INPUTS = new ConcurrentHashMap<>();
	private static final Map<UUID, MaxCallSession> CALLS_BY_ID = new ConcurrentHashMap<>();
	private static final Map<ScreenRuntimeKey, UUID> CALL_BY_SCREEN = new ConcurrentHashMap<>();

	private MonitorMaxRuntime() {
	}

	static MaxVisualSnapshot captureSnapshot(MinecraftServer server, ScreenComponent component) {
		if (server == null || component == null) {
			return emptySnapshot();
		}
		MaxRuntimeState state = ensureState(server, component);
		if (state == null) {
			return emptySnapshot();
		}
		boolean sanitizedContacts;
		List<LiveCameraReference> cameras = connectedCameraReferences(server, component);
		synchronized (state) {
			sanitizedContacts = sanitizeContactsLocked(state);
			if (sanitizedContacts) {
				state.version++;
			}
			boolean selectedCameraMissing = state.selectedCameraUrl != null
					&& !state.selectedCameraUrl.isBlank()
					&& cameras.stream().map(MonitorScreenLiveSources::liveCameraGalleryUrl).noneMatch(url -> Objects.equals(url, state.selectedCameraUrl));
			if ((state.selectedCameraUrl == null || state.selectedCameraUrl.isBlank() || selectedCameraMissing) && !cameras.isEmpty()) {
				state.selectedCameraUrl = liveCameraGalleryUrl(cameras.get(0));
				state.version++;
				persistState(server, component.runtimeKey(), state);
			}
		}
		if (sanitizedContacts) {
			persistState(server, component.runtimeKey(), state);
		}
		MaxCallVisualSnapshot callSnapshot = captureCallSnapshot(server, component, state, cameras);
		List<MaxContactSnapshot> contacts = captureContactSnapshots(server, state, component.runtimeKey());
		List<MaxAvatarCandidateSnapshot> avatarCandidates;
		List<MaxRingtoneCandidateSnapshot> ringtoneCandidates;
		List<MaxFileShareContactSnapshot> fileShareContacts;
		MaxIncomingFileSnapshot incomingFile;
		synchronized (state) {
			avatarCandidates = state.avatarPickerOpen ? avatarCandidates(component) : List.of();
			ringtoneCandidates = state.ringtonePickerOpen ? ringtoneCandidates(component, state) : List.of();
			fileShareContacts = state.fileSharePickerOpen ? fileShareContacts(server, state, component.runtimeKey()) : List.of();
			incomingFile = state.notificationsOpen ? incomingFileSnapshot(server, state) : null;
			boolean animatedAvatars = maxAnimatedAvatarsVisible(component, state, contacts, callSnapshot, incomingFile);
			if (animatedAvatars) {
				scheduleAvatarAnimationRender(server, component.runtimeKey(), state);
			}
			return new MaxVisualSnapshot(
					state.version,
					state.accountCode,
					currentAvatarFrameLocked(state),
					contacts,
					callSnapshot,
					avatarCandidates,
					ringtoneCandidates,
					fileShareContacts,
					incomingFile,
					state.incomingFiles.size(),
					state.fileShareFiles.size(),
					state.fileShareSelectedContacts.size(),
					fileShareTitleLocked(state),
					state.avatarPickerOpen,
					state.ringtonePickerOpen,
					state.fileSharePickerOpen,
					state.notificationsOpen,
					animatedAvatars,
					state.ringtonePreviewPlaying,
					state.statusText
			);
		}
	}

	static boolean handleTouch(ServerPlayer player, ServerLevel level, ScreenComponent component, UiLayout layout, UiPoint touchPoint) {
		if (player == null || level == null || component == null || layout == null || touchPoint == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return false;
		}
		MaxRuntimeState state = ensureState(server, component);
		if (state == null) {
			return false;
		}
		if (mediaCloseRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			applyTransientComponentViewState(server, level, component, ScreenViewMode.HOME, component.launcherPage());
			return true;
		}
		boolean avatarPickerOpen;
		boolean ringtonePickerOpen;
		boolean fileSharePickerOpen;
		boolean notificationsOpen;
		synchronized (state) {
			avatarPickerOpen = state.avatarPickerOpen;
			ringtonePickerOpen = state.ringtonePickerOpen;
			fileSharePickerOpen = state.fileSharePickerOpen;
			notificationsOpen = state.notificationsOpen;
		}
		if (fileSharePickerOpen) {
			return handleFileSharePickerTouch(server, component, state, layout, touchPoint);
		}
		if (notificationsOpen) {
			return handleNotificationsTouch(server, component, state, layout, touchPoint);
		}
		if (avatarPickerOpen) {
			return handleAvatarPickerTouch(server, component, state, layout, touchPoint);
		}
		if (ringtonePickerOpen) {
			return handleRingtonePickerTouch(server, component, state, layout, touchPoint);
		}

		if (handleCallTouch(player, level, component, state, layout, touchPoint)) {
			return true;
		}

		if (maxProfileAvatarRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.avatarPickerOpen = true;
				state.statusText = "";
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (maxProfileCodeRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			sendCopyCodeMessage(player, state.accountCode);
			return true;
		}
		if (maxRingtonePreviewRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			toggleSelectedRingtonePreview(server, component, state);
			return true;
		}
		if (maxRingtonePickerOpenRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.ringtonePickerOpen = true;
				state.avatarPickerOpen = false;
				state.notificationsOpen = false;
				state.fileSharePickerOpen = false;
				state.statusText = "";
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (maxNotificationsRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.notificationsOpen = true;
				state.avatarPickerOpen = false;
				state.ringtonePickerOpen = false;
				state.fileSharePickerOpen = false;
				state.statusText = state.incomingFiles.isEmpty() ? "Нет уведомлений" : "";
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (maxAddContactRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			PENDING_CONTACT_CODE_INPUTS.put(player.getUUID(), component.runtimeKey());
			synchronized (state) {
				state.statusText = MAX_PENDING_ADD_STATUS;
				state.version++;
			}
			player.displayClientMessage(Component.literal(MAX_PENDING_ADD_STATUS), true);
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		int contactIndex = maxContactIndexAt(layout, state.contacts.size(), touchPoint);
		if (contactIndex >= 0) {
			String contactCode;
			synchronized (state) {
				contactCode = contactIndex < state.contacts.size() ? state.contacts.get(contactIndex) : null;
			}
			if (maxContactDeleteRect(layout, contactIndex).contains(touchPoint.x(), touchPoint.y())) {
				removeContact(server, component.runtimeKey(), state, contactCode);
				return true;
			}
			if (contactCode != null && !contactCode.isBlank()) {
				startCall(server, component.runtimeKey(), contactCode);
			}
			return true;
		}
		return true;
	}

	static boolean handleGlobalTouch(ServerPlayer player, ServerLevel level, ScreenComponent component, UiLayout layout, UiPoint touchPoint) {
		if (player == null || level == null || component == null || layout == null || touchPoint == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (server == null || !hasVisibleCall(component.runtimeKey())) {
			return false;
		}
		MaxRuntimeState state = ensureState(server, component);
		return state != null && handleCallTouch(player, level, component, state, layout, touchPoint);
	}

	private static boolean handleCallTouch(ServerPlayer player, ServerLevel level, ScreenComponent component, MaxRuntimeState state, UiLayout layout, UiPoint touchPoint) {
		if (level == null || component == null || state == null || layout == null || touchPoint == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (server == null) {
			return false;
		}
		MaxCallSession call = currentCall(component.runtimeKey());
		MaxCallPhase phase = callPhase(call, component.runtimeKey());
		if (phase == MaxCallPhase.IDLE) {
			return false;
		}
		if (phase == MaxCallPhase.ACTIVE) {
			boolean cameraPickerOpen;
			boolean contactPickerOpen;
			synchronized (state) {
				cameraPickerOpen = state.cameraPickerOpen;
				contactPickerOpen = state.callContactPickerOpen;
			}
			if (cameraPickerOpen) {
				return handleCallCameraPickerTouch(server, component, state, layout, touchPoint);
			}
			if (contactPickerOpen) {
				return handleCallContactPickerTouch(server, component, state, call, layout, touchPoint);
			}
		}
		if (phase == MaxCallPhase.INCOMING) {
			if (maxIncomingAcceptRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				acceptCall(server, component.runtimeKey());
				return true;
			}
			if (maxIncomingDeclineRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				endCall(server, component.runtimeKey());
				return true;
			}
			return true;
		}
		if (phase == MaxCallPhase.OUTGOING) {
			if (maxOutgoingCancelRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				endCall(server, component.runtimeKey());
				return true;
			}
			return true;
		}
		boolean menuVisible = isCallMenuVisible(state);
		boolean focused = callFocused(state);
		int microphoneCount = MicrophoneSystem.connectedMicrophoneCount(server, component.runtimeKey());
		boolean multiMicrophone = microphoneCount > 1;
		if (menuVisible) {
			if (maxCallLeaveRect(layout, multiMicrophone, focused).contains(touchPoint.x(), touchPoint.y())) {
				endCall(server, component.runtimeKey());
				return true;
			}
			if (maxCallCameraToggleRect(layout, multiMicrophone, focused).contains(touchPoint.x(), touchPoint.y())) {
				toggleCamera(server, component.runtimeKey());
				return true;
			}
			if (maxCallCameraSelectRect(layout, multiMicrophone, focused).contains(touchPoint.x(), touchPoint.y())) {
				openCameraPicker(server, component.runtimeKey());
				return true;
			}
			if (maxCallInviteRect(layout, multiMicrophone, focused).contains(touchPoint.x(), touchPoint.y())) {
				openCallContactPicker(server, component.runtimeKey());
				return true;
			}
			if (maxCallMicrophoneToggleRect(layout, multiMicrophone, focused).contains(touchPoint.x(), touchPoint.y())) {
				toggleMicrophone(server, component.runtimeKey());
				return true;
			}
			if (multiMicrophone && maxCallMicrophoneSelectRect(layout, focused).contains(touchPoint.x(), touchPoint.y())) {
				openCameraPicker(server, component.runtimeKey());
				return true;
			}
		}
		boolean focusedSelf = callFocusSelf(state);
		boolean focusedPeer = callFocusPeer(state);
		if (focusedSelf || focusedPeer) {
			if (menuVisible && maxCallExitFullscreenRect(layout, multiMicrophone, true).contains(touchPoint.x(), touchPoint.y())) {
				clearCallFocus(server, component.runtimeKey());
				return true;
			}
			if (menuVisible) {
				List<ScreenRuntimeKey> miniParticipants = callMiniParticipantKeys(component.runtimeKey(), state, call);
				int miniIndex = maxCallMiniParticipantIndexAt(layout, miniParticipants.size(), touchPoint);
				if (miniIndex >= 0 && miniIndex < miniParticipants.size()) {
					ScreenRuntimeKey selected = miniParticipants.get(miniIndex);
					if (Objects.equals(selected, component.runtimeKey())) {
						focusCallParticipant(server, component.runtimeKey(), true);
					} else {
						focusCallParticipant(server, component.runtimeKey(), selected);
					}
					return true;
				}
			}
			if (maxCallFocusedTileRect(layout).contains(touchPoint.x(), touchPoint.y())) {
				toggleCallMenu(server, component.runtimeKey());
				return true;
			}
			return true;
		}
		List<ScreenRuntimeKey> participants = call.participants();
		int participantIndex = maxCallParticipantIndexAt(layout, participants.size(), menuVisible, touchPoint);
		if (participantIndex >= 0 && participantIndex < participants.size()) {
			ScreenRuntimeKey selected = participants.get(participantIndex);
			if (Objects.equals(selected, component.runtimeKey())) {
				focusCallParticipant(server, component.runtimeKey(), true);
			} else {
				focusCallParticipant(server, component.runtimeKey(), selected);
			}
			return true;
		}
		toggleCallMenu(server, component.runtimeKey());
		return true;
	}

	static boolean onAllowChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
		if (sender == null || message == null) {
			return true;
		}
		ScreenRuntimeKey key = PENDING_CONTACT_CODE_INPUTS.remove(sender.getUUID());
		if (key == null) {
			return true;
		}
		MinecraftServer server = sender.level().getServer();
		if (server == null) {
			return false;
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		MaxRuntimeState state = component != null ? ensureState(server, component) : MAX_STATES.get(key);
		String rawCode = message.signedContent() != null ? message.signedContent().trim() : "";
		String code = normalizeAccountCode(rawCode);
		if (state == null || code.isBlank()) {
			sender.displayClientMessage(Component.literal("MAX: код не распознан"), true);
			return false;
		}
		String ownCode;
		boolean added;
		synchronized (state) {
			ownCode = state.accountCode;
			if (Objects.equals(code, ownCode)) {
				state.statusText = "Это код этого экрана";
				added = false;
			} else if (!state.contacts.contains(code)) {
				state.contacts.add(code);
				state.statusText = "Контакт добавлен";
				added = true;
			} else {
				state.statusText = "Контакт уже добавлен";
				added = false;
			}
			state.version++;
		}
		if (added) {
			persistState(server, key, state);
			addReverseContact(server, code, ownCode);
		}
		sender.displayClientMessage(Component.literal(state.statusText), true);
		requestRuntimeRender(server, key);
		return false;
	}

	private static void addReverseContact(MinecraftServer server, String ownerCode, String contactCode) {
		if (server == null || ownerCode == null || contactCode == null || ownerCode.isBlank() || contactCode.isBlank()) {
			return;
		}
		ScreenRuntimeKey ownerKey = ACCOUNT_INDEX.get(ownerCode);
		if (ownerKey == null) {
			return;
		}
		ScreenComponent ownerComponent = resolveScreenComponent(server, ownerKey);
		if (ownerComponent == null) {
			return;
		}
		MaxRuntimeState ownerState = ensureState(server, ownerComponent);
		if (ownerState == null) {
			return;
		}
		boolean added;
		synchronized (ownerState) {
			added = !Objects.equals(contactCode, ownerState.accountCode) && !ownerState.contacts.contains(contactCode);
			if (added) {
				ownerState.contacts.add(contactCode);
				ownerState.version++;
			}
		}
		if (added) {
			persistState(server, ownerKey, ownerState);
			requestRuntimeRender(server, ownerKey);
		}
	}

	static List<SpeakerAudioSource> findSpeakerAudioSources(MinecraftServer server, Collection<ScreenComponent> components) {
		if (server == null || components == null || components.isEmpty()) {
			return List.of();
		}
		List<SpeakerAudioSource> sources = new ArrayList<>();
		for (ScreenComponent component : components) {
			if (component == null || component.runtimeKey() == null || !component.powered()) {
				continue;
			}
			MaxRuntimeState state = MAX_STATES.get(component.runtimeKey());
			if (state != null) {
				SpeakerAudioSource preview = previewRingtoneSource(component.runtimeKey(), state);
				if (preview != null) {
					sources.add(preview);
				}
			}
			MaxCallSession call = currentCall(component.runtimeKey());
			if (call == null || !call.isRinging(component.runtimeKey())) {
				continue;
			}
			String ringtone = ringtoneSourceForScreen(call.inviterFor(component.runtimeKey()));
			if (ringtone == null || ringtone.isBlank()) {
				continue;
			}
			String sourceKey = MAX_RINGTONE_SOURCE_PREFIX + call.id + ":" + componentGroupId(component.runtimeKey());
			sources.add(new SpeakerAudioSource(
					sourceKey,
					sourceKey,
					ringtone,
					0L,
					call.createdAtMillis,
					false,
					false,
					false,
					false,
					true
			));
		}
		return sources.isEmpty() ? List.of() : List.copyOf(sources);
	}

	static boolean hasVisibleCall(ScreenRuntimeKey key) {
		return key != null && currentCall(key) != null;
	}

	static void onDeviceNetworkChanged(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null || currentCall(key) == null) {
			return;
		}
		MicrophoneSystem.onMaxCallStateChanged(server);
		requestRuntimeRender(server, key);
		requestPeerRender(server, key);
	}

	static boolean hasCallOverlay(MaxVisualSnapshot snapshot) {
		return snapshot != null && snapshot.call() != null && snapshot.call().phase() != MaxCallPhase.IDLE;
	}

	static boolean beginGalleryFileShare(MinecraftServer server, ScreenComponent component, List<GalleryItem> items) {
		if (server == null || component == null || component.runtimeKey() == null || items == null || items.isEmpty()) {
			return false;
		}
		MaxRuntimeState state = ensureState(server, component);
		if (state == null) {
			return false;
		}
		List<MaxSharedGalleryFile> files = new ArrayList<>();
		for (GalleryItem item : items) {
			MaxSharedGalleryFile file = sharedGalleryFile(item);
			if (file != null) {
				files.add(file);
			}
		}
		synchronized (state) {
			if (files.isEmpty()) {
				state.statusText = "Эти файлы нельзя отправить";
				state.version++;
				requestRuntimeRender(server, component.runtimeKey());
				return false;
			}
			state.fileShareFiles.clear();
			state.fileShareFiles.addAll(files);
			state.fileShareSelectedContacts.clear();
			state.fileSharePickerOpen = true;
			state.notificationsOpen = false;
			state.avatarPickerOpen = false;
			state.ringtonePickerOpen = false;
			state.ringtonePreviewPlaying = false;
			state.statusText = files.size() == 1 ? "Выбери получателя" : "Выбери получателей";
			state.version++;
		}
		refreshConnectedSpeakersNow(server, component);
		requestRuntimeRender(server, component.runtimeKey());
		return true;
	}

	private static MaxSharedGalleryFile sharedGalleryFile(GalleryItem item) {
		if (item == null || effectiveGalleryItemKind(item) == GalleryItemKind.LIVE_CAMERA) {
			return null;
		}
		String localMediaKey = item.localMediaKey() == null ? "" : item.localMediaKey().trim();
		String url = item.url() == null ? "" : item.url().trim();
		if (url.isBlank() && localMediaKey.isBlank()) {
			return null;
		}
		GalleryItemKind kind = effectiveGalleryItemKind(item);
		String title = item.title() == null || item.title().isBlank() ? defaultSharedFileTitle(kind) : item.title().trim();
		String subtitle = item.subtitle() == null ? "" : item.subtitle().trim();
		if (url.isBlank()) {
			url = MAX_TRANSFER_URL_PREFIX + localMediaKey;
		}
		return new MaxSharedGalleryFile(title, subtitle, url, localMediaKey, kind != null ? kind : GalleryItemKind.MEDIA);
	}

	private static String defaultSharedFileTitle(GalleryItemKind kind) {
		return switch (kind != null ? kind : GalleryItemKind.MEDIA) {
			case AUDIO -> "Аудиофайл";
			case VIDEO -> "Видео";
			case YOUTUBE -> "YouTube";
			case LIVE_CAMERA -> "Камера";
			case MEDIA -> "Файл";
		};
	}

	private static String fileShareTitleLocked(MaxRuntimeState state) {
		if (state == null || state.fileShareFiles.isEmpty()) {
			return "";
		}
		if (state.fileShareFiles.size() == 1) {
			MaxSharedGalleryFile file = state.fileShareFiles.get(0);
			return file != null && file.title() != null ? file.title() : "";
		}
		return "Файлов: " + state.fileShareFiles.size();
	}

	static void drawCallOverlay(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot snapshot) {
		if (!hasCallOverlay(snapshot)) {
			return;
		}
		drawMaxCallScreen(graphics, layout, snapshot);
	}

	static List<MicrophoneSystem.ScreenMicrophoneCallRoute> collectMicrophoneCallRoutes(MinecraftServer server) {
		if (server == null || CALLS_BY_ID.isEmpty()) {
			return List.of();
		}
		List<MicrophoneSystem.ScreenMicrophoneCallRoute> routes = new ArrayList<>();
		for (MaxCallSession call : CALLS_BY_ID.values()) {
			if (call == null || !call.accepted) {
				continue;
			}
			List<ScreenRuntimeKey> acceptedParticipants = call.acceptedParticipants();
			for (ScreenRuntimeKey sourceKey : acceptedParticipants) {
				ScreenComponent source = resolveScreenComponent(server, sourceKey);
				if (source == null || !source.powered() || !isMicrophoneEnabled(sourceKey)) {
					continue;
				}
				for (ScreenRuntimeKey targetKey : acceptedParticipants) {
					if (Objects.equals(sourceKey, targetKey)) {
						continue;
					}
					ScreenComponent target = resolveScreenComponent(server, targetKey);
					if (target == null || !target.powered()) {
						continue;
					}
					routes.add(new MicrophoneSystem.ScreenMicrophoneCallRoute(
							"max:" + call.id + ":" + componentGroupId(sourceKey) + "-to-" + componentGroupId(targetKey),
							sourceKey,
							targetKey,
							selectedMicrophoneIndex(server, sourceKey)
					));
				}
			}
		}
		return routes.isEmpty() ? List.of() : List.copyOf(routes);
	}

	private static boolean isMicrophoneEnabled(ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return true;
		}
		synchronized (state) {
			return state.microphoneEnabled;
		}
	}

	private static int selectedMicrophoneIndex(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return -1;
		}
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return -1;
		}
		List<MicrophoneSystem.ScreenMicrophoneDevice> microphones = MicrophoneSystem.connectedMicrophoneDevices(server, key);
		synchronized (state) {
			return normalizeSelectedMicrophoneLocked(state, microphones);
		}
	}

	static void closeRuntime(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		endCall(server, key);
		RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(key));
		RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(key));
		MaxRuntimeState removed = MAX_STATES.remove(key);
		if (removed != null && removed.accountCode != null) {
			ACCOUNT_INDEX.remove(removed.accountCode, key);
		}
		PENDING_CONTACT_CODE_INPUTS.entrySet().removeIf(entry -> key.equals(entry.getValue()));
	}

	static void clearRuntime() {
		for (ScreenRuntimeKey key : new ArrayList<>(MAX_STATES.keySet())) {
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(key));
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(key));
		}
		MAX_STATES.clear();
		ACCOUNT_INDEX.clear();
		PENDING_CONTACT_CODE_INPUTS.clear();
		CALLS_BY_ID.clear();
		CALL_BY_SCREEN.clear();
	}

	static void drawMaxScreen(Graphics2D graphics, UiLayout layout, MonitorApp app, MaxVisualSnapshot snapshot) {
		if (graphics == null || layout == null) {
			return;
		}
		MaxVisualSnapshot state = snapshot != null ? snapshot : emptySnapshot();
		UiRect canvas = mediaCanvasRect(layout);
		drawMaxAtmosphere(graphics, canvas, layout);
		drawMediaCloseButton(graphics, mediaCloseRect(layout), layout, MediaButtonSegment.SINGLE);
		if (state.fileSharePickerOpen()) {
			drawMaxFileSharePicker(graphics, layout, state);
			return;
		}
		if (state.notificationsOpen()) {
			drawMaxNotificationsScreen(graphics, layout, state);
			return;
		}
		if (state.avatarPickerOpen()) {
			drawMaxAvatarPicker(graphics, layout, state);
			return;
		}
		if (state.ringtonePickerOpen()) {
			drawMaxRingtonePicker(graphics, layout, state);
			return;
		}
		if (state.call() != null && state.call().phase() != MaxCallPhase.IDLE) {
			drawMaxCallScreen(graphics, layout, state);
			return;
		}
		drawMaxFeedScreen(graphics, layout, app, state);
	}

	private static MaxVisualSnapshot emptySnapshot() {
		return new MaxVisualSnapshot(0L, "MAX-000000", null, List.of(), null, List.of(), List.of(), List.of(), null, 0, 0, 0, "", false, false, false, false, false, false, "");
	}

	private static MaxRuntimeState ensureState(MinecraftServer server, ScreenComponent component) {
		if (server == null || component == null || component.runtimeKey() == null) {
			return null;
		}
		MaxRuntimeState state = MAX_STATES.computeIfAbsent(component.runtimeKey(), ignored -> new MaxRuntimeState());
		synchronized (state) {
			if (!state.hydrated) {
				PersistedMaxState persisted = resolvePersistedMaxState(component);
				state.accountCode = persisted != null && !persisted.accountCode().isBlank()
						? normalizeAccountCode(persisted.accountCode())
						: generateAccountCode();
				state.avatarUrl = persisted != null ? persisted.avatarUrl() : "";
				state.avatarLocalMediaKey = persisted != null ? persisted.avatarLocalMediaKey() : "";
				state.selectedCameraUrl = persisted != null ? persisted.selectedCameraUrl() : "";
				state.selectedMicrophoneKey = persisted != null ? persisted.selectedMicrophoneKey() : "";
				state.selectedMicrophoneIndex = persisted != null ? persisted.selectedMicrophoneIndex() : -1;
				state.cameraEnabled = persisted == null || persisted.cameraEnabled();
				state.microphoneEnabled = persisted == null || persisted.microphoneEnabled();
				state.ringtoneUrl = persisted != null ? persisted.ringtoneUrl() : "";
				state.ringtoneLocalMediaKey = persisted != null ? persisted.ringtoneLocalMediaKey() : "";
				state.ringtoneTitle = persisted != null ? persisted.ringtoneTitle() : "";
				state.incomingFiles.clear();
				if (persisted != null) {
					state.incomingFiles.addAll(persisted.incomingFiles());
				}
				state.contacts.clear();
				if (persisted != null) {
					for (String contact : persisted.contacts()) {
						String normalized = normalizeAccountCode(contact);
						if (!normalized.isBlank() && !Objects.equals(normalized, state.accountCode) && !state.contacts.contains(normalized)) {
							state.contacts.add(normalized);
						}
					}
				}
				state.avatarMedia = loadAvatarMedia(component, state.avatarUrl, state.avatarLocalMediaKey);
				state.avatarFrame = avatarFrame(state.avatarMedia, 0, null);
				state.avatarAnimationStartedAtMillis = System.currentTimeMillis();
				state.hydrated = true;
				state.version++;
				ACCOUNT_INDEX.put(state.accountCode, component.runtimeKey());
				persistState(server, component.runtimeKey(), state);
			}
		}
		return state;
	}

	private static PersistedMaxState resolvePersistedMaxState(ScreenComponent component) {
		if (component == null || component.frameCoords().isEmpty()) {
			return null;
		}
		List<Map.Entry<ItemFrame, TileCoord>> frames = new ArrayList<>(component.frameCoords().entrySet());
		frames.sort(Comparator
				.comparingInt((Map.Entry<ItemFrame, TileCoord> entry) -> entry.getValue().y())
				.thenComparingInt(entry -> entry.getValue().x()));
		for (Map.Entry<ItemFrame, TileCoord> entry : frames) {
			PersistedMaxState state = readPersistedMaxState(entry.getKey().getItem());
			if (state != null && !state.accountCode().isBlank()) {
				return state;
			}
		}
		return null;
	}

	private static PersistedMaxState readPersistedMaxState(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null) {
			return null;
		}
		CompoundTag root = customData.copyTag();
		if (!root.contains(PERSISTED_MAX_ROOT_TAG)) {
			return null;
		}
		CompoundTag maxTag = root.getCompoundOrEmpty(PERSISTED_MAX_ROOT_TAG);
		String code = normalizeAccountCode(maxTag.getStringOr(MAX_ACCOUNT_CODE_TAG, ""));
		if (code.isBlank()) {
			return null;
		}
		int contactCount = Math.max(0, maxTag.getIntOr(MAX_CONTACT_COUNT_TAG, 0));
		List<String> contacts = new ArrayList<>(contactCount);
		for (int index = 0; index < contactCount; index++) {
			String contact = normalizeAccountCode(maxTag.getStringOr(MAX_CONTACT_PREFIX + index, ""));
			if (!contact.isBlank() && !contacts.contains(contact)) {
				contacts.add(contact);
			}
		}
		int incomingFileCount = Math.max(0, maxTag.getIntOr(MAX_INCOMING_FILE_COUNT_TAG, 0));
		List<MaxIncomingFile> incomingFiles = new ArrayList<>(incomingFileCount);
		for (int index = 0; index < incomingFileCount; index++) {
			CompoundTag fileTag = maxTag.getCompoundOrEmpty(MAX_INCOMING_FILE_PREFIX + index);
			String id = fileTag.getStringOr(MAX_INCOMING_FILE_ID_TAG, "");
			String sender = normalizeAccountCode(fileTag.getStringOr(MAX_INCOMING_FILE_SENDER_TAG, ""));
			String title = fileTag.getStringOr(MAX_INCOMING_FILE_TITLE_TAG, "");
			String subtitle = fileTag.getStringOr(MAX_INCOMING_FILE_SUBTITLE_TAG, "");
			String url = fileTag.getStringOr(MAX_INCOMING_FILE_URL_TAG, "");
			String localMediaKey = fileTag.getStringOr(MAX_INCOMING_FILE_LOCAL_MEDIA_TAG, "");
			GalleryItemKind kind = effectiveGalleryItemKind(url, localMediaKey, GalleryItemKind.fromPersisted(fileTag.getStringOr(MAX_INCOMING_FILE_KIND_TAG, ""), url));
			long createdAtMillis = fileTag.getLongOr(MAX_INCOMING_FILE_CREATED_TAG, 0L);
			if (!sender.isBlank() && (!url.isBlank() || !localMediaKey.isBlank())) {
				incomingFiles.add(new MaxIncomingFile(
						id == null || id.isBlank() ? UUID.randomUUID().toString() : id,
						sender,
						title,
						subtitle,
						url,
						localMediaKey,
						kind,
						createdAtMillis > 0L ? createdAtMillis : System.currentTimeMillis()
				));
			}
		}
		return new PersistedMaxState(
				code,
				maxTag.getStringOr(MAX_AVATAR_URL_TAG, ""),
				maxTag.getStringOr(MAX_AVATAR_LOCAL_MEDIA_TAG, ""),
				contacts,
				maxTag.getStringOr(MAX_SELECTED_CAMERA_URL_TAG, ""),
				maxTag.getStringOr(MAX_SELECTED_MICROPHONE_KEY_TAG, ""),
				maxTag.getIntOr(MAX_SELECTED_MICROPHONE_INDEX_TAG, -1),
				maxTag.getBooleanOr(MAX_CAMERA_ENABLED_TAG, true),
				maxTag.getBooleanOr(MAX_MICROPHONE_ENABLED_TAG, true),
				maxTag.getStringOr(MAX_RINGTONE_URL_TAG, ""),
				maxTag.getStringOr(MAX_RINGTONE_LOCAL_MEDIA_TAG, ""),
				maxTag.getStringOr(MAX_RINGTONE_TITLE_TAG, ""),
				List.copyOf(incomingFiles)
		);
	}

	private static void persistState(MinecraftServer server, ScreenRuntimeKey key, MaxRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component == null) {
			return;
		}
		PersistedMaxState snapshot;
		synchronized (state) {
			snapshot = new PersistedMaxState(
					state.accountCode,
					state.avatarUrl,
					state.avatarLocalMediaKey,
					List.copyOf(state.contacts),
					state.selectedCameraUrl,
					state.selectedMicrophoneKey,
					state.selectedMicrophoneIndex,
					state.cameraEnabled,
					state.microphoneEnabled,
					state.ringtoneUrl,
					state.ringtoneLocalMediaKey,
					state.ringtoneTitle,
					List.copyOf(state.incomingFiles)
			);
		}
		for (ItemFrame frame : component.frameCoords().keySet()) {
			if (frame == null || frame.getItem().isEmpty()) {
				continue;
			}
			ItemStack updated = frame.getItem().copy();
			writePersistedMaxState(updated, snapshot);
			frame.setItem(updated, false);
		}
	}

	private static void writePersistedMaxState(ItemStack stack, PersistedMaxState state) {
		if (stack == null || stack.isEmpty() || state == null || state.accountCode().isBlank()) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag maxTag = new CompoundTag();
			maxTag.putString(MAX_ACCOUNT_CODE_TAG, state.accountCode());
			if (state.avatarUrl() != null && !state.avatarUrl().isBlank()) {
				maxTag.putString(MAX_AVATAR_URL_TAG, state.avatarUrl());
			}
			if (state.avatarLocalMediaKey() != null && !state.avatarLocalMediaKey().isBlank()) {
				maxTag.putString(MAX_AVATAR_LOCAL_MEDIA_TAG, state.avatarLocalMediaKey());
			}
			if (state.selectedCameraUrl() != null && !state.selectedCameraUrl().isBlank()) {
				maxTag.putString(MAX_SELECTED_CAMERA_URL_TAG, state.selectedCameraUrl());
			}
			if (state.selectedMicrophoneKey() != null && !state.selectedMicrophoneKey().isBlank()) {
				maxTag.putString(MAX_SELECTED_MICROPHONE_KEY_TAG, state.selectedMicrophoneKey());
			}
			maxTag.putInt(MAX_SELECTED_MICROPHONE_INDEX_TAG, state.selectedMicrophoneIndex());
			maxTag.putBoolean(MAX_CAMERA_ENABLED_TAG, state.cameraEnabled());
			maxTag.putBoolean(MAX_MICROPHONE_ENABLED_TAG, state.microphoneEnabled());
			if (state.ringtoneUrl() != null && !state.ringtoneUrl().isBlank()) {
				maxTag.putString(MAX_RINGTONE_URL_TAG, state.ringtoneUrl());
			}
			if (state.ringtoneLocalMediaKey() != null && !state.ringtoneLocalMediaKey().isBlank()) {
				maxTag.putString(MAX_RINGTONE_LOCAL_MEDIA_TAG, state.ringtoneLocalMediaKey());
			}
			if (state.ringtoneTitle() != null && !state.ringtoneTitle().isBlank()) {
				maxTag.putString(MAX_RINGTONE_TITLE_TAG, state.ringtoneTitle());
			}
			List<String> contacts = state.contacts() != null ? state.contacts() : List.of();
			maxTag.putInt(MAX_CONTACT_COUNT_TAG, contacts.size());
			for (int index = 0; index < contacts.size(); index++) {
				maxTag.putString(MAX_CONTACT_PREFIX + index, contacts.get(index));
			}
			List<MaxIncomingFile> incomingFiles = state.incomingFiles() != null ? state.incomingFiles() : List.of();
			maxTag.putInt(MAX_INCOMING_FILE_COUNT_TAG, incomingFiles.size());
			for (int index = 0; index < incomingFiles.size(); index++) {
				MaxIncomingFile incoming = incomingFiles.get(index);
				if (incoming == null) {
					continue;
				}
				CompoundTag fileTag = new CompoundTag();
				fileTag.putString(MAX_INCOMING_FILE_ID_TAG, incoming.id());
				fileTag.putString(MAX_INCOMING_FILE_SENDER_TAG, incoming.senderCode());
				fileTag.putString(MAX_INCOMING_FILE_TITLE_TAG, incoming.title());
				fileTag.putString(MAX_INCOMING_FILE_SUBTITLE_TAG, incoming.subtitle());
				fileTag.putString(MAX_INCOMING_FILE_URL_TAG, incoming.url());
				fileTag.putString(MAX_INCOMING_FILE_LOCAL_MEDIA_TAG, incoming.localMediaKey());
				fileTag.putString(MAX_INCOMING_FILE_KIND_TAG, (incoming.kind() != null ? incoming.kind() : GalleryItemKind.MEDIA).persistedName());
				fileTag.putLong(MAX_INCOMING_FILE_CREATED_TAG, incoming.createdAtMillis());
				maxTag.put(MAX_INCOMING_FILE_PREFIX + index, fileTag);
			}
			tag.put(PERSISTED_MAX_ROOT_TAG, maxTag);
		});
	}

	private static MaxCallVisualSnapshot captureCallSnapshot(
			MinecraftServer server,
			ScreenComponent component,
			MaxRuntimeState state,
			List<LiveCameraReference> cameras
	) {
		MaxCallSession call = currentCall(component.runtimeKey());
		MaxCallPhase phase = callPhase(call, component.runtimeKey());
		if (phase == MaxCallPhase.IDLE) {
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(component.runtimeKey()));
			return null;
		}
		ScreenRuntimeKey peerKey = call.peer(component.runtimeKey());
		MaxRuntimeState peerState = peerKey != null ? MAX_STATES.get(peerKey) : null;
		BufferedImage peerAvatar;
		boolean peerAvatarAnimated;
		String peerCode;
		boolean cameraEnabled;
		boolean microphoneEnabled;
		boolean callMenuOpen;
		boolean cameraPickerOpen;
		boolean contactPickerOpen;
		boolean focusSelf;
		boolean focusPeer;
		ScreenRuntimeKey focusedPeerKey;
		String selectedMicrophoneKey;
		int selectedMicrophoneIndex;
		int cameraScroll;
		int microphoneScroll;
		BufferedImage remoteFrame;
		synchronized (state) {
			cameraEnabled = state.cameraEnabled;
			microphoneEnabled = state.microphoneEnabled;
			callMenuOpen = state.callMenuOpen;
			cameraPickerOpen = state.cameraPickerOpen;
			contactPickerOpen = state.callContactPickerOpen;
			focusSelf = state.focusSelf;
			focusPeer = state.focusPeer;
			focusedPeerKey = state.focusedPeerKey;
			selectedMicrophoneKey = state.selectedMicrophoneKey;
			selectedMicrophoneIndex = state.selectedMicrophoneIndex;
			cameraScroll = state.cameraPickerScroll;
			microphoneScroll = state.microphonePickerScroll;
			remoteFrame = state.remoteFrame;
		}
		if (focusPeer && focusedPeerKey != null && call.isParticipant(focusedPeerKey) && !Objects.equals(focusedPeerKey, component.runtimeKey())) {
			peerKey = focusedPeerKey;
			peerState = MAX_STATES.get(peerKey);
		}
		if (peerState != null) {
			synchronized (peerState) {
				peerAvatar = currentAvatarFrameLocked(peerState);
				peerAvatarAnimated = avatarAnimatedLocked(peerState);
				peerCode = peerState.accountCode;
			}
		} else {
			peerAvatar = null;
			peerAvatarAnimated = false;
			peerCode = call.peerCode(component.runtimeKey());
		}
		List<MaxCameraOptionSnapshot> cameraOptions = cameraOptions(server, state, cameras);
		int connectedCameraCount = connectedCameraCount(cameras);
		int connectedDroneCount = connectedDroneCount(cameras);
		int selectedCameraIndex = selectedCameraIndex(state, cameraOptions);
		BufferedImage localPreview = null;
		if (cameraEnabled && selectedCameraIndex >= 0 && selectedCameraIndex < cameraOptions.size()) {
			refreshLocalVideo(server, component, state);
			synchronized (state) {
				localPreview = state.localFrame;
			}
		} else {
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(component.runtimeKey()));
			clearLocalFrame(component.runtimeKey());
		}
		List<MicrophoneSystem.ScreenMicrophoneDevice> microphones = MicrophoneSystem.connectedMicrophoneDevices(server, component.runtimeKey());
		int microphoneCount = microphones.size();
		List<MaxMicrophoneOptionSnapshot> microphoneOptions;
		boolean microphoneSelectionChanged = false;
		synchronized (state) {
			int normalizedMicrophoneIndex = normalizeSelectedMicrophoneLocked(state, microphones);
			microphoneSelectionChanged = normalizedMicrophoneIndex != selectedMicrophoneIndex
					|| !Objects.equals(selectedMicrophoneKey, state.selectedMicrophoneKey);
			selectedMicrophoneIndex = normalizedMicrophoneIndex;
			UiLayout pickerLayout = createUiLayout(component.width(), component.height());
			normalizeCallDevicePickerScrollLocked(state, pickerLayout, cameraOptions.size(), microphoneCount);
			cameraScroll = state.cameraPickerScroll;
			microphoneScroll = state.microphonePickerScroll;
			microphoneOptions = microphoneOptionsLocked(state, microphones);
		}
		if (microphoneSelectionChanged) {
			persistState(server, component.runtimeKey(), state);
		}
		if (phase == MaxCallPhase.ACTIVE && peerState != null) {
			refreshRemoteVideo(server, component, peerState);
		} else {
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(component.runtimeKey()));
		}
		List<MaxCallParticipantSnapshot> participants = captureCallParticipants(component.runtimeKey(), state, call, peerKey, localPreview, remoteFrame);
		String focusedParticipantCode = focusSelf ? state.accountCode : focusPeer ? peerCode : "";
		String status = switch (phase) {
			case INCOMING -> "Входящий вызов";
			case OUTGOING -> "Ожидание ответа";
			case ACTIVE -> "Защищённый видеоканал";
			case IDLE -> "";
		};
		return new MaxCallVisualSnapshot(
				phase,
				peerCode,
				peerAvatar,
				peerAvatarAnimated,
				localPreview,
				remoteFrame,
				participants,
				focusedParticipantCode,
				status,
				cameraEnabled,
				microphoneEnabled,
				cameraOptions,
				microphoneOptions,
				connectedCameraCount,
				connectedDroneCount,
				selectedCameraIndex,
				microphoneCount,
				selectedMicrophoneIndex,
				cameraScroll,
				microphoneScroll,
				callMenuOpen,
				cameraPickerOpen,
				contactPickerOpen,
				focusSelf,
				focusPeer,
				phase == MaxCallPhase.ACTIVE ? Math.max(0L, System.currentTimeMillis() - call.acceptedAtMillis) : 0L
		);
	}

	private static List<MaxCallParticipantSnapshot> captureCallParticipants(
			ScreenRuntimeKey selfKey,
			MaxRuntimeState selfState,
			MaxCallSession call,
			ScreenRuntimeKey videoPeerKey,
			BufferedImage localPreview,
			BufferedImage remoteFrame
	) {
		if (selfKey == null || selfState == null || call == null) {
			return List.of();
		}
		List<ScreenRuntimeKey> participantKeys = call.participants();
		if (participantKeys.isEmpty()) {
			return List.of();
		}
		List<MaxCallParticipantSnapshot> participants = new ArrayList<>(participantKeys.size());
		for (ScreenRuntimeKey participantKey : participantKeys) {
			if (participantKey == null) {
				continue;
			}
			boolean self = Objects.equals(participantKey, selfKey);
			MaxRuntimeState participantState = self ? selfState : MAX_STATES.get(participantKey);
			String code = call.participantCode(participantKey);
			BufferedImage avatar = null;
			boolean avatarAnimated = false;
			BufferedImage video = null;
			boolean cameraEnabled = false;
			boolean microphoneEnabled = true;
			if (participantState != null) {
				synchronized (participantState) {
					code = participantState.accountCode == null || participantState.accountCode.isBlank() ? code : participantState.accountCode;
					avatar = currentAvatarFrameLocked(participantState);
					avatarAnimated = avatarAnimatedLocked(participantState);
					cameraEnabled = participantState.cameraEnabled;
					microphoneEnabled = participantState.microphoneEnabled;
				}
			}
			if (self) {
				video = localPreview;
			} else if (Objects.equals(participantKey, videoPeerKey)) {
				video = remoteFrame;
			}
			participants.add(new MaxCallParticipantSnapshot(
					code,
					avatar,
					avatarAnimated,
					video,
					self,
					cameraEnabled && (self || video != null),
					microphoneEnabled,
					call.isRinging(participantKey)
			));
		}
		return participants.isEmpty() ? List.of() : List.copyOf(participants);
	}

	private static List<MaxContactSnapshot> captureContactSnapshots(MinecraftServer server, MaxRuntimeState state, ScreenRuntimeKey selfKey) {
		List<String> contacts;
		synchronized (state) {
			contacts = List.copyOf(state.contacts);
		}
		if (contacts.isEmpty()) {
			return List.of();
		}
		List<MaxContactSnapshot> snapshots = new ArrayList<>(contacts.size());
		for (String contact : contacts) {
			ScreenRuntimeKey key = ACCOUNT_INDEX.get(contact);
			MaxRuntimeState peerState = key != null ? MAX_STATES.get(key) : null;
			ScreenComponent component = key != null ? resolveScreenComponent(server, key) : null;
			MaxCallSession call = key != null ? currentCall(key) : null;
			BufferedImage avatar = null;
			boolean avatarAnimated = false;
			if (peerState != null) {
				synchronized (peerState) {
					avatar = currentAvatarFrameLocked(peerState);
					avatarAnimated = avatarAnimatedLocked(peerState);
				}
			}
			boolean sameCall = call != null && call.isParticipant(selfKey);
			snapshots.add(new MaxContactSnapshot(
					contact,
					avatar,
					avatarAnimated,
					component != null && component.powered(),
					sameCall && call.isRinging(key),
					sameCall && call.isAccepted(key)
			));
		}
		return snapshots;
	}

	private static List<MaxFileShareContactSnapshot> fileShareContacts(MinecraftServer server, MaxRuntimeState state, ScreenRuntimeKey selfKey) {
		List<MaxContactSnapshot> contacts = captureContactSnapshots(server, state, selfKey);
		if (contacts.isEmpty()) {
			return List.of();
		}
		Set<String> selected = new HashSet<>(state.fileShareSelectedContacts);
		List<MaxFileShareContactSnapshot> snapshots = new ArrayList<>(contacts.size());
		for (MaxContactSnapshot contact : contacts) {
			if (contact == null) {
				continue;
			}
			snapshots.add(new MaxFileShareContactSnapshot(
					contact.code(),
					contact.avatarFrame(),
					contact.online(),
					selected.contains(contact.code())
			));
		}
		return snapshots.isEmpty() ? List.of() : List.copyOf(snapshots);
	}

	private static MaxIncomingFileSnapshot incomingFileSnapshot(MinecraftServer server, MaxRuntimeState state) {
		if (state == null || state.incomingFiles.isEmpty()) {
			return null;
		}
		MaxIncomingFile incoming = state.incomingFiles.get(0);
		MaxRuntimeState senderState = MAX_STATES.get(ACCOUNT_INDEX.get(incoming.senderCode()));
		BufferedImage senderAvatar = null;
		boolean senderAvatarAnimated = false;
		if (senderState != null) {
			synchronized (senderState) {
				senderAvatar = currentAvatarFrameLocked(senderState);
				senderAvatarAnimated = avatarAnimatedLocked(senderState);
			}
		}
		return new MaxIncomingFileSnapshot(
				incoming.senderCode(),
				senderAvatar,
				senderAvatarAnimated,
				incoming.title() == null || incoming.title().isBlank() ? defaultSharedFileTitle(incoming.kind()) : incoming.title(),
				incoming.subtitle() == null ? "" : incoming.subtitle(),
				incoming.kind() != null ? incoming.kind() : GalleryItemKind.MEDIA
		);
	}

	private static List<MaxContactSnapshot> contactInviteCandidates(MinecraftServer server, MaxRuntimeState state, MaxCallSession call, ScreenRuntimeKey selfKey) {
		if (state == null || call == null) {
			return List.of();
		}
		List<MaxContactSnapshot> contacts = captureContactSnapshots(server, state, selfKey);
		if (contacts.isEmpty()) {
			return List.of();
		}
		List<MaxContactSnapshot> candidates = new ArrayList<>();
		for (MaxContactSnapshot contact : contacts) {
			if (contact == null) {
				continue;
			}
			ScreenRuntimeKey key = ACCOUNT_INDEX.get(contact.code());
			if (key != null && call.isParticipant(key)) {
				continue;
			}
			candidates.add(contact);
		}
		return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
	}

	private static void startCall(MinecraftServer server, ScreenRuntimeKey callerKey, String targetCode) {
		if (server == null || callerKey == null) {
			return;
		}
		String code = normalizeAccountCode(targetCode);
		MaxRuntimeState callerState = MAX_STATES.get(callerKey);
		ScreenRuntimeKey calleeKey = ACCOUNT_INDEX.get(code);
		ScreenComponent callerComponent = resolveScreenComponent(server, callerKey);
		ScreenComponent calleeComponent = calleeKey != null ? resolveScreenComponent(server, calleeKey) : null;
		if (callerState == null || callerComponent == null) {
			return;
		}
		if (calleeKey == null || calleeComponent == null || !calleeComponent.powered()) {
			synchronized (callerState) {
				callerState.statusText = "Контакт недоступен";
				callerState.version++;
			}
			requestRuntimeRender(server, callerKey);
			return;
		}
		endCall(server, callerKey);
		endCall(server, calleeKey);
		String callerCode;
		synchronized (callerState) {
			callerCode = callerState.accountCode;
		}
		MaxCallSession call = new MaxCallSession(UUID.randomUUID(), callerKey, calleeKey, callerCode, code);
		CALLS_BY_ID.put(call.id, call);
		CALL_BY_SCREEN.put(callerKey, call.id);
		CALL_BY_SCREEN.put(calleeKey, call.id);
		synchronized (callerState) {
			callerState.callMenuOpen = true;
			callerState.cameraPickerOpen = false;
			callerState.callContactPickerOpen = false;
			callerState.focusPeer = false;
			callerState.focusSelf = false;
			callerState.focusedPeerKey = null;
			callerState.version++;
		}
		MaxRuntimeState calleeState = ensureState(server, calleeComponent);
		if (calleeState != null) {
			synchronized (calleeState) {
				calleeState.callMenuOpen = true;
				calleeState.cameraPickerOpen = false;
				calleeState.callContactPickerOpen = false;
				calleeState.focusPeer = false;
				calleeState.focusSelf = false;
				calleeState.focusedPeerKey = null;
				calleeState.version++;
			}
		}
		refreshConnectedSpeakersNow(server, calleeComponent);
		requestRuntimeRender(server, callerKey);
		requestRuntimeRender(server, calleeKey);
	}

	private static void inviteContactToCall(MinecraftServer server, ScreenRuntimeKey inviterKey, String targetCode) {
		if (server == null || inviterKey == null) {
			return;
		}
		MaxCallSession call = currentCall(inviterKey);
		MaxRuntimeState inviterState = MAX_STATES.get(inviterKey);
		String code = normalizeAccountCode(targetCode);
		ScreenRuntimeKey inviteeKey = ACCOUNT_INDEX.get(code);
		ScreenComponent inviteeComponent = inviteeKey != null ? resolveScreenComponent(server, inviteeKey) : null;
		if (call == null || !call.isAccepted(inviterKey) || inviterState == null) {
			return;
		}
		if (inviteeKey == null || inviteeComponent == null || !inviteeComponent.powered()) {
			synchronized (inviterState) {
				inviterState.statusText = "Контакт недоступен";
				inviterState.callContactPickerOpen = false;
				inviterState.version++;
			}
			requestRuntimeRender(server, inviterKey);
			return;
		}
		UUID previousCallId = CALL_BY_SCREEN.get(inviteeKey);
		if (previousCallId != null && !Objects.equals(previousCallId, call.id)) {
			endCall(server, inviteeKey);
		}
		if (!call.addInvitee(inviterKey, inviteeKey, code)) {
			synchronized (inviterState) {
				inviterState.statusText = "Контакт уже в звонке";
				inviterState.callContactPickerOpen = false;
				inviterState.version++;
			}
			requestRuntimeRender(server, inviterKey);
			return;
		}
		CALL_BY_SCREEN.put(inviteeKey, call.id);
		synchronized (inviterState) {
			inviterState.callContactPickerOpen = false;
			inviterState.version++;
		}
		MaxRuntimeState inviteeState = ensureState(server, inviteeComponent);
		if (inviteeState != null) {
			synchronized (inviteeState) {
				inviteeState.callMenuOpen = true;
				inviteeState.cameraPickerOpen = false;
				inviteeState.callContactPickerOpen = false;
				inviteeState.focusPeer = false;
				inviteeState.focusSelf = false;
				inviteeState.focusedPeerKey = null;
				inviteeState.version++;
			}
		}
		refreshConnectedSpeakersNow(server, inviteeComponent);
		MicrophoneSystem.onMaxCallStateChanged(server);
		requestRuntimeRender(server, inviterKey);
		requestRuntimeRender(server, inviteeKey);
		for (ScreenRuntimeKey participant : call.acceptedParticipants()) {
			requestRuntimeRender(server, participant);
		}
	}

	private static void acceptCall(MinecraftServer server, ScreenRuntimeKey calleeKey) {
		MaxCallSession call = currentCall(calleeKey);
		if (server == null || call == null || !call.isRinging(calleeKey)) {
			return;
		}
		call.accept(calleeKey);
		for (ScreenRuntimeKey participant : call.participants()) {
			setCallMenuState(participant, false);
		}
		ScreenComponent callee = resolveScreenComponent(server, calleeKey);
		if (callee != null) {
			refreshConnectedSpeakersNow(server, callee);
		}
		MicrophoneSystem.onMaxCallStateChanged(server);
		for (ScreenRuntimeKey participant : call.participants()) {
			requestRuntimeRender(server, participant);
		}
	}

	private static void endCall(MinecraftServer server, ScreenRuntimeKey key) {
		if (key == null) {
			return;
		}
		UUID callId = CALL_BY_SCREEN.remove(key);
		if (callId == null) {
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(key));
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(key));
			return;
		}
		MaxCallSession call = CALLS_BY_ID.get(callId);
		if (call == null) {
			CALL_BY_SCREEN.remove(key);
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(key));
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(key));
			return;
		}
		if (call.isRinging(key) && call.acceptedParticipantCount() >= 2) {
			call.removeParticipant(key);
			CALL_BY_SCREEN.remove(key);
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(key));
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(key));
			clearRemoteFrame(key);
			clearLocalFrame(key);
			clearCallUiState(key);
			if (server != null) {
				ScreenComponent component = resolveScreenComponent(server, key);
				if (component != null) {
					refreshConnectedSpeakersNow(server, component);
				}
				requestRuntimeRender(server, key);
				for (ScreenRuntimeKey participant : call.participants()) {
					requestRuntimeRender(server, participant);
				}
				MicrophoneSystem.onMaxCallStateChanged(server);
			}
			return;
		}
		List<ScreenRuntimeKey> participants = call.participants();
		if (call.acceptedParticipantCount() > 2 && call.isAccepted(key)) {
			call.removeParticipant(key);
			CALL_BY_SCREEN.remove(key);
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(key));
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(key));
			clearRemoteFrame(key);
			clearLocalFrame(key);
			clearCallUiState(key);
			if (server != null) {
				ScreenComponent component = resolveScreenComponent(server, key);
				if (component != null) {
					refreshConnectedSpeakersNow(server, component);
				}
				requestRuntimeRender(server, key);
				for (ScreenRuntimeKey participant : call.participants()) {
					requestRuntimeRender(server, participant);
				}
				MicrophoneSystem.onMaxCallStateChanged(server);
			}
			return;
		}
		CALLS_BY_ID.remove(callId);
		for (ScreenRuntimeKey participant : participants) {
			CALL_BY_SCREEN.remove(participant);
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(participant));
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(participant));
			clearRemoteFrame(participant);
			clearLocalFrame(participant);
			clearCallUiState(participant);
		}
		if (server != null) {
			for (ScreenRuntimeKey participant : participants) {
				ScreenComponent participantComponent = resolveScreenComponent(server, participant);
				if (participantComponent != null) {
					refreshConnectedSpeakersNow(server, participantComponent);
				}
				requestRuntimeRender(server, participant);
			}
			MicrophoneSystem.onMaxCallStateChanged(server);
		}
	}

	private static void clearCallUiState(ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.callMenuOpen = false;
			state.cameraPickerOpen = false;
			state.callContactPickerOpen = false;
			state.focusPeer = false;
			state.focusSelf = false;
			state.focusedPeerKey = null;
			state.version++;
		}
	}

	private static void toggleCamera(MinecraftServer server, ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.cameraEnabled = !state.cameraEnabled;
			state.version++;
		}
		persistState(server, key, state);
		RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(key));
		clearLocalFrame(key);
		requestRuntimeRender(server, key);
		requestPeerRenderAfterLocalCameraChange(server, key);
	}

	private static void setCallMenuState(ScreenRuntimeKey key, boolean open) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.callMenuOpen = open;
			state.version++;
		}
	}

	private static boolean isCallMenuVisible(MaxRuntimeState state) {
		if (state == null) {
			return false;
		}
		synchronized (state) {
			return state.callMenuOpen;
		}
	}

	private static boolean callFocused(MaxRuntimeState state) {
		if (state == null) {
			return false;
		}
		synchronized (state) {
			return state.focusSelf || state.focusPeer;
		}
	}

	private static boolean callFocusSelf(MaxRuntimeState state) {
		if (state == null) {
			return false;
		}
		synchronized (state) {
			return state.focusSelf;
		}
	}

	private static boolean callFocusPeer(MaxRuntimeState state) {
		if (state == null) {
			return false;
		}
		synchronized (state) {
			return state.focusPeer;
		}
	}

	private static ScreenRuntimeKey focusedCallParticipantKey(ScreenRuntimeKey selfKey, MaxRuntimeState state, MaxCallSession call) {
		if (selfKey == null || state == null || call == null) {
			return null;
		}
		synchronized (state) {
			if (state.focusSelf) {
				return selfKey;
			}
			if (state.focusPeer && state.focusedPeerKey != null && call.isParticipant(state.focusedPeerKey)) {
				return state.focusedPeerKey;
			}
			if (state.focusPeer) {
				return call.peer(selfKey);
			}
		}
		return null;
	}

	private static List<ScreenRuntimeKey> callMiniParticipantKeys(ScreenRuntimeKey selfKey, MaxRuntimeState state, MaxCallSession call) {
		if (call == null) {
			return List.of();
		}
		ScreenRuntimeKey focusedKey = focusedCallParticipantKey(selfKey, state, call);
		List<ScreenRuntimeKey> participants = call.participants();
		if (participants.isEmpty()) {
			return List.of();
		}
		List<ScreenRuntimeKey> miniParticipants = new ArrayList<>(participants.size());
		for (ScreenRuntimeKey participant : participants) {
			if (!Objects.equals(participant, focusedKey)) {
				miniParticipants.add(participant);
			}
		}
		return List.copyOf(miniParticipants);
	}

	private static void toggleCallMenu(MinecraftServer server, ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.callMenuOpen = !state.callMenuOpen;
			state.version++;
		}
		requestRuntimeRender(server, key);
	}

	private static void focusCallParticipant(MinecraftServer server, ScreenRuntimeKey key, boolean self) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.focusSelf = self;
			state.focusPeer = !self;
			state.focusedPeerKey = null;
			state.callMenuOpen = true;
			state.version++;
		}
		requestRuntimeRender(server, key);
	}

	private static void focusCallParticipant(MinecraftServer server, ScreenRuntimeKey key, ScreenRuntimeKey participantKey) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null || participantKey == null) {
			return;
		}
		synchronized (state) {
			state.focusSelf = false;
			state.focusPeer = true;
			state.focusedPeerKey = participantKey;
			state.callMenuOpen = true;
			state.version++;
		}
		requestRuntimeRender(server, key);
	}

	private static void clearCallFocus(MinecraftServer server, ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.focusSelf = false;
			state.focusPeer = false;
			state.focusedPeerKey = null;
			state.callMenuOpen = true;
			state.version++;
		}
		requestRuntimeRender(server, key);
	}

	private static void toggleMicrophone(MinecraftServer server, ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.microphoneEnabled = !state.microphoneEnabled;
			state.version++;
		}
		persistState(server, key, state);
		requestRuntimeRender(server, key);
		requestPeerRender(server, key);
		MicrophoneSystem.onMaxCallStateChanged(server);
	}

	private static void cycleSelectedMicrophone(MinecraftServer server, ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		List<MicrophoneSystem.ScreenMicrophoneDevice> microphones = MicrophoneSystem.connectedMicrophoneDevices(server, key);
		int count = microphones.size();
		synchronized (state) {
			int currentIndex = normalizeSelectedMicrophoneLocked(state, microphones);
			if (count > 1) {
				int nextIndex = currentIndex < 0 ? 0 : Math.floorMod(currentIndex + 1, count);
				state.selectedMicrophoneIndex = nextIndex;
				state.selectedMicrophoneKey = microphoneDeviceKey(microphones.get(nextIndex));
			} else {
				state.selectedMicrophoneIndex = -1;
				state.selectedMicrophoneKey = "";
			}
			state.version++;
		}
		persistState(server, key, state);
		requestRuntimeRender(server, key);
		requestPeerRender(server, key);
		MicrophoneSystem.onMaxCallStateChanged(server);
	}

	private static void openCameraPicker(MinecraftServer server, ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.cameraPickerOpen = true;
			state.callContactPickerOpen = false;
			state.callMenuOpen = true;
			state.version++;
		}
		requestRuntimeRender(server, key);
	}

	private static void openCallContactPicker(MinecraftServer server, ScreenRuntimeKey key) {
		MaxRuntimeState state = MAX_STATES.get(key);
		MaxCallSession call = currentCall(key);
		if (state == null || call == null || callPhase(call, key) != MaxCallPhase.ACTIVE) {
			return;
		}
		synchronized (state) {
			state.callContactPickerOpen = true;
			state.cameraPickerOpen = false;
			state.callMenuOpen = true;
			state.version++;
		}
		requestRuntimeRender(server, key);
	}

	private static void cycleSelectedCamera(MinecraftServer server, ScreenComponent component) {
		if (server == null || component == null) {
			return;
		}
		MaxRuntimeState state = MAX_STATES.get(component.runtimeKey());
		if (state == null) {
			return;
		}
		List<LiveCameraReference> cameras = connectedCameraReferences(server, component);
		if (cameras.isEmpty()) {
			synchronized (state) {
				state.selectedCameraUrl = "";
				state.statusText = "Нет подключённых камер";
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return;
		}
		List<String> urls = cameras.stream().map(MonitorScreenLiveSources::liveCameraGalleryUrl).toList();
		synchronized (state) {
			int index = urls.indexOf(state.selectedCameraUrl);
			state.selectedCameraUrl = urls.get(Math.floorMod(index + 1, urls.size()));
			state.statusText = "";
			state.version++;
		}
		persistState(server, component.runtimeKey(), state);
		RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(component.runtimeKey()));
		clearLocalFrame(component.runtimeKey());
		requestRuntimeRender(server, component.runtimeKey());
		requestPeerRenderAfterLocalCameraChange(server, component.runtimeKey());
	}

	private static void selectCamera(MinecraftServer server, ScreenComponent component, MaxRuntimeState state, MaxCameraOptionSnapshot option) {
		if (server == null || component == null || state == null || option == null) {
			return;
		}
		synchronized (state) {
			state.selectedCameraUrl = option.url();
			state.cameraPickerOpen = false;
			state.statusText = "";
			state.version++;
		}
		persistState(server, component.runtimeKey(), state);
		RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(component.runtimeKey()));
		clearLocalFrame(component.runtimeKey());
		requestRuntimeRender(server, component.runtimeKey());
		requestPeerRenderAfterLocalCameraChange(server, component.runtimeKey());
	}

	private static void selectMicrophone(MinecraftServer server, ScreenRuntimeKey key, MaxRuntimeState state, MaxMicrophoneOptionSnapshot option) {
		if (server == null || key == null || state == null || option == null) {
			return;
		}
		synchronized (state) {
			state.selectedMicrophoneIndex = option.index();
			state.selectedMicrophoneKey = option.deviceKey() == null ? "" : option.deviceKey();
			state.cameraPickerOpen = false;
			state.statusText = "";
			state.version++;
		}
		persistState(server, key, state);
		requestRuntimeRender(server, key);
		requestPeerRender(server, key);
		MicrophoneSystem.onMaxCallStateChanged(server);
	}

	private static void scrollCallCameraPicker(MinecraftServer server, ScreenRuntimeKey key, MaxRuntimeState state, UiLayout layout, int cameraCount, int microphoneCount, int delta) {
		if (server == null || key == null || state == null || layout == null || delta == 0) {
			return;
		}
		boolean changed = false;
		synchronized (state) {
			normalizeCallDevicePickerScrollLocked(state, layout, cameraCount, microphoneCount);
			int nextScroll = clampInt(state.cameraPickerScroll + delta, 0, maxCallDeviceCameraScroll(layout, cameraCount));
			if (nextScroll != state.cameraPickerScroll) {
				state.cameraPickerScroll = nextScroll;
				state.version++;
				changed = true;
			}
		}
		if (changed) {
			requestRuntimeRender(server, key);
		}
	}

	private static void scrollCallMicrophonePicker(MinecraftServer server, ScreenRuntimeKey key, MaxRuntimeState state, UiLayout layout, int cameraCount, int microphoneCount, int delta) {
		if (server == null || key == null || state == null || layout == null || delta == 0) {
			return;
		}
		boolean changed = false;
		synchronized (state) {
			normalizeCallDevicePickerScrollLocked(state, layout, cameraCount, microphoneCount);
			int nextScroll = clampInt(state.microphonePickerScroll + delta, 0, maxCallDeviceMicrophoneScroll(layout, microphoneCount));
			if (nextScroll != state.microphonePickerScroll) {
				state.microphonePickerScroll = nextScroll;
				state.version++;
				changed = true;
			}
		}
		if (changed) {
			requestRuntimeRender(server, key);
		}
	}

	private static void normalizeCallDevicePickerScrollLocked(MaxRuntimeState state, UiLayout layout, int cameraCount, int microphoneCount) {
		if (state == null || layout == null) {
			return;
		}
		state.cameraPickerScroll = clampInt(state.cameraPickerScroll, 0, maxCallDeviceCameraScroll(layout, cameraCount));
		state.microphonePickerScroll = clampInt(state.microphonePickerScroll, 0, maxCallDeviceMicrophoneScroll(layout, microphoneCount));
	}

	private static void requestPeerRenderAfterLocalCameraChange(MinecraftServer server, ScreenRuntimeKey key) {
		MaxCallSession call = currentCall(key);
		List<ScreenRuntimeKey> peers = call != null ? call.otherParticipants(key) : List.of();
		if (peers.isEmpty()) {
			return;
		}
		for (ScreenRuntimeKey peer : peers) {
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(peer));
			clearRemoteFrame(peer);
			requestRuntimeRender(server, peer);
		}
	}

	private static void requestPeerRender(MinecraftServer server, ScreenRuntimeKey key) {
		MaxCallSession call = currentCall(key);
		List<ScreenRuntimeKey> peers = call != null ? call.otherParticipants(key) : List.of();
		for (ScreenRuntimeKey peer : peers) {
			requestRuntimeRender(server, peer);
		}
	}

	private static void refreshRemoteVideo(MinecraftServer server, ScreenComponent viewerComponent, MaxRuntimeState peerState) {
		if (server == null || viewerComponent == null || peerState == null) {
			return;
		}
		boolean cameraEnabled;
		String cameraUrl;
		synchronized (peerState) {
			cameraEnabled = peerState.cameraEnabled;
			cameraUrl = peerState.selectedCameraUrl;
		}
		if (!cameraEnabled || cameraUrl == null || cameraUrl.isBlank()) {
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(viewerComponent.runtimeKey()));
			clearRemoteFrame(viewerComponent.runtimeKey());
			return;
		}
		LiveCameraReference cameraRef = liveCameraGalleryReference(cameraUrl, viewerComponent.runtimeKey().dimension());
		if (cameraRef == null) {
			RendererBotCameraSystem.stopLiveStream(maxVideoStreamOwnerId(viewerComponent.runtimeKey()));
			clearRemoteFrame(viewerComponent.runtimeKey());
			return;
		}
		startRemoteVideoStream(server, viewerComponent, cameraRef, cameraUrl);
	}

	private static void refreshLocalVideo(MinecraftServer server, ScreenComponent component, MaxRuntimeState state) {
		if (server == null || component == null || state == null) {
			return;
		}
		boolean cameraEnabled;
		String cameraUrl;
		synchronized (state) {
			cameraEnabled = state.cameraEnabled;
			cameraUrl = state.selectedCameraUrl;
		}
		if (!cameraEnabled || cameraUrl == null || cameraUrl.isBlank()) {
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(component.runtimeKey()));
			clearLocalFrame(component.runtimeKey());
			return;
		}
		LiveCameraReference cameraRef = liveCameraGalleryReference(cameraUrl, component.runtimeKey().dimension());
		if (cameraRef == null) {
			RendererBotCameraSystem.stopLiveStream(maxLocalVideoStreamOwnerId(component.runtimeKey()));
			clearLocalFrame(component.runtimeKey());
			return;
		}
		startVideoStream(server, component, cameraRef, cameraUrl, true);
	}

	private static void startRemoteVideoStream(MinecraftServer server, ScreenComponent viewerComponent, LiveCameraReference cameraRef, String sourceUrl) {
		startVideoStream(server, viewerComponent, cameraRef, sourceUrl, false);
	}

	private static void startVideoStream(MinecraftServer server, ScreenComponent viewerComponent, LiveCameraReference cameraRef, String sourceUrl, boolean localStream) {
		ServerLevel screenLevel = server.getLevel(viewerComponent.runtimeKey().dimension());
		if (screenLevel == null || cameraRef == null) {
			clearVideoFrame(viewerComponent.runtimeKey(), localStream);
			return;
		}
		int fullWidth = Math.max(1, viewerComponent.width()) * MAP_SIZE;
		int fullHeight = Math.max(1, viewerComponent.height()) * MAP_SIZE;
		String ownerId = localStream ? maxLocalVideoStreamOwnerId(viewerComponent.runtimeKey()) : maxVideoStreamOwnerId(viewerComponent.runtimeKey());
		if (cameraRef.sourceType() == LiveCameraSourceType.DRONE) {
			DroneSystem.DroneLiveFeedState droneState = cameraRef.sourceUuid() != null
					? DroneSystem.resolveLiveFeedState(server, cameraRef.sourceUuid(), cameraRef.dimension(), cameraRef.pos())
					: null;
			if (droneState == null) {
				clearVideoFrame(viewerComponent.runtimeKey(), localStream);
				return;
			}
			ServerLevel droneLevel = server.getLevel(droneState.dimension());
			if (droneLevel == null) {
				clearVideoFrame(viewerComponent.runtimeKey(), localStream);
				return;
			}
			RendererBotCameraSystem.ensureLiveStream(
					ownerId,
					droneLevel,
					null,
					droneState.expectedX(),
					droneState.expectedY(),
					droneState.expectedZ(),
					droneState.yaw(),
					droneState.pitch(),
					droneState.followEntityUuid(),
					droneState.hiddenEntityUuids(),
					droneState.omnidirectionalChunkLoading(),
					fullWidth,
					fullHeight,
					LIVE_CAMERA_FOV_DEGREES,
					LIVE_CAMERA_TARGET_FPS,
					frame -> onVideoFrame(server, viewerComponent.runtimeKey(), sourceUrl, fullWidth, fullHeight, frame.pixels(), localStream),
					error -> onVideoFailure(server, viewerComponent.runtimeKey(), error, localStream)
			);
			return;
		}
		ServerLevel cameraLevel = server.getLevel(cameraRef.dimension());
		BlockPos cameraPos = cameraRef.pos();
		if (cameraLevel == null || cameraPos == null || !cameraLevel.hasChunkAt(cameraPos) || !isCameraBlock(cameraLevel, cameraPos)) {
			clearVideoFrame(viewerComponent.runtimeKey(), localStream);
			return;
		}
		BlockState cameraState = cameraLevel.getBlockState(cameraPos);
		LiveCameraPose pose = liveCameraCapturePose(cameraLevel, cameraPos, cameraState);
		Vec3 origin = pose.origin();
		RendererBotCameraSystem.ensureLiveStream(
				ownerId,
				cameraLevel,
				cameraPos,
				origin.x,
				origin.y - RENDERER_BOT_EYE_HEIGHT,
				origin.z,
				pose.yaw(),
				pose.pitch(),
				fullWidth,
				fullHeight,
				LIVE_CAMERA_FOV_DEGREES,
				LIVE_CAMERA_TARGET_FPS,
				frame -> onVideoFrame(server, viewerComponent.runtimeKey(), sourceUrl, fullWidth, fullHeight, frame.pixels(), localStream),
				error -> onVideoFailure(server, viewerComponent.runtimeKey(), error, localStream)
		);
	}

	private static void onRemoteVideoFrame(MinecraftServer server, ScreenRuntimeKey key, String sourceUrl, int width, int height, byte[] pixels) {
		onVideoFrame(server, key, sourceUrl, width, height, pixels, false);
	}

	private static void onVideoFrame(MinecraftServer server, ScreenRuntimeKey key, String sourceUrl, int width, int height, byte[] pixels, boolean localStream) {
		if (server == null || key == null || pixels == null || pixels.length == 0) {
			return;
		}
		ensureExecutors();
		liveCameraExecutor.execute(() -> {
			BufferedImage image = mapPaletteImage(pixels, width, height);
			server.execute(() -> {
				MaxRuntimeState state = MAX_STATES.get(key);
				MaxCallSession call = currentCall(key);
				if (state == null || call == null) {
					RendererBotCameraSystem.stopLiveStream(localStream ? maxLocalVideoStreamOwnerId(key) : maxVideoStreamOwnerId(key));
					return;
				}
				synchronized (state) {
					if (localStream && (!state.cameraEnabled || !Objects.equals(state.selectedCameraUrl, sourceUrl))) {
						return;
					}
					if (localStream) {
						state.localVideoUrl = sourceUrl;
						state.localFrame = image;
					} else {
						state.remoteVideoUrl = sourceUrl;
						state.remoteFrame = image;
					}
					state.version++;
				}
				requestRuntimeRender(server, key);
			});
		});
	}

	private static void onRemoteVideoFailure(MinecraftServer server, ScreenRuntimeKey key, String error) {
		onVideoFailure(server, key, error, false);
	}

	private static void onVideoFailure(MinecraftServer server, ScreenRuntimeKey key, String error, boolean localStream) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			state.statusText = error == null || error.isBlank() ? "Видео недоступно" : error;
			if (localStream) {
				state.localFrame = null;
				state.localVideoUrl = "";
			} else {
				state.remoteFrame = null;
				state.remoteVideoUrl = "";
			}
			state.version++;
		}
		requestRuntimeRender(server, key);
	}

	private static void clearRemoteFrame(ScreenRuntimeKey key) {
		clearVideoFrame(key, false);
	}

	private static void clearLocalFrame(ScreenRuntimeKey key) {
		clearVideoFrame(key, true);
	}

	private static void clearVideoFrame(ScreenRuntimeKey key, boolean localStream) {
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		synchronized (state) {
			BufferedImage frame = localStream ? state.localFrame : state.remoteFrame;
			String url = localStream ? state.localVideoUrl : state.remoteVideoUrl;
			if (frame == null && (url == null || url.isBlank())) {
				return;
			}
			if (localStream) {
				state.localFrame = null;
				state.localVideoUrl = "";
			} else {
				state.remoteFrame = null;
				state.remoteVideoUrl = "";
			}
			state.version++;
		}
	}

	private static boolean handleAvatarPickerTouch(
			MinecraftServer server,
			ScreenComponent component,
			MaxRuntimeState state,
			UiLayout layout,
			UiPoint touchPoint
	) {
		if (maxAvatarPickerBackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.avatarPickerOpen = false;
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		List<MaxAvatarCandidateSnapshot> candidates = avatarCandidates(component);
		int index = maxAvatarCandidateIndexAt(layout, candidates.size(), touchPoint);
		if (index >= 0 && index < candidates.size()) {
			MaxAvatarCandidateSnapshot candidate = candidates.get(index);
			MonitorMediaApp.LoadedMedia avatarMedia = loadAvatarMedia(component, candidate.url(), candidate.localMediaKey());
			BufferedImage avatarFrame = avatarFrame(avatarMedia, 0, candidate.preview());
			synchronized (state) {
				state.avatarUrl = candidate.url();
				state.avatarLocalMediaKey = candidate.localMediaKey();
				state.avatarMedia = avatarMedia;
				state.avatarFrame = avatarFrame;
				state.avatarAnimationStartedAtMillis = System.currentTimeMillis();
				state.avatarRenderScheduled = false;
				state.avatarPickerOpen = false;
				state.statusText = "Аватар обновлён";
				state.version++;
			}
			persistState(server, component.runtimeKey(), state);
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		return true;
	}

	private static boolean handleRingtonePickerTouch(
			MinecraftServer server,
			ScreenComponent component,
			MaxRuntimeState state,
			UiLayout layout,
			UiPoint touchPoint
	) {
		if (maxAvatarPickerBackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.ringtonePickerOpen = false;
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		List<MaxRingtoneCandidateSnapshot> candidates = ringtoneCandidates(component, state);
		int index = maxRingtoneCandidateIndexAt(layout, candidates.size(), touchPoint);
		if (index < 0 || index >= candidates.size()) {
			return true;
		}
		MaxRingtoneCandidateSnapshot candidate = candidates.get(index);
		UiRect row = maxRingtoneCandidateRect(layout, index);
		if (maxRingtoneCandidatePlayRect(row, layout).contains(touchPoint.x(), touchPoint.y())) {
			toggleRingtonePreview(server, component, state, candidate);
			return true;
		}
		if (maxRingtoneCandidateSelectRect(row, layout).contains(touchPoint.x(), touchPoint.y())) {
			selectRingtone(server, component, state, candidate);
			return true;
		}
		return true;
	}

	private static void toggleSelectedRingtonePreview(MinecraftServer server, ScreenComponent component, MaxRuntimeState state) {
		if (component == null || state == null) {
			return;
		}
		MaxRingtoneCandidateSnapshot selected;
		synchronized (state) {
			String url = state.ringtoneUrl == null || state.ringtoneUrl.isBlank() ? MAX_DEFAULT_RINGTONE_URL : state.ringtoneUrl;
			String title = state.ringtoneTitle == null || state.ringtoneTitle.isBlank() ? "Стандартный рингтон" : state.ringtoneTitle;
			selected = ringtoneCandidate(state, title, "MAX", url, state.ringtoneLocalMediaKey);
		}
		toggleRingtonePreview(server, component, state, selected);
	}

	private static void toggleRingtonePreview(MinecraftServer server, ScreenComponent component, MaxRuntimeState state, MaxRingtoneCandidateSnapshot candidate) {
		if (component == null || state == null || candidate == null) {
			return;
		}
		synchronized (state) {
			boolean same = state.ringtonePreviewPlaying
					&& Objects.equals(Objects.toString(state.ringtonePreviewUrl, MAX_DEFAULT_RINGTONE_URL), Objects.toString(candidate.url(), MAX_DEFAULT_RINGTONE_URL))
					&& Objects.equals(Objects.toString(state.ringtonePreviewLocalMediaKey, ""), Objects.toString(candidate.localMediaKey(), ""));
			if (same) {
				state.ringtonePreviewPlaying = false;
			} else {
				state.ringtonePreviewPlaying = true;
				state.ringtonePreviewUrl = candidate.url() == null || candidate.url().isBlank() ? MAX_DEFAULT_RINGTONE_URL : candidate.url();
				state.ringtonePreviewLocalMediaKey = candidate.localMediaKey() == null ? "" : candidate.localMediaKey();
				state.ringtonePreviewTitle = candidate.title() == null ? "" : candidate.title();
				state.ringtonePreviewStartedAtMillis = System.currentTimeMillis();
			}
			state.version++;
		}
		refreshConnectedSpeakersNow(server, component);
		requestRuntimeRender(server, component.runtimeKey());
	}

	private static void selectRingtone(MinecraftServer server, ScreenComponent component, MaxRuntimeState state, MaxRingtoneCandidateSnapshot candidate) {
		if (component == null || state == null || candidate == null) {
			return;
		}
		synchronized (state) {
			boolean defaultRingtone = candidate.url() == null || candidate.url().isBlank() || Objects.equals(candidate.url(), MAX_DEFAULT_RINGTONE_URL);
			state.ringtoneUrl = defaultRingtone ? "" : candidate.url();
			state.ringtoneLocalMediaKey = defaultRingtone || candidate.localMediaKey() == null ? "" : candidate.localMediaKey();
			state.ringtoneTitle = defaultRingtone || candidate.title() == null ? "" : candidate.title();
			state.ringtonePickerOpen = false;
			state.statusText = "Рингтон выбран";
			state.version++;
		}
		persistState(server, component.runtimeKey(), state);
		requestRuntimeRender(server, component.runtimeKey());
	}

	private static void removeContact(MinecraftServer server, ScreenRuntimeKey key, MaxRuntimeState state, String contactCode) {
		if (key == null || state == null || contactCode == null || contactCode.isBlank()) {
			return;
		}
		boolean removed;
		synchronized (state) {
			removed = state.contacts.remove(contactCode);
			if (removed) {
				state.statusText = "Контакт удалён";
				state.version++;
			}
		}
		if (removed) {
			persistState(server, key, state);
			requestRuntimeRender(server, key);
		}
	}

	private static boolean handleCallCameraPickerTouch(
			MinecraftServer server,
			ScreenComponent component,
			MaxRuntimeState state,
			UiLayout layout,
			UiPoint touchPoint
	) {
		if (maxAvatarPickerBackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.cameraPickerOpen = false;
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		List<LiveCameraReference> cameraRefs = connectedCameraReferences(server, component);
		List<MicrophoneSystem.ScreenMicrophoneDevice> microphones = MicrophoneSystem.connectedMicrophoneDevices(server, component.runtimeKey());
		if (maxCallDeviceCameraScrollLeftRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			scrollCallCameraPicker(server, component.runtimeKey(), state, layout, cameraRefs.size(), microphones.size(), -maxCallDeviceCameraScrollStep(layout));
			return true;
		}
		if (maxCallDeviceCameraScrollRightRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			scrollCallCameraPicker(server, component.runtimeKey(), state, layout, cameraRefs.size(), microphones.size(), maxCallDeviceCameraScrollStep(layout));
			return true;
		}
		if (maxCallDeviceMicrophoneScrollUpRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			scrollCallMicrophonePicker(server, component.runtimeKey(), state, layout, cameraRefs.size(), microphones.size(), -1);
			return true;
		}
		if (maxCallDeviceMicrophoneScrollDownRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			scrollCallMicrophonePicker(server, component.runtimeKey(), state, layout, cameraRefs.size(), microphones.size(), 1);
			return true;
		}
		int cameraScroll;
		int microphoneScroll;
		synchronized (state) {
			normalizeSelectedMicrophoneLocked(state, microphones);
			normalizeCallDevicePickerScrollLocked(state, layout, cameraRefs.size(), microphones.size());
			cameraScroll = state.cameraPickerScroll;
			microphoneScroll = state.microphonePickerScroll;
		}
		List<MaxCameraOptionSnapshot> options = cameraOptions(server, state, cameraRefs);
		int index = maxCallDeviceCameraIndexAt(layout, options.size(), cameraScroll, touchPoint);
		if (index >= 0 && index < options.size()) {
			selectCamera(server, component, state, options.get(index));
			return true;
		}
		List<MaxMicrophoneOptionSnapshot> microphoneOptions = microphoneOptions(state, microphones);
		int microphoneIndex = maxCallDeviceMicrophoneIndexAt(layout, microphoneOptions.size(), microphoneScroll, touchPoint);
		if (microphoneIndex >= 0 && microphoneIndex < microphoneOptions.size()) {
			selectMicrophone(server, component.runtimeKey(), state, microphoneOptions.get(microphoneIndex));
			return true;
		}
		return true;
	}

	private static boolean handleCallContactPickerTouch(
			MinecraftServer server,
			ScreenComponent component,
			MaxRuntimeState state,
			MaxCallSession call,
			UiLayout layout,
			UiPoint touchPoint
	) {
		if (maxAvatarPickerBackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.callContactPickerOpen = false;
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		List<MaxContactSnapshot> contacts = contactInviteCandidates(server, state, call, component.runtimeKey());
		int index = maxContactPickerIndexAt(layout, contacts.size(), touchPoint);
		if (index >= 0 && index < contacts.size()) {
			inviteContactToCall(server, component.runtimeKey(), contacts.get(index).code());
			return true;
		}
		return true;
	}

	private static boolean handleFileSharePickerTouch(
			MinecraftServer server,
			ScreenComponent component,
			MaxRuntimeState state,
			UiLayout layout,
			UiPoint touchPoint
	) {
		if (maxAvatarPickerBackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				clearFileShareDraftLocked(state);
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (maxFileShareSendRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			sendPendingFilesToSelectedContacts(server, component.runtimeKey());
			return true;
		}
		List<MaxFileShareContactSnapshot> contacts;
		synchronized (state) {
			contacts = fileShareContacts(server, state, component.runtimeKey());
		}
		int index = maxContactPickerIndexAt(layout, contacts.size(), touchPoint);
		if (index >= 0 && index < contacts.size()) {
			String code = contacts.get(index).code();
			synchronized (state) {
				if (state.fileShareSelectedContacts.contains(code)) {
					state.fileShareSelectedContacts.remove(code);
				} else {
					state.fileShareSelectedContacts.add(code);
				}
				state.statusText = "";
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
		}
		return true;
	}

	private static boolean handleNotificationsTouch(
			MinecraftServer server,
			ScreenComponent component,
			MaxRuntimeState state,
			UiLayout layout,
			UiPoint touchPoint
	) {
		if (maxAvatarPickerBackRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			synchronized (state) {
				state.notificationsOpen = false;
				state.version++;
			}
			requestRuntimeRender(server, component.runtimeKey());
			return true;
		}
		if (maxNotificationAcceptRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			acceptFirstIncomingFile(server, component.runtimeKey());
			return true;
		}
		if (maxNotificationDeclineRect(layout).contains(touchPoint.x(), touchPoint.y())) {
			ignoreFirstIncomingFile(server, component.runtimeKey());
			return true;
		}
		return true;
	}

	private static void clearFileShareDraftLocked(MaxRuntimeState state) {
		if (state == null) {
			return;
		}
		state.fileSharePickerOpen = false;
		state.fileShareFiles.clear();
		state.fileShareSelectedContacts.clear();
		state.statusText = "";
	}

	private static void sendPendingFilesToSelectedContacts(MinecraftServer server, ScreenRuntimeKey senderKey) {
		if (server == null || senderKey == null) {
			return;
		}
		MaxRuntimeState senderState = MAX_STATES.get(senderKey);
		if (senderState == null) {
			return;
		}
		String senderCode;
		List<MaxSharedGalleryFile> files;
		List<String> recipients;
		synchronized (senderState) {
			senderCode = senderState.accountCode;
			files = List.copyOf(senderState.fileShareFiles);
			recipients = List.copyOf(senderState.fileShareSelectedContacts);
			if (files.isEmpty()) {
				senderState.statusText = "Нет файлов для отправки";
				senderState.version++;
				requestRuntimeRender(server, senderKey);
				return;
			}
			if (recipients.isEmpty()) {
				senderState.statusText = "Выбери хотя бы один контакт";
				senderState.version++;
				requestRuntimeRender(server, senderKey);
				return;
			}
			senderState.statusText = "Отправка...";
			senderState.version++;
		}
		requestRuntimeRender(server, senderKey);
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> prepareFileDeliveries(senderCode, recipients, files), mediaIoExecutor)
				.thenAccept(deliveries -> server.execute(() -> applyPreparedFileDeliveries(server, senderKey, deliveries)));
	}

	private static List<MaxPreparedFileDelivery> prepareFileDeliveries(String senderCode, List<String> recipients, List<MaxSharedGalleryFile> files) {
		if (senderCode == null || senderCode.isBlank() || recipients == null || recipients.isEmpty() || files == null || files.isEmpty()) {
			return List.of();
		}
		List<MaxPreparedFileDelivery> deliveries = new ArrayList<>();
		for (String recipient : recipients) {
			String normalizedRecipient = normalizeAccountCode(recipient);
			if (normalizedRecipient.isBlank()) {
				continue;
			}
			for (MaxSharedGalleryFile file : files) {
				if (file == null) {
					continue;
				}
				String id = UUID.randomUUID().toString();
				String transferLocalMediaKey = copyTransferLocalMedia(file, id);
				String url = !transferLocalMediaKey.isBlank() ? MAX_TRANSFER_URL_PREFIX + transferLocalMediaKey : file.url();
				deliveries.add(new MaxPreparedFileDelivery(
						normalizedRecipient,
						new MaxIncomingFile(
								id,
								senderCode,
								file.title(),
								file.subtitle(),
								url,
								transferLocalMediaKey.isBlank() ? file.localMediaKey() : transferLocalMediaKey,
								file.kind(),
								System.currentTimeMillis()
						)
				));
			}
		}
		return deliveries.isEmpty() ? List.of() : List.copyOf(deliveries);
	}

	private static String copyTransferLocalMedia(MaxSharedGalleryFile file, String id) {
		if (file == null || id == null || id.isBlank()) {
			return "";
		}
		String sourceLocalMediaKey = file.localMediaKey() == null ? "" : file.localMediaKey().trim();
		if (sourceLocalMediaKey.isBlank()) {
			return "";
		}
		Path sourcePath = MonitorMediaApp.savedGalleryMediaFile(sourceLocalMediaKey);
		if (sourcePath == null) {
			return "";
		}
		try {
			return MonitorMediaApp.persistLocalGalleryFile(MAX_TRANSFER_LOCAL_KEY_PREFIX + id, sourcePath);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to prepare MAX file transfer {}: {}", sourceLocalMediaKey, sanitizeMediaError(exception.getMessage()));
			return "";
		}
	}

	private static void applyPreparedFileDeliveries(MinecraftServer server, ScreenRuntimeKey senderKey, List<MaxPreparedFileDelivery> deliveries) {
		MaxRuntimeState senderState = MAX_STATES.get(senderKey);
		if (server == null || senderKey == null || senderState == null) {
			return;
		}
		int delivered = 0;
		Set<ScreenRuntimeKey> changedRecipients = new HashSet<>();
		for (MaxPreparedFileDelivery delivery : deliveries == null ? List.<MaxPreparedFileDelivery>of() : deliveries) {
			if (delivery == null || delivery.file() == null) {
				continue;
			}
			ScreenRuntimeKey recipientKey = ACCOUNT_INDEX.get(delivery.recipientCode());
			ScreenComponent recipientComponent = recipientKey != null ? resolveScreenComponent(server, recipientKey) : null;
			if (recipientKey == null || recipientComponent == null || !recipientComponent.powered()) {
				deleteTransferLocalMediaIfTemporary(delivery.file());
				continue;
			}
			MaxRuntimeState recipientState = ensureState(server, recipientComponent);
			if (recipientState == null) {
				deleteTransferLocalMediaIfTemporary(delivery.file());
				continue;
			}
			synchronized (recipientState) {
				recipientState.incomingFiles.add(delivery.file());
				recipientState.version++;
			}
			persistState(server, recipientKey, recipientState);
			playNotificationSound(server, recipientComponent);
			requestRuntimeRender(server, recipientKey);
			changedRecipients.add(recipientKey);
			delivered++;
		}
		synchronized (senderState) {
			clearFileShareDraftLocked(senderState);
			senderState.statusText = delivered > 0 ? "Отправлено: " + delivered : "Получатели недоступны";
			senderState.version++;
		}
		requestRuntimeRender(server, senderKey);
		for (ScreenRuntimeKey recipientKey : changedRecipients) {
			requestRuntimeRender(server, recipientKey);
		}
	}

	private static void acceptFirstIncomingFile(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		MaxIncomingFile incoming;
		synchronized (state) {
			if (state.incomingFiles.isEmpty()) {
				state.statusText = "Нет уведомлений";
				state.notificationsOpen = false;
				state.version++;
				requestRuntimeRender(server, key);
				return;
			}
			incoming = state.incomingFiles.remove(0);
			state.statusText = saveIncomingFileToGallery(server, key, incoming) ? "Файл сохранён" : "Не удалось сохранить файл";
			if (state.incomingFiles.isEmpty()) {
				state.notificationsOpen = false;
			}
			state.version++;
		}
		persistState(server, key, state);
		requestRuntimeRender(server, key);
	}

	private static void ignoreFirstIncomingFile(MinecraftServer server, ScreenRuntimeKey key) {
		if (server == null || key == null) {
			return;
		}
		MaxRuntimeState state = MAX_STATES.get(key);
		if (state == null) {
			return;
		}
		MaxIncomingFile ignored;
		synchronized (state) {
			if (state.incomingFiles.isEmpty()) {
				state.statusText = "Нет уведомлений";
				state.notificationsOpen = false;
				state.version++;
				requestRuntimeRender(server, key);
				return;
			}
			ignored = state.incomingFiles.remove(0);
			state.statusText = "Файл отклонён";
			if (state.incomingFiles.isEmpty()) {
				state.notificationsOpen = false;
			}
			state.version++;
		}
		deleteTransferLocalMediaIfTemporary(ignored);
		persistState(server, key, state);
		requestRuntimeRender(server, key);
	}

	private static boolean saveIncomingFileToGallery(MinecraftServer server, ScreenRuntimeKey key, MaxIncomingFile incoming) {
		if (server == null || key == null || incoming == null) {
			return false;
		}
		String url = incoming.url() == null || incoming.url().isBlank()
				? MAX_TRANSFER_URL_PREFIX + Objects.toString(incoming.localMediaKey(), "")
				: incoming.url();
		if (url == null || url.isBlank()) {
			return false;
		}
		GalleryItemKind kind = effectiveGalleryItemKind(url, incoming.localMediaKey(), incoming.kind());
		PersistedGalleryItem persisted = new PersistedGalleryItem(
				incoming.title() == null || incoming.title().isBlank() ? defaultSharedFileTitle(kind) : incoming.title(),
				incoming.subtitle() == null ? "" : incoming.subtitle(),
				url,
				kind,
				incoming.localMediaKey() == null ? "" : incoming.localMediaKey()
		);
		MediaRuntimeState mediaState = MEDIA_STATES.computeIfAbsent(
				key,
				ignored -> MediaRuntimeState.fresh(ScreenViewMode.GALLERY, "", () -> onMediaProgressChanged(server, key))
		);
		ScreenComponent component = resolveScreenComponent(server, key);
		if (component != null) {
			ensureGalleryStateHydrated(server, key, mediaState);
		}
		synchronized (mediaState) {
			int existingIndex = resolveGalleryItemIndex(mediaState, persisted.url(), -1);
			if (existingIndex < 0) {
				BufferedImage preview = persistedGalleryPreviewForDisplay(persisted, kind);
				mediaState.galleryItems.add(new GalleryItem(
						persisted.title(),
						persisted.subtitle(),
						persisted.url(),
						persisted.localMediaKey(),
						null,
						preview,
						kind
				));
				mediaState.galleryHydrated = true;
				mediaState.statusText = "Файл сохранён";
				mediaState.version++;
			}
		}
		persistGalleryState(server, key, mediaState);
		requestRuntimeRender(server, key);
		return true;
	}

	private static void deleteTransferLocalMediaIfTemporary(MaxIncomingFile incoming) {
		if (incoming == null || incoming.localMediaKey() == null || incoming.id() == null) {
			return;
		}
		String ownedTransferPrefix = MAX_TRANSFER_LOCAL_KEY_PREFIX + incoming.id();
		if (!incoming.localMediaKey().startsWith(ownedTransferPrefix)) {
			return;
		}
		MonitorMediaApp.deleteSavedGalleryMedia(incoming.localMediaKey());
	}

	private static void playNotificationSound(MinecraftServer server, ScreenComponent component) {
		if (server == null || component == null || component.runtimeKey() == null) {
			return;
		}
		ServerLevel level = server.getLevel(component.runtimeKey().dimension());
		if (level == null) {
			return;
		}
		Vec3 center = screenCenter(component);
		long seed = level.random.nextLong();
		double rangeSqr = 16.0D * 16.0D;
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(center) > rangeSqr) {
				continue;
			}
			boolean hasPack = PolymerResourcePackUtils.hasMainPack(viewer);
			Holder<SoundEvent> sound = hasPack
					? Holder.direct(MAX_NOTIFICATION_SOUND)
					: BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.NOTE_BLOCK_BELL.value());
			viewer.connection.send(new ClientboundSoundPacket(
					sound,
					SoundSource.BLOCKS,
					center.x,
					center.y,
					center.z,
					0.9F,
					hasPack ? 1.0F : 1.35F,
					seed
			));
		}
	}

	private static Vec3 screenCenter(ScreenComponent component) {
		if (component == null || component.frameCoords().isEmpty()) {
			return Vec3.ZERO;
		}
		double x = 0.0D;
		double y = 0.0D;
		double z = 0.0D;
		int count = 0;
		for (ItemFrame frame : component.frameCoords().keySet()) {
			if (frame == null) {
				continue;
			}
			x += frame.getX();
			y += frame.getY();
			z += frame.getZ();
			count++;
		}
		if (count <= 0) {
			return Vec3.ZERO;
		}
		return new Vec3(x / count, y / count, z / count);
	}

	private static List<MaxAvatarCandidateSnapshot> avatarCandidates(ScreenComponent component) {
		List<PersistedGalleryItem> persisted = resolvePersistedGalleryState(component);
		if (persisted.isEmpty()) {
			return List.of();
		}
		List<MaxAvatarCandidateSnapshot> candidates = new ArrayList<>();
		for (PersistedGalleryItem item : persisted) {
			if (item == null || effectiveGalleryItemKind(item) != GalleryItemKind.MEDIA || item.url() == null || item.url().isBlank()) {
				continue;
			}
			BufferedImage preview = persistedGalleryPreviewForDisplay(item, GalleryItemKind.MEDIA);
			if (preview == null) {
				continue;
			}
			candidates.add(new MaxAvatarCandidateSnapshot(
					item.title() == null || item.title().isBlank() ? "Аватар" : item.title(),
					item.url(),
					item.localMediaKey(),
					preview
			));
		}
		return candidates.isEmpty() ? List.of() : List.copyOf(candidates);
	}

	private static List<MaxRingtoneCandidateSnapshot> ringtoneCandidates(ScreenComponent component, MaxRuntimeState state) {
		List<MaxRingtoneCandidateSnapshot> candidates = new ArrayList<>();
		candidates.add(ringtoneCandidate(state, "Стандартный рингтон", "MAX", MAX_DEFAULT_RINGTONE_URL, ""));
		for (PersistedGalleryItem item : resolvePersistedGalleryState(component)) {
			if (item == null || effectiveGalleryItemKind(item) != GalleryItemKind.AUDIO) {
				continue;
			}
			String title = item.title() == null || item.title().isBlank() ? "Аудиотрек" : item.title();
			String subtitle = item.subtitle() == null || item.subtitle().isBlank() ? "Галерея" : item.subtitle();
			String url = item.url() == null || item.url().isBlank() ? "max:gallery:" + Objects.toString(item.localMediaKey(), title) : item.url();
			candidates.add(ringtoneCandidate(state, title, subtitle, url, item.localMediaKey()));
		}
		return List.copyOf(candidates);
	}

	private static MaxRingtoneCandidateSnapshot ringtoneCandidate(MaxRuntimeState state, String title, String subtitle, String url, String localMediaKey) {
		boolean selected;
		boolean playing;
		float fraction;
		synchronized (state) {
			String stateUrl = state.ringtoneUrl == null || state.ringtoneUrl.isBlank() ? MAX_DEFAULT_RINGTONE_URL : state.ringtoneUrl;
			String candidateUrl = url == null || url.isBlank() ? MAX_DEFAULT_RINGTONE_URL : url;
			selected = Objects.equals(stateUrl, candidateUrl)
					&& Objects.equals(Objects.toString(state.ringtoneLocalMediaKey, ""), Objects.toString(localMediaKey, ""));
			playing = state.ringtonePreviewPlaying
					&& Objects.equals(Objects.toString(state.ringtonePreviewUrl, MAX_DEFAULT_RINGTONE_URL), candidateUrl)
					&& Objects.equals(Objects.toString(state.ringtonePreviewLocalMediaKey, ""), Objects.toString(localMediaKey, ""));
			long elapsed = playing ? Math.max(0L, System.currentTimeMillis() - state.ringtonePreviewStartedAtMillis) : 0L;
			fraction = playing ? (elapsed % MAX_RINGTONE_PREVIEW_TIMELINE_MS) / (float) MAX_RINGTONE_PREVIEW_TIMELINE_MS : 0.0F;
		}
		return new MaxRingtoneCandidateSnapshot(title, subtitle, url, localMediaKey, selected, playing, fraction);
	}

	private static MonitorMediaApp.LoadedMedia loadAvatarMedia(ScreenComponent component, String avatarUrl, String localMediaKey) {
		String localKey = localMediaKey != null ? localMediaKey.trim() : "";
		if (localKey.isBlank() && avatarUrl != null && !avatarUrl.isBlank()) {
			for (PersistedGalleryItem item : resolvePersistedGalleryState(component)) {
				if (item != null && Objects.equals(item.url(), avatarUrl)) {
					localKey = item.localMediaKey() != null ? item.localMediaKey().trim() : "";
					break;
				}
			}
		}
		if (localKey.isBlank()) {
			return null;
		}
		try {
			return MonitorMediaApp.loadSavedGalleryMedia(localKey, null);
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to load MAX avatar {}: {}", localKey, sanitizeMediaError(exception.getMessage()));
			return null;
		}
	}

	private static BufferedImage avatarFrame(MonitorMediaApp.LoadedMedia media, int index, BufferedImage fallback) {
		BufferedImage frame = media != null && media.frameCount() > 0 ? media.frame(index) : null;
		return frame != null ? frame : fallback;
	}

	private static BufferedImage currentAvatarFrameLocked(MaxRuntimeState state) {
		if (state == null) {
			return null;
		}
		MonitorMediaApp.LoadedMedia media = state.avatarMedia;
		if (media == null || media.frameCount() <= 0) {
			return state.avatarFrame;
		}
		return avatarFrame(media, currentAvatarFrameIndexLocked(state, System.currentTimeMillis()), state.avatarFrame);
	}

	private static int currentAvatarFrameIndexLocked(MaxRuntimeState state, long nowMillis) {
		MonitorMediaApp.LoadedMedia media = state != null ? state.avatarMedia : null;
		if (media == null || media.frameCount() <= 1) {
			return 0;
		}
		long cycleMillis = avatarCycleMillis(media);
		if (cycleMillis <= 0L) {
			return 0;
		}
		long elapsedMillis = Math.max(0L, nowMillis - state.avatarAnimationStartedAtMillis);
		long cyclePosition = elapsedMillis % cycleMillis;
		long cursor = 0L;
		for (int index = 0; index < media.frameCount(); index++) {
			cursor += Math.max(20, media.delayMillis(index));
			if (cyclePosition < cursor) {
				return index;
			}
		}
		return Math.max(0, media.frameCount() - 1);
	}

	private static long avatarCycleMillis(MonitorMediaApp.LoadedMedia media) {
		if (media == null || media.frameCount() <= 0) {
			return 0L;
		}
		long total = 0L;
		for (int index = 0; index < media.frameCount(); index++) {
			total += Math.max(20, media.delayMillis(index));
		}
		return total;
	}

	private static boolean avatarAnimatedLocked(MaxRuntimeState state) {
		return state != null && state.avatarMedia != null && state.avatarMedia.frameCount() > 1;
	}

	private static boolean maxAnimatedAvatarsVisible(
			ScreenComponent component,
			MaxRuntimeState state,
			List<MaxContactSnapshot> contacts,
			MaxCallVisualSnapshot call,
			MaxIncomingFileSnapshot incomingFile
	) {
		if (component == null) {
			return false;
		}
		boolean maxVisible = component.viewMode() == ScreenViewMode.MAX || hasVisibleCall(component.runtimeKey());
		if (!maxVisible) {
			return false;
		}
		if (avatarAnimatedLocked(state)) {
			return true;
		}
		if (contacts != null) {
			for (MaxContactSnapshot contact : contacts) {
				if (contact != null && contact.avatarAnimated()) {
					return true;
				}
			}
		}
		if (incomingFile != null && incomingFile.senderAvatarAnimated()) {
			return true;
		}
		if (call != null) {
			if (call.peerAvatarAnimated()) {
				return true;
			}
			for (MaxCallParticipantSnapshot participant : call.participants()) {
				if (participant != null && participant.avatarAnimated()) {
					return true;
				}
			}
		}
		return false;
	}

	private static void scheduleAvatarAnimationRender(MinecraftServer server, ScreenRuntimeKey key, MaxRuntimeState state) {
		if (server == null || key == null || state == null) {
			return;
		}
		synchronized (state) {
			if (state.avatarRenderScheduled) {
				return;
			}
			state.avatarRenderScheduled = true;
		}
		ensureExecutors();
		mediaScheduler.schedule(() -> server.execute(() -> {
			MaxRuntimeState current = MAX_STATES.get(key);
			if (current != null) {
				synchronized (current) {
					current.avatarRenderScheduled = false;
				}
			}
			ScreenComponent component = resolveScreenComponent(server, key);
			if (component != null && (component.viewMode() == ScreenViewMode.MAX || hasVisibleCall(key))) {
				requestRuntimeRender(server, key);
			}
		}), MAX_AVATAR_ANIMATION_RENDER_DELAY_MS, TimeUnit.MILLISECONDS);
	}

	private static List<LiveCameraReference> connectedCameraReferences(MinecraftServer server, ScreenComponent component) {
		if (server == null || component == null || component.runtimeKey() == null) {
			return List.of();
		}
		ServerLevel level = server.getLevel(component.runtimeKey().dimension());
		if (level == null) {
			return List.of();
		}
		return collectConnectedCameraPositions(level, component);
	}

	private static int connectedCameraCount(List<LiveCameraReference> cameras) {
		if (cameras == null || cameras.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (LiveCameraReference camera : cameras) {
			if (camera != null && camera.sourceType() != LiveCameraSourceType.DRONE) {
				count++;
			}
		}
		return count;
	}

	private static int connectedDroneCount(List<LiveCameraReference> cameras) {
		if (cameras == null || cameras.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (LiveCameraReference camera : cameras) {
			if (camera != null && camera.sourceType() == LiveCameraSourceType.DRONE) {
				count++;
			}
		}
		return count;
	}

	private static List<MaxCameraOptionSnapshot> cameraOptions(MinecraftServer server, MaxRuntimeState state, List<LiveCameraReference> cameras) {
		if (cameras == null || cameras.isEmpty()) {
			return List.of();
		}
		String selected;
		synchronized (state) {
			selected = state.selectedCameraUrl;
		}
		List<MaxCameraOptionSnapshot> options = new ArrayList<>(cameras.size());
		for (LiveCameraReference camera : cameras) {
			String url = liveCameraGalleryUrl(camera);
			BlockPos pos = camera.pos();
			String title = camera.sourceType() == LiveCameraSourceType.DRONE ? "Дрон" : "Камера";
			String subtitle = pos != null ? formatLiveSourceCoordinates(pos) : "live";
			boolean online = isLiveCameraOnline(server, camera);
			options.add(new MaxCameraOptionSnapshot(
					title,
					subtitle,
					url,
					createLiveCameraPlaceholderPreview(title, subtitle, online, camera.sourceType()),
					Objects.equals(url, selected),
					online
			));
		}
		return List.copyOf(options);
	}

	private static List<MaxMicrophoneOptionSnapshot> microphoneOptions(MaxRuntimeState state, List<MicrophoneSystem.ScreenMicrophoneDevice> microphones) {
		synchronized (state) {
			return microphoneOptionsLocked(state, microphones);
		}
	}

	private static List<MaxMicrophoneOptionSnapshot> microphoneOptionsLocked(MaxRuntimeState state, List<MicrophoneSystem.ScreenMicrophoneDevice> microphones) {
		if (state == null || microphones == null || microphones.isEmpty()) {
			return List.of();
		}
		List<MaxMicrophoneOptionSnapshot> options = new ArrayList<>(microphones.size());
		String selectedKey = state.selectedMicrophoneKey;
		int selectedIndex = state.selectedMicrophoneIndex;
		for (MicrophoneSystem.ScreenMicrophoneDevice microphone : microphones) {
			String deviceKey = microphoneDeviceKey(microphone);
			boolean selected = selectedKey != null && !selectedKey.isBlank()
					? Objects.equals(selectedKey, deviceKey)
					: microphone.index() == selectedIndex;
			options.add(new MaxMicrophoneOptionSnapshot(
					microphone.index(),
					microphone.title(),
					microphone.subtitle(),
					deviceKey,
					selected,
					true
			));
		}
		return List.copyOf(options);
	}

	private static int selectedCameraIndex(MaxRuntimeState state, List<MaxCameraOptionSnapshot> options) {
		if (state == null || options == null || options.isEmpty()) {
			return -1;
		}
		String selected;
		synchronized (state) {
			selected = state.selectedCameraUrl;
		}
		for (int index = 0; index < options.size(); index++) {
			if (Objects.equals(options.get(index).url(), selected)) {
				return index;
			}
		}
		return -1;
	}

	private static int normalizeSelectedMicrophoneLocked(MaxRuntimeState state, List<MicrophoneSystem.ScreenMicrophoneDevice> microphones) {
		if (state == null || microphones == null || microphones.isEmpty()) {
			if (state != null) {
				state.selectedMicrophoneKey = "";
				state.selectedMicrophoneIndex = -1;
			}
			return -1;
		}
		if ((state.selectedMicrophoneKey == null || state.selectedMicrophoneKey.isBlank())
				&& state.selectedMicrophoneIndex >= 0
				&& state.selectedMicrophoneIndex < microphones.size()) {
			state.selectedMicrophoneKey = microphoneDeviceKey(microphones.get(state.selectedMicrophoneIndex));
		}
		if (state.selectedMicrophoneKey != null && !state.selectedMicrophoneKey.isBlank()) {
			for (MicrophoneSystem.ScreenMicrophoneDevice microphone : microphones) {
				if (Objects.equals(state.selectedMicrophoneKey, microphoneDeviceKey(microphone))) {
					state.selectedMicrophoneIndex = microphone.index();
					return state.selectedMicrophoneIndex;
				}
			}
			state.selectedMicrophoneKey = "";
			state.selectedMicrophoneIndex = -1;
			return -1;
		}
		state.selectedMicrophoneIndex = -1;
		return -1;
	}

	private static String microphoneDeviceKey(MicrophoneSystem.ScreenMicrophoneDevice microphone) {
		if (microphone == null || microphone.dimension() == null || microphone.pos() == null) {
			return "";
		}
		BlockPos pos = microphone.pos();
		return microphone.dimension().identifier() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
	}

	private static MaxCallSession currentCall(ScreenRuntimeKey key) {
		UUID callId = key != null ? CALL_BY_SCREEN.get(key) : null;
		return callId != null ? CALLS_BY_ID.get(callId) : null;
	}

	private static MaxCallPhase callPhase(MaxCallSession call, ScreenRuntimeKey key) {
		if (call == null || key == null) {
			return MaxCallPhase.IDLE;
		}
		if (call.isRinging(key)) {
			return MaxCallPhase.INCOMING;
		}
		if (call.accepted && call.isAccepted(key)) {
			return MaxCallPhase.ACTIVE;
		}
		return Objects.equals(call.caller, key) ? MaxCallPhase.OUTGOING : MaxCallPhase.INCOMING;
	}

	private static String generateAccountCode() {
		for (int attempt = 0; attempt < 100; attempt++) {
			String code = "MAX-" + String.format(Locale.ROOT, "%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000));
			if (!ACCOUNT_INDEX.containsKey(code)) {
				return code;
			}
		}
		return "MAX-" + String.format(Locale.ROOT, "%06d", Math.floorMod(UUID.randomUUID().hashCode(), 1_000_000));
	}

	private static String normalizeAccountCode(String code) {
		if (code == null) {
			return "";
		}
		String compact = code.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("#", "");
		if (compact.matches("\\d{6}")) {
			return "MAX-" + compact;
		}
		if (compact.matches("MAX\\d{6}")) {
			return "MAX-" + compact.substring(3);
		}
		if (compact.matches("MAX-\\d{6}")) {
			return compact;
		}
		return "";
	}

	private static boolean sanitizeContactsLocked(MaxRuntimeState state) {
		if (state == null || state.contacts.isEmpty()) {
			return false;
		}
		boolean changed = false;
		Set<String> seen = new HashSet<>();
		for (int index = 0; index < state.contacts.size(); ) {
			String normalized = normalizeAccountCode(state.contacts.get(index));
			if (normalized.isBlank() || Objects.equals(normalized, state.accountCode) || !seen.add(normalized)) {
				state.contacts.remove(index);
				changed = true;
				continue;
			}
			if (!Objects.equals(normalized, state.contacts.get(index))) {
				state.contacts.set(index, normalized);
				changed = true;
			}
			index++;
		}
		return changed;
	}

	private static String displayAccountCode(String code) {
		String normalized = normalizeAccountCode(code);
		if (normalized.startsWith("MAX-") && normalized.length() == 10) {
			return normalized.substring(4);
		}
		return code == null ? "" : code;
	}

	private static void sendCopyCodeMessage(ServerPlayer player, String code) {
		if (player == null || code == null || code.isBlank()) {
			return;
		}
		String displayCode = displayAccountCode(code);
		Component copy = Component.literal(displayCode)
				.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withBold(true)
						.withUnderlined(true)
						.withClickEvent(new ClickEvent.CopyToClipboard(displayCode)));
		player.sendSystemMessage(copy);
	}

	private static Path defaultRingtonePath() {
		if (Files.isRegularFile(DEFAULT_PROJECT_RINGTONE)) {
			return DEFAULT_PROJECT_RINGTONE;
		}
		if (Files.isRegularFile(DEFAULT_SOURCE_RINGTONE)) {
			return DEFAULT_SOURCE_RINGTONE;
		}
		return null;
	}

	private static String ringtoneSourceForScreen(ScreenRuntimeKey key) {
		MaxRuntimeState state = key != null ? MAX_STATES.get(key) : null;
		if (state != null) {
			synchronized (state) {
				String source = ringtoneSource(state.ringtoneUrl, state.ringtoneLocalMediaKey);
				if (source != null && !source.isBlank()) {
					return source;
				}
			}
		}
		Path fallback = defaultRingtonePath();
		return fallback != null ? fallback.toString() : "";
	}

	private static String ringtoneSource(String url, String localMediaKey) {
		String localKey = localMediaKey != null ? localMediaKey.trim() : "";
		if (!localKey.isBlank()) {
			Path saved = MonitorMediaApp.savedGalleryMediaFile(localKey);
			if (saved != null) {
				return saved.toAbsolutePath().toString();
			}
		}
		String normalizedUrl = url != null ? url.trim() : "";
		if (normalizedUrl.isBlank() || Objects.equals(normalizedUrl, MAX_DEFAULT_RINGTONE_URL)) {
			Path fallback = defaultRingtonePath();
			return fallback != null ? fallback.toString() : "";
		}
		if (normalizedUrl.startsWith("max:gallery:")) {
			return "";
		}
		return normalizedUrl;
	}

	private static SpeakerAudioSource previewRingtoneSource(ScreenRuntimeKey key, MaxRuntimeState state) {
		if (key == null || state == null) {
			return null;
		}
		boolean playing;
		String url;
		String localMediaKey;
		long startedAtMillis;
		synchronized (state) {
			playing = state.ringtonePreviewPlaying;
			url = state.ringtonePreviewUrl;
			localMediaKey = state.ringtonePreviewLocalMediaKey;
			startedAtMillis = state.ringtonePreviewStartedAtMillis;
		}
		if (!playing) {
			return null;
		}
		String source = ringtoneSource(url, localMediaKey);
		if (source == null || source.isBlank()) {
			return null;
		}
		String sourceKey = MAX_RINGTONE_PREVIEW_SOURCE_PREFIX + componentGroupId(key);
		return new SpeakerAudioSource(
				sourceKey,
				sourceKey,
				source,
				0L,
				startedAtMillis,
				false,
				false,
				false,
				false,
				true
		);
	}

	private static String maxVideoStreamOwnerId(ScreenRuntimeKey key) {
		return "max-call|" + liveCameraStreamOwnerId(key);
	}

	private static String maxLocalVideoStreamOwnerId(ScreenRuntimeKey key) {
		return "max-call-local|" + liveCameraStreamOwnerId(key);
	}

	private static void drawMaxFeedScreen(Graphics2D graphics, UiLayout layout, MonitorApp app, MaxVisualSnapshot state) {
		UiRect header = maxProfilePanelRect(layout);
		fillRoundedRect(graphics, header, clampInt(layout.unit() * 2, 14, 28), new Color(6, 10, 14, 172));
		strokeRoundedRect(graphics, header, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 52));
		drawAvatar(graphics, maxProfileAvatarRect(layout), state.avatarFrame(), layout);
		drawVerticalText(graphics, "MAX", maxAppTitleRect(layout), new Color(248, 251, 255, 240), Font.BOLD, clampInt(layout.unit() + 2, 13, 22));
		drawVerticalText(graphics, displayAccountCode(state.accountCode()), maxProfileCodeRect(layout), new Color(214, 232, 244, 232), Font.BOLD, clampInt(layout.unit(), 10, 17));
		drawMaxAddContactButton(graphics, maxAddContactRect(layout), layout);
		drawMaxNotificationsButton(graphics, maxNotificationsRect(layout), state.notificationCount(), layout);
		drawMaxRingtoneControls(graphics, layout, state);

		UiRect listRect = maxContactListRect(layout);
		List<MaxContactSnapshot> contacts = state.contacts();
		if (contacts.isEmpty()) {
			drawMaxEmptyContacts(graphics, layout, listRect);
		} else {
			int count = Math.min(contacts.size(), maxVisibleContactRows(layout));
			for (int index = 0; index < count; index++) {
				drawMaxContactRow(graphics, layout, maxContactRowRect(layout, index), contacts.get(index), true);
			}
		}
		if (state.statusText() != null && !state.statusText().isBlank()) {
			drawCenteredTextFitted(graphics, state.statusText(), maxStatusRect(layout), new Color(210, 232, 244, 224), Font.BOLD, clampInt(layout.unit() - 1, 8, 13), 6);
		}
	}

	private static void drawMaxCallScreen(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state) {
		MaxCallVisualSnapshot call = state.call();
		if (call.phase() == MaxCallPhase.INCOMING) {
			drawMaxIncomingCallScreen(graphics, layout, call);
			return;
		}
		if (call.phase() == MaxCallPhase.OUTGOING) {
			drawMaxOutgoingCallScreen(graphics, layout, call);
			return;
		}
		drawMaxActiveCallScreen(graphics, layout, state, call);
	}

	private static void drawMaxIncomingCallScreen(Graphics2D graphics, UiLayout layout, MaxCallVisualSnapshot call) {
		UiRect canvas = mediaCanvasRect(layout);
		drawAvatarBackdrop(graphics, canvas, call.peerAvatarFrame(), call.peerCode());
		UiRect avatar = maxIncomingAvatarRect(layout);
		drawAvatar(graphics, avatar, call.peerAvatarFrame(), layout);
		drawCenteredTextFitted(graphics, displayAccountCode(call.peerCode()), maxIncomingCodeRect(layout), new Color(248, 251, 255, 246), Font.BOLD, clampInt(layout.unit() + 4, 16, 28), 8);
		drawCenteredTextFitted(graphics, "Входящий вызов MAX", maxIncomingSubtitleRect(layout), new Color(221, 235, 244, 224), Font.PLAIN, clampInt(layout.unit(), 10, 15), 6);
		drawRoundCallButton(graphics, maxIncomingAcceptRect(layout), PlayerUiIcon.CALL_ACCEPT, new Color(74, 214, 142), new Color(8, 18, 13, 238), layout);
		drawRoundCallButton(graphics, maxIncomingDeclineRect(layout), PlayerUiIcon.CALL_DECLINE, new Color(240, 88, 96), new Color(255, 248, 248, 246), layout);
	}

	private static void drawMaxOutgoingCallScreen(Graphics2D graphics, UiLayout layout, MaxCallVisualSnapshot call) {
		UiRect canvas = mediaCanvasRect(layout);
		drawAvatarBackdrop(graphics, canvas, call.peerAvatarFrame(), call.peerCode());
		drawAvatar(graphics, maxIncomingAvatarRect(layout), call.peerAvatarFrame(), layout);
		drawCenteredTextFitted(graphics, displayAccountCode(call.peerCode()), maxIncomingCodeRect(layout), new Color(248, 251, 255, 246), Font.BOLD, clampInt(layout.unit() + 4, 16, 28), 8);
		drawCenteredTextFitted(graphics, "Ожидание ответа", maxIncomingSubtitleRect(layout), new Color(221, 235, 244, 224), Font.PLAIN, clampInt(layout.unit(), 10, 15), 6);
		drawRoundCallButton(graphics, maxOutgoingCancelRect(layout), PlayerUiIcon.CALL_DECLINE, new Color(240, 88, 96), new Color(255, 248, 248, 246), layout);
	}

	private static void drawMaxActiveCallScreen(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state, MaxCallVisualSnapshot call) {
		UiRect canvas = mediaCanvasRect(layout);
		graphics.setPaint(new GradientPaint(canvas.x(), canvas.y(), new Color(10, 12, 18), canvas.right(), canvas.bottom(), new Color(20, 26, 36)));
		graphics.fillRect(canvas.x(), canvas.y(), canvas.width(), canvas.height());
		List<MaxCallParticipantSnapshot> participants = maxCallParticipants(state, call);
		boolean focused = call.selfFocused() || call.peerFocused();
		if (focused) {
			MaxCallParticipantSnapshot focusedParticipant = maxCallFocusedParticipant(state, call, participants);
			UiRect focusedRect = maxCallFocusedTileRect(layout);
			drawMaxParticipantTile(graphics, layout, focusedRect, focusedParticipant, true, MediaScaleMode.FILL);
			if (call.menuOpen()) {
				List<MaxCallParticipantSnapshot> miniParticipants = participants.stream()
						.filter(participant -> !sameMaxParticipant(participant, focusedParticipant))
						.toList();
				int count = Math.min(miniParticipants.size(), maxCallMiniParticipantCapacity(layout));
				for (int index = 0; index < count; index++) {
					drawMaxParticipantTile(graphics, layout, maxCallMiniParticipantRect(layout, index), miniParticipants.get(index), false, MediaScaleMode.FILL);
				}
			}
		} else {
			int count = participants.size();
			for (int index = 0; index < count; index++) {
				drawMaxParticipantTile(graphics, layout, maxCallParticipantTileRect(layout, count, index, call.menuOpen()), participants.get(index), false, MediaScaleMode.FILL);
			}
		}
		if (call.menuOpen()) {
			drawMaxCallMenu(graphics, layout, call);
		}
		if (call.cameraPickerOpen()) {
			drawMaxCameraPicker(graphics, layout, call);
		} else if (call.contactPickerOpen()) {
			drawMaxContactPicker(graphics, layout, state, call);
		}
	}

	private static List<MaxCallParticipantSnapshot> maxCallParticipants(MaxVisualSnapshot state, MaxCallVisualSnapshot call) {
		if (call.participants() != null && !call.participants().isEmpty()) {
			return call.participants();
		}
		List<MaxCallParticipantSnapshot> fallback = new ArrayList<>(2);
		fallback.add(new MaxCallParticipantSnapshot(
				state.accountCode(),
				state.avatarFrame(),
				state.animatedAvatars(),
				call.localPreviewFrame(),
				true,
				call.cameraEnabled(),
				call.microphoneEnabled(),
				false
		));
		if (call.peerCode() != null && !call.peerCode().isBlank()) {
			fallback.add(new MaxCallParticipantSnapshot(
					call.peerCode(),
					call.peerAvatarFrame(),
					call.peerAvatarAnimated(),
					call.remoteFrame(),
					false,
					call.remoteFrame() != null,
					true,
					false
			));
		}
		return List.copyOf(fallback);
	}

	private static MaxCallParticipantSnapshot maxCallFocusedParticipant(MaxVisualSnapshot state, MaxCallVisualSnapshot call, List<MaxCallParticipantSnapshot> participants) {
		String focusedCode = call.focusedParticipantCode();
		if (focusedCode != null && !focusedCode.isBlank()) {
			for (MaxCallParticipantSnapshot participant : participants) {
				if (participant != null && Objects.equals(participant.code(), focusedCode)) {
					return participant;
				}
			}
		}
		if (call.selfFocused()) {
			for (MaxCallParticipantSnapshot participant : participants) {
				if (participant != null && participant.self()) {
					return participant;
				}
			}
		}
		for (MaxCallParticipantSnapshot participant : participants) {
			if (participant != null && !participant.self()) {
				return participant;
			}
		}
		return new MaxCallParticipantSnapshot(state.accountCode(), state.avatarFrame(), state.animatedAvatars(), call.localPreviewFrame(), true, call.cameraEnabled(), call.microphoneEnabled(), false);
	}

	private static boolean sameMaxParticipant(MaxCallParticipantSnapshot left, MaxCallParticipantSnapshot right) {
		if (left == null || right == null) {
			return false;
		}
		return left.self() == right.self() && Objects.equals(left.code(), right.code());
	}

	private static void drawMaxParticipantTile(
			Graphics2D graphics,
			UiLayout layout,
			UiRect rect,
			MaxCallParticipantSnapshot participant,
			boolean focused,
			MediaScaleMode scaleMode
	) {
		if (participant == null) {
			return;
		}
		drawMaxParticipantTile(
				graphics,
				layout,
				rect,
				displayAccountCode(participant.code()),
				participant.avatarFrame(),
				participant.videoFrame(),
				focused,
				participant.cameraEnabled(),
				participant.microphoneEnabled(),
				scaleMode
		);
	}

	private static void drawMaxParticipantTile(
			Graphics2D graphics,
			UiLayout layout,
			UiRect rect,
			String code,
			BufferedImage avatar,
			BufferedImage video,
			boolean focused,
			boolean cameraEnabled,
			boolean microphoneEnabled,
			MediaScaleMode scaleMode
	) {
		if (rect.width() <= 1 || rect.height() <= 1) {
			return;
		}
		int arc = clampInt(focused ? layout.unit() * 2 : layout.unit() + 8, 12, focused ? 30 : 22);
		Shape previousClip = graphics.getClip();
		Shape shape = roundedRectShape(rect, arc, arc, arc, arc);
		graphics.setClip(shape);
		if (cameraEnabled && video != null) {
			drawScaledImage(graphics, video, rect, scaleMode);
		} else {
			Color accent = participantAccent(code, avatar);
			graphics.setColor(accent);
			graphics.fillRect(rect.x(), rect.y(), rect.width(), rect.height());
			int maxAvatar = Math.max(12, Math.min(rect.width(), rect.height()) - 6);
			int avatarSize = clampInt(Math.min(rect.width(), rect.height()) / (focused ? 4 : 3), Math.min(18, maxAvatar), Math.min(focused ? 116 : 72, maxAvatar));
			UiRect avatarRect = new UiRect(rect.x() + (rect.width() - avatarSize) / 2, rect.y() + (rect.height() - avatarSize) / 2, avatarSize, avatarSize);
			drawAvatar(graphics, avatarRect, avatar, layout);
		}
		graphics.setClip(previousClip);
		strokeRoundedRect(graphics, rect, arc, 1.0F, new Color(255, 255, 255, focused ? 70 : 46));
		drawMaxParticipantLabel(graphics, layout, rect, code, focused);
		if (!microphoneEnabled && rect.width() >= 20 && rect.height() >= 20) {
			int micSize = clampInt(Math.min(rect.width(), rect.height()) / 5, 12, 30);
			int inset = Math.max(2, layout.unit() / 2);
			UiRect micOff = new UiRect(rect.right() - inset - micSize, rect.y() + inset, micSize, micSize);
			fillRoundedRect(graphics, micOff, micOff.height(), new Color(0, 0, 0, 108));
			drawPlayerUiIcon(graphics, mediaChromeIconRect(micOff, layout), PlayerUiIcon.MIC_OFF, new Color(248, 251, 255, 230));
		}
	}

	private static void drawMaxParticipantLabel(Graphics2D graphics, UiLayout layout, UiRect tile, String code, boolean focused) {
		if (code == null || code.isBlank() || tile.width() < 16 || tile.height() < 12) {
			return;
		}
		int textSize = clampInt(layout.unit() - (focused ? 0 : 2), tile.height() < 32 ? 6 : 7, focused ? 13 : 11);
		Font font = new Font(Font.SANS_SERIF, Font.BOLD, textSize);
		FontMetrics metrics = graphics.getFontMetrics(font);
		int padX = Math.max(3, layout.unit() / 3);
		int padY = Math.max(2, layout.unit() / 5);
		int maxWidth = Math.max(8, tile.width() - padX * 4);
		String labelText = truncateWithEllipsis(metrics, code, maxWidth);
		int labelWidth = Math.min(tile.width() - padX * 2, metrics.stringWidth(labelText) + padX * 2);
		int labelHeight = Math.min(tile.height(), metrics.getHeight() + padY * 2);
		int x = tile.x() + padX;
		int y = tile.bottom() - labelHeight - padX;
		UiRect label = new UiRect(x, Math.max(tile.y(), y), Math.max(1, labelWidth), Math.max(1, labelHeight));
		fillRoundedRect(graphics, label, label.height(), new Color(0, 0, 0, 92));
		graphics.setFont(font);
		graphics.setColor(new Color(248, 251, 255, 238));
		graphics.drawString(labelText, label.x() + padX, label.y() + (label.height() - metrics.getHeight()) / 2 + metrics.getAscent());
	}

	private static void drawMaxCallMenu(Graphics2D graphics, UiLayout layout, MaxCallVisualSnapshot call) {
		UiRect dock = maxCallMenuDockRect(layout, call);
		fillRoundedRect(graphics, dock, dock.height(), new Color(8, 10, 14, 126));
		strokeRoundedRect(graphics, dock, dock.height(), 1.0F, new Color(255, 255, 255, 42));
		drawCallSegmentButton(graphics, maxCallMicrophoneToggleRect(layout, call), call.microphoneEnabled() ? PlayerUiIcon.MIC : PlayerUiIcon.MIC_OFF, call.microphoneEnabled(), call.microphoneCount() > 1 ? MediaButtonSegment.LEFT : MediaButtonSegment.SINGLE, layout);
		if (call.microphoneCount() > 1) {
			drawCallSegmentButton(graphics, maxCallMicrophoneSelectRect(layout, call), PlayerUiIcon.DEVICE_SELECT, false, MediaButtonSegment.RIGHT, layout);
		}
		drawCallSegmentButton(graphics, maxCallCameraToggleRect(layout, call), call.cameraEnabled() ? PlayerUiIcon.VIDEO_CAMERA : PlayerUiIcon.VIDEO_CAMERA_OFF, call.cameraEnabled(), MediaButtonSegment.LEFT, layout);
		drawCallSegmentButton(graphics, maxCallCameraSelectRect(layout, call), PlayerUiIcon.DEVICE_SELECT, false, MediaButtonSegment.RIGHT, layout);
		drawCallSegmentButton(graphics, maxCallInviteRect(layout, call), PlayerUiIcon.CONTACT_ADD, false, MediaButtonSegment.SINGLE, layout);
		if (call.selfFocused() || call.peerFocused()) {
			drawCallSegmentButton(graphics, maxCallExitFullscreenRect(layout, call), PlayerUiIcon.FULLSCREEN_EXIT, false, MediaButtonSegment.SINGLE, layout);
		}
		drawRoundCallButton(graphics, maxCallLeaveRect(layout, call), PlayerUiIcon.CALL_DECLINE, new Color(240, 88, 96), new Color(255, 248, 248, 246), layout);
	}

	private static void drawCallSegmentButton(Graphics2D graphics, UiRect rect, PlayerUiIcon icon, boolean active, MediaButtonSegment segment, UiLayout layout) {
		Color iconColor = drawSmallMediaButtonBase(graphics, rect, segment, active, mediaChromeStrokeWidth(rect), active ? null : new Color(255, 255, 255, 0));
		drawPlayerUiIcon(graphics, mediaChromeIconRect(rect, layout), icon, iconColor);
	}

	private static void drawRoundCallButton(Graphics2D graphics, UiRect rect, PlayerUiIcon icon, Color fill, Color iconColor, UiLayout layout) {
		fillRoundedRect(graphics, rect, rect.height(), fill);
		strokeRoundedRect(graphics, rect, rect.height(), 1.0F, new Color(255, 255, 255, 80));
		drawPlayerUiIcon(graphics, mediaChromeIconRect(rect, layout), icon, iconColor);
	}

	private static void drawAvatarBackdrop(Graphics2D graphics, UiRect canvas, BufferedImage avatar, String code) {
		Color accent = participantAccent(code, avatar);
		if (avatar != null) {
			drawScaledImage(graphics, avatar, canvas, MediaScaleMode.FILL);
			graphics.setColor(new Color(0, 0, 0, 134));
			graphics.fillRect(canvas.x(), canvas.y(), canvas.width(), canvas.height());
		}
		graphics.setPaint(new GradientPaint(canvas.x(), canvas.y(), withAlpha(brighten(accent, 14), avatar != null ? 144 : 255), canvas.right(), canvas.bottom(), withAlpha(darken(accent, 50), avatar != null ? 178 : 255)));
		graphics.fillRect(canvas.x(), canvas.y(), canvas.width(), canvas.height());
		graphics.setPaint(new GradientPaint(canvas.x(), canvas.y(), new Color(255, 255, 255, 34), canvas.x(), canvas.bottom(), new Color(0, 0, 0, 106)));
		graphics.fillRect(canvas.x(), canvas.y(), canvas.width(), canvas.height());
	}

	private static Color participantAccent(String code, BufferedImage avatar) {
		if (avatar != null) {
			long r = 0L;
			long g = 0L;
			long b = 0L;
			long count = 0L;
			int stepX = Math.max(1, avatar.getWidth() / 12);
			int stepY = Math.max(1, avatar.getHeight() / 12);
			for (int y = 0; y < avatar.getHeight(); y += stepY) {
				for (int x = 0; x < avatar.getWidth(); x += stepX) {
					int argb = avatar.getRGB(x, y);
					int alpha = (argb >>> 24) & 0xFF;
					if (alpha < 32) {
						continue;
					}
					r += (argb >>> 16) & 0xFF;
					g += (argb >>> 8) & 0xFF;
					b += argb & 0xFF;
					count++;
				}
			}
			if (count > 0L) {
				return new Color(clampInt((int) (r / count), 38, 210), clampInt((int) (g / count), 42, 210), clampInt((int) (b / count), 52, 220));
			}
		}
		int hash = Math.abs(Objects.toString(code, "MAX").hashCode());
		float hue = (hash % 360) / 360.0F;
		return Color.getHSBColor(hue, 0.48F, 0.64F);
	}

	private static Color brighten(Color color, int amount) {
		return new Color(clampInt(color.getRed() + amount, 0, 255), clampInt(color.getGreen() + amount, 0, 255), clampInt(color.getBlue() + amount, 0, 255), color.getAlpha());
	}

	private static Color darken(Color color, int amount) {
		return new Color(clampInt(color.getRed() - amount, 0, 255), clampInt(color.getGreen() - amount, 0, 255), clampInt(color.getBlue() - amount, 0, 255), color.getAlpha());
	}

	private static Color withAlpha(Color color, int alpha) {
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampInt(alpha, 0, 255));
	}

	private static void drawMaxAvatarPicker(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		fillRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), new Color(6, 10, 14, 224));
		strokeRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 50));
		drawMediaBackButton(graphics, maxAvatarPickerBackRect(layout), layout);
		drawVerticalText(graphics, "ВЫБЕРИ АВАТАР", maxAvatarPickerTitleRect(layout), new Color(248, 251, 255, 236), Font.BOLD, clampInt(layout.unit(), 10, 16));
		List<MaxAvatarCandidateSnapshot> candidates = state.avatarCandidates();
		if (candidates.isEmpty()) {
			drawCenteredText(graphics, "В галерее нет картинок или GIF", maxAvatarPickerGridRect(layout), new Color(210, 224, 236, 224), Font.BOLD, clampInt(layout.unit(), 9, 14));
			return;
		}
		int count = Math.min(candidates.size(), maxAvatarPickerCapacity(layout));
		for (int index = 0; index < count; index++) {
			UiRect rect = maxAvatarCandidateRect(layout, index);
			MaxAvatarCandidateSnapshot candidate = candidates.get(index);
			fillRoundedRect(graphics, rect, clampInt(layout.unit(), 8, 16), new Color(255, 255, 255, 18));
			if (candidate.preview() != null) {
				drawScaledImage(graphics, candidate.preview(), rect.inset(Math.max(2, layout.unit() / 4)), MediaScaleMode.FILL);
			}
		}
	}

	private static void drawMaxCameraPicker(Graphics2D graphics, UiLayout layout, MaxCallVisualSnapshot call) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		fillRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), new Color(6, 10, 14, 230));
		strokeRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 54));
		drawMediaBackButton(graphics, maxAvatarPickerBackRect(layout), layout);
		UiRect title = maxAvatarPickerTitleRect(layout);
		drawVerticalText(graphics, "УСТРОЙСТВА", new UiRect(title.x(), title.y(), title.width() / 2, title.height()), new Color(248, 251, 255, 236), Font.BOLD, clampInt(layout.unit(), 10, 16));
		drawCenteredTextFitted(graphics, maxCallDeviceCountLabel(call), new UiRect(title.x() + title.width() / 2, title.y(), title.width() / 2, title.height()), new Color(188, 204, 218, 224), Font.PLAIN, clampInt(layout.unit() - 2, 7, 11), 5);

		UiRect cameraTitle = maxCallDeviceCameraTitleRect(layout);
		drawVerticalText(graphics, "КАМЕРЫ " + call.connectedCameraCount() + " · ДРОНЫ " + call.connectedDroneCount(), cameraTitle, new Color(188, 204, 218, 224), Font.BOLD, clampInt(layout.unit() - 2, 7, 11));
		List<MaxCameraOptionSnapshot> cameras = call.cameras();
		int cameraCapacity = maxCallDeviceCameraCapacity(layout);
		int cameraScroll = clampInt(call.cameraScroll(), 0, Math.max(0, (cameras == null ? 0 : cameras.size()) - cameraCapacity));
		if (cameras != null && cameras.size() > cameraCapacity) {
			drawMaxDeviceScrollStatus(graphics, cameraTitle, layout, cameraScroll, cameraCapacity, cameras.size());
			drawMaxDeviceScrollButton(graphics, maxCallDeviceCameraScrollLeftRect(layout), "<", layout, cameraScroll > 0);
			drawMaxDeviceScrollButton(graphics, maxCallDeviceCameraScrollRightRect(layout), ">", layout, cameraScroll + cameraCapacity < cameras.size());
		}
		if (cameras == null || cameras.isEmpty()) {
			drawCenteredText(graphics, "Подключи камеру или дрон к экрану", maxCallDeviceCameraGridRect(layout), new Color(210, 224, 236, 224), Font.BOLD, clampInt(layout.unit(), 9, 14));
		} else {
			int count = Math.min(Math.max(0, cameras.size() - cameraScroll), cameraCapacity);
			for (int visibleIndex = 0; visibleIndex < count; visibleIndex++) {
				int index = cameraScroll + visibleIndex;
				MaxCameraOptionSnapshot camera = cameras.get(index);
				UiRect rect = maxCallDeviceCameraRect(layout, visibleIndex);
				fillRoundedRect(graphics, rect, clampInt(layout.unit(), 8, 16), new Color(255, 255, 255, camera.selected() ? 34 : 18));
				if (camera.preview() != null) {
					drawScaledImage(graphics, camera.preview(), rect.inset(Math.max(2, layout.unit() / 4)), MediaScaleMode.FILL);
				}
				if (camera.selected()) {
					strokeRoundedRect(graphics, rect, clampInt(layout.unit(), 8, 16), 1.5F, new Color(255, 255, 255, 172));
				}
				UiRect label = new UiRect(rect.x() + layout.unit() / 2, rect.bottom() - clampInt(layout.unit() * 3, 24, 38), rect.width() - layout.unit(), clampInt(layout.unit() * 2, 18, 28));
				fillRoundedRect(graphics, label, label.height(), new Color(0, 0, 0, 112));
				drawCenteredTextFitted(graphics, camera.title() + " " + camera.subtitle(), label.inset(2), camera.online() ? new Color(248, 251, 255, 238) : new Color(248, 251, 255, 136), Font.BOLD, clampInt(layout.unit() - 2, 7, 11), 6);
			}
		}

		UiRect microphoneTitle = maxCallDeviceMicrophoneTitleRect(layout);
		drawVerticalText(graphics, "МИКРОФОНЫ", microphoneTitle, new Color(188, 204, 218, 224), Font.BOLD, clampInt(layout.unit() - 2, 7, 11));
		List<MaxMicrophoneOptionSnapshot> microphones = call.microphones();
		int microphoneCapacity = maxCallDeviceMicrophoneCapacity(layout);
		int microphoneScroll = clampInt(call.microphoneScroll(), 0, Math.max(0, (microphones == null ? 0 : microphones.size()) - microphoneCapacity));
		if (microphones != null && microphones.size() > microphoneCapacity) {
			drawMaxDeviceScrollStatus(graphics, microphoneTitle, layout, microphoneScroll, microphoneCapacity, microphones.size());
			drawMaxDeviceScrollButton(graphics, maxCallDeviceMicrophoneScrollUpRect(layout), "^", layout, microphoneScroll > 0);
			drawMaxDeviceScrollButton(graphics, maxCallDeviceMicrophoneScrollDownRect(layout), "v", layout, microphoneScroll + microphoneCapacity < microphones.size());
		}
		if (microphones == null || microphones.isEmpty()) {
			drawCenteredText(graphics, "Подключи микрофон к экрану", maxCallDeviceMicrophoneListRect(layout), new Color(210, 224, 236, 224), Font.BOLD, clampInt(layout.unit(), 9, 14));
			return;
		}
		int microphoneCount = Math.min(Math.max(0, microphones.size() - microphoneScroll), microphoneCapacity);
		for (int visibleIndex = 0; visibleIndex < microphoneCount; visibleIndex++) {
			int index = microphoneScroll + visibleIndex;
			drawMaxDeviceMicrophoneRow(graphics, layout, maxCallDeviceMicrophoneRowRect(layout, visibleIndex), microphones.get(index));
		}
	}

	private static void drawMaxDeviceMicrophoneRow(Graphics2D graphics, UiLayout layout, UiRect rect, MaxMicrophoneOptionSnapshot microphone) {
		int arc = clampInt(layout.unit(), 8, 16);
		fillRoundedRect(graphics, rect, arc, new Color(255, 255, 255, microphone.selected() ? 30 : 14));
		strokeRoundedRect(graphics, rect, arc, 1.0F, microphone.selected() ? new Color(255, 255, 255, 128) : new Color(255, 255, 255, 42));
		int iconSize = clampInt(layout.unit() * 2, 18, 28);
		int gap = Math.max(4, layout.unit() / 2);
		UiRect check = new UiRect(rect.x() + gap, rect.y() + (rect.height() - iconSize) / 2, iconSize, iconSize);
		Color checkColor = drawSmallMediaButtonBase(graphics, check, MediaButtonSegment.SINGLE, microphone.selected(), mediaChromeStrokeWidth(check));
		if (microphone.selected()) {
			drawPlayerUiIcon(graphics, mediaChromeIconRect(check, layout), PlayerUiIcon.CHECK, checkColor);
		}
		UiRect micIcon = new UiRect(check.right() + gap, rect.y() + (rect.height() - iconSize) / 2, iconSize, iconSize);
		drawPlayerUiIcon(graphics, micIcon.inset(Math.max(2, layout.unit() / 5)), PlayerUiIcon.MIC, new Color(238, 244, 250, 214));
		UiRect textRect = new UiRect(micIcon.right() + gap, rect.y() + Math.max(2, layout.unit() / 4), Math.max(8, rect.right() - micIcon.right() - gap * 2), rect.height() - Math.max(4, layout.unit() / 2));
		int titleHeight = Math.max(10, textRect.height() / 2);
		drawWrappedText(graphics, microphone.title(), new UiRect(textRect.x(), textRect.y(), textRect.width(), titleHeight), new Color(248, 251, 255, 232), Font.BOLD, clampInt(layout.unit() - 1, 8, 13), 1);
		drawWrappedText(graphics, microphone.subtitle(), new UiRect(textRect.x(), textRect.y() + titleHeight, textRect.width(), textRect.height() - titleHeight), new Color(178, 194, 210, 214), Font.PLAIN, clampInt(layout.unit() - 2, 7, 11), 1);
	}

	private static void drawMaxDeviceScrollButton(Graphics2D graphics, UiRect rect, String label, UiLayout layout, boolean enabled) {
		Color color = drawSmallMediaButtonBase(graphics, rect, MediaButtonSegment.SINGLE, false, mediaChromeStrokeWidth(rect));
		drawCenteredText(graphics, label, rect, enabled ? color : new Color(color.getRed(), color.getGreen(), color.getBlue(), 96), Font.BOLD, clampInt(layout.unit(), 9, 14));
	}

	private static void drawMaxDeviceScrollStatus(Graphics2D graphics, UiRect titleRect, UiLayout layout, int offset, int visibleCount, int totalCount) {
		if (totalCount <= visibleCount || visibleCount <= 0) {
			return;
		}
		int width = clampInt(layout.unit() * 4, 30, 56);
		UiRect statusRect = new UiRect(titleRect.right() - width - clampInt(layout.unit() * 4, 28, 44), titleRect.y(), width, titleRect.height());
		int from = offset + 1;
		int to = Math.min(totalCount, offset + visibleCount);
		drawCenteredText(graphics, from + "-" + to, statusRect, new Color(188, 204, 218, 208), Font.PLAIN, clampInt(layout.unit() - 2, 7, 11));
	}

	private static String maxCallDeviceCountLabel(MaxCallVisualSnapshot call) {
		int cameras = call != null ? Math.max(0, call.connectedCameraCount()) : 0;
		int drones = call != null ? Math.max(0, call.connectedDroneCount()) : 0;
		int microphones = call != null && call.microphones() != null ? call.microphones().size() : 0;
		return "Камер: " + cameras + " · Дронов: " + drones + " · Микрофонов: " + microphones;
	}

	private static void drawMaxRingtonePicker(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		fillRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), new Color(6, 10, 14, 232));
		strokeRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 54));
		drawMediaBackButton(graphics, maxAvatarPickerBackRect(layout), layout);
		drawVerticalText(graphics, "РИНГТОН", maxAvatarPickerTitleRect(layout), new Color(248, 251, 255, 236), Font.BOLD, clampInt(layout.unit(), 10, 16));
		List<MaxRingtoneCandidateSnapshot> candidates = state.ringtoneCandidates();
		if (candidates == null || candidates.isEmpty()) {
			drawCenteredText(graphics, "В галерее нет аудиофайлов", maxAvatarPickerGridRect(layout), new Color(210, 224, 236, 224), Font.BOLD, clampInt(layout.unit(), 9, 14));
			return;
		}
		int count = Math.min(candidates.size(), maxRingtonePickerCapacity(layout));
		for (int index = 0; index < count; index++) {
			drawMaxRingtoneCandidate(graphics, layout, maxRingtoneCandidateRect(layout, index), candidates.get(index));
		}
	}

	private static void drawMaxRingtoneCandidate(Graphics2D graphics, UiLayout layout, UiRect rect, MaxRingtoneCandidateSnapshot candidate) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 12, 22), new Color(255, 255, 255, candidate.selected() ? 30 : 16));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 12, 22), 1.0F, candidate.selected() ? new Color(255, 255, 255, 92) : new Color(255, 255, 255, 36));
		UiRect play = maxRingtoneCandidatePlayRect(rect, layout);
		Color playColor = drawMediaHeaderControlBase(graphics, play, MediaButtonSegment.SINGLE);
		drawPlayerUiIcon(graphics, mediaChromeIconRect(play, layout), candidate.playing() ? PlayerUiIcon.PAUSE : PlayerUiIcon.PLAY, playColor);
		UiRect select = maxRingtoneCandidateSelectRect(rect, layout);
		Color selectColor = drawMediaHeaderControlBase(graphics, select, MediaButtonSegment.SINGLE);
		drawPlayerUiIcon(graphics, mediaChromeIconRect(select, layout), PlayerUiIcon.CHECK, selectColor);
		UiRect text = new UiRect(play.right() + layout.unit(), rect.y() + layout.unit() / 2, select.x() - play.right() - layout.unit() * 2, rect.height() / 2);
		drawVerticalText(graphics, candidate.title(), text, new Color(248, 251, 255, 238), Font.BOLD, clampInt(layout.unit(), 9, 14));
		drawVerticalText(graphics, candidate.subtitle(), new UiRect(text.x(), text.bottom(), text.width(), rect.height() / 3), new Color(176, 200, 216, 216), Font.PLAIN, clampInt(layout.unit() - 2, 7, 11));
	}

	private static void drawMaxContactPicker(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state, MaxCallVisualSnapshot call) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		fillRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), new Color(6, 10, 14, 230));
		strokeRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 54));
		drawMediaBackButton(graphics, maxAvatarPickerBackRect(layout), layout);
		drawVerticalText(graphics, "ДОБАВИТЬ В ЗВОНОК", maxAvatarPickerTitleRect(layout), new Color(248, 251, 255, 236), Font.BOLD, clampInt(layout.unit(), 10, 16));
		List<MaxContactSnapshot> contacts = state.contacts() != null ? state.contacts() : List.of();
		List<MaxContactSnapshot> candidates = contacts.stream()
				.filter(contact -> contact != null && !contact.active() && !contact.ringing() && !Objects.equals(contact.code(), call.peerCode()))
				.toList();
		if (candidates.isEmpty()) {
			drawCenteredText(graphics, "Нет контактов для приглашения", maxAvatarPickerGridRect(layout), new Color(210, 224, 236, 224), Font.BOLD, clampInt(layout.unit(), 9, 14));
			return;
		}
		int count = Math.min(candidates.size(), maxContactPickerCapacity(layout));
		for (int index = 0; index < count; index++) {
			drawMaxContactRow(graphics, layout, maxContactPickerRowRect(layout, index), candidates.get(index), false);
		}
	}

	private static void drawMaxFileSharePicker(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		fillRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), new Color(6, 10, 14, 232));
		strokeRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 54));
		drawMediaBackButton(graphics, maxAvatarPickerBackRect(layout), layout);
		String title = state.fileShareFileCount() <= 1 ? "ОТПРАВИТЬ ФАЙЛ" : "ОТПРАВИТЬ ФАЙЛЫ";
		drawVerticalText(graphics, title, maxAvatarPickerTitleRect(layout), new Color(248, 251, 255, 236), Font.BOLD, clampInt(layout.unit(), 10, 16));
		UiRect send = maxFileShareSendRect(layout);
		Color sendColor = drawMediaHeaderControlBase(graphics, send, MediaButtonSegment.SINGLE);
		drawPlayerUiIcon(graphics, mediaChromeIconRect(send, layout), PlayerUiIcon.SEND_PLANE, sendColor);

		List<MaxFileShareContactSnapshot> contacts = state.fileShareContacts() != null ? state.fileShareContacts() : List.of();
		if (contacts.isEmpty()) {
			drawCenteredText(graphics, "Добавь контакты в MAX", maxContactPickerListRect(layout), new Color(210, 224, 236, 224), Font.BOLD, clampInt(layout.unit(), 9, 14));
			return;
		}
		UiRect hint = maxFileShareHintRect(layout);
		String fileLabel = state.fileShareFileCount() <= 1 ? state.fileShareTitle() : "Файлов: " + state.fileShareFileCount();
		drawCenteredTextFitted(graphics, fileLabel, hint, new Color(176, 202, 220, 224), Font.BOLD, clampInt(layout.unit() - 2, 7, 11), 6);
		int count = Math.min(contacts.size(), maxContactPickerCapacity(layout));
		for (int index = 0; index < count; index++) {
			drawMaxFileShareContactRow(graphics, layout, maxContactPickerRowRect(layout, index), contacts.get(index));
		}
	}

	private static void drawMaxFileShareContactRow(Graphics2D graphics, UiLayout layout, UiRect rect, MaxFileShareContactSnapshot contact) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 12, 24), new Color(8, 12, 16, contact.selected() ? 206 : 174));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 12, 24), contact.selected() ? 1.4F : 1.0F, contact.selected() ? new Color(255, 255, 255, 112) : contact.online() ? new Color(255, 255, 255, 58) : new Color(255, 255, 255, 24));
		UiRect avatarRect = new UiRect(rect.x() + layout.unit(), rect.y() + layout.unit() / 2, rect.height() - layout.unit(), rect.height() - layout.unit());
		drawAvatar(graphics, avatarRect, contact.avatarFrame(), layout);
		UiRect checkRect = maxFileShareContactCheckRect(rect, layout);
		int textRight = checkRect.x();
		UiRect codeRect = new UiRect(avatarRect.right() + layout.unit(), rect.y() + layout.unit() / 3, textRight - avatarRect.right() - layout.unit() * 2, rect.height() / 2);
		drawVerticalText(graphics, displayAccountCode(contact.code()), codeRect, new Color(248, 251, 255, 238), Font.BOLD, clampInt(layout.unit(), 10, 16));
		drawVerticalText(graphics, contact.online() ? "доступен" : "недоступен", new UiRect(codeRect.x(), codeRect.bottom(), codeRect.width(), rect.height() / 3), new Color(178, 202, 218, 218), Font.PLAIN, clampInt(layout.unit() - 2, 7, 11));
		fillRoundedRect(graphics, checkRect, checkRect.height(), contact.selected() ? new Color(248, 251, 255, 232) : new Color(255, 255, 255, 16));
		strokeRoundedRect(graphics, checkRect, checkRect.height(), 1.0F, new Color(255, 255, 255, contact.selected() ? 150 : 48));
		drawPlayerUiIcon(graphics, mediaChromeIconRect(checkRect, layout), contact.selected() ? PlayerUiIcon.CHECKBOX_FILL : PlayerUiIcon.CHECKBOX_LINE, contact.selected() ? new Color(20, 24, 30, 238) : new Color(248, 251, 255, 220));
	}

	private static void drawMaxNotificationsScreen(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		fillRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), new Color(6, 10, 14, 232));
		strokeRoundedRect(graphics, panel, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 54));
		drawMediaBackButton(graphics, maxAvatarPickerBackRect(layout), layout);
		drawVerticalText(graphics, "УВЕДОМЛЕНИЯ", maxAvatarPickerTitleRect(layout), new Color(248, 251, 255, 236), Font.BOLD, clampInt(layout.unit(), 10, 16));
		MaxIncomingFileSnapshot incoming = state.incomingFile();
		if (incoming == null) {
			drawCenteredText(graphics, "Нет входящих файлов", maxAvatarPickerGridRect(layout), new Color(210, 224, 236, 224), Font.BOLD, clampInt(layout.unit(), 9, 14));
			return;
		}
		UiRect card = maxNotificationCardRect(layout);
		fillRoundedRect(graphics, card, clampInt(layout.unit() * 2, 14, 28), new Color(255, 255, 255, 18));
		strokeRoundedRect(graphics, card, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 46));
		UiRect avatar = maxNotificationAvatarRect(layout);
		drawAvatar(graphics, avatar, incoming.senderAvatarFrame(), layout);
		drawCenteredTextFitted(graphics, displayAccountCode(incoming.senderCode()), maxNotificationSenderRect(layout), new Color(248, 251, 255, 240), Font.BOLD, clampInt(layout.unit() + 2, 12, 20), 8);
		drawCenteredTextFitted(graphics, incoming.fileName(), maxNotificationFileRect(layout), new Color(220, 238, 248, 232), Font.BOLD, clampInt(layout.unit(), 9, 15), 7);
		String subtitle = incoming.subtitle() == null || incoming.subtitle().isBlank() ? notificationKindLabel(incoming.kind()) : incoming.subtitle();
		drawCenteredTextFitted(graphics, subtitle, maxNotificationSubtitleRect(layout), new Color(166, 194, 214, 218), Font.PLAIN, clampInt(layout.unit() - 2, 7, 11), 6);
		drawRoundCallButton(graphics, maxNotificationAcceptRect(layout), PlayerUiIcon.CHECK, new Color(74, 214, 142), new Color(8, 18, 13, 238), layout);
		drawRoundCallButton(graphics, maxNotificationDeclineRect(layout), PlayerUiIcon.CLOSE, new Color(240, 88, 96), new Color(255, 248, 248, 246), layout);
		if (state.notificationCount() > 1) {
			drawCenteredTextFitted(graphics, "Ещё: " + (state.notificationCount() - 1), maxNotificationQueueRect(layout), new Color(210, 224, 236, 210), Font.BOLD, clampInt(layout.unit() - 2, 7, 11), 6);
		}
	}

	private static String notificationKindLabel(GalleryItemKind kind) {
		return switch (kind != null ? kind : GalleryItemKind.MEDIA) {
			case AUDIO -> "аудиофайл";
			case VIDEO -> "видео";
			case YOUTUBE -> "ссылка YouTube";
			case LIVE_CAMERA -> "камера";
			case MEDIA -> "файл из галереи";
		};
	}

	private static void drawMaxAtmosphere(Graphics2D graphics, UiRect canvas, UiLayout layout) {
		graphics.setPaint(new GradientPaint(canvas.x(), canvas.y(), new Color(0, 184, 255, 40), canvas.right(), canvas.bottom(), new Color(255, 255, 255, 8)));
		graphics.fillRect(canvas.x(), canvas.y(), canvas.width(), canvas.height());
		graphics.setColor(new Color(255, 255, 255, 8));
		int step = clampInt(layout.unit() * 3, 18, 42);
		for (int x = canvas.x(); x < canvas.right(); x += step) {
			graphics.drawLine(x, canvas.y(), x, canvas.bottom());
		}
	}

	private static void drawAvatar(Graphics2D graphics, UiRect rect, BufferedImage avatar, UiLayout layout) {
		Shape previousClip = graphics.getClip();
		Ellipse2D.Float circle = new Ellipse2D.Float(rect.x(), rect.y(), rect.width(), rect.height());
		graphics.setClip(circle);
		if (avatar != null) {
			drawScaledImage(graphics, avatar, rect, MediaScaleMode.FILL);
		} else {
			graphics.setPaint(new GradientPaint(rect.x(), rect.y(), new Color(255, 255, 255, 36), rect.right(), rect.bottom(), new Color(255, 255, 255, 10)));
			graphics.fillOval(rect.x(), rect.y(), rect.width(), rect.height());
		}
		graphics.setClip(previousClip);
		Stroke previousStroke = graphics.getStroke();
		graphics.setStroke(new BasicStroke(Math.max(1.0F, layout.unit() / 8.0F)));
		graphics.setColor(new Color(255, 255, 255, 86));
		graphics.drawOval(rect.x(), rect.y(), rect.width(), rect.height());
		graphics.setStroke(previousStroke);
	}

	private static void drawMaxAddContactButton(Graphics2D graphics, UiRect rect, UiLayout layout) {
		Color color = drawMediaHeaderControlBase(graphics, rect, MediaButtonSegment.SINGLE);
		drawPlayerUiIcon(graphics, mediaChromeIconRect(rect, layout), PlayerUiIcon.CONTACT_ADD, color);
	}

	private static void drawMaxNotificationsButton(Graphics2D graphics, UiRect rect, int count, UiLayout layout) {
		Color color = drawMediaHeaderControlBase(graphics, rect, MediaButtonSegment.SINGLE);
		drawPlayerUiIcon(graphics, mediaChromeIconRect(rect, layout), PlayerUiIcon.NOTIFICATION, color);
		if (count > 0) {
			drawNotificationBadge(graphics, maxNotificationButtonBadgeRect(rect, layout), count, layout);
		}
	}

	private static void drawMaxRingtoneControls(Graphics2D graphics, UiLayout layout, MaxVisualSnapshot state) {
		UiRect play = maxRingtonePreviewRect(layout);
		UiRect picker = maxRingtonePickerOpenRect(layout);
		Color playColor = drawMediaHeaderControlBase(graphics, play, MediaButtonSegment.SINGLE);
		drawPlayerUiIcon(graphics, mediaChromeIconRect(play, layout), state.ringtonePreviewPlaying() ? PlayerUiIcon.PAUSE : PlayerUiIcon.PLAY, playColor);
		Color pickerColor = drawMediaHeaderControlBase(graphics, picker, MediaButtonSegment.SINGLE);
		UiRect icon = new UiRect(picker.x() + clampInt(layout.unit() / 2, 5, 9), picker.y() + picker.height() / 4, picker.height() / 2, picker.height() / 2);
		drawPlayerUiIcon(graphics, icon, PlayerUiIcon.FILE_MUSIC, pickerColor);
		drawVerticalText(graphics, "ВЫБРАТЬ РИНГТОН", new UiRect(icon.right() + layout.unit() / 2, picker.y(), picker.right() - icon.right() - layout.unit(), picker.height()), pickerColor, Font.BOLD, clampInt(layout.unit() - 2, 7, 11));
	}

	private static void drawMaxContactRow(Graphics2D graphics, UiLayout layout, UiRect rect, MaxContactSnapshot contact, boolean deleteVisible) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 12, 24), new Color(8, 12, 16, 174));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 12, 24), 1.0F, contact.online() ? new Color(255, 255, 255, 58) : new Color(255, 255, 255, 24));
		UiRect avatarRect = new UiRect(rect.x() + layout.unit(), rect.y() + layout.unit() / 2, rect.height() - layout.unit(), rect.height() - layout.unit());
		drawAvatar(graphics, avatarRect, contact.avatarFrame(), layout);
		UiRect deleteRect = maxContactDeleteRect(rect, layout);
		int textRight = deleteVisible ? deleteRect.x() : rect.right() - layout.unit();
		UiRect codeRect = new UiRect(avatarRect.right() + layout.unit(), rect.y() + layout.unit() / 3, textRight - avatarRect.right() - layout.unit() * 2, rect.height() / 2);
		drawVerticalText(graphics, displayAccountCode(contact.code()), codeRect, new Color(248, 251, 255, 238), Font.BOLD, clampInt(layout.unit(), 10, 16));
		String status = contact.active() ? "в вызове" : contact.ringing() ? "звонит" : contact.online() ? "доступен" : "недоступен";
		drawVerticalText(graphics, status, new UiRect(codeRect.x(), codeRect.bottom(), codeRect.width(), rect.height() / 3), new Color(178, 202, 218, 218), Font.PLAIN, clampInt(layout.unit() - 2, 7, 11));
		if (deleteVisible) {
			Color deleteColor = drawMediaHeaderControlBase(graphics, deleteRect, MediaButtonSegment.SINGLE);
			drawPlayerUiIcon(graphics, mediaChromeIconRect(deleteRect, layout), PlayerUiIcon.TRASH, deleteColor);
		}
	}

	private static void drawMaxEmptyContacts(Graphics2D graphics, UiLayout layout, UiRect rect) {
		fillRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 14, 28), new Color(8, 12, 16, 148));
		strokeRoundedRect(graphics, rect, clampInt(layout.unit() * 2, 14, 28), 1.0F, new Color(255, 255, 255, 34));
		drawCenteredText(graphics, "Контактов пока нет", new UiRect(rect.x(), rect.y() + rect.height() / 4, rect.width(), rect.height() / 4), new Color(248, 251, 255, 232), Font.BOLD, clampInt(layout.unit() + 1, 11, 18));
		drawCenteredText(graphics, "Добавь экран по MAX-коду и начни видеозвонок", new UiRect(rect.x() + layout.unit(), rect.y() + rect.height() / 2, rect.width() - layout.unit() * 2, rect.height() / 4), new Color(180, 202, 218, 220), Font.PLAIN, clampInt(layout.unit() - 1, 8, 13));
	}

	private static void drawMaxCallPillButton(Graphics2D graphics, UiRect rect, String label, Color fill, Color text, UiLayout layout, boolean accept) {
		fillRoundedRect(graphics, rect, rect.height(), fill != null ? fill : new Color(255, 255, 255, 0));
		strokeRoundedRect(graphics, rect, rect.height(), 1.1F, new Color(255, 255, 255, accept ? 136 : 76));
		drawCenteredTextFitted(graphics, label, rect, text, Font.BOLD, clampInt(layout.unit(), 10, 15), 7);
	}

	private static void drawMaxToggleButton(Graphics2D graphics, UiRect rect, String label, boolean active, UiLayout layout) {
		Color icon = drawSmallMediaButtonBase(graphics, rect, MediaButtonSegment.SINGLE, active, mediaChromeStrokeWidth(rect), active ? null : new Color(255, 255, 255, 0));
		drawCenteredTextFitted(graphics, label, rect, icon, Font.BOLD, clampInt(layout.unit() - 1, 8, 12), 6);
	}

	private static void drawMaxCameraSelect(Graphics2D graphics, UiRect rect, MaxCallVisualSnapshot call, UiLayout layout) {
		Color color = drawMediaHeaderControlBase(graphics, rect, MediaButtonSegment.SINGLE);
		String label = "КАМЕРА";
		if (call.cameras() != null && call.selectedCameraIndex() >= 0 && call.selectedCameraIndex() < call.cameras().size()) {
			MaxCameraOptionSnapshot selected = call.cameras().get(call.selectedCameraIndex());
			label = selected.title() + " " + selected.subtitle();
		}
		drawPlayerUiIcon(graphics, new UiRect(rect.x() + clampInt(layout.unit() / 2, 5, 8), rect.y() + rect.height() / 4, rect.height() / 2, rect.height() / 2), PlayerUiIcon.CAMERA, color);
		drawVerticalText(graphics, label, new UiRect(rect.x() + rect.height(), rect.y(), rect.width() - rect.height() - layout.unit() / 2, rect.height()), color, Font.BOLD, clampInt(layout.unit() - 2, 7, 11));
	}

	private static void drawPhoneGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		Stroke previous = graphics.getStroke();
		graphics.setStroke(new BasicStroke(Math.max(1.4F, strokeWidth), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(color);
		int x1 = rect.x() + rect.width() / 4;
		int y1 = rect.y() + rect.height() / 3;
		int x2 = rect.right() - rect.width() / 4;
		int y2 = rect.bottom() - rect.height() / 3;
		graphics.drawArc(rect.x(), rect.y(), rect.width(), rect.height(), 205, 130);
		graphics.drawLine(x1, y1, x1 + rect.width() / 8, y1 + rect.height() / 8);
		graphics.drawLine(x2, y2, x2 - rect.width() / 8, y2 - rect.height() / 8);
		graphics.setStroke(previous);
	}

	private static void drawPlusGlyph(Graphics2D graphics, UiRect rect, Color color, float strokeWidth) {
		Stroke previous = graphics.getStroke();
		graphics.setStroke(new BasicStroke(Math.max(1.4F, strokeWidth), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(color);
		graphics.drawLine(rect.x() + rect.width() / 2, rect.y(), rect.x() + rect.width() / 2, rect.bottom());
		graphics.drawLine(rect.x(), rect.y() + rect.height() / 2, rect.right(), rect.y() + rect.height() / 2);
		graphics.setStroke(previous);
	}

	private static UiRect maxProfilePanelRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int top = canvas.y() + clampInt(layout.unit() * 3, 24, 48);
		return new UiRect(canvas.x() + layout.unit(), top, canvas.width() - layout.unit() * 2, clampInt(layout.unit() * 6, 58, 88));
	}

	private static UiRect maxProfileAvatarRect(UiLayout layout) {
		UiRect panel = maxProfilePanelRect(layout);
		int size = panel.height() - layout.unit() * 2;
		return new UiRect(panel.x() + layout.unit(), panel.y() + layout.unit(), size, size);
	}

	private static UiRect maxAppTitleRect(UiLayout layout) {
		UiRect avatar = maxProfileAvatarRect(layout);
		UiRect panel = maxProfilePanelRect(layout);
		return new UiRect(avatar.right() + layout.unit(), panel.y() + layout.unit() / 2, panel.width() / 3, panel.height() / 2);
	}

	private static UiRect maxProfileCodeRect(UiLayout layout) {
		UiRect title = maxAppTitleRect(layout);
		UiRect panel = maxProfilePanelRect(layout);
		return new UiRect(title.x(), title.bottom() - layout.unit() / 4, title.width() + layout.unit() * 3, panel.height() / 3);
	}

	private static UiRect maxAddContactRect(UiLayout layout) {
		UiRect panel = maxProfilePanelRect(layout);
		int height = clampInt(layout.unit() * 2 + 4, 24, 34);
		return new UiRect(panel.right() - height - layout.unit(), panel.y() + (panel.height() - height) / 2, height, height);
	}

	private static UiRect maxNotificationsRect(UiLayout layout) {
		UiRect add = maxAddContactRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		return new UiRect(add.x() - add.width() - gap, add.y(), add.width(), add.height());
	}

	private static UiRect maxNotificationButtonBadgeRect(UiRect button, UiLayout layout) {
		int height = clampInt(layout.unit() + 4, 12, 18);
		int width = clampInt(height + layout.unit() / 2, height, 30);
		return new UiRect(button.right() - width / 2, button.y() - height / 3, width, height);
	}

	private static UiRect maxRingtoneControlsRect(UiLayout layout) {
		UiRect header = maxProfilePanelRect(layout);
		int height = clampInt(layout.unit() * 2 + 4, 24, 34);
		int y = header.bottom() + Math.max(4, layout.unit() / 2);
		return new UiRect(header.x(), y, header.width(), height);
	}

	private static UiRect maxRingtonePreviewRect(UiLayout layout) {
		UiRect controls = maxRingtoneControlsRect(layout);
		return new UiRect(controls.x(), controls.y(), controls.height(), controls.height());
	}

	private static UiRect maxRingtonePickerOpenRect(UiLayout layout) {
		UiRect controls = maxRingtoneControlsRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		UiRect preview = maxRingtonePreviewRect(layout);
		int width = clampInt(layout.unit() * 14, 112, Math.max(112, controls.width() - preview.width() - gap));
		return new UiRect(preview.right() + gap, controls.y(), Math.min(width, controls.right() - preview.right() - gap), controls.height());
	}

	private static UiRect maxContactListRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		UiRect controls = maxRingtoneControlsRect(layout);
		int y = controls.bottom() + layout.unit();
		return new UiRect(canvas.x() + layout.unit(), y, canvas.width() - layout.unit() * 2, canvas.bottom() - y - layout.unit());
	}

	private static int maxVisibleContactRows(UiLayout layout) {
		UiRect list = maxContactListRect(layout);
		int row = maxContactRowHeight(layout) + layout.unit() / 2;
		return Math.max(1, list.height() / Math.max(1, row));
	}

	private static int maxContactRowHeight(UiLayout layout) {
		return clampInt(layout.unit() * 5, 46, 70);
	}

	private static UiRect maxContactRowRect(UiLayout layout, int index) {
		UiRect list = maxContactListRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int height = maxContactRowHeight(layout);
		return new UiRect(list.x(), list.y() + index * (height + gap), list.width(), height);
	}

	private static UiRect maxContactDeleteRect(UiLayout layout, int index) {
		return maxContactDeleteRect(maxContactRowRect(layout, index), layout);
	}

	private static UiRect maxContactDeleteRect(UiRect row, UiLayout layout) {
		int size = clampInt(layout.unit() * 2 + 4, 24, 34);
		return new UiRect(row.right() - size - layout.unit(), row.y() + (row.height() - size) / 2, size, size);
	}

	private static int maxContactIndexAt(UiLayout layout, int contactCount, UiPoint point) {
		int count = Math.min(contactCount, maxVisibleContactRows(layout));
		for (int index = 0; index < count; index++) {
			if (maxContactRowRect(layout, index).contains(point.x(), point.y())) {
				return index;
			}
		}
		return -1;
	}

	private static UiRect maxStatusRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		return new UiRect(canvas.x() + layout.unit(), canvas.bottom() - clampInt(layout.unit() * 2, 18, 30), canvas.width() - layout.unit() * 2, clampInt(layout.unit() * 2, 18, 30));
	}

	private static UiRect maxIncomingAvatarRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int size = clampInt(Math.min(canvas.width(), canvas.height()) / 4, 30, 132);
		return new UiRect(canvas.x() + (canvas.width() - size) / 2, canvas.y() + canvas.height() / 2 - size, size, size);
	}

	private static UiRect maxIncomingCodeRect(UiLayout layout) {
		UiRect avatar = maxIncomingAvatarRect(layout);
		return new UiRect(mediaCanvasRect(layout).x() + layout.unit(), avatar.bottom() + layout.unit(), mediaCanvasRect(layout).width() - layout.unit() * 2, clampInt(layout.unit() * 3, 28, 48));
	}

	private static UiRect maxIncomingSubtitleRect(UiLayout layout) {
		UiRect code = maxIncomingCodeRect(layout);
		return new UiRect(code.x(), code.bottom(), code.width(), clampInt(layout.unit() * 2, 18, 30));
	}

	private static UiRect maxIncomingAcceptRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int gap = clampInt(layout.unit(), 6, 20);
		int fit = Math.max(14, (canvas.width() - gap - clampInt(layout.unit(), 4, 12) * 2) / 2);
		int size = Math.min(clampInt(Math.min(canvas.width(), canvas.height()) / 5, 18, 58), fit);
		int y = canvas.bottom() - size - clampInt(layout.unit() * 2, 10, 34);
		return new UiRect(canvas.x() + (canvas.width() - size * 2 - gap) / 2, y, size, size);
	}

	private static UiRect maxIncomingDeclineRect(UiLayout layout) {
		UiRect accept = maxIncomingAcceptRect(layout);
		return new UiRect(accept.right() + clampInt(layout.unit(), 6, 20), accept.y(), accept.width(), accept.height());
	}

	private static UiRect maxOutgoingCancelRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int size = clampInt(Math.min(canvas.width(), canvas.height()) / 5, 18, 58);
		return new UiRect(canvas.x() + (canvas.width() - size) / 2, canvas.bottom() - size - clampInt(layout.unit() * 2, 10, 34), size, size);
	}

	private static UiRect maxCallFocusedTileRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int inset = clampInt(layout.unit() / 2, 1, 6);
		return new UiRect(canvas.x() + inset, canvas.y() + inset, canvas.width() - inset * 2, canvas.height() - inset * 2);
	}

	private static UiRect maxCallParticipantsAreaRect(UiLayout layout, boolean menuOpen) {
		UiRect canvas = mediaCanvasRect(layout);
		int inset = clampInt(layout.unit() / 2, 1, 6);
		int bottom = canvas.bottom() - inset;
		if (menuOpen) {
			bottom -= maxCallMenuButtonSize(layout) + clampInt(layout.unit(), 4, 10);
		}
		int top = canvas.y() + inset;
		if (bottom <= top + 8) {
			bottom = canvas.bottom() - inset;
		}
		return new UiRect(canvas.x() + inset, top, Math.max(1, canvas.width() - inset * 2), Math.max(1, bottom - top));
	}

	private static int maxCallGridGap(UiLayout layout) {
		return clampInt(layout.unit() / 2, 2, 8);
	}

	private static int maxCallGridColumns(int count, UiRect area, int gap) {
		if (count <= 1) {
			return 1;
		}
		int bestColumns = 1;
		double bestScore = Double.MAX_VALUE;
		for (int columns = 1; columns <= count; columns++) {
			int rows = (count + columns - 1) / columns;
			int cellWidth = (area.width() - gap * (columns - 1)) / columns;
			int cellHeight = (area.height() - gap * (rows - 1)) / rows;
			if (cellWidth <= 0 || cellHeight <= 0) {
				continue;
			}
			double aspect = cellWidth / (double) cellHeight;
			double score = Math.abs(Math.log(Math.max(0.01D, aspect))) - Math.min(cellWidth, cellHeight) * 0.002D;
			if (score < bestScore) {
				bestScore = score;
				bestColumns = columns;
			}
		}
		return Math.max(1, bestColumns);
	}

	private static UiRect maxCallParticipantTileRect(UiLayout layout, int participantCount, int index, boolean menuOpen) {
		if (participantCount <= 0 || index < 0 || index >= participantCount) {
			return emptyRect();
		}
		UiRect area = maxCallParticipantsAreaRect(layout, menuOpen);
		int gap = maxCallGridGap(layout);
		int columns = maxCallGridColumns(participantCount, area, gap);
		int rows = (participantCount + columns - 1) / columns;
		int cellWidth = Math.max(1, (area.width() - gap * (columns - 1)) / columns);
		int cellHeight = Math.max(1, (area.height() - gap * (rows - 1)) / rows);
		int row = index / columns;
		int itemInRow = index % columns;
		int itemsInRow = Math.min(columns, participantCount - row * columns);
		int rowWidth = itemsInRow * cellWidth + Math.max(0, itemsInRow - 1) * gap;
		int gridHeight = rows * cellHeight + Math.max(0, rows - 1) * gap;
		int x = area.x() + (area.width() - rowWidth) / 2 + itemInRow * (cellWidth + gap);
		int y = area.y() + (area.height() - gridHeight) / 2 + row * (cellHeight + gap);
		return new UiRect(x, y, cellWidth, cellHeight);
	}

	private static int maxCallParticipantIndexAt(UiLayout layout, int participantCount, boolean menuOpen, UiPoint point) {
		for (int index = 0; index < participantCount; index++) {
			if (maxCallParticipantTileRect(layout, participantCount, index, menuOpen).contains(point.x(), point.y())) {
				return index;
			}
		}
		return -1;
	}

	private static int maxCallMiniParticipantWidth(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		return clampInt(canvas.width() / 5, 24, 96);
	}

	private static int maxCallMiniParticipantHeight(UiLayout layout) {
		return clampInt(maxCallMiniParticipantWidth(layout) * 9 / 16, 18, 64);
	}

	private static UiRect maxCallMiniParticipantRect(UiLayout layout, int index) {
		UiRect canvas = mediaCanvasRect(layout);
		int inset = clampInt(layout.unit() / 2, 2, 8);
		int gap = maxCallGridGap(layout);
		int width = maxCallMiniParticipantWidth(layout);
		int height = maxCallMiniParticipantHeight(layout);
		int columns = Math.max(1, (canvas.width() - inset * 2 + gap) / Math.max(1, width + gap));
		int row = index / columns;
		int column = index % columns;
		return new UiRect(canvas.x() + inset + column * (width + gap), canvas.y() + inset + row * (height + gap), width, height);
	}

	private static int maxCallMiniParticipantCapacity(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int inset = clampInt(layout.unit() / 2, 2, 8);
		int gap = maxCallGridGap(layout);
		int width = maxCallMiniParticipantWidth(layout);
		int height = maxCallMiniParticipantHeight(layout);
		int columns = Math.max(1, (canvas.width() - inset * 2 + gap) / Math.max(1, width + gap));
		int bottom = maxCallMenuDockRect(layout, false, true).y() - gap;
		int availableHeight = Math.max(height, bottom - canvas.y() - inset);
		int rows = Math.max(1, (availableHeight + gap) / Math.max(1, height + gap));
		return Math.max(1, columns * rows);
	}

	private static int maxCallMiniParticipantIndexAt(UiLayout layout, int participantCount, UiPoint point) {
		int count = Math.min(participantCount, maxCallMiniParticipantCapacity(layout));
		for (int index = 0; index < count; index++) {
			if (maxCallMiniParticipantRect(layout, index).contains(point.x(), point.y())) {
				return index;
			}
		}
		return -1;
	}

	private static UiRect maxCallMenuDockRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallMenuDockRect(layout, call.microphoneCount() > 1, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallMenuDockRect(UiLayout layout, boolean multiMicrophone, boolean focused) {
		UiRect canvas = mediaCanvasRect(layout);
		int size = maxCallMenuButtonSize(layout);
		int gap = maxCallGridGap(layout);
		int padding = clampInt(layout.unit() / 2, 2, 6);
		int micWidth = multiMicrophone ? size * 2 : size;
		int width = micWidth + gap + size * 2 + gap + size + gap + (focused ? size + gap : 0) + size;
		int dockWidth = Math.min(canvas.width() - padding * 2, width + padding * 2);
		int dockHeight = size + padding * 2;
		int x = canvas.x() + (canvas.width() - dockWidth) / 2;
		int y = canvas.bottom() - dockHeight - clampInt(layout.unit() / 2, 2, 8);
		return new UiRect(x, y, Math.max(1, dockWidth), Math.max(1, dockHeight));
	}

	private static int maxCallMenuButtonSize(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		int gap = maxCallGridGap(layout);
		int padding = clampInt(layout.unit() / 2, 2, 6);
		int fit = (canvas.width() - padding * 2 - gap * 5) / 7;
		int desired = clampInt(layout.unit() * 2, 16, 34);
		return Math.max(10, Math.min(desired, fit));
	}

	private static UiRect maxCallMicrophoneToggleRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallMicrophoneToggleRect(layout, call.microphoneCount() > 1, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallMicrophoneToggleRect(UiLayout layout, boolean multiMicrophone) {
		return maxCallMicrophoneToggleRect(layout, multiMicrophone, false);
	}

	private static UiRect maxCallMicrophoneToggleRect(UiLayout layout, boolean multiMicrophone, boolean focused) {
		UiRect dock = maxCallMenuDockRect(layout, multiMicrophone, focused);
		int size = maxCallMenuButtonSize(layout);
		int padding = Math.max(1, (dock.height() - size) / 2);
		return new UiRect(dock.x() + padding, dock.y() + padding, size, size);
	}

	private static UiRect maxCallMicrophoneSelectRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallMicrophoneSelectRect(layout, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallMicrophoneSelectRect(UiLayout layout) {
		return maxCallMicrophoneSelectRect(layout, false);
	}

	private static UiRect maxCallMicrophoneSelectRect(UiLayout layout, boolean focused) {
		UiRect mic = maxCallMicrophoneToggleRect(layout, true, focused);
		return new UiRect(mic.right(), mic.y(), mic.width(), mic.height());
	}

	private static UiRect maxCallCameraToggleRect(UiLayout layout) {
		return maxCallCameraToggleRect(layout, true);
	}

	private static UiRect maxCallCameraToggleRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallCameraToggleRect(layout, call.microphoneCount() > 1, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallCameraToggleRect(UiLayout layout, boolean multiMicrophone) {
		return maxCallCameraToggleRect(layout, multiMicrophone, false);
	}

	private static UiRect maxCallCameraToggleRect(UiLayout layout, boolean multiMicrophone, boolean focused) {
		UiRect dock = maxCallMenuDockRect(layout, multiMicrophone, focused);
		int size = maxCallMenuButtonSize(layout);
		int gap = maxCallGridGap(layout);
		UiRect mic = multiMicrophone ? maxCallMicrophoneSelectRect(layout, focused) : maxCallMicrophoneToggleRect(layout, false, focused);
		return new UiRect(mic.right() + gap, dock.y() + Math.max(1, (dock.height() - size) / 2), size, size);
	}

	private static UiRect maxCallCameraSelectRect(UiLayout layout) {
		return maxCallCameraSelectRect(layout, true);
	}

	private static UiRect maxCallCameraSelectRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallCameraSelectRect(layout, call.microphoneCount() > 1, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallCameraSelectRect(UiLayout layout, boolean multiMicrophone) {
		return maxCallCameraSelectRect(layout, multiMicrophone, false);
	}

	private static UiRect maxCallCameraSelectRect(UiLayout layout, boolean multiMicrophone, boolean focused) {
		UiRect camera = maxCallCameraToggleRect(layout, multiMicrophone, focused);
		return new UiRect(camera.right(), camera.y(), camera.width(), camera.height());
	}

	private static UiRect maxCallInviteRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallInviteRect(layout, call.microphoneCount() > 1, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallInviteRect(UiLayout layout, boolean multiMicrophone, boolean focused) {
		int gap = maxCallGridGap(layout);
		UiRect camera = maxCallCameraSelectRect(layout, multiMicrophone, focused);
		return new UiRect(camera.right() + gap, camera.y(), camera.width(), camera.height());
	}

	private static UiRect maxCallLeaveRect(UiLayout layout) {
		return maxCallLeaveRect(layout, true, true);
	}

	private static UiRect maxCallLeaveRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallLeaveRect(layout, call.microphoneCount() > 1, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallLeaveRect(UiLayout layout, boolean multiMicrophone, boolean focused) {
		int size = maxCallMenuButtonSize(layout);
		int gap = maxCallGridGap(layout);
		UiRect previous = focused ? maxCallExitFullscreenRect(layout, multiMicrophone, true) : maxCallInviteRect(layout, multiMicrophone, false);
		return new UiRect(previous.right() + gap, previous.y(), size, size);
	}

	private static UiRect maxCallExitFullscreenRect(UiLayout layout, MaxCallVisualSnapshot call) {
		return maxCallExitFullscreenRect(layout, call.microphoneCount() > 1, call.selfFocused() || call.peerFocused());
	}

	private static UiRect maxCallExitFullscreenRect(UiLayout layout, boolean multiMicrophone, boolean focused) {
		if (!focused) {
			return emptyRect();
		}
		int gap = maxCallGridGap(layout);
		UiRect invite = maxCallInviteRect(layout, multiMicrophone, true);
		return new UiRect(invite.right() + gap, invite.y(), invite.width(), invite.height());
	}

	private static UiRect emptyRect() {
		return new UiRect(0, 0, 0, 0);
	}

	private static UiRect maxAvatarPickerPanelRect(UiLayout layout) {
		UiRect canvas = mediaCanvasRect(layout);
		return new UiRect(canvas.x() + layout.unit(), canvas.y() + clampInt(layout.unit() * 3, 24, 48), canvas.width() - layout.unit() * 2, canvas.height() - clampInt(layout.unit() * 4, 32, 60));
	}

	private static UiRect maxAvatarPickerBackRect(UiLayout layout) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		int size = clampInt(layout.unit() * 2 + 4, 24, 36);
		return new UiRect(panel.x() + layout.unit(), panel.y() + layout.unit(), size, size);
	}

	private static UiRect maxAvatarPickerTitleRect(UiLayout layout) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		UiRect back = maxAvatarPickerBackRect(layout);
		return new UiRect(back.right() + layout.unit(), back.y(), panel.right() - back.right() - layout.unit() * 2, back.height());
	}

	private static UiRect maxAvatarPickerGridRect(UiLayout layout) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		UiRect back = maxAvatarPickerBackRect(layout);
		int y = back.bottom() + layout.unit();
		return new UiRect(panel.x() + layout.unit(), y, panel.width() - layout.unit() * 2, panel.bottom() - y - layout.unit());
	}

	private static int maxAvatarPickerColumns(UiLayout layout) {
		return compactScreenLayout(layout) ? 3 : 4;
	}

	private static int maxAvatarPickerCapacity(UiLayout layout) {
		UiRect grid = maxAvatarPickerGridRect(layout);
		int columns = maxAvatarPickerColumns(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int cell = Math.max(1, (grid.width() - gap * (columns - 1)) / columns);
		int rows = Math.max(1, grid.height() / (cell + gap));
		return rows * columns;
	}

	private static UiRect maxAvatarCandidateRect(UiLayout layout, int index) {
		UiRect grid = maxAvatarPickerGridRect(layout);
		int columns = maxAvatarPickerColumns(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int cell = Math.max(1, (grid.width() - gap * (columns - 1)) / columns);
		int row = index / columns;
		int column = index % columns;
		return new UiRect(grid.x() + column * (cell + gap), grid.y() + row * (cell + gap), cell, cell);
	}

	private static int maxAvatarCandidateIndexAt(UiLayout layout, int candidateCount, UiPoint point) {
		int count = Math.min(candidateCount, maxAvatarPickerCapacity(layout));
		for (int index = 0; index < count; index++) {
			if (maxAvatarCandidateRect(layout, index).contains(point.x(), point.y())) {
				return index;
			}
		}
		return -1;
	}

	private static UiRect maxCallDeviceContentRect(UiLayout layout) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		UiRect back = maxAvatarPickerBackRect(layout);
		int y = back.bottom() + Math.max(4, layout.unit() / 2);
		return new UiRect(panel.x() + layout.unit(), y, panel.width() - layout.unit() * 2, Math.max(18, panel.bottom() - y - layout.unit()));
	}

	private static UiRect maxCallDeviceCameraTitleRect(UiLayout layout) {
		UiRect content = maxCallDeviceContentRect(layout);
		int height = clampInt(layout.unit() + 4, 14, 22);
		return new UiRect(content.x(), content.y(), content.width(), height);
	}

	private static UiRect maxCallDeviceCameraGridRect(UiLayout layout) {
		UiRect content = maxCallDeviceContentRect(layout);
		UiRect title = maxCallDeviceCameraTitleRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int height = Math.max(clampInt(layout.unit() * 7, 56, 112), (content.height() - title.height() - gap * 3) / 2);
		return new UiRect(content.x(), title.bottom() + gap, content.width(), Math.min(height, Math.max(18, content.bottom() - title.bottom() - gap)));
	}

	private static UiRect maxCallDeviceMicrophoneTitleRect(UiLayout layout) {
		UiRect cameraGrid = maxCallDeviceCameraGridRect(layout);
		UiRect content = maxCallDeviceContentRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int height = clampInt(layout.unit() + 4, 14, 22);
		return new UiRect(content.x(), cameraGrid.bottom() + gap, content.width(), height);
	}

	private static UiRect maxCallDeviceMicrophoneListRect(UiLayout layout) {
		UiRect content = maxCallDeviceContentRect(layout);
		UiRect title = maxCallDeviceMicrophoneTitleRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		return new UiRect(content.x(), title.bottom() + gap, content.width(), Math.max(18, content.bottom() - title.bottom() - gap));
	}

	private static UiRect maxCallDeviceScrollButtonRect(UiRect titleRect, int indexFromRight, UiLayout layout) {
		int gap = Math.max(4, layout.unit() / 2);
		int size = Math.min(titleRect.height(), clampInt(layout.unit() * 2, 18, 30));
		int x = titleRect.right() - size - indexFromRight * (size + gap);
		return new UiRect(x, titleRect.y(), size, titleRect.height());
	}

	private static UiRect maxCallDeviceCameraScrollLeftRect(UiLayout layout) {
		return maxCallDeviceScrollButtonRect(maxCallDeviceCameraTitleRect(layout), 1, layout);
	}

	private static UiRect maxCallDeviceCameraScrollRightRect(UiLayout layout) {
		return maxCallDeviceScrollButtonRect(maxCallDeviceCameraTitleRect(layout), 0, layout);
	}

	private static UiRect maxCallDeviceMicrophoneScrollUpRect(UiLayout layout) {
		return maxCallDeviceScrollButtonRect(maxCallDeviceMicrophoneTitleRect(layout), 1, layout);
	}

	private static UiRect maxCallDeviceMicrophoneScrollDownRect(UiLayout layout) {
		return maxCallDeviceScrollButtonRect(maxCallDeviceMicrophoneTitleRect(layout), 0, layout);
	}

	private static int maxCallDeviceCameraColumns(UiLayout layout) {
		return compactScreenLayout(layout) ? 3 : 4;
	}

	private static int maxCallDeviceCameraCapacity(UiLayout layout) {
		UiRect grid = maxCallDeviceCameraGridRect(layout);
		int columns = maxCallDeviceCameraColumns(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int cell = Math.max(1, (grid.width() - gap * (columns - 1)) / columns);
		int rows = Math.max(1, grid.height() / (cell + gap));
		return rows * columns;
	}

	private static UiRect maxCallDeviceCameraRect(UiLayout layout, int index) {
		UiRect grid = maxCallDeviceCameraGridRect(layout);
		int columns = maxCallDeviceCameraColumns(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int cell = Math.max(1, (grid.width() - gap * (columns - 1)) / columns);
		int row = index / columns;
		int column = index % columns;
		return new UiRect(grid.x() + column * (cell + gap), grid.y() + row * (cell + gap), cell, cell);
	}

	private static int maxCallDeviceCameraIndexAt(UiLayout layout, int cameraCount, int cameraScroll, UiPoint point) {
		int count = Math.min(Math.max(0, cameraCount - cameraScroll), maxCallDeviceCameraCapacity(layout));
		for (int visibleIndex = 0; visibleIndex < count; visibleIndex++) {
			if (maxCallDeviceCameraRect(layout, visibleIndex).contains(point.x(), point.y())) {
				return cameraScroll + visibleIndex;
			}
		}
		return -1;
	}

	private static int maxCallDeviceCameraScrollStep(UiLayout layout) {
		return Math.max(1, maxCallDeviceCameraColumns(layout));
	}

	private static int maxCallDeviceCameraScroll(UiLayout layout, int cameraCount) {
		return Math.max(0, cameraCount - maxCallDeviceCameraCapacity(layout));
	}

	private static int maxCallDeviceMicrophoneCapacity(UiLayout layout) {
		UiRect list = maxCallDeviceMicrophoneListRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int rowHeight = maxCallDeviceMicrophoneRowHeight(layout);
		return Math.max(1, (list.height() + gap) / Math.max(1, rowHeight + gap));
	}

	private static int maxCallDeviceMicrophoneRowHeight(UiLayout layout) {
		return clampInt(layout.unit() * 3, 28, 44);
	}

	private static UiRect maxCallDeviceMicrophoneRowRect(UiLayout layout, int index) {
		UiRect list = maxCallDeviceMicrophoneListRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int height = maxCallDeviceMicrophoneRowHeight(layout);
		return new UiRect(list.x(), list.y() + index * (height + gap), list.width(), height);
	}

	private static int maxCallDeviceMicrophoneIndexAt(UiLayout layout, int microphoneCount, int microphoneScroll, UiPoint point) {
		int count = Math.min(Math.max(0, microphoneCount - microphoneScroll), maxCallDeviceMicrophoneCapacity(layout));
		for (int visibleIndex = 0; visibleIndex < count; visibleIndex++) {
			if (maxCallDeviceMicrophoneRowRect(layout, visibleIndex).contains(point.x(), point.y())) {
				return microphoneScroll + visibleIndex;
			}
		}
		return -1;
	}

	private static int maxCallDeviceMicrophoneScroll(UiLayout layout, int microphoneCount) {
		return Math.max(0, microphoneCount - maxCallDeviceMicrophoneCapacity(layout));
	}

	private static int maxRingtonePickerCapacity(UiLayout layout) {
		UiRect list = maxAvatarPickerGridRect(layout);
		int row = maxRingtoneCandidateHeight(layout);
		int gap = Math.max(4, layout.unit() / 2);
		return Math.max(1, list.height() / Math.max(1, row + gap));
	}

	private static int maxRingtoneCandidateHeight(UiLayout layout) {
		return clampInt(layout.unit() * 5, 44, 64);
	}

	private static UiRect maxRingtoneCandidateRect(UiLayout layout, int index) {
		UiRect list = maxAvatarPickerGridRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int height = maxRingtoneCandidateHeight(layout);
		return new UiRect(list.x(), list.y() + index * (height + gap), list.width(), height);
	}

	private static UiRect maxRingtoneCandidatePlayRect(UiRect row, UiLayout layout) {
		int size = clampInt(layout.unit() * 2 + 4, 24, 34);
		return new UiRect(row.x() + layout.unit(), row.y() + (row.height() - size) / 2, size, size);
	}

	private static UiRect maxRingtoneCandidateSelectRect(UiRect row, UiLayout layout) {
		int size = clampInt(layout.unit() * 2 + 4, 24, 34);
		return new UiRect(row.right() - size - layout.unit(), row.y() + (row.height() - size) / 2, size, size);
	}

	private static int maxRingtoneCandidateIndexAt(UiLayout layout, int candidateCount, UiPoint point) {
		int count = Math.min(candidateCount, maxRingtonePickerCapacity(layout));
		for (int index = 0; index < count; index++) {
			if (maxRingtoneCandidateRect(layout, index).contains(point.x(), point.y())) {
				return index;
			}
		}
		return -1;
	}

	private static UiRect maxContactPickerListRect(UiLayout layout) {
		return maxAvatarPickerGridRect(layout);
	}

	private static UiRect maxFileShareSendRect(UiLayout layout) {
		UiRect panel = maxAvatarPickerPanelRect(layout);
		int size = clampInt(layout.unit() * 2 + 4, 24, 36);
		return new UiRect(panel.right() - size - layout.unit(), panel.y() + layout.unit(), size, size);
	}

	private static UiRect maxFileShareHintRect(UiLayout layout) {
		UiRect title = maxAvatarPickerTitleRect(layout);
		UiRect send = maxFileShareSendRect(layout);
		return new UiRect(title.x(), title.bottom(), Math.max(1, send.x() - title.x() - layout.unit()), clampInt(layout.unit() * 2, 16, 24));
	}

	private static UiRect maxFileShareContactCheckRect(UiRect row, UiLayout layout) {
		int size = clampInt(layout.unit() * 2 + 4, 24, 34);
		return new UiRect(row.right() - size - layout.unit(), row.y() + (row.height() - size) / 2, size, size);
	}

	private static UiRect maxNotificationCardRect(UiLayout layout) {
		UiRect grid = maxAvatarPickerGridRect(layout);
		int verticalInset = clampInt(layout.unit(), 6, 18);
		return new UiRect(grid.x(), grid.y() + verticalInset, grid.width(), Math.max(1, grid.height() - verticalInset * 2));
	}

	private static UiRect maxNotificationAvatarRect(UiLayout layout) {
		UiRect card = maxNotificationCardRect(layout);
		int size = clampInt(Math.min(card.width(), card.height()) / 4, 34, 92);
		return new UiRect(card.x() + (card.width() - size) / 2, card.y() + clampInt(layout.unit() * 2, 12, 28), size, size);
	}

	private static UiRect maxNotificationSenderRect(UiLayout layout) {
		UiRect avatar = maxNotificationAvatarRect(layout);
		UiRect card = maxNotificationCardRect(layout);
		return new UiRect(card.x() + layout.unit(), avatar.bottom() + layout.unit(), card.width() - layout.unit() * 2, clampInt(layout.unit() * 3, 24, 38));
	}

	private static UiRect maxNotificationFileRect(UiLayout layout) {
		UiRect sender = maxNotificationSenderRect(layout);
		UiRect card = maxNotificationCardRect(layout);
		return new UiRect(card.x() + layout.unit(), sender.bottom(), card.width() - layout.unit() * 2, clampInt(layout.unit() * 3, 24, 38));
	}

	private static UiRect maxNotificationSubtitleRect(UiLayout layout) {
		UiRect file = maxNotificationFileRect(layout);
		UiRect card = maxNotificationCardRect(layout);
		return new UiRect(card.x() + layout.unit(), file.bottom(), card.width() - layout.unit() * 2, clampInt(layout.unit() * 2, 16, 28));
	}

	private static UiRect maxNotificationAcceptRect(UiLayout layout) {
		UiRect card = maxNotificationCardRect(layout);
		int gap = clampInt(layout.unit(), 6, 18);
		int size = clampInt(Math.min(card.width(), card.height()) / 6, 22, 48);
		int y = card.bottom() - size - clampInt(layout.unit() * 2, 12, 28);
		return new UiRect(card.x() + (card.width() - size * 2 - gap) / 2, y, size, size);
	}

	private static UiRect maxNotificationDeclineRect(UiLayout layout) {
		UiRect accept = maxNotificationAcceptRect(layout);
		return new UiRect(accept.right() + clampInt(layout.unit(), 6, 18), accept.y(), accept.width(), accept.height());
	}

	private static UiRect maxNotificationQueueRect(UiLayout layout) {
		UiRect accept = maxNotificationAcceptRect(layout);
		UiRect card = maxNotificationCardRect(layout);
		return new UiRect(card.x() + layout.unit(), accept.y() - clampInt(layout.unit() * 2, 16, 26), card.width() - layout.unit() * 2, clampInt(layout.unit() * 2, 16, 26));
	}

	private static int maxContactPickerCapacity(UiLayout layout) {
		UiRect list = maxContactPickerListRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int row = maxContactRowHeight(layout);
		return Math.max(1, list.height() / Math.max(1, row + gap));
	}

	private static UiRect maxContactPickerRowRect(UiLayout layout, int index) {
		UiRect list = maxContactPickerListRect(layout);
		int gap = Math.max(4, layout.unit() / 2);
		int height = maxContactRowHeight(layout);
		return new UiRect(list.x(), list.y() + index * (height + gap), list.width(), height);
	}

	private static int maxContactPickerIndexAt(UiLayout layout, int contactCount, UiPoint point) {
		int count = Math.min(contactCount, maxContactPickerCapacity(layout));
		for (int index = 0; index < count; index++) {
			if (maxContactPickerRowRect(layout, index).contains(point.x(), point.y())) {
				return index;
			}
		}
		return -1;
	}

	private record PersistedMaxState(
			String accountCode,
			String avatarUrl,
			String avatarLocalMediaKey,
			List<String> contacts,
			String selectedCameraUrl,
			String selectedMicrophoneKey,
			int selectedMicrophoneIndex,
			boolean cameraEnabled,
			boolean microphoneEnabled,
			String ringtoneUrl,
			String ringtoneLocalMediaKey,
			String ringtoneTitle,
			List<MaxIncomingFile> incomingFiles
	) {
	}

	private static final class MaxRuntimeState {
		private boolean hydrated;
		private long version;
		private String accountCode = "";
		private String avatarUrl = "";
		private String avatarLocalMediaKey = "";
		private BufferedImage avatarFrame;
		private MonitorMediaApp.LoadedMedia avatarMedia;
		private long avatarAnimationStartedAtMillis;
		private boolean avatarRenderScheduled;
		private final List<String> contacts = new ArrayList<>();
		private String selectedCameraUrl = "";
		private String selectedMicrophoneKey = "";
		private int selectedMicrophoneIndex = -1;
		private String ringtoneUrl = "";
		private String ringtoneLocalMediaKey = "";
		private String ringtoneTitle = "";
		private boolean cameraEnabled = true;
		private boolean microphoneEnabled = true;
		private boolean avatarPickerOpen;
		private boolean ringtonePickerOpen;
		private boolean ringtonePreviewPlaying;
		private String ringtonePreviewUrl = MAX_DEFAULT_RINGTONE_URL;
		private String ringtonePreviewLocalMediaKey = "";
		private String ringtonePreviewTitle = "";
		private long ringtonePreviewStartedAtMillis;
		private boolean callMenuOpen;
		private boolean cameraPickerOpen;
		private int cameraPickerScroll;
		private int microphonePickerScroll;
		private boolean callContactPickerOpen;
		private boolean focusSelf;
		private boolean focusPeer;
		private ScreenRuntimeKey focusedPeerKey;
		private String statusText = "";
		private BufferedImage localFrame;
		private String localVideoUrl = "";
		private BufferedImage remoteFrame;
		private String remoteVideoUrl = "";
		private final List<MaxIncomingFile> incomingFiles = new ArrayList<>();
		private boolean notificationsOpen;
		private boolean fileSharePickerOpen;
		private final List<MaxSharedGalleryFile> fileShareFiles = new ArrayList<>();
		private final Set<String> fileShareSelectedContacts = new LinkedHashSet<>();
	}

	private record MaxSharedGalleryFile(
			String title,
			String subtitle,
			String url,
			String localMediaKey,
			GalleryItemKind kind
	) {
	}

	private record MaxIncomingFile(
			String id,
			String senderCode,
			String title,
			String subtitle,
			String url,
			String localMediaKey,
			GalleryItemKind kind,
			long createdAtMillis
	) {
	}

	private record MaxPreparedFileDelivery(String recipientCode, MaxIncomingFile file) {
	}

	private static final class MaxCallSession {
		private final UUID id;
		private final ScreenRuntimeKey caller;
		private final ScreenRuntimeKey callee;
		private final String callerCode;
		private final String calleeCode;
		private final long createdAtMillis;
		private final LinkedHashSet<ScreenRuntimeKey> participants = new LinkedHashSet<>();
		private final Set<ScreenRuntimeKey> acceptedParticipants = new LinkedHashSet<>();
		private final Set<ScreenRuntimeKey> ringingParticipants = new LinkedHashSet<>();
		private final Map<ScreenRuntimeKey, String> participantCodes = new ConcurrentHashMap<>();
		private final Map<ScreenRuntimeKey, ScreenRuntimeKey> ringingInviters = new ConcurrentHashMap<>();
		private volatile boolean accepted;
		private volatile long acceptedAtMillis;

		private MaxCallSession(UUID id, ScreenRuntimeKey caller, ScreenRuntimeKey callee, String callerCode, String calleeCode) {
			this.id = id;
			this.caller = caller;
			this.callee = callee;
			this.callerCode = callerCode;
			this.calleeCode = calleeCode;
			this.createdAtMillis = System.currentTimeMillis();
			this.participants.add(caller);
			this.participants.add(callee);
			this.acceptedParticipants.add(caller);
			this.ringingParticipants.add(callee);
			this.participantCodes.put(caller, callerCode);
			this.participantCodes.put(callee, calleeCode);
			this.ringingInviters.put(callee, caller);
		}

		private synchronized boolean addInvitee(ScreenRuntimeKey inviter, ScreenRuntimeKey invitee, String code) {
			if (inviter == null || invitee == null || !this.acceptedParticipants.contains(inviter) || this.participants.contains(invitee)) {
				return false;
			}
			this.participants.add(invitee);
			this.ringingParticipants.add(invitee);
			this.participantCodes.put(invitee, code);
			this.ringingInviters.put(invitee, inviter);
			return true;
		}

		private synchronized void accept(ScreenRuntimeKey key) {
			if (key == null || !this.participants.contains(key)) {
				return;
			}
			this.ringingParticipants.remove(key);
			this.ringingInviters.remove(key);
			this.acceptedParticipants.add(key);
			if (this.acceptedParticipants.size() >= 2) {
				this.accepted = true;
				if (this.acceptedAtMillis <= 0L) {
					this.acceptedAtMillis = System.currentTimeMillis();
				}
			}
		}

		private synchronized void removeParticipant(ScreenRuntimeKey key) {
			if (key == null) {
				return;
			}
			this.participants.remove(key);
			this.acceptedParticipants.remove(key);
			this.ringingParticipants.remove(key);
			this.participantCodes.remove(key);
			this.ringingInviters.remove(key);
			this.accepted = this.acceptedParticipants.size() >= 2;
		}

		private synchronized ScreenRuntimeKey inviterFor(ScreenRuntimeKey key) {
			ScreenRuntimeKey inviter = this.ringingInviters.get(key);
			return inviter != null ? inviter : this.caller;
		}

		private synchronized String participantCode(ScreenRuntimeKey key) {
			String code = this.participantCodes.get(key);
			if (code != null && !code.isBlank()) {
				return code;
			}
			if (Objects.equals(this.caller, key)) {
				return this.callerCode;
			}
			if (Objects.equals(this.callee, key)) {
				return this.calleeCode;
			}
			return "";
		}

		private synchronized boolean isParticipant(ScreenRuntimeKey key) {
			return key != null && this.participants.contains(key);
		}

		private synchronized boolean isRinging(ScreenRuntimeKey key) {
			return key != null && this.ringingParticipants.contains(key);
		}

		private synchronized boolean isAccepted(ScreenRuntimeKey key) {
			return key != null && this.acceptedParticipants.contains(key);
		}

		private synchronized int acceptedParticipantCount() {
			return this.acceptedParticipants.size();
		}

		private synchronized List<ScreenRuntimeKey> participants() {
			return List.copyOf(this.participants);
		}

		private synchronized List<ScreenRuntimeKey> acceptedParticipants() {
			return List.copyOf(this.acceptedParticipants);
		}

		private synchronized List<ScreenRuntimeKey> otherParticipants(ScreenRuntimeKey key) {
			List<ScreenRuntimeKey> others = new ArrayList<>();
			for (ScreenRuntimeKey participant : this.participants) {
				if (!Objects.equals(participant, key)) {
					others.add(participant);
				}
			}
			return List.copyOf(others);
		}

		private synchronized ScreenRuntimeKey peer(ScreenRuntimeKey key) {
			ScreenRuntimeKey fallback = null;
			for (ScreenRuntimeKey participant : this.participants) {
				if (Objects.equals(participant, key)) {
					continue;
				}
				if (fallback == null) {
					fallback = participant;
				}
				if (this.acceptedParticipants.contains(participant)) {
					return participant;
				}
			}
			return fallback;
		}

		private synchronized String peerCode(ScreenRuntimeKey key) {
			ScreenRuntimeKey peer = peer(key);
			if (peer != null) {
				String code = this.participantCodes.get(peer);
				if (code != null && !code.isBlank()) {
					return code;
				}
			}
			return Objects.equals(this.caller, key) ? this.calleeCode : this.callerCode;
		}
	}
}
