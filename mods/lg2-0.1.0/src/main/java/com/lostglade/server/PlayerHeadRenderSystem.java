package com.lostglade.server;

import com.mojang.authlib.properties.Property;
import it.unimi.dsi.fastutil.Pair;
import net.lionarius.skinrestorer.SkinRestorer;
import net.lionarius.skinrestorer.skin.SkinStorage;
import net.lionarius.skinrestorer.skin.SkinValue;
import net.lionarius.skinrestorer.util.PlayerUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

final class PlayerHeadRenderSystem {
	private static final long RETRY_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(45L);
	private static final BufferedImage EMPTY_HEAD = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
	private static final Map<String, BufferedImage> HEAD_CACHE = new ConcurrentHashMap<>();
	// The UI compositor can safely use this identity cache without looking at
	// the player list.  Resolving a skin property itself remains server-thread
	// work and is requested explicitly below.
	private static final Map<String, BufferedImage> HEAD_BY_IDENTITY = new ConcurrentHashMap<>();
	private static final Map<String, BufferedImage> PLACEHOLDER_CACHE = new ConcurrentHashMap<>();
	private static final Map<String, Long> RETRY_AT_MS = new ConcurrentHashMap<>();
	private static final Map<String, CopyOnWriteArrayList<Runnable>> READY_CALLBACKS = new ConcurrentHashMap<>();
	private static final java.util.Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();

	private PlayerHeadRenderSystem() {
	}

	static void clearRuntime() {
		HEAD_CACHE.clear();
		HEAD_BY_IDENTITY.clear();
		PLACEHOLDER_CACHE.clear();
		RETRY_AT_MS.clear();
		READY_CALLBACKS.clear();
		IN_FLIGHT.clear();
	}

	static BufferedImage resolveHead(MinecraftServer server, UUID playerId, String playerName, Runnable onReady) {
		HeadLookup lookup = resolveLookup(server, playerId, playerName);
		BufferedImage cached = HEAD_CACHE.get(lookup.cacheKey());
		if (cached != null) {
			HEAD_BY_IDENTITY.put(lookup.identity(), cached);
			return cached == EMPTY_HEAD ? placeholderHead(lookup.playerName()) : cached;
		}
		queueHeadLoad(lookup, onReady);
		return placeholderHead(lookup.playerName());
	}

	/**
	 * Returns a cached head (or a deterministic placeholder) without touching
	 * MinecraftServer.  It is safe for MonitorScreenSystem's render executor.
	 */
	static BufferedImage cachedHead(UUID playerId, String playerName) {
		BufferedImage cached = HEAD_BY_IDENTITY.get(identityKey(playerId, playerName));
		return cached == null || cached == EMPTY_HEAD ? placeholderHead(playerName) : cached;
	}

	static boolean hasCachedHead(UUID playerId, String playerName) {
		return HEAD_BY_IDENTITY.containsKey(identityKey(playerId, playerName));
	}

	/**
	 * Must be called from the server thread.  It resolves the current skin
	 * property and starts the asynchronous image fetch when needed.
	 */
	static boolean requestHead(MinecraftServer server, UUID playerId, String playerName, Runnable onReady) {
		HeadLookup lookup = resolveLookup(server, playerId, playerName);
		BufferedImage cached = HEAD_CACHE.get(lookup.cacheKey());
		if (cached != null) {
			HEAD_BY_IDENTITY.put(lookup.identity(), cached);
			return false;
		}
		return queueHeadLoad(lookup, onReady);
	}

	private static boolean queueHeadLoad(HeadLookup lookup, Runnable onReady) {
		if (lookup == null || lookup.skinProperty() == null) {
			return false;
		}
		if (onReady != null) {
			READY_CALLBACKS.computeIfAbsent(lookup.cacheKey(), ignored -> new CopyOnWriteArrayList<>()).add(onReady);
		}
		maybeStartLoad(lookup);
		return true;
	}

	private static HeadLookup resolveLookup(MinecraftServer server, UUID playerId, String playerName) {
		String safeName = sanitizeName(playerName);
		Property property = resolveSkinProperty(server, playerId);
		int propertyHash = property == null ? 0 : Objects.hash(property.name(), property.value(), property.signature());
		String identity = identityKey(playerId, safeName);
		return new HeadLookup(identity, identity + ":" + Integer.toHexString(propertyHash), property, safeName);
	}

