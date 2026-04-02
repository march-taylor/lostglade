package com.lostglade.server.map;

import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

public final class MapPaletteQuantizer {
	private static final double REF_X = 0.95047D;
	private static final double REF_Y = 1.0D;
	private static final double REF_Z = 1.08883D;
	private static final int[] BAYER_8X8 = {
			0, 48, 12, 60, 3, 51, 15, 63,
			32, 16, 44, 28, 35, 19, 47, 31,
			8, 56, 4, 52, 11, 59, 7, 55,
			40, 24, 36, 20, 43, 27, 39, 23,
			2, 50, 14, 62, 1, 49, 13, 61,
			34, 18, 46, 30, 33, 17, 45, 29,
			10, 58, 6, 54, 9, 57, 5, 53,
			42, 26, 38, 22, 41, 25, 37, 21
	};
	private static final double[] SRGB_TO_LINEAR = buildSrgbToLinear();
	private static final PaletteEntry[] PALETTE = buildPalette();
	private static final byte[] RGB565_LOOKUP = buildRgb565Lookup();

	private MapPaletteQuantizer() {
	}

	public static byte quantize(int rgb) {
		return RGB565_LOOKUP[rgb565Key(rgb) & 0xFFFF];
	}

	private static byte quantizeExact(int rgb) {
		LabColor lab = toLab(rgb);
		double bestDistance = Double.MAX_VALUE;
		byte bestPackedId = PALETTE.length > 0 ? PALETTE[0].packedId() : 0;
		for (PaletteEntry entry : PALETTE) {
			double dl = lab.lightness() - entry.lightness();
			double da = lab.a() - entry.a();
			double db = lab.b() - entry.b();
			double distance = dl * dl + da * da + db * db;
			if (distance < bestDistance) {
				bestDistance = distance;
				bestPackedId = entry.packedId();
			}
		}
		return bestPackedId;
	}

	public static byte quantizeAverage(int[] colors, int count) {
		if (count <= 0) {
			return 0;
		}
		return quantize(averageRgb(colors, count));
	}

	public static byte quantizeDithered(int rgb, int x, int y) {
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		int max = Math.max(red, Math.max(green, blue));
		int min = Math.min(red, Math.min(green, blue));
		double saturation = max <= 0 ? 0.0D : (max - min) / (double) max;
		double saturationWeight = clamp01(saturation / 0.35D);
		double strength = 5.5D - 3.0D * saturationWeight;
		int threshold = BAYER_8X8[((y & 7) << 3) | (x & 7)] - 31;
		double delta = threshold * (strength / 31.0D);
		int ditheredRed = clampByte((int) Math.round(red + delta));
		int ditheredGreen = clampByte((int) Math.round(green + delta));
		int ditheredBlue = clampByte((int) Math.round(blue + delta));
		return quantize((ditheredRed << 16) | (ditheredGreen << 8) | ditheredBlue);
	}

	public static int averageRgb(int[] colors, int count) {
		if (count <= 0) {
			return 0;
		}
		if (count == 1) {
			return colors[0] & 0xFFFFFF;
		}
		double red = 0.0D;
		double green = 0.0D;
		double blue = 0.0D;
		for (int index = 0; index < count; index++) {
			int rgb = colors[index];
			red += toLinear((rgb >> 16) & 0xFF);
			green += toLinear((rgb >> 8) & 0xFF);
			blue += toLinear(rgb & 0xFF);
		}
		double inv = 1.0D / count;
		int r = toSrgb(red * inv);
		int g = toSrgb(green * inv);
		int b = toSrgb(blue * inv);
		return (r << 16) | (g << 8) | b;
	}

	public static int scaleRgb(int rgb, float factor) {
		double red = toLinear((rgb >> 16) & 0xFF) * factor;
		double green = toLinear((rgb >> 8) & 0xFF) * factor;
		double blue = toLinear(rgb & 0xFF) * factor;
		int r = toSrgb(red);
		int g = toSrgb(green);
		int b = toSrgb(blue);
		return (r << 16) | (g << 8) | b;
	}

