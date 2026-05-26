package com.lostglade.server;

import static com.lostglade.server.MonitorScreenMessages.*;
import static com.lostglade.server.MonitorScreenMapTransport.*;
import static com.lostglade.server.MonitorScreenGalleryRuntime.*;
import static com.lostglade.server.MonitorScreenLiveSources.*;
import static com.lostglade.server.MonitorScreenMediaActions.*;
import static com.lostglade.server.MonitorScreenSystem.*;
import static com.lostglade.server.MonitorScreenMediaHydration.*;
import static com.lostglade.server.MonitorScreenPlaybackScheduler.*;
import static com.lostglade.server.MonitorScreenTickScheduler.*;

import com.lostglade.Lg2;
import com.lostglade.block.CameraBlock;
import com.lostglade.block.ModBlocks;
import com.lostglade.config.Lg2Config;
import com.lostglade.item.ModItems;
import com.lostglade.item.MonitorItem;
import com.lostglade.item.PhotoPrintData;
import com.lostglade.server.map.MapPaletteQuantizer;
import com.lostglade.server.monitor.MonitorApp;
import com.lostglade.server.monitor.MonitorAppRole;
import com.lostglade.server.monitor.MonitorAppRegistry;
import com.lostglade.server.monitor.MonitorBackgroundPlaybackPolicy;
import com.lostglade.server.monitor.MonitorMediaApp;
import com.lostglade.server.monitor.MonitorSberDronesCatalog;
import com.lostglade.server.monitor.MonitorYoutubeRelayClient;
import com.lostglade.server.monitor.MonitorYoutubeMusicCache;
import com.lostglade.server.progress.TaskProgress;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

final class MonitorScreenBackgroundLoader {
	private MonitorScreenBackgroundLoader() {
	}

	static void scheduleWallpaperLoad(MinecraftServer server, ScreenRuntimeKey key, String url, String localMediaKey) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		long sessionGeneration;
		synchronized (state) {
			if (state.wallpaperLoading || (state.wallpaperMedia != null && Objects.equals(state.wallpaperUrl, url))) {
				return;
			}
			state.wallpaperLoading = true;
			sessionGeneration = state.sessionGeneration;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new WallpaperLoadResult(
								key,
								url,
								localMediaKey,
								loadGalleryMedia(url, localMediaKey, null),
								sessionGeneration,
								null
						);
					} catch (Exception exception) {
						return new WallpaperLoadResult(key, url, localMediaKey, null, sessionGeneration, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyWallpaperLoadResult(server, result)));
	}

	static void schedulePlayerBackgroundLoad(MinecraftServer server, ScreenRuntimeKey key, String url, String localMediaKey) {
		if (server == null || key == null || url == null || url.isBlank()) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(key);
		if (state == null) {
			return;
		}
		long sessionGeneration;
		synchronized (state) {
			if (state.playerBackgroundLoading || (state.playerBackgroundMedia != null && Objects.equals(state.playerBackgroundUrl, url))) {
				return;
			}
			state.playerBackgroundLoading = true;
			sessionGeneration = state.sessionGeneration;
		}
		ensureExecutors();
		CompletableFuture
				.supplyAsync(() -> {
					try {
						return new PlayerBackgroundLoadResult(
								key,
								url,
								localMediaKey,
								loadGalleryMedia(url, localMediaKey, null),
								sessionGeneration,
								null
						);
					} catch (Exception exception) {
						return new PlayerBackgroundLoadResult(key, url, localMediaKey, null, sessionGeneration, sanitizeMediaError(exception.getMessage()));
					}
				}, mediaIoExecutor)
				.thenAccept(result -> server.execute(() -> applyPlayerBackgroundLoadResult(server, result)));
	}

	static void applyWallpaperLoadResult(MinecraftServer server, WallpaperLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		boolean shouldRender = false;
		boolean shouldAnimate = false;
		synchronized (state) {
			if (result.sessionGeneration() != state.sessionGeneration) {
				return;
			}
			state.wallpaperLoading = false;
			if (!Objects.equals(state.wallpaperUrl, result.url())) {
				return;
			}
			if (result.loadedMedia() == null) {
				Lg2.LOGGER.debug("Failed to load monitor wallpaper {}: {}", result.url(), result.error());
				return;
			}
			state.wallpaperMedia = result.loadedMedia();
			state.wallpaperFrameIndex = 0;
			int galleryIndex = resolveGalleryItemIndex(state, result.url(), -1);
			if (galleryIndex >= 0 && galleryIndex < state.galleryItems.size()) {
				GalleryItem existing = state.galleryItems.get(galleryIndex);
				if (existing != null && existing.kind() == GalleryItemKind.MEDIA) {
					state.galleryItems.set(
							galleryIndex,
							new GalleryItem(
									existing.title(),
									existing.subtitle(),
									existing.url(),
									existing.localMediaKey(),
									result.loadedMedia(),
									result.loadedMedia().frameCount() > 0 ? result.loadedMedia().frame(0) : existing.preview(),
									existing.kind()
							)
					);
				}
			}
			compactGalleryRuntimeMediaLocked(state);
			state.version++;
			shouldRender = true;
			shouldAnimate = wallpaperAnimationActiveLocked(state);
		}
		if (shouldRender) {
			requestRuntimeRender(server, result.screenKey());
		}
		if (shouldAnimate) {
			scheduleBackgroundPlaybackIfNeeded(server, result.screenKey());
		}
	}

	static void applyPlayerBackgroundLoadResult(MinecraftServer server, PlayerBackgroundLoadResult result) {
		if (server == null || result == null) {
			return;
		}
		MediaRuntimeState state = MEDIA_STATES.get(result.screenKey());
		if (state == null) {
			return;
		}
		boolean shouldRender = false;
		boolean shouldAnimate = false;
		synchronized (state) {
			if (result.sessionGeneration() != state.sessionGeneration) {
				return;
			}
			state.playerBackgroundLoading = false;
			if (!Objects.equals(state.playerBackgroundUrl, result.url())) {
				return;
			}
			if (result.loadedMedia() == null) {
				Lg2.LOGGER.debug("Failed to load monitor player background {}: {}", result.url(), result.error());
				return;
			}
			state.playerBackgroundMedia = result.loadedMedia();
			state.playerBackgroundFrameIndex = 0;
			int galleryIndex = resolveGalleryItemIndex(state, result.url(), -1);
			if (galleryIndex >= 0 && galleryIndex < state.galleryItems.size()) {
				GalleryItem existing = state.galleryItems.get(galleryIndex);
				if (existing != null && existing.kind() == GalleryItemKind.MEDIA) {
					state.galleryItems.set(
							galleryIndex,
							new GalleryItem(
									existing.title(),
									existing.subtitle(),
									existing.url(),
									existing.localMediaKey(),
									result.loadedMedia(),
									result.loadedMedia().frameCount() > 0 ? result.loadedMedia().frame(0) : existing.preview(),
									existing.kind()
							)
					);
				}
			}
			compactGalleryRuntimeMediaLocked(state);
			state.version++;
			shouldRender = true;
			shouldAnimate = playerBackgroundAnimationActiveLocked(state);
		}
		if (shouldRender) {
			requestRuntimeRender(server, result.screenKey());
		}
		if (shouldAnimate) {
			scheduleBackgroundPlaybackIfNeeded(server, result.screenKey());
		}
	}
}
