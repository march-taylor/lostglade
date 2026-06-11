package com.lostglade.server;

import com.lostglade.Lg2;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

final class MonitorCoverPlaceholderRenderer {
	private static final int COVER_SIZE = 640;
	private static final Path RECORDER_DISC_PATH = Path.of("/home/mart/Pictures/pixel/recorder_lg.png");
	private static final Path RECORDER_CENTER_PATH = Path.of("/home/mart/Pictures/pixel/recorder_lg_center.png");
	private static volatile BufferedImage recorderDisc;
	private static volatile BufferedImage recorderCenter;

	private MonitorCoverPlaceholderRenderer() {
	}

	static BufferedImage recordedAudioCover(String identifier, String subtitle, BufferedImage cameraFrame, String seed) {
		String resolvedIdentifier = identifier == null || identifier.isBlank() ? "REC" : identifier.trim();
		String resolvedSubtitle = subtitle == null || subtitle.isBlank() ? "AUDIO" : subtitle.trim();
		long randomSeed = seed != null && !seed.isBlank() ? seed.hashCode() * 31L + resolvedIdentifier.hashCode() : System.nanoTime();
		Random random = new Random(randomSeed);

		BufferedImage image = new BufferedImage(COVER_SIZE, COVER_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			configure(graphics);
			if (cameraFrame != null) {
				drawCoveredImage(graphics, cameraFrame, 0, 0, COVER_SIZE, COVER_SIZE);
				graphics.setPaint(new GradientPaint(0, 0, new Color(4, 8, 12, 116), COVER_SIZE, COVER_SIZE, new Color(3, 7, 10, 226)));
				graphics.fillRect(0, 0, COVER_SIZE, COVER_SIZE);
			} else {
				drawGeneratedBackdrop(graphics, random);
			}
			drawVignette(graphics);
			drawRecorderDisc(graphics, random);
			drawIdentifier(graphics, resolvedIdentifier, resolvedSubtitle);
		} finally {
			graphics.dispose();
		}
		return image;
	}

	private static void configure(Graphics2D graphics) {
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	}

	private static void drawGeneratedBackdrop(Graphics2D graphics, Random random) {
		Color top = new Color(13 + random.nextInt(20), 24 + random.nextInt(28), 34 + random.nextInt(34));
		Color bottom = new Color(64 + random.nextInt(56), 28 + random.nextInt(44), 76 + random.nextInt(52));
		graphics.setPaint(new GradientPaint(0, 0, top, COVER_SIZE, COVER_SIZE, bottom));
		graphics.fillRect(0, 0, COVER_SIZE, COVER_SIZE);
		graphics.setComposite(AlphaComposite.SrcOver.derive(0.18F));
		graphics.setColor(new Color(255, 255, 255));
		for (int index = 0; index < 9; index++) {
			int x = random.nextInt(COVER_SIZE);
			int y = random.nextInt(COVER_SIZE);
			int size = 48 + random.nextInt(130);
			graphics.fillOval(x - size / 2, y - size / 2, size, size);
		}
		graphics.setComposite(AlphaComposite.SrcOver);
	}

