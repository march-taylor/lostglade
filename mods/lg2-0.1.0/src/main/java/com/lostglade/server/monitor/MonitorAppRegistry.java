package com.lostglade.server.monitor;

import java.util.List;

public final class MonitorAppRegistry {
	private static final List<MonitorApp> APPS = List.of(
			new MonitorMediaApp(),
			new MonitorSberDronesApp(),
			new MonitorMaxApp(),
			new MonitorYoutubeApp(),
			new MonitorYoutubeMusicApp()
	);

	private MonitorAppRegistry() {
	}

	public static List<MonitorApp> apps() {
		return APPS;
	}

	public static MonitorApp findById(String id) {
		if (id == null || id.isBlank()) {
			return null;
		}
		for (MonitorApp app : APPS) {
			if (app.id().equalsIgnoreCase(id)) {
				return app;
			}
		}
		return null;
	}
}
