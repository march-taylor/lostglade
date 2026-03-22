package de.bluecolored.bluemap.core.map.hires;

public final class ArrayTileModelAccess {
	private ArrayTileModelAccess() {
	}

	public static int size(ArrayTileModel model) {
		return model.size;
	}

	public static float[] positions(ArrayTileModel model) {
		return model.position;
	}

	public static float[] colors(ArrayTileModel model) {
		return model.color;
	}

	public static float[] uvs(ArrayTileModel model) {
		return model.uv;
	}

	public static float[] aos(ArrayTileModel model) {
		return model.ao;
	}

	public static byte[] sunlight(ArrayTileModel model) {
		return model.sunlight;
	}

	public static byte[] blocklight(ArrayTileModel model) {
		return model.blocklight;
	}

	public static int[] materialIndices(ArrayTileModel model) {
		return model.materialIndex;
	}
}