	private static Property resolveSkinProperty(MinecraftServer server, UUID playerId) {
		if (playerId == null) {
			return null;
		}
		ServerPlayer onlinePlayer = server != null ? server.getPlayerList().getPlayer(playerId) : null;
		if (onlinePlayer != null && onlinePlayer.getGameProfile() != null) {
			Property current = PlayerUtils.getPlayerSkin(onlinePlayer.getGameProfile());
			if (current != null && current.value() != null && !current.value().isBlank()) {
				return current;
			}
		}
		try {
			SkinStorage storage = SkinRestorer.getSkinStorage();
			if (storage == null || !storage.hasSavedSkin(playerId)) {
				return null;
			}
			SkinValue stored = storage.getSkin(playerId);
			Property property = stored != null ? stored.value() : null;
			return property != null && property.value() != null && !property.value().isBlank() ? property : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private static void maybeStartLoad(HeadLookup lookup) {
		long now = System.currentTimeMillis();
		Long retryAt = RETRY_AT_MS.get(lookup.cacheKey());
		if (retryAt != null && now < retryAt) {
			return;
		}
		if (!IN_FLIGHT.add(lookup.cacheKey())) {
			return;
		}
		CompletableFuture.runAsync(() -> loadHeadAsync(lookup));
	}

	private static void loadHeadAsync(HeadLookup lookup) {
		BufferedImage head = null;
		long now = System.currentTimeMillis();
		try {
			head = buildHeadImage(lookup.skinProperty());
			if (head != null) {
				HEAD_CACHE.put(lookup.cacheKey(), head);
				HEAD_BY_IDENTITY.put(lookup.identity(), head);
				RETRY_AT_MS.remove(lookup.cacheKey());
			} else {
				RETRY_AT_MS.put(lookup.cacheKey(), now + RETRY_COOLDOWN_MS);
			}
		} finally {
			IN_FLIGHT.remove(lookup.cacheKey());
			CopyOnWriteArrayList<Runnable> callbacks = READY_CALLBACKS.remove(lookup.cacheKey());
			if (callbacks != null) {
				for (Runnable callback : callbacks) {
					if (callback == null) {
						continue;
					}
					try {
						callback.run();
					} catch (Exception ignored) {
					}
				}
			}
		}
	}

	private static BufferedImage buildHeadImage(Property property) {
		if (property == null) {
			return null;
		}
		try {
			Pair<String, ?> skinData = PlayerUtils.getSkinUrl(property);
			String skinUrl = skinData != null ? skinData.first() : null;
			if (skinUrl == null || skinUrl.isBlank()) {
				return null;
			}
			BufferedImage skin = loadSkinImage(new URI(skinUrl));
			if (skin == null || skin.getWidth() < 48 || skin.getHeight() < 16) {
				return null;
			}
			BufferedImage head = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = head.createGraphics();
			try {
				graphics.setComposite(AlphaComposite.Src);
				graphics.drawImage(skin.getSubimage(8, 8, 8, 8), 0, 0, null);
				graphics.setComposite(AlphaComposite.SrcOver);
				graphics.drawImage(skin.getSubimage(40, 8, 8, 8), 0, 0, null);
				return head;
			} finally {
				graphics.dispose();
			}
		} catch (Exception ignored) {
			return null;
		}
	}

	private static BufferedImage loadSkinImage(URI uri) throws IOException {
		try (InputStream stream = uri.toURL().openStream()) {
			BufferedImage image = ImageIO.read(stream);
			if (image == null) {
				return null;
			}
			return normalizeSkinImage(toArgb(image));
		}
	}

	private static BufferedImage normalizeSkinImage(BufferedImage image) {
		if (image.getWidth() == 64 && image.getHeight() == 64) {
			return image;
		}
		if (image.getWidth() == 64 && image.getHeight() == 32) {
			BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = normalized.createGraphics();
			try {
				graphics.setComposite(AlphaComposite.Src);
				graphics.drawImage(image, 0, 0, null);
			} finally {
				graphics.dispose();
			}
			return normalized;
		}
		BufferedImage normalized = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = normalized.createGraphics();
		try {
			graphics.setComposite(AlphaComposite.Src);
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			graphics.drawImage(image, 0, 0, 64, 64, null);
			return normalized;
		} finally {
			graphics.dispose();
		}
	}

	private static BufferedImage toArgb(BufferedImage image) {
		if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
			return image;
		}
		BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = converted.createGraphics();
		graphics.setComposite(AlphaComposite.Src);
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return converted;
	}

	private static BufferedImage placeholderHead(String playerName) {
		String safeName = sanitizeName(playerName);
		return PLACEHOLDER_CACHE.computeIfAbsent(safeName.toLowerCase(Locale.ROOT), ignored -> buildPlaceholderHead(safeName));
	}

	private static BufferedImage buildPlaceholderHead(String playerName) {
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			graphics.setComposite(AlphaComposite.Src);
			Color base = placeholderColor(playerName);
			graphics.setColor(base);
			graphics.fillRoundRect(0, 0, 32, 32, 10, 10);
			graphics.setColor(new Color(255, 255, 255, 26));
			graphics.fillRoundRect(0, 0, 32, 12, 10, 10);
			graphics.setColor(new Color(255, 255, 255, 226));
			graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
			FontMetrics metrics = graphics.getFontMetrics();
			String initial = firstGrapheme(playerName);
			int x = (32 - metrics.stringWidth(initial)) / 2;
			int y = (32 - metrics.getHeight()) / 2 + metrics.getAscent();
			graphics.drawString(initial, x, y);
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static Color placeholderColor(String playerName) {
		int hash = Math.abs(Objects.hashCode(playerName));
		float hue = (hash % 360) / 360.0F;
		return Color.getHSBColor(hue, 0.38F, 0.72F);
	}

	private static String firstGrapheme(String playerName) {
		String safeName = sanitizeName(playerName);
		return safeName.isEmpty() ? "?" : safeName.substring(0, 1).toUpperCase(Locale.ROOT);
	}

	private static String sanitizeName(String playerName) {
		if (playerName == null) {
			return "Player";
		}
		String trimmed = playerName.trim();
		return trimmed.isEmpty() ? "Player" : trimmed;
	}

	private static String identityKey(UUID playerId, String playerName) {
		return playerId != null ? playerId.toString() : sanitizeName(playerName).toLowerCase(Locale.ROOT);
	}

	private record HeadLookup(
			String identity,
			String cacheKey,
			Property skinProperty,
			String playerName
	) {
	}
}
