package com.lostglade.server.monitor;

import java.util.Comparator;
import java.util.UUID;

public final class MonitorSberDronesCatalog {
	public static final String URL_PREFIX = "lg2-live-camera:";
	private static final String DRONE_PREFIX = "drone|";
	private static final String CAMERA_PREFIX = "camera|";

	private MonitorSberDronesCatalog() {
	}

	public enum SourceType {
		CAMERA,
		DRONE
	}

	public record Source(SourceType sourceType, String dimensionId, Integer x, Integer y, Integer z, UUID sourceUuid) {
		public static Source camera(String dimensionId, int x, int y, int z) {
			return new Source(SourceType.CAMERA, sanitize(dimensionId), x, y, z, null);
		}

		public static Source drone(String dimensionId, Integer x, Integer y, Integer z, UUID sourceUuid) {
			return new Source(SourceType.DRONE, sanitize(dimensionId), x, y, z, sourceUuid);
		}

		public boolean hasPosition() {
			return this.x != null && this.y != null && this.z != null;
		}

		private static String sanitize(String value) {
			return value == null ? "" : value.trim();
		}
	}

	public record Card(String title, String subtitle, String url) {
	}

	public static Card card(Source source, boolean online) {
		return new Card(title(source), subtitle(source, online), url(source));
	}

	public static String title(Source source) {
		return source != null && source.sourceType() == SourceType.DRONE ? "Дрон" : "Камера";
	}

	public static String subtitle(Source source, boolean online) {
		if (source == null) {
			return "Прямая трансляция";
		}
		String status = online ? "online" : "offline";
		if (!source.hasPosition()) {
			if (source.sourceType() == SourceType.DRONE && source.sourceUuid() != null) {
				return status + "  •  drone  •  " + source.sourceUuid();
			}
			return "Прямая трансляция";
		}
		String sourceLabel = source.sourceType() == SourceType.DRONE ? "drone" : "camera";
		return status
				+ "  •  "
				+ sourceLabel
				+ "  •  "
				+ source.dimensionId()
				+ "  •  X: "
				+ source.x()
				+ "  Y: "
				+ source.y()
				+ "  Z: "
				+ source.z();
	}

	public static String url(Source source) {
		if (source == null) {
			return "";
		}
		if (source.sourceType() == SourceType.DRONE && source.sourceUuid() != null) {
			StringBuilder builder = new StringBuilder(URL_PREFIX)
					.append(DRONE_PREFIX)
					.append(source.sourceUuid());
			if (source.dimensionId() != null && !source.dimensionId().isBlank()) {
				builder.append("|").append(source.dimensionId());
			}
			return builder.toString();
		}
		if (source.dimensionId() == null || source.dimensionId().isBlank() || !source.hasPosition()) {
			return "";
		}
		return URL_PREFIX
				+ source.dimensionId()
				+ "|"
				+ source.x()
				+ ","
				+ source.y()
				+ ","
				+ source.z();
	}

	public static boolean isLiveCameraUrl(String url) {
		return url != null && url.startsWith(URL_PREFIX);
	}

	public static Source parseUrl(String url, String fallbackDimensionId) {
		if (!isLiveCameraUrl(url)) {
			return null;
		}
		String payload = url.substring(URL_PREFIX.length()).trim();
		if (payload.startsWith(DRONE_PREFIX)) {
			String[] parts = payload.split("\\|", 3);
			if (parts.length < 2) {
				return null;
			}
			try {
				UUID droneUuid = UUID.fromString(parts[1].trim());
				String dimensionId = parts.length >= 3 && !parts[2].isBlank() ? parts[2].trim() : fallbackDimensionId;
				return Source.drone(dimensionId, null, null, null, droneUuid);
			} catch (IllegalArgumentException exception) {
				return null;
			}
		}
		if (payload.startsWith(CAMERA_PREFIX)) {
			payload = payload.substring(CAMERA_PREFIX.length()).trim();
		}
		String dimensionId = fallbackDimensionId == null ? "" : fallbackDimensionId.trim();
		String coordinatesPayload = payload;
		int dimensionSeparator = payload.indexOf('|');
		if (dimensionSeparator >= 0) {
			dimensionId = payload.substring(0, dimensionSeparator).trim();
			coordinatesPayload = payload.substring(dimensionSeparator + 1).trim();
		}
		if (dimensionId.isBlank()) {
			return null;
		}
		String[] parts = coordinatesPayload.split(",", 3);
		if (parts.length != 3) {
			return null;
		}
		try {
			return Source.camera(
					dimensionId,
					Integer.parseInt(parts[0].trim()),
					Integer.parseInt(parts[1].trim()),
					Integer.parseInt(parts[2].trim())
			);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	public static int compare(Source first, Source second) {
		return ORDERING.compare(first, second);
	}

	public static boolean sameIdentity(Source first, Source second) {
		if (first == second) {
			return true;
		}
		if (first == null || second == null || first.sourceType() != second.sourceType()) {
			return false;
		}
		if (first.sourceType() == SourceType.DRONE && first.sourceUuid() != null && second.sourceUuid() != null) {
			return first.sourceUuid().equals(second.sourceUuid());
		}
		return stringValue(first.dimensionId()).equals(stringValue(second.dimensionId()))
				&& value(first.x()) == value(second.x())
				&& value(first.y()) == value(second.y())
				&& value(first.z()) == value(second.z());
	}

	private static final Comparator<Source> ORDERING = (first, second) -> {
		if (first == second) {
			return 0;
		}
		if (first == null) {
			return -1;
		}
		if (second == null) {
			return 1;
		}
		int compareType = first.sourceType().compareTo(second.sourceType());
		if (compareType != 0) {
			return compareType;
		}
		if (first.sourceType() == SourceType.DRONE || second.sourceType() == SourceType.DRONE) {
			int compareUuid = stringValue(first.sourceUuid()).compareTo(stringValue(second.sourceUuid()));
			if (compareUuid != 0) {
				return compareUuid;
			}
		}
		int compareDimension = stringValue(first.dimensionId()).compareTo(stringValue(second.dimensionId()));
		if (compareDimension != 0) {
			return compareDimension;
		}
		int compareX = Integer.compare(value(first.x()), value(second.x()));
		if (compareX != 0) {
			return compareX;
		}
		int compareY = Integer.compare(value(first.y()), value(second.y()));
		if (compareY != 0) {
			return compareY;
		}
		return Integer.compare(value(first.z()), value(second.z()));
	};

	private static String stringValue(Object value) {
		return value == null ? "" : value.toString();
	}

	private static int value(Integer value) {
		return value == null ? Integer.MIN_VALUE : value;
	}
}