	private static double[] buildSrgbToLinear() {
		double[] table = new double[256];
		for (int value = 0; value < table.length; value++) {
			double normalized = value / 255.0D;
			table[value] = normalized <= 0.04045D
					? normalized / 12.92D
					: Math.pow((normalized + 0.055D) / 1.055D, 2.4D);
		}
		return table;
	}

	private static PaletteEntry[] buildPalette() {
		MapColor.Brightness[] brightnesses = MapColor.Brightness.values();
		List<PaletteEntry> entries = new ArrayList<>((64 - 1) * brightnesses.length);
		for (int colorId = 1; colorId < 64; colorId++) {
			MapColor color = MapColor.byId(colorId);
			for (MapColor.Brightness brightness : brightnesses) {
				byte packedId = color.getPackedId(brightness);
				int unsignedPackedId = Byte.toUnsignedInt(packedId);
				if (unsignedPackedId < 4) {
					continue;
				}
				int packedRgb = MapColor.getColorFromPackedId(unsignedPackedId) & 0xFFFFFF;
				LabColor lab = toLab(packedRgb);
				entries.add(new PaletteEntry(
						packedId,
						lab.lightness(),
						lab.a(),
						lab.b()
				));
			}
		}
		return entries.toArray(PaletteEntry[]::new);
	}

	private static byte[] buildRgb565Lookup() {
		byte[] lookup = new byte[1 << 16];
		for (int key = 0; key < lookup.length; key++) {
			int red5 = (key >> 11) & 0x1F;
			int green6 = (key >> 5) & 0x3F;
			int blue5 = key & 0x1F;
			int red = (red5 << 3) | (red5 >> 2);
			int green = (green6 << 2) | (green6 >> 4);
			int blue = (blue5 << 3) | (blue5 >> 2);
			lookup[key] = quantizeExact((red << 16) | (green << 8) | blue);
		}
		return lookup;
	}

	private static int rgb565Key(int rgb) {
		int red = (rgb >> 16) & 0xFF;
		int green = (rgb >> 8) & 0xFF;
		int blue = rgb & 0xFF;
		return ((red >> 3) << 11) | ((green >> 2) << 5) | (blue >> 3);
	}

	private static double toLinear(int channel) {
		return SRGB_TO_LINEAR[channel & 0xFF];
	}

	private static LabColor toLab(int rgb) {
		double red = toLinear((rgb >> 16) & 0xFF);
		double green = toLinear((rgb >> 8) & 0xFF);
		double blue = toLinear(rgb & 0xFF);
		double x = red * 0.4124564D + green * 0.3575761D + blue * 0.1804375D;
		double y = red * 0.2126729D + green * 0.7151522D + blue * 0.0721750D;
		double z = red * 0.0193339D + green * 0.1191920D + blue * 0.9503041D;
		double fx = labPivot(x / REF_X);
		double fy = labPivot(y / REF_Y);
		double fz = labPivot(z / REF_Z);
		double lightness = 116.0D * fy - 16.0D;
		double a = 500.0D * (fx - fy);
		double b = 200.0D * (fy - fz);
		return new LabColor(lightness, a, b);
	}

	private static double labPivot(double value) {
		return value > 0.008856D ? Math.cbrt(value) : value * 7.787D + 16.0D / 116.0D;
	}

	private static int toSrgb(double linear) {
		double clamped = Math.max(0.0D, Math.min(1.0D, linear));
		double srgb = clamped <= 0.0031308D
				? clamped * 12.92D
				: 1.055D * Math.pow(clamped, 1.0D / 2.4D) - 0.055D;
		return Math.max(0, Math.min(255, (int) Math.round(srgb * 255.0D)));
	}

	private static double clamp01(double value) {
		return Math.max(0.0D, Math.min(1.0D, value));
	}

	private static int clampByte(int value) {
		return Math.max(0, Math.min(255, value));
	}

	private record PaletteEntry(byte packedId, double lightness, double a, double b) {
	}

	private record LabColor(double lightness, double a, double b) {
	}
}