	private static void drawVignette(Graphics2D graphics) {
		graphics.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 52), 0, COVER_SIZE, new Color(0, 0, 0, 206)));
		graphics.fillRect(0, 0, COVER_SIZE, COVER_SIZE);
		graphics.setColor(new Color(255, 255, 255, 30));
		graphics.setStroke(new BasicStroke(2.0F));
		graphics.draw(new RoundRectangle2D.Float(18, 18, COVER_SIZE - 36, COVER_SIZE - 36, 34, 34));
	}

	private static void drawRecorderDisc(Graphics2D graphics, Random random) {
		BufferedImage disc = recorderDisc();
		int size = 280 + random.nextInt(62);
		int centerX = COVER_SIZE / 2 + random.nextInt(70) - 35;
		int centerY = COVER_SIZE / 2 - 42 + random.nextInt(44);
		double rotation = random.nextDouble() * Math.PI * 2.0D;
		float centerHueShift = random.nextFloat();
		AffineTransform previous = graphics.getTransform();
		graphics.translate(centerX, centerY);
		graphics.rotate(rotation);
		graphics.setComposite(AlphaComposite.SrcOver.derive(0.34F));
		graphics.setColor(Color.BLACK);
		graphics.fillOval(-size / 2 + 18, -size / 2 + 24, size, size);
		graphics.setComposite(AlphaComposite.SrcOver);
		Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		if (disc != null) {
			graphics.drawImage(disc, -size / 2, -size / 2, size, size, null);
		} else {
			graphics.setColor(new Color(12, 18, 26, 236));
			graphics.fillOval(-size / 2, -size / 2, size, size);
			graphics.setColor(new Color(80, 210, 255, 220));
			graphics.drawOval(-size / 3, -size / 3, size * 2 / 3, size * 2 / 3);
		}
		BufferedImage center = hueShifted(recorderCenter(), centerHueShift);
		if (center != null) {
			graphics.drawImage(center, -size / 2, -size / 2, size, size, null);
		}
		if (interpolation != null) {
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		}
		graphics.setTransform(previous);
	}

	private static void drawIdentifier(Graphics2D graphics, String identifier, String subtitle) {
		int panelX = 54;
		int panelY = COVER_SIZE - 190;
		int panelWidth = COVER_SIZE - 108;
		int panelHeight = 126;
		graphics.setColor(new Color(5, 8, 12, 178));
		graphics.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 28, 28);
		graphics.setColor(new Color(255, 255, 255, 48));
		graphics.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 28, 28);

		graphics.setColor(new Color(184, 212, 230, 220));
		graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
		graphics.drawString(subtitle.toUpperCase(Locale.ROOT), panelX + 28, panelY + 42);

		graphics.setFont(fittedFont(graphics, identifier, Font.BOLD, 56, panelWidth - 56));
		graphics.setColor(new Color(248, 251, 255, 242));
		graphics.drawString(identifier, panelX + 28, panelY + 96);
	}

	private static Font fittedFont(Graphics2D graphics, String text, int style, int startSize, int maxWidth) {
		int size = startSize;
		Font font = new Font(Font.SANS_SERIF, style, size);
		while (size > 20 && graphics.getFontMetrics(font).stringWidth(text) > maxWidth) {
			size -= 2;
			font = new Font(Font.SANS_SERIF, style, size);
		}
		return font;
	}

	private static void drawCoveredImage(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height) {
		double scale = Math.max(width / (double) image.getWidth(), height / (double) image.getHeight());
		int drawWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int drawHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		int drawX = x + (width - drawWidth) / 2;
		int drawY = y + (height - drawHeight) / 2;
		graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
	}

	private static BufferedImage recorderDisc() {
		BufferedImage cached = recorderDisc;
		if (cached != null) {
			return cached;
		}
		try {
			if (!Files.isRegularFile(RECORDER_DISC_PATH)) {
				return null;
			}
			BufferedImage loaded = ImageIO.read(RECORDER_DISC_PATH.toFile());
			recorderDisc = loaded;
			return loaded;
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to load recorder cover disc", exception);
			return null;
		}
	}

	private static BufferedImage recorderCenter() {
		BufferedImage cached = recorderCenter;
		if (cached != null) {
			return cached;
		}
		try {
			if (!Files.isRegularFile(RECORDER_CENTER_PATH)) {
				return null;
			}
			BufferedImage loaded = ImageIO.read(RECORDER_CENTER_PATH.toFile());
			recorderCenter = loaded;
			return loaded;
		} catch (Exception exception) {
			Lg2.LOGGER.debug("Failed to load recorder cover center", exception);
			return null;
		}
	}

	private static BufferedImage hueShifted(BufferedImage source, float hueShift) {
		if (source == null) {
			return null;
		}
		BufferedImage shifted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		float[] hsb = new float[3];
		for (int y = 0; y < source.getHeight(); y++) {
			for (int x = 0; x < source.getWidth(); x++) {
				int argb = source.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				if (alpha <= 0) {
					continue;
				}
				int red = (argb >>> 16) & 0xFF;
				int green = (argb >>> 8) & 0xFF;
				int blue = argb & 0xFF;
				Color.RGBtoHSB(red, green, blue, hsb);
				int shiftedRgb = Color.HSBtoRGB((hsb[0] + hueShift) % 1.0F, hsb[1], hsb[2]);
				shifted.setRGB(x, y, (alpha << 24) | (shiftedRgb & 0x00FFFFFF));
			}
		}
		return shifted;
	}
}
