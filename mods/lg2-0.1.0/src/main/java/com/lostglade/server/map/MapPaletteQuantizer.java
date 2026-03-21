package com.lostglade.server.map;

import net.minecraft.world.level.material.MapColor;

public final class MapPaletteQuantizer {
	private static final double REF_X = 0.95047D;
	private static final double REF_Y = 1.0D;
	private static final double REF_Z = 1.08883D;
	private static final double[] SRGB_TO_LINEAR = buildSrgbToLinear();
	private static final PaletteEntry[] PALETTE = buildPalette();

	private MapPaletteQuantizer() {
	}

	public static byte quantize(int rgb) {
		LabColor lab = toLab(rgb);
		double bestDistance = Double.MAX_VALUE;
		byte bestPackedId = 0;
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
		PaletteEntry[] entries = new PaletteEntry[(64 - 1) * brightnesses.length];
		int index = 0;
		for (int colorId = 1; colorId < 64; colorId++) {
			MapColor color = MapColor.byId(colorId);
			for (MapColor.Brightness brightness : brightnesses) {
				byte packedId = color.getPackedId(brightness);
				int packedRgb = MapColor.getColorFromPackedId(Byte.toUnsignedInt(packedId)) & 0xFFFFFF;
				LabColor lab = toLab(packedRgb);
				entries[index++] = new PaletteEntry(
						packedId,
						lab.lightness(),
						lab.a(),
						lab.b()
				);
			}
		}
		return entries;
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

	private record PaletteEntry(byte packedId, double lightness, double a, double b) {
	}

	private record LabColor(double lightness, double a, double b) {
	}
}
